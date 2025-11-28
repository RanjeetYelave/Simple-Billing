// js/ui/row-builder.js
import { productModule } from "../product.js";
import { totals } from "./totals.js";

export const rowBuilder = {

  rowIndex: 0,

  clear() {
    const tbody = document.getElementById("createItemsBody");
    if (tbody) tbody.innerHTML = "";
    this.rowIndex = 0;
  },

  // helper: returns array of unit strings (unique) with "PCS" first
  getUnitOptions() {
    const units = new Set();
    units.add("PCS");
    (productModule.products || []).forEach(p => {
      if (p.unit && p.unit.trim()) units.add(p.unit.trim());
    });
    return Array.from(units);
  },

  // create a <select> HTML string for unit options
  buildUnitSelectHtml(selected = "PCS") {
    const opts = this.getUnitOptions()
      .map(u => `<option value="${u}" ${u === selected ? "selected" : ""}>${u}</option>`)
      .join("");
    return `<select class="unit">${opts}</select>`;
  },

  addRow() {
    const tbody = document.getElementById("createItemsBody");
    if (!tbody) return;

    const rowId = ++this.rowIndex;

    const row = document.createElement("tr");
    row.dataset.row = String(rowId);

    // NOTE: quantity default is BLANK (user must type). Unit is a select defaulting to PCS.
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

    // AUTO-FILL PRODUCT ON SELECT
    if (pname) {
      pname.onchange = () => {
        const val = pname.value.trim();
        if (!val) {
          totals.recalc();
          return;
        }

        const p = productModule.findByName(val);
        if (!p) {
          // unknown product: leave fields as typed
          totals.recalc();
          return;
        }

        // autofill fields using product
        if (qty)   qty.value   = qty.value || "1";
        if (unit)  unit.value  = p.unit || "PCS";
        if (price) price.value = (p.price != null ? p.price : 0);
        if (gst)   gst.value   = (p.gstPercentage != null ? p.gstPercentage : 0);

        // store dataset to help edits later (optional)
        row.dataset.productId = p.id;
        row.dataset.productName = p.name;

        totals.recalc();
        if (qty) qty.focus();
      };

      // when typing product name and pressing Enter -> jump to qty (or add row if last)
      pname.onkeydown = (e) => {
        if (e.key !== "Enter") return;
        e.preventDefault();
        // if there's a product selected auto-fill will happen via onchange, but we move focus
        const next = qty || row.querySelector(".unit") || row.querySelector(".price");
        if (next) next.focus();
        totals.recalc();
      };
    }

    // NAVIGATION ORDER (tally-like)
    const navOrder = [".pname", ".qty", ".unit", ".price", ".dval", ".gst"];

    navOrder.forEach((selector, idx) => {
      const field = row.querySelector(selector);
      if (!field) return;

      field.onkeydown = e => {
        if (e.key !== "Enter") return;
        e.preventDefault();

        // Last field in the row -> recalc, add next row and focus next row's pname
        if (idx === navOrder.length - 1) {
          totals.recalc();

          // Always add next row for fast billing
          const prevRowIndex = rowBuilder.rowIndex;
          rowBuilder.addRow();

          // focus next newly created row's pname
          const nextRowPname = document.querySelector(`#createItemsBody tr[data-row="${prevRowIndex + 1}"] .pname`);
          if (nextRowPname) nextRowPname.focus();
          return;
        }

        // Move to next field in same row
        const nextField = row.querySelector(navOrder[idx + 1]);
        if (nextField) {
          nextField.focus();
        }
        totals.recalc();
      };
    });

    // Recalc on any value change
    row.querySelectorAll("input, select").forEach(inp => {
      inp.oninput = () => totals.recalc();
    });

    // REMOVE ROW
    if (remove) {
      remove.onclick = () => {
        row.remove();
        totals.recalc();
      };
    }

    return row;
  },

  // BUILD PAYLOAD ITEMS (AUTO-CREATE PRODUCTS)
  // Note: UI no longer uses percentage discounts. We send discountValue (absolute).
  async buildPayloadItems(rows) {
    const items = [];

    for (const r of rows) {
      const getVal = cls => {
        const el = r.querySelector(cls);
        if (!el) return "";
        // select vs input
        return (el.value || "").toString().trim();
      };

      const name  = getVal(".pname");
      const unit  = getVal(".unit") || "PCS";
      const qty   = Number(getVal(".qty")) || 0;
      const price = Number(getVal(".price")) || 0;
      const dval  = Number(getVal(".dval")) || 0;
      const gst   = getVal(".gst") === "" ? null : Number(getVal(".gst"));

      // Skip completely empty row
      const isEmpty =
        !name && !unit &&
        qty === 0 && price === 0 &&
        dval === 0 && (gst === null || gst === 0);

      if (isEmpty) continue;

      // Find or create product
      let prod = productModule.findByName(name);

      if (!prod) {
        // create silently using typed values (same behaviour as earlier)
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
        discountPercent: null, // UI doesn't send percent discounts
        gstPercent: gst == null ? (prod.gstPercentage || 0) : gst
      });
    }

    return items;
  }
};
