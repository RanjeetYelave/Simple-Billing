// js/utils.js
export function $(idOrSelector) {
  if (!idOrSelector) return null;
  const byId = document.getElementById(idOrSelector);
  if (byId) return byId;
  return document.querySelector(idOrSelector);
}

// money: accepts number, numeric string, BigDecimal string
export function money(n) {
  if (n == null) return "₹ 0.00";
  // if object has toString that is BigDecimal-like, coerce
  const num = Number(n);
  if (isNaN(num)) return "₹ 0.00";
  return "₹ " + num.toFixed(2);
}

export function extractId(text) {
  if (!text) return null;
  const m = /id\s*[:=]\s*(\d+)/i.exec(text);
  if (m) return Number(m[1]);
  if (/^\d+$/.test(text.trim())) return Number(text.trim());
  return null;
}

export function fileToBase64(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const result = reader.result || "";
      const base64 = result.includes("base64,") ? result.split("base64,")[1] : result;
      resolve(base64);
    };
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}
