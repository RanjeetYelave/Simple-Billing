import { createInvoice, getInvoices, getInvoice } from "./api.js";
import { $, money } from "./utils.js";
import { productModule } from "./product.js";

export const invoiceModule = {
    async save(payload) {
        return await createInvoice(payload);
    },

    async preview(id) {
        return await getInvoice(id);
    },

    async list() {
        return await getInvoices();
    },

    calculateTotal(items) {
        let total = 0;
        items.forEach(i => {
            const p = productModule.products.find(x => x.id == i.productId);
            if (!p) return;
            const base = p.price * i.qty;
            const gst = base * (p.gstPercentage / 100);
            total += base + gst;
        });
        return total;
    }
};
