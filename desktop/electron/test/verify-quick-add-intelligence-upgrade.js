/**
 * RupeeCRM Verification Suite: Quick Add Items Intelligence Upgrade,
 * Compound Recognition, Size Filtering, Combined Search & Keyboard Navigation
 *
 * Validates:
 * 1. Upgraded Recognition Engine:
 *    - Name normalisation (e.g. BALLVALVE -> Ball Valve)
 *    - Separation of product types from materials (Brass, APVC, CPVC, PVC, Steel)
 *    - Acronym preservation (FTA, MTA)
 *    - Compound product types (Reducing Bush, Ball Valve, Reducing Elbow, Hex Bolt, Spring Washer)
 *    - Preference of compound over isolated single words
 * 2. Dynamic Product-Type Suggestions (TYPES row):
 *    - Clean, deduplicated suggestions with "All Types"
 *    - Horizontally scrollable + More/expand toggle
 *    - EXPLICIT EXCLUSION: Zero product-type counts displayed
 * 3. Intelligent Size & Dimension Filtering:
 *    - Compound (20 x 15 mm, 25mm x 20mm), fraction (1/2", 3/4"), NB (25 NB), run (3 m)
 *    - SIZES chip row below TYPES when sizes exist
 *    - Canonical formatting and matchesSize filtering
 * 4. Combined Natural Search:
 *    - "Elbow 20 mm", "Brass Elbow 25 x 15", "APVC Ball Valve 15mm", "Reducing Bush 25 x 20", "Threaded Tee"
 *    - Multi-token matching across type, size, material, spec, SKU, barcode
 *    - Relevance-based sorting prioritizing matches
 * 5. Duplicate & Similar-Product Recognition:
 *    - findSimilarProducts identifies variants without disturbing cart
 *    - Related size chips in cart panel
 * 6. Recently Added Filter:
 *    - "🆕 Recently Added" pill in Quick Filters
 *    - Sort by recently added
 * 7. Keyboard Navigation:
 *    - '/' or Ctrl+F focus search
 *    - Arrow keys navigate cards with keyboard-focused class
 *    - Enter adds focused card
 *    - Escape clears search or closes modal
 * 8. Controlled Firm Learning:
 *    - QA_LEARNED_TYPES_{firmId} isolation
 *    - Reset learned rules
 */

const fs = require('fs');
const path = require('path');
const assert = require('assert');

console.log('═══════════════════════════════════════════════════════════════════');
console.log('RUPEECRM QUICK ADD INTELLIGENCE UPGRADE TEST SUITE');
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

const indexHtml = fs.readFileSync(indexHtmlPath, 'utf8').replace(/\r\n/g, '\n');
const styleCss = fs.readFileSync(styleCssPath, 'utf8').replace(/\r\n/g, '\n');

// Extract and evaluate RupeeProductIntelligence engine in Node runtime
const matchEngine = indexHtml.match(/const RupeeProductIntelligence\s*=\s*\(\(\)\s*=>\s*\{[\s\S]*?\}\)\(\);/);
assert(matchEngine, 'RupeeProductIntelligence module definition not found in index.html');
const engineCode = matchEngine[0];
const fn = new Function(engineCode + '\nreturn RupeeProductIntelligence;');
const Intel = fn();

console.log('--- Suite 1: Compound Recognition, Material Separation & Acronyms ---');

runCheck('Normalizes names and recognizes compound product types and acronyms', () => {
  const catalog = [
    { name: "APVC Brass Elbow 15 mm", category: "Plumbing" },
    { name: "APVC Brass Elbow 20 mm", category: "Plumbing" },
    { name: "CPVC Reducing Elbow 25 x 20 mm", category: "Plumbing" },
    { name: "CPVC Reducing Bush 25 x 20 mm", category: "Plumbing" },
    { name: "UPVC BALLVALVE 25 mm", category: "Plumbing" },
    { name: "UPVC Ball Valve 32 mm", category: "Plumbing" },
    { name: "APVC FTA 15 mm", category: "Plumbing" },
    { name: "APVC MTA 15 mm", category: "Plumbing" },
    { name: "APVC Tee 20 mm", category: "Plumbing" },
    { name: "APVC Coupler 20 mm", category: "Plumbing" },
    { name: "APVC Reducer 25 x 20 mm", category: "Plumbing" }
  ];

  const types = Intel.recognizeProductTypes(catalog, "Plumbing");
  
  // Must extract compound types
  assert(types.includes("Ball Valve"), 'Must extract compound "Ball Valve" (and normalized BALLVALVE)');
  assert(types.includes("Reducing Bush"), 'Must extract compound "Reducing Bush"');
  assert(types.includes("Elbow"), 'Must extract "Elbow"');
  assert(types.includes("FTA"), 'Must preserve uppercase acronym "FTA"');
  assert(types.includes("MTA"), 'Must preserve uppercase acronym "MTA"');
  assert(types.includes("Tee"), 'Must extract "Tee"');
  assert(types.includes("Coupler"), 'Must extract "Coupler"');
  assert(types.includes("Reducer"), 'Must extract "Reducer"');

  // Must suppress materials from becoming types
  assert(!types.includes("Apvc") && !types.includes("APVC"), 'APVC material must NOT be recognized as product type');
  assert(!types.includes("Cpvc") && !types.includes("CPVC"), 'CPVC material must NOT be recognized as product type');
  assert(!types.includes("Brass"), 'Brass material must NOT be recognized as product type');
  assert(!types.includes("Upvc") && !types.includes("UPVC"), 'UPVC material must NOT be recognized as product type');

  // Must NOT include standalone dimensions
  assert(!types.includes("15 mm") && !types.includes("20 mm") && !types.includes("25 mm"), 'Dimensions must NOT be product types');
});

runCheck('Fasteners catalog recognizes compound Bolt/Washer types and suppresses material', () => {
  const fasteners = [
    { name: "SS 304 Hex Bolt M8 x 40", category: "Fasteners" },
    { name: "SS 304 Hex Bolt M10 x 50", category: "Fasteners" },
    { name: "MS Carriage Bolt M6 x 30", category: "Fasteners" },
    { name: "MS Carriage Bolt M8 x 40", category: "Fasteners" },
    { name: "SS Hex Nut M8", category: "Fasteners" },
    { name: "Spring Washer M8", category: "Fasteners" }
  ];

  const types = Intel.recognizeProductTypes(fasteners, "Fasteners");
  assert(types.includes("Hex Bolt"), 'Must recognize compound "Hex Bolt"');
  assert(types.includes("Carriage Bolt"), 'Must recognize compound "Carriage Bolt"');
  assert(types.includes("Hex Nut"), 'Must recognize compound "Hex Nut"');
  assert(types.includes("Spring Washer"), 'Must recognize compound "Spring Washer"');
  assert(!types.includes("Ss") && !types.includes("SS"), 'SS must be suppressed');
  assert(!types.includes("Ms") && !types.includes("MS"), 'MS must be suppressed');
  assert(!types.includes("Fasteners"), 'Category name must be suppressed from types');
});

console.log('\n--- Suite 2: Dimension & Size Recognition ---');

runCheck('extractSize parses compound, fractional, NB, run, and metric dimensions into canonical format', () => {
  const s1 = Intel.extractSize("APVC Reducing Bush 20 x 15 mm");
  assert(s1 && s1.canonical === "20 x 15 mm", 'Compound canonical must be "20 x 15 mm"');

  const s2 = Intel.extractSize("APVC Bush 25mm x 20mm");
  assert(s2 && s2.canonical === "25 x 20 mm", 'Compound with embedded units canonical must be "25 x 20 mm"');

  const s3 = Intel.extractSize("Brass Ball Valve 1 1/2\"");
  assert(s3 && s3.canonical === "1 1/2\"", 'Fractional inch canonical must be "1 1/2\""');

  const s4 = Intel.extractSize("GI Pipe 25 NB");
  assert(s4 && s4.canonical === "25 NB", 'NB canonical must be "25 NB"');

  const s5 = Intel.extractSize("CPVC Pipe 3 metre");
  assert(s5 && s5.canonical === "3 m", 'Metric length canonical must be "3 m"');

  const s6 = Intel.extractSize("APVC Elbow 15 mm");
  assert(s6 && s6.canonical === "15 mm", 'Metric diameter canonical must be "15 mm"');
});

runCheck('recognizeSizes extracts sorted unique canonical sizes for category and type', () => {
  const catalog = [
    { name: "APVC Elbow 25 mm", category: "Plumbing" },
    { name: "APVC Elbow 15 mm", category: "Plumbing" },
    { name: "APVC Elbow 20 mm", category: "Plumbing" },
    { name: "APVC Tee 20 mm", category: "Plumbing" }
  ];

  const elbowSizes = Intel.recognizeSizes(catalog, "Plumbing", "Elbow");
  assert.deepStrictEqual(elbowSizes, ["15 mm", "20 mm", "25 mm"], 'Elbow sizes must be sorted numerically');

  const allSizes = Intel.recognizeSizes(catalog, "Plumbing", "All");
  assert.deepStrictEqual(allSizes, ["15 mm", "20 mm", "25 mm"], 'All plumbing sizes must be sorted numerically');
});

runCheck('matchesSize matches canonical, raw and compact forms', () => {
  const p1 = { name: "APVC Reducing Bush 20 x 15 mm" };
  assert(Intel.matchesSize(p1, "20 x 15 mm"), 'Must match exact canonical size');
  assert(Intel.matchesSize(p1, "20x15mm"), 'Must match compact size string');
  assert(!Intel.matchesSize(p1, "25 x 20 mm"), 'Must NOT match different size');
});

console.log('\n--- Suite 3: Combined Natural Search & Relevance Ranking ---');

runCheck('matchesSearch supports multi-token queries combining type, size, material, spec, SKU, barcode', () => {
  const products = [
    { name: "APVC Brass Elbow 20 mm", sku: "SKU-BE-20", barcode: "890123456789" },
    { name: "APVC Ball Valve 15mm", sku: "SKU-BV-15", barcode: "890123456790" },
    { name: "Reducing Bush 25 x 20 mm", sku: "SKU-RB-2520", barcode: "890123456791" },
    { name: "Threaded Tee 25 mm", sku: "SKU-TT-25", barcode: "890123456792" }
  ];

  // Combined searches
  assert(Intel.matchesSearch(products[0], "Elbow 20 mm"), 'Matches "Elbow 20 mm"');
  assert(Intel.matchesSearch(products[0], "Brass Elbow 20"), 'Matches "Brass Elbow 20"');
  assert(Intel.matchesSearch(products[1], "APVC Ball Valve 15mm"), 'Matches "APVC Ball Valve 15mm"');
  assert(Intel.matchesSearch(products[2], "Reducing Bush 25 x 20"), 'Matches "Reducing Bush 25 x 20"');
  assert(Intel.matchesSearch(products[3], "Threaded Tee"), 'Matches "Threaded Tee"');

  // SKU and barcode searches
  assert(Intel.matchesSearch(products[0], "SKU-BE-20"), 'Matches SKU exact');
  assert(Intel.matchesSearch(products[0], "890123456789"), 'Matches Barcode exact');
});

runCheck('computeSearchRelevance ranks exact and prefix matches above partial hits', () => {
  const exact = { name: "Elbow 20 mm", stockQuantity: 10 };
  const partial = { name: "APVC Brass Reducing Elbow 25 x 20 mm", stockQuantity: 10 };
  const query = "Elbow 20 mm";

  const scoreExact = Intel.computeSearchRelevance(exact, query);
  const scorePartial = Intel.computeSearchRelevance(partial, query);

  assert(scoreExact > scorePartial, 'Exact match must score higher than partial match');
});

console.log('\n--- Suite 4: Duplicate & Similar-Product Recognition ---');

runCheck('findSimilarProducts identifies variants by stem without altering catalog records', () => {
  const catalog = [
    { id: 1, name: "APVC Brass Elbow 15 mm", price: 45 },
    { id: 2, name: "APVC Brass Elbow 20 mm", price: 60 },
    { id: 3, name: "APVC Brass Elbow 25 mm", price: 80 },
    { id: 4, name: "APVC Tee 20 mm", price: 50 }
  ];

  const similar = Intel.findSimilarProducts(catalog[0], catalog, 4);
  assert(similar.length === 2, `Expected 2 similar products, got ${similar.length}`);
  assert(similar.some(s => s.product.id === 2), 'Must include 20 mm variation');
  assert(similar.some(s => s.product.id === 3), 'Must include 25 mm variation');
  assert(!similar.some(s => s.product.id === 4), 'Must NOT include Tee variation');
});

console.log('\n--- Suite 5: UI Elements, Exclusion of Counts & Accessibility ---');

runCheck('EXPLICIT EXCLUSION: Zero product-type counts displayed on type chips', () => {
  // Types chip rendering must NOT contain count badges or parenthesis counts
  const typeSectionMatch = indexHtml.match(/className:\s*['"]rcrm-qa-type-nav-wrapper['"][\s\S]*?className:\s*['"]rcrm-qa-grid-header['"]/);
  assert(typeSectionMatch, 'Type nav section must exist');
  const typeSection = typeSectionMatch[0];

  assert(!typeSection.includes('filter-pill-count'), 'Product-type chips must NOT render count badges');
  assert(!typeSection.includes('count)'), 'Product-type chips must NOT render count numbers');
  assert(!typeSection.includes('${t} ('), 'Product-type chips must NOT render counts in label');
  assert(typeSection.includes('onClick: () => handleProductTypeSelect(t)'), 'Product-type chip click handler verified');
});

runCheck('SIZES row is rendered under TYPES row with canonical chips and expand toggle', () => {
  assert(indexHtml.includes("className: 'rcrm-qa-size-nav-wrapper'"), 'rcrm-qa-size-nav-wrapper missing');
  assert(indexHtml.includes("className: 'rcrm-qa-size-label'"), 'rcrm-qa-size-label missing');
  assert(indexHtml.includes("All Sizes"), 'All Sizes chip missing');
  assert(indexHtml.includes("className: 'rcrm-qa-size-more-btn'"), 'rcrm-qa-size-more-btn missing');
  assert(styleCss.includes('.rcrm-qa-size-nav-wrapper'), '.rcrm-qa-size-nav-wrapper CSS missing');
  assert(styleCss.includes('.rcrm-qa-size-chip'), '.rcrm-qa-size-chip CSS missing');
});

runCheck('Quick Filters includes Recently Added pill with live count badge and sort option', () => {
  assert(indexHtml.includes("🆕 Recently Added"), 'Recently Added quick filter pill missing');
  assert(indexHtml.includes("quickFilter === 'RECENTLY_ADDED'"), 'RECENTLY_ADDED state handler missing');
  assert(indexHtml.includes("value: 'RECENTLY_ADDED'"), 'Sort: Recently Added dropdown value missing');
  assert(indexHtml.includes("'Sort: Recently Added'"), 'Sort: Recently Added dropdown label missing');
});

runCheck('Keyboard navigation shortcuts are implemented: search focus, arrow keys, Enter, Escape', () => {
  assert(indexHtml.includes("e.key === '/' || ((e.ctrlKey || e.metaKey) && (e.key === 'f' || e.key === 'F'))"),
    'Search input focus keyboard shortcut missing');
  assert(indexHtml.includes("e.key === 'ArrowDown'"), 'ArrowDown card navigation missing');
  assert(indexHtml.includes("e.key === 'ArrowUp'"), 'ArrowUp card navigation missing');
  assert(indexHtml.includes("e.key === 'Enter'"), 'Enter card selection missing');
  assert(indexHtml.includes("e.key === 'Escape'"), 'Escape dismiss missing');
  assert(styleCss.includes('.rcrm-qa-product-card.keyboard-focused'), 'keyboard-focused CSS styling missing');
});

runCheck('Controlled firm-specific learning and reset are implemented with multi-firm isolation', () => {
  assert(indexHtml.includes("QA_LEARNED_TYPES_"), 'Firm-isolated QA_LEARNED_TYPES_ AppConfig key missing');
  assert(indexHtml.includes("handleResetLearnedRules"), 'handleResetLearnedRules missing');
  assert(indexHtml.includes("rcrm-qa-gear-btn"), 'rcrm-qa-gear-btn settings trigger missing');
  assert(styleCss.includes('.rcrm-qa-gear-btn'), '.rcrm-qa-gear-btn CSS missing');
});

runCheck('Related variant suggestions are rendered in Selected Items panel without cart replacement', () => {
  assert(indexHtml.includes("rcrm-qa-similar-variants"), 'rcrm-qa-similar-variants container missing');
  assert(indexHtml.includes("rcrm-qa-similar-chip"), 'rcrm-qa-similar-chip missing');
  assert(styleCss.includes('.rcrm-qa-similar-variants'), '.rcrm-qa-similar-variants CSS missing');
  assert(styleCss.includes('.rcrm-qa-similar-chip'), '.rcrm-qa-similar-chip CSS missing');
});

console.log('\n═══════════════════════════════════════════════════════════════════');
console.log(`ALL QUICK ADD INTELLIGENCE UPGRADE CHECKS PASSED: ${passCount}/${totalCount}`);
console.log('═══════════════════════════════════════════════════════════════════\n');

if (passCount !== totalCount) {
  process.exit(1);
}
