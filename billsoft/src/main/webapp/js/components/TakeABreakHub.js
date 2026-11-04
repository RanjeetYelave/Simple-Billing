/**
 * TakeABreakHub.js
 * Billsoft Take a Break Hub Component.
 * Hosts the Snake Classic gameplay experience enclosed within a sandboxed React ErrorBoundary.
 * Ensures 100% crash isolation from billing, invoicing, and accounting workflows.
 */

(function (root, factory) {
  if (typeof define === 'function' && define.amd) {
    define(['react', './SnakeClassic'], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory(
      require('react'),
      require('./SnakeClassic')
    );
  } else {
    root.TakeABreakHub = factory(
      root.React,
      root.SnakeClassic
    );
  }
}(typeof self !== 'undefined' ? self : this, function (React, SnakeClassic) {
  'use strict';

  const { Component, useState, useEffect } = React;
  const SnakeComp = SnakeClassic || (typeof window !== 'undefined' ? window.SnakeClassic : null);

  /**
   * React ErrorBoundary isolating Take a Break mini-game from the core billing application
   */
  class TakeABreakErrorBoundary extends Component {
    constructor(props) {
      super(props);
      this.state = { hasError: false, error: null };
    }

    static getDerivedStateFromError(error) {
      return { hasError: true, error };
    }

    componentDidCatch(error, errorInfo) {
      try {
        console.warn('[TakeABreak] Sandboxed non-critical mini-game error caught:', error, errorInfo);
        if (typeof window !== 'undefined' && typeof window.__billsoftReportNonCriticalError === 'function') {
          window.__billsoftReportNonCriticalError('TakeABreak', error);
        }
      } catch (e) {}
    }

    handleRetry = () => {
      this.setState({ hasError: false, error: null });
    };

    render() {
      if (this.state.hasError) {
        return React.createElement('div', {
          className: 'card',
          style: {
            padding: 36,
            textAlign: 'center',
            maxWidth: 520,
            margin: '40px auto',
            background: 'var(--bg-card, #1e293b)',
            border: '1px solid var(--border, #334155)',
            borderRadius: 16,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: 14,
            boxShadow: '0 10px 30px rgba(0,0,0,0.3)'
          }
        },
          React.createElement('div', { style: { fontSize: '2.8rem' } }, '🐍'),
          React.createElement('h3', { style: { margin: 0, fontWeight: 800, color: 'var(--text)' } }, 'Take a Break is Temporarily Unavailable'),
          React.createElement('p', { style: { margin: 0, fontSize: '0.92rem', color: 'var(--text-secondary)' } },
            'A non-critical issue occurred while loading Snake. Your invoices, customer records, and core billing functions remain 100% operational.'
          ),
          React.createElement('button', {
            type: 'button',
            className: 'btn btn-primary',
            onClick: this.handleRetry,
            style: { marginTop: 8, padding: '8px 24px', fontWeight: 700, borderRadius: 8 }
          }, 'Reload Snake')
        );
      }
      return this.props.children;
    }
  }

  function TakeABreakHubInner() {
    const [isSnakeEnabled, setIsSnakeEnabled] = useState(() => {
      try {
        if (typeof localStorage === 'undefined') return true;
        return localStorage.getItem('CONFIG_SNAKE_CLASSIC_ENABLED') !== 'false';
      } catch (e) {
        return true;
      }
    });

    useEffect(() => {
      const handleToggle = (e) => {
        try {
          if (e && e.detail && typeof e.detail.enabled === 'boolean') {
            setIsSnakeEnabled(e.detail.enabled);
          } else if (typeof localStorage !== 'undefined') {
            setIsSnakeEnabled(localStorage.getItem('CONFIG_SNAKE_CLASSIC_ENABLED') !== 'false');
          }
        } catch (err) {}
      };

      window.addEventListener('billsoft:snake-toggle', handleToggle);
      window.addEventListener('billsoft:circuit-toggle', handleToggle);

      return () => {
        window.removeEventListener('billsoft:snake-toggle', handleToggle);
        window.removeEventListener('billsoft:circuit-toggle', handleToggle);
      };
    }, []);

    if (!isSnakeEnabled) {
      return React.createElement('div', {
        className: 'card',
        style: {
          padding: 36,
          textAlign: 'center',
          maxWidth: 520,
          margin: '40px auto',
          background: 'var(--bg-card, #1e293b)',
          border: '1px solid var(--border, #334155)',
          borderRadius: 16,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: 14,
          boxShadow: '0 10px 30px rgba(0,0,0,0.3)'
        }
      },
        React.createElement('div', { style: { fontSize: '2.8rem' } }, '🐍'),
        React.createElement('h3', { style: { margin: 0, fontWeight: 800, color: 'var(--text)' } }, 'Snake Classic is Disabled'),
        React.createElement('p', { style: { margin: 0, fontSize: '0.92rem', color: 'var(--text-secondary)', lineHeight: 1.5 } },
          'Snake Classic is currently turned off. You can re-enable it anytime from Settings.'
        )
      );
    }

    return React.createElement('div', {
      className: 'take-a-break-hub-wrapper'
    },
      React.createElement('div', {
        className: 'take-a-break-game-container'
      },
        SnakeComp ? React.createElement(SnakeComp, { key: 'snake_classic_view' }) : React.createElement('div', null, 'Loading Snake...')
      )
    );
  }

  function TakeABreakHub() {
    return React.createElement(TakeABreakErrorBoundary, null,
      React.createElement(TakeABreakHubInner, null)
    );
  }

  return TakeABreakHub;
}));
