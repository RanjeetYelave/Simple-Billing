// js/invoice.js
const API = "http://localhost:8080/api/invoices";

export const invoiceModule = {

  async list() {
    const res = await fetch(API);
    if (!res.ok) throw new Error("Failed to load invoices");
    return res.json();
  },

  async preview(id) {
    const res = await fetch(`${API}/${id}`);
    if (!res.ok) throw new Error("Failed to load invoice");
    return res.json();
  },

  async save(payload) {
    const res = await fetch(API, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    if (!res.ok) throw new Error("Failed to create invoice");
    return res.json();
  },

  async update(id, payload) {
    const res = await fetch(`${API}/${id}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    if (!res.ok) throw new Error("Failed to update invoice");
    return res.json();
  },

  async delete(id) {
    const res = await fetch(`${API}/${id}`, {
      method: "DELETE"
    });
    if (!res.ok) throw new Error("Failed to delete invoice");
    return true;
  },

  async markPaid(id, paid) {
    const res = await fetch(`${API}/${id}/paid?paid=${paid}`, {
      method: "PUT"
    });
    if (!res.ok) throw new Error("Failed to update paid flag");
    return res.json();
  },

  // ---- ANALYTICS ----
  async analyticsByCustomer(customerId) {
    const res = await fetch(`${API}/analytics/customer/${customerId}`);
    if (!res.ok) throw new Error("Failed to load analytics");
    return res.json();
  },

  async analyticsByName(name) {
    const res = await fetch(
      `${API}/analytics/search?name=${encodeURIComponent(name)}`
    );
    if (!res.ok) throw new Error("Failed to load analytics");
    return res.json();
  }
};
