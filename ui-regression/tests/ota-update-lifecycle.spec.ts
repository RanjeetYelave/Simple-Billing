import { test, expect } from '../fixtures/base-fixture';

test.describe('Over-The-Air (OTA) Software Update UI & API Lifecycle', () => {
  const BASE_URL = process.env.BASE_URL || 'http://127.0.0.1:28080';

  test('OTA-01: Update status endpoint returns valid contract with current version and release notes metadata', async ({ request }) => {
    const resp = await request.get(`${BASE_URL}/api/system/update-status?force=true`, { timeout: 25000 });
    expect(resp.status()).toBe(200);

    const body = await resp.json();
    expect(body).toHaveProperty('currentVersion');
    expect(body).toHaveProperty('updateAvailable');
    expect(body).toHaveProperty('automaticInstallSupported', true);
  });

  test('OTA-02: SSE Update Progress endpoint establishes text/event-stream connection', async ({ page }) => {
    const sseConnected = await page.evaluate(async (url) => {
      return new Promise((resolve) => {
        const es = new EventSource(`${url}/api/system/update-progress`);
        es.addEventListener('connected', () => {
          es.close();
          resolve(true);
        });
        es.onerror = () => {
          es.close();
          resolve(false);
        };
        setTimeout(() => {
          es.close();
          resolve(false);
        }, 5000);
      });
    }, BASE_URL);

    expect(sseConnected).toBe(true);
  });

  test('OTA-03: Settings Software Update view mounts and displays current version cleanly', async ({ page, app, errorGate }) => {
    // Intercept update status to provide clean metadata
    await page.route('**/api/system/update-status*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          currentVersion: '1.0.0',
          latestVersion: '1.0.0',
          updateAvailable: false,
          automaticInstallSupported: true,
          releaseNotes: 'System running current stable release.'
        })
      });
    });

    await app.gotoApp();
    await app.navigateTo('Settings');

    // Click Software Updates subtab
    const updatesSubtab = page.locator('button:has-text("Software Updates")').first();
    await expect(updatesSubtab).toBeVisible({ timeout: 8000 });
    await updatesSubtab.click();

    // Verify Software Update card header
    const heading = page.locator('h2:has-text("Software Update")');
    await expect(heading).toBeVisible({ timeout: 8000 });

    // Verify current version is rendered cleanly
    const versionLabel = page.locator('text=/Current Version: 1.0.0/');
    await expect(versionLabel).toBeVisible({ timeout: 8000 });

    await errorGate.assertZeroErrors(page, 'OTA-03 Settings Update Subtab');
  });

  test('OTA-04: Software Update view displays update available state and formatted release notes', async ({ page, app, errorGate }) => {
    // Intercept update status to return update available with markdown release notes
    await page.route('**/api/system/update-status*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          currentVersion: '1.0.0',
          latestVersion: '1.1.0',
          updateAvailable: true,
          automaticInstallSupported: true,
          releaseNotes: '## Version 1.1.0\n- **Security**: Ed25519 cryptographic signature verification\n- **Reliability**: Atomic war swap and database preservation guarantee\n- **Crash Recovery**: Two-phase journal commit with safe rollback'
        })
      });
    });

    await app.gotoApp();
    await app.navigateTo('Settings');

    const updatesSubtab = page.locator('button:has-text("Software Updates")').first();
    await expect(updatesSubtab).toBeVisible({ timeout: 8000 });
    await updatesSubtab.click();

    // Verify update available UI card
    const updateHeader = page.locator('text=/Update Available/i');
    await expect(updateHeader.first()).toBeVisible({ timeout: 8000 });

    // Verify target version 1.1.0 is shown
    const newVersion = page.locator('text=/1.1.0/');
    await expect(newVersion.first()).toBeVisible({ timeout: 8000 });

    // Verify formatted release notes bullets are rendered
    const securityNote = page.locator('strong:has-text("Security")');
    await expect(securityNote).toBeVisible({ timeout: 8000 });

    const reliabilityNote = page.locator('strong:has-text("Reliability")');
    await expect(reliabilityNote).toBeVisible({ timeout: 8000 });

    // Verify Install / Update action button exists
    const updateBtn = page.locator('button:has-text("Update Now"), button:has-text("Install Update")').first();
    await expect(updateBtn).toBeVisible({ timeout: 8000 });

    await errorGate.assertZeroErrors(page, 'OTA-04 Update Available Presentation');
  });

  test('OTA-05: check-update-complete endpoint returns structured update state', async ({ request }) => {
    const resp = await request.get(`${BASE_URL}/api/system/check-update-complete`);
    expect(resp.status()).toBe(200);
    const body = await resp.json();
    expect(body).toHaveProperty('justUpdated');
    expect(typeof body.justUpdated).toBe('boolean');
  });

  test('OTA-06: apply-update returns 409 conflict when no update is available or lock is occupied', async ({ request }) => {
    const resp = await request.post(`${BASE_URL}/api/system/apply-update`, { timeout: 25000 });
    expect(resp.status()).toBe(409);
    const body = await resp.json();
    expect(body).toHaveProperty('error');
    expect(body.error).toContain('Cannot start update');
  });
});
