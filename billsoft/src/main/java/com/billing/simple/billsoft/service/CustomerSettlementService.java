package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dtos.*;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repo.InvoiceItemRepository;
import com.billing.simple.billsoft.repo.InvoicePaymentRepository;
import com.billing.simple.billsoft.repo.InvoiceRepository;
import com.billing.simple.billsoft.repo.SalesReturnRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CustomerSettlementService {

    private final CustomerRepository customerRepo;
    private final InvoiceRepository invoiceRepo;
    private final InvoicePaymentRepository invoicePaymentRepo;
    private final SalesReturnRepository salesReturnRepo;
    private final javax.sql.DataSource dataSource;

    private static final int SCALE = 2;

    public CustomerSettlementService(
            CustomerRepository customerRepo,
            InvoiceRepository invoiceRepo,
            InvoicePaymentRepository invoicePaymentRepo,
            SalesReturnRepository salesReturnRepo,
            javax.sql.DataSource dataSource) {
        this.customerRepo = customerRepo;
        this.invoiceRepo = invoiceRepo;
        this.invoicePaymentRepo = invoicePaymentRepo;
        this.salesReturnRepo = salesReturnRepo;
        this.dataSource = dataSource;
    }

    @jakarta.annotation.PostConstruct
    public void initSchema() {
        if (dataSource != null) {
            try (java.sql.Connection conn = dataSource.getConnection();
                 java.sql.Statement stmt = conn.createStatement()) {
                try {
                    stmt.execute("ALTER TABLE invoice_payments ALTER COLUMN invoice_id DROP NOT NULL");
                } catch (Exception ignored) {
                }
                try {
                    stmt.execute("ALTER TABLE invoice_payments ALTER COLUMN invoice_id SET NULL");
                } catch (Exception ignored) {
                }
            } catch (Exception e) {
                System.err.println("Note on invoice_payments table schema: " + e.getMessage());
            }
        }
    }

    private Long resolveFirmId() {
        Long fid = TenantContext.getCurrentFirmId();
        if (fid == null) {
            throw new TenantSecurityException("Active firm context is required for customer settlement operations");
        }
        return fid;
    }

    private BigDecimal nz(BigDecimal val) {
        return val == null ? BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP) : val.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Complete Customer 360 overview data loaded in O(1) customer scope without N+1 queries.
     */
    @Transactional(readOnly = true)
    public Customer360Response getCustomer360(Long customerId) {
        Long firmId = resolveFirmId();
        Customer customer = customerRepo.findByIdAndFirmId(customerId, firmId)
                .orElseThrow(() -> new TenantSecurityException("Customer #" + customerId + " not found in firm"));

        List<Invoice> allInvoices = invoiceRepo.findByFirmIdAndCustomer_Id(firmId, customerId);
        List<InvoicePayment> allPayments = invoicePaymentRepo.findByFirmIdAndCustomerIdOrderByPaymentDateAscIdAsc(firmId, customerId);
        List<SalesReturn> allReturns = salesReturnRepo.findByFirmIdAndCustomerIdOrderByReturnDateAscIdAsc(firmId, customerId);

        // Map returns and payments by invoice
        Map<Long, BigDecimal> returnsByInvoice = new HashMap<>();
        for (SalesReturn sr : allReturns) {
            if (sr.getInvoice() != null && sr.getInvoice().getId() != null && sr.getTotalRefundAmount() != null) {
                returnsByInvoice.merge(sr.getInvoice().getId(), sr.getTotalRefundAmount(), BigDecimal::add);
            }
        }

        Map<Long, BigDecimal> paymentsByInvoice = new HashMap<>();
        BigDecimal unallocatedCredit = BigDecimal.ZERO;
        for (InvoicePayment p : allPayments) {
            if (p.getInvoiceId() != null && p.getAmount() != null) {
                paymentsByInvoice.merge(p.getInvoiceId(), p.getAmount(), BigDecimal::add);
            } else if (p.getInvoiceId() == null && p.getAmount() != null) {
                unallocatedCredit = unallocatedCredit.add(p.getAmount());
            }
        }

        LocalDate today = LocalDate.now();
        List<CustomerOutstandingInvoiceDto> outstandingList = new ArrayList<>();
        List<CustomerInvoiceSummary> recentInvoices = new ArrayList<>();

        BigDecimal totalBilled = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        BigDecimal overdueAmount = BigDecimal.ZERO;
        long unpaidCount = 0;
        long overdueCount = 0;
        long paidCount = 0;
        long validInvoiceCount = 0;

        // Process valid active invoices (ignoring ESTIMATE and CANCELLED)
        for (Invoice inv : allInvoices) {
            if (inv.getStatus() == InvoiceStatus.ESTIMATE || inv.getStatus() == InvoiceStatus.CANCELLED) {
                continue;
            }
            validInvoiceCount++;

            BigDecimal rawTotal = nz(inv.getTotalAmount());
            BigDecimal retAmt = returnsByInvoice.getOrDefault(inv.getId(), BigDecimal.ZERO);
            BigDecimal effectiveReceivable = rawTotal.subtract(retAmt);
            if (effectiveReceivable.compareTo(BigDecimal.ZERO) < 0) {
                effectiveReceivable = BigDecimal.ZERO;
            }

            BigDecimal paidForInv = paymentsByInvoice.getOrDefault(inv.getId(), BigDecimal.ZERO);
            if (paidForInv.compareTo(BigDecimal.ZERO) == 0 && Boolean.TRUE.equals(inv.getPaid())) {
                paidForInv = effectiveReceivable;
            }
            if (paidForInv.compareTo(effectiveReceivable) > 0) {
                paidForInv = effectiveReceivable;
            }

            BigDecimal outstanding = effectiveReceivable.subtract(paidForInv);
            if (outstanding.compareTo(BigDecimal.ZERO) < 0) {
                outstanding = BigDecimal.ZERO;
            }

            totalBilled = totalBilled.add(effectiveReceivable);
            totalPaid = totalPaid.add(paidForInv);

            boolean isOverdue = (inv.getDueDate() != null && inv.getDueDate().isBefore(today) && outstanding.compareTo(BigDecimal.ZERO) > 0);

            if (outstanding.compareTo(BigDecimal.ZERO) > 0) {
                unpaidCount++;
                if (isOverdue) {
                    overdueCount++;
                    overdueAmount = overdueAmount.add(outstanding);
                }
                outstandingList.add(CustomerOutstandingInvoiceDto.builder()
                        .invoiceId(inv.getId())
                        .invoiceNumber(inv.getInvoiceNumber() != null ? inv.getInvoiceNumber() : "INV-" + inv.getId())
                        .invoiceDate(inv.getInvoiceDate())
                        .dueDate(inv.getDueDate())
                        .totalAmount(rawTotal)
                        .returnedAmount(retAmt)
                        .paidAmount(paidForInv)
                        .outstandingAmount(outstanding)
                        .status(inv.getStatus())
                        .isOverdue(isOverdue)
                        .build());
            } else {
                paidCount++;
            }

            CustomerInvoiceSummary summary = new CustomerInvoiceSummary();
            summary.setInvoiceId(inv.getId());
            summary.setInvoiceNumber(inv.getInvoiceNumber());
            summary.setInvoiceDate(inv.getInvoiceDate() != null ? inv.getInvoiceDate().toString() : null);
            summary.setTotalAmount(rawTotal.doubleValue());
            summary.setPaid(outstanding.compareTo(BigDecimal.ZERO) <= 0);
            recentInvoices.add(summary);
        }

        // Add pure advance unallocated payments to totalPaid
        totalPaid = totalPaid.add(unallocatedCredit);

        BigDecimal netBalance = totalBilled.subtract(totalPaid);

        // Sort outstanding: Overdue first, then by oldest invoiceDate
        outstandingList.sort(Comparator.comparing(CustomerOutstandingInvoiceDto::getIsOverdue).reversed()
                .thenComparing(dto -> dto.getInvoiceDate() != null ? dto.getInvoiceDate() : LocalDateTime.MIN));

        // Sort recent invoices newest first (limit 10)
        recentInvoices.sort(Comparator.comparing(CustomerInvoiceSummary::getInvoiceDate, Comparator.nullsLast(Comparator.reverseOrder())));
        if (recentInvoices.size() > 10) {
            recentInvoices = recentInvoices.subList(0, 10);
        }

        // Recent payments newest first (limit 10)
        List<InvoicePayment> recentPayments = new ArrayList<>(allPayments);
        recentPayments.sort(Comparator.comparing(InvoicePayment::getPaymentDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Comparator.comparing(InvoicePayment::getId).reversed()));
        if (recentPayments.size() > 10) {
            recentPayments = recentPayments.subList(0, 10);
        }

        return Customer360Response.builder()
                .customer(customer)
                .totalBilled(totalBilled.setScale(SCALE, RoundingMode.HALF_UP).doubleValue())
                .totalPaid(totalPaid.setScale(SCALE, RoundingMode.HALF_UP).doubleValue())
                .netBalance(netBalance.setScale(SCALE, RoundingMode.HALF_UP).doubleValue())
                .unallocatedCredit(unallocatedCredit.setScale(SCALE, RoundingMode.HALF_UP).doubleValue())
                .overdueAmount(overdueAmount.setScale(SCALE, RoundingMode.HALF_UP).doubleValue())
                .invoiceCount(validInvoiceCount)
                .unpaidInvoiceCount(unpaidCount)
                .overdueInvoiceCount(overdueCount)
                .paidInvoiceCount(paidCount)
                .outstandingInvoices(outstandingList)
                .recentInvoices(recentInvoices)
                .recentPayments(recentPayments)
                .build();
    }

    /**
     * Get only outstanding invoices for a customer with live breakdown.
     */
    @Transactional(readOnly = true)
    public List<CustomerOutstandingInvoiceDto> getOutstandingInvoices(Long customerId) {
        Customer360Response c360 = getCustomer360(customerId);
        return c360.getOutstandingInvoices();
    }

    /**
     * Get all payment history for a customer (both invoice-linked and unallocated advance credits).
     */
    @Transactional(readOnly = true)
    public List<InvoicePayment> getCustomerPayments(Long customerId) {
        Long firmId = resolveFirmId();
        customerRepo.findByIdAndFirmId(customerId, firmId)
                .orElseThrow(() -> new TenantSecurityException("Customer #" + customerId + " not found in firm"));
        return invoicePaymentRepo.findByFirmIdAndCustomerIdOrderByPaymentDateAscIdAsc(firmId, customerId);
    }

    /**
     * Atomic Multi-Invoice Settlement and Payment Allocation Engine.
     * Enforces financial invariants, tenant isolation, and status reconciliation.
     */
    @Transactional(rollbackFor = Exception.class)
    public CustomerSettlementResponse settleInvoices(Long customerId, CustomerSettlementRequest request) {
        Long firmId = resolveFirmId();
        Customer customer = customerRepo.findByIdAndFirmId(customerId, firmId)
                .orElseThrow(() -> new TenantSecurityException("Customer #" + customerId + " not found in firm"));

        if (request == null) {
            throw new IllegalArgumentException("Settlement request cannot be null");
        }

        List<InvoiceAllocationItem> allocations = request.getAllocations() != null ? request.getAllocations() : Collections.emptyList();
        BigDecimal totalAllocated = BigDecimal.ZERO;

        for (InvoiceAllocationItem item : allocations) {
            if (item.getAmount() == null || item.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Allocation amount must be strictly greater than 0");
            }
            totalAllocated = totalAllocated.add(item.getAmount().setScale(SCALE, RoundingMode.HALF_UP));
        }

        List<InvoicePayment> paymentsCreated = new ArrayList<>();
        Set<Long> affectedInvoiceIds = new HashSet<>();

        // CASE 1: Allocating from Existing Unallocated Advance Credit
        if (request.getUnallocatedPaymentId() != null) {
            if (allocations.isEmpty()) {
                throw new IllegalArgumentException("At least one invoice allocation is required when allocating existing credit");
            }
            InvoicePayment advancePmt = invoicePaymentRepo.findByIdAndFirmId(request.getUnallocatedPaymentId(), firmId)
                    .orElseThrow(() -> new TenantSecurityException("Advance credit payment #" + request.getUnallocatedPaymentId() + " not found"));

            if (!Objects.equals(advancePmt.getCustomerId(), customerId)) {
                throw new IllegalArgumentException("Advance credit #" + advancePmt.getId() + " does not belong to customer #" + customerId);
            }
            if (advancePmt.getInvoiceId() != null) {
                throw new IllegalArgumentException("Payment #" + advancePmt.getId() + " is already allocated to Invoice #" + advancePmt.getInvoiceId());
            }
            if (advancePmt.getAmount().compareTo(totalAllocated) < 0) {
                throw new IllegalArgumentException("Allocations total ₹" + totalAllocated + " exceeds available credit ₹" + advancePmt.getAmount());
            }

            BigDecimal remainingCredit = advancePmt.getAmount().subtract(totalAllocated);

            // Validate all targeted invoices first
            validateAllocations(allocations, customerId, firmId);

            // Apply allocations
            for (int i = 0; i < allocations.size(); i++) {
                InvoiceAllocationItem alloc = allocations.get(i);
                affectedInvoiceIds.add(alloc.getInvoiceId());

                if (i == 0 && remainingCredit.compareTo(BigDecimal.ZERO) == 0 && allocations.size() == 1) {
                    // Exactly 1 allocation consuming entire credit: directly link the existing payment
                    advancePmt.setInvoiceId(alloc.getInvoiceId());
                    advancePmt.setAmount(alloc.getAmount());
                    if (request.getNotes() != null) advancePmt.setNotes(request.getNotes());
                    invoicePaymentRepo.save(advancePmt);
                    paymentsCreated.add(advancePmt);
                } else {
                    // Create an allocated payment record
                    InvoicePayment p = InvoicePayment.builder()
                            .firmId(firmId)
                            .customerId(customerId)
                            .invoiceId(alloc.getInvoiceId())
                            .amount(alloc.getAmount())
                            .paymentDate(request.getPaymentDate() != null ? request.getPaymentDate() : advancePmt.getPaymentDate())
                            .paymentMode(request.getPaymentMode() != null ? request.getPaymentMode() : advancePmt.getPaymentMode())
                            .referenceNumber(advancePmt.getReferenceNumber())
                            .notes((request.getNotes() != null ? request.getNotes() : "") + " (Allocated from Advance #" + advancePmt.getId() + ")")
                            .build();
                    paymentsCreated.add(invoicePaymentRepo.save(p));
                }
            }

            if (allocations.size() > 1 || (allocations.size() == 1 && remainingCredit.compareTo(BigDecimal.ZERO) > 0)) {
                if (remainingCredit.compareTo(BigDecimal.ZERO) > 0) {
                    advancePmt.setAmount(remainingCredit);
                    invoicePaymentRepo.save(advancePmt);
                } else {
                    invoicePaymentRepo.delete(advancePmt);
                }
            }
        }
        // CASE 2: New Payment Received with Live Distribution / Advance
        else {
            BigDecimal paymentAmount = request.getAmount() != null ? request.getAmount().setScale(SCALE, RoundingMode.HALF_UP) : null;
            if (paymentAmount == null || paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Payment amount must be greater than 0");
            }
            if (totalAllocated.compareTo(paymentAmount) > 0) {
                throw new IllegalArgumentException("Total allocations (₹" + totalAllocated + ") cannot exceed total payment amount (₹" + paymentAmount + ")");
            }

            LocalDate pDate = request.getPaymentDate() != null ? request.getPaymentDate() : LocalDate.now();
            String pMode = request.getPaymentMode() != null ? request.getPaymentMode() : "Cash";

            // Validate all targeted invoices first
            if (!allocations.isEmpty()) {
                validateAllocations(allocations, customerId, firmId);
            }

            // Create allocated payment records
            for (InvoiceAllocationItem alloc : allocations) {
                affectedInvoiceIds.add(alloc.getInvoiceId());
                InvoicePayment p = InvoicePayment.builder()
                        .firmId(firmId)
                        .customerId(customerId)
                        .invoiceId(alloc.getInvoiceId())
                        .amount(alloc.getAmount())
                        .paymentDate(pDate)
                        .paymentMode(pMode)
                        .referenceNumber(request.getReferenceNumber())
                        .notes(request.getNotes())
                        .build();
                paymentsCreated.add(invoicePaymentRepo.save(p));
            }

            // If payment exceeds total allocated amount, retain remainder as unallocated advance credit
            BigDecimal unallocatedRemainder = paymentAmount.subtract(totalAllocated);
            if (unallocatedRemainder.compareTo(BigDecimal.ZERO) > 0) {
                String refSuffix = request.getReferenceNumber() != null ? request.getReferenceNumber() : "";
                InvoicePayment unallocPmt = InvoicePayment.builder()
                        .firmId(firmId)
                        .customerId(customerId)
                        .invoiceId(null) // Unallocated
                        .amount(unallocatedRemainder)
                        .paymentDate(pDate)
                        .paymentMode(pMode)
                        .referenceNumber(refSuffix)
                        .notes((request.getNotes() != null ? request.getNotes() + " " : "") + "(Unallocated Advance Credit)")
                        .build();
                paymentsCreated.add(invoicePaymentRepo.save(unallocPmt));
            }
        }

        // Reconcile status and paid flag for all affected invoices
        List<CustomerOutstandingInvoiceDto> updatedInvoices = new ArrayList<>();
        for (Long invId : affectedInvoiceIds) {
            Invoice inv = invoiceRepo.findByIdAndFirmId(invId, firmId)
                    .orElseThrow(() -> new TenantSecurityException("Invoice #" + invId + " not found"));

            List<InvoicePayment> allInvPayments = invoicePaymentRepo.findByInvoiceIdAndFirmIdOrderByPaymentDateAscIdAsc(invId, firmId);
            BigDecimal paidSum = allInvPayments.stream()
                    .map(InvoicePayment::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            List<SalesReturn> returns = salesReturnRepo.findByInvoiceIdAndFirmIdOrderByCreatedAtDesc(invId, firmId);
            BigDecimal returnSum = returns.stream()
                    .map(SalesReturn::getTotalRefundAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal invTotal = nz(inv.getTotalAmount());
            BigDecimal effectiveReceivable = invTotal.subtract(returnSum);
            if (effectiveReceivable.compareTo(BigDecimal.ZERO) < 0) {
                effectiveReceivable = BigDecimal.ZERO;
            }

            boolean isFullyPaid = (paidSum.compareTo(effectiveReceivable) >= 0 && effectiveReceivable.compareTo(BigDecimal.ZERO) > 0)
                    || (effectiveReceivable.compareTo(BigDecimal.ZERO) == 0 && invTotal.compareTo(BigDecimal.ZERO) > 0);

            inv.setPaid(isFullyPaid);
            if (isFullyPaid) {
                inv.setStatus(InvoiceStatus.PAID);
            } else if (inv.getStatus() == InvoiceStatus.PAID && !isFullyPaid) {
                inv.setStatus(InvoiceStatus.UNPAID);
            }
            invoiceRepo.save(inv);

            BigDecimal remainingOutstanding = effectiveReceivable.subtract(paidSum);
            if (remainingOutstanding.compareTo(BigDecimal.ZERO) < 0) {
                remainingOutstanding = BigDecimal.ZERO;
            }

            updatedInvoices.add(CustomerOutstandingInvoiceDto.builder()
                    .invoiceId(inv.getId())
                    .invoiceNumber(inv.getInvoiceNumber())
                    .invoiceDate(inv.getInvoiceDate())
                    .dueDate(inv.getDueDate())
                    .totalAmount(invTotal)
                    .returnedAmount(returnSum)
                    .paidAmount(paidSum)
                    .outstandingAmount(remainingOutstanding)
                    .status(inv.getStatus())
                    .isOverdue(inv.getDueDate() != null && inv.getDueDate().isBefore(LocalDate.now()) && remainingOutstanding.compareTo(BigDecimal.ZERO) > 0)
                    .build());
        }

        BigDecimal totalPaymentAmt = request.getAmount() != null ? request.getAmount() : totalAllocated;
        BigDecimal unallocAmt = totalPaymentAmt.subtract(totalAllocated);
        if (unallocAmt.compareTo(BigDecimal.ZERO) < 0) {
            unallocAmt = BigDecimal.ZERO;
        }

        return CustomerSettlementResponse.builder()
                .customerId(customerId)
                .totalAmount(totalPaymentAmt)
                .totalAllocated(totalAllocated)
                .unallocatedCredit(unallocAmt)
                .paymentsCreated(paymentsCreated)
                .invoicesUpdated(updatedInvoices)
                .build();
    }

    private void validateAllocations(List<InvoiceAllocationItem> allocations, Long customerId, Long firmId) {
        for (InvoiceAllocationItem alloc : allocations) {
            Invoice inv = invoiceRepo.findByIdAndFirmId(alloc.getInvoiceId(), firmId)
                    .orElseThrow(() -> new TenantSecurityException("Invoice #" + alloc.getInvoiceId() + " not found or unauthorized"));

            if (inv.getCustomer() == null || !Objects.equals(inv.getCustomer().getId(), customerId)) {
                throw new IllegalArgumentException("Invoice #" + (inv.getInvoiceNumber() != null ? inv.getInvoiceNumber() : inv.getId()) + " does not belong to Customer #" + customerId);
            }

            if (inv.getStatus() == InvoiceStatus.CANCELLED) {
                throw new IllegalStateException("Cannot allocate payment to CANCELLED Invoice #" + inv.getInvoiceNumber());
            }
            if (inv.getStatus() == InvoiceStatus.DRAFT || inv.getStatus() == InvoiceStatus.ESTIMATE) {
                throw new IllegalStateException("Cannot allocate payment to DRAFT or ESTIMATE document #" + (inv.getEstimateNumber() != null ? inv.getEstimateNumber() : inv.getId()));
            }

            // Check outstanding
            List<InvoicePayment> pastPayments = invoicePaymentRepo.findByInvoiceIdAndFirmIdOrderByPaymentDateAscIdAsc(inv.getId(), firmId);
            BigDecimal paidSum = pastPayments.stream().map(InvoicePayment::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);

            List<SalesReturn> returns = salesReturnRepo.findByInvoiceIdAndFirmIdOrderByCreatedAtDesc(inv.getId(), firmId);
            BigDecimal returnSum = returns.stream().map(SalesReturn::getTotalRefundAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal effectiveReceivable = nz(inv.getTotalAmount()).subtract(returnSum);
            if (effectiveReceivable.compareTo(BigDecimal.ZERO) < 0) effectiveReceivable = BigDecimal.ZERO;

            BigDecimal currentOutstanding = effectiveReceivable.subtract(paidSum);
            if (currentOutstanding.compareTo(BigDecimal.ZERO) < 0) currentOutstanding = BigDecimal.ZERO;

            if (alloc.getAmount().compareTo(currentOutstanding.add(new BigDecimal("0.01"))) > 0) {
                throw new IllegalArgumentException("Allocation amount ₹" + alloc.getAmount() + " exceeds current outstanding ₹" + currentOutstanding + " on Invoice #" + (inv.getInvoiceNumber() != null ? inv.getInvoiceNumber() : inv.getId()));
            }
        }
    }

    /**
     * Delete/Reverse a payment with tenant isolation and invoice status reconciliation.
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean deletePayment(Long paymentId) {
        Long firmId = resolveFirmId();
        InvoicePayment p = invoicePaymentRepo.findByIdAndFirmId(paymentId, firmId)
                .orElse(null);
        if (p == null) {
            return false;
        }

        Long invoiceId = p.getInvoiceId();
        invoicePaymentRepo.delete(p);

        // If payment was linked to an invoice, recompute invoice status
        if (invoiceId != null) {
            Invoice inv = invoiceRepo.findByIdAndFirmId(invoiceId, firmId).orElse(null);
            if (inv != null) {
                List<InvoicePayment> remainingPayments = invoicePaymentRepo.findByInvoiceIdAndFirmIdOrderByPaymentDateAscIdAsc(invoiceId, firmId);
                BigDecimal paidSum = remainingPayments.stream()
                        .map(InvoicePayment::getAmount)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                List<SalesReturn> returns = salesReturnRepo.findByInvoiceIdAndFirmIdOrderByCreatedAtDesc(invoiceId, firmId);
                BigDecimal returnSum = returns.stream()
                        .map(SalesReturn::getTotalRefundAmount)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal effectiveReceivable = nz(inv.getTotalAmount()).subtract(returnSum);
                if (effectiveReceivable.compareTo(BigDecimal.ZERO) < 0) effectiveReceivable = BigDecimal.ZERO;

                boolean isFullyPaid = paidSum.compareTo(effectiveReceivable) >= 0 && effectiveReceivable.compareTo(BigDecimal.ZERO) > 0;
                inv.setPaid(isFullyPaid);
                if (!isFullyPaid && inv.getStatus() == InvoiceStatus.PAID) {
                    inv.setStatus(InvoiceStatus.UNPAID);
                }
                invoiceRepo.save(inv);
            }
        }

        return true;
    }
}
