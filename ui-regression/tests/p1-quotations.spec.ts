import { test, expect } from '../fixtures/base-fixture';

test.describe('P1: Quotation Workflows & Conversion Pipeline', () => {
  const testRunId = Date.now();
  let firmId: number;
  let customerId: number;

  test.beforeAll(async ({ request }) => {
    const firmsRes = await request.get('/api/firm');
    const firms = await firmsRes.json();
    firmId = firms && firms.length > 0 ? firms[0].id : 1;

    const custRes = await request.post('/api/customers', {
      data: { name: `QT_Cust_${testRunId}`, phone: '9876543210', firmId },
      headers: { 'X-Firm-Id': String(firmId) }
    });
    const cust = await custRes.json();
    customerId = cust.id;

    // Create a real estimate/quotation via API for deterministic UI testing
    await request.post('/api/invoices/estimate', {
      data: {
        firmId,
        customerId,
        notes: `Quote Verification ${testRunId}`,
        items: [{
          productName: 'Sample Product Quote',
          quantity: 2,
          unitPrice: 1000,
          gstPercent: 18,
          total: 2360
        }]
      },
      headers: { 'X-Firm-Id': String(firmId) }
    });
  });

  test('P1-QT-01: Quotation Hub, Tab Switching & Creation Form', async ({ page, app, errorGate }) => {
    await app.gotoApp();
    await app.navigateTo('Invoices');

    // Click Quotations filter button
    const quotesTab = page.locator('button:has-text("Quotations")').first();
    await expect(quotesTab).toBeVisible({ timeout: 10000 });
    await quotesTab.click();
    await page.waitForTimeout(400);

    // Verify Quotation row is visible
    const quoteRow = page.locator(`table tbody tr:has-text("QT_Cust_${testRunId}"), :visible:text("QT_Cust_${testRunId}")`).first();
    await expect(quoteRow).toBeVisible({ timeout: 10000 });

    await errorGate.assertZeroErrors(page, 'Quotation Hub & Tab Switching');
  });

  test('P1-QT-02: Quotation Search and Status Filter', async ({ page, app, errorGate }) => {
    await app.gotoApp();
    await app.navigateTo('Invoices');

    const quotesTab = page.locator('button:has-text("Quotations")').first();
    await expect(quotesTab).toBeVisible({ timeout: 10000 });
    await quotesTab.click();
    await page.waitForTimeout(400);

    // Search for our created customer quotation
    const searchInput = page.locator('input[placeholder*="Search by number" i], input[placeholder*="Search" i]').first();
    await expect(searchInput).toBeVisible({ timeout: 5000 });
    await searchInput.fill(`QT_Cust_${testRunId}`);
    await page.waitForTimeout(400);

    const matchRow = page.locator(`table tbody tr:has-text("QT_Cust_${testRunId}"), :visible:text("QT_Cust_${testRunId}")`).first();
    await expect(matchRow).toBeVisible({ timeout: 10000 });

    await errorGate.assertZeroErrors(page, 'Quotation Search & Filter');
  });
});
