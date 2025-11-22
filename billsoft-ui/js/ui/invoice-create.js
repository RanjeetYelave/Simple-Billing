// ui/invoice-create.js
import { $, money, extractId } from "../utils.js";
import { productModule } from "../product.js";
import { customerModule } from "../customer.js";
import { invoiceModule } from "../invoice.js";
import { totals } from "./totals.js";
import { rowBuilder } from "./row-builder.js";

export const invoiceCreate = {

  render() {
    return `
      <div class="card">
        <h2>New Invoice</h2>

        <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px">
          <div>
            <label>Customer</label>
            <input id="custInput" list="custList" placeholder="Type customer..." />
            <datalist id="custList"></datalist>
          </div>
          <div>
            <label>Invoice Discount</label>
            <div style="display:flex;gap:8px">
              <select id="discountType"><option value="PERCENT">%</option><option value="VALUE">₹</option></select>
              <input id="discountValue" type="number" value="0" />
            </div>
          </div>
        </div>

        <h3>Items</h3>
        <table class="invoice-table">
          <thead>
            <tr>
              <th>Product</th><th>Qty</th><th>Unit</th><th>Price</th><th>Amt</th>
              <th>Disc</th><th>D%</th><th>Taxable</th><th>GST%</th><th>GST Amt</th>
              <th>Total</th><th></th>
            </tr>
          </thead>
          <tbody id="createItemsBody"></tbody>
        </table>

        <button class="btn" id="addItemBtn">+ Add Item</button>

        <div id="createTotals" style="margin-top:14px;text-align:right;">
          <div id="subtotalLine"></div>
          <div id="taxTotalLine"></div>
          <div id="discountLine"></div>
          <div id="grandTotalLine" style="font-size:18px;font-weight:700"></div>
        </div>

        <label>Notes</label>
        <textarea id="createNotes"></textarea>

        <button class="btn primary save-big" id="saveInvBtn">💾 Create Invoice</button>
        <div id="createResult" class="small muted"></div>
      </div>`;
  },

  init() {
    rowBuilder.clear();
    rowBuilder.addRow();
    totals.recalc();

    document.getElementById("addItemBtn").onclick = () => {
      rowBuilder.addRow();
      totals.recalc();
    };

    document.getElementById("discountType").onchange = totals.recalc;
    document.getElementById("discountValue").oninput = totals.recalc;

    document.getElementById("saveInvBtn").onclick = () => this.submit();
  },

  async submit() {
    const custText = $("custInput").value;
    const customerId = extractId(custText);
    if (!customerId) return alert("Select valid customer");

    const rows = [...document.querySelectorAll("#createItemsBody tr")];
    if (!rows.length) return alert("Add at least one item");

    const items = rowBuilder.buildPayloadItems(rows);

    const payload = totals.buildFinalPayload(items, customerId, $("createNotes").value);

    const created = await invoiceModule.save(payload);

    $("createResult").textContent = `Invoice created: ${created.invoiceNumber}`;
    rowBuilder.clear();
    rowBuilder.addRow();
    totals.recalc();
  }
};
