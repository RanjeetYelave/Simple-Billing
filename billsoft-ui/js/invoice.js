// js/invoice.js
import { apiGet, apiPost, apiPut, apiDelete } from "./api.js";

export const invoiceModule = {
  
  // GET all invoices
  async list() {
    return await apiGet("/api/invoices");
  },

  // CREATE invoice  ⭐ REQUIRED BY UI
  async save(payload) {
    console.log("📤 Creating invoice:", payload);
    return await apiPost("/api/invoices", payload);
  },

  // PREVIEW / GET BY ID
  async preview(id) {
    return await apiGet(`/api/invoices/${id}`);
  },

  // UPDATE invoice
  async update(id, payload) {
    return await apiPut(`/api/invoices/${id}`, payload);
  },

  // DELETE invoice
  async delete(id) {
    return await apiDelete(`/api/invoices/${id}`);
  }
};
