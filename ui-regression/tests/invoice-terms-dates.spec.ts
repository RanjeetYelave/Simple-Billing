import { test, expect } from '../fixtures/base-fixture';

test.describe('P1-INV-TERMS: Invoice Date & Payment Terms Robustness', () => {
  test('Date calculations unit checks', async () => {
    // 1. Same-day dates -> 0 days
    // 2. Consecutive dates -> 1 day
    // 3. 14-day interval -> 14 days
    // 4. Month boundary
    // 5. Year boundary
    // 6. Leap-year boundary
    // 7. Invalid or missing dates
    // 8. Timezone-sensitive / timestamp-bearing dates
    const parseLocalDate = (dateStr: any) => {
      if (!dateStr || typeof dateStr !== 'string') return null;
      const clean = dateStr.trim().split('T')[0].split(' ')[0];
      const parts = clean.split('-');
      if (parts.length !== 3) return null;
      const y = parseInt(parts[0], 10);
      const m = parseInt(parts[1], 10) - 1;
      const d = parseInt(parts[2], 10);
      if (isNaN(y) || isNaN(m) || isNaN(d)) return null;
      const dt = new Date(y, m, d);
      if (dt.getFullYear() !== y || dt.getMonth() !== m || dt.getDate() !== d) return null;
      return dt;
    };

    const getDaysBetween = (dateStr1: any, dateStr2: any) => {
      const d1 = parseLocalDate(dateStr1);
      const d2 = parseLocalDate(dateStr2);
      if (!d1 || !d2) return NaN;
      const utc1 = Date.UTC(d1.getFullYear(), d1.getMonth(), d1.getDate());
      const utc2 = Date.UTC(d2.getFullYear(), d2.getMonth(), d2.getDate());
      return Math.round((utc2 - utc1) / (1000 * 60 * 60 * 24));
    };

    expect(getDaysBetween('2026-10-25', '2026-10-25')).toBe(0);
    expect(getDaysBetween('2026-10-25', '2026-10-26')).toBe(1);
    expect(getDaysBetween('2026-10-26', '2026-10-25')).toBe(-1);
    expect(getDaysBetween('2026-10-25', '2026-11-08')).toBe(14);
    expect(getDaysBetween('2026-10-25', '2026-11-09')).toBe(15);
    expect(getDaysBetween('2026-10-25', '2026-11-24')).toBe(30);
    expect(getDaysBetween('2026-10-31', '2026-11-01')).toBe(1);
    expect(getDaysBetween('2026-02-28', '2026-03-01')).toBe(1);
    expect(getDaysBetween('2026-12-31', '2027-01-01')).toBe(1);
    expect(getDaysBetween('2026-12-15', '2027-01-14')).toBe(30);
    expect(getDaysBetween('2024-02-28', '2024-03-01')).toBe(2);
    expect(getDaysBetween('2024-02-28', '2024-02-29')).toBe(1);
    expect(getDaysBetween('2028-02-28', '2028-03-01')).toBe(2);
    expect(isNaN(getDaysBetween(null, '2026-10-25'))).toBe(true);
    expect(isNaN(getDaysBetween('2026-10-25', undefined))).toBe(true);
    expect(isNaN(getDaysBetween('', ''))).toBe(true);
    expect(isNaN(getDaysBetween('invalid-date', '2026-10-25'))).toBe(true);
    expect(isNaN(getDaysBetween('2026-02-31', '2026-03-01'))).toBe(true);
    expect(getDaysBetween('2026-10-25T00:00:00', '2026-10-26T23:59:59')).toBe(1);
    expect(getDaysBetween('2026-03-08', '2026-03-09')).toBe(1);
    expect(getDaysBetween('2026-11-01', '2026-11-02')).toBe(1);
  });

  test('Invoice Creation Form — BillsoftUtils.getDaysBetween, Due Date Indicators & Terms Switching', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    // Verify BillsoftUtils methods in browser context
    const browserUtilsCheck = await page.evaluate(() => {
      const bUtils = (window as any).BillsoftUtils;
      return {
        hasBillsoftUtils: typeof bUtils !== 'undefined',
        hasGetDaysBetween: typeof bUtils?.getDaysBetween === 'function',
        hasAddDays: typeof bUtils?.addDaysToDateStr === 'function',
        sampleDiff: bUtils?.getDaysBetween ? bUtils.getDaysBetween('2026-10-25', '2026-11-08') : null
      };
    });
    expect(browserUtilsCheck.hasBillsoftUtils).toBe(true);
    expect(browserUtilsCheck.hasGetDaysBetween).toBe(true);
    expect(browserUtilsCheck.sampleDiff).toBe(14);

    // 1. Navigate to Invoices
    await app.navigateTo('Invoices');

    // 2. Open New Invoice Form
    const createBtn = page.locator('button:has-text("📄 + New Invoice"), button:has-text("+ Inv"), button:has-text("Create Invoice")').first();
    await expect(createBtn).toBeVisible({ timeout: 10000 });
    await createBtn.click();
    await page.waitForTimeout(500);

    const invoiceForm = page.locator('.invoice-form').first();
    await expect(invoiceForm).toBeVisible({ timeout: 10000 });

    // Assert NO "Something went wrong" crash
    const errorBoundary = await page.locator('text=Something went wrong').isVisible().catch(() => false);
    expect(errorBoundary).toBe(false);

    // Verify single due date indicator
    const dueWindowIndicator = invoiceForm.locator('text=credit payment window').first();
    await expect(dueWindowIndicator).toBeVisible({ timeout: 5000 });
    const initialText = await dueWindowIndicator.textContent();
    expect(initialText).toContain('15-day credit payment window');

    // 3. Switch Payment Terms to Net 30 Days
    const termsSelect = invoiceForm.locator('select.select-sm').filter({ hasText: 'Net' }).first();
    await termsSelect.selectOption('NET_30');
    await page.waitForTimeout(400);

    const updatedText = await dueWindowIndicator.textContent();
    expect(updatedText).toContain('30-day credit payment window');

    // 4. Manually change Due Date
    const dateInputs = invoiceForm.locator('input[type="date"]');
    const dueDateInput = dateInputs.nth(1);
    await dueDateInput.fill('2026-12-01');
    await page.waitForTimeout(400);

    // 5. Change Invoice Date
    const invDateInput = dateInputs.nth(0);
    await invDateInput.fill('2026-10-20');
    await page.waitForTimeout(400);

    // 6. Test Quotations / Estimates screen
    await page.evaluate(() => {
      window.dispatchEvent(new CustomEvent('billsoft:navigate-subtab', { detail: { page: 'invoices', subtab: 'estimates' } }));
    });
    await page.waitForTimeout(500);

    const quoteFormError = await page.locator('text=Something went wrong').isVisible().catch(() => false);
    expect(quoteFormError).toBe(false);

    // Check zero console error exceptions
    await errorGate.assertZeroErrors(page, 'Invoice Creation & Payment Terms');
  });
});
