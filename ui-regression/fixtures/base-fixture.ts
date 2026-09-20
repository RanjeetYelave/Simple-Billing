import { test as base, Page, expect } from '@playwright/test';
import { ErrorGate } from '../support/error-gate';

export type TestOptions = {
  eulaMode: 'accepted' | 'first-launch';
};

export type TestFixtures = {
  errorGate: ErrorGate;
  app: RupeeCRMAppHelper;
};

export class RupeeCRMAppHelper {
  constructor(public page: Page, public errorGate: ErrorGate) {}

  async gotoApp(ensureEulaAccepted = true) {
    if (ensureEulaAccepted) {
      await this.page.addInitScript(() => {
        try {
          if (!sessionStorage.getItem('__rupeecrm_preseeded')) {
            sessionStorage.setItem('__rupeecrm_preseeded', 'true');
            if (!localStorage.getItem('rupeecrm_eula_status')) {
              localStorage.setItem('rupeecrm_eula_status', 'ACCEPTED');
              localStorage.setItem('rupeecrm_eula_version', '1.0.0');
              localStorage.setItem('rupeecrm_eula_accepted_at', new Date().toISOString());
            }
            if (!localStorage.getItem('rupeecrm_announcements_ack')) {
              localStorage.setItem('rupeecrm_announcements_ack', 'true');
            }
          }
        } catch (e) {}
      });
    }
    await this.page.goto('/index.html', { waitUntil: 'domcontentloaded' });
    await this.page.waitForSelector('#root', { timeout: 15000 });
    // Wait for initial firm and status sync
    await this.page.waitForTimeout(400);
    await this.errorGate.assertZeroErrors(this.page, 'Initial App Mount');
  }

  async navigateTo(pageId: string) {
    const targetMap: Record<string, string> = {
      dashboard: 'Dashboard',
      invoices: 'Invoices & Quotes',
      firm: 'Firm',
      planner: 'Planner',
      inbox: 'Inbox',
      hr: 'HR',
      settings: 'Settings'
    };
    const lookup = pageId.toLowerCase();
    const matchedKey = Object.keys(targetMap).find(k => lookup.includes(k)) || 'dashboard';
    const label = targetMap[matchedKey];

    const navBtn = this.page.locator(`.nav-item:has-text("${label}")`).first();
    if (await navBtn.isVisible()) {
      await navBtn.click();
    } else {
      await this.page.evaluate((target) => {
        window.dispatchEvent(new CustomEvent('billsoft:navigate-subtab', { detail: { page: target } }));
      }, matchedKey);
    }
    await this.page.waitForTimeout(400);
    await this.errorGate.assertZeroErrors(this.page, `Navigation to ${pageId}`);
  }

  async switchFirm(firmIdOrName: string) {
    const firmSelect = this.page.locator('select.firm-select, select[title*="firm" i], .firm-dropdown select').first();
    if (await firmSelect.isVisible()) {
      await firmSelect.selectOption({ label: firmIdOrName }).catch(async () => {
        await firmSelect.selectOption({ value: String(firmIdOrName) });
      });
      await this.page.waitForTimeout(400);
    }
    await this.errorGate.assertZeroErrors(this.page, `Switch Firm to ${firmIdOrName}`);
  }

  async openGlobalSearch() {
    await this.page.keyboard.press('Meta+k');
    const modal = this.page.locator('.omni-container, .omni-modal-overlay');
    await expect(modal).toBeVisible({ timeout: 5000 });
    await this.errorGate.assertZeroErrors(this.page, 'Open Global Search');
  }

  async closeGlobalSearch() {
    await this.page.keyboard.press('Escape');
    const modal = this.page.locator('.omni-modal-overlay');
    await expect(modal).toBeHidden({ timeout: 5000 });
    await this.errorGate.assertZeroErrors(this.page, 'Close Global Search');
  }
}

export const test = base.extend<TestOptions & TestFixtures>({
  eulaMode: ['accepted', { option: true }],

  errorGate: async ({ page }, use) => {
    const gate = new ErrorGate();
    gate.attach(page);
    await use(gate);
    // Automatically enforce release gate at end of every test
    await gate.assertZeroErrors(page, 'Test Teardown Zero-Error Gate');
  },

  app: [async ({ page, errorGate, eulaMode }, use, testInfo) => {
    // 1. Default deterministic empty announcements so tests are never blocked by startup modal or real network
    await page.route('**/api/announcements*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([])
      });
    });

    // 2. Automatic EULA acceptance initialization for normal regression tests
    const isFirstLaunch = eulaMode === 'first-launch' || testInfo.file.includes('p3-eula');
    if (!isFirstLaunch) {
      await page.addInitScript(() => {
        try {
          if (!sessionStorage.getItem('__rupeecrm_preseeded')) {
            sessionStorage.setItem('__rupeecrm_preseeded', 'true');
            if (!localStorage.getItem('rupeecrm_eula_status')) {
              localStorage.setItem('rupeecrm_eula_status', 'ACCEPTED');
              localStorage.setItem('rupeecrm_eula_version', '1.0.0');
              localStorage.setItem('rupeecrm_eula_accepted_at', new Date().toISOString());
            }
            if (!localStorage.getItem('rupeecrm_announcements_ack')) {
              localStorage.setItem('rupeecrm_announcements_ack', 'true');
            }
          }
        } catch (e) {}
      });
    }

    const helper = new RupeeCRMAppHelper(page, errorGate);
    await use(helper);
  }, { auto: true }],
});

export { expect };
