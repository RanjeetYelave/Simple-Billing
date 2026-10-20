package com.billing.simple.billsoft.service.impl;

import com.billing.simple.billsoft.dtos.CustomerBalanceSummary;
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.InvoicePayment;
import com.billing.simple.billsoft.entities.InvoiceStatus;
import com.billing.simple.billsoft.entities.SalesReturn;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repo.InvoicePaymentRepository;
import com.billing.simple.billsoft.repo.InvoiceRepository;
import com.billing.simple.billsoft.repo.SalesReturnRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;
import com.billing.simple.billsoft.service.CustomerBalanceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Authoritative implementation of CustomerBalanceService.
 * Single source of truth for customer ledger debt calculations across all modules.
 * Enforces canonical formula:
 * Closing Balance = Active Issued Invoices - Payments (allocated + unallocated) - Sales Returns
 */
@Service
@Transactional(readOnly = true)
public class CustomerBalanceServiceImpl implements CustomerBalanceService {

    private static final int SCALE = 2;

    private final CustomerRepository customerRepo;
    private final InvoiceRepository invoiceRepo;
    private final InvoicePaymentRepository invoicePaymentRepo;
    private final SalesReturnRepository salesReturnRepo;

    public CustomerBalanceServiceImpl(
            CustomerRepository customerRepo,
            InvoiceRepository invoiceRepo,
            InvoicePaymentRepository invoicePaymentRepo,
            SalesReturnRepository salesReturnRepo) {
        this.customerRepo = customerRepo;
        this.invoiceRepo = invoiceRepo;
        this.invoicePaymentRepo = invoicePaymentRepo;
        this.salesReturnRepo = salesReturnRepo;
    }

    @Override
    public CustomerBalanceSummary getCustomerBalance(Long customerId) {
        Long authoritativeFirmId = TenantContext.getCurrentFirmId();
        if (authoritativeFirmId == null) {
            throw new TenantSecurityException("Active firm context is required for customer balance operations");
        }
        return getCustomerBalance(authoritativeFirmId, customerId);
    }

    @Override
    public CustomerBalanceSummary getCustomerBalance(Long firmId, Long customerId) {
        Long authoritativeFirmId = TenantContext.getCurrentFirmId();
        if (authoritativeFirmId != null && firmId != null && !authoritativeFirmId.equals(firmId)) {
            throw new TenantSecurityException("Cross-firm access prohibited");
        }
        Long targetFirmId = authoritativeFirmId != null ? authoritativeFirmId : firmId;
        if (targetFirmId == null) {
            throw new TenantSecurityException("Active firm context is required for customer balance operations");
        }

        Customer customer = customerRepo.findByIdAndFirmId(customerId, targetFirmId)
                .orElseThrow(() -> new TenantSecurityException("Customer #" + customerId + " not found in firm"));

        List<Invoice> allInvoices = invoiceRepo.findByFirmIdAndCustomer_Id(targetFirmId, customerId);
        List<InvoicePayment> allPayments = invoicePaymentRepo.findByFirmIdAndCustomerIdOrderByPaymentDateAscIdAsc(targetFirmId, customerId);
        List<SalesReturn> allReturns = salesReturnRepo.findByFirmIdAndCustomerIdOrderByReturnDateAscIdAsc(targetFirmId, customerId);

        // 1. Filter active issued invoices (strictly exclude DRAFT, ESTIMATE, CANCELLED)
        List<Invoice> activeInvoices = allInvoices.stream()
                .filter(i -> i.getInvoiceDate() != null)
                .filter(i -> i.getStatus() != InvoiceStatus.ESTIMATE)
                .filter(i -> i.getStatus() != InvoiceStatus.DRAFT)
                .filter(i -> i.getStatus() != InvoiceStatus.CANCELLED)
                .collect(Collectors.toList());

        // 2. Debits = Sum of active issued invoices
        BigDecimal totalBilled = activeInvoices.stream()
                .map(i -> nz(i.getTotalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 3. Credits from payments
        Set<Long> invoicesWithPayments = allPayments.stream()
                .map(InvoicePayment::getInvoiceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        BigDecimal paymentsSum = allPayments.stream()
                .map(p -> nz(p.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Legacy fallback: direct-paid invoices without child payment records
        BigDecimal directPaidSum = activeInvoices.stream()
                .filter(i -> Boolean.TRUE.equals(i.getPaid()) && !invoicesWithPayments.contains(i.getId()))
                .map(i -> nz(i.getTotalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPaid = paymentsSum.add(directPaidSum);

        // 4. Credits from sales return refund notes
        BigDecimal totalReturns = (allReturns != null)
                ? allReturns.stream()
                        .map(sr -> nz(sr.getTotalRefundAmount()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                : BigDecimal.ZERO;

        // 5. Authoritative closing balance (negative represents customer credit/advance)
        BigDecimal closingBalance = totalBilled.subtract(totalPaid).subtract(totalReturns);

        return CustomerBalanceSummary.builder()
                .customerId(customer.getId())
                .firmId(targetFirmId)
                .totalBilled(totalBilled.setScale(SCALE, RoundingMode.HALF_UP))
                .totalPaid(totalPaid.setScale(SCALE, RoundingMode.HALF_UP))
                .totalReturns(totalReturns.setScale(SCALE, RoundingMode.HALF_UP))
                .closingBalance(closingBalance.setScale(SCALE, RoundingMode.HALF_UP))
                .build();
    }

    private BigDecimal nz(BigDecimal val) {
        return val == null ? BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP) : val.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
