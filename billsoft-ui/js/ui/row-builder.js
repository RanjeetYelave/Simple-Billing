// js/ui/row-builder.js
import { productModule } from "../product.js";
import { totals } from "./totals.js";

export const rowBuilder = {

  clear() {
    const body = document.getElementById("createItemsBody");
    if (body) body.innerHTML = "";
  },

  addRow() {
    const tbody = document.getElementById("createItemsBody");
    if (!tbody) return;

    const row = document.createElement("tr");
    row.innerHTML = `
      <td><input list="productListGlobal" class="pname" placeholder="Product name"/></td>
      <td><input type="number" class="qty" value="1" min="1"/></td>
      <td><input type="text" class="unit" placeholder="Unit"/></td>
      <td><input type="number" class="price" value="0" min="0" step="0.01"/></td>
      <td class="amt">0</td>
      <td><input type="number" class="dval" value="0" min="0" step="0.01"/></td>
      <td><input type="number" class="dpct" value="0" min="0" max="100" step="0.01"/></td>
      <td class="taxable">0</td>
      <td><input type="number" class="gst" value="18" min="0" step="0.01"/></td>
      <td class="gstamt">0</td>
      <td class="total">0</td>
      <td><button class="btn small danger remove">×</button></td>
    `;

    tbody.appendChild(row);

    const pname = row.querySelector(".pname");
    const qty = row.querySelector(".qty");
    const unit = row.querySelector(".unit");
    const price = row.querySelector(".price");
    const dval = row.querySelector(".dval");
    const dpct = row.querySelector(".dpct");
    const gst = row.querySelector(".gst");
    const removeBtn = row.querySelector(".remove");

    // Autofill from product list on change / blur / Enter
    const tryAutofill = () => {
      const name = (pname.value || "").trim();
      if (!name) return;
      const prod = productModule.findByName(name);
      if (!prod) return;
      unit.value = prod.unit || "";
      price.value = prod.price != null ? prod.price : 0;
      gst.value = prod.gstPercentage != null ? prod.gstPercentage : 0;
      totals.recalc();
    };

    pname.addEventListener("change", tryAutofill);
    pname.addEventListener("blur", tryAutofill);
    pname.addEventListener("keydown", e => {
      if (e.key === "Enter") {
        tryAutofill();
        e.preventDefault();
      }
    });

    // All numeric changes recalc totals
    [qty, price, dval, dpct, gst].forEach(el => {
      el.addEventListener("input", () => totals.recalc());
      el.addEventListener("change", () => totals.recalc());
    });

    // Remove row
    removeBtn.onclick = () => {
      row.remove();
      totals.recalc();
    };
  },

  buildPayloadItems(rows) {
    const items = [];

    rows.forEach(r => {
      const name = r.querySelector(".pname").value.trim();
      const prod = productModule.findByName(name);

      if (!prod) {
        throw new Error("Unknown product: " + name);
      }

      items.push({
        productId: prod.id,
        qty: Number(r.querySelector(".qty").value),
        unit: r.querySelector(".unit").value,
        pricePerUnit: Number(r.querySelector(".price").value),
        amountWithoutTax: Number(r.querySelector(".amt").textContent) || 0,
        discountValue: Number(r.querySelector(".dval").value) || 0,
        discountPercent: Number(r.querySelector(".dpct").value) || 0,
        taxableAmount: Number(r.querySelector(".taxable").textContent) || 0,
        gstPercent: Number(r.querySelector(".gst").value) || 0,
        gstAmount: Number(r.querySelector(".gstamt").textContent) || 0,
        lineTotal: Number(r.querySelector(".total").textContent) || 0
      });
    });

    return items;
  }
};
