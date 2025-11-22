// js/ui/firm-profile-screen.js
import { $, fileToBase64 } from "../utils.js";

export const firmProfileScreen = {

  data: null,

  render() {
    return `
      <div class="card">
        <h2>Firm Profile</h2>

        <div style="display:grid;grid-template-columns:1fr 1fr;gap:16px;">

          <div>
            <label>Firm Name</label>
            <input id="fp_firmName">

            <label>Owner Name</label>
            <input id="fp_ownerName">

            <label>Address Line 1</label>
            <input id="fp_addr1">

            <label>Address Line 2</label>
            <input id="fp_addr2">

            <label>City</label>
            <input id="fp_city">

            <label>State</label>
            <input id="fp_state">

            <label>Pincode</label>
            <input id="fp_pin">
          </div>

          <div>
            <label>Phone</label>
            <input id="fp_phone">

            <label>Email</label>
            <input id="fp_email">

            <label>GSTIN</label>
            <input id="fp_gstin">

            <h3 style="margin-top:8px;">Bank Details</h3>

            <label>Bank Name</label>
            <input id="fp_bankName">

            <label>Account No.</label>
            <input id="fp_bankAcc">

            <label>IFSC</label>
            <input id="fp_bankIFSC">

            <label>Invoice Prefix</label>
            <input id="fp_prefix">
          </div>
        </div>

        <h3 style="margin-top:20px;">Branding</h3>
        <div style="display:flex;gap:16px;align-items:center;">
          <img id="fp_logoPreview" 
               style="width:120px;height:120px;object-fit:contain;
                      background:#111;border-radius:8px;border:1px solid #334;" />

          <div>
            <input type="file" id="fp_logoInput" accept="image/*">
            <button class="btn small" id="fp_uploadLogoBtn">Upload Logo</button>
            <button class="btn small danger" id="fp_removeLogoBtn">Remove Logo</button>
          </div>
        </div>

        <h3 style="margin-top:20px;">Footer Note</h3>
        <textarea id="fp_footer" rows="3"></textarea>

        <button class="btn primary save-big" id="fp_saveBtn" style="margin-top:16px;">
          💾 Save Profile
        </button>

      </div>
    `;
  },

  async init() {
    await this.load();
    this.fillForm();

    $("fp_uploadLogoBtn").onclick = () => this.uploadLogo();
    $("fp_removeLogoBtn").onclick = () => this.removeLogo();
    $("fp_saveBtn").onclick = () => this.save();
  },

  async load() {
    const res = await fetch("http://localhost:8080/api/firm");
    this.data = await res.json();
  },

  fillForm() {
    const d = this.data || {};

    $("fp_firmName").value = d.firmName || "";
    $("fp_ownerName").value = d.ownerName || "";
    $("fp_addr1").value = d.addressLine1 || "";
    $("fp_addr2").value = d.addressLine2 || "";
    $("fp_city").value = d.city || "";
    $("fp_state").value = d.state || "";
    $("fp_pin").value = d.pincode || "";
    $("fp_phone").value = d.phone || "";
    $("fp_email").value = d.email || "";
    $("fp_gstin").value = d.gstin || "";

    $("fp_bankName").value = d.bankName || "";
    $("fp_bankAcc").value = d.bankAccountNo || "";
    $("fp_bankIFSC").value = d.bankIFSC || "";

    $("fp_prefix").value = d.invoicePrefix || "";

    $("fp_footer").value = d.footerNote || "";

    $("fp_logoPreview").src = d.logoBase64 ? `data:image/png;base64,${d.logoBase64}` : "";
  },

  async uploadLogo() {
    const file = $("fp_logoInput").files[0];
    if (!file) return alert("Choose a file first");

    const base64 = await fileToBase64(file);
    this.data.logoBase64 = base64.split(",")[1]; // pure base64

    $("fp_logoPreview").src = base64;
  },

  removeLogo() {
    this.data.logoBase64 = null;
    $("fp_logoPreview").src = "";
  },

  async save() {
    const body = {
      firmName: $("fp_firmName").value,
      ownerName: $("fp_ownerName").value,
      addressLine1: $("fp_addr1").value,
      addressLine2: $("fp_addr2").value,
      city: $("fp_city").value,
      state: $("fp_state").value,
      pincode: $("fp_pin").value,
      phone: $("fp_phone").value,
      email: $("fp_email").value,
      gstin: $("fp_gstin").value,
      bankName: $("fp_bankName").value,
      bankAccountNo: $("fp_bankAcc").value,
      bankIFSC: $("fp_bankIFSC").value,
      invoicePrefix: $("fp_prefix").value,
      footerNote: $("fp_footer").value,
      logoBase64: this.data.logoBase64 || null
    };

    const res = await fetch("http://localhost:8080/api/firm", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body)
    });

    if (res.ok) alert("Profile Saved");
    else alert("Failed to save");
  }
};
