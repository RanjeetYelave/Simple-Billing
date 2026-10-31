/**
 * RupeeCRM Desktop - OpenJDK 21 Windows JRE Downloader
 *
 * Downloads and extracts the official Eclipse Temurin 21 JRE for Windows x64
 * into desktop/electron/resources/jre for standalone packaging.
 */

const fs = require('fs');
const path = require('path');
const https = require('https');
const child_process = require('child_process');

const electronRoot = path.resolve(__dirname, '..');
const jreDest = path.join(electronRoot, 'resources', 'jre');

async function downloadJre() {
  if (fs.existsSync(path.join(jreDest, 'bin', 'javaw.exe'))) {
    console.log('[RupeeCRM JRE] Bundled Windows JRE 21 already present at:', jreDest);
    return;
  }

  console.log('[RupeeCRM JRE] Windows JRE 21 not detected in resources/jre.');
  console.log('[RupeeCRM JRE] Fetching official Eclipse Temurin 21 Windows x64 JRE...');

  fs.mkdirSync(path.join(electronRoot, 'resources'), { recursive: true });
  const archivePath = path.join(electronRoot, 'resources', 'temurin-21-jre-win64.zip');

  const jreUrl = 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jre/hotspot/normal/eclipse';

  function fetchUrl(url, destStream, resolve, reject) {
    https.get(url, (res) => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        return fetchUrl(res.headers.location, destStream, resolve, reject);
      }
      if (res.statusCode !== 200) {
        return reject(new Error(`HTTP ${res.statusCode} from ${url}`));
      }
      res.pipe(destStream);
      destStream.on('finish', () => destStream.close(resolve));
    }).on('error', reject);
  }

  const fileStream = fs.createWriteStream(archivePath);
  await new Promise((resolve, reject) => fetchUrl(jreUrl, fileStream, resolve, reject));
  console.log('  -> Downloaded JRE zip archive.');

  console.log('  -> Extracting JRE archive...');
  const extractTemp = path.join(electronRoot, 'resources', 'jre-temp');
  fs.mkdirSync(extractTemp, { recursive: true });

  child_process.execSync(`unzip -q -o "${archivePath}" -d "${extractTemp}"`);

  // Locate the root jre directory inside extracted archive
  const entries = fs.readdirSync(extractTemp);
  const rootEntry = entries.find(e => fs.existsSync(path.join(extractTemp, e, 'bin')));
  if (rootEntry) {
    fs.rmSync(jreDest, { recursive: true, force: true });
    fs.renameSync(path.join(extractTemp, rootEntry), jreDest);
  } else {
    fs.renameSync(extractTemp, jreDest);
  }

  fs.rmSync(extractTemp, { recursive: true, force: true });
  fs.rmSync(archivePath, { force: true });

  console.log('[RupeeCRM JRE] Windows JRE 21 successfully staged at:', jreDest);
}

if (require.main === module) {
  downloadJre().catch(err => {
    console.error('[RupeeCRM JRE] Error downloading Windows JRE:', err);
    process.exit(1);
  });
}

module.exports = { downloadJre };
