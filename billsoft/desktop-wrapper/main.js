const { app, BrowserWindow, ipcMain } = require('electron');
const { spawn } = require('child_process');
const path = require('path');
const fs = require('fs');
const http = require('http');

let mainWindow;
let javaProcess;
let updateProgressWindow;
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

function startJavaBackend() {
  console.log("Starting Spring Boot...");

  // Check if WAR exists
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

    // Check if there's an update waiting
    if (fs.existsSync(updateWarPath)) {
      console.log("Update detected! Swapping WAR files...");
      // Clear cache to ensure new UI files are loaded
      if (mainWindow && !mainWindow.isDestroyed()) {
        mainWindow.webContents.session.clearCache();
      }
      handleUpdateSwap();
      return; // don't fall through to error handling
    }

    // If Java died with non-zero code (crash or error), or if it died with code 0 but no update was found (unexpected shutdown)
    if (code !== null) {
      restartAttempts++;
      console.log(`Java exited with code ${code}. Restart attempt ${restartAttempts}/${MAX_RESTART_ATTEMPTS}`);
      
      if (restartAttempts < MAX_RESTART_ATTEMPTS) {
        const delay = RESTART_DELAY_MS * Math.min(restartAttempts, 5);
        setTimeout(() => {
          startJavaBackend();
          if (mainWindow && !mainWindow.isDestroyed()) {
            setTimeout(() => {
              mainWindow.reload();
            }, delay + 5000);
          }
        }, delay);
      } else {
        console.error("Max restart attempts reached.");
        if (mainWindow && !mainWindow.isDestroyed()) {
          mainWindow.loadURL('data:text/html;charset=utf-8,' + encodeURIComponent(
            '<div style="background:#1e293b;color:#fff;height:100vh;display:flex;align-items:center;justify-content:center;font-family:sans-serif;text-align:center;padding:20px;">' +
            '<div><h2 style="color:#ef4444;">Backend Connection Lost</h2><p>The application backend stopped unexpectedly and could not be restarted.</p>' +
            '<button onclick="window.location.reload()" style="padding:10px 20px;background:#3b82f6;color:white;border:none;border-radius:5px;cursor:pointer;">Try Again</button></div></div>'
          ));
        }
      }
    }
  });

  javaProcess.on('error', (err) => {
    console.error("Failed to spawn Java process:", err);
  });
}

function handleUpdateSwap() {
  showUpdateProgressWindow();
  updateProgressWindow.webContents.executeJavaScript(
    `document.getElementById('status').textContent = 'Installing update...';` +
    `document.getElementById('phase-text').textContent = 'Step 2 of 3: Installing';` +
    `document.getElementById('progress-bar').style.width = '60%';`
  );

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
        console.error("Max swap retries reached. Attempting emergency restart of old version.");
        finalizeRestart(); // restart whatever is there
      }
    }
  };

  const finalizeRestart = () => {
    if (updateProgressWindow && !updateProgressWindow.isDestroyed()) {
      updateProgressWindow.webContents.executeJavaScript(
        `document.getElementById('status').textContent = 'Restarting application...';` +
        `document.getElementById('phase-text').textContent = 'Step 3 of 3: Restarting';` +
        `document.getElementById('progress-bar').style.width = '90%';`
      );
    }
    
    restartAttempts = 0;
    startJavaBackend();

    if (mainWindow && !mainWindow.isDestroyed()) {
      pollServerAfterUpdate(mainWindow);
    }
  };

  // Wait 2 seconds before first attempt to ensure file locks are released
  setTimeout(performSwap, 2000);
}

function pollServerAfterUpdate(win) {
  let pollCount = 0;
  const maxPolls = 60; // 60 seconds max wait

  const checkServer = () => {
    pollCount++;
    http.get('http://127.0.0.1:8080', (res) => {
      if (res.statusCode < 500) {
        console.log("Backend is back up after update!");
        
        // Show complete in native window
        if (updateProgressWindow && !updateProgressWindow.isDestroyed()) {
          showUpdateCompleteWindow();
        }
        
        // Reload main window to the app
        win.loadURL('http://127.0.0.1:8080');
      } else {
        if (pollCount < maxPolls) {
          setTimeout(checkServer, 1000);
        } else {
          console.error("Backend did not come back after update.");
          win.loadURL('data:text/html;charset=utf-8,' + encodeURIComponent(
            '<h2 style="font-family:sans-serif;color:#ef4444;padding:40px;">Update Error: Backend failed to restart. Please relaunch the application.</h2>'
          ));
        }
      }
    }).on('error', () => {
      // Server not ready yet - keep polling
      if (pollCount < maxPolls) {
        setTimeout(checkServer, 1000);
      } else {
        console.error("Backend did not come back after update (timeout).");
        win.loadURL('data:text/html;charset=utf-8,' + encodeURIComponent(
          '<h2 style="font-family:sans-serif;color:#ef4444;padding:40px;">Update Error: Backend failed to restart. Please relaunch the application.</h2>'
        ));
      }
    });
  };

  checkServer();
}

function showUpdateProgressWindow() {
  if (updateProgressWindow && !updateProgressWindow.isDestroyed()) {
    updateProgressWindow.show();
    updateProgressWindow.focus();
    return;
  }

  updateProgressWindow = new BrowserWindow({
    width: 480,
    height: 320,
    frame: false,
    resizable: false,
    alwaysOnTop: true,
    backgroundColor: '#1e293b',
    parent: mainWindow, // Make it a child of main window
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true
    }
  });

  // Blur the main window content
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.webContents.executeJavaScript(`
      if (!document.getElementById('update-blur-style')) {
        const s = document.createElement('style');
        s.id = 'update-blur-style';
        s.innerHTML = 'body > *:not(.modal-overlay) { filter: blur(8px) brightness(0.6); pointer-events: none; transition: filter 0.5s ease; }';
        document.head.appendChild(s);
      }
    `);
  }

  const html = `<!DOCTYPE html>
<html>
<body style="margin:0;padding:40px;font-family:-apple-system,system-ui,sans-serif;background:#1e293b;color:#fff;display:flex;flex-direction:column;align-items:center;justify-content:center;height:100vh;text-align:center;overflow:hidden;border:1px solid #334155;">
  <h2 style="margin:0 0 12px;font-weight:600;font-size:1.4rem;">🔄 Updating Billsoft</h2>
  <p id="phase-text" style="margin:0 0 20px;color:#60a5fa;font-size:0.85rem;text-transform:uppercase;letter-spacing:1.5px;font-weight:500;">Step 1: Downloading</p>
  
  <div style="width:100%;background:#334155;border-radius:10px;padding:3px;margin-bottom:12px;box-shadow:inset 0 2px 4px rgba(0,0,0,0.3);">
    <div id="progress-bar" style="width:0%;height:12px;background:linear-gradient(90deg,#3b82f6,#60a5fa);border-radius:8px;transition:width 0.4s cubic-bezier(0.4, 0, 0.2, 1); box-shadow: 0 0 10px rgba(59,130,246,0.4);"></div>
  </div>

  <div style="display:flex;justify-content:space-between;width:100%;margin-bottom:24px;font-size:0.8rem;color:#94a3b8;font-variant-numeric: tabular-nums;">
    <span id="speed-text" style="visibility:hidden;">0.00 MB/s</span>
    <span id="size-text" style="visibility:hidden;">0.0 MB / 0.0 MB</span>
  </div>

  <p id="status" style="margin:0;color:#cbd5e1;font-size:0.95rem;min-height:1.2em;font-weight:400;">Connecting to update server...</p>
  <p style="margin-top:30px;font-size:0.75rem;color:#f87171;opacity:0.9;font-weight:500;">⚠️ Do not close the app or disconnect internet</p>

  <script>
    const source = new EventSource('http://127.0.0.1:8080/api/system/update-progress');
    source.addEventListener('progress', e => {
      try {
        const data = JSON.parse(e.data);
        const percent = data.percent || 0;
        
        document.getElementById('progress-bar').style.width = percent + '%';
        document.getElementById('status').textContent = data.message || '';
        
        if (data.status) {
          let step = '1: Downloading';
          if (data.status === 'installing') step = '2: Installing';
          if (data.status === 'restarting') step = '3: Restarting';
          document.getElementById('phase-text').textContent = 'Step ' + step;
        }
        if (data.speed) {
          document.getElementById('speed-text').textContent = data.speed;
          document.getElementById('speed-text').style.visibility = 'visible';
        } else {
          document.getElementById('speed-text').style.visibility = 'hidden';
        }
        if (data.size) {
          document.getElementById('size-text').textContent = data.size;
          document.getElementById('size-text').style.visibility = 'visible';
        } else {
          document.getElementById('size-text').style.visibility = 'hidden';
        }
      } catch (err) {
        console.error('Progress parse error', err);
      }
    });
    source.onerror = () => {
      console.log('SSE connection lost/closed');
    };
  </script>
</body>
</html>`;

  updateProgressWindow.loadURL('data:text/html;charset=utf-8,' + encodeURIComponent(html));
  updateProgressWindow.center(); // Center on parent (mainWindow)
  
  updateProgressWindow.on('closed', () => {
    // Remove blur from main window
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.executeJavaScript(`
        const s = document.getElementById('update-blur-style');
        if (s) s.remove();
      `);
    }
    updateProgressWindow = null;
  });
}

function showUpdateCompleteWindow() {
  if (updateProgressWindow && !updateProgressWindow.isDestroyed()) {
    updateProgressWindow.webContents.executeJavaScript(
      `document.body.innerHTML = \`<div style="display:flex;flex-direction:column;align-items:center;justify-content:center;height:100vh;text-align:center;padding:20px;background:#1e293b;color:#fff;">
        <div style="font-size:64px;margin-bottom:12px;">✅</div>
        <h2 style="margin:0 0 8px;font-weight:600;">Update Complete!</h2>
        <p style="color:#94a3b8;font-size:0.95rem;margin:0;">Your app has been updated successfully.</p>
      </div>\`;`
    );
    setTimeout(() => {
      if (updateProgressWindow && !updateProgressWindow.isDestroyed()) {
        updateProgressWindow.close();
      }
    }, 3000);
  }
}

function pollServerAndLoad(win) {
  const checkServer = () => {
    http.get('http://127.0.0.1:8080', (res) => {
      // Accept 2xx or 3xx (redirects from Spring Security)
      if (res.statusCode < 400) {
        // If there was an update progress window, show complete
        if (updateProgressWindow && !updateProgressWindow.isDestroyed()) {
          showUpdateCompleteWindow();
        }
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

  ipcMain.on('show-update-progress', () => {
    showUpdateProgressWindow();
  });

  ipcMain.on('hide-update-progress', () => {
    if (updateProgressWindow && !updateProgressWindow.isDestroyed()) {
      updateProgressWindow.close();
    }
  });

  mainWindow.webContents.on('console-message', (event, level, message, line, sourceId) => {
    console.log(`[Browser Console]: ${message}`);
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

  // Open DevTools in development only
  if (!isPackaged) {
    mainWindow.webContents.openDevTools();
  }

  pollServerAndLoad(mainWindow);
});

app.on('will-quit', () => {
  if (javaProcess) {
    javaProcess.kill('SIGTERM');
    // Give Java a moment to shut down cleanly
    setTimeout(() => {
      if (javaProcess && !javaProcess.killed) {
        javaProcess.kill('SIGKILL');
      }
    }, 5000);
  }
  if (updateProgressWindow && !updateProgressWindow.isDestroyed()) {
    updateProgressWindow.close();
  }
});