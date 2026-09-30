/**
 * TrophyProgressBar.js
 * Reusable animated Trophy Milestone Progress Bar with locked tier preview
 * and Maharashtra Location context for Take a Break.
 */

(function (root, factory) {
  if (typeof define === 'function' && define.amd) {
    define(['react', '../maharashtraWorld'], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory(require('react'), require('../maharashtraWorld'));
  } else {
    root.TrophyProgressBar = factory(root.React, root.MaharashtraWorld);
  }
}(typeof self !== 'undefined' ? self : this, function (React, MaharashtraWorld) {
  'use strict';

  const { useState, useEffect, useRef } = React;
  const WorldEngine = MaharashtraWorld || (typeof window !== 'undefined' ? window.MaharashtraWorld : null);

  // Unified Progressive Trophy Tiers across Modern and Classic games
  const TROPHY_TIERS = [
    { maxMilestone: 5,   circuitName: 'Bronze Apprentice',   snakeName: 'Bronze Viper',       icon: '🥉', color: '#cd7f32', tier: 'Bronze' },
    { maxMilestone: 10,  circuitName: 'Silver Technician',   snakeName: 'Silver Cobra',        icon: '🥈', color: '#c0c0c0', tier: 'Silver' },
    { maxMilestone: 20,  circuitName: 'Gold Engineer',       snakeName: 'Gold Python',         icon: '🥇', color: '#ffd700', tier: 'Gold' },
    { maxMilestone: 50,  circuitName: 'Platinum Specialist', snakeName: 'Platinum Anaconda',   icon: '💎', color: '#00e5ff', tier: 'Platinum' },
    { maxMilestone: 100, circuitName: 'Diamond Architect',   snakeName: 'Diamond Ouroboros',   icon: '👑', color: '#e040fb', tier: 'Diamond' },
    { maxMilestone: Infinity, circuitName: 'Cosmic Grandmaster', snakeName: 'Cosmic Leviathan', icon: '⚡', color: '#ff9100', tier: 'Cosmic' }
  ];

  function getTrophyData(milestoneNumber, gameType = 'modern') {
    if (!milestoneNumber || milestoneNumber < 1) {
      return {
        milestoneNumber: 0,
        name: gameType === 'classic' ? 'Novice Serpent' : 'Novice Electrician',
        icon: '🔰',
        color: 'var(--text-secondary, #94a3b8)',
        tier: 'Novice',
        starThreshold: 0
      };
    }

    for (let i = 0; i < TROPHY_TIERS.length; i++) {
      if (milestoneNumber <= TROPHY_TIERS[i].maxMilestone) {
        const t = TROPHY_TIERS[i];
        const name = gameType === 'classic' ? t.snakeName : t.circuitName;
        return {
          milestoneNumber,
          starThreshold: milestoneNumber * 50,
          tier: t.tier,
          name: `${name} (M${milestoneNumber})`,
          icon: t.icon,
          color: t.color
        };
      }
    }
    const last = TROPHY_TIERS[TROPHY_TIERS.length - 1];
    const name = gameType === 'classic' ? last.snakeName : last.circuitName;
    return {
      milestoneNumber,
      starThreshold: milestoneNumber * 50,
      tier: last.tier,
      name: `${name} (M${milestoneNumber})`,
      icon: last.icon,
      color: last.color
    };
  }

  function TrophyProgressBar({ totalStars = 0, currentLevel = 1, gameType = 'modern' }) {
    const stars = Math.max(0, Math.floor(totalStars || 0));
    const milestoneIndex = Math.floor(stars / 50);
    const progressStars = stars % 50;
    const progressPercent = Math.min(100, Math.max(0, (progressStars / 50) * 100));
    const nextMilestoneNumber = milestoneIndex + 1;
    const nextMilestoneStars = nextMilestoneNumber * 50;
    const starsRemaining = Math.max(1, 50 - progressStars);

    const currentTrophy = getTrophyData(milestoneIndex, gameType);
    const nextTrophy = getTrophyData(nextMilestoneNumber, gameType);

    // Dynamic World Location calculation
    const worldData = WorldEngine ? WorldEngine.getWorldForLevel(gameType, currentLevel) : null;

    // Smooth percentage interpolation state
    const [animatedPercent, setAnimatedPercent] = useState(0);

    useEffect(() => {
      const timer = setTimeout(() => {
        setAnimatedPercent(progressPercent);
      }, 50);
      return () => clearTimeout(timer);
    }, [progressPercent]);

    return React.createElement('div', {
      className: 'trophy-milestone-panel',
      style: {
        background: 'rgba(255, 255, 255, 0.03)',
        border: '1px solid var(--border, rgba(255,255,255,0.1))',
        borderRadius: 12,
        padding: '16px 18px',
        display: 'flex',
        flexDirection: 'column',
        gap: 12,
        width: '100%'
      }
    },
      // Header: Current Trophy & Milestone Tag
      React.createElement('div', {
        style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 8 }
      },
        React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 8 } },
          React.createElement('span', { style: { fontSize: '1.6rem', filter: `drop-shadow(0 0 6px ${currentTrophy.color})` } }, currentTrophy.icon),
          React.createElement('div', null,
            React.createElement('div', { style: { fontSize: '0.98rem', fontWeight: 800, color: currentTrophy.color, letterSpacing: '-0.01em' } },
              currentTrophy.name
            ),
            React.createElement('div', { style: { fontSize: '0.74rem', color: 'var(--text-secondary, #94a3b8)' } },
              milestoneIndex > 0 ? `${currentTrophy.tier} Tier • Milestone ${milestoneIndex}` : 'Starting Journey'
            )
          )
        ),
        React.createElement('div', {
          style: {
            background: 'rgba(251, 191, 36, 0.12)',
            border: '1px solid rgba(251, 191, 36, 0.3)',
            borderRadius: 6,
            padding: '3px 8px',
            fontSize: '0.78rem',
            fontWeight: 700,
            color: '#fbbf24'
          }
        },
          `⭐ ${stars} Stars`
        )
      ),

      // Animated Star Progress Bar
      React.createElement('div', { style: { display: 'flex', flexDirection: 'column', gap: 4 } },
        React.createElement('div', {
          style: { display: 'flex', justifyContent: 'space-between', fontSize: '0.75rem', color: 'var(--text-secondary, #94a3b8)', fontWeight: 600 }
        },
          React.createElement('span', null, `${progressStars} / 50 ⭐ towards next Trophy`),
          React.createElement('span', null, `${Math.round(progressPercent)}%`)
        ),
        React.createElement('div', {
          style: {
            width: '100%',
            height: 10,
            background: 'rgba(0, 0, 0, 0.35)',
            borderRadius: 5,
            overflow: 'hidden',
            border: '1px solid rgba(255, 255, 255, 0.08)',
            position: 'relative'
          }
        },
          React.createElement('div', {
            className: 'trophy-progress-fill',
            style: {
              width: `${animatedPercent}%`,
              height: '100%',
              background: `linear-gradient(90deg, ${currentTrophy.color || '#38bdf8'}, ${nextTrophy.color || '#fbbf24'})`,
              borderRadius: 5,
              transition: 'width 0.6s cubic-bezier(0.34, 1.56, 0.64, 1)',
              boxShadow: `0 0 10px ${nextTrophy.color}66`
            }
          })
        )
      ),

      // Next Locked Trophy Preview
      React.createElement('div', {
        style: {
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          background: 'rgba(0, 0, 0, 0.2)',
          borderRadius: 8,
          padding: '8px 12px',
          border: '1px dashed rgba(255, 255, 255, 0.12)'
        }
      },
        React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 6 } },
          React.createElement('span', { style: { fontSize: '1rem', opacity: 0.6 } }, '🔒'),
          React.createElement('span', { style: { fontSize: '0.82rem', fontWeight: 600, color: 'var(--text, #e2e8f0)' } },
            `Next: ${nextTrophy.name}`
          )
        ),
        React.createElement('div', { style: { fontSize: '0.74rem', color: '#fbbf24', fontWeight: 700 } },
          `${starsRemaining} ⭐ remaining`
        )
      ),

      // Maharashtra Location World Context (if available)
      worldData && React.createElement('div', {
        style: {
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          borderTop: '1px solid rgba(255, 255, 255, 0.06)',
          paddingTop: 8,
          fontSize: '0.76rem',
          color: 'var(--text-secondary, #94a3b8)'
        }
      },
        React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 4 } },
          React.createElement('span', null, '📍'),
          React.createElement('strong', { style: { color: worldData.location.themeColor || '#38bdf8' } }, worldData.location.name),
          React.createElement('span', null, `• ${worldData.location.district}`)
        ),
        React.createElement('div', null,
          `Level ${worldData.progressInLocation}/${worldData.levelsPerLocation} • ${worldData.cycleTag}`
        )
      )
    );
  }

  return TrophyProgressBar;
}));
