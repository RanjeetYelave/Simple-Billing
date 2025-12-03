package com.billing.simple.billsoft.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

        invoice.setInvoiceDate(LocalDateTime.now());
        invoice.setPaid(Boolean.TRUE.equals(request.getPaid()));

        /* ======================================================================
           ⚠️ IMPORTANT FIX FOR CONVERSION
           If request.items is empty or null → retain existing DB items
        ====================================================================== */
        if ((request.getItems() == null || request.getItems().isEmpty())
                && invoice.getItems() != null && !invoice.getItems().isEmpty()) {

            List<InvoiceItem> cloned = new ArrayList<>();
            for (InvoiceItem old : invoice.getItems()) {
                InvoiceItem ni = new InvoiceItem();
                ni.setProduct(old.getProduct());
                ni.setQty(old.getQty());
                ni.setUnit(old.getUnit());
                ni.setPricePerUnit(old.getPricePerUnit());
                ni.setAmountWithoutTax(old.getAmountWithoutTax());
                ni.setDiscountType(old.getDiscountType());
                ni.setDiscountPercent(old.getDiscountPercent());
                ni.setDiscountValue(old.getDiscountValue());
                ni.setTaxableAmount(old.getTaxableAmount());
                ni.setGstPercent(old.getGstPercent());
                ni.setGstAmount(old.getGstAmount());
                ni.setLineTotal(old.getLineTotal());
                ni.setInvoice(invoice);
                cloned.add(ni);
            }

            invoice.getItems().clear();
            invoice.getItems().addAll(cloned);
            return invoice; // skip recalculating totals
        }

        /* ---------------------------
           BUILD ITEMS (normal case)
        ---------------------------- */
        invoice.getItems().clear();
        List<InvoiceItem> items = new ArrayList<>();

        for (InvoiceRequestItem ri : request.getItems()) {
            Product product = findProduct(ri.getProductId(), productList);
            InvoiceItem item = buildItem(ri, product);
            if (item != null) {
                item.setInvoice(invoice);
                items.add(item);
            }
        }

        /* ---------------------------
           SUBTOTAL & DISCOUNT
        ---------------------------- */
        BigDecimal rawSubtotal = ZERO;
        BigDecimal itemDiscountSum = ZERO;

        for (InvoiceItem it : items) {
            rawSubtotal = rawSubtotal.add(nz(it.getAmountWithoutTax()));
            BigDecimal id = discountAmount(it.getAmountWithoutTax(), it.getDiscountPercent(), it.getDiscountValue());
            itemDiscountSum = itemDiscountSum.add(id);
        }

        BigDecimal taxableSubtotal = rawSubtotal.subtract(itemDiscountSum);
        if (taxableSubtotal.compareTo(BigDecimal.ZERO) < 0) taxableSubtotal = ZERO;

        BigDecimal invDiscAmt = calculateInvoiceLevelDiscount(taxableSubtotal, request.getInvoiceDiscount());
        applyInvoiceLevelDiscount(items, invDiscAmt);

        /* ---------------------------
           FINAL TOTALS
        ---------------------------- */
        BigDecimal finalSubtotal = ZERO;
        BigDecimal gstTotal = ZERO;

        for (InvoiceItem it : items) {
            finalSubtotal = finalSubtotal.add(nz(it.getTaxableAmount()));
            gstTotal = gstTotal.add(nz(it.getGstAmount()));
        }

        BigDecimal grand = finalSubtotal.add(gstTotal);
        BigDecimal finalDiscount = itemDiscountSum.add(invDiscAmt);

        if (request.getRoundOff() != null) {
            BigDecimal rounded = grand.setScale(0, RoundingMode.HALF_UP);
            BigDecimal ro = rounded.subtract(grand).setScale(SCALE, RoundingMode.HALF_UP);
            invoice.setRoundOff(ro);
            grand = rounded;
        } else {
            invoice.setRoundOff(ZERO);
        }

        invoice.setSubtotalWithoutTax(finalSubtotal);
        invoice.setTotalTax(gstTotal);
        invoice.setTotalDiscount(finalDiscount);
        invoice.setTotalAmount(grand);

        if (request.getInvoiceDiscount() != null) {
            invoice.setInvoiceDiscountType(request.getInvoiceDiscount().getType());
            invoice.setInvoiceDiscountValue(request.getInvoiceDiscount().getValue());
        } else {
            invoice.setInvoiceDiscountType(null);
            invoice.setInvoiceDiscountValue(null);
        }

        items.forEach(i -> i.setInvoice(invoice));
        invoice.getItems().addAll(items);

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

        BigDecimal price = req.getPricePerUnit() != null
                ? req.getPricePerUnit()
                : (product != null ? nz(product.getPrice()) : ZERO);

        price = price.setScale(SCALE, RoundingMode.HALF_UP);
        item.setPricePerUnit(price);

        BigDecimal amountNoTax = price.multiply(BigDecimal.valueOf(qty)).setScale(SCALE, RoundingMode.HALF_UP);
        item.setAmountWithoutTax(amountNoTax);

        BigDecimal dpct = req.getDiscountPercent();
        BigDecimal dval = req.getDiscountValue();

        if (dpct != null && dpct.compareTo(BigDecimal.ZERO) > 0) {
            item.setDiscountType("PERCENT");
            item.setDiscountPercent(dpct);
        } else if (dval != null && dval.compareTo(BigDecimal.ZERO) > 0) {
            item.setDiscountType("VALUE");
            item.setDiscountValue(dval);
        }

        BigDecimal discAmt = discountAmount(amountNoTax, dpct, dval);

        BigDecimal taxable = amountNoTax.subtract(discAmt);
        if (taxable.compareTo(BigDecimal.ZERO) < 0) taxable = ZERO;
        item.setTaxableAmount(taxable);

        BigDecimal gstPct = req.getGstPercent() != null
                ? req.getGstPercent()
                : (product != null ? nz(product.getGstPercentage()) : ZERO);

        item.setGstPercent(gstPct.setScale(2, RoundingMode.HALF_UP));

        BigDecimal gstAmount = pctOf(taxable, gstPct);
        item.setGstAmount(gstAmount);

        item.setLineTotal(taxable.add(gstAmount).setScale(SCALE, RoundingMode.HALF_UP));
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

    private BigDecimal discountAmount(BigDecimal base, BigDecimal pct, BigDecimal val) {
        if (pct != null && pct.compareTo(BigDecimal.ZERO) > 0)
            return pctOf(base, pct);
        if (val != null)
            return val.setScale(SCALE, RoundingMode.HALF_UP);
        return ZERO;
    }

    private BigDecimal calculateInvoiceLevelDiscount(BigDecimal taxable, Discount d) {
        if (d == null || d.getValue() == null) return ZERO;
        if ("PERCENT".equalsIgnoreCase(d.getType())) {
            return pctOf(taxable, d.getValue());
        } else {
            return d.getValue().setScale(SCALE, RoundingMode.HALF_UP);
        }
    }

    private void applyInvoiceLevelDiscount(List<InvoiceItem> items, BigDecimal invoiceDiscount) {
        if (invoiceDiscount == null || invoiceDiscount.compareTo(BigDecimal.ZERO) <= 0) return;

        BigDecimal taxableSum = ZERO;
        for (InvoiceItem it : items)
            taxableSum = taxableSum.add(nz(it.getTaxableAmount()));

        if (taxableSum.compareTo(BigDecimal.ZERO) <= 0) return;

        for (InvoiceItem it : items) {
            BigDecimal taxable = nz(it.getTaxableAmount());
            BigDecimal share = taxable.divide(taxableSum, CALC_SCALE, RoundingMode.HALF_UP);
            BigDecimal reduction = invoiceDiscount.multiply(share);

            BigDecimal newTaxable = taxable.subtract(reduction);
            if (newTaxable.compareTo(BigDecimal.ZERO) < 0) newTaxable = ZERO;

            BigDecimal gstPct = nz(it.getGstPercent());
            BigDecimal newGst = pctOf(newTaxable, gstPct);

            it.setTaxableAmount(newTaxable);
            it.setGstAmount(newGst);
            it.setLineTotal(newTaxable.add(newGst).setScale(SCALE, RoundingMode.HALF_UP));
        }
    }
}
