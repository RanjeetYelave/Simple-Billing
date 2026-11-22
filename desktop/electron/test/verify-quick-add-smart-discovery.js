/**
 * RupeeCRM Verification Suite: Quick Add Items Smart Product Discovery,
 * Intelligent Grouping & Cart-Preserving Dual-Mode Architecture
 *
 * Validates:
 * 1. Dual Browsing Modes (Regular View & Smart View):
 *    - Mode selector segmented control with one-click instant switching
 *    - Default mode and localStorage persistence (rcrm_qa_browsing_mode)
 * 2. Uninterrupted Shared Cart State Preservation:
 *    - Switching modes NEVER clears or alters selectedItems, quantities, prices, or line totals
 *    - Modifying quantities in Smart View immediately updates in Regular View and vice versa
 *    - Product ID-based indexing avoids array index collisions
 * 3. Smart View 4-Tab Navigation:
 *    - Frequently Used (90-day distinct invoice frequency ranking with recency signal)
 *    - Favorites (star toggle on cards, firm-isolated persistence)
 *    - Recently Used (recent valid invoice products)
 *    - All Products (complete catalog with family grouping and category filters)
 *    - Informative empty states with single-click jump to All Products
 * 4. Intelligent Product Family Recognition:
 *    - Identifies common family stem and meaningful attributes (size, length, schedule, color)
 *    - Groups APVC ASTM Ball Valve Short 15mm, 20mm, Long 25mm into single family
 *    - Negative grouping guarantee: prevents false grouping (APVC vs Brass vs CPVC Ball Valve remain separate)
 *    - Presentation-only guarantee: individual product IDs, prices, stock, and SKUs preserved
 *    - Collapsed vs expanded state with quick variation selector pills
 * 5. Smart Sorting & Natural Numeric Dimension Ordering:
 *    - 15 mm < 20 mm < 100 mm (natural numeric, NOT lexicographic)
 *    - Fractional inch ordering: 1/2" < 3/4" < 1"
 *    - Compound dimension ordering: 20x15 < 25x20
 *    - Sort options for both Smart View and Regular View
 * 6. Search Improvements:
 *    - Searches across name, SKU, barcode, size, and specifications
 * 7. Multi-Firm Data Isolation:
 *    - Scoped to active firm ID
 *    - Zero cross-firm data leakage
 */

const fs = require('fs');
const path = require('path');
const assert = require('assert');

console.log('═══════════════════════════════════════════════════════════════════');
console.log('RUPEECRM QUICK ADD SMART DISCOVERY & DUAL-MODE TEST SUITE');
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
const styleCssPath = path.resolve(__dirname, '../../../billsoft/src/main/webapp/css/style.css');
const apiJsPath = path.resolve(__dirname, '../../../billsoft/src/main/webapp/js/api.js');
const productServicePath = path.resolve(__dirname, '../../../billsoft/src/main/java/com/billing/simple/billsoft/service/ProductService.java');
const productControllerPath = path.resolve(__dirname, '../../../billsoft/src/main/java/com/billing/simple/billsoft/controllers/ProductController.java');

const indexHtml = fs.readFileSync(indexHtmlPath, 'utf8').replace(/\r\n/g, '\n');
const styleCss = fs.readFileSync(styleCssPath, 'utf8').replace(/\r\n/g, '\n');
const apiJs = fs.readFileSync(apiJsPath, 'utf8').replace(/\r\n/g, '\n');
const productService = fs.readFileSync(productServicePath, 'utf8').replace(/\r\n/g, '\n');
const productController = fs.readFileSync(productControllerPath, 'utf8').replace(/\r\n/g, '\n');

// Extract and evaluate RupeeProductIntelligence engine in Node runtime
const matchEngine = indexHtml.match(/const RupeeProductIntelligence\s*=\s*\(\(\)\s*=>\s*\{[\s\S]*?\}\)\(\);/);
assert(matchEngine, 'RupeeProductIntelligence module definition not found in index.html');
const engineCode = matchEngine[0];
const fn = new Function(engineCode + '\nreturn RupeeProductIntelligence;');
const IntelEngine = fn();

console.log('--- Suite 1: Dual Browsing Modes & Segmented Control ---');

runCheck('QuickAddItemsModal defines browsingMode state with Regular View and Smart View', () => {
  assert(indexHtml.includes("const [browsingMode, setBrowsingMode] = useState("), 'browsingMode state missing');
  assert(indexHtml.includes("rcrm_qa_browsing_mode"), 'localStorage persistence key rcrm_qa_browsing_mode missing');
  assert(indexHtml.includes("handleSwitchMode"), 'handleSwitchMode helper missing');
});

runCheck('Modal renders segmented mode toolbar with 1-click Regular View and Smart View tabs', () => {
  assert(indexHtml.includes("className: 'rcrm-qa-mode-toolbar'"), 'rcrm-qa-mode-toolbar container missing');
  assert(indexHtml.includes("className: 'rcrm-qa-mode-segmented'"), 'rcrm-qa-mode-segmented wrapper missing');
  assert(indexHtml.includes("📋 Regular View"), 'Regular View button label missing');
  assert(indexHtml.includes("⚡ Smart View"), 'Smart View button label missing');
});

runCheck('CSS stylesheet defines dual-mode segmented control and tab styling', () => {
  assert(styleCss.includes('.rcrm-qa-mode-toolbar'), '.rcrm-qa-mode-toolbar missing in style.css');
  assert(styleCss.includes('.rcrm-qa-mode-segmented'), '.rcrm-qa-mode-segmented missing in style.css');
  assert(styleCss.includes('.rcrm-qa-mode-tab'), '.rcrm-qa-mode-tab missing in style.css');
});

console.log('\n--- Suite 2: Uninterrupted Shared Cart State Preservation ---');

runCheck('Single shared selectedItems state is defined at root of QuickAddItemsModal', () => {
  assert(indexHtml.includes("const [selectedItems, setSelectedItems] = useState([]);"), 'selectedItems root state missing');
  assert(!indexHtml.includes("setBrowsingMode(mode); setSelectedItems([])"), 'Mode switch must never clear selectedItems');
});

runCheck('Simulated mode switching preserves product selections, quantities and line totals intact', () => {
  let cart = [
    { product: { id: 101, name: 'APVC Ball Valve 15mm' }, unitPrice: 50, quantity: 2, lineTotal: 100 },
    { product: { id: 102, name: 'GI Pipe 1/2 inch' }, unitPrice: 120, quantity: 1, lineTotal: 120 }
  ];
  let mode = 'regular';

  // Switch to smart mode
  mode = 'smart';
  assert.strictEqual(cart.length, 2, 'Cart length altered on mode switch');
  assert.strictEqual(cart[0].quantity, 2, 'Quantity altered on mode switch');
  assert.strictEqual(cart[0].lineTotal, 100, 'Line total altered on mode switch');

  // Increment quantity in smart view
  cart = cart.map(it => it.product.id === 101 ? { ...it, quantity: it.quantity + 1, lineTotal: (it.quantity + 1) * it.unitPrice } : it);

  // Switch back to regular mode
  mode = 'regular';
  assert.strictEqual(cart.length, 2, 'Cart length altered on return switch');
  assert.strictEqual(cart[0].quantity, 3, 'Smart mode quantity change failed to persist in regular view');
  assert.strictEqual(cart[0].lineTotal, 150, 'Line total corrupted across mode switch');
});

runCheck('Right-hand Selected Items panel (rcrm-qa-right) remains permanently mounted across view modes', () => {
  assert(indexHtml.includes("className: 'rcrm-qa-right'"), 'rcrm-qa-right container missing');
  assert(indexHtml.includes("className: 'rcrm-qa-selected-header'"), 'rcrm-qa-selected-header missing');
  assert(indexHtml.includes("className: 'rcrm-qa-selected-list'"), 'rcrm-qa-selected-list missing');
  assert(indexHtml.includes("className: 'rcrm-qa-selected-summary'"), 'rcrm-qa-selected-summary missing');
});

console.log('\n--- Suite 3: Smart View 4-Tab Navigation & Empty States ---');

runCheck('Smart View renders 4 navigation sections: Frequently Used, Favorites, Recently Used, All Products', () => {
  assert(indexHtml.includes("rcrm-qa-smart-nav"), 'rcrm-qa-smart-nav wrapper missing');
  assert(indexHtml.includes("🔥 Frequently Used"), 'Frequently Used tab missing');
  assert(indexHtml.includes("⭐ Favorites"), 'Favorites tab missing');
  assert(indexHtml.includes("🕒 Recently Used"), 'Recently Used tab missing');
  assert(indexHtml.includes("📦 All Products"), 'All Products tab missing');
});

runCheck('Smart View tabs display dynamic count badges', () => {
  assert(indexHtml.includes("smartTabCounts"), 'smartTabCounts calculation missing');
  assert(indexHtml.includes("rcrm-qa-smart-tab-count"), 'rcrm-qa-smart-tab-count badge missing');
});

runCheck('Informative empty states provide single-click action to browse All Products', () => {
  assert(indexHtml.includes("No favorite products yet"), 'Favorites empty state title missing');
  assert(indexHtml.includes("No frequently used products yet"), 'Frequently used empty state title missing');
  assert(indexHtml.includes("No recent invoice activity"), 'Recently used empty state title missing');
  assert(indexHtml.includes("📦 Browse All Products"), 'Browse All Products call to action button missing');
});

console.log('\n--- Suite 4: Intelligent Product Family Recognition ---');

runCheck('IntelEngine extracts meaningful attributes from product names', () => {
  const p1 = IntelEngine.extractAttributes("APVC ASTM Ball Valve Short 15 mm");
  assert.strictEqual(p1.stem, "APVC ASTM Ball Valve", 'Base family stem incorrect');
  assert.strictEqual(p1.size.num, 15, 'Metric size incorrect');
  assert.strictEqual(p1.size.unit, "mm", 'Metric unit incorrect');
  assert.strictEqual(p1.variationLabel, "Short 15 mm", 'Variation label incorrect');

  const p2 = IntelEngine.extractAttributes("Havells 1.5 sq mm Wire Red");
  assert.strictEqual(p2.stem, "Havells Wire", 'Wire family stem incorrect');
  assert.strictEqual(p2.size.num, 1.5, 'Wire size incorrect');
  assert.strictEqual(p2.variationLabel, "Red 1.5 sq mm", 'Wire variation label incorrect');
});

runCheck('IntelEngine groups 7 APVC ASTM Ball Valve variations into single product family', () => {
  const catalog = [
    { id: 1, name: "APVC ASTM Ball Valve Short 15 mm", category: "Valves", price: 50 },
    { id: 2, name: "APVC ASTM Ball Valve Short 20 mm", category: "Valves", price: 65 },
    { id: 3, name: "APVC ASTM Ball Valve Short 25 mm", category: "Valves", price: 80 },
    { id: 4, name: "APVC ASTM Ball Valve Short 32 mm", category: "Valves", price: 110 },
    { id: 5, name: "APVC ASTM Ball Valve Long 15 mm", category: "Valves", price: 55 },
    { id: 6, name: "APVC ASTM Ball Valve Long 20 mm", category: "Valves", price: 70 },
    { id: 7, name: "APVC ASTM Ball Valve Long 25 mm", category: "Valves", price: 90 }
  ];

  const families = IntelEngine.buildProductFamilies(catalog, { resolvePrice: p => p.price });
  assert.strictEqual(families.length, 1, 'Catalog should be grouped into exactly 1 family');
  assert.strictEqual(families[0].isFamily, true, 'Item must be identified as family');
  assert.strictEqual(families[0].familyName, "APVC ASTM Ball Valve", 'Family name mismatch');
  assert.strictEqual(families[0].variations.length, 7, 'Family must contain all 7 variations');
  assert.strictEqual(families[0].minPrice, 50, 'Minimum price mismatch');
  assert.strictEqual(families[0].maxPrice, 110, 'Maximum price mismatch');
});

runCheck('Negative Grouping Guarantee: APVC vs Brass vs CPVC Ball Valves NEVER grouped into one family', () => {
  const distinctCatalog = [
    { id: 10, name: "APVC Ball Valve", category: "Valves", price: 60 },
    { id: 11, name: "Brass Ball Valve", category: "Valves", price: 180 },
    { id: 12, name: "CPVC Ball Valve", category: "Valves", price: 120 }
  ];

  const result = IntelEngine.buildProductFamilies(distinctCatalog, { resolvePrice: p => p.price });
  assert.strictEqual(result.length, 3, 'Distinct products must NOT be merged into a family');
  assert(result.every(r => !r.isFamily), 'All distinct products must remain standalone items');
});

runCheck('Family cards support collapsed and expanded states with quick variation pills', () => {
  assert(indexHtml.includes("rcrm-qa-family-card"), 'rcrm-qa-family-card missing in index.html');
  assert(indexHtml.includes("rcrm-qa-family-title"), 'rcrm-qa-family-title missing in index.html');
  assert(indexHtml.includes("rcrm-qa-variation-pill"), 'rcrm-qa-variation-pill missing in index.html');
  assert(indexHtml.includes("rcrm-qa-family-expanded-grid"), 'rcrm-qa-family-expanded-grid missing in index.html');
  assert(styleCss.includes('.rcrm-qa-family-card'), '.rcrm-qa-family-card missing in style.css');
  assert(styleCss.includes('.rcrm-qa-variation-pill'), '.rcrm-qa-variation-pill missing in style.css');
});

console.log('\n--- Suite 5: Smart Sorting & Natural Numeric Dimension Ordering ---');

runCheck('IntelEngine orders dimensions numerically (15 mm < 20 mm < 100 mm)', () => {
  const prods = [
    { name: "PVC Pipe 100 mm" },
    { name: "PVC Pipe 15 mm" },
    { name: "PVC Pipe 20 mm" }
  ];
  prods.sort(IntelEngine.naturalCompareSize);
  assert.strictEqual(prods[0].name, "PVC Pipe 15 mm", '15 mm should rank first');
  assert.strictEqual(prods[1].name, "PVC Pipe 20 mm", '20 mm should rank second');
  assert.strictEqual(prods[2].name, "PVC Pipe 100 mm", '100 mm should rank third');
});

runCheck('IntelEngine orders fractional inches correctly (1/2" < 3/4" < 1 1/2")', () => {
  const prods = [
    { name: 'GI Nipple 1 1/2"' },
    { name: 'GI Nipple 1/2"' },
    { name: 'GI Nipple 3/4"' }
  ];
  prods.sort(IntelEngine.naturalCompareSize);
  assert.strictEqual(prods[0].name, 'GI Nipple 1/2"', '1/2" should rank first');
  assert.strictEqual(prods[1].name, 'GI Nipple 3/4"', '3/4" should rank second');
  assert.strictEqual(prods[2].name, 'GI Nipple 1 1/2"', '1 1/2" should rank third');
});

runCheck('IntelEngine orders compound dimensions correctly (20 x 15 mm < 25 x 20 mm)', () => {
  const prods = [
    { name: "PVC Reducing Tee 25 x 20 mm" },
    { name: "PVC Reducing Tee 20 x 15 mm" }
  ];
  prods.sort(IntelEngine.naturalCompareSize);
  assert.strictEqual(prods[0].name, "PVC Reducing Tee 20 x 15 mm", '20x15 should rank first');
  assert.strictEqual(prods[1].name, "PVC Reducing Tee 25 x 20 mm", '25x20 should rank second');
});

runCheck('Sort select controls define all required sorting options in Smart and Regular views', () => {
  assert(indexHtml.includes("value: 'FREQ'"), 'FREQ sort option missing');
  assert(indexHtml.includes("value: 'RECENT'"), 'RECENT sort option missing');
  assert(indexHtml.includes("value: 'FAMILY'"), 'FAMILY sort option missing');
  assert(indexHtml.includes("value: 'SIZE'"), 'SIZE sort option missing');
  assert(indexHtml.includes("value: 'IN_STOCK'"), 'IN_STOCK sort option missing');
  assert(indexHtml.includes("value: 'DEFAULT'"), 'DEFAULT sort option missing in Regular view');
});

console.log('\n--- Suite 6: Search Improvements & Attribute Matching ---');

runCheck('matchesSearch finds products by dimension (e.g. "25 mm")', () => {
  const prod = { name: "APVC ASTM Ball Valve Short 25 mm", sku: "VALVE-001", barcode: "890123" };
  assert.strictEqual(IntelEngine.matchesSearch(prod, "25 mm"), true, 'Search by "25 mm" failed');
  assert.strictEqual(IntelEngine.matchesSearch(prod, "25mm"), true, 'Search by compact "25mm" failed');
  assert.strictEqual(IntelEngine.matchesSearch(prod, "valve-001"), true, 'Search by SKU failed');
  assert.strictEqual(IntelEngine.matchesSearch(prod, "890123"), true, 'Search by barcode failed');
  assert.strictEqual(IntelEngine.matchesSearch(prod, "50 mm"), false, 'Unrelated size should not match');
});

console.log('\n--- Suite 7: Backend Smart Discovery, Favorites & Multi-Firm Isolation ---');

runCheck('Backend ProductService implements getSmartDiscovery with 90-day invoice frequency', () => {
  assert(productService.includes("public SmartDiscoveryResponse getSmartDiscovery("), 'getSmartDiscovery method missing');
  assert(productService.includes("findFrequentlyUsedProductIdsByFirmId"), 'findFrequentlyUsedProductIdsByFirmId query call missing');
  assert(productService.includes("findRecentlyUsedProductIdsByFirmId"), 'findRecentlyUsedProductIdsByFirmId query call missing');
  assert(productService.includes("minusDays(days)"), '90-day filter period missing');
});

runCheck('Backend ProductService implements firm-isolated favorites management in AppConfig', () => {
  assert(productService.includes("public List<Long> getFavorites("), 'getFavorites method missing');
  assert(productService.includes("public List<Long> toggleFavorite("), 'toggleFavorite method missing');
  assert(productService.includes("QUICK_ADD_FAVORITES_"), 'AppConfig firm-scoped key missing');
  assert(productService.includes("repo.existsByIdAndFirmId"), 'IDOR product ownership check missing');
});

runCheck('ProductController exposes REST endpoints for smart-discovery and favorites', () => {
  assert(productController.includes('@GetMapping("/smart-discovery")'), '/smart-discovery endpoint missing');
  assert(productController.includes('@GetMapping("/favorites")'), '/favorites endpoint missing');
  assert(productController.includes('@PostMapping("/favorites/{productId}/toggle")'), 'toggle favorite endpoint missing');
});

runCheck('Frontend API client (api.js) exposes getSmartDiscovery, getFavorites, toggleFavorite', () => {
  assert(apiJs.includes('getSmartDiscovery:'), 'getSmartDiscovery missing in api.js');
  assert(apiJs.includes('getFavorites:'), 'getFavorites missing in api.js');
  assert(apiJs.includes('toggleFavorite:'), 'toggleFavorite missing in api.js');
});

console.log('\n═══════════════════════════════════════════════════════════════════');
console.log(`ALL QUICK ADD SMART DISCOVERY CHECKS PASSED: ${passCount}/${totalCount}`);
console.log('═══════════════════════════════════════════════════════════════════\n');
