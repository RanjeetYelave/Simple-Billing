import { test, expect } from '../fixtures/base-fixture';

test.describe('PHASE 2: Date Math & Financial Extensions Regression Suite', () => {

  test('P2-DATE-01: Date Offsets, Relative Math & Days Between Dates', async ({ page }) => {
    await page.goto('/index.html');
    await page.waitForLoadState('networkidle');

    const results = await page.evaluate(() => {
      const p = (window as any).OmnibarPipeline;
      const ref = new Date(2026, 8, 19); // 19 Sep 2026

      return {
        todayPlus15: p.processQuery('today + 15 days').data,
        dueIn30: p.processQuery('due in 30 days').data,
        todayMinus7: p.processQuery('today - 7 days').data,
        daysBetweenJan: p.processQuery('days between 1 Jan and 15 Jan').data,
        daysBetweenIso: p.processQuery('difference between 2026-05-01 and 2026-05-20').data,
      };
    });

    expect(results.todayPlus15.type).toBe('DATE_CALC');
    expect(results.todayPlus15.days).toBe(15);
    expect(results.dueIn30.type).toBe('DATE_CALC');
    expect(results.dueIn30.days).toBe(30);
    expect(results.todayMinus7.type).toBe('DATE_CALC');
    expect(results.todayMinus7.days).toBe(7);
    expect(results.daysBetweenJan.type).toBe('DATE_DIFF');
    expect(results.daysBetweenJan.days).toBe(14);
    expect(results.daysBetweenIso.type).toBe('DATE_DIFF');
    expect(results.daysBetweenIso.days).toBe(19);
  });

  test('P2-DATE-02: Calendar & Fiscal Period Anchors (Month End, Quarter End, FY End)', async ({ page }) => {
    await page.goto('/index.html');
    await page.waitForLoadState('networkidle');

    const results = await page.evaluate(() => {
      const p = (window as any).OmnibarPipeline;
      return {
        endOfMonth: p.processQuery('end of month').data,
        endOfQuarter: p.processQuery('end of current quarter').data,
        q1End: p.processQuery('q1 end').data,
        fyEnd: p.processQuery('financial year end').data,
      };
    });

    expect(results.endOfMonth.type).toBe('DATE_ANCHOR');
    expect(results.endOfMonth.iso).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(results.endOfQuarter.type).toBe('DATE_ANCHOR');
    expect(results.q1End.type).toBe('DATE_ANCHOR');
    expect(results.q1End.iso).toMatch(/-03-31$/);
    expect(results.fyEnd.type).toBe('DATE_ANCHOR');
    expect(results.fyEnd.iso).toMatch(/-03-31$/);
  });

  test('P2-FIN-01: Simple Interest & Compound Interest Evaluations', async ({ page }) => {
    await page.goto('/index.html');
    await page.waitForLoadState('networkidle');

    const results = await page.evaluate(() => {
      const p = (window as any).OmnibarPipeline;
      return {
        si: p.processQuery('simple interest on 50000 at 8% for 3 years').data,
        siWords: p.processQuery('si on 1 lakh at 12% for 2 yrs').data,
        ci: p.processQuery('compound interest on 100000 at 10% for 5 years').data,
      };
    });

    // SI on 50,000 at 8% for 3 years: SI = 12,000, Total = 62,000
    expect(results.si.principal).toBe(50000);
    expect(results.si.rate).toBe(8);
    expect(results.si.interest).toBe(12000);
    expect(results.si.total).toBe(62000);

    // SI on 1,00,000 at 12% for 2 yrs: SI = 24,000, Total = 1,24,000
    expect(results.siWords.principal).toBe(100000);
    expect(results.siWords.rate).toBe(12);
    expect(results.siWords.interest).toBe(24000);
    expect(results.siWords.total).toBe(124000);

    // CI on 1,00,000 at 10% for 5 years: Total = 161051, CI = 61051
    expect(results.ci.principal).toBe(100000);
    expect(results.ci.rate).toBe(10);
    expect(results.ci.interest).toBe(61051);
    expect(results.ci.total).toBe(161051);
  });

  test('P2-FIN-02: SIP (Systematic Investment) & TDS Evaluations', async ({ page }) => {
    await page.goto('/index.html');
    await page.waitForLoadState('networkidle');

    const results = await page.evaluate(() => {
      const p = (window as any).OmnibarPipeline;
      return {
        sip: p.processQuery('sip 5000 per month for 5 years at 12%').data,
        tds10: p.processQuery('tds 10% on 50000').data,
        tds1: p.processQuery('tds 1% on 1 lakh').data,
      };
    });

    // SIP 5,000 / mo for 5 yrs at 12%: Total maturity = 412432, Invested = 300000, Gain = 112432
    expect(results.sip.monthlyDeposit).toBe(5000);
    expect(results.sip.invested).toBe(300000);
    expect(results.sip.totalValue).toBe(412432);
    expect(results.sip.wealthGain).toBe(112432);

    // TDS 10% on 50,000: TDS = 5,000, Net = 45,000
    expect(results.tds10.grossAmount).toBe(50000);
    expect(results.tds10.rate).toBe(10);
    expect(results.tds10.tdsAmount).toBe(5000);
    expect(results.tds10.netPayable).toBe(45000);

    // TDS 1% on 1,00,000: TDS = 1,000, Net = 99,000
    expect(results.tds1.grossAmount).toBe(100000);
    expect(results.tds1.rate).toBe(1);
    expect(results.tds1.tdsAmount).toBe(1000);
    expect(results.tds1.netPayable).toBe(99000);
  });

  test('P2-CURR-01: Static Reference Currency Conversions with Disclaimers', async ({ page }) => {
    await page.goto('/index.html');
    await page.waitForLoadState('networkidle');

    const results = await page.evaluate(() => {
      const p = (window as any).OmnibarPipeline;
      return {
        usdToInr: p.processQuery('100 usd to inr').data,
        inrToUsd: p.processQuery('1000 inr to usd').data,
        eurToInr: p.processQuery('50 eur to inr').data,
        aedToInr: p.processQuery('100 aed to inr').data,
      };
    });

    expect(results.usdToInr.result).toBe(8650);
    expect(results.usdToInr.isStaticRate).toBe(true);
    expect(results.usdToInr.disclaimer).toContain('Static reference rate');

    expect(results.inrToUsd.result).toBe(11.56);
    expect(results.inrToUsd.isStaticRate).toBe(true);

    expect(results.eurToInr.result).toBe(4560);
    expect(results.aedToInr.result).toBe(2355);
  });
});
