// js/ui/invoice-edit.js
import { invoiceModule } from "../invoice.js";
import { productModule } from "../product.js";
import { $, money } from "../utils.js";

export const invoiceEdit = {

  previewTimer: null,

  async open(id) {
    const inv = await invoiceModule.preview(id);
    if (!inv) {
      alert("Invoice not found");
      return;
    }

    $("invDetails").innerHTML = this.render(inv);
    this.init(inv);
  },

  render(inv) {
    const customerLabel = inv.customer
      ? `${inv.customer.name} (id:${inv.customer.id})`
      : "Unknown customer";

    return `
      <div class="invoice-container">
        <h2>Edit ${inv.invoiceNumber}</h2>

        <div class="invoice-meta-edit">
          <label>Customer</label>
          <input type="text" value="${customerLabel}" disabled />

          <label>Date</label>
          <input id="editDate" type="datetime-local"
                 value="${inv.invoiceDate ? inv.invoiceDate.slice(0,16) : ""}"/>

          <label>Notes</label>
          <textarea id="editNotes">${inv.notes || ""}</textarea>
        </div>

        <h3 style="margin-top:14px;">Items</h3>
        <table class="invoice-table">
          <thead>
            <tr>
              <th>Product</th>
              <th>Qty</th>
              <th>Unit</th>
              <th>Price</th>
              <th>Disc</th>
              <th>D%</th>
              <th>GST%</th>
              <th>Total</th>
              <th></th>
            </tr>
          </thead>
          <tbody id="editItemsBody"></tbody>
        </table>

        <datalist id="editProductList"></datalist>

        <div style="margin-top:10px; display:flex; justify-content:space-between; align-items:center;">
          <button class="btn small" id="addEditItemBtn">+ Add Item</button>
          <div id="editGrandTotal" class="invoice-total-box"></div>
        </div>

        <button class="btn primary save-big" id="saveInvoiceBtn" style="margin-top:10px;">💾 Save</button>
      </div>
    `;
  },

  init(inv) {
    // fill product datalist
    const dl = $("editProductList");
    if (dl) {
      dl.innerHTML = "";
      productModule.products.forEach(p => {
        const opt = document.createElement("option");
        opt.value = p.name;
        dl.appendChild(opt);
      });
    }

    const tbody = $("editItemsBody");
    tbody.innerHTML = "";

    // existing items
    (inv.items || []).forEach(i => {
      const row = document.createElement("tr");
      row.dataset.productId = i.product?.id || "";
      row.dataset.productName = i.product?.name || "";
      row.dataset.unit = i.unit || "";

      const qty   = i.qty ?? 0;
      const price = i.pricePerUnit ?? 0;
      const dval  = i.discountValue ?? 0;
      const dpct  = i.discountPercent ?? 0;
      const gst   = i.gstPercent ?? 0;

      const base = qty * price;
      const disc = dpct > 0 ? (base * dpct / 100) : dval;
      const taxable = base - disc;
      const gstAmt = taxable * gst / 100;
      const total = taxable + gstAmt;

      row.innerHTML = `
        <td>${i.product?.name || "-"}</td>
        <td><input class="qty"   type="number" min="0" value="${qty}"/></td>
        <td><input class="unit"  type="text" value="${i.unit || ""}"/></td>
        <td><input class="price" type="number" min="0" value="${price}"/></td>
        <td><input class="dval"  type="number" min="0" value="${dval}"/></td>
        <td><input class="dpct"  type="number" min="0" value="${dpct}"/></td>
        <td><input class="gst"   type="number" min="0" value="${gst}"/></td>
        <td class="total">${total.toFixed(2)}</td>
        <td><button class="btn small danger removeBtn">✖</button></td>
      `;
      tbody.appendChild(row);
    });

    // attach global delegated handlers for inputs and remove buttons
    this.attachRowHandlers();

    // add new item row
    $("addEditItemBtn").onclick = () => {
      const row = document.createElement("tr");
      row.dataset.productId = "";
      row.dataset.productName = "";
      row.dataset.unit = "";

      row.innerHTML = `
        <td><input class="pname" list="editProductList" placeholder="Product"/></td>
        <td><input class="qty"   type="number" min="0" value="1"/></td>
        <td><input class="unit"  type="text" value=""/></td>
        <td><input class="price" type="number" min="0" value="0"/></td>
        <td><input class="dval"  type="number" min="0" value="0"/></td>
        <td><input class="dpct"  type="number" min="0" value="0"/></td>
        <td><input class="gst"   type="number" min="0" value="0"/></td>
        <td class="total">0.00</td>
        <td><button class="btn small danger removeBtn">✖</button></td>
      `;
      tbody.appendChild(row);

      // auto-fill on product select (one-time hookup for this row)
      const pname = row.querySelector(".pname");
      if (pname) {
        pname.onchange = () => {
          const val = pname.value.trim();
          if (!val) return;
          const p = productModule.findByName(val);
          if (!p) return;

          const qtyEl   = row.querySelector(".qty");
          const unitEl  = row.querySelector(".unit");
          const priceEl = row.querySelector(".price");
          const gstEl   = row.querySelector(".gst");

          if (qtyEl)   qtyEl.value   = qtyEl.value || "1";
          if (unitEl)  unitEl.value  = p.unit || "";
          if (priceEl) priceEl.value = p.price ?? 0;
          if (gstEl)   gstEl.value   = p.gstPercentage ?? 0;

          row.dataset.productId = p.id;
          row.dataset.productName = p.name;
          row.dataset.unit = unitEl?.value || p.unit || "";

          this.recalc();
          this.schedulePreview();
        };
      }

      this.recalc();
      this.schedulePreview();
    };

    $("saveInvoiceBtn").onclick = () => this.save(inv.id);

    // initial recalc & preview
    this.recalc();
    this.schedulePreview();
  },

  attachRowHandlers() {
    const tbody = $("editItemsBody");
    if (!tbody) return;

    // Delegate input events for recalc + preview scheduling
    tbody.addEventListener("input", (e) => {
      const target = e.target;
      if (target.matches(".qty, .unit, .price, .dval, .dpct, .gst, .pname")) {
        this.recalc();
        this.schedulePreview();
      }
    });

    // Delegate remove button clicks
    tbody.addEventListener("click", (e) => {
      const btn = e.target.closest(".removeBtn");
      if (btn) {
        const row = btn.closest("tr");
        if (row) row.remove();
        this.recalc();
        this.schedulePreview();
      }
    });
  },

  schedulePreview() {
    clearTimeout(this.previewTimer);
    this.previewTimer = setTimeout(() => this.runPreview(), 300);
  },

  async runPreview() {
    try {
      // Build lightweight items payload from rows to ask backend for authoritative totals
      const rows = [...document.querySelectorAll("#editItemsBody tr")];
      const items = rows.map(r => {
        const pnameInput = r.querySelector(".pname");
        let productId = r.dataset.productId ? Number(r.dataset.productId) : null;
        if (!productId && pnameInput && pnameInput.value.trim()) {
          const p = productModule.findByName(pnameInput.value.trim());
          if (p) productId = p.id;
        }

        const qty   = Number(r.querySelector(".qty")?.value)  || 0;
        const unit  = (r.querySelector(".unit")?.value || "").trim();
        const price = Number(r.querySelector(".price")?.value)|| 0;
        const dval  = Number(r.querySelector(".dval")?.value) || 0;
        const dpct  = Number(r.querySelector(".dpct")?.value) || 0;
        const gst   = Number(r.querySelector(".gst")?.value)  || 0;

        const isEmpty = !productId && !pnameInput?.value && qty === 0 && price === 0 && dval === 0 && dpct === 0 && gst === 0;
        if (isEmpty) return null;

        return {
          productId,
          qty,
          unit,
          pricePerUnit: price,
          discountType: null,
          discountValue: dval,
          discountPercent: dpct,
          gstPercent: gst
        };
      }).filter(Boolean);

      if (!items.length) return;

      const payload = {
        customerId: null,
        notes: "",
        items,
        invoiceDiscount: null
      };

      // ask backend for preview calculation (authoritative totals)
      if (typeof invoiceModule.previewCalc === "function") {
        const preview = await invoiceModule.previewCalc(payload);
        if (preview?.totals) {
          $("editGrandTotal").textContent = "TOTAL: " + money(preview.totals.grandTotal);
        }
      }
    } catch (err) {
      // don't spam console on small preview failures
      // console.warn("Preview failed", err);
    }
  },

  recalc() {
    let total = 0;
    document.querySelectorAll("#editItemsBody tr").forEach(r => {
      const qty   = Number(r.querySelector(".qty")?.value)  || 0;
      const price = Number(r.querySelector(".price")?.value)|| 0;
      const dval  = Number(r.querySelector(".dval")?.value) || 0;
      const dpct  = Number(r.querySelector(".dpct")?.value) || 0;
      const gst   = Number(r.querySelector(".gst")?.value)  || 0;

      const base = qty * price;
      const disc = dpct > 0 ? (base * dpct / 100) : dval;
      const taxable = base - disc;
      const gstAmt = taxable * gst / 100;
      const line = taxable + gstAmt;

      const cell = r.querySelector(".total");
      if (cell) cell.textContent = line.toFixed(2);

      total += line;
    });

    $("editGrandTotal").textContent = "TOTAL: " + money(total);
  },

  async save(id) {
    const items = [];

    document.querySelectorAll("#editItemsBody tr").forEach(r => {
      const qty   = Number(r.querySelector(".qty")?.value)  || 0;
      const unit  = (r.querySelector(".unit")?.value || "").trim();
      const price = Number(r.querySelector(".price")?.value)|| 0;
      const dval  = Number(r.querySelector(".dval")?.value) || 0;
      const dpct  = Number(r.querySelector(".dpct")?.value) || 0;
      const gst   = Number(r.querySelector(".gst")?.value)  || 0;

      const pnameInput = r.querySelector(".pname");
      let productId = r.dataset.productId ? Number(r.dataset.productId) : null;

      if (!productId && pnameInput && pnameInput.value.trim()) {
        const p = productModule.findByName(pnameInput.value.trim());
        if (p) productId = p.id;
      }

      const isEmpty =
        !productId &&
        !pnameInput?.value &&
        qty === 0 && price === 0 && dval === 0 && dpct === 0 && gst === 0;

      if (isEmpty) return;

      items.push({
        productId,
        qty,
        unit,
        pricePerUnit: price,
        discountType: null,
        discountValue: dval,
        discountPercent: dpct,
        gstPercent: gst
      });
    });

    const payload = {
      customerId: null, // customer NOT changed here
      notes: $("editNotes").value || "",
      invoiceDate: $("editDate").value || null,
      invoiceDiscount: null,
      items
    };

    try {
      await invoiceModule.update(id, payload);
      alert("Invoice updated");
    } catch (err) {
      console.error("Failed to update invoice", err);
      alert("Failed to save invoice.");
    }
  }
};
