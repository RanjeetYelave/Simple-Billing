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
              <table id="itemsTbl">
                <thead><tr><th>Product</th><th>Qty</th><th></th></tr></thead>
                <tbody id="itemsBody"></tbody>
              </table>
              <button class="btn" id="addItemBtn">+ Add Item</button>

              <div id="invTotal" style="margin-top:10px;font-weight:600"></div>
              <button class="btn" id="saveInvBtn">Create Invoice</button>
            </div>

            <div class="card">
              <h2>Create Customer</h2>
              <input id="c_name" placeholder="Name"/>
              <input id="c_phone" placeholder="Phone"/>
              <button class="btn" id="saveCustBtn">Save Customer</button>
            </div>

            <div class="card">
              <h2>Create Product</h2>
              <input id="p_name" placeholder="Product Name"/>
              <input id="p_price" placeholder="Price" type="number"/>
              <input id="p_gst" placeholder="GST %" type="number"/>
              <button class="btn" id="saveProdBtn">Save Product</button>
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
        remove.innerText = "x";
        remove.className = "btn ghost";
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
        const total = invoiceModule.calculateTotal(items);
        $("invTotal").innerText = "Total: " + money(total);
    }
};
