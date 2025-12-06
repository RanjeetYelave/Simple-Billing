// main.js
import { productModule } from "./product.js";
import { customerModule } from "./customer.js";
import { invoiceModule } from "./invoice.js";
import { layout } from "./ui/layout.js";
import { authScreen } from "./ui/auth-screen.js";

document.addEventListener("DOMContentLoaded", init);

async function init() {
  try {
    // Apply theme ASAP
    applySavedTheme();

    // Show auth screen first (hard login gate)
    renderLoginScreen();

  } catch (err) {
    console.error("Init failed", err);
  }
}

/* ============================
   Auth → App bootstrap
   ============================ */

function renderLoginScreen() {
  const app = document.getElementById("app");
  if (!app) {
    console.error("#app container not found");
    return;
  }

  app.innerHTML = authScreen.render();
  authScreen.init({
    onLoginSuccess: async () => {
      // After successful login, boot full app
      await bootstrapApp();
    }
  });

  // Theme toggle exists on login screen too
  setupThemeToggle();
}

async function bootstrapApp() {
  const app = document.getElementById("app");
  if (!app) return;

  console.log("🔥 Loading initial data...");

  // Load base data AFTER login
  await productModule.load();
  await customerModule.load();

  console.log("🔥 Rendering UI shell...");

  app.innerHTML = layout.render();
  layout.init();

  // Reconnect theme toggle in app layout
  setupThemeToggle();
}

/* ============================
   Theme Loader (before render)
   ============================ */
function applySavedTheme() {
  const saved = localStorage.getItem("theme") || "dark";
  document.documentElement.setAttribute("data-theme", saved);
}

/* ============================
   Theme Toggle
   ============================ */
function setupThemeToggle() {
  const toggle = document.getElementById("themeToggle");
  if (!toggle) {
    // On some screens (rare), it may be missing; it's fine.
    return;
  }

  const root = document.documentElement;

  const updateIcon = (mode) => {
    toggle.textContent = mode === "dark" ? "🌞" : "🌙";
  };

  const saved = localStorage.getItem("theme") || "dark";
  root.setAttribute("data-theme", saved);
  updateIcon(saved);

  toggle.onclick = () => {
    const current = root.getAttribute("data-theme");
    const next = current === "dark" ? "light" : "dark";
    root.setAttribute("data-theme", next);
    localStorage.setItem("theme", next);
    updateIcon(next);
  };
}
