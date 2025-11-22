// js/ui/layout.js

export const layout = {
  renderShell() {
    return `
      <div class="layout">
        <aside class="sidebar">
          <div class="brand">
            <div class="brand-badge">B</div>
            <span>Billsoft</span>
          </div>

          <div class="nav-section-title">Navigation</div>
          <div class="nav-list">
            <button class="nav-item active" data-view="invoiceCreate">
              🧾 Create Invoice
            </button>
            <button class="nav-item" data-view="invoices">
              📚 Invoices
            </button>
            <button class="nav-item" data-view="customers">
              👤 Customers
            </button>
            <button class="nav-item" data-view="products">
              📦 Products
            </button>
          </div>

          <div class="sidebar-footer">
            Local Dev • http://localhost:8080
          </div>
        </aside>

        <main class="main">
          <h1 id="mainTitle">Create Invoice</h1>

          <section id="view-invoiceCreate" class="view active"></section>
          <section id="view-invoices" class="view"></section>
          <section id="view-customers" class="view"></section>
          <section id="view-products" class="view"></section>
        </main>
      </div>
    `;
  },

  switchView(view) {
    document.querySelectorAll(".view").forEach(v => v.classList.remove("active"));
    document.getElementById(`view-${view}`).classList.add("active");

    document.querySelectorAll(".nav-item")
      .forEach(btn => btn.classList.toggle("active", btn.dataset.view === view));

    const titles = {
      invoiceCreate: "Create Invoice",
      invoices: "Invoices",
      customers: "Customers",
      products: "Products"
    };

    document.getElementById("mainTitle").textContent = titles[view];
  }
};
