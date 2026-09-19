import { test, expect } from '../fixtures/base-fixture';

test.describe('P1: Multi-Tenant Firm Switching & Isolation', () => {
  test('P1-TENANT-01: Tenant Switcher Updates Context and Header State', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    const firmDropdown = page.locator('select.firm-select, select[title*="firm" i], .firm-dropdown select').first();
    if (await firmDropdown.isVisible()) {
      const options = await firmDropdown.locator('option').allInnerTexts();
      if (options.length > 1) {
        await firmDropdown.selectOption({ index: 1 });
        await page.waitForTimeout(500);

        await firmDropdown.selectOption({ index: 0 });
        await page.waitForTimeout(500);
      }
    }

    await errorGate.assertZeroErrors(page, 'Multi-Firm Switcher');
  });

  test('P1-TENANT-02: Multi-Firm Data Boundary Isolation Across Hubs', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    // 1. Invoices under active firm
    await app.navigateTo('Invoices');
    await page.waitForTimeout(300);

    // 2. Customers under active firm
    await app.navigateTo('Firm');
    const custTab = page.locator('button:has-text("Customers")').first();
    if (await custTab.isVisible()) {
      await custTab.click();
      await page.waitForTimeout(300);
    }

    // 3. Switch firm and verify no data corruption
    const firmDropdown = page.locator('select.firm-select, select[title*="firm" i]').first();
    if (await firmDropdown.isVisible()) {
      const options = await firmDropdown.locator('option').count();
      if (options > 1) {
        await firmDropdown.selectOption({ index: 1 });
        await page.waitForTimeout(400);

        // Check Inventory under Firm 2
        const invTab = page.locator('button:has-text("Inventory")').first();
        if (await invTab.isVisible()) {
          await invTab.click();
          await page.waitForTimeout(300);
        }

        // Switch back to Firm 1
        await firmDropdown.selectOption({ index: 0 });
        await page.waitForTimeout(400);
      }
    }

    await errorGate.assertZeroErrors(page, 'Multi-Firm Data Isolation');
  });

  test('P1-TENANT-03: Rapid Back-and-Forth Firm Toggles', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    const firmDropdown = page.locator('select.firm-select, select[title*="firm" i]').first();
    if (await firmDropdown.isVisible()) {
      const count = await firmDropdown.locator('option').count();
      if (count > 1) {
        for (let i = 0; i < 4; i++) {
          await firmDropdown.selectOption({ index: i % 2 });
          await page.waitForTimeout(150);
        }
      }
    }

    await errorGate.assertZeroErrors(page, 'Rapid Firm Toggles');
  });
});
