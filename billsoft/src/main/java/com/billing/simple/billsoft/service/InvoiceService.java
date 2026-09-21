package com.billing.simple.billsoft.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billing.simple.billsoft.dtos.CustomerAnalyticsResponse;
import com.billing.simple.billsoft.dtos.CustomerInvoiceSummary;
import com.billing.simple.billsoft.dtos.FirmAnalyticsResponse;
import com.billing.simple.billsoft.dtos.InvoiceRequest;
import com.billing.simple.billsoft.dtos.InvoiceRequest.Discount;
import com.billing.simple.billsoft.dtos.InvoiceRequestItem;
import com.billing.simple.billsoft.dtos.InvoiceUpdateRequest;
import com.billing.simple.billsoft.engine.InvoiceCalculationEngine;
import com.billing.simple.billsoft.entities.AppConfig;
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.InvoiceItem;
import com.billing.simple.billsoft.entities.InvoicePayment;
import com.billing.simple.billsoft.entities.InvoiceStatus;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.entities.SalesReturn;
import com.billing.simple.billsoft.entities.SalesReturnItem;
import com.billing.simple.billsoft.dtos.SalesReturnRequest;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repo.InvoiceItemRepository;
import com.billing.simple.billsoft.repo.InvoicePaymentRepository;
import com.billing.simple.billsoft.repo.InvoiceRepository;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.repo.SalesReturnItemRepository;
import com.billing.simple.billsoft.repo.SalesReturnRepository;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepo;
    private final InvoiceItemRepository invoiceItemRepo;
    private final CustomerRepository customerRepo;
    private final ProductRepository productRepo;
    private final ProductService productService;
    private final InvoicePaymentRepository invoicePaymentRepo;
    private final AppConfigRepository appConfigRepo;
    private final SalesReturnRepository salesReturnRepo;
    private final SalesReturnItemRepository salesReturnItemRepo;
    private final InvoiceCalculationEngine engine;
    private SavedItemService savedItemService;

    // scales
    private static final int SCALE = 2;
    private static final int CALC_SCALE = 10;

    // zero convenience
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);

    public InvoiceService(
            InvoiceRepository invoiceRepo,
            InvoiceItemRepository invoiceItemRepo,
            CustomerRepository customerRepo,
            ProductRepository productRepo,
            ProductService productService,
            InvoicePaymentRepository invoicePaymentRepo,
            AppConfigRepository appConfigRepo,
            SalesReturnRepository salesReturnRepo,
            SalesReturnItemRepository salesReturnItemRepo,
            @org.springframework.beans.factory.annotation.Autowired(required = false) SavedItemService savedItemService) {
        this.invoiceRepo = invoiceRepo;
        this.invoiceItemRepo = invoiceItemRepo;
        this.customerRepo = customerRepo;
        this.productRepo = productRepo;
        this.productService = productService;
        this.invoicePaymentRepo = invoicePaymentRepo;
        this.appConfigRepo = appConfigRepo;
        this.salesReturnRepo = salesReturnRepo;
        this.salesReturnItemRepo = salesReturnItemRepo;
        this.savedItemService = savedItemService;
        this.engine = new InvoiceCalculationEngine();
    }

    // -------------------------
    // Number generators (atomic monotonic max sequence & read-only peek)
    // -------------------------
    public String peekNextInvoiceNumber(Long firmId) {
        String configKey = "LAST_INVOICE_SEQ_" + (firmId != null ? firmId : 0);
        long lastSeq = appConfigRepo.findById(configKey)
                .map(AppConfig::getConfigValue)
                .map(v -> {
                    try {
                        return Long.parseLong(v);
                    } catch (Exception e) {
                        return 0L;
                    }
                })
                .orElse(0L);

        if (lastSeq == 0 && firmId != null) {
            long dbMax = 0;
            List<String> numbers = invoiceRepo.findInvoiceNumbersByFirmId(firmId);
            if (numbers != null) {
                for (String num : numbers) {
                    if (num != null && num.trim().startsWith("INV-")) {
                        try {
                            long seq = Long.parseLong(num.trim().substring(4).trim());
                            if (seq > dbMax)
                                dbMax = seq;
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
            lastSeq = dbMax;
            if (lastSeq > 0) {
                try {
                    AppConfig cfg = appConfigRepo.findById(configKey).orElse(new AppConfig());
                    cfg.setConfigKey(configKey);
                    cfg.setConfigValue(String.valueOf(lastSeq));
                    appConfigRepo.save(cfg);
                } catch (Exception ignored) {
                }
            }
        }

        long nextVal = lastSeq + 1;
        return String.format("INV-%04d", nextVal);
    }

    public synchronized String generateInvoiceNumber(Long firmId) {
        String nextNo = peekNextInvoiceNumber(firmId);
        try {
            long nextVal = Long.parseLong(nextNo.substring(4));
            String configKey = "LAST_INVOICE_SEQ_" + (firmId != null ? firmId : 0);
            AppConfig cfg = appConfigRepo.findById(configKey).orElse(new AppConfig());
            cfg.setConfigKey(configKey);
            cfg.setConfigValue(String.valueOf(nextVal));
            appConfigRepo.save(cfg);
        } catch (Exception ignored) {
        }
        return nextNo;
    }

    public String peekNextEstimateNumber(Long firmId) {
        String configKey = "LAST_ESTIMATE_SEQ_" + (firmId != null ? firmId : 0);
        long lastSeq = appConfigRepo.findById(configKey)
                .map(AppConfig::getConfigValue)
                .map(v -> {
                    try {
                        return Long.parseLong(v);
                    } catch (Exception e) {
                        return 0L;
                    }
                })
                .orElse(0L);

        if (lastSeq == 0 && firmId != null) {
            long dbMax = 0;
            List<String> numbers = invoiceRepo.findEstimateNumbersByFirmId(firmId);
            if (numbers != null) {
                for (String num : numbers) {
                    if (num != null && num.trim().startsWith("EST-")) {
                        try {
                            long seq = Long.parseLong(num.trim().substring(4).trim());
                            if (seq > dbMax)
                                dbMax = seq;
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
            lastSeq = dbMax;
            if (lastSeq > 0) {
                try {
                    AppConfig cfg = appConfigRepo.findById(configKey).orElse(new AppConfig());
                    cfg.setConfigKey(configKey);
                    cfg.setConfigValue(String.valueOf(lastSeq));
                    appConfigRepo.save(cfg);
                } catch (Exception ignored) {
                }
            }
        }

        long nextVal = lastSeq + 1;
        return String.format("EST-%04d", nextVal);
    }

    public synchronized String generateEstimateNumber(Long firmId) {
        String nextNo = peekNextEstimateNumber(firmId);
        try {
            long nextVal = Long.parseLong(nextNo.substring(4));
            String configKey = "LAST_ESTIMATE_SEQ_" + (firmId != null ? firmId : 0);
            AppConfig cfg = appConfigRepo.findById(configKey).orElse(new AppConfig());
            cfg.setConfigKey(configKey);
            cfg.setConfigValue(String.valueOf(nextVal));
            appConfigRepo.save(cfg);
        } catch (Exception ignored) {
        }
        return nextNo;
    }

    public void syncSequenceIfApplicable(Long firmId, String number, String prefix, String configKeyPrefix) {
        if (number != null && number.trim().startsWith(prefix)) {
            try {
                long seq = Long.parseLong(number.trim().substring(prefix.length()).trim());
                String configKey = configKeyPrefix + (firmId != null ? firmId : 0);
                long currentSeq = appConfigRepo.findById(configKey)
                        .map(AppConfig::getConfigValue)
                        .map(v -> {
                            try {
                                return Long.parseLong(v);
                            } catch (Exception e) {
                                return 0L;
                            }
                        }).orElse(0L);
                if (seq > currentSeq) {
                    AppConfig cfg = appConfigRepo.findById(configKey).orElse(new AppConfig());
                    cfg.setConfigKey(configKey);
                    cfg.setConfigValue(String.valueOf(seq));
                    appConfigRepo.save(cfg);
                }
            } catch (Exception ignored) {
            }
        }
    }

    // -------------------------
    // Date parser (lenient)
    // -------------------------
    private LocalDateTime parseDateLenient(String s) {
        if (s == null)
            return null;
        try {
            return LocalDateTime.parse(s);
        } catch (Exception e1) {
            try {
                return LocalDateTime.parse(s + ":00");
            } catch (Exception e2) {
                try {
                    LocalDate ld = LocalDate.parse(s);
                    return ld.atStartOfDay();
                } catch (Exception e3) {
                    return null;
                }
            }
        }
    }

    // -------------------------
    // Helpers
    // -------------------------
    private void normalizeStatus(Invoice inv) {
        if (inv.getStatus() == null) {
            inv.setStatus(Boolean.TRUE.equals(inv.getPaid()) ? InvoiceStatus.PAID : InvoiceStatus.UNPAID);
        }
        if (Boolean.TRUE.equals(inv.getPaid()) && (inv.getStatus() == InvoiceStatus.UNPAID
                || inv.getStatus() == InvoiceStatus.OVERDUE || inv.getStatus() == InvoiceStatus.SENT)) {
            inv.setStatus(InvoiceStatus.PAID);
        }
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    @Transactional(readOnly = true)
    public FirmAnalyticsResponse getFirmAnalytics() {
        return getFirmAnalytics(com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId());
    }

    @Transactional(readOnly = true)
    public FirmAnalyticsResponse getFirmAnalytics(Long firmId) {
        Long targetFirmId = firmId != null ? firmId
                : com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        if (targetFirmId == null) {
            throw new com.billing.simple.billsoft.security.TenantSecurityException(
                    "Active firm ID is required to generate firm analytics.");
        }
        List<InvoiceStatus> activeStatuses = List.of(InvoiceStatus.FINAL, InvoiceStatus.UNPAID,
                InvoiceStatus.PAID, InvoiceStatus.OVERDUE, InvoiceStatus.SENT);
        List<Invoice> all = invoiceRepo.findInvoicesWithCustomerForAnalytics(targetFirmId, activeStatuses);
        List<Invoice> estimates = invoiceRepo.findAllByFirmIdAndStatusOrderByInvoiceDateAsc(targetFirmId,
                InvoiceStatus.ESTIMATE);
        if (estimates != null) {
            all.addAll(estimates);
        }
        LocalDate today = LocalDate.now();

        BigDecimal totalBusiness = BigDecimal.ZERO.setScale(SCALE);
        BigDecimal totalPaid = BigDecimal.ZERO.setScale(SCALE);
        BigDecimal totalPending = BigDecimal.ZERO.setScale(SCALE);

        BigDecimal todayBusiness = BigDecimal.ZERO.setScale(SCALE);
        BigDecimal weekBusiness = BigDecimal.ZERO.setScale(SCALE);
        BigDecimal monthBusiness = BigDecimal.ZERO.setScale(SCALE);
        BigDecimal yearBusiness = BigDecimal.ZERO.setScale(SCALE);

        Map<Long, FirmAnalyticsResponse.TopCustomer> customerMap = new HashMap<>();

        LocalDate weekStart = today.minusDays(6);

        List<InvoicePayment> allFirmPayments = invoicePaymentRepo
                .findByFirmIdOrderByPaymentDateDescIdDesc(targetFirmId);
        Map<Long, BigDecimal> invoicePaymentMap = new HashMap<>();
        for (InvoicePayment ip : allFirmPayments) {
            if (ip.getInvoiceId() != null && ip.getAmount() != null) {
                invoicePaymentMap.merge(ip.getInvoiceId(), ip.getAmount(), BigDecimal::add);
            }
        }

        List<Object[]> returnAggs = salesReturnRepo.sumRefundTotalsByInvoiceForFirm(targetFirmId);
        Map<Long, BigDecimal> invoiceReturnMap = new HashMap<>();
        if (returnAggs != null) {
            for (Object[] row : returnAggs) {
                if (row[0] != null && row[1] != null) {
                    invoiceReturnMap.put(((Number) row[0]).longValue(), (BigDecimal) row[1]);
                }
            }
        }

        long unpaidCount = 0;
        long overdueCount = 0;
        double quotePipelineVal = 0.0;
        long quoteCnt = 0;

        for (Invoice inv : all) {
            normalizeStatus(inv);

            BigDecimal rawAmt = nz(inv.getTotalAmount());
            if (inv.getStatus() == InvoiceStatus.ESTIMATE) {
                if (inv.getConvertedInvoiceId() == null) {
                    quoteCnt++;
                    quotePipelineVal += rawAmt.doubleValue();
                }
                continue;
            }

            BigDecimal returnedAmt = invoiceReturnMap.getOrDefault(inv.getId(), BigDecimal.ZERO);
            BigDecimal effectiveAmt = rawAmt.subtract(returnedAmt);
            if (effectiveAmt.compareTo(BigDecimal.ZERO) < 0)
                effectiveAmt = BigDecimal.ZERO;

            totalBusiness = totalBusiness.add(effectiveAmt);

            BigDecimal paidForInv = invoicePaymentMap.getOrDefault(inv.getId(), BigDecimal.ZERO);
            if (paidForInv.compareTo(BigDecimal.ZERO) == 0 && Boolean.TRUE.equals(inv.getPaid())) {
                paidForInv = effectiveAmt;
            }
            if (paidForInv.compareTo(effectiveAmt) > 0) {
                paidForInv = effectiveAmt;
            }

            BigDecimal pendingForInv = effectiveAmt.subtract(paidForInv);
            if (pendingForInv.compareTo(BigDecimal.ZERO) < 0) {
                pendingForInv = BigDecimal.ZERO;
            }

            totalPaid = totalPaid.add(paidForInv);
            totalPending = totalPending.add(pendingForInv);

            InvoiceStatus st = inv.getStatus();
            if (st == InvoiceStatus.UNPAID || st == InvoiceStatus.OVERDUE || st == InvoiceStatus.SENT
                    || st == InvoiceStatus.DRAFT) {
                unpaidCount++;
            }
            if (st == InvoiceStatus.OVERDUE || (inv.getDueDate() != null && inv.getDueDate().isBefore(today)
                    && !Boolean.TRUE.equals(inv.getPaid()) && st != InvoiceStatus.CANCELLED)) {
                overdueCount++;
            }

            LocalDate invDate = inv.getInvoiceDate() != null ? inv.getInvoiceDate().toLocalDate() : null;

            if (invDate != null) {
                if (invDate.isEqual(today))
                    todayBusiness = todayBusiness.add(effectiveAmt);
                if (!invDate.isBefore(weekStart))
                    weekBusiness = weekBusiness.add(effectiveAmt);
                if (invDate.getMonth() == today.getMonth() && invDate.getYear() == today.getYear())
                    monthBusiness = monthBusiness.add(effectiveAmt);
                if (invDate.getYear() == today.getYear())
                    yearBusiness = yearBusiness.add(effectiveAmt);
            }

            Customer c = inv.getCustomer();
            if (c != null) {
                FirmAnalyticsResponse.TopCustomer agg = customerMap.computeIfAbsent(c.getId(), id -> {
                    FirmAnalyticsResponse.TopCustomer t = new FirmAnalyticsResponse.TopCustomer();
                    t.setCustomerId(c.getId());
                    t.setCustomerName(c.getName());
                    t.setTotalAmount(0.0);
                    t.setPendingAmount(0.0);
                    t.setInvoiceCount(0L);
                    return t;
                });

                agg.setTotalAmount(agg.getTotalAmount() + effectiveAmt.doubleValue());
                agg.setPendingAmount(agg.getPendingAmount() + pendingForInv.doubleValue());
                agg.setInvoiceCount(agg.getInvoiceCount() + 1);
            }
        }

        List<FirmAnalyticsResponse.TopCustomer> topCustomers = new ArrayList<>(customerMap.values());
        topCustomers.sort((a, b) -> Double.compare(
                b.getTotalAmount() != null ? b.getTotalAmount() : 0.0,
                a.getTotalAmount() != null ? a.getTotalAmount() : 0.0));
        if (topCustomers.size() > 5)
            topCustomers = topCustomers.subList(0, 5);

        // Fetch top products directly via aggregate SQL query
        List<Object[]> productAggs = (firmId != null)
                ? invoiceItemRepo.findTopProductsByFirmId(firmId, activeStatuses, PageRequest.of(0, 5))
                : invoiceItemRepo.findTopProductsAll(activeStatuses, PageRequest.of(0, 5));

        List<FirmAnalyticsResponse.TopProduct> topProducts = new ArrayList<>();
        if (productAggs != null) {
            for (Object[] row : productAggs) {
                if (row != null && row.length >= 4 && row[0] != null) {
                    FirmAnalyticsResponse.TopProduct p = new FirmAnalyticsResponse.TopProduct();
                    p.setProductId(((Number) row[0]).longValue());
                    p.setProductName(row[1] != null ? row[1].toString() : "Product");
                    p.setTotalQty(row[2] != null ? ((Number) row[2]).longValue() : 0L);
                    p.setTotalAmount(row[3] != null ? ((Number) row[3]).doubleValue() : 0.0);
                    topProducts.add(p);
                }
            }
        }

        FirmAnalyticsResponse resp = new FirmAnalyticsResponse();
        resp.setTotalBusiness(totalBusiness.doubleValue());
        resp.setTotalPaid(totalPaid.doubleValue());
        resp.setTotalPending(totalPending.doubleValue());

        resp.setUnpaidInvoiceCount(unpaidCount);
        resp.setOverdueInvoiceCount(overdueCount);
        resp.setQuotePipelineValue(quotePipelineVal);
        resp.setQuoteCount(quoteCnt);

        resp.setBusinessToday(todayBusiness.doubleValue());
        resp.setBusinessThisWeek(weekBusiness.doubleValue());
        resp.setBusinessThisMonth(monthBusiness.doubleValue());
        resp.setBusinessThisYear(yearBusiness.doubleValue());

        resp.setTopCustomers(topCustomers);
        resp.setTopProducts(topProducts);

        return resp;
    }

    private List<Product> fetchProductsReferencedBy(InvoiceRequest req, InvoiceUpdateRequest uReq) {
        Set<Long> ids = new HashSet<>();
        if (req != null && req.getItems() != null) {
            req.getItems().stream()
                    .map(InvoiceRequestItem::getProductId)
                    .filter(Objects::nonNull)
                    .forEach(ids::add);
        }
        if (uReq != null && uReq.getItems() != null) {
            uReq.getItems().stream()
                    .map(InvoiceRequestItem::getProductId)
                    .filter(Objects::nonNull)
                    .forEach(ids::add);
        }
        if (ids.isEmpty())
            return Collections.emptyList();
        Iterable<Product> found = productRepo.findAllById(ids);
        List<Product> list = new ArrayList<>();
        found.forEach(list::add);
        return list;
    }

    private Customer resolveCustomerForFirm(Long requestedCustomerId, Long effectiveFirmId) {
        if (requestedCustomerId == null) {
            return null;
        }
        if (effectiveFirmId == null || effectiveFirmId <= 0) {
            throw new com.billing.simple.billsoft.security.TenantSecurityException("Active firm context is required to resolve customer");
        }
        Customer customer = customerRepo.findByIdAndFirmId(requestedCustomerId, effectiveFirmId).orElse(null);
        if (customer != null) {
            return customer;
        }
        throw new com.billing.simple.billsoft.security.TenantSecurityException(
                "Customer not found or does not belong to firm: " + requestedCustomerId);
    }

    // -------------------------
    // CREATE (persist)
    // -------------------------
    @Transactional(rollbackFor = Exception.class)
    public Invoice createInvoice(InvoiceRequest request) {
        Long currentFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        Long effectiveFirmId = currentFirmId != null ? currentFirmId : request.getFirmId();
        if (effectiveFirmId == null) {
            throw new com.billing.simple.billsoft.security.TenantSecurityException(
                    "firmId is required to create an invoice");
        }
        request.setFirmId(effectiveFirmId);

        if (request.getItems() == null) {
            request.setItems(Collections.emptyList());
        }

        // Basic validation: invoice-level discount & items
        validateInvoiceDiscount(request.getInvoiceDiscount());
        validateInvoiceItems(request.getItems());

        // Load customer if provided (resolve or replicate into target firm if switching
        // firms)
        Customer customer = resolveCustomerForFirm(request.getCustomerId(), effectiveFirmId);
        if (customer != null) {
            request.setCustomerId(customer.getId());
        }

        // Determine effective status
        InvoiceStatus status = request.getStatus();
        if (status == null || status == InvoiceStatus.FINAL) {
            if (Boolean.TRUE.equals(request.getPaid())) {
                status = InvoiceStatus.PAID;
            } else {
                status = InvoiceStatus.UNPAID;
            }
            request.setStatus(status);
        } else if (Boolean.TRUE.equals(request.getPaid()) && status != InvoiceStatus.ESTIMATE
                && status != InvoiceStatus.DRAFT) {
            status = InvoiceStatus.PAID;
            request.setStatus(status);
        }

        Invoice invoice = new Invoice();
        invoice.setFirmId(effectiveFirmId);
        invoice.setPaid(status == InvoiceStatus.PAID);

        // identifiers (Option B behaviour)
        if (status == InvoiceStatus.ESTIMATE) {
            // Estimate: ONLY estimateNumber, NO invoiceNumber
            if (request.getEstimateNumber() != null && !request.getEstimateNumber().isBlank()) {
                invoice.setEstimateNumber(request.getEstimateNumber());
            } else {
                invoice.setEstimateNumber(generateEstimateNumber(effectiveFirmId));
                request.setEstimateNumber(invoice.getEstimateNumber()); // keep dto consistent
            }
            invoice.setInvoiceNumber(null);
        } else {
            // Final/invoice: MUST have invoiceNumber, estimateNumber optional
            String invNo = request.getInvoiceNumber();
            if (invNo == null || invNo.isBlank()) {
                invNo = generateInvoiceNumber(effectiveFirmId);
                request.setInvoiceNumber(invNo);
            }
            invoice.setInvoiceNumber(invNo);

            // if caller accidentally sent estimateNumber also, keep it but it's not
            // required
            if (request.getEstimateNumber() != null && !request.getEstimateNumber().isBlank()) {
                invoice.setEstimateNumber(request.getEstimateNumber());
            }
        }

        // fetch product data once
        List<Product> products = fetchProductsReferencedBy(request, null);
        for (Product p : products) {
            if (p.getFirmId() != null && !p.getFirmId().equals(effectiveFirmId)) {
                throw new com.billing.simple.billsoft.security.TenantSecurityException(
                        "Product " + p.getName() + " (ID " + p.getId() + ") belongs to a different firm.");
            }
        }

        // delegate calculations to engine (isUpdateMode=false)
        Invoice calculated = engine.calculate(invoice, customer, products, request, false);
        if (status == InvoiceStatus.PAID) {
            calculated.setPaid(true);
            calculated.setStatus(InvoiceStatus.PAID);
        } else if (status == InvoiceStatus.UNPAID) {
            calculated.setPaid(false);
            calculated.setStatus(InvoiceStatus.UNPAID);
        }
        Invoice saved = invoiceRepo.save(calculated);

        if (saved.getInvoiceNumber() != null) {
            syncSequenceIfApplicable(saved.getFirmId(), saved.getInvoiceNumber(), "INV-", "LAST_INVOICE_SEQ_");
        }
        if (saved.getEstimateNumber() != null) {
            syncSequenceIfApplicable(saved.getFirmId(), saved.getEstimateNumber(), "EST-", "LAST_ESTIMATE_SEQ_");
        }

        // If this invoice is converted from a quotation/estimate, link the quotation
        if (request.getConvertedInvoiceId() != null) {
            Invoice linkedEstimate = invoiceRepo.findByIdAndFirmId(request.getConvertedInvoiceId(), effectiveFirmId)
                    .orElseThrow(() -> new com.billing.simple.billsoft.security.TenantSecurityException(
                            "Converted quotation not found for firm: " + request.getConvertedInvoiceId()));
            if (linkedEstimate.getConvertedInvoiceId() != null
                    && !linkedEstimate.getConvertedInvoiceId().equals(saved.getId())) {
                throw new RuntimeException("This quotation has already been converted to invoice #"
                        + linkedEstimate.getConvertedInvoiceId());
            }
            linkedEstimate.setConvertedInvoiceId(saved.getId());
            invoiceRepo.save(linkedEstimate);
        }

        // Deduct inventory stock for finalized sales invoices (skip for DRAFT,
        // ESTIMATE, CANCELLED)
        if (saved.getStatus() != InvoiceStatus.ESTIMATE && saved.getStatus() != InvoiceStatus.DRAFT
                && saved.getStatus() != InvoiceStatus.CANCELLED && saved.getItems() != null) {
            for (InvoiceItem it : saved.getItems()) {
                if (it.getProduct() != null && it.getProduct().getId() != null && it.getQty() != null
                        && it.getQty() > 0) {
                    BigDecimal qty = new BigDecimal(it.getQty());
                    productService.recordStockMovement(
                            it.getProduct().getId(),
                            saved.getFirmId(),
                            "INVOICE_SALE",
                            qty.negate(),
                            "INVOICE",
                            saved.getInvoiceNumber() != null ? saved.getInvoiceNumber() : String.valueOf(saved.getId()),
                            "Invoice sale to "
                                    + (saved.getCustomer() != null ? saved.getCustomer().getName() : "Customer"));
                }
            }
        }

        // Record uncatalogued line items into SavedItems (fail-safe auxiliary hook)
        if (savedItemService != null) {
            savedItemService.recordOrUpdateFromInvoice(saved);
        }

        return saved;
    }

    // convenience: create estimate
    @Transactional(rollbackFor = Exception.class)
    public Invoice createEstimate(InvoiceRequest request) {
        request.setStatus(InvoiceStatus.ESTIMATE);
        if (request.getEstimateNumber() == null || request.getEstimateNumber().isBlank()) {
            request.setEstimateNumber(generateEstimateNumber(request.getFirmId()));
        }
        // IMPORTANT: do NOT set invoiceNumber here; createInvoice() will treat ESTIMATE
        // correctly
        return createInvoice(request);
    }

    // -------------------------
    // PREVIEW (no persist)
    // -------------------------
    public Invoice previewInvoice(InvoiceRequest request) {
        if (request.getItems() == null)
            request.setItems(Collections.emptyList());

        // Validation for invoice-level discount & items
        validateInvoiceDiscount(request.getInvoiceDiscount());
        validateInvoiceItems(request.getItems());

        Customer customer = null;
        if (request.getCustomerId() != null) {
            customer = customerRepo.findById(request.getCustomerId()).orElse(null);
        }

        InvoiceStatus status = request.getStatus();
        if (status == null) {
            status = InvoiceStatus.FINAL;
            request.setStatus(status);
        }

        Invoice invoice = new Invoice();
        Long firmId = request.getFirmId();

        if (status == InvoiceStatus.ESTIMATE) {
            String estNo = request.getEstimateNumber();
            if (estNo == null || estNo.isBlank()) {
                estNo = peekNextEstimateNumber(firmId);
                request.setEstimateNumber(estNo);
            }
            invoice.setEstimateNumber(estNo);
            invoice.setInvoiceNumber(null);
        } else {
            String invNo = request.getInvoiceNumber();
            if (invNo == null || invNo.isBlank()) {
                invNo = peekNextInvoiceNumber(firmId);
                request.setInvoiceNumber(invNo);
            }
            invoice.setInvoiceNumber(invNo);
            invoice.setEstimateNumber(request.getEstimateNumber());
        }

        List<Product> products = fetchProductsReferencedBy(request, null);
        Invoice calc = engine.calculate(invoice, customer, products, request, false);
        calc.setId(null);
        return calc;
    }

    // -------------------------
    // GET / LIST / DELETE
    // -------------------------
    public List<Invoice> getAll(Long firmId) {
        Long authoritativeFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        if (authoritativeFirmId != null && firmId != null && !authoritativeFirmId.equals(firmId)) {
            throw new com.billing.simple.billsoft.security.TenantSecurityException("Cross-firm access prohibited");
        }
        Long targetFirmId = (authoritativeFirmId != null) ? authoritativeFirmId : firmId;
        List<Invoice> all = (targetFirmId != null)
                ? invoiceRepo.findByFirmIdAndCustomerNameContainingIgnoreCase(targetFirmId, "")
                : invoiceRepo.findAll();
        all.forEach(this::normalizeStatus);
        return all;
    }

    public List<Invoice> getAll() {
        Long currentFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        List<Invoice> all = currentFirmId != null ? invoiceRepo.findByFirmId(currentFirmId) : invoiceRepo.findAll();
        all.forEach(this::normalizeStatus);
        return all;
    }

    public Invoice getById(Long id) {
        Long currentFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        Invoice inv = (currentFirmId != null ? invoiceRepo.findByIdAndFirmId(id, currentFirmId) : invoiceRepo.findById(id)).orElse(null);
        if (inv == null)
            return null;
        normalizeStatus(inv);
        return inv;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long id) {
        Long currentFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        Invoice inv = (currentFirmId != null ? invoiceRepo.findByIdAndFirmId(id, currentFirmId) : invoiceRepo.findById(id)).orElse(null);
        if (inv == null)
            return false;
        // Prevent deleting an estimate that has already been converted to an invoice
        if (inv.getConvertedInvoiceId() != null) {
            throw new RuntimeException("Cannot delete this estimate as it has already been converted to invoice #"
                    + inv.getConvertedInvoiceId());
        }

        // Restore inventory stock if an invoice is deleted (subtracting any quantities
        // already returned)
        if (inv.getStatus() != InvoiceStatus.ESTIMATE && inv.getStatus() != InvoiceStatus.CANCELLED
                && inv.getItems() != null) {
            Map<Long, Integer> returnedQtyByItemId = new HashMap<>();
            List<Object[]> returnQtyAggs = salesReturnItemRepo.sumReturnedQtyByInvoiceItemIdForInvoice(id);
            if (returnQtyAggs != null) {
                for (Object[] row : returnQtyAggs) {
                    if (row[0] != null && row[1] != null) {
                        returnedQtyByItemId.put(((Number) row[0]).longValue(), ((Number) row[1]).intValue());
                    }
                }
            }
            for (InvoiceItem it : inv.getItems()) {
                if (it.getProduct() != null && it.getProduct().getId() != null && it.getQty() != null
                        && it.getQty() > 0) {
                    int alreadyReturned = returnedQtyByItemId.getOrDefault(it.getId(), 0);
                    int restorableQty = Math.max(0, it.getQty() - alreadyReturned);
                    if (restorableQty > 0) {
                        BigDecimal qty = new BigDecimal(restorableQty);
                        productService.recordStockMovement(
                                it.getProduct().getId(),
                                inv.getFirmId(),
                                "INVOICE_CANCELLED",
                                qty,
                                "INVOICE",
                                inv.getInvoiceNumber() != null ? inv.getInvoiceNumber() : String.valueOf(inv.getId()),
                                "Invoice deleted - stock restored (" + restorableQty + " of " + it.getQty()
                                        + " unreturned items)");
                    }
                }
            }
        }

        salesReturnItemRepo.nullifyInvoiceItemReferencesByInvoiceId(id);
        invoiceRepo.delete(inv);
        return true;
    }

    // -------------------------
    // UPDATE FULL
    // -------------------------
    @Transactional(rollbackFor = Exception.class)
    public Invoice updateFullInvoice(Long id, InvoiceUpdateRequest req) {
        Long currentFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        Invoice existing = (currentFirmId != null ? invoiceRepo.findByIdAndFirmId(id, currentFirmId)
                : invoiceRepo.findById(id))
                .orElseThrow(() -> new com.billing.simple.billsoft.security.TenantSecurityException(
                        "Invoice not found in firm: " + id));

        // Nullify foreign key references in sales_return_items before items are
        // replaced or deleted
        salesReturnItemRepo.nullifyInvoiceItemReferencesByInvoiceId(id);

        // If items are null, we do a metadata-only update.
        // We'll use a temporary InvoiceRequest to leverage the engine.
        InvoiceRequest engineReq = new InvoiceRequest();

        // Map req to engineReq (InvoiceUpdateRequest -> InvoiceRequest)
        engineReq.setCustomerId(req.getCustomerId());
        engineReq.setInvoiceDate(req.getInvoiceDate());
        engineReq.setCustomerNote(req.getCustomerNote() != null ? req.getCustomerNote() : req.getNotes());

        engineReq.setTermsAndConditions(req.getTermsAndConditions());
        engineReq.setPaymentMethod(req.getPaymentMethod());
        engineReq.setCurrency(req.getCurrency());
        engineReq.setTags(req.getTags());
        engineReq.setStatus(req.getStatus());
        engineReq.setEstimateNumber(req.getEstimateNumber());
        engineReq.setDueDate(req.getDueDate());
        engineReq.setPaid(req.getPaid());
        engineReq.setRoundOff(req.getRoundOff());
        engineReq.setInvoiceDiscount(req.getInvoiceDiscount());
        engineReq.setItems(req.getItems());

        // Resolve customer
        Customer cust = (req.getCustomerId() != null)
                ? resolveCustomerForFirm(req.getCustomerId(), existing.getFirmId())
                : existing.getCustomer();

        // Parse and set date if provided
        if (req.getInvoiceDate() != null) {
            LocalDateTime dt = parseDateLenient(req.getInvoiceDate());
            if (dt != null)
                existing.setInvoiceDate(dt);
        }

        // Resolve products
        List<Product> products = fetchProductsReferencedBy(null, req);

        // Delegate to engine
        Invoice calculated = engine.calculate(existing, cust, products, engineReq, true);

        Invoice saved = invoiceRepo.save(calculated);

        // Record uncatalogued line items into SavedItems (fail-safe auxiliary hook)
        if (savedItemService != null) {
            savedItemService.recordOrUpdateFromInvoice(saved);
        }

        return saved;
    }

    private InvoiceRequest buildInvoiceRequestFromExistingInvoice(Invoice existing) {
        InvoiceRequest r = new InvoiceRequest();
        if (existing.getCustomer() != null)
            r.setCustomerId(existing.getCustomer().getId());
        r.setCustomerNote(existing.getCustomerNote());
        r.setTermsAndConditions(existing.getTermsAndConditions());
        r.setPaymentMethod(existing.getPaymentMethod());
        r.setCurrency(existing.getCurrency());
        r.setTags(existing.getTags());
        r.setStatus(existing.getStatus());
        r.setEstimateNumber(existing.getEstimateNumber());
        r.setDueDate(existing.getDueDate());
        r.setPaid(existing.getPaid());
        r.setRoundOff(existing.getRoundOff() != null && existing.getRoundOff().compareTo(BigDecimal.ZERO) != 0);

        // Rebuild invoice-level discount from entity fields, if present
        if (existing.getInvoiceDiscountType() != null && existing.getInvoiceDiscountValue() != null) {
            InvoiceRequest.Discount d = new InvoiceRequest.Discount();
            d.setType(existing.getInvoiceDiscountType());
            d.setValue(existing.getInvoiceDiscountValue());
            r.setInvoiceDiscount(d);
        }

        if (existing.getItems() != null) {
            List<InvoiceRequestItem> items = existing.getItems().stream().map(it -> {
                InvoiceRequestItem ri = new InvoiceRequestItem();
                ri.setProductId(it.getProduct() != null ? it.getProduct().getId() : null);
                ri.setProductName(it.getProductName());
                ri.setHsnCode(it.getHsnCode());
                ri.setQty(it.getQty());
                ri.setUnit(it.getUnit());
                ri.setPricePerUnit(it.getPricePerUnit());
                // Item-level discount is VALUE only
                ri.setDiscountValue(it.getDiscountValue());
                ri.setGstPercent(it.getGstPercent());
                return ri;
            }).collect(Collectors.toList());
            r.setItems(items);
        } else {
            r.setItems(Collections.emptyList());
        }
        return r;
    }

    // -------------------------
    // Update paid flag
    // -------------------------
    @Transactional(rollbackFor = Exception.class)
    public Invoice updatePaidFlag(Long id, boolean paid) {
        Long currentFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        Invoice i = (currentFirmId != null ? invoiceRepo.findByIdAndFirmId(id, currentFirmId)
                : invoiceRepo.findById(id)).orElse(null);
        if (i == null)
            return null;
        i.setPaid(paid);
        if (paid) {
            i.setStatus(InvoiceStatus.PAID);
        } else {
            // Revert status to UNPAID if it was PAID
            if (i.getStatus() == InvoiceStatus.PAID || i.getStatus() == InvoiceStatus.FINAL) {
                i.setStatus(InvoiceStatus.UNPAID);
            }
        }
        return invoiceRepo.save(i);
    }

    @Transactional(rollbackFor = Exception.class)
    public Invoice updateStatus(Long id, InvoiceStatus newStatus) {
        Long currentFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        Invoice i = (currentFirmId != null ? invoiceRepo.findByIdAndFirmId(id, currentFirmId)
                : invoiceRepo.findById(id)).orElse(null);
        if (i == null)
            return null;
        InvoiceStatus oldStatus = i.getStatus();
        if (oldStatus == newStatus)
            return i;

        // If cancelling an active invoice, restore stock to inventory (subtracting any
        // quantities already returned)
        if (newStatus == InvoiceStatus.CANCELLED) {
            if (oldStatus != InvoiceStatus.CANCELLED && oldStatus != InvoiceStatus.DRAFT
                    && oldStatus != InvoiceStatus.ESTIMATE && i.getItems() != null) {
                Map<Long, Integer> returnedQtyByItemId = new HashMap<>();
                List<Object[]> returnQtyAggs = salesReturnItemRepo.sumReturnedQtyByInvoiceItemIdForInvoice(i.getId());
                if (returnQtyAggs != null) {
                    for (Object[] row : returnQtyAggs) {
                        if (row[0] != null && row[1] != null) {
                            returnedQtyByItemId.put(((Number) row[0]).longValue(), ((Number) row[1]).intValue());
                        }
                    }
                }
                for (InvoiceItem it : i.getItems()) {
                    if (it.getProduct() != null && it.getProduct().getId() != null && it.getQty() != null
                            && it.getQty() > 0) {
                        int alreadyReturned = returnedQtyByItemId.getOrDefault(it.getId(), 0);
                        int restorableQty = Math.max(0, it.getQty() - alreadyReturned);
                        if (restorableQty > 0) {
                            BigDecimal qty = new BigDecimal(restorableQty);
                            productService.recordStockMovement(
                                    it.getProduct().getId(),
                                    i.getFirmId(),
                                    "INVOICE_CANCELLED",
                                    qty,
                                    "INVOICE",
                                    i.getInvoiceNumber() != null ? i.getInvoiceNumber() : String.valueOf(i.getId()),
                                    "Invoice cancelled - stock restored for "
                                            + (i.getCustomer() != null ? i.getCustomer().getName() : "Customer")
                                            + " (" + restorableQty + " of " + it.getQty() + " unreturned items)");
                        }
                    }
                }
            }
            i.setPaid(false);
        } else if (oldStatus == InvoiceStatus.CANCELLED || oldStatus == InvoiceStatus.DRAFT) {
            // Re-activating from CANCELLED or finalizing from DRAFT -> deduct stock
            if (newStatus != InvoiceStatus.ESTIMATE && newStatus != InvoiceStatus.DRAFT && i.getItems() != null) {
                for (InvoiceItem it : i.getItems()) {
                    if (it.getProduct() != null && it.getProduct().getId() != null && it.getQty() != null
                            && it.getQty() > 0) {
                        BigDecimal qty = new BigDecimal(it.getQty());
                        productService.recordStockMovement(
                                it.getProduct().getId(),
                                i.getFirmId(),
                                "INVOICE_SALE",
                                qty.negate(),
                                "INVOICE",
                                i.getInvoiceNumber() != null ? i.getInvoiceNumber() : String.valueOf(i.getId()),
                                "Invoice reactivated/finalized for "
                                        + (i.getCustomer() != null ? i.getCustomer().getName() : "Customer"));
                    }
                }
            }
        }

        i.setStatus(newStatus);
        if (newStatus == InvoiceStatus.PAID) {
            i.setPaid(true);
        } else if (newStatus == InvoiceStatus.UNPAID || newStatus == InvoiceStatus.FINAL
                || newStatus == InvoiceStatus.DRAFT || newStatus == InvoiceStatus.CANCELLED) {
            i.setPaid(false);
        }
        return invoiceRepo.save(i);
    }

    @Transactional(rollbackFor = Exception.class)
    public Invoice convertEstimateToInvoice(Long estimateId, InvoiceRequest overrideRequest) {
        Long currentFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        Invoice estimate = (currentFirmId != null)
                ? invoiceRepo.findByIdAndFirmId(estimateId, currentFirmId).orElse(null)
                : invoiceRepo.findById(estimateId).orElse(null);

        if (estimate == null)
            throw new com.billing.simple.billsoft.security.TenantSecurityException("Estimate not found: " + estimateId);

        if (currentFirmId != null && !currentFirmId.equals(estimate.getFirmId())) {
            throw new com.billing.simple.billsoft.security.TenantSecurityException(
                    "Cross-firm quotation conversion is strictly prohibited.");
        }

        if (estimate.getStatus() != InvoiceStatus.ESTIMATE && estimate.getStatus() != InvoiceStatus.DRAFT)
            throw new RuntimeException("Only estimates/drafts can be converted");

        if (estimate.getConvertedInvoiceId() != null) {
            throw new RuntimeException(
                    "This quotation has already been converted to invoice #" + estimate.getConvertedInvoiceId());
        }

        // Validate that customer and items belong to the authorized firm
        Long firmId = estimate.getFirmId();
        if (estimate.getCustomer() != null && estimate.getCustomer().getFirmId() != null) {
            if (!estimate.getCustomer().getFirmId().equals(firmId)) {
                throw new com.billing.simple.billsoft.security.TenantSecurityException(
                        "Quotation customer belongs to a different firm.");
            }
        }
        if (estimate.getItems() != null) {
            for (InvoiceItem it : estimate.getItems()) {
                if (it.getProduct() != null && it.getProduct().getFirmId() != null
                        && !it.getProduct().getFirmId().equals(firmId)) {
                    throw new com.billing.simple.billsoft.security.TenantSecurityException(
                            "Quotation product (ID " + it.getProduct().getId() + ") belongs to a different firm.");
                }
            }
        }

        // Validate invoice-level discount on override request (if present)
        if (overrideRequest != null) {
            validateInvoiceDiscount(overrideRequest.getInvoiceDiscount());
            overrideRequest.setFirmId(firmId);
        }

        // Base clone of estimate → new invoice
        Invoice newInv = new Invoice();
        newInv.setCustomer(estimate.getCustomer());
        newInv.setCustomerNote(estimate.getCustomerNote());
        newInv.setTermsAndConditions(estimate.getTermsAndConditions());
        newInv.setPaymentMethod(estimate.getPaymentMethod());
        newInv.setCurrency(estimate.getCurrency());
        newInv.setTags(estimate.getTags());

        boolean isPaid = overrideRequest != null && Boolean.TRUE.equals(overrideRequest.getPaid());
        newInv.setFirmId(firmId);
        newInv.setInvoiceNumber(generateInvoiceNumber(firmId));
        newInv.setStatus(isPaid ? InvoiceStatus.PAID : InvoiceStatus.UNPAID);
        newInv.setInvoiceDate(LocalDateTime.now());
        newInv.setPaid(isPaid);

        if (estimate.getDueDate() != null) {
            newInv.setDueDate(estimate.getDueDate());
        } else if (estimate.getInvoiceDate() != null) {
            newInv.setDueDate(estimate.getInvoiceDate().toLocalDate().plusDays(14));
        }

        // Case 1: overrideRequest has items -> use override completely
        if (overrideRequest != null && overrideRequest.getItems() != null && !overrideRequest.getItems().isEmpty()) {
            overrideRequest.setStatus(isPaid ? InvoiceStatus.PAID : InvoiceStatus.UNPAID);
            overrideRequest.setPaid(isPaid);
            overrideRequest.setInvoiceNumber(newInv.getInvoiceNumber());
            overrideRequest.setEstimateNumber(null);
            overrideRequest.setFirmId(firmId);

            List<Product> products = fetchProductsReferencedBy(overrideRequest, null);
            for (Product p : products) {
                if (p.getFirmId() != null && !p.getFirmId().equals(firmId)) {
                    throw new com.billing.simple.billsoft.security.TenantSecurityException(
                            "Override product " + p.getName() + " belongs to a different firm.");
                }
            }

            Invoice calculated = engine.calculate(newInv, newInv.getCustomer(), products, overrideRequest, false);
            calculated.setStatus(isPaid ? InvoiceStatus.PAID : InvoiceStatus.UNPAID);
            calculated.setPaid(isPaid);

            Invoice saved = invoiceRepo.save(calculated);
            estimate.setConvertedInvoiceId(saved.getId());
            invoiceRepo.save(estimate);

            // Deduct inventory stock for sales invoices
            deductInventoryStockForInvoice(saved);

            return saved;
        }

        // Case 2: NO item override -> keep original items, BUT apply metadata overrides
        if (overrideRequest != null) {
            // Only override fields that are explicitly provided
            if (overrideRequest.getCustomerNote() != null) {
                newInv.setCustomerNote(overrideRequest.getCustomerNote());
            }
            if (overrideRequest.getTermsAndConditions() != null) {
                newInv.setTermsAndConditions(overrideRequest.getTermsAndConditions());
            }
            if (overrideRequest.getPaymentMethod() != null) {
                newInv.setPaymentMethod(overrideRequest.getPaymentMethod());
            }
            if (overrideRequest.getCurrency() != null) {
                newInv.setCurrency(overrideRequest.getCurrency());
            }
            if (overrideRequest.getTags() != null) {
                newInv.setTags(overrideRequest.getTags());
            }
            if (overrideRequest.getDueDate() != null) {
                newInv.setDueDate(overrideRequest.getDueDate());
            }
        }

        // Clone items from estimate as-is
        List<InvoiceItem> cloned = new ArrayList<>();
        if (estimate.getItems() != null) {
            for (InvoiceItem it : estimate.getItems()) {
                InvoiceItem ni = new InvoiceItem();
                ni.setProduct(it.getProduct());
                ni.setProductName(it.getProductName());
                ni.setQty(it.getQty());
                ni.setUnit(it.getUnit());
                ni.setHsnCode(it.getHsnCode());
                ni.setPricePerUnit(it.getPricePerUnit());
                ni.setAmountWithoutTax(it.getAmountWithoutTax());
                ni.setDiscountType(it.getDiscountType());
                ni.setDiscountPercent(it.getDiscountPercent());
                ni.setDiscountValue(it.getDiscountValue());
                ni.setTaxableAmount(it.getTaxableAmount());
                ni.setGstPercent(it.getGstPercent());
                ni.setGstAmount(it.getGstAmount());
                ni.setLineTotal(it.getLineTotal());
                ni.setInvoice(newInv);
                cloned.add(ni);
            }
        }
        newInv.getItems().addAll(cloned);

        // Build a request from the (now overridden) newInv
        InvoiceRequest req = buildInvoiceRequestFromExistingInvoice(newInv);
        req.setStatus(isPaid ? InvoiceStatus.PAID : InvoiceStatus.UNPAID);
        req.setPaid(isPaid);
        req.setInvoiceNumber(newInv.getInvoiceNumber());
        req.setEstimateNumber(null);
        req.setFirmId(firmId);

        List<Product> products = fetchProductsReferencedBy(req, null);
        for (Product p : products) {
            if (p.getFirmId() != null && !p.getFirmId().equals(firmId)) {
                throw new com.billing.simple.billsoft.security.TenantSecurityException(
                        "Product " + p.getName() + " belongs to a different firm.");
            }
        }

        Invoice calculated = engine.calculate(newInv, newInv.getCustomer(), products, req, true);

        Invoice saved = invoiceRepo.save(calculated);
        estimate.setConvertedInvoiceId(saved.getId());
        invoiceRepo.save(estimate);

        // Deduct inventory stock for sales invoices
        deductInventoryStockForInvoice(saved);

        return saved;
    }

    private void deductInventoryStockForInvoice(Invoice invoice) {
        if (invoice != null && invoice.getStatus() != InvoiceStatus.ESTIMATE && invoice.getItems() != null) {
            for (InvoiceItem it : invoice.getItems()) {
                if (it.getProduct() != null && it.getProduct().getId() != null && it.getQty() != null
                        && it.getQty() > 0) {
                    BigDecimal qty = new BigDecimal(it.getQty());
                    productService.recordStockMovement(
                            it.getProduct().getId(),
                            invoice.getFirmId(),
                            "INVOICE_SALE",
                            qty.negate(),
                            "INVOICE",
                            invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber()
                                    : String.valueOf(invoice.getId()),
                            "Invoice sale to "
                                    + (invoice.getCustomer() != null ? invoice.getCustomer().getName() : "Customer"));
                }
            }
        }
    }

    // -------------------------
    // Analytics / helpers
    // -------------------------
    public CustomerAnalyticsResponse getCustomerAnalytics(Long customerId) {
        List<Invoice> invoices = invoiceRepo.findByCustomer_Id(customerId);

        CustomerAnalyticsResponse resp = new CustomerAnalyticsResponse();
        resp.setCustomerId(customerId);

        if (invoices == null || invoices.isEmpty()) {
            resp.setCustomerName(null);
            resp.setTotalBusiness(0.0);
            resp.setTotalPaid(0.0);
            resp.setTotalPending(0.0);
            resp.setInvoiceCount(0L);
            resp.setInvoices(Collections.emptyList());
            return resp;
        }

        Customer c = invoices.get(0).getCustomer();
        resp.setCustomerName(c != null ? c.getName() : null);

        BigDecimal totalBusiness = BigDecimal.ZERO.setScale(SCALE);
        BigDecimal totalPaid = BigDecimal.ZERO.setScale(SCALE);

        List<SalesReturn> custReturns = salesReturnRepo.findByCustomerIdOrderByReturnDateDesc(customerId);
        Map<Long, BigDecimal> custInvoiceReturnMap = new HashMap<>();
        for (SalesReturn sr : custReturns) {
            if (sr.getInvoice() != null && sr.getTotalRefundAmount() != null) {
                custInvoiceReturnMap.merge(sr.getInvoice().getId(), sr.getTotalRefundAmount(), BigDecimal::add);
            }
        }

        List<InvoicePayment> allCustPayments = invoicePaymentRepo
                .findByCustomerIdOrderByPaymentDateAscIdAsc(customerId);
        Map<Long, BigDecimal> custInvoicePaymentMap = new HashMap<>();
        for (InvoicePayment ip : allCustPayments) {
            if (ip.getInvoiceId() != null && ip.getAmount() != null) {
                custInvoicePaymentMap.merge(ip.getInvoiceId(), ip.getAmount(), BigDecimal::add);
            }
        }

        for (Invoice inv : invoices) {
            BigDecimal rawAmt = nz(inv.getTotalAmount());
            BigDecimal retAmt = custInvoiceReturnMap.getOrDefault(inv.getId(), BigDecimal.ZERO);
            BigDecimal effectiveAmt = rawAmt.subtract(retAmt);
            if (effectiveAmt.compareTo(BigDecimal.ZERO) < 0)
                effectiveAmt = BigDecimal.ZERO;

            totalBusiness = totalBusiness.add(effectiveAmt);

            BigDecimal paidForInv = custInvoicePaymentMap.getOrDefault(inv.getId(), BigDecimal.ZERO);
            if (paidForInv.compareTo(BigDecimal.ZERO) == 0 && Boolean.TRUE.equals(inv.getPaid())) {
                paidForInv = effectiveAmt;
            }
            if (paidForInv.compareTo(effectiveAmt) > 0) {
                paidForInv = effectiveAmt;
            }
            totalPaid = totalPaid.add(paidForInv);
        }

        List<CustomerInvoiceSummary> list = invoices.stream()
                .map(inv -> {
                    CustomerInvoiceSummary s = new CustomerInvoiceSummary();
                    s.setInvoiceId(inv.getId());
                    s.setInvoiceNumber(inv.getInvoiceNumber());
                    s.setInvoiceDate(inv.getInvoiceDate() == null ? null : inv.getInvoiceDate().toString());
                    s.setTotalAmount(inv.getTotalAmount() == null ? null : inv.getTotalAmount().doubleValue());
                    s.setPaid(inv.getPaid());
                    return s;
                }).collect(Collectors.toList());

        resp.setTotalBusiness(totalBusiness.doubleValue());
        resp.setTotalPaid(totalPaid.doubleValue());
        BigDecimal totalPending = totalBusiness.subtract(totalPaid);
        if (totalPending.compareTo(BigDecimal.ZERO) < 0)
            totalPending = BigDecimal.ZERO;
        resp.setTotalPending(totalPending.doubleValue());
        resp.setInvoiceCount((long) invoices.size());
        resp.setInvoices(list);

        return resp;
    }

    public List<CustomerAnalyticsResponse> getCustomerAnalyticsByName(String namePart) {
        if (namePart == null || namePart.trim().isEmpty())
            return Collections.emptyList();

        List<Invoice> invoices = invoiceRepo.findByCustomer_NameContainingIgnoreCase(namePart);
        if (invoices == null || invoices.isEmpty())
            return Collections.emptyList();

        Set<Long> ids = invoices.stream()
                .map(Invoice::getCustomer)
                .filter(Objects::nonNull)
                .map(Customer::getId)
                .collect(Collectors.toSet());

        List<CustomerAnalyticsResponse> list = new ArrayList<>();
        for (Long id : ids)
            list.add(getCustomerAnalytics(id));

        return list;
    }

    public Map<String, Double> getFirmStats() {
        Long firmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        List<Invoice> all = firmId != null ? invoiceRepo.findByFirmId(firmId) : invoiceRepo.findAll();
        LocalDate today = LocalDate.now();

        BigDecimal totalBusiness = BigDecimal.ZERO.setScale(SCALE);
        BigDecimal totalPaid = BigDecimal.ZERO.setScale(SCALE);
        BigDecimal totalPending = BigDecimal.ZERO.setScale(SCALE);

        BigDecimal todayBusiness = BigDecimal.ZERO.setScale(SCALE);
        BigDecimal weekBusiness = BigDecimal.ZERO.setScale(SCALE);
        BigDecimal monthBusiness = BigDecimal.ZERO.setScale(SCALE);
        BigDecimal yearBusiness = BigDecimal.ZERO.setScale(SCALE);

        List<InvoicePayment> allPayments = firmId != null ? invoicePaymentRepo.findByFirmIdOrderByPaymentDateDescIdDesc(firmId) : invoicePaymentRepo.findAll();
        Map<Long, BigDecimal> invoicePaymentMap = new HashMap<>();
        for (InvoicePayment ip : allPayments) {
            if (ip.getInvoiceId() != null && ip.getAmount() != null) {
                invoicePaymentMap.merge(ip.getInvoiceId(), ip.getAmount(), BigDecimal::add);
            }
        }

        List<Object[]> returnAggs = salesReturnRepo.sumRefundTotalsByInvoiceForFirm(firmId);
        Map<Long, BigDecimal> invoiceReturnMap = new HashMap<>();
        if (returnAggs != null) {
            for (Object[] row : returnAggs) {
                if (row[0] != null && row[1] != null) {
                    invoiceReturnMap.put(((Number) row[0]).longValue(), (BigDecimal) row[1]);
                }
            }
        }

        for (Invoice i : all) {
            BigDecimal rawAmt = nz(i.getTotalAmount());
            if (i.getStatus() == InvoiceStatus.ESTIMATE || i.getStatus() == InvoiceStatus.CANCELLED) {
                continue;
            }
            BigDecimal returnedAmt = invoiceReturnMap.getOrDefault(i.getId(), BigDecimal.ZERO);
            BigDecimal effectiveAmt = rawAmt.subtract(returnedAmt);
            if (effectiveAmt.compareTo(BigDecimal.ZERO) < 0)
                effectiveAmt = BigDecimal.ZERO;

            totalBusiness = totalBusiness.add(effectiveAmt);

            BigDecimal paidForInv = invoicePaymentMap.getOrDefault(i.getId(), BigDecimal.ZERO);
            if (paidForInv.compareTo(BigDecimal.ZERO) == 0 && Boolean.TRUE.equals(i.getPaid())) {
                paidForInv = effectiveAmt;
            }
            if (paidForInv.compareTo(effectiveAmt) > 0) {
                paidForInv = effectiveAmt;
            }
            BigDecimal pendingForInv = effectiveAmt.subtract(paidForInv);
            if (pendingForInv.compareTo(BigDecimal.ZERO) < 0) {
                pendingForInv = BigDecimal.ZERO;
            }

            totalPaid = totalPaid.add(paidForInv);
            totalPending = totalPending.add(pendingForInv);

            if (i.getInvoiceDate() != null) {
                LocalDate d = i.getInvoiceDate().toLocalDate();
                if (d.isEqual(today))
                    todayBusiness = todayBusiness.add(effectiveAmt);
                if (!d.isBefore(today.minusDays(6)))
                    weekBusiness = weekBusiness.add(effectiveAmt);
                if (d.getMonth() == today.getMonth() && d.getYear() == today.getYear())
                    monthBusiness = monthBusiness.add(effectiveAmt);
                if (d.getYear() == today.getYear())
                    yearBusiness = yearBusiness.add(effectiveAmt);
            }
        }

        Map<String, Double> map = new HashMap<>();
        map.put("totalBusiness", totalBusiness.doubleValue());
        map.put("totalPaid", totalPaid.doubleValue());
        map.put("totalPending", totalPending.doubleValue());
        map.put("businessToday", todayBusiness.doubleValue());
        map.put("businessThisWeek", weekBusiness.doubleValue());
        map.put("businessThisMonth", monthBusiness.doubleValue());
        map.put("businessThisYear", yearBusiness.doubleValue());

        return map;
    }

    // -------------------------
    // Paginated lists
    // -------------------------
    public Page<Invoice> getPaginated(Long firmId, Pageable pageable) {
        Long targetFirmId = (firmId != null) ? firmId
                : com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        if (targetFirmId != null) {
            return invoiceRepo.findByFirmId(targetFirmId, pageable);
        }
        return Page.empty(pageable);
    }

    public Page<Invoice> getPaginatedEstimates(Long firmId, Pageable pageable) {
        Long targetFirmId = (firmId != null) ? firmId
                : com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        if (targetFirmId != null) {
            return invoiceRepo.findByFirmIdAndStatus(targetFirmId, InvoiceStatus.ESTIMATE, pageable);
        }
        return Page.empty(pageable);
    }

    public Page<Invoice> getPaginatedFinalInvoices(Long firmId, Pageable pageable) {
        Long targetFirmId = (firmId != null) ? firmId
                : com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        if (targetFirmId != null) {
            return invoiceRepo
                    .findByFirmIdAndStatusIn(targetFirmId,
                            List.of(InvoiceStatus.FINAL, InvoiceStatus.UNPAID, InvoiceStatus.PAID, InvoiceStatus.DRAFT,
                                    InvoiceStatus.CANCELLED, InvoiceStatus.OVERDUE, InvoiceStatus.SENT),
                            pageable);
        }
        return Page.empty(pageable);
    }

    public List<Invoice> getAll(Long firmId, Pageable pageable) {
        return getPaginated(firmId, pageable).getContent();
    }

    public List<Invoice> getAllEstimates(Long firmId, Pageable pageable) {
        return getPaginatedEstimates(firmId, pageable).getContent();
    }

    public List<Invoice> getAllFinalInvoices(Long firmId, Pageable pageable) {
        return getPaginatedFinalInvoices(firmId, pageable).getContent();
    }

    // -------------------------
    // Convenience lists
    // -------------------------
    public List<Invoice> getAllEstimates(Long firmId) {
        Long targetFirmId = (firmId != null) ? firmId
                : com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        List<Invoice> list;
        if (targetFirmId != null) {
            list = invoiceRepo.findAllByFirmIdAndStatusOrderByInvoiceDateDesc(targetFirmId, InvoiceStatus.ESTIMATE);
        } else {
            list = java.util.Collections.emptyList();
        }
        list.forEach(this::normalizeStatus);
        return list;
    }

    public List<Invoice> getAllFinalInvoices(Long firmId) {
        Long targetFirmId = (firmId != null) ? firmId
                : com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        List<Invoice> list;
        if (targetFirmId != null) {
            list = invoiceRepo.findAllByFirmIdAndStatusInOrderByInvoiceDateDesc(targetFirmId,
                    List.of(InvoiceStatus.FINAL, InvoiceStatus.UNPAID, InvoiceStatus.PAID, InvoiceStatus.DRAFT,
                            InvoiceStatus.CANCELLED, InvoiceStatus.OVERDUE, InvoiceStatus.SENT));
        } else {
            list = java.util.Collections.emptyList();
        }
        list.forEach(this::normalizeStatus);
        return list;
    }

    // -------------------------
    // Validation helpers
    // -------------------------
    private void validateInvoiceDiscount(Discount d) {
        if (d == null)
            return;
        if (d.getType() == null || d.getValue() == null)
            return;

        String type = d.getType().trim().toUpperCase();
        if (!"PERCENT".equals(type) && !"VALUE".equals(type)) {
            throw new IllegalArgumentException("Invalid invoiceDiscount.type: " + d.getType());
        }

        BigDecimal value = d.getValue();
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("invoiceDiscount.value cannot be negative");
        }
    }

    private void validateInvoiceItems(List<InvoiceRequestItem> items) {
        if (items == null)
            return;
        for (InvoiceRequestItem it : items) {
            if (it.getQty() != null && it.getQty() <= 0) {
                throw new IllegalArgumentException("Item quantity must be greater than 0");
            }
            if (it.getPricePerUnit() != null && it.getPricePerUnit().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Item price per unit cannot be negative");
            }
            if (it.getDiscountValue() != null && it.getDiscountValue().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Item discount cannot be negative");
            }
            if (it.getGstPercent() != null && it.getGstPercent().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Item GST percentage cannot be negative");
            }
        }
    }

    // -------------------------
    // PARTIAL PAYMENTS
    // -------------------------
    @Transactional(rollbackFor = Exception.class)
    public InvoicePayment recordPayment(Long invoiceId, BigDecimal amount, LocalDate paymentDate, String paymentMode,
            String referenceNumber, String notes) {
        Long currentFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        Invoice invoice = (currentFirmId != null ? invoiceRepo.findByIdAndFirmId(invoiceId, currentFirmId)
                : invoiceRepo.findById(invoiceId))
                .orElseThrow(() -> new com.billing.simple.billsoft.security.TenantSecurityException(
                        "Invoice not found in firm: " + invoiceId));

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than 0");
        }

        InvoicePayment payment = InvoicePayment.builder()
                .firmId(invoice.getFirmId())
                .invoiceId(invoice.getId())
                .customerId(invoice.getCustomer() != null ? invoice.getCustomer().getId() : null)
                .amount(amount.setScale(2, RoundingMode.HALF_UP))
                .paymentDate(paymentDate != null ? paymentDate : LocalDate.now())
                .paymentMode(paymentMode != null ? paymentMode : "Cash")
                .referenceNumber(referenceNumber)
                .notes(notes)
                .build();

        InvoicePayment saved = invoicePaymentRepo.save(payment);

        Long effectiveFirmId = invoice.getFirmId();

        // Check if invoice is fully settled (factoring in sales returns / credit notes)
        List<InvoicePayment> allPayments = effectiveFirmId != null
                ? invoicePaymentRepo.findByInvoiceIdAndFirmIdOrderByPaymentDateAscIdAsc(invoiceId, effectiveFirmId)
                : invoicePaymentRepo.findByInvoiceIdOrderByPaymentDateAscIdAsc(invoiceId);
        BigDecimal totalPaid = allPayments.stream()
                .map(InvoicePayment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<SalesReturn> returns = effectiveFirmId != null
                ? salesReturnRepo.findByInvoiceIdAndFirmIdOrderByCreatedAtDesc(invoiceId, effectiveFirmId)
                : salesReturnRepo.findByInvoiceIdOrderByCreatedAtDesc(invoiceId);
        BigDecimal totalReturned = returns.stream()
                .map(SalesReturn::getTotalRefundAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal invoiceTotal = invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal effectiveReceivable = invoiceTotal.subtract(totalReturned);
        if (effectiveReceivable.compareTo(BigDecimal.ZERO) < 0) {
            effectiveReceivable = BigDecimal.ZERO;
        }

        invoice.setPaid(totalPaid.compareTo(effectiveReceivable) >= 0);
        if (Boolean.TRUE.equals(invoice.getPaid())) {
            invoice.setStatus(InvoiceStatus.PAID);
        }
        invoiceRepo.save(invoice);

        return saved;
    }

    public List<InvoicePayment> getPayments(Long invoiceId) {
        Long currentFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        return currentFirmId != null
                ? invoicePaymentRepo.findByInvoiceIdAndFirmIdOrderByPaymentDateAscIdAsc(invoiceId, currentFirmId)
                : invoicePaymentRepo.findByInvoiceIdOrderByPaymentDateAscIdAsc(invoiceId);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean deletePayment(Long paymentId) {
        Long currentFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        InvoicePayment payment = (currentFirmId != null
                ? invoicePaymentRepo.findByIdAndFirmId(paymentId, currentFirmId)
                : invoicePaymentRepo.findById(paymentId)).orElse(null);

        if (payment == null) {
            return false;
        }

        Long invoiceId = payment.getInvoiceId();
        Long effectiveFirmId = payment.getFirmId();
        invoicePaymentRepo.delete(payment);

        if (invoiceId != null) {
            Invoice invoice = (effectiveFirmId != null
                    ? invoiceRepo.findByIdAndFirmId(invoiceId, effectiveFirmId)
                    : invoiceRepo.findById(invoiceId)).orElse(null);

            if (invoice != null) {
                List<InvoicePayment> allPayments = effectiveFirmId != null
                        ? invoicePaymentRepo.findByInvoiceIdAndFirmIdOrderByPaymentDateAscIdAsc(invoiceId, effectiveFirmId)
                        : invoicePaymentRepo.findByInvoiceIdOrderByPaymentDateAscIdAsc(invoiceId);
                BigDecimal totalPaid = allPayments.stream()
                        .map(InvoicePayment::getAmount)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                List<SalesReturn> returns = effectiveFirmId != null
                        ? salesReturnRepo.findByInvoiceIdAndFirmIdOrderByCreatedAtDesc(invoiceId, effectiveFirmId)
                        : salesReturnRepo.findByInvoiceIdOrderByCreatedAtDesc(invoiceId);
                BigDecimal totalReturned = returns.stream()
                        .map(SalesReturn::getTotalRefundAmount)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal invoiceTotal = invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO;
                BigDecimal effectiveReceivable = invoiceTotal.subtract(totalReturned);
                if (effectiveReceivable.compareTo(BigDecimal.ZERO) < 0) {
                    effectiveReceivable = BigDecimal.ZERO;
                }

                boolean isFullyPaid = (totalPaid.compareTo(effectiveReceivable) >= 0
                        && effectiveReceivable.compareTo(BigDecimal.ZERO) > 0)
                        || (effectiveReceivable.compareTo(BigDecimal.ZERO) == 0
                                && invoiceTotal.compareTo(BigDecimal.ZERO) > 0);

                invoice.setPaid(isFullyPaid);
                if (isFullyPaid) {
                    invoice.setStatus(InvoiceStatus.PAID);
                } else if (invoice.getStatus() == InvoiceStatus.PAID) {
                    invoice.setStatus(InvoiceStatus.UNPAID);
                }
                invoiceRepo.save(invoice);
            }
        }

        return true;
    }

    // -------------------------
    // SALES RETURNS / CREDIT NOTES
    // -------------------------
    @Transactional(rollbackFor = Exception.class)
    public SalesReturn createSalesReturn(Long invoiceId, SalesReturnRequest request) {
        Long currentFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        Invoice invoice = (currentFirmId != null ? invoiceRepo.findByIdAndFirmId(invoiceId, currentFirmId)
                : invoiceRepo.findById(invoiceId))
                .orElseThrow(() -> new com.billing.simple.billsoft.security.TenantSecurityException(
                        "Invoice not found in firm: " + invoiceId));

        if (invoice.getStatus() == InvoiceStatus.CANCELLED || invoice.getStatus() == InvoiceStatus.DRAFT
                || invoice.getStatus() == InvoiceStatus.ESTIMATE) {
            throw new IllegalStateException("Cannot return items for cancelled, draft or estimate documents");
        }

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("At least one item must be returned");
        }

        Long firmId = invoice.getFirmId() != null
                ? invoice.getFirmId()
                : (request.getFirmId() != null ? request.getFirmId() : (currentFirmId != null ? currentFirmId : 1L));
        String returnNumber = (request.getReturnNumber() != null && !request.getReturnNumber().trim().isEmpty())
                ? request.getReturnNumber().trim()
                : generateReturnNumber(firmId);

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;
        BigDecimal excludedTaxTotal = BigDecimal.ZERO;
        BigDecimal grandTotal = BigDecimal.ZERO;

        SalesReturn salesReturn = SalesReturn.builder()
                .firmId(firmId)
                .invoice(invoice)
                .customer(invoice.getCustomer())
                .returnNumber(returnNumber)
                .returnDate(request.getReturnDate() != null ? request.getReturnDate() : LocalDate.now())
                .reason(request.getReason() != null ? request.getReason().trim() : "Customer Return")
                .refundMode(request.getRefundMode() != null ? request.getRefundMode().trim() : "CASH")
                .notes(request.getNotes())
                .items(new ArrayList<>())
                .build();

        boolean excludeTax = Boolean.TRUE.equals(request.getExcludeTax());
        BigDecimal penaltyAmount = (request.getPenaltyAmount() != null
                && request.getPenaltyAmount().compareTo(BigDecimal.ZERO) > 0)
                        ? request.getPenaltyAmount().setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
        String penaltyReason = (request.getPenaltyReason() != null && !request.getPenaltyReason().trim().isEmpty())
                ? request.getPenaltyReason().trim()
                : null;

        for (SalesReturnRequest.SalesReturnItemRequest reqItem : request.getItems()) {
            int retQty = reqItem.getReturnQty() != null ? reqItem.getReturnQty() : 0;
            if (retQty <= 0)
                continue;

            // Find matching InvoiceItem
            InvoiceItem invItem = null;
            if (reqItem.getInvoiceItemId() != null && invoice.getItems() != null) {
                invItem = invoice.getItems().stream()
                        .filter(it -> it.getId() != null && it.getId().equals(reqItem.getInvoiceItemId()))
                        .findFirst()
                        .orElse(null);
            }

            BigDecimal unitPrice = reqItem.getUnitPrice() != null ? reqItem.getUnitPrice()
                    : (invItem != null && invItem.getPricePerUnit() != null ? invItem.getPricePerUnit()
                            : BigDecimal.ZERO);

            Product prod = invItem != null ? invItem.getProduct() : null;
            if (prod == null && reqItem.getProductId() != null) {
                prod = productRepo.findByIdAndFirmId(reqItem.getProductId(), firmId).orElse(null);
            }

            BigDecimal originalGstPct = (reqItem.getGstPercent() != null
                    && reqItem.getGstPercent().compareTo(BigDecimal.ZERO) >= 0)
                            ? reqItem.getGstPercent()
                            : (invItem != null && invItem.getGstPercent() != null ? invItem.getGstPercent()
                                    : (prod != null && prod.getGstPercentage() != null ? prod.getGstPercentage()
                                            : BigDecimal.ZERO));

            BigDecimal gstPct = excludeTax ? BigDecimal.ZERO : originalGstPct;

            BigDecimal itemLineSubtotal = unitPrice.multiply(BigDecimal.valueOf(retQty)).setScale(2,
                    RoundingMode.HALF_UP);
            BigDecimal itemTax = itemLineSubtotal.multiply(gstPct).divide(BigDecimal.valueOf(100), 2,
                    RoundingMode.HALF_UP);
            BigDecimal itemTotal = itemLineSubtotal.add(itemTax);

            if (excludeTax && originalGstPct.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal itemExcludedTax = itemLineSubtotal.multiply(originalGstPct).divide(BigDecimal.valueOf(100),
                        2,
                        RoundingMode.HALF_UP);
                excludedTaxTotal = excludedTaxTotal.add(itemExcludedTax);
            }

            subtotal = subtotal.add(itemLineSubtotal);
            taxTotal = taxTotal.add(itemTax);
            grandTotal = grandTotal.add(itemTotal);

            SalesReturnItem returnItem = SalesReturnItem.builder()
                    .salesReturn(salesReturn)
                    .invoiceItem(invItem)
                    .product(prod)
                    .productName(reqItem.getProductName() != null ? reqItem.getProductName()
                            : (invItem != null && invItem.getProductName() != null ? invItem.getProductName()
                                    : "Returned Item"))
                    .hsnCode(invItem != null ? invItem.getHsnCode() : null)
                    .unit(invItem != null ? invItem.getUnit() : "pcs")
                    .returnQty(retQty)
                    .unitPrice(unitPrice)
                    .discountValue(BigDecimal.ZERO)
                    .gstPercent(originalGstPct)
                    .gstAmount(itemTax)
                    .refundTotal(itemTotal)
                    .build();

            salesReturn.getItems().add(returnItem);

            // Restore inventory to stock
            if (prod != null && prod.getId() != null) {
                productService.recordStockMovement(
                        prod.getId(),
                        firmId,
                        "SALE_RETURN",
                        BigDecimal.valueOf(retQty),
                        "SALES_RETURN",
                        returnNumber,
                        "Sales return for invoice "
                                + (invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : invoice.getId())
                                + " (" + returnItem.getProductName() + ")");
            }
        }

        if (salesReturn.getItems().isEmpty()) {
            throw new IllegalArgumentException("At least one item must have return quantity > 0");
        }

        BigDecimal netRefund = grandTotal.subtract(penaltyAmount);
        if (netRefund.compareTo(BigDecimal.ZERO) < 0) {
            netRefund = BigDecimal.ZERO;
        }

        salesReturn.setSubtotal(subtotal);
        salesReturn.setTaxAmount(taxTotal);
        salesReturn.setExcludedTaxAmount(excludedTaxTotal);
        salesReturn.setPenaltyAmount(penaltyAmount);
        salesReturn.setPenaltyReason(penaltyReason);
        salesReturn.setTotalRefundAmount(netRefund);

        SalesReturn saved = salesReturnRepo.save(salesReturn);
        if (saved.getReturnNumber() != null) {
            syncSequenceIfApplicable(saved.getFirmId(), saved.getReturnNumber(), "CN-", "LAST_RETURN_SEQ_");
        }
        return saved;
    }

    public String peekNextReturnNumber(Long firmId) {
        Long safeFirmId = firmId != null ? firmId
                : com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        if (safeFirmId == null)
            safeFirmId = 1L;
        String configKey = "LAST_RETURN_SEQ_" + safeFirmId;
        long lastSeq = appConfigRepo.findById(configKey)
                .map(AppConfig::getConfigValue)
                .map(v -> {
                    try {
                        return Long.parseLong(v);
                    } catch (Exception e) {
                        return 0L;
                    }
                }).orElse(0L);

        long nextSeq = Math.max(lastSeq + 1, salesReturnRepo.countByFirmId(safeFirmId) + 1);
        return String.format("CN-%04d", nextSeq);
    }

    public synchronized String generateReturnNumber(Long firmId) {
        Long safeFirmId = firmId != null ? firmId
                : com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        if (safeFirmId == null)
            safeFirmId = 1L;
        String nextNo = peekNextReturnNumber(safeFirmId);
        try {
            long nextSeq = Long.parseLong(nextNo.substring(3));
            String configKey = "LAST_RETURN_SEQ_" + safeFirmId;
            appConfigRepo.save(new AppConfig(configKey, String.valueOf(nextSeq)));
        } catch (Exception ignored) {
        }
        return nextNo;
    }

    public List<SalesReturn> getSalesReturnsForInvoice(Long invoiceId) {
        Long currentFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        if (currentFirmId != null) {
            return salesReturnRepo.findByInvoiceIdAndFirmIdOrderByCreatedAtDesc(invoiceId, currentFirmId);
        }
        return salesReturnRepo.findByInvoiceIdOrderByCreatedAtDesc(invoiceId);
    }

    public List<SalesReturn> getAllSalesReturns(Long firmId) {
        Long targetFirmId = firmId != null ? firmId
                : com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        if (targetFirmId == null)
            return Collections.emptyList();
        return salesReturnRepo.findByFirmIdOrderByReturnDateDescCreatedAtDesc(targetFirmId);
    }

    public com.billing.simple.billsoft.dtos.PageResponse<SalesReturn> getPaginatedSalesReturns(Long firmId,
            org.springframework.data.domain.Pageable pageable) {
        Long targetFirmId = firmId != null ? firmId
                : com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        if (targetFirmId == null)
            return com.billing.simple.billsoft.dtos.PageResponse.empty(pageable.getPageNumber(),
                    pageable.getPageSize());
        org.springframework.data.domain.Page<SalesReturn> p = salesReturnRepo
                .findByFirmIdOrderByReturnDateDescCreatedAtDesc(targetFirmId, pageable);
        return com.billing.simple.billsoft.dtos.PageResponse.of(p);
    }

    public SalesReturn getSalesReturnById(Long id) {
        Long currentFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        return (currentFirmId != null ? salesReturnRepo.findByIdAndFirmId(id, currentFirmId)
                : salesReturnRepo.findById(id)).orElse(null);
    }
}
