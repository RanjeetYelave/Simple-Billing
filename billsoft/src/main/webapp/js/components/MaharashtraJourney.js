/**
 * MaharashtraJourney.js
 * Retro Adventure Expedition Map Component & Interactive Modal for Snake Classic.
 * 
 * Features:
 * - Inspired by classic adventure game cartography (Prince of Persia design philosophy).
 * - Authoritative single-source-of-truth integration with MaharashtraWorld progression engine.
 * - Illustrated vertical expedition route with illuminated path segments and regional landmarks.
 * - Distinct visual states:
 *     1. COMPLETED: Illuminated golden badge, checkmark, explored lore, rich theme accent.
 *     2. CURRENT: Glowing pulsing "YOU ARE HERE", checkpoint count (e.g. 7 / 15), active level, animated trail.
 *     3. FUTURE / UNKNOWN: Shrouded mystery fog-of-war ("🔒 ???", concealed name, locked silhouette).
 *     4. LEVEL 1000 FINALE: Royal Sovereign Coronation Citadel culmination.
 * - Regional headers (Sahyadri, Konkan, Pune Heartland, Marathwada, Khandesh, Vidarbha).
 * - Auto-scrolls to current player location on modal open.
 * - Pure procedural SVG/Canvas/CSS, 0 external image dependencies, 60 FPS performance.
 */

(function (root, factory) {
  if (typeof define === 'function' && define.amd) {
    define(['react', '../maharashtraWorld'], factory);
  } else if (typeof module === 'object' && module.exports) {
    let R = null;
    try { R = require('react'); } catch (e) { R = (typeof global !== 'undefined' && global.React) ? global.React : (root && root.React ? root.React : null); }
    module.exports = factory(R, require('../maharashtraWorld'));
  } else {
    root.MaharashtraJourney = factory(root.React, root.MaharashtraWorld);
  }
}(typeof self !== 'undefined' ? self : (typeof global !== 'undefined' ? global : this), function (React, MaharashtraWorld) {
  'use strict';

  const { useState, useEffect, useMemo, useRef } = React;
  const WorldEngine = MaharashtraWorld || (typeof window !== 'undefined' ? window.MaharashtraWorld : null);

  function renderModalPortal(element) {
    if (typeof document !== 'undefined' && document.body && typeof ReactDOM !== 'undefined' && ReactDOM.createPortal) {
      return ReactDOM.createPortal(element, document.body);
    }
    return element;
  }

  // Regional aesthetic grouping headers
  const REGION_META = {
    'PASCHIM_MAHARASHTRA': {
      title: 'PASCHIM MAHARASHTRA',
      subtitle: 'Sahyadri Foothills & Krishna-Bhima River Basins',
      icon: '🏔️',
      color: '#38bdf8',
      accent: '#0284c7'
    },
    'KONKAN': {
      title: 'KONKAN COAST & ARABIAN SEA',
      subtitle: 'Laterite Sea Forts, Palm Shores & Estuaries',
      icon: '🌊',
      color: '#00e5ff',
      accent: '#0891b2'
    },
    'PUNE_HEARTLAND': {
      title: 'PUNE HEARTLAND & MARATHA CITADELS',
      subtitle: 'Historic Hill Forts, Wadas & Ghat Shrines',
      icon: '🏛️',
      color: '#f59e0b',
      accent: '#d97706'
    },
    'MARATHWADA': {
      title: 'MARATHWADA & ROCK-CUT HERITAGE',
      subtitle: 'Monolithic Basalt Caves, Jyotirlingas & Royal Citadels',
      icon: '🗿',
      color: '#a855f7',
      accent: '#7e22ce'
    },
    'KHANDESH': {
      title: 'KHANDESH & NORTHERN SAHYADRIS',
      subtitle: 'Godavari Headwaters, Mountain Pinnacles & Satpura Hills',
      icon: '🛕',
      color: '#10b981',
      accent: '#059669'
    },
    'VIDARBHA': {
      title: 'VIDARBHA & EASTERN TIGER FORESTS',
      subtitle: 'Teak Reserves, Ancient Gond Citadels & Sacred Rivers',
      icon: '🐅',
      color: '#fbbf24',
      accent: '#b45309'
    }
  };

  // Injected CSS Styles for Retro Adventure Map Aesthetics
  const INJECTED_STYLES = `
    @keyframes expeditionPulse {
      0% { box-shadow: 0 0 0 0 rgba(245, 158, 11, 0.6), inset 0 0 12px rgba(245, 158, 11, 0.3); }
      50% { box-shadow: 0 0 20px 4px rgba(245, 158, 11, 0.3), inset 0 0 20px rgba(245, 158, 11, 0.5); }
      100% { box-shadow: 0 0 0 0 rgba(245, 158, 11, 0.6), inset 0 0 12px rgba(245, 158, 11, 0.3); }
    }
    @keyframes routeFlow {
      0% { background-position: 0 0; }
      100% { background-position: 0 40px; }
    }
    @keyframes snakeCrawlMini {
      0% { transform: translateX(0); }
      50% { transform: translateX(6px); }
      100% { transform: translateX(0); }
    }
    @keyframes beaconGlow {
      0%, 100% { transform: scale(1); opacity: 0.8; }
      50% { transform: scale(1.15); opacity: 1; }
    }
    @keyframes fogDrift {
      0% { opacity: 0.6; }
      50% { opacity: 0.85; }
      100% { opacity: 0.6; }
    }
    .expedition-card-hover:hover {
      transform: translateY(-2px);
      border-color: rgba(245, 158, 11, 0.6) !important;
      background: rgba(30, 41, 59, 0.95) !important;
    }
  `;

  function MaharashtraJourney({ currentLevel = 1, gameType = 'modern' }) {
    const [showJourneyModal, setShowJourneyModal] = useState(false);
    const [selectedLocationDetail, setSelectedLocationDetail] = useState(null);
    const activeNodeRef = useRef(null);

    const safeLevel = Math.max(1, Math.floor(currentLevel || 1));

    // Ensure style injection
    useEffect(() => {
      if (typeof document !== 'undefined') {
        let styleEl = document.getElementById('maharashtra-expedition-map-styles');
        if (!styleEl) {
          styleEl = document.createElement('style');
          styleEl.id = 'maharashtra-expedition-map-styles';
          styleEl.innerHTML = INJECTED_STYLES;
          document.head.appendChild(styleEl);
        }
      }
    }, []);

    // Resolve current world data from authoritative engine
    const worldData = useMemo(() => {
      if (!WorldEngine) return null;
      return WorldEngine.getWorldForLevel(gameType, safeLevel);
    }, [gameType, safeLevel]);

    // Resolve mystery next destination teaser
    const mysteryNext = useMemo(() => {
      if (!WorldEngine) return null;
      return WorldEngine.getMysteryNextLocation(gameType, safeLevel);
    }, [gameType, safeLevel]);

    // Resolve complete expedition timeline (past, current, and upcoming mystery nodes)
    const journeyData = useMemo(() => {
      if (!WorldEngine) return null;
      // Show complete expedition route from Level 1 to 1000
      return WorldEngine.getJourneyTimeline(gameType, safeLevel, 15, 'all');
    }, [gameType, safeLevel]);

    // Auto-scroll to active node on modal open
    useEffect(() => {
      if (showJourneyModal && activeNodeRef.current) {
        setTimeout(() => {
          if (activeNodeRef.current) {
            activeNodeRef.current.scrollIntoView({ behavior: 'smooth', block: 'center' });
          }
        }, 120);
      }
    }, [showJourneyModal]);

    if (!worldData || !worldData.location) return null;

    const loc = worldData.location;
    const progressInLoc = worldData.progressInLocation;
    const totalInLoc = worldData.levelsPerLocation;
    const percent = worldData.progressPercent;
    const isFinale = safeLevel === 1000;

    // Build mini 6-checkpoint route track for the header bar
    const nodeCount = 6;
    const nodes = [];
    const activeStepRatio = (progressInLoc - 1) / Math.max(1, totalInLoc - 1);
    const activeNodeIndex = Math.min(nodeCount - 1, Math.floor(activeStepRatio * (nodeCount - 1)));

    for (let i = 0; i < nodeCount; i++) {
      nodes.push({
        index: i,
        isPast: i < activeNodeIndex,
        isCurrent: i === activeNodeIndex
      });
    }

    return React.createElement(React.Fragment, null,
      // =======================================================================
      // 1. Compact Expedition Header Bar (Atmospheric Basalt & Saffron Surface)
      // =======================================================================
      React.createElement('div', {
        className: 'maharashtra-journey-card',
        onClick: () => setShowJourneyModal(true),
        role: 'button',
        tabIndex: 0,
        'aria-label': `Maharashtra Expedition: Currently exploring ${loc.name}, level ${safeLevel}. Checkpoint ${progressInLoc} of ${totalInLoc}. Click to view full Expedition Map.`,
        onKeyDown: (e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            setShowJourneyModal(true);
          }
        },
        style: {
          width: '100%',
          maxWidth: '1280px',
          background: 'linear-gradient(135deg, rgba(15, 23, 42, 0.94) 0%, rgba(30, 41, 59, 0.90) 100%)',
          backdropFilter: 'blur(10px)',
          WebkitBackdropFilter: 'blur(10px)',
          border: '1px solid rgba(245, 158, 11, 0.35)',
          borderRadius: '8px',
          padding: '6px 14px',
          marginBottom: 6,
          cursor: 'pointer',
          boxShadow: '0 4px 20px rgba(0, 0, 0, 0.4), inset 0 1px 0 rgba(255, 255, 255, 0.08)',
          display: 'flex',
          flexDirection: 'column',
          gap: 4,
          transition: 'border-color 0.2s ease, box-shadow 0.2s ease, transform 0.15s ease',
          userSelect: 'none',
          boxSizing: 'border-box'
        }
      },
        // Row 1: Header Identity + Location & Checkpoint Progress
        React.createElement('div', {
          style: {
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            fontSize: '0.8rem',
            fontWeight: 800,
            letterSpacing: '0.04em'
          }
        },
          // Left: Expedition Crest & Location Name
          React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' } },
            React.createElement('span', {
              style: {
                fontSize: '1.05rem',
                filter: 'drop-shadow(0 0 6px rgba(245, 158, 11, 0.5))'
              }
            }, loc.icon || (isFinale ? '👑' : '🚩')),
            React.createElement('span', {
              style: {
                color: '#f59e0b',
                textTransform: 'uppercase',
                fontSize: '0.72rem',
                fontWeight: 900,
                letterSpacing: '0.08em'
              }
            }, 'MAHARASHTRA EXPEDITION'),
            React.createElement('span', { style: { color: 'rgba(148, 163, 184, 0.4)' } }, '•'),
            React.createElement('span', {
              style: {
                color: '#f8fafc',
                fontWeight: 800,
                fontSize: '0.84rem',
                textShadow: '0 1px 3px rgba(0,0,0,0.8)'
              }
            }, loc.name.toUpperCase()),
            React.createElement('span', {
              style: {
                fontSize: '0.66rem',
                fontWeight: 800,
                padding: '2px 8px',
                borderRadius: 4,
                background: 'rgba(245, 158, 11, 0.15)',
                color: '#fbbf24',
                border: '1px solid rgba(245, 158, 11, 0.3)',
                letterSpacing: '0.05em'
              }
            }, loc.district.toUpperCase())
          ),

          // Right: Checkpoint Count & Interactive Map Trigger
          React.createElement('div', {
            style: {
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              fontSize: '0.74rem',
              fontWeight: 700
            }
          },
            React.createElement('span', {
              style: {
                color: '#94a3b8',
                display: 'flex',
                alignItems: 'center',
                gap: 4
              }
            },
              React.createElement('strong', { style: { color: '#f59e0b' } }, `${progressInLoc}`),
              ` / ${totalInLoc} checkpoints (${percent}%)`
            ),
            React.createElement('span', {
              style: {
                background: 'linear-gradient(135deg, rgba(245, 158, 11, 0.25) 0%, rgba(217, 119, 6, 0.15) 100%)',
                padding: '3px 10px',
                borderRadius: 4,
                color: '#fbbf24',
                border: '1px solid rgba(245, 158, 11, 0.45)',
                fontSize: '0.72rem',
                fontWeight: 800,
                letterSpacing: '0.04em',
                boxShadow: '0 0 10px rgba(245, 158, 11, 0.2)'
              }
            }, '🗺️ EXPEDITION MAP ➔')
          )
        ),

        // Row 2: Route Nodes Track + Next Mystery Node
        React.createElement('div', {
          style: {
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 12,
            position: 'relative'
          }
        },
          // Route nodes container
          React.createElement('div', {
            style: {
              display: 'flex',
              alignItems: 'center',
              flex: 1,
              position: 'relative',
              height: 16
            }
          },
            // Background route line (dark stone track)
            React.createElement('div', {
              style: {
                position: 'absolute',
                left: 6,
                right: 20,
                height: 2,
                background: 'rgba(51, 65, 85, 0.7)',
                borderRadius: 1,
                zIndex: 1
              }
            }),
            // Saffron illuminated progress line
            React.createElement('div', {
              style: {
                position: 'absolute',
                left: 6,
                width: `calc(${percent}% * 0.85)`,
                height: 2,
                background: 'linear-gradient(90deg, #f59e0b, #ef4444)',
                borderRadius: 1,
                zIndex: 2,
                boxShadow: '0 0 8px rgba(245, 158, 11, 0.6)',
                transition: 'width 0.4s ease'
              }
            }),
            // Checkpoint nodes
            React.createElement('div', {
              style: {
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                width: '100%',
                position: 'relative',
                zIndex: 3
              }
            },
              nodes.map(node => {
                return React.createElement('div', {
                  key: `node_${node.index}`,
                  title: node.isCurrent
                    ? `You are here: ${loc.name} (Checkpoint ${progressInLoc}/${totalInLoc})`
                    : (node.isPast ? 'Explored checkpoint' : 'Upcoming checkpoint'),
                  style: {
                    width: node.isCurrent ? 13 : 8,
                    height: node.isCurrent ? 13 : 8,
                    borderRadius: '50%',
                    background: node.isCurrent
                      ? '#f59e0b'
                      : node.isPast ? '#f59e0b' : '#1e293b',
                    border: node.isCurrent
                      ? '2px solid #ffffff'
                      : (node.isPast ? '1px solid #d97706' : '1.5px solid rgba(148, 163, 184, 0.3)'),
                    boxShadow: node.isCurrent
                      ? '0 0 10px rgba(245, 158, 11, 0.8), 0 0 20px rgba(239, 68, 68, 0.4)'
                      : (node.isPast ? '0 0 4px rgba(245, 158, 11, 0.3)' : 'none'),
                    transition: 'all 0.3s ease'
                  }
                });
              }),
              // Next locked mystery milestone node
              React.createElement('div', {
                title: mysteryNext
                  ? `Next Discovery: ${mysteryNext.displayName} (Unlocks Level ${mysteryNext.unlocksAtLevel})`
                  : 'Expedition Finale',
                style: {
                  width: 18,
                  height: 18,
                  borderRadius: '50%',
                  background: 'rgba(30, 41, 59, 0.8)',
                  border: '1.5px dashed rgba(245, 158, 11, 0.4)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: '0.65rem',
                  color: '#94a3b8'
                }
              }, isFinale ? '👑' : '🔒')
            )
          ),

          // Next Destination Teaser
          mysteryNext && React.createElement('div', {
            style: {
              fontSize: '0.68rem',
              fontWeight: 800,
              color: '#cbd5e1',
              background: 'rgba(30, 41, 59, 0.7)',
              padding: '2px 8px',
              borderRadius: 4,
              border: '1px solid rgba(148, 163, 184, 0.2)',
              whiteSpace: 'nowrap',
              letterSpacing: '0.04em'
            }
          },
            `NEXT: ${mysteryNext.displayName} · LVL ${mysteryNext.unlocksAtLevel}`
          )
        )
      ),

      // =======================================================================
      // 2. Full Retro Adventure Expedition Map Modal (Prince of Persia Style)
      // =======================================================================
      showJourneyModal && renderModalPortal(React.createElement('div', {
        className: 'modal-overlay',
        style: {
          position: 'fixed',
          inset: 0,
          background: 'rgba(7, 11, 20, 0.88)',
          backdropFilter: 'blur(12px)',
          WebkitBackdropFilter: 'blur(12px)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 99999,
          padding: 16,
          animation: 'milestoneFadeIn 0.25s ease'
        },
        onClick: () => {
          setShowJourneyModal(false);
          setSelectedLocationDetail(null);
        }
      },
        React.createElement('div', {
          className: 'modal-card',
          style: {
            background: 'linear-gradient(180deg, #0b1120 0%, #0f172a 100%)',
            border: '2px solid rgba(245, 158, 11, 0.45)',
            borderRadius: '12px',
            padding: '20px 24px',
            maxWidth: 680,
            width: '95%',
            maxHeight: '88vh',
            display: 'flex',
            flexDirection: 'column',
            gap: 14,
            boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.7), 0 0 35px rgba(245, 158, 11, 0.2)',
            overflow: 'hidden',
            boxSizing: 'border-box'
          },
          onClick: e => e.stopPropagation()
        },
          // Modal Header: Retro Cartography Identity
          React.createElement('div', {
            style: {
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              borderBottom: '1px solid rgba(245, 158, 11, 0.25)',
              paddingBottom: 14
            }
          },
            React.createElement('div', null,
              React.createElement('h3', {
                style: {
                  margin: 0,
                  fontSize: '1.25rem',
                  fontWeight: 900,
                  color: '#f8fafc',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 10,
                  letterSpacing: '0.04em'
                }
              },
                '🏔️ MAHARASHTRA EXPEDITION ROUTE',
                React.createElement('span', {
                  style: {
                    fontSize: '0.70rem',
                    padding: '2px 8px',
                    borderRadius: 4,
                    background: 'rgba(245, 158, 11, 0.2)',
                    color: '#fbbf24',
                    fontWeight: 800,
                    border: '1px solid rgba(245, 158, 11, 0.4)',
                    letterSpacing: '0.05em'
                  }
                }, worldData.cycleTag)
              ),
              React.createElement('div', {
                style: {
                  fontSize: '0.78rem',
                  color: '#94a3b8',
                  marginTop: 3
                }
              }, `A 1,000-level cultural odyssey across Sahyadri peaks, sea forts, river ghats, and royal citadels.`)
            ),
            React.createElement('button', {
              type: 'button',
              onClick: () => {
                setShowJourneyModal(false);
                setSelectedLocationDetail(null);
              },
              style: {
                background: 'rgba(30, 41, 59, 0.6)',
                border: '1px solid rgba(148, 163, 184, 0.3)',
                color: '#e2e8f0',
                padding: '6px 12px',
                fontSize: '1.1rem',
                cursor: 'pointer',
                borderRadius: 6,
                transition: 'all 0.2s ease'
              }
            }, '✕')
          ),

          // Scrollable Illustrated Vertical Expedition Route
          React.createElement('div', {
            style: {
              overflowY: 'auto',
              paddingRight: 6,
              display: 'flex',
              flexDirection: 'column',
              gap: 12,
              maxHeight: '58vh',
              position: 'relative'
            }
          },
            journeyData && journeyData.timeline.map((item, idx) => {
              const isSelected = selectedLocationDetail && selectedLocationDetail.index === item.index;
              const prevItem = idx > 0 ? journeyData.timeline[idx - 1] : null;
              const isNewRegion = !prevItem || prevItem.region !== item.region;
              const regionMeta = REGION_META[item.region] || { title: item.region, icon: '🚩', color: '#f59e0b' };

              return React.createElement(React.Fragment, { key: `expedition_node_${item.index}_${item.levelStart}` },
                // Regional Header / Section Division Banner
                isNewRegion && React.createElement('div', {
                  style: {
                    display: 'flex',
                    alignItems: 'center',
                    gap: 10,
                    padding: '8px 12px',
                    borderRadius: 6,
                    background: 'rgba(15, 23, 42, 0.8)',
                    border: `1px solid ${regionMeta.color}40`,
                    marginTop: idx > 0 ? 8 : 0,
                    marginBottom: 2
                  }
                },
                  React.createElement('span', { style: { fontSize: '1.1rem' } }, regionMeta.icon),
                  React.createElement('div', null,
                    React.createElement('div', {
                      style: {
                        fontWeight: 900,
                        fontSize: '0.78rem',
                        color: regionMeta.color,
                        letterSpacing: '0.08em'
                      }
                    }, regionMeta.title),
                    regionMeta.subtitle && React.createElement('div', {
                      style: { fontSize: '0.68rem', color: '#94a3b8' }
                    }, regionMeta.subtitle)
                  )
                ),

                // Destination Card Node
                item.isLocked ? (
                  // =========================================================
                  // STATE 1: MYSTERY DESTINATION (Fog-of-War Shrouded)
                  // =========================================================
                  React.createElement('div', {
                    style: {
                      display: 'flex',
                      alignItems: 'center',
                      gap: 14,
                      padding: '12px 16px',
                      borderRadius: 8,
                      background: 'rgba(15, 23, 42, 0.5)',
                      border: '1.5px dashed rgba(148, 163, 184, 0.2)',
                      opacity: 0.7,
                      position: 'relative'
                    }
                  },
                    // Locked Icon Container
                    React.createElement('div', {
                      style: {
                        width: 38,
                        height: 38,
                        borderRadius: '50%',
                        background: 'rgba(30, 41, 59, 0.6)',
                        border: '1px solid rgba(148, 163, 184, 0.25)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        fontSize: '1rem',
                        color: '#64748b'
                      }
                    }, '🔒'),
                    // Concealed Fog-of-War Title
                    React.createElement('div', { style: { flex: 1 } },
                      React.createElement('div', {
                        style: {
                          fontWeight: 800,
                          fontSize: '0.88rem',
                          letterSpacing: '0.12em',
                          color: '#94a3b8'
                        }
                      }, item.name),
                      React.createElement('div', {
                        style: {
                          fontSize: '0.72rem',
                          color: '#64748b',
                          marginTop: 2
                        }
                      }, `Unlocks upon reaching Level ${item.levelStart}`)
                    ),
                    // Level Range Tag
                    React.createElement('div', {
                      style: {
                        fontSize: '0.72rem',
                        fontWeight: 700,
                        color: '#64748b',
                        textAlign: 'right'
                      }
                    }, `Levels ${item.levelStart}–${item.levelEnd}`)
                  )
                ) : item.isCurrent ? (
                  // =========================================================
                  // STATE 2: ACTIVE CURRENT DESTINATION (Prominent Illuminated)
                  // =========================================================
                  React.createElement('div', {
                    ref: activeNodeRef,
                    onClick: () => setSelectedLocationDetail(isSelected ? null : item),
                    style: {
                      display: 'flex',
                      flexDirection: 'column',
                      gap: 10,
                      padding: '16px 18px',
                      borderRadius: 10,
                      background: 'linear-gradient(135deg, rgba(30, 41, 59, 0.95) 0%, rgba(15, 23, 42, 0.95) 100%)',
                      border: '2px solid #f59e0b',
                      boxShadow: '0 0 25px rgba(245, 158, 11, 0.35), inset 0 0 15px rgba(245, 158, 11, 0.15)',
                      cursor: 'pointer',
                      position: 'relative'
                    }
                  },
                    // "YOU ARE HERE" Saffron Badge
                    React.createElement('div', {
                      style: {
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between'
                      }
                    },
                      React.createElement('div', {
                        style: {
                          display: 'flex',
                          alignItems: 'center',
                          gap: 6,
                          fontSize: '0.72rem',
                          fontWeight: 900,
                          color: '#fbbf24',
                          letterSpacing: '0.08em',
                          animation: 'beaconGlow 2s infinite ease-in-out'
                        }
                      },
                        '✨ YOU ARE HERE',
                        React.createElement('span', { style: { color: 'rgba(251, 191, 36, 0.5)' } }, '•'),
                        `Level ${safeLevel} / 1000`
                      ),
                      React.createElement('div', {
                        style: {
                          fontSize: '0.74rem',
                          fontWeight: 800,
                          color: '#f59e0b'
                        }
                      }, `Checkpoint ${item.progressInLocation} / ${item.levelsPerLocation} (${item.progressPercent}%)`)
                    ),

                    // Destination Info Row
                    React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 14 } },
                      React.createElement('div', {
                        style: {
                          width: 44,
                          height: 44,
                          borderRadius: '50%',
                          background: 'rgba(245, 158, 11, 0.18)',
                          border: '2px solid #f59e0b',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          fontSize: '1.4rem',
                          boxShadow: '0 0 12px rgba(245, 158, 11, 0.5)'
                        }
                      }, item.icon || (isFinale ? '👑' : '🏰')),
                      React.createElement('div', { style: { flex: 1 } },
                        React.createElement('div', {
                          style: {
                            fontWeight: 900,
                            fontSize: '1.05rem',
                            color: '#ffffff',
                            letterSpacing: '0.02em',
                            display: 'flex',
                            alignItems: 'center',
                            gap: 8
                          }
                        },
                          item.name.toUpperCase(),
                          isFinale && React.createElement('span', {
                            style: {
                              fontSize: '0.65rem',
                              padding: '2px 6px',
                              borderRadius: 4,
                              background: '#ef4444',
                              color: '#fff',
                              fontWeight: 900
                            }
                          }, 'FINALE')
                        ),
                        React.createElement('div', {
                          style: { fontSize: '0.78rem', color: '#cbd5e1', marginTop: 2 }
                        }, `${item.subtitle} • ${item.district} District`)
                      ),
                      React.createElement('div', {
                        style: {
                          fontSize: '0.74rem',
                          fontWeight: 800,
                          color: '#f59e0b',
                          textAlign: 'right'
                        }
                      }, `Lvl ${item.levelStart}–${item.levelEnd}`)
                    ),

                    // Active Saffron Progress Bar
                    React.createElement('div', {
                      style: {
                        width: '100%',
                        height: 6,
                        background: 'rgba(51, 65, 85, 0.6)',
                        borderRadius: 3,
                        overflow: 'hidden'
                      }
                    },
                      React.createElement('div', {
                        style: {
                          width: `${item.progressPercent}%`,
                          height: '100%',
                          background: 'linear-gradient(90deg, #f59e0b 0%, #ef4444 100%)',
                          borderRadius: 3,
                          boxShadow: '0 0 8px rgba(245, 158, 11, 0.8)',
                          transition: 'width 0.4s ease'
                        }
                      })
                    ),

                    // Cultural Lore Description Snippet
                    item.culturalNote && React.createElement('div', {
                      style: {
                        fontSize: '0.74rem',
                        color: '#94a3b8',
                        background: 'rgba(15, 23, 42, 0.75)',
                        padding: '8px 12px',
                        borderRadius: 6,
                        border: '1px solid rgba(245, 158, 11, 0.2)',
                        lineHeight: 1.45
                      }
                    }, item.culturalNote)
                  )
                ) : (
                  // =========================================================
                  // STATE 3: COMPLETED DESTINATION (Illuminated Gold Badge)
                  // =========================================================
                  React.createElement('div', {
                    onClick: () => setSelectedLocationDetail(isSelected ? null : item),
                    className: 'expedition-card-hover',
                    style: {
                      display: 'flex',
                      alignItems: 'center',
                      gap: 14,
                      padding: '11px 16px',
                      borderRadius: 8,
                      background: isSelected ? 'rgba(30, 41, 59, 0.9)' : 'rgba(15, 23, 42, 0.65)',
                      border: isSelected ? '1px solid #f59e0b' : '1px solid rgba(51, 65, 85, 0.5)',
                      cursor: 'pointer',
                      transition: 'all 0.2s ease'
                    }
                  },
                    // Explored Gold Checkmark Badge
                    React.createElement('div', {
                      style: {
                        width: 34,
                        height: 34,
                        borderRadius: '50%',
                        background: 'rgba(16, 185, 129, 0.15)',
                        border: '1.5px solid #10b981',
                        color: '#34d399',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        fontSize: '0.92rem',
                        fontWeight: 900
                      }
                    }, '✓'),
                    // Location Details
                    React.createElement('div', { style: { flex: 1 } },
                      React.createElement('div', {
                        style: {
                          fontWeight: 800,
                          fontSize: '0.90rem',
                          color: '#e2e8f0',
                          display: 'flex',
                          alignItems: 'center',
                          gap: 6
                        }
                      },
                        item.name,
                        React.createElement('span', { style: { fontSize: '0.85rem' } }, item.icon)
                      ),
                      React.createElement('div', {
                        style: { fontSize: '0.72rem', color: '#94a3b8', marginTop: 1 }
                      }, `${item.district} • Explored`)
                    ),
                    // Explored 100% Badge
                    React.createElement('div', {
                      style: {
                        fontSize: '0.70rem',
                        fontWeight: 800,
                        color: '#34d399',
                        padding: '2px 8px',
                        borderRadius: 4,
                        background: 'rgba(16, 185, 129, 0.12)',
                        border: '1px solid rgba(16, 185, 129, 0.25)'
                      }
                    }, '✓ EXPLORED')
                  )
                )
              );
            })
          ),

          // Modal Footer: Expedition Stats Summary
          React.createElement('div', {
            style: {
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              borderTop: '1px solid rgba(245, 158, 11, 0.25)',
              paddingTop: 12,
              fontSize: '0.78rem',
              color: '#94a3b8'
            }
          },
            React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 6 } },
              '🏆 Explored:',
              React.createElement('strong', { style: { color: '#fbbf24' } },
                `${journeyData ? journeyData.completedLocationsCount : 0}`
              ),
              'Maharashtra Landmarks'
            ),
            React.createElement('button', {
              type: 'button',
              onClick: () => {
                setShowJourneyModal(false);
                setSelectedLocationDetail(null);
              },
              style: {
                background: 'linear-gradient(135deg, #f59e0b 0%, #d97706 100%)',
                color: '#0f172a',
                border: 'none',
                padding: '6px 20px',
                fontWeight: 900,
                fontSize: '0.82rem',
                borderRadius: 6,
                cursor: 'pointer',
                letterSpacing: '0.04em',
                boxShadow: '0 0 15px rgba(245, 158, 11, 0.3)'
              }
            }, 'RETURN TO EXPEDITION')
          )
        )
      ))
    );
  }

  return MaharashtraJourney;
}));
