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

  addRow() {
    const tbody = document.getElementById("createItemsBody");
    if (!tbody) return;

    const rowId = ++this.rowIndex;

    const row = document.createElement("tr");
    row.dataset.row = String(rowId);

    row.innerHTML = `
      <td><input list="productListGlobal" class="pname" /></td>
      <td><input type="number" class="qty" value="1" min="0"/></td>
      <td><input type="text" class="unit"/></td>
      <td><input type="number" class="price" value="0" min="0"/></td>
      <td class="amt">0</td>
      <td><input type="number" class="dval" value="0" min="0"/></td>
      <td><input type="number" class="dpct" value="0" min="0"/></td>
      <td class="taxable">0</td>
      <td><input type="number" class="gst" value="0" min="0"/></td>
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
    const dpct    = row.querySelector(".dpct");
    const gst     = row.querySelector(".gst");
    const remove  = row.querySelector(".remove");

    // -------------------------------
    // AUTO-FILL PRODUCT ON SELECT
    // -------------------------------
    if (pname) {
      pname.onchange = () => {
        const val = pname.value.trim();
        if (!val) return;

        const p = productModule.findByName(val);
        if (!p) {
          // No autofill if unknown; will be auto-created on save.
          totals.recalc();
          return;
        }

        if (qty)   qty.value   = qty.value || "1";
        if (unit)  unit.value  = p.unit || "";
        if (price) price.value = (p.price != null ? p.price : 0);
        if (gst)   gst.value   = (p.gstPercentage != null ? p.gstPercentage : 0);

        totals.recalc();
        if (qty) qty.focus();
      };
    }

    // -------------------------------
    // TALLY-LIKE ENTER NAVIGATION
    // -------------------------------
    const navOrder = [".pname", ".qty", ".unit", ".price", ".dval", ".dpct", ".gst"];

    navOrder.forEach((selector, idx) => {
      const field = row.querySelector(selector);
      if (!field) return;

      field.onkeydown = e => {
        if (e.key !== "Enter") return;
        e.preventDefault();

        // Last field in the row
        if (idx === navOrder.length - 1) {
          totals.recalc();
          // Always add next row for fast billing, even if this row is empty.
          rowBuilder.addRow();
          const nextRow = document.querySelector(
            `#createItemsBody tr[data-row="${rowBuilder.rowIndex}"] .pname`
          );
          if (nextRow) nextRow.focus();
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
    row.querySelectorAll("input").forEach(inp => {
      inp.oninput = () => totals.recalc();
    });

    // -------------------------------
    // REMOVE ROW
    // -------------------------------
    if (remove) {
      remove.onclick = () => {
        row.remove();
        totals.recalc();
      };
    }

    return row;
  },

  // -----------------------------------
  // BUILD PAYLOAD ITEMS (AUTO-CREATE PRODUCTS)
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
      let prod = productModule.findByName(name);

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
