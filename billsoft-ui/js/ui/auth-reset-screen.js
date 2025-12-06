// js/ui/auth-reset-screen.js
import { authDeveloperReset } from "../api.js";
import { $ } from "../utils.js";

export const authResetScreen = {

  // state
  step: 1,                 // 1 = verify, 2 = choose new password, 3 = done
  loginId: "",
  secureKey: "",
  attempts: 0,
  onBackToLogin: null,

  render() {
    return `
      <div class="auth-root" style="min-height:100vh;display:flex;align-items:center;justify-content:center;">
        <div style="width:100%;max-width:420px;padding:20px;">
          
          <!-- BRAND HEADER -->
          <div style="text-align:center;margin-bottom:14px;">
            <div style="font-size:26px;font-weight:700;letter-spacing:0.06em;">
              <span>📄</span> <span>InvoiceSuite</span>
            </div>
            <div class="small muted" style="margin-top:2px;">
              Secure Password Reset
            </div>
          </div>

          <!-- SECURITY THERMOMETER / STATUS -->
          <div id="resetSecurityBar"
               style="display:none;margin-bottom:10px;padding:6px 8px;border-radius:6px;
                      font-size:13px;font-weight:600;display:flex;align-items:center;gap:6px;">
            <span id="resetSecurityIcon">🟢</span>
            <span id="resetSecurityText"></span>
          </div>

          <!-- STEP 1: VERIFY DETAILS -->
          <div id="resetStep1Card" class="card">
            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:4px;">
              <h2 style="margin:0;font-size:18px;">🔐 Reset Password</h2>
              <span class="small muted">Step 1 of 2</span>
            </div>

            <p class="small muted" style="margin-bottom:8px;">
              Use this screen only when you have connected with our support team.
            </p>

            <!-- Strong warning -->
            <div style="background:#2f0000;border:1px solid #ff4d4f;padding:8px;border-radius:6px;
                        color:#ffd6d6;font-size:12px;margin-bottom:10px;display:flex;gap:6px;">
              <span>⚠️</span>
              <span>
                <strong>Extreme caution:</strong> Resetting password incorrectly may lead to
                permanent loss of access. Please proceed only under guidance from support.
              </span>
            </div>

            <label>Login ID (used to login)</label>
            <input id="resetLoginId" placeholder="example@company.com" />

            <label style="margin-top:8px;">Secure Reset Key</label>
            <input id="resetSecureKey" type="password" placeholder="Used by support team only" />

            <div id="resetStep1Msg" class="small muted" style="min-height:16px;margin-top:4px;"></div>

            <button id="resetCheckBtn"
                    class="btn primary"
                    style="width:100%;margin-top:10px;">
              ✅ Continue to New Password
            </button>

            <button id="resetBackToLoginBtn"
                    class="btn ghost small"
                    style="width:100%;margin-top:6px;">
              ← Back to Login
            </button>

            <!-- Contact support (dummy) -->
            <button id="resetContactSupportBtn"
                    class="btn small"
                    style="width:100%;margin-top:10px;background:#b91c1c;color:#fff;">
              📞 Contact Support for Password Reset
            </button>
            <div class="small muted" style="margin-top:4px;">
              Password reset can only be performed by the company’s support team.
            </div>
          </div>

          <!-- STEP 2: NEW PASSWORD -->
          <div id="resetStep2Card" class="card" style="display:none;margin-top:12px;">
            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:4px;">
              <h2 style="margin:0;font-size:18px;">🔑 Set New Password</h2>
              <span class="small muted">Step 2 of 2</span>
            </div>

            <div class="small muted" style="margin-bottom:6px;">
              Login ID: <span id="resetLoginIdLabel" style="font-weight:600;"></span>
            </div>

            <p class="small muted" style="margin-bottom:8px;">
              Choose a strong password and store it safely.  
              <strong>If you forget again, your data may become permanently unreachable.</strong>
            </p>

            <label>New Password</label>
            <input id="resetNewPassword" type="password" />

            <label style="margin-top:8px;">Confirm New Password</label>
            <input id="resetNewPassword2" type="password" />

            <div id="resetStep2Msg" class="small muted" style="min-height:16px;margin-top:4px;"></div>

            <button id="resetDoBtn"
                    class="btn primary"
                    style="width:100%;margin-top:10px;">
              💾 Save New Password
            </button>

            <button id="resetBackToStep1Btn"
                    class="btn ghost small"
                    style="width:100%;margin-top:6px;">
              ← Back (change Login ID / Key)
            </button>
          </div>

          <!-- STEP 3: DONE -->
          <div id="resetDoneCard" class="card" style="display:none;margin-top:12px;">
            <h2 style="margin-top:0;">✅ Password Updated</h2>
            <p class="small" style="margin-bottom:8px;">
              Your password has been updated successfully.
            </p>
            <p class="small muted" style="margin-bottom:12px;">
              Please remember this new password.  
              It is the only way to access your firm’s billing data.
            </p>
            <button id="resetGoLoginBtn"
                    class="btn primary"
                    style="width:100%;">
              🔁 Back to Login
            </button>
          </div>

        </div>
      </div>
    `;
  },

  init(opts = {}) {
    this.onBackToLogin = opts.onBackToLogin || null;
    this.step = 1;
    this.attempts = 0;
    this.loginId = "";
    this.secureKey = "";

    // Wire buttons
    $("resetCheckBtn").onclick = () => this.handleCheckDetails();
    $("resetBackToLoginBtn").onclick = () => this.backToLogin();
    $("resetContactSupportBtn").onclick = () => this.handleContactSupport();

    $("resetDoBtn").onclick = () => this.handleDoReset();
    $("resetBackToStep1Btn").onclick = () => this.goToStep(1);
    $("resetGoLoginBtn").onclick = () => this.backToLogin();

    // Enter key on Step 1
    $("resetSecureKey").addEventListener("keydown", (e) => {
      if (e.key === "Enter") this.handleCheckDetails();
    });

    // Enter key on Step 2
    $("resetNewPassword2").addEventListener("keydown", (e) => {
      if (e.key === "Enter") this.handleDoReset();
    });

    // initial state UI
    this.renderStepState();
    this.updateSecurityBar();
  },

  // ---------------- STEP HANDLING ----------------

  goToStep(step) {
    this.step = step;
    this.renderStepState();
  },

  renderStepState() {
    const s1 = $("resetStep1Card");
    const s2 = $("resetStep2Card");
    const s3 = $("resetDoneCard");

    s1.style.display = this.step === 1 ? "block" : "none";
    s2.style.display = this.step === 2 ? "block" : "none";
    s3.style.display = this.step === 3 ? "block" : "none";

    if (this.step === 2) {
      $("resetLoginIdLabel").textContent = this.loginId || "";
      $("resetNewPassword").value = "";
      $("resetNewPassword2").value = "";
      $("resetStep2Msg").textContent = "";
    }

    if (this.step === 1) {
      $("resetStep1Msg").textContent = "";
    }
  },

  // ---------------- SECURITY BAR ----------------

  updateSecurityBar(extraMessage = null) {
    const bar = $("resetSecurityBar");
    const icon = $("resetSecurityIcon");
    const txt = $("resetSecurityText");

    // attempts used for "temperature"; unlimited but visually scary
    let level = "SAFE";
    if (this.attempts >= 6) level = "CRITICAL";
    else if (this.attempts >= 3) level = "DANGER";
    else if (this.attempts >= 1) level = "WARN";

    let bg = "#0f3a0f33";
    let sym = "🟢";
    let baseText = "System safe. Use reset only with support.";

    if (level === "WARN") {
      bg = "#ffa50233";
      sym = "🟠";
      baseText = "Several incorrect attempts detected. Double-check details with support.";
    } else if (level === "DANGER") {
      bg = "#ff6b8133";
      sym = "🔴";
      baseText = "Multiple incorrect attempts. Risk of permanent lock on main login.";
    } else if (level === "CRITICAL") {
      bg = "#7f1d1d";
      sym = "☠";
      baseText = "Extreme risk. Stop guessing and contact support immediately.";
    }

    bar.style.display = "flex";
    bar.style.background = bg;
    icon.textContent = sym;
    txt.textContent = extraMessage ? `${baseText} ${extraMessage}` : baseText;
  },

  bumpAttempts(extraMessage) {
    this.attempts += 1;
    this.updateSecurityBar(extraMessage);
  },

  // ---------------- STEP 1: VERIFY DETAILS ----------------

  handleCheckDetails() {
    const loginId = $("resetLoginId").value.trim();
    const key = $("resetSecureKey").value.trim();
    const msg = $("resetStep1Msg");

    if (!loginId || !key) {
      msg.textContent = "Please enter both Login ID and Secure Reset Key.";
      this.updateSecurityBar("Missing required fields.");
      return;
    }

    // We don't call backend here (to keep backend simple).
    // Visual "validation" step only — actual validity is checked when saving.
    this.loginId = loginId;
    this.secureKey = key;

    msg.textContent = "Details captured. Proceed to set new password.";
    this.goToStep(2);
  },

  // ---------------- STEP 2: DO RESET ----------------

  async handleDoReset() {
    const p1 = $("resetNewPassword").value;
    const p2 = $("resetNewPassword2").value;
    const msg = $("resetStep2Msg");

    if (!p1 || !p2) {
      msg.textContent = "Please enter and confirm the new password.";
      this.updateSecurityBar("Password fields are empty.");
      return;
    }
    if (p1.length < 6) {
      msg.textContent = "Password must be at least 6 characters.";
      this.updateSecurityBar("Chosen password is too weak.");
      return;
    }
    if (p1 !== p2) {
      msg.textContent = "Passwords do not match.";
      this.updateSecurityBar("Passwords did not match.");
      return;
    }

    msg.textContent = "Applying reset…";

    try {
      const res = await authDeveloperReset(this.loginId, this.secureKey, p1);

      if (!res || !res.success) {
        const errMsg = (res && res.message) || "Reset failed. Please verify Login ID and key.";
        msg.textContent = errMsg;
        this.bumpAttempts("Reset failed. Do not keep guessing — talk to support.");
        return;
      }

      // success
      this.step = 3;
      this.renderStepState();
      this.updateSecurityBar("Password updated successfully. System back to safe state.");
    } catch (e) {
      console.error("Reset error", e);
      msg.textContent = "Network/server error during reset.";
      this.bumpAttempts("Network error. Try again later or contact support.");
    }
  },

  // ---------------- NAVIGATION & SUPPORT ----------------

  backToLogin() {
    if (typeof this.onBackToLogin === "function") {
      this.onBackToLogin();
    } else {
      // fallback: simple reload (if no router wired yet)
      window.location.reload();
    }
  },

  handleContactSupport() {
    // Dummy for now – you will later plug in phone/email/WhatsApp.
    alert(
      "Password reset is a paid support service.\n\n" +
      "Please contact the InvoiceSuite support team. " +
      "They will use this screen to safely reset your password for the correct firm.\n\n" +
      "(Contact details will be added here later.)"
    );
  }
};
