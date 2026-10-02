/**
 * testDestinationContentAudit.js
 * Comprehensive automated audit suite for Maharashtra Destination Content,
 * Research Evidence, Uniqueness Rules, and Zero Generic Fallbacks.
 */

const assert = require('assert');
const MaharashtraWorld = require('./src/main/webapp/js/maharashtraWorld');
const SnakeSpriteRenderers = require('./src/main/webapp/js/snakeSpriteRenderers');

console.log('======================================================================');
console.log('🔍 STARTING MAHARASHTRA DESTINATION CONTENT & RESEARCH AUDIT');
console.log('======================================================================\n');

let passCount = 0;
let failCount = 0;

function assertCheck(cond, msg) {
  if (!cond) {
    failCount++;
    console.error(`❌ FAILED: ${msg}`);
    throw new Error(msg);
  }
  passCount++;
}

// 1. Dynamic Destination Discovery
const allLocations = MaharashtraWorld.getAllLocations ? MaharashtraWorld.getAllLocations() : [];
const destinations = MaharashtraWorld.getDestinations ? MaharashtraWorld.getDestinations() : allLocations;

assertCheck(Array.isArray(destinations) && destinations.length > 0, `Discovered dynamic destinations (${destinations.length})`);
console.log(`✓ Discovered ${destinations.length} destination sector entries in dynamic world registry.`);

// 2. Audit Every Destination Definition
console.log('\n--- Auditing Destination Content, Research Evidence & Renderer Coverage ---');

const bannedGenericIds = new Set([
  'OBSTACLE_BLOCK', 'DEFAULT_APPLE', 'GENERIC_BLOCK', 'DEFAULT_OBSTACLE', 'GENERIC_CUBE', 'STONE_BLOCK'
]);

const rawIds = new Set();

destinations.forEach((loc, idx) => {
  const locId = loc.id;
  const content = MaharashtraWorld.getDestinationContent ? MaharashtraWorld.getDestinationContent(locId) : null;
  const theme = MaharashtraWorld.getDestinationTheme ? MaharashtraWorld.getDestinationTheme(locId, loc.archetypeId, idx * 15 + 1) : null;

  assertCheck(theme !== null && typeof theme === 'object', `Destination theme exists for ${locId}`);

  // Base raw destination check
  const baseId = locId.includes('_sec') ? locId.split('_sec')[0] : locId;
  rawIds.add(baseId);

  // Check Research Evidence
  const research = (content && content.research) || (theme && theme.research);
  assertCheck(research && typeof research === 'object', `Research block exists for ${locId}`);
  assertCheck(Array.isArray(research.sources) && research.sources.length >= 1, `Research sources exist for ${locId}`);
  research.sources.forEach(src => {
    assertCheck(typeof src.title === 'string' && src.title.length > 0, `Research source has valid title in ${locId}`);
    assertCheck(typeof src.verifiedFact === 'string' && src.verifiedFact.length > 0, `Research source has verifiedFact in ${locId}`);
  });

  // Check Visual Theme
  const vt = (content && content.visualTheme) || (theme && theme.visualTheme) || theme;
  assertCheck(vt.palette && typeof vt.palette === 'object', `Visual theme palette exists for ${locId}`);

  // Check Location Content - Obstacles (>= 3)
  const lc = (content && content.locationContent) || (theme && theme.locationContent) || theme;
  const obstaclesList = lc.obstacles || theme.obstacleTypes || [];
  assertCheck(obstaclesList.length >= 3, `Obstacles count >= 3 for ${locId} (Found: ${obstaclesList.length})`);

  obstaclesList.forEach(obs => {
    const typeId = typeof obs === 'string' ? obs : (obs.typeId || obs.id);
    assertCheck(!bannedGenericIds.has(typeId), `Obstacle '${typeId}' in ${locId} is not a banned generic block`);
    const rendererId = (typeof obs === 'object' && obs.rendererId) ? obs.rendererId : null;
    if (rendererId) {
      assertCheck(typeof SnakeSpriteRenderers.obstacles[rendererId] === 'function', `Renderer '${rendererId}' exists in SnakeSpriteRenderers.obstacles`);
    }
  });

  // Check Location Content - Foods (>= 2)
  const foodsList = lc.foods || (theme.regionalFood ? [{ typeId: theme.regionalFood, name: theme.foodName, icon: theme.foodIcon }] : []);
  assertCheck(foodsList.length >= 2 || (theme.regionalFood && theme.secondaryFood), `Foods count >= 2 for ${locId}`);
  foodsList.forEach(f => {
    const typeId = f.typeId || f;
    assertCheck(!bannedGenericIds.has(typeId), `Food '${typeId}' in ${locId} is not a banned generic fruit`);
    if (f.rendererId) {
      assertCheck(typeof SnakeSpriteRenderers.foods[f.rendererId] === 'function', `Renderer '${f.rendererId}' exists in SnakeSpriteRenderers.foods`);
    }
  });

  // Check Landmark & Quote
  const landmark = lc.landmark || (theme && theme.landmark);
  if (landmark) {
    assertCheck(typeof landmark.heroQuote === 'string' && landmark.heroQuote.length > 0, `Landmark heroQuote exists for ${locId}`);
  }
});

console.log(`✓ Audited all ${destinations.length} destination sectors across ${rawIds.size} unique raw destination identities.`);

// 3. Representative Destinations Explicit Verification
console.log('\n--- Representative 5-Location Content & Research Proof Gate ---');

const rep5 = ['satara_pratapgad', 'sindhudurg_malvan', 'pune_shaniwar_wada', 'sambhajinagar_ajanta', 'satara_wai_ghat'];

rep5.forEach(repId => {
  const content = MaharashtraWorld.getDestinationContent(repId);
  assertCheck(content !== null, `Representative destination '${repId}' resolves successfully`);
  
  const obs = content.locationContent.obstacles;
  assertCheck(obs.length >= 3, `${content.name} has >= 3 obstacles (Found: ${obs.length})`);
  obs.forEach(o => {
    assertCheck(typeof o.researchProof === 'string' && o.researchProof.length > 0, `${content.name} obstacle '${o.name}' has researchProof`);
    assertCheck(typeof SnakeSpriteRenderers.obstacles[o.rendererId] === 'function', `${content.name} obstacle renderer '${o.rendererId}' exists`);
  });

  const foods = content.locationContent.foods;
  assertCheck(foods.length >= 2, `${content.name} has >= 2 foods (Found: ${foods.length})`);
  foods.forEach(f => {
    assertCheck(typeof f.researchProof === 'string' && f.researchProof.length > 0, `${content.name} food '${f.name}' has researchProof`);
    assertCheck(typeof SnakeSpriteRenderers.foods[f.rendererId] === 'function', `${content.name} food renderer '${f.rendererId}' exists`);
  });

  console.log(`  [PASS] ${content.name.padEnd(25)} | Obstacles: ${obs.length} | Foods: ${foods.length} | Sources: ${content.research.sources.length} | Landmark: ✓`);
});

// 4. Persistence & Boundary Safety Checks
console.log('\n--- Journey Sector & Boundary Progression Math ---');
const w15 = MaharashtraWorld.getWorldForLevel('classic', 15);
const w16 = MaharashtraWorld.getWorldForLevel('classic', 16);
assertCheck(w15.location.id !== w16.location.id, `Boundary trigger: Level 15 (${w15.location.name}) -> Level 16 (${w16.location.name})`);
assertCheck(w15.isLocationFinalLevel === true, `Level 15 is marked as isLocationFinalLevel`);
assertCheck(w16.isLocationFinalLevel === false, `Level 16 is start of new sector`);

console.log('\n======================================================================');
console.log(`🎉 ALL ${passCount} DESTINATION CONTENT & RESEARCH AUDIT CHECKS PASSED!`);
console.log('======================================================================');
