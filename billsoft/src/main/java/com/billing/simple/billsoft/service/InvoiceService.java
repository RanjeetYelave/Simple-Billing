package com.billing.simple.billsoft.service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        // invoice-level discount
        double invoiceDisc = 0.0;
        Discount invDisc = request.getInvoiceDiscount();
        if (invDisc != null && invDisc.getValue() != null && invDisc.getValue() > 0) {
            if ("PERCENT".equalsIgnoreCase(invDisc.getType())) {
                invoiceDisc = (taxableSubtotal) * invDisc.getValue() / 100.0;
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

        // Set invoice-level discount meta for storage
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
            if ("PERCENT".equalsIgnoreCase(invDisc.getType()))
                invDiscAmt = taxableSubtotal * invDisc.getValue() / 100.0;
            else
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

        // set invoice discount meta
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
    // small rounding helper (2 decimals)
    // ---------------------------------------------------------
    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    // -------------------------------
    // The rest of analytics / firm methods unchanged
    // (getCustomerAnalytics, getFirmAnalytics, etc.)
    // -------------------------------
}
