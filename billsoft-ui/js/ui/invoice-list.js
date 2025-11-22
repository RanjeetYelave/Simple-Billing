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
    $("reloadInvoices").onclick = () => this.loadAll();
    $("invSearchIdBtn").onclick = () => this.searchById();
    $("invSearchCustomerBtn").onclick = () => this.searchByCustomer();

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
    if (!idText) return alert("Enter an Invoice ID");

    const id = Number(idText);
    if (isNaN(id)) return alert("Invoice ID must be a number");

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
    if (!cidText) return alert("Enter a Customer ID");

    const cid = Number(cidText);
    if (isNaN(cid)) return alert("Customer ID must be a number");

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

  async markPaid(id) {
    if (!confirm("Mark this invoice as PAID?")) return;

    try {
      await invoiceModule.update(id, { paid: true });
      this.loadAll();
    } catch (err) {
      console.error("Failed to mark invoice paid", err);
      alert("Failed to mark invoice paid.");
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

      const isPaid = inv.paid === true;

      div.innerHTML = `
        <b>${inv.invoiceNumber}</b>
        <div class="small muted">${inv.customer?.name || "—"}</div>
        <div>${money(inv.totalAmount)}</div>

        <!-- Status -->
        <div class="small" style="margin-top:6px;color:${isPaid ? '#10b981' : '#f87171'};">
          Status: ${isPaid ? "Paid" : "Unpaid"}
        </div>

        <!-- Mark Paid row -->
        <div style="margin-top:8px;">
          ${
            isPaid
              ? `<div class="small" style="color:#10b981;">✓ Already Paid</div>`
              : `<button class="btn small" style="background:#10b981;border-radius:6px;font-size:11px;" data-pay="${inv.id}">
                    Mark Paid
                 </button>`
          }
        </div>
      `;

      // Open invoice on main card click
      div.onclick = (e) => {
        if (e.target.dataset.pay) return; // Allow button click without opening

        invoiceEdit.open(inv.id);
      };

      // Button handler
      if (!isPaid) {
        const btn = div.querySelector("button[data-pay]");
        btn.onclick = (ev) => {
          ev.stopPropagation(); // prevent opening invoice editor
          this.markPaid(inv.id);
        };
      }

      box.appendChild(div);
    });

    $("invDetails").innerHTML = "";
  }
};
