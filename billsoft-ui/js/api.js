// js/api.js
export const API_BASE = "http://localhost:8080";

/* ============================================================
   BASE HELPERS
============================================================ */

async function handleResp(res, textOnError) {
  if (res.ok) {
    const ct = res.headers.get("content-type") || "";
    if (ct.includes("application/json")) return res.json();
    return res.text();
  }
  const txt = await res.text().catch(() => res.statusText);
  throw new Error(textOnError ? `${textOnError}: ${txt}` : txt);
}

export async function apiGet(path) {
  const res = await fetch(API_BASE + path, {
    method: "GET",
    headers: { "Content-Type": "application/json" }
  });
  return handleResp(res, `GET ${path} failed`);
}

export async function apiPost(path, data) {
  const res = await fetch(API_BASE + path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: data != null ? JSON.stringify(data) : null
  });
  return handleResp(res, `POST ${path} failed`);
}

export async function apiPut(path, data) {
  const res = await fetch(API_BASE + path, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: data != null ? JSON.stringify(data) : null
  });
  return handleResp(res, `PUT ${path} failed`);
}

export async function apiDelete(path) {
  const res = await fetch(API_BASE + path, { method: "DELETE" });
  if (!res.ok) {
    const txt = await res.text().catch(() => res.statusText);
    throw new Error(`DELETE ${path} failed: ${txt}`);
  }
  return true;
}

/* ============================================================
   AUTH ENDPOINTS
============================================================ */

// ---------- AUTH ----------
export function authLogin(loginId, password, activationKey = null) {
  return apiPost("/api/auth/login", { loginId, password, activationKey });
}

export function authRegister(loginId, password) {
  return apiPost("/api/auth/register", { loginId, password });
}



export function authDeveloperValidate(loginId, secureKey) {
  return apiPost("/api/auth/forgot-password/validate", {
    loginId,
    developerKey: secureKey
  });
}

export function authDeveloperReset(loginId, secureKey, newPassword) {
  return apiPost("/api/auth/forgot-password/developer-reset", {
    loginId,
    developerKey: secureKey,
    newPassword
  });
}


/* ============================================================
   INVOICE & ESTIMATE ENDPOINTS
============================================================ */

export function createInvoice(data) {
  return apiPost("/api/invoices", data);
}

export function createEstimate(data) {
  return apiPost("/api/invoices/estimate", data);
}

export function previewInvoice(data) {
  return apiPost("/api/invoices/preview", data);
}

export function convertEstimate(estimateId, overrideRequest = null) {
  return apiPost(`/api/invoices/convert/${estimateId}`, overrideRequest || {});
}

export function updateInvoice(id, data) {
  return apiPut(`/api/invoices/${id}`, data);
}

export function markInvoicePaid(id, paid) {
  return apiPut(`/api/invoices/${id}/paid?paid=${paid}`);
}

export function deleteInvoice(id) {
  return apiDelete(`/api/invoices/${id}`);
}

export function getAllInvoices() {
  return apiGet("/api/invoices");
}

export function getAllEstimates() {
  return apiGet("/api/invoices/estimates");
}

export function getAllFinalInvoices() {
  return apiGet("/api/invoices/final");
}

export function getInvoiceById(id) {
  return apiGet(`/api/invoices/${id}`);
}

export async function downloadInvoicePdf(id, size = "A4") {
  const res = await fetch(`${API_BASE}/api/invoices/${id}/pdf?size=${encodeURIComponent(size)}`, { method: "GET" });
  if (!res.ok) {
    const txt = await res.text().catch(() => res.statusText);
    throw new Error(`Invoice PDF failed: ${txt}`);
  }
  return res.blob();
}

/* ============================================================
   Statements
============================================================ */

export function getCustomerStatement(customerId, from, to) {
  const params = new URLSearchParams();
  if (from) params.append("from", from);
  if (to) params.append("to", to);
  return apiGet(`/api/statements/customer/${customerId}?${params.toString()}`);
}

export function downloadCustomerStatementPdf(customerId, from, to) {
  const params = new URLSearchParams();
  if (from) params.append("from", from);
  if (to) params.append("to", to);
  return fetch(`${API_BASE}/api/statements/customer/${customerId}/pdf?${params.toString()}`, { method: "GET" })
    .then(res => {
      if (!res.ok) return res.text().then(t => { throw new Error(t); });
      return res.blob();
    });
}

export function getFirmStatement(from, to) {
  const params = new URLSearchParams();
  if (from) params.append("from", from);
  if (to) params.append("to", to);
  return apiGet(`/api/statements/firm?${params.toString()}`);
}

export function downloadFirmStatementPdf(from, to) {
  const params = new URLSearchParams();
  if (from) params.append("from", from);
  if (to) params.append("to", to);
  return fetch(`${API_BASE}/api/statements/firm/pdf?${params.toString()}`, { method: "GET" })
    .then(res => {
      if (!res.ok) return res.text().then(t => { throw new Error(t); });
      return res.blob();
    });
}

/* ============================================================
   Export unified api object
============================================================ */
export const api = {
  apiGet, apiPost, apiPut, apiDelete,
  authLogin, authRegister, authDeveloperValidate, authDeveloperReset,
  createInvoice, createEstimate, previewInvoice, convertEstimate, updateInvoice, markInvoicePaid, deleteInvoice,
  getAllInvoices, getAllEstimates, getAllFinalInvoices, getInvoiceById, downloadInvoicePdf,
  getCustomerStatement, downloadCustomerStatementPdf, getFirmStatement, downloadFirmStatementPdf
};
