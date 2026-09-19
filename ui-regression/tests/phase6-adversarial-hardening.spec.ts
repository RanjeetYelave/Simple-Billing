import { test, expect } from '../fixtures/base-fixture';

test.describe('PHASE 6: Adversarial Hardening & Expanded Currency Suite', () => {

  test('P6-ADV-01: Adversarial Arithmetic, Zero-Division, Decimals, Chained Parens & Malformed Syntax', async ({ page }) => {
    await page.goto('/index.html');
    await page.waitForLoadState('networkidle');

    const results = await page.evaluate(() => {
      const p = (window as any).OmnibarPipeline;

      // 1. Division by zero
      const divZero = p.processQuery('100 / 0');
      // 2. Large multiplication
      const largeNum = p.processQuery('999999 * 88888');
      // 3. Precision decimals
      const precDec = p.processQuery('0.0001 + 0.0002');
      // 4. Complex nested parentheses
      const nestedParens = p.processQuery('((100 + 50) * (20 - 5)) / (2 + 3)');
      // 5. Unary signs
      const unaryMath = p.processQuery('-50 + 100');
      // 6. Indian colloquial math
      const indianMath1 = p.processQuery('dedh lakh + dhai hazar');
      const indianMath2 = p.processQuery('paune char lakh - sawa do lakh');
      // 7. Malformed / broken expressions (must not throw or crash)
      const malformed1 = p.processQuery('100 + * 50');
      const malformed2 = p.processQuery('((50 + 20');
      const malformed3 = p.processQuery('+++');
      const malformed4 = p.processQuery('');
      const malformed5 = p.processQuery('   ');

      return {
        divZero,
        largeNum,
        precDec,
        nestedParens,
        unaryMath,
        indianMath1,
        indianMath2,
        malformed1,
        malformed2,
        malformed3,
        malformed4,
        malformed5
      };
    });

    // Zero division handles gracefully
    expect(results.divZero).toBeDefined();

    // Large number
    expect(results.largeNum.data.result).toBe(88887911112);

    // Precision decimal (0.0001 + 0.0002 = 0.0003)
    expect(results.precDec.data.result).toBe(0.0003);

    // ((150) * (15)) / 5 = 2250 / 5 = 450
    expect(results.nestedParens.data.result).toBe(450);

    // -50 + 100 = 50
    expect(results.unaryMath.data.result).toBe(50);

    // 1.5 lakh (150,000) + 2.5 hazar (2,500) = 152,500
    expect(results.indianMath1.data.result).toBe(152500);

    // 3.75 lakh (375,000) - 2.25 lakh (225,000) = 150,000
    expect(results.indianMath2.data.result).toBe(150000);

    // Malformed expressions return graceful objects without throwing
    expect(results.malformed1).toBeDefined();
    expect(results.malformed2).toBeDefined();
    expect(results.malformed3).toBeDefined();
    expect(results.malformed4.status).toBe('NO_MATCH');
    expect(results.malformed5.status).toBe('NO_MATCH');
  });

  test('P6-CURR-01: Comprehensive World Currency Registry (30+ Currencies & Cross Pairs)', async ({ page }) => {
    await page.goto('/index.html');
    await page.waitForLoadState('networkidle');

    const results = await page.evaluate(() => {
      const p = (window as any).OmnibarPipeline;

      return {
        usdToInr: p.processQuery('100 USD to INR'),
        inrToUsd: p.processQuery('5000 rupees in USD'),
        eurToGbp: p.processQuery('100 EUR to GBP'),
        gbpToInr: p.processQuery('50 GBP in INR'),
        aedToInr: p.processQuery('100 AED to INR'),
        jpyToInr: p.processQuery('1000 JPY to INR'),
        sgdToMyr: p.processQuery('100 SGD in MYR'),
        sarToAed: p.processQuery('1000 SAR in AED'),
        kwdToUsd: p.processQuery('100 KWD in USD'),
        cadToAud: p.processQuery('100 CAD to AUD'),
        chfToEur: p.processQuery('100 CHF to EUR'),
        cnyToInr: p.processQuery('100 CNY to INR'),
        lakhInrToUsd: p.processQuery('1 lakh INR to USD'),
        symbolUsd: p.processQuery('$100 to INR'),
        symbolEur: p.processQuery('€50 to USD'),
        invalidCurr: p.processQuery('100 FOOBAR to BAZ')
      };
    });

    // 100 USD -> 100 * 86.50 = 8650 INR
    expect(results.usdToInr.capabilityId).toBe('SPEC_CONV_CURRENCY');
    expect(results.usdToInr.data.result).toBe(8650);
    expect(results.usdToInr.data.isStaticRate).toBe(true);
    expect(results.usdToInr.data.asOfDate).toBe('September 2026');

    // 5000 INR in USD -> 5000 / 86.50 = 57.80 USD
    expect(results.inrToUsd.capabilityId).toBe('SPEC_CONV_CURRENCY');
    expect(results.inrToUsd.data.result).toBe(57.8);

    // 100 EUR to GBP -> (100 * 91.20) / 109.80 = 83.06 GBP
    expect(results.eurToGbp.capabilityId).toBe('SPEC_CONV_CURRENCY');
    expect(results.eurToGbp.data.result).toBe(83.06);

    // 50 GBP in INR -> 50 * 109.80 = 5490 INR
    expect(results.gbpToInr.capabilityId).toBe('SPEC_CONV_CURRENCY');
    expect(results.gbpToInr.data.result).toBe(5490);

    // 100 AED to INR -> 100 * 23.55 = 2355 INR
    expect(results.aedToInr.capabilityId).toBe('SPEC_CONV_CURRENCY');
    expect(results.aedToInr.data.result).toBe(2355);

    // 1000 JPY to INR -> 1000 * 0.58 = 580 INR
    expect(results.jpyToInr.capabilityId).toBe('SPEC_CONV_CURRENCY');
    expect(results.jpyToInr.data.result).toBe(580);

    // 100 SGD in MYR -> (100 * 64.80) / 19.80 = 327.27 MYR
    expect(results.sgdToMyr.capabilityId).toBe('SPEC_CONV_CURRENCY');
    expect(results.sgdToMyr.data.result).toBe(327.27);

    // 1000 SAR in AED -> (1000 * 23.05) / 23.55 = 978.77 AED
    expect(results.sarToAed.capabilityId).toBe('SPEC_CONV_CURRENCY');
    expect(results.sarToAed.data.result).toBe(978.77);

    // 100 KWD in USD -> (100 * 281.50) / 86.50 = 325.43 USD
    expect(results.kwdToUsd.capabilityId).toBe('SPEC_CONV_CURRENCY');
    expect(results.kwdToUsd.data.result).toBe(325.43);

    // 100 CAD to AUD -> (100 * 61.20) / 55.40 = 110.47 AUD
    expect(results.cadToAud.capabilityId).toBe('SPEC_CONV_CURRENCY');
    expect(results.cadToAud.data.result).toBe(110.47);

    // 100 CHF to EUR -> (100 * 97.40) / 91.20 = 106.80 EUR
    expect(results.chfToEur.capabilityId).toBe('SPEC_CONV_CURRENCY');
    expect(results.chfToEur.data.result).toBe(106.8);

    // 100 CNY to INR -> 100 * 11.90 = 1190 INR
    expect(results.cnyToInr.capabilityId).toBe('SPEC_CONV_CURRENCY');
    expect(results.cnyToInr.data.result).toBe(1190);

    // 1 lakh INR to USD -> 100,000 / 86.50 = 1156.07 USD
    expect(results.lakhInrToUsd.capabilityId).toBe('SPEC_CONV_CURRENCY');
    expect(results.lakhInrToUsd.data.result).toBe(1156.07);

    // Symbol $100 to INR
    expect(results.symbolUsd.capabilityId).toBe('SPEC_CONV_CURRENCY');
    expect(results.symbolUsd.data.result).toBe(8650);

    // Symbol €50 to USD -> (50 * 91.20) / 86.50 = 52.72 USD
    expect(results.symbolEur.capabilityId).toBe('SPEC_CONV_CURRENCY');
    expect(results.symbolEur.data.result).toBe(52.72);

    // Invalid currency does not crash
    expect(results.invalidCurr.capabilityId).not.toBe('SPEC_CONV_CURRENCY');
  });

  test('P6-ADV-02: Adversarial Financial Edge Cases, Zero Rates, 100% Discounts & Reverse Calculations', async ({ page }) => {
    await page.goto('/index.html');
    await page.waitForLoadState('networkidle');

    const results = await page.evaluate(() => {
      const p = (window as any).OmnibarPipeline;

      return {
        zeroGst: p.processQuery('0% gst on 1000'),
        gstOnZero: p.processQuery('18% gst on 0'),
        fullDiscount: p.processQuery('100% discount on 5000'),
        reverseGstDec: p.processQuery('118.50 with 18% gst'),
        zeroSi: p.processQuery('SI on 0 at 8% for 2 years'),
        zeroTds: p.processQuery('TDS 10% on 0'),
        largeCi: p.processQuery('CI on 1 crore at 8% for 5 years'),
        marginZero: p.processQuery('selling 0 with 20% margin')
      };
    });

    // 0% GST on 1000 -> Tax = 0, Total = 1000
    expect(results.zeroGst.data.tax).toBe(0);
    expect(results.zeroGst.data.total).toBe(1000);

    // 18% GST on 0 -> Tax = 0, Total = 0
    expect(results.gstOnZero.data.tax).toBe(0);
    expect(results.gstOnZero.data.total).toBe(0);

    // 100% discount on 5000 -> Final = 0
    expect(results.fullDiscount.data.finalAmount).toBe(0);
    expect(results.fullDiscount.data.discount).toBe(5000);

    // Reverse GST on 118.50 with 18% GST: Base = 118.50 / 1.18 = 100.42, Tax = 18.08
    expect(results.reverseGstDec.data.base).toBe(100.42);
    expect(results.reverseGstDec.data.tax).toBe(18.08);

    // Zero SI -> Total = 0
    expect(results.zeroSi.data.interest).toBe(0);
    expect(results.zeroSi.data.total).toBe(0);

    // Zero TDS -> TDS = 0, Net = 0
    expect(results.zeroTds.data.tdsAmount).toBe(0);
    expect(results.zeroTds.data.netPayable).toBe(0);

    // CI on 1 crore (10,000,000) at 8% for 5 years -> 10,000,000 * (1.08)^5 = 14,693,280.77 ≈ 14,693,281
    expect(Math.round(results.largeCi.data.total)).toBe(14693281);
    expect(Math.round(results.largeCi.data.interest)).toBe(4693281);

    // Margin on 0 selling
    expect(results.marginZero.data.profit).toBe(0);
  });

  test('P6-ADV-03: Security Injections, Mixed Scripts, Extreme Text & Multi-Tenant Boundaries', async ({ page }) => {
    await page.goto('/index.html');
    await page.waitForLoadState('networkidle');

    const results = await page.evaluate(() => {
      const p = (window as any).OmnibarPipeline;

      // 1. Injections (XSS, SQL, Prototype Pollution)
      const xssQuery = p.processQuery('<script>alert("XSS")</script>');
      const sqlQuery = p.processQuery("' OR '1'='1' --");
      const protoQuery = p.processQuery('__proto__.polluted = true');

      // 2. Mixed Devanagari & Latin script arithmetic
      const mixedScriptMath = p.processQuery('२.५ लाख + 50000');

      // 3. Uppercase & mixed casing vernacular queries
      const upperVernacular = p.processQuery('AAJ KA SALE');

      // 4. Multi-Tenant Session Isolation
      p.setFirmContext(10, { ans: 5000, lastEntity: { name: 'Tenant Alpha Customer', type: 'CUSTOMER' } });
      p.setFirmContext(20, { ans: 99999, lastEntity: { name: 'Tenant Beta Customer', type: 'CUSTOMER' } });

      const ansTenant10 = p.processQuery('ans + 200', { activeFirmId: 10 });
      const ansTenant20 = p.processQuery('ans + 1', { activeFirmId: 20 });

      return {
        xssQuery,
        sqlQuery,
        protoQuery,
        mixedScriptMath,
        upperVernacular,
        ansTenant10,
        ansTenant20
      };
    });

    // Injections handled as normal search queries without execution or crash
    expect(results.xssQuery.status).toBeDefined();
    expect(results.sqlQuery.status).toBeDefined();
    expect(results.protoQuery.status).toBeDefined();

    // Mixed script: 2.5 lakh (250,000) + 50,000 = 300,000
    expect(results.mixedScriptMath.data.result).toBe(300000);

    // Uppercase vernacular
    expect(results.upperVernacular.capabilityId).toBe('QH_SALES_TODAY');

    // Multi-tenant session values remain strictly isolated
    expect(results.ansTenant10.data.result).toBe(5200);
    expect(results.ansTenant20.data.result).toBe(100000);
  });

});
