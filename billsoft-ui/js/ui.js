// js/ui.js

import { $, money, extractId } from "./utils.js";
import { productModule } from "./product.js";
import { customerModule } from "./customer.js";
import { invoiceModule } from "./invoice.js";

export const ui = {

    render() {
        $("app").innerHTML = `
            <div class="card">
              <h2>Create Invoice</h2>
              <input id="custInput" placeholder="Type customer name or id:3" list="custList"/>
              <datalist id="custList"></datalist>

              <h3>Items</h3>
              <table class="table">
                <thead><tr><th>Product</th><th>Qty</th><th></th></tr></thead>
                <tbody id="itemsBody"></tbody>
              </table>

              <button class="btn" id="addItemBtn">+ Add Item</button>
              <div id="invTotal" class="total-box"></div>

              <button class="btn primary" id="saveInvBtn">Create Invoice</button>
            </div>

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

    addItemRow() {
        const tbody = $("itemsBody");
        const tr = document.createElement("tr");

        const prodSel = document.createElement("select");
        productModule.products.forEach(p => {
            const opt = document.createElement("option");
            opt.value = p.id;
            opt.text = `${p.name} — ₹${p.price}`;
            prodSel.appendChild(opt);
        });

        const qty = document.createElement("input");
        qty.type = "number";
        qty.value = 1;

        const remove = document.createElement("button");
        remove.innerText = "×";
        remove.className = "btn danger small";
        remove.onclick = () => { tr.remove(); ui.updateTotal(); };

        tr.innerHTML = "";
        const td1 = document.createElement("td"); td1.appendChild(prodSel);
        const td2 = document.createElement("td"); td2.appendChild(qty);
        const td3 = document.createElement("td"); td3.appendChild(remove);

        tr.appendChild(td1); tr.appendChild(td2); tr.appendChild(td3);
        tbody.appendChild(tr);

        qty.oninput = () => ui.updateTotal();
        prodSel.onchange = () => ui.updateTotal();
    },

    updateTotal() {
        const rows = [...$("itemsBody").children];
        const items = rows.map(r => ({
            productId: Number(r.children[0].children[0].value),
            qty: Number(r.children[1].children[0].value)
        }));
        $("invTotal").innerText = "Total: " + money(invoiceModule.calculateTotal(items));
    },

    // ----------------------------
    // Render list of invoices
    // ----------------------------
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
                        <div><b>${inv.invoiceNumber}</b></div>
                        <div>Customer: ${inv.customer.name}</div>
                        <div>Total: ${money(inv.totalAmount)}</div>
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

    async showInvoiceDetails(id) {
    const inv = await invoiceModule.preview(id);

    let itemRows = "";
    inv.items.forEach(i => {
        itemRows += `
            <tr data-item-id="${i.id}">
                <td>${i.product.name}</td>
                <td><span class="view-mode">${i.quantity}</span>
                    <input class="edit-mode hidden" type="number" value="${i.quantity}" />
                </td>
                <td>${money(i.price)}</td>
                <td>${i.gstPercentage}%</td>
                <td>${money(i.lineTotal)}</td>
                <td>
                    <span class="view-mode edit-icon" data-action="edit">✏️</span>
                    <span class="edit-mode hidden save-icon" data-action="save">💾</span>
                </td>
            </tr>
        `;
    });

    $("invDetails").innerHTML = `
        <div class="invoice-container">
            <h2>${inv.invoiceNumber}</h2>
            <div class="invoice-meta">
                <div><b>Date:</b> ${inv.invoiceDate}</div>
                <div><b>Customer:</b> ${inv.customer.name}</div>
            </div>

            <table class="invoice-table">
                <thead>
                    <tr>
                        <th>Product</th>
                        <th>Qty</th>
                        <th>Price</th>
                        <th>GST</th>
                        <th>Total</th>
                        <th></th>
                    </tr>
                </thead>
                <tbody>${itemRows}</tbody>
            </table>

            <div class="invoice-total-box">
                GRAND TOTAL: ${money(inv.totalAmount)}
            </div>
        </div>
    `;

    // ---- INLINE EDIT HANDLERS ----
    document.querySelectorAll("#invDetails tr").forEach(row => {
        const editIcon = row.querySelector("[data-action='edit']");
        const saveIcon = row.querySelector("[data-action='save']");
        const viewElems = row.querySelectorAll(".view-mode");
        const editElems = row.querySelectorAll(".edit-mode");

        if (!editIcon) return;

        editIcon.onclick = () => {
            viewElems.forEach(v => v.classList.add("hidden"));
            editElems.forEach(v => v.classList.remove("hidden"));
        };

        saveIcon.onclick = async () => {
            const qtyInput = row.querySelector("input[type='number']");
            const newQty = Number(qtyInput.value);
            const itemId = Number(row.getAttribute("data-item-id"));

            const updatedItems = inv.items.map(it =>
                it.id === itemId ? { ...it, quantity: newQty } : it
            );

            const payload = {
                customerId: inv.customer.id,
                items: updatedItems.map(i => ({
                    itemId: i.id,
                    quantity: i.quantity
                }))
            };

            await invoiceModule.update(inv.id, payload);

            // reload UI view
            ui.showInvoiceDetails(inv.id);
        };
    });
}

};
