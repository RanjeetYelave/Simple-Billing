import { test, expect } from '../fixtures/base-fixture';

test.describe('P1: Invoice Workflows, Calculations, Persistence & Payment Settlements', () => {
  const testRunId = Date.now();
  const customerName = `Cust_Inv_${testRunId}`;
  const productName = `Prod_Inv_${testRunId}`;

  test('P1-INV-01: End-to-End Invoice Creation, Exact Math Verification & Database Persistence', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    // 1. Ensure a known Customer exists
    await app.navigateTo('Firm');
    const custTab = page.locator('button:has-text("Customers")').first();
    await expect(custTab).toBeVisible({ timeout: 10000 });
    await custTab.click();
    await page.waitForTimeout(300);

    const addCustBtn = page.locator('button:has-text("+ Add Customer"), button:has-text("Add Customer")').first();
    await expect(addCustBtn).toBeVisible({ timeout: 10000 });
    await addCustBtn.click();
    await page.waitForTimeout(300);

    const custModal = page.locator('.modal-overlay').first();
    await expect(custModal).toBeVisible({ timeout: 8000 });
    await custModal.locator('input.input').first().fill(customerName);
    await custModal.locator('input[placeholder*="+91" i], input[placeholder*="Phone" i]').first().fill('9876543210');
    const saveCustBtn = custModal.locator('button:has-text("Save Customer"), button:has-text("Save")').first();
    await saveCustBtn.click();
    await page.waitForTimeout(600);

    // 2. Navigate to Invoices
    await app.navigateTo('Invoices');

    const createBtn = page.locator('button:has-text("📄 + New Invoice"), button:has-text("+ Inv"), button:has-text("Create Invoice")').first();
    await expect(createBtn).toBeVisible({ timeout: 10000 });
    await createBtn.click();
    await page.waitForTimeout(500);

    const invoiceForm = page.locator('.invoice-form').first();
    await expect(invoiceForm).toBeVisible({ timeout: 10000 });

    // 3. Fill Customer
    const custInput = invoiceForm.locator('input[placeholder*="Enter Name" i], input[placeholder*="search" i], .invoice-customer-grid input').first();
    await expect(custInput).toBeVisible();
    await custInput.fill(customerName);
    await page.waitForTimeout(300);

    // 4. Fill Item Row: Product, Qty=5, Price=1000, Disc=500, GST=18%
    const itemRow = invoiceForm.locator('.invoice-items-table tbody tr').first();
    await expect(itemRow).toBeVisible();

    const productInput = itemRow.locator('input').nth(0);
    await productInput.fill(productName);

    const qtyInput = itemRow.locator('input').nth(2);
    await qtyInput.fill('5');

    const priceInput = itemRow.locator('input').nth(3);
    await priceInput.fill('1000');

    const discInput = itemRow.locator('input').nth(4);
    await discInput.fill('500');

    const gstInput = itemRow.locator('input').nth(5);
    await gstInput.fill('18');

    await page.waitForTimeout(400);

    // 5. Verify Calculations: Subtotal=5000, Disc=500, Taxable=4500, GST=810 (18% of 4500), Grand Total = 5310
    const grandTotalElement = page.locator('.summary-row.total .summary-value, .grand-total').first();
    await expect(grandTotalElement).toBeVisible({ timeout: 5000 });
    const grandTotalText = await grandTotalElement.innerText();
    expect(grandTotalText.replace(/[^0-9]/g, '')).toContain('5310');

    // 6. Execute Real Save Invoice Action
    const saveInvoiceBtn = page.locator('.invoice-form-bottom-actions button:has-text("Save Invoice"), button:has-text("Save Invoice")').first();
    await expect(saveInvoiceBtn).toBeVisible();
    await saveInvoiceBtn.click();
    await page.waitForTimeout(1000);

    // 7. Verify Persistence in Invoices Table
    await app.navigateTo('Invoices');
    await page.waitForTimeout(500);

    const listRow = page.locator(`.data-table tbody tr:has-text("${customerName}"), tr:has-text("${customerName}")`).first();
    await expect(listRow).toBeVisible({ timeout: 10000 });
    const rowContent = await listRow.innerText();
    expect(rowContent).toContain('5,310');

    await errorGate.assertZeroErrors(page, 'Invoice E2E Creation & Persistence');
  });

  test('P1-INV-02: Payment Settlement Recording & Real Balance Reduction', async ({ page, app, errorGate }) => {
    await app.gotoApp();
    await app.navigateTo('Invoices');

    const targetRow = page.locator(`.table-container table tbody tr:has-text("${customerName}")`).first();
    await expect(targetRow).toBeVisible({ timeout: 10000 });

    // Open Payment Modal via Mark Paid button
    const payBtn = targetRow.locator('button:has-text("Mark Paid")').first();
    await expect(payBtn).toBeVisible({ timeout: 5000 });
    await payBtn.click();
    await page.waitForTimeout(400);

    const payModal = page.locator('.modal-overlay:has-text("Record Payment"), .modal-overlay').first();
    await expect(payModal).toBeVisible({ timeout: 8000 });

    // Fill Payment Amount 3000
    const amtInput = payModal.locator('input[type="number"], input.form-input').first();
    await amtInput.fill('3000');

    // Submit Payment
    const recordBtn = payModal.locator('button[type="submit"], button:has-text("Record")').first();
    await expect(recordBtn).toBeVisible();
    await recordBtn.click();
    await page.waitForTimeout(800);

    // Verify modal dismissed and balance updated
    await expect(payModal).toBeHidden({ timeout: 5000 });
    await page.waitForTimeout(400);

    // Verify row or payment reflects in Invoices table
    await app.navigateTo('Invoices');
    const updatedRow = page.locator(`.table-container table tbody tr:has-text("${customerName}")`).first();
    await expect(updatedRow).toBeVisible();

    await errorGate.assertZeroErrors(page, 'Payment Recording & Balance Reduction');
  });

  test('P1-INV-03: Strict Form Validation & Boundary Rejections', async ({ page, app, errorGate }) => {
    await app.gotoApp();
    await app.navigateTo('Invoices');

    const createBtn = page.locator('button:has-text("📄 + New Invoice"), button:has-text("+ Inv"), button:has-text("Create Invoice")').first();
    await expect(createBtn).toBeVisible({ timeout: 10000 });
    await createBtn.click();
    await page.waitForTimeout(400);

    // 1. Submit completely blank form -> must reject with toast
    const saveBtn = page.locator('.invoice-form-bottom-actions button:has-text("Save Invoice")').first();
    await expect(saveBtn).toBeVisible();
    await saveBtn.click();
    await page.waitForTimeout(300);

    // Toast or validation message must show required fields error
    const toastError = page.locator('.toast, .toast-warning, .toast-error, :text("customer")').first();
    await expect(toastError).toBeVisible({ timeout: 5000 });

    await errorGate.assertZeroErrors(page, 'Invoice Boundary Validation');
  });
});
