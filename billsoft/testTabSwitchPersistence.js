/**
 * Automated Test Suite: Snake Tab Switch & Visibility Persistence
 */

const SnakeEngine = require('./src/main/webapp/js/snakeEngine');
const SnakeGenerator = require('./src/main/webapp/js/snakeGenerator');
const SnakePersistence = require('./src/main/webapp/js/snakePersistence');

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

console.log('=== RUNNING SNAKE TAB SWITCH & VISIBILITY PERSISTENCE AUDIT ===\n');

// 1. Initial State Setup
const initialProgression = {
  schemaVersion: 1,
  currentLevel: 8,
  totalStars: 21,
  completedCount: 7,
  totalPlayTimeSeconds: 450,
  currentStreak: 4,
  bestStreak: 6
};

const activeBoard = {
  level: 8,
  seed: 12345,
  width: 32,
  height: 18,
  targetFruits: 10,
  baseTickMs: 150,
  layoutType: 'PILLARS',
  isMilestone: false,
  isOasis: false,
  snake: [[10, 8], [9, 8], [8, 8], [7, 8], [6, 8], [5, 8], [4, 8]], // Length 7
  direction: 'UP',
  obstacles: [3 * 32 + 5, 3 * 32 + 25],
  fruitsEatenInLevel: 4, // 4 apples eaten
  elapsedSeconds: 28,
  restartsCount: 0,
  food: { x: 15, y: 12, type: 'GOLDEN_APPLE', spawnTime: Date.now(), lifetimeMs: 14000 },
  boostTimer: 3500,
  magnetTimer: 4200,
  chillTimer: 0,
  comboCount: 3,
  consecutiveSpecials: 1,
  noveltyCooldown: 1,
  speedModifier: 1.28
};

// 2. Test 1: Single Tab Switch Out and Return
console.log('--- Test 1: Single Tab Switch Out & Return ---');

// Simulate handleFlushAndPause()
let stateBeforeLeave = {
  ...initialProgression,
  currentBoard: { ...activeBoard }
};

let savedJson = JSON.stringify(SnakePersistence.sanitizeState(stateBeforeLeave));
assert(savedJson.length > 0, 'Serialized state is valid JSON');

// Simulate returning to tab and recovering
let loadedState = JSON.parse(savedJson);
let restoredProgression = SnakePersistence.sanitizeState(loadedState);
let restoredBoard = restoredProgression.currentBoard;

assert(restoredBoard !== null, 'Restored board must exist');
assert(restoredBoard.level === 8, 'Level must be 8');
assert(restoredBoard.fruitsEatenInLevel === 4, 'Fruits eaten must remain 4');
assert(restoredBoard.snake.length === 7, 'Snake length must remain 7');
assert(restoredBoard.snake[0][0] === 10 && restoredBoard.snake[0][1] === 8, 'Snake head must be at exact (10, 8)');
assert(restoredBoard.direction === 'UP', 'Direction must be UP');
assert(restoredBoard.food.type === 'GOLDEN_APPLE', 'Food type must remain GOLDEN_APPLE');
assert(restoredBoard.food.x === 15 && restoredBoard.food.y === 12, 'Food position must be (15, 12)');
assert(restoredBoard.magnetTimer === 4200, 'Magnet timer must be 4200ms');
assert(restoredBoard.boostTimer === 3500, 'Boost timer must be 3500ms');
assert(restoredBoard.comboCount === 3, 'Combo count must be 3');
assert(restoredProgression.currentStreak === 4, 'Streak must be 4');

// 3. Test 2: Multiple Consecutive Tab Switches
console.log('\n--- Test 2: Multiple Consecutive Tab Switches (10x) ---');

let currentState = stateBeforeLeave;
for (let i = 0; i < 10; i++) {
  // Save on blur
  const serialized = JSON.stringify(SnakePersistence.sanitizeState(currentState));
  // Restore on focus
  const deserialized = JSON.parse(serialized);
  currentState = SnakePersistence.sanitizeState(deserialized);
}

assert(currentState.currentBoard.fruitsEatenInLevel === 4, 'Multiple tab switches must not corrupt fruits eaten');
assert(currentState.currentBoard.snake.length === 7, 'Multiple tab switches must not reset snake length');
assert(currentState.currentBoard.magnetTimer === 4200, 'Multiple tab switches must not wipe power-up timer');

// 4. Test 3: Leave while paused or at Level Goal Card
console.log('\n--- Test 3: Leave while paused or at Level Goal Card ---');

let pausedState = {
  ...currentState,
  currentBoard: {
    ...currentState.currentBoard,
    fruitsEatenInLevel: 2
  }
};

let savedPaused = SnakePersistence.sanitizeState(pausedState);
assert(savedPaused.currentBoard.fruitsEatenInLevel === 2, 'Paused state preserves exact fruits eaten');

// 5. Test 4: Game Switch to Circuit Connect and Back
console.log('\n--- Test 4: Unmount, Game Switch & Remount Roundtrip ---');

// Player switches to Circuit Connect (unmount SnakeClassic)
const flushedSnakeState = SnakePersistence.sanitizeState(stateBeforeLeave);

// Player plays Circuit Connect (persists Circuit state separately)
const circuitState = {
  schemaVersion: 1,
  currentLevel: 15,
  completedCount: 14,
  totalStars: 42,
  highestStarMilestone: 0,
  totalPlayTimeSeconds: 1200,
  hintBalance: 3,
  hintProgressSeconds: 600
};

// Player switches back to Snake (remount SnakeClassic)
const recoveredSnake = SnakePersistence.sanitizeState(flushedSnakeState);
assert(recoveredSnake.currentLevel === 8, 'Snake level preserved across game switch');
assert(recoveredSnake.currentBoard.fruitsEatenInLevel === 4, 'Snake apples eaten preserved across game switch');
assert(recoveredSnake.currentBoard.food.type === 'GOLDEN_APPLE', 'Snake food preserved across game switch');

console.log(`\n========================================`);
console.log(`PERSISTENCE AUDIT: ${testsPassed} PASSED, ${testsFailed} FAILED`);
console.log(`========================================\n`);

if (testsFailed > 0) process.exit(1);
else process.exit(0);
