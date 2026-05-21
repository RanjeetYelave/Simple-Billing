const fetch = require('node-fetch');

async function test() {
    // 1. Create firm
    let res = await fetch('http://127.0.0.1:8080/api/firms', { method: 'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({firmName: "Test Firm"}) });
    let firm = await res.json();
    let firmId = firm.id;
    
    // 2. Create customer
    res = await fetch('http://127.0.0.1:8080/api/customers', { method: 'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({firmId: firmId, name: "Test Customer"}) });
    let customer = await res.json();
    
    // 3. Create product
    res = await fetch('http://127.0.0.1:8080/api/products', { method: 'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({firmId: firmId, name: "Test Product", price: 100}) });
    let product = await res.json();
    
    // 4. Create invoice
    let invoiceData = {
        firmId: firmId,
        customer: { id: customer.id },
        items: [ { product: { id: product.id }, qty: 1, pricePerUnit: 100 } ],
        totalAmount: 100
    };
    res = await fetch('http://127.0.0.1:8080/api/invoices', { method: 'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify(invoiceData) });
    let invoice = await res.json();
    
    console.log("Created all data, invoice ID:", invoice.id);
    
    // 5. Factory reset
    res = await fetch('http://127.0.0.1:8080/api/backup/factory-reset', { method: 'POST' });
    console.log("Reset response:", res.status, await res.text());
}
test().catch(console.error);
