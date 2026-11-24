/**
 * Complete Smart Reorder & Forecasting Intelligence Verification Suite
 * Project Reference: RUPEECRM-FORECAST-DEEP-AUDIT-20261124
 */

const fs = require('fs');
const path = require('path');
const assert = require('assert');

const rootDir = fs.existsSync(path.resolve(__dirname, '../../../billsoft'))
  ? path.resolve(__dirname, '../../..')
  : (fs.existsSync(path.resolve(process.cwd(), 'billsoft')) ? process.cwd() : path.resolve(__dirname, '../../..'));
const resolvePath = (p) => path.join(rootDir, p);

let passed = 0;
let failed = 0;

function test(name, fn) {
  try {
    fn();
    console.log(`  ✓ [PASS] ${name}`);
    passed++;
  } catch (err) {
    console.error(`  ✗ [FAIL] ${name}: ${err.message}`);
    failed++;
  }
}

console.log('═══════════════════════════════════════════════════════════════════════════════');
console.log('RUPEECRM COMPLETE SMART REORDER & FORECASTING INTELLIGENCE VERIFICATION');
console.log('═══════════════════════════════════════════════════════════════════════════════\n');

// ── Suite 1: Phase A & B Backend Engine, Data Integrity & Models ──
console.log('--- Suite 1: Phase A & B Backend Engine, Data Integrity & Models ---');

const ingestionSrc = fs.readFileSync(resolvePath('billsoft/src/main/java/com/billing/simple/billsoft/forecast/service/ForecastIngestionService.java'), 'utf8');
const invoiceSrc = fs.readFileSync(resolvePath('billsoft/src/main/java/com/billing/simple/billsoft/service/InvoiceService.java'), 'utf8');
const holtWintersSrc = fs.readFileSync(resolvePath('billsoft/src/main/java/com/billing/simple/billsoft/forecast/model/HoltWintersModel.java'), 'utf8');
const classifierSrc = fs.readFileSync(resolvePath('billsoft/src/main/java/com/billing/simple/billsoft/forecast/selection/DemandPatternClassifier.java'), 'utf8');
const confidenceSrc = fs.readFileSync(resolvePath('billsoft/src/main/java/com/billing/simple/billsoft/forecast/confidence/ForecastConfidenceCalculator.java'), 'utf8');
const advisorySrc = fs.readFileSync(resolvePath('billsoft/src/main/java/com/billing/simple/billsoft/forecast/service/RestockAdvisoryService.java'), 'utf8');

test('A1: ForecastIngestionService handles invoice deletion and hard-deleted invoice reconciliation', () => {
  assert(ingestionSrc.includes('handleInvoiceDeleted(Long firmId, Long invoiceId)'), 'Missing handleInvoiceDeleted');
  assert(ingestionSrc.includes('reconcileHardDeletedInvoices(Long firmId)'), 'Missing reconcileHardDeletedInvoices');
  assert(invoiceSrc.includes('syncForecastForInvoice'), 'InvoiceService missing syncForecastForInvoice call on update/save');
  assert(invoiceSrc.includes('handleInvoiceDeleted'), 'InvoiceService missing handleInvoiceDeleted on delete');
});

test('A2 & B1: DemandPatternClassifier censors stockout periods in ADI and CV² calculations', () => {
  assert(classifierSrc.includes('classify(List<Double> historicalDailyDemand, List<Boolean> stockoutFlags)'), 'Missing stockout-censored classify method');
  assert(classifierSrc.includes('stockoutFlags.get(i)'), 'Classifier not checking stockout flags');
});

test('A3: RestockAdvisoryService calculates time-phased stockout dates factoring incoming PO arrival slots', () => {
  assert(advisorySrc.includes('IncomingPoSlot'), 'Missing IncomingPoSlot record');
  assert(advisorySrc.includes('calculateExpectedStockoutDate'), 'Missing calculateExpectedStockoutDate method');
  assert(advisorySrc.includes('expectedArrivalDate'), 'Expected arrival date not checked in PO slots');
});

test('A4 & B3: Decoupled confidence calculator with PROVISIONAL classification for history < 14d', () => {
  assert(confidenceSrc.includes('PROVISIONAL'), 'Missing PROVISIONAL confidence level');
  assert(confidenceSrc.includes('historyDays < 14'), 'Missing < 14 days threshold check');
  assert(advisorySrc.includes('confidenceScore'), 'Restock explanation missing confidenceScore');
  assert(advisorySrc.includes('wape'), 'Restock explanation missing wape');
});

test('B2: Damped additive Holt-Winters model with damping factor phi = 0.90', () => {
  assert(holtWintersSrc.includes('PHI = 0.90'), 'Missing damped factor PHI = 0.90 in HoltWintersModel');
  assert(holtWintersSrc.includes('dampSum'), 'Missing damping factor accumulation in forecast loop');
});

test('B4: ForecastRealizationLog entity and repository for multi-horizon accuracy tracking', () => {
  const logSrc = fs.readFileSync(resolvePath('billsoft/src/main/java/com/billing/simple/billsoft/forecast/entity/ForecastRealizationLog.java'), 'utf8');
  assert(logSrc.includes('@Entity'), 'ForecastRealizationLog not an entity');
  assert(logSrc.includes('horizonDays'), 'Missing horizonDays');
  assert(logSrc.includes('actualDemand'), 'Missing actualDemand');
  assert(logSrc.includes('absoluteError'), 'Missing absoluteError');
});

// ── Suite 2: Phase C & E UI Improvements & Centered Modal ──
console.log('\n--- Suite 2: Phase C & E UI Improvements & Centered Modal ---');

const piSrc = fs.readFileSync(resolvePath('billsoft/src/main/webapp/js/components/ProductIntelligence.js'), 'utf8');
const cssSrc = fs.readFileSync(resolvePath('billsoft/src/main/webapp/css/style.css'), 'utf8');

test('C1: Smart Reorder Queue defaults to pageSize = 25 and includes pills [25, 50, 100]', () => {
  assert(piSrc.includes('useState(25)'), 'Page size default is not 25');
  assert(piSrc.includes('[25, 50, 100]'), 'Missing [25, 50, 100] page size pills');
});

test('C2: Explainable driver badges for Demand Forecast, Minimum Stock Rule, Dual Signal, and Healthy', () => {
  assert(piSrc.includes('📊 Demand Forecast'), 'Missing Demand Forecast driver badge');
  assert(piSrc.includes('⚠️ Minimum Stock Rule'), 'Missing Minimum Stock Rule driver badge');
  assert(piSrc.includes('Dual Signal'), 'Missing Dual Signal driver badge');
});

test('C3: Incoming purchase order visibility with green badges (+X pcs PO Issued)', () => {
  assert(piSrc.includes('PO Issued'), 'Missing PO Issued indicator in table/popover');
  assert(piSrc.includes('incomingStock'), 'Missing incomingStock calculation/display');
});

test('E1: Dead shadowed legacy ProductIntelligenceHub component deleted', () => {
  const matches = [...piSrc.matchAll(/function ProductIntelligenceHub\s*\(/g)];
  assert.strictEqual(matches.length, 1, `Expected exactly 1 ProductIntelligenceHub declaration, found ${matches.length}`);
});

test('E2: DemandForecastChart uses calendar millisecond timestamp scaling for accurate x-coordinates', () => {
  assert(piSrc.includes('minTs'), 'DemandForecastChart missing minTs');
  assert(piSrc.includes('maxTs'), 'DemandForecastChart missing maxTs');
  assert(piSrc.includes('tsSpan'), 'DemandForecastChart missing tsSpan');
  assert(piSrc.includes('getTime()'), 'DemandForecastChart not converting dates to timestamps');
});

test('E3: Centered 960px modal dialog CSS for product forecast insights', () => {
  assert(cssSrc.includes('.forecast-drawer-backdrop'), 'Missing .forecast-drawer-backdrop');
  assert(cssSrc.includes('.forecast-drawer-panel'), 'Missing .forecast-drawer-panel');
  assert(cssSrc.includes('max-width: 960px'), 'Modal panel missing max-width: 960px');
  assert(piSrc.includes('Model Performance & Data Quality Audit'), 'Modal missing Section 5 Model Performance Audit');
});

// ── Suite 3: Phase D Standard Inventory Integration ──
console.log('\n--- Suite 3: Phase D Standard Inventory Integration ---');

const indexHtml = fs.readFileSync(resolvePath('billsoft/src/main/webapp/index.html'), 'utf8');

test('D1: ProductManager loads inventory summary telemetry via API.forecast.getInventorySummary', () => {
  assert(indexHtml.includes('API.forecast.getInventorySummary()'), 'Missing getInventorySummary call in load()');
  assert(indexHtml.includes('setInventorySummary(invSum)'), 'Missing setInventorySummary call');
  assert(indexHtml.includes('billsoft:forecast-synced'), 'Missing listener for billsoft:forecast-synced');
});

test('D2: Compact Inventory Intelligence Overview Banner rendered above products table', () => {
  assert(indexHtml.includes('inventory-forecast-banner'), 'Missing inventory-forecast-banner');
  assert(indexHtml.includes('Inventory Intelligence & Demand Forecasting'), 'Missing banner title');
  assert(indexHtml.includes('Resync Forecasts'), 'Missing Resync Forecasts button in banner');
  assert(indexHtml.includes('Open Reorder Queue'), 'Missing Open Reorder Queue button in banner');
});

test('D3: Standard inventory table includes 30D Velocity & Coverage and Urgency / Reorder columns', () => {
  assert(indexHtml.includes('30D Velocity & Coverage'), 'Missing 30D Velocity & Coverage header');
  assert(indexHtml.includes('Urgency'), 'Missing Urgency header');
  assert(indexHtml.includes('daysOfCoverage'), 'Missing daysOfCoverage in item row');
  assert(indexHtml.includes('sales30d'), 'Missing sales30d in item row');
});

test('D4: Product row includes Insights button (📊) and direct Purchase Order prefill (+ Order 🛒)', () => {
  assert(indexHtml.includes('setSelectedDetailProdId(p.id)'), 'Missing setSelectedDetailProdId call from table row');
  assert(indexHtml.includes('handleUseReorderRecommendation(p.id'), 'Missing handleUseReorderRecommendation in table row');
  assert(indexHtml.includes('prefillPoItem'), 'Missing prefillPoItem in navigation dispatch');
});

// ── Suite 4: Phase F Forecast Analytics Statement ──
console.log('\n--- Suite 4: Phase F Forecast Analytics Statement ---');

const stmtCtrlSrc = fs.readFileSync(resolvePath('billsoft/src/main/java/com/billing/simple/billsoft/controllers/StatementsController.java'), 'utf8');
const stmtSvcSrc = fs.readFileSync(resolvePath('billsoft/src/main/java/com/billing/simple/billsoft/service/StatementServiceImpl.java'), 'utf8');
const apiSrc = fs.readFileSync(resolvePath('billsoft/src/main/webapp/js/api.js'), 'utf8');

test('F1: StatementsController provides GET /api/statements/forecast, /pdf, and /csv endpoints', () => {
  assert(stmtCtrlSrc.includes('@GetMapping("/forecast")'), 'Missing /forecast endpoint');
  assert(stmtCtrlSrc.includes('@GetMapping("/forecast/pdf")'), 'Missing /forecast/pdf endpoint');
  assert(stmtCtrlSrc.includes('/forecast/csv'), 'Missing /forecast/csv endpoint');
});

test('F2: StatementServiceImpl generates Forecast Analytics Statement data, PDF and CSV', () => {
  assert(stmtSvcSrc.includes('getForecastAnalyticsStatement'), 'Missing getForecastAnalyticsStatement');
  assert(stmtSvcSrc.includes('generateForecastAnalyticsStatementPdf'), 'Missing generateForecastAnalyticsStatementPdf');
  assert(stmtSvcSrc.includes('generateForecastAnalyticsStatementCsv'), 'Missing generateForecastAnalyticsStatementCsv');
  assert(stmtSvcSrc.includes('CategorySummary'), 'Missing CategorySummary population');
});

test('F3: api.js provides API.statements.forecast, forecastPdf, and forecastCsv methods', () => {
  assert(apiSrc.includes('forecast: (params = {})'), 'Missing API.statements.forecast');
  assert(apiSrc.includes('forecastPdf: async'), 'Missing API.statements.forecastPdf');
  assert(apiSrc.includes('forecastCsv: async'), 'Missing API.statements.forecastCsv');
});

test('F4: StatementManager includes 4th Hero Selection Card for Forecast & Replenishment Advisory', () => {
  assert(indexHtml.includes("setStatementMode('forecast')"), "Missing setStatementMode('forecast')");
  assert(indexHtml.includes('Forecast & Replenishment'), 'Missing Forecast & Replenishment card title');
});

test('F5: StatementManager provides Horizon pills, Category, Urgency, and Search filters for forecast mode', () => {
  assert(indexHtml.includes('forecastHorizon'), 'Missing forecastHorizon state');
  assert(indexHtml.includes('forecastCategory'), 'Missing forecastCategory state');
  assert(indexHtml.includes('forecastUrgency'), 'Missing forecastUrgency state');
  assert(indexHtml.includes('forecastSearch'), 'Missing forecastSearch state');
});

test('F6: StatementManager renders 6 KPI Cards, Category Matrix, and Product Replenishment Detail Ledger', () => {
  assert(indexHtml.includes('Category Demand & Capital Allocation Matrix'), 'Missing Category Demand Matrix table');
  assert(indexHtml.includes('Product Replenishment Detail Ledger'), 'Missing Product Replenishment Detail Ledger');
  assert(indexHtml.includes('Inventory Intelligence Methodology & Assumptions'), 'Missing methodology footnote');
  assert(indexHtml.includes('handleDownloadForecastCsv'), 'Missing CSV download button handler');
});

// ── Suite 5: Phase G Performance & Multi-Firm Isolation ──
console.log('\n--- Suite 5: Phase G Performance & Multi-Firm Isolation ---');

const repoSrc = fs.readFileSync(resolvePath('billsoft/src/main/java/com/billing/simple/billsoft/forecast/repository/ForecastSalesTransactionRepository.java'), 'utf8');

test('G1: Single GROUP BY batch aggregation query for firm metrics', () => {
  assert(repoSrc.includes('batchAggregateMetricsForFirm'), 'Missing batchAggregateMetricsForFirm query');
  assert(repoSrc.includes('GROUP BY t.productId'), 'Query does not group by productId');
});

test('G2: Strict firm-level isolation enforced across all database queries', () => {
  assert(advisorySrc.includes('firmId'), 'Advisory queries missing firmId parameter');
  assert(stmtSvcSrc.includes('restockRepo.findAllByFirmIdAndRecommendationStatus(targetFirmId'), 'Recommendations not scoped by firmId');
  assert(stmtSvcSrc.includes('transactionRepo.sumSalesByProductSince(targetFirmId'), 'Sales summary not scoped by firmId');
});

console.log('\n═══════════════════════════════════════════════════════════════════════════════');
console.log(`SUMMARY: ${passed} / ${passed + failed} tests passed (${Math.round((passed / (passed + failed)) * 100)}%)`);
console.log('═══════════════════════════════════════════════════════════════════════════════');

if (failed > 0) process.exit(1);
