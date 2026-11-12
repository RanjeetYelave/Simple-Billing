/**
 * RupeeCRM Desktop - Electron Main Process
 *
 * Responsibilities:
 * - Native application window, tray, and taskbar identity
 * - Single-instance enforcement and secondary invocation focusing
 * - Bundled Java Supervisor lifecycle and health readiness polling
 * - Graceful coordinated shutdown preventing orphaned Java processes
 * - Context-isolated, sandbox-compliant security architecture
 * - Seamless integration with existing Spring Boot backend & in-app updater
 */

const electron = require('electron');
const isElectron = typeof electron === 'object' && electron !== null && !!electron.app;

const app = isElectron ? electron.app : {
  isPackaged: false,
  getVersion: () => {
    try {
      const p = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'package.json'), 'utf8'));
      return p.version || '1.0.308';
    } catch (e) {
      return '1.0.308';
    }
  },
  getPath: (name) => path.join(process.env.LOCALAPPDATA || process.env.HOME || '.', '.rupeecrm'),
  requestSingleInstanceLock: () => true,
  setAppUserModelId: () => { },
  getLoginItemSettings: () => ({ openAtLogin: false }),
  setLoginItemSettings: () => { },
  quit: () => { },
  on: () => { },
  whenReady: () => new Promise(() => { })
};
const BrowserWindow = isElectron ? electron.BrowserWindow : class { };
const Tray = isElectron ? electron.Tray : class { };
const Menu = isElectron ? electron.Menu : class { static buildFromTemplate() { return {}; } static setApplicationMenu() { } };
const ipcMain = isElectron ? electron.ipcMain : { handle: () => { }, on: () => { } };
const shell = isElectron ? electron.shell : { openPath: () => { }, openExternal: () => { } };
const dialog = isElectron ? electron.dialog : { showSaveDialogSync: () => null };
const session = isElectron ? electron.session : null;

const path = require('path');
const http = require('http');
const net = require('net');
const fs = require('fs');
const { pathToFileURL } = require('url');
const child_process = require('child_process');

// =========================================================================
// 1. Single Instance Lock
// =========================================================================
const gotTheLock = app.requestSingleInstanceLock();
if (!gotTheLock) {
  console.log('[RupeeCRM] Another desktop instance is already running. Exiting secondary launcher.');
  app.quit();
  process.exit(0);
}

// App Configuration & Constants
const PORT = parseInt(process.env.RUPEECRM_PORT || '28080', 10);
const APP_URL = `http://localhost:${PORT}/`;
const HEALTH_URL = `http://127.0.0.1:${PORT}/api/health`;
const IDENTITY_URL = `http://127.0.0.1:${PORT}/api/system/runtime-identity`;
const SHUTDOWN_URL = `http://127.0.0.1:${PORT}/api/shutdown`;

// Global State
let mainWindow = null;
let splashWindow = null;
let diagnosticsWindow = null;
let tray = null;
let supervisorProcess = null;
let isQuitting = false;
let startHidden = false;
let backendHealthy = false;
let hasShownTrayNotice = false;
let recentLogs = [];
let legacyMigrationDetected = false;
let legacyMigrationDetails = [];
let activeBackendIdentity = null;
let unidentifiedProcessConflict = null;

function logMessage(msg) {
  const line = `[${new Date().toLocaleTimeString()}] ${msg}`;
  console.log(line);
  recentLogs.push(line);
  if (recentLogs.length > 50) recentLogs.shift();
}

// Inspect Launch Arguments
for (const arg of process.argv) {
  if (arg === '--hidden' || arg === '--background' || arg === '-b') {
    startHidden = true;
  }
}

// Set Windows AppUserModelId for proper taskbar grouping & notifications
if (process.platform === 'win32') {
  app.setAppUserModelId('com.rupeecrm.desktop');
}

// =========================================================================
// 2. Path Resolution (Authoritative Local Storage & Binaries)
// =========================================================================
function resolveDirectories() {
  let dataDir, logsDir, backupsDir;

  if (process.platform === 'win32') {
    const localAppData = process.env.LOCALAPPDATA || path.join(process.env.USERPROFILE || 'C:\\', 'AppData', 'Local');
    const root = path.join(localAppData, 'RupeeCRM');
    dataDir = path.join(root, 'data');
    logsDir = path.join(root, 'logs');
    backupsDir = path.join(root, 'backups');
  } else {
    const userData = app.getPath('userData');
    dataDir = path.join(userData, 'data');
    logsDir = path.join(userData, 'logs');
    backupsDir = path.join(userData, 'backups');
  }

  try {
    fs.mkdirSync(dataDir, { recursive: true });
    fs.mkdirSync(logsDir, { recursive: true });
    fs.mkdirSync(backupsDir, { recursive: true });
  } catch (e) {
    console.warn('[RupeeCRM] Could not ensure directory structure:', e.message);
  }

  return { dataDir, logsDir, backupsDir };
}

const dirs = resolveDirectories();

function resolveAssetPath(assetName) {
  return path.join(__dirname, '..', 'assets', assetName);
}

function resolveJavaRuntime() {
  if (app.isPackaged) {
    const jreExe = process.platform === 'win32'
      ? path.join(process.resourcesPath, 'jre', 'bin', 'javaw.exe')
      : path.join(process.resourcesPath, 'jre', 'bin', 'java');
    if (fs.existsSync(jreExe)) return jreExe;

    const jreExeFallback = process.platform === 'win32'
      ? path.join(process.resourcesPath, 'jre', 'bin', 'java.exe')
      : jreExe;
    if (fs.existsSync(jreExeFallback)) return jreExeFallback;
  }

  // Development environment fallbacks
  if (process.env.JAVA_HOME) {
    const devJava = path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'javaw.exe' : 'java');
    if (fs.existsSync(devJava)) return devJava;
  }
  if (process.platform === 'darwin') {
    try {
      const jh = child_process.execSync('/usr/libexec/java_home', { encoding: 'utf8' }).trim();
      if (jh && fs.existsSync(path.join(jh, 'bin', 'java'))) {
        return path.join(jh, 'bin', 'java');
      }
    } catch (e) {}
  }
  return process.platform === 'win32' ? 'javaw' : 'java';
}

function resolveLauncherJar() {
  if (app.isPackaged) {
    const jar = path.join(process.resourcesPath, 'launcher.jar');
    if (fs.existsSync(jar)) return jar;
  }

  // Check workspace built target
  const candidate = path.resolve(__dirname, '../../../launcher/target/launcher-0.0.1-SNAPSHOT.jar');
  if (fs.existsSync(candidate)) return candidate;

  return null;
}

// =========================================================================
// 3. Backend Health, Lifecycle & Supervisor Management
// =========================================================================
function checkBackendHealth(timeoutMs = 1500) {
  return new Promise((resolve) => {
    const req = http.get(HEALTH_URL, { timeout: timeoutMs }, (res) => {
      resolve(res.statusCode === 200);
    });
    req.on('error', () => resolve(false));
    req.on('timeout', () => {
      req.destroy();
      resolve(false);
    });
  });
}

function isPortListening(port, timeoutMs = 400) {
  return new Promise((resolve) => {
    const socket = new net.Socket();
    let settled = false;

    const cleanup = (status) => {
      if (!settled) {
        settled = true;
        socket.destroy();
        resolve(status);
      }
    };

    socket.setTimeout(timeoutMs);
    socket.once('connect', () => cleanup(true));
    socket.once('timeout', () => cleanup(false));
    socket.once('error', () => cleanup(false));

    socket.connect(port, '127.0.0.1');
  });
}

function inspectListeningProcess(port) {
  return new Promise((resolve) => {
    if (process.platform === 'win32') {
      const psCmd = `powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "$c = Get-NetTCPConnection -LocalPort ${port} -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1; if ($c) { $p = Get-CimInstance Win32_Process -Filter \\"ProcessId = $($c.OwningProcess)\\" -ErrorAction SilentlyContinue; if ($p) { [PSCustomObject]@{ pid = $p.ProcessId; name = $p.Name; path = $p.ExecutablePath; cmd = $p.CommandLine } | ConvertTo-Json -Compress } else { [PSCustomObject]@{ pid = $c.OwningProcess; name = ''; path = ''; cmd = '' } | ConvertTo-Json -Compress } }"`;
      child_process.exec(psCmd, { timeout: 3500 }, (err, stdout) => {
        if (err || !stdout || !stdout.trim()) {
          try {
            const netstatOut = child_process.execSync('netstat -ano -p tcp', { encoding: 'utf8', timeout: 2000 });
            const line = netstatOut.split(/\r?\n/).find(l => l.includes(`:${port}`) && l.includes('LISTENING'));
            if (line) {
              const parts = line.trim().split(/\s+/);
              const pid = parseInt(parts[parts.length - 1], 10);
              if (!isNaN(pid)) {
                return resolve({ pid, name: 'unknown', executablePath: '', commandLine: '', isRupeeCRM: false });
              }
            }
          } catch (ignored) { }
          return resolve({ pid: null, name: '', executablePath: '', commandLine: '', isRupeeCRM: false });
        }
        try {
          const parsed = JSON.parse(stdout.trim());
          const cmd = (parsed.cmd || '').toLowerCase();
          const pPath = (parsed.path || '').toLowerCase();
          const pName = (parsed.name || '').toLowerCase();
          const isRupeeCRM = cmd.includes('rupeecrm') || cmd.includes('billsoft') || cmd.includes('launcher.jar') ||
            pPath.includes('rupeecrm') || pPath.includes('simple-billing') || pName.includes('rupeecrm');
          resolve({
            pid: parsed.pid || null,
            name: parsed.name || '',
            executablePath: parsed.path || '',
            commandLine: parsed.cmd || '',
            isRupeeCRM
          });
        } catch (e) {
          resolve({ pid: null, name: '', executablePath: '', commandLine: '', isRupeeCRM: false });
        }
      });
    } else {
      // macOS / Linux
      child_process.exec(`lsof -nP -iTCP:${port} -sTCP:LISTEN -t`, { timeout: 2500 }, (err, stdout) => {
        if (err || !stdout || !stdout.trim()) {
          return resolve({ pid: null, name: '', executablePath: '', commandLine: '', isRupeeCRM: false });
        }
        const pid = parseInt(stdout.trim().split(/\s+/)[0], 10);
        if (isNaN(pid)) {
          return resolve({ pid: null, name: '', executablePath: '', commandLine: '', isRupeeCRM: false });
        }
        child_process.exec(`ps -p ${pid} -o comm=,args=`, { timeout: 2000 }, (err2, stdout2) => {
          const raw = stdout2 ? stdout2.trim() : '';
          const lower = raw.toLowerCase();
          const isRupeeCRM = lower.includes('rupeecrm') || lower.includes('billsoft') || lower.includes('launcher') || lower.includes('simple-billing');
          resolve({
            pid,
            name: raw.split(/\s+/)[0] || '',
            executablePath: raw.split(/\s+/)[0] || '',
            commandLine: raw,
            isRupeeCRM
          });
        });
      });
    }
  });
}

function getBackendRuntimeIdentity(timeoutMs = 1500) {
  return new Promise((resolve) => {
    // 1. First query authoritative /api/system/runtime-identity endpoint
    const req = http.get(IDENTITY_URL, { timeout: timeoutMs }, (res) => {
      if (res.statusCode === 200) {
        let data = '';
        res.on('data', (chunk) => { data += chunk; });
        res.on('end', () => {
          try {
            const json = JSON.parse(data);
            return resolve({
              version: json.version || null,
              commit: json.commit || null,
              buildTimestamp: json.buildTimestamp || null,
              pid: json.pid || null,
              warPath: json.warPath || null,
              service: json.service || 'RupeeCRM',
              authoritative: true
            });
          } catch (e) {
            // fallback to /api/health
          }
        });
        return;
      }

      // 2. Fallback to /api/health
      queryHealthFallback(resolve, timeoutMs);
    });

    req.on('error', () => queryHealthFallback(resolve, timeoutMs));
    req.on('timeout', () => {
      req.destroy();
      resolve(null);
    });
  });
}

function queryHealthFallback(resolve, timeoutMs) {
  const req = http.get(HEALTH_URL, { timeout: timeoutMs }, (res) => {
    if (res.statusCode !== 200) return resolve(null);
    let data = '';
    res.on('data', (chunk) => { data += chunk; });
    res.on('end', () => {
      try {
        const json = JSON.parse(data);
        resolve({
          version: json.version || null,
          commit: json.commit || null,
          pid: json.pid || null,
          service: json.service || null,
          authoritative: false
        });
      } catch (e) {
        resolve(null);
      }
    });
  });
  req.on('error', () => resolve(null));
  req.on('timeout', () => {
    req.destroy();
    resolve(null);
  });
}

function compareAppVersions(v1, v2) {
  if (!v1 && !v2) return 0;
  if (!v1) return -1;
  if (!v2) return 1;
  const p1 = v1.replace(/^[vV]/, '').split(/[-+]/)[0].split('.');
  const p2 = v2.replace(/^[vV]/, '').split(/[-+]/)[0].split('.');
  const max = Math.max(p1.length, p2.length);
  for (let i = 0; i < max; i++) {
    const n1 = parseInt((p1[i] || '0').replace(/\D/g, ''), 10) || 0;
    const n2 = parseInt((p2[i] || '0').replace(/\D/g, ''), 10) || 0;
    if (n1 !== n2) return n1 > n2 ? 1 : -1;
  }
  return 0;
}

function cleanLegacyStartupEntries() {
  if (process.platform !== 'win32') return;

  try {
    // 1. Inspect and remove legacy Startup folder VBS script from v1.0.280
    const appData = process.env.APPDATA || path.join(process.env.USERPROFILE || 'C:\\', 'AppData', 'Roaming');
    const startupDir = path.join(appData, 'Microsoft', 'Windows', 'Start Menu', 'Programs', 'Startup');
    const vbsPath = path.join(startupDir, 'RupeeCRM.vbs');

    if (fs.existsSync(vbsPath)) {
      try {
        const content = fs.readFileSync(vbsPath, 'utf8');
        if (/rupeecrm|billsoft/i.test(content)) {
          fs.unlinkSync(vbsPath);
          legacyMigrationDetected = true;
          legacyMigrationDetails.push(`Removed legacy startup script: ${vbsPath}`);
          logMessage(`[Migration] Cleaned legacy startup script: ${vbsPath}`);
        }
      } catch (err) {
        logMessage(`[Migration] Notice: Could not remove legacy VBS script: ${err.message}`);
      }
    }

    // 2. Inspect and remove legacy HKCU Run registry value from v1.0.280
    try {
      const queryOut = child_process.execSync(
        'reg query "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run" /v RupeeCRMService',
        { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'], timeout: 2000 }
      );
      if (/rupeecrm|billsoft/i.test(queryOut)) {
        child_process.execSync(
          'reg delete "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run" /v RupeeCRMService /f',
          { stdio: ['ignore', 'pipe', 'ignore'], timeout: 2000 }
        );
        legacyMigrationDetected = true;
        legacyMigrationDetails.push('Removed legacy registry Run key: RupeeCRMService');
        logMessage('[Migration] Cleaned legacy registry key: HKCU Run -> RupeeCRMService');
      }
    } catch (ignored) { }

    // 3. Clean legacy jpackage binary directory if present alongside modern installation
    const localAppData = process.env.LOCALAPPDATA || path.join(process.env.USERPROFILE || 'C:\\', 'AppData', 'Local');
    const legacyAppDir = path.join(localAppData, 'Programs', 'RupeeCRM', 'app');
    const legacyCfg = path.join(legacyAppDir, 'RupeeCRM.cfg');
    if (fs.existsSync(legacyCfg)) {
      try {
        fs.rmSync(legacyAppDir, { recursive: true, force: true });
        legacyMigrationDetected = true;
        legacyMigrationDetails.push(`Removed stale jpackage app directory: ${legacyAppDir}`);
        logMessage(`[Migration] Cleaned stale jpackage directory: ${legacyAppDir}`);
      } catch (err) {
        logMessage(`[Migration] Notice: Could not remove stale jpackage app dir: ${err.message}`);
      }
    }
  } catch (err) {
    logMessage(`[Migration] Error during legacy cleanup: ${err.message}`);
  }
}

async function triggerGracefulBackendShutdown(targetPort = PORT) {
  logMessage(`Triggering graceful backend HTTP shutdown on port ${targetPort}...`);
  return new Promise((resolve) => {
    try {
      const req = http.request(
        {
          hostname: '127.0.0.1',
          port: targetPort,
          path: '/api/shutdown',
          method: 'POST',
          timeout: 2000
        },
        (res) => {
          resolve(res.statusCode === 200);
        }
      );
      req.on('error', () => resolve(false));
      req.on('timeout', () => {
        req.destroy();
        resolve(false);
      });
      req.end();
    } catch (e) {
      resolve(false);
    }
  });
}

async function terminateRupeeCRMProcess(procInfo, maxWaitMs = 6000, targetPort = PORT) {
  if (!procInfo || !procInfo.isRupeeCRM) {
    logMessage('Refusing to terminate process: Target process is not verified as RupeeCRM.');
    return false;
  }

  logMessage(`Initiating controlled shutdown of RupeeCRM process (PID: ${procInfo.pid || 'unknown'}) on port ${targetPort}...`);

  // Step 1: Attempt graceful HTTP shutdown signal
  await triggerGracefulBackendShutdown(targetPort);

  // Wait up to 1500ms for port to release naturally
  const start = Date.now();
  while (Date.now() - start < 1500) {
    const stillListening = await isPortListening(targetPort, 250);
    if (!stillListening) {
      logMessage(`Backend (PID: ${procInfo.pid}) exited cleanly following HTTP shutdown signal.`);
      return true;
    }
    await new Promise((r) => setTimeout(r, 200));
  }

  // Step 2: Target-specific PID termination (OS-level fallback)
  if (procInfo.pid) {
    logMessage(`Backend still active after graceful shutdown signal. Terminating verified RupeeCRM PID ${procInfo.pid}...`);
    try {
      if (process.platform === 'win32') {
        child_process.execSync(`taskkill /PID ${procInfo.pid} /F`, { stdio: 'ignore', timeout: 3000 });
      } else {
        process.kill(procInfo.pid, 'SIGKILL');
      }
    } catch (err) {
      logMessage(`Notice: Process termination command returned: ${err.message}`);
    }
  }

  // Step 3: Wait for port to become available
  const deadline = Date.now() + maxWaitMs;
  while (Date.now() < deadline) {
    const occupied = await isPortListening(targetPort, 250);
    if (!occupied) {
      logMessage(`Port ${targetPort} confirmed released.`);
      return true;
    }
    await new Promise((r) => setTimeout(r, 250));
  }

  logMessage(`Warning: Port ${targetPort} remained occupied after termination attempts.`);
  return false;
}

async function waitForBackend(maxSeconds = 60, onProgress = null) {
  const start = Date.now();
  const maxMs = maxSeconds * 1000;
  let attempts = 0;

  while (Date.now() - start < maxMs) {
    attempts++;
    const healthy = await checkBackendHealth(1200);
    if (healthy) {
      backendHealthy = true;
      activeBackendIdentity = await getBackendRuntimeIdentity(1500);
      updateTrayMenu();
      return true;
    }

    if (onProgress) {
      if (attempts < 6) {
        onProgress('Initializing RupeeCRM supervisor & JVM...');
      } else if (attempts < 15) {
        onProgress('Starting Spring Boot application engine...');
      } else {
        onProgress('Verifying database schema & business services...');
      }
    }

    await new Promise((r) => setTimeout(r, 600));
  }
  return false;
}

async function startJavaSupervisor() {
  cleanLegacyStartupEntries();

  const packagedVer = app.getVersion();
  logMessage(`Evaluating backend lifecycle. Packaged Electron version: v${packagedVer}`);

  const portOccupied = await isPortListening(PORT, 500);

  if (portOccupied) {
    logMessage(`Port ${PORT} is occupied. Inspecting owning process & backend identity...`);
    const procInfo = await inspectListeningProcess(PORT);
    const identity = await getBackendRuntimeIdentity(2000);
    const runningVer = identity ? identity.version : null;

    logMessage(`Process on port ${PORT}: PID=${procInfo.pid || 'unknown'}, isRupeeCRM=${procInfo.isRupeeCRM}, reportedVersion=${runningVer || 'UNKNOWN'}`);

    if (!procInfo.isRupeeCRM) {
      // Case D-unidentified: Unrelated process occupying port
      logMessage(`CRITICAL: Port ${PORT} is occupied by an unidentified non-RupeeCRM process (PID: ${procInfo.pid}, Path: ${procInfo.executablePath}).`);
      unidentifiedProcessConflict = procInfo;
      updateSplashStatus(`Port ${PORT} is occupied by an unidentified process (${procInfo.name || 'PID ' + procInfo.pid}). Startup halted for safety.`, true);
      showDiagnosticsWindow();
      return false;
    }

    // Process IS verified as RupeeCRM
    if (!runningVer) {
      // Case D-legacy: Legacy RupeeCRM without version reporting (e.g. v1.0.280)
      logMessage(`Legacy RupeeCRM process detected on port ${PORT} (PID ${procInfo.pid}) without version metadata. Migrating to packaged v${packagedVer}...`);
      updateSplashStatus('Migrating legacy RupeeCRM backend process...');
      const terminated = await terminateRupeeCRMProcess(procInfo);
      if (!terminated) {
        logMessage('Failed to release port from legacy RupeeCRM process.');
        updateSplashStatus('Could not terminate legacy RupeeCRM process. Please close it manually.', true);
        return false;
      }
    } else {
      const cmp = compareAppVersions(packagedVer, runningVer);

      if (cmp === 0) {
        // Case B: Matching version & verified RupeeCRM process
        logMessage(`Legitimate RupeeCRM backend (v${runningVer}, PID ${procInfo.pid}) is active and matches packaged version. Reusing.`);
        backendHealthy = true;
        activeBackendIdentity = identity;
        return true;
      } else if (cmp > 0) {
        // Case C: Older version (e.g. running 1.0.280 or older while packaged is 1.0.308)
        logMessage(`Active RupeeCRM backend (v${runningVer}) is older than packaged version (v${packagedVer}). Terminating older backend...`);
        updateSplashStatus(`Updating RupeeCRM runtime from v${runningVer} to v${packagedVer}...`);
        const terminated = await terminateRupeeCRMProcess(procInfo);
        if (!terminated) {
          logMessage('Failed to release port from older RupeeCRM process.');
          updateSplashStatus('Could not terminate older RupeeCRM process. Please close it manually.', true);
          return false;
        }
      } else {
        // Case E: Running backend is newer than packaged Electron
        logMessage(`Active RupeeCRM backend reports newer version (v${runningVer} > packaged v${packagedVer}). Retaining newer active backend under compatibility policy.`);
        backendHealthy = true;
        activeBackendIdentity = identity;
        return true;
      }
    }
  }

  // Case A (or post-termination of Case C/D): Spawn packaged supervisor
  const javaBin = resolveJavaRuntime();
  const launcherJar = resolveLauncherJar();

  if (!launcherJar) {
    logMessage('Warning: launcher.jar not located. Assuming backend started externally.');
    return false;
  }

  logMessage(`Spawning packaged Java Supervisor via: ${javaBin} -jar ${launcherJar}`);

  const args = [
    '-Xms128m',
    '-Xmx512m',
    '-Dfile.encoding=UTF-8',
    '-Djava.awt.headless=true',
    `-DRUPEECRM_DATA_DIR=${dirs.dataDir}`,
    '-jar',
    launcherJar,
    '--background'
  ];

  try {
    const logOutPath = path.join(dirs.logsDir, 'supervisor-stdout.log');
    const logErrPath = path.join(dirs.logsDir, 'supervisor-stderr.log');
    const outStream = fs.openSync(logOutPath, 'a');
    const errStream = fs.openSync(logErrPath, 'a');

    supervisorProcess = child_process.spawn(javaBin, args, {
      detached: false,
      stdio: ['ignore', outStream, errStream],
      windowsHide: true,
      cwd: path.dirname(launcherJar)
    });

    logMessage(`Java supervisor spawned (PID: ${supervisorProcess.pid})`);

    supervisorProcess.on('error', (err) => {
      logMessage(`Supervisor process error: ${err.message}`);
    });

    supervisorProcess.on('exit', (code, signal) => {
      logMessage(`Supervisor process exited with code ${code}, signal ${signal}`);
      supervisorProcess = null;
      backendHealthy = false;
      updateTrayMenu();
    });

    return true;
  } catch (err) {
    logMessage(`Failed to spawn supervisor: ${err.message}`);
    return false;
  }
}

async function stopSupervisor() {
  if (supervisorProcess && !supervisorProcess.killed) {
    logMessage(`Stopping Java supervisor process (PID: ${supervisorProcess.pid})...`);
    await triggerGracefulBackendShutdown();

    try {
      supervisorProcess.kill('SIGTERM');
      const waitPromise = new Promise((resolve) => {
        if (!supervisorProcess) return resolve(true);
        supervisorProcess.once('exit', () => resolve(true));
        setTimeout(() => resolve(false), 4000);
      });

      const exited = await waitPromise;
      if (!exited && supervisorProcess) {
        logMessage('Supervisor did not exit gracefully; sending SIGKILL.');
        supervisorProcess.kill('SIGKILL');
      }
    } catch (ignored) { }
    supervisorProcess = null;
  }
}

// =========================================================================
// 4. Window Creation & Splash Management
// =========================================================================
function createSplashWindow() {
  splashWindow = new BrowserWindow({
    width: 440,
    height: 380,
    resizable: false,
    frame: false,
    transparent: true,
    alwaysOnTop: true,
    show: false,
    icon: resolveAssetPath(process.platform === 'win32' ? 'icon.ico' : 'icon.png'),
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      sandbox: true,
      preload: path.join(__dirname, 'preload.js')
    }
  });

  splashWindow.loadFile(path.join(__dirname, 'splash', 'splash.html'));

  splashWindow.once('ready-to-show', () => {
    if (!startHidden) {
      splashWindow.show();
    }
  });

  splashWindow.on('closed', () => {
    splashWindow = null;
  });
}

function updateSplashStatus(text, isError = false) {
  if (splashWindow && !splashWindow.isDestroyed()) {
    splashWindow.webContents.send('message', { type: 'status', text, isError });
  }
}

function createMainWindow() {
  mainWindow = new BrowserWindow({
    width: 1366,
    height: 850,
    minWidth: 1024,
    minHeight: 700,
    title: 'RupeeCRM',
    icon: resolveAssetPath(process.platform === 'win32' ? 'icon.ico' : 'icon.png'),
    show: false,
    backgroundColor: '#f8fafc',
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      sandbox: true,
      preload: path.join(__dirname, 'preload.js')
    }
  });

  // Native Application Menu
  const menuTemplate = [
    {
      label: 'File',
      submenu: [
        {
          label: 'System Diagnostics',
          click: () => showDiagnosticsWindow()
        },
        {
          label: 'Open Data Folder',
          click: () => shell.openPath(dirs.dataDir)
        },
        {
          label: 'Open Logs Folder',
          click: () => shell.openPath(dirs.logsDir)
        },
        { type: 'separator' },
        {
          label: 'Hide to System Tray',
          accelerator: 'CmdOrCtrl+W',
          click: () => mainWindow.hide()
        },
        {
          label: 'Exit RupeeCRM',
          accelerator: 'CmdOrCtrl+Q',
          click: () => quitApplication()
        }
      ]
    },
    {
      label: 'Edit',
      submenu: [
        { role: 'undo' },
        { role: 'redo' },
        { type: 'separator' },
        { role: 'cut' },
        { role: 'copy' },
        { role: 'paste' },
        { role: 'selectAll' }
      ]
    },
    {
      label: 'View',
      submenu: [
        { role: 'reload' },
        { role: 'forceReload' },
        { type: 'separator' },
        { role: 'resetZoom' },
        { role: 'zoomIn' },
        { role: 'zoomOut' },
        { type: 'separator' },
        { role: 'togglefullscreen' }
      ]
    },
    {
      label: 'Help',
      submenu: [
        {
          label: 'RupeeCRM Diagnostics',
          click: () => showDiagnosticsWindow()
        },
        {
          label: 'About RupeeCRM',
          click: () => {
            dialog.showMessageBox(mainWindow, {
              type: 'info',
              title: 'About RupeeCRM',
              message: 'RupeeCRM Desktop Edition',
              detail: `Version: ${app.getVersion()}\nAuthoritative Data: ${dirs.dataDir}\nPlatform: Windows Desktop (Electron)\nBackend: Spring Boot on OpenJDK 21`,
              buttons: ['OK']
            });
          }
        }
      ]
    }
  ];

  const menu = Menu.buildFromTemplate(menuTemplate);
  Menu.setApplicationMenu(menu);

  // Load backend UI with cache-busting version parameter
  const appVer = app.getVersion();
  const targetUrl = `${APP_URL}?v=${encodeURIComponent(appVer)}`;
  mainWindow.loadURL(targetUrl);

  mainWindow.once('ready-to-show', () => {
    if (splashWindow && !splashWindow.isDestroyed()) {
      splashWindow.close();
      splashWindow = null;
    }
    if (!startHidden) {
      mainWindow.show();
      mainWindow.focus();
    }
  });

  // Handle OTA In-App Update Disconnects Gracefully
  mainWindow.webContents.on('did-fail-load', (event, errorCode, errorDescription, validatedURL) => {
    logMessage(`Navigation failed to ${validatedURL} (code: ${errorCode}, error: ${errorDescription})`);
    if (errorCode === -102 || errorCode === -105 || errorCode === -106) {
      // Backend temporarily offline during restart or update handoff
      mainWindow.webContents.loadURL(`data:text/html;charset=utf-8,${encodeURIComponent(`
        <!DOCTYPE html>
        <html>
        <head>
          <title>RupeeCRM - Reconnecting</title>
          <style>
            body { font-family: Segoe UI, sans-serif; background: #0f172a; color: #f8fafc; display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100vh; margin: 0; }
            h2 { margin-bottom: 8px; font-weight: 600; }
            p { color: #94a3b8; font-size: 14px; margin-bottom: 24px; }
            .spinner { width: 36px; height: 36px; border: 3px solid rgba(255,255,255,0.1); border-top-color: #3b82f6; border-radius: 50%; animation: spin 1s infinite linear; }
            @keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }
          </style>
          <script>
            function pollHealth() {
              fetch('${HEALTH_URL}').then(r => {
                if (r.ok) location.href = '${APP_URL}?v=${encodeURIComponent(appVer)}&t=' + Date.now();
                else setTimeout(pollHealth, 1000);
              }).catch(() => setTimeout(pollHealth, 1000));
            }
            setInterval(pollHealth, 1500);
          </script>
        </head>
        <body>
          <div class="spinner"></div>
          <h2 style="margin-top: 20px;">Updating RupeeCRM Backend...</h2>
          <p>Please wait while services restart and complete transaction verification.</p>
        </body>
        </html>
      `)}`);
    }
  });

  // External Navigation Restriction (Prevent arbitrary URL navigation)
  mainWindow.webContents.on('will-navigate', (event, targetUrl) => {
    try {
      const parsed = new URL(targetUrl);
      if (parsed.hostname !== 'localhost' && parsed.hostname !== '127.0.0.1') {
        event.preventDefault();
        shell.openExternal(targetUrl);
      }
    } catch (e) {
      event.preventDefault();
    }
  });

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url);
    return { action: 'deny' };
  });

  // Minimize to Tray on Window Close
  mainWindow.on('close', (event) => {
    if (!isQuitting) {
      event.preventDefault();
      mainWindow.hide();

      if (!hasShownTrayNotice && tray) {
        hasShownTrayNotice = true;
        if (process.platform === 'win32') {
          tray.displayBalloon({
            title: 'RupeeCRM',
            content: 'RupeeCRM is running in the background. Click the tray icon to restore.',
            iconType: 'info'
          });
        }
      }
    }
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
  });
}

function showDiagnosticsWindow() {
  if (diagnosticsWindow && !diagnosticsWindow.isDestroyed()) {
    diagnosticsWindow.show();
    diagnosticsWindow.focus();
    return;
  }

  diagnosticsWindow = new BrowserWindow({
    width: 680,
    height: 580,
    title: 'RupeeCRM System Diagnostics',
    icon: resolveAssetPath('icon.ico'),
    resizable: true,
    minimizable: false,
    backgroundColor: '#0f172a',
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      sandbox: true,
      preload: path.join(__dirname, 'preload.js')
    }
  });

  diagnosticsWindow.setMenu(null);
  diagnosticsWindow.loadFile(path.join(__dirname, 'diagnostics', 'diagnostics.html'));

  diagnosticsWindow.on('closed', () => {
    diagnosticsWindow = null;
  });
}

// =========================================================================
// 5. System Tray & Context Menu
// =========================================================================
function setupSystemTray() {
  try {
    const iconName = process.platform === 'win32' ? 'icon.ico' : 'icon.png';
    const iconPath = resolveAssetPath(iconName);
    tray = new Tray(iconPath);
    tray.setToolTip('RupeeCRM [Starting...]');

    updateTrayMenu();

    tray.on('click', () => {
      if (mainWindow) {
        if (mainWindow.isVisible()) {
          mainWindow.focus();
        } else {
          mainWindow.show();
        }
      }
    });
  } catch (err) {
    logMessage('Tray initialization warning: ' + err.message);
  }
}

function updateTrayMenu() {
  if (!tray) return;

  const autostart = app.getLoginItemSettings().openAtLogin;
  const statusLabel = backendHealthy
    ? `Backend: Online (Port ${PORT})`
    : 'Backend: Offline / Starting';

  tray.setToolTip(`RupeeCRM [${backendHealthy ? 'Online' : 'Starting'}]`);

  const contextMenu = Menu.buildFromTemplate([
    {
      label: 'Open RupeeCRM',
      click: () => {
        if (mainWindow) {
          mainWindow.show();
          mainWindow.focus();
        }
      }
    },
    {
      label: mainWindow && mainWindow.isVisible() ? 'Hide Window' : 'Show Window',
      click: () => {
        if (!mainWindow) return;
        if (mainWindow.isVisible()) {
          mainWindow.hide();
        } else {
          mainWindow.show();
          mainWindow.focus();
        }
      }
    },
    { type: 'separator' },
    {
      label: statusLabel,
      enabled: false
    },
    {
      label: 'System Diagnostics',
      click: () => showDiagnosticsWindow()
    },
    {
      label: 'Restart Application',
      click: async () => {
        logMessage('Manual application restart requested via tray.');
        backendHealthy = false;
        updateTrayMenu();
        await stopSupervisor();
        await startJavaSupervisor();
        await waitForBackend(45);
        if (mainWindow && !mainWindow.isDestroyed()) {
          mainWindow.loadURL(APP_URL);
        }
      }
    },
    {
      label: 'Start with Windows',
      type: 'checkbox',
      checked: autostart,
      click: (item) => {
        logMessage(`Toggling Start with Windows to: ${item.checked}`);
        app.setLoginItemSettings({
          openAtLogin: item.checked,
          args: ['--hidden']
        });
      }
    },
    { type: 'separator' },
    {
      label: 'Exit RupeeCRM',
      click: () => quitApplication()
    }
  ]);

  tray.setContextMenu(contextMenu);
}

// =========================================================================
// 6. IPC Handlers
// =========================================================================
function registerIpcHandlers() {
  ipcMain.handle('app:get-version', () => app.getVersion());

  ipcMain.handle('backend:get-status', () => ({
    healthy: backendHealthy,
    port: PORT,
    pid: supervisorProcess ? supervisorProcess.pid : null
  }));

  ipcMain.handle('diagnostics:get-info', async () => {
    let latencyMs = null;
    let identity = activeBackendIdentity;

    if (backendHealthy) {
      const start = Date.now();
      try {
        await checkBackendHealth(1000);
        latencyMs = Date.now() - start;
        const freshId = await getBackendRuntimeIdentity(1000);
        if (freshId) identity = freshId;
      } catch (ignored) { }
    }

    const dbPath = path.join(dirs.dataDir, 'rupeecrm.mv.db');
    const dbExists = fs.existsSync(dbPath);

    return {
      appVersion: app.getVersion(),
      javaRuntime: resolveJavaRuntime(),
      supervisorPid: supervisorProcess ? supervisorProcess.pid : (identity && identity.pid ? identity.pid : 'External / Managed'),
      supervisorMode: 'Background Service',
      backendPort: PORT,
      backendHealthy,
      latencyMs,
      backendVersion: identity ? (identity.version || 'v' + app.getVersion()) : 'Unknown',
      backendCommit: identity ? (identity.commit || 'HEAD') : 'HEAD',
      warPath: identity && identity.warPath ? identity.warPath : 'authoritative packaged WAR',
      legacyMigrationDetected,
      legacyMigrationDetails,
      unidentifiedProcessConflict,
      dataDir: dirs.dataDir,
      dbExists,
      logsDir: dirs.logsDir,
      recentLogs: recentLogs.slice(-20)
    };
  });

  ipcMain.handle('system:open-logs', () => shell.openPath(dirs.logsDir));
  ipcMain.handle('system:open-data', () => shell.openPath(dirs.dataDir));

  ipcMain.handle('backend:restart', async () => {
    logMessage('Backend restart invoked via IPC');
    await stopSupervisor();
    await startJavaSupervisor();
    await waitForBackend(45);
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.loadURL(APP_URL);
    }
    return { success: true };
  });

  ipcMain.on('system:open-external', (event, urlStr) => {
    if (typeof urlStr === 'string' && (urlStr.startsWith('http:') || urlStr.startsWith('https:') || urlStr.startsWith('mailto:'))) {
      shell.openExternal(urlStr);
    }
  });

  ipcMain.on('window:minimize', () => {
    if (mainWindow) mainWindow.minimize();
  });

  ipcMain.on('window:maximize', () => {
    if (mainWindow) {
      if (mainWindow.isMaximized()) mainWindow.unmaximize();
      else mainWindow.maximize();
    }
  });

  ipcMain.on('window:close', () => {
    if (mainWindow) mainWindow.hide();
  });

  // Native PDF Document Actions (Print & Save)
  ipcMain.handle('pdf:save', async (event, { base64Data, defaultFilename }) => {
    try {
      if (!base64Data) throw new Error('No PDF content provided for saving');
      const win = BrowserWindow.fromWebContents(event.sender) || mainWindow;
      const { canceled, filePath } = await dialog.showSaveDialog(win, {
        title: 'Save PDF Document',
        defaultPath: defaultFilename || 'document.pdf',
        filters: [
          { name: 'PDF Documents', extensions: ['pdf'] },
          { name: 'All Files', extensions: ['*'] }
        ]
      });
      if (canceled || !filePath) {
        return { canceled: true };
      }
      const buffer = Buffer.from(base64Data, 'base64');
      await fs.promises.writeFile(filePath, buffer);
      logMessage(`PDF saved successfully to: ${filePath}`);
      return { success: true, filePath };
    } catch (err) {
      logMessage(`PDF save failed: ${err.message}`);
      return { success: false, error: err.message };
    }
  });

  ipcMain.handle('pdf:print', async (event, payload = {}) => {
    let printWin = null;
    try {
      const base64Data = payload.base64Data || (typeof payload === 'string' ? payload : null);
      const pages = payload.pages || null;
      const format = payload.format || 'A4';
      const options = payload.options || {};

      if (!base64Data && (!pages || pages.length === 0)) {
        throw new Error('No PDF content or rendered pages provided for printing');
      }

      logMessage(`[Print] Native print requested: format=${format}, hasPreRenderedPages=${Boolean(pages && pages.length)}, pageCount=${pages ? pages.length : 'dynamic'}, silent=${Boolean(options.silent)}`);

      const runnerPath = path.join(__dirname, 'print/print-runner.html');
      if (!fs.existsSync(runnerPath)) {
        throw new Error(`Print runner template not found at ${runnerPath}`);
      }

      printWin = new BrowserWindow({
        show: false,
        webPreferences: {
          nodeIntegration: false,
          contextIsolation: true,
          sandbox: true
        }
      });

      await printWin.loadURL(pathToFileURL(runnerPath).href);
      logMessage('[Print] Print runner loaded; rendering document pages into DOM...');

      if (Array.isArray(pages) && pages.length > 0) {
        await printWin.webContents.executeJavaScript(
          `window.renderPagesFromImages(${JSON.stringify(pages)}, ${JSON.stringify(format)})`
        );
      } else {
        await printWin.webContents.executeJavaScript(
          `window.renderPagesFromPdfBase64(${JSON.stringify(base64Data)}, ${JSON.stringify(format)}, 2.5)`
        );
      }

      const renderedCount = await printWin.webContents.executeJavaScript(
        `document.querySelectorAll('.print-page img').length`
      );
      logMessage(`[Print] Document rendering verified: ${renderedCount} page(s) ready in print pipeline`);

      if (renderedCount === 0) {
        throw new Error('Document rendering failed: 0 pages rendered');
      }

      // Ensure layout and paint pass before invoking native print dialog
      await printWin.webContents.executeJavaScript(
        `new Promise(resolve => requestAnimationFrame(() => setTimeout(resolve, 80)))`
      );

      const printOpts = {
        silent: Boolean(options.silent),
        printBackground: true,
        color: true,
        margins: { marginType: 'none' },
        ...(options || {})
      };

      logMessage('[Print] Invoking native webContents.print...');
      return new Promise((resolve) => {
        printWin.webContents.print(printOpts, (success, failureReason) => {
          if (!success) {
            logMessage(`[Print] Native print dismissed/cancelled: ${failureReason || 'User cancelled'}`);
          } else {
            logMessage('[Print] Native print job successfully sent to printer spooler');
          }
          if (printWin && !printWin.isDestroyed()) {
            printWin.close();
            printWin = null;
          }
          resolve({ success, failureReason: success ? null : (failureReason || 'User cancelled') });
        });
      });
    } catch (err) {
      if (printWin && !printWin.isDestroyed()) {
        printWin.close();
        printWin = null;
      }
      logMessage(`[Print] Native print error: ${err.message}`);
      return { success: false, error: err.message };
    }
  });
}

// =========================================================================
// 7. Application Lifecycle & Secondary Launch
// =========================================================================
app.on('second-instance', (event, commandLine) => {
  logMessage('Secondary instance launched; focusing active window.');
  if (mainWindow) {
    if (mainWindow.isMinimized()) mainWindow.restore();
    if (!mainWindow.isVisible()) mainWindow.show();
    mainWindow.focus();
  }
});

async function quitApplication() {
  if (isQuitting) return;
  isQuitting = true;
  logMessage('Exiting RupeeCRM desktop application...');

  if (tray) {
    tray.destroy();
    tray = null;
  }

  await stopSupervisor();
  app.quit();
}

app.on('before-quit', (event) => {
  if (!isQuitting) {
    event.preventDefault();
    quitApplication();
  }
});

app.on('window-all-closed', () => {
  // On Windows, keep running in the tray unless explicit exit requested
  if (isQuitting) {
    app.quit();
  }
});

app.whenReady().then(async () => {
  logMessage('RupeeCRM Desktop Shell starting up...');
  registerIpcHandlers();
  setupSystemTray();

  // Clear HTTP/storage cache on startup to prevent stale frontend resources from lingering
  try {
    await session.defaultSession.clearCache();
    await session.defaultSession.clearStorageData({
      storages: ['serviceworkers', 'cachestorage']
    });
  } catch (err) {
    logMessage(`Notice: Error clearing session cache: ${err.message}`);
  }

  // Handle native file downloads cleanly
  session.defaultSession.on('will-download', (event, item, webContents) => {
    const defaultFilename = item.getFilename();
    const win = BrowserWindow.fromWebContents(webContents) || mainWindow;
    const savePath = dialog.showSaveDialogSync(win, {
      title: 'Save File',
      defaultPath: defaultFilename,
      filters: [
        { name: 'Documents', extensions: ['pdf', 'zip', 'csv', 'xlsx'] },
        { name: 'All Files', extensions: ['*'] }
      ]
    });
    if (savePath) {
      item.setSavePath(savePath);
    } else {
      event.preventDefault();
    }
  });

  if (!startHidden) {
    createSplashWindow();
  }

  // Start supervisor & wait for readiness
  const supervisorStarted = await startJavaSupervisor();
  if (!supervisorStarted && !backendHealthy) {
    logMessage('Backend supervisor startup halted.');
    return;
  }

  updateSplashStatus('Waiting for RupeeCRM application backend...');
  const ready = await waitForBackend(60, (statusText) => {
    updateSplashStatus(statusText);
  });

  if (ready) {
    logMessage('RupeeCRM backend readiness verified. Creating main window...');
    createMainWindow();
  } else {
    logMessage('RupeeCRM backend failed to signal readiness within timeout.');
    updateSplashStatus('Backend startup timed out. Check system logs.', true);
  }
});

// Export lifecycle helpers for automated regression verification
if (typeof module !== 'undefined' && module.exports) {
  module.exports = {
    compareAppVersions,
    isPortListening,
    inspectListeningProcess,
    cleanLegacyStartupEntries,
    getBackendRuntimeIdentity,
    terminateRupeeCRMProcess,
    triggerGracefulBackendShutdown
  };
}
