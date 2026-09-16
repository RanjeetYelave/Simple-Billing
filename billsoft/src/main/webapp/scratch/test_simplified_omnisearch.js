/**
 * test_simplified_omnisearch.js
 * Comprehensive automated verification test suite for Simplified OmniSearch
 */

const assert = require('assert');
const path = require('path');
const pipeline = require('../js/omnibarPipeline.js');

const {
  DeterministicNormalizer,
  UnitRates,
  WordsEngine,
  RoleResolver,
  CalculationEvaluator,
  CapabilityClassifier,
  QuickHelpAdapter,
  OmnibarPipeline,
  processQuery
} = pipeline;

console.log('🧪 Starting Simplified OmniSearch Automated Test Suite...\n');

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

// ─────────────────────────────────────────────────────────────
// 1. NLP NORMALIZATION & USER MISTAKE TOLERANCE
// ─────────────────────────────────────────────────────────────
console.log('1. Testing NLP Normalization & Mistake Tolerance:');

runTest('Punctuation and case normalization', () => {
  const norm = DeterministicNormalizer.normalize("TODAY'S SALES???");
  assert.strictEqual(norm.lower, 'todays sales');
});

runTest('Glued tokens decoupling (18%gst, 500upi, 10000rs)', () => {
  assert.strictEqual(DeterministicNormalizer.splitGluedTokens('18%gst'), '18% gst');
  assert.strictEqual(DeterministicNormalizer.splitGluedTokens('500upi'), '500 upi');
  assert.strictEqual(DeterministicNormalizer.splitGluedTokens('10000rs'), '10000 rs');
  assert.strictEqual(DeterministicNormalizer.splitGluedTokens('rs500'), 'rs 500');
  assert.strictEqual(DeterministicNormalizer.splitGluedTokens('30mm'), '30 mm');
});

runTest('Typo correction (custmer, invoce, outstading, expence, kharch)', () => {
  const norm = DeterministicNormalizer.normalize('custmer invoce outstading expence kharch');
  assert.ok(norm.lower.includes('customer'));
  assert.ok(norm.lower.includes('invoice'));
  assert.ok(norm.lower.includes('outstanding'));
  assert.ok(norm.lower.includes('expense'));
  assert.ok(norm.lower.includes('kharcha'));
});

runTest('Conversational filler removal', () => {
  const norm = DeterministicNormalizer.normalize('please tell me what is the today sales');
  assert.strictEqual(norm.cleaned, 'today sales');
});

// ─────────────────────────────────────────────────────────────
// 2. SPECIAL: NUMBER-TO-WORDS & WORDS-TO-NUMBER
// ─────────────────────────────────────────────────────────────
console.log('\n2. Testing Special: Number-to-Words & Words-to-Number:');

runTest('Number to Indian Words (125000)', () => {
  const words = WordsEngine.toWords(125000);
  assert.ok(words.toLowerCase().includes('one lakh twenty-five thousand'));
});

runTest('Words to Number ("dedh lakh", "dhai hazar", "two crore")', () => {
  assert.strictEqual(WordsEngine.wordsToNumber('dedh lakh'), 150000);
  assert.strictEqual(WordsEngine.wordsToNumber('dhai hazar'), 2500);
  assert.strictEqual(WordsEngine.wordsToNumber('two crore'), 20000000);
  assert.strictEqual(WordsEngine.wordsToNumber('one lakh twenty five thousand'), 125000);
});

// ─────────────────────────────────────────────────────────────
// 3. SPECIAL: COMMERCIAL & TAX MATH
// ─────────────────────────────────────────────────────────────
console.log('\n3. Testing Special: Commercial & Tax Math:');

runTest('Arithmetic with Operator Precedence (100 + 20 * 5 - 50 / 2)', () => {
  const res = CalculationEvaluator.evaluateArithmetic('100 + 20 * 5 - 50 / 2');
  assert.strictEqual(res.result, 175);
});

runTest('Complex Arithmetic ("(450 * 12) + 2500" & "450 + 2500 - 300")', () => {
  const r1 = processQuery('(450 * 12) + 2500');
  assert.strictEqual(r1.category, 'SPECIAL');
  assert.strictEqual(r1.capabilityId, 'SPEC_MATH_ARITH');
  assert.strictEqual(r1.data.result, 7900);

  const r2 = processQuery('450 + 2500 - 300');
  assert.strictEqual(r2.data.result, 2650);
});

runTest('Percentage of Base ("17% of 5633", "what is 17 percent of 5633", "5000 ka 10 percent")', () => {
  const r1 = processQuery('17% of 5633');
  assert.strictEqual(r1.category, 'SPECIAL');
  assert.strictEqual(r1.capabilityId, 'SPEC_MATH_ARITH');
  assert.strictEqual(r1.data.result, 957.61);

  const r2 = processQuery('what is 17 percent of 5633');
  assert.strictEqual(r2.data.result, 957.61);

  const r3 = processQuery('5000 ka 10 percent');
  assert.strictEqual(r3.data.result, 500);

  const r4 = processQuery('5633 * 17%');
  assert.strictEqual(r4.data.result, 957.61);
});

runTest('Percentage Addition / Increase / Markup ("5633 + 17%", "add 17% to 5633", "17% increase on 5633")', () => {
  const r1 = processQuery('5633 + 17%');
  assert.strictEqual(r1.category, 'SPECIAL');
  assert.strictEqual(r1.capabilityId, 'SPEC_MATH_ARITH');
  assert.strictEqual(r1.data.result, 6590.61);

  const r2 = processQuery('add 17% to 5633');
  assert.strictEqual(r2.data.result, 6590.61);

  const r3 = processQuery('17% increase on 5633');
  assert.strictEqual(r3.data.result, 6590.61);

  const r4 = processQuery('5000 mein 10 percent add karo');
  assert.strictEqual(r4.data.result, 5500);

  const r5 = processQuery('5000 मध्ये 10% वाढ');
  assert.strictEqual(r5.data.result, 5500);
});

runTest('Percentage Subtraction / Discount / Decrease ("5633 - 17%", "reduce 5633 by 17%", "5633 less 17%")', () => {
  const r1 = processQuery('5633 - 17%');
  assert.strictEqual(r1.category, 'SPECIAL');
  assert.strictEqual(r1.capabilityId, 'SPEC_MATH_ARITH');
  assert.strictEqual(r1.data.result, 4675.39);

  const r2 = processQuery('reduce 5633 by 17%');
  assert.strictEqual(r2.data.result, 4675.39);

  const r3 = processQuery('5633 less 17%');
  assert.strictEqual(r3.data.result, 4675.39);

  const r4 = processQuery('take 17 percent off 5633');
  assert.strictEqual(r4.data.result, 4675.39);

  const r5 = processQuery('5000 se 10 percent kam karo');
  assert.strictEqual(r5.data.result, 4500);

  const r6 = processQuery('5000 madhun 10 percent kami');
  assert.strictEqual(r6.data.result, 4500);
});

runTest('Complex Percentage Expressions ("20% of 5000 + 350", "5000 - 10% + 250", "(1500 + 500) * 18%")', () => {
  const r1 = processQuery('20% of 5000 + 350');
  assert.strictEqual(r1.data.result, 1350);

  const r2 = processQuery('5000 - 10% + 250');
  assert.strictEqual(r2.data.result, 4750);

  const r3 = processQuery('(1500 + 500) * 18%');
  assert.strictEqual(r3.data.result, 360);
});

runTest('Powers, Roots & Fractions ("square of 25", "cube of 12", "sqrt 144", "half of 15000", "2 ^ 8")', () => {
  const r1 = processQuery('square of 25');
  assert.strictEqual(r1.data.result, 625);

  const r2 = processQuery('cube of 12');
  assert.strictEqual(r2.data.result, 1728);

  const r3 = processQuery('square root of 144');
  assert.strictEqual(r3.data.result, 12);

  const r4 = processQuery('2 ^ 8');
  assert.strictEqual(r4.data.result, 256);

  const r5 = processQuery('half of 15000');
  assert.strictEqual(r5.data.result, 7500);

  const r6 = processQuery('5000 ka aadha');
  assert.strictEqual(r6.data.result, 2500);

  const r7 = processQuery('quarter of 50000');
  assert.strictEqual(r7.data.result, 12500);

  const r8 = processQuery('double of 8500');
  assert.strictEqual(r8.data.result, 17000);

  const r9 = processQuery('triple of 4200');
  assert.strictEqual(r9.data.result, 12600);
});

runTest('Natural Language Binary Arithmetic ("divide 10000 by 4", "multiply 250 by 12", "5000 less 750")', () => {
  const r1 = processQuery('divide 10000 by 4');
  assert.strictEqual(r1.data.result, 2500);

  const r2 = processQuery('multiply 250 by 12');
  assert.strictEqual(r2.data.result, 3000);

  const r3 = processQuery('5000 less 750');
  assert.strictEqual(r3.data.result, 4250);

  const r4 = processQuery('5000 + 750');
  assert.strictEqual(r4.data.result, 5750);

  const r5 = processQuery('calculate (500 + 250) * 4');
  assert.strictEqual(r5.data.result, 3000);

  const r6 = processQuery('10000 ko 4 se divide karo');
  assert.strictEqual(r6.data.result, 2500);
});

runTest('Indian Numbering, Suffixes & Currencies ("₹1,50,000 + ₹25,000", "1.5 lakh + 50 hazar", "2.5 cr - 50 lakh")', () => {
  const r1 = processQuery('₹1,50,000 + ₹25,000');
  assert.strictEqual(r1.data.result, 175000);

  const r2 = processQuery('1.5 lakh + 50 hazar');
  assert.strictEqual(r2.data.result, 200000);

  const r3 = processQuery('2.5 cr - 50 lakh');
  assert.strictEqual(r3.data.result, 20000000);

  const r4 = processQuery('10k * 5');
  assert.strictEqual(r4.data.result, 50000);
});

runTest('Typo Tolerance in Arithmetic ("17 percentof 5633", "17%of5633", "multipy 250 by 12", "divde 10000 by 4")', () => {
  const r1 = processQuery('17 percentof 5633');
  assert.strictEqual(r1.data.result, 957.61);

  const r2 = processQuery('17%of5633');
  assert.strictEqual(r2.data.result, 957.61);

  const r3 = processQuery('multipy 250 by 12');
  assert.strictEqual(r3.data.result, 3000);

  const r4 = processQuery('divde 10000 by 4');
  assert.strictEqual(r4.data.result, 2500);

  const r5 = processQuery('substact 300 from 1500');
  assert.strictEqual(r5.data.result, 1200);

  const r6 = processQuery('calculte 15% of 8000');
  assert.strictEqual(r6.data.result, 1200);
});

runTest('Ambiguity Safety (Do NOT evaluate incomplete/ambiguous fragments as math)', () => {
  const r1 = processQuery('10% less');
  assert.notStrictEqual(r1.capabilityId, 'SPEC_MATH_ARITH');

  const r2 = processQuery('increase by 20');
  assert.notStrictEqual(r2.capabilityId, 'SPEC_MATH_ARITH');
});

runTest('GST Exclusive ("gst 18% on 5000")', () => {
  const res = processQuery('gst 18% on 5000');
  assert.strictEqual(res.category, 'SPECIAL');
  assert.strictEqual(res.capabilityId, 'SPEC_MATH_GST_EXCL');
  assert.strictEqual(res.data.base, 5000);
  assert.strictEqual(res.data.tax, 900);
  assert.strictEqual(res.data.total, 5900);
  assert.strictEqual(res.data.cgst, 450);
  assert.strictEqual(res.data.sgst, 450);
});

runTest('GST Inclusive ("gst 18% inclusive 5900")', () => {
  const res = processQuery('gst 18% inclusive 5900');
  assert.strictEqual(res.category, 'SPECIAL');
  assert.strictEqual(res.capabilityId, 'SPEC_MATH_GST_INCL');
  assert.strictEqual(res.data.base, 5000);
  assert.strictEqual(res.data.tax, 900);
  assert.strictEqual(res.data.total, 5900);
});

runTest('Cashier Change & Note Breakdown ("change for 2000 bill 1435")', () => {
  const res = processQuery('change for 2000 bill 1435');
  assert.strictEqual(res.category, 'SPECIAL');
  assert.strictEqual(res.capabilityId, 'SPEC_MATH_CHANGE');
  assert.strictEqual(res.data.paid, 2000);
  assert.strictEqual(res.data.bill, 1435);
  assert.strictEqual(res.data.change, 565);
  assert.deepStrictEqual(res.data.notes, { '500': 1, '50': 1, '10': 1, '5': 1 });
});

runTest('Bill Splitting ("split 4500 by 4")', () => {
  const res = processQuery('split 4500 by 4');
  assert.strictEqual(res.category, 'SPECIAL');
  assert.strictEqual(res.capabilityId, 'SPEC_MATH_SPLIT');
  assert.strictEqual(res.data.total, 4500);
  assert.strictEqual(res.data.count, 4);
  assert.strictEqual(res.data.perPerson, 1125);
});

runTest('Profit Margin & Markup ("margin cost 800 price 1200")', () => {
  const res = processQuery('margin cost 800 price 1200');
  assert.strictEqual(res.category, 'SPECIAL');
  assert.strictEqual(res.capabilityId, 'SPEC_MATH_MARGIN');
  assert.strictEqual(res.data.profit, 400);
  assert.strictEqual(res.data.marginPercent, 33.33);
  assert.strictEqual(res.data.markupPercent, 50);
});

runTest('Discount Calculation ("10% discount on 5000")', () => {
  const res = processQuery('10% discount on 5000');
  assert.strictEqual(res.category, 'SPECIAL');
  assert.strictEqual(res.capabilityId, 'SPEC_MATH_DISCOUNT');
  assert.strictEqual(res.data.amount, 5000);
  assert.strictEqual(res.data.discount, 500);
  assert.strictEqual(res.data.netTotal, 4500);
});

runTest('Loan EMI Calculation ("emi 500000 at 9.5% for 5 years")', () => {
  const res = processQuery('emi 500000 at 9.5% for 5 years');
  assert.strictEqual(res.category, 'SPECIAL');
  assert.strictEqual(res.capabilityId, 'SPEC_MATH_EMI');
  assert.strictEqual(res.data.principal, 500000);
  assert.ok(res.data.monthlyEmi > 10000 && res.data.monthlyEmi < 11000);
});

// ─────────────────────────────────────────────────────────────
// 4. SPECIAL: UNIT CONVERSIONS & LIVE UPI QR
// ─────────────────────────────────────────────────────────────
console.log('\n4. Testing Special: Unit Conversions & Live UPI QR:');

runTest('Unit Conversion (5 kg in grams)', () => {
  const res = processQuery('5 kg in grams');
  assert.strictEqual(res.category, 'SPECIAL');
  assert.strictEqual(res.capabilityId, 'SPEC_CONV_UNIT');
  assert.strictEqual(res.data.result, 5000);
});

runTest('Indian Trade Unit (2 quintal in kg)', () => {
  const res = processQuery('2 quintal in kg');
  assert.strictEqual(res.category, 'SPECIAL');
  assert.strictEqual(res.capabilityId, 'SPEC_CONV_UNIT');
  assert.strictEqual(res.data.result, 200);
});

runTest('Live UPI QR generation ("upi 500")', () => {
  const mockCtx = { firm: { upiId: 'store@upi', firmName: 'Test Store' } };
  const res = processQuery('upi 500', mockCtx);
  assert.strictEqual(res.category, 'SPECIAL');
  assert.strictEqual(res.capabilityId, 'SPEC_PAY_UPI_QR');
  assert.strictEqual(res.data.amount, 500);
  assert.strictEqual(res.data.upiId, 'store@upi');
  assert.ok(res.data.qrUrl.includes('store%40upi'));
});

// ─────────────────────────────────────────────────────────────
// 5. QUICK HELP: AUTHORITATIVE CRM QUERIES
// ─────────────────────────────────────────────────────────────
console.log('\n5. Testing Quick Help: Authoritative CRM Queries:');

const mockCtx = {
  customers: [
    { id: 1, name: 'Rahul Patil', phone: '9822114455', balance: 4500, city: 'Kolhapur' },
    { id: 2, name: 'Suresh More', phone: '9822998877', balance: 0, city: 'Pune' },
    { id: 3, name: 'Amit Sharma', phone: '9811223344', balance: 12000, city: 'Mumbai' }
  ],
  invoices: [
    { id: 101, invoiceNumber: 'INV-101', customerName: 'Rahul Patil', netTotal: 4500, invoiceDate: new Date().toISOString().slice(0, 10), status: 'UNPAID' },
    { id: 102, invoiceNumber: 'INV-102', customerName: 'Suresh More', netTotal: 8000, invoiceDate: new Date().toISOString().slice(0, 10), status: 'PAID' }
  ],
  products: [
    { id: 1, name: 'Cement 50kg Bag', stock: 3, minStockAlert: 10, unit: 'bags' },
    { id: 2, name: 'Steel 10mm Rod', stock: 50, minStockAlert: 5, unit: 'pcs' }
  ],
  firm: {
    firmName: 'Patil Enterprises',
    bankName: 'State Bank of India',
    accountNumber: '1234567890',
    ifscCode: 'SBIN0001234',
    gstin: '27AAAAA0000A1Z5'
  }
};

runTest('Quick Help: Today Sales ("today sales")', () => {
  const res = processQuery('today sales', mockCtx);
  assert.strictEqual(res.category, 'QUICK_HELP');
  assert.strictEqual(res.capabilityId, 'QH_SALES_TODAY');
  assert.strictEqual(res.data.totalRevenue, 12500);
  assert.strictEqual(res.data.invoiceCount, 2);
});

runTest('Quick Help: Total Udhari ("total udhari")', () => {
  const res = processQuery('total udhari', mockCtx);
  assert.strictEqual(res.category, 'QUICK_HELP');
  assert.strictEqual(res.capabilityId, 'QH_TOTAL_UDHARI');
  assert.strictEqual(res.data.totalUdhari, 16500);
  assert.strictEqual(res.data.customerCount, 2);
});

runTest('Quick Help: Low Stock ("low stock")', () => {
  const res = processQuery('low stock', mockCtx);
  assert.strictEqual(res.category, 'QUICK_HELP');
  assert.strictEqual(res.capabilityId, 'QH_LOW_STOCK');
  assert.strictEqual(res.data.lowStockCount, 1);
});

runTest('Quick Help: Firm Bank Details ("bank details")', () => {
  const res = processQuery('bank details', mockCtx);
  assert.strictEqual(res.category, 'QUICK_HELP');
  assert.strictEqual(res.capabilityId, 'QH_FIRM_BANK');
  assert.strictEqual(res.data.bankName, 'State Bank of India');
  assert.strictEqual(res.data.accountNumber, '1234567890');
});

runTest('Quick Help: Customer Due ("rahul udhari")', () => {
  const res = processQuery('rahul udhari', mockCtx);
  assert.strictEqual(res.category, 'QUICK_HELP');
  assert.strictEqual(res.capabilityId, 'QH_CUSTOMER_DUE');
  assert.strictEqual(res.data.customerName, 'Rahul Patil');
  assert.strictEqual(res.data.balance, 4500);
});

// ─────────────────────────────────────────────────────────────
// 6. ACTIONS: PREVIEWS, DIRECT ACTIONS, NAVIGATION
// ─────────────────────────────────────────────────────────────
console.log('\n6. Testing Actions: Previews, Direct Actions, Navigation:');

runTest('Action: Direct Theme Toggle ("dark mode")', () => {
  const res = processQuery('dark mode');
  assert.strictEqual(res.category, 'ACTION');
  assert.strictEqual(res.capabilityId, 'ACT_THEME_TOGGLE');
  assert.strictEqual(res.status, 'DIRECT_ACTION');
});

runTest('Action: Direct App Lock ("lock screen")', () => {
  const res = processQuery('lock screen');
  assert.strictEqual(res.category, 'ACTION');
  assert.strictEqual(res.capabilityId, 'ACT_APP_LOCK');
  assert.strictEqual(res.status, 'DIRECT_ACTION');
});

runTest('Action: Reminder with Date & Clean Title ("set reminder create bhel on 27 september")', () => {
  const res = processQuery('set reminder create bhel on 27 september');
  assert.strictEqual(res.category, 'ACTION');
  assert.strictEqual(res.capabilityId, 'ACT_CREATE_TASK');
  assert.strictEqual(res.status, 'ACTION_PREVIEW');
  assert.strictEqual(res.badge, '⚡ Action');
  assert.strictEqual(res.icon, '⏰');
  assert.strictEqual(res.entities.title, 'Create bhel');
  assert.strictEqual(res.entities.dueDate, `${new Date().getFullYear()}-09-27T09:00:00`);
  assert.strictEqual(res.editableConfig.initialValues.title, 'Create bhel');
  assert.strictEqual(res.editableConfig.initialValues.dueDate, `${new Date().getFullYear()}-09-27T09:00:00`);
  assert.strictEqual(res.title, '⏰ Set Reminder: "Create bhel"');
});

runTest('Action: Reminder with Typos & Human Query ("remind me to buy milk on septeber 27")', () => {
  const res = processQuery('remind me to buy milk on septeber 27');
  assert.strictEqual(res.category, 'ACTION');
  assert.strictEqual(res.capabilityId, 'ACT_CREATE_TASK');
  assert.strictEqual(res.status, 'ACTION_PREVIEW');
  assert.strictEqual(res.badge, '⚡ Action');
  assert.strictEqual(res.icon, '⏰');
  assert.strictEqual(res.entities.title, 'Buy milk');
  assert.strictEqual(res.entities.dueDate, `${new Date().getFullYear()}-09-27T09:00:00`);
  assert.strictEqual(res.editableConfig.initialValues.title, 'Buy milk');
  assert.strictEqual(res.title, '⏰ Set Reminder: "Buy milk"');
});

runTest('Action: Expense Preview ("kharcha 120 chai nashta")', () => {
  const res = processQuery('kharcha 120 chai nashta');
  assert.strictEqual(res.category, 'ACTION');
  assert.strictEqual(res.capabilityId, 'ACT_CREATE_EXPENSE');
  assert.strictEqual(res.status, 'ACTION_PREVIEW');
  assert.strictEqual(res.badge, '⚡ Action');
  assert.strictEqual(res.entities.amount, 120);
  assert.strictEqual(res.entities.title, 'Chai nashta');
  assert.strictEqual(res.title, '💸 Record Expense: ₹120 (Chai nashta)');
});

runTest('Action: Sticky Note ("note client requested 5% discount on bulk")', () => {
  const res = processQuery('note client requested 5% discount on bulk');
  assert.strictEqual(res.category, 'ACTION');
  assert.strictEqual(res.capabilityId, 'ACT_CREATE_NOTE');
  assert.strictEqual(res.status, 'ACTION_PREVIEW');
  assert.strictEqual(res.badge, '⚡ Action');
  assert.strictEqual(res.entities.title, 'Client requested 5% discount on bulk');
});

runTest('Action: Customer Registration ("customer Ramesh 9811223344 Pune 5000")', () => {
  const res = processQuery('customer Ramesh 9811223344 Pune 5000');
  assert.strictEqual(res.category, 'ACTION');
  assert.strictEqual(res.capabilityId, 'ACT_CREATE_CUSTOMER');
  assert.strictEqual(res.status, 'ACTION_PREVIEW');
  assert.strictEqual(res.badge, '⚡ Action');
  assert.strictEqual(res.entities.customerName, 'Ramesh');
  assert.strictEqual(res.entities.phone, '9811223344');
  assert.strictEqual(res.entities.city, 'Pune');
});

runTest('Action: Mark Attendance ("attendance Sachin present today")', () => {
  const res = processQuery('attendance Sachin present today');
  assert.strictEqual(res.category, 'ACTION');
  assert.strictEqual(res.capabilityId, 'ACT_MARK_ATTENDANCE');
  assert.strictEqual(res.status, 'ACTION_PREVIEW');
  assert.strictEqual(res.badge, '⚡ Action');
  assert.strictEqual(res.entities.staffName, 'Sachin');
  assert.strictEqual(res.entities.attendanceStatus, 'PRESENT');
});

runTest('Action: Record Staff Advance ("advance Sachin 2000")', () => {
  const res = processQuery('advance Sachin 2000');
  assert.strictEqual(res.category, 'ACTION');
  assert.strictEqual(res.capabilityId, 'ACT_RECORD_ADVANCE');
  assert.strictEqual(res.status, 'ACTION_PREVIEW');
  assert.strictEqual(res.badge, '⚡ Action');
  assert.strictEqual(res.entities.staffName, 'Sachin');
  assert.strictEqual(res.entities.amount, 2000);
});

runTest('Action: Navigation Slash Command ("/inv")', () => {
  const res = processQuery('/inv');
  assert.strictEqual(res.category, 'ACTION');
  assert.strictEqual(res.capabilityId, 'ACT_NAV_SLASH');
  assert.strictEqual(res.status, 'NAVIGATE');
  assert.strictEqual(res.target.page, 'invoices');
});

// ─────────────────────────────────────────────────────────────
// 7. ENTITY DISAMBIGUATION
// ─────────────────────────────────────────────────────────────
console.log('\n7. Testing Entity Disambiguation:');

runTest('Disambiguation on multiple name matches ("Amit")', () => {
  const ambigCtx = {
    customers: [
      { id: 10, name: 'Amit Sharma', phone: '9811000000' },
      { id: 11, name: 'Amit Verma', phone: '9822000000' }
    ]
  };
  const res = processQuery('Amit udhari', ambigCtx);
  assert.strictEqual(res.status, 'AMBIGUOUS');
  assert.strictEqual(res.candidates.length, 2);
});

console.log(`\n========================================`);
console.log(`🎯 Test Results: ${passedTests} / ${totalTests} passed (${Math.round((passedTests / totalTests) * 100)}%)`);
console.log(`========================================\n`);

if (passedTests !== totalTests) {
  process.exit(1);
}
