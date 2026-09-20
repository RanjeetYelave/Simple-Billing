import { test, expect } from '../fixtures/base-fixture';

test.describe('P4-CALC: Unified Casio Calculator & Omnisearch NLP Engine', () => {

  test('P4-CALC-01: Header Calculator Button (Text & Icon) & Keyboard Shortcuts Open and Close Unified Window', async ({ page, app, errorGate }) => {
    await app.gotoApp(true);

    const calcWindow = page.locator('#rupeecrm-calculator-window');
    await expect(calcWindow).toBeHidden();

    // 1. Open via topbar Calculator button that clearly says "Calculator"
    const topbarBtn = page.locator('#topbar-calculator-btn');
    await expect(topbarBtn).toBeVisible();
    await expect(topbarBtn).toContainText('Calculator');
    await topbarBtn.click();

    await expect(calcWindow).toBeVisible();
    await expect(page.locator('.casio-calc-casing')).toBeVisible();
    await expect(page.locator('.casio-solar-cell')).toBeVisible();
    await expect(page.locator('#calc-unified-input')).toBeVisible();
    await expect(page.locator('#calc-lcd-digits')).toHaveText('0');

    // Verify auto-focus
    await expect(page.locator('#calc-unified-input')).toBeFocused();

    // 2. Close via Escape key
    await page.keyboard.press('Escape');
    await expect(calcWindow).toBeHidden();

    // 3. Open via Alt+C shortcut
    await page.keyboard.press('Alt+c');
    await expect(calcWindow).toBeVisible();

    // 4. Minimize (Collapse Keypad) -> LCD & Input stay active as a compact bar
    const minBtn = page.locator('#calc-minimize-btn');
    await expect(minBtn).toHaveText('−');
    await minBtn.click();
    await expect(minBtn).toHaveText('+');
    await expect(page.locator('.casio-keypad-grid')).toBeHidden();
    await expect(page.locator('#calc-unified-input')).toBeVisible();
    await expect(page.locator('#calc-lcd-digits')).toBeVisible();

    // Verify typing works while keypad is collapsed
    const input = page.locator('#calc-unified-input');
    await input.fill('12500 + 18%');
    await expect(page.locator('#calc-lcd-digits')).toHaveText('14,750');

    // Restore full keypad
    await minBtn.click();
    await expect(minBtn).toHaveText('−');
    await expect(page.locator('.casio-keypad-grid')).toBeVisible();
    await expect(page.locator('#calc-lcd-digits')).toHaveText('14,750');

    // 5. Close via X button
    const closeBtn = page.locator('#calc-close-btn');
    await closeBtn.click();
    await expect(calcWindow).toBeHidden();

    await errorGate.assertZeroErrors(page, 'Calculator Launcher & Window Controls');
  });

  test('P4-CALC-02: Live Keystroke Calculation for Normal Arithmetic (No Enter Required, 100% Local)', async ({ page, app, errorGate }) => {
    await app.gotoApp(true);
    await page.locator('#topbar-calculator-btn').click();

    const unifiedInput = page.locator('#calc-unified-input');
    const digits = page.locator('#calc-lcd-digits');

    // 1. Live keystroke: type "12500 + 500" without pressing Enter
    await unifiedInput.fill('12500 + 500');
    await expect(digits).toHaveText('13,000');

    // 2. Type "100 * 25" live
    await unifiedInput.fill('100 * 25');
    await expect(digits).toHaveText('2,500');

    // 3. Percentage arithmetic: "8500 - 10%"
    await unifiedInput.fill('8500 - 10%');
    await expect(digits).toHaveText('7,650');

    // 4. Parentheses: "(500 + 250) * 2"
    await unifiedInput.fill('(500 + 250) * 2');
    await expect(digits).toHaveText('1,500');

    await errorGate.assertZeroErrors(page, 'Live Normal Arithmetic Keystrokes');
  });

  test('P4-CALC-03: Live Keystroke NLP & Conversion Queries (Immediate Debounced Live Results)', async ({ page, app, errorGate }) => {
    await app.gotoApp(true);
    await page.locator('#topbar-calculator-btn').click();

    const unifiedInput = page.locator('#calc-unified-input');
    const digits = page.locator('#calc-lcd-digits');

    // 1. NLP percentage: "18% of 12500" live
    await unifiedInput.fill('18% of 12500');
    await expect(digits).toHaveText('2,250');

    // 2. Word division: "2.5 lakh divided by 5" live
    await unifiedInput.fill('2.5 lakh divided by 5');
    await expect(digits).toHaveText('50,000');

    // 3. Unit conversion: "convert 10 km to miles" live
    await unifiedInput.fill('convert 10 km to miles');
    await expect(digits).toContainText(/miles/i);

    // 4. GST query: "12500 + 18% GST" live
    await unifiedInput.fill('12500 + 18% GST');
    await expect(digits).toHaveText('14,750');
    await expect(page.locator('#calc-details-line')).toBeVisible();

    await errorGate.assertZeroErrors(page, 'Live NLP & Conversion Keystrokes');
  });

  test('P4-CALC-04: Smart Delete / Backspace Behavior (Normal Mode Char-by-Char vs NLP Mode Full Unit Clear)', async ({ page, app, errorGate }) => {
    await app.gotoApp(true);
    await page.locator('#topbar-calculator-btn').click();

    const unifiedInput = page.locator('#calc-unified-input');
    const digits = page.locator('#calc-lcd-digits');
    const delBtn = page.locator('#calc-btn-del');

    // ── CASE A: Normal Arithmetic Mode (Char by Char) ──
    await unifiedInput.fill('12500');
    await expect(digits).toHaveText('12,500');

    // Click DEL button once -> 1250
    await delBtn.click();
    await expect(unifiedInput).toHaveValue('1250');
    await expect(digits).toHaveText('1,250');

    // Click DEL button again -> 125
    await delBtn.click();
    await expect(unifiedInput).toHaveValue('125');
    await expect(digits).toHaveText('125');

    // ── CASE B: NLP Mode (Clears entire query as one unit) ──
    await unifiedInput.fill('18% of 12500');
    await expect(digits).toHaveText('2,250');

    // Click DEL button -> should clear the entire NLP query!
    await delBtn.click();
    await expect(unifiedInput).toHaveValue('');
    await expect(digits).toHaveText('0');

    // Test with Keyboard Backspace inside NLP query
    await unifiedInput.fill('2.5 lakh divided by 5');
    await expect(digits).toHaveText('50,000');
    await page.keyboard.press('Backspace');
    await expect(unifiedInput).toHaveValue('');
    await expect(digits).toHaveText('0');

    await errorGate.assertZeroErrors(page, 'Smart Delete & Backspace');
  });

  test('P4-CALC-05: Keypad Arithmetic & History Tape Persistence', async ({ page, app, errorGate }) => {
    await app.gotoApp(true);
    await page.locator('#topbar-calculator-btn').click();

    const digits = page.locator('#calc-lcd-digits');

    // Perform calculation via keypad: 8000 + 250 = 8,250
    await page.locator('#calc-btn-8').click();
    await page.locator('#calc-btn-0').click();
    await page.locator('#calc-btn-00').click();
    await page.locator('#calc-btn-add').click();
    await page.locator('#calc-btn-2').click();
    await page.locator('#calc-btn-5').click();
    await page.locator('#calc-btn-0').click();
    await page.locator('#calc-btn-eq').click();
    await expect(digits).toHaveText('8,250');

    // Open History Tape
    const histBtn = page.locator('#calc-btn-hist');
    await histBtn.click();

    const historyDrawer = page.locator('#calc-history-drawer');
    await expect(historyDrawer).toBeVisible();
    await expect(historyDrawer).toContainText('8,250');

    // Clear history tape
    const clearTapeBtn = page.locator('#calc-clear-history-btn');
    await clearTapeBtn.click();
    await expect(historyDrawer).toContainText('No previous calculations');

    await errorGate.assertZeroErrors(page, 'Keypad and History Tape');
  });

  test('P4-CALC-06: Focus Coexistence & Main Application Shortcut Safety', async ({ page, app, errorGate }) => {
    await app.gotoApp(true);

    // 1. Omnisearch shortcut (Ctrl+K) works
    await page.keyboard.press('Control+k');
    const omniModal = page.locator('.omni-modal-overlay');
    await expect(omniModal).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(omniModal).toBeHidden();

    // 2. Calculator shortcut (Alt+C) opens calculator
    await page.keyboard.press('Alt+c');
    const calcWindow = page.locator('#rupeecrm-calculator-window');
    await expect(calcWindow).toBeVisible();

    // 3. Close calculator (Escape)
    await page.keyboard.press('Escape');
    await expect(calcWindow).toBeHidden();

    await errorGate.assertZeroErrors(page, 'Focus and Shortcut Safety');
  });

  test('P4-CALC-07: Immediate Auto-Focus on Open & Strict Focus Isolation with Underlying App', async ({ page, app, errorGate }) => {
    await app.gotoApp(true);

    // 1. Open calculator via header button
    await page.locator('#topbar-calculator-btn').click();
    const calcWindow = page.locator('#rupeecrm-calculator-window');
    await expect(calcWindow).toBeVisible();

    const unifiedInput = page.locator('#calc-unified-input');
    const digits = page.locator('#calc-lcd-digits');

    // Verify input is automatically focused
    await expect(unifiedInput).toBeFocused();

    // Immediately type WITHOUT clicking display or input
    await page.keyboard.type('500 + 200');
    await expect(unifiedInput).toHaveValue('500 + 200');
    await expect(digits).toHaveText('700');

    // 2. Click in the underlying app (e.g. Omnisearch bar)
    const omniPill = page.locator('.topbar-search-pill');
    await omniPill.click();

    // Omnisearch modal opens
    const omniModal = page.locator('.omni-modal-overlay');
    await expect(omniModal).toBeVisible();

    // Type query into Omnisearch
    const omniInput = page.locator('.omni-search-input');
    await omniInput.click();
    await page.keyboard.type('ABC');
    await expect(omniInput).toHaveValue('ABC');

    // Calculator remains intact and did NOT receive 'ABC'
    await expect(unifiedInput).toHaveValue('500 + 200');
    await expect(digits).toHaveText('700');

    // Close Omnisearch modal
    await page.keyboard.press('Escape');
    await expect(omniModal).toBeHidden();

    // 3. Re-focus Calculator by clicking inside its display/window
    await digits.click();
    await expect(unifiedInput).toBeFocused();

    // Typing now routes back to Calculator
    await page.keyboard.type(' + 300');
    await expect(unifiedInput).toHaveValue('500 + 200 + 300');
    await expect(digits).toHaveText('1,000');

    await errorGate.assertZeroErrors(page, 'Immediate Auto-Focus & Focus Isolation');
  });

});
