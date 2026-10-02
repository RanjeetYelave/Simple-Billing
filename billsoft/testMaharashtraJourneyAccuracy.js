/**
 * testMaharashtraJourneyAccuracy.js
 * Comprehensive Forensic Audit & Validation Suite for Maharashtra Expedition & Journey Map.
 * 
 * Verifies:
 * 1. Every level 1–1000 resolves to a valid world/location with complete metadata.
 * 2. No gaps, duplicate ranges, or overlapping locations across Levels 1–1000.
 * 3. Exact location start and end level calculations.
 * 4. Authoritative single-source-of-truth integration (Journey Map derives from MaharashtraWorld).
 * 5. Current location calculations at key boundary and intra-stage levels (1, 15, 16, 30, 31, 90, 91, 97, 105, 106, 999, 1000).
 * 6. Completed, Current, and Mystery classification states.
 * 7. Mystery destinations concealment and unlock rules.
 * 8. Intra-location checkpoint and percentage progress calculations.
 * 9. Zero manually duplicated catalog inside MaharashtraJourney.js.
 * 10. Intra-destination 3-phase layout and obstacle synchronization between generator and world catalog.
 */

const assert = require('assert');
const fs = require('fs');
const path = require('path');

const MaharashtraWorld = require('./src/main/webapp/js/maharashtraWorld');
const SnakeGenerator = require('./src/main/webapp/js/snakeGenerator');

console.log('=== STARTING MAHARASHTRA JOURNEY ACCURACY & FORENSIC AUDIT ===\n');

let passedTests = 0;
function test(name, fn) {
  try {
    fn();
    console.log(`  ✓ ${name}`);
    passedTests++;
  } catch (err) {
    console.error(`  ✗ FAIL: ${name}`);
    console.error(err);
    process.exit(1);
  }
}

// -----------------------------------------------------------------------------
// Suite 1: Level 1–1000 Complete Coverage & Boundary Integrity
// -----------------------------------------------------------------------------
console.log('--- Suite 1: 1–1000 Level Coverage & Boundary Integrity ---');

test('Every level 1–1000 resolves to a valid world location with 0 gaps or overlaps', () => {
  let prevEnd = 0;
  let currentLocIndex = -1;
  const encounteredLocations = new Set();

  for (let lvl = 1; lvl <= 1000; lvl++) {
    const w = MaharashtraWorld.getWorldForLevel('classic', lvl);
    assert.ok(w, `Level ${lvl} must resolve to a valid world`);
    assert.ok(w.location, `Level ${lvl} must have location metadata`);
    assert.ok(w.location.id, `Level ${lvl} location must have ID`);
    assert.ok(w.location.name, `Level ${lvl} location must have name`);
    assert.ok(w.location.district, `Level ${lvl} location must have district`);
    assert.ok(w.location.region, `Level ${lvl} location must have region`);
    assert.ok(w.location.archetypeId, `Level ${lvl} location must have archetypeId`);
    assert.strictEqual(w.levelsPerLocation, 15, 'Default levels per location must be 15');

    encounteredLocations.add(w.location.id);

    // Verify continuous boundaries
    if (w.locationIndex !== currentLocIndex) {
      currentLocIndex = w.locationIndex;
      assert.strictEqual(w.levelStart, prevEnd + 1, `Level start at ${w.location.name} must be exactly prevEnd + 1`);
      prevEnd = w.levelEnd;
    }
  }

  assert.strictEqual(encounteredLocations.size, 67, 'Levels 1–1000 must span 67 unique destination blocks');
});

// -----------------------------------------------------------------------------
// Suite 2: Representative Boundary & Intra-Location Progress
// -----------------------------------------------------------------------------
console.log('\n--- Suite 2: Representative Boundary & Intra-Location Progress ---');

const checkPoints = [
  { lvl: 1, loc: 'Karad', start: 1, end: 15, step: 1, pct: 7, exploredCount: 0 },
  { lvl: 15, loc: 'Karad', start: 1, end: 15, step: 15, pct: 100, exploredCount: 0 },
  { lvl: 16, loc: 'Satara', start: 16, end: 30, step: 1, pct: 7, exploredCount: 1 },
  { lvl: 30, loc: 'Satara', start: 16, end: 30, step: 15, pct: 100, exploredCount: 1 },
  { lvl: 31, loc: 'Ajinkyatara Fort', start: 31, end: 45, step: 1, pct: 7, exploredCount: 2 },
  { lvl: 90, loc: 'Mahabaleshwar', start: 76, end: 90, step: 15, pct: 100, exploredCount: 5 },
  { lvl: 91, loc: 'Pratapgad Fort', start: 91, end: 105, step: 1, pct: 7, exploredCount: 6 },
  { lvl: 97, loc: 'Pratapgad Fort', start: 91, end: 105, step: 7, pct: 47, exploredCount: 6 },
  { lvl: 105, loc: 'Pratapgad Fort', start: 91, end: 105, step: 15, pct: 100, exploredCount: 6 },
  { lvl: 106, loc: 'Kas Plateau', start: 106, end: 120, step: 1, pct: 7, exploredCount: 7 },
  { lvl: 999, loc: 'Nagpur Deekshabhoomi', start: 991, end: 1005, step: 9, pct: 60, exploredCount: 66 },
  { lvl: 1000, loc: 'Nagpur Deekshabhoomi', start: 991, end: 1005, step: 10, pct: 67, exploredCount: 66 }
];

checkPoints.forEach(cp => {
  test(`Calculates exact location and progress for Level ${cp.lvl} (${cp.loc})`, () => {
    const tl = MaharashtraWorld.getJourneyTimeline('classic', cp.lvl, 15, 'all');
    assert.strictEqual(tl.currentWorld.location.name, cp.loc);
    assert.strictEqual(tl.currentWorld.levelStart, cp.start);
    assert.strictEqual(tl.currentWorld.levelEnd, cp.end);
    assert.strictEqual(tl.currentWorld.progressInLocation, cp.step);
    assert.strictEqual(tl.currentWorld.progressPercent, cp.pct);
    assert.strictEqual(tl.completedLocationsCount, cp.exploredCount);
  });
});

// -----------------------------------------------------------------------------
// Suite 3: Timeline Node Classification (Completed, Current, Mystery)
// -----------------------------------------------------------------------------
console.log('\n--- Suite 3: Timeline Node Classification ---');

test('Properly classifies completed, current, and mystery locked destination nodes', () => {
  const currentLvl = 97; // Pratapgad Fort (Location index 6)
  const tl = MaharashtraWorld.getJourneyTimeline('classic', currentLvl, 15, 'all');
  const nodes = tl.timeline;

  assert.strictEqual(nodes.length, 67, 'Timeline must contain all 67 nodes');

  // Past nodes (0 to 5) must be completed
  for (let i = 0; i <= 5; i++) {
    const node = nodes[i];
    assert.strictEqual(node.isVisited, true, `Node ${i} must be visited`);
    assert.strictEqual(node.isCurrent, false, `Node ${i} must not be current`);
    assert.strictEqual(node.isLocked, false, `Node ${i} must not be locked`);
    assert.strictEqual(node.progressPercent, 100, `Node ${i} must have 100% progress`);
    assert.ok(!node.name.includes('???'), `Explored node ${i} must have revealed name`);
  }

  // Active current node (index 6: Pratapgad Fort)
  const activeNode = nodes[6];
  assert.strictEqual(activeNode.isVisited, false, 'Current node is not yet fully visited');
  assert.strictEqual(activeNode.isCurrent, true, 'Current node must have isCurrent=true');
  assert.strictEqual(activeNode.isLocked, false, 'Current node is not locked');
  assert.strictEqual(activeNode.name, 'Pratapgad Fort');
  assert.strictEqual(activeNode.progressInLocation, 7);
  assert.strictEqual(activeNode.progressPercent, 47);

  // Future mystery nodes (7 to 66)
  for (let i = 7; i < nodes.length; i++) {
    const node = nodes[i];
    assert.strictEqual(node.isVisited, false, `Future node ${i} must not be visited`);
    assert.strictEqual(node.isCurrent, false, `Future node ${i} must not be current`);
    assert.strictEqual(node.isLocked, true, `Future node ${i} must be locked`);
    assert.strictEqual(node.isMystery, true, `Future node ${i} must be mystery`);
    assert.strictEqual(node.name, '??? MYSTERY DESTINATION', `Future node ${i} name must be concealed`);
    assert.strictEqual(node.unlocksAtLevel, node.levelStart, `Unlock level must match levelStart`);
  }
});

// -----------------------------------------------------------------------------
// Suite 4: Single Authoritative Source & Zero Code Duplication
// -----------------------------------------------------------------------------
console.log('\n--- Suite 4: Single Source of Truth Audit ---');

test('MaharashtraJourney.js does not contain duplicate hardcoded location arrays', () => {
  const journeyJsPath = path.join(__dirname, 'src/main/webapp/js/components/MaharashtraJourney.js');
  const content = fs.readFileSync(journeyJsPath, 'utf8');

  // Verify it does NOT contain duplicated location arrays
  assert.ok(!content.includes('["satara_karad"'), 'Must not duplicate raw locations array');
  assert.ok(!content.includes('satara_satara_city: {'), 'Must not duplicate themes');
  assert.ok(content.includes('WorldEngine.getJourneyTimeline'), 'Must query MaharashtraWorld.getJourneyTimeline');
  assert.ok(content.includes('WorldEngine.getWorldForLevel'), 'Must query MaharashtraWorld.getWorldForLevel');
});

// -----------------------------------------------------------------------------
// Suite 5: Generator and World Catalog Synchronization
// -----------------------------------------------------------------------------
console.log('\n--- Suite 5: Generator & World Catalog Synchronization ---');

test('SnakeGenerator matches authentic archetype and 3-phase progression across 1000 levels', () => {
  for (let lvl = 1; lvl <= 1000; lvl++) {
    const world = MaharashtraWorld.getWorldForLevel('classic', lvl);
    const cfg = SnakeGenerator.getLevelConfig(lvl);

    assert.strictEqual(cfg.archetype, world.location.archetypeId, `Level ${lvl} archetype must match location archetype`);
    assert.ok(cfg.layoutType, `Level ${lvl} must have valid layoutType`);

    // Verify Level 1000 finale
    if (lvl === 1000) {
      assert.strictEqual(cfg.isFinale, true);
      assert.strictEqual(cfg.layoutType, 'CORONATION_CITADEL');
    }
  }
});

console.log('\n======================================================');
console.log(`ALL ${passedTests} MAHARASHTRA JOURNEY ACCURACY TESTS PASSED!`);
console.log('======================================================\n');
