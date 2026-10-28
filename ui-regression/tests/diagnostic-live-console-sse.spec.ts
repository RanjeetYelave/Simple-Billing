import { test, expect } from '@playwright/test';

test.describe('Real-Time Live Console SSE Streaming', () => {
  const BASE_URL = process.env.BASE_URL || 'http://127.0.0.1:28090';

  test.beforeEach(async ({ request }) => {
    // Enable persistent console mode with VERBOSE logging
    const resp = await request.post(`${BASE_URL}/api/recovery/settings/persistent-console`, {
      headers: {
        'Content-Type': 'application/json',
        'X-Diagnostic-Key': 'Saidarshan*1'
      },
      data: {
        enabled: true,
        logLevel: 'VERBOSE'
      }
    });
    expect(resp.ok()).toBeTruthy();
  });

  test.afterAll(async ({ request }) => {
    await request.post(`${BASE_URL}/api/recovery/settings/persistent-console`, {
      headers: {
        'Content-Type': 'application/json',
        'X-Diagnostic-Key': 'Saidarshan*1'
      },
      data: {
        enabled: false
      }
    });
  });

  test('SSE-01: PersistentLiveConsole in index.html connects via EventSource and streams logs live', async ({ page, request }) => {
    await page.goto(`${BASE_URL}/index.html`);
    await page.waitForLoadState('domcontentloaded');

    // Verify PersistentLiveConsole panel is mounted and visible
    const consolePanel = page.locator('#persistent-live-console-panel');
    await expect(consolePanel).toBeVisible({ timeout: 10000 });

    // Verify connection state text
    await expect(consolePanel).toContainText(/STREAMING|CONNECTED/, { timeout: 10000 });

    // Generate unique application log via an API call
    const uniqueMarker = `LIVE_CONSOLE_TEST_${Date.now()}`;
    await request.get(`${BASE_URL}/api/system/heartbeat?marker=${uniqueMarker}`);

    // Verify that the new log automatically appears in the DOM viewport
    await expect(consolePanel).toContainText('heartbeat', { timeout: 8000 });
  });

  test('SSE-02: recovery.html connects via EventSource on live console view and receives logs', async ({ page, request }) => {
    // Pre-authenticate diagnostic session via clearance key submission or cookie
    await page.goto(`${BASE_URL}/recovery.html`);
    await page.waitForLoadState('domcontentloaded');

    // If access gate is visible, enter master key
    const gateInput = page.locator('#masterKeyInput');
    if (await gateInput.isVisible()) {
      await gateInput.fill('Saidarshan*1');
      await page.locator('#gateSubmitBtn').click();
      await page.waitForTimeout(500);
    }

    // Switch to Live Console view
    const liveConsoleNav = page.locator('.nav-item').filter({ hasText: 'Live Console' });
    await expect(liveConsoleNav).toBeVisible({ timeout: 8000 });
    await liveConsoleNav.click();

    const liveBadge = page.locator('#liveConnBadge');
    await expect(liveBadge).toContainText('STREAMING', { timeout: 10000 });

    const terminalWin = page.locator('#terminalLogWindow');
    await expect(terminalWin).toBeVisible();

    // Generate log event
    await request.get(`${BASE_URL}/api/firm`);

    // Verify log lines appear in terminalLogWindow
    await expect(terminalWin.locator('.terminal-line').first()).toBeVisible({ timeout: 8000 });
  });

  test('SSE-03: Continuous streaming across multiple user interactions without page refresh', async ({ page, request }) => {
    await page.goto(`${BASE_URL}/index.html`);
    await page.waitForLoadState('domcontentloaded');

    const consolePanel = page.locator('#persistent-live-console-panel');
    await expect(consolePanel).toBeVisible({ timeout: 10000 });

    // Generate burst of sequential application operations
    for (let i = 1; i <= 3; i++) {
      const marker = `BURST_LOG_${i}_${Date.now()}`;
      await request.get(`${BASE_URL}/api/system/heartbeat?iter=${i}&marker=${marker}`);
    }

    // Verify all logs continuously streamed and rendered in real time
    await expect(consolePanel).toContainText('BURST_LOG_3', { timeout: 8000 });
  });

  test('SSE-04: Multiple simultaneous browser tabs receive identical continuous log streams', async ({ browser, request }) => {
    const context1 = await browser.newContext();
    const page1 = await context1.newPage();
    await page1.goto(`${BASE_URL}/index.html`);

    const context2 = await browser.newContext();
    const page2 = await context2.newPage();
    await page2.goto(`${BASE_URL}/index.html`);

    const panel1 = page1.locator('#persistent-live-console-panel');
    const panel2 = page2.locator('#persistent-live-console-panel');

    await expect(panel1).toBeVisible({ timeout: 10000 });
    await expect(panel2).toBeVisible({ timeout: 10000 });

    const sharedMarker = `SHARED_STREAM_EVENT_${Date.now()}`;
    await request.get(`${BASE_URL}/api/system/heartbeat?shared=${sharedMarker}`);

    await expect(panel1).toContainText(sharedMarker, { timeout: 8000 });
    await expect(panel2).toContainText(sharedMarker, { timeout: 8000 });

    await context1.close();
    await context2.close();
  });
});
