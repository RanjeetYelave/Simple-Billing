// js/ui/invoice-edit.js
import { invoiceModule } from "../invoice.js";
import { customerModule } from "../customer.js";
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
    // Build customer dropdown from cached customers
    const custOptions = customerModule.customers.map(c => `
      <option value="${c.id}" ${c.id === inv.customer.id ? "selected" : ""}>
        ${c.name}
      </option>
    `).join("");

    return `
      <div class="invoice-container">
        <h2>Edit ${inv.invoiceNumber}</h2>

        <div class="invoice-meta-edit">
          <label>Customer</label>
          <select id="editCustomer">
            ${custOptions}
          </select>

          <label>Date</label>
          <input id="editDate" type="datetime-local"
                 value="${inv.invoiceDate ? inv.invoiceDate.slice(0,16) : ""}"/>

          <label>Notes</label>
          <textarea id="editNotes">${inv.notes || ""}</textarea>
        </div>

        <table class="invoice-table">
          <thead>
            <tr>
              <th>Product</th>
              <th>Qty</th>
              <th>Price</th>
              <th>GST%</th>
              <th>Total</th>
            </tr>
          </thead>
          <tbody id="editItemsBody"></tbody>
        </table>

        <div style="margin-top:10px; display:flex; justify-content:space-between; align-items:center;">
          <button class="btn small" id="addEditItemBtn">+ Add Item</button>
          <div id="editGrandTotal" class="invoice-total-box"></div>
        </div>

        <button class="btn primary save-big" id="saveInvoiceBtn">💾 Save</button>
      </div>
    `;
  },

  init(inv) {
    const tbody = $("editItemsBody");

    // Render each existing invoice item as editable row
    inv.items.forEach(i => {
      const row = document.createElement("tr");

      // Keep references so we can send proper payload later
      row.dataset.itemId = i.id;
      row.dataset.productId = i.product?.id;
      row.dataset.unit = i.unit || "";

      const gst = i.gstPercent ?? 0;
      const base = (i.qty ?? 0) * (i.pricePerUnit ?? 0);
      const total = base + (base * gst / 100);

      row.innerHTML = `
        <td>${i.product?.name || "Product"}</td>
        <td><input class="qty" type="number" min="0" value="${i.qty ?? 0}"/></td>
        <td><input class="price" type="number" min="0" value="${i.pricePerUnit ?? 0}"/></td>
        <td><input class="gst" type="number" min="0" value="${gst}"/></td>
        <td class="total">${total.toFixed(2)}</td>
      `;
      tbody.appendChild(row);
    });

    // Add listeners for recalculation
    document.querySelectorAll("#editItemsBody .qty, #editItemsBody .price, #editItemsBody .gst")
      .forEach(input => {
        input.oninput = () => this.recalc();
      });

    // Add new empty item (will be treated as new row)
    $("addEditItemBtn").onclick = () => {
      const row = document.createElement("tr");

      // New row has no existing itemId
      row.dataset.itemId = "";
      row.dataset.productId = "";
      row.dataset.unit = "";

      row.innerHTML = `
        <td><input class="pname" placeholder="Product name (not changeable in backend yet)" /></td>
        <td><input class="qty" type="number" min="0" value="1"/></td>
        <td><input class="price" type="number" min="0" value="0"/></td>
        <td><input class="gst" type="number" min="0" value="18"/></td>
        <td class="total">0.00</td>
      `;

      tbody.appendChild(row);

      row.querySelectorAll(".qty,.price,.gst").forEach(input => {
        input.oninput = () => this.recalc();
      });

      this.recalc();
    };

    // Save button
    $("saveInvoiceBtn").onclick = () => this.save(inv.id);

    // Initial total
    this.recalc();
  },

  recalc() {
    let total = 0;
    document.querySelectorAll("#editItemsBody tr").forEach(r => {
      const qty = Number(r.querySelector(".qty").value) || 0;
      const price = Number(r.querySelector(".price").value) || 0;
      const gst = Number(r.querySelector(".gst").value) || 0;

      const base = qty * price;
      const line = base + (base * gst / 100);

      r.querySelector(".total").textContent = line.toFixed(2);
      total += line;
    });

    $("editGrandTotal").textContent = "TOTAL: " + money(total);
  },

  async save(id) {
    const items = [];

    document.querySelectorAll("#editItemsBody tr").forEach(r => {
      const qty = Number(r.querySelector(".qty").value) || 0;
      const price = Number(r.querySelector(".price").value) || 0;
      const gst = Number(r.querySelector(".gst").value) || 0;

      const base = qty * price;
      const gstAmt = base * gst / 100;
      const total = base + gstAmt;

      const itemId = r.dataset.itemId ? Number(r.dataset.itemId) : null;
      const productId = r.dataset.productId ? Number(r.dataset.productId) : null;
      const unit = r.dataset.unit || "";

      items.push({
        itemId,              // 👈 so backend knows which row to update
        productId,           // 👈 keep product fixed for now
        qty,
        unit,
        pricePerUnit: price,
        amountWithoutTax: base,
        discountType: null,
        discountValue: 0,
        discountPercent: 0,
        taxableAmount: base,
        gstPercent: gst,
        gstAmount: gstAmt,
        lineTotal: total
      });
    });

    const payload = {
      customerId: Number($("editCustomer").value),
      notes: $("editNotes").value || "",
      invoiceDate: $("editDate").value,
      items
    };

    await invoiceModule.update(id, payload);
    alert("Invoice updated");
  }
};
