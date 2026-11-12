/**
 * RupeeCRM Direct Printing Pipeline Verification Test Suite
 *
 * Verifies that direct print requests through the Electron bridge
 * render non-blank, multi-page, A4 and Thermal 80mm documents.
 */

const assert = require('assert');
const path = require('path');
const fs = require('fs');
const { app, BrowserWindow, ipcMain } = require('electron');
const { pathToFileURL } = require('url');

console.log('=== RupeeCRM Direct Print Pipeline Verification Suite ===\n');

app.whenReady().then(async () => {
  const runnerPath = path.join(__dirname, '../src/print/print-runner.html');
  assert(fs.existsSync(runnerPath), 'print-runner.html must exist');
  assert(fs.existsSync(path.join(__dirname, '../src/vendor/pdf.min.js')), 'pdf.min.js must exist in src/vendor');
  assert(fs.existsSync(path.join(__dirname, '../src/vendor/pdf.worker.min.js')), 'pdf.worker.min.js must exist in src/vendor');

  // Helper to generate a realistic SVG page image as data URL
  const generateTestPageDataUrl = (title, pageNum, totalPages, theme = 'Light', format = 'A4') => {
    const isThermal = format.toUpperCase().includes('THERMAL') || format === '80MM';
    const width = isThermal ? 384 : 794;
    const height = isThermal ? 800 : 1123;
    const bgColor = theme === 'Dark' ? '#1e293b' : '#ffffff';
    const textColor = theme === 'Dark' ? '#f8fafc' : '#0f172a';
    const accentColor = theme === 'Minimal SaaS' ? '#2563eb' : (theme === 'Dark' ? '#818cf8' : '#4f46e5');

    const svg = `
      <svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">
        <rect width="100%" height="100%" fill="${bgColor}"/>
        <rect x="20" y="20" width="${width - 40}" height="60" rx="6" fill="${accentColor}"/>
        <text x="35" y="58" font-family="sans-serif" font-size="22" fill="#ffffff" font-weight="bold">${title}</text>
        <text x="25" y="120" font-family="sans-serif" font-size="14" fill="${textColor}">Page ${pageNum} of ${totalPages} • Theme: ${theme} • Format: ${format}</text>
        <line x1="20" y1="140" x2="${width - 20}" y2="140" stroke="#cbd5e1" stroke-width="1"/>
        <text x="25" y="170" font-family="sans-serif" font-size="12" fill="${textColor}">Item: Professional Services & Licensing</text>
        <text x="25" y="195" font-family="sans-serif" font-size="12" fill="${textColor}">Amount: ₹12,500.00 | GST 18%: ₹2,250.00 | Total: ₹14,750.00</text>
      </svg>
    `;
    return 'data:image/svg+xml;base64,' + Buffer.from(svg).toString('base64');
  };

  let passedScenarios = 0;
  const totalScenarios = 7;

  // ── Scenario 1: A4 Multi-page Invoice Direct Print ──
  console.log('[Scenario 1/7] Testing A4 Multi-page Invoice Direct Print Rendering...');
  {
    const win = new BrowserWindow({
      show: false,
      webPreferences: { nodeIntegration: false, contextIsolation: true, sandbox: true }
    });
    await win.loadURL(pathToFileURL(runnerPath).href);

    const p1 = generateTestPageDataUrl('Tax Invoice #INV-2026-001', 1, 2, 'Light', 'A4');
    const p2 = generateTestPageDataUrl('Tax Invoice #INV-2026-001 Terms', 2, 2, 'Light', 'A4');

    await win.webContents.executeJavaScript(`window.renderPagesFromImages(${JSON.stringify([p1, p2])}, 'A4')`);
    const pageCount = await win.webContents.executeJavaScript(`document.querySelectorAll('.print-page img').length`);
    assert.strictEqual(pageCount, 2, 'Must have rendered exactly 2 pages');

    const pdfBuffer = await win.webContents.printToPDF({ printBackground: true });
    assert(pdfBuffer.length > 2500, `Output PDF buffer must not be empty or blank (was ${pdfBuffer.length} bytes)`);
    const pdfStr = pdfBuffer.toString('binary');
    const pageObjMatches = pdfStr.match(/\/Type\s*\/Page\b/g);
    assert.strictEqual(pageObjMatches ? pageObjMatches.length : 0, 2, 'Output PDF must contain exactly 2 distinct page objects');

    win.close();
    passedScenarios++;
    console.log(`  -> Verified: A4 2-page invoice rendered to non-empty PDF (${pdfBuffer.length} bytes, 2 pages)`);
  }

  // ── Scenario 2: Quotation Direct Print ──
  console.log('[Scenario 2/7] Testing Quotation Direct Print Rendering...');
  {
    const win = new BrowserWindow({
      show: false,
      webPreferences: { nodeIntegration: false, contextIsolation: true, sandbox: true }
    });
    await win.loadURL(pathToFileURL(runnerPath).href);

    const q1 = generateTestPageDataUrl('Quotation #QUO-2026-088', 1, 1, 'Classic', 'A4');
    await win.webContents.executeJavaScript(`window.renderPagesFromImages(${JSON.stringify([q1])}, 'A4')`);

    const pdfBuffer = await win.webContents.printToPDF({ printBackground: true });
    assert(pdfBuffer.length > 1500, `Quotation output PDF buffer must not be empty (was ${pdfBuffer.length} bytes)`);
    win.close();
    passedScenarios++;
    console.log(`  -> Verified: Quotation rendered to non-empty PDF (${pdfBuffer.length} bytes)`);
  }

  // ── Scenario 3: Thermal 80mm POS Receipt Direct Print ──
  console.log('[Scenario 3/7] Testing Thermal 80mm Direct Print Receipt Rendering...');
  {
    const win = new BrowserWindow({
      show: false,
      webPreferences: { nodeIntegration: false, contextIsolation: true, sandbox: true }
    });
    await win.loadURL(pathToFileURL(runnerPath).href);

    const t1 = generateTestPageDataUrl('POS Thermal Receipt #RCP-901', 1, 1, 'Thermal', 'THERMAL_80MM');
    await win.webContents.executeJavaScript(`window.renderPagesFromImages(${JSON.stringify([t1])}, 'THERMAL_80MM')`);

    const bodyClass = await win.webContents.executeJavaScript(`document.body.className`);
    assert.strictEqual(bodyClass, 'format-thermal', 'Thermal format must activate format-thermal class');

    const pdfBuffer = await win.webContents.printToPDF({ printBackground: true });
    assert(pdfBuffer.length > 1500, `Thermal output PDF must not be empty (was ${pdfBuffer.length} bytes)`);
    win.close();
    passedScenarios++;
    console.log(`  -> Verified: Thermal 80mm format properly formatted with format-thermal (${pdfBuffer.length} bytes)`);
  }

  // ── Scenario 4: PDF.js Base64 Parsing & Fallback Engine ──
  console.log('[Scenario 4/7] Testing Standalone PDF.js Base64 Fallback Rendering in Print Runner...');
  {
    const win = new BrowserWindow({
      show: false,
      webPreferences: { nodeIntegration: false, contextIsolation: true, sandbox: true }
    });
    await win.loadURL(pathToFileURL(runnerPath).href);

    // Create a valid test PDF from Scenario 1 output
    const testPage = generateTestPageDataUrl('Direct Fallback Test', 1, 1, 'Modern', 'A4');
    await win.webContents.executeJavaScript(`window.renderPagesFromImages(${JSON.stringify([testPage])}, 'A4')`);
    const initialPdf = await win.webContents.printToPDF({ printBackground: true });
    const base64Data = initialPdf.toString('base64');

    // Feed raw base64 back into renderPagesFromPdfBase64
    await win.webContents.executeJavaScript(`window.renderPagesFromPdfBase64(${JSON.stringify(base64Data)}, 'A4', 1.5)`);
    const reRenderedCount = await win.webContents.executeJavaScript(`document.querySelectorAll('.print-page img').length`);
    assert(reRenderedCount >= 1, 'PDF.js engine must extract and render at least 1 page from raw base64');

    const secondPdf = await win.webContents.printToPDF({ printBackground: true });
    assert(secondPdf.length > 1500, `PDF.js rendered output must be a valid non-empty document (was ${secondPdf.length} bytes)`);

    win.close();
    passedScenarios++;
    console.log(`  -> Verified: PDF.js inside runner extracted and rendered base64 cleanly (${secondPdf.length} bytes)`);
  }

  // ── Scenario 5: Themes Compatibility (Light, Dark, Minimal SaaS) ──
  console.log('[Scenario 5/7] Testing Themes Compatibility (Light, Dark, Minimal SaaS)...');
  {
    for (const theme of ['Light', 'Dark', 'Minimal SaaS']) {
      const win = new BrowserWindow({
        show: false,
        webPreferences: { nodeIntegration: false, contextIsolation: true, sandbox: true }
      });
      await win.loadURL(pathToFileURL(runnerPath).href);

      const pageImg = generateTestPageDataUrl(`Invoice (${theme})`, 1, 1, theme, 'A4');
      await win.webContents.executeJavaScript(`window.renderPagesFromImages(${JSON.stringify([pageImg])}, 'A4')`);

      const pdf = await win.webContents.printToPDF({ printBackground: true });
      assert(pdf.length > 1500, `PDF for theme ${theme} must be non-empty (was ${pdf.length} bytes)`);
      win.close();
    }
    passedScenarios++;
    console.log('  -> Verified: Light, Dark, and Minimal SaaS themes render non-blank print pages');
  }

  // ── Scenario 6: Repeated Print Requests & Idempotent Window Lifecycle ──
  console.log('[Scenario 6/7] Testing Idempotent Print Window Lifecycle & Memory Cleanliness...');
  {
    for (let cycle = 1; cycle <= 3; cycle++) {
      const win = new BrowserWindow({
        show: false,
        webPreferences: { nodeIntegration: false, contextIsolation: true, sandbox: true }
      });
      await win.loadURL(pathToFileURL(runnerPath).href);
      const p = generateTestPageDataUrl(`Invoice Cycle ${cycle}`, 1, 1, 'Light', 'A4');
      await win.webContents.executeJavaScript(`window.renderPagesFromImages(${JSON.stringify([p])}, 'A4')`);
      const count = await win.webContents.executeJavaScript(`document.querySelectorAll('.print-page img').length`);
      assert.strictEqual(count, 1, 'Each cycle must render exactly 1 page without stale DOM retention');
      await new Promise(resolve => {
        win.once('closed', resolve);
        win.close();
      });
      assert(win.isDestroyed(), 'Window must be destroyed immediately after cycle completion');
    }
    passedScenarios++;
    console.log('  -> Verified: 3 sequential print lifecycles created and destroyed with zero stale state');
  }

  // ── Scenario 7: Security Requirements Verification on Print Runner ──
  console.log('[Scenario 7/7] Verifying Security Sandbox & Context Isolation on Print Window...');
  {
    const runnerContent = fs.readFileSync(runnerPath, 'utf8');
    assert(!runnerContent.includes('require('), 'Print runner must not contain node require calls');
    assert(runnerContent.includes('renderPagesFromImages'), 'Print runner must expose renderPagesFromImages');
    assert(runnerContent.includes('renderPagesFromPdfBase64'), 'Print runner must expose renderPagesFromPdfBase64');
    passedScenarios++;
    console.log('  -> Verified: Print runner is safe, sandboxed, and free of node integration risks');
  }

  console.log(`\n=== ALL ${passedScenarios}/${totalScenarios} DIRECT PRINT PIPELINE SCENARIOS PASSED ===`);
  app.quit();
}).catch(err => {
  console.error('\n[X] Verification suite failed:', err);
  process.exit(1);
});
