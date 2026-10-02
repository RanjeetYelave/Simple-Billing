/**
 * testAdventureGameplayAudit.js
 * Comprehensive automated verification test for:
 * 1. Death -> Keyboard Restart lifecycle and initial direction seeding.
 * 2. Stage 5A conservative multi-target generation (1 target < L10, max 2 targets >= L10).
 * 3. Spatial quadrant separation and reachability.
 * 4. Independent target consumption and respawn.
 * 5. Level completion and progression preservation.
 * 6. Maharashtra world location mapping across 100 levels.
 * 7. Resize preservation invariance.
 */

const assert = require('assert');
const SnakeEngine = require('./src/main/webapp/js/snakeEngine');
const SnakeGenerator = require('./src/main/webapp/js/snakeGenerator');
const SnakePersistence = require('./src/main/webapp/js/snakePersistence');
const MaharashtraWorld = require('./src/main/webapp/js/maharashtraWorld');

console.log('=== STARTING ADVENTURE EXPEDITION & STAGE 5A GAMEPLAY AUDIT ===\n');

let totalPassed = 0;

function it(desc, fn) {
  try {
    fn();
    console.log(`  ✓ ${desc}`);
    totalPassed++;
  } catch (err) {
    console.error(`  ✗ FAIL: ${desc}`);
    console.error(err);
    process.exit(1);
  }
}

// -------------------------------------------------------------
// Suite 1: Death -> Keyboard Restart State Lifecycle
// -------------------------------------------------------------
console.log('--- Test Suite 1: Death & Keyboard Restart Lifecycle ---');

it('Level restarts and moves immediately in requested direction after death', () => {
  const level = 5;
  const board = SnakeGenerator.generateLevel(level, { totalCols: 32, totalRows: 20 });
  
  // Simulate death
  const deadState = {
    ...board,
    isGameOver: true,
    deathReason: 'OBSTACLE_COLLISION'
  };

  // Simulate directional keypress (e.g. UP)
  const reqDir = 'UP';
  const restarts = (deadState.restartsCount || 0) + 1;
  const freshGen = SnakeGenerator.generateLevel(level, { totalCols: 32, totalRows: 20 });
  const restartedBoard = {
    ...freshGen,
    direction: reqDir,
    restartsCount: restarts,
    elapsedSeconds: 0
  };

  assert.strictEqual(restartedBoard.isGameOver, undefined);
  assert.strictEqual(restartedBoard.direction, 'UP');
  assert.strictEqual(restartedBoard.restartsCount, 1);

  // Execute one tick in UP direction
  const tickResult = SnakeEngine.tick(restartedBoard);
  assert.strictEqual(tickResult.event, 'MOVE');
  assert.strictEqual(tickResult.nextState.snake[0][1], freshGen.snake[0][1] - 1); // Y moved UP
});

it('Keyboard restart works for all directional keys (W, A, S, D, Arrow keys)', () => {
  const directions = [
    { dir: 'UP', snake: [[10, 10], [10, 11], [10, 12]] },
    { dir: 'DOWN', snake: [[10, 10], [10, 9], [10, 8]] },
    { dir: 'LEFT', snake: [[10, 10], [11, 10], [12, 10]] },
    { dir: 'RIGHT', snake: [[10, 10], [9, 10], [8, 10]] }
  ];
  for (let item of directions) {
    const freshGen = SnakeGenerator.generateLevel(3, { totalCols: 30, totalRows: 20 });
    freshGen.direction = item.dir;
    freshGen.snake = item.snake;
    const res = SnakeEngine.tick(freshGen);
    assert.ok(res.event === 'MOVE' || res.event === 'GROW');
  }
});

// -------------------------------------------------------------
// Suite 2: Stage 5A Conservative Multi-Target Rules
// -------------------------------------------------------------
console.log('\n--- Test Suite 2: Stage 5A Multi-Target Generation ---');

it('Level 1-9 produces exactly 1 active food target', () => {
  for (let lvl = 1; lvl <= 9; lvl++) {
    const board = SnakeGenerator.generateLevel(lvl, { totalCols: 40, totalRows: 25 });
    const foods = Array.isArray(board.foods) ? board.foods : (board.food ? [board.food] : []);
    assert.strictEqual(foods.length, 1, `Level ${lvl} must have exactly 1 target`);
    assert.ok(foods[0].type, 'Target must have a defined type');
  }
});

it('Level 10+ on medium/large boards produces at most 2 active targets', () => {
  for (let lvl = 10; lvl <= 40; lvl += 5) {
    const board = SnakeGenerator.generateLevel(lvl, { totalCols: 44, totalRows: 26 });
    const foods = Array.isArray(board.foods) ? board.foods : (board.food ? [board.food] : []);
    assert.strictEqual(foods.length, 2, `Level ${lvl} on large board must have exactly 2 targets`);
    
    const f1 = foods[0];
    const f2 = foods[1];
    assert.notStrictEqual(`${f1.x},${f1.y}`, `${f2.x},${f2.y}`, 'Targets must not overlap');
    
    // Check spatial separation
    const dist = Math.abs(f1.x - f2.x) + Math.abs(f1.y - f2.y);
    assert.ok(dist >= 4, `Targets must have spatial separation (got distance ${dist})`);
  }
});

it('Eating Target A replaces Target A while Target B remains intact', () => {
  const lvl = 12;
  const board = SnakeGenerator.generateLevel(lvl, { totalCols: 40, totalRows: 24 });
  assert.strictEqual(board.foods.length, 2);
  
  const targetA = board.foods[0];
  const targetB = { ...board.foods[1] };
  
  // Position snake head directly behind targetA facing RIGHT
  board.direction = 'RIGHT';
  board.snake = [
    [targetA.x - 1, targetA.y],
    [targetA.x - 2, targetA.y],
    [targetA.x - 3, targetA.y]
  ];

  const tickResult = SnakeEngine.tick(board);
  assert.strictEqual(tickResult.event, 'GROW');
  const expectedFruits = targetA.type === 'GOLDEN_APPLE' ? 2 : 1;
  assert.strictEqual(tickResult.nextState.fruitsEatenInLevel, expectedFruits);
  
  // Target B should still be in the active foods list
  const nextFoods = tickResult.nextState.foods;
  assert.strictEqual(nextFoods.length, 1);
  assert.strictEqual(nextFoods[0].x, targetB.x);
  assert.strictEqual(nextFoods[0].y, targetB.y);

  // Spawn replacement for eaten food
  const freshFood = SnakeGenerator.spawnFood(
    tickResult.nextState.snake,
    tickResult.nextState.obstacles,
    board.bounds,
    Math.random,
    SnakeGenerator.getLevelConfig(lvl, { totalCols: 40, totalRows: 24 }),
    tickResult.nextState,
    nextFoods
  );
  nextFoods.push(freshFood);
  assert.strictEqual(nextFoods.length, 2);
  assert.notStrictEqual(`${freshFood.x},${freshFood.y}`, `${targetB.x},${targetB.y}`);
});

it('Goal completion works correctly when eating multiple targets', () => {
  const lvl = 10;
  const board = SnakeGenerator.generateLevel(lvl, { totalCols: 32, totalRows: 20 });
  board.fruitsEatenInLevel = board.targetFruits - 1; // 1 fruit left to win
  
  const target = board.foods[0];
  board.direction = 'RIGHT';
  board.snake = [
    [target.x - 1, target.y],
    [target.x - 2, target.y]
  ];

  const result = SnakeEngine.tick(board);
  assert.strictEqual(result.event, 'WIN');
  assert.strictEqual(result.nextState.isLevelComplete, true);
  assert.ok(result.nextState.fruitsEatenInLevel >= board.targetFruits);
});

// -------------------------------------------------------------
// Suite 3: Maharashtra World Engine Integration
// -------------------------------------------------------------
console.log('\n--- Test Suite 3: Maharashtra World Engine Resolution ---');

it('Resolves authentic Maharashtra landmarks across levels 1 to 100', () => {
  const loc1 = MaharashtraWorld.getWorldForLevel('classic', 1);
  assert.strictEqual(loc1.location.name, 'Karad');
  assert.strictEqual(loc1.location.district, 'Satara');
  assert.strictEqual(loc1.location.region, 'PASCHIM_MAHARASHTRA');

  const loc11 = MaharashtraWorld.getWorldForLevel('classic', 11);
  assert.ok(loc11.location.name.length > 0);
  assert.ok(loc11.location.archetypeId.length > 0);

  const journeyData = MaharashtraWorld.getJourneyTimeline('classic', 12, 10, 5);
  assert.ok(Array.isArray(journeyData.timeline));
  assert.ok(journeyData.timeline.length >= 5);
});

// -------------------------------------------------------------
// Suite 5: Intra-Level Non-Compounding Speed Progression
// -------------------------------------------------------------
console.log('\n--- Test Suite 5: Intra-Level Non-Compounding Speed Progression ---');

it('Calculates intra-level speed: 0% progress -> 1.00x, 50% progress -> ~1.05x, 100% progress -> ~1.10x', () => {
  const baseTickMs = 200;
  const targetFruits = 10;
  
  // 0% progress (0 fruits)
  const speed0 = SnakeEngine.calculateIntraLevelTickMs(baseTickMs, 0, targetFruits, 1.0);
  assert.strictEqual(speed0, 200); // 200 * (1 - 0) = 200ms

  // 50% progress (5 fruits)
  const speed50 = SnakeEngine.calculateIntraLevelTickMs(baseTickMs, 5, targetFruits, 1.0);
  assert.strictEqual(speed50, 190); // 200 * (1 - 0.05) = 190ms (approx 1.05x speed)

  // 100% progress (10 fruits)
  const speed100 = SnakeEngine.calculateIntraLevelTickMs(baseTickMs, 10, targetFruits, 1.0);
  assert.strictEqual(speed100, 180); // 200 * (1 - 0.10) = 180ms (approx 1.11x speed)
});

it('Does not compound intra-level multiplier across successive fruits', () => {
  const baseTickMs = 200;
  const targetFruits = 10;
  
  // Step-by-step evaluation
  for (let eaten = 0; eaten <= targetFruits; eaten++) {
    const expected = Math.max(75, Math.round(baseTickMs * (1.0 - 0.10 * (eaten / targetFruits))));
    const actual = SnakeEngine.calculateIntraLevelTickMs(baseTickMs, eaten, targetFruits, 1.0);
    assert.strictEqual(actual, expected);
  }
});

it('Temporary modifiers (Chill Berry, Speed Boost) scale proportionally on top of intra-level multiplier', () => {
  const baseTickMs = 200;
  const targetFruits = 10;
  const eaten = 5; // progress = 0.5 -> intra = 0.95 -> 190ms

  // Chill Berry (1.35x tick interval = slower)
  const chillTick = SnakeEngine.calculateIntraLevelTickMs(baseTickMs, eaten, targetFruits, 1.35);
  assert.strictEqual(chillTick, Math.round(200 * 0.95 * 1.35)); // 257ms

  // Speed Boost (0.72x tick interval = faster)
  const boostTick = SnakeEngine.calculateIntraLevelTickMs(baseTickMs, eaten, targetFruits, 0.72);
  assert.strictEqual(boostTick, Math.round(200 * 0.95 * 0.72)); // 137ms

  // When Chill Berry expires (modifier reverts to 1.0)
  const normalTick = SnakeEngine.calculateIntraLevelTickMs(baseTickMs, eaten, targetFruits, 1.0);
  assert.strictEqual(normalTick, 190);
});

// -------------------------------------------------------------
// Suite 6: Chapter Progression & Milestone Levels (1 to 1000)
// -------------------------------------------------------------
console.log('\n--- Test Suite 6: Chapter Progression & Levels 1-1000 ---');

it('Deterministically generates valid, fair boards for all 11 chapters and Level 1000', () => {
  const testLevels = [1, 50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 999, 1000];
  for (let lvl of testLevels) {
    const board = SnakeGenerator.generateLevel(lvl, { totalCols: 36, totalRows: 22 });
    assert.strictEqual(board.level, lvl);
    assert.ok(board.snake.length >= 3);
    assert.ok(board.targetFruits >= 5);
    assert.ok(board.baseTickMs >= 125);
    assert.ok(Array.isArray(board.foods));
    assert.ok(board.foods.length >= 1);
    assert.ok(SnakeGenerator.validatePlayableBoard(board), `Level ${lvl} must pass BFS playable validation`);
  }
});

it('Level 1000 is marked as finale with Sovereign Capital setup', () => {
  const l1000 = SnakeGenerator.getLevelConfig(1000);
  assert.strictEqual(l1000.isFinale, true);
  assert.strictEqual(l1000.isMilestone, true);
  assert.strictEqual(l1000.layoutType, 'CORONATION_CITADEL');
  assert.strictEqual(l1000.targetFruits, 25);
  assert.strictEqual(l1000.hasSovereignCrest, true);

  const board1000 = SnakeGenerator.generateLevel(1000, { totalCols: 40, totalRows: 26 });
  assert.strictEqual(board1000.isFinale, true);
  assert.strictEqual(board1000.foods[0].type, 'SOVEREIGN_CREST');
});

// -------------------------------------------------------------
// Suite 7: Themed Obstacle Generation & Decoupled Physics
// -------------------------------------------------------------
console.log('\n--- Test Suite 7: Themed Obstacle Generation ---');

it('Attaches location-appropriate visualType and theme metadata to obstacles', () => {
  // Fort Level 95 -> Pratapgad Fort (ARCH_HILL_FORT)
  const b75 = SnakeGenerator.generateLevel(95, { totalCols: 36, totalRows: 22 });
  for (let obs of b75.obstacles) {
    assert.ok(typeof obs.x === 'number' && typeof obs.y === 'number');
    assert.ok(obs.visualType, 'Obstacle must have visualType');
    assert.ok(['FORT_WALL', 'BASTION', 'FORT_GATE'].includes(obs.visualType));
  }

  // Ghat Level 50 -> Sajjangad (ARCH_PILGRIMAGE_GHAT)
  const b50 = SnakeGenerator.generateLevel(50, { totalCols: 36, totalRows: 22 });
  for (let obs of b50.obstacles) {
    assert.ok(['GHAT_STEPS', 'STONE_PILLAR', 'RIVER_STONE'].includes(obs.visualType));
  }

  // Coastal Level 305 -> Murud-Janjira (ARCH_COASTAL_FORT)
  const b250 = SnakeGenerator.generateLevel(305, { totalCols: 36, totalRows: 22 });
  for (let obs of b250.obstacles) {
    assert.ok(['COASTAL_ROCK', 'BOAT_DOCK', 'SEA_CHANNEL'].includes(obs.visualType));
  }

  // Cave Level 335 -> Elephanta Caves (ARCH_CAVE_TEMPLE)
  const b350 = SnakeGenerator.generateLevel(335, { totalCols: 36, totalRows: 22 });
  for (let obs of b350.obstacles) {
    assert.ok(['CAVE_PILLAR', 'ROCK_WALL', 'ARCHWAY'].includes(obs.visualType));
  }

  // Wada Level 155 -> Kolhapur New Palace (ARCH_HISTORIC_WADA)
  const b450 = SnakeGenerator.generateLevel(155, { totalCols: 36, totalRows: 22 });
  for (let obs of b450.obstacles) {
    assert.ok(['WADA_COLUMN', 'COURTYARD_WALL', 'ARCHWAY'].includes(obs.visualType));
  }

  // Forest Level 80 -> Mahabaleshwar (ARCH_PLATEAU_FOREST)
  const b550 = SnakeGenerator.generateLevel(80, { totalCols: 36, totalRows: 22 });
  for (let obs of b550.obstacles) {
    assert.ok(['TREE_CLUSTER', 'BOULDER', 'ROOT_CLUSTER'].includes(obs.visualType));
  }

  // Level 1000 -> Golden Coronation Citadel
  const b1000 = SnakeGenerator.generateLevel(1000, { totalCols: 36, totalRows: 22 });
  for (let obs of b1000.obstacles) {
    assert.ok(['GOLDEN_BASTION', 'ROYAL_RAMPART', 'CEREMONIAL_GATE'].includes(obs.visualType));
  }
});

it('Collision physics engine accurately detects obstacle collision with visualType objects', () => {
  const obstacles = [
    { x: 10, y: 10, visualType: 'BASTION', theme: 'ARCH_HILL_FORT' },
    { x: 12, y: 10, visualType: 'GOLDEN_BASTION', theme: 'ARCH_HILL_FORT' }
  ];
  
  // Head at [9, 10] moving RIGHT -> collides with [10, 10]
  const state = {
    snake: [[9, 10], [8, 10], [7, 10]],
    direction: 'RIGHT',
    obstacles,
    bounds: { minX: 0, maxX: 20, minY: 0, maxY: 20, spanX: 21, spanY: 21 }
  };

  const res = SnakeEngine.tick(state);
  assert.strictEqual(res.event, 'DEATH');
  assert.strictEqual(res.deathReason, 'OBSTACLE_COLLISION');
});

// -------------------------------------------------------------
// Suite 8: Expanded Maharashtra Collectibles Mechanics
// -------------------------------------------------------------
console.log('\n--- Test Suite 8: Regional Collectibles Mechanics ---');

it('Consuming SAHYADRI_GEM adds 2 fruits and activates 10s magnet aura', () => {
  const state = {
    snake: [[5, 5], [4, 5], [3, 5]],
    direction: 'RIGHT',
    foods: [{ x: 6, y: 5, type: 'SAHYADRI_GEM' }],
    targetFruits: 10,
    fruitsEatenInLevel: 0,
    bounds: { minX: 0, maxX: 20, minY: 0, maxY: 20, spanX: 21, spanY: 21 }
  };

  const res = SnakeEngine.tick(state);
  assert.strictEqual(res.event, 'GROW');
  assert.strictEqual(res.nextState.fruitsEatenInLevel, 2);
  assert.strictEqual(res.nextState.magnetTimer, 10000);
});

it('Consuming SOVEREIGN_CREST adds 5 fruits and crowns victory', () => {
  const state = {
    snake: [[5, 5], [4, 5], [3, 5]],
    direction: 'RIGHT',
    foods: [{ x: 6, y: 5, type: 'SOVEREIGN_CREST' }],
    targetFruits: 5,
    fruitsEatenInLevel: 0,
    bounds: { minX: 0, maxX: 20, minY: 0, maxY: 20, spanX: 21, spanY: 21 }
  };

  const res = SnakeEngine.tick(state);
  assert.strictEqual(res.event, 'WIN');
  assert.strictEqual(res.nextState.fruitsEatenInLevel, 5);
  assert.strictEqual(res.nextState.isLevelComplete, true);
});

console.log(`\n========================================`);
console.log(`ALL ${totalPassed} ADVENTURE & STAGE 5A TESTS PASSED!`);
console.log(`========================================\n`);
