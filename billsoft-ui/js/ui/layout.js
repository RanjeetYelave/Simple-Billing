// js/ui/layout.js
import { navigation } from "./navigation.js";

export const layout = {
  render() {
    return `
      <div class="layout">
        <div class="sidebar">
          <div class="brand">
            <div class="brand-badge">afk</div>
            <span>Saivai365</span>
          </div>

          <div class="nav-list" id="sidebarNav"></div>

          <div class="sidebar-footer">v1.0-DEV-LICENCED-RNJT</div>
        </div>

        <div class="main">
          <div id="mainContent"></div>
        </div>

        <!-- Global PDF viewer modal -->
        <div id="pdfModal" class="hidden"
             style="
               position:fixed;inset:0;
               background:rgba(15,23,42,0.85);
               display:flex;
               align-items:center;
               justify-content:center;
               z-index:9999;
             ">
          <div style="
               background:#020617;
               border-radius:14px;
               border:1px solid #1e293b;
               box-shadow:0 18px 40px rgba(0,0,0,0.7);
               width:80%;
               max-width:900px;
               height:80%;
               display:flex;
               flex-direction:column;
             ">
            <div style="
                 padding:10px 14px;
                 border-bottom:1px solid #1f2937;
                 display:flex;
                 justify-content:space-between;
                 align-items:center;
               ">
              <div style="font-size:14px;color:#e5e7eb;">
                <b id="pdfTitle">Invoice PDF</b>
              </div>
              <button class="btn small ghost" id="pdfCloseBtn">✕</button>
            </div>

            <div style="flex:1;overflow:hidden;">
              <embed id="pdfEmbed"
                     type="application/pdf"
                     style="width:100%;height:100%;border:0;border-radius:0 0 12px 12px;" />
            </div>

            <div style="
                 padding:10px 14px;
                 border-top:1px solid #1f2937;
                 display:flex;
                 justify-content:space-between;
                 align-items:center;
               ">
              <span class="small muted">Preview generated invoice PDF.</span>
              <a id="pdfDownloadLink" class="btn primary small" download>
                ⬇ Download
              </a>
            </div>
          </div>
        </div>
      </div>
    `;
  },

  init() {
    navigation.init();
  }
};
