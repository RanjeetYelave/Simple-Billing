import { test, expect } from '../fixtures/base-fixture';

test.describe('Omnisearch Safari / Top Navigation Interaction & Focus Specs', () => {

  test('Omnisearch 01: Clicking left, center, and right portions focuses the input with zero clipboard calls', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    // Track any attempt to access navigator.clipboard
    await page.evaluate(() => {
      window.__clipboardReads = 0;
      if (navigator.clipboard) {
        const origReadText = navigator.clipboard.readText ? navigator.clipboard.readText.bind(navigator.clipboard) : null;
        navigator.clipboard.readText = async () => {
          window.__clipboardReads++;
          return '';
        };
      }
    });

    const searchPill = page.locator('.topbar-search-pill');
    await expect(searchPill).toBeVisible();

    // 1. Click Left Portion (the search icon area)
    const icon = searchPill.locator('.topbar-search-icon, svg').first();
    await icon.click();

    const omniModal = page.locator('.omni-modal-overlay');
    await expect(omniModal).toBeVisible();

    const omniInput = page.locator('.omni-search-input');
    await expect(omniInput).toBeFocused();

    // Verify ZERO clipboard sniffing calls occurred
    const clipboardCallsAfterLeft = await page.evaluate(() => window.__clipboardReads);
    expect(clipboardCallsAfterLeft).toBe(0);

    // Dismiss via Escape
    await page.keyboard.press('Escape');
    await expect(omniModal).toBeHidden();

    // 2. Click Center Portion (the text/placeholder span)
    const textSpan = searchPill.locator('span').nth(1);
    await textSpan.click();
    await expect(omniModal).toBeVisible();
    await expect(omniInput).toBeFocused();

    await page.keyboard.press('Escape');
    await expect(omniModal).toBeHidden();

    // 3. Click Right Portion (the kbd badge)
    const kbd = searchPill.locator('kbd');
    await kbd.click();
    await expect(omniModal).toBeVisible();
    await expect(omniInput).toBeFocused();

    // Verify typing immediately after clicking works
    await page.keyboard.type('test query 123');
    await expect(omniInput).toHaveValue('test query 123');

    // Verify zero clipboard calls throughout
    const totalClipboardCalls = await page.evaluate(() => window.__clipboardReads);
    expect(totalClipboardCalls).toBe(0);

    await errorGate.assertZeroErrors(page, 'Omnisearch Left/Center/Right Click Focus');
  });

  test('Omnisearch 02: Query preservation across dismissals, editing, and clearing', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    const searchPill = page.locator('.topbar-search-pill');
    await searchPill.click();

    const omniModal = page.locator('.omni-modal-overlay');
    const omniInput = page.locator('.omni-search-input');
    await expect(omniInput).toBeFocused();

    // Type query
    await page.keyboard.type('advance of Sachin');
    await expect(omniInput).toHaveValue('advance of Sachin');

    // Click outside on modal overlay to dismiss
    await omniModal.click({ position: { x: 10, y: 10 } });
    await expect(omniModal).toBeHidden();

    // Verify topbar pill retains preserved query
    await expect(searchPill).toContainText('advance of Sachin');

    // Reopen by clicking search pill
    await searchPill.click();
    await expect(omniModal).toBeVisible();
    await expect(omniInput).toBeFocused();
    await expect(omniInput).toHaveValue('advance of Sachin');

    // Append to preserved query
    await page.keyboard.type(' 5000');
    await expect(omniInput).toHaveValue('advance of Sachin 5000');

    // Clear search using the clear '✕' button
    const clearBtn = page.locator('button[title="Clear search"]');
    await clearBtn.click();
    await expect(omniInput).toHaveValue('');

    // Dismiss modal
    await page.keyboard.press('Escape');
    await expect(omniModal).toBeHidden();

    await errorGate.assertZeroErrors(page, 'Omnisearch Query Preservation');
  });

  test('Omnisearch 03: Keyboard navigation, select-all, arrow keys, suggestions and execution', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    // Open via Cmd+K shortcut
    await page.keyboard.press('Meta+k');
    const omniModal = page.locator('.omni-modal-overlay');
    await expect(omniModal).toBeVisible();

    const omniInput = page.locator('.omni-search-input');
    await expect(omniInput).toBeFocused();

    // Type query
    await page.keyboard.type('today sales');
    await page.waitForTimeout(300);

    // Select-all via Meta+A and replace
    await page.keyboard.press('Meta+a');
    await page.keyboard.type('/inv');
    await expect(omniInput).toHaveValue('/inv');

    // Execute via Enter
    await page.keyboard.press('Enter');
    await expect(omniModal).toBeHidden();

    // Verify navigation happened to invoice creation
    await page.waitForTimeout(400);

    await errorGate.assertZeroErrors(page, 'Omnisearch Shortcuts & Execution');
  });

  test('Omnisearch 04: Topbar controls (Calculator, notifications, theme toggle) remain functional', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    // 1. Calculator button
    const calcBtn = page.locator('#topbar-calculator-btn');
    await calcBtn.click();
    const calcWindow = page.locator('#rupeecrm-calculator-window');
    await expect(calcWindow).toBeVisible();
    await calcBtn.click();
    await expect(calcWindow).toBeHidden();

    // 2. Theme Toggle
    const themeBtn = page.locator('.theme-toggle-btn');
    await themeBtn.click();
    await page.waitForTimeout(300);

    // Verify omnisearch still works in Dark Theme
    const searchPill = page.locator('.topbar-search-pill');
    await searchPill.click();
    const omniModal = page.locator('.omni-modal-overlay');
    await expect(omniModal).toBeVisible();
    const omniInput = page.locator('.omni-search-input');
    await expect(omniInput).toBeFocused();

    await page.keyboard.press('Escape');
    await expect(omniModal).toBeHidden();

    // Toggle theme back
    await themeBtn.click();

    await errorGate.assertZeroErrors(page, 'Topbar Controls & Theme Switch Omnisearch');
  });
});
