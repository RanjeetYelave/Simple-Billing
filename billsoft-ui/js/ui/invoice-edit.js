// js/ui/invoice-edit.js
import { invoiceModule } from "../invoice.js";
import { productModule } from "../product.js";
import { $, money } from "../utils.js";

export const invoiceEdit = {

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
          <div>
            <div id="editProductDiscount" class="small muted" style="text-align:right;margin-bottom:6px;"></div>
            <div id="editGrandTotal" class="invoice-total-box"></div>
          </div>
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

    // existing items
    inv.items.forEach(i => {
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
        <td><input class="gst"   type="number" min="0" value="${gst}"/></td>
        <td class="total">${total.toFixed(2)}</td>
        <td><button class="btn small danger removeBtn">✖</button></td>
      `;
      tbody.appendChild(row);
    });

    // recalc on change
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
        <td><input class="gst"   type="number" min="0" value="0"/></td>
        <td class="total">0.00</td>
        <td><button class="btn small danger removeBtn">✖</button></td>
      `;
      tbody.appendChild(row);

      // auto-fill on product select
      const pname = row.querySelector(".pname");
      if (pname) {
        pname.onchange = () => {
          const val = pname.value.trim();
          if (!val) return;
          const p = productModule.findByName(val);
          if (!p) return;

          const qty   = row.querySelector(".qty");
          const unit  = row.querySelector(".unit");
          const price = row.querySelector(".price");
          const gst   = row.querySelector(".gst");

          if (qty)   qty.value   = qty.value || "1";
          if (unit)  unit.value  = p.unit || "";
          if (price) price.value = p.price ?? 0;
          if (gst)   gst.value   = p.gstPercentage ?? 0;

          row.dataset.productId = p.id;
          row.dataset.productName = p.name;
          row.dataset.unit = unit?.value || p.unit || "";

          this.recalc();
        };
      }

      this.attachRowHandlers();
      this.recalc();
    };

    $("saveInvoiceBtn").onclick = () => this.save(inv.id);

    this.recalc();
  },

  attachRowHandlers() {
    const tbody = $("editItemsBody");
    if (!tbody) return;

    tbody.querySelectorAll(".qty,.unit,.price,.dval,.gst").forEach(inp => {
      inp.oninput = () => this.recalc();
    });

    tbody.querySelectorAll(".removeBtn").forEach(btn => {
      btn.onclick = () => {
        btn.closest("tr")?.remove();
        this.recalc();
      };
    });
  },

  recalc() {
    let total = 0;
    let productDiscount = 0;

    document.querySelectorAll("#editItemsBody tr").forEach(r => {
      const qty   = Number(r.querySelector(".qty")?.value)  || 0;
      const price = Number(r.querySelector(".price")?.value)|| 0;
      const dval  = Number(r.querySelector(".dval")?.value) || 0;
      const dpct  = Number(r.querySelector(".dpct")?.value) || 0; // note: dpct may not exist in rows, treated as 0
      const gst   = Number(r.querySelector(".gst")?.value)  || 0;

      const base = qty * price;
      const disc = dpct > 0 ? (base * dpct / 100) : dval;
      const taxable = base - disc;
      const gstAmt = taxable * gst / 100;
      const line = taxable + gstAmt;

      const cell = r.querySelector(".total");
      if (cell) cell.textContent = line.toFixed(2);

      total += line;
      productDiscount += disc;
    });

    $("editProductDiscount").textContent = "Product Discount: -" + money(productDiscount);
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

      // skip totally empty rows
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
      customerId: null, // customer NOT changed
      notes: $("editNotes").value || "",
      invoiceDate: $("editDate").value || null,
      invoiceDiscount: null,
      items
    };

    await invoiceModule.update(id, payload);
    alert("Invoice updated");
  }
};
