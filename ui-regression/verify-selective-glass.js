const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

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

  console.log('--- 1. Testing Invoice Screen Actions (Light Mode) ---');
  await page.goto('http://127.0.0.1:28090/index.html', { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#root', { timeout: 15000 });
  await page.waitForTimeout(600);

  // Dismiss modal overlay if any
  await page.keyboard.press('Escape');
  await page.evaluate(() => {
    if (window.setAppTheme) window.setAppTheme('light-1');
  });
  await page.waitForTimeout(500);

  // Navigate to invoice creation/form
  await page.evaluate(() => {
    window.dispatchEvent(new CustomEvent('billsoft:navigate-subtab', { detail: { page: 'invoices', quickDocType: 'invoice' } }));
  });
  await page.waitForTimeout(1500);

  // Capture bottom action dock on Invoice
  const invoiceBottomDock = page.locator('.invoice-form-bottom-actions, .invoice-bottom-dock').first();
  if (await invoiceBottomDock.isVisible().catch(() => false)) {
    await invoiceBottomDock.screenshot({ path: path.join(__dirname, 'selective_invoice_bottom_actions_light.png') });
    console.log('Saved selective_invoice_bottom_actions_light.png');
  }

  // Inspect computed styles of Invoice buttons
  const invoiceStyles = await page.evaluate(() => {
    const primaryBtn = document.querySelector('.invoice-bottom-dock .btn-primary, .invoice-form-bottom-actions .btn-primary');
    const draftBtn = document.querySelector('.invoice-bottom-dock .btn-ghost, .invoice-form-bottom-actions .btn-ghost');
    const printBtn = document.querySelector('.invoice-bottom-dock .btn-outline, .invoice-form-bottom-actions .btn-outline');

    const getBtnInfo = (el) => {
      if (!el) return null;
      const cs = window.getComputedStyle(el);
      const beforeCs = window.getComputedStyle(el, '::before');
      return {
        text: el.innerText.trim(),
        bg: cs.backgroundColor,
        bgImg: cs.backgroundImage,
        backdropFilter: cs.backdropFilter || cs.webkitBackdropFilter,
        boxShadow: cs.boxShadow,
        border: cs.border,
        color: cs.color,
        beforeContent: beforeCs.content
      };
    };

    return {
      saveInvoice: getBtnInfo(primaryBtn),
      saveDraft: getBtnInfo(draftBtn),
      savePrint: getBtnInfo(printBtn)
    };
  });
  console.log('Invoice Button Styles:', JSON.stringify(invoiceStyles, null, 2));

  // Capture full Invoice screen
  await page.screenshot({ path: path.join(__dirname, 'selective_invoice_screen_light.png') });
  console.log('Saved selective_invoice_screen_light.png');

  // --- 2. Testing Inventory Toolbar: Smart Reorder, Add Product, Bulk Actions ---
  console.log('--- 2. Testing Inventory Toolbar Actions (Light Mode) ---');
  await page.evaluate(() => {
    window.dispatchEvent(new CustomEvent('billsoft:navigate-subtab', { detail: { page: 'inventory' } }));
  });
  await page.waitForTimeout(1000);

  const invToolbar = page.locator('.inventory-header, .firm-kpi-ribbon').first();
  await page.screenshot({ path: path.join(__dirname, 'selective_inventory_toolbar_light.png') });
  console.log('Saved selective_inventory_toolbar_light.png');

  const invStyles = await page.evaluate(() => {
    const smartReorderBtn = Array.from(document.querySelectorAll('button')).find(b => b.textContent.includes('Smart Reorder'));
    const addProductBtn = Array.from(document.querySelectorAll('button')).find(b => b.textContent.includes('Add Product'));
    const bulkAddBtn = Array.from(document.querySelectorAll('button')).find(b => b.textContent.includes('Bulk Add'));
    const duplicatesBtn = document.getElementById('btn-duplicates-merge') || Array.from(document.querySelectorAll('button')).find(b => b.textContent.includes('Duplicates'));
    const movementBtn = Array.from(document.querySelectorAll('button')).find(b => b.textContent.includes('Movement Ledger'));

    const getBtnInfo = (el) => {
      if (!el) return null;
      const cs = window.getComputedStyle(el);
      const beforeCs = window.getComputedStyle(el, '::before');
      return {
        text: el.innerText.trim(),
        className: el.className,
        bg: cs.backgroundColor,
        bgImg: cs.backgroundImage,
        backdropFilter: cs.backdropFilter || cs.webkitBackdropFilter,
        boxShadow: cs.boxShadow,
        border: cs.border,
        color: cs.color,
        beforeContent: beforeCs.content
      };
    };

    return {
      smartReorder: getBtnInfo(smartReorderBtn),
      addProduct: getBtnInfo(addProductBtn),
      bulkAdd: getBtnInfo(bulkAddBtn),
      duplicates: getBtnInfo(duplicatesBtn),
      movementLedger: getBtnInfo(movementBtn)
    };
  });
  console.log('Inventory Button Styles:', JSON.stringify(invStyles, null, 2));

  // --- 3. Topbar Controls Liquid Glass Verification ---
  console.log('--- 3. Testing Topbar Liquid Glass (Light Mode) ---');
  const topbar = page.locator('.topbar, header.topbar').first();
  if (await topbar.isVisible().catch(() => false)) {
    await topbar.screenshot({ path: path.join(__dirname, 'selective_topbar_light.png') });
    console.log('Saved selective_topbar_light.png');
  }

  // --- 4. Confirm Dialog: Discard vs Cancel ---
  console.log('--- 4. Testing Confirm Dialog: Discard vs Cancel ---');
  await page.evaluate(() => {
    const overlay = document.createElement('div');
    overlay.id = 'selective-confirm-overlay';
    overlay.className = 'modal-overlay';
    overlay.style.cssText = 'position:fixed;inset:0;z-index:9999;display:flex;align-items:center;justify-content:center;background:rgba(15,23,42,0.6);';
    overlay.innerHTML = `
      <div class="modal-box" style="max-width:480px;width:90%;padding:24px;border-radius:14px;">
        <div style="display:flex;align-items:center;gap:12px;margin-bottom:14px;">
          <span style="font-size:1.75rem;">⚠️</span>
          <h3 style="margin:0;font-size:1.18rem;font-weight:700;color:var(--danger,#ef4444);">Discard Invoice Changes?</h3>
        </div>
        <p style="font-size:0.92rem;color:var(--text-secondary);margin-bottom:22px;">Are you sure you want to discard unsaved items? This action cannot be undone.</p>
        <div style="display:flex;justify-content:flex-end;gap:10px;">
          <button type="button" class="btn btn-outline btn-sm" id="btn-dialog-cancel" style="min-width:80px;">Cancel</button>
          <button type="button" class="btn btn-sm btn-danger" id="btn-dialog-discard" style="min-width:90px;font-weight:600;">Discard</button>
        </div>
      </div>
    `;
    document.body.appendChild(overlay);
  });
  await page.waitForTimeout(300);

  const confirmBox = page.locator('#selective-confirm-overlay .modal-box');
  await confirmBox.screenshot({ path: path.join(__dirname, 'selective_confirm_dialog_light.png') });
  console.log('Saved selective_confirm_dialog_light.png');

  const dialogStyles = await page.evaluate(() => {
    const cancelBtn = document.getElementById('btn-dialog-cancel');
    const discardBtn = document.getElementById('btn-dialog-discard');
    const getBtnInfo = (el) => {
      if (!el) return null;
      const cs = window.getComputedStyle(el);
      const beforeCs = window.getComputedStyle(el, '::before');
      return {
        text: el.innerText.trim(),
        className: el.className,
        bg: cs.backgroundColor,
        bgImg: cs.backgroundImage,
        backdropFilter: cs.backdropFilter || cs.webkitBackdropFilter,
        boxShadow: cs.boxShadow,
        beforeContent: beforeCs.content
      };
    };
    return {
      cancel: getBtnInfo(cancelBtn),
      discard: getBtnInfo(discardBtn)
    };
  });
  console.log('Confirm Dialog Button Styles:', JSON.stringify(dialogStyles, null, 2));

  // --- 5. Modern Dark Mode Verification ---
  console.log('--- 5. Testing Modern Dark Mode ---');
  await page.evaluate(() => {
    if (window.setAppTheme) window.setAppTheme('dark-1');
  });
  await page.waitForTimeout(600);
  await confirmBox.screenshot({ path: path.join(__dirname, 'selective_confirm_dialog_dark.png') });
  console.log('Saved selective_confirm_dialog_dark.png');

  // Dismiss confirm dialog
  await page.evaluate(() => {
    const el = document.getElementById('selective-confirm-overlay');
    if (el) el.remove();
  });
  await page.waitForTimeout(300);

  await page.screenshot({ path: path.join(__dirname, 'selective_inventory_screen_dark.png') });
  console.log('Saved selective_inventory_screen_dark.png');

  if (await topbar.isVisible().catch(() => false)) {
    await topbar.screenshot({ path: path.join(__dirname, 'selective_topbar_dark.png') });
    console.log('Saved selective_topbar_dark.png');
  }

  // --- 6. Retro Classic & Classic Dark Immunity ---
  console.log('--- 6. Testing Retro Classic Theme Immunity ---');
  await page.evaluate(() => {
    if (window.setAppTheme) window.setAppTheme('classic');
  });
  await page.waitForTimeout(600);
  await page.screenshot({ path: path.join(__dirname, 'selective_classic_light_full.png') });
  console.log('Saved selective_classic_light_full.png');

  console.log('--- 7. Testing Classic Dark Theme Immunity ---');
  await page.evaluate(() => {
    if (window.setAppTheme) window.setAppTheme('classic-dark');
  });
  await page.waitForTimeout(600);
  await page.screenshot({ path: path.join(__dirname, 'selective_classic_dark_full.png') });
  console.log('Saved selective_classic_dark_full.png');

  console.log('Done! Console errors:', consoleErrors.length);
  await browser.close();
})();
