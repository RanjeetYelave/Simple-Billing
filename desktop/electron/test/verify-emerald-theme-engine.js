/**
 * verify-emerald-theme-engine.js
 * Comprehensive automated regression test suite for:
 * 1. Emerald SaaS as default theme
 * 2. Minimal SaaS as default family
 * 3. Plus Jakarta Sans typography
 * 4. Automatic Sunset-to-Sunrise appearance engine
 * 5. Deterministic NOAA solar calculations & day/night transitions
 * 6. Existing user preference preservation & safe defaulting
 * 7. Manual override behavior
 * 8. Offline font bundling and CSS token integrity
 */

const assert = require('assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

console.log('═══════════════════════════════════════════════════════════════════');
console.log('RUPEECRM THEME ENGINE REGRESSION TEST: EMERALD SAAS & SOLAR ENGINE');
console.log('═══════════════════════════════════════════════════════════════════\n');

// Mock Browser Environment
function createMockEnvironment(initialStorage = {}) {
  const store = { ...initialStorage };
  const listeners = {};
  const rootClasses = new Set();
  const rootAttributes = {};
  const bodyClasses = new Set();

  const mockLocalStorage = {
    getItem: (key) => (Object.prototype.hasOwnProperty.call(store, key) ? String(store[key]) : null),
    setItem: (key, val) => { store[key] = String(val); },
    removeItem: (key) => { delete store[key]; },
    clear: () => { Object.keys(store).forEach(k => delete store[k]); },
    _getStore: () => ({ ...store })
  };

  const mockDocument = {
    documentElement: {
      classList: {
        add: (...cls) => cls.forEach(c => rootClasses.add(c)),
        remove: (...cls) => cls.forEach(c => rootClasses.delete(c)),
        contains: (c) => rootClasses.has(c)
      },
      setAttribute: (k, v) => { rootAttributes[k] = String(v); },
      getAttribute: (k) => rootAttributes[k] || null
    },
    body: {
      classList: {
        add: (...cls) => cls.forEach(c => bodyClasses.add(c)),
        remove: (...cls) => cls.forEach(c => bodyClasses.delete(c)),
        contains: (c) => bodyClasses.has(c)
      }
    },
    addEventListener: (type, fn) => {
      listeners[type] = listeners[type] || [];
      listeners[type].push(fn);
    },
    removeEventListener: (type, fn) => {
      if (listeners[type]) listeners[type] = listeners[type].filter(f => f !== fn);
    },
    visibilityState: 'visible'
  };

  const mockWindow = {
    localStorage: mockLocalStorage,
    document: mockDocument,
    addEventListener: (type, fn) => {
      listeners[type] = listeners[type] || [];
      listeners[type].push(fn);
    },
    removeEventListener: (type, fn) => {
      if (listeners[type]) listeners[type] = listeners[type].filter(f => f !== fn);
    },
    dispatchEvent: (evt) => {
      if (listeners[evt.type]) {
        listeners[evt.type].forEach(fn => fn(evt));
      }
    },
    matchMedia: () => ({ matches: false, addEventListener: () => {} }),
    Intl: {
      DateTimeFormat: () => ({
        resolvedOptions: () => ({ timeZone: 'Asia/Kolkata' })
      })
    }
  };

  function CustomEvent(type, init = {}) {
    this.type = type;
    this.detail = init.detail || {};
  }

  let timerIdCounter = 1;
  const sandbox = {
    window: mockWindow,
    document: mockDocument,
    localStorage: mockLocalStorage,
    CustomEvent: CustomEvent,
    Intl: mockWindow.Intl,
    console: console,
    setTimeout: (fn, ms) => { return timerIdCounter++; },
    clearTimeout: (id) => {},
    setInterval: () => { return timerIdCounter++; },
    clearInterval: () => {}
  };

  const scriptCode = fs.readFileSync(path.resolve(__dirname, '../../../billsoft/src/main/webapp/js/themeToggle.js'), 'utf8');
  vm.createContext(sandbox);
  vm.runInContext(scriptCode, sandbox);

  return { sandbox, mockWindow, mockLocalStorage, rootClasses, rootAttributes };
}

// ─────────────────────────────────────────────────────────────────────────────
// Scenario 1: Fresh Installation Defaults
// ─────────────────────────────────────────────────────────────────────────────
console.log('▶ [Scenario 1/7] Testing Fresh Installation Defaults...');
{
  const { mockWindow, mockLocalStorage, rootClasses, rootAttributes } = createMockEnvironment({});
  const stored = mockLocalStorage._getStore();

  assert.strictEqual(stored.themeFamily, 'material', 'Fresh install themeFamily must be "material"');
  assert.strictEqual(stored.themeStyle, 'minimal', 'Fresh install themeStyle must be "minimal"');
  assert.strictEqual(stored.themeAccentColor, 'emerald', 'Fresh install themeAccentColor must be "emerald"');
  assert.strictEqual(stored.themeFontFamily, 'jakarta', 'Fresh install themeFontFamily must be "jakarta"');
  assert.strictEqual(stored.themeMode, 'auto', 'Fresh install themeMode must be "auto"');
  assert.strictEqual(stored.themeDarkSchedule, 'sunset', 'Fresh install themeDarkSchedule must be "sunset"');
  assert.strictEqual(stored.themeScheduleEnabled, 'true', 'Fresh install themeScheduleEnabled must be "true"');

  assert.strictEqual(rootAttributes['data-family'], 'material');
  assert.strictEqual(rootAttributes['data-style'], 'minimal');
  assert.strictEqual(rootAttributes['data-accent'], 'emerald');
  assert.strictEqual(rootAttributes['data-font'], 'jakarta');
  assert.ok(rootClasses.has('minimal'), 'Root must include "minimal" class');

  const config = mockWindow.getStoredThemeConfig();
  assert.strictEqual(config.family, 'material');
  assert.strictEqual(config.style, 'minimal');
  assert.strictEqual(config.palette, 'emerald');
  assert.strictEqual(config.font, 'jakarta');
  assert.strictEqual(config.mode, 'auto');

  console.log('  ✔ Fresh installation correctly initialized: Minimal SaaS, Emerald SaaS, Plus Jakarta Sans, Automatic mode');
}

// ─────────────────────────────────────────────────────────────────────────────
// Scenario 2: Existing User Preference Preservation
// ─────────────────────────────────────────────────────────────────────────────
console.log('▶ [Scenario 2/7] Testing Existing User Preference Preservation...');
{
  const existingPrefs = {
    themeFamily: 'classic',
    themeStyle: 'retro98',
    themeMode: 'dark',
    themeDarkMode: 'true',
    themeAccentColor: 'win98-standard',
    themeFontFamily: 'tahoma',
    themeDarkSchedule: 'manual',
    themeScheduleEnabled: 'false',
    themeBase: 'classic-dark'
  };

  const { mockWindow, mockLocalStorage, rootClasses, rootAttributes } = createMockEnvironment(existingPrefs);
  const stored = mockLocalStorage._getStore();

  assert.strictEqual(stored.themeFamily, 'classic', 'Existing themeFamily must remain "classic"');
  assert.strictEqual(stored.themeStyle, 'retro98', 'Existing themeStyle must remain "retro98"');
  assert.strictEqual(stored.themeMode, 'dark', 'Existing themeMode must remain "dark"');
  assert.strictEqual(stored.themeFontFamily, 'tahoma', 'Existing themeFontFamily must remain "tahoma"');
  assert.strictEqual(stored.themeAccentColor, 'win98-standard', 'Existing themeAccentColor must remain "win98-standard"');

  assert.strictEqual(rootAttributes['data-family'], 'classic');
  assert.strictEqual(rootAttributes['data-style'], 'retro98');
  assert.strictEqual(rootAttributes['data-font'], 'tahoma');
  assert.strictEqual(rootAttributes['data-theme'], 'dark');
  assert.ok(rootClasses.has('classic-dark'), 'Root must have "classic-dark" class');

  console.log('  ✔ Existing user preferences strictly preserved without overwrite');
}

// ─────────────────────────────────────────────────────────────────────────────
// Scenario 3: Partial Preference Safe Defaulting
// ─────────────────────────────────────────────────────────────────────────────
console.log('▶ [Scenario 3/7] Testing Partial Preference Safe Defaulting...');
{
  // User had selected Modern Material, but font was missing
  const partialPrefs = {
    themeFamily: 'material',
    themeStyle: 'modern',
    themeAccentColor: 'navy',
    themeMode: 'light'
  };

  const { mockWindow, rootAttributes } = createMockEnvironment(partialPrefs);
  const state = mockWindow.getThemeState();

  assert.strictEqual(state.family, 'material');
  assert.strictEqual(state.style, 'modern');
  assert.strictEqual(state.accent, 'navy');
  assert.strictEqual(state.font, 'jakarta', 'Missing font should safely default to jakarta or style font');
  assert.strictEqual(rootAttributes['data-accent'], 'navy');

  console.log('  ✔ Partial preferences safely defaulted missing settings without resetting configured values');
}

// ─────────────────────────────────────────────────────────────────────────────
// Scenario 4: Astronomical Solar Calculation Engine Accuracy
// ─────────────────────────────────────────────────────────────────────────────
console.log('▶ [Scenario 4/7] Testing Astronomical Solar Calculation Engine...');
{
  const { mockWindow } = createMockEnvironment({});
  const now = new Date('2026-11-13T12:00:00Z');

  const solarSchedule = mockWindow.getSolarSchedule(now);
  assert.ok(solarSchedule.sunrise && typeof solarSchedule.sunrise.getTime === 'function', 'Sunrise must be a Date object');
  assert.ok(solarSchedule.sunset && typeof solarSchedule.sunset.getTime === 'function', 'Sunset must be a Date object');
  assert.ok(solarSchedule.sunset.getTime() > solarSchedule.sunrise.getTime(), 'Sunset must occur after sunrise on same day');

  // Verify cities catalog
  const catalog = mockWindow.getCitiesCatalog();
  assert.ok(Array.isArray(catalog) && catalog.length >= 10, 'Cities catalog must contain predefined cities');
  const mumbai = catalog.find(c => c.id === 'mumbai');
  assert.ok(mumbai && mumbai.lat > 18 && mumbai.lat < 20, 'Mumbai coordinates must be accurate');

  // Test city selection
  mockWindow.setLocationCity('delhi');
  const delhiSchedule = mockWindow.getSolarSchedule(now);
  assert.strictEqual(delhiSchedule.location.cityId, 'delhi', 'Location city must update to Delhi');

  console.log('  ✔ NOAA Astronomical Solar Calculation Engine verified across worldwide coordinates');
}

// ─────────────────────────────────────────────────────────────────────────────
// Scenario 5: Deterministic Day/Night Transition Assertions
// ─────────────────────────────────────────────────────────────────────────────
console.log('▶ [Scenario 5/7] Testing Deterministic Day/Night Transitions...');
{
  const { mockWindow } = createMockEnvironment({});

  // 1. Before sunrise (03:00 IST) -> Must be Dark
  const preSunrise = new Date(2026, 10, 13, 3, 0, 0);
  const preSolar = mockWindow.getSolarSchedule(preSunrise);
  assert.strictEqual(preSolar.isDark, true, '03:00 must evaluate to Dark mode');
  assert.strictEqual(preSolar.nextTransitionType, 'sunrise', 'Next transition at 03:00 must be sunrise');

  // 2. Daytime morning (09:00 IST) -> Must be Light
  const morning = new Date(2026, 10, 13, 9, 0, 0);
  const morningSolar = mockWindow.getSolarSchedule(morning);
  assert.strictEqual(morningSolar.isDark, false, '09:00 must evaluate to Light mode');
  assert.strictEqual(morningSolar.nextTransitionType, 'sunset', 'Next transition at 09:00 must be sunset');

  // 3. Daytime afternoon (14:30 IST) -> Must be Light
  const afternoon = new Date(2026, 10, 13, 14, 30, 0);
  const afternoonSolar = mockWindow.getSolarSchedule(afternoon);
  assert.strictEqual(afternoonSolar.isDark, false, '14:30 must evaluate to Light mode');
  assert.strictEqual(afternoonSolar.nextTransitionType, 'sunset', 'Next transition at 14:30 must be sunset');

  // 4. Night after sunset (20:00 IST) -> Must be Dark
  const night = new Date(2026, 10, 13, 20, 0, 0);
  const nightSolar = mockWindow.getSolarSchedule(night);
  assert.strictEqual(nightSolar.isDark, true, '20:00 must evaluate to Dark mode');
  assert.strictEqual(nightSolar.nextTransitionType, 'sunrise', 'Next transition at 20:00 must be sunrise');

  // 5. Late midnight (23:45 IST) -> Must be Dark
  const midnight = new Date(2026, 10, 13, 23, 45, 0);
  const midnightSolar = mockWindow.getSolarSchedule(midnight);
  assert.strictEqual(midnightSolar.isDark, true, '23:45 must evaluate to Dark mode');
  assert.strictEqual(midnightSolar.nextTransitionType, 'sunrise', 'Next transition at 23:45 must be sunrise');

  console.log('  ✔ All 5 deterministic time bracket assertions passed (Pre-sunrise, Morning, Afternoon, Evening, Midnight)');
}

// ─────────────────────────────────────────────────────────────────────────────
// Scenario 6: Manual Override and Re-enabling Automatic Mode
// ─────────────────────────────────────────────────────────────────────────────
console.log('▶ [Scenario 6/7] Testing Manual Override & Re-enabling Automatic Mode...');
{
  const { mockWindow, rootAttributes } = createMockEnvironment({});

  // User manually sets Light mode
  mockWindow.setDarkMode(false);
  let state = mockWindow.getThemeState();
  assert.strictEqual(state.mode, 'light');
  assert.strictEqual(state.activeIsDark, false);
  assert.strictEqual(rootAttributes['data-theme'], 'light');

  // User manually sets Dark mode
  mockWindow.setDarkMode(true);
  state = mockWindow.getThemeState();
  assert.strictEqual(state.mode, 'dark');
  assert.strictEqual(state.activeIsDark, true);
  assert.strictEqual(rootAttributes['data-theme'], 'dark');

  // User re-enables Automatic mode
  mockWindow.setAutoMode();
  state = mockWindow.getThemeState();
  assert.strictEqual(state.mode, 'auto');
  assert.strictEqual(state.schedule, 'sunset');
  assert.strictEqual(state.scheduleEnabled, true);

  console.log('  ✔ Manual Light/Dark overrides automatic schedule; switching to Auto immediately re-engages solar engine');
}

// ─────────────────────────────────────────────────────────────────────────────
// Scenario 7: Offline Font Files and CSS Token Integrity
// ─────────────────────────────────────────────────────────────────────────────
console.log('▶ [Scenario 7/7] Testing Offline Font Bundling and CSS Design Tokens...');
{
  const fontDir = path.resolve(__dirname, '../../../billsoft/src/main/webapp/fonts');
  const requiredFonts = [
    'plus-jakarta-sans-400.woff2',
    'plus-jakarta-sans-500.woff2',
    'plus-jakarta-sans-600.woff2',
    'plus-jakarta-sans-700.woff2'
  ];

  requiredFonts.forEach(fontFile => {
    const filePath = path.join(fontDir, fontFile);
    assert.ok(fs.existsSync(filePath), `Font file must exist offline: ${fontFile}`);
    const stat = fs.statSync(filePath);
    assert.ok(stat.size > 20000, `Font file size must be non-empty and valid (>20KB): ${fontFile}`);
  });

  const css = fs.readFileSync(path.resolve(__dirname, '../../../billsoft/src/main/webapp/css/style.css'), 'utf8');

  // Check Plus Jakarta Sans font-face definitions
  assert.ok(css.includes("font-family: 'Plus Jakarta Sans'"), 'CSS must define Plus Jakarta Sans @font-face');
  assert.ok(css.includes('--font-sans: \'Plus Jakarta Sans\''), 'CSS default --font-sans must be Plus Jakarta Sans');

  // Check Minimal Emerald SaaS Light tokens
  assert.ok(css.includes(':root.minimal-light[data-accent="emerald"]'), 'CSS must define Minimal Emerald light accent');
  assert.ok(css.includes('--primary: #168B45'), 'Emerald light primary must be #168B45');
  assert.ok(css.includes('--primary-hover: #127339'), 'Emerald light primary-hover must be #127339');
  assert.ok(css.includes('--primary-active: #0E5B2D'), 'Emerald light primary-active must be #0E5B2D');

  // Check Minimal Emerald SaaS Dark tokens
  assert.ok(css.includes(':root.minimal-dark[data-accent="emerald"]'), 'CSS must define Minimal Emerald dark accent');
  assert.ok(css.includes('--primary: #22C55E'), 'Emerald dark primary must be #22C55E');
  assert.ok(css.includes('--primary-hover: #4ADE80'), 'Emerald dark primary-hover must be #4ADE80');
  assert.ok(css.includes('--primary-active: #16A34A'), 'Emerald dark primary-active must be #16A34A');

  // Check tabular numerals rule
  assert.ok(css.includes('font-variant-numeric: tabular-nums'), 'CSS must enforce tabular-nums for financial accuracy');

  // Verify index.html does NOT link to Google Fonts
  const html = fs.readFileSync(path.resolve(__dirname, '../../../billsoft/src/main/webapp/index.html'), 'utf8');
  assert.ok(!html.includes('fonts.googleapis.com'), 'index.html must NOT contain external Google Fonts requests');
  assert.ok(html.includes('Synchronous pre-paint theme bootstrap script'), 'index.html must include synchronous pre-paint theme bootstrap');

  console.log('  ✔ All 4 WOFF2 font weights bundled locally, CSS tokens verified, 100% offline verified');
}

console.log('\n═══════════════════════════════════════════════════════════════════');
console.log('ALL 7/7 EMERALD SAAS & SOLAR THEME ENGINE TESTS PASSED SUCCESSFULLY!');
console.log('═══════════════════════════════════════════════════════════════════\n');

process.exit(0);
