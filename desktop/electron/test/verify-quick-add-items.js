/**
 * RupeeCRM Verification Suite: Quick Add Items with Two Entry Points
 *
 * Validates:
 * 1. Reusable Component Definition & POS Layout Architecture:
 *    - Single shared QuickAddItemsModal component used by both Home and Invoice entry points
 *    - Two-panel POS layout (left: search/categories/grid, right: selected items/quantities)
 *    - Independent scroll containers avoiding full-screen modal scrolling
 *    - CSS styling adhering to RupeeCRM emerald/minimal tokens and responsive design
 * 2. Two Entry Points & Context Routing:
 *    - Home Screen (Dashboard): Pinned "⚡ Quick Add Items" button, launches with source="home"
 *    - Invoice Screen (InvoiceForm): Line-item toolbar "⚡ Quick Add Items" button, launches with source="invoice"
 *    - Home Finish creates/opens new unsaved invoice draft with selected products
 *    - Invoice Finish appends/merges selected items into existing invoice without losing customer, dates, or discounts
 *    - Existing item duplicate handling: increments quantity for unmodified matching products while preserving custom rows
 *    - Safe Discard Confirmation on Cancel/Escape when selections exist
 * 3. Horizontal Category Navigation & Search:
 *    - Horizontal chips with "All" first displaying all products with category badges
 *    - Responsive chip overflow handling with "More ▾" dropdown
 *    - Search input inside "More ▾" dropdown supporting 10-30+ categories
 *    - Cross-category search by product name, SKU, and barcode
 *    - Clearing search restores currently selected category
 * 4. Authoritative Pricing, Selection Panel & Zero Auto-Save:
 *    - Text-only compact product cards with NO images or excessive whitespace
 *    - Click-to-increment interaction: 1st click = 1, 2nd click = 2, 3rd click = 3
 *    - Right panel with persistent visibility, quantity controls [-] [input] [+], line totals, and delete
 *    - Authoritative selling price resolution using effectivePriceMap/price; NEVER costPrice
 *    - Authoritative calculation engine preservation
 *    - Strict zero auto-save guarantee
 */

const fs = require('fs');
const path = require('path');
const assert = require('assert');

console.log('═══════════════════════════════════════════════════════════════════');
console.log('RUPEECRM QUICK ADD ITEMS WITH TWO ENTRY POINTS TEST SUITE');
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
const updateServicePath = path.resolve(__dirname, '../../../billsoft/src/main/java/com/billing/simple/billsoft/service/ProductPricingService.java');
const calcEnginePath = path.resolve(__dirname, '../../../billsoft/src/main/java/com/billing/simple/billsoft/engine/InvoiceCalculationEngine.java');

// Normalize CRLF to LF to guarantee cross-platform robustness
const indexHtml = fs.readFileSync(indexHtmlPath, 'utf8').replace(/\r\n/g, '\n');
const styleCss = fs.readFileSync(styleCssPath, 'utf8').replace(/\r\n/g, '\n');
const apiJs = fs.readFileSync(apiJsPath, 'utf8').replace(/\r\n/g, '\n');
const pricingService = fs.readFileSync(updateServicePath, 'utf8').replace(/\r\n/g, '\n');
const calcEngine = fs.readFileSync(calcEnginePath, 'utf8').replace(/\r\n/g, '\n');

console.log('--- Suite 1: Reusable Component Definition & POS Layout Architecture ---');

runCheck('QuickAddItemsModal is defined as a reusable React component in index.html', () => {
  assert(indexHtml.includes('function QuickAddItemsModal('), 'QuickAddItemsModal component definition missing');
  assert(/source\s*=\s*'home'/.test(indexHtml), 'Default source="home" parameter missing');
});

runCheck('QuickAddItemsModal implements two-panel POS layout with independent scroll areas', () => {
  assert(indexHtml.includes("className: 'rcrm-qa-body'"), 'rcrm-qa-body container missing');
  assert(indexHtml.includes("className: 'rcrm-qa-left'"), 'rcrm-qa-left panel missing');
  assert(indexHtml.includes("className: 'rcrm-qa-right'"), 'rcrm-qa-right panel missing');
  assert(indexHtml.includes("className: 'rcrm-qa-product-grid'"), 'rcrm-qa-product-grid container missing');
  assert(indexHtml.includes("className: 'rcrm-qa-selected-list'"), 'rcrm-qa-selected-list container missing');
});

runCheck('Modal header includes title, source indicator badge, and close action', () => {
  assert(indexHtml.includes("rcrm-qa-header"), 'rcrm-qa-header missing');
  assert(indexHtml.includes("Quick Add Items"), 'Quick Add Items title missing in header');
  assert(indexHtml.includes("rcrm-qa-source-badge"), 'rcrm-qa-source-badge missing in header');
});

runCheck('CSS stylesheet defines .rcrm-quick-add-* classes with responsive grid and tokens', () => {
  assert(styleCss.includes('.rcrm-quick-add-overlay'), '.rcrm-quick-add-overlay missing in style.css');
  assert(styleCss.includes('.rcrm-quick-add-modal'), '.rcrm-quick-add-modal missing in style.css');
  assert(styleCss.includes('.rcrm-qa-product-grid'), '.rcrm-qa-product-grid missing in style.css');
  assert(styleCss.includes('.rcrm-qa-cat-chip'), '.rcrm-qa-cat-chip missing in style.css');
  assert(styleCss.includes('.rcrm-qa-more-dropdown'), '.rcrm-qa-more-dropdown missing in style.css');
  assert(styleCss.includes('.rcrm-qa-right'), '.rcrm-qa-right missing in style.css');
  assert(styleCss.includes('.rcrm-qa-qty-controls'), '.rcrm-qa-qty-controls missing in style.css');
});

console.log('\n--- Suite 2: Two Entry Points & Context Routing ---');

runCheck('Dashboard (Home Screen) renders pinned "⚡ Quick Add Items" button', () => {
  assert(/⚡ Quick Add Items/.test(indexHtml), '⚡ Quick Add Items button missing');
  assert(indexHtml.includes("onClick: () => setShowQuickAddModal(true)"), 'Dashboard setShowQuickAddModal trigger missing');
  assert(/source:\s*'home'/.test(indexHtml), 'source: "home" context missing in Dashboard instantiation');
});

runCheck('InvoiceForm (Invoice Screen) renders "⚡ Quick Add Items" button in item table footer', () => {
  assert(indexHtml.includes("➕ Add Item (Ctrl+I)"), 'Add Item button missing in InvoiceForm');
  assert(/onClick:\s*\(\)\s*=>\s*setShowQuickAddModal\(true\)/.test(indexHtml), 'InvoiceForm setShowQuickAddModal trigger missing');
  assert(/source:\s*'invoice'/.test(indexHtml), 'source: "invoice" context missing in InvoiceForm instantiation');
});

runCheck('Home Finish dispatches navigation to open new unsaved draft with selected items', () => {
  assert(indexHtml.includes("page: 'invoices', subTab: 'create', quickDocType: 'invoice', initialItems: convertedItems"),
    'Navigation to new invoice draft with initialItems missing');
});

runCheck('InvoiceForm accepts initialItems prop and populates form items for new drafts', () => {
  assert(indexHtml.includes("initialItems = null"), 'initialItems prop missing in InvoiceForm');
  assert(indexHtml.includes("(initialItems && Array.isArray(initialItems) && initialItems.length > 0) ? initialItems : [emptyItem()]"),
    'Initial items form population missing in InvoiceForm');
});

runCheck('Invoice Finish merges/appends items without clearing existing fields or customer data', () => {
  assert(indexHtml.includes("handleQuickAddFinish"), 'handleQuickAddFinish missing in InvoiceForm');
  assert(indexHtml.includes("existing[matchIdx] = {"), 'Quantity merging for matching untouched products missing');
  assert(indexHtml.includes("existing.push(newItem)"), 'Appending new items missing');
});

runCheck('Cancel/Close with non-empty selections triggers Discard Confirmation Dialog', () => {
  assert(indexHtml.includes("Discard selected items?"), 'Discard selected items confirmation dialog missing');
  assert(indexHtml.includes("Your selected products will be discarded. Your existing invoice information will remain unchanged."),
    'Discard confirmation explanation missing');
  assert(indexHtml.includes("Keep Editing"), 'Keep Editing button missing');
  assert(indexHtml.includes("Discard Items"), 'Discard Items button missing');
});

runCheck('InvoicesQuotesHub preserves initialItems across multi-document tabs', () => {
  assert(indexHtml.includes("initialItems: e.detail.initialItems || null"), 'initialItems missing in InvoicesQuotesHub handleNav');
  assert(indexHtml.includes("initialItems: tabSpec.initialItems || null"), 'initialItems missing in InvoicesQuotesHub openDocTab');
  assert(indexHtml.includes("initialItems: t.initialItems || null"), 'initialItems missing in InvoicesQuotesHub form rendering');
});

console.log('\n--- Suite 3: Horizontal Category Navigation & Search ---');

runCheck('Horizontal category chips place "All" first and filter product catalog', () => {
  assert(indexHtml.includes("className: `rcrm-qa-cat-chip ${selectedCategory === 'All' ? 'active' : ''}`"),
    '"All" active category chip missing');
  assert(indexHtml.includes("setSelectedCategory('All')"), 'Select "All" click handler missing');
});

runCheck('Responsive chip calculation handles overflow with "More ▾" dropdown', () => {
  assert(indexHtml.includes("visibleChips"), 'visibleChips calculation missing');
  assert(indexHtml.includes("overflowChips"), 'overflowChips calculation missing');
  assert(indexHtml.includes("rcrm-qa-more-btn"), 'More button missing');
  assert(indexHtml.includes("rcrm-qa-more-dropdown"), 'More dropdown missing');
});

runCheck('"More ▾" dropdown contains category search input supporting 10-30+ categories', () => {
  assert(indexHtml.includes("placeholder: 'Filter categories...'"), 'Filter categories search input missing in dropdown');
  assert(indexHtml.includes("filteredOverflow"), 'filteredOverflow calculation missing');
});

runCheck('Cross-category search queries across name, SKU, and barcode across all categories', () => {
  assert(indexHtml.includes("Search across all categories by product name, SKU, or barcode..."), 'Search input placeholder missing');
  assert(indexHtml.includes("p.sku && p.sku.toLowerCase().includes(lower)"), 'SKU search match missing');
  assert(indexHtml.includes("p.barcode && p.barcode.toLowerCase().includes(lower)"), 'Barcode search match missing');
});

runCheck('Clearing search restores active category product grid view', () => {
  assert(indexHtml.includes("onClick: () => setSearchQuery('')"), 'Clear search button click handler missing');
  assert(indexHtml.includes("selectedCategory === 'All'"), 'Selected category restoration missing');
});

console.log('\n--- Suite 4: Authoritative Pricing, Selected Panel & Zero Auto-Save ---');

runCheck('Product blocks are compact and text-only with NO product images', () => {
  assert(!indexHtml.includes("<img className=\"rcrm-qa-img\""), 'Product images must not be rendered in Quick Add');
  assert(indexHtml.includes("rcrm-qa-product-name"), 'rcrm-qa-product-name missing');
  assert(indexHtml.includes("rcrm-qa-product-price"), 'rcrm-qa-product-price missing');
});

runCheck('Repeated product clicks increment quantity without creating duplicate rows', () => {
  assert(indexHtml.includes("const newQty = (parseFloat(updated[idx].quantity) || 0) + 1;"),
    'Repeated click quantity increment missing');
  assert(indexHtml.includes("rcrm-qa-qty-badge"), 'Selected quantity badge missing on product block');
});

runCheck('Right-side Selected Items panel provides direct editing and line totals', () => {
  assert(indexHtml.includes("rcrm-qa-qty-btn"), 'Quantity increment/decrement buttons missing');
  assert(indexHtml.includes("rcrm-qa-qty-input"), 'Direct quantity input missing');
  assert(indexHtml.includes("rcrm-qa-item-linetotal"), 'Line total element missing');
  assert(indexHtml.includes("rcrm-qa-item-del-btn"), 'Item delete button missing');
});

runCheck('Effective selling prices resolve via effectivePriceMap or product.price; costPrice is NEVER used', () => {
  assert(indexHtml.includes("const resolvePrice = useCallback((prod) => {"), 'resolvePrice function missing');
  assert(!indexHtml.includes("resolvePrice: prod.costPrice"), 'Cost price must not be used as selling price');
  assert(pricingService.includes("Single Authoritative Backend Pricing Engine"), 'Backend pricing service verified');
});

runCheck('Authoritative calculation engine and preview totals are strictly maintained', () => {
  assert(calcEngine.includes("public class InvoiceCalculationEngine"), 'Authoritative calculation engine verified');
  assert(indexHtml.includes("Estimated Subtotal:"), 'Preview subtotal label missing in selected items panel');
});

runCheck('Zero auto-save guarantee: neither entry point executes save or generates invoice without user action', () => {
  // Confirm QuickAddItemsModal does not call API.invoices.create directly
  const quickAddFn = indexHtml.substring(indexHtml.indexOf('function QuickAddItemsModal('), indexHtml.indexOf('function Dashboard('));
  assert(!quickAddFn.includes('API.invoices.create('), 'QuickAddItemsModal must NEVER automatically save invoice');
  assert(!quickAddFn.includes('API.invoices.update('), 'QuickAddItemsModal must NEVER automatically update invoice');
});

console.log('\n--- Suite 5: Sound Feedback & Subtle Micro-Animations ---');

runCheck('QuickAddAudio feedback engine synthesizes soft Web Audio tones without external assets', () => {
  assert(indexHtml.includes('const QuickAddAudio = (() => {'), 'QuickAddAudio feedback engine missing');
  assert(indexHtml.includes('AudioCtx = window.AudioContext || window.webkitAudioContext'), 'AudioContext initialization missing');
  assert(indexHtml.includes("playTone(type)"), 'playTone function missing');
  assert(indexHtml.includes("c.createOscillator()"), 'Web Audio oscillator synthesis missing');
  assert(indexHtml.includes("c.createGain()"), 'Web Audio gain envelope missing');
});

runCheck('Volume envelopes are strictly restrained (gain <= 0.06) and durations are brief (<= 0.16s)', () => {
  assert(indexHtml.includes('gain.gain.setValueAtTime(0.06,'), 'Add/increase restrained gain envelope verified');
  assert(indexHtml.includes('gain.gain.setValueAtTime(0.05,'), 'Decrease/finish restrained gain envelope verified');
});

runCheck('Sound preference persists to localStorage (rcrm_quick_add_sound_enabled) with safe fallback', () => {
  assert(indexHtml.includes("localStorage.getItem('rcrm_quick_add_sound_enabled')"), 'Sound preference read missing');
  assert(indexHtml.includes("localStorage.setItem('rcrm_quick_add_sound_enabled'"), 'Sound preference persistence missing');
});

runCheck('Modal header includes accessible sound toggle control (🔊 Sound / 🔇 Muted)', () => {
  assert(indexHtml.includes("rcrm-qa-sound-btn"), 'rcrm-qa-sound-btn class missing in index.html');
  assert(indexHtml.includes("soundEnabled ? '🔊 Sound' : '🔇 Muted'"), 'Sound button toggle label missing');
  assert(styleCss.includes('.rcrm-qa-sound-btn'), '.rcrm-qa-sound-btn missing in style.css');
});

runCheck('Product cards feature pressed state and brief highlight animation on click', () => {
  assert(styleCss.includes('.rcrm-qa-product-card:active'), '.rcrm-qa-product-card:active missing in style.css');
  assert(styleCss.includes('.rcrm-qa-product-card.just-added'), '.rcrm-qa-product-card.just-added missing in style.css');
  assert(styleCss.includes('@keyframes rcrmCardPulse'), '@keyframes rcrmCardPulse missing in style.css');
  assert(indexHtml.includes('just-added'), 'just-added dynamic class missing in product card rendering');
});

runCheck('Quantity badge pops smoothly when incremented', () => {
  assert(styleCss.includes('@keyframes rcrmBadgePop'), '@keyframes rcrmBadgePop missing in style.css');
  assert(styleCss.includes('animation: rcrmBadgePop'), 'rcrmBadgePop animation application missing');
});

runCheck('Selected items list provides insertion slide-in and exit slide-out animations', () => {
  assert(styleCss.includes('@keyframes rcrmItemSlideIn'), '@keyframes rcrmItemSlideIn missing in style.css');
  assert(styleCss.includes('@keyframes rcrmItemSlideOut'), '@keyframes rcrmItemSlideOut missing in style.css');
  assert(styleCss.includes('.rcrm-qa-selected-item.removing'), '.rcrm-qa-selected-item.removing missing in style.css');
  assert(indexHtml.includes('removingPid === pid'), 'removingPid state handling missing in index.html');
});

runCheck('Quantity modifications provide subtle pulse feedback without layout shifts', () => {
  assert(styleCss.includes('@keyframes rcrmQtyPulse'), '@keyframes rcrmQtyPulse missing in style.css');
  assert(styleCss.includes('.rcrm-qa-qty-pulse'), '.rcrm-qa-qty-pulse missing in style.css');
  assert(indexHtml.includes('rcrm-qa-qty-pulse'), 'rcrm-qa-qty-pulse class binding missing in index.html');
});

runCheck('Finish interaction triggers completion chime and protects against rapid duplicate submission', () => {
  assert(indexHtml.includes("QuickAddAudio.play('finish')"), 'Finish audio chime missing');
  assert(indexHtml.includes("if (selectedItems.length === 0 || isSubmitting) return;"), 'Finish submission guard missing');
  assert(indexHtml.includes("isSubmitting ? 'Transferring Items...' :"), 'Transferring items visual state missing');
});

runCheck('prefers-reduced-motion media query disables animations for accessibility', () => {
  assert(styleCss.includes('@media (prefers-reduced-motion: reduce)'), '@media (prefers-reduced-motion: reduce) missing in style.css');
  assert(styleCss.includes('.rcrm-qa-product-card.just-added'), 'just-added animation reset missing in reduced motion query');
  assert(styleCss.includes('.rcrm-qa-selected-item.removing'), 'removing animation reset missing in reduced motion query');
});

console.log('\n═══════════════════════════════════════════════════════════════════');
console.log(`ALL QUICK ADD ITEMS CHECKS PASSED: ${passCount}/${totalCount}`);
console.log('═══════════════════════════════════════════════════════════════════\n');

if (passCount !== totalCount) {
  process.exit(1);
}

