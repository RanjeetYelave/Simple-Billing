package com.billing.simple.billsoft.kpi;

import com.billing.simple.billsoft.dtos.FirmAnalyticsResponse;
import com.billing.simple.billsoft.dtos.PartyFinancialSummary;
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.InvoicePayment;
import com.billing.simple.billsoft.entities.InvoiceStatus;
import com.billing.simple.billsoft.entities.SalesReturn;
import com.billing.simple.billsoft.kpi.dto.CustomerKpiSummary;
import com.billing.simple.billsoft.kpi.dto.KpiDashboardResponse;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repo.InvoicePaymentRepository;
import com.billing.simple.billsoft.repo.InvoiceRepository;
import com.billing.simple.billsoft.repo.SalesReturnRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;
import com.billing.simple.billsoft.service.ExpenseService;
import com.billing.simple.billsoft.service.InvoiceService;
import com.billing.simple.billsoft.service.PartyService;
import com.billing.simple.billsoft.service.ProductService;
import com.billing.simple.billsoft.service.SavingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of KpiService acting as a facade aggregator.
 * Strictly read-only, firm-isolated, with zero changes to underlying accounting engine.
 */
@Service
public class KpiServiceImpl implements KpiService {

    private static final Logger log = LoggerFactory.getLogger(KpiServiceImpl.class);

    private final InvoiceService invoiceService;
    private final PartyService partyService;
    private final ProductService productService;
    private final ExpenseService expenseService;
    private final SavingService savingService;

    private final CustomerRepository customerRepo;
    private final InvoiceRepository invoiceRepo;
    private final InvoicePaymentRepository invoicePaymentRepo;
    private final SalesReturnRepository salesReturnRepo;

    public KpiServiceImpl(
            InvoiceService invoiceService,
            PartyService partyService,
            ProductService productService,
            ExpenseService expenseService,
            SavingService savingService,
            CustomerRepository customerRepo,
            InvoiceRepository invoiceRepo,
            InvoicePaymentRepository invoicePaymentRepo,
            SalesReturnRepository salesReturnRepo
    ) {
        this.invoiceService = invoiceService;
        this.partyService = partyService;
        this.productService = productService;
        this.expenseService = expenseService;
        this.savingService = savingService;
        this.customerRepo = customerRepo;
        this.invoiceRepo = invoiceRepo;
        this.invoicePaymentRepo = invoicePaymentRepo;
        this.salesReturnRepo = salesReturnRepo;
    }

    @Override
    @Transactional(readOnly = true)
    public KpiDashboardResponse getDashboardKpis(Long firmId) {
        Long targetFirmId = resolveFirmId(firmId);

        // 1. Authoritative Revenue & Receivables from InvoiceService
        FirmAnalyticsResponse analytics = invoiceService.getFirmAnalytics(targetFirmId);

        // 2. Authoritative Vendor Payables from PartyService
        List<PartyFinancialSummary> partySummaries = partyService.getAllPartiesWithFinancialSummaries(targetFirmId);
        BigDecimal vendorPayables = BigDecimal.ZERO;
        long openPoCount = 0L;
        if (partySummaries != null) {
            for (PartyFinancialSummary ps : partySummaries) {
                if (ps.getNetBalance() != null && ps.getNetBalance().compareTo(BigDecimal.ZERO) > 0) {
                    vendorPayables = vendorPayables.add(ps.getNetBalance());
                }
                if (ps.getPendingPurchaseOrders() != null) {
                    openPoCount += ps.getPendingPurchaseOrders();
                }
            }
        }
        long totalVendors = partySummaries != null ? partySummaries.size() : 0L;

        // 3. Authoritative Inventory Summary from ProductService
        Map<String, Object> invSummary = productService.getInventorySummary(targetFirmId);

        // 4. Authoritative Expense Summary from ExpenseService
        Map<String, Object> expSummary = expenseService.getSummaryByFirm(targetFirmId);

        // 5. Authoritative Savings Summary from SavingService
        Map<String, Object> savSummary = savingService.getSummaryByFirm(targetFirmId);

        return KpiDashboardResponse.builder()
                .totalBusiness(analytics != null && analytics.getTotalBusiness() != null ? analytics.getTotalBusiness() : 0.0)
                .businessToday(analytics != null && analytics.getBusinessToday() != null ? analytics.getBusinessToday() : 0.0)
                .businessThisWeek(analytics != null && analytics.getBusinessThisWeek() != null ? analytics.getBusinessThisWeek() : 0.0)
                .businessThisMonth(analytics != null && analytics.getBusinessThisMonth() != null ? analytics.getBusinessThisMonth() : 0.0)
                .businessThisYear(analytics != null && analytics.getBusinessThisYear() != null ? analytics.getBusinessThisYear() : 0.0)
                .totalPaid(analytics != null && analytics.getTotalPaid() != null ? analytics.getTotalPaid() : 0.0)
                .totalPending(analytics != null && analytics.getTotalPending() != null ? analytics.getTotalPending() : 0.0)
                .unpaidInvoiceCount(analytics != null && analytics.getUnpaidInvoiceCount() != null ? analytics.getUnpaidInvoiceCount() : 0L)
                .overdueInvoiceCount(analytics != null && analytics.getOverdueInvoiceCount() != null ? analytics.getOverdueInvoiceCount() : 0L)
                .quotePipelineValue(analytics != null && analytics.getQuotePipelineValue() != null ? analytics.getQuotePipelineValue() : 0.0)
                .quoteCount(analytics != null && analytics.getQuoteCount() != null ? analytics.getQuoteCount() : 0L)
                .vendorPayables(vendorPayables.setScale(2, RoundingMode.HALF_UP).doubleValue())
                .openPoCount(openPoCount)
                .totalVendors(totalVendors)
                .totalInventoryCostValue(toDouble(invSummary != null ? invSummary.get("totalCostValue") : null))
                .totalInventoryRetailValue(toDouble(invSummary != null ? invSummary.get("totalRetailValue") : null))
                .lowStockCount(toLong(invSummary != null ? invSummary.get("lowStockCount") : null))
                .outOfStockCount(toLong(invSummary != null ? invSummary.get("outOfStockCount") : null))
                .totalProducts(toLong(invSummary != null ? invSummary.get("totalProducts") : null))
                .totalExpenses(toDouble(expSummary != null ? expSummary.get("totalAllTime") : null))
                .currentMonthExpenses(toDouble(expSummary != null ? expSummary.get("totalCurrentMonth") : null))
                .totalSavings(toDouble(savSummary != null ? savSummary.get("totalAllTime") : null))
                .currentMonthSavings(toDouble(savSummary != null ? savSummary.get("totalCurrentMonth") : null))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerKpiSummary> getCustomerKpiSummaries(Long firmId) {
        Long targetFirmId = resolveFirmId(firmId);

        List<Customer> customers = customerRepo.findByFirmIdOrderByNameAsc(targetFirmId);
        if (customers == null || customers.isEmpty()) {
            return Collections.emptyList();
        }

        List<InvoiceStatus> activeStatuses = List.of(
                InvoiceStatus.FINAL, InvoiceStatus.UNPAID,
                InvoiceStatus.PAID, InvoiceStatus.OVERDUE, InvoiceStatus.SENT
        );

        // Fetch firm-scoped records in 4 bulk queries (avoiding N+1 loops)
        List<Invoice> allInvoices = invoiceRepo.findInvoicesWithCustomerForAnalytics(targetFirmId, activeStatuses);
        List<InvoicePayment> allPayments = invoicePaymentRepo.findByFirmIdOrderByPaymentDateDescIdDesc(targetFirmId);
        List<SalesReturn> allReturns = salesReturnRepo.findByFirmIdOrderByReturnDateDescIdDesc(targetFirmId);

        Set<Long> invoicesWithPayments = allPayments.stream()
                .map(InvoicePayment::getInvoiceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Aggregate payments by customer
        Map<Long, BigDecimal> customerPayments = new HashMap<>();
        for (InvoicePayment p : allPayments) {
            if (p.getCustomerId() != null && p.getAmount() != null) {
                customerPayments.merge(p.getCustomerId(), p.getAmount(), BigDecimal::add);
            }
        }

        // Aggregate returns / credit notes by customer
        Map<Long, BigDecimal> customerReturns = new HashMap<>();
        for (SalesReturn sr : allReturns) {
            if (sr.getCustomer() != null && sr.getCustomer().getId() != null && sr.getTotalRefundAmount() != null) {
                customerReturns.merge(sr.getCustomer().getId(), sr.getTotalRefundAmount(), BigDecimal::add);
            }
        }

        // Aggregate invoices by customer
        Map<Long, BigDecimal> customerBilled = new HashMap<>();
        Map<Long, BigDecimal> customerDirectPaid = new HashMap<>();
        Map<Long, Long> customerInvoiceCount = new HashMap<>();
        Map<Long, Long> customerUnpaidCount = new HashMap<>();
        Map<Long, Long> customerOverdueCount = new HashMap<>();

        LocalDate today = LocalDate.now();

        if (allInvoices != null) {
            for (Invoice inv : allInvoices) {
                if (inv.getCustomer() == null || inv.getCustomer().getId() == null) {
                    continue;
                }
                Long cId = inv.getCustomer().getId();
                BigDecimal rawAmt = inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO;

                customerBilled.merge(cId, rawAmt, BigDecimal::add);
                customerInvoiceCount.merge(cId, 1L, Long::sum);

                InvoiceStatus st = inv.getStatus();
                if (st == InvoiceStatus.UNPAID || st == InvoiceStatus.OVERDUE || st == InvoiceStatus.SENT || st == InvoiceStatus.DRAFT) {
                    customerUnpaidCount.merge(cId, 1L, Long::sum);
                }

                if (st == InvoiceStatus.OVERDUE || (inv.getDueDate() != null && inv.getDueDate().isBefore(today) && !Boolean.TRUE.equals(inv.getPaid()) && st != InvoiceStatus.CANCELLED)) {
                    customerOverdueCount.merge(cId, 1L, Long::sum);
                }

                // Legacy fallback (aligned with StatementServiceImpl)
                if (Boolean.TRUE.equals(inv.getPaid()) && !invoicesWithPayments.contains(inv.getId())) {
                    customerDirectPaid.merge(cId, rawAmt, BigDecimal::add);
                }
            }
        }

        // Assemble DTOs
        List<CustomerKpiSummary> summaries = new ArrayList<>(customers.size());
        for (Customer c : customers) {
            Long cId = c.getId();
            BigDecimal billed = customerBilled.getOrDefault(cId, BigDecimal.ZERO);
            BigDecimal paid = customerPayments.getOrDefault(cId, BigDecimal.ZERO)
                    .add(customerDirectPaid.getOrDefault(cId, BigDecimal.ZERO));
            BigDecimal returned = customerReturns.getOrDefault(cId, BigDecimal.ZERO);

            // Authoritative Ledger formula: netBalance = (billed - returns) - paid
            BigDecimal netBalance = billed.subtract(returned).subtract(paid);

            summaries.add(CustomerKpiSummary.builder()
                    .customerId(cId)
                    .customerName(c.getName())
                    .phone(c.getPhone())
                    .email(c.getEmail())
                    .gstin(c.getGstin())
                    .address(c.getAddress())
                    .totalBilled(billed.setScale(2, RoundingMode.HALF_UP).doubleValue())
                    .totalPaid(paid.setScale(2, RoundingMode.HALF_UP).doubleValue())
                    .totalReturns(returned.setScale(2, RoundingMode.HALF_UP).doubleValue())
                    .netBalance(netBalance.setScale(2, RoundingMode.HALF_UP).doubleValue())
                    .invoiceCount(customerInvoiceCount.getOrDefault(cId, 0L))
                    .unpaidInvoiceCount(customerUnpaidCount.getOrDefault(cId, 0L))
                    .overdueInvoiceCount(customerOverdueCount.getOrDefault(cId, 0L))
                    .build());
        }

        return summaries;
    }

    private Long resolveFirmId(Long firmId) {
        Long authoritativeFirmId = firmId != null ? firmId : TenantContext.getCurrentFirmId();
        if (authoritativeFirmId == null) {
            throw new TenantSecurityException("Active firm ID is required for KPI aggregation.");
        }
        return authoritativeFirmId;
    }

    private Double toDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try {
            return Double.parseDouble(val.toString());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private Long toLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Number) return ((Number) val).longValue();
        try {
            return Long.parseLong(val.toString());
        } catch (Exception e) {
            return 0L;
        }
    }
}
