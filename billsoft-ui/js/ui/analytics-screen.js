// js/ui/analytics-screen.js
import { $, money, extractId } from "../utils.js";
import { customerModule } from "../customer.js";
import { invoiceModule } from "../invoice.js";

export const analyticsScreen = {
  currentAnalytics: null,
  currentInvoices: [],
  render() {
    return `
      <div class="card">
        <h2>Customer Analytics</h2>
        <div style="display:grid;grid-template-columns:2fr auto;gap:10px;align-items:end">
          <div>
            <label>Customer</label>
            <input id="analyticsCustInput" list="analyticsCustList" placeholder="Type customer name or id:3" />
            <datalist id="analyticsCustList"></datalist>
          </div>
          <button class="btn primary" id="analyticsSearchBtn">🔍 Analyse</button>
        </div>

        <div id="analyticsSummary" class="small muted" style="margin-top:10px;">Enter customer name or id and click Analyse.</div>

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
              <th>#</th><th>Date</th><th>Total</th><th>Status</th><th>Action</th>
            </tr>
          </thead>
          <tbody id="analyticsInvoiceBody"></tbody>
        </table>
      </div>
    `;
  },

  init() {
    const dl = $("analyticsCustList"); if (dl) { dl.innerHTML = ""; customerModule.customers.forEach(c => { const opt = document.createElement("option"); opt.value = `${c.name} (id:${c.id})`; dl.appendChild(opt); }); }
    $("analyticsSearchBtn").onclick = () => this.search();
    $("analyticsCustInput").onkeydown = e => { if (e.key === "Enter") this.search(); };
    const filterSel = $("analyticsStatusFilter"); if (filterSel) filterSel.onchange = () => this.applyFilter();
  },

  async search() {
    const text = ($("analyticsCustInput")?.value || "").trim();
    if (!text) return alert("Enter customer");
    let analytics;
    const id = extractId(text);
    try {
      if (id) {
        // backend path returns CustomerAnalyticsResponse
        analytics = await invoiceModule.analyticsByCustomer ? await invoiceModule.analyticsByCustomer(id) : await (await fetch(`/api/invoices/analytics/customer/${id}`)).json();
      } else {
        const list = await invoiceModule.analyticsByName ? await invoiceModule.analyticsByName(text) : await (await fetch(`/api/invoices/analytics/search?name=${encodeURIComponent(text)}`)).json();
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

    // Normalise invoices list to UI shape (id, invoiceId)
    this.currentAnalytics = analytics;
    this.currentInvoices = (analytics.invoices || []).map(inv => ({
      id: inv.id ?? inv.invoiceId,
      invoiceId: inv.id ?? inv.invoiceId,
      invoiceNumber: inv.invoiceNumber,
      invoiceDate: inv.invoiceDate,
      totalAmount: inv.totalAmount ?? inv.totalAmount,
      paid: inv.paid,
      status: inv.status
    }));

    this.renderSummary(analytics);
    this.applyFilter();
  },

  renderSummary(a) {
    const name = a.customerName || "(Unknown)";
    const cid = a.customerId ?? "-";
    $("analyticsSummary").innerHTML = `<b>${name}</b> • Customer ID: ${cid}<br>Total invoices: ${a.invoiceCount ?? 0}`;
    const totalBusiness = Number(a.totalBusiness || 0);
    const totalPaid = Number(a.totalPaid || 0);
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

  applyFilter() {
    const filterSel = $("analyticsStatusFilter");
    if (!this.currentInvoices || !filterSel) { this.renderInvoices([]); return; }
    const filter = filterSel.value;
    const sorted = this.currentInvoices.slice().sort((a,b) => {
      const da = a.invoiceDate ? new Date(a.invoiceDate).getTime() : 0;
      const db = b.invoiceDate ? new Date(b.invoiceDate).getTime() : 0;
      return db - da;
    });
    let final = sorted;
    if (filter === "PAID") final = sorted.filter(inv => inv.paid === true || inv.status === "PAID");
    else if (filter === "UNPAID") final = sorted.filter(inv => !(inv.paid || inv.status === "PAID"));
    this.renderInvoices(final);
  },

  renderInvoices(list) {
    const body = $("analyticsInvoiceBody");
    body.innerHTML = "";
    if (!list.length) { body.innerHTML = `<tr><td colspan="5" class="small muted">No invoices found.</td></tr>`; return; }
    list.forEach(inv => {
      const id = inv.invoiceId ?? inv.id;
      const paid = !!inv.paid;
      const tr = document.createElement("tr");
      tr.innerHTML = `
        <td>${inv.invoiceNumber}</td>
        <td>${inv.invoiceDate ? inv.invoiceDate.slice(0,10) : "-"}</td>
        <td>${money(inv.totalAmount)}</td>
        <td>${paid ? "Paid" : (inv.status || "Unpaid")}</td>
        <td><button class="btn small ghost analytics-toggle-paid" data-id="${id}">${paid ? "Mark Unpaid" : "Mark Paid"}</button></td>
      `;
      body.appendChild(tr);
    });

    body.querySelectorAll(".analytics-toggle-paid").forEach(btn => btn.onclick = () => this.togglePaid(btn));
  },

  async togglePaid(btn) {
    const id = Number(btn.dataset.id);
    if (!id) return;
    const inv = this.currentInvoices.find(i => (i.invoiceId ?? i.id) === id);
    if (!inv) return;
    const newStatus = !inv.paid;
    const label = newStatus ? "PAID" : "UNPAID";
    if (!confirm(`Mark ${inv.invoiceNumber} as ${label}?`)) return;
    try {
      await invoiceModule.markPaid(id, newStatus);
      inv.paid = newStatus;
      const a = this.currentAnalytics;
      const amount = Number(inv.totalAmount || 0);
      if (newStatus) { a.totalPaid = (Number(a.totalPaid || 0) + amount); a.totalPending = (Number(a.totalPending || 0) - amount); }
      else { a.totalPaid = (Number(a.totalPaid || 0) - amount); a.totalPending = (Number(a.totalPending || 0) + amount); }
      this.renderSummary(a);
      this.applyFilter();
    } catch (e) {
      console.error("Failed updating paid flag", e);
      alert("Failed to update paid/unpaid.");
    }
  }
};
