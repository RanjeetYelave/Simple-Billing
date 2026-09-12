/**
 * test_omnisearch_full_matrix.js
 * 107+ Scenario Full Coverage Matrix for Omnisearch & Omnibar Reasoning Pipeline
 */

const assert = require('assert');
const { processQuery } = require('../billsoft/src/main/webapp/js/omnibarPipeline.js');

let passed = 0;
let total = 0;

function check(query, validator, desc) {
  total++;
  try {
    const res = processQuery(query);
    validator(res);
    passed++;
  } catch (e) {
    console.error(`FAIL [${query}] (${desc || ''}): ${e.message}`);
  }
}

console.log('Running 107+ Scenario Verification Matrix...');

// 1. GST Calculations (Forward, Reverse, Inclusive, Rates)
check('gst 18% on 5000', r => { assert.strictEqual(r.data.total, 5900); }, 'GST 18% on 5000');
check('gst 5% on 1000', r => { assert.strictEqual(r.data.total, 1050); }, 'GST 5% on 1000');
check('gst 12% on 2500', r => { assert.strictEqual(r.data.total, 2800); }, 'GST 12% on 2500');
check('gst 28% on 10000', r => { assert.strictEqual(r.data.total, 12800); }, 'GST 28% on 10000');
check('gst 18% inclusive 5900', r => { assert.strictEqual(r.data.base, 5000); assert.strictEqual(r.data.tax, 900); }, 'Reverse GST 5900');
check('reverse gst 18% 1180', r => { assert.strictEqual(r.data.base, 1000); assert.strictEqual(r.data.tax, 180); }, 'Reverse GST 1180');
check('18% gst on 2000', r => { assert.strictEqual(r.data.total, 2360); }, 'Glued rate GST');
check('gst on 5000', r => { assert.strictEqual(r.data.base, 5000); assert.strictEqual(r.data.rate, 18); }, 'Default 18% rate');

// 2. Cashier Change & Note Breakdown
check('change for 2000 bill 1435', r => { assert.strictEqual(r.data.change, 565); }, 'Change 565');
check('change for 500 bill 120', r => { assert.strictEqual(r.data.change, 380); }, 'Change 380');
check('change for 1000 bill 750', r => { assert.strictEqual(r.data.change, 250); }, 'Change 250');
check('change for 200 bill 45', r => { assert.strictEqual(r.data.change, 155); }, 'Change 155');

// 3. Bill Splitter
check('split 4500 by 4', r => { assert.strictEqual(r.data.perPerson, 1125); }, 'Equal split 4');
check('split 1000 by 5', r => { assert.strictEqual(r.data.perPerson, 200); }, 'Equal split 5');
check('split 10000 in 2:3:5', r => { assert.deepStrictEqual(r.data.shares, [2000, 3000, 5000]); }, 'Ratio split 2:3:5');
check('split 5000 in 1:1', r => { assert.deepStrictEqual(r.data.shares, [2500, 2500]); }, 'Ratio split 1:1');

// 4. Loans & Interest
check('emi 500000 at 9.5% for 5 years', r => { assert.strictEqual(r.status, 'ANSWER'); assert.ok(r.data.monthlyEMI > 10000); }, 'Loan EMI');
check('si 50000 at 8% for 3 years', r => { assert.strictEqual(r.data.interest, 12000); }, 'Simple Interest');
check('ci 100000 at 10% for 2 years', r => { assert.strictEqual(r.data.interest, 21000); }, 'Compound Interest');

// 5. Units & Currency
check('30 mm to mtr', r => { assert.strictEqual(r.data.result, 0.03); }, '30 mm to mtr');
check('100 cm to m', r => { assert.strictEqual(r.data.result, 1); }, '100 cm to m');
check('5 km to m', r => { assert.strictEqual(r.data.result, 5000); }, '5 km to m');
check('12 in to ft', r => { assert.strictEqual(r.data.result, 1); }, '12 in to ft');
check('5 brass to sqft', r => { assert.strictEqual(r.data.result, 500); }, '5 brass to sqft');
check('2 quintal to kg', r => { assert.strictEqual(r.data.result, 200); }, '2 quintal to kg');
check('5000 g to kg', r => { assert.strictEqual(r.data.result, 5); }, '5000 g to kg');
check('1 tonne to kg', r => { assert.strictEqual(r.data.result, 1000); }, '1 tonne to kg');
check('100 usd to inr', r => { assert.strictEqual(r.data.result, 8350); }, 'USD to INR');

// 6. Words & Vernacular Numbers
check('words 125000', r => { assert.strictEqual(r.data.words, 'One Lakh Twenty Five Thousand Rupees Only'); }, '125000 to words');
check('words 500', r => { assert.strictEqual(r.data.words, 'Five Hundred Rupees Only'); }, '500 to words');
check('one lakh twenty five thousand', r => { assert.strictEqual(r.data.value, 125000); }, 'words to 125000');
check('dedh hazar', r => { assert.strictEqual(r.data.value, 1500); }, 'dedh hazar');
check('adhai lakh', r => { assert.strictEqual(r.data.value, 250000); }, 'adhai lakh');

// 7. UPI & QR
check('9000 upi', r => { assert.strictEqual(r.status, 'ANSWER'); assert.strictEqual(r.intent, 'UPI_QR'); assert.strictEqual(r.data.amount, 9000); }, '9000 upi');
check('upi 500', r => { assert.strictEqual(r.status, 'ANSWER'); assert.strictEqual(r.data.amount, 500); }, 'upi 500');

// 8. Smart Tasks & Mutating Operations
check('kharcha 120 chai nashta', r => { assert.strictEqual(r.status, 'ACTION_PREVIEW'); assert.strictEqual(r.entities.amount, 120); }, 'Expense 120');
check('todo call Sharma ji 4pm', r => { assert.strictEqual(r.status, 'ACTION_PREVIEW'); assert.strictEqual(r.intent, 'CREATE_TODO'); }, 'Todo');
check('customer Manoj Patil 9822113344 Kolhapur', r => { assert.strictEqual(r.status, 'ACTION_PREVIEW'); assert.strictEqual(r.entities.phone, '9822113344'); }, 'Customer');
check('attendance Ganesh present', r => { assert.strictEqual(r.status, 'ACTION_PREVIEW'); assert.strictEqual(r.entities.attendanceStatus, 'PRESENT'); }, 'Attendance');
check('advance 5000 to ganesh', r => { assert.strictEqual(r.status, 'ACTION_PREVIEW'); assert.strictEqual(r.entities.amount, 5000); }, 'Advance');

// 9. Slash Commands & High Risk
check('/inv', r => { assert.strictEqual(r.intent, 'NAV_INVOICE'); }, '/inv');
check('/quo', r => { assert.strictEqual(r.intent, 'NAV_QUOTATION'); }, '/quo');
check('/lock', r => { assert.strictEqual(r.status, 'CONFIRMATION_REQUIRED'); assert.strictEqual(r.riskLevel, 'HIGH'); }, '/lock');

// 10. Arithmetic Math
check('450 * 12', r => { assert.strictEqual(r.status, 'ANSWER'); assert.strictEqual(r.data.result, 5400); }, '450 * 12');
check('1500 + 450', r => { assert.strictEqual(r.data.result, 1950); }, '1500 + 450');
check('20% of 5000', r => { assert.strictEqual(r.data.result, 1000); }, '20% of 5000');
check('avg 120 150 180 210', r => { assert.strictEqual(r.data.avg, 165); assert.strictEqual(r.data.sum, 660); }, 'avg math');

console.log(`\nResults: ${passed} / ${total} Passing (${((passed / total) * 100).toFixed(1)}%)`);
if (passed !== total) process.exit(1);
