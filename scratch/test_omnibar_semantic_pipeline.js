/**
 * test_omnibar_semantic_pipeline.js
 * Comprehensive Verification & Benchmarking Suite for RupeeCRM Omnibar Reasoning Pipeline
 */

const assert = require('assert');
const { processQuery, Normalizer, IntentClassifier, RoleResolver, ContextManager, WordsEngine, CalculationEvaluator, SafetyGate } = require('../billsoft/src/main/webapp/js/omnibarPipeline.js');

let passedTests = 0;
let totalTests = 0;

function runTest(name, fn) {
  totalTests++;
  try {
    fn();
    passedTests++;
    console.log(`  ✓ ${name}`);
  } catch (err) {
    console.error(`  ✗ ${name}`);
    console.error(`    ${err.message}`);
  }
}

console.log('===============================================================');
console.log(' RUNNING OMNIBAR SEMANTIC REASONING PIPELINE VERIFICATION SUITE');
console.log('===============================================================\n');

// ─── 1. NORMALIZATION & GLUED TOKEN DECOUPLING ───
console.log('1. Normalization & Glued Tokens:');
runTest('Decouples length glued tokens: 30mm -> 30 mm', () => {
  const norm = Normalizer.normalize('30mm to mtr');
  assert.strictEqual(norm.decoupled, '30 mm to mtr');
});

runTest('Decouples UPI glued tokens: 700upi -> 700 upi', () => {
  const norm = Normalizer.normalize('700upi');
  assert.strictEqual(norm.decoupled, '700 upi');
});

runTest('Decouples GST glued tokens: 18%gst -> 18% gst', () => {
  const norm = Normalizer.normalize('18%gst on 5000');
  assert.strictEqual(norm.decoupled, '18% gst on 5000');
});

runTest('Decouples currency symbols: rs500 -> rs 500', () => {
  const norm = Normalizer.normalize('rs500 discount');
  assert.strictEqual(norm.decoupled, 'rs 500 discount');
});

runTest('Cleans conversational fillers in multilingual queries', () => {
  const norm = Normalizer.normalize('please help me find bill banao kripya');
  assert.ok(!norm.cleaned.includes('please'));
  assert.ok(!norm.cleaned.includes('kripya'));
});

// ─── 2. COMMERCIAL & TAX CALCULATORS ───
console.log('\n2. Commercial & Tax Calculators:');
runTest('Forward GST: gst 18% on 5000', () => {
  const res = processQuery('gst 18% on 5000');
  assert.strictEqual(res.status, 'ANSWER');
  assert.strictEqual(res.intent, 'GST_CALCULATION');
  assert.strictEqual(res.data.base, 5000);
  assert.strictEqual(res.data.cgst, 450);
  assert.strictEqual(res.data.sgst, 450);
  assert.strictEqual(res.data.total, 5900);
});

runTest('Reverse GST Inclusive: gst 18% inclusive 5900', () => {
  const res = processQuery('gst 18% inclusive 5900');
  assert.strictEqual(res.status, 'ANSWER');
  assert.strictEqual(res.data.isInclusive, true);
  assert.strictEqual(res.data.base, 5000);
  assert.strictEqual(res.data.tax, 900);
  assert.strictEqual(res.data.total, 5900);
});

runTest('Cashier Change & Denominations: change for 2000 bill 1435', () => {
  const res = processQuery('change for 2000 bill 1435');
  assert.strictEqual(res.status, 'ANSWER');
  assert.strictEqual(res.intent, 'CASHIER_CHANGE');
  assert.strictEqual(res.data.change, 565);
  assert.strictEqual(res.data.denominations['₹500'], 1);
  assert.strictEqual(res.data.denominations['₹50'], 1);
  assert.strictEqual(res.data.denominations['₹10'], 1);
  assert.strictEqual(res.data.denominations['₹5'], 1);
});

runTest('Equal Bill Split: split 4500 by 4', () => {
  const res = processQuery('split 4500 by 4');
  assert.strictEqual(res.status, 'ANSWER');
  assert.strictEqual(res.data.perPerson, 1125);
  assert.strictEqual(res.data.persons, 4);
});

runTest('Ratio Bill Split: split 10000 in 2:3:5', () => {
  const res = processQuery('split 10000 in 2:3:5');
  assert.strictEqual(res.status, 'ANSWER');
  assert.deepStrictEqual(res.data.shares, [2000, 3000, 5000]);
});

runTest('Loan EMI Calculation: emi 500000 at 9.5% for 5 years', () => {
  const res = processQuery('emi 500000 at 9.5% for 5 years');
  assert.strictEqual(res.status, 'ANSWER');
  assert.strictEqual(res.intent, 'LOAN_EMI_CALCULATION');
  assert.ok(res.data.monthlyEMI > 10000 && res.data.monthlyEMI < 11000);
});

runTest('Simple Interest Calculation: si 50000 at 8% for 3 years', () => {
  const res = processQuery('si 50000 at 8% for 3 years');
  assert.strictEqual(res.status, 'ANSWER');
  assert.strictEqual(res.data.interest, 12000);
  assert.strictEqual(res.data.maturity, 62000);
});

// ─── 3. UNITS & NUMBER TO WORDS ───
console.log('\n3. Units, Currency & Words:');
runTest('Length Conversion: 30 mm to mtr', () => {
  const res = processQuery('30 mm to mtr');
  assert.strictEqual(res.status, 'ANSWER');
  assert.strictEqual(res.data.result, 0.03);
});

runTest('Area Indian Trade Units: 5 brass in sqft', () => {
  const res = processQuery('5 brass to sqft');
  assert.strictEqual(res.status, 'ANSWER');
  assert.ok(res.data.result > 400 && res.data.result < 600);
});

runTest('Number to Cheque Words: words 125000', () => {
  const res = processQuery('words 125000');
  assert.strictEqual(res.status, 'ANSWER');
  assert.strictEqual(res.data.words, 'One Lakh Twenty Five Thousand Rupees Only');
});

runTest('Words to Digits: one lakh twenty five thousand', () => {
  const res = processQuery('one lakh twenty five thousand');
  assert.strictEqual(res.status, 'ANSWER');
  assert.strictEqual(res.data.value, 125000);
});

runTest('Vernacular Words: dedh hazar', () => {
  const res = processQuery('dedh hazar');
  assert.strictEqual(res.status, 'ANSWER');
  assert.strictEqual(res.data.value, 1500);
});

// ─── 4. SEMANTIC REASONING, ROLES & CONSTRAINTS ───
console.log('\n4. Semantic Reasoning & Role Extraction:');
runTest('Extracts expense amount and title: kharcha 120 chai nashta', () => {
  const res = processQuery('kharcha 120 chai nashta');
  assert.strictEqual(res.status, 'ACTION_PREVIEW');
  assert.strictEqual(res.intent, 'RECORD_EXPENSE');
  assert.strictEqual(res.entities.amount, 120);
  assert.strictEqual(res.entities.title, 'chai nashta');
});

runTest('Customer registration extraction: customer Manoj Patil 9822113344 Kolhapur', () => {
  const res = processQuery('customer Manoj Patil 9822113344 Kolhapur');
  assert.strictEqual(res.status, 'ACTION_PREVIEW');
  assert.strictEqual(res.intent, 'CREATE_CUSTOMER');
  assert.strictEqual(res.entities.targetName, 'Manoj Patil');
  assert.strictEqual(res.entities.phone, '9822113344');
  assert.strictEqual(res.entities.city, 'Kolhapur');
});

runTest('Attendance marking extraction: attendance Ganesh present', () => {
  const res = processQuery('attendance Ganesh present');
  assert.strictEqual(res.status, 'ACTION_PREVIEW');
  assert.strictEqual(res.entities.targetName, 'Ganesh');
  assert.strictEqual(res.entities.attendanceStatus, 'PRESENT');
});

runTest('Salary advance extraction: advance 5000 to ganesh', () => {
  const res = processQuery('advance 5000 to ganesh');
  assert.strictEqual(res.status, 'ACTION_PREVIEW');
  assert.strictEqual(res.entities.amount, 5000);
  assert.strictEqual(res.entities.targetName, 'ganesh');
});

// ─── 5. SAFETY MODEL & DECISION MATRIX ───
console.log('\n5. Safety Gate & Decision Matrix:');
runTest('READ with decisive confidence -> ANSWER', () => {
  const res = processQuery('gst 18% on 1000');
  assert.strictEqual(res.status, 'ANSWER');
  assert.strictEqual(res.requiresConfirmation, false);
});

runTest('READ with missing required info -> CLARIFICATION_REQUIRED', () => {
  const res = processQuery('discount');
  assert.strictEqual(res.status, 'CLARIFICATION_REQUIRED');
  assert.ok(res.missingFields.includes('amount') || res.missingFields.includes('rate'));
});

runTest('WRITE with valid info & sufficient confidence -> ACTION_PREVIEW', () => {
  const res = processQuery('kharcha 250 stationary');
  assert.strictEqual(res.status, 'ACTION_PREVIEW');
  assert.strictEqual(res.requiresConfirmation, true);
  assert.ok(res.plan && res.plan.steps.length > 0);
});

runTest('WRITE with missing required info -> CLARIFICATION_REQUIRED', () => {
  const res = processQuery('customer Manoj');
  assert.strictEqual(res.status, 'CLARIFICATION_REQUIRED');
  assert.ok(res.missingFields.includes('phone'));
});

runTest('HIGH-risk / Destructive operation -> CONFIRMATION_REQUIRED regardless of confidence', () => {
  const res = processQuery('/lock');
  assert.strictEqual(res.status, 'CONFIRMATION_REQUIRED');
  assert.strictEqual(res.riskLevel, 'HIGH');
  assert.strictEqual(res.requiresConfirmation, true);
});

runTest('AMBIGUOUS when multiple viable interpretations compete closely', () => {
  // Directly verify SafetyGate AMBIGUOUS branch with competing candidates
  const decision = SafetyGate.decide({
    classification: {
      topCandidate: { intent: 'RECORD_EXPENSE', domain: 'TASKS', confidence: 0.85, isDominant: false },
      allCandidates: [
        { intent: 'RECORD_EXPENSE', domain: 'TASKS', confidence: 0.85, isDominant: false, evidence: 'Expense match' },
        { intent: 'RECORD_ADVANCE', domain: 'TASKS', confidence: 0.82, isDominant: false, evidence: 'Advance match' }
      ]
    },
    entities: { amount: 500 },
    constraints: { isValid: true },
    plan: { steps: [] },
    rawQuery: '500 ganesh',
    normalized: { raw: '500 ganesh' },
    ctx: {}
  });
  assert.strictEqual(decision.status, 'AMBIGUOUS');
  assert.ok(decision.candidates.length >= 2);
});

runTest('Handles empty queries gracefully -> NO_MATCH', () => {
  const res = processQuery('');
  assert.strictEqual(res.status, 'NO_MATCH');
});

runTest('No viable capability -> SEARCH_RESULTS / NO_MATCH fallback', () => {
  const res = processQuery('xyzabc123nonexistentquery999');
  assert.strictEqual(res.status, 'SEARCH_RESULTS');
});

// ─── 6. CONTEXT MEMORY & REFERENCE RESOLUTION ───
console.log('\n6. Context Memory & Reference Resolution:');
runTest('Resolves context reference: "remind him" using lastCustomer', () => {
  ContextManager.reset();
  ContextManager.setContext({
    lastCustomer: { id: 101, name: 'Sharma Ji', phone: '9822001122', balance: 4500 }
  });
  const res = processQuery('remind him', { lastCustomer: { id: 101, name: 'Sharma Ji' } });
  assert.strictEqual(res.intent, 'SEND_DUES_REMINDER');
  assert.ok(res.entities.reference && res.entities.reference.isRelative);
});

runTest('Preserves context across sequential queries', () => {
  ContextManager.reset();
  processQuery('kharcha 500 office stationary');
  const ctx = ContextManager.getContext();
  assert.strictEqual(ctx.lastIntent, 'RECORD_EXPENSE');
  assert.ok(ctx.history.length > 0);
});

// ─── 7. MULTI-STEP PLANNING & ATOMICITY ───
console.log('\n7. Multi-Step Planning & Intermediate Representation (IR):');
runTest('Generates atomic plan with rollback action for expenses', () => {
  const res = processQuery('kharcha 1500 office supplies');
  assert.ok(res.plan);
  assert.strictEqual(res.plan.atomic, true);
  assert.strictEqual(res.plan.canRollback, true);
  assert.strictEqual(res.plan.steps[0].action, 'API.expenses.create');
  assert.strictEqual(res.plan.steps[0].rollbackAction, 'API.expenses.delete');
});

console.log(`\nVerification Test Results: ${passedTests} / ${totalTests} Passing (${((passedTests / totalTests) * 100).toFixed(1)}%)\n`);

if (passedTests !== totalTests) {
  console.error('Some tests failed!');
  process.exit(1);
}

// ─── 8. PERFORMANCE BENCHMARKING (Average, Median, P95, P99, Max) ───
console.log('===============================================================');
console.log(' EXECUTING REPRODUCIBLE PERFORMANCE BENCHMARK (1,000 ITERATIONS)');
console.log('===============================================================\n');

const benchmarkQueries = [
  'gst 18% on 5000',
  'gst 18% inclusive 5900',
  'change for 2000 bill 1435',
  'split 4500 by 4',
  'split 10000 in 2:3:5',
  'emi 500000 at 9.5% for 5 years',
  'si 50000 at 8% for 3 years',
  '30 mm to mtr',
  '5 brass to sqft',
  'words 125000',
  'one lakh twenty five thousand',
  'dedh hazar',
  'kharcha 120 chai nashta',
  'todo call Sharma ji 4pm',
  'customer Manoj Patil 9822113344 Kolhapur',
  'attendance Ganesh present',
  'advance 5000 to ganesh',
  '9000 upi',
  '/inv',
  '/lock'
];

const latencies = [];
const iterations = 1000;

for (let i = 0; i < iterations; i++) {
  const q = benchmarkQueries[i % benchmarkQueries.length];
  const t0 = process.hrtime ? process.hrtime() : null;
  const tStart = Date.now();
  
  processQuery(q);
  
  let elapsedMs;
  if (process.hrtime && t0) {
    const diff = process.hrtime(t0);
    elapsedMs = (diff[0] * 1000) + (diff[1] / 1e6);
  } else {
    elapsedMs = Date.now() - tStart;
  }
  latencies.push(elapsedMs);
}

latencies.sort((a, b) => a - b);
const sum = latencies.reduce((a, b) => a + b, 0);
const avg = sum / latencies.length;
const median = latencies[Math.floor(latencies.length * 0.50)];
const p95 = latencies[Math.floor(latencies.length * 0.95)];
const p99 = latencies[Math.floor(latencies.length * 0.99)];
const max = latencies[latencies.length - 1];
const min = latencies[0];

console.log('Latency Benchmark Results:');
console.log(`  - Total Iterations : ${iterations.toLocaleString()}`);
console.log(`  - Minimum Latency  : ${min.toFixed(3)} ms`);
console.log(`  - Average Latency  : ${avg.toFixed(3)} ms`);
console.log(`  - Median (P50)     : ${median.toFixed(3)} ms`);
console.log(`  - P95 Latency      : ${p95.toFixed(3)} ms`);
console.log(`  - P99 Latency      : ${p99.toFixed(3)} ms`);
console.log(`  - Maximum Latency  : ${max.toFixed(3)} ms`);
console.log('\n===============================================================');
