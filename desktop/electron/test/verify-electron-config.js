/**
 * Verification Suite for RupeeCRM Electron Desktop Shell Configuration
 */

const fs = require('fs');
const path = require('path');
const assert = require('assert');

console.log('--- RupeeCRM Electron Configuration Test Suite ---');

const baseDir = path.resolve(__dirname, '..');
const pkgPath = path.join(baseDir, 'package.json');
const mainPath = path.join(baseDir, 'src', 'main.js');
const preloadPath = path.join(baseDir, 'src', 'preload.js');
const builderConfigPath = path.join(baseDir, 'electron-builder.yml');
const splashPath = path.join(baseDir, 'src', 'splash', 'splash.html');
const diagPath = path.join(baseDir, 'src', 'diagnostics', 'diagnostics.html');
const icoPath = path.join(baseDir, 'assets', 'icon.ico');
const pngPath = path.join(baseDir, 'assets', 'icon.png');

// 1. Verify Files Exist
console.log('[1/7] Verifying core desktop files exist...');
assert(fs.existsSync(pkgPath), 'package.json must exist');
assert(fs.existsSync(mainPath), 'src/main.js must exist');
assert(fs.existsSync(preloadPath), 'src/preload.js must exist');
assert(fs.existsSync(builderConfigPath), 'electron-builder.yml must exist');
assert(fs.existsSync(splashPath), 'splash.html must exist');
assert(fs.existsSync(diagPath), 'diagnostics.html must exist');
assert(fs.existsSync(icoPath), 'assets/icon.ico must exist');
assert(fs.existsSync(pngPath), 'assets/icon.png must exist');
console.log('  -> All core files present.');

// 2. Verify package.json
console.log('[2/7] Verifying package.json...');
const pkg = JSON.parse(fs.readFileSync(pkgPath, 'utf8'));
assert.strictEqual(pkg.name, 'rupeecrm-desktop');
assert.strictEqual(pkg.main, 'src/main.js');
assert(pkg.devDependencies.electron, 'electron must be a devDependency');
assert(pkg.devDependencies['electron-builder'], 'electron-builder must be a devDependency');
console.log('  -> package.json valid.');

// 3. Verify electron-builder.yml configuration
console.log('[3/7] Verifying electron-builder.yml...');
const builderYaml = fs.readFileSync(builderConfigPath, 'utf8');
assert(builderYaml.includes('appId: com.rupeecrm.desktop'), 'App ID must be com.rupeecrm.desktop');
assert(builderYaml.includes('target:\n    - target: nsis'), 'NSIS must be target');
assert(builderYaml.includes('deleteAppDataOnUninstall: false'), 'Must preserve customer data on uninstall');
assert(builderYaml.includes('oneClick: false'), 'Must use conventional setup wizard');
assert(builderYaml.includes('perMachine: false'), 'Must install per-user');
assert(builderYaml.includes('extraResources:'), 'Must configure extraResources for JRE and WAR');
console.log('  -> electron-builder.yml valid with data preservation confirmed.');

// 4. Verify Security Settings in main.js & preload.js
console.log('[4/7] Verifying Security Requirements (contextIsolation, sandbox, nodeIntegration)...');
const mainSrc = fs.readFileSync(mainPath, 'utf8');
assert(mainSrc.includes('contextIsolation: true'), 'contextIsolation must be enabled in main.js');
assert(mainSrc.includes('nodeIntegration: false'), 'nodeIntegration must be disabled in main.js');
assert(mainSrc.includes('sandbox: true'), 'sandbox must be enabled in main.js');
assert(mainSrc.includes('shell.openExternal'), 'External links must be routed to system browser');
assert(mainSrc.includes('app.requestSingleInstanceLock()'), 'Single instance lock must be requested');
assert(mainSrc.includes('dirs.dataDir'), 'Data directory must be resolved explicitly');
console.log('  -> Security requirements verified.');

// 5. Verify Preload Bridge Allowlist
console.log('[5/7] Verifying Preload Bridge...');
const preloadSrc = fs.readFileSync(preloadPath, 'utf8');
assert(preloadSrc.includes("contextBridge.exposeInMainWorld('rupeeDesktop'"), 'rupeeDesktop bridge must be exposed');
assert(preloadSrc.includes('ALLOWED_EXTERNAL_PROTOCOLS'), 'External protocols must be allowlisted');
console.log('  -> Preload bridge verified.');

// 6. Verify Customer Data Location
console.log('[6/7] Verifying Customer Data Isolation...');
assert(mainSrc.includes('path.join(root, \'data\')') || mainSrc.includes('LOCALAPPDATA'), 'Customer data must map to LocalAppData\\RupeeCRM\\data');
console.log('  -> Customer data path verified.');

// 7. Verify In-App Updater and Health Integration
console.log('[7/7] Verifying Health Monitoring & Updater Integration...');
assert(mainSrc.includes('/api/health'), 'Must poll /api/health');
assert(mainSrc.includes('did-fail-load'), 'Must handle temporary disconnects during updates gracefully');
console.log('  -> Health and updater integration verified.');

console.log('--- ALL ELECTRON CONFIGURATION CHECKS PASSED (7/7) ---');
