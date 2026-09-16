/**
 * test_comprehensive_quick_help.js
 * Exhaustive Automated Test Matrix for OmniSearch Quick Help Authoritative Data Retrieval
 */

const assert = require('assert');
const pipeline = require('../js/omnibarPipeline.js');

const {
  DeterministicNormalizer,
  RoleResolver,
  CapabilityClassifier,
  QuickHelpAdapter,
  OmnibarPipeline,
  processQuery
} = pipeline;

console.log('🧪 Starting Comprehensive Quick Help Automated Test Matrix...\n');

let totalTests = 0;
let passedTests = 0;

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

// Rich Mock CRM Context for Offline Verification
const mockCtx = {
  firm: {
    firmName: 'Shree Ganesh Traders',
    gstin: '27AABCU9603R1ZM',
    address: '123 Market Yard, Pune, Maharashtra 411037',
    bankName: 'HDFC Bank',
    accountNumber: '50200012345678',
    ifscCode: 'HDFC0001234',
    branchName: 'Market Yard Pune'
  },
  customers: [
    { id: 1, name: 'Rahul Patil', phone: '9822011223', city: 'Pune', balance: 4500, gstin: '27ABCDE1234F1Z5' },
    { id: 2, name: 'Gauri Shinde', phone: '9822044556', city: 'Satara', balance: 12000, gstin: '' },
    { id: 3, name: 'Amit Verma', phone: '9822077889', city: 'Kolhapur', balance: 0, gstin: '' }
  ],
  parties: [
    { id: 101, name: 'ABC Steel Traders', phone: '9422011111', contactPerson: 'Mr. Agarwal', netBalance: 85000 },
    { id: 102, name: 'Om Cement Suppliers', phone: '9422022222', contactPerson: 'Suresh Patil', netBalance: 24000 }
  ],
  products: [
    { id: 501, name: 'UltraTech Cement 50kg', stock: 25, minStockAlert: 10, price: 380, costPrice: 320, unit: 'bags' },
    { id: 502, name: 'TMT Steel Bar 12mm', stock: 4, minStockAlert: 10, price: 550, costPrice: 480, unit: 'pcs' },
    { id: 503, name: 'Berger Primer 20L', stock: 2, minStockAlert: 5, price: 2100, costPrice: 1750, unit: 'buckets' }
  ],
  staff: [
    { id: 301, name: 'Sachin Kamble', role: 'Sales Executive', designation: 'Senior Sales', monthlyBaseSalary: 22000, currentAdvanceBalance: 3000, phone: '9890011223' },
    { id: 302, name: 'Pooja Kulkarni', role: 'Accountant', designation: 'Lead Accountant', monthlyBaseSalary: 28000, currentAdvanceBalance: 0, phone: '9890044556' }
  ],
  invoices: [
    {
      id: 1001,
      invoiceNumber: 'INV-1001',
      customerName: 'Rahul Patil',
      customerId: 1,
      invoiceDate: new Date().toISOString().slice(0, 10),
      totalAmount: 15000,
      netTotal: 15000,
      totalTax: 2700,
      status: 'PAID',
      items: [{ name: 'UltraTech Cement 50kg', qty: 30, price: 380 }]
    },
    {
      id: 1002,
      invoiceNumber: 'INV-1002',
      customerName: 'Gauri Shinde',
      customerId: 2,
      invoiceDate: new Date().toISOString().slice(0, 10),
      totalAmount: 25000,
      netTotal: 25000,
      totalTax: 4500,
      status: 'UNPAID',
      balanceAmount: 12000,
      items: [{ name: 'TMT Steel Bar 12mm', qty: 40, price: 550 }]
    }
  ],
  expenses: [
    { id: 701, title: 'Shop Rent', amount: 15000, category: 'Rent', expenseDate: new Date().toISOString().slice(0, 10) },
    { id: 702, title: 'Staff Tea & Refreshments', amount: 350, category: 'Food', expenseDate: new Date().toISOString().slice(0, 10) }
  ]
};

// ─────────────────────────────────────────────────────────────
// 1. CUSTOMERS & RECEIVABLES DOMAIN
// ─────────────────────────────────────────────────────────────
console.log('1. Testing Customers & Receivables Domain:');

runTest('Customer specific dues lookup ("rahul udhari")', () => {
  const res = processQuery('rahul udhari', mockCtx);
  assert.strictEqual(res.category, 'QUICK_HELP');
  assert.strictEqual(res.capabilityId, 'QH_CUSTOMER_DUE');
  assert.strictEqual(res.domain, 'CUSTOMER');
  assert.strictEqual(res.data.customerName, 'Rahul Patil');
  assert.strictEqual(res.data.balance, 4500);
});

runTest('Customer balance query in Hindi ("rahul ka khata")', () => {
  const res = processQuery('rahul ka khata', mockCtx);
  assert.strictEqual(res.category, 'QUICK_HELP');
  assert.strictEqual(res.capabilityId, 'QH_CUSTOMER_DUE');
});

runTest('Total Udhari / Receivables Summary ("total udhari")', () => {
  const res = processQuery('total udhari', mockCtx);
  assert.strictEqual(res.category, 'QUICK_HELP');
  assert.strictEqual(res.capabilityId, 'QH_TOTAL_UDHARI');
  assert.strictEqual(res.data.totalUdhari, 16500);
  assert.strictEqual(res.data.customerCount, 2);
});

runTest('All customer dues in English ("who owes money")', () => {
  const res = processQuery('who owes money', mockCtx);
  assert.strictEqual(res.category, 'QUICK_HELP');
  assert.strictEqual(res.capabilityId, 'QH_TOTAL_UDHARI');
});

runTest('Customer Invoices Lookup ("invoices for rahul")', () => {
  const res = processQuery('invoices for rahul', mockCtx);
  assert.strictEqual(res.category, 'QUICK_HELP');
  assert.strictEqual(res.capabilityId, 'QH_CUSTOMER_INVS');
  assert.strictEqual(res.domain, 'INVOICE');
  assert.ok(res.data.invoices.length >= 1);
});

// ─────────────────────────────────────────────────────────────
// 2. VENDORS & PAYABLES DOMAIN
// ─────────────────────────────────────────────────────────────
console.log('\n2. Testing Vendors & Payables Domain:');

runTest('Vendor specific due lookup ("abc steel traders payable")', () => {
  const res = processQuery('abc steel traders payable', mockCtx);
  assert.strictEqual(res.category, 'QUICK_HELP');
  assert.strictEqual(res.capabilityId, 'QH_VENDOR_DUE');
  assert.strictEqual(res.domain, 'VENDOR');
  assert.strictEqual(res.data.vendorName, 'ABC Steel Traders');
  assert.strictEqual(res.data.balance, 85000);
});

runTest('Total vendor payables ("vendor dues")', () => {
  const res = processQuery('vendor dues', mockCtx);
  assert.strictEqual(res.category, 'QUICK_HELP');
  assert.strictEqual(res.capabilityId, 'QH_VENDOR_DUES_TOTAL');
  assert.strictEqual(res.data.totalPayable, 109000);
  assert.strictEqual(res.data.vendorCount, 2);
});

runTest('Total payables query in English ("who do we owe")', () => {
  const res = processQuery('who do we owe', mockCtx);
  assert.strictEqual(res.category, 'QUICK_HELP');
  assert.strictEqual(res.capabilityId, 'QH_VENDOR_DUES_TOTAL');
});

// ─────────────────────────────────────────────────────────────
// 3. INVOICES & DOCUMENTS DOMAIN
// ─────────────────────────────────────────────────────────────
console.log('\n3. Testing Invoices & Documents Domain:');

runTest('Specific Invoice by Number ("INV-1001")', () => {
  const res = processQuery('INV-1001', mockCtx);
  assert.strictEqual(res.category, 'QUICK_HELP');
  assert.strictEqual(res.capabilityId, 'QH_INVOICE_INFO');
  assert.strictEqual(res.domain, 'INVOICE');
  assert.ok(res.title.includes('INV-1001'));
});

runTest('Specific Invoice by Digits ("bill 1002")', () => {
  const res = processQuery('bill 1002', mockCtx);
  assert.strictEqual(res.category, 'QUICK_HELP');
  assert.strictEqual(res.capabilityId, 'QH_INVOICE_INFO');
  assert.strictEqual(res.domain, 'INVOICE');
  assert.ok(res.title.includes('INV-1002'));
});

runTest('Recent Invoices List ("recent invoices")', () => {
  const res = processQuery('recent invoices', mockCtx);
  assert.strictEqual(res.category, 'QUICK_HELP');
  assert.strictEqual(res.capabilityId, 'QH_INVOICE_LIST');
  assert.strictEqual(res.domain, 'INVOICE');
});

runTest('Overdue / Unpaid Invoices Count ("overdue invoices")', () => {
  const res = processQuery('overdue invoices', mockCtx);
  assert.strictEqual(res.category, 'QUICK_HELP');
  assert.strictEqual(res.capabilityId, 'QH_OVERDUE_COUNT');
  assert.strictEqual(res.domain, 'INVOICE');
});

runTest('Quotation / Estimate Lookup ("estimate 101")', () => {
  const res = processQuery('estimate 101', mockCtx);
  assert.strictEqual(res.category, 'QUICK_HELP');
  assert.strictEqual(res.capabilityId, 'QH_QUOTE_INFO');
});

runTest('Purchase Order Lookup ("po 50")', () => {
  const res = processQuery('po 50', mockCtx);
  assert.strictEqual(res.category, 'QUICK_HELP');
  assert.strictEqual(res.capabilityId, 'QH_PO_INFO');
});

// ─────────────────────────────────────────────────────────────
// 4. SALES & REVENUE DOMAIN
// ─────────────────────────────────────────────────────────────
console.log('\n4. Testing Sales & Revenue Domain:');

runTest('Today Sales ("today sales")', () => {
  const res = processQuery('today sales', mockCtx);
  assert.strictEqual(res.category, 'QUICK_HELP');
  assert.strictEqual(res.capabilityId, 'QH_SALES_TODAY');
  assert.strictEqual(res.data.totalRevenue, 40000);
});

runTest('Today Sales in Marathi ("aajchi bikri")', () => {
  const res = processQuery('aajchi bikri', mockCtx);
  assert.strictEqual(res.category, 'QUICK_HELP');
  assert.strictEqual(res.capabilityId, 'QH_SALES_TODAY');
});

runTest('Yesterday Sales ("yesterday sales")', () => {
  const res = processQuery('yesterday sales', mockCtx);
  assert.strictEqual(res.category, 'QUICK_HELP');
  assert.strictEqual(res.capabilityId, 'QH_SALES_YESTERDAY');
});

runTest('This Week Sales ("this week sales")', () => {
  const res = processQuery('this week sales', mockCtx);
  assert.strictEqual(res.category, 'QUICK_HELP');
  assert.strictEqual(res.capabilityId, 'QH_SALES_WEEK');
});

runTest('This Month Sales ("this month sales")', () => {
  const res = processQuery('this month sales', mockCtx);
  assert.strictEqual(res.category, 'QUICK_HELP');
  assert.strictEqual(res.capabilityId, 'QH_SALES_MONTH');
});

runTest('Total Sales / Revenue ("total sales")', () => {
  const res = processQuery('total sales', mockCtx);
  assert.strictEqual(res.category, 'QUICK_HELP');
  assert.strictEqual(res.capabilityId, 'QH_SALES_TOTAL');
  assert.strictEqual(res.data.totalRevenue, 40000);
});

runTest('Top Selling Products ("top products")', () => {
  const res = processQuery('top products', mockCtx);
  assert.strictEqual(res.category, 'QUICK_HELP');
  assert.strictEqual(res.capabilityId, 'QH_TOP_PRODUCTS');
});

runTest('Top Customers by Revenue ("top customers")', () => {
  const res = processQuery('top customers', mockCtx);
  assert.strictEqual(res.category, 'QUICK_HELP');
  assert.strictEqual(res.capabilityId, 'QH_TOP_CUSTOMERS');
});

// ─────────────────────────────────────────────────────────────
// 5. EXPENSES DOMAIN
// ─────────────────────────────────────────────────────────────
console.log('\n5. Testing Expenses Domain:');

runTest('Today Expenses ("today expenses")', () => {
  const res = processQuery('today expenses', mockCtx);
  assert.strictEqual(res.category, 'QUICK_HELP');
  assert.strictEqual(res.capabilityId, 'QH_EXPENSE_TODAY');
  assert.strictEqual(res.data.totalExpense, 15350);
});

runTest('This Month Expenses ("this month expenses")', () => {
  const res = processQuery('this month expenses', mockCtx);
  assert.strictEqual(res.category, 'QUICK_HELP');
  assert.strictEqual(res.capabilityId, 'QH_EXPENSE_MONTH');
});

runTest('Rent Expense Breakdown ("rent expense")', () => {
  const res = processQuery('rent expense', mockCtx);
  assert.strictEqual(res.category, 'QUICK_HELP');
  assert.strictEqual(res.capabilityId, 'QH_EXPENSE_CATEGORY');
  assert.strictEqual(res.data.category, 'Rent');
  assert.strictEqual(res.data.totalExpense, 15000);
});

// ─────────────────────────────────────────────────────────────
// 6. INVENTORY & PRODUCTS DOMAIN
// ─────────────────────────────────────────────────────────────
console.log('\n6. Testing Inventory & Products Domain:');

runTest('Low Stock Alert ("low stock")', () => {
  const res = processQuery('low stock', mockCtx);
  assert.strictEqual(res.category, 'QUICK_HELP');
  assert.strictEqual(res.capabilityId, 'QH_LOW_STOCK');
  assert.strictEqual(res.data.lowStockCount, 2);
});

runTest('Inventory Valuation ("inventory valuation")', () => {
  const res = processQuery('inventory valuation', mockCtx);
  assert.strictEqual(res.category, 'QUICK_HELP');
  assert.strictEqual(res.capabilityId, 'QH_INVENTORY_VALUATION');
  assert.strictEqual(res.domain, 'INVENTORY');
  assert.ok(res.data.totalValuation > 0);
});

runTest('Specific Product Stock Query ("stock of ultratech cement")', () => {
  const res = processQuery('stock of ultratech cement', mockCtx);
  assert.strictEqual(res.category, 'QUICK_HELP');
  assert.strictEqual(res.capabilityId, 'QH_PRODUCT_INFO');
  assert.strictEqual(res.domain, 'INVENTORY');
  assert.strictEqual(res.data.productName, 'UltraTech Cement 50kg');
  assert.strictEqual(res.data.stock, 25);
});

// ─────────────────────────────────────────────────────────────
// 7. HR & STAFF DOMAIN
// ─────────────────────────────────────────────────────────────
console.log('\n7. Testing HR & Staff Domain:');

runTest('Staff Attendance Summary ("today attendance")', () => {
  const res = processQuery('today attendance', mockCtx);
  assert.strictEqual(res.category, 'QUICK_HELP');
  assert.strictEqual(res.capabilityId, 'QH_HR_SUMMARY');
  assert.strictEqual(res.domain, 'HR');
  assert.strictEqual(res.data.employeeCount, 2);
});

runTest('Staff Attendance query in Hindi ("who is on leave")', () => {
  const res = processQuery('who is on leave', mockCtx);
  assert.strictEqual(res.category, 'QUICK_HELP');
  assert.strictEqual(res.capabilityId, 'QH_HR_SUMMARY');
});

runTest('Specific Employee Info ("salary of sachin")', () => {
  const res = processQuery('salary of sachin', mockCtx);
  assert.strictEqual(res.category, 'QUICK_HELP');
  assert.strictEqual(res.capabilityId, 'QH_EMPLOYEE_INFO');
  assert.strictEqual(res.domain, 'HR');
  assert.strictEqual(res.data.employeeName, 'Sachin Kamble');
  assert.strictEqual(res.data.salary, 22000);
  assert.strictEqual(res.data.advanceBalance, 3000);
});

// ─────────────────────────────────────────────────────────────
// 8. FIRM & MACRO BUSINESS SUMMARY
// ─────────────────────────────────────────────────────────────
console.log('\n8. Testing Firm & Macro Business Summary:');

runTest('Firm Bank Details ("bank details")', () => {
  const res = processQuery('bank details', mockCtx);
  assert.strictEqual(res.category, 'QUICK_HELP');
  assert.strictEqual(res.capabilityId, 'QH_FIRM_BANK');
  assert.strictEqual(res.data.bankName, 'HDFC Bank');
  assert.strictEqual(res.data.accountNumber, '50200012345678');
});

runTest('Firm GST Details ("gstin")', () => {
  const res = processQuery('gstin', mockCtx);
  assert.strictEqual(res.category, 'QUICK_HELP');
  assert.strictEqual(res.capabilityId, 'QH_FIRM_TAX');
  assert.strictEqual(res.data.gstin, '27AABCU9603R1ZM');
});

runTest('Macro Business Snapshot ("business summary")', () => {
  const res = processQuery('business summary', mockCtx);
  assert.strictEqual(res.category, 'QUICK_HELP');
  assert.strictEqual(res.capabilityId, 'QH_MACRO_SUMMARY');
  assert.strictEqual(res.domain, 'AGGREGATE');
  assert.strictEqual(res.data.totalSales, 40000);
  assert.strictEqual(res.data.totalReceivables, 16500);
  assert.strictEqual(res.data.totalPayables, 109000);
});

console.log(`\n========================================`);
console.log(`🎯 Comprehensive Quick Help Tests: ${passedTests} / ${totalTests} passed (${Math.round(passedTests / totalTests * 100)}%)`);
console.log(`========================================\n`);
