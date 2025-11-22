// js/ui/invoice-create.js
import { $, extractId } from "../utils.js";
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
            <label style="color:white">Customer</label>
            <input id="custInput" list="custList" placeholder="Type customer..." />
            <datalist id="custList"></datalist>
          </div>

          <div>
            <label style="color:white">Invoice Discount</label>
            <div style="display:flex;gap:8px">
              <select id="discountType">
                <option value="PERCENT">%</option>
                <option value="VALUE">₹</option>
              </select>
              <input id="discountValue" type="number" value="0" />
            </div>
          </div>
        </div>

        <h3 style="color:white">Items</h3>
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

        <datalist id="productListGlobal"></datalist>

        <button class="btn" id="addItemBtn">+ Add Item</button>

        <div id="createTotals" style="margin-top:14px;text-align:right;">
          <div id="subtotalLine"></div>
          <div id="taxTotalLine"></div>
          <div id="discountLine"></div>
          <div id="grandTotalLine"></div>
        </div>

        <label style="color:white">Notes</label>
        <textarea id="createNotes"></textarea>

        <label style="color:white;display:flex;align-items:center;gap:8px;margin-top:10px">
          <input type="checkbox" id="createMarkPaid"/>
          Mark as Paid
        </label>

        <button class="btn primary save-big" id="saveInvBtn">💾 Create Invoice</button>
        <div id="createResult" class="small muted"></div>
      </div>
    `;
  },

  init() {
    // Fill customer list
    const custList = $("custList");
    custList.innerHTML = "";
    customerModule.customers.forEach(c => {
      const opt = document.createElement("option");
      opt.value = `${c.name} (id:${c.id})`;
      custList.appendChild(opt);
    });

    // Fill products
    const prodList = $("productListGlobal");
    prodList.innerHTML = "";
    productModule.products.forEach(p => {
      const opt = document.createElement("option");
      opt.value = p.name;
      prodList.appendChild(opt);
    });

    rowBuilder.clear();
    rowBuilder.addRow();
    totals.recalc();

    $("addItemBtn").onclick = () => {
      rowBuilder.addRow();
      totals.recalc();
    };

    $("discountType").onchange = totals.recalc;
    $("discountValue").oninput = totals.recalc;

    $("saveInvBtn").onclick = () => this.submit();
  },

  async submit() {
    const text = $("custInput").value.trim();
    let customerId = extractId(text);

    // NEW LOGIC: Create customer if not found
    if (!customerId) {
      const existing = customerModule.customers.find(
        c => c.name.toLowerCase() === text.toLowerCase()
      );

      if (existing) {
        customerId = existing.id;
      } else {
        // auto-create silently
        const created = await customerModule.create({
          name: text,
          phone: "",
          email: "",
          address: ""
        });

        customerModule.customers.push(created);
        customerId = created.id;
      }
    }

    const rows = [...document.querySelectorAll("#createItemsBody tr")];
    const items = await rowBuilder.buildPayloadItems(rows);

    const payload = totals.buildFinalPayload(
      items,
      customerId,
      $("createNotes").value
    );

    payload.paid = $("createMarkPaid").checked;

    const created = await invoiceModule.save(payload);

    $("createResult").textContent = `Invoice created: ${created.invoiceNumber}`;

    rowBuilder.clear();
    rowBuilder.addRow();
    totals.recalc();
  }
};
