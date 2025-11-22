// main.js
import { productModule } from "./product.js";
import { customerModule } from "./customer.js";
import { invoiceModule } from "./invoice.js";
import { layout } from "./ui/layout.js";

document.addEventListener("DOMContentLoaded", init);

async function init() {
  try {
    console.log("🔥 Loading initial data...");

    await productModule.load();
    await customerModule.load();
    // invoices are fetched screen-wise, no need to load all here

    console.log("🔥 Rendering UI shell...");
    document.body.innerHTML = layout.render();
    layout.init();

  } catch (err) {
    console.error("Init failed", err);
  }
}
