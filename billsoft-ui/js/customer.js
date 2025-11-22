// js/customer.js
const API = "http://localhost:8080/api/customers";

export const customerModule = {

  customers: [],

  async load() {
    console.log("🔥 Loading customers...");
    const res = await fetch(API);
    this.customers = await res.json();
    console.log("Customers loaded:", this.customers);
  },

  findById(id) {
    return this.customers.find(c => c.id === id) || null;
  }
};
