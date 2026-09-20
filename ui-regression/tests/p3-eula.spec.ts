import { test, expect } from '../fixtures/base-fixture';

test.describe('P3-EULA: End User License Agreement Acceptance, Revocation & Gate Enforcement', () => {

  // ── P3-EULA-01 ──────────────────────────────────────────────────────────────
  // Requires a genuinely clean BrowserContext — no pre-accepted EULA state.
  // Uses eulaMode: 'first-launch' so the fixture does NOT inject acceptance.
  test(
    'P3-EULA-01: Fresh Installation Mounts Mandatory EULA Gate with Checkbox Requirement',
    { tag: '@eula' },
    async ({ page, errorGate }) => {
      // 1. Fresh launch without pre-accepted EULA — navigate directly via page,
      //    bypassing the app fixture's automatic acceptance injection.
      await page.goto('/index.html', { waitUntil: 'domcontentloaded' });
      await page.waitForSelector('#root', { timeout: 15000 });

      const gateOverlay = page.locator('#eula-gate-overlay');
      await expect(gateOverlay).toBeVisible({ timeout: 5000 });

      // Check header and content
      const title = page.locator('#eula-modal-card h3');
      await expect(title).toContainText('End User License Agreement');

      const scrollBox = page.locator('#eula-scroll-box');
      await expect(scrollBox).toBeVisible();
      await expect(scrollBox).toContainText('Software License Grant');
      await expect(scrollBox).toContainText('Ranjeet Yelave');

      // 2. Accept button must remain disabled until checkbox is checked
      const acceptBtn = page.locator('#eula-accept-btn');
      await expect(acceptBtn).toBeDisabled();

      // 3. Test Decline button
      const declineBtn = page.locator('#eula-decline-btn');
      await declineBtn.click();

      await expect(page.locator(':text("EULA Acceptance Required")').first()).toBeVisible();
      await expect(page.locator('.sidebar-container')).toBeHidden();

      // Click review again
      const reviewBtn = page.locator('#eula-review-again-btn');
      await reviewBtn.click();
      await expect(scrollBox).toBeVisible();

      // 4. Check checkbox and accept
      const agreeCheckbox = page.locator('#eula-agree-checkbox');
      await agreeCheckbox.check();
      await expect(acceptBtn).toBeEnabled();

      await acceptBtn.click();

      // 5. Gate disappears and app shell becomes accessible
      await expect(gateOverlay).toBeHidden({ timeout: 5000 });
      const topbar = page.locator('.topbar');
      await expect(topbar).toBeVisible();

      await errorGate.assertZeroErrors(page, 'EULA Acceptance Flow');
    }
  );

  // ── P3-EULA-02 ──────────────────────────────────────────────────────────────
  // Uses the default eulaMode: 'accepted' — fixture pre-seeds acceptance.
  test('P3-EULA-02: Restart After Acceptance Keeps Application Accessible', async ({ page, app, errorGate }) => {
    // 1. Launch with pre-accepted EULA (fixture has already seeded the state)
    await app.gotoApp(true);

    const gateOverlay = page.locator('#eula-gate-overlay');
    await expect(gateOverlay).toBeHidden();

    const topbar = page.locator('.topbar');
    await expect(topbar).toBeVisible();

    // Reload page to simulate restart — addInitScript persists across reloads
    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.waitForSelector('#root', { timeout: 15000 });

    await expect(gateOverlay).toBeHidden();
    await expect(topbar).toBeVisible();

    await errorGate.assertZeroErrors(page, 'Restart After EULA Acceptance');
  });

  // ── P3-EULA-03 ──────────────────────────────────────────────────────────────
  test('P3-EULA-03: Settings Legal & EULA Hub Displays Version, Status, and View Agreement Modal', async ({ page, app, errorGate }) => {
    await app.gotoApp(true);

    // 1. Navigate to Settings
    await app.navigateTo('Settings');

    // 2. Click Legal & EULA tab
    const legalTabBtn = page.locator('#settings-tab-legal, button:has-text("Legal & EULA")').first();
    await legalTabBtn.click();
    await page.waitForTimeout(400);

    // 3. Verify status badge and version details
    const statusBadge = page.locator('#eula-status-badge');
    await expect(statusBadge).toBeVisible();
    await expect(statusBadge).toContainText('ACCEPTED');

    const versionText = page.locator('#eula-version-text');
    await expect(versionText).toContainText('1.0.0');

    const dateText = page.locator('#eula-date-text');
    await expect(dateText).not.toBeEmpty();

    // 4. View complete EULA modal
    const viewBtn = page.locator('#view-eula-btn');
    await viewBtn.click();

    const eulaCard = page.locator('#eula-modal-card');
    await expect(eulaCard).toBeVisible();
    await expect(page.locator('#eula-scroll-box')).toBeVisible();

    // Close view modal
    const closeBtn = page.locator('#eula-close-view-btn, #eula-close-top-btn').first();
    await closeBtn.click();
    await expect(page.locator('#eula-gate-overlay')).toBeHidden();

    await errorGate.assertZeroErrors(page, 'Settings Legal & EULA Hub');
  });

  // ── P3-EULA-04 ──────────────────────────────────────────────────────────────
  test('P3-EULA-04: Voluntarily Revoke EULA Acceptance Immediately Blocks Application Access and Persists', async ({ page, app, errorGate }) => {
    await app.gotoApp(true);

    // 1. Navigate to Settings -> Legal & EULA
    await app.navigateTo('Settings');
    const legalTabBtn = page.locator('#settings-tab-legal, button:has-text("Legal & EULA")').first();
    await legalTabBtn.click();
    await page.waitForTimeout(400);

    // 2. Click Revoke EULA Acceptance
    const revokeBtn = page.locator('#revoke-eula-btn');
    await revokeBtn.click();

    // 3. Strong confirmation modal appears
    const revokeModal = page.locator('#revoke-eula-modal');
    await expect(revokeModal).toBeVisible();
    await expect(page.locator('#revoke-warning-text')).toContainText('immediately prevent access to RupeeCRM');

    // 4. Confirm revocation
    const confirmBtn = page.locator('#confirm-revoke-btn');
    await confirmBtn.click();

    // 5. Application is immediately blocked by the EULA Gate
    const gateOverlay = page.locator('#eula-gate-overlay');
    await expect(gateOverlay).toBeVisible({ timeout: 5000 });
    await expect(page.locator('#eula-notice-banner')).toContainText('Access Locked');

    // 6. Test direct navigation attempt while revoked (must remain blocked)
    await page.evaluate(() => {
      window.dispatchEvent(new CustomEvent('billsoft:navigate-subtab', { detail: { page: 'invoices' } }));
    });
    await expect(gateOverlay).toBeVisible();
    await expect(page.locator('.sidebar-container')).toBeHidden();

    // 7. Reload page (Restart after revocation) -> Gate must appear immediately.
    //    After revocation, localStorage contains REVOKED status. The addInitScript
    //    guard (`if (!localStorage.getItem(statusKey))`) does NOT overwrite an
    //    already-present EULA state, so the REVOKED state persists correctly.
    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.waitForSelector('#root', { timeout: 15000 });
    await expect(gateOverlay).toBeVisible({ timeout: 5000 });
    await expect(page.locator('.sidebar-container')).toBeHidden();

    // 8. Re-accept EULA after revocation
    const agreeCheckbox = page.locator('#eula-agree-checkbox');
    await agreeCheckbox.check();
    const acceptBtn = page.locator('#eula-accept-btn');
    await acceptBtn.click();

    // 9. Application becomes accessible again
    await expect(gateOverlay).toBeHidden({ timeout: 5000 });
    await expect(page.locator('.topbar')).toBeVisible();

    await errorGate.assertZeroErrors(page, 'EULA Revocation and Re-acceptance Lifecycle');
  });
});
