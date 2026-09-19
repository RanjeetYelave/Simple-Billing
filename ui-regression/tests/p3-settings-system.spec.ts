import { test, expect } from '../fixtures/base-fixture';

test.describe('P3: Settings Hub, Configuration & Backup/Restore', () => {
  test('P3-SET-01: Settings Hub Subtab Navigation (General, Firm, Licensing, Security)', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    await app.navigateTo('Settings');

    const settingsHub = page.locator('.settings-hub, :text("Settings")').first();
    await expect(settingsHub).toBeVisible();

    // Test subtab switching
    const licensingTab = page.locator('button:has-text("Licensing"), button:has-text("License"), .tab-btn:has-text("License")').first();
    if (await licensingTab.isVisible()) {
      await licensingTab.click();
      await page.waitForTimeout(300);
    }

    const securityTab = page.locator('button:has-text("Security"), button:has-text("Data Protection"), .tab-btn:has-text("Security")').first();
    if (await securityTab.isVisible()) {
      await securityTab.click();
      await page.waitForTimeout(300);
    }

    await errorGate.assertZeroErrors(page, 'Settings Subtabs');
  });

  test('P3-SET-02: Backup & Restore Data Interface Integrity', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    await app.navigateTo('Settings');

    const backupTab = page.locator('button:has-text("Backup"), button:has-text("Restore"), button:has-text("Database")').first();
    if (await backupTab.isVisible()) {
      await backupTab.click();
      await page.waitForTimeout(300);

      const backupView = page.locator('.backup-restore-view, :text("Backup"), :text("Restore")').first();
      await expect(backupView).toBeVisible();
    }

    await errorGate.assertZeroErrors(page, 'Backup & Restore Interface');
  });
});
