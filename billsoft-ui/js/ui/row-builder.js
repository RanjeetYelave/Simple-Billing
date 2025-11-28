// js/row-builder.js
// Improved row builder: delegation, fewer querySelector calls, safer event wiring.
// Preserves buildPayloadItems semantics and auto-create behavior.

import { productModule } from "../product.js";
import { totals } from "./totals.js";

export const rowBuilder = {

  rowIndex: 0,

  clear() {
    const tbody = document.getElementById("createItemsBody");
    if (tbody) tbody.innerHTML = "";
    this.rowIndex = 0;
  },

  addRow() {
    const tbody = document.getElementById("createItemsBody");
    if (!tbody) return;

    const rowId = ++this.rowIndex;

    // create elements programmatically (avoids innerHTML thrash)
    const tr = document.createElement("tr");
    tr.dataset.row = String(rowId);

    tr.innerHTML = `
      <td><input list="productListGlobal" class="pname" autocomplete="off" /></td>
      <td><input type="number" class="qty" value="1" min="0"/></td>
      <td><input type="text" class="unit"/></td>
      <td><input type="number" class="price" value="0" min="0" step="0.01"/></td>
      <td class="amt">0.00</td>
      <td><input type="number" class="dval" value="0" min="0" step="0.01"/></td>
      <td><input type="number" class="dpct" value="0" min="0" step="0.01"/></td>
      <td class="taxable">0.00</td>
      <td><input type="number" class="gst" value="0" min="0" step="0.01"/></td>
      <td class="gstamt">0.00</td>
      <td class="total">0.00</td>
      <td><button class="btn small danger remove">×</button></td>
    `;

    tbody.appendChild(tr);

    // Efficient bindings: event delegation handled in initDelegation below.
    // But we need an onchange autofill on pname that may require immediate fill when selected.
    const pname = tr.querySelector(".pname");
    if (pname) {
      pname.addEventListener("change", () => {
        const val = pname.value.trim();
        if (!val) {
          totals.recalc();
          return;
        }
        const p = productModule.findByName(val);
        if (!p) {
          totals.recalc();
          return;
        }

        const qtyEl = tr.querySelector(".qty");
        const unitEl = tr.querySelector(".unit");
        const priceEl = tr.querySelector(".price");
        const gstEl = tr.querySelector(".gst");

        if (qtyEl) qtyEl.value = qtyEl.value || "1";
        if (unitEl) unitEl.value = p.unit || "";
        if (priceEl) priceEl.value = (p.price != null ? p.price : 0);
        if (gstEl) gstEl.value = (p.gstPercentage != null ? p.gstPercentage : 0);

        // store product meta on row for save optimization
        tr.dataset.productId = p.id || "";
        tr.dataset.productName = p.name || "";

        totals.recalc();
        if (qtyEl) qtyEl.focus();
      });
    }

    // Return row element for further manipulation if caller needs it
    return tr;
  },

  // Event delegation initialization for rows (call this once on page init)
  initDelegation() {
    const tbody = document.getElementById("createItemsBody");
    if (!tbody) return;

    // Use a single input listener for recalculation (throttled by totals)
    tbody.addEventListener("input", (e) => {
      // Only care about inputs inside rows
      if (!e.target) return;
      if (e.target.matches(".qty, .price, .dval, .dpct, .gst, .pname, .unit")) {
        totals.recalc();
      }
    });

    // Click handler for remove buttons
    tbody.addEventListener("click", (e) => {
      const btn = e.target.closest(".remove");
      if (btn) {
        const row = btn.closest("tr");
        if (row) {
          row.remove();
          totals.recalc();
        }
      }
    });

    // Keyboard navigation: Enter to move to next field or add row
    tbody.addEventListener("keydown", (e) => {
      if (e.key !== "Enter") return;
      const el = e.target;
      if (!el) return;
      e.preventDefault();

      const row = el.closest("tr");
      if (!row) return;
      // field order used in create UI
      const order = [".pname", ".qty", ".unit", ".price", ".dval", ".dpct", ".gst"];
      const idx = order.findIndex(sel => el.matches(sel));
      if (idx === -1) return;

      // if last field -> add next row and focus its pname
      if (idx === order.length - 1) {
        totals.recalc();
        this.addRow();
        const nextPname = document.querySelector(`#createItemsBody tr[data-row="${this.rowIndex}"] .pname`);
        if (nextPname) nextPname.focus();
        return;
      }

      // move to next field in same row
      const next = row.querySelector(order[idx + 1]);
      if (next) next.focus();
      totals.recalc();
    });
  },

  // -----------------------------------
  // BUILD PAYLOAD ITEMS (AUTO-CREATE PRODUCTS)
  // Kept semantics identical to previous impl but made more defensive.
  // -----------------------------------
  async buildPayloadItems(rows) {
    const items = [];

    for (const r of rows) {
      const getVal = cls => (r.querySelector(cls)?.value || "").trim();

      const name  = getVal(".pname");
      const unit  = getVal(".unit");
      const qty   = Number(getVal(".qty")) || 0;
      const price = Number(getVal(".price")) || 0;
      const dval  = Number(getVal(".dval")) || 0;
      const dpct  = Number(getVal(".dpct")) || 0;
      const gst   = getVal(".gst") === "" ? null : Number(getVal(".gst"));

      // CASE C: completely empty row -> skip
      const isEmpty =
        !name && !unit &&
        qty === 0 && price === 0 &&
        dval === 0 && dpct === 0 &&
        (gst === null || gst === 0);

      if (isEmpty) continue;

      // Find or create product
      let prod = null;
      const pid = r.dataset.productId ? Number(r.dataset.productId) : null;
      if (pid) {
        prod = productModule.products.find(p => p.id === pid) || null;
      } else if (name) {
        prod = productModule.findByName(name);
      }

      if (!prod) {
        // CASE B: create silently using TYPED values
        const payload = {
          name,
          price: price || 0,
          unit: unit || "Pcs",
          gstPercentage: gst == null ? null : gst
        };

        try {
          prod = await productModule.create(payload);
          // ensure UI product list is refreshed by productModule.create
        } catch (e) {
          console.error("Auto-create product failed for:", name, e);
          throw new Error("Failed to auto-create product: " + name);
        }
      }

      items.push({
        productId: prod.id,
        qty,
        unit: unit || prod.unit || "",
        pricePerUnit: price || prod.price || 0,
        discountType: null,
        discountValue: dval || 0,
        discountPercent: dpct || 0,
        gstPercent: gst == null
          ? (prod.gstPercentage || 0)
          : gst
      });
    }

    return items;
  }
};
