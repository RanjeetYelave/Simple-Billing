// js/ui/auth-reset-screen.js
import { authDeveloperValidate, authDeveloperReset } from "../api.js";
import { $ } from "../utils.js";

export const authResetScreen = {

  // state
  step: 1,                 // 1 = verify, 2 = new password, 3 = done
  loginId: "",
  secureKey: "",
  attempts: 0,
  maxAttempts: 10,
  lockUntil: null,
  onBackToLogin: null,

  render() {
    return `
      <div class="xp-reset-root">
        <div class="xp-window">

          <!-- TITLE BAR -->
          <div class="xp-title-bar">
            <div class="xp-title-left">
              <div class="xp-title-icon">🔐</div>
              <div class="xp-title-text">
                InvoiceSuite – Support Password Maintenance Tool
              </div>
            </div>
            <div class="xp-title-meta">
              Restricted console · For authorised support use only
            </div>
          </div>

          <!-- MENU/BREADCRUMB -->
          <div class="xp-menu-bar">
            System Tools &gt; Maintenance &gt; Password Recovery
          </div>

          <!-- BODY -->
          <div class="xp-body">

            <!-- SECURITY STATUS BAR (only when needed) -->
            <div id="resetSecurityBar">
              <span id="resetSecurityIcon">⚠</span>
              <span id="resetSecurityText"></span>
            </div>

            <!-- INFO PANEL -->
            <div class="xp-panel">
              <div class="xp-panel-header">
               PASSWORD RESET (ONLY CUSTOMER SUPPORT CAN RESET)
              </div>
              <div class="xp-panel-text">
               PLEASE CONTACT SUPPORT NUMBER/EMAIL BELOW TO RESET PASSWORD WITHOUT LOOSING DATA
              </div>
            </div>

            <!-- STEP 1: VERIFY DETAILS -->
            <div id="resetStep1Card" class="xp-step-card">
              <div class="xp-step-header">
                <div class="xp-step-title">Step 1 — Verify Support Access</div>
                <div class="xp-step-count">1 / 3</div>
              </div>

              <!-- Support contact info inside step 1 (B2) -->
              <div class="xp-panel-text" style="margin-bottom:6px;">
                Contact InvoiceSuite Support to reset your password:<br>
                📞 +91 8830546789<br>
                ✉ yelaveranjeet@gmail.com
              </div>

              <div class="xp-label">Login ID</div>
              <div class="auth-input-group">
                <span class="icon">👤</span>
                <input id="resetLoginId" type="text" autocomplete="off" />
              </div>

              <div class="xp-label">Secure Reset Key (provided by support)</div>
              <div class="auth-input-group">
                <span class="icon">🔑</span>
                <input id="resetSecureKey" type="password" autocomplete="off" />
              </div>

              <div id="resetStep1Msg"></div>

              <div class="xp-btn-row">
                <button id="resetVerifyBtn" class="xp-btn primary">
                  ✅ Verify Access
                </button>
                <button id="resetBackToLoginBtn" class="xp-btn">
                  ← Back to Login
                </button>
              </div>

              <div id="resetAttempts" class="xp-footer-note">
                Attempts remaining: 10
              </div>
            </div>

            <!-- STEP 2: NEW PASSWORD -->
            <div id="resetStep2Card" class="xp-step-card" style="display:none;">
              <div class="xp-step-header">
                <div class="xp-step-title">Step 2 — Set New Password</div>
                <div class="xp-step-count">2 / 3</div>
              </div>

              <div class="xp-label">New Password</div>
              <div class="auth-input-group">
                <span class="icon">🔒</span>
                <input id="resetNewPassword" type="password" />
              </div>

              <div class="xp-label">Confirm New Password</div>
              <div class="auth-input-group">
                <span class="icon">🔒</span>
                <input id="resetNewPassword2" type="password" />
              </div>

              <div id="resetStep2Msg"></div>

              <div class="xp-btn-row">
                <button id="resetDoBtn" class="xp-btn primary">
                  💾 Save New Password
                </button>
                <button id="resetBackToStep1Btn" class="xp-btn">
                  ← Back
                </button>
              </div>
            </div>

            <!-- STEP 3: DONE -->
            <div id="resetDoneCard" class="xp-step-card" style="display:none;">
              <div class="xp-step-header">
                <div class="xp-step-title">Step 3 — Completed</div>
                <div class="xp-step-count">3 / 3</div>
              </div>

              <div class="xp-done-text-main">
                ✅ Password updated successfully.
              </div>
              <div class="xp-done-text-sub">
                Please remember this new password. It is the only way to access your billing data.
              </div>

              <div class="xp-btn-row">
                <button id="resetGoLoginBtn" class="xp-btn primary">
                  🔁 Back to Login
                </button>
              </div>
            </div>

          </div>
        </div>
      </div>
    `;
  },

  init(opts = {}) {
    this.onBackToLogin = opts.onBackToLogin || null;
    this.step = 1;
    this.loginId = "";
    this.secureKey = "";
    this.attempts = 0;
    this.lockUntil = null;

    // wire buttons
    $("resetVerifyBtn").onclick      = () => this.handleVerify();
    $("resetBackToLoginBtn").onclick = () => this.backToLogin();

    $("resetDoBtn").onclick          = () => this.handleDoReset();
    $("resetBackToStep1Btn").onclick = () => this.goToStep(1);
    $("resetGoLoginBtn").onclick     = () => this.backToLogin();

    // enter key handlers
    $("resetSecureKey").addEventListener("keydown", (e) => {
      if (e.key === "Enter") this.handleVerify();
    });
    $("resetNewPassword2").addEventListener("keydown", (e) => {
      if (e.key === "Enter") this.handleDoReset();
    });

    this.updateAttemptsUI();
    this.renderStepState();
    this.hideStatus();
  },

  // ---- Step switching ----
  goToStep(step) {
    this.step = step;
    this.renderStepState();
  },

  renderStepState() {
    $("resetStep1Card").style.display = this.step === 1 ? "block" : "none";
    $("resetStep2Card").style.display = this.step === 2 ? "block" : "none";
    $("resetDoneCard").style.display  = this.step === 3 ? "block" : "none";

    if (this.step === 1) {
      $("resetStep1Msg").textContent = "";
    } else if (this.step === 2) {
      $("resetStep2Msg").textContent = "";
      $("resetNewPassword").value = "";
      $("resetNewPassword2").value = "";
    }
  },

  // ---- Status bar ----
  showStatus(message) {
    const bar = $("resetSecurityBar");
    const icon = $("resetSecurityIcon");
    const txt = $("resetSecurityText");

    bar.style.display = "block";
    icon.textContent = "⚠";
    txt.textContent = message || "Password can be only reseted by customer support, contact them to avoid data loss";
  },

  hideStatus() {
    const bar = $("resetSecurityBar");
    bar.style.display = "none";
  },

  // ---- Attempts / lock ----
  isLocked() {
    if (!this.lockUntil) return false;
    return Date.now() < this.lockUntil;
  },

  startLock() {
    // 3 hours lock
    const threeHours = 3 * 60 * 60 * 1000;
    this.lockUntil = Date.now() + threeHours;

    this.showStatus("Password can be only reseted by customer support, contact them to avoid data loss");
    $("resetStep1Msg").textContent = "Too many incorrect attempts. Locked for 3 hours. Contact support.";
    $("resetVerifyBtn").disabled = true;
    this.updateAttemptsUI();
  },

  updateAttemptsUI() {
    const remaining = Math.max(0, this.maxAttempts - this.attempts);
    $("resetAttempts").textContent = `Attempts remaining: ${remaining}`;
  },

  // ---- STEP 1: Verify ----
  async handleVerify() {
    const loginId = $("resetLoginId").value.trim();
    const key     = $("resetSecureKey").value.trim();
    const msg     = $("resetStep1Msg");

    if (this.isLocked()) {
      msg.textContent = "Locked for 3 hours. Please contact support.";
      this.showStatus("Password can be only reseted by customer support, contact them to avoid data loss");
      $("resetVerifyBtn").disabled = true;
      return;
    }

    if (!loginId || !key) {
      msg.textContent = "Please enter both Login ID and Secure Reset Key.";
      this.showStatus("Password can be only reseted by customer support, contact them to avoid data loss");
      return;
    }

    msg.textContent = "Verifying…";
    this.hideStatus();

    try {
      const res = await authDeveloperValidate(loginId, key);

      if (!res || !res.success) {
        this.attempts += 1;
        this.updateAttemptsUI();

        msg.textContent = (res && res.message) || "Invalid Login ID or Secure Reset Key.";
        this.showStatus("Password can be only reseted by customer support, contact them to avoid data loss");

        if (this.attempts >= this.maxAttempts) {
          this.startLock();
        }
        return;
      }

      // success
      this.loginId   = loginId;
      this.secureKey = key;
      this.attempts  = 0;
      this.lockUntil = null;
      this.hideStatus();
      this.updateAttemptsUI();
      this.goToStep(2);

    } catch (e) {
      console.error("Reset verify error", e);
      msg.textContent = "Network/server error during verification.";
      this.showStatus("Password can be only reseted by customer support, contact them to avoid data loss");
    }
  },

  // ---- STEP 2: Do reset ----
  async handleDoReset() {
    const p1  = $("resetNewPassword").value;
    const p2  = $("resetNewPassword2").value;
    const msg = $("resetStep2Msg");

    if (!p1 || !p2) {
      msg.textContent = "Please enter and confirm the new password.";
      return;
    }
    if (p1.length < 6) {
      msg.textContent = "New password must be at least 6 characters.";
      return;
    }
    if (p1 !== p2) {
      msg.textContent = "Passwords do not match.";
      return;
    }

    msg.textContent = "Applying reset…";
    this.hideStatus();

    try {
      const res = await authDeveloperReset(this.loginId, this.secureKey, p1);

      if (!res || !res.success) {
        msg.textContent = (res && res.message) || "Reset failed. Contact support.";
        this.showStatus("Password can be only reseted by customer support, contact them to avoid data loss");
        return;
      }

      // success
      this.goToStep(3);

    } catch (e) {
      console.error("Reset error", e);
      msg.textContent = "Network/server error during reset.";
      this.showStatus("Password can be only reseted by customer support, contact them to avoid data loss");
    }
  },

  // ---- Back to login ----
  backToLogin() {
    if (typeof this.onBackToLogin === "function") {
      this.onBackToLogin();
    } else {
      window.location.reload();
    }
  }
};
