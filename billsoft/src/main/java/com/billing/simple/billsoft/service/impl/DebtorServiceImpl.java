package com.billing.simple.billsoft.service.impl;

import com.billing.simple.billsoft.dtos.BusinessPartyLinkResponse;
import com.billing.simple.billsoft.dtos.CustomerBalanceSummary;
import com.billing.simple.billsoft.dtos.debtor.*;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.security.TenantSecurityException;
import com.billing.simple.billsoft.service.BusinessPartyLinkService;
import com.billing.simple.billsoft.service.CustomerBalanceService;
import com.billing.simple.billsoft.service.DebtorService;
import com.billing.simple.billsoft.service.pdf.core.UniversalPageNumberHelper;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DebtorServiceImpl implements DebtorService {

    private final CustomerRepository customerRepo;
    private final InvoiceRepository invoiceRepo;
    private final InvoicePaymentRepository invoicePaymentRepo;
    private final SalesReturnRepository salesReturnRepo;
    private final CustomerPromiseToPayRepository promiseRepo;
    private final CustomerCollectionActivityRepository activityRepo;
    private final ReminderRepository reminderRepo;
    private final CustomerBalanceService balanceService;
    private final BusinessPartyLinkService businessPartyLinkService;
    private final FirmDetailsRepository firmRepo;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.billing.simple.billsoft.repo.PromisePaymentAllocationRepository promiseAllocationRepo;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.billing.simple.billsoft.repo.CustomerPromiseHistoryRepository promiseHistoryRepo;

    private static final int SCALE = 2;

    @org.springframework.beans.factory.annotation.Autowired
    public DebtorServiceImpl(
            CustomerRepository customerRepo,
            InvoiceRepository invoiceRepo,
            InvoicePaymentRepository invoicePaymentRepo,
            SalesReturnRepository salesReturnRepo,
            CustomerPromiseToPayRepository promiseRepo,
            CustomerCollectionActivityRepository activityRepo,
            ReminderRepository reminderRepo,
            CustomerBalanceService balanceService,
            BusinessPartyLinkService businessPartyLinkService,
            FirmDetailsRepository firmRepo,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.billing.simple.billsoft.repo.PromisePaymentAllocationRepository promiseAllocationRepo,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.billing.simple.billsoft.repo.CustomerPromiseHistoryRepository promiseHistoryRepo
    ) {
        this.customerRepo = customerRepo;
        this.invoiceRepo = invoiceRepo;
        this.invoicePaymentRepo = invoicePaymentRepo;
        this.salesReturnRepo = salesReturnRepo;
        this.promiseRepo = promiseRepo;
        this.activityRepo = activityRepo;
        this.reminderRepo = reminderRepo;
        this.balanceService = balanceService;
        this.businessPartyLinkService = businessPartyLinkService;
        this.firmRepo = firmRepo;
        this.promiseAllocationRepo = promiseAllocationRepo;
        this.promiseHistoryRepo = promiseHistoryRepo;
    }

    public DebtorServiceImpl(
            CustomerRepository customerRepo,
            InvoiceRepository invoiceRepo,
            InvoicePaymentRepository invoicePaymentRepo,
            SalesReturnRepository salesReturnRepo,
            CustomerPromiseToPayRepository promiseRepo,
            CustomerCollectionActivityRepository activityRepo,
            ReminderRepository reminderRepo,
            CustomerBalanceService balanceService,
            BusinessPartyLinkService businessPartyLinkService,
            FirmDetailsRepository firmRepo
    ) {
        this(customerRepo, invoiceRepo, invoicePaymentRepo, salesReturnRepo, promiseRepo, activityRepo, reminderRepo, balanceService, businessPartyLinkService, firmRepo, null, null);
    }

    private BigDecimal nz(BigDecimal val) {
        return val == null ? BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP) : val.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private boolean isBillableInvoiceStatus(InvoiceStatus status) {
        return status != null && (
                status == InvoiceStatus.UNPAID ||
                status == InvoiceStatus.FINAL ||
                status == InvoiceStatus.PAID ||
                status == InvoiceStatus.SENT ||
                status == InvoiceStatus.OVERDUE
        );
    }

    private boolean isOverdueInvoiceStatus(InvoiceStatus status) {
        return status != null && (
                status == InvoiceStatus.UNPAID ||
                status == InvoiceStatus.FINAL ||
                status == InvoiceStatus.SENT ||
                status == InvoiceStatus.OVERDUE
        );
    }

    @Override
    @Transactional(readOnly = true)
    public DebtorsOverviewDto getOverview(Long firmId) {
        LocalDate today = LocalDate.now();
        List<Customer> customers = customerRepo.findByFirmId(firmId);

        BigDecimal totalOutstanding = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal totalAdvanceCredit = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        int totalDebtorsCount = 0;
        int overdueDebtorsCount = 0;
        int criticalDebtorsCount = 0;

        BigDecimal aging0To30 = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal aging31To60 = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal aging61To90 = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal aging90Plus = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal firmOverdueAmount = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);

        // Load all billable invoices for firm once to avoid N+1
        List<Invoice> allInvoices = invoiceRepo.findByFirmId(firmId);
        List<InvoicePayment> allPayments = invoicePaymentRepo.findByFirmId(firmId);
        List<SalesReturn> allReturns = salesReturnRepo.findByFirmId(firmId);

        // Precompute payments and returns per invoice
        Map<Long, BigDecimal> paymentsByInvoice = new HashMap<>();
        for (InvoicePayment pmt : allPayments) {
            if (pmt.getInvoiceId() != null && pmt.getAmount() != null) {
                paymentsByInvoice.merge(pmt.getInvoiceId(), pmt.getAmount(), BigDecimal::add);
            }
        }

        Map<Long, BigDecimal> returnsByInvoice = new HashMap<>();
        for (SalesReturn sr : allReturns) {
            if (sr.getInvoice() != null && sr.getInvoice().getId() != null && sr.getTotalRefundAmount() != null) {
                returnsByInvoice.merge(sr.getInvoice().getId(), sr.getTotalRefundAmount(), BigDecimal::add);
            }
        }

        // Map invoices by customer
        Map<Long, List<Invoice>> invoicesByCustomer = allInvoices.stream()
                .filter(i -> i.getCustomer() != null && i.getCustomer().getId() != null)
                .collect(Collectors.groupingBy(i -> i.getCustomer().getId()));

        for (Customer c : customers) {
            CustomerBalanceSummary summary = balanceService.getCustomerBalance(firmId, c.getId());
            BigDecimal closing = nz(summary.getClosingBalance());

            if (closing.compareTo(BigDecimal.ZERO) > 0) {
                totalOutstanding = totalOutstanding.add(closing);
                totalDebtorsCount++;
            } else if (closing.compareTo(BigDecimal.ZERO) < 0) {
                totalAdvanceCredit = totalAdvanceCredit.add(closing.abs());
            }

            // Check overdue invoices for this customer
            List<Invoice> custInvoices = invoicesByCustomer.getOrDefault(c.getId(), Collections.emptyList());
            boolean hasOverdue = false;
            boolean isCritical = false;

            for (Invoice inv : custInvoices) {
                if (isOverdueInvoiceStatus(inv.getStatus())) {
                    LocalDate dueDate = inv.getDueDate() != null ? inv.getDueDate() : (inv.getInvoiceDate() != null ? inv.getInvoiceDate().toLocalDate() : null);
                    if (dueDate != null && dueDate.isBefore(today)) {
                        BigDecimal total = nz(inv.getTotalAmount());
                        BigDecimal paid = nz(paymentsByInvoice.get(inv.getId()));
                        BigDecimal ret = nz(returnsByInvoice.get(inv.getId()));
                        BigDecimal balDue = total.subtract(paid).subtract(ret);

                        if (balDue.compareTo(BigDecimal.ZERO) > 0) {
                            hasOverdue = true;
                            firmOverdueAmount = firmOverdueAmount.add(balDue);
                            long days = ChronoUnit.DAYS.between(dueDate, today);

                            if (days <= 30) {
                                aging0To30 = aging0To30.add(balDue);
                            } else if (days <= 60) {
                                aging31To60 = aging31To60.add(balDue);
                            } else if (days <= 90) {
                                aging61To90 = aging61To90.add(balDue);
                            } else {
                                aging90Plus = aging90Plus.add(balDue);
                                isCritical = true;
                            }
                        }
                    }
                }
            }

            if (hasOverdue) {
                overdueDebtorsCount++;
            }
            if (isCritical) {
                criticalDebtorsCount++;
            }
        }

        // Collected this month
        YearMonth currentMonth = YearMonth.from(today);
        LocalDate startOfMonth = currentMonth.atDay(1);
        LocalDate endOfMonth = currentMonth.atEndOfMonth();

        BigDecimal collectedThisMonth = allPayments.stream()
                .filter(p -> p.getPaymentDate() != null && !p.getPaymentDate().isBefore(startOfMonth) && !p.getPaymentDate().isAfter(endOfMonth))
                .map(p -> nz(p.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Today's follow-ups
        List<DebtorFollowUpDto> todayFollowUps = getFollowUps(firmId, "TODAY");

        return DebtorsOverviewDto.builder()
                .totalOutstanding(totalOutstanding)
                .overdueAmount(firmOverdueAmount)
                .collectedThisMonth(collectedThisMonth)
                .advanceCredit(totalAdvanceCredit)
                .totalDebtorsCount(totalDebtorsCount)
                .overdueDebtorsCount(overdueDebtorsCount)
                .criticalDebtorsCount(criticalDebtorsCount)
                .aging0To30(aging0To30)
                .aging31To60(aging31To60)
                .aging61To90(aging61To90)
                .aging90Plus(aging90Plus)
                .todayFollowUpsCount(todayFollowUps.size())
                .todayFollowUps(todayFollowUps)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DebtorListItemDto> getDebtors(Long firmId, String search, String filter, String sortBy, String sortDir) {
        LocalDate today = LocalDate.now();
        List<Customer> allCustomers = customerRepo.findByFirmId(firmId);

        // Batch fetch invoices, payments, returns, promises for firm
        List<Invoice> allInvoices = invoiceRepo.findByFirmId(firmId);
        List<InvoicePayment> allPayments = invoicePaymentRepo.findByFirmId(firmId);
        List<SalesReturn> allReturns = salesReturnRepo.findByFirmId(firmId);
        List<CustomerPromiseToPay> allPromises = promiseRepo.findByFirmId(firmId);

        Map<Long, BigDecimal> paymentsByInvoice = new HashMap<>();
        for (InvoicePayment pmt : allPayments) {
            if (pmt.getInvoiceId() != null && pmt.getAmount() != null) {
                paymentsByInvoice.merge(pmt.getInvoiceId(), pmt.getAmount(), BigDecimal::add);
            }
        }

        Map<Long, BigDecimal> returnsByInvoice = new HashMap<>();
        for (SalesReturn sr : allReturns) {
            if (sr.getInvoice() != null && sr.getInvoice().getId() != null && sr.getTotalRefundAmount() != null) {
                returnsByInvoice.merge(sr.getInvoice().getId(), sr.getTotalRefundAmount(), BigDecimal::add);
            }
        }

        Map<Long, List<Invoice>> invoicesByCustomer = allInvoices.stream()
                .filter(i -> i.getCustomer() != null && i.getCustomer().getId() != null)
                .collect(Collectors.groupingBy(i -> i.getCustomer().getId()));

        Map<Long, List<InvoicePayment>> paymentsByCustomer = allPayments.stream()
                .filter(p -> p.getCustomerId() != null)
                .collect(Collectors.groupingBy(InvoicePayment::getCustomerId));

        Map<Long, List<CustomerPromiseToPay>> promisesByCustomer = allPromises.stream()
                .collect(Collectors.groupingBy(CustomerPromiseToPay::getCustomerId));

        List<DebtorListItemDto> result = new ArrayList<>();

        for (Customer c : allCustomers) {
            if (search != null && !search.isBlank()) {
                String q = search.trim().toLowerCase();
                String name = c.getName() != null ? c.getName().toLowerCase() : "";
                String phone = c.getPhone() != null ? c.getPhone().toLowerCase() : "";
                String email = c.getEmail() != null ? c.getEmail().toLowerCase() : "";
                if (!name.contains(q) && !phone.contains(q) && !email.contains(q)) {
                    continue;
                }
            }

            CustomerBalanceSummary summary = balanceService.getCustomerBalance(firmId, c.getId());
            BigDecimal closing = nz(summary.getClosingBalance());
            BigDecimal outstanding = closing.compareTo(BigDecimal.ZERO) > 0 ? closing : BigDecimal.ZERO;
            BigDecimal advance = closing.compareTo(BigDecimal.ZERO) < 0 ? closing.abs() : BigDecimal.ZERO;

            List<Invoice> custInvoices = invoicesByCustomer.getOrDefault(c.getId(), Collections.emptyList());
            BigDecimal custOverdue = BigDecimal.ZERO;
            BigDecimal a0 = BigDecimal.ZERO;
            BigDecimal a30 = BigDecimal.ZERO;
            BigDecimal a60 = BigDecimal.ZERO;
            BigDecimal a90 = BigDecimal.ZERO;

            LocalDate oldestOverdueDate = null;
            int oldestDays = 0;

            for (Invoice inv : custInvoices) {
                if (isOverdueInvoiceStatus(inv.getStatus())) {
                    LocalDate dueDate = inv.getDueDate() != null ? inv.getDueDate() : (inv.getInvoiceDate() != null ? inv.getInvoiceDate().toLocalDate() : null);
                    if (dueDate != null && dueDate.isBefore(today)) {
                        BigDecimal total = nz(inv.getTotalAmount());
                        BigDecimal paid = nz(paymentsByInvoice.get(inv.getId()));
                        BigDecimal ret = nz(returnsByInvoice.get(inv.getId()));
                        BigDecimal balDue = total.subtract(paid).subtract(ret);

                        if (balDue.compareTo(BigDecimal.ZERO) > 0) {
                            custOverdue = custOverdue.add(balDue);
                            int days = (int) ChronoUnit.DAYS.between(dueDate, today);

                            if (oldestOverdueDate == null || dueDate.isBefore(oldestOverdueDate)) {
                                oldestOverdueDate = dueDate;
                                oldestDays = days;
                            }

                            if (days <= 30) a0 = a0.add(balDue);
                            else if (days <= 60) a30 = a30.add(balDue);
                            else if (days <= 90) a60 = a60.add(balDue);
                            else a90 = a90.add(balDue);
                        }
                    }
                }
            }

            // Last payment
            List<InvoicePayment> custPmts = paymentsByCustomer.getOrDefault(c.getId(), Collections.emptyList());
            LocalDate lastPaymentDate = null;
            BigDecimal lastPaymentAmount = null;

            Optional<InvoicePayment> latestPaymentOpt = custPmts.stream()
                    .filter(p -> p.getAmount() != null && p.getAmount().compareTo(BigDecimal.ZERO) > 0)
                    .max(Comparator.comparing(InvoicePayment::getPaymentDate, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(InvoicePayment::getId, Comparator.nullsLast(Comparator.naturalOrder())));

            if (latestPaymentOpt.isPresent()) {
                lastPaymentDate = latestPaymentOpt.get().getPaymentDate();
                lastPaymentAmount = latestPaymentOpt.get().getAmount();
            }

            // Promises
            List<CustomerPromiseToPay> custPromises = promisesByCustomer.getOrDefault(c.getId(), Collections.emptyList());
            Optional<CustomerPromiseToPay> activePromiseOpt = custPromises.stream()
                    .filter(p -> p.getStatus() == PromiseStatus.PROMISED || p.getStatus() == PromiseStatus.PARTIALLY_FULFILLED)
                    .min(Comparator.comparing(CustomerPromiseToPay::getPromisedDate));

            BigDecimal activePromiseAmount = null;
            LocalDate activePromiseDate = null;
            String promiseStatusStr = null;

            if (activePromiseOpt.isPresent()) {
                activePromiseAmount = activePromiseOpt.get().getRemainingAmount();
                activePromiseDate = activePromiseOpt.get().getPromisedDate();
                promiseStatusStr = activePromiseOpt.get().getStatus().name();
            }

            // Missed promises count
            long missedPromises = custPromises.stream()
                    .filter(p -> p.getStatus() == PromiseStatus.MISSED)
                    .count();

            // Next follow-up date: from active promise or reminder
            LocalDate nextFollowUpDate = activePromiseDate;

            // Risk calculation
            String riskLevel = "LOW";
            String riskReason = "Account in good standing with on-time payment track record";

            if (a90.compareTo(BigDecimal.ZERO) > 0 || missedPromises > 0 || oldestDays > 90) {
                riskLevel = "HIGH";
                if (a90.compareTo(BigDecimal.ZERO) > 0) {
                    riskReason = "Overdue ₹" + a90 + " exceeding 90+ days";
                } else if (missedPromises > 0) {
                    riskReason = missedPromises + " missed promise(s) to pay recorded";
                } else {
                    riskReason = "Oldest overdue invoice is " + oldestDays + " days past due";
                }
            } else if (a30.compareTo(BigDecimal.ZERO) > 0 || a60.compareTo(BigDecimal.ZERO) > 0 || custOverdue.compareTo(BigDecimal.ZERO) > 0) {
                riskLevel = "MEDIUM";
                riskReason = "Receivables aging between 31 and 90 days (₹" + a30.add(a60) + ")";
            }

            // Counterparty link check
            boolean hasSupplierCounterpart = false;
            BigDecimal supplierPayableAmount = null;
            try {
                BusinessPartyLinkResponse linkResp = businessPartyLinkService.getLinkedPartyForCustomer(c.getId());
                if (linkResp != null && linkResp.getPartyId() != null) {
                    hasSupplierCounterpart = true;
                    if (linkResp.getPartyNetPayable() != null) {
                        supplierPayableAmount = BigDecimal.valueOf(linkResp.getPartyNetPayable()).setScale(SCALE, RoundingMode.HALF_UP);
                    }
                }
            } catch (Exception ignored) {
            }

            DebtorListItemDto dto = DebtorListItemDto.builder()
                    .customerId(c.getId())
                    .customerName(c.getName())
                    .customerPhone(c.getPhone())
                    .customerEmail(c.getEmail())
                    .outstandingAmount(outstanding)
                    .advanceCredit(advance)
                    .overdueAmount(custOverdue)
                    .oldestOverdueDate(oldestOverdueDate)
                    .oldestOverdueDays(oldestDays)
                    .aging0To30(a0)
                    .aging31To60(a30)
                    .aging61To90(a60)
                    .aging90Plus(a90)
                    .lastPaymentDate(lastPaymentDate)
                    .lastPaymentAmount(lastPaymentAmount)
                    .nextFollowUpDate(nextFollowUpDate)
                    .riskLevel(riskLevel)
                    .riskReason(riskReason)
                    .activePromiseAmount(activePromiseAmount)
                    .activePromiseDate(activePromiseDate)
                    .promiseStatus(promiseStatusStr)
                    .hasSupplierCounterpart(hasSupplierCounterpart)
                    .supplierPayableAmount(supplierPayableAmount)
                    .build();

            // Filter check
            if ("overdue".equalsIgnoreCase(filter)) {
                if (custOverdue.compareTo(BigDecimal.ZERO) <= 0) continue;
            } else if ("critical".equalsIgnoreCase(filter)) {
                if (!"HIGH".equals(riskLevel)) continue;
            } else if ("promised".equalsIgnoreCase(filter)) {
                if (activePromiseAmount == null) continue;
            } else if ("advance".equalsIgnoreCase(filter)) {
                if (advance.compareTo(BigDecimal.ZERO) <= 0) continue;
            } else if (!"all_including_zero".equalsIgnoreCase(filter)) {
                // Default: show customers with positive outstanding balance or overdue or advance
                if (outstanding.compareTo(BigDecimal.ZERO) <= 0 && custOverdue.compareTo(BigDecimal.ZERO) <= 0 && advance.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
            }

            result.add(dto);
        }

        // Sorting
        boolean desc = sortDir == null || "desc".equalsIgnoreCase(sortDir);
        Comparator<DebtorListItemDto> comparator;

        if ("name".equalsIgnoreCase(sortBy)) {
            comparator = Comparator.comparing(d -> d.getCustomerName() != null ? d.getCustomerName().toLowerCase() : "");
        } else if ("outstanding".equalsIgnoreCase(sortBy)) {
            comparator = Comparator.comparing(DebtorListItemDto::getOutstandingAmount);
        } else if ("overdue".equalsIgnoreCase(sortBy)) {
            comparator = Comparator.comparing(DebtorListItemDto::getOverdueAmount);
        } else if ("days".equalsIgnoreCase(sortBy)) {
            comparator = Comparator.comparingInt(DebtorListItemDto::getOldestOverdueDays);
        } else {
            // Default ranking: weighted risk score
            comparator = Comparator.comparing(d -> {
                double score = 0;
                score += d.getAging90Plus().doubleValue() * 4.0;
                score += d.getAging61To90().doubleValue() * 2.0;
                score += d.getAging31To60().doubleValue() * 1.5;
                score += d.getAging0To30().doubleValue() * 1.0;
                score += d.getOverdueAmount().doubleValue() * 2.0;
                if ("HIGH".equals(d.getRiskLevel())) score += 10000;
                else if ("MEDIUM".equals(d.getRiskLevel())) score += 5000;
                return score;
            });
        }

        if (desc) {
            comparator = comparator.reversed();
        }
        result.sort(comparator);

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public DebtorDetailDto getDebtorDetail(Long firmId, Long customerId) {
        LocalDate today = LocalDate.now();
        Customer customer = customerRepo.findByIdAndFirmId(customerId, firmId)
                .orElseThrow(() -> new TenantSecurityException("Customer #" + customerId + " not found in firm"));

        CustomerBalanceSummary balanceSummary = balanceService.getCustomerBalance(firmId, customerId);
        BigDecimal closing = nz(balanceSummary.getClosingBalance());
        BigDecimal outstanding = closing.compareTo(BigDecimal.ZERO) > 0 ? closing : BigDecimal.ZERO;
        BigDecimal advance = closing.compareTo(BigDecimal.ZERO) < 0 ? closing.abs() : BigDecimal.ZERO;

        List<DebtorOverdueInvoiceDto> overdueInvoices = getCustomerOverdueInvoices(firmId, customerId);

        BigDecimal overdueAmount = BigDecimal.ZERO;
        BigDecimal a0 = BigDecimal.ZERO;
        BigDecimal a30 = BigDecimal.ZERO;
        BigDecimal a60 = BigDecimal.ZERO;
        BigDecimal a90 = BigDecimal.ZERO;

        for (DebtorOverdueInvoiceDto inv : overdueInvoices) {
            overdueAmount = overdueAmount.add(inv.getBalanceDue());
            if ("0-30".equals(inv.getAgingBucket())) a0 = a0.add(inv.getBalanceDue());
            else if ("31-60".equals(inv.getAgingBucket())) a30 = a30.add(inv.getBalanceDue());
            else if ("61-90".equals(inv.getAgingBucket())) a60 = a60.add(inv.getBalanceDue());
            else a90 = a90.add(inv.getBalanceDue());
        }

        // Payments & average delay
        List<InvoicePayment> payments = invoicePaymentRepo.findByFirmIdAndCustomerIdOrderByPaymentDateAscIdAsc(firmId, customerId);
        LocalDate lastPaymentDate = null;
        BigDecimal lastPaymentAmount = null;

        double totalDelayWeighted = 0;
        double totalPaidWeight = 0;

        // Map invoices to find due dates
        List<Invoice> custInvoices = invoiceRepo.findByFirmIdAndCustomer_Id(firmId, customerId);
        Map<Long, Invoice> invoiceMap = custInvoices.stream()
                .collect(Collectors.toMap(Invoice::getId, i -> i, (a, b) -> a));

        for (InvoicePayment p : payments) {
            if (p.getAmount() != null && p.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                lastPaymentDate = p.getPaymentDate();
                lastPaymentAmount = p.getAmount();

                if (p.getInvoiceId() != null && invoiceMap.containsKey(p.getInvoiceId())) {
                    Invoice inv = invoiceMap.get(p.getInvoiceId());
                    LocalDate due = inv.getDueDate() != null ? inv.getDueDate() : (inv.getInvoiceDate() != null ? inv.getInvoiceDate().toLocalDate() : null);
                    if (due != null && p.getPaymentDate() != null) {
                        long diff = ChronoUnit.DAYS.between(due, p.getPaymentDate());
                        long delay = Math.max(0, diff);
                        double amt = p.getAmount().doubleValue();
                        totalDelayWeighted += (delay * amt);
                        totalPaidWeight += amt;
                    }
                }
            }
        }

        double averageDelay = totalPaidWeight > 0 ? (totalDelayWeighted / totalPaidWeight) : 0.0;

        // Promises
        List<CustomerPromiseToPay> promises = promiseRepo.findByFirmIdAndCustomerIdOrderByPromisedDateDesc(firmId, customerId);
        double totalPromised = 0;
        double totalFulfilled = 0;
        long missedPromises = 0;

        for (CustomerPromiseToPay pr : promises) {
            if (pr.getStatus() != PromiseStatus.CANCELLED) {
                if (pr.getPromisedAmount() != null) totalPromised += pr.getPromisedAmount().doubleValue();
                if (pr.getFulfilledAmount() != null) totalFulfilled += pr.getFulfilledAmount().doubleValue();
            }
            if (pr.getStatus() == PromiseStatus.MISSED) {
                missedPromises++;
            }
        }

        double fulfilmentRate = totalPromised > 0 ? Math.min(100.0, (totalFulfilled / totalPromised) * 100.0) : 100.0;

        // Risk explanation
        String riskLevel = "LOW";
        String riskReason = "Customer has strong payment consistency with no delinquent aging";

        if (a90.compareTo(BigDecimal.ZERO) > 0 || missedPromises > 0 || averageDelay > 45) {
            riskLevel = "HIGH";
            if (a90.compareTo(BigDecimal.ZERO) > 0) {
                riskReason = "Significant 90+ days delinquent balance of ₹" + a90;
            } else if (missedPromises > 0) {
                riskReason = missedPromises + " promise(s) to pay missed or dishonored";
            } else {
                riskReason = "Average payment delay is high (" + Math.round(averageDelay) + " days past due)";
            }
        } else if (a30.compareTo(BigDecimal.ZERO) > 0 || a60.compareTo(BigDecimal.ZERO) > 0 || overdueAmount.compareTo(BigDecimal.ZERO) > 0) {
            riskLevel = "MEDIUM";
            riskReason = "Receivables aging between 31 and 90 days (₹" + a30.add(a60) + ")";
        }

        // Counterparty
        boolean hasSupplier = false;
        Long partyId = null;
        String partyName = null;
        BigDecimal supplierPayable = null;

        try {
            BusinessPartyLinkResponse linkResp = businessPartyLinkService.getLinkedPartyForCustomer(customerId);
            if (linkResp != null && linkResp.getPartyId() != null) {
                hasSupplier = true;
                partyId = linkResp.getPartyId();
                partyName = linkResp.getPartyName();
                if (linkResp.getPartyNetPayable() != null) {
                    supplierPayable = BigDecimal.valueOf(linkResp.getPartyNetPayable()).setScale(SCALE, RoundingMode.HALF_UP);
                }
            }
        } catch (Exception ignored) {
        }

        // Activities
        List<CustomerCollectionActivity> activities = getCustomerTimeline(firmId, customerId);

        return DebtorDetailDto.builder()
                .customer(customer)
                .balanceSummary(balanceSummary)
                .outstandingAmount(outstanding)
                .overdueAmount(overdueAmount)
                .advanceCredit(advance)
                .aging0To30(a0)
                .aging31To60(a30)
                .aging61To90(a60)
                .aging90Plus(a90)
                .lastPaymentDate(lastPaymentDate)
                .lastPaymentAmount(lastPaymentAmount)
                .averagePaymentDelayDays(Math.round(averageDelay * 10.0) / 10.0)
                .promiseFulfilmentRate(Math.round(fulfilmentRate * 10.0) / 10.0)
                .riskLevel(riskLevel)
                .riskReason(riskReason)
                .hasSupplierCounterpart(hasSupplier)
                .linkedPartyId(partyId)
                .linkedPartyName(partyName)
                .supplierPayableAmount(supplierPayable)
                .promises(promises)
                .overdueInvoices(overdueInvoices)
                .recentActivities(activities)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DebtorOverdueInvoiceDto> getCustomerOverdueInvoices(Long firmId, Long customerId) {
        LocalDate today = LocalDate.now();
        Customer customer = customerRepo.findByIdAndFirmId(customerId, firmId)
                .orElseThrow(() -> new TenantSecurityException("Customer #" + customerId + " not found in firm"));

        List<Invoice> invoices = invoiceRepo.findByFirmIdAndCustomer_Id(firmId, customerId);
        List<InvoicePayment> payments = invoicePaymentRepo.findByFirmIdAndCustomerIdOrderByPaymentDateAscIdAsc(firmId, customerId);
        List<SalesReturn> returns = salesReturnRepo.findByFirmIdAndCustomerIdOrderByReturnDateAscIdAsc(firmId, customerId);

        Map<Long, BigDecimal> paymentsByInvoice = new HashMap<>();
        for (InvoicePayment pmt : payments) {
            if (pmt.getInvoiceId() != null && pmt.getAmount() != null) {
                paymentsByInvoice.merge(pmt.getInvoiceId(), pmt.getAmount(), BigDecimal::add);
            }
        }

        Map<Long, BigDecimal> returnsByInvoice = new HashMap<>();
        for (SalesReturn sr : returns) {
            if (sr.getInvoice() != null && sr.getInvoice().getId() != null && sr.getTotalRefundAmount() != null) {
                returnsByInvoice.merge(sr.getInvoice().getId(), sr.getTotalRefundAmount(), BigDecimal::add);
            }
        }

        List<DebtorOverdueInvoiceDto> result = new ArrayList<>();

        for (Invoice inv : invoices) {
            if (isOverdueInvoiceStatus(inv.getStatus())) {
                LocalDate dueDate = inv.getDueDate() != null ? inv.getDueDate() : (inv.getInvoiceDate() != null ? inv.getInvoiceDate().toLocalDate() : null);
                if (dueDate != null && dueDate.isBefore(today)) {
                    BigDecimal total = nz(inv.getTotalAmount());
                    BigDecimal paid = nz(paymentsByInvoice.get(inv.getId()));
                    BigDecimal ret = nz(returnsByInvoice.get(inv.getId()));
                    BigDecimal balDue = total.subtract(paid).subtract(ret);

                    if (balDue.compareTo(BigDecimal.ZERO) > 0) {
                        int days = (int) ChronoUnit.DAYS.between(dueDate, today);
                        String bucket;
                        if (days <= 30) bucket = "0-30";
                        else if (days <= 60) bucket = "31-60";
                        else if (days <= 90) bucket = "61-90";
                        else bucket = "90+";

                        result.add(DebtorOverdueInvoiceDto.builder()
                                .invoiceId(inv.getId())
                                .invoiceNumber(inv.getInvoiceNumber())
                                .customerId(customerId)
                                .customerName(customer.getName())
                                .customerPhone(customer.getPhone())
                                .invoiceDate(inv.getInvoiceDate() != null ? inv.getInvoiceDate().toLocalDate() : null)
                                .dueDate(dueDate)
                                .totalAmount(total)
                                .paidAmount(paid)
                                .refundedAmount(ret)
                                .balanceDue(balDue)
                                .daysOverdue(days)
                                .agingBucket(bucket)
                                .build());
                    }
                }
            }
        }

        result.sort(Comparator.comparing(DebtorOverdueInvoiceDto::getDueDate));
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DebtorOverdueInvoiceDto> getFirmOverdueInvoices(Long firmId) {
        LocalDate today = LocalDate.now();
        List<Invoice> invoices = invoiceRepo.findByFirmId(firmId);
        List<InvoicePayment> payments = invoicePaymentRepo.findByFirmId(firmId);
        List<SalesReturn> returns = salesReturnRepo.findByFirmId(firmId);

        Map<Long, BigDecimal> paymentsByInvoice = new HashMap<>();
        for (InvoicePayment pmt : payments) {
            if (pmt.getInvoiceId() != null && pmt.getAmount() != null) {
                paymentsByInvoice.merge(pmt.getInvoiceId(), pmt.getAmount(), BigDecimal::add);
            }
        }

        Map<Long, BigDecimal> returnsByInvoice = new HashMap<>();
        for (SalesReturn sr : returns) {
            if (sr.getInvoice() != null && sr.getInvoice().getId() != null && sr.getTotalRefundAmount() != null) {
                returnsByInvoice.merge(sr.getInvoice().getId(), sr.getTotalRefundAmount(), BigDecimal::add);
            }
        }

        List<DebtorOverdueInvoiceDto> result = new ArrayList<>();

        for (Invoice inv : invoices) {
            if (isOverdueInvoiceStatus(inv.getStatus())) {
                LocalDate dueDate = inv.getDueDate() != null ? inv.getDueDate() : (inv.getInvoiceDate() != null ? inv.getInvoiceDate().toLocalDate() : null);
                if (dueDate != null && dueDate.isBefore(today)) {
                    BigDecimal total = nz(inv.getTotalAmount());
                    BigDecimal paid = nz(paymentsByInvoice.get(inv.getId()));
                    BigDecimal ret = nz(returnsByInvoice.get(inv.getId()));
                    BigDecimal balDue = total.subtract(paid).subtract(ret);

                    if (balDue.compareTo(BigDecimal.ZERO) > 0) {
                        int days = (int) ChronoUnit.DAYS.between(dueDate, today);
                        String bucket;
                        if (days <= 30) bucket = "0-30";
                        else if (days <= 60) bucket = "31-60";
                        else if (days <= 90) bucket = "61-90";
                        else bucket = "90+";

                        Long custId = inv.getCustomer() != null ? inv.getCustomer().getId() : null;
                        String custName = inv.getCustomer() != null ? inv.getCustomer().getName() : "Unknown";
                        String custPhone = inv.getCustomer() != null ? inv.getCustomer().getPhone() : null;

                        result.add(DebtorOverdueInvoiceDto.builder()
                                .invoiceId(inv.getId())
                                .invoiceNumber(inv.getInvoiceNumber())
                                .customerId(custId)
                                .customerName(custName)
                                .customerPhone(custPhone)
                                .invoiceDate(inv.getInvoiceDate() != null ? inv.getInvoiceDate().toLocalDate() : null)
                                .dueDate(dueDate)
                                .totalAmount(total)
                                .paidAmount(paid)
                                .refundedAmount(ret)
                                .balanceDue(balDue)
                                .daysOverdue(days)
                                .agingBucket(bucket)
                                .build());
                    }
                }
            }
        }

        result.sort(Comparator.comparing(DebtorOverdueInvoiceDto::getDueDate));
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerCollectionActivity> getCustomerTimeline(Long firmId, Long customerId) {
        List<CustomerCollectionActivity> list = activityRepo.findByFirmIdAndCustomerIdOrderByCreatedAtDesc(firmId, customerId);
        if (list.isEmpty()) {
            // Synthesize historical timeline from payments and promises
            List<InvoicePayment> payments = invoicePaymentRepo.findByFirmIdAndCustomerIdOrderByPaymentDateAscIdAsc(firmId, customerId);
            List<CustomerPromiseToPay> promises = promiseRepo.findByFirmIdAndCustomerIdOrderByPromisedDateDesc(firmId, customerId);

            List<CustomerCollectionActivity> synth = new ArrayList<>();
            for (InvoicePayment p : payments) {
                synth.add(CustomerCollectionActivity.builder()
                        .firmId(firmId)
                        .customerId(customerId)
                        .paymentId(p.getId())
                        .activityType(CollectionActivityType.PAYMENT_RECEIVED)
                        .description("Payment of ₹" + p.getAmount() + " received via " + (p.getPaymentMode() != null ? p.getPaymentMode() : "Cash"))
                        .performedBy("System")
                        .createdAt(p.getPaymentDate() != null ? p.getPaymentDate().atStartOfDay() : LocalDateTime.now())
                        .build());
            }

            for (CustomerPromiseToPay pr : promises) {
                synth.add(CustomerCollectionActivity.builder()
                        .firmId(firmId)
                        .customerId(customerId)
                        .promiseId(pr.getId())
                        .activityType(CollectionActivityType.PROMISE_CREATED)
                        .description("Customer promised to pay ₹" + pr.getPromisedAmount() + " by " + pr.getPromisedDate() + (pr.getNotes() != null ? " (" + pr.getNotes() + ")" : ""))
                        .performedBy(pr.getCreatedBy() != null ? pr.getCreatedBy() : "System")
                        .createdAt(pr.getCreatedAt() != null ? pr.getCreatedAt() : LocalDateTime.now())
                        .build());
            }

            synth.sort(Comparator.comparing(CustomerCollectionActivity::getCreatedAt).reversed());
            return synth;
        }
        return list;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DebtorPaymentHistoryItemDto> getCustomerPaymentHistory(Long firmId, Long customerId) {
        List<InvoicePayment> payments = invoicePaymentRepo.findByFirmIdAndCustomerIdOrderByPaymentDateAscIdAsc(firmId, customerId);
        List<Invoice> custInvoices = invoiceRepo.findByFirmIdAndCustomer_Id(firmId, customerId);
        Map<Long, String> invoiceNumMap = custInvoices.stream()
                .collect(Collectors.toMap(Invoice::getId, Invoice::getInvoiceNumber, (a, b) -> a));

        List<DebtorPaymentHistoryItemDto> result = new ArrayList<>();
        for (InvoicePayment p : payments) {
            String invNum = p.getInvoiceId() != null ? invoiceNumMap.get(p.getInvoiceId()) : null;
            result.add(DebtorPaymentHistoryItemDto.builder()
                    .paymentId(p.getId())
                    .paymentDate(p.getPaymentDate())
                    .amount(p.getAmount())
                    .paymentMode(p.getPaymentMode())
                    .referenceNumber(p.getReferenceNumber())
                    .invoiceId(p.getInvoiceId())
                    .invoiceNumber(invNum)
                    .notes(p.getNotes())
                    .isAdvanceCredit(p.getInvoiceId() == null)
                    .build());
        }

        result.sort(Comparator.comparing(DebtorPaymentHistoryItemDto::getPaymentDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(DebtorPaymentHistoryItemDto::getPaymentId).reversed());
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DebtorFollowUpDto> getFollowUps(Long firmId, String statusFilter) {
        LocalDate today = LocalDate.now();
        List<Customer> allCustomers = customerRepo.findByFirmId(firmId);
        Map<Long, Customer> customerMap = allCustomers.stream()
                .collect(Collectors.toMap(Customer::getId, c -> c, (a, b) -> a));

        List<CustomerPromiseToPay> promises = promiseRepo.findByFirmId(firmId);
        List<Reminder> reminders = reminderRepo.findByFirmId(firmId);

        List<DebtorFollowUpDto> result = new ArrayList<>();

        // 1. Follow-ups from active promises
        for (CustomerPromiseToPay pr : promises) {
            if (pr.getStatus() == PromiseStatus.PROMISED || pr.getStatus() == PromiseStatus.PARTIALLY_FULFILLED || pr.getStatus() == PromiseStatus.MISSED) {
                Customer c = customerMap.get(pr.getCustomerId());
                String cName = c != null ? c.getName() : "Customer #" + pr.getCustomerId();
                String cPhone = c != null ? c.getPhone() : null;

                String followUpStatus;
                if (pr.getStatus() == PromiseStatus.MISSED) {
                    followUpStatus = "MISSED";
                } else if (pr.getPromisedDate().isBefore(today)) {
                    followUpStatus = "OVERDUE";
                } else if (pr.getPromisedDate().isEqual(today)) {
                    followUpStatus = "DUE_TODAY";
                } else {
                    followUpStatus = "UPCOMING";
                }

                result.add(DebtorFollowUpDto.builder()
                        .id(pr.getId())
                        .customerId(pr.getCustomerId())
                        .customerName(cName)
                        .customerPhone(cPhone)
                        .outstandingAmount(pr.getRemainingAmount())
                        .overdueAmount(pr.getRemainingAmount())
                        .dueDate(pr.getPromisedDate())
                        .type("PROMISE_FOLLOW_UP")
                        .title("Promise to Pay ₹" + pr.getRemainingAmount())
                        .notes(pr.getNotes())
                        .status(followUpStatus)
                        .source("PROMISE")
                        .sourceId(pr.getId())
                        .build());
            }
        }

        // 2. Follow-ups from reminders linked to customers
        for (Reminder r : reminders) {
            if (!r.isCompleted() && r.getCustomerId() != null) {
                Customer c = customerMap.get(r.getCustomerId());
                String cName = c != null ? c.getName() : "Customer #" + r.getCustomerId();
                String cPhone = c != null ? c.getPhone() : null;

                LocalDate remDueDate = r.getDueDate() != null ? r.getDueDate().toLocalDate() : null;
                String followUpStatus;
                if (remDueDate == null) {
                    followUpStatus = "UPCOMING";
                } else if (remDueDate.isBefore(today)) {
                    followUpStatus = "OVERDUE";
                } else if (remDueDate.isEqual(today)) {
                    followUpStatus = "DUE_TODAY";
                } else {
                    followUpStatus = "UPCOMING";
                }

                result.add(DebtorFollowUpDto.builder()
                        .id(r.getId())
                        .customerId(r.getCustomerId())
                        .customerName(cName)
                        .customerPhone(cPhone)
                        .outstandingAmount(BigDecimal.ZERO)
                        .overdueAmount(BigDecimal.ZERO)
                        .dueDate(remDueDate)
                        .type("REMINDER")
                        .title(r.getTitle())
                        .notes(r.getNote())
                        .status(followUpStatus)
                        .source("REMINDER")
                        .sourceId(r.getId())
                        .build());
            }
        }

        // Filter
        if (statusFilter != null && !statusFilter.isBlank() && !"ALL".equalsIgnoreCase(statusFilter.trim())) {
            String target = statusFilter.trim().toUpperCase();
            result = result.stream()
                    .filter(f -> f.getStatus().equalsIgnoreCase(target)
                            || ("PROMISED".equalsIgnoreCase(target) && "PROMISE".equalsIgnoreCase(f.getSource())))
                    .collect(Collectors.toList());
        }

        result.sort(Comparator.comparing(DebtorFollowUpDto::getDueDate, Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public com.billing.simple.billsoft.dtos.debtor.PromiseWorkspaceDto getPromisesWorkspace(Long firmId, String statusFilter, String search) {
        LocalDate today = LocalDate.now();
        List<Customer> customers = customerRepo.findByFirmId(firmId);
        Map<Long, Customer> customerMap = customers.stream()
                .collect(Collectors.toMap(Customer::getId, c -> c, (a, b) -> a));

        List<Invoice> invoices = invoiceRepo.findByFirmId(firmId);
        Map<Long, Invoice> invoiceMap = invoices.stream()
                .collect(Collectors.toMap(Invoice::getId, inv -> inv, (a, b) -> a));

        List<InvoicePayment> allPayments = invoicePaymentRepo.findByFirmId(firmId);
        Map<Long, BigDecimal> paymentsByInvoice = new HashMap<>();
        for (InvoicePayment p : allPayments) {
            if (p.getInvoiceId() != null && p.getAmount() != null) {
                paymentsByInvoice.merge(p.getInvoiceId(), p.getAmount(), BigDecimal::add);
            }
        }

        List<SalesReturn> allReturns = salesReturnRepo.findByFirmId(firmId);
        Map<Long, BigDecimal> returnsByInvoice = new HashMap<>();
        for (SalesReturn r : allReturns) {
            if (r.getInvoice() != null && r.getInvoice().getId() != null && r.getTotalRefundAmount() != null) {
                returnsByInvoice.merge(r.getInvoice().getId(), r.getTotalRefundAmount(), BigDecimal::add);
            }
        }

        Map<Long, BigDecimal> invoiceOutstandingMap = new HashMap<>();
        for (Invoice inv : invoices) {
            BigDecimal rawTotal = nz(inv.getTotalAmount());
            BigDecimal ret = returnsByInvoice.getOrDefault(inv.getId(), BigDecimal.ZERO);
            BigDecimal eff = rawTotal.subtract(ret);
            if (eff.compareTo(BigDecimal.ZERO) < 0) eff = BigDecimal.ZERO;
            BigDecimal paid = paymentsByInvoice.getOrDefault(inv.getId(), BigDecimal.ZERO);
            if (paid.compareTo(BigDecimal.ZERO) == 0 && Boolean.TRUE.equals(inv.getPaid())) {
                paid = eff;
            }
            BigDecimal out = eff.subtract(paid);
            if (out.compareTo(BigDecimal.ZERO) < 0) out = BigDecimal.ZERO;
            invoiceOutstandingMap.put(inv.getId(), out);
        }

        List<CustomerPromiseToPay> allPromises = promiseRepo.findByFirmIdOrderByPromisedDateDesc(firmId);

        Map<Long, BigDecimal> activePromisesTotalByInvoice = new HashMap<>();
        for (CustomerPromiseToPay pr : allPromises) {
            if (pr.getInvoiceId() != null && pr.getStatus() != PromiseStatus.CANCELLED && pr.getStatus() != PromiseStatus.FULFILLED && pr.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0) {
                activePromisesTotalByInvoice.merge(pr.getInvoiceId(), pr.getRemainingAmount(), BigDecimal::add);
            }
        }

        int activeCount = 0;
        int dueTodayCount = 0;
        int overdueCount = 0;
        int fulfilledCount = 0;

        List<com.billing.simple.billsoft.dtos.debtor.DebtorPromiseDetailDto> dtoList = new ArrayList<>();

        for (CustomerPromiseToPay pr : allPromises) {
            Customer c = customerMap.get(pr.getCustomerId());
            String cName = c != null ? c.getName() : "Customer #" + pr.getCustomerId();
            String cPhone = c != null ? c.getPhone() : "";

            Invoice inv = pr.getInvoiceId() != null ? invoiceMap.get(pr.getInvoiceId()) : null;
            String invNum = inv != null ? (inv.getInvoiceNumber() != null ? inv.getInvoiceNumber() : "#" + inv.getId()) : "—";
            BigDecimal invOut = pr.getInvoiceId() != null ? invoiceOutstandingMap.getOrDefault(pr.getInvoiceId(), BigDecimal.ZERO) : BigDecimal.ZERO;

            BigDecimal invRemainingPromises = pr.getInvoiceId() != null ? activePromisesTotalByInvoice.getOrDefault(pr.getInvoiceId(), BigDecimal.ZERO) : BigDecimal.ZERO;
            boolean reconciliationRequired = pr.getInvoiceId() != null && invRemainingPromises.compareTo(invOut) > 0;
            BigDecimal excessAmt = reconciliationRequired ? invRemainingPromises.subtract(invOut) : BigDecimal.ZERO;

            boolean isActive = pr.getStatus() != PromiseStatus.CANCELLED && pr.getStatus() != PromiseStatus.FULFILLED && pr.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0;
            boolean isOverdue = isActive && pr.getPromisedDate().isBefore(today);
            boolean isDueToday = isActive && pr.getPromisedDate().isEqual(today);
            boolean isFulfilled = pr.getStatus() == PromiseStatus.FULFILLED || (pr.getStatus() != PromiseStatus.CANCELLED && pr.getRemainingAmount().compareTo(BigDecimal.ZERO) <= 0);

            if (isActive) activeCount++;
            if (isDueToday) dueTodayCount++;
            if (isOverdue) overdueCount++;
            if (isFulfilled) fulfilledCount++;

            String displayStatus;
            if (pr.getStatus() == PromiseStatus.CANCELLED) {
                displayStatus = "CANCELLED";
            } else if (isFulfilled) {
                displayStatus = "FULFILLED";
            } else if (pr.getStatus() == PromiseStatus.PARTIALLY_FULFILLED || (pr.getFulfilledAmount() != null && pr.getFulfilledAmount().compareTo(BigDecimal.ZERO) > 0)) {
                displayStatus = "PARTIALLY_FULFILLED";
            } else if (isOverdue) {
                displayStatus = "OVERDUE";
            } else if (isDueToday) {
                displayStatus = "DUE_TODAY";
            } else {
                displayStatus = "UPCOMING";
            }

            com.billing.simple.billsoft.dtos.debtor.DebtorPromiseDetailDto dto = com.billing.simple.billsoft.dtos.debtor.DebtorPromiseDetailDto.builder()
                    .id(pr.getId())
                    .firmId(pr.getFirmId())
                    .customerId(pr.getCustomerId())
                    .customerName(cName)
                    .customerPhone(cPhone)
                    .invoiceId(pr.getInvoiceId())
                    .invoiceNumber(invNum)
                    .invoiceOutstanding(invOut)
                    .promisedAmount(pr.getPromisedAmount())
                    .promisedDate(pr.getPromisedDate())
                    .fulfilledAmount(pr.getFulfilledAmount())
                    .remainingAmount(pr.getRemainingAmount())
                    .status(pr.getStatus())
                    .displayStatus(displayStatus)
                    .isOverdue(isOverdue)
                    .notes(pr.getNotes())
                    .createdBy(pr.getCreatedBy())
                    .createdAt(pr.getCreatedAt())
                    .updatedAt(pr.getUpdatedAt())
                    .invoiceRemainingPromisesTotal(invRemainingPromises)
                    .reconciliationRequired(reconciliationRequired)
                    .excessPromisedAmount(excessAmt)
                    .build();

            boolean matchesStatus = true;
            if (statusFilter != null && !statusFilter.isBlank() && !"ALL".equalsIgnoreCase(statusFilter.trim())) {
                String filter = statusFilter.trim().toUpperCase();
                switch (filter) {
                    case "UPCOMING":
                        matchesStatus = isActive && pr.getPromisedDate().isAfter(today);
                        break;
                    case "DUE_TODAY":
                        matchesStatus = isDueToday;
                        break;
                    case "OVERDUE":
                        matchesStatus = isOverdue;
                        break;
                    case "PARTIALLY_FULFILLED":
                        matchesStatus = "PARTIALLY_FULFILLED".equalsIgnoreCase(displayStatus);
                        break;
                    case "FULFILLED":
                        matchesStatus = isFulfilled;
                        break;
                    case "CANCELLED":
                        matchesStatus = pr.getStatus() == PromiseStatus.CANCELLED;
                        break;
                    default:
                        matchesStatus = displayStatus.equalsIgnoreCase(filter) || pr.getStatus().name().equalsIgnoreCase(filter);
                        break;
                }
            }

            boolean matchesSearch = true;
            if (search != null && !search.isBlank()) {
                String q = search.trim().toLowerCase();
                matchesSearch = cName.toLowerCase().contains(q)
                        || (cPhone != null && cPhone.contains(q))
                        || (invNum != null && invNum.toLowerCase().contains(q))
                        || (pr.getNotes() != null && pr.getNotes().toLowerCase().contains(q));
            }

            if (matchesStatus && matchesSearch) {
                dtoList.add(dto);
            }
        }

        return com.billing.simple.billsoft.dtos.debtor.PromiseWorkspaceDto.builder()
                .activeCount(activeCount)
                .dueTodayCount(dueTodayCount)
                .overdueCount(overdueCount)
                .fulfilledCount(fulfilledCount)
                .promises(dtoList)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.billing.simple.billsoft.dtos.debtor.DebtorPromiseDetailDto> getInvoicePromises(Long firmId, Long invoiceId) {
        com.billing.simple.billsoft.dtos.debtor.PromiseWorkspaceDto ws = getPromisesWorkspace(firmId, "ALL", null);
        return ws.getPromises().stream()
                .filter(p -> Objects.equals(p.getInvoiceId(), invoiceId))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CustomerPromiseToPay createPromise(Long firmId, CreatePromiseRequest request, String createdBy) {
        if (request == null || request.getCustomerId() == null) {
            throw new IllegalArgumentException("Customer ID is required");
        }
        if (request.getPromisedAmount() == null || request.getPromisedAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Promised amount must be greater than 0");
        }
        if (request.getPromisedDate() == null) {
            throw new IllegalArgumentException("Promised date is required");
        }

        Customer customer = customerRepo.findByIdAndFirmId(request.getCustomerId(), firmId)
                .orElseThrow(() -> new TenantSecurityException("Customer #" + request.getCustomerId() + " not found in firm"));

        if (request.getInvoiceId() != null) {
            Invoice inv = invoiceRepo.findByIdAndFirmId(request.getInvoiceId(), firmId)
                    .orElseThrow(() -> new TenantSecurityException("Invoice #" + request.getInvoiceId() + " not found in firm"));

            if (inv.getCustomer() == null || !Objects.equals(inv.getCustomer().getId(), request.getCustomerId())) {
                throw new IllegalArgumentException("Invoice #" + (inv.getInvoiceNumber() != null ? inv.getInvoiceNumber() : inv.getId()) + " does not belong to Customer #" + request.getCustomerId());
            }

            if (inv.getStatus() == InvoiceStatus.CANCELLED || inv.getStatus() == InvoiceStatus.DRAFT || inv.getStatus() == InvoiceStatus.ESTIMATE) {
                throw new IllegalStateException("Cannot create a promise against a " + inv.getStatus() + " document");
            }

            List<InvoicePayment> existingPayments = invoicePaymentRepo.findByInvoiceIdAndFirmIdOrderByPaymentDateAscIdAsc(inv.getId(), firmId);
            BigDecimal paidSum = existingPayments.stream().map(InvoicePayment::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);

            List<SalesReturn> returns = salesReturnRepo.findByInvoiceIdAndFirmIdOrderByCreatedAtDesc(inv.getId(), firmId);
            BigDecimal returnSum = returns.stream().map(SalesReturn::getTotalRefundAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal rawTotal = nz(inv.getTotalAmount());
            BigDecimal eff = rawTotal.subtract(returnSum);
            if (eff.compareTo(BigDecimal.ZERO) < 0) eff = BigDecimal.ZERO;
            BigDecimal invoiceOutstanding = eff.subtract(paidSum);
            if (invoiceOutstanding.compareTo(BigDecimal.ZERO) < 0) invoiceOutstanding = BigDecimal.ZERO;

            if (invoiceOutstanding.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Invoice #" + (inv.getInvoiceNumber() != null ? inv.getInvoiceNumber() : inv.getId()) + " is already fully paid or settled");
            }

            List<CustomerPromiseToPay> existingPromises = promiseRepo.findByFirmIdAndInvoiceIdAndStatusNotIn(
                    firmId, inv.getId(), List.of(PromiseStatus.CANCELLED, PromiseStatus.FULFILLED)
            );
            BigDecimal existingCommitted = existingPromises.stream()
                    .map(CustomerPromiseToPay::getRemainingAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal availableBalance = invoiceOutstanding.subtract(existingCommitted);
            if (availableBalance.compareTo(BigDecimal.ZERO) < 0) availableBalance = BigDecimal.ZERO;

            BigDecimal requested = request.getPromisedAmount().setScale(SCALE, RoundingMode.HALF_UP);
            if (requested.compareTo(availableBalance) > 0) {
                throw new IllegalArgumentException("Promised amount ₹" + requested + " exceeds available invoice balance ₹" + availableBalance +
                        " (Invoice Outstanding: ₹" + invoiceOutstanding + ", Existing Active Promises: ₹" + existingCommitted + ")");
            }
        }

        CustomerPromiseToPay promise = CustomerPromiseToPay.builder()
                .firmId(firmId)
                .customerId(request.getCustomerId())
                .invoiceId(request.getInvoiceId())
                .promisedAmount(request.getPromisedAmount().setScale(SCALE, RoundingMode.HALF_UP))
                .promisedDate(request.getPromisedDate())
                .fulfilledAmount(BigDecimal.ZERO)
                .remainingAmount(request.getPromisedAmount().setScale(SCALE, RoundingMode.HALF_UP))
                .status(PromiseStatus.PROMISED)
                .notes(request.getNotes())
                .createdBy(createdBy != null ? createdBy : "User")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        CustomerPromiseToPay saved = promiseRepo.save(promise);

        if (promiseHistoryRepo != null) {
            promiseHistoryRepo.save(com.billing.simple.billsoft.entities.CustomerPromiseHistory.builder()
                    .firmId(firmId)
                    .promiseId(saved.getId())
                    .actionType("CREATED")
                    .newAmount(saved.getPromisedAmount())
                    .newDate(saved.getPromisedDate())
                    .newStatus(saved.getStatus().name())
                    .notes(saved.getNotes())
                    .performedBy(createdBy != null ? createdBy : "User")
                    .createdAt(LocalDateTime.now())
                    .build());
        }

        activityRepo.save(CustomerCollectionActivity.builder()
                .firmId(firmId)
                .customerId(request.getCustomerId())
                .invoiceId(request.getInvoiceId())
                .promiseId(saved.getId())
                .activityType(CollectionActivityType.PROMISE_CREATED)
                .description("Promise to pay recorded: ₹" + saved.getPromisedAmount() + " by " + saved.getPromisedDate() +
                        (request.getNotes() != null && !request.getNotes().isBlank() ? " (" + request.getNotes() + ")" : ""))
                .performedBy(createdBy != null ? createdBy : "User")
                .createdAt(LocalDateTime.now())
                .build());

        return saved;
    }

    @Override
    @Transactional
    public CustomerPromiseToPay editPromise(Long firmId, Long promiseId, com.billing.simple.billsoft.dtos.debtor.EditPromiseRequest request, String performedBy) {
        CustomerPromiseToPay promise = promiseRepo.findByIdAndFirmId(promiseId, firmId)
                .orElseThrow(() -> new TenantSecurityException("Promise #" + promiseId + " not found in firm"));

        if (promise.getStatus() == PromiseStatus.CANCELLED) {
            throw new IllegalStateException("Cannot edit CANCELLED promise #" + promiseId);
        }

        BigDecimal prevAmount = promise.getPromisedAmount();
        LocalDate prevDate = promise.getPromisedDate();
        PromiseStatus prevStatus = promise.getStatus();

        if (request.getPromisedAmount() != null) {
            BigDecimal newAmt = request.getPromisedAmount().setScale(SCALE, RoundingMode.HALF_UP);
            if (newAmt.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Promised amount must be greater than 0");
            }
            BigDecimal fulfilled = promise.getFulfilledAmount() != null ? promise.getFulfilledAmount() : BigDecimal.ZERO;
            if (newAmt.compareTo(fulfilled) < 0) {
                throw new IllegalArgumentException("Promised amount ₹" + newAmt + " cannot be less than already fulfilled amount ₹" + fulfilled);
            }

            if (promise.getInvoiceId() != null) {
                Invoice inv = invoiceRepo.findByIdAndFirmId(promise.getInvoiceId(), firmId).orElse(null);
                if (inv != null) {
                    List<InvoicePayment> existingPayments = invoicePaymentRepo.findByInvoiceIdAndFirmIdOrderByPaymentDateAscIdAsc(inv.getId(), firmId);
                    BigDecimal paidSum = existingPayments.stream().map(InvoicePayment::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
                    List<SalesReturn> returns = salesReturnRepo.findByInvoiceIdAndFirmIdOrderByCreatedAtDesc(inv.getId(), firmId);
                    BigDecimal returnSum = returns.stream().map(SalesReturn::getTotalRefundAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal eff = nz(inv.getTotalAmount()).subtract(returnSum);
                    if (eff.compareTo(BigDecimal.ZERO) < 0) eff = BigDecimal.ZERO;
                    BigDecimal invoiceOutstanding = eff.subtract(paidSum);
                    if (invoiceOutstanding.compareTo(BigDecimal.ZERO) < 0) invoiceOutstanding = BigDecimal.ZERO;

                    List<CustomerPromiseToPay> otherPromises = promiseRepo.findByFirmIdAndInvoiceIdAndStatusNotIn(
                            firmId, inv.getId(), List.of(PromiseStatus.CANCELLED, PromiseStatus.FULFILLED)
                    ).stream().filter(p -> !p.getId().equals(promiseId)).collect(Collectors.toList());

                    BigDecimal otherCommitted = otherPromises.stream().map(CustomerPromiseToPay::getRemainingAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal maxAllowed = invoiceOutstanding.subtract(otherCommitted).add(fulfilled);
                    if (newAmt.compareTo(maxAllowed) > 0) {
                        throw new IllegalArgumentException("Revised amount ₹" + newAmt + " exceeds available balance ₹" + maxAllowed +
                                " (Invoice Outstanding: ₹" + invoiceOutstanding + ", Other Active Promises: ₹" + otherCommitted + ")");
                    }
                }
            }

            promise.setPromisedAmount(newAmt);
            BigDecimal newRemaining = newAmt.subtract(fulfilled);
            if (newRemaining.compareTo(BigDecimal.ZERO) < 0) newRemaining = BigDecimal.ZERO;
            promise.setRemainingAmount(newRemaining);
            if (newRemaining.compareTo(BigDecimal.ZERO) == 0) {
                promise.setStatus(PromiseStatus.FULFILLED);
            } else if (fulfilled.compareTo(BigDecimal.ZERO) > 0) {
                promise.setStatus(PromiseStatus.PARTIALLY_FULFILLED);
            } else {
                promise.setStatus(PromiseStatus.PROMISED);
            }
        }

        if (request.getPromisedDate() != null) {
            promise.setPromisedDate(request.getPromisedDate());
        }

        if (request.getNotes() != null) {
            promise.setNotes(request.getNotes());
        }

        promise.setUpdatedAt(LocalDateTime.now());
        CustomerPromiseToPay saved = promiseRepo.save(promise);

        if (promiseHistoryRepo != null) {
            promiseHistoryRepo.save(com.billing.simple.billsoft.entities.CustomerPromiseHistory.builder()
                    .firmId(firmId)
                    .promiseId(saved.getId())
                    .actionType("EDITED")
                    .previousAmount(prevAmount)
                    .newAmount(saved.getPromisedAmount())
                    .previousDate(prevDate)
                    .newDate(saved.getPromisedDate())
                    .previousStatus(prevStatus != null ? prevStatus.name() : null)
                    .newStatus(saved.getStatus().name())
                    .notes(request.getNotes())
                    .performedBy(performedBy != null ? performedBy : "User")
                    .createdAt(LocalDateTime.now())
                    .build());
        }

        activityRepo.save(CustomerCollectionActivity.builder()
                .firmId(firmId)
                .customerId(saved.getCustomerId())
                .invoiceId(saved.getInvoiceId())
                .promiseId(saved.getId())
                .activityType(CollectionActivityType.PROMISE_RESCHEDULED)
                .description("Promise #" + saved.getId() + " edited (Amount: ₹" + saved.getPromisedAmount() + ", Date: " + saved.getPromisedDate() + ")")
                .performedBy(performedBy != null ? performedBy : "User")
                .createdAt(LocalDateTime.now())
                .build());

        return saved;
    }

    @Override
    @Transactional
    public CustomerPromiseToPay cancelPromise(Long firmId, Long promiseId, String reason, String performedBy) {
        CustomerPromiseToPay promise = promiseRepo.findByIdAndFirmId(promiseId, firmId)
                .orElseThrow(() -> new TenantSecurityException("Promise #" + promiseId + " not found in firm"));

        PromiseStatus prevStatus = promise.getStatus();
        promise.setStatus(PromiseStatus.CANCELLED);
        promise.setUpdatedAt(LocalDateTime.now());
        CustomerPromiseToPay saved = promiseRepo.save(promise);

        if (promiseHistoryRepo != null) {
            promiseHistoryRepo.save(com.billing.simple.billsoft.entities.CustomerPromiseHistory.builder()
                    .firmId(firmId)
                    .promiseId(saved.getId())
                    .actionType("CANCELLED")
                    .previousAmount(saved.getPromisedAmount())
                    .newAmount(saved.getPromisedAmount())
                    .previousDate(saved.getPromisedDate())
                    .newDate(saved.getPromisedDate())
                    .previousStatus(prevStatus != null ? prevStatus.name() : null)
                    .newStatus("CANCELLED")
                    .notes(reason != null ? reason : "Cancelled by user")
                    .performedBy(performedBy != null ? performedBy : "User")
                    .createdAt(LocalDateTime.now())
                    .build());
        }

        activityRepo.save(CustomerCollectionActivity.builder()
                .firmId(firmId)
                .customerId(saved.getCustomerId())
                .invoiceId(saved.getInvoiceId())
                .promiseId(saved.getId())
                .activityType(CollectionActivityType.FOLLOW_UP_COMPLETED)
                .description("Promise #" + saved.getId() + " cancelled" + (reason != null ? ": " + reason : ""))
                .performedBy(performedBy != null ? performedBy : "User")
                .createdAt(LocalDateTime.now())
                .build());

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.billing.simple.billsoft.entities.CustomerPromiseHistory> getPromiseHistory(Long firmId, Long promiseId) {
        if (promiseHistoryRepo == null) return Collections.emptyList();
        return promiseHistoryRepo.findByFirmIdAndPromiseIdOrderByCreatedAtDesc(firmId, promiseId);
    }

    @Override
    @Transactional
    public CustomerPromiseToPay updatePromiseStatus(Long firmId, Long promiseId, UpdatePromiseStatusRequest request) {
        CustomerPromiseToPay promise = promiseRepo.findByIdAndFirmId(promiseId, firmId)
                .orElseThrow(() -> new TenantSecurityException("Promise #" + promiseId + " not found in firm"));

        if (request.getStatus() != null) {
            promise.setStatus(request.getStatus());
        }

        if (request.getRescheduledDate() != null) {
            promise.setPromisedDate(request.getRescheduledDate());
            promise.setStatus(PromiseStatus.RESCHEDULED);
        }

        if (request.getFulfilledAmount() != null) {
            BigDecimal fulfilled = request.getFulfilledAmount().setScale(SCALE, RoundingMode.HALF_UP);
            promise.setFulfilledAmount(fulfilled);
            BigDecimal remaining = promise.getPromisedAmount().subtract(fulfilled);
            promise.setRemainingAmount(remaining.compareTo(BigDecimal.ZERO) > 0 ? remaining : BigDecimal.ZERO);
            if (promise.getRemainingAmount().compareTo(BigDecimal.ZERO) <= 0) {
                promise.setStatus(PromiseStatus.FULFILLED);
            } else if (fulfilled.compareTo(BigDecimal.ZERO) > 0) {
                promise.setStatus(PromiseStatus.PARTIALLY_FULFILLED);
            }
        }

        if (request.getNotes() != null) {
            promise.setNotes(request.getNotes());
        }

        promise.setUpdatedAt(LocalDateTime.now());
        CustomerPromiseToPay saved = promiseRepo.save(promise);

        // Log collection activity
        CollectionActivityType actType = CollectionActivityType.PROMISE_RESCHEDULED;
        if (saved.getStatus() == PromiseStatus.FULFILLED) actType = CollectionActivityType.PROMISE_FULFILLED;
        else if (saved.getStatus() == PromiseStatus.MISSED) actType = CollectionActivityType.PROMISE_MISSED;

        activityRepo.save(CustomerCollectionActivity.builder()
                .firmId(firmId)
                .customerId(saved.getCustomerId())
                .promiseId(saved.getId())
                .activityType(actType)
                .description("Promise #" + saved.getId() + " status updated to " + saved.getStatus() +
                        (request.getNotes() != null ? " - " + request.getNotes() : ""))
                .performedBy("User")
                .createdAt(LocalDateTime.now())
                .build());

        return saved;
    }

    @Override
    @Transactional
    public CustomerCollectionActivity logActivity(Long firmId, LogActivityRequest request, String performedBy) {
        if (request == null || request.getCustomerId() == null) {
            throw new IllegalArgumentException("Customer ID is required");
        }
        Customer customer = customerRepo.findByIdAndFirmId(request.getCustomerId(), firmId)
                .orElseThrow(() -> new TenantSecurityException("Customer #" + request.getCustomerId() + " not found in firm"));

        CustomerCollectionActivity act = CustomerCollectionActivity.builder()
                .firmId(firmId)
                .customerId(request.getCustomerId())
                .invoiceId(request.getInvoiceId())
                .paymentId(request.getPaymentId())
                .promiseId(request.getPromiseId())
                .activityType(request.getActivityType() != null ? request.getActivityType() : CollectionActivityType.NOTE_ADDED)
                .description(request.getDescription())
                .performedBy(performedBy != null ? performedBy : "User")
                .createdAt(LocalDateTime.now())
                .build();

        return activityRepo.save(act);
    }

    @Override
    @Transactional
    public void reconcilePaymentWithPromises(Long firmId, Long customerId, BigDecimal paymentAmount, Long paymentId) {
        if (paymentAmount == null || paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        List<CustomerPromiseToPay> activePromises = new ArrayList<>(promiseRepo.findByFirmIdAndCustomerIdAndStatusIn(
                firmId,
                customerId,
                List.of(PromiseStatus.PROMISED, PromiseStatus.PARTIALLY_FULFILLED)
        ));

        activePromises.sort(Comparator.comparing(CustomerPromiseToPay::getPromisedDate));
        BigDecimal availableToReconcile = paymentAmount;

        for (CustomerPromiseToPay pr : activePromises) {
            if (availableToReconcile.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal remaining = pr.getRemainingAmount();
            if (availableToReconcile.compareTo(remaining) >= 0) {
                // Fully fulfilled
                pr.setFulfilledAmount(pr.getPromisedAmount());
                pr.setRemainingAmount(BigDecimal.ZERO);
                pr.setStatus(PromiseStatus.FULFILLED);
                pr.setUpdatedAt(LocalDateTime.now());
                promiseRepo.save(pr);

                activityRepo.save(CustomerCollectionActivity.builder()
                        .firmId(firmId)
                        .customerId(customerId)
                        .paymentId(paymentId)
                        .promiseId(pr.getId())
                        .activityType(CollectionActivityType.PROMISE_FULFILLED)
                        .description("Promise of ₹" + pr.getPromisedAmount() + " due on " + pr.getPromisedDate() + " fully fulfilled by payment")
                        .performedBy("System")
                        .createdAt(LocalDateTime.now())
                        .build());

                availableToReconcile = availableToReconcile.subtract(remaining);
            } else {
                // Partially fulfilled
                BigDecimal newFulfilled = pr.getFulfilledAmount().add(availableToReconcile);
                pr.setFulfilledAmount(newFulfilled);
                pr.setRemainingAmount(pr.getPromisedAmount().subtract(newFulfilled));
                pr.setStatus(PromiseStatus.PARTIALLY_FULFILLED);
                pr.setUpdatedAt(LocalDateTime.now());
                promiseRepo.save(pr);

                activityRepo.save(CustomerCollectionActivity.builder()
                        .firmId(firmId)
                        .customerId(customerId)
                        .paymentId(paymentId)
                        .promiseId(pr.getId())
                        .activityType(CollectionActivityType.PAYMENT_RECEIVED)
                        .description("Promise of ₹" + pr.getPromisedAmount() + " partially fulfilled with ₹" + availableToReconcile)
                        .performedBy("System")
                        .createdAt(LocalDateTime.now())
                        .build());

                availableToReconcile = BigDecimal.ZERO;
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportDebtorsCsv(Long firmId) {
        List<DebtorListItemDto> list = getDebtors(firmId, null, "all", "outstanding", "desc");
        StringBuilder sb = new StringBuilder();
        // UTF-8 BOM for Excel compatibility
        sb.append('\ufeff');
        sb.append("Customer Name,Customer ID,Phone,Total Outstanding,Overdue Amount,Current Amount,0-30 Days,31-60 Days,61-90 Days,90+ Days,Last Payment Date,Last Payment Amount,Next Follow-up,Risk Status\n");

        BigDecimal sumOut = BigDecimal.ZERO;
        BigDecimal sumOverdue = BigDecimal.ZERO;
        BigDecimal sumCurrent = BigDecimal.ZERO;
        BigDecimal sum0To30 = BigDecimal.ZERO;
        BigDecimal sum31To60 = BigDecimal.ZERO;
        BigDecimal sum61To90 = BigDecimal.ZERO;
        BigDecimal sum90Plus = BigDecimal.ZERO;

        for (DebtorListItemDto d : list) {
            BigDecimal outAmt = d.getOutstandingAmount() != null ? d.getOutstandingAmount() : BigDecimal.ZERO;
            BigDecimal ovdAmt = d.getOverdueAmount() != null ? d.getOverdueAmount() : BigDecimal.ZERO;
            BigDecimal curAmt = outAmt.subtract(ovdAmt).max(BigDecimal.ZERO);
            BigDecimal a0 = d.getAging0To30() != null ? d.getAging0To30() : BigDecimal.ZERO;
            BigDecimal a30 = d.getAging31To60() != null ? d.getAging31To60() : BigDecimal.ZERO;
            BigDecimal a60 = d.getAging61To90() != null ? d.getAging61To90() : BigDecimal.ZERO;
            BigDecimal a90 = d.getAging90Plus() != null ? d.getAging90Plus() : BigDecimal.ZERO;

            sumOut = sumOut.add(outAmt);
            sumOverdue = sumOverdue.add(ovdAmt);
            sumCurrent = sumCurrent.add(curAmt);
            sum0To30 = sum0To30.add(a0);
            sum31To60 = sum31To60.add(a30);
            sum61To90 = sum61To90.add(a60);
            sum90Plus = sum90Plus.add(a90);

            String nextFollowUp = d.getNextFollowUpDate() != null ? d.getNextFollowUpDate().toString() :
                    (d.getActivePromiseDate() != null ? ("Promise: " + d.getActivePromiseDate()) : "");

            sb.append(escapeCsv(d.getCustomerName())).append(",")
                    .append(d.getCustomerId() != null ? d.getCustomerId().toString() : "").append(",")
                    .append(escapeCsv(d.getCustomerPhone())).append(",")
                    .append(outAmt.setScale(SCALE, RoundingMode.HALF_UP)).append(",")
                    .append(ovdAmt.setScale(SCALE, RoundingMode.HALF_UP)).append(",")
                    .append(curAmt.setScale(SCALE, RoundingMode.HALF_UP)).append(",")
                    .append(a0.setScale(SCALE, RoundingMode.HALF_UP)).append(",")
                    .append(a30.setScale(SCALE, RoundingMode.HALF_UP)).append(",")
                    .append(a60.setScale(SCALE, RoundingMode.HALF_UP)).append(",")
                    .append(a90.setScale(SCALE, RoundingMode.HALF_UP)).append(",")
                    .append(d.getLastPaymentDate() != null ? d.getLastPaymentDate().toString() : "").append(",")
                    .append(d.getLastPaymentAmount() != null ? d.getLastPaymentAmount().setScale(SCALE, RoundingMode.HALF_UP).toString() : "").append(",")
                    .append(escapeCsv(nextFollowUp)).append(",")
                    .append(escapeCsv(d.getRiskLevel() != null ? d.getRiskLevel() : "LOW"))
                    .append("\n");
        }

        // Summary Totals Row
        sb.append("TOTAL,,,")
                .append(sumOut.setScale(SCALE, RoundingMode.HALF_UP)).append(",")
                .append(sumOverdue.setScale(SCALE, RoundingMode.HALF_UP)).append(",")
                .append(sumCurrent.setScale(SCALE, RoundingMode.HALF_UP)).append(",")
                .append(sum0To30.setScale(SCALE, RoundingMode.HALF_UP)).append(",")
                .append(sum31To60.setScale(SCALE, RoundingMode.HALF_UP)).append(",")
                .append(sum61To90.setScale(SCALE, RoundingMode.HALF_UP)).append(",")
                .append(sum90Plus.setScale(SCALE, RoundingMode.HALF_UP)).append(",")
                .append(",,,")
                .append("\n");

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportDebtorsExcel(Long firmId) {
        // For standard spreadsheet import, CSV with BOM and tab separators or commas works across Excel/Sheets
        return exportDebtorsCsv(firmId);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportDebtorsPdf(Long firmId) throws Exception {
        FirmDetails firm = firmId != null ? firmRepo.findById(firmId).orElse(null) : null;
        String firmName = firm != null && firm.getFirmName() != null && !firm.getFirmName().isBlank() ? firm.getFirmName() : "RupeeCRM Business";
        String firmAddress = firm != null && firm.getAddressLine1() != null ? firm.getAddressLine1() : "";
        String firmGstin = firm != null && firm.getGstin() != null && !firm.getGstin().isBlank() ? ("GSTIN: " + firm.getGstin()) : "";

        List<DebtorListItemDto> list = getDebtors(firmId, null, "all", "outstanding", "desc");
        DebtorsOverviewDto overview = getOverview(firmId);

        Document doc = new Document(PageSize.A4.rotate(), 18, 18, 20, 24);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(doc, baos);
        writer.setPageEvent(new UniversalPageNumberHelper(8f));
        doc.open();

        // Fonts
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, new Color(15, 23, 42));
        Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, new Color(71, 85, 105));
        Font thFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
        Font tdFont = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, new Color(30, 41, 59));
        Font tdBoldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, new Color(15, 23, 42));

        // Header Section
        Paragraph title = new Paragraph(firmName + " — Debtors & Receivables Aging Register", titleFont);
        StringBuilder subSb = new StringBuilder();
        if (!firmAddress.isBlank()) subSb.append(firmAddress).append(" | ");
        if (!firmGstin.isBlank()) subSb.append(firmGstin).append(" | ");
        subSb.append("Generated on: ").append(LocalDate.now());
        Paragraph subtitle = new Paragraph(subSb.toString(), subtitleFont);
        doc.add(title);
        doc.add(subtitle);
        doc.add(new Paragraph(" "));

        // Executive Aging Summary Bar
        PdfPTable summaryTable = new PdfPTable(7);
        summaryTable.setWidthPercentage(100);
        summaryTable.setWidths(new float[]{1.4f, 1.4f, 1.4f, 1.2f, 1.2f, 1.2f, 1.2f});

        String[] sumHeaders = {"Total Debtors", "Total Outstanding", "Overdue Amount", "0-30 Days", "31-60 Days", "61-90 Days", "90+ Days"};
        for (String sh : sumHeaders) {
            PdfPCell cell = new PdfPCell(new Phrase(sh, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, new Color(51, 65, 85))));
            cell.setBackgroundColor(new Color(241, 245, 249));
            cell.setPadding(4);
            cell.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
            summaryTable.addCell(cell);
        }

        PdfPCell c1 = new PdfPCell(new Phrase(String.valueOf(overview != null ? overview.getTotalDebtorsCount() : 0), tdBoldFont));
        c1.setHorizontalAlignment(PdfPCell.ALIGN_CENTER); c1.setPadding(4); summaryTable.addCell(c1);

        PdfPCell c2 = new PdfPCell(new Phrase("Rs. " + (overview != null && overview.getTotalOutstanding() != null ? overview.getTotalOutstanding().setScale(2, RoundingMode.HALF_UP) : "0.00"), tdBoldFont));
        c2.setHorizontalAlignment(PdfPCell.ALIGN_CENTER); c2.setPadding(4); summaryTable.addCell(c2);

        PdfPCell c3 = new PdfPCell(new Phrase("Rs. " + (overview != null && overview.getOverdueAmount() != null ? overview.getOverdueAmount().setScale(2, RoundingMode.HALF_UP) : "0.00"), tdBoldFont));
        c3.setHorizontalAlignment(PdfPCell.ALIGN_CENTER); c3.setPadding(4); summaryTable.addCell(c3);

        PdfPCell c4 = new PdfPCell(new Phrase("Rs. " + (overview != null && overview.getAging0To30() != null ? overview.getAging0To30().setScale(2, RoundingMode.HALF_UP) : "0.00"), tdFont));
        c4.setHorizontalAlignment(PdfPCell.ALIGN_CENTER); c4.setPadding(4); summaryTable.addCell(c4);

        PdfPCell c5 = new PdfPCell(new Phrase("Rs. " + (overview != null && overview.getAging31To60() != null ? overview.getAging31To60().setScale(2, RoundingMode.HALF_UP) : "0.00"), tdFont));
        c5.setHorizontalAlignment(PdfPCell.ALIGN_CENTER); c5.setPadding(4); summaryTable.addCell(c5);

        PdfPCell c6 = new PdfPCell(new Phrase("Rs. " + (overview != null && overview.getAging61To90() != null ? overview.getAging61To90().setScale(2, RoundingMode.HALF_UP) : "0.00"), tdFont));
        c6.setHorizontalAlignment(PdfPCell.ALIGN_CENTER); c6.setPadding(4); summaryTable.addCell(c6);

        PdfPCell c7 = new PdfPCell(new Phrase("Rs. " + (overview != null && overview.getAging90Plus() != null ? overview.getAging90Plus().setScale(2, RoundingMode.HALF_UP) : "0.00"), tdFont));
        c7.setHorizontalAlignment(PdfPCell.ALIGN_CENTER); c7.setPadding(4); summaryTable.addCell(c7);

        doc.add(summaryTable);
        doc.add(new Paragraph(" "));

        // Detail Table (11 Columns)
        PdfPTable table = new PdfPTable(11);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2.2f, 0.9f, 1.3f, 1.2f, 1.2f, 1.1f, 1.1f, 1.1f, 1.1f, 0.9f, 1.8f});

        String[] headers = {"Customer", "ID", "Phone", "Outstanding", "Overdue", "0-30 Days", "31-60 Days", "61-90 Days", "90+ Days", "Risk", "Last Payment"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, thFont));
            cell.setBackgroundColor(new Color(15, 23, 42));
            cell.setPadding(4);
            cell.setHorizontalAlignment(h.contains("Outstanding") || h.contains("Overdue") || h.contains("Days") ? PdfPCell.ALIGN_RIGHT : PdfPCell.ALIGN_LEFT);
            table.addCell(cell);
        }

        BigDecimal sumOut = BigDecimal.ZERO;
        BigDecimal sumOverdue = BigDecimal.ZERO;
        BigDecimal sum0To30 = BigDecimal.ZERO;
        BigDecimal sum31To60 = BigDecimal.ZERO;
        BigDecimal sum61To90 = BigDecimal.ZERO;
        BigDecimal sum90Plus = BigDecimal.ZERO;

        for (int i = 0; i < list.size(); i++) {
            DebtorListItemDto d = list.get(i);
            Color rowBg = (i % 2 == 1) ? new Color(248, 250, 252) : Color.WHITE;

            BigDecimal outAmt = d.getOutstandingAmount() != null ? d.getOutstandingAmount() : BigDecimal.ZERO;
            BigDecimal ovdAmt = d.getOverdueAmount() != null ? d.getOverdueAmount() : BigDecimal.ZERO;
            BigDecimal a0 = d.getAging0To30() != null ? d.getAging0To30() : BigDecimal.ZERO;
            BigDecimal a30 = d.getAging31To60() != null ? d.getAging31To60() : BigDecimal.ZERO;
            BigDecimal a60 = d.getAging61To90() != null ? d.getAging61To90() : BigDecimal.ZERO;
            BigDecimal a90 = d.getAging90Plus() != null ? d.getAging90Plus() : BigDecimal.ZERO;

            sumOut = sumOut.add(outAmt);
            sumOverdue = sumOverdue.add(ovdAmt);
            sum0To30 = sum0To30.add(a0);
            sum31To60 = sum31To60.add(a30);
            sum61To90 = sum61To90.add(a60);
            sum90Plus = sum90Plus.add(a90);

            addCell(table, d.getCustomerName() != null ? d.getCustomerName() : "", tdFont, rowBg, PdfPCell.ALIGN_LEFT);
            addCell(table, d.getCustomerId() != null ? String.valueOf(d.getCustomerId()) : "", tdFont, rowBg, PdfPCell.ALIGN_LEFT);
            addCell(table, d.getCustomerPhone() != null ? d.getCustomerPhone() : "", tdFont, rowBg, PdfPCell.ALIGN_LEFT);
            addCell(table, "Rs. " + outAmt.setScale(SCALE, RoundingMode.HALF_UP), tdBoldFont, rowBg, PdfPCell.ALIGN_RIGHT);
            addCell(table, ovdAmt.compareTo(BigDecimal.ZERO) > 0 ? ("Rs. " + ovdAmt.setScale(SCALE, RoundingMode.HALF_UP)) : "-", tdFont, rowBg, PdfPCell.ALIGN_RIGHT);
            addCell(table, a0.compareTo(BigDecimal.ZERO) > 0 ? ("Rs. " + a0.setScale(SCALE, RoundingMode.HALF_UP)) : "-", tdFont, rowBg, PdfPCell.ALIGN_RIGHT);
            addCell(table, a30.compareTo(BigDecimal.ZERO) > 0 ? ("Rs. " + a30.setScale(SCALE, RoundingMode.HALF_UP)) : "-", tdFont, rowBg, PdfPCell.ALIGN_RIGHT);
            addCell(table, a60.compareTo(BigDecimal.ZERO) > 0 ? ("Rs. " + a60.setScale(SCALE, RoundingMode.HALF_UP)) : "-", tdFont, rowBg, PdfPCell.ALIGN_RIGHT);
            addCell(table, a90.compareTo(BigDecimal.ZERO) > 0 ? ("Rs. " + a90.setScale(SCALE, RoundingMode.HALF_UP)) : "-", tdFont, rowBg, PdfPCell.ALIGN_RIGHT);
            addCell(table, d.getRiskLevel() != null ? d.getRiskLevel() : "LOW", tdFont, rowBg, PdfPCell.ALIGN_LEFT);
            String lastPmt = d.getLastPaymentDate() != null ? ("Rs. " + (d.getLastPaymentAmount() != null ? d.getLastPaymentAmount().setScale(SCALE, RoundingMode.HALF_UP) : "0.00") + " (" + d.getLastPaymentDate() + ")") : "-";
            addCell(table, lastPmt, tdFont, rowBg, PdfPCell.ALIGN_LEFT);
        }

        // Totals Footer Row
        Color footBg = new Color(226, 232, 240);
        addCell(table, "TOTAL", tdBoldFont, footBg, PdfPCell.ALIGN_LEFT);
        addCell(table, "", tdBoldFont, footBg, PdfPCell.ALIGN_LEFT);
        addCell(table, "", tdBoldFont, footBg, PdfPCell.ALIGN_LEFT);
        addCell(table, "Rs. " + sumOut.setScale(SCALE, RoundingMode.HALF_UP), tdBoldFont, footBg, PdfPCell.ALIGN_RIGHT);
        addCell(table, "Rs. " + sumOverdue.setScale(SCALE, RoundingMode.HALF_UP), tdBoldFont, footBg, PdfPCell.ALIGN_RIGHT);
        addCell(table, "Rs. " + sum0To30.setScale(SCALE, RoundingMode.HALF_UP), tdBoldFont, footBg, PdfPCell.ALIGN_RIGHT);
        addCell(table, "Rs. " + sum31To60.setScale(SCALE, RoundingMode.HALF_UP), tdBoldFont, footBg, PdfPCell.ALIGN_RIGHT);
        addCell(table, "Rs. " + sum61To90.setScale(SCALE, RoundingMode.HALF_UP), tdBoldFont, footBg, PdfPCell.ALIGN_RIGHT);
        addCell(table, "Rs. " + sum90Plus.setScale(SCALE, RoundingMode.HALF_UP), tdBoldFont, footBg, PdfPCell.ALIGN_RIGHT);
        addCell(table, "", tdBoldFont, footBg, PdfPCell.ALIGN_LEFT);
        addCell(table, "", tdBoldFont, footBg, PdfPCell.ALIGN_LEFT);

        doc.add(table);
        doc.close();

        return baos.toByteArray();
    }

    private void addCell(PdfPTable table, String text, Font font, Color bgColor, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bgColor);
        cell.setPadding(4);
        cell.setHorizontalAlignment(alignment);
        table.addCell(cell);
    }

    private String escapeCsv(String str) {
        if (str == null) return "";
        if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
            return "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
    }
}
