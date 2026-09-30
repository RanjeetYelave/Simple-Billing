/**
 * Comprehensive Automated Gameplay, Obstacle, Balance & Solvability Audit
 */

const SnakeEngine = require('./src/main/webapp/js/snakeEngine');
const SnakeGenerator = require('./src/main/webapp/js/snakeGenerator');
const SnakePersistence = require('./src/main/webapp/js/snakePersistence');
const CircuitEngine = require('./src/main/webapp/js/circuitEngine');
const CircuitGenerator = require('./src/main/webapp/js/circuitGenerator');
const CircuitPersistence = require('./src/main/webapp/js/circuitPersistence');

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

console.log('=== RUNNING FINAL GAMEPLAY, OBSTACLE, BALANCE & SOLVABILITY AUDIT ===\n');

// 1. Snake Obstacle Progression & Rectangular Mapping Validation
console.log('--- Test 1: Snake Obstacle Progression & Rectangular Collision Checks ---');

const rectangularDimensions = [
  { width: 34, height: 16, desc: 'Desktop Standard' },
  { width: 44, height: 20, desc: 'Widescreen HD' },
  { width: 24, height: 14, desc: 'Compact Laptop' },
  { width: 18, height: 12, desc: 'Tablet Mini' }
];

rectangularDimensions.forEach(dim => {
  for (let lvl = 1; lvl <= 100; lvl++) {
    const config = SnakeGenerator.getLevelConfig(lvl, dim);
    const board = SnakeGenerator.generateLevel(lvl, dim);

    assert(board.width === dim.width && board.height === dim.height, `Board dimensions match target ${dim.desc} at level ${lvl}`);

    if (config.layoutType === 'CLEAN' || config.layoutType === 'OASIS') {
      assert(board.obstacles.length === 0, `Clean/Oasis level ${lvl} must have 0 obstacles`);
    } else {
      assert(board.obstacles.length > 0, `Obstacle level ${lvl} (${config.layoutType}) must have > 0 obstacles on ${dim.desc}`);
      
      // Verify all obstacles are strictly in-bounds
      const allInBounds = board.obstacles.every(idx => {
        const x = idx % board.width;
        const y = Math.floor(idx / board.width);
        return x >= 0 && x < board.width && y >= 0 && y < board.height;
      });
      assert(allInBounds, `All obstacles must be in-bounds at level ${lvl} on ${dim.desc}`);

      // Verify snake does not start on an obstacle
      const obstacleSet = new Set(board.obstacles);
      const snakeCollides = board.snake.some(seg => obstacleSet.has(seg[1] * board.width + seg[0]));
      assert(!snakeCollides, `Snake starting position must never intersect obstacles at level ${lvl} (${config.layoutType}) on ${dim.desc}`);
    }

    // Verify initial food is in-bounds and not on snake or obstacles
    assert(board.food !== null, `Initial food must exist at level ${lvl}`);
    assert(board.food.x >= 0 && board.food.x < board.width && board.food.y >= 0 && board.food.y < board.height, `Food must be in-bounds at level ${lvl}`);
    const foodIdx = board.food.y * board.width + board.food.x;
    const foodOnSnake = board.snake.some(seg => seg[0] === board.food.x && seg[1] === board.food.y);
    const foodOnObstacle = (board.obstacles || []).includes(foodIdx);
    assert(!foodOnSnake && !foodOnObstacle, `Food must not spawn on snake or obstacle at level ${lvl}`);
  }
});

// 2. Obstacle Collision Detection Test
console.log('\n--- Test 2: Snake Obstacle Collision Detection ---');
const obstacleBoard = {
  level: 10,
  width: 20,
  height: 15,
  targetFruits: 10,
  fruitsEatenInLevel: 0,
  baseTickMs: 150,
  direction: 'RIGHT',
  snake: [[5, 5], [4, 5], [3, 5]],
  obstacles: [5 * 20 + 6], // Obstacle placed at (6, 5) directly in front of head
  food: { x: 10, y: 10, type: 'APPLE', spawnTime: Date.now() }
};

const collisionResult = SnakeEngine.tick(obstacleBoard);
assert(collisionResult.event === 'DEATH', `Snake moving into obstacle tile must trigger DEATH event`);
assert(collisionResult.deathReason === 'OBSTACLE_COLLISION', `Death reason must be OBSTACLE_COLLISION`);
assert(collisionResult.nextState.isGameOver === true, `isGameOver must be true upon obstacle collision`);

// 3. Special Item Balance Simulation (Goal of 10 Apples)
console.log('\n--- Test 3: Special Item Balance Across 1,000 Levels with 10-Apple Goals ---');

let totalRuns = 1000;
let totalNormalApplesInRun = 0;
let totalSpecialsInRun = 0;
let consecutiveSpecialViolations = 0;

for (let r = 0; r < totalRuns; r++) {
  const lvl = 5 + (r % 90); // Levels 5–95
  const config = SnakeGenerator.getLevelConfig(lvl, { width: 34, height: 16 });
  let mockBoard = SnakeGenerator.generateLevel(lvl, { width: 34, height: 16 });
  
  let applesInLevel = 0;
  let specialsInLevel = 0;
  let consecutive = 0;

  for (let appleCount = 0; appleCount < 10; appleCount++) {
    const fruit = SnakeGenerator.determineNextFruit(mockBoard, config, Math.random);
    if (fruit.type === 'APPLE') {
      applesInLevel++;
      consecutive = 0;
      mockBoard.noveltyCooldown = Math.max(0, (mockBoard.noveltyCooldown || 0) - 1);
    } else {
      specialsInLevel++;
      consecutive++;
      mockBoard.noveltyCooldown = 1;
      if (consecutive > 2) {
        consecutiveSpecialViolations++;
      }
    }
    mockBoard.consecutiveSpecials = consecutive;
  }

  totalNormalApplesInRun += applesInLevel;
  totalSpecialsInRun += specialsInLevel;
}

const avgNormals = totalNormalApplesInRun / totalRuns;
const avgSpecials = totalSpecialsInRun / totalRuns;

console.log(`Average Normal Apples per 10-Apple Goal: ${avgNormals.toFixed(2)} (Target: 7.0 - 8.0)`);
console.log(`Average Special Items per 10-Apple Goal: ${avgSpecials.toFixed(2)} (Target: 2.0 - 3.0)`);
console.log(`Consecutive Special Violations (>2): ${consecutiveSpecialViolations}`);

assert(avgNormals >= 7.0 && avgNormals <= 8.5, `Average normal apples ${avgNormals.toFixed(2)} must be within 7.0-8.5`);
assert(avgSpecials >= 1.5 && avgSpecials <= 3.0, `Average special items ${avgSpecials.toFixed(2)} must be within 1.5-3.0`);
assert(consecutiveSpecialViolations === 0, `No more than 2 consecutive special items ever allowed`);

// 4. Circuit Connect Hint Timer & Solvability Audit (100 Levels)
console.log('\n--- Test 4: Circuit Connect Hint Timer & 100% Solvability Audit ---');

let validPuzzles = 0;
for (let lvl = 1; lvl <= 100; lvl++) {
  const puzzle = CircuitGenerator.generateLevel(lvl);
  const isValid = CircuitGenerator.validatePlayablePuzzle(puzzle);
  if (isValid) validPuzzles++;
}
console.log(`Solvable Circuit Connect Puzzles: ${validPuzzles} / 100`);
assert(validPuzzles === 100, `All 100 generated Circuit Connect puzzles must be 100% solvable`);

// Test Hint Economy Roundtrip & Active Play Accumulation
let sampleCircuitState = {
  schemaVersion: 1,
  currentLevel: 14,
  completedCount: 13,
  totalStars: 39,
  highestStarMilestone: 0,
  totalPlayTimeSeconds: 1500,
  hintBalance: 2,
  hintProgressSeconds: 1450 // 24m 10s accumulated (Next hint in 350s / 5m 50s)
};

let sanitizedCircuit = CircuitPersistence.sanitizeState(sampleCircuitState);
assert(sanitizedCircuit.hintBalance === 2, `Sanitized circuit state must retain hintBalance = 2`);
assert(sanitizedCircuit.hintProgressSeconds === 1450, `Sanitized circuit state must retain hintProgressSeconds = 1450`);

console.log(`\n========================================`);
console.log(`AUDIT RESULTS: ${testsPassed} PASSED, ${testsFailed} FAILED`);
console.log(`========================================\n`);

if (testsFailed > 0) {
  process.exit(1);
} else {
  process.exit(0);
}
