export const BASE = "http://localhost:8080";

async function api(path, options = {}) {
    const res = await fetch(BASE + path, options);
    if (!res.ok) throw new Error(await res.text());
    return res.json();
}

// Customers
export const getCustomers = () => api("/api/customers");
export const createCustomer = (data) => api("/api/customers", {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(data)
});
export const getCustomer = (id) => api(`/api/customers/${id}`);

// Products
export const getProducts = () => api("/api/products");
export const createProduct = (data) => api("/api/products", {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(data)
});
export const getProduct = (id) => api(`/api/products/${id}`);

// Invoices
export const getInvoices = () => api("/api/invoices");
export const createInvoice = (data) => api("/api/invoices", {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(data)
});
export const getInvoice = (id) => api(`/api/invoices/${id}`);
