// js/invoice.js
// Invoice module — unified, backend-first, robust, backward-compatible.
// Uses exported `api` object from ./api.js when available. Falls back to direct fetch if not.

import { API_BASE, api as apiHelpers } from "./api.js"; // api.js exports API_BASE and `api` object

const BASE = (typeof API_BASE !== "undefined" && API_BASE) ? API_BASE : "http://localhost:8080";
const API = BASE + "/api/invoices";

// Helper to detect if apiHelpers exists and function exists
function hasApi(fnName) {
  return apiHelpers && typeof apiHelpers[fnName] === "function";
}

export const invoiceModule = {
  // ------------------------------------------
  // LISTS
  // ------------------------------------------
  // Fetch list of invoices (all statuses)
  async list() {
    if (hasApi("getAllInvoices")) {
      return apiHelpers.getAllInvoices();
    }
    const res = await fetch(API);
    if (!res.ok) {
      const txt = await res.text().catch(() => res.statusText);
      throw new Error("Failed to list invoices: " + txt);
    }
    return res.json();
  },

  // Fetch only ESTIMATES
  async listEstimates() {
    if (hasApi("getAllEstimates")) {
      return apiHelpers.getAllEstimates();
    }
    const res = await fetch(`${API}/estimates`);
    if (!res.ok) {
      const txt = await res.text().catch(() => res.statusText);
      throw new Error("Failed to list estimates: " + txt);
    }
    return res.json();
  },

  // Fetch only FINAL invoices (helper, not currently used but available)
  async listFinal() {
    if (hasApi("getAllFinalInvoices")) {
      return apiHelpers.getAllFinalInvoices();
    }
    const res = await fetch(`${API}/final`);
    if (!res.ok) {
      const txt = await res.text().catch(() => res.statusText);
      throw new Error("Failed to list final invoices: " + txt);
    }
    return res.json();
  },

  // Get invoice/estimate by id
  async preview(id) {
    if (hasApi("getInvoiceById")) {
      return apiHelpers.getInvoiceById(id);
    }
    const res = await fetch(`${API}/${id}`);
    if (!res.ok) {
      const txt = await res.text().catch(() => res.statusText);
      throw new Error(`Preview ${id} failed: ${txt}`);
    }
    return res.json();
  },

  // ------------------------------------------
  // CREATE / UPDATE / DELETE
  // ------------------------------------------

  // Save new invoice/estimate (server will compute authoritative totals)
  // If payload.status === 'ESTIMATE' will call estimate endpoint when api helper available
  async save(payload) {
    // prefer api helper createInvoice/createEstimate
    try {
      if (payload && payload.status && payload.status.toString().toUpperCase() === "ESTIMATE") {
        if (hasApi("createEstimate")) return apiHelpers.createEstimate(payload);
        // fallback to estimate endpoint
        const res = await fetch(`${API}/estimate`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload)
        });
        if (!res.ok) {
          const txt = await res.text().catch(() => res.statusText);
          throw new Error("Create estimate failed: " + txt);
        }
        return res.json();
      } else {
        if (hasApi("createInvoice")) return apiHelpers.createInvoice(payload);
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
      }
    } catch (err) {
      // bubble up with consistent message
      throw err;
    }
  },

  // Update existing invoice (full update)
  async update(id, payload) {
    if (hasApi("updateInvoice")) return apiHelpers.updateInvoice(id, payload);

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

  // Delete invoice / estimate
  async delete(id) {
    if (hasApi("deleteInvoice")) return apiHelpers.deleteInvoice(id);

    const res = await fetch(`${API}/${id}`, { method: "DELETE" });
    if (!res.ok) {
      const txt = await res.text().catch(() => res.statusText);
      throw new Error("Delete invoice failed: " + txt);
    }
    return true;
  },

  // Mark paid/unpaid
  async markPaid(id, paid) {
    if (hasApi("markInvoicePaid")) return apiHelpers.markInvoicePaid(id, paid);

    const res = await fetch(`${API}/${id}/paid?paid=${paid}`, { method: "PUT" });
    if (!res.ok) {
      const txt = await res.text().catch(() => res.statusText);
      throw new Error(`Mark paid failed: ${txt}`);
    }
    return res.json();
  },

  // Convert ESTIMATE → FINAL INVOICE
  async convert(estimateId, overridePayload = {}) {
    if (!estimateId) throw new Error("estimateId is required");

    // Prefer helper
    if (hasApi("convertEstimate")) {
      // api.convertEstimate currently ignores payload, extra arg is harmless
      return apiHelpers.convertEstimate(estimateId, overridePayload);
    }

    // Fallback direct call
    const res = await fetch(`${API}/convert/${estimateId}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(overridePayload || {})
    });

    if (!res.ok) {
      const txt = await res.text().catch(() => res.statusText);
      throw new Error("Convert estimate failed: " + txt);
    }

    return res.json();
  },

  // ------------------------------------------
  // ANALYTICS
  // ------------------------------------------
  async analyticsByCustomer(customerId) {
    // note: older code used endpoint /api/invoices/analytics/customer/{id}
    if (hasApi("analyticsByCustomer")) return apiHelpers.analyticsByCustomer(customerId);
    const res = await fetch(`${API}/analytics/customer/${customerId}`);
    if (!res.ok) {
      const txt = await res.text().catch(() => res.statusText);
      throw new Error(`Analytics by customer failed: ${txt}`);
    }
    return res.json();
  },

  async analyticsByName(name) {
    if (hasApi("analyticsByName")) return apiHelpers.analyticsByName(name);
    const res = await fetch(`${API}/analytics/search?name=${encodeURIComponent(name)}`);
    if (!res.ok) {
      const txt = await res.text().catch(() => res.statusText);
      throw new Error(`Analytics by name failed: ${txt}`);
    }
    return res.json();
  },

  // ------------------------------------------
  // PDF
  // ------------------------------------------
  // PDF download (returns Blob)
  async pdf(id, size = "A4") {
    // prefer helper
    if (hasApi("downloadInvoicePdf")) {
      return apiHelpers.downloadInvoicePdf(id, size);
    }

    const url = `${API}/${id}/pdf?size=${encodeURIComponent(size)}`;
    const res = await fetch(url);
    if (!res.ok) {
      let txt;
      try {
        const j = await res.json();
        txt = j?.message || JSON.stringify(j);
      } catch (e) {
        txt = await res.text().catch(() => res.statusText);
      }
      throw new Error("Failed to fetch invoice PDF: " + txt);
    }
    return res.blob();
  },

  // ------------------------------------------
  // PREVIEW CALC
  // ------------------------------------------
  // previewCalc(payload)
  // - Sends payload to backend preview endpoint to get authoritative calculation (not persisted)
  // - If backend preview endpoint is missing or fails, falls back to local calculation (_computePreviewLocally)
  async previewCalc(payload) {
    // Try backend first via api helper
    if (hasApi("previewInvoice")) {
      try {
        return await apiHelpers.previewInvoice(payload);
      } catch (err) {
        console.warn("api.previewInvoice failed, falling back to local calc:", err && err.message ? err.message : err);
      }
    } else {
      // try direct endpoint
      try {
        const res = await fetch(`${API}/preview`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload)
        });
        if (res.ok) {
          return res.json();
        } else {
          const txt = await res.text().catch(() => res.statusText);
          console.warn("Backend preview endpoint returned not-ok:", txt);
        }
      } catch (e) {
        console.warn("Backend preview call failed (network), falling back:", e && e.message ? e.message : e);
      }
    }

    // fallback: local compute
    return this._computePreviewLocally(payload);
  },

  // Local fallback calculator (keeps parity with backend as best-effort)
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
        discountAmount: round2(disc),
        taxableAmount: round2(taxable),
        gstAmount: round2(gstAmt),
        lineTotal: round2(lineTotal)
      };
    });

    // Sum taxable before invoice-level discount
    let taxableSum = items.reduce((s, it) => s + (it.taxableAmount || 0), 0);
    if (taxableSum < 0) taxableSum = 0;

    // invoice-level discount amount
    let invoiceDiscAmt = 0;
    const invDisc = payload.invoiceDiscount;
    if (invDisc && invDisc.value != null) {
      const v = Number(invDisc.value) || 0;
      if (invDisc.type && invDisc.type.toString().toUpperCase() === "PERCENT") {
        const pct = Math.max(0, Math.min(100, v));
        invoiceDiscAmt = taxableSum * pct / 100;
      } else {
        invoiceDiscAmt = v;
      }
      invoiceDiscAmt = round2(invoiceDiscAmt);
    }

    // Distribute invoice-level discount proportionally across taxable items
    if (invoiceDiscAmt > 0 && taxableSum > 0) {
      items.forEach(it => {
        const share = (it.taxableAmount || 0) / taxableSum;
        const reduction = invoiceDiscAmt * share;
        const newTaxable = Math.max(0, (it.taxableAmount || 0) - reduction);
        const gstPct = Number(it.gstPercent) || 0;
        const newGst = newTaxable * gstPct / 100;
        it.taxableAmount = round2(newTaxable);
        it.gstAmount = round2(newGst);
        it.lineTotal = round2(newTaxable + newGst);
      });
    }

    const subtotalWithoutTax = round2(items.reduce((s, it) => s + (it.taxableAmount || 0), 0));
    const totalTax = round2(items.reduce((s, it) => s + (it.gstAmount || 0), 0));

    // total discount = per-item discounts + invoice level discount
    const perItemDiscSum = itemsIn.reduce((s, it) => {
      const qty = Number(it.qty) || 0;
      const price = Number(it.pricePerUnit) || 0;
      const base = qty * price;
      const dpct = it.discountPercent != null ? Number(it.discountPercent) : 0;
      const dval = it.discountValue != null ? Number(it.discountValue) : 0;
      const perItemDisc = dpct > 0 ? (base * dpct / 100) : dval;
      return s + (perItemDisc || 0);
    }, 0);

    const totalDiscount = round2(perItemDiscSum + invoiceDiscAmt);
    const grandTotal = round2(subtotalWithoutTax + totalTax);

    return {
      subtotalWithoutTax,
      totalTax,
      discountAmount: totalDiscount,
      grandTotal,
      items
    };

    function round2(v) {
      return Math.round((v || 0) * 100) / 100;
    }
  }

}; // end invoiceModule
