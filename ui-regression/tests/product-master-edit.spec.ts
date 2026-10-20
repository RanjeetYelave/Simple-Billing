import { test, expect } from '../fixtures/base-fixture';

test.describe('Product Master Inline & Bulk Edit Workspace E2E Tests', () => {

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

  test('01: Single product inline edit saves and updates row values in Product Master', async ({ app, page, request }) => {
    const firmsRes = await request.get('/api/firm');
    const firms = await firmsRes.json();
    const firmId = firms[0].id;
    const testProdName = 'Inline Edit ' + Date.now();

    // Seed product under firm
    await request.post('/api/products', {
      data: { name: testProdName, price: 100, costPrice: 60, minStockLevel: 5, stock: 10, unit: 'pcs', category: 'UNCATEGORIZED', firmId },
      headers: { 'X-Firm-Id': String(firmId) }
    });

    await app.gotoApp();
    await app.navigateTo('Firm');

    // Switch to Products subtab
    const productsTab = page.locator('button:has-text("Catalog & Product Master"), button:has-text("Products")').first();
    await expect(productsTab).toBeVisible({ timeout: 10000 });
    await productsTab.click();
    await page.waitForTimeout(400);

    // Search for the seeded product
    const searchInput = page.locator('input[placeholder*="Search name, SKU"]').first();
    await expect(searchInput).toBeVisible();
    await searchInput.fill(testProdName);
    await page.waitForTimeout(400);

    // Find row and click Edit
    const editBtn = page.locator(`.firm-workspace-pane .table-container table tbody tr:has-text("${testProdName}") button:has-text("Edit")`).first();
    await expect(editBtn).toBeVisible({ timeout: 10000 });
    await editBtn.click();
    await page.waitForTimeout(400);

    // Find active editing row
    const editRow = page.locator(`.firm-workspace-pane .table-container table tbody tr:has(input[value*="${testProdName}"])`).first();
    await expect(editRow).toBeVisible();

    // Enter updated base price and category
    const catInput = editRow.locator('input[list="master-categories-datalist"]').first();
    await catInput.fill('Hardware & Tools');

    const priceInput = editRow.locator('input[type="number"]').first();
    await priceInput.fill('195.00');

    // Click Save
    const saveBtn = editRow.locator('button:has-text("Save")').first();
    await saveBtn.click();
    await page.waitForTimeout(500);

    // Verify row displays updated values
    await expect(page.locator('.firm-workspace-pane .table-container table tbody tr:has-text("Hardware & Tools")').first()).toBeVisible({ timeout: 8000 });
  });

  test('02: Bulk Edit Mode multi-row editing with persistent draft and Review Changes Modal', async ({ app, page, request }) => {
    const firmsRes = await request.get('/api/firm');
    const firms = await firmsRes.json();
    const firmId = firms[0].id;

    const alphaName = 'Bulk Alpha ' + Date.now();
    const betaName = 'Bulk Beta ' + Date.now();

    // Seed 2 products
    await request.post('/api/products', {
      data: { name: alphaName, price: 50, costPrice: 30, minStockLevel: 5, unit: 'pcs', category: 'UNCATEGORIZED', firmId },
      headers: { 'X-Firm-Id': String(firmId) }
    });
    await request.post('/api/products', {
      data: { name: betaName, price: 80, costPrice: 45, minStockLevel: 10, unit: 'pcs', category: 'UNCATEGORIZED', firmId },
      headers: { 'X-Firm-Id': String(firmId) }
    });

    await app.gotoApp();
    await app.navigateTo('Firm');

    const productsTab = page.locator('button:has-text("Catalog & Product Master"), button:has-text("Products")').first();
    await expect(productsTab).toBeVisible({ timeout: 10000 });
    await productsTab.click();
    await page.waitForTimeout(400);

    // Click Bulk Edit Mode
    const bulkEditBtn = page.locator('button:has-text("Bulk Edit Mode")').first();
    await expect(bulkEditBtn).toBeVisible({ timeout: 10000 });
    await bulkEditBtn.click();
    await page.waitForTimeout(400);

    // Verify workspace banner
    await expect(page.locator('text=Bulk Product Master Edit Workspace')).toBeVisible();

    // Filter by Alpha
    const searchInput = page.locator('input[placeholder*="Search name, SKU"]').first();
    await searchInput.fill(alphaName);
    await page.waitForTimeout(400);

    const alphaRow = page.locator('.firm-workspace-pane .table-container table tbody tr').first();
    await expect(alphaRow).toBeVisible();

    // Modify price of Alpha
    const alphaPriceInput = alphaRow.locator('input[type="number"]').first();
    await alphaPriceInput.fill('75.00');

    // Switch search filter to Beta (Alpha edits must be retained in draft!)
    await searchInput.fill(betaName);
    await page.waitForTimeout(400);

    const betaRow = page.locator('.firm-workspace-pane .table-container table tbody tr').first();
    await expect(betaRow).toBeVisible();

    // Modify category of Beta
    const betaCatInput = betaRow.locator('input[list="master-categories-datalist"]').first();
    await betaCatInput.fill('Plumbing');

    // Clear search
    await searchInput.fill('');
    await page.waitForTimeout(400);

    // Open Review Changes modal
    const reviewBtn = page.locator('button:has-text("Review Changes & Save")').first();
    await expect(reviewBtn).toBeEnabled();
    await reviewBtn.click();
    await page.waitForTimeout(400);

    // Verify Review Modal
    const reviewModal = page.locator('.modal-content:has-text("Review Bulk Product Master Changes")');
    await expect(reviewModal).toBeVisible();
    await expect(reviewModal.locator('text=Products Changed')).toBeVisible();

    // Verify diff table contains Alpha and Beta
    await expect(reviewModal.locator(`tr:has-text("${alphaName}")`).first()).toBeVisible();
    await expect(reviewModal.locator(`tr:has-text("${betaName}")`).first()).toBeVisible();

    // Click Back to Editing
    const backBtn = reviewModal.locator('button:has-text("Back to Editing")');
    await backBtn.click();
    await page.waitForTimeout(300);
    await expect(reviewModal).toBeHidden();

    // Open Review modal and Confirm & Save
    await reviewBtn.click();
    await expect(reviewModal).toBeVisible();

    const saveAllBtn = reviewModal.locator('button:has-text("Confirm & Save All Changes")');
    await saveAllBtn.click();
    await page.waitForTimeout(600);

    // Verify modal closes and success toast is shown
    await expect(reviewModal).toBeHidden({ timeout: 10000 });
    await expect(page.locator('text=Successfully updated').first()).toBeVisible({ timeout: 8000 });
  });

  test('03: Needs Setup filter isolates incomplete product master records', async ({ app, page, request }) => {
    const firmsRes = await request.get('/api/firm');
    const firms = await firmsRes.json();
    const firmId = firms[0].id;
    const incName = 'Incomplete Wire ' + Date.now();

    // Seed incomplete product
    await request.post('/api/products', {
      data: { name: incName, price: 50, costPrice: 0, category: 'UNCATEGORIZED', firmId },
      headers: { 'X-Firm-Id': String(firmId) }
    });

    await app.gotoApp();
    await app.navigateTo('Firm');

    const productsTab = page.locator('button:has-text("Catalog & Product Master"), button:has-text("Products")').first();
    await expect(productsTab).toBeVisible({ timeout: 10000 });
    await productsTab.click();
    await page.waitForTimeout(400);

    // Click Needs Setup filter
    const needsSetupBtn = page.locator('button:has-text("Needs Setup")').first();
    await expect(needsSetupBtn).toBeVisible();
    await needsSetupBtn.click();
    await page.waitForTimeout(300);

    // Search for the incomplete product to verify filter match across paginated views
    const searchInput = page.locator('input[placeholder*="Search name, SKU"]').first();
    await searchInput.fill(incName);
    await page.waitForTimeout(400);

    // Verify incomplete wire appears
    await expect(page.locator(`.firm-workspace-pane .table-container table tbody tr:has-text("${incName}")`).first()).toBeVisible();
  });

  test('04: Intra-batch duplicate SKU displays error in Review modal and preserves draft', async ({ app, page, request, errorGate }) => {
    const firmsRes = await request.get('/api/firm');
    const firms = await firmsRes.json();
    const firmId = firms[0].id;

    const name1 = 'Batch Dup 1 ' + Date.now();
    const name2 = 'Batch Dup 2 ' + Date.now();

    await request.post('/api/products', {
      data: { name: name1, price: 100, costPrice: 50, firmId },
      headers: { 'X-Firm-Id': String(firmId) }
    });
    await request.post('/api/products', {
      data: { name: name2, price: 120, costPrice: 60, firmId },
      headers: { 'X-Firm-Id': String(firmId) }
    });

    await app.gotoApp();
    await app.navigateTo('Firm');

    const productsTab = page.locator('button:has-text("Catalog & Product Master"), button:has-text("Products")').first();
    await expect(productsTab).toBeVisible({ timeout: 10000 });
    await productsTab.click();
    await page.waitForTimeout(400);

    const bulkEditBtn = page.locator('button:has-text("Bulk Edit Mode")').first();
    await bulkEditBtn.click();
    await page.waitForTimeout(400);

    const searchInput = page.locator('input[placeholder*="Search name, SKU"]').first();

    // Assign same SKU to name1
    await searchInput.fill(name1);
    await page.waitForTimeout(400);
    const row1 = page.locator('.firm-workspace-pane .table-container table tbody tr').first();
    const sku1Input = row1.locator('input[placeholder*="SKU"]').first();
    await sku1Input.fill('COLLIDE-SKU-99');

    // Assign same SKU to name2
    await searchInput.fill(name2);
    await page.waitForTimeout(400);
    const row2 = page.locator('.firm-workspace-pane .table-container table tbody tr').first();
    const sku2Input = row2.locator('input[placeholder*="SKU"]').first();
    await sku2Input.fill('COLLIDE-SKU-99');

    await searchInput.fill('');
    await page.waitForTimeout(400);

    // Open review modal
    const reviewBtn = page.locator('button:has-text("Review Changes & Save")').first();
    await reviewBtn.click();
    await page.waitForTimeout(400);

    const reviewModal = page.locator('.modal-content:has-text("Review Bulk Product Master Changes")');
    await expect(reviewModal).toBeVisible();

    // Mark expected HTTP 400 for bulk collision
    errorGate.expectHttpError('/api/products/bulk');

    // Attempt save
    const saveAllBtn = reviewModal.locator('button:has-text("Confirm & Save All Changes")');
    await saveAllBtn.click();
    await page.waitForTimeout(600);

    // Verify error is shown in review modal and modal does not close
    await expect(reviewModal.locator('text=Duplicate SKU in batch')).toBeVisible({ timeout: 8000 });
    await expect(reviewModal).toBeVisible();

    // Click Back to Editing and confirm draft is still preserved
    const backBtn = reviewModal.locator('button:has-text("Back to Editing")');
    await backBtn.click();
    await page.waitForTimeout(300);
    await expect(reviewModal).toBeHidden();
    await expect(page.locator('text=2 products modified')).toBeVisible();

    // Clear intentional error from test gate
    errorGate.clear();
  });

  test('05: Single product inline edit can onboard catalog-only product to inventory with opening stock', async ({ app, page, request }) => {
    const firmsRes = await request.get('/api/firm');
    const firms = await firmsRes.json();
    const firmId = firms[0].id;
    const testProdName = 'Catalog To Inventory ' + Date.now();

    // Seed catalog-only product (inInventory = false)
    await request.post('/api/products', {
      data: { name: testProdName, price: 250, costPrice: 150, inInventory: false, category: 'UNCATEGORIZED', firmId },
      headers: { 'X-Firm-Id': String(firmId) }
    });

    await app.gotoApp();
    await app.navigateTo('Firm');

    const productsTab = page.locator('button:has-text("Catalog & Product Master"), button:has-text("Products")').first();
    await expect(productsTab).toBeVisible({ timeout: 10000 });
    await productsTab.click();
    await page.waitForTimeout(400);

    const searchInput = page.locator('input[placeholder*="Search name, SKU"]').first();
    await searchInput.fill(testProdName);
    await page.waitForTimeout(400);

    // Find row and click Edit
    const editBtn = page.locator(`.firm-workspace-pane .table-container table tbody tr:has-text("${testProdName}") button:has-text("Edit")`).first();
    await editBtn.click();
    await page.waitForTimeout(400);

    const editRow = page.locator(`.firm-workspace-pane .table-container table tbody tr:has(input[value*="${testProdName}"])`).first();
    await expect(editRow).toBeVisible();

    // Check "Track Stock"
    const trackCheckbox = editRow.locator('input[type="checkbox"]').first();
    await trackCheckbox.check();

    // Enter opening stock
    const openQtyInput = editRow.locator('input[placeholder*="Open Qty"]').first();
    await expect(openQtyInput).toBeVisible();
    await openQtyInput.fill('45');

    // Save
    const saveBtn = editRow.locator('button:has-text("Save")').first();
    await saveBtn.click();
    await page.waitForTimeout(600);

    // Verify row now shows tracked inventory with 45 pcs
    await searchInput.fill(testProdName);
    await page.waitForTimeout(400);
    const updatedRow = page.locator('.firm-workspace-pane .table-container table tbody tr').first();
    await expect(updatedRow.locator('text=45 pcs')).toBeVisible({ timeout: 8000 });
  });
});
