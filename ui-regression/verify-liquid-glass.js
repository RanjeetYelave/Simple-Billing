const { chromium } = require('playwright');
const path = require('path');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();

  const consoleErrors = [];
  page.on('console', msg => {
    if (msg.type() === 'error') consoleErrors.push(msg.text());
  });

  await page.route('**/api/announcements*', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }));

  await page.addInitScript(() => {
    localStorage.setItem('rupeecrm_eula_status', 'ACCEPTED');
    localStorage.setItem('rupeecrm_eula_version', '1.0.0');
    localStorage.setItem('rupeecrm_eula_accepted_at', new Date().toISOString());
    localStorage.setItem('rupeecrm_announcements_ack', 'mock');
    sessionStorage.setItem('employeePin', '0000');
  });

  console.log('1. Loading application in Light Mode...');
  await page.goto('http://127.0.0.1:28080/index.html', { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#root', { timeout: 15000 });
  await page.waitForTimeout(800);

  // Dismiss any startup announcements modal
  const modalOverlay = page.locator('#announcements-startup-modal-overlay, .modal-overlay:has(#announcements-startup-modal)');
  if (await modalOverlay.isVisible().catch(() => false)) {
    await modalOverlay.click({ force: true });
    await page.waitForTimeout(300);
  }
  await page.keyboard.press('Escape');

  await page.evaluate(() => {
    if (window.setAppTheme) window.setAppTheme('light-1');
  });
  await page.waitForTimeout(600);

  // A. Capture Light Mode Topbar
  const topbar = page.locator('header.topbar');
  await topbar.screenshot({ path: path.join(__dirname, 'glass_light_topbar.png') });
  console.log('Saved glass_light_topbar.png');

  // Hover over topbar search capsule
  const searchPill = page.locator('.topbar-search-pill');
  await searchPill.hover({ force: true });
  await page.waitForTimeout(200);
  await topbar.screenshot({ path: path.join(__dirname, 'glass_light_topbar_search_hover.png') });

  // Hover over calculator button
  const calcBtn = page.locator('#topbar-calculator-btn');
  await calcBtn.hover({ force: true });
  await page.waitForTimeout(200);
  await topbar.screenshot({ path: path.join(__dirname, 'glass_light_topbar_calc_hover.png') });

  // Hover over sync button
  const syncBtn = page.locator('.topbar-action-icon-btn').first();
  await syncBtn.hover({ force: true });
  await page.waitForTimeout(200);
  await topbar.screenshot({ path: path.join(__dirname, 'glass_light_topbar_sync_hover.png') });

  // B. Capture Dashboard Segmented Tabs in Light Mode
  await page.evaluate(() => {
    window.dispatchEvent(new CustomEvent('billsoft:navigate-subtab', { detail: { page: 'dashboard' } }));
  });
  await page.waitForTimeout(800);
  const dashNav = page.locator('.rcrm-dash-segmented-nav').first();
  if (await dashNav.isVisible().catch(() => false)) {
    await dashNav.screenshot({ path: path.join(__dirname, 'glass_light_dash_segmented.png') });
    console.log('Saved glass_light_dash_segmented.png');
  }

  // C. Capture Firm / Inventory Subtabs Track in Light Mode
  await page.evaluate(() => {
    window.dispatchEvent(new CustomEvent('billsoft:navigate-subtab', { detail: { page: 'firm' } }));
  });
  await page.waitForTimeout(800);
  const subtabsTrack = page.locator('.rcrm-subtabs-track').first();
  if (await subtabsTrack.isVisible().catch(() => false)) {
    await subtabsTrack.screenshot({ path: path.join(__dirname, 'glass_light_subtabs_track.png') });
    console.log('Saved glass_light_subtabs_track.png');
  }

  // Full page view Light Mode
  await page.screenshot({ path: path.join(__dirname, 'glass_light_full.png') });
  console.log('Saved glass_light_full.png');

  // 2. Modern Dark Mode
  console.log('2. Switching to Modern Dark Mode...');
  await page.evaluate(() => {
    if (window.setAppTheme) window.setAppTheme('dark-1');
  });
  await page.waitForTimeout(600);

  await topbar.screenshot({ path: path.join(__dirname, 'glass_dark_topbar.png') });
  console.log('Saved glass_dark_topbar.png');

  await page.evaluate(() => {
    window.dispatchEvent(new CustomEvent('billsoft:navigate-subtab', { detail: { page: 'dashboard' } }));
  });
  await page.waitForTimeout(600);
  if (await dashNav.isVisible().catch(() => false)) {
    await dashNav.screenshot({ path: path.join(__dirname, 'glass_dark_dash_segmented.png') });
    console.log('Saved glass_dark_dash_segmented.png');
  }

  await page.evaluate(() => {
    window.dispatchEvent(new CustomEvent('billsoft:navigate-subtab', { detail: { page: 'firm' } }));
  });
  await page.waitForTimeout(600);
  if (await subtabsTrack.isVisible().catch(() => false)) {
    await subtabsTrack.screenshot({ path: path.join(__dirname, 'glass_dark_subtabs_track.png') });
    console.log('Saved glass_dark_subtabs_track.png');
  }

  await page.screenshot({ path: path.join(__dirname, 'glass_dark_full.png') });
  console.log('Saved glass_dark_full.png');

  // 3. Retro Classic Mode
  console.log('3. Switching to Retro Classic Mode...');
  await page.evaluate(() => {
    if (window.setAppTheme) window.setAppTheme('classic');
  });
  await page.waitForTimeout(600);
  await topbar.screenshot({ path: path.join(__dirname, 'classic_light_topbar.png') });
  await page.screenshot({ path: path.join(__dirname, 'classic_light_full.png') });
  console.log('Saved classic_light_topbar.png and classic_light_full.png');

  // 4. Classic Dark Mode
  console.log('4. Switching to Classic Dark Mode...');
  await page.evaluate(() => {
    if (window.setAppTheme) window.setAppTheme('classic-dark');
  });
  await page.waitForTimeout(600);
  await topbar.screenshot({ path: path.join(__dirname, 'classic_dark_topbar.png') });
  await page.screenshot({ path: path.join(__dirname, 'classic_dark_full.png') });
  console.log('Saved classic_dark_topbar.png and classic_dark_full.png');

  // 5. Test Centralized Disable Mechanism
  console.log('5. Testing Centralized Disable Mechanism...');
  await page.evaluate(() => {
    if (window.setAppTheme) window.setAppTheme('light-1');
    document.documentElement.setAttribute('data-liquid-glass', 'false');
  });
  await page.waitForTimeout(600);
  await topbar.screenshot({ path: path.join(__dirname, 'glass_disabled_topbar.png') });
  console.log('Saved glass_disabled_topbar.png');

  // Re-enable
  await page.evaluate(() => {
    document.documentElement.removeAttribute('data-liquid-glass');
  });
  await page.waitForTimeout(300);

  // 6. Test Narrow Viewport (768px tablet)
  console.log('6. Testing Narrow Viewport (768px)...');
  await page.setViewportSize({ width: 768, height: 1024 });
  await page.waitForTimeout(300);
  await page.screenshot({ path: path.join(__dirname, 'glass_narrow_768.png') });
  console.log('Saved glass_narrow_768.png');

  console.log('Console Errors encountered:', consoleErrors.length);
  if (consoleErrors.length > 0) {
    console.log('Console errors:', consoleErrors);
  }

  await browser.close();
  console.log('Verification completed successfully.');
})();
