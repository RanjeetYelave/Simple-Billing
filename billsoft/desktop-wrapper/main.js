const { app, BrowserWindow, ipcMain } = require('electron');
const { spawn } = require('child_process');
const path = require('path');
const fs = require('fs');
const http = require('http');

let mainWindow;
let javaProcess;
let isUpdating = false;
let restartAttempts = 0;
const MAX_RESTART_ATTEMPTS = 5;
const RESTART_DELAY_MS = 2000;

const isPackaged = app.isPackaged;
const baseDir = isPackaged ? process.resourcesPath : __dirname;

const backendDir = isPackaged ? path.join(baseDir, 'backend') : path.join(__dirname, '..', 'target');
const warPath = isPackaged ? path.join(backendDir, 'billsoft.war') : path.join(backendDir, 'billsoft-0.0.1-SNAPSHOT.war');
const updateWarPath = path.join(backendDir, 'billsoft-update.war');

const javaExecutable = isPackaged
  ? (process.platform === 'win32'
    ? path.join(baseDir, 'jre', 'bin', 'java.exe')
    : path.join(baseDir, 'jre', 'bin', 'java'))
  : 'java';

/**
 * Called from the renderer via IPC when the user clicks "Install Update".
 * This must set isUpdating BEFORE the Java process exits, otherwise
 * the exit handler won't know to swap the WAR file.
 */
ipcMain.on('set-updating', () => {
  console.log('[Update] Renderer signalled update is starting');
  isUpdating = true;
});

function startJavaBackend() {
  console.log("Starting Spring Boot...");

  if (!fs.existsSync(warPath)) {
    console.error(`WAR not found at ${warPath}`);
    return;
  }

  javaProcess = spawn(javaExecutable, ['-jar', warPath], { cwd: backendDir, stdio: ['ignore', 'pipe', 'pipe'] });

  javaProcess.stdout.on('data', (data) => {
    console.log(data.toString());
  });

  javaProcess.stderr.on('data', (data) => {
    console.error(data.toString());
  });

  javaProcess.on('exit', (code) => {
    console.log(`Java process exited with code ${code}`);

    // Check if the update WAR file exists on disk
    const updateWarExists = fs.existsSync(updateWarPath);

    // If we are in the middle of an update (signalled from renderer), swap the WAR
    if (isUpdating && updateWarExists) {
      console.log("Backend exited for update. Proceeding with swap...");
      handleUpdateSwap();
      return;
    }

    // Also check just the file existence as a fallback (in case IPC was missed)
    if (updateWarExists && !isUpdating) {
      console.log("Update WAR found on disk but isUpdating flag not set. Attempting swap anyway...");
      isUpdating = true;
      handleUpdateSwap();
      return;
    }

    // If it's updating but updateWar does NOT exist, that means we already swapped,
    // and this is a crash of the NEW version! So we should handle it as a crash.
    if (isUpdating && !updateWarExists) {
      isUpdating = false;
    }

    // Otherwise, this is an unexpected exit (crash or manual close)
    if (!isUpdating && code !== null) {
      restartAttempts++;
      console.log(`Java exited unexpectedly. Restart attempt ${restartAttempts}/${MAX_RESTART_ATTEMPTS}`);
      
      if (restartAttempts < MAX_RESTART_ATTEMPTS) {
        const delay = RESTART_DELAY_MS * Math.min(restartAttempts, 5);
        setTimeout(() => {
          startJavaBackend();
          if (mainWindow && !mainWindow.isDestroyed()) {
            setTimeout(() => {
              mainWindow.reload();
            }, 2000);
          }
        }, delay);
      } else {
        showCrashScreen();
      }
    }
  });

  javaProcess.on('error', (err) => {
    console.error("Failed to spawn Java process:", err);
    if (!isUpdating) showCrashScreen();
  });
}

function showCrashScreen() {
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.loadURL('data:text/html;charset=utf-8,' + encodeURIComponent(
      '<div style="background:#1e293b;color:#fff;height:100vh;display:flex;align-items:center;justify-content:center;font-family:sans-serif;text-align:center;padding:20px;border-top:4px solid #ef4444;">' +
      '<div><h1 style="font-size:3rem;margin:0;">⚠️</h1><h2 style="color:#ef4444;margin-top:0;">Backend Connection Lost</h2>' +
      '<p style="color:#94a3b8;">The application backend stopped unexpectedly.</p>' +
      '<button onclick="window.location.reload()" style="margin-top:20px;padding:12px 24px;background:#3b82f6;color:white;border:none;border-radius:6px;cursor:pointer;font-weight:600;box-shadow:0 4px 6px -1px rgba(0,0,0,0.1);">Relaunch Application</button></div></div>'
    ));
  }
}

function handleUpdateSwap() {
  isUpdating = true;
  console.log(`Update swap started. Source: ${updateWarPath}, Target: ${warPath}`);

  let retryCount = 0;
  const maxRetries = 10;
  const retryInterval = 500;

  const performSwap = () => {
    try {
      if (!fs.existsSync(updateWarPath)) {
        throw new Error("Update file missing");
      }

      if (fs.existsSync(warPath)) {
        fs.unlinkSync(warPath);
      }
      fs.renameSync(updateWarPath, warPath);
      
      console.log("Swap successful!");
      finalizeRestart();
    } catch (err) {
      console.error(`Swap attempt ${retryCount + 1} failed: ${err.message}`);
      if (retryCount < maxRetries) {
        retryCount++;
        setTimeout(performSwap, retryInterval);
      } else {
        console.error("Max swap retries reached. Attempting emergency restart.");
        finalizeRestart();
      }
    }
  };

  const finalizeRestart = () => {
    restartAttempts = 0;
    startJavaBackend();

    if (mainWindow && !mainWindow.isDestroyed()) {
      pollServerAfterUpdate(mainWindow);
    }
  };

  setTimeout(performSwap, 2000);
}

function pollServerAfterUpdate(win) {
  let pollCount = 0;
  const maxPolls = 90; // 90 seconds max

  const checkServer = () => {
    pollCount++;
    http.get('http://127.0.0.1:8080', (res) => {
      if (res.statusCode < 400) {
        console.log("Backend is back up after update!");
        isUpdating = false;
        win.loadURL('http://127.0.0.1:8080');
      } else {
        if (pollCount < maxPolls) {
          setTimeout(checkServer, 1000);
        } else {
          isUpdating = false;
          showCrashScreen();
        }
      }
    }).on('error', () => {
      if (pollCount < maxPolls) {
        setTimeout(checkServer, 1000);
      } else {
        isUpdating = false;
        showCrashScreen();
      }
    });
  };

  checkServer();
}

function pollServerAndLoad(win) {
  const checkServer = () => {
    http.get('http://127.0.0.1:8080', (res) => {
      if (res.statusCode < 400) {
        win.loadURL('http://127.0.0.1:8080');
      } else {
        setTimeout(checkServer, 1000);
      }
    }).on('error', () => {
      setTimeout(checkServer, 1000);
    });
  };
  checkServer();
}

app.whenReady().then(() => {
  startJavaBackend();

  mainWindow = new BrowserWindow({
    width: 1200,
    height: 800,
    title: 'Billsoft',
    autoHideMenuBar: true,
    webPreferences: {
      webSecurity: false,
      preload: path.join(__dirname, 'preload.js')
    }
  });

  const loadingHtml = `<!DOCTYPE html>
<html><body style="margin:0;height:100vh;background:linear-gradient(135deg,#1e293b,#334155);display:flex;flex-direction:column;align-items:center;justify-content:center;font-family:-apple-system,system-ui,sans-serif;color:#fff;">
  <div style="font-size:48px;font-weight:800;color:#3b82f6;margin-bottom:8px;">B</div>
  <h1 style="margin:0 0 4px;font-weight:600;font-size:1.5rem;">Billsoft</h1>
  <p style="margin:0 0 24px;color:#94a3b8;font-size:0.9rem;">Simple Billing & Invoicing</p>
  <div style="width:200px;height:4px;background:#475569;border-radius:2px;overflow:hidden;">
    <div style="width:30%;height:100%;background:linear-gradient(90deg,#3b82f6,#60a5fa);border-radius:2px;animation:load 2s ease-in-out infinite;"></div>
  </div>
  <p style="margin-top:16px;color:#94a3b8;font-size:0.8rem;">Loading...</p>
  <style>@keyframes load{0%{transform:translateX(-100%)}100%{transform:translateX(400%)}}</style>
</body></html>`;
  
  mainWindow.loadURL('data:text/html;charset=utf-8,' + encodeURIComponent(loadingHtml));

  if (!isPackaged) {
    mainWindow.webContents.openDevTools();
  }

  pollServerAndLoad(mainWindow);
});

app.on('will-quit', () => {
  if (javaProcess) {
    javaProcess.kill('SIGTERM');
  }
});