// js/ui/ui.js
import { layout } from "./layout.js";
import { navigation } from "./navigation.js";

import { invoiceCreate } from "./invoice-create.js";

export const ui = {

  renderShell() {
    document.getElementById("app").innerHTML = layout.renderShell();
    navigation.init();
  },

  showCreateInvoice() {
    navigation.loadView("invoiceCreate");
  },

  showInvoiceList() {
    navigation.loadView("invoices");
  },

  showProducts() {
    navigation.loadView("products");
  },

  showCustomers() {
    navigation.loadView("customers");
  }
};
