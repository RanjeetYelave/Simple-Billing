import { test, expect } from '../fixtures/base-fixture';

test.describe('Quotation-to-Invoice Conversion State After Invoice Deletion E2E Workflow', () => {
  const runId = Date.now();
  const artifactDir = '/Users/afk/.gemini/antigravity/brain/e29d8bbe-645d-4d4c-8cf3-90f97c0281f3';

  test('Execute complete 11-step quotation conversion, deletion, and reconversion lifecycle in Light & Dark themes', async ({ page, app, request, errorGate }) => {
    test.setTimeout(60000);
    errorGate.expectHttpError('/api/party-links/customer');
    // 1. Get firm & seed customer
    const firmsRes = await request.get('/api/firm');
    const firms = await firmsRes.json();
    const firmId = firms && firms.length > 0 ? firms[0].id : 225;

    const custRes = await request.post('/api/customers', {
      data: { name: `Quote_Corp_${runId}`, phone: '9876543210', firmId },
      headers: { 'X-Firm-Id': String(firmId) }
    });
    const customer = await custRes.json();

    // STEP 1: Create a quotation via API
    const quoteRes = await request.post('/api/invoices', {
      data: {
        firmId,
        customerId: customer.id,
        status: 'ESTIMATE',
        customerNote: `Quote Notes ${runId}`,
        items: [{
          productName: 'Professional Consulting Package',
          qty: 3,
          pricePerUnit: 1500,
          discountValue: 0
        }]
      },
      headers: { 'X-Firm-Id': String(firmId) }
    });
    const quote = await quoteRes.json();
    const quoteId = quote.id;
    const quoteNum = quote.estimateNumber || `#${quoteId}`;

    // Navigate to UI
    await app.gotoApp();
    await app.navigateTo('Invoices');

    // Click Quotations sub-tab
    const quotesTab = page.locator('button:has-text("Quotations")').first();
    await expect(quotesTab).toBeVisible({ timeout: 10000 });
    await quotesTab.click();
    await page.waitForTimeout(500);

    // STEP 1 Verify: Quotation is visible and displays "Convert"
    const quoteRow = page.locator(`tr:has-text("${customer.name}"), tr:has-text("${quoteNum}")`).first();
    await expect(quoteRow).toBeVisible({ timeout: 10000 });

    const convertBtn = quoteRow.locator('button:has-text("Convert")');
    await expect(convertBtn).toBeVisible();

    // STEP 2: Convert quotation into an invoice
    await convertBtn.click();
    await page.waitForTimeout(400);

    // Choice modal opens; click 1-Click Instant Convert
    const instantConvertBtn = page.locator('.modal-content button:has-text("1-Click Instant Convert")');
    await expect(instantConvertBtn).toBeVisible({ timeout: 5000 });
    await instantConvertBtn.click();
    await page.waitForTimeout(1000);

    // STEP 3: Confirm quotation displays "Go to Inv ➔" and "Converted" status badge
    await expect(quoteRow.locator('.badge.badge-final:has-text("Converted")')).toBeVisible({ timeout: 10000 });
    const goToInvBtn = quoteRow.locator('.actions-primary-btn:has-text("Go to Inv")');
    await expect(goToInvBtn).toBeVisible();

    // Capture screenshot: Converted Quotation (Light Theme)
    await page.screenshot({ path: `${artifactDir}/quotation_converted_light.png` });

    // STEP 4: Open the converted invoice
    await goToInvBtn.click();
    await page.waitForTimeout(1000);

    // Confirm Invoice Form tab is open
    const invoiceTabHeading = page.locator('span:has-text("Edit Inv"), .invoice-meta-col').first();
    await expect(invoiceTabHeading).toBeVisible({ timeout: 10000 });

    // Close the invoice tab using its Close tab button to return to overview
    const closeTabBtn = page.locator('[title="Close tab"]').first();
    if (await closeTabBtn.isVisible()) {
      await closeTabBtn.click();
      await page.waitForTimeout(500);
    } else {
      const backBtn = page.locator('button:has-text("← Overview & Documents")').first();
      if (await backBtn.isVisible()) {
        await backBtn.click();
        await page.waitForTimeout(500);
      }
    }

    // Find the converted invoice ID from API
    const linkedRes = await request.get(`/api/invoices/${quoteId}/linked-invoice`, {
      headers: { 'X-Firm-Id': String(firmId) }
    });
    expect(linkedRes.status()).toBe(200);
    const convertedInvoice = await linkedRes.json();
    const firstInvoiceId = convertedInvoice.id;
    const firstInvoiceNum = convertedInvoice.invoiceNumber;

    // STEP 5: Delete that invoice using normal invoice deletion workflow
    const delRes = await request.delete(`/api/invoices/${firstInvoiceId}`, {
      headers: { 'X-Firm-Id': String(firmId) }
    });
    expect(delRes.status()).toBe(204);

    // Trigger UI refresh
    await page.evaluate(() => window.dispatchEvent(new CustomEvent('billsoft:app-refresh')));
    await page.waitForTimeout(600);

    // STEP 6: Return to the original quotation
    await quotesTab.click();
    await page.waitForTimeout(600);

    // STEP 7: Confirm the quotation displays Deleted + Convert Again
    const updatedQuoteRow = page.locator(`tr:has-text("${customer.name}"), tr:has-text("${quoteNum}")`).first();
    await expect(updatedQuoteRow).toBeVisible({ timeout: 10000 });

    // Deleted badge visible in status column
    const deletedBadge = updatedQuoteRow.locator('.badge.badge-danger:has-text("Deleted")');
    await expect(deletedBadge).toBeVisible({ timeout: 8000 });

    // Stale "Go to Inv ➔" is GONE
    await expect(updatedQuoteRow.locator('button:has-text("Go to Inv")')).not.toBeVisible();

    // "Convert Again" action button is visible
    const convertAgainBtn = updatedQuoteRow.locator('button:has-text("Convert Again")');
    await expect(convertAgainBtn).toBeVisible();

    // Capture screenshot: Deleted status + Convert Again (Light Theme)
    await page.screenshot({ path: `${artifactDir}/quotation_invoice_deleted_light.png` });

    // Now test in Dark Theme as requested by Section 9
    await page.evaluate(() => {
      document.documentElement.className = 'dark';
      localStorage.setItem('theme', 'dark');
    });
    await page.waitForTimeout(400);

    // Capture screenshot: Deleted status + Convert Again (Dark Theme)
    await page.screenshot({ path: `${artifactDir}/quotation_invoice_deleted_dark.png` });

    // STEP 8: Click Convert Again
    await convertAgainBtn.click();
    await page.waitForTimeout(400);

    // Conversion modal opens; click 1-Click Instant Convert
    const reconvertInstantBtn = page.locator('.modal-content button:has-text("1-Click Instant Convert")');
    await expect(reconvertInstantBtn).toBeVisible({ timeout: 5000 });
    await reconvertInstantBtn.click();
    await page.waitForTimeout(1000);

    // STEP 9: Confirm a new invoice is created successfully
    const reconvertedQuoteRow = page.locator(`tr:has-text("${customer.name}"), tr:has-text("${quoteNum}")`).first();
    await expect(reconvertedQuoteRow.locator('.badge.badge-final:has-text("Converted")')).toBeVisible({ timeout: 10000 });

    const newGoToInvBtn = reconvertedQuoteRow.locator('.actions-primary-btn:has-text("Go to Inv")');
    await expect(newGoToInvBtn).toBeVisible();

    // Verify via backend that the new invoice ID is DIFFERENT from the deleted invoice ID
    const newLinkedRes = await request.get(`/api/invoices/${quoteId}/linked-invoice`, {
      headers: { 'X-Firm-Id': String(firmId) }
    });
    expect(newLinkedRes.status()).toBe(200);
    const newInvoice = await newLinkedRes.json();
    expect(newInvoice.id).not.toBe(firstInvoiceId);
    expect(newInvoice.invoiceNumber).not.toBe(firstInvoiceNum);

    // Capture screenshot: Reconverted Quotation (Dark Theme)
    await page.screenshot({ path: `${artifactDir}/quotation_reconverted_dark.png` });

    // Switch back to light theme for final screenshot
    await page.evaluate(() => {
      document.documentElement.className = '';
      localStorage.setItem('theme', 'modern-light');
    });
    await page.waitForTimeout(400);

    // Capture screenshot: Reconverted Quotation (Light Theme)
    await page.screenshot({ path: `${artifactDir}/quotation_reconverted_light.png` });

    // STEP 10 & 11: Click Go to Invoice and confirm it opens newly created invoice
    await newGoToInvBtn.click();
    await page.waitForTimeout(1000);

    // Verify it opened the new invoice tab (not the deleted one)
    const newTabTitle = page.locator(`span:has-text("Edit Inv #${newInvoice.id}")`).first();
    await expect(newTabTitle).toBeVisible({ timeout: 10000 });
    const pageText = await page.textContent('body');
    expect(pageText).toContain(newInvoice.invoiceNumber || String(newInvoice.id));
    expect(pageText).not.toContain(firstInvoiceNum);
  });
});
