// js/ui/customer-screen.js
import { customerModule } from "../customer.js";
import { $ } from "../utils.js";

export const customerScreen = {

  pageSize: 10,
  currentPage: 1,
  searchQuery: "",
  sortMode: "AZ", // AZ, ZA, NEW, OLD
  allCustomers: [],
  unpaidMap: {},

  render() {
    return `
      <div class="card">
        <h2>Customers</h2>

        <!-- SEARCH + SORT -->
        <div style="display:flex;gap:10px;align-items:end;margin-bottom:12px;">
          <div style="flex:1;">
            <label class="small muted">Search</label>
            <input id="custSearch" placeholder="Search name, phone, email...">
          </div>

          <div>
            <label class="small muted">Sort</label>
            <select id="custSort">
              <option value="AZ">A → Z</option>
              <option value="ZA">Z → A</option>
              <option value="NEW">Newest First</option>
              <option value="OLD">Oldest First</option>
            </select>
          </div>

          <button class="btn small" id="custReloadBtn">Reload</button>
        </div>

        <!-- TABLE -->
        <table class="invoice-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Name</th>
              <th>Phone</th>
              <th>Email</th>
              <th>Address</th>
              <th style="width:120px;">Action</th>
            </tr>
          </thead>
          <tbody id="custTableBody"></tbody>
        </table>

        <!-- PAGINATION -->
        <div id="custPagination" style="margin-top:10px;display:flex;gap:8px;"></div>

        <h3 style="margin-top:22px;">Add Customer</h3>

        <div>
          <label>Name</label>
          <input id="custName" placeholder="Full name">

          <label>Phone</label>
          <input id="custPhone" placeholder="10-digit number">

          <label>Email</label>
          <input id="custEmail" placeholder="Email">

          <label>Address</label>
          <input id="custAddress" placeholder="Address">

          <button class="btn" id="custSaveBtn">Save</button>
        </div>

        <!-- EDIT MODAL -->
        <div id="custEditModal" class="hidden"
             style="
                position:fixed;top:0;left:0;width:100%;height:100%;
                background:rgba(0,0,0,0.6);display:flex;
                justify-content:center;align-items:center;">
           
          <div style="background:#020617;padding:20px;border-radius:12px;
                      width:360px;border:1px solid #1e293b;">
            
            <h3>Edit Customer</h3>
            <input id="editName">
            <input id="editPhone">
            <input id="editEmail">
            <input id="editAddress">

            <div style="display:flex;gap:10px;margin-top:10px;">
              <button class="btn primary" id="editSaveBtn">Save</button>
              <button class="btn ghost" id="editCancelBtn">Cancel</button>
            </div>
          </div>

        </div>

      </div>
    `;
  },

  init() {
    $("custReloadBtn").onclick = () => this.load();
    $("custSaveBtn").onclick = () => this.saveNew();
    $("custSort").onchange = () => {
      this.sortMode = $("custSort").value;
      this.renderTable();
    };

    // DEBOUNCED SEARCH
    let timer;
    $("custSearch").oninput = () => {
      clearTimeout(timer);
      timer = setTimeout(() => {
        this.searchQuery = $("custSearch").value.trim().toLowerCase();
        this.currentPage = 1;
        this.renderTable();
      }, 200);
    };

    // Modal close
    $("editCancelBtn").onclick = () =>
      $("custEditModal").classList.add("hidden");

    this.load();
  },

  async load() {
    const customers = await customerModule.list();
    this.allCustomers = customers;

    // Build unpaid map
    this.unpaidMap = {};
    for (const c of customers) {
      try {
        const analytics = await customerModule.getAnalytics(c.id);
        if (analytics.totalPending > 0) {
          this.unpaidMap[c.id] = true;
        }
      } catch {}
    }

    this.renderTable();
  },

  // VALIDATION
  validate(name, phone, email) {
    if (!name.trim()) return "Name required";
    if (phone && !/^\d{10}$/.test(phone)) return "Invalid phone";
    if (email && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email))
      return "Invalid email";
    return null;
  },

  async saveNew() {
    const name = $("custName").value.trim();
    const phone = $("custPhone").value.trim();
    const email = $("custEmail").value.trim();
    const address = $("custAddress").value.trim();

    const err = this.validate(name, phone, email);
    if (err) return alert(err);

    await customerModule.create({ name, phone, email, address });
    alert("Customer added");
    this.load();
  },

  // ---------- EDIT ----------
  openEdit(c) {
    $("editName").value = c.name;
    $("editPhone").value = c.phone;
    $("editEmail").value = c.email;
    $("editAddress").value = c.address;

    $("custEditModal").classList.remove("hidden");

    $("editSaveBtn").onclick = async () => {
      const payload = {
        name: $("editName").value.trim(),
        phone: $("editPhone").value.trim(),
        email: $("editEmail").value.trim(),
        address: $("editAddress").value.trim()
      };

      const err = this.validate(payload.name, payload.phone, payload.email);
      if (err) return alert(err);

      await customerModule.update(c.id, payload);
      $("custEditModal").classList.add("hidden");
      this.load();
    };
  },

  // ---------- TABLE + PAGINATION ----------
  renderTable() {
    const body = $("custTableBody");
    body.innerHTML = "";

    // Search filter
    let filtered = this.allCustomers.filter(c =>
      `${c.name} ${c.phone} ${c.email} ${c.address}`
        .toLowerCase()
        .includes(this.searchQuery)
    );

    // Sort
    if (this.sortMode === "AZ") {
      filtered.sort((a, b) => a.name.localeCompare(b.name));
    } else if (this.sortMode === "ZA") {
      filtered.sort((a, b) => b.name.localeCompare(a.name));
    } else if (this.sortMode === "NEW") {
      filtered.sort((a, b) => b.id - a.id);
    } else if (this.sortMode === "OLD") {
      filtered.sort((a, b) => a.id - b.id);
    }

    // Pagination
    const start = (this.currentPage - 1) * this.pageSize;
    const pageItems = filtered.slice(start, start + this.pageSize);

    for (const c of pageItems) {
      const tr = document.createElement("tr");

      tr.style.background = this.unpaidMap[c.id]
        ? "rgba(255,80,80,0.10)"
        : "transparent";

      tr.innerHTML = `
        <td>${c.id}</td>
        <td>${c.name}</td>
        <td>${c.phone || "-"}</td>
        <td>${c.email || "-"}</td>
        <td>${c.address || "-"}</td>
        <td>
          <button class="btn small ghost" data-edit="${c.id}">Edit</button>
          <button class="btn small danger" data-del="${c.id}">Delete</button>
        </td>
      `;
      body.appendChild(tr);
    }

    // Bind edit & delete
    body.querySelectorAll("[data-edit]").forEach(btn => {
      btn.onclick = () => {
        const id = Number(btn.dataset.edit);
        const c = this.allCustomers.find(x => x.id === id);
        this.openEdit(c);
      };
    });

    body.querySelectorAll("[data-del]").forEach(btn => {
      btn.onclick = async () => {
        const id = Number(btn.dataset.del);
        if (!confirm("Delete customer?")) return;
        await customerModule.remove(id);
        this.load();
      };
    });

    this.renderPagination(filtered.length);
  },

  renderPagination(total) {
    const totalPages = Math.ceil(total / this.pageSize);
    const box = $("custPagination");
    box.innerHTML = "";

    if (totalPages <= 1) return;

    for (let p = 1; p <= totalPages; p++) {
      const btn = document.createElement("button");
      btn.className = "btn small ghost";
      btn.textContent = p;
      if (p === this.currentPage) btn.style.background = "#2563eb";

      btn.onclick = () => {
        this.currentPage = p;
        this.renderTable();
      };

      box.appendChild(btn);
    }
  }
};
