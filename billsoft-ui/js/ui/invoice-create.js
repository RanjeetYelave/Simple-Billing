// js/ui/invoice-create.js
import { $, extractId } from "../utils.js";
import { productModule } from "../product.js";
import { customerModule } from "../customer.js";
import { invoiceModule } from "../invoice.js";
import { totals } from "./totals.js";
import { rowBuilder } from "./row-builder.js";
import { pdfViewer } from "../pdf-viewer.js";

export const invoiceCreate = {

  render() {
    return `
      <div class="card">
        <h2>New Invoice</h2>

        <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px">
          <div>
            <label style="color:white">Customer</label>
            <input id="custInput" list="custList" placeholder="Type customer..." />
            <datalist id="custList"></datalist>
          </div>

          <div>
            <!-- Renamed to Additional Discount (invoice-level) -->
            <label style="color:white">Additional Discount</label>
            <div style="display:flex;gap:8px">
              <select id="discountType">
                <option value="PERCENT">%</option>
                <option value="VALUE">₹</option>
              </select>
              <input id="discountValue" type="number" value="0" />
            </div>
          </div>
        </div>

        <h3 style="color:white;margin-top:14px;">Items</h3>
        <table class="invoice-table">
          <thead>
            <tr>
              <th>Product</th><th>Qty</th><th>Unit</th><th>Price</th><th>Amt</th>
              <th>Disc</th><th>Taxable</th><th>GST%</th><th>GST Amt</th>
              <th>Total</th><th></th>
            </tr>
          </thead>
          <tbody id="createItemsBody"></tbody>
        </table>

        <datalist id="productListGlobal"></datalist>

        <button class="btn" id="addItemBtn" style="margin-top:10px;">+ Add Item</button>

        <div id="createTotals" style="margin-top:14px;text-align:right;">
          <div id="subtotalLine"></div>
          <div id="productDiscountLine"></div> <!-- new: product-level discount total -->
          <div id="taxTotalLine"></div>
          <div id="discountLine"></div> <!-- reused: Additional Discount (invoice-level) -->
          <div id="grandTotalLine"></div>
        </div>

        <label style="color:white;margin-top:10px;">Notes</label>
        <textarea id="createNotes"></textarea>

        <label style="color:white;display:flex;align-items:left;gap:8px;margin-top:10px">
          <input type="checkbox" id="createMarkPaid"/>
          Mark as Paid
        </label>

        <div style="margin-top:10px;display:flex;gap:10px;flex-wrap:wrap;">
          <button class="btn primary save-big" id="saveInvBtn">💾 Save Invoice</button>
          <button class="btn ghost save-big" id="saveInvPdfBtn">💾 Save &amp; PDF</button>
        </div>

        <div id="createResult" class="small muted" style="margin-top:6px;"></div>
      </div>
    `;
  },

  init() {
    // Fill customer list
    const custList = $("custList");
    if (custList) {
      custList.innerHTML = "";
      customerModule.customers.forEach(c => {
        const opt = document.createElement("option");
        opt.value = `${c.name} (id:${c.id})`;
        custList.appendChild(opt);
      });
    }

    // Fill products list
    const prodList = $("productListGlobal");
    if (prodList) {
      prodList.innerHTML = "";
      productModule.products.forEach(p => {
        const opt = document.createElement("option");
        opt.value = p.name;
        prodList.appendChild(opt);
      });
    }

    rowBuilder.clear();
    rowBuilder.addRow();
    totals.recalc();

    $("addItemBtn").onclick = () => {
      rowBuilder.addRow();
      totals.recalc();
    };

    $("discountType").onchange = () => totals.recalc();
    $("discountValue").oninput = () => totals.recalc();

    $("saveInvBtn").onclick = () => this.submit(false);
    $("saveInvPdfBtn").onclick = () => this.submit(true);
  },

  async submit(withPdf) {
    const text = $("custInput").value.trim();
    if (!text) {
      alert("Enter customer name");
      return;
    }

    let customerId = extractId(text);

    // AUTO-CREATE CUSTOMER IF NOT FOUND
    if (!customerId) {
      const existing = customerModule.customers.find(
        c => (c.name || "").toLowerCase() === text.toLowerCase()
      );

      if (existing) {
        customerId = existing.id;
      } else {
        const created = await customerModule.create({
          name: text,
          phone: "",
          email: "",
          address: ""
        });

        if (typeof customerModule.list === "function") {
          await customerModule.list(); // refresh cache
        }

        customerId = created.id;
      }
    }

    const rows = [...document.querySelectorAll("#createItemsBody tr")];
    if (!rows.length) {
      alert("Add at least one item.");
      return;
    }

    const items = await rowBuilder.buildPayloadItems(rows);
    if (!items.length) {
      alert("No valid items to save.");
      return;
    }

    // buildFinalPayload now includes item-level and additional/invoice discount
    const payload = totals.buildFinalPayload(
      items,
      customerId,
      $("createNotes").value || ""
    );

    // set paid flag
    payload.paid = $("createMarkPaid").checked;

    const created = await invoiceModule.save(payload);

    $("createResult").textContent =
      `Invoice created: ${created.invoiceNumber || created.id}`;

    if (withPdf && created && created.id) {
      try {
        const blob = await invoiceModule.pdf(created.id);
        const fname = `invoice-${created.invoiceNumber || created.id}.pdf`;
        pdfViewer.open(blob, fname, `Invoice ${created.invoiceNumber || created.id}`);
      } catch (e) {
        console.error("PDF fetch failed", e);
        alert("Invoice saved, but PDF could not be loaded.");
      }
    }

    // reset form
    rowBuilder.clear();
    rowBuilder.addRow();
    totals.recalc();
    $("createMarkPaid").checked = false;
  }
};
