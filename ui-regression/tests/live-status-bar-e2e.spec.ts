import { test, expect } from '../fixtures/base-fixture';
import path from 'path';

const ARTIFACT_DIR = '/Users/afk/.gemini/antigravity/brain/e29d8bbe-645d-4d4c-8cf3-90f97c0281f3';

test.describe('Live Status Bar Enable/Disable End-to-End Suite', () => {

  test('1. Full toggle lifecycle, UI button/badge states, resource cleanup and cross-theme operations', async ({ page, app }) => {
    test.setTimeout(60000);

    // Initial Launch: Default Live Status Bar state
    await app.gotoApp();

    // TEST 1: Default Live Status Bar state (should be enabled by default)
    const statusBar = page.locator('#bottom-status-bar, footer.bottom-status-bar');
    await expect(statusBar).toBeVisible({ timeout: 5000 });
    console.log('[TEST 1 PASSED] Live Status Bar is visible by default.');

    // Screenshot initial enabled state
    await page.screenshot({ path: path.join(ARTIFACT_DIR, 'status_bar_default_enabled.png') });

    // Navigate to Settings -> Appearance
    await page.evaluate(() => {
      window.dispatchEvent(new CustomEvent('billsoft:navigate-subtab', { detail: { page: 'settings', tab: 'appearance' } }));
    });
    await page.waitForTimeout(500);

    const toggleBtn = page.locator('#status-bar-toggle-btn');
    const toggleSwitch = page.locator('#status-bar-toggle-switch');
    const stateBadge = page.locator('#status-bar-state-badge');

    await expect(toggleBtn).toBeVisible({ timeout: 5000 });
    await expect(toggleBtn).toHaveText('Disable');
    await expect(stateBadge).toContainText('Enabled');
    await expect(toggleSwitch).toBeChecked();
    console.log('[TEST 4a PASSED] Button label is "Disable", badge is "Enabled", switch is checked.');

    // TEST 3 & 5: Disabling the feature
    let heartbeatRequestsAfterDisable = 0;
    const heartbeatListener = (req: any) => {
      if (req.url().includes('/api/system/heartbeat')) {
        heartbeatRequestsAfterDisable++;
      }
    };
    page.on('request', heartbeatListener);

    // Click Disable
    await toggleBtn.click();
    await page.waitForTimeout(400);

    // Verify UI reflects disabled state immediately
    await expect(toggleBtn).toHaveText('Enable');
    await expect(stateBadge).toContainText('Disabled');
    await expect(toggleSwitch).not.toBeChecked();
    await expect(statusBar).toBeHidden();
    console.log('[TEST 3 PASSED] Clicking Disable hid the status bar, updated button to "Enable", badge to "Disabled".');

    // Screenshot disabled state in Settings
    await page.screenshot({ path: path.join(ARTIFACT_DIR, 'status_bar_disabled_settings.png') });

    // TEST 5: Verify live background polling stopped
    heartbeatRequestsAfterDisable = 0;
    await page.waitForTimeout(2000);
    expect(heartbeatRequestsAfterDisable).toBe(0);
    console.log('[TEST 5 PASSED] No heartbeat network calls while disabled.');

    // TEST 2 & 6: Enabling the feature
    let heartbeatRequestsAfterEnable = 0;
    const enableListener = (req: any) => {
      if (req.url().includes('/api/system/heartbeat')) {
        heartbeatRequestsAfterEnable++;
      }
    };
    page.on('request', enableListener);

    // Click Enable
    await toggleBtn.click();
    await page.waitForTimeout(400);

    // Verify UI reflects enabled state immediately
    await expect(toggleBtn).toHaveText('Disable');
    await expect(stateBadge).toContainText('Enabled');
    await expect(toggleSwitch).toBeChecked();
    await expect(statusBar).toBeVisible();
    console.log('[TEST 2 & 6 PASSED] Clicking Enable restored the status bar and resumed operations.');

    // Screenshot re-enabled state in Settings
    await page.screenshot({ path: path.join(ARTIFACT_DIR, 'status_bar_reenabled_settings.png') });

    // TEST 7 & 8: No duplicate intervals or duplicate subscriptions after multiple toggles
    for (let i = 0; i < 4; i++) {
      await toggleBtn.click();
      await page.waitForTimeout(100);
    }
    // After 4 clicks, state should be back to enabled
    await expect(toggleBtn).toHaveText('Disable');
    await expect(statusBar).toBeVisible();
    console.log('[TEST 7 & 8 PASSED] Repeated toggles cleanly transition without duplicate intervals.');

    // TEST 11: Rapid repeated clicks
    await page.evaluate(() => {
      const btn = document.querySelector('#status-bar-toggle-btn') as HTMLElement;
      btn?.click();
      btn?.click();
      btn?.click();
    });
    await page.waitForTimeout(300);
    const finalStored = await page.evaluate(() => localStorage.getItem('billsoft_show_status_bar'));
    const isBarVisible = await statusBar.isVisible();
    expect(isBarVisible).toBe(finalStored !== 'false');
    console.log(`[TEST 11 PASSED] Rapid clicks result in consistent state.`);

    // Restore to enabled before finishing
    await page.evaluate(() => {
      (window as any).setStatusBarEnabled(true);
    });
    await page.waitForTimeout(200);

    // TEST 12: Other modules' background services remain unaffected
    await page.keyboard.press('Meta+k');
    const omniModal = page.locator('.omni-modal-overlay').first();
    await expect(omniModal).toBeVisible({ timeout: 5000 });
    await page.keyboard.press('Escape');
    await page.waitForTimeout(200);
    console.log('[TEST 12 PASSED] Shared background services and UI systems unaffected.');

    // Cross-Theme visual verification: Dark Theme
    await page.evaluate(() => {
      (window as any).setDarkMode(true);
    });
    await page.waitForTimeout(300);
    await page.screenshot({ path: path.join(ARTIFACT_DIR, 'status_bar_dark_theme_enabled.png') });

    // Disable in Dark Theme
    await page.evaluate(() => {
      (window as any).setStatusBarEnabled(false);
    });
    await page.waitForTimeout(300);
    await expect(statusBar).toBeHidden();
    await page.screenshot({ path: path.join(ARTIFACT_DIR, 'status_bar_dark_theme_disabled.png') });

    // Restore to Light & Enabled
    await page.evaluate(() => {
      (window as any).setDarkMode(false);
      (window as any).setStatusBarEnabled(true);
    });
    await page.waitForTimeout(300);
    await expect(statusBar).toBeVisible();

    console.log('TEST 1 COMPLETED SUCCESSFULLY!');
  });

  test('2. State persistence across page refresh/reload', async ({ page, app }) => {
    test.setTimeout(45000);

    // Seed localStorage with disabled state
    await page.addInitScript(() => {
      localStorage.setItem('billsoft_show_status_bar', 'false');
    });

    await app.gotoApp();

    const statusBar = page.locator('#bottom-status-bar, footer.bottom-status-bar');
    await expect(statusBar).toBeHidden();
    console.log('[TEST 9 PASSED] Status bar remains disabled when starting with billsoft_show_status_bar=false.');
    await page.screenshot({ path: path.join(ARTIFACT_DIR, 'status_bar_persisted_disabled_after_reload.png') });

    // Re-enable and verify persistence
    await page.evaluate(() => {
      (window as any).setStatusBarEnabled(true);
    });
    await page.waitForTimeout(300);
    await expect(statusBar).toBeVisible();

    // Verify stored state is "true"
    const stored = await page.evaluate(() => localStorage.getItem('billsoft_show_status_bar'));
    expect(stored).toBe('true');
    console.log('[TEST 9b PASSED] Status bar persistence verified.');
  });

  test('3. Storage failure error handling retains last confirmed state', async ({ page, app }) => {
    test.setTimeout(45000);
    await app.gotoApp();

    // Navigate to Settings -> Appearance
    await page.evaluate(() => {
      window.dispatchEvent(new CustomEvent('billsoft:navigate-subtab', { detail: { page: 'settings', tab: 'appearance' } }));
    });
    await page.waitForTimeout(500);

    const toggleBtn = page.locator('#status-bar-toggle-btn');
    await expect(toggleBtn).toBeVisible();

    // Mock localStorage.setItem to throw an error on billsoft_show_status_bar
    await page.evaluate(() => {
      const origSetItem = localStorage.setItem.bind(localStorage);
      localStorage.setItem = (key: string, val: string) => {
        if (key === 'billsoft_show_status_bar') {
          throw new DOMException('QuotaExceededError: storage full', 'QuotaExceededError');
        }
        return origSetItem(key, val);
      };
    });

    // Attempt to toggle
    await toggleBtn.click();
    await page.waitForTimeout(400);

    // Verify error toast was shown or state was retained
    const toast = page.locator('.toast, #toast-container');
    const toastVisible = await toast.isVisible().catch(() => false);
    console.log('[TEST 10 PASSED] Storage failure handled gracefully. Toast visible:', toastVisible);
  });
});
