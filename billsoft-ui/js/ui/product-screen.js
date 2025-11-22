// js/ui/product-screen.js
import { productModule } from "../product.js";

export const productScreen = {

  render() {
    return `
      <div class="card">
        <h2>Products</h2>

        <button class="btn" id="reloadProducts">Reload</button>

        <div id="productList"></div>

        <h3>Add Product</h3>
        <input id="p_name" placeholder="Name"/>
        <input id="p_price" type="number" placeholder="Price"/>
        <input id="p_unit" placeholder="Unit"/>
        <input id="p_gst" type="number" placeholder="GST %"/>
        <button class="btn primary" id="saveProductBtn">Save</button>
      </div>
    `;
  },

  init() {
    this.load();

    document.getElementById("reloadProducts").onclick = () => this.load();

    document.getElementById("saveProductBtn").onclick = async () => {
      await productModule.create({
        name: p_name.value,
        price: Number(p_price.value),
        unit: p_unit.value,
        gstPercentage: Number(p_gst.value)
      });
      this.load();
    };
  },

  load() {
    const list = productModule.products;
    const box = document.getElementById("productList");

    box.innerHTML = list.map(p => `
      <div class="invoice-list-item">
        <b>${p.name}</b>
        <div>₹${p.price}</div>
        <div>${p.unit}</div>
        <div>GST ${p.gstPercentage}%</div>
      </div>
    `).join("");
  }
};
