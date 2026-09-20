import { test, expect } from '../fixtures/base-fixture';

test.describe('Announcements Feature Gate & Isolation', () => {
  test('ANN-01: Displays announcements card with multiple items when present', async ({ page, app, errorGate }) => {
    // Intercept announcements endpoint with sample items
    await page.route('**/api/announcements*', async route => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          'Welcome to RupeeCRM v2.4! Cloud sync & backup enhancements active.',
          'Scheduled maintenance window on Sunday 02:00 UTC.'
        ])
      });
    });

    await app.gotoApp();

    // Acknowledge startup modal if visible
    const okBtn = page.locator('#btn-acknowledge-announcements');
    try {
      await okBtn.waitFor({ state: 'visible', timeout: 3000 });
      await okBtn.click();
    } catch {
      // Modal not displayed or already dismissed
    }

    const card = page.locator('#dashboard-announcements-card');
    await expect(card).toBeVisible({ timeout: 6000 });

    // Verify title and counter badge
    await expect(card.locator('.announcements-card-title')).toContainText('Announcements');
    await expect(card.locator('.badge, .badge-info')).toHaveText('2');

    // Verify item texts
    const items = card.locator('.announcement-item');
    await expect(items).toHaveCount(2);
    await expect(items.nth(0)).toContainText('Welcome to RupeeCRM v2.4!');
    await expect(items.nth(1)).toContainText('Scheduled maintenance window');

    await errorGate.assertZeroErrors(page, 'Announcements Multiple Items Render');
  });

  test('ANN-02: Completely hides announcements card when empty', async ({ page, app, errorGate }) => {
    await page.route('**/api/announcements*', async route => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([])
      });
    });

    await app.gotoApp();

    const card = page.locator('#dashboard-announcements-card');
    await expect(card).toHaveCount(0);

    await errorGate.assertZeroErrors(page, 'Announcements Empty State Hiding');
  });

  test('ANN-03: Dashboard renders independently without waiting on slow announcements', async ({ page, app, errorGate }) => {
    // Delay announcements response by 1.2 seconds
    await page.route('**/api/announcements*', async route => {
      await new Promise(r => setTimeout(r, 1200));
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(['Delayed announcement message'])
      });
    });

    await app.gotoApp();

    // Main dashboard KPI or header should be visible before delayed announcement finishes
    const dashboardHeader = page.getByRole('heading', { name: 'Dashboard' }).first();
    await expect(dashboardHeader).toBeVisible({ timeout: 3000 });

    // Acknowledge startup modal once it arrives
    const okBtn = page.locator('#btn-acknowledge-announcements');
    try {
      await okBtn.waitFor({ state: 'visible', timeout: 3000 });
      await okBtn.click();
    } catch {
      // Modal not displayed
    }

    // After delay, announcement card is loaded
    await expect(page.locator('#dashboard-announcements-card')).toBeVisible({ timeout: 5000 });

    await errorGate.assertZeroErrors(page, 'Announcements Non-blocking Async Loading');
  });

  test('ANN-04: Gracefully handles empty / offline fallback without crashing or throwing', async ({ page, app, errorGate }) => {
    // Return empty list representing offline cached fallback
    await page.route('**/api/announcements*', async route => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([])
      });
    });

    await app.gotoApp();

    // Dashboard loads normally and card is omitted
    const dashboardHeader = page.getByRole('heading', { name: 'Dashboard' }).first();
    await expect(dashboardHeader).toBeVisible();

    const card = page.locator('#dashboard-announcements-card');
    await expect(card).toHaveCount(0);

    await errorGate.assertZeroErrors(page, 'Announcements Graceful Fallback');
  });

  test('ANN-05: Plain text announcements safely escape HTML and script tags (No XSS)', async ({ page, app, errorGate }) => {
    await page.route('**/api/announcements*', async route => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          '<script>window.__xss_vulnerable = true;</script><b>Notice:</b> System operational.'
        ])
      });
    });

    await app.gotoApp();

    // Acknowledge modal if open
    const okBtn = page.locator('#btn-acknowledge-announcements');
    try {
      await okBtn.waitFor({ state: 'visible', timeout: 3000 });
      await okBtn.click();
    } catch {
      // Modal not displayed
    }

    const card = page.locator('#dashboard-announcements-card');
    await expect(card).toBeVisible({ timeout: 5000 });
    await expect(card.locator('.announcement-item-text')).toContainText('<script>');

    const isVulnerable = await page.evaluate(() => (window as any).__xss_vulnerable === true);
    expect(isVulnerable).toBe(false);

    await errorGate.assertZeroErrors(page, 'Announcements XSS Safety Verification');
  });

  test('ANN-06: Settings > Software Updates provides Check for Announcements button with sync', async ({ page, app, errorGate }) => {
    let forceRefreshCalled = false;
    await page.route('**/api/announcements*', async route => {
      const url = route.request().url();
      if (url.includes('force=true')) {
        forceRefreshCalled = true;
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(['Fresh announcement synced from GitHub'])
      });
    });

    await app.gotoApp();

    // Dismiss startup modal if open before navigating to settings
    const okBtn = page.locator('#btn-acknowledge-announcements');
    try {
      await okBtn.waitFor({ state: 'visible', timeout: 3000 });
      await okBtn.click();
    } catch {
      // Modal not displayed
    }

    await app.navigateTo('Settings');

    // Click 'Software Updates' tab
    const updatesTab = page.locator('button:has-text("Software Updates")');
    await updatesTab.click();

    // Check for Announcements button
    const checkBtn = page.locator('button:has-text("Check for Announcements")');
    await expect(checkBtn).toBeVisible();

    await checkBtn.click();
    await expect(page.locator('.toast-card').filter({ hasText: 'Synced 1 active announcement' })).toBeVisible();
    expect(forceRefreshCalled).toBe(true);

    await errorGate.assertZeroErrors(page, 'Check for Announcements in Software Updates');
  });

  test('ANN-07: Displays Announcement Startup Modal upon detection and acknowledges to bottom on click', async ({ page, app, errorGate }) => {
    await page.route('**/api/announcements*', async route => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(['Critical server migration tonight at 10 PM.'])
      });
    });

    await app.gotoApp();

    // Startup Announcement modal should appear
    const modal = page.locator('#announcements-startup-modal');
    await expect(modal).toBeVisible({ timeout: 6000 });
    await expect(modal).toContainText('System Announcements');
    await expect(modal).toContainText('Critical server migration tonight at 10 PM.');

    // Click 'Okay, Understood' button
    const okBtn = page.locator('#btn-acknowledge-announcements');
    await expect(okBtn).toBeVisible();
    await okBtn.click();

    // Modal should disappear
    await expect(modal).toHaveCount(0);

    // Announcement should now sit at the bottom of Dashboard
    const bottomCard = page.locator('#dashboard-announcements-card');
    await expect(bottomCard).toBeVisible();
    await expect(bottomCard).toContainText('Critical server migration tonight at 10 PM.');

    await errorGate.assertZeroErrors(page, 'Announcements Startup Modal and Acknowledgment');
  });
});
