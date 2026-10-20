import { test, expect } from '../fixtures/base-fixture';

const VIEWPORTS = [
  { name: '1920x1080_FHD', width: 1920, height: 1080 },
  { name: '1440x900_MacBook', width: 1440, height: 900 },
  { name: '1280x800_Compact', width: 1280, height: 800 },
  { name: '1024x768_MinDesktop', width: 1024, height: 768 }
];

test.describe('Smart Reorder Table Escaping & Multi-Viewport Verification', () => {
  for (const vp of VIEWPORTS) {
    test(`Verify Row Containment, Border Integrity, and Zero Overlap at ${vp.name}`, async ({ app, page, request }) => {
      await page.setViewportSize({ width: vp.width, height: vp.height });

      // Switch to firm 1 (Sangam Hardware)
      await app.gotoApp();
      await page.evaluate(() => {
        sessionStorage.setItem('currentFirmId', '1');
        if (window.API) window.API.firmId = 1;
      });
      await app.gotoApp();
      await app.navigateTo('Firm');

      const invTab = page.locator('button:has-text("Inventory")').first();
      await expect(invTab).toBeVisible({ timeout: 10000 });
      await invTab.click();
      await page.waitForTimeout(400);

      // Switch to Smart Reorder Queue tab
      const queueTab = page.locator('button:has-text("Smart Reorder Queue")').first();
      await expect(queueTab).toBeVisible({ timeout: 10000 });
      await queueTab.click();
      await page.waitForTimeout(600);

      // Collect DOM metrics
      const diagnostics = await page.evaluate(() => {
        const card = document.querySelector('.firm-table-card');
        const table = card ? card.querySelector('table') : null;
        const rows = card ? Array.from(card.querySelectorAll('tbody tr')) : [];
        const pagination = card ? card.querySelector('.firm-docked-pagination') : document.querySelector('.firm-docked-pagination');
        const statusBar = document.querySelector('.bottom-status-bar');

        const cardRect = card ? card.getBoundingClientRect() : null;
        const tableRect = table ? table.getBoundingClientRect() : null;
        const paginationRect = pagination ? pagination.getBoundingClientRect() : null;
        const statusBarRect = statusBar ? statusBar.getBoundingClientRect() : null;

        const rowMetrics = rows.map((r, i) => {
          const rect = r.getBoundingClientRect();
          const escapesBottom = cardRect ? rect.bottom > cardRect.bottom + 1 : false;
          const escapesTop = cardRect ? rect.top < cardRect.top - 1 : false;
          const overlapsStatus = statusBarRect ? rect.bottom > statusBarRect.top : false;
          const colHeights = Array.from(r.children).map((td, cIdx) => ({
            col: cIdx,
            h: td.getBoundingClientRect().height,
            w: td.getBoundingClientRect().width,
            txt: td.innerText.replace(/\n/g, ' ')
          }));
          return {
            index: i,
            text: r.innerText.split('\n')[0],
            top: rect.top,
            bottom: rect.bottom,
            height: rect.height,
            colHeights,
            escapesBottom,
            escapesTop,
            overlapsStatus
          };
        });

        const docScrollWidth = document.documentElement.scrollWidth;
        const docInnerWidth = window.innerWidth;
        const docScrollHeight = document.documentElement.scrollHeight;
        const docInnerHeight = window.innerHeight;

        return {
          cardRect,
          tableRect,
          paginationRect,
          statusBarRect,
          rowsCount: rows.length,
          rowMetrics,
          hasHorizontalOverflow: docScrollWidth > docInnerWidth,
          hasVerticalOverflow: docScrollHeight > docInnerHeight,
          docScrollWidth,
          docInnerWidth,
          docScrollHeight,
          docInnerHeight,
          paginationOverlapsStatusBar: (paginationRect && statusBarRect) ? paginationRect.bottom > statusBarRect.top : false,
          cardOverlapsStatusBar: (cardRect && statusBarRect) ? cardRect.bottom > statusBarRect.top : false
        };
      });

      console.log(`[METRICS ${vp.name}]:`, JSON.stringify(diagnostics, null, 2));

      // Assertions
      expect(diagnostics.rowsCount, 'Should have recommendation rows rendered').toBeGreaterThanOrEqual(1);

      // Assertion 1: Zero product rows escape table or card
      for (const row of diagnostics.rowMetrics) {
        expect(row.escapesBottom, `Row ${row.index} (${row.text}) escapes card bottom: row.bottom=${row.bottom} > card.bottom=${diagnostics.cardRect.bottom}`).toBe(false);
        expect(row.escapesTop, `Row ${row.index} (${row.text}) escapes card top`).toBe(false);
        expect(row.overlapsStatus, `Row ${row.index} (${row.text}) overlaps fixed bottom status bar: row.bottom=${row.bottom} > statusBar.top=${diagnostics.statusBarRect.top}`).toBe(false);
      }

      // Assertion 2: Pagination is inside card and does not overlap status bar
      expect(diagnostics.cardRect.bottom, `Pagination footer should be inside card: pagination.bottom=${diagnostics.paginationRect.bottom} <= card.bottom=${diagnostics.cardRect.bottom}`)
        .toBeGreaterThanOrEqual(diagnostics.paginationRect.bottom - 1);

      expect(diagnostics.paginationOverlapsStatusBar, `Pagination footer overlaps status bar at ${vp.name}: pagination.bottom=${diagnostics.paginationRect.bottom} > statusBar.top=${diagnostics.statusBarRect.top}`)
        .toBe(false);

      // Assertion 3: Zero horizontal overflow
      expect(diagnostics.hasHorizontalOverflow, `Horizontal overflow at ${vp.name}: scrollWidth=${diagnostics.docScrollWidth} > innerWidth=${diagnostics.docInnerWidth}`).toBe(false);

      // Assertion 4: Zero vertical page overflow
      expect(diagnostics.hasVerticalOverflow, `Vertical page overflow at ${vp.name}: scrollHeight=${diagnostics.docScrollHeight} > innerHeight=${diagnostics.docInnerHeight}`).toBe(false);

      await page.screenshot({ path: `test-results/audit-fixed-reorder-${vp.name}.png`, fullPage: false });
    });
  }
});
