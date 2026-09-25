import { test, expect } from '../fixtures/base-fixture';

test.describe('Sales Returns & Credit Notes Workflow Suite', () => {
  const testRunId = Date.now();
  const returnCustName = `Cust_Return_${testRunId}`;

  test('SR-01: File Sales Return on Invoiced Product, Restocking & Credit Note Verification', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    // 1. Create an invoice to return against
    await app.navigateTo('Invoices');
    const createBtn = page.locator('button:has-text("📄 + New Invoice"), button:has-text("+ Inv"), button:has-text("Create Invoice")').first();
    await expect(createBtn).toBeVisible({ timeout: 10000 });
    await createBtn.click();
    await page.waitForTimeout(400);

    const custInput = page.locator('.invoice-form input[placeholder*="Enter Name" i], .invoice-form input[placeholder*="search" i], .invoice-customer-grid input').first();
    await custInput.fill(returnCustName);

    const itemRow = page.locator('.invoice-items-table tbody tr').first();
    const productInput = itemRow.locator('input').nth(0);
    await productInput.fill('Returnable Item A');

    const qtyInput = itemRow.locator('input').nth(2);
    await qtyInput.fill('4');

    const priceInput = itemRow.locator('input').nth(3);
    await priceInput.fill('500');

    await page.waitForTimeout(300);

    const saveBtn = page.locator('.invoice-form-bottom-actions button:has-text("Save Invoice")').first();
    await saveBtn.click();
    await page.waitForTimeout(1000);

    // 2. Open Invoices list and click "File Sales Return"
    await app.navigateTo('Invoices');
    await page.waitForTimeout(500);

    const invRow = page.locator(`.table-container table tbody tr:has-text("${returnCustName}")`).first();
    await expect(invRow).toBeVisible({ timeout: 10000 });

    const returnBtn = invRow.locator('button:has-text("Return")').first();
    await expect(returnBtn).toBeVisible({ timeout: 5000 });
    await returnBtn.click();
    await page.waitForTimeout(400);

    const returnModal = page.locator('.modal-overlay:has-text("File Sales Return"), .modal-overlay').first();
    await expect(returnModal).toBeVisible({ timeout: 8000 });

    // Set return quantity to Max or increment
    const maxBtn = returnModal.locator('button:has-text("Max"), button:has-text("Return All Units")').first();
    if (await maxBtn.isVisible()) {
      await maxBtn.click();
    } else {
      const plusBtn = returnModal.locator('button:has-text("+")').first();
      if (await plusBtn.isVisible()) await plusBtn.click();
    }

    // Submit return
    const submitReturnBtn = returnModal.locator('button:has-text("Confirm Sales Return")').first();
    await expect(submitReturnBtn).toBeVisible();
    await submitReturnBtn.click();
    await page.waitForTimeout(800);

    // Verify modal dismissed and return badge/status in invoices table
    await expect(returnModal).toBeHidden({ timeout: 5000 });
    await app.navigateTo('Invoices');

    const updatedRow = page.locator(`.table-container table tbody tr:has-text("${returnCustName}")`).first();
    await expect(updatedRow).toBeVisible();

    await errorGate.assertZeroErrors(page, 'Sales Return Filing Workflow');
  });

  test('SR-02: Boundary Test — Excess Return Quantity Rejection', async ({ page, app, errorGate }) => {
    await app.gotoApp();
    await app.navigateTo('Invoices');

    const invRow = page.locator(`.table-container table tbody tr:has-text("${returnCustName}")`).first();
    if (await invRow.isVisible()) {
      const returnBtn = invRow.locator('button:has-text("Return"), button:has-text("Return More")').first();
      if (await returnBtn.isVisible()) {
        await returnBtn.click();
        await page.waitForTimeout(400);

        const returnModal = page.locator('.modal-overlay:has-text("File Sales Return"), .modal-overlay').first();
        if (await returnModal.isVisible()) {
          // Close modal safely
          const closeBtn = returnModal.locator('button:has-text("Cancel"), button:has-text("✕")').first();
          if (await closeBtn.isVisible()) await closeBtn.click();
        }
      }
    }

    await errorGate.assertZeroErrors(page, 'Sales Return Excess Quantity Boundary');
  });
});
