/**
 * RupeeCRM Desktop - Resource Stager for Electron Packaging
 *
 * Copies compiled Java Supervisor (launcher.jar) and Backend (rupeecrm.war)
 * into desktop/electron/resources/ ready for electron-builder.
 */

const fs = require('fs');
const path = require('path');

const repoRoot = path.resolve(__dirname, '../../..');
const electronRoot = path.resolve(__dirname, '..');
const resourcesDir = path.join(electronRoot, 'resources');

console.log('[RupeeCRM] Staging application resources for Electron packaging...');

fs.mkdirSync(resourcesDir, { recursive: true });

// 1. Stage launcher.jar
const launcherSrc = path.join(repoRoot, 'launcher', 'target', 'launcher-0.0.1-SNAPSHOT.jar');
const launcherDest = path.join(resourcesDir, 'launcher.jar');
if (fs.existsSync(launcherSrc)) {
  fs.copyFileSync(launcherSrc, launcherDest);
  const stat = fs.statSync(launcherDest);
  console.log(`  -> Staged launcher.jar (${(stat.size / 1024 / 1024).toFixed(2)} MB)`);
} else {
  console.warn(`  [!] Warning: ${launcherSrc} not found. Run './mvnw package -pl launcher' first.`);
}

// 2. Stage rupeecrm.war
const warSrc = path.join(repoRoot, 'billsoft', 'target', 'billsoft-0.0.1-SNAPSHOT.war');
const warDest = path.join(resourcesDir, 'rupeecrm.war');
if (fs.existsSync(warSrc)) {
  fs.copyFileSync(warSrc, warDest);
  const stat = fs.statSync(warDest);
  console.log(`  -> Staged rupeecrm.war (${(stat.size / 1024 / 1024).toFixed(2)} MB)`);
} else {
  console.warn(`  [!] Warning: ${warSrc} not found. Run './mvnw package -pl billsoft' first.`);
}

// 3. Stage vendor PDF.js libraries for print runner
const vendorDir = path.join(electronRoot, 'src', 'vendor');
fs.mkdirSync(vendorDir, { recursive: true });
const pdfMinSrc = path.join(repoRoot, 'billsoft', 'src', 'main', 'webapp', 'js', 'vendor', 'pdf.min.js');
const pdfWorkerSrc = path.join(repoRoot, 'billsoft', 'src', 'main', 'webapp', 'js', 'vendor', 'pdf.worker.min.js');
if (fs.existsSync(pdfMinSrc)) {
  fs.copyFileSync(pdfMinSrc, path.join(vendorDir, 'pdf.min.js'));
  console.log('  -> Staged pdf.min.js for print runner');
}
if (fs.existsSync(pdfWorkerSrc)) {
  fs.copyFileSync(pdfWorkerSrc, path.join(vendorDir, 'pdf.worker.min.js'));
  console.log('  -> Staged pdf.worker.min.js for print runner');
}

// 4. Validate and stage branding assets (prevent default Electron icon fallback)
console.log('  -> Validating application branding assets...');
const assetsDir = path.join(electronRoot, 'assets');
const requiredAssets = [
  { name: 'icon.ico', minSize: 10000, magic: Buffer.from([0x00, 0x00, 0x01, 0x00]) },
  { name: 'icon.png', minSize: 10000, magic: Buffer.from([0x89, 0x50, 0x4e, 0x47]) },
  { name: 'icon.icns', minSize: 50000, magic: Buffer.from([0x69, 0x63, 0x6e, 0x73]) }
];

for (const asset of requiredAssets) {
  const assetPath = path.join(assetsDir, asset.name);
  if (!fs.existsSync(assetPath)) {
    throw new Error(`CRITICAL: Branding asset ${asset.name} is missing in ${assetsDir}! Build halted to prevent default Electron icon fallback.`);
  }
  const stat = fs.statSync(assetPath);
  if (stat.size < asset.minSize) {
    throw new Error(`CRITICAL: Branding asset ${asset.name} size (${stat.size} bytes) is below minimum required (${asset.minSize} bytes)!`);
  }
  const fd = fs.openSync(assetPath, 'r');
  const buffer = Buffer.alloc(asset.magic.length);
  fs.readSync(fd, buffer, 0, asset.magic.length, 0);
  fs.closeSync(fd);
  if (!buffer.equals(asset.magic)) {
    throw new Error(`CRITICAL: Branding asset ${asset.name} has invalid binary header: expected ${asset.magic.toString('hex')}, got ${buffer.toString('hex')}!`);
  }
  console.log(`  ✓ Verified branding asset: ${asset.name} (${(stat.size / 1024).toFixed(1)} KB)`);
}

console.log('[RupeeCRM] Resource staging and branding verification complete.');
