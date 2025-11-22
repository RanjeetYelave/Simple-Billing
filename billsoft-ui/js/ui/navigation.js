// js/ui/navigation.js
import { $ } from "../utils.js";

// SCREENS
import { invoiceList } from "./invoice-list.js";
import { invoiceCreate } from "./invoice-create.js";
import { customerScreen } from "./customer-screen.js";
import { productScreen } from "./product-screen.js";
import { analyticsScreen } from "./analytics-screen.js";
import { firmAnalyticsScreen } from "./firm-analytics-screen.js"; // NEW

export const navigation = {

  screens: {
    invoices: invoiceList,
    invoiceCreate: invoiceCreate,
    customers: customerScreen,
    products: productScreen,
    analytics: analyticsScreen,
    firmAnalytics: firmAnalyticsScreen
  },

  navItems: [
    { id: "invoices",       label: "Invoices",        icon: "📄" },
    { id: "invoiceCreate",  label: "Create Invoice",  icon: "➕" },
    { id: "customers",      label: "Customers",       icon: "👥" },
    { id: "products",       label: "Products",        icon: "📦" },
    { id: "analytics",      label: "Customer Insights", icon: "📊" },
    { id: "firmAnalytics",  label: "Firm Dashboard",  icon: "🏢" }
  ],

  init() {
    const sidebar = $("sidebarNav");
    sidebar.innerHTML = this.navItems
      .map(
        item => `
        <div class="nav-item" data-id="${item.id}">
            <span class="nav-icon">${item.icon}</span>
            <span>${item.label}</span>
        </div>`
      )
      .join("");

    // Bind navigation clicks
    sidebar.querySelectorAll(".nav-item").forEach(el => {
      el.onclick = () => {
        sidebar.querySelectorAll(".nav-item")
               .forEach(n => n.classList.remove("active"));
        el.classList.add("active");
        this.show(el.dataset.id);
      };
    });

    // Default screen
    this.show("invoices");
    sidebar.querySelector(`[data-id="invoices"]`).classList.add("active");
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
