// js/ui/layout.js
// NOTE: no import of ui here to avoid circular dependency

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
              <span class="icon">🧾</span>
              <span>Create Invoice</span>
            </button>
            <button class="nav-item" data-view="invoices">
              <span class="icon">📚</span>
              <span>Invoices</span>
            </button>
            <button class="nav-item" data-view="customers">
              <span class="icon">👤</span>
              <span>Customers</span>
            </button>
            <button class="nav-item" data-view="products">
              <span class="icon">📦</span>
              <span>Products</span>
            </button>
          </div>

          <div class="sidebar-footer">
            <div>Local Dev • http://localhost:8080</div>
          </div>
        </aside>

        <main class="main">
          <div class="main-header">
            <div>
              <h1 id="mainTitle">Create Invoice</h1>
              <div class="subtitle" id="mainSubtitle">
                Quickly create and save multi-item invoices.
              </div>
            </div>
          </div>

          <section id="view-invoiceCreate" class="view active"></section>
          <section id="view-invoices" class="view"></section>
          <section id="view-customers" class="view"></section>
          <section id="view-products" class="view"></section>
        </main>
      </div>
    `;
  },

  switchView(view) {
    // toggle sections
    document.querySelectorAll(".view").forEach(v => v.classList.remove("active"));
    const target = document.getElementById(`view-${view}`);
    if (target) target.classList.add("active");

    // toggle nav active
    document.querySelectorAll(".nav-item")
      .forEach(btn => btn.classList.toggle("active", btn.dataset.view === view));

    const titleMap = {
      invoiceCreate: "Create Invoice",
      invoices: "Invoices",
      customers: "Customers",
      products: "Products"
    };

    const subtitleMap = {
      invoiceCreate: "Quickly create and save multi-item invoices.",
      invoices: "Browse, open and edit existing invoices.",
      customers: "Manage customers used while creating invoices.",
      products: "Manage products with price and GST."
    };

    const titleEl = document.getElementById("mainTitle");
    const subEl = document.getElementById("mainSubtitle");
    if (titleEl) titleEl.textContent = titleMap[view] || "";
    if (subEl) subEl.textContent = subtitleMap[view] || "";
  }
};
