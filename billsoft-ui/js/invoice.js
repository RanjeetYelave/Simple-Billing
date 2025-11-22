// js/invoice.js
const API = "http://localhost:8080/api/invoices";

export const invoiceModule = {

  async list() {
    const res = await fetch(API);
    return res.json();
  },

  async preview(id) {
    const res = await fetch(`${API}/${id}`);
    return res.json();
  },

  async save(payload) {
    const res = await fetch(API, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    return res.json();
  },

  async update(id, payload) {
    const res = await fetch(`${API}/${id}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    return res.json();
  },

  /** Mark paid/unpaid — reused across invoice list + analytics */
  async markPaid(id, paid) {
    const res = await fetch(`${API}/${id}/paid?value=${paid}`, {
      method: "PUT"
    });
    return res.json();
  },

  // ---- Customer Analytics ----
  async analyticsByCustomer(customerId) {
    const res = await fetch(`${API}/analytics/customer/${customerId}`);
    return res.json();
  },

  async analyticsByName(name) {
    const res = await fetch(`${API}/analytics/search?name=${encodeURIComponent(name)}`);
    return res.json();
  }
};
