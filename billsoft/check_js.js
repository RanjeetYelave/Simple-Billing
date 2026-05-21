const fs = require('fs');
const html = fs.readFileSync('src/main/webapp/index.html', 'utf8');
const match = html.match(/<script type="text\/babel">([\s\S]*?)<\/script>/);
if (match) {
    const js = match[1];
    const lines = js.split('\n');
    
    // Check for common babel syntax issues
    let issues = [];
    
    // Find the closing of App function and ReactDOM rendering
    const rootCallIdx = js.indexOf('ReactDOM.createRoot');
    console.log('ReactDOM.createRoot found:', rootCallIdx >= 0);
    
    if (rootCallIdx < 0) {
        issues.push('ReactDOM.createRoot not found!');
    }
    
    // Check function definitions are complete
    let braceCount = 0;
    let parenCount = 0;
    for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        // Count braces
        for (const ch of line) {
            if (ch === '{') braceCount++;
            if (ch === '}') braceCount--;
            if (ch === '(') parenCount++;
            if (ch === ')') parenCount--;
        }
    }
    
    console.log('Final brace count:', braceCount);
    console.log('Final paren count:', parenCount);
    console.log('Total lines:', lines.length);
    
    if (braceCount !== 0) {
        issues.push(`Unbalanced braces! Count: ${braceCount}`);
    }
    if (parenCount !== 0) {
        issues.push(`Unbalanced parentheses! Count: ${parenCount}`);
    }
    
    // Check for the last line being proper
    const lastLine = lines[lines.length - 1].trim();
    console.log('Last line:', lastLine);
    
    // Look for common issues in React.createElement calls spanning multiple lines
    console.log('Issues found:', issues.length ? issues : 'None');
} else {
    console.log('No script tag found!');
}