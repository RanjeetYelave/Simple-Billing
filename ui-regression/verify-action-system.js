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
  await page.goto('http://127.0.0.1:28090/index.html', { waitUntil: 'domcontentloaded' });
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

  // ─── 1. Invoices & Quotes Hub: Primary & Secondary Actions ───
  console.log('2. Inspecting Invoices & Quotes Hub actions in Light Mode...');
  await page.evaluate(() => {
    // Dismiss any open modal
    const closeBtn = document.querySelector('.modal-box button[title="Close"], .modal-box .btn-ghost, .modal-box .btn-outline');
    if (closeBtn) closeBtn.click();
    window.dispatchEvent(new CustomEvent('billsoft:navigate-subtab', { detail: { page: 'invoices' } }));
  });
  await page.keyboard.press('Escape');
  await page.waitForTimeout(1000);

  const invoicesHeader = page.locator('.invoices-hub-header, .manager-header, .sub-tabs-bar').first();
  if (await invoicesHeader.isVisible().catch(() => false)) {
    await invoicesHeader.screenshot({ path: path.join(__dirname, 'glass_actions_invoices_header_light.png') });
    console.log('Saved glass_actions_invoices_header_light.png');
  }

  // Capture Invoices subtabs / header area
  await page.screenshot({ path: path.join(__dirname, 'glass_actions_invoices_hub_light.png') });
  console.log('Saved glass_actions_invoices_hub_light.png');

  // Capture pagination controls if present
  const pagination = page.locator('.pagination-controls').first();
  if (await pagination.isVisible().catch(() => false)) {
    await pagination.screenshot({ path: path.join(__dirname, 'glass_actions_pagination_light.png') });
    console.log('Saved glass_actions_pagination_light.png');
  }

  // ─── 4. Dashboard View: Quick Actions & Timeframe Pills ───
  console.log('5. Inspecting Dashboard Quick Actions in Light Mode...');
  await page.evaluate(() => {
    window.dispatchEvent(new CustomEvent('billsoft:navigate-subtab', { detail: { page: 'dashboard' } }));
  });
  await page.waitForTimeout(1000);

  const dashLowerGrid = page.locator('.rcrm-dash-lower-grid').first();
  if (await dashLowerGrid.isVisible().catch(() => false)) {
    await dashLowerGrid.screenshot({ path: path.join(__dirname, 'glass_actions_dashboard_lower_light.png') });
    console.log('Saved glass_actions_dashboard_lower_light.png');
  }

  // ─── 5. ConfirmDialog Trigger & Capture ───
  console.log('6. Inspecting ConfirmDialog modal box & actions...');
  await page.evaluate(() => {
    // Render a sample ConfirmDialog programmatically or click a delete/cancel trigger
    const confirmOverlay = document.createElement('div');
    confirmOverlay.id = 'test-confirm-overlay';
    confirmOverlay.className = 'modal-overlay';
    confirmOverlay.style.cssText = 'position:fixed;inset:0;z-index:9999;display:flex;align-items:center;justify-content:center;background:rgba(15,23,42,0.6);';
    confirmOverlay.innerHTML = `
      <div class="modal-box" style="max-width:480px;width:90%;padding:24px;border-radius:14px;">
        <div style="display:flex;align-items:center;gap:12px;margin-bottom:14px;">
          <span style="font-size:1.75rem;">⚠️</span>
          <h3 style="margin:0;font-size:1.18rem;font-weight:700;color:var(--danger,#ef4444);">Discard Invoice Draft?</h3>
        </div>
        <p style="font-size:0.92rem;color:var(--text-secondary);margin-bottom:22px;">Are you sure you want to discard this draft? Unsaved changes will be lost.</p>
        <div style="display:flex;justify-content:flex-end;gap:10px;">
          <button type="button" class="btn btn-outline btn-sm" id="test-cancel-btn" style="min-width:80px;">Cancel</button>
          <button type="button" class="btn btn-sm btn-danger" id="test-danger-btn" style="min-width:90px;font-weight:600;">Discard</button>
        </div>
      </div>
    `;
    document.body.appendChild(confirmOverlay);
  });
  await page.waitForTimeout(300);

  const testModal = page.locator('#test-confirm-overlay .modal-box');
  if (await testModal.isVisible().catch(() => false)) {
    await testModal.screenshot({ path: path.join(__dirname, 'glass_actions_confirm_modal_light.png') });
    console.log('Saved glass_actions_confirm_modal_light.png');
  }

  // Hover over the discard button to inspect hover specular reflection
  const dangerBtn = page.locator('#test-danger-btn');
  await dangerBtn.hover();
  await page.waitForTimeout(200);
  await testModal.screenshot({ path: path.join(__dirname, 'glass_actions_confirm_modal_danger_hover.png') });
  console.log('Saved glass_actions_confirm_modal_danger_hover.png');

  // Remove test modal
  await page.evaluate(() => {
    const el = document.getElementById('test-confirm-overlay');
    if (el) el.remove();
  });
  await page.waitForTimeout(200);

  // Full page view Light Mode
  await page.screenshot({ path: path.join(__dirname, 'glass_actions_light_full.png') });
  console.log('Saved glass_actions_light_full.png');

  // ─── 6. Modern Dark Mode ───
  console.log('7. Switching to Modern Dark Mode...');
  await page.evaluate(() => {
    if (window.setAppTheme) window.setAppTheme('dark-1');
  });
  await page.waitForTimeout(800);

  // Capture Invoices Header in Dark Mode
  await page.evaluate(() => {
    window.dispatchEvent(new CustomEvent('billsoft:navigate-subtab', { detail: { page: 'invoices' } }));
  });
  await page.waitForTimeout(800);

  if (await invoicesHeader.isVisible().catch(() => false)) {
    await invoicesHeader.screenshot({ path: path.join(__dirname, 'glass_actions_invoices_header_dark.png') });
    console.log('Saved glass_actions_invoices_header_dark.png');
  }

  // ConfirmDialog in Dark Mode
  await page.evaluate(() => {
    const confirmOverlay = document.createElement('div');
    confirmOverlay.id = 'test-confirm-overlay-dark';
    confirmOverlay.className = 'modal-overlay';
    confirmOverlay.style.cssText = 'position:fixed;inset:0;z-index:9999;display:flex;align-items:center;justify-content:center;background:rgba(0,0,0,0.7);';
    confirmOverlay.innerHTML = `
      <div class="modal-box" style="max-width:480px;width:90%;padding:24px;border-radius:14px;">
        <div style="display:flex;align-items:center;gap:12px;margin-bottom:14px;">
          <span style="font-size:1.75rem;">⚠️</span>
          <h3 style="margin:0;font-size:1.18rem;font-weight:700;color:var(--danger,#ef4444);">Discard Invoice Draft?</h3>
        </div>
        <p style="font-size:0.92rem;color:var(--text-secondary);margin-bottom:22px;">Are you sure you want to discard this draft? Unsaved changes will be lost.</p>
        <div style="display:flex;justify-content:flex-end;gap:10px;">
          <button type="button" class="btn btn-outline btn-sm" style="min-width:80px;">Cancel</button>
          <button type="button" class="btn btn-sm btn-danger" style="min-width:90px;font-weight:600;">Discard</button>
        </div>
      </div>
    `;
    document.body.appendChild(confirmOverlay);
  });
  await page.waitForTimeout(300);

  const testModalDark = page.locator('#test-confirm-overlay-dark .modal-box');
  if (await testModalDark.isVisible().catch(() => false)) {
    await testModalDark.screenshot({ path: path.join(__dirname, 'glass_actions_confirm_modal_dark.png') });
    console.log('Saved glass_actions_confirm_modal_dark.png');
  }
  await page.evaluate(() => {
    const el = document.getElementById('test-confirm-overlay-dark');
    if (el) el.remove();
  });
  await page.waitForTimeout(200);

  await page.screenshot({ path: path.join(__dirname, 'glass_actions_dark_full.png') });
  console.log('Saved glass_actions_dark_full.png');

  // ─── 7. Retro Classic Mode (Immunity Verification) ───
  console.log('8. Verifying Retro Classic Mode immunity...');
  await page.evaluate(() => {
    if (window.setAppTheme) window.setAppTheme('classic');
  });
  await page.waitForTimeout(800);
  if (await invoicesHeader.isVisible().catch(() => false)) {
    await invoicesHeader.screenshot({ path: path.join(__dirname, 'classic_actions_invoices_header.png') });
    console.log('Saved classic_actions_invoices_header.png');
  }

  // ─── 8. Centralized Disabled Switch Verification ───
  console.log('9. Verifying Centralized Disabled Switch...');
  await page.evaluate(() => {
    if (window.setAppTheme) window.setAppTheme('light-1');
    document.documentElement.setAttribute('data-liquid-glass', 'false');
  });
  await page.waitForTimeout(800);
  if (await invoicesHeader.isVisible().catch(() => false)) {
    await invoicesHeader.screenshot({ path: path.join(__dirname, 'glass_disabled_actions_invoices_header.png') });
    console.log('Saved glass_disabled_actions_invoices_header.png');
  }

  // Restore
  await page.evaluate(() => {
    document.documentElement.removeAttribute('data-liquid-glass');
  });

  console.log('Console Errors encountered:', consoleErrors.length);
  if (consoleErrors.length > 0) {
    console.log('Console errors:', consoleErrors);
  }

  await browser.close();
  console.log('Action System Verification completed successfully.');
})();
