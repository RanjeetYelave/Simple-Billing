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

export const test = base.extend<TestOptions & TestFixtures>({
  // ── Option: eulaMode ────────────────────────────────────────────────────────
  // Default to 'accepted' so every ordinary test automatically receives a
  // deterministic, pre-accepted EULA state.  Only EULA-specific tests should
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

  // ── Fixture: app ────────────────────────────────────────────────────────────
  app: async ({ page, errorGate, eulaMode }, use) => {
    // ── Step 1: EULA isolation ─────────────────────────────────────────────
    // Seed accepted EULA state BEFORE the application's first JS execution.
    // addInitScript fires on every navigation, so this covers the initial load
    // as well as any subsequent reload() within the same test.
    //
    // When eulaMode is 'first-launch' we do NOT inject anything — the app will
    // start with a completely clean localStorage (no EULA state) and display
    // the real first-launch EULA gate.
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

    // ── Step 2: Firm & License seed (CI / fresh-DB isolation) ─────────────
    // On CI the Spring Boot backend starts with an empty database.
    // The production App component calls GET /api/firm on startup; when the
    // response is an empty array it sets firstTimeSetup=true and renders the
    // Onboarding screen instead of the main application shell.
    //
    // Strategy: FETCH-THROUGH with fallback.
    //   • Always let the real request go to the backend first.
    //   • If the backend returns an empty firm list (CI cold-start), respond
    //     with our seed stub so the app boots into the main shell.
    //   • If the backend returns real firms (local dev), pass the real response
    //     through unchanged — no interference with local IDs.
    //
    // This means the SAME fixture works identically on both local and CI:
    //   Local: real data passes through, no 404 on GET /api/firm/:realId
    //   CI:    seed firm is used, and GET /api/firm/0 is also stubbed below
    //
    // We also stub GET /api/firm/:id for the seed firm ID to prevent the app
    // from producing a 404 when it fetches the individual seeded firm.
    await page.route('**/api/firm', async (route) => {
      const method = route.request().method().toUpperCase();
      if (method !== 'GET') {
        // Let mutations (POST, PUT, DELETE) pass through unmodified.
        await route.continue();
        return;
      }

      // Fetch the real backend response first.
      const response = await route.fetch();
      const text = await response.text();
      let firms: any[] = [];
      try { firms = JSON.parse(text); } catch { /* ignore parse error */ }

      if (Array.isArray(firms) && firms.length === 0) {
        // Backend has no firms (CI cold-start): substitute the seed firm.
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(TEST_FIRM_SEED),
        });
      } else {
        // Backend has real firms: pass the real response through.
        await route.fulfill({ response });
      }
    });

    // Stub GET /api/firm/0 (the seed firm's individual lookup) so the app
    // doesn't receive a 404 when it fetches firm details by the seeded ID.
    // This route only fires on CI where the seed firm is used; on local the
    // real backend firm ID is served by the pass-through above.
    await page.route(`**/api/firm/${TEST_FIRM_SEED_ID}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(TEST_FIRM_SEED[0]),
      });
    });

    // Similarly, seed the license/status so the app never shows the
    // "TrialExpired" gate on CI (where hasFirm is false in a fresh DB).
    // Same fetch-through approach: only override when hasFirm is false.
    await page.route('**/api/license/status', async (route) => {
      const response = await route.fetch();
      const text = await response.text();
      let lic: any = {};
      try { lic = JSON.parse(text); } catch { /* ignore */ }

      if (lic.hasFirm === false) {
        // CI: no firm in DB — send a valid trial stub with hasFirm:true.
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(TEST_LICENSE_SEED),
        });
      } else {
        // Local (or CI once firm exists): pass real response through.
        await route.fulfill({ response });
      }
    });

    // ── Step 3: Announcement isolation ────────────────────────────────────
    // Route all announcement API calls to return an empty list so that the
    // real GitHub-backed announcement source never affects ordinary regression
    // tests.  Individual announcement tests override this route with their own
    // mock data BEFORE calling app.gotoApp(), so the per-test route takes
    // precedence (Playwright applies the most-recently-registered handler).
    await page.route('**/api/announcements*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([])
      });
    });

    const helper = new RupeeCRMAppHelper(page, errorGate);
    await use(helper);
  },
});

export { expect };
