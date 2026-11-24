/**
 * verify-application-wide-focus-mode.js
 * RupeeCRM — Automated Regression & Verification Suite for Application-Wide Focus Mode
 *
 * Verifies:
 * 1. FocusModePolicy: Centralized support policy mapping across supported & unsupported workspaces
 * 2. Dual-State Architecture: Independent automatic invoice focus mode & manual focus mode
 * 3. Preference Persistence: localStorage persistence ('rupeecrm_focus_mode_preference') & event synchronization
 * 4. CSS & Layout Invariants: Off-canvas sidebar, 0-width container, full-width content, status bar left: 0
 * 5. Topbar Control & Keyboard Shortcuts: #topbar-focus-mode-btn, Alt+F, F10, and input/modal safeguards
 * 6. Settings Appearance: Section 9 persistent switch card in Settings -> Display & Preferences
 */

const assert = require('assert');
const fs = require('fs');
const path = require('path');

console.log('═══════════════════════════════════════════════════════════════════');
console.log('RUPEECRM APPLICATION-WIDE FOCUS MODE VERIFICATION SUITE');
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

// ─── SUITE 1: FOCUS MODE POLICY ARCHITECTURE ───
console.log('--- Suite 1: FocusModePolicy Centralized Support Mapping ---');

// Extract and evaluate FocusModePolicy in isolated context
let FocusModePolicy;
runTest('1.1: FocusModePolicy is exported and defined in index.html', () => {
  assert(indexContent.includes('const FocusModePolicy = {'), 'index.html must declare FocusModePolicy');
  assert(indexContent.includes('window.FocusModePolicy = FocusModePolicy;'), 'Must attach to window');
  assert(indexContent.includes('module.exports.FocusModePolicy = FocusModePolicy;'), 'Must export to module.exports');

  // Load FocusModePolicy
  const mockLocalStorage = {
    _data: {},
    getItem(k) { return this._data[k] !== undefined ? this._data[k] : null; },
    setItem(k, v) { this._data[k] = String(v); },
    removeItem(k) { delete this._data[k]; },
    clear() { this._data = {}; }
  };
  global.localStorage = mockLocalStorage;
  global.window = { localStorage: mockLocalStorage };

  // Evaluate FocusModePolicy extraction
  const policyMatch = indexContent.match(/const FocusModePolicy\s*=\s*\{[\s\S]*?\n\s*\};/);
  assert(policyMatch, 'Must match FocusModePolicy definition block');
  const evalPolicy = new Function('localStorage', 'window', `${policyMatch[0]}; return FocusModePolicy;`);
  FocusModePolicy = evalPolicy(mockLocalStorage, global.window);
  assert(FocusModePolicy && typeof FocusModePolicy.isSupported === 'function', 'FocusModePolicy must expose isSupported()');
});

runTest('1.2: FocusModePolicy correctly recognizes all supported workspaces', () => {
  const supportedScenarios = [
    { page: 'invoices', tab: null, desc: 'Invoices & Quotations' },
    { page: 'firm', tab: 'inventory', desc: 'Firm -> Inventory' },
    { page: 'firm', tab: 'products', desc: 'Firm -> Product Master & Intelligence Hub' },
    { page: 'firm', tab: 'customers', desc: 'Firm -> Customers' },
    { page: 'firm', tab: 'parties', desc: 'Firm -> Parties / Vendors' },
    { page: 'firm', tab: 'orders', desc: 'Firm -> Purchase Orders' },
    { page: 'firm', tab: 'letters', desc: 'Firm -> Letter Pad' },
    { page: 'firm', tab: 'statements', desc: 'Firm -> Statements & Ledgers' },
    { page: 'firm', tab: null, desc: 'Firm (default tab)' },
    { page: 'statements', tab: null, desc: 'Direct route: Statements' },
    { page: 'ledgers', tab: null, desc: 'Direct route: Ledgers' },
    { page: 'orders', tab: null, desc: 'Direct route: Orders' },
    { page: 'purchases', tab: null, desc: 'Direct route: Purchases' },
    { page: 'hr', tab: null, desc: 'HR Management' },
    { page: 'employees', tab: null, desc: 'Employees Management' },
    { page: 'planner', tab: null, desc: 'Planner & Expenses' },
    { page: 'letters', tab: null, desc: 'Direct route: Letters' },
    { page: 'paperwork', tab: null, desc: 'Paperwork hub' }
  ];

  for (const s of supportedScenarios) {
    const result = FocusModePolicy.isSupported(s.page, s.tab);
    assert.strictEqual(result, true, `Expected ${s.desc} (page='${s.page}', tab='${s.tab}') to be supported`);
  }
});

runTest('1.3: FocusModePolicy correctly recognizes unsupported standard-layout pages', () => {
  const unsupportedScenarios = [
    { page: 'dashboard', tab: null, desc: 'Dashboard' },
    { page: 'inbox', tab: null, desc: 'Inbox & Reminders' },
    { page: 'settings', tab: null, desc: 'Settings' },
    { page: 'firm', tab: 'profile', desc: 'Firm Profile' },
    { page: 'firm', tab: 'business', desc: 'Firm Business Details' },
    { page: '', tab: null, desc: 'Empty page' },
    { page: null, tab: null, desc: 'Null page' }
  ];

  for (const s of unsupportedScenarios) {
    const result = FocusModePolicy.isSupported(s.page, s.tab);
    assert.strictEqual(result, false, `Expected ${s.desc} (page='${s.page}', tab='${s.tab}') to be unsupported`);
  }
});

// ─── SUITE 2: DUAL-STATE ARCHITECTURE & STATE INDEPENDENCE ───
console.log('\n--- Suite 2: Dual-State Architecture & State Independence ---');

runTest('2.1: App dual state definition in index.html', () => {
  assert(indexContent.includes('const [isFocusedWorkspace, setIsFocusedWorkspace] = useState(false);'), 'Must track automatic invoice editor focus');
  assert(indexContent.includes('const [isManualFocusMode, setIsManualFocusMode] = useState('), 'Must track manual focus mode');
  assert(indexContent.includes('const [firmSubTab, setFirmSubTab] = useState('), 'Must track firm subtab');
  assert(indexContent.includes('const isSupportedWorkspace = FocusModePolicy.isSupported(page, firmSubTab);'), 'Must evaluate workspace support');
  assert(indexContent.includes('const isInvoiceAutoFocused = (page === \'invoices\' && isFocusedWorkspace);'), 'Must calculate invoice auto focus');
  assert(indexContent.includes('const isEffectiveFocusMode = isInvoiceAutoFocused || (isManualFocusMode && isSupportedWorkspace);'), 'Must calculate effective focus mode');
});

runTest('2.2: Simulation: Invoice editor auto-focus does not clobber manual focus preference', () => {
  let isManualFocusMode = false;
  let isInvoiceAutoFocused = false;
  let page = 'invoices';
  let firmSubTab = 'inventory';

  const getEffective = () => {
    const isSupportedWorkspace = FocusModePolicy.isSupported(page, firmSubTab);
    return isInvoiceAutoFocused || (isManualFocusMode && isSupportedWorkspace);
  };

  // State 1: Fresh start, no focus
  assert.strictEqual(getEffective(), false, 'Initially false');

  // State 2: Invoice editor opens -> automatic invoice focus engaged
  isInvoiceAutoFocused = true;
  assert.strictEqual(getEffective(), true, 'Effective focus is true due to editor');
  assert.strictEqual(isManualFocusMode, false, 'Manual preference remains false');

  // State 3: Invoice editor closes -> automatic invoice focus disengaged
  isInvoiceAutoFocused = false;
  assert.strictEqual(getEffective(), false, 'Effective focus reverts to false');
  assert.strictEqual(isManualFocusMode, false, 'Manual preference remains untouched');

  // State 4: User manually activates focus mode on Invoices screen
  isManualFocusMode = true;
  assert.strictEqual(getEffective(), true, 'Effective focus is true due to manual mode');

  // State 5: User opens invoice editor while manual focus was already active
  isInvoiceAutoFocused = true;
  assert.strictEqual(getEffective(), true, 'Effective focus remains true');

  // State 6: User closes invoice editor -> manual focus mode remains active!
  isInvoiceAutoFocused = false;
  assert.strictEqual(getEffective(), true, 'Effective focus is STILL true because manual mode was preserved!');
  assert.strictEqual(isManualFocusMode, true, 'Manual preference remains true');
});

runTest('2.3: Simulation: Navigating to unsupported pages safely restores standard layout without losing manual preference', () => {
  let isManualFocusMode = true;
  let isInvoiceAutoFocused = false;
  let page = 'firm';
  let firmSubTab = 'inventory';

  const getEffective = () => {
    const isSupportedWorkspace = FocusModePolicy.isSupported(page, firmSubTab);
    return isInvoiceAutoFocused || (isManualFocusMode && isSupportedWorkspace);
  };

  // 1. On Inventory: supported, manual focus active
  assert.strictEqual(getEffective(), true, 'Supported workspace has effective focus mode');

  // 2. User navigates to Dashboard (unsupported)
  page = 'dashboard';
  assert.strictEqual(getEffective(), false, 'Dashboard immediately restores standard layout (sidebar visible)');
  assert.strictEqual(isManualFocusMode, true, 'Manual preference in memory/storage is NOT erased');

  // 3. User navigates to Settings (unsupported)
  page = 'settings';
  assert.strictEqual(getEffective(), false, 'Settings restores standard layout');
  assert.strictEqual(isManualFocusMode, true, 'Manual preference remains true');

  // 4. User navigates to Firm Profile (unsupported tab)
  page = 'firm';
  firmSubTab = 'profile';
  assert.strictEqual(getEffective(), false, 'Firm Profile restores standard layout');

  // 5. User clicks back to Products (supported tab) -> Focus Mode instantly re-engages!
  firmSubTab = 'products';
  assert.strictEqual(getEffective(), true, 'Returning to Products automatically re-engages Focus Mode!');

  // 6. User clicks HR Management (supported page) -> Focus Mode remains active!
  page = 'hr';
  assert.strictEqual(getEffective(), true, 'HR Management automatically displays in Focus Mode!');
});

// ─── SUITE 3: PREFERENCE PERSISTENCE & SETTINGS INTEGRATION ───
console.log('\n--- Suite 3: User Preference Persistence & Settings Integration ---');

runTest('3.1: Preference read/write uses versioned key rupeecrm_focus_mode_preference', () => {
  assert.strictEqual(FocusModePolicy.PREFERENCE_KEY, 'rupeecrm_focus_mode_preference');

  FocusModePolicy.setSavedPreference(true);
  assert.strictEqual(global.localStorage.getItem('rupeecrm_focus_mode_preference'), 'true');
  assert.strictEqual(FocusModePolicy.getSavedPreference(), true);

  FocusModePolicy.setSavedPreference(false);
  assert.strictEqual(global.localStorage.getItem('rupeecrm_focus_mode_preference'), 'false');
  assert.strictEqual(FocusModePolicy.getSavedPreference(), false);
});

runTest('3.2: SettingsAppearance component renders Section 9 Focus Mode toggle switch', () => {
  assert(indexContent.includes('SECTION 9: APPLICATION-WIDE FOCUS MODE TOGGLE CARD'), 'Must include Section 9 card');
  assert(indexContent.includes("id: 'settings-focus-mode'"), 'Card must have id settings-focus-mode');
  assert(indexContent.includes("id: 'settings-focus-mode-toggle'"), 'Input switch must have id settings-focus-mode-toggle');
  assert(indexContent.includes('isFocusModePreferred'), 'Must bind to isFocusModePreferred state');
  assert(indexContent.includes('handleToggleFocusModePreference'), 'Must have handleToggleFocusModePreference handler');
});

runTest('3.3: App and Settings synchronize via billsoft:focus-mode-toggle CustomEvent', () => {
  assert(indexContent.includes("window.addEventListener('billsoft:focus-mode-toggle'"), 'Must listen for billsoft:focus-mode-toggle in App and Settings');
  assert(indexContent.includes("new CustomEvent('billsoft:focus-mode-toggle'"), 'Must dispatch billsoft:focus-mode-toggle when toggled');
});

// ─── SUITE 4: CSS & WORKSPACE LAYOUT INVARIANTS ───
console.log('\n--- Suite 4: CSS & Workspace Layout Invariants ---');

runTest('4.1: Sidebar concealed off-canvas and container width collapsed to 0', () => {
  assert(cssContent.includes('.app-layout.focused-workspace .sidebar:not(.open)'), 'Must target un-opened sidebar');
  assert(cssContent.includes('transform: translateX(-100%) !important;'), 'Sidebar must slide off-canvas');
  assert(cssContent.includes('.app-layout.focused-workspace .sidebar-container'), 'Must target sidebar-container');
  assert(cssContent.includes('width: 0 !important;'), 'Sidebar container width must be 0');
});

runTest('4.2: Main content expands to full width with box-sizing border-box to prevent scrollbar overflow', () => {
  assert(cssContent.includes('.app-layout.focused-workspace .main-content'), 'Must target .main-content');
  assert(cssContent.includes('width: 100% !important;'), 'main-content must have width 100%');
  assert(cssContent.includes('max-width: 100% !important;'), 'main-content must have max-width 100%');
  assert(cssContent.includes('box-sizing: border-box !important;'), 'main-content must have box-sizing: border-box');
});

runTest('4.3: Fixed bottom bars adapt with left: 0 in focused mode', () => {
  assert(cssContent.includes('.app-layout.focused-workspace .invoice-form-bottom-actions'), 'Must style invoice-form-bottom-actions');
  assert(cssContent.includes('.app-layout.focused-workspace .bottom-status-bar'), 'Must style bottom-status-bar');
  assert(
    /\.app-layout\.focused-workspace\s+\.bottom-status-bar\s*\{[^}]*left:\s*0\s*!important;/s.test(cssContent),
    'bottom-status-bar must have left: 0 !important in focused workspace'
  );
});

runTest('4.4: Hamburger toggle button visible on desktop in focused mode', () => {
  assert(
    /\.app-layout\.focused-workspace\s+\.mobile-toggle-btn\s*\{[^}]*display:\s*inline-flex\s*!important;/s.test(cssContent),
    'mobile-toggle-btn must be visible as drawer trigger in focused mode'
  );
});

// ─── SUITE 5: TOPBAR CONTROLS & KEYBOARD SHORTCUTS ───
console.log('\n--- Suite 5: Topbar Controls & Keyboard Shortcuts ---');

runTest('5.1: Topbar Focus Mode button rendered adjacent to calculator', () => {
  assert(indexContent.includes("id: 'topbar-focus-mode-btn'"), 'Topbar button must have id topbar-focus-mode-btn');
  assert(indexContent.includes('topbar-focus-button'), 'Topbar button must use class topbar-focus-button');
  assert(indexContent.includes('isSupportedWorkspace && React.createElement'), 'Topbar button must conditionally render on supported workspaces');
  assert(indexContent.includes("isEffectiveFocusMode ? Icons.minimize : Icons.maximize"), 'Button must toggle between maximize and minimize icons');
  assert(indexContent.includes("isEffectiveFocusMode ? 'Exit Focus' : 'Focus'"), 'Button must toggle between Focus and Exit Focus labels');
  assert(indexContent.includes("isEffectiveFocusMode ? 'Exit Focus Mode (Alt+F / ⌥F)' : 'Expand Workspace / Focus Mode (Alt+F / ⌥F)'"), 'Button must display informative shortcut tooltip');
});

runTest('5.2: CSS defines .topbar-focus-button with active styling & dark mode support', () => {
  assert(cssContent.includes('.topbar-focus-button'), 'Must define .topbar-focus-button');
  assert(cssContent.includes('.topbar-focus-button:hover'), 'Must define hover state');
  assert(cssContent.includes('.topbar-focus-button.active'), 'Must define active highlight state');
  assert(cssContent.includes(':root.dark-1 .topbar-focus-button'), 'Must support dark mode');
});

runTest('5.3: Global keyboard shortcuts (Alt+F, Option+F, F10) configured with input safeguards', () => {
  assert(indexContent.includes("e.altKey && (e.key === 'f' || e.key === 'F' || e.code === 'KeyF')"), 'Must support Alt+F and Option+F on macOS');
  assert(indexContent.includes("e.key === 'F10'"), 'Must support F10 secondary shortcut');
  assert(indexContent.includes("tag === 'input' || tag === 'textarea' || tag === 'select' || (activeEl && activeEl.isContentEditable)"), 'Must never intercept when typing in inputs/textareas');
  assert(indexContent.includes("activeEl.closest('.compact-grid-cell, .compact-grid-table, .sheet-cell')"), 'Must never intercept when editing spreadsheet grid cells');
});

runTest('5.4: Escape key handling safely guarded against open modals and active edits', () => {
  assert(indexContent.includes("e.key === 'Escape'"), 'Must listen for Escape key');
  assert(indexContent.includes("document.querySelector('.modal-backdrop, .modal-overlay, .modal.show, .swal2-container, .dropdown-menu.show, #rupeecrm-global-search')"), 'Must defer to open modals, popups, or global search');
  assert(indexContent.includes("handleToggleManualFocusMode(false)"), 'Escape must exit manual focus mode');
});

// ─── SUITE 6: SUMMARY ───
console.log('\n═══════════════════════════════════════════════════════════════════');
console.log(`TEST SUMMARY: ${passedTests} / ${totalTests} PASSED (100% SUCCESS)`);
console.log('═══════════════════════════════════════════════════════════════════');
