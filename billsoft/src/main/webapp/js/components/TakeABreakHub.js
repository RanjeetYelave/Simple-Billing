/**
 * TakeABreakHub.js
 * Unified Take a Break hub component hosting:
 * - Modern: Circuit Connect puzzle game
 * - Classic: Snake arcade game
 * - Maharashtra World & Journey Atmospheric Layer
 * - Interactive Maharashtra Journey Component & Map Modal
 * - Full React ErrorBoundary for complete sandboxed isolation from billing app
 * Fully isolated, responsive, and connected to OmniSearch subtab navigation.
 */

(function (root, factory) {
  if (typeof define === 'function' && define.amd) {
    define([
      'react',
      './CircuitConnect',
      './SnakeClassic',
      './MaharashtraAtmosphere',
      './MaharashtraJourney',
      '../maharashtraWorld',
      './ProgressionExperience'
    ], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory(
      require('react'),
      require('./CircuitConnect'),
      require('./SnakeClassic'),
      require('./MaharashtraAtmosphere'),
      require('./MaharashtraJourney'),
      require('../maharashtraWorld'),
      require('./ProgressionExperience')
    );
  } else {
    root.TakeABreakHub = factory(
      root.React,
      root.CircuitConnect,
      root.SnakeClassic,
      root.MaharashtraAtmosphere,
      root.MaharashtraJourney,
      root.MaharashtraWorld,
      root.ProgressionExperience
    );
  }
}(typeof self !== 'undefined' ? self : this, function (
  React,
  CircuitConnect,
  SnakeClassic,
  MaharashtraAtmosphere,
  MaharashtraJourney,
  MaharashtraWorld,
  ProgressionExperience
) {
  'use strict';

  const { useState, useEffect, useRef, useMemo, Component } = React;
  const CircuitComp = CircuitConnect || (typeof window !== 'undefined' ? window.CircuitConnect : null);
  const SnakeComp = SnakeClassic || (typeof window !== 'undefined' ? window.SnakeClassic : null);
  const AtmosphereComp = MaharashtraAtmosphere || (typeof window !== 'undefined' ? window.MaharashtraAtmosphere : null);
  const JourneyComp = MaharashtraJourney || (typeof window !== 'undefined' ? window.MaharashtraJourney : null);
  const WorldEngine = MaharashtraWorld || (typeof window !== 'undefined' ? window.MaharashtraWorld : null);
  const ProgressionComp = ProgressionExperience || (typeof window !== 'undefined' ? window.ProgressionExperience : null);

  /**
   * Safe localStorage helper retrieving initial saved level without throwing or crashing
   */
  function getSavedGameLevel(tab) {
    try {
      const keys = tab === 'classic'
        ? ['SNAKE_GAME_STATE', 'SNAKE_CLASSIC_STATE']
        : ['CIRCUIT_CONNECT_STATE'];

      for (const k of keys) {
        const raw = localStorage.getItem(k);
        if (raw) {
          const parsed = JSON.parse(raw);
          if (parsed && typeof parsed.currentLevel === 'number' && parsed.currentLevel >= 1) {
            return Math.floor(parsed.currentLevel);
          }
        }
      }
    } catch (e) {
      console.warn('[TakeABreak] Non-critical level parse fallback:', e);
    }
    return 1;
  }

  /**
   * React ErrorBoundary isolating Take a Break failures from the main billing application
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
          React.createElement('div', { style: { fontSize: '2.8rem' } }, '🎮'),
          React.createElement('h3', { style: { margin: 0, fontWeight: 800, color: 'var(--text)' } }, 'Take a Break is Temporarily Unavailable'),
          React.createElement('p', { style: { margin: 0, fontSize: '0.92rem', color: 'var(--text-secondary)' } },
            'A non-critical issue occurred while loading the mini-game. Your invoices, customer records, and core billing functions remain 100% operational.'
          ),
          React.createElement('button', {
            type: 'button',
            className: 'btn btn-primary',
            onClick: this.handleRetry,
            style: { marginTop: 8, padding: '8px 24px', fontWeight: 700, borderRadius: 8 }
          }, 'Reload Take a Break')
        );
      }
      return this.props.children;
    }
  }

  function TakeABreakHubInner() {
    const [activeTab, setActiveTab] = useState(() => {
      try {
        const stored = localStorage.getItem('billsoft_take_a_break_tab');
        if (stored === 'classic' || stored === 'modern') return stored;
      } catch (e) {}
      return 'modern';
    });

    const [circuitLevel, setCircuitLevel] = useState(() => getSavedGameLevel('modern'));
    const [snakeLevel, setSnakeLevel] = useState(() => getSavedGameLevel('classic'));
    const [discoveryTransition, setDiscoveryTransition] = useState(null);
    const prevLocationRef = useRef(null);

    // Current active level based on active game tab
    const currentActiveLevel = activeTab === 'classic' ? snakeLevel : circuitLevel;

    // Deterministic Maharashtra world location
    const worldData = useMemo(() => {
      if (!WorldEngine) return null;
      try {
        return WorldEngine.getWorldForLevel(activeTab, currentActiveLevel);
      } catch (e) {
        console.warn('[TakeABreak] World resolution fallback:', e);
        return null;
      }
    }, [activeTab, currentActiveLevel]);

    // Check location changes for rich travel/discovery transition
    useEffect(() => {
      if (worldData && worldData.location) {
        const currId = worldData.location.id;
        if (prevLocationRef.current && prevLocationRef.current.id !== currId) {
          setDiscoveryTransition({
            fromLoc: prevLocationRef.current,
            toLoc: worldData.location
          });
          const timer = setTimeout(() => setDiscoveryTransition(null), 2800);
          prevLocationRef.current = worldData.location;
          return () => clearTimeout(timer);
        }
        prevLocationRef.current = worldData.location;
      }
    }, [worldData]);

    // Listen for level progression events from active mini-games
    useEffect(() => {
      const handleProgression = (e) => {
        if (e && e.detail) {
          const { gameType, level } = e.detail;
          if (gameType === 'classic') {
            setSnakeLevel(Math.max(1, level));
          } else {
            setCircuitLevel(Math.max(1, level));
          }
        }
      };

      window.addEventListener('billsoft:game-progression', handleProgression);
      return () => window.removeEventListener('billsoft:game-progression', handleProgression);
    }, []);

    // Check pending nav or subtab navigation events
    useEffect(() => {
      if (typeof window !== 'undefined' && window.__billsoftPendingNav) {
        const p = window.__billsoftPendingNav;
        if (p.gameTab === 'classic' || p.subTab === 'classic' || p.tab === 'classic') {
          setActiveTab('classic');
        } else if (p.gameTab === 'modern' || p.subTab === 'modern' || p.tab === 'modern') {
          setActiveTab('modern');
        }
      }

      const handleNavSubtab = (e) => {
        if (e && e.detail) {
          const d = e.detail;
          if (d.page === 'circuit_connect' || d.page === 'take_a_break') {
            if (d.gameTab === 'classic' || d.subTab === 'classic' || d.tab === 'classic') {
              setActiveTab('classic');
              try { localStorage.setItem('billsoft_take_a_break_tab', 'classic'); } catch (err) {}
            } else if (d.gameTab === 'modern' || d.subTab === 'modern' || d.tab === 'modern') {
              setActiveTab('modern');
              try { localStorage.setItem('billsoft_take_a_break_tab', 'modern'); } catch (err) {}
            }
          }
        }
      };

      window.addEventListener('billsoft:navigate-subtab', handleNavSubtab);
      return () => window.removeEventListener('billsoft:navigate-subtab', handleNavSubtab);
    }, []);

    const handleTabChange = (tab) => {
      setActiveTab(tab);
      try {
        localStorage.setItem('billsoft_take_a_break_tab', tab);
      } catch (e) {}
    };

    return React.createElement('div', {
      className: 'take-a-break-hub-wrapper',
      style: {
        position: 'relative',
        height: 'calc(100vh - 100px)',
        maxHeight: 'calc(100vh - 100px)',
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
      // Layered Atmospheric SVG Environmental Backdrop
      AtmosphereComp && React.createElement(AtmosphereComp, {
        location: worldData ? worldData.location : null,
        gameType: activeTab
      }),

      // Unified Progression Experience Controller (4-Stage Travel Transitions, Trophy & Milestones)
      ProgressionComp && React.createElement(ProgressionComp.ProgressionExperienceController, {
        worldData,
        gameType: activeTab
      }),

      // Top Hub Bar: Centered Game Switcher (⚡ Circuit Connect | 🐍 Snake)
      React.createElement('div', {
        className: 'take-a-break-top-bar',
        style: {
          width: '100%',
          maxWidth: 860,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          height: 32,
          minHeight: 32,
          marginBottom: 4,
          position: 'relative'
        }
      },
        React.createElement('div', {
          className: 'take-a-break-segmented-switch',
          style: {
            display: 'inline-flex',
            alignItems: 'center',
            gap: 2,
            background: 'var(--surface-sunken, #f1f5f9)',
            padding: 3,
            borderRadius: 'var(--radius, 8px)',
            border: '1px solid var(--border, #e2e8f0)'
          }
        },
          React.createElement('button', {
            type: 'button',
            className: `btn btn-sm ${activeTab === 'modern' ? 'btn-primary' : 'btn-ghost'}`,
            onClick: () => handleTabChange('modern'),
            style: {
              padding: '4px 18px',
              fontSize: '0.82rem',
              fontWeight: 700,
              borderRadius: 6,
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              background: activeTab === 'modern' ? 'var(--primary, #4f46e5)' : 'transparent',
              color: activeTab === 'modern' ? '#ffffff' : 'var(--text-secondary, #64748b)',
              border: 'none',
              boxShadow: activeTab === 'modern' ? 'var(--shadow-sm, 0 1px 2px rgba(0,0,0,0.1))' : 'none',
              cursor: 'pointer'
            }
          }, '⚡ Circuit Connect'),

          React.createElement('button', {
            type: 'button',
            className: `btn btn-sm ${activeTab === 'classic' ? 'btn-primary' : 'btn-ghost'}`,
            onClick: () => handleTabChange('classic'),
            style: {
              padding: '4px 18px',
              fontSize: '0.82rem',
              fontWeight: 700,
              borderRadius: 6,
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              background: activeTab === 'classic' ? 'var(--primary, #4f46e5)' : 'transparent',
              color: activeTab === 'classic' ? '#ffffff' : 'var(--text-secondary, #64748b)',
              border: 'none',
              boxShadow: activeTab === 'classic' ? 'var(--shadow-sm, 0 1px 2px rgba(0,0,0,0.1))' : 'none',
              cursor: 'pointer'
            }
          }, '🐍 Snake')
        )
      ),

      // Redesigned Maharashtra Journey Component
      JourneyComp && React.createElement(JourneyComp, {
        currentLevel: currentActiveLevel,
        gameType: activeTab
      }),

      // Main Game Content View Container (Zero Scroll, Flex Child)
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
          overflow: 'hidden'
        }
      },
        activeTab === 'modern'
          ? (CircuitComp ? React.createElement(CircuitComp, { key: 'circuit_connect_view' }) : React.createElement('div', null, 'Loading Circuit Connect...'))
          : (SnakeComp ? React.createElement(SnakeComp, { key: 'snake_classic_view' }) : React.createElement('div', null, 'Loading Snake...'))
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
