package com.billing.simple.billsoft.forecast.service;

import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.forecast.entity.ForecastSalesTransaction;
import com.billing.simple.billsoft.forecast.repository.ForecastSalesTransactionRepository;
import com.billing.simple.billsoft.repo.InvoiceRepository;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.repo.SalesReturnRepository;
import com.billing.simple.billsoft.repo.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Authoritative ingestion service for normalizing invoice sales and sales returns.
 *
 * Key Architectural Guarantees:
 * 1. Invoice-Level Atomic Replacement: Deletes prior SALE facts for an invoice and re-inserts current valid state.
 * 2. Physical Fulfillment Reconstruction: Reconstructs net physical stock deduction from StockMovement ledger.
 * 3. Deterministic Sequential FIFO Line Allocation: Correctly partitions product-level fulfillment across
 *    same-product multi-line invoices without double-counting.
 * 4. Item Type Discrimination: SERVICE items are 100% fulfilled without stock movements; physical GOODS
 *    with 0 movement deduction evaluate to 0 fulfilled and 100% shortage.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForecastIngestionService {

    private final ForecastSalesTransactionRepository transactionRepo;
    private final StockMovementRepository stockMovementRepo;
    private final ProductRepository productRepo;
    private final InvoiceRepository invoiceRepo;
    private final SalesReturnRepository salesReturnRepo;

    @Transactional
    public void syncInvoice(Invoice invoice) {
        if (invoice == null || invoice.getId() == null) return;
        Long firmId = invoice.getFirmId();
        Long invoiceId = invoice.getId();
        String invNo = invoice.getInvoiceNumber();
        String invIdStr = String.valueOf(invoiceId);

        // 1. Atomically delete existing SALE rows for this invoice
        transactionRepo.deleteAllByFirmIdAndSourceTypeAndSourceHeaderId(firmId, "SALE", invoiceId);

        // 2. Ignore non-commercial / non-demand statuses (DRAFT, ESTIMATE, CANCELLED)
        InvoiceStatus status = invoice.getStatus();
        if (status == null ||
            status == InvoiceStatus.DRAFT ||
            status == InvoiceStatus.ESTIMATE ||
            status == InvoiceStatus.CANCELLED) {
            log.debug("Skipping invoice {} with non-sales status {}", invoiceId, status);
            return;
        }

        List<InvoiceItem> items = invoice.getItems();
        if (items == null || items.isEmpty()) return;

        // 3. Reconstruct authoritative net physical stock deduction by Product ID from Stock Movements
        Map<Long, BigDecimal> netDeductedByProduct = new HashMap<>();
        List<Object[]> movAggs = stockMovementRepo.sumQuantityChangeByProductIdForInvoice(firmId, invNo, invIdStr);
        if (movAggs != null) {
            for (Object[] row : movAggs) {
                if (row[0] != null && row[1] != null) {
                    Long pId = ((Number) row[0]).longValue();
                    BigDecimal netChange = (BigDecimal) row[1];
                    // Negative net change indicates physical stock was deducted for this invoice
                    if (netChange.compareTo(BigDecimal.ZERO) < 0) {
                        netDeductedByProduct.put(pId, netChange.abs());
                    }
                }
            }
        }

        // 4. Calculate total requested demand per product across all lines of this invoice
        Map<Long, BigDecimal> totalRequestedByProduct = new HashMap<>();
        for (InvoiceItem item : items) {
            Long prodId = resolveCanonicalProductId(item.getProduct(), firmId);
            if (prodId != null) {
                BigDecimal qty = item.getQty() != null ? BigDecimal.valueOf(item.getQty()) : BigDecimal.ZERO;
                totalRequestedByProduct.merge(prodId, qty, BigDecimal::add);
            }
        }

        // 5. Establish physical fulfillment budget per product
        Map<Long, BigDecimal> remainingFulfilledBudget = new HashMap<>();
        for (Map.Entry<Long, BigDecimal> entry : totalRequestedByProduct.entrySet()) {
            Long pId = entry.getKey();
            BigDecimal totalReq = entry.getValue();
            Product prod = productRepo.findById(pId).orElse(null);
            boolean isService = prod != null && "SERVICE".equalsIgnoreCase(prod.getItemType());

            if (isService) {
                // Service products do not consume physical stock -> fully fulfilled
                remainingFulfilledBudget.put(pId, totalReq);
            } else {
                // Physical goods -> bounded by actual physical deduction in ledger
                BigDecimal totalDeducted = netDeductedByProduct.getOrDefault(pId, BigDecimal.ZERO);
                remainingFulfilledBudget.put(pId, totalDeducted.min(totalReq));
            }
        }

        // 6. Sequential FIFO line allocation across invoice items in deterministic list order
        List<ForecastSalesTransaction> txList = new ArrayList<>();
        int ordinal = 0;
        for (InvoiceItem item : items) {
            BigDecimal reqQty = item.getQty() != null ? BigDecimal.valueOf(item.getQty()) : BigDecimal.ZERO;
            Long prodId = resolveCanonicalProductId(item.getProduct(), firmId);

            BigDecimal fulfilledQty = BigDecimal.ZERO;
            if (prodId != null) {
                BigDecimal availableBudget = remainingFulfilledBudget.getOrDefault(prodId, BigDecimal.ZERO);
                fulfilledQty = availableBudget.min(reqQty);
                remainingFulfilledBudget.put(prodId, availableBudget.subtract(fulfilledQty));
            }
            BigDecimal shortageQty = reqQty.subtract(fulfilledQty).max(BigDecimal.ZERO);

            BigDecimal lineTotal = item.getLineTotal() != null ? item.getLineTotal() : BigDecimal.ZERO;
            BigDecimal unitPrice = reqQty.compareTo(BigDecimal.ZERO) > 0
                ? lineTotal.divide(reqQty, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            ForecastSalesTransaction tx = ForecastSalesTransaction.builder()
                .firmId(firmId)
                .sourceType("SALE")
                .sourceHeaderId(invoiceId)
                .sourceLineOrdinal(ordinal++)
                .productId(prodId)
                .customerId(invoice.getCustomer() != null ? invoice.getCustomer().getId() : null)
                .transactionDate(invoice.getInvoiceDate() != null ? invoice.getInvoiceDate().toLocalDate() : (invoice.getCreatedAt() != null ? invoice.getCreatedAt().toLocalDate() : LocalDate.now()))
                .transactionTimestamp(invoice.getCreatedAt())
                .requestedQty(reqQty)
                .fulfilledQty(fulfilledQty)
                .shortageQty(shortageQty)
                .unitPrice(unitPrice)
                .taxableAmount(item.getTaxableAmount() != null ? item.getTaxableAmount() : BigDecimal.ZERO)
                .taxAmount(item.getGstAmount() != null ? item.getGstAmount() : BigDecimal.ZERO)
                .lineTotal(lineTotal)
                .invoiceStatus(status.name())
                .sourceUpdatedAt(invoice.getUpdatedAt() != null ? invoice.getUpdatedAt() : LocalDateTime.now())
                .build();
            txList.add(tx);
        }
        transactionRepo.saveAll(txList);
        log.debug("Ingested {} SALE transactions for invoice {}", txList.size(), invoiceId);
    }

    @Transactional
    public void syncSalesReturn(SalesReturn sr) {
        if (sr == null || sr.getId() == null) return;
        Long firmId = sr.getFirmId();
        Long returnId = sr.getId();

        List<SalesReturnItem> items = sr.getItems();
        if (items == null || items.isEmpty()) return;

        int ordinal = 0;
        for (SalesReturnItem item : items) {
            // Idempotent insertion check
            if (transactionRepo.existsByFirmIdAndSourceTypeAndSourceHeaderIdAndSourceLineOrdinal(
                    firmId, "RETURN", returnId, ordinal)) {
                ordinal++;
                continue;
            }

            BigDecimal retQty = item.getReturnQty() != null ? BigDecimal.valueOf(item.getReturnQty()) : BigDecimal.ZERO;
            BigDecimal refundTotal = item.getRefundTotal() != null ? item.getRefundTotal() : BigDecimal.ZERO;
            BigDecimal unitPrice = retQty.compareTo(BigDecimal.ZERO) > 0
                ? refundTotal.divide(retQty, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            BigDecimal gstAmount = item.getGstAmount() != null ? item.getGstAmount() : BigDecimal.ZERO;
            BigDecimal taxable = refundTotal.subtract(gstAmount).max(BigDecimal.ZERO);

            Long prodId = resolveCanonicalProductId(item.getProduct(), firmId);

            ForecastSalesTransaction tx = ForecastSalesTransaction.builder()
                .firmId(firmId)
                .sourceType("RETURN")
                .sourceHeaderId(returnId)
                .sourceLineOrdinal(ordinal++)
                .productId(prodId)
                .customerId(sr.getCustomer() != null ? sr.getCustomer().getId() : null)
                .transactionDate(sr.getReturnDate() != null ? sr.getReturnDate() : sr.getCreatedAt().toLocalDate())
                .transactionTimestamp(sr.getCreatedAt())
                .requestedQty(retQty)
                .fulfilledQty(BigDecimal.ZERO) // Returns do not represent outbound physical fulfillment
                .shortageQty(BigDecimal.ZERO)
                .unitPrice(unitPrice)
                .taxableAmount(taxable)
                .taxAmount(gstAmount)
                .lineTotal(refundTotal)
                .invoiceStatus(null)
                .sourceUpdatedAt(sr.getUpdatedAt() != null ? sr.getUpdatedAt() : LocalDateTime.now())
                .build();
            transactionRepo.save(tx);
        }
        log.debug("Ingested RETURN items for return credit note {}", returnId);
    }

    @Transactional
    public int reconcileHardDeletedInvoices(Long firmId) {
        int purged = transactionRepo.purgeOrphanedSaleTransactionsForDeletedInvoices(firmId);
        if (purged > 0) {
            log.info("Purged {} orphaned forecast SALE facts for deleted invoices in firm {}", purged, firmId);
        }
        return purged;
    }

    @Transactional
    public int syncAllInvoicesForTenant(Long firmId) {
        List<Invoice> invoices = invoiceRepo.findAllByFirmIdWithItems(firmId);
        for (Invoice inv : invoices) {
            syncInvoice(inv);
        }
        return invoices.size();
    }

    @Transactional
    public int syncAllInvoicesForFirm(Long firmId) {
        return syncAllInvoicesForTenant(firmId);
    }

    @Transactional
    public int syncAllReturnsForTenant(Long firmId) {
        List<SalesReturn> returns = salesReturnRepo.findByFirmIdOrderByReturnDateDescIdDesc(firmId);
        for (SalesReturn sr : returns) {
            syncSalesReturn(sr);
        }
        return returns.size();
    }

    @Transactional
    public int syncAllSalesReturnsForFirm(Long firmId) {
        return syncAllReturnsForTenant(firmId);
    }

    private Long resolveCanonicalProductId(Product product, Long firmId) {
        if (product == null || product.getId() == null) return null;
        Long curr = product.getId();
        if (product.getCanonicalProductId() != null) {
            curr = product.getCanonicalProductId();
        }
        Set<Long> visited = new HashSet<>();
        visited.add(product.getId());
        while (curr != null && visited.add(curr)) {
            Optional<Product> pOpt = productRepo.findByIdAndFirmId(curr, firmId);
            if (pOpt.isEmpty()) break;
            Product p = pOpt.get();
            if (p.getCanonicalProductId() != null && !p.getCanonicalProductId().equals(curr)) {
                curr = p.getCanonicalProductId();
            } else {
                break;
            }
        }
        return curr;
    }
}
