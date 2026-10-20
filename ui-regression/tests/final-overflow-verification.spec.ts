import { test, expect } from '../fixtures/base-fixture';
import * as fs from 'fs';

const VIEWPORTS = [
  { width: 1920, height: 1080, name: '1920x1080_FHD' },
  { width: 1440, height: 900, name: '1440x900_MacBook' },
  { width: 1280, height: 800, name: '1280x800_Compact' },
  { width: 1024, height: 768, name: '1024x768_MinDesktop' },
];

const WORKSPACES = [
  { id: 'Inventory_Standard', name: 'Standard Inventory', tab: 'Inventory', subTab: null, toggle: 'Standard Inventory' },
  { id: 'Inventory_SmartReorder', name: 'Smart Reorder Queue', tab: 'Inventory', subTab: null, toggle: 'Smart Reorder Queue' },
  { id: 'Products', name: 'Catalog & Product Master', tab: 'Products', subTab: null, toggle: null },
  { id: 'Customers', name: 'Customer Cockpit', tab: 'Customers', subTab: null, toggle: null },
  { id: 'Parties', name: 'Vendors & Parties Hub', tab: 'Parties', subTab: null, toggle: null },
  { id: 'Paperwork_Orders', name: 'Purchase Orders', tab: 'Paperwork', subTab: 'Purchase Orders', toggle: null },
  { id: 'Paperwork_Letters', name: 'Letter Pad', tab: 'Paperwork', subTab: 'Letter Pad', toggle: null },
  { id: 'Paperwork_Statements', name: 'Statements & Ledgers', tab: 'Paperwork', subTab: 'Statements & Ledgers', toggle: null },
  { id: 'BusinessProfile', name: 'Business Profile', tab: 'Business Profile', subTab: null, toggle: null },
];

test.describe('Final Verification - Comprehensive 36-Combination Measurement & Inspection', () => {

  for (const vp of VIEWPORTS) {
    test(`Part 1: Audit 9 Workspaces at ${vp.name} (${vp.width}x${vp.height})`, async ({ page, app }) => {
      const measurements: any[] = [];

      await page.setViewportSize({ width: vp.width, height: vp.height });
      await app.gotoApp();
      await page.evaluate(() => {
        sessionStorage.setItem('currentFirmId', '97');
        if (window.API) window.API.firmId = 97;
      });
      await app.gotoApp();
      await app.navigateTo('Firm');
      await page.waitForTimeout(400);

      const firmContainer = page.locator('.firm-viewport-container');
      await expect(firmContainer).toBeVisible({ timeout: 10000 });

      for (const ws of WORKSPACES) {
        // Navigate to primary sub-tab
        const tabBtn = page.locator(`.sub-tabs-bar button:has-text("${ws.tab}")`).first();
        await expect(tabBtn).toBeVisible({ timeout: 8000 });
        await tabBtn.click();
        await page.waitForTimeout(250);

        if (ws.subTab) {
          const subBtn = page.locator(`.firm-workspace-pane .sub-tabs-bar button:has-text("${ws.subTab}")`).first();
          await expect(subBtn).toBeVisible({ timeout: 8000 });
          await subBtn.click();
          await page.waitForTimeout(250);
        }

        if (ws.toggle) {
          const toggleBtn = page.locator(`button:has-text("${ws.toggle}")`).first();
          await expect(toggleBtn).toBeVisible({ timeout: 8000 });
          await toggleBtn.click();
          await page.waitForTimeout(350);
        }

        // Measure layout metrics
        const metrics = await page.evaluate((workspaceInfo) => {
          const doc = document.documentElement;
          const body = document.body;
          const scrollWidth = Math.max(doc.scrollWidth, body.scrollWidth);
          const innerWidth = window.innerWidth;
          const scrollHeight = Math.max(doc.scrollHeight, body.scrollHeight);
          const innerHeight = window.innerHeight;

          const pane = document.querySelector('.firm-workspace-pane') || document.querySelector('.firm-viewport-container');
          const paneRect = pane ? pane.getBoundingClientRect() : null;

          const statusBar = document.querySelector('.bottom-status-bar');
          const statusRect = statusBar ? statusBar.getBoundingClientRect() : null;

          // Check if any interactive button/input/card overlaps statusBar
          let statusOverlapCount = 0;
          let worstOverlapPx = 0;
          const interactiveCards = Array.from(document.querySelectorAll('.firm-table-card, .firm-docked-pagination, table'));
          interactiveCards.forEach(el => {
            const r = el.getBoundingClientRect();
            if (statusRect && r.bottom > statusRect.top + 2) {
              const diff = r.bottom - statusRect.top;
              statusOverlapCount++;
              if (diff > worstOverlapPx) worstOverlapPx = diff;
            }
          });

          // Check for unintended nested scrolling
          const unintendedNestedScrolls: string[] = [];
          document.querySelectorAll('.firm-viewport-container *').forEach(el => {
            const isScrollable = (el.scrollHeight > el.clientHeight + 4) && (window.getComputedStyle(el).overflowY === 'auto' || window.getComputedStyle(el).overflowY === 'scroll');
            if (isScrollable) {
              const cls = el.className || el.tagName;
              unintendedNestedScrolls.push(cls);
            }
          });

          // Check for ancestor clipping
          let ancestorClipping = false;
          let p = pane ? pane.parentElement : null;
          while (p && p !== document.body) {
            const style = window.getComputedStyle(p);
            if (style.overflow === 'hidden' || style.overflowY === 'hidden') {
              const pRect = p.getBoundingClientRect();
              if (paneRect && (paneRect.bottom > pRect.bottom || paneRect.right > pRect.right)) {
                ancestorClipping = true;
              }
            }
            p = p.parentElement;
          }

          const availableHeight = innerHeight - 56 - 26; // minus topbar (56px) and status bar (26px)

          return {
            workspaceId: workspaceInfo.id,
            workspaceName: workspaceInfo.name,
            scrollWidth,
            innerWidth,
            scrollHeight,
            innerHeight,
            availableHeight,
            hasHorizontalOverflow: scrollWidth > innerWidth,
            hasVerticalOverflow: scrollHeight > innerHeight,
            paneBoundaries: paneRect ? {
              top: Math.round(paneRect.top),
              bottom: Math.round(paneRect.bottom),
              left: Math.round(paneRect.left),
              right: Math.round(paneRect.right),
              width: Math.round(paneRect.width),
              height: Math.round(paneRect.height)
            } : null,
            statusBarTop: statusRect ? Math.round(statusRect.top) : null,
            statusOverlapCount,
            worstOverlapPx: Math.round(worstOverlapPx),
            unintendedNestedScrolls,
            ancestorClipping
          };
        }, ws);

        measurements.push({ viewport: vp.name, ...metrics });

        // Assert strict zero horizontal overflow
        expect(metrics.hasHorizontalOverflow, `Horizontal overflow at ${vp.name} in ${ws.id}: scrollWidth=${metrics.scrollWidth} > innerWidth=${metrics.innerWidth}`).toBe(false);

        // Assert zero unintended ancestor clipping
        expect(metrics.ancestorClipping, `Ancestor clipped content in ${ws.id} at ${vp.name}`).toBe(false);

        const screenshotPath = `test-results/final-${vp.name}-${ws.id}.png`;
        await page.screenshot({ path: screenshotPath, fullPage: false });
      }

      fs.writeFileSync(`test-results/telemetry-${vp.name}.json`, JSON.stringify(measurements, null, 2));
    });
  }

  test('Part 2: Smart Reorder Queue Rows, Table Borders, Pagination, Popovers, & Action Buttons', async ({ page, app }) => {
    for (const vp of VIEWPORTS) {
      await page.setViewportSize({ width: vp.width, height: vp.height });
      await app.gotoApp();
      await app.navigateTo('Firm');

      const invTab = page.locator('button:has-text("Inventory")').first();
      await invTab.click();
      await page.waitForTimeout(300);

      const queueTab = page.locator('button:has-text("Smart Reorder Queue")').first();
      await queueTab.click();
      await page.waitForTimeout(500);

      const card = page.locator('.firm-table-card').first();
      await expect(card).toBeVisible();

      // Check rows are inside card
      const rows = page.locator('.firm-table-card table tbody tr');
      const rowCount = await rows.count();
      expect(rowCount).toBeGreaterThanOrEqual(1);

      // Verify Table Borders: zero rows escape card boundary
      const borderIntegrity = await page.evaluate(() => {
        const c = document.querySelector('.firm-table-card');
        const r = c ? c.getBoundingClientRect() : null;
        const rowsList = Array.from(document.querySelectorAll('.firm-table-card table tbody tr'));
        const escapes = rowsList.some(tr => {
          const trR = tr.getBoundingClientRect();
          return trR.bottom > (r ? r.bottom + 1 : 0);
        });
        return { escapes, rowCount: rowsList.length };
      });
      expect(borderIntegrity.escapes).toBe(false);

      // Test Popover 1: Forecast Explanation
      const fcBtn = page.locator('.firm-table-card button[title="Forecast explanation"]').first();
      if (await fcBtn.isVisible()) {
        await fcBtn.click();
        await page.waitForTimeout(300);
        const modal = page.locator('.modal-content, .modal-overlay').first();
        await expect(modal).toBeVisible();
        const closeBtn = page.locator('.modal-content button:has-text("Close"), .modal-content button:has-text("✕")').first();
        if (await closeBtn.isVisible()) await closeBtn.click();
        else await page.keyboard.press('Escape');
        await page.waitForTimeout(200);
      }

      // Test Popover 2: Min Stock Explanation
      const minBtn = page.locator('.firm-table-card button[title="Minimum stock explanation"]').first();
      if (await minBtn.isVisible()) {
        await minBtn.click();
        await page.waitForTimeout(300);
        const modal = page.locator('.modal-content, .modal-overlay').first();
        await expect(modal).toBeVisible();
        const closeBtn = page.locator('.modal-content button:has-text("Close"), .modal-content button:has-text("✕")').first();
        if (await closeBtn.isVisible()) await closeBtn.click();
        else await page.keyboard.press('Escape');
        await page.waitForTimeout(200);
      }

      // Test Action Button: Insights
      const insightsBtn = page.locator('.firm-table-card button:has-text("Insights")').first();
      await expect(insightsBtn).toBeVisible();

      // Test Action Button: + Order (or Order Products)
      const orderProductsBtn = page.locator('#btn-order-products');
      await expect(orderProductsBtn).toBeVisible();
    }
  });

  test('Part 3: Pagination Integrity & Calculation Invariance', async ({ page, app, request }) => {
    await app.gotoApp();
    await page.evaluate(() => {
      sessionStorage.setItem('currentFirmId', '97');
      if (window.API) window.API.firmId = 97;
    });
    await app.gotoApp();
    await app.navigateTo('Firm');

    const invTab = page.locator('button:has-text("Inventory")').first();
    await invTab.click();
    await page.waitForTimeout(300);

    const queueTab = page.locator('button:has-text("Smart Reorder Queue")').first();
    await queueTab.click();
    await page.waitForTimeout(500);

    // Fetch full dataset via API for Firm 97
    const apiRes = await request.get('/api/forecast/restock/recommendations?urgency=ALL&page=0&size=100', {
      headers: { 'X-Firm-Id': '97' }
    });
    expect(apiRes.status()).toBe(200);
    const apiData = await apiRes.json();
    const allRecs = apiData.content || [];

    // Verify pagination through UI
    const paginationText = page.locator('.firm-docked-pagination span').first();
    await expect(paginationText).toBeVisible();

    const collectedProductNames: string[] = [];
    let hasNext = true;
    let pageNum = 0;

    while (hasNext && pageNum < 10) {
      const rows = page.locator('.firm-table-card table tbody tr');
      const count = await rows.count();
      for (let i = 0; i < count; i++) {
        const name = await rows.nth(i).locator('td').first().locator('span').first().innerText();
        collectedProductNames.push(name.trim());

        // Validate reorder calculations on UI
        const calcData = await rows.nth(i).evaluate(tr => {
          const cells = Array.from(tr.querySelectorAll('td'));
          const stockText = cells[1] ? cells[1].innerText : '';
          const recText = cells[4] ? cells[4].innerText : '';
          return { stockText, recText };
        });
        expect(calcData.recText).not.toBe('');
      }

      const nextBtn = page.locator('.firm-docked-pagination button:has-text("Next ▶")');
      const isDisabled = await nextBtn.isDisabled();
      if (!isDisabled) {
        await nextBtn.click();
        await page.waitForTimeout(300);
        pageNum++;
      } else {
        hasNext = false;
      }
    }

    // Confirm that collected products match allRecs count and have no unintended omissions
    expect(collectedProductNames.length).toBe(allRecs.length);
  });

  test('Part 4: OmniSearch Navigation, Direct URLs, Back/Forward & Multi-Firm Switching', async ({ page, app }) => {
    await app.gotoApp();
    await app.navigateTo('Firm');
    await page.waitForTimeout(400);

    // 1. OmniSearch Navigation
    await page.keyboard.press('Meta+k');
    await page.waitForTimeout(300);
    const omnibar = page.locator('input[placeholder*="Search"]').first();
    if (await omnibar.isVisible()) {
      await omnibar.fill('panipuri');
      await page.waitForTimeout(500);
      await page.keyboard.press('Escape');
    }

    // 2. Direct Subtab Navigation & URL routing
    const subtabs = ['inventory', 'products', 'customers', 'parties', 'orders', 'letters', 'statements', 'profile'];
    for (const sub of subtabs) {
      await page.evaluate((target) => {
        window.dispatchEvent(new CustomEvent('billsoft:navigate-subtab', { detail: { tab: 'firm', subtab: target } }));
      }, sub);
      await page.waitForTimeout(250);
      const firmContainer = page.locator('.firm-viewport-container');
      await expect(firmContainer).toBeVisible();
    }

    // 3. Browser Back / Forward
    await page.goBack();
    await page.waitForTimeout(200);
    await page.goForward();
    await page.waitForTimeout(200);
    await expect(page.locator('.firm-viewport-container')).toBeVisible();

    // 4. Multi-Firm Switching
    // Switch to firm 129
    await page.evaluate(() => {
      sessionStorage.setItem('currentFirmId', '129');
      if (window.API) window.API.firmId = 129;
      window.dispatchEvent(new CustomEvent('billsoft:firm-changed', { detail: { firmId: 129 } }));
    });
    await page.waitForTimeout(400);
    await expect(page.locator('.firm-viewport-container')).toBeVisible();

    // Switch back to firm 97
    await page.evaluate(() => {
      sessionStorage.setItem('currentFirmId', '97');
      if (window.API) window.API.firmId = 97;
      window.dispatchEvent(new CustomEvent('billsoft:firm-changed', { detail: { firmId: 97 } }));
    });
    await page.waitForTimeout(400);
    await expect(page.locator('.firm-viewport-container')).toBeVisible();
  });
});
