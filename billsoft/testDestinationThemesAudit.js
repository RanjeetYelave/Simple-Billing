/**
 * testDestinationThemesAudit.js
 * Comprehensive visual world audit for Snake Classic - Maharashtra Expedition.
 * Validates:
 * 1. Complete destination theme coverage across all 68 raw locations and expanded sectors.
 * 2. Visual distinctiveness: Pratapgad vs Kas vs Kolhapur vs Murud-Janjira vs Raigad.
 * 3. Theme completeness (palette, skyStyle, horizonStyle, frameStyle, groundStyle, snakeSkin, foodVisual, obstacleStyle).
 * 4. Deterministic theme resolution across Levels 1 through 1000.
 */

const assert = require('assert');
const MaharashtraWorld = require('./src/main/webapp/js/maharashtraWorld');

console.log('=== STARTING MAHARASHTRA WORLD DESTINATION THEMES AUDIT ===\n');

// --- Test 1: All Locations Have Valid Themes ---
console.log('--- Test Suite 1: Theme Completeness & Coverage ---');
const allLocations = MaharashtraWorld.getAllLocations();
assert(allLocations.length >= 1024, `Expected at least 1024 locations, got ${allLocations.length}`);

const requiredPaletteKeys = [
  'skyTop', 'skyBottom', 'frameBorder', 'frameAccent', 'groundBase',
  'snakeHead', 'snakeBodyPrimary', 'foodPrimary'
];

allLocations.forEach((loc, idx) => {
  const world = MaharashtraWorld.getWorldForLevel('classic', idx + 1);
  assert(world.theme, `Location ${loc.id} at index ${idx} must resolve a theme`);
  assert(world.theme.palette, `Theme for ${loc.id} must contain palette`);
  
  requiredPaletteKeys.forEach(k => {
    assert(world.theme.palette[k], `Theme palette for ${loc.id} missing ${k}`);
  });

  assert(world.theme.skyStyle, `Theme for ${loc.id} missing skyStyle`);
  assert(world.theme.horizonStyle, `Theme for ${loc.id} missing horizonStyle`);
  assert(world.theme.frameStyle, `Theme for ${loc.id} missing frameStyle`);
  assert(world.theme.groundStyle, `Theme for ${loc.id} missing groundStyle`);
  assert(world.theme.snakeSkin, `Theme for ${loc.id} missing snakeSkin`);
  assert(world.theme.foodVisual, `Theme for ${loc.id} missing foodVisual`);
  assert(world.theme.obstacleStyle, `Theme for ${loc.id} missing obstacleStyle`);
});
console.log(`  ✓ All ${allLocations.length} locations have complete, valid visual themes`);

// --- Test 2: Visual Distinctiveness of Iconic Locations ---
console.log('\n--- Test Suite 2: Visual Distinctiveness Between Key Landmarks ---');
const pratapgadTheme = MaharashtraWorld.getDestinationTheme('satara_pratapgad', 'ARCH_HILL_FORT', 95);
const kasTheme = MaharashtraWorld.getDestinationTheme('satara_kas_plateau', 'ARCH_PLATEAU_FOREST', 110);
const kolhapurTheme = MaharashtraWorld.getDestinationTheme('kolhapur_mahalakshmi', 'ARCH_PILGRIMAGE_GHAT', 125);
const murudTheme = MaharashtraWorld.getDestinationTheme('raigad_murud_janjira', 'ARCH_COASTAL_FORT', 305);
const wadaTheme = MaharashtraWorld.getDestinationTheme('pune_shaniwar_wada', 'ARCH_HISTORIC_WADA', 380);
const elloraTheme = MaharashtraWorld.getDestinationTheme('sambhajinagar_ellora', 'ARCH_CAVE_TEMPLE', 460);
const coronationTheme = MaharashtraWorld.getDestinationTheme('raigad_capital_fort', 'ARCH_HILL_FORT', 1000);

// Pratapgad: Basalt Fort, Misty Sahyadri, Basalt Cobra, Sahyadri Gem
assert.strictEqual(pratapgadTheme.frameStyle, 'sahyadri_battlement');
assert.strictEqual(pratapgadTheme.groundStyle, 'basalt_flagstone');
assert.strictEqual(pratapgadTheme.snakeSkin, 'basalt_cobra');

// Kas: Wildflower, Forest Bough, Emerald Viper, Forest Spirit
assert.strictEqual(kasTheme.frameStyle, 'forest_bough');
assert.strictEqual(kasTheme.groundStyle, 'rocky_plateau');
assert.strictEqual(kasTheme.snakeSkin, 'emerald_viper');
assert.strictEqual(kasTheme.foodVisual, 'forest_spirit');

// Kolhapur: Temple Ghat Tier, Vermilion, Temple Saffron Snake, Diya Lamp
assert.strictEqual(kolhapurTheme.frameStyle, 'temple_ghat_tier');
assert.strictEqual(kolhapurTheme.skyStyle, 'temple_vermilion');
assert.strictEqual(kolhapurTheme.snakeSkin, 'temple_saffron');
assert.strictEqual(kolhapurTheme.foodVisual, 'prasaad_lamp');

// Murud-Janjira: Konkan Laterite, Coastal Sand, Coastal Azure Snake, Konkan Pearl
assert.strictEqual(murudTheme.frameStyle, 'konkan_laterite');
assert.strictEqual(murudTheme.groundStyle, 'coastal_sand');
assert.strictEqual(murudTheme.snakeSkin, 'coastal_azure');
assert.strictEqual(murudTheme.foodVisual, 'konkan_pearl');

// Shaniwar Wada: Wada Teak, Terracotta, Wada Teak Snake, Wada Token
assert.strictEqual(wadaTheme.frameStyle, 'wada_teak');
assert.strictEqual(wadaTheme.groundStyle, 'wada_terracotta');
assert.strictEqual(wadaTheme.snakeSkin, 'wada_teak');
assert.strictEqual(wadaTheme.foodVisual, 'wada_token');

// Ellora: Chaitya Arch, Cave Monolith, Cave Carved Stone, Cave Naga, Cave Relic
assert.strictEqual(elloraTheme.frameStyle, 'chaitya_arch');
assert.strictEqual(elloraTheme.groundStyle, 'cave_carved_stone');
assert.strictEqual(elloraTheme.snakeSkin, 'cave_naga');
assert.strictEqual(elloraTheme.foodVisual, 'cave_relic');

// Level 1000 Coronation Finale: Coronation Imperial Frame, Dais, Dragon Snake, Sovereign Crest
assert.strictEqual(coronationTheme.frameStyle, 'coronation_imperial');
assert.strictEqual(coronationTheme.groundStyle, 'coronation_dais');
assert.strictEqual(coronationTheme.snakeSkin, 'coronation_dragon');
assert.strictEqual(coronationTheme.foodVisual, 'coronation_crest');

console.log('  ✓ Key landmark destinations have distinctly authored, non-generic identities');

// --- Test 3: 1 to 1000 Level Range Theme Resolution ---
console.log('\n--- Test Suite 3: 1 to 1000 Continuous Journey Verification ---');
for (let lvl = 1; lvl <= 1000; lvl += 15) {
  const w = MaharashtraWorld.getWorldForLevel('classic', lvl);
  assert(w && w.theme, `Level ${lvl} must have world theme`);
  assert(w.location && w.location.name, `Level ${lvl} must have location name`);
}
console.log('  ✓ Successfully sampled and verified world themes across Levels 1–1000');

console.log('\n========================================');
console.log('ALL DESTINATION THEME AUDIT TESTS PASSED!');
console.log('========================================\n');
