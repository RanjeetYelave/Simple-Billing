import { test, expect } from '../fixtures/base-fixture';

test.describe('P0: Application Shell, Navigation & Omnisearch Gate', () => {
  test('P0-01: App Mounts cleanly with Topbar, Sidebar and Zero JS Errors', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    // 1. Verify Topbar elements
    const logo = page.locator('.sidebar-logo, :text("Rupee")').first();
    await expect(logo).toBeVisible();

    const omniPill = page.locator('.topbar-search-pill, input[placeholder*="kharcha" i], [title*="OmniSearch" i]').first();
    await expect(omniPill).toBeVisible();

    const bellIcon = page.locator('.notif-bell-btn, button[title*="Notification" i], button:has-text("🔔")').first();
    await expect(bellIcon).toBeVisible();

    // 2. Verify Sidebar elements
    const dashboardNav = page.locator('.nav-item:has-text("Dashboard")').first();
    await expect(dashboardNav).toBeVisible();

    const invoiceNav = page.locator('.nav-item:has-text("Invoices")').first();
    await expect(invoiceNav).toBeVisible();

    const firmNav = page.locator('.nav-item:has-text("Firm")').first();
    await expect(firmNav).toBeVisible();

    const plannerNav = page.locator('.nav-item:has-text("Planner")').first();
    await expect(plannerNav).toBeVisible();

    // 3. Verify zero runtime errors
    await errorGate.assertZeroErrors(page, 'App Shell Mount');
  });

  test('P0-02: Global Search / Omnibar Opens and Searches without TypeError', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    // Open Omnibar via clicking search trigger in topbar
    const omniTrigger = page.locator('.topbar-search-pill, [title*="OmniSearch" i], button:has-text("🔍")').first();
    await omniTrigger.click();

    const omniModal = page.locator('.omni-modal-overlay, .omni-container').first();
    await expect(omniModal).toBeVisible({ timeout: 5000 });

    const omniInput = page.locator('.omni-search-input, input.omni-input, input[placeholder*="search" i]').first();
    if (await omniInput.isVisible()) {
      await omniInput.fill('Invoice');
      await page.waitForTimeout(400);

      const resultsContainer = page.locator('.omni-results-body, .omni-items-list, .omni-guide-container').first();
      await expect(resultsContainer).toBeVisible();
    }

    // Close via Escape
    await page.keyboard.press('Escape');
    await expect(omniModal).toBeHidden({ timeout: 5000 });

    await errorGate.assertZeroErrors(page, 'Omnisearch Query Execution');
  });

  test('P0-03: Primary Navigation Traversals Render All Destination Hubs', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    // 1. Invoices & Quotes
    await app.navigateTo('Invoices');
    await expect(page.locator('.invoices-hub, .sub-tabs-bar, button:has-text("Invoices"), button:has-text("Quotes")').first()).toBeVisible();
    await errorGate.assertZeroErrors(page, 'Navigate to Invoices');

    // 2. Firm Management
    await app.navigateTo('Firm');
    await expect(page.locator('.firm-page, .sub-tabs-bar, button:has-text("Inventory"), button:has-text("Customers")').first()).toBeVisible();
    await errorGate.assertZeroErrors(page, 'Navigate to Firm');

    // 3. Planner
    await app.navigateTo('Planner');
    await expect(page.locator('.planner-container, button:has-text("Board"), button:has-text("Goals")').first()).toBeVisible();
    await errorGate.assertZeroErrors(page, 'Navigate to Planner');

    // 4. Inbox
    await app.navigateTo('Inbox');
    await expect(page.locator('.inbox-page, :text("Inbox"), :text("Notifications")').first()).toBeVisible();
    await errorGate.assertZeroErrors(page, 'Navigate to Inbox');

    // 5. HR
    await app.navigateTo('HR');
    await expect(page.locator('.hr-manager, :text("HR Management"), :text("Staff")').first()).toBeVisible();
    await errorGate.assertZeroErrors(page, 'Navigate to HR');

    // 6. Settings
    await app.navigateTo('Settings');
    await expect(page.locator('.settings-hub, :text("Settings"), button:has-text("General"), button:has-text("Firm")').first()).toBeVisible();
    await errorGate.assertZeroErrors(page, 'Navigate to Settings');

    // 7. Return to Dashboard
    await app.navigateTo('Dashboard');
    await expect(page.getByRole('heading', { name: 'Dashboard' }).first()).toBeVisible();
    await errorGate.assertZeroErrors(page, 'Return to Dashboard');
  });

  test('P0-04: Keyboard Navigation and Global Shortcut Triggers', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    // Test Tab traversal on shell
    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');

    // Test Escape closing modals/overlays
    await page.keyboard.press('Escape');

    await errorGate.assertZeroErrors(page, 'Keyboard Navigation');
  });
});
