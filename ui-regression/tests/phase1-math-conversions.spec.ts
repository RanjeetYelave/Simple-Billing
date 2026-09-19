import { test, expect } from '../fixtures/base-fixture';

test.describe('PHASE 1: Omnisearch Math, Conversions & Quantifiers Regression Suite', () => {

  test('P1-MATH-01: Standard Arithmetic AST & Chained Expressions in Omnibar Pipeline', async ({ page }) => {
    await page.goto('/index.html');
    await page.waitForLoadState('networkidle');

    const results = await page.evaluate(() => {
      const p = (window as any).OmnibarPipeline;
      return {
        add: p.processQuery('1500 + 3500').data?.result,
        chain: p.processQuery('100 + 200 * 3').data?.result,
        parens: p.processQuery('(500 + 200) / 2').data?.result,
        percentage: p.processQuery('17% of 5633').data?.result,
        addPercent: p.processQuery('12500 + 18%').data?.result,
        subPercent: p.processQuery('8500 - 15%').data?.result,
      };
    });

    expect(results.add).toBe(5000);
    expect(results.chain).toBe(700);
    expect(results.parens).toBe(350);
    expect(results.percentage).toBe(957.61);
    expect(results.addPercent).toBe(14750);
    expect(results.subPercent).toBe(7225);
  });

  test('P1-MATH-02: Indian Number Words, Colloquial Quantifiers & Compound Quantities', async ({ page }) => {
    await page.goto('/index.html');
    await page.waitForLoadState('networkidle');

    const results = await page.evaluate(() => {
      const p = (window as any).OmnibarPipeline;
      return {
        dedhLakh: p.processQuery('dedh lakh').data?.number,
        dhaiLakh: p.processQuery('dhai lakh').data?.number,
        sawaLakh: p.processQuery('sawa lakh').data?.number,
        pauneLakh: p.processQuery('paune lakh').data?.number,
        aadhaLakh: p.processQuery('aadha lakh').data?.number,
        sawaDoLakh: p.processQuery('sawa do lakh').data?.number,
        pauneDoLakh: p.processQuery('paune do lakh').data?.number,
        sawaTeenLakh: p.processQuery('sawa teen lakh').data?.number,
        dedhCrore: p.processQuery('dedh crore').data?.number,
        dhaiCrore: p.processQuery('dhai crore').data?.number,
        dedhHazar: p.processQuery('dedh hazar').data?.number,
        dhaiHazar: p.processQuery('dhai hazar').data?.number,
        compoundLakhThousand: p.processQuery('2 lakh 50 thousand + 50000').data?.result,
        compoundCroreLakh: p.processQuery('5 crore 20 lakh + 80 lakh').data?.result,
      };
    });

    expect(results.dedhLakh).toBe(150000);
    expect(results.dhaiLakh).toBe(250000);
    expect(results.sawaLakh).toBe(125000);
    expect(results.pauneLakh).toBe(75000);
    expect(results.aadhaLakh).toBe(50000);
    expect(results.sawaDoLakh).toBe(225000);
    expect(results.pauneDoLakh).toBe(175000);
    expect(results.sawaTeenLakh).toBe(325000);
    expect(results.dedhCrore).toBe(15000000);
    expect(results.dhaiCrore).toBe(25000000);
    expect(results.dedhHazar).toBe(1500);
    expect(results.dhaiHazar).toBe(2500);
    expect(results.compoundLakhThousand).toBe(300000);
    expect(results.compoundCroreLakh).toBe(60000000);
  });

  test('P1-MATH-03: Affine Temperature Conversions (°C ↔ °F ↔ K)', async ({ page }) => {
    await page.goto('/index.html');
    await page.waitForLoadState('networkidle');

    const results = await page.evaluate(() => {
      const p = (window as any).OmnibarPipeline;
      return {
        cToF: p.processQuery('100 c to f').data?.result,
        fToC: p.processQuery('32 f to c').data?.result,
        cToK: p.processQuery('0 c to k').data?.result,
        fToK: p.processQuery('212 f to k').data?.result,
        gluedCToF: p.processQuery('100c to f').data?.result,
      };
    });

    expect(results.cToF).toBe(212);
    expect(results.fToC).toBe(0);
    expect(results.cToK).toBe(273.15);
    expect(results.fToK).toBe(373.15);
    expect(results.gluedCToF).toBe(212);
  });

  test('P1-MATH-04: Speed, Storage & Time Dimensional Unit Conversions', async ({ page }) => {
    await page.goto('/index.html');
    await page.waitForLoadState('networkidle');

    const results = await page.evaluate(() => {
      const p = (window as any).OmnibarPipeline;
      return {
        mphToKmh: p.processQuery('60 mph to kmh').data?.result,
        kmhToMps: p.processQuery('36 kmh to mps').data?.result,
        mbToGb: p.processQuery('1024 mb to gb').data?.result,
        gbToTb: p.processQuery('2048 gb to tb').data?.result,
        hrsToDays: p.processQuery('48 hrs to days').data?.result,
        daysToWeeks: p.processQuery('14 days to weeks').data?.result,
        minToHours: p.processQuery('120 min to hours').data?.result,
      };
    });

    expect(results.mphToKmh).toBe(96.561);
    expect(results.kmhToMps).toBe(10);
    expect(results.mbToGb).toBe(1);
    expect(results.gbToTb).toBe(2);
    expect(results.hrsToDays).toBe(2);
    expect(results.daysToWeeks).toBe(2);
    expect(results.minToHours).toBe(2);
  });

  test('P1-MATH-05: Length, Area, Weight, Volume & Indian Trade Units', async ({ page }) => {
    await page.goto('/index.html');
    await page.waitForLoadState('networkidle');

    const results = await page.evaluate(() => {
      const p = (window as any).OmnibarPipeline;
      return {
        kmToMiles: p.processQuery('10 km to miles').data?.result,
        ftToM: p.processQuery('100 ft to m').data?.result,
        acreToSqft: p.processQuery('1 acre to sqft').data?.result,
        gunthaToSqft: p.processQuery('1 guntha to sqft').data?.result,
        bighaToSqft: p.processQuery('1 bigha to sqft').data?.result,
        quintalToKg: p.processQuery('1 quintal to kg').data?.result,
        tonneToKg: p.processQuery('1 tonne to kg').data?.result,
        tolaToGram: p.processQuery('1 tola to gram').data?.result,
        gallonToLiters: p.processQuery('1 gallon to liters').data?.result,
      };
    });

    expect(results.kmToMiles).toBe(6.214);
    expect(results.ftToM).toBe(30.48);
    expect(results.acreToSqft).toBe(43560);
    expect(results.gunthaToSqft).toBe(1089);
    expect(results.bighaToSqft).toBe(27225);
    expect(results.quintalToKg).toBe(100);
    expect(results.tonneToKg).toBe(1000);
    expect(results.tolaToGram).toBe(11.664);
    expect(results.gallonToLiters).toBe(3.785);
  });

  test('P1-MATH-06: Financial Calculations — Reverse GST, Forward GST, Discounts, Markup & Margin', async ({ page }) => {
    await page.goto('/index.html');
    await page.waitForLoadState('networkidle');

    const results = await page.evaluate(() => {
      const p = (window as any).OmnibarPipeline;
      return {
        reverseGst: p.processQuery('14750 incl 18% gst').data,
        forwardGst: p.processQuery('12500 + 18% gst').data,
        pctDiscount: p.processQuery('15% discount on 8500').data,
        flatDiscount: p.processQuery('discount 500 on 5000').data,
        markup: p.processQuery('20% markup on 500').data,
        margin: p.processQuery('margin 20% on 1000').data,
      };
    });

    // Reverse GST
    expect(results.reverseGst.base).toBe(12500);
    expect(results.reverseGst.tax).toBe(2250);
    expect(results.reverseGst.total).toBe(14750);

    // Forward GST
    expect(results.forwardGst.base).toBe(12500);
    expect(results.forwardGst.tax).toBe(2250);
    expect(results.forwardGst.total).toBe(14750);

    // Percentage Discount
    expect(results.pctDiscount.discount).toBe(1275);
    expect(results.pctDiscount.finalAmount).toBe(7225);

    // Flat Discount
    expect(results.flatDiscount.discount).toBe(500);
    expect(results.flatDiscount.finalAmount).toBe(4500);

    // Markup
    expect(results.markup.cost).toBe(500);
    expect(results.markup.markup).toBe(100);
    expect(results.markup.sellingPrice).toBe(600);

    // Margin
    expect(results.margin.selling).toBe(1000);
    expect(results.margin.profit).toBe(200);
    expect(results.margin.cost).toBe(800);
  });
});
