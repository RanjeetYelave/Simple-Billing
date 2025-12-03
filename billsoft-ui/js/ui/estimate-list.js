// js/ui/estimate-list.js
import { invoiceModule } from "../invoice.js";
import { $, money } from "../utils.js";
import { pdfViewer } from "../pdf-viewer.js";

export const estimateList = {

  allEstimates: [],
  currentPage: 1,
  pageSize: 10,
  currentSort: "DATE_DESC",

  render() {
    return `
      <div class="card">
        <h2>Estimates</h2>
        <p class="small muted" style="margin-top:4px;">
          View, convert and manage all estimates (quotations).
        </p>

        <div style="display:flex;flex-wrap:wrap;gap:8px;margin:12px 0;align-items:flex-end">

          <div style="max-width:160px;">
            <label class="small muted">Estimate ID</label>
            <input id="estSearchId" placeholder="e.g. 5" />
          </div>
          <button class="btn small" id="estSearchIdBtn">Search ID</button>

          <div style="max-width:160px;">
            <label class="small muted">Customer ID</label>
            <input id="estSearchCustomer" placeholder="e.g. 2" />
          </div>
          <button class="btn small" id="estSearchCustomerBtn">Search Customer</button>

          <div style="margin-left:auto;display:flex;gap:10px;align-items:flex-end;">
            <div>
              <label class="small muted">Sort</label>
              <select id="estSortSelect">
                <option value="DATE_DESC">Latest first</option>
                <option value="DATE_ASC">Oldest first</option>
                <option value="AMOUNT_DESC">Amount high → low</option>
                <option value="AMOUNT_ASC">Amount low → high</option>
              </select>
            </div>

            <button class="btn ghost small" id="reloadEstimates">Reload All</button>
          </div>
        </div>

        <table class="invoice-table">
          <thead>
            <tr>
              <th>Estimate</th>
              <th>Customer</th>
              <th>Date</th>
              <th>Total</th>
              <th>Status</th>
              <th style="width:260px;">Actions</th>
            </tr>
          </thead>
          <tbody id="estTableBody"></tbody>
        </table>

        <div id="estPagination"
             class="small muted"
             style="margin-top:10px;display:flex;justify-content:space-between;align-items:center;">
          <div id="estPaginationInfo"></div>
          <div style="display:flex;gap:6px;">
            <button class="btn small ghost" id="estPrevPage">Prev</button>
            <button class="btn small ghost" id="estNextPage">Next</button>
          </div>
        </div>
      </div>
    `;
  },

  init() {
    this.allEstimates = [];
    this.currentPage = 1;
    this.currentSort = "DATE_DESC";

    const reloadBtn = $("reloadEstimates");
    if (reloadBtn) reloadBtn.onclick = () => this.loadAll();

    const idBtn = $("estSearchIdBtn");
    if (idBtn) idBtn.onclick = () => this.searchById();

    const custBtn = $("estSearchCustomerBtn");
    if (custBtn) custBtn.onclick = () => this.searchByCustomer();

    const sortSel = $("estSortSelect");
    if (sortSel) {
      sortSel.onchange = () => {
        this.currentSort = sortSel.value || "DATE_DESC";
        this.currentPage = 1;
        this.renderTable();
      };
    }

    const prev = $("estPrevPage");
    if (prev) prev.onclick = () => {
      if (this.currentPage > 1) {
        this.currentPage--;
        this.renderTable();
      }
    };

    const next = $("estNextPage");
    if (next) next.onclick = () => {
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
      const list = await invoiceModule.listEstimates();
      this.setData(list);
    } catch (err) {
      console.error("Failed to load estimates", err);
      const body = $("estTableBody");
      if (body) {
        body.innerHTML = `<tr><td colspan="6" class="small muted">Failed to load estimates.</td></tr>`;
      }
      const info = $("estPaginationInfo");
      if (info) info.textContent = "";
    }
  },

  async searchById() {
    const idEl = $("estSearchId");
    const idText = idEl ? idEl.value.trim() : "";
    if (!idText) return alert("Enter an Estimate ID");

    const id = Number(idText);
    if (isNaN(id)) return alert("Estimate ID must be a number");

    try {
      const est = await invoiceModule.preview(id);
      if (!est || est.id == null || est.status !== "ESTIMATE") {
        this.setData([]);
        const body = $("estTableBody");
        if (body) body.innerHTML =
          `<tr><td colspan="6" class="small muted">Estimate not found.</td></tr>`;
        const info = $("estPaginationInfo");
        if (info) info.textContent = "";
        return;
      }
      this.setData([est]);
    } catch (err) {
      console.error("Search estimate by ID failed", err);
      const body = $("estTableBody");
      if (body) body.innerHTML =
        `<tr><td colspan="6" class="small muted">Error fetching estimate.</td></tr>`;
      const info = $("estPaginationInfo");
      if (info) info.textContent = "";
    }
  },

  async searchByCustomer() {
    const cidEl = $("estSearchCustomer");
    const cidText = cidEl ? cidEl.value.trim() : "";
    if (!cidText) return alert("Enter a Customer ID");

    const cid = Number(cidText);
    if (isNaN(cid)) return alert("Customer ID must be a number");

    try {
      const all = await invoiceModule.listEstimates();
      const filtered = all.filter(
        inv => inv.customer && Number(inv.customer.id) === cid
      );
      this.setData(filtered);
    } catch (err) {
      console.error("Search estimates by customer failed", err);
      const body = $("estTableBody");
      if (body) body.innerHTML =
        `<tr><td colspan="6" class="small muted">Error fetching estimates.</td></tr>`;
      const info = $("estPaginationInfo");
      if (info) info.textContent = "";
    }
  },

  setData(list) {
    this.allEstimates = Array.isArray(list) ? list : [];
    this.currentPage = 1;
    this.renderTable();
  },

  // ---------------- ACTIONS ----------------

  async convertEstimate(inv) {
    if (!inv || !inv.id) return;

    if (inv.convertedInvoiceId) {
      const msg = `This estimate is already converted to invoice #${inv.convertedInvoiceId}.`;
      alert(msg);
      return;
    }

    const ok = window.confirm(
      `Convert estimate ${inv.estimateNumber || ("EST-" + inv.id)} to Invoice?`
    );
    if (!ok) return;

    try {
      const newInvoice = await invoiceModule.convert(inv.id);
      const msg = `Converted to invoice ${
        newInvoice.invoiceNumber || ("INV-" + newInvoice.id)
      }.`;
      alert(msg);
      // Reload list to reflect convertedInvoiceId flag
      await this.loadAll();
    } catch (err) {
      console.error("Convert estimate failed", err);
      alert("Failed to convert estimate.");
    }
  },

  async deleteEstimate(inv) {
    const ok = window.confirm(
      `Delete estimate ${inv.estimateNumber || ("EST-" + inv.id)}? This cannot be undone.`
    );
    if (!ok) return;

    try {
      await invoiceModule.delete(inv.id);
      this.allEstimates = this.allEstimates.filter(x => x.id !== inv.id);
      if (this.currentPage > this.getTotalPages()) {
        this.currentPage = this.getTotalPages();
      }
      this.renderTable();
    } catch (err) {
      console.error("Failed to delete estimate", err);
      alert("Failed to delete estimate.");
    }
  },

  async openPdf(inv, size = "A4") {
    try {
      const blob = await invoiceModule.pdf(inv.id, size);
      const displayNumber = inv.estimateNumber || inv.invoiceNumber || `EST-${inv.id}`;
      const fname = `estimate-${displayNumber}.pdf`;
      pdfViewer.open(blob, fname, `Estimate ${displayNumber}`);
    } catch (e) {
      console.error("Estimate PDF fetch failed", e);
      alert("Failed to load PDF.");
    }
  },

  // ---------------- RENDER TABLE ----------------

  getTotalPages() {
    if (!this.allEstimates.length) return 1;
    return Math.ceil(this.allEstimates.length / this.pageSize);
  }

  ,
  getSortedEstimates() {
    const list = this.allEstimates.slice();
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
    const body = $("estTableBody");
    const info = $("estPaginationInfo");

    if (!this.allEstimates.length) {
      if (body) {
        body.innerHTML =
          `<tr><td colspan="6" class="small muted">No estimates found.</td></tr>`;
      }
      if (info) info.textContent = "0 estimates";
      return;
    }

    const sorted = this.getSortedEstimates();
    const totalPages = this.getTotalPages();

    if (this.currentPage > totalPages) this.currentPage = totalPages;
    if (this.currentPage < 1) this.currentPage = 1;

    const start = (this.currentPage - 1) * this.pageSize;
    const pageItems = sorted.slice(start, start + this.pageSize);

    if (body) body.innerHTML = "";

    pageItems.forEach(inv => {
      const tr = document.createElement("tr");

      const isConverted = !!inv.convertedInvoiceId;
      const displayNumber = inv.estimateNumber || inv.invoiceNumber || `EST-${inv.id}`;

      let statusLabel = "ESTIMATE";
      if (isConverted) {
        statusLabel = `Converted → #${inv.convertedInvoiceId}`;
      }

      tr.innerHTML = `
        <td>${displayNumber}</td>
        <td>${inv.customer?.name || "—"}</td>
        <td>${inv.invoiceDate ? inv.invoiceDate.slice(0, 10) : "-"}</td>
        <td class="numeric">${money(inv.totalAmount)}</td>
        <td>
          <span class="small" style="color:${
            isConverted ? "#22c55e" : "#38bdf8"
          };">
            ${statusLabel}
          </span>
        </td>
        <td>
          <div style="display:flex;gap:6px;flex-wrap:wrap;align-items:center;">
            <button class="btn small" data-convert="${inv.id}" ${isConverted ? "disabled" : ""}>
              Convert
            </button>
            <button class="btn small ghost" data-pdf="${inv.id}">
              PDF
            </button>
            <button class="btn small danger" data-del="${inv.id}">
              Delete
            </button>
          </div>
        </td>
      `;
      if (body) body.appendChild(tr);
    });

    if (info) {
      info.textContent =
        `Page ${this.currentPage} of ${totalPages} • ${this.allEstimates.length} estimate${this.allEstimates.length === 1 ? "" : "s"}`;
    }

    const prevBtn = $("estPrevPage");
    if (prevBtn) prevBtn.disabled = this.currentPage <= 1;
    const nextBtn = $("estNextPage");
    if (nextBtn) nextBtn.disabled = this.currentPage >= totalPages;

    this.attachHandlers();
  },

  attachHandlers() {
    const body = $("estTableBody");
    if (!body) return;

    // Convert
    body.querySelectorAll("button[data-convert]").forEach(btn => {
      btn.onclick = () => {
        const id = Number(btn.dataset.convert);
        const inv = this.allEstimates.find(x => x.id === id);
        if (inv) this.convertEstimate(inv);
      };
    });

    // Delete
    body.querySelectorAll("button[data-del]").forEach(btn => {
      btn.onclick = () => {
        const id = Number(btn.dataset.del);
        const inv = this.allEstimates.find(x => x.id === id);
        if (inv) this.deleteEstimate(inv);
      };
    });

    // PDF
    body.querySelectorAll("button[data-pdf]").forEach(btn => {
      btn.onclick = () => {
        const id = Number(btn.dataset.pdf);
        const inv = this.allEstimates.find(x => x.id === id);
        if (inv) this.openPdf(inv, "A4");
      };
    });
  }
};
