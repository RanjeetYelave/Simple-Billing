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

  async delete(id) {
    await fetch(`${API}/${id}`, { method: "DELETE" });
  },

  async markPaid(id, paid) {
    const res = await fetch(`${API}/${id}/paid?paid=${paid}`, {
      method: "PUT"
    });
    return res.json();
  },

  // ---- ANALYTICS ----
  async analyticsByCustomer(customerId) {
    const res = await fetch(`${API}/analytics/customer/${customerId}`);
    return res.json();
  },

  async analyticsByName(name) {
    const res = await fetch(
      `${API}/analytics/search?name=${encodeURIComponent(name)}`
    );
    return res.json();
  },

  // ---- PDF DOWNLOAD ----
  async pdf(id, size = "A4") {
    const res = await fetch(`${API}/${id}/pdf?size=${size}`);
    if (!res.ok) throw new Error("Failed to fetch invoice PDF");
    return res.blob();
  }
};
