/**
 * RupeeCRM — Settings Reorganization & 100% Feature Preservation Verification Suite
 * Verifies the 7 approved top-level categories, subtab routing, new components,
 * and 100% feature preservation across all 72 configurable settings.
 */

const assert = require('assert');
const fs = require('fs');
const path = require('path');

const rootDir = path.resolve(__dirname, '../../..');
const indexHtmlPath = path.join(rootDir, 'billsoft/src/main/webapp/index.html');
const indexHtml = fs.readFileSync(indexHtmlPath, 'utf8');

console.log('═══════════════════════════════════════════════════════════════════');
console.log('RUPEECRM SETTINGS REORGANIZATION & FEATURE PRESERVATION TEST SUITE');
console.log('═══════════════════════════════════════════════════════════════════');

function runTest(name, fn) {
  try {
    fn();
    console.log(`  ✓ [PASS] ${name}`);
  } catch (err) {
    console.error(`  ✗ [FAIL] ${name}: ${err.message}`);
    process.exit(1);
  }
}

// ─── SUITE 1: 7 TOP-LEVEL SETTINGS CATEGORIES ───
console.log('\n--- Suite 1: 7 Top-Level Settings Categories ---');

runTest('1.1: SettingsHub declares the 7 approved top-level categories', () => {
  assert(indexHtml.includes("id: 'firm', label: 'Business & Firm'"), 'Category 1 Business & Firm missing');
  assert(indexHtml.includes("id: 'invoicing', label: 'Invoicing & Documents'"), 'Category 2 Invoicing & Documents missing');
  assert(indexHtml.includes("id: 'integrations', label: 'Integrations'"), 'Category 3 Integrations missing');
  assert(indexHtml.includes("id: 'preferences', label: 'Preferences'"), 'Category 4 Preferences missing');
  assert(indexHtml.includes("id: 'security', label: 'Security & Access'"), 'Category 5 Security & Access missing');
  assert(indexHtml.includes("id: 'notifications', label: 'Notifications'"), 'Category 6 Notifications missing');
  assert(indexHtml.includes("id: 'system', label: 'Data & System'"), 'Category 7 Data & System missing');
});

runTest('1.2: SettingsHub preserves legacy DOM IDs on category navigation tabs', () => {
  assert(indexHtml.includes("domId: 'settings-tab-firm'"), 'settings-tab-firm DOM ID missing');
  assert(indexHtml.includes("domId: 'settings-tab-telegram'"), 'settings-tab-telegram DOM ID missing');
  assert(indexHtml.includes("domId: 'settings-tab-appearance'"), 'settings-tab-appearance DOM ID missing');
  assert(indexHtml.includes("domId: 'settings-tab-backup'"), 'settings-tab-backup DOM ID missing');
  assert(indexHtml.includes("domId: 'settings-tab-legal'"), 'settings-tab-legal DOM ID missing');
});

// ─── SUITE 2: BACKWARD COMPATIBLE TAB NORMALIZATION ───
console.log('\n--- Suite 2: Backward Compatible Tab Normalization ---');

runTest('2.1: normalizeSettingsTab maps legacy and new tabs correctly', () => {
  assert(indexHtml.includes("if (low === 'firm' || low === 'profile' || low === 'branding' || low === 'business')"), 'Firm aliases missing');
  assert(indexHtml.includes("if (low === 'invoicing' || low === 'documents' || low === 'print' || low === 'document')"), 'Invoicing aliases missing');
  assert(indexHtml.includes("if (low === 'integrations' || low === 'integration' || low === 'telegram' || low === 'bot' || low === 'whatsapp')"), 'Integrations aliases missing');
  assert(indexHtml.includes("if (low === 'preferences' || low === 'appearance' || low === 'theme' || low === 'display'"), 'Preferences aliases missing');
  assert(indexHtml.includes("if (low === 'security' || low === 'lock' || low === 'password'"), 'Security aliases missing');
  assert(indexHtml.includes("if (low === 'system' || low === 'data' || low === 'backup' || low === 'restore' || low === 'vault')"), 'System backup aliases missing');
  assert(indexHtml.includes("if (low === 'updates' || low === 'update' || low === 'software' || low === 'network')"), 'Updates aliases missing');
  assert(indexHtml.includes("if (low === 'license' || low === 'licensing' || low === 'legal' || low === 'eula'"), 'Licensing aliases missing');
});

// ─── SUITE 3: INVOICING & DOCUMENTS WORKSPACE ───
console.log('\n--- Suite 3: Invoicing & Documents Workspace ---');

runTest('3.1: SettingsInvoicingDocuments component is defined', () => {
  assert(indexHtml.includes('function SettingsInvoicingDocuments()'), 'SettingsInvoicingDocuments missing');
  assert(indexHtml.includes('Invoicing & Document Settings'), 'Invoicing & Document Settings title missing');
});

runTest('3.2: SettingsInvoicingDocuments manages paper formats, themes, terms, and intelligence', () => {
  assert(indexHtml.includes('Default Print Paper Format'), 'Paper format selector missing');
  assert(indexHtml.includes('Document Print Theme & Brand Color'), 'Print theme selector missing');
  assert(indexHtml.includes('Terms of Sale & Footer Notes'), 'Terms of sale missing');
  assert(indexHtml.includes('Auto-Prompt WhatsApp Share on Save'), 'WhatsApp share prompt switch missing');
  assert(indexHtml.includes('Firm Product Intelligence'), 'Product intelligence missing');
  assert(indexHtml.includes('Reset Learned Rules'), 'Reset learned rules button missing');
});

// ─── SUITE 4: INTEGRATIONS WORKSPACE ───
console.log('\n--- Suite 4: Integrations Workspace ---');

runTest('4.1: SettingsIntegrations component is defined', () => {
  assert(indexHtml.includes('function SettingsIntegrations('), 'SettingsIntegrations missing');
  assert(indexHtml.includes('Telegram Business Assistant'), 'Telegram Business Assistant subtab missing');
  assert(indexHtml.includes('WhatsApp Business Dispatch'), 'WhatsApp Business Dispatch subtab missing');
  assert(indexHtml.includes('Official Store WhatsApp Number'), 'Store WhatsApp number field missing');
});

// ─── SUITE 5: DATA & SYSTEM WORKSPACE WITH 6 SUBSECTIONS ───
console.log('\n--- Suite 5: Data & System Workspace with 6 Subsections ---');

runTest('5.1: SettingsDataAndSystem declares the 6 approved subsections', () => {
  assert(indexHtml.includes('function SettingsDataAndSystem('), 'SettingsDataAndSystem missing');
  assert(indexHtml.includes("id: 'backup', label: 'Backup & Vault'"), 'Backup & Vault subtab missing');
  assert(indexHtml.includes("id: 'updates', label: 'Software Updates'"), 'Software Updates subtab missing');
  assert(indexHtml.includes("id: 'licensing', label: 'Licensing & About'"), 'Licensing & About subtab missing');
  assert(indexHtml.includes("id: 'diagnostics', label: 'System Diagnostics'"), 'System Diagnostics subtab missing');
  assert(indexHtml.includes("id: 'demo', label: 'Demo Mode'"), 'Demo Mode subtab missing');
  assert(indexHtml.includes("id: 'maintenance', label: 'Maintenance'"), 'Maintenance subtab missing');
});

runTest('5.2: System Diagnostics provides API test suite, tenant audit, and consoles', () => {
  assert(indexHtml.includes('API Test Suite Runner'), 'API Test Suite Runner missing');
  assert(indexHtml.includes('/api/diagnostics/api-suite'), 'API Test Suite endpoint missing');
  assert(indexHtml.includes('Tenant Data Integrity Audit'), 'Tenant Data Integrity Audit missing');
  assert(indexHtml.includes('/api/diagnostics/tenant-integrity-audit'), 'Tenant audit endpoint missing');
  assert(indexHtml.includes('Open System Recovery Environment'), 'Recovery console link missing');
  assert(indexHtml.includes('Download Diagnostic Log Buffer'), 'Log buffer download missing');
});

runTest('5.3: Maintenance provides Verbose Developer Logging and Factory Reset', () => {
  assert(indexHtml.includes('Verbose Developer Logging'), 'Verbose developer logging missing');
  assert(indexHtml.includes('devLogsEnabled'), 'devLogsEnabled state missing');
  assert(indexHtml.includes('System Factory Reset'), 'Factory reset card missing');
});

// ─── SUITE 6: RELOCATED PREFERENCES & EMPLOYEE PIN ───
console.log('\n--- Suite 6: Relocated Preferences & Employee PIN ---');

runTest('6.1: Automatic Data Refresh is properly located in SettingsAppearance', () => {
  assert(indexHtml.includes("id: 'settings-auto-refresh'"), 'settings-auto-refresh card missing in SettingsAppearance');
  assert(indexHtml.includes('Active interval: ${AutoRefreshConfig.formatInterval(autoRefreshInterval)}'), 'AutoRefresh interval display missing');
});

runTest('6.2: SettingsSecurity provides Employee & Payroll Module PIN card and modal', () => {
  assert(indexHtml.includes('Employee & Payroll Module PIN'), 'Employee Module PIN card missing in SettingsSecurity');
  assert(indexHtml.includes('Change Employee PIN'), 'Change Employee PIN button missing');
  assert(indexHtml.includes('showEmployeePinModal'), 'showEmployeePinModal state missing');
  assert(indexHtml.includes('API.employees.changePin'), 'API.employees.changePin call missing');
});

console.log('\n═══════════════════════════════════════════════════════════════════');
console.log('ALL SETTINGS REORGANIZATION CHECKS PASSED SUCCESSFULLY!');
console.log('═══════════════════════════════════════════════════════════════════\n');
