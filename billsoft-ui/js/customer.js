// js/customer.js
// Customer cache + API helper with refresh & safer create

const API = "http://localhost:8080/api/customers";
const ANALYTICS_API = "http://localhost:8080/api/invoices/analytics/customer";

export const customerModule = {

  customers: [],

  // Load all customers once at startup (or refresh)
  async load() {
    console.log("🔥 Loading customers...");
    const res = await fetch(API);
    if (!res.ok) {
      const txt = await res.text().catch(() => res.statusText);
      throw new Error("Failed to load customers: " + txt);
    }
    this.customers = await res.json();
    console.log("Customers loaded:", this.customers);
  },

  // Return already-loaded customers
  list() {
    return this.customers;
  },

  findById(id) {
    if (id == null) return null;
    return this.customers.find(c => c.id === Number(id)) || null;
  },

  // Find by name (case-insensitive)
  findByName(name) {
    if (!name) return null;
    const n = (name || "").trim().toLowerCase();
    return this.customers.find(c => (c.name || "").toLowerCase() === n) || null;
  },

  // Create new customer and keep cache in sync
  async create(payload) {
    const res = await fetch(API, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });

    if (!res.ok) {
      const txt = await res.text().catch(() => res.statusText);
      throw new Error("Create customer failed: " + txt);
    }

    const data = await res.json();
    // keep local cache in sync
    const existing = this.customers.find(c => c.id === data.id);
    if (!existing) this.customers.push(data);
    else Object.assign(existing, data);
    return data;
  },

  // Update customer
  async update(id, payload) {
    const res = await fetch(`${API}/${id}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });

    if (!res.ok) {
      const txt = await res.text().catch(() => res.statusText);
      throw new Error("Update customer failed: " + txt);
    }

    const updated = await res.json();
    const idx = this.customers.findIndex(c => c.id === updated.id);
    if (idx !== -1) this.customers[idx] = updated;
    return updated;
  },

  // Delete customer
  async remove(id) {
    const res = await fetch(`${API}/${id}`, { method: "DELETE" });
    if (!res.ok) {
      const txt = await res.text().catch(() => res.statusText);
      throw new Error("Delete customer failed: " + txt);
    }
    this.customers = this.customers.filter(c => c.id !== id);
    return true;
  },

  // Analytics for unpaid invoices
  async getAnalytics(customerId) {
    const res = await fetch(`${ANALYTICS_API}/${customerId}`);
    if (!res.ok) {
      const txt = await res.text().catch(() => res.statusText);
      throw new Error("Customer analytics failed: " + txt);
    }
    return res.json();
  }
};
