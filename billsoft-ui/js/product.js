// js/product.js
import { api } from "./api.js";

export const productModule = {
  products: [],

  async load() {
    try {
      this.products = await api.getAllProducts();
    } catch (e) {
      console.error("Failed to load products", e);
      this.products = [];
    }
  },

  findById(id) {
    return this.products.find(p => p.id === id) || null;
  },

  async create(product) {
    const saved = await api.createProduct(product);
    this.products.push(saved);
    return saved;
  },

  async update(id, patch) {
    const updated = await api.updateProduct(id, patch);
    const idx = this.products.findIndex(p => p.id === id);
    if (idx >= 0) this.products[idx] = updated;
    return updated;
  },

  async remove(id) {
    await api.deleteProduct(id);
    this.products = this.products.filter(p => p.id !== id);
  }
};
