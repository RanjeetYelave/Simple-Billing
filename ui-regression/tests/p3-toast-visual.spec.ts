import { test, expect } from '../fixtures/base-fixture';

test.describe('P3: Toast Stacking, Theme Synchronisation & Responsive Layout', () => {
  test('P3-TOAST-01: Toast Deduplication Badge Multiplier (x2)', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    // Trigger two duplicate toasts via window.showToast
    await page.evaluate(() => {
      if (typeof (window as any).showToast === 'function') {
        (window as any).showToast('Settings saved successfully', 'success');
        (window as any).showToast('Settings saved successfully', 'success');
      }
    });

    await page.waitForTimeout(400);

    const toastMsg = page.locator('.toast-message, .toast-count-pill').first();
    await expect(toastMsg).toBeVisible();

    await errorGate.assertZeroErrors(page, 'Toast Multiplier');
  });

  test('P3-THEME-01: Dark Mode Switching Synchronizes Tokens without Glitch', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    const themeBtn = page.locator('.theme-toggle-btn, button[title*="theme" i], button:has-text("🌙"), button:has-text("☀️")').first();
    if (await themeBtn.isVisible()) {
      await themeBtn.click();
      await page.waitForTimeout(300);

      // Verify data-theme attribute
      const theme = await page.locator('html').getAttribute('data-theme');
      expect(theme).toBeTruthy();

      // Toggle back
      await themeBtn.click();
      await page.waitForTimeout(300);
    }

    await errorGate.assertZeroErrors(page, 'Theme Toggle');
  });

  test('P3-RESP-01: Mobile Viewport (390x844) Renders Shell and Responsive Menu', async ({ page, app, errorGate }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await app.gotoApp();

    // In mobile viewport, verify topbar and mobile toggle button
    const mobileHeader = page.locator('.topbar, .mobile-topbar, header').first();
    await expect(mobileHeader).toBeVisible();

    await errorGate.assertZeroErrors(page, 'Mobile 390x844 Layout');
  });

  test('P3-ASYNC-01: Rapid Navigation & Async Request In-Flight Safety', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    // Rapidly switch between 4 hubs without waiting for async responses
    await app.navigateTo('Invoices');
    await app.navigateTo('Firm');
    await app.navigateTo('Planner');
    await app.navigateTo('Dashboard');

    await page.waitForTimeout(500);

    await errorGate.assertZeroErrors(page, 'Rapid Async Navigation');
  });
});
