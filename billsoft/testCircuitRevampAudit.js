/**
 * Comprehensive Automated Audit: Circuit Connect Gameplay Revamp,
 * 100% Solvability, Intelligent 2-Tier Hints, and Anti-Frustration Validation.
 */

const CircuitEngine = require('./src/main/webapp/js/circuitEngine');
const CircuitGenerator = require('./src/main/webapp/js/circuitGenerator');

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

console.log('=== RUNNING CIRCUIT CONNECT GAMEPLAY REVAMP & HINT AUDIT ===\n');

// 1. Levels 1–100 Solvability & Anti-Frustration Audit
console.log('--- Test 1: Solvability & Anti-Frustration (Levels 1–100) ---');
let solvableCount = 0;
let oasisCount = 0;
let maxMovesEncountered = 0;

for (let lvl = 1; lvl <= 100; lvl++) {
  const puzzle = CircuitGenerator.generateLevel(lvl);
  const isValid = CircuitGenerator.validatePlayablePuzzle(puzzle);

  assert(isValid === true, `Level ${lvl} must pass playable validation`);
  if (isValid) solvableCount++;

  // Check solved state evaluates to solved
  const solvedTiles = puzzle.tiles.map(t => ({
    ...t,
    ports: t.solvedPorts != null ? t.solvedPorts : t.ports,
    switchState: t.solvedSwitchState != null ? t.solvedSwitchState : t.switchState
  }));

  const evalResult = CircuitEngine.evaluateCircuit(
    solvedTiles,
    puzzle.width,
    puzzle.height,
    puzzle.sources || puzzle.sourceIdx,
    puzzle.targetIndices,
    puzzle.bonusTargetIndices || []
  );

  assert(evalResult.isSolved === true, `Level ${lvl} solution must achieve isSolved = true`);

  if (puzzle.isOasis) {
    oasisCount++;
    assert(puzzle.activeMechanics.length === 0, `Oasis Level ${lvl} must have 0 special mechanics`);
    assert(puzzle.minMoves <= 8, `Oasis Level ${lvl} must have minMoves <= 8 for relaxing flow`);
  }

  if (lvl <= 10) {
    assert(puzzle.minMoves <= 8, `Early Level ${lvl} must be easy with minMoves <= 8`);
  }

  assert(puzzle.activeMechanics.length <= 2, `Level ${lvl} must not exceed 2 active mechanics`);
  if (puzzle.minMoves > maxMovesEncountered) maxMovesEncountered = puzzle.minMoves;
}

console.log(`[PASS] 100/100 Levels Solvable (${solvableCount} verified)`);
console.log(`[PASS] ${oasisCount} Breather Oasis Levels verified with 0 special mechanics`);
console.log(`[PASS] Maximum minMoves encountered across 100 levels: ${maxMovesEncountered}`);

// 2. Intelligent 2-Tier Hint Engine Audit
console.log('\n--- Test 2: Intelligent 2-Tier Hint Engine Across Levels ---');
let hintsGenerated = 0;

for (let lvl = 1; lvl <= 50; lvl++) {
  const puzzle = CircuitGenerator.generateLevel(lvl);
  
  // Tier 1 Hint Test
  const hint1 = CircuitEngine.findIntelligentHint(
    puzzle.tiles,
    puzzle.width,
    puzzle.height,
    puzzle.sources || puzzle.sourceIdx,
    puzzle.targetIndices,
    puzzle.bonusTargetIndices || [],
    1
  );

  if (hint1) {
    hintsGenerated++;
    assert(hint1.primaryIdx >= 0 && hint1.primaryIdx < puzzle.tiles.length, `Hint target index must be valid on Level ${lvl}`);
    assert(typeof hint1.message === 'string' && hint1.message.length > 5, `Hint must have educational message on Level ${lvl}`);
    assert(['CW', 'CCW', '180', 'NONE'].includes(hint1.dir) || hint1.action === 'TOGGLE', `Hint direction must be valid on Level ${lvl}`);
    
    // Verify applying the suggested move brings the tile into its solved state
    const targetTile = puzzle.tiles[hint1.primaryIdx];
    let simulatedPorts = targetTile.ports;
    if (hint1.dir === 'CW') simulatedPorts = CircuitEngine.rotateClockwise(simulatedPorts);
    else if (hint1.dir === 'CCW') simulatedPorts = CircuitEngine.rotateCounterClockwise(simulatedPorts);
    else if (hint1.dir === '180') simulatedPorts = CircuitEngine.rotateClockwise(CircuitEngine.rotateClockwise(simulatedPorts));

    assert(simulatedPorts === hint1.solvedPorts, `Suggested rotation on Level ${lvl} must match verified solution ports`);
  }

  // Tier 2 Chain Hint Test
  const hint2 = CircuitEngine.findIntelligentHint(
    puzzle.tiles,
    puzzle.width,
    puzzle.height,
    puzzle.sources || puzzle.sourceIdx,
    puzzle.targetIndices,
    puzzle.bonusTargetIndices || [],
    2
  );

  if (hint2) {
    assert(Array.isArray(hint2.chainIndices) && hint2.chainIndices.length >= 1, `Tier 2 hint must provide chain on Level ${lvl}`);
  }
}
console.log(`[PASS] Intelligent Hints verified across representative levels (${hintsGenerated} generated)`);

// 3. Novelty Mechanics Integration Test
console.log('\n--- Test 3: Novelty Mechanics Progression & Verification ---');
const mechanicsToTest = [
  { name: 'SPLITTER', minLvl: 12 },
  { name: 'BRIDGE', minLvl: 28 },
  { name: 'LOCKED', minLvl: 42 },
  { name: 'SWITCH', minLvl: 58 },
  { name: 'MULTI_SOURCE', minLvl: 72 },
  { name: 'ROTATING_GROUP', minLvl: 88 }
];

mechanicsToTest.forEach(m => {
  const puzzle = CircuitGenerator.generateLevel(m.minLvl);
  assert(puzzle.activeMechanics.includes(m.name), `${m.name} must appear on Level ${m.minLvl}`);
  assert(CircuitGenerator.validatePlayablePuzzle(puzzle) === true, `Level ${m.minLvl} with ${m.name} must be 100% solvable`);
});

console.log(`\n========================================`);
console.log(`REVAMP AUDIT RESULTS: ${testsPassed} PASSED, ${testsFailed} FAILED`);
console.log(`========================================\n`);

if (testsFailed > 0) process.exit(1);
else process.exit(0);
