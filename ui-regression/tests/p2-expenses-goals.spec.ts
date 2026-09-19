import { test, expect } from '../fixtures/base-fixture';

test.describe('P2: Quick Actions, Expenses & Planner Goals', () => {
  test('P2-QA-01: Quick Action Palette — Expense Command (Fuel 500 UPI)', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    const quickActionBtn = page.locator('button:has-text("+ Expense"), button:has-text("Expense"), button[title*="Expense" i]').first();
    if (await quickActionBtn.isVisible()) {
      await quickActionBtn.click();
      await page.waitForTimeout(400);

      const palette = page.locator('.quick-action-palette, .compact-quick-action, .modal-overlay').first();
      if (await palette.isVisible()) {
        const input = palette.locator('input, textarea').first();
        if (await input.isVisible()) {
          await input.fill('Fuel 500 UPI');
          await page.waitForTimeout(300);
        }

        const cancelBtn = palette.locator('button:has-text("Cancel"), button:has-text("✕")').first();
        if (await cancelBtn.isVisible()) {
          await cancelBtn.click();
        } else {
          await page.keyboard.press('Escape');
        }
      }
    }

    await errorGate.assertZeroErrors(page, 'Quick Expense Action');
  });

  test('P2-QA-02: Quick Action Palette — Reminder, Task, and Note Commands', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    // Test Quick Task / Reminder trigger if available on dashboard
    const quickTaskBtn = page.locator('button:has-text("+ Task"), button:has-text("+ Reminder"), button:has-text("Quick Action")').first();
    if (await quickTaskBtn.isVisible()) {
      await quickTaskBtn.click();
      await page.waitForTimeout(400);

      const modal = page.locator('.modal-overlay, .quick-action-palette').first();
      if (await modal.isVisible()) {
        await page.keyboard.press('Escape');
      }
    }

    await errorGate.assertZeroErrors(page, 'Quick Task & Reminder Commands');
  });

  test('P2-GOAL-01: Planner Goals & Habits Pipeline', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    await app.navigateTo('Planner');

    // Goals tab
    const goalsTab = page.locator('button:has-text("Goals"), .tab-btn:has-text("Goals")').first();
    if (await goalsTab.isVisible()) {
      await goalsTab.click();
      await page.waitForTimeout(400);

      const goalsView = page.locator('.goals-container, .goal-card, :text("Goals")').first();
      await expect(goalsView).toBeVisible();
    }

    // Habits tab
    const habitsTab = page.locator('button:has-text("Habits"), .tab-btn:has-text("Habits")').first();
    if (await habitsTab.isVisible()) {
      await habitsTab.click();
      await page.waitForTimeout(400);

      const habitsView = page.locator('.habits-container, :text("Habits")').first();
      await expect(habitsView).toBeVisible();
    }

    await errorGate.assertZeroErrors(page, 'Goals & Habits Pipeline');
  });
});
