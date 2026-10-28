const { webkit } = require('playwright');
const path = require('path');

(async () => {
  console.log('Launching Playwright WebKit (Safari engine)...');
  const browser = await webkit.launch({ headless: true });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 }
  });
  const page = await context.newPage();

  // Monitor console errors and dialogs
  const consoleErrors = [];
  page.on('console', msg => {
    if (msg.type() === 'error') consoleErrors.push(msg.text());
  });

  // Track clipboard reads in WebKit and seed EULA
  await page.addInitScript(() => {
    window.__clipboardReads = 0;
    if (navigator.clipboard) {
      navigator.clipboard.readText = async () => {
        window.__clipboardReads++;
        return 'intercepted';
      };
    }
    localStorage.setItem('rupeecrm_eula_status', 'ACCEPTED');
    localStorage.setItem('rupeecrm_eula_version', '1.0.0');
    localStorage.setItem('rupeecrm_eula_accepted_at', new Date().toISOString());
    sessionStorage.setItem('employeePin', '0000');
  });

  console.log('Navigating to RupeeCRM in WebKit...');
  await page.goto('http://127.0.0.1:28090/index.html', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(1000);

  // Dismiss announcement if open
  const annBtn = page.locator('#btn-acknowledge-announcements');
  if (await annBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
    await annBtn.click().catch(() => {});
  }

  // 1. Capture before-click topbar in WebKit
  const searchPill = page.locator('.topbar-search-pill');
  await searchPill.waitFor({ state: 'visible', timeout: 10000 });
  await page.screenshot({ path: path.join(__dirname, 'safari_webkit_topbar_initial.png') });
  console.log('Saved safari_webkit_topbar_initial.png');

  // 2. Click search pill and verify input focus
  console.log('Clicking search pill in WebKit...');
  await searchPill.click();

  const omniModal = page.locator('.omni-modal-overlay');
  await omniModal.waitFor({ state: 'visible', timeout: 5000 });
  const omniInput = page.locator('.omni-search-input');
  await omniInput.waitFor({ state: 'visible', timeout: 5000 });

  // Check active element is the input
  const isFocused = await page.evaluate(() => document.activeElement && document.activeElement.classList.contains('omni-search-input'));
  console.log('Omni search input is focused in WebKit:', isFocused);

  const clipboardReads = await page.evaluate(() => window.__clipboardReads);
  console.log('Clipboard reads performed in WebKit:', clipboardReads);

  // Type immediately into input
  await page.keyboard.type('advance of Sachin');
  const queryVal = await omniInput.inputValue();
  console.log('Input value after typing in WebKit:', queryVal);

  // Capture modal open screenshot in WebKit
  await page.screenshot({ path: path.join(__dirname, 'safari_webkit_omnisearch_open.png') });
  console.log('Saved safari_webkit_omnisearch_open.png');

  // Click outside on overlay to dismiss
  console.log('Dismissing modal by clicking outside in WebKit...');
  await omniModal.click({ position: { x: 15, y: 15 } });
  await omniModal.waitFor({ state: 'hidden', timeout: 5000 });

  // Verify topbar pill retains preserved query
  const pillText = await searchPill.textContent();
  console.log('Search pill text after dismissal in WebKit:', pillText.trim());

  // Capture topbar with preserved query in WebKit
  await page.screenshot({ path: path.join(__dirname, 'safari_webkit_topbar_query_preserved.png') });
  console.log('Saved safari_webkit_topbar_query_preserved.png');

  // Reopen and verify preserved query remains intact
  await searchPill.click();
  await omniModal.waitFor({ state: 'visible', timeout: 5000 });
  const reopenedVal = await omniInput.inputValue();
  console.log('Input value on reopen in WebKit:', reopenedVal);

  console.log('Console errors in WebKit:', consoleErrors);
  await browser.close();
  console.log('WebKit Safari verification complete successfully!');
})();
