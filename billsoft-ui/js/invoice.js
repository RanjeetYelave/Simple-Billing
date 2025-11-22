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
  }
};
