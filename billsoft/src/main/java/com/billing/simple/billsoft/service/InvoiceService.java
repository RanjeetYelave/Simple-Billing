package com.billing.simple.billsoft.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billing.simple.billsoft.dtos.CustomerAnalyticsResponse;
import com.billing.simple.billsoft.dtos.CustomerInvoiceSummary;
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
    // BUILD ITEM FROM REQUEST
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

        if (dpct != null && dpct > 0) {
            item.setDiscountType("PERCENT");
            item.setDiscountPercent(dpct);
        } else if (dval != null && dval > 0) {
            item.setDiscountType("VALUE");
            item.setDiscountValue(dval);
        } else {
            item.setDiscountType(null);
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

        double gstAmount = taxable * gstPercent / 100.0;
        item.setGstAmount(gstAmount);

        item.setLineTotal(taxable + gstAmount);

        return item;
    }

    // ---------------------------------------------------------
    // CREATE INVOICE
    // ---------------------------------------------------------
    @Transactional
    public Invoice createInvoice(InvoiceRequest request) {

        Customer customer = customerRepo.findById(request.getCustomerId())
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        Invoice invoice = new Invoice();
        invoice.setCustomer(customer);
        invoice.setNotes(request.getNotes());
        invoice.setInvoiceNumber(generateInvoiceNumber());
        invoice.setInvoiceDate(LocalDateTime.now());
        invoice.setPaid(Boolean.TRUE.equals(request.getPaid()));

        List<InvoiceItem> items = new ArrayList<>();
        double subtotal = 0, gstTotal = 0, itemDiscountSum = 0;

        for (InvoiceRequestItem ri : request.getItems()) {
            InvoiceItem item = buildItemFromRequest(ri);
            item.setInvoice(invoice);
            items.add(item);

            subtotal += item.getAmountWithoutTax();
            gstTotal += item.getGstAmount();

            double idisc = 0;
            if (item.getDiscountPercent() != null && item.getDiscountPercent() > 0) {
                idisc = item.getAmountWithoutTax() * item.getDiscountPercent() / 100.0;
            } else if (item.getDiscountValue() != null) {
                idisc = item.getDiscountValue();
            }
            itemDiscountSum += idisc;
        }

        double invoiceDisc = 0.0;
        Discount invDisc = request.getInvoiceDiscount();
        if (invDisc != null && invDisc.getValue() != null && invDisc.getValue() > 0) {
            if ("PERCENT".equalsIgnoreCase(invDisc.getType())) {
                invoiceDisc = (subtotal - itemDiscountSum) * invDisc.getValue() / 100.0;
            } else {
                invoiceDisc = invDisc.getValue();
            }
        }

        double grand = subtotal - itemDiscountSum - invoiceDisc + gstTotal;

        invoice.getItems().clear();
        items.forEach(it -> it.setInvoice(invoice));
        invoice.getItems().addAll(items);

        invoice.setTotalAmount(grand);

        return invoiceRepo.save(invoice);
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
        double subtotal = 0, gstTotal = 0, discTotal = 0;

        for (InvoiceRequestItem ri : req.getItems()) {
            InvoiceItem item = buildItemFromRequest(ri);
            item.setInvoice(invoice);
            items.add(item);

            subtotal += item.getAmountWithoutTax();
            gstTotal += item.getGstAmount();

            double idisc;
            if (item.getDiscountPercent() != null && item.getDiscountPercent() > 0)
                idisc = item.getAmountWithoutTax() * item.getDiscountPercent() / 100.0;
            else
                idisc = item.getDiscountValue() != null ? item.getDiscountValue() : 0;

            discTotal += idisc;
        }

        double invDiscAmt = 0;
        Discount invDisc = req.getInvoiceDiscount();
        if (invDisc != null && invDisc.getValue() != null) {
            if ("PERCENT".equalsIgnoreCase(invDisc.getType()))
                invDiscAmt = (subtotal - discTotal) * invDisc.getValue() / 100.0;
            else
                invDiscAmt = invDisc.getValue();
        }

        double grand = subtotal - discTotal - invDiscAmt + gstTotal;

        invoice.getItems().clear();
        invoice.getItems().addAll(items);

        invoice.setTotalAmount(grand);

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
    // FIRM-WIDE ANALYTICS (NEW)
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
}
