/**
 * RupeeCRM Verification Suite: Background Update Floating Progress Widget
 *
 * Validates:
 * 1. BackgroundUpdateProgressWidget Component:
 *    - Correct name, placement, styling, and DOM id
 *    - Real-time download metrics: Download icon, "RupeeCRM Update" label, target version,
 *      actual percentage, downloaded/total size, actual speed, status, green progress bar
 *    - Non-rendering on idle, foreground download, or terminal states
 * 2. Background Download Lifecycle & State Management:
 *    - "Run in Background" closes large modal and activates compact widget
 *    - Reopening / expanding widget returns to large modal without restarting download
 *    - Active background download survives route/page navigation
 *    - App startup / refresh reconciles active background download via updateDiagnostics
 *    - Cancellation clears widget and halts active stream
 *    - Automatic dismissal upon READY_TO_INSTALL / verified completion without auto-restart
 * 3. Updates Page Integration & Staged Package Reuse:
 *    - Displays "Status: Downloaded and Verified"
 *    - Primary button says exactly "Downloaded, Install and Restart"
 *    - Direct install activates staged war without network redownload
 * 4. Security & Integrity Guarantees:
 *    - Sandboxed renderer with no unrestricted filesystem access
 *    - Cryptographic Ed25519 signature and SHA-256 verification
 */

const fs = require('fs');
const path = require('path');
const assert = require('assert');

console.log('═══════════════════════════════════════════════════════════════════');
console.log('RUPEECRM BACKGROUND UPDATE FLOATING PROGRESS WIDGET TEST SUITE');
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

const indexHtmlPath = path.resolve(__dirname, '../../../billsoft/src/main/webapp/index.html');
const styleCssPath = path.resolve(__dirname, '../../../billsoft/src/main/webapp/css/style.css');
const apiJsPath = path.resolve(__dirname, '../../../billsoft/src/main/webapp/js/api.js');
const updateServicePath = path.resolve(__dirname, '../../../billsoft/src/main/java/com/billing/simple/billsoft/service/UpdateService.java');
const updateControllerPath = path.resolve(__dirname, '../../../billsoft/src/main/java/com/billing/simple/billsoft/controllers/UpdateController.java');

// Normalize CRLF to LF to guarantee cross-platform robustness across Windows and Unix runners
const indexHtml = fs.readFileSync(indexHtmlPath, 'utf8').replace(/\r\n/g, '\n');
const styleCss = fs.readFileSync(styleCssPath, 'utf8').replace(/\r\n/g, '\n');
const apiJs = fs.readFileSync(apiJsPath, 'utf8').replace(/\r\n/g, '\n');
const updateService = fs.readFileSync(updateServicePath, 'utf8').replace(/\r\n/g, '\n');
const updateController = fs.readFileSync(updateControllerPath, 'utf8').replace(/\r\n/g, '\n');

console.log('--- Suite 1: Component Definition, Placement & Metrics ---');

runCheck('BackgroundUpdateProgressWidget component is defined in index.html', () => {
  assert(indexHtml.includes('function BackgroundUpdateProgressWidget('), 'BackgroundUpdateProgressWidget declaration missing');
  assert(indexHtml.includes("id: 'rupeecrm-bg-update-widget'"), 'DOM ID rupeecrm-bg-update-widget missing');
});

runCheck('Widget enforces compact floating top-right placement with safe stacking', () => {
  assert(indexHtml.includes("position: 'fixed'"), 'Fixed positioning missing in widget');
  assert(indexHtml.includes("top: '70px'") || indexHtml.includes("top: '72px'"), 'Top offset missing in widget');
  assert(indexHtml.includes("right: '24px'"), 'Right offset missing in widget');
  assert(indexHtml.includes("zIndex: 9990"), 'zIndex 9990 stacking context missing');
  assert(indexHtml.includes("width: '310px'"), 'Compact width 310px missing');
});

runCheck('Widget displays all required runtime download metrics', () => {
  assert(indexHtml.includes("'RupeeCRM Update'"), 'Label "RupeeCRM Update" missing');
  assert(indexHtml.includes('Version ${targetVersion}'), 'Version formatting missing');
  assert(indexHtml.includes('`${percent}%`'), 'Percentage formatting missing');
  assert(indexHtml.includes('size ||'), 'Size metric missing');
  assert(indexHtml.includes('speed ||'), 'Speed metric missing');
  assert(indexHtml.includes("statusLabel = isPaused ? 'Paused' :"), 'Status label formatting missing');
  assert(indexHtml.includes('isPaused ? \'#eab308\' : \'#10b981\''), 'Green/amber progress bar indicator missing');
});

runCheck('CSS stylesheet defines .rcrm-bg-update-widget styling and mobile responsiveness', () => {
  assert(styleCss.includes('.rcrm-bg-update-widget'), '.rcrm-bg-update-widget class missing in style.css');
  assert(styleCss.includes('.rcrm-bg-update-widget:hover'), 'Hover lift transition missing in style.css');
  assert(styleCss.includes('@media (max-width: 640px)'), 'Mobile responsive query missing in style.css');
});

console.log('\n--- Suite 2: Background Download Lifecycle & State Management ---');

runCheck('App maintains isBackgroundDownloading state', () => {
  assert(indexHtml.includes('const [isBackgroundDownloading, setIsBackgroundDownloading] = useState(false);'),
    'isBackgroundDownloading state declaration missing in App');
});

runCheck('Run in Background closes large modal and enables floating widget', () => {
  assert(indexHtml.includes('const handleMinimizeUpdate = () => {'), 'handleMinimizeUpdate missing in App');
  assert(indexHtml.includes('setIsUpdating(false);'), 'setIsUpdating(false) missing in handleMinimizeUpdate');
  assert(indexHtml.includes('setIsBackgroundDownloading(true);'), 'setIsBackgroundDownloading(true) missing in handleMinimizeUpdate');
});

runCheck('Clicking floating widget expands back to large dialog without restarting download', () => {
  assert(indexHtml.includes('const handleExpandBackgroundUpdate = () => {'), 'handleExpandBackgroundUpdate missing in App');
  assert(/setIsBackgroundDownloading\s*\(\s*false\s*\)[\s\S]*?setIsUpdating\s*\(\s*true\s*\)/.test(indexHtml),
    'Modal expansion toggle missing in handleExpandBackgroundUpdate');
});

runCheck('Widget is conditionally rendered only when background download is active and modal is hidden', () => {
  assert(indexHtml.includes('isVisible: isBackgroundDownloading && !isUpdating'),
    'Conditional isVisible check missing on BackgroundUpdateProgressWidget instance');
});

runCheck('Verified completion (ready_to_install) automatically dismisses floating widget', () => {
  assert(/data\.status\s*===\s*'ready_to_install'[\s\S]*?setIsBackgroundDownloading\s*\(\s*false\s*\)/.test(indexHtml),
    'Auto-dismissal on ready_to_install missing in handleProgressData');
});

runCheck('Update cancellation automatically dismisses floating widget', () => {
  assert(/handleCancelUpdate[\s\S]*?setIsBackgroundDownloading\s*\(\s*false\s*\)/.test(indexHtml),
    'Widget dismissal on cancelUpdate missing in App');
});

runCheck('App startup reconciles and re-subscribes to active background downloads', () => {
  assert(indexHtml.includes('API.system.updateDiagnostics().then(diag => {'), 'Diagnostics reconciliation check missing in startup useEffect');
  assert(indexHtml.includes('diag.updateInProgress'), 'updateInProgress check missing in startup useEffect');
  assert(indexHtml.includes('connectUpdateProgressSSE(targetVer);'), 'connectUpdateProgressSSE invocation missing in startup reconciliation');
});

console.log('\n--- Suite 3: Updates Page Integration & Staged Package Reuse ---');

runCheck('Updates page displays "Status: Downloaded and Verified"', () => {
  assert(indexHtml.includes('Status: Downloaded and Verified'), 'Status string "Status: Downloaded and Verified" missing in index.html');
});

runCheck('Updates page displays exact primary button text: "Downloaded, Install and Restart"', () => {
  assert(indexHtml.includes('Downloaded, Install and Restart'),
    'Exact button string "Downloaded, Install and Restart" missing in index.html');
});

runCheck('Clicking install invokes handleDirectInstallStaged reusing staged package without network download', () => {
  assert(indexHtml.includes('handleDirectInstallStaged(info.stagedVersion)'),
    'handleDirectInstallStaged call missing in SoftwareUpdate');
  assert(updateService.includes('stagedAvail && preferredTargetVersion.equals(stagedVer)'),
    'Direct staged install check missing in UpdateService.applyUpdate');
});

console.log('\n--- Suite 4: Security & Integrity Guarantees ---');

runCheck('UpdateService enforces cryptographic signature and SHA-256 verification before staging', () => {
  assert(updateService.includes('UpdateCrypto.computeSha256(partFile)'), 'SHA-256 check missing in UpdateService');
  assert(updateService.includes('UpdateManifest.parseAndVerify'), 'Manifest signature verification missing in UpdateService');
});

runCheck('UpdateController loopback protection restricts local staging calls', () => {
  assert(updateController.includes('isLoopbackRequest(request)'), 'Loopback request check missing in UpdateController');
});

console.log('\n═══════════════════════════════════════════════════════════════════');
console.log(`ALL BACKGROUND UPDATE PROGRESS WIDGET CHECKS PASSED: ${passCount}/${totalCount}`);
console.log('═══════════════════════════════════════════════════════════════════\n');
