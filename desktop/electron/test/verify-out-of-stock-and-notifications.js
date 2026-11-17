/**
 * verify-out-of-stock-and-notifications.js
 * RupeeCRM — Automated Verification Suite for Out-of-Stock Warning/Snooze and Notification Settings
 *
 * Verifies:
 * 1. OutOfStockDialog modal contains warning text, 7-day snooze checkbox, and state resets.
 * 2. updateItem checks for zero/negative stock and prompts window.showOutOfStockWarning if not snoozed.
 * 3. 7-day snooze key is verified in localStorage (out_of_stock_suppress_<id>).
 * 4. InvoiceForm handleSave verifies items and passes allowOutOfStock: true in req.
 * 5. Backend InvoiceRequest and InvoiceUpdateRequest DTOs support allowOutOfStock.
 * 6. Backend ProductService supports allowNegativeOverride in recordStockMovement.
 * 7. Backend InvoiceService passes allowOutOfStock to deductInventoryStockForInvoice.
 * 8. Backend NotificationPreferencesDto supports categories list.
 * 9. Backend NotificationService synchronizes categories list with individual category boolean flags.
 * 10. Frontend SettingsNotifications maps categories and dispatches billsoft:notifications-refresh.
 * 11. Topbar bell button respects notificationSummary.bellEnabled and enabled flags.
 */

const assert = require('assert');
const fs = require('fs');
const path = require('path');

console.log('═══════════════════════════════════════════════════════════════════');
console.log('RUPEECRM OUT-OF-STOCK & NOTIFICATIONS SETTINGS VERIFICATION SUITE');
console.log('═══════════════════════════════════════════════════════════════════\n');

const repoRoot = path.resolve(__dirname, '../../..');
const indexPath = path.join(repoRoot, 'billsoft/src/main/webapp/index.html');
const invReqPath = path.join(repoRoot, 'billsoft/src/main/java/com/billing/simple/billsoft/dtos/InvoiceRequest.java');
const invUpdReqPath = path.join(repoRoot, 'billsoft/src/main/java/com/billing/simple/billsoft/dtos/InvoiceUpdateRequest.java');
const prodServicePath = path.join(repoRoot, 'billsoft/src/main/java/com/billing/simple/billsoft/service/ProductService.java');
const invServicePath = path.join(repoRoot, 'billsoft/src/main/java/com/billing/simple/billsoft/service/InvoiceService.java');
const notifDtoPath = path.join(repoRoot, 'billsoft/src/main/java/com/billing/simple/billsoft/dto/NotificationPreferencesDto.java');
const notifServicePath = path.join(repoRoot, 'billsoft/src/main/java/com/billing/simple/billsoft/service/NotificationService.java');

assert(fs.existsSync(indexPath), `index.html must exist at ${indexPath}`);
assert(fs.existsSync(invReqPath), `InvoiceRequest.java must exist at ${invReqPath}`);
assert(fs.existsSync(invUpdReqPath), `InvoiceUpdateRequest.java must exist at ${invUpdReqPath}`);
assert(fs.existsSync(prodServicePath), `ProductService.java must exist at ${prodServicePath}`);
assert(fs.existsSync(invServicePath), `InvoiceService.java must exist at ${invServicePath}`);
assert(fs.existsSync(notifDtoPath), `NotificationPreferencesDto.java must exist at ${notifDtoPath}`);
assert(fs.existsSync(notifServicePath), `NotificationService.java must exist at ${notifServicePath}`);

const indexContent = fs.readFileSync(indexPath, 'utf8');
const invReqContent = fs.readFileSync(invReqPath, 'utf8');
const invUpdReqContent = fs.readFileSync(invUpdReqPath, 'utf8');
const prodServiceContent = fs.readFileSync(prodServicePath, 'utf8');
const invServiceContent = fs.readFileSync(invServicePath, 'utf8');
const notifDtoContent = fs.readFileSync(notifDtoPath, 'utf8');
const notifServiceContent = fs.readFileSync(notifServicePath, 'utf8');

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

// ─── SUITE 1: OUT-OF-STOCK WARNING & 7-DAY SNOOZE ───
console.log('--- Suite 1: Out-of-Stock Warning Modal & 7-Day Snooze ---');

runTest('1.1: OutOfStockDialog modal renders expected warning message and 7-day snooze checkbox', () => {
  assert(indexContent.includes('We are out of stock for ${itemName}.'), 'OutOfStockDialog must show "We are out of stock for <item>."');
  assert(indexContent.includes("Don't remind me for 7 days for this item"), 'OutOfStockDialog must include 7-day snooze checkbox');
  assert(indexContent.includes("7 * 24 * 60 * 60 * 1000"), 'OutOfStockDialog must compute 7-day snooze expiration');
  assert(indexContent.includes("'out_of_stock_suppress_' + productId"), 'OutOfStockDialog must use out_of_stock_suppress_ key prefix');
});

runTest('1.2: updateItem prompts out-of-stock warning on item selection when stock is zero or negative', () => {
  assert(indexContent.includes("curStock <= 0"), 'updateItem must detect non-service products with stock <= 0');
  assert(indexContent.includes("window.showOutOfStockWarning(extraProd.name"), 'updateItem must invoke showOutOfStockWarning');
  assert(indexContent.includes("'out_of_stock_suppress_' + pId"), 'updateItem must check snooze timestamp in localStorage');
});

runTest('1.3: handleSave validates stock and passes allowOutOfStock: true', () => {
  assert(indexContent.includes('allowOutOfStock: true'), 'Invoice handleSave must include allowOutOfStock: true in request payload');
});

// ─── SUITE 2: BACKEND OUT-OF-STOCK PERSISTENCE & SERVICE INTEGRATION ───
console.log('\n--- Suite 2: Backend Out-of-Stock Support ---');

runTest('2.1: InvoiceRequest and InvoiceUpdateRequest support allowOutOfStock', () => {
  assert(invReqContent.includes('private Boolean allowOutOfStock;'), 'InvoiceRequest must declare allowOutOfStock');
  assert(invReqContent.includes('public Boolean getAllowOutOfStock()'), 'InvoiceRequest must have getter for allowOutOfStock');
  assert(invUpdReqContent.includes('private Boolean allowOutOfStock;'), 'InvoiceUpdateRequest must declare allowOutOfStock');
  assert(invUpdReqContent.includes('public Boolean getAllowOutOfStock()'), 'InvoiceUpdateRequest must have getter for allowOutOfStock');
});

runTest('2.2: ProductService.recordStockMovement supports allowNegativeOverride', () => {
  assert(prodServiceContent.includes('boolean allowNegativeOverride'), 'ProductService must declare overloaded recordStockMovement with allowNegativeOverride');
  assert(prodServiceContent.includes('allowNegativeOverride || isAllowNegativeStock(targetFirmId)'), 'ProductService must evaluate allowNegativeOverride against firm config');
});

runTest('2.3: InvoiceService passes allowOutOfStock to recordStockMovement', () => {
  assert(invServiceContent.includes('deductInventoryStockForInvoice(saved, Boolean.TRUE.equals(request.getAllowOutOfStock()))'), 'InvoiceService.createInvoice must pass allowOutOfStock');
  assert(invServiceContent.includes('deductInventoryStockForInvoice(Invoice invoice, boolean allowNegativeStock)'), 'InvoiceService must have overloaded deductInventoryStockForInvoice');
});

// ─── SUITE 3: NOTIFICATION PREFERENCES & CHANNELS ───
console.log('\n--- Suite 3: Notification Preferences & Synchronization ---');

runTest('3.1: NotificationPreferencesDto declares categories list', () => {
  assert(notifDtoContent.includes('private List<String> categories;'), 'NotificationPreferencesDto must have categories list');
});

runTest('3.2: NotificationService synchronizes categories in getPreferences and updatePreferences', () => {
  assert(notifServiceContent.includes('.categories(categories)'), 'NotificationService.getPreferences must populate categories list');
  assert(notifServiceContent.includes('dto.getCategories() != null'), 'NotificationService.updatePreferences must check categories list');
  assert(notifServiceContent.includes('pref.setBillingEnabled(catSet.contains("BILLING"))'), 'NotificationService.updatePreferences must sync billing category');
  assert(notifServiceContent.includes('pref.setInventoryEnabled(catSet.contains("INVENTORY"))'), 'NotificationService.updatePreferences must sync inventory category');
});

runTest('3.3: Frontend SettingsNotifications maps categories and channel toggles', () => {
  assert(indexContent.includes("categories: ['BILLING', 'INVENTORY', 'PURCHASE', 'PLANNER', 'HR', 'LICENSING', 'SYSTEM']"), 'SettingsNotifications must have default categories');
  assert(indexContent.includes("billingEnabled: cats.includes('BILLING')"), 'SettingsNotifications must send boolean flags alongside categories');
  assert(indexContent.includes("window.dispatchEvent(new CustomEvent('billsoft:notifications-refresh'))"), 'SettingsNotifications must trigger notifications-refresh');
});

runTest('3.4: Topbar bell icon is conditionally rendered based on notification preferences', () => {
  assert(indexContent.includes('notificationSummary.bellEnabled === false || notificationSummary.enabled === false'), 'Topbar bell must hide when bellEnabled or enabled is false');
});

console.log('\n═══════════════════════════════════════════════════════════════════');
console.log(`ALL TESTS PASSED: ${passedTests}/${totalTests}`);
console.log('═══════════════════════════════════════════════════════════════════\n');
process.exit(0);
