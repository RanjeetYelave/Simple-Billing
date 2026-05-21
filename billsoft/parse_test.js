const fs = require('fs');
const html = fs.readFileSync('src/main/webapp/index.html', 'utf8');
const match = html.match(/<script type="text\/babel">([\s\S]*?)<\/script>/);
if (match) {
    const js = match[1];
    fs.writeFileSync('extracted.js', js);
    try {
        new (require('vm').Script)(js);
        console.log("No syntax errors found by Node.js VM!");
    } catch (e) {
        console.log(e.stack);
    }
}
