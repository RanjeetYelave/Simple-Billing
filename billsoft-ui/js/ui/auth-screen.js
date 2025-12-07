// js/ui/auth-screen.js
import { authLogin, authRegister } from "../api.js";
import { $ } from "../utils.js";

export const authScreen = {

  onLoginSuccess: null,
  onShowResetScreen: null,
  countdownTimer: null,

  render() {
    return `
      <div class="auth-root" style="min-height:100vh;display:flex;align-items:center;justify-content:center;">
        <div style="width:100%;max-width:420px;padding:22px;">

          <!-- BRAND HEADER -->
          <div style="text-align:center;margin-bottom:18px;">
            <div style="font-size:26px;font-weight:700;letter-spacing:0.06em;">
              📄 <span>InvoiceSuite</span>
            </div>
            <div class="small muted">Smart Billing. Offline & Secure.</div>
          </div>

          <!-- TAB HEADER -->
          <div style="display:flex;margin-bottom:14px;">
            <button id="tabLoginBtn" class="btn small primary" style="flex:1;margin-right:6px;">Login</button>
            <button id="tabRegisterBtn" class="btn small ghost" style="flex:1;">Create Account</button>
          </div>

          <!-- SECURITY STATUS BAR -->
          <div id="secBar"
               style="display:none;margin-bottom:10px;padding:6px 8px;border-radius:8px;
                      font-size:12px;font-weight:600;align-items:center;gap:8px;">
            <span id="secIcon">🟢</span>
            <span id="secText"></span>
          </div>

          <!-- LOGIN FORM -->
          <div id="loginCard" class="card">
            <h2 style="margin-top:0;margin-bottom:6px;">🔐 Login</h2>

            <div id="authLoginMsg" class="small muted" style="min-height:18px;margin-bottom:4px;"></div>

            <label>Login ID</label>
            <div class="auth-input-group">
              <span class="icon">📧</span>
              <input id="authLoginId" autocomplete="username" placeholder="you@example.com" />
            </div>

            <label>Password</label>
            <div class="auth-input-group">
              <span class="icon">🔑</span>
              <input id="authPassword" type="password" autocomplete="current-password" placeholder="••••••••" />
            </div>

            <button class="btn" id="authLoginBtn"
                    style="width:100%;margin-top:10px;justify-content:center;">
              Login
            </button>

            <button id="authShowForgotBtn"
                    class="btn ghost small"
                    style="width:100%;margin-top:10px;justify-content:center;">
              ❓ Forgot Password?
            </button>
          </div>

          <!-- REGISTER FORM -->
          <div id="registerCard" class="card" style="display:none;">
            <h2 style="margin-top:0;margin-bottom:6px;">✨ Create Account</h2>

            <div id="authRegisterMsg" class="small muted" style="min-height:18px;margin-bottom:4px;"></div>

            <label>Login ID</label>
            <div class="auth-input-group">
              <span class="icon">📧</span>
              <input id="authRegLoginId" autocomplete="username" placeholder="you@example.com" />
            </div>

            <label style="margin-top:4px;">Password</label>
            <div class="auth-input-group">
              <span class="icon">🔒</span>
              <input id="authRegPassword" type="password" autocomplete="new-password" placeholder="At least 6 characters" />
            </div>

            <label style="margin-top:4px;">Confirm Password</label>
            <div class="auth-input-group">
              <span class="icon">✅</span>
              <input id="authRegPassword2" type="password" placeholder="Re-type password" />
            </div>

            <p class="small muted" style="margin:6px 0 10px;">
               Firm details can be added later in <strong>Firm Profile</strong>.
            </p>

            <button class="btn"
                    id="authRegisterBtn"
                    style="width:100%;margin-top:4px;justify-content:center;">
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

    $("authLoginBtn").onclick = () => this.handleLogin();
    $("authRegisterBtn").onclick = () => this.handleRegister();
    $("authShowForgotBtn").onclick = () => this.goToResetScreen();

    $("authPassword").addEventListener("keydown", (e) => {
      if (e.key === "Enter") this.handleLogin();
    });

    $("tabLoginBtn").onclick = () => this.switchTab("login");
    $("tabRegisterBtn").onclick = () => this.switchTab("register");

    this.switchTab("login");
  },

  switchTab(mode) {
    $("loginCard").style.display = mode === "login" ? "block" : "none";
    $("registerCard").style.display = mode === "register" ? "block" : "none";

    $("tabLoginBtn").classList.toggle("primary", mode === "login");
    $("tabLoginBtn").classList.toggle("ghost", mode !== "login");

    $("tabRegisterBtn").classList.toggle("primary", mode === "register");
    $("tabRegisterBtn").classList.toggle("ghost", mode !== "register");

    $("authLoginMsg").textContent = "";
    $("authRegisterMsg").textContent = "";
    this.setSecurityBar(null); // hide on tab switch
  },

  setSecurityBar(res) {
    const bar = $("secBar");
    const txt = $("secText");
    const icon = $("secIcon");

    if (!res) {
      bar.style.display = "none";
      return;
    }

    const max = res.maxAttempts ?? 7;
    const remaining = res.remainingAttempts ?? max;
    const used = Math.max(0, max - remaining);

    // If nothing used and no lock → hide (removes that weird green dot)
    if (!res.locked && used === 0) {
      bar.style.display = "none";
      return;
    }

    bar.style.display = "flex";

    // Base text
    let msg = res.message || "";
    if (res.locked && res.unlockAt) {
      msg += " ";
      msg += "(Locked temporarily)";
    } else if (used > 0) {
      msg += ` (${remaining} attempts left)`;
    }
    txt.textContent = msg;

    // Visual levels
    let bg = "#1b5e2033";
    let ic = "🟢";

    if (res.locked && res.securityLevel === "TEMP_LOCK") {
      bg = "#ff473a44";
      ic = "⏳";
    } else if (res.securityLevel === "FROZEN") {
      bg = "#7f1d1d";
      ic = "☠";
    } else if (used >= 3 && used < max) {
      // user has started burning attempts → warning
      bg = "#ffa50233";
      ic = "🟠";
    } else if (used > 0) {
      bg = "#eab30833";
      ic = "⚠️";
    }

    bar.style.background = bg;
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

      if (!res.success) {
        msg.textContent = res.message || "Login failed.";
        if (res.locked && res.unlockAt) {
          this.startCountdown(res.unlockAt);
        }
        return;
      }

      // success
      localStorage.setItem("firmId", res.firmId?.toString() || "");
      msg.textContent = "Login successful. Loading...";
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
      msg.textContent = "All fields are required.";
      return;
    }
    if (p1 !== p2) {
      msg.textContent = "Passwords do not match.";
      return;
    }
    if (p1.length < 6) {
      msg.textContent = "Password too short (min 6 characters).";
      return;
    }

    msg.textContent = "Creating…";

    try {
      const res = await authRegister(loginId, p1);
      msg.textContent = res.message || "Done.";
      if (res.success) {
        this.switchTab("login");
        $("authLoginId").value = loginId;
      }
    } catch (e) {
      console.error(e);
      msg.textContent = "Server error.";
    }
  },

  startCountdown(until) {
    clearInterval(this.countdownTimer);

    const target = new Date(until);
    this.countdownTimer = setInterval(() => {
      const diffSec = Math.max(0, Math.floor((target.getTime() - Date.now()) / 1000));
      const barText = diffSec > 0
        ? `Locked — retry in ${diffSec}s`
        : "You can try logging in again now.";

      const secText = $("secText");
      if (secText) secText.textContent = barText;
      if (diffSec <= 0) {
        clearInterval(this.countdownTimer);
      }
    }, 1000);
  },

  goToResetScreen() {
    if (typeof this.onShowResetScreen === "function") {
      this.onShowResetScreen();
    }
  }
};
