// js/ui/analytics-screen.js
import { $, money, extractId } from "../utils.js";
import { customerModule } from "../customer.js";
import { invoiceModule } from "../invoice.js";

export const analyticsScreen = {

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

        <h3 style="margin-top:18px">Invoices</h3>
        <table class="invoice-table">
          <thead>
            <tr>
              <th>#</th>
              <th>Date</th>
              <th>Total</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody id="analyticsInvoiceBody"></tbody>
        </table>
      </div>
    `;
  },

  init() {
    // load customers into datalist
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
  },

  async search() {
    const text = $("analyticsCustInput").value.trim();
    if (!text) return alert("Enter customer");

    let analytics;
    const id = extractId(text);

    if (id) analytics = await invoiceModule.analyticsByCustomer(id);
    else analytics = await invoiceModule.analyticsByName(text);

    if (!analytics) {
      $("analyticsSummary").textContent = "No data found.";
      $("analyticsInvoiceBody").innerHTML = "";
      $("analyticsCards").innerHTML = "";
      return;
    }

    this.renderSummary(analytics);
    this.renderInvoices(analytics.invoices || []);
  },

  renderSummary(a) {
    $("analyticsSummary").innerHTML = `
      <b>${a.customerName}</b> • Customer ID: ${a.customerId}
      <br>Total invoices: ${a.totalInvoices}
    `;

    const totalBusiness = a.totalBusiness ?? a.totalAmount ?? 0;
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

  renderInvoices(list) {
    const body = $("analyticsInvoiceBody");
    body.innerHTML = "";

    list.forEach(inv => {
      const tr = document.createElement("tr");
      tr.innerHTML = `
        <td>${inv.invoiceNumber}</td>
        <td>${inv.invoiceDate?.slice(0,10) || "-"}</td>
        <td>${money(inv.totalAmount)}</td>
        <td>${inv.paid ? "Paid" : "Unpaid"}</td>
      `;
      body.appendChild(tr);
    });
  }
};
