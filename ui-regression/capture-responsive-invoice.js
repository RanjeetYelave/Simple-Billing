const { chromium } = require('playwright');
const path = require('path');

(async () => {
  const browser = await chromium.launch({ headless: true });

  // 1. Tablet 768x1024
  {
    const context = await browser.newContext({ viewport: { width: 768, height: 1024 } });
    const page = await context.newPage();
    await page.addInitScript(() => {
      localStorage.setItem('rupeecrm_eula_status', 'ACCEPTED');
      localStorage.setItem('rupeecrm_eula_version', '1.0.0');
      localStorage.setItem('rupeecrm_eula_accepted_at', new Date().toISOString());
      sessionStorage.setItem('employeePin', '0000');
    });
    await page.goto('http://127.0.0.1:28090/index.html', { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('#root', { timeout: 15000 });
    await page.waitForTimeout(500);
    await page.evaluate(() => {
      window.dispatchEvent(new CustomEvent('billsoft:navigate-subtab', { detail: { page: 'invoices' } }));
    });
    await page.waitForTimeout(600);
    const newInvBtn = page.locator('button:has-text("+ Inv"), button:has-text("📄 + New Invoice"), button:has-text("Create Invoice")').first();
    if (await newInvBtn.isVisible({ timeout: 5000 }).catch(() => false)) {
      await newInvBtn.click();
      await page.waitForTimeout(600);
    }
    await page.waitForSelector('.invoice-meta-grid', { timeout: 10000 });
    await page.screenshot({ path: path.join(__dirname, 'narrow_768_viewport.png') });
  }

  // 2. Mobile 390x844
  {
    const context = await browser.newContext({ viewport: { width: 390, height: 844 } });
    const page = await context.newPage();
    await page.addInitScript(() => {
      localStorage.setItem('rupeecrm_eula_status', 'ACCEPTED');
      localStorage.setItem('rupeecrm_eula_version', '1.0.0');
      localStorage.setItem('rupeecrm_eula_accepted_at', new Date().toISOString());
      sessionStorage.setItem('employeePin', '0000');
    });
    await page.goto('http://127.0.0.1:28090/index.html', { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('#root', { timeout: 15000 });
    await page.waitForTimeout(500);
    await page.evaluate(() => {
      window.dispatchEvent(new CustomEvent('billsoft:navigate-subtab', { detail: { page: 'invoices' } }));
    });
    await page.waitForTimeout(600);
    const newInvBtn = page.locator('button:has-text("+ Inv"), button:has-text("📄 + New Invoice"), button:has-text("Create Invoice")').first();
    if (await newInvBtn.isVisible({ timeout: 5000 }).catch(() => false)) {
      await newInvBtn.click();
      await page.waitForTimeout(600);
    }
    await page.waitForSelector('.invoice-meta-grid', { timeout: 10000 });
    await page.screenshot({ path: path.join(__dirname, 'narrow_390_viewport.png') });
  }

  console.log('Captured responsive screenshots successfully.');
  await browser.close();
})();
