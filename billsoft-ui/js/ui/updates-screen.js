// js/ui/updates-screen.js
import { $ } from "../utils.js";

export const updatesScreen = {

    render() {
        return `
        <div class="card">

            <h2>Updates & Connectivity</h2>
            <p class="small muted">Software version details and connectivity status</p>
            <br>

            <!-- Internet Status -->
            <div id="netStatusBox" style="margin-bottom: 20px;">
                <b>Internet Status:</b>
                <span id="netStatusBadge" class="status-badge offline">Checking...</span>
            </div>

            <!-- Versions -->
            <h3>Version Information</h3>
            <div class="info-box">
                <div><b>UI Version:</b> <span id="uiVersion">1.0.0</span></div>
                <div><b>Backend Version:</b> <span id="backendVersion">Unknown (UI-only mode)</span></div>
            </div>

            <br>

            <!-- Check for update -->
            <button class="btn" id="checkUpdateBtn">Check for Updates 🔄</button>

        </div>
        `;
    },

    init() {
        this.bindNetworkStatus();
        this.bindCheckUpdate();
    },

    /* --------------------------------------
       CONNECTIVITY CHECKING (REAL-TIME)
    --------------------------------------- */
    bindNetworkStatus() {
        const badge = $("netStatusBadge");

        const update = () => {
            if (navigator.onLine) {
                badge.textContent = "Online";
                badge.classList.remove("offline");
                badge.classList.add("online");
            } else {
                badge.textContent = "Offline";
                badge.classList.remove("online");
                badge.classList.add("offline");
            }
        };

        update(); // initial
        window.addEventListener("online", update);
        window.addEventListener("offline", update);
    },

    /* --------------------------------------
       CHECK FOR UPDATE BUTTON (DUMMY)
    --------------------------------------- */
    bindCheckUpdate() {
        $("checkUpdateBtn").onclick = () => {
            alert("You are using the latest version.\n(Real update system coming soon)");
        };
    }
};
