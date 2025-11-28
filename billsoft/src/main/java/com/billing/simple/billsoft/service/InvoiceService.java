package com.billing.simple.billsoft.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.InvoiceItem;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repo.InvoiceRepository;
import com.billing.simple.billsoft.repo.ProductRepository;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepo;
    private final CustomerRepository customerRepo;
    private final ProductRepository productRepo;

    public InvoiceService(
            InvoiceRepository invoiceRepo,
            CustomerRepository customerRepo,
            ProductRepository productRepo
    ) {
        this.invoiceRepo = invoiceRepo;
        this.customerRepo = customerRepo;
        this.productRepo = productRepo;
    }

    // ---------------------------------------------------------
    // INVOICE NUMBER
    // ---------------------------------------------------------
    public String generateInvoiceNumber() {
        Invoice last = invoiceRepo.findTopByOrderByIdDesc();
        long next = (last == null) ? 1 : last.getId() + 1;
        return String.format("INV-%04d", next);
    }

    // ---------------------------------------------------------
    // DATE PARSE
    // ---------------------------------------------------------
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

    // ---------------------------------------------------------
    // BUILD ITEM FROM REQUEST (initial mapping only)
    // - clamps discount percent to 0..100 to avoid >100 issues
    // ---------------------------------------------------------
    private InvoiceItem buildItemFromRequest(InvoiceRequestItem reqItem) {
        if (reqItem == null) return null;

        Product product = null;
        if (reqItem.getProductId() != null) {
            product = productRepo.findById(reqItem.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found: " + reqItem.getProductId()));
        }

        InvoiceItem item = new InvoiceItem();
        item.setProduct(product);

        int qty = reqItem.getQty() != null ? reqItem.getQty() : 0;
        item.setQty(qty);

        String unit = reqItem.getUnit() != null ? reqItem.getUnit()
                : (product != null ? product.getUnit() : null);
        item.setUnit(unit);

        double price = reqItem.getPricePerUnit() != null
                ? reqItem.getPricePerUnit()
                : (product != null ? product.getPrice() : 0.0);
        item.setPricePerUnit(price);

        double amountNoTax = qty * price;
        item.setAmountWithoutTax(amountNoTax);

        Double dval = reqItem.getDiscountValue();
        Double dpct = reqItem.getDiscountPercent();

        // sanitize percent discount to 0..100
        if (dpct != null) {
            dpct = Math.max(0.0, Math.min(100.0, dpct));
        }

        if (dpct != null && dpct > 0) {
            item.setDiscountType("PERCENT");
            item.setDiscountPercent(dpct);
            item.setDiscountValue(null);
        } else if (dval != null && dval > 0) {
            item.setDiscountType("VALUE");
            item.setDiscountValue(dval);
            item.setDiscountPercent(null);
        } else {
            item.setDiscountType(null);
            item.setDiscountValue(null);
            item.setDiscountPercent(null);
        }

        double discountAmt = 0.0;
        if (dpct != null && dpct > 0) {
            discountAmt = amountNoTax * dpct / 100.0;
        } else if (dval != null && dval > 0) {
            discountAmt = dval;
        }

        double taxable = Math.max(0, amountNoTax - discountAmt);
        item.setTaxableAmount(taxable);

        double gstPercent = reqItem.getGstPercent() != null
                ? reqItem.getGstPercent()
                : (product != null ? product.getGstPercentage() : 0.0);
        item.setGstPercent(gstPercent);

        // Initially compute gst and line total BEFORE invoice-level discount distribution
        double gstAmount = taxable * gstPercent / 100.0;
        item.setGstAmount(gstAmount);

        item.setLineTotal(taxable + gstAmount);

        return item;
    }

    // ---------------------------------------------------------
    // HELPER: distribute invoice-level discount across items proportionally
    // so GST is recomputed correctly (industry-standard/legal)
    // ---------------------------------------------------------
    private void applyInvoiceLevelDiscountAndRecompute(List<InvoiceItem> items, double invoiceDiscountAmount) {
        if (invoiceDiscountAmount <= 0 || items == null || items.isEmpty()) {
            // nothing to do
            return;
        }

        // Sum taxable amounts (after per-item discounts)
        double taxableSum = 0.0;
        for (InvoiceItem it : items) {
            taxableSum += (it.getTaxableAmount() != null ? it.getTaxableAmount() : 0.0);
        }
        if (taxableSum <= 0) return;

        // For each item, compute share and reduce taxable, recompute gst and lineTotal
        for (InvoiceItem it : items) {
            double taxable = it.getTaxableAmount() != null ? it.getTaxableAmount() : 0.0;
            double share = taxable / taxableSum;
            double reduction = invoiceDiscountAmount * share;
            double newTaxable = Math.max(0.0, taxable - reduction);
            it.setTaxableAmount(newTaxable);

            double gstPct = it.getGstPercent() != null ? it.getGstPercent() : 0.0;
            double newGst = newTaxable * gstPct / 100.0;
            it.setGstAmount(newGst);

            it.setLineTotal(newTaxable + newGst);
        }
    }

    // ---------------------------------------------------------
    // INTERNAL: compute invoice object from request (no persistence)
    // Returns an Invoice instance with items filled and totals computed
    // ---------------------------------------------------------
    private Invoice buildInvoiceFromRequest(InvoiceRequest request, boolean forUpdate, Invoice existingInvoiceIfAny) {
        // When forUpdate==true, existingInvoiceIfAny may provide existing metadata (not strictly necessary)
        Customer customer = null;
        if (request.getCustomerId() != null) {
            customer = customerRepo.findById(request.getCustomerId())
                    .orElseThrow(() -> new RuntimeException("Customer not found"));
        }

        Invoice invoice = new Invoice();
        if (forUpdate && existingInvoiceIfAny != null) {
            invoice.setId(existingInvoiceIfAny.getId());
            invoice.setInvoiceNumber(existingInvoiceIfAny.getInvoiceNumber());
        } else {
            invoice.setInvoiceNumber(generateInvoiceNumber());
        }

        invoice.setCustomer(customer);
        invoice.setNotes(request.getNotes());
        invoice.setInvoiceDate(LocalDateTime.now());
        invoice.setPaid(Boolean.TRUE.equals(request.getPaid()));

        List<InvoiceItem> items = new ArrayList<>();
        double rawSubtotal = 0.0;          // sum of amountWithoutTax (qty * price)
        double itemDiscountSum = 0.0;      // sum of per-item discounts
        double gstTotal = 0.0;

        // Build items (per-item discounts applied)
        if (request.getItems() != null) {
            for (InvoiceRequestItem ri : request.getItems()) {
                InvoiceItem item = buildItemFromRequest(ri);
                if (item == null) continue;
                item.setInvoice(invoice);
                items.add(item);

                rawSubtotal += (item.getAmountWithoutTax() != null ? item.getAmountWithoutTax() : 0.0);

                // compute per-item discount amount
                double idisc = 0.0;
                if (item.getDiscountPercent() != null && item.getDiscountPercent() > 0) {
                    idisc = (item.getAmountWithoutTax() != null ? item.getAmountWithoutTax() : 0.0) * item.getDiscountPercent() / 100.0;
                } else if (item.getDiscountValue() != null) {
                    idisc = item.getDiscountValue();
                }
                itemDiscountSum += idisc;

                // gstTotal currently is based on taxable BEFORE invoice-level discount;
                gstTotal += item.getGstAmount() != null ? item.getGstAmount() : 0.0;
            }
        }

        // taxable subtotal (after per-item discounts, before invoice-level discount)
        double taxableSubtotal = rawSubtotal - itemDiscountSum;
        if (taxableSubtotal < 0) taxableSubtotal = 0.0;

        // invoice-level discount (clamp percent to 0..100 if percent)
        double invoiceDisc = 0.0;
        Discount invDisc = request.getInvoiceDiscount();
        if (invDisc != null && invDisc.getValue() != null && invDisc.getValue() > 0) {
            if ("PERCENT".equalsIgnoreCase(invDisc.getType())) {
                double pct = Math.max(0.0, Math.min(100.0, invDisc.getValue()));
                invoiceDisc = (taxableSubtotal) * pct / 100.0;
            } else {
                invoiceDisc = invDisc.getValue();
            }
        }

        // Apply invoice-level discount proportionally to items (so GST recalculated correctly)
        applyInvoiceLevelDiscountAndRecompute(items, invoiceDisc);

        // recompute totals from items AFTER invoice-level discount distribution
        double finalSubtotal = 0.0; // taxable after invoice-level discount
        double finalGstTotal = 0.0;
        for (InvoiceItem it : items) {
            finalSubtotal += it.getTaxableAmount() != null ? it.getTaxableAmount() : 0.0;
            finalGstTotal += it.getGstAmount() != null ? it.getGstAmount() : 0.0;
        }

        double totalDiscount = itemDiscountSum + invoiceDisc;
        double grand = finalSubtotal + finalGstTotal;

        // Set invoice-level discount meta for storage (store provided type/value)
        if (invDisc != null) {
            invoice.setInvoiceDiscountType(invDisc.getType());
            invoice.setInvoiceDiscountValue(invDisc.getValue());
        } else {
            invoice.setInvoiceDiscountType(null);
            invoice.setInvoiceDiscountValue(null);
        }

        // Attach items and set invoice totals
        invoice.getItems().clear();
        items.forEach(it -> it.setInvoice(invoice));
        invoice.getItems().addAll(items);

        invoice.setSubtotalWithoutTax(round(finalSubtotal));
        invoice.setTotalTax(round(finalGstTotal));
        invoice.setTotalDiscount(round(totalDiscount));
        invoice.setTotalAmount(round(grand));

        return invoice;
    }

    // ---------------------------------------------------------
    // CREATE INVOICE (persists)
    // ---------------------------------------------------------
    @Transactional
    public Invoice createInvoice(InvoiceRequest request) {
        Invoice invoice = buildInvoiceFromRequest(request, false, null);
        // ensure persistence cascade will save items (entity mappings assumed correct)
        return invoiceRepo.save(invoice);
    }

    // ---------------------------------------------------------
    // PREVIEW INVOICE (does NOT persist) — NEW
    // ---------------------------------------------------------
    public Invoice previewInvoice(InvoiceRequest request) {
        // Build the invoice object with full calculation, but DO NOT save
        Invoice invoice = buildInvoiceFromRequest(request, false, null);
        // Ensure id is null so client knows it is not persisted
        invoice.setId(null);
        return invoice;
    }

    // ---------------------------------------------------------
    // GET / DELETE
    // ---------------------------------------------------------
    public List<Invoice> getAll() { return invoiceRepo.findAll(); }

    public Invoice getById(Long id) { return invoiceRepo.findById(id).orElse(null); }

    @Transactional
    public boolean delete(Long id) {
        if (!invoiceRepo.existsById(id)) return false;
        invoiceRepo.deleteById(id);
        return true;
    }

    // ---------------------------------------------------------
    // UPDATE FULL INVOICE
    // ---------------------------------------------------------
    @Transactional
    public Invoice updateFullInvoice(Long id, InvoiceUpdateRequest req) {
        Invoice invoice = invoiceRepo.findById(id).orElse(null);
        if (invoice == null) return null;

        if (req.getCustomerId() != null) {
            customerRepo.findById(req.getCustomerId())
                    .ifPresent(invoice::setCustomer);
        }

        if (req.getInvoiceDate() != null) {
            LocalDateTime dt = parseDateLenient(req.getInvoiceDate());
            if (dt != null) invoice.setInvoiceDate(dt);
        }

        if (req.getNotes() != null) invoice.setNotes(req.getNotes());
        if (req.getPaid() != null) invoice.setPaid(req.getPaid());

        List<InvoiceItem> items = new ArrayList<>();
        double rawSubtotal = 0.0;
        double itemDiscountSum = 0.0;

        if (req.getItems() != null) {
            for (InvoiceRequestItem ri : req.getItems()) {
                InvoiceItem item = buildItemFromRequest(ri);
                if (item == null) continue;
                item.setInvoice(invoice);
                items.add(item);

                rawSubtotal += (item.getAmountWithoutTax() != null ? item.getAmountWithoutTax() : 0.0);

                double idisc = 0.0;
                if (item.getDiscountPercent() != null && item.getDiscountPercent() > 0)
                    idisc = (item.getAmountWithoutTax() != null ? item.getAmountWithoutTax() : 0.0) * item.getDiscountPercent() / 100.0;
                else
                    idisc = item.getDiscountValue() != null ? item.getDiscountValue() : 0;

                itemDiscountSum += idisc;
            }
        }

        double taxableSubtotal = rawSubtotal - itemDiscountSum;
        if (taxableSubtotal < 0) taxableSubtotal = 0.0;

        double invDiscAmt = 0.0;
        Discount invDisc = req.getInvoiceDiscount();
        if (invDisc != null && invDisc.getValue() != null && invDisc.getValue() > 0) {
            if ("PERCENT".equalsIgnoreCase(invDisc.getType())) {
                double pct = Math.max(0.0, Math.min(100.0, invDisc.getValue()));
                invDiscAmt = taxableSubtotal * pct / 100.0;
            } else
                invDiscAmt = invDisc.getValue();
        }

        // apply invoice-level discount distribution and recompute gst/lines
        applyInvoiceLevelDiscountAndRecompute(items, invDiscAmt);

        double finalSubtotal = 0.0;
        double finalGstTotal = 0.0;
        for (InvoiceItem it : items) {
            finalSubtotal += it.getTaxableAmount() != null ? it.getTaxableAmount() : 0.0;
            finalGstTotal += it.getGstAmount() != null ? it.getGstAmount() : 0.0;
        }

        double totalDiscount = itemDiscountSum + invDiscAmt;
        double grand = finalSubtotal + finalGstTotal;

        // set invoice discount meta (store original type/value)
        if (invDisc != null) {
            invoice.setInvoiceDiscountType(invDisc.getType());
            invoice.setInvoiceDiscountValue(invDisc.getValue());
        } else {
            invoice.setInvoiceDiscountType(null);
            invoice.setInvoiceDiscountValue(null);
        }

        invoice.getItems().clear();
        invoice.getItems().addAll(items);

        invoice.setSubtotalWithoutTax(round(finalSubtotal));
        invoice.setTotalTax(round(finalGstTotal));
        invoice.setTotalDiscount(round(totalDiscount));
        invoice.setTotalAmount(round(grand));

        return invoiceRepo.save(invoice);
    }

    // ---------------------------------------------------------
    // PAID FLAG UPDATE
    // ---------------------------------------------------------
    @Transactional
    public Invoice updatePaidFlag(Long id, boolean paid) {
        Invoice i = invoiceRepo.findById(id).orElse(null);
        if (i == null) return null;
        i.setPaid(paid);
        return invoiceRepo.save(i);
    }

    // ---------------------------------------------------------
    // CUSTOMER-WISE ANALYTICS
    // ---------------------------------------------------------
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

        double totalBusiness = invoices.stream()
                .map(Invoice::getTotalAmount)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();

        double totalPaid = invoices.stream()
                .filter(x -> Boolean.TRUE.equals(x.getPaid()))
                .map(Invoice::getTotalAmount)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();

        List<CustomerInvoiceSummary> list = invoices.stream()
                .map(inv -> {
                    CustomerInvoiceSummary s = new CustomerInvoiceSummary();
                    s.setInvoiceId(inv.getId());
                    s.setInvoiceNumber(inv.getInvoiceNumber());
                    s.setInvoiceDate(inv.getInvoiceDate() == null ? null : inv.getInvoiceDate().toString());
                    s.setTotalAmount(inv.getTotalAmount());
                    s.setPaid(inv.getPaid());
                    return s;
                }).collect(Collectors.toList());

        resp.setTotalBusiness(totalBusiness);
        resp.setTotalPaid(totalPaid);
        resp.setTotalPending(totalBusiness - totalPaid);
        resp.setInvoiceCount((long) invoices.size());
        resp.setInvoices(list);

        return resp;
    }

    // ---------------------------------------------------------
    // SEARCH BY NAME ANALYTICS
    // ---------------------------------------------------------
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

    // ---------------------------------------------------------
    // FIRM-WIDE ANALYTICS (DTO VERSION)
    // ---------------------------------------------------------
    @Transactional(readOnly = true)
    public FirmAnalyticsResponse getFirmAnalytics() {
        List<Invoice> all = invoiceRepo.findAll();
        LocalDate today = LocalDate.now();

        double totalBusiness = 0, totalPaid = 0, totalPending = 0;
        double todayBusiness = 0, weekBusiness = 0, monthBusiness = 0, yearBusiness = 0;

        // For top customers
        Map<Long, FirmAnalyticsResponse.TopCustomer> customerMap = new HashMap<>();

        // For top products
        Map<Long, FirmAnalyticsResponse.TopProduct> productMap = new HashMap<>();

        LocalDate weekStart = today.minusDays(6);

        for (Invoice inv : all) {
            double amt = inv.getTotalAmount() != null ? inv.getTotalAmount() : 0.0;
            totalBusiness += amt;

            boolean paid = Boolean.TRUE.equals(inv.getPaid());
            if (paid) totalPaid += amt;
            else totalPending += amt;

            // Date-based breakdown
            LocalDate invDate = inv.getInvoiceDate() != null
                    ? inv.getInvoiceDate().toLocalDate()
                    : null;

            if (invDate != null) {
                if (invDate.isEqual(today)) {
                    todayBusiness += amt;
                }
                if (!invDate.isBefore(weekStart)) {
                    weekBusiness += amt;
                }
                if (invDate.getMonth() == today.getMonth()
                        && invDate.getYear() == today.getYear()) {
                    monthBusiness += amt;
                }
                if (invDate.getYear() == today.getYear()) {
                    yearBusiness += amt;
                }
            }

            // Top customers
            Customer c = inv.getCustomer();
            if (c != null) {
                FirmAnalyticsResponse.TopCustomer agg =
                        customerMap.computeIfAbsent(c.getId(), id -> {
                            FirmAnalyticsResponse.TopCustomer t = new FirmAnalyticsResponse.TopCustomer();
                            t.setCustomerId(c.getId());
                            t.setCustomerName(c.getName());
                            t.setTotalAmount(0.0);
                            t.setPendingAmount(0.0);
                            t.setInvoiceCount(0L);
                            return t;
                        });

                agg.setTotalAmount(agg.getTotalAmount() + amt);
                if (!paid) {
                    agg.setPendingAmount(agg.getPendingAmount() + amt);
                }
                agg.setInvoiceCount(agg.getInvoiceCount() + 1);
            }

            // Top products
            if (inv.getItems() != null) {
                for (InvoiceItem item : inv.getItems()) {
                    Product p = item.getProduct();
                    if (p == null) continue;

                    Long pid = p.getId();
                    if (pid == null) continue;

                    FirmAnalyticsResponse.TopProduct pAgg =
                            productMap.computeIfAbsent(pid, id -> {
                                FirmAnalyticsResponse.TopProduct t = new FirmAnalyticsResponse.TopProduct();
                                t.setProductId(p.getId());
                                t.setProductName(p.getName());
                                t.setTotalQty(0L);
                                t.setTotalAmount(0.0);
                                return t;
                            });

                    long qty = item.getQty() != null ? item.getQty() : 0;
                    double lineAmt = item.getLineTotal() != null ? item.getLineTotal() : 0.0;

                    pAgg.setTotalQty(pAgg.getTotalQty() + qty);
                    pAgg.setTotalAmount(pAgg.getTotalAmount() + lineAmt);
                }
            }
        }

        // sort topN
        List<FirmAnalyticsResponse.TopCustomer> topCustomers = new ArrayList<>(customerMap.values());
        topCustomers.sort((a, b) -> Double.compare(
                b.getTotalAmount() != null ? b.getTotalAmount() : 0.0,
                a.getTotalAmount() != null ? a.getTotalAmount() : 0.0
        ));
        if (topCustomers.size() > 5) {
            topCustomers = topCustomers.subList(0, 5);
        }

        List<FirmAnalyticsResponse.TopProduct> topProducts = new ArrayList<>(productMap.values());
        topProducts.sort((a, b) -> Long.compare(
                b.getTotalQty() != null ? b.getTotalQty() : 0L,
                a.getTotalQty() != null ? a.getTotalQty() : 0L
        ));
        if (topProducts.size() > 5) {
            topProducts = topProducts.subList(0, 5);
        }

        FirmAnalyticsResponse resp = new FirmAnalyticsResponse();
        resp.setTotalBusiness(totalBusiness);
        resp.setTotalPaid(totalPaid);
        resp.setTotalPending(totalPending);

        resp.setBusinessToday(todayBusiness);
        resp.setBusinessThisWeek(weekBusiness);
        resp.setBusinessThisMonth(monthBusiness);
        resp.setBusinessThisYear(yearBusiness);

        resp.setTopCustomers(topCustomers);
        resp.setTopProducts(topProducts);

        return resp;
    }

    // ---------------------------------------------------------
    // LEGACY: SIMPLE MAP STATS (kept in case used elsewhere)
    // ---------------------------------------------------------
    public Map<String, Double> getFirmStats() {

        List<Invoice> all = invoiceRepo.findAll();
        LocalDate today = LocalDate.now();

        double totalBusiness = 0, totalPaid = 0, totalPending = 0;
        double todayBusiness = 0, weekBusiness = 0, monthBusiness = 0, yearBusiness = 0;

        for (Invoice i : all) {
            double amt = i.getTotalAmount() == null ? 0 : i.getTotalAmount();
            totalBusiness += amt;

            if (Boolean.TRUE.equals(i.getPaid())) totalPaid += amt;
            else totalPending += amt;

            if (i.getInvoiceDate() != null) {
                LocalDate d = i.getInvoiceDate().toLocalDate();

                if (d.isEqual(today)) todayBusiness += amt;
                if (!d.isBefore(today.minusDays(6))) weekBusiness += amt;
                if (d.getMonth() == today.getMonth() && d.getYear() == today.getYear()) monthBusiness += amt;
                if (d.getYear() == today.getYear()) yearBusiness += amt;
            }
        }

        Map<String, Double> map = new HashMap<>();
        map.put("totalBusiness", totalBusiness);
        map.put("totalPaid", totalPaid);
        map.put("totalPending", totalPending);
        map.put("businessToday", todayBusiness);
        map.put("businessThisWeek", weekBusiness);
        map.put("businessThisMonth", monthBusiness);
        map.put("businessThisYear", yearBusiness);

        return map;
    }

    // ---------------------------------------------------------
    // small rounding helper (2 decimals)
    // ---------------------------------------------------------
    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
