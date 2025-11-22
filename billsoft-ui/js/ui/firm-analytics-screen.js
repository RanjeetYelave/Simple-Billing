// js/ui/firm-analytics-screen.js
import { $, money } from "../utils.js";
import { invoiceModule } from "../invoice.js";

export const firmAnalyticsScreen = {

  render() {
    return `
      <div class="card">
        <h2>Firm Dashboard</h2>

        <div id="firmCards"
             style="display:flex;gap:12px;flex-wrap:wrap;margin-top:12px;">
        </div>

        <h3 style="margin-top:20px;">Business Summary</h3>
        <div id="firmSummary" class="small muted"></div>
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
      const date = inv.invoiceDate?.slice(0,10);

      total += amt;
      if (inv.paid) paid += amt;
      else pending += amt;

      if (date === today) todaySum += amt;
      if (date?.startsWith(month)) monthSum += amt;
      if (date?.startsWith(year)) yearSum += amt;
    });

    this.renderCards({ total, paid, pending, todaySum, monthSum, yearSum });

    $("firmSummary").innerHTML = `
      Total invoices: ${list.length}<br>
      Paid invoices: ${list.filter(x => x.paid).length}<br>
      Unpaid invoices: ${list.filter(x => !x.paid).length}
    `;
  },

  renderCards(data) {
    $("firmCards").innerHTML = `
      <div class="invoice-total-box" style="flex:1;min-width:200px;">
        Total Business<br><b>${money(data.total)}</b>
      </div>

      <div class="invoice-total-box" style="flex:1;min-width:200px;">
        Paid Amount<br><b>${money(data.paid)}</b>
      </div>

      <div class="invoice-total-box" style="flex:1;min-width:200px;">
        Outstanding<br><b>${money(data.pending)}</b>
      </div>

      <div class="invoice-total-box" style="flex:1;min-width:200px;">
        Today<br><b>${money(data.todaySum)}</b>
      </div>

      <div class="invoice-total-box" style="flex:1;min-width:200px;">
        This Month<br><b>${money(data.monthSum)}</b>
      </div>

      <div class="invoice-total-box" style="flex:1;min-width:200px;">
        This Year<br><b>${money(data.yearSum)}</b>
      </div>
    `;
  }
};
