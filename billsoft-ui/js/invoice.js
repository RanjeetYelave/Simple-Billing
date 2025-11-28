// js/invoice.js
// API wrapper for invoices (patched)
// Preserves all existing functions and adds previewCalc for backend-authoritative calculations.
// If backend preview endpoint is unavailable, falls back to a local preview computation.

import { API_BASE } from "./api.js"; // optional, if you keep API_BASE in api.js
// Note: In your repo API_BASE lives in js/api.js. If not available, fallback to localhost.
const BASE = (typeof API_BASE !== "undefined") ? API_BASE : "http://localhost:8080";
const API = BASE + "/api/invoices";

export const invoiceModule = {

  // Fetch list of invoices
  async list() {
    const res = await fetch(API);
    if (!res.ok) {
      const txt = await res.text().catch(() => res.statusText);
      throw new Error("Failed to list invoices: " + txt);
    }
    return res.json();
  },

  // Preview (get full invoice object by id)
  async preview(id) {
    const res = await fetch(`${API}/${id}`);
    if (!res.ok) {
      const txt = await res.text().catch(() => res.statusText);
      throw new Error(`Preview ${id} failed: ${txt}`);
    }
    return res.json();
  },

  // Save new invoice (server will compute authoritative totals)
  async save(payload) {
    const res = await fetch(API, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    if (!res.ok) {
      const txt = await res.text().catch(() => res.statusText);
      throw new Error("Save invoice failed: " + txt);
    }
    return res.json();
  },

  // Update existing invoice
  async update(id, payload) {
    const res = await fetch(`${API}/${id}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    if (!res.ok) {
      const txt = await res.text().catch(() => res.statusText);
      throw new Error("Update invoice failed: " + txt);
    }
    return res.json();
  },

  // Delete invoice
  async delete(id) {
    const res = await fetch(`${API}/${id}`, { method: "DELETE" });
    if (!res.ok) {
      const txt = await res.text().catch(() => res.statusText);
      throw new Error("Delete invoice failed: " + txt);
    }
    return true;
  },

  // Mark paid/unpaid
  async markPaid(id, paid) {
    const res = await fetch(`${API}/${id}/paid?paid=${paid}`, {
      method: "PUT"
    });
    if (!res.ok) {
      const txt = await res.text().catch(() => res.statusText);
      throw new Error(`Mark paid failed: ${txt}`);
    }
    return res.json();
  },

  // ---- ANALYTICS ----
  async analyticsByCustomer(customerId) {
    const res = await fetch(`${API}/analytics/customer/${customerId}`);
    if (!res.ok) {
      const txt = await res.text().catch(() => res.statusText);
      throw new Error(`Analytics by customer failed: ${txt}`);
    }
    return res.json();
  },

  async analyticsByName(name) {
    const res = await fetch(
      `${API}/analytics/search?name=${encodeURIComponent(name)}`
    );
    if (!res.ok) {
      const txt = await res.text().catch(() => res.statusText);
      throw new Error(`Analytics by name failed: ${txt}`);
    }
    return res.json();
  },

  // ---- PDF DOWNLOAD (robust handling) ----
  async pdf(id, size = "A4") {
    const url = `${API}/${id}/pdf?size=${encodeURIComponent(size)}`;
    const res = await fetch(url);
    // If server returned not-ok, try to capture returned error message (text/json)
    if (!res.ok) {
      // Try json first
      let txt;
      try {
        const j = await res.json();
        txt = j?.message || JSON.stringify(j);
      } catch (e) {
        txt = await res.text().catch(() => res.statusText);
      }
      throw new Error("Failed to fetch invoice PDF: " + txt);
    }

    // On success return blob (caller expects blob)
    const blob = await res.blob();
    return blob;
  },

  // -------------------------------------------------------
  // NEW: previewCalc(payload)
  // - Send the invoice payload to backend to receive authoritative totals.
  // - Backend endpoint expected: POST /api/invoices/preview (not persisted)
  // - If backend preview endpoint is missing/unavailable, fallback to client-side computePreview.
  // - Returns an object with structure:
  //   { subtotalWithoutTax, totalTax, discountAmount, grandTotal, items: [ {taxableAmount, gstAmount, lineTotal, ...} ] }
  // -------------------------------------------------------
  async previewCalc(payload) {
    // Try backend first
    try {
      const res = await fetch(`${API}/preview`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      });

      if (res.ok) {
        return res.json();
      } else {
        // If preview endpoint not implemented (404) or error, try to parse message for debugging then fallback
        const errText = await res.text().catch(() => res.statusText);
        console.warn("invoiceModule.previewCalc backend preview failed:", errText);
        // fallthrough to local calc
      }
    } catch (e) {
      // networks issues or endpoint not found -> fallback
      console.warn("invoiceModule.previewCalc backend call failed, falling back to client calc:", e && e.message ? e.message : e);
    }

    // Fallback client-side calculation (best-effort, matches server logic approx)
    return this._computePreviewLocally(payload);
  },

  // Local fallback calculator (keeps parity with backend rounding / logic)
  _computePreviewLocally(payload) {
    // payload: { items: [ { productId, qty, unit, pricePerUnit, discountType, discountValue, discountPercent, gstPercent } ], invoiceDiscount: { type, value } }
    const itemsIn = Array.isArray(payload.items) ? payload.items : [];

    // Build items with computed fields
    const items = itemsIn.map(it => {
      const qty = Number(it.qty) || 0;
      const price = Number(it.pricePerUnit) || 0;
      const base = qty * price;

      const dpct = it.discountPercent != null ? Number(it.discountPercent) : 0;
      const dval = it.discountValue != null ? Number(it.discountValue) : 0;

      const disc = dpct > 0 ? (base * dpct / 100) : dval;
      const taxable = Math.max(0, base - disc);

      const gstPct = it.gstPercent != null ? Number(it.gstPercent) : 0;
      const gstAmt = taxable * gstPct / 100;
      const lineTotal = taxable + gstAmt;

      return {
        ...it,
        base,
        discountAmount: disc,
        taxableAmount: taxable,
        gstAmount: gstAmt,
        lineTotal
      };
    });

    // Sum taxable before invoice-level discount
    let taxableSum = items.reduce((s, it) => s + (it.taxableAmount || 0), 0);
    if (taxableSum < 0) taxableSum = 0;

    // invoice-level discount
    let invoiceDiscAmt = 0;
    const invDisc = payload.invoiceDiscount;
    if (invDisc && invDisc.value) {
      const v = Number(invDisc.value) || 0;
      if (invDisc.type && invDisc.type.toUpperCase() === "PERCENT") {
        const pct = Math.max(0, Math.min(100, v));
        invoiceDiscAmt = taxableSum * pct / 100;
      } else {
        invoiceDiscAmt = v;
      }
    }

    // Distribute invoice-level discount proportionally across taxable items
    if (invoiceDiscAmt > 0 && taxableSum > 0) {
      items.forEach(it => {
        const share = (it.taxableAmount || 0) / taxableSum;
        const reduction = invoiceDiscAmt * share;
        const newTaxable = Math.max(0, (it.taxableAmount || 0) - reduction);
        const gstPct = Number(it.gstPercent) || 0;
        const newGst = newTaxable * gstPct / 100;
        it.taxableAmount = roundTo2(newTaxable);
        it.gstAmount = roundTo2(newGst);
        it.lineTotal = roundTo2(newTaxable + newGst);
      });
    } else {
      // round those values
      items.forEach(it => {
        it.taxableAmount = roundTo2(it.taxableAmount || 0);
        it.gstAmount = roundTo2(it.gstAmount || 0);
        it.lineTotal = roundTo2(it.lineTotal || 0);
      });
    }

    const subtotalWithoutTax = roundTo2(items.reduce((s, it) => s + (it.taxableAmount || 0), 0));
    const totalTax = roundTo2(items.reduce((s, it) => s + (it.gstAmount || 0), 0));
    const totalDiscount = roundTo2(itemsIn.reduce((s, it, idx) => {
      // per-item discount amounts + invoice-level discount total
      const perItemDisc = (it.discountPercent && it.discountPercent > 0)
        ? ((it.qty || 0) * (it.pricePerUnit || 0) * (it.discountPercent / 100))
        : (it.discountValue || 0);
      return s + perItemDisc;
    }, 0) + invoiceDiscAmt);

    const grandTotal = roundTo2(subtotalWithoutTax + totalTax);

    return {
      subtotalWithoutTax,
      totalTax,
      discountAmount: totalDiscount,
      grandTotal,
      items
    };

    function roundTo2(v) {
      return Math.round((v || 0) * 100) / 100;
    }
  }

};
