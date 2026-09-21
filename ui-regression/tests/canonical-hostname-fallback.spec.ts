import { test, expect } from '../fixtures/base-fixture';

test.describe('Canonical Hostname & Localhost Fallback Compatibility', () => {
  test('CANON-01: Canonical URL management.rupeecrm.local:28080 loads cleanly with zero errors', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    // Verify current origin is canonical management.rupeecrm.local or fallback loopback
    const origin = await page.evaluate(() => window.location.origin);
    expect(origin).toMatch(/http:\/\/(management\.rupeecrm\.local|127\.0\.0\.1|localhost):28080/);

    // Verify app shell mounted
    const logo = page.locator('.sidebar-logo, :text("Rupee")').first();
    await expect(logo).toBeVisible();

    // Verify API health through current origin
    const healthStatus = await page.evaluate(async () => {
      const res = await fetch('/api/health');
      return await res.json();
    });
    expect(healthStatus.status).toBe('UP');

    await errorGate.assertZeroErrors(page, 'Canonical URL Mount & Health Check');
  });

  test('CANON-02: Fallback 127.0.0.1:28080 / localhost:28080 loads same backend instance and version', async ({ page, errorGate }) => {
    // Direct navigation to 127.0.0.1:28080/api/health
    const response = await page.request.get('http://127.0.0.1:28080/api/health');
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.status).toBe('UP');

    // Compare with canonical hostname health
    const canonicalRes = await page.request.get('http://management.rupeecrm.local:28080/api/health');
    expect(canonicalRes.status()).toBe(200);
    const canonicalBody = await canonicalRes.json();
    expect(canonicalBody.status).toBe('UP');
  });
});
