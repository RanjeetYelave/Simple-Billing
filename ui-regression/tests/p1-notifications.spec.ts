import { test, expect } from '../fixtures/base-fixture';

test.describe('P1: Notifications & Inbox Action Dispatch', () => {
  const testRunId = Date.now();
  const notifSubject = `Audit_Subject_${testRunId}`;

  test('P1-NOTIF-01: Notification Bell Popover & Inbox Hub Navigation', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    const bellBtn = page.locator('.notif-bell-btn, button[title*="Notification" i], button:has-text("🔔")').first();
    await expect(bellBtn).toBeVisible({ timeout: 10000 });
    await bellBtn.click();
    await page.waitForTimeout(400);

    await errorGate.assertZeroErrors(page, 'Notification Bell Interaction');
  });

  test('P1-NOTIF-02: Inbox Compose, Search and List Verification', async ({ page, app, errorGate }) => {
    await app.gotoApp();
    await app.navigateTo('Inbox');

    // Verify Inbox List Pane mounts
    const searchInput = page.locator('.inbox-list-pane input[placeholder*="Search notifications" i]').first();
    await expect(searchInput).toBeVisible({ timeout: 10000 });

    // Open Compose modal
    const composeBtn = page.locator('.inbox-list-pane button:has-text("+ Compose")').first();
    await expect(composeBtn).toBeVisible({ timeout: 5000 });
    await composeBtn.click();
    await page.waitForTimeout(400);

    const modal = page.locator('.modal-overlay').first();
    await expect(modal).toBeVisible({ timeout: 5000 });

    // Fill Subject and Body
    const subjectInput = modal.locator('input[placeholder*="Subject" i]').first();
    await expect(subjectInput).toBeVisible();
    await subjectInput.fill(notifSubject);

    const bodyInput = modal.locator('textarea[placeholder*="Message Body" i]').first();
    await expect(bodyInput).toBeVisible();
    await bodyInput.fill(`Detailed notification body content ${testRunId}`);

    // Send Message
    const sendBtn = modal.locator('button:has-text("Send")').first();
    await expect(sendBtn).toBeVisible();
    await sendBtn.click();
    await page.waitForTimeout(600);

    // Search for our created notification
    await searchInput.fill(notifSubject);
    await page.waitForTimeout(400);

    // Verify notification item is displayed
    const notifItem = page.locator(`:visible:text("${notifSubject}")`).first();
    await expect(notifItem).toBeVisible({ timeout: 10000 });

    await errorGate.assertZeroErrors(page, 'Inbox Notification List & Search');
  });

  test('P1-NOTIF-03: Inbox Subtab and Filter Switching', async ({ page, app, errorGate }) => {
    await app.gotoApp();
    await app.navigateTo('Inbox');

    // Click Unread filter in inbox list pane
    const unreadFilter = page.locator('.inbox-list-pane button:has-text("Unread")').first();
    await expect(unreadFilter).toBeVisible({ timeout: 10000 });
    await unreadFilter.click();
    await page.waitForTimeout(300);

    // Click All filter in inbox list pane
    const allFilter = page.locator('.inbox-list-pane button:has-text("All")').first();
    await expect(allFilter).toBeVisible({ timeout: 5000 });
    await allFilter.click();
    await page.waitForTimeout(300);

    await errorGate.assertZeroErrors(page, 'Inbox Filter Switching');
  });
});
