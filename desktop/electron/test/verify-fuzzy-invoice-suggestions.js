/**
 * verify-fuzzy-invoice-suggestions.js
 * RupeeCRM — Automated Verification Suite for Fuzzy, Space-Insensitive,
 * Special-Char-Insensitive & Partial-Word Invoice Suggestions.
 */

const assert = require('assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

console.log('═══════════════════════════════════════════════════════════════════');
console.log('RUPEECRM FUZZY INVOICE SUGGESTIONS VERIFICATION SUITE');
console.log('═══════════════════════════════════════════════════════════════════\n');

const repoRoot = path.resolve(__dirname, '../../..');
const utilsPath = path.join(repoRoot, 'billsoft/src/main/webapp/js/utils.js');
const indexPath = path.join(repoRoot, 'billsoft/src/main/webapp/index.html');
const fuzzyJavaPath = path.join(repoRoot, 'billsoft/src/main/java/com/billing/simple/billsoft/util/FuzzySearchMatcher.java');
const productSvcPath = path.join(repoRoot, 'billsoft/src/main/java/com/billing/simple/billsoft/service/ProductService.java');
const customerSvcPath = path.join(repoRoot, 'billsoft/src/main/java/com/billing/simple/billsoft/service/CustomerService.java');
const savedItemSvcPath = path.join(repoRoot, 'billsoft/src/main/java/com/billing/simple/billsoft/service/SavedItemService.java');

assert(fs.existsSync(utilsPath), `utils.js must exist at ${utilsPath}`);
assert(fs.existsSync(indexPath), `index.html must exist at ${indexPath}`);
assert(fs.existsSync(fuzzyJavaPath), `FuzzySearchMatcher.java must exist at ${fuzzyJavaPath}`);
assert(fs.existsSync(productSvcPath), `ProductService.java must exist at ${productSvcPath}`);
assert(fs.existsSync(customerSvcPath), `CustomerService.java must exist at ${customerSvcPath}`);
assert(fs.existsSync(savedItemSvcPath), `SavedItemService.java must exist at ${savedItemSvcPath}`);

const utilsContent = fs.readFileSync(utilsPath, 'utf8');
const indexContent = fs.readFileSync(indexPath, 'utf8');
const fuzzyJavaContent = fs.readFileSync(fuzzyJavaPath, 'utf8');
const productSvcContent = fs.readFileSync(productSvcPath, 'utf8');
const customerSvcContent = fs.readFileSync(customerSvcPath, 'utf8');
const savedItemSvcContent = fs.readFileSync(savedItemSvcPath, 'utf8');

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

// ─── SUITE 1: UTILS.JS ALGORITHM VERIFICATION ───
console.log('--- Suite 1: BillsoftUtils Fuzzy Matching Implementation ---');

// Extract BillsoftUtils in sandbox
const sandbox = { window: {}, console };
vm.createContext(sandbox);
vm.runInContext(utilsContent, sandbox);
const BillsoftUtils = sandbox.window.BillsoftUtils;

runTest('1.1: BillsoftUtils exposes fuzzy matching functions', () => {
  assert(typeof BillsoftUtils.normalizeSearchText === 'function', 'normalizeSearchText must be exported');
  assert(typeof BillsoftUtils.toCompactAlphanumeric === 'function', 'toCompactAlphanumeric must be exported');
  assert(typeof BillsoftUtils.toDigitsOnly === 'function', 'toDigitsOnly must be exported');
  assert(typeof BillsoftUtils.scoreSearchMatch === 'function', 'scoreSearchMatch must be exported');
  assert(typeof BillsoftUtils.fuzzyFilterAndSort === 'function', 'fuzzyFilterAndSort must be exported');
});

const sampleProducts = [
  { id: 1, name: 'CPVC Ball Valve - 15 mm', category: 'CPVC Fitting', hsnCode: '84818090', barcode: '8901234567890' },
  { id: 2, name: 'CPVC Ball Valve - 20 mm', category: 'CPVC Fitting', hsnCode: '84818090', barcode: '' },
  { id: 3, name: 'CPVC Ball Valve - 25 mm', category: 'CPVC Fitting', hsnCode: '84818090', barcode: '' },
  { id: 4, name: 'UPVC Brass Elbow - 15 mm', category: 'UPVC Fitting', hsnCode: '74122000', barcode: '' },
  { id: 5, name: 'Stainless Steel Flange 150#', category: 'Piping', hsnCode: '73072100', barcode: '' }
];

const sampleCustomers = [
  { id: 101, name: 'Samrat Machinery Pvt Ltd', phone: '+91-98765-43210', gstin: '27AAAAA0000A1Z5', email: 'info@samratmachinery.com' },
  { id: 102, name: 'Samarth Tools & Hardware', phone: '9822012345', gstin: '', email: 'samarth@gmail.com' },
  { id: 103, name: 'Apex Engineering Works', phone: '020-25678900', gstin: '27BBBBB1111B1Z2', email: 'sales@apexengg.in' }
];

runTest('1.2: Case-insensitive product search', () => {
  const res = BillsoftUtils.fuzzyFilterAndSort(sampleProducts, 'cpvc', p => [p.name, p.category]);
  assert(res.length >= 3, 'Should find all CPVC items');
  assert(res[0].name.toLowerCase().includes('cpvc'));
});

runTest('1.3: Missing words / tokenized search ("cpvc 15")', () => {
  const res = BillsoftUtils.fuzzyFilterAndSort(sampleProducts, 'cpvc 15', p => [p.name, p.category]);
  assert(res.length >= 1, 'Must match CPVC 15mm');
  assert.strictEqual(res[0].name, 'CPVC Ball Valve - 15 mm');
});

runTest('1.4: Special character insensitivity ("cpvc-ball" and "15mm")', () => {
  const res1 = BillsoftUtils.fuzzyFilterAndSort(sampleProducts, 'cpvc-ball', p => [p.name, p.category]);
  assert(res1.length >= 3, 'Should match hyphens');

  const res2 = BillsoftUtils.fuzzyFilterAndSort(sampleProducts, '15mm', p => [p.name, p.category]);
  assert(res2.length >= 2, 'Should match 15mm with or without space');
  assert(res2.some(p => p.name === 'CPVC Ball Valve - 15 mm'));
});

runTest('1.5: Numeric token exactness ("valve 20" matches 20 mm, not 15 mm)', () => {
  const res = BillsoftUtils.fuzzyFilterAndSort(sampleProducts, 'valve 20', p => [p.name, p.category]);
  assert(res.length === 1, 'Only 20 mm should match');
  assert.strictEqual(res[0].name, 'CPVC Ball Valve - 20 mm');
});

runTest('1.6: Partial words / prefix matching ("bal val 15")', () => {
  const res = BillsoftUtils.fuzzyFilterAndSort(sampleProducts, 'bal val 15', p => [p.name, p.category]);
  assert(res.length >= 1, 'Must match Ball Valve 15mm');
  assert.strictEqual(res[0].name, 'CPVC Ball Valve - 15 mm');
});

runTest('1.7: Customer search with missing words ("samrat ltd")', () => {
  const res = BillsoftUtils.fuzzyFilterAndSort(sampleCustomers, 'samrat ltd', c => [c.name, c.phone]);
  assert(res.length >= 1, 'Must match Samrat Machinery Pvt Ltd');
  assert.strictEqual(res[0].name, 'Samrat Machinery Pvt Ltd');
});

runTest('1.8: Customer search by phone digits only ("9876543210")', () => {
  const res = BillsoftUtils.fuzzyFilterAndSort(sampleCustomers, '9876543210', c => [c.name, c.phone]);
  assert(res.length === 1, 'Must match formatted phone');
  assert.strictEqual(res[0].name, 'Samrat Machinery Pvt Ltd');
});

// ─── SUITE 2: FRONTEND INDEX.HTML INTEGRATION ───
console.log('--- Suite 2: Frontend Autocomplete Components Integration ---');

runTest('2.1: ProductSearchInput uses BillsoftUtils.fuzzyFilterAndSort', () => {
  assert(indexContent.includes('BillsoftUtils.fuzzyFilterAndSort(pool, q'), 'ProductSearchInput must use BillsoftUtils.fuzzyFilterAndSort');
  assert(indexContent.includes('p.name, p.category, p.sku, p.hsnCode'), 'ProductSearchInput must evaluate name, category, sku, hsnCode');
});

runTest('2.2: CustomerSearchInput uses BillsoftUtils.fuzzyFilterAndSort', () => {
  assert(indexContent.includes('BillsoftUtils.fuzzyFilterAndSort(uniqueList, q'), 'CustomerSearchInput must use BillsoftUtils.fuzzyFilterAndSort');
  assert(indexContent.includes('c.canonicalName || c.name'), 'CustomerSearchInput must evaluate name');
});

// ─── SUITE 3: BACKEND JAVA SERVICES INTEGRATION ───
console.log('--- Suite 3: Backend Java Services Integration ---');

runTest('3.1: FuzzySearchMatcher exists with full matching and scoring capabilities', () => {
  assert(fuzzyJavaContent.includes('public class FuzzySearchMatcher'), 'FuzzySearchMatcher class must exist');
  assert(fuzzyJavaContent.includes('public static <T> List<T> filterAndSort'), 'filterAndSort must exist');
  assert(fuzzyJavaContent.includes('public static int score('), 'score must exist');
  assert(fuzzyJavaContent.includes('public static String normalize('), 'normalize must exist');
  assert(fuzzyJavaContent.includes('public static String toCompact('), 'toCompact must exist');
});

runTest('3.2: ProductService.searchProducts integrates FuzzySearchMatcher', () => {
  assert(productSvcContent.includes('FuzzySearchMatcher.filterAndSort'), 'ProductService must call FuzzySearchMatcher.filterAndSort');
});

runTest('3.3: CustomerService.search integrates FuzzySearchMatcher', () => {
  assert(customerSvcContent.includes('FuzzySearchMatcher.filterAndSort'), 'CustomerService must call FuzzySearchMatcher.filterAndSort');
});

runTest('3.4: SavedItemService.searchUnifiedItems integrates FuzzySearchMatcher', () => {
  assert(savedItemSvcContent.includes('FuzzySearchMatcher.filterAndSort'), 'SavedItemService must call FuzzySearchMatcher.filterAndSort');
});

console.log(`\n═══════════════════════════════════════════════════════════════════`);
console.log(`ALL ${passedTests}/${totalTests} TESTS PASSED SUCCESSFULLY!`);
console.log(`═══════════════════════════════════════════════════════════════════\n`);
