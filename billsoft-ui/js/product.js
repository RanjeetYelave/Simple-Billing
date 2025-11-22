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
    return this.products.find(p => p.name.toLowerCase() === name.toLowerCase());
  }
};
