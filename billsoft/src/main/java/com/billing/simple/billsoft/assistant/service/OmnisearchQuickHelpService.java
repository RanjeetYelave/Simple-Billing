package com.billing.simple.billsoft.assistant.service;

import com.billing.simple.billsoft.assistant.dto.OmnisearchQuickHelpDTOs.*;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.repositories.PartyPaymentRepository;
import com.billing.simple.billsoft.repositories.PartyRepository;
import com.billing.simple.billsoft.repositories.PurchaseOrderRepository;
import com.billing.simple.billsoft.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dedicated, Independent, Read-Only Query Service for OmniSearch Quick Help.
 * Strictly isolated: Operates directly against database repositories without modifying or calling existing business logic.
 */
@Service
@Transactional(readOnly = true)
public class OmnisearchQuickHelpService {

    private final CustomerRepository customerRepo;
    private final InvoiceRepository invoiceRepo;
    private final InvoiceItemRepository invoiceItemRepo;
    private final InvoicePaymentRepository invoicePaymentRepo;
    private final PartyRepository partyRepo;
    private final PartyPaymentRepository partyPaymentRepo;
    private final PurchaseOrderRepository purchaseOrderRepo;
    private final ExpenseRepository expenseRepo;
    private final ProductRepository productRepo;
    private final EmployeeRepository employeeRepo;
    private final AttendanceRecordRepository attendanceRecordRepo;
    private final SalesReturnRepository salesReturnRepo;
    private final SalaryRecordRepository salaryRepo;
    private final EmployeeAdvanceRepository advanceRepo;
    private final StockMovementRepository stockMovementRepo;

    public OmnisearchQuickHelpService(
            CustomerRepository customerRepo,
            InvoiceRepository invoiceRepo,
            InvoiceItemRepository invoiceItemRepo,
            InvoicePaymentRepository invoicePaymentRepo,
            PartyRepository partyRepo,
            PartyPaymentRepository partyPaymentRepo,
            PurchaseOrderRepository purchaseOrderRepo,
            ExpenseRepository expenseRepo,
            ProductRepository productRepo,
            EmployeeRepository employeeRepo,
            AttendanceRecordRepository attendanceRecordRepo,
            SalesReturnRepository salesReturnRepo,
            SalaryRecordRepository salaryRepo,
            EmployeeAdvanceRepository advanceRepo,
            StockMovementRepository stockMovementRepo
    ) {
        this.customerRepo = customerRepo;
        this.invoiceRepo = invoiceRepo;
        this.invoiceItemRepo = invoiceItemRepo;
        this.invoicePaymentRepo = invoicePaymentRepo;
        this.partyRepo = partyRepo;
        this.partyPaymentRepo = partyPaymentRepo;
        this.purchaseOrderRepo = purchaseOrderRepo;
        this.expenseRepo = expenseRepo;
        this.productRepo = productRepo;
        this.employeeRepo = employeeRepo;
        this.attendanceRecordRepo = attendanceRecordRepo;
        this.salesReturnRepo = salesReturnRepo;
        this.salaryRepo = salaryRepo;
        this.advanceRepo = advanceRepo;
        this.stockMovementRepo = stockMovementRepo;
    }

    // ─────────────────────────────────────────────────────────────
    // 0. DATE-RANGE ENGINE (Indian Financial Year & App Timezone)
    // ─────────────────────────────────────────────────────────────
    public static class DateRangeWindow {
        public final LocalDate fromDate;
        public final LocalDate toDate;
        public final String label;

        public DateRangeWindow(LocalDate fromDate, LocalDate toDate, String label) {
            this.fromDate = fromDate;
            this.toDate = toDate;
            this.label = label;
        }

        public LocalDateTime getStartDateTime() {
            return fromDate.atStartOfDay();
        }

        public LocalDateTime getEndDateTime() {
            return toDate.atTime(LocalTime.MAX);
        }
    }

    public DateRangeWindow resolveDateRange(String dateRangeKey, LocalDate customFrom, LocalDate customTo) {
        LocalDate today = LocalDate.now();
        if (customFrom != null && customTo != null) {
            return new DateRangeWindow(customFrom, customTo, "Custom (" + customFrom + " to " + customTo + ")");
        }

        String key = (dateRangeKey != null) ? dateRangeKey.trim().toLowerCase() : "today";

        switch (key) {
            case "yesterday":
            case "kal": {
                LocalDate y = today.minusDays(1);
                return new DateRangeWindow(y, y, "Yesterday");
            }
            case "this_week":
            case "week":
            case "hfte": {
                LocalDate startOfWeek = today.minusDays(today.getDayOfWeek().getValue() - 1);
                return new DateRangeWindow(startOfWeek, today, "This Week");
            }
            case "last_week": {
                LocalDate endOfLastWeek = today.minusDays(today.getDayOfWeek().getValue());
                LocalDate startOfLastWeek = endOfLastWeek.minusDays(6);
                return new DateRangeWindow(startOfLastWeek, endOfLastWeek, "Last Week");
            }
            case "this_month":
            case "month":
            case "is_mahine":
            case "mahina": {
                LocalDate startOfMonth = today.withDayOfMonth(1);
                return new DateRangeWindow(startOfMonth, today, "This Month");
            }
            case "last_month": {
                LocalDate prevMonthDay = today.minusMonths(1);
                LocalDate startOfLastMonth = prevMonthDay.withDayOfMonth(1);
                LocalDate endOfLastMonth = prevMonthDay.withDayOfMonth(prevMonthDay.lengthOfMonth());
                return new DateRangeWindow(startOfLastMonth, endOfLastMonth, "Last Month");
            }
            case "this_fy":
            case "financial_year":
            case "fy": {
                int year = today.getYear();
                LocalDate fyStart = (today.getMonthValue() >= 4)
                        ? LocalDate.of(year, 4, 1)
                        : LocalDate.of(year - 1, 4, 1);
                LocalDate fyEnd = fyStart.plusYears(1).minusDays(1);
                return new DateRangeWindow(fyStart, (today.isBefore(fyEnd) ? today : fyEnd), "This Financial Year (FY " + fyStart.getYear() + "-" + (fyStart.getYear() + 1) + ")");
            }
            case "last_fy": {
                int year = today.getYear();
                LocalDate currentFyStart = (today.getMonthValue() >= 4)
                        ? LocalDate.of(year, 4, 1)
                        : LocalDate.of(year - 1, 4, 1);
                LocalDate lastFyStart = currentFyStart.minusYears(1);
                LocalDate lastFyEnd = currentFyStart.minusDays(1);
                return new DateRangeWindow(lastFyStart, lastFyEnd, "Last Financial Year (FY " + lastFyStart.getYear() + "-" + (lastFyStart.getYear() + 1) + ")");
            }
            case "last_7_days":
                return new DateRangeWindow(today.minusDays(6), today, "Last 7 Days");
            case "last_30_days":
                return new DateRangeWindow(today.minusDays(29), today, "Last 30 Days");
            case "last_90_days":
                return new DateRangeWindow(today.minusDays(89), today, "Last 90 Days");
            case "all_time":
            case "total":
                return new DateRangeWindow(LocalDate.of(2000, 1, 1), today, "All Time");
            case "today":
            case "aaj":
            default:
                return new DateRangeWindow(today, today, "Today");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 1. CUSTOMER QUICK HELP
    // ─────────────────────────────────────────────────────────────
    public QuickHelpResponse<CustomerQuickHelpDTO> getCustomerQuickHelp(String nameOrPhone) {
        Long firmId = TenantContext.getRequiredFirmId();
        QuickHelpResponse<CustomerQuickHelpDTO> response = new QuickHelpResponse<>();
        response.setCapabilityId("QH_CUSTOMER_INFO");

        if (nameOrPhone == null || nameOrPhone.trim().isEmpty()) {
            response.setStatus("NO_MATCH");
            response.setTitle("Customer Lookup");
            response.setSubtitle("Please provide a customer name or phone number.");
            return response;
        }

        String query = nameOrPhone.trim();
        List<Customer> candidates = customerRepo.findTop5ByFirmIdAndNameContainingIgnoreCaseOrderByNameAsc(firmId, query);
        if (candidates.isEmpty() && query.matches(".*\\d+.*")) {
            candidates = customerRepo.findTop5ByFirmIdAndPhoneContainingOrderByNameAsc(firmId, query);
        }

        if (candidates.isEmpty()) {
            response.setStatus("NO_MATCH");
            response.setTitle("No Customer Found");
            response.setSubtitle("No customer matches \"" + query + "\"");
            return response;
        }

        if (candidates.size() > 1 && !candidates.get(0).getName().equalsIgnoreCase(query)) {
            response.setStatus("AMBIGUOUS");
            response.setTitle("Multiple Customers Found");
            response.setSubtitle("Select the intended customer:");
            List<Map<String, Object>> candidateList = candidates.stream().map(c -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", c.getId());
                map.put("name", c.getName());
                map.put("phone", c.getPhone());
                map.put("address", c.getAddress());
                return map;
            }).collect(Collectors.toList());
            response.setCandidates(candidateList);
            return response;
        }

        Customer customer = candidates.get(0);
        CustomerQuickHelpDTO dto = assembleCustomerDetails(firmId, customer);

        response.setStatus("ANSWER");
        response.setData(dto);
        response.setTitle("👤 " + customer.getName());
        response.setSubtitle("Phone: " + (customer.getPhone() != null ? customer.getPhone() : "N/A") + " • Outstanding: ₹" + dto.getCurrentOutstanding().setScale(2, RoundingMode.HALF_UP));

        Map<String, Object> summary = new HashMap<>();
        summary.put("outstanding", dto.getCurrentOutstanding());
        summary.put("totalSales", dto.getTotalSales());
        summary.put("totalPaid", dto.getTotalPaid());
        summary.put("invoiceCount", dto.getTotalInvoiceCount());
        summary.put("unpaidCount", dto.getUnpaidInvoiceCount());
        response.setSummary(summary);

        response.getDeepLinks().add(new QuickHelpAction("📄 View Statement", "📊", "STATEMENT", Map.of("customerId", customer.getId())));
        response.getDeepLinks().add(new QuickHelpAction("➕ Create Invoice", "📄", "NAVIGATE", Map.of("page", "invoices", "customerId", customer.getId())));

        return response;
    }

    private CustomerQuickHelpDTO assembleCustomerDetails(Long firmId, Customer customer) {
        CustomerQuickHelpDTO dto = new CustomerQuickHelpDTO();
        dto.setId(customer.getId());
        dto.setName(customer.getName());
        dto.setPhone(customer.getPhone());
        dto.setEmail(customer.getEmail());
        dto.setAddress(customer.getAddress());
        dto.setGstin(customer.getGstin());

        List<Invoice> allInvoices = invoiceRepo.findByFirmIdAndCustomer_Id(firmId, customer.getId());
        List<Invoice> validInvoices = allInvoices.stream()
                .filter(i -> i.getStatus() != InvoiceStatus.ESTIMATE && i.getStatus() != InvoiceStatus.DRAFT && i.getStatus() != InvoiceStatus.CANCELLED)
                .collect(Collectors.toList());

        BigDecimal totalSales = BigDecimal.ZERO;
        long unpaidCount = 0;
        long overdueCount = 0;
        LocalDate today = LocalDate.now();
        LocalDate lastTxDate = null;

        for (Invoice inv : validInvoices) {
            BigDecimal amt = inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO;
            totalSales = totalSales.add(amt);
            if (inv.getInvoiceDate() != null) {
                LocalDate invDate = inv.getInvoiceDate().toLocalDate();
                if (lastTxDate == null || invDate.isAfter(lastTxDate)) {
                    lastTxDate = invDate;
                }
            }
        }

        List<InvoicePayment> payments = invoicePaymentRepo.findByFirmIdAndCustomerIdOrderByPaymentDateAscIdAsc(firmId, customer.getId());
        BigDecimal totalPaid = BigDecimal.ZERO;
        LocalDate lastPayDate = null;
        BigDecimal lastPayAmt = BigDecimal.ZERO;

        for (InvoicePayment p : payments) {
            if (p.getAmount() != null) {
                totalPaid = totalPaid.add(p.getAmount());
                if (p.getPaymentDate() != null) {
                    lastPayDate = p.getPaymentDate();
                    lastPayAmt = p.getAmount();
                }
            }
        }

        List<SalesReturn> returns = salesReturnRepo.findByFirmIdOrderByReturnDateDescIdDesc(firmId);
        BigDecimal totalReturnCredit = BigDecimal.ZERO;
        for (SalesReturn sr : returns) {
            if (sr.getCustomer() != null && customer.getId().equals(sr.getCustomer().getId())) {
                if (sr.getTotalRefundAmount() != null) {
                    totalReturnCredit = totalReturnCredit.add(sr.getTotalRefundAmount());
                }
            }
        }

        BigDecimal outstanding = totalSales.subtract(totalPaid).subtract(totalReturnCredit);
        if (outstanding.compareTo(BigDecimal.ZERO) < 0) {
            outstanding = BigDecimal.ZERO;
        }

        // Calculate unpaid and overdue invoices based on balance
        Map<Long, BigDecimal> invPaidMap = new HashMap<>();
        for (InvoicePayment ip : payments) {
            if (ip.getInvoiceId() != null && ip.getAmount() != null) {
                invPaidMap.merge(ip.getInvoiceId(), ip.getAmount(), BigDecimal::add);
            }
        }

        List<InvoiceSummaryDTO> recentInvs = new ArrayList<>();
        List<Invoice> sorted = validInvoices.stream()
                .sorted((a, b) -> {
                    if (a.getInvoiceDate() == null) return 1;
                    if (b.getInvoiceDate() == null) return -1;
                    return b.getInvoiceDate().compareTo(a.getInvoiceDate());
                })
                .limit(5)
                .collect(Collectors.toList());

        for (Invoice inv : sorted) {
            InvoiceSummaryDTO sumDTO = new InvoiceSummaryDTO();
            sumDTO.setId(inv.getId());
            sumDTO.setInvoiceNumber(inv.getInvoiceNumber());
            sumDTO.setCustomerName(customer.getName());
            sumDTO.setCustomerId(customer.getId());
            sumDTO.setInvoiceDate(inv.getInvoiceDate());
            sumDTO.setDueDate(inv.getDueDate());
            sumDTO.setTotalAmount(inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO);
            BigDecimal pAmt = invPaidMap.getOrDefault(inv.getId(), BigDecimal.ZERO);
            sumDTO.setPaidAmount(pAmt);
            BigDecimal bal = sumDTO.getTotalAmount().subtract(pAmt);
            sumDTO.setBalanceAmount(bal.compareTo(BigDecimal.ZERO) > 0 ? bal : BigDecimal.ZERO);
            sumDTO.setStatus(inv.getStatus() != null ? inv.getStatus().name() : "FINAL");

            boolean isOverdue = inv.getDueDate() != null && inv.getDueDate().isBefore(today) && sumDTO.getBalanceAmount().compareTo(BigDecimal.ZERO) > 0;
            sumDTO.setOverdue(isOverdue);

            if (sumDTO.getBalanceAmount().compareTo(BigDecimal.ZERO) > 0) {
                unpaidCount++;
                if (isOverdue) overdueCount++;
            }
            recentInvs.add(sumDTO);
        }

        dto.setTotalSales(totalSales);
        dto.setTotalPaid(totalPaid);
        dto.setTotalReturnCredit(totalReturnCredit);
        dto.setCurrentOutstanding(outstanding);
        dto.setTotalInvoiceCount(validInvoices.size());
        dto.setUnpaidInvoiceCount(unpaidCount);
        dto.setOverdueInvoiceCount(overdueCount);
        dto.setLastTransactionDate(lastTxDate);
        dto.setLastPaymentDate(lastPayDate);
        dto.setLastPaymentAmount(lastPayAmt);
        dto.setRecentInvoices(recentInvs);

        return dto;
    }

    // ─────────────────────────────────────────────────────────────
    // 2. VENDOR / PARTY QUICK HELP
    // ─────────────────────────────────────────────────────────────
    public QuickHelpResponse<VendorQuickHelpDTO> getVendorQuickHelp(String nameOrPhone) {
        Long firmId = TenantContext.getRequiredFirmId();
        QuickHelpResponse<VendorQuickHelpDTO> response = new QuickHelpResponse<>();
        response.setCapabilityId("QH_VENDOR_INFO");

        if (nameOrPhone == null || nameOrPhone.trim().isEmpty()) {
            response.setStatus("NO_MATCH");
            response.setTitle("Vendor Lookup");
            response.setSubtitle("Please provide a vendor or party name.");
            return response;
        }

        String query = nameOrPhone.trim();
        List<Party> candidates = partyRepo.findByFirmIdAndNameContainingIgnoreCase(firmId, query);

        if (candidates.isEmpty()) {
            response.setStatus("NO_MATCH");
            response.setTitle("No Vendor Found");
            response.setSubtitle("No vendor matches \"" + query + "\"");
            return response;
        }

        if (candidates.size() > 1 && !candidates.get(0).getName().equalsIgnoreCase(query)) {
            response.setStatus("AMBIGUOUS");
            response.setTitle("Multiple Vendors Found");
            response.setSubtitle("Select the intended vendor:");
            List<Map<String, Object>> candidateList = candidates.stream().map(p -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", p.getId());
                map.put("name", p.getName());
                map.put("phone", p.getPhone());
                map.put("city", p.getCity());
                return map;
            }).collect(Collectors.toList());
            response.setCandidates(candidateList);
            return response;
        }

        Party party = candidates.get(0);
        VendorQuickHelpDTO dto = assembleVendorDetails(firmId, party);

        response.setStatus("ANSWER");
        response.setData(dto);
        response.setTitle("🏢 " + party.getName());
        response.setSubtitle("Phone: " + (party.getPhone() != null ? party.getPhone() : "N/A") + " • Payable Dues: ₹" + dto.getCurrentPayable().setScale(2, RoundingMode.HALF_UP));

        Map<String, Object> summary = new HashMap<>();
        summary.put("payableDues", dto.getCurrentPayable());
        summary.put("totalPurchases", dto.getTotalPurchases());
        summary.put("totalPaid", dto.getTotalPaid());
        summary.put("poCount", dto.getTotalPurchaseOrderCount());
        response.setSummary(summary);

        response.getDeepLinks().add(new QuickHelpAction("📋 View POs", "📦", "NAVIGATE", Map.of("page", "firm", "tab", "paperwork", "subTab", "orders", "partyId", party.getId())));

        return response;
    }

    private VendorQuickHelpDTO assembleVendorDetails(Long firmId, Party party) {
        VendorQuickHelpDTO dto = new VendorQuickHelpDTO();
        dto.setId(party.getId());
        dto.setName(party.getName());
        dto.setContactPerson(party.getContactPerson());
        dto.setPhone(party.getPhone());
        dto.setEmail(party.getEmail());
        dto.setAddress(party.getAddress());
        dto.setGstin(party.getGstin());
        dto.setPan(party.getPan());
        dto.setBankName(party.getBankName());
        dto.setBankAccount(party.getBankAccount());
        dto.setBankIfsc(party.getBankIfsc());
        dto.setUpiId(party.getUpiId());

        BigDecimal openingBalance = party.getOpeningBalance() != null ? party.getOpeningBalance() : BigDecimal.ZERO;
        String opType = party.getOpeningBalanceType() != null ? party.getOpeningBalanceType().toUpperCase() : "PAYABLE";
        dto.setOpeningBalance(openingBalance);
        dto.setOpeningBalanceType(opType);

        List<PurchaseOrder> poList = purchaseOrderRepo.findByFirmIdAndPartyIdOrderByPoDateDescIdDesc(firmId, party.getId());
        List<PurchaseOrder> validPOs = poList.stream()
                .filter(po -> po.getStatus() != PurchaseOrderStatus.CANCELLED)
                .collect(Collectors.toList());

        BigDecimal totalPurchases = BigDecimal.ZERO;
        for (PurchaseOrder po : validPOs) {
            if (po.getTotalAmount() != null) {
                totalPurchases = totalPurchases.add(po.getTotalAmount());
            }
        }

        List<PartyPayment> payments = partyPaymentRepo.findByFirmIdAndPartyIdOrderByPaymentDateDescIdDesc(firmId, party.getId());
        BigDecimal totalPaid = BigDecimal.ZERO;
        for (PartyPayment pay : payments) {
            if (pay.getAmount() != null) {
                totalPaid = totalPaid.add(pay.getAmount());
            }
        }

        BigDecimal netPayable = (opType.equals("ADVANCE") ? openingBalance.negate() : openingBalance)
                .add(totalPurchases)
                .subtract(totalPaid);

        if (netPayable.compareTo(BigDecimal.ZERO) < 0) {
            netPayable = BigDecimal.ZERO;
        }

        List<PurchaseOrderSummaryDTO> recentPOs = validPOs.stream().limit(5).map(po -> {
            PurchaseOrderSummaryDTO pDTO = new PurchaseOrderSummaryDTO();
            pDTO.setId(po.getId());
            pDTO.setOrderNumber(po.getPoNumber() != null ? po.getPoNumber() : "PO-" + po.getId());
            pDTO.setPartyName(party.getName());
            pDTO.setOrderDate(po.getPoDate());
            pDTO.setStatus(po.getStatus() != null ? po.getStatus().name() : "ISSUED");
            pDTO.setTotalAmount(po.getTotalAmount() != null ? po.getTotalAmount() : BigDecimal.ZERO);
            return pDTO;
        }).collect(Collectors.toList());

        dto.setTotalPurchases(totalPurchases);
        dto.setTotalPaid(totalPaid);
        dto.setCurrentPayable(netPayable);
        dto.setTotalPurchaseOrderCount(validPOs.size());
        dto.setRecentPurchaseOrders(recentPOs);

        return dto;
    }

    // ─────────────────────────────────────────────────────────────
    // 3. INVOICE & DOCUMENT QUICK HELP
    // ─────────────────────────────────────────────────────────────
    public QuickHelpResponse<InvoiceDetailDTO> getInvoiceQuickHelp(String invoiceNumberOrId) {
        Long firmId = TenantContext.getRequiredFirmId();
        QuickHelpResponse<InvoiceDetailDTO> response = new QuickHelpResponse<>();
        response.setCapabilityId("QH_INVOICE_INFO");

        if (invoiceNumberOrId == null || invoiceNumberOrId.trim().isEmpty()) {
            response.setStatus("NO_MATCH");
            response.setTitle("Invoice Lookup");
            response.setSubtitle("Please provide an invoice number (e.g. INV-1002 or 1002).");
            return response;
        }

        String raw = invoiceNumberOrId.trim();
        Optional<Invoice> optInv = invoiceRepo.findByInvoiceNumberAndFirmId(raw, firmId);

        if (optInv.isEmpty()) {
            optInv = invoiceRepo.findByEstimateNumberAndFirmId(raw, firmId);
        }

        if (optInv.isEmpty() && raw.matches("\\d+")) {
            optInv = invoiceRepo.findByIdAndFirmId(Long.parseLong(raw), firmId);
            if (optInv.isEmpty()) {
                optInv = invoiceRepo.findByInvoiceNumberAndFirmId("INV-" + raw, firmId);
            }
        }

        if (optInv.isEmpty()) {
            response.setStatus("NO_MATCH");
            response.setTitle("Invoice Not Found");
            response.setSubtitle("No document matches \"" + raw + "\"");
            return response;
        }

        Invoice inv = optInv.get();
        InvoiceDetailDTO dto = assembleInvoiceDetail(firmId, inv);

        response.setStatus("ANSWER");
        response.setData(dto);
        String docNo = inv.getInvoiceNumber() != null ? inv.getInvoiceNumber() : (inv.getEstimateNumber() != null ? inv.getEstimateNumber() : "INV-" + inv.getId());
        response.setTitle("📄 " + docNo + " • ₹" + dto.getTotalAmount().setScale(2, RoundingMode.HALF_UP));
        response.setSubtitle("Customer: " + dto.getCustomerName() + " • Status: " + dto.getStatus() + " • Balance: ₹" + dto.getBalanceAmount().setScale(2, RoundingMode.HALF_UP));

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalAmount", dto.getTotalAmount());
        summary.put("paidAmount", dto.getPaidAmount());
        summary.put("balanceAmount", dto.getBalanceAmount());
        summary.put("status", dto.getStatus());
        summary.put("isOverdue", dto.isOverdue());
        summary.put("itemCount", dto.getItems().size());
        response.setSummary(summary);

        response.getDeepLinks().add(new QuickHelpAction("✏️ Open Invoice", "📄", "NAVIGATE", Map.of("page", "invoices", "invoiceId", inv.getId())));

        return response;
    }

    private InvoiceDetailDTO assembleInvoiceDetail(Long firmId, Invoice inv) {
        InvoiceDetailDTO dto = new InvoiceDetailDTO();
        dto.setId(inv.getId());
        dto.setInvoiceNumber(inv.getInvoiceNumber());
        dto.setEstimateNumber(inv.getEstimateNumber());
        dto.setCustomerName(inv.getCustomer() != null ? inv.getCustomer().getName() : "Cash Customer");
        dto.setCustomerId(inv.getCustomer() != null ? inv.getCustomer().getId() : null);
        dto.setInvoiceDate(inv.getInvoiceDate());
        dto.setDueDate(inv.getDueDate());
        dto.setTotalAmount(inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO);
        dto.setDiscount(inv.getTotalDiscount() != null ? inv.getTotalDiscount() : BigDecimal.ZERO);
        dto.setTaxAmount(inv.getTotalTax() != null ? inv.getTotalTax() : BigDecimal.ZERO);
        List<InvoicePayment> payments = invoicePaymentRepo.findByInvoiceIdAndFirmIdOrderByPaymentDateAscIdAsc(inv.getId(), firmId);
        BigDecimal paidAmount = BigDecimal.ZERO;
        List<PaymentRecordDTO> payDTOs = new ArrayList<>();
        String detectedPaymentMode = null;
        for (InvoicePayment p : payments) {
            PaymentRecordDTO pr = new PaymentRecordDTO();
            pr.setId(p.getId());
            pr.setAmount(p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO);
            pr.setPaymentDate(p.getPaymentDate());
            pr.setPaymentMode(p.getPaymentMode());
            pr.setReferenceNumber(p.getReferenceNumber());
            pr.setNotes(p.getNotes());
            payDTOs.add(pr);
            paidAmount = paidAmount.add(pr.getAmount());
            if (detectedPaymentMode == null && p.getPaymentMode() != null && !p.getPaymentMode().trim().isEmpty()) {
                detectedPaymentMode = p.getPaymentMode();
            }
        }
        dto.setPayments(payDTOs);
        dto.setPaidAmount(paidAmount);
        dto.setPaymentType(detectedPaymentMode != null ? detectedPaymentMode : (inv.getStatus() != null ? inv.getStatus().name() : "STANDARD"));
        dto.setStatus(inv.getStatus() != null ? inv.getStatus().name() : "FINAL");

        List<SalesReturn> returns = salesReturnRepo.findByInvoiceIdAndFirmIdOrderByCreatedAtDesc(inv.getId(), firmId);
        BigDecimal returnAmount = BigDecimal.ZERO;
        List<SalesReturnSummaryDTO> retDTOs = new ArrayList<>();
        for (SalesReturn sr : returns) {
            SalesReturnSummaryDTO srDTO = new SalesReturnSummaryDTO();
            srDTO.setId(sr.getId());
            srDTO.setReturnNumber(sr.getReturnNumber());
            srDTO.setReturnDate(sr.getReturnDate());
            srDTO.setTotalAmount(sr.getTotalRefundAmount() != null ? sr.getTotalRefundAmount() : BigDecimal.ZERO);
            srDTO.setReason(sr.getReason());
            retDTOs.add(srDTO);
            returnAmount = returnAmount.add(srDTO.getTotalAmount());
        }
        dto.setReturns(retDTOs);

        BigDecimal balance = dto.getTotalAmount().subtract(paidAmount).subtract(returnAmount);
        dto.setBalanceAmount(balance.compareTo(BigDecimal.ZERO) > 0 ? balance : BigDecimal.ZERO);

        LocalDate today = LocalDate.now();
        boolean isOverdue = inv.getDueDate() != null && inv.getDueDate().isBefore(today) && dto.getBalanceAmount().compareTo(BigDecimal.ZERO) > 0;
        dto.setOverdue(isOverdue);

        List<InvoiceItem> items = invoiceItemRepo.findByInvoice_IdAndInvoice_FirmId(inv.getId(), firmId);
        List<InvoiceItemDTO> itemDTOs = new ArrayList<>();
        for (InvoiceItem itm : items) {
            InvoiceItemDTO iDTO = new InvoiceItemDTO();
            iDTO.setId(itm.getId());
            iDTO.setProductName(itm.getProductName() != null ? itm.getProductName() : (itm.getProduct() != null ? itm.getProduct().getName() : "Item"));
            iDTO.setDescription(itm.getUnit());
            iDTO.setQuantity(itm.getQty() != null ? itm.getQty() : 1);
            iDTO.setPrice(itm.getPricePerUnit() != null ? itm.getPricePerUnit() : BigDecimal.ZERO);
            iDTO.setTaxRate(itm.getGstPercent() != null ? itm.getGstPercent() : BigDecimal.ZERO);
            iDTO.setTaxAmount(itm.getGstAmount() != null ? itm.getGstAmount() : BigDecimal.ZERO);
            iDTO.setTotalAmount(itm.getLineTotal() != null ? itm.getLineTotal() : BigDecimal.ZERO);
            itemDTOs.add(iDTO);
        }
        dto.setItems(itemDTOs);

        return dto;
    }

    // ─────────────────────────────────────────────────────────────
    // 4. SALES & REVENUE SUMMARY QUICK HELP
    // ─────────────────────────────────────────────────────────────
    public QuickHelpResponse<SalesSummaryDTO> getSalesSummary(String dateRangeKey, LocalDate customFrom, LocalDate customTo) {
        Long firmId = TenantContext.getRequiredFirmId();
        DateRangeWindow window = resolveDateRange(dateRangeKey, customFrom, customTo);

        QuickHelpResponse<SalesSummaryDTO> response = new QuickHelpResponse<>();
        response.setCapabilityId("QH_SALES_SUMMARY");

        List<Invoice> invoices = invoiceRepo.findAllByFirmIdAndInvoiceDateBetweenOrderByInvoiceDateAsc(
                firmId, window.getStartDateTime(), window.getEndDateTime());

        List<Invoice> validInvoices = invoices.stream()
                .filter(i -> i.getStatus() != InvoiceStatus.ESTIMATE && i.getStatus() != InvoiceStatus.DRAFT && i.getStatus() != InvoiceStatus.CANCELLED)
                .collect(Collectors.toList());

        SalesSummaryDTO dto = new SalesSummaryDTO();
        dto.setDateRangeLabel(window.label);
        dto.setFromDate(window.getStartDateTime());
        dto.setToDate(window.getEndDateTime());

        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        Map<String, BigDecimal> customerRevenueMap = new HashMap<>();

        for (Invoice inv : validInvoices) {
            BigDecimal amt = inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO;
            totalRevenue = totalRevenue.add(amt);
            if (inv.getTotalTax() != null) {
                totalTax = totalTax.add(inv.getTotalTax());
            }
            String custName = inv.getCustomer() != null ? inv.getCustomer().getName() : "Cash Customer";
            customerRevenueMap.merge(custName, amt, BigDecimal::add);
        }

        List<InvoicePayment> payments = invoicePaymentRepo.findByFirmIdAndPaymentDateBetweenOrderByPaymentDateAscIdAsc(
                firmId, window.fromDate, window.toDate);
        BigDecimal totalPaid = BigDecimal.ZERO;
        for (InvoicePayment p : payments) {
            if (p.getAmount() != null) totalPaid = totalPaid.add(p.getAmount());
        }

        BigDecimal pending = totalRevenue.subtract(totalPaid);
        if (pending.compareTo(BigDecimal.ZERO) < 0) pending = BigDecimal.ZERO;

        dto.setTotalRevenue(totalRevenue);
        dto.setTotalTaxCollected(totalTax);
        dto.setTotalPaidReceived(totalPaid);
        dto.setTotalPendingBalance(pending);
        dto.setInvoiceCount(validInvoices.size());

        if (!validInvoices.isEmpty()) {
            dto.setAverageTicketSize(totalRevenue.divide(BigDecimal.valueOf(validInvoices.size()), 2, RoundingMode.HALF_UP));
        }

        List<Map<String, Object>> topCust = customerRevenueMap.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(5)
                .map(e -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", e.getKey());
                    map.put("revenue", e.getValue());
                    return map;
                })
                .collect(Collectors.toList());
        dto.setTopCustomers(topCust);

        response.setStatus("ANSWER");
        response.setData(dto);
        response.setTitle("📊 " + window.label + " Sales: ₹" + totalRevenue.setScale(2, RoundingMode.HALF_UP));
        response.setSubtitle("Total Invoices: " + validInvoices.size() + " • Paid: ₹" + totalPaid.setScale(2, RoundingMode.HALF_UP) + " • Pending: ₹" + pending.setScale(2, RoundingMode.HALF_UP));

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalRevenue", totalRevenue);
        summary.put("totalPaid", totalPaid);
        summary.put("pendingBalance", pending);
        summary.put("invoiceCount", validInvoices.size());
        summary.put("averageTicket", dto.getAverageTicketSize());
        response.setSummary(summary);

        return response;
    }

    // ─────────────────────────────────────────────────────────────
    // 5. EXPENSES SUMMARY QUICK HELP
    // ─────────────────────────────────────────────────────────────
    public QuickHelpResponse<ExpenseSummaryDTO> getExpenseSummary(String dateRangeKey, String categoryFilter, LocalDate customFrom, LocalDate customTo) {
        Long firmId = TenantContext.getRequiredFirmId();
        DateRangeWindow window = resolveDateRange(dateRangeKey, customFrom, customTo);

        QuickHelpResponse<ExpenseSummaryDTO> response = new QuickHelpResponse<>();
        response.setCapabilityId("QH_EXPENSE_SUMMARY");

        List<Expense> expenses = expenseRepo.findByFirmIdAndExpenseDateBetweenOrderByExpenseDateDescIdDesc(
                firmId, window.fromDate, window.toDate);

        if (categoryFilter != null && !categoryFilter.trim().isEmpty()) {
            String filter = categoryFilter.trim().toLowerCase();
            expenses = expenses.stream()
                    .filter(e -> e.getCategory() != null && e.getCategory().toLowerCase().contains(filter))
                    .collect(Collectors.toList());
        }

        ExpenseSummaryDTO dto = new ExpenseSummaryDTO();
        dto.setDateRangeLabel(window.label + (categoryFilter != null ? " (" + categoryFilter + ")" : ""));
        dto.setFromDate(window.fromDate);
        dto.setToDate(window.toDate);

        BigDecimal totalExpense = BigDecimal.ZERO;
        Map<String, BigDecimal> catMap = new HashMap<>();
        Map<String, BigDecimal> modeMap = new HashMap<>();
        List<ExpenseItemDTO> recent = new ArrayList<>();

        for (Expense e : expenses) {
            BigDecimal amt = e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO;
            totalExpense = totalExpense.add(amt);
            String cat = e.getCategory() != null && !e.getCategory().trim().isEmpty() ? e.getCategory() : "General";
            catMap.merge(cat, amt, BigDecimal::add);
            String mode = e.getPaymentMode() != null && !e.getPaymentMode().trim().isEmpty() ? e.getPaymentMode() : "Cash";
            modeMap.merge(mode, amt, BigDecimal::add);

            if (recent.size() < 5) {
                ExpenseItemDTO item = new ExpenseItemDTO();
                item.setId(e.getId());
                item.setTitle(e.getTitle());
                item.setAmount(amt);
                item.setCategory(cat);
                item.setExpenseDate(e.getExpenseDate());
                item.setPaymentMode(mode);
                recent.add(item);
            }
        }

        dto.setTotalExpense(totalExpense);
        dto.setExpenseCount(expenses.size());
        dto.setCategoryBreakdown(catMap);
        dto.setPaymentModeBreakdown(modeMap);
        dto.setRecentExpenses(recent);

        response.setStatus("ANSWER");
        response.setData(dto);
        response.setTitle("💸 " + dto.getDateRangeLabel() + " Expenses: ₹" + totalExpense.setScale(2, RoundingMode.HALF_UP));
        response.setSubtitle("Total Entries: " + expenses.size() + " • Top Category: " + (catMap.isEmpty() ? "None" : Collections.max(catMap.entrySet(), Map.Entry.comparingByValue()).getKey()));

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalExpense", totalExpense);
        summary.put("expenseCount", expenses.size());
        summary.put("categoryBreakdown", catMap);
        response.setSummary(summary);

        return response;
    }

    // ─────────────────────────────────────────────────────────────
    // 6. INVENTORY & PRODUCT QUICK HELP
    // ─────────────────────────────────────────────────────────────
    public QuickHelpResponse<InventorySummaryDTO> getInventoryQuickHelp(String productNameOrCategory, boolean onlyLowStock) {
        Long firmId = TenantContext.getRequiredFirmId();
        QuickHelpResponse<InventorySummaryDTO> response = new QuickHelpResponse<>();
        response.setCapabilityId("QH_INVENTORY_INFO");

        List<Product> allProducts = productRepo.findByFirmId(firmId);
        List<Product> lowStockList = productRepo.findLowStockProductsByFirmId(firmId);

        InventorySummaryDTO dto = new InventorySummaryDTO();
        dto.setTotalProductsCount(allProducts.size());
        dto.setLowStockCount(lowStockList.size());

        BigDecimal totalCostVal = BigDecimal.ZERO;
        BigDecimal totalRetailVal = BigDecimal.ZERO;
        long outOfStock = 0;

        for (Product p : allProducts) {
            BigDecimal qty = p.getStockQuantity() != null ? p.getStockQuantity() : BigDecimal.ZERO;
            BigDecimal price = p.getPrice() != null ? p.getPrice() : BigDecimal.ZERO;
            BigDecimal cost = p.getCostPrice() != null ? p.getCostPrice() : price;

            totalRetailVal = totalRetailVal.add(price.multiply(qty));
            totalCostVal = totalCostVal.add(cost.multiply(qty));

            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                outOfStock++;
            }
        }

        dto.setOutOfStockCount(outOfStock);
        dto.setTotalRetailValuation(totalRetailVal);
        dto.setTotalCostValuation(totalCostVal);

        List<ProductSummaryDTO> lowStockDTOs = lowStockList.stream().limit(10).map(this::toProductSummaryDTO).collect(Collectors.toList());
        dto.setLowStockItems(lowStockDTOs);

        if (productNameOrCategory != null && !productNameOrCategory.trim().isEmpty()) {
            String query = productNameOrCategory.trim();
            List<Product> matched = productRepo.findTop5ByFirmIdAndNameContainingIgnoreCaseOrderByNameAsc(firmId, query);
            dto.setMatchedProducts(matched.stream().map(this::toProductSummaryDTO).collect(Collectors.toList()));
        }

        response.setStatus("ANSWER");
        response.setData(dto);

        if (onlyLowStock || (productNameOrCategory != null && productNameOrCategory.toLowerCase().contains("low stock"))) {
            response.setTitle("⚠️ Low Stock Alert: " + lowStockList.size() + " Item" + (lowStockList.size() == 1 ? "" : "s"));
            response.setSubtitle("Items below reorder thresholds needing restock.");
        } else if (dto.getMatchedProducts() != null && !dto.getMatchedProducts().isEmpty()) {
            ProductSummaryDTO first = dto.getMatchedProducts().get(0);
            response.setTitle("📦 " + first.getName() + ": " + first.getStockQuantity() + " " + first.getUnit());
            response.setSubtitle("Selling Price: ₹" + first.getPrice() + " • Cost: ₹" + first.getCostPrice() + " • Status: " + (first.isLowStock() ? "⚠️ LOW STOCK" : "✓ In Stock"));
        } else {
            response.setTitle("📦 Inventory Summary (" + allProducts.size() + " Products)");
            response.setSubtitle("Retail Value: ₹" + totalRetailVal.setScale(2, RoundingMode.HALF_UP) + " • Low Stock: " + lowStockList.size() + " items");
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalProducts", allProducts.size());
        summary.put("lowStockCount", lowStockList.size());
        summary.put("retailValuation", totalRetailVal);
        summary.put("costValuation", totalCostVal);
        response.setSummary(summary);

        return response;
    }

    private ProductSummaryDTO toProductSummaryDTO(Product p) {
        ProductSummaryDTO dto = new ProductSummaryDTO();
        dto.setId(p.getId());
        dto.setName(p.getName());
        dto.setPrice(p.getPrice() != null ? p.getPrice() : BigDecimal.ZERO);
        dto.setCostPrice(p.getCostPrice() != null ? p.getCostPrice() : BigDecimal.ZERO);
        dto.setStockQuantity(p.getStockQuantity() != null ? p.getStockQuantity() : BigDecimal.ZERO);
        dto.setMinStockLevel(p.getMinStockLevel() != null ? p.getMinStockLevel() : BigDecimal.ZERO);
        dto.setSku(p.getSku());
        dto.setBarcode(p.getBarcode());
        dto.setCategory(p.getCategory());
        dto.setUnit(p.getUnit() != null ? p.getUnit() : "pcs");
        dto.setGstPercentage(p.getGstPercentage() != null ? p.getGstPercentage() : BigDecimal.ZERO);
        boolean low = (p.getItemType() == null || p.getItemType().equalsIgnoreCase("GOODS"))
                && dto.getStockQuantity().compareTo(dto.getMinStockLevel()) <= 0;
        dto.setLowStock(low);
        return dto;
    }

    // ─────────────────────────────────────────────────────────────
    // 7. HR & STAFF QUICK HELP
    // ─────────────────────────────────────────────────────────────
    public QuickHelpResponse<HrStaffSummaryDTO> getHrStaffQuickHelp(String employeeName) {
        Long firmId = TenantContext.getRequiredFirmId();
        QuickHelpResponse<HrStaffSummaryDTO> response = new QuickHelpResponse<>();
        response.setCapabilityId("QH_HR_STAFF_INFO");

        if (employeeName == null || employeeName.trim().isEmpty()) {
            response.setStatus("NO_MATCH");
            response.setTitle("Staff Lookup");
            response.setSubtitle("Please provide an employee name.");
            return response;
        }

        String query = employeeName.trim();
        List<Employee> candidates = employeeRepo.findTop5ByFirmIdAndNameContainingIgnoreCaseOrderByNameAsc(firmId, query);

        if (candidates.isEmpty()) {
            response.setStatus("NO_MATCH");
            response.setTitle("No Employee Found");
            response.setSubtitle("No employee matches \"" + query + "\"");
            return response;
        }

        if (candidates.size() > 1 && !candidates.get(0).getName().equalsIgnoreCase(query)) {
            response.setStatus("AMBIGUOUS");
            response.setTitle("Multiple Employees Found");
            response.setSubtitle("Select the intended employee:");
            List<Map<String, Object>> candidateList = candidates.stream().map(e -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", e.getId());
                map.put("name", e.getName());
                map.put("department", e.getDepartment());
                map.put("role", e.getRole());
                return map;
            }).collect(Collectors.toList());
            response.setCandidates(candidateList);
            return response;
        }

        Employee emp = candidates.get(0);
        HrStaffSummaryDTO dto = assembleEmployeeSummary(firmId, emp);

        response.setStatus("ANSWER");
        response.setData(dto);
        response.setTitle("🧑‍💼 " + emp.getName() + " (" + (emp.getRole() != null ? emp.getRole() : "Staff") + ")");
        response.setSubtitle("Today Attendance: " + dto.getTodayAttendanceStatus() + " • Department: " + (emp.getDepartment() != null ? emp.getDepartment() : "General") + " • Advance: ₹" + dto.getCurrentAdvanceBalance());

        Map<String, Object> summary = new HashMap<>();
        summary.put("todayStatus", dto.getTodayAttendanceStatus());
        summary.put("presentDaysThisMonth", dto.getPresentDaysThisMonth());
        summary.put("advanceBalance", dto.getCurrentAdvanceBalance());
        response.setSummary(summary);

        return response;
    }

    private HrStaffSummaryDTO assembleEmployeeSummary(Long firmId, Employee emp) {
        HrStaffSummaryDTO dto = new HrStaffSummaryDTO();
        dto.setId(emp.getId());
        dto.setName(emp.getName());
        dto.setPhone(emp.getPhone());
        dto.setRole(emp.getRole());
        dto.setDepartment(emp.getDepartment());
        dto.setDesignation(emp.getDesignation());
        dto.setEmail(emp.getEmail());
        dto.setDateOfJoining(emp.getDateOfJoining());
        dto.setIsActive(emp.getIsActive() != null ? emp.getIsActive() : true);
        dto.setMonthlyBaseSalary(emp.getMonthlyBaseSalary() != null ? emp.getMonthlyBaseSalary() : 0.0);
        dto.setCurrentAdvanceBalance(emp.getCurrentAdvanceBalance() != null ? emp.getCurrentAdvanceBalance() : 0.0);
        dto.setAllowedPaidLeavesPerMonth(emp.getAllowedPaidLeavesPerMonth() != null ? emp.getAllowedPaidLeavesPerMonth() : 0);

        LocalDate today = LocalDate.now();
        Optional<AttendanceRecord> todayRec = attendanceRecordRepo.findByEmployeeIdAndDate(emp.getId(), today);
        dto.setTodayAttendanceStatus(todayRec.map(AttendanceRecord::getStatus).orElse("NOT_MARKED"));

        LocalDate startOfMonth = today.withDayOfMonth(1);
        List<AttendanceRecord> monthRecords = attendanceRecordRepo.findByEmployeeIdAndDateBetween(emp.getId(), startOfMonth, today);

        long present = 0;
        long absent = 0;
        long half = 0;
        long leave = 0;

        for (AttendanceRecord ar : monthRecords) {
            if ("PRESENT".equalsIgnoreCase(ar.getStatus())) present++;
            else if ("ABSENT".equalsIgnoreCase(ar.getStatus())) absent++;
            else if ("HALF_DAY".equalsIgnoreCase(ar.getStatus())) half++;
            else if ("LEAVE".equalsIgnoreCase(ar.getStatus())) leave++;
        }

        dto.setPresentDaysThisMonth(present);
        dto.setAbsentDaysThisMonth(absent);
        dto.setHalfDaysThisMonth(half);
        dto.setLeaveDaysThisMonth(leave);

        return dto;
    }

    // ─────────────────────────────────────────────────────────────
    // 8. MACRO CROSS-DOMAIN BUSINESS SUMMARY
    // ─────────────────────────────────────────────────────────────
    public QuickHelpResponse<MacroBusinessSummaryDTO> getMacroBusinessSummary() {
        Long firmId = TenantContext.getRequiredFirmId();
        QuickHelpResponse<MacroBusinessSummaryDTO> response = new QuickHelpResponse<>();
        response.setCapabilityId("QH_MACRO_SUMMARY");

        List<Customer> customers = customerRepo.findByFirmIdOrderByNameAsc(firmId);
        List<Party> parties = partyRepo.findByFirmIdOrderByNameAsc(firmId);

        BigDecimal totalReceivables = BigDecimal.ZERO;
        List<Map<String, Object>> debtors = new ArrayList<>();

        for (Customer c : customers) {
            CustomerQuickHelpDTO cDTO = assembleCustomerDetails(firmId, c);
            if (cDTO.getCurrentOutstanding().compareTo(BigDecimal.ZERO) > 0) {
                totalReceivables = totalReceivables.add(cDTO.getCurrentOutstanding());
                Map<String, Object> map = new HashMap<>();
                map.put("id", c.getId());
                map.put("name", c.getName());
                map.put("phone", c.getPhone());
                map.put("outstanding", cDTO.getCurrentOutstanding());
                debtors.add(map);
            }
        }

        debtors.sort((a, b) -> ((BigDecimal) b.get("outstanding")).compareTo((BigDecimal) a.get("outstanding")));

        BigDecimal totalPayables = BigDecimal.ZERO;
        List<Map<String, Object>> creditors = new ArrayList<>();

        for (Party p : parties) {
            VendorQuickHelpDTO vDTO = assembleVendorDetails(firmId, p);
            if (vDTO.getCurrentPayable().compareTo(BigDecimal.ZERO) > 0) {
                totalPayables = totalPayables.add(vDTO.getCurrentPayable());
                Map<String, Object> map = new HashMap<>();
                map.put("id", p.getId());
                map.put("name", p.getName());
                map.put("phone", p.getPhone());
                map.put("payable", vDTO.getCurrentPayable());
                creditors.add(map);
            }
        }

        creditors.sort((a, b) -> ((BigDecimal) b.get("payable")).compareTo((BigDecimal) a.get("payable")));

        MacroBusinessSummaryDTO dto = new MacroBusinessSummaryDTO();
        dto.setTotalReceivables(totalReceivables);
        dto.setTotalPayables(totalPayables);
        dto.setTotalCustomerCount(customers.size());
        dto.setTotalVendorCount(parties.size());
        dto.setDebtorsCount(debtors.size());
        dto.setCreditorsCount(creditors.size());
        dto.setTopDebtors(debtors.stream().limit(5).collect(Collectors.toList()));
        dto.setTopCreditors(creditors.stream().limit(5).collect(Collectors.toList()));

        response.setStatus("ANSWER");
        response.setData(dto);
        response.setTitle("💰 Total Receivables (Udhari): ₹" + totalReceivables.setScale(2, RoundingMode.HALF_UP));
        response.setSubtitle("Market Dues: " + debtors.size() + " customers • Vendor Payables: ₹" + totalPayables.setScale(2, RoundingMode.HALF_UP) + " (" + creditors.size() + " vendors)");

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalReceivables", totalReceivables);
        summary.put("totalPayables", totalPayables);
        summary.put("debtorsCount", debtors.size());
        summary.put("creditorsCount", creditors.size());
        response.setSummary(summary);

        return response;
    }

    // ─────────────────────────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────
    // 8. SALARIES & PAYROLL QUICK HELP
    // ─────────────────────────────────────────────────────────────
    public QuickHelpResponse<SalaryQuickHelpDTO> getSalaryQuickHelp(String employeeName, String period) {
        Long firmId = TenantContext.getRequiredFirmId();
        DateRangeWindow window = resolveDateRange(period != null ? period : "this_month", null, null);
        QuickHelpResponse<SalaryQuickHelpDTO> response = new QuickHelpResponse<>();
        response.setCapabilityId("QH_SALARIES_SUMMARY");

        SalaryQuickHelpDTO dto = new SalaryQuickHelpDTO();
        dto.setPeriod(window.label);

        List<SalaryRecord> records;
        if (employeeName != null && !employeeName.trim().isEmpty()) {
            String q = employeeName.trim();
            List<Employee> emps = employeeRepo.findTop5ByFirmIdAndNameContainingIgnoreCaseOrderByNameAsc(firmId, q);
            if (emps.isEmpty()) {
                response.setStatus("NO_MATCH");
                response.setTitle("No Employee Found");
                response.setSubtitle("No employee matches \"" + q + "\"");
                return response;
            }
            Employee emp = emps.get(0);
            dto.setEmployeeName(emp.getName());
            dto.setEmployeeSalary(emp.getMonthlyBaseSalary());
            records = salaryRepo.findByEmployeeIdOrderByPaymentDateDesc(emp.getId());
        } else {
            records = salaryRepo.findByEmployee_FirmIdOrderByPaymentDateDesc(firmId);
        }

        double totalPayroll = 0;
        double totalAdvDed = 0;
        double totalBonus = 0;
        List<Map<String, Object>> list = new ArrayList<>();

        for (SalaryRecord s : records) {
            totalPayroll += s.getNetPaid() != null ? s.getNetPaid() : 0;
            totalAdvDed += s.getAdvanceDeducted() != null ? s.getAdvanceDeducted() : 0;
            totalBonus += s.getBonusAmount() != null ? s.getBonusAmount() : 0;
            Map<String, Object> map = new HashMap<>();
            map.put("id", s.getId());
            map.put("employeeName", s.getEmployee() != null ? s.getEmployee().getName() : "Staff");
            map.put("monthYear", s.getMonthYear());
            map.put("netPaid", s.getNetPaid());
            map.put("baseSalary", s.getBaseSalaryAtTime());
            map.put("paymentDate", s.getPaymentDate());
            list.add(map);
        }

        dto.setTotalPayroll(Math.round(totalPayroll * 100.0) / 100.0);
        dto.setTotalPaidCount(records.size());
        dto.setTotalAdvanceDeductions(Math.round(totalAdvDed * 100.0) / 100.0);
        dto.setTotalBonuses(Math.round(totalBonus * 100.0) / 100.0);
        dto.setSalaryRecords(list.stream().limit(10).collect(Collectors.toList()));

        response.setStatus("ANSWER");
        response.setData(dto);
        if (dto.getEmployeeName() != null) {
            response.setTitle("🧑‍💼 Salary Info: " + dto.getEmployeeName() + " (₹" + dto.getEmployeeSalary() + "/mo)");
            response.setSubtitle("Total Paid Records: " + records.size() + " • Net Payout: ₹" + dto.getTotalPayroll());
        } else {
            response.setTitle("💵 Total Payroll: ₹" + dto.getTotalPayroll() + " (" + dto.getTotalPaidCount() + " Payslips)");
            response.setSubtitle("Period: " + window.label + " • Advance Deductions: ₹" + dto.getTotalAdvanceDeductions());
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalPayroll", dto.getTotalPayroll());
        summary.put("totalPaidCount", dto.getTotalPaidCount());
        summary.put("advanceDeductions", dto.getTotalAdvanceDeductions());
        response.setSummary(summary);

        return response;
    }

    // ─────────────────────────────────────────────────────────────
    // 9. STAFF ADVANCES QUICK HELP
    // ─────────────────────────────────────────────────────────────
    public QuickHelpResponse<AdvanceQuickHelpDTO> getAdvanceQuickHelp(String employeeName) {
        Long firmId = TenantContext.getRequiredFirmId();
        QuickHelpResponse<AdvanceQuickHelpDTO> response = new QuickHelpResponse<>();
        response.setCapabilityId("QH_ADVANCES_SUMMARY");

        AdvanceQuickHelpDTO dto = new AdvanceQuickHelpDTO();
        List<Employee> allEmployees = employeeRepo.findByFirmId(firmId);

        double totalOutstanding = 0;
        long withAdvances = 0;
        for (Employee e : allEmployees) {
            double adv = e.getCurrentAdvanceBalance() != null ? e.getCurrentAdvanceBalance() : 0;
            if (adv > 0) {
                totalOutstanding += adv;
                withAdvances++;
            }
        }

        dto.setTotalOutstandingAdvances(Math.round(totalOutstanding * 100.0) / 100.0);
        dto.setStaffWithAdvancesCount(withAdvances);

        List<EmployeeAdvance> advances;
        if (employeeName != null && !employeeName.trim().isEmpty()) {
            String q = employeeName.trim();
            List<Employee> emps = employeeRepo.findTop5ByFirmIdAndNameContainingIgnoreCaseOrderByNameAsc(firmId, q);
            if (emps.isEmpty()) {
                response.setStatus("NO_MATCH");
                response.setTitle("No Employee Found");
                response.setSubtitle("No employee matches \"" + q + "\"");
                return response;
            }
            Employee emp = emps.get(0);
            dto.setEmployeeName(emp.getName());
            dto.setEmployeeAdvanceBalance(emp.getCurrentAdvanceBalance());
            advances = advanceRepo.findByEmployeeIdOrderByDateDesc(emp.getId());
        } else {
            advances = advanceRepo.findByEmployee_FirmIdOrderByDateDesc(firmId);
        }

        List<Map<String, Object>> records = new ArrayList<>();
        for (EmployeeAdvance a : advances) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", a.getId());
            map.put("employeeName", a.getEmployee() != null ? a.getEmployee().getName() : "Staff");
            map.put("amount", a.getAmount());
            map.put("date", a.getDate());
            map.put("repaymentStatus", a.getAmount() != null && a.getAmount() > 0 ? "DISBURSED" : "DEDUCTED");
            map.put("reason", a.getDescription() != null ? a.getDescription() : "Advance");
            records.add(map);
        }
        dto.setAdvanceRecords(records.stream().limit(10).collect(Collectors.toList()));

        response.setStatus("ANSWER");
        response.setData(dto);
        if (dto.getEmployeeName() != null) {
            response.setTitle("💵 Staff Advance: " + dto.getEmployeeName() + " (₹" + dto.getEmployeeAdvanceBalance() + " Due)");
            response.setSubtitle("Advance history entries: " + records.size());
        } else {
            response.setTitle("💵 Total Staff Advances Due: ₹" + dto.getTotalOutstandingAdvances());
            response.setSubtitle("Staff members with pending advances: " + withAdvances);
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalOutstandingAdvances", dto.getTotalOutstandingAdvances());
        summary.put("staffWithAdvancesCount", dto.getStaffWithAdvancesCount());
        response.setSummary(summary);

        return response;
    }

    // ─────────────────────────────────────────────────────────────
    // 10. SALES RETURNS & CREDIT NOTES QUICK HELP
    // ─────────────────────────────────────────────────────────────
    public QuickHelpResponse<SalesReturnQuickHelpDTO> getSalesReturnQuickHelp(String query, String period) {
        Long firmId = TenantContext.getRequiredFirmId();
        DateRangeWindow window = resolveDateRange(period != null ? period : "this_month", null, null);
        QuickHelpResponse<SalesReturnQuickHelpDTO> response = new QuickHelpResponse<>();
        response.setCapabilityId("QH_RETURNS_SUMMARY");

        SalesReturnQuickHelpDTO dto = new SalesReturnQuickHelpDTO();
        dto.setPeriod(window.label);

        List<SalesReturn> returns = salesReturnRepo.findByFirmIdOrderByReturnDateDescCreatedAtDesc(firmId);
        BigDecimal totalValue = BigDecimal.ZERO;
        List<Map<String, Object>> records = new ArrayList<>();

        for (SalesReturn r : returns) {
            BigDecimal val = r.getTotalRefundAmount() != null ? r.getTotalRefundAmount() : BigDecimal.ZERO;
            totalValue = totalValue.add(val);
            Map<String, Object> map = new HashMap<>();
            map.put("id", r.getId());
            map.put("returnNumber", r.getReturnNumber());
            map.put("returnDate", r.getReturnDate());
            map.put("customerName", r.getCustomer() != null ? r.getCustomer().getName() : "Customer");
            map.put("netTotal", val);
            map.put("reason", r.getReason());
            records.add(map);
        }

        dto.setTotalReturnValue(totalValue);
        dto.setReturnCount(returns.size());
        dto.setReturnRecords(records.stream().limit(10).collect(Collectors.toList()));

        response.setStatus("ANSWER");
        response.setData(dto);
        response.setTitle("↩️ Sales Returns & Credit Notes: ₹" + totalValue.setScale(2, RoundingMode.HALF_UP));
        response.setSubtitle("Total returned orders: " + returns.size() + " (" + window.label + ")");

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalReturnValue", totalValue);
        summary.put("returnCount", returns.size());
        response.setSummary(summary);

        return response;
    }

    // ─────────────────────────────────────────────────────────────
    // 11. COLLECTIONS & RECEIPTS QUICK HELP
    // ─────────────────────────────────────────────────────────────
    public QuickHelpResponse<CollectionReceiptQuickHelpDTO> getCollectionReceiptQuickHelp(String query, String period, String mode) {
        Long firmId = TenantContext.getRequiredFirmId();
        DateRangeWindow window = resolveDateRange(period != null ? period : "today", null, null);
        QuickHelpResponse<CollectionReceiptQuickHelpDTO> response = new QuickHelpResponse<>();
        response.setCapabilityId("QH_COLLECTIONS_SUMMARY");

        CollectionReceiptQuickHelpDTO dto = new CollectionReceiptQuickHelpDTO();
        dto.setPeriod(window.label);

        List<InvoicePayment> payments = invoicePaymentRepo.findByFirmIdOrderByPaymentDateDescIdDesc(firmId);
        BigDecimal totalColl = BigDecimal.ZERO;
        Map<String, BigDecimal> modes = new HashMap<>();
        List<Map<String, Object>> list = new ArrayList<>();

        for (InvoicePayment p : payments) {
            BigDecimal amt = p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO;
            totalColl = totalColl.add(amt);
            String m = p.getPaymentMode() != null ? p.getPaymentMode() : "CASH";
            modes.put(m, modes.getOrDefault(m, BigDecimal.ZERO).add(amt));

            Map<String, Object> map = new HashMap<>();
            map.put("id", p.getId());
            map.put("amount", amt);
            map.put("paymentDate", p.getPaymentDate());
            map.put("paymentMode", m);
            list.add(map);
        }

        dto.setTotalCollection(totalColl);
        dto.setReceiptCount(payments.size());
        dto.setModeBreakdown(modes);
        dto.setReceipts(list.stream().limit(10).collect(Collectors.toList()));

        response.setStatus("ANSWER");
        response.setData(dto);
        response.setTitle("💰 Total Payments Received: ₹" + totalColl.setScale(2, RoundingMode.HALF_UP));
        response.setSubtitle(payments.size() + " receipts recorded • Mode breakdown: Cash / UPI / Bank");

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalCollection", totalColl);
        summary.put("receiptCount", payments.size());
        summary.put("modeBreakdown", modes);
        response.setSummary(summary);

        return response;
    }

    // ─────────────────────────────────────────────────────────────
    // 12. VENDOR PAYMENTS QUICK HELP
    // ─────────────────────────────────────────────────────────────
    public QuickHelpResponse<VendorPayoutQuickHelpDTO> getVendorPayoutQuickHelp(String vendorName, String period) {
        Long firmId = TenantContext.getRequiredFirmId();
        DateRangeWindow window = resolveDateRange(period != null ? period : "this_month", null, null);
        QuickHelpResponse<VendorPayoutQuickHelpDTO> response = new QuickHelpResponse<>();
        response.setCapabilityId("QH_VENDOR_PAYOUTS_SUMMARY");

        VendorPayoutQuickHelpDTO dto = new VendorPayoutQuickHelpDTO();
        dto.setPeriod(window.label);

        List<PartyPayment> payments = partyPaymentRepo.findByFirmIdOrderByPaymentDateDescIdDesc(firmId);
        Map<Long, String> partyNames = partyRepo.findByFirmIdOrderByNameAsc(firmId).stream()
                .collect(Collectors.toMap(Party::getId, Party::getName, (a, b) -> a));

        BigDecimal totalPayout = BigDecimal.ZERO;
        List<Map<String, Object>> list = new ArrayList<>();

        for (PartyPayment p : payments) {
            BigDecimal amt = p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO;
            totalPayout = totalPayout.add(amt);
            Map<String, Object> map = new HashMap<>();
            map.put("id", p.getId());
            map.put("amount", amt);
            map.put("paymentDate", p.getPaymentDate());
            map.put("paymentMode", p.getPaymentMode());
            map.put("vendorName", partyNames.getOrDefault(p.getPartyId(), "Supplier"));
            list.add(map);
        }

        dto.setTotalPayout(totalPayout);
        dto.setPaymentCount(payments.size());
        dto.setPaymentRecords(list.stream().limit(10).collect(Collectors.toList()));

        response.setStatus("ANSWER");
        response.setData(dto);
        response.setTitle("💳 Vendor Payouts: ₹" + totalPayout.setScale(2, RoundingMode.HALF_UP));
        response.setSubtitle("Total payments to suppliers: " + payments.size() + " (" + window.label + ")");

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalPayout", totalPayout);
        summary.put("paymentCount", payments.size());
        response.setSummary(summary);

        return response;
    }

    // ─────────────────────────────────────────────────────────────
    // 13. PURCHASE ORDERS QUICK HELP
    // ─────────────────────────────────────────────────────────────
    public QuickHelpResponse<PurchaseOrderQuickHelpDTO> getPurchaseOrderQuickHelp(String query) {
        Long firmId = TenantContext.getRequiredFirmId();
        QuickHelpResponse<PurchaseOrderQuickHelpDTO> response = new QuickHelpResponse<>();
        response.setCapabilityId("QH_PURCHASE_ORDERS_SUMMARY");

        PurchaseOrderQuickHelpDTO dto = new PurchaseOrderQuickHelpDTO();
        List<PurchaseOrder> orders = purchaseOrderRepo.findByFirmIdOrderByPoDateDescIdDesc(firmId);

        long openCount = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<Map<String, Object>> list = new ArrayList<>();

        for (PurchaseOrder po : orders) {
            BigDecimal amt = po.getTotalAmount() != null ? po.getTotalAmount() : BigDecimal.ZERO;
            totalAmount = totalAmount.add(amt);
            if (po.getStatus() == PurchaseOrderStatus.ISSUED || po.getStatus() == PurchaseOrderStatus.DRAFT) {
                openCount++;
            }
            Map<String, Object> map = new HashMap<>();
            map.put("id", po.getId());
            map.put("poNumber", po.getPoNumber());
            map.put("poDate", po.getPoDate());
            map.put("vendorName", po.getParty() != null ? po.getParty().getName() : "Supplier");
            map.put("totalAmount", amt);
            map.put("status", po.getStatus() != null ? po.getStatus().name() : "OPEN");
            list.add(map);
        }

        dto.setTotalPoCount(orders.size());
        dto.setOpenPoCount(openCount);
        dto.setTotalPoAmount(totalAmount);
        dto.setRecentOrders(list.stream().limit(10).collect(Collectors.toList()));

        response.setStatus("ANSWER");
        response.setData(dto);
        response.setTitle("📦 Purchase Orders: " + orders.size() + " Total (" + openCount + " Open)");
        response.setSubtitle("Total Order Volume: ₹" + totalAmount.setScale(2, RoundingMode.HALF_UP));

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalPoCount", orders.size());
        summary.put("openPoCount", openCount);
        summary.put("totalPoAmount", totalAmount);
        response.setSummary(summary);

        return response;
    }

    // ─────────────────────────────────────────────────────────────
    // 14. STOCK MOVEMENTS QUICK HELP
    // ─────────────────────────────────────────────────────────────
    public QuickHelpResponse<StockMovementQuickHelpDTO> getStockMovementQuickHelp(String productQuery) {
        Long firmId = TenantContext.getRequiredFirmId();
        QuickHelpResponse<StockMovementQuickHelpDTO> response = new QuickHelpResponse<>();
        response.setCapabilityId("QH_STOCK_MOVEMENTS_SUMMARY");

        StockMovementQuickHelpDTO dto = new StockMovementQuickHelpDTO();
        List<StockMovement> movements = stockMovementRepo.findByFirmIdOrderByCreatedAtDesc(firmId);

        BigDecimal inward = BigDecimal.ZERO;
        BigDecimal outward = BigDecimal.ZERO;
        List<Map<String, Object>> list = new ArrayList<>();

        for (StockMovement sm : movements) {
            BigDecimal qty = sm.getQuantityChange() != null ? sm.getQuantityChange().abs() : BigDecimal.ZERO;
            String type = sm.getMovementType() != null ? sm.getMovementType() : "MOVEMENT";
            if (type.contains("IN") || (sm.getQuantityChange() != null && sm.getQuantityChange().compareTo(BigDecimal.ZERO) > 0)) {
                inward = inward.add(qty);
            } else {
                outward = outward.add(qty);
            }
            Map<String, Object> map = new HashMap<>();
            map.put("id", sm.getId());
            map.put("productName", sm.getProductName() != null ? sm.getProductName() : "Item");
            map.put("quantity", qty);
            map.put("type", type);
            map.put("date", sm.getCreatedAt());
            map.put("reason", sm.getReferenceType() != null ? sm.getReferenceType() : "Adjustment");
            list.add(map);
        }

        dto.setTotalMovements(movements.size());
        dto.setTotalInward(inward);
        dto.setTotalOutward(outward);
        dto.setMovements(list.stream().limit(10).collect(Collectors.toList()));

        response.setStatus("ANSWER");
        response.setData(dto);
        response.setTitle("📊 Stock Movement Log: " + movements.size() + " Recent Actions");
        response.setSubtitle("Total Inward: " + inward + " units • Total Outward: " + outward + " units");

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalMovements", movements.size());
        summary.put("totalInward", inward);
        summary.put("totalOutward", outward);
        response.setSummary(summary);

        return response;
    }

    // ─────────────────────────────────────────────────────────────
    // 15. CUSTOMER / VENDOR LEDGER STATEMENT QUICK HELP
    // ─────────────────────────────────────────────────────────────
    public QuickHelpResponse<LedgerStatementQuickHelpDTO> getLedgerQuickHelp(String entityType, String name) {
        Long firmId = TenantContext.getRequiredFirmId();
        QuickHelpResponse<LedgerStatementQuickHelpDTO> response = new QuickHelpResponse<>();
        response.setCapabilityId("QH_LEDGER_SUMMARY");

        LedgerStatementQuickHelpDTO dto = new LedgerStatementQuickHelpDTO();
        dto.setEntityType(entityType != null ? entityType.toUpperCase() : "CUSTOMER");

        if ("VENDOR".equalsIgnoreCase(entityType)) {
            List<Party> parties = partyRepo.findByFirmIdAndNameContainingIgnoreCase(firmId, name != null ? name.trim() : "");
            if (parties.isEmpty()) {
                response.setStatus("NO_MATCH");
                response.setTitle("Vendor Not Found");
                response.setSubtitle("No vendor matches \"" + name + "\"");
                return response;
            }
            Party p = parties.get(0);
            VendorQuickHelpDTO vDTO = assembleVendorDetails(firmId, p);
            dto.setEntityId(p.getId());
            dto.setEntityName(p.getName());
            dto.setOpeningBalance(p.getOpeningBalance() != null ? p.getOpeningBalance() : BigDecimal.ZERO);
            dto.setTotalDebits(vDTO.getTotalPurchases());
            dto.setTotalCredits(vDTO.getTotalPaid());
            dto.setClosingBalance(vDTO.getCurrentPayable());
        } else {
            List<Customer> customers = customerRepo.findTop5ByFirmIdAndNameContainingIgnoreCaseOrderByNameAsc(firmId, name != null ? name.trim() : "");
            if (customers.isEmpty()) {
                response.setStatus("NO_MATCH");
                response.setTitle("Customer Not Found");
                response.setSubtitle("No customer matches \"" + name + "\"");
                return response;
            }
            Customer c = customers.get(0);
            CustomerQuickHelpDTO cDTO = assembleCustomerDetails(firmId, c);
            dto.setEntityId(c.getId());
            dto.setEntityName(c.getName());
            dto.setOpeningBalance(BigDecimal.ZERO);
            dto.setTotalDebits(cDTO.getTotalSales());
            dto.setTotalCredits(cDTO.getTotalPaid());
            dto.setClosingBalance(cDTO.getCurrentOutstanding());
        }

        response.setStatus("ANSWER");
        response.setData(dto);
        response.setTitle("📑 Ledger: " + dto.getEntityName() + " (" + dto.getEntityType() + ")");
        response.setSubtitle("Opening: ₹" + dto.getOpeningBalance() + " • Billed: ₹" + dto.getTotalDebits() + " • Paid: ₹" + dto.getTotalCredits() + " • Net Balance: ₹" + dto.getClosingBalance());

        Map<String, Object> summary = new HashMap<>();
        summary.put("entityName", dto.getEntityName());
        summary.put("openingBalance", dto.getOpeningBalance());
        summary.put("closingBalance", dto.getClosingBalance());
        summary.put("totalDebits", dto.getTotalDebits());
        summary.put("totalCredits", dto.getTotalCredits());
        response.setSummary(summary);

        return response;
    }

    // ─────────────────────────────────────────────────────────────
    // 16. UNIFIED CROSS-DOMAIN SEARCH BACKEND
    // ─────────────────────────────────────────────────────────────
    public QuickHelpResponse<UnifiedSearchResultDTO> searchAllEntities(String query) {
        Long firmId = TenantContext.getRequiredFirmId();
        QuickHelpResponse<UnifiedSearchResultDTO> response = new QuickHelpResponse<>();
        response.setCapabilityId("QH_UNIFIED_SEARCH");

        UnifiedSearchResultDTO dto = new UnifiedSearchResultDTO();
        dto.setQuery(query);

        if (query == null || query.trim().isEmpty()) {
            response.setStatus("ANSWER");
            response.setData(dto);
            return response;
        }

        String q = query.trim();

        // 1. Employees
        List<Employee> emps = employeeRepo.findTop5ByFirmIdAndNameContainingIgnoreCaseOrderByNameAsc(firmId, q);
        dto.setEmployees(emps.stream().map(e -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", e.getId());
            m.put("name", e.getName());
            m.put("role", e.getRole());
            m.put("phone", e.getPhone());
            m.put("monthlyBaseSalary", e.getMonthlyBaseSalary());
            m.put("advanceBalance", e.getCurrentAdvanceBalance());
            return m;
        }).collect(Collectors.toList()));

        // 2. Customers
        List<Customer> custs = customerRepo.findTop5ByFirmIdAndNameContainingIgnoreCaseOrderByNameAsc(firmId, q);
        dto.setCustomers(custs.stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            m.put("phone", c.getPhone());
            m.put("city", c.getAddress());
            return m;
        }).collect(Collectors.toList()));

        // 3. Vendors
        List<Party> parties = partyRepo.findByFirmIdAndNameContainingIgnoreCase(firmId, q).stream().limit(5).collect(Collectors.toList());
        dto.setVendors(parties.stream().map(p -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("phone", p.getPhone());
            return m;
        }).collect(Collectors.toList()));

        // 4. Products
        List<Product> prods = productRepo.findTop5ByFirmIdAndNameContainingIgnoreCaseOrderByNameAsc(firmId, q);
        dto.setProducts(prods.stream().map(p -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("price", p.getPrice());
            m.put("stock", p.getStockQuantity());
            return m;
        }).collect(Collectors.toList()));

        // 5. Invoices
        List<Invoice> invs = invoiceRepo.findByFirmId(firmId).stream()
                .filter(i -> i.getInvoiceNumber() != null && i.getInvoiceNumber().toLowerCase().contains(q.toLowerCase()))
                .limit(5)
                .collect(Collectors.toList());
        dto.setInvoices(invs.stream().map(i -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", i.getId());
            m.put("invoiceNumber", i.getInvoiceNumber());
            m.put("customerName", i.getCustomer() != null ? i.getCustomer().getName() : "Customer");
            m.put("amount", i.getTotalAmount());
            return m;
        }).collect(Collectors.toList()));

        // 6. Expenses
        List<Expense> exps = expenseRepo.findByFirmIdOrderByExpenseDateDescIdDesc(firmId).stream()
                .filter(e -> e.getTitle() != null && e.getTitle().toLowerCase().contains(q.toLowerCase()))
                .limit(5)
                .collect(Collectors.toList());
        dto.setExpenses(exps.stream().map(e -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", e.getId());
            m.put("title", e.getTitle());
            m.put("amount", e.getAmount());
            m.put("category", e.getCategory());
            return m;
        }).collect(Collectors.toList()));

        response.setStatus("ANSWER");
        response.setData(dto);
        int totalMatches = dto.getEmployees().size() + dto.getCustomers().size() + dto.getVendors().size() + dto.getProducts().size() + dto.getInvoices().size() + dto.getExpenses().size();
        response.setTitle("🔍 Found " + totalMatches + " Result(s) for \"" + q + "\"");
        response.setSubtitle("Staff: " + dto.getEmployees().size() + " • Customers: " + dto.getCustomers().size() + " • Items: " + dto.getProducts().size());

        return response;
    }
}

