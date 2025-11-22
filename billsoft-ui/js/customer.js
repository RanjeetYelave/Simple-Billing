// js/customer.js
const API = "http://localhost:8080/api/customers";

export const customerModule = {

  customers: [],

  async load() {
    const res = await fetch(API);
    this.customers = await res.json();
  },

  async create(payload) {
    const res = await fetch(API, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    const data = await res.json();
    this.customers.push(data);
    return data;
  },

  findById(id) {
    return this.customers.find(c => c.id === id) || null;
  }
};
