// ui/invoice-list.js
import { invoiceModule } from "../invoice.js";
import { $, money } from "../utils.js";
import { invoiceEdit } from "./invoice-edit.js";

export const invoiceList = {

  render() {
    return `
      <div class="card">
        <h2>Invoices</h2>

        <button class="btn ghost" id="reloadInvoices">Reload All</button>
        <div id="invList"></div>
        <div id="invDetails"></div>
      </div>`;
  },

  init() {
    document.getElementById("reloadInvoices").onclick = () => this.load();

    this.load();
  },

  async load() {
    const list = await invoiceModule.list();
    this.renderList(list);
  },

  renderList(list) {
    const box = $("invList");
    box.innerHTML = "";

    list.forEach(inv => {
      const div = document.createElement("div");
      div.className = "invoice-list-item";
      div.innerHTML = `
        <b>${inv.invoiceNumber}</b>
        <div class="small muted">${inv.customer?.name}</div>
        <div>${money(inv.totalAmount)}</div>
      `;
      div.onclick = () => invoiceEdit.open(inv.id);
      box.appendChild(div);
    });
  }
};
