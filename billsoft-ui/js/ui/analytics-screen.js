// js/ui/analytics-screen.js
import { $, money, extractId } from "../utils.js";
import { customerModule } from "../customer.js";
import { invoiceModule } from "../invoice.js";

export const analyticsScreen = {

  // state
  currentAnalytics: null,
  currentInvoices: [],

  render() {
    return `
      <div class="card">
        <h2>Customer Analytics</h2>

        <div style="display:grid;grid-template-columns:2fr auto;gap:10px;align-items:end">
          <div>
            <label>Customer</label>
            <input id="analyticsCustInput"
                   list="analyticsCustList"
                   placeholder="Type customer name or id:3" />
            <datalist id="analyticsCustList"></datalist>
          </div>
          <button class="btn primary" id="analyticsSearchBtn">🔍 Analyse</button>
        </div>

        <div id="analyticsSummary" class="small muted" style="margin-top:10px;">
          Enter customer name or id and click Analyse.
        </div>

        <div id="analyticsCards" style="display:flex;gap:10px;flex-wrap:wrap;margin-top:12px;"></div>

        <div style="display:flex;justify-content:space-between;align-items:center;margin-top:18px;">
          <h3 style="margin:0;">Invoices</h3>
          <div>
            <label class="small muted">Status Filter</label>
            <select id="analyticsStatusFilter" style="margin-left:6px;max-width:140px;">
              <option value="ALL">All</option>
              <option value="PAID">Paid</option>
              <option value="UNPAID">Unpaid</option>
            </select>
          </div>
        </div>

        <table class="invoice-table" style="margin-top:10px;">
          <thead>
            <tr>
              <th>#</th>
              <th>Date</th>
              <th>Total</th>
              <th>Status</th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody id="analyticsInvoiceBody"></tbody>
        </table>
      </div>
    `;
  },

  init() {
    // populate datalist
    const dl = $("analyticsCustList");
    dl.innerHTML = "";
    customerModule.customers.forEach(c => {
      const opt = document.createElement("option");
      opt.value = `${c.name} (id:${c.id})`;
      dl.appendChild(opt);
    });

    $("analyticsSearchBtn").onclick = () => this.search();
    $("analyticsCustInput").onkeydown = e => {
      if (e.key === "Enter") this.search();
    };

    const filterSel = $("analyticsStatusFilter");
    if (filterSel) {
      filterSel.onchange = () => this.applyFilter();
    }
  },

  // -----------------------------------------------
  // SEARCH LOGIC
  // -----------------------------------------------
  async search() {
    const text = $("analyticsCustInput").value.trim();
    if (!text) return alert("Enter customer");

    let analytics;
    const id = extractId(text);

    try {
      if (id) {
        analytics = await invoiceModule.analyticsByCustomer(id);
      } else {
        const list = await invoiceModule.analyticsByName(text);
        analytics = (list && list.length) ? list[0] : null;
      }
    } catch (e) {
      console.error("Analytics fetch failed", e);
      $("analyticsSummary").textContent = "Error fetching analytics.";
      $("analyticsInvoiceBody").innerHTML = "";
      $("analyticsCards").innerHTML = "";
      return;
    }

    if (!analytics) {
      $("analyticsSummary").textContent = "No data found.";
      $("analyticsInvoiceBody").innerHTML = "";
      $("analyticsCards").innerHTML = "";
      this.currentAnalytics = null;
      this.currentInvoices = [];
      return;
    }

    this.currentAnalytics = analytics;
    this.currentInvoices = [...analytics.invoices];

    this.renderSummary(analytics);
    this.applyFilter();
  },

  // -----------------------------------------------
  // SUMMARY SECTION
  // -----------------------------------------------
  renderSummary(a) {
    const name = a.customerName || "(Unknown)";
    const cid = a.customerId ?? "-";

    $("analyticsSummary").innerHTML = `
      <b>${name}</b> • Customer ID: ${cid}
      <br>Total invoices: ${a.invoiceCount ?? 0}
    `;

    const totalBusiness = a.totalBusiness ?? 0;
    const totalPaid = a.totalPaid ?? 0;
    const outstanding = totalBusiness - totalPaid;

    $("analyticsCards").innerHTML = `
      <div class="invoice-total-box" style="flex:1;min-width:180px;">
        Total Business<br><b>${money(totalBusiness)}</b>
      </div>
      <div class="invoice-total-box" style="flex:1;min-width:180px;">
        Paid Amount<br><b>${money(totalPaid)}</b>
      </div>
      <div class="invoice-total-box" style="flex:1;min-width:180px;">
        Outstanding<br><b>${money(outstanding)}</b>
      </div>
    `;
  },

  // -----------------------------------------------
  // FILTER + SORT
  // -----------------------------------------------
  applyFilter() {
    const filterSel = $("analyticsStatusFilter");
    if (!this.currentInvoices || !filterSel) {
      this.renderInvoices([]);
      return;
    }

    const filter = filterSel.value;

    // SORT newest first
    const sorted = this.currentInvoices
      .slice()
      .sort((a, b) => {
        const da = a.invoiceDate ? new Date(a.invoiceDate).getTime() : 0;
        const db = b.invoiceDate ? new Date(b.invoiceDate).getTime() : 0;
        return db - da;
      });

    let final = sorted;

    if (filter === "PAID") {
      final = sorted.filter(inv => inv.paid);
    } else if (filter === "UNPAID") {
      final = sorted.filter(inv => !inv.paid);
    }

    this.renderInvoices(final);
  },

  // -----------------------------------------------
  // RENDER INVOICE TABLE
  // -----------------------------------------------
  renderInvoices(list) {
    const body = $("analyticsInvoiceBody");
    body.innerHTML = "";

    if (!list.length) {
      body.innerHTML = `
        <tr><td colspan="5" class="small muted">No invoices found.</td></tr>
      `;
      return;
    }

    list.forEach(inv => {
      const id = inv.invoiceId ?? inv.id;
      const paid = !!inv.paid;

      const tr = document.createElement("tr");
      tr.innerHTML = `
        <td>${inv.invoiceNumber}</td>
        <td>${inv.invoiceDate ? inv.invoiceDate.slice(0, 10) : "-"}</td>
        <td>${money(inv.totalAmount)}</td>
        <td>${paid ? "Paid" : "Unpaid"}</td>
        <td>
          <button 
            class="btn small ghost analytics-toggle-paid"
            data-id="${id}">
            ${paid ? "Mark Unpaid" : "Mark Paid"}
          </button>
        </td>
      `;

      body.appendChild(tr);
    });

    // attach handlers
    body.querySelectorAll(".analytics-toggle-paid").forEach(btn => {
      btn.onclick = () => this.togglePaid(btn);
    });
  },

  // -----------------------------------------------
  // MARK PAID / UNPAID
  // -----------------------------------------------
  async togglePaid(btn) {
    const id = Number(btn.dataset.id);
    if (!id) return;

    const inv = this.currentInvoices.find(i => (i.invoiceId ?? i.id) === id);
    if (!inv) return;

    const newStatus = !inv.paid;
    const label = newStatus ? "PAID" : "UNPAID";

    const ok = confirm(`Mark ${inv.invoiceNumber} as ${label}?`);
    if (!ok) return;

    try {
      await invoiceModule.markPaid(id, newStatus);
    } catch (e) {
      console.error("Failed updating paid flag", e);
      alert("Failed to update paid/unpaid.");
      return;
    }

    // update local
    inv.paid = newStatus;

    // update summary
    const a = this.currentAnalytics;
    const amount = inv.totalAmount || 0;

    if (newStatus) {
      a.totalPaid += amount;
      a.totalPending -= amount;
    } else {
      a.totalPaid -= amount;
      a.totalPending += amount;
    }

    this.renderSummary(a);
    this.applyFilter();
  }
};
