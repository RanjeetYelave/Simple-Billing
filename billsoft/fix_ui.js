const fs = require('fs');
let html = fs.readFileSync('src/main/webapp/index.html', 'utf8');

// 1. Fix sidebar icon
html = html.replace(
  "{ id: 'employees', label: 'Employees', icon: '<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2\"></path><circle cx=\"9\" cy=\"7\" r=\"4\"></circle><path d=\"M23 21v-2a4 4 0 0 0-3-3.87\"></path><path d=\"M16 3.13a4 4 0 0 1 0 7.75\"></path></svg>' },",
  "{ id: 'employees', label: 'Employees', icon: Icons.users },"
);

// 2. Fix EmployeeManager UI header
const oldHeader = "React.createElement('div', { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 } },\n          React.createElement('h2', { style: { fontSize: '1.5rem', fontWeight: 600 } }, 'Employee Management'),";

const newHeader = "React.createElement('div', { className: 'manager-header' },\n          React.createElement('div', { className: 'card-title' }, 'Employees'),";

html = html.replace(oldHeader, newHeader);

// 3. Fix Fragment wrapping
const oldReturn = "return React.createElement('div', null,\n        React.createElement('div', { className: 'manager-header' }";
const newReturn = "return React.createElement(Fragment, null,\n        React.createElement('div', { className: 'manager-header' }";

html = html.replace(oldReturn, newReturn);

fs.writeFileSync('src/main/webapp/index.html', html);
console.log("Fixed UI");
