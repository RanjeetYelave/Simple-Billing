const fs = require('fs');
let code = fs.readFileSync('src/main/webapp/js/api.js', 'utf8');

const apis = `    getSalaries: (id) => API._json(\`/api/employees/\${id}/salaries\`),
    processSalary: (id, data) => API._json(\`/api/employees/\${id}/salaries\`, { method: 'POST', body: data }),
    getPromotions: (id) => API._json(\`/api/employees/\${id}/promotions\`),
    addPromotion: (id, data) => API._json(\`/api/employees/\${id}/promotions\`, { method: 'POST', body: data })
  },`;

code = code.replace(`    getSalaries: (id) => API._json(\`/api/employees/\${id}/salaries\`),
    processSalary: (id, data) => API._json(\`/api/employees/\${id}/salaries\`, { method: 'POST', body: data })
  },`, apis);

fs.writeFileSync('src/main/webapp/js/api.js', code);
console.log("Patched api.js");
