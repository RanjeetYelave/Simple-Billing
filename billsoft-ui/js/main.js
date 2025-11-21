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

    // Bind events
    $("addItemBtn").onclick = () => ui.addItemRow();

    $("saveCustBtn").onclick = async () => {
        await customerModule.save();
        await customerModule.load();
        ui.populateCustomers();
    };

    $("saveProdBtn").onclick = async () => {
        await productModule.save();
        await productModule.load();
    };

    $("saveInvBtn").onclick = async () => {
        const custText = $("custInput").value;
        const custId = extractId(custText);
        if (!custId) return alert("Pick a customer from autocomplete (must contain id:3)");

        const rows = [...$("itemsBody").children];
        const items = rows.map(r => ({
            productId: Number(r.children[0].children[0].value),
            qty: Number(r.children[1].children[0].value)
        }));

        if (items.length === 0) return alert("Add items");

        const payload = { customerId: custId, items, notes: "" };

        const inv = await invoiceModule.save(payload);
        alert("Invoice created: " + inv.invoiceNumber);
    };
}

// RUN
init();
