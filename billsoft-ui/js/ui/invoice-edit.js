// ui/invoice-edit.js
import { invoiceModule } from "../invoice.js";
import { productModule } from "../product.js";
import { $, money } from "../utils.js";

export const invoiceEdit = {

  async open(id) {
    const inv = await invoiceModule.preview(id);
    if (!inv) return alert("Invoice not found");

    $("invDetails").innerHTML = this.render(inv);
    this.init(inv);
  },

  render(inv) {
    return `
      <div class="invoice-container">
        <h2>Edit ${inv.invoiceNumber}</h2>

        <label>Customer</label>
        <select id="editCustomer">
          <option value="${inv.customer.id}">${inv.customer.name}</option>
        </select>

        <label>Date</label>
        <input id="editDate" type="datetime-local" value="${inv.invoiceDate.slice(0,16)}"/>

        <table class="invoice-table">
          <thead>
            <tr><th>Product</th><th>Qty</th><th>Price</th><th>GST</th><th>Total</th></tr>
          </thead>
          <tbody id="editItemsBody"></tbody>
        </table>

        <button class="btn primary save-big" id="saveInvoiceBtn">Save</button>

        <div id="editGrandTotal" class="invoice-total-box"></div>
      </div>
    `;
  },

  init(inv) {
    const tbody = $("editItemsBody");

    inv.items.forEach(i => {
      const row = document.createElement("tr");
      const gst = i.gstPercent;
      const base = i.qty * i.pricePerUnit;
      const total = base + (base * gst / 100);

      row.innerHTML = `
        <td>${i.product.name}</td>
        <td><input class="qty" type="number" value="${i.qty}"/></td>
        <td><input class="price" type="number" value="${i.pricePerUnit}"/></td>
        <td><input class="gst" type="number" value="${i.gstPercent}"/></td>
        <td class="total">${total.toFixed(2)}</td>
      `;
      tbody.appendChild(row);
    });

    document.querySelectorAll(".qty,.price,.gst").forEach(i => {
      i.oninput = () => this.recalc();
    });

    $("saveInvoiceBtn").onclick = () => this.save(inv.id);

    this.recalc();
  },

  recalc() {
    let total = 0;
    document.querySelectorAll("#editItemsBody tr").forEach(r => {
      const qty = Number(r.querySelector(".qty").value);
      const price = Number(r.querySelector(".price").value);
      const gst = Number(r.querySelector(".gst").value);

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
      const qty = Number(r.querySelector(".qty").value);
      const price = Number(r.querySelector(".price").value);
      const gst = Number(r.querySelector(".gst").value);

      const base = qty * price;
      const gstAmt = base * gst / 100;
      const total = base + gstAmt;

      items.push({
        qty,
        unit: "",
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

    await invoiceModule.update(id, {
      customerId: Number($("editCustomer").value),
      notes: "",
      invoiceDate: $("editDate").value,
      items
    });

    alert("Invoice updated");
  }
};
