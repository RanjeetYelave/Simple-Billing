const puppeteer = require('puppeteer');

(async () => {
  const browser = await puppeteer.launch({ headless: "new" });
  const page = await browser.newPage();
  
  // Set up console listener to see what's happening
  page.on('console', msg => console.log('PAGE LOG:', msg.text()));
  
  try {
    await page.goto('http://localhost:8080/');
    
    // Wait for Invoices tab to be available
    await page.waitForSelector('button');
    
    // Evaluate to click the 'Invoices' tab or just find a print button
    // Let's first ensure we have an invoice
    // Actually we can just run the function directly
    const errorMsg = await page.evaluate(async () => {
      try {
        // Find an invoice ID or just use 1
        const invoices = await API.invoices.listFinal();
        if (invoices.length === 0) {
          // create one
          await API.invoices.create({
            customerId: 1, items: [{ productId: null, productName: "Test", qty: 1, pricePerUnit: 100 }], status: "FINAL"
          });
        }
        const invs = await API.invoices.listFinal();
        const id = invs[0].id;
        
        // Call downloadPdf
        try {
          const blob = await API.invoices.downloadPdf(id);
          BillsoftUtils.printBlob(blob);
          return "SUCCESS";
        } catch (e) {
          return "ERROR: " + e.message + "\n" + e.stack;
        }
      } catch (e) {
        return "SETUP ERROR: " + e.message;
      }
    });
    
    console.log("Result:", errorMsg);
    
    // Wait a bit for pdf to render
    await new Promise(r => setTimeout(r, 2000));
    await page.screenshot({ path: 'test_screenshot.png' });
    console.log("Screenshot saved.");
    
  } catch(e) {
    console.log("Script error:", e);
  } finally {
    await browser.close();
  }
})();
