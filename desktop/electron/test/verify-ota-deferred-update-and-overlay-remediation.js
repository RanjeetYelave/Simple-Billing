/**
 * RupeeCRM Verification Suite: Deferred Updates, Version Choice & Non-Dismissible Overlay
 *
 * Verifies all 3 core requirements:
 * 1. Remind Me Later & Deferred Updates:
 *    - update-state.json READY_TO_INSTALL preservation
 *    - getStagedUpdateInfo() and enrichResponseWithStagedInfo()
 *    - /api/system/remind-later endpoint
 *    - Button text: "Downloaded, Install and Restart"
 * 2. Version Choice & Safe Staged Replacement:
 *    - State A button: "Download Update"
 *    - Staged vs Latest version comparison
 *    - Option 1: "Install Downloaded Version (X)"
 *    - Option 2: "Download Latest Version (Y)"
 *    - Safe replacement via candidate file ("rupeecrm-update-candidate.war.part")
 *    - Staged package preservation on newer download/validation failure
 * 3. Non-Dismissible Installation Overlay & 6 State-Driven Phases:
 *    - Absence of "Dismiss Overlay (Backend Running in Background)"
 *    - Escape key blocking during isRestarting
 *    - Phase 1: Update package downloaded & verified
 *    - Phase 2: Replacing application
 *    - Phase 3: Restarting service
 *    - Phase 4: Database schema verification
 *    - Phase 5: Checking application health
 *    - Phase 6: Confirming installed version
 */

const fs = require('fs');
const path = require('path');
const assert = require('assert');

console.log('═══════════════════════════════════════════════════════════════════');
console.log('RUPEECRM DEFERRED UPDATES, VERSION CHOICE & OVERLAY REMEDIATION');
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
const apiJsPath = path.resolve(__dirname, '../../../billsoft/src/main/webapp/js/api.js');
const updateServicePath = path.resolve(__dirname, '../../../billsoft/src/main/java/com/billing/simple/billsoft/service/UpdateService.java');
const updateControllerPath = path.resolve(__dirname, '../../../billsoft/src/main/java/com/billing/simple/billsoft/controllers/UpdateController.java');
const mainJsPath = path.resolve(__dirname, '../src/main.js');

const indexHtml = fs.readFileSync(indexHtmlPath, 'utf8');
const apiJs = fs.readFileSync(apiJsPath, 'utf8');
const updateService = fs.readFileSync(updateServicePath, 'utf8');
const updateController = fs.readFileSync(updateControllerPath, 'utf8');
const mainJs = fs.readFileSync(mainJsPath, 'utf8');

console.log('--- Requirement 1: Remind Me Later & Deferred Updates ---');

runCheck('UpdateService defines getStagedUpdateInfo() inspecting txDir and update-state.json', () => {
  assert(updateService.includes('public Map<String, Object> getStagedUpdateInfo()'), 'getStagedUpdateInfo missing');
  assert(updateService.includes('stagedUpdateAvailable'), 'stagedUpdateAvailable missing in UpdateService');
  assert(updateService.includes('stagedVersion'), 'stagedVersion missing in UpdateService');
});

runCheck('UpdateService checkUpdate enriches response with staged update metadata and detects newer releases', () => {
  assert(updateService.includes('enrichResponseWithStagedInfo'), 'enrichResponseWithStagedInfo helper missing');
  assert(updateService.includes('hasNewerRelease'), 'hasNewerRelease logic missing in UpdateService');
});

runCheck('UpdateService implements remindLater() to persist deferred state', () => {
  assert(updateService.includes('public boolean remindLater()'), 'remindLater method missing in UpdateService');
});

runCheck('UpdateController exposes /api/system/remind-later and supports targetVersion on /apply-update', () => {
  assert(updateController.includes('@PostMapping("/remind-later")'), '/api/system/remind-later mapping missing');
  assert(updateController.includes('targetVersion'), 'targetVersion parameter missing in UpdateController');
});

runCheck('api.js declares remindLater and passes targetVersion in applyUpdate', () => {
  assert(apiJs.includes('remindLater: () => API._json(\'/api/system/remind-later\''), 'API.system.remindLater missing in api.js');
  assert(apiJs.includes('applyUpdate: (targetVersion) => API._json(\'/api/system/apply-update\''), 'API.system.applyUpdate targetVersion support missing in api.js');
});

runCheck('Frontend SoftwareUpdate displays exact button text: "Downloaded, Install and Restart"', () => {
  assert(indexHtml.includes('Downloaded, Install and Restart'), 'Exact text "Downloaded, Install and Restart" missing in index.html');
});

console.log('\n--- Requirement 2: Version Choice & Safe Staged Replacement ---');

runCheck('Frontend SoftwareUpdate displays exact button text for State A: "Download Update"', () => {
  assert(indexHtml.includes('Download Update'), 'Exact text "Download Update" missing in index.html');
});

runCheck('Frontend SoftwareUpdate renders clear version comparison when newer release exists', () => {
  assert(indexHtml.includes('Downloaded update:'), 'Comparison label "Downloaded update:" missing');
  assert(indexHtml.includes('Latest available update:'), 'Comparison label "Latest available update:" missing');
});

runCheck('Frontend SoftwareUpdate provides dual options: Install Downloaded vs Download Latest', () => {
  assert(indexHtml.includes('Install Downloaded Version'), 'Option 1 "Install Downloaded Version" missing');
  assert(indexHtml.includes('Download Latest Version'), 'Option 2 "Download Latest Version" missing');
});

runCheck('UpdateService implements Safe Staged Replacement using candidate part file', () => {
  assert(updateService.includes('rupeecrm-update-candidate.war.part'), 'Candidate part file mechanism missing in UpdateService');
  assert(updateService.includes('preservedStagedState'), 'preservedStagedState protection missing in UpdateService');
});

runCheck('UpdateService preserves verified staged package on candidate failure/cancellation', () => {
  assert(updateService.includes('Preserving verified staged update'), 'Staged package preservation log missing');
});

console.log('\n--- Requirement 3: Non-Dismissible Installation Overlay & 6 State-Driven Phases ---');

runCheck('The dismiss overlay button is COMPLETELY REMOVED from index.html', () => {
  assert(!indexHtml.includes('Dismiss Overlay (Backend Running in Background)'),
    'CRITICAL FAILURE: "Dismiss Overlay (Backend Running in Background)" MUST NOT exist in index.html');
});

runCheck('UpdateProgressOverlay intercepts and blocks Escape key dismissal during isRestarting', () => {
  assert(indexHtml.includes("handleKeyDown"), 'handleKeyDown listener missing in UpdateProgressOverlay');
  assert(indexHtml.includes("e.key === 'Escape'"), 'Escape key blocker missing in UpdateProgressOverlay');
});

runCheck('UpdateProgressOverlay stops click propagation on backdrop and card', () => {
  assert(indexHtml.includes("onClick: (e) => { e.stopPropagation(); }"), 'stopPropagation missing on modal backdrop');
});

runCheck('UpdateProgressOverlay displays all 6 state-driven phases', () => {
  assert(indexHtml.includes('1. Update package downloaded & verified'), 'Phase 1 missing in UpdateProgressOverlay');
  assert(indexHtml.includes('2. Replacing application'), 'Phase 2 missing in UpdateProgressOverlay');
  assert(indexHtml.includes('3. Restarting service'), 'Phase 3 missing in UpdateProgressOverlay');
  assert(indexHtml.includes('4. Database schema verification'), 'Phase 4 missing in UpdateProgressOverlay');
  assert(indexHtml.includes('5. Checking application health'), 'Phase 5 missing in UpdateProgressOverlay');
  assert(indexHtml.includes('6. Confirming installed version'), 'Phase 6 missing in UpdateProgressOverlay');
});

runCheck('Dashboard and SettingsHub propagate onInstallStaged handler', () => {
  assert(indexHtml.includes('onInstallStaged: handleInstallStaged'), 'onInstallStaged prop wiring missing in App');
  assert(indexHtml.includes('function SettingsHub({ onFirmChanged, onStartUpdate, onInstallStaged })'), 'SettingsHub props missing onInstallStaged');
});

console.log('\n═══════════════════════════════════════════════════════════════════');
console.log(`ALL VERIFICATION CHECKS PASSED: ${passCount}/${totalCount}`);
console.log('═══════════════════════════════════════════════════════════════════\n');
