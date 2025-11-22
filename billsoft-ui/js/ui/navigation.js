// js/ui/navigation.js
import { layout } from "./layout.js";
import { invoiceCreate } from "./invoice-create.js";
import { invoiceList } from "./invoice-list.js";
import { customerScreen } from "./customer-screen.js";
import { productScreen } from "./product-screen.js";

export const navigation = {

  init() {
    // Bind sidebar buttons
    document.querySelectorAll(".nav-item").forEach(btn => {
      btn.onclick = () => {
        const view = btn.dataset.view;

        layout.switchView(view);
        this.loadView(view);
      };
    });

    // Load default view
    this.loadView("invoiceCreate");
  },

  loadView(view) {
    if (view === "invoiceCreate") {
      document.getElementById("view-invoiceCreate").innerHTML = invoiceCreate.render();
      invoiceCreate.init();
    }
    else if (view === "invoices") {
      document.getElementById("view-invoices").innerHTML = invoiceList.render();
      invoiceList.init();
    }
    else if (view === "customers") {
      document.getElementById("view-customers").innerHTML = customerScreen.render();
      customerScreen.init();
    }
    else if (view === "products") {
      document.getElementById("view-products").innerHTML = productScreen.render();
      productScreen.init();
    }
  }
};
