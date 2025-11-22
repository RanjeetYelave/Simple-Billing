package com.billing.simple.billsoft.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billing.simple.billsoft.dtos.InvoiceRequest;
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

    public InvoiceService(InvoiceRepository invoiceRepo, CustomerRepository customerRepo,
            ProductRepository productRepo) {
        this.invoiceRepo = invoiceRepo;
        this.customerRepo = customerRepo;
        this.productRepo = productRepo;
    }

    public String generateInvoiceNumber() {
        Invoice last = invoiceRepo.findTopByOrderByIdDesc();
        long next = (last == null) ? 1 : last.getId() + 1;
        return String.format("INV-%04d", next);
    }

    // ---------- helpers ----------
    private LocalDateTime parseDateLenient(String s) {
        if (s == null) return null;
        try {
            return LocalDateTime.parse(s);
        } catch (DateTimeParseException ex) {
            try {
                return LocalDateTime.parse(s + ":00");
            } catch (Exception e) {
                return null;
            }
        }
    }

    private InvoiceItem buildItemFromRequest(InvoiceRequestItem reqItem) {
        Product product = null;
        if (reqItem.getProductId() != null) {
            product = productRepo.findById(reqItem.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found: " + reqItem.getProductId()));
        }

        InvoiceItem item = new InvoiceItem();

        Integer qty = reqItem.getQty() == null ? 0 : reqItem.getQty();
        double price = reqItem.getPricePerUnit() == null
                ? (product != null && product.getPrice() != null ? product.getPrice() : 0.0)
                : reqItem.getPricePerUnit();

        String unit = reqItem.getUnit() == null ? (product != null ? product.getUnit() : "") : reqItem.getUnit();

        double amountNoTax = qty * price;

        // discount
        String dType = reqItem.getDiscountType();
        double dValue = reqItem.getDiscountValue() == null ? 0.0 : reqItem.getDiscountValue();
        double dPercent = reqItem.getDiscountPercent() == null ? 0.0 : reqItem.getDiscountPercent();
        double discountAmt = dPercent > 0 ? (amountNoTax * dPercent / 100.0) : dValue;

        double taxable = Math.max(0.0, amountNoTax - discountAmt);

        double gstPct = reqItem.getGstPercent() == null
                ? (product != null && product.getGstPercentage() != null ? product.getGstPercentage() : 0.0)
                : reqItem.getGstPercent();

        double gstAmt = taxable * (gstPct / 100.0);
        double lineTotal = taxable + gstAmt;

        item.setProduct(product);
        item.setQty(qty);
        item.setUnit(unit);
        item.setPricePerUnit(price);
        item.setAmountWithoutTax(amountNoTax);
        item.setDiscountType(dType);
        item.setDiscountValue(dValue);
        item.setDiscountPercent(dPercent);
        item.setTaxableAmount(taxable);
        item.setGstPercent(gstPct);
        item.setGstAmount(gstAmt);
        item.setLineTotal(lineTotal);

        return item;
    }

    // ---------------------------
    // CREATE INVOICE
    // ---------------------------
    @Transactional
    public Invoice createInvoice(InvoiceRequest request) {

        if (request == null)
            throw new IllegalArgumentException("Request cannot be null");

        Customer customer = customerRepo.findById(request.getCustomerId())
                .orElseThrow(() -> new RuntimeException("Customer not found: " + request.getCustomerId()));

        Invoice invoice = new Invoice();
        invoice.setCustomer(customer);
        invoice.setNotes(request.getNotes());
        invoice.setInvoiceNumber(generateInvoiceNumber());
        invoice.setInvoiceDate(LocalDateTime.now());

        // NEW — handle paid flag
        invoice.setPaid(request.getPaid() != null ? request.getPaid() : false);

        List<InvoiceItem> itemEntities = new ArrayList<>();
        double subtotalWithoutTax = 0.0;
        double totalTax = 0.0;
        double totalItemDiscounts = 0.0;

        if (request.getItems() != null) {
            for (InvoiceRequestItem ri : request.getItems()) {
                InvoiceItem it = buildItemFromRequest(ri);
                it.setInvoice(invoice);

                subtotalWithoutTax += it.getAmountWithoutTax();
                totalTax += it.getGstAmount();

                double itemDiscount = it.getDiscountPercent() > 0
                        ? (it.getAmountWithoutTax() * it.getDiscountPercent() / 100.0)
                        : it.getDiscountValue();
                totalItemDiscounts += itemDiscount;

                itemEntities.add(it);
            }
        }

        double invoiceDiscountAmount = 0.0;
        if (request.getInvoiceDiscount() != null) {
            String type = request.getInvoiceDiscount().getType();
            Double value = request.getInvoiceDiscount().getValue() == null ? 0.0
                    : request.getInvoiceDiscount().getValue();
            if ("PERCENT".equalsIgnoreCase(type)) {
                invoiceDiscountAmount = (subtotalWithoutTax - totalItemDiscounts) * (value / 100.0);
            } else {
                invoiceDiscountAmount = value;
            }
            invoice.setInvoiceDiscountType(type);
            invoice.setInvoiceDiscountValue(value);
        }

        double totalDiscount = totalItemDiscounts + invoiceDiscountAmount;
        double grandTotal = (subtotalWithoutTax - totalDiscount) + totalTax;

        invoice.getItems().clear();
        for (InvoiceItem it : itemEntities) {
            it.setInvoice(invoice);
            invoice.getItems().add(it);
        }

        invoice.setSubtotalWithoutTax(subtotalWithoutTax);
        invoice.setTotalTax(totalTax);
        invoice.setTotalDiscount(totalDiscount);
        invoice.setTotalAmount(grandTotal);

        return invoiceRepo.save(invoice);
    }

    // ---------------------------
    // GET / DELETE
    // ---------------------------
    public List<Invoice> getAll() {
        return invoiceRepo.findAll();
    }

    public Invoice getById(Long id) {
        return invoiceRepo.findById(id).orElse(null);
    }

    public boolean delete(Long id) {
        if (!invoiceRepo.existsById(id))
            return false;
        invoiceRepo.deleteById(id);
        return true;
    }

    // ---------------------------
    // UPDATE FULLY — new items replace old
    // ---------------------------
    @Transactional
    public Invoice updateFullInvoice(Long id, InvoiceUpdateRequest req) {

        Invoice invoice = invoiceRepo.findById(id).orElse(null);
        if (invoice == null)
            return null;

        if (req.getCustomerId() != null) {
            Customer c = customerRepo.findById(req.getCustomerId()).orElse(null);
            if (c != null)
                invoice.setCustomer(c);
        }

        if (req.getInvoiceDate() != null) {
            LocalDateTime dt = parseDateLenient(req.getInvoiceDate());
            if (dt != null)
                invoice.setInvoiceDate(dt);
        }

        if (req.getNotes() != null)
            invoice.setNotes(req.getNotes());

        // NEW — update "paid" flag
        if (req.getPaid() != null)
            invoice.setPaid(req.getPaid());

        // Build new items
        List<InvoiceItem> newItems = new ArrayList<>();
        double subtotalWithoutTax = 0.0;
        double totalTax = 0.0;
        double totalItemDiscounts = 0.0;

        if (req.getItems() != null) {
            for (InvoiceRequestItem ri : req.getItems()) {

                Product prod = null;
                if (ri.getProductId() != null) {
                    prod = productRepo.findById(ri.getProductId())
                            .orElseThrow(() -> new RuntimeException("Product not found: " + ri.getProductId()));
                }

                InvoiceItem it = buildItemFromRequest(ri);
                it.setInvoice(invoice);
                it.setProduct(prod);

                subtotalWithoutTax += it.getAmountWithoutTax();
                totalTax += it.getGstAmount();

                double itemDiscount = it.getDiscountPercent() > 0
                        ? (it.getAmountWithoutTax() * it.getDiscountPercent() / 100.0)
                        : it.getDiscountValue();
                totalItemDiscounts += itemDiscount;

                newItems.add(it);
            }
        }

        double invoiceDiscountAmount = 0.0;
        if (req.getInvoiceDiscount() != null) {
            String type = req.getInvoiceDiscount().getType();
            Double value = req.getInvoiceDiscount().getValue() == null ? 0.0
                    : req.getInvoiceDiscount().getValue();
            if ("PERCENT".equalsIgnoreCase(type)) {
                invoiceDiscountAmount = (subtotalWithoutTax - totalItemDiscounts) * (value / 100.0);
            } else {
                invoiceDiscountAmount = value;
            }
            invoice.setInvoiceDiscountType(type);
            invoice.setInvoiceDiscountValue(value);
        } else {
            invoice.setInvoiceDiscountType(null);
            invoice.setInvoiceDiscountValue(null);
        }

        double totalDiscount = totalItemDiscounts + invoiceDiscountAmount;
        double grandTotal = (subtotalWithoutTax - totalDiscount) + totalTax;

        invoice.getItems().clear();
        for (InvoiceItem it : newItems) {
            it.setInvoice(invoice);
            invoice.getItems().add(it);
        }

        invoice.setSubtotalWithoutTax(subtotalWithoutTax);
        invoice.setTotalTax(totalTax);
        invoice.setTotalDiscount(totalDiscount);
        invoice.setTotalAmount(grandTotal);

        return invoiceRepo.save(invoice);
    }
    
 // ------------------------------------------------------------
 // ANALYTICS
 // ------------------------------------------------------------
 public Object getCustomerAnalytics(Long customerId) {

     List<Invoice> invoices = invoiceRepo.findAll()
             .stream()
             .filter(inv -> inv.getCustomer() != null && inv.getCustomer().getId().equals(customerId))
             .toList();

     double totalBusiness = invoices.stream()
             .mapToDouble(inv -> inv.getTotalAmount() == null ? 0 : inv.getTotalAmount())
             .sum();

     double totalPaid = invoices.stream()
             .filter(Invoice::getPaid)
             .mapToDouble(inv -> inv.getTotalAmount() == null ? 0 : inv.getTotalAmount())
             .sum();

     double totalPending = totalBusiness - totalPaid;

     return new java.util.HashMap<>() {{
         put("customerId", customerId);
         put("totalBusiness", totalBusiness);
         put("totalPaid", totalPaid);
         put("totalPending", totalPending);
         put("invoices", invoices);
     }};
 }

 public Object getCustomerAnalyticsByName(String name) {

     // find all invoices where customer name partially matches
     List<Invoice> invoices = invoiceRepo.findAll()
             .stream()
             .filter(inv -> inv.getCustomer() != null &&
                     inv.getCustomer().getName().toLowerCase().contains(name.toLowerCase()))
             .toList();

     if (invoices.isEmpty()) {
         return new java.util.HashMap<>() {{
             put("message", "No customer found with name: " + name);
         }};
     }

     Long customerId = invoices.get(0).getCustomer().getId();

     return getCustomerAnalytics(customerId);
 }

}
