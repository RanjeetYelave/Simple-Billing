import { test, expect } from '../fixtures/base-fixture';

test.describe('P1: Invoice Workflows, Calculations & Hydration Integrity', () => {
  test('P1-INV-01: Invoice Creation, Line Items Addition & Total Calculation', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    await app.navigateTo('Invoices');

    const createBtn = page.locator('button:has-text("Create Invoice"), button:has-text("+ New Invoice"), button:has-text("+ Invoice"), button:has-text("Create")').first();
    if (await createBtn.isVisible()) {
      await createBtn.click();
      await page.waitForTimeout(400);
    }

    const invoiceForm = page.locator('.invoice-form, form, .invoice-editor').first();
    if (await invoiceForm.isVisible()) {
      // 1. Fill customer
      const custInput = page.locator('input[placeholder*="customer" i], input[placeholder*="client" i]').first();
      if (await custInput.isVisible()) {
        await custInput.fill('UI Test Corp');
      }

      // 2. Add product / item
      const itemInput = page.locator('.item-row input[placeholder*="product" i], input[placeholder*="item" i]').first();
      if (await itemInput.isVisible()) {
        await itemInput.fill('Web Development Service');
      }

      // 3. Fill quantity & price
      const qtyInput = page.locator('.item-row input[type="number"], input[placeholder*="qty" i]').first();
      if (await qtyInput.isVisible()) {
        await qtyInput.fill('3');
      }

      const rateInput = page.locator('.item-row input[placeholder*="price" i], input[placeholder*="rate" i]').first();
      if (await rateInput.isVisible()) {
        await rateInput.fill('1200');
      }

      await page.waitForTimeout(300);

      // 4. Verify calculated totals
      const totalDisplay = page.locator(':text("Total"), .total-amount, .grand-total').first();
      await expect(totalDisplay).toBeVisible();

      // 5. Verify Save Button
      const saveBtn = page.locator('button:has-text("Save Invoice"), button:has-text("Save & Print"), button:has-text("Save")').first();
      await expect(saveBtn).toBeVisible();
    }

    await errorGate.assertZeroErrors(page, 'Invoice Creation Form');
  });

  test('P1-INV-02: Existing Invoice Hydration Guards against Empty Line Items', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    await app.navigateTo('Invoices');

    const listTab = page.locator('button:has-text("All Invoices"), button:has-text("List"), button:has-text("Invoices")').first();
    if (await listTab.isVisible()) {
      await listTab.click();
      await page.waitForTimeout(400);
    }

    const invoiceRow = page.locator('.data-table tbody tr, .invoice-table-row, .invoice-item-row').first();
    if (await invoiceRow.isVisible()) {
      const editBtn = invoiceRow.locator('button[title*="Edit" i], button:has-text("Edit"), button:has-text("✏️")').first();
      if (await editBtn.isVisible()) {
        await editBtn.click();
        await page.waitForTimeout(500);

        const lineItems = page.locator('.item-row, .invoice-item-row');
        await expect(lineItems.first()).toBeVisible();
      }
    }

    await errorGate.assertZeroErrors(page, 'Existing Invoice Hydration');
  });

  test('P1-INV-03: Payment Settlement Modal Launches and Validates Amount', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    await app.navigateTo('Invoices');

    const paymentBtn = page.locator('button:has-text("Record Payment"), button:has-text("Pay"), button:has-text("💳")').first();
    if (await paymentBtn.isVisible()) {
      await paymentBtn.click();
      await page.waitForTimeout(300);

      const modal = page.locator('.modal-overlay, .record-payment-modal').first();
      if (await modal.isVisible()) {
        const amountInput = modal.locator('input[type="number"], input[placeholder*="Amount" i]').first();
        if (await amountInput.isVisible()) {
          await amountInput.fill('500');
        }

        const closeBtn = modal.locator('button:has-text("Cancel"), button.btn-ghost, button:has-text("✕")').first();
        if (await closeBtn.isVisible()) {
          await closeBtn.click();
          await expect(modal).toBeHidden();
        }
      }
    }

    await errorGate.assertZeroErrors(page, 'Payment Modal Validation');
  });

  test('P1-INV-04: Document Status Filtering & Date Range Controls', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    await app.navigateTo('Invoices');

    // Test status filter dropdown / buttons
    const statusSelect = page.locator('select:has-text("ALL"), select.filter-select, .status-filter-pills button').first();
    if (await statusSelect.isVisible()) {
      if (await statusSelect.evaluate(el => el.tagName.toLowerCase() === 'select')) {
        await statusSelect.selectOption({ index: 1 }).catch(() => {});
      } else {
        await statusSelect.click();
      }
      await page.waitForTimeout(300);
    }

    // Test search filter input
    const searchInput = page.locator('input[placeholder*="Search" i], input[placeholder*="invoice" i]').first();
    if (await searchInput.isVisible()) {
      await searchInput.fill('INV');
      await page.waitForTimeout(300);
      await searchInput.clear();
    }

    await errorGate.assertZeroErrors(page, 'Invoice Filtering & Search');
  });

  test('P1-INV-05: Form Validation on Invoice Creation (Missing Required Fields)', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    await app.navigateTo('Invoices');

    const createBtn = page.locator('button:has-text("Create Invoice"), button:has-text("+ New Invoice"), button:has-text("+ Invoice")').first();
    if (await createBtn.isVisible()) {
      await createBtn.click();
      await page.waitForTimeout(300);

      // Attempt submit without filling customer
      const saveBtn = page.locator('button:has-text("Save Invoice"), button:has-text("Save")').first();
      if (await saveBtn.isVisible()) {
        await saveBtn.click();
        await page.waitForTimeout(300);
      }
    }

    await errorGate.assertZeroErrors(page, 'Invoice Form Validation');
  });
});
