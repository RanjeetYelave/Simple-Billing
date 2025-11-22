// js/invoice.js
const API = "http://localhost:8080/api/invoices";

export const invoiceModule = {

  // -----------------------------------------
  // LIST ALL
  // -----------------------------------------
  async list() {
    const res = await fetch(API);
    return res.json();
  },

  // -----------------------------------------
  // PREVIEW SINGLE INVOICE
  // -----------------------------------------
  async preview(id) {
    const res = await fetch(`${API}/${id}`);
    return res.json();
  },

  // -----------------------------------------
  // CREATE
  // -----------------------------------------
  async save(payload) {
    const res = await fetch(API, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    return res.json();
  },

  // -----------------------------------------
  // FULL UPDATE
  // -----------------------------------------
  async update(id, payload) {
    const res = await fetch(`${API}/${id}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    return res.json();
  },

  // -----------------------------------------
  // 🔥 NEW — MARK PAID/UNPAID
  // -----------------------------------------
  async markPaid(id, paid) {
    const res = await fetch(`${API}/${id}/paid?paid=${paid}`, {
      method: "PUT"
    });
    return res.json();
  },

  // -----------------------------------------
  // ANALYTICS — BY CUSTOMER ID
  // -----------------------------------------
  async analyticsByCustomer(customerId) {
    const res = await fetch(`${API}/analytics/customer/${customerId}`);
    return res.json();
  },

  // -----------------------------------------
  // ANALYTICS — SEARCH BY NAME
  // -----------------------------------------
  async analyticsByName(name) {
    const res = await fetch(
      `${API}/analytics/search?name=${encodeURIComponent(name)}`
    );
    return res.json();
  }
};
