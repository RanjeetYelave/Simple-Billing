// js/ui/product-screen.js
import { productModule } from "../product.js";

export const productScreen = {

  render() {
    return `
      <div class="card">
        <h2>Products</h2>

        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;">
          <button class="btn small" id="reloadProducts">Reload</button>
        </div>

        <table class="invoice-table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Price</th>
              <th>Unit</th>
              <th>GST%</th>
            </tr>
          </thead>
          <tbody id="productListBody"></tbody>
        </table>

        <h3 style="margin-top:18px;">Add Product</h3>
        <div style="display:grid;grid-template-columns:2fr 1fr 1fr 1fr;gap:8px;margin-bottom:10px;">
          <input id="p_name" placeholder="Name"/>
          <input id="p_price" type="number" placeholder="Price"/>
          <input id="p_unit" placeholder="Unit"/>
          <input id="p_gst" type="number" placeholder="GST %"/>
        </div>
        <button class="btn primary" id="saveProductBtn">Save</button>
      </div>
    `;
  },

  async init() {
    document.getElementById("reloadProducts").onclick = () => this.load();
    document.getElementById("saveProductBtn").onclick = () => this.save();
    await this.load();
  },

  async load() {
    // refresh from backend to stay in sync with auto-creates too
    await productModule.load();

    const list = productModule.products || [];
    const body = document.getElementById("productListBody");
    body.innerHTML = "";

    list.forEach(p => {
      const tr = document.createElement("tr");
      tr.innerHTML = `
        <td>${p.name}</td>
        <td>₹ ${p.price?.toFixed ? p.price.toFixed(2) : p.price}</td>
        <td>${p.unit || "-"}</td>
        <td>${p.gstPercentage ?? 0}%</td>
      `;
      body.appendChild(tr);
    });
  },

  async save() {
    const name = p_name.value.trim();
    const price = Number(p_price.value);
    const unit = p_unit.value.trim() || "pcs";
    const gst = Number(p_gst.value) || 0;

    if (!name) {
      alert("Name is required");
      return;
    }

    await productModule.create({
      name,
      price: isNaN(price) ? 0 : price,
      unit,
      gstPercentage: isNaN(gst) ? 0 : gst
    });

    p_name.value = "";
    p_price.value = "";
    p_unit.value = "";
    p_gst.value = "";

    await this.load();
  }
};
