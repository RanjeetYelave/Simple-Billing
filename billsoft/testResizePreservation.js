/**
 * testResizePreservation.js
 * Dedicated Behavioral Test Suite for Dynamic Board Resizing and Coordinate Re-Anchoring.
 * Verifies that logical game state, score, level, streak, snake length, and progression
 * remain strictly invariant across arbitrary container resize events.
 */

const Engine = require('./src/main/webapp/js/snakeEngine');
const Generator = require('./src/main/webapp/js/snakeGenerator');
const Persistence = require('./src/main/webapp/js/snakePersistence');
const assert = require('assert');

console.log('=== RUNNING DYNAMIC RESIZE PRESERVATION & COORDINATE RE-ANCHORING TEST ===\n');

let testsPassed = 0;
function check(cond, msg) {
  assert(cond, msg);
  testsPassed++;
}

// 1. Initial State: Level 15 on 40x25 container
const initialLvl = 15;
const boardInitial = Generator.generateLevel(initialLvl, { totalCols: 40, totalRows: 25 });
check(boardInitial !== null, 'Initial level generation must succeed');
check(boardInitial.totalCols === 40 && boardInitial.totalRows === 25, 'Initial grid must be 40x25');

// Simulate active gameplay: Move snake, eat 3 fruits, gain combo
boardInitial.fruitsEatenInLevel = 3;
boardInitial.comboCount = 3;
boardInitial.direction = 'DOWN';
boardInitial.snake = [
  [boardInitial.bounds.minX + 5, boardInitial.bounds.minY + 6],
  [boardInitial.bounds.minX + 5, boardInitial.bounds.minY + 5],
  [boardInitial.bounds.minX + 4, boardInitial.bounds.minY + 5],
  [boardInitial.bounds.minX + 3, boardInitial.bounds.minY + 5]
];
boardInitial.restartsCount = 0;
boardInitial.chillTimer = 3500;

const stateBeforeResize = JSON.parse(JSON.stringify(boardInitial));

// 2. Expand resize: 40x25 -> 60x35
console.log('--- Test 1: Container Expansion (40x25 -> 60x35) ---');
const boardExpanded = Generator.adaptBoardDimensions(boardInitial, 60, 35);

check(boardExpanded.totalCols === 60 && boardExpanded.totalRows === 35, 'New grid dimensions must be 60x35');
check(boardExpanded.level === initialLvl, 'Level must remain unchanged');
check(boardExpanded.fruitsEatenInLevel === 3, 'Fruits eaten must remain unchanged');
check(boardExpanded.comboCount === 3, 'Combo count must remain unchanged');
check(boardExpanded.direction === 'DOWN', 'Direction must remain unchanged');
check(boardExpanded.chillTimer === 3500, 'Power-up timers must remain unchanged');
check(boardExpanded.snake.length === 4, 'Snake length must be preserved');

// Verify snake body shape integrity (relative offsets between segments preserved)
for (let i = 0; i < boardExpanded.snake.length - 1; i++) {
  const dxOld = stateBeforeResize.snake[i][0] - stateBeforeResize.snake[i + 1][0];
  const dyOld = stateBeforeResize.snake[i][1] - stateBeforeResize.snake[i + 1][1];
  const dxNew = boardExpanded.snake[i][0] - boardExpanded.snake[i + 1][0];
  const dyNew = boardExpanded.snake[i][1] - boardExpanded.snake[i + 1][1];
  check(dxOld === dxNew && dyOld === dyNew, `Segment relative offset ${i} must match`);
}

// Verify all entities remain strictly inside active territory bounds
const expBounds = boardExpanded.bounds;
for (let s of boardExpanded.snake) {
  check(s[0] >= expBounds.minX && s[0] <= expBounds.maxX, 'Snake segment X within expanded bounds');
  check(s[1] >= expBounds.minY && s[1] <= expBounds.maxY, 'Snake segment Y within expanded bounds');
}
check(boardExpanded.food.x >= expBounds.minX && boardExpanded.food.x <= expBounds.maxX, 'Food X within expanded bounds');
check(boardExpanded.food.y >= expBounds.minY && boardExpanded.food.y <= expBounds.maxY, 'Food Y within expanded bounds');

// 3. Compact resize: 60x35 -> 22x14
console.log('--- Test 2: Container Contraction (60x35 -> 22x14) ---');
const boardContracted = Generator.adaptBoardDimensions(boardExpanded, 22, 14);

check(boardContracted.totalCols === 22 && boardContracted.totalRows === 14, 'New grid dimensions must be 22x14');
check(boardContracted.level === initialLvl, 'Level preserved on contraction');
check(boardContracted.fruitsEatenInLevel === 3, 'Score preserved on contraction');
check(boardContracted.snake.length === 4, 'Snake length preserved on contraction');

const conBounds = boardContracted.bounds;
for (let s of boardContracted.snake) {
  check(s[0] >= conBounds.minX && s[0] <= conBounds.maxX, 'Snake segment X clamped inside contracted bounds');
  check(s[1] >= conBounds.minY && s[1] <= conBounds.maxY, 'Snake segment Y clamped inside contracted bounds');
}
check(boardContracted.food.x >= conBounds.minX && boardContracted.food.x <= conBounds.maxX, 'Food X clamped inside contracted bounds');
check(boardContracted.food.y >= conBounds.minY && boardContracted.food.y <= conBounds.maxY, 'Food Y clamped inside contracted bounds');

// 4. Continued Gameplay: Tick after resize
console.log('--- Test 3: Gameplay Continues Smoothly After Resize ---');
const tickResult = Engine.tick(boardContracted);
check(tickResult.event === 'MOVE' || tickResult.event === 'GROW', 'Game loop ticks normally after resize');
check(tickResult.nextState.isGameOver !== true, 'Resize did not cause accidental collision or game over');

console.log(`\n========================================`);
console.log(`ALL ${testsPassed} DYNAMIC RESIZE TESTS PASSED SUCCESSFULLY!`);
console.log(`========================================\n`);
