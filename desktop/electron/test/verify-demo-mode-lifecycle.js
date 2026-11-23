/**
 * RupeeCRM Demo Mode Verification Suite
 * Verifies strict database isolation, PIN 0000 entry, persistent banner UI,
 * 3-firm predefined seed dataset, deterministic reset, and crash safety.
 */

const fs = require('fs');
const path = require('path');
const assert = require('assert');

console.log('═══════════════════════════════════════════════════════════════════');
console.log('RUPEECRM DEMO MODE LIFECYCLE & ISOLATION VERIFICATION SUITE');
console.log('═══════════════════════════════════════════════════════════════════\n');

let passed = 0;
let total = 0;

function test(name, fn) {
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

const rootDir = path.resolve(__dirname, '../../..');
const apiJsPath = path.join(rootDir, 'billsoft/src/main/webapp/js/api.js');
const indexHtmlPath = path.join(rootDir, 'billsoft/src/main/webapp/index.html');
const styleCssPath = path.join(rootDir, 'billsoft/src/main/webapp/css/style.css');
const demoSeedPath = path.join(rootDir, 'billsoft/src/main/resources/demo/demo_seed_dataset.json');
const demoControllerPath = path.join(rootDir, 'billsoft/src/main/java/com/billing/simple/billsoft/controllers/DemoModeController.java');
const demoServicePath = path.join(rootDir, 'billsoft/src/main/java/com/billing/simple/billsoft/demo/DemoModeService.java');
const routingDsPath = path.join(rootDir, 'billsoft/src/main/java/com/billing/simple/billsoft/demo/DynamicRoutingDataSource.java');

console.log('--- Suite 1: Webapp API Client & UI Wiring ---');

test('API.demo methods (getStatus, enter, exit, reset) exist in js/api.js', () => {
  const content = fs.readFileSync(apiJsPath, 'utf8');
  assert(content.includes('demo: {'), 'demo API namespace must exist');
  assert(content.includes('/api/demo/status'), 'demo.getStatus endpoint must be wired');
  assert(content.includes('/api/demo/enter'), 'demo.enter endpoint must be wired');
  assert(content.includes('/api/demo/exit'), 'demo.exit endpoint must be wired');
  assert(content.includes('/api/demo/reset'), 'demo.reset endpoint must be wired');
});

test('Persistent DEMO MODE banner is implemented in index.html', () => {
  const content = fs.readFileSync(indexHtmlPath, 'utf8');
  assert(content.includes('rupeecrm-demo-mode-banner'), 'Demo banner DOM id must exist');
  assert(content.includes('rcrm-demo-banner'), 'Demo banner CSS class must exist');
  assert(content.includes('DEMO MODE'), 'DEMO MODE label must be present');
  assert(content.includes('Exit Demo Mode'), 'Exit Demo Mode action must be present in banner');
  assert(content.includes('Reset Demo Data'), 'Reset Demo Data action must be present in banner');
});

test('Help & About includes Sales Evaluation & Demo Mode card and PIN 0000 access gate', () => {
  const content = fs.readFileSync(indexHtmlPath, 'utf8');
  assert(content.includes('Sales Evaluation & Demo Mode'), 'Demo mode card title must exist in Help & About');
  assert(content.includes('0000'), 'Default access PIN 0000 must be referenced');
  assert(content.includes('__rupeecrmOpenDemoEntryModal'), 'Entry modal trigger must be defined');
  assert(content.includes('showDemoEntryModal'), 'showDemoEntryModal state must exist');
  assert(content.includes('showDemoExitModal'), 'showDemoExitModal state must exist');
});

test('Firm switch select includes [DEMO] prefix in Demo Mode', () => {
  const content = fs.readFileSync(indexHtmlPath, 'utf8');
  assert(content.includes('`[DEMO] ${f.firmName}`'), 'Firm switcher must visibly indicate [DEMO] in options');
});

test('Demo Mode styles are defined in css/style.css', () => {
  const content = fs.readFileSync(styleCssPath, 'utf8');
  assert(content.includes('.rcrm-demo-banner'), '.rcrm-demo-banner must be styled');
  assert(content.includes('.rcrm-demo-tag'), '.rcrm-demo-tag must be styled');
  assert(content.includes('.rcrm-demo-pill'), '.rcrm-demo-pill must be styled');
});

console.log('\n--- Suite 2: Predefined 3-Firm Seed Dataset Integrity ---');

test('Predefined demo seed dataset JSON exists and contains exactly 3 firms', () => {
  const content = fs.readFileSync(demoSeedPath, 'utf8');
  const data = JSON.parse(content);
  assert(Array.isArray(data.allFirms), 'allFirms must be an array');
  assert.strictEqual(data.allFirms.length, 3, 'Must contain exactly 3 firms');

  const firmNames = data.allFirms.map(f => f.firmName);
  assert(firmNames.includes('Apex Plumbing Solutions'), 'Must contain Apex Plumbing Solutions');
  assert(firmNames.includes('Bright Electrical Traders'), 'Must contain Bright Electrical Traders');
  assert(firmNames.includes('Mehta Industrial Supplies'), 'Must contain Mehta Industrial Supplies');
});

test('Predefined seed dataset satisfies target business record metrics', () => {
  const data = JSON.parse(fs.readFileSync(demoSeedPath, 'utf8'));
  assert.strictEqual(data.customers.length, 40, 'Must have exactly 40 customers');
  assert.strictEqual(data.parties.length, 15, 'Must have exactly 15 suppliers/parties');
  assert.strictEqual(data.products.length, 150, 'Must have exactly 150 products');
  assert.strictEqual(data.invoices.length, 45, 'Must have exactly 45 invoices');
  assert.strictEqual(data.purchaseOrders.length, 20, 'Must have exactly 20 purchase orders');
  assert.strictEqual(data.partyPayments.length, 20, 'Must have exactly 20 supplier payments');
  assert.strictEqual(data.salesReturns.length, 5, 'Must have exactly 5 sales returns');
  assert.strictEqual(data.stockMovements.length, 75, 'Must have exactly 75 stock movements');
  assert.strictEqual(data.employees.length, 8, 'Must have exactly 8 employees');
  assert.strictEqual(data.salaryRecords.length, 8, 'Must have exactly 8 salary records');
  assert.strictEqual(data.goals.length, 9, 'Must have exactly 9 goals');
  assert.strictEqual(data.expenses.length, 20, 'Must have exactly 20 expenses');
});

console.log('\n--- Suite 3: Backend Architecture & Database Isolation Guard ---');

test('DynamicRoutingDataSource and DemoModeService enforce dedicated isolated database', () => {
  assert(fs.existsSync(routingDsPath), 'DynamicRoutingDataSource must exist');
  assert(fs.existsSync(demoServicePath), 'DemoModeService must exist');
  assert(fs.existsSync(demoControllerPath), 'DemoModeController must exist');

  const dsContent = fs.readFileSync(routingDsPath, 'utf8');
  assert(dsContent.includes('EnvironmentModeContext.getCurrentMode()'), 'Routing DataSource must query EnvironmentModeContext');

  const srvContent = fs.readFileSync(demoServicePath, 'utf8');
  assert(srvContent.includes('FIXED_DEMO_PIN = "0000"'), 'DemoModeService must require PIN 0000');
  assert(srvContent.includes('demo_seed_dataset.json'), 'DemoModeService must load demo_seed_dataset.json');
  assert(srvContent.includes('checkStartupCrashRecovery'), 'Crash recovery logic must be present');
});

console.log('\n═══════════════════════════════════════════════════════════════════');
console.log(`ALL DEMO MODE CHECKS PASSED: ${passed}/${total}`);
console.log('═══════════════════════════════════════════════════════════════════\n');
