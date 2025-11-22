// js/ui.js
import { $, money, extractId } from "./utils.js";
import { productModule } from "./product.js";
import { customerModule } from "./customer.js";
import { invoiceModule } from "./invoice.js";

export const ui = {

  /* ------------------------------------------------------------
     RENDER SHELL (SIDEBAR + VIEWS)
  ------------------------------------------------------------- */
  render() {
    $("app").innerHTML = `
      <div class="layout">
        <aside class="sidebar">
          <div class="brand">
            <div class="brand-badge">B</div>
            <span>Billsoft</span>
          </div>

          <div class="nav-section-title">Navigation</div>
          <div class="nav-list">
            <button class="nav-item active" data-view="invoiceCreate">
              <span class="icon">🧾</span>
              <span>Create Invoice</span>
            </button>
            <button class="nav-item" data-view="invoices">
              <span class="icon">📚</span>
              <span>Invoices</span>
            </button>
            <button class="nav-item" data-view="customers">
              <span class="icon">👤</span>
              <span>Customers</span>
            </button>
            <button class="nav-item" data-view="products">
              <span class="icon">📦</span>
              <span>Products</span>
            </button>
          </div>

          <div class="sidebar-footer">
            <div>Local Dev • http://localhost:8080</div>
          </div>
        </aside>

        <main class="main">
          <div class="main-header">
            <div>
              <h1 id="mainTitle">Create Invoice</h1>
              <div class="subtitle" id="mainSubtitle">Quickly create and save multi-item invoices.</div>
            </div>
          </div>

          <!-- CREATE INVOICE VIEW -->
          <section id="view-invoiceCreate" class="view active">
            <div class="card">
              <h2>New Invoice</h2>

              <div style="display:grid;grid-template-columns:1.4fr 0.8fr;gap:12px;align-items:end">
                <div>
                  <label>Customer</label>
                  <input id="custInput" placeholder="Type customer name or id:3" list="custList"/>
                  <datalist id="custList"></datalist>
                  <div class="small muted">Example: "Ranjeet (id:1)" or just "1"</div>
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
                  <div class="small muted">Applied after per-line discounts.</div>
                </div>
              </div>

              <h3 style="margin-top:12px">Items</h3>

              <table class="invoice-table" id="createItemsTable">
                <thead>
                  <tr>
                    <th style="width:22%">ITEM</th>
                    <th style="width:6%">QTY</th>
                    <th style="width:8%">UNIT</th>
                    <th style="width:10%">PRICE/UNIT</th>
                    <th style="width:10%">AMOUNT</th>
                    <th style="width:8%">DISC</th>
                    <th style="width:6%">DISC%</th>
                    <th style="width:8%">TAXABLE</th>
                    <th style="width:6%">GST%</th>
                    <th style="width:8%">GST AMT</th>
                    <th style="width:8%">LINE TOTAL</th>
                    <th style="width:4%"></th>
                  </tr>
                </thead>
                <tbody id="createItemsBody"></tbody>
              </table>

              <div style="margin-top:10px; display:flex; gap:10px; align-items:center;">
                <button id="createAddItem" class="btn">+ Add Item</button>
                <button id="createClear" class="btn ghost">Clear</button>
              </div>

              <div id="createTotals" style="margin-top:14px;text-align:right">
                <div id="subtotalLine" class="small muted"></div>
                <div id="taxTotalLine" class="small muted"></div>
                <div id="discountLine" class="small muted"></div>
                <div id="grandTotalLine" style="font-weight:700;font-size:18px;margin-top:6px"></div>
              </div>

              <div style="margin-top:12px">
                <label>Notes</label>
                <textarea id="createNotes" placeholder="Optional notes for this invoice..."></textarea>
              </div>

              <div style="margin-top:12px" class="actions">
                <button id="saveInvBtn" class="btn primary save-big">💾 Create Invoice</button>
              </div>

              <div id="createResult" class="small muted" style="margin-top:10px"></div>
            </div>
          </section>

          <!-- INVOICES VIEW -->
          <section id="view-invoices" class="view">
            <div class="card">
              <h2>Invoices</h2>
              <div style="display:flex;gap:10px;flex-wrap:wrap;align-items:flex-end">
                <div>
                  <label>Invoice ID</label>
                  <input id="fetchInvId" placeholder="e.g. 1" />
                </div>
                <button class="btn" id="fetchInvIdBtn">Fetch by ID</button>

                <div>
                  <label>Customer ID</label>
                  <input id="fetchInvCustomer" placeholder="e.g. 1" />
                </div>
                <button class="btn" id="fetchInvCustomerBtn">Fetch by Customer</button>

                <button class="btn ghost" id="fetchAllInvBtn">Refresh All</button>
              </div>

              <div id="invList" style="margin-top:16px"></div>
              <div id="invDetails" style="margin-top:16px"></div>
            </div>
          </section>

          <!-- CUSTOMERS VIEW -->
          <section id="view-customers" class="view">
            <div class="card">
              <h2>Customers</h2>
              <div style="display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px">
                <div>
                  <h3>Add / Update Customer</h3>
                  <label>Name</label>
                  <input id="c_name" placeholder="Full name" />
                  <label>Phone</label>
                  <input id="c_phone" placeholder="Phone" />
                  <label>Email</label>
                  <input id="c_email" placeholder="Email" />
                  <label>Address</label>
                  <input id="c_address" placeholder="Address" />
                  <button class="btn primary" id="saveCustBtn">Save Customer</button>
                </div>
                <div>
                  <h3>Existing Customers</h3>
                  <button class="btn ghost" id="loadCustBtn">Reload</button>
                  <div id="custListBox" style="margin-top:10px"></div>
                </div>
              </div>
            </div>
          </section>

          <!-- PRODUCTS VIEW -->
          <section id="view-products" class="view">
            <div class="card">
              <h2>Products</h2>
              <div style="display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px">
                <div>
                  <h3>Add Product</h3>
                  <label>Product Name</label>
                  <input id="p_name" placeholder="Product Name" />
                  <label>Price</label>
                  <input id="p_price" placeholder="Price" type="number" />
                  <label>Unit</label>
                  <input id="p_unit" placeholder="Unit (pcs, kg, etc.)" />
                  <label>GST (%)</label>
                  <input id="p_gst" placeholder="GST (%)" type="number" />
                  <button class="btn primary" id="saveProdBtn">Save Product</button>
                </div>
                <div>
                  <h3>Existing Products</h3>
                  <button class="btn ghost" id="loadProdBtn">Reload</button>
                  <div id="prodListBox" style="margin-top:10px"></div>
                </div>
              </div>
            </div>
          </section>

        </main>
      </div>
    `;
  },

  /* ------------------------------------------------------------
     VIEW SWITCHING
  ------------------------------------------------------------- */
  switchView(viewName) {
    // sections
    document.querySelectorAll(".view").forEach(v => v.classList.remove("active"));
    const active = document.getElementById(`view-${viewName}`);
    if (active) active.classList.add("active");

    // nav buttons
    document.querySelectorAll(".nav-item").forEach(btn => {
      btn.classList.toggle("active", btn.dataset.view === viewName);
    });

    // titles
    const title = $("mainTitle");
    const subtitle = $("mainSubtitle");
    if (!title || !subtitle) return;

    if (viewName === "invoiceCreate") {
      title.textContent = "Create Invoice";
      subtitle.textContent = "Quickly create and save multi-item invoices.";
    } else if (viewName === "invoices") {
      title.textContent = "Invoices";
      subtitle.textContent = "Browse, open and edit existing invoices.";
    } else if (viewName === "customers") {
      title.textContent = "Customers";
      subtitle.textContent = "Manage customers used while creating invoices.";
    } else if (viewName === "products") {
      title.textContent = "Products";
      subtitle.textContent = "Manage products with price and GST.";
    }
  },

  /* ------------------------------------------------------------
     CUSTOMER AUTOCOMPLETE
  ------------------------------------------------------------- */
  populateCustomers() {
    const dl = $("custList");
    if (!dl) return;
    dl.innerHTML = "";
    customerModule.customers.forEach(c => {
      const opt = document.createElement("option");
      opt.value = `${c.name} (id:${c.id})`;
      dl.appendChild(opt);
    });
  },

  /* ------------------------------------------------------------
     PRODUCT AUTOCOMPLETE (GLOBAL DATALIST)
  ------------------------------------------------------------- */
  populateProductsDatalist() {
    let dl = document.getElementById("productListGlobal");
    if (!dl) {
      dl = document.createElement("datalist");
      dl.id = "productListGlobal";
      document.body.appendChild(dl);
    }
    dl.innerHTML = "";
    productModule.products.forEach(p => {
      const o = document.createElement("option");
      o.value = p.name;
      dl.appendChild(o);
    });
  },

  /* ------------------------------------------------------------
     CREATE INVOICE: ROW MANAGEMENT
  ------------------------------------------------------------- */
  addCreateItemRow() {
    const tbody = document.querySelector("#createItemsBody");
    if (!tbody) return;

    const row = document.createElement("tr");
    row.className = "item-row";

    row.innerHTML = `
      <td>
        <input list="productListGlobal" class="ci-product-input" placeholder="Type product name" />
      </td>
      <td><input type="number" class="ci-qty" value="1" min="0" /></td>
      <td><input type="text" class="ci-unit" placeholder="Unit" /></td>
      <td><input type="number" class="ci-price" value="0" min="0" step="0.01" /></td>
      <td class="ci-amt-no-tax numeric">0</td>
      <td><input type="number" class="ci-disc-value" value="0" min="0" step="0.01" /></td>
      <td><input type="number" class="ci-disc-percent" value="0" min="0" max="100" step="0.01" /></td>
      <td class="ci-taxable numeric">0</td>
      <td><input type="number" class="ci-gst" value="18" min="0" step="0.01" /></td>
      <td class="ci-gst-amt numeric">0</td>
      <td class="ci-line-total numeric">0</td>
      <td><button class="btn danger small ci-remove">×</button></td>
    `;

    tbody.appendChild(row);

    const prodInput = row.querySelector(".ci-product-input");
    const qtyInput = row.querySelector(".ci-qty");
    const unitInput = row.querySelector(".ci-unit");
    const priceInput = row.querySelector(".ci-price");
    const discVal = row.querySelector(".ci-disc-value");
    const discPct = row.querySelector(".ci-disc-percent");
    const gstInput = row.querySelector(".ci-gst");
    const removeBtn = row.querySelector(".ci-remove");

    const tryAutofill = () => {
      const name = prodInput.value?.trim();
      if (!name) return;
      const prod = productModule.findByName(name);
      if (prod) {
        unitInput.value = prod.unit || "";
        priceInput.value = prod.price ?? 0;
        gstInput.value = prod.gstPercentage ?? 0;
        this.recalcCreateTotals();
      }
    };

    prodInput.addEventListener("change", tryAutofill);
    prodInput.addEventListener("blur", tryAutofill);
    prodInput.addEventListener("keydown", (e) => {
      if (e.key === "Enter") {
        tryAutofill();
        e.preventDefault();
      }
    });

    [qtyInput, priceInput, discVal, discPct, gstInput].forEach(el => {
      el.addEventListener("input", () => this.recalcCreateTotals());
      el.addEventListener("change", () => this.recalcCreateTotals());
    });

    removeBtn.onclick = () => {
      row.remove();
      this.recalcCreateTotals();
    };

    this.recalcCreateTotals();
  },

  clearCreateItems() {
    const tbody = document.querySelector("#createItemsBody");
    if (tbody) tbody.innerHTML = "";
  },

  recalcCreateTotals() {
    const rows = [...document.querySelectorAll("#createItemsBody tr")];

    let subtotalWithoutTax = 0;
    let totalTax = 0;
    let subtotalAfterItemDiscounts = 0;

    rows.forEach(row => {
      const qty = Number(row.querySelector(".ci-qty").value) || 0;
      const price = Number(row.querySelector(".ci-price").value) || 0;
      const discValue = Number(row.querySelector(".ci-disc-value").value) || 0;
      const discPct = Number(row.querySelector(".ci-disc-percent").value) || 0;
      const gstPct = Number(row.querySelector(".ci-gst").value) || 0;

      const amtNoTax = qty * price;
      row.querySelector(".ci-amt-no-tax").innerText = amtNoTax.toFixed(2);

      let discount = 0;
      if (discPct > 0) discount = (amtNoTax * discPct) / 100;
      else discount = discValue;

      const taxable = Math.max(0, amtNoTax - discount);
      row.querySelector(".ci-taxable").innerText = taxable.toFixed(2);

      const gstAmt = (taxable * gstPct) / 100;
      row.querySelector(".ci-gst-amt").innerText = gstAmt.toFixed(2);

      const lineTotal = taxable + gstAmt;
      row.querySelector(".ci-line-total").innerText = lineTotal.toFixed(2);

      subtotalWithoutTax += amtNoTax;
      totalTax += gstAmt;
      subtotalAfterItemDiscounts += taxable;
    });

    const invDiscType = $("discountType") ? $("discountType").value : "PERCENT";
    const invDiscVal = $("discountValue") ? (Number($("discountValue").value) || 0) : 0;
    let invoiceDiscountAmount = 0;
    if (invDiscType === "PERCENT") invoiceDiscountAmount = subtotalAfterItemDiscounts * (invDiscVal / 100);
    else invoiceDiscountAmount = invDiscVal;

    const finalGrand = (subtotalAfterItemDiscounts - invoiceDiscountAmount) + totalTax;

    if ($("subtotalLine")) $("subtotalLine").innerText = `Subtotal (without tax): ${money(subtotalWithoutTax)}`;
    if ($("taxTotalLine")) $("taxTotalLine").innerText = `Total Tax: ${money(totalTax)}`;
    if ($("discountLine")) $("discountLine").innerText =
      `Discount: - ${money(invoiceDiscountAmount)} (${invDiscType === "PERCENT" ? invDiscVal + '%' : '₹' + invDiscVal})`;
    if ($("grandTotalLine")) $("grandTotalLine").innerText = `Grand Total: ${money(finalGrand)}`;

    if ($("createResult")) {
      $("createResult").dataset.subtotal = subtotalWithoutTax;
      $("createResult").dataset.tax = totalTax;
      $("createResult").dataset.discountAmount = invoiceDiscountAmount;
      $("createResult").dataset.grandTotal = finalGrand;
    }
  },

  /* ------------------------------------------------------------
     SUBMIT INVOICE  (keeps existing backend contract)
  ------------------------------------------------------------- */
  async submitCreateInvoice() {
    try {
      const custText = $("custInput").value.trim();
      const customerId = extractId(custText) || Number(prompt("Enter Customer ID"));
      if (!customerId) return alert("Provide a valid customer (autocomplete id:NN or enter ID when prompted)");

      const notes = $("createNotes").value || "";

      const discType = $("discountType").value;
      const discVal = Number($("discountValue").value) || 0;

      const rows = [...document.querySelectorAll("#createItemsBody tr")];
      if (rows.length === 0) return alert("Add at least one item");

      this.populateProductsDatalist(); // ensure datalist is current

      const items = [];
      for (const row of rows) {
        let prodName = row.querySelector(".ci-product-input").value?.trim();
        if (!prodName) return alert("Please enter product name for all rows");

        let prod = productModule.findByName(prodName);

        if (!prod) {
          const newProd = {
            name: prodName,
            price: Number(row.querySelector(".ci-price").value) || 0,
            unit: row.querySelector(".ci-unit").value || "",
            gstPercentage: Number(row.querySelector(".ci-gst").value) || 0
          };
          try {
            const created = await productModule.create(newProd);
            prod = created;
          } catch (err) {
            console.error("Failed to auto-create product", err);
            return alert("Failed to create product " + prodName);
          }
        }

        const qty = Number(row.querySelector(".ci-qty").value) || 0;
        const unit = row.querySelector(".ci-unit").value || "";
        const pricePerUnit = Number(row.querySelector(".ci-price").value) || 0;
        const amountWithoutTax = Number(row.querySelector(".ci-amt-no-tax").innerText) || 0;
        const discountValue = Number(row.querySelector(".ci-disc-value").value) || 0;
        const discountPercent = Number(row.querySelector(".ci-disc-percent").value) || 0;
        const taxableAmount = Number(row.querySelector(".ci-taxable").innerText) || 0;
        const gstPercent = Number(row.querySelector(".ci-gst").value) || 0;
        const gstAmount = Number(row.querySelector(".ci-gst-amt").innerText) || 0;
        const lineTotal = Number(row.querySelector(".ci-line-total").innerText) || 0;

        items.push({
          productId: prod.id,
          qty,
          unit,
          pricePerUnit,
          amountWithoutTax,
          discountType: (discountPercent > 0 ? "PERCENT" : (discountValue > 0 ? "VALUE" : null)),
          discountValue,
          discountPercent,
          taxableAmount,
          gstPercent,
          gstAmount,
          lineTotal
        });
      }

      const subtotalWithoutTax = Number($("createResult").dataset.subtotal) || 0;
      const totalTax = Number($("createResult").dataset.tax) || 0;
      const discountAmount = Number($("createResult").dataset.discountAmount) || 0;
      const grandTotal = Number($("createResult").dataset.grandTotal) || 0;

      const payload = {
        customerId,
        notes,
        discount: { type: discType, value: discVal },  // backend can ignore if not used
        items,
        totals: {
          subtotalWithoutTax,
          totalTax,
          discountAmount,
          grandTotal
        }
      };

      const createdInv = await invoiceModule.save(payload);
      if (createdInv) {
        $("createResult").innerText = `Invoice created: ${createdInv.invoiceNumber || createdInv.id}`;
        this.clearCreateItems();
        this.addCreateItemRow();
        this.recalcCreateTotals();
        await productModule.load();
        await customerModule.load();
        this.populateProductsDatalist();
        this.populateCustomers();
      }

    } catch (err) {
      console.error("submitCreateInvoice error", err);
      alert("Failed to create invoice: " + (err.message || err));
    }
  },

  /* ------------------------------------------------------------
     INVOICE LIST + DETAILS
  ------------------------------------------------------------- */
  renderInvoiceList(invoices) {
    const box = $("invList");
    box.innerHTML = "";
    if (!invoices || !invoices.length) {
      box.innerHTML = "<div class='small muted'>No invoices found.</div>";
      return;
    }
    let html = "";
    invoices.forEach(inv => {
      html += `
        <div class="invoice-list-item">
          <a class="inv-link" data-id="${inv.id}">
            <b>${inv.invoiceNumber}</b><br>
            <span class="small muted">Customer: ${inv.customer?.name || "-"}</span><br>
            <span class="small">Total: ${money(inv.totalAmount)}</span>
          </a>
        </div>
      `;
    });
    box.innerHTML = html;
    [...box.querySelectorAll(".inv-link")].forEach(link => {
      link.onclick = (e) => {
        const id = e.currentTarget.getAttribute("data-id");
        this.showInvoiceDetails(id);
      };
    });
  },

  async showInvoiceDetails(id) {
    try {
      const inv = await invoiceModule.preview(id);
      if (!inv) return alert("Invoice not found");

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
              ${customerModule.customers.map(c =>
                `<option value="${c.id}" ${c.id === inv.customer?.id ? "selected" : ""}>${c.name}</option>`
              ).join("")}
            </select>
            <label>Invoice Date</label>
            <input id="editDate" type="datetime-local"
                   value="${inv.invoiceDate?.slice(0,16) || ''}" />
            <label>Notes</label>
            <textarea id="editNotes">${inv.notes || ""}</textarea>
          </div>

          <table class="invoice-table">
            <thead><tr>
              <th>Product</th><th>Qty</th><th>Price</th><th>GST</th><th>Total</th><th></th>
            </tr></thead>
            <tbody id="editItemsBody">${rows}</tbody>
          </table>

          <button id="addItemBtnEdit" class="btn">➕ Add Item</button>
          <div id="editGrandTotal" class="invoice-total-box"></div>
          <button id="saveInvoiceBtn" class="btn primary save-big">💾 Save Invoice</button>
        </div>
      `;

      this.refreshTotals();
      this.bindEditEvents(inv);

    } catch (err) {
      console.error("showInvoiceDetails", err);
      alert("Failed to load invoice details");
    }
  },

  buildEditableRow(item) {
    return `
      <tr data-item-id="${item.id || ''}">
        <td>
          <select class="prodSelect">
            ${productModule.products.map(p =>
              `<option value="${p.id}" ${p.id === (item.product?.id || item.productId) ? "selected" : ""}>${p.name}</option>`
            ).join("")}
          </select>
        </td>
        <td><input class="qtyInput" type="number" min="1" value="${item.qty ?? item.quantity ?? 1}"></td>
        <td><input class="priceInput" type="number" value="${item.pricePerUnit ?? item.price ?? item.product?.price ?? 0}"></td>
        <td><input class="gstInput" type="number" value="${item.gstPercent ?? item.gstPercentage ?? item.product?.gstPercentage ?? 0}"></td>
        <td class="lineTotal">0</td>
        <td><button class="btn danger small deleteItem">🗑</button></td>
      </tr>
    `;
  },

  bindEditEvents(inv) {
    const refresh = () => this.refreshTotals();

    document.querySelectorAll("#editItemsBody input, #editItemsBody select")
      .forEach(el => el.oninput = refresh);

    const addBtn = $("addItemBtnEdit");
    if (addBtn) addBtn.onclick = () => {
      const p = productModule.products[0] || { price:0, gstPercentage:0 };
      const newItem = {
        id: null,
        product: p,
        qty: 1,
        pricePerUnit: p.price,
        gstPercent: p.gstPercentage
      };
      $("editItemsBody").insertAdjacentHTML("beforeend", this.buildEditableRow(newItem));
      this.bindEditEvents(inv);
      refresh();
    };

    document.querySelectorAll(".deleteItem").forEach(btn =>
      btn.onclick = () => { btn.closest("tr").remove(); refresh(); });

    const saveBtn = $("saveInvoiceBtn");
    if (saveBtn) saveBtn.onclick = async () => {
      const payload = {
        customerId: Number($("editCustomer").value),
        invoiceDate: $("editDate").value,
        notes: $("editNotes").value,
        items: []
      };

      document.querySelectorAll("#editItemsBody tr").forEach(row => {
        // recompute line-level fields on client
        const qty = Number(row.querySelector(".qtyInput").value) || 0;
        const price = Number(row.querySelector(".priceInput").value) || 0;
        const gst = Number(row.querySelector(".gstInput").value) || 0;
        const base = qty * price;
        const gstAmt = base * (gst / 100);
        const lineTotal = base + gstAmt;

        payload.items.push({
          itemId: row.getAttribute("data-item-id") || null,
          productId: Number(row.querySelector(".prodSelect").value),
          qty,
          unit: null,
          pricePerUnit: price,
          amountWithoutTax: base,
          discountType: null,
          discountValue: 0,
          discountPercent: 0,
          taxableAmount: base,
          gstPercent: gst,
          gstAmount: gstAmt,
          lineTotal: lineTotal
        });
      });

      await invoiceModule.update(inv.id, payload);
      alert("Invoice Updated Successfully!");
      this.showInvoiceDetails(inv.id);
    };
  },

  refreshTotals() {
    let total = 0;
    document.querySelectorAll("#editItemsBody tr").forEach(row => {
      const qty = Number(row.querySelector(".qtyInput").value) || 0;
      const price = Number(row.querySelector(".priceInput").value) || 0;
      const gst = Number(row.querySelector(".gstInput").value) || 0;
      const base = qty * price;
      const gstAmount = base * (gst / 100);
      const lineTotal = base + gstAmount;
      row.querySelector(".lineTotal").innerText = lineTotal.toFixed(2);
      total += lineTotal;
    });
    $("editGrandTotal").innerHTML = `GRAND TOTAL: ${money(total)}`;
  },

  /* ------------------------------------------------------------
     CUSTOMERS / PRODUCTS LIST RENDERING
  ------------------------------------------------------------- */
  renderCustomerList() {
    const box = $("custListBox");
    if (!box) return;
    if (!customerModule.customers.length) {
      box.innerHTML = `<div class="small muted">No customers yet.</div>`;
      return;
    }
    let html = `<table class="invoice-table"><thead>
                  <tr><th>ID</th><th>Name</th><th>Phone</th></tr>
                </thead><tbody>`;
    customerModule.customers.forEach(c => {
      html += `<tr><td>${c.id}</td><td>${c.name}</td><td>${c.phone}</td></tr>`;
    });
    html += `</tbody></table>`;
    box.innerHTML = html;
  },

  renderProductList() {
    const box = $("prodListBox");
    if (!box) return;
    if (!productModule.products.length) {
      box.innerHTML = `<div class="small muted">No products yet.</div>`;
      return;
    }
    let html = `<table class="invoice-table"><thead>
                  <tr><th>ID</th><th>Name</th><th>Price</th><th>GST%</th></tr>
                </thead><tbody>`;
    productModule.products.forEach(p => {
      html += `<tr><td>${p.id}</td><td>${p.name}</td><td>${money(p.price)}</td><td>${p.gstPercentage ?? 0}</td></tr>`;
    });
    html += `</tbody></table>`;
    box.innerHTML = html;
  },

  /* ------------------------------------------------------------
     GLOBAL BINDINGS (called from main.js)
  ------------------------------------------------------------- */
  bindEvents() {
    // Sidebar nav
    document.querySelectorAll(".nav-item").forEach(btn => {
      btn.onclick = () => {
        this.switchView(btn.dataset.view);
      };
    });

    // Create-invoice item buttons
    const addBtn = $("createAddItem");
    if (addBtn) addBtn.onclick = () => this.addCreateItemRow();

    const clearBtn = $("createClear");
    if (clearBtn) clearBtn.onclick = () => {
      if (confirm("Clear all items?")) {
        this.clearCreateItems();
        this.addCreateItemRow();
        this.recalcCreateTotals();
      }
    };

    const discType = $("discountType");
    const discVal = $("discountValue");
    if (discType) discType.onchange = () => this.recalcCreateTotals();
    if (discVal) discVal.oninput = () => this.recalcCreateTotals();

    // Save invoice
    const saveBtn = $("saveInvBtn");
    if (saveBtn) saveBtn.onclick = async () => {
      saveBtn.disabled = true;
      saveBtn.innerText = "Saving...";
      try {
        await this.submitCreateInvoice();
      } catch (err) {
        console.error(err);
        alert("Create invoice failed");
      } finally {
        saveBtn.disabled = false;
        saveBtn.innerText = "Create Invoice";
      }
    };

    // Fetch invoices section
    const fetchAllBtn = $("fetchAllInvBtn");
    if (fetchAllBtn) fetchAllBtn.onclick = async () => {
      try {
        const all = await invoiceModule.list();
        this.renderInvoiceList(all);
      } catch (err) {
        console.error(err);
        alert("Failed to fetch invoices");
      }
    };

    const fetchByIdBtn = $("fetchInvIdBtn");
    if (fetchByIdBtn) fetchByIdBtn.onclick = async () => {
      const id = $("fetchInvId").value.trim();
      if (!id) return alert("Enter invoice ID");
      try {
        const inv = await invoiceModule.preview(id);
        this.renderInvoiceList(inv ? [inv] : []);
      } catch (err) {
        console.error(err);
        alert("Failed to fetch invoice");
      }
    };

    const fetchByCustomerBtn = $("fetchInvCustomerBtn");
    if (fetchByCustomerBtn) fetchByCustomerBtn.onclick = async () => {
      const cid = $("fetchInvCustomer").value.trim();
      if (!cid) return alert("Enter customer ID");
      try {
        const all = await invoiceModule.list();
        const filtered = all.filter(inv => inv.customer && String(inv.customer.id) === String(cid));
        this.renderInvoiceList(filtered);
      } catch (err) {
        console.error(err);
        alert("Failed to fetch invoices");
      }
    };

    // Customer view
    const loadCustBtn = $("loadCustBtn");
    if (loadCustBtn) loadCustBtn.onclick = async () => {
      await customerModule.load();
      this.populateCustomers();
      this.renderCustomerList();
    };

    const saveCustBtn = $("saveCustBtn");
    if (saveCustBtn) saveCustBtn.onclick = async () => {
      try {
        const payload = {
          name: $("c_name").value.trim(),
          phone: $("c_phone").value.trim(),
          email: $("c_email").value.trim(),
          address: $("c_address").value.trim()
        };
        if (!payload.name || !payload.phone) {
          return alert("Name and phone are required");
        }
        await customerModule.create(payload);
        await customerModule.load();
        this.populateCustomers();
        this.renderCustomerList();
        alert("Customer saved.");
      } catch (err) {
        console.error("Save customer error:", err);
        alert("Failed to save customer");
      }
    };

    // Product view
    const loadProdBtn = $("loadProdBtn");
    if (loadProdBtn) loadProdBtn.onclick = async () => {
      await productModule.load();
      this.populateProductsDatalist();
      this.renderProductList();
    };

    const saveProdBtn = $("saveProdBtn");
    if (saveProdBtn) saveProdBtn.onclick = async () => {
      try {
        const payload = {
          name: $("p_name").value.trim(),
          price: Number($("p_price").value) || 0,
          unit: $("p_unit").value || "",
          gstPercentage: Number($("p_gst").value) || 0
        };
        if (!payload.name) return alert("Product name is required");
        await productModule.create(payload);
        await productModule.load();
        this.populateProductsDatalist();
        this.renderProductList();
        alert("Product saved.");
      } catch (err) {
        console.error("Save product error:", err);
        alert("Failed to save product");
      }
    };
  }
};
