// js/invoice.js

import { createInvoice, getInvoices, getInvoice, updateInvoice } from "./api.js";
import { money } from "./utils.js";
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

    async update(id, payload) {
        return await updateInvoice(id, payload);
    },

    calculateTotal(items) {
        let total = 0;
        items.forEach(i => {
            const base = i.price * i.quantity;
            const gst = base * (i.gstPercentage / 100);
            total += base + gst;
        });
        return total;
    }
};
