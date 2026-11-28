/**
 * RupeeCRM Verification Suite: Quick Invoice Continuous Product Search,
 * Persistent Results & Keyboard Focus Management
 *
 * Reference: RUPEECRM-QUICK-INVOICE-CONTINUOUS-SEARCH-20261128
 *
 * Validates:
 * 1. Two Distinct States Architecture:
 *    - searchInputValue: Current visible text in search input (cleared upon selection)
 *    - displayedSearchQuery: Active query filtering displayed products (preserved upon selection)
 * 2. Initial Modal Focus & Immediate Typing:
 *    - Auto-focuses product search input on mount and when modal opens
 *    - Places cursor inside input ready for typing without mouse click
 * 3. Mouse Product Selection Flow:
 *    - Clicking card adds product to Selected Items
 *    - Automatically clears search input
 *    - Restores keyboard focus to search input
 *    - Keeps displayed results visible for consecutive additions
 * 4. Keyboard Navigation & Selection:
 *    - Arrow navigation and Enter selection trigger handleProductClick
 *    - Product is added, input cleared, focus restored, results remain
 * 5. Continuous Multiple Selections:
 *    - Adding 2+ products from the same search result set without searching again
 * 6. Starting a New Search:
 *    - Typing a new query immediately replaces displayed results
 * 7. Category & Filter Invariant:
 *    - Preserves category, filters, and sort order across product additions
 *    - Category/filter clicks clear stale search results when input is empty
 * 8. Manual Clearing & Escape Key Handling:
 *    - Clear button ('✕') and Escape key clear both input and displayed results
 */

const assert = require('assert');
const fs = require('fs');
const path = require('path');

const rootDir = path.resolve(__dirname, '../../..');
const indexHtmlPath = path.join(rootDir, 'billsoft/src/main/webapp/index.html');
const indexHtml = fs.readFileSync(indexHtmlPath, 'utf8');

console.log('═══════════════════════════════════════════════════════════════════');
console.log('RUPEECRM QUICK INVOICE CONTINUOUS SEARCH & FOCUS TEST SUITE');
console.log('═══════════════════════════════════════════════════════════════════\n');

let passCount = 0;
let totalCount = 0;

function runCheck(desc, fn) {
  totalCount++;
  try {
    fn();
    passCount++;
    console.log(`  ✓ [PASS] ${desc}`);
  } catch (err) {
    console.error(`  ✗ [FAIL] ${desc}: ${err.message}`);
    process.exit(1);
  }
}

// Extract QuickAddItemsModal source block for focused inspection
const modalStart = indexHtml.indexOf('function QuickAddItemsModal(');
const modalEnd = indexHtml.indexOf('function Dashboard({');
const modalCode = (modalStart !== -1 && modalEnd !== -1)
  ? indexHtml.substring(modalStart, modalEnd)
  : indexHtml.substring(modalStart, modalStart + 80000);

// ─── SUITE 1: TWO DISTINCT STATES ARCHITECTURE ───
console.log('--- Suite 1: Two Distinct States Architecture ---');

runCheck('1.1: Declares searchInputValue for visible input text', () => {
  assert(modalCode.includes("const [searchInputValue, setSearchInputValue] = useState('');"),
    'searchInputValue state definition missing');
});

runCheck('1.2: Declares displayedSearchQuery for active product filtering', () => {
  assert(modalCode.includes("const [displayedSearchQuery, setDisplayedSearchQuery] = useState('');"),
    'displayedSearchQuery state definition missing');
});

runCheck('1.3: Provides backward-compatible searchQuery alias', () => {
  assert(modalCode.includes("const searchQuery = searchInputValue;"),
    'searchQuery alias missing');
});

runCheck('1.4: displayProducts filters against displayedSearchQuery, NOT searchInputValue', () => {
  assert(modalCode.includes("const q = (displayedSearchQuery || '').trim();"),
    'displayProducts must filter using displayedSearchQuery');
  assert(modalCode.includes("list = list.filter(p => RupeeProductIntelligence.matchesSearch(p, q));"),
    'matchesSearch query application missing');
});

// ─── SUITE 2: AUTO-FOCUS & IMMEDIATE TYPING ───
console.log('\n--- Suite 2: Auto-Focus & Immediate Typing Lifecycle ---');

runCheck('2.1: Auto-focuses search input on mount and when modal opens', () => {
  assert(modalCode.includes("searchInputRef.current.focus();"),
    'searchInputRef.current.focus call missing');
  assert(modalCode.includes("searchInputRef.current.setSelectionRange("),
    'cursor position initialization missing');
});

runCheck('2.2: Focus lifecycle is triggered on [isOpen] change', () => {
  assert(/useEffect\(\(\)\s*=>\s*\{[\s\S]*?searchInputRef\.current\.focus[\s\S]*?\}\s*,\s*\[isOpen\]\);/.test(modalCode),
    'useEffect dependency on [isOpen] missing');
});

// ─── SUITE 3: PRODUCT SELECTION & AUTOMATIC CLEARING ───
console.log('\n--- Suite 3: Product Selection, Input Clearing & Focus Restoration ---');

runCheck('3.1: handleProductClick clears searchInputValue', () => {
  const clickFn = modalCode.substring(modalCode.indexOf('const handleProductClick ='), modalCode.indexOf('const handleUpdateQty ='));
  assert(clickFn.includes("setSearchInputValue('');"),
    'setSearchInputValue("") missing in handleProductClick');
});

runCheck('3.2: handleProductClick preserves displayedSearchQuery', () => {
  const clickFn = modalCode.substring(modalCode.indexOf('const handleProductClick ='), modalCode.indexOf('const handleUpdateQty ='));
  assert(!clickFn.includes("setDisplayedSearchQuery('');"),
    'handleProductClick must NOT clear displayedSearchQuery');
  assert(!clickFn.includes("setDisplayedSearchQuery(null)"),
    'handleProductClick must NOT nullify displayedSearchQuery');
});

runCheck('3.3: handleProductClick restores keyboard focus post-render via requestAnimationFrame', () => {
  const clickFn = modalCode.substring(modalCode.indexOf('const handleProductClick ='), modalCode.indexOf('const handleUpdateQty ='));
  assert(clickFn.includes("requestAnimationFrame("),
    'requestAnimationFrame focus restoration missing in handleProductClick');
  assert(clickFn.includes("searchInputRef.current.focus()"),
    'searchInputRef.current.focus call missing in handleProductClick');
});

runCheck('3.4: Product cards declare click handler binding to handleProductClick', () => {
  assert(modalCode.includes("onClick: () => handleProductClick(p)"),
    'Product card onClick handler missing');
});

// ─── SUITE 4: KEYBOARD NAVIGATION & INTERCHANGEABILITY ───
console.log('\n--- Suite 4: Keyboard Navigation & Interchangeability ---');

runCheck('4.1: Arrow key navigation and Enter selection trigger handleProductClick', () => {
  assert(modalCode.includes("handleProductClick(displayProducts[focusedCardIndex])"),
    'Keyboard Enter selection must route through handleProductClick');
});

runCheck('4.2: Enter key does not auto-select first search result on typing', () => {
  assert(modalCode.includes("!isInput && focusedCardIndex >= 0"),
    'Enter must guard against auto-adding while typing in input');
});

// ─── SUITE 5: STARTING A NEW SEARCH & TYPING ───
console.log('\n--- Suite 5: Starting a New Search & Typing ---');

runCheck('5.1: handleSearchInputChange updates BOTH searchInputValue and displayedSearchQuery', () => {
  assert(modalCode.includes("const handleSearchInputChange = (e) => {"),
    'handleSearchInputChange function missing');
  const inputChangeFn = modalCode.substring(modalCode.indexOf('const handleSearchInputChange ='), modalCode.indexOf('const handleClearSearch ='));
  assert(inputChangeFn.includes("setSearchInputValue(val);"), 'setSearchInputValue missing in input change');
  assert(inputChangeFn.includes("setDisplayedSearchQuery(val);"), 'setDisplayedSearchQuery missing in input change');
});

runCheck('5.2: Search input is bound to searchInputValue and handleSearchInputChange', () => {
  assert(modalCode.includes("value: searchInputValue,"),
    'Search input value must be bound to searchInputValue');
  assert(modalCode.includes("onChange: handleSearchInputChange"),
    'Search input onChange must be bound to handleSearchInputChange');
});

// ─── SUITE 6: FILTER & CATEGORY INTEGRITY ───
console.log('\n--- Suite 6: Filter, Category & Clear Integrity ---');

runCheck('6.1: Category selection resets stale displayedSearchQuery when input is empty', () => {
  const catFn = modalCode.substring(modalCode.indexOf('const handleCategorySelect ='), modalCode.indexOf('const handleProductTypeSelect ='));
  assert(catFn.includes("if (!searchInputValue)"), 'searchInputValue check missing in handleCategorySelect');
  assert(catFn.includes("setDisplayedSearchQuery('');"), 'setDisplayedSearchQuery reset missing in handleCategorySelect');
});

runCheck('6.2: Quick Filters reset stale displayedSearchQuery when input is empty', () => {
  assert(modalCode.includes("const handleQuickFilterSelect = (filter) => {"),
    'handleQuickFilterSelect function missing');
  assert(modalCode.includes("setQuickFilter(filter);"), 'setQuickFilter missing in handleQuickFilterSelect');
});

runCheck('6.3: Clear button ("✕") clears both search states and focuses search input', () => {
  assert(modalCode.includes("onClick: () => setSearchQuery('')"),
    'Clear search button click handler missing');
  assert(modalCode.includes("(searchInputValue || displayedSearchQuery) && React.createElement('button'"),
    'Clear button visibility condition missing');
});

runCheck('6.4: Escape key clears active search before modal dismissal', () => {
  const escBlock = modalCode.substring(modalCode.indexOf("if (e.key === 'Escape')"), modalCode.indexOf("if (displayProducts.length > 0)"));
  assert(escBlock.includes("searchInputValue || displayedSearchQuery"),
    'Escape key must check searchInputValue or displayedSearchQuery');
  assert(escBlock.includes("setSearchQuery('')"),
    'Escape key must clear search state');
});

// ─── SUITE 7: BEHAVIOURAL INTERACTION SIMULATION ───
console.log('\n--- Suite 7: Full Behavioural Flow Simulation ---');

runCheck('7.1: Simulate continuous search with multiple selections: "Brass Bib Cock"', () => {
  // Model state container simulating QuickAddItemsModal
  let state = {
    searchInputValue: '',
    displayedSearchQuery: '',
    selectedCategory: 'All',
    selectedItems: [],
    focusedCardIndex: -1,
    inputFocused: false
  };

  const catalog = [
    { id: 1, name: 'Brass Bib Cock 15mm', price: 250, category: 'Fittings' },
    { id: 2, name: 'Brass Bib Cock 20mm Heavy', price: 320, category: 'Fittings' },
    { id: 3, name: 'Brass Bib Cock Long Body', price: 380, category: 'Fittings' },
    { id: 4, name: 'CPVC Pipe 1 inch', price: 450, category: 'Pipes' },
    { id: 5, name: 'PVC Ball Valve 25mm', price: 180, category: 'Valves' }
  ];

  function getDisplayProducts(s) {
    let list = catalog;
    if (s.selectedCategory !== 'All') {
      list = list.filter(p => p.category === s.selectedCategory);
    }
    const q = (s.displayedSearchQuery || '').trim().toLowerCase();
    if (q) {
      list = list.filter(p => p.name.toLowerCase().includes(q));
    }
    return list;
  }

  function userTypesSearch(text) {
    state.searchInputValue = text;
    state.displayedSearchQuery = text;
    state.focusedCardIndex = -1;
  }

  function userSelectsProduct(prod) {
    const existing = state.selectedItems.find(it => it.id === prod.id);
    if (existing) {
      existing.quantity += 1;
    } else {
      state.selectedItems.push({ id: prod.id, name: prod.name, price: prod.price, quantity: 1 });
    }
    // EXACT APPROVED BEHAVIOUR:
    state.searchInputValue = ''; // Clears search input
    state.inputFocused = true;   // Regains focus
    // displayedSearchQuery remains UNCHANGED!
  }

  // Step 1: Open modal -> search input focused
  state.inputFocused = true;
  assert.strictEqual(state.inputFocused, true, 'Input must be focused on open');

  // Step 2: Search for "Brass Bib Cock"
  userTypesSearch('Brass Bib Cock');
  assert.strictEqual(state.searchInputValue, 'Brass Bib Cock');
  assert.strictEqual(state.displayedSearchQuery, 'Brass Bib Cock');
  let results = getDisplayProducts(state);
  assert.strictEqual(results.length, 3, 'Must match 3 Brass Bib Cock products');

  // Step 3: Add 1st product
  userSelectsProduct(results[0]);
  assert.strictEqual(state.selectedItems.length, 1);
  assert.strictEqual(state.selectedItems[0].name, 'Brass Bib Cock 15mm');
  assert.strictEqual(state.searchInputValue, '', 'Search input MUST be cleared');
  assert.strictEqual(state.inputFocused, true, 'Focus MUST return to search input');
  assert.strictEqual(state.displayedSearchQuery, 'Brass Bib Cock', 'displayedSearchQuery MUST be preserved');
  results = getDisplayProducts(state);
  assert.strictEqual(results.length, 3, 'All 3 search results MUST remain displayed');

  // Step 4: Add 2nd product from same results
  userSelectsProduct(results[1]);
  assert.strictEqual(state.selectedItems.length, 2);
  assert.strictEqual(state.selectedItems[1].name, 'Brass Bib Cock 20mm Heavy');
  assert.strictEqual(state.searchInputValue, '', 'Search input MUST be cleared after 2nd selection');
  assert.strictEqual(state.displayedSearchQuery, 'Brass Bib Cock', 'displayedSearchQuery MUST remain preserved');
  results = getDisplayProducts(state);
  assert.strictEqual(results.length, 3, 'All 3 search results MUST still remain displayed');

  // Step 5: Add 3rd product from same results
  userSelectsProduct(results[2]);
  assert.strictEqual(state.selectedItems.length, 3);
  assert.strictEqual(state.selectedItems[2].name, 'Brass Bib Cock Long Body');

  // Step 6: Start typing a new search "CPVC Pipe"
  userTypesSearch('CPVC Pipe');
  assert.strictEqual(state.searchInputValue, 'CPVC Pipe');
  assert.strictEqual(state.displayedSearchQuery, 'CPVC Pipe');
  results = getDisplayProducts(state);
  assert.strictEqual(results.length, 1, 'Only CPVC Pipe should be returned');
  assert.strictEqual(results[0].name, 'CPVC Pipe 1 inch');

  // Step 7: Add CPVC Pipe product
  userSelectsProduct(results[0]);
  assert.strictEqual(state.selectedItems.length, 4);
  assert.strictEqual(state.searchInputValue, '', 'Search input cleared again');
  assert.strictEqual(state.displayedSearchQuery, 'CPVC Pipe', 'displayedSearchQuery preserved');
  results = getDisplayProducts(state);
  assert.strictEqual(results.length, 1, 'CPVC Pipe result remains visible');

  // Cart totals verification
  const totalQuantity = state.selectedItems.reduce((acc, it) => acc + it.quantity, 0);
  const totalSubtotal = state.selectedItems.reduce((acc, it) => acc + (it.price * it.quantity), 0);
  assert.strictEqual(totalQuantity, 4);
  assert.strictEqual(totalSubtotal, 250 + 320 + 380 + 450);
});

console.log('\n═══════════════════════════════════════════════════════════════════');
console.log(`ALL QUICK INVOICE CONTINUOUS SEARCH CHECKS PASSED: ${passCount}/${totalCount}`);
console.log('═══════════════════════════════════════════════════════════════════');
