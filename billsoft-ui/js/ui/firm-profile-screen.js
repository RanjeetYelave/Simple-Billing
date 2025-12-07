// js/ui/firm-profile-screen.js
import { apiGet, apiPut, getCurrentFirmId } from "../api.js";
import { $ } from "../utils.js";

export const firmProfileScreen = {

  current: null,

  maxWidth: 600,
  maxHeight: 200,
  minWidth: 80,
  minHeight: 30,
  maxFileSizeBytes: 350 * 1024,

  render() {
    return `
      <div class="card">
        <h2>Firm Profile</h2>

        <div id="firmMsg" class="small muted" style="margin-bottom:8px;"></div>

        <div style="display:grid;grid-template-columns:1fr 320px;gap:20px;align-items:start;">

          <div>
            <label>Firm Name</label>
            <input id="firmName" />
            <label>Owner</label>
            <input id="firmOwner" />
            <label>Address Line 1</label>
            <input id="firmAddr1" />
            <label>Address Line 2</label>
            <input id="firmAddr2" />

            <div style="display:grid;grid-template-columns:1fr 1fr;gap:8px;">
              <div><label>City</label><input id="firmCity" /></div>
              <div><label>State</label><input id="firmState" /></div>
            </div>

            <div style="display:grid;grid-template-columns:1fr 1fr;gap:8px;">
              <div><label>Pincode</label><input id="firmPincode" /></div>
              <div><label>Phone</label><input id="firmPhone" /></div>
            </div>

            <label>Email</label>
            <input id="firmEmail" />

            <label>GSTIN</label>
            <input id="firmGstin" />

            <h3 style="margin-top:14px;">Bank Details</h3>
            <label>Bank Name</label>
            <input id="firmBankName" />
            <label>Account</label>
            <input id="firmBankAccount" />
            <label>IFSC</label>
            <input id="firmBankIfsc" />

            <label style="margin-top:14px;">Footer Note (PDF)</label>
            <textarea id="firmFooter"></textarea>

            <div style="display:flex;gap:10px;margin-top:14px;">
              <button class="btn primary" id="saveFirmBtn">Save</button>
              <button class="btn ghost" id="resetFirmBtn">Reload</button>
            </div>
          </div>

          <!-- LOGO -->
          <div style="background:var(--card-bg);padding:16px;border-radius:10px;border:1px solid var(--border);">
            <div style="display:flex;justify-content:space-between;">
              <strong>Logo</strong>
              <span class="small muted">Max ${this.maxWidth}×${this.maxHeight}px</span>
            </div>

            <div id="logoPreviewBox"
                 style="margin-top:10px;height:120px;display:flex;align-items:center;justify-content:center;
                 background:var(--subtle-bg);border-radius:8px;">
              <span class="small muted">No logo</span>
            </div>

            <input id="logoFileInput" type="file"
                   accept="image/png,image/jpeg,image/jpg,image/svg+xml"
                   style="margin-top:12px;" />

            <div style="display:flex;gap:8px;margin-top:10px;">
              <button class="btn small" id="uploadLogoBtn">Upload Logo</button>
              <button class="btn small ghost" id="removeLogoBtn">Remove</button>
            </div>

            <div id="logoInfo" class="small muted" style="margin-top:8px;"></div>
          </div>

        </div>
      </div>
    `;
  },

  init() {
    $("saveFirmBtn").onclick = () => this.save();
    $("resetFirmBtn").onclick = () => this.load();

    $("uploadLogoBtn").onclick = () => this.saveLogoOnly();

    $("removeLogoBtn").onclick = async () => {
      if (!confirm("Remove logo?")) return;
      this.current.logoBase64 = null;
      await this.saveLogoOnly();
    };

    $("logoFileInput").onchange = async (e) => {
      const f = e.target.files?.[0];
      if (!f) return;
      await this.handleFile(f);
    };

    this.load();
  },

  async load() {
    try {
      const firmId = getCurrentFirmId();
      if (!firmId) {
        $("firmMsg").textContent = "No firm selected. Please login again.";
        return;
      }

      $("firmMsg").textContent = "Loading...";
      const data = await apiGet(`/api/firm?firmId=${encodeURIComponent(firmId)}`);

      this.current = data || {};

      this.populate(data);
      this.showPreview(this.buildDataUrl(data?.logoBase64));

      $("firmMsg").textContent = "";
    } catch {
      $("firmMsg").textContent = "Failed to load";
    }
  },

  populate(f) {
    $("firmName").value = f?.firmName || "";
    $("firmOwner").value = f?.ownerName || "";
    $("firmAddr1").value = f?.addressLine1 || "";
    $("firmAddr2").value = f?.addressLine2 || "";
    $("firmCity").value = f?.city || "";
    $("firmState").value = f?.state || "";
    $("firmPincode").value = f?.pincode || "";
    $("firmPhone").value = f?.phone || "";
    $("firmEmail").value = f?.email || "";
    $("firmGstin").value = f?.gstin || "";
    $("firmBankName").value = f?.bankName || "";
    $("firmBankAccount").value = f?.bankAccount || "";
    $("firmBankIfsc").value = f?.bankIfsc || "";
    $("firmFooter").value = f?.footerNote || "";
  },

  buildDataUrl(raw) {
    if (!raw) return null;
    if (raw.startsWith("data:image")) return raw;
    return `data:image/jpeg;base64,${raw}`;
  },

  showPreview(dataUrl) {
    const box = $("logoPreviewBox");
    box.innerHTML = "";

    if (!dataUrl) {
      $("logoInfo").textContent = "";
      box.innerHTML = `<span class="small muted">No logo</span>`;
      return;
    }

    const img = new Image();
    img.src = dataUrl;
    img.style.maxWidth = "100%";
    img.style.maxHeight = "100%";
    img.style.objectFit = "contain";

    img.onload = () =>
      $("logoInfo").textContent = `Size: ${img.naturalWidth}×${img.naturalHeight}px`;

    box.appendChild(img);
  },

  async handleFile(file) {
    if (!file.type.startsWith("image/")) {
      alert("Only images allowed");
      return;
    }

    const dataUrl = await this.fileToDataUrl(file);

    if (file.type === "image/svg+xml") {
      this.current.logoBase64 = dataUrl.replace(/^data:image\/svg\+xml;base64,/, "");
      this.showPreview(dataUrl);
      return;
    }

    const img = await this.loadImage(dataUrl);

    const resized = this.resizeImage(img, this.maxWidth, this.maxHeight);
    const raw = resized.replace(/^data:image\/jpeg;base64,/, "");

    this.current.logoBase64 = raw;
    this.showPreview(resized);
  },

  async saveLogoOnly() {
    try {
      const firmId = getCurrentFirmId();
      if (!firmId) {
        $("firmMsg").textContent = "No firm selected. Please login again.";
        return;
      }

      $("firmMsg").textContent = "Saving logo...";

      const result = await apiPut(`/api/firm?firmId=${encodeURIComponent(firmId)}`, {
        logoBase64: this.current.logoBase64
      });

      this.current.logoBase64 = result.logoBase64;
      this.showPreview(this.buildDataUrl(result.logoBase64));

      $("firmMsg").textContent = "Logo updated";
    } catch (err) {
      console.error(err);
      alert("Logo update failed");
      $("firmMsg").textContent = "Save failed";
    }
  },

  async fileToDataUrl(file) {
    return new Promise((resolve, reject) => {
      const r = new FileReader();
      r.onload = () => resolve(r.result);
      r.onerror = reject;
      r.readAsDataURL(file);
    });
  },

  loadImage(src) {
    return new Promise((resolve, reject) => {
      const img = new Image();
      img.onload = () => resolve(img);
      img.onerror = reject;
      img.src = src;
    });
  },

  resizeImage(img, maxW, maxH) {
    const ratio = Math.min(maxW / img.width, maxH / img.height, 1);
    const w = img.width * ratio;
    const h = img.height * ratio;

    const canvas = document.createElement("canvas");
    canvas.width = w;
    canvas.height = h;

    const ctx = canvas.getContext("2d");
    ctx.fillStyle = "white";
    ctx.fillRect(0, 0, w, h);
    ctx.drawImage(img, 0, 0, w, h);

    return canvas.toDataURL("image/jpeg", 0.85);
  },

  async save() {
    try {
      const firmId = getCurrentFirmId();
      if (!firmId) {
        $("firmMsg").textContent = "No firm selected. Please login again.";
        return;
      }

      $("firmMsg").textContent = "Saving...";

      const payload = {
        firmName: $("firmName").value.trim() || null,
        ownerName: $("firmOwner").value.trim() || null,
        addressLine1: $("firmAddr1").value.trim() || null,
        addressLine2: $("firmAddr2").value.trim() || null,
        city: $("firmCity").value.trim() || null,
        state: $("firmState").value.trim() || null,
        pincode: $("firmPincode").value.trim() || null,
        phone: $("firmPhone").value.trim() || null,
        email: $("firmEmail").value.trim() || null,
        gstin: $("firmGstin").value.trim() || null,
        bankName: $("firmBankName").value.trim() || null,
        bankAccount: $("firmBankAccount").value.trim() || null,
        bankIfsc: $("firmBankIfsc").value.trim() || null,
        footerNote: $("firmFooter").value.trim() || null,
        logoBase64: this.current.logoBase64 ?? null
      };

      const updated = await apiPut(`/api/firm?firmId=${encodeURIComponent(firmId)}`, payload);

      this.current = updated;
      this.populate(updated);
      this.showPreview(this.buildDataUrl(updated.logoBase64));

      $("firmMsg").textContent = "Saved successfully";
    } catch (err) {
      $("firmMsg").textContent = "Save failed";
      alert("Save failed");
    }
  }
};
