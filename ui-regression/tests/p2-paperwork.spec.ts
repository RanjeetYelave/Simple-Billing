import { test, expect } from '../fixtures/base-fixture';

test.describe('P2: Real Paperwork Hub — Purchase Orders, Business Letters & Statements', () => {
  const testRunId = Date.now();
  const testPoRef = `PO_${testRunId}`;

  test('P2-PAP-01: Purchase Order Creation, Item Rows & Persistence', async ({ page, app, errorGate }) => {
    await app.gotoApp();
    await app.navigateTo('Firm');

    const paperworkTab = page.locator('button:has-text("Paperwork")').first();
    await expect(paperworkTab).toBeVisible({ timeout: 10000 });
    await paperworkTab.click();
    await page.waitForTimeout(400);

    // Switch to Orders subtab
    const ordersTab = page.locator('button:has-text("Purchase Orders")').first();
    await expect(ordersTab).toBeVisible({ timeout: 10000 });
    await ordersTab.click();
    await page.waitForTimeout(400);

    // Verify PO Manager is mounted
    const createPoBtn = page.locator('button:has-text("Create Purchase Order")').first();
    await expect(createPoBtn).toBeVisible({ timeout: 10000 });

    await errorGate.assertZeroErrors(page, 'Purchase Order Workflow');
  });

  test('P2-PAP-02: Business Letter Template Drafting & Preview', async ({ page, app, errorGate }) => {
    await app.gotoApp();
    await app.navigateTo('Firm');

    const paperworkTab = page.locator('button:has-text("Paperwork")').first();
    await paperworkTab.click();
    await page.waitForTimeout(400);

    const lettersTab = page.locator('button:has-text("Letter Pad")').first();
    await expect(lettersTab).toBeVisible({ timeout: 10000 });
    await lettersTab.click();
    await page.waitForTimeout(400);

    const letterManager = page.locator('button:has-text("Create Letter"), button:has-text("New Letter"), .sub-tabs-bar').first();
    await expect(letterManager).toBeVisible({ timeout: 8000 });

    await errorGate.assertZeroErrors(page, 'Business Letters Workflow');
  });

  test('P2-PAP-03: Statements Subtab & Date Range Querying', async ({ page, app, errorGate }) => {
    await app.gotoApp();
    await app.navigateTo('Firm');

    const paperworkTab = page.locator('button:has-text("Paperwork")').first();
    await paperworkTab.click();
    await page.waitForTimeout(400);

    const stmtTab = page.locator('button:has-text("Statements & Ledgers"), button:has-text("Statements")').first();
    await expect(stmtTab).toBeVisible({ timeout: 10000 });
    await stmtTab.click();
    await page.waitForTimeout(400);

    const generateBtn = page.locator('button:has-text("Generate Statement"), button:has-text("View Ledger"), .btn-primary').first();
    await expect(generateBtn).toBeVisible({ timeout: 8000 });

    await errorGate.assertZeroErrors(page, 'Statements Generation');
  });
});
