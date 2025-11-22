// js/product.js
import { apiGet, apiPost } from "./api.js";

export const productModule = {
    products: [],

    // Load all products
    async load() {
        try {
            console.log("🔥 Loading products...");
            this.products = await apiGet("/api/products");
            console.log("Products loaded:", this.products);
        } catch (err) {
            console.error("Failed to load products", err);
        }
    },

    // Create a product
    async create(product) {
        try {
            console.log("🔥 Creating product:", product);
            const res = await apiPost("/api/products", product);
            this.products.push(res);
            return res;
        } catch (err) {
            console.error("Failed to create product", err);
            alert("Product creation failed: " + err.message);
        }
    },

    // ⭐ FIX: Add missing helper method
    findByName(name) {
        if (!name) return null;
        const lower = name.trim().toLowerCase();
        return this.products.find(p => p.name.toLowerCase() === lower) || null;
    }
};
