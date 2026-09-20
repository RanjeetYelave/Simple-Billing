import { test, expect } from '../fixtures/base-fixture';

test.describe('PHASE 5: Business Intelligence & Aggregations Regression Suite', () => {

  test('P5-BI-01: Top & Bottom N Rankings (Customers, Products, Debtors)', async ({ page }) => {
    await page.goto('/index.html');
    await page.waitForLoadState('networkidle');

    const results = await page.evaluate(() => {
      const p = (window as any).OmnibarPipeline;
      const ctx = {
        customers: [
          { id: 1, name: 'Aditi Sharma', balance: 15000 },
          { id: 2, name: 'Bharat Traders', balance: 45000 },
          { id: 3, name: 'Chirag Patil', balance: 5000 },
          { id: 4, name: 'Dinesh Gupta', balance: 0 },
          { id: 5, name: 'Esha Mehta', balance: 28000 }
        ],
        invoices: [
          { id: 101, customerName: 'Bharat Traders', netTotal: 120000, items: [{ name: 'Cement 50kg', qty: 50 }, { name: 'Steel Rods', qty: 20 }] },
          { id: 102, customerName: 'Aditi Sharma', netTotal: 45000, items: [{ name: 'Paint 10L', qty: 10 }, { name: 'Cement 50kg', qty: 15 }] },
          { id: 103, customerName: 'Esha Mehta', netTotal: 85000, items: [{ name: 'Steel Rods', qty: 35 }] }
        ],
        products: [
          { id: 1, name: 'Cement 50kg' },
          { id: 2, name: 'Steel Rods' },
          { id: 3, name: 'Paint 10L' },
          { id: 4, name: 'Bricks 1000' }
        ]
      };

      const topCust = p.processQuery('top 2 customers by revenue', ctx);
      const topProd = p.processQuery('top 2 products', ctx);
      const bottomProd = p.processQuery('bottom 2 selling items', ctx);
      const topDebtors = p.processQuery('top 3 debtors', ctx);

      return {
        topCust,
        topProd,
        bottomProd,
        topDebtors
      };
    });

    // Top Customers
    expect(results.topCust.capabilityId).toBe('QH_TOP_CUSTOMERS');
    expect(results.topCust.data.topCustomers.length).toBe(2);
    expect(results.topCust.data.topCustomers[0][0]).toBe('Bharat Traders');

    // Top Products (Steel Rods = 55, Cement = 65)
    expect(results.topProd.capabilityId).toBe('QH_TOP_PRODUCTS');
    expect(results.topProd.data.topProducts.length).toBe(2);
    expect(results.topProd.data.topProducts[0][0]).toBe('Cement 50kg');

    // Bottom Products
    expect(results.bottomProd.capabilityId).toBe('QH_BOTTOM_PRODUCTS');
    expect(results.bottomProd.data.bottomProducts.length).toBe(2);

    // Top Debtors (Bharat: 45k, Esha: 28k, Aditi: 15k)
    expect(results.topDebtors.capabilityId).toBe('QH_TOP_DEBTORS');
    expect(results.topDebtors.data.debtors.length).toBe(3);
    expect(results.topDebtors.data.debtors[0].name).toBe('Bharat Traders');
    expect(results.topDebtors.data.debtors[0].balance).toBe(45000);
  });

  test('P5-BI-02: Threshold / Range Filters (Invoices, Customers, Expenses)', async ({ page }) => {
    await page.goto('/index.html');
    await page.waitForLoadState('networkidle');

    const results = await page.evaluate(() => {
      const p = (window as any).OmnibarPipeline;
      const ctx = {
        invoices: [
          { id: 101, invoiceNumber: 'INV-101', grandTotal: 75000 },
          { id: 102, invoiceNumber: 'INV-102', grandTotal: 25000 },
          { id: 103, invoiceNumber: 'INV-103', grandTotal: 120000 },
          { id: 104, invoiceNumber: 'INV-104', grandTotal: 3000 }
        ],
        customers: [
          { id: 1, name: 'Aditi Sharma', balance: 15000 },
          { id: 2, name: 'Bharat Traders', balance: 45000 },
          { id: 3, name: 'Chirag Patil', balance: 4000 }
        ],
        expenses: [
          { id: 1, title: 'Office Rent', amount: 25000 },
          { id: 2, title: 'Chai & Snacks', amount: 350 },
          { id: 3, title: 'Electricity Bill', amount: 6500 }
        ]
      };

      const invAbove = p.processQuery('invoices over 50000', ctx);
      const invBelow = p.processQuery('invoices under 10000', ctx);
      const custOwing = p.processQuery('customers owing more than 10000', ctx);
      const expAbove = p.processQuery('expenses above 2000', ctx);

      return {
        invAbove,
        invBelow,
        custOwing,
        expAbove
      };
    });

    // Invoices above 50,000 (INV-101: 75k, INV-103: 120k -> 2 invoices, total 195k)
    expect(results.invAbove.capabilityId).toBe('QH_INVOICES_FILTER_ABOVE');
    expect(results.invAbove.data.count).toBe(2);
    expect(results.invAbove.data.totalAmount).toBe(195000);

    // Invoices under 10,000 (INV-104: 3k -> 1 invoice)
    expect(results.invBelow.capabilityId).toBe('QH_INVOICES_FILTER_BELOW');
    expect(results.invBelow.data.count).toBe(1);
    expect(results.invBelow.data.totalAmount).toBe(3000);

    // Customers owing > 10,000 (Aditi: 15k, Bharat: 45k -> 2 customers)
    expect(results.custOwing.capabilityId).toBe('QH_CUSTOMERS_FILTER_DUE');
    expect(results.custOwing.data.count).toBe(2);
    expect(results.custOwing.data.totalDue).toBe(60000);

    // Expenses above 2,000 (Rent: 25k, Electricity: 6.5k -> 2 expenses)
    expect(results.expAbove.capabilityId).toBe('QH_EXPENSES_FILTER_ABOVE');
    expect(results.expAbove.data.count).toBe(2);
    expect(results.expAbove.data.totalAmount).toBe(31500);
  });

  test('P5-BI-03: Period Comparisons & Growth Metrics (MoM & DoD)', async ({ page }) => {
    await page.goto('/index.html');
    await page.waitForLoadState('networkidle');

    const results = await page.evaluate(() => {
      const p = (window as any).OmnibarPipeline;
      const todayStr = new Date().toISOString().slice(0, 10);
      const yesterdayStr = new Date(Date.now() - 86400000).toISOString().slice(0, 10);
      const curMonth = todayStr.slice(0, 7);
      const nowD = new Date();
      const lastMonthD = new Date(nowD.getFullYear(), nowD.getMonth() - 1, 1);
      const lastMonthStr = `${lastMonthD.getFullYear()}-${String(lastMonthD.getMonth() + 1).padStart(2, '0')}`;

      const ctx = {
        invoices: [
          { id: 101, invoiceDate: `${curMonth}-05`, grandTotal: 50000 },
          { id: 102, invoiceDate: `${curMonth}-12`, grandTotal: 70000 },
          { id: 103, invoiceDate: `${lastMonthStr}-10`, grandTotal: 80000 },
          { id: 104, invoiceDate: todayStr, grandTotal: 15000 },
          { id: 105, invoiceDate: yesterdayStr, grandTotal: 10000 }
        ]
      };

      const momComp = p.processQuery('sales this month vs last month', ctx);
      const dodComp = p.processQuery('sales today vs yesterday', ctx);

      return {
        momComp,
        dodComp
      };
    });

    // Month-over-Month Comparison: This month = 145,000 (50k+70k+15k+10k), Last month = 80,000 -> Delta = +65,000 (+81.25%)
    expect(results.momComp.capabilityId).toBe('QH_SALES_COMPARISON');
    expect(results.momComp.data.thisMonth).toBe(145000);
    expect(results.momComp.data.lastMonth).toBe(80000);
    expect(results.momComp.data.delta).toBe(65000);
    expect(results.momComp.data.growthPct).toBe(81.25);

    // Day-over-Day Comparison: Today = 15,000, Yesterday = 10,000 -> Delta = +5,000 (+50%)
    expect(results.dodComp.capabilityId).toBe('QH_SALES_TODAY_VS_YESTERDAY');
    expect(results.dodComp.data.todaySales).toBe(15000);
    expect(results.dodComp.data.yesterdaySales).toBe(10000);
    expect(results.dodComp.data.delta).toBe(5000);
    expect(results.dodComp.data.growthPct).toBe(50);
  });

  test('P5-BI-04: Accounts Receivable Aging Buckets & Valuation', async ({ page }) => {
    await page.goto('/index.html');
    await page.waitForLoadState('networkidle');

    const results = await page.evaluate(() => {
      const p = (window as any).OmnibarPipeline;
      const nowMs = Date.now();
      const d10Ago = new Date(nowMs - 10 * 86400000).toISOString().slice(0, 10);
      const d45Ago = new Date(nowMs - 45 * 86400000).toISOString().slice(0, 10);
      const d75Ago = new Date(nowMs - 75 * 86400000).toISOString().slice(0, 10);
      const d120Ago = new Date(nowMs - 120 * 86400000).toISOString().slice(0, 10);

      const ctx = {
        invoices: [
          { id: 101, invoiceDate: d10Ago, balanceAmount: 10000, status: 'UNPAID' },
          { id: 102, invoiceDate: d45Ago, balanceAmount: 20000, status: 'UNPAID' },
          { id: 103, invoiceDate: d75Ago, balanceAmount: 30000, status: 'UNPAID' },
          { id: 104, invoiceDate: d120Ago, balanceAmount: 40000, status: 'UNPAID' }
        ],
        products: [
          { id: 1, name: 'Item A', stock: 2, minStockAlert: 5, price: 500 },
          { id: 2, name: 'Item B', stock: 100, minStockAlert: 10, price: 150 }
        ]
      };

      const aging = p.processQuery('aging summary', ctx);
      const lowStock = p.processQuery('low stock products', ctx);

      return {
        aging,
        lowStock
      };
    });

    // Aging Buckets: 0-30d: 10k, 31-60d: 20k, 61-90d: 30k, 90d+: 40k -> Total = 100k
    expect(results.aging.capabilityId).toBe('QH_AGING_SUMMARY');
    expect(results.aging.data.buckets['0-30']).toBe(10000);
    expect(results.aging.data.buckets['31-60']).toBe(20000);
    expect(results.aging.data.buckets['61-90']).toBe(30000);
    expect(results.aging.data.buckets['90+']).toBe(40000);
    expect(results.aging.data.totalDue).toBe(100000);

    // Low stock: Item A (stock 2 <= minStock 5)
    expect(results.lowStock.capabilityId).toBe('QH_LOW_STOCK');
    expect(results.lowStock.data.lowStockCount).toBe(1);
  });

});
