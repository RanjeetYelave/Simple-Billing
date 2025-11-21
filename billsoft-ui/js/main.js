// js/main.js
import { ui } from "./ui.js";
import { productModule } from "./product.js";
import { customerModule } from "./customer.js";
import { invoiceModule } from "./invoice.js";
import { $, extractId } from "./utils.js";

async function init() {
  // Render UI skeleton
  ui.render();

  // Load data from backend
  try {
    await Promise.all([productModule.load(), customerModule.load()]);
  } catch (err) {
    console.error("Failed to load products/customers:", err);
    alert("Failed to load initial data. Check console.");
  }

  // Populate customers datalist & other selects
  ui.populateCustomers();

  // Ensure product selects in create table are populated when adding rows
  // Add an initial empty item row so user can begin immediately
  ui.clearCreateItems();
  ui.addCreateItemRow();

  // ---------- BIND CREATE-INVOICE CONTROLS ----------
  // Add Item
  const addBtn = $("createAddItem");
  if (addBtn) addBtn.onclick = () => {
    ui.addCreateItemRow();
  };

  // Clear items
  const clearBtn = $("createClear");
  if (clearBtn) clearBtn.onclick = () => {
    if (confirm("Clear all items?")) {
      ui.clearCreateItems();
      ui.addCreateItemRow();
    }
  };

  // Discount inputs -> recalc totals live
  const discType = $("discountType");
  const discVal = $("discountValue");
  if (discType) discType.onchange = () => ui.recalcCreateTotals();
  if (discVal) discVal.oninput = () => ui.recalcCreateTotals();

  // Create invoice (save)
  const saveBtn = $("saveInvBtn");
  if (saveBtn) saveBtn.onclick = async () => {
    saveBtn.disabled = true;
    saveBtn.innerText = "Saving...";
    try {
      await ui.submitCreateInvoice();
      // refresh products/customers/invoices view if needed
      // you may want to fetch invoices list again or reset form
    } catch (err) {
      console.error("Create invoice failed:", err);
      alert("Create invoice failed. See console.");
    } finally {
      saveBtn.disabled = false;
      saveBtn.innerText = "Create Invoice";
    }
  };

  // ---------- BIND FETCH CONTROLS ----------
  const fetchAllBtn = $("fetchAllInvBtn");
  if (fetchAllBtn) fetchAllBtn.onclick = async () => {
    try {
      const all = await invoiceModule.list();
      ui.renderInvoiceList(all);
    } catch (err) {
      console.error("Fetch all invoices error:", err);
      alert("Failed to fetch invoices. See console.");
    }
  };

  const fetchByIdBtn = $("fetchInvIdBtn");
  if (fetchByIdBtn) fetchByIdBtn.onclick = async () => {
    const id = $("fetchInvId").value.trim();
    if (!id) return alert("Enter invoice ID");
    try {
      const inv = await invoiceModule.preview(id);
      ui.renderInvoiceList([inv]);
    } catch (err) {
      console.error("Fetch invoice by id error:", err);
      alert("Failed to fetch invoice. See console.");
    }
  };

  const fetchByCustomerBtn = $("fetchInvCustomerBtn");
  if (fetchByCustomerBtn) fetchByCustomerBtn.onclick = async () => {
    const cid = $("fetchInvCustomer").value.trim();
    if (!cid) return alert("Enter customer ID");
    try {
      const all = await invoiceModule.list();
      const filtered = all.filter(inv => inv.customer && String(inv.customer.id) === String(cid));
      ui.renderInvoiceList(filtered);
    } catch (err) {
      console.error("Fetch by customer error:", err);
      alert("Failed to fetch invoices. See console.");
    }
  };

  // Optional: load invoices list initially (comment/uncomment as you like)
  // try { const all = await invoiceModule.list(); ui.renderInvoiceList(all); } catch(e){}

  // ---------- BIND Create Customer / Product buttons ----------
  const saveCustBtn = $("saveCustBtn");
  if (saveCustBtn) saveCustBtn.onclick = async () => {
    try {
      await customerModule.save();
      await customerModule.load();
      ui.populateCustomers();
      alert("Customer saved.");
    } catch (err) {
      console.error("Save customer error:", err);
      alert("Failed to save customer. See console.");
    }
  };

  const saveProdBtn = $("saveProdBtn");
  if (saveProdBtn) saveProdBtn.onclick = async () => {
    try {
      await productModule.save();
      await productModule.load();
      alert("Product saved.");
    } catch (err) {
      console.error("Save product error:", err);
      alert("Failed to save product. See console.");
    }
  };

  // Ensure dynamic selects (product lists) are refreshed after product create
  // If user adds product, they may need to manually reopen or add a new row to pick it.

  // Debug info
  console.log("UI initialized");
}

init();
