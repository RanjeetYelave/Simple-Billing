import { test, expect } from '../fixtures/base-fixture';

test.describe('PHASE 3: Multilingual & Transliteration Regression Suite', () => {

  test('P3-MULTI-01: Devanagari Numerals & Operators in Deterministic Math', async ({ page }) => {
    await page.goto('/index.html');
    await page.waitForLoadState('networkidle');

    const results = await page.evaluate(() => {
      const p = (window as any).OmnibarPipeline;
      return {
        devNumAdd: p.processQuery('१५०० + ३५००').data?.result,
        devNumMult: p.processQuery('२५ * ४').data?.result,
        marathiAdd: p.processQuery('१००० अधिक ५००').data?.result,
        marathiSub: p.processQuery('५००० वजा १०००').data?.result,
        marathiMult: p.processQuery('५० गुणिले २०').data?.result,
        marathiDiv: p.processQuery('१००० भागिले ४').data?.result,
      };
    });

    expect(results.devNumAdd).toBe(5000);
    expect(results.devNumMult).toBe(100);
    expect(results.marathiAdd).toBe(1500);
    expect(results.marathiSub).toBe(4000);
    expect(results.marathiMult).toBe(1000);
    expect(results.marathiDiv).toBe(250);
  });

  test('P3-MULTI-02: Devanagari & Vernacular Indian Unit Conversions', async ({ page }) => {
    await page.goto('/index.html');
    await page.waitForLoadState('networkidle');

    const results = await page.evaluate(() => {
      const p = (window as any).OmnibarPipeline;
      return {
        kgToG: p.processQuery('५ किलो to g').data?.result,
        mToFt: p.processQuery('१० meter to feet').data?.result,
        tonToKg: p.processQuery('२ टन to kg').data?.result,
      };
    });

    expect(results.kgToG).toBe(5000);
    expect(results.mToFt).toBe(32.808);
    expect(results.tonToKg).toBe(2000);
  });

  test('P3-MULTI-03: Devanagari, Marathi & Hindi Business Query Resolution', async ({ page }) => {
    await page.goto('/index.html');
    await page.waitForLoadState('networkidle');

    const results = await page.evaluate(() => {
      const p = (window as any).OmnibarPipeline;
      const ctx = {
        customers: [{ id: 101, name: 'Ramesh Patel', balance: 4500 }],
        staff: [{ id: 201, name: 'Suresh Kumar', monthlyBaseSalary: 25000 }]
      };

      return {
        expense: p.processQuery('चहा ५० खर्च', ctx),
        todaySales: p.processQuery('आजची विक्री', ctx),
        yesterdaySales: p.processQuery('कल का सेल', ctx),
        attendance: p.processQuery('सुरेश हजेरी', ctx),
      };
    });

    expect(results.expense.category).toBe('ACTION');
    expect(results.expense.capabilityId).toBe('ACT_CREATE_EXPENSE');

    expect(results.todaySales.category).toBe('QUICK_HELP');
    expect(results.todaySales.capabilityId).toBe('QH_SALES_TODAY');

    expect(results.yesterdaySales.category).toBe('QUICK_HELP');
    expect(results.yesterdaySales.capabilityId).toBe('QH_SALES_YESTERDAY');

    expect(results.attendance.category).toBe('ACTION');
    expect(results.attendance.capabilityId).toBe('ACT_MARK_ATTENDANCE');
  });

  test('P3-MULTI-04: Non-Regression Guarantee for Standard English Queries', async ({ page }) => {
    await page.goto('/index.html');
    await page.waitForLoadState('networkidle');

    const results = await page.evaluate(() => {
      const p = (window as any).OmnibarPipeline;
      return {
        engMath: p.processQuery('1500 + 3500').data?.result,
        engSales: p.processQuery('total sales today').capabilityId,
        engInvoice: p.processQuery('/inv').capabilityId,
      };
    });

    expect(results.engMath).toBe(5000);
    expect(results.engSales).toBe('QH_SALES_TODAY');
    expect(results.engInvoice).toBe('ACT_NAV_SLASH');
  });
});
