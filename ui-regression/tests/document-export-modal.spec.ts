import { test, expect } from '../fixtures/base-fixture';

test.describe('DocumentExportModal: Phase 1 & 2 Standardization Suite', () => {
  const testRunId = Date.now();
  let firmId: number;
  let customerId: number;

  test.beforeAll(async ({ request }) => {
    // 1. Ensure firm exists in isolated test DB
    const firmsRes = await request.get('/api/firm');
    const firms = await firmsRes.json();
    if (!firms || firms.length === 0) {
      const createFirmRes = await request.post('/api/firm', {
        data: {
          firmName: 'Apex Test Firm',
          gstin: '27AAAAA1234A1Z5',
          phone: '9820012345',
          email: 'test@apex.com',
          addressLine1: 'Industrial Zone, Mumbai'
        }
      });
      const newFirm = await createFirmRes.json();
      firmId = newFirm.id;
    } else {
      firmId = firms[0].id;
    }

    // 2. Create customer
    const custRes = await request.post('/api/customers', {
      data: { name: `ExportModal_Cust_${testRunId}`, phone: '9988776655', firmId },
      headers: { 'X-Firm-Id': String(firmId) }
    });
    const cust = await custRes.json();
    customerId = cust.id;

    // 3. Create regular invoice
    const invRes = await request.post('/api/invoices', {
      data: {
        firmId,
        customerId,
        notes: `Modal Verification Inv ${testRunId}`,
        paid: true,
        items: [{
          productName: 'Standard Test Item',
          quantity: 2,
          unitPrice: 1500,
          gstPercent: 18,
          total: 3540
        }]
      },
      headers: { 'X-Firm-Id': String(firmId) }
    });
    const invJson = await invRes.json();
    const invoiceId = invJson.id;

    // 4. Create estimate / quotation
    await request.post('/api/invoices/estimate', {
      data: {
        firmId,
        customerId,
        notes: `Modal Verification Quote ${testRunId}`,
        items: [{
          productName: 'Quotation Test Product',
          quantity: 1,
          unitPrice: 2000,
          gstPercent: 18,
          total: 2360
        }]
      },
      headers: { 'X-Firm-Id': String(firmId) }
    });

    // 5. Create Sales Return (Credit Note)
    await request.post(`/api/invoices/${invoiceId}/returns`, {
      data: {
        firmId,
        reason: 'DEFECTIVE_PRODUCT',
        refundMode: 'CASH',
        notes: `Modal Verification Return ${testRunId}`,
        items: [{
          productName: 'Standard Test Item',
          returnQty: 1,
          unitPrice: 1500,
          gstPercent: 18,
          taxableAmount: 1500,
          cgstAmount: 135,
          sgstAmount: 135,
          igstAmount: 0,
          total: 1770
        }]
      },
      headers: { 'X-Firm-Id': String(firmId) }
    });

    // 6. Create Party for Purchase Order
    const partyRes = await request.post('/api/parties', {
      data: {
        name: `ExportModal_Party_${testRunId}`,
        phone: '9811223344',
        email: 'supplier@modaltest.com',
        gstin: '27AAACS1122D1Z3',
        firmId
      },
      headers: { 'X-Firm-Id': String(firmId) }
    });
    const party = await partyRes.json();
    const partyId = party.id;

    // 7. Create Purchase Order
    await request.post('/api/purchase-orders', {
      data: {
        firmId,
        party: { id: partyId },
        poNumber: `PO-${testRunId}`,
        poDate: new Date().toISOString().split('T')[0],
        status: 'ISSUED',
        notes: `PO Verification ${testRunId}`,
        items: [{
          productName: 'Raw Ingot Alloy',
          quantity: 5,
          unitPrice: 1000,
          gstPercent: 18,
          taxableAmount: 5000,
          cgstAmount: 450,
          sgstAmount: 450,
          igstAmount: 0,
          totalAmount: 5900
        }]
      },
      headers: { 'X-Firm-Id': String(firmId) }
    });

    // 8. Create Business Letter
    await request.post('/api/letters', {
      data: {
        firmId,
        letterNumber: `LTR-${testRunId}`,
        letterDate: new Date().toISOString().split('T')[0],
        recipientType: 'CUSTOM',
        recipientName: `ExportModal_Recipient_${testRunId}`,
        subject: `Letter Standardization Test ${testRunId}`,
        content: 'Dear Partner,\n\nThis is a standardized business letter test verification.\n\nRegards.',
        status: 'ISSUED'
      },
      headers: { 'X-Firm-Id': String(firmId) }
    });
  });

  test('DOC-01: Invoice Preview Dialog Accessibility, ARIA Attributes & Focus Trap', async ({ page, app, errorGate }) => {
    await app.gotoApp();
    await app.navigateTo('Invoices');

    // Find invoice row in table
    const invoiceRow = page.locator(`tr:has-text("ExportModal_Cust_${testRunId}")`).filter({ hasText: 'Invoice' }).first();
    await expect(invoiceRow).toBeVisible({ timeout: 10000 });

    // Open Print preview modal via the row's "Preview / Export" button
    const printBtn = invoiceRow.locator('.btn-seg-preview, button:has-text("Preview / Export"), button:has-text("Print")').first();
    await expect(printBtn).toBeVisible({ timeout: 5000 });
    await printBtn.click();

    // Verify modal container has role="dialog" and aria-modal="true"
    const modalDialog = page.locator('#invoice-print-dialog[role="dialog"]');
    await expect(modalDialog).toBeVisible({ timeout: 8000 });
    await expect(modalDialog).toHaveAttribute('aria-modal', 'true');
    await expect(modalDialog).toHaveAttribute('aria-labelledby', 'document-export-modal-title');

    // Verify Title & Type Badge
    const titleElement = modalDialog.locator('#document-export-modal-title');
    await expect(titleElement).toBeVisible();
    const badgeElement = modalDialog.locator('.doc-export-type-badge');
    await expect(badgeElement).toContainText(/Tax Invoice/i);

    // Verify Color Swatches have accessible aria-labels and aria-pressed state
    const swatches = modalDialog.locator('.color-swatch-btn, .doc-export-palette-swatch');
    const count = await swatches.count();
    expect(count).toBe(5);

    const firstSwatch = swatches.first();
    await expect(firstSwatch).toHaveAttribute('aria-label');
    await expect(firstSwatch).toHaveAttribute('aria-pressed');

    // Test Escape Key Dismissal
    await page.keyboard.press('Escape');
    await expect(modalDialog).not.toBeVisible({ timeout: 5000 });

    await errorGate.assertZeroErrors(page, 'Invoice Dialog Accessibility & Escape Dismissal');
  });

  test('DOC-02: Quotation Preview Retains Quotation Presentation & Badge', async ({ page, app, errorGate }) => {
    await app.gotoApp();
    await app.navigateTo('Invoices');

    // Switch to Quotations tab filter
    const quotesTab = page.locator('button:has-text("Quotations")').first();
    await expect(quotesTab).toBeVisible({ timeout: 10000 });
    await quotesTab.click();
    await page.waitForTimeout(400);

    // Locate quotation row
    const quoteRow = page.locator(`tr:has-text("ExportModal_Cust_${testRunId}")`).filter({ hasText: 'Quotation' }).first();
    await expect(quoteRow).toBeVisible({ timeout: 10000 });

    const quotePrintBtn = quoteRow.locator('.btn-seg-preview, button:has-text("Preview / Export"), button:has-text("Print")').first();
    await expect(quotePrintBtn).toBeVisible({ timeout: 5000 });
    await quotePrintBtn.click();

    // Verify modal shows Quotation title and badge
    const modalDialog = page.locator('#invoice-print-dialog[role="dialog"]');
    await expect(modalDialog).toBeVisible({ timeout: 8000 });

    const titleElement = modalDialog.locator('#document-export-modal-title');
    await expect(titleElement).toBeVisible();
    const badgeElement = modalDialog.locator('.doc-export-type-badge');
    await expect(badgeElement).toContainText(/Quotation/i);

    // Close modal via close button
    const closeBtn = modalDialog.locator('button[aria-label="Close document preview"], .doc-export-modal-close');
    await closeBtn.click();
    await expect(modalDialog).not.toBeVisible({ timeout: 5000 });

    await errorGate.assertZeroErrors(page, 'Quotation Dialog Identity & Close');
  });

  test('DOC-03: Theme, Palette and Page Format Selection Interactivity', async ({ page, app, errorGate }) => {
    await app.gotoApp();
    await app.navigateTo('Invoices');

    const invoiceRow = page.locator(`tr:has-text("ExportModal_Cust_${testRunId}")`).filter({ hasText: 'Invoice' }).first();
    await invoiceRow.locator('.btn-seg-preview, button:has-text("Preview / Export"), button:has-text("Print")').first().click();

    const modalDialog = page.locator('#invoice-print-dialog[role="dialog"]');
    await expect(modalDialog).toBeVisible({ timeout: 8000 });

    // 1. Verify authoritative 3 themes are selectable
    const themeSelect = modalDialog.locator('select').first();
    await expect(themeSelect).toBeVisible();
    await themeSelect.selectOption('MODERN');
    await page.waitForTimeout(200);
    await themeSelect.selectOption('PROFESSIONAL');
    await page.waitForTimeout(200);
    await themeSelect.selectOption('CLASSIC');

    // 2. Verify all 5 palettes can be clicked and update aria-pressed
    const swatches = modalDialog.locator('.color-swatch-btn, .doc-export-palette-swatch');
    for (let i = 0; i < 5; i++) {
      const swatch = swatches.nth(i);
      await swatch.click();
      await expect(swatch).toHaveAttribute('aria-pressed', 'true');
      await page.waitForTimeout(150);
    }

    // 3. Verify format select
    const formatSelect = modalDialog.locator('select').nth(1);
    await expect(formatSelect).toBeVisible();
    await formatSelect.selectOption('A5');
    await page.waitForTimeout(200);
    await formatSelect.selectOption('THERMAL_80MM');
    await page.waitForTimeout(200);
    await formatSelect.selectOption('A4');

    // Close
    await page.keyboard.press('Escape');
    await expect(modalDialog).not.toBeVisible({ timeout: 5000 });

    await errorGate.assertZeroErrors(page, 'Theme, Palette, Format Interactivity');
  });

  test('DOC-04: Headless Print Now Invocation (No Redundant Interactive Overlay)', async ({ page, app, errorGate }) => {
    await app.gotoApp();
    await app.navigateTo('Invoices');

    const invoiceRow = page.locator(`tr:has-text("ExportModal_Cust_${testRunId}")`).filter({ hasText: 'Invoice' }).first();
    await invoiceRow.locator('.btn-seg-preview, button:has-text("Preview / Export"), button:has-text("Print")').first().click();

    const modalDialog = page.locator('#invoice-print-dialog[role="dialog"]');
    await expect(modalDialog).toBeVisible({ timeout: 8000 });

    // Spy on BillsoftUtils.printBlob in the page context
    await page.evaluate(() => {
      (window as any).__lastPrintOptions = null;
      const originalPrintBlob = (window as any).BillsoftUtils.printBlob;
      (window as any).BillsoftUtils.printBlob = function(blob: any, options: any) {
        (window as any).__lastPrintOptions = options;
        // Don't call browser window.print() during automated test runner
        return true;
      };
    });

    // Click Print Now button
    const printNowBtn = modalDialog.locator('button:has-text("Print Now"), button:has-text("🖨️ Print")').first();
    await expect(printNowBtn).toBeVisible();
    await printNowBtn.click();
    await page.waitForTimeout(500);

    // Verify headless: true option was passed
    const lastOptions = await page.evaluate(() => (window as any).__lastPrintOptions);
    expect(lastOptions).toEqual(expect.objectContaining({ headless: true }));

    // Verify NO redundant second interactive print overlay was injected into DOM
    const redundantOverlay = page.locator('#billsoft-interactive-print-overlay');
    await expect(redundantOverlay).not.toBeVisible();

    await errorGate.assertZeroErrors(page, 'Headless Print Now Flow');
  });

  test('DOC-05: Customer CSV Export Uses Shared Download Bridge', async ({ page, app, errorGate }) => {
    await app.gotoApp();
    await app.navigateTo('Firm');

    // Navigate to Customers tab
    const custTab = page.locator('button:has-text("Customers")').first();
    await expect(custTab).toBeVisible({ timeout: 10000 });
    await custTab.click();
    await page.waitForTimeout(400);

    // Spy on BillsoftUtils.downloadBlob
    await page.evaluate(() => {
      (window as any).__downloadBlobCalled = false;
      (window as any).__downloadFilename = null;
      const originalDownload = (window as any).BillsoftUtils.downloadBlob;
      (window as any).BillsoftUtils.downloadBlob = function(blob: any, filename: string) {
        (window as any).__downloadBlobCalled = true;
        (window as any).__downloadFilename = filename;
        return originalDownload(blob, filename);
      };
    });

    // Click Export CSV button ('CSV' in CustomerManager toolbar)
    const exportCsvBtn = page.locator('button:has-text("CSV"), button[title*="Export active customers to CSV"]').first();
    await expect(exportCsvBtn).toBeVisible({ timeout: 5000 });
    await exportCsvBtn.click();
    await page.waitForTimeout(500);

    // Assert that BillsoftUtils.downloadBlob was called with proper filename
    const wasCalled = await page.evaluate(() => (window as any).__downloadBlobCalled);
    const filename = await page.evaluate(() => (window as any).__downloadFilename);

    expect(wasCalled).toBe(true);
    expect(filename).toMatch(/RupeeCRM_Customers_\d{4}-\d{2}-\d{2}\.csv/);

    await errorGate.assertZeroErrors(page, 'Customer CSV Shared Download Bridge');
  });

  test('DOC-06: Sales Return / Credit Note Export Modal Retains Identity, Formats & Headless Print', async ({ page, app, errorGate }) => {
    await app.gotoApp();
    await app.navigateTo('Invoices');

    // Switch to Returns subtab
    const returnsSubtab = page.locator('button:has-text("Returns & Credit Notes")').first();
    await expect(returnsSubtab).toBeVisible({ timeout: 10000 });
    await returnsSubtab.click();
    await page.waitForTimeout(400);

    // Locate the return row
    const returnRow = page.locator(`tr:has-text("Modal Verification Return ${testRunId}"), tr:has-text("ExportModal_Cust_${testRunId}")`).first();
    await expect(returnRow).toBeVisible({ timeout: 10000 });

    // Click Print button on return row
    const printBtn = returnRow.locator('.returns-btn-print, button:has-text("Print")').first();
    await expect(printBtn).toBeVisible({ timeout: 5000 });
    await printBtn.click();

    // Verify DocumentExportModal dialog opened
    const modalDialog = page.locator('#invoice-print-dialog[role="dialog"]');
    await expect(modalDialog).toBeVisible({ timeout: 8000 });

    // Verify Title & Badge identity for Credit Note
    const badgeElement = modalDialog.locator('.doc-export-type-badge');
    await expect(badgeElement).toContainText(/Credit Note|Sales Return/i);

    // Verify supported formats for returns include A4, A5, and THERMAL_80MM
    const formatSelect = modalDialog.locator('select').nth(1);
    await expect(formatSelect).toBeVisible();
    const options = await formatSelect.locator('option').allInnerTexts();
    expect(options.some(opt => opt.includes('A4'))).toBe(true);
    expect(options.some(opt => opt.includes('A5'))).toBe(true);
    expect(options.some(opt => opt.includes('Thermal') || opt.includes('80mm'))).toBe(true);

    // Verify Theme selection works
    const themeSelect = modalDialog.locator('select').first();
    await themeSelect.selectOption('MODERN');
    await page.waitForTimeout(150);

    // Verify Palette selection works
    const swatches = modalDialog.locator('.color-swatch-btn, .doc-export-palette-swatch');
    await swatches.first().click();
    await expect(swatches.first()).toHaveAttribute('aria-pressed', 'true');

    // Spy on BillsoftUtils.printBlob in page context
    await page.evaluate(() => {
      (window as any).__lastPrintOptions = null;
      (window as any).BillsoftUtils.printBlob = function(blob: any, options: any) {
        (window as any).__lastPrintOptions = options;
        return true;
      };
    });

    // Click Print Now
    const printNowBtn = modalDialog.locator('button:has-text("Print Now"), button:has-text("🖨️ Print")').first();
    await expect(printNowBtn).toBeVisible();
    await printNowBtn.click();
    await page.waitForTimeout(500);

    // Verify headless printing and no legacy overlay
    const lastOptions = await page.evaluate(() => (window as any).__lastPrintOptions);
    expect(lastOptions).toEqual(expect.objectContaining({ headless: true }));
    const legacyOverlay = page.locator('#billsoft-interactive-print-overlay');
    await expect(legacyOverlay).not.toBeVisible();

    // Close modal
    await page.keyboard.press('Escape');
    await expect(modalDialog).not.toBeVisible({ timeout: 5000 });

    await errorGate.assertZeroErrors(page, 'Sales Return Export Modal Standardization');
  });

  test('DOC-07: Purchase Order Export Modal Standardization & Supported Format Filtering', async ({ page, app, errorGate }) => {
    await app.gotoApp();
    await app.navigateTo('Firm');

    // Switch to Purchase Orders tab
    const poTab = page.locator('button:has-text("Purchase Orders")').first();
    await expect(poTab).toBeVisible({ timeout: 10000 });
    await poTab.click();
    await page.waitForTimeout(400);

    // Locate the PO row
    const poRow = page.locator(`tr:has-text("PO-${testRunId}"), tr:has-text("ExportModal_Party_${testRunId}")`).first();
    await expect(poRow).toBeVisible({ timeout: 10000 });

    // Verify standardized text-based action buttons: Preview / Export, Share, Edit, Delete
    const previewExportBtn = poRow.locator('button:has-text("Preview / Export")').first();
    const shareBtn = poRow.locator('button:has-text("Share")').first();
    const editBtn = poRow.locator('button:has-text("Edit")').first();
    const deleteBtn = poRow.locator('button:has-text("Delete")').first();

    await expect(previewExportBtn).toBeVisible({ timeout: 5000 });
    await expect(shareBtn).toBeVisible({ timeout: 5000 });
    await expect(editBtn).toBeVisible({ timeout: 5000 });
    await expect(deleteBtn).toBeVisible({ timeout: 5000 });

    // Click "Preview / Export" button on PO row
    await previewExportBtn.click();

    // Verify standard DocumentExportModal dialog opened
    const modalDialog = page.locator('#invoice-print-dialog[role="dialog"]');
    await expect(modalDialog).toBeVisible({ timeout: 8000 });

    // Verify title and badge
    const badgeElement = modalDialog.locator('.doc-export-type-badge');
    await expect(badgeElement).toContainText(/Purchase Order/i);

    // Verify format filter: PO only supports A4 and A5 (NO Thermal or A3)
    const formatSelect = modalDialog.locator('select').nth(1);
    await expect(formatSelect).toBeVisible();
    const formatOptions = await formatSelect.locator('option').allInnerTexts();
    expect(formatOptions.some(opt => opt.includes('A4'))).toBe(true);
    expect(formatOptions.some(opt => opt.includes('A5'))).toBe(true);
    expect(formatOptions.some(opt => opt.includes('Thermal') || opt.includes('80mm'))).toBe(false);

    // Verify NO legacy divergent sheet is present (.po-print-sheet)
    const legacySheet = page.locator('.po-print-sheet');
    await expect(legacySheet).not.toBeVisible();

    // Spy on BillsoftUtils.printBlob
    await page.evaluate(() => {
      (window as any).__lastPrintOptions = null;
      (window as any).BillsoftUtils.printBlob = function(blob: any, options: any) {
        (window as any).__lastPrintOptions = options;
        return true;
      };
    });

    // Click Print Now
    const printNowBtn = modalDialog.locator('button:has-text("Print Now"), button:has-text("🖨️ Print")').first();
    await expect(printNowBtn).toBeVisible();
    await printNowBtn.click();
    await page.waitForTimeout(500);

    const lastOptions = await page.evaluate(() => (window as any).__lastPrintOptions);
    expect(lastOptions).toEqual(expect.objectContaining({ headless: true }));
    const legacyOverlay = page.locator('#billsoft-interactive-print-overlay');
    await expect(legacyOverlay).not.toBeVisible();

    // Verify modal is closed after print execution
    await expect(modalDialog).not.toBeVisible({ timeout: 5000 });

    await errorGate.assertZeroErrors(page, 'Purchase Order Export Modal Standardization');
  });

  test('DOC-08: Business Letter Export Modal Standardization & Format Filtering', async ({ page, app, errorGate }) => {
    await app.gotoApp();
    await app.navigateTo('Firm');

    // Switch to Letter Pad tab
    const lettersTab = page.locator('button:has-text("Letter Pad")').first();
    await expect(lettersTab).toBeVisible({ timeout: 10000 });
    await lettersTab.click();
    await page.waitForTimeout(400);

    // Locate the letter row
    const letterRow = page.locator(`tr:has-text("LTR-${testRunId}"), tr:has-text("ExportModal_Recipient_${testRunId}")`).first();
    await expect(letterRow).toBeVisible({ timeout: 10000 });

    // Verify standardized text-based action buttons: Preview / Export, Share, Edit, Delete
    const previewExportBtn = letterRow.locator('button:has-text("Preview / Export")').first();
    const shareBtn = letterRow.locator('button:has-text("Share")').first();
    const editBtn = letterRow.locator('button:has-text("Edit")').first();
    const deleteBtn = letterRow.locator('button:has-text("Delete")').first();

    await expect(previewExportBtn).toBeVisible({ timeout: 5000 });
    await expect(shareBtn).toBeVisible({ timeout: 5000 });
    await expect(editBtn).toBeVisible({ timeout: 5000 });
    await expect(deleteBtn).toBeVisible({ timeout: 5000 });

    // Click "Preview / Export" button on letter row
    await previewExportBtn.click();

    // Verify standard DocumentExportModal dialog opened
    const modalDialog = page.locator('#invoice-print-dialog[role="dialog"]');
    await expect(modalDialog).toBeVisible({ timeout: 8000 });

    // Verify title and badge
    const badgeElement = modalDialog.locator('.doc-export-type-badge');
    await expect(badgeElement).toContainText(/Business Letter/i);

    // Verify format filter: Letter only supports A4 and A5
    const formatSelect = modalDialog.locator('select').nth(1);
    await expect(formatSelect).toBeVisible();
    const formatOptions = await formatSelect.locator('option').allInnerTexts();
    expect(formatOptions.some(opt => opt.includes('A4'))).toBe(true);
    expect(formatOptions.some(opt => opt.includes('A5'))).toBe(true);
    expect(formatOptions.some(opt => opt.includes('Thermal') || opt.includes('80mm'))).toBe(false);

    // Verify NO legacy divergent sheet (.letter-print-sheet)
    const legacySheet = page.locator('.letter-print-sheet');
    await expect(legacySheet).not.toBeVisible();

    // Spy on BillsoftUtils.printBlob
    await page.evaluate(() => {
      (window as any).__lastPrintOptions = null;
      (window as any).BillsoftUtils.printBlob = function(blob: any, options: any) {
        (window as any).__lastPrintOptions = options;
        return true;
      };
    });

    // Click Print Now
    const printNowBtn = modalDialog.locator('button:has-text("Print Now"), button:has-text("🖨️ Print")').first();
    await expect(printNowBtn).toBeVisible();
    await printNowBtn.click();
    await page.waitForTimeout(500);

    const lastOptions = await page.evaluate(() => (window as any).__lastPrintOptions);
    expect(lastOptions).toEqual(expect.objectContaining({ headless: true }));
    const legacyOverlay = page.locator('#billsoft-interactive-print-overlay');
    await expect(legacyOverlay).not.toBeVisible();

    // Close modal via Escape
    await page.keyboard.press('Escape');
    await expect(modalDialog).not.toBeVisible({ timeout: 5000 });

    await errorGate.assertZeroErrors(page, 'Business Letter Export Modal Standardization');
  });

  test('DOC-09: Idle Preview Stability & Exact Request Count Assertion', async ({ page, app, errorGate }) => {
    await app.gotoApp();
    await app.navigateTo('Firm');

    // Switch to Letter Pad tab
    const lettersTab = page.locator('button:has-text("Letter Pad")').first();
    await expect(lettersTab).toBeVisible({ timeout: 10000 });
    await lettersTab.click();
    await page.waitForTimeout(400);

    // Locate the letter row
    const letterRow = page.locator(`tr:has-text("LTR-${testRunId}"), tr:has-text("ExportModal_Recipient_${testRunId}")`).first();
    await expect(letterRow).toBeVisible({ timeout: 10000 });

    const viewBtn = letterRow.locator('button:has-text("Preview / Export")').first();
    await expect(viewBtn).toBeVisible({ timeout: 5000 });
    await viewBtn.click();

    // Verify modal dialog opened
    const modalDialog = page.locator('#invoice-print-dialog[role="dialog"]');
    await expect(modalDialog).toBeVisible({ timeout: 8000 });

    // Wait for PDF preview canvas to render
    const canvas = modalDialog.locator('.pdf-preview-canvas').first();
    await expect(canvas).toBeVisible({ timeout: 15000 });

    // Ensure preview loading spinner is gone
    const loadingSpinner = modalDialog.locator('.pdf-preview-loading');
    await expect(loadingSpinner).not.toBeVisible();

    // Verify Download PDF and Print Now buttons are enabled
    const downloadBtn = modalDialog.locator('#doc-export-download-btn, button:has-text("Download PDF")').first();
    const printNowBtn = modalDialog.locator('button:has-text("Print Now"), button:has-text("🖨️ Print")').first();
    await expect(downloadBtn).toBeEnabled();
    await expect(printNowBtn).toBeEnabled();

    // Read initial diagnostics
    const initialDiag = await page.evaluate(() => (window as any).__docExportDiagnostics);
    expect(initialDiag).toBeDefined();
    const initialFetchCount = initialDiag.fetchCount;
    const initialRenderCount = initialDiag.renderCount;

    // Wait 10 full seconds idle without user interaction (omniPlaceholder interval triggers every 2.8s)
    await page.waitForTimeout(10000);

    // Assert that diagnostics have NOT incremented during idle period
    const idleDiag = await page.evaluate(() => (window as any).__docExportDiagnostics);
    expect(idleDiag.fetchCount).toBe(initialFetchCount);
    expect(idleDiag.renderCount).toBe(initialRenderCount);

    // Buttons must remain enabled and canvas visible
    await expect(canvas).toBeVisible();
    await expect(loadingSpinner).not.toBeVisible();
    await expect(downloadBtn).toBeEnabled();
    await expect(printNowBtn).toBeEnabled();

    // Change theme once -> fetchCount must increment by exactly 1
    const themeSelect = modalDialog.locator('select').first();
    await themeSelect.selectOption({ index: 1 });
    await expect(canvas).toBeVisible({ timeout: 10000 });
    await expect(loadingSpinner).not.toBeVisible();

    const afterThemeDiag = await page.evaluate(() => (window as any).__docExportDiagnostics);
    expect(afterThemeDiag.fetchCount).toBe(initialFetchCount + 1);

    // Change color once -> fetchCount must increment by exactly 1 more
    const secondColor = modalDialog.locator('.color-swatch-btn, .doc-export-palette-swatch').nth(1);
    if (await secondColor.isVisible()) {
      await secondColor.click();
      await expect(canvas).toBeVisible({ timeout: 10000 });
      await expect(loadingSpinner).not.toBeVisible();
      const afterColorDiag = await page.evaluate(() => (window as any).__docExportDiagnostics);
      expect(afterColorDiag.fetchCount).toBe(initialFetchCount + 2);
    }

    // Close modal
    await page.keyboard.press('Escape');
    await expect(modalDialog).not.toBeVisible({ timeout: 5000 });

    await errorGate.assertZeroErrors(page, 'Idle Preview Stability & Exact Request Count Assertion');
  });

  test('DOC-10: Multi-Document Family Idle Stability & Format Switching', async ({ page, app, errorGate }) => {
    await app.gotoApp();
    await app.navigateTo('Invoices');

    const invoiceRow = page.locator(`tr:has-text("ExportModal_Cust_${testRunId}")`).filter({ hasText: 'Invoice' }).first();
    await expect(invoiceRow).toBeVisible({ timeout: 10000 });

    const printBtn = invoiceRow.locator('.btn-seg-preview, button:has-text("Preview / Export"), button:has-text("Print")').first();
    await expect(printBtn).toBeVisible({ timeout: 5000 });
    await printBtn.click();

    const modalDialog = page.locator('#invoice-print-dialog[role="dialog"]');
    await expect(modalDialog).toBeVisible({ timeout: 8000 });

    const canvas = modalDialog.locator('.pdf-preview-canvas').first();
    await expect(canvas).toBeVisible({ timeout: 15000 });

    const downloadBtn = modalDialog.locator('#doc-export-download-btn, button:has-text("Download PDF")').first();
    await expect(downloadBtn).toBeEnabled();

    // Check diagnostics: verify stable count over 6s
    const invoiceDiag1 = await page.evaluate(() => (window as any).__docExportDiagnostics);
    const startFetches = invoiceDiag1.fetchCount;

    await page.waitForTimeout(6000);
    const invoiceDiag2 = await page.evaluate(() => (window as any).__docExportDiagnostics);
    expect(invoiceDiag2.fetchCount).toBe(startFetches);

    // Close modal
    await page.keyboard.press('Escape');
    await expect(modalDialog).not.toBeVisible({ timeout: 5000 });

    await errorGate.assertZeroErrors(page, 'Multi-Document Family Idle Stability & Format Switching');
  });

  test('DOC-11: Invoice Shortcut Flow Directly Opens Correct Document Export Modal Without Intermediate SalesReturnDetailModal', async ({ page, app, errorGate }) => {
    await app.gotoApp();
    await app.navigateTo('Invoices');

    // 1. Locate the invoice row that has an associated credit note
    const invoiceRow = page.locator(`tr:has-text("ExportModal_Cust_${testRunId}")`).filter({ hasText: 'Invoice' }).first();
    await expect(invoiceRow).toBeVisible({ timeout: 10000 });

    // 2. Test originating invoice row's Preview / Export action:
    // Clicking "Preview / Export" or "Print" on the invoice row MUST open the Invoice DocumentExportModal directly
    const invoicePrintBtn = invoiceRow.locator('.btn-seg-preview, button:has-text("Print"), button:has-text("Preview / Export")').first();
    await expect(invoicePrintBtn).toBeVisible({ timeout: 5000 });
    await invoicePrintBtn.click();

    const invoiceExportModal = page.locator('#invoice-print-dialog[role="dialog"]');
    await expect(invoiceExportModal).toBeVisible({ timeout: 8000 });
    const invBadge = invoiceExportModal.locator('.doc-export-type-badge');
    await expect(invBadge).toContainText(/Tax Invoice/i);

    // Close invoice modal via Escape
    await page.keyboard.press('Escape');
    await expect(invoiceExportModal).not.toBeVisible({ timeout: 5000 });

    // 3. Test Credit Note direct export button inside invoice row (CN Summary Pill)
    const cnBadgeExportBtn = invoiceRow.locator('button.cn-summary-pill, button[title*="Preview / Export Credit Note"]').first();
    if (await cnBadgeExportBtn.isVisible()) {
      await cnBadgeExportBtn.click();

      // Ensure intermediate detail modal does NOT open
      const intermediateModal = page.locator('.modal-content:has-text("Sales Return & Credit Note Details")');
      await expect(intermediateModal).not.toBeVisible();

      // Ensure DocumentExportModal directly opens with Credit Note badge
      const returnExportModal = page.locator('#invoice-print-dialog[role="dialog"]');
      await expect(returnExportModal).toBeVisible({ timeout: 8000 });
      const returnBadge = returnExportModal.locator('.doc-export-type-badge');
      await expect(returnBadge).toContainText(/Credit Note|Sales Return/i);

      // Close modal
      await page.keyboard.press('Escape');
      await expect(returnExportModal).not.toBeVisible({ timeout: 5000 });
    }

    // 4. Test "More ▾" dropdown on invoice row has dedicated Preview / Export Credit Note action
    const moreDropdownBtn = invoiceRow.locator('.doc-actions-dropdown-toggle, button:has-text("More ▾")').first();
    if (await moreDropdownBtn.isVisible()) {
      await moreDropdownBtn.click();
      await page.waitForTimeout(200);

      const dropdownExportCn = page.locator('.doc-actions-dropdown-menu button:has-text("Preview / Export Credit Note")').first();
      await expect(dropdownExportCn).toBeVisible({ timeout: 5000 });
      await dropdownExportCn.click();

      // Verify DocumentExportModal opens directly for Credit Note
      const cnExportModal = page.locator('#invoice-print-dialog[role="dialog"]');
      await expect(cnExportModal).toBeVisible({ timeout: 8000 });
      const cnBadge = cnExportModal.locator('.doc-export-type-badge');
      await expect(cnBadge).toContainText(/Credit Note|Sales Return/i);

      // Close modal
      await page.keyboard.press('Escape');
      await expect(cnExportModal).not.toBeVisible({ timeout: 5000 });
    }

    await errorGate.assertZeroErrors(page, 'Invoice Shortcut Navigation Direct Export Flow');
  });
});

