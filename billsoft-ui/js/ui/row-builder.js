// js/ui/row-builder.js
import { productModule } from "../product.js";
import { totals } from "./totals.js";

export const rowBuilder = {
  rowIndex: 0,
  getUnitOptions() {
    const units = new Set();
    units.add("PCS");
    (productModule.products || []).forEach(p => {
      if (p.unit && p.unit.trim()) units.add(p.unit.trim());
    });
    return Array.from(units);
  },
  buildUnitSelectHtml(selected = "PCS") {
    const opts = this.getUnitOptions().map(u => `<option value="${u}" ${u === selected ? "selected" : ""}>${u}</option>`).join("");
    return `<select class="unit">${opts}</select>`;
  },
  clear() {
    const tbody = document.getElementById("createItemsBody");
    if (tbody) tbody.innerHTML = "";
    this.rowIndex = 0;
  },
  addRow() {
    const tbody = document.getElementById("createItemsBody");
    if (!tbody) return;
    const rowId = ++this.rowIndex;
    const row = document.createElement("tr");
    row.dataset.row = String(rowId);
    row.innerHTML = `
      <td><input list="productListGlobal" class="pname" autocomplete="off" /></td>
      <td><input type="number" class="qty" value="" min="0" step="1" /></td>
      <td>${this.buildUnitSelectHtml("PCS")}</td>
      <td><input type="number" class="price" value="0" min="0" step="0.01" /></td>
      <td class="amt">0</td>
      <td><input type="number" class="dval" value="0" min="0" step="0.01" /></td>
      <td class="taxable">0</td>
      <td><input type="number" class="gst" value="0" min="0" step="0.01" /></td>
      <td class="gstamt">0</td>
      <td class="total">0</td>
      <td><button class="btn small danger remove">×</button></td>
    `;
    tbody.appendChild(row);

    const pname   = row.querySelector(".pname");
    const qty     = row.querySelector(".qty");
    const unit    = row.querySelector(".unit");
    const price   = row.querySelector(".price");
    const dval    = row.querySelector(".dval");
    const gst     = row.querySelector(".gst");
    const remove  = row.querySelector(".remove");

    if (pname) {
      pname.onchange = () => {
        const val = pname.value.trim();
        if (!val) { totals.recalc(); return; }
        const p = productModule.findByName(val);
        if (!p) { totals.recalc(); return; }
        if (qty) qty.value = qty.value || "1";
        if (unit) unit.value = p.unit || "PCS";
        if (price) price.value = (p.price != null ? p.price : 0);
        if (gst) gst.value = (p.gstPercentage != null ? p.gstPercentage : 0);
        row.dataset.productId = p.id;
        row.dataset.productName = p.name;
        totals.recalc();
        if (qty) qty.focus();
      };
      pname.onkeydown = e => {
        if (e.key !== "Enter") return;
        e.preventDefault();
        const next = qty || row.querySelector(".unit") || row.querySelector(".price");
        if (next) next.focus();
        totals.recalc();
      };
    }

    const navOrder = [".pname", ".qty", ".unit", ".price", ".dval", ".gst"];
    navOrder.forEach((selector, idx) => {
      const field = row.querySelector(selector);
      if (!field) return;
      field.onkeydown = e => {
        if (e.key !== "Enter") return;
        e.preventDefault();
        if (idx === navOrder.length - 1) {
          totals.recalc();
          const prevRowIndex = rowBuilder.rowIndex;
          rowBuilder.addRow();
          const nextRowPname = document.querySelector(`#createItemsBody tr[data-row="${prevRowIndex + 1}"] .pname`);
          if (nextRowPname) nextRowPname.focus();
          return;
        }
        const nextField = row.querySelector(navOrder[idx + 1]);
        if (nextField) nextField.focus();
        totals.recalc();
      };
    });

    row.querySelectorAll("input, select").forEach(inp => inp.oninput = () => totals.recalc());
    if (remove) remove.onclick = () => { row.remove(); totals.recalc(); };
    return row;
  },

  async buildPayloadItems(rows) {
    const items = [];
    for (const r of rows) {
      const getVal = cls => {
        const el = r.querySelector(cls);
        if (!el) return "";
        return (el.value || "").toString().trim();
      };
      const name  = getVal(".pname");
      const unit  = getVal(".unit") || "PCS";
      const qty   = Number(getVal(".qty")) || 0;
      const price = Number(getVal(".price")) || 0;
      const dval  = Number(getVal(".dval")) || 0;
      const gst   = getVal(".gst") === "" ? null : Number(getVal(".gst"));

      const isEmpty =
        !name && !unit && qty === 0 && price === 0 && dval === 0 && (gst === null || gst === 0);
      if (isEmpty) continue;

      let prod = productModule.findByName(name);
      if (!prod) {
        const payload = {
          name,
          price: price || 0,
          unit: unit || "PCS",
          gstPercentage: gst == null ? null : gst
        };
        try {
          prod = await productModule.create(payload);
        } catch (e) {
          console.error("Auto-create product failed for:", name, e);
          throw new Error("Failed to auto-create product: " + name);
        }
      }

      items.push({
        productId: prod.id,
        qty,
        unit: unit || prod.unit || "PCS",
        pricePerUnit: price || prod.price || 0,
        discountType: dval > 0 ? "VALUE" : null,
        discountValue: dval || 0,
        discountPercent: null,
        gstPercent: gst == null ? (prod.gstPercentage || 0) : gst
      });
    }
    return items;
  }
};
