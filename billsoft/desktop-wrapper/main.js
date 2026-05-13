const { app, BrowserWindow } = require('electron');
const { spawn } = require('child_process');
const path = require('path');
const fs = require('fs');
const http = require('http');

let mainWindow;
let javaProcess;

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

  javaProcess = spawn(javaExecutable, ['-jar', warPath], { cwd: backendDir });

  javaProcess.stdout.on('data', (data) => {
    console.log(data.toString());
  });

  javaProcess.stderr.on('data', (data) => {
    console.error(data.toString());
  });

  javaProcess.on('exit', (code) => {
    console.log(`Java process exited with code ${code}`);

    if (code === 0) {
      if (fs.existsSync(updateWarPath)) {
        console.log("Update detected! Waiting for file locks to release...");

        setTimeout(() => {
          try {
            if (fs.existsSync(warPath)) {
              fs.unlinkSync(warPath);
            }
            fs.renameSync(updateWarPath, warPath);
            console.log("Swap complete! Restarting backend...");
          } catch (err) {
            console.error("Failed to swap update files:", err);
          }

          startJavaBackend();

          if (mainWindow) {
            setTimeout(() => {
              mainWindow.reload();
            }, 5000);
          }
        }, 2000);
      }
    }
  });
}

function pollServerAndLoad(win) {
  const checkServer = () => {
    http.get('http://127.0.0.1:8080', (res) => {
      // Accept 200 OK or 302 Redirect (often happens if Spring Security is active)
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
      webSecurity: false
    }
  });

  mainWindow.webContents.on('console-message', (event, level, message, line, sourceId) => {
    console.log(`[Browser Console]: ${message}`);
  });

  const loadingHtml = '<h2 style="font-family:sans-serif;padding:20px;">Starting Billsoft Engine... Please wait.</h2>';
  mainWindow.loadURL('data:text/html;charset=utf-8,' + encodeURIComponent(loadingHtml));

  mainWindow.webContents.openDevTools();

  pollServerAndLoad(mainWindow);
});

app.on('will-quit', () => {
  if (javaProcess) {
    javaProcess.kill();
  }
});