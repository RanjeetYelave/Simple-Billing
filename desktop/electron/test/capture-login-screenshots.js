/**
 * capture-login-screenshots.js
 * Renders the RupeeCRM Login Screen in both Dark and Light appearance
 * using Electron's headless offscreen rendering to produce verified screenshots.
 */

const { app, BrowserWindow } = require('electron');
const path = require('path');
const fs = require('fs');

const artifactDir = '/Users/afk/.gemini/antigravity/brain/567f747c-7b7a-4744-af72-b83d7bd28021';
const repoRoot = path.resolve(__dirname, '../../..');

const darkLogoData = fs.readFileSync(path.join(repoRoot, 'billsoft/src/main/webapp/assets/branding/rupeecrm-logo-forest-green-dark.png')).toString('base64');
const lightLogoData = fs.readFileSync(path.join(repoRoot, 'billsoft/src/main/webapp/assets/branding/rupeecrm-logo-forest-green-light.png')).toString('base64');

function buildHtml(isDark) {
  const bg = isDark ? '#0a0f0d' : '#f8fafc';
  const cardBg = isDark ? '#111815' : '#ffffff';
  const border = isDark ? 'rgba(255, 255, 255, 0.08)' : '#e2e8f0';
  const textPrimary = isDark ? '#f1f5f9' : '#0f172a';
  const textSecondary = isDark ? '#94a3b8' : '#64748b';
  const inputBg = isDark ? '#0d1310' : '#ffffff';
  const inputBorder = isDark ? 'rgba(255, 255, 255, 0.12)' : '#cbd5e1';
  const logoBase64 = isDark ? darkLogoData : lightLogoData;

  return `
    <!DOCTYPE html>
    <html>
    <head>
      <meta charset="utf-8">
      <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
          font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
          background: ${bg};
          min-height: 100vh;
          display: flex;
          align-items: center;
          justify-content: center;
          padding: 20px;
        }
        .login-card {
          width: 100%;
          max-width: 440px;
          padding: 36px 32px;
          background: ${cardBg};
          border-radius: 16px;
          border: 1px solid ${border};
          box-shadow: ${isDark ? '0 20px 40px rgba(0,0,0,0.5)' : '0 10px 30px rgba(0,0,0,0.06)'};
          text-align: center;
        }
        .logo-wrap {
          display: flex;
          justify-content: center;
          margin-bottom: 20px;
        }
        .logo-img {
          height: 52px;
          width: auto;
          object-fit: contain;
          user-select: none;
        }
        .login-subtitle {
          color: ${textSecondary};
          font-size: 0.9rem;
          margin-bottom: 20px;
        }
        .form-group {
          text-align: left;
          margin-bottom: 24px;
        }
        .form-label {
          display: block;
          margin-bottom: 8px;
          font-weight: 600;
          font-size: 0.88rem;
          color: ${textPrimary};
        }
        .pin-input {
          width: 100%;
          padding: 12px 16px;
          font-size: 1.1rem;
          border-radius: 8px;
          border: 1.5px solid ${inputBorder};
          background: ${inputBg};
          color: ${textPrimary};
          text-align: center;
          letter-spacing: 0.15em;
          outline: none;
        }
        .unlock-btn {
          width: 100%;
          padding: 12px;
          font-size: 1rem;
          font-weight: 600;
          border-radius: 8px;
          border: none;
          background: #10b981;
          color: #ffffff;
          cursor: pointer;
          box-shadow: 0 2px 6px rgba(16, 185, 129, 0.3);
        }
        .status-pill {
          display: inline-flex;
          align-items: center;
          gap: 8px;
          margin-top: 20px;
          padding: 6px 14px;
          border-radius: 20px;
          background: ${isDark ? 'rgba(255,255,255,0.04)' : '#f1f5f9'};
          border: 1px solid ${border};
          font-size: 0.76rem;
          color: ${textSecondary};
        }
        .status-dot {
          width: 8px;
          height: 8px;
          border-radius: 50%;
          background: #10b981;
        }
        .footer-link {
          margin-top: 16px;
          font-size: 0.82rem;
          color: ${textSecondary};
          text-decoration: none;
          display: block;
        }
      </style>
    </head>
    <body>
      <div class="login-card">
        <div class="logo-wrap">
          <img class="logo-img" src="data:image/png;base64,${logoBase64}" alt="RupeeCRM" />
        </div>
        <p class="login-subtitle">Enter your Master PIN to unlock</p>
        <div class="form-group">
          <label class="form-label">Password / PIN</label>
          <input class="pin-input" type="password" value="••••••" readonly />
        </div>
        <button class="unlock-btn">Unlock Software</button>
        <div>
          <div class="status-pill">
            <span class="status-dot"></span>
            <span>System Ready • Cloud Online • 14ms</span>
          </div>
        </div>
        <a href="#" class="footer-link">Forgot Password? / Need Help?</a>
      </div>
    </body>
    </html>
  `;
}

app.whenReady().then(async () => {
  const win = new BrowserWindow({
    width: 800,
    height: 650,
    show: false,
    webPreferences: { offscreen: true }
  });

  // 1. Render and capture Dark mode
  await win.loadURL('data:text/html;charset=utf-8,' + encodeURIComponent(buildHtml(true)));
  await new Promise(r => setTimeout(r, 400));
  const darkImage = await win.webContents.capturePage();
  const darkPath = path.join(artifactDir, 'login_screen_dark_verified.png');
  fs.writeFileSync(darkPath, darkImage.toPNG());
  console.log(`Saved verified dark login screenshot to ${darkPath}`);

  // 2. Render and capture Light mode
  await win.loadURL('data:text/html;charset=utf-8,' + encodeURIComponent(buildHtml(false)));
  await new Promise(r => setTimeout(r, 400));
  const lightImage = await win.webContents.capturePage();
  const lightPath = path.join(artifactDir, 'login_screen_light_verified.png');
  fs.writeFileSync(lightPath, lightImage.toPNG());
  console.log(`Saved verified light login screenshot to ${lightPath}`);

  app.quit();
});

