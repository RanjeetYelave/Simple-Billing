// ui/totals.js
import { $, money } from "../utils.js";

export const totals = {

  recalc() {
    const rows = [...document.querySelectorAll("#createItemsBody tr")];

    let subtotal = 0;
    let taxTotal = 0;
    let taxableSum = 0;

    rows.forEach(r => {
      const qty = Number(r.querySelector(".qty").value);
      const price = Number(r.querySelector(".price").value);
      const dval = Number(r.querySelector(".dval").value);
      const dpct = Number(r.querySelector(".dpct").value);
      const gst = Number(r.querySelector(".gst").value);

      const amt = qty * price;
      const disc = dpct > 0 ? (amt * dpct / 100) : dval;
      const taxable = amt - disc;
      const gstamt = taxable * gst / 100;
      const total = taxable + gstamt;

      r.querySelector(".amt").textContent = amt.toFixed(2);
      r.querySelector(".taxable").textContent = taxable.toFixed(2);
      r.querySelector(".gstamt").textContent = gstamt.toFixed(2);
      r.querySelector(".total").textContent = total.toFixed(2);

      subtotal += amt;
      taxTotal += gstamt;
      taxableSum += taxable;
    });

    const type = $("discountType").value;
    const val = Number($("discountValue").value);
    const discInv = type === "PERCENT" ? taxableSum * val / 100 : val;

    const grand = taxableSum - discInv + taxTotal;

    $("subtotalLine").textContent = "Subtotal: " + money(subtotal);
    $("taxTotalLine").textContent = "Tax Total: " + money(taxTotal);
    $("discountLine").textContent = "Discount: " + money(discInv);
    $("grandTotalLine").textContent = "Grand Total: " + money(grand);

    $("createResult").dataset.subtotal = subtotal;
    $("createResult").dataset.tax = taxTotal;
    $("createResult").dataset.discountAmount = discInv;
    $("createResult").dataset.grandTotal = grand;
  },

  buildFinalPayload(items, customerId, notes) {
    return {
      customerId,
      notes,
      items,
      totals: {
        subtotalWithoutTax: Number($("createResult").dataset.subtotal),
        totalTax: Number($("createResult").dataset.tax),
        discountAmount: Number($("createResult").dataset.discountAmount),
        grandTotal: Number($("createResult").dataset.grandTotal)
      }
    };
  }
};
