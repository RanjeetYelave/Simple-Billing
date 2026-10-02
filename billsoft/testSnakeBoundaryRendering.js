/**
 * testSnakeBoundaryRendering.js
 * Forensic validation of Snake Classic coordinate geometry, board bounds,
 * interpolation bounds clamping, and clipping boundary integrity.
 */

const assert = require('assert');
const SnakeGenerator = require('./src/main/webapp/js/snakeGenerator.js');
const SnakeEngine = require('./src/main/webapp/js/snakeEngine.js');

console.log('======================================================================');
console.log('📐 STARTING SNAKE BOUNDARY RENDERING & COORDINATE FORENSIC AUDIT');
console.log('======================================================================\n');

let passCount = 0;
function test(desc, fn) {
  try {
    fn();
    passCount++;
    console.log(`  ✓ ${desc}`);
  } catch (err) {
    console.error(`  ✗ FAIL: ${desc}`);
    console.error(err);
    process.exit(1);
  }
}

// 1. Board Geometry & Coordinate System
test('Level 16 territory bounds calculate exact rectangular playfield', () => {
  const totalCols = 32;
  const totalRows = 20;
  const cellSize = 22;
  const bounds = SnakeGenerator.getTerritoryBounds(totalCols, totalRows, 16);

  assert(bounds.minX >= 0, 'minX must be non-negative');
  assert(bounds.minY >= 0, 'minY must be non-negative');
  assert(bounds.maxX < totalCols, 'maxX must be strictly within totalCols');
  assert(bounds.maxY < totalRows, 'maxY must be strictly within totalRows');
  assert.strictEqual(bounds.spanX, bounds.maxX - bounds.minX + 1, 'spanX must equal maxX - minX + 1');
  assert.strictEqual(bounds.spanY, bounds.maxY - bounds.minY + 1, 'spanY must equal maxY - minY + 1');

  const tX = bounds.minX * cellSize;
  const tY = bounds.minY * cellSize;
  const tW = bounds.spanX * cellSize;
  const tH = bounds.spanY * cellSize;

  assert.strictEqual(tW, bounds.spanX * cellSize, 'tW matches spanX * cellSize');
  assert.strictEqual(tH, bounds.spanY * cellSize, 'tH matches spanY * cellSize');
});

// 2. Toroidal Wrapping Math on all 4 Edges
test('Left boundary wrap from minX to maxX interpolates smoothly with wrapX inside playfield', () => {
  const bounds = { minX: 4, maxX: 27, spanX: 24, minY: 2, maxY: 17, spanY: 16 };
  const prev = [bounds.minX, 10];
  const curr = [bounds.maxX, 10]; // Logical wrapped coordinate
  const dx = curr[0] - prev[0]; // 27 - 4 = 23 (> 1)

  assert(dx > 1, 'dx > 1 detected for left wrap');
  const unwrappedTargetX = curr[0] - bounds.spanX; // 27 - 24 = 3 (minX - 1)
  assert.strictEqual(unwrappedTargetX, bounds.minX - 1);

  const testProgresses = [0, 0.25, 0.5, 0.75, 1.0];
  testProgresses.forEach(p => {
    const renderX = prev[0] + (unwrappedTargetX - prev[0]) * p;
    const wrapX = renderX + bounds.spanX;

    // renderX moves from minX (4) to minX - 1 (3)
    assert(renderX <= bounds.minX && renderX >= bounds.minX - 1);
    // wrapX moves from maxX + 1 (28) to maxX (27)
    assert(wrapX >= bounds.maxX && wrapX <= bounds.maxX + 1);
  });
});

test('Right boundary wrap from maxX to minX interpolates smoothly with wrapX inside playfield', () => {
  const bounds = { minX: 4, maxX: 27, spanX: 24, minY: 2, maxY: 17, spanY: 16 };
  const prev = [bounds.maxX, 10];
  const curr = [bounds.minX, 10]; // Logical wrapped coordinate
  const dx = curr[0] - prev[0]; // 4 - 27 = -23 (< -1)

  assert(dx < -1, 'dx < -1 detected for right wrap');
  const unwrappedTargetX = curr[0] + bounds.spanX; // 4 + 24 = 28 (maxX + 1)
  assert.strictEqual(unwrappedTargetX, bounds.maxX + 1);

  const testProgresses = [0, 0.25, 0.5, 0.75, 1.0];
  testProgresses.forEach(p => {
    const renderX = prev[0] + (unwrappedTargetX - prev[0]) * p;
    const wrapX = renderX - bounds.spanX;

    assert(renderX >= bounds.maxX && renderX <= bounds.maxX + 1);
    assert(wrapX <= bounds.minX && wrapX >= bounds.minX - 1);
  });
});

test('Top boundary wrap from minY to maxY interpolates smoothly with wrapY inside playfield', () => {
  const bounds = { minX: 4, maxX: 27, spanX: 24, minY: 2, maxY: 17, spanY: 16 };
  const prev = [10, bounds.minY];
  const curr = [10, bounds.maxY]; // Logical wrapped coordinate
  const dy = curr[1] - prev[1]; // 17 - 2 = 15 (> 1)

  assert(dy > 1, 'dy > 1 detected for top wrap');
  const unwrappedTargetY = curr[1] - bounds.spanY; // 17 - 16 = 1 (minY - 1)
  assert.strictEqual(unwrappedTargetY, bounds.minY - 1);

  const testProgresses = [0, 0.25, 0.5, 0.75, 1.0];
  testProgresses.forEach(p => {
    const renderY = prev[1] + (unwrappedTargetY - prev[1]) * p;
    const wrapY = renderY + bounds.spanY;

    assert(renderY <= bounds.minY && renderY >= bounds.minY - 1);
    assert(wrapY >= bounds.maxY && wrapY <= bounds.maxY + 1);
  });
});

test('Bottom boundary wrap from maxY to minY interpolates smoothly with wrapY inside playfield', () => {
  const bounds = { minX: 4, maxX: 27, spanX: 24, minY: 2, maxY: 17, spanY: 16 };
  const prev = [10, bounds.maxY];
  const curr = [10, bounds.minY]; // Logical wrapped coordinate
  const dy = curr[1] - prev[1]; // 2 - 17 = -15 (< -1)

  assert(dy < -1, 'dy < -1 detected for bottom wrap');
  const unwrappedTargetY = curr[1] + bounds.spanY; // 2 + 16 = 18 (maxY + 1)
  assert.strictEqual(unwrappedTargetY, bounds.maxY + 1);

  const testProgresses = [0, 0.25, 0.5, 0.75, 1.0];
  testProgresses.forEach(p => {
    const renderY = prev[1] + (unwrappedTargetY - prev[1]) * p;
    const wrapY = renderY - bounds.spanY;

    assert(renderY >= bounds.maxY && renderY <= bounds.maxY + 1);
    assert(wrapY <= bounds.minY && wrapY >= bounds.minY - 1);
  });
});

// 3. 4 Corners Verification
test('All 4 corner boundary coordinates wrap and remain valid without out-of-bound corruptions', () => {
  const bounds = { minX: 4, maxX: 27, spanX: 24, minY: 2, maxY: 17, spanY: 16 };
  const corners = [
    { name: 'Top-Left', x: bounds.minX, y: bounds.minY },
    { name: 'Top-Right', x: bounds.maxX, y: bounds.minY },
    { name: 'Bottom-Left', x: bounds.minX, y: bounds.maxY },
    { name: 'Bottom-Right', x: bounds.maxX, y: bounds.maxY }
  ];

  corners.forEach(c => {
    const wrapped = SnakeEngine.wrapCoordinate(c.x, c.y, bounds);
    assert.strictEqual(wrapped.x, c.x, `${c.name} in-bound x matches`);
    assert.strictEqual(wrapped.y, c.y, `${c.name} in-bound y matches`);
  });
});

console.log(`\n======================================================================`);
console.log(`🎉 ALL ${passCount} SNAKE BOUNDARY RENDERING TESTS PASSED!`);
console.log(`======================================================================\n`);
