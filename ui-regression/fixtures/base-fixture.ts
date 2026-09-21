import { test as base, Page, expect } from '@playwright/test';
import { ErrorGate } from '../support/error-gate';

// ─── EULA CONSTANTS ───────────────────────────────────────────────────────────
// These keys are the exact localStorage keys used by the production EulaService.
// They must match the production implementation and must NEVER be changed here
// without a corresponding change in the application.
const EULA_STORAGE_KEY     = 'rupeecrm_eula_status';
const EULA_VERSION_KEY     = 'rupeecrm_eula_version';
const EULA_ACCEPTED_AT_KEY = 'rupeecrm_eula_accepted_at';
const EULA_CURRENT_VERSION = '1.0.0';

// ─── TEST FIRM SEED ───────────────────────────────────────────────────────────
// Used on CI where the backend starts with an empty database.
// The app calls GET /api/firm on startup; when it returns [] the app enters
// the FirstTimeSetup / Onboarding screen, blocking all non-onboarding tests.
//
// Strategy: fetch the REAL backend response first. Only substitute our seed
// when the response is an empty array. This preserves local developer state
// (id=175 etc.) while fixing CI (id=0 seed, no real backend firm).
const TEST_FIRM_SEED_ID = 0;         // Distinct from any real DB id
const TEST_FIRM_SEED = [{
  id: TEST_FIRM_SEED_ID,
  firmName: 'Test Firm (CI Seed)',
}];

// A minimal license/status stub. On CI the backend returns { hasFirm: false }
// for an empty DB, which would trigger the license-expired gate.
// We override only when hasFirm is false.
const TEST_LICENSE_SEED = {
  hasFirm: true,
  isValid: true,
  status: 'trial',
  isTrial: true,
  trialDaysRemaining: 30,
  plan: 'TRIAL',
};

/**
 * EULA mode controls whether the `app` fixture pre-seeds localStorage with an
 * accepted EULA state before the application mounts.
 *
 *  'accepted'    — (default) Inject a deterministic "ACCEPTED" EULA state into
 *                  localStorage BEFORE the application's first JS execution.
 *                  Guarantees the EULA gate never blocks unrelated tests.
 *                  Use this for every ordinary UI regression test.
 *
 *  'first-launch' — Do NOT inject any EULA state.  The BrowserContext starts
 *                   completely clean.  Use this only for dedicated EULA tests
 *                   that must observe the real first-launch EULA gate flow.
 */
export type EulaMode = 'accepted' | 'first-launch';

export type TestOptions = {
  eulaMode: EulaMode;
};

export type TestFixtures = {
  errorGate: ErrorGate;
  app: RupeeCRMAppHelper;
};

export class RupeeCRMAppHelper {
  constructor(public page: Page, public errorGate: ErrorGate) {}

  /**
   * Navigate to the application.
   *
   * @param ensureEulaAccepted  When true (default), inject the accepted EULA
   *   state via addInitScript before navigating.  This is a safety net for any
   *   call site that has NOT already had the state seeded at the fixture level
   *   (e.g. a test that uses `{ page }` directly instead of `{ app }`).
   *   When the `eulaMode: 'accepted'` fixture option is active the state is
   *   already seeded at fixture-setup time, so calling gotoApp(true) is
   *   idempotent and harmless.
   */
  async gotoApp(ensureEulaAccepted = true) {
    if (ensureEulaAccepted) {
      await this.page.addInitScript(
        ({ statusKey, versionKey, acceptedAtKey, version }) => {
          // Only write if not already set — do not overwrite a test that
          // explicitly cleared state for a specific scenario.
          if (!localStorage.getItem(statusKey)) {
            localStorage.setItem(statusKey, 'ACCEPTED');
            localStorage.setItem(versionKey, version);
            localStorage.setItem(acceptedAtKey, new Date().toISOString());
          }
        },
        {
          statusKey:     EULA_STORAGE_KEY,
          versionKey:    EULA_VERSION_KEY,
          acceptedAtKey: EULA_ACCEPTED_AT_KEY,
          version:       EULA_CURRENT_VERSION,
        }
      );
    }
    await this.page.goto('/index.html', { waitUntil: 'domcontentloaded' });
    await this.page.waitForSelector('#root', { timeout: 15000 });
    // Wait for initial firm and status sync
    await this.page.waitForTimeout(500);
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

// ─── COMBINED FIXTURE EXTENSION ──────────────────────────────────────────────

export const test = base.extend<TestOptions & TestFixtures & { _autoSetup: void }>({
  // ── Option: eulaMode ────────────────────────────────────────────────────────
  // Default to 'accepted' so every ordinary test automatically receives a
  // deterministic, pre-accepted EULA state. Only EULA-specific tests should
  // override this to 'first-launch'.
  eulaMode: ['accepted', { option: true }],

  // ── Fixture: errorGate ──────────────────────────────────────────────────────
  errorGate: async ({ page }, use) => {
    const gate = new ErrorGate();
    gate.attach(page);
    await use(gate);
    // Automatically enforce release gate at end of every test
    await gate.assertZeroErrors(page, 'Test Teardown Zero-Error Gate');
  },

  // ── Automatic Preconditions Fixture (Runs for EVERY test) ───────────────────
  _autoSetup: [async ({ page, eulaMode }, use) => {
    // 1. EULA deterministic pre-seeding
    if (eulaMode === 'accepted') {
      await page.addInitScript(
        ({ statusKey, versionKey, acceptedAtKey, version }) => {
          if (!localStorage.getItem(statusKey)) {
            localStorage.setItem(statusKey, 'ACCEPTED');
            localStorage.setItem(versionKey, version);
            localStorage.setItem(acceptedAtKey, new Date().toISOString());
          }
        },
        {
          statusKey:     EULA_STORAGE_KEY,
          versionKey:    EULA_VERSION_KEY,
          acceptedAtKey: EULA_ACCEPTED_AT_KEY,
          version:       EULA_CURRENT_VERSION,
        }
      );
    }

    // 2. Firm & License seed (CI cold-start isolation)
    await page.route('**/api/firm', async (route) => {
      const method = route.request().method().toUpperCase();
      if (method !== 'GET') {
        await route.continue().catch(() => {});
        return;
      }

      try {
        const response = await route.fetch();
        const text = await response.text();
        let firms: any[] = [];
        try { firms = JSON.parse(text); } catch {}

        if (Array.isArray(firms) && firms.length === 0) {
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify(TEST_FIRM_SEED),
          }).catch(() => {});
        } else {
          await route.fulfill({ response }).catch(() => {});
        }
      } catch (e) {
        // Ignored if test ended or connection closed
      }
    });

    await page.route(`**/api/firm/${TEST_FIRM_SEED_ID}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(TEST_FIRM_SEED[0]),
      }).catch(() => {});
    });

    await page.route('**/api/license/status', async (route) => {
      try {
        const response = await route.fetch();
        const text = await response.text();
        let lic: any = {};
        try { lic = JSON.parse(text); } catch {}

        if (lic.hasFirm === false) {
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify(TEST_LICENSE_SEED),
          }).catch(() => {});
        } else {
          await route.fulfill({ response }).catch(() => {});
        }
      } catch (e) {
        // Ignored if test ended
      }
    });

    // 3. Announcement isolation
    await page.route('**/api/announcements*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([])
      }).catch(() => {});
    });

    await use();
  }, { auto: true }],

  // ── Fixture: app ────────────────────────────────────────────────────────────
  app: async ({ page, errorGate }, use) => {
    const helper = new RupeeCRMAppHelper(page, errorGate);
    await use(helper);
  },
});

export { expect };
