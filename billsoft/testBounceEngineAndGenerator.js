/**
 * testBounceEngineAndGenerator.js
 * Comprehensive automated test suite for Nokia Bounce physics engine and procedural level generator.
 * Runs 10,000 deterministic candidate generation validations, physics checks, and replay verification.
 */

const assert = require('assert');
const Engine = require('./src/main/webapp/js/bounce/bounceEngine');
const Generator = require('./src/main/webapp/js/bounce/bounceLevelGenerator');

console.log('--- STARTING BOUNCE ENGINE & GENERATOR VERIFICATION ---');

// 1. Test Player Creation and Configurations
console.log('1. Testing Player Creation & Ball Transformation Configs...');
const p = Engine.createPlayerState(64, 400, 3);
assert.strictEqual(p.x, 64);
assert.strictEqual(p.y, 400);
assert.strictEqual(p.lives, 3);
assert.strictEqual(p.state, 'NORMAL');
assert(Engine.BALL_CONFIGS.NORMAL.radius === 7);
assert(Engine.BALL_CONFIGS.BIG.radius === 11);
assert(Engine.BALL_CONFIGS.SMALL.radius === 4.5);
console.log('✓ Player creation & configs verified.');

// 2. Test Movement, Gravity, and Jumping
console.log('2. Testing Movement, Gravity, and Jumping Physics...');
const testLevel = {
  width: 1000,
  height: 500,
  platforms: [{ x: 0, y: 450, width: 1000, height: 50, type: 'solid' }],
  hoops: [],
  hazards: [],
  waterBodies: [],
  trampolines: [],
  pumps: [],
  checkpoints: []
};

// Tick with gravity
p.y = 100;
p.isGrounded = false;
Engine.tick(testLevel, p, {}, 1 / 60);
assert(p.vy > 0, 'Gravity should accelerate ball downward');

// Move Right
p.x = 100;
p.vx = 0;
Engine.tick(testLevel, p, { right: true }, 1 / 60);
assert(p.vx > 0, 'Right key should apply positive horizontal velocity');

// Land on platform
p.y = 442;
p.vy = 200;
Engine.tick(testLevel, p, {}, 1 / 60);
assert(p.y <= 450 - Engine.BALL_CONFIGS.NORMAL.radius, 'Player should rest atop platform');

// Jump
Engine.tick(testLevel, p, { jump: true }, 1 / 60);
assert(p.vy < 0, 'Jump key should apply negative vertical impulse');
console.log('✓ Movement, gravity & jump physics verified.');

// 3. Test Ball Transformations & Water Buoyancy
console.log('3. Testing Ball Transformations & Water Buoyancy...');
const transformLevel = {
  width: 1000,
  height: 500,
  platforms: [{ x: 0, y: 450, width: 1000, height: 50, type: 'solid' }],
  pumps: [
    { id: 'p_big', x: 200, y: 430, radius: 20, targetState: 'BIG' },
    { id: 'p_sml', x: 400, y: 430, radius: 20, targetState: 'SMALL' }
  ],
  waterBodies: [
    { x: 500, y: 350, width: 300, height: 100 }
  ]
};

// Touch Inflater Pump
p.x = 200;
p.y = 430;
const { events: ev1 } = Engine.tick(transformLevel, p, {}, 1 / 60);
assert.strictEqual(p.state, 'BIG', 'Ball should transform to BIG');
assert(ev1.some(e => e.type === 'TRANSFORM' && e.to === 'BIG'), 'Transform event should be emitted');

// Touch Deflater Pump
p.x = 400;
p.y = 430;
const { events: ev2 } = Engine.tick(transformLevel, p, {}, 1 / 60);
assert.strictEqual(p.state, 'SMALL', 'Ball should transform to SMALL');
assert(ev2.some(e => e.type === 'TRANSFORM' && e.to === 'SMALL'), 'Transform event should be emitted');

// Enter Water: Buoyancy Test
p.x = 600;
p.y = 380;
p.vy = 0;
p.state = 'BIG'; // High buoyancy
Engine.tick(transformLevel, p, {}, 1 / 60);
assert(p.inWater, 'Player should be detected inside water');
assert(p.vy < 0, 'Big ball should experience net upward buoyant force in water');
console.log('✓ Transformations & water buoyancy verified.');

// 4. Test Hoops Collection and Exit Gate Unlocking
console.log('4. Testing Hoops Collection & Exit Unlocking...');
const hoopLevel = {
  width: 1000,
  height: 500,
  exitOpen: false,
  platforms: [{ x: 0, y: 450, width: 1000, height: 50, type: 'solid' }],
  hoops: [
    { id: 'h1', x: 200, y: 430, radius: 24, collected: false },
    { id: 'h2', x: 300, y: 430, radius: 24, collected: false }
  ],
  exit: { x: 800, y: 370, width: 50, height: 80 }
};

// Collect first hoop
p.x = 200;
p.y = 430;
Engine.tick(hoopLevel, p, {}, 1 / 60);
assert(hoopLevel.hoops[0].collected, 'First hoop should be collected');
assert(!hoopLevel.exitOpen, 'Exit should remain locked until all hoops collected');

// Collect second hoop
p.x = 300;
p.y = 430;
const { events: evHoop } = Engine.tick(hoopLevel, p, {}, 1 / 60);
assert(hoopLevel.hoops[1].collected, 'Second hoop should be collected');
assert(hoopLevel.exitOpen, 'Exit should unlock after all hoops collected');
assert(evHoop.some(e => e.type === 'EXIT_UNLOCKED'), 'EXIT_UNLOCKED event should be emitted');

// Enter unlocked exit
p.x = 810;
p.y = 400;
const { events: evExit } = Engine.tick(hoopLevel, p, {}, 1 / 60);
assert(evExit.some(e => e.type === 'LEVEL_COMPLETE'), 'LEVEL_COMPLETE event should be emitted');
console.log('✓ Hoops collection & exit unlocking verified.');

// 5. Test Hazards, Death & Checkpoint Respawning
console.log('5. Testing Hazards, Death & Checkpoints...');
const hazardLevel = {
  width: 1000,
  height: 500,
  platforms: [{ x: 0, y: 450, width: 1000, height: 50, type: 'solid' }],
  checkpoints: [
    { id: 'cp1', x: 150, y: 440, active: false }
  ],
  hazards: [
    { id: 'spk1', x: 400, y: 450, width: 32, height: 24, type: 'spike' }
  ]
};

// Activate Checkpoint
p.x = 150;
p.y = 430;
p.lives = 3;
Engine.tick(hazardLevel, p, {}, 1 / 60);
assert(hazardLevel.checkpoints[0].active, 'Checkpoint should be activated');
assert(p.lastCheckpoint && p.lastCheckpoint.id === 'cp1');

// Hit Spike
p.x = 410;
p.y = 440;
p.invulnerableTimer = 0;
const { events: evDeath } = Engine.tick(hazardLevel, p, {}, 1 / 60);
assert(p.isDead, 'Player should be dead after hitting spike');
assert.strictEqual(p.lives, 2, 'Player should lose 1 life');
assert(evDeath.some(e => e.type === 'DEATH'), 'DEATH event should be emitted');

// Respawn countdown
p.deathTimer = 0;
Engine.tick(hazardLevel, p, {}, 1 / 60);
assert(!p.isDead, 'Player should be respawned');
assert.strictEqual(p.x, 150, 'Player should respawn at last active checkpoint');
console.log('✓ Hazards, death & checkpoint respawn verified.');

// 6. Test Deterministic Procedural Level Generator & 10,000 Batch Validation
console.log('6. Running 10,000 Deterministic Batch Generation Validations...');
const BATCH_SIZE = 10000;
let validatedCount = 0;
let fallbackCount = 0;

const startTime = Date.now();
for (let i = 1; i <= BATCH_SIZE; i++) {
  const seed = (i * 31337 + 7919) >>> 0;
  const levelNum = ((i - 1) % 50) + 1; // Levels 1 through 50
  const lvl = Generator.generateLevel(levelNum, { seed });

  assert(lvl.platforms.length > 0, 'Generated level must contain platforms');
  assert(lvl.hoops.length > 0, 'Generated level must contain hoops');
  assert(lvl.exit, 'Generated level must contain an exit gate');

  // Independent Solvability Validation
  const validation = Generator.validateLevel(lvl);
  assert(validation.valid, `Level ${levelNum} with seed ${seed} must pass validation: ${validation.reason}`);

  if (lvl.generationStatus === 'VALIDATED') {
    validatedCount++;
  } else {
    fallbackCount++;
  }
}
const elapsed = Date.now() - startTime;
console.log(`✓ 10,000 Batch Generations complete in ${elapsed}ms (${(elapsed / BATCH_SIZE).toFixed(2)}ms / level).`);
console.log(`  - Direct Validated: ${validatedCount} / ${BATCH_SIZE} (${((validatedCount / BATCH_SIZE) * 100).toFixed(1)}%)`);
console.log(`  - Fallback Validated: ${fallbackCount} / ${BATCH_SIZE}`);

// 7. Deterministic Replay Verification
console.log('7. Verifying Deterministic Replay...');
const lvlA = Generator.generateLevel(12, { seed: 998877 });
const lvlB = Generator.generateLevel(12, { seed: 998877 });
assert.strictEqual(lvlA.width, lvlB.width);
assert.strictEqual(lvlA.platforms.length, lvlB.platforms.length);
assert.strictEqual(lvlA.hoops.length, lvlB.hoops.length);
assert.strictEqual(JSON.stringify(lvlA), JSON.stringify(lvlB), 'Identical seed and level number must generate 100% identical level geometry');
console.log('✓ Deterministic replay verified.');

console.log('--- ALL BOUNCE ENGINE & GENERATOR TESTS PASSED SUCCESSFULLY! ---');
