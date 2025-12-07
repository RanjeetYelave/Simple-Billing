// js/customer.js
import { api } from "./api.js";

export const customerModule = {
  customers: [],

  async load() {
    try {
      this.customers = await api.getAllCustomers();
    } catch (e) {
      console.error("Failed to load customers", e);
      this.customers = [];
    }
  },

  findById(id) {
    return this.customers.find(c => c.id === id) || null;
  },

  async create(customer) {
    const saved = await api.createCustomer(customer);
    this.customers.push(saved);
    return saved;
  },

  async update(id, patch) {
    const updated = await api.updateCustomer(id, patch);
    const idx = this.customers.findIndex(c => c.id === id);
    if (idx >= 0) this.customers[idx] = updated;
    return updated;
  },

  async remove(id) {
    await api.deleteCustomer(id);
    this.customers = this.customers.filter(c => c.id !== id);
  }
};
