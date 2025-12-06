// js/ui/auth-screen.js
import { authLogin, authRegister, authDeveloperReset } from "../api.js";
import { $ } from "../utils.js";

export const authScreen = {

  onLoginSuccess: null,
  resetLoginId: "",
  resetKey: "",
  lockCountdownTimer: null,

  render() {
    return `
      <div style="min-height:100vh;display:flex;align-items:center;justify-content:center;">
        <div style="width:100%;max-width:420px;padding:24px 18px 32px;">

          <!-- Branding -->
          <div style="text-align:center;margin-bottom:10px;">
            <div style="font-size:28px;font-weight:700;">
              <span style="margin-right:6px;">📄</span>
              <span>InvoiceSuite</span>
            </div>
          </div>

          <!-- Tabs -->
          <div style="display:flex;border-radius:999px;background:var(--subtle-bg,#e9edf5);padding:3px;margin-bottom:14px;">
            <button id="tabLoginBtn"
              class="btn small primary"
              style="flex:1;border-radius:999px;">Login</button>
            <button id="tabRegisterBtn"
              class="btn small ghost"
              style="flex:1;border-radius:999px;">Create Account</button>
          </div>

          <!-- ========== LOGIN CARD ========== -->
          <div id="loginCard" class="card">
            <div style="display:flex;align-items:center;gap:8px;margin-bottom:4px;">
              <span style="font-size:20px;">🔐</span>
              <h2 style="margin:0;">Login</h2>
            </div>

            <div id="authLoginMsg" class="small muted" style="min-height:18px;"></div>

            <label>Login ID</label>
            <input id="authLoginId" autocomplete="username" />

            <label>Password</label>
            <input id="authPassword" type="password" autocomplete="current-password" />

            <label>Activation Key (optional)</label>
            <input id="authActivationKey" placeholder="Enter activation key if you have one" />

            <div id="authLicenseHint" class="small muted" style="margin-top:4px;">
              Without key you get <strong>30 days free trial</strong>. After that, a valid activation key is required.
            </div>

            <!-- Attempts bar -->
            <div style="margin-top:10px;">
              <div id="attemptsText" class="small muted">
                You have 7 attempts before permanent lock.
              </div>
              <div id="attemptsBarOuter"
                   style="margin-top:4px;height:6px;border-radius:999px;background:#e5e7eb;overflow:hidden;">
                <div id="attemptsBarInner"
                     style="height:100%;width:0%;background:#22c55e;transition:width .25s ease, background .25s ease;"></div>
              </div>
            </div>

            <!-- Security message (e.g. locked) -->
            <div id="authSecurityMsg"
                 class="small"
                 style="margin-top:6px;min-height:18px;"></div>

            <button class="btn primary" id="authLoginBtn"
                    style="width:100%;margin-top:14px;">Login</button>

            <button id="authShowForgotBtn"
              class="btn link small"
              style="width:100%;margin-top:8px;padding:0;border:none;background:none;">
              Forgot password?
            </button>
          </div>

          <!-- ========== RESET PASSWORD CARD (2-step) ========== -->
          <div id="resetCard" class="card" style="display:none;margin-top:14px;">
            <div style="display:flex;align-items:center;gap:8px;margin-bottom:4px;">
              <span style="font-size:20px;">🛠️</span>
              <h2 style="margin:0;">Reset Password</h2>
            </div>

            <!-- Strong warning -->
            <div style="margin:6px 0 10px;
                        padding:8px 10px;
                        border-radius:8px;
                        background:#fee2e2;
                        color:#b91c1c;
                        font-size:12px;
                        display:flex;gap:8px;align-items:flex-start;">
              <span style="font-size:16px;margin-top:2px;">⚠️</span>
              <span>
                <strong>Important:</strong> Do <u>not</u> try random details here.
                Incorrect information may <strong>permanently lock</strong> your account.
                Password reset is done only by company support staff.
              </span>
            </div>

            <!-- Step 1 -->
            <div id="resetStep1">
              <button id="resetContactBtn"
                class="btn small"
                style="width:100%;margin-bottom:10px;background:#ef4444;border-color:#ef4444;">
                📞 Contact Support to Reset Password
              </button>

              <p class="small muted" style="margin-bottom:10px;">
                Our support team will help you reset the password.  
                They may ask you for your <strong>Login ID</strong> and use an internal key.
              </p>

              <label>Login ID</label>
              <input id="resetLoginId" placeholder="Same Login ID used on Login screen" />

              <label>Internal KEY (for support use only)</label>
              <input id="resetKey" type="password" placeholder="Entered by company support" />

              <button id="resetContinueBtn"
                class="btn primary small"
                style="width:100%;margin-top:12px;">
                Continue
              </button>
            </div>

            <!-- Step 2 -->
            <div id="resetStep2" style="display:none;">
              <p class="small muted">
                Resetting password for: <strong id="resetSummaryLoginId"></strong>
              </p>

              <label>New Password</label>
              <input id="resetNewPassword" type="password" />

              <label>Confirm New Password</label>
              <input id="resetNewPassword2" type="password" />

              <button id="resetBackBtn"
                class="btn small ghost"
                style="margin-top:10px;width:48%;">⬅ Back</button>

              <button id="resetSubmitBtn"
                class="btn small primary"
                style="margin-top:10px;width:48%;float:right;">Reset Password</button>
              <div style="clear:both;"></div>
            </div>

            <div id="resetMsg" class="small muted" style="margin-top:8px;min-height:18px;"></div>
          </div>

          <!-- ========== REGISTER CARD ========== -->
          <div id="registerCard" class="card" style="display:none;margin-top:14px;">
            <div style="display:flex;align-items:center;gap:8px;margin-bottom:4px;">
              <span style="font-size:20px;">🧾</span>
              <h2 style="margin:0;">Create Account</h2>
            </div>

            <div id="authRegisterMsg" class="small muted" style="min-height:18px;"></div>

            <label>Login ID</label>
            <input id="authRegLoginId" autocomplete="username" />

            <label>Password</label>
            <input id="authRegPassword" type="password" autocomplete="new-password" />

            <label>Confirm Password</label>
            <input id="authRegPassword2" type="password" autocomplete="new-password" />

            <p class="small muted" style="margin-top:6px;">
              Firm name and other details can be configured later in <strong>Firm Profile</strong>.
            </p>

            <button class="btn primary" id="authRegisterBtn"
                    style="width:100%;margin-top:12px;">Register</button>
          </div>
        </div>
      </div>
    `;
  },

  // -------------------------------------------------------------------
  // INIT
  // -------------------------------------------------------------------
  init(opts = {}) {
    this.onLoginSuccess = opts.onLoginSuccess || null;

    // tabs
    $("tabLoginBtn").onclick = () => this.switchTab("login");
    $("tabRegisterBtn").onclick = () => this.switchTab("register");

    // login
    $("authLoginBtn").onclick = () => this.handleLogin();
    $("authPassword").addEventListener("keydown", (e) => {
      if (e.key === "Enter") this.handleLogin();
    });

    // forgot / reset
    $("authShowForgotBtn").onclick = () => this.showResetCard(true);
    $("resetContactBtn").onclick = () => this.handleContactSupport();
    $("resetContinueBtn").onclick = () => this.handleResetStep1();
    $("resetBackBtn").onclick = () => this.handleResetBack();
    $("resetSubmitBtn").onclick = () => this.handleResetSubmit();

    // register
    $("authRegisterBtn").onclick = () => this.handleRegister();

    this.updateAttemptsBar(0, 7); // initial state
    this.switchTab("login");
  },

  // -------------------------------------------------------------------
  // TAB SWITCHING
  // -------------------------------------------------------------------
  switchTab(tab) {
    const loginCard = $("loginCard");
    const registerCard = $("registerCard");
    const resetCard = $("resetCard");

    if (tab === "login") {
      loginCard.style.display = "block";
      resetCard.style.display = "block"; // reset is shown under login
      registerCard.style.display = "none";
    } else {
      loginCard.style.display = "none";
      resetCard.style.display = "none";
      registerCard.style.display = "block";
    }

    $("tabLoginBtn").classList.toggle("primary", tab === "login");
    $("tabLoginBtn").classList.toggle("ghost", tab !== "login");
    $("tabRegisterBtn").classList.toggle("primary", tab === "register");
    $("tabRegisterBtn").classList.toggle("ghost", tab !== "register");

    // when switching to login, default reset step 1
    if (tab === "login") {
      this.showResetCard(false); // start collapsed
    }
  },

  showResetCard(expanded) {
    const card = $("resetCard");
    if (!card) return;
    card.style.display = expanded ? "block" : "none";
    this.showResetStep(1);
    $("resetMsg").textContent = "";
  },

  showResetStep(step) {
    $("resetStep1").style.display = step === 1 ? "block" : "none";
    $("resetStep2").style.display = step === 2 ? "block" : "none";
  },

  // -------------------------------------------------------------------
  // LOGIN
  // -------------------------------------------------------------------
  async handleLogin() {
    const loginId = $("authLoginId").value.trim();
    const password = $("authPassword").value;
    const activationKey = $("authActivationKey").value.trim() || null;
    const msgEl = $("authLoginMsg");
    const secMsg = $("authSecurityMsg");

    if (!loginId || !password) {
      msgEl.textContent = "Login ID and password are required.";
      return;
    }

    msgEl.textContent = "Checking...";
    secMsg.textContent = "";

    try {
      const res = await authLogin(loginId, password, activationKey);

      // attempts bar
      const max = res.maxAttempts || 7;
      const rem = typeof res.remainingAttempts === "number"
        ? res.remainingAttempts
        : max;
      this.updateAttemptsBar(max - rem, max);

      // security text
      this.updateSecurityText(res);

      if (!res.success) {
        msgEl.textContent = res.message || "Login failed.";
        return;
      }

      msgEl.textContent = "Login successful.";

      // store simple session
      localStorage.setItem("firmId", String(res.firmId || ""));
      localStorage.setItem("firmName", res.firmName || "");
      localStorage.setItem("licenseLevel", res.licenseLevel || "");
      localStorage.setItem("licenseStatus", res.licenseStatus || "");

      if (typeof this.onLoginSuccess === "function") {
        this.onLoginSuccess(res);
      }

    } catch (err) {
      console.error("Login error", err);
      msgEl.textContent = "Network / server error.";
    }
  },

  updateAttemptsBar(used, max) {
    const inner = $("attemptsBarInner");
    const text = $("attemptsText");
    if (!inner || !text) return;

    used = Math.max(0, Math.min(max, used));
    const remaining = max - used;
    const pct = (used / max) * 100;

    inner.style.width = `${pct}%`;

    // colour based on danger
    if (used <= 2) {
      inner.style.background = "#22c55e"; // green
    } else if (used <= 4) {
      inner.style.background = "#f97316"; // orange
    } else {
      inner.style.background = "#ef4444"; // red
    }

    text.textContent = `You have ${remaining} attempt${remaining !== 1 ? "s" : ""} remaining before permanent lock.`;
  },

  updateSecurityText(res) {
    const sec = $("authSecurityMsg");
    if (!sec) return;

    if (res.locked) {
      sec.style.color = "#b91c1c";
      if (res.securityLevel === "FROZEN") {
        sec.textContent = "🚫 Account permanently locked. Please contact support to reset password.";
      } else if (res.securityLevel === "LOCK_5") {
        sec.textContent = "⏱️ Too many failed attempts. Locked for 5 minutes.";
      } else if (res.securityLevel === "LOCK_30") {
        sec.textContent = "⏱️ Too many failed attempts. Locked for 30 minutes.";
      } else if (res.securityLevel === "TEMP_LOCK" && res.unlockAt) {
        sec.textContent = "⏱️ Account temporarily locked. Try again in a few minutes.";
      } else {
        sec.textContent = res.lockReason || "Account locked.";
      }
    } else if (res.remainingAttempts != null && res.remainingAttempts < res.maxAttempts) {
      sec.style.color = "#f97316";
      sec.textContent = `⚠️ Invalid login. ${res.remainingAttempts} attempt${res.remainingAttempts !== 1 ? "s" : ""} left.`;
    } else {
      sec.textContent = "";
    }
  },

  // -------------------------------------------------------------------
  // REGISTER
  // -------------------------------------------------------------------
  async handleRegister() {
    const loginId = $("authRegLoginId").value.trim();
    const p1 = $("authRegPassword").value;
    const p2 = $("authRegPassword2").value;
    const msg = $("authRegisterMsg");

    if (!loginId || !p1 || !p2) {
      msg.textContent = "All fields are required.";
      return;
    }
    if (p1.length < 6) {
      msg.textContent = "Password must be at least 6 characters.";
      return;
    }
    if (p1 !== p2) {
      msg.textContent = "Passwords do not match.";
      return;
    }

    msg.textContent = "Creating account...";

    try {
      const res = await authRegister(loginId, p1);
      msg.textContent = res.message || (res.success ? "Account created." : "Failed.");
      if (res.success) {
        // pre-fill login form
        this.switchTab("login");
        $("authLoginId").value = loginId;
        $("authPassword").focus();
      }
    } catch (err) {
      console.error("Register error", err);
      msg.textContent = "Network / server error.";
    }
  },

  // -------------------------------------------------------------------
  // RESET PASSWORD (2-step)
  // -------------------------------------------------------------------
  handleContactSupport() {
    alert(
      "Password reset is a paid support service.\n\n" +
      "Please contact the software provider / company support.\n\n" +
      "(This button is dummy for now – add phone / WhatsApp / email later.)"
    );
  },

  handleResetStep1() {
    const loginId = $("resetLoginId").value.trim();
    const key = $("resetKey").value.trim();
    const msg = $("resetMsg");

    if (!loginId || !key) {
      msg.textContent = "Login ID and KEY are required.";
      msg.style.color = "#b91c1c";
      return;
    }

    this.resetLoginId = loginId;
    this.resetKey = key;
    $("resetSummaryLoginId").textContent = loginId;
    msg.textContent = "";
    msg.style.color = "";

    this.showResetStep(2);
  },

  handleResetBack() {
    this.showResetStep(1);
    $("resetMsg").textContent = "";
  },

  async handleResetSubmit() {
    const p1 = $("resetNewPassword").value;
    const p2 = $("resetNewPassword2").value;
    const msg = $("resetMsg");

    if (!p1 || !p2) {
      msg.textContent = "Please enter password in both fields.";
      msg.style.color = "#b91c1c";
      return;
    }
    if (p1.length < 6) {
      msg.textContent = "Password must be at least 6 characters.";
      msg.style.color = "#b91c1c";
      return;
    }
    if (p1 !== p2) {
      msg.textContent = "Passwords do not match.";
      msg.style.color = "#b91c1c";
      return;
    }

    msg.textContent = "Resetting password...";
    msg.style.color = "";

    try {
      const res = await authDeveloperReset(this.resetLoginId, this.resetKey, p1);

      if (!res.success) {
        msg.textContent = res.message || "Reset failed.";
        msg.style.color = "#b91c1c";
        return;
      }

      msg.textContent = "Password updated. Please login with the new password.";
      msg.style.color = "#16a34a";

      // optionally auto-switch back after a short delay
      setTimeout(() => {
        this.switchTab("login");
        this.showResetCard(false);
      }, 1500);

    } catch (err) {
      console.error("Reset error", err);
      msg.textContent = "Network / server error.";
      msg.style.color = "#b91c1c";
    }
  },
};
