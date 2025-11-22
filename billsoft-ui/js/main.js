// js/main.js

import { ui } from "./ui/ui.js";
import { productModule } from "./product.js";
import { customerModule } from "./customer.js";

async function init() {
  try {
    console.log("🔥 Loading initial data...");

    await Promise.all([
      productModule.load(),
      customerModule.load()
    ]);

    console.log("🔥 Rendering UI shell...");
    ui.render();

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
