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
import com.billing.simple.billsoft.entities.InvoiceStatus;
import com.billing.simple.billsoft.entities.Product;
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
    public String generateInvoiceNumber(Long firmId) {
        long count = invoiceRepo.countByFirmId(firmId);
        return String.format("INV-%04d", count + 1);
    }

    public String generateEstimateNumber(Long firmId) {
        long count = invoiceRepo.countByFirmIdAndStatus(firmId, InvoiceStatus.ESTIMATE);
        return String.format("EST-%04d", count + 1);
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
        if (request.getItems() == null) {
            request.setItems(Collections.emptyList());
        }

        // Basic validation: invoice-level discount
        validateInvoiceDiscount(request.getInvoiceDiscount());

        // Load customer if provided
        Customer customer = null;
        if (request.getCustomerId() != null) {
            customer = customerRepo.findById(request.getCustomerId())
                    .orElseThrow(() -> new RuntimeException("Customer not found: " + request.getCustomerId()));
        }

        // Determine effective status
        InvoiceStatus status = request.getStatus();
        if (status == null) {
            status = InvoiceStatus.FINAL;  // default
            request.setStatus(status);
        }

        Invoice invoice = new Invoice();
        invoice.setFirmId(request.getFirmId());

        // identifiers (Option B behaviour)
        if (status == InvoiceStatus.ESTIMATE) {
            // Estimate: ONLY estimateNumber, NO invoiceNumber
            if (request.getEstimateNumber() != null && !request.getEstimateNumber().isBlank()) {
                invoice.setEstimateNumber(request.getEstimateNumber());
            } else {
                invoice.setEstimateNumber(generateEstimateNumber(request.getFirmId()));
                request.setEstimateNumber(invoice.getEstimateNumber()); // keep dto consistent
            }
            invoice.setInvoiceNumber(null);
        } else {
            // Final/invoice: MUST have invoiceNumber, estimateNumber optional
            String invNo = request.getInvoiceNumber();
            if (invNo == null || invNo.isBlank()) {
                invNo = generateInvoiceNumber(request.getFirmId());
                request.setInvoiceNumber(invNo);
            }
            invoice.setInvoiceNumber(invNo);

            // if caller accidentally sent estimateNumber also, keep it but it's not required
            if (request.getEstimateNumber() != null && !request.getEstimateNumber().isBlank()) {
                invoice.setEstimateNumber(request.getEstimateNumber());
            }
        }

        // fetch product data once
        List<Product> products = fetchProductsReferencedBy(request, null);

        // delegate calculations to engine (isUpdateMode=false)
        Invoice calculated = engine.calculate(invoice, customer, products, request, false);

        // persist and return
        return invoiceRepo.save(calculated);
    }

    // convenience: create estimate
    @Transactional
    public Invoice createEstimate(InvoiceRequest request) {
        request.setStatus(InvoiceStatus.ESTIMATE);
        if (request.getEstimateNumber() == null || request.getEstimateNumber().isBlank()) {
            request.setEstimateNumber(generateEstimateNumber(request.getFirmId()));
        }
        // IMPORTANT: do NOT set invoiceNumber here; createInvoice() will treat ESTIMATE correctly
        return createInvoice(request);
    }

    // -------------------------
    // PREVIEW (no persist)
    // -------------------------
    public Invoice previewInvoice(InvoiceRequest request) {
        if (request.getItems() == null) request.setItems(Collections.emptyList());

        // Validation for invoice-level discount
        validateInvoiceDiscount(request.getInvoiceDiscount());

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
                estNo = generateEstimateNumber(firmId);
                request.setEstimateNumber(estNo);
            }
            invoice.setEstimateNumber(estNo);
            invoice.setInvoiceNumber(null);
        } else {
            String invNo = request.getInvoiceNumber();
            if (invNo == null || invNo.isBlank()) {
                invNo = generateInvoiceNumber(firmId);
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
        List<Invoice> all;
        if (firmId != null) {
            all = invoiceRepo.findByFirmIdAndCustomerNameContainingIgnoreCase(firmId, "");
        } else {
            all = invoiceRepo.findAll();
        }
        all.forEach(this::normalizeStatus);
        return all;
    }

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

        // Validate invoice-level discount on update request
        if (req.getInvoiceDiscount() != null) {
            validateInvoiceDiscount(req.getInvoiceDiscount());
        }

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
        newReq.setCustomerId(
                req.getCustomerId() != null
                        ? req.getCustomerId()
                        : (existing.getCustomer() != null ? existing.getCustomer().getId() : null)
        );
        newReq.setCustomerNote(
                req.getCustomerNote() != null
                        ? req.getCustomerNote()
                        : (req.getNotes() != null ? req.getNotes() : existing.getCustomerNote())
        );
        newReq.setTermsAndConditions(
                req.getTermsAndConditions() != null ? req.getTermsAndConditions() : existing.getTermsAndConditions()
        );
        newReq.setPaymentMethod(
                req.getPaymentMethod() != null ? req.getPaymentMethod() : existing.getPaymentMethod()
        );
        newReq.setCurrency(
                req.getCurrency() != null ? req.getCurrency() : existing.getCurrency()
        );
        newReq.setTags(
                req.getTags() != null ? req.getTags() : existing.getTags()
        );
        newReq.setStatus(
                req.getStatus() != null ? req.getStatus() : existing.getStatus()
        );
        newReq.setEstimateNumber(
                req.getEstimateNumber() != null ? req.getEstimateNumber() : existing.getEstimateNumber()
        );
        newReq.setDueDate(
                req.getDueDate() != null ? req.getDueDate() : existing.getDueDate()
        );
        newReq.setPaid(
                req.getPaid() != null ? req.getPaid() : existing.getPaid()
        );

        // Preserve existing invoice-level discount if not overridden
        if (req.getInvoiceDiscount() != null) {
            newReq.setInvoiceDiscount(req.getInvoiceDiscount());
        } else if (existing.getInvoiceDiscountType() != null && existing.getInvoiceDiscountValue() != null) {
            Discount d = new Discount();
            d.setType(existing.getInvoiceDiscountType());
            d.setValue(existing.getInvoiceDiscountValue());
            newReq.setInvoiceDiscount(d);
        }

        newReq.setRoundOff(
                req.getRoundOff() != null ? req.getRoundOff() : existing.getRoundOff()
        );
        newReq.setItems(req.getItems());

        // parse invoiceDate if provided
        if (req.getInvoiceDate() != null) {
            LocalDateTime dt = parseDateLenient(req.getInvoiceDate());
            if (dt != null) existing.setInvoiceDate(dt);
        }

        Customer cust;
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

        // Rebuild invoice-level discount from entity fields, if present
        if (existing.getInvoiceDiscountType() != null && existing.getInvoiceDiscountValue() != null) {
            Discount d = new Discount();
            d.setType(existing.getInvoiceDiscountType());
            d.setValue(existing.getInvoiceDiscountValue());
            r.setInvoiceDiscount(d);
        }

        if (existing.getItems() != null) {
            List<InvoiceRequestItem> items = existing.getItems().stream().map(it -> {
                InvoiceRequestItem ri = new InvoiceRequestItem();
                ri.setProductId(it.getProduct() != null ? it.getProduct().getId() : null);
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
    @Transactional
    public Invoice updatePaidFlag(Long id, boolean paid) {
        Invoice i = invoiceRepo.findById(id).orElse(null);
        if (i == null) return null;
        i.setPaid(paid);
        if (paid) i.setStatus(InvoiceStatus.PAID);
        return invoiceRepo.save(i);
    }

    @Transactional
    public Invoice convertEstimateToInvoice(Long estimateId, InvoiceRequest overrideRequest) {
        Invoice estimate = invoiceRepo.findById(estimateId).orElse(null);
        if (estimate == null)
            throw new RuntimeException("Estimate not found: " + estimateId);

        if (estimate.getStatus() != InvoiceStatus.ESTIMATE && estimate.getStatus() != InvoiceStatus.DRAFT)
            throw new RuntimeException("Only estimates/drafts can be converted");

        // Validate invoice-level discount on override request (if present)
        if (overrideRequest != null) {
            validateInvoiceDiscount(overrideRequest.getInvoiceDiscount());
        }

        // Base clone of estimate → new invoice
        Invoice newInv = new Invoice();
        newInv.setCustomer(estimate.getCustomer());
        newInv.setCustomerNote(estimate.getCustomerNote());
        newInv.setTermsAndConditions(estimate.getTermsAndConditions());
        newInv.setPaymentMethod(estimate.getPaymentMethod());
        newInv.setCurrency(estimate.getCurrency());
        newInv.setTags(estimate.getTags());

        newInv.setFirmId(estimate.getFirmId());
        newInv.setInvoiceNumber(generateInvoiceNumber(estimate.getFirmId()));
        newInv.setStatus(InvoiceStatus.FINAL);
        newInv.setInvoiceDate(LocalDateTime.now());
        newInv.setPaid(false);

        if (estimate.getDueDate() != null) {
            newInv.setDueDate(estimate.getDueDate());
        } else if (estimate.getInvoiceDate() != null) {
            newInv.setDueDate(estimate.getInvoiceDate().toLocalDate().plusDays(14));
        }

        // Case 1: overrideRequest has items -> use override completely
        if (overrideRequest != null && overrideRequest.getItems() != null && !overrideRequest.getItems().isEmpty()) {
            overrideRequest.setStatus(InvoiceStatus.FINAL);
            overrideRequest.setInvoiceNumber(newInv.getInvoiceNumber());
            overrideRequest.setEstimateNumber(null);

            List<Product> products = fetchProductsReferencedBy(overrideRequest, null);
            Invoice calculated = engine.calculate(newInv, newInv.getCustomer(), products, overrideRequest, false);

            Invoice saved = invoiceRepo.save(calculated);
            estimate.setConvertedInvoiceId(saved.getId());
            invoiceRepo.save(estimate);

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

        // Build a request from the (now overridden) newInv
        InvoiceRequest req = buildInvoiceRequestFromExistingInvoice(newInv);
        req.setStatus(InvoiceStatus.FINAL);
        req.setInvoiceNumber(newInv.getInvoiceNumber());
        req.setEstimateNumber(null);

        List<Product> products = fetchProductsReferencedBy(req, null);
        Invoice calculated = engine.calculate(newInv, newInv.getCustomer(), products, req, true);

        Invoice saved = invoiceRepo.save(calculated);
        estimate.setConvertedInvoiceId(saved.getId());
        invoiceRepo.save(estimate);

        return saved;
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
        return getFirmAnalytics(null);
    }

    @Transactional(readOnly = true)
    public FirmAnalyticsResponse getFirmAnalytics(Long firmId) {
        List<Invoice> all;
        if (firmId != null) {
            all = invoiceRepo.findAllByFirmIdAndStatusIn(firmId, List.of(InvoiceStatus.FINAL, InvoiceStatus.PAID, InvoiceStatus.OVERDUE, InvoiceStatus.SENT));
            // Also include estimates that may have become invoices
            List<Invoice> estimates = invoiceRepo.findAllByFirmIdAndStatusOrderByInvoiceDateAsc(firmId, InvoiceStatus.ESTIMATE);
            if (estimates != null) all.addAll(estimates);
        } else {
            all = invoiceRepo.findAll();
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
                // NOTE: this will also mark ESTIMATE as OVERDUE if they have a dueDate in past.
                // If you want to avoid that, guard here with:
                // if (inv.getStatus() == InvoiceStatus.FINAL || inv.getStatus() == InvoiceStatus.DRAFT) { ... }
                inv.setStatus(InvoiceStatus.OVERDUE);
                return;
            }
        }

        if (inv.getStatus() == null) inv.setStatus(InvoiceStatus.FINAL);
    }

    // -------------------------
    // Convenience lists
    // -------------------------
    public List<Invoice> getAllEstimates(Long firmId) {
        List<Invoice> list;
        if (firmId != null) {
            list = invoiceRepo.findAllByFirmIdAndStatusOrderByInvoiceDateAsc(firmId, InvoiceStatus.ESTIMATE);
        } else {
            list = invoiceRepo.findAllByStatusOrderByInvoiceDateAsc(InvoiceStatus.ESTIMATE);
        }
        list.forEach(this::normalizeStatus);
        return list;
    }

    public List<Invoice> getAllFinalInvoices(Long firmId) {
        List<Invoice> list;
        if (firmId != null) {
            list = invoiceRepo.findAllByFirmIdAndStatusOrderByInvoiceDateAsc(firmId, InvoiceStatus.FINAL);
        } else {
            list = invoiceRepo.findAllByStatusOrderByInvoiceDateAsc(InvoiceStatus.FINAL);
        }
        list.forEach(this::normalizeStatus);
        return list;
    }

    // -------------------------
    // Validation helpers
    // -------------------------
    private void validateInvoiceDiscount(Discount d) {
        if (d == null) return;
        if (d.getType() == null || d.getValue() == null) return;

        String type = d.getType().trim().toUpperCase();
        if (!"PERCENT".equals(type) && !"VALUE".equals(type)) {
            throw new IllegalArgumentException("Invalid invoiceDiscount.type: " + d.getType());
        }

        BigDecimal value = d.getValue();
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("invoiceDiscount.value cannot be negative");
        }
    }
}
