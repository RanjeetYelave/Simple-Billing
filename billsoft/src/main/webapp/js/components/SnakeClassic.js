/**
 * SnakeClassic.js
 * Production-ready, ultra-responsive, high-performance classic Snake game for RupeeCRM.
 * Features:
 * - Dynamic rectangular grid automatically utilizing 90-96% available horizontal stage width
 * - Strictly square cells with zero physical cropping across arbitrary viewport dimensions
 * - Decoupled high-performance imperative canvas rendering loop (zero React input lag / frame jitter)
 * - Immediate keyboard input response (Arrow keys & WASD)
 * - Seamless ResizeObserver with active gameplay coordinate adaptation (no level reset on resize)
 * - Full audio, powerups (Gold Apple, Chill Berry slow-mo, Hopping Fruit, Gateways), milestones & persistence
 */

(function (root, factory) {
  if (typeof define === 'function' && define.amd) {
    define(['react', '../snakeEngine', '../snakeGenerator', '../snakePersistence', '../circuitAudio'], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory(
      require('react'),
      require('../snakeEngine'),
      require('../snakeGenerator'),
      require('../snakePersistence'),
      require('../circuitAudio')
    );
  } else {
    root.SnakeClassic = factory(
      root.React,
      root.SnakeEngine,
      root.SnakeGenerator,
      root.SnakePersistence,
      root.CircuitAudio
    );
  }
}(typeof self !== 'undefined' ? self : this, function (React, SnakeEngine, SnakeGenerator, SnakePersistence, CircuitAudio) {
  'use strict';

  const { useState, useEffect, useRef, useCallback } = React;
  const Engine = SnakeEngine || (typeof window !== 'undefined' ? window.SnakeEngine : null);
  const Generator = SnakeGenerator || (typeof window !== 'undefined' ? window.SnakeGenerator : null);
  const Persistence = SnakePersistence || (typeof window !== 'undefined' ? window.SnakePersistence : null);
  const Audio = CircuitAudio || (typeof window !== 'undefined' ? window.CircuitAudio : null);

  function renderModalPortal(element) {
    if (typeof document !== 'undefined' && document.body && typeof ReactDOM !== 'undefined' && ReactDOM.createPortal) {
      return ReactDOM.createPortal(element, document.body);
    }
    return element;
  }

  // Progressive infinite trophy tiers
  const SNAKE_TROPHY_TIERS = [
    { maxMilestone: 5,   name: 'Bronze Viper',       icon: '🥉', color: '#cd7f32', tier: 'Bronze' },
    { maxMilestone: 10,  name: 'Silver Cobra',        icon: '🥈', color: '#c0c0c0', tier: 'Silver' },
    { maxMilestone: 20,  name: 'Gold Python',         icon: '🥇', color: '#ffd700', tier: 'Gold' },
    { maxMilestone: 50,  name: 'Platinum Anaconda',   icon: '💎', color: '#00e5ff', tier: 'Platinum' },
    { maxMilestone: 100, name: 'Diamond Ouroboros',   icon: '👑', color: '#e040fb', tier: 'Diamond' },
    { maxMilestone: Infinity, name: 'Cosmic Leviathan', icon: '🌌', color: '#ff9100', tier: 'Cosmic' }
  ];

  // Dynamic Level Start Tips (Educating players about novelties, special items & mechanics)
  const SNAKE_LEVEL_TIPS = [
    {
      icon: '🌟',
      badge: 'Golden Apple',
      badgeBg: 'rgba(234, 179, 8, 0.16)',
      badgeBorder: '#eab308',
      badgeColor: '#fde047',
      text: 'Counts as +2 apples toward your goal! Grab it quickly before its sparkle fades.'
    },
    {
      icon: '❄️',
      badge: 'Chill Berry',
      badgeBg: 'rgba(56, 189, 248, 0.16)',
      badgeBorder: '#38bdf8',
      badgeColor: '#7dd3fc',
      text: 'Slows the snake by 35% for 7 seconds — perfect for navigating tight maze corridors!'
    },
    {
      icon: '⚡',
      badge: 'Speed Rush',
      badgeBg: 'rgba(245, 158, 11, 0.16)',
      badgeBorder: '#f59e0b',
      badgeColor: '#fcd34d',
      text: 'Grants a 5-second turbo sprint (+28% speed) to zip across wide open stages.'
    },
    {
      icon: '🎁',
      badge: 'Mystery Box',
      badgeBg: 'rgba(168, 85, 247, 0.16)',
      badgeBorder: '#a855f7',
      badgeColor: '#d8b4fe',
      text: 'Surprise pickup! Spawns random safe rewards: bonus apple surge, freeze, or magnetic pull.'
    },
    {
      icon: '🧲',
      badge: 'Magnet Attractor',
      badgeBg: 'rgba(236, 72, 153, 0.16)',
      badgeBorder: '#ec4899',
      badgeColor: '#f472b6',
      text: 'Generates a magnetic aura for 8s that pulls nearby apples straight into your path!'
    },
    {
      icon: '🌀',
      badge: 'Quantum Gateways',
      badgeBg: 'rgba(6, 182, 212, 0.16)',
      badgeBorder: '#06b6d4',
      badgeColor: '#67e8f9',
      text: 'Slither into one glowing portal to instantly teleport across the board to its twin portal!'
    },
    {
      icon: '🔥',
      badge: 'Combo Multipliers',
      badgeBg: 'rgba(239, 68, 68, 0.16)',
      badgeBorder: '#ef4444',
      badgeColor: '#fca5a5',
      text: 'Eat fruits in quick succession to trigger combo multipliers (x3, x5, x8) and score bursts!'
    },
    {
      icon: '🌴',
      badge: 'Breather Oasis',
      badgeBg: 'rgba(16, 185, 129, 0.16)',
      badgeBorder: '#10b981',
      badgeColor: '#6ee7b7',
      text: 'Every 5th level is an obstacle-free Zen garden to relax, practice smooth turns, and build streaks.'
    },
    {
      icon: '🧱',
      badge: 'Obstacle Bricks',
      badgeBg: 'rgba(100, 116, 139, 0.16)',
      badgeBorder: '#64748b',
      badgeColor: '#cbd5e1',
      text: 'Slate bricks are solid stone! Plan your path 2 turns ahead to avoid getting cornered.'
    },
    {
      icon: '⭐',
      badge: 'Star Ratings',
      badgeBg: 'rgba(245, 158, 11, 0.16)',
      badgeBorder: '#f59e0b',
      badgeColor: '#fde68a',
      text: 'Complete on 1st attempt for 3 Stars ⭐⭐⭐, 2nd attempt for 2 Stars ⭐⭐, 3rd for 1 Star ⭐.'
    },
    {
      icon: '🏆',
      badge: 'Maharashtra Journey',
      badgeBg: 'rgba(139, 92, 246, 0.16)',
      badgeBorder: '#8b5cf6',
      badgeColor: '#c4b5fd',
      text: 'Collect stars to journey across 15 iconic Maharashtra destinations from Raigad to Tadoba!'
    },
    {
      icon: '⌨️',
      badge: 'Pro Controls',
      badgeBg: 'rgba(59, 130, 246, 0.16)',
      badgeBorder: '#3b82f6',
      badgeColor: '#93c5fd',
      text: 'Use Arrow Keys or WASD to navigate. Spacebar or Esc instantly pauses the game anytime.'
    }
  ];

  function getTrophyForMilestone(milestoneNumber) {
    if (!milestoneNumber || milestoneNumber < 1) return null;
    for (let i = 0; i < SNAKE_TROPHY_TIERS.length; i++) {
      if (milestoneNumber <= SNAKE_TROPHY_TIERS[i].maxMilestone) {
        const t = SNAKE_TROPHY_TIERS[i];
        return {
          milestoneNumber,
          starThreshold: milestoneNumber * 50,
          tier: t.tier,
          name: `${t.name} (M${milestoneNumber})`,
          icon: t.icon,
          color: t.color
        };
      }
    }
    return null;
  }

  function formatLifetimeTime(totalSeconds) {
    const s = Math.max(0, Math.floor(totalSeconds || 0));
    const hours = Math.floor(s / 3600);
    const mins = Math.floor((s % 3600) / 60);
    const secs = s % 60;
    if (hours > 0) {
      return `${String(hours).padStart(2, '0')}:${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
    }
    return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
  }

  function SnakeClassic() {
    const [progression, setProgression] = useState(null);
    const [isPaused, setIsPaused] = useState(true);
    const [showLevelGoalCard, setShowLevelGoalCard] = useState(true);
    const [deathState, setDeathState] = useState(null);
    const [isMuted, setIsMuted] = useState(() => (Audio ? Audio.isMuted() : false));
    const [showStatsModal, setShowStatsModal] = useState(false);
    const [milestoneCelebration, setMilestoneCelebration] = useState(null);
    const [levelMilestoneCelebration, setLevelMilestoneCelebration] = useState(null);
    const [levelCelebration, setLevelCelebration] = useState(null);
    const [uiBoardHeader, setUiBoardHeader] = useState(null); // Lightweight state for header/rail
    const [stageDimensions, setStageDimensions] = useState({ width: 600, height: 340 });

    const containerRef = useRef(null);
    const canvasRef = useRef(null);
    const levelMilestoneTimerRef = useRef(null);

    // Live mutable refs for high-frequency game engine
    const boardRef = useRef(null);
    const stateRef = useRef({ progression: null, isPaused: true, deathState: null, showLevelGoalCard: true });
    const layoutRef = useRef({ cols: 24, rows: 14, cellSize: 22, canvasW: 528, canvasH: 308 });
    const gameLoopTimerRef = useRef(null);
    const activeTimerRef = useRef(null);
    const hoppingTimerRef = useRef(null);
    const nextDirectionQueueRef = useRef([]);
    const particlesRef = useRef([]);

    // Synchronize stateRef
    useEffect(() => {
      stateRef.current.progression = progression;
      stateRef.current.isPaused = isPaused;
      stateRef.current.deathState = deathState;
      stateRef.current.showLevelGoalCard = showLevelGoalCard;
    }, [progression, isPaused, deathState, showLevelGoalCard]);

    // High-performance canvas drawing function
    const drawCanvas = useCallback(() => {
      const canvas = canvasRef.current;
      const board = boardRef.current;
      const layout = layoutRef.current;
      if (!canvas || !board || !layout) return;

      const ctx = canvas.getContext('2d');
      if (!ctx) return;

      // Single Source of Truth: board.width & board.height define the grid columns and rows
      const cols = board.width;
      const rows = board.height;
      const snake = board.snake || [];
      const cellSize = layout && layout.cellSize ? layout.cellSize : 22;
      const displayW = cols * cellSize;
      const displayH = rows * cellSize;

      const dpr = typeof window !== 'undefined' ? (window.devicePixelRatio || 1) : 1;
      const bufferW = Math.round(displayW * dpr);
      const bufferH = Math.round(displayH * dpr);

      if (canvas.width !== bufferW || canvas.height !== bufferH) {
        canvas.width = bufferW;
        canvas.height = bufferH;
      }
      canvas.style.width = `${displayW}px`;
      canvas.style.height = `${displayH}px`;

      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

      // 1. Clear background
      ctx.fillStyle = board.chillTimer > 0 ? '#0b1928' : '#0f172a';
      ctx.fillRect(0, 0, displayW, displayH);

      // 2. Draw subtle grid
      ctx.strokeStyle = board.chillTimer > 0 ? 'rgba(0, 229, 255, 0.08)' : 'rgba(255, 255, 255, 0.05)';
      ctx.lineWidth = 1;
      for (let x = 0; x <= cols; x++) {
        ctx.beginPath();
        ctx.moveTo(x * cellSize, 0);
        ctx.lineTo(x * cellSize, displayH);
        ctx.stroke();
      }
      for (let y = 0; y <= rows; y++) {
        ctx.beginPath();
        ctx.moveTo(0, y * cellSize);
        ctx.lineTo(displayW, y * cellSize);
        ctx.stroke();
      }

      // 3. Draw Obstacles (Bricks)
      const obstacleList = board.obstacles || [];
      const bWidth = board.width || cols;
      for (let obsIdx of obstacleList) {
        const ox = (obsIdx % bWidth) * cellSize;
        const oy = Math.floor(obsIdx / bWidth) * cellSize;
        ctx.fillStyle = '#475569';
        ctx.beginPath();
        ctx.roundRect(ox + 2, oy + 2, cellSize - 4, cellSize - 4, 4);
        ctx.fill();

        // Brick highlight
        ctx.fillStyle = '#64748b';
        ctx.fillRect(ox + 4, oy + 4, cellSize - 8, 2);
      }

      // 4. Draw Gateways
      if (board.gateways && board.gateways.length >= 2) {
        for (let g of board.gateways) {
          const gx = g.x * cellSize;
          const gy = g.y * cellSize;
          const glow = ctx.createRadialGradient(
            gx + cellSize / 2, gy + cellSize / 2, 2,
            gx + cellSize / 2, gy + cellSize / 2, cellSize / 2
          );
          glow.addColorStop(0, '#00e5ff');
          glow.addColorStop(1, 'rgba(0, 229, 255, 0.1)');
          ctx.fillStyle = glow;
          ctx.beginPath();
          ctx.arc(gx + cellSize / 2, gy + cellSize / 2, cellSize / 2 - 2, 0, Math.PI * 2);
          ctx.fill();
        }
      }

      // 5. Draw Food / Novelty Pickups (Guaranteed valid in-bounds food)
      let food = board.food;
      if (!food || typeof food.x !== 'number' || typeof food.y !== 'number' ||
          food.x < 0 || food.x >= cols || food.y < 0 || food.y >= rows) {
        if (Generator && typeof Generator.ensureValidFood === 'function') {
          food = Generator.ensureValidFood(board);
          board.food = food;
        }
      }

      if (food) {
        const fx = food.x * cellSize;
        const fy = food.y * cellSize;
        const radius = Math.max(4, (cellSize / 2) - 3);
        const cx = fx + cellSize / 2;
        const cy = fy + cellSize / 2;

        if (food.type === 'GOLDEN_APPLE') {
          // 🌟 Golden Apple: Radiant gold glow + shimmer
          const grad = ctx.createRadialGradient(cx - 2, cy - 2, 2, cx, cy, radius);
          grad.addColorStop(0, '#fffbeb');
          grad.addColorStop(0.4, '#fde047');
          grad.addColorStop(1, '#eab308');
          ctx.fillStyle = grad;
          ctx.shadowColor = '#ffd700';
          ctx.shadowBlur = 14;
          ctx.beginPath();
          ctx.arc(cx, cy, radius + 1, 0, Math.PI * 2);
          ctx.fill();
          ctx.shadowBlur = 0;

          // Golden leaf & sparkle
          ctx.fillStyle = '#f59e0b';
          ctx.fillRect(cx - 1, cy - radius - 3, 2, 4);
          ctx.fillStyle = '#ffffff';
          ctx.beginPath();
          ctx.arc(cx - 2, cy - 2, 1.5, 0, Math.PI * 2);
          ctx.fill();
        } else if (food.type === 'CHILL_BERRY') {
          // ❄️ Chill Berry: Cyan frost glow + ice halo
          const grad = ctx.createRadialGradient(cx - 2, cy - 2, 2, cx, cy, radius);
          grad.addColorStop(0, '#e0f2fe');
          grad.addColorStop(0.5, '#38bdf8');
          grad.addColorStop(1, '#0284c7');
          ctx.fillStyle = grad;
          ctx.shadowColor = '#00e5ff';
          ctx.shadowBlur = 12;
          ctx.beginPath();
          ctx.arc(cx, cy, radius, 0, Math.PI * 2);
          ctx.fill();
          ctx.shadowBlur = 0;

          // Ice stem
          ctx.fillStyle = '#bae6fd';
          ctx.fillRect(cx - 1, cy - radius - 2, 2, 4);
        } else if (food.type === 'SPEED_BOOST') {
          // ⚡ Speed Boost: Vibrant amber lightning pickup
          const grad = ctx.createRadialGradient(cx - 2, cy - 2, 2, cx, cy, radius);
          grad.addColorStop(0, '#fef3c7');
          grad.addColorStop(0.5, '#f59e0b');
          grad.addColorStop(1, '#d97706');
          ctx.fillStyle = grad;
          ctx.shadowColor = '#f59e0b';
          ctx.shadowBlur = 12;
          ctx.beginPath();
          ctx.arc(cx, cy, radius, 0, Math.PI * 2);
          ctx.fill();
          ctx.shadowBlur = 0;

          // Lightning bolt symbol
          ctx.fillStyle = '#ffffff';
          ctx.font = `bold ${Math.max(8, Math.floor(cellSize * 0.55))}px sans-serif`;
          ctx.textAlign = 'center';
          ctx.textBaseline = 'middle';
          ctx.fillText('⚡', cx, cy + 1);
        } else if (food.type === 'MYSTERY') {
          // 🎁 Mystery Item: Glowing purple box with '?'
          ctx.fillStyle = '#9333ea';
          ctx.shadowColor = '#c084fc';
          ctx.shadowBlur = 12;
          ctx.beginPath();
          ctx.roundRect(fx + 2, fy + 2, cellSize - 4, cellSize - 4, 4);
          ctx.fill();
          ctx.shadowBlur = 0;

          // Inscribed '?'
          ctx.fillStyle = '#ffffff';
          ctx.font = `bold ${Math.max(9, Math.floor(cellSize * 0.65))}px sans-serif`;
          ctx.textAlign = 'center';
          ctx.textBaseline = 'middle';
          ctx.fillText('?', cx, cy + 1);
        } else if (food.type === 'MAGNET') {
          // 🧲 Magnet: Crimson magnetic attraction pickup
          const grad = ctx.createRadialGradient(cx - 2, cy - 2, 2, cx, cy, radius);
          grad.addColorStop(0, '#fce7f3');
          grad.addColorStop(0.5, '#ec4899');
          grad.addColorStop(1, '#be185d');
          ctx.fillStyle = grad;
          ctx.shadowColor = '#ec4899';
          ctx.shadowBlur = 12;
          ctx.beginPath();
          ctx.arc(cx, cy, radius, 0, Math.PI * 2);
          ctx.fill();
          ctx.shadowBlur = 0;

          // Magnet 'U' symbol
          ctx.fillStyle = '#ffffff';
          ctx.font = `bold ${Math.max(8, Math.floor(cellSize * 0.55))}px sans-serif`;
          ctx.textAlign = 'center';
          ctx.textBaseline = 'middle';
          ctx.fillText('🧲', cx, cy + 1);
        } else if (food.type === 'HOPPING_FRUIT') {
          // 🍇 Hopping Fruit: Berry purple
          ctx.fillStyle = '#a855f7';
          ctx.shadowColor = '#a855f7';
          ctx.shadowBlur = 8;
          ctx.beginPath();
          ctx.arc(cx, cy, radius, 0, Math.PI * 2);
          ctx.fill();
          ctx.shadowBlur = 0;

          ctx.fillStyle = '#22c55e';
          ctx.fillRect(cx - 1, cy - radius - 2, 2, 4);
        } else {
          // 🍎 Classic Normal Apple
          const grad = ctx.createRadialGradient(cx - 2, cy - 2, 2, cx, cy, radius);
          grad.addColorStop(0, '#fca5a5');
          grad.addColorStop(0.4, '#ef4444');
          grad.addColorStop(1, '#b91c1c');
          ctx.fillStyle = grad;
          ctx.shadowColor = '#ef4444';
          ctx.shadowBlur = 8;
          ctx.beginPath();
          ctx.arc(cx, cy, radius, 0, Math.PI * 2);
          ctx.fill();
          ctx.shadowBlur = 0;

          // Emerald leaf & stem
          ctx.fillStyle = '#15803d';
          ctx.fillRect(cx - 1, cy - radius - 2, 2, 4);
          ctx.fillStyle = '#4ade80';
          ctx.fillRect(cx + 1, cy - radius - 1, 3, 2);
        }
      }

      // 6. Draw Snake Body & Head
      const isDead = !!stateRef.current.deathState;
      const chillActive = board.chillTimer > 0;
      const boostActive = board.boostTimer > 0;
      const magnetActive = board.magnetTimer > 0;

      if (snake && snake.length > 0) {
        for (let i = snake.length - 1; i >= 0; i--) {
          const seg = snake[i];
          const sx = seg[0] * cellSize;
          const sy = seg[1] * cellSize;
          const isHead = (i === 0);

          if (isHead) {
            // Distinct Head Colors for Active Novelty Modifiers
            if (isDead) {
              ctx.fillStyle = '#ef4444';
            } else if (chillActive) {
              ctx.fillStyle = '#38bdf8'; // Frost cyan
              ctx.shadowColor = '#00e5ff';
              ctx.shadowBlur = 8;
            } else if (boostActive) {
              ctx.fillStyle = '#f59e0b'; // Speed amber
              ctx.shadowColor = '#fbbf24';
              ctx.shadowBlur = 10;
            } else if (magnetActive) {
              ctx.fillStyle = '#ec4899'; // Magnet magenta
              ctx.shadowColor = '#f472b6';
              ctx.shadowBlur = 8;
            } else {
              ctx.fillStyle = '#10b981'; // Classic emerald
            }

            ctx.beginPath();
            ctx.roundRect(sx + 2, sy + 2, cellSize - 4, cellSize - 4, 6);
            ctx.fill();
            ctx.shadowBlur = 0;

            // Optional Magnet Aura Ring around Head
            if (magnetActive && !isDead) {
              ctx.strokeStyle = 'rgba(236, 72, 153, 0.4)';
              ctx.lineWidth = 2;
              ctx.beginPath();
              ctx.arc(sx + cellSize / 2, sy + cellSize / 2, cellSize * 0.9, 0, Math.PI * 2);
              ctx.stroke();
            }

            // Eyes on Head
            ctx.fillStyle = '#ffffff';
            const dir = board.direction;
            let eye1 = { x: sx + 4, y: sy + 4 };
            let eye2 = { x: sx + cellSize - 8, y: sy + 4 };

            if (dir === 'DOWN') {
              eye1 = { x: sx + 4, y: sy + cellSize - 8 };
              eye2 = { x: sx + cellSize - 8, y: sy + cellSize - 8 };
            } else if (dir === 'LEFT') {
              eye1 = { x: sx + 4, y: sy + 4 };
              eye2 = { x: sx + 4, y: sy + cellSize - 8 };
            } else if (dir === 'RIGHT') {
              eye1 = { x: sx + cellSize - 8, y: sy + 4 };
              eye2 = { x: sx + cellSize - 8, y: sy + cellSize - 8 };
            }

            if (isDead) {
              ctx.fillStyle = '#ffffff';
              ctx.font = 'bold 9px sans-serif';
              ctx.fillText('✕', eye1.x - 1, eye1.y + 6);
              ctx.fillText('✕', eye2.x - 1, eye2.y + 6);
            } else {
              ctx.fillRect(eye1.x, eye1.y, 4, 4);
              ctx.fillRect(eye2.x, eye2.y, 4, 4);
              ctx.fillStyle = '#0f172a';
              ctx.fillRect(eye1.x + 1, eye1.y + 1, 2, 2);
              ctx.fillRect(eye2.x + 1, eye2.y + 1, 2, 2);
            }
          } else {
            // Body segment with smooth gradient fade
            const alpha = Math.max(0.4, 1 - (i / snake.length) * 0.6);
            if (isDead) {
              ctx.fillStyle = `rgba(239, 68, 68, ${alpha})`;
            } else if (chillActive) {
              ctx.fillStyle = `rgba(56, 189, 248, ${alpha})`;
            } else if (boostActive) {
              ctx.fillStyle = `rgba(245, 158, 11, ${alpha})`;
            } else if (magnetActive) {
              ctx.fillStyle = `rgba(236, 72, 153, ${alpha})`;
            } else {
              ctx.fillStyle = `rgba(16, 185, 129, ${alpha})`;
            }
            ctx.beginPath();
            ctx.roundRect(sx + 3, sy + 3, cellSize - 6, cellSize - 6, 4);
            ctx.fill();
          }
        }
      }

      // 7. Draw Death Burst Particles & Floating Novelty Banner Text
      if (particlesRef.current.length > 0) {
        const nextParticles = [];
        for (let p of particlesRef.current) {
          if (p.text) {
            ctx.save();
            ctx.globalAlpha = Math.max(0, p.alpha || 1);
            ctx.fillStyle = p.color || '#fde047';
            ctx.font = 'bold 12px sans-serif';
            ctx.textAlign = 'center';
            ctx.shadowColor = p.color || '#fde047';
            ctx.shadowBlur = 6;
            ctx.fillText(p.text, p.x, p.y);
            ctx.restore();
            p.y += (p.vy || -1);
            p.alpha = (p.alpha || 1) - 0.04;
            if (p.alpha > 0.05) nextParticles.push(p);
          } else {
            ctx.fillStyle = p.color;
            ctx.beginPath();
            ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2);
            ctx.fill();
            p.x += (p.vx || 0);
            p.y += (p.vy || 0);
            p.life = (p.life || 1) - 0.05;
            if (p.life > 0) nextParticles.push(p);
          }
        }
        particlesRef.current = nextParticles;
      }
    }, []);

    // Calculate dynamic responsive rectangular dimensions
    const computeRectangularGrid = useCallback((availW, availH) => {
      // Target cell size ~ 20-24px for ideal playable scale
      const targetCell = 22;
      let cols = Math.floor(availW / targetCell);
      let rows = Math.floor(availH / targetCell);

      // Bounded playable limits: expand horizontally on wide screens (up to 48 columns)
      cols = Math.max(16, Math.min(48, cols));
      rows = Math.max(10, Math.min(24, rows));

      const cellSize = Math.floor(Math.min(availW / cols, availH / rows));
      const canvasW = cols * cellSize;
      const canvasH = rows * cellSize;

      return { cols, rows, cellSize, canvasW, canvasH };
    }, []);

    // ResizeObserver on the container to adjust canvas and preserve active board
    useEffect(() => {
      const container = containerRef.current;
      if (!container || typeof ResizeObserver === 'undefined') return;

      const observer = new ResizeObserver((entries) => {
        if (!entries || entries.length === 0) return;
        const entry = entries[0];
        const rect = entry.contentRect;
        const width = Math.floor(rect.width);
        const height = Math.floor(rect.height);
        if (width <= 0 || height <= 0) return;

        // Subtract header (~32px) and control rail (~64px) + safe padding (~12px)
        const usableW = Math.max(260, width - 20);
        const usableH = Math.max(180, height - 108);

        const newGrid = computeRectangularGrid(usableW, usableH);
        layoutRef.current = newGrid;
        setStageDimensions({ width: newGrid.canvasW, height: newGrid.canvasH });

        // Adapt active board seamlessly if dimensions changed while playing
        if (boardRef.current && Generator) {
          const adapted = Generator.adaptBoardDimensions(boardRef.current, newGrid.cols, newGrid.rows);
          if (typeof Generator.ensureValidFood === 'function') {
            adapted.food = Generator.ensureValidFood(adapted);
          }
          boardRef.current = adapted;
          setUiBoardHeader({
            level: adapted.level,
            layoutType: adapted.layoutType,
            isMilestone: adapted.isMilestone,
            isOasis: adapted.isOasis,
            fruitsEaten: adapted.fruitsEatenInLevel,
            targetFruits: adapted.targetFruits,
            restartsCount: adapted.restartsCount,
            chillTimer: adapted.chillTimer
          });
        }

        drawCanvas();
      });

      observer.observe(container);
      return () => observer.disconnect();
    }, [computeRectangularGrid, drawCanvas]);

    // Handle Death / Collision Sequence
    const handleDeath = useCallback((failedBoard, reason) => {
      if (Audio) Audio.playCrash();
      setIsPaused(true);

      const head = failedBoard.snake[0];
      const cellSize = layoutRef.current.cellSize || 20;
      const headPx = head[0] * cellSize + cellSize / 2;
      const headPy = head[1] * cellSize + cellSize / 2;

      // Burst particles for death animation
      const particles = [];
      for (let i = 0; i < 24; i++) {
        const angle = (Math.PI * 2 * i) / 24 + (Math.random() * 0.2);
        const speed = 2 + Math.random() * 4;
        particles.push({
          x: headPx,
          y: headPy,
          vx: Math.cos(angle) * speed,
          vy: Math.sin(angle) * speed,
          life: 1.0,
          color: i % 2 === 0 ? '#ef4444' : '#fbbf24',
          size: 3 + Math.random() * 4
        });
      }
      particlesRef.current = particles;

      const nextRestarts = (failedBoard.restartsCount || 0) + 1;
      const remainingStars = Engine.calculateStars(nextRestarts);

      setDeathState({
        active: true,
        reason: reason === 'SELF_COLLISION' ? 'Hit own body!' : 'Crashed into barrier!',
        remainingStars,
        failedBoard
      });

      drawCanvas();
    }, [drawCanvas]);

    // Handle Level Completion & Auto Advance
    const handleLevelComplete = useCallback((completedBoard) => {
      if (Audio) Audio.playVictory();

      const starsEarned = Engine.calculateStars(completedBoard.restartsCount);
      setLevelCelebration({
        level: completedBoard.level,
        stars: starsEarned
      });

      const currentProg = stateRef.current.progression || Persistence.createDefaultState();
      const prevTotalStars = currentProg.totalStars || 0;
      const newTotalStars = prevTotalStars + starsEarned;
      const nextLevel = completedBoard.level + 1;

      const newMilestone = Math.floor(newTotalStars / 50);
      const oldMilestone = currentProg.highestStarMilestone || 0;
      const isNewMilestone = newMilestone > oldMilestone && newMilestone >= 1;

      const isFirstTry = (completedBoard.restartsCount === 0);
      const newStreak = isFirstTry ? ((currentProg.currentStreak || 0) + 1) : 0;
      const bestStreak = Math.max(currentProg.bestStreak || 0, newStreak);
      const nextCompleted = (currentProg.completedCount || 0) + 1;

      const grid = layoutRef.current;
      const nextBoard = Generator.generateLevel(nextLevel, { width: grid.cols, height: grid.rows });
      if (typeof Generator.ensureValidFood === 'function') {
        nextBoard.food = Generator.ensureValidFood(nextBoard);
      }

      const nextProgression = {
        ...currentProg,
        currentLevel: nextLevel,
        completedCount: nextCompleted,
        totalStars: newTotalStars,
        highestStarMilestone: Math.max(oldMilestone, newMilestone),
        totalFruitsEaten: (currentProg.totalFruitsEaten || 0) + (completedBoard.fruitsEatenInLevel || 0),
        longestSnake: Math.max(currentProg.longestSnake || 3, completedBoard.longestSnakeInLevel || 3),
        totalPlayTimeSeconds: Math.max(0, currentProg.totalPlayTimeSeconds || 0),
        bestStreak,
        currentStreak: newStreak,
        currentBoard: nextBoard
      };

      Persistence.saveProgressionImmediate(nextProgression);
      setProgression(nextProgression);
      if (stateRef.current) stateRef.current.progression = nextProgression;

      if (nextCompleted > 0 && nextCompleted % 50 === 0) {
        setLevelMilestoneCelebration(nextCompleted);
        try {
          window.dispatchEvent(new CustomEvent('billsoft:level-milestone-reached', { detail: { levelCount: nextCompleted } }));
        } catch (e) {}
      }

      // Check for 15-level Location Unlock Transition (e.g. Level 15 completed -> Location 2 unlocked)
      const WorldEngine = typeof window !== 'undefined' ? window.MaharashtraWorld : null;
      if (WorldEngine && typeof WorldEngine.checkAndTriggerLocationUnlock === 'function') {
        WorldEngine.checkAndTriggerLocationUnlock(completedBoard.level, 'classic');
      }

      try {
        window.dispatchEvent(new CustomEvent('billsoft:game-progression', {
          detail: { gameType: 'classic', level: nextLevel, totalStars: newTotalStars, completedCount: nextCompleted }
        }));
      } catch (e) {}

      setTimeout(() => {
        setLevelCelebration(null);
        boardRef.current = nextBoard;
        setUiBoardHeader({
          level: nextBoard.level,
          layoutType: nextBoard.layoutType,
          isMilestone: nextBoard.isMilestone,
          isOasis: nextBoard.isOasis,
          fruitsEaten: 0,
          targetFruits: nextBoard.targetFruits,
          restartsCount: 0,
          chillTimer: 0
        });
        nextDirectionQueueRef.current = [];
        setIsPaused(true);
        setShowLevelGoalCard(true);
        drawCanvas();

        if (isNewMilestone) {
          const trophy = getTrophyForMilestone(newMilestone);
          if (trophy) {
            if (Audio) Audio.playMilestone();
            try {
              window.dispatchEvent(new CustomEvent('billsoft:trophy-unlocked', {
                detail: { milestoneNumber: newMilestone, starThreshold: newMilestone * 50, trophy }
              }));
            } catch (e) {}
          }
        }
      }, 1000);
    }, [drawCanvas]);

    // High-frequency game tick (Executes imperatively with zero React state overhead on movement)
    const runGameTick = useCallback(() => {
      const currBoard = boardRef.current;
      const s = stateRef.current;
      if (!currBoard || s.isPaused || s.showLevelGoalCard || s.deathState || !Engine) return;

      // Process queued direction change
      let currentDir = currBoard.direction;
      if (nextDirectionQueueRef.current.length > 0) {
        const nextDir = nextDirectionQueueRef.current.shift();
        currentDir = Engine.changeDirection(currentDir, nextDir, currBoard.snake.length);
      }

      const boardToTick = {
        ...currBoard,
        direction: currentDir,
        currentTickMs: Math.max(115, Math.floor(currBoard.baseTickMs * (currBoard.speedModifier || 1.0)))
      };

      const result = Engine.tick(boardToTick);
      const { nextState, event, fruitEaten, deathReason, needsFoodSpawn } = result;

      if (event === 'DEATH') {
        boardRef.current = nextState;
        handleDeath(nextState, deathReason);
        return;
      }

      if (event === 'WIN') {
        boardRef.current = nextState;
        drawCanvas();
        handleLevelComplete(nextState);
        return;
      }

      if (event === 'GROW') {
        if (result.noveltyEffect) {
          if (result.noveltyEffect.type === 'GOLDEN_APPLE' && Audio) {
            Audio.playMilestone();
          } else if (result.noveltyEffect.type === 'CHILL_BERRY' && Audio) {
            Audio.playPortal();
          } else if (result.noveltyEffect.type === 'SPEED_BOOST' && Audio) {
            Audio.playPortal();
          } else if (result.noveltyEffect.type === 'MAGNET' && Audio) {
            Audio.playMilestone();
          } else if (Audio) {
            Audio.playEat(fruitEaten);
          }

          if (result.noveltyEffect.text) {
            const head = nextState.snake[0];
            const cellSize = layoutRef.current.cellSize || 20;
            particlesRef.current.push({
              text: result.noveltyEffect.text,
              x: head[0] * cellSize + cellSize / 2,
              y: Math.max(16, head[1] * cellSize - 8),
              vy: -1.2,
              alpha: 1.0,
              color: result.noveltyEffect.type === 'GOLDEN_APPLE' ? '#fde047' :
                     result.noveltyEffect.type === 'CHILL_BERRY' ? '#7dd3fc' :
                     result.noveltyEffect.type === 'SPEED_BOOST' ? '#fcd34d' :
                     result.noveltyEffect.type === 'MAGNET' ? '#f472b6' :
                     result.noveltyEffect.type === 'MYSTERY' ? '#d8b4fe' : '#fb923c'
            });
          }
        } else if (Audio) {
          Audio.playEat(fruitEaten);
        }

        setUiBoardHeader(prev => prev ? ({
          ...prev,
          fruitsEaten: nextState.fruitsEatenInLevel,
          chillTimer: nextState.chillTimer,
          boostTimer: nextState.boostTimer,
          magnetTimer: nextState.magnetTimer
        }) : prev);
      } else if (event === 'TELEPORT') {
        if (Audio) Audio.playPortal();
      }

      // Guarantee valid in-bounds food after every tick
      if (needsFoodSpawn || !nextState.food) {
        if (Generator && typeof Generator.ensureValidFood === 'function') {
          nextState.food = Generator.ensureValidFood(nextState);
        }
      }

      boardRef.current = nextState;
      drawCanvas();

      // Schedule next tick
      const tickInterval = Math.max(115, Math.floor(nextState.baseTickMs * (nextState.speedModifier || 1.0)));
      gameLoopTimerRef.current = setTimeout(runGameTick, tickInterval);
    }, [drawCanvas, handleDeath, handleLevelComplete]);

    // Game loop starter / scheduler
    useEffect(() => {
      if (isPaused || showLevelGoalCard || deathState || levelCelebration || milestoneCelebration) {
        if (gameLoopTimerRef.current) clearTimeout(gameLoopTimerRef.current);
        return;
      }

      const currBoard = boardRef.current;
      const tickInterval = currBoard ? Math.max(115, Math.floor(currBoard.baseTickMs * (currBoard.speedModifier || 1.0))) : 150;

      gameLoopTimerRef.current = setTimeout(runGameTick, tickInterval);

      return () => {
        if (gameLoopTimerRef.current) clearTimeout(gameLoopTimerRef.current);
      };
    }, [isPaused, showLevelGoalCard, deathState, levelCelebration, milestoneCelebration, runGameTick]);

    // Start / Resume Game Helper
    const startGame = useCallback((initialDirection = null) => {
      if (initialDirection && boardRef.current) {
        const b = boardRef.current;
        b.direction = Engine.changeDirection(b.direction, initialDirection, b.snake.length);
      }
      if (boardRef.current && Generator && typeof Generator.ensureValidFood === 'function') {
        boardRef.current.food = Generator.ensureValidFood(boardRef.current);
      }
      setShowLevelGoalCard(false);
      setDeathState(null);
      setIsPaused(false);
      drawCanvas();
    }, [drawCanvas]);

    // Retry / Restart from Death
    const handleRetry = useCallback((initialDirection = null) => {
      const currDeath = stateRef.current.deathState;
      if (!currDeath || !currDeath.failedBoard) return;

      const grid = layoutRef.current;
      const regenerated = Generator.generateLevel(currDeath.failedBoard.level, { width: grid.cols, height: grid.rows });
      regenerated.restartsCount = (currDeath.failedBoard.restartsCount || 0) + 1;
      if (initialDirection) {
        regenerated.direction = Engine.changeDirection(regenerated.direction, initialDirection, regenerated.snake.length);
      }
      if (typeof Generator.ensureValidFood === 'function') {
        regenerated.food = Generator.ensureValidFood(regenerated);
      }

      boardRef.current = regenerated;
      setUiBoardHeader({
        level: regenerated.level,
        layoutType: regenerated.layoutType,
        isMilestone: regenerated.isMilestone,
        isOasis: regenerated.isOasis,
        fruitsEaten: 0,
        targetFruits: regenerated.targetFruits,
        restartsCount: regenerated.restartsCount,
        chillTimer: 0
      });

      nextDirectionQueueRef.current = [];
      particlesRef.current = [];
      setDeathState(null);
      setShowLevelGoalCard(false);
      setIsPaused(false);
      drawCanvas();

      setProgression(prev => {
        if (!prev) return prev;
        const updated = {
          ...prev,
          currentStreak: 0,
          currentBoard: regenerated
        };
        Persistence.saveWorkingState(updated);
        return updated;
      });
    }, [drawCanvas]);

    // Manual Restart Current Level
    const handleRestartCurrentLevel = useCallback(() => {
      if (!boardRef.current) return;
      const curr = boardRef.current;
      const grid = layoutRef.current;
      const regenerated = Generator.generateLevel(curr.level, { width: grid.cols, height: grid.rows });
      regenerated.restartsCount = (curr.restartsCount || 0) + 1;
      if (typeof Generator.ensureValidFood === 'function') {
        regenerated.food = Generator.ensureValidFood(regenerated);
      }

      boardRef.current = regenerated;
      setUiBoardHeader({
        level: regenerated.level,
        layoutType: regenerated.layoutType,
        isMilestone: regenerated.isMilestone,
        isOasis: regenerated.isOasis,
        fruitsEaten: 0,
        targetFruits: regenerated.targetFruits,
        restartsCount: regenerated.restartsCount,
        chillTimer: 0
      });

      nextDirectionQueueRef.current = [];
      particlesRef.current = [];
      setDeathState(null);
      setShowLevelGoalCard(false);
      setIsPaused(false);
      drawCanvas();

      setProgression(prev => {
        if (!prev) return prev;
        const updated = {
          ...prev,
          currentStreak: 0,
          currentBoard: regenerated
        };
        Persistence.saveWorkingState(updated);
        return updated;
      });
    }, [drawCanvas]);

    // Keyboard Controls: PRESS ANY KEY TO START / RETRY / IMMEDIATE DIRECTION CHANGE
    useEffect(() => {
      const handleKeyDown = (e) => {
        const key = e.key;
        if (key === 'Shift' || key === 'Control' || key === 'Alt' || key === 'Meta') return;

        if (showStatsModal) {
          if (key === 'Escape') setShowStatsModal(false);
          return;
        }
        if (milestoneCelebration) {
          if (key === 'Escape' || key === 'Enter' || key === ' ') setMilestoneCelebration(null);
          return;
        }
        if (levelMilestoneCelebration) {
          if (key === 'Escape' || key === 'Enter' || key === ' ') setLevelMilestoneCelebration(null);
          return;
        }

        let reqDir = null;
        if (key === 'ArrowUp' || key === 'w' || key === 'W') reqDir = 'UP';
        else if (key === 'ArrowDown' || key === 's' || key === 'S') reqDir = 'DOWN';
        else if (key === 'ArrowLeft' || key === 'a' || key === 'A') reqDir = 'LEFT';
        else if (key === 'ArrowRight' || key === 'd' || key === 'D') reqDir = 'RIGHT';

        // 1. Press ANY key to start from level goal card
        if (stateRef.current.showLevelGoalCard) {
          e.preventDefault();
          startGame(reqDir);
          return;
        }

        // 2. Press ANY key to restart from death overlay
        if (stateRef.current.deathState) {
          e.preventDefault();
          handleRetry(reqDir);
          return;
        }

        // 3. Spacebar toggles pause/resume
        if (key === ' ' || key === 'Spacebar') {
          e.preventDefault();
          setIsPaused(prev => !prev);
          return;
        }

        // 4. Direction navigation during active play
        if (reqDir) {
          e.preventDefault();
          if (stateRef.current.isPaused) {
            setIsPaused(false);
          }
          if (nextDirectionQueueRef.current.length < 2) {
            nextDirectionQueueRef.current.push(reqDir);
          }
        }
      };

      window.addEventListener('keydown', handleKeyDown);
      return () => window.removeEventListener('keydown', handleKeyDown);
    }, [showStatsModal, milestoneCelebration, levelMilestoneCelebration, startGame, handleRetry]);

    // Developer / Test Harness Integration: Force spawn any collectible type into active board
    useEffect(() => {
      window.__billsoft_snake_force_spawn = (type) => {
        if (!boardRef.current) return false;
        const b = boardRef.current;
        const width = b.width;
        const height = b.height;
        const occupied = new Set();
        for (let s of (b.snake || [])) occupied.add(s[1] * width + s[0]);
        for (let o of (b.obstacles || [])) occupied.add(o);

        const available = [];
        for (let y = 0; y < height; y++) {
          for (let x = 0; x < width; x++) {
            const idx = y * width + x;
            if (!occupied.has(idx)) available.push({ x, y });
          }
        }
        if (available.length === 0) return false;
        const pick = available[Math.floor(Math.random() * available.length)];
        const validTypes = ['APPLE', 'GOLDEN_APPLE', 'CHILL_BERRY', 'SPEED_BOOST', 'MYSTERY', 'MAGNET', 'HOPPING_FRUIT'];
        const chosenType = validTypes.includes(type) ? type : 'APPLE';
        const lifetimeMs = chosenType === 'APPLE' ? null : 15000;

        b.food = {
          x: pick.x,
          y: pick.y,
          type: chosenType,
          spawnTime: Date.now(),
          lifetimeMs
        };
        drawCanvas();
        return true;
      };

      return () => {
        delete window.__billsoft_snake_force_spawn;
      };
    }, [drawCanvas]);

    // Initialize Game & Persistence
    useEffect(() => {
      let isMounted = true;

      async function init() {
        if (!Persistence || !Generator) return;
        const loaded = await Persistence.loadAndRecoverState();
        if (!isMounted) return;

        const grid = layoutRef.current;
        let activeBoard = loaded.currentBoard;
        if (!activeBoard || !Persistence.validateBoard(activeBoard, loaded.currentLevel)) {
          activeBoard = Generator.generateLevel(loaded.currentLevel, { width: grid.cols, height: grid.rows });
          loaded.currentBoard = activeBoard;
          Persistence.saveWorkingState(loaded);
        } else {
          // Adapt loaded board to current responsive aspect ratio
          activeBoard = Generator.adaptBoardDimensions(activeBoard, grid.cols, grid.rows);
        }

        if (typeof Generator.ensureValidFood === 'function') {
          activeBoard.food = Generator.ensureValidFood(activeBoard);
        }

        boardRef.current = activeBoard;
        setProgression(loaded);
        setUiBoardHeader({
          level: activeBoard.level,
          layoutType: activeBoard.layoutType,
          isMilestone: activeBoard.isMilestone,
          isOasis: activeBoard.isOasis,
          fruitsEaten: activeBoard.fruitsEatenInLevel || 0,
          targetFruits: activeBoard.targetFruits || 5,
          restartsCount: activeBoard.restartsCount || 0,
          chillTimer: activeBoard.chillTimer || 0
        });
        setIsPaused(true);
        setShowLevelGoalCard(true);
        drawCanvas();

        try {
          window.dispatchEvent(new CustomEvent('billsoft:game-progression', {
            detail: { gameType: 'classic', level: loaded.currentLevel || 1, totalStars: loaded.totalStars || 0, completedCount: loaded.completedCount || 0 }
          }));
        } catch (e) {}
      }

      init();

      return () => {
        isMounted = false;
        if (Persistence && stateRef.current.progression) {
          Persistence.flushPending();
        }
      };
    }, [drawCanvas]);

    // Active Play Time tracking (Runs steadily every 1s when active)
    useEffect(() => {
      activeTimerRef.current = setInterval(() => {
        const s = stateRef.current;
        if (s && !s.isPaused && !s.showLevelGoalCard && !s.deathState && boardRef.current && document.visibilityState === 'visible') {
          setProgression(prev => {
            if (!prev) return prev;
            const updated = {
              ...prev,
              totalPlayTimeSeconds: (prev.totalPlayTimeSeconds || 0) + 1
            };
            if (stateRef.current) stateRef.current.progression = updated;
            return updated;
          });
        }
      }, 1000);

      const persistInterval = setInterval(() => {
        const s = stateRef.current;
        if (s && s.progression && Persistence) {
          const currentState = {
            ...s.progression,
            currentBoard: boardRef.current ? { ...boardRef.current } : s.progression.currentBoard
          };
          Persistence.saveWorkingState(currentState);
        }
      }, 5000);

      return () => {
        if (activeTimerRef.current) clearInterval(activeTimerRef.current);
        if (persistInterval) clearInterval(persistInterval);
        if (Persistence && stateRef.current && stateRef.current.progression) {
          const currentState = {
            ...stateRef.current.progression,
            currentBoard: boardRef.current ? { ...boardRef.current } : stateRef.current.progression.currentBoard
          };
          Persistence.saveWorkingState(currentState);
          Persistence.flushPending();
        }
      };
    }, []);

    // Automatic pause on tab blur / visibility change / unload
    useEffect(() => {
      const handleFlushAndPause = () => {
        setIsPaused(true);
        if (!stateRef.current.deathState) {
          setShowLevelGoalCard(true);
        }
        if (Persistence && stateRef.current.progression) {
          const currentState = {
            ...stateRef.current.progression,
            currentBoard: boardRef.current ? { ...boardRef.current } : stateRef.current.progression.currentBoard
          };
          Persistence.saveWorkingState(currentState);
          Persistence.flushPending();
        }
      };

      const handleVisibilityChange = () => {
        if (document.hidden) {
          handleFlushAndPause();
        }
      };

      document.addEventListener('visibilitychange', handleVisibilityChange);
      window.addEventListener('blur', handleFlushAndPause);
      window.addEventListener('beforeunload', handleFlushAndPause);
      window.addEventListener('pagehide', handleFlushAndPause);

      return () => {
        document.removeEventListener('visibilitychange', handleVisibilityChange);
        window.removeEventListener('blur', handleFlushAndPause);
        window.removeEventListener('beforeunload', handleFlushAndPause);
        window.removeEventListener('pagehide', handleFlushAndPause);
        handleFlushAndPause();
      };
    }, []);

    // 50-Level milestone celebration auto-dismiss timer
    useEffect(() => {
      if (levelMilestoneCelebration) {
        if (levelMilestoneTimerRef.current) clearTimeout(levelMilestoneTimerRef.current);
        levelMilestoneTimerRef.current = setTimeout(() => {
          setLevelMilestoneCelebration(null);
        }, 1800);
        return () => {
          if (levelMilestoneTimerRef.current) clearTimeout(levelMilestoneTimerRef.current);
        };
      }
    }, [levelMilestoneCelebration]);

    const stars = Math.max(0, Math.floor(progression ? progression.totalStars : 0));
    const currentMilestoneCount = Math.floor(stars / 50);
    const progressInMilestone = stars % 50;
    const currentTrophy = getTrophyForMilestone(currentMilestoneCount);
    const WorldEngine = typeof window !== 'undefined' ? window.MaharashtraWorld : null;
    const worldData = WorldEngine && uiBoardHeader ? WorldEngine.getWorldForLevel('classic', uiBoardHeader.level) : null;

    return React.createElement('div', {
      ref: containerRef,
      className: 'snake-classic-container',
      style: {
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'flex-start',
        padding: '0 8px 4px',
        maxWidth: 960,
        width: '100%',
        margin: '0 auto',
        userSelect: 'none',
        flex: 1,
        minHeight: 0,
        boxSizing: 'border-box',
        overflow: 'hidden'
      }
    },
      // Centered Compact Game Header
      React.createElement('div', {
        className: 'snake-game-header',
        style: {
          textAlign: 'center',
          marginBottom: 4,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center'
        }
      },
        React.createElement('div', {
          style: {
            fontSize: '1.02rem',
            fontWeight: 800,
            color: uiBoardHeader && uiBoardHeader.isMilestone ? '#fbbf24' : 'var(--text)',
            letterSpacing: '0.04em',
            display: 'flex',
            alignItems: 'center',
            gap: 6
          }
        },
          `LEVEL ${uiBoardHeader ? uiBoardHeader.level : 1} · SNAKE RECTANGULAR`,
          uiBoardHeader && uiBoardHeader.isOasis && React.createElement('span', {
            style: { fontSize: '0.7rem', background: '#0284c7', padding: '1px 6px', borderRadius: 6, color: '#fff', fontWeight: 700 }
          }, '🌴 Oasis'),
          uiBoardHeader && uiBoardHeader.isMilestone && React.createElement('span', {
            style: { fontSize: '0.7rem', background: '#8b5cf6', padding: '1px 6px', borderRadius: 6, color: '#fff', fontWeight: 700 }
          }, '⭐ Milestone')
        ),
        React.createElement('div', {
          style: {
            fontSize: '0.74rem',
            fontWeight: 600,
            color: 'var(--text-secondary, #64748b)',
            marginTop: 1
          }
        },
          `⭐ ${stars} · ${currentTrophy ? `${currentTrophy.icon} ${currentTrophy.tier}` : '🥉 Bronze'} · ${progressInMilestone}/50 → next trophy`
        )
      ),

      // 2. Responsive Rectangular Canvas Game Stage with Overlay Wrappers
      React.createElement('div', {
        className: 'snake-board-wrapper',
        style: {
          position: 'relative',
          borderRadius: 12,
          overflow: 'hidden',
          boxShadow: 'var(--shadow-md, 0 6px 24px rgba(0,0,0,0.22))',
          border: deathState ? '2px solid #ef4444' : (uiBoardHeader && uiBoardHeader.chillTimer > 0 ? '2px solid #00e5ff' : '1.5px solid var(--border, rgba(255,255,255,0.12))'),
          transition: 'border 0.2s ease',
          background: '#0f172a',
          margin: '0 auto',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          width: stageDimensions.width,
          height: stageDimensions.height,
          flexShrink: 0
        }
      },
        React.createElement('canvas', {
          ref: canvasRef,
          width: stageDimensions.width,
          height: stageDimensions.height,
          style: { display: 'block', cursor: 'pointer' },
          onClick: () => {
            if (showLevelGoalCard) {
              startGame();
            } else if (deathState) {
              handleRetry();
            } else if (isPaused) {
              setIsPaused(false);
            }
          }
        }),

        // Explicit Level Goals Card with Dynamic Gameplay Tips
        showLevelGoalCard && !levelCelebration && !showStatsModal && (() => {
          const currentLevelNum = uiBoardHeader ? uiBoardHeader.level : 1;
          let selectedTip = null;
          if (uiBoardHeader && uiBoardHeader.isOasis) {
            selectedTip = SNAKE_LEVEL_TIPS.find(t => t.badge === 'Breather Oasis');
          } else if (boardRef.current && boardRef.current.gateways && boardRef.current.gateways.length > 0) {
            selectedTip = SNAKE_LEVEL_TIPS.find(t => t.badge === 'Quantum Gateways');
          }
          if (!selectedTip) {
            selectedTip = SNAKE_LEVEL_TIPS[(currentLevelNum - 1) % SNAKE_LEVEL_TIPS.length];
          }

          return React.createElement('div', {
            style: {
              position: 'absolute',
              inset: 0,
              background: 'rgba(15, 23, 42, 0.90)',
              backdropFilter: 'blur(6px)',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              padding: '16px 20px',
              textAlign: 'center',
              zIndex: 10
            },
            onClick: () => startGame()
          },
            React.createElement('div', { style: { fontSize: '2.2rem', marginBottom: 2 } }, '🐍'),
            React.createElement('div', { style: { fontSize: '1.25rem', fontWeight: 800, color: '#ffffff', letterSpacing: 0.5 } },
              `Level ${uiBoardHeader ? uiBoardHeader.level : 1} — ${uiBoardHeader ? uiBoardHeader.layoutType.replace('_', ' ') : 'Challenge'}`
            ),

            // Goal Badge
            React.createElement('div', {
              style: {
                background: 'rgba(239, 68, 68, 0.2)',
                border: '1.5px solid rgba(239, 68, 68, 0.6)',
                borderRadius: 10,
                padding: '6px 16px',
                marginTop: 8,
                marginBottom: 8,
                maxWidth: 360
              }
            },
              React.createElement('div', { style: { fontSize: '1.05rem', fontWeight: 800, color: '#fca5a5' } },
                `🎯 GOAL: Eat ${uiBoardHeader ? uiBoardHeader.targetFruits : 5} Apples`
              ),
              React.createElement('div', { style: { fontSize: '0.76rem', color: '#fecaca', marginTop: 2 } },
                uiBoardHeader && uiBoardHeader.isOasis ? '🌴 Relaxing breather: 0 obstacles on this level!' : 'Arrow Keys or WASD to control'
              )
            ),

            // 💡 Dynamic Single-Tip Box for Game Novelties / Mechanics
            selectedTip && React.createElement('div', {
              style: {
                background: selectedTip.badgeBg,
                border: `1.5px solid ${selectedTip.badgeBorder}66`,
                borderRadius: 10,
                padding: '8px 14px',
                marginTop: 2,
                marginBottom: 10,
                maxWidth: 380,
                textAlign: 'left',
                display: 'flex',
                alignItems: 'center',
                gap: 10,
                boxShadow: '0 4px 12px rgba(0,0,0,0.25)'
              }
            },
              React.createElement('div', { style: { fontSize: '1.5rem', lineHeight: 1, flexShrink: 0 } }, selectedTip.icon),
              React.createElement('div', { style: { flex: 1 } },
                React.createElement('div', {
                  style: {
                    fontSize: '0.72rem',
                    fontWeight: 800,
                    color: selectedTip.badgeColor,
                    textTransform: 'uppercase',
                    letterSpacing: 0.6,
                    marginBottom: 2
                  }
                }, `💡 TIP: ${selectedTip.badge}`),
                React.createElement('div', {
                  style: {
                    fontSize: '0.78rem',
                    color: '#e2e8f0',
                    lineHeight: 1.35
                  }
                }, selectedTip.text)
              )
            ),

            React.createElement('button', {
              type: 'button',
              className: 'btn btn-primary',
              onClick: (e) => {
                e.stopPropagation();
                startGame();
              },
              style: {
                padding: '8px 26px',
                fontSize: '0.92rem',
                fontWeight: 800,
                borderRadius: 8,
                background: '#10b981',
                borderColor: '#10b981',
                color: '#ffffff',
                boxShadow: '0 4px 16px rgba(16, 185, 129, 0.4)'
              }
            }, '▶ START LEVEL'),

            React.createElement('div', { style: { fontSize: '0.74rem', color: '#94a3b8', marginTop: 6 } },
              '⌨️ Press ANY key to start'
            )
          );
        })(),

        // Satisfying Death / Crash Overlay
        deathState && !levelCelebration && !milestoneCelebration && React.createElement('div', {
          style: {
            position: 'absolute',
            inset: 0,
            background: 'rgba(15, 23, 42, 0.9)',
            backdropFilter: 'blur(4px)',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '16px 20px',
            textAlign: 'center',
            zIndex: 15
          },
          onClick: () => handleRetry()
        },
          React.createElement('div', { style: { fontSize: '2.4rem', marginBottom: 4 } }, '💥'),
          React.createElement('div', { style: { fontSize: '1.45rem', fontWeight: 800, color: '#ef4444', letterSpacing: 0.5 } },
            'CRASHED!'
          ),
          React.createElement('div', { style: { fontSize: '0.88rem', fontWeight: 600, color: '#fca5a5', marginTop: 2 } },
            deathState.reason
          ),

          React.createElement('div', {
            style: {
              background: 'rgba(255,255,255,0.06)',
              borderRadius: 8,
              padding: '8px 14px',
              margin: '10px 0',
              fontSize: '0.82rem',
              color: '#cbd5e1'
            }
          },
            React.createElement('div', null, `Level ${uiBoardHeader ? uiBoardHeader.level : 1} Attempt Failed`),
            React.createElement('div', { style: { marginTop: 3, color: '#fbbf24', fontWeight: 700 } },
              `Rating on retry: ${'⭐'.repeat(deathState.remainingStars)}${'☆'.repeat(3 - deathState.remainingStars)}`
            )
          ),

          React.createElement('button', {
            type: 'button',
            className: 'btn btn-primary',
            onClick: (e) => {
              e.stopPropagation();
              handleRetry();
            },
            style: {
              padding: '8px 24px',
              fontSize: '0.92rem',
              fontWeight: 800,
              borderRadius: 8,
              background: '#ef4444',
              borderColor: '#ef4444',
              color: '#ffffff',
              boxShadow: '0 4px 16px rgba(239, 68, 68, 0.4)'
            }
          }, '🔄 TRY AGAIN'),

          React.createElement('div', { style: { fontSize: '0.76rem', color: '#94a3b8', marginTop: 6 } },
            'Press ANY key to restart'
          )
        ),

        // Level Complete Celebration Overlay
        levelCelebration && React.createElement('div', {
          style: {
            position: 'absolute',
            inset: 0,
            background: 'rgba(16, 185, 129, 0.94)',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 10,
            zIndex: 20
          }
        },
          React.createElement('div', { style: { fontSize: '1.8rem', fontWeight: 900, color: '#fff' } },
            'LEVEL COMPLETE!'
          ),
          React.createElement('div', { style: { fontSize: '1.6rem' } },
            '⭐'.repeat(levelCelebration.stars)
          ),
          React.createElement('div', { style: { fontSize: '0.9rem', color: '#f0fdf4', fontWeight: 600 } },
            'Generating next challenge...'
          )
        )
      ),

      // 3. Compact Control & Status Rail below Canvas
      React.createElement('div', {
        className: 'snake-action-rail',
        style: {
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: 5,
          marginTop: 6,
          width: '100%',
          maxWidth: 480,
          boxSizing: 'border-box'
        }
      },
        // Row 1: Action Buttons
        React.createElement('div', {
          style: {
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 8,
            width: '100%'
          }
        },
          // Play / Pause Button
          React.createElement('button', {
            type: 'button',
            className: 'btn btn-outline btn-sm',
            onClick: () => {
              if (showLevelGoalCard) {
                startGame();
              } else if (deathState) {
                handleRetry();
              } else {
                setIsPaused(prev => !prev);
              }
            },
            title: isPaused ? 'Resume Game (Space)' : 'Pause Game (Space)',
            style: {
              fontSize: '0.8rem',
              fontWeight: 700,
              padding: '4px 14px',
              borderRadius: 'var(--radius, 8px)',
              display: 'flex',
              alignItems: 'center',
              gap: 4
            }
          }, isPaused ? '▶ Play' : '⏸ Pause'),

          // Restart Button
          React.createElement('button', {
            type: 'button',
            className: 'btn btn-outline btn-sm',
            onClick: () => {
              if (deathState) {
                handleRetry();
              } else {
                handleRestartCurrentLevel();
              }
            },
            title: 'Restart Level',
            style: {
              fontSize: '0.8rem',
              fontWeight: 600,
              padding: '4px 14px',
              borderRadius: 'var(--radius, 8px)',
              display: 'flex',
              alignItems: 'center',
              gap: 4
            }
          }, '↺ Restart'),

          // Audio Mute/Unmute Button
          React.createElement('button', {
            type: 'button',
            className: 'btn btn-outline btn-sm',
            onClick: () => {
              const nextMuted = !isMuted;
              setIsMuted(nextMuted);
              if (Audio) Audio.setMuted(nextMuted);
            },
            title: isMuted ? 'Unmute Audio' : 'Mute Audio',
            style: {
              width: 32,
              padding: 0,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: '0.85rem',
              borderRadius: 'var(--radius, 8px)'
            }
          }, isMuted ? '🔇' : '🔊'),

          // Stats Button
          React.createElement('button', {
            type: 'button',
            className: 'btn btn-outline btn-sm',
            onClick: () => {
              setIsPaused(true);
              setShowStatsModal(true);
            },
            title: 'View Lifetime Statistics & Milestone Trophies',
            style: {
              fontSize: '0.8rem',
              fontWeight: 600,
              padding: '4px 14px',
              borderRadius: 'var(--radius, 8px)',
              display: 'flex',
              alignItems: 'center',
              gap: 4
            }
          }, '📊 Stats')
        ),

        // Row 2: Unified Supporting Status Bar
        React.createElement('div', {
          style: {
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 8,
            fontSize: '0.72rem',
            fontWeight: 600,
            color: 'var(--text-secondary, #64748b)',
            background: 'var(--surface-sunken, #f8fafc)',
            border: '1px solid var(--border, #e2e8f0)',
            borderRadius: 'var(--radius, 8px)',
            padding: '3px 12px',
            width: '100%',
            boxSizing: 'border-box',
            flexWrap: 'wrap'
          }
        },
          React.createElement('span', { style: { color: 'var(--text, #1e293b)', fontWeight: 700 } },
            `⭐ ${stars} Stars`
          ),
          React.createElement('span', { style: { color: 'var(--border, #cbd5e1)' } }, '•'),
          React.createElement('span', { style: { color: currentTrophy ? currentTrophy.color : '#d97706', fontWeight: 700 } },
            `${currentTrophy ? `${currentTrophy.icon} ${currentTrophy.tier}` : '🥉 Bronze'} (${progressInMilestone}/50)`
          ),
          worldData && worldData.location && React.createElement(React.Fragment, null,
            React.createElement('span', { style: { color: 'var(--border, #cbd5e1)' } }, '•'),
            React.createElement('span', { style: { color: 'var(--text-secondary, #64748b)' } },
              `📍 ${worldData.location.name} (${worldData.progressInLocation}/${worldData.levelsPerLocation})`
            )
          ),
          React.createElement('span', { style: { color: 'var(--border, #cbd5e1)' } }, '•'),
          React.createElement('span', { style: { color: '#ef4444', fontWeight: 700 } },
            `🍎 ${uiBoardHeader ? uiBoardHeader.fruitsEaten : 0} / ${uiBoardHeader ? uiBoardHeader.targetFruits : 5}`
          ),
          React.createElement('span', { style: { color: 'var(--border, #cbd5e1)' } }, '•'),
          React.createElement('span', null, `Streak: 🔥 ${progression ? progression.currentStreak : 0}`)
        )
      ),

      // Stats Modal
      showStatsModal && renderModalPortal(React.createElement('div', {
        className: 'modal-overlay take-a-break-stats-overlay',
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
          padding: 16
        },
        onClick: () => setShowStatsModal(false)
      },
        React.createElement('div', {
          className: 'modal-card take-a-break-stats-card',
          style: {
            width: 480,
            maxWidth: '92vw',
            maxHeight: '90vh',
            overflowY: 'auto',
            padding: '24px 28px',
            borderRadius: 16,
            background: 'var(--bg-card, #ffffff)',
            color: 'var(--text, #1e293b)',
            border: '1px solid var(--border, #e2e8f0)',
            boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.5), 0 0 0 1px rgba(255,255,255,0.05)',
            display: 'flex',
            flexDirection: 'column',
            gap: 16,
            boxSizing: 'border-box'
          },
          onClick: (e) => e.stopPropagation()
        },
          React.createElement('div', { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center' } },
            React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 8 } },
              React.createElement('span', { style: { fontSize: '1.4rem' } }, '🐍'),
              React.createElement('h3', { style: { margin: 0, fontSize: '1.2rem', fontWeight: 800, color: 'var(--text)' } }, 'Snake Statistics')
            ),
            React.createElement('button', {
              type: 'button',
              className: 'btn btn-ghost btn-sm',
              style: { padding: '4px 8px', fontSize: '1.1rem', cursor: 'pointer', borderRadius: 6 },
              onClick: () => setShowStatsModal(false)
            }, '✕')
          ),

          React.createElement('div', { style: { display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 10 } },
            React.createElement('div', { style: { background: 'var(--surface-sunken, #f8fafc)', padding: '10px 14px', borderRadius: 10, border: '1px solid var(--border)' } },
              React.createElement('div', { style: { fontSize: '0.75rem', color: 'var(--text-secondary)', fontWeight: 600 } }, 'Current Level'),
              React.createElement('div', { style: { fontSize: '1.25rem', fontWeight: 800, marginTop: 2, color: 'var(--text)' } }, progression ? progression.currentLevel : 1)
            ),
            React.createElement('div', { style: { background: 'var(--surface-sunken, #f8fafc)', padding: '10px 14px', borderRadius: 10, border: '1px solid var(--border)' } },
              React.createElement('div', { style: { fontSize: '0.75rem', color: 'var(--text-secondary)', fontWeight: 600 } }, 'Levels Completed'),
              React.createElement('div', { style: { fontSize: '1.25rem', fontWeight: 800, marginTop: 2, color: 'var(--text)' } }, progression ? progression.completedCount : 0)
            ),
            React.createElement('div', { style: { background: 'var(--surface-sunken, #f8fafc)', padding: '10px 14px', borderRadius: 10, border: '1px solid var(--border)' } },
              React.createElement('div', { style: { fontSize: '0.75rem', color: 'var(--text-secondary)', fontWeight: 600 } }, 'Total Lifetime Stars'),
              React.createElement('div', { style: { fontSize: '1.25rem', fontWeight: 800, marginTop: 2, color: '#eab308' } }, `⭐ ${progression ? progression.totalStars : 0}`)
            ),
            React.createElement('div', { style: { background: 'var(--surface-sunken, #f8fafc)', padding: '10px 14px', borderRadius: 10, border: '1px solid var(--border)' } },
              React.createElement('div', { style: { fontSize: '0.75rem', color: 'var(--text-secondary)', fontWeight: 600 } }, 'Longest Snake'),
              React.createElement('div', { style: { fontSize: '1.25rem', fontWeight: 800, marginTop: 2, color: 'var(--text)' } }, `${progression ? progression.longestSnake : 3} units`)
            ),
            React.createElement('div', { style: { background: 'var(--surface-sunken, #f8fafc)', padding: '10px 14px', borderRadius: 10, border: '1px solid var(--border)' } },
              React.createElement('div', { style: { fontSize: '0.75rem', color: 'var(--text-secondary)', fontWeight: 600 } }, 'Total Fruits Eaten'),
              React.createElement('div', { style: { fontSize: '1.25rem', fontWeight: 800, marginTop: 2, color: '#ef4444' } }, `🍎 ${progression ? progression.totalFruitsEaten : 0}`)
            ),
            React.createElement('div', { style: { background: 'var(--surface-sunken, #f8fafc)', padding: '10px 14px', borderRadius: 10, border: '1px solid var(--border)' } },
              React.createElement('div', { style: { fontSize: '0.75rem', color: 'var(--text-secondary)', fontWeight: 600 } }, 'Best 1st-Try Streak'),
              React.createElement('div', { style: { fontSize: '1.25rem', fontWeight: 800, marginTop: 2, color: '#f97316' } }, `🔥 ${progression ? progression.bestStreak : 0}`)
            ),
            React.createElement('div', { style: { gridColumn: 'span 2', background: 'var(--surface-sunken, #f8fafc)', padding: '10px 14px', borderRadius: 10, border: '1px solid var(--border)' } },
              React.createElement('div', { style: { fontSize: '0.75rem', color: 'var(--text-secondary)', fontWeight: 600 } }, 'Lifetime Play Time'),
              React.createElement('div', { style: { fontSize: '1.25rem', fontWeight: 800, marginTop: 2, color: 'var(--text)' } }, formatLifetimeTime(progression ? progression.totalPlayTimeSeconds : 0))
            )
          ),

          React.createElement(window.TrophyProgressBar || TrophyProgressBar, {
            totalStars: progression ? progression.totalStars : 0,
            currentLevel: progression ? progression.currentLevel : 1,
            gameType: 'classic'
          }),

          React.createElement('div', { style: { marginTop: 4, textAlign: 'right' } },
            React.createElement('button', {
              type: 'button',
              className: 'btn btn-primary',
              onClick: () => setShowStatsModal(false),
              style: { padding: '8px 24px', fontWeight: 700, borderRadius: 8 }
            }, 'Close')
          )
        )
      )),

      // 50-Level Celebration Modal
      levelMilestoneCelebration && renderModalPortal(React.createElement('div', {
        className: 'level-milestone-modal-overlay',
        style: {
          position: 'fixed',
          inset: 0,
          background: 'rgba(0,0,0,0.75)',
          backdropFilter: 'blur(8px)',
          WebkitBackdropFilter: 'blur(8px)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 99999,
          animation: 'milestoneFadeIn 0.3s cubic-bezier(0.16, 1, 0.3, 1)'
        },
        onClick: () => setLevelMilestoneCelebration(null)
      },
        React.createElement('div', {
          className: 'level-milestone-modal-card',
          style: {
            background: 'linear-gradient(135deg, rgba(30, 41, 59, 0.96), rgba(15, 23, 42, 0.98))',
            border: '2px solid rgba(255, 215, 0, 0.7)',
            borderRadius: 18,
            padding: '28px 32px',
            maxWidth: 440,
            width: '90%',
            textAlign: 'center',
            boxShadow: '0 0 50px rgba(255, 215, 0, 0.4)',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: 12,
            animation: 'milestonePop 0.4s cubic-bezier(0.16, 1, 0.3, 1)'
          },
          onClick: e => e.stopPropagation()
        },
          React.createElement('div', { style: { fontSize: '3.2rem', filter: 'drop-shadow(0 0 16px gold)' } }, '👑'),
          React.createElement('h2', { style: { margin: 0, fontSize: '1.45rem', fontWeight: 900, color: '#ffd700', letterSpacing: '0.04em' } },
            `✨ ${levelMilestoneCelebration} LEVELS COMPLETE! ✨`
          ),
          React.createElement('p', { style: { margin: 0, fontSize: '0.92rem', color: 'var(--text-secondary, #94a3b8)' } },
            'Incredible agility! Maharashtra world level milestone reached.'
          ),
          React.createElement('button', {
            type: 'button',
            className: 'btn btn-primary',
            onClick: () => setLevelMilestoneCelebration(null),
            style: { marginTop: 8, padding: '8px 24px', fontWeight: 700, borderRadius: 8 }
          }, 'Continue Playing ➔')
        )
      ))
    );
  }

  return SnakeClassic;
}));
