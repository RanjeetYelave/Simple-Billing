/**
 * RupeeCRM Desktop - Authoritative Resource Stager & Version Validator
 *
 * Copies compiled Java Supervisor (launcher.jar) and Backend (rupeecrm.war)
 * into desktop/electron/resources/ ready for electron-builder.
 *
 * ENFORCES:
 * 1. Existence and minimum sizes of rupeecrm.war and launcher.jar.
 * 2. Extraction and inspection of embedded application.properties from rupeecrm.war.
 * 3. Exact version agreement between package.json and the embedded backend WAR.
 * 4. SHA-256 calculation and generation of version-report.json.
 * 5. Branding asset binary signature validation.
 */

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const zlib = require('zlib');
const child_process = require('child_process');
const os = require('os');

const repoRoot = path.resolve(__dirname, '../../..');
const electronRoot = path.resolve(__dirname, '..');
const resourcesDir = path.join(electronRoot, 'resources');

console.log('═══════════════════════════════════════════════════════════════════');
console.log('[RupeeCRM] Staging Application Resources & Validating Version Integrity');
console.log('═══════════════════════════════════════════════════════════════════\n');

fs.mkdirSync(resourcesDir, { recursive: true });

// Load target Electron package version
const pkgPath = path.join(electronRoot, 'package.json');
if (!fs.existsSync(pkgPath)) {
  throw new Error(`CRITICAL: package.json missing at ${pkgPath}!`);
}
const pkg = JSON.parse(fs.readFileSync(pkgPath, 'utf8'));
const expectedVersion = (pkg.version || '').trim();
if (!expectedVersion) {
  throw new Error('CRITICAL: package.json does not define a valid version string!');
}
console.log(`Authoritative Electron Package Version: ${expectedVersion}`);

// 1. Stage and validate launcher.jar
const launcherSrc = path.join(repoRoot, 'launcher', 'target', 'launcher-0.0.1-SNAPSHOT.jar');
const launcherDest = path.join(resourcesDir, 'launcher.jar');
if (fs.existsSync(launcherSrc)) {
  fs.copyFileSync(launcherSrc, launcherDest);
  const stat = fs.statSync(launcherDest);
  console.log(`  -> Staged freshly compiled launcher.jar (${(stat.size / 1024).toFixed(1)} KB)`);
} else if (fs.existsSync(launcherDest)) {
  console.log('  -> Using existing staged launcher.jar');
} else {
  throw new Error(`CRITICAL: launcher.jar not found at ${launcherSrc} or ${launcherDest}! Run './mvnw package -pl launcher' first.`);
}

const launcherStat = fs.statSync(launcherDest);
if (launcherStat.size < 50000) {
  throw new Error(`CRITICAL: launcher.jar size (${launcherStat.size} bytes) is suspiciously small!`);
}
const launcherSha256 = crypto.createHash('sha256').update(fs.readFileSync(launcherDest)).digest('hex');
console.log(`  ✓ Verified launcher.jar (${(launcherStat.size / 1024).toFixed(1)} KB, SHA-256: ${launcherSha256.substring(0, 16)}...)`);

// 2. Stage and validate rupeecrm.war
const warSrc = path.join(repoRoot, 'billsoft', 'target', 'billsoft-0.0.1-SNAPSHOT.war');
const warDest = path.join(resourcesDir, 'rupeecrm.war');
if (fs.existsSync(warSrc)) {
  fs.copyFileSync(warSrc, warDest);
  const stat = fs.statSync(warDest);
  console.log(`  -> Staged freshly compiled rupeecrm.war (${(stat.size / 1024 / 1024).toFixed(2)} MB)`);
} else if (fs.existsSync(warDest)) {
  console.log('  -> Using existing staged rupeecrm.war');
} else {
  throw new Error(`CRITICAL: rupeecrm.war not found at ${warSrc} or ${warDest}! Run './mvnw package -pl billsoft' first.`);
}

const warStat = fs.statSync(warDest);
if (warStat.size < 50000000) {
  throw new Error(`CRITICAL: rupeecrm.war size (${warStat.size} bytes) is below minimum expected 50 MB!`);
}

// 3. Inspect actual embedded application.properties from rupeecrm.war
function extractWarVersion(warPath) {
  // Strategy 1: unzip -p (fast, standard Unix/macOS)
  try {
    const out = child_process.execSync(`unzip -p "${warPath}" WEB-INF/classes/application.properties`, {
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'ignore'],
      timeout: 5000
    });
    const match = out.match(/app\.version=(.+)/);
    if (match) return match[1].trim().replace(/^[vV]/, '');
  } catch (_) {}

  // Strategy 2: PowerShell (Windows native)
  if (process.platform === 'win32') {
    try {
      const psCmd = `powershell -NoProfile -NonInteractive -Command "Add-Type -AssemblyName System.IO.Compression.FileSystem; $zip = [System.IO.Compression.ZipFile]::OpenRead('${warPath.replace(/'/g, "''")}'); $entry = $zip.GetEntry('WEB-INF/classes/application.properties'); if ($entry) { $reader = New-Object System.IO.StreamReader($entry.Open()); $content = $reader.ReadToEnd(); $reader.Close(); $zip.Dispose(); if ($content -match 'app\\.version=(.+)') { $matches[1].Trim() } }"`;
      const out = child_process.execSync(psCmd, { encoding: 'utf8', timeout: 5000 }).trim();
      if (out) return out.replace(/^[vV]/, '');
    } catch (_) {}
  }

  // Strategy 3: Pure Node.js Central Directory scanner + zlib (zero dependencies, cross-platform)
  try {
    const fd = fs.openSync(warPath, 'r');
    const stat = fs.fstatSync(fd);
    const bufferSize = Math.min(stat.size, 65536);
    const buffer = Buffer.alloc(bufferSize);
    fs.readSync(fd, buffer, 0, bufferSize, stat.size - bufferSize);

    let eocdOffset = -1;
    for (let i = bufferSize - 22; i >= 0; i--) {
      if (buffer.readUInt32LE(i) === 0x06054b50) {
        eocdOffset = i;
        break;
      }
    }
    if (eocdOffset !== -1) {
      const cdOffset = buffer.readUInt32LE(eocdOffset + 16);
      const cdSize = buffer.readUInt32LE(eocdOffset + 12);
      const cdBuffer = Buffer.alloc(cdSize);
      fs.readSync(fd, cdBuffer, 0, cdSize, cdOffset);

      let pos = 0;
      while (pos + 46 <= cdSize) {
        if (cdBuffer.readUInt32LE(pos) !== 0x02014b50) break;
        const compMethod = cdBuffer.readUInt16LE(pos + 10);
        const compSize = cdBuffer.readUInt32LE(pos + 20);
        const nameLen = cdBuffer.readUInt16LE(pos + 28);
        const extraLen = cdBuffer.readUInt16LE(pos + 30);
        const commentLen = cdBuffer.readUInt16LE(pos + 32);
        const localHeaderOffset = cdBuffer.readUInt32LE(pos + 42);
        const fileName = cdBuffer.toString('utf8', pos + 46, pos + 46 + nameLen);

        if (fileName === 'WEB-INF/classes/application.properties') {
          const localHeader = Buffer.alloc(30);
          fs.readSync(fd, localHeader, 0, 30, localHeaderOffset);
          const localNameLen = localHeader.readUInt16LE(26);
          const localExtraLen = localHeader.readUInt16LE(28);
          const dataOffset = localHeaderOffset + 30 + localNameLen + localExtraLen;

          const compressedData = Buffer.alloc(compSize);
          fs.readSync(fd, compressedData, 0, compSize, dataOffset);
          fs.closeSync(fd);

          const decompressed = compMethod === 8
            ? zlib.inflateRawSync(compressedData).toString('utf8')
            : compressedData.toString('utf8');
          const match = decompressed.match(/app\.version=(.+)/);
          if (match) return match[1].trim().replace(/^[vV]/, '');
          break;
        }
        pos += 46 + nameLen + extraLen + commentLen;
      }
    }
    fs.closeSync(fd);
  } catch (_) {}

  return null;
}

const embeddedWarVersion = extractWarVersion(warDest);
console.log(`  -> Embedded WAR Version: ${embeddedWarVersion || 'UNKNOWN'}`);

if (!embeddedWarVersion) {
  throw new Error(`CRITICAL: Failed to extract app.version from WEB-INF/classes/application.properties inside ${warDest}!`);
}

const cleanPkg = expectedVersion.replace(/^[vV]/, '');
const cleanWar = embeddedWarVersion.replace(/^[vV]/, '');

if (cleanPkg !== cleanWar) {
  throw new Error(`CRITICAL VERSION MISMATCH REJECTED:
  - Electron package.json version: ${cleanPkg}
  - Embedded backend WAR version:  ${cleanWar}
  The packaged backend WAR does NOT match the desktop application version!
  Packaging aborted to prevent deploying a split-version application.`);
}

console.log(`  ✓ Version agreement verified: Electron (v${cleanPkg}) === Backend WAR (v${cleanWar})`);

// Calculate SHA-256
const warSha256 = crypto.createHash('sha256').update(fs.readFileSync(warDest)).digest('hex');
console.log(`  ✓ Verified rupeecrm.war (${(warStat.size / 1024 / 1024).toFixed(2)} MB, SHA-256: ${warSha256.substring(0, 16)}...)`);

// 4. Generate Authoritative Release Artifact Verification Report
const reportPath = path.join(resourcesDir, 'version-report.json');
const versionReport = {
  appVersion: cleanPkg,
  warVersion: cleanWar,
  warSizeBytes: warStat.size,
  warSha256: warSha256,
  launcherSizeBytes: launcherStat.size,
  launcherSha256: launcherSha256,
  verifiedAt: new Date().toISOString(),
  platform: process.platform
};
fs.writeFileSync(reportPath, JSON.stringify(versionReport, null, 2), 'utf8');
console.log(`  ✓ Generated release artifact verification report at resources/version-report.json`);

// 5. Stage vendor PDF.js libraries for print runner
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

// 6. Validate and stage branding assets
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
    throw new Error(`CRITICAL: Branding asset ${asset.name} is missing in ${assetsDir}! Build halted.`);
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
    throw new Error(`CRITICAL: Branding asset ${asset.name} has invalid binary header!`);
  }
  console.log(`  ✓ Verified branding asset: ${asset.name} (${(stat.size / 1024).toFixed(1)} KB)`);
}

console.log('\n[RupeeCRM] Resource staging and version verification complete successfully.');
