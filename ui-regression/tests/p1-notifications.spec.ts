import { test, expect } from '../fixtures/base-fixture';

test.describe('P1: Notifications & Inbox Action Dispatch', () => {
  test('P1-NOTIF-01: Notification Bell Popover & Inbox Hub Navigation', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    const bellBtn = page.locator('.notif-bell-btn, button[title*="Notification" i], button:has-text("🔔")').first();
    await expect(bellBtn).toBeVisible();
    await bellBtn.click();
    await page.waitForTimeout(400);

    const notifPopup = page.locator('.notif-popup, .notifications-dropdown, .inbox-page, .modal-overlay').first();
    if (await notifPopup.isVisible()) {
      await page.keyboard.press('Escape');
    }

    await errorGate.assertZeroErrors(page, 'Notification Bell Interaction');
  });

  test('P1-NOTIF-02: Notification Action Button Navigation Pipeline', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    await app.navigateTo('Inbox');

    const notifItem = page.locator('.notification-item, .inbox-message-row').first();
    if (await notifItem.isVisible()) {
      const actionBtn = notifItem.locator('button:has-text("View"), button:has-text("Open"), button.btn-action').first();
      if (await actionBtn.isVisible()) {
        await actionBtn.click();
        await page.waitForTimeout(500);
      }
    }

    await errorGate.assertZeroErrors(page, 'Notification Action Dispatch');
  });

  test('P1-NOTIF-03: Inbox Subtab and Filter Switching', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    await app.navigateTo('Inbox');

    const filterBtn = page.locator('button:has-text("Unread"), button:has-text("Alerts"), .tab-btn').first();
    if (await filterBtn.isVisible()) {
      await filterBtn.click();
      await page.waitForTimeout(300);
    }

    await errorGate.assertZeroErrors(page, 'Inbox Filter Switching');
  });
});
