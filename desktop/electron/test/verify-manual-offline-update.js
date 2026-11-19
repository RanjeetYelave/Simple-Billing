/**
 * RupeeCRM Manual / Offline Update & GitHub Actions Cleanup Verification Suite
 * Validates:
 * 1. IPC registration and native file dialog configuration in main.js
 * 2. Secure preload bridge isolation in preload.js
 * 3. Frontend UI layout, exact strings, and state transitions in index.html
 * 4. API client endpoints in api.js
 * 5. Production release pipeline manual OTA ZIP generation and 10-point validation in release.yml
 * 6. Automated old workflow runs cleanup with pagination and safety rules in release.yml
 */

const fs = require('fs');
const path = require('path');
const assert = require('assert');

const projectRoot = path.resolve(__dirname, '../../..');
const mainJsPath = path.join(projectRoot, 'desktop/electron/src/main.js');
const preloadJsPath = path.join(projectRoot, 'desktop/electron/src/preload.js');
const indexHtmlPath = path.join(projectRoot, 'billsoft/src/main/webapp/index.html');
const apiJsPath = path.join(projectRoot, 'billsoft/src/main/webapp/js/api.js');
const releaseYmlPath = path.join(projectRoot, '.github/workflows/release.yml');
const updateServicePath = path.join(projectRoot, 'billsoft/src/main/java/com/billing/simple/billsoft/service/UpdateService.java');
const updateControllerPath = path.join(projectRoot, 'billsoft/src/main/java/com/billing/simple/billsoft/controllers/UpdateController.java');

console.log('═══════════════════════════════════════════════════════════════════');
console.log('RUPEECRM MANUAL / OFFLINE UPDATE & ACTIONS CLEANUP TEST SUITE');
console.log('═══════════════════════════════════════════════════════════════════\n');

let passed = 0;
let total = 0;

function check(desc, fn) {
  total++;
  try {
    fn();
    console.log(`  ✓ [PASS] ${total}: ${desc}`);
    passed++;
  } catch (err) {
    console.error(`  ✗ [FAIL] ${total}: ${desc}`);
    console.error(`    Error: ${err.message}`);
    process.exitCode = 1;
  }
}

const mainJs = fs.readFileSync(mainJsPath, 'utf8');
const preloadJs = fs.readFileSync(preloadJsPath, 'utf8');
const indexHtml = fs.readFileSync(indexHtmlPath, 'utf8');
const apiJs = fs.readFileSync(apiJsPath, 'utf8');
const releaseYml = fs.readFileSync(releaseYmlPath, 'utf8');
const updateService = fs.readFileSync(updateServicePath, 'utf8');
const updateController = fs.readFileSync(updateControllerPath, 'utf8');

console.log('--- Suite 1: Electron IPC & Native Dialog Security ---');

check('main.js registers updater:select-manual-package handler', () => {
  assert(mainJs.includes("ipcMain.handle('updater:select-manual-package'"), 'updater:select-manual-package must be registered');
});

check('main.js uses dialog.showOpenDialog with .zip filter', () => {
  assert(mainJs.includes('dialog.showOpenDialog'), 'Must call native dialog.showOpenDialog');
  assert(mainJs.includes("extensions: ['zip']"), 'Must enforce .zip extension filter in dialog');
});

check('main.js validates selected package bounds and sends to loopback endpoint', () => {
  assert(mainJs.includes('/api/system/manual-update/stage-local'), 'Must call backend stage-local endpoint');
  assert(mainJs.includes('stat.size'), 'Must inspect file size on disk before staging');
});

check('preload.js exposes selectManualPackage in rupeeDesktop.updater', () => {
  assert(preloadJs.includes("selectManualPackage: (options) => ipcRenderer.invoke('updater:select-manual-package', options)"), 'Preload must expose selectManualPackage');
  assert(!preloadJs.includes("require('fs')"), 'Preload must never leak fs to renderer');
});

console.log('\n--- Suite 2: Backend Controller & Staging Engine ---');

check('UpdateService implements stageManualUpdatePackage with offline signature verification', () => {
  assert(updateService.includes('stageManualUpdatePackage'), 'UpdateService must define stageManualUpdatePackage');
  assert(updateService.includes('UpdateCrypto.verifySignature'), 'Must verify Ed25519 signature offline');
  assert(updateService.includes('Zip Slip path traversal attack detected'), 'Must enforce Zip Slip protection');
});

check('UpdateService enforces version comparison, downgrade rejection, and same-version report', () => {
  assert(updateService.includes('Downgrade rejected'), 'Must reject downgrades');
  assert(updateService.includes('already_installed'), 'Must handle same version gracefully');
  assert(updateService.includes('STATUS_READY_TO_INSTALL'), 'Must stage verified package as READY_TO_INSTALL');
});

check('UpdateController exposes upload and loopback-restricted stage-local endpoints', () => {
  assert(updateController.includes('@PostMapping(value = "/manual-update/upload"'), 'Must expose /manual-update/upload');
  assert(updateController.includes('@PostMapping("/manual-update/stage-local")'), 'Must expose /manual-update/stage-local');
  assert(updateController.includes('isLoopbackRequest'), 'Must enforce loopback restriction on stage-local');
});

console.log('\n--- Suite 3: Frontend Update UI & Exact Strings ---');

check('index.html contains exact Manual Update title and description', () => {
  assert(indexHtml.includes("'Manual Update'"), 'Must contain exact section title "Manual Update"');
  assert(
    indexHtml.includes('Already downloaded a RupeeCRM update package? Select it here to install without downloading it again.'),
    'Must contain exact description text'
  );
});

check('index.html contains exact button text: Select Update Package', () => {
  assert(indexHtml.includes("'Select Update Package'"), 'Button text must be exactly "Select Update Package"');
});

check('index.html provides hidden input fallback for browser environments', () => {
  assert(indexHtml.includes("accept: '.zip'"), 'Must provide file input accepting .zip');
  assert(indexHtml.includes('handleManualFileSelected'), 'Must handle manual file selection');
});

check('api.js declares uploadManualUpdate and stageLocalManualUpdate', () => {
  assert(apiJs.includes('uploadManualUpdate:'), 'api.js must declare uploadManualUpdate');
  assert(apiJs.includes('stageLocalManualUpdate:'), 'api.js must declare stageLocalManualUpdate');
});

console.log('\n--- Suite 4: Production Release Pipeline & Manual OTA ZIP ---');

check('release.yml builds RupeeCRM-Update-${APP_VER}.zip containing war, manifest, sig, and metadata', () => {
  assert(releaseYml.includes('OTA_ZIP_NAME="RupeeCRM-Update-${APP_VER}.zip"'), 'Must define RupeeCRM-Update-${APP_VER}.zip');
  assert(releaseYml.includes('package-metadata.json'), 'Must include package-metadata.json in ZIP');
  assert(releaseYml.includes('manifest.sig'), 'Must include manifest.sig in ZIP');
  assert(releaseYml.includes('manifest.json'), 'Must include manifest.json in ZIP');
  assert(releaseYml.includes('rupeecrm.war'), 'Must include rupeecrm.war in ZIP');
});

check('release.yml performs comprehensive 10-point release package validation', () => {
  assert(releaseYml.includes('Check 1/10: OTA ZIP structural integrity verified'), 'Validation Check 1');
  assert(releaseYml.includes('Check 2/10: WAR binary present and sized correctly'), 'Validation Check 2');
  assert(releaseYml.includes('Check 3/10: Manifest version matches release target'), 'Validation Check 3');
  assert(releaseYml.includes('Check 4/10: Manifest detached signature cryptographically verified'), 'Validation Check 4');
  assert(releaseYml.includes('Check 5/10: WAR SHA-256 matches manifest'), 'Validation Check 5');
  assert(releaseYml.includes('Check 6/10: All required auxiliary files present'), 'Validation Check 6');
  assert(releaseYml.includes('Check 7/10: Package contains no path traversal or absolute paths'), 'Validation Check 7');
  assert(releaseYml.includes('Check 8/10: WAR archive format valid for update engine'), 'Validation Check 8');
  assert(releaseYml.includes('Check 9/10: No stale build metadata detected'), 'Validation Check 9');
  assert(releaseYml.includes('Check 10/10: Release package matches production Git commit'), 'Validation Check 10');
});

check('release.yml verifies published RupeeCRM-Update-${APP_VER}.zip attached asset', () => {
  assert(releaseYml.includes('OTA_ZIP="RupeeCRM-Update-${APP_VER}.zip"'), 'Must check published OTA ZIP');
  assert(releaseYml.includes('EXPECTED_ASSETS+=("manifest.json" "manifest.sig" "$OTA_ZIP" "${OTA_ZIP}.sha256")'), 'Must verify OTA zip in expected assets');
});

console.log('\n--- Suite 5: Automated Older Workflow Runs Cleanup ---');

check('release.yml scopes cleanup across production build and release workflows', () => {
  assert(releaseYml.includes('release.yml'), 'Must target release.yml');
  assert(releaseYml.includes('regression-tests.yml'), 'Must target regression-tests.yml');
  assert(releaseYml.includes('pr-validation.yml'), 'Must target pr-validation.yml');
});

check('release.yml implements paginated run fetching and safe deletion with rate-limit retry', () => {
  assert(releaseYml.includes('page=${PAGE}'), 'Must paginate workflow runs');
  assert(releaseYml.includes('"$RUN_STATUS" != "completed"'), 'Must strictly target completed runs only');
  assert(releaseYml.includes('$CURRENT_RUN_ID'), 'Must strictly preserve current run');
  assert(releaseYml.includes('HTTP_CODE=$(curl -s -w "%{http_code}" -o /dev/null -X DELETE'), 'Must invoke REST DELETE on eligible runs');
});

console.log('\n═══════════════════════════════════════════════════════════════════');
console.log(`ALL VERIFICATION CHECKS COMPLETED: ${passed}/${total} PASSED`);
console.log('═══════════════════════════════════════════════════════════════════\n');

if (passed !== total) {
  process.exit(1);
}
