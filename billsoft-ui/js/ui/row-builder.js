// ui/row-builder.js
import { productModule } from "../product.js";
import { totals } from "./totals.js";

export const rowBuilder = {

  clear() {
    document.getElementById("createItemsBody").innerHTML = "";
  },

  addRow() {
    const tbody = document.getElementById("createItemsBody");

    const row = document.createElement("tr");
    row.innerHTML = `
      <td><input list="productListGlobal" class="pname" /></td>
      <td><input type="number" class="qty" value="1" min="1"/></td>
      <td><input type="text" class="unit"/></td>
      <td><input type="number" class="price" value="0"/></td>
      <td class="amt">0</td>
      <td><input type="number" class="dval" value="0"/></td>
      <td><input type="number" class="dpct" value="0"/></td>
      <td class="taxable">0</td>
      <td><input type="number" class="gst" value="18"/></td>
      <td class="gstamt">0</td>
      <td class="total">0</td>
      <td><button class="btn small danger remove">×</button></td>
    `;

    tbody.appendChild(row);

    row.querySelector(".remove").onclick = () => {
      row.remove();
      totals.recalc();
    };

    row.querySelectorAll("input").forEach(i => {
      i.oninput = () => totals.recalc();
    });
  },

  buildPayloadItems(rows) {
    const items = [];

    rows.forEach(r => {
      const name = r.querySelector(".pname").value.trim();
      let prod = productModule.findByName(name);

      if (!prod) {
        alert("Unknown product: " + name);
        throw "Product missing";
      }

      items.push({
        productId: prod.id,
        qty: Number(r.querySelector(".qty").value),
        unit: r.querySelector(".unit").value,
        pricePerUnit: Number(r.querySelector(".price").value),
        amountWithoutTax: Number(r.querySelector(".amt").textContent),
        discountValue: Number(r.querySelector(".dval").value),
        discountPercent: Number(r.querySelector(".dpct").value),
        taxableAmount: Number(r.querySelector(".taxable").textContent),
        gstPercent: Number(r.querySelector(".gst").value),
        gstAmount: Number(r.querySelector(".gstamt").textContent),
        lineTotal: Number(r.querySelector(".total").textContent)
      });
    });

    return items;
  }
};
