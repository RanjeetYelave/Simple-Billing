/**
 * bounceRenderer.js
 * High-fidelity Nokia 7210 pixel-art canvas renderer for Nokia Bounce.
 * Supports authentic shaded red ball with rolling rotation and squash-and-stretch,
 * multi-theme pixel-art tiles, animated water, gold hoops, spring trampolines,
 * checkpoints, exit gate, particle bursts, and retro arcade HUD.
 */

(function (root, factory) {
  if (typeof define === 'function' && define.amd) {
    define([], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  } else {
    root.BounceRenderer = factory();
  }
}(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  // Theme palettes
  const THEMES = {
    classic_brick: {
      skyTop: '#38bdf8',
      skyBottom: '#bae6fd',
      hillFar: '#86efac',
      hillNear: '#4ade80',
      brickMain: '#b91c1c',
      brickMortar: '#fca5a5',
      brickShadow: '#7f1d1d',
      waterTop: 'rgba(56, 189, 248, 0.45)',
      waterBody: 'rgba(2, 132, 199, 0.65)'
    },
    cavern_basalt: {
      skyTop: '#090d16',
      skyBottom: '#1e293b',
      hillFar: '#334155',
      hillNear: '#1e293b',
      brickMain: '#334155',
      brickMortar: '#64748b',
      brickShadow: '#0f172a',
      waterTop: 'rgba(34, 211, 238, 0.45)',
      waterBody: 'rgba(8, 145, 178, 0.65)'
    },
    industrial_foundry: {
      skyTop: '#261305',
      skyBottom: '#451a03',
      hillFar: '#78350f',
      hillNear: '#451a03',
      brickMain: '#7c2d12',
      brickMortar: '#fdba74',
      brickShadow: '#431407',
      waterTop: 'rgba(251, 146, 60, 0.45)',
      waterBody: 'rgba(194, 65, 12, 0.65)'
    },
    mystic_ruins: {
      skyTop: '#1e1035',
      skyBottom: '#3b0764',
      hillFar: '#6b21a8',
      hillNear: '#4c1d95',
      brickMain: '#92400e',
      brickMortar: '#fde68a',
      brickShadow: '#78350f',
      waterTop: 'rgba(168, 85, 247, 0.45)',
      waterBody: 'rgba(109, 40, 217, 0.65)'
    }
  };

  /**
   * Main render function called each animation frame.
   */
  function render(ctx, level, player, camera, width, height, animTime = 0) {
    if (!ctx || !level || !player) return;

    const theme = THEMES[level.environment] || THEMES.classic_brick;

    ctx.save();
    // Enable pixel-art crisp rendering
    ctx.imageSmoothingEnabled = false;

    // 1. Draw Sky & Parallax Background
    renderBackground(ctx, theme, camera, width, height, level);

    // 2. Camera Transform for World Objects
    ctx.save();
    ctx.translate(-Math.floor(camera.x), -Math.floor(camera.y));

    // 3. Draw Water Under-layer
    renderWater(ctx, level, theme, animTime);

    // 4. Draw Platforms & Terrain Blocks
    renderPlatforms(ctx, level, theme);

    // 5. Draw Interactive Objects (Pumps, Trampolines, Checkpoints, Hoops, Exit)
    renderInteractive(ctx, level, animTime);

    // 6. Draw Hazards (Spikes)
    renderHazards(ctx, level);

    // 7. Draw Player Ball
    renderPlayer(ctx, player, animTime);

    ctx.restore();

    // 8. Draw Screen-Space HUD (Lives, Hoops, Score, Level)
    renderHud(ctx, level, player, width, height);

    ctx.restore();
  }

  function renderBackground(ctx, theme, camera, width, height, level) {
    // Gradient Sky
    const grad = ctx.createLinearGradient(0, 0, 0, height);
    grad.addColorStop(0, theme.skyTop);
    grad.addColorStop(1, theme.skyBottom);
    ctx.fillStyle = grad;
    ctx.fillRect(0, 0, width, height);

    // Distant Parallax Hills / Silhouettes
    const camX = camera.x || 0;
    ctx.fillStyle = theme.hillFar;
    ctx.beginPath();
    ctx.moveTo(0, height);
    for (let x = 0; x <= width; x += 40) {
      const worldX = x + camX * 0.2;
      const hillY = height - 120 + Math.sin(worldX * 0.005) * 40;
      ctx.lineTo(x, hillY);
    }
    ctx.lineTo(width, height);
    ctx.closePath();
    ctx.fill();

    ctx.fillStyle = theme.hillNear;
    ctx.beginPath();
    ctx.moveTo(0, height);
    for (let x = 0; x <= width; x += 30) {
      const worldX = x + camX * 0.4;
      const hillY = height - 70 + Math.sin(worldX * 0.008 + 1) * 30;
      ctx.lineTo(x, hillY);
    }
    ctx.lineTo(width, height);
    ctx.closePath();
    ctx.fill();
  }

  function renderPlatforms(ctx, level, theme) {
    const plats = level.platforms || [];
    for (let i = 0; i < plats.length; i++) {
      const p = plats[i];
      // Brick pattern fill
      ctx.fillStyle = theme.brickMain;
      ctx.fillRect(p.x, p.y, p.width, p.height);

      // Dark bottom/right edge
      ctx.fillStyle = theme.brickShadow;
      ctx.fillRect(p.x, p.y + p.height - 4, p.width, 4);
      ctx.fillRect(p.x + p.width - 4, p.y, 4, p.height);

      // Light top edge (grass or mortar)
      ctx.fillStyle = theme.brickMortar;
      ctx.fillRect(p.x, p.y, p.width, 3);

      // Brick mortar line details
      ctx.strokeStyle = theme.brickShadow;
      ctx.lineWidth = 1;
      const tileSize = 32;
      for (let y = p.y + 16; y < p.y + p.height; y += 16) {
        ctx.beginPath();
        ctx.moveTo(p.x, y);
        ctx.lineTo(p.x + p.width, y);
        ctx.stroke();
      }
      for (let x = p.x + 32; x < p.x + p.width; x += 32) {
        ctx.beginPath();
        ctx.moveTo(x, p.y);
        ctx.lineTo(x, p.y + p.height);
        ctx.stroke();
      }
    }
  }

  function renderWater(ctx, level, theme, animTime) {
    const bodies = level.waterBodies || [];
    for (let i = 0; i < bodies.length; i++) {
      const w = bodies[i];
      // Body
      ctx.fillStyle = theme.waterBody;
      ctx.fillRect(w.x, w.y, w.width, w.height);

      // Animated surface wave
      ctx.fillStyle = theme.waterTop;
      ctx.beginPath();
      ctx.moveTo(w.x, w.y);
      for (let x = 0; x <= w.width; x += 16) {
        const wave = Math.sin((w.x + x) * 0.08 + animTime * 4) * 4;
        ctx.lineTo(w.x + x, w.y + wave);
      }
      ctx.lineTo(w.x + w.width, w.y + 12);
      ctx.lineTo(w.x, w.y + 12);
      ctx.closePath();
      ctx.fill();
    }
  }

  function renderHazards(ctx, level) {
    const hazards = level.hazards || [];
    for (let i = 0; i < hazards.length; i++) {
      const h = hazards[i];
      if (h.type === 'spike') {
        const spikeW = h.width || 32;
        const spikeH = h.height || 24;
        const count = Math.max(1, Math.floor(spikeW / 16));
        const segW = spikeW / count;

        ctx.fillStyle = '#cbd5e1';
        ctx.strokeStyle = '#475569';
        ctx.lineWidth = 1;

        for (let s = 0; s < count; s++) {
          const sx = h.x + s * segW;
          ctx.beginPath();
          ctx.moveTo(sx, h.y);
          ctx.lineTo(sx + segW / 2, h.y - spikeH);
          ctx.lineTo(sx + segW, h.y);
          ctx.closePath();
          ctx.fill();
          ctx.stroke();

          // Gleaming tip
          ctx.fillStyle = '#ffffff';
          ctx.fillRect(sx + segW / 2 - 1, h.y - spikeH, 2, 4);
          ctx.fillStyle = '#cbd5e1';
        }
      }
    }
  }

  function renderInteractive(ctx, level, animTime) {
    // 1. Trampolines / Bounce Pads
    const tramps = level.trampolines || [];
    for (let i = 0; i < tramps.length; i++) {
      const t = tramps[i];
      ctx.fillStyle = '#f59e0b';
      ctx.fillRect(t.x, t.y, t.width || 50, 8);
      // Spring coils
      ctx.strokeStyle = '#ef4444';
      ctx.lineWidth = 2;
      ctx.beginPath();
      ctx.moveTo(t.x + 8, t.y + 8);
      ctx.lineTo(t.x + 14, t.y + 14);
      ctx.lineTo(t.x + 20, t.y + 8);
      ctx.lineTo(t.x + 26, t.y + 14);
      ctx.lineTo(t.x + 32, t.y + 8);
      ctx.stroke();
    }

    // 2. Pumps (Inflator / Deflator)
    const pumps = level.pumps || [];
    for (let i = 0; i < pumps.length; i++) {
      const p = pumps[i];
      const isBig = p.targetState === 'BIG';
      const color = isBig ? '#38bdf8' : '#eab308';

      // Base
      ctx.fillStyle = '#1e293b';
      ctx.fillRect(p.x - 12, p.y + 8, 24, 8);

      // Bellows Chamber
      ctx.fillStyle = color;
      const squeeze = Math.sin(animTime * 5) * 2;
      ctx.fillRect(p.x - 8, p.y - 8 + squeeze, 16, 16 - squeeze);

      // Indicator Icon
      ctx.fillStyle = '#ffffff';
      ctx.font = 'bold 10px monospace';
      ctx.textAlign = 'center';
      ctx.fillText(isBig ? '➕' : '➖', p.x, p.y + 4);
    }

    // 3. Hoops (Rings)
    const hoops = level.hoops || [];
    for (let i = 0; i < hoops.length; i++) {
      const h = hoops[i];
      const radius = h.radius || 24;

      ctx.save();
      ctx.translate(h.x, h.y);

      if (h.collected) {
        // Collected Ring: faint glowing green ring
        ctx.strokeStyle = 'rgba(74, 222, 128, 0.4)';
        ctx.lineWidth = 3;
        ctx.beginPath();
        ctx.ellipse(0, 0, radius, radius * 0.7, 0, 0, Math.PI * 2);
        ctx.stroke();
      } else {
        // Active Uncollected Ring: 3D Gold Hoop
        const bob = Math.sin(animTime * 3 + h.x) * 3;
        ctx.translate(0, bob);

        // Gold outer ring
        ctx.strokeStyle = '#f59e0b';
        ctx.lineWidth = 5;
        ctx.beginPath();
        ctx.ellipse(0, 0, radius, radius * 0.75, 0, 0, Math.PI * 2);
        ctx.stroke();

        // Inner highlight
        ctx.strokeStyle = '#fef08a';
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.ellipse(0, 0, radius - 2, (radius - 2) * 0.75, 0, 0, Math.PI * 2);
        ctx.stroke();
      }
      ctx.restore();
    }

    // 4. Checkpoints
    const cps = level.checkpoints || [];
    for (let i = 0; i < cps.length; i++) {
      const cp = cps[i];
      // Mast
      ctx.fillStyle = '#64748b';
      ctx.fillRect(cp.x - 2, cp.y - 36, 4, 36);

      // Flag
      ctx.fillStyle = cp.active ? '#22c55e' : '#ef4444';
      ctx.beginPath();
      const wave = Math.sin(animTime * 6 + cp.x) * 3;
      ctx.moveTo(cp.x + 2, cp.y - 36);
      ctx.lineTo(cp.x + 22 + wave, cp.y - 28);
      ctx.lineTo(cp.x + 2, cp.y - 20);
      ctx.closePath();
      ctx.fill();
    }

    // 5. Exit Gate
    if (level.exit) {
      const ex = level.exit;
      const isOpen = !!level.exitOpen;

      // Stone Arch
      ctx.fillStyle = '#334155';
      ctx.fillRect(ex.x, ex.y, ex.width, ex.height);

      if (isOpen) {
        // Glowing open portal beam
        const portalGrad = ctx.createLinearGradient(ex.x, ex.y, ex.x + ex.width, ex.y);
        portalGrad.addColorStop(0, '#f59e0b');
        portalGrad.addColorStop(0.5, '#fef08a');
        portalGrad.addColorStop(1, '#f59e0b');
        ctx.fillStyle = portalGrad;
        ctx.fillRect(ex.x + 6, ex.y + 6, ex.width - 12, ex.height - 6);

        // Radiant star in doorway
        ctx.fillStyle = '#ffffff';
        ctx.beginPath();
        ctx.arc(ex.x + ex.width / 2, ex.y + ex.height / 2, 8 + Math.sin(animTime * 8) * 2, 0, Math.PI * 2);
        ctx.fill();
      } else {
        // Closed iron/wood door with lock crystal
        ctx.fillStyle = '#1e293b';
        ctx.fillRect(ex.x + 6, ex.y + 6, ex.width - 12, ex.height - 6);

        // Red lock crystal
        ctx.fillStyle = '#ef4444';
        ctx.beginPath();
        ctx.arc(ex.x + ex.width / 2, ex.y + ex.height / 2, 6, 0, Math.PI * 2);
        ctx.fill();
      }
    }
  }

  function renderPlayer(ctx, player, animTime) {
    if (player.isDead) {
      // Pop death particles
      ctx.fillStyle = '#ef4444';
      const pCount = 8;
      for (let i = 0; i < pCount; i++) {
        const angle = (i / pCount) * Math.PI * 2;
        const d = (1.0 - player.deathTimer) * 45;
        const px = player.x + Math.cos(angle) * d;
        const py = player.y + Math.sin(angle) * d;
        ctx.beginPath();
        ctx.arc(px, py, 4, 0, Math.PI * 2);
        ctx.fill();
      }
      return;
    }

    // Invulnerability flashing
    if (player.invulnerableTimer > 0 && Math.floor(animTime * 14) % 2 === 0) {
      return;
    }

    const radius = (player.state === 'BIG' ? 22 : (player.state === 'SMALL' ? 9 : 14));

    ctx.save();
    ctx.translate(player.x, player.y);
    ctx.rotate(player.rotation || 0);

    // 1. Drop shadow / 3D sphere gradient
    const ballGrad = ctx.createRadialGradient(-radius * 0.35, -radius * 0.35, radius * 0.1, 0, 0, radius);
    ballGrad.addColorStop(0, '#f87171');
    ballGrad.addColorStop(0.5, '#dc2626');
    ballGrad.addColorStop(1, '#7f1d1d');
    ctx.fillStyle = ballGrad;

    ctx.beginPath();
    ctx.arc(0, 0, radius, 0, Math.PI * 2);
    ctx.fill();

    // 2. Rolling Texture Line (authentic Nokia ball pattern that spins)
    ctx.strokeStyle = '#fca5a5';
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(-radius * 0.6, 0);
    ctx.lineTo(radius * 0.6, 0);
    ctx.moveTo(0, -radius * 0.6);
    ctx.lineTo(0, radius * 0.6);
    ctx.stroke();

    // 3. Specular highlight dot
    ctx.fillStyle = '#ffffff';
    ctx.beginPath();
    ctx.arc(-radius * 0.35, -radius * 0.35, Math.max(1.5, radius * 0.22), 0, Math.PI * 2);
    ctx.fill();

    ctx.restore();
  }

  function renderHud(ctx, level, player, width, height) {
    ctx.save();
    // High-contrast translucent top bar
    ctx.fillStyle = 'rgba(15, 23, 42, 0.85)';
    ctx.fillRect(0, 0, width, 40);
    ctx.strokeStyle = 'rgba(56, 189, 248, 0.3)';
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(0, 40);
    ctx.lineTo(width, 40);
    ctx.stroke();

    // Font setup
    ctx.font = 'bold 13px Inter, -apple-system, sans-serif';
    ctx.textBaseline = 'middle';

    // A. Lives (Left)
    ctx.fillStyle = '#f87171';
    ctx.beginPath();
    ctx.arc(22, 20, 7, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = '#ffffff';
    ctx.fillText(`× ${player.lives || 0}`, 35, 20);

    // B. Hoops (Center)
    const hoops = level.hoops || [];
    const collected = hoops.filter(h => h.collected).length;
    const total = hoops.length;

    ctx.strokeStyle = '#f59e0b';
    ctx.lineWidth = 3;
    ctx.beginPath();
    ctx.arc(width / 2 - 35, 20, 8, 0, Math.PI * 2);
    ctx.stroke();

    ctx.fillStyle = level.exitOpen ? '#4ade80' : '#fef08a';
    ctx.fillText(`HOOPS: ${collected} / ${total}`, width / 2 - 20, 20);

    // C. Level & Score (Right)
    ctx.fillStyle = '#38bdf8';
    ctx.textAlign = 'right';
    ctx.fillText(`LVL ${level.levelNumber || 1}  •  ${(player.score || 0).toLocaleString()} PTS`, width - 20, 20);

    ctx.restore();
  }

  return {
    THEMES,
    render
  };
}));
