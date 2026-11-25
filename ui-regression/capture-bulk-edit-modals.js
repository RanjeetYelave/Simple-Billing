const { chromium } = require('./node_modules/playwright');
const path = require('path');

const ARTIFACTS_DIR = '/Users/afk/.gemini/antigravity/brain/567f747c-7b7a-4744-af72-b83d7bd28021';
const FIXTURE_PATH = 'file://' + path.resolve(__dirname, 'fixtures/test_bulk_edit_modal.html');

async function capture() {
  console.log('Launching headless browser with Chromium...');
  const browser = await chromium.launch({ headless: true });

  const viewports = [
    { name: '1366', width: 1366, height: 768 },
    { name: '1920', width: 1920, height: 1080 }
  ];

  for (const vp of viewports) {
    console.log(`\nCapturing at viewport ${vp.width}x${vp.height}...`);
    const context = await browser.newContext({ viewport: { width: vp.width, height: vp.height } });
    const page = await context.newPage();

    // 1. Capture Product Master Bulk Edit Modal
    await page.goto(FIXTURE_PATH, { waitUntil: 'load' });
    await page.evaluate(() => window.renderModal('PRODUCT_MASTER'));
    await page.waitForTimeout(600);

    const pmScreenshotPath = path.join(ARTIFACTS_DIR, `product_master_bulk_edit_${vp.name}.png`);
    await page.screenshot({ path: pmScreenshotPath, fullPage: false });
    console.log(`Saved Product Master Bulk Edit screenshot: ${pmScreenshotPath}`);

    // 2. Capture Inventory Bulk Edit Modal
    await page.goto(FIXTURE_PATH, { waitUntil: 'load' });
    await page.evaluate(() => window.renderModal('INVENTORY'));
    await page.waitForTimeout(600);

    const invScreenshotPath = path.join(ARTIFACTS_DIR, `inventory_bulk_edit_${vp.name}.png`);
    await page.screenshot({ path: invScreenshotPath, fullPage: false });
    console.log(`Saved Inventory Bulk Edit screenshot: ${invScreenshotPath}`);

    await context.close();
  }

  await browser.close();
  console.log('\nAll modal screenshots captured successfully!');
}

capture().catch(err => {
  console.error('Capture error:', err);
  process.exit(1);
});
