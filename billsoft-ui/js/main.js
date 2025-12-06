// main.js
import { productModule } from "./product.js";
import { customerModule } from "./customer.js";
import { invoiceModule } from "./invoice.js";
import { layout } from "./ui/layout.js";

import { authScreen } from "./ui/auth-screen.js";
import { authResetScreen } from "./ui/auth-reset-screen.js"; // NEW

document.addEventListener("DOMContentLoaded", init);

/* ============================
   INIT
   ============================ */
async function init() {
  applySavedTheme();
  showLogin();
}

/* ============================
   SCREEN ROUTING
   ============================ */
function showLogin() {
  const app = document.getElementById("app");
  app.innerHTML = authScreen.render();

  authScreen.init({
    onLoginSuccess: bootstrapApp,
    onShowResetScreen: showResetPassword // NEW CALLBACK
  });

  setupThemeToggle();
}

function showResetPassword() {
  const app = document.getElementById("app");
  app.innerHTML = authResetScreen.render();

  authResetScreen.init({
    onBackToLogin: showLogin
  });

  setupThemeToggle();
}

/* ============================
   LOAD APP — AFTER LOGIN
   ============================ */
async function bootstrapApp() {
  const app = document.getElementById("app");
  if (!app) return;

  console.log("🔥 Bootstrapping InvoiceSuite…");

  await productModule.load();
  await customerModule.load();

  app.innerHTML = layout.render();
  layout.init();

  setupThemeToggle();
}

/* ============================
   THEME
   ============================ */
function applySavedTheme() {
  const theme = localStorage.getItem("theme") || "dark";
  document.documentElement.setAttribute("data-theme", theme);
}

function setupThemeToggle() {
  const toggle = document.getElementById("themeToggle");
  if (!toggle) return;

  const root = document.documentElement;

  function setIcon(mode) {
    toggle.textContent = mode === "dark" ? "🌞" : "🌙";
  }

  setIcon(localStorage.getItem("theme") || "dark");

  toggle.onclick = () => {
    const current = root.getAttribute("data-theme");
    const next = current === "dark" ? "light" : "dark";
    root.setAttribute("data-theme", next);
    localStorage.setItem("theme", next);
    setIcon(next);
  };
}
