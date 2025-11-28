// js/ui/totals.js
import { $, money } from "../utils.js";

export const totals = {

  recalc() {
    const rows = [...document.querySelectorAll("#createItemsBody tr")];

    let subtotal = 0;
    let taxTotal = 0;
    let taxableSum = 0;

    rows.forEach(r => {
      const qty  = Number(r.querySelector(".qty")?.value)  || 0;
      const price= Number(r.querySelector(".price")?.value)|| 0;
      const dval = Number(r.querySelector(".dval")?.value) || 0;
      const dpct = Number(r.querySelector(".dpct")?.value) || 0;
      const gst  = Number(r.querySelector(".gst")?.value)  || 0;

      const amt = qty * price;
      const disc = dpct > 0 ? (amt * dpct / 100) : dval;
      const taxable = amt - disc;
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
    });

    const type = $("discountType")?.value || "VALUE";
    const val  = Number($("discountValue")?.value) || 0;
    const discInv = type === "PERCENT" ? taxableSum * val / 100 : val;

    const grand = taxableSum - discInv + taxTotal;

    $("subtotalLine").textContent = "Subtotal: " + money(subtotal);
    $("taxTotalLine").textContent = "Tax Total: " + money(taxTotal);
    $("discountLine").textContent = "Discount: " + money(discInv);
    $("grandTotalLine").textContent = "Grand Total: " + money(grand);

    // Keep UI dataset for convenience (these are UI-derived preview numbers)
    $("createResult").dataset.subtotal        = subtotal;
    $("createResult").dataset.tax             = taxTotal;
    $("createResult").dataset.discountAmount  = discInv;
    $("createResult").dataset.grandTotal      = grand;
  },

  // Build payload-friendly wrapper (no server-only totals included).
  // Backend will compute authoritative totals.
  buildFinalPayload(items, customerId, notes) {
    const discountType = $("discountType")?.value || "VALUE";
    const discountValue = Number($("discountValue")?.value) || 0;
    const invoiceDiscount = (discountValue > 0) ? { type: discountType, value: discountValue } : null;

    return {
      customerId,
      notes,
      items,
      invoiceDiscount: invoiceDiscount
    };
  }
};
