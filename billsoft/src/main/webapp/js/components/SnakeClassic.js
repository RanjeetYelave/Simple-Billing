/**
 * SnakeClassic.js
 * Billsoft Full-Viewport Dynamic Territory Snake Component.
 * Features:
 * - Dynamic full-viewport responsive canvas driven by ResizeObserver.
 * - Progressive playable territory: centered beginner play area expanding to full viewport at level 40+.
 * - Illustrated Multi-Layer Canvas World Renderer (Prince of Persia philosophy):
 *     Layer 0: Atmospheric Sky & Horizon Silhouette (Sahyadri peaks, sea forts, caves, temples, wadas)
 *     Layer 1: Tactile Ground Material (basalt flagstone, coastal sand, cave stone, terracotta, moss, coronation dais)
 *     Layer 2: Architectural Board Framing (fort battlements, laterite sea walls, chaitya arches, teak mouldings)
 *     Layer 3: 3D Architectural Obstacles (bastions, arched gates, monolithic pillars, timber docks, banyans)
 *     Layer 4: Destination-Themed Snake Avatar (Basalt Cobra, Konkan Azure Serpent, Cave Naga, Teak Python, Emerald Viper)
 *     Layer 5: Regional Cultural Collectibles (Sahyadri Gem, Konkan Pearl, Cave Relic, Wada Hon, Forest Orchid, Diya)
 *     Layer 6: Ambient Environmental Particles (mist motes, sea spray, stone dust, gold dust, flower petals)
 * - Offscreen static layer caching for 60 FPS silky smooth performance.
 * - Non-destructive live container resize: coordinates adapt smoothly without resetting score or progression.
 * - Pure procedural audio synthesis via snakeAudio.js (0 external assets).
 * - Consolidated 38px status header & 34px control bottom rail (0 page scroll).
 */

(function (root, factory) {
  if (typeof define === 'function' && define.amd) {
    define(['react', '../snakeEngine', '../snakeGenerator', '../snakePersistence', '../snakeAudio', '../maharashtraWorld'], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory(
      require('react'),
      require('../snakeEngine'),
      require('../snakeGenerator'),
      require('../snakePersistence'),
      require('../snakeAudio'),
      require('../maharashtraWorld')
    );
  } else {
    root.SnakeClassic = factory(
      root.React,
      root.SnakeEngine,
      root.SnakeGenerator,
      root.SnakePersistence,
      root.SnakeAudio,
      root.MaharashtraWorld
    );
  }
}(typeof self !== 'undefined' ? self : this, function (React, SnakeEngine, SnakeGenerator, SnakePersistence, SnakeAudio, MaharashtraWorld) {
  'use strict';

  const { useState, useEffect, useRef, useCallback } = React;
  const Engine = SnakeEngine || (typeof window !== 'undefined' ? window.SnakeEngine : null);
  const Generator = SnakeGenerator || (typeof window !== 'undefined' ? window.SnakeGenerator : null);
  const Persistence = SnakePersistence || (typeof window !== 'undefined' ? window.SnakePersistence : null);
  const Audio = SnakeAudio || (typeof window !== 'undefined' ? window.SnakeAudio : null);
  const MWorld = MaharashtraWorld || (typeof window !== 'undefined' ? window.MaharashtraWorld : null);

  function renderModalPortal(element) {
    if (typeof document !== 'undefined' && document.body && typeof ReactDOM !== 'undefined' && ReactDOM.createPortal) {
      return ReactDOM.createPortal(element, document.body);
    }
    return element;
  }

  // Progressive infinite trophy tiers
  const SNAKE_TROPHY_TIERS = [
    { maxMilestone: 5, name: 'Bronze Viper', icon: '🥉', color: '#cd7f32', tier: 'Bronze' },
    { maxMilestone: 10, name: 'Silver Cobra', icon: '🥈', color: '#c0c0c0', tier: 'Silver' },
    { maxMilestone: 20, name: 'Gold Python', icon: '🥇', color: '#ffd700', tier: 'Gold' },
    { maxMilestone: 50, name: 'Platinum Anaconda', icon: '💎', color: '#00e5ff', tier: 'Platinum' },
    { maxMilestone: 100, name: 'Diamond Ouroboros', icon: '👑', color: '#e040fb', tier: 'Diamond' },
    { maxMilestone: Infinity, name: 'Cosmic Leviathan', icon: '🌌', color: '#ff9100', tier: 'Cosmic' }
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

  // =========================================================================
  // ILLUSTRATED MAHARASHTRA WORLD CANVAS VECTOR RENDERERS
  // =========================================================================

  /**
   * Layer 0: Atmospheric Sky & Horizon Silhouette
   */
  function renderAtmosphericBackdrop(ctx, displayW, displayH, bounds, cellSize, theme, level, isChill) {
    const pal = theme.palette;
    const skyGrad = ctx.createLinearGradient(0, 0, 0, displayH);
    if (isChill) {
      skyGrad.addColorStop(0, '#071322');
      skyGrad.addColorStop(1, '#0f2438');
    } else {
      skyGrad.addColorStop(0, pal.skyTop || '#0b1120');
      skyGrad.addColorStop(1, pal.skyBottom || '#1e293b');
    }
    ctx.fillStyle = skyGrad;
    ctx.fillRect(0, 0, displayW, displayH);

    // Distant horizon silhouettes in upper sky backdrop
    const horizonH = Math.min(Math.floor(displayH * 0.35), 140);
    ctx.save();

    if (theme.horizonStyle === 'citadel_peaks' || theme.horizonStyle === 'mountain_peaks') {
      // Sahyadri mountain ridgelines & fort bastions
      ctx.fillStyle = isChill ? 'rgba(56, 189, 248, 0.08)' : 'rgba(0, 0, 0, 0.35)';
      ctx.beginPath();
      ctx.moveTo(0, horizonH);
      ctx.lineTo(displayW * 0.15, horizonH - 45);
      ctx.lineTo(displayW * 0.30, horizonH - 20);
      ctx.lineTo(displayW * 0.50, horizonH - 60); // Peak
      ctx.lineTo(displayW * 0.65, horizonH - 30);
      ctx.lineTo(displayW * 0.85, horizonH - 55);
      ctx.lineTo(displayW, horizonH - 15);
      ctx.lineTo(displayW, displayH);
      ctx.lineTo(0, displayH);
      ctx.closePath();
      ctx.fill();

      // Citadel bastions atop peaks
      ctx.fillStyle = isChill ? 'rgba(56, 189, 248, 0.15)' : 'rgba(0, 0, 0, 0.45)';
      const bx = displayW * 0.50 - 12;
      const by = horizonH - 72;
      ctx.fillRect(bx, by, 24, 14);
      // Saffron pennant flagpole
      ctx.strokeStyle = '#ef4444';
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.moveTo(bx + 12, by);
      ctx.lineTo(bx + 12, by - 12);
      ctx.stroke();
      ctx.fillStyle = '#f59e0b';
      ctx.beginPath();
      ctx.moveTo(bx + 12, by - 12);
      ctx.lineTo(bx + 20, by - 8);
      ctx.lineTo(bx + 12, by - 4);
      ctx.fill();
    } else if (theme.horizonStyle === 'sea_fort_islands') {
      // Arabian Sea rolling waves & distant fort towers
      ctx.fillStyle = isChill ? 'rgba(56, 189, 248, 0.1)' : 'rgba(2, 132, 199, 0.2)';
      ctx.beginPath();
      ctx.moveTo(0, horizonH - 15);
      for (let x = 0; x <= displayW; x += 40) {
        ctx.quadraticCurveTo(x + 20, horizonH - 25, x + 40, horizonH - 15);
      }
      ctx.lineTo(displayW, displayH);
      ctx.lineTo(0, displayH);
      ctx.closePath();
      ctx.fill();

      // Distant sea-fort rampart
      ctx.fillStyle = isChill ? 'rgba(56, 189, 248, 0.18)' : 'rgba(12, 74, 110, 0.45)';
      const fx = displayW * 0.70;
      ctx.fillRect(fx - 25, horizonH - 35, 50, 20);
      ctx.fillRect(fx - 8, horizonH - 45, 16, 10);
    } else if (theme.horizonStyle === 'temple_shikharas') {
      // Hemadpanthi temple shikhara outlines & deepmal towers
      ctx.fillStyle = isChill ? 'rgba(56, 189, 248, 0.1)' : 'rgba(120, 53, 15, 0.25)';
      const sx = displayW * 0.45;
      ctx.beginPath();
      ctx.moveTo(sx - 35, horizonH);
      ctx.lineTo(sx - 20, horizonH - 35);
      ctx.lineTo(sx, horizonH - 65); // Shikhara apex
      ctx.lineTo(sx + 20, horizonH - 35);
      ctx.lineTo(sx + 35, horizonH);
      ctx.closePath();
      ctx.fill();

      // Kalash finial
      ctx.fillStyle = '#fbbf24';
      ctx.beginPath();
      ctx.arc(sx, horizonH - 68, 3, 0, Math.PI * 2);
      ctx.fill();
    } else if (theme.horizonStyle === 'cave_cliffs') {
      // Massive monolithic basalt cliff overhangs & chaitya arches
      ctx.fillStyle = isChill ? 'rgba(56, 189, 248, 0.1)' : 'rgba(24, 24, 27, 0.5)';
      ctx.beginPath();
      ctx.moveTo(0, 0);
      ctx.lineTo(displayW, 0);
      ctx.lineTo(displayW, horizonH - 20);
      ctx.quadraticCurveTo(displayW * 0.75, horizonH - 50, displayW * 0.5, horizonH - 25);
      ctx.quadraticCurveTo(displayW * 0.25, horizonH, 0, horizonH - 30);
      ctx.closePath();
      ctx.fill();
    } else if (theme.horizonStyle === 'wada_parapets') {
      // Multi-tiered Peshwa timber rooflines & Delhi Darwaza spikes
      ctx.fillStyle = isChill ? 'rgba(56, 189, 248, 0.1)' : 'rgba(69, 26, 3, 0.35)';
      ctx.beginPath();
      ctx.moveTo(0, horizonH);
      for (let x = 0; x < displayW; x += 60) {
        ctx.lineTo(x + 10, horizonH - 25);
        ctx.lineTo(x + 30, horizonH - 40);
        ctx.lineTo(x + 50, horizonH - 25);
        ctx.lineTo(x + 60, horizonH);
      }
      ctx.lineTo(displayW, displayH);
      ctx.lineTo(0, displayH);
      ctx.closePath();
      ctx.fill();
    } else {
      // Dense forest & canopy ridge
      ctx.fillStyle = isChill ? 'rgba(56, 189, 248, 0.1)' : 'rgba(6, 78, 59, 0.3)';
      ctx.beginPath();
      ctx.moveTo(0, horizonH);
      for (let x = 0; x < displayW; x += 30) {
        ctx.arc(x + 15, horizonH - 20, 18, Math.PI, 0);
      }
      ctx.lineTo(displayW, displayH);
      ctx.lineTo(0, displayH);
      ctx.closePath();
      ctx.fill();
    }

    // Subtle uncharted territory grid stars / dots
    ctx.fillStyle = pal.particleColor || 'rgba(148, 163, 184, 0.15)';
    const totalCols = bounds.totalCols || Math.floor(displayW / cellSize);
    const totalRows = bounds.totalRows || Math.floor(displayH / cellSize);
    for (let y = 0; y < totalRows; y++) {
      for (let x = 0; x < totalCols; x++) {
        if (x < bounds.minX || x > bounds.maxX || y < bounds.minY || y > bounds.maxY) {
          ctx.beginPath();
          ctx.arc(x * cellSize + cellSize / 2, y * cellSize + cellSize / 2, 1.2, 0, Math.PI * 2);
          ctx.fill();
        }
      }
    }
    ctx.restore();
  }

  /**
   * Layer 1: Tactile Ground Material & Patterns
   */
  function renderTactileGround(ctx, tX, tY, tW, tH, bounds, cellSize, theme, level) {
    const pal = theme.palette;
    ctx.save();

    // Base ground floor fill
    ctx.fillStyle = pal.groundBase || '#0f172a';
    ctx.fillRect(tX, tY, tW, tH);

    const style = theme.groundStyle;
    const tileColor = pal.groundTile || '#1e293b';
    const groutColor = pal.groundGrout || '#09090b';

    if (style === 'basalt_flagstone') {
      // Interlocking dark basalt stone flagstones with masonry brick seams
      for (let y = bounds.minY; y <= bounds.maxY; y++) {
        const isOddRow = (y % 2 !== 0);
        const yPos = y * cellSize;
        for (let x = bounds.minX; x <= bounds.maxX; x++) {
          const xPos = x * cellSize;
          ctx.fillStyle = tileColor;
          ctx.fillRect(xPos + 1, yPos + 1, cellSize - 2, cellSize - 2);

          // Subtle stone chisel relief lines
          ctx.strokeStyle = groutColor;
          ctx.lineWidth = 1;
          ctx.strokeRect(xPos + 1, yPos + 1, cellSize - 2, cellSize - 2);

          // Corner stone highlight chip
          if ((x + y) % 3 === 0) {
            ctx.fillStyle = pal.frameDetail || '#475569';
            ctx.fillRect(xPos + 3, yPos + 3, 2, 2);
          }
        }
      }
    } else if (style === 'coastal_sand') {
      // Coastal fine sand with tidal wave contour gradients
      const sandGrad = ctx.createLinearGradient(tX, tY, tX + tW, tY + tH);
      sandGrad.addColorStop(0, pal.groundBase || '#082f49');
      sandGrad.addColorStop(0.5, pal.groundTile || '#0c4a6e');
      sandGrad.addColorStop(1, pal.groundBase || '#082f49');
      ctx.fillStyle = sandGrad;
      ctx.fillRect(tX, tY, tW, tH);

      // Gentle tidal wave ripple contours
      ctx.strokeStyle = 'rgba(56, 189, 248, 0.12)';
      ctx.lineWidth = 1.5;
      for (let y = bounds.minY; y <= bounds.maxY; y += 2) {
        ctx.beginPath();
        const yPos = y * cellSize + cellSize / 2;
        ctx.moveTo(tX, yPos);
        for (let x = tX; x <= tX + tW; x += 40) {
          ctx.quadraticCurveTo(x + 20, yPos - 3, x + 40, yPos);
        }
        ctx.stroke();
      }
    } else if (style === 'cave_carved_stone') {
      // Monolithic rock-hewn floor with ancient carved relief markings
      ctx.fillStyle = pal.groundBase || '#18181b';
      ctx.fillRect(tX, tY, tW, tH);

      ctx.strokeStyle = pal.groundTile || '#27272a';
      ctx.lineWidth = 1;
      for (let y = bounds.minY; y <= bounds.maxY; y++) {
        for (let x = bounds.minX; x <= bounds.maxX; x++) {
          const xPos = x * cellSize;
          const yPos = y * cellSize;
          ctx.strokeRect(xPos + 1, yPos + 1, cellSize - 2, cellSize - 2);

          // Carved glyph accent
          if ((x * 7 + y * 13) % 5 === 0) {
            ctx.fillStyle = 'rgba(168, 85, 247, 0.15)';
            ctx.beginPath();
            ctx.arc(xPos + cellSize / 2, yPos + cellSize / 2, 3, 0, Math.PI * 2);
            ctx.fill();
          }
        }
      }
    } else if (style === 'wada_terracotta') {
      // Interlocking terracotta courtyard floor tiles
      for (let y = bounds.minY; y <= bounds.maxY; y++) {
        const yPos = y * cellSize;
        for (let x = bounds.minX; x <= bounds.maxX; x++) {
          const xPos = x * cellSize;
          ctx.fillStyle = ((x + y) % 2 === 0) ? tileColor : pal.groundBase;
          ctx.fillRect(xPos + 1, yPos + 1, cellSize - 2, cellSize - 2);
          ctx.strokeStyle = groutColor;
          ctx.lineWidth = 1;
          ctx.strokeRect(xPos + 1, yPos + 1, cellSize - 2, cellSize - 2);
        }
      }
    } else if (style === 'forest_moss' || style === 'rocky_plateau') {
      // Rich forest loam with moss patches and wildflower flecks
      ctx.fillStyle = pal.groundBase || '#052e16';
      ctx.fillRect(tX, tY, tW, tH);

      for (let y = bounds.minY; y <= bounds.maxY; y++) {
        for (let x = bounds.minX; x <= bounds.maxX; x++) {
          const xPos = x * cellSize;
          const yPos = y * cellSize;
          if ((x * 3 + y * 5) % 4 === 0) {
            ctx.fillStyle = pal.groundTile || '#14532d';
            ctx.beginPath();
            ctx.arc(xPos + cellSize / 2, yPos + cellSize / 2, Math.floor(cellSize * 0.35), 0, Math.PI * 2);
            ctx.fill();
          }
          if ((x + y * 2) % 7 === 0) {
            ctx.fillStyle = pal.groundDecor || '#ec4899';
            ctx.beginPath();
            ctx.arc(xPos + cellSize * 0.3, yPos + cellSize * 0.3, 1.5, 0, Math.PI * 2);
            ctx.fill();
          }
        }
      }
    } else if (style === 'coronation_dais') {
      // Sovereign 24K coronation dais with radial gold inlay mosaic
      const daisGrad = ctx.createRadialGradient(tX + tW / 2, tY + tH / 2, 10, tX + tW / 2, tY + tH / 2, Math.max(tW, tH) / 2);
      daisGrad.addColorStop(0, '#2e1065');
      daisGrad.addColorStop(0.5, '#1e1b4b');
      daisGrad.addColorStop(1, '#0f0728');
      ctx.fillStyle = daisGrad;
      ctx.fillRect(tX, tY, tW, tH);

      // Gold inlaid grid lines
      ctx.strokeStyle = 'rgba(245, 158, 11, 0.25)';
      ctx.lineWidth = 1;
      for (let x = bounds.minX; x <= bounds.maxX + 1; x++) {
        ctx.beginPath();
        ctx.moveTo(x * cellSize, tY);
        ctx.lineTo(x * cellSize, tY + tH);
        ctx.stroke();
      }
      for (let y = bounds.minY; y <= bounds.maxY + 1; y++) {
        ctx.beginPath();
        ctx.moveTo(tX, y * cellSize);
        ctx.lineTo(tX + tW, y * cellSize);
        ctx.stroke();
      }
    } else {
      // River silt / default grid pavers
      for (let y = bounds.minY; y <= bounds.maxY; y++) {
        for (let x = bounds.minX; x <= bounds.maxX; x++) {
          const xPos = x * cellSize;
          const yPos = y * cellSize;
          ctx.fillStyle = tileColor;
          ctx.fillRect(xPos + 1, yPos + 1, cellSize - 2, cellSize - 2);
          ctx.strokeStyle = groutColor;
          ctx.lineWidth = 1;
          ctx.strokeRect(xPos + 1, yPos + 1, cellSize - 2, cellSize - 2);
        }
      }
    }
    ctx.restore();
  }

  /**
   * Layer 2: Architectural Board Framing & Ornamental Borders
   */
  function renderArchitecturalFraming(ctx, tX, tY, tW, tH, bounds, cellSize, theme, worldData, level, isChill) {
    const pal = theme.palette;
    const style = theme.frameStyle;
    ctx.save();

    const frameColor = isChill ? '#38bdf8' : (pal.frameBorder || '#475569');
    const accentColor = isChill ? '#00e5ff' : (pal.frameAccent || '#f59e0b');
    const embossColor = isChill ? '#0c4a6e' : (pal.frameEmboss || '#1e293b');

    // Outer framing thickness & drop shadow line
    ctx.strokeStyle = embossColor;
    ctx.lineWidth = 5;
    ctx.strokeRect(tX - 2, tY - 2, tW + 4, tH + 4);

    ctx.strokeStyle = frameColor;
    ctx.lineWidth = 2.5;
    ctx.strokeRect(tX + 1, tY + 1, tW - 2, tH - 2);

    const cornerSize = Math.min(18, Math.floor(cellSize * 0.9));

    if (style === 'sahyadri_battlement') {
      // Basalt stone fort battlements with crenellations & corner bastions
      ctx.fillStyle = accentColor;
      // Top-Left bastion
      ctx.fillRect(tX - 4, tY - 4, cornerSize, 4);
      ctx.fillRect(tX - 4, tY - 4, 4, cornerSize);
      // Top-Right bastion
      ctx.fillRect(tX + tW - cornerSize + 4, tY - 4, cornerSize, 4);
      ctx.fillRect(tX + tW, tY - 4, 4, cornerSize);
      // Bottom-Left bastion
      ctx.fillRect(tX - 4, tY + tH, cornerSize, 4);
      ctx.fillRect(tX - 4, tY + tH - cornerSize + 4, 4, cornerSize);
      // Bottom-Right bastion
      ctx.fillRect(tX + tW - cornerSize + 4, tY + tH, cornerSize, 4);
      ctx.fillRect(tX + tW, tY + tH - cornerSize + 4, 4, cornerSize);

      // Saffron corner emblem studs
      ctx.fillStyle = '#f59e0b';
      ctx.beginPath();
      ctx.arc(tX, tY, 3.5, 0, Math.PI * 2);
      ctx.arc(tX + tW, tY, 3.5, 0, Math.PI * 2);
      ctx.arc(tX, tY + tH, 3.5, 0, Math.PI * 2);
      ctx.arc(tX + tW, tY + tH, 3.5, 0, Math.PI * 2);
      ctx.fill();
    } else if (style === 'konkan_laterite') {
      // Red-laterite stone sea-wall with nautical brass rivets
      ctx.strokeStyle = accentColor;
      ctx.lineWidth = 2;
      ctx.strokeRect(tX + 3, tY + 3, tW - 6, tH - 6);

      // Nautical brass corner anchors
      ctx.fillStyle = '#38bdf8';
      for (let px of [tX, tX + tW]) {
        for (let py of [tY, tY + tH]) {
          ctx.beginPath();
          ctx.arc(px, py, 4, 0, Math.PI * 2);
          ctx.fill();
        }
      }
    } else if (style === 'chaitya_arch') {
      // Rock-cut chaitya arch ornamental corners
      ctx.strokeStyle = accentColor;
      ctx.lineWidth = 2.5;
      // Top arch crest
      ctx.beginPath();
      ctx.arc(tX + tW / 2, tY + 2, 8, Math.PI, 0);
      ctx.stroke();
      // Corner lotus brackets
      ctx.fillStyle = '#a855f7';
      for (let px of [tX + 4, tX + tW - 4]) {
        for (let py of [tY + 4, tY + tH - 4]) {
          ctx.beginPath();
          ctx.arc(px, py, 3.5, 0, Math.PI * 2);
          ctx.fill();
        }
      }
    } else if (style === 'wada_teak') {
      // Carved teak wood moulding with golden corner brackets
      ctx.strokeStyle = '#f59e0b';
      ctx.lineWidth = 2;
      ctx.strokeRect(tX + 2, tY + 2, tW - 4, tH - 4);
      // Golden corner brackets
      ctx.fillStyle = '#fbbf24';
      ctx.fillRect(tX - 2, tY - 2, cornerSize, 3);
      ctx.fillRect(tX - 2, tY - 2, 3, cornerSize);
      ctx.fillRect(tX + tW - cornerSize + 2, tY - 2, cornerSize, 3);
      ctx.fillRect(tX + tW - 1, tY - 2, 3, cornerSize);
      ctx.fillRect(tX - 2, tY + tH - 1, cornerSize, 3);
      ctx.fillRect(tX - 2, tY + tH - cornerSize + 2, 3, cornerSize);
      ctx.fillRect(tX + tW - cornerSize + 2, tY + tH - 1, cornerSize, 3);
      ctx.fillRect(tX + tW - 1, tY + tH - cornerSize + 2, 3, cornerSize);
    } else if (style === 'coronation_imperial') {
      // 24K gold filigree frame with Maratha imperial sun motifs
      ctx.strokeStyle = '#ffd700';
      ctx.lineWidth = 3;
      ctx.shadowColor = '#f59e0b';
      ctx.shadowBlur = 10;
      ctx.strokeRect(tX, tY, tW, tH);
      ctx.shadowBlur = 0;

      // Radiant golden sunburst corner medallions
      ctx.fillStyle = '#fbbf24';
      for (let px of [tX, tX + tW]) {
        for (let py of [tY, tY + tH]) {
          ctx.beginPath();
          ctx.arc(px, py, 6, 0, Math.PI * 2);
          ctx.fill();
          ctx.fillStyle = '#ef4444';
          ctx.beginPath();
          ctx.arc(px, py, 2.5, 0, Math.PI * 2);
          ctx.fill();
          ctx.fillStyle = '#fbbf24';
        }
      }
    } else {
      // Standard corner territory accents
      ctx.strokeStyle = accentColor;
      ctx.lineWidth = 3;
      // Top-Left
      ctx.beginPath(); ctx.moveTo(tX, tY + cornerSize); ctx.lineTo(tX, tY); ctx.lineTo(tX + cornerSize, tY); ctx.stroke();
      // Top-Right
      ctx.beginPath(); ctx.moveTo(tX + tW - cornerSize, tY); ctx.lineTo(tX + tW, tY); ctx.lineTo(tX + tW, tY + cornerSize); ctx.stroke();
      // Bottom-Left
      ctx.beginPath(); ctx.moveTo(tX, tY + tH - cornerSize); ctx.lineTo(tX, tY + tH); ctx.lineTo(tX + cornerSize, tY + tH); ctx.stroke();
      // Bottom-Right
      ctx.beginPath(); ctx.moveTo(tX + tW - cornerSize, tY + tH); ctx.lineTo(tX + tW, tY + tH); ctx.lineTo(tX + tW, tY + tH - cornerSize); ctx.stroke();
    }
    ctx.restore();
  }

  /**
   * Layer 3: Chunky 3D Illustrated Architectural Obstacles
   */
  /**
   * Layer 3: 3D Illustrated Location-Specific Architectural Obstacles
   * Renders genuine handcrafted vector sprites for forts, cannons, boulders, palms, rocks, boats,
   * chaitya pillars, stupas, teak columns, terracotta walls, deepmal lamp towers, ghat steps, and thickets.
   */
  function renderArchitecturalObstacle(ctx, obs, ox, oy, sz, cx, cy, pad, cellSize, theme, gameTick) {
    let vType = 'FORT_WALL';
    if (obs && typeof obs === 'object' && obs.visualType) {
      vType = obs.visualType;
    }
    const pal = theme.palette;
    ctx.save();

    if (vType === 'GOLDEN_BASTION') {
      // Level 1000 Royal Sovereign Golden Bastion (3D Gold Citadel Tower)
      ctx.fillStyle = 'rgba(0, 0, 0, 0.4)';
      ctx.beginPath();
      ctx.ellipse(cx, oy + sz - 2, sz * 0.45, sz * 0.2, 0, 0, Math.PI * 2);
      ctx.fill();

      // Polished 24K Gold Cylinder
      const bGrad = ctx.createLinearGradient(ox + pad, oy + pad, ox + pad + sz, oy + pad);
      bGrad.addColorStop(0, '#d97706');
      bGrad.addColorStop(0.3, '#fef08a');
      bGrad.addColorStop(0.7, '#f59e0b');
      bGrad.addColorStop(1, '#92400e');
      ctx.fillStyle = bGrad;
      ctx.beginPath();
      ctx.roundRect(ox + pad + 2, oy + pad + 4, sz - 4, sz - 6, [2, 2, 4, 4]);
      ctx.fill();

      // Crenellations & Golden Merlons
      ctx.fillStyle = '#fde047';
      const merlonW = Math.max(3, Math.floor(sz / 5));
      for (let mx = ox + pad + 3; mx < ox + pad + sz - 4; mx += merlonW + 2) {
        ctx.fillRect(mx, oy + pad + 1, merlonW, 4);
      }

      // Maratha Sunburst Imperial Emblem & Ruby Gem
      ctx.fillStyle = '#ef4444';
      ctx.beginPath();
      ctx.arc(cx, cy + 2, 2.5, 0, Math.PI * 2);
      ctx.fill();

      // Saffron Imperial Pennant
      ctx.strokeStyle = '#ef4444';
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.moveTo(cx, oy + pad);
      ctx.lineTo(cx, oy + pad - 6);
      ctx.stroke();
      ctx.fillStyle = '#f59e0b';
      ctx.beginPath();
      ctx.moveTo(cx, oy + pad - 6);
      ctx.lineTo(cx + 6, oy + pad - 4);
      ctx.lineTo(cx, oy + pad - 2);
      ctx.fill();
    } else if (vType === 'ROYAL_RAMPART') {
      // Level 1000 Sovereign Coronation Rampart
      ctx.fillStyle = 'rgba(0, 0, 0, 0.4)';
      ctx.fillRect(ox + pad + 2, oy + pad + 2, sz, sz);

      const rGrad = ctx.createLinearGradient(ox + pad, oy + pad, ox + pad, oy + pad + sz);
      rGrad.addColorStop(0, '#78350f');
      rGrad.addColorStop(0.5, '#451a03');
      rGrad.addColorStop(1, '#290f02');
      ctx.fillStyle = rGrad;
      ctx.fillRect(ox + pad, oy + pad, sz, sz);

      // Gold Coping & Chevron Inlay
      ctx.fillStyle = '#f59e0b';
      ctx.fillRect(ox + pad + 2, oy + pad + 2, sz - 4, 3);
      ctx.fillStyle = '#fbbf24';
      for (let x = ox + pad + 3; x < ox + pad + sz - 4; x += 6) {
        ctx.fillRect(x, oy + pad + 6, 3, sz - 10);
      }
    } else if (vType === 'BASTION' || vType === 'BASTION_TOWER') {
      // Authentic Sahyadri Circular Basalt Fort Bastion Tower
      ctx.fillStyle = 'rgba(0, 0, 0, 0.45)';
      ctx.beginPath();
      ctx.ellipse(cx, oy + sz, sz * 0.45, sz * 0.22, 0, 0, Math.PI * 2);
      ctx.fill();

      // Basalt Cylindrical Drum with 3D Radial Curve
      const drumGrad = ctx.createLinearGradient(ox + pad, cy, ox + pad + sz, cy);
      drumGrad.addColorStop(0, '#1e293b');
      drumGrad.addColorStop(0.35, '#475569');
      drumGrad.addColorStop(0.7, '#334155');
      drumGrad.addColorStop(1, '#0f172a');
      ctx.fillStyle = drumGrad;
      ctx.beginPath();
      ctx.arc(cx, cy, sz / 2 - 1, 0, Math.PI * 2);
      ctx.fill();

      // Stonework Ashlar Masonry Rings
      ctx.strokeStyle = 'rgba(15, 23, 42, 0.6)';
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.arc(cx, cy, sz * 0.35, 0, Math.PI * 2);
      ctx.stroke();

      // Crenellated Stone Merlons on Top Parapet
      ctx.fillStyle = '#334155';
      const numMerlons = 5;
      for (let m = 0; m < numMerlons; m++) {
        const ang = (m / numMerlons) * Math.PI * 2 + (gameTick * 0.01);
        const mx = cx + Math.cos(ang) * (sz * 0.38);
        const my = cy + Math.sin(ang) * (sz * 0.38);
        ctx.fillRect(mx - 1.5, my - 1.5, 3, 3);
      }

      // Deep Arrow Slit (Embrasure) with Amber Lantern Glint
      ctx.fillStyle = '#020617';
      ctx.fillRect(cx - 1.5, cy - sz * 0.22, 3, sz * 0.44);
      ctx.fillStyle = '#f59e0b';
      ctx.fillRect(cx - 1, cy - sz * 0.1, 2, 2.5);

      // Saffron Maratha Pennant Flag
      ctx.strokeStyle = '#ef4444';
      ctx.lineWidth = 1.2;
      ctx.beginPath();
      ctx.moveTo(cx, cy - sz * 0.35);
      ctx.lineTo(cx, cy - sz * 0.48);
      ctx.stroke();
      ctx.fillStyle = '#f59e0b';
      ctx.beginPath();
      ctx.moveTo(cx, cy - sz * 0.48);
      ctx.lineTo(cx + 6, cy - sz * 0.42);
      ctx.lineTo(cx, cy - sz * 0.36);
      ctx.fill();
    } else if (vType === 'FORT_GATE' || vType === 'CEREMONIAL_GATE' || vType === 'ARCHWAY' || vType === 'STONE_ARCH') {
      // Arched Fort Gate with Wooden Studded Doors & Keystone
      ctx.fillStyle = 'rgba(0, 0, 0, 0.35)';
      ctx.fillRect(ox + pad + 2, oy + pad + 2, sz, sz);

      // Stone Wall Pier
      ctx.fillStyle = '#334155';
      ctx.fillRect(ox + pad, oy + pad, sz, sz);

      // Deep Recessed Arch
      ctx.fillStyle = '#0f172a';
      ctx.beginPath();
      ctx.arc(cx, oy + pad + sz, sz / 2 - 2, Math.PI, 0);
      ctx.fill();

      // Reinforced Teak Gate Panels
      ctx.fillStyle = '#78350f';
      ctx.fillRect(cx - sz * 0.35, cy, sz * 0.7, sz * 0.5);

      // Iron Horizontal Straps & Anti-Elephant Brass Spikes
      ctx.fillStyle = '#fbbf24';
      ctx.fillRect(cx - sz * 0.32, cy + 2, sz * 0.64, 1.5);
      ctx.fillRect(cx - sz * 0.32, cy + sz * 0.25, sz * 0.64, 1.5);
      ctx.beginPath();
      ctx.arc(cx - sz * 0.15, cy + 3, 1.5, 0, Math.PI * 2);
      ctx.arc(cx + sz * 0.15, cy + 3, 1.5, 0, Math.PI * 2);
      ctx.fill();

      // Carved Keystone at Arch Apex
      ctx.fillStyle = pal.frameAccent || '#f59e0b';
      ctx.fillRect(cx - 2, oy + pad + 1, 4, 3.5);

      // Torch Sconces
      ctx.fillStyle = '#f59e0b';
      ctx.beginPath();
      ctx.arc(ox + pad + 3, cy, 1.8, 0, Math.PI * 2);
      ctx.arc(ox + pad + sz - 3, cy, 1.8, 0, Math.PI * 2);
      ctx.fill();
    } else if (vType === 'COASTAL_ROCK' || vType === 'LATERITE_CRAG') {
      // Konkan Laterite Reef Crag with Porous Cavities & Turquoise Tidal Foam
      ctx.fillStyle = 'rgba(2, 132, 199, 0.25)';
      ctx.beginPath();
      ctx.ellipse(cx, oy + sz - 1, sz * 0.48, sz * 0.2, 0, 0, Math.PI * 2);
      ctx.fill();

      // Multi-Faceted Laterite Red-Brown Rock
      const rockGrad = ctx.createLinearGradient(ox + pad, oy + pad, ox + pad + sz, oy + pad + sz);
      rockGrad.addColorStop(0, '#9a3412');
      rockGrad.addColorStop(0.45, '#7c2d12');
      rockGrad.addColorStop(0.8, '#431407');
      rockGrad.addColorStop(1, '#1c1917');
      ctx.fillStyle = rockGrad;
      ctx.beginPath();
      ctx.moveTo(ox + pad + 4, oy + pad + 2);
      ctx.lineTo(ox + pad + sz - 3, oy + pad + 5);
      ctx.lineTo(ox + pad + sz - 1, oy + pad + sz - 3);
      ctx.lineTo(ox + pad + 2, oy + pad + sz - 1);
      ctx.closePath();
      ctx.fill();

      // Porous Laterite Cavity Pits
      ctx.fillStyle = '#290f02';
      ctx.beginPath();
      ctx.arc(cx - 3, cy - 2, 2, 0, Math.PI * 2);
      ctx.arc(cx + 4, cy + 2, 1.8, 0, Math.PI * 2);
      ctx.arc(cx - 1, cy + 4, 1.5, 0, Math.PI * 2);
      ctx.fill();

      // White Barnacles & Turquoise Wave Foam Rim
      ctx.strokeStyle = '#38bdf8';
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.moveTo(ox + pad + 2, oy + pad + sz - 2);
      ctx.quadraticCurveTo(cx, oy + pad + sz - 5, ox + pad + sz - 1, oy + pad + sz - 2);
      ctx.stroke();
      ctx.fillStyle = '#ffffff';
      ctx.fillRect(ox + pad + 5, oy + pad + sz - 4, 1.5, 1.5);
      ctx.fillRect(cx + 2, oy + pad + sz - 5, 1.5, 1.5);
    } else if (vType === 'BOAT_DOCK' || vType === 'WOODEN_BOAT') {
      // Konkan Timber Jetty Pier & Moored Fishing Canoe
      ctx.fillStyle = 'rgba(0, 0, 0, 0.35)';
      ctx.fillRect(ox + pad + 1, oy + pad + 1, sz, sz);

      // Teak Wood Planks
      const plankH = Math.max(3, Math.floor(sz / 3.5));
      for (let p = 0; p < 3; p++) {
        const py = oy + pad + 1 + p * (plankH + 1);
        ctx.fillStyle = (p % 2 === 0) ? '#78350f' : '#451a03';
        ctx.fillRect(ox + pad + 1, py, sz - 2, plankH);
        // Brass Rivet Nails
        ctx.fillStyle = '#fbbf24';
        ctx.fillRect(ox + pad + 3, py + 1, 1.2, 1.2);
        ctx.fillRect(ox + pad + sz - 4, py + 1, 1.2, 1.2);
      }

      // Coiled Hemp Mooring Rope & Cleat
      ctx.strokeStyle = '#d97706';
      ctx.lineWidth = 1.2;
      ctx.beginPath();
      ctx.arc(cx, cy, 3, 0, Math.PI * 2);
      ctx.stroke();
      ctx.fillStyle = '#38bdf8';
      ctx.fillRect(cx - 1, cy - 3, 2, 6);
    } else if (vType === 'SEA_CHANNEL' || vType === 'COASTAL_PALM') {
      // Coastal Palm Trunk & Radiating Green Fronds
      ctx.fillStyle = 'rgba(0, 0, 0, 0.35)';
      ctx.beginPath();
      ctx.ellipse(cx, oy + sz - 1, sz * 0.4, sz * 0.18, 0, 0, Math.PI * 2);
      ctx.fill();

      // Ringed Brown Palm Trunk
      ctx.fillStyle = '#78350f';
      ctx.beginPath();
      ctx.moveTo(cx - 2, oy + sz - 2);
      ctx.quadraticCurveTo(cx - 1, cy, cx - 1.5, oy + pad + 6);
      ctx.lineTo(cx + 1.5, oy + pad + 6);
      ctx.quadraticCurveTo(cx + 1, cy, cx + 2, oy + sz - 2);
      ctx.closePath();
      ctx.fill();

      // Palm Crown of 5 Arching Emerald Fronds
      const frondAngles = [-2.2, -1.4, -0.6, 0.6, 1.4, 2.2];
      ctx.strokeStyle = '#059669';
      ctx.lineWidth = 2;
      for (let a of frondAngles) {
        ctx.beginPath();
        ctx.moveTo(cx, oy + pad + 6);
        ctx.quadraticCurveTo(cx + Math.cos(a) * (sz * 0.4), oy + pad + 6 + Math.sin(a) * (sz * 0.25), cx + Math.cos(a) * (sz * 0.5), oy + pad + 6 + Math.sin(a) * (sz * 0.45));
        ctx.stroke();
      }

      // Golden Coconuts
      ctx.fillStyle = '#d97706';
      ctx.beginPath();
      ctx.arc(cx - 2, oy + pad + 7, 2, 0, Math.PI * 2);
      ctx.arc(cx + 2, oy + pad + 7, 2, 0, Math.PI * 2);
      ctx.fill();
    } else if (vType === 'CAVE_PILLAR' || vType === 'CAVE_CHAITYA_PILLAR') {
      // Rock-Cut Monolithic Chaitya Pillar with Carved Plinth & Lotus Capital
      ctx.fillStyle = 'rgba(0, 0, 0, 0.4)';
      ctx.fillRect(ox + pad + 1, oy + pad + 1, sz, sz);

      // Fluted Octagonal Pillar Shaft
      const pilGrad = ctx.createLinearGradient(ox + pad, cy, ox + pad + sz, cy);
      pilGrad.addColorStop(0, '#27272a');
      pilGrad.addColorStop(0.35, '#52525b');
      pilGrad.addColorStop(0.7, '#3f3f46');
      pilGrad.addColorStop(1, '#18181b');
      ctx.fillStyle = pilGrad;
      ctx.fillRect(ox + pad + 3, oy + pad + 4, sz - 6, sz - 8);

      // Vertical Fluting Lines
      ctx.strokeStyle = '#18181b';
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.moveTo(cx - 2, oy + pad + 4); ctx.lineTo(cx - 2, oy + pad + sz - 4);
      ctx.moveTo(cx + 2, oy + pad + 4); ctx.lineTo(cx + 2, oy + pad + sz - 4);
      ctx.stroke();

      // Stepped Plinth Base & Lotus Capital
      ctx.fillStyle = '#71717a';
      ctx.fillRect(ox + pad + 1, oy + pad + 1, sz - 2, 3.5);
      ctx.fillRect(ox + pad + 1, oy + pad + sz - 4.5, sz - 2, 3.5);

      // Mystic Purple Relief Medallion
      ctx.fillStyle = '#a855f7';
      ctx.beginPath();
      ctx.arc(cx, cy, 2.5, 0, Math.PI * 2);
      ctx.fill();
    } else if (vType === 'ROCK_WALL' || vType === 'BUDDHIST_STUPA') {
      // Monolithic Carved Buddhist Stupa Dome with Harmika & Chhatra Finial
      ctx.fillStyle = 'rgba(0, 0, 0, 0.4)';
      ctx.beginPath();
      ctx.ellipse(cx, oy + sz - 1, sz * 0.45, sz * 0.2, 0, 0, Math.PI * 2);
      ctx.fill();

      // Hemispherical Stone Stupa Dome
      const stupaGrad = ctx.createRadialGradient(cx - 2, cy + 2, 2, cx, cy + 2, sz * 0.45);
      stupaGrad.addColorStop(0, '#52525b');
      stupaGrad.addColorStop(0.7, '#27272a');
      stupaGrad.addColorStop(1, '#18181b');
      ctx.fillStyle = stupaGrad;
      ctx.beginPath();
      ctx.arc(cx, oy + sz - 3, sz * 0.42, Math.PI, 0);
      ctx.fill();

      // Harmika Balustrade & 3-Tiered Chhatra Finial
      ctx.fillStyle = '#fbbf24';
      ctx.fillRect(cx - 3, oy + pad + 5, 6, 2.5);
      ctx.fillRect(cx - 4, oy + pad + 2, 8, 1.5);
      ctx.fillRect(cx - 2, oy + pad, 4, 1.2);
    } else if (vType === 'WADA_COLUMN' || vType === 'WADA_TEAK_PILLAR') {
      // Peshwa Carved Teakwood Pillar with Cypress Base & Golden Peacock Bracket
      ctx.fillStyle = 'rgba(0, 0, 0, 0.4)';
      ctx.fillRect(ox + pad + 2, oy + pad + 2, sz, sz);

      // Polished Teak Shaft
      const teakGrad = ctx.createLinearGradient(ox + pad, cy, ox + pad + sz, cy);
      teakGrad.addColorStop(0, '#451a03');
      teakGrad.addColorStop(0.4, '#78350f');
      teakGrad.addColorStop(0.8, '#92400e');
      teakGrad.addColorStop(1, '#290f02');
      ctx.fillStyle = teakGrad;
      ctx.fillRect(ox + pad + 4, oy + pad + 4, sz - 8, sz - 8);

      // Carved Wooden Bracket Capital (Peacock Flairs)
      ctx.fillStyle = '#ea580c';
      ctx.fillRect(ox + pad + 1, oy + pad + 1, sz - 2, 3.5);
      ctx.fillStyle = '#fbbf24';
      ctx.fillRect(ox + pad + 2, oy + pad + 4.5, sz - 4, 1.5);
      ctx.fillRect(cx - 2, cy - 2, 4, 4);
    } else if (vType === 'COURTYARD_WALL' || vType === 'WADA_FOUNTAIN') {
      // Terracotta Jali Courtyard Wall with Diamond Screen Pattern
      ctx.fillStyle = 'rgba(0, 0, 0, 0.35)';
      ctx.fillRect(ox + pad + 1, oy + pad + 1, sz, sz);

      ctx.fillStyle = '#78350f';
      ctx.fillRect(ox + pad, oy + pad, sz, sz);

      // Terracotta Coping Tiles
      ctx.fillStyle = '#c2410c';
      ctx.fillRect(ox + pad + 1, oy + pad + 1, sz - 2, 3);

      // Diamond Jali Openwork Screen Cutouts
      ctx.fillStyle = '#f97316';
      for (let jx = ox + pad + 3; jx < ox + pad + sz - 4; jx += 5) {
        ctx.beginPath();
        ctx.moveTo(jx + 2, cy - 2);
        ctx.lineTo(jx + 4, cy);
        ctx.lineTo(jx + 2, cy + 2);
        ctx.lineTo(jx, cy);
        ctx.closePath();
        ctx.fill();
      }
    } else if (vType === 'TREE_CLUSTER' || vType === 'TEAK_TREE' || vType === 'BANYAN_TRUNK') {
      // Sahyadri Teak & Banyan Canopy Tree
      ctx.fillStyle = 'rgba(0, 0, 0, 0.35)';
      ctx.beginPath();
      ctx.ellipse(cx, oy + sz - 2, sz * 0.45, sz * 0.2, 0, 0, Math.PI * 2);
      ctx.fill();

      // Textured Tree Trunk
      ctx.fillStyle = '#451a03';
      ctx.fillRect(cx - 2.5, cy, 5, sz / 2 - 2);

      // Multi-Lobed Lush Emerald Canopy
      ctx.fillStyle = '#064e3b';
      ctx.beginPath();
      ctx.arc(cx - 3, cy - 3, sz * 0.32, 0, Math.PI * 2);
      ctx.arc(cx + 3, cy - 3, sz * 0.32, 0, Math.PI * 2);
      ctx.arc(cx, cy - 6, sz * 0.34, 0, Math.PI * 2);
      ctx.fill();

      // Leaf Highlights
      ctx.fillStyle = '#10b981';
      ctx.beginPath();
      ctx.arc(cx - 2, cy - 6, sz * 0.2, 0, Math.PI * 2);
      ctx.arc(cx + 2, cy - 4, sz * 0.18, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = '#34d399';
      ctx.fillRect(cx - 1, cy - 7, 2, 2);
    } else if (vType === 'BOULDER' || vType === 'SAHYADRI_ROCK') {
      // Mossy Sahyadri Volcanic Basalt Boulder
      ctx.fillStyle = 'rgba(0, 0, 0, 0.4)';
      ctx.beginPath();
      ctx.ellipse(cx, oy + sz - 2, sz * 0.45, sz * 0.2, 0, 0, Math.PI * 2);
      ctx.fill();

      // Faceted Angular Basalt Stone
      const rockGrad = ctx.createLinearGradient(ox + pad, oy + pad, ox + pad + sz, oy + pad + sz);
      rockGrad.addColorStop(0, '#475569');
      rockGrad.addColorStop(0.5, '#334155');
      rockGrad.addColorStop(1, '#0f172a');
      ctx.fillStyle = rockGrad;
      ctx.beginPath();
      ctx.moveTo(ox + pad + 3, oy + pad + 5);
      ctx.lineTo(cx, oy + pad + 2);
      ctx.lineTo(ox + pad + sz - 2, oy + pad + 4);
      ctx.lineTo(ox + pad + sz - 1, oy + pad + sz - 3);
      ctx.lineTo(ox + pad + 2, oy + pad + sz - 2);
      ctx.closePath();
      ctx.fill();

      // Vibrant Green Sahyadri Moss Cushion
      ctx.fillStyle = '#10b981';
      ctx.beginPath();
      ctx.arc(cx - 2, oy + pad + 3, 3, 0, Math.PI * 2);
      ctx.arc(cx + 3, oy + pad + 4, 2.5, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = '#34d399';
      ctx.fillRect(cx - 1, oy + pad + 2, 2, 1.5);
    } else if (vType === 'STONE_PILLAR' || vType === 'TEMPLE_DEEPMAL') {
      // Hemadpanthi Temple Deepmal (Stepped Black Stone Lamp Tower)
      ctx.fillStyle = 'rgba(0, 0, 0, 0.4)';
      ctx.fillRect(ox + pad + 1, oy + pad + 1, sz, sz);

      // Tapered Black Stone Pillar
      ctx.fillStyle = '#1e293b';
      ctx.fillRect(cx - 3, oy + pad + 2, 6, sz - 4);

      // Projecting Stone Lamp Brackets
      ctx.fillStyle = '#475569';
      ctx.fillRect(cx - 6, cy - 3, 12, 2);
      ctx.fillRect(cx - 5, cy + 3, 10, 2);

      // Glowing Golden Diya Oil Flames
      ctx.fillStyle = '#f59e0b';
      ctx.beginPath();
      ctx.arc(cx - 5, cy - 4, 1.8, 0, Math.PI * 2);
      ctx.arc(cx + 5, cy - 4, 1.8, 0, Math.PI * 2);
      ctx.arc(cx - 4, cy + 2, 1.5, 0, Math.PI * 2);
      ctx.arc(cx + 4, cy + 2, 1.5, 0, Math.PI * 2);
      ctx.fill();
    } else if (vType === 'GHAT_STEPS' || vType === 'RIVER_STONE' || vType === 'STONE_BRIDGE') {
      // Stepped Hemadpanthi River Ghat Platforms
      ctx.fillStyle = 'rgba(0, 0, 0, 0.35)';
      ctx.fillRect(ox + pad + 1, oy + pad + 1, sz, sz);

      // 3 Descending Stone Steps
      const stepH = Math.max(3, Math.floor(sz / 3));
      for (let s = 0; s < 3; s++) {
        const sy = oy + pad + s * stepH;
        ctx.fillStyle = (s === 0) ? '#475569' : (s === 1 ? '#334155' : '#1e293b');
        ctx.fillRect(ox + pad + s * 2, sy, sz - s * 4, stepH);
        // Step Edge Bullnose Highlight
        ctx.fillStyle = '#94a3b8';
        ctx.fillRect(ox + pad + s * 2, sy, sz - s * 4, 1);
      }

      // Iron Mooring Ring
      ctx.strokeStyle = '#00e5ff';
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.arc(cx, oy + pad + sz - 4, 2, 0, Math.PI * 2);
      ctx.stroke();
    } else if (vType === 'BAMBOO_THICKET' || vType === 'ROOT_CLUSTER') {
      // Dense Bamboo Cane Cluster
      ctx.fillStyle = 'rgba(0, 0, 0, 0.3)';
      ctx.beginPath();
      ctx.ellipse(cx, oy + sz - 2, sz * 0.4, sz * 0.15, 0, 0, Math.PI * 2);
      ctx.fill();

      // 3 Bamboo Canes with Nodes
      const bXs = [cx - 4, cx, cx + 4];
      for (let bx of bXs) {
        ctx.fillStyle = '#059669';
        ctx.fillRect(bx - 1, oy + pad + 2, 2, sz - 4);
        // Golden Nodes
        ctx.fillStyle = '#fbbf24';
        ctx.fillRect(bx - 1.5, cy - 3, 3, 1.2);
        ctx.fillRect(bx - 1.5, cy + 3, 3, 1.2);
      }

      // Pointed Leaves
      ctx.fillStyle = '#34d399';
      ctx.beginPath();
      ctx.ellipse(cx - 3, cy - 4, 4, 1.5, -Math.PI / 4, 0, Math.PI * 2);
      ctx.ellipse(cx + 3, cy - 2, 4, 1.5, Math.PI / 4, 0, Math.PI * 2);
      ctx.fill();
    } else {
      // Default Basalt Fort Wall Masonry Block
      ctx.fillStyle = 'rgba(0, 0, 0, 0.35)';
      ctx.fillRect(ox + pad + 1, oy + pad + 1, sz, sz);

      ctx.fillStyle = '#1e293b';
      ctx.beginPath();
      ctx.roundRect(ox + pad, oy + pad, sz, sz, 3);
      ctx.fill();

      // Stone Chisel Relief Lines & Mortar Joints
      ctx.fillStyle = '#334155';
      ctx.fillRect(ox + pad + 2, oy + pad + 2, sz - 4, 2.5);
      ctx.fillStyle = '#0f172a';
      ctx.fillRect(cx - 1, oy + pad + 3, 2, sz - 6);

      // Corner Highlight Chip
      ctx.fillStyle = pal.frameAccent || '#f59e0b';
      ctx.fillRect(ox + pad + sz - 4, oy + pad + 2, 2, 2);
    }
    ctx.restore();
  }

  function renderThemedSnake(ctx, snake, direction, cellSize, theme, boostTimer, chillTimer, magnetTimer, gameTick, interpolatedSegments) {
    const len = snake.length;
    if (len === 0) return;
    const pal = theme.palette;
    ctx.save();

    const skin = theme.snakeSkin;

    for (let i = len - 1; i >= 0; i--) {
      const segData = (interpolatedSegments && interpolatedSegments[i])
        ? interpolatedSegments[i]
        : { x: snake[i][0], y: snake[i][1], hasWrap: false, wrapX: snake[i][0], wrapY: snake[i][1] };

      const sx = segData.x * cellSize;
      const sy = segData.y * cellSize;
      const isHead = (i === 0);
      const isTail = (i === len - 1);

      const drawSegment = (drawSx, drawSy) => {
        if (isHead) {
          // Snake Head
          let headColor = pal.snakeHead || '#22c55e';
          let glowColor = pal.snakeGlow || '#4ade80';

          if (boostTimer > 0) {
            headColor = '#f59e0b';
            glowColor = '#fbbf24';
          } else if (chillTimer > 0) {
            headColor = '#38bdf8';
            glowColor = '#00e5ff';
          } else if (magnetTimer > 0) {
            headColor = '#ec4899';
            glowColor = '#f472b6';
          }

          ctx.fillStyle = headColor;
          ctx.shadowColor = glowColor;
          ctx.shadowBlur = 12;

          ctx.beginPath();
          ctx.roundRect(drawSx + 1, drawSy + 1, cellSize - 2, cellSize - 2, 6);
          ctx.fill();
          ctx.shadowBlur = 0;

          // Cobra hood flare / sovereign crown motif
          if (skin === 'basalt_cobra' || skin === 'coronation_dragon') {
            ctx.fillStyle = pal.frameAccent || '#f59e0b';
            if (direction === 'UP' || direction === 'DOWN') {
              ctx.fillRect(drawSx - 2, drawSy + 4, 2, cellSize - 8);
              ctx.fillRect(drawSx + cellSize, drawSy + 4, 2, cellSize - 8);
            } else {
              ctx.fillRect(drawSx + 4, drawSy - 2, cellSize - 8, 2);
              ctx.fillRect(drawSx + 4, drawSy + cellSize, cellSize - 8, 2);
            }
          }

          // Expressive serpent eyes & slit pupils
          const eyeRadius = Math.max(1.8, Math.floor(cellSize * 0.14));
          const eyeOffset = Math.floor(cellSize * 0.28);
          ctx.fillStyle = pal.snakeEye || '#fef08a';

          let eye1X, eye1Y, eye2X, eye2Y;
          if (direction === 'UP') {
            eye1X = drawSx + eyeOffset; eye1Y = drawSy + eyeOffset;
            eye2X = drawSx + cellSize - eyeOffset; eye2Y = drawSy + eyeOffset;
          } else if (direction === 'DOWN') {
            eye1X = drawSx + eyeOffset; eye1Y = drawSy + cellSize - eyeOffset;
            eye2X = drawSx + cellSize - eyeOffset; eye2Y = drawSy + cellSize - eyeOffset;
          } else if (direction === 'LEFT') {
            eye1X = drawSx + eyeOffset; eye1Y = drawSy + eyeOffset;
            eye2X = drawSx + eyeOffset; eye2Y = drawSy + cellSize - eyeOffset;
          } else {
            eye1X = drawSx + cellSize - eyeOffset; eye1Y = drawSy + eyeOffset;
            eye2X = drawSx + cellSize - eyeOffset; eye2Y = drawSy + cellSize - eyeOffset;
          }

          ctx.beginPath(); ctx.arc(eye1X, eye1Y, eyeRadius, 0, Math.PI * 2); ctx.fill();
          ctx.beginPath(); ctx.arc(eye2X, eye2Y, eyeRadius, 0, Math.PI * 2); ctx.fill();

          // Eye slit pupils
          ctx.fillStyle = '#000000';
          ctx.beginPath(); ctx.arc(eye1X, eye1Y, eyeRadius * 0.5, 0, Math.PI * 2); ctx.fill();
          ctx.beginPath(); ctx.arc(eye2X, eye2Y, eyeRadius * 0.5, 0, Math.PI * 2); ctx.fill();

          // Forked tongue flicker (procedural animation)
          if ((gameTick % 8) < 4) {
            ctx.strokeStyle = '#ef4444';
            ctx.lineWidth = 1.5;
            ctx.beginPath();
            const tLen = 6;
            if (direction === 'UP') {
              ctx.moveTo(drawSx + cellSize / 2, drawSy);
              ctx.lineTo(drawSx + cellSize / 2, drawSy - tLen);
              ctx.lineTo(drawSx + cellSize / 2 - 2, drawSy - tLen - 2);
              ctx.moveTo(drawSx + cellSize / 2, drawSy - tLen);
              ctx.lineTo(drawSx + cellSize / 2 + 2, drawSy - tLen - 2);
            } else if (direction === 'DOWN') {
              ctx.moveTo(drawSx + cellSize / 2, drawSy + cellSize);
              ctx.lineTo(drawSx + cellSize / 2, drawSy + cellSize + tLen);
              ctx.lineTo(drawSx + cellSize / 2 - 2, drawSy + cellSize + tLen + 2);
              ctx.moveTo(drawSx + cellSize / 2, drawSy + cellSize + tLen);
              ctx.lineTo(drawSx + cellSize / 2 + 2, drawSy + cellSize + tLen + 2);
            } else if (direction === 'LEFT') {
              ctx.moveTo(drawSx, drawSy + cellSize / 2);
              ctx.lineTo(drawSx - tLen, drawSy + cellSize / 2);
              ctx.lineTo(drawSx - tLen - 2, drawSy + cellSize / 2 - 2);
              ctx.moveTo(drawSx - tLen, drawSy + cellSize / 2);
              ctx.lineTo(drawSx - tLen - 2, drawSy + cellSize / 2 + 2);
            } else {
              ctx.moveTo(drawSx + cellSize, drawSy + cellSize / 2);
              ctx.lineTo(drawSx + cellSize + tLen, drawSy + cellSize / 2);
              ctx.lineTo(drawSx + cellSize + tLen + 2, drawSy + cellSize / 2 - 2);
              ctx.moveTo(drawSx + cellSize + tLen, drawSy + cellSize / 2);
              ctx.lineTo(drawSx + cellSize + tLen + 2, drawSy + cellSize / 2 + 2);
            }
            ctx.stroke();
          }
        } else {
          // Snake Body Segments
          let bodyColorPrimary = pal.snakeBodyPrimary || '#16a34a';
          let bodyColorSecondary = pal.snakeBodySecondary || '#4ade80';

          if (boostTimer > 0) {
            bodyColorPrimary = '#d97706';
            bodyColorSecondary = '#fde047';
          } else if (chillTimer > 0) {
            bodyColorPrimary = '#0284c7';
            bodyColorSecondary = '#7dd3fc';
          } else if (magnetTimer > 0) {
            bodyColorPrimary = '#be185d';
            bodyColorSecondary = '#f472b6';
          }

          ctx.fillStyle = (i % 2 === 0) ? bodyColorPrimary : bodyColorSecondary;
          ctx.beginPath();
          const rad = isTail ? 6 : 4;
          ctx.roundRect(drawSx + 2, drawSy + 2, cellSize - 4, cellSize - 4, rad);
          ctx.fill();

          // Dorsal scale ridge / rune / gold ring
          ctx.fillStyle = pal.frameAccent || '#f59e0b';
          ctx.fillRect(drawSx + cellSize / 2 - 1, drawSy + cellSize / 2 - 1, 2, 2);
        }
      };

      drawSegment(sx, sy);
      if (segData.hasWrap) {
        drawSegment(segData.wrapX * cellSize, segData.wrapY * cellSize);
      }
    }
    ctx.restore();
  }

  /**
   * Layer 5: Regional Cultural Collectibles & Relics
   */
  /**
   * Layer 5: Regional Cultural Collectibles & Handcrafted Fruit Vector Sprites
   * Renders genuine handcrafted vector illustrations for strawberries, mangoes, oranges, grapes,
   * modaks, coconuts, jaggery blocks, custard apples, bananas, pedas, guavas, pomegranates, and royal relics.
   */
  function renderThemedCollectible(ctx, food, fx, fy, cx, cy, radius, cellSize, theme, gameTick) {
    const pal = theme.palette;
    const fType = food.type || (theme.regionalFood || theme.foodVisual || 'APPLE');
    const fVis = theme.foodVisual || '';
    ctx.save();

    if (fType === 'SOVEREIGN_CREST' || fVis === 'coronation_crest') {
      // Level 1000 Sovereign Coronation Crest (Maratha Rajmudra Seal)
      const grad = ctx.createRadialGradient(cx, cy, 2, cx, cy, radius + 2);
      grad.addColorStop(0, '#fffbeb');
      grad.addColorStop(0.3, '#fde047');
      grad.addColorStop(0.7, '#f59e0b');
      grad.addColorStop(1, '#b45309');
      ctx.fillStyle = grad;
      ctx.shadowColor = '#ffd700';
      ctx.shadowBlur = 18;
      ctx.beginPath();
      ctx.arc(cx, cy, radius + 1, 0, Math.PI * 2);
      ctx.fill();
      ctx.shadowBlur = 0;

      // Crown Jewel Accent & Seal Sunburst
      ctx.fillStyle = '#ef4444';
      ctx.beginPath();
      ctx.arc(cx, cy, 2.8, 0, Math.PI * 2);
      ctx.fill();
      ctx.strokeStyle = '#78350f';
      ctx.lineWidth = 1;
      ctx.strokeRect(cx - 3, cy - 3, 6, 6);
    } else if (fType === 'STRAWBERRY' || fVis === 'strawberry') {
      // Mahabaleshwar Fresh Strawberry (Heart-Shaped Crimson Berry + Yellow Seeds + Green Sepals)
      const grad = ctx.createRadialGradient(cx - 2, cy - 2, 2, cx, cy, radius);
      grad.addColorStop(0, '#f87171');
      grad.addColorStop(0.4, '#ef4444');
      grad.addColorStop(0.85, '#dc2626');
      grad.addColorStop(1, '#991b1b');
      ctx.fillStyle = grad;
      ctx.shadowColor = '#ef4444';
      ctx.shadowBlur = 12;

      // Heart/Conical Berry Silhouette
      ctx.beginPath();
      ctx.moveTo(cx, cy + radius);
      ctx.bezierCurveTo(cx - radius * 1.1, cy + radius * 0.3, cx - radius, cy - radius * 0.7, cx, cy - radius * 0.4);
      ctx.bezierCurveTo(cx + radius, cy - radius * 0.7, cx + radius * 1.1, cy + radius * 0.3, cx, cy + radius);
      ctx.closePath();
      ctx.fill();
      ctx.shadowBlur = 0;

      // Realistic Golden-Yellow Seed Specks
      ctx.fillStyle = '#fde047';
      const seedOffsets = [
        [-radius * 0.4, -radius * 0.1], [radius * 0.4, -radius * 0.1],
        [-radius * 0.2, radius * 0.3], [radius * 0.2, radius * 0.3],
        [0, 0], [0, radius * 0.6]
      ];
      for (let [sx, sy] of seedOffsets) {
        ctx.fillRect(cx + sx, cy + sy, 1.2, 1.8);
      }

      // 5-Pointed Fresh Green Sepals (Calyx) & Stem
      ctx.fillStyle = '#22c55e';
      ctx.beginPath();
      ctx.moveTo(cx, cy - radius * 0.4);
      ctx.lineTo(cx - radius * 0.8, cy - radius * 0.7);
      ctx.lineTo(cx - radius * 0.3, cy - radius * 0.4);
      ctx.lineTo(cx, cy - radius * 0.8);
      ctx.lineTo(cx + radius * 0.3, cy - radius * 0.4);
      ctx.lineTo(cx + radius * 0.8, cy - radius * 0.7);
      ctx.closePath();
      ctx.fill();

      // Stem
      ctx.strokeStyle = '#15803d';
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.moveTo(cx, cy - radius * 0.6);
      ctx.quadraticCurveTo(cx + 2, cy - radius - 2, cx + 4, cy - radius - 1);
      ctx.stroke();
    } else if (fType === 'ALPHONSO_MANGO' || fVis === 'alphonso_mango') {
      // Ratnagiri / Devgad Alphonso Hapus Mango (Golden S-Curve + Orange/Pink Blush + Stem Leaves)
      const grad = ctx.createRadialGradient(cx - 2, cy - 2, 2, cx, cy, radius + 1);
      grad.addColorStop(0, '#fef08a');
      grad.addColorStop(0.35, '#f59e0b');
      grad.addColorStop(0.7, '#ea580c');
      grad.addColorStop(1, '#dc2626');
      ctx.fillStyle = grad;
      ctx.shadowColor = '#f59e0b';
      ctx.shadowBlur = 12;

      // Characteristic Alphonso Beak Curve Silhouette
      ctx.beginPath();
      ctx.moveTo(cx, cy - radius);
      ctx.bezierCurveTo(cx + radius * 1.1, cy - radius * 0.5, cx + radius * 1.2, cy + radius * 0.6, cx + radius * 0.3, cy + radius);
      ctx.bezierCurveTo(cx - radius * 0.2, cy + radius * 1.1, cx - radius * 1.1, cy + radius * 0.5, cx - radius * 0.8, cy - radius * 0.2);
      ctx.bezierCurveTo(cx - radius * 0.6, cy - radius * 0.8, cx - radius * 0.2, cy - radius, cx, cy - radius);
      ctx.closePath();
      ctx.fill();
      ctx.shadowBlur = 0;

      // Glossy Specular Highlight Arc
      ctx.strokeStyle = 'rgba(255, 255, 255, 0.5)';
      ctx.lineWidth = 1.2;
      ctx.beginPath();
      ctx.arc(cx - 2, cy - 1, radius * 0.6, Math.PI * 0.8, Math.PI * 1.4);
      ctx.stroke();

      // Stem & Twin Green Leaves
      ctx.strokeStyle = '#78350f';
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.moveTo(cx, cy - radius);
      ctx.lineTo(cx, cy - radius - 3);
      ctx.stroke();
      ctx.fillStyle = '#16a34a';
      ctx.beginPath();
      ctx.ellipse(cx + 3, cy - radius - 2, 4, 1.8, Math.PI / 4, 0, Math.PI * 2);
      ctx.fill();
    } else if (fType === 'NAGPUR_ORANGE' || fVis === 'nagpur_orange') {
      // Nagpur Sweet Orange (Santra - Textured Spherical Orange + Leaf + Segment Detail)
      const grad = ctx.createRadialGradient(cx - 2, cy - 2, 2, cx, cy, radius);
      grad.addColorStop(0, '#fed7aa');
      grad.addColorStop(0.35, '#fb923c');
      grad.addColorStop(0.75, '#ea580c');
      grad.addColorStop(1, '#c2410c');
      ctx.fillStyle = grad;
      ctx.shadowColor = '#f97316';
      ctx.shadowBlur = 12;

      ctx.beginPath();
      ctx.arc(cx, cy, radius, 0, Math.PI * 2);
      ctx.fill();
      ctx.shadowBlur = 0;

      // Stippled Peel Pores Texture
      ctx.fillStyle = '#9a3412';
      ctx.fillRect(cx - 3, cy - 2, 1, 1);
      ctx.fillRect(cx + 3, cy + 2, 1, 1);
      ctx.fillRect(cx - 1, cy + 4, 1, 1);
      ctx.fillRect(cx + 2, cy - 4, 1, 1);

      // Curved Specular Highlight
      ctx.strokeStyle = 'rgba(255, 255, 255, 0.6)';
      ctx.lineWidth = 1.2;
      ctx.beginPath();
      ctx.arc(cx - 2, cy - 2, radius * 0.6, Math.PI * 0.9, Math.PI * 1.5);
      ctx.stroke();

      // Stem Node & Leaf
      ctx.fillStyle = '#78350f';
      ctx.beginPath();
      ctx.arc(cx, cy - radius, 1.5, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = '#15803d';
      ctx.beginPath();
      ctx.ellipse(cx + 3, cy - radius - 1, 4, 2, -Math.PI / 6, 0, Math.PI * 2);
      ctx.fill();
    } else if (fType === 'PURPLE_GRAPES' || fVis === 'purple_grapes') {
      // Nashik Table Grapes (Clustered Deep Violet Spheres + Translucent Bloom + Tendril)
      ctx.shadowColor = '#8b5cf6';
      ctx.shadowBlur = 10;
      const grapeR = Math.max(2.2, radius * 0.38);
      const grapes = [
        [cx, cy + radius * 0.6],
        [cx - grapeR * 0.9, cy + radius * 0.2], [cx + grapeR * 0.9, cy + radius * 0.2],
        [cx - grapeR * 1.4, cy - radius * 0.2], [cx, cy - radius * 0.2], [cx + grapeR * 1.4, cy - radius * 0.2],
        [cx - grapeR * 0.8, cy - radius * 0.6], [cx + grapeR * 0.8, cy - radius * 0.6]
      ];
      for (let [gx, gy] of grapes) {
        const gGrad = ctx.createRadialGradient(gx - 1, gy - 1, 1, gx, gy, grapeR);
        gGrad.addColorStop(0, '#c084fc');
        gGrad.addColorStop(0.5, '#7e22ce');
        gGrad.addColorStop(1, '#3b0764');
        ctx.fillStyle = gGrad;
        ctx.beginPath();
        ctx.arc(gx, gy, grapeR, 0, Math.PI * 2);
        ctx.fill();
      }
      ctx.shadowBlur = 0;

      // Woody Stem & Curly Vine Tendril
      ctx.strokeStyle = '#78350f';
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.moveTo(cx, cy - radius * 0.6);
      ctx.lineTo(cx, cy - radius - 2);
      ctx.stroke();
      ctx.strokeStyle = '#22c55e';
      ctx.lineWidth = 1.2;
      ctx.beginPath();
      ctx.arc(cx + 3, cy - radius - 1, 2, Math.PI, 0);
      ctx.stroke();
    } else if (fType === 'MODAK' || fVis === 'modak') {
      // Pune Ukadiche Modak (Steamed Ivory Teardrop + 7 Pinched Pleated Ridges + Saffron Strand)
      const grad = ctx.createRadialGradient(cx - 2, cy - 2, 2, cx, cy, radius);
      grad.addColorStop(0, '#ffffff');
      grad.addColorStop(0.5, '#fef3c7');
      grad.addColorStop(1, '#fde68a');
      ctx.fillStyle = grad;
      ctx.shadowColor = '#f59e0b';
      ctx.shadowBlur = 12;

      // Teardrop Modak Body
      ctx.beginPath();
      ctx.moveTo(cx, cy - radius);
      ctx.bezierCurveTo(cx + radius * 1.1, cy - radius * 0.2, cx + radius * 0.9, cy + radius, cx, cy + radius);
      ctx.bezierCurveTo(cx - radius * 0.9, cy + radius, cx - radius * 1.1, cy - radius * 0.2, cx, cy - radius);
      ctx.closePath();
      ctx.fill();
      ctx.shadowBlur = 0;

      // 7 Pinched Pleated Folds (Vertical Grooves)
      ctx.strokeStyle = '#d97706';
      ctx.lineWidth = 1;
      for (let ang = -0.7; ang <= 0.7; ang += 0.25) {
        ctx.beginPath();
        ctx.moveTo(cx, cy - radius);
        ctx.quadraticCurveTo(cx + ang * radius, cy, cx + ang * (radius * 0.8), cy + radius - 1);
        ctx.stroke();
      }

      // Saffron Strand (Kesar) at Apex
      ctx.strokeStyle = '#dc2626';
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.moveTo(cx, cy - radius);
      ctx.lineTo(cx + 2, cy - radius - 2.5);
      ctx.stroke();
    } else if (fType === 'TENDER_COCONUT' || fVis === 'tender_coconut') {
      // Konkan Tender Coconut (Shahaale - Green Husk + White Meat Ring + Straw)
      const grad = ctx.createRadialGradient(cx - 2, cy - 2, 2, cx, cy, radius);
      grad.addColorStop(0, '#a7f3d0');
      grad.addColorStop(0.4, '#34d399');
      grad.addColorStop(0.8, '#059669');
      grad.addColorStop(1, '#064e3b');
      ctx.fillStyle = grad;
      ctx.shadowColor = '#10b981';
      ctx.shadowBlur = 10;

      ctx.beginPath();
      ctx.ellipse(cx, cy + 1, radius, radius * 0.9, 0, 0, Math.PI * 2);
      ctx.fill();
      ctx.shadowBlur = 0;

      // Shaved Top Cut & Coconut Meat Ring
      ctx.fillStyle = '#ffffff';
      ctx.beginPath();
      ctx.ellipse(cx, cy - radius * 0.4, radius * 0.55, radius * 0.25, 0, 0, Math.PI * 2);
      ctx.fill();

      // Aqua Fresh Water Surface
      ctx.fillStyle = '#38bdf8';
      ctx.beginPath();
      ctx.ellipse(cx, cy - radius * 0.4, radius * 0.35, radius * 0.15, 0, 0, Math.PI * 2);
      ctx.fill();

      // Striped Straw
      ctx.strokeStyle = '#ef4444';
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.moveTo(cx, cy - radius * 0.4);
      ctx.lineTo(cx + 4, cy - radius - 3);
      ctx.stroke();
    } else if (fType === 'KOLHAPURI_JAGGERY' || fVis === 'kolhapuri_jaggery') {
      // Kolhapuri Amber Jaggery Block & Sugarcane Stalks
      ctx.shadowColor = '#d97706';
      ctx.shadowBlur = 10;
      const bGrad = ctx.createLinearGradient(cx - radius, cy - radius, cx + radius, cy + radius);
      bGrad.addColorStop(0, '#fde047');
      bGrad.addColorStop(0.5, '#d97706');
      bGrad.addColorStop(1, '#78350f');
      ctx.fillStyle = bGrad;
      ctx.fillRect(cx - radius * 0.7, cy - radius * 0.5, radius * 1.4, radius * 1.2);
      ctx.shadowBlur = 0;

      // Crystalline Glistening Flecks
      ctx.fillStyle = '#fef08a';
      ctx.fillRect(cx - radius * 0.3, cy - radius * 0.2, 2, 2);
      ctx.fillRect(cx + radius * 0.2, cy + radius * 0.2, 2, 2);

      // Sugarcane Stalk Cut
      ctx.fillStyle = '#15803d';
      ctx.fillRect(cx - radius * 0.8, cy - radius * 0.8, radius * 0.4, radius * 1.6);
      ctx.fillStyle = '#fbbf24';
      ctx.fillRect(cx - radius * 0.8, cy - radius * 0.1, radius * 0.4, 1.5);
    } else if (fType === 'CUSTARD_APPLE' || fVis === 'custard_apple') {
      // Daulatabad Sitaphal (Custard Apple - Knobby Polygonal Green Lobes)
      ctx.shadowColor = '#10b981';
      ctx.shadowBlur = 10;
      ctx.fillStyle = '#065f46';
      ctx.beginPath();
      ctx.arc(cx, cy, radius, 0, Math.PI * 2);
      ctx.fill();
      ctx.shadowBlur = 0;

      // Knobby Segment Lobes
      const lobeOffsets = [
        [0, 0], [-radius * 0.45, -radius * 0.3], [radius * 0.45, -radius * 0.3],
        [-radius * 0.45, radius * 0.3], [radius * 0.45, radius * 0.3],
        [0, -radius * 0.6], [0, radius * 0.6]
      ];
      for (let [lx, ly] of lobeOffsets) {
        const lGrad = ctx.createRadialGradient(cx + lx - 1, cy + ly - 1, 1, cx + lx, cy + ly, radius * 0.35);
        lGrad.addColorStop(0, '#a7f3d0');
        lGrad.addColorStop(0.6, '#34d399');
        lGrad.addColorStop(1, '#059669');
        ctx.fillStyle = lGrad;
        ctx.beginPath();
        ctx.arc(cx + lx, cy + ly, radius * 0.32, 0, Math.PI * 2);
        ctx.fill();
      }
    } else if (fType === 'JALGAON_BANANA' || fVis === 'jalgaon_banana') {
      // Jalgaon Golden Banana (Crescent Yellow Curve + Green Tip & Stem)
      ctx.shadowColor = '#eab308';
      ctx.shadowBlur = 10;
      const bGrad = ctx.createLinearGradient(cx - radius, cy - radius, cx + radius, cy + radius);
      bGrad.addColorStop(0, '#fef08a');
      bGrad.addColorStop(0.6, '#eab308');
      bGrad.addColorStop(1, '#ca8a04');
      ctx.fillStyle = bGrad;

      ctx.beginPath();
      ctx.moveTo(cx - radius * 0.7, cy - radius * 0.6);
      ctx.quadraticCurveTo(cx + radius * 1.1, cy, cx - radius * 0.6, cy + radius * 0.8);
      ctx.quadraticCurveTo(cx + radius * 0.5, cy, cx - radius * 0.7, cy - radius * 0.6);
      ctx.closePath();
      ctx.fill();
      ctx.shadowBlur = 0;

      // Green Tip & Brown Stem
      ctx.fillStyle = '#16a34a';
      ctx.fillRect(cx - radius * 0.8, cy + radius * 0.6, 3, 2.5);
      ctx.fillStyle = '#78350f';
      ctx.fillRect(cx - radius * 0.8, cy - radius * 0.7, 3, 2.5);
    } else if (fType === 'PRASAAD_PEDA' || fType === 'BHAKRI_POHA' || fVis === 'prasaad_lamp') {
      // Temple Prasaad Peda / Roasted Bhakri (Golden Round Disc + Pistachio Garnish)
      const grad = ctx.createRadialGradient(cx - 2, cy - 2, 2, cx, cy, radius);
      grad.addColorStop(0, '#fef3c7');
      grad.addColorStop(0.45, '#fbbf24');
      grad.addColorStop(0.85, '#d97706');
      grad.addColorStop(1, '#92400e');
      ctx.fillStyle = grad;
      ctx.shadowColor = '#f59e0b';
      ctx.shadowBlur = 10;

      ctx.beginPath();
      ctx.arc(cx, cy, radius, 0, Math.PI * 2);
      ctx.fill();
      ctx.shadowBlur = 0;

      // Pressed Center Stamp
      ctx.strokeStyle = '#b45309';
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.arc(cx, cy, radius * 0.45, 0, Math.PI * 2);
      ctx.stroke();

      // Pistachio Garnish & Cardamom Flecks
      ctx.fillStyle = '#16a34a';
      ctx.fillRect(cx - 1.5, cy - 1.5, 3, 3);
      ctx.fillStyle = '#451a03';
      ctx.fillRect(cx - 3, cy + 2, 1.2, 1.2);
      ctx.fillRect(cx + 2, cy - 3, 1.2, 1.2);
    } else if (fType === 'WAI_GUAVA' || fVis === 'wai_guava') {
      // Wai Sardar Guava (Pear-Shaped Lime-Green Fruit + Raised Crown)
      const grad = ctx.createRadialGradient(cx - 2, cy - 2, 2, cx, cy, radius);
      grad.addColorStop(0, '#d9f99d');
      grad.addColorStop(0.4, '#a3e635');
      grad.addColorStop(0.8, '#65a30d');
      grad.addColorStop(1, '#3f6212');
      ctx.fillStyle = grad;
      ctx.shadowColor = '#84cc16';
      ctx.shadowBlur = 10;

      ctx.beginPath();
      ctx.moveTo(cx, cy - radius);
      ctx.bezierCurveTo(cx + radius * 1.1, cy - radius * 0.3, cx + radius * 0.9, cy + radius, cx, cy + radius);
      ctx.bezierCurveTo(cx - radius * 0.9, cy + radius, cx - radius * 1.1, cy - radius * 0.3, cx, cy - radius);
      ctx.closePath();
      ctx.fill();
      ctx.shadowBlur = 0;

      // Crown Calyx
      ctx.fillStyle = '#3f6212';
      ctx.fillRect(cx - 2, cy + radius - 2, 4, 2);
    } else if (fType === 'POMEGRANATE' || fVis === 'pomegranate') {
      // Solapur Bhagwa Pomegranate (Ruby-Red Crowned Fruit + Sparkling Arils)
      const grad = ctx.createRadialGradient(cx - 2, cy - 2, 2, cx, cy, radius);
      grad.addColorStop(0, '#f87171');
      grad.addColorStop(0.4, '#dc2626');
      grad.addColorStop(0.8, '#991b1b');
      grad.addColorStop(1, '#450a0a');
      ctx.fillStyle = grad;
      ctx.shadowColor = '#ef4444';
      ctx.shadowBlur = 12;

      ctx.beginPath();
      ctx.arc(cx, cy, radius, 0, Math.PI * 2);
      ctx.fill();
      ctx.shadowBlur = 0;

      // Crowned Top Apex
      ctx.fillStyle = '#dc2626';
      ctx.beginPath();
      ctx.moveTo(cx - 3, cy - radius);
      ctx.lineTo(cx - 4, cy - radius - 3);
      ctx.lineTo(cx, cy - radius - 1);
      ctx.lineTo(cx + 4, cy - radius - 3);
      ctx.lineTo(cx + 3, cy - radius);
      ctx.fill();

      // Sparkling Aril Seeds
      ctx.fillStyle = '#fee2e2';
      ctx.fillRect(cx - 1, cy - 1, 2, 2);
      ctx.fillRect(cx + 2, cy + 1, 1.5, 1.5);
    } else if (fType === 'SAHYADRI_GEM' || fVis === 'sahyadri_gem') {
      // Multi-Faceted Sahyadri Ruby/Sapphire Gem in Gold Setting
      const grad = ctx.createRadialGradient(cx - 2, cy - 2, 1, cx, cy, radius);
      grad.addColorStop(0, '#fee2e2');
      grad.addColorStop(0.4, '#ef4444');
      grad.addColorStop(1, '#991b1b');
      ctx.fillStyle = grad;
      ctx.shadowColor = '#ef4444';
      ctx.shadowBlur = 14;
      ctx.beginPath();
      ctx.moveTo(cx, cy - radius);
      ctx.lineTo(cx + radius, cy);
      ctx.lineTo(cx, cy + radius);
      ctx.lineTo(cx - radius, cy);
      ctx.closePath();
      ctx.fill();
      ctx.shadowBlur = 0;

      // Gold Setting Prongs & Sparkling Glint
      ctx.fillStyle = '#fbbf24';
      ctx.beginPath();
      ctx.arc(cx, cy, 2, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = '#ffffff';
      ctx.fillRect(cx - 1, cy - radius * 0.4, 2, 2);
    } else if (fType === 'KONKAN_PEARL' || fVis === 'konkan_pearl') {
      // Konkan Iridescent Shimmering Pearl in Oyster
      ctx.fillStyle = '#0284c7';
      ctx.beginPath();
      ctx.arc(cx, cy + 2, radius, 0, Math.PI);
      ctx.fill();
      const grad = ctx.createRadialGradient(cx - 2, cy - 2, 1, cx, cy, radius - 1);
      grad.addColorStop(0, '#ffffff');
      grad.addColorStop(0.6, '#e0f2fe');
      grad.addColorStop(1, '#38bdf8');
      ctx.fillStyle = grad;
      ctx.shadowColor = '#38bdf8';
      ctx.shadowBlur = 12;
      ctx.beginPath();
      ctx.arc(cx, cy - 1, radius - 2, 0, Math.PI * 2);
      ctx.fill();
      ctx.shadowBlur = 0;
    } else if (fType === 'CAVE_RELIC' || fVis === 'cave_relic') {
      // Ancient Rock-Cut Golden Lotus Relic
      ctx.fillStyle = '#a855f7';
      ctx.shadowColor = '#c084fc';
      ctx.shadowBlur = 14;
      ctx.beginPath();
      ctx.arc(cx, cy, radius, 0, Math.PI * 2);
      ctx.fill();
      ctx.shadowBlur = 0;
      ctx.fillStyle = '#fbbf24';
      ctx.fillRect(cx - 2, cy - 2, 4, 4);
    } else if (fType === 'ROYAL_WADA_TOKEN' || fVis === 'wada_token') {
      // Maratha Gold Hon / Peshwa Coin
      ctx.fillStyle = '#f59e0b';
      ctx.shadowColor = '#fbbf24';
      ctx.shadowBlur = 12;
      ctx.beginPath();
      ctx.arc(cx, cy, radius, 0, Math.PI * 2);
      ctx.fill();
      ctx.shadowBlur = 0;
      ctx.fillStyle = '#dc2626';
      ctx.beginPath();
      ctx.arc(cx, cy, radius / 2, 0, Math.PI * 2);
      ctx.fill();
    } else if (fType === 'FOREST_SPIRIT' || fVis === 'forest_spirit') {
      // Sahyadri Wildflower Orchid
      const grad = ctx.createRadialGradient(cx, cy, 1, cx, cy, radius);
      grad.addColorStop(0, '#fbcfe8');
      grad.addColorStop(0.5, '#ec4899');
      grad.addColorStop(1, '#9d174d');
      ctx.fillStyle = grad;
      ctx.shadowColor = '#ec4899';
      ctx.shadowBlur = 14;
      ctx.beginPath();
      ctx.arc(cx, cy, radius, 0, Math.PI * 2);
      ctx.fill();
      ctx.shadowBlur = 0;
    } else if (fType === 'GOLDEN_APPLE') {
      // Radiant Golden Fig
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
    } else if (fType === 'CHILL_BERRY') {
      // Frost Ice Berry
      const grad = ctx.createRadialGradient(cx - 2, cy - 2, 2, cx, cy, radius);
      grad.addColorStop(0, '#e0f2fe');
      grad.addColorStop(0.5, '#38bdf8');
      grad.addColorStop(1, '#0284c7');
      ctx.fillStyle = grad;
      ctx.shadowColor = '#00e5ff';
      ctx.shadowBlur = 14;
      ctx.beginPath();
      ctx.arc(cx, cy, radius, 0, Math.PI * 2);
      ctx.fill();
      ctx.shadowBlur = 0;
    } else if (fType === 'SPEED_BOOST') {
      // Agni Flame
      const grad = ctx.createRadialGradient(cx - 2, cy - 2, 2, cx, cy, radius);
      grad.addColorStop(0, '#fef08a');
      grad.addColorStop(0.5, '#f59e0b');
      grad.addColorStop(1, '#d97706');
      ctx.fillStyle = grad;
      ctx.shadowColor = '#f59e0b';
      ctx.shadowBlur = 14;
      ctx.beginPath();
      ctx.arc(cx, cy, radius, 0, Math.PI * 2);
      ctx.fill();
      ctx.shadowBlur = 0;
    } else if (fType === 'MAGNET') {
      // Magnet Stone
      const grad = ctx.createRadialGradient(cx - 2, cy - 2, 2, cx, cy, radius);
      grad.addColorStop(0, '#fbcfe8');
      grad.addColorStop(0.5, '#ec4899');
      grad.addColorStop(1, '#be185d');
      ctx.fillStyle = grad;
      ctx.shadowColor = '#ec4899';
      ctx.shadowBlur = 14;
      ctx.beginPath();
      ctx.arc(cx, cy, radius, 0, Math.PI * 2);
      ctx.fill();
      ctx.shadowBlur = 0;
    } else {
      // Crisp Maharashtra Royal Apple
      const grad = ctx.createRadialGradient(cx - 2, cy - 2, 2, cx, cy, radius);
      grad.addColorStop(0, pal.foodSecondary || '#fca5a5');
      grad.addColorStop(0.5, pal.foodPrimary || '#ef4444');
      grad.addColorStop(1, pal.foodGlow || '#b91c1c');
      ctx.fillStyle = grad;
      ctx.shadowColor = pal.foodPrimary || '#ef4444';
      ctx.shadowBlur = 10;
      ctx.beginPath();
      ctx.arc(cx, cy, radius, 0, Math.PI * 2);
      ctx.fill();
      ctx.shadowBlur = 0;

      // Leaf accent
      ctx.fillStyle = '#22c55e';
      ctx.beginPath();
      ctx.ellipse(cx + 2, cy - radius - 1, 3, 2, Math.PI / 4, 0, Math.PI * 2);
      ctx.fill();
    }
    ctx.restore();
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
    const [uiBoardHeader, setUiBoardHeader] = useState(null);

    const containerRef = useRef(null);
    const canvasRef = useRef(null);
    const offscreenStaticCanvasRef = useRef(null);
    const staticCacheKeyRef = useRef('');
    const gameTickRef = useRef(0);
    const levelMilestoneTimerRef = useRef(null);

    // Live mutable refs for game loop
    const boardRef = useRef(null);
    const stateRef = useRef({ progression: null, isPaused: true, deathState: null, showLevelGoalCard: true });
    const layoutRef = useRef({ totalCols: 32, totalRows: 20, cellSize: 22, canvasW: 704, canvasH: 440 });
    const gameLoopTimerRef = useRef(null);
    const activeTimerRef = useRef(null);
    const nextDirectionQueueRef = useRef([]);
    const particlesRef = useRef([]);

    // Presentation / Smooth Visual Interpolation Layer Refs
    const previousSnakeRef = useRef([]);
    const lastTickTimeRef = useRef(0);
    const tickDurationRef = useRef(200);
    const interpolatedSegmentsRef = useRef([]);

    const syncPreviousSnake = useCallback((board, tickMs) => {
      if (!board || !board.snake) return;
      previousSnakeRef.current = board.snake.map(s => [s[0], s[1]]);
      lastTickTimeRef.current = (typeof performance !== 'undefined') ? performance.now() : Date.now();
      if (typeof tickMs === 'number') tickDurationRef.current = tickMs;
    }, []);

    // Keep stateRef synchronized
    useEffect(() => {
      stateRef.current.progression = progression;
      stateRef.current.isPaused = isPaused;
      stateRef.current.deathState = deathState;
      stateRef.current.showLevelGoalCard = showLevelGoalCard;
    }, [progression, isPaused, deathState, showLevelGoalCard]);

    // High-performance canvas drawing function (Multi-Layer Illustrated Architecture + Offscreen Caching + Presentation Interpolation)
    const drawCanvas = useCallback(() => {
      const canvas = canvasRef.current;
      const board = boardRef.current;
      const layout = layoutRef.current;
      if (!canvas || !board || !layout) return;

      const ctx = canvas.getContext('2d');
      if (!ctx) return;

      gameTickRef.current = (gameTickRef.current + 1) % 10000;
      const tick = gameTickRef.current;

      const { totalCols, totalRows, cellSize } = layout;
      const bounds = board.bounds || (Generator ? Generator.getTerritoryBounds(totalCols, totalRows, board.level || 1) : {
        minX: 0, maxX: totalCols - 1, minY: 0, maxY: totalRows - 1, spanX: totalCols, spanY: totalRows
      });

      const displayW = totalCols * cellSize;
      const displayH = totalRows * cellSize;

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

      // Resolve Maharashtra destination world & theme
      const worldData = MWorld ? MWorld.getWorldForLevel('classic', board.level || 1) : null;
      const theme = (worldData && worldData.theme) ? worldData.theme : (
        MWorld ? MWorld.getDestinationTheme(null, null, board.level || 1) : {
          palette: { skyTop: '#0b1120', skyBottom: '#1e293b', groundBase: '#0f172a', groundTile: '#1e293b', groundGrout: '#09090b', frameBorder: '#475569', frameAccent: '#f59e0b', frameEmboss: '#1e293b', snakeHead: '#22c55e', snakeBodyPrimary: '#16a34a', snakeBodySecondary: '#4ade80', snakeEye: '#fef08a', snakeGlow: '#4ade80', foodPrimary: '#ef4444', foodSecondary: '#fca5a5', foodGlow: '#b91c1c', particleColor: 'rgba(245, 158, 11, 0.35)' },
          skyStyle: 'misty_sahyadri', horizonStyle: 'citadel_peaks', frameStyle: 'sahyadri_battlement', groundStyle: 'basalt_flagstone', snakeSkin: 'basalt_cobra', foodVisual: 'sahyadri_gem', obstacleStyle: 'fort_bastion_gate'
        }
      );

      const tX = bounds.minX * cellSize;
      const tY = bounds.minY * cellSize;
      const tW = bounds.spanX * cellSize;
      const tH = bounds.spanY * cellSize;

      // =======================================================================
      // OFFSCREEN CACHE (LAYERS 0, 1, 2): SKY, HORIZON, GROUND & FRAME
      // =======================================================================
      const cacheKey = `${board.level}_${totalCols}_${totalRows}_${cellSize}_${bounds.minX}_${bounds.minY}_${bounds.spanX}_${bounds.spanY}_${board.chillTimer > 0 ? 'chill' : 'normal'}`;

      if (staticCacheKeyRef.current !== cacheKey || !offscreenStaticCanvasRef.current) {
        let offCanvas = offscreenStaticCanvasRef.current;
        if (!offCanvas) {
          offCanvas = (typeof document !== 'undefined') ? document.createElement('canvas') : null;
          offscreenStaticCanvasRef.current = offCanvas;
        }
        if (offCanvas) {
          offCanvas.width = displayW;
          offCanvas.height = displayH;
          const offCtx = offCanvas.getContext('2d');
          if (offCtx) {
            // Layer 0: Atmospheric Sky & Horizon Silhouette
            renderAtmosphericBackdrop(offCtx, displayW, displayH, bounds, cellSize, theme, board.level, board.chillTimer > 0);
            // Layer 1: Tactile Ground Material
            renderTactileGround(offCtx, tX, tY, tW, tH, bounds, cellSize, theme, board.level);
            // Layer 2: Architectural Board Framing
            renderArchitecturalFraming(offCtx, tX, tY, tW, tH, bounds, cellSize, theme, worldData, board.level, board.chillTimer > 0);
            staticCacheKeyRef.current = cacheKey;
          }
        }
      }

      // Blit cached background & framework
      if (offscreenStaticCanvasRef.current) {
        ctx.drawImage(offscreenStaticCanvasRef.current, 0, 0);
      } else {
        renderAtmosphericBackdrop(ctx, displayW, displayH, bounds, cellSize, theme, board.level, board.chillTimer > 0);
        renderTactileGround(ctx, tX, tY, tW, tH, bounds, cellSize, theme, board.level);
        renderArchitecturalFraming(ctx, tX, tY, tW, tH, bounds, cellSize, theme, worldData, board.level, board.chillTimer > 0);
      }

      // =======================================================================
      // DYNAMIC PLAYFIELD LAYERS (LAYERS 3, 4, 5) - CLIPPED TO PLAYFIELD RECT
      // Clamps all dynamic rendering, toroidal snake wraps, collectibles and obstacles
      // cleanly inside the territory box so segments never bleed across borders.
      // =======================================================================
      ctx.save();
      ctx.beginPath();
      ctx.rect(tX, tY, tW, tH);
      ctx.clip();

      // =======================================================================
      // LAYER 3: 3D ARCHITECTURAL OBSTACLES
      // =======================================================================
      const obstacleList = board.obstacles || [];
      for (let obs of obstacleList) {
        let ox, oy;
        if (typeof obs === 'string') {
          const parts = obs.split(',');
          ox = parseInt(parts[0], 10) * cellSize;
          oy = parseInt(parts[1], 10) * cellSize;
        } else if (typeof obs === 'number') {
          const stride = bounds.totalCols || bounds.spanX;
          ox = (obs % stride) * cellSize;
          oy = Math.floor(obs / stride) * cellSize;
        } else if (obs && typeof obs === 'object') {
          ox = obs.x * cellSize;
          oy = obs.y * cellSize;
        }

        if (ox === undefined || oy === undefined) continue;

        const pad = Math.max(1, Math.floor(cellSize * 0.08));
        const sz = cellSize - pad * 2;
        const cx = ox + cellSize / 2;
        const cy = oy + cellSize / 2;

        renderArchitecturalObstacle(ctx, obs, ox, oy, sz, cx, cy, pad, cellSize, theme, tick);
      }

      // Draw Gateways (if any)
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

      // =======================================================================
      // LAYER 4: REGIONAL CULTURAL COLLECTIBLES / FRUITS
      // =======================================================================
      const activeFoods = Array.isArray(board.foods) ? board.foods : (board.food ? [board.food] : []);
      for (let food of activeFoods) {
        if (!food || typeof food.x !== 'number' || typeof food.y !== 'number' ||
            food.x < bounds.minX || food.x > bounds.maxX || food.y < bounds.minY || food.y > bounds.maxY) {
          continue;
        }

        const fx = food.x * cellSize;
        const fy = food.y * cellSize;
        const radius = Math.max(4, (cellSize / 2) - 3);
        const cx = fx + cellSize / 2;
        const cy = fy + cellSize / 2;

        renderThemedCollectible(ctx, food, fx, fy, cx, cy, radius, cellSize, theme, tick);
      }

      // =======================================================================
      // LAYER 5: DESTINATION-THEMED SNAKE AVATAR (SMOOTH INTERPOLATED PRESENTATION)
      // =======================================================================
      const currSnake = board.snake || [];
      const prevSnake = previousSnakeRef.current;
      const now = (typeof performance !== 'undefined') ? performance.now() : Date.now();
      const elapsed = Math.max(0, now - (lastTickTimeRef.current || now));
      const duration = Math.max(1, tickDurationRef.current || 200);
      let progress = Math.min(1.0, Math.max(0, elapsed / duration));

      const isReducedMotion = (typeof window !== 'undefined' && window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches);
      if (stateRef.current.isPaused || stateRef.current.deathState || stateRef.current.showLevelGoalCard || isReducedMotion || !prevSnake || prevSnake.length === 0) {
        progress = 1.0;
      }

      // Preallocated mutation buffer: 0 GC allocations per frame
      const interpBuf = interpolatedSegmentsRef.current;
      while (interpBuf.length < currSnake.length) {
        interpBuf.push({ x: 0, y: 0, hasWrap: false, wrapX: 0, wrapY: 0 });
      }
      interpBuf.length = currSnake.length;

      for (let i = 0; i < currSnake.length; i++) {
        const curr = currSnake[i];
        const prev = (prevSnake && i < prevSnake.length)
          ? prevSnake[i]
          : (prevSnake && prevSnake.length > 0 ? prevSnake[prevSnake.length - 1] : curr);

        const dx = curr[0] - prev[0];
        const dy = curr[1] - prev[1];
        let renderX = prev[0];
        let renderY = prev[1];
        let hasWrap = false;
        let wrapX = renderX;
        let wrapY = renderY;

        // Toroidal boundary wrap handling along X axis
        if (dx < -1) {
          hasWrap = true;
          const unwrappedTargetX = curr[0] + bounds.spanX;
          renderX = prev[0] + (unwrappedTargetX - prev[0]) * progress;
          wrapX = renderX - bounds.spanX;
        } else if (dx > 1) {
          hasWrap = true;
          const unwrappedTargetX = curr[0] - bounds.spanX;
          renderX = prev[0] + (unwrappedTargetX - prev[0]) * progress;
          wrapX = renderX + bounds.spanX;
        } else {
          renderX = prev[0] + dx * progress;
        }

        // Toroidal boundary wrap handling along Y axis
        if (dy < -1) {
          hasWrap = true;
          const unwrappedTargetY = curr[1] + bounds.spanY;
          renderY = prev[1] + (unwrappedTargetY - prev[1]) * progress;
          wrapY = renderY - bounds.spanY;
        } else if (dy > 1) {
          hasWrap = true;
          const unwrappedTargetY = curr[1] - bounds.spanY;
          renderY = prev[1] + (unwrappedTargetY - prev[1]) * progress;
          wrapY = renderY + bounds.spanY;
        } else {
          renderY = prev[1] + dy * progress;
        }

        interpBuf[i].x = renderX;
        interpBuf[i].y = renderY;
        interpBuf[i].hasWrap = hasWrap;
        interpBuf[i].wrapX = wrapX;
        interpBuf[i].wrapY = wrapY;
      }

      renderThemedSnake(
        ctx,
        currSnake,
        board.direction,
        cellSize,
        theme,
        board.boostTimer,
        board.chillTimer,
        board.magnetTimer,
        tick,
        interpBuf
      );

      ctx.restore();

      // =======================================================================
      // LAYER 6: AMBIENT PARTICLES & FLOATING TEXT FX
      // =======================================================================
      const particles = particlesRef.current;
      for (let pIdx = particles.length - 1; pIdx >= 0; pIdx--) {
        const p = particles[pIdx];
        p.x += p.vx;
        p.y += p.vy;
        p.alpha -= 0.035;

        if (p.alpha <= 0) {
          particles.splice(pIdx, 1);
          continue;
        }

        ctx.fillStyle = p.color || (pal.particleColor || '#fde047');
        ctx.globalAlpha = Math.max(0, p.alpha);
        if (p.text) {
          ctx.font = 'bold 12px Inter, sans-serif';
          ctx.fillText(p.text, p.x, p.y);
        } else {
          ctx.beginPath();
          ctx.arc(p.x, p.y, p.radius || 2, 0, Math.PI * 2);
          ctx.fill();
        }
        ctx.globalAlpha = 1.0;
      }
    }, []);

    // Active Play Time Tracker
    useEffect(() => {
      if (isPaused || deathState || showLevelGoalCard) {
        if (activeTimerRef.current) clearInterval(activeTimerRef.current);
        return;
      }
      activeTimerRef.current = setInterval(() => {
        setProgression(prev => {
          if (!prev) return prev;
          const updated = {
            ...prev,
            totalPlayTimeSeconds: (prev.totalPlayTimeSeconds || 0) + 1
          };
          if (boardRef.current) {
            boardRef.current.elapsedSeconds = (boardRef.current.elapsedSeconds || 0) + 1;
            updated.currentBoard = { ...boardRef.current };
          }
          if (Persistence) Persistence.saveWorkingState(updated);
          return updated;
        });
      }, 1000);

      return () => {
        if (activeTimerRef.current) clearInterval(activeTimerRef.current);
      };
    }, [isPaused, deathState, showLevelGoalCard]);

    // Handle Level Completion
    const handleLevelWin = useCallback((completedBoard) => {
      if (Audio) Audio.playVictory();

      setProgression(prev => {
        if (!prev) return prev;
        const currentLevel = completedBoard.level || 1;
        const restarts = completedBoard.restartsCount || 0;
        const starsGained = Engine ? Engine.calculateStars(restarts) : 3;

        const nextLevel = currentLevel + 1;
        const nextCompleted = (prev.completedCount || 0) + 1;
        const newTotalStars = (prev.totalStars || 0) + starsGained;
        const nextStreak = (prev.currentStreak || 0) + 1;
        const bestStreak = Math.max(prev.bestStreak || 0, nextStreak);

        setLevelCelebration({
          level: currentLevel,
          stars: starsGained,
          restarts,
          totalStars: newTotalStars
        });

        // 50-Star Milestone Trophy Calculation
        const prevMilestone = Math.floor((prev.totalStars || 0) / 50);
        const newMilestone = Math.floor(newTotalStars / 50);
        if (newMilestone > prevMilestone) {
          const trophy = getTrophyForMilestone(newMilestone);
          if (trophy) {
            setMilestoneCelebration({ milestoneNumber: newMilestone, trophy });
            if (Audio) Audio.playMilestone();
            try {
              window.dispatchEvent(new CustomEvent('billsoft:trophy-unlocked', {
                detail: { milestoneNumber: newMilestone, starThreshold: newMilestone * 50, trophy }
              }));
            } catch (e) {}
          }
        }

        if (nextCompleted > 0 && nextCompleted % 50 === 0) {
          setLevelMilestoneCelebration(nextCompleted);
          try {
            window.dispatchEvent(new CustomEvent('billsoft:level-milestone-reached', { detail: { levelCount: nextCompleted } }));
          } catch (e) {}
        }

        // Trigger Location Unlock & Travel Transition on Sector Boundary (every 15 levels)
        if (MWorld && typeof MWorld.checkAndTriggerLocationUnlock === 'function') {
          MWorld.checkAndTriggerLocationUnlock(currentLevel, 'classic');
        }

        try {
          window.dispatchEvent(new CustomEvent('billsoft:game-progression', {
            detail: { gameType: 'classic', level: nextLevel, totalStars: newTotalStars, completedCount: nextCompleted }
          }));
        } catch (e) {}

        setTimeout(() => {
          setLevelCelebration(null);
        }, 3200);

        const nextGen = Generator ? Generator.generateLevel(nextLevel, {
          totalCols: layoutRef.current.totalCols,
          totalRows: layoutRef.current.totalRows
        }) : null;

        const nextBoard = nextGen ? {
          ...nextGen,
          elapsedSeconds: 0,
          restartsCount: 0
        } : null;

        boardRef.current = nextBoard;
        syncPreviousSnake(nextBoard, nextBoard ? nextBoard.baseTickMs : 200);
        setShowLevelGoalCard(true);
        setIsPaused(true);
        nextDirectionQueueRef.current = [];

        const updatedState = {
          ...prev,
          currentLevel: nextLevel,
          completedCount: nextCompleted,
          totalStars: newTotalStars,
          highestStarMilestone: Math.max(prev.highestStarMilestone || 0, newMilestone),
          currentStreak: nextStreak,
          bestStreak,
          currentBoard: nextBoard
        };

        if (Persistence) Persistence.saveProgressionImmediate(updatedState);
        setUiBoardHeader({ ...nextBoard });
        return updatedState;
      });
    }, [syncPreviousSnake]);

    // Main Game Engine Tick Callback
    const runGameTick = useCallback(() => {
      const board = boardRef.current;
      const state = stateRef.current;
      if (!board || state.isPaused || state.deathState || state.showLevelGoalCard) return;

      // Dequeue next direction input
      if (nextDirectionQueueRef.current.length > 0) {
        const nextDir = nextDirectionQueueRef.current.shift();
        if (Engine) {
          board.direction = Engine.changeDirection(board.direction, nextDir, board.snake.length);
        }
      }

      if (!Engine) return;

      // 1. Save previous snake segment positions for presentation interpolation
      const prevSnake = board.snake.map(s => [s[0], s[1]]);

      // 2. Calculate movement tick duration in ms
      const baseMs = board.baseTickMs || 200;
      const eaten = board.fruitsEatenInLevel || 0;
      const target = board.targetFruits || 5;
      const modifier = board.speedModifier || 1.0;
      const tickInterval = Engine.calculateIntraLevelTickMs
        ? Engine.calculateIntraLevelTickMs(baseMs, eaten, target, modifier)
        : Math.max(75, Math.round(baseMs * modifier));

      const result = Engine.tick(board);
      const nextBoard = result.nextState;
      boardRef.current = nextBoard;

      // 3. Update interpolation state
      previousSnakeRef.current = prevSnake;
      lastTickTimeRef.current = (typeof performance !== 'undefined') ? performance.now() : Date.now();
      tickDurationRef.current = tickInterval;

      // Handle Events
      if (result.event === 'DEATH') {
        if (Audio) Audio.playCrash();
        setDeathState({
          reason: result.deathReason,
          fruitsEaten: nextBoard.fruitsEatenInLevel || 0,
          targetFruits: nextBoard.targetFruits || 5
        });
        setIsPaused(true);
        setProgression(prev => {
          if (!prev) return prev;
          const updated = {
            ...prev,
            currentStreak: 0,
            currentBoard: { ...nextBoard }
          };
          if (Persistence) Persistence.saveWorkingState(updated);
          return updated;
        });
      } else if (result.event === 'WIN') {
        handleLevelWin(nextBoard);
      } else if (result.event === 'GROW') {
        if (Audio) Audio.playEat();
        if (result.noveltyEffect) {
          const head = nextBoard.snake[0];
          const cs = layoutRef.current.cellSize;
          particlesRef.current.push({
            x: head[0] * cs + cs / 2,
            y: head[1] * cs,
            text: result.noveltyEffect.text,
            vx: 0,
            vy: -1.2,
            alpha: 1.0,
            color: result.noveltyEffect.type === 'GOLDEN_APPLE' ? '#fde047' :
                   result.noveltyEffect.type === 'CHILL_BERRY' ? '#7dd3fc' :
                   result.noveltyEffect.type === 'SPEED_BOOST' ? '#fcd34d' :
                   result.noveltyEffect.type === 'MAGNET' ? '#f472b6' :
                   result.noveltyEffect.type === 'MYSTERY' ? '#d8b4fe' :
                   result.noveltyEffect.type === 'SAHYADRI_GEM' ? '#60a5fa' :
                   result.noveltyEffect.type === 'KONKAN_PEARL' ? '#38bdf8' :
                   result.noveltyEffect.type === 'CAVE_RELIC' ? '#c084fc' :
                   result.noveltyEffect.type === 'ROYAL_WADA_TOKEN' ? '#fbbf24' :
                   result.noveltyEffect.type === 'FOREST_SPIRIT' ? '#34d399' :
                   result.noveltyEffect.type === 'EMBER_RELIC' ? '#f97316' :
                   result.noveltyEffect.type === 'DECCAN_CRYSTAL' ? '#e0e7ff' :
                   result.noveltyEffect.type === 'EXPEDITION_RELIC' ? '#f59e0b' :
                   result.noveltyEffect.type === 'SOVEREIGN_CREST' ? '#ffd700' : '#fb923c'
          });
        }
        setProgression(prev => {
          if (!prev) return prev;
          const updated = {
            ...prev,
            totalFruitsEaten: (prev.totalFruitsEaten || 0) + 1,
            longestSnake: Math.max(prev.longestSnake || 3, nextBoard.snake.length),
            currentBoard: { ...nextBoard }
          };
          if (Persistence) Persistence.saveWorkingState(updated);
          return updated;
        });
      } else if (result.event === 'TELEPORT') {
        if (Audio) Audio.playPortal();
      }

      // Spawn replacement food if needed
      if (result.needsFoodSpawn && Generator) {
        const currentFoods = Array.isArray(nextBoard.foods) ? nextBoard.foods : (nextBoard.food ? [nextBoard.food] : []);
        const lvlCfg = Generator.getLevelConfig(nextBoard.level, {
          totalCols: layoutRef.current.totalCols,
          totalRows: layoutRef.current.totalRows
        });
        const maxTargets = (nextBoard.level >= 10 && nextBoard.bounds && nextBoard.bounds.spanX >= 24) ? 2 : 1;
        while (currentFoods.length < maxTargets) {
          const fresh = Generator.spawnFood(
            nextBoard.snake,
            nextBoard.obstacles,
            nextBoard.bounds,
            Math.random,
            lvlCfg,
            nextBoard,
            currentFoods
          );
          if (fresh) currentFoods.push(fresh);
          else break;
        }
        nextBoard.foods = currentFoods;
        nextBoard.food = currentFoods[0] || null;
      }

      setUiBoardHeader({ ...nextBoard });
      drawCanvas();
    }, [drawCanvas, handleLevelWin]);

    // Dynamic Game Loop Timer (Intra-Level Speed Progression)
    useEffect(() => {
      if (isPaused || deathState || showLevelGoalCard) {
        if (gameLoopTimerRef.current) clearTimeout(gameLoopTimerRef.current);
        return;
      }

      const scheduleNextTick = () => {
        const board = boardRef.current;
        const baseMs = (board && board.baseTickMs) ? board.baseTickMs : 200;
        const eaten = (board && board.fruitsEatenInLevel) ? board.fruitsEatenInLevel : 0;
        const target = (board && board.targetFruits) ? board.targetFruits : 5;
        const modifier = (board && board.speedModifier) ? board.speedModifier : 1.0;
        const tickInterval = Engine && Engine.calculateIntraLevelTickMs
          ? Engine.calculateIntraLevelTickMs(baseMs, eaten, target, modifier)
          : Math.max(75, Math.round(baseMs * modifier));

        gameLoopTimerRef.current = setTimeout(() => {
          runGameTick();
          if (!stateRef.current.isPaused && !stateRef.current.deathState && !stateRef.current.showLevelGoalCard) {
            scheduleNextTick();
          }
        }, tickInterval);
      };

      scheduleNextTick();

      return () => {
        if (gameLoopTimerRef.current) clearTimeout(gameLoopTimerRef.current);
      };
    }, [isPaused, deathState, showLevelGoalCard, runGameTick]);

    // Keyboard Input Handler (Arrow keys, WASD, Space/Esc pause, Death restart)
    useEffect(() => {
      const handleKeyDown = (e) => {
        const target = e.target;
        if (target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable)) {
          return;
        }

        const key = e.key;
        let reqDir = null;
        if (key === 'ArrowUp' || key === 'w' || key === 'W') reqDir = 'UP';
        else if (key === 'ArrowDown' || key === 's' || key === 'S') reqDir = 'DOWN';
        else if (key === 'ArrowLeft' || key === 'a' || key === 'A') reqDir = 'LEFT';
        else if (key === 'ArrowRight' || key === 'd' || key === 'D') reqDir = 'RIGHT';

        // 1. Restart on death with directional key or Space/Enter/R
        if (stateRef.current.deathState) {
          if (reqDir || key === ' ' || key === 'Enter' || key === 'r' || key === 'R' || key === 'Escape') {
            e.preventDefault();
            handleRestartLevel(reqDir);
            return;
          }
        }

        // 2. Start from level goal card
        if (stateRef.current.showLevelGoalCard) {
          if (reqDir || key === ' ' || key === 'Enter') {
            e.preventDefault();
            handleStartLevel(reqDir);
            return;
          }
        }

        // 3. Space/Escape pause toggle
        if (key === ' ' || key === 'Escape') {
          e.preventDefault();
          setIsPaused(prev => !prev);
          return;
        }

        // 4. Quick restart with 'r' or 'R'
        if (key === 'r' || key === 'R') {
          e.preventDefault();
          handleRestartLevel();
          return;
        }

        // 5. Active gameplay directional navigation
        if (reqDir) {
          e.preventDefault();
          if (stateRef.current.isPaused) {
            setIsPaused(false);
          }
          if (nextDirectionQueueRef.current.length < 3) {
            nextDirectionQueueRef.current.push(reqDir);
          }
        }
      };

      window.addEventListener('keydown', handleKeyDown, { passive: false });
      return () => window.removeEventListener('keydown', handleKeyDown);
    }, [showLevelGoalCard, isPaused, deathState]);

    // Robust ResizeObserver for Snake Stage Container
    useEffect(() => {
      const container = containerRef.current;
      if (!container) return;

      const handleResize = () => {
        const rect = container.getBoundingClientRect();
        const availableW = Math.max(300, Math.floor(rect.width - 8));
        const availableH = Math.max(200, Math.floor(rect.height - 8));

        // Clamped cell size S in [18, 32]
        let cols = Math.floor(availableW / 24);
        let rows = Math.floor(availableH / 24);

        cols = Math.max(16, Math.min(48, cols));
        rows = Math.max(12, Math.min(30, rows));

        let cellSize = Math.min(
          Math.floor(availableW / cols),
          Math.floor(availableH / rows)
        );
        cellSize = Math.max(18, Math.min(32, cellSize));

        const canvasW = cols * cellSize;
        const canvasH = rows * cellSize;

        layoutRef.current = { totalCols: cols, totalRows: rows, cellSize, canvasW, canvasH };

        if (boardRef.current && Generator) {
          boardRef.current = Generator.adaptBoardDimensions(boardRef.current, cols, rows);
          syncPreviousSnake(boardRef.current);
          setUiBoardHeader({ ...boardRef.current });
        }
        drawCanvas();
      };

      const ro = new ResizeObserver(() => {
        handleResize();
      });
      ro.observe(container);
      handleResize();

      return () => ro.disconnect();
    }, [drawCanvas, syncPreviousSnake]);

    // Initial State Loader & Lifecycle Hook
    useEffect(() => {
      async function init() {
        let loaded = null;
        if (Persistence) {
          loaded = await Persistence.loadAndRecoverState();
        }
        if (!loaded) {
          loaded = Persistence ? Persistence.createDefaultState() : { currentLevel: 1, totalStars: 0, completedCount: 0 };
        }

        const currentLvl = loaded.currentLevel || 1;
        const cols = layoutRef.current.totalCols || 32;
        const rows = layoutRef.current.totalRows || 20;

        let activeBoard = loaded.currentBoard;
        if (!activeBoard || activeBoard.level !== currentLvl) {
          if (Generator) {
            activeBoard = Generator.generateLevel(currentLvl, { totalCols: cols, totalRows: rows });
          }
        } else if (Generator) {
          activeBoard = Generator.adaptBoardDimensions(activeBoard, cols, rows);
        }

        boardRef.current = activeBoard;
        syncPreviousSnake(activeBoard, activeBoard ? activeBoard.baseTickMs : 200);
        setProgression(loaded);
        setUiBoardHeader(activeBoard);
        setShowLevelGoalCard(true);
        setIsPaused(true);

        try {
          window.dispatchEvent(new CustomEvent('billsoft:game-progression', {
            detail: { gameType: 'classic', level: loaded.currentLevel || 1, totalStars: loaded.totalStars || 0, completedCount: loaded.completedCount || 0 }
          }));
        } catch (e) {}

        requestAnimationFrame(drawCanvas);
      }

      init();

      const handleReset = (e) => {
        const cleanState = (e && e.detail && e.detail.state) ? e.detail.state : (Persistence ? Persistence.createDefaultState() : { currentLevel: 1, totalStars: 0, completedCount: 0 });
        const cols = layoutRef.current.totalCols || 32;
        const rows = layoutRef.current.totalRows || 20;
        let freshBoard = null;
        if (Generator) {
          freshBoard = Generator.generateLevel(1, { totalCols: cols, totalRows: rows });
        }
        boardRef.current = freshBoard;
        syncPreviousSnake(freshBoard, freshBoard ? freshBoard.baseTickMs : 200);
        setProgression(cleanState);
        setUiBoardHeader(freshBoard);
        setShowLevelGoalCard(true);
        setIsPaused(true);
        setDeathState(null);
        setCompletedCelebration(null);
        requestAnimationFrame(drawCanvas);
      };

      window.addEventListener('billsoft:snake-progress-reset', handleReset);

      return () => {
        window.removeEventListener('billsoft:snake-progress-reset', handleReset);
        if (Persistence) Persistence.flushPending();
      };
    }, [drawCanvas, syncPreviousSnake]);

    // Continuous Animation Frame for visual effects
    useEffect(() => {
      let animId;
      const loop = () => {
        drawCanvas();
        animId = requestAnimationFrame(loop);
      };
      animId = requestAnimationFrame(loop);
      return () => cancelAnimationFrame(animId);
    }, [drawCanvas]);

    // Actions
    const handleStartLevel = (initialDir) => {
      if (initialDir && boardRef.current && ['UP', 'DOWN', 'LEFT', 'RIGHT'].includes(initialDir)) {
        boardRef.current.direction = initialDir;
        nextDirectionQueueRef.current = [initialDir];
      }
      syncPreviousSnake(boardRef.current, boardRef.current ? boardRef.current.baseTickMs : 200);
      setShowLevelGoalCard(false);
      setIsPaused(false);
      setDeathState(null);
    };

    const handleRestartLevel = (initialDir) => {
      const currentLvl = progression ? progression.currentLevel : 1;
      const cols = layoutRef.current.totalCols || 32;
      const rows = layoutRef.current.totalRows || 20;
      const freshGen = Generator ? Generator.generateLevel(currentLvl, { totalCols: cols, totalRows: rows }) : null;

      const restarts = (boardRef.current ? boardRef.current.restartsCount : 0) + 1;
      const chosenDir = (initialDir && ['UP', 'DOWN', 'LEFT', 'RIGHT'].includes(initialDir))
        ? initialDir
        : (freshGen ? freshGen.direction : 'RIGHT');

      const freshBoard = freshGen ? {
        ...freshGen,
        direction: chosenDir,
        restartsCount: restarts,
        elapsedSeconds: 0
      } : null;

      boardRef.current = freshBoard;
      syncPreviousSnake(freshBoard, freshBoard ? freshBoard.baseTickMs : 200);
      setDeathState(null);
      setShowLevelGoalCard(false);
      setIsPaused(false);
      nextDirectionQueueRef.current = (initialDir && ['UP', 'DOWN', 'LEFT', 'RIGHT'].includes(initialDir)) ? [initialDir] : [];

      setProgression(prev => {
        if (!prev) return prev;
        const updated = {
          ...prev,
          currentStreak: 0,
          currentBoard: freshBoard
        };
        if (Persistence) Persistence.saveWorkingState(updated);
        return updated;
      });

      setUiBoardHeader(freshBoard);
      drawCanvas();
    };

    const handleToggleMute = () => {
      const next = !isMuted;
      setIsMuted(next);
      if (Audio) Audio.setMuted(next);
    };

    const currentLvl = progression ? progression.currentLevel : 1;
    const totalStars = progression ? progression.totalStars : 0;
    const fruitsEaten = uiBoardHeader ? (uiBoardHeader.fruitsEatenInLevel || 0) : 0;
    const targetFruits = uiBoardHeader ? (uiBoardHeader.targetFruits || 5) : 5;
    const streak = progression ? (progression.currentStreak || 0) : 0;
    const bestStreak = progression ? (progression.bestStreak || 0) : 0;
    const milestoneCount = Math.floor(totalStars / 50);

    return React.createElement('div', {
      className: 'snake-classic-container',
      style: {
        width: '100%',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'space-between',
        overflow: 'hidden',
        boxSizing: 'border-box'
      }
    }, [
      // 1. CONSOLIDATED COMPACT STATUS HEADER (38px)
      React.createElement('div', {
        key: 'snake-header',
        style: {
          width: '100%',
          maxWidth: '1280px',
          height: '38px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '0 16px',
          background: 'rgba(15, 23, 42, 0.75)',
          backdropFilter: 'blur(8px)',
          border: '1px solid rgba(56, 189, 248, 0.2)',
          borderRadius: '8px',
          margin: '0 0 6px 0',
          boxSizing: 'border-box',
          fontSize: '13px',
          color: '#f1f5f9',
          flexShrink: 0
        }
      }, [
        React.createElement('div', { key: 'header-left', style: { display: 'flex', alignItems: 'center', gap: '12px' } }, [
          React.createElement('span', { key: 'game-title', style: { fontWeight: 700, color: '#38bdf8', letterSpacing: '0.04em' } }, '🐍 TAKE A BREAK'),
          React.createElement('span', { key: 'level-badge', style: { background: 'rgba(56, 189, 248, 0.15)', border: '1px solid #38bdf8', padding: '2px 8px', borderRadius: '4px', fontWeight: 600 } }, `Level ${currentLvl}`),
          React.createElement('span', { key: 'stars-badge', style: { color: '#fbbf24', fontWeight: 600 } }, `⭐ ${totalStars} Stars`),
          milestoneCount > 0 ? React.createElement('span', { key: 'milestone-badge', style: { color: '#00e5ff', fontWeight: 600 } }, `🏆 M${milestoneCount}`) : null
        ]),
        React.createElement('div', { key: 'header-right', style: { display: 'flex', alignItems: 'center', gap: '14px' } }, [
          React.createElement('span', { key: 'streak-badge', style: { color: streak >= 3 ? '#f59e0b' : '#94a3b8', fontWeight: 600 } }, `🔥 Streak: ${streak} (Best: ${bestStreak})`),
          React.createElement('button', {
            key: 'stats-btn',
            onClick: () => setShowStatsModal(true),
            style: {
              background: 'transparent',
              border: '1px solid rgba(148, 163, 184, 0.3)',
              color: '#94a3b8',
              padding: '2px 8px',
              borderRadius: '4px',
              cursor: 'pointer',
              fontSize: '12px'
            }
          }, '📊 Stats')
        ])
      ]),

      // 2. DOMINANT RESPONSIVE CANVAS STAGE
      React.createElement('div', {
        key: 'snake-stage',
        ref: containerRef,
        style: {
          width: '100%',
          flex: 1,
          minHeight: 0,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          position: 'relative',
          overflow: 'hidden',
          borderRadius: '8px',
          boxShadow: '0 8px 32px rgba(0,0,0,0.5)'
        }
      }, [
        React.createElement('canvas', {
          key: 'snake-canvas',
          ref: canvasRef,
          style: {
            display: 'block',
            borderRadius: '6px',
            boxShadow: '0 4px 20px rgba(0,0,0,0.6)'
          }
        }),

        // Start / Pause Goal Card Overlay
        showLevelGoalCard ? React.createElement('div', {
          key: 'level-goal-card',
          style: {
            position: 'absolute',
            inset: 0,
            background: 'rgba(15, 23, 42, 0.85)',
            backdropFilter: 'blur(6px)',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 10,
            color: '#fff',
            padding: '20px'
          }
        }, [
          React.createElement('div', { key: 'goal-title', style: { fontSize: '24px', fontWeight: 800, color: currentLvl === 1000 ? '#ffd700' : '#38bdf8', marginBottom: '4px' } },
            currentLvl === 1000 ? '👑 LEVEL 1000: SOVEREIGN FINALE' : `LEVEL ${currentLvl}`
          ),
          (uiBoardHeader && uiBoardHeader.chapterName) ? React.createElement('div', { key: 'chapter-title', style: { fontSize: '13px', fontWeight: 700, color: '#f59e0b', textTransform: 'uppercase', letterSpacing: '0.08em', marginBottom: '8px' } }, `Chapter ${uiBoardHeader.chapter || 1}: ${uiBoardHeader.chapterName}`) : null,
          React.createElement('div', { key: 'goal-desc', style: { fontSize: '15px', color: '#cbd5e1', marginBottom: '16px' } },
            currentLvl === 1000 ? 'Coronation Quest: Collect the Sovereign Maharashtra Crest to complete the 1,000-level expedition!' : `Goal: Collect ${targetFruits} fruits to advance through Maharashtra`
          ),
          React.createElement('div', { key: 'goal-controls-tip', style: { fontSize: '13px', color: '#94a3b8', marginBottom: '24px' } }, 'Use Arrow Keys or WASD to navigate'),
          React.createElement('button', {
            key: 'start-btn',
            onClick: handleStartLevel,
            style: {
              background: 'linear-gradient(135deg, #0284c7, #0369a1)',
              border: '1px solid #38bdf8',
              color: '#fff',
              fontSize: '16px',
              fontWeight: 700,
              padding: '10px 32px',
              borderRadius: '8px',
              cursor: 'pointer',
              boxShadow: '0 0 16px rgba(56, 189, 248, 0.4)'
            }
          }, '▶ START SLITHERING')
        ]) : null,

        // Death / Crash Overlay
        deathState ? React.createElement('div', {
          key: 'death-card',
          style: {
            position: 'absolute',
            inset: 0,
            background: 'rgba(15, 23, 42, 0.9)',
            backdropFilter: 'blur(6px)',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 10,
            color: '#fff',
            padding: '20px'
          }
        }, [
          React.createElement('div', { key: 'death-icon', style: { fontSize: '36px', marginBottom: '8px' } }, '💥'),
          React.createElement('div', { key: 'death-title', style: { fontSize: '22px', fontWeight: 800, color: '#ef4444', marginBottom: '8px' } }, 'COLLISION DETECTED'),
          React.createElement('div', { key: 'death-desc', style: { fontSize: '14px', color: '#cbd5e1', marginBottom: '20px' } }, `Progress: ${fruitsEaten} / ${targetFruits} fruits collected`),
          React.createElement('button', {
            key: 'retry-btn',
            onClick: handleRestartLevel,
            style: {
              background: 'linear-gradient(135deg, #ef4444, #b91c1c)',
              border: '1px solid #f87171',
              color: '#fff',
              fontSize: '15px',
              fontWeight: 700,
              padding: '10px 28px',
              borderRadius: '8px',
              cursor: 'pointer',
              boxShadow: '0 0 16px rgba(239, 68, 68, 0.4)'
            }
          }, '🔄 TRY AGAIN')
        ]) : null,

        // Level Victory Celebration Toast
        levelCelebration ? React.createElement('div', {
          key: 'win-toast',
          style: {
            position: 'absolute',
            top: '20px',
            background: 'linear-gradient(135deg, rgba(16, 185, 129, 0.95), rgba(5, 150, 105, 0.95))',
            border: '1px solid #34d399',
            borderRadius: '8px',
            padding: '12px 24px',
            color: '#fff',
            display: 'flex',
            alignItems: 'center',
            gap: '12px',
            boxShadow: '0 8px 32px rgba(16, 185, 129, 0.5)',
            zIndex: 20
          }
        }, [
          React.createElement('span', { key: 'win-star', style: { fontSize: '24px' } }, '⭐'.repeat(levelCelebration.stars)),
          React.createElement('div', { key: 'win-text' }, [
            React.createElement('div', { key: 'win-title', style: { fontWeight: 800, fontSize: '15px' } }, `LEVEL ${levelCelebration.level} MASTERED!`),
            React.createElement('div', { key: 'win-sub', style: { fontSize: '12px', opacity: 0.9 } }, `+${levelCelebration.stars} Stars • Expanding Territory...`)
          ])
        ]) : null
      ]),

      // 3. CONSOLIDATED BOTTOM CONTROL RAIL (34px)
      React.createElement('div', {
        key: 'snake-bottom-rail',
        style: {
          width: '100%',
          maxWidth: '1280px',
          height: '34px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '0 16px',
          background: 'rgba(15, 23, 42, 0.75)',
          backdropFilter: 'blur(8px)',
          border: '1px solid rgba(56, 189, 248, 0.2)',
          borderRadius: '8px',
          margin: '6px 0 0 0',
          boxSizing: 'border-box',
          fontSize: '12px',
          color: '#94a3b8',
          flexShrink: 0
        }
      }, [
        React.createElement('div', { key: 'rail-left', style: { display: 'flex', alignItems: 'center', gap: '8px' } }, [
          React.createElement('button', {
            key: 'pause-btn',
            onClick: () => setIsPaused(prev => !prev),
            disabled: showLevelGoalCard || !!deathState,
            style: {
              background: isPaused ? 'rgba(56, 189, 248, 0.2)' : 'transparent',
              border: '1px solid rgba(56, 189, 248, 0.4)',
              color: '#38bdf8',
              padding: '2px 10px',
              borderRadius: '4px',
              cursor: 'pointer',
              fontWeight: 600
            }
          }, isPaused ? '▶ Resume' : '⏸ Pause'),
          React.createElement('button', {
            key: 'restart-btn',
            onClick: handleRestartLevel,
            style: {
              background: 'transparent',
              border: '1px solid rgba(148, 163, 184, 0.3)',
              color: '#94a3b8',
              padding: '2px 8px',
              borderRadius: '4px',
              cursor: 'pointer'
            }
          }, '🔄 Restart'),
          React.createElement('button', {
            key: 'mute-btn',
            onClick: handleToggleMute,
            style: {
              background: 'transparent',
              border: '1px solid rgba(148, 163, 184, 0.3)',
              color: isMuted ? '#f87171' : '#94a3b8',
              padding: '2px 8px',
              borderRadius: '4px',
              cursor: 'pointer'
            }
          }, isMuted ? '🔇 Muted' : '🔊 Sound')
        ]),

        React.createElement('div', { key: 'rail-center', style: { fontWeight: 600, color: '#f1f5f9' } },
          `🍎 Food Progress: ${fruitsEaten} / ${targetFruits}`
        ),

        React.createElement('div', { key: 'rail-right', style: { color: '#64748b' } },
          'Arrows / WASD to move • Space to pause'
        )
      ]),

      // Stats Modal Portal
      showStatsModal && renderModalPortal(
        React.createElement('div', {
          key: 'stats-modal-overlay',
          style: {
            position: 'fixed',
            inset: 0,
            background: 'rgba(0,0,0,0.7)',
            backdropFilter: 'blur(4px)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 9999
          }
        }, [
          React.createElement('div', {
            key: 'stats-modal-card',
            style: {
              background: '#0f172a',
              border: '1px solid rgba(56, 189, 248, 0.3)',
              borderRadius: '12px',
              padding: '24px',
              width: '380px',
              color: '#fff',
              boxShadow: '0 16px 48px rgba(0,0,0,0.6)'
            }
          }, [
            React.createElement('div', { key: 'modal-title', style: { fontSize: '18px', fontWeight: 800, color: '#38bdf8', marginBottom: '16px' } }, '🐍 Snake Career Stats'),
            React.createElement('div', { key: 'stats-grid', style: { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', fontSize: '13px', color: '#cbd5e1' } }, [
              React.createElement('div', { key: 'stat-lvl' }, `Current Level: ${currentLvl}`),
              React.createElement('div', { key: 'stat-stars' }, `Total Stars: ⭐ ${totalStars}`),
              React.createElement('div', { key: 'stat-completed' }, `Levels Solved: ${progression ? progression.completedCount : 0}`),
              React.createElement('div', { key: 'stat-fruits' }, `Fruits Eaten: ${progression ? progression.totalFruitsEaten : 0}`),
              React.createElement('div', { key: 'stat-len' }, `Longest Snake: ${progression ? progression.longestSnake : 3}`),
              React.createElement('div', { key: 'stat-streak' }, `Best Streak: 🔥 ${bestStreak}`),
              React.createElement('div', { key: 'stat-time', style: { gridColumn: 'span 2' } }, `Play Time: ⏱️ ${formatLifetimeTime(progression ? progression.totalPlayTimeSeconds : 0)}`)
            ]),
            React.createElement('button', {
              key: 'close-stats-btn',
              onClick: () => setShowStatsModal(false),
              style: {
                marginTop: '20px',
                width: '100%',
                background: 'rgba(56, 189, 248, 0.15)',
                border: '1px solid #38bdf8',
                color: '#38bdf8',
                padding: '8px',
                borderRadius: '6px',
                cursor: 'pointer',
                fontWeight: 700
              }
            }, 'Close')
          ])
        ])
      )
    ]);
  }

  return SnakeClassic;
}));
