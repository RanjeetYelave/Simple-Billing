// js/ui/navigation.js
import { $ } from "../utils.js";

// SCREENS
import { invoiceList } from "./invoice-list.js";
import { invoiceCreate } from "./invoice-create.js";
import { customerScreen } from "./customer-screen.js";
import { productScreen } from "./product-screen.js";
import { analyticsScreen } from "./analytics-screen.js";
import { firmAnalyticsScreen } from "./firm-analytics-screen.js";
import { firmProfileScreen } from "./firm-profile-screen.js";

// NEW — Statements Screen
import { statementScreen } from "./statement-screen.js";

// NEW — Updates Screen
import { updatesScreen } from "./updates-screen.js";

// NEW — Estimates Screen
import { estimateList } from "./estimate-list.js";

export const navigation = {

  screens: {
    firmProfile: firmProfileScreen,
    invoiceCreate,
    invoices: invoiceList,
    estimates: estimateList,      
    customers: customerScreen,
    products: productScreen,
    analytics: analyticsScreen,
    firmAnalytics: firmAnalyticsScreen,
    statements: statementScreen,
    updates: updatesScreen
  },

  // FINAL SIDEBAR ORDER
  navItems: [
    { id: "firmProfile",   label: "Firm Profile",      icon: "🏢" },
    { id: "invoiceCreate", label: "Create Invoice",    icon: "➕" },
    { id: "invoices",      label: "Invoices",          icon: "📄" },
    { id: "estimates",     label: "Estimates",         icon: "🧾" },  // 👈 NEW
    { id: "customers",     label: "Customers",         icon: "👥" },
    { id: "products",      label: "Products",          icon: "📦" },
    { id: "analytics",     label: "Customer Insights", icon: "📊" },
    { id: "firmAnalytics", label: "Firm Dashboard",    icon: "📈" },
    { id: "statements",    label: "Statements",        icon: "🧾" },
    { id: "updates",       label: "Updates",           icon: "🔄" }
  ],

  init() {
    const sidebar = $("sidebarNav");

    // Render sidebar items
    sidebar.innerHTML = this.navItems
      .map(
        item => `
          <div class="nav-item" data-id="${item.id}">
            <span class="nav-icon">${item.icon}</span>
            <span>${item.label}</span>
          </div>
        `
      )
      .join("");

    // Click handlers
    sidebar.querySelectorAll(".nav-item").forEach(el => {
      el.onclick = () => {
        sidebar.querySelectorAll(".nav-item")
          .forEach(n => n.classList.remove("active"));

        el.classList.add("active");
        this.show(el.dataset.id);
      };
    });

    // DEFAULT SCREEN → Create Invoice
    this.show("invoiceCreate");
    sidebar.querySelector(`[data-id="invoiceCreate"]`).classList.add("active");
  },

  show(screenId) {
    const screen = this.screens[screenId];
    if (!screen) {
      console.error("Invalid screen:", screenId);
      return;
    }

    const main = $("mainContent");
    main.innerHTML = screen.render();
    screen.init?.();
  }
};
