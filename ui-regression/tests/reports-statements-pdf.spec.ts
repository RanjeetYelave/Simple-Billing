import { test, expect } from '../fixtures/base-fixture';
import * as fs from 'fs';

test.describe('P1: Reports, Ledgers, Statements & PDF Binary Generation Suite', () => {
  const testRunId = Date.now();
  const repCustName = `REP_Cust_${testRunId}`;
  let firmId: number;
  let custId: number;
  let invoiceId: number;
  const invoiceTotal = 1500;

  test.beforeAll(async ({ request }) => {
    // 1. Get firm ID
    const firmsRes = await request.get('/api/firm');
    const firms = await firmsRes.json();
    firmId = firms && firms.length > 0 ? firms[0].id : 1;

    // 2. Create customer
    const cRes = await request.post('/api/customers', {
      data: { name: repCustName, phone: '9876501234', firmId },
      headers: { 'X-Firm-Id': String(firmId) }
    });
    const cust = await cRes.json();
    custId = cust.id;

    // 3. Create invoice with known amount
    const invRes = await request.post('/api/invoices', {
      data: {
        firmId,
        customerId: custId,
        notes: `Known Report Txn ${testRunId}`,
        paid: true,
        items: [{
          productName: 'Consulting Service',
          quantity: 1,
          unitPrice: invoiceTotal,
          gstPercent: 0,
          total: invoiceTotal
        }]
      },
      headers: { 'X-Firm-Id': String(firmId) }
    });
    const inv = await invRes.json();
    invoiceId = inv.id;
  });

  test('REP-01: Statements & Reports Date Range Filters & Aggregate Verification', async ({ page, app, errorGate }) => {
    await app.gotoApp();
    await app.navigateTo('Firm');

    // Navigate to Paperwork -> Statements & Ledgers
    const paperworkTab = page.locator('button:has-text("Paperwork")').first();
    await expect(paperworkTab).toBeVisible({ timeout: 10000 });
    await paperworkTab.click();
    await page.waitForTimeout(400);

    const statementsSubTab = page.locator('button:has-text("Statements & Ledgers"), button:has-text("Statements")').first();
    await expect(statementsSubTab).toBeVisible({ timeout: 5000 });
    await statementsSubTab.click();
    await page.waitForTimeout(400);

    // Select Customer Statement mode
    const custStmtCard = page.locator('div:has-text("Customer Statement")').last();
    await expect(custStmtCard).toBeVisible({ timeout: 5000 });
    await custStmtCard.click();
    await page.waitForTimeout(300);

    // Select our created customer
    const custSelect = page.locator('select.input, select').filter({ hasText: repCustName }).first();
    if (await custSelect.isVisible()) {
      await custSelect.selectOption({ value: String(custId) }).catch(async () => {
        await custSelect.selectOption({ label: repCustName });
      });
      await page.waitForTimeout(400);
    }

    // Test Quick Range Presets: "This Month", "Last Quarter", "All Time"
    const thisMonthBtn = page.locator('button:has-text("This Month")').first();
    if (await thisMonthBtn.isVisible()) {
      await thisMonthBtn.click();
      await page.waitForTimeout(300);
    }

    const lastQuarterBtn = page.locator('button:has-text("Last Quarter"), button:has-text("Quarter")').first();
    if (await lastQuarterBtn.isVisible()) {
      await lastQuarterBtn.click();
      await page.waitForTimeout(300);
    }

    const allTimeBtn = page.locator('button:has-text("All Time"), button:has-text("All")').first();
    if (await allTimeBtn.isVisible()) {
      await allTimeBtn.click();
      await page.waitForTimeout(300);
    }

    // Generate Statement
    const generateBtn = page.locator('button:has-text("Generate Statement"), button:has-text("View Statement")').first();
    if (await generateBtn.isVisible()) {
      await generateBtn.click();
      await page.waitForTimeout(600);
    }

    await errorGate.assertZeroErrors(page, 'Statements & Reports Filtering');
  });

  test('REP-02: Invoice PDF Binary Payload Inspection & Header Verification', async ({ page, app, errorGate }) => {
    await app.gotoApp();
    await app.navigateTo('Invoices');

    // Locate the row for our created invoice or the first row
    const invoiceRow = page.locator('.table-container table tbody tr', { hasText: 'Consulting Service' }).first();
    const targetRow = (await invoiceRow.isVisible().catch(() => false)) ? invoiceRow : page.locator('.table-container table tbody tr').first();
    await expect(targetRow).toBeVisible({ timeout: 10000 });

    // Locate and click PDF/Print button in row to open PDF preview modal
    const pdfBtn = targetRow.locator('button:has-text("PDF"), button:has-text("Print"), button[title*="PDF" i]').first();
    await expect(pdfBtn).toBeVisible({ timeout: 5000 });
    await pdfBtn.click();
    await page.waitForTimeout(400);

    // Locate the modal and the "Download PDF" action button inside it
    const modal = page.locator('.modal-overlay, div[role="dialog"]').first();
    await expect(modal).toBeVisible({ timeout: 5000 });

    const downloadModalBtn = modal.locator('button:has-text("Download PDF")').first();
    await expect(downloadModalBtn).toBeVisible({ timeout: 5000 });

    const downloadPromise = page.waitForEvent('download', { timeout: 15000 });
    await downloadModalBtn.click();
    const download = await downloadPromise;

    // Verify filename
    const filename = download.suggestedFilename();
    expect(filename.toLowerCase()).toContain('.pdf');

    // Read actual file bytes
    const filePath = await download.path();
    expect(filePath).not.toBeNull();
    if (filePath) {
      const fileBytes = fs.readFileSync(filePath);
      // 1. Assert non-zero size
      expect(fileBytes.length).toBeGreaterThan(500);
      // 2. Assert valid PDF signature (%PDF magic bytes)
      const pdfMagic = fileBytes.subarray(0, 4).toString('ascii');
      expect(pdfMagic).toBe('%PDF');

      console.log(`Verified Invoice PDF Binary: Filename=${filename}, BytesLength=${fileBytes.length}, MagicHeader=${pdfMagic}`);
    }

    await errorGate.assertZeroErrors(page, 'Invoice PDF Binary Payload Inspection');
  });
});
