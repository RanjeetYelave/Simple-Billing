// js/api.js
// Global API base URL — ALWAYS point to backend, not VS Code Live Server
export const API_BASE = "http://localhost:8080";

// -----------------------------
// Generic GET
// -----------------------------
export async function apiGet(path) {
    const res = await fetch(API_BASE + path, {
        method: "GET",
        headers: {
            "Content-Type": "application/json"
        }
    });

    if (!res.ok) {
        const text = await res.text();
        throw new Error(`GET ${path} failed: ${text}`);
    }

    return res.json();
}

// -----------------------------
// Generic POST
// -----------------------------
export async function apiPost(path, data) {
    const res = await fetch(API_BASE + path, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(data)
    });

    if (!res.ok) {
        const text = await res.text();
        throw new Error(`POST ${path} failed: ${text}`);
    }

    return res.json();
}

// -----------------------------
// Generic PUT
// -----------------------------
export async function apiPut(path, data) {
    const res = await fetch(API_BASE + path, {
        method: "PUT",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(data)
    });

    if (!res.ok) {
        const text = await res.text();
        throw new Error(`PUT ${path} failed: ${text}`);
    }

    return res.json();
}

// -----------------------------
// Generic DELETE
// -----------------------------
export async function apiDelete(path) {
    const res = await fetch(API_BASE + path, {
        method: "DELETE"
    });

    if (!res.ok) {
        const text = await res.text();
        throw new Error(`DELETE ${path} failed: ${text}`);
    }

    return true;
}

/* ============================================================
   STATEMENTS API (NEW)
   Fully Safe – Does NOT break existing functionality
   ============================================================ */

// ---------- Customer Statement (JSON) ----------
async function getCustomerStatement(customerId, from, to) {
    const params = new URLSearchParams();
    if (from) params.append("from", from);
    if (to) params.append("to", to);

    return apiGet(`/api/statements/customer/${customerId}?` + params.toString());
}

// ---------- Customer Statement PDF ----------
async function downloadCustomerStatementPdf(customerId, from, to) {
    const params = new URLSearchParams();
    if (from) params.append("from", from);
    if (to) params.append("to", to);

    const res = await fetch(API_BASE + `/api/statements/customer/${customerId}/pdf?` + params.toString(), {
        method: "GET"
    });

    if (!res.ok) {
        const text = await res.text();
        throw new Error(`PDF download failed: ${text}`);
    }

    return res.blob(); // return Blob for download
}

// ---------- Firm Statement (JSON) ----------
async function getFirmStatement(from, to) {
    const params = new URLSearchParams();
    if (from) params.append("from", from);
    if (to) params.append("to", to);

    return apiGet(`/api/statements/firm?` + params.toString());
}

// ---------- Firm Statement PDF ----------
async function downloadFirmStatementPdf(from, to) {
    const params = new URLSearchParams();
    if (from) params.append("from", from);
    if (to) params.append("to", to);

    const res = await fetch(API_BASE + `/api/statements/firm/pdf?` + params.toString(), {
        method: "GET"
    });

    if (!res.ok) {
        const text = await res.text();
        throw new Error(`PDF download failed: ${text}`);
    }

    return res.blob();
}

/* ------------------------------------------------------------
   EXPORT NEW STATEMENT METHODS
   (Attach without breaking existing imports)
   ------------------------------------------------------------ */
export const api = {
    apiGet,
    apiPost,
    apiPut,
    apiDelete,

    // NEW
    getCustomerStatement,
    downloadCustomerStatementPdf,
    getFirmStatement,
    downloadFirmStatementPdf
};
