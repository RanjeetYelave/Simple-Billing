// js/product.js
const API = "http://localhost:8080/api/products";

export const productModule = {

  products: [],

  async load() {
    console.log("🔥 Loading products...");
    const res = await fetch(API);
    this.products = await res.json();
    console.log("Products loaded:", this.products);
  },

  findByName(name) {
    if (!name) return null;
    const n = name.trim().toLowerCase();
    return this.products.find(p => (p.name || "").toLowerCase() === n) || null;
  },

  async create(payload) {
    // payload: { name, price, unit, gstPercentage }
    const res = await fetch(API, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });

    if (!res.ok) {
      const txt = await res.text().catch(() => "");
      throw new Error("Product create failed: " + (txt || res.status));
    }

    const created = await res.json();
    // keep client cache in sync
    this.products.push(created);
    return created;
  }
};
