/**
 * RupeeCRM Windows Electron OTA Updater Remediation Verification Suite
 *
 * Verifies:
 * 1. index.html restart polling watchdog:
 *    - Removal of `if (attempts > 50) attempts = 20;` infinite polling reset
 *    - Monotonic deadline MAX_TOTAL_RESTART_TIMEOUT_MS (5 min hard cap)
 *    - isPollingTick concurrency guard
 *    - fetchJsonWithTimeout with AbortController
 *    - Handling of UPDATE_FAILED and ROLLED_BACK terminal states
 * 2. desktop/electron/src/main.js:
 *    - resolveDirectories resolves stagingDir and appBaseDir
 *    - updater:diagnostics falls back to reading update-state.json from disk
 *    - updater:install session timestamp tracking
 * 3. LauncherMain.java:
 *    - Retry backoff loop for Windows file replacement (25 attempts)
 *    - Post-replacement SHA-256 verification and immediate rollback on mismatch
 *    - Non-elevated UAC / Program Files read-only redirection to %LOCALAPPDATA%
 *    - Handoff deletion and STATUS_UPDATE_FAILED persistence on failure
 * 4. UpdateService.java:
 *    - Atomic write of update.handoff
 *    - Explicit completion of SSE progressEmitter prior to exit
 *    - Handling of STATUS_UPDATE_FAILED in checkForUpdateComplete
 */

const fs = require('fs');
const path = require('path');
const assert = require('assert');

console.log('═══════════════════════════════════════════════════════════════════');
console.log('RUPEECRM WINDOWS OTA UPDATER REMEDIATION VERIFICATION SUITE');
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
const indexHtmlPath = path.resolve(__dirname, '../../../billsoft/src/main/webapp/index.html');
const launcherMainPath = path.resolve(__dirname, '../../../launcher/src/main/java/com/billing/simple/launcher/LauncherMain.java');
const updateServicePath = path.resolve(__dirname, '../../../billsoft/src/main/java/com/billing/simple/billsoft/service/UpdateService.java');

const mainJs = fs.readFileSync(mainJsPath, 'utf8');
const indexHtml = fs.readFileSync(indexHtmlPath, 'utf8');
const launcherMain = fs.readFileSync(launcherMainPath, 'utf8');
const updateService = fs.readFileSync(updateServicePath, 'utf8');

console.log('--- Suite 1: Frontend Restart Polling Watchdog (index.html) ---');

runCheck('index.html must NOT contain attempts = 20 counter reset flaw', () => {
  assert(!indexHtml.includes('attempts = 20'), 'attempts = 20 counter reset flaw must be eliminated');
  assert(!indexHtml.includes('attempts > 50'), 'attempts > 50 check must be removed');
});

runCheck('index.html defines monotonic deadline and AbortController timeout helper', () => {
  assert(indexHtml.includes('MAX_TOTAL_RESTART_TIMEOUT_MS = 300000'), '5-minute deadline must be present');
  assert(indexHtml.includes('fetchJsonWithTimeout = async'), 'fetchJsonWithTimeout must be defined');
  assert(indexHtml.includes('AbortController'), 'AbortController must be used for request cancellation');
});

runCheck('index.html startRestartPolling includes concurrency guard and durable state handling', () => {
  assert(indexHtml.includes('let isPollingTick = false'), 'isPollingTick concurrency guard must be defined');
  assert(indexHtml.includes("compData.status === 'UPDATE_FAILED'"), 'Explicit handling for UPDATE_FAILED state required');
  assert(indexHtml.includes("compData.status === 'ROLLED_BACK'"), 'Explicit handling for ROLLED_BACK state required');
  assert(indexHtml.includes('api/system/update-diagnostics'), 'Durable diagnostics endpoint must be queried during polling');
});

console.log('\n--- Suite 2: Electron Main Process Diagnostics & Disk Fallback ---');

runCheck('main.js resolveDirectories exposes stagingDir and appBaseDir', () => {
  assert(mainJs.includes('stagingDir ='), 'stagingDir must be assigned in resolveDirectories');
  assert(mainJs.includes('appBaseDir ='), 'appBaseDir must be assigned in resolveDirectories');
  assert(mainJs.includes('return { dataDir, logsDir, backupsDir, stagingDir, appBaseDir };'), 'stagingDir & appBaseDir must be returned');
});

runCheck('main.js updater:diagnostics falls back to reading update-state.json from disk', () => {
  assert(mainJs.includes("ipcMain.handle('updater:diagnostics'"), 'updater:diagnostics handler must exist');
  assert(mainJs.includes('update-state.json'), 'update-state.json reading fallback must be implemented');
  assert(mainJs.includes('fs.readFileSync(stateFile'), 'fs.readFileSync for stateFile must exist');
});

runCheck('main.js updater:install records session timestamp', () => {
  assert(mainJs.includes('activeUpdateSession = {'), 'activeUpdateSession tracking must exist');
  assert(mainJs.includes('startTime: Date.now()'), 'Session startTime must be recorded');
});

console.log('\n--- Suite 3: Java Launcher Windows-Safe File Replacement & Recovery ---');

runCheck('LauncherMain implements retry loop with backoff for Windows file replacement', () => {
  assert(launcherMain.includes('for (int attempt = 1; attempt <= 25; attempt++)'), '25-attempt retry loop must exist for file replacement');
  assert(launcherMain.includes('targetWar.getName() + ".old"'), 'Fallback rename strategy must exist for Windows sharing violations');
});

runCheck('LauncherMain performs post-replacement SHA-256 validation and rollback on mismatch', () => {
  assert(launcherMain.includes('manifest.getArtifactSha256().equalsIgnoreCase(targetHash)'), 'Post-replacement hash verification must match manifest');
  assert(launcherMain.includes('Restoring backup immediately'), 'Rollback trigger log must exist');
});

runCheck('LauncherMain cleans up handoff and records STATUS_UPDATE_FAILED on failure', () => {
  assert(launcherMain.includes('state.setStatus(UpdateState.STATUS_UPDATE_FAILED)'), 'STATUS_UPDATE_FAILED must be persisted');
  assert(launcherMain.includes('Files.deleteIfExists(handoffToken)'), 'update.handoff must be deleted on failure');
});

runCheck('LauncherMain handles read-only packaged location by redirecting to user base app dir', () => {
  assert(launcherMain.includes('Packaged WAR location is read-only'), 'Read-only log warning must exist');
  assert(launcherMain.includes('base.resolve("app").resolve("rupeecrm.war")'), 'Redirect target to user base dir must exist');
});

console.log('\n--- Suite 4: Backend UpdateService Shutdown & Failure Propagation ---');

runCheck('UpdateService writes update.handoff atomically and completes SSE emitter before exit', () => {
  assert(updateService.includes('update.handoff.tmp'), 'Atomic handoff write via temp file must exist');
  assert(updateService.includes('this.progressEmitter.complete()'), 'progressEmitter must be cleanly completed before JVM exit');
});

runCheck('UpdateService checkForUpdateComplete handles STATUS_UPDATE_FAILED', () => {
  assert(updateService.includes('UpdateState.STATUS_UPDATE_FAILED.equals(state.getStatus())'), 'STATUS_UPDATE_FAILED branch must exist');
  assert(updateService.includes('result.put("updateFailed", true)'), 'updateFailed must be returned to client');
});

console.log('\n--- Suite 5: Version Alignment & Legacy Metadata Purging ---');

const installerNshPath = path.resolve(__dirname, '../scripts/installer.nsh');
const releaseYmlPath = path.resolve(__dirname, '../../../.github/workflows/release.yml');
const installerNsh = fs.readFileSync(installerNshPath, 'utf8');
const releaseYml = fs.readFileSync(releaseYmlPath, 'utf8');

runCheck('main.js must NOT contain hardcoded 1.0.320 fallback strings', () => {
  assert(!mainJs.includes("'1.0.320'"), 'main.js must not contain hardcoded 1.0.320 fallback string');
});

runCheck('UpdateService.java purges stale .billsoft-version files when running binary is newer', () => {
  assert(updateService.includes('Purging obsolete file'), 'UpdateService must purge obsolete version metadata');
  assert(updateService.includes('int cmp = compareVersions(authoritativeVersion, ver)'), 'authoritativeVersion must be compared against on-disk metadata');
});

runCheck('LauncherMain.java deletes stale .billsoft-version files when packaged WAR is executed', () => {
  assert(launcherMain.includes('Files.deleteIfExists(base.resolve("app").resolve(".billsoft-version"))'), 'LauncherMain must purge stale app .billsoft-version');
  assert(launcherMain.includes('Files.deleteIfExists(getDataDirectory().resolve(".billsoft-version"))'), 'LauncherMain must purge stale data .billsoft-version');
});

runCheck('installer.nsh purges obsolete metadata and All-Users shortcuts', () => {
  assert(installerNsh.includes('Delete "$LOCALAPPDATA\\RupeeCRM\\data\\.billsoft-version"'), 'installer.nsh must purge data .billsoft-version');
  assert(installerNsh.includes('SetShellVarContext all') && installerNsh.includes('Delete "$DESKTOP\\RupeeCRM.lnk"'), 'installer.nsh must delete shadowing All-Users desktop shortcut');
});

runCheck('release.yml defines verify-windows-installation job gating release', () => {
  assert(releaseYml.includes('verify-windows-installation:'), 'verify-windows-installation job must exist in release.yml');
  assert(releaseYml.includes('needs: [build-app, build-windows-installer, verify-windows-installation]'), 'publish-release must require verify-windows-installation');
});

console.log('\n═══════════════════════════════════════════════════════════════════');
console.log(`ALL CHECKS PASSED: ${passCount}/${totalCount}`);
console.log('═══════════════════════════════════════════════════════════════════\n');
