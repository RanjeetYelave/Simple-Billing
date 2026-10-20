package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dtos.migration.InvoiceMigrationDTOs.*;
import com.billing.simple.billsoft.dtos.migration.InvoiceSnapshotDTO;
import com.billing.simple.billsoft.dtos.migration.InvoiceSnapshotDTO.*;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.forecast.repository.ForecastSalesTransactionRepository;
import com.billing.simple.billsoft.forecast.service.ForecastAggregationService;
import com.billing.simple.billsoft.forecast.service.ForecastIngestionService;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dedicated orchestration engine for Cross-Firm Invoice Migration & Exact-Snapshot Revert.
 *
 * Invariants:
 * 1. Transactional Atomicity: All-or-nothing rollback on any failure.
 * 2. Concurrency Safety: Row-level pessimistic write lock on Invoice (SELECT ... FOR UPDATE).
 * 3. Ledger Precision: Physical inventory reversals are derived from actual stock_movements ledger.
 * 4. Price Snapshot Freeze: InvoiceItem pricing & tax amounts are never recalculated from target product master data.
 * 5. Monotonic Sequence: Old invoice numbers are never reused; every transition gets a brand-new number.
 * 6. Revert Fidelity: Revert restores the exact pre-migration snapshot, discarding post-migration target firm edits.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceMigrationService {

    private final InvoiceRepository invoiceRepo;
    private final CustomerRepository customerRepo;
    private final ProductRepository productRepo;
    private final SavedItemRepository savedItemRepo;
    private final StockMovementRepository stockMovementRepo;
    private final InvoicePaymentRepository invoicePaymentRepo;
    private final SalesReturnRepository salesReturnRepo;
    private final SalesReturnItemRepository salesReturnItemRepo;
    private final FirmDetailsRepository firmDetailsRepo;
    private final InvoiceMigrationRecordRepository migrationRecordRepo;
    private final ProductService productService;
    private final InvoiceService invoiceService;
    private final ForecastIngestionService forecastIngestionService;
    private final ForecastAggregationService forecastAggregationService;
    private final ForecastSalesTransactionRepository forecastTransactionRepo;
    private final ReminderRepository reminderRepo;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    /**
     * Read-only Migration Preview: calculates eligibility, customer matching, product mappings,
     * inventory impact, and next invoice number without mutating any state.
     */
    @Transactional(readOnly = true)
    public InvoiceMigrationPreviewResponse previewMigration(Long invoiceId, Long targetFirmId) {
        if (invoiceId == null) {
            throw new IllegalArgumentException("invoiceId is required");
        }
        if (targetFirmId == null) {
            throw new IllegalArgumentException("targetFirmId is required");
        }

        Long activeFirmId = TenantContext.getRequiredFirmId();

        Invoice invoice = invoiceRepo.findByIdAndFirmId(invoiceId, activeFirmId)
                .or(() -> invoiceRepo.findById(invoiceId).filter(inv -> activeFirmId.equals(inv.getFirmId())))
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found with id: " + invoiceId));

        Long sourceFirmId = invoice.getFirmId();
        String sourceFirmName = getFirmName(sourceFirmId);
        String targetFirmName = getFirmName(targetFirmId);

        InvoiceMigrationPreviewResponse.InvoiceMigrationPreviewResponseBuilder resp =
                InvoiceMigrationPreviewResponse.builder()
                        .invoiceId(invoiceId)
                        .sourceFirmId(sourceFirmId)
                        .sourceFirmName(sourceFirmName)
                        .targetFirmId(targetFirmId)
                        .targetFirmName(targetFirmName)
                        .currentInvoiceNumber(invoice.getInvoiceNumber())
                        .estimatedTargetInvoiceNumber(estimateNextInvoiceNumber(targetFirmId))
                        .eligible(true)
                        .hasPayments(false)
                        .hasReturns(false)
                        .forecastAffected(true);

        // 1. Eligibility Validations
        if (sourceFirmId.equals(targetFirmId)) {
            resp.eligible(false).ineligibilityReason("Invoice already belongs to target firm (" + targetFirmName + ").");
            return resp.build();
        }

        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            resp.eligible(false).ineligibilityReason("Cancelled invoices cannot be migrated.");
            return resp.build();
        }

        if (invoice.getStatus() == InvoiceStatus.ESTIMATE) {
            resp.eligible(false).ineligibilityReason("Estimates cannot be migrated as invoices.");
            return resp.build();
        }

        // Check payments
        List<InvoicePayment> payments = invoicePaymentRepo.findByInvoiceIdOrderByPaymentDateAscIdAsc(invoiceId);
        if (payments != null && !payments.isEmpty()) {
            resp.eligible(false)
                .hasPayments(true)
                .ineligibilityReason("Invoice has existing payments (" + payments.size() + "). Only completely unpaid invoices can be migrated.");
            return resp.build();
        }

        // Check returns
        List<SalesReturn> returns = salesReturnRepo.findByInvoiceIdOrderByCreatedAtDesc(invoiceId);
        if (returns != null && !returns.isEmpty()) {
            resp.eligible(false)
                .hasReturns(true)
                .ineligibilityReason("Invoice has existing sales returns (" + returns.size() + "). Invoices with returns cannot be migrated.");
            return resp.build();
        }

        // 2. Customer Resolution Preview
        Customer sourceCustomer = invoice.getCustomer();
        CustomerResolutionDTO customerRes = resolveCustomerForPreview(sourceCustomer, targetFirmId);
        resp.customerResolution(customerRes);

        // 3. Product Resolution Preview & Inventory Impact Calculation
        List<ProductResolutionDTO> productResolutions = new ArrayList<>();
        BigDecimal totalSourceRestoration = BigDecimal.ZERO;
        BigDecimal totalTargetDeduction = BigDecimal.ZERO;

        Map<Long, BigDecimal> historicalDeductions = getHistoricalPhysicalDeductions(sourceFirmId, invoice);

        if (invoice.getItems() != null) {
            for (InvoiceItem item : invoice.getItems()) {
                ProductResolutionDTO itemRes = resolveItemForPreview(item, targetFirmId);
                productResolutions.add(itemRes);

                if (item.getProduct() != null && item.getProduct().getId() != null) {
                    BigDecimal deducted = historicalDeductions.getOrDefault(item.getProduct().getId(), BigDecimal.ZERO);
                    totalSourceRestoration = totalSourceRestoration.add(deducted);
                }

                if (("INVENTORY_PRODUCT".equals(itemRes.getResolutionType()) || "AUTO_CREATE_PRODUCT".equals(itemRes.getResolutionType())) && itemRes.getStockDeduction() != null) {
                    totalTargetDeduction = totalTargetDeduction.add(itemRes.getStockDeduction());
                }
            }
        }

        resp.productResolutions(productResolutions)
            .sourceStockRestoration(totalSourceRestoration)
            .targetStockDeduction(totalTargetDeduction);

        return resp.build();
    }

    /**
     * Authoritative Forward Migration: moves an unpaid invoice from Firm A to Firm B atomically.
     */
    @Transactional(rollbackFor = Exception.class)
    public InvoiceMigrationResponse migrateInvoice(Long invoiceId, InvoiceMigrationRequest request) {
        if (invoiceId == null) {
            throw new IllegalArgumentException("invoiceId is required");
        }
        if (request == null || request.getTargetFirmId() == null) {
            throw new IllegalArgumentException("targetFirmId is required");
        }
        Long targetFirmId = request.getTargetFirmId();

        // 0. Enforce active tenant context
        Long activeFirmId = TenantContext.getRequiredFirmId();

        // 1. Lock invoice row using pessimistic write lock scoped to active firm
        Invoice invoice = invoiceRepo.findByIdAndFirmIdForUpdate(invoiceId, activeFirmId)
                .or(() -> invoiceRepo.findByIdForUpdate(invoiceId))
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found with id: " + invoiceId));

        Long sourceFirmId = invoice.getFirmId();
        if (!activeFirmId.equals(sourceFirmId)) {
            throw new TenantSecurityException("Cross-firm migration blocked: invoice belongs to firm " + sourceFirmId + " but active context is " + activeFirmId);
        }

        // 2. Authoritative Source Validation & Idempotency check
        if (sourceFirmId.equals(targetFirmId)) {
            throw new IllegalStateException("Invoice " + invoiceId + " already belongs to target firm " + targetFirmId);
        }

        FirmDetails targetFirm = firmDetailsRepo.findById(targetFirmId)
                .orElseThrow(() -> new IllegalArgumentException("Target firm not found with id: " + targetFirmId));

        // 3. Strict Precondition Validations
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new IllegalStateException("Cannot migrate a cancelled invoice");
        }
        if (invoice.getStatus() == InvoiceStatus.ESTIMATE) {
            throw new IllegalStateException("Cannot migrate an estimate as an invoice");
        }

        List<InvoicePayment> payments = invoicePaymentRepo.findByInvoiceIdOrderByPaymentDateAscIdAsc(invoiceId);
        if (payments != null && !payments.isEmpty()) {
            throw new IllegalStateException("Migration blocked: Invoice has " + payments.size() + " recorded payment(s).");
        }

        List<SalesReturn> returns = salesReturnRepo.findByInvoiceIdOrderByCreatedAtDesc(invoiceId);
        if (returns != null && !returns.isEmpty()) {
            throw new IllegalStateException("Migration blocked: Invoice has " + returns.size() + " recorded sales return(s).");
        }

        // 4. Capture Complete Pre-Migration Snapshot before ANY mutations
        Map<Long, BigDecimal> historicalDeductions = getHistoricalPhysicalDeductions(sourceFirmId, invoice);
        InvoiceSnapshotDTO snapshot = captureSnapshot(invoice, historicalDeductions);
        String snapshotJson;
        try {
            snapshotJson = objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize pre-migration snapshot", e);
        }

        // 5. Reverse Source Firm A Inventory Effects (Physical ledger restoration)
        for (Map.Entry<Long, BigDecimal> entry : historicalDeductions.entrySet()) {
            Long prodId = entry.getKey();
            BigDecimal physicallyDeducted = entry.getValue();
            if (physicallyDeducted.compareTo(BigDecimal.ZERO) > 0) {
                productService.recordStockMovement(
                        prodId,
                        sourceFirmId,
                        "INVOICE_MIGRATION_REVERSAL",
                        physicallyDeducted,
                        "INVOICE",
                        invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : String.valueOf(invoiceId),
                        "Compensating inventory restoration for migration to firm " + targetFirmId);
            }
        }

        // 6. Remove Source Firm A Forecast Data & Recalculate Source Metrics
        forecastTransactionRepo.deleteAllByFirmIdAndSourceTypeAndSourceHeaderId(sourceFirmId, "SALE", invoiceId);
        LocalDate invoiceDate = invoice.getInvoiceDate() != null ? invoice.getInvoiceDate().toLocalDate() : LocalDate.now();
        if (invoice.getItems() != null) {
            Set<Long> affectedSourceProds = invoice.getItems().stream()
                    .filter(it -> it.getProduct() != null && it.getProduct().getId() != null)
                    .map(it -> it.getProduct().getId())
                    .collect(Collectors.toSet());
            for (Long pId : affectedSourceProds) {
                forecastAggregationService.aggregateProductDailyMetrics(sourceFirmId, pId, invoiceDate);
            }
        }

        // 7. Resolve / Create Customer in Target Firm B
        Customer resolvedCustomer = resolveOrCreateCustomer(invoice.getCustomer(), targetFirmId, request);

        // 8. Map User-Selected Products / SavedItems in Target Firm B
        Map<Long, ProductResolutionSelection> selectionMap = new HashMap<>();
        if (request.getProductSelections() != null) {
            for (ProductResolutionSelection sel : request.getProductSelections()) {
                if (sel.getItemId() != null) {
                    selectionMap.put(sel.getItemId(), sel);
                }
            }
        }

        List<InvoiceItem> items = invoice.getItems();
        if (items != null) {
            for (InvoiceItem item : items) {
                Product targetProd = resolveOrCreateProduct(item, targetFirmId, request);
                item.setProduct(targetProd);
                if (targetProd != null) {
                    item.setProductName(targetProd.getName());
                }
                // NOTE: Historical pricing snapshots (pricePerUnit, taxableAmount, lineTotal, gstPercent) are PRESERVED EXACTLY!
            }
        }

        // 9. Allocate New Target Firm Invoice Number
        String newInvoiceNumber = invoiceService.generateInvoiceNumber(targetFirmId);

        // 10. Re-home Invoice Entity
        invoice.setFirmId(targetFirmId);
        invoice.setInvoiceNumber(newInvoiceNumber);
        invoice.setCustomer(resolvedCustomer);

        // Detach cross-firm converted estimate reference (snapshot preserves original estimate ID)
        if (invoice.getConvertedInvoiceId() != null) {
            invoice.setConvertedInvoiceId(null);
        }

        Invoice savedInvoice = invoiceRepo.save(invoice);

        // 11. Deduct Inventory in Target Firm B (for inventory products)
        if (items != null) {
            for (InvoiceItem item : items) {
                if (item.getProduct() != null && item.getProduct().getId() != null) {
                    Product targetProd = item.getProduct();
                    if (!"SERVICE".equalsIgnoreCase(targetProd.getItemType())) {
                        BigDecimal qty = item.getQty() != null ? BigDecimal.valueOf(item.getQty()) : BigDecimal.ZERO;
                        if (qty.compareTo(BigDecimal.ZERO) > 0) {
                            productService.recordStockMovement(
                                    targetProd.getId(),
                                    targetFirmId,
                                    "INVOICE_SALE",
                                    qty.negate(),
                                    "INVOICE",
                                    newInvoiceNumber,
                                    "Stock deducted for invoice migrated from firm " + sourceFirmId);
                        }
                    }
                }
            }
        }

        // 12. Create Target Firm B Forecast Transactions & Recalculate Metrics
        forecastIngestionService.syncInvoice(savedInvoice);
        if (items != null) {
            Set<Long> affectedTargetProds = items.stream()
                    .filter(it -> it.getProduct() != null && it.getProduct().getId() != null)
                    .map(it -> it.getProduct().getId())
                    .collect(Collectors.toSet());
            for (Long pId : affectedTargetProds) {
                forecastAggregationService.aggregateProductDailyMetrics(targetFirmId, pId, invoiceDate);
            }
        }

        // 13. Re-home Associated Reminders
        List<Reminder> reminders = reminderRepo.findByFirmIdAndInvoiceId(sourceFirmId, invoiceId);
        if (reminders != null && !reminders.isEmpty()) {
            for (Reminder r : reminders) {
                r.setFirmId(targetFirmId);
                if (resolvedCustomer != null) {
                    r.setCustomerId(resolvedCustomer.getId());
                }
                reminderRepo.save(r);
            }
        }

        // 14. Persist Immutable Migration Record
        // Mark previous active migration record as SUPERSEDED if exists
        migrationRecordRepo.findFirstByInvoiceIdAndStatusOrderByCreatedAtDesc(invoiceId, "ACTIVE")
                .ifPresent(rec -> {
                    rec.setStatus("SUPERSEDED");
                    migrationRecordRepo.save(rec);
                });

        String currentUser = "master_admin";

        InvoiceMigrationRecord record = InvoiceMigrationRecord.builder()
                .invoiceId(invoiceId)
                .sourceFirmId(sourceFirmId)
                .targetFirmId(targetFirmId)
                .sourceInvoiceNumber(snapshot.getInvoiceNumber())
                .targetInvoiceNumber(newInvoiceNumber)
                .isRevert(false)
                .status("ACTIVE")
                .preMigrationSnapshot(snapshotJson)
                .migratedBy(currentUser)
                .createdAt(LocalDateTime.now())
                .build();

        InvoiceMigrationRecord savedRecord = migrationRecordRepo.save(record);

        log.info("Invoice {} migrated successfully from Firm {} ({}) to Firm {} ({}) as {}",
                invoiceId, sourceFirmId, snapshot.getInvoiceNumber(), targetFirmId, newInvoiceNumber, record.getId());

        return InvoiceMigrationResponse.builder()
                .invoiceId(invoiceId)
                .migrationRecordId(savedRecord.getId())
                .sourceFirmId(sourceFirmId)
                .sourceFirmName(getFirmName(sourceFirmId))
                .targetFirmId(targetFirmId)
                .targetFirmName(targetFirm.getFirmName())
                .sourceInvoiceNumber(snapshot.getInvoiceNumber())
                .targetInvoiceNumber(newInvoiceNumber)
                .isRevert(false)
                .status("ACTIVE")
                .migratedAt(savedRecord.getCreatedAt())
                .migratedInvoice(savedInvoice)
                .build();
    }

    /**
     * Exact-Snapshot Revert: restores the invoice to its exact pre-migration state in the original source firm,
     * discarding any post-migration target firm modifications, while allocating a brand-new monotonic invoice number.
     */
    @Transactional(rollbackFor = Exception.class)
    public InvoiceMigrationResponse revertMigration(Long invoiceId) {
        if (invoiceId == null) {
            throw new IllegalArgumentException("invoiceId is required");
        }

        // 0. Enforce active tenant context
        Long activeFirmId = TenantContext.getRequiredFirmId();

        // 1. Lock invoice row
        Invoice invoice = invoiceRepo.findByIdForUpdate(invoiceId)
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found with id: " + invoiceId));

        Long currentFirmId = invoice.getFirmId();

        // 2. Find latest active migration record
        InvoiceMigrationRecord activeMigration = migrationRecordRepo
                .findFirstByInvoiceIdAndStatusOrderByCreatedAtDesc(invoiceId, "ACTIVE")
                .or(() -> migrationRecordRepo.findFirstByInvoiceIdOrderByCreatedAtDesc(invoiceId))
                .orElseThrow(() -> new IllegalStateException("No migration record found for invoice: " + invoiceId));

        if (!currentFirmId.equals(activeMigration.getTargetFirmId())) {
            throw new IllegalStateException("Invoice is currently in firm " + currentFirmId + " but active migration target was " + activeMigration.getTargetFirmId());
        }

        // 2b. Verify caller authorization: caller must be current firm or originating source firm
        if (!activeFirmId.equals(currentFirmId) && !activeFirmId.equals(activeMigration.getSourceFirmId())) {
            throw new TenantSecurityException("Cross-firm revert blocked: caller firm " + activeFirmId + " is not authorized for invoice in firm " + currentFirmId);
        }

        // 3. Strict Precondition Validations on Current Firm
        List<InvoicePayment> payments = invoicePaymentRepo.findByInvoiceIdOrderByPaymentDateAscIdAsc(invoiceId);
        if (payments != null && !payments.isEmpty()) {
            throw new IllegalStateException("Revert blocked: Invoice has " + payments.size() + " recorded payment(s) in current firm.");
        }

        List<SalesReturn> returns = salesReturnRepo.findByInvoiceIdOrderByCreatedAtDesc(invoiceId);
        if (returns != null && !returns.isEmpty()) {
            throw new IllegalStateException("Revert blocked: Invoice has " + returns.size() + " recorded sales return(s) in current firm.");
        }

        // 4. Deserialize authoritative pre-migration snapshot
        InvoiceSnapshotDTO snapshot;
        try {
            snapshot = objectMapper.readValue(activeMigration.getPreMigrationSnapshot(), InvoiceSnapshotDTO.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize pre-migration snapshot for revert", e);
        }

        Long restoredFirmId = snapshot.getSourceFirmId();
        FirmDetails restoredFirm = firmDetailsRepo.findById(restoredFirmId)
                .orElseThrow(() -> new IllegalArgumentException("Original source firm no longer exists: " + restoredFirmId));

        // 5. Reverse Current Target Firm B Inventory Effects
        Map<Long, BigDecimal> currentDeductions = getHistoricalPhysicalDeductions(currentFirmId, invoice);
        for (Map.Entry<Long, BigDecimal> entry : currentDeductions.entrySet()) {
            Long prodId = entry.getKey();
            BigDecimal physicallyDeducted = entry.getValue();
            if (physicallyDeducted.compareTo(BigDecimal.ZERO) > 0) {
                productService.recordStockMovement(
                        prodId,
                        currentFirmId,
                        "INVOICE_MIGRATION_REVERSAL",
                        physicallyDeducted,
                        "INVOICE",
                        invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : String.valueOf(invoiceId),
                        "Compensating inventory restoration for migration revert to firm " + restoredFirmId);
            }
        }

        // 6. Remove Current Target Firm B Forecast Data & Recalculate Metrics
        forecastTransactionRepo.deleteAllByFirmIdAndSourceTypeAndSourceHeaderId(currentFirmId, "SALE", invoiceId);
        LocalDate invoiceDate = invoice.getInvoiceDate() != null ? invoice.getInvoiceDate().toLocalDate() : LocalDate.now();
        if (invoice.getItems() != null) {
            Set<Long> affectedCurrentProds = invoice.getItems().stream()
                    .filter(it -> it.getProduct() != null && it.getProduct().getId() != null)
                    .map(it -> it.getProduct().getId())
                    .collect(Collectors.toSet());
            for (Long pId : affectedCurrentProds) {
                forecastAggregationService.aggregateProductDailyMetrics(currentFirmId, pId, invoiceDate);
            }
        }

        // 7. Restore Original Source Firm A Customer
        Customer restoredCustomer = null;
        if (snapshot.getCustomer() != null && snapshot.getCustomer().getCustomerId() != null) {
            restoredCustomer = customerRepo.findByIdAndFirmId(snapshot.getCustomer().getCustomerId(), restoredFirmId).orElse(null);
        }
        if (restoredCustomer == null && snapshot.getCustomer() != null) {
            // Re-resolve or re-create in source firm
            String gstin = snapshot.getCustomer().getGstin();
            Optional<Customer> gstinOpt = (gstin != null && !gstin.trim().isEmpty())
                    ? customerRepo.findByFirmIdAndGstinIgnoreCase(restoredFirmId, gstin.trim()).stream().findFirst()
                    : Optional.empty();

            restoredCustomer = gstinOpt
                    .or(() -> customerRepo.findFirstByFirmIdAndPhone(restoredFirmId, snapshot.getCustomer().getPhone()))
                    .or(() -> customerRepo.findFirstByFirmIdAndNameIgnoreCase(restoredFirmId, snapshot.getCustomer().getName()))
                    .orElseGet(() -> {
                        Customer newCust = Customer.builder()
                                .name(snapshot.getCustomer().getName())
                                .phone(snapshot.getCustomer().getPhone())
                                .email(snapshot.getCustomer().getEmail())
                                .address(snapshot.getCustomer().getAddress())
                                .gstin(snapshot.getCustomer().getGstin())
                                .firmId(restoredFirmId)
                                .build();
                        return customerRepo.save(newCust);
                    });
        }

        // 8. Allocate BRAND NEW monotonic invoice number in Restored Firm A (Never reuse consumed old numbers!)
        String newSourceInvoiceNumber = invoiceService.generateInvoiceNumber(restoredFirmId);

        // 9. Restore Invoice Business Fields Exactly from Snapshot
        invoice.setFirmId(restoredFirmId);
        invoice.setInvoiceNumber(newSourceInvoiceNumber);
        invoice.setEstimateNumber(snapshot.getEstimateNumber());
        invoice.setStatus(snapshot.getStatus() != null ? snapshot.getStatus() : InvoiceStatus.UNPAID);
        invoice.setCustomer(restoredCustomer);
        invoice.setInvoiceDate(snapshot.getInvoiceDate());
        invoice.setDueDate(snapshot.getDueDate());
        invoice.setCustomerNote(snapshot.getCustomerNote());
        invoice.setTermsAndConditions(snapshot.getTermsAndConditions());
        invoice.setPaymentMethod(snapshot.getPaymentMethod());
        invoice.setCurrency(snapshot.getCurrency());
        invoice.setRoundOff(snapshot.getRoundOff());
        invoice.setTags(snapshot.getTags());
        invoice.setInvoiceDiscountType(snapshot.getInvoiceDiscountType());
        invoice.setInvoiceDiscountValue(snapshot.getInvoiceDiscountValue());
        invoice.setSubtotalWithoutTax(snapshot.getSubtotalWithoutTax());
        invoice.setTotalTax(snapshot.getTotalTax());
        invoice.setTotalDiscount(snapshot.getTotalDiscount());
        invoice.setTotalAmount(snapshot.getTotalAmount());
        invoice.setPaid(false);

        // Reconnect converted estimate relationship if estimate still exists in restored firm
        if (snapshot.getConvertedInvoiceId() != null) {
            if (invoiceRepo.existsByIdAndFirmId(snapshot.getConvertedInvoiceId(), restoredFirmId)) {
                invoice.setConvertedInvoiceId(snapshot.getConvertedInvoiceId());
            } else {
                invoice.setConvertedInvoiceId(null);
            }
        } else {
            invoice.setConvertedInvoiceId(null);
        }

        // 10. Reconstruct Invoice Items from Snapshot
        if (invoice.getItems() == null) {
            invoice.setItems(new ArrayList<>());
        } else {
            invoice.getItems().clear();
        }

        if (snapshot.getItems() != null) {
            for (InvoiceItemSnapshotDTO itemSnap : snapshot.getItems()) {
                Product prod = null;
                if (itemSnap.getProductId() != null) {
                    prod = productRepo.findByIdAndFirmId(itemSnap.getProductId(), restoredFirmId).orElse(null);
                }

                InvoiceItem item = InvoiceItem.builder()
                        .invoice(invoice)
                        .product(prod)
                        .productName(itemSnap.getProductName())
                        .qty(itemSnap.getQty())
                        .unit(itemSnap.getUnit())
                        .hsnCode(itemSnap.getHsnCode())
                        .pricePerUnit(itemSnap.getPricePerUnit())
                        .amountWithoutTax(itemSnap.getAmountWithoutTax())
                        .discountType(itemSnap.getDiscountType())
                        .discountValue(itemSnap.getDiscountValue())
                        .discountPercent(itemSnap.getDiscountPercent())
                        .taxableAmount(itemSnap.getTaxableAmount())
                        .gstPercent(itemSnap.getGstPercent())
                        .gstAmount(itemSnap.getGstAmount())
                        .lineTotal(itemSnap.getLineTotal())
                        .build();

                invoice.getItems().add(item);
            }
        }

        Invoice savedInvoice = invoiceRepo.save(invoice);

        // 11. Re-apply Restored Firm A Inventory Deductions (for inventory products)
        if (savedInvoice.getItems() != null) {
            for (InvoiceItem item : savedInvoice.getItems()) {
                if (item.getProduct() != null && item.getProduct().getId() != null) {
                    Product prod = item.getProduct();
                    if (!"SERVICE".equalsIgnoreCase(prod.getItemType())) {
                        BigDecimal qty = item.getQty() != null ? BigDecimal.valueOf(item.getQty()) : BigDecimal.ZERO;
                        if (qty.compareTo(BigDecimal.ZERO) > 0) {
                            productService.recordStockMovement(
                                    prod.getId(),
                                    restoredFirmId,
                                    "INVOICE_SALE",
                                    qty.negate(),
                                    "INVOICE",
                                    newSourceInvoiceNumber,
                                    "Stock deducted on migration revert from firm " + currentFirmId);
                        }
                    }
                }
            }
        }

        // 12. Re-create Restored Firm A Forecast Transactions & Recalculate Metrics
        forecastIngestionService.syncInvoice(savedInvoice);
        if (savedInvoice.getItems() != null) {
            Set<Long> affectedRestoredProds = savedInvoice.getItems().stream()
                    .filter(it -> it.getProduct() != null && it.getProduct().getId() != null)
                    .map(it -> it.getProduct().getId())
                    .collect(Collectors.toSet());
            for (Long pId : affectedRestoredProds) {
                forecastAggregationService.aggregateProductDailyMetrics(restoredFirmId, pId, invoiceDate);
            }
        }

        // 13. Re-home Associated Reminders back to Restored Firm & Restored Customer
        List<Reminder> reminders = reminderRepo.findByFirmIdAndInvoiceId(currentFirmId, invoiceId);
        if (reminders != null && !reminders.isEmpty()) {
            for (Reminder r : reminders) {
                r.setFirmId(restoredFirmId);
                if (restoredCustomer != null) {
                    r.setCustomerId(restoredCustomer.getId());
                }
                reminderRepo.save(r);
            }
        }

        // 14. Update Migration History
        activeMigration.setStatus("REVERTED");
        migrationRecordRepo.save(activeMigration);

        String currentUser = "master_admin";

        InvoiceMigrationRecord revertRecord = InvoiceMigrationRecord.builder()
                .invoiceId(invoiceId)
                .sourceFirmId(currentFirmId)
                .targetFirmId(restoredFirmId)
                .sourceInvoiceNumber(invoice.getInvoiceNumber())
                .targetInvoiceNumber(newSourceInvoiceNumber)
                .isRevert(true)
                .parentMigrationId(activeMigration.getId())
                .status("ACTIVE")
                .preMigrationSnapshot(activeMigration.getPreMigrationSnapshot())
                .migratedBy(currentUser)
                .createdAt(LocalDateTime.now())
                .build();

        InvoiceMigrationRecord savedRevertRecord = migrationRecordRepo.save(revertRecord);

        log.info("Invoice {} reverted successfully from Firm {} to Firm {} ({}) as {}",
                invoiceId, currentFirmId, restoredFirmId, newSourceInvoiceNumber, savedRevertRecord.getId());

        return InvoiceMigrationResponse.builder()
                .invoiceId(invoiceId)
                .migrationRecordId(savedRevertRecord.getId())
                .sourceFirmId(currentFirmId)
                .sourceFirmName(getFirmName(currentFirmId))
                .targetFirmId(restoredFirmId)
                .targetFirmName(restoredFirm.getFirmName())
                .sourceInvoiceNumber(invoice.getInvoiceNumber())
                .targetInvoiceNumber(newSourceInvoiceNumber)
                .isRevert(true)
                .status("ACTIVE")
                .migratedAt(savedRevertRecord.getCreatedAt())
                .migratedInvoice(savedInvoice)
                .build();
    }

    /**
     * Retrieves immutable migration history for an invoice.
     */
    @Transactional(readOnly = true)
    public List<InvoiceMigrationRecordDTO> getMigrationHistory(Long invoiceId) {
        if (invoiceId == null) {
            return Collections.emptyList();
        }
        Long activeFirmId = TenantContext.getRequiredFirmId();

        Invoice invoice = invoiceRepo.findById(invoiceId)
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found with id: " + invoiceId));

        List<InvoiceMigrationRecord> records = migrationRecordRepo.findByInvoiceIdOrderByCreatedAtDesc(invoiceId);

        // Verify authorized firm access: caller must be current invoice firm OR a party in migration history
        boolean isCurrentFirm = activeFirmId.equals(invoice.getFirmId());
        boolean isMigrationParty = records.stream().anyMatch(r ->
                activeFirmId.equals(r.getSourceFirmId()) || activeFirmId.equals(r.getTargetFirmId()));

        if (!isCurrentFirm && !isMigrationParty) {
            throw new EntityNotFoundException("Invoice not found with id: " + invoiceId);
        }

        List<InvoiceMigrationRecordDTO> dtoList = new ArrayList<>();
        for (InvoiceMigrationRecord r : records) {
            if (invoiceId.equals(r.getInvoiceId())) {
                dtoList.add(InvoiceMigrationRecordDTO.builder()
                    .id(r.getId())
                    .invoiceId(r.getInvoiceId())
                    .sourceFirmId(r.getSourceFirmId())
                    .sourceFirmName(getFirmName(r.getSourceFirmId()))
                    .targetFirmId(r.getTargetFirmId())
                    .targetFirmName(getFirmName(r.getTargetFirmId()))
                    .sourceInvoiceNumber(r.getSourceInvoiceNumber())
                    .targetInvoiceNumber(r.getTargetInvoiceNumber())
                    .isRevert(r.getIsRevert())
                    .parentMigrationId(r.getParentMigrationId())
                    .status(r.getStatus())
                    .migratedBy(r.getMigratedBy())
                    .createdAt(r.getCreatedAt())
                    .build());
            }
        }
        return dtoList;
    }

    // =========================================================================
    // HELPER METHODS: SNAPSHOT, RESOLUTION, LEDGER QUERIES
    // =========================================================================

    private InvoiceSnapshotDTO captureSnapshot(Invoice invoice, Map<Long, BigDecimal> historicalDeductions) {
        Customer cust = invoice.getCustomer();
        CustomerSnapshotDTO custSnap = null;
        if (cust != null) {
            custSnap = CustomerSnapshotDTO.builder()
                    .customerId(cust.getId())
                    .name(cust.getName())
                    .phone(cust.getPhone())
                    .email(cust.getEmail())
                    .address(cust.getAddress())
                    .gstin(cust.getGstin())
                    .build();
        }

        List<InvoiceItemSnapshotDTO> itemSnaps = new ArrayList<>();
        if (invoice.getItems() != null) {
            for (InvoiceItem item : invoice.getItems()) {
                Long pId = item.getProduct() != null ? item.getProduct().getId() : null;
                String itemType = item.getProduct() != null ? item.getProduct().getItemType() : "GOODS";
                BigDecimal stockDeducted = (pId != null) ? historicalDeductions.getOrDefault(pId, BigDecimal.ZERO) : BigDecimal.ZERO;

                itemSnaps.add(InvoiceItemSnapshotDTO.builder()
                        .itemId(item.getId())
                        .productId(pId)
                        .productName(item.getProductName())
                        .qty(item.getQty())
                        .unit(item.getUnit())
                        .hsnCode(item.getHsnCode())
                        .pricePerUnit(item.getPricePerUnit())
                        .amountWithoutTax(item.getAmountWithoutTax())
                        .discountType(item.getDiscountType())
                        .discountValue(item.getDiscountValue())
                        .discountPercent(item.getDiscountPercent())
                        .taxableAmount(item.getTaxableAmount())
                        .gstPercent(item.getGstPercent())
                        .gstAmount(item.getGstAmount())
                        .lineTotal(item.getLineTotal())
                        .itemType(itemType)
                        .physicalStockDeducted(stockDeducted)
                        .build());
            }
        }

        return InvoiceSnapshotDTO.builder()
                .snapshotVersion(1)
                .snapshotTimestamp(LocalDateTime.now())
                .sourceFirmId(invoice.getFirmId())
                .invoiceId(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .estimateNumber(invoice.getEstimateNumber())
                .status(invoice.getStatus())
                .invoiceDate(invoice.getInvoiceDate())
                .dueDate(invoice.getDueDate())
                .convertedInvoiceId(invoice.getConvertedInvoiceId())
                .paid(invoice.getPaid())
                .customerNote(invoice.getCustomerNote())
                .termsAndConditions(invoice.getTermsAndConditions())
                .paymentMethod(invoice.getPaymentMethod())
                .currency(invoice.getCurrency())
                .roundOff(invoice.getRoundOff())
                .tags(invoice.getTags())
                .invoiceDiscountType(invoice.getInvoiceDiscountType())
                .invoiceDiscountValue(invoice.getInvoiceDiscountValue())
                .subtotalWithoutTax(invoice.getSubtotalWithoutTax())
                .totalTax(invoice.getTotalTax())
                .totalDiscount(invoice.getTotalDiscount())
                .totalAmount(invoice.getTotalAmount())
                .customer(custSnap)
                .items(itemSnaps)
                .build();
    }

    private Map<Long, BigDecimal> getHistoricalPhysicalDeductions(Long firmId, Invoice invoice) {
        Map<Long, BigDecimal> result = new HashMap<>();
        if (invoice == null || invoice.getId() == null || firmId == null) {
            return result;
        }
        String invNo = invoice.getInvoiceNumber();
        String invIdStr = String.valueOf(invoice.getId());

        List<Object[]> movAggs = stockMovementRepo.sumQuantityChangeByProductIdForInvoice(firmId, invNo, invIdStr);
        if (movAggs != null) {
            for (Object[] row : movAggs) {
                if (row[0] != null && row[1] != null) {
                    Long pId = ((Number) row[0]).longValue();
                    BigDecimal netChange = (BigDecimal) row[1];
                    if (netChange.compareTo(BigDecimal.ZERO) < 0) {
                        result.put(pId, netChange.abs());
                    }
                }
            }
        }
        return result;
    }

    private CustomerResolutionDTO resolveCustomerForPreview(Customer sourceCustomer, Long targetFirmId) {
        if (sourceCustomer == null) {
            return CustomerResolutionDTO.builder()
                    .matchType("NO_MATCH")
                    .build();
        }

        List<CustomerCandidateDTO> candidates = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();

        // 1. Match by GSTIN
        if (sourceCustomer.getGstin() != null && !sourceCustomer.getGstin().trim().isEmpty()) {
            List<Customer> byGstin = customerRepo.findByFirmIdAndGstinIgnoreCase(targetFirmId, sourceCustomer.getGstin().trim());
            if (byGstin != null) {
                for (Customer c : byGstin) {
                    if (seenIds.add(c.getId())) {
                        candidates.add(toCustomerCandidate(c, "GSTIN"));
                    }
                }
            }
        }

        // 2. Match by Phone
        if (sourceCustomer.getPhone() != null && !sourceCustomer.getPhone().trim().isEmpty()) {
            List<Customer> byPhone = customerRepo.findByFirmIdAndPhone(targetFirmId, sourceCustomer.getPhone().trim());
            if (byPhone != null) {
                for (Customer c : byPhone) {
                    if (seenIds.add(c.getId())) {
                        candidates.add(toCustomerCandidate(c, "PHONE"));
                    }
                }
            }
        }

        // 3. Match by Name
        if (sourceCustomer.getName() != null && !sourceCustomer.getName().trim().isEmpty()) {
            List<Customer> byName = customerRepo.findByFirmIdAndNameIgnoreCase(targetFirmId, sourceCustomer.getName().trim());
            if (byName != null) {
                for (Customer c : byName) {
                    if (seenIds.add(c.getId())) {
                        candidates.add(toCustomerCandidate(c, "NAME"));
                    }
                }
            }
        }

        if (candidates.size() == 1) {
            return CustomerResolutionDTO.builder()
                    .matchType("EXACT_MATCH")
                    .matchedCustomer(candidates.get(0))
                    .candidates(candidates)
                    .build();
        } else if (candidates.size() > 1) {
            return CustomerResolutionDTO.builder()
                    .matchType("AMBIGUOUS")
                    .candidates(candidates)
                    .build();
        } else {
            return CustomerResolutionDTO.builder()
                    .matchType("NO_MATCH")
                    .candidates(Collections.emptyList())
                    .build();
        }
    }

    private CustomerCandidateDTO toCustomerCandidate(Customer c, String reason) {
        return CustomerCandidateDTO.builder()
                .customerId(c.getId())
                .name(c.getName())
                .phone(c.getPhone())
                .email(c.getEmail())
                .address(c.getAddress())
                .gstin(c.getGstin())
                .matchReason(reason)
                .build();
    }

    private Customer resolveOrCreateCustomer(Customer sourceCustomer, Long targetFirmId, InvoiceMigrationRequest request) {
        if (request.getResolvedCustomerId() != null) {
            return customerRepo.findByIdAndFirmId(request.getResolvedCustomerId(), targetFirmId)
                    .orElseThrow(() -> new IllegalArgumentException("Resolved customer " + request.getResolvedCustomerId() + " does not belong to target firm " + targetFirmId));
        }

        if (sourceCustomer == null) {
            return null;
        }

        // Match automatically if exact match
        CustomerResolutionDTO preview = resolveCustomerForPreview(sourceCustomer, targetFirmId);
        if ("EXACT_MATCH".equals(preview.getMatchType()) && preview.getMatchedCustomer() != null) {
            return customerRepo.findByIdAndFirmId(preview.getMatchedCustomer().getCustomerId(), targetFirmId).orElse(null);
        }

        // Otherwise auto-create customer in target firm if opted (default true)
        if (Boolean.TRUE.equals(request.getCreateCustomerIfMissing())) {
            Customer newCust = Customer.builder()
                    .name(sourceCustomer.getName())
                    .phone(sourceCustomer.getPhone())
                    .email(sourceCustomer.getEmail())
                    .address(sourceCustomer.getAddress())
                    .gstin(sourceCustomer.getGstin())
                    .firmId(targetFirmId)
                    .build();
            return customerRepo.save(newCust);
        }

        return null;
    }

    private Product resolveOrCreateProduct(InvoiceItem item, Long targetFirmId, InvoiceMigrationRequest request) {
        if (item == null) {
            return null;
        }

        // 1. Explicit user selection if provided in request
        if (request != null && request.getProductSelections() != null) {
            ProductResolutionSelection sel = request.getProductSelections().stream()
                    .filter(s -> s.getItemId() != null && s.getItemId().equals(item.getId()))
                    .findFirst()
                    .orElse(null);
            if (sel != null && sel.getTargetProductId() != null) {
                return productRepo.findByIdAndFirmId(sel.getTargetProductId(), targetFirmId)
                        .orElseThrow(() -> new IllegalArgumentException("Target product " + sel.getTargetProductId() + " does not belong to firm " + targetFirmId));
            }
        }

        // 2. Exact match in target firm by SKU or Name
        Product matched = findBestMatchingProduct(item, targetFirmId);
        if (matched != null) {
            return matched;
        }

        // 3. Auto-create new UNCATEGORIZED Product in target firm
        Product src = item.getProduct();
        String prodName = (item.getProductName() != null && !item.getProductName().trim().isEmpty())
                ? item.getProductName().trim()
                : (src != null && src.getName() != null ? src.getName() : "Item");

        // Validate SKU uniqueness in target firm before copying
        String sku = null;
        if (src != null && src.getSku() != null && !src.getSku().trim().isEmpty()) {
            String srcSku = src.getSku().trim();
            if (productRepo.findFirstByFirmIdAndSku(targetFirmId, srcSku).isEmpty()) {
                sku = srcSku;
            }
        }

        String barcode = (src != null && src.getBarcode() != null && !src.getBarcode().trim().isEmpty())
                ? src.getBarcode().trim()
                : null;

        String itemType = (src != null && src.getItemType() != null && !src.getItemType().trim().isEmpty())
                ? src.getItemType()
                : "GOODS";

        String unit = (item.getUnit() != null && !item.getUnit().trim().isEmpty())
                ? item.getUnit().trim()
                : (src != null && src.getUnit() != null ? src.getUnit() : "pcs");

        String hsn = (item.getHsnCode() != null && !item.getHsnCode().trim().isEmpty())
                ? item.getHsnCode().trim()
                : (src != null ? src.getHsnCode() : null);

        BigDecimal gst = item.getGstPercent() != null
                ? item.getGstPercent()
                : (src != null ? src.getGstPercentage() : null);

        BigDecimal price = item.getPricePerUnit() != null
                ? item.getPricePerUnit()
                : (src != null && src.getPrice() != null ? src.getPrice() : BigDecimal.ZERO);

        BigDecimal costPrice = (src != null) ? src.getCostPrice() : null;

        Product newProduct = Product.builder()
                .firmId(targetFirmId)
                .name(prodName)
                .category("UNCATEGORIZED")
                .itemType(itemType)
                .price(price)
                .costPrice(costPrice)
                .unit(unit)
                .hsnCode(hsn)
                .gstPercentage(gst)
                .sku(sku)
                .barcode(barcode)
                .stockQuantity(BigDecimal.ZERO)
                .minStockLevel(new BigDecimal("5.000"))
                .inInventory(false)
                .isArchived(false)
                .build();

        return productRepo.save(newProduct);
    }

    private ProductResolutionDTO resolveItemForPreview(InvoiceItem item, Long targetFirmId) {
        if (item == null) {
            return null;
        }

        BigDecimal qty = item.getQty() != null ? BigDecimal.valueOf(item.getQty()) : BigDecimal.ZERO;
        Product sourceProduct = item.getProduct();

        List<ProductCandidateDTO> candidates = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();

        // Match by SKU
        if (sourceProduct != null && sourceProduct.getSku() != null && !sourceProduct.getSku().trim().isEmpty()) {
            List<Product> bySku = productRepo.findByFirmIdAndSku(targetFirmId, sourceProduct.getSku().trim());
            if (bySku != null) {
                for (Product p : bySku) {
                    if (seenIds.add(p.getId())) {
                        candidates.add(toProductCandidate(p, "SKU"));
                    }
                }
            }
        }

        // Match by Name
        String nameToMatch = (item.getProductName() != null && !item.getProductName().trim().isEmpty())
                ? item.getProductName().trim()
                : (sourceProduct != null ? sourceProduct.getName() : null);
        if (nameToMatch != null && !nameToMatch.trim().isEmpty()) {
            List<Product> byName = productRepo.findByFirmIdAndNameIgnoreCase(targetFirmId, nameToMatch.trim());
            if (byName != null) {
                for (Product p : byName) {
                    if (seenIds.add(p.getId())) {
                        candidates.add(toProductCandidate(p, "NAME"));
                    }
                }
            }
        }

        if (candidates.size() == 1) {
            ProductCandidateDTO cand = candidates.get(0);
            boolean isService = "SERVICE".equalsIgnoreCase(cand.getItemType());
            return ProductResolutionDTO.builder()
                    .itemId(item.getId())
                    .originalProductName(item.getProductName())
                    .qty(item.getQty())
                    .pricePerUnit(item.getPricePerUnit())
                    .lineTotal(item.getLineTotal())
                    .resolutionType("INVENTORY_PRODUCT")
                    .matchedProduct(cand)
                    .candidates(candidates)
                    .stockDeduction(isService ? BigDecimal.ZERO : qty)
                    .build();
        } else if (candidates.size() > 1) {
            return ProductResolutionDTO.builder()
                    .itemId(item.getId())
                    .originalProductName(item.getProductName())
                    .qty(item.getQty())
                    .pricePerUnit(item.getPricePerUnit())
                    .lineTotal(item.getLineTotal())
                    .resolutionType("AMBIGUOUS")
                    .candidates(candidates)
                    .stockDeduction(BigDecimal.ZERO)
                    .build();
        } else {
            boolean isService = sourceProduct != null && "SERVICE".equalsIgnoreCase(sourceProduct.getItemType());
            return ProductResolutionDTO.builder()
                    .itemId(item.getId())
                    .originalProductName(item.getProductName())
                    .qty(item.getQty())
                    .pricePerUnit(item.getPricePerUnit())
                    .lineTotal(item.getLineTotal())
                    .resolutionType("AUTO_CREATE_PRODUCT")
                    .candidates(Collections.emptyList())
                    .stockDeduction(isService ? BigDecimal.ZERO : qty)
                    .build();
        }
    }

    private ProductCandidateDTO toProductCandidate(Product p, String reason) {
        return ProductCandidateDTO.builder()
                .productId(p.getId())
                .name(p.getName())
                .sku(p.getSku())
                .currentStock(p.getStockQuantity())
                .price(p.getPrice())
                .itemType(p.getItemType())
                .matchReason(reason)
                .build();
    }

    private Product findBestMatchingProduct(InvoiceItem item, Long targetFirmId) {
        if (item == null || item.getProduct() == null) {
            return null;
        }
        Product src = item.getProduct();
        if (src.getSku() != null && !src.getSku().trim().isEmpty()) {
            List<Product> bySku = productRepo.findByFirmIdAndSku(targetFirmId, src.getSku().trim());
            if (bySku != null && bySku.size() == 1) {
                return bySku.get(0);
            }
        }
        if (src.getName() != null && !src.getName().trim().isEmpty()) {
            List<Product> byName = productRepo.findByFirmIdAndNameIgnoreCase(targetFirmId, src.getName().trim());
            if (byName != null && byName.size() == 1) {
                return byName.get(0);
            }
        }
        return null;
    }

    private String getFirmName(Long firmId) {
        if (firmId == null) return "Unknown";
        return firmDetailsRepo.findById(firmId)
                .map(FirmDetails::getFirmName)
                .orElse("Firm #" + firmId);
    }

    private String estimateNextInvoiceNumber(Long targetFirmId) {
        return invoiceService.peekNextInvoiceNumber(targetFirmId);
    }
}
