/**
 * verify-product-master-bulk-edit-modal.js
 * RupeeCRM — Automated Regression Test Suite for Product Master Bulk Edit Modal UI Standardization
 *
 * Verifies:
 * 1. ProductMasterBulkEditModal export & global window integration
 * 2. 2-line wrapped product name cell with textarea & WebkitLineClamp in CompactEditableGrid
 * 3. Centered modal dimensions (95vw, maxWidth: 1250px, maxHeight: 92vh, borderRadius: 14) matching Inventory Bulk Edit
 * 4. Product master columns definition (isolated from inventory physical stock reconciliation)
 * 5. Search, Category, Status, Needs Setup filters & Advanced Columns toggle
 * 6. Review Changes modal with 4 metric cards & diff table
 * 7. Unsaved changes confirmation dialog on close
 * 8. Clean return to pristine 7-column catalog table and docked pagination in ProductIntelligenceHub
 * 9. Non-null PATCH semantics and concurrency expectedCurrentStock payload
 */

const assert = require('assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

console.log('═══════════════════════════════════════════════════════════════════');
console.log('RUPEECRM PRODUCT MASTER BULK EDIT MODAL VERIFICATION SUITE');
console.log('═══════════════════════════════════════════════════════════════════\n');

function createTestHarness() {
  const confirmCalls = [];
  let confirmResponse = true;
  const toasts = [];

  const mockContext = {
    console,
    confirm: (msg) => {
      confirmCalls.push(msg);
      return confirmResponse;
    },
    showToast: (msg, type) => {
      toasts.push({ msg, type });
    },
    document: {
      createElement: () => ({ appendChild: () => {}, removeChild: () => {} }),
      body: { appendChild: () => {}, removeChild: () => {} }
    },
    window: {
      dispatchEvent: () => {},
      RupeeModalStack: {
        getZIndex: () => 10100
      }
    },
    setTimeout: (cb) => { cb(); },
    clearTimeout: () => {}
  };
  mockContext.window = mockContext;
  mockContext.self = mockContext;

  const ctx = vm.createContext(mockContext);

  const reactPath = path.resolve(__dirname, '../../../billsoft/src/main/webapp/js/vendor/react.production.min.js');
  vm.runInContext(fs.readFileSync(reactPath, 'utf8'), ctx);

  const gridPath = path.resolve(__dirname, '../../../billsoft/src/main/webapp/js/components/CompactEditableGrid.js');
  vm.runInContext(fs.readFileSync(gridPath, 'utf8'), ctx);

  const compPath = path.resolve(__dirname, '../../../billsoft/src/main/webapp/js/components/ProductIntelligence.js');
  vm.runInContext(fs.readFileSync(compPath, 'utf8'), ctx);

  return { ctx, confirmCalls, toasts };
}

let passedTests = 0;
let totalTests = 0;

function runTest(testName, fn) {
  totalTests++;
  try {
    fn();
    console.log(`  ✓ [PASS] ${testName}`);
    passedTests++;
  } catch (err) {
    console.error(`  ✗ [FAIL] ${testName}`);
    console.error(`    ${err.message}`);
    if (err.stack) console.error(err.stack.split('\n').slice(1, 4).join('\n'));
    process.exitCode = 1;
  }
}

const harness = createTestHarness();
const { ctx } = harness;
const gridSrc = fs.readFileSync(path.resolve(__dirname, '../../../billsoft/src/main/webapp/js/components/CompactEditableGrid.js'), 'utf8').replace(/\r\n/g, '\n');
const piSrc = fs.readFileSync(path.resolve(__dirname, '../../../billsoft/src/main/webapp/js/components/ProductIntelligence.js'), 'utf8').replace(/\r\n/g, '\n');

// ═══════════════════════════════════════════════════════════════════
// SECTION 1: EXPORT & COMPONENT REGISTRATION
// ═══════════════════════════════════════════════════════════════════
console.log('[SECTION 1] ProductMasterBulkEditModal Export & Registration');

runTest('1.1 ProductMasterBulkEditModal is exported on global window and module exports', () => {
  assert(ctx.ProductMasterBulkEditModal, 'ProductMasterBulkEditModal must be on global context');
  assert.strictEqual(typeof ctx.ProductMasterBulkEditModal, 'function', 'ProductMasterBulkEditModal must be a function');
  assert(piSrc.includes('root.ProductMasterBulkEditModal = exports.ProductMasterBulkEditModal;'), 'Must export to window root in UMD wrapper');
  assert(piSrc.includes('ProductMasterBulkEditModal,'), 'Must export in factory return object');
});

runTest('1.2 ProductIntelligenceHub renders ProductMasterBulkEditModal conditionally', () => {
  assert(piSrc.includes('showBulkEditModal && React.createElement(ProductMasterBulkEditModal,'), 'ProductIntelligenceHub must render ProductMasterBulkEditModal');
  assert(piSrc.includes("id: 'btn-product-master-bulk-edit'"), 'ProductIntelligenceHub must have bulk edit button');
});

// ═══════════════════════════════════════════════════════════════════
// SECTION 2: 2-LINE WRAPPED PRODUCT NAME IN COMPACT EDITABLE GRID
// ═══════════════════════════════════════════════════════════════════
console.log('\n[SECTION 2] CompactEditableGrid 2-Line Wrapping & Multi-Line Textarea');

runTest('2.1 CompactEditableGrid defines 38px row height for 2-line comfort', () => {
  assert(gridSrc.includes("height: 38, minHeight: 38"), 'CompactEditableGrid rows must have 38px height');
  assert(gridSrc.includes("verticalAlign: 'middle'"), 'CompactEditableGrid cell td must use middle vertical alignment');
});

runTest('2.2 CompactEditableGrid detects wrap columns via multiline or name key', () => {
  assert(gridSrc.includes("var isWrapCol = Boolean(col.multiline || col.key === 'name');"), 'Must identify wrap column by multiline property or key === name');
});

runTest('2.3 CompactEditableGrid renders 2-line clamped textarea in wrap column', () => {
  assert(gridSrc.includes("React.createElement('textarea'"), 'Must render textarea for multiline column');
  assert(gridSrc.includes("WebkitLineClamp: 2"), 'Textarea must have WebkitLineClamp 2');
  assert(gridSrc.includes("whiteSpace: 'normal'"), 'Textarea must allow normal white-space wrapping');
  assert(gridSrc.includes("wordBreak: 'break-word'"), 'Textarea must use break-word for long tokens');
  assert(gridSrc.includes("maxHeight: '38px'"), 'Textarea must constrain max height to cell bounds');
});

runTest('2.4 CompactEditableGrid strips internal newlines in onChange to preserve single logical value', () => {
  assert(gridSrc.includes("val = e.target.value.replace(/[\\r\\n]+/g, ' ');"), 'Must normalize newlines to single space in onChange');
});

runTest('2.5 CompactEditableGrid keyboard navigation handles TEXTAREA without newline insertion', () => {
  assert(gridSrc.includes("e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA'"), 'handleKeyDown must treat TEXTAREA as an input cell');
});

runTest('2.6 Read-only wrap column renders div with 2-line clamp and tooltip inspection', () => {
  assert(gridSrc.includes("WebkitLineClamp: 2"), 'Read-only wrap column must clamp at 2 lines');
  assert(gridSrc.includes("textOverflow: 'ellipsis'"), 'Read-only wrap column must ellipsis overflow');
  assert(gridSrc.includes("title: cellError || displayVal"), 'Read-only cell must offer title tooltip inspection');
});

runTest('2.7 Both Inventory and Product Master Bulk Edit configure multiline for Product Name', () => {
  assert(piSrc.includes("{ key: 'name', label: 'Product Name', minWidth: 190, required: true, multiline: true }"), 'InventoryBulkEditModal must enable multiline for Product Name');
  assert(piSrc.includes("{ key: 'name', label: 'Product Name', required: true, minWidth: 200, multiline: true }"), 'ProductMasterBulkEditModal must enable multiline for Product Name');
});

// ═══════════════════════════════════════════════════════════════════
// SECTION 3: MODAL LAYOUT & DIMENSIONS MATCHING INVENTORY BULK EDIT
// ═══════════════════════════════════════════════════════════════════
console.log('\n[SECTION 3] Centered Modal Architecture Matching Screenshot 1');

runTest('3.1 Modal card uses 95vw, maxWidth 1250px, maxHeight 92vh, borderRadius 14', () => {
  assert(piSrc.includes("width: '95vw', maxWidth: 1250, maxHeight: '92vh'"), 'ProductMasterBulkEditModal must use 95vw, maxWidth 1250, maxHeight 92vh');
  assert(piSrc.includes("borderRadius: 14"), 'ProductMasterBulkEditModal must have borderRadius 14');
  assert(piSrc.includes("background: 'rgba(15, 23, 42, 0.65)', backdropFilter: 'blur(4px)'"), 'Must use backdrop filter blur');
});

runTest('3.2 Modal header displays icon, title, and descriptive subtitle', () => {
  assert(piSrc.includes("'Product Master Bulk Edit'"), 'Header title must be Product Master Bulk Edit');
  assert(piSrc.includes("'Edit multiple product records directly across prices, categories, HSN, tax, and inventory settings.'"), 'Header subtitle must match expected description');
});

runTest('3.3 Modal toolbar includes search, categories, status filter, needs setup, and advanced columns', () => {
  assert(piSrc.includes("placeholder: '🔍 Search product name, HSN, barcode...'"), 'Search placeholder must match specification');
  assert(piSrc.includes("'All Categories'"), 'Category selector must include All Categories');
  assert(piSrc.includes("'⚠️ UNCATEGORIZED'"), 'Category selector must include UNCATEGORIZED option');
  assert(piSrc.includes("'All Status'"), 'Status filter must include All Status');
  assert(piSrc.includes("'📦 In Inventory'"), 'Status filter must include In Inventory');
  assert(piSrc.includes("'📄 Catalog Only'"), 'Status filter must include Catalog Only');
  assert(piSrc.includes("Needs Setup ("), 'Needs Setup button must display count');
  assert(piSrc.includes("'Show Advanced Columns (SKU, Barcode)'"), 'Advanced columns toggle checkbox must be present in toolbar');
  assert(piSrc.includes("`Showing ${displayedRows.length} of ${rows.length} products (${changedProductIds.length} modified)`"),
    'Toolbar must display accurate visible/total product and modification counts');
});

// ═══════════════════════════════════════════════════════════════════
// SECTION 4: PRODUCT MASTER FIELD DEFINITION & ISOLATION
// ═══════════════════════════════════════════════════════════════════
console.log('\n[SECTION 4] Column Definitions & Field Isolation');

runTest('4.1 Master columns include all required Product Master commercial & catalog fields', () => {
  assert(piSrc.includes("{ key: 'name', label: 'Product Name'"), 'Must include Product Name');
  assert(piSrc.includes("{ key: 'hsnCode', label: 'HSN/SAC Code', width: 110 }"), 'Must include HSN/SAC');
  assert(piSrc.includes("{ key: 'category', label: 'Category', datalistId: 'pm-bulk-cat-list'"), 'Must include Category with datalist');
  assert(piSrc.includes("{ key: 'price', label: 'Base Price (₹)'"), 'Must include Base Price');
  assert(piSrc.includes("{ key: 'costPrice', label: 'Cost Price (₹)'"), 'Must include Cost Price');
  assert(piSrc.includes("{ key: 'minStockLevel', label: 'Min Stock'"), 'Must include Min Stock');
  assert(piSrc.includes("{ key: 'unit', label: 'Unit'"), 'Must include Unit');
  assert(piSrc.includes("key: 'gstPercentage', label: 'GST %'"), 'Must include GST %');
  assert(piSrc.includes("key: 'preferredPartyId', label: 'Preferred Vendor'"), 'Must include Preferred Vendor');
  assert(piSrc.includes("key: 'trackInInventory', label: 'Track Stock'"), 'Must include Track Stock');
  assert(piSrc.includes("key: 'stockQuantity', label: 'Stock', width: 75, align: 'right', readOnly: true"), 'Must include read-only Stock');
  assert(piSrc.includes("{ key: 'sku', label: 'SKU Code', isAdvanced: true"), 'Must include advanced SKU');
  assert(piSrc.includes("{ key: 'barcode', label: 'Barcode', isAdvanced: true"), 'Must include advanced Barcode');
});

runTest('4.2 Master columns do NOT contain inventory physical reconciliation columns', () => {
  const masterModalMatch = piSrc.match(/function ProductMasterBulkEditModal[\s\S]*?const masterColumns = useMemo\(\(\) => (\[[\s\S]*?\]),/);
  assert(masterModalMatch, 'masterColumns must be defined inside ProductMasterBulkEditModal');
  const colsText = masterModalMatch[1];
  assert(!colsText.includes('physicalCount'), 'ProductMasterBulkEditModal must NOT contain physicalCount');
  assert(!colsText.includes('adjustmentDelta'), 'ProductMasterBulkEditModal must NOT contain adjustmentDelta');
  assert(!colsText.includes('Physical Count'), 'ProductMasterBulkEditModal must NOT contain Physical Count label');
});

// ═══════════════════════════════════════════════════════════════════
// SECTION 5: REVIEW CONFIRMATION MODAL & METRIC CARDS
// ═══════════════════════════════════════════════════════════════════
console.log('\n[SECTION 5] Review Changes Confirmation Modal & Metrics');

runTest('5.1 Review modal contains 4 summary metric cards', () => {
  assert(piSrc.includes("'Products Changed'"), 'Must display Products Changed card');
  assert(piSrc.includes("'Category Updates'"), 'Must display Category Updates card');
  assert(piSrc.includes("'Price/Cost Changes'"), 'Must display Price/Cost Changes card');
  assert(piSrc.includes("'Inventory Onboardings'"), 'Must display Inventory Onboardings card');
});

runTest('5.2 Review modal diff table presents old vs new values with styling', () => {
  assert(piSrc.includes("textDecoration: 'line-through'"), 'Diff table must strike through old values');
  assert(piSrc.includes("color: '#10b981'"), 'Diff table must highlight new values in green');
  assert(piSrc.includes("'💾 Confirm & Save Changes'"), 'Confirm button must match specification');
});

// ═══════════════════════════════════════════════════════════════════
// SECTION 6: UNSAVED CHANGES CONFIRMATION DIALOG
// ═══════════════════════════════════════════════════════════════════
console.log('\n[SECTION 6] Unsaved Changes Dialog & Data Protection');

runTest('6.1 Requesting close with zero changes exits immediately', () => {
  assert(piSrc.includes('if (totalChangesCount > 0)') && piSrc.includes('setShowUnsavedPrompt(true);') && piSrc.includes('onClose();'), 'handleRequestClose must prompt only if totalChangesCount > 0');
});

runTest('6.2 Unsaved changes modal informs user of modified product/field counts', () => {
  assert(piSrc.includes("You have ${totalChangesCount} unsaved field changes across ${changedProductIds.length} product(s)"), 'Dialog must state exact counts of modified products and fields');
  assert(piSrc.includes("'Continue Editing'"), 'Dialog must provide Continue Editing button');
  assert(piSrc.includes("'Discard & Close'"), 'Dialog must provide Discard & Close button');
});

// ═══════════════════════════════════════════════════════════════════
// SECTION 7: CLEAN RETURN TO CATALOG & BANNER REMOVAL
// ═══════════════════════════════════════════════════════════════════
console.log('\n[SECTION 7] Clean Return to Pristine Catalog');

runTest('7.1 Inline workspace editing banner is completely removed from ProductIntelligenceHub', () => {
  assert(!piSrc.includes('Bulk Product Master Edit Workspace'), 'Inline banner Bulk Product Master Edit Workspace must NOT be present in source');
  assert(!piSrc.includes('Multi-Item Workspace'), 'Inline badge Multi-Item Workspace must NOT be present in source');
});

runTest('7.2 ProductIntelligenceHub preserves pristine 7-column catalog table and docked pagination', () => {
  assert(piSrc.includes("className: 'table-container card firm-table-card'"), 'Catalog table container must be preserved');
  assert(piSrc.includes("className: 'firm-docked-pagination'"), 'Docked pagination must be preserved');
  assert(piSrc.includes("'Product Name'"), 'Product Name column header must be preserved');
  assert(piSrc.includes("'HSN/SAC'"), 'HSN/SAC column header must be preserved');
  assert(piSrc.includes("'Category'"), 'Category column header must be preserved');
  assert(piSrc.includes("'Commercials'"), 'Commercials column header must be preserved');
  assert(piSrc.includes("'Stock Status'"), 'Stock Status column header must be preserved');
  assert(piSrc.includes("'Tax & Vendor'"), 'Tax & Vendor column header must be preserved');
  assert(piSrc.includes("'Actions'"), 'Actions column header must be preserved');
});

runTest('7.3 Table row Edit button launches modal with target product prefilled in search', () => {
  assert(piSrc.includes('setBulkEditInitialProdId(p.id);') && piSrc.includes('setShowBulkEditModal(true);'), 'Row Edit button must set initial product ID and open modal');
  assert(piSrc.includes('setSearchQuery(target.name);'), 'ProductMasterBulkEditModal must initialize search query with selected product name');
});

// ═══════════════════════════════════════════════════════════════════
// SECTION 8: PAYLOAD CONCURRENCY & NON-NULL PATCH INTEGRITY
// ═══════════════════════════════════════════════════════════════════
console.log('\n[SECTION 8] Payload Concurrency & Non-Null PATCH Semantics');

runTest('8.1 Save payload includes expectedCurrentStock for optimistic concurrency protection', () => {
  assert(piSrc.includes("row.expectedCurrentStock = Number(original.stockQuantity || 0);"), 'Save payload must include expectedCurrentStock');
});

runTest('8.2 SKU and Barcode are omitted unless explicitly modified', () => {
  assert(piSrc.includes("if (changes.sku !== undefined) row.sku = (changes.sku || '').trim();"), 'SKU must be omitted if not changed');
  assert(piSrc.includes("if (changes.barcode !== undefined) row.barcode = (changes.barcode || '').trim();"), 'Barcode must be omitted if not changed');
});

runTest('8.3 Client validations guard against empty name and negative numeric values', () => {
  assert(piSrc.includes("Product name cannot be empty"), 'Must validate product name');
  assert(piSrc.includes("Base selling price cannot be negative"), 'Must validate selling price');
  assert(piSrc.includes("Cost price cannot be negative"), 'Must validate cost price');
  assert(piSrc.includes("Min stock level cannot be negative"), 'Must validate min stock level');
});

// ═══════════════════════════════════════════════════════════════════
// SUMMARY
// ═══════════════════════════════════════════════════════════════════
console.log('\n═══════════════════════════════════════════════════════════════════');
console.log(`TEST SUMMARY: ${passedTests} / ${totalTests} PASSED (${Math.round((passedTests / totalTests) * 100)}% SUCCESS)`);
console.log('═══════════════════════════════════════════════════════════════════\n');

if (passedTests !== totalTests) {
  process.exit(1);
}
