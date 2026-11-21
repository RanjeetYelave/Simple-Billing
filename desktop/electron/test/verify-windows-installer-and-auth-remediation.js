/**
 * RupeeCRM Windows Installer, Supervisor Recovery & Manual Update Auth Remediation Test Suite
 */

const fs = require('fs');
const path = require('path');
const assert = require('assert');

let totalCount = 0;
let passedCount = 0;

function runCheck(desc, fn) {
  totalCount++;
  try {
    fn();
    passedCount++;
    console.log(`  ✓ [PASS] ${totalCount}: ${desc}`);
  } catch (err) {
    console.error(`  ✗ [FAIL] ${totalCount}: ${desc}`);
    console.error(`    -> ${err.message}`);
    process.exitCode = 1;
  }
}

const mainJsPath = path.resolve(__dirname, '../src/main.js');
const preloadJsPath = path.resolve(__dirname, '../src/preload.js');
const installerNshPath = path.resolve(__dirname, '../scripts/installer.nsh');
const indexHtmlPath = path.resolve(__dirname, '../../../billsoft/src/main/webapp/index.html');
const launcherMainPath = path.resolve(__dirname, '../../../launcher/src/main/java/com/billing/simple/launcher/LauncherMain.java');
const updateServicePath = path.resolve(__dirname, '../../../billsoft/src/main/java/com/billing/simple/billsoft/service/UpdateService.java');

const mainJs = fs.readFileSync(mainJsPath, 'utf8');
const preloadJs = fs.readFileSync(preloadJsPath, 'utf8');
const installerNsh = fs.readFileSync(installerNshPath, 'utf8');
const indexHtml = fs.readFileSync(indexHtmlPath, 'utf8');
const launcherMain = fs.readFileSync(launcherMainPath, 'utf8');
const updateService = fs.readFileSync(updateServicePath, 'utf8');

console.log('═══════════════════════════════════════════════════════════════════');
console.log('RUPEECRM WINDOWS INSTALLER & AUTHENTICATION REMEDIATION SUITE');
console.log('═══════════════════════════════════════════════════════════════════\n');

console.log('--- Suite 1: Manual Update Authentication Token Propagation ---');

runCheck('main.js defines session token tracking and sendBackendRequest attaches headers', () => {
  assert(mainJs.includes('let currentSessionToken = null;'), 'currentSessionToken variable must exist');
  assert(mainJs.includes("headers['Authorization'] = `Bearer ${token}`") || mainJs.includes("headers['Authorization'] = 'Bearer ' + token"), 'Authorization Bearer header must be set');
  assert(mainJs.includes("headers['X-Auth-Token'] = token"), 'X-Auth-Token header must be set');
});

runCheck('main.js registers session:set-token listener', () => {
  assert(mainJs.includes("ipcMain.on('session:set-token'"), 'session:set-token IPC listener must be registered');
});

runCheck('preload.js exposes setSessionToken bridge function', () => {
  assert(preloadJs.includes('setSessionToken:'), 'setSessionToken must be exposed in preload.js');
  assert(preloadJs.includes("ipcRenderer.send('session:set-token'"), 'setSessionToken must send session:set-token IPC message');
});

runCheck('main.js updater:select-manual-package captures token and passes it to backend request', () => {
  assert(mainJs.includes("const token = options && (options.token || options.authToken);"), 'select-manual-package must extract token');
  assert(mainJs.includes('token\n      );') || mainJs.includes('token);') || mainJs.includes('token\r\n      );'), 'sendBackendRequest must receive token parameter');
});

runCheck('index.html handleSelectManualPackage passes active session token', () => {
  assert(indexHtml.includes('const authToken = (typeof API !== \'undefined\' && API && API.token)'), 'index.html must resolve active auth token');
  assert(indexHtml.includes('selectManualPackage({ confirmReplace, token: authToken })'), 'selectManualPackage must receive token: authToken');
});

console.log('\n--- Suite 2: Windows Installer Synchronous Process & File-Lock Testing ---');

runCheck('installer.nsh customInit synchronously polls for process termination', () => {
  assert(installerNsh.includes('for ($$i=0; $$i -lt 20; $$i++)'), 'customInit must contain polling loop');
  assert(installerNsh.includes('RupeeCRM.exe'), 'customInit must check for RupeeCRM.exe termination');
});

runCheck('installer.nsh customInit actively tests rupeecrm.war file release with FileShare.None', () => {
  assert(installerNsh.includes('[System.IO.File]::Open($$f, [System.IO.FileMode]::Open, [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)'),
    'installer.nsh must test exclusive file write share before extraction');
});

runCheck('installer.nsh customInstall purges all stale staging transaction artifacts', () => {
  assert(installerNsh.includes('Delete "$LOCALAPPDATA\\RupeeCRM\\staging\\update-tx\\rupeecrm-update.war.part"'), 'Staging part file must be purged');
  assert(installerNsh.includes('Delete "$LOCALAPPDATA\\RupeeCRM\\staging\\update-tx\\manifest.json"'), 'Staging manifest must be purged');
  assert(installerNsh.includes('Delete "$LOCALAPPDATA\\RupeeCRM\\staging\\update.lock"'), 'Staging lock must be purged');
});

runCheck('installer.nsh customInstall verifies core backend WAR post-extraction', () => {
  assert(installerNsh.includes('${If} ${FileExists} "$INSTDIR\\resources\\rupeecrm.war"'), 'Post-extract WAR existence check must be present');
  assert(installerNsh.includes('MessageBox MB_ICONSTOP|MB_OK'), 'Failure dialog must be shown if WAR is missing');
  assert(installerNsh.includes('Abort'), 'Installer must abort rather than claim false success');
});

console.log('\n--- Suite 3: Supervisor Port Clearance & Startup Diagnostic Logging ---');

runCheck('LauncherMain implements isPortAvailable and ensurePortAvailable', () => {
  assert(launcherMain.includes('public static boolean isPortAvailable(int port)'), 'isPortAvailable helper must exist');
  assert(launcherMain.includes('ensurePortAvailable(PORT);'), 'launchBackendProcess must invoke ensurePortAvailable before starting process');
});

runCheck('LauncherMain dumps stderr log lines when child process exits unexpectedly', () => {
  assert(launcherMain.includes('logRecentStderrLines()'), 'waitForBackend must invoke logRecentStderrLines on premature process termination');
  assert(launcherMain.includes('backend-stderr.log'), 'logRecentStderrLines must inspect backend-stderr.log');
});

runCheck('LauncherMain verifyRunningCandidateVersion queries runtime-identity endpoint', () => {
  assert(launcherMain.includes('/api/system/runtime-identity'), 'verifyRunningCandidateVersion must query runtime-identity');
});

console.log('\n--- Suite 4: OTA Transaction Metadata Consistency ---');

runCheck('UpdateService purges manifest.json and manifest.sig on new transaction', () => {
  assert(updateService.includes('Files.deleteIfExists(txDir.resolve("manifest.json"));'), 'txDir manifest.json must be deleted on new transaction');
  assert(updateService.includes('Files.deleteIfExists(txDir.resolve("manifest.sig"));'), 'txDir manifest.sig must be deleted on new transaction');
});

runCheck('UpdateService getStagedUpdateInfo checks manifest version against transaction target', () => {
  assert(updateService.includes('!manifestVer.equals(targetVer)'), 'Staged manifest version mismatch must be detected');
  assert(updateService.includes('ignoring corrupt staged update'), 'Corrupt staged update must be logged and rejected');
});

runCheck('UpdateService installStagedUpdate validates manifest and WAR hash before creating handoff', () => {
  assert(updateService.includes('!manifestVer.equals(state.getTargetVersion())'), 'installStagedUpdate must validate manifest version');
  assert(updateService.includes('!state.getExpectedSha256().equalsIgnoreCase(actualWarSha)'), 'installStagedUpdate must validate WAR hash');
});

console.log('\n═══════════════════════════════════════════════════════════════════');
console.log(`ALL TESTS PASSED: ${passedCount}/${totalCount}`);
console.log('═══════════════════════════════════════════════════════════════════\n');
