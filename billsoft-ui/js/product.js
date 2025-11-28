// js/product.js
// Product cache + API helper with safer create & refresh behaviour.

const API = "http://localhost:8080/api/products";

export const productModule = {

  products: [],

  // Load full list (refresh)
  async load() {
    console.log("🔥 Loading products...");
    const res = await fetch(API);
    if (!res.ok) {
      const txt = await res.text().catch(() => res.statusText);
      throw new Error("Failed to load products: " + txt);
    }
    this.products = await res.json();
    console.log("Products loaded:", this.products);
  },

  // Return list (cached)
  list() {
    return this.products || [];
  },

  findByName(name) {
    if (!name) return null;
    const n = name.trim().toLowerCase();
    return this.products.find(p => (p.name || "").toLowerCase() === n) || null;
  },

  findById(id) {
    if (id == null) return null;
    return this.products.find(p => p.id === Number(id)) || null;
  },

  // Create product and keep cache in sync
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

    // keep client cache in sync (avoid duplicates)
    const existing = this.products.find(p => p.id === created.id);
    if (!existing) this.products.push(created);
    else {
      // update fields
      Object.assign(existing, created);
    }

    return created;
  },

  // Update product (keeps cache in sync)
  async update(id, payload) {
    const res = await fetch(`${API}/${id}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    if (!res.ok) {
      const txt = await res.text().catch(() => res.statusText);
      throw new Error("Product update failed: " + txt);
    }
    const updated = await res.json();
    const idx = this.products.findIndex(p => p.id === updated.id);
    if (idx !== -1) this.products[idx] = updated;
    return updated;
  },

  // Remove product locally and on server
  async remove(id) {
    const res = await fetch(`${API}/${id}`, { method: "DELETE" });
    if (!res.ok) {
      const txt = await res.text().catch(() => res.statusText);
      throw new Error("Product delete failed: " + txt);
    }
    this.products = this.products.filter(p => p.id !== id);
    return true;
  }
};
