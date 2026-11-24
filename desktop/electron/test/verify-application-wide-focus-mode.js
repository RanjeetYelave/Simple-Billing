/**
 * verify-application-wide-focus-mode.js
 * RupeeCRM — Automated Regression & Verification Suite for Application-Wide Auto-Hide Sidebar
 *
 * Verifies:
 * 1. AutoHideSidebarPolicy: Default enabled for new installs, localStorage persistence ('rupeecrm_auto_hide_sidebar')
 * 2. Application-Wide Support: All modules and workspaces covered without manual toggle toggling
 * 3. Settings Appearance: Section 9 persistent switch card (#settings-autohide-sidebar-toggle)
 * 4. Topbar Cleanliness: Removal of manual #topbar-focus-mode-btn from topbar
 * 5. CSS & Layout Invariants: Zero-width sidebar-container, 100% width main-content (no workspace shift on hover)
 * 6. Extreme Left Edge Hover Trigger: .sidebar-hover-trigger (14px width, z-index 1040, modal/search suppression)
 * 7. Overlay Drawer Behavior: .sidebar.revealed overlays workspace at z-index 1050 with elevated shadow
 * 8. Interaction & Delayed Hide: Scheduled hide timer (350ms), cancel on re-enter, auto-dismiss on nav item click
 * 9. Keyboard Accessibility & Safeguards: Alt+S, Option+S, Alt+F, F10, Escape dismissal, input/cell edit protection
 */

const assert = require('assert');
const fs = require('fs');
const path = require('path');

console.log('═══════════════════════════════════════════════════════════════════');
console.log('RUPEECRM APPLICATION-WIDE AUTO-HIDE SIDEBAR VERIFICATION SUITE');
console.log('═══════════════════════════════════════════════════════════════════\n');

const repoRoot = path.resolve(__dirname, '../../..');
const indexPath = path.join(repoRoot, 'billsoft/src/main/webapp/index.html');
const cssPath = path.join(repoRoot, 'billsoft/src/main/webapp/css/style.css');

assert(fs.existsSync(indexPath), `index.html must exist at ${indexPath}`);
assert(fs.existsSync(cssPath), `style.css must exist at ${cssPath}`);

const indexContent = fs.readFileSync(indexPath, 'utf8');
const cssContent = fs.readFileSync(cssPath, 'utf8');

let totalTests = 0;
let passedTests = 0;

function runTest(name, fn) {
  totalTests++;
  try {
    fn();
    passedTests++;
    console.log(`  ✓ [PASS] ${name}`);
  } catch (err) {
    console.error(`  ✗ [FAIL] ${name}: ${err.message}`);
    throw err;
  }
}

// ─── SUITE 1: AUTO-HIDE SIDEBAR POLICY & PERSISTENCE ───
console.log('--- Suite 1: AutoHideSidebarPolicy & Default State ---');

let AutoHideSidebarPolicy;
let FocusModePolicy;

runTest('1.1: AutoHideSidebarPolicy is exported and defined in index.html', () => {
  assert(indexContent.includes('const AutoHideSidebarPolicy = {'), 'index.html must declare AutoHideSidebarPolicy');
  assert(indexContent.includes('window.AutoHideSidebarPolicy = AutoHideSidebarPolicy;'), 'Must attach to window');
  assert(indexContent.includes('module.exports.AutoHideSidebarPolicy = AutoHideSidebarPolicy;'), 'Must export to module.exports');
  assert(indexContent.includes('const FocusModePolicy = AutoHideSidebarPolicy;'), 'Must provide FocusModePolicy alias');

  const mockLocalStorage = {
    _data: {},
    getItem(k) { return this._data[k] !== undefined ? this._data[k] : null; },
    setItem(k, v) { this._data[k] = String(v); },
    removeItem(k) { delete this._data[k]; },
    clear() { this._data = {}; }
  };
  global.localStorage = mockLocalStorage;
  global.window = {
    localStorage: mockLocalStorage,
    dispatchEvent: () => {}
  };

  const policyMatch = indexContent.match(/const AutoHideSidebarPolicy\s*=\s*\{[\s\S]*?\n\s*\};/);
  assert(policyMatch, 'Must match AutoHideSidebarPolicy definition block');
  const evalPolicy = new Function('localStorage', 'window', `${policyMatch[0]}; return AutoHideSidebarPolicy;`);
  AutoHideSidebarPolicy = evalPolicy(mockLocalStorage, global.window);
  FocusModePolicy = AutoHideSidebarPolicy;
  assert(AutoHideSidebarPolicy && typeof AutoHideSidebarPolicy.getSavedPreference === 'function', 'Must expose getSavedPreference()');
});

runTest('1.2: Enabled by default for new installations (returns true when unconfigured)', () => {
  global.localStorage.clear();
  assert.strictEqual(global.localStorage.getItem('rupeecrm_auto_hide_sidebar'), null);
  const defaultVal = AutoHideSidebarPolicy.getSavedPreference();
  assert.strictEqual(defaultVal, true, 'Default preference MUST be true for new installations');
});

runTest('1.3: Preference persistence across reboots via rupeecrm_auto_hide_sidebar', () => {
  // Test disabling
  AutoHideSidebarPolicy.setSavedPreference(false);
  assert.strictEqual(global.localStorage.getItem('rupeecrm_auto_hide_sidebar'), 'false');
  assert.strictEqual(AutoHideSidebarPolicy.getSavedPreference(), false);

  // Test enabling
  AutoHideSidebarPolicy.setSavedPreference(true);
  assert.strictEqual(global.localStorage.getItem('rupeecrm_auto_hide_sidebar'), 'true');
  assert.strictEqual(AutoHideSidebarPolicy.getSavedPreference(), true);
});

runTest('1.4: Application-wide support across all pages and workspaces', () => {
  const pages = ['dashboard', 'invoices', 'firm', 'statements', 'ledgers', 'orders', 'purchases', 'hr', 'planner', 'settings'];
  for (const p of pages) {
    assert.strictEqual(AutoHideSidebarPolicy.isSupported(p), true, `Page ${p} must be supported by policy`);
  }
});

// ─── SUITE 2: SETTINGS APPEARANCE INTEGRATION ───
console.log('\n--- Suite 2: Settings -> Appearance Integration ---');

runTest('2.1: Section 9 card renders Auto-Hide Sidebar toggle (#settings-autohide-sidebar-toggle)', () => {
  assert(indexContent.includes("id: 'settings-autohide-sidebar'"), 'Settings card must have id settings-autohide-sidebar');
  assert(indexContent.includes("id: 'settings-autohide-sidebar-toggle'"), 'Switch input must have id settings-autohide-sidebar-toggle');
  assert(indexContent.includes('📌 Auto-Hide Sidebar'), 'Must have heading 📌 Auto-Hide Sidebar');
  assert(indexContent.includes('isAutoHideSidebar'), 'Must bind to isAutoHideSidebar state');
  assert(indexContent.includes('handleToggleAutoHideSidebar'), 'Must bind to handleToggleAutoHideSidebar handler');
});

runTest('2.2: Settings listens to and emits billsoft:autohide-sidebar-toggle custom event', () => {
  assert(indexContent.includes("addEventListener('billsoft:autohide-sidebar-toggle'"), 'Must register billsoft:autohide-sidebar-toggle event listener');
  assert(indexContent.includes("new CustomEvent('billsoft:autohide-sidebar-toggle'"), 'Must dispatch billsoft:autohide-sidebar-toggle event on preference change');
});

// ─── SUITE 3: TOPBAR CONTROL STREAMLINING ───
console.log('\n--- Suite 3: Topbar Control Streamlining ---');

runTest('3.1: Manual #topbar-focus-mode-btn button is removed from top bar', () => {
  // Verify that the topbar no longer renders #topbar-focus-mode-btn
  assert(!indexContent.includes("id: 'topbar-focus-mode-btn'"), 'Manual focus mode button MUST be removed from top bar');
  assert(!indexContent.includes('topbar-focus-button'), 'topbar-focus-button class must not be rendered in topbar');
});

runTest('3.2: Topbar retains calculator launcher, search pill, and title header', () => {
  assert(indexContent.includes("id: 'topbar-calculator-btn'"), 'Calculator button must remain in topbar');
  assert(indexContent.includes("className: 'topbar-search-pill'"), 'OmniSearch pill must remain in topbar');
  assert(indexContent.includes("className: 'topbar-title'"), 'Topbar title must remain in topbar');
});

// ─── SUITE 4: CSS & OVERLAY DRAWER ARCHITECTURE ───
console.log('\n--- Suite 4: CSS & Overlay Drawer Invariants ---');

runTest('4.1: Extreme left edge hover trigger zone (.sidebar-hover-trigger) configured', () => {
  assert(cssContent.includes('.sidebar-hover-trigger'), 'style.css must define .sidebar-hover-trigger');
  assert(/position:\s*fixed;/s.test(cssContent), 'Trigger must be fixed position');
  assert(/width:\s*14px;/s.test(cssContent), 'Trigger width must be 14px');
  assert(/z-index:\s*1040;/s.test(cssContent), 'Trigger z-index must be 1040');
});

runTest('4.2: Hover trigger suppressed when auto-hide is disabled or when modals/search are open', () => {
  assert(
    /\.app-layout:not\(\.auto-hide-sidebar\)\s+\.sidebar-hover-trigger\s*\{[^}]*display:\s*none\s*!important;/s.test(cssContent),
    'Trigger must be hidden when auto-hide is disabled'
  );
  assert(
    cssContent.includes('body.modal-open .sidebar-hover-trigger') ||
    cssContent.includes('body:has(.modal.show) .sidebar-hover-trigger'),
    'Trigger must be suppressed when modals are active'
  );
  assert(
    cssContent.includes('body:has(#rupeecrm-global-search) .sidebar-hover-trigger'),
    'Trigger must be suppressed when global search is open'
  );
});

runTest('4.3: Main content maintains 100% width and 0 margin (no workspace resize/jump on hover)', () => {
  assert(
    /\.app-layout\.auto-hide-sidebar\s+\.main-content\s*\{[^}]*margin-left:\s*0\s*!important;/s.test(cssContent),
    'main-content must have margin-left: 0 !important'
  );
  assert(
    /\.app-layout\.auto-hide-sidebar\s+\.main-content\s*\{[^}]*width:\s*100%\s*!important;/s.test(cssContent),
    'main-content must have width: 100% !important'
  );
  assert(
    /\.app-layout\.auto-hide-sidebar\s+\.sidebar-container\s*\{[^}]*width:\s*0\s*!important;/s.test(cssContent),
    'sidebar-container must collapse to width: 0 !important'
  );
});

runTest('4.4: Sidebar reveals as floating drawer overlay with z-index 1050 and elevated shadow', () => {
  assert(
    /\.app-layout\.auto-hide-sidebar\s+\.sidebar:not\(\.revealed\):not\(\.open\)\s*\{[^}]*transform:\s*translateX\(-100%\)\s*!important;/s.test(cssContent),
    'Sidebar concealed off-canvas by default in auto-hide mode'
  );
  assert(
    cssContent.includes('.app-layout.auto-hide-sidebar .sidebar.revealed') ||
    cssContent.includes('.app-layout.auto-hide-sidebar .sidebar.open'),
    'Must define revealed/open state for auto-hide drawer'
  );
  assert(
    /\.app-layout\.auto-hide-sidebar\s+\.sidebar\.revealed[^{]*\{[^}]*z-index:\s*1050\s*!important;/s.test(cssContent),
    'Revealed sidebar must have z-index: 1050 !important'
  );
  assert(
    /\.app-layout\.auto-hide-sidebar\s+\.sidebar\.revealed[^{]*\{[^}]*box-shadow:/s.test(cssContent),
    'Revealed sidebar must cast elevated drawer shadow'
  );
});

runTest('4.5: Smooth cubic-bezier transition without distracting delay', () => {
  assert(
    cssContent.includes('0.22s cubic-bezier(0.16, 1, 0.3, 1)'),
    'Must use 0.22s cubic-bezier(0.16, 1, 0.3, 1) for professional drawer animation'
  );
});

// ─── SUITE 5: HOVER INTERACTION & AUTO-DISMISS LOGIC ───
console.log('\n--- Suite 5: Hover Interaction & Auto-Dismiss Logic ---');

runTest('5.1: Hover trigger and sidebar container wire enter and leave handlers', () => {
  assert(indexContent.includes('className: \'sidebar-hover-trigger\''), 'App must render .sidebar-hover-trigger');
  assert(indexContent.includes('onMouseEnter: handleTriggerEnter'), 'Trigger must handle mouseEnter');
  assert(indexContent.includes('onMouseLeave: handleTriggerLeave'), 'Trigger must handle mouseLeave');
  assert(indexContent.includes('onMouseEnter: handleSidebarEnter'), 'Sidebar container must handle mouseEnter');
  assert(indexContent.includes('onMouseLeave: handleSidebarLeave'), 'Sidebar container must handle mouseLeave');
});

runTest('5.2: Scheduled hide timer (350ms delay) cancels when cursor re-enters sidebar', () => {
  assert(indexContent.includes('scheduleHideTimer = useCallback((delay = 350)'), 'Must implement 350ms scheduleHideTimer');
  assert(indexContent.includes('cancelHideTimer = useCallback('), 'Must implement cancelHideTimer');
  assert(indexContent.includes('cancelHideTimer();'), 'Must cancel hide timer on mouse re-entry');
});

runTest('5.3: Navigation click dismisses revealed sidebar automatically', () => {
  assert(indexContent.includes('isRevealed = false, onDismissRevealed = null'), 'Sidebar accepts isRevealed and onDismissRevealed props');
  assert(indexContent.includes('if (onDismissRevealed) onDismissRevealed();'), 'handleNav must call onDismissRevealed()');
  assert(indexContent.includes('onDismissRevealed: handleDismissRevealed'), 'App must pass handleDismissRevealed to Sidebar');
});

// ─── SUITE 6: KEYBOARD ACCESSIBILITY & INPUT SAFEGUARDS ───
console.log('\n--- Suite 6: Keyboard Accessibility & Input Safeguards ---');

runTest('6.1: Alt+S / Option+S reveals and toggles sidebar with focus shift to nav items', () => {
  assert(
    indexContent.includes("e.altKey && (e.key === 's' || e.key === 'S' || e.code === 'KeyS')"),
    'Must support Alt+S (Option+S on macOS)'
  );
  assert(
    indexContent.includes("firstNav = document.querySelector('.sidebar .nav-item')"),
    'Must support shifting focus to first navigation item for accessible keyboard navigation'
  );
});

runTest('6.2: Backward-compatible keyboard shortcuts (Alt+F, F10, F9) also reveal sidebar', () => {
  assert(indexContent.includes("e.altKey && (e.key === 'f' || e.key === 'F' || e.code === 'KeyF')"), 'Must support Alt+F');
  assert(indexContent.includes("e.key === 'F10'"), 'Must support F10');
  assert(indexContent.includes("e.key === 'F9'"), 'Must support F9');
});

runTest('6.3: Escape key dismisses revealed sidebar without cancelling inputs or grid cells', () => {
  assert(indexContent.includes("e.key === 'Escape'"), 'Must handle Escape key');
  assert(indexContent.includes('if (isSidebarRevealed)'), 'Must check if sidebar is currently revealed');
  assert(indexContent.includes('handleDismissRevealed();'), 'Escape must invoke handleDismissRevealed');
  assert(
    indexContent.includes("tag === 'input' || tag === 'textarea' || tag === 'select' || (activeEl && activeEl.isContentEditable)"),
    'Must protect input, textarea, and select from Escape interception'
  );
  assert(
    indexContent.includes("activeEl.closest('.compact-grid-cell, .compact-grid-table, .sheet-cell')"),
    'Must protect compact grid and sheet cell editing from Escape interception'
  );
});

// ─── SUITE 7: SUMMARY ───
console.log('\n═══════════════════════════════════════════════════════════════════');
console.log(`TEST SUMMARY: ${passedTests} / ${totalTests} PASSED (100% SUCCESS)`);
console.log('═══════════════════════════════════════════════════════════════════');
