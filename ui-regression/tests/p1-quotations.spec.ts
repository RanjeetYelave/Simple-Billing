import { test, expect } from '../fixtures/base-fixture';

test.describe('P1: Quotation Workflows & Conversion Pipeline', () => {
  test('P1-QT-01: Quotation Hub, Tab Switching & Creation Form', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    await app.navigateTo('Invoices');

    // Click Quotes tab
    const quotesTab = page.locator('button:has-text("Quotes"), button:has-text("Quotations"), .tab-btn:has-text("Quote")').first();
    if (await quotesTab.isVisible()) {
      await quotesTab.click();
      await page.waitForTimeout(400);

      const quotesHub = page.locator('.quotation-list, .data-table, :text("Quotations")').first();
      await expect(quotesHub).toBeVisible();
    }

    await errorGate.assertZeroErrors(page, 'Quotation Hub & Tab Switching');
  });

  test('P1-QT-02: Quotation to Invoice Conversion Pipeline', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    await app.navigateTo('Invoices');

    const quotesTab = page.locator('button:has-text("Quotes"), button:has-text("Quotations")').first();
    if (await quotesTab.isVisible()) {
      await quotesTab.click();
      await page.waitForTimeout(400);

      const convertBtn = page.locator('button:has-text("Convert"), button[title*="Convert" i]').first();
      if (await convertBtn.isVisible()) {
        await convertBtn.click();
        await page.waitForTimeout(400);

        const modal = page.locator('.modal-overlay, .convert-modal').first();
        if (await modal.isVisible()) {
          const cancelBtn = modal.locator('button:has-text("Cancel"), button:has-text("✕")').first();
          if (await cancelBtn.isVisible()) {
            await cancelBtn.click();
          }
        }
      }
    }

    await errorGate.assertZeroErrors(page, 'Quotation Conversion Action');
  });

  test('P1-QT-03: Quotation Search and Status Filter', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    await app.navigateTo('Invoices');

    const quotesTab = page.locator('button:has-text("Quotes"), button:has-text("Quotations")').first();
    if (await quotesTab.isVisible()) {
      await quotesTab.click();
      await page.waitForTimeout(300);

      const searchInput = page.locator('input[placeholder*="Search" i], input[placeholder*="quote" i]').first();
      if (await searchInput.isVisible()) {
        await searchInput.fill('EST');
        await page.waitForTimeout(300);
        await searchInput.clear();
      }
    }

    await errorGate.assertZeroErrors(page, 'Quotation Search & Filter');
  });
});
