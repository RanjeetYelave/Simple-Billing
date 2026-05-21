const puppeteer = require('puppeteer');

(async () => {
  const browser = await puppeteer.launch();
  const page = await browser.newPage();
  
  page.on('console', msg => console.log('PAGE LOG:', msg.text()));
  page.on('pageerror', error => console.log('PAGE ERROR:', error.message));

  await page.goto('http://127.0.0.1:8080', { waitUntil: 'networkidle0' });
  
  // Navigate to backup page
  await page.evaluate(() => {
    // find Backup & Restore in sidebar
    const items = document.querySelectorAll('.sidebar-item');
    for (const item of items) {
      if (item.innerText.includes('Backup & Restore')) {
        item.click();
      }
    }
  });
  
  await new Promise(r => setTimeout(r, 1000));
  
  // Click Factory Reset
  await page.evaluate(() => {
    const btns = document.querySelectorAll('.btn-danger');
    for (const btn of btns) {
      if (btn.innerText.includes('Factory Reset')) {
        btn.click();
      }
    }
  });

  await new Promise(r => setTimeout(r, 1000));

  // Click Yes, Delete Everything
  await page.evaluate(() => {
    const btns = document.querySelectorAll('.btn-danger');
    for (const btn of btns) {
      if (btn.innerText.includes('Yes, Delete Everything')) {
        btn.click();
      }
    }
  });

  // Wait 12 seconds for countdown
  await new Promise(r => setTimeout(r, 12000));
  
  // Type reset software and submit
  await page.evaluate(() => {
    const inputs = document.querySelectorAll('input');
    for (const input of inputs) {
      if (input.placeholder.includes('Type "reset software"')) {
        // simulate typing using react events?
        const nativeInputValueSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value").set;
        nativeInputValueSetter.call(input, 'reset software');
        const ev2 = new Event('input', { bubbles: true});
        input.dispatchEvent(ev2);
      }
    }
    
    setTimeout(() => {
        const btns = document.querySelectorAll('.btn-danger');
        for (const btn of btns) {
          if (btn.innerText.includes('Confirm Reset')) {
            btn.click();
          }
        }
    }, 500);
  });
  
  await new Promise(r => setTimeout(r, 2000));
  
  await browser.close();
})();
