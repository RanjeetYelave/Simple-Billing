// js/main.js

import { ui } from "./ui.js";
import { productModule } from "./product.js";
import { customerModule } from "./customer.js";
import { invoiceModule } from "./invoice.js";
import { $, extractId } from "./utils.js";

async function init() {
    ui.render();

    // Load data
    await productModule.load();
    await customerModule.load();

    ui.populateCustomers();

    // EVENTS
    $("addItemBtn").onclick = () => ui.addItemRow();

    $("saveCustBtn").onclick = async () => {
        await customerModule.save();
        await customerModule.load();
        ui.populateCustomers();
        alert("Customer saved.");
    };

    $("saveProdBtn").onclick = async () => {
        await productModule.save();
        await productModule.load();
        alert("Product saved.");
    };

    $("saveInvBtn").onclick = async () => {
        const custText = $("custInput").value;
        const custId = extractId(custText);
        if (!custId) return alert("Select customer with id:XX");

        const rows = [...$("itemsBody").children];
        if (rows.length === 0) return alert("Add items");

        const items = rows.map(r => ({
            productId: Number(r.children[0].children[0].value),
            qty: Number(r.children[1].children[0].value)
        }));

        const payload = { customerId: custId, items, notes: "" };
        const inv = await invoiceModule.save(payload);

        alert("Invoice created: " + inv.invoiceNumber);
    };

    // FETCH INVOICES
    $("fetchAllInvBtn").onclick = async () => {
        const all = await invoiceModule.list();
        ui.renderInvoiceList(all);
    };

    $("fetchInvIdBtn").onclick = async () => {
        const id = $("fetchInvId").value.trim();
        if (!id) return alert("Enter Invoice ID.");
        const inv = await invoiceModule.preview(id);
        ui.renderInvoiceList([inv]);
    };

    $("fetchInvCustomerBtn").onclick = async () => {
        const cid = $("fetchInvCustomer").value.trim();
        if (!cid) return alert("Enter Customer ID.");

        const all = await invoiceModule.list();
        const filtered = all.filter(inv => inv.customer.id == cid);
        ui.renderInvoiceList(filtered);
    };
}

init();
