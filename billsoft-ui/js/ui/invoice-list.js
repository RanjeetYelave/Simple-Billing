import { invoiceModule } from "../invoice.js";
import { $, money } from "../utils.js";
import { invoiceEdit } from "./invoice-edit.js";
import { pdfViewer } from "../pdf-viewer.js";

export const invoiceList = {

  allInvoices: [],
  currentPage: 1,
  pageSize: 10,
  currentSort: "DATE_DESC",

  _docClickHandler: null,
  _docClickHandlerAdded: false,

  render() {
    return `
      <div class="card">
        <h2>Invoices</h2>

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

          <div style="margin-left:auto;display:flex;gap:10px;align-items:flex-end;">
            <div>
              <label class="small muted">Sort</label>
              <select id="invSortSelect">
                <option value="DATE_DESC">Latest first</option>
                <option value="DATE_ASC">Oldest first</option>
                <option value="AMOUNT_DESC">Amount high → low</option>
                <option value="AMOUNT_ASC">Amount low → high</option>
              </select>
            </div>

            <button class="btn ghost small" id="reloadInvoices">Reload All</button>
          </div>
        </div>

        <table class="invoice-table">
          <thead>
            <tr>
              <th>Invoice</th>
              <th>Customer</th>
              <th>Date</th>
              <th>Total</th>
              <th>Status</th>
              <th style="width:260px;">Actions</th>
            </tr>
          </thead>
          <tbody id="invTableBody"></tbody>
        </table>

        <div id="invPagination"
             class="small muted"
             style="margin-top:10px;display:flex;justify-content:space-between;align-items:center;">
          <div id="invPaginationInfo"></div>
          <div style="display:flex;gap:6px;">
            <button class="btn small ghost" id="invPrevPage">Prev</button>
            <button class="btn small ghost" id="invNextPage">Next</button>
          </div>
        </div>

        <div id="invDetails" style="margin-top:16px;"></div>
      </div>
    `;
  },

  init() {
    this.allInvoices = [];
    this.currentPage = 1;
    this.currentSort = "DATE_DESC";

    $("reloadInvoices").onclick = () => this.loadAll();
    $("invSearchIdBtn").onclick = () => this.searchById();
    $("invSearchCustomerBtn").onclick = () => this.searchByCustomer();

    $("invSortSelect").onchange = () => {
      this.currentSort = $("invSortSelect").value || "DATE_DESC";
      this.currentPage = 1;
      this.renderTable();
    };

    $("invPrevPage").onclick = () => {
      if (this.currentPage > 1) {
        this.currentPage--;
        this.renderTable();
      }
    };

    $("invNextPage").onclick = () => {
      if (this.currentPage < this.getTotalPages()) {
        this.currentPage++;
        this.renderTable();
      }
    };

    if (!this._docClickHandlerAdded) {
      this._docClickHandler = (e) => {
        if (!e.target.closest(".pdf-drop") && !e.target.closest(".pdf-menu")) {
          document.querySelectorAll(".pdf-menu").forEach(m => m.classList.add("hidden"));
        }
      };
      document.addEventListener("click", this._docClickHandler);
      this._docClickHandlerAdded = true;
    }

    this.loadAll();
  },

  async loadAll() {
    try {
      const list = await invoiceModule.list();
      this.setData(list);
    } catch (err) {
      console.error("Failed to load invoices", err);
      $("invTableBody").innerHTML =
        `<tr><td colspan="6" class="small muted">Failed to load invoices.</td></tr>`;
      $("invPaginationInfo").textContent = "";
    }
  },

  async searchById() {
    const id = Number($("invSearchId").value.trim());
    if (!id) return alert("Enter an Invoice ID");

    try {
      const inv = await invoiceModule.preview(id);
      if (!inv) {
        this.setData([]);
        $("invTableBody").innerHTML =
          `<tr><td colspan="6" class="small muted">Invoice not found.</td></tr>`;
        return;
      }
      this.setData([inv]);
    } catch (err) {
      console.error("Search by ID failed", err);
    }
  },

  async searchByCustomer() {
    const cid = Number($("invSearchCustomer").value.trim());
    if (!cid) return alert("Enter a Customer ID");

    const all = await invoiceModule.list();
    this.setData(all.filter(i =>
      i.customer?.id && Number(i.customer.id) === cid
    ));
  },

  setData(list) {
    this.allInvoices = Array.isArray(list) ? list : [];
    this.currentPage = 1;
    this.renderTable();
  },

  // STATUS LOGIC UPDATED ✔
  async togglePaid(inv) {
    const willBePaid = !inv.paid;
    const newStatus = willBePaid ? "PAID" : "FINAL";

    const ok = confirm(
      `Mark invoice ${inv.invoiceNumber || inv.estimateNumber} as ${newStatus}?`
    );
    if (!ok) return;

    try {
      await invoiceModule.update(inv.id, {
        paid: willBePaid,
        status: newStatus
      });

      inv.status = newStatus;
      inv.paid = willBePaid;
      this.renderTable();
    } catch (e) {
      console.error(e);
      alert("Status update failed");
    }
  },

  async deleteInvoice(inv) {
    if (!confirm(`Delete invoice ${inv.invoiceNumber}?`)) return;
    await invoiceModule.delete(inv.id);
    this.allInvoices = this.allInvoices.filter(x => x.id !== inv.id);
    this.renderTable();
  },

  async openPdf(inv, size = "A4") {
    try {
      const blob = await invoiceModule.pdf(inv.id, size);
      pdfViewer.open(blob,
        `invoice-${inv.invoiceNumber || inv.id}.pdf`,
        `Invoice ${inv.invoiceNumber || inv.id}`);
    } catch (err) {
      console.error("PDF fetch failed", err);
    }
  },

  getTotalPages() {
    return Math.max(1, Math.ceil(this.allInvoices.length / this.pageSize));
  },

  getSortedInvoices() {
    const sorted = [...this.allInvoices];
    const key = this.currentSort;

    sorted.sort((a, b) => {
      const da = new Date(a.invoiceDate ?? 0).getTime();
      const db = new Date(b.invoiceDate ?? 0).getTime();
      const aa = Number(a.totalAmount ?? 0);
      const ab = Number(b.totalAmount ?? 0);

      switch (key) {
        case "DATE_ASC": return da - db;
        case "AMOUNT_DESC": return ab - aa;
        case "AMOUNT_ASC": return aa - ab;
        case "DATE_DESC":
        default:
          if (db !== da) return db - da;
          return (b.id ?? 0) - (a.id ?? 0);
      }
    });
    return sorted;
  },

  renderTable() {
    const body = $("invTableBody");
    const info = $("invPaginationInfo");

    if (!this.allInvoices.length) {
      body.innerHTML = `<tr><td colspan="6" class="small muted">No invoices found.</td></tr>`;
      info.textContent = "0 invoices";
      $("invDetails").innerHTML = "";
      return;
    }

    const sorted = this.getSortedInvoices();
    const totalPages = this.getTotalPages();
    const start = (this.currentPage - 1) * this.pageSize;
    const pageItems = sorted.slice(start, start + this.pageSize);

    body.innerHTML = "";

    pageItems.forEach(inv => {
      const isPaid = inv.status === "PAID";
      const isEstimate = inv.status === "ESTIMATE";

      const statusColor = {
        PAID: "#22c55e",
        ESTIMATE: "#38bdf8",
        DRAFT: "#a78bfa",
        OVERDUE: "#fb923c",
        CANCELLED: "#9ca3af",
        SENT: "#3b82f6",
        FINAL: "#f97373",
      }[inv.status] || "#f97373";

      const displayNumber =
        inv.invoiceNumber ||
        inv.estimateNumber ||
        `#${inv.id}`;

      const tr = document.createElement("tr");
      tr.innerHTML = `
        <td>${displayNumber}</td>
        <td>${inv.customer?.name || "—"}</td>
        <td>${inv.invoiceDate ? inv.invoiceDate.slice(0, 10) : "-"}</td>
        <td class="numeric">${money(inv.totalAmount)}</td>
        <td>
          <span class="small" style="color:${statusColor};">
            ${inv.status}
          </span>
        </td>
        <td>
          <div style="display:flex;gap:6px;flex-wrap:wrap;align-items:center;">
            <button class="btn small" data-view="${inv.id}">Edit</button>

            <button class="btn small ghost" data-paid="${inv.id}">
              ${isPaid ? "Mark Unpaid" : "Mark Paid"}
            </button>

            <button class="btn small danger" data-del="${inv.id}">Delete</button>

            <div style="display:flex;position:relative;">
              <button class="btn small ghost pdf-main" data-pdf-main="${inv.id}">
                PDF
              </button>
              <button class="btn small ghost pdf-drop" data-pdf-drop="${inv.id}">▾</button>
              <div class="pdf-menu hidden" id="pdfMenu-${inv.id}">
                <div class="pdf-option" data-pdf-size="A4" data-pdf-id="${inv.id}">Open A4</div>
                <div class="pdf-option" data-pdf-size="A5" data-pdf-id="${inv.id}">Open A5</div>
              </div>
            </div>
          </div>
        </td>
      `;
      body.appendChild(tr);
    });

    info.textContent = `Page ${this.currentPage} of ${totalPages} • ${this.allInvoices.length} invoice(s)`;
    $("invPrevPage").disabled = this.currentPage <= 1;
    $("invNextPage").disabled = this.currentPage >= totalPages;

    this.attachHandlers();
  },

  attachHandlers() {
    const body = $("invTableBody");

    body.querySelectorAll("button[data-view]").forEach(btn => {
      btn.onclick = () => invoiceEdit.open(Number(btn.dataset.view));
    });

    body.querySelectorAll("button[data-paid]").forEach(btn => {
      btn.onclick = () => {
        const inv = this.allInvoices.find(x => x.id === Number(btn.dataset.paid));
        this.togglePaid(inv);
      };
    });

    body.querySelectorAll("button[data-del]").forEach(btn => {
      btn.onclick = () => {
        const inv = this.allInvoices.find(x => x.id === Number(btn.dataset.del));
        this.deleteInvoice(inv);
      };
    });

    body.querySelectorAll("button[data-pdf-main]").forEach(btn => {
      btn.onclick = (e) => {
        e.stopPropagation();
        const inv = this.allInvoices.find(x => x.id === Number(btn.dataset.pdfMain));
        this.openPdf(inv, "A4");
      };
    });

    body.querySelectorAll("button[data-pdf-drop]").forEach(btn => {
      btn.onclick = e => {
        e.stopPropagation();
        const id = Number(btn.dataset.pdfDrop);
        document.querySelectorAll(".pdf-menu")
          .forEach(m => m.id !== `pdfMenu-${id}` && m.classList.add("hidden"));
        $(`pdfMenu-${id}`).classList.toggle("hidden");
      };
    });

    body.querySelectorAll(".pdf-option").forEach(opt => {
      opt.onclick = e => {
        e.stopPropagation();
        const inv = this.allInvoices.find(x => x.id === Number(opt.dataset.pdfId));
        this.openPdf(inv, opt.dataset.pdfSize);
        $(`pdfMenu-${opt.dataset.pdfId}`).classList.add("hidden");
      };
    });
  }
};
