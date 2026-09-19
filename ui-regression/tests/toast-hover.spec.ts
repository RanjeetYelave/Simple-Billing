import { test, expect } from '../fixtures/base-fixture';

test.describe('Toast Notification Hover & Stack Deck Behavior', () => {
  test('Multiple sequential toasts (such as adding multiple goal savings) stack and expand smoothly on hover', async ({ app, page }) => {
    await app.gotoApp();
    
    // Accept EULA if gate is open
    const eulaBtn = page.locator('#eula-accept-btn');
    if (await eulaBtn.isVisible()) {
      await page.locator('#eula-agree-checkbox').check();
      await eulaBtn.click();
      await page.waitForTimeout(500);
    }

    // Trigger 5 sequential toast notifications with same message spaced 400ms apart
    // (Simulating user clicking quick save / add EMI 5 times)
    for (let i = 1; i <= 5; i++) {
      await page.evaluate(() => {
        (window as any).showToast('₹1,000 added to goal savings!', 'success');
      });
      await page.waitForTimeout(400);
    }

    // Verify toast dock exists and has stacked cards
    const toastDock = page.locator('.toast-dock');
    await expect(toastDock).toBeVisible();

    // Verify count badge shows "+4 more" (5 cards total)
    const badge = page.locator('.toast-stack-badge');
    await expect(badge).toBeVisible();
    await expect(badge).toContainText('+4 more');

    // Hover over the toast dock
    await toastDock.hover();
    await page.waitForTimeout(400);

    // Verify all 5 toast cards are present in DOM and visible
    const toastCards = page.locator('.toast-card');
    const count = await toastCards.count();
    expect(count).toBe(5);

    for (let i = 0; i < 5; i++) {
      await expect(toastCards.nth(i)).toBeVisible();
    }
  });

  test('Rapid spam double-clicks within 500ms deduplicate into multiplier badge', async ({ app, page }) => {
    await app.gotoApp();
    
    // Accept EULA if gate is open
    const eulaBtn = page.locator('#eula-accept-btn');
    if (await eulaBtn.isVisible()) {
      await page.locator('#eula-agree-checkbox').check();
      await eulaBtn.click();
      await page.waitForTimeout(500);
    }

    // Trigger 3 identical toasts synchronously within 50ms (accidental double/triple click)
    await page.evaluate(() => {
      (window as any).showToast('Rapid click detected', 'info');
      (window as any).showToast('Rapid click detected', 'info');
      (window as any).showToast('Rapid click detected', 'info');
    });

    await page.waitForTimeout(300);

    // Should only have 1 card with multiplier badge "×3"
    const toastCards = page.locator('.toast-card');
    await expect(toastCards).toHaveCount(1);

    const pill = page.locator('.toast-count-pill');
    await expect(pill).toBeVisible();
    await expect(pill).toHaveText('×3');
  });
});
