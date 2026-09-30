/**
 * Automated Test: Location Unlock Transition Coordination & Idempotency
 */

const MaharashtraWorld = require('./src/main/webapp/js/maharashtraWorld');

let testsPassed = 0;
let testsFailed = 0;

function assert(condition, message) {
  if (condition) {
    testsPassed++;
  } else {
    testsFailed++;
    console.error(`[FAIL] ${message}`);
  }
}

console.log('=== RUNNING LOCATION UNLOCK TRANSITION COORDINATION AUDIT ===\n');

// Mock localStorage for Node environment
const storageMock = {};
global.localStorage = {
  getItem: (k) => storageMock[k] || null,
  setItem: (k, v) => { storageMock[k] = String(v); },
  removeItem: (k) => { delete storageMock[k]; }
};

// Mock window event dispatching
const dispatchedEvents = [];
global.window = {
  dispatchEvent: (e) => {
    dispatchedEvents.push(e);
  }
};
global.CustomEvent = class CustomEvent {
  constructor(name, params) {
    this.type = name;
    this.detail = params ? params.detail : null;
  }
};

// 1. Normal Level Completions (Levels 1 to 14)
console.log('--- Test 1: Normal Level Completions (Levels 1-14) Must NOT Trigger Unlock ---');
for (let lvl = 1; lvl <= 14; lvl++) {
  const triggered = MaharashtraWorld.checkAndTriggerLocationUnlock(lvl, 'modern');
  assert(triggered === false, `Level ${lvl} completion must NOT trigger location unlock`);
}
assert(dispatchedEvents.length === 0, 'No location unlock events must be dispatched for levels 1-14');

// 2. Level 15 Completion (First Location Unlock: Location 1 -> Location 2)
console.log('\n--- Test 2: Level 15 Completion Triggers Exactly Once for Location 2 ---');
const triggered15 = MaharashtraWorld.checkAndTriggerLocationUnlock(15, 'modern');
assert(triggered15 === true, 'Level 15 completion MUST trigger location unlock');
assert(dispatchedEvents.length === 1, 'Exactly 1 location unlock event dispatched');

const event15 = dispatchedEvents[0];
assert(event15.type === 'billsoft:location-unlocked', 'Event type must be billsoft:location-unlocked');
assert(event15.detail.completedLevel === 15, 'Completed level must be 15');
assert(event15.detail.unlockKey === 'LOC_UNLOCK_LVL_15', 'Unlock key must be LOC_UNLOCK_LVL_15');
assert(event15.detail.toLoc !== null, 'Destination location must exist');

// 3. Duplicate / Re-render / Reload Idempotency Guard
console.log('\n--- Test 3: Duplicate Calls & Reloads on Level 15 Must NOT Re-trigger ---');
const duplicate15 = MaharashtraWorld.checkAndTriggerLocationUnlock(15, 'modern');
assert(duplicate15 === false, 'Duplicate call on level 15 MUST return false');

// Switch game to Snake on level 15
const snakeSwitch15 = MaharashtraWorld.checkAndTriggerLocationUnlock(15, 'classic');
assert(snakeSwitch15 === false, 'Switching to Snake MUST NOT re-trigger already acknowledged unlock');

assert(dispatchedEvents.length === 1, 'Total dispatched events must remain exactly 1');

// 4. Levels 16 to 29
console.log('\n--- Test 4: Levels 16 to 29 Must NOT Trigger Unlock ---');
for (let lvl = 16; lvl <= 29; lvl++) {
  const triggered = MaharashtraWorld.checkAndTriggerLocationUnlock(lvl, 'classic');
  assert(triggered === false, `Level ${lvl} completion must NOT trigger location unlock`);
}
assert(dispatchedEvents.length === 1, 'Total dispatched events must remain 1 for levels 16-29');

// 5. Level 30 Completion (Second Location Unlock: Location 2 -> Location 3)
console.log('\n--- Test 5: Level 30 Completion Triggers Exactly Once for Location 3 ---');
const triggered30 = MaharashtraWorld.checkAndTriggerLocationUnlock(30, 'classic');
assert(triggered30 === true, 'Level 30 completion MUST trigger location unlock');
assert(dispatchedEvents.length === 2, 'Total dispatched events must be exactly 2');

const event30 = dispatchedEvents[1];
assert(event30.detail.completedLevel === 30, 'Completed level must be 30');
assert(event30.detail.unlockKey === 'LOC_UNLOCK_LVL_30', 'Unlock key must be LOC_UNLOCK_LVL_30');

// 6. Persistence Verification
console.log('\n--- Test 6: Acknowledged Unlocks Persisted in Shared Storage ---');
const acknowledged = MaharashtraWorld.getAcknowledgedLocationUnlocks();
assert(acknowledged.has('LOC_UNLOCK_LVL_15'), 'Storage contains LOC_UNLOCK_LVL_15');
assert(acknowledged.has('LOC_UNLOCK_LVL_30'), 'Storage contains LOC_UNLOCK_LVL_30');
assert(!acknowledged.has('LOC_UNLOCK_LVL_45'), 'Storage does not contain unearned LOC_UNLOCK_LVL_45');

console.log(`\n========================================`);
console.log(`LOCATION UNLOCK AUDIT: ${testsPassed} PASSED, ${testsFailed} FAILED`);
console.log(`========================================\n`);

if (testsFailed > 0) process.exit(1);
else process.exit(0);
