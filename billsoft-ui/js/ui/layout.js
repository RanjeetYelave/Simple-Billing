// js/ui/layout.js
import { navigation } from "./navigation.js";

export const layout = {
  render() {
    return `
      <div class="layout">
        <div class="sidebar">
          <div class="brand">
            <div class="brand-badge">B</div>
            <span>BillSoft</span>
          </div>

          <div class="nav-list" id="sidebarNav"></div>

          <div class="sidebar-footer">v1.0</div>
        </div>

        <div class="main">
          <div id="mainContent"></div>
        </div>
      </div>
    `;
  },

  init() {
    navigation.init();
  }
};
