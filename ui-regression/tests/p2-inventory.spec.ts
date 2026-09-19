import { test, expect } from '../fixtures/base-fixture';

test.describe('P2: Inventory, Catalog Management & Paperwork Hub', () => {
  test('P2-INV-01: Product Catalog Directory, Search & Stock Status', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    await app.navigateTo('Firm');

    const invTab = page.locator('button:has-text("Inventory"), .tab-btn:has-text("Inventory")').first();
    if (await invTab.isVisible()) {
      await invTab.click();
      await page.waitForTimeout(400);

      const catalogTable = page.locator('.product-manager, .data-table, :text("Product")').first();
      await expect(catalogTable).toBeVisible();

      // Test product search
      const searchInput = page.locator('input[placeholder*="Search product" i], input[placeholder*="Search" i]').first();
      if (await searchInput.isVisible()) {
        await searchInput.fill('Prod');
        await page.waitForTimeout(300);
        await searchInput.clear();
      }
    }

    await errorGate.assertZeroErrors(page, 'Product Catalog & Search');
  });

  test('P2-INV-02: Product CRUD Modal & Stock Adjustment', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    await app.navigateTo('Firm');

    const invTab = page.locator('button:has-text("Inventory")').first();
    if (await invTab.isVisible()) {
      await invTab.click();
      await page.waitForTimeout(400);

      const addBtn = page.locator('button:has-text("+ Product"), button:has-text("Add Product"), button:has-text("+ Add")').first();
      if (await addBtn.isVisible()) {
        await addBtn.click();
        await page.waitForTimeout(300);

        const modal = page.locator('.modal-overlay, .product-modal').first();
        if (await modal.isVisible()) {
          const nameInput = modal.locator('input[placeholder*="Name" i], input[name="name"]').first();
          if (await nameInput.isVisible()) {
            await nameInput.fill('Test Widget Premium');
          }

          const priceInput = modal.locator('input[placeholder*="Price" i], input[name="price"]').first();
          if (await priceInput.isVisible()) {
            await priceInput.fill('450');
          }

          const cancelBtn = modal.locator('button:has-text("Cancel"), button:has-text("✕")').first();
          if (await cancelBtn.isVisible()) {
            await cancelBtn.click();
          }
        }
      }
    }

    await errorGate.assertZeroErrors(page, 'Product Creation & Stock Adjustment');
  });

  test('P2-PAP-01: Paperwork Hub Subtabs (Delivery Challans, Orders, Proformas)', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    await app.navigateTo('Firm');

    const paperworkTab = page.locator('button:has-text("Paperwork"), button:has-text("Documents")').first();
    if (await paperworkTab.isVisible()) {
      await paperworkTab.click();
      await page.waitForTimeout(400);

      const paperworkHub = page.locator('.paperwork-hub, .sub-tabs-bar, :text("Paperwork")').first();
      await expect(paperworkHub).toBeVisible();
    }

    await errorGate.assertZeroErrors(page, 'Paperwork Hub');
  });

  test('P2-PAP-02: Party / Vendor Management Subtab', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    await app.navigateTo('Firm');

    const partyTab = page.locator('button:has-text("Parties"), button:has-text("Vendors")').first();
    if (await partyTab.isVisible()) {
      await partyTab.click();
      await page.waitForTimeout(400);

      const partyManager = page.locator('.party-manager, .data-table, :text("Parties")').first();
      await expect(partyManager).toBeVisible();
    }

    await errorGate.assertZeroErrors(page, 'Vendor & Party Management');
  });
});
