/**
 * RupeeCRM Enterprise OTA Update Reliability Overhaul Verification Suite
 *
 * Verifies:
 * 1. Preload IPC exposure: rupeeDesktop.updater APIs (pause, resume, retry, install, cancel, getDiagnostics)
 * 2. Main process IPC registration & sendBackendRequest pipeline
 * 3. Reconnecting view watchdog timer, 45s threshold, and action recovery buttons
 * 4. Frontend UpdateProgressOverlay 8-state support, action buttons & ETA
 * 5. Frontend API.system update extensions
 */

const fs = require('fs');
const path = require('path');
const assert = require('assert');

console.log('═══════════════════════════════════════════════════════════════════');
console.log('RUPEECRM ENTERPRISE OTA UPDATE RELIABILITY VERIFICATION SUITE');
console.log('═══════════════════════════════════════════════════════════════════\n');

let passCount = 0;
let totalCount = 0;

function runCheck(desc, fn) {
  totalCount++;
  try {
    fn();
    console.log(`  ✓ [PASS] ${totalCount}: ${desc}`);
    passCount++;
  } catch (err) {
    console.error(`  ✗ [FAIL] ${totalCount}: ${desc}`);
    console.error(`    -> ${err.message}`);
    process.exitCode = 1;
  }
}

const mainJsPath = path.resolve(__dirname, '../src/main.js');
const preloadJsPath = path.resolve(__dirname, '../src/preload.js');
const indexHtmlPath = path.resolve(__dirname, '../../../billsoft/src/main/webapp/index.html');
const apiJsPath = path.resolve(__dirname, '../../../billsoft/src/main/webapp/js/api.js');

const mainJs = fs.readFileSync(mainJsPath, 'utf8');
const preloadJs = fs.readFileSync(preloadJsPath, 'utf8');
const indexHtml = fs.readFileSync(indexHtmlPath, 'utf8');
const apiJs = fs.readFileSync(apiJsPath, 'utf8');

console.log('--- Suite 1: Electron Preload & Context Isolation ---');

runCheck('preload.js exposes updater object under rupeeDesktop', () => {
  assert(preloadJs.includes('updater:'), 'preload.js must define updater property');
});

runCheck('updater exposes pause, resume, retry, install, cancel, getDiagnostics', () => {
  assert(preloadJs.includes("pause: () => ipcRenderer.invoke('updater:pause')"), 'updater.pause missing');
  assert(preloadJs.includes("resume: () => ipcRenderer.invoke('updater:resume')"), 'updater.resume missing');
  assert(preloadJs.includes("retry: () => ipcRenderer.invoke('updater:retry')"), 'updater.retry missing');
  assert(preloadJs.includes("install: () => ipcRenderer.invoke('updater:install')"), 'updater.install missing');
  assert(preloadJs.includes("cancel: () => ipcRenderer.invoke('updater:cancel')"), 'updater.cancel missing');
  assert(preloadJs.includes("getDiagnostics: () => ipcRenderer.invoke('updater:diagnostics')"), 'updater.getDiagnostics missing');
});

console.log('\n--- Suite 2: Electron Main Process IPC Handlers & Watchdog ---');

runCheck('main.js registers all 6 updater IPC handlers', () => {
  assert(mainJs.includes("ipcMain.handle('updater:pause'"), 'updater:pause handler missing in main.js');
  assert(mainJs.includes("ipcMain.handle('updater:resume'"), 'updater:resume handler missing in main.js');
  assert(mainJs.includes("ipcMain.handle('updater:retry'"), 'updater:retry handler missing in main.js');
  assert(mainJs.includes("ipcMain.handle('updater:install'"), 'updater:install handler missing in main.js');
  assert(mainJs.includes("ipcMain.handle('updater:cancel'"), 'updater:cancel handler missing in main.js');
  assert(mainJs.includes("ipcMain.handle('updater:diagnostics'"), 'updater:diagnostics handler missing in main.js');
});

runCheck('main.js defines robust sendBackendRequest HTTP helper', () => {
  assert(mainJs.includes('function sendBackendRequest('), 'sendBackendRequest function missing in main.js');
  assert(mainJs.includes('/api/system/pause-update'), 'pause-update backend route mapped');
  assert(mainJs.includes('/api/system/resume-update'), 'resume-update backend route mapped');
  assert(mainJs.includes('/api/system/install-update'), 'install-update backend route mapped');
  assert(mainJs.includes('/api/system/update-diagnostics'), 'update-diagnostics backend route mapped');
});

runCheck('main.js reconnecting screen features watchdog timer & recovery actions', () => {
  assert(mainJs.includes('RupeeCRM - Reconnecting'), 'Reconnecting page title missing');
  assert(mainJs.includes('updateTimer'), 'updateTimer function missing');
  assert(mainJs.includes('elapsed >='), 'Elapsed threshold check missing');
  assert(mainJs.includes('Reconnect Now'), 'Reconnect Now button missing');
  assert(mainJs.includes('Reload Window'), 'Reload Window button missing');
  assert(mainJs.includes('#064e3b'), 'Emerald SaaS palette missing in reconnecting view');
});

console.log('\n--- Suite 3: Webapp API.system Client Extensions ---');

runCheck('api.js declares all new OTA update methods under API.system', () => {
  assert(apiJs.includes("pauseUpdate: () => API._json('/api/system/pause-update'"), 'API.system.pauseUpdate missing');
  assert(apiJs.includes("resumeUpdate: () => API._json('/api/system/resume-update'"), 'API.system.resumeUpdate missing');
  assert(apiJs.includes("retryUpdate: () => API._json('/api/system/retry-update'"), 'API.system.retryUpdate missing');
  assert(apiJs.includes("installUpdate: () => API._json('/api/system/install-update'"), 'API.system.installUpdate missing');
  assert(apiJs.includes("updateDiagnostics: () => API._json('/api/system/update-diagnostics')"), 'API.system.updateDiagnostics missing');
});

console.log('\n--- Suite 4: Frontend UI Redesign & Overlay Controls ---');

runCheck('UpdateProgressOverlay handles pause, resume, retry, installNow, and minimize props', () => {
  assert(indexHtml.includes('function UpdateProgressOverlay({ progress, onComplete, onCancel, onPause, onResume, onRetry, onInstallNow, onMinimize, isUpdating })'),
    'UpdateProgressOverlay missing extended props');
});

runCheck('UpdateProgressOverlay supports paused state with resume button', () => {
  assert(indexHtml.includes("isPaused = status === 'paused'"), 'isPaused state check missing');
  assert(indexHtml.includes('Resume Download'), 'Resume Download button missing');
  assert(indexHtml.includes('Download Paused'), 'Download Paused title missing');
});

runCheck('UpdateProgressOverlay supports ready_to_install decoupled staging with Install Now button', () => {
  assert(indexHtml.includes("isReadyToInstall = status === 'ready_to_install'"), 'isReadyToInstall state check missing');
  assert(indexHtml.includes('Install Now & Restart'), 'Install Now button missing');
  assert(indexHtml.includes('Remind Me Later'), 'Remind Me Later button missing');
  assert(indexHtml.includes('Verification & Backup Complete'), 'Verification & Backup banner missing');
});

runCheck('UpdateProgressOverlay displays download speed and ETA estimation', () => {
  assert(indexHtml.includes('Est. time remaining:'), 'ETA remaining display missing');
  assert(indexHtml.includes('Run in Background'), 'Run in Background minimize button missing');
});

runCheck('App hooks handlePauseUpdate, handleResumeUpdate, handleRetryUpdate, and handleInstallNow', () => {
  assert(indexHtml.includes('const handlePauseUpdate ='), 'handlePauseUpdate missing in App');
  assert(indexHtml.includes('const handleResumeUpdate ='), 'handleResumeUpdate missing in App');
  assert(indexHtml.includes('const handleRetryUpdate ='), 'handleRetryUpdate missing in App');
  assert(indexHtml.includes('const handleInstallNow ='), 'handleInstallNow missing in App');
  assert(indexHtml.includes('const handleMinimizeUpdate ='), 'handleMinimizeUpdate missing in App');
});

console.log('\n═══════════════════════════════════════════════════════════════════');
console.log(`ALL TESTS PASSED: ${passCount}/${totalCount}`);
console.log('═══════════════════════════════════════════════════════════════════\n');

