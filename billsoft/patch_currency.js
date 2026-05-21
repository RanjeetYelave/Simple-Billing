const fs = require('fs');
let html = fs.readFileSync('src/main/webapp/index.html', 'utf8');

html = html.replace(/formatCurrency\(/g, 'BillsoftUtils.formatCurrency(');

// Wait, what if I replaced it multiple times? I should be careful.
// Let's replace ONLY where it says " formatCurrency(" or " `-${formatCurrency(" or ` \${formatCurrency` etc.
// Better, just replace `formatCurrency(` with `BillsoftUtils.formatCurrency(` 
// but wait, does `BillsoftUtils.formatCurrency` get replaced to `BillsoftUtils.BillsoftUtils.formatCurrency`?
// Yes if I just blindly replace.
html = html.replace(/BillsoftUtils\.formatCurrency\(/g, 'FORMAT_CURRENCY_PLACEHOLDER(');
html = html.replace(/formatCurrency\(/g, 'BillsoftUtils.formatCurrency(');
html = html.replace(/FORMAT_CURRENCY_PLACEHOLDER\(/g, 'BillsoftUtils.formatCurrency(');

fs.writeFileSync('src/main/webapp/index.html', html);
console.log("Patched formatCurrency");
