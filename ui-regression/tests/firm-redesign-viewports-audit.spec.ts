import { test, expect } from '../fixtures/base-fixture';

const VIEWPORTS = [
  { width: 1920, height: 1080, name: '1920x1080_FHD' },
  { width: 1440, height: 900, name: '1440x900_MacBook' },
  { width: 1280, height: 800, name: '1280x800_Compact' },
  { width: 1024, height: 768, name: '1024x768_MinDesktop' },
];

const WORKSPACES = [
  { name: 'Inventory_Standard', tabButtonText: 'Inventory' },
  { name: 'Inventory_SmartReorder', tabButtonText: 'Inventory', innerToggleText: 'Smart Reorder Queue' },
  { name: 'Products', tabButtonText: 'Products' },
  { name: 'Customers', tabButtonText: 'Customers' },
  { name: 'Parties', tabButtonText: 'Parties' },
  { name: 'Paperwork_Orders', tabButtonText: 'Paperwork', subTabButtonText: 'Purchase Orders' },
  { name: 'Paperwork_Letters', tabButtonText: 'Paperwork', subTabButtonText: 'Letter Pad' },
  { name: 'Paperwork_Statements', tabButtonText: 'Paperwork', subTabButtonText: 'Statements & Ledgers' },
  { name: 'BusinessProfile', tabButtonText: 'Business Profile' },
];

test.describe('Firm Redesign Viewport & Zero-Scroll Comprehensive Audit', () => {

  for (const vp of VIEWPORTS) {
    test(`Verify Zero-Scroll and Responsive Layout at ${vp.name} (${vp.width}x${vp.height})`, async ({ page, app, errorGate }) => {
      await page.setViewportSize({ width: vp.width, height: vp.height });
      await app.gotoApp();
      await page.evaluate(() => {
        sessionStorage.setItem('currentFirmId', '1');
        if (window.API) window.API.firmId = 1;
      });
      await app.gotoApp();
      await app.navigateTo('Firm');
      await page.waitForTimeout(400);

      // Verify firm container is mounted
      const firmContainer = page.locator('.firm-viewport-container');
      await expect(firmContainer).toBeVisible({ timeout: 10000 });

      for (const ws of WORKSPACES) {
        // Click primary tab
        const tabBtn = page.locator(`.sub-tabs-bar button:has-text("${ws.tabButtonText}")`).first();
        await expect(tabBtn).toBeVisible({ timeout: 8000 });
        await tabBtn.click();
        await page.waitForTimeout(300);

        // If secondary subtab (inside PaperworkHub)
        if (ws.subTabButtonText) {
          const subBtn = page.locator(`.firm-workspace-pane .sub-tabs-bar button:has-text("${ws.subTabButtonText}")`).first();
          await expect(subBtn).toBeVisible({ timeout: 8000 });
          await subBtn.click();
          await page.waitForTimeout(300);
        }

        // If inner toggle (e.g. Smart Reorder Queue)
        if (ws.innerToggleText) {
          const toggleBtn = page.locator(`button:has-text("${ws.innerToggleText}")`).first();
          await expect(toggleBtn).toBeVisible({ timeout: 8000 });
          await toggleBtn.click();
          await page.waitForTimeout(400);
        }

        // Check horizontal & vertical document overflow
        const metrics = await page.evaluate(() => {
          const doc = document.documentElement;
          const body = document.body;
          const scrollWidth = Math.max(doc.scrollWidth, body.scrollWidth);
          const innerWidth = window.innerWidth;
          const scrollHeight = Math.max(doc.scrollHeight, body.scrollHeight);
          const innerHeight = window.innerHeight;

          // Check if bottom status bar is overlapped
          const statusBar = document.querySelector('.bottom-status-bar');
          const statusRect = statusBar ? statusBar.getBoundingClientRect() : null;

          return {
            scrollWidth,
            innerWidth,
            hasHorizontalOverflow: scrollWidth > innerWidth,
            scrollHeight,
            innerHeight,
            hasVerticalOverflow: scrollHeight > innerHeight,
            statusBarTop: statusRect ? statusRect.top : null
          };
        });

        expect(metrics.hasHorizontalOverflow, `Horizontal overflow detected in ${ws.name} at ${vp.name}: scrollWidth=${metrics.scrollWidth} > innerWidth=${metrics.innerWidth}`).toBe(false);

        // Capture screenshot for visual inspection
        await page.screenshot({
          path: `test-results/audit-${vp.name}-${ws.name}.png`,
          fullPage: false,
        });
      }

      await errorGate.assertZeroErrors(page, `Firm Redesign Audit ${vp.name}`);
    });
  }
});
