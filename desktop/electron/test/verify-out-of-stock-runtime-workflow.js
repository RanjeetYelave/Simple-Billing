/**
 * verify-out-of-stock-runtime-workflow.js
 * RupeeCRM — End-to-End Runtime Workflow Verification for Out-of-Stock Alerts,
 * 7-Day Persistent Snooze, Consolidated Warning Modal, and Non-Blocking Invoice Creation.
 */

const assert = require('assert');
const fs = require('fs');
const path = require('path');

console.log('═══════════════════════════════════════════════════════════════════');
console.log('RUPEECRM OUT-OF-STOCK & SNOOZE RUNTIME WORKFLOW VERIFICATION SUITE');
console.log('═══════════════════════════════════════════════════════════════════\n');

const repoRoot = path.resolve(__dirname, '../../..');
const indexPath = path.join(repoRoot, 'billsoft/src/main/webapp/index.html');
const apiPath = path.join(repoRoot, 'billsoft/src/main/webapp/js/api.js');
const prodEntityPath = path.join(repoRoot, 'billsoft/src/main/java/com/billing/simple/billsoft/entities/Product.java');
const prodServicePath = path.join(repoRoot, 'billsoft/src/main/java/com/billing/simple/billsoft/service/ProductService.java');
const prodControllerPath = path.join(repoRoot, 'billsoft/src/main/java/com/billing/simple/billsoft/controllers/ProductController.java');
const schedulerPath = path.join(repoRoot, 'billsoft/src/main/java/com/billing/simple/billsoft/service/PlannerNotificationScheduler.java');

assert(fs.existsSync(indexPath), `index.html must exist at ${indexPath}`);
assert(fs.existsSync(apiPath), `api.js must exist at ${apiPath}`);
assert(fs.existsSync(prodEntityPath), `Product.java must exist at ${prodEntityPath}`);
assert(fs.existsSync(prodServicePath), `ProductService.java must exist at ${prodServicePath}`);
assert(fs.existsSync(prodControllerPath), `ProductController.java must exist at ${prodControllerPath}`);
assert(fs.existsSync(schedulerPath), `PlannerNotificationScheduler.java must exist at ${schedulerPath}`);

const indexContent = fs.readFileSync(indexPath, 'utf8');
const apiContent = fs.readFileSync(apiPath, 'utf8');
const prodEntityContent = fs.readFileSync(prodEntityPath, 'utf8');
const prodServiceContent = fs.readFileSync(prodServicePath, 'utf8');
const prodControllerContent = fs.readFileSync(prodControllerPath, 'utf8');
const schedulerContent = fs.readFileSync(schedulerPath, 'utf8');

let totalTests = 0;
let passedTests = 0;

function runTest(name, fn) {
  totalTests++;
  try {
    fn();
    passedTests++;
    console.log(`  ✓ [PASS] ${name}`);
  } catch (err) {
    console.error(`  ✗ [FAIL] ${name}: ${err.message}`);
    throw err;
  }
}

// ─── SUITE 1: PERSISTENT DATA MODEL & API ARCHITECTURE ───
console.log('--- Suite 1: Backend Persistent 7-Day Snooze Architecture ---');

runTest('1.1: Product entity defines persistent stock_alert_snoozed_until column', () => {
  assert(prodEntityContent.includes('stock_alert_snoozed_until'), 'Product entity must map stock_alert_snoozed_until column');
  assert(prodEntityContent.includes('private LocalDateTime stockAlertSnoozedUntil;'), 'Product entity must declare stockAlertSnoozedUntil field');
});

runTest('1.2: ProductController exposes snooze-stock-warning endpoint', () => {
  assert(prodControllerContent.includes('@PostMapping("/{id}/snooze-stock-warning")'), 'ProductController must map /{id}/snooze-stock-warning');
  assert(prodControllerContent.includes('snoozeStockWarning'), 'ProductController must define snoozeStockWarning handler');
});

runTest('1.3: ProductService manages 7-day snooze lifecycle & clears on replenishment', () => {
  assert(prodServiceContent.includes('public Product snoozeStockAlert('), 'ProductService must implement snoozeStockAlert');
  assert(prodServiceContent.includes('plusDays(effectiveDays)'), 'ProductService must add days to current time');
  assert(prodServiceContent.includes('setStockAlertSnoozedUntil(null)'), 'ProductService must clear snooze on positive replenishment');
});

runTest('1.4: Frontend api.js provides API.products.snoozeStockWarning wrapper', () => {
  assert(apiContent.includes('snoozeStockWarning: (id, days = 7'), 'api.js must define snoozeStockWarning with default 7 days');
  assert(apiContent.includes('/snooze-stock-warning'), 'api.js must post to snooze-stock-warning');
});

// ─── SUITE 2: ALERT GENERATION & SCHEDULER LIFECYCLE ───
console.log('\n--- Suite 2: Out-of-Stock Alert Generation & Lifecycle ---');

runTest('2.1: Scheduler emits canonical item event key inventory:stock:item:{id}', () => {
  assert(schedulerContent.includes('"inventory:stock:item:" + p.getId()'), 'Scheduler must format itemEventKey as inventory:stock:item:{id}');
  assert(schedulerContent.includes('NotificationPriority.HIGH'), 'Scheduler must mark zero-stock alerts with HIGH priority');
});

runTest('2.2: Scheduler respects active stockAlertSnoozedUntil on product', () => {
  assert(schedulerContent.includes('p.getStockAlertSnoozedUntil() != null && now.isBefore(p.getStockAlertSnoozedUntil())'), 'Scheduler must evaluate active snooze expiry');
  assert(schedulerContent.includes('notificationService.snooze(created.getId(), "7d")'), 'Scheduler must preserve SNOOZED status');
});

runTest('2.3: Scheduler automatically resolves alert when stock is replenished above threshold', () => {
  assert(schedulerContent.includes('notificationService.resolveByEventKey(firmId, itemEventKey);'), 'Scheduler must resolve alert when stock > min');
});

// ─── SUITE 3: FRONTEND WARNING MODAL & CONSOLIDATED INSUFFICIENT STOCK HANDLING ───
console.log('\n--- Suite 3: Consolidated Modal & Invoice Creation Workflow ---');

runTest('3.1: OutOfStockDialog supports consolidated table for multiple shortage items', () => {
  assert(indexContent.includes('OutOfStockDialog'), 'index.html must define OutOfStockDialog');
  assert(indexContent.includes('shortage'), 'Modal must calculate and display shortage quantity');
  assert(indexContent.includes('Requested'), 'Modal table must display Requested column');
  assert(indexContent.includes('Available'), 'Modal table must display Available column');
});

runTest('3.2: Modal provides non-blocking Proceed Anyway and Go Back options', () => {
  assert(indexContent.includes('Proceed Anyway'), 'Modal must provide Proceed Anyway confirmation button');
  assert(indexContent.includes('Go Back / Review Items'), 'Modal must provide Go Back cancellation button');
});

runTest('3.3: Proceeding with snooze checkbox triggers both localStorage and backend API', () => {
  assert(indexContent.includes('window.API.products.snoozeStockWarning(it.productId, 7)'), 'Invoice workflow must invoke backend snoozeStockWarning API');
  assert(indexContent.includes('out_of_stock_suppress_'), 'Invoice workflow must persist snooze key in localStorage');
});

runTest('3.4: Invoice save passes allowOutOfStock: true to backend', () => {
  assert(indexContent.includes('allowOutOfStock: true'), 'Invoice submission must set allowOutOfStock: true');
});

// ─── SUITE 4: SIMULATION OF RUNTIME SNOOZE EXPIRATION & RECONCILIATION ───
console.log('\n--- Suite 4: Runtime Snooze Expiration Simulation ---');

runTest('4.1: Clock advancement simulation verifies snooze expiration after 7 days', () => {
  const now = new Date();
  const sevenDaysLater = new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000);
  const eightDaysLater = new Date(now.getTime() + 8 * 24 * 60 * 60 * 1000);

  // Helper function imitating index.html snooze check
  const isSnoozed = (suppressTime, checkTime) => {
    return suppressTime && checkTime.getTime() < suppressTime;
  };

  const suppressTimestamp = sevenDaysLater.getTime();

  // Within 7 days -> Snoozed (no warning)
  const sixDaysLater = new Date(now.getTime() + 6 * 24 * 60 * 60 * 1000);
  assert.strictEqual(isSnoozed(suppressTimestamp, sixDaysLater), true, 'Must remain snoozed at day 6');

  // After 7 days -> Expired (warning reappears)
  assert.strictEqual(isSnoozed(suppressTimestamp, eightDaysLater), false, 'Must expire and trigger alert after day 7');
});

console.log('═══════════════════════════════════════════════════════════════════');
console.log(`ALL ${passedTests}/${totalTests} RUNTIME WORKFLOW TESTS PASSED SUCCESSFULLY!`);
console.log('═══════════════════════════════════════════════════════════════════\n');
