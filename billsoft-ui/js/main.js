// js/main.js
import { ui } from "./ui.js";
import { productModule } from "./product.js";
import { customerModule } from "./customer.js";
import { invoiceModule } from "./invoice.js";

async function init() {
  try {
    // Load initial data (products + customers)
    await Promise.all([
      productModule.load(),
      customerModule.load()
    ]);

    // Render shell
    ui.render();

    // Populate autocomplete
    ui.populateProductsDatalist();
    ui.populateCustomers();

    // Prepare invoice form
    ui.clearCreateItems();
    ui.addCreateItemRow();
    ui.recalcCreateTotals();

    // Bind all events
    ui.bindEvents();

    console.log("UI initialized successfully");
  } catch (err) {
    console.error("Init failed", err);
    document.getElementById("app").innerHTML =
      `<div style="padding:20px;color:#fecaca">
         <h2>UI load failed</h2>
         <pre>${err.message || err}</pre>
       </div>`;
  }
}

document.addEventListener("DOMContentLoaded", init);
