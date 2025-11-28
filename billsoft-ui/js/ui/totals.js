// js/ui/totals.js
import { $, money } from "../utils.js";

export const totals = {

  /**
   * Recalculate line totals and invoice totals.
   *
   * - Product Discount = total of per-line discounts (value or percent applied per line)
   * - Additional Discount = invoice-level discount (either percent or value, applied on taxable sum after product discounts)
   *
   * The UI shows:
   *   Subtotal (sum of qty * price)
   *   Product Discount (sum of per-item discounts)
   *   Tax Total
   *   Additional Discount (invoice-level)
   *   Grand Total
   *
   * We keep compatibility with previous dataset keys:
   * - createResult.dataset.subtotal  => raw subtotal (sum qty*price)
   * - createResult.dataset.tax       => taxTotal
   * - createResult.dataset.discountAmount => additionalDiscount (invoice-level)
   * - createResult.dataset.itemDiscount => productDiscount (new)
   * - createResult.dataset.grandTotal => grand total
   */
  recalc() {
    const rows = [...document.querySelectorAll("#createItemsBody tr")];

    let subtotal = 0;           // sum of qty * price (raw)
    let taxTotal = 0;           // sum of GST amounts (based on taxable per-line before invoice-level discount)
    let taxableSum = 0;         // sum of taxable per-line (after per-line discounts)
    let productDiscountTotal = 0; // sum of per-line discounts (value)

    rows.forEach(r => {
      const qty  = Number(r.querySelector(".qty")?.value)  || 0;
      const price= Number(r.querySelector(".price")?.value)|| 0;
      const dval = Number(r.querySelector(".dval")?.value) || 0;
      const dpct = Number(r.querySelector(".dpct")?.value) || 0;
      const gst  = Number(r.querySelector(".gst")?.value)  || 0;

      const amt = qty * price; // raw line amount (before any discount)
      // per-line discount (either percent or absolute)
      const disc = dpct > 0 ? (amt * dpct / 100) : dval;
      const taxable = Math.max(0, amt - disc);
      const gstamt = taxable * gst / 100;
      const total = taxable + gstamt;

      // update row displays (if cells exist)
      const amtCell  = r.querySelector(".amt");
      const taxCell  = r.querySelector(".taxable");
      const gstCell  = r.querySelector(".gstamt");
      const totCell  = r.querySelector(".total");

      if (amtCell) amtCell.textContent = amt.toFixed(2);
      if (taxCell) taxCell.textContent = taxable.toFixed(2);
      if (gstCell) gstCell.textContent = gstamt.toFixed(2);
      if (totCell) totCell.textContent = total.toFixed(2);

      subtotal    += amt;
      taxTotal    += gstamt;
      taxableSum  += taxable;
      productDiscountTotal += disc;
    });

    // Invoice-level discount (Additional Discount)
    const type = $("discountType")?.value || "VALUE";
    const val  = Number($("discountValue")?.value) || 0;
    const additionalDiscount = type === "PERCENT" ? taxableSum * val / 100 : val;

    // Grand total = (taxableSum - additionalDiscount) + taxTotal
    const grand = Math.max(0, taxableSum - additionalDiscount) + taxTotal;

    // Update UI lines (ensure nodes exist)
    // Keep old element ids for compatibility; add new productDiscountLine
    const productLineEl = $("productDiscountLine");
    const subtotalEl = $("subtotalLine");
    const taxEl = $("taxTotalLine");
    const addDiscEl = $("discountLine"); // reused as Additional Discount line
    const grandEl = $("grandTotalLine");

    if (subtotalEl) subtotalEl.textContent = "Subtotal: " + money(subtotal);
    if (productLineEl) productLineEl.textContent = "Product Discount: -" + money(productDiscountTotal);
    if (taxEl) taxEl.textContent = "Tax Total: " + money(taxTotal);
    if (addDiscEl) addDiscEl.textContent = "Additional Discount: -" + money(additionalDiscount);
    if (grandEl) grandEl.textContent = "Grand Total: " + money(grand);

    // Persist values on dataset for buildFinalPayload and other logic
    const resultEl = $("createResult");
    if (resultEl) {
      resultEl.dataset.subtotal = subtotal;
      resultEl.dataset.tax = taxTotal;
      resultEl.dataset.discountAmount = additionalDiscount; // legacy key (invoice-level)
      resultEl.dataset.itemDiscount = productDiscountTotal; // new key (product-level)
      resultEl.dataset.grandTotal = grand;
    }
  },

  /**
   * Build final payload for invoice create API.
   * We keep previous structure but add explicit item-level discount and additionalDiscount values
   * for clarity and server-side reconciliation (server remains authoritative).
   */
  buildFinalPayload(items, customerId, notes) {
    const resultEl = $("createResult");
    const subtotalRaw = Number(resultEl?.dataset.subtotal || 0);
    const taxTotal = Number(resultEl?.dataset.tax || 0);
    const additionalDiscount = Number(resultEl?.dataset.discountAmount || 0);
    const itemDiscount = Number(resultEl?.dataset.itemDiscount || 0);
    const grandTotal = Number(resultEl?.dataset.grandTotal || 0);

    return {
      customerId,
      notes,
      items,
      totals: {
        // backward-compatible keys
        subtotalWithoutTax: subtotalRaw,
        totalTax: taxTotal,
        discountAmount: additionalDiscount, // invoice-level (legacy)
        grandTotal: grandTotal,

        // new explicit keys
        totalItemDiscount: itemDiscount,
        additionalDiscount: additionalDiscount
      }
    };
  }
};
