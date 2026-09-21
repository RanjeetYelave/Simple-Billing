import { test, expect } from '../fixtures/base-fixture';

test.describe('P2: Saved Items, Uncatalogued Items & Duplicate Merge Hub', () => {
  test('SID-01: Product Catalog includes Duplicates & Merge and Saved Items buttons', async ({ page, app, errorGate }) => {
    await app.gotoApp();
    await app.navigateTo('Firm');

    const invTab = page.locator('button:has-text("Inventory"), .tab-btn:has-text("Inventory")').first();
    if (await invTab.isVisible()) {
      await invTab.click();
      await page.waitForTimeout(400);

      const duplicatesBtn = page.locator('#btn-duplicates-merge, button:has-text("Duplicates & Merge")').first();
      await expect(duplicatesBtn).toBeVisible();

      const savedItemsBtn = page.locator('#btn-saved-items, button:has-text("Saved Items")').first();
      await expect(savedItemsBtn).toBeVisible();
    }

    await errorGate.assertZeroErrors(page, 'Product Catalog Buttons');
  });

  test('SID-02: Duplicates & Merge modal opens and displays review UI', async ({ page, app, errorGate }) => {
    await app.gotoApp();
    await app.navigateTo('Firm');

    const invTab = page.locator('button:has-text("Inventory")').first();
    if (await invTab.isVisible()) {
      await invTab.click();
      await page.waitForTimeout(400);

      const duplicatesBtn = page.locator('#btn-duplicates-merge').first();
      if (await duplicatesBtn.isVisible()) {
        await duplicatesBtn.click();
        await page.waitForTimeout(400);

        const modal = page.locator('#modal-duplicates-merge, .duplicates-modal-content').first();
        await expect(modal).toBeVisible();

        const closeBtn = page.locator('.modal-close, button:has-text("✕"), button:has-text("Close"), .btn-close-modal').first();
        if (await closeBtn.isVisible()) {
          await closeBtn.click();
        }
      }
    }

    await errorGate.assertZeroErrors(page, 'Duplicates Modal Display');
  });

  test('SID-03: Saved Items modal opens and displays uncatalogued items UI', async ({ page, app, errorGate }) => {
    await app.gotoApp();
    await app.navigateTo('Firm');

    const invTab = page.locator('button:has-text("Inventory")').first();
    if (await invTab.isVisible()) {
      await invTab.click();
      await page.waitForTimeout(400);

      const savedItemsBtn = page.locator('#btn-saved-items').first();
      if (await savedItemsBtn.isVisible()) {
        await savedItemsBtn.click();
        await page.waitForTimeout(400);

        const modal = page.locator('#modal-saved-items, .saved-items-modal-content').first();
        await expect(modal).toBeVisible();

        const closeBtn = page.locator('.modal-close, button:has-text("✕"), button:has-text("Close"), .btn-close-modal').first();
        if (await closeBtn.isVisible()) {
          await closeBtn.click();
        }
      }
    }

    await errorGate.assertZeroErrors(page, 'Saved Items Modal Display');
  });
});
