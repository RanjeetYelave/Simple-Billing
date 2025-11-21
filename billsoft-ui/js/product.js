import { getProducts, createProduct, getProduct } from "./api.js";
import { $, extractId } from "./utils.js";

export const productModule = {
    products: [],

    async load() {
        this.products = await getProducts();
        return this.products;
    },

    async save() {
        const data = {
            name: $("p_name").value.trim(),
            price: Number($("p_price").value),
            unit: $("p_unit").value.trim(),
            gstPercentage: Number($("p_gst").value),
        };
        return await createProduct(data);
    },

    async preview(id) {
        return await getProduct(id);
    }
};
