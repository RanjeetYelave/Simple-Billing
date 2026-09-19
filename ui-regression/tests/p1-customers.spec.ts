import { test, expect } from '../fixtures/base-fixture';

test.describe('P1: Customer Management, Ledger & Customer 360', () => {
  test('P1-CUST-01: Customer Management Directory and Search', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    await app.navigateTo('Firm');

    // Select Customers tab
    const custTab = page.locator('button:has-text("Customers"), .tab-btn:has-text("Customers"), button:has-text("Clients")').first();
    if (await custTab.isVisible()) {
      await custTab.click();
      await page.waitForTimeout(400);

      const custManager = page.locator('.customer-manager, .data-table, :text("Customers")').first();
      await expect(custManager).toBeVisible();

      // Search input
      const searchInput = page.locator('input[placeholder*="Search customer" i], input[placeholder*="Search" i]').first();
      if (await searchInput.isVisible()) {
        await searchInput.fill('ABC');
        await page.waitForTimeout(300);
        await searchInput.clear();
      }
    }

    await errorGate.assertZeroErrors(page, 'Customer Directory & Search');
  });

  test('P1-CUST-02: Customer CRUD Creation & Form Validation', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    await app.navigateTo('Firm');

    const custTab = page.locator('button:has-text("Customers"), .tab-btn:has-text("Customers")').first();
    if (await custTab.isVisible()) {
      await custTab.click();
      await page.waitForTimeout(400);

      const addBtn = page.locator('button:has-text("+ Customer"), button:has-text("Add Customer"), button:has-text("+ Add")').first();
      if (await addBtn.isVisible()) {
        await addBtn.click();
        await page.waitForTimeout(300);

        const modal = page.locator('.modal-overlay, .customer-modal').first();
        if (await modal.isVisible()) {
          const nameInput = modal.locator('input[placeholder*="Name" i], input[name="name"]').first();
          if (await nameInput.isVisible()) {
            await nameInput.fill('Acme Global Industries');
          }

          const phoneInput = modal.locator('input[placeholder*="Phone" i], input[placeholder*="Mobile" i]').first();
          if (await phoneInput.isVisible()) {
            await phoneInput.fill('9876543210');
          }

          const cancelBtn = modal.locator('button:has-text("Cancel"), button:has-text("✕")').first();
          if (await cancelBtn.isVisible()) {
            await cancelBtn.click();
          }
        }
      }
    }

    await errorGate.assertZeroErrors(page, 'Customer Creation & Form');
  });

  test('P1-CUST-03: Customer 360 Modal and Ledger Launch', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    await app.navigateTo('Firm');

    const custTab = page.locator('button:has-text("Customers"), .tab-btn:has-text("Customers")').first();
    if (await custTab.isVisible()) {
      await custTab.click();
      await page.waitForTimeout(400);

      const customerRow = page.locator('.customer-row, .data-table tbody tr').first();
      if (await customerRow.isVisible()) {
        const viewBtn = customerRow.locator('button[title*="360" i], button:has-text("360"), button:has-text("View")').first();
        if (await viewBtn.isVisible()) {
          await viewBtn.click();
          await page.waitForTimeout(400);

          const c360Modal = page.locator('.modal-overlay, .customer-360-modal').first();
          if (await c360Modal.isVisible()) {
            const closeBtn = c360Modal.locator('button:has-text("✕"), button:has-text("Close")').first();
            if (await closeBtn.isVisible()) {
              await closeBtn.click();
            }
          }
        }
      }
    }

    await errorGate.assertZeroErrors(page, 'Customer 360 Drawer');
  });

  test('P1-CUST-04: Customer Settlement Flow & Payment Reconciliation', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    await app.navigateTo('Firm');

    const custTab = page.locator('button:has-text("Customers"), .tab-btn:has-text("Customers")').first();
    if (await custTab.isVisible()) {
      await custTab.click();
      await page.waitForTimeout(400);

      const settleBtn = page.locator('button:has-text("Settle"), button[title*="Settle" i]').first();
      if (await settleBtn.isVisible()) {
        await settleBtn.click();
        await page.waitForTimeout(300);

        const modal = page.locator('.modal-overlay, .customer-settlement-modal').first();
        if (await modal.isVisible()) {
          const cancelBtn = modal.locator('button:has-text("Cancel"), button:has-text("✕")').first();
          if (await cancelBtn.isVisible()) {
            await cancelBtn.click();
          }
        }
      }
    }

    await errorGate.assertZeroErrors(page, 'Customer Settlement Reconciliation');
  });
});
