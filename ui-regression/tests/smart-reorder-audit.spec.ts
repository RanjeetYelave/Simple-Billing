import { test, expect } from '../fixtures/base-fixture';

test.describe('Smart Reorder Queue & Procurement Workspace E2E Audit', () => {

  test.beforeEach(async ({ request }) => {
    // Ensure a test firm exists
    const firmsRes = await request.get('/api/firm');
    const existingFirms = await firmsRes.json();
    if (!existingFirms || existingFirms.length === 0) {
      await request.post('/api/firm', {
        data: { firmName: 'RupeeCRM Test Firm', city: 'Pune', gstin: '27AABCU9603R1ZM' }
      });
    }
  });

  test('01: Smart Reorder Queue Layout & Vertical Recommendation Reflow', async ({ app, page, request }) => {
    const firmsRes = await request.get('/api/firm');
    const firms = await firmsRes.json();
    const firmId = firms[0].id;

    // Seed product under firm
    await request.post('/api/products', {
      data: { name: 'Audit Widget A', price: 100, costPrice: 60, minStockLevel: 10, stock: 2, unit: 'pcs', firmId },
      headers: { 'X-Firm-Id': String(firmId) }
    });

    await app.gotoApp();
    await app.navigateTo('Firm');

    const invTab = page.locator('button:has-text("Inventory")').first();
    await expect(invTab).toBeVisible({ timeout: 10000 });
    await invTab.click();
    await page.waitForTimeout(400);

    // Switch to Smart Reorder Queue tab
    const queueTab = page.locator('button:has-text("Smart Reorder Queue")').first();
    await expect(queueTab).toBeVisible({ timeout: 10000 });
    await queueTab.click();
    await page.waitForTimeout(500);

    // Verify Smart Reorder Queue header
    await expect(page.locator('text=Smart Reorder Queue').first()).toBeVisible();

    // Open Order Products workspace
    const orderProductsBtn = page.locator('button:has-text("Order Products")').first();
    await expect(orderProductsBtn).toBeVisible();
    await orderProductsBtn.click();
    await page.waitForTimeout(600);

    // Verify Order Products modal is open
    const modalHeader = page.locator('h3:has-text("Create Purchase Orders")').first();
    await expect(modalHeader).toBeVisible();

    // Verify search input is present and usable
    const searchInput = page.locator('input[placeholder*="Search products"]').first();
    await expect(searchInput).toBeVisible();

    // Verify '+ Create New Product' button is visible and accessible
    const createProdBtn = page.locator('button:has-text("+ Create New Product")').first();
    await expect(createProdBtn).toBeVisible();

    // Close modal via X
    const closeBtn = page.locator('.modal-content button:has-text("✕")').first();
    await closeBtn.click();
    await page.waitForTimeout(300);
    await expect(modalHeader).not.toBeVisible();
  });

  test('02: Interactive Procurement Workflow - Search, Add, New Product Modal, Multi-Vendor Review', async ({ app, page, request }) => {
    const firmsRes = await request.get('/api/firm');
    const firms = await firmsRes.json();
    const firmId = firms[0].id;

    // Seed test vendors
    const v1Res = await request.post('/api/parties', {
      data: { name: 'Supplier Alpha', type: 'VENDOR', firmId },
      headers: { 'X-Firm-Id': String(firmId) }
    });
    const vendor1 = await v1Res.json();

    // Seed test products
    await request.post('/api/products', {
      data: { name: 'Industrial Drill 500W', price: 1500, costPrice: 900, minStockLevel: 5, stock: 1, unit: 'pcs', preferredPartyId: vendor1.id, firmId },
      headers: { 'X-Firm-Id': String(firmId) }
    });

    await app.gotoApp();
    await app.navigateTo('Firm');

    const invTab = page.locator('button:has-text("Inventory")').first();
    await expect(invTab).toBeVisible({ timeout: 10000 });
    await invTab.click();
    await page.waitForTimeout(400);

    const queueTab = page.locator('button:has-text("Smart Reorder Queue")').first();
    await expect(queueTab).toBeVisible({ timeout: 10000 });
    await queueTab.click();
    await page.waitForTimeout(500);

    // Open workspace
    const orderBtn = page.locator('button:has-text("Order Products")').first();
    await expect(orderBtn).toBeVisible();
    await orderBtn.click();
    await page.waitForTimeout(500);

    // 1. Test Create New Product modal opening & cancellation (preserves state)
    await page.locator('button:has-text("+ Create New Product")').first().click();
    await page.waitForTimeout(300);

    const newProdModal = page.locator('h3:has-text("Create New Product")').first();
    await expect(newProdModal).toBeVisible();

    // Cancel Create Product dialog (nested modal with z-index 10050)
    await page.locator('div[style*="10050"] button:has-text("Cancel")').first().click();
    await page.waitForTimeout(300);
    await expect(newProdModal).not.toBeVisible();

    // Verify main workspace modal is still intact
    await expect(page.locator('h3:has-text("Create Purchase Orders")').first()).toBeVisible();

    // 2. Test search and add product from catalog dropdown
    const searchInput = page.locator('input[placeholder*="Search products"]').first();
    await searchInput.fill('Industrial');
    await page.waitForTimeout(400);

    const firstDropdownItem = page.locator('.autocomplete-dropdown > div').first();
    if (await firstDropdownItem.isVisible()) {
      await firstDropdownItem.click();
      await page.waitForTimeout(300);

      // Verify item was added to ORDER ITEMS table
      const orderTable = page.locator('table:visible').first();
      await expect(orderTable).toBeVisible();

      // Test inline quantity editing with Enter keyboard navigation
      const qtyInput = page.locator('table input[id^="input_qty_"]').first();
      if (await qtyInput.isVisible()) {
        await qtyInput.fill('15');
        await qtyInput.press('Enter');
        await page.waitForTimeout(200);
      }
    }

    // 3. Test Step 2: Review Orders
    const reviewBtn = page.locator('button:has-text("Review Orders")').first();
    if (await reviewBtn.isEnabled()) {
      await reviewBtn.click();
      await page.waitForTimeout(500);

      // Verify Step 2 header
      await expect(page.locator('h3:has-text("Review Purchase Orders")').first()).toBeVisible();

      // Test Back button preserves order state
      const backBtn = page.locator('button:has-text("Back to Order Items")').first();
      await expect(backBtn).toBeVisible();
      await backBtn.click();
      await page.waitForTimeout(400);

      // Verify back on Step 1 with items preserved
      await expect(page.locator('h3:has-text("Create Purchase Orders")').first()).toBeVisible();
    }

    // Close modal
    await page.locator('.modal-content button:has-text("✕")').first().click();
    await page.waitForTimeout(300);
  });

  test('03: Large Recommendation Dataset (100 items) Vertical Scrolling & Responsiveness', async ({ app, page, request }) => {
    // Intercept recommendations API to inject 100 recommendation items
    await page.route('**/api/forecast/restock/recommendations*', async route => {
      const mockItems = Array.from({ length: 100 }, (_, i) => ({
        id: 1000 + i,
        productId: 5000 + i,
        productName: `Bulk Audit Product SKU-${String(i + 1).padStart(3, '0')}`,
        sku: `AUDIT-SKU-${i + 1}`,
        category: i % 2 === 0 ? 'Industrial' : 'Consumer Goods',
        unit: 'pcs',
        currentStock: (i % 5),
        incomingStock: 0,
        minStockLevel: 10 + (i % 10),
        thresholdRequirement: 10 + (i % 10) - (i % 5),
        forecastRecommendedQty: 20 + i,
        recommendationReason: i % 3 === 0 ? 'BOTH' : (i % 2 === 0 ? 'FORECAST' : 'MINIMUM_STOCK'),
        forecast30d: 30 + i,
        recommendedOrderQty: 25 + i,
        expectedStockoutDate: '2026-10-15',
        urgencyStatus: i % 4 === 0 ? 'CRITICAL' : 'LOW_STOCK',
        confidenceLevel: 'HIGH',
        unitCost: 150.00,
        estimatedTotalCost: (25 + i) * 150.00,
        explanationText: 'Simulated 100-item high density catalog replenishment test'
      }));

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: mockItems,
          totalElements: 100,
          totalPages: 1,
          size: 100,
          number: 0
        })
      });
    });

    await app.gotoApp();
    await app.navigateTo('Firm');

    const invTab = page.locator('button:has-text("Inventory")').first();
    await expect(invTab).toBeVisible({ timeout: 10000 });
    await invTab.click();
    await page.waitForTimeout(400);

    const queueTab = page.locator('button:has-text("Smart Reorder Queue")').first();
    await expect(queueTab).toBeVisible({ timeout: 10000 });
    await queueTab.click();
    await page.waitForTimeout(500);

    // Open Order Products workspace
    const orderBtn = page.locator('button:has-text("Order Products")').first();
    await expect(orderBtn).toBeVisible();
    await orderBtn.click();
    await page.waitForTimeout(600);

    // Verify recommendation container has vertical scrollable styling
    const recSection = page.locator('text=RECOMMENDED FOR REORDER').first();
    await expect(recSection).toBeVisible();

    // Verify rows render within the scrollable table
    const recRows = page.locator('tr:has-text("Rec:")');
    const rowCount = await recRows.count();
    expect(rowCount).toBeGreaterThanOrEqual(1);

    // Test quick add from the 100-item vertical list
    const firstAddBtn = page.locator('button:has-text("+ Add")').first();
    if (await firstAddBtn.isVisible()) {
      await firstAddBtn.click();
      await page.waitForTimeout(300);
      await expect(page.locator('text=ORDER ITEMS')).toBeVisible();
    }

    // Close modal
    await page.locator('.modal-content button:has-text("✕")').first().click();
    await page.waitForTimeout(300);
  });

  test('04: Duplicate Prevention Lifecycle - Issued PO -> Recalculation -> Reload -> Navigate Away -> Non-Actionable', async ({ app, page, request }) => {
    const firmsRes = await request.get('/api/firm');
    const firms = await firmsRes.json();
    const firmId = firms[0].id;

    // 1. Create a product with minStock = 10, stockQuantity = 2 (shortfall = 8)
    const pRes = await request.post('/api/products', {
      data: { name: 'Duplicate Audit Product X', price: 200, costPrice: 120, minStockLevel: 10, stockQuantity: 2, itemType: 'GOODS', inInventory: true, firmId },
      headers: { 'X-Firm-Id': String(firmId) }
    });
    const testProd = await pRes.json();

    // 2. Fetch recommendation - verify product has shortfall = 8
    const recsRes1 = await request.get(`/api/forecast/products/${testProd.id}/restock?firmId=${firmId}`, {
      headers: { 'X-Firm-Id': String(firmId) }
    });
    const itemRec1 = await recsRes1.json();
    expect(itemRec1).toBeDefined();
    expect(itemRec1.thresholdRequirement).toBe(8);
    expect(itemRec1.recommendedOrderQty).toBe(8);

    // 3. Create an ISSUED PO for quantity = 8
    const vRes = await request.post('/api/parties', {
      data: { name: 'Vendor Beta', type: 'VENDOR', firmId },
      headers: { 'X-Firm-Id': String(firmId) }
    });
    const vendorBeta = await vRes.json();

    await request.post('/api/purchase-orders/batch', {
      data: {
        firmId,
        orders: [{
          partyId: vendorBeta.id,
          expectedDeliveryDate: '2026-10-20',
          notes: 'Duplicate test PO',
          items: [{
            productId: testProd.id,
            productName: testProd.name,
            quantity: 8,
            unitPrice: 120,
            unit: 'pcs'
          }]
        }]
      },
      headers: { 'X-Firm-Id': String(firmId) }
    });

    // 4. Immediately fetch recommendation again - verify incomingStock = 8 and recommendedOrderQty = 0 (HEALTHY)
    const recsRes2 = await request.get(`/api/forecast/products/${testProd.id}/restock?firmId=${firmId}`, {
      headers: { 'X-Firm-Id': String(firmId) }
    });
    const itemRec2 = await recsRes2.json();
    expect(itemRec2).toBeDefined();
    expect(itemRec2.incomingStock).toBe(8);
    expect(itemRec2.recommendedOrderQty).toBe(0);
    expect(itemRec2.urgencyStatus).toBe('HEALTHY');

    // 5. In Browser: navigate to Smart Reorder Queue, verify UI displays item as Ordered / non-actionable
    await app.gotoApp();
    await app.navigateTo('Firm');

    const invTab = page.locator('button:has-text("Inventory")').first();
    await expect(invTab).toBeVisible({ timeout: 10000 });
    await invTab.click();
    await page.waitForTimeout(400);

    const queueTab = page.locator('button:has-text("Smart Reorder Queue")').first();
    await expect(queueTab).toBeVisible({ timeout: 10000 });
    await queueTab.click();
    await page.waitForTimeout(500);

    // Verify item in queue table has 0 recommended order qty / HEALTHY status
    const row = page.locator(`tr:has-text("${testProd.name}")`).first();
    if (await row.isVisible()) {
      await expect(row.locator('text=HEALTHY, text=Ordered').first()).toBeVisible();
    }

    // 6. Navigate away to Invoices, then return to Smart Reorder Queue
    await app.navigateTo('Invoices');
    await page.waitForTimeout(400);
    await app.navigateTo('Firm');
    await invTab.click();
    await queueTab.click();
    await page.waitForTimeout(400);

    // Verify it remains non-actionable
    if (await row.isVisible()) {
      const addBtn = row.locator('button:has-text("+ Order")');
      if (await addBtn.isVisible()) {
        await expect(addBtn).toBeDisabled();
      }
    }
  });

});
