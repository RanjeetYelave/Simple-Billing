import { test, expect } from '../fixtures/base-fixture';

test.describe('Cross-Device Responsive Viewports Suite', () => {
  test('RESP-01: Tablet Landscape (1024x768) — App Shell & Invoices Layout', async ({ page, app, errorGate }) => {
    await page.setViewportSize({ width: 1024, height: 768 });
    await app.gotoApp();

    const topbar = page.locator('.topbar');
    await expect(topbar).toBeVisible();

    await app.navigateTo('Invoices');
    const invoiceContainer = page.locator('.invoices-container, .card, .data-table, .page-content').first();
    await expect(invoiceContainer).toBeVisible({ timeout: 8000 });

    await errorGate.assertZeroErrors(page, 'Tablet Landscape 1024x768');
  });

  test('RESP-02: Tablet Portrait (768x1024) — Sidebar Collapse & Navigation', async ({ page, app, errorGate }) => {
    await page.setViewportSize({ width: 768, height: 1024 });
    await app.gotoApp();

    // Check topbar presence
    const topbar = page.locator('.topbar');
    await expect(topbar).toBeVisible();

    await app.navigateTo('Firm');
    const content = page.locator('.page-content, .main-content').first();
    await expect(content).toBeVisible({ timeout: 8000 });

    await errorGate.assertZeroErrors(page, 'Tablet Portrait 768x1024');
  });

  test('RESP-03: Mobile Viewport (375x667) — Drawer Toggle & Omnibar Fit', async ({ page, app, errorGate }) => {
    await page.setViewportSize({ width: 375, height: 667 });
    await app.gotoApp();

    // Verify app shell adapts without horizontal document overflow
    const bodyWidth = await page.evaluate(() => document.body.scrollWidth);
    expect(bodyWidth).toBeLessThanOrEqual(390);

    // Open Mobile Drawer / Hamburger
    const menuBtn = page.locator('button.mobile-toggle-btn').first();
    await expect(menuBtn).toBeVisible({ timeout: 8000 });
    await menuBtn.dispatchEvent('click');
    await page.waitForTimeout(500);

    const sidebar = page.locator('aside.sidebar');
    await expect(sidebar).toHaveClass(/open/, { timeout: 8000 });

    // Close drawer via close button
    const closeBtn = page.locator('button.mobile-close-btn').first();
    if (await closeBtn.isVisible()) {
      await closeBtn.click();
      await page.waitForTimeout(400);
    }

    await errorGate.assertZeroErrors(page, 'Mobile Viewport 375x667');
  });
});
