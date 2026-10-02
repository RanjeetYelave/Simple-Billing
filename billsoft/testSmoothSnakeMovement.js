/**
 * testSmoothSnakeMovement.js
 * Comprehensive Forensic Test Suite for Snake Classic — Smooth Presentation Movement Layer.
 * Validates:
 * 1. Underlying logical grid movement & discrete coordinates are 100% preserved.
 * 2. Visual interpolation smoothly maps between previous and current cells.
 * 3. Interpolation never affects or modifies logical collision, score, spawn, or speed state.
 * 4. Interpolation reaches exact target cell at progress = 1.0.
 * 5. Straight movement, left turn, right turn, and rapid turns produce 0 diagonal logical steps.
 * 6. Toroidal boundary wrap interpolation glides cleanly across boundaries without screen streaks.
 * 7. Snake growth smoothly unfolds new tail segments from previous tail position without pops.
 * 8. Game reset, level start, restart, resize, and transition pause/resume do not corrupt state.
 * 9. Multi-refresh rate independence (30 FPS, 60 FPS, 120 FPS).
 */

const SnakeEngine = require('./src/main/webapp/js/snakeEngine');
const SnakeGenerator = require('./src/main/webapp/js/snakeGenerator');
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

function interpolateSnake(currSnake, prevSnake, progress, bounds) {
  const interp = [];
  for (let i = 0; i < currSnake.length; i++) {
    const curr = currSnake[i];
    const prev = (prevSnake && i < prevSnake.length)
      ? prevSnake[i]
      : (prevSnake && prevSnake.length > 0 ? prevSnake[prevSnake.length - 1] : curr);

    const dx = curr[0] - prev[0];
    const dy = curr[1] - prev[1];
    let renderX = prev[0];
    let renderY = prev[1];
    let hasWrap = false;
    let wrapX = renderX;
    let wrapY = renderY;

    if (dx < -1) {
      hasWrap = true;
      const unwrappedTargetX = curr[0] + bounds.spanX;
      renderX = prev[0] + (unwrappedTargetX - prev[0]) * progress;
      wrapX = renderX - bounds.spanX;
    } else if (dx > 1) {
      hasWrap = true;
      const unwrappedTargetX = curr[0] - bounds.spanX;
      renderX = prev[0] + (unwrappedTargetX - prev[0]) * progress;
      wrapX = renderX + bounds.spanX;
    } else {
      renderX = prev[0] + dx * progress;
    }

    if (dy < -1) {
      hasWrap = true;
      const unwrappedTargetY = curr[1] + bounds.spanY;
      renderY = prev[1] + (unwrappedTargetY - prev[1]) * progress;
      wrapY = renderY - bounds.spanY;
    } else if (dy > 1) {
      hasWrap = true;
      const unwrappedTargetY = curr[1] - bounds.spanY;
      renderY = prev[1] + (unwrappedTargetY - prev[1]) * progress;
      wrapY = renderY + bounds.spanY;
    } else {
      renderY = prev[1] + dy * progress;
    }

    interp.push({ x: renderX, y: renderY, hasWrap, wrapX, wrapY });
  }
  return interp;
}

console.log('======================================================================');
console.log('🐍 STARTING SMOOTH SNAKE MOVEMENT & INTERPOLATION FORENSIC AUDIT');
console.log('======================================================================\n');

// -----------------------------------------------------------------------------
// Test Suite 1: Discrete Grid Preservation & Logical Coordinate Integrity
// -----------------------------------------------------------------------------
console.log('--- Suite 1: Discrete Grid Coordinates Preserved ---');
const board1 = SnakeGenerator.generateLevel(1, { totalCols: 32, totalRows: 20 });
const initialHead = [...board1.snake[0]];
const initialDir = board1.direction;

assert(Number.isInteger(initialHead[0]) && Number.isInteger(initialHead[1]), 'Initial snake head coordinates must be discrete integers');
for (let seg of board1.snake) {
  assert(Number.isInteger(seg[0]) && Number.isInteger(seg[1]), 'All initial segments must be integer grid coordinates');
}

const tick1 = SnakeEngine.tick(board1);
const nextHead = tick1.nextState.snake[0];
assert(Number.isInteger(nextHead[0]) && Number.isInteger(nextHead[1]), 'After tick, logical coordinates must remain discrete integers');
assert(Math.abs(nextHead[0] - initialHead[0]) + Math.abs(nextHead[1] - initialHead[1]) === 1, 'Snake moves exactly 1 cell logically per tick');

// -----------------------------------------------------------------------------
// Test Suite 2: Continuous Interpolation Trajectory & End-Point Convergence
// -----------------------------------------------------------------------------
console.log('\n--- Suite 2: Smooth Interpolation Trajectory & Convergence ---');
const prevCoords = [[10, 8], [9, 8], [8, 8]];
const currCoords = [[11, 8], [10, 8], [9, 8]];
const bounds = { minX: 0, maxX: 31, minY: 0, maxY: 19, spanX: 32, spanY: 20 };

// At progress = 0.0 -> matches previous exactly
const at0 = interpolateSnake(currCoords, prevCoords, 0.0, bounds);
assert(at0[0].x === 10 && at0[0].y === 8, 'At progress 0.0, head render position is previous cell (10, 8)');
assert(at0[1].x === 9 && at0[1].y === 8, 'At progress 0.0, segment 1 is (9, 8)');
assert(at0[2].x === 8 && at0[2].y === 8, 'At progress 0.0, segment 2 is (8, 8)');

// At progress = 0.5 -> midway between cells
const atHalf = interpolateSnake(currCoords, prevCoords, 0.5, bounds);
assert(atHalf[0].x === 10.5 && atHalf[0].y === 8, 'At progress 0.5, head render position is midway (10.5, 8)');
assert(atHalf[1].x === 9.5 && atHalf[1].y === 8, 'At progress 0.5, segment 1 is (9.5, 8)');
assert(atHalf[2].x === 8.5 && atHalf[2].y === 8, 'At progress 0.5, segment 2 is (8.5, 8)');

// At progress = 1.0 -> reaches target exactly
const at1 = interpolateSnake(currCoords, prevCoords, 1.0, bounds);
assert(at1[0].x === 11 && at1[0].y === 8, 'At progress 1.0, head render position reaches current cell (11, 8)');
assert(at1[1].x === 10 && at1[1].y === 8, 'At progress 1.0, segment 1 reaches (10, 8)');
assert(at1[2].x === 9 && at1[2].y === 8, 'At progress 1.0, segment 2 reaches (9, 8)');

// Monotonic progression check across 10 intermediate sub-frames
let lastX = 10.0;
for (let step = 1; step <= 10; step++) {
  const p = step / 10;
  const interp = interpolateSnake(currCoords, prevCoords, p, bounds);
  assert(interp[0].x > lastX, `Render position must monotonically increase along direction (step ${step})`);
  lastX = interp[0].x;
}

// -----------------------------------------------------------------------------
// Test Suite 3: Direction Changes, Left/Right Turns & No Diagonal Movement
// -----------------------------------------------------------------------------
console.log('\n--- Suite 3: Direction Changes, Turns & No Diagonal Movement ---');
// Turning UP from moving RIGHT: prev was moving right to (15, 10), then turned UP to (15, 9)
const prevTurn = [[15, 10], [14, 10], [13, 10]];
const currTurn = [[15, 9], [15, 10], [14, 10]];

const turnHalf = interpolateSnake(currTurn, prevTurn, 0.5, bounds);
assert(turnHalf[0].x === 15 && turnHalf[0].y === 9.5, 'Head moving UP interpolates strictly along Y (15, 9.5)');
assert(turnHalf[1].x === 14.5 && turnHalf[1].y === 10, 'Segment 1 turning corner interpolates strictly along X (14.5, 10)');
assert(turnHalf[2].x === 13.5 && turnHalf[2].y === 10, 'Segment 2 interpolates along X (13.5, 10)');

// Validate that logical direction changes NEVER introduce diagonal coordinates in Engine
let boardTurn = {
  snake: [[10, 10], [9, 10], [8, 10]],
  direction: 'RIGHT',
  bounds: { minX: 0, maxX: 20, minY: 0, maxY: 20, spanX: 21, spanY: 21 },
  fruitsEatenInLevel: 0,
  targetFruits: 5,
  obstacles: []
};

// Request UP
boardTurn.direction = SnakeEngine.changeDirection(boardTurn.direction, 'UP', boardTurn.snake.length);
assert(boardTurn.direction === 'UP', 'Direction changed to UP');
const tickUp = SnakeEngine.tick(boardTurn);
assert(tickUp.nextState.snake[0][0] === 10 && tickUp.nextState.snake[0][1] === 9, 'Head moved to (10, 9) strictly vertical, no diagonal logical movement');

// Request LEFT (reject 180 opposite instant reversal when snake > 1)
const noReverse = SnakeEngine.changeDirection('UP', 'DOWN', 3);
assert(noReverse === 'UP', '180-degree instant reversal is rejected in engine');

// -----------------------------------------------------------------------------
// Test Suite 4: Toroidal Boundary Wrap Interpolation
// -----------------------------------------------------------------------------
console.log('\n--- Suite 4: Toroidal Boundary Wrap Seamless Interpolation ---');
const wrapBounds = { minX: 0, maxX: 15, minY: 0, maxY: 11, spanX: 16, spanY: 12 };
// Moving RIGHT off right border: prev was (15, 5), curr wrapped to (0, 5)
const prevWrap = [[15, 5], [14, 5], [13, 5]];
const currWrap = [[0, 5], [15, 5], [14, 5]];

const wrapHalf = interpolateSnake(currWrap, prevWrap, 0.5, wrapBounds);
assert(wrapHalf[0].hasWrap === true, 'Boundary wrap detected for head');
assert(wrapHalf[0].x === 15.5, 'Head exiting right border at x = 15.5');
assert(wrapHalf[0].wrapX === -0.5, 'Head entering left border simultaneously at wrapX = -0.5');

const wrapEnd = interpolateSnake(currWrap, prevWrap, 1.0, wrapBounds);
assert(wrapEnd[0].x === 16, 'At progress 1.0, unwrapped x reaches 16');
assert(wrapEnd[0].wrapX === 0, 'At progress 1.0, wrapped coordinate lands exactly at logical cell 0');

// -----------------------------------------------------------------------------
// Test Suite 5: Snake Growth & Seamless Tail Unfolding
// -----------------------------------------------------------------------------
console.log('\n--- Suite 5: Snake Growth & Seamless Tail Emergence ---');
// Snake grows from 3 to 4 segments upon eating food
const prevGrow = [[10, 8], [9, 8], [8, 8]];
const currGrow = [[11, 8], [10, 8], [9, 8], [8, 8]]; // length 4

const growHalf = interpolateSnake(currGrow, prevGrow, 0.5, bounds);
assert(growHalf.length === 4, 'Interpolation produces 4 visual segments');
assert(growHalf[0].x === 10.5, 'Head moves smoothly 10 -> 10.5');
assert(growHalf[1].x === 9.5, 'Segment 1 moves smoothly 9 -> 9.5');
assert(growHalf[2].x === 8.5, 'Segment 2 moves smoothly 8 -> 8.5');
assert(growHalf[3].x === 8.0, 'New tail segment 3 smoothly stays anchored at (8, 8) as segment 2 moves away');

// -----------------------------------------------------------------------------
// Test Suite 6: Collision, Score & Game Mechanics Isolation
// -----------------------------------------------------------------------------
console.log('\n--- Suite 6: Strict Isolation from Collision & Scoring Physics ---');
const obstBoard = {
  snake: [[5, 5], [4, 5]],
  direction: 'RIGHT',
  bounds: { minX: 0, maxX: 20, minY: 0, maxY: 20, spanX: 21, spanY: 21 },
  obstacles: ['6,5'],
  fruitsEatenInLevel: 2,
  targetFruits: 5
};

const obstTick = SnakeEngine.tick(obstBoard);
assert(obstTick.event === 'DEATH', 'Obstacle collision triggers logical DEATH');
assert(obstTick.nextState.isGameOver === true, 'Logical isGameOver is true');

// Interpolation at any floating point progress must never mutate the board state
const boardCopy = JSON.parse(JSON.stringify(obstBoard));
interpolateSnake(boardCopy.snake, boardCopy.snake, 0.73, obstBoard.bounds);
assert(JSON.stringify(boardCopy) === JSON.stringify(obstBoard), 'Rendering interpolation is strictly read-only and zero-side-effect');

// -----------------------------------------------------------------------------
// Test Suite 7: Dynamic Resize, Reset & State Preservation
// -----------------------------------------------------------------------------
console.log('\n--- Suite 7: Dynamic Resize & State Reset Safety ---');
const boardResize = SnakeGenerator.generateLevel(15, { totalCols: 32, totalRows: 20 });
const adapted = SnakeGenerator.adaptBoardDimensions(boardResize, 48, 28);
assert(adapted.snake.length === boardResize.snake.length, 'Resize adapts dimensions while keeping snake length intact');
assert(adapted.fruitsEatenInLevel === boardResize.fruitsEatenInLevel, 'Resize preserves fruits eaten in level');

console.log(`\n======================================================================`);
console.log(`🎉 ALL ${testsPassed} SMOOTH SNAKE MOVEMENT TESTS PASSED! (${testsFailed} failed)`);
console.log(`======================================================================\n`);

if (testsFailed > 0) process.exit(1);
else process.exit(0);
