/**
 * test_natural_language_assistant.js
 * Comprehensive Natural-Language Capability, Paraphrase, Implied Intent,
 * Discourse, Pronoun Resolution, Hinglish/Marathi, Negation, Correction,
 * Mutation Safety, and Latency Benchmark Suite.
 */

const assert = require('assert');
const {
  processQuery,
  Normalizer,
  SemanticParser,
  IntentClassifier,
  RoleResolver,
  ContextManager,
  CalculationEvaluator,
  ActionPlanner,
  SafetyGate,
  NLG
} = require('../billsoft/src/main/webapp/js/omnibarPipeline.js');

const { BillsoftSearchEngine } = require('../billsoft/src/main/webapp/js/utils.js');

let totalTests = 0;
let passedTests = 0;
const failures = [];

function testCase(category, query, runner) {
  totalTests++;
  try {
    runner();
    passedTests++;
    console.log(`  ✓ [${category}] ${query}`);
  } catch (err) {
    console.error(`  ✗ [${category}] ${query}`);
    console.error(`    Error: ${err.message}`);
    failures.push({ category, query, error: err.message });
  }
}

console.log('======================================================================');
console.log(' RUNNING NATURAL-LANGUAGE BUSINESS ASSISTANT COMPREHENSIVE TEST SUITE');
console.log('======================================================================\n');

// ─── 1. REQUIRED E2E DEMONSTRATIONS (A, B, C, D, E) ───
console.log('1. Required E2E Demos (A, B, C, D, E):');

testCase('DEMO_A', 'My bill is ₹5,000, GST is 18%, and the customer gives me ₹10,000. How much change should I give?', () => {
  const res = processQuery('My bill is ₹5,000, GST is 18%, and the customer gives me ₹10,000. How much change should I give?');
  assert.strictEqual(res.status, 'ANSWER');
  assert.strictEqual(res.intent, 'COMPOUND_GST_CHANGE');
  assert.strictEqual(res.data.base, 5000);
  assert.strictEqual(res.data.rate, 18);
  assert.strictEqual(res.data.total, 5900);
  assert.strictEqual(res.data.paid, 10000);
  assert.strictEqual(res.data.change, 4100);
  assert.ok(res.message.includes('5,900'));
  assert.ok(res.message.includes('4,100'));
});

testCase('DEMO_B', "Show Manoj's outstanding balance and tell me how much I need to collect from him.", () => {
  const mockCustomer = { id: 101, name: 'Manoj', balance: 2450 };
  const res = processQuery("Show Manoj's outstanding balance and tell me how much I need to collect from him.", {
    customers: [mockCustomer]
  });
  assert.strictEqual(res.status, 'ANSWER');
  assert.strictEqual(res.intent, 'VIEW_CUSTOMER_OUTSTANDING');
  assert.strictEqual(res.data.customer.name, 'Manoj');
  assert.strictEqual(res.data.balance, 2450);
  assert.ok(res.message.includes('2,450'));
});

testCase('DEMO_C', 'Conversational Pronoun Flow: Show Manoj -> How much does he owe? -> Remind him about it.', () => {
  ContextManager.reset();
  const mockCustomer = { id: 101, name: 'Manoj Patil', balance: 3200, phone: '9822113344' };
  
  // Step 1: "Show Manoj Patil"
  const step1 = processQuery('show Manoj Patil', { customers: [mockCustomer] });
  assert.ok(step1.status === 'ANSWER' || step1.status === 'SEARCH_RESULTS');
  
  // Step 2: "How much does he owe?" -> Resolves 'he' to Manoj Patil
  const step2 = processQuery('how much does he owe?', { customers: [mockCustomer] });
  assert.strictEqual(step2.status, 'ANSWER');
  assert.strictEqual(step2.intent, 'VIEW_CUSTOMER_OUTSTANDING');
  assert.strictEqual(step2.data.customer.name, 'Manoj Patil');
  assert.strictEqual(step2.data.balance, 3200);

  // Step 3: "Remind him about it." -> Resolves 'him' to Manoj Patil
  const step3 = processQuery('remind him about it', { customers: [mockCustomer] });
  assert.strictEqual(step3.intent, 'SEND_DUES_REMINDER');
  assert.strictEqual(step3.entities.targetName, 'Manoj Patil');
  assert.strictEqual(step3.requiresConfirmation, false); // WhatsApp preview
});

testCase('DEMO_D', "Tell me today's sales, then open invoices.", () => {
  const todayStr = new Date().toISOString().split('T')[0];
  const mockInvoices = [
    { id: 1, invoiceDate: todayStr, netTotal: 25000 },
    { id: 2, invoiceDate: todayStr, netTotal: 23750 }
  ];
  const res = processQuery("Tell me today's sales, then open invoices.", { invoices: mockInvoices });
  assert.strictEqual(res.status, 'ANSWER');
  assert.strictEqual(res.intent, 'BI_TODAY_SALES');
  assert.strictEqual(res.data.totalRevenue, 48750);
  assert.strictEqual(res.data.invoiceCount, 2);
  assert.ok(res.message.includes('48,750'));
});

testCase('DEMO_E', '5000 ka 18% GST laga ke agar customer 10000 de toh kitna wapas karna hai?', () => {
  const res = processQuery('5000 ka 18% GST laga ke agar customer 10000 de toh kitna wapas karna hai?');
  assert.strictEqual(res.status, 'ANSWER');
  assert.strictEqual(res.intent, 'COMPOUND_GST_CHANGE');
  assert.strictEqual(res.data.base, 5000);
  assert.strictEqual(res.data.rate, 18);
  assert.strictEqual(res.data.total, 5900);
  assert.strictEqual(res.data.paid, 10000);
  assert.strictEqual(res.data.change, 4100);
});

// ─── 2. NATURAL PARAPHRASES (GST, CHANGE, OUTSTANDING, SALES) ───
console.log('\n2. Natural Language Paraphrases:');

// GST Paraphrases
const gstParaphrases = [
  'what is 18% GST on 5000',
  'add 18 percent GST to 5000',
  '5000 plus 18% GST',
  '5000 ka 18 percent GST',
  '5000 pe 18 GST lagao',
  'calculate 18% GST on 5000'
];

gstParaphrases.forEach(q => {
  testCase('PARAPHRASE_GST', q, () => {
    const res = processQuery(q);
    assert.strictEqual(res.status, 'ANSWER');
    assert.strictEqual(res.intent, 'GST_CALCULATION');
    assert.strictEqual(res.data.base, 5000);
    assert.strictEqual(res.data.rate, 18);
    assert.strictEqual(res.data.total, 5900);
  });
});

// Change Paraphrases
const changeParaphrases = [
  'bill is 1435 customer gives 2000',
  'how much change from 2000 for a 1435 bill',
  'customer paid 2000, bill 1435, what do I return',
  '2000 diye bill 1435 ka hai kitna wapas',
  'The customer paid 2000 for a 1435 bill'
];

changeParaphrases.forEach(q => {
  testCase('PARAPHRASE_CHANGE', q, () => {
    const res = processQuery(q);
    assert.strictEqual(res.status, 'ANSWER');
    assert.strictEqual(res.intent, 'CASHIER_CHANGE');
    assert.strictEqual(res.data.change, 565);
    assert.strictEqual(res.data.paid, 2000);
    assert.strictEqual(res.data.bill, 1435);
  });
});

// Outstanding Paraphrases
const outstandingParaphrases = [
  'what does Manoj owe',
  "how much is Manoj's outstanding",
  'Manoj ka udhari kitna hai',
  'show me Manoj balance',
  'what is pending from Manoj',
  'how much do I need to collect from Manoj',
  'Manoj cha balance kiti aahe'
];

outstandingParaphrases.forEach(q => {
  testCase('PARAPHRASE_OUTSTANDING', q, () => {
    const mockCustomer = { id: 101, name: 'Manoj', balance: 3500 };
    const res = processQuery(q, { customers: [mockCustomer] });
    assert.strictEqual(res.status, 'ANSWER');
    assert.strictEqual(res.intent, 'VIEW_CUSTOMER_OUTSTANDING');
    assert.strictEqual(res.data.customer.name, 'Manoj');
    assert.strictEqual(res.data.balance, 3500);
  });
});

// Today Sales Paraphrases
const salesParaphrases = [
  "today's sales",
  'how much did we sell today',
  'how much have we sold today',
  "today's revenue?",
  'sales for today',
  'show me today sales',
  'can you tell me what we made today',
  'kitne ki sale hui aaj',
  'aaj ka sale kitna hai',
  'aajchi sale kiti aahe'
];

salesParaphrases.forEach(q => {
  testCase('PARAPHRASE_SALES', q, () => {
    const res = processQuery(q);
    assert.strictEqual(res.status, 'ANSWER');
    assert.strictEqual(res.intent, 'BI_TODAY_SALES');
    assert.ok(res.data.totalRevenue > 0);
  });
});

// ─── 3. COMPOUND QUERIES & IMPLIED OPERATIONS ───
console.log('\n3. Compound Queries & Implied Operations:');

testCase('COMPOUND', 'calculate 18% GST on 5000 and then tell me the change from 10000', () => {
  const res = processQuery('calculate 18% GST on 5000 and then tell me the change from 10000');
  assert.strictEqual(res.status, 'ANSWER');
  assert.strictEqual(res.intent, 'COMPOUND_GST_CHANGE');
  assert.strictEqual(res.data.total, 5900);
  assert.strictEqual(res.data.change, 4100);
});

testCase('COMPOUND', 'add 18% GST to 5000, then calculate the change if I give 10000', () => {
  const res = processQuery('add 18% GST to 5000, then calculate the change if I give 10000');
  assert.strictEqual(res.status, 'ANSWER');
  assert.strictEqual(res.intent, 'COMPOUND_GST_CHANGE');
  assert.strictEqual(res.data.total, 5900);
  assert.strictEqual(res.data.change, 4100);
});

testCase('IMPLIED_CHANGE', '10000 diye hain, bill 7350 ka hai, kitna wapas dena hai', () => {
  const res = processQuery('10000 diye hain, bill 7350 ka hai, kitna wapas dena hai');
  assert.strictEqual(res.status, 'ANSWER');
  assert.strictEqual(res.intent, 'CASHIER_CHANGE');
  assert.strictEqual(res.data.change, 2650);
});

testCase('IMPLIED_CHANGE', 'bill 5000 aahe, GST 18%, customer ne 10000 dile', () => {
  const res = processQuery('bill 5000 aahe, GST 18%, customer ne 10000 dile');
  assert.strictEqual(res.status, 'ANSWER');
  assert.strictEqual(res.intent, 'COMPOUND_GST_CHANGE');
  assert.strictEqual(res.data.total, 5900);
  assert.strictEqual(res.data.change, 4100);
});

// ─── 4. CONVERSATIONAL DISCOURSE, PRONOUNS & CORRECTIONS ───
console.log('\n4. Conversational Discourse, Pronouns & Corrections:');

testCase('CORRECTION_RATE', 'GST on 5000 followed by "No, I meant 12%."', () => {
  ContextManager.reset();
  const step1 = processQuery('gst 18% on 5000');
  assert.strictEqual(step1.data.total, 5900);
  
  const step2 = processQuery('No, I meant 12%');
  assert.strictEqual(step2.status, 'ANSWER');
  assert.strictEqual(step2.intent, 'GST_CALCULATION');
  assert.strictEqual(step2.data.base, 5000);
  assert.strictEqual(step2.data.rate, 12);
  assert.strictEqual(step2.data.total, 5600);
});

testCase('FOLLOWUP_SWITCH', 'Context Switch: Manoj -> Rahul -> and his phone number?', () => {
  ContextManager.reset();
  const manoj = { id: 101, name: 'Manoj', phone: '9822113344', balance: 1200 };
  const rahul = { id: 102, name: 'Rahul', phone: '9822998877', balance: 4500 };
  
  processQuery('show Manoj', { customers: [manoj, rahul] });
  const checkManoj = processQuery('what does he owe?', { customers: [manoj, rahul] });
  assert.strictEqual(checkManoj.data.customer.name, 'Manoj');

  // Switch subject to Rahul
  processQuery('what about Rahul?', { customers: [manoj, rahul] });
  const checkRahul = processQuery('what does he owe?', { customers: [manoj, rahul] });
  assert.strictEqual(checkRahul.data.customer.name, 'Rahul');
  assert.strictEqual(checkRahul.data.balance, 4500);
});

// ─── 5. NEGATION & MUTATION SAFETY ───
console.log('\n5. Negations & Passive Statements:');

testCase('NEGATION', "don't remind Manoj", () => {
  const res = processQuery("don't remind Manoj");
  assert.strictEqual(res.status, 'ANSWER');
  assert.strictEqual(res.intent, 'CANCELLED_MUTATION');
  assert.strictEqual(res.requiresConfirmation, false);
});

testCase('NEGATION', "Don't send reminder to Rahul", () => {
  const res = processQuery("Don't send reminder to Rahul");
  assert.strictEqual(res.status, 'ANSWER');
  assert.strictEqual(res.intent, 'CANCELLED_MUTATION');
});

testCase('PASSIVE_NOTE', 'I was talking to Manoj yesterday', () => {
  const res = processQuery('I was talking to Manoj yesterday');
  assert.strictEqual(res.status, 'ANSWER');
  assert.strictEqual(res.intent, 'CONVERSATIONAL_NOTE');
  assert.strictEqual(res.requiresConfirmation, false);
});

testCase('PASSIVE_NOTE', 'I need to know whether I should send Rahul a reminder', () => {
  const res = processQuery('I need to know whether I should send Rahul a reminder');
  assert.strictEqual(res.status, 'ANSWER');
  assert.strictEqual(res.intent, 'CONVERSATIONAL_NOTE');
  assert.strictEqual(res.requiresConfirmation, false);
});

// ─── 6. INDIAN / HINGLISH & REGIONAL TERMS ───
console.log('\n6. Indian / Hinglish & Regional Terminology:');

testCase('HINGLISH', 'Rahul ko payment reminder bhejo', () => {
  const res = processQuery('Rahul ko payment reminder bhejo');
  assert.strictEqual(res.intent, 'SEND_DUES_REMINDER');
  assert.strictEqual(res.entities.targetName, 'Rahul');
});

testCase('HINGLISH_CONV', '5 kilo ko gram mein batao', () => {
  const res = processQuery('5 kilo ko gram mein batao');
  assert.strictEqual(res.status, 'ANSWER');
  assert.strictEqual(res.intent, 'UNIT_CONVERSION');
  assert.strictEqual(res.data.result, 5000);
});

testCase('BUSINESS_TERMS', 'kharcha 250 chai nashta', () => {
  const res = processQuery('kharcha 250 chai nashta');
  assert.strictEqual(res.status, 'ACTION_PREVIEW');
  assert.strictEqual(res.intent, 'RECORD_EXPENSE');
  assert.strictEqual(res.entities.amount, 250);
  assert.strictEqual(res.entities.title, 'chai nashta');
});

testCase('BUSINESS_TERMS', 'advance 3000 to Sagar', () => {
  const res = processQuery('advance 3000 to Sagar');
  assert.strictEqual(res.status, 'ACTION_PREVIEW');
  assert.strictEqual(res.intent, 'RECORD_ADVANCE');
  assert.strictEqual(res.entities.amount, 3000);
  assert.strictEqual(res.entities.targetName, 'Sagar');
});

// ─── 7. SUMMARY & PERFORMANCE BENCHMARK ───
console.log('\n======================================================================');
console.log(` NATURAL LANGUAGE SUITE RESULTS: ${passedTests} / ${totalTests} Passing (${((passedTests / totalTests) * 100).toFixed(1)}%)`);
console.log('======================================================================\n');

if (failures.length > 0) {
  console.error('FAILURES SUMMARY:');
  failures.forEach(f => console.error(` - [${f.category}] "${f.query}": ${f.error}`));
  process.exit(1);
}

// Latency Benchmark on Natural Queries
console.log('EXECUTING NATURAL QUERY LATENCY BENCHMARK (1,000 Iterations)...');
const sampleQueries = [
  'My bill is ₹5,000, GST is 18%, and the customer gives me ₹10,000. How much change should I give?',
  "Show Manoj's outstanding balance and tell me how much I need to collect from him.",
  '5000 ka 18% GST laga ke agar customer 10000 de toh kitna wapas karna hai?',
  'what does Manoj owe?',
  "today's sales",
  'kharcha 120 chai nashta',
  '5000 plus 18% GST'
];

const latencies = [];
for (let i = 0; i < 1000; i++) {
  const q = sampleQueries[i % sampleQueries.length];
  const t0 = performance.now();
  processQuery(q);
  const t1 = performance.now();
  latencies.push(t1 - t0);
}

latencies.sort((a, b) => a - b);
const min = latencies[0];
const max = latencies[latencies.length - 1];
const sum = latencies.reduce((a, b) => a + b, 0);
const avg = sum / latencies.length;
const p50 = latencies[Math.floor(latencies.length * 0.50)];
const p95 = latencies[Math.floor(latencies.length * 0.95)];
const p99 = latencies[Math.floor(latencies.length * 0.99)];

console.log('Natural Query Latency Benchmark:');
console.log(`  - Total Iterations : ${latencies.length}`);
console.log(`  - Min Latency      : ${min.toFixed(4)} ms`);
console.log(`  - Average Latency  : ${avg.toFixed(4)} ms`);
console.log(`  - Median (P50)     : ${p50.toFixed(4)} ms`);
console.log(`  - P95 Latency      : ${p95.toFixed(4)} ms`);
console.log(`  - P99 Latency      : ${p99.toFixed(4)} ms`);
console.log(`  - Maximum Latency  : ${max.toFixed(4)} ms\n`);
