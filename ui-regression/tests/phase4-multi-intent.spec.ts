import { test, expect } from '../fixtures/base-fixture';

test.describe('PHASE 4: Multi-Intent & Contextual Queries Regression Suite', () => {

  test('P4-MULTI-01: Compound Multi-Intent Parsing (Entity Lookup + Calculation, Action + Navigation)', async ({ page }) => {
    await page.goto('/index.html');
    await page.waitForLoadState('networkidle');

    const results = await page.evaluate(() => {
      const p = (window as any).OmnibarPipeline;
      const ctx = {
        customers: [{ id: 101, name: 'Ramesh Patel', balance: 4500 }],
        activeFirmId: 1
      };

      const compound1 = p.processQuery('Ramesh balance and 1500 + 500', ctx);
      const compound2 = p.processQuery('Add expense Chai 50 then /planner', ctx);
      const compound3 = p.processQuery('today sales aur 5000 - 10%', ctx);

      return {
        compound1,
        compound2,
        compound3
      };
    });

    // Compound 1: Lookup + Math
    expect(results.compound1.category).toBe('COMPOSITE');
    expect(results.compound1.segments.length).toBe(2);
    expect(results.compound1.segments[0].category).toBe('QUICK_HELP');
    expect(results.compound1.segments[1].category).toBe('SPECIAL');
    expect(results.compound1.segments[1].data.result).toBe(2000);

    // Compound 2: Expense Action + Slash Navigation
    expect(results.compound2.category).toBe('COMPOSITE');
    expect(results.compound2.segments[0].capabilityId).toBe('ACT_CREATE_EXPENSE');
    expect(results.compound2.segments[1].capabilityId).toBe('ACT_NAV_SLASH');

    // Compound 3: Today Sales + Discount Math (aur connector)
    expect(results.compound3.category).toBe('COMPOSITE');
    expect(results.compound3.segments[0].capabilityId).toBe('QH_SALES_TODAY');
    expect(results.compound3.segments[1].category).toBe('SPECIAL');
  });

  test('P4-MULTI-02: Session Context Memory for Last Entity & Pronoun Resolution (his/her/unka)', async ({ page }) => {
    await page.goto('/index.html');
    await page.waitForLoadState('networkidle');

    const results = await page.evaluate(() => {
      const p = (window as any).OmnibarPipeline;
      const ctx = {
        customers: [
          { id: 101, name: 'Ramesh Patel', balance: 4500, phone: '9876543210' },
          { id: 102, name: 'Suresh Shah', balance: 1200, phone: '9822334455' }
        ],
        activeFirmId: 1
      };

      // 1. Initial query targeting Ramesh
      const q1 = p.processQuery('Ramesh Patel', ctx);

      // 2. Follow-up query using pronoun "his balance"
      const q2 = p.processQuery('his balance', ctx);

      // 3. Follow-up query in Hindi "unka udhari"
      const q3 = p.processQuery('unka udhari', ctx);

      return {
        q1Target: q1.data?.customer?.name || q1.target?.customerName || q1.title,
        q2Customer: q2.data?.customerName || q2.data?.targetName || q2.title,
        q2Capability: q2.capabilityId,
        q3Customer: q3.data?.customerName || q3.data?.targetName || q3.title,
        q3Capability: q3.capabilityId
      };
    });

    expect(results.q2Capability).toBe('QH_CUSTOMER_DUE');
    expect(results.q2Customer).toContain('Ramesh');
    expect(results.q3Capability).toBe('QH_CUSTOMER_DUE');
    expect(results.q3Customer).toContain('Ramesh');
  });

  test('P4-MULTI-03: Calculation Continuity with "ans" / Prior Result Chaining', async ({ page }) => {
    await page.goto('/index.html');
    await page.waitForLoadState('networkidle');

    const results = await page.evaluate(() => {
      const p = (window as any).OmnibarPipeline;
      const ctx = { activeFirmId: 1 };

      // Step 1: Base calculation (1500 + 3500 = 5000)
      const r1 = p.processQuery('1500 + 3500', ctx);

      // Step 2: Follow-up math chaining with "ans"
      const r2 = p.processQuery('ans + 250', ctx);

      // Step 3: Follow-up GST on ans (18% gst on ans)
      const r3 = p.processQuery('18% gst on ans', ctx);

      return {
        r1Result: r1.data?.result,
        r2Result: r2.data?.result,
        r3Total: r3.data?.total
      };
    });

    expect(results.r1Result).toBe(5000);
    expect(results.r2Result).toBe(5250);
    expect(results.r3Total).toBe(6195); // 5250 + 18% = 6195
  });

  test('P4-MULTI-04: Strict Multi-Tenant Isolation for Session Context', async ({ page }) => {
    await page.goto('/index.html');
    await page.waitForLoadState('networkidle');

    const results = await page.evaluate(() => {
      const p = (window as any).OmnibarPipeline;
      
      // Firm 1 Context
      const ctxFirm1 = {
        customers: [{ id: 101, name: 'Firm1 Customer', balance: 5000 }],
        activeFirmId: 1
      };

      // Firm 2 Context (Different Firm)
      const ctxFirm2 = {
        customers: [{ id: 201, name: 'Firm2 Customer', balance: 9000 }],
        activeFirmId: 2
      };

      // Firm 1: Query customer & calculation
      p.processQuery('Firm1 Customer', ctxFirm1);
      p.processQuery('1000 + 2000', ctxFirm1); // ans = 3000

      // Firm 2: Check pronoun & ans (must NOT leak Firm 1 state)
      const firm2Pronoun = p.processQuery('his balance', ctxFirm2);
      const firm2Ans = p.processQuery('ans + 500', ctxFirm2);

      return {
        firm2CustomerMatched: firm2Pronoun.data?.customerName,
        firm2AnsResult: firm2Ans.data?.result
      };
    });

    // In Firm 2, there is no previous customer so customerName is undefined
    expect(results.firm2CustomerMatched).toBeUndefined();
    expect(results.firm2AnsResult).toBe(500); // 0 + 500 = 500
  });

});
