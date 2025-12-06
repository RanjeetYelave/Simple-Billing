// js/ui/auth-screen.js
import { authLogin, authRegister, authDeveloperReset } from "../api.js";
import { $ } from "../utils.js";

export const authScreen = {

  // callback set by main.js after successful login
  onLoginSuccess: null,

  render() {
    return `
      <div class="auth-root" style="min-height:100vh;display:flex;align-items:center;justify-content:center;">
        
        <!-- Theme switch still allowed -->
        <button id="themeToggle"
          class="theme-toggle"
          style="position:fixed;top:12px;right:12px;z-index:10;">
          🌙
        </button>

        <div style="width:100%;max-width:380px;padding:20px;">
          
          <!-- ---------- TABS ---------- -->
          <div style="display:flex;justify-content:space-between;margin-bottom:12px;">
            <button id="tabLoginBtn" class="btn small primary" style="flex:1;margin-right:6px;">Login</button>
            <button id="tabRegisterBtn" class="btn small ghost" style="flex:1;">Create Account</button>
          </div>

          <!-- ---------- LOGIN CARD ---------- -->
          <div id="loginCard" class="card">
            <h2>Login</h2>
            <div id="authLoginMsg" class="small muted" style="min-height:18px;"></div>

            <label>Login ID</label>
            <input id="authLoginId" autocomplete="username" />

            <label>Password</label>
            <input id="authPassword" type="password" autocomplete="current-password" />

            <label>Activation Key (optional)</label>
            <input id="authActivationKey" placeholder="Enter key if any" />

            <div id="authLicenseHint" class="small muted" style="margin-top:6px;">
              You get <strong>30 days free trial</strong> without key.
            </div>

            <button class="btn primary" id="authLoginBtn" style="width:100%;margin-top:14px;">Login</button>

            <button id="authShowForgotBtn"
                    class="btn link small"
                    style="padding:0;margin-top:8px;border:none;background:none;">
              Forgot password?
            </button>
          </div>

          <!-- ---------- REGISTER CARD ---------- -->
          <div id="registerCard" class="card" style="display:none;">
            <h2>Create Account</h2>
            <div id="authRegisterMsg" class="small muted" style="min-height:18px;"></div>

            <label>Login ID</label>
            <input id="authRegLoginId" autocomplete="username" />

            <label>Password</label>
            <input id="authRegPassword" type="password" autocomplete="new-password" />

            <label>Confirm Password</label>
            <input id="authRegPassword2" type="password" />

            <p class="small muted" style="margin-top:6px;">
              Firm details can be configured later in <strong>Firm Profile</strong>.
            </p>

            <button class="btn primary" id="authRegisterBtn" style="width:100%;margin-top:14px;">
              Create Account
            </button>
          </div>

          <!-- ---------- FORGOT PASSWORD ---------- -->
          <div id="authForgotSection"
            class="card"
            style="display:none;margin-top:16px;">
            <h3>Reset Password (Developer Only)</h3>

            <p class="small" style="color:#ff4d4f;font-weight:bold;">
              ❌ DO NOT enter random keys — may lose access to data.  
              Paid support service.
            </p>

            <label>Developer Reset Key</label>
            <input id="authDevKey" type="password" placeholder="Provided by support" />

            <label>New Password</label>
            <input id="authDevNewPassword" type="password" />

            <button id="authDevResetBtn" class="btn small primary" style="width:100%;margin-top:10px;">
              Reset Password
            </button>

            <button id="authContactSupportBtn" class="btn link small"
              style="width:100%;margin-top:6px;padding:0;border:none;background:none;">
              Contact Support
            </button>

            <div id="authDevMsg" class="small muted" style="min-height:18px;margin-top:6px;"></div>
          </div>

        </div>
      </div>
    `;
  },

  init(opts = {}) {
    this.onLoginSuccess = opts.onLoginSuccess || null;

    $("authLoginBtn").onclick = () => this.handleLogin();
    $("authRegisterBtn").onclick = () => this.handleRegister();
    $("authDevResetBtn").onclick = () => this.handleDeveloperReset();
    $("authContactSupportBtn").onclick = () => this.handleContactSupport();

    $("authShowForgotBtn").onclick = () => this.toggleForgot(true);

    $("tabLoginBtn").onclick = () => this.switchTab("login");
    $("tabRegisterBtn").onclick = () => this.switchTab("register");

    $("authPassword").addEventListener("keydown", (e) => {
      if (e.key === "Enter") this.handleLogin();
    });

    this.switchTab("login"); // default tab
  },

  switchTab(tab) {
    $("loginCard").style.display = (tab === "login" ? "block" : "none");
    $("registerCard").style.display = (tab === "register" ? "block" : "none");
    $("authForgotSection").style.display = "none"; // hide forgot section switch

    $("tabLoginBtn").classList.toggle("primary", tab === "login");
    $("tabRegisterBtn").classList.toggle("primary", tab === "register");

    $("tabLoginBtn").classList.toggle("ghost", tab !== "login");
    $("tabRegisterBtn").classList.toggle("ghost", tab !== "register");
  },

  toggleForgot(show) {
    $("authForgotSection").style.display = show ? "block" : "none";
  },

  async handleLogin() {
    const loginId = $("authLoginId").value.trim();
    const password = $("authPassword").value;
    const activationKey = $("authActivationKey").value.trim() || null;

    const msg = $("authLoginMsg");
    const licenseHint = $("authLicenseHint");
    msg.textContent = "Checking...";

    try {
      const res = await authLogin(loginId, password, activationKey);

      if (!res.success) {
        msg.textContent = res.message || "Login failed";
        if (res.licenseStatus) licenseHint.textContent = "License: " + res.licenseStatus;
        if (res.showForgotPassword) this.toggleForgot(true);
        return;
      }

      msg.textContent = "Success!";

      localStorage.setItem("firmId", String(res.firmId));
      localStorage.setItem("firmName", res.firmName || "");
      localStorage.setItem("licenseLevel", res.licenseLevel || "");
      localStorage.setItem("licenseStatus", res.licenseStatus || "");

      this.onLoginSuccess && this.onLoginSuccess(res);

    } catch (err) {
      console.error(err);
      msg.textContent = "Network / server error";
    }
  },

  async handleRegister() {
    const loginId = $("authRegLoginId").value.trim();
    const p1 = $("authRegPassword").value;
    const p2 = $("authRegPassword2").value;

    const msg = $("authRegisterMsg");

    if (!loginId || !p1) return msg.textContent = "All fields required";
    if (p1.length < 6) return msg.textContent = "Weak password";
    if (p1 !== p2) return msg.textContent = "Passwords do not match";

    msg.textContent = "Creating...";

    try {
      const res = await authRegister(loginId, p1);

      if (!res.success) {
        msg.textContent = res.message;
        return;
      }

      msg.textContent = "Created! You can login now";
      this.switchTab("login");
      $("authLoginId").value = loginId;

    } catch (err) {
      msg.textContent = "Failed";
    }
  },

  async handleDeveloperReset() {
    const key = $("authDevKey").value.trim();
    const p = $("authDevNewPassword").value;
    const msg = $("authDevMsg");

    if (!key) return msg.textContent = "Key required";
    if (!p || p.length < 6) return msg.textContent = "Weak password";

    msg.textContent = "Resetting...";

    try {
      const res = await authDeveloperReset(null, key, p);

      msg.textContent = res.success ? "Password updated. Login now." : res.message;

    } catch (err) {
      msg.textContent = "Failed";
    }
  },

  handleContactSupport() {
    alert("Paid support required.\nContact: +91-XXXXXXXXXX\n(Replace later)");
  },
};
