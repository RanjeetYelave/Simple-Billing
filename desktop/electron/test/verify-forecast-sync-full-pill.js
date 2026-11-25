/**
 * verify-forecast-sync-full-pill.js
 * RupeeCRM — Forecast Sync Progress Pill, Backend Stage Tracking & Security Isolation Suite
 */

const assert = require('assert');
const fs = require('fs');
const path = require('path');

console.log('═══════════════════════════════════════════════════════════════════');
console.log('RUPEECRM FORECAST SYNC FULL-PILL & SECURITY ISOLATION TEST SUITE');
console.log('═══════════════════════════════════════════════════════════════════\n');

const rootDir = path.resolve(__dirname, '../../..');
const piJsPath = path.join(rootDir, 'billsoft/src/main/webapp/js/components/ProductIntelligence.js');
const indexHtmlPath = path.join(rootDir, 'billsoft/src/main/webapp/index.html');
const apiJsPath = path.join(rootDir, 'billsoft/src/main/webapp/js/api.js');
const syncServicePath = path.join(rootDir, 'billsoft/src/main/webapp/js/forecastSyncService.js');
const controllerPath = path.join(rootDir, 'billsoft/src/main/java/com/billing/simple/billsoft/forecast/controller/ForecastController.java');
const workerPath = path.join(rootDir, 'billsoft/src/main/java/com/billing/simple/billsoft/forecast/service/ForecastAsyncWorkerService.java');
const poBatchPath = path.join(rootDir, 'billsoft/src/main/java/com/billing/simple/billsoft/entities/PurchaseOrderBatchRecord.java');
const gitignorePath = path.join(rootDir, '.gitignore');

let passed = 0;
let total = 0;

function runTest(name, fn) {
  total++;
  try {
    fn();
    console.log(`  ✓ [PASS] ${total}: ${name}`);
    passed++;
  } catch (err) {
    console.error(`  ✗ [FAIL] ${total}: ${name}`);
    console.error(`    -> ${err.message}`);
    process.exitCode = 1;
  }
}

console.log('--- Suite 1: Full-Pill Progress UI in Order Products & Inventory Toolbars ---');

runTest('Single full-pill progress container in ProductIntelligence.js with Left-to-Right gradient fill', () => {
  const content = fs.readFileSync(piJsPath, 'utf8');
  assert(content.includes("id: 'btn-resync-forecasts-queue'"), 'btn-resync-forecasts-queue must exist');
  assert(content.includes('linear-gradient(to right, rgba(16, 185, 129, 0.26), rgba(16, 185, 129, 0.45))'), 'Must have smooth left-to-right green progress fill');
  assert(content.includes('Forecast Sync Intelligence'), 'Must have hover/click diagnostic popover');
  assert(content.includes('billsoft:forecast-sync-progress'), 'Must listen to granular progress updates');
});

runTest('Single full-pill progress container in index.html (Inventory toolbar)', () => {
  const content = fs.readFileSync(indexHtmlPath, 'utf8');
  assert(content.includes("id: 'btn-inventory-forecast-sync-pill'"), 'btn-inventory-forecast-sync-pill must exist');
  assert(content.includes('linear-gradient(to right, rgba(16, 185, 129, 0.26), rgba(16, 185, 129, 0.45))'), 'Inventory pill must have matching left-to-right progress fill');
  assert(content.includes('billsoft:forecast-sync-progress'), 'Inventory view must listen to sync progress');
  assert(!content.includes("'Forecasts Synced'"), 'Old competing static badge must be removed');
});

console.log('\n--- Suite 2: Controller & Backend Pipeline Stage Tracking ---');

runTest('ForecastController declares force and async RequestParam with default false', () => {
  const content = fs.readFileSync(controllerPath, 'utf8');
  assert(content.includes('@RequestParam(value = "force", required = false, defaultValue = "false") Boolean force'), 'Must accept force param');
  assert(content.includes('@RequestParam(value = "async", required = false, defaultValue = "false") Boolean isAsync'), 'Must accept async param');
});

runTest('ForecastAsyncWorkerService updates progress across 5 distinct pipeline stages', () => {
  const content = fs.readFileSync(workerPath, 'utf8');
  assert(content.includes('READING_SALES'), 'Stage 1 READING_SALES must exist');
  assert(content.includes('AGGREGATING_METRICS'), 'Stage 2 AGGREGATING_METRICS must exist');
  assert(content.includes('CALCULATING_FORECASTS'), 'Stage 3 CALCULATING_FORECASTS must exist');
  assert(content.includes('UPDATING_RECOMMENDATIONS'), 'Stage 4 UPDATING_RECOMMENDATIONS must exist');
  assert(content.includes('COMPLETED'), 'Stage 5 COMPLETED must exist');
  assert(content.includes('heartbeatAt'), 'Must track heartbeatAt timestamp');
});

runTest('ForecastSyncService provides automatic polling during async execution', () => {
  const content = fs.readFileSync(syncServicePath, 'utf8');
  assert(content.includes('billsoft:forecast-sync-progress'), 'Must emit billsoft:forecast-sync-progress CustomEvents');
  assert(content.includes('API.forecast.getSyncStatus(firmId)'), 'Must poll getSyncStatus for firm');
});

console.log('\n--- Suite 3: Multi-Vendor Idempotency & Database Security Isolation ---');

runTest('PurchaseOrderBatchRecord enforces unique constraint on (firmId, supplierOpId)', () => {
  const content = fs.readFileSync(poBatchPath, 'utf8');
  assert(content.includes('uk_pobatch_firm_supplier_op'), 'Unique constraint uk_pobatch_firm_supplier_op must be declared');
  assert(content.includes('columnNames = {"firmId", "supplierOpId"}') || content.includes('columnNames = {"firmId", "supplier_op_id"}'), 'Constraint must encompass firmId and supplierOpId');
});

runTest('.gitignore protects all forensic snapshots, H2 databases, and test db artifacts', () => {
  const content = fs.readFileSync(gitignorePath, 'utf8');
  assert(content.includes('forensic_evidence_snapshot/'), '.gitignore must ignore forensic_evidence_snapshot/');
  assert(content.includes('forensic_incident_cross_firm_restore_*/'), '.gitignore must ignore forensic_incident_cross_firm_restore/');
  assert(content.includes('billsoft/test_validation_db/'), '.gitignore must ignore billsoft/test_validation_db/');
  assert(content.includes('pre_financial_fixes_backup_*.mv.db'), '.gitignore must ignore pre_financial_fixes_backup_*.mv.db');
});

console.log('\n═══════════════════════════════════════════════════════════════════');
console.log(`TEST SUMMARY: ${passed} / ${total} PASSED (${Math.round((passed / total) * 100)}% SUCCESS)`);
console.log('═══════════════════════════════════════════════════════════════════');

if (passed !== total) {
  process.exitCode = 1;
}
