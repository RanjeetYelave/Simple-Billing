// js/ui.js
// Updated Create Invoice UI: full line fields, invoice-level discount, live totals

import { $, money, extractId } from "./utils.js";
import { productModule } from "./product.js";
import { customerModule } from "./customer.js";
import { invoiceModule } from "./invoice.js";

export const ui = {

  // render main layout (unchanged except create-invoice area expanded)
  render() {
    $("app").innerHTML = `
      <div class="card">
        <h2>Create Invoice</h2>

        <div style="display:grid;grid-template-columns:1fr 220px;gap:12px;align-items:end">
          <div>
            <label>Customer</label>
            <input id="custInput" placeholder="Type customer name or id:3" list="custList"/>
            <datalist id="custList"></datalist>
          </div>

          <div>
            <label>Invoice-level Discount</label>
            <div style="display:flex;gap:8px">
              <select id="discountType" style="width:40%">
                <option value="PERCENT">%</option>
                <option value="VALUE">₹</option>
              </select>
              <input id="discountValue" type="number" placeholder="0" style="width:60%" />
            </div>
            <div class="small muted">Choose discount type and amount</div>
          </div>
        </div>

        <h3 style="margin-top:12px">Items</h3>

        <table class="invoice-table" id="createItemsTable">
          <thead>
            <tr>
              <th style="width:28%">ITEM</th>
              <th style="width:8%">QTY</th>
              <th style="width:8%">UNIT</th>
              <th style="width:12%">PRICE/UNIT</th>
              <th style="width:12%">AMOUNT (w/o tax)</th>
              <th style="width:8%">DISCOUNT</th>
              <th style="width:8%">TAX %</th>
              <th style="width:10%">TAX AMOUNT</th>
              <th style="width:10%">LINE TOTAL</th>
              <th style="width:6%"></th>
            </tr>
          </thead>
          <tbody id="createItemsBody"></tbody>
        </table>

        <div style="margin-top:10px; display:flex; gap:10px; align-items:center;">
          <button id="createAddItem" class="btn">+ Add Item</button>
          <button id="createClear" class="btn ghost">Clear</button>
        </div>

        <div id="createTotals" style="margin-top:14px;text-align:right">
          <!-- totals filled by JS -->
          <div id="subtotalLine" class="small muted"></div>
          <div id="taxTotalLine" class="small muted"></div>
          <div id="discountLine" class="small muted"></div>
          <div id="grandTotalLine" style="font-weight:700;font-size:18px;margin-top:6px"></div>
        </div>

        <div style="margin-top:12px">
          <label>Notes</label>
          <textarea id="createNotes"></textarea>
        </div>

        <div style="margin-top:12px" class="actions">
          <button id="saveInvBtn" class="btn primary">Create Invoice</button>
        </div>

        <div id="createResult" class="small muted" style="margin-top:10px"></div>
      </div>

      <!-- Keep the rest of the UI (create customer/product, fetch invoices) -->
      <div class="card">
        <h2>Create Customer</h2>
        <input id="c_name" placeholder="Name"/>
        <input id="c_phone" placeholder="Phone"/>
        <input id="c_email" placeholder="Email"/>
        <input id="c_address" placeholder="Address"/>
        <button class="btn primary" id="saveCustBtn">Save Customer</button>
      </div>

      <div class="card">
        <h2>Create Product</h2>
        <input id="p_name" placeholder="Product Name"/>
        <input id="p_price" placeholder="Price" type="number"/>
        <input id="p_unit" placeholder="Unit"/>
        <input id="p_gst" placeholder="GST (%)" type="number"/>
        <button class="btn primary" id="saveProdBtn">Save Product</button>
      </div>

      <div class="card">
        <h2>Fetch Invoices</h2>
        <button class="btn primary" id="fetchAllInvBtn">Fetch All Invoices</button>

        <h3>Search</h3>
        <input id="fetchInvId" placeholder="Invoice ID"/>
        <button class="btn" id="fetchInvIdBtn">Fetch by ID</button>

        <input id="fetchInvCustomer" placeholder="Customer ID" style="margin-top:10px"/>
        <button class="btn" id="fetchInvCustomerBtn">Fetch by Customer</button>

        <div id="invList" style="margin-top:20px"></div>
        <div id="invDetails" style="margin-top:20px"></div>
      </div>
    `;
  },

  populateCustomers() {
    const dl = $("custList");
    dl.innerHTML = "";
    customerModule.customers.forEach(c => {
      const opt = document.createElement("option");
      opt.value = `${c.name} (id:${c.id})`;
      dl.appendChild(opt);
    });
  },

  // -----------------------
  // Create invoice helpers
  // -----------------------
  addCreateItemRow(item) {
    // item optional - { productId, quantity, unit, price, gstPercentage, discountPct (per item optional) }
    const tbody = $("createItemsBody");
    const tr = document.createElement("tr");

    // Product select
    const prodSel = document.createElement("select");
    prodSel.className = "create-prod";
    productModule.products.forEach(p => {
      const opt = document.createElement("option");
      opt.value = p.id;
      opt.text = `${p.name}`;
      prodSel.appendChild(opt);
    });

    // Preselect if passed
    if (item && item.productId) prodSel.value = item.productId;

    // Inputs
    const qty = document.createElement("input"); qty.type = "number"; qty.className = "create-qty"; qty.value = item && item.quantity ? item.quantity : 1; qty.min = 0;
    const unit = document.createElement("input"); unit.type = "text"; unit.className = "create-unit"; unit.value = (item && item.unit) ? item.unit : (productModule.products.find(p=>p.id==prodSel.value)?.unit || '');
    const price = document.createElement("input"); price.type = "number"; price.className = "create-price"; price.step="0.01"; price.value = (item && item.price) ? item.price : (productModule.products.find(p=>p.id==prodSel.value)?.price || 0);
    const amountNoTax = document.createElement("div"); amountNoTax.className = "create-amount-notax"; amountNoTax.innerText = money( (Number(qty.value) * Number(price.value)) || 0 );
    // per-item discount (we keep input but invoice-level discount is primary). Default 0%
    const discountPct = document.createElement("input"); discountPct.type = "number"; discountPct.className = "create-discountPct"; discountPct.step="0.01"; discountPct.value = (item && item.discountPct) ? item.discountPct : 0;
    const taxPct = document.createElement("input"); taxPct.type = "number"; taxPct.className = "create-taxPct"; taxPct.step="0.01"; taxPct.value = (item && item.gstPercentage) ? item.gstPercentage : (productModule.products.find(p=>p.id==prodSel.value)?.gstPercentage || 0);
    const taxAmount = document.createElement("div"); taxAmount.className = "create-taxAmount"; taxAmount.innerText = "₹0.00";
    const lineTotal = document.createElement("div"); lineTotal.className = "create-lineTotal"; lineTotal.innerText = "₹0.00";

    const removeBtn = document.createElement("button"); removeBtn.className = "btn danger small"; removeBtn.innerText = "🗑";
    removeBtn.onclick = () => { tr.remove(); this.recalcCreateTotals(); };

    // Build cells
    const td1 = document.createElement("td"); td1.appendChild(prodSel);
    const td2 = document.createElement("td"); td2.appendChild(qty);
    const td3 = document.createElement("td"); td3.appendChild(unit);
    const td4 = document.createElement("td"); td4.appendChild(price);
    const td5 = document.createElement("td"); td5.appendChild(amountNoTax);
    const td6 = document.createElement("td"); td6.appendChild(discountPct);
    const td7 = document.createElement("td"); td7.appendChild(taxPct);
    const td8 = document.createElement("td"); td8.appendChild(taxAmount);
    const td9 = document.createElement("td"); td9.appendChild(lineTotal);
    const td10 = document.createElement("td"); td10.appendChild(removeBtn);

    tr.append(td1,td2,td3,td4,td5,td6,td7,td8,td9,td10);
    tbody.appendChild(tr);

    // Event handlers
    const recalcRow = () => {
      const q = Number(qty.value) || 0;
      const p = Number(price.value) || 0;
      const dPct = Number(discountPct.value) || 0;
      const tPct = Number(taxPct.value) || 0;

      const amountWithoutTax = q * p;
      const afterDiscount = amountWithoutTax * (1 - (dPct/100));
      const taxAmt = afterDiscount * (tPct/100);
      const total = afterDiscount + taxAmt;

      amountNoTax.innerText = money(amountWithoutTax);
      taxAmount.innerText = money(taxAmt);
      lineTotal.innerText = money(total);

      this.recalcCreateTotals();
    };

    // when product changes, fill unit/price/gst defaults
    prodSel.onchange = () => {
      const prod = productModule.products.find(pr => pr.id == prodSel.value);
      if (prod) {
        unit.value = prod.unit || "";
        price.value = prod.price || 0;
        taxPct.value = prod.gstPercentage || 0;
      }
      recalcRow();
    };

    qty.oninput = recalcRow;
    price.oninput = recalcRow;
    discountPct.oninput = recalcRow;
    taxPct.oninput = recalcRow;

    // initial recalc
    recalcRow();
  },

  // clear create items
  clearCreateItems() {
    $("createItemsBody").innerHTML = "";
    this.recalcCreateTotals();
  },

  // recalc totals for create invoice page
  recalcCreateTotals() {
    const rows = Array.from(document.querySelectorAll("#createItemsBody tr"));
    let subtotalWithoutTax = 0;
    let totalTax = 0;
    let subtotalAfterItemDiscounts = 0;

    rows.forEach(row => {
      const q = Number(row.querySelector(".create-qty").value) || 0;
      const p = Number(row.querySelector(".create-price").value) || 0;
      const dPct = Number(row.querySelector(".create-discountPct").value) || 0;
      const tPct = Number(row.querySelector(".create-taxPct").value) || 0;

      const amountWithoutTax = q * p;
      const afterDiscount = amountWithoutTax * (1 - (dPct/100));
      const taxAmt = afterDiscount * (tPct/100);
      const lineTotal = afterDiscount + taxAmt;

      subtotalWithoutTax += amountWithoutTax;
      totalTax += taxAmt;
      subtotalAfterItemDiscounts += afterDiscount;
    });

    // invoice level discount
    const discType = $("discountType").value;
    const discVal = Number($("discountValue").value) || 0;
    let discountAmount = 0;

    // Discount applies on subtotal AFTER per-item discounts (if any)
    if (discType === "PERCENT") {
      discountAmount = subtotalAfterItemDiscounts * (discVal/100);
    } else {
      discountAmount = discVal;
    }

    const grandTotal = (subtotalAfterItemDiscounts - discountAmount) + totalTax;

    // update UI
    $("subtotalLine").innerText = `Subtotal (without tax): ${money(subtotalWithoutTax)}`;
    $("taxTotalLine").innerText = `Total Tax: ${money(totalTax)}`;
    $("discountLine").innerText = `Discount: - ${money(discountAmount)} (${discType === "PERCENT" ? discVal + '%' : '₹' + discVal})`;
    $("grandTotalLine").innerText = `Grand Total: ${money(grandTotal)}`;

    // store computed totals in element dataset (optional)
    $("createResult").dataset.subtotal = subtotalWithoutTax;
    $("createResult").dataset.tax = totalTax;
    $("createResult").dataset.discountAmount = discountAmount;
    $("createResult").dataset.grandTotal = grandTotal;
  },

  // build payload and submit create-invoice
  async submitCreateInvoice() {
    try {
      const custText = $("custInput").value.trim();
      const customerId = extractId(custText) || Number(prompt("Enter Customer ID")); // fallback

      if (!customerId) return alert("Provide a valid customer (autocomplete id:NN or enter ID when prompted)");

      const notes = $("createNotes").value || "";

      const discType = $("discountType").value;
      const discVal = Number($("discountValue").value) || 0;

      const rows = Array.from(document.querySelectorAll("#createItemsBody tr"));
      if (rows.length === 0) return alert("Add at least one item");

      const items = rows.map(row => {
        const prodId = Number(row.querySelector(".create-prod").value);
        const qty = Number(row.querySelector(".create-qty").value) || 0;
        const unit = row.querySelector(".create-unit").value || "";
        const price = Number(row.querySelector(".create-price").value) || 0;
        const discountPct = Number(row.querySelector(".create-discountPct").value) || 0;
        const gstPercentage = Number(row.querySelector(".create-taxPct").value) || 0;

        const amountWithoutTax = qty * price;
        const afterDiscount = amountWithoutTax * (1 - (discountPct/100));
        const taxAmount = afterDiscount * (gstPercentage/100);
        const lineTotal = afterDiscount + taxAmount;

        return {
          productId: prodId,
          quantity: qty,
          unit: unit,
          price: price,
          discountPct: discountPct,
          gstPercentage: gstPercentage,
          amountWithoutTax: amountWithoutTax,
          taxAmount: taxAmount,
          lineTotal: lineTotal
        };
      });

      // totals (re-use computed)
      const subtotalWithoutTax = Number($("createResult").dataset.subtotal) || 0;
      const totalTax = Number($("createResult").dataset.tax) || 0;
      const discountAmount = Number($("createResult").dataset.discountAmount) || 0;
      const grandTotal = Number($("createResult").dataset.grandTotal) || 0;

      const payload = {
        customerId: customerId,
        notes: notes,
        discount: { type: discType, value: discVal },
        items: items,
        totals: {
          subtotalWithoutTax: subtotalWithoutTax,
          totalTax: totalTax,
          discountAmount: discountAmount,
          grandTotal: grandTotal
        }
      };

      // call backend (invoiceModule.save delegates to api)
      const res = await invoiceModule.save(payload);
      $("createResult").innerText = `Invoice created: ${res.invoiceNumber || res.id}`;
      this.clearCreateItems();
      this.recalcCreateTotals();
    } catch (err) {
      console.error(err);
      alert("Failed to create invoice: " + (err.message || err));
    }
  },

  // -------------------------
  // Other UI pieces (simpler)
  // -------------------------
  // (kept same as before) renderInvoiceList, showInvoiceDetails, etc.
  renderInvoiceList(invoices) {
    const box = $("invList");
    box.innerHTML = "";
    if (!invoices.length) {
      box.innerHTML = "<div>No invoices found.</div>";
      return;
    }
    let html = "";
    invoices.forEach(inv => {
      html += `
        <div class="invoice-list-item">
          <a class="inv-link" data-id="${inv.id}">
            <b>${inv.invoiceNumber}</b><br>
            Customer: ${inv.customer.name}<br>
            Total: ${money(inv.totalAmount)}
          </a>
        </div>
      `;
    });
    box.innerHTML = html;
    [...box.querySelectorAll(".inv-link")].forEach(link => {
      link.onclick = (e) => {
        const id = e.currentTarget.getAttribute("data-id");
        ui.showInvoiceDetails(id);
      };
    });
  },

  // keep previous full editor for editing existing invoice (unchanged)
  async showInvoiceDetails(id) {
    const inv = await invoiceModule.preview(id);

    // Build editable rows for existing invoice (reuse previous full editor)
    let rows = "";
    inv.items.forEach(i => {
      rows += this.buildEditableRow(i);
    });

    $("invDetails").innerHTML = `
      <div class="invoice-container">

        <h2>Invoice Editor — ${inv.invoiceNumber}</h2>

        <div class="invoice-meta-edit">
          <label>Customer</label>
          <select id="editCustomer">
            ${customerModule.customers.map(c => `
              <option value="${c.id}" ${c.id === inv.customer.id ? "selected" : ""}>
                ${c.name}
              </option>
            `).join("")}
          </select>

          <label>Invoice Date</label>
          <input id="editDate" type="datetime-local" value="${inv.invoiceDate.slice(0,16)}"/>

          <label>Notes</label>
          <textarea id="editNotes">${inv.notes || ""}</textarea>
        </div>

        <table class="invoice-table">
          <thead>
            <tr>
              <th>Product</th><th>Qty</th><th>Price</th><th>GST</th><th>Total</th><th></th>
            </tr>
          </thead>
          <tbody id="editItemsBody">${rows}</tbody>
        </table>

        <button id="addItemBtnEdit" class="btn">➕ Add Item</button>

        <div id="editGrandTotal" class="invoice-total-box"></div>

        <button id="saveInvoiceBtn" class="btn primary save-big">💾 Save Invoice</button>

      </div>
    `;

    this.refreshTotals();
    this.bindEditEvents(inv);
  },

  // build rows for full existing editor (same as previous)
  buildEditableRow(item) {
    return `
      <tr data-item-id="${item.id || ""}">
        <td>
          <select class="prodSelect">
            ${productModule.products.map(p => `
              <option value="${p.id}" ${p.id === (item.product?.id || item.productId) ? "selected" : ""}>
                ${p.name}
              </option>
            `).join("")}
          </select>
        </td>
        <td><input class="qtyInput" type="number" min="1" value="${item.quantity}"></td>
        <td><input class="priceInput" type="number" value="${item.price ?? item.product?.price ?? 0}"></td>
        <td><input class="gstInput" type="number" value="${item.gstPercentage ?? item.product?.gstPercentage ?? 0}"></td>
        <td class="lineTotal">0</td>
        <td><button class="btn danger deleteItem">🗑</button></td>
      </tr>
    `;
  },

  // previous edit bindings (unchanged)
  bindEditEvents(inv) {
    const refresh = () => this.refreshTotals();
    document.querySelectorAll("#editItemsBody input, #editItemsBody select")
      .forEach(el => el.oninput = refresh);

    $("addItemBtnEdit").onclick = () => {
      const newItem = {
        id: null,
        product: productModule.products[0],
        quantity: 1,
        price: productModule.products[0].price,
        gstPercentage: productModule.products[0].gstPercentage
      };
      $("editItemsBody").insertAdjacentHTML("beforeend", this.buildEditableRow(newItem));
      this.bindEditEvents(inv);
      refresh();
    };

    document.querySelectorAll(".deleteItem").forEach(btn => {
      btn.onclick = () => { btn.closest("tr").remove(); refresh(); };
    });

    $("saveInvoiceBtn").onclick = async () => {
      const payload = {
        customerId: Number($("editCustomer").value),
        invoiceDate: $("editDate").value,
        notes: $("editNotes").value,
        items: []
      };

      document.querySelectorAll("#editItemsBody tr").forEach(row => {
        payload.items.push({
          itemId: row.getAttribute("data-item-id") || null,
          productId: Number(row.querySelector(".prodSelect").value),
          quantity: Number(row.querySelector(".qtyInput").value),
          price: Number(row.querySelector(".priceInput").value),
          gstPercentage: Number(row.querySelector(".gstInput").value)
        });
      });

      await invoiceModule.update(inv.id, payload);
      alert("Invoice Updated Successfully!");
      this.showInvoiceDetails(inv.id);
    };
  },

  refreshTotals() {
    // used by full editor; keep same as before
    let total = 0;
    document.querySelectorAll("#editItemsBody tr").forEach(row => {
      const qty = Number(row.querySelector(".qtyInput").value);
      const price = Number(row.querySelector(".priceInput").value);
      const gst = Number(row.querySelector(".gstInput").value);
      const base = qty * price;
      const gstAmount = base * (gst / 100);
      const lineTotal = base + gstAmount;
      row.querySelector(".lineTotal").innerText = lineTotal.toFixed(2);
      total += lineTotal;
    });
    $("editGrandTotal").innerHTML = `GRAND TOTAL: ${money(total)}`;
  }
};
