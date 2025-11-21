package com.billing.simple.billsoft.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
            // try adding seconds if missing
            try {
                return LocalDateTime.parse(s + ":00");
            } catch (Exception e) {
                return null;
            }
        }
    }

    /**
     * Calculate values for a single item based on request and product fallback.
     * Returns a populated InvoiceItem (not yet persisted).
     */
    private InvoiceItem buildItemFromRequest(InvoiceRequestItem reqItem) {
        // Resolve product if present
        Product product = null;
        if (reqItem.getProductId() != null) {
            Optional<Product> pOpt = productRepo.findById(reqItem.getProductId());
            if (pOpt.isPresent()) product = pOpt.get();
        }

        InvoiceItem item = new InvoiceItem();

        Integer qty = reqItem.getQty() == null ? 0 : reqItem.getQty();
        double price = reqItem.getPricePerUnit() == null
                ? (product != null && product.getPrice() != null ? product.getPrice() : 0.0)
                : reqItem.getPricePerUnit();

        String unit = reqItem.getUnit() == null ? (product != null ? product.getUnit() : "") : reqItem.getUnit();

        // amount without tax
        double amountNoTax = qty * price;

        // discount
        String dType = reqItem.getDiscountType();
        double dValue = reqItem.getDiscountValue() == null ? 0.0 : reqItem.getDiscountValue();
        double dPercent = reqItem.getDiscountPercent() == null ? 0.0 : reqItem.getDiscountPercent();
        double discountAmt = 0.0;
        if (dPercent > 0) discountAmt = (amountNoTax * dPercent) / 100.0;
        else discountAmt = dValue;

        // taxable amount
        double taxable = Math.max(0.0, amountNoTax - discountAmt);

        // gst percent
        double gstPct = reqItem.getGstPercent() == null
                ? (product != null && product.getGstPercentage() != null ? product.getGstPercentage() : 0.0)
                : reqItem.getGstPercent();

        double gstAmt = taxable * (gstPct / 100.0);

        double lineTotal = taxable + gstAmt;

        // populate
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
    // CREATE (UI sends minimal fields; server calculates)
    // ---------------------------
    @Transactional
    public Invoice createInvoice(InvoiceRequest request) {

        if (request == null) throw new IllegalArgumentException("Request cannot be null");

        Customer customer = customerRepo.findById(request.getCustomerId())
                .orElseThrow(() -> new RuntimeException("Customer not found: " + request.getCustomerId()));

        Invoice invoice = new Invoice();
        invoice.setCustomer(customer);
        invoice.setNotes(request.getNotes());
        invoice.setInvoiceNumber(generateInvoiceNumber());
        invoice.setInvoiceDate(LocalDateTime.now());

        // process items
        List<InvoiceItem> itemEntities = new ArrayList<>();
        double subtotalWithoutTax = 0.0;
        double totalTax = 0.0;
        double totalItemDiscounts = 0.0;

        if (request.getItems() != null) {
            for (InvoiceRequestItem ri : request.getItems()) {
                // Build item (calculations inside)
                InvoiceItem it = buildItemFromRequest(ri);
                // ensure invoice link (will set after invoice saved or before saving)
                it.setInvoice(invoice);

                // accumulate
                subtotalWithoutTax += (it.getAmountWithoutTax() == null ? 0.0 : it.getAmountWithoutTax());
                totalTax += (it.getGstAmount() == null ? 0.0 : it.getGstAmount());

                double itemDiscount = (it.getDiscountPercent() != null && it.getDiscountPercent() > 0)
                        ? (it.getAmountWithoutTax() * it.getDiscountPercent() / 100.0)
                        : (it.getDiscountValue() == null ? 0.0 : it.getDiscountValue());
                totalItemDiscounts += itemDiscount;

                itemEntities.add(it);
            }
        }

        // invoice-level discount (if any)
        double invoiceDiscountAmount = 0.0;
        if (request.getInvoiceDiscount() != null) {
            String type = request.getInvoiceDiscount().getType();
            Double value = request.getInvoiceDiscount().getValue() == null ? 0.0 : request.getInvoiceDiscount().getValue();
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

        // grand totals
        double totalDiscount = totalItemDiscounts + invoiceDiscountAmount;
        double grandTotal = (subtotalWithoutTax - totalItemDiscounts - invoiceDiscountAmount) + totalTax;

        // attach items to invoice (ensure bidirectional)
        invoice.getItems().clear();
        for (InvoiceItem it : itemEntities) {
            it.setInvoice(invoice);
            invoice.getItems().add(it);
        }

        // set totals
        invoice.setSubtotalWithoutTax(subtotalWithoutTax);
        invoice.setTotalTax(totalTax);
        invoice.setTotalDiscount(totalDiscount);
        invoice.setTotalAmount(grandTotal);

        // save invoice (Cascade.ALL will save items)
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
        if (!invoiceRepo.existsById(id)) return false;
        invoiceRepo.deleteById(id);
        return true;
    }

    // ---------------------------
    // UPDATE: Replace entire items list with new list sent by UI
    // ---------------------------
    @Transactional
    public Invoice updateFullInvoice(Long id, InvoiceUpdateRequest req) {
        Invoice invoice = invoiceRepo.findById(id).orElse(null);
        if (invoice == null) return null;

        if (req.getCustomerId() != null) {
            Customer c = customerRepo.findById(req.getCustomerId()).orElse(null);
            if (c != null) invoice.setCustomer(c);
        }

        if (req.getInvoiceDate() != null) {
            LocalDateTime dt = parseDateLenient(req.getInvoiceDate());
            if (dt != null) invoice.setInvoiceDate(dt);
        }

        if (req.getNotes() != null) invoice.setNotes(req.getNotes());

        // Build new items list (server calculates)
        List<InvoiceItem> newItems = new ArrayList<>();
        double subtotalWithoutTax = 0.0;
        double totalTax = 0.0;
        double totalItemDiscounts = 0.0;

        if (req.getItems() != null) {
            for (InvoiceRequestItem ri : req.getItems()) {
                // if product doesn't exist, skip or throw. We'll throw to surface problem.
                Product prod = null;
                if (ri.getProductId() != null) {
                    prod = productRepo.findById(ri.getProductId()).orElseThrow(
                            () -> new RuntimeException("Product not found: " + ri.getProductId()));
                }

                InvoiceItem it = buildItemFromRequest(ri);
                it.setInvoice(invoice);
                it.setProduct(prod);

                subtotalWithoutTax += (it.getAmountWithoutTax() == null ? 0.0 : it.getAmountWithoutTax());
                totalTax += (it.getGstAmount() == null ? 0.0 : it.getGstAmount());

                double itemDiscount = (it.getDiscountPercent() != null && it.getDiscountPercent() > 0)
                        ? (it.getAmountWithoutTax() * it.getDiscountPercent() / 100.0)
                        : (it.getDiscountValue() == null ? 0.0 : it.getDiscountValue());
                totalItemDiscounts += itemDiscount;

                newItems.add(it);
            }
        }

        // invoice-level discount handling
        double invoiceDiscountAmount = 0.0;
        if (req.getInvoiceDiscount() != null) {
            String type = req.getInvoiceDiscount().getType();
            Double value = req.getInvoiceDiscount().getValue() == null ? 0.0 : req.getInvoiceDiscount().getValue();
            if ("PERCENT".equalsIgnoreCase(type)) {
                invoiceDiscountAmount = (subtotalWithoutTax - totalItemDiscounts) * (value / 100.0);
            } else {
                invoiceDiscountAmount = value;
            }
            invoice.setInvoiceDiscountType(req.getInvoiceDiscount().getType());
            invoice.setInvoiceDiscountValue(req.getInvoiceDiscount().getValue());
        } else {
            invoice.setInvoiceDiscountType(null);
            invoice.setInvoiceDiscountValue(null);
        }

        double totalDiscount = totalItemDiscounts + invoiceDiscountAmount;
        double grandTotal = (subtotalWithoutTax - totalItemDiscounts - invoiceDiscountAmount) + totalTax;

        // Replace existing items: clear and add new ones (orphanRemoval=true will delete old)
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
}
