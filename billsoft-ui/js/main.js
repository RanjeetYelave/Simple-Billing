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
    document.body.innerHTML = `
      <div id="themeToggle" class="theme-toggle">
        <span class="icon">🌙</span>
      </div>
    ` + layout.render();

    layout.init();

    initThemeToggle(); // theme toggle initialization

  } catch (err) {
    console.error("Init failed", err);
  }
}

// ============================
// THEME CONTROLLER
// ============================
function initThemeToggle() {
  const root = document.documentElement;
  const toggleBtn = document.getElementById("themeToggle");

  if (!toggleBtn) return;

  function setTheme(mode) {
    root.setAttribute("data-theme", mode);
    localStorage.setItem("theme", mode);
    toggleBtn.querySelector(".icon").textContent =
      mode === "dark" ? "🌞" : "🌙";
  }

  // Load saved theme (default = dark)
  const saved = localStorage.getItem("theme") || "dark";
  setTheme(saved);

  toggleBtn.onclick = () => {
    const current = root.getAttribute("data-theme");
    const next = current === "dark" ? "light" : "dark";
    setTheme(next);
  };
}
