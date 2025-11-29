// js/ui/statement-screen.js
import { $ , money } from "../utils.js";
import { customerModule } from "../customer.js";
import { invoiceModule } from "../invoice.js";
import { api } from "../api.js";

/*
  Statements Screen (Tabs)
  - Customer Statement tab: select customer, date range, Generate, Download PDF
  - Firm Statement tab: date range, Generate, Download PDF

  NOTE: This file only imports $ and money from utils.js to avoid missing-export errors.
*/

// small local helper (avoids relying on formatDateIso from utils)
function formatDateIso(v) {
  if (!v) return "-";
  // if it's a simple iso date string (yyyy-MM-dd) return as-is
  if (typeof v === "string" && /^\d{4}-\d{2}-\d{2}$/.test(v)) return v;
  try {
    const d = new Date(v);
    if (isNaN(d)) return String(v);
    return d.toISOString().slice(0, 10);
  } catch (e) {
    return String(v);
  }
}

export const statementScreen = {
  render() {
    return `
      <div class="card">
        <div style="display:flex;justify-content:space-between;align-items:center;">
          <div>
            <h2>Statements</h2>
            <div class="small muted">Customer and firm statements — ledger-style. Download PDF or view online.</div>
          </div>

          <div style="display:flex;gap:8px;align-items:center;">
            <button id="btn-refresh-stmt" class="btn small ghost">Refresh</button>
          </div>
        </div>

        <div style="margin-top:14px;">
          <div id="stmtTabs" style="display:flex;gap:8px;">
            <button class="btn small ghost tab-btn active" data-tab="customer">Customer Statement</button>
            <button class="btn small ghost tab-btn" data-tab="firm">Firm Statement</button>
          </div>

          <div id="stmtContent" style="margin-top:14px;">
            <!-- customer tab -->
            <div class="stmt-tab view active" data-tab="customer">
              <div style="display:flex;gap:12px;flex-wrap:wrap;align-items:end;">
                <div style="min-width:240px;flex:1;">
                  <label>Customer</label>
                  <select id="stmtCustomer" style="width:100%;"></select>
                </div>

                <div style="width:160px;">
                  <label>From</label>
                  <input id="stmtFrom" type="date" />
                </div>

                <div style="width:160px;">
                  <label>To</label>
                  <input id="stmtTo" type="date" />
                </div>

                <div style="display:flex;gap:8px;">
                  <button id="btnGenerateCustomerStmt" class="btn small">Generate</button>
                  <button id="btnDownloadCustomerPdf" class="btn small ghost">Download PDF</button>
                </div>
              </div>

              <div id="customerStmtSummary" class="card small muted" style="margin-top:12px;"></div>

              <div id="customerStmtTableWrap" style="margin-top:12px;"></div>
            </div>

            <!-- firm tab -->
            <div class="stmt-tab view" data-tab="firm">
              <div style="display:flex;gap:12px;flex-wrap:wrap;align-items:end;">
                <div style="width:160px;">
                  <label>From</label>
                  <input id="firmFrom" type="date" />
                </div>

                <div style="width:160px;">
                  <label>To</label>
                  <input id="firmTo" type="date" />
                </div>

                <div style="display:flex;gap:8px;">
                  <button id="btnGenerateFirmStmt" class="btn small">Generate</button>
                  <button id="btnDownloadFirmPdf" class="btn small ghost">Download PDF</button>
                </div>
              </div>

              <div id="firmStmtSummary" class="card small muted" style="margin-top:12px;"></div>

              <div id="firmStmtBody" style="margin-top:12px;"></div>
            </div>
          </div>
        </div>
      </div>
    `;
  },

  async init() {
    this.bindTabClicks();
    await this.loadCustomers();
    this.setDefaultDates();
    this.attachEventHandlers();
  },

  bindTabClicks() {
    // querySelectorAll is safe and avoids needing $$ export
    const tabs = document.querySelectorAll(".tab-btn");
    tabs.forEach(t => t.addEventListener("click", (ev) => {
      tabs.forEach(x => x.classList.remove("active"));
      ev.currentTarget.classList.add("active");
      const tab = ev.currentTarget.dataset.tab;
      document.querySelectorAll(".stmt-tab").forEach(v => {
        v.classList.toggle("active", v.dataset.tab === tab);
      });
    }));
  },

  async loadCustomers() {
    try {
      const customers = await customerModule.list();
      const sel = $("stmtCustomer");
      sel.innerHTML = `<option value="">-- Select customer --</option>`;
      customers.forEach(c => {
        const opt = document.createElement("option");
        opt.value = c.id;
        opt.textContent = c.name + (c.phone ? ` • ${c.phone}` : "");
        sel.appendChild(opt);
      });
    } catch (err) {
      console.error("Failed loading customers", err);
      const sel = $("stmtCustomer");
      if (sel) sel.innerHTML = `<option value="">(failed to load customers)</option>`;
    }
  },

  setDefaultDates() {
    const today = new Date().toISOString().slice(0,10);
    $("stmtTo").value = today;
    $("firmTo").value = today;
    $("stmtFrom").value = "";
    $("firmFrom").value = "";
  },

  attachEventHandlers() {
    const btnGenerateCustomer = $("btnGenerateCustomerStmt");
    const btnDownloadCustomer = $("btnDownloadCustomerPdf");
    const btnGenerateFirm = $("btnGenerateFirmStmt");
    const btnDownloadFirm = $("btnDownloadFirmPdf");
    const btnRefresh = $("btn-refresh-stmt");

    if (btnGenerateCustomer) btnGenerateCustomer.onclick = async () => { await this.generateCustomerStatement(false); };
    if (btnDownloadCustomer) btnDownloadCustomer.onclick = async () => { await this.generateCustomerStatement(true); };
    if (btnGenerateFirm) btnGenerateFirm.onclick = async () => { await this.generateFirmStatement(false); };
    if (btnDownloadFirm) btnDownloadFirm.onclick = async () => { await this.generateFirmStatement(true); };
    if (btnRefresh) btnRefresh.onclick = async () => {
      await this.loadCustomers();
      const activeTab = document.querySelector(".tab-btn.active")?.dataset?.tab;
      if (activeTab === "customer") await this.generateCustomerStatement(false);
      else await this.generateFirmStatement(false);
    };
  },

  async generateCustomerStatement(isDownload) {
    const custId = $("stmtCustomer").value;
    if (!custId) {
      alert("Please select a customer");
      return;
    }
    const from = $("stmtFrom").value || null;
    const to = $("stmtTo").value || null;

    if (isDownload) {
      try {
        const blob = await api.downloadCustomerStatementPdf(custId, from, to);
        this.downloadBlob(blob, `statement-customer-${custId}.pdf`);
      } catch (e) {
        console.error(e);
        alert("Failed to download PDF. See console.");
      }
      return;
    }

    try {
      const data = await api.getCustomerStatement(custId, from, to);
      this.renderCustomerStatement(data);
    } catch (err) {
      console.error("Customer statement failed", err);
      $("customerStmtTableWrap").innerHTML = `<div class="card small muted">Failed to load statement.</div>`;
    }
  },

  renderCustomerStatement(data) {
    $("customerStmtSummary").innerHTML = `
      <b>Customer:</b> ${data.customerName} &nbsp; | &nbsp;
      <b>Period:</b> ${formatDateIso(data.from)} → ${formatDateIso(data.to)} &nbsp; | &nbsp;
      <b>Opening:</b> ${money(data.openingBalance)} &nbsp; | &nbsp;
      <b>Closing:</b> ${money(data.closingBalance)}
    `;

    const rows = (data.entries || []).map(e => `
      <tr>
        <td style="white-space:nowrap;">${e.date ? formatDateIso(e.date) : "-"}</td>
        <td>${e.type || "-"}</td>
        <td>${e.ref || "-"}</td>
        <td>${e.description || "-"}</td>
        <td class="numeric">${money(e.debit)}</td>
        <td class="numeric">${money(e.credit)}</td>
        <td class="numeric">${money(e.balance)}</td>
      </tr>
    `).join("");

    $("customerStmtTableWrap").innerHTML = `
      <div class="card">
        <table class="invoice-table">
          <thead>
            <tr>
              <th>Date</th><th>Type</th><th>Ref</th><th>Description</th>
              <th style="text-align:right;">Debit</th><th style="text-align:right;">Credit</th><th style="text-align:right;">Balance</th>
            </tr>
          </thead>
          <tbody>
            ${rows || `<tr><td colspan="7">No entries</td></tr>`}
          </tbody>
        </table>
      </div>
    `;
  },

  async generateFirmStatement(isDownload) {
    const from = $("firmFrom").value || null;
    const to = $("firmTo").value || null;

    if (isDownload) {
      try {
        const blob = await api.downloadFirmStatementPdf(from, to);
        this.downloadBlob(blob, `statement-firm.pdf`);
      } catch (e) {
        console.error(e);
        alert("Failed to download PDF. See console.");
      }
      return;
    }

    try {
      const data = await api.getFirmStatement(from, to);
      this.renderFirmStatement(data);
    } catch (err) {
      console.error("Firm statement failed", err);
      $("firmStmtBody").innerHTML = `<div class="card small muted">Failed to load firm statement.</div>`;
    }
  },

  renderFirmStatement(data) {
    $("firmStmtSummary").innerHTML = `
      <b>Period:</b> ${formatDateIso(data.from)} → ${formatDateIso(data.to)} &nbsp; | &nbsp;
      <b>Total:</b> ${money(data.totalBilled)} &nbsp; | &nbsp;
      <b>Paid:</b> ${money(data.totalPaid)} &nbsp; | &nbsp;
      <b>Outstanding:</b> ${money(data.outstanding)}
    `;

    const gstRows = (data.gstSummary || []).map(g => `
      <tr>
        <td>${g.gstPercent}%</td>
        <td class="numeric">${money(g.taxableValue)}</td>
        <td class="numeric">${money(g.gstAmount)}</td>
      </tr>
    `).join("");

    $("firmStmtBody").innerHTML = `
      <div class="card">
        <h3>GST Summary</h3>
        <table class="invoice-table">
          <thead>
            <tr><th>GST %</th><th style="text-align:right;">Taxable</th><th style="text-align:right;">GST Amount</th></tr>
          </thead>
          <tbody>
            ${gstRows || `<tr><td colspan="3">No GST data</td></tr>`}
          </tbody>
        </table>
      </div>
    `;
  },

  downloadBlob(blob, filename) {
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
  }
};
