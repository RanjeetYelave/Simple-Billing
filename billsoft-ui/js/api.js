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
