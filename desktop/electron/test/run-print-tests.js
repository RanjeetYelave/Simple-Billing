const { spawnSync } = require('child_process');
const path = require('path');
const electronPath = require('electron');

console.log('--- Launching RupeeCRM Direct Print Pipeline Verification Suite ---');
const testScript = path.join(__dirname, 'verify-direct-print-pipeline.js');
const env = { ...process.env };
delete env.ELECTRON_RUN_AS_NODE;

const res = spawnSync(electronPath, [testScript], {
  stdio: 'inherit',
  env: env
});

if (res.status !== 0) {
  process.exit(res.status || 1);
}

