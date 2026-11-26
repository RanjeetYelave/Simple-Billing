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

    private static final int SCALE = 2;

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
        if (statusFilter != null && !statusFilter.isBlank() && !"ALL".equalsIgnoreCase(statusFilter)) {
            String target = statusFilter.toUpperCase();
            result = result.stream()
                    .filter(f -> f.getStatus().equalsIgnoreCase(target))
                    .collect(Collectors.toList());
        }

        result.sort(Comparator.comparing(DebtorFollowUpDto::getDueDate, Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
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

        // Log collection activity
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
        sb.append("Customer Name,Phone,Email,Outstanding Balance,Overdue Balance,0-30 Days,31-60 Days,61-90 Days,90+ Days,Oldest Overdue Days,Risk Level,Risk Reason,Last Payment Date,Last Payment Amount,Active Promise\n");

        for (DebtorListItemDto d : list) {
            sb.append(escapeCsv(d.getCustomerName())).append(",")
                    .append(escapeCsv(d.getCustomerPhone())).append(",")
                    .append(escapeCsv(d.getCustomerEmail())).append(",")
                    .append(d.getOutstandingAmount()).append(",")
                    .append(d.getOverdueAmount()).append(",")
                    .append(d.getAging0To30()).append(",")
                    .append(d.getAging31To60()).append(",")
                    .append(d.getAging61To90()).append(",")
                    .append(d.getAging90Plus()).append(",")
                    .append(d.getOldestOverdueDays()).append(",")
                    .append(escapeCsv(d.getRiskLevel())).append(",")
                    .append(escapeCsv(d.getRiskReason())).append(",")
                    .append(d.getLastPaymentDate() != null ? d.getLastPaymentDate().toString() : "").append(",")
                    .append(d.getLastPaymentAmount() != null ? d.getLastPaymentAmount().toString() : "").append(",")
                    .append(d.getActivePromiseAmount() != null ? ("₹" + d.getActivePromiseAmount() + " by " + d.getActivePromiseDate()) : "")
                    .append("\n");
        }

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
        FirmDetails firm = firmRepo.findById(firmId).orElse(null);
        String firmName = firm != null && firm.getFirmName() != null ? firm.getFirmName() : "RupeeCRM Business";

        List<DebtorListItemDto> list = getDebtors(firmId, null, "all", "outstanding", "desc");
        DebtorsOverviewDto overview = getOverview(firmId);

        Document doc = new Document(PageSize.A4.rotate(), 20, 20, 20, 20);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter.getInstance(doc, baos);
        doc.open();

        // Title & Header
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.DARK_GRAY);
        Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.GRAY);
        Font thFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        Font tdFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.BLACK);

        Paragraph title = new Paragraph(firmName + " — Debtors & Receivables Aging Register", titleFont);
        Paragraph subtitle = new Paragraph("Generated on: " + LocalDate.now() + " | Total Debtors: " + overview.getTotalDebtorsCount() + " | Total Outstanding: ₹" + overview.getTotalOutstanding(), subtitleFont);
        doc.add(title);
        doc.add(subtitle);
        doc.add(new Paragraph(" "));

        // Table
        PdfPTable table = new PdfPTable(9);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2.5f, 1.5f, 1.5f, 1.2f, 1.2f, 1.2f, 1.2f, 1.2f, 2.0f});

        String[] headers = {"Customer", "Phone", "Outstanding", "0-30 Days", "31-60 Days", "61-90 Days", "90+ Days", "Risk", "Last Payment"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, thFont));
            cell.setBackgroundColor(new Color(24, 43, 73));
            cell.setPadding(5);
            table.addCell(cell);
        }

        for (DebtorListItemDto d : list) {
            table.addCell(new Phrase(d.getCustomerName() != null ? d.getCustomerName() : "", tdFont));
            table.addCell(new Phrase(d.getCustomerPhone() != null ? d.getCustomerPhone() : "", tdFont));
            table.addCell(new Phrase("₹" + d.getOutstandingAmount(), tdFont));
            table.addCell(new Phrase("₹" + d.getAging0To30(), tdFont));
            table.addCell(new Phrase("₹" + d.getAging31To60(), tdFont));
            table.addCell(new Phrase("₹" + d.getAging61To90(), tdFont));
            table.addCell(new Phrase("₹" + d.getAging90Plus(), tdFont));
            table.addCell(new Phrase(d.getRiskLevel(), tdFont));
            String lastPmt = d.getLastPaymentDate() != null ? ("₹" + d.getLastPaymentAmount() + " (" + d.getLastPaymentDate() + ")") : "-";
            table.addCell(new Phrase(lastPmt, tdFont));
        }

        doc.add(table);
        doc.close();

        return baos.toByteArray();
    }

    private String escapeCsv(String str) {
        if (str == null) return "";
        if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
            return "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
    }
}
