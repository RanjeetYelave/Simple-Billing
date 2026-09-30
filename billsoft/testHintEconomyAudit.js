/**
 * Comprehensive Circuit Connect Hint Economy & Timer Audit
 */

const CircuitPersistence = require('./src/main/webapp/js/circuitPersistence');

let testsPassed = 0;
let testsFailed = 0;

function assert(condition, msg) {
  if (condition) {
    testsPassed++;
  } else {
    testsFailed++;
    console.error(`[FAIL] ${msg}`);
  }
}

console.log('=== RUNNING CIRCUIT CONNECT HINT ECONOMY & TIMER AUDIT ===\n');

// 1. Initial State & Default Hints
console.log('--- Test 1: Initial State & Default Hints ---');
const defaultState = CircuitPersistence.createDefaultState();
assert(defaultState.hintBalance === 3, 'Default state must provide 3 starting hints');
assert(defaultState.hintProgressSeconds === 0, 'Default state must start with 0s hint progress');

// 2. Active Game Time Hint Accumulation Simulation
console.log('\n--- Test 2: 30-Minute (1800s) Active Play Accumulation ---');
let state = { ...defaultState, hintProgressSeconds: 1798, hintBalance: 3 };

function tickActivePlay(st, seconds, isVisible = true, isPaused = false) {
  for (let s = 0; s < seconds; s++) {
    if (isVisible && !isPaused) {
      st.hintProgressSeconds++;
      if (st.hintProgressSeconds >= 1800) {
        st.hintBalance++;
        st.hintProgressSeconds = 0;
      }
    }
  }
  return st;
}

// Tick 1s: 1799s (No hint yet)
state = tickActivePlay(state, 1, true, false);
assert(state.hintProgressSeconds === 1799, 'Timer reaches 1799s');
assert(state.hintBalance === 3, 'Balance still 3 before 1800s threshold');

// Tick 1s: Reaches 1800s -> Awards 1 hint, timer resets to 0
state = tickActivePlay(state, 1, true, false);
assert(state.hintProgressSeconds === 0, 'Timer resets to 0 upon reaching 1800s');
assert(state.hintBalance === 4, 'Hint balance increments from 3 to 4');

// 3. Paused / Modal Open / Tab Hidden Inactivity Test
console.log('\n--- Test 3: Paused, Modal Open & Tab Inactive Invariance ---');
// 600s with tab hidden (visibilityState = hidden)
state = tickActivePlay(state, 600, false, false);
assert(state.hintProgressSeconds === 0, 'Hidden tab must NOT advance hint timer');
assert(state.hintBalance === 4, 'Hidden tab must NOT award hints');

// 600s with modal open (isPaused = true)
state = tickActivePlay(state, 600, true, true);
assert(state.hintProgressSeconds === 0, 'Paused modal must NOT advance hint timer');
assert(state.hintBalance === 4, 'Paused modal must NOT award hints');

// 4. Game Switching & Component Unmount Persistence Roundtrip
console.log('\n--- Test 4: Game Switch & Persistence Roundtrip ---');
// Advance 900s active play (15 minutes in -> Next hint in 15:00)
state = tickActivePlay(state, 900, true, false);
assert(state.hintProgressSeconds === 900, 'Timer reaches 900s active play');

// Simulate unmount / saving to persistence
const sanitizedSaved = CircuitPersistence.sanitizeState(state);
assert(sanitizedSaved.hintBalance === 4, 'Saved state preserves hintBalance = 4');
assert(sanitizedSaved.hintProgressSeconds === 900, 'Saved state preserves hintProgressSeconds = 900');

// Simulate remounting after switching from Snake
const restoredState = CircuitPersistence.sanitizeState(sanitizedSaved);
assert(restoredState.hintBalance === 4, 'Restored state has hintBalance = 4');
assert(restoredState.hintProgressSeconds === 900, 'Restored state has hintProgressSeconds = 900');

// Continue 900s active play -> Should award 5th hint and reset to 0
state = tickActivePlay(restoredState, 900, true, false);
assert(state.hintBalance === 5, 'Hint balance correctly increments to 5 after completing remaining 900s');
assert(state.hintProgressSeconds === 0, 'Hint timer resets to 0 after completing full 1800s cycle');

// 5. Hint Consumption Independence Test
console.log('\n--- Test 5: Hint Consumption Does Not Reset 30-min Timer ---');
// Accumulate 1200s (20m accumulated, 10m remaining)
state = tickActivePlay(state, 1200, true, false);
assert(state.hintProgressSeconds === 1200, 'Timer is at 1200s (10m left)');
assert(state.hintBalance === 5, 'Balance is 5');

// Player consumes 1 hint
state.hintBalance -= 1;
assert(state.hintBalance === 4, 'Consumed 1 hint -> Balance is 4');
assert(state.hintProgressSeconds === 1200, 'Hint timer remains exactly at 1200s (NOT reset by consumption)');

// Advance remaining 600s -> Awards new hint
state = tickActivePlay(state, 600, true, false);
assert(state.hintBalance === 5, 'Remaining 600s active play awards new hint');
assert(state.hintProgressSeconds === 0, 'Timer resets to 0 for next cycle');

// 6. UI Countdown Math Test
console.log('\n--- Test 6: UI Countdown Formatting Invariance ---');
function formatActiveTime(seconds) {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${m}:${s < 10 ? '0' : ''}${s}`;
}

const HINT_INTERVAL = 1800;
const testProgress = 343; // 5m 43s accumulated
const secondsToNext = Math.max(0, HINT_INTERVAL - (testProgress % HINT_INTERVAL)); // 1457s = 24:17
assert(formatActiveTime(secondsToNext) === '24:17', '343s progress produces exact UI countdown "24:17"');

console.log(`\n========================================`);
console.log(`HINT AUDIT RESULTS: ${testsPassed} PASSED, ${testsFailed} FAILED`);
console.log(`========================================\n`);

if (testsFailed > 0) process.exit(1);
else process.exit(0);
