// js/ui/invoice-list.js
import { invoiceModule } from "../invoice.js";
import { $, money } from "../utils.js";
import { invoiceEdit } from "./invoice-edit.js";
import { pdfViewer } from "../pdf-viewer.js";

export const invoiceList = {

  allInvoices: [],
  currentPage: 1,
  pageSize: 10,
  currentSort: "DATE_DESC",

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

    const sortSel = $("invSortSelect");
    if (sortSel) {
      sortSel.onchange = () => {
        this.currentSort = sortSel.value || "DATE_DESC";
        this.currentPage = 1;
        this.renderTable();
      };
    }

    $("invPrevPage").onclick = () => {
      if (this.currentPage > 1) {
        this.currentPage--;
        this.renderTable();
      }
    };

    $("invNextPage").onclick = () => {
      const totalPages = this.getTotalPages();
      if (this.currentPage < totalPages) {
        this.currentPage++;
        this.renderTable();
      }
    };

    this.loadAll();
  },

  // ---------------- LOAD / SEARCH ----------------

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
    const idText = $("invSearchId").value.trim();
    if (!idText) return alert("Enter an Invoice ID");

    const id = Number(idText);
    if (isNaN(id)) return alert("Invoice ID must be a number");

    try {
      const inv = await invoiceModule.preview(id);
      if (!inv || inv.id == null) {
        this.setData([]);
        $("invTableBody").innerHTML =
          `<tr><td colspan="6" class="small muted">Invoice not found.</td></tr>`;
        $("invPaginationInfo").textContent = "";
        return;
      }
      this.setData([inv]);
    } catch (err) {
      console.error("Search by ID failed", err);
      $("invTableBody").innerHTML =
        `<tr><td colspan="6" class="small muted">Error fetching invoice.</td></tr>`;
      $("invPaginationInfo").textContent = "";
    }
  },

  async searchByCustomer() {
    const cidText = $("invSearchCustomer").value.trim();
    if (!cidText) return alert("Enter a Customer ID");

    const cid = Number(cidText);
    if (isNaN(cid)) return alert("Customer ID must be a number");

    try {
      const all = await invoiceModule.list();
      const filtered = all.filter(
        inv => inv.customer && Number(inv.customer.id) === cid
      );
      this.setData(filtered);
    } catch (err) {
      console.error("Search by customer failed", err);
      $("invTableBody").innerHTML =
        `<tr><td colspan="6" class="small muted">Error fetching invoices.</td></tr>`;
      $("invPaginationInfo").textContent = "";
    }
  },

  setData(list) {
    this.allInvoices = Array.isArray(list) ? list : [];
    this.currentPage = 1;
    this.renderTable();
  },

  // ---------------- ACTIONS ----------------

  async togglePaid(inv) {
    const id = inv.id;
    const newStatus = !inv.paid;
    const label = newStatus ? "PAID" : "UNPAID";

    const ok = window.confirm(`Mark invoice ${inv.invoiceNumber} as ${label}?`);
    if (!ok) return;

    try {
      await invoiceModule.markPaid(id, newStatus);
      inv.paid = newStatus;
      this.renderTable();
    } catch (err) {
      console.error("Failed to update paid flag", err);
      alert("Failed to update paid status.");
    }
  },

  async deleteInvoice(inv) {
    const ok = window.confirm(
      `Delete invoice ${inv.invoiceNumber}? This cannot be undone.`
    );
    if (!ok) return;

    try {
      await invoiceModule.delete(inv.id);
      this.allInvoices = this.allInvoices.filter(x => x.id !== inv.id);
      if (this.currentPage > this.getTotalPages()) {
        this.currentPage = this.getTotalPages();
      }
      this.renderTable();
    } catch (err) {
      console.error("Failed to delete invoice", err);
      alert("Failed to delete invoice.");
    }
  },

  async openPdf(inv, size = "A4") {
    try {
      const blob = await invoiceModule.pdf(inv.id, size);
      const fname = `invoice-${inv.invoiceNumber || inv.id}.pdf`;
      pdfViewer.open(blob, fname, `Invoice ${inv.invoiceNumber || inv.id}`);
    } catch (e) {
      console.error("PDF fetch failed", e);
      alert("Failed to load PDF.");
    }
  },

  // ---------------- RENDER TABLE ----------------

  getTotalPages() {
    if (!this.allInvoices.length) return 1;
    return Math.ceil(this.allInvoices.length / this.pageSize);
  },

  getSortedInvoices() {
    const list = this.allInvoices.slice();
    const sortKey = this.currentSort;

    return list.sort((a, b) => {

      const da = a.invoiceDate ? new Date(a.invoiceDate).getTime() : 0;
      const db = b.invoiceDate ? new Date(b.invoiceDate).getTime() : 0;

      const aa = a.totalAmount ?? 0;
      const ab = b.totalAmount ?? 0;

      switch (sortKey) {
        case "DATE_ASC":
          return da - db;
        case "AMOUNT_DESC":
          return ab - aa;
        case "AMOUNT_ASC":
          return aa - ab;
        case "DATE_DESC":
        default:
          if (db !== da) return db - da;
          return (b.id ?? 0) - (a.id ?? 0);
      }
    });
  },

  renderTable() {
    const body = $("invTableBody");
    const info = $("invPaginationInfo");

    if (!this.allInvoices.length) {
      body.innerHTML =
        `<tr><td colspan="6" class="small muted">No invoices found.</td></tr>`;
      if (info) info.textContent = "0 invoices";
      $("invDetails").innerHTML = "";
      return;
    }

    const sorted = this.getSortedInvoices();
    const totalPages = this.getTotalPages();

    if (this.currentPage > totalPages) this.currentPage = totalPages;
    if (this.currentPage < 1) this.currentPage = 1;

    const start = (this.currentPage - 1) * this.pageSize;
    const pageItems = sorted.slice(start, start + this.pageSize);

    body.innerHTML = "";

    pageItems.forEach(inv => {
      const tr = document.createElement("tr");
      const isPaid = inv.paid === true;

      tr.innerHTML = `
        <td>${inv.invoiceNumber}</td>
        <td>${inv.customer?.name || "—"}</td>
        <td>${inv.invoiceDate ? inv.invoiceDate.slice(0, 10) : "-"}</td>
        <td class="numeric">${money(inv.totalAmount)}</td>
        <td>
          <span class="small" style="color:${isPaid ? "#22c55e" : "#f97373"};">
            ${isPaid ? "Paid" : "Unpaid"}
          </span>
        </td>
        <td>
          <div style="display:flex;gap:6px;flex-wrap:wrap;align-items:center;">

            <button class="btn small" data-view="${inv.id}">Edit</button>

            <button class="btn small ghost" data-paid="${inv.id}">
              ${isPaid ? "Mark Unpaid" : "Mark Paid"}
            </button>

            <button class="btn small danger" data-del="${inv.id}">Delete</button>

            <!-- PDF SPLIT BUTTON -->
            <div style="display:flex;position:relative;">
              <button class="btn small ghost pdf-main" data-pdf-main="${inv.id}">
                PDF
              </button>
              <button class="btn small ghost pdf-drop" data-pdf-drop="${inv.id}">
                ▾
              </button>

              <!-- Dropdown -->
              <div class="pdf-menu hidden" id="pdfMenu-${inv.id}"
                style="
                  position:absolute;
                  top:28px;
                  right:0;
                  background:#0f172a;
                  border:1px solid #334155;
                  border-radius:6px;
                  padding:4px;
                  z-index:50;
                ">
                <div class="pdf-option" data-pdf-size="A4" data-pdf-id="${inv.id}"
                     style="padding:6px 10px;cursor:pointer;">Open A4</div>
                <div class="pdf-option" data-pdf-size="A5" data-pdf-id="${inv.id}"
                     style="padding:6px 10px;cursor:pointer;">Open A5</div>
              </div>
            </div>

          </div>
        </td>
      `;
      body.appendChild(tr);
    });

    if (info) {
      info.textContent =
        `Page ${this.currentPage} of ${totalPages} • ${this.allInvoices.length} invoice${this.allInvoices.length === 1 ? "" : "s"}`;
    }

    $("invPrevPage").disabled = this.currentPage <= 1;
    $("invNextPage").disabled = this.currentPage >= totalPages;

    // Attach handlers
    this.attachHandlers();
  },

  attachHandlers() {
    const body = $("invTableBody");

    // Edit
    body.querySelectorAll("button[data-view]").forEach(btn => {
      btn.onclick = () => invoiceEdit.open(Number(btn.dataset.view));
    });

    // Paid toggle
    body.querySelectorAll("button[data-paid]").forEach(btn => {
      btn.onclick = () => {
        const inv = this.allInvoices.find(x => x.id === Number(btn.dataset.paid));
        if (inv) this.togglePaid(inv);
      };
    });

    // Delete
    body.querySelectorAll("button[data-del]").forEach(btn => {
      btn.onclick = () => {
        const inv = this.allInvoices.find(x => x.id === Number(btn.dataset.del));
        if (inv) this.deleteInvoice(inv);
      };
    });

    // Main PDF button (default A4)
    body.querySelectorAll("button[data-pdf-main]").forEach(btn => {
      btn.onclick = () => {
        const id = Number(btn.dataset.pdfMain);
        const inv = this.allInvoices.find(x => x.id === id);
        if (inv) this.openPdf(inv, "A4");
      };
    });

    // ▼ Dropdown toggle
    body.querySelectorAll("button[data-pdf-drop]").forEach(btn => {
      btn.onclick = () => {
        const id = Number(btn.dataset.pdfDrop);
        const menu = $(`pdfMenu-${id}`);
        if (menu) menu.classList.toggle("hidden");
      };
    });

    // Dropdown options
    body.querySelectorAll(".pdf-option").forEach(opt => {
      opt.onclick = () => {
        const id = Number(opt.dataset.pdfId);
        const size = opt.dataset.pdfSize;
        const inv = this.allInvoices.find(x => x.id === id);
        if (inv) this.openPdf(inv, size);

        const menu = $(`pdfMenu-${id}`);
        if (menu) menu.classList.add("hidden");
      };
    });

    // Close dropdown on outside click
    document.addEventListener("click", (e) => {
      if (!e.target.closest(".pdf-drop") && !e.target.closest(".pdf-menu")) {
        document.querySelectorAll(".pdf-menu").forEach(m => m.classList.add("hidden"));
      }
    });
  }
};
