import { test, expect } from '../fixtures/base-fixture';

test.describe('Omnisearch Guide Redesign & Single Source of Truth Suite', () => {

  test.beforeEach(async ({ page, app }) => {
    await app.gotoApp(true);
  });

  async function openOmnisearchGuide(page: any) {
    const omniTrigger = page.locator('.topbar-search-pill, [title*="OmniSearch" i], button:has-text("🔍")').first();
    await omniTrigger.click();
    await expect(page.locator('.omni-modal-overlay, .omni-container').first()).toBeVisible({ timeout: 5000 });

    const guideBtn = page.locator('button:has-text("Guide"), button[title*="Guide"]').first();
    await expect(guideBtn).toBeVisible({ timeout: 5000 });
    await guideBtn.click();
    await expect(page.locator('.omni-power-guide-container')).toBeVisible({ timeout: 5000 });
  }

  test('GUIDE-01: Guide Launch, Heading, & Capability Count', async ({ page, errorGate }) => {
    await openOmnisearchGuide(page);

    // 1. Verify redesigned Power Guide container & heading
    const guideContainer = page.locator('.omni-power-guide-container');
    await expect(guideContainer).toBeVisible();

    const heading = page.locator('.omni-guide-main-heading');
    await expect(heading).toHaveText('What can I ask Omnisearch?');

    // 2. Verify count pill displays capabilities and examples count
    const countPill = page.locator('.omni-guide-count-pill');
    await expect(countPill).toBeVisible();
    const countText = await countPill.textContent();
    expect(countText).toMatch(/\d+\s+Capabilities\s+•\s+\d+\s+Examples/);

    await errorGate.assertZeroErrors(page, 'Guide Launch and Initial Render');
  });

  test('GUIDE-02: Randomized Query Suggestions & Shuffle Action', async ({ page, errorGate }) => {
    await openOmnisearchGuide(page);

    // Check shuffled prompt chips row
    const promptChips = page.locator('.omni-guide-prompt-chip');
    await expect(promptChips.first()).toBeVisible();
    const initialPrompts = await promptChips.allTextContents();
    expect(initialPrompts.length).toBeGreaterThanOrEqual(1);

    // Click shuffle button
    const shuffleBtn = page.locator('button:has-text("🎲 Shuffle Suggestions")');
    await expect(shuffleBtn).toBeVisible();
    await shuffleBtn.click();

    // Verify chips are present after shuffle
    await expect(promptChips.first()).toBeVisible();

    // Click the first prompt chip to try it
    const firstChip = promptChips.first();
    const chipText = (await firstChip.textContent() || '').replace('→', '').trim();
    await firstChip.click();

    // Guide closes and query is filled/executed in Omnisearch
    await expect(page.locator('.omni-power-guide-container')).toBeHidden();
    const omniInput = page.locator('.omni-search-input, input.omni-input').first();
    if (await omniInput.isVisible()) {
      await expect(omniInput).toHaveValue(chipText);
    }

    await errorGate.assertZeroErrors(page, 'Randomized Query Suggestions');
  });

  test('GUIDE-03: Deep Full-Text Search across Units, Currencies, and Vernacular', async ({ page, errorGate }) => {
    await openOmnisearchGuide(page);

    const searchInput = page.locator('.omni-guide-search-input');
    await expect(searchInput).toBeVisible();

    // 1. Search for regional land unit 'guntha'
    await searchInput.fill('guntha');
    await expect(page.locator('.omni-capability-card')).toHaveCount(1);
    await expect(page.locator('.omni-capability-name')).toContainText('Area & Indian Land Unit Conversions');

    // 2. Search for gold mass unit 'tola'
    await searchInput.fill('tola');
    await expect(page.locator('.omni-capability-card')).toHaveCount(1);
    await expect(page.locator('.omni-capability-name')).toContainText('Weight, Mass & Indian Bullion Units');

    // 3. Search for currency 'AED' or 'Dirham'
    await searchInput.fill('AED');
    await expect(page.locator('.omni-capability-card')).toHaveCount(1);
    await expect(page.locator('.omni-capability-name')).toContainText('World Currency Conversions');

    // 4. Search for vernacular phrase 'udhari'
    await searchInput.fill('udhari');
    const cards = page.locator('.omni-capability-card');
    const cardCount = await cards.count();
    expect(cardCount).toBeGreaterThanOrEqual(1);

    // 5. Search for financial concept 'SIP'
    await searchInput.fill('SIP');
    await expect(page.locator('.omni-capability-card')).toHaveCount(1);
    await expect(page.locator('.omni-capability-name')).toContainText('Systematic Investment Plan (SIP)');

    // Clear search
    await page.locator('.omni-guide-search-clear').click();
    await expect(searchInput).toHaveValue('');
    const allCards = await page.locator('.omni-capability-card').count();
    expect(allCards).toBeGreaterThanOrEqual(20);

    await errorGate.assertZeroErrors(page, 'Deep Search Across Metadata');
  });

  test('GUIDE-04: 6 Broad Category Filters', async ({ page, errorGate }) => {
    await openOmnisearchGuide(page);

    const pills = page.locator('.omni-guide-pill');
    await expect(pills).toHaveCount(7); // All + 6 categories

    // Click 'Money & Calculations'
    await page.locator('.omni-guide-pill:has-text("Money & Calculations")').click();
    let cards = page.locator('.omni-capability-card');
    let count = await cards.count();
    expect(count).toBeGreaterThanOrEqual(3);

    // Click 'Dates & Conversions'
    await page.locator('.omni-guide-pill:has-text("Dates & Conversions")').click();
    cards = page.locator('.omni-capability-card');
    count = await cards.count();
    expect(count).toBeGreaterThanOrEqual(4);

    // Click 'Business & Reports'
    await page.locator('.omni-guide-pill:has-text("Business & Reports")').click();
    cards = page.locator('.omni-capability-card');
    count = await cards.count();
    expect(count).toBeGreaterThanOrEqual(4);

    // Click 'All Capabilities'
    await page.locator('.omni-guide-pill:has-text("All Capabilities")').click();
    cards = page.locator('.omni-capability-card');
    count = await cards.count();
    expect(count).toBeGreaterThanOrEqual(20);

    await errorGate.assertZeroErrors(page, 'Category Filter Navigation');
  });

  test('GUIDE-05: Progressive Disclosure Expansion & Actionable Try/Fill', async ({ page, errorGate }) => {
    await openOmnisearchGuide(page);

    // Find the first capability card
    const firstCard = page.locator('.omni-capability-card').first();
    await expect(firstCard).toBeVisible();

    // Expand details
    const expandBtn = firstCard.locator('.omni-expand-toggle-btn');
    await expect(expandBtn).toContainText('Details & Full Syntax');
    await expandBtn.click();

    // Verify expanded panel
    const expandedPanel = firstCard.locator('.omni-capability-expanded-panel');
    await expect(expandedPanel).toBeVisible();
    await expect(expandBtn).toContainText('Collapse');

    // Check for "Fill ✎" or "Try it →" button inside expanded panel
    const tryBtn = expandedPanel.locator('.omni-guide-action-btn:has-text("Try it →")').first();
    await expect(tryBtn).toBeVisible();
    await tryBtn.click();

    // Guide closes and query executes in search
    await expect(page.locator('.omni-power-guide-container')).toBeHidden();

    await errorGate.assertZeroErrors(page, 'Progressive Disclosure and Action Execution');
  });

  test('GUIDE-06: Mobile Viewport Responsiveness (375x667)', async ({ page, errorGate }) => {
    await page.setViewportSize({ width: 375, height: 667 });
    await openOmnisearchGuide(page);

    const guideContainer = page.locator('.omni-power-guide-container');
    await expect(guideContainer).toBeVisible();

    // Header, search input, and cards should all be visible
    await expect(page.locator('.omni-guide-main-heading')).toBeVisible();
    await expect(page.locator('.omni-guide-search-input')).toBeVisible();
    await expect(page.locator('.omni-capability-card').first()).toBeVisible();

    await errorGate.assertZeroErrors(page, 'Mobile Viewport Responsiveness');
  });

});
