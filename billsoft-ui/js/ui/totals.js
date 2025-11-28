// js/ui/totals.js
import { $, money } from "../utils.js";

function round2(v) {
  return Math.round((v || 0) * 100) / 100;
}

export const totals = {

  recalc() {
    const rows = [...document.querySelectorAll("#createItemsBody tr")];

    let rawSubtotal = 0;    // sum of qty * price (before per-item discounts)
    let taxTotal = 0;
    let taxableSum = 0;     // sum of taxable amounts after per-item discounts

    rows.forEach(r => {
      const qty  = Number(r.querySelector(".qty")?.value)  || 0;
      const price= Number(r.querySelector(".price")?.value)|| 0;
      const dval = Number(r.querySelector(".dval")?.value) || 0;
      const dpct = Number(r.querySelector(".dpct")?.value) || 0;
      const gst  = Number(r.querySelector(".gst")?.value)  || 0;

      const amt = qty * price;                    // raw amount (before per-item discount)
      const disc = dpct > 0 ? (amt * dpct / 100) : dval;
      const taxable = Math.max(0, amt - disc);    // taxable after per-item discount
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

      rawSubtotal += amt;
      taxTotal    += gstamt;
      taxableSum  += taxable;
    });

    // Invoice-level discount inputs
    const type = ($("discountType")?.value || "VALUE");
    const val  = Number($("discountValue")?.value) || 0;

    // Discount amount applied at invoice level (calculated on taxableSum)
    const discInv = type === "PERCENT" ? (taxableSum * val / 100) : val;

    const grand = taxableSum - discInv + taxTotal;

    // update lines shown to user
    $("subtotalLine").textContent = "Subtotal: " + money(round2(taxableSum));
    $("taxTotalLine").textContent = "Tax Total: " + money(round2(taxTotal));
    $("discountLine").textContent = "Discount: " + money(round2(discInv));
    $("grandTotalLine").textContent = "Grand Total: " + money(round2(grand));

    // store canonical numbers (rounded) for payload builder
    $("createResult").dataset.subtotal        = round2(taxableSum);
    $("createResult").dataset.tax             = round2(taxTotal);
    $("createResult").dataset.discountAmount  = round2(discInv);
    $("createResult").dataset.grandTotal      = round2(grand);

    // also store invoice discount meta so buildFinalPayload can pick it up (optional)
    $("createResult").dataset.invoiceDiscountType  = type;
    $("createResult").dataset.invoiceDiscountValue = Number(val);
  },

  buildFinalPayload(items, customerId, notes) {
    // Read stored computed values (fall back to zero)
    const subtotalWithoutTax = Number($("createResult").dataset.subtotal || 0);
    const totalTax           = Number($("createResult").dataset.tax || 0);
    const discountAmount     = Number($("createResult").dataset.discountAmount || 0);
    const grandTotal         = Number($("createResult").dataset.grandTotal || 0);

    // Invoice-level discount object (match backend DTO shape)
    const invoiceDiscountType  = $("createResult").dataset.invoiceDiscountType || "VALUE";
    const invoiceDiscountValue = Number($("createResult").dataset.invoiceDiscountValue || 0);

    const payload = {
      customerId,
      notes,
      items,
      invoiceDiscount: {
        type: invoiceDiscountType,
        value: invoiceDiscountValue
      },
      totals: {
        subtotalWithoutTax: subtotalWithoutTax,
        totalTax:           totalTax,
        discountAmount:     discountAmount,
        grandTotal:         grandTotal
      }
    };

    return payload;
  }
};
