// js/ui/firm-profile-screen.js
import { apiGet, apiPut } from "../api.js";
import { $ } from "../utils.js";

export const firmProfileScreen = {

  current: null,

  // Recommended logo limits
  maxWidth: 600,
  maxHeight: 200,
  minWidth: 80,
  minHeight: 30,
  maxFileSizeBytes: 350 * 1024, // after resize expected small

  render() {
    return `
      <div class="card">
        <h2>Firm Profile</h2>

        <div id="firmMsg" class="small muted" style="margin-bottom:8px;"></div>

        <div style="display:grid;grid-template-columns:1fr 320px;gap:20px;align-items:start;">

          <!-- FORM -->
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
              <div>
                <label>City</label>
                <input id="firmCity" />
              </div>
              <div>
                <label>State</label>
                <input id="firmState" />
              </div>
            </div>

            <div style="display:grid;grid-template-columns:1fr 1fr;gap:8px;">
              <div>
                <label>Pincode</label>
                <input id="firmPincode" />
              </div>
              <div>
                <label>Phone</label>
                <input id="firmPhone" />
              </div>
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

          <!-- LOGO SECTION -->
          <div style="background:var(--card-bg);padding:16px;border-radius:10px;border:1px solid var(--border);">
            <div style="display:flex;justify-content:space-between;">
              <strong>Logo (optional)</strong>
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

  // ---------------- INIT ----------------
  init() {
    $("saveFirmBtn").onclick = () => this.save();
    $("resetFirmBtn").onclick = () => this.load();

    $("uploadLogoBtn").onclick = async () => {
      const f = $("logoFileInput").files?.[0];
      if (!f) return alert("Select a file first.");
      await this.handleFile(f);
    };

    $("removeLogoBtn").onclick = () => {
      if (!confirm("Remove logo?")) return;
      this.current.logoBase64 = null;
      this.showPreview(null);
      $("logoInfo").textContent = "Logo removed (save to apply).";
    };

    $("logoFileInput").onchange = async (e) => {
      const f = e.target.files?.[0];
      if (!f) return;
      const d = await this.fileToDataUrl(f);
      this.showPreview(d);
    };

    this.load();
  },

  // ---------------- LOAD ----------------
  async load() {
    try {
      $("firmMsg").textContent = "Loading...";
      const data = await apiGet("/api/firm");

      this.current = data || {};

      this.populate(data);
      this.showPreview(this.buildDataUrl(data?.logoBase64));

      $("firmMsg").textContent = "";
    } catch (err) {
      console.error(err);
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
    $("logoInfo").textContent = "";
  },

  // ---------------- PREVIEW ----------------
  buildDataUrl(raw) {
    if (!raw) return null;
    return `data:image/jpeg;base64,${raw}`;
  },

  showPreview(dataUrl) {
    const box = $("logoPreviewBox");
    box.innerHTML = "";

    if (!dataUrl) {
      box.innerHTML = `<span class="small muted">No logo</span>`;
      return;
    }

    const img = new Image();
    img.src = dataUrl;
    img.style.maxWidth = "100%";
    img.style.maxHeight = "100%";
    img.style.objectFit = "contain";

    img.onload = () => {
      $("logoInfo").textContent = `Preview ${img.naturalWidth}×${img.naturalHeight}px`;
    };

    box.appendChild(img);
  },

  // ---------------- FILE PROCESSING ----------------
  async handleFile(file) {
    try {
      if (!file.type.startsWith("image/")) {
        alert("Only images allowed.");
        return;
      }

      // READ → LOAD → RESIZE
      const dataUrl = await this.fileToDataUrl(file);

      // For SVG: store raw
      if (file.type === "image/svg+xml") {
        this.current.logoBase64 = dataUrl.replace(/^data:image\/svg\+xml;base64,/, "");
        this.showPreview(dataUrl);
        $("logoInfo").textContent = `SVG uploaded (${Math.round(file.size / 1024)} KB)`;
        return;
      }

      const img = await this.loadImage(dataUrl);

      // Validate minimum size
      if (img.width < this.minWidth || img.height < this.minHeight) {
        alert("Image too small. It will look blurry.");
      }

      const resized = this.resizeImage(img, this.maxWidth, this.maxHeight);

      // store raw base64 only
      this.current.logoBase64 = resized.replace(/^data:image\/jpeg;base64,/, "");

      this.showPreview(resized);

      $("logoInfo").textContent =
        `Processed & saved (${Math.round((resized.length * 3) / 4 / 1024)} KB)`;

    } catch (err) {
      console.error("Logo upload failed", err);
      alert("Failed to upload logo");
    }
  },

  async fileToDataUrl(file) {
    return new Promise((res, rej) => {
      const reader = new FileReader();
      reader.onload = () => res(reader.result);
      reader.onerror = rej;
      reader.readAsDataURL(file);
    });
  },

  loadImage(src) {
    return new Promise((res, rej) => {
      const img = new Image();
      img.onload = () => res(img);
      img.onerror = rej;
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

  // ---------------- SAVE ----------------
  async save() {
    try {
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

      const updated = await apiPut("/api/firm", payload);
      this.current = updated;

      this.populate(updated);
      this.showPreview(this.buildDataUrl(updated.logoBase64));

      $("firmMsg").textContent = "Saved successfully.";

    } catch (err) {
      console.error(err);
      $("firmMsg").textContent = "Save failed";
      alert("Save failed");
    }
  }
};
