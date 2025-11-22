// js/ui/navigation.js
import { ui } from "./ui.js";

export const navigation = {

  items: [
    { id: "invoices", label: "Invoices", icon: "📄" },
    { id: "customers", label: "Customers", icon: "👥" },
    { id: "products", label: "Products", icon: "📦" },
    { id: "analytics", label: "Customer Analytics", icon: "📊" },
    { id: "firm", label: "Firm Dashboard", icon: "🏢" }   // 👈 NEW TAB
  ],

  init() {
    const navBox = document.getElementById("sidebarNav");
    navBox.innerHTML = "";

    this.items.forEach(item => {
      const btn = document.createElement("button");
      btn.className = "nav-item";
      btn.dataset.id = item.id;

      btn.innerHTML = `
        <span class="icon">${item.icon}</span>
        ${item.label}
      `;

      btn.onclick = () => this.activate(item.id);
      navBox.appendChild(btn);
    });

    this.activate("invoices");
  },

  activate(id) {
    document.querySelectorAll("#sidebarNav .nav-item")
      .forEach(btn => {
        btn.classList.toggle("active", btn.dataset.id === id);
      });

    ui.showScreen(id);
  }
};
