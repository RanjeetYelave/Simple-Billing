/**
 * Comprehensive Test Suite for OmniSearch Deterministic Arithmetic Engine
 */
const fs = require('fs');
const path = require('path');
const assert = require('assert');

const pipeline = require('../js/omnibarPipeline.js');

const {
  DeterministicNormalizer,
  SafeArithmeticEngine,
  CalculationEvaluator,
  OmnibarPipeline,
  processQuery
} = pipeline;

console.log('🧪 Starting Comprehensive Arithmetic Engine Test Matrix...\n');

let passed = 0;
let total = 0;

function testCase(category, name, fn) {
  total++;
  try {
    fn();
    passed++;
    console.log(`  ✓ [${category}] ${name}`);
  } catch (err) {
    console.error(`  ✗ [${category}] ${name}`);
    console.error(`    ${err.message}`);
  }
}

function assertMath(query, expectedResult, expectedCapability = 'SPEC_MATH_ARITH') {
  const res = processQuery(query);
  assert(res != null, `Query "${query}" returned null result`);
  assert.strictEqual(res.category, 'SPECIAL', `Query "${query}" category was ${res.category}, expected SPECIAL`);
  assert(
    res.capabilityId === expectedCapability || res.capabilityId.startsWith('SPEC_MATH'),
    `Query "${query}" capability was ${res.capabilityId}, expected ${expectedCapability}`
  );
  
  const val = res.data.result != null ? res.data.result : (res.data.total != null ? res.data.total : res.data.finalAmount);
  const diff = Math.abs(val - expectedResult);
  assert(diff < 0.02, `Query "${query}" expected ${expectedResult}, got ${val} (diff: ${diff})`);
}

// 1. Basic Operators & Precedence
testCase('Operators', 'Standard addition', () => assertMath('450 + 2500', 2950));
testCase('Operators', 'Standard subtraction', () => assertMath('2500 - 450', 2050));
testCase('Operators', 'Standard multiplication', () => assertMath('450 * 12', 5400));
testCase('Operators', 'Standard division', () => assertMath('10000 / 4', 2500));
testCase('Operators', 'Modulo remainder', () => assertMath('25 % 4', 1));
testCase('Operators', 'Power / Exponent', () => assertMath('2 ^ 8', 256));
testCase('Operators', 'Precedence without parens (100 + 20 * 5 - 50 / 2)', () => assertMath('100 + 20 * 5 - 50 / 2', 175));
testCase('Operators', 'Precedence with parens ((450 * 12) + 2500)', () => assertMath('(450 * 12) + 2500', 7900));
testCase('Operators', 'Nested parens ((100 + 50) * (20 - 5))', () => assertMath('((100 + 50) * (20 - 5))', 2250));
testCase('Operators', 'Decimal precision (45.75 * 3.5)', () => assertMath('45.75 * 3.5', 160.13));

// 2. Natural Language Equivalents
testCase('Natural Language', 'plus: 450 plus 2500', () => assertMath('450 plus 2500', 2950));
testCase('Natural Language', 'minus: 2500 minus 450', () => assertMath('2500 minus 450', 2050));
testCase('Natural Language', 'times: 250 times 12', () => assertMath('250 times 12', 3000));
testCase('Natural Language', 'divide by: divide 10000 by 4', () => assertMath('divide 10000 by 4', 2500));
testCase('Natural Language', 'multiply by: multiply 250 by 12', () => assertMath('multiply 250 by 12', 3000));
testCase('Natural Language', 'add ... and ...: add 500 and 1500', () => assertMath('add 500 and 1500', 2000));
testCase('Natural Language', 'subtract ... from ...: subtract 300 from 1500', () => assertMath('subtract 300 from 1500', 1200));
testCase('Natural Language', '5000 less 750', () => assertMath('5000 less 750', 4250));
testCase('Natural Language', 'square of: square of 25', () => assertMath('square of 25', 625));
testCase('Natural Language', 'cube of: cube of 12', () => assertMath('cube of 12', 1728));
testCase('Natural Language', 'sqrt: sqrt 144', () => assertMath('sqrt 144', 12));
testCase('Natural Language', 'half of: half of 15000', () => assertMath('half of 15000', 7500));
testCase('Natural Language', 'quarter of: quarter of 50000', () => assertMath('quarter of 50000', 12500));
testCase('Natural Language', 'double of: double of 8500', () => assertMath('double of 8500', 17000));
testCase('Natural Language', 'triple of: triple of 4200', () => assertMath('triple of 4200', 12600));

// 3. Percentage Intelligence
testCase('Percentage', '17% of 5633', () => assertMath('17% of 5633', 957.61));
testCase('Percentage', 'what is 17 percent of 5633', () => assertMath('what is 17 percent of 5633', 957.61));
testCase('Percentage', 'calculate 15% of 8000', () => assertMath('calculate 15% of 8000', 1200));
testCase('Percentage', '5633 * 17%', () => assertMath('5633 * 17%', 957.61));
testCase('Percentage', '5633 + 17%', () => assertMath('5633 + 17%', 6590.61));
testCase('Percentage', 'add 17% to 5633', () => assertMath('add 17% to 5633', 6590.61));
testCase('Percentage', '17% increase on 5633', () => assertMath('17% increase on 5633', 6590.61));
testCase('Percentage', '5633 - 17%', () => assertMath('5633 - 17%', 4675.39));
testCase('Percentage', '5633 less 17%', () => assertMath('5633 less 17%', 4675.39));
testCase('Percentage', 'reduce 5633 by 17%', () => assertMath('reduce 5633 by 17%', 4675.39));
testCase('Percentage', 'take 17 percent off 5633', () => assertMath('take 17 percent off 5633', 4675.39));
testCase('Percentage', '17% discount on 5633', () => assertMath('17% discount on 5633', 4675.39));
testCase('Percentage', '20% of 5000 + 350', () => assertMath('20% of 5000 + 350', 1350));
testCase('Percentage', '5000 - 10% + 250', () => assertMath('5000 - 10% + 250', 4750));
testCase('Percentage', '(1500 + 500) * 18%', () => assertMath('(1500 + 500) * 18%', 360));

// 4. Number & Currency Normalization
testCase('Currency & Suffixes', '₹1,50,000 + ₹25,000', () => assertMath('₹1,50,000 + ₹25,000', 175000));
testCase('Currency & Suffixes', 'Rs. 1,500 + Rs 3,500', () => assertMath('Rs. 1,500 + Rs 3,500', 5000));
testCase('Currency & Suffixes', 'INR 1500 * 4', () => assertMath('INR 1500 * 4', 6000));
testCase('Currency & Suffixes', '1.5k + 2.5k', () => assertMath('1.5k + 2.5k', 4000));
testCase('Currency & Suffixes', '1.5 lakh + 50 hazar', () => assertMath('1.5 lakh + 50 hazar', 200000));
testCase('Currency & Suffixes', '2.5 cr - 50 lakh', () => assertMath('2.5 cr - 50 lakh', 20000000));
testCase('Currency & Suffixes', 'dedh lakh + dhai hazar', () => assertMath('dedh lakh + dhai hazar', 152500));

// 5. Multilingual & Hinglish Support
testCase('Multilingual', '5000 ka 10 percent', () => assertMath('5000 ka 10 percent', 500));
testCase('Multilingual', '5000 mein 10 percent add karo', () => assertMath('5000 mein 10 percent add karo', 5500));
testCase('Multilingual', '5000 se 10 percent kam karo', () => assertMath('5000 se 10 percent kam karo', 4500));
testCase('Multilingual', '5000 madhun 10 percent kami', () => assertMath('5000 madhun 10 percent kami', 4500));
testCase('Multilingual', '5000 मध्ये 10% वाढ', () => assertMath('5000 मध्ये 10% वाढ', 5500));
testCase('Multilingual', '5000 ka aadha', () => assertMath('5000 ka aadha', 2500));
testCase('Multilingual', '10000 ko 4 se divide karo', () => assertMath('10000 ko 4 se divide karo', 2500));

// 6. Typo Tolerance
testCase('Typo Tolerance', '17 percentof 5633', () => assertMath('17 percentof 5633', 957.61));
testCase('Typo Tolerance', '17%of5633', () => assertMath('17%of5633', 957.61));
testCase('Typo Tolerance', 'multipy 250 by 12', () => assertMath('multipy 250 by 12', 3000));
testCase('Typo Tolerance', 'divde 10000 by 4', () => assertMath('divde 10000 by 4', 2500));
testCase('Typo Tolerance', 'calculte 15% of 8000', () => assertMath('calculte 15% of 8000', 1200));

// 7. Ambiguity Safety (Must NOT evaluate incomplete/ambiguous fragments as math)
testCase('Ambiguity Safety', '"10% less" must not evaluate as valid calculation', () => {
  const res = SafeArithmeticEngine.evaluate('10% less');
  assert.strictEqual(res, null, 'Ambiguous "10% less" should return null');
});
testCase('Ambiguity Safety', '"increase by 20" must not evaluate as valid calculation', () => {
  const res = SafeArithmeticEngine.evaluate('increase by 20');
  assert.strictEqual(res, null, 'Ambiguous "increase by 20" should return null');
});
testCase('Ambiguity Safety', '"margin 15" must not evaluate as arith math', () => {
  const res = SafeArithmeticEngine.evaluate('margin 15');
  assert.strictEqual(res, null, 'Ambiguous "margin 15" should return null');
});

console.log(`\n========================================`);
console.log(`🎯 Test Results: ${passed} / ${total} passed (${Math.round((passed/total)*100)}%)`);
console.log(`========================================\n`);

if (passed !== total) {
  process.exit(1);
}
