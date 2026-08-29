package com.billing.simple.billsoft.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import com.billing.simple.billsoft.dtos.InvoiceRequest;
import com.billing.simple.billsoft.dtos.InvoiceRequest.Discount;
import com.billing.simple.billsoft.dtos.InvoiceRequestItem;
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.InvoiceItem;
import com.billing.simple.billsoft.entities.InvoiceStatus;
import com.billing.simple.billsoft.entities.Product;

/**
 * Single source of truth for ALL invoice calculations.
 */
public class InvoiceCalculationEngine {

    private static final int SCALE = 2;
    private static final int CALC_SCALE = 10;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);
    private static final DateTimeFormatter[] DATE_PARSERS = {
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ISO_LOCAL_DATE_TIME
    };

    /* ======================================================================
       PUBLIC ENTRY
       ====================================================================== */
    public Invoice calculate(
            Invoice invoice,
            Customer customer,
            List<Product> productList,
            InvoiceRequest request,
            boolean isUpdateMode
    ) {
        invoice.setCustomer(customer);

        /* ---------------------------
           METADATA
        ---------------------------- */
        invoice.setCustomerNote(request.getCustomerNote());
        invoice.setTermsAndConditions(request.getTermsAndConditions());
        invoice.setPaymentMethod(request.getPaymentMethod());
        if (request.getCurrency() != null) invoice.setCurrency(request.getCurrency());
        invoice.setTags(request.getTags());

        InvoiceStatus st = request.getStatus() != null ? request.getStatus() : InvoiceStatus.FINAL;
        invoice.setStatus(st);

        if (request.getDueDate() != null) {
            invoice.setDueDate(request.getDueDate());
        } else if (st == InvoiceStatus.FINAL || st == InvoiceStatus.DRAFT) {
            invoice.setDueDate(LocalDate.now().plusDays(14));
        }

        // Parse invoice date from request string if provided
        if (request.getInvoiceDate() != null && !request.getInvoiceDate().isBlank()) {
            LocalDateTime parsedDate = parseDateString(request.getInvoiceDate());
            if (parsedDate != null) {
                invoice.setInvoiceDate(parsedDate);
            }
        }

        // Fallback: set invoice date to now if still null
        if (invoice.getInvoiceDate() == null) {
            invoice.setInvoiceDate(LocalDateTime.now());
        }
        
        invoice.setPaid(Boolean.TRUE.equals(request.getPaid()));

        if (request.getFirmId() != null) {
            invoice.setFirmId(request.getFirmId());
        }

        /* ======================================================================
           ⚠️ METADATA-ONLY UPDATE CASE
           If request.items is NULL -> keep existing items & totals
        ====================================================================== */
        if (request.getItems() == null && invoice.getItems() != null && !invoice.getItems().isEmpty()) {
            return invoice;
        }

        /* ---------------------------
           REBUILD ITEMS ALWAYS
        ---------------------------- */
        invoice.getItems().clear();
        List<InvoiceItem> items = new ArrayList<>();

        if (request.getItems() != null) {
            for (InvoiceRequestItem ri : request.getItems()) {
                Product product = findProduct(ri.getProductId(), productList);
                InvoiceItem item = buildItem(ri, product);
                if (item != null) {
                    item.setInvoice(invoice);
                    items.add(item);
                }
            }
        }

        /* ---------------------------
           BASE SUBTOTAL + ITEM DISCOUNT
           - rawSubtotal = sum of amountWithoutTax (pre-discount)
           - itemDiscountSum = sum of item-level discountValue
        ---------------------------- */
        BigDecimal rawSubtotal = ZERO;
        BigDecimal itemDiscountSum = ZERO;

        for (InvoiceItem it : items) {
            BigDecimal amountWithoutTax = nz(it.getAmountWithoutTax());
            rawSubtotal = rawSubtotal.add(amountWithoutTax);

            // Amount-only item discount
            BigDecimal itemDisc = nz(it.getDiscountValue());
            if (itemDisc.compareTo(amountWithoutTax) > 0) itemDisc = amountWithoutTax;
            if (itemDisc.compareTo(ZERO) < 0) itemDisc = ZERO;

            itemDiscountSum = itemDiscountSum.add(itemDisc);

            // Taxable AFTER item-level discount
            BigDecimal taxableAfterItemDisc = amountWithoutTax.subtract(itemDisc);
            if (taxableAfterItemDisc.compareTo(BigDecimal.ZERO) < 0) {
                taxableAfterItemDisc = ZERO;
            }
            it.setTaxableAmount(taxableAfterItemDisc);
        }

        /* ---------------------------
           INVOICE-LEVEL DISCOUNT
           - compute on sum of taxableAfterItemDisc
           - distribute proportionally per item
        ---------------------------- */
        BigDecimal taxableBeforeInvDisc = ZERO;
        for (InvoiceItem it : items) {
            taxableBeforeInvDisc = taxableBeforeInvDisc.add(nz(it.getTaxableAmount()));
        }
        if (taxableBeforeInvDisc.compareTo(BigDecimal.ZERO) < 0) {
            taxableBeforeInvDisc = ZERO;
        }

        BigDecimal invoiceDiscountAmt = calculateInvoiceLevelDiscount(taxableBeforeInvDisc, request.getInvoiceDiscount());

        // Distribute invoice-level discount across items (pro-rata)
        if (invoiceDiscountAmt.compareTo(ZERO) > 0 && taxableBeforeInvDisc.compareTo(ZERO) > 0) {
            BigDecimal remaining = invoiceDiscountAmt;

            for (int idx = 0; idx < items.size(); idx++) {
                InvoiceItem it = items.get(idx);
                BigDecimal itemTaxable = nz(it.getTaxableAmount());

                if (idx == items.size() - 1) {
                    // Last item gets whatever discount remains to fix rounding diffs
                    BigDecimal share = remaining;
                    BigDecimal newTaxable = itemTaxable.subtract(share);
                    if (newTaxable.compareTo(ZERO) < 0) newTaxable = ZERO;
                    it.setTaxableAmount(newTaxable);
                    it.setDiscountValue(nz(it.getDiscountValue()).add(share));
                    remaining = ZERO;
                } else {
                    BigDecimal share = itemTaxable
                            .multiply(invoiceDiscountAmt)
                            .divide(taxableBeforeInvDisc, SCALE, RoundingMode.HALF_UP);
                    if (share.compareTo(remaining) > 0) {
                        share = remaining;
                    }

                    BigDecimal newTaxable = itemTaxable.subtract(share);
                    if (newTaxable.compareTo(ZERO) < 0) newTaxable = ZERO;
                    it.setTaxableAmount(newTaxable);
                    it.setDiscountValue(nz(it.getDiscountValue()).add(share));

                    remaining = remaining.subtract(share);
                }
            }
        }

        /* ---------------------------
           FINAL TOTALS
           - GST calculated AFTER all discounts
           - gstPercent falls back to product GST if null
        ---------------------------- */
        BigDecimal finalTaxable = ZERO;
        BigDecimal gstTotal = ZERO;

        for (InvoiceItem it : items) {
            BigDecimal taxable = nz(it.getTaxableAmount());

            BigDecimal gstPercent = nz(it.getGstPercent());
            if (gstPercent.compareTo(ZERO) == 0 && it.getProduct() != null && it.getProduct().getGstPercentage() != null) {
                gstPercent = nz(it.getProduct().getGstPercentage());
                it.setGstPercent(gstPercent);
            }

            BigDecimal gstAmt = pctOf(taxable, gstPercent);
            it.setGstAmount(gstAmt);

            BigDecimal lineTotal = taxable.add(gstAmt).setScale(SCALE, RoundingMode.HALF_UP);
            it.setLineTotal(lineTotal);

            finalTaxable = finalTaxable.add(taxable);
            gstTotal = gstTotal.add(gstAmt);
        }

        BigDecimal grand = finalTaxable.add(gstTotal);
        BigDecimal totalDiscount = itemDiscountSum.add(invoiceDiscountAmt);

        if (Boolean.TRUE.equals(request.getRoundOff())) {
            BigDecimal rounded = grand.setScale(0, RoundingMode.HALF_UP);
            invoice.setRoundOff(rounded.subtract(grand).setScale(SCALE, RoundingMode.HALF_UP));
            grand = rounded;
        } else {
            invoice.setRoundOff(ZERO);
        }

        /* ---------------------------
           WRITE TOTALS BACK
           NOTE: subtotalWithoutTax = RAW base total (requested!)
        ---------------------------- */
        invoice.setSubtotalWithoutTax(rawSubtotal);       // BEFORE any discounts
        invoice.setTotalDiscount(totalDiscount);
        invoice.setTotalTax(gstTotal);
        invoice.setTotalAmount(grand);

        if (request.getInvoiceDiscount() != null) {
            invoice.setInvoiceDiscountType(request.getInvoiceDiscount().getType());
            invoice.setInvoiceDiscountValue(request.getInvoiceDiscount().getValue());
        } else {
            invoice.setInvoiceDiscountType(null);
            invoice.setInvoiceDiscountValue(null);
        }

        invoice.getItems().addAll(items);
        items.forEach(i -> i.setInvoice(invoice));

        return invoice;
    }

    /* ======================================================================
       ITEM BUILDER
       ====================================================================== */
    private InvoiceItem buildItem(InvoiceRequestItem req, Product product) {
        InvoiceItem item = new InvoiceItem();

        item.setProduct(product);
        int qty = req.getQty() != null ? req.getQty() : 0;
        item.setQty(qty);

        item.setUnit(req.getUnit() != null ? req.getUnit()
                : (product != null ? product.getUnit() : null));

        item.setHsnCode(req.getHsnCode() != null && !req.getHsnCode().isBlank() ? req.getHsnCode()
                : (product != null ? product.getHsnCode() : null));

        BigDecimal price = req.getPricePerUnit() != null
                ? req.getPricePerUnit()
                : (product != null ? nz(product.getPrice()) : ZERO);

        price = price.setScale(SCALE, RoundingMode.HALF_UP);
        item.setPricePerUnit(price);

        BigDecimal amountNoTax = price.multiply(BigDecimal.valueOf(qty))
                .setScale(SCALE, RoundingMode.HALF_UP);
        item.setAmountWithoutTax(amountNoTax);

        // -------------------------
        // ITEM DISCOUNT — VALUE ONLY
        // -------------------------
        BigDecimal dval = req.getDiscountValue();
        BigDecimal discAmt = dval != null ? dval.setScale(SCALE, RoundingMode.HALF_UP) : ZERO;

        if (discAmt.compareTo(amountNoTax) > 0) discAmt = amountNoTax;
        if (discAmt.compareTo(ZERO) < 0) discAmt = ZERO;

        item.setDiscountType("VALUE");
        item.setDiscountPercent(null);
        item.setDiscountValue(discAmt);

        // -------------------------
        // TAXABLE AMOUNT & GST
        // -------------------------
        BigDecimal taxable = amountNoTax.subtract(discAmt);
        if (taxable.compareTo(BigDecimal.ZERO) < 0) taxable = ZERO;
        item.setTaxableAmount(taxable);

        BigDecimal gstPct = req.getGstPercent() != null
                ? req.getGstPercent()
                : (product != null ? nz(product.getGstPercentage()) : ZERO);

        gstPct = gstPct.setScale(SCALE, RoundingMode.HALF_UP);
        item.setGstPercent(gstPct);

        BigDecimal gstAmt = pctOf(taxable, gstPct);
        item.setGstAmount(gstAmt);

        item.setLineTotal(taxable.add(gstAmt).setScale(SCALE, RoundingMode.HALF_UP));

        return item;
    }

    /* ======================================================================
       HELPERS
       ====================================================================== */
    private BigDecimal nz(BigDecimal v) {
        return v == null ? ZERO : v.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal pctOf(BigDecimal base, BigDecimal pct) {
        if (base == null || pct == null) return ZERO;
        return base.multiply(pct)
                .divide(BigDecimal.valueOf(100), CALC_SCALE, RoundingMode.HALF_UP)
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    private Product findProduct(Long id, List<Product> products) {
        if (id == null) return null;
        return products.stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    private BigDecimal calculateInvoiceLevelDiscount(BigDecimal taxable, Discount d) {
        if (d == null || d.getValue() == null) return ZERO;
        if ("PERCENT".equalsIgnoreCase(d.getType())) {
            return pctOf(taxable, d.getValue());
        } else {
            return d.getValue().setScale(SCALE, RoundingMode.HALF_UP);
        }
    }

    /**
     * Parse a date string in multiple supported formats.
     */
    private LocalDateTime parseDateString(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        String trimmed = dateStr.trim();
        for (DateTimeFormatter fmt : DATE_PARSERS) {
            try {
                return LocalDateTime.parse(trimmed, fmt);
            } catch (DateTimeParseException e) {
                // Try next format
            }
        }
        // Try parsing as just a date (yyyy-MM-dd) -> start of day
        try {
            LocalDate date = LocalDate.parse(trimmed);
            return date.atStartOfDay();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Old helper – currently unused but kept for reference.
     * Distribute invoice-level discount proportionally to each invoice item
     * BEFORE GST — tax must be applied on reduced value.
     */
    @SuppressWarnings("unused")
    private void applyInvoiceLevelDiscount(List<InvoiceItem> items, BigDecimal invoiceDiscountAmt) {
        if (invoiceDiscountAmt == null || invoiceDiscountAmt.compareTo(ZERO) <= 0) return;

        // Total taxable BEFORE invoice discount
        BigDecimal baseTotal = items.stream()
                .map(i -> nz(i.getTaxableAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (baseTotal.compareTo(ZERO) == 0) return;

        // Distribute proportionally
        for (InvoiceItem item : items) {
            BigDecimal proportion = nz(item.getTaxableAmount())
                    .divide(baseTotal, CALC_SCALE, RoundingMode.HALF_UP);

            BigDecimal itemShare = invoiceDiscountAmt.multiply(proportion)
                    .setScale(SCALE, RoundingMode.HALF_UP);

            BigDecimal amountNoTax = nz(item.getAmountWithoutTax());
            BigDecimal existing = nz(item.getDiscountValue());
            BigDecimal newTotalDiscount = existing.add(itemShare);

            if (newTotalDiscount.compareTo(amountNoTax) > 0)
                newTotalDiscount = amountNoTax;

            // Persist combined discount as VALUE type
            item.setDiscountType("VALUE");
            item.setDiscountPercent(null);
            item.setDiscountValue(newTotalDiscount);

            // Recalculate taxable + GST + line total
            BigDecimal taxable = amountNoTax.subtract(newTotalDiscount);
            if (taxable.compareTo(ZERO) < 0) taxable = ZERO;
            item.setTaxableAmount(taxable);

            BigDecimal gstPct = nz(item.getGstPercent());
            BigDecimal gstAmt = pctOf(taxable, gstPct);
            item.setGstAmount(gstAmt);

            item.setLineTotal(taxable.add(gstAmt).setScale(SCALE, RoundingMode.HALF_UP));
        }
    }
}