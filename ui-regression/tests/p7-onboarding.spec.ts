import { test, expect } from '../fixtures/base-fixture';

test.describe('P7-ONB: Fresh Start / First-Time Setup UI/UX Redesign Suite', () => {
  // Helper to mount app in first-time setup state (empty firm list)
  async function gotoFreshStart(page: any) {
    await page.addInitScript(() => {
      sessionStorage.clear();
      localStorage.clear();
      sessionStorage.setItem('__eula_initialized', 'true');
      localStorage.setItem('rupeecrm_eula_status', 'ACCEPTED');
      localStorage.setItem('rupeecrm_eula_version', '1.0.0');
      localStorage.setItem('rupeecrm_eula_accepted_at', new Date().toISOString());

      const originalFetch = window.fetch;
      window.fetch = async function(url, options) {
        const u = String(url);
        if (u.includes('/api/firm') && (!options || !options.method || options.method === 'GET')) {
          return new Response(JSON.stringify([]), { status: 200, headers: { 'Content-Type': 'application/json' } });
        }
        if (u.includes('/api/licens')) {
          return new Response(JSON.stringify({
            isValid: true,
            status: 'trial',
            isTrial: true,
            hasFirm: false,
            trialDaysRemaining: 30,
            plan: 'TRIAL'
          }), { status: 200, headers: { 'Content-Type': 'application/json' } });
        }
        if (u.includes('/api/backup/auto-status')) {
          return new Response(JSON.stringify({ fileExists: true, status: 'HEALTHY', lastModifiedFormatted: 'Today' }), { status: 200, headers: { 'Content-Type': 'application/json' } });
        }
        return originalFetch.apply(this, arguments);
      };
    });

    await page.goto('/index.html', { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('#root', { timeout: 15000 });
    await page.waitForSelector('.onboarding-card', { timeout: 10000 });
  }

  test('P7-ONB-01: Fresh Start Initial Screen Mounting, Branding & Dual-Card Chooser', async ({ page, errorGate }) => {
    await gotoFreshStart(page);

    // 1. Verify modern container and backdrop
    const backdrop = page.locator('.onboarding-backdrop');
    await expect(backdrop).toBeVisible();

    const card = page.locator('.onboarding-card.welcome-mode');
    await expect(card).toBeVisible();

    // 2. Legacy banner and circular 'B' badge must NOT exist
    const legacyBadge = page.locator(':text("B")').filter({ hasText: /^B$/ });
    await expect(legacyBadge).toBeHidden();

    // 3. Official RupeeCRM brand logo and typography
    const wordmark = page.locator(':text("RupeeCRM")').first();
    await expect(wordmark).toBeVisible();

    const subtitle = page.locator('.onboarding-card p').first();
    await expect(subtitle).toContainText('Get started in minutes');

    // 4. Dual Choice Cards
    const newBizCard = page.locator('.onboarding-choice-card.primary-choice');
    await expect(newBizCard).toBeVisible();
    await expect(newBizCard).toContainText('Set Up New Business');
    await expect(newBizCard).toContainText('Create your firm profile');

    const restoreCard = page.locator('.onboarding-choice-card.secondary-choice');
    await expect(restoreCard).toBeVisible();
    await expect(restoreCard).toContainText('Restore from Backup');
    await expect(restoreCard).toContainText('Restore firms, invoices');

    // 5. Trust Indicators / Footnote
    const footerNote = page.locator('.onboarding-footer-note');
    await expect(footerNote).toBeVisible();
    await expect(footerNote).toContainText('100% Offline & Local Data');
    await expect(footerNote).toContainText('Instant Multi-Firm Capability');

    await page.screenshot({ path: '/Users/afk/.gemini/antigravity-ide/brain/3b76a3a9-de93-4de4-bf70-b6353f78dcbc/fresh_start_welcome_desktop.png' });

    await errorGate.assertZeroErrors(page, 'Fresh Start Mounting');
  });

  test('P7-ONB-02: New Business Setup 4-Step Flow, Validation & Back Navigation', async ({ page, errorGate }) => {
    await gotoFreshStart(page);

    // 1. Launch New Business Setup
    const newBizCard = page.locator('.onboarding-choice-card.primary-choice');
    await newBizCard.click();

    // Header & Step Indicator
    const stepCard = page.locator('.onboarding-card.step-mode');
    await expect(stepCard).toBeVisible();
    await expect(page.locator('.onboarding-title')).toContainText('Set Up Your Business');
    await expect(page.locator('.onboarding-subtitle')).toContainText('Step 1 of 4');

    await page.screenshot({ path: '/Users/afk/.gemini/antigravity-ide/brain/3b76a3a9-de93-4de4-bf70-b6353f78dcbc/fresh_start_step1_identity.png' });

    // Step 1: Validation failure when submitting empty firm name
    const nextBtn = page.locator('button:has-text("Next")');
    await nextBtn.click();
    await expect(page.locator(':text("Firm Name is required to continue.")')).toBeVisible();

    // Fill Step 1
    await page.locator('input[placeholder*="Acme"]').fill('Acme Corp India');
    await page.locator('input[placeholder*="John Doe"]').fill('Rajesh Sharma');
    await page.locator('input[placeholder*="9876543210"]').fill('9822012345');
    await page.locator('input[placeholder*="billing@acme.com"]').fill('billing@acmecorp.in');
    await nextBtn.click();

    // Step 2: Address & Banking
    await expect(page.locator('.onboarding-subtitle')).toContainText('Step 2 of 4 — Address & Banking');
    await page.locator('input[placeholder*="Shop / Building"]').fill('Plot 42, MIDC Industrial Area');
    await page.locator('input[placeholder*="Area / Landmark"]').fill('Near Metro Station');
    await page.locator('input[placeholder*="Mumbai"]').fill('Pune');
    await page.locator('input[placeholder*="Maharashtra"]').fill('Maharashtra');
    await page.locator('input[placeholder*="400001"]').fill('411018');
    await page.locator('input[placeholder*="HDFC Bank"]').fill('State Bank of India');
    await page.locator('input[placeholder*="50200012345678"]').fill('302918273645');
    await page.locator('input[placeholder*="HDFC0001234"]').fill('SBIN0001234');
    await page.locator('input[placeholder*="merchant@okaxis"]').fill('acmecorp@sbi');
    await page.locator('button:has-text("Next: Security")').click();

    // Step 3: Security Lock
    await expect(page.locator('.onboarding-subtitle')).toContainText('Step 3 of 4 — Security');
    const lockCheckbox = page.locator('input[type="checkbox"]');
    await lockCheckbox.check();

    // Test password mismatch
    await page.locator('input[type="password"]').first().fill('123456');
    await page.locator('input[type="password"]').nth(1).fill('654321');
    await page.locator('button:has-text("Next: Activation")').click();
    await expect(page.locator(':text("Passwords do not match.")')).toBeVisible();

    // Correct password
    await page.locator('input[type="password"]').nth(1).fill('123456');
    await page.locator('button:has-text("Next: Activation")').click();

    // Step 4: Activation & Trial
    await expect(page.locator('.onboarding-subtitle')).toContainText('Step 4 of 4 — Activation');
    await expect(page.locator('div:text-is("30-Day Free Trial")')).toBeVisible();
    await expect(page.locator('div:text-is("Activate License")')).toBeVisible();
    await expect(page.locator(':text("MACHINE IDENTITY")')).toBeVisible();
    await expect(page.locator('button:has-text("Copy Machine ID")')).toBeVisible();
    await expect(page.locator('button:has-text("Start 30-Day Free Trial")')).toBeVisible();

    // Test Back Navigation through all steps
    const backBtn = page.locator('button:has-text("← Back")');
    await backBtn.click(); // Back to Step 3
    await expect(page.locator('.onboarding-subtitle')).toContainText('Step 3 of 4');

    await backBtn.click(); // Back to Step 2
    await expect(page.locator('.onboarding-subtitle')).toContainText('Step 2 of 4');
    // Ensure form data retained
    await expect(page.locator('input[placeholder*="Mumbai"]')).toHaveValue('Pune');

    await backBtn.click(); // Back to Step 1
    await expect(page.locator('.onboarding-subtitle')).toContainText('Step 1 of 4');
    await expect(page.locator('input[placeholder*="Acme"]')).toHaveValue('Acme Corp India');

    await backBtn.click(); // Back to Welcome Chooser
    await expect(page.locator('.onboarding-choice-card.primary-choice')).toBeVisible();

    await errorGate.assertZeroErrors(page, 'New Business Setup Flow');
  });

  test('P7-ONB-03: Restore from Backup Flow & Back Navigation', async ({ page, errorGate }) => {
    await gotoFreshStart(page);

    // 1. Launch Restore Flow
    const restoreCard = page.locator('.onboarding-choice-card.secondary-choice');
    await restoreCard.click();

    // 2. Verify Step 1 of Restore
    const stepCard = page.locator('.onboarding-card.step-mode');
    await expect(stepCard).toBeVisible();
    await expect(page.locator('.onboarding-title')).toContainText('Restore from Backup');
    await expect(page.locator('.onboarding-subtitle')).toContainText('Step 1 of 3 — Upload & Inspect');

    // Verify Custom File Browse Option
    const browseLabel = page.locator('label:has-text("Browse File")');
    await expect(browseLabel).toBeVisible();

    // 3. Test Back Navigation to Welcome Chooser
    const backBtn = page.locator('button:has-text("← Back")');
    await backBtn.click();

    await expect(page.locator('.onboarding-choice-card.secondary-choice')).toBeVisible();
    await errorGate.assertZeroErrors(page, 'Restore from Backup Flow');
  });

  test('P7-ONB-04: Keyboard Accessibility (Tab, Enter, Space)', async ({ page, errorGate }) => {
    await gotoFreshStart(page);

    // 1. Tab to first choice (Set Up New Business)
    await page.keyboard.press('Tab');
    const focusedCard = page.locator('.onboarding-choice-card.primary-choice');
    await expect(focusedCard).toBeFocused();

    // 2. Activate via Enter key
    await page.keyboard.press('Enter');
    await expect(page.locator('.onboarding-title')).toContainText('Set Up Your Business');

    // Go back
    await page.locator('button:has-text("← Back")').click();
    await expect(page.locator('.onboarding-card.welcome-mode')).toBeVisible();

    // 3. Tab to second choice (Restore from Backup)
    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');
    const restoreCard = page.locator('.onboarding-choice-card.secondary-choice');
    await expect(restoreCard).toBeFocused();

    // 4. Activate via Space key
    await page.keyboard.press('Space');
    await expect(page.locator('.onboarding-title')).toContainText('Restore from Backup');

    await errorGate.assertZeroErrors(page, 'Keyboard Navigation');
  });

  test('P7-ONB-05: Responsive Viewport Adaptability & Zero Overflow', async ({ page, errorGate }) => {
    const viewports = [
      { name: 'Desktop (1280x800)', width: 1280, height: 800 },
      { name: 'Tablet (768x1024)', width: 768, height: 1024 },
      { name: 'Mobile (390x844)', width: 390, height: 844 },
    ];

    for (const vp of viewports) {
      await page.setViewportSize({ width: vp.width, height: vp.height });
      await gotoFreshStart(page);

      // Verify Welcome Chooser has no horizontal overflow
      const overflowWelcome = await page.evaluate(() => {
        return document.documentElement.scrollWidth > window.innerWidth;
      });
      expect(overflowWelcome).toBe(false);

      if (vp.width === 390) {
        await page.screenshot({ path: '/Users/afk/.gemini/antigravity-ide/brain/3b76a3a9-de93-4de4-bf70-b6353f78dcbc/fresh_start_mobile_390.png' });
      }

      // Navigate to Step 2 (dense form)
      await page.locator('.onboarding-choice-card.primary-choice').click();
      await page.locator('input[placeholder*="Acme"]').fill('Responsive Test Firm');
      await page.locator('button:has-text("Next")').click();

      if (vp.width === 390) {
        await page.screenshot({ path: '/Users/afk/.gemini/antigravity-ide/brain/3b76a3a9-de93-4de4-bf70-b6353f78dcbc/fresh_start_mobile_step2.png' });
      }

      // Verify Step 2 grid fields wrap nicely without overflow
      const overflowForm = await page.evaluate(() => {
        return document.documentElement.scrollWidth > window.innerWidth;
      });
      expect(overflowForm).toBe(false);

      // Inputs must be visible and properly sized
      const cityInput = page.locator('input[placeholder*="Mumbai"]');
      await expect(cityInput).toBeVisible();
      const boundingBox = await cityInput.boundingBox();
      expect(boundingBox?.width).toBeGreaterThan(60);
    }

    await errorGate.assertZeroErrors(page, 'Responsive Viewport Test');
  });
});
