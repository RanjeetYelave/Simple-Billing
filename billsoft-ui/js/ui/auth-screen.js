// js/ui/auth-screen.js
import { authLogin, authRegister } from "../api.js";
import { $ } from "../utils.js";

export const authScreen = {

  onLoginSuccess: null,
  onShowResetScreen: null,   // <-- assigned from main.js

  countdownTimer: null,

  render() {
    return `
      <div class="auth-root" style="min-height:100vh;display:flex;align-items:center;justify-content:center;">
        <div style="width:100%;max-width:420px;padding:22px;">

          <!-- BRAND HEADER -->
          <div style="text-align:center;margin-bottom:14px;">
            <div style="font-size:26px;font-weight:700;letter-spacing:0.06em;">
              📄 InvoiceSuite
            </div>
            <div class="small muted">Smart Billing. Simple.</div>
          </div>

          <!-- TAB HEADER -->
          <div style="display:flex;margin-bottom:12px;">
            <button id="tabLoginBtn" class="btn primary small" style="flex:1;margin-right:6px;">Login</button>
            <button id="tabRegisterBtn" class="btn ghost small" style="flex:1;">Create Account</button>
          </div>

          <!-- SECURITY STATUS BAR -->
          <div id="secBar"
               style="display:none;margin-bottom:10px;padding:6px;border-radius:6px;
                      font-size:13px;font-weight:600;display:flex;align-items:center;gap:6px;">
            <span id="secIcon">🟢</span>
            <span id="secText"></span>
          </div>

          <!-- LOGIN FORM -->
          <div id="loginCard" class="card">
            <h2 style="margin-top:0;">🔐 Login</h2>

            <div id="authLoginMsg" class="small muted" style="min-height:18px;"></div>

            <label>Login ID</label>
            <input id="authLoginId" autocomplete="username" />

            <label>Password</label>
            <input id="authPassword" type="password" autocomplete="current-password" />

            <button class="btn primary" id="authLoginBtn" style="width:100%;margin-top:14px;">Login</button>

            <button id="authShowForgotBtn"
                    class="btn link small"
                    style="width:100%;margin-top:10px;padding:0;border:none;background:none;">
              ❓ Forgot password?
            </button>
          </div>

          <!-- REGISTER FORM -->
          <div id="registerCard" class="card" style="display:none;">
            <h2 style="margin-top:0;">✨ Create Account</h2>

            <div id="authRegisterMsg" class="small muted"></div>

            <label>Login ID</label><input id="authRegLoginId" autocomplete="username" />

            <label style="margin-top:8px;">Password</label>
            <input id="authRegPassword" type="password" autocomplete="new-password" />

            <label style="margin-top:8px;">Confirm Password</label>
            <input id="authRegPassword2" type="password" />

            <p class="small muted" style="margin:8px 0;">
               Firm details can be added later in <strong>Firm Profile</strong>
            </p>

            <button class="btn primary"
                    id="authRegisterBtn"
                    style="width:100%;margin-top:12px;">
              Create Account
            </button>
          </div>

        </div>
      </div>
    `;
  },

  init(opts={}) {
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
    $("loginCard").style.display = mode==="login" ? "block" : "none";
    $("registerCard").style.display = mode==="register" ? "block" : "none";

    $("tabLoginBtn").classList.toggle("primary", mode==="login");
    $("tabLoginBtn").classList.toggle("ghost", mode!=="login");

    $("tabRegisterBtn").classList.toggle("primary", mode==="register");
    $("tabRegisterBtn").classList.toggle("ghost", mode!=="register");

    $("authLoginMsg").textContent = "";
    $("authRegisterMsg").textContent = "";
  },

  setSecurityBar(res) {
    const bar = $("secBar");
    const txt = $("secText");
    const icon = $("secIcon");

    if (!res.securityLevel) {
      bar.style.display = "none";
      return;
    }

    bar.style.display = "flex";

    txt.textContent = `${res.message} (${res.remainingAttempts ?? "∞"} attempts left)`;

    let bg = "#1b5e2033", ic = "🟢"; // safe default
    if (res.securityLevel === "MODERATE") { bg="#ffa50233"; ic="🟠"; }
    if (res.securityLevel === "DANGER")   { bg="#ff6b8133"; ic="🔴"; }
    if (res.securityLevel === "TEMP_LOCK"){ bg="#ff473a44"; ic="⏳"; }
    if (res.securityLevel === "FROZEN")   { bg="#7f1d1d"; ic="☠"; }

    icon.textContent = ic;
    bar.style.background = bg;
  },

  async handleLogin() {
    const loginId = $("authLoginId").value.trim();
    const password = $("authPassword").value;
    const msg = $("authLoginMsg");

    if (!loginId || !password) {
      msg.textContent = "Please fill both fields";
      return;
    }

    msg.textContent = "Checking…";

    try {
      const res = await authLogin(loginId, password, null);

      this.setSecurityBar(res);

      if (!res.success) {
        msg.textContent = res.message || "Login failed";
        if (res.locked && res.unlockAt) {
          this.startCountdown(res.unlockAt);
        }
        return;
      }

      // success
      localStorage.setItem("firmId", res.firmId?.toString() || "");
      this.onLoginSuccess && this.onLoginSuccess(res);

    } catch (e) {
      console.error(e);
      msg.textContent = "Network/Server error";
    }
  },

  async handleRegister() {
    const loginId = $("authRegLoginId").value.trim();
    const p1 = $("authRegPassword").value;
    const p2 = $("authRegPassword2").value;
    const msg = $("authRegisterMsg");

    if (!loginId || !p1 || !p2) return msg.textContent = "All fields required";
    if (p1 !== p2) return msg.textContent = "Passwords do not match";
    if (p1.length < 6) return msg.textContent = "Password too short";

    msg.textContent = "Creating…";

    try {
      const res = await authRegister(loginId, p1);
      msg.textContent = res.message;
      if (res.success) {
        this.switchTab("login");
        $("authLoginId").value = loginId;
      }
    } catch {
      msg.textContent = "Server error";
    }
  },

  startCountdown(until) {
    clearInterval(this.countdownTimer);

    this.countdownTimer = setInterval(() => {
      const diffSec = Math.max(0, Math.floor((new Date(until) - Date.now()) / 1000));
      $("secText").textContent = `Locked — retry in ${diffSec}s`;
      if (diffSec <= 0) clearInterval(this.countdownTimer);
    }, 1000);
  },

  goToResetScreen() {
    if (typeof this.onShowResetScreen === "function") {
      this.onShowResetScreen();
    }
  }
};
