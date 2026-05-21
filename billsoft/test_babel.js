const babel = require('@babel/core');
const fs = require('fs');

const code = fs.readFileSync('extracted.js', 'utf8');

try {
  babel.transformSync(code, {
    presets: ['@babel/preset-react'],
    filename: 'extracted.js'
  });
  console.log("Babel parse OK");
} catch (e) {
  console.log("BABEL ERROR:", e.message);
}
