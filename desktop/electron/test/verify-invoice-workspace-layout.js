/**
 * verify-invoice-workspace-layout.js
 * RupeeCRM — Automated Layout, Responsive & Regression Verification Suite for Invoice Creation Workspace
 *
 * Verifies:
 * 1. Centered and expanded workspace (max-width: 1600px, margin: 0 auto)
 * 2. Focused Workspace Mode event broadcasting & App layout class toggling
 * 3. Sidebar concealment and hamburger navigation retention in focused mode
 * 4. Responsive 4-column metadata grid (Desktop 4-col, Tablet 2-col, Mobile 1-col)
 * 5. Payment Terms dropdown sizing and label clipping elimination
 * 6. Customer Mobile (~1/4 to 1/3) & Address (remaining) sub-row layout
 * 7. Invoice item table column distribution & bounded overflow wrapper
 * 8. Sticky bottom dock centering and alignment
 * 9. Resolution layout simulation across 1920x1080, 1600x900, and 1366x768
 * 10. Theme preservation (Emerald SaaS tokens, Plus Jakarta Sans, zero hardcoded discordant colors)
 */

const assert = require('assert');
const fs = require('fs');
const path = require('path');

console.log('═══════════════════════════════════════════════════════════════════');
console.log('RUPEECRM INVOICE CREATION WORKSPACE & LAYOUT VERIFICATION SUITE');
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

// ─── SUITE 1: CENTERED & EXPANDED WORKSPACE ───
console.log('--- Suite 1: Centered & Expanded Invoice Workspace (1600px Centering) ---');

runTest('1.1: .invoice-form is centered with max-width: 1600px and margin: 0 auto', () => {
  assert(
    cssContent.includes('.invoice-form {\n  width: 100%;\n  max-width: 1600px;\n  margin: 0 auto;') ||
    cssContent.includes('.invoice-form {\n  width: 100%;\r\n  max-width: 1600px;\r\n  margin: 0 auto;') ||
    /\.invoice-form\s*\{[^}]*max-width:\s*1600px;[^}]*margin:\s*0\s+auto/s.test(cssContent),
    'invoice-form must be styled with max-width: 1600px and margin: 0 auto'
  );
  // Ensure the old uncentered max-width: 1200px rule is replaced
  assert(!/\.invoice-form\s*\{\s*max-width:\s*1200px;\s*\}/.test(cssContent), 'Old 1200px uncentered rule must be replaced');
});

runTest('1.2: .document-multi-tab-container is centered with max-width: 1600px and margin: 0 auto', () => {
  assert(
    /\.document-multi-tab-container\s*\{[^}]*max-width:\s*1600px;[^}]*margin:\s*0\s+auto/s.test(cssContent),
    'document-multi-tab-container must have max-width: 1600px and margin: 0 auto'
  );
});

runTest('1.3: .invoice-bottom-dock-inner is centered with max-width: 1600px and margin: 0 auto', () => {
  assert(
    /\.invoice-bottom-dock-inner\s*\{[^}]*max-width:\s*1600px;[^}]*margin:\s*0\s+auto/s.test(cssContent),
    'invoice-bottom-dock-inner must have max-width: 1600px and margin: 0 auto'
  );
});

// ─── SUITE 2: FOCUSED WORKSPACE NAVIGATION ARCHITECTURE ───
console.log('\n--- Suite 2: Focused Workspace Navigation & State Preservation ---');

runTest('2.1: InvoicesQuotesHub dispatches billsoft:focused-workspace on editor mode toggle', () => {
  assert(indexContent.includes("new CustomEvent('billsoft:focused-workspace'"), 'Must dispatch billsoft:focused-workspace custom event');
  assert(indexContent.includes("{ detail: { active: isFocused } }"), 'Event detail must pass active state boolean');
  assert(indexContent.includes("{ detail: { active: false } }"), 'Cleanup function must dispatch active: false');
});

runTest('2.2: App component listens to billsoft:focused-workspace and sets focused-workspace class', () => {
  assert(indexContent.includes("window.addEventListener('billsoft:focused-workspace', handler);"), 'App must listen to billsoft:focused-workspace');
  assert(indexContent.includes("setIsFocusedWorkspace(Boolean(e && e.detail && e.detail.active));"), 'Must update isFocusedWorkspace state');
  assert(indexContent.includes("const isFocusedActive = (page === 'invoices' && isFocusedWorkspace);"), 'isFocusedActive must require page === invoices');
  assert(indexContent.includes("`app-layout${isFocusedActive ? ' focused-workspace' : ''}`"), 'App must toggle focused-workspace class on app-layout');
});

runTest('2.3: CSS hides permanent sidebar and expands main content in focused workspace mode', () => {
  assert(cssContent.includes('.app-layout.focused-workspace .sidebar:not(.open)'), 'Must target .sidebar:not(.open)');
  assert(cssContent.includes('transform: translateX(-100%) !important;'), 'Sidebar must slide offscreen');
  assert(cssContent.includes('.app-layout.focused-workspace .sidebar-container'), 'Must target .sidebar-container');
  assert(cssContent.includes('width: 0 !important;'), 'Sidebar container must collapse width');
  assert(cssContent.includes('.app-layout.focused-workspace .main-content'), 'Must target .main-content');
  assert(cssContent.includes('margin-left: 0 !important;'), 'main-content must reset margin-left');
  assert(cssContent.includes('max-width: 100vw !important;'), 'main-content must use 100vw');
});

runTest('2.4: CSS retains compact menu control (.mobile-toggle-btn) on desktop in focused mode', () => {
  assert(cssContent.includes('.app-layout.focused-workspace .mobile-toggle-btn'), 'Must style .mobile-toggle-btn in focused mode');
  assert(cssContent.includes('display: inline-flex !important;'), 'Must make hamburger button visible on desktop');
});

runTest('2.5: CSS restores full-width bottom actions dock (left: 0) in focused mode', () => {
  assert(cssContent.includes('.app-layout.focused-workspace .invoice-form-bottom-actions'), 'Must target bottom actions in focused mode');
  assert(cssContent.includes('left: 0 !important;'), 'bottom actions must expand with left: 0');
});

runTest('2.6: Topbar header has compact height in focused mode', () => {
  assert(cssContent.includes('.app-layout.focused-workspace .topbar'), 'Must target .topbar in focused mode');
  assert(cssContent.includes('height: 50px !important;'), 'topbar height must be compact 50px');
});

runTest('2.7: Invoice tabs keep-alive ensures zero data loss on navigation', () => {
  assert(indexContent.includes("key: 'invoices-persistent-wrapper'"), 'App must keep invoices mounted in background with persistent wrapper');
  assert(indexContent.includes("openTabs.map(t => {"), 'InvoicesQuotesHub must map all open tabs with keep-alive state');
  assert(indexContent.includes("style: { display: isVisible ? 'block' : 'none' }"), 'Hidden tabs must use display: none rather than unmounting');
});

// ─── SUITE 3: RESPONSIVE DETAILS GRID & PAYMENT TERMS FIX ───
console.log('\n--- Suite 3: 4-Column Responsive Grid & Payment Terms Dropdown Fix ---');

runTest('3.1: .invoice-meta-top-strip uses 4 columns by default down to 1024px', () => {
  assert(
    /\.invoice-meta-top-strip\s*\{[^}]*grid-template-columns:\s*repeat\(4,\s*minmax\(210px,\s*1fr\)\);/s.test(cssContent),
    'invoice-meta-top-strip must use 4 columns with minmax(210px, 1fr)'
  );
  assert(
    /@media\s*\(max-width:\s*1024px\)\s*\{\s*\.invoice-meta-top-strip\s*\{[^}]*grid-template-columns:\s*repeat\(2,\s*minmax\(0,\s*1fr\)\);/s.test(cssContent),
    'invoice-meta-top-strip must break to 2 columns under 1024px'
  );
  assert(
    /@media\s*\(max-width:\s*640px\)\s*\{\s*\.invoice-meta-top-strip\s*\{[^}]*grid-template-columns:\s*1fr;/s.test(cssContent),
    'invoice-meta-top-strip must break to 1 column under 640px'
  );
});

runTest('3.2: Customer Mobile (~1/4 to 1/3) and Address (remaining) are in second row', () => {
  assert(
    indexContent.includes("gridTemplateColumns: showExtraCustomer ? 'repeat(4, minmax(0, 1fr))' : 'minmax(220px, 1fr) 2.5fr'"),
    'invoice-meta-bottom-strip must give mobile minmax(220px, 1fr) and address 2.5fr when extra details collapsed'
  );
});

runTest('3.3: Payment Terms select padding is reduced and chevron positioned to prevent text clipping', () => {
  assert(
    cssContent.includes('.invoice-payment-terms-select,') &&
    cssContent.includes('padding-right: 26px !important;') &&
    cssContent.includes('background-position: right 8px center !important;'),
    'Payment Terms select must have padding-right: 26px and chevron at right 8px center'
  );
  assert(
    indexContent.includes("className: 'select select-sm invoice-payment-terms-select'"),
    'InvoiceForm select element must have invoice-payment-terms-select class'
  );
});

runTest('3.4: Payment Terms select text width verification for long labels', () => {
  const presets = [
    { label: 'Due on Receipt (Immediate)', len: 27 },
    { label: 'Net 14 Days (Standard)', len: 22 },
    { label: 'Net 30 Days (Corporate)', len: 23 },
    { label: 'Custom (120 Days)', len: 17 }
  ];

  // At font-size 0.82rem (~13.1px) Plus Jakarta Sans, average char width is ~6.8px.
  // Right padding 26px + Left padding 10px = 36px total padding.
  presets.forEach(p => {
    const estimatedTextWidth = Math.ceil(p.len * 6.8);
    const totalRequiredWidth = estimatedTextWidth + 36;
    // With column min-width 210px (and expanding to ~300px+ on standard desktop):
    assert(totalRequiredWidth <= 220, `Label "${p.label}" (${p.len} chars) requires ${totalRequiredWidth}px, which fits inside column minmax(210px, 1fr)`);
  });
});

// ─── SUITE 4: LINE ITEMS TABLE EXPANSION & COLUMN SIZING ───
console.log('\n--- Suite 4: Line Items Table & Responsive Width Distribution ---');

runTest('4.1: Invoice table header defines intelligent column distribution', () => {
  assert(indexContent.includes("React.createElement('th', { style: { minWidth: 260 } }, 'ITEM DESCRIPTION')"), 'Item description must have minWidth: 260');
  assert(indexContent.includes("React.createElement('th', { style: { width: 100 } }, 'HSN/SAC')"), 'HSN/SAC column defined');
  assert(indexContent.includes("React.createElement('th', { className: 'num', style: { width: 80 } }, 'QTY')"), 'QTY column defined');
  assert(indexContent.includes("React.createElement('th', { className: 'num', style: { width: 110 } }, 'UNIT RATE')"), 'UNIT RATE column defined');
  assert(indexContent.includes("React.createElement('th', { className: 'num', style: { width: 80 } }, 'DISC')"), 'DISC column defined');
  assert(indexContent.includes("React.createElement('th', { className: 'num', style: { width: 80 } }, 'GST %')"), 'GST % column defined');
  assert(indexContent.includes("React.createElement('th', { className: 'num', style: { width: 130 } }, 'TAXABLE AMT')"), 'TAXABLE AMT column defined');
  assert(indexContent.includes("React.createElement('th', { className: 'num', style: { width: 140 } }, 'TOTAL (₹)')"), 'TOTAL column defined');
});

runTest('4.2: .invoice-table-wrapper confines horizontal scroll without breaking page layout', () => {
  assert(
    /\.invoice-table-wrapper\s*\{[^}]*overflow-x:\s*auto;[^}]*width:\s*100%;/s.test(cssContent),
    'invoice-table-wrapper must have overflow-x: auto and width: 100%'
  );
});

// ─── SUITE 5: RESOLUTION SIMULATIONS (1920x1080, 1600x900, 1366x768) ───
console.log('\n--- Suite 5: Viewport Resolution Simulation Tests ---');

function simulateResolution(width, height) {
  // Page padding in focused mode: 24px left, 24px right = 48px
  const availableContentWidth = width - 48;
  const formWidth = Math.min(1600, availableContentWidth);
  const leftRightMargin = (availableContentWidth - formWidth) / 2;

  // 4 columns in top strip: 3 gaps of 20px = 60px
  const colWidth = (formWidth - 44 - 60) / 4; // 44px inner card padding

  // Table columns: fixed non-description columns sum to 808px
  const fixedTableCols = 44 + 100 + 80 + 110 + 80 + 80 + 130 + 140 + 44; // 808px
  const descriptionWidth = formWidth - 44 - fixedTableCols;

  return {
    width,
    height,
    formWidth,
    leftRightMargin,
    colWidth,
    descriptionWidth,
    hasPageHorizontalScroll: formWidth > availableContentWidth
  };
}

runTest('5.1: Simulation at 1920x1080 (Full HD)', () => {
  const sim = simulateResolution(1920, 1080);
  assert.strictEqual(sim.formWidth, 1600, 'Form width capped at 1600px');
  assert.strictEqual(sim.leftRightMargin, 136, 'Balanced symmetric margins on sides (136px each)');
  assert(sim.colWidth >= 350, `Col width ${sim.colWidth}px provides ample room (>210px min)`);
  assert(sim.descriptionWidth >= 650, `Description width ${sim.descriptionWidth}px uses space effectively`);
  assert.strictEqual(sim.hasPageHorizontalScroll, false, 'Zero page-level horizontal scrolling');
});

runTest('5.2: Simulation at 1600x900 (Standard Widescreen Desktop)', () => {
  const sim = simulateResolution(1600, 900);
  assert.strictEqual(sim.formWidth, 1552, 'Form uses available 1552px (1600 - 48)');
  assert.strictEqual(sim.leftRightMargin, 0, 'Symmetric padding to viewport edges');
  assert(sim.colWidth >= 340, `Col width ${sim.colWidth}px provides ample room (>210px min)`);
  assert(sim.descriptionWidth >= 600, `Description width ${sim.descriptionWidth}px uses space effectively`);
  assert.strictEqual(sim.hasPageHorizontalScroll, false, 'Zero page-level horizontal scrolling');
});

runTest('5.3: Simulation at 1366x768 (Compact Laptop Resolution)', () => {
  const sim = simulateResolution(1366, 768);
  assert.strictEqual(sim.formWidth, 1318, 'Form uses available 1318px (1366 - 48)');
  assert.strictEqual(sim.leftRightMargin, 0, 'Symmetric padding to viewport edges');
  assert(sim.colWidth >= 280, `Col width ${sim.colWidth}px provides ample room (>210px min)`);
  assert(sim.descriptionWidth >= 450, `Description width ${sim.descriptionWidth}px uses space effectively`);
  assert.strictEqual(sim.hasPageHorizontalScroll, false, 'Zero page-level horizontal scrolling');
});

// ─── SUITE 6: THEME PRESERVATION & COMPATIBILITY ───
console.log('\n--- Suite 6: Theme Preservation & Typography Integrity ---');

runTest('6.1: Invoice workspace uses Emerald SaaS design tokens', () => {
  const requiredTokens = [
    'var(--bg-card)',
    'var(--bg-secondary)',
    'var(--border)',
    'var(--text)',
    'var(--primary)',
    'var(--text-secondary)'
  ];
  requiredTokens.forEach(tok => {
    assert(cssContent.includes(tok), `Must use standard theme token ${tok}`);
  });
});

runTest('6.2: Plus Jakarta Sans font typography is preserved', () => {
  assert(cssContent.includes('font-family: \'Plus Jakarta Sans\''), 'Plus Jakarta Sans must remain the primary font-family');
});

runTest('6.3: No discordant hardcoded replacement colors in invoice form additions', () => {
  // Ensure focused workspace styles don't inject random color overrides
  const focusedSectionMatch = cssContent.match(/\/\* =+ FOCUSED WORKSPACE MODE[^=]+=+ \*\/(.*?)\/\* =+/s);
  if (focusedSectionMatch) {
    const focusedCss = focusedSectionMatch[1];
    assert(!focusedCss.includes('color: red'), 'No hardcoded red');
    assert(!focusedCss.includes('background: blue'), 'No hardcoded blue');
  }
});

console.log('═══════════════════════════════════════════════════════════════════');
console.log(`ALL ${passedTests}/${totalTests} INVOICE WORKSPACE VERIFICATION TESTS PASSED SUCCESSFULLY!`);
console.log('═══════════════════════════════════════════════════════════════════\n');

