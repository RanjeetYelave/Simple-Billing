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
            <div id="resetSecurityBar" style="display:none;">
              <span id="resetSecurityIcon">⚠</span>
              <span id="resetSecurityText"></span>
            </div>

            <!-- INFO PANEL -->
            <div class="xp-panel">
              <div class="xp-panel-header">
               PASSWORD RESET (SUPPORT-ONLY)
              </div>
              <div class="xp-panel-text">
               PLEASE CONTACT SUPPORT NUMBER/EMAIL BELOW TO RESET PASSWORD WITHOUT LOSING DATA
              </div>
            </div>

            <!-- STEP 1: VERIFY DETAILS -->
            <div id="resetStep1Card" class="xp-step-card">
              <div class="xp-step-header">
                <div class="xp-step-title">Step 1 — Verify Support Access</div>
                <div class="xp-step-count">1 / 3</div>
              </div>

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
                Please remember this new password to access your billing data.
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

    $("resetVerifyBtn").onclick      = () => this.handleVerify();
    $("resetBackToLoginBtn").onclick = () => this.backToLogin();

    $("resetDoBtn").onclick          = () => this.handleDoReset();
    $("resetBackToStep1Btn").onclick = () => this.goToStep(1);
    $("resetGoLoginBtn").onclick     = () => this.backToLogin();

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

  showStatus(message) {
    const bar = $("resetSecurityBar");
    const txt = $("resetSecurityText");
    const icon = $("resetSecurityIcon");

    bar.style.display = "block";
    icon.textContent = "⚠";
    txt.textContent = message || "Password can be reset only by customer support.";
  },

  hideStatus() {
    $("resetSecurityBar").style.display = "none";
  },

  isLocked() {
    return this.lockUntil && Date.now() < this.lockUntil;
  },

  startLock() {
    const threeHours = 3 * 60 * 60 * 1000;
    this.lockUntil = Date.now() + threeHours;

    $("resetVerifyBtn").disabled = true;
    $("resetSecureKey").disabled = true;

    this.showStatus("Too many incorrect attempts. Locked for 3 hours.");
    $("resetStep1Msg").textContent = "Locked due to security. Contact support.";
  },

  updateAttemptsUI() {
    const remaining = Math.max(0, this.maxAttempts - this.attempts);
    $("resetAttempts").textContent = `Attempts remaining: ${remaining}`;
  },

  async handleVerify() {
    const loginId = $("resetLoginId").value.trim();
    const key = $("resetSecureKey").value.trim();
    const msg = $("resetStep1Msg");

    if (this.isLocked()) {
      msg.textContent = "Locked for 3 hours. Contact support.";
      this.showStatus();
      return;
    }

    if (!loginId || !key) {
      msg.textContent = "Login ID & Secure Reset Key required.";
      this.showStatus();
      return;
    }

    msg.textContent = "Verifying…";
    this.hideStatus();

    try {
      const res = await authDeveloperValidate(loginId, key);

      if (!res?.success) {
        this.attempts++;
        this.updateAttemptsUI();

        msg.textContent = res?.message || "Invalid login ID or key.";
        this.showStatus();

        if (this.attempts >= this.maxAttempts) this.startLock();
        return;
      }

      this.loginId = loginId;
      this.secureKey = key;
      this.attempts = 0;
      this.lockUntil = null;

      $("resetVerifyBtn").disabled = false;
      $("resetSecureKey").disabled = false;

      this.hideStatus();
      this.updateAttemptsUI();
      this.goToStep(2);

    } catch (e) {
      msg.textContent = "Network/server error.";
      this.showStatus();
    }
  },

  async handleDoReset() {
    const p1 = $("resetNewPassword").value;
    const p2 = $("resetNewPassword2").value;
    const msg = $("resetStep2Msg");

    if (!p1 || !p2) {
      msg.textContent = "Please enter and confirm.";
      return;
    }
    if (p1.length < 6) {
      msg.textContent = "Min. 6 characters required.";
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

      if (!res?.success) {
        msg.textContent = res?.message || "Reset failed.";
        this.showStatus();
        return;
      }

      this.goToStep(3);

    } catch (e) {
      msg.textContent = "Network/server error.";
      this.showStatus();
    }
  },

  backToLogin() {
    this.onBackToLogin?.() || window.location.reload();
  }
};
