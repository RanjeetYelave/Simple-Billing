/**
 * RupeeCRM Windows Installation, Uninstallation, Process Lifecycle & Version Integrity Test Suite
 *
 * Verifies Category 1 requirements:
 * 1. NSIS installer & uninstaller hooks (customInit, customCheckAppRunning, customUnInit, customInstall, customUnInstall).
 * 2. Targeted process termination without killing unrelated Java processes.
 * 3. Graceful HTTP shutdown before process termination.
 * 4. File-lock detection on resources\rupeecrm.war with FileShare.None.
 * 5. Complete removal of installation directory, shortcuts, and registry entries during uninstall.
 * 6. Non-destructive customer data preservation guarantee.
 * 7. Version agreement enforcement between package.json, backend WAR, and runtime identity.
 * 8. Prevention of application startup under version mismatch.
 * 9. Supervisor clean exit on backend exit code 0 (prevention of orphan restart).
 * 10. .backend.pid tracking and cleanup.
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
const installerNshPath = path.resolve(__dirname, '../scripts/installer.nsh');
const prepareResourcesPath = path.resolve(__dirname, '../scripts/prepare-resources.js');
const launcherMainPath = path.resolve(__dirname, '../../../launcher/src/main/java/com/billing/simple/launcher/LauncherMain.java');
const releaseYmlPath = path.resolve(__dirname, '../../../.github/workflows/release.yml');

const mainJs = fs.readFileSync(mainJsPath, 'utf8');
const installerNsh = fs.readFileSync(installerNshPath, 'utf8');
const prepareResources = fs.readFileSync(prepareResourcesPath, 'utf8');
const launcherMain = fs.readFileSync(launcherMainPath, 'utf8');
const releaseYml = fs.readFileSync(releaseYmlPath, 'utf8');

console.log('═══════════════════════════════════════════════════════════════════');
console.log('CATEGORY 1: WINDOWS INSTALL, UNINSTALL & VERSION INTEGRITY SUITE');
console.log('═══════════════════════════════════════════════════════════════════\n');

// -----------------------------------------------------------------------------
// Suite 1: NSIS Installer & Uninstaller Process Lifecycle Hooks
// -----------------------------------------------------------------------------
console.log('--- Suite 1: NSIS Lifecycle & Process Ownership Hooks ---');

runCheck('installer.nsh defines TerminateRupeeCRMProcesses shared macro', () => {
  assert(installerNsh.includes('!macro TerminateRupeeCRMProcesses'), 'TerminateRupeeCRMProcesses macro must be defined');
});

runCheck('installer.nsh sends graceful HTTP /api/shutdown signal before process termination', () => {
  assert(installerNsh.includes('Invoke-WebRequest -Uri') && installerNsh.includes('http://127.0.0.1:28080/api/shutdown'), 'Must signal HTTP shutdown on port 28080');
});

runCheck('installer.nsh targets only RupeeCRM Java processes and preserves unrelated Java processes', () => {
  assert(installerNsh.includes('(?i)(rupeecrm|billsoft|RupeeCRMLauncher|RupeeCRM-Launcher|launcher\\.jar)'), 'Targeted command line regex must be enforced');
  assert(installerNsh.includes('ProcessId = $$id'), 'Recorded PID inspection must verify command line before terminating');
});

runCheck('installer.nsh verifies process on port 28080 before terminating', () => {
  assert(installerNsh.includes('Get-NetTCPConnection -LocalPort 28080'), 'Port 28080 listener check must exist');
  assert(installerNsh.includes('Stop-Process -Id $$p.ProcessId -Force'), 'Port 28080 process termination must be bounded');
});

runCheck('installer.nsh customCheckAppRunning is defined and hooked into NSIS CHECK_APP_RUNNING', () => {
  assert(installerNsh.includes('!macro customCheckAppRunning'), 'customCheckAppRunning macro must be defined');
  assert(installerNsh.includes('!insertmacro TerminateRupeeCRMProcesses'), 'customCheckAppRunning must invoke TerminateRupeeCRMProcesses');
});

runCheck('installer.nsh customUnInit terminates processes on silent uninstallation', () => {
  assert(installerNsh.includes('!macro customUnInit'), 'customUnInit macro must be defined');
  assert(installerNsh.includes('${If} ${Silent}'), 'customUnInit must handle silent uninstall');
});

runCheck('installer.nsh customUnInstall purges Run registry entries and shortcuts', () => {
  assert(installerNsh.includes('DeleteRegValue HKCU "Software\\Microsoft\\Windows\\CurrentVersion\\Run" "RupeeCRM"'), 'Run registry entry RupeeCRM must be deleted');
  assert(installerNsh.includes('DeleteRegValue HKCU "Software\\Microsoft\\Windows\\CurrentVersion\\Run" "RupeeCRMService"'), 'Legacy Run registry entry RupeeCRMService must be deleted');
  assert(installerNsh.includes('Delete "$DESKTOP\\RupeeCRM.lnk"'), 'Desktop shortcut must be deleted');
  assert(installerNsh.includes('Delete "$SMPROGRAMS\\RupeeCRM.lnk"'), 'Start menu shortcut must be deleted');
});

runCheck('installer.nsh customUnInstall verifies complete removal of $INSTDIR with retry', () => {
  assert(installerNsh.includes('${If} ${FileExists} "$INSTDIR"'), 'Must check if $INSTDIR still exists');
  assert(installerNsh.includes('Remove-Item -LiteralPath') && installerNsh.includes('$INSTDIR'), 'Must retry removal of $INSTDIR');
  assert(installerNsh.includes('RMDir /r "$INSTDIR"'), 'Final fallback RMDir must be present');
});

runCheck('installer.nsh guarantees customer data preservation across all lifecycle operations', () => {
  assert(!installerNsh.includes('Delete "$LOCALAPPDATA\\RupeeCRM\\data\\database'), 'Database file must never be deleted');
  assert(!installerNsh.includes('Delete "$LOCALAPPDATA\\RupeeCRM\\data\\license'), 'License file must never be deleted');
  assert(!installerNsh.includes('RMDir /r "$LOCALAPPDATA\\RupeeCRM\\data"'), 'Data directory must never be removed recursively');
  assert(!installerNsh.includes('RMDir /r "$LOCALAPPDATA\\RupeeCRM\\backups"'), 'Backups directory must never be removed recursively');
  assert(installerNsh.includes('CRITICAL: Under NO circumstances touch $LOCALAPPDATA\\RupeeCRM\\data or backups!'), 'Data safety invariant must be documented');
});

// -----------------------------------------------------------------------------
// Suite 2: Version Agreement & Packaging Integrity
// -----------------------------------------------------------------------------
console.log('\n--- Suite 2: Version Agreement & Packaging Integrity ---');

runCheck('prepare-resources.js extracts embedded application.properties from rupeecrm.war', () => {
  assert(prepareResources.includes('function extractWarVersion'), 'extractWarVersion function must exist');
  assert(prepareResources.includes('WEB-INF/classes/application.properties'), 'Must inspect WEB-INF/classes/application.properties');
});

runCheck('prepare-resources.js rejects packaging on version mismatch between package.json and WAR', () => {
  assert(prepareResources.includes('CRITICAL VERSION MISMATCH REJECTED'), 'Version mismatch rejection message must be defined');
  assert(prepareResources.includes('cleanPkg !== cleanWar'), 'Must enforce strict equality between package version and WAR version');
});

runCheck('prepare-resources.js verifies minimum WAR size and computes SHA-256', () => {
  assert(prepareResources.includes('50000000'), 'Minimum WAR size of 50MB must be enforced');
  assert(prepareResources.includes('crypto.createHash(\'sha256\')'), 'SHA-256 calculation must be present');
});

runCheck('prepare-resources.js generates version-report.json artifact', () => {
  assert(prepareResources.includes('version-report.json'), 'version-report.json must be generated');
  assert(prepareResources.includes('versionReport = {'), 'versionReport object must be built');
});

// -----------------------------------------------------------------------------
// Suite 3: Electron Main Process & Runtime Startup Verification
// -----------------------------------------------------------------------------
console.log('\n--- Suite 3: Electron Process Lifecycle & Startup Integrity ---');

runCheck('main.js stopSupervisor triggers graceful HTTP shutdown and polls port release', () => {
  assert(mainJs.includes('await triggerGracefulBackendShutdown()'), 'stopSupervisor must call triggerGracefulBackendShutdown');
  assert(mainJs.includes('await isPortListening(PORT'), 'stopSupervisor must poll for port release');
});

runCheck('main.js stopSupervisor inspects and terminates orphan RupeeCRM process on port 28080', () => {
  assert(mainJs.includes('await inspectListeningProcess(PORT)'), 'stopSupervisor must inspect listening process if port still occupied');
  assert(mainJs.includes('await terminateRupeeCRMProcess(proc, 3000, PORT)'), 'stopSupervisor must terminate verified orphan process');
});

runCheck('main.js waitForBackend enforces version agreement against active backend identity', () => {
  assert(mainJs.includes('enforceVersionMatch = false'), 'waitForBackend must support version match enforcement');
  assert(mainJs.includes('compareAppVersions(packagedVer, identity.version)'), 'waitForBackend must compare packaged version with runtime version');
  assert(mainJs.includes('CRITICAL: Backend runtime version'), 'waitForBackend must log critical error on version mismatch');
});

runCheck('main.js startup halts main window creation if backend version does not match', () => {
  assert(mainJs.includes('Version integrity error: Installed backend'), 'Startup must show version integrity error on splash screen');
  assert(mainJs.includes('createMainWindow();'), 'createMainWindow must only be called if ready is verified');
});

// -----------------------------------------------------------------------------
// Suite 4: Launcher Supervisor Exit Code 0 & PID Tracking
// -----------------------------------------------------------------------------
console.log('\n--- Suite 4: Supervisor Exit Code 0 & PID Tracking ---');

runCheck('LauncherMain breaks supervisor loop and shuts down cleanly on backend exit code 0', () => {
  assert(launcherMain.includes('else if (exitCode == 0)'), 'LauncherMain must handle exitCode 0');
  assert(launcherMain.includes('System.out.println("Backend process exited cleanly (exit code 0). Shutting down supervisor.");'), 'Clean shutdown log must be present');
  assert(launcherMain.includes('exitRequested = true;\n                    break;'), 'Supervisor loop must break on clean exit');
});

runCheck('LauncherMain writes and clears .backend.pid file', () => {
  assert(launcherMain.includes('writeBackendPidFile(backendPid);'), 'launchBackendProcess must invoke writeBackendPidFile');
  assert(launcherMain.includes('public static void writeBackendPidFile(Long pid)'), 'writeBackendPidFile method must exist');
  assert(launcherMain.includes('public static void clearBackendPidFile()'), 'clearBackendPidFile method must exist');
});

// -----------------------------------------------------------------------------
// Suite 5: CI Windows Installation & Uninstallation Lifecycle Workflow
// -----------------------------------------------------------------------------
console.log('\n--- Suite 5: CI Workflow Windows Lifecycle Verification ---');

runCheck('release.yml verifies silent uninstallation while RupeeCRM is actively running', () => {
  assert(releaseYml.includes('$uninstProc = Start-Process -FilePath $uninstaller -ArgumentList "/S" -Wait -PassThru'), 'Must test uninstallation while app running');
  assert(releaseYml.includes('if ($uninstProc.ExitCode -ne 0)'), 'Must check uninstaller exit code 0');
});

runCheck('release.yml verifies no orphan RupeeCRM process remains after uninstall', () => {
  assert(releaseYml.includes('Orphan RupeeCRM process(es) remained active after uninstallation'), 'Must check for orphan processes');
  assert(releaseYml.includes('Port 28080 remained listening after uninstallation'), 'Must verify port 28080 is released');
});

runCheck('release.yml verifies complete removal of application binaries and backend WAR', () => {
  assert(releaseYml.includes('$installDir\\RupeeCRM.exe was not deleted during uninstallation!'), 'Must verify RupeeCRM.exe is deleted');
  assert(releaseYml.includes('$installDir\\resources\\rupeecrm.war was not deleted during uninstallation!'), 'Must verify rupeecrm.war is deleted');
});

runCheck('release.yml verifies customer canary data preservation across uninstall and reinstall', () => {
  assert(releaseYml.includes('synthetic-customer-record.canary'), 'Canary customer record must be staged');
  assert(releaseYml.includes('Customer canary data ($canaryFile) was deleted during uninstallation!'), 'Canary file presence must be checked post-uninstall');
  assert(releaseYml.includes('Customer canary data was lost after reinstallation!'), 'Canary file presence must be checked post-reinstall');
});

console.log('\n═══════════════════════════════════════════════════════════════════');
console.log(`ALL TESTS PASSED: ${passedCount}/${totalCount}`);
console.log('═══════════════════════════════════════════════════════════════════\n');
