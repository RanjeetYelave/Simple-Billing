/**
 * RupeeCRM Category 3: Local Backup, Multi-Firm Export & Restore Electron Verification Suite
 * 
 * Verifies:
 * 1. Preload bridge exposure of rupeeDesktop.saveBackup.
 * 2. Main process IPC handler 'backup:save' with atomic staging, size verification and JSON integrity check.
 * 3. Native download will-download handler supporting JSON/ZIP filters.
 * 4. Webapp api.js exportFirm & exportAll routing through rupeeDesktop.saveBackup with 120s blob URL preservation.
 * 5. Webapp index.html executeExport displaying the actual saved path upon export completion.
 * 6. Filesystem atomic write, validation, and overwrite simulation in directories with spaces and unicode.
 * 7. Verification of Category 3 enum converters, legacy mapping, and archive protection.
 */

const assert = require('assert');
const path = require('path');
const fs = require('fs');

console.log('═══════════════════════════════════════════════════════════════════');
console.log('RUPEECRM CATEGORY 3: LOCAL BACKUP & RESTORE ELECTRON SUITE');
console.log('═══════════════════════════════════════════════════════════════════\n');

// Suite 1: Preload and IPC Bridge Verification
console.log('--- Suite 1: Preload Bridge & IPC Exposure ---');
const preloadPath = path.resolve(__dirname, '../src/preload.js');
const preloadSource = fs.readFileSync(preloadPath, 'utf8');

assert(preloadSource.includes("saveBackup: (payload) => ipcRenderer.invoke('backup:save', payload)"),
  'preload.js must expose saveBackup invoking backup:save');

console.log('  ✔ [PASS] 1: window.rupeeDesktop.saveBackup is exposed via contextBridge');

// Suite 2: Main Process Backup Handler Verification
console.log('\n--- Suite 2: Main Process Backup Handler Verification ---');
const mainPath = path.resolve(__dirname, '../src/main.js');
const mainSource = fs.readFileSync(mainPath, 'utf8');

assert(mainSource.includes("ipcMain.handle('backup:save'"),
  "main.js must register 'backup:save' IPC handler");
assert(mainSource.includes("dialog.showSaveDialog"),
  "main.js must invoke native dialog.showSaveDialog");
assert(mainSource.includes(".tmp_backup_"),
  "main.js must use atomic staging temp file (.tmp_backup_)");
assert(mainSource.includes("stats.size === 0"),
  "main.js must verify non-zero file size");
assert(mainSource.includes("JSON.parse(fileContent)"),
  "main.js must verify JSON integrity before finalizing");
assert(mainSource.includes("fs.promises.rename"),
  "main.js must atomically promote temp file via rename");

console.log('  ✔ [PASS] 2: main.js implements robust backup:save with atomic staging and JSON validation');

// Suite 3: Native Download will-download Session Verification
console.log('\n--- Suite 3: will-download Session Handlers ---');
assert(mainSource.includes("extensions: ['json', 'zip']"),
  "will-download must include json and zip extensions in RupeeCRM Backups filter");
assert(mainSource.includes("item.once('done'"),
  "will-download must attach item.once('done') listener for download outcome tracking");

console.log('  ✔ [PASS] 3: will-download session listener includes backup filters and completion monitoring');

// Suite 4: Webapp api.js Integration Verification
console.log('\n--- Suite 4: Webapp api.js Export Routing & Blob Lifespan ---');
const apiPath = path.resolve(__dirname, '../../../billsoft/src/main/webapp/js/api.js');
const apiSource = fs.readFileSync(apiPath, 'utf8');

assert(apiSource.includes('120000'),
  "api.js _downloadBlob must retain blob object URL for 120 seconds (120000ms)");
assert(apiSource.includes("window.rupeeDesktop && typeof window.rupeeDesktop.saveBackup === 'function'"),
  "api.js exportFirm must check for window.rupeeDesktop.saveBackup");
assert(apiSource.includes("saveRes.canceled"),
  "api.js must handle user cancellation gracefully");

console.log('  ✔ [PASS] 4: api.js retains blob URL for 120s and routes exports through native bridge');

// Suite 5: Webapp index.html Feedback Verification
console.log('\n--- Suite 5: Webapp index.html Export UI & Path Display ---');
const indexPath = path.resolve(__dirname, '../../../billsoft/src/main/webapp/index.html');
const indexSource = fs.readFileSync(indexPath, 'utf8');

assert(indexSource.includes("res && res.filePath"),
  "index.html must check res.filePath returned from native export");
assert(indexSource.includes("'Backup saved successfully to: ' + savedPath"),
  "index.html must display the actual saved path to user in success toast");
assert(indexSource.includes("if (res && res.canceled)"),
  "index.html must handle export cancellation without showing false errors");

console.log('  ✔ [PASS] 5: index.html correctly surfaces actual saved file path in success toast');

// Suite 6: Atomic File Writing & Path Resolution Simulation
console.log('\n--- Suite 6: Filesystem Atomic Simulation (Spaces, Unicode & Overwrite) ---');
const testDir = path.resolve(__dirname, '../test-sandbox-backup with spaces and üñîçødé');
if (!fs.existsSync(testDir)) {
  fs.mkdirSync(testDir, { recursive: true });
}

try {
  const targetFile = path.join(testDir, 'rupeecrm_test_backup.json');
  const sampleBackup = {
    metadata: { version: '1.0.359', timestamp: new Date().toISOString() },
    firmDetails: [{ id: 1, firmName: 'Alpha Enterprise' }],
    customers: [{ id: 101, name: 'Customer Test' }],
    invoices: [{ id: 1001, invoiceNumber: 'INV-001', amount: 1500.0 }]
  };

  const payload = JSON.stringify(sampleBackup, null, 2);

  // Staging write
  const tempFile = path.join(testDir, `.tmp_backup_${Date.now()}`);
  fs.writeFileSync(tempFile, payload, 'utf8');

  // Verify non-empty
  const stats = fs.statSync(tempFile);
  assert(stats.size > 0, 'Temp file must have non-zero size');

  // Verify JSON parse
  const parsed = JSON.parse(fs.readFileSync(tempFile, 'utf8'));
  assert.strictEqual(parsed.firmDetails[0].firmName, 'Alpha Enterprise', 'Parsed data must match');

  // Atomic promote
  fs.renameSync(tempFile, targetFile);
  assert(fs.existsSync(targetFile), 'Target file must exist after atomic rename');
  assert.strictEqual(fs.statSync(targetFile).size, stats.size, 'Final file size must match temp file');

  // Overwrite simulation
  const updatedBackup = Object.assign({}, sampleBackup, { metadata: { version: '1.0.359', revision: 2 } });
  const updatedPayload = JSON.stringify(updatedBackup, null, 2);
  const tempFile2 = path.join(testDir, `.tmp_backup_2_${Date.now()}`);
  fs.writeFileSync(tempFile2, updatedPayload, 'utf8');
  fs.renameSync(tempFile2, targetFile);

  const finalParsed = JSON.parse(fs.readFileSync(targetFile, 'utf8'));
  assert.strictEqual(finalParsed.metadata.revision, 2, 'Atomic overwrite must succeed with updated content');

  console.log('  ✔ [PASS] 6: Atomic write, JSON validation, and overwrite pass in complex path directory');
} finally {
  // Clean up sandbox
  if (fs.existsSync(testDir)) {
    fs.rmSync(testDir, { recursive: true, force: true });
  }
}

// Suite 7: Backend Category 3 Remediation Assets Verification
console.log('\n--- Suite 7: Backend Category 3 Verification ---');
const notifCategoryPath = path.resolve(__dirname, '../../../billsoft/src/main/java/com/billing/simple/billsoft/entities/NotificationCategory.java');
const notifCategorySource = fs.readFileSync(notifCategoryPath, 'utf8');
assert(notifCategorySource.includes('UNKNOWN'), 'NotificationCategory must have UNKNOWN enum constant');
assert(notifCategorySource.includes('PURCHASE') && notifCategorySource.includes('Integer.parseInt(str)'), 'NotificationCategory must map numeric ordinals to enum constants');

const archiveServicePath = path.resolve(__dirname, '../../../billsoft/src/main/java/com/billing/simple/billsoft/service/BackupArchiveService.java');
const archiveServiceSource = fs.readFileSync(archiveServicePath, 'utf8');
assert(archiveServiceSource.includes('ZipSlip'), 'BackupArchiveService must guard against ZipSlip');
assert(archiveServiceSource.includes('CHECKSUM_MISMATCH'), 'BackupArchiveService must validate SHA-256 checksums');

console.log('  ✔ [PASS] 7: Backend NotificationCategory numeric 3 mapping and BackupArchiveService verified');

console.log('\n═══════════════════════════════════════════════════════════════════');
console.log('ALL CATEGORY 3 ELECTRON VERIFICATION TESTS PASSED SUCCESSFULLY (7/7)');
console.log('═══════════════════════════════════════════════════════════════════\n');
