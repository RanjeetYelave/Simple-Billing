/**
 * testLocationTransitionRuntime.js
 * End-to-end runtime verification of Location Travel Transition trigger conditions,
 * event dispatch payload, stage pipeline, and progression consistency.
 */

const assert = require('assert');

// Mock browser window and CustomEvent
global.window = {
  events: {},
  addEventListener(evt, fn) {
    this.events[evt] = this.events[evt] || [];
    this.events[evt].push(fn);
  },
  removeEventListener(evt, fn) {
    if (this.events[evt]) {
      this.events[evt] = this.events[evt].filter(f => f !== fn);
    }
  },
  dispatchEvent(customEvt) {
    const list = this.events[customEvt.type] || [];
    list.forEach(fn => fn(customEvt));
    return true;
  }
};

global.CustomEvent = class CustomEvent {
  constructor(type, initDict) {
    this.type = type;
    this.detail = initDict ? initDict.detail : {};
  }
};

global.localStorage = {
  store: {},
  getItem(k) { return this.store[k] || null; },
  setItem(k, v) { this.store[k] = String(v); },
  removeItem(k) { delete this.store[k]; }
};

global.fetch = async () => ({ ok: true, json: async () => ({ value: null }) });

const MaharashtraWorld = require('./src/main/webapp/js/maharashtraWorld.js');
const SnakePersistence = require('./src/main/webapp/js/snakePersistence.js');

console.log('======================================================================');
console.log('🗺️ STARTING LOCATION TRANSITION RUNTIME TRIGGER AUDIT');
console.log('======================================================================\n');

let passCount = 0;
async function test(desc, fn) {
  try {
    await fn();
    passCount++;
    console.log(`  ✓ ${desc}`);
  } catch (err) {
    console.error(`  ✗ FAIL: ${desc}`);
    console.error(err);
    process.exit(1);
  }
}

async function run() {
  // 1. Non-boundary levels must NEVER trigger transition
  await test('Levels 1 to 14 do not trigger location unlock transition', () => {
    for (let lvl = 1; lvl <= 14; lvl++) {
      const res = MaharashtraWorld.checkAndTriggerLocationUnlock(lvl, 'classic');
      assert.strictEqual(res, false, `Level ${lvl} must return false`);
    }
  });

  // 2. Level 15 (Karad boundary) triggers transition with valid metadata
  await test('Level 15 completion triggers Karad -> Satara transition event', () => {
    let capturedEvent = null;
    const listener = (e) => { capturedEvent = e.detail; };
    window.addEventListener('billsoft:location-unlocked', listener);

    const res = MaharashtraWorld.checkAndTriggerLocationUnlock(15, 'classic');
    assert.strictEqual(res, true, 'Level 15 completion must return true');
    assert(capturedEvent !== null, 'billsoft:location-unlocked event must be dispatched');
    assert.strictEqual(capturedEvent.completedLevel, 15);
    assert.strictEqual(capturedEvent.fromLoc.name, 'Karad');
    assert.strictEqual(capturedEvent.toLoc.name, 'Satara');
    assert.strictEqual(capturedEvent.unlockKey, 'LOC_UNLOCK_LVL_15');

    window.removeEventListener('billsoft:location-unlocked', listener);
  });

  // 3. Duplicate trigger protection
  await test('Duplicate trigger for Level 15 is prevented by single-fire lock', () => {
    let capturedEvent = null;
    const listener = (e) => { capturedEvent = e.detail; };
    window.addEventListener('billsoft:location-unlocked', listener);

    const res = MaharashtraWorld.checkAndTriggerLocationUnlock(15, 'classic');
    assert.strictEqual(res, false, 'Duplicate level 15 trigger must return false');
    assert.strictEqual(capturedEvent, null, 'Duplicate event must NOT be dispatched');

    window.removeEventListener('billsoft:location-unlocked', listener);
  });

  // 4. Reset clears acknowledged unlocks
  await test('Reset game progress clears acknowledged unlocks so transitions can replay', async () => {
    assert.strictEqual(localStorage.getItem('TAKE_A_BREAK_SHOWN_LOCATION_UNLOCKS') !== null, true);
    await SnakePersistence.resetGameProgress();
    assert.strictEqual(localStorage.getItem('TAKE_A_BREAK_SHOWN_LOCATION_UNLOCKS'), null);

    // Now Level 15 can trigger again
    const res = MaharashtraWorld.checkAndTriggerLocationUnlock(15, 'classic');
    assert.strictEqual(res, true, 'After reset, Level 15 trigger must succeed again');
  });

  // 5. Level 30 (Satara -> Ajinkyatara) triggers transition
  await test('Level 30 completion triggers Satara -> Ajinkyatara Fort transition', () => {
    let capturedEvent = null;
    const listener = (e) => { capturedEvent = e.detail; };
    window.addEventListener('billsoft:location-unlocked', listener);

    const res = MaharashtraWorld.checkAndTriggerLocationUnlock(30, 'classic');
    assert.strictEqual(res, true, 'Level 30 completion must return true');
    assert(capturedEvent !== null, 'Event must be dispatched');
    assert.strictEqual(capturedEvent.completedLevel, 30);
    assert.strictEqual(capturedEvent.fromLoc.name, 'Satara');
    assert.strictEqual(capturedEvent.toLoc.name, 'Ajinkyatara Fort');

    window.removeEventListener('billsoft:location-unlocked', listener);
  });

  console.log(`\n======================================================================`);
  console.log(`🎉 ALL ${passCount} LOCATION TRANSITION RUNTIME TESTS PASSED!`);
  console.log(`======================================================================\n`);
}

run();
