/**
 * ProgressionExperience.js
 * Premium Progression, Travel Transitions & Reward Celebration System for Take a Break.
 * 
 * Features:
 * 1. 🗺️ 4-Stage Travel Transitions for Location Unlocks:
 *    - Stage A: Departure (current environment fades away with directional fog)
 *    - Stage B: Travel (abstract parallax Sahyadri silhouettes, star trails, travelling compass)
 *    - Stage C: Destination Reveal (archetype-specific reveal: Fort mountain mist, Wada doorway, Coastal waves, Temple saffron light, etc.)
 *    - Stage D: Arrival (grand destination announcement, level range, cultural note, ambient theme crossfade)
 * 
 * 2. 🏆 5-Stage Celebratory Trophy / Medal Unlocks:
 *    - Stage 1: Anticipation (dimmed backdrop, central radial glow, mystery ring)
 *    - Stage 2: Reveal (3D perspective scale & rotation, metallic light sweep)
 *    - Stage 3: Recognition (grand typography, tier badge, star threshold, achievement quote)
 *    - Stage 4: Celebration (sparkle particles, star trail bursts)
 *    - Stage 5: Return (smooth return to game)
 * 
 * 3. 👑 Grand 50-Level Major Milestone Celebrations:
 *    - Giant milestone numeral, radial fanfare light beams, gold confetti, journey advancement.
 * 
 * 4. 🛡️ Priority Queue & Anti-Fatigue Engine:
 *    - Prevents overlapping/stacked modals: Location Transition > Major Milestone > Trophy Unlock > Star Reward.
 *    - Includes instant Skip/Continue buttons.
 *    - Pure presentation layer: 0% disruption to game mechanics or core billing logic.
 */

(function (root, factory) {
  if (typeof define === 'function' && define.amd) {
    define(['react', '../maharashtraWorld'], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory(require('react'), require('../maharashtraWorld'));
  } else {
    root.ProgressionExperience = factory(root.React, root.MaharashtraWorld);
  }
}(typeof self !== 'undefined' ? self : this, function (React, MaharashtraWorld) {
  'use strict';

  const { useState, useEffect, useRef, useMemo, useCallback } = React;
  const WorldEngine = MaharashtraWorld || (typeof window !== 'undefined' ? window.MaharashtraWorld : null);

  function renderModalPortal(element) {
    if (typeof document !== 'undefined' && document.body && typeof ReactDOM !== 'undefined' && ReactDOM.createPortal) {
      return ReactDOM.createPortal(element, document.body);
    }
    return element;
  }

  /**
   * Helper: Archetype Reveal Graphic for Stage C
   */
  function getArchetypeRevealSvg(archetypeId, color = '#38bdf8') {
    const primary = color || '#38bdf8';
    switch (archetypeId) {
      case 'ARCH_HILL_FORT':
        return `<svg viewBox="0 0 400 240" width="100%" height="100%" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <linearGradient id="fortRevealGrad" x1="0%" y1="0%" x2="0%" y2="100%">
              <stop offset="0%" stop-color="${primary}" stop-opacity="0.9"/>
              <stop offset="100%" stop-color="${primary}" stop-opacity="0.3"/>
            </linearGradient>
            <radialGradient id="fortGlow" cx="50%" cy="50%" r="50%">
              <stop offset="0%" stop-color="${primary}" stop-opacity="0.4"/>
              <stop offset="100%" stop-color="transparent" stop-opacity="0"/>
            </radialGradient>
          </defs>
          <circle cx="200" cy="120" r="100" fill="url(#fortGlow)"/>
          <!-- Sahyadri Ridge -->
          <path d="M20,220 L100,140 L160,170 L200,90 L240,160 L300,130 L380,220 Z" fill="url(#fortRevealGrad)"/>
          <!-- Citadel Bastion & Battlements -->
          <rect x="180" y="100" width="40" height="60" fill="${primary}" opacity="0.95"/>
          <rect x="175" y="90" width="10" height="12" fill="${primary}"/>
          <rect x="195" y="90" width="10" height="12" fill="${primary}"/>
          <rect x="215" y="90" width="10" height="12" fill="${primary}"/>
          <!-- Saffron Pennant Flag -->
          <line x1="200" y1="90" x2="200" y2="50" stroke="#ffd700" stroke-width="2.5"/>
          <polygon points="200,50 235,62 200,75" fill="#f59e0b"/>
          <!-- Rising Mist Waves -->
          <path d="M40,190 Q120,170 200,190 T360,190" stroke="#ffffff" stroke-width="3" fill="none" opacity="0.4" stroke-dasharray="8 6"/>
          <path d="M80,210 Q160,195 240,210 T380,210" stroke="#ffffff" stroke-width="2" fill="none" opacity="0.3" stroke-dasharray="6 4"/>
        </svg>`;

      case 'ARCH_HISTORIC_WADA':
        return `<svg viewBox="0 0 400 240" width="100%" height="100%" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <radialGradient id="wadaGlow" cx="50%" cy="50%" r="50%">
              <stop offset="0%" stop-color="${primary}" stop-opacity="0.5"/>
              <stop offset="100%" stop-color="transparent" stop-opacity="0"/>
            </radialGradient>
          </defs>
          <circle cx="200" cy="120" r="100" fill="url(#wadaGlow)"/>
          <!-- Grand Wooden Wada Arched Gateway -->
          <rect x="110" y="50" width="180" height="170" fill="rgba(30,41,59,0.9)" stroke="${primary}" stroke-width="3" rx="6"/>
          <!-- Archway Opening -->
          <path d="M140,220 L140,130 Q200,70 260,130 L260,220 Z" fill="${primary}" opacity="0.25"/>
          <!-- Door Leaf Left (Swinging Open) -->
          <path d="M140,130 L170,140 L170,220 L140,220 Z" fill="${primary}" opacity="0.8"/>
          <!-- Door Leaf Right (Swinging Open) -->
          <path d="M260,130 L230,140 L230,220 L260,220 Z" fill="${primary}" opacity="0.8"/>
          <!-- Wooden Jharokha Balcony above -->
          <polygon points="200,20 160,50 240,50" fill="#f59e0b"/>
          <rect x="175" y="50" width="50" height="30" fill="${primary}" opacity="0.9"/>
          <!-- Lantern Glows -->
          <circle cx="125" cy="120" r="6" fill="#ffd700"/>
          <circle cx="275" cy="120" r="6" fill="#ffd700"/>
        </svg>`;

      case 'ARCH_COASTAL_FORT':
        return `<svg viewBox="0 0 400 240" width="100%" height="100%" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <linearGradient id="seaRevealGrad" x1="0%" y1="0%" x2="0%" y2="100%">
              <stop offset="0%" stop-color="${primary}" stop-opacity="0.9"/>
              <stop offset="100%" stop-color="#0284c7" stop-opacity="0.3"/>
            </linearGradient>
          </defs>
          <!-- Ocean Waves -->
          <path d="M0,220 Q100,190 200,220 T400,220 L400,240 L0,240 Z" fill="${primary}" opacity="0.5"/>
          <path d="M0,200 Q100,175 200,200 T400,200 L400,240 L0,240 Z" fill="${primary}" opacity="0.3"/>
          <!-- Island Bastion Silhouette Rising -->
          <path d="M120,200 L140,130 L160,130 L160,115 L180,115 L180,130 L220,130 L220,110 L240,110 L240,130 L260,130 L280,200 Z" fill="url(#seaRevealGrad)"/>
          <line x1="200" y1="110" x2="200" y2="70" stroke="#ffd700" stroke-width="2.5"/>
          <polygon points="200,70 230,80 200,90" fill="#f59e0b"/>
          <!-- Coastal Palm Left -->
          <path d="M70,200 Q80,150 90,110" stroke="#10b981" stroke-width="4"/>
          <path d="M90,110 Q60,95 40,110 M90,110 Q90,75 80,60 M90,110 Q120,95 140,110" stroke="#10b981" stroke-width="2.5" fill="none"/>
        </svg>`;

      case 'ARCH_PILGRIMAGE_GHAT':
        return `<svg viewBox="0 0 400 240" width="100%" height="100%" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <radialGradient id="templeRays" cx="50%" cy="40%" r="60%">
              <stop offset="0%" stop-color="#fbbf24" stop-opacity="0.6"/>
              <stop offset="100%" stop-color="transparent" stop-opacity="0"/>
            </radialGradient>
          </defs>
          <circle cx="200" cy="100" r="110" fill="url(#templeRays)"/>
          <!-- Stepped River Ghats -->
          <path d="M40,220 L70,220 L70,205 L110,205 L110,190 L160,190 L160,175 L240,175 L240,190 L290,190 L290,205 L330,205 L330,220 L360,220" stroke="${primary}" stroke-width="3" fill="none"/>
          <!-- Main Temple Shikhara Spire -->
          <polygon points="200,40 160,175 240,175" fill="${primary}" opacity="0.9"/>
          <!-- Kalash & Golden Flag -->
          <circle cx="200" cy="36" r="5" fill="#ffd700"/>
          <line x1="200" y1="36" x2="200" y2="12" stroke="#ffd700" stroke-width="2.5"/>
          <polygon points="200,12 228,22 200,32" fill="#ea580c"/>
          <!-- Deepstambha (Lamp Pillar) -->
          <rect x="270" y="90" width="14" height="100" fill="${primary}" opacity="0.8"/>
          <rect x="262" y="110" width="30" height="4" fill="#ffd700"/>
          <rect x="262" y="135" width="30" height="4" fill="#ffd700"/>
          <rect x="262" y="160" width="30" height="4" fill="#ffd700"/>
        </svg>`;

      case 'ARCH_RIVER_VALLEY':
        return `<svg viewBox="0 0 400 240" width="100%" height="100%" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <linearGradient id="riverGradRev" x1="0%" y1="0%" x2="100%" y2="100%">
              <stop offset="0%" stop-color="${primary}" stop-opacity="0.8"/>
              <stop offset="100%" stop-color="#0284c7" stop-opacity="0.4"/>
            </linearGradient>
          </defs>
          <!-- Rolling Green Valley Slopes -->
          <path d="M0,220 Q120,120 220,160 T400,140 L400,240 L0,240 Z" fill="rgba(16, 185, 129, 0.4)"/>
          <path d="M0,240 L0,180 Q100,140 200,180 T400,200 L400,240 Z" fill="rgba(5, 150, 105, 0.5)"/>
          <!-- River Confluence Ribbon -->
          <path d="M120,240 Q180,180 200,170 Q240,160 300,150 L320,155 Q250,170 220,185 Q170,205 150,240 Z" fill="url(#riverGradRev)"/>
          <!-- Traditional Riverboat -->
          <path d="M240,195 Q265,205 290,195 L280,188 L250,188 Z" fill="#ffffff" opacity="0.9"/>
          <line x1="265" y1="188" x2="265" y2="165" stroke="#ffffff" stroke-width="2"/>
          <polygon points="265,168 285,178 265,185" fill="${primary}"/>
        </svg>`;

      case 'ARCH_CAVE_TEMPLE':
        return `<svg viewBox="0 0 400 240" width="100%" height="100%" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <radialGradient id="caveInnerGlow" cx="50%" cy="50%" r="50%">
              <stop offset="0%" stop-color="#8b5cf6" stop-opacity="0.6"/>
              <stop offset="100%" stop-color="transparent" stop-opacity="0"/>
            </radialGradient>
          </defs>
          <circle cx="200" cy="120" r="100" fill="url(#caveInnerGlow)"/>
          <!-- Basalt Mountain Cliff Cavity -->
          <path d="M40,220 L60,80 L200,50 L340,80 L360,220 Z" fill="rgba(15,23,42,0.95)" stroke="${primary}" stroke-width="2"/>
          <!-- Horseshoe Chaitya Window Arch -->
          <path d="M150,180 Q200,90 250,180 L250,220 L150,220 Z" fill="${primary}" opacity="0.35"/>
          <path d="M165,180 Q200,110 235,180" stroke="${primary}" stroke-width="3" fill="none"/>
          <!-- Carved Rock Pillars -->
          <rect x="130" y="140" width="16" height="80" fill="${primary}" opacity="0.8"/>
          <rect x="254" y="140" width="16" height="80" fill="${primary}" opacity="0.8"/>
          <!-- Glowing Lotus Monolith in Center -->
          <circle cx="200" cy="170" r="12" fill="#fbbf24"/>
        </svg>`;

      case 'ARCH_PLATEAU_FOREST':
        return `<svg viewBox="0 0 400 240" width="100%" height="100%" xmlns="http://www.w3.org/2000/svg">
          <!-- Tableland Ridge -->
          <path d="M20,120 L150,120 L180,180 L220,180 L250,120 L380,120 L400,240 L0,240 Z" fill="rgba(5, 150, 105, 0.5)"/>
          <!-- Waterfall Stream -->
          <line x1="200" y1="180" x2="200" y2="240" stroke="#38bdf8" stroke-width="5" stroke-dasharray="6 3" opacity="0.9"/>
          <!-- Pine / Teak Trees Canopy -->
          <polygon points="70,70 40,120 100,120" fill="#10b981"/>
          <polygon points="120,60 90,120 150,120" fill="#059669"/>
          <polygon points="300,60 270,120 330,120" fill="#059669"/>
          <polygon points="350,70 320,120 380,120" fill="#10b981"/>
          <!-- Monsoonal Mist Streaks -->
          <path d="M60,100 Q150,90 240,100 T360,95" stroke="#ffffff" stroke-width="2.5" fill="none" opacity="0.45" stroke-dasharray="8 6"/>
        </svg>`;

      case 'ARCH_METROPOLIS':
      default:
        return `<svg viewBox="0 0 400 240" width="100%" height="100%" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <radialGradient id="metroGlow" cx="50%" cy="50%" r="50%">
              <stop offset="0%" stop-color="${primary}" stop-opacity="0.5"/>
              <stop offset="100%" stop-color="transparent" stop-opacity="0"/>
            </radialGradient>
          </defs>
          <circle cx="200" cy="120" r="100" fill="url(#metroGlow)"/>
          <!-- Monumental Arch Gateway (Gateway of India / Heritage Dome) -->
          <rect x="130" y="80" width="140" height="140" fill="rgba(30,41,59,0.9)" stroke="${primary}" stroke-width="2"/>
          <rect x="130" y="65" width="20" height="15" fill="${primary}"/>
          <rect x="250" y="65" width="20" height="15" fill="${primary}"/>
          <!-- Central Grand Arch -->
          <path d="M165,220 L165,150 Q200,110 235,150 L235,220 Z" fill="${primary}" opacity="0.3"/>
          <polygon points="200,30 170,65 230,65" fill="#ffd700"/>
          <!-- Cable Bridge Silhouettes on Right -->
          <polygon points="340,90 335,220 345,220" fill="${primary}" opacity="0.7"/>
          <line x1="340" y1="100" x2="290" y2="200" stroke="${primary}" stroke-width="1.5" opacity="0.6"/>
          <line x1="340" y1="100" x2="390" y2="200" stroke="${primary}" stroke-width="1.5" opacity="0.6"/>
        </svg>`;
    }
  }

  /**
   * 🗺️ ENHANCED 7-STAGE LOCATION TRAVEL TRANSITION OVERLAY
   * Sequence:
   * 1. CURRENT LOCATION / DEPARTURE (Establish current destination & pause gameplay)
   * 2. DEPARTURE MOTION (Visually communicate departure into Sahyadri mists)
   * 3. TRAVEL JOURNEY (Parallax Sahyadri silhouettes, compass, journey route progress)
   * 4. ARRIVAL APPROACH (Progressive theme introduction & landmark/food/obstacle reveal)
   * 5. DESTINATION REVEAL (Archetype illustration, destination title & district)
   * 6. CELEBRATION (Celebratory fanfare, level range, cultural note)
   * 7. INTERACTIVE HANDOVER (Single-fire CTA, keyboard trap, safety cleanup)
   */
  function LocationTravelTransition({ fromLoc, toLoc, worldData, onComplete, onDismiss }) {
    const isReducedMotion = (typeof window !== 'undefined' && window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches);
    const [stage, setStage] = useState(isReducedMotion ? 'ARRIVAL' : 'DEPARTURE');
    const stageRef = useRef(stage);
    stageRef.current = stage;
    const toColor = toLoc ? (toLoc.themeColor || '#38bdf8') : '#38bdf8';
    const fromColor = fromLoc ? (fromLoc.themeColor || '#64748b') : '#64748b';
    const hasCompletedRef = useRef(false);

    useEffect(() => {
      if (isReducedMotion) return;

      let t1 = null;
      let t2 = null;
      let t3 = null;
      let t4 = null;
      let t5 = null;

      const scheduleTimers = () => {
        t1 = setTimeout(() => setStage('DEPARTURE_MOTION'), 650);
        t2 = setTimeout(() => setStage('TRAVEL'), 1300);
        t3 = setTimeout(() => setStage('ARRIVAL_APPROACH'), 2400);
        t4 = setTimeout(() => setStage('REVEAL'), 3300);
        t5 = setTimeout(() => setStage('ARRIVAL'), 4300);
      };

      const clearAll = () => {
        if (t1) clearTimeout(t1);
        if (t2) clearTimeout(t2);
        if (t3) clearTimeout(t3);
        if (t4) clearTimeout(t4);
        if (t5) clearTimeout(t5);
      };

      scheduleTimers();

      const handleVisibilityChange = () => {
        if (document.hidden) {
          clearAll();
        } else {
          // If returning from background, advance directly to final arrival card for stability
          setStage('ARRIVAL');
        }
      };

      const handleKeyTrap = (e) => {
        if (e.key === 'Escape') {
          e.preventDefault();
          handleComplete(e);
        } else if (stageRef.current === 'ARRIVAL' && (e.key === ' ' || e.key === 'Enter')) {
          e.preventDefault();
          handleComplete(e);
        } else if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'w', 'a', 's', 'd', 'W', 'A', 'S', 'D', ' '].includes(e.key)) {
          e.preventDefault();
          e.stopPropagation();
        }
      };

      document.addEventListener('visibilitychange', handleVisibilityChange);
      window.addEventListener('keydown', handleKeyTrap, { capture: true });

      return () => {
        clearAll();
        document.removeEventListener('visibilitychange', handleVisibilityChange);
        window.removeEventListener('keydown', handleKeyTrap, { capture: true });
      };
    }, [isReducedMotion]);

    const archetypeSvg = useMemo(() => {
      return getArchetypeRevealSvg(toLoc ? toLoc.archetypeId : 'ARCH_HILL_FORT', toColor);
    }, [toLoc, toColor]);

    const handleComplete = (e) => {
      if (e) e.stopPropagation();
      if (hasCompletedRef.current) return;
      hasCompletedRef.current = true;
      if (typeof onDismiss === 'function') onDismiss();
      else if (typeof onComplete === 'function') onComplete();
    };

    const handleSkip = (e) => {
      handleComplete(e);
    };

    const fromRegion = fromLoc ? (fromLoc.region ? fromLoc.region.replace(/_/g, ' ') : 'MAHARASHTRA') : 'MAHARASHTRA';
    const toRegion = toLoc ? (toLoc.region ? toLoc.region.replace(/_/g, ' ') : 'MAHARASHTRA') : 'MAHARASHTRA';

    return renderModalPortal(React.createElement('div', {
      className: 'progression-travel-overlay',
      role: 'dialog',
      'aria-modal': 'true',
      'aria-label': `Travel transition to ${toLoc ? toLoc.name : 'Next Destination'}`,
      style: {
        position: 'fixed',
        inset: 0,
        zIndex: 99999,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'radial-gradient(ellipse at 50% 50%, rgba(15, 23, 42, 0.98) 0%, rgba(8, 12, 22, 1) 100%)',
        backdropFilter: 'blur(12px)',
        overflow: 'hidden',
        color: '#ffffff',
        userSelect: 'none',
        animation: 'progressionFadeIn 0.3s cubic-bezier(0.16, 1, 0.3, 1)'
      }
    },
      // Top Status & Skip Action
      React.createElement('div', {
        style: {
          position: 'absolute',
          top: 20,
          left: 24,
          right: 24,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          zIndex: 10
        }
      },
        React.createElement('div', {
          style: {
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            fontSize: '0.85rem',
            fontWeight: 800,
            letterSpacing: '0.1em',
            textTransform: 'uppercase',
            color: 'rgba(255,255,255,0.7)'
          }
        },
          React.createElement('span', { style: { color: toColor } }, '🗺️'),
          'Maharashtra Journey · Expedition Route'
        ),
        React.createElement('button', {
          type: 'button',
          className: 'btn btn-ghost btn-sm',
          onClick: handleSkip,
          style: {
            color: 'rgba(255,255,255,0.8)',
            border: '1px solid rgba(255,255,255,0.2)',
            borderRadius: 20,
            padding: '4px 14px',
            fontSize: '0.8rem',
            fontWeight: 700,
            background: 'rgba(255,255,255,0.08)',
            cursor: 'pointer'
          }
        }, 'Skip Transition ⏭')
      ),

      // STAGE 1: CURRENT LOCATION / DEPARTURE (Clearly establish current location)
      stage === 'DEPARTURE' && React.createElement('div', {
        className: 'travel-stage-departure',
        style: {
          textAlign: 'center',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: 12,
          animation: 'departureFadeIn 0.65s ease-out forwards',
          zIndex: 5
        }
      },
        React.createElement('div', {
          style: {
            fontSize: '3.6rem',
            filter: `drop-shadow(0 0 18px ${fromColor})`,
            animation: 'departureIconPulse 0.65s ease-in-out'
          }
        }, fromLoc ? (fromLoc.icon || '🚩') : '🏰'),
        React.createElement('div', {
          style: {
            fontSize: '0.82rem',
            fontWeight: 800,
            letterSpacing: '0.14em',
            textTransform: 'uppercase',
            color: '#fbbf24'
          }
        }, `✦ ${fromRegion} · ${fromLoc && fromLoc.district ? fromLoc.district.toUpperCase() : ''} ✦`),
        React.createElement('h1', {
          style: {
            fontSize: '2.4rem',
            fontWeight: 900,
            margin: 0,
            letterSpacing: '-0.02em',
            color: '#f8fafc',
            textShadow: '0 2px 12px rgba(0,0,0,0.6)'
          }
        }, `Departing ${fromLoc ? fromLoc.name : 'Current Territory'}`),
        React.createElement('div', {
          style: {
            fontSize: '1rem',
            fontWeight: 700,
            color: fromColor
          }
        }, fromLoc ? fromLoc.subtitle : 'Expedition Completed'),
        React.createElement('div', {
          style: {
            fontSize: '0.92rem',
            color: 'rgba(255,255,255,0.75)',
            maxWidth: 440,
            lineHeight: 1.4,
            marginTop: 4
          }
        }, 'Packing supplies and concluding this chapter across Maharashtra.')
      ),

      // STAGE 2: DEPARTURE MOTION (Leaving current location & entering mountain passes)
      stage === 'DEPARTURE_MOTION' && React.createElement('div', {
        className: 'travel-stage-departure-motion',
        style: {
          textAlign: 'center',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: 14,
          animation: 'departureSlideAway 0.65s ease-in forwards',
          zIndex: 5
        }
      },
        React.createElement('div', {
          style: {
            fontSize: '3.2rem',
            opacity: 0.8,
            transform: 'scale(0.9) translateY(-10px)',
            filter: `drop-shadow(0 0 12px ${fromColor})`
          }
        }, '🐎'),
        React.createElement('div', {
          style: {
            fontSize: '0.85rem',
            fontWeight: 800,
            letterSpacing: '0.12em',
            textTransform: 'uppercase',
            color: 'rgba(255,255,255,0.7)'
          }
        }, `Leaving ${fromLoc ? fromLoc.name : 'the Citadel'} Behind`),
        React.createElement('h2', {
          style: {
            fontSize: '2rem',
            fontWeight: 900,
            margin: 0,
            color: '#f8fafc'
          }
        }, 'Entering the Sahyadri Mountain Passes...'),
        React.createElement('div', {
          style: {
            fontSize: '0.9rem',
            color: 'rgba(255,255,255,0.6)',
            maxWidth: 420
          }
        }, 'Advancing across rugged historical frontiers towards uncharted lands.')
      ),

      // STAGE 3: TRAVEL JOURNEY (Moving Visuals across Sahyadri Parallax Peaks)
      stage === 'TRAVEL' && React.createElement('div', {
        className: 'travel-stage-motion',
        style: {
          width: '100%',
          height: '100%',
          position: 'absolute',
          inset: 0,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          overflow: 'hidden'
        }
      },
        // Parallax Layer 1: Distant Sahyadri Silhouette (Moving Slow)
        React.createElement('div', {
          className: 'sahyadri-parallax-bg',
          style: {
            position: 'absolute',
            bottom: 60,
            width: '200%',
            height: '240px',
            opacity: 0.28,
            backgroundImage: `radial-gradient(ellipse at 50% 100%, ${toColor} 0%, transparent 70%)`
          }
        }),
        // Parallax Layer 2: Moving Mountain Silhouettes SVG
        React.createElement('div', {
          className: 'sahyadri-peaks-layer',
          style: {
            position: 'absolute',
            bottom: 0,
            left: 0,
            width: '200%',
            height: '200px',
            animation: 'sahyadriSlide 3.5s linear infinite'
          },
          dangerouslySetInnerHTML: {
            __html: `<svg viewBox="0 0 1200 200" preserveAspectRatio="none" width="100%" height="100%">
              <path d="M0,200 L0,110 L90,60 L180,120 L270,40 L380,130 L490,50 L600,110 L700,40 L810,120 L920,50 L1030,130 L1140,60 L1200,110 L1200,200 Z" fill="${toColor}" opacity="0.35"/>
              <path d="M0,200 L0,140 L120,90 L240,150 L360,80 L480,160 L600,90 L720,150 L840,90 L960,160 L1080,90 L1200,150 L1200,200 Z" fill="#0f172a" opacity="0.75"/>
            </svg>`
          }
        }),
        // Drifting Maharashtra Particle Stars
        React.createElement('div', { className: 'travel-star-trails' }),
        // Center Travel Compass & Route Progression
        React.createElement('div', {
          style: {
            zIndex: 5,
            textAlign: 'center',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: 12,
            animation: 'travelTextPulse 1.4s ease-in-out infinite alternate'
          }
        },
          React.createElement('div', {
            style: {
              width: 68,
              height: 68,
              borderRadius: '50%',
              background: `radial-gradient(circle, ${toColor} 0%, rgba(15,23,42,0.8) 80%)`,
              border: `2px solid ${toColor}`,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: '2rem',
              boxShadow: `0 0 32px ${toColor}`,
              animation: 'compassSpin 4s linear infinite'
            }
          }, '🧭'),
          React.createElement('div', {
            style: {
              fontSize: '0.85rem',
              fontWeight: 800,
              letterSpacing: '0.14em',
              textTransform: 'uppercase',
              color: toColor
            }
          }, 'Traversing Maharashtra Expedition Route'),
          React.createElement('h2', {
            style: {
              fontSize: '2.1rem',
              fontWeight: 900,
              margin: 0,
              color: '#ffffff',
              letterSpacing: '0.03em'
            }
          }, 'Navigating Historic Sahyadri Ghats...'),
          React.createElement('div', {
            style: {
              display: 'flex',
              alignItems: 'center',
              gap: 14,
              marginTop: 6,
              background: 'rgba(255,255,255,0.08)',
              padding: '6px 20px',
              borderRadius: 30,
              border: '1px solid rgba(255,255,255,0.15)'
            }
          },
            React.createElement('span', { style: { opacity: 0.8, fontSize: '0.92rem', fontWeight: 700 } }, fromLoc ? fromLoc.name : 'Start'),
            React.createElement('span', { style: { color: toColor, fontWeight: 900 } }, '━━━━━━ ➔'),
            React.createElement('span', { style: { color: '#fbbf24', fontWeight: 900, fontSize: '0.92rem' } }, toLoc ? toLoc.name : '???')
          )
        )
      ),

      // STAGE 4: ARRIVAL APPROACH (Progressive Theme Introduction & Visual Identity Reveal)
      stage === 'ARRIVAL_APPROACH' && React.createElement('div', {
        className: 'travel-stage-approach',
        style: {
          textAlign: 'center',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: 16,
          animation: 'revealScaleIn 0.8s cubic-bezier(0.16, 1, 0.3, 1) forwards',
          zIndex: 5
        }
      },
        React.createElement('div', {
          style: {
            fontSize: '0.82rem',
            fontWeight: 800,
            letterSpacing: '0.16em',
            textTransform: 'uppercase',
            color: '#fbbf24'
          }
        }, '✨ Approaching New Expedition Sector ✨'),
        React.createElement('div', {
          style: {
            fontSize: '3.8rem',
            filter: `drop-shadow(0 0 25px ${toColor})`,
            animation: 'arrivalIconBounce 0.7s cubic-bezier(0.34, 1.56, 0.64, 1)'
          }
        }, toLoc ? (toLoc.icon || '🚩') : '🚩'),
        React.createElement('h2', {
          style: {
            fontSize: '2rem',
            fontWeight: 900,
            margin: 0,
            color: '#ffffff',
            textShadow: `0 0 20px ${toColor}`
          }
        }, `Approaching ${toLoc ? toLoc.name : 'Next Destination'}`),
        React.createElement('div', {
          style: {
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            fontSize: '0.88rem',
            fontWeight: 700,
            color: toColor
          }
        },
          React.createElement('span', null, `Region: ${toRegion}`),
          React.createElement('span', null, '·'),
          React.createElement('span', null, `District: ${toLoc && toLoc.district ? toLoc.district : ''}`)
        )
      ),

      // STAGE 5: DESTINATION REVEAL (Archetype Vector Graphic & Theme Unveiled)
      stage === 'REVEAL' && React.createElement('div', {
        className: 'travel-stage-reveal',
        style: {
          textAlign: 'center',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: 16,
          animation: 'revealScaleIn 0.8s cubic-bezier(0.16, 1, 0.3, 1) forwards',
          zIndex: 5
        }
      },
        React.createElement('div', {
          style: {
            fontSize: '0.82rem',
            fontWeight: 800,
            letterSpacing: '0.16em',
            textTransform: 'uppercase',
            color: '#fbbf24'
          }
        }, '✦ DESTINATION UNLOCKED ✦'),
        React.createElement('div', {
          className: 'reveal-graphic-frame',
          style: {
            width: 320,
            height: 190,
            borderRadius: 16,
            background: 'rgba(30, 41, 59, 0.85)',
            border: `2px solid ${toColor}`,
            boxShadow: `0 0 45px ${toColor}`,
            overflow: 'hidden',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            position: 'relative'
          },
          dangerouslySetInnerHTML: { __html: archetypeSvg }
        }),
        React.createElement('div', {
          style: {
            fontSize: '1.8rem',
            fontWeight: 900,
            letterSpacing: '0.08em',
            color: '#ffffff',
            textShadow: `0 0 20px ${toColor}`
          }
        }, `DISCOVERING: ${toLoc ? toLoc.name.toUpperCase() : 'DESTINATION'}`)
      ),

      // STAGE 6 & 7: CELEBRATION & INTERACTIVE HANDOVER (Grand Arrival Card & Control Handover)
      stage === 'ARRIVAL' && React.createElement('div', {
        className: 'travel-stage-arrival',
        style: {
          textAlign: 'center',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: 14,
          padding: '24px 32px',
          maxWidth: 540,
          width: '90%',
          background: 'linear-gradient(135deg, rgba(30, 41, 59, 0.95), rgba(15, 23, 42, 0.98))',
          border: `2px solid ${toColor}`,
          borderRadius: 20,
          boxShadow: `0 16px 50px rgba(0,0,0,0.8), 0 0 40px ${toColor}`,
          animation: 'arrivalPop 0.5s cubic-bezier(0.16, 1, 0.3, 1) forwards',
          zIndex: 5
        }
      },
        React.createElement('div', {
          style: {
            fontSize: '3.6rem',
            filter: `drop-shadow(0 0 20px ${toColor})`,
            animation: 'arrivalIconBounce 0.6s cubic-bezier(0.34, 1.56, 0.64, 1)'
          }
        }, toLoc ? (toLoc.icon || '🚩') : '🚩'),
        React.createElement('div', {
          style: {
            fontSize: '0.8rem',
            fontWeight: 800,
            letterSpacing: '0.16em',
            textTransform: 'uppercase',
            color: '#fbbf24'
          }
        }, `✦ ${toRegion} · ${toLoc && toLoc.district ? toLoc.district.toUpperCase() : ''} ✦`),
        React.createElement('h1', {
          style: {
            fontSize: '2.4rem',
            fontWeight: 900,
            margin: 0,
            letterSpacing: '-0.02em',
            color: '#ffffff',
            textShadow: `0 0 25px ${toColor}`
          }
        }, toLoc ? toLoc.name : 'Maharashtra Destination'),
        React.createElement('div', {
          style: {
            fontSize: '1.05rem',
            fontWeight: 700,
            color: toColor,
            marginTop: -4
          }
        }, toLoc ? toLoc.subtitle : 'Swarajya Expedition'),
        React.createElement('p', {
          style: {
            margin: 0,
            fontSize: '0.92rem',
            color: 'rgba(255,255,255,0.8)',
            lineHeight: 1.5,
            maxWidth: 440
          }
        }, toLoc && toLoc.culturalNote ? toLoc.culturalNote : 'An iconic historical territory of Maharashtra heritage.'),
        React.createElement('div', {
          style: {
            display: 'inline-flex',
            alignItems: 'center',
            gap: 8,
            background: 'rgba(255,255,255,0.08)',
            padding: '6px 16px',
            borderRadius: 20,
            fontSize: '0.82rem',
            fontWeight: 800,
            color: '#ffd700',
            marginTop: 4
          }
        }, `🚩 Levels ${worldData ? worldData.levelStart : 1}–${worldData ? worldData.levelEnd : 15} · ${worldData ? worldData.cycleTag : 'Journey I'}`),
        React.createElement('button', {
          type: 'button',
          className: 'btn btn-primary',
          onClick: handleComplete,
          style: {
            marginTop: 10,
            padding: '10px 32px',
            fontSize: '1rem',
            fontWeight: 800,
            borderRadius: 10,
            boxShadow: `0 0 20px ${toColor}`,
            cursor: 'pointer'
          }
        }, `Begin Solving in ${toLoc ? toLoc.name : 'Destination'} ➔`)
      )
    ));
  }

  /**
   * 🏆 5-STAGE CELEBRATORY TROPHY / MEDAL UNLOCK MODAL
   */
  function TrophyCelebrationModal({ data, onDismiss }) {
    // Stages: 'ANTICIPATION' -> 'REVEAL' -> 'RECOGNITION' -> 'CELEBRATION'
    const [stage, setStage] = useState('ANTICIPATION');
    const { milestoneNumber, starThreshold, trophy } = data || {};
    const trophyIcon = trophy ? trophy.icon : '🏆';
    const trophyName = trophy ? trophy.name : 'Circuit Master';
    const trophyTier = trophy ? (trophy.tier || 'Gold').toUpperCase() : 'GOLD';

    useEffect(() => {
      const t1 = setTimeout(() => setStage('REVEAL'), 450);
      const t2 = setTimeout(() => setStage('RECOGNITION'), 1100);
      const t3 = setTimeout(() => setStage('CELEBRATION'), 1800);

      return () => {
        clearTimeout(t1);
        clearTimeout(t2);
        clearTimeout(t3);
      };
    }, []);

    const handleClaim = (e) => {
      if (e) e.stopPropagation();
      if (typeof onDismiss === 'function') onDismiss();
    };

    return renderModalPortal(React.createElement('div', {
      className: 'progression-trophy-overlay',
      role: 'dialog',
      'aria-modal': 'true',
      'aria-label': `Trophy Unlocked: ${trophyName}`,
      style: {
        position: 'fixed',
        inset: 0,
        zIndex: 99999,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'radial-gradient(ellipse at 50% 50%, rgba(15, 23, 42, 0.94) 0%, rgba(8, 12, 22, 0.98) 100%)',
        backdropFilter: 'blur(10px)',
        overflow: 'hidden',
        color: '#ffffff',
        animation: 'progressionFadeIn 0.25s ease-out'
      },
      onClick: handleClaim
    },
      React.createElement('div', {
        className: 'trophy-celebration-card',
        onClick: e => e.stopPropagation(),
        style: {
          background: 'linear-gradient(135deg, rgba(30, 41, 59, 0.96), rgba(15, 23, 42, 0.98))',
          border: '2px solid #ffd700',
          borderRadius: 22,
          padding: '32px 28px',
          maxWidth: 440,
          width: '90%',
          textAlign: 'center',
          boxShadow: '0 0 60px rgba(255, 215, 0, 0.45)',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: 14,
          position: 'relative',
          animation: 'trophyCardPop 0.4s cubic-bezier(0.16, 1, 0.3, 1)'
        }
      },
        // Radial light beams behind trophy
        React.createElement('div', {
          className: 'trophy-radial-rays',
          style: {
            position: 'absolute',
            top: '25%',
            left: '50%',
            transform: 'translate(-50%, -50%)',
            width: 240,
            height: 240,
            borderRadius: '50%',
            background: 'radial-gradient(circle, rgba(255, 215, 0, 0.35) 0%, transparent 70%)',
            pointerEvents: 'none',
            zIndex: 0
          }
        }),

        // Trophy 3D Reveal Graphic
        React.createElement('div', {
          style: {
            fontSize: '4.5rem',
            filter: 'drop-shadow(0 0 25px gold)',
            zIndex: 1,
            animation: stage === 'ANTICIPATION'
              ? 'trophyAnticipate 0.5s infinite alternate'
              : 'trophyReveal3D 0.8s cubic-bezier(0.34, 1.56, 0.64, 1) forwards'
          }
        }, stage === 'ANTICIPATION' ? '✦ ? ✦' : trophyIcon),

        // Stage 3 & 4: Recognition & Typography
        React.createElement('div', {
          style: {
            fontSize: '0.82rem',
            fontWeight: 900,
            letterSpacing: '0.18em',
            textTransform: 'uppercase',
            color: '#ffd700',
            zIndex: 1
          }
        }, `🏆 TROPHY UNLOCKED · ${trophyTier} 🏆`),

        React.createElement('h2', {
          style: {
            margin: 0,
            fontSize: '1.8rem',
            fontWeight: 900,
            color: '#ffffff',
            letterSpacing: '-0.02em',
            zIndex: 1
          }
        }, trophyName),

        React.createElement('div', {
          style: {
            fontSize: '1.15rem',
            fontWeight: 800,
            color: '#38bdf8',
            display: 'flex',
            alignItems: 'center',
            gap: 6,
            zIndex: 1
          }
        },
          React.createElement('span', null, '⭐'),
          `${starThreshold || (milestoneNumber * 50)} TOTAL STARS MILESTONE`
        ),

        React.createElement('p', {
          style: {
            margin: 0,
            fontSize: '0.92rem',
            color: 'rgba(255,255,255,0.75)',
            lineHeight: 1.4,
            zIndex: 1
          }
        }, trophy ? (trophy.description || 'Your journey across Maharashtra reaches greater heights!') : 'Mastery recognized across all circuits.'),

        React.createElement('button', {
          type: 'button',
          className: 'btn btn-primary',
          onClick: handleClaim,
          style: {
            marginTop: 8,
            padding: '10px 32px',
            fontSize: '0.95rem',
            fontWeight: 800,
            borderRadius: 10,
            boxShadow: '0 0 20px rgba(255, 215, 0, 0.5)',
            zIndex: 1
          }
        }, 'Claim Trophy & Continue ➔')
      )
    ));
  }

  /**
   * 👑 GRAND 50-LEVEL MAJOR MILESTONE CELEBRATION MODAL
   */
  function MajorMilestoneModal({ levelCount, onDismiss }) {
    const handleDismiss = (e) => {
      if (e) e.stopPropagation();
      if (typeof onDismiss === 'function') onDismiss();
    };

    return renderModalPortal(React.createElement('div', {
      className: 'progression-milestone-overlay',
      role: 'dialog',
      'aria-modal': 'true',
      'aria-label': `${levelCount} Levels Completed Milestone`,
      style: {
        position: 'fixed',
        inset: 0,
        zIndex: 99999,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'radial-gradient(ellipse at 50% 50%, rgba(15, 23, 42, 0.95) 0%, rgba(8, 12, 22, 0.98) 100%)',
        backdropFilter: 'blur(10px)',
        overflow: 'hidden',
        color: '#ffffff',
        animation: 'progressionFadeIn 0.25s ease-out'
      },
      onClick: handleDismiss
    },
      React.createElement('div', {
        className: 'milestone-celebration-card',
        onClick: e => e.stopPropagation(),
        style: {
          background: 'linear-gradient(135deg, rgba(30, 41, 59, 0.96), rgba(15, 23, 42, 0.98))',
          border: '2px solid rgba(255, 215, 0, 0.8)',
          borderRadius: 24,
          padding: '36px 32px',
          maxWidth: 460,
          width: '90%',
          textAlign: 'center',
          boxShadow: '0 0 70px rgba(255, 215, 0, 0.5)',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: 14,
          position: 'relative',
          animation: 'levelMilestonePop 0.45s cubic-bezier(0.16, 1, 0.3, 1)'
        }
      },
        React.createElement('div', {
          style: {
            fontSize: '3.6rem',
            filter: 'drop-shadow(0 0 20px gold)',
            animation: 'milestoneBounce 0.6s cubic-bezier(0.34, 1.56, 0.64, 1)'
          }
        }, '👑'),

        // Giant Milestone Numeral
        React.createElement('div', {
          style: {
            fontSize: '4.2rem',
            fontWeight: 900,
            lineHeight: 1,
            color: '#ffd700',
            textShadow: '0 0 35px rgba(255, 215, 0, 0.75)',
            letterSpacing: '-0.04em'
          }
        }, levelCount || 50),

        React.createElement('div', {
          style: {
            fontSize: '0.9rem',
            fontWeight: 900,
            letterSpacing: '0.2em',
            textTransform: 'uppercase',
            color: '#ffffff'
          }
        }, 'LEVELS CONQUERED'),

        React.createElement('p', {
          style: {
            margin: 0,
            fontSize: '0.95rem',
            color: 'rgba(255,255,255,0.8)',
            lineHeight: 1.5
          }
        }, `You have conquered a monumental milestone of ${levelCount || 50} puzzle challenges across Maharashtra. Your legacy as a Swarajya Master grows!`),

        React.createElement('button', {
          type: 'button',
          className: 'btn btn-primary',
          onClick: handleDismiss,
          style: {
            marginTop: 10,
            padding: '12px 36px',
            fontSize: '1rem',
            fontWeight: 800,
            borderRadius: 10,
            boxShadow: '0 0 25px rgba(255, 215, 0, 0.6)'
          }
        }, 'Resume Journey ➔')
      )
    ));
  }

  /**
   * 🛡️ UNIFIED PROGRESSION EXPERIENCE CONTROLLER
   */
  function ProgressionExperienceController({ worldData, gameType }) {
    const [activeEvent, setActiveEvent] = useState(null);
    const activeEventRef = useRef(null);
    const queueRef = useRef([]);
    const prevLocationRef = useRef(worldData && worldData.location ? worldData.location : null);

    const processNext = useCallback(() => {
      if (queueRef.current.length > 0) {
        const next = queueRef.current.shift();
        activeEventRef.current = next;
        setActiveEvent(next);
      } else {
        activeEventRef.current = null;
        setActiveEvent(null);
      }
    }, []);

    const enqueueEvent = useCallback((type, payload) => {
      if (!payload) return;

      // Deduplicate if already transitioning to the same location
      if (type === 'LOCATION' && payload.toLoc) {
        if (activeEventRef.current && activeEventRef.current.type === 'LOCATION' &&
            activeEventRef.current.payload && activeEventRef.current.payload.toLoc &&
            activeEventRef.current.payload.toLoc.id === payload.toLoc.id) {
          return;
        }
        const alreadyQueued = queueRef.current.some(item =>
          item.type === 'LOCATION' && item.payload && item.payload.toLoc &&
          item.payload.toLoc.id === payload.toLoc.id
        );
        if (alreadyQueued) return;
      }

      const priorityMap = { LOCATION: 1, MILESTONE: 2, TROPHY: 3 };
      const newEvent = { type, payload, priority: priorityMap[type] || 4 };

      if (!activeEventRef.current) {
        activeEventRef.current = newEvent;
        setActiveEvent(newEvent);
      } else {
        queueRef.current.push(newEvent);
        queueRef.current.sort((a, b) => a.priority - b.priority);
      }
    }, []);

    // React to worldData location change across levels/destinations
    useEffect(() => {
      if (!worldData || !worldData.location) return;
      const currentLoc = worldData.location;
      if (!prevLocationRef.current) {
        prevLocationRef.current = currentLoc;
        return;
      }
      if (prevLocationRef.current.id !== currentLoc.id) {
        const fromLoc = prevLocationRef.current;
        prevLocationRef.current = currentLoc;
        enqueueEvent('LOCATION', {
          fromLoc,
          toLoc: currentLoc,
          worldData
        });
      }
    }, [worldData, enqueueEvent]);

    // Listen for Dedicated Location Unlock & Celebration Global Events
    useEffect(() => {
      const handleLocationUnlockEvent = (e) => {
        if (e && e.detail && e.detail.toLoc) {
          if (e.detail.toLoc) {
            prevLocationRef.current = e.detail.toLoc;
          }
          enqueueEvent('LOCATION', {
            unlockKey: e.detail.unlockKey,
            fromLoc: e.detail.fromLoc,
            toLoc: e.detail.toLoc,
            worldData: e.detail.worldData
          });
        }
      };

      const handleTrophyEvent = (e) => {
        if (e && e.detail) {
          enqueueEvent('TROPHY', e.detail);
        }
      };

      const handleMilestoneEvent = (e) => {
        if (e && e.detail && typeof e.detail.levelCount === 'number') {
          enqueueEvent('MILESTONE', e.detail.levelCount);
        }
      };

      window.addEventListener('billsoft:location-unlocked', handleLocationUnlockEvent);
      window.addEventListener('billsoft:trophy-unlocked', handleTrophyEvent);
      window.addEventListener('billsoft:level-milestone-reached', handleMilestoneEvent);

      return () => {
        window.removeEventListener('billsoft:location-unlocked', handleLocationUnlockEvent);
        window.removeEventListener('billsoft:trophy-unlocked', handleTrophyEvent);
        window.removeEventListener('billsoft:level-milestone-reached', handleMilestoneEvent);
      };
    }, [enqueueEvent]);

    const handleDismissActive = () => {
      processNext();
    };

    if (!activeEvent) return null;

    if (activeEvent.type === 'LOCATION') {
      return React.createElement(LocationTravelTransition, {
        fromLoc: activeEvent.payload.fromLoc,
        toLoc: activeEvent.payload.toLoc,
        worldData: activeEvent.payload.worldData,
        onComplete: handleDismissActive,
        onDismiss: handleDismissActive
      });
    }

    if (activeEvent.type === 'TROPHY') {
      return React.createElement(TrophyCelebrationModal, {
        data: activeEvent.payload,
        onDismiss: handleDismissActive
      });
    }

    if (activeEvent.type === 'MILESTONE') {
      return React.createElement(MajorMilestoneModal, {
        levelCount: activeEvent.payload,
        onDismiss: handleDismissActive
      });
    }

    return null;
  }

  return {
    LocationTravelTransition,
    TrophyCelebrationModal,
    MajorMilestoneModal,
    ProgressionExperienceController
  };
}));
