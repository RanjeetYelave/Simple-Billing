/**
 * testSnakeSettingsAndReset.js
 * Automated Verification Suite for Snake Classic Enable/Disable & Reset Game Progress Feature.
 */

const assert = require('assert');
const SnakePersistence = require('./src/main/webapp/js/snakePersistence');
const MaharashtraWorld = require('./src/main/webapp/js/maharashtraWorld');

console.log('=== STARTING SNAKE SETTINGS & RESET PROGRESS TEST SUITE ===\n');

// Mock localStorage and fetch for isolated testing
const mockLocalStorage = {};
global.localStorage = {
  getItem: (key) => mockLocalStorage[key] || null,
  setItem: (key, val) => { mockLocalStorage[key] = String(val); },
  removeItem: (key) => { delete mockLocalStorage[key]; },
  clear: () => { throw new Error('localStorage.clear() MUST NOT be called!'); }
};

const mockBackendAppConfig = {};
global.fetch = async (url, options) => {
  const match = url.match(/\/api\/app-config\/(.+)/);
  if (!match) return { ok: false, status: 404 };
  const key = match[1];

  if (!options || options.method === 'GET' || !options.method) {
    if (mockBackendAppConfig[key] !== undefined) {
      return {
        ok: true,
        json: async () => ({ key, value: mockBackendAppConfig[key], exists: true })
      };
    }
    return {
      ok: true,
      json: async () => ({ key, value: null, exists: false })
    };
  } else if (options.method === 'PUT') {
    const body = JSON.parse(options.body);
    mockBackendAppConfig[key] = body.value;
    return {
      ok: true,
      json: async () => ({ key, value: body.value, saved: true })
    };
  }
  return { ok: false, status: 400 };
};

let dispatchedEvents = [];
global.window = {
  dispatchEvent: (evt) => { dispatchedEvents.push(evt); }
};
global.CustomEvent = class {
  constructor(name, opts) {
    this.type = name;
    this.detail = opts && opts.detail;
  }
};

let passed = 0;
function it(name, fn) {
  try {
    fn();
    console.log(`  ✓ ${name}`);
    passed++;
  } catch (err) {
    console.error(`  ✗ FAIL: ${name}`);
    console.error(err);
    process.exit(1);
  }
}

async function itAsync(name, fn) {
  try {
    await fn();
    console.log(`  ✓ ${name}`);
    passed++;
  } catch (err) {
    console.error(`  ✗ FAIL: ${name}`);
    console.error(err);
    process.exit(1);
  }
}

async function run() {
  console.log('--- Test Suite 1: Enable / Disable Setting & Non-Destructive Toggle ---');

  it('Default state enables Snake Classic in Navigation', () => {
    delete mockLocalStorage['CONFIG_SNAKE_CLASSIC_ENABLED'];
    delete mockLocalStorage['CONFIG_CIRCUIT_CONNECT_ENABLED'];
    const isEnabled = localStorage.getItem('CONFIG_SNAKE_CLASSIC_ENABLED') !== 'false' && localStorage.getItem('CONFIG_CIRCUIT_CONNECT_ENABLED') !== 'false';
    assert.strictEqual(isEnabled, true);
  });

  it('Turning game OFF saves setting without altering game progress', () => {
    // Set mid-game progress
    const midGameState = {
      schemaVersion: 1,
      stateRevision: 42,
      currentLevel: 97,
      completedCount: 6,
      totalStars: 480,
      totalFruitsEaten: 920
    };
    SnakePersistence.saveToLocalStorage(midGameState);

    // Toggle game OFF
    localStorage.setItem('CONFIG_SNAKE_CLASSIC_ENABLED', 'false');
    localStorage.setItem('CONFIG_CIRCUIT_CONNECT_ENABLED', 'false');
    assert.strictEqual(localStorage.getItem('CONFIG_SNAKE_CLASSIC_ENABLED'), 'false');
    assert.strictEqual(localStorage.getItem('CONFIG_CIRCUIT_CONNECT_ENABLED'), 'false');

    // Verify progress remains completely intact
    const loaded = SnakePersistence.loadFromLocalStorage();
    assert.strictEqual(loaded.currentLevel, 97);
    assert.strictEqual(loaded.totalStars, 480);
  });

  it('Turning game ON restores exact previous game progress', () => {
    localStorage.setItem('CONFIG_SNAKE_CLASSIC_ENABLED', 'true');
    localStorage.setItem('CONFIG_CIRCUIT_CONNECT_ENABLED', 'true');
    const isEnabled = localStorage.getItem('CONFIG_SNAKE_CLASSIC_ENABLED') !== 'false' && localStorage.getItem('CONFIG_CIRCUIT_CONNECT_ENABLED') !== 'false';
    assert.strictEqual(isEnabled, true);

    const loaded = SnakePersistence.loadFromLocalStorage();
    assert.strictEqual(loaded.currentLevel, 97);
    assert.strictEqual(loaded.totalStars, 480);
    assert.strictEqual(loaded.completedCount, 6);
  });

  console.log('\n--- Test Suite 2: Reset Game Progress Lifecycle ---');

  await itAsync('Resets mid-game progress (Level 97 -> Level 1) across Tier 1 & Tier 2', async () => {
    dispatchedEvents = [];
    // Set mock state in backend and local
    mockLocalStorage['SNAKE_GAME_STATE'] = JSON.stringify({ currentLevel: 97, totalStars: 480, completedCount: 6 });
    mockLocalStorage['TAKE_A_BREAK_SHOWN_LOCATION_UNLOCKS'] = JSON.stringify(['LOC_UNLOCK_LVL_15', 'LOC_UNLOCK_LVL_90']);
    mockBackendAppConfig['SNAKE_GAME_STATE'] = JSON.stringify({ currentLevel: 97, totalStars: 480, completedCount: 6 });

    // Execute authoritative reset
    const res = await SnakePersistence.resetGameProgress();
    assert.strictEqual(res.success, true);
    assert.strictEqual(res.state.currentLevel, 1);
    assert.strictEqual(res.state.totalStars, 0);
    assert.strictEqual(res.state.completedCount, 0);

    // Verify Tier 1 (localStorage) reset
    const local = SnakePersistence.loadFromLocalStorage();
    assert.strictEqual(local.currentLevel, 1);
    assert.strictEqual(local.totalStars, 0);
    assert.strictEqual(local.completedCount, 0);
    assert.strictEqual(mockLocalStorage['TAKE_A_BREAK_SHOWN_LOCATION_UNLOCKS'], undefined);

    // Verify Tier 2 (AppConfig backend) reset
    const remote = await SnakePersistence.loadFromBackend();
    assert.strictEqual(remote.currentLevel, 1);
    assert.strictEqual(remote.totalStars, 0);
    assert.strictEqual(remote.completedCount, 0);

    // Verify events dispatched
    const resetEvt = dispatchedEvents.find(e => e.type === 'billsoft:snake-progress-reset');
    assert.ok(resetEvt, 'Must dispatch billsoft:snake-progress-reset');
    assert.strictEqual(resetEvt.detail.state.currentLevel, 1);

    const progEvt = dispatchedEvents.find(e => e.type === 'billsoft:game-progression');
    assert.ok(progEvt, 'Must dispatch billsoft:game-progression');
    assert.strictEqual(progEvt.detail.level, 1);
  });

  await itAsync('Journey Map and World Engine resolve to initial Karad destination after reset', async () => {
    const localState = SnakePersistence.loadFromLocalStorage();
    const world = MaharashtraWorld.getWorldForLevel('classic', localState.currentLevel);
    assert.strictEqual(world.location.id, 'satara_karad');
    assert.strictEqual(world.location.name, 'Karad');
    assert.strictEqual(world.progressInLocation, 1);
    assert.strictEqual(world.progressPercent, 7);

    const timeline = MaharashtraWorld.getJourneyTimeline('classic', localState.currentLevel, 15, 'all');
    assert.strictEqual(timeline.completedLocationsCount, 0);
    assert.strictEqual(timeline.currentWorld.location.name, 'Karad');
  });

  await itAsync('Reset is idempotent: resetting at Level 1 remains safe Level 1', async () => {
    const res1 = await SnakePersistence.resetGameProgress();
    assert.strictEqual(res1.success, true);
    assert.strictEqual(res1.state.currentLevel, 1);

    const res2 = await SnakePersistence.resetGameProgress();
    assert.strictEqual(res2.success, true);
    assert.strictEqual(res2.state.currentLevel, 1);

    const loaded = SnakePersistence.loadFromLocalStorage();
    assert.strictEqual(loaded.currentLevel, 1);
  });

  console.log('\n--- Test Suite 3: Isolation & Strict Non-Interference ---');

  await itAsync('Reset operation does NOT touch unrelated localStorage or backend data', async () => {
    // Populate unrelated data
    mockLocalStorage['THEME_SETTING'] = 'dark';
    mockLocalStorage['AUTH_TOKEN'] = 'secret-token-123';
    mockLocalStorage['FIRM_CONFIG'] = JSON.stringify({ name: 'Acme Corp', gst: '27AAAAA0000A1Z5' });
    mockBackendAppConfig['INVOICE_NUMBERING_CONFIG'] = JSON.stringify({ prefix: 'INV-2026', nextVal: 1042 });
    mockBackendAppConfig['COMPANY_DETAILS'] = JSON.stringify({ company: 'Acme Ltd' });

    // Execute reset on high level (Level 1000)
    mockLocalStorage['SNAKE_GAME_STATE'] = JSON.stringify({ currentLevel: 1000, totalStars: 5000 });
    mockBackendAppConfig['SNAKE_GAME_STATE'] = JSON.stringify({ currentLevel: 1000, totalStars: 5000 });

    const res = await SnakePersistence.resetGameProgress();
    assert.strictEqual(res.success, true);

    // Verify Snake progress is reset
    assert.strictEqual(SnakePersistence.loadFromLocalStorage().currentLevel, 1);

    // Verify unrelated localStorage keys are 100% untouched
    assert.strictEqual(mockLocalStorage['THEME_SETTING'], 'dark');
    assert.strictEqual(mockLocalStorage['AUTH_TOKEN'], 'secret-token-123');
    assert.strictEqual(mockLocalStorage['FIRM_CONFIG'], JSON.stringify({ name: 'Acme Corp', gst: '27AAAAA0000A1Z5' }));

    // Verify unrelated AppConfig keys are 100% untouched
    assert.strictEqual(mockBackendAppConfig['INVOICE_NUMBERING_CONFIG'], JSON.stringify({ prefix: 'INV-2026', nextVal: 1042 }));
    assert.strictEqual(mockBackendAppConfig['COMPANY_DETAILS'], JSON.stringify({ company: 'Acme Ltd' }));
  });

  console.log('\n======================================================');
  console.log(`ALL ${passed} SNAKE SETTINGS & RESET TESTS PASSED!`);
  console.log('======================================================\n');
}

run();
