/**
 * bounceEngine.js
 * High-fidelity deterministic physics simulation for Nokia Bounce (Nokia 7210 baseline).
 * Fixed-timestep simulation with subpixel accuracy, ball transformations, water buoyancy,
 * bounce pads, interactive hoops, checkpoints, and hazard collisions.
 */

(function (root, factory) {
  if (typeof define === 'function' && define.amd) {
    define([], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  } else {
    root.BounceEngine = factory();
  }
}(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  // Fixed simulation step: 60Hz (approx 16.666ms)
  const FIXED_DT = 1 / 60;
  const GRAVITY = 520; // px/s^2 for 16px tile grid

  // Ball transformation configurations (calibrated for 16px grid: NORMAL diameter is 14px)
  const BALL_CONFIGS = {
    NORMAL: {
      radius: 7, // 14px diameter (matches reference screenshot)
      mass: 1.0,
      jumpImpulse: -215, // reaches ~44.4px (~2.8 tiles high)
      maxSpeed: 110,
      groundAccel: 520,
      airAccel: 320,
      groundFriction: 0.86,
      airFriction: 0.96,
      bounceRestitution: 0.58,
      waterBuoyancy: 0.95,
      waterDrag: 0.72
    },
    BIG: {
      radius: 11, // 22px diameter
      mass: 1.7,
      jumpImpulse: -260,
      maxSpeed: 95,
      groundAccel: 420,
      airAccel: 250,
      groundFriction: 0.88,
      airFriction: 0.94,
      bounceRestitution: 0.72,
      waterBuoyancy: 1.65,
      waterDrag: 0.65
    },
    SMALL: {
      radius: 4.5, // 9px diameter
      mass: 0.6,
      jumpImpulse: -190,
      maxSpeed: 135,
      groundAccel: 620,
      airAccel: 380,
      groundFriction: 0.84,
      airFriction: 0.97,
      bounceRestitution: 0.48,
      waterBuoyancy: 0.55,
      waterDrag: 0.82
    }
  };

  /**
   * Create an initial player ball state.
   */
  function createPlayerState(spawnX, spawnY, lives = 3) {
    return {
      x: spawnX || 64,
      y: spawnY || 200,
      vx: 0,
      vy: 0,
      rotation: 0, // radians for rolling visual
      state: 'NORMAL', // 'NORMAL' | 'BIG' | 'SMALL'
      lives: Math.max(0, lives),
      isGrounded: false,
      inWater: false,
      isDead: false,
      deathTimer: 0,
      invulnerableTimer: 0,
      lastCheckpoint: null,
      score: 0,
      gameplayTime: 0
    };
  }

  /**
   * Continuous fixed-timestep physics update.
   */
  function tick(level, player, input, dt = FIXED_DT) {
    if (!level || !player) return { player, events: [] };
    const events = [];

    // Handle death animation countdown
    if (player.isDead) {
      player.deathTimer -= dt;
      if (player.deathTimer <= 0) {
        respawnPlayer(player, level);
        events.push({ type: 'RESPAWN' });
      }
      return { player, events };
    }

    if (player.invulnerableTimer > 0) {
      player.invulnerableTimer = Math.max(0, player.invulnerableTimer - dt);
    }

    player.gameplayTime += dt;
    const cfg = BALL_CONFIGS[player.state] || BALL_CONFIGS.NORMAL;

    // 1. Water check
    const inWater = checkWater(level, player.x, player.y, cfg.radius);
    player.inWater = inWater;

    // 2. Horizontal Movement
    const moveLeft = input && (input.left || input.arrowLeft || input.keyA);
    const moveRight = input && (input.right || input.arrowRight || input.keyD);
    const jump = input && (input.jump || input.space || input.arrowUp || input.keyW);

    const accel = player.isGrounded ? cfg.groundAccel : cfg.airAccel;
    if (moveLeft && !moveRight) {
      player.vx = Math.max(-cfg.maxSpeed, player.vx - accel * dt);
    } else if (moveRight && !moveLeft) {
      player.vx = Math.min(cfg.maxSpeed, player.vx + accel * dt);
    } else {
      // Apply friction/drag
      const friction = player.isGrounded ? cfg.groundFriction : cfg.airFriction;
      player.vx *= Math.pow(friction, dt * 60);
      if (Math.abs(player.vx) < 2) player.vx = 0;
    }

    // 3. Vertical Movement (Gravity & Buoyancy)
    if (inWater) {
      // Buoyancy force lifts upward against gravity
      const buoyantForce = GRAVITY * cfg.waterBuoyancy;
      player.vy += (GRAVITY - buoyantForce) * dt;
      player.vy *= Math.pow(cfg.waterDrag, dt * 60);

      // Swim impulse in water
      if (jump) {
        player.vy = Math.max(-180, player.vy - 320 * dt);
      }
    } else {
      player.vy += GRAVITY * dt;
      // Terminal velocity cap
      if (player.vy > 850) player.vy = 850;

      // Jump impulse
      if (jump && player.isGrounded) {
        player.vy = cfg.jumpImpulse;
        player.isGrounded = false;
        events.push({ type: 'JUMP', sound: 'jump' });
      }
    }

    // 4. Update Position with Collision Resolution
    const newX = player.x + player.vx * dt;
    const newY = player.y + player.vy * dt;

    // Rotate ball based on horizontal roll distance
    const rollDist = player.vx * dt;
    player.rotation = (player.rotation + rollDist / cfg.radius) % (Math.PI * 2);

    // Collision against solid terrain/platforms
    const colResult = resolveTerrainCollision(level, player.x, player.y, newX, newY, cfg.radius, cfg.bounceRestitution);
    player.x = colResult.x;
    player.y = colResult.y;
    player.vx = colResult.vx;
    player.vy = colResult.vy;
    player.isGrounded = colResult.isGrounded;

    if (colResult.bounced && Math.abs(colResult.bounceSpeed) > 120) {
      events.push({ type: 'BOUNCE', speed: Math.abs(colResult.bounceSpeed) });
    }

    // Level boundaries containment
    const minX = cfg.radius;
    const maxX = (level.width || 1600) - cfg.radius;
    if (player.x < minX) { player.x = minX; player.vx = Math.max(0, player.vx); }
    if (player.x > maxX) { player.x = maxX; player.vx = Math.min(0, player.vx); }

    // Fall into abyss (below level height)
    if (player.y > (level.height || 600) + 50) {
      killPlayer(player);
      events.push({ type: 'DEATH', reason: 'ABYSS', sound: 'pop' });
      return { player, events };
    }

    // 5. Interactive Objects & Hazards Check
    if (player.invulnerableTimer <= 0) {
      // A. Hazards (Spikes & Moving Hazards)
      if (checkHazardCollision(level, player.x, player.y, cfg.radius)) {
        killPlayer(player);
        events.push({ type: 'DEATH', reason: 'SPIKE', sound: 'pop' });
        return { player, events };
      }
    }

    // B. Trampolines / Bounce Pads
    const trampoline = checkTrampolines(level, player.x, player.y, cfg.radius);
    if (trampoline && player.vy > 0) {
      player.vy = trampoline.impulse || -640;
      player.isGrounded = false;
      events.push({ type: 'TRAMPOLINE', sound: 'bounce_pad' });
    }

    // C. Inflator / Deflator Pumps
    const pump = checkPumps(level, player.x, player.y, cfg.radius);
    if (pump && pump.targetState && player.state !== pump.targetState) {
      const oldState = player.state;
      player.state = pump.targetState;
      events.push({ type: 'TRANSFORM', from: oldState, to: pump.targetState, sound: 'transform' });
    }

    // D. Hoops (Rings)
    if (level.hoops && Array.isArray(level.hoops)) {
      for (let i = 0; i < level.hoops.length; i++) {
        const hoop = level.hoops[i];
        if (!hoop.collected && checkHoopPass(hoop, player.x, player.y, cfg.radius)) {
          hoop.collected = true;
          player.score += 500;
          events.push({ type: 'HOOP_COLLECTED', hoopId: hoop.id, sound: 'hoop' });

          // Check if all hoops collected
          const allCollected = level.hoops.every(h => h.collected);
          if (allCollected) {
            level.exitOpen = true;
            events.push({ type: 'EXIT_UNLOCKED', sound: 'fanfare' });
          }
        }
      }
    }

    // E. Checkpoints
    if (level.checkpoints && Array.isArray(level.checkpoints)) {
      for (let i = 0; i < level.checkpoints.length; i++) {
        const cp = level.checkpoints[i];
        if (!cp.active && Math.hypot(player.x - cp.x, player.y - cp.y) < cfg.radius + 24) {
          level.checkpoints.forEach(c => c.active = false);
          cp.active = true;
          player.lastCheckpoint = { x: cp.x, y: cp.y, id: cp.id };
          events.push({ type: 'CHECKPOINT', checkpointId: cp.id, sound: 'checkpoint' });
        }
      }
    }

    // F. Exit Gate
    if (level.exit && level.exitOpen) {
      const exitBox = level.exit;
      if (
        player.x >= exitBox.x && player.x <= exitBox.x + exitBox.width &&
        player.y >= exitBox.y && player.y <= exitBox.y + exitBox.height
      ) {
        player.score += 2000;
        events.push({ type: 'LEVEL_COMPLETE', sound: 'victory' });
      }
    }

    return { player, events };
  }

  /**
   * Collision resolution against solid platforms, slopes, and tiles.
   */
  function resolveTerrainCollision(level, oldX, oldY, newX, newY, radius, restitution) {
    let x = newX;
    let y = newY;
    let vx = (newX - oldX) / FIXED_DT;
    let vy = (newY - oldY) / FIXED_DT;
    let isGrounded = false;
    let bounced = false;
    let bounceSpeed = 0;

    const platforms = level.platforms || [];
    for (let i = 0; i < platforms.length; i++) {
      const p = platforms[i];
      if (p.type === 'slope_up') {
        // 45-degree slope going up to the right
        if (x >= p.x && x <= p.x + p.width) {
          const t = (x - p.x) / p.width;
          const slopeY = p.y + p.height - t * p.height;
          if (y + radius >= slopeY && y - radius <= p.y + p.height) {
            y = slopeY - radius;
            vy = Math.min(0, vy);
            isGrounded = true;
          }
        }
      } else if (p.type === 'slope_down') {
        // 45-degree slope going down to the right
        if (x >= p.x && x <= p.x + p.width) {
          const t = (x - p.x) / p.width;
          const slopeY = p.y + t * p.height;
          if (y + radius >= slopeY && y - radius <= p.y + p.height) {
            y = slopeY - radius;
            vy = Math.min(0, vy);
            isGrounded = true;
          }
        }
      } else {
        // Standard Axis-Aligned Bounding Box (AABB) Solid
        const nearestX = Math.max(p.x, Math.min(x, p.x + p.width));
        const nearestY = Math.max(p.y, Math.min(y, p.y + p.height));
        const dx = x - nearestX;
        const dy = y - nearestY;
        const distSq = dx * dx + dy * dy;

        if (distSq < radius * radius) {
          const dist = Math.sqrt(distSq);
          let nx = 0;
          let ny = 0;
          let overlap = radius;

          if (dist > 0.0001) {
            nx = dx / dist;
            ny = dy / dist;
            overlap = radius - dist;
          } else {
            // Inside box; eject vertically or horizontally based on shallow penetration
            const penLeft = (x - p.x);
            const penRight = (p.x + p.width - x);
            const penTop = (y - p.y);
            const penBottom = (p.y + p.height - y);
            const minPen = Math.min(penLeft, penRight, penTop, penBottom);

            if (minPen === penTop) { nx = 0; ny = -1; overlap = penTop + radius; }
            else if (minPen === penBottom) { nx = 0; ny = 1; overlap = penBottom + radius; }
            else if (minPen === penLeft) { nx = -1; ny = 0; overlap = penLeft + radius; }
            else { nx = 1; ny = 0; overlap = penRight + radius; }
          }

          x += nx * overlap;
          y += ny * overlap;

          // Velocity normal projection
          const vn = vx * nx + vy * ny;
          if (vn < 0) {
            vx = vx - (1 + restitution) * vn * nx;
            vy = vy - (1 + restitution) * vn * ny;

            if (ny < -0.7) {
              // Hit ground
              if (Math.abs(vn) > 100) {
                bounced = true;
                bounceSpeed = vn;
              }
              if (Math.abs(vy) < 40) {
                vy = 0;
                isGrounded = true;
              }
            } else if (ny > 0.7) {
              // Hit ceiling
              vy = Math.max(0, vy);
            }
          }
        }
      }
    }

    return { x, y, vx, vy, isGrounded, bounced, bounceSpeed };
  }

  function checkWater(level, x, y, radius) {
    if (!level.waterBodies || !Array.isArray(level.waterBodies)) return false;
    for (let i = 0; i < level.waterBodies.length; i++) {
      const w = level.waterBodies[i];
      if (x + radius >= w.x && x - radius <= w.x + w.width &&
          y + radius >= w.y && y - radius <= w.y + w.height) {
        return true;
      }
    }
    return false;
  }

  function checkHazardCollision(level, x, y, radius) {
    if (!level.hazards || !Array.isArray(level.hazards)) return false;
    for (let i = 0; i < level.hazards.length; i++) {
      const h = level.hazards[i];
      if (h.type === 'spike') {
        const sw = h.width || 32;
        const sh = h.height || 24;
        const nearestX = Math.max(h.x, Math.min(x, h.x + sw));
        const nearestY = Math.max(h.y - sh, Math.min(y, h.y));
        const dx = x - nearestX;
        const dy = y - nearestY;
        if (dx * dx + dy * dy < (radius + 2) * (radius + 2)) return true;
      } else if (h.type === 'moving_spike') {
        const dist = Math.hypot(x - h.x, y - h.y);
        if (dist < radius + (h.radius || 14)) return true;
      }
    }
    return false;
  }

  function checkTrampolines(level, x, y, radius) {
    if (!level.trampolines || !Array.isArray(level.trampolines)) return null;
    for (let i = 0; i < level.trampolines.length; i++) {
      const t = level.trampolines[i];
      if (x >= t.x - 8 && x <= t.x + t.width + 8 &&
          y + radius >= t.y - 4 && y + radius <= t.y + 16) {
        return t;
      }
    }
    return null;
  }

  function checkPumps(level, x, y, radius) {
    if (!level.pumps || !Array.isArray(level.pumps)) return null;
    for (let i = 0; i < level.pumps.length; i++) {
      const p = level.pumps[i];
      const dist = Math.hypot(x - p.x, y - p.y);
      if (dist < radius + (p.radius || 18)) {
        return p;
      }
    }
    return null;
  }

  function checkHoopPass(hoop, x, y, radius) {
    const dist = Math.hypot(x - hoop.x, y - hoop.y);
    return dist < (hoop.radius || 24) + radius * 0.5;
  }

  function killPlayer(player) {
    player.isDead = true;
    player.deathTimer = 1.0; // 1s pop death animation
    player.lives = Math.max(0, player.lives - 1);
    player.vx = 0;
    player.vy = 0;
  }

  function respawnPlayer(player, level) {
    player.isDead = false;
    player.deathTimer = 0;
    player.invulnerableTimer = 1.8; // 1.8s flashing invulnerability
    player.state = 'NORMAL';
    player.vx = 0;
    player.vy = 0;

    if (player.lastCheckpoint) {
      player.x = player.lastCheckpoint.x;
      player.y = player.lastCheckpoint.y;
    } else {
      player.x = level.spawnX || 64;
      player.y = level.spawnY || 350;
    }
  }

  return {
    FIXED_DT,
    GRAVITY,
    BALL_CONFIGS,
    createPlayerState,
    tick,
    resolveTerrainCollision,
    checkWater,
    checkHazardCollision,
    checkTrampolines,
    checkPumps,
    checkHoopPass,
    killPlayer,
    respawnPlayer
  };
}));
