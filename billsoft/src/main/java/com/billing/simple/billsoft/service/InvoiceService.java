package com.billing.simple.billsoft.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

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
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.InvoiceItem;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.entities.InvoiceStatus;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repo.InvoiceRepository;
import com.billing.simple.billsoft.repo.ProductRepository;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepo;
    private final CustomerRepository customerRepo;
    private final ProductRepository productRepo;
    private final InvoiceCalculationEngine engine;

    // scales
    private static final int SCALE = 2;
    private static final int CALC_SCALE = 10;

    // zero convenience
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);

    public InvoiceService(
            InvoiceRepository invoiceRepo,
            CustomerRepository customerRepo,
            ProductRepository productRepo
    ) {
        this.invoiceRepo = invoiceRepo;
        this.customerRepo = customerRepo;
        this.productRepo = productRepo;
        this.engine = new InvoiceCalculationEngine();
    }

    // -------------------------
    // Number generators
    // -------------------------
    public String generateInvoiceNumber() {
        Invoice last = invoiceRepo.findTopByOrderByIdDesc();
        long next = (last == null) ? 1 : last.getId() + 1;
        return String.format("INV-%04d", next);
    }

    public String generateEstimateNumber() {
        List<Invoice> estimates = invoiceRepo.findAllByStatusOrderByInvoiceDateAsc(InvoiceStatus.ESTIMATE);
        int next = (estimates == null) ? 1 : estimates.size() + 1;
        return String.format("EST-%04d", next);
    }

    // -------------------------
    // Date parser (lenient)
    // -------------------------
    private LocalDateTime parseDateLenient(String s) {
        if (s == null) return null;
        try {
            return LocalDateTime.parse(s);
        } catch (Exception e1) {
            try {
                return LocalDateTime.parse(s + ":00");
            } catch (Exception e2) {
                return null;
            }
        }
    }

    // -------------------------
    // Helpers
    // -------------------------
    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO.setScale(SCALE) : v.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal pctOf(BigDecimal base, BigDecimal percent) {
        if (base == null || percent == null) return BigDecimal.ZERO.setScale(SCALE);
        return base.multiply(percent).divide(BigDecimal.valueOf(100), CALC_SCALE, RoundingMode.HALF_UP)
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    private List<Product> fetchProductsReferencedBy(InvoiceRequest req, InvoiceUpdateRequest uReq) {
        Set<Long> ids = new HashSet<>();
        if (req != null && req.getItems() != null) {
            req.getItems().stream().map(InvoiceRequestItem::getProductId).filter(Objects::nonNull).forEach(ids::add);
        }
        if (uReq != null && uReq.getItems() != null) {
            uReq.getItems().stream().map(InvoiceRequestItem::getProductId).filter(Objects::nonNull).forEach(ids::add);
        }
        if (ids.isEmpty()) return Collections.emptyList();
        Iterable<Product> found = productRepo.findAllById(ids);
        List<Product> list = new ArrayList<>();
        found.forEach(list::add);
        return list;
    }

    // -------------------------
    // CREATE (persist)
    // -------------------------
    @Transactional
    public Invoice createInvoice(InvoiceRequest request) {
        if (request.getItems() == null) request.setItems(Collections.emptyList());

        Customer customer = null;
        if (request.getCustomerId() != null) {
            customer = customerRepo.findById(request.getCustomerId())
                    .orElseThrow(() -> new RuntimeException("Customer not found: " + request.getCustomerId()));
        }

        Invoice invoice = new Invoice();

        // identifiers
        if (request.getStatus() != null && request.getStatus() == InvoiceStatus.ESTIMATE) {
            invoice.setEstimateNumber(request.getEstimateNumber() != null ? request.getEstimateNumber() : generateEstimateNumber());
        } else {
            invoice.setInvoiceNumber(generateInvoiceNumber());
        }

        // fetch product data once
        List<Product> products = fetchProductsReferencedBy(request, null);

        // delegate calculations to engine (isUpdateMode=false)
        Invoice calculated = engine.calculate(invoice, customer, products, request, false);

        // persist and return
        Invoice saved = invoiceRepo.save(calculated);
        return saved;
    }

    // convenience: create estimate
    @Transactional
    public Invoice createEstimate(InvoiceRequest request) {
        request.setStatus(InvoiceStatus.ESTIMATE);
        if (request.getEstimateNumber() == null) request.setEstimateNumber(generateEstimateNumber());
        return createInvoice(request);
    }

    // -------------------------
    // PREVIEW (no persist)
    // -------------------------
    public Invoice previewInvoice(InvoiceRequest request) {
        if (request.getItems() == null) request.setItems(Collections.emptyList());

        Customer customer = null;
        if (request.getCustomerId() != null) {
            customer = customerRepo.findById(request.getCustomerId()).orElse(null);
        }

        Invoice invoice = new Invoice();
        if (request.getStatus() != null && request.getStatus() == InvoiceStatus.ESTIMATE) {
            invoice.setEstimateNumber(request.getEstimateNumber() != null ? request.getEstimateNumber() : generateEstimateNumber());
        } else {
            invoice.setInvoiceNumber(generateInvoiceNumber());
        }

        List<Product> products = fetchProductsReferencedBy(request, null);
        Invoice calc = engine.calculate(invoice, customer, products, request, false);
        calc.setId(null);
        return calc;
    }

    // -------------------------
    // GET / LIST / DELETE
    // -------------------------
    public List<Invoice> getAll() {
        List<Invoice> all = invoiceRepo.findAll();
        all.forEach(this::normalizeStatus);
        return all;
    }

    public Invoice getById(Long id) {
        Invoice inv = invoiceRepo.findById(id).orElse(null);
        if (inv == null) return null;
        normalizeStatus(inv);
        return inv;
    }

    @Transactional
    public boolean delete(Long id) {
        if (!invoiceRepo.existsById(id)) return false;
        invoiceRepo.deleteById(id);
        return true;
    }

    // -------------------------
    // UPDATE FULL
    // -------------------------
    @Transactional
    public Invoice updateFullInvoice(Long id, InvoiceUpdateRequest req) {
        Invoice existing = invoiceRepo.findById(id).orElse(null);
        if (existing == null) return null;

        // If update request omits items -> preserve existing items by creating an InvoiceRequest from existing invoice
        if (req.getItems() == null) {
            InvoiceRequest preserveReq = buildInvoiceRequestFromExistingInvoice(existing);

            // copy overrides from req
            if (req.getCustomerId() != null) preserveReq.setCustomerId(req.getCustomerId());
            if (req.getNotes() != null) preserveReq.setCustomerNote(req.getNotes());
            if (req.getInvoiceDiscount() != null) preserveReq.setInvoiceDiscount(req.getInvoiceDiscount());
            if (req.getPaid() != null) preserveReq.setPaid(req.getPaid());
            if (req.getStatus() != null) preserveReq.setStatus(req.getStatus());
            if (req.getEstimateNumber() != null) preserveReq.setEstimateNumber(req.getEstimateNumber());
            if (req.getDueDate() != null) preserveReq.setDueDate(req.getDueDate());
            if (req.getCustomerNote() != null) preserveReq.setCustomerNote(req.getCustomerNote());
            if (req.getTermsAndConditions() != null) preserveReq.setTermsAndConditions(req.getTermsAndConditions());
            if (req.getPaymentMethod() != null) preserveReq.setPaymentMethod(req.getPaymentMethod());
            if (req.getCurrency() != null) preserveReq.setCurrency(req.getCurrency());
            if (req.getTags() != null) preserveReq.setTags(req.getTags());
            if (req.getRoundOff() != null) preserveReq.setRoundOff(req.getRoundOff());

            // parse provided invoiceDate (if any)
            if (req.getInvoiceDate() != null) {
                LocalDateTime dt = parseDateLenient(req.getInvoiceDate());
                if (dt != null) existing.setInvoiceDate(dt);
            }

            List<Product> products = fetchProductsReferencedBy(preserveReq, null);

            // engine runs in update mode and mutates the existing invoice object
            Customer cust = preserveReq.getCustomerId() == null ? existing.getCustomer()
                    : customerRepo.findById(preserveReq.getCustomerId()).orElse(existing.getCustomer());

            Invoice calculated = engine.calculate(existing, cust, products, preserveReq, true);
            return invoiceRepo.save(calculated);
        }

        // If req.items present -> overwrite items
        InvoiceRequest newReq = new InvoiceRequest();
        newReq.setCustomerId(req.getCustomerId() != null ? req.getCustomerId() : (existing.getCustomer() != null ? existing.getCustomer().getId() : null));
        newReq.setCustomerNote(req.getCustomerNote() != null ? req.getCustomerNote() : (req.getNotes() != null ? req.getNotes() : existing.getCustomerNote()));
        newReq.setTermsAndConditions(req.getTermsAndConditions() != null ? req.getTermsAndConditions() : existing.getTermsAndConditions());
        newReq.setPaymentMethod(req.getPaymentMethod() != null ? req.getPaymentMethod() : existing.getPaymentMethod());
        newReq.setCurrency(req.getCurrency() != null ? req.getCurrency() : existing.getCurrency());
        newReq.setTags(req.getTags() != null ? req.getTags() : existing.getTags());
        newReq.setStatus(req.getStatus() != null ? req.getStatus() : existing.getStatus());
        newReq.setEstimateNumber(req.getEstimateNumber() != null ? req.getEstimateNumber() : existing.getEstimateNumber());
        newReq.setDueDate(req.getDueDate() != null ? req.getDueDate() : existing.getDueDate());
        newReq.setPaid(req.getPaid() != null ? req.getPaid() : existing.getPaid());
        newReq.setInvoiceDiscount(req.getInvoiceDiscount() != null ? req.getInvoiceDiscount() : null);
        newReq.setRoundOff(req.getRoundOff() != null ? req.getRoundOff() : null);
        newReq.setItems(req.getItems());

        // parse invoiceDate if provided
        if (req.getInvoiceDate() != null) {
            LocalDateTime dt = parseDateLenient(req.getInvoiceDate());
            if (dt != null) existing.setInvoiceDate(dt);
        }

        Customer cust = null;
        if (newReq.getCustomerId() != null) {
            cust = customerRepo.findById(newReq.getCustomerId()).orElse(null);
        } else {
            cust = existing.getCustomer();
        }

        List<Product> products = fetchProductsReferencedBy(null, req);

        Invoice calculated = engine.calculate(existing, cust, products, newReq, true);
        return invoiceRepo.save(calculated);
    }

    private InvoiceRequest buildInvoiceRequestFromExistingInvoice(Invoice existing) {
        InvoiceRequest r = new InvoiceRequest();
        if (existing.getCustomer() != null) r.setCustomerId(existing.getCustomer().getId());
        r.setCustomerNote(existing.getCustomerNote());
        r.setTermsAndConditions(existing.getTermsAndConditions());
        r.setPaymentMethod(existing.getPaymentMethod());
        r.setCurrency(existing.getCurrency());
        r.setTags(existing.getTags());
        r.setStatus(existing.getStatus());
        r.setEstimateNumber(existing.getEstimateNumber());
        r.setDueDate(existing.getDueDate());
        r.setPaid(existing.getPaid());
        r.setRoundOff(existing.getRoundOff());

        if (existing.getItems() != null) {
            List<InvoiceRequestItem> items = existing.getItems().stream().map(it -> {
                InvoiceRequestItem ri = new InvoiceRequestItem();
                ri.setProductId(it.getProduct() != null ? it.getProduct().getId() : null);
                ri.setQty(it.getQty());
                ri.setUnit(it.getUnit());
                ri.setPricePerUnit(it.getPricePerUnit());
                ri.setDiscountPercent(it.getDiscountPercent());
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
    @Transactional
    public Invoice updatePaidFlag(Long id, boolean paid) {
        Invoice i = invoiceRepo.findById(id).orElse(null);
        if (i == null) return null;
        i.setPaid(paid);
        if (paid) i.setStatus(InvoiceStatus.PAID);
        return invoiceRepo.save(i);
    }

    // -------------------------
    // Convert estimate -> invoice
    // -------------------------
    @Transactional
    public Invoice convertEstimateToInvoice(Long estimateId, InvoiceRequest overrideRequest) {
        Invoice estimate = invoiceRepo.findById(estimateId).orElse(null);
        if (estimate == null) throw new RuntimeException("Estimate not found: " + estimateId);
        if (estimate.getStatus() != InvoiceStatus.ESTIMATE && estimate.getStatus() != InvoiceStatus.DRAFT)
            throw new RuntimeException("Only estimates/drafts can be converted");

        Invoice source = estimate;
        Invoice newInv = new Invoice();

        // copy simple metadata
        newInv.setCustomer(source.getCustomer());
        newInv.setCustomerNote(source.getCustomerNote());
        newInv.setTermsAndConditions(source.getTermsAndConditions());
        newInv.setPaymentMethod(source.getPaymentMethod());
        newInv.setCurrency(source.getCurrency());
        newInv.setTags(source.getTags());

        newInv.setInvoiceNumber(generateInvoiceNumber());
        newInv.setStatus(InvoiceStatus.FINAL);
        newInv.setInvoiceDate(LocalDateTime.now());
        newInv.setDueDate(source.getDueDate() != null ? source.getDueDate() : (source.getInvoiceDate() != null ? source.getInvoiceDate().toLocalDate().plusDays(14) : null));
        newInv.setPaid(false);

        if (overrideRequest != null) {
            if (overrideRequest.getCustomerId() == null && source.getCustomer() != null)
                overrideRequest.setCustomerId(source.getCustomer().getId());

            List<Product> products = fetchProductsReferencedBy(overrideRequest, null);
            Customer cust = overrideRequest.getCustomerId() != null ? customerRepo.findById(overrideRequest.getCustomerId()).orElse(source.getCustomer()) : source.getCustomer();
            Invoice calculated = engine.calculate(newInv, cust, products, overrideRequest, false);
            Invoice saved = invoiceRepo.save(calculated);
            source.setConvertedInvoiceId(saved.getId());
            invoiceRepo.save(source);
            return saved;
        } else {
            // clone items as-is
            List<InvoiceItem> cloned = new ArrayList<>();
            if (source.getItems() != null) {
                for (InvoiceItem it : source.getItems()) {
                    InvoiceItem ni = new InvoiceItem();
                    ni.setProduct(it.getProduct());
                    ni.setQty(it.getQty());
                    ni.setUnit(it.getUnit());
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

            // copy totals
            newInv.setSubtotalWithoutTax(source.getSubtotalWithoutTax());
            newInv.setTotalTax(source.getTotalTax());
            newInv.setTotalDiscount(source.getTotalDiscount());
            newInv.setRoundOff(source.getRoundOff());
            newInv.setTotalAmount(source.getTotalAmount() != null ? source.getTotalAmount()
                    : (source.getSubtotalWithoutTax() != null ? source.getSubtotalWithoutTax().add(source.getTotalTax() == null ? BigDecimal.ZERO : source.getTotalTax()) : null));

            Invoice saved = invoiceRepo.save(newInv);
            source.setConvertedInvoiceId(saved.getId());
            invoiceRepo.save(source);
            return saved;
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

        for (Invoice inv : invoices) {
            totalBusiness = totalBusiness.add(nz(inv.getTotalAmount()));
            if (Boolean.TRUE.equals(inv.getPaid())) totalPaid = totalPaid.add(nz(inv.getTotalAmount()));
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
        resp.setTotalPending(totalBusiness.subtract(totalPaid).doubleValue());
        resp.setInvoiceCount((long) invoices.size());
        resp.setInvoices(list);

        return resp;
    }

    public List<CustomerAnalyticsResponse> getCustomerAnalyticsByName(String namePart) {
        if (namePart == null || namePart.trim().isEmpty()) return Collections.emptyList();

        List<Invoice> invoices = invoiceRepo.findByCustomer_NameContainingIgnoreCase(namePart);
        if (invoices == null || invoices.isEmpty()) return Collections.emptyList();

        Set<Long> ids = invoices.stream()
                .map(Invoice::getCustomer)
                .filter(Objects::nonNull)
                .map(Customer::getId)
                .collect(Collectors.toSet());

        List<CustomerAnalyticsResponse> list = new ArrayList<>();
        for (Long id : ids) list.add(getCustomerAnalytics(id));

        return list;
    }

    @Transactional(readOnly = true)
    public FirmAnalyticsResponse getFirmAnalytics() {
        List<Invoice> all = invoiceRepo.findAll();
        LocalDate today = LocalDate.now();

        BigDecimal totalBusiness = BigDecimal.ZERO.setScale(SCALE);
        BigDecimal totalPaid = BigDecimal.ZERO.setScale(SCALE);
        BigDecimal totalPending = BigDecimal.ZERO.setScale(SCALE);

        BigDecimal todayBusiness = BigDecimal.ZERO.setScale(SCALE);
        BigDecimal weekBusiness = BigDecimal.ZERO.setScale(SCALE);
        BigDecimal monthBusiness = BigDecimal.ZERO.setScale(SCALE);
        BigDecimal yearBusiness = BigDecimal.ZERO.setScale(SCALE);

        Map<Long, FirmAnalyticsResponse.TopCustomer> customerMap = new HashMap<>();
        Map<Long, FirmAnalyticsResponse.TopProduct> productMap = new HashMap<>();

        LocalDate weekStart = today.minusDays(6);

        for (Invoice inv : all) {
            normalizeStatus(inv);

            BigDecimal amt = nz(inv.getTotalAmount());
            totalBusiness = totalBusiness.add(amt);

            boolean paid = Boolean.TRUE.equals(inv.getPaid());
            if (paid) totalPaid = totalPaid.add(amt);
            else totalPending = totalPending.add(amt);

            LocalDate invDate = inv.getInvoiceDate() != null ? inv.getInvoiceDate().toLocalDate() : null;

            if (invDate != null) {
                if (invDate.isEqual(today)) todayBusiness = todayBusiness.add(amt);
                if (!invDate.isBefore(weekStart)) weekBusiness = weekBusiness.add(amt);
                if (invDate.getMonth() == today.getMonth() && invDate.getYear() == today.getYear()) monthBusiness = monthBusiness.add(amt);
                if (invDate.getYear() == today.getYear()) yearBusiness = yearBusiness.add(amt);
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

                agg.setTotalAmount(agg.getTotalAmount() + amt.doubleValue());
                if (!paid) agg.setPendingAmount(agg.getPendingAmount() + amt.doubleValue());
                agg.setInvoiceCount(agg.getInvoiceCount() + 1);
            }

            if (inv.getItems() != null) {
                for (InvoiceItem item : inv.getItems()) {
                    Product p = item.getProduct();
                    if (p == null) continue;
                    Long pid = p.getId();
                    if (pid == null) continue;

                    FirmAnalyticsResponse.TopProduct pAgg = productMap.computeIfAbsent(pid, id -> {
                        FirmAnalyticsResponse.TopProduct t = new FirmAnalyticsResponse.TopProduct();
                        t.setProductId(p.getId());
                        t.setProductName(p.getName());
                        t.setTotalQty(0L);
                        t.setTotalAmount(0.0);
                        return t;
                    });

                    long qty = item.getQty() != null ? item.getQty() : 0;
                    double lineAmt = item.getLineTotal() != null ? item.getLineTotal().doubleValue() : 0.0;

                    pAgg.setTotalQty(pAgg.getTotalQty() + qty);
                    pAgg.setTotalAmount(pAgg.getTotalAmount() + lineAmt);
                }
            }
        }

        List<FirmAnalyticsResponse.TopCustomer> topCustomers = new ArrayList<>(customerMap.values());
        topCustomers.sort((a, b) -> Double.compare(
                b.getTotalAmount() != null ? b.getTotalAmount() : 0.0,
                a.getTotalAmount() != null ? a.getTotalAmount() : 0.0
        ));
        if (topCustomers.size() > 5) topCustomers = topCustomers.subList(0, 5);

        List<FirmAnalyticsResponse.TopProduct> topProducts = new ArrayList<>(productMap.values());
        topProducts.sort((a, b) -> Long.compare(
                b.getTotalQty() != null ? b.getTotalQty() : 0L,
                a.getTotalQty() != null ? a.getTotalQty() : 0L
        ));
        if (topProducts.size() > 5) topProducts = topProducts.subList(0, 5);

        FirmAnalyticsResponse resp = new FirmAnalyticsResponse();
        resp.setTotalBusiness(totalBusiness.doubleValue());
        resp.setTotalPaid(totalPaid.doubleValue());
        resp.setTotalPending(totalPending.doubleValue());

        resp.setBusinessToday(todayBusiness.doubleValue());
        resp.setBusinessThisWeek(weekBusiness.doubleValue());
        resp.setBusinessThisMonth(monthBusiness.doubleValue());
        resp.setBusinessThisYear(yearBusiness.doubleValue());

        resp.setTopCustomers(topCustomers);
        resp.setTopProducts(topProducts);

        return resp;
    }

    public Map<String, Double> getFirmStats() {
        List<Invoice> all = invoiceRepo.findAll();
        LocalDate today = LocalDate.now();

        BigDecimal totalBusiness = BigDecimal.ZERO.setScale(SCALE);
        BigDecimal totalPaid = BigDecimal.ZERO.setScale(SCALE);
        BigDecimal totalPending = BigDecimal.ZERO.setScale(SCALE);

        BigDecimal todayBusiness = BigDecimal.ZERO.setScale(SCALE);
        BigDecimal weekBusiness = BigDecimal.ZERO.setScale(SCALE);
        BigDecimal monthBusiness = BigDecimal.ZERO.setScale(SCALE);
        BigDecimal yearBusiness = BigDecimal.ZERO.setScale(SCALE);

        for (Invoice i : all) {
            BigDecimal amt = nz(i.getTotalAmount());
            totalBusiness = totalBusiness.add(amt);
            if (Boolean.TRUE.equals(i.getPaid())) totalPaid = totalPaid.add(amt);
            else totalPending = totalPending.add(amt);

            if (i.getInvoiceDate() != null) {
                LocalDate d = i.getInvoiceDate().toLocalDate();
                if (d.isEqual(today)) todayBusiness = todayBusiness.add(amt);
                if (!d.isBefore(today.minusDays(6))) weekBusiness = weekBusiness.add(amt);
                if (d.getMonth() == today.getMonth() && d.getYear() == today.getYear()) monthBusiness = monthBusiness.add(amt);
                if (d.getYear() == today.getYear()) yearBusiness = yearBusiness.add(amt);
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
    // Normalize status helper
    // -------------------------
    private void normalizeStatus(Invoice inv) {
        if (inv == null) return;

        if (Boolean.TRUE.equals(inv.getPaid())) {
            inv.setStatus(InvoiceStatus.PAID);
            return;
        }

        if (inv.getDueDate() != null && !Boolean.TRUE.equals(inv.getPaid())) {
            if (inv.getDueDate().isBefore(LocalDate.now())) {
                inv.setStatus(InvoiceStatus.OVERDUE);
                return;
            }
        }

        if (inv.getStatus() == null) inv.setStatus(InvoiceStatus.FINAL);
    }

    // -------------------------
    // Convenience lists
    // -------------------------
    public List<Invoice> getAllEstimates() {
        List<Invoice> list = invoiceRepo.findAllByStatusOrderByInvoiceDateAsc(InvoiceStatus.ESTIMATE);
        list.forEach(this::normalizeStatus);
        return list;
    }

    public List<Invoice> getAllFinalInvoices() {
        List<Invoice> list = invoiceRepo.findAllByStatusOrderByInvoiceDateAsc(InvoiceStatus.FINAL);
        list.forEach(this::normalizeStatus);
        return list;
    }
}
