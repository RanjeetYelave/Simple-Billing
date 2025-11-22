// js/ui/row-builder.js
import { productModule } from "../product.js";
import { totals } from "./totals.js";

export const rowBuilder = {

  clear() {
    const tbody = document.getElementById("createItemsBody");
    if (tbody) tbody.innerHTML = "";
  },

  addRow() {
    const tbody = document.getElementById("createItemsBody");
    if (!tbody) return;

    const row = document.createElement("tr");
    row.innerHTML = `
      <td><input list="productListGlobal" class="pname" /></td>
      <td><input type="number" class="qty" value="1" min="1"/></td>
      <td><input type="text" class="unit" value="pcs"/></td>
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

    row.querySelectorAll("input").forEach(i => i.oninput = totals.recalc);
  },

  async buildPayloadItems(rows) {
    const items = [];

    for (const r of rows) {
      const name = r.querySelector(".pname").value.trim();
      if (!name) throw new Error("Missing product name");

      let prod = productModule.findByName(name);

      if (!prod) {
        // silently auto-create product
        prod = await productModule.create({
          name,
          price: Number(r.querySelector(".price").value) || 0,
          unit: r.querySelector(".unit").value || "pcs",
          gstPercentage: Number(r.querySelector(".gst").value) || 0,
        });
      }

      items.push({
        productId: prod.id,
        qty: Number(r.querySelector(".qty").value),
        unit: r.querySelector(".unit").value,
        pricePerUnit: Number(r.querySelector(".price").value),
        discountValue: Number(r.querySelector(".dval").value),
        discountPercent: Number(r.querySelector(".dpct").value),
        gstPercent: Number(r.querySelector(".gst").value),
      });
    }

    return items;
  }
};
