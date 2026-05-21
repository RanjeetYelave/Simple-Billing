const fs = require('fs');
let html = fs.readFileSync('src/main/webapp/index.html', 'utf8');

const errorBoundaryCode = `
    class ErrorBoundary extends React.Component {
      constructor(props) {
        super(props);
        this.state = { hasError: false, error: null, errorInfo: null };
      }
      static getDerivedStateFromError(error) {
        return { hasError: true };
      }
      componentDidCatch(error, errorInfo) {
        this.setState({ error, errorInfo });
        console.error("React Error Boundary caught an error:", error, errorInfo);
      }
      render() {
        if (this.state.hasError) {
          return React.createElement('div', { style: { padding: '40px', textAlign: 'center', color: 'var(--danger)' } },
            React.createElement('h2', null, 'Something went wrong rendering this module.'),
            React.createElement('p', null, this.state.error && this.state.error.toString()),
            React.createElement('button', { className: 'btn btn-primary', onClick: () => this.setState({ hasError: false }), style: { marginTop: '20px' } }, 'Try Again')
          );
        }
        return this.props.children;
      }
    }

    // ─── UTILS ───
`;

html = html.replace('    // ─── UTILS ───', errorBoundaryCode);

// Now wrap the main content in ErrorBoundary
const renderMain = `React.createElement('main', { className: 'main-content' },
          React.createElement('header', { className: 'topbar' },
            React.createElement('div', { className: 'topbar-left' }, React.createElement('h1', { className: 'topbar-title' }, current.title)),
            React.createElement('div', { className: 'topbar-right' },
              authEnabled && React.createElement('div', { className: 'user-profile' },
                React.createElement('div', { className: 'avatar' }, 'A'),
                React.createElement('span', null, 'Admin')
              )
            )
          ),
          React.createElement('div', { className: 'content-area' },
            React.createElement(ErrorBoundary, { key: page }, current.comp)
          )
        )`;

const oldRenderMainPattern = /React\.createElement\('main', \{ className: 'main-content' \},[\s\S]*?current\.comp\n\s*\)\n\s*\)/;
html = html.replace(oldRenderMainPattern, renderMain);

fs.writeFileSync('src/main/webapp/index.html', html);
console.log("Patched ErrorBoundary");
