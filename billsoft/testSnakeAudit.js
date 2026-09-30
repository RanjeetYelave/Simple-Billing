/**
 * Comprehensive Automated Simulation & Test Harness for Snake Power-Ups & Collectibles.
 * Tests 10,000 spawn events, rate limiting, magnet attraction & consumption,
 * all power-up lifecycles, bounds validation, and persistence roundtrip.
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

console.log('=== STARTING SNAKE POWER-UP & COLLECTIBLE COMPREHENSIVE AUDIT ===\n');

// 1. Simulation of 10,000 Spawns Across Representative Levels (Level 1, 5, 10, 20, 35, 50)
console.log('--- Test Suite 1: Spawn Distribution & Anti-Spam Rate Limiting (10,000 Iterations) ---');

const testLevels = [1, 5, 8, 12, 16, 20, 35, 50];
let totalSpawns = 0;
let totalNormalApples = 0;
let totalSpecials = 0;
let maxConsecutiveSpecialsObserved = 0;
let invalidBoundsCount = 0;
let specialBreakdown = {};

testLevels.forEach(lvl => {
  const config = SnakeGenerator.getLevelConfig(lvl);
  let mockBoard = SnakeGenerator.generateLevel(lvl);
  let consecutiveSpecials = 0;

  for (let i = 0; i < 1250; i++) {
    totalSpawns++;
    const fruit = SnakeGenerator.determineNextFruit(mockBoard, config, Math.random);

    if (fruit.type === 'APPLE') {
      totalNormalApples++;
      consecutiveSpecials = 0;
      mockBoard.noveltyCooldown = Math.max(0, (mockBoard.noveltyCooldown || 0) - 1);
    } else {
      totalSpecials++;
      consecutiveSpecials++;
      mockBoard.noveltyCooldown = 1;
      specialBreakdown[fruit.type] = (specialBreakdown[fruit.type] || 0) + 1;
    }

    mockBoard.consecutiveSpecials = consecutiveSpecials;
    if (consecutiveSpecials > maxConsecutiveSpecialsObserved) {
      maxConsecutiveSpecialsObserved = consecutiveSpecials;
    }

    // Spawn coordinate test
    const spawnedFood = SnakeGenerator.spawnFood(mockBoard.snake, mockBoard.obstacles, mockBoard.width, mockBoard.height, Math.random, config, mockBoard);
    if (spawnedFood) {
      if (spawnedFood.x < 0 || spawnedFood.x >= mockBoard.width || spawnedFood.y < 0 || spawnedFood.y >= mockBoard.height) {
        invalidBoundsCount++;
      }
      const occupiedBySnake = mockBoard.snake.some(s => s[0] === spawnedFood.x && s[1] === spawnedFood.y);
      const occupiedByObstacle = (mockBoard.obstacles || []).includes(spawnedFood.y * mockBoard.width + spawnedFood.x);
      assert(!occupiedBySnake && !occupiedByObstacle, `Spawned food on occupied tile at level ${lvl}`);
    }
  }
});

const normalRatio = (totalNormalApples / totalSpawns) * 100;
const specialRatio = (totalSpecials / totalSpawns) * 100;

console.log(`Total Spawns: ${totalSpawns}`);
console.log(`Normal Apples: ${totalNormalApples} (${normalRatio.toFixed(2)}%)`);
console.log(`Special Items: ${totalSpecials} (${specialRatio.toFixed(2)}%)`);
console.log(`Special Breakdown:`, specialBreakdown);
console.log(`Max Consecutive Specials Observed: ${maxConsecutiveSpecialsObserved}`);

assert(normalRatio >= 70 && normalRatio <= 85, `Normal apple ratio ${normalRatio.toFixed(2)}% must be within 70-85%`);
assert(specialRatio >= 15 && specialRatio <= 30, `Special item ratio ${specialRatio.toFixed(2)}% must be within 15-30%`);
assert(maxConsecutiveSpecialsObserved <= 2, `Max consecutive specials must NEVER exceed 2 (observed: ${maxConsecutiveSpecialsObserved})`);
assert(invalidBoundsCount === 0, `All spawned food must be within grid bounds (errors: ${invalidBoundsCount})`);

// 2. Magnet Bug Fix & Multi-Target Attraction Test
console.log('\n--- Test Suite 2: Magnet Attraction, Instant Consumption & Multi-Target Test ---');

// Setup active board at Level 20 with Magnet activated
let magnetBoard = {
  level: 20,
  width: 20,
  height: 15,
  targetFruits: 10,
  fruitsEatenInLevel: 0,
  baseTickMs: 150,
  direction: 'RIGHT',
  snake: [[5, 5], [4, 5], [3, 5]],
  obstacles: [],
  magnetTimer: 8000,
  chillTimer: 0,
  boostTimer: 0,
  comboCount: 0,
  food: { x: 8, y: 5, type: 'APPLE', spawnTime: Date.now() }
};

// Tick 1: Snake moves right to (6,5), Magnet pulls food left from (8,5) to (7,5).
// Distance is now |6-7| + |5-5| = 1 (within collection radius <= 1), so instant consumption occurs!
let tickResult1 = SnakeEngine.tick(magnetBoard);
assert(tickResult1.event === 'GROW', `Tick 1: Food pulled into collection radius should trigger instant GROW event`);
assert(tickResult1.nextState.fruitsEatenInLevel === 1, `Tick 1: fruitsEatenInLevel should increment to 1`);
assert(tickResult1.nextState.food === null, `Tick 1: Consumed food must be cleared (null)`);
assert(tickResult1.needsFoodSpawn === true, `Tick 1: needsFoodSpawn must be true to spawn replacement`);

// Spawn 2nd target food at distance (12, 5) while magnet is still active
let config20 = SnakeGenerator.getLevelConfig(20);
magnetBoard = {
  ...tickResult1.nextState,
  food: { x: 12, y: 5, type: 'GOLDEN_APPLE', spawnTime: Date.now(), lifetimeMs: 14000 }
};

// Advance ticks until 2nd target reaches head and is consumed
let ticksRun = 0;
let secondConsumed = false;
while (magnetBoard.magnetTimer > 0 && ticksRun < 15) {
  ticksRun++;
  let res = SnakeEngine.tick(magnetBoard);
  magnetBoard = res.nextState;
  if (res.event === 'GROW' && res.fruitEaten === 'GOLDEN_APPLE') {
    secondConsumed = true;
    assert(magnetBoard.fruitsEatenInLevel === 3, `Golden Apple must add +2 to fruits eaten (total: ${magnetBoard.fruitsEatenInLevel})`);
    break;
  }
}
assert(secondConsumed, `Magnet must attract and instantly consume multiple targets during single activation`);

// Test Magnet Expiration behavior: remaining food stays in valid cell
magnetBoard.magnetTimer = 100; // expiring on next tick
magnetBoard.food = { x: 15, y: 8, type: 'APPLE', spawnTime: Date.now() };
let expireResult = SnakeEngine.tick(magnetBoard);
assert(expireResult.nextState.magnetTimer === 0, `Magnet timer must cleanly expire to 0`);
assert(expireResult.nextState.food !== null, `Food must remain on board after magnet expiration`);
assert(expireResult.nextState.food.x >= 0 && expireResult.nextState.food.x < magnetBoard.width, `Food coordinate x must be valid`);
assert(expireResult.nextState.food.y >= 0 && expireResult.nextState.food.y < magnetBoard.height, `Food coordinate y must be valid`);

// 3. Power-Up Behaviour Matrix Verification
console.log('\n--- Test Suite 3: Power-Up Matrix (All 7 Items) Lifecycle & Stacking ---');

const itemsToTest = ['APPLE', 'GOLDEN_APPLE', 'CHILL_BERRY', 'SPEED_BOOST', 'MYSTERY', 'MAGNET', 'HOPPING_FRUIT'];

itemsToTest.forEach(itemType => {
  let testBoard = {
    level: 25,
    width: 20,
    height: 15,
    targetFruits: 20,
    fruitsEatenInLevel: 0,
    baseTickMs: 150,
    direction: 'RIGHT',
    snake: [[5, 5], [4, 5], [3, 5]],
    obstacles: [],
    magnetTimer: 0,
    chillTimer: 0,
    boostTimer: 0,
    comboCount: 0,
    food: { x: 6, y: 5, type: itemType, spawnTime: Date.now(), lifetimeMs: 14000 }
  };

  let tickRes = SnakeEngine.tick(testBoard);
  assert(tickRes.event === 'GROW', `Item ${itemType} must trigger GROW event on collision`);
  assert(tickRes.nextState.food === null, `Item ${itemType} must be permanently cleared after consumption`);
  assert(tickRes.needsFoodSpawn === true, `Item ${itemType} must request replacement food spawn`);

  if (itemType === 'GOLDEN_APPLE') {
    assert(tickRes.nextState.fruitsEatenInLevel === 2, `Golden Apple must give +2 fruits`);
    assert(tickRes.noveltyEffect.type === 'GOLDEN_APPLE', `Golden Apple novelty effect verified`);
  } else if (itemType === 'CHILL_BERRY') {
    assert(tickRes.nextState.chillTimer === 7000, `Chill Berry must grant 7000ms chillTimer`);
    assert(tickRes.nextState.speedModifier === 1.35, `Chill Berry speed modifier must be 1.35`);
  } else if (itemType === 'SPEED_BOOST') {
    assert(tickRes.nextState.boostTimer === 5000, `Speed Boost must grant 5000ms boostTimer`);
    assert(tickRes.nextState.speedModifier === 0.72, `Speed Boost speed modifier must be 0.72`);
  } else if (itemType === 'MAGNET') {
    assert(tickRes.nextState.magnetTimer === 8000, `Magnet must grant 8000ms magnetTimer`);
  } else if (itemType === 'MYSTERY') {
    assert(tickRes.noveltyEffect.type === 'MYSTERY', `Mystery box roll effect verified`);
  }
});

// Stacking & Mutual Exclusivity: Chill Berry replaces Speed Boost, and vice versa
let boostBoard = {
  level: 15,
  width: 20,
  height: 15,
  targetFruits: 10,
  fruitsEatenInLevel: 0,
  baseTickMs: 150,
  direction: 'RIGHT',
  snake: [[5, 5], [4, 5], [3, 5]],
  boostTimer: 4000,
  chillTimer: 0,
  food: { x: 6, y: 5, type: 'CHILL_BERRY', spawnTime: Date.now() }
};
let chillOverrideRes = SnakeEngine.tick(boostBoard);
assert(chillOverrideRes.nextState.chillTimer === 7000 && chillOverrideRes.nextState.boostTimer === 0, `Chill berry must cleanly override speed boost`);

// 4. Persistence Roundtrip & Board Adaptation Test
console.log('\n--- Test Suite 4: Persistence Serialization & Responsive Adaptation ---');

let fullProgression = {
  schemaVersion: 1,
  stateRevision: 42,
  currentLevel: 18,
  completedCount: 17,
  totalStars: 48,
  highestStarMilestone: 0,
  totalFruitsEaten: 150,
  longestSnake: 16,
  totalPlayTimeSeconds: 1200,
  bestStreak: 12,
  currentStreak: 5,
  currentBoard: {
    level: 18,
    seed: 98765,
    width: 24,
    height: 16,
    targetFruits: 12,
    baseTickMs: 135,
    layoutType: 'CHAMBERS',
    isMilestone: false,
    isOasis: false,
    snake: [[8, 8], [7, 8], [6, 8], [5, 8]],
    direction: 'RIGHT',
    obstacles: [100, 101, 102],
    gateways: [],
    fruitsEatenInLevel: 6,
    elapsedSeconds: 24,
    restartsCount: 0,
    food: { x: 12, y: 8, type: 'MAGNET', spawnTime: Date.now(), lifetimeMs: 15000 },
    chillTimer: 0,
    boostTimer: 0,
    magnetTimer: 5400,
    comboCount: 3,
    consecutiveSpecials: 1,
    noveltyCooldown: 2,
    speedModifier: 1.0,
    longestSnakeInLevel: 10
  }
};

let sanitized = SnakePersistence.sanitizeState(fullProgression);
assert(sanitized.currentBoard !== null, `Sanitized state must retain currentBoard`);
assert(sanitized.currentBoard.magnetTimer === 5400, `Sanitized state must retain magnetTimer`);
assert(sanitized.currentBoard.comboCount === 3, `Sanitized state must retain comboCount`);
assert(sanitized.currentBoard.consecutiveSpecials === 1, `Sanitized state must retain consecutiveSpecials`);
assert(sanitized.currentBoard.noveltyCooldown === 2, `Sanitized state must retain noveltyCooldown`);

// Board Adaptation Test: Resize from 24x16 to 32x18
let adaptedBoard = SnakeGenerator.adaptBoardDimensions(sanitized.currentBoard, 32, 18);
assert(adaptedBoard.width === 32 && adaptedBoard.height === 18, `Adapted board must have new dimensions 32x18`);
assert(adaptedBoard.snake.length === 4, `Adapted board must preserve snake length`);
assert(adaptedBoard.snake.every(s => s[0] >= 0 && s[0] < 32 && s[1] >= 0 && s[1] < 18), `All snake segments must be in-bounds after adaptation`);
assert(adaptedBoard.food !== null, `Adapted board must retain valid food`);

console.log(`\n========================================`);
console.log(`TEST RESULTS: ${testsPassed} PASSED, ${testsFailed} FAILED`);
console.log(`========================================\n`);

if (testsFailed > 0) {
  process.exit(1);
} else {
  process.exit(0);
}
