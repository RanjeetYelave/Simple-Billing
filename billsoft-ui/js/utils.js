// js/utils.js
export function $(idOrSelector) {
  if (!idOrSelector) return null;
  // try by id first
  const byId = document.getElementById(idOrSelector);
  if (byId) return byId;
  // otherwise querySelector
  return document.querySelector(idOrSelector);
}

export function money(n) {
  if (n == null || isNaN(n)) return "₹0.00";
  return "₹ " + Number(n).toFixed(2);
}

// extract numeric id from strings like "Name (id:3)"
export function extractId(text) {
  if (!text) return null;
  const m = /id\s*[:=]\s*(\d+)/i.exec(text);
  if (m) return Number(m[1]);
  // also allow bare number
  if (/^\d+$/.test(text.trim())) return Number(text.trim());
  return null;
}
