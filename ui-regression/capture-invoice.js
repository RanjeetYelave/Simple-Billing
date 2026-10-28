const { chromium } = require('playwright');
const path = require('path');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1024, height: 768 } });
  const page = await context.newPage();

  // Inject EULA accepted & employee PIN
  await page.addInitScript(() => {
    localStorage.setItem('rupeecrm_eula_status', 'ACCEPTED');
    localStorage.setItem('rupeecrm_eula_version', '1.0.0');
    localStorage.setItem('rupeecrm_eula_accepted_at', new Date().toISOString());
    sessionStorage.setItem('employeePin', '0000');
  });

  console.log('Navigating to app...');
  await page.goto('http://127.0.0.1:28090/index.html', { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#root', { timeout: 15000 });
  await page.waitForTimeout(500);

  // Dismiss announcements if any
  const annBtn = page.locator('#btn-acknowledge-announcements');
  if (await annBtn.isVisible().catch(() => false)) {
    await annBtn.click().catch(() => {});
  }

  // Navigate to Invoices
  console.log('Navigating to Invoices...');
  await page.evaluate(() => {
    window.dispatchEvent(new CustomEvent('billsoft:navigate-subtab', { detail: { page: 'invoices' } }));
  });
  await page.waitForTimeout(600);

  // Click "+ Inv" or "New Invoice"
  const newInvBtn = page.locator('button:has-text("+ Inv"), button:has-text("📄 + New Invoice"), button:has-text("Create Invoice")').first();
  if (await newInvBtn.isVisible({ timeout: 5000 }).catch(() => false)) {
    await newInvBtn.click();
    await page.waitForTimeout(600);
  }

  // Ensure invoice form is visible
  await page.waitForSelector('.invoice-meta-grid', { timeout: 10000 });

  // Screenshot at 1024x768
  await page.screenshot({ path: path.join(__dirname, 'current_1024_viewport.png') });
  const metaGrid = page.locator('.invoice-meta-grid').first();
  await metaGrid.screenshot({ path: path.join(__dirname, 'current_meta_grid.png') });

  // Screenshot at 1280x900
  await page.setViewportSize({ width: 1280, height: 900 });
  await page.waitForTimeout(300);
  await page.screenshot({ path: path.join(__dirname, 'current_1280_viewport.png') });

  console.log('Captured current screenshots successfully.');
  await browser.close();
})();
