// js/customer.js
const API = "http://localhost:8080/api/customers";
const ANALYTICS_API = "http://localhost:8080/api/invoices/analytics/customer";

export const customerModule = {
  customers: [],
  async load() {
    const res = await fetch(API);
    if (!res.ok) {
      const txt = await res.text().catch(() => res.statusText);
      throw new Error("Failed to load customers: " + txt);
    }
    this.customers = await res.json();
  },
  list() {
    return this.customers;
  },
  findById(id) {
    if (id == null) return null;
    return this.customers.find(c => c.id === Number(id)) || null;
  },
  findByName(name) {
    if (!name) return null;
    const n = name.trim().toLowerCase();
    return this.customers.find(c => (c.name || "").toLowerCase() === n) || null;
  },
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
    const existing = this.customers.find(c => c.id === data.id);
    if (!existing) this.customers.push(data);
    else Object.assign(existing, data);
    return data;
  },
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
  async remove(id) {
    const res = await fetch(`${API}/${id}`, { method: "DELETE" });
    if (!res.ok) {
      const txt = await res.text().catch(() => res.statusText);
      throw new Error("Delete customer failed: " + txt);
    }
    this.customers = this.customers.filter(c => c.id !== id);
    return true;
  },
  async getAnalytics(customerId) {
    const res = await fetch(`${ANALYTICS_API}/${customerId}`);
    if (!res.ok) {
      const txt = await res.text().catch(() => res.statusText);
      throw new Error("Customer analytics failed: " + txt);
    }
    return res.json();
  }
};
