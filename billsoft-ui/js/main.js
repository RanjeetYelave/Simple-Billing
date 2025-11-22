// js/main.js
import { ui } from "./ui/ui.js";
import { productModule } from "./product.js";
import { customerModule } from "./customer.js";

async function init() {
  try {
    console.log("🔥 Loading initial data...");
    await Promise.all([productModule.load(), customerModule.load()]);

    console.log("🔥 Rendering UI shell...");
    ui.renderShell();       // Only renders layout, does NOT load views yet.

    console.log("🔥 Initializing first view manually...");
    ui.showCreateInvoice(); // Loads the Create Invoice screen AFTER data is loaded.

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
