export const $ = (id) => document.getElementById(id);
export const money = (v) => "₹" + Number(v || 0).toFixed(2);

// Extract ID from autocomplete "name (id:3)"
export function extractId(str) {
    const m = (str || "").match(/id:(\d+)/);
    return m ? Number(m[1]) : null;
}
