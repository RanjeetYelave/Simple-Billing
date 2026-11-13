/**
 * RupeeCRM Desktop Branding Assets and Packaging Verification Suite
 */

const fs = require('fs');
const path = require('path');
const assert = require('assert');

console.log('═══════════════════════════════════════════════════════════════════');
console.log('RUPEECRM BRANDING ASSETS & ELECTRON PACKAGING VERIFICATION SUITE');
console.log('═══════════════════════════════════════════════════════════════════');

const electronDir = path.resolve(__dirname, '..');
const repoRoot = path.resolve(electronDir, '..', '..');

const pngPath = path.join(electronDir, 'assets', 'icon.png');
const icoPath = path.join(electronDir, 'assets', 'icon.ico');
const icnsPath = path.join(electronDir, 'assets', 'icon.icns');

const builderConfigPath = path.join(electronDir, 'electron-builder.yml');
const prepareScriptPath = path.join(electronDir, 'scripts', 'prepare-resources.js');
const mainPath = path.join(electronDir, 'src', 'main.js');

// 1. Verify Master High-Resolution PNG
console.log('\n▶ [Check 1/6] Verifying Master PNG Asset (icon.png)...');
assert(fs.existsSync(pngPath), 'assets/icon.png must exist');
const pngBuffer = fs.readFileSync(pngPath);
assert(pngBuffer.length > 100000, `icon.png must be >100KB, got ${pngBuffer.length} bytes`);
// Check PNG signature: 89 50 4E 47 0D 0A 1A 0A
assert.strictEqual(pngBuffer.subarray(0, 8).toString('hex'), '89504e470d0a1a0a', 'icon.png must have valid PNG signature');
// Read IHDR chunk (starts at byte 12): Length (4 bytes), Type "IHDR" (4 bytes), Width (4 bytes BE), Height (4 bytes BE)
assert.strictEqual(pngBuffer.subarray(12, 16).toString('ascii'), 'IHDR', 'IHDR chunk missing in icon.png');
const pngWidth = pngBuffer.readUInt32BE(16);
const pngHeight = pngBuffer.readUInt32BE(20);
const bitDepth = pngBuffer.readUInt8(24);
const colorType = pngBuffer.readUInt8(25);
console.log(`  -> Dimensions: ${pngWidth}x${pngHeight}, Bit Depth: ${bitDepth}, Color Type: ${colorType}, Size: ${pngBuffer.length} bytes`);
assert.strictEqual(pngWidth, 1024, 'Master icon.png width must be 1024px');
assert.strictEqual(pngHeight, 1024, 'Master icon.png height must be 1024px');
assert.strictEqual(bitDepth, 8, 'Bit depth must be 8');
assert.strictEqual(colorType, 6, 'Color type must be 6 (RGBA)');
console.log('  ✔ Master PNG asset is valid 1024x1024 RGBA.');

// 2. Verify Apple ICNS Asset
console.log('\n▶ [Check 2/6] Verifying Apple ICNS Asset (icon.icns)...');
assert(fs.existsSync(icnsPath), 'assets/icon.icns must exist');
const icnsBuffer = fs.readFileSync(icnsPath);
assert(icnsBuffer.length > 500000, `icon.icns must be >500KB with Retina assets, got ${icnsBuffer.length} bytes`);
// Check magic 'icns'
assert.strictEqual(icnsBuffer.subarray(0, 4).toString('ascii'), 'icns', 'icon.icns must start with "icns" magic bytes');
const totalIcnsBytes = icnsBuffer.readUInt32BE(4);
assert.strictEqual(totalIcnsBytes, icnsBuffer.length, 'ICNS declared length must match actual file size');

// Scan chunks inside ICNS
const icnsTypes = [];
let offset = 8;
while (offset < icnsBuffer.length) {
  const chunkType = icnsBuffer.subarray(offset, offset + 4).toString('ascii');
  const chunkSize = icnsBuffer.readUInt32BE(offset + 4);
  icnsTypes.push({ type: chunkType, size: chunkSize });
  offset += chunkSize;
}
console.log(`  -> Detected ICNS chunk types: ${icnsTypes.map(c => c.type).join(', ')}`);
// Must contain ic10 (512x512@2x / 1024x1024 Retina representation) or ic09 (512x512)
const hasRetina1024 = icnsTypes.some(c => c.type === 'ic10');
const has512 = icnsTypes.some(c => c.type === 'ic09' || c.type === 'ic10');
assert(hasRetina1024, 'ICNS must contain ic10 (1024x1024 Retina) representation for macOS Apple Silicon / Retina displays');
assert(has512, 'ICNS must contain 512x512 representation');
console.log('  ✔ Apple ICNS asset is complete with full Retina resolution support.');

// 3. Verify Windows ICO Asset
console.log('\n▶ [Check 3/6] Verifying Windows ICO Asset (icon.ico)...');
assert(fs.existsSync(icoPath), 'assets/icon.ico must exist');
const icoBuffer = fs.readFileSync(icoPath);
assert(icoBuffer.length > 30000, `icon.ico must be >30KB, got ${icoBuffer.length} bytes`);
// Check ICO header: 00 00 01 00
assert.strictEqual(icoBuffer.subarray(0, 4).toString('hex'), '00000100', 'icon.ico must have valid ICO header');
const icoImageCount = icoBuffer.readUInt16LE(4);
console.log(`  -> Contained ICO icon resolutions count: ${icoImageCount}`);
assert(icoImageCount >= 5, `icon.ico must contain at least 5 standard sizes, found ${icoImageCount}`);

// Inspect icon entries (16 bytes each starting at offset 6)
const icoSizes = [];
for (let i = 0; i < icoImageCount; i++) {
  const entryOffset = 6 + (i * 16);
  let w = icoBuffer.readUInt8(entryOffset);
  let h = icoBuffer.readUInt8(entryOffset + 1);
  if (w === 0) w = 256;
  if (h === 0) h = 256;
  icoSizes.push(`${w}x${h}`);
}
console.log(`  -> Available ICO sizes: ${icoSizes.join(', ')}`);
assert(icoSizes.includes('256x256'), 'ICO must contain 256x256 high-resolution icon');
assert(icoSizes.includes('48x48') || icoSizes.includes('32x32'), 'ICO must contain standard desktop sizes');
console.log('  ✔ Windows ICO asset is multi-resolution and includes 256x256.');

// 4. Verify electron-builder.yml Configuration
console.log('\n▶ [Check 4/6] Verifying electron-builder.yml branding configuration...');
const builderYaml = fs.readFileSync(builderConfigPath, 'utf8').replace(/\r\n/g, '\n');
// mac block
assert(/mac:\s*\n\s*icon:\s*assets\/icon\.icns/.test(builderYaml) || builderYaml.includes('icon: assets/icon.icns'),
  'electron-builder.yml must specify mac.icon: assets/icon.icns');
assert(builderYaml.includes('category: public.app-category.business'), 'mac category must be public.app-category.business');
// win block
assert(/win:[\s\S]*?icon:\s*assets\/icon\.ico/.test(builderYaml),
  'electron-builder.yml must specify win.icon: assets/icon.ico');
// nsis block
assert(builderYaml.includes('installerIcon: assets/icon.ico'), 'NSIS must specify installerIcon: assets/icon.ico');
assert(builderYaml.includes('uninstallerIcon: assets/icon.ico'), 'NSIS must specify uninstallerIcon: assets/icon.ico');
assert(builderYaml.includes('installerHeaderIcon: assets/icon.ico'), 'NSIS must specify installerHeaderIcon: assets/icon.ico');
console.log('  ✔ electron-builder.yml correctly configures macOS and Windows icon paths.');

// 5. Verify Build Preparation Script Validation
console.log('\n▶ [Check 5/6] Verifying prepare-resources.js branding validation...');
const prepareScript = fs.readFileSync(prepareScriptPath, 'utf8');
assert(prepareScript.includes("Validating application branding assets"), 'prepare-resources.js must validate branding assets');
assert(prepareScript.includes('icon.icns'), 'prepare-resources.js must validate icon.icns');
assert(prepareScript.includes('icon.ico'), 'prepare-resources.js must validate icon.ico');
assert(prepareScript.includes('icon.png'), 'prepare-resources.js must validate icon.png');
console.log('  ✔ prepare-resources.js enforces mandatory branding asset validation before packaging.');

// 6. Verify main.js Runtime Dock & Window Icon Setup
console.log('\n▶ [Check 6/6] Verifying main.js runtime Dock and Window branding logic...');
const mainSrc = fs.readFileSync(mainPath, 'utf8');
assert(mainSrc.includes("nativeImage"), 'main.js must define or import nativeImage');
assert(mainSrc.includes("if (process.platform === 'darwin' && app.dock)"),
  'main.js must guard app.dock for macOS');
assert(mainSrc.includes("app.dock.setIcon"),
  'main.js must explicitly call app.dock.setIcon() for macOS Dock and App Switcher');
assert(mainSrc.includes("function resolveAssetPath(assetName)"),
  'main.js must implement robust resolveAssetPath');
assert(mainSrc.includes("BrowserWindow({"), 'BrowserWindow must be created');
assert(mainSrc.includes("icon:"), 'BrowserWindow options must include icon');
console.log('  ✔ main.js correctly configures macOS Dock icon and BrowserWindow window icons.');

console.log('\n═══════════════════════════════════════════════════════════════════');
console.log('ALL 6/6 BRANDING VERIFICATION CHECKS PASSED SUCCESSFULLY!');
console.log('═══════════════════════════════════════════════════════════════════\n');
