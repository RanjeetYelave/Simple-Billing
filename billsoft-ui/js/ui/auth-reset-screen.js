// js/ui/auth-reset-screen.js
import { authDeveloperReset, authDeveloperValidate } from "../api.js";
import { $ } from "../utils.js";

export const authResetScreen = {

  step: 1,                 // 1 = verify, 2 = new password, 3 = done
  loginId: "",
  secureKey: "",
  attempts: 0,
  unlockAt: null,
  countdownTimer: null,
  onBackToLogin: null,

  render() {
    return `
      <div class="xp-reset-root">
        <div class="xp-window">

          <!-- TITLE BAR -->
          <div class="xp-title-bar">
            <div class="xp-title-left">
              <div class="xp-title-icon">🛡️</div>
              <div>
                <div class="xp-title-text">InvoiceSuite – Support Password Maintenance</div>
                <div class="xp-title-meta">Restricted console · For authorised technician use only</div>
              </div>
            </div>
          </div>

          <!-- MENU / BREADCRUMB -->
          <div class="xp-menu-bar">
            System Tools &gt; Maintenance &gt; Password Recovery
          </div>

          <!-- BODY -->
          <div class="xp-body">

            <!-- SECURITY BAR -->
            <div id="resetSecurityBar" style="display:none;align-items:center;">
              <span id="resetSecurityIcon">🟢</span>
              <span id="resetSecurityText"></span>
            </div>

            <!-- INFO PANEL -->
            <div class="xp-panel">
              <div class="xp-panel-header">Support-only recovery console</div>
              <div class="xp-panel-text">
                This tool is designed for trained support personnel to recover access to a billing firm.
                The Login ID and Secure Reset Key must be read out by support.
                End users should not attempt to guess or reuse any keys here.
              </div>
            </div>

            <!-- STEP 1: VERIFY -->
            <div id="resetStep1Card" class="xp-step-card">
              <div class="xp-step-header">
                <div class="xp-step-title">Step 1 – Verify support access</div>
                <div class="xp-step-count">Step 1 of 3</div>
              </div>

              <p class="xp-panel-text" style="margin-bottom:6px;">
                Enter the exact <strong>Login ID</strong> and <strong>Secure Reset Key</strong> given by support.
                The system will only continue if both are valid.
              </p>

              <label class="xp-label">Login ID (used to login)</label>
              <div class="auth-input-group">
                <span class="icon">👤</span>
                <input id="resetLoginId" placeholder="example@company.com" />
              </div>

              <label class="xp-label">Secure Reset Key (from support)</label>
              <div class="auth-input-group">
                <span class="icon">🗝️</span>
                <input id="resetSecureKey" type="password" placeholder="Do not guess this value" />
              </div>

              <div id="resetStep1Msg" style="min-height:14px;margin-top:2px;"></div>

              <div class="xp-btn-row">
                <button id="resetCheckBtn" class="xp-btn primary">
                  Verify access
                </button>
                <button id="resetBackToLoginBtn" class="xp-btn">
                  &lt; Back to login
                </button>
              </div>

              <div class="xp-footer-note">
                If you do not have a Secure Reset Key, close this window and contact your InvoiceSuite support partner.
              </div>
            </div>

            <!-- STEP 2: NEW PASSWORD -->
            <div id="resetStep2Card" class="xp-step-card" style="display:none;">
              <div class="xp-step-header">
                <div class="xp-step-title">Step 2 – Set new password</div>
                <div class="xp-step-count">Step 2 of 3</div>
              </div>

              <p class="xp-panel-text" style="margin-bottom:6px;">
                The new password will fully replace the existing login password for:
                <strong id="resetLoginIdLabel"></strong>
              </p>

              <label class="xp-label">New password</label>
              <div class="auth-input-group">
                <span class="icon">🔒</span>
                <input id="resetNewPassword" type="password" />
              </div>

              <label class="xp-label">Confirm new password</label>
              <div class="auth-input-group">
                <span class="icon">✅</span>
                <input id="resetNewPassword2" type="password" />
              </div>

              <div id="resetStep2Msg" style="min-height:14px;margin-top:2px;"></div>

              <div class="xp-btn-row">
                <button id="resetDoBtn" class="xp-btn primary">
                  Apply password change
                </button>
                <button id="resetBackToStep1Btn" class="xp-btn">
                  &lt; Back (change Login ID / key)
                </button>
              </div>
            </div>

            <!-- STEP 3: DONE -->
            <div id="resetDoneCard" class="xp-step-card" style="display:none;">
              <div class="xp-step-header">
                <div class="xp-step-title">Step 3 – Completed</div>
                <div class="xp-step-count">Step 3 of 3</div>
              </div>

              <p class="xp-done-text-main">
                Password has been updated successfully for the selected firm.
              </p>
              <p class="xp-done-text-sub">
                Please inform the user of the new password over a secure channel. This console can now be closed.
              </p>

              <div class="xp-btn-row">
                <button id="resetGoLoginBtn" class="xp-btn primary">
                  Return to login screen
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
    this.attempts = 0;
    this.loginId = "";
    this.secureKey = "";
    this.unlockAt = null;

    // wire buttons
    $("resetCheckBtn").onclick = () => this.handleCheckDetails();
    $("resetBackToLoginBtn").onclick = () => this.backToLogin();

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

  updateSecurityBar(extraMessage = null, opts = {}) {
    const bar = $("resetSecurityBar");
    const icon = $("resetSecurityIcon");
    const txt = $("resetSecurityText");

    const locked = opts.locked || false;
    const attempts = this.attempts || 0;

    if (!locked && attempts === 0 && !extraMessage) {
      bar.style.display = "none";
      return;
    }

    let sym = "🟢";
    let text = "Console available. Use only with support guidance.";

    if (locked) {
      sym = "⛔";
      text = "Reset console is temporarily locked due to repeated incorrect keys.";
    } else if (attempts >= 7) {
      sym = "⚠️";
      text = "Multiple incorrect keys entered. Stop guessing and confirm details with support.";
    } else if (attempts >= 3) {
      sym = "⚠️";
      text = "Several incorrect attempts detected. Double-check values with support.";
    }

    if (extraMessage) {
      text += " " + extraMessage;
    }

    bar.style.display = "flex";
    icon.textContent = sym;
    txt.textContent = text;
  },

  bumpAttempts(extraMessage) {
    this.attempts += 1;
    this.updateSecurityBar(extraMessage);
  },

  // ---------------- STEP 1: VERIFY DETAILS ----------------

  async handleCheckDetails() {
    const loginId = $("resetLoginId").value.trim();
    const key = $("resetSecureKey").value.trim();
    const msg = $("resetStep1Msg");

    if (!loginId || !key) {
      msg.textContent = "Login ID and Secure Reset Key are both required.";
      this.updateSecurityBar("Missing mandatory fields.");
      return;
    }

    msg.textContent = "Verifying access with server…";

    try {
      const res = await authDeveloperValidate(loginId, key);

      if (res.locked) {
        this.unlockAt = res.unlockAt || null;
        msg.textContent = res.message || "Support reset is temporarily locked.";
        this.updateSecurityBar("Please wait for the lock to clear, or contact support.", { locked: true });
        this.startCountdown();
        return;
      }

      if (!res.valid) {
        msg.textContent = res.message || "Validation failed. Please confirm values with support.";
        this.bumpAttempts("Reset validation failed. Do not guess the key.");
        return;
      }

      // success → store state & go to step 2
      this.loginId = loginId;
      this.secureKey = key;
      this.attempts = 0;
      this.unlockAt = null;

      msg.textContent = "Access verified. You can now set a new password.";
      this.updateSecurityBar("Support identity confirmed. Proceed carefully.");
      this.goToStep(2);

    } catch (e) {
      console.error("Reset validation error", e);
      msg.textContent = "Network/server error during validation.";
      this.bumpAttempts("Network error. Try again later or through support.");
    }
  },

  // ---------------- STEP 2: DO RESET ----------------

  async handleDoReset() {
    const p1 = $("resetNewPassword").value;
    const p2 = $("resetNewPassword2").value;
    const msg = $("resetStep2Msg");

    if (!p1 || !p2) {
      msg.textContent = "Both password fields are required.";
      this.updateSecurityBar("Password fields cannot be empty.");
      return;
    }
    if (p1.length < 6) {
      msg.textContent = "Password must be at least 6 characters.";
      this.updateSecurityBar("Chosen password is too short.");
      return;
    }
    if (p1 !== p2) {
      msg.textContent = "Passwords do not match.";
      this.updateSecurityBar("Passwords did not match.");
      return;
    }

    msg.textContent = "Applying password reset…";

    try {
      const res = await authDeveloperReset(this.loginId, this.secureKey, p1);

      if (!res || !res.success) {
        const errMsg = (res && res.message) || "Reset failed. Please verify details with support.";
        msg.textContent = errMsg;
        this.bumpAttempts("Password change failed. Confirm values with support.");
        return;
      }

      // success
      this.step = 3;
      this.renderStepState();
      this.attempts = 0;
      this.unlockAt = null;
      this.updateSecurityBar("Password updated successfully. Console is in safe state.");

    } catch (e) {
      console.error("Reset error", e);
      msg.textContent = "Network/server error during reset.";
      this.bumpAttempts("Network error. Try again later via support.");
    }
  },

  // ---------------- COUNTDOWN FOR RESET LOCK ----------------

  startCountdown() {
    if (!this.unlockAt) return;
    clearInterval(this.countdownTimer);

    const target = new Date(this.unlockAt);
    this.countdownTimer = setInterval(() => {
      const diffSec = Math.max(0, Math.floor((target.getTime() - Date.now()) / 1000));
      const mins = Math.floor(diffSec / 60);
      const secs = diffSec % 60;

      const txt = $("resetSecurityText");
      if (txt) {
        txt.textContent = `Reset console is locked. Try again in ${mins}m ${secs}s, or contact support.`;
      }

      if (diffSec <= 0) {
        clearInterval(this.countdownTimer);
        this.unlockAt = null;
        this.attempts = 0;
        this.updateSecurityBar("Lock cleared. You may verify again with correct details.");
      }
    }, 1000);
  },

  // ---------------- NAVIGATION ----------------

  backToLogin() {
    if (typeof this.onBackToLogin === "function") {
      this.onBackToLogin();
    } else {
      window.location.reload();
    }
  }
};
