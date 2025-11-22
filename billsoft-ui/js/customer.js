// js/customer.js
const API = "http://localhost:8080/api/customers";
const ANALYTICS_API = "http://localhost:8080/api/invoices/analytics/customer";

export const customerModule = {

  customers: [],

  // Load all customers once at startup
  async load() {
    console.log("🔥 Loading customers...");
    const res = await fetch(API);
    this.customers = await res.json();
    console.log("Customers loaded:", this.customers);
  },

  // Return already-loaded customers
  list() {
    return this.customers;
  },

  // Create new customer
  async create(payload) {
    const res = await fetch(API, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });

    const data = await res.json();
    this.customers.push(data); // keep in sync
    return data;
  },

  // Update customer
  async update(id, payload) {
    const res = await fetch(`${API}/${id}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });

    const updated = await res.json();

    // update local list
    const idx = this.customers.findIndex(c => c.id === id);
    if (idx !== -1) this.customers[idx] = updated;

    return updated;
  },

  // Delete customer
  async remove(id) {
    await fetch(`${API}/${id}`, { method: "DELETE" });

    // update local list
    this.customers = this.customers.filter(c => c.id !== id);
  },

  // Analytics for unpaid invoices
  async getAnalytics(customerId) {
    const res = await fetch(`${ANALYTICS_API}/${customerId}`);
    return res.json();
  },

  findById(id) {
    return this.customers.find(c => c.id === id) || null;
  }
};
