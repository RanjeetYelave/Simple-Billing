// js/ui/invoice-list.js
import { invoiceModule } from "../invoice.js";
import { $, money } from "../utils.js";
import { invoiceEdit } from "./invoice-edit.js";

export const invoiceList = {

  render() {
    return `
      <div class="card">
        <h2>Invoices</h2>

        <!-- Search / filter bar -->
        <div style="display:flex;flex-wrap:wrap;gap:8px;margin-bottom:12px;align-items:flex-end">

          <div style="max-width:160px;">
            <label class="small muted">Invoice ID</label>
            <input id="invSearchId" placeholder="e.g. 5" />
          </div>
          <button class="btn small" id="invSearchIdBtn">Search ID</button>

          <div style="max-width:160px;">
            <label class="small muted">Customer ID</label>
            <input id="invSearchCustomer" placeholder="e.g. 2" />
          </div>
          <button class="btn small" id="invSearchCustomerBtn">Search Customer</button>

          <button class="btn ghost small" id="reloadInvoices" style="margin-left:auto;">
            Reload All
          </button>
        </div>

        <div id="invList"></div>
        <div id="invDetails"></div>
      </div>
    `;
  },

  init() {
    // Bind buttons
    $("reloadInvoices").onclick = () => this.loadAll();
    $("invSearchIdBtn").onclick = () => this.searchById();
    $("invSearchCustomerBtn").onclick = () => this.searchByCustomer();

    // Initial load
    this.loadAll();
  },

  async loadAll() {
    try {
      const list = await invoiceModule.list();
      this.renderList(list);
    } catch (err) {
      console.error("Failed to load invoices", err);
      $("invList").innerHTML = `<div class="small muted">Failed to load invoices.</div>`;
    }
  },

  async searchById() {
    const idText = $("invSearchId").value.trim();
    if (!idText) {
      alert("Enter an Invoice ID");
      return;
    }
    const id = Number(idText);
    if (isNaN(id)) {
      alert("Invoice ID must be a number");
      return;
    }

    try {
      const inv = await invoiceModule.preview(id);
      if (!inv || inv.id == null) {
        $("invList").innerHTML = `<div class="small muted">Invoice not found.</div>`;
        $("invDetails").innerHTML = "";
        return;
      }
      this.renderList([inv]);
    } catch (err) {
      console.error("Search by ID failed", err);
      $("invList").innerHTML = `<div class="small muted">Error fetching invoice.</div>`;
      $("invDetails").innerHTML = "";
    }
  },

  async searchByCustomer() {
    const cidText = $("invSearchCustomer").value.trim();
    if (!cidText) {
      alert("Enter a Customer ID");
      return;
    }
    const cid = Number(cidText);
    if (isNaN(cid)) {
      alert("Customer ID must be a number");
      return;
    }

    try {
      const all = await invoiceModule.list();
      const filtered = all.filter(inv => inv.customer && Number(inv.customer.id) === cid);

      if (!filtered.length) {
        $("invList").innerHTML = `<div class="small muted">No invoices for this customer.</div>`;
        $("invDetails").innerHTML = "";
        return;
      }

      this.renderList(filtered);
    } catch (err) {
      console.error("Search by customer failed", err);
      $("invList").innerHTML = `<div class="small muted">Error fetching invoices.</div>`;
      $("invDetails").innerHTML = "";
    }
  },

  renderList(list) {
    const box = $("invList");
    box.innerHTML = "";

    if (!list || !list.length) {
      box.innerHTML = `<div class="small muted">No invoices found.</div>`;
      $("invDetails").innerHTML = "";
      return;
    }

    list.forEach(inv => {
      const div = document.createElement("div");
      div.className = "invoice-list-item";
      div.innerHTML = `
        <b>${inv.invoiceNumber}</b>
        <div class="small muted">${inv.customer?.name || "—"}</div>
        <div>${money(inv.totalAmount)}</div>
      `;
      div.onclick = () => invoiceEdit.open(inv.id);
      box.appendChild(div);
    });

    // Clear details panel until an invoice is clicked
    $("invDetails").innerHTML = "";
  }
};
