// js/product.js
const API = "http://localhost:8080/api/products";

export const productModule = {
  products: [],
  async load() {
    const res = await fetch(API);
    if (!res.ok) {
      const txt = await res.text().catch(() => res.statusText);
      throw new Error("Failed to load products: " + txt);
    }
    this.products = await res.json();
  },
  list() { return this.products || []; },
  findByName(name) {
    if (!name) return null;
    const n = name.trim().toLowerCase();
    return this.products.find(p => (p.name || "").toLowerCase() === n) || null;
  },
  findById(id) {
    if (id == null) return null;
    return this.products.find(p => p.id === Number(id)) || null;
  },
  async create(payload) {
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
    const existing = this.products.find(p => p.id === created.id);
    if (!existing) this.products.push(created);
    else Object.assign(existing, created);
    return created;
  },
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
