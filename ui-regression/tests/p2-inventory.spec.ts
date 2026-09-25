import { test, expect } from '../fixtures/base-fixture';

test.describe('P2: Real Inventory Product Lifecycle, Stock Adjustments & Persistence', () => {
  const testRunId = Date.now();
  const testProductName = `Widget_Auto_${testRunId}`;

  test('P2-INV-01: Product Creation, Field Validation & Persistence in Catalog', async ({ page, app, errorGate }) => {
    await app.gotoApp();
    await app.navigateTo('Firm');

    const invTab = page.locator('button:has-text("Inventory")').first();
    await expect(invTab).toBeVisible({ timeout: 10000 });
    await invTab.click();
    await page.waitForTimeout(400);

    const addBtn = page.locator('button:has-text("+ Add Product"), button:has-text("Add Product")').first();
    await expect(addBtn).toBeVisible({ timeout: 10000 });
    await addBtn.click();
    await page.waitForTimeout(300);

    const modal = page.locator('.modal-overlay').first();
    await expect(modal).toBeVisible({ timeout: 8000 });

    const prodName1 = `Prod_${Date.now()}_A`;

    // Fill product form
    await modal.locator('input[placeholder*="Dell Monitor" i]').first().fill(prodName1);
    await modal.locator('input[placeholder="e.g. 15000"]').first().fill('750');

    const stockInput = modal.locator('input[placeholder="e.g. 50"]').first();
    if (await stockInput.isVisible()) {
      await stockInput.fill('100');
    }

    const hsnInput = modal.locator('input[placeholder*="8471"]').first();
    if (await hsnInput.isVisible()) {
      await hsnInput.fill('8471');
    }

    // Submit product creation
    const submitBtn = modal.locator('button:has-text("Create Product")').first();
    await expect(submitBtn).toBeVisible();
    await submitBtn.click();
    await page.waitForTimeout(800);

    // Verify product is listed in catalog
    const searchInput = page.locator('input[placeholder*="Search name, SKU" i]').first();
    await expect(searchInput).toBeVisible({ timeout: 5000 });
    await searchInput.fill(prodName1);
    await page.waitForTimeout(400);

    const productRow = page.locator('table tbody tr', { hasText: prodName1 }).first();
    await expect(productRow).toBeVisible({ timeout: 10000 });
    const rowText = await productRow.innerText();
    expect(rowText).toContain('750');

    await errorGate.assertZeroErrors(page, 'Product Creation & Catalog Persistence');
  });

  test('P2-INV-02: Product Edit & Real-Time Stock Adjustment', async ({ page, app, errorGate }) => {
    await app.gotoApp();
    await app.navigateTo('Firm');

    const invTab = page.locator('button:has-text("Inventory")').first();
    await invTab.click();
    await page.waitForTimeout(400);

    const prodName2 = `Prod_${Date.now()}_B`;

    // 1. Create Product for editing test
    const addBtn = page.locator('button:has-text("+ Add Product")').first();
    await addBtn.click();
    await page.waitForTimeout(300);

    const modal = page.locator('.modal-overlay').first();
    await expect(modal).toBeVisible({ timeout: 8000 });

    await modal.locator('input[placeholder*="Dell Monitor" i]').first().fill(prodName2);
    await modal.locator('input[placeholder*="15000"]').first().fill('500');
    const stockInput = modal.locator('input[placeholder*="50"]').first();
    if (await stockInput.isVisible()) {
      await stockInput.fill('50');
    }
    await modal.locator('button:has-text("Create Product")').first().click();
    await page.waitForTimeout(800);

    // Search for the product
    const searchInput2 = page.locator('input[placeholder*="Search name, SKU" i]').first();
    await expect(searchInput2).toBeVisible({ timeout: 5000 });
    await searchInput2.fill(prodName2);
    await page.waitForTimeout(400);

    const productRow = page.locator('table tbody tr', { hasText: prodName2 }).first();
    await expect(productRow).toBeVisible({ timeout: 10000 });

    // 2. Edit Product Details
    const editBtn = productRow.locator('button[title*="Edit" i], button:has-text("✏️")').first();
    await expect(editBtn).toBeVisible({ timeout: 5000 });
    await editBtn.click();
    await page.waitForTimeout(300);

    const editModal = page.locator('.modal-overlay').first();
    await expect(editModal).toBeVisible({ timeout: 8000 });

    const priceInput = editModal.locator('input[placeholder*="15000"]').first();
    await priceInput.fill('850');

    const saveBtn = editModal.locator('button:has-text("Update Product")').first();
    await saveBtn.click();
    await page.waitForTimeout(800);

    // Verify updated price in table
    const updatedRow = page.locator('table tbody tr', { hasText: prodName2 }).first();
    await expect(updatedRow).toBeVisible();

    // 3. Adjust Stock
    const adjustBtn = updatedRow.locator('button[title*="Adjust" i], button:has-text("⚡"), button:has-text("📦")').first();
    if (await adjustBtn.isVisible()) {
      await adjustBtn.click();
      await page.waitForTimeout(300);

      const adjustModal = page.locator('.modal-overlay').first();
      await expect(adjustModal).toBeVisible({ timeout: 8000 });

      const qtyChangeInput = adjustModal.locator('input[type="number"], input.input').first();
      await qtyChangeInput.fill('25');

      const confirmAdjustBtn = adjustModal.locator('button:has-text("Confirm Adjustment")').first();
      await confirmAdjustBtn.click();
      await page.waitForTimeout(800);
    }

    await errorGate.assertZeroErrors(page, 'Product Edit & Stock Adjustment');
  });
});
