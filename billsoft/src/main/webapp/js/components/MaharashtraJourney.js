/**
 * MaharashtraJourney.js
 * Redesigned "Journey Through Maharashtra" Progression Component & Interactive Modal.
 * Features:
 * - Replaces static location banner with an interactive journey progression bar.
 * - Shows current destination, level progress ("4 of 15 completed"), node path, and locked mystery next destination.
 * - Clicking opens the full Maharashtra Journey Map Modal with visited checkmarks, active glowing waypoint, and locked mystery destinations.
 * - Respects mystery destination concealment until unlocked.
 * - Accessible, responsive, and theme-adaptive.
 */

(function (root, factory) {
  if (typeof define === 'function' && define.amd) {
    define(['react', '../maharashtraWorld'], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory(require('react'), require('../maharashtraWorld'));
  } else {
    root.MaharashtraJourney = factory(root.React, root.MaharashtraWorld);
  }
}(typeof self !== 'undefined' ? self : this, function (React, MaharashtraWorld) {
  'use strict';

  const { useState, useMemo } = React;
  const WorldEngine = MaharashtraWorld || (typeof window !== 'undefined' ? window.MaharashtraWorld : null);

  function renderModalPortal(element) {
    if (typeof document !== 'undefined' && document.body && typeof ReactDOM !== 'undefined' && ReactDOM.createPortal) {
      return ReactDOM.createPortal(element, document.body);
    }
    return element;
  }

  function MaharashtraJourney({ currentLevel = 1, gameType = 'modern' }) {
    const [showJourneyModal, setShowJourneyModal] = useState(false);
    const [selectedLocationDetail, setSelectedLocationDetail] = useState(null);

    const safeLevel = Math.max(1, Math.floor(currentLevel || 1));

    // Resolve current world data
    const worldData = useMemo(() => {
      if (!WorldEngine) return null;
      return WorldEngine.getWorldForLevel(gameType, safeLevel);
    }, [gameType, safeLevel]);

    // Resolve mystery next destination teaser
    const mysteryNext = useMemo(() => {
      if (!WorldEngine) return null;
      return WorldEngine.getMysteryNextLocation(gameType, safeLevel);
    }, [gameType, safeLevel]);

    // Resolve full journey timeline for the modal
    const journeyData = useMemo(() => {
      if (!WorldEngine) return null;
      return WorldEngine.getJourneyTimeline(gameType, safeLevel, 15, 9);
    }, [gameType, safeLevel]);

    if (!worldData || !worldData.location) return null;

    const loc = worldData.location;
    const progressInLoc = worldData.progressInLocation;
    const totalInLoc = worldData.levelsPerLocation;
    const percent = worldData.progressPercent;
    const themeColor = loc.themeColor || '#38bdf8';

    // Build 7-node visual route for the compact header bar
    // Nodes: 5 step marks in location + current active node + next locked destination node
    const nodeCount = 6;
    const nodes = [];
    const activeStepRatio = (progressInLoc - 1) / Math.max(1, totalInLoc - 1);
    const activeNodeIndex = Math.min(nodeCount - 1, Math.floor(activeStepRatio * (nodeCount - 1)));

    for (let i = 0; i < nodeCount; i++) {
      const isPast = i < activeNodeIndex;
      const isCurrent = i === activeNodeIndex;
      nodes.push({ index: i, isPast, isCurrent });
    }

    return React.createElement(React.Fragment, null,
      // Compact Journey Progression Header (Native RupeeCRM Surface)
      React.createElement('div', {
        className: 'maharashtra-journey-card',
        onClick: () => setShowJourneyModal(true),
        role: 'button',
        tabIndex: 0,
        'aria-label': `Maharashtra Journey: Currently exploring ${loc.name}, level ${safeLevel}. Click to view full Journey Map.`,
        onKeyDown: (e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            setShowJourneyModal(true);
          }
        },
        style: {
          width: '100%',
          maxWidth: 860,
          background: 'var(--bg-card, #ffffff)',
          border: '1px solid var(--border, #e2e8f0)',
          borderRadius: 'var(--radius, 8px)',
          padding: '6px 14px',
          marginBottom: 6,
          cursor: 'pointer',
          boxShadow: 'var(--shadow, 0 1px 2px rgba(0, 0, 0, 0.05))',
          display: 'flex',
          flexDirection: 'column',
          gap: 4,
          transition: 'border-color 0.15s ease, box-shadow 0.15s ease',
          userSelect: 'none',
          boxSizing: 'border-box'
        }
      },
        // Row 1: Header + Location & Progress Count
        React.createElement('div', {
          style: {
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            fontSize: '0.78rem',
            fontWeight: 800,
            letterSpacing: '0.04em'
          }
        },
          React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 6 } },
            React.createElement('span', { style: { fontSize: '0.9rem' } }, loc.icon || '🗺️'),
            React.createElement('span', { style: { color: 'var(--text-secondary, #64748b)', textTransform: 'uppercase', fontSize: '0.74rem' } }, 'MAHARASHTRA JOURNEY'),
            React.createElement('span', { style: { color: 'var(--border, #cbd5e1)' } }, '·'),
            React.createElement('span', { style: { color: 'var(--text, #1e293b)', fontWeight: 800 } }, loc.name.toUpperCase()),
            React.createElement('span', {
              style: {
                fontSize: '0.65rem',
                fontWeight: 700,
                padding: '1px 6px',
                borderRadius: 4,
                background: 'var(--surface-sunken, #f1f5f9)',
                color: 'var(--primary, #4f46e5)',
                border: '1px solid var(--border, #e2e8f0)'
              }
            }, loc.district.toUpperCase())
          ),
          React.createElement('div', {
            style: {
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              fontSize: '0.74rem',
              fontWeight: 700
            }
          },
            React.createElement('span', { style: { color: 'var(--text-secondary, #64748b)' } },
              `${progressInLoc} / ${totalInLoc} completed (${percent}%)`
            ),
            React.createElement('span', {
              style: {
                background: 'var(--surface-sunken, #f1f5f9)',
                padding: '2px 8px',
                borderRadius: 4,
                color: 'var(--primary, #4f46e5)',
                border: '1px solid var(--border, #e2e8f0)',
                fontSize: '0.68rem',
                fontWeight: 700
              }
            }, '🗺️ Map ➔')
          )
        ),

        // Row 2: Route Track with Nodes + Mystery Next Teaser
        React.createElement('div', {
          style: {
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 12,
            position: 'relative'
          }
        },
          // Route nodes with track
          React.createElement('div', {
            style: {
              display: 'flex',
              alignItems: 'center',
              flex: 1,
              position: 'relative',
              height: 16
            }
          },
            // Background line
            React.createElement('div', {
              style: {
                position: 'absolute',
                left: 6,
                right: 20,
                height: 2,
                background: 'var(--border, #e2e8f0)',
                borderRadius: 1,
                zIndex: 1
              }
            }),
            // Completed fill line
            React.createElement('div', {
              style: {
                position: 'absolute',
                left: 6,
                width: `calc(${percent}% * 0.85)`,
                height: 2,
                background: 'var(--primary, #4f46e5)',
                borderRadius: 1,
                zIndex: 2,
                transition: 'width 0.4s ease'
              }
            }),
            // Nodes container
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
                  title: node.isCurrent ? `You are here: ${loc.name} (${progressInLoc}/${totalInLoc})` : (node.isPast ? 'Completed stage' : 'Upcoming stage'),
                  style: {
                    width: node.isCurrent ? 12 : 8,
                    height: node.isCurrent ? 12 : 8,
                    borderRadius: '50%',
                    background: node.isCurrent
                      ? 'var(--primary, #4f46e5)'
                      : node.isPast ? 'var(--primary, #4f46e5)' : 'var(--bg-card, #ffffff)',
                    border: node.isCurrent
                      ? '2px solid var(--bg-card, #ffffff)'
                      : (node.isPast ? 'none' : '1.5px solid var(--border, #cbd5e1)'),
                    boxShadow: node.isCurrent ? '0 0 6px rgba(79, 70, 229, 0.4)' : 'none',
                    transition: 'all 0.3s ease'
                  }
                });
              }),
              // Locked mystery next node
              React.createElement('div', {
                title: mysteryNext ? `Next: ${mysteryNext.displayName} (Unlocks Level ${mysteryNext.unlocksAtLevel})` : 'Next destination',
                style: {
                  width: 16,
                  height: 16,
                  borderRadius: '50%',
                  background: 'var(--surface-sunken, #f1f5f9)',
                  border: '1px dashed var(--border, #cbd5e1)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: '0.62rem',
                  color: 'var(--text-muted, #94a3b8)'
                }
              }, '🔒')
            )
          ),

          // Mystery Next Destination Pill
          mysteryNext && React.createElement('div', {
            style: {
              fontSize: '0.68rem',
              fontWeight: 700,
              color: 'var(--text-secondary, #64748b)',
              background: 'var(--surface-sunken, #f1f5f9)',
              padding: '2px 8px',
              borderRadius: 4,
              border: '1px solid var(--border, #e2e8f0)',
              whiteSpace: 'nowrap'
            }
          },
            `NEXT: ${mysteryNext.displayName} · LEVEL ${mysteryNext.unlocksAtLevel}`
          )
        )
      ),

      // Interactive Maharashtra Journey Map Modal
      showJourneyModal && renderModalPortal(React.createElement('div', {
        className: 'modal-overlay',
        style: {
          position: 'fixed',
          inset: 0,
          background: 'rgba(15, 23, 42, 0.75)',
          backdropFilter: 'blur(8px)',
          WebkitBackdropFilter: 'blur(8px)',
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
            background: 'var(--bg-card, #ffffff)',
            border: '1px solid var(--border, #e2e8f0)',
            borderRadius: 'var(--radius-lg, 12px)',
            padding: 22,
            maxWidth: 580,
            width: '92%',
            maxHeight: '85vh',
            display: 'flex',
            flexDirection: 'column',
            gap: 14,
            boxShadow: 'var(--shadow-lg, 0 20px 25px -5px rgba(0,0,0,0.1))',
            animation: 'milestonePop 0.3s ease',
            overflow: 'hidden'
          },
          onClick: e => e.stopPropagation()
        },
          // Modal Header
          React.createElement('div', {
            style: {
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              borderBottom: '1px solid var(--border, #e2e8f0)',
              paddingBottom: 12
            }
          },
            React.createElement('div', null,
              React.createElement('h3', {
                style: {
                  margin: 0,
                  fontSize: '1.2rem',
                  fontWeight: 800,
                  color: 'var(--text, #1e293b)',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8
                }
              },
                '🏔️ Maharashtra Journey Map',
                React.createElement('span', {
                  style: {
                    fontSize: '0.72rem',
                    padding: '2px 8px',
                    borderRadius: 6,
                    background: 'var(--primary-light, #eef2ff)',
                    color: 'var(--primary, #4f46e5)',
                    fontWeight: 700,
                    border: '1px solid var(--border, #e2e8f0)'
                  }
                }, worldData.cycleTag)
              ),
              React.createElement('div', {
                style: {
                  fontSize: '0.78rem',
                  color: 'var(--text-secondary, #64748b)',
                  marginTop: 2
                }
              }, `Explore historic forts, river ghats, wadas, and sanctuaries across Maharashtra.`)
            ),
            React.createElement('button', {
              type: 'button',
              className: 'btn btn-ghost',
              onClick: () => {
                setShowJourneyModal(false);
                setSelectedLocationDetail(null);
              },
              style: { padding: '4px 8px', fontSize: '1.2rem', cursor: 'pointer' }
            }, '✕')
          ),

          // Journey Route & Locations List (Scrollable)
          React.createElement('div', {
            style: {
              overflowY: 'auto',
              paddingRight: 4,
              display: 'flex',
              flexDirection: 'column',
              gap: 10,
              maxHeight: '52vh'
            }
          },
            journeyData && journeyData.timeline.map((item) => {
              const isSelected = selectedLocationDetail && selectedLocationDetail.index === item.index;

              if (item.isLocked) {
                // Mystery Locked Next Destination
                return React.createElement('div', {
                  key: `loc_locked_${item.index}`,
                  style: {
                    display: 'flex',
                    alignItems: 'center',
                    gap: 12,
                    padding: '12px 14px',
                    borderRadius: 'var(--radius, 8px)',
                    background: 'var(--surface-sunken, #f8fafc)',
                    border: '1.5px dashed var(--border, #cbd5e1)',
                    opacity: 0.75
                  }
                },
                  React.createElement('div', {
                    style: {
                      width: 36,
                      height: 36,
                      borderRadius: '50%',
                      background: 'var(--bg-card, #ffffff)',
                      border: '1px solid var(--border, #e2e8f0)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontSize: '1rem',
                      color: 'var(--text-muted, #94a3b8)'
                    }
                  }, '🔒'),
                  React.createElement('div', { style: { flex: 1 } },
                    React.createElement('div', {
                      style: {
                        fontWeight: 800,
                        fontSize: '0.92rem',
                        letterSpacing: '0.1em',
                        color: 'var(--text-secondary, #64748b)'
                      }
                    }, item.name),
                    React.createElement('div', {
                      style: { fontSize: '0.74rem', color: 'var(--text-muted, #94a3b8)' }
                    }, item.subtitle)
                  ),
                  React.createElement('div', {
                    style: {
                      fontSize: '0.72rem',
                      fontWeight: 700,
                      color: 'var(--text-muted, #94a3b8)',
                      textAlign: 'right'
                    }
                  }, `Levels ${item.levelStart}–${item.levelEnd}`)
                );
              }

              if (item.isCurrent) {
                // Active Current Destination (Glowing)
                return React.createElement('div', {
                  key: `loc_current_${item.index}`,
                  onClick: () => setSelectedLocationDetail(isSelected ? null : item),
                  style: {
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 8,
                    padding: '14px 16px',
                    borderRadius: 'var(--radius, 8px)',
                    background: 'var(--primary-light, #eef2ff)',
                    border: `2px solid var(--primary, #4f46e5)`,
                    boxShadow: 'var(--shadow, 0 1px 3px rgba(0,0,0,0.05))',
                    cursor: 'pointer'
                  }
                },
                  React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 12 } },
                    React.createElement('div', {
                      style: {
                        width: 40,
                        height: 40,
                        borderRadius: '50%',
                        background: 'var(--bg-card, #ffffff)',
                        border: `1.5px solid var(--primary, #4f46e5)`,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        fontSize: '1.3rem'
                      }
                    }, item.icon || '📍'),
                    React.createElement('div', { style: { flex: 1 } },
                      React.createElement('div', {
                        style: {
                          display: 'flex',
                          alignItems: 'center',
                          gap: 8,
                          fontWeight: 800,
                          fontSize: '0.96rem',
                          color: 'var(--text, #1e293b)'
                        }
                      },
                        `➔ ${item.name.toUpperCase()}`,
                        React.createElement('span', {
                          style: {
                            fontSize: '0.65rem',
                            fontWeight: 800,
                            padding: '2px 6px',
                            borderRadius: 4,
                            background: 'var(--primary, #4f46e5)',
                            color: '#ffffff'
                          }
                        }, 'CURRENT')
                      ),
                      React.createElement('div', {
                        style: { fontSize: '0.76rem', color: 'var(--text-secondary, #64748b)' }
                      }, `${item.subtitle} • ${item.district}`)
                    ),
                    React.createElement('div', { style: { textAlign: 'right' } },
                      React.createElement('div', { style: { fontSize: '0.8rem', fontWeight: 800, color: 'var(--primary, #4f46e5)' } },
                        `${item.progressInLocation} / ${item.levelsPerLocation}`
                      ),
                      React.createElement('div', { style: { fontSize: '0.7rem', color: 'var(--text-secondary, #64748b)' } },
                        `Lvl ${item.levelStart}–${item.levelEnd}`
                      )
                    )
                  ),
                  // Progress Bar
                  React.createElement('div', {
                    style: {
                      width: '100%',
                      height: 6,
                      background: 'var(--border, #e2e8f0)',
                      borderRadius: 3,
                      overflow: 'hidden'
                    }
                  },
                    React.createElement('div', {
                      style: {
                        width: `${item.progressPercent}%`,
                        height: '100%',
                        background: 'var(--primary, #4f46e5)',
                        borderRadius: 3,
                        transition: 'width 0.4s ease'
                      }
                    })
                  ),
                  item.culturalNote && React.createElement('div', {
                    style: {
                      fontSize: '0.74rem',
                      color: 'var(--text-secondary, #64748b)',
                      background: 'var(--bg-card, #ffffff)',
                      padding: '6px 10px',
                      borderRadius: 6,
                      border: '1px solid var(--border, #e2e8f0)',
                      lineHeight: 1.4
                    }
                  }, item.culturalNote)
                );
              }

              // Completed Past Destination
              return React.createElement('div', {
                key: `loc_visited_${item.index}`,
                onClick: () => setSelectedLocationDetail(isSelected ? null : item),
                style: {
                  display: 'flex',
                  alignItems: 'center',
                  gap: 12,
                  padding: '10px 14px',
                  borderRadius: 'var(--radius, 8px)',
                  background: isSelected ? 'var(--primary-light, #eef2ff)' : 'var(--surface-sunken, #f8fafc)',
                  border: '1px solid var(--border, #e2e8f0)',
                  cursor: 'pointer',
                  transition: 'background 0.2s ease'
                }
              },
                React.createElement('div', {
                  style: {
                    width: 32,
                    height: 32,
                    borderRadius: '50%',
                    background: 'var(--success-light, #d1fae5)',
                    border: '1px solid var(--success, #059669)',
                    color: 'var(--success, #059669)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: '0.9rem',
                    fontWeight: 900
                  }
                }, '✓'),
                React.createElement('div', { style: { flex: 1 } },
                  React.createElement('div', {
                    style: {
                      fontWeight: 700,
                      fontSize: '0.88rem',
                      color: 'var(--text, #1e293b)',
                      display: 'flex',
                      alignItems: 'center',
                      gap: 6
                    }
                  },
                    item.name,
                    React.createElement('span', { style: { fontSize: '0.8rem' } }, item.icon)
                  ),
                  React.createElement('div', {
                    style: { fontSize: '0.74rem', color: 'var(--text-secondary, #64748b)' }
                  }, `${item.district} · Completed`)
                ),
                React.createElement('div', {
                  style: {
                    fontSize: '0.72rem',
                    fontWeight: 700,
                    color: 'var(--success, #059669)',
                    textAlign: 'right'
                  }
                }, '100% ✓')
              );
            })
          ),

          // Modal Footer / Journey Stats Summary
          React.createElement('div', {
            style: {
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              borderTop: '1px solid var(--border, #e2e8f0)',
              paddingTop: 12,
              fontSize: '0.78rem',
              color: 'var(--text-secondary, #64748b)'
            }
          },
            React.createElement('div', null,
              `Explored ${journeyData ? journeyData.totalExploredLocations : 1} Maharashtra locations`
            ),
            React.createElement('button', {
              type: 'button',
              className: 'btn btn-primary',
              onClick: () => {
                setShowJourneyModal(false);
                setSelectedLocationDetail(null);
              },
              style: { padding: '6px 18px', fontWeight: 700, borderRadius: 6 }
            }, 'Close')
          )
        )
      ))
    );
  }

  return MaharashtraJourney;
}));
