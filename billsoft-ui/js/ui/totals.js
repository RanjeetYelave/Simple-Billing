// js/ui/totals.js
import { $, money } from "../utils.js";

export const totals = {

  recalc() {
    const rows = [...document.querySelectorAll("#createItemsBody tr")];

    let subtotal = 0;              // sum of qty * price (raw)
    let taxTotal = 0;              // sum GST
    let taxableSum = 0;            // sum taxable after per-line discounts
    let productDiscountTotal = 0;  // sum per-line discount values

    rows.forEach(r => {
      const qty  = Number(r.querySelector(".qty")?.value)  || 0;
      const price= Number(r.querySelector(".price")?.value)|| 0;
      const dval = Number(r.querySelector(".dval")?.value) || 0;
      const dpct = Number(r.querySelector(".dpct")?.value) || 0;
      const gst  = Number(r.querySelector(".gst")?.value)  || 0;

      const amt = qty * price; // raw line amount
      const disc = dpct > 0 ? (amt * dpct / 100) : dval;
      const taxable = Math.max(0, amt - disc);
      const gstamt = taxable * gst / 100;
      const total = taxable + gstamt;

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
    const type = $("discountType")?.value || "VALUE";  // PERCENT / VALUE
    const val  = Number($("discountValue")?.value) || 0;
    const additionalDiscount = type === "PERCENT"
      ? (taxableSum * val / 100)
      : val;

    const grand = Math.max(0, taxableSum - additionalDiscount) + taxTotal;

    const productLineEl = $("productDiscountLine");
    const subtotalEl = $("subtotalLine");
    const taxEl = $("taxTotalLine");
    const addDiscEl = $("discountLine");
    const grandEl = $("grandTotalLine");

    if (subtotalEl) subtotalEl.textContent = "Subtotal: " + money(subtotal);
    if (productLineEl) productLineEl.textContent = "Product Discount: -" + money(productDiscountTotal);
    if (taxEl) taxEl.textContent = "Tax Total: " + money(taxTotal);
    if (addDiscEl) addDiscEl.textContent = "Additional Discount: -" + money(additionalDiscount);
    if (grandEl) grandEl.textContent = "Grand Total: " + money(grand);

    const resultEl = $("createResult");
    if (resultEl) {
      resultEl.dataset.subtotal = subtotal;
      resultEl.dataset.tax = taxTotal;
      resultEl.dataset.discountAmount = additionalDiscount;
      resultEl.dataset.itemDiscount = productDiscountTotal;
      resultEl.dataset.grandTotal = grand;
    }
  },

  /**
   * Build final payload for invoice create API.
   * Now:
   *  - sends `customerNote`
   *  - sends proper `invoiceDiscount { type, value }`
   *  - leaves server as single source of truth for totals
   */
  buildFinalPayload(items, customerId, customerNote) {
    const resultEl = $("createResult");
    const subtotalRaw = Number(resultEl?.dataset.subtotal || 0);
    const taxTotal = Number(resultEl?.dataset.tax || 0);
    const additionalDiscount = Number(resultEl?.dataset.discountAmount || 0);
    const itemDiscount = Number(resultEl?.dataset.itemDiscount || 0);
    const grandTotal = Number(resultEl?.dataset.grandTotal || 0);

    const discType = $("discountType")?.value || "VALUE";
    const discVal  = Number($("discountValue")?.value) || 0;

    const invoiceDiscount =
      discVal > 0
        ? { type: discType, value: discVal }
        : null;

    return {
      customerId,
      customerNote,      // <-- backend reads this
      invoiceDiscount,   // <-- InvoiceRequest.Discount
      items,

      // purely informational, backend can ignore
      clientTotals: {
        subtotalWithoutTax: subtotalRaw,
        totalTax: taxTotal,
        totalItemDiscount: itemDiscount,
        additionalDiscount,
        grandTotal
      }
    };
  }
};
