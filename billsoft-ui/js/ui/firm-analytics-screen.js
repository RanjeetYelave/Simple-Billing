// js/ui/firm-analytics-screen.js
import { $, money } from "../utils.js";
import { invoiceModule } from "../invoice.js";

export const firmAnalyticsScreen = {

  render() {
    return `
      <div class="card firm-dashboard">
        <h2 class="fd-title">Firm Dashboard</h2>

        <div id="fdKpiGrid" class="fd-kpi-grid"></div>

        <h3 class="fd-section-title">Business Summary</h3>
        <div id="firmSummary" class="fd-summary-box"></div>
      </div>
    `;
  },

  async init() {
    const list = await invoiceModule.list();
    this.calculate(list);
  },

  calculate(list) {
    let total = 0;
    let paid = 0;
    let pending = 0;

    const today = new Date().toISOString().slice(0,10);
    const month = today.slice(0,7);
    const year = today.slice(0,4);

    let todaySum = 0;
    let monthSum = 0;
    let yearSum = 0;

    list.forEach(inv => {
      const amt = Number(inv.totalAmount || 0);
      const date = inv.invoiceDate?.slice(0, 10);

      total += amt;
      if (inv.paid) paid += amt;
      else pending += amt;

      if (date === today) todaySum += amt;
      if (date?.startsWith(month)) monthSum += amt;
      if (date?.startsWith(year)) yearSum += amt;
    });

    this.renderCards({ total, paid, pending, todaySum, monthSum, yearSum });

    $("firmSummary").innerHTML = `
      <div class="fd-summary-row"><span>Total invoices:</span> <b>${list.length}</b></div>
      <div class="fd-summary-row"><span>Paid invoices:</span> <b>${list.filter(x => x.paid).length}</b></div>
      <div class="fd-summary-row"><span>Unpaid invoices:</span> <b>${list.filter(x => !x.paid).length}</b></div>
    `;
  },

  renderCards(data) {
    $("fdKpiGrid").innerHTML = `
      <div class="fd-kpi-card fd-accent-blue">
        <div class="fd-kpi-icon">💼</div>
        <div class="fd-kpi-title">Total Business</div>
        <div class="fd-kpi-value">${money(data.total)}</div>
      </div>

      <div class="fd-kpi-card fd-accent-green">
        <div class="fd-kpi-icon">✔️</div>
        <div class="fd-kpi-title">Paid Amount</div>
        <div class="fd-kpi-value">${money(data.paid)}</div>
      </div>

      <div class="fd-kpi-card fd-accent-orange">
        <div class="fd-kpi-icon">⏳</div>
        <div class="fd-kpi-title">Outstanding</div>
        <div class="fd-kpi-value">${money(data.pending)}</div>
      </div>

      <div class="fd-kpi-card fd-accent-cyan">
        <div class="fd-kpi-title small">Today</div>
        <div class="fd-kpi-value small">${money(data.todaySum)}</div>
      </div>

      <div class="fd-kpi-card fd-accent-purple">
        <div class="fd-kpi-title small">This Month</div>
        <div class="fd-kpi-value small">${money(data.monthSum)}</div>
      </div>

      <div class="fd-kpi-card fd-accent-pink">
        <div class="fd-kpi-title small">This Year</div>
        <div class="fd-kpi-value small">${money(data.yearSum)}</div>
      </div>
    `;
  }
};
