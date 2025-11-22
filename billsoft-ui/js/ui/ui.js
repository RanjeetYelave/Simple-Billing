// js/ui/ui.js
import { invoiceList } from "./invoice-list.js";
import { invoiceCreate } from "./invoice-create.js";
import { invoiceEdit } from "./invoice-edit.js";
import { customerScreen } from "./customer-screen.js";
import { productScreen } from "./product-screen.js";
import { analyticsScreen } from "./analytics-screen.js";
import { firmAnalyticsScreen } from "./firm-analytics-screen.js";

export const ui = {

  screens: {
    invoices: invoiceList,
    customers: customerScreen,
    products: productScreen,
    analytics: analyticsScreen,
    firm: firmAnalyticsScreen        // 👈 NEW
  },

  showScreen(id) {
    const scr = this.screens[id];
    if (!scr) return;

    document.getElementById("mainContent").innerHTML = scr.render();
    setTimeout(() => scr.init(), 0);
  }
};
