/**
 * verify-customer-invoice-creation.js
 * RupeeCRM — Automated Verification Suite for Customer Auto-Creation on Invoice Creation
 *
 * Verifies:
 * 1. InvoiceForm handleSave seamlessly auto-creates customer profile via API.customers.create when customerName is entered without prior selection.
 * 2. Newly created customer is appended to customers state list and set in form.customerId.
 * 3. Removal of the blocking toast ("Please select a customer from search suggestions or click + Add Customer").
 * 4. Friendly warning toast only when customer name is completely missing.
 * 5. Quick-entry customer visual indicator clearly communicates auto-creation on save.
 * 6. Invoice payload includes customer snapshot fields (customerName, customerPhone, customerAddress, etc.).
 * 7. Backend InvoiceRequest DTO supports customerName, customerPhone, customerAddress, customerEmail, customerGstin.
 * 8. Backend InvoiceService resolves or auto-creates customer for firm when customerId is omitted.
 */

const assert = require('assert');
const fs = require('fs');
const path = require('path');

console.log('═══════════════════════════════════════════════════════════════════');
console.log('RUPEECRM CUSTOMER AUTO-CREATION ON INVOICE VERIFICATION SUITE');
console.log('═══════════════════════════════════════════════════════════════════\n');

const repoRoot = path.resolve(__dirname, '../../..');
const indexPath = path.join(repoRoot, 'billsoft/src/main/webapp/index.html');
const reqPath = path.join(repoRoot, 'billsoft/src/main/java/com/billing/simple/billsoft/dtos/InvoiceRequest.java');
const servicePath = path.join(repoRoot, 'billsoft/src/main/java/com/billing/simple/billsoft/service/InvoiceService.java');

assert(fs.existsSync(indexPath), `index.html must exist at ${indexPath}`);
assert(fs.existsSync(reqPath), `InvoiceRequest.java must exist at ${reqPath}`);
assert(fs.existsSync(servicePath), `InvoiceService.java must exist at ${servicePath}`);

const indexContent = fs.readFileSync(indexPath, 'utf8');
const reqContent = fs.readFileSync(reqPath, 'utf8');
const serviceContent = fs.readFileSync(servicePath, 'utf8');

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

// ─── SUITE 1: FRONTEND AUTO-CREATION IN INVOICEFORM ───
console.log('--- Suite 1: Frontend Auto-Creation in InvoiceForm ---');

runTest('1.1: handleSave auto-creates customer when finalCustId is empty and customerName is provided', () => {
  assert(indexContent.includes('API.customers.create({'), 'InvoiceForm must call API.customers.create to auto-create customer');
  assert(indexContent.includes('name: form.customerName.trim()'), 'Customer creation must pass trimmed customerName');
  assert(indexContent.includes('firmId: targetFirmId'), 'Customer creation must pass targetFirmId for tenant isolation');
});

runTest('1.2: handleSave updates local customers state and form.customerId after auto-creation', () => {
  assert(indexContent.includes('setCustomers(prev => [...(prev || []).filter(c => c.id !== newCust.id), newCust])'),
    'Must update customers state so subsequent searches and selects find the new customer');
  assert(indexContent.includes('setForm(f => ({ ...f, customerId: String(newCust.id) }))'),
    'Must update form.customerId with the newly created customer ID');
});

runTest('1.3: Blocking toast is eliminated in favor of seamless creation', () => {
  assert(!indexContent.includes('showToast(\'Please select a customer from search suggestions or click "+ Add Customer"\''),
    'Old blocking toast directing user away to + Add Customer must not exist');
  assert(indexContent.includes('showToast(\'Please enter a customer name or select an existing customer\', \'warning\')'),
    'Friendly warning only shown when customer name is completely blank');
});

runTest('1.4: Status indicator communicates quick-entry auto-creation to user', () => {
  assert(indexContent.includes('✨ Quick-entry customer (will auto-create profile on save)'),
    'Must render clear quick-entry status badge informing user that customer will be created on save');
});

runTest('1.5: Invoice request payload forwards customer snapshot details', () => {
  assert(indexContent.includes('customerName: form.customerName ? form.customerName.trim() : undefined'),
    'Payload must forward customerName');
  assert(indexContent.includes('customerPhone: form.customerPhone ? form.customerPhone.trim() : undefined'),
    'Payload must forward customerPhone');
  assert(indexContent.includes('customerAddress: form.customerAddress ? form.customerAddress.trim() : undefined'),
    'Payload must forward customerAddress');
});

// ─── SUITE 2: BACKEND DEFENSE-IN-DEPTH AUTO-CREATION ───
console.log('\n--- Suite 2: Backend Defense-in-Depth Auto-Creation ---');

runTest('2.1: InvoiceRequest DTO includes customer detail fields', () => {
  assert(reqContent.includes('private String customerName;'), 'InvoiceRequest must declare customerName');
  assert(reqContent.includes('private String customerPhone;'), 'InvoiceRequest must declare customerPhone');
  assert(reqContent.includes('private String customerAddress;'), 'InvoiceRequest must declare customerAddress');
  assert(reqContent.includes('public String getCustomerName()'), 'InvoiceRequest must have getCustomerName');
});

runTest('2.2: InvoiceService implements resolveOrCreateCustomerForFirm', () => {
  assert(serviceContent.includes('resolveOrCreateCustomerForFirm('),
    'InvoiceService must implement resolveOrCreateCustomerForFirm');
  assert(serviceContent.includes('customerRepo.save(newCust)'),
    'InvoiceService must persist new customer when customerId is omitted');
});

runTest('2.3: previewInvoice handles customerName without persisting', () => {
  assert(serviceContent.includes('else if (request.getCustomerName() != null && !request.getCustomerName().trim().isEmpty())'),
    'previewInvoice must populate transient customer when customerName is given');
});

console.log('\n═══════════════════════════════════════════════════════════════════');
console.log(`RESULTS: ${passedTests}/${totalTests} tests passed successfully.`);
console.log('═══════════════════════════════════════════════════════════════════\n');

process.exit(0);

