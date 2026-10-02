/**
 * TakeABreakHub.js
 * Billsoft Take a Break Hub Component.
 * Hosts the full-viewport Snake Classic experience enclosed within a sandboxed React ErrorBoundary.
 * Ensures 100% crash isolation from billing, invoicing, and accounting workflows.
 */

(function (root, factory) {
  if (typeof define === 'function' && define.amd) {
    define([
      'react',
      './SnakeClassic',
      './MaharashtraAtmosphere',
      './MaharashtraJourney',
      './ProgressionExperience',
      '../maharashtraWorld'
    ], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory(
      require('react'),
      require('./SnakeClassic'),
      require('./MaharashtraAtmosphere'),
      require('./MaharashtraJourney'),
      require('./ProgressionExperience'),
      require('../maharashtraWorld')
    );
  } else {
    root.TakeABreakHub = factory(
      root.React,
      root.SnakeClassic,
      root.MaharashtraAtmosphere,
      root.MaharashtraJourney,
      root.ProgressionExperience,
      root.MaharashtraWorld
    );
  }
}(typeof self !== 'undefined' ? self : this, function (
  React,
  SnakeClassic,
  MaharashtraAtmosphere,
  MaharashtraJourney,
  ProgressionExperience,
  MaharashtraWorld
) {
  'use strict';

  const { Component, useState, useEffect, useMemo } = React;
  const SnakeComp = SnakeClassic || (typeof window !== 'undefined' ? window.SnakeClassic : null);
  const AtmosphereComp = MaharashtraAtmosphere || (typeof window !== 'undefined' ? window.MaharashtraAtmosphere : null);
  const JourneyComp = MaharashtraJourney || (typeof window !== 'undefined' ? window.MaharashtraJourney : null);
  const ProgressionComp = ProgressionExperience || (typeof window !== 'undefined' ? window.ProgressionExperience : null);
  const WorldEngine = MaharashtraWorld || (typeof window !== 'undefined' ? window.MaharashtraWorld : null);

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
    const [activeLevel, setActiveLevel] = useState(1);
    const [isSnakeEnabled, setIsSnakeEnabled] = useState(() => {
      try {
        if (typeof localStorage === 'undefined') return true;
        return localStorage.getItem('CONFIG_SNAKE_CLASSIC_ENABLED') !== 'false' && localStorage.getItem('CONFIG_CIRCUIT_CONNECT_ENABLED') !== 'false';
      } catch (e) {
        return true;
      }
    });

    useEffect(() => {
      const handleProgression = (e) => {
        if (e && e.detail && typeof e.detail.level === 'number') {
          setActiveLevel(Math.max(1, e.detail.level));
        }
      };
      const handleToggle = () => {
        try {
          if (typeof localStorage === 'undefined') return;
          const enabled = localStorage.getItem('CONFIG_SNAKE_CLASSIC_ENABLED') !== 'false' && localStorage.getItem('CONFIG_CIRCUIT_CONNECT_ENABLED') !== 'false';
          setIsSnakeEnabled(enabled);
        } catch (e) {}
      };

      window.addEventListener('billsoft:game-progression', handleProgression);
      window.addEventListener('billsoft:snake-toggle', handleToggle);
      window.addEventListener('billsoft:circuit-toggle', handleToggle);

      return () => {
        window.removeEventListener('billsoft:game-progression', handleProgression);
        window.removeEventListener('billsoft:snake-toggle', handleToggle);
        window.removeEventListener('billsoft:circuit-toggle', handleToggle);
      };
    }, []);

    const worldData = useMemo(() => {
      if (!WorldEngine) return null;
      try {
        return WorldEngine.getWorldForLevel('classic', activeLevel);
      } catch (e) {
        return null;
      }
    }, [activeLevel]);

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
          'Snake Classic — Maharashtra Expedition is currently turned off. You can re-enable it anytime from Settings → Appearance.'
        )
      );
    }

    return React.createElement('div', {
      className: 'take-a-break-hub-wrapper',
      style: {
        position: 'relative',
        height: '100%',
        maxHeight: '100%',
        width: '100%',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        overflow: 'hidden',
        boxSizing: 'border-box',
        padding: '2px 8px 4px 8px',
        flex: 1
      }
    },
      // Layer 1: Ambient Regional Atmosphere Silhouette Backdrop
      AtmosphereComp && React.createElement(AtmosphereComp, {
        location: worldData ? worldData.location : null,
        gameType: 'classic'
      }),

      // Layer 2: Interactive Maharashtra Journey Route & World Map Modal
      JourneyComp && React.createElement(JourneyComp, {
        currentLevel: activeLevel,
        gameType: 'classic'
      }),

      // Layer 3: Dynamic Full-Viewport Snake Game Container
      React.createElement('div', {
        className: 'take-a-break-game-container',
        style: {
          width: '100%',
          flex: 1,
          minHeight: 0,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          position: 'relative',
          boxSizing: 'border-box',
          overflow: 'hidden',
          zIndex: 1
        }
      },
        SnakeComp ? React.createElement(SnakeComp, { key: 'snake_classic_view' }) : React.createElement('div', null, 'Loading Snake...')
      ),

      // Layer 4: Sandboxed Progression, Travel Transition & Reward Controller
      ProgressionComp && ProgressionComp.ProgressionExperienceController && React.createElement(ProgressionComp.ProgressionExperienceController, {
        worldData,
        gameType: 'classic'
      })
    );
  }

  function TakeABreakHub() {
    return React.createElement(TakeABreakErrorBoundary, null,
      React.createElement(TakeABreakHubInner, null)
    );
  }

  return TakeABreakHub;
}));
