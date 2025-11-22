// js/customer.js
import { apiGet, apiPost, apiPut, apiDelete } from "./api.js";

export const customerModule = {

    customers: [],

    // ----------------------------------------------
    // Load all customers
    // ----------------------------------------------
    async load() {
        try {
            this.customers = await apiGet("/api/customers");
            console.log("✅ Customers loaded:", this.customers);
            return this.customers;
        } catch (err) {
            console.error("❌ Failed to load customers:", err);
            throw err;
        }
    },

    // ----------------------------------------------
    // Create customer
    // ----------------------------------------------
    async create(data) {
        try {
            const res = await apiPost("/api/customers", data);
            this.customers.push(res);
            return res;
        } catch (err) {
            console.error("❌ Customer creation failed:", err);
            throw err;
        }
    },

    // ----------------------------------------------
    // Update
    // ----------------------------------------------
    async update(id, data) {
        try {
            const updated = await apiPut(`/api/customers/${id}`, data);

            const idx = this.customers.findIndex(c => c.id == id);
            if (idx !== -1) this.customers[idx] = updated;

            return updated;
        } catch (err) {
            console.error("❌ Customer update failed:", err);
            throw err;
        }
    },

    // ----------------------------------------------
    // Delete
    // ----------------------------------------------
    async delete(id) {
        try {
            await apiDelete(`/api/customers/${id}`);
            this.customers = this.customers.filter(c => c.id !== id);
        } catch (err) {
            console.error("❌ Customer delete failed:", err);
            throw err;
        }
    }
};
