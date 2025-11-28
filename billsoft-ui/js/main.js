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

    // Apply theme BEFORE rendering UI
    applySavedTheme();

    console.log("🔥 Rendering UI shell...");

    // Instead of replacing body.innerHTML, place layout INTO #app
    document.getElementById("app").innerHTML = layout.render();

    layout.init();

    setupThemeToggle(); // connect button AFTER rendering

  } catch (err) {
    console.error("Init failed", err);
  }
}

// ============================
// Theme Loader (before render)
// ============================
function applySavedTheme() {
  const saved = localStorage.getItem("theme") || "dark";
  document.documentElement.setAttribute("data-theme", saved);
}

// ============================
// Theme Toggle
// ============================
function setupThemeToggle() {
  const toggle = document.getElementById("themeToggle");
  if (!toggle) {
    console.warn("Theme toggle button missing");
    return;
  }

  const root = document.documentElement;

  const updateIcon = (mode) => {
    toggle.textContent = mode === "dark" ? "🌞" : "🌙";
  };

  // Load saved theme
  const saved = localStorage.getItem("theme") || "dark";
  updateIcon(saved);

  toggle.onclick = () => {
    const current = root.getAttribute("data-theme");
    const next = current === "dark" ? "light" : "dark";
    root.setAttribute("data-theme", next);
    localStorage.setItem("theme", next);
    updateIcon(next);
  };
}
