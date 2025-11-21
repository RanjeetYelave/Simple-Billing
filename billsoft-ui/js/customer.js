import { getCustomers, createCustomer, getCustomer } from "./api.js";
import { $, extractId } from "./utils.js";

export const customerModule = {
    customers: [],

    async load() {
        this.customers = await getCustomers();
        return this.customers;
    },

    async save() {
        const data = {
            name: $("c_name").value.trim(),
            phone: $("c_phone").value.trim(),
            email: $("c_email").value.trim(),
            address: $("c_address").value.trim(),
        };
        return await createCustomer(data);
    },

    async preview(id) {
        return await getCustomer(id);
    }
};
