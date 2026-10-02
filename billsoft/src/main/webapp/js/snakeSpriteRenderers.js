/**
 * snakeSpriteRenderers.js
 * Dedicated Procedural Vector Canvas & SVG Sprite Engine for Snake Classic (Take a Break).
 * 
 * Quality Principles:
 * 1. 🎨 100% Procedural Vector Geometry (0 KB external image assets, 100% offline).
 * 2. 🏛️ Authentic Cultural & Architectural Distinction (No generic recolored blocks/cubes).
 * 3. 🍱 Authentic Regional Produce & Delicacies (Recognizable fruits, sweets, agricultural items).
 * 4. 📐 Strict Visual-to-Cell Bounding (All sprites fit cleanly within cell size [cs x cs]).
 * 5. ⚡ 60 FPS Canvas Performance (Lightweight path drawing with offscreen-cached materials).
 */

(function (root, factory) {
  if (typeof define === 'function' && define.amd) {
    define([], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  } else {
    root.SnakeSpriteRenderers = factory();
  }
}(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  // Helper: create smooth gradient
  function createLinear(ctx, x1, y1, x2, y2, stops) {
    const grad = ctx.createLinearGradient(x1, y1, x2, y2);
    for (let i = 0; i < stops.length; i++) {
      grad.addColorStop(stops[i][0], stops[i][1]);
    }
    return grad;
  }

  // Helper: create radial glow
  function createRadial(ctx, cx, cy, r1, r2, stops) {
    const grad = ctx.createRadialGradient(cx, cy, r1, cx, cy, r2);
    for (let i = 0; i < stops.length; i++) {
      grad.addColorStop(stops[i][0], stops[i][1]);
    }
    return grad;
  }

  // =========================================================================
  // 1. TANGIBLE LOCATION OBSTACLE VECTOR RENDERERS
  // =========================================================================
  const obstacles = {
    // --- HILL FORT & MARATHA CITADEL OBSTACLES ---
    drawBastion(ctx, x, y, size, colors) {
      const pad = size * 0.05;
      const w = size - pad * 2;
      const h = size - pad * 2;
      const bx = x + pad;
      const by = y + pad;
      const col = (colors && colors.wallBase) || '#334155';
      const detail = (colors && colors.wallDetail) || '#64748b';
      const highlight = (colors && colors.wallHighlight) || '#f59e0b';

      // Drop shadow
      ctx.fillStyle = 'rgba(0,0,0,0.35)';
      ctx.beginPath();
      ctx.ellipse(bx + w / 2, by + h * 0.9, w * 0.45, h * 0.15, 0, 0, Math.PI * 2);
      ctx.fill();

      // Semi-circular curved bastion tower
      const towerGrad = createLinear(ctx, bx, by, bx + w, by, [
        [0, '#0f172a'],
        [0.35, col],
        [0.7, detail],
        [1.0, '#0f172a']
      ]);
      ctx.fillStyle = towerGrad;
      ctx.beginPath();
      ctx.roundRect(bx, by + h * 0.25, w, h * 0.75, [0, 0, 6, 6]);
      ctx.fill();

      // Curved stone masonry courses
      ctx.strokeStyle = 'rgba(0,0,0,0.45)';
      ctx.lineWidth = 1;
      for (let r = 0.45; r <= 0.85; r += 0.2) {
        ctx.beginPath();
        ctx.arc(bx + w / 2, by - h * 0.3, w * r, 0.4, Math.PI - 0.4);
        ctx.stroke();
      }

      // Crenellated Battlements & Embrasures
      ctx.fillStyle = detail;
      const crenW = w / 5;
      for (let i = 0; i < 5; i += 2) {
        ctx.fillRect(bx + i * crenW, by + h * 0.1, crenW, h * 0.2);
      }
      ctx.fillStyle = '#0f172a';
      ctx.fillRect(bx + crenW, by + h * 0.18, crenW, h * 0.12);
      ctx.fillRect(bx + crenW * 3, by + h * 0.18, crenW, h * 0.12);

      // Central Arrow Slit / Gunport
      ctx.fillStyle = '#05070a';
      ctx.beginPath();
      ctx.roundRect(bx + w * 0.44, by + h * 0.45, w * 0.12, h * 0.3, 2);
      ctx.fill();

      // Saffron Pennant Flag on Top
      ctx.strokeStyle = '#d97706';
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.moveTo(bx + w * 0.5, by + h * 0.1);
      ctx.lineTo(bx + w * 0.5, by - h * 0.05);
      ctx.stroke();
      ctx.fillStyle = highlight || '#f59e0b';
      ctx.beginPath();
      ctx.moveTo(bx + w * 0.5, by - h * 0.05);
      ctx.lineTo(bx + w * 0.85, by + h * 0.02);
      ctx.lineTo(bx + w * 0.5, by + h * 0.08);
      ctx.closePath();
      ctx.fill();
    },

    drawCannon(ctx, x, y, size, colors) {
      const bx = x + size * 0.1;
      const by = y + size * 0.15;
      const w = size * 0.8;
      const h = size * 0.7;

      // Heavy timber carriage (Teak wood)
      ctx.fillStyle = '#451a03';
      ctx.beginPath();
      ctx.roundRect(bx + w * 0.1, by + h * 0.45, w * 0.8, h * 0.45, 4);
      ctx.fill();

      // Timber grain line
      ctx.strokeStyle = '#78350f';
      ctx.lineWidth = 1;
      ctx.strokeRect(bx + w * 0.12, by + h * 0.48, w * 0.76, h * 0.38);

      // Iron wheels
      ctx.fillStyle = '#1e293b';
      ctx.beginPath();
      ctx.arc(bx + w * 0.25, by + h * 0.75, h * 0.2, 0, Math.PI * 2);
      ctx.arc(bx + w * 0.75, by + h * 0.75, h * 0.2, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = '#94a3b8';
      ctx.beginPath();
      ctx.arc(bx + w * 0.25, by + h * 0.75, h * 0.06, 0, Math.PI * 2);
      ctx.arc(bx + w * 0.75, by + h * 0.75, h * 0.06, 0, Math.PI * 2);
      ctx.fill();

      // Cast-Iron Cannon Barrel (Swivel mount)
      const barrelGrad = createLinear(ctx, bx + w * 0.1, by + h * 0.1, bx + w * 0.9, by + h * 0.5, [
        [0, '#0f172a'],
        [0.4, '#475569'],
        [0.7, '#94a3b8'],
        [1.0, '#0f172a']
      ]);
      ctx.fillStyle = barrelGrad;
      ctx.beginPath();
      ctx.moveTo(bx + w * 0.1, by + h * 0.4);
      ctx.lineTo(bx + w * 0.9, by + h * 0.15);
      ctx.lineTo(bx + w * 0.92, by + h * 0.32);
      ctx.lineTo(bx + w * 0.15, by + h * 0.55);
      ctx.closePath();
      ctx.fill();

      // Cannon Muzzle Ring
      ctx.fillStyle = '#0f172a';
      ctx.beginPath();
      ctx.ellipse(bx + w * 0.91, by + h * 0.235, w * 0.04, h * 0.1, 0, 0, Math.PI * 2);
      ctx.fill();
    },

    drawBasaltBoulder(ctx, x, y, size, colors) {
      const bx = x + size * 0.1;
      const by = y + size * 0.1;
      const w = size * 0.8;
      const h = size * 0.8;

      // Cast shadow
      ctx.fillStyle = 'rgba(0,0,0,0.3)';
      ctx.beginPath();
      ctx.ellipse(bx + w * 0.5, by + h * 0.85, w * 0.45, h * 0.15, 0, 0, Math.PI * 2);
      ctx.fill();

      // Angular volcanic basalt rock facets
      const rockGrad = createLinear(ctx, bx, by, bx + w, by + h, [
        [0, '#334155'],
        [0.4, '#1e293b'],
        [1.0, '#090d16']
      ]);
      ctx.fillStyle = rockGrad;
      ctx.beginPath();
      ctx.moveTo(bx + w * 0.2, by + h * 0.1);
      ctx.lineTo(bx + w * 0.75, by + h * 0.05);
      ctx.lineTo(bx + w * 0.95, by + h * 0.45);
      ctx.lineTo(bx + w * 0.85, by + h * 0.9);
      ctx.lineTo(bx + w * 0.15, by + h * 0.85);
      ctx.lineTo(bx + w * 0.05, by + h * 0.4);
      ctx.closePath();
      ctx.fill();

      // Rock cleavage fractures
      ctx.strokeStyle = 'rgba(0,0,0,0.6)';
      ctx.lineWidth = 1.2;
      ctx.beginPath();
      ctx.moveTo(bx + w * 0.2, by + h * 0.1);
      ctx.lineTo(bx + w * 0.45, by + h * 0.5);
      ctx.lineTo(bx + w * 0.85, by + h * 0.9);
      ctx.moveTo(bx + w * 0.45, by + h * 0.5);
      ctx.lineTo(bx + w * 0.05, by + h * 0.4);
      ctx.stroke();

      // Verdant Sahyadri moss patches
      ctx.fillStyle = '#10b981';
      ctx.beginPath();
      ctx.ellipse(bx + w * 0.3, by + h * 0.2, w * 0.12, h * 0.06, 0.3, 0, Math.PI * 2);
      ctx.ellipse(bx + w * 0.65, by + h * 0.3, w * 0.15, h * 0.08, -0.2, 0, Math.PI * 2);
      ctx.fill();
    },

    drawFortGate(ctx, x, y, size, colors) {
      const bx = x + size * 0.08;
      const by = y + size * 0.08;
      const w = size * 0.84;
      const h = size * 0.84;

      // Outer stone archway perimeter
      ctx.fillStyle = '#1e293b';
      ctx.beginPath();
      ctx.roundRect(bx, by, w, h, [8, 8, 2, 2]);
      ctx.fill();

      // Arched gateway portal opening
      ctx.fillStyle = '#090d16';
      ctx.beginPath();
      ctx.roundRect(bx + w * 0.12, by + h * 0.15, w * 0.76, h * 0.85, [w * 0.38, w * 0.38, 0, 0]);
      ctx.fill();

      // Heavy timber door leaves (Teak planks)
      ctx.fillStyle = '#451a03';
      ctx.beginPath();
      ctx.roundRect(bx + w * 0.16, by + h * 0.2, w * 0.32, h * 0.78, [w * 0.16, 0, 0, 0]);
      ctx.roundRect(bx + w * 0.52, by + h * 0.2, w * 0.32, h * 0.78, [0, w * 0.16, 0, 0]);
      ctx.fill();

      // Iron defensive spikes (Anti-elephant studs)
      ctx.fillStyle = '#ffd700';
      for (let r = 0.35; r <= 0.85; r += 0.2) {
        ctx.beginPath();
        ctx.arc(bx + w * 0.28, by + h * r, 2, 0, Math.PI * 2);
        ctx.arc(bx + w * 0.36, by + h * r, 2, 0, Math.PI * 2);
        ctx.arc(bx + w * 0.64, by + h * r, 2, 0, Math.PI * 2);
        ctx.arc(bx + w * 0.72, by + h * r, 2, 0, Math.PI * 2);
        ctx.fill();
      }
    },

    // --- COASTAL SEA FORT OBSTACLES ---
    drawSeaRampart(ctx, x, y, size, colors) {
      const bx = x + size * 0.05;
      const by = y + size * 0.1;
      const w = size * 0.9;
      const h = size * 0.8;

      // Weathered Laterite red-brown stone blocks
      const latGrad = createLinear(ctx, bx, by, bx, by + h, [
        [0, '#9a3412'],
        [0.5, '#7c2d12'],
        [1.0, '#431407']
      ]);
      ctx.fillStyle = latGrad;
      ctx.beginPath();
      ctx.roundRect(bx, by + h * 0.2, w, h * 0.8, 4);
      ctx.fill();

      // Mortar seams
      ctx.strokeStyle = 'rgba(0,0,0,0.5)';
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.moveTo(bx, by + h * 0.5); ctx.lineTo(bx + w, by + h * 0.5);
      ctx.moveTo(bx, by + h * 0.75); ctx.lineTo(bx + w, by + h * 0.75);
      ctx.moveTo(bx + w * 0.45, by + h * 0.2); ctx.lineTo(bx + w * 0.45, by + h * 0.5);
      ctx.moveTo(bx + w * 0.75, by + h * 0.5); ctx.lineTo(bx + w * 0.75, by + h * 0.75);
      ctx.moveTo(bx + w * 0.25, by + h * 0.75); ctx.lineTo(bx + w * 0.25, by + h);
      ctx.stroke();

      // Top parapet crenellations
      ctx.fillStyle = '#b45309';
      ctx.fillRect(bx, by + h * 0.05, w * 0.28, h * 0.18);
      ctx.fillRect(bx + w * 0.36, by + h * 0.05, w * 0.28, h * 0.18);
      ctx.fillRect(bx + w * 0.72, by + h * 0.05, w * 0.28, h * 0.18);

      // Sea wave splash highlights at base
      ctx.fillStyle = 'rgba(56, 189, 248, 0.45)';
      ctx.beginPath();
      ctx.arc(bx + w * 0.2, by + h * 0.95, w * 0.15, Math.PI, 0);
      ctx.arc(bx + w * 0.6, by + h * 0.95, w * 0.18, Math.PI, 0);
      ctx.arc(bx + w * 0.9, by + h * 0.95, w * 0.12, Math.PI, 0);
      ctx.fill();
    },

    drawCoralReef(ctx, x, y, size, colors) {
      const bx = x + size * 0.1;
      const by = y + size * 0.1;
      const w = size * 0.8;
      const h = size * 0.8;

      ctx.fillStyle = createLinear(ctx, bx, by, bx + w, by + h, [
        [0, '#0891b2'],
        [0.5, '#0e7490'],
        [1.0, '#155e75']
      ]);
      ctx.beginPath();
      ctx.moveTo(bx + w * 0.1, by + h * 0.9);
      ctx.lineTo(bx + w * 0.25, by + h * 0.3);
      ctx.lineTo(bx + w * 0.4, by + h * 0.6);
      ctx.lineTo(bx + w * 0.6, by + h * 0.1);
      ctx.lineTo(bx + w * 0.75, by + h * 0.45);
      ctx.lineTo(bx + w * 0.9, by + h * 0.9);
      ctx.closePath();
      ctx.fill();

      // Sharp coral crests
      ctx.strokeStyle = '#67e8f9';
      ctx.lineWidth = 1.5;
      ctx.stroke();
    },

    drawMachvaBoat(ctx, x, y, size, colors) {
      const bx = x + size * 0.08;
      const by = y + size * 0.2;
      const w = size * 0.84;
      const h = size * 0.6;

      // Wooden Boat Hull
      ctx.fillStyle = '#78350f';
      ctx.beginPath();
      ctx.moveTo(bx, by + h * 0.3);
      ctx.lineTo(bx + w * 0.85, by + h * 0.3);
      ctx.lineTo(bx + w * 0.75, by + h * 0.85);
      ctx.lineTo(bx + w * 0.15, by + h * 0.85);
      ctx.closePath();
      ctx.fill();

      // Mast & Rolled Sail
      ctx.strokeStyle = '#fef08a';
      ctx.lineWidth = 2;
      ctx.beginPath();
      ctx.moveTo(bx + w * 0.45, by + h * 0.3);
      ctx.lineTo(bx + w * 0.45, by - h * 0.1);
      ctx.stroke();

      ctx.fillStyle = '#f8fafc';
      ctx.beginPath();
      ctx.moveTo(bx + w * 0.45, by - h * 0.08);
      ctx.lineTo(bx + w * 0.75, by + h * 0.15);
      ctx.lineTo(bx + w * 0.45, by + h * 0.25);
      ctx.closePath();
      ctx.fill();
    },

    // --- WADA & COURTYARD OBSTACLES ---
    drawDilliDarwaza(ctx, x, y, size, colors) {
      const bx = x + size * 0.08;
      const by = y + size * 0.05;
      const w = size * 0.84;
      const h = size * 0.9;

      // Outer stone arch framing
      ctx.fillStyle = '#78350f';
      ctx.beginPath();
      ctx.roundRect(bx, by, w, h, [6, 6, 0, 0]);
      ctx.fill();

      // Inner cusped Maratha archway
      ctx.fillStyle = '#1c1917';
      ctx.beginPath();
      ctx.roundRect(bx + w * 0.15, by + h * 0.18, w * 0.7, h * 0.82, [w * 0.35, w * 0.35, 0, 0]);
      ctx.fill();

      // Brass door knockers & studs
      ctx.fillStyle = '#fbbf24';
      ctx.beginPath();
      ctx.arc(bx + w * 0.35, by + h * 0.55, 3, 0, Math.PI * 2);
      ctx.arc(bx + w * 0.65, by + h * 0.55, 3, 0, Math.PI * 2);
      ctx.fill();

      // Terracotta roof eave above
      ctx.fillStyle = '#ea580c';
      ctx.beginPath();
      ctx.moveTo(bx - w * 0.05, by + h * 0.1);
      ctx.lineTo(bx + w * 1.05, by + h * 0.1);
      ctx.lineTo(bx + w * 0.95, by);
      ctx.lineTo(bx + w * 0.05, by);
      ctx.closePath();
      ctx.fill();
    },

    drawTeakColumn(ctx, x, y, size, colors) {
      const bx = x + size * 0.2;
      const by = y + size * 0.05;
      const w = size * 0.6;
      const h = size * 0.9;

      // Stone plinth base
      ctx.fillStyle = '#475569';
      ctx.fillRect(bx, by + h * 0.8, w, h * 0.2);

      // Fluted dark teak timber pillar shaft
      const teakGrad = createLinear(ctx, bx + w * 0.15, by, bx + w * 0.85, by, [
        [0, '#291807'],
        [0.5, '#78350f'],
        [1.0, '#1c0f04']
      ]);
      ctx.fillStyle = teakGrad;
      ctx.fillRect(bx + w * 0.15, by + h * 0.18, w * 0.7, h * 0.64);

      // Carved lotus capital on top
      ctx.fillStyle = '#f59e0b';
      ctx.beginPath();
      ctx.moveTo(bx, by + h * 0.18);
      ctx.lineTo(bx + w, by + h * 0.18);
      ctx.lineTo(bx + w * 0.8, by + h * 0.04);
      ctx.lineTo(bx + w * 0.2, by + h * 0.04);
      ctx.closePath();
      ctx.fill();
    },

    drawWadaFountain(ctx, x, y, size, colors) {
      const bx = x + size * 0.1;
      const by = y + size * 0.1;
      const w = size * 0.8;
      const h = size * 0.8;

      // Octagonal stone basin
      ctx.fillStyle = '#334155';
      ctx.beginPath();
      ctx.roundRect(bx, by + h * 0.2, w, h * 0.7, 8);
      ctx.fill();

      // Water basin interior
      ctx.fillStyle = '#0284c7';
      ctx.beginPath();
      ctx.ellipse(bx + w * 0.5, by + h * 0.55, w * 0.38, h * 0.25, 0, 0, Math.PI * 2);
      ctx.fill();

      // Center stone lotus spout with water glints
      ctx.fillStyle = '#f8fafc';
      ctx.beginPath();
      ctx.arc(bx + w * 0.5, by + h * 0.5, w * 0.08, 0, Math.PI * 2);
      ctx.fill();
    },

    // --- ROCK-CUT CAVE TEMPLE OBSTACLES ---
    drawChaityaArch(ctx, x, y, size, colors) {
      const bx = x + size * 0.08;
      const by = y + size * 0.05;
      const w = size * 0.84;
      const h = size * 0.9;

      // Outer basalt rock face
      ctx.fillStyle = '#1e1b4b';
      ctx.beginPath();
      ctx.roundRect(bx, by, w, h, [w * 0.45, w * 0.45, 4, 4]);
      ctx.fill();

      // Horseshoe sunburst arch ribs
      ctx.strokeStyle = '#8b5cf6';
      ctx.lineWidth = 2.5;
      ctx.beginPath();
      ctx.arc(bx + w * 0.5, by + h * 0.45, w * 0.38, Math.PI, 0);
      ctx.lineTo(bx + w * 0.88, by + h * 0.9);
      ctx.moveTo(bx + w * 0.12, by + h * 0.45);
      ctx.lineTo(bx + w * 0.12, by + h * 0.9);
      ctx.stroke();

      // Inner cave darkness cavity
      ctx.fillStyle = '#05030a';
      ctx.beginPath();
      ctx.arc(bx + w * 0.5, by + h * 0.45, w * 0.26, Math.PI, 0);
      ctx.lineTo(bx + w * 0.76, by + h * 0.9);
      ctx.lineTo(bx + w * 0.24, by + h * 0.9);
      ctx.closePath();
      ctx.fill();
    },

    drawBasaltStupa(ctx, x, y, size, colors) {
      const bx = x + size * 0.12;
      const by = y + size * 0.1;
      const w = size * 0.76;
      const h = size * 0.8;

      // Cylindrical drum base
      ctx.fillStyle = '#334155';
      ctx.fillRect(bx, by + h * 0.6, w, h * 0.35);

      // Hemispherical dome (Anda)
      const domeGrad = createLinear(ctx, bx, by, bx + w, by + h, [
        [0, '#64748b'],
        [0.5, '#334155'],
        [1.0, '#0f172a']
      ]);
      ctx.fillStyle = domeGrad;
      ctx.beginPath();
      ctx.arc(bx + w * 0.5, by + h * 0.6, w * 0.45, Math.PI, 0);
      ctx.fill();

      // Harmika & Stone Umbrella Finial
      ctx.fillStyle = '#fbbf24';
      ctx.fillRect(bx + w * 0.4, by + h * 0.08, w * 0.2, h * 0.1);
      ctx.beginPath();
      ctx.arc(bx + w * 0.5, by + h * 0.06, w * 0.18, Math.PI, 0);
      ctx.fill();
    },

    drawCavePillar(ctx, x, y, size, colors) {
      const bx = x + size * 0.2;
      const by = y + size * 0.05;
      const w = size * 0.6;
      const h = size * 0.9;

      ctx.fillStyle = '#1e293b';
      ctx.fillRect(bx, by, w, h);

      // Monolithic vertical fluting & floral band
      ctx.fillStyle = '#475569';
      ctx.fillRect(bx + w * 0.2, by + h * 0.1, w * 0.6, h * 0.8);

      ctx.fillStyle = '#a855f7';
      ctx.fillRect(bx + w * 0.1, by + h * 0.45, w * 0.8, h * 0.1);
    },

    // --- RIVER GHAT & PILGRIMAGE TEMPLE OBSTACLES ---
    drawDeepstambha(ctx, x, y, size, colors) {
      const bx = x + size * 0.2;
      const by = y + size * 0.05;
      const w = size * 0.6;
      const h = size * 0.9;

      // Octagonal basalt stone lamp tower shaft
      ctx.fillStyle = '#1e293b';
      ctx.fillRect(bx + w * 0.25, by + h * 0.1, w * 0.5, h * 0.85);

      // Protruding stone oil lamp brackets
      ctx.fillStyle = '#d97706';
      for (let r = 0.25; r <= 0.85; r += 0.15) {
        ctx.fillRect(bx, by + h * r, w, h * 0.04);
        // Golden diya flame glow on ends
        ctx.fillStyle = '#fde047';
        ctx.beginPath();
        ctx.arc(bx + 2, by + h * r - 2, 2, 0, Math.PI * 2);
        ctx.arc(bx + w - 2, by + h * r - 2, 2, 0, Math.PI * 2);
        ctx.fill();
        ctx.fillStyle = '#d97706';
      }
    },

    drawGhatSteps(ctx, x, y, size, colors) {
      const bx = x + size * 0.05;
      const by = y + size * 0.1;
      const w = size * 0.9;
      const h = size * 0.8;

      // Stepped stone river landings
      const stepH = h / 4;
      const stoneColors = ['#1e293b', '#334155', '#475569', '#0284c7'];
      for (let i = 0; i < 4; i++) {
        ctx.fillStyle = stoneColors[i];
        ctx.fillRect(bx + i * (w * 0.08), by + i * stepH, w - i * (w * 0.08), stepH);
      }
    },

    drawTempleShikhara(ctx, x, y, size, colors) {
      const bx = x + size * 0.15;
      const by = y + size * 0.05;
      const w = size * 0.7;
      const h = size * 0.9;

      // Pyramidal stepped Shikhara spire
      ctx.fillStyle = '#b45309';
      ctx.beginPath();
      ctx.moveTo(bx + w * 0.5, by + h * 0.1);
      ctx.lineTo(bx + w, by + h * 0.85);
      ctx.lineTo(bx, by + h * 0.85);
      ctx.closePath();
      ctx.fill();

      // Kalash and Saffron Dhwaja flag
      ctx.fillStyle = '#ffd700';
      ctx.beginPath();
      ctx.arc(bx + w * 0.5, by + h * 0.08, w * 0.1, 0, Math.PI * 2);
      ctx.fill();

      ctx.fillStyle = '#ea580c';
      ctx.beginPath();
      ctx.moveTo(bx + w * 0.5, by + h * 0.06);
      ctx.lineTo(bx + w * 0.85, by + h * 0.02);
      ctx.lineTo(bx + w * 0.5, by);
      ctx.closePath();
      ctx.fill();
    }
  };

  // =========================================================================
  // 2. AUTHENTIC REGIONAL FOOD / DELICACY VECTOR RENDERERS
  // =========================================================================
  const foods = {
    drawStrawberry(ctx, x, y, size) {
      const cx = x + size / 2;
      const cy = y + size / 2;
      const r = size * 0.38;

      // Crimson Berry Heart-Cone Body
      const berryGrad = createRadial(ctx, cx - r * 0.2, cy - r * 0.2, 0, r * 1.2, [
        [0, '#ff4d6d'],
        [0.4, '#e63946'],
        [0.85, '#9d0208'],
        [1.0, '#590d22']
      ]);
      ctx.fillStyle = berryGrad;
      ctx.beginPath();
      ctx.moveTo(cx, cy - r * 0.6);
      ctx.bezierCurveTo(cx + r * 1.1, cy - r * 0.7, cx + r * 1.1, cy + r * 0.3, cx, cy + r * 1.05);
      ctx.bezierCurveTo(cx - r * 1.1, cy + r * 0.3, cx - r * 1.1, cy - r * 0.7, cx, cy - r * 0.6);
      ctx.fill();

      // Golden Achene Seed Flecks
      ctx.fillStyle = '#ffea00';
      const seeds = [
        [-0.3, -0.2], [0.3, -0.2], [0, 0],
        [-0.45, 0.15], [0.45, 0.15], [-0.2, 0.45],
        [0.2, 0.45], [0, 0.75]
      ];
      for (let s of seeds) {
        ctx.beginPath();
        ctx.ellipse(cx + s[0] * r * 0.7, cy + s[1] * r * 0.7, 1.4, 0.8, 0.3, 0, Math.PI * 2);
        ctx.fill();
      }

      // Green Sepal Calyx Crown & Stem
      ctx.fillStyle = '#2d6a4f';
      ctx.beginPath();
      ctx.moveTo(cx, cy - r * 0.6);
      ctx.lineTo(cx - r * 0.65, cy - r * 0.95);
      ctx.lineTo(cx - r * 0.2, cy - r * 0.65);
      ctx.lineTo(cx, cy - r * 1.1);
      ctx.lineTo(cx + r * 0.2, cy - r * 0.65);
      ctx.lineTo(cx + r * 0.65, cy - r * 0.95);
      ctx.closePath();
      ctx.fill();
    },

    drawKandiPeda(ctx, x, y, size) {
      const cx = x + size / 2;
      const cy = y + size / 2;
      const r = size * 0.36;

      // Caramelized Golden-Brown Mawa Disc
      const pedaGrad = createRadial(ctx, cx - r * 0.25, cy - r * 0.25, 0, r * 1.1, [
        [0, '#fef08a'],
        [0.35, '#d97706'],
        [0.85, '#92400e'],
        [1.0, '#451a03']
      ]);
      ctx.fillStyle = pedaGrad;
      ctx.beginPath();
      ctx.ellipse(cx, cy, r * 1.1, r * 0.85, 0, 0, Math.PI * 2);
      ctx.fill();

      // Top Thumb Depression & Crushed Pistachio Fleck
      ctx.fillStyle = '#78350f';
      ctx.beginPath();
      ctx.arc(cx, cy, r * 0.28, 0, Math.PI * 2);
      ctx.fill();

      ctx.fillStyle = '#10b981';
      ctx.beginPath();
      ctx.ellipse(cx - 2, cy - 1, 3, 1.5, 0.4, 0, Math.PI * 2);
      ctx.ellipse(cx + 2, cy + 1, 2.5, 1.2, -0.4, 0, Math.PI * 2);
      ctx.fill();
    },

    drawHoneycomb(ctx, x, y, size) {
      const cx = x + size / 2;
      const cy = y + size / 2;
      const r = size * 0.35;

      // Golden Amber Hexagonal Wax Lattice
      ctx.fillStyle = '#fbbf24';
      ctx.beginPath();
      for (let i = 0; i < 6; i++) {
        const a = (i * Math.PI) / 3;
        const hx = cx + r * Math.cos(a);
        const hy = cy + r * Math.sin(a);
        if (i === 0) ctx.moveTo(hx, hy);
        else ctx.lineTo(hx, hy);
      }
      ctx.closePath();
      ctx.fill();

      // Dripping nectar drop
      ctx.fillStyle = '#f59e0b';
      ctx.beginPath();
      ctx.arc(cx, cy + r * 0.4, r * 0.25, 0, Math.PI * 2);
      ctx.fill();
    },

    drawAlphonsoMango(ctx, x, y, size) {
      const cx = x + size / 2;
      const cy = y + size / 2;
      const r = size * 0.38;

      // Saffron-Golden Mango with Red-Orange Blush
      const mangoGrad = createRadial(ctx, cx - r * 0.3, cy - r * 0.3, 0, r * 1.2, [
        [0, '#fef08a'],
        [0.4, '#f59e0b'],
        [0.85, '#ea580c'],
        [1.0, '#b91c1c']
      ]);
      ctx.fillStyle = mangoGrad;
      ctx.beginPath();
      ctx.moveTo(cx, cy - r * 0.7);
      ctx.bezierCurveTo(cx + r * 1.1, cy - r * 0.4, cx + r * 1.0, cy + r * 0.8, cx - r * 0.1, cy + r * 0.95);
      ctx.bezierCurveTo(cx - r * 0.9, cy + r * 0.9, cx - r * 1.0, cy - r * 0.1, cx, cy - r * 0.7);
      ctx.fill();

      // Green Stem Leaf
      ctx.fillStyle = '#15803d';
      ctx.beginPath();
      ctx.ellipse(cx - r * 0.2, cy - r * 0.85, r * 0.4, r * 0.15, -0.6, 0, Math.PI * 2);
      ctx.fill();
    },

    drawSolkadhi(ctx, x, y, size) {
      const cx = x + size / 2;
      const cy = y + size / 2;
      const r = size * 0.35;

      // Ruby-Pink Kokum Drink Glass
      ctx.fillStyle = '#ec4899';
      ctx.beginPath();
      ctx.roundRect(cx - r * 0.6, cy - r * 0.8, r * 1.2, r * 1.6, [2, 2, 8, 8]);
      ctx.fill();

      // Mint Leaf Garnish
      ctx.fillStyle = '#10b981';
      ctx.beginPath();
      ctx.arc(cx, cy - r * 0.7, 3, 0, Math.PI * 2);
      ctx.fill();
    },

    drawTenderCoconut(ctx, x, y, size) {
      const cx = x + size / 2;
      const cy = y + size / 2;
      const r = size * 0.38;

      ctx.fillStyle = '#15803d';
      ctx.beginPath();
      ctx.arc(cx, cy, r, 0, Math.PI * 2);
      ctx.fill();

      ctx.fillStyle = '#f8fafc';
      ctx.beginPath();
      ctx.ellipse(cx, cy - r * 0.3, r * 0.4, r * 0.2, 0, 0, Math.PI * 2);
      ctx.fill();
    },

    drawBakarwadi(ctx, x, y, size) {
      const cx = x + size / 2;
      const cy = y + size / 2;
      const r = size * 0.36;

      ctx.fillStyle = '#d97706';
      ctx.beginPath();
      ctx.arc(cx, cy, r, 0, Math.PI * 2);
      ctx.fill();

      // Spiral Spiced Swirl
      ctx.strokeStyle = '#78350f';
      ctx.lineWidth = 2;
      ctx.beginPath();
      ctx.arc(cx, cy, r * 0.6, 0, Math.PI * 1.5);
      ctx.stroke();
    },

    drawModak(ctx, x, y, size) {
      const cx = x + size / 2;
      const cy = y + size / 2;
      const r = size * 0.38;

      // Steamed Rice Flour Dumpling (Ukadiche Modak)
      const modakGrad = createLinear(ctx, cx - r, cy, cx + r, cy, [
        [0, '#e2e8f0'],
        [0.5, '#ffffff'],
        [1.0, '#cbd5e1']
      ]);
      ctx.fillStyle = modakGrad;
      ctx.beginPath();
      ctx.moveTo(cx, cy - r * 0.95);
      ctx.bezierCurveTo(cx + r * 1.1, cy + r * 0.2, cx + r * 0.7, cy + r * 0.9, cx, cy + r * 0.9);
      ctx.bezierCurveTo(cx - r * 0.7, cy + r * 0.9, cx - r * 1.1, cy + r * 0.2, cx, cy - r * 0.95);
      ctx.fill();

      // Pleated vertical grooves
      ctx.strokeStyle = 'rgba(0,0,0,0.12)';
      ctx.lineWidth = 1;
      for (let ox of [-0.4, -0.15, 0.15, 0.4]) {
        ctx.beginPath();
        ctx.moveTo(cx, cy - r * 0.9);
        ctx.quadraticCurveTo(cx + ox * r, cy + r * 0.2, cx + ox * r * 1.2, cy + r * 0.85);
        ctx.stroke();
      }

      // Saffron Strand Droplet on Peak
      ctx.fillStyle = '#ea580c';
      ctx.beginPath();
      ctx.arc(cx, cy - r * 0.88, 2.5, 0, Math.PI * 2);
      ctx.fill();
    },

    drawCustardApple(ctx, x, y, size) {
      const cx = x + size / 2;
      const cy = y + size / 2;
      const r = size * 0.38;

      ctx.fillStyle = '#15803d';
      ctx.beginPath();
      ctx.arc(cx, cy, r, 0, Math.PI * 2);
      ctx.fill();

      // Knobby green carpel scales
      ctx.fillStyle = '#86efac';
      for (let a = 0; a < Math.PI * 2; a += Math.PI / 3) {
        ctx.beginPath();
        ctx.arc(cx + r * 0.5 * Math.cos(a), cy + r * 0.5 * Math.sin(a), r * 0.22, 0, Math.PI * 2);
        ctx.fill();
      }
    },

    drawGoldenBanana(ctx, x, y, size) {
      const cx = x + size / 2;
      const cy = y + size / 2;
      const r = size * 0.38;

      ctx.fillStyle = '#fde047';
      ctx.beginPath();
      ctx.moveTo(cx - r * 0.7, cy + r * 0.4);
      ctx.quadraticCurveTo(cx, cy - r * 0.8, cx + r * 0.7, cy + r * 0.4);
      ctx.quadraticCurveTo(cx, cy - r * 0.3, cx - r * 0.7, cy + r * 0.4);
      ctx.fill();
    },

    drawWaiGuava(ctx, x, y, size) {
      const cx = x + size / 2;
      const cy = y + size / 2;
      const r = size * 0.36;

      ctx.fillStyle = '#16a34a';
      ctx.beginPath();
      ctx.arc(cx, cy, r, 0, Math.PI * 2);
      ctx.fill();

      // Pink seeded core
      ctx.fillStyle = '#f43f5e';
      ctx.beginPath();
      ctx.arc(cx, cy, r * 0.65, 0, Math.PI * 2);
      ctx.fill();
    },

    drawPrasaadLaddu(ctx, x, y, size) {
      const cx = x + size / 2;
      const cy = y + size / 2;
      const r = size * 0.36;

      ctx.fillStyle = '#f59e0b';
      ctx.beginPath();
      ctx.arc(cx, cy, r, 0, Math.PI * 2);
      ctx.fill();

      ctx.fillStyle = '#fef08a';
      for (let i = 0; i < 6; i++) {
        const a = (i * Math.PI) / 3;
        ctx.beginPath();
        ctx.arc(cx + r * 0.4 * Math.cos(a), cy + r * 0.4 * Math.sin(a), 2, 0, Math.PI * 2);
        ctx.fill();
      }
    }
  };

  return {
    obstacles,
    foods
  };
}));
