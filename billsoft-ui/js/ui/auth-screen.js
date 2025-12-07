// js/ui/auth-screen.js
import { authLogin, authRegister } from "../api.js";
import { $ } from "../utils.js";

export const authScreen = {

  onLoginSuccess: null,
  onShowResetScreen: null,

  countdownTimer: null,

  // licensing state
  activationMode: false,        // is activation box visible?
  activationMandatory: false,   // true when license expired
  pendingLoginId: null,
  pendingPassword: null,

  render() {
    return `
      <div class="auth-root">
        <div class="auth-shell">

          <!-- BRAND HEADER -->
          <div class="auth-brand">
            <div class="auth-brand-icon">📄</div>
            <div class="auth-brand-text">
              <div class="auth-brand-title">InvoiceSuite</div>
              <div class="auth-brand-subtitle small muted">Smart Billing. Simple.</div>
            </div>
          </div>

          <!-- TAB HEADER -->
          <div class="auth-tabs">
            <button id="tabLoginBtn"
                    class="btn small primary auth-tab">
              Login
            </button>
            <button id="tabRegisterBtn"
                    class="btn small ghost auth-tab">
              Create Account
            </button>
          </div>

          <!-- SECURITY STATUS BAR (login lock) -->
          <div id="secBar" class="auth-secbar">
            <span id="secIcon">🟢</span>
            <span id="secText"></span>
          </div>

          <!-- LOGIN FORM -->
          <div id="loginCard" class="card auth-card">
            <h2 class="auth-card-title">
              <span class="auth-card-emoji">🔐</span>
              <span>Login</span>
            </h2>

            <div id="authLoginMsg"
                 class="small muted"
                 style="min-height:18px;"></div>

            <label for="authLoginId">Login ID</label>
            <input id="authLoginId" autocomplete="username" />

            <label for="authPassword">Password</label>
            <input id="authPassword"
                   type="password"
                   autocomplete="current-password" />

            <button class="btn primary auth-main-btn"
                    id="authLoginBtn">
              Login
            </button>

            <!-- Links row -->
            <div class="auth-links-row">
              <button id="authShowForgotBtn"
                      class="btn link small">
                ❓ Forgot password? (support only)
              </button>

              <button id="authToggleActivationBtn"
                      class="btn link small">
                🔑 Have an activation key?
              </button>
            </div>

            <!-- Stored license badge (trial / premium info) -->
            <div id="authLicenseBadge"
                 class="small muted auth-license-badge"
                 style="display:none;">
            </div>

            <!-- Inline activation box (collapsed by default) -->
            <div id="authActivationBox" class="auth-activation-box" style="display:none;">
              <div id="authActivationTitle"
                   class="auth-activation-title small">
                Enter activation key
              </div>

              <div id="authActivationInfo"
                   class="small muted auth-activation-info">
              </div>

              <label class="small" for="authActivationKey">Activation Key</label>
              <input id="authActivationKey" autocomplete="off" />

              <div id="authActivationMsg"
                   class="small muted auth-activation-msg"></div>

              <div class="auth-activation-buttons">
                <button id="authActivationBtn"
                        class="btn small primary">
                  ✅ Activate &amp; Login
                </button>
                <button id="authActivationCancelBtn"
                        class="btn small ghost">
                  ✖ Cancel
                </button>
              </div>
            </div>

          </div>

          <!-- REGISTER FORM -->
          <div id="registerCard" class="card auth-card" style="display:none;">
            <h2 class="auth-card-title">
              <span class="auth-card-emoji">✨</span>
              <span>Create Account</span>
            </h2>

            <div id="authRegisterMsg" class="small muted"></div>

            <label for="authRegLoginId">Login ID</label>
            <input id="authRegLoginId" autocomplete="username" />

            <label for="authRegPassword" style="margin-top:8px;">Password</label>
            <input id="authRegPassword"
                   type="password"
                   autocomplete="new-password" />

            <label for="authRegPassword2" style="margin-top:8px;">Confirm Password</label>
            <input id="authRegPassword2" type="password" />

            <p class="small muted" style="margin:8px 0;">
               Firm details can be added later in <strong>Firm Profile</strong>.
            </p>

            <button class="btn primary auth-main-btn"
                    id="authRegisterBtn">
              Create Account
            </button>
          </div>

        </div>
      </div>
    `;
  },

  init(opts = {}) {
    this.onLoginSuccess = opts.onLoginSuccess || null;
    this.onShowResetScreen = opts.onShowResetScreen || null;

    this.activationMode = false;
    this.activationMandatory = false;
    this.pendingLoginId = null;
    this.pendingPassword = null;

    // Login
    $("authLoginBtn").onclick = () => this.handleLogin();
    $("authPassword").addEventListener("keydown", (e) => {
      if (e.key === "Enter") this.handleLogin();
    });

    // Register
    $("authRegisterBtn").onclick = () => this.handleRegister();

    // Forgot password
    $("authShowForgotBtn").onclick = () => this.goToResetScreen();

    // Tabs
    $("tabLoginBtn").onclick = () => this.switchTab("login");
    $("tabRegisterBtn").onclick = () => this.switchTab("register");

    // Activation UI
    $("authToggleActivationBtn").onclick = () => this.toggleActivationManual();
    $("authActivationBtn").onclick = () => this.handleActivation();
    $("authActivationCancelBtn").onclick = () => this.closeActivationManual();
    $("authActivationKey").addEventListener("keydown", (e) => {
      if (e.key === "Enter") this.handleActivation();
    });

    this.switchTab("login");
    this.applyStoredLicenseBadge();   // show trial/premium info
  },

  switchTab(mode) {
    if (mode === "login") {
      $("loginCard").style.display = "block";
      $("registerCard").style.display = "none";
    } else {
      $("loginCard").style.display = "none";
      $("registerCard").style.display = "block";
    }

    $("tabLoginBtn").classList.toggle("primary", mode === "login");
    $("tabLoginBtn").classList.toggle("ghost", mode !== "login");
    $("tabRegisterBtn").classList.toggle("primary", mode === "register");
    $("tabRegisterBtn").classList.toggle("ghost", mode !== "register");

    $("authLoginMsg").textContent = "";
    $("authRegisterMsg").textContent = "";
  },

  setSecurityBar(res) {
    const bar = $("secBar");
    const txt = $("secText");
    const icon = $("secIcon");

    if (!res || !res.securityLevel) {
      bar.style.display = "none";
      return;
    }

    bar.style.display = "flex";
    const remaining = res.remainingAttempts ?? "∞";
    txt.textContent = `${res.message || ""} (${remaining} attempts left)`;

    let cls = "auth-secbar-safe";
    let ic = "🟢";

    if (res.securityLevel === "MODERATE" || res.securityLevel === "LOCK_5") {
      cls = "auth-secbar-warn";
      ic = "🟠";
    }
    if (res.securityLevel === "DANGER" || res.securityLevel === "LOCK_30") {
      cls = "auth-secbar-danger";
      ic = "🔴";
    }
    if (res.securityLevel === "TEMP_LOCK") {
      cls = "auth-secbar-lock";
      ic = "⏳";
    }
    if (res.securityLevel === "FROZEN") {
      cls = "auth-secbar-frozen";
      ic = "☠";
    }

    bar.className = `auth-secbar ${cls}`;
    icon.textContent = ic;
  },

  async handleLogin() {
    const loginId = $("authLoginId").value.trim();
    const password = $("authPassword").value;
    const msg = $("authLoginMsg");

    if (!loginId || !password) {
      msg.textContent = "Please fill both fields.";
      return;
    }

    msg.textContent = "Checking…";

    try {
      const res = await authLogin(loginId, password, null);

      this.setSecurityBar(res);

      // License expired → force activation
      if (!res.licenseOk && (res.message || "").toLowerCase().includes("license expired")) {
        msg.textContent = res.message || "License expired.";
        this.enterActivationMandatory(res, loginId, password);
        return;
      }

      if (!res.success) {
        msg.textContent = res.message || "Login failed.";
        if (res.locked && res.unlockAt) this.startCountdown(res.unlockAt);
        return;
      }

      // success + license OK → store license info
      this.storeLicenseInfo(res);

      localStorage.setItem("firmId", res.firmId?.toString() || "");
      this.onLoginSuccess && this.onLoginSuccess(res);

    } catch (e) {
      console.error(e);
      msg.textContent = "Network/Server error.";
    }
  },

  async handleRegister() {
    const loginId = $("authRegLoginId").value.trim();
    const p1 = $("authRegPassword").value;
    const p2 = $("authRegPassword2").value;
    const msg = $("authRegisterMsg");

    if (!loginId || !p1 || !p2) {
      msg.textContent = "All fields required.";
      return;
    }
    if (p1 !== p2) {
      msg.textContent = "Passwords do not match.";
      return;
    }
    if (p1.length < 6) {
      msg.textContent = "Password too short.";
      return;
    }

    msg.textContent = "Creating…";

    try {
      const res = await authRegister(loginId, p1);
      msg.textContent = res.message;
      if (res.success) {
        this.switchTab("login");
        $("authLoginId").value = loginId;
      }
    } catch {
      msg.textContent = "Server error.";
    }
  },

  startCountdown(until) {
    clearInterval(this.countdownTimer);

    this.countdownTimer = setInterval(() => {
      const diffSec = Math.max(0,
        Math.floor((new Date(until) - Date.now()) / 1000));
      $("secText").textContent = `Locked — retry in ${diffSec}s`;
      if (diffSec <= 0) clearInterval(this.countdownTimer);
    }, 1000);
  },

  goToResetScreen() {
    if (typeof this.onShowResetScreen === "function") {
      this.onShowResetScreen();
    } else {
      window.location.reload();
    }
  },

  // --------------- ACTIVATION UI HELPERS ---------------

  updateActivationUI() {
    const box = $("authActivationBox");
    const toggle = $("authToggleActivationBtn");
    const loginBtn = $("authLoginBtn");
    const idInput = $("authLoginId");
    const pwInput = $("authPassword");
    const title = $("authActivationTitle");
    const info = $("authActivationInfo");

    box.style.display = this.activationMode ? "block" : "none";
    toggle.style.display = this.activationMandatory ? "none" : "inline-flex";

    const frozen = this.activationMandatory;
    loginBtn.disabled = frozen;
    idInput.disabled = frozen;
    pwInput.disabled = frozen;

    if (!this.activationMode) {
      $("authActivationMsg").textContent = "";
      $("authActivationKey").value = "";
      return;
    }

    if (this.activationMandatory) {
      title.textContent = "License expired — activation required";
      info.textContent =
        "Your trial or license for this device has ended. Enter a valid activation key to continue using InvoiceSuite.";
    } else {
      title.textContent = "Enter activation key (optional)";
      info.textContent =
        "Apply a new license or upgrade to premium. Existing data remains safe.";
    }
  },

  toggleActivationManual() {
    // manual open/close (not expired)
    this.activationMode = !this.activationMode;
    this.activationMandatory = false;
    this.updateActivationUI();
    if (this.activationMode) $("authActivationKey").focus();
  },

  closeActivationManual() {
    if (this.activationMandatory) return; // cannot close when expired
    this.activationMode = false;
    this.updateActivationUI();
  },

  enterActivationMandatory(res, loginId, password) {
    this.activationMode = true;
    this.activationMandatory = true;
    this.pendingLoginId = loginId;
    this.pendingPassword = password;

    const badge = $("authLicenseBadge");
    const exp = res.licenseExpiryAt ? new Date(res.licenseExpiryAt) : null;
    if (exp && !isNaN(exp.getTime())) {
      badge.style.display = "block";
      badge.textContent = `❌ License expired on ${exp.toLocaleDateString()}.`;
    } else {
      badge.style.display = "block";
      badge.textContent = "❌ License expired.";
    }

    this.updateActivationUI();
    $("authActivationMsg").textContent = "";
    $("authActivationKey").value = "";
    $("authActivationKey").focus();
  },

  // --------------- ACTIVATION CALL ---------------

  async handleActivation() {
    const key = $("authActivationKey").value.trim();
    const msg = $("authActivationMsg");

    if (!key) {
      msg.textContent = "Activation key is required.";
      return;
    }

    const loginId = this.pendingLoginId || $("authLoginId").value.trim();
    const password = this.pendingPassword || $("authPassword").value;
    if (!loginId || !password) {
      msg.textContent = "Please enter Login ID and Password first.";
      return;
    }

    msg.textContent = "Validating key…";

    try {
      const res = await authLogin(loginId, password, key);

      // expired + wrong key → licenseOk=false / success=false
      if (!res.success || !res.licenseOk) {
        msg.textContent = res.message || "Activation failed. Please check your key.";
        return;
      }

      // If manual activation and still TRIAL, key was ignored
      if (!this.activationMandatory && (res.licenseLevel === "TRIAL" || res.trial)) {
        msg.textContent =
          "Invalid activation key. Your trial is still active — login without entering a key.";
        return;
      }

      // success + real license updated → store & enter app
      this.storeLicenseInfo(res);

      localStorage.setItem("firmId", res.firmId?.toString() || "");
      this.onLoginSuccess && this.onLoginSuccess(res);

    } catch (e) {
      console.error(e);
      msg.textContent = "Network/Server error while activating.";
    }
  },

  // --------------- LICENSE BADGE STORAGE ---------------

  storeLicenseInfo(res) {
    try {
      if (res.licenseLevel) {
        localStorage.setItem("licenseLevel", res.licenseLevel);
      }
      if (res.licenseExpiryAt) {
        localStorage.setItem("licenseExpiryAt", res.licenseExpiryAt);
      }
    } catch (_) {
      // ignore storage errors
    }
  },

  applyStoredLicenseBadge() {
    let level = null;
    let expiry = null;
    try {
      level = localStorage.getItem("licenseLevel");
      expiry = localStorage.getItem("licenseExpiryAt");
    } catch (_) {}

    const badge = $("authLicenseBadge");

    if (!level || !expiry) {
      badge.style.display = "none";
      return;
    }

    const d = new Date(expiry);
    if (isNaN(d.getTime())) {
      badge.style.display = "none";
      return;
    }

    // normalize to whole days
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const expDay = new Date(d.getFullYear(), d.getMonth(), d.getDate());
    const diffMs = expDay.getTime() - today.getTime();
    const diffDays = Math.ceil(diffMs / (1000 * 60 * 60 * 24));

    let text;

    if (level === "TRIAL") {
      if (diffDays > 0) {
        text = `🎁 Trial active — ${diffDays} day${diffDays !== 1 ? "s" : ""} left (till ${d.toLocaleDateString()})`;
      } else {
        text = `❌ Trial expired on ${d.toLocaleDateString()}.`;
      }
    } else {
      if (diffDays > 0) {
        text = `⭐ Premium license active • valid till ${d.toLocaleDateString()}`;
      } else {
        text = `⭐ Premium license — renewal recommended (expired on ${d.toLocaleDateString()})`;
      }
    }

    badge.textContent = text;
    badge.style.display = "block";
  }
};
