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

console.log('[RupeeCRM] Resource staging complete.');
