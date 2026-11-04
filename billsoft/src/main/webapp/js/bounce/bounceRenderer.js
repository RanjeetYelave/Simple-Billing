/**
 * bounceRenderer.js
 * Authentic Nokia 7210 / Nokia Bounce Pixel-Art Renderer.
 * Treats the user reference screenshot as the exact visual acceptance specification:
 * - 240 x 180 fixed logical resolution (scaled with image-rendering: pixelated)
 * - Solid flat cyan-blue sky (#98cbdf) - zero gradients, zero bezier hills
 * - 16x16 red brick tiles with orange highlight and dark mortar
 * - 16x16 45-degree slope tiles (slope_up, slope_down)
 * - Pink and white striped support columns
 * - Vertical silver elongated hoop loops (transparent hollow center)
 * - 14x14 pixel red ball with specular highlight and rolling texture
 * - Bottom Nokia blue status bar (#0b5cb8) with mini red ball life counters
 * - 7-digit authentic Nokia bitmap score (e.g. 0001730) in crisp white (#ffffff)
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

  // Constants & Dimensions
  const LOGICAL_WIDTH = 240;
  const LOGICAL_HEIGHT = 180;
  const PLAYFIELD_HEIGHT = 144;
  const HUD_HEIGHT = 36;
  const TILE_SIZE = 16;

  // Exact color palette sampled from the reference screenshot
  const PALETTE = {
    sky: '#98cbdf',
    hudBlue: '#0b5cb8',
    hudTopLine: '#07448c',
    scoreWhite: '#ffffff',
    brickHighlight: '#ea4c16',
    brickHighlightBright: '#ff622c',
    brickMortar: '#300400',
    brickBody: '#b42004',
    brickStippleDark: '#681002',
    brickShadow: '#280400',
    pillarPink: '#f89898',
    pillarWhite: '#ffffff',
    hoopDark: '#2a3038',
    hoopSilver: '#bdc6cf',
    hoopGlint: '#ffffff',
    ballRed: '#dc2810',
    ballShadow: '#781004',
    ballOutline: '#200404',
    ballWhite: '#ffffff',
    ballHalo: '#ff8878'
  };

  // Cached Offscreen Sprites
  let cachedSprites = null;

  /**
   * Generates all pixel-art raster sprites on first run.
   */
  function initSprites() {
    if (cachedSprites) return cachedSprites;

    // Helper to create an offscreen canvas
    function createOffscreen(w, h) {
      if (typeof document !== 'undefined' && document.createElement) {
        const can = document.createElement('canvas');
        can.width = w;
        can.height = h;
        const c = can.getContext('2d');
        c.imageSmoothingEnabled = false;
        return { canvas: can, ctx: c, width: w, height: h };
      }
      return null;
    }

    // 1. Red Brick Tile (16x16)
    const brick = createOffscreen(16, 16);
    if (brick) {
      const c = brick.ctx;
      // Main brick red
      c.fillStyle = PALETTE.brickBody;
      c.fillRect(0, 0, 16, 16);

      // Top highlight bevel (rows 0-1)
      c.fillStyle = PALETTE.brickHighlight;
      c.fillRect(0, 0, 15, 2);
      c.fillStyle = PALETTE.brickHighlightBright;
      c.fillRect(1, 0, 13, 1);

      // Dark mortar line (row 3)
      c.fillStyle = PALETTE.brickMortar;
      c.fillRect(0, 3, 16, 1);

      // Dark stippling pattern across lower brick rows
      c.fillStyle = PALETTE.brickStippleDark;
      const stipples = [
        [3, 5], [7, 5], [11, 6], [2, 7], [9, 7],
        [4, 9], [12, 9], [6, 11], [1, 12], [8, 12],
        [3, 14], [10, 14]
      ];
      stipples.forEach(([x, y]) => c.fillRect(x, y, 2, 2));

      // Vertical brick seam (sub-dividing upper and lower brick layers)
      c.fillStyle = PALETTE.brickMortar;
      c.fillRect(8, 0, 1, 3);
      c.fillRect(0, 4, 1, 11);
      c.fillRect(8, 8, 1, 7);

      // Bottom and right shadow border
      c.fillStyle = PALETTE.brickShadow;
      c.fillRect(0, 15, 16, 1);
      c.fillRect(15, 0, 1, 16);
    }

    // 2. Slope Up Tile (16x16, rising 45 degrees from bottom-left to top-right)
    const slopeUp = createOffscreen(16, 16);
    if (slopeUp) {
      const c = slopeUp.ctx;
      for (let y = 0; y < 16; y++) {
        const slopeX = 15 - y;
        for (let x = 0; x < 16; x++) {
          if (x < slopeX) {
            // Transparent sky
            continue;
          } else if (x === slopeX) {
            // Slope top edge highlight
            c.fillStyle = PALETTE.brickHighlight;
            c.fillRect(x, y, 1, 1);
          } else if (x === slopeX + 1) {
            // Mortar line
            c.fillStyle = PALETTE.brickMortar;
            c.fillRect(x, y, 1, 1);
          } else {
            // Brick red body with stipples
            c.fillStyle = ((x + y) % 5 === 0) ? PALETTE.brickStippleDark : PALETTE.brickBody;
            c.fillRect(x, y, 1, 1);
          }
        }
      }
      // Right edge shadow
      c.fillStyle = PALETTE.brickShadow;
      c.fillRect(15, 0, 1, 16);
    }

    // 3. Slope Down Tile (16x16, falling 45 degrees from top-left to bottom-right)
    const slopeDown = createOffscreen(16, 16);
    if (slopeDown) {
      const c = slopeDown.ctx;
      for (let y = 0; y < 16; y++) {
        const slopeX = y;
        for (let x = 0; x < 16; x++) {
          if (x > slopeX) {
            // Transparent sky
            continue;
          } else if (x === slopeX) {
            // Slope top edge highlight
            c.fillStyle = PALETTE.brickHighlight;
            c.fillRect(x, y, 1, 1);
          } else if (x === slopeX - 1) {
            c.fillStyle = PALETTE.brickMortar;
            c.fillRect(x, y, 1, 1);
          } else {
            c.fillStyle = ((x + y) % 5 === 0) ? PALETTE.brickStippleDark : PALETTE.brickBody;
            c.fillRect(x, y, 1, 1);
          }
        }
      }
      c.fillStyle = PALETTE.brickShadow;
      c.fillRect(0, 15, 16, 1);
    }

    // 4. Striped Support Pillar Tile (16x16, alternating pink and white stripes)
    const stripedPillar = createOffscreen(16, 16);
    if (stripedPillar) {
      const c = stripedPillar.ctx;
      // 4 repeating stripe pairs (each 2px pink, 2px white)
      for (let x = 0; x < 16; x += 4) {
        c.fillStyle = PALETTE.pillarPink;
        c.fillRect(x, 0, 2, 16);
        c.fillStyle = PALETTE.pillarWhite;
        c.fillRect(x + 2, 0, 2, 16);
      }
      // Subtle vertical edge definition
      c.fillStyle = 'rgba(40, 4, 0, 0.25)';
      c.fillRect(0, 0, 1, 16);
      c.fillRect(15, 0, 1, 16);
    }

    // 5. Vertical Silver Elongated Hoop (10x28, hollow metallic capsule)
    const silverHoop = createOffscreen(10, 28);
    if (silverHoop) {
      const c = silverHoop.ctx;
      // Dark outline capsule
      c.fillStyle = PALETTE.hoopDark;
      // Top curve
      c.fillRect(2, 0, 6, 2);
      c.fillRect(1, 1, 8, 2);
      // Left rail
      c.fillRect(0, 3, 3, 22);
      // Right rail
      c.fillRect(7, 3, 3, 22);
      // Bottom curve
      c.fillRect(1, 25, 8, 2);
      c.fillRect(2, 26, 6, 2);

      // Silver fill inside rails
      c.fillStyle = PALETTE.hoopSilver;
      c.fillRect(1, 2, 2, 24);
      c.fillRect(7, 2, 2, 24);

      // Specular metallic glints
      c.fillStyle = PALETTE.hoopGlint;
      c.fillRect(1, 4, 1, 8);
      c.fillRect(1, 16, 1, 6);
      c.fillRect(7, 5, 1, 7);
      c.fillRect(7, 17, 1, 6);
      c.fillRect(3, 1, 4, 1);
      c.fillRect(3, 26, 4, 1);

      // Center (x: 3..6, y: 3..24) is intentionally transparent for the pass-through!
    }

    // 6. Red Ball Sprite (14x14)
    const ball = createOffscreen(14, 14);
    if (ball) {
      const c = ball.ctx;
      // Circular raster matrix (diameter 14, radius 7)
      const matrix = [
        "....XXXXXX....",
        "..XXRRRRRRXX..",
        ".XRRRRRRWWRRX.",
        ".XRRRRRRWWRRX.",
        "XRRRRRRRRRRRRX",
        "XRRRRRRRRRRRRX",
        "XRRRRRRRRRRRRX",
        "XSSRRRRRRRRRRX",
        "XSSSSRRRRRRRRX",
        "XSSSSSSRRRRRRX",
        ".XSSSSSSRRRRX.",
        ".XSSSSSSSSRRX.",
        "..XXSSSSSSXX..",
        "....XXXXXX...."
      ];

      for (let y = 0; y < 14; y++) {
        const row = matrix[y];
        for (let x = 0; x < 14; x++) {
          const ch = row[x];
          if (ch === 'X') {
            c.fillStyle = PALETTE.ballOutline;
            c.fillRect(x, y, 1, 1);
          } else if (ch === 'R') {
            c.fillStyle = PALETTE.ballRed;
            c.fillRect(x, y, 1, 1);
          } else if (ch === 'S') {
            c.fillStyle = PALETTE.ballShadow;
            c.fillRect(x, y, 1, 1);
          } else if (ch === 'W') {
            c.fillStyle = PALETTE.ballWhite;
            c.fillRect(x, y, 1, 1);
          }
        }
      }
    }

    // 7. Mini Life Counter Ball (10x10)
    const miniBall = createOffscreen(10, 10);
    if (miniBall) {
      const c = miniBall.ctx;
      const mMatrix = [
        "..XXXX..",
        ".XRRWRX.",
        "XRRRWRRX",
        "XRRRRRRX",
        "XSRRRRRX",
        "XSSSRRRX",
        ".XSSSRX.",
        "..XXXX.."
      ];
      for (let y = 0; y < 8; y++) {
        for (let x = 0; x < 8; x++) {
          const ch = mMatrix[y][x];
          if (ch === 'X') {
            c.fillStyle = PALETTE.ballOutline;
            c.fillRect(x + 1, y + 1, 1, 1);
          } else if (ch === 'R') {
            c.fillStyle = PALETTE.ballRed;
            c.fillRect(x + 1, y + 1, 1, 1);
          } else if (ch === 'S') {
            c.fillStyle = PALETTE.ballShadow;
            c.fillRect(x + 1, y + 1, 1, 1);
          } else if (ch === 'W') {
            c.fillStyle = PALETTE.ballWhite;
            c.fillRect(x + 1, y + 1, 1, 1);
          }
        }
      }
    }

    // 8. Nokia 7-Segment Bitmap Digits (12x18 each for 0-9)
    const digits = [];
    const DIGIT_FONT = [
      // 0
      [
        "011111111110",
        "111111111111",
        "111000000111",
        "111000000111",
        "111000000111",
        "111000000111",
        "111000000111",
        "111000000111",
        "111000000111",
        "111000000111",
        "111000000111",
        "111000000111",
        "111000000111",
        "111000000111",
        "111000000111",
        "111000000111",
        "111111111111",
        "011111111110"
      ],
      // 1
      [
        "000011110000",
        "000111110000",
        "001111110000",
        "011101110000",
        "000001110000",
        "000001110000",
        "000001110000",
        "000001110000",
        "000001110000",
        "000001110000",
        "000001110000",
        "000001110000",
        "000001110000",
        "000001110000",
        "000001110000",
        "000001110000",
        "111111111111",
        "111111111111"
      ],
      // 2
      [
        "011111111110",
        "111111111111",
        "111000000111",
        "000000000111",
        "000000000111",
        "000000001110",
        "000000011100",
        "000000111000",
        "000001110000",
        "000011100000",
        "000111000000",
        "001110000000",
        "011100000000",
        "111000000000",
        "111000000000",
        "111111111111",
        "111111111111",
        "111111111111"
      ],
      // 3
      [
        "011111111110",
        "111111111111",
        "111000000111",
        "000000000111",
        "000000000111",
        "000000000111",
        "000000000111",
        "000111111110",
        "000111111110",
        "000000000111",
        "000000000111",
        "000000000111",
        "000000000111",
        "000000000111",
        "111000000111",
        "111000000111",
        "111111111111",
        "011111111110"
      ],
      // 4
      [
        "000000111100",
        "000001111100",
        "000011111100",
        "000111011100",
        "001110011100",
        "011100011100",
        "111000011100",
        "111000011100",
        "111000011100",
        "111111111111",
        "111111111111",
        "000000011100",
        "000000011100",
        "000000011100",
        "000000011100",
        "000000011100",
        "000000011100",
        "000000011100"
      ],
      // 5
      [
        "111111111111",
        "111111111111",
        "111000000000",
        "111000000000",
        "111000000000",
        "111111111100",
        "111111111110",
        "000000000111",
        "000000000111",
        "000000000111",
        "000000000111",
        "000000000111",
        "000000000111",
        "000000000111",
        "111000000111",
        "111000000111",
        "111111111111",
        "011111111110"
      ],
      // 6
      [
        "011111111110",
        "111111111111",
        "111000000111",
        "111000000000",
        "111000000000",
        "111000000000",
        "111000000000",
        "111111111110",
        "111111111111",
        "111000000111",
        "111000000111",
        "111000000111",
        "111000000111",
        "111000000111",
        "111000000111",
        "111000000111",
        "111111111111",
        "011111111110"
      ],
      // 7
      [
        "111111111111",
        "111111111111",
        "000000000111",
        "000000000111",
        "000000001110",
        "000000011100",
        "000000111000",
        "000001110000",
        "000011100000",
        "000011100000",
        "000111000000",
        "000111000000",
        "001110000000",
        "001110000000",
        "011100000000",
        "011100000000",
        "011100000000",
        "011100000000"
      ],
      // 8
      [
        "011111111110",
        "111111111111",
        "111000000111",
        "111000000111",
        "111000000111",
        "111000000111",
        "111000000111",
        "011111111110",
        "011111111110",
        "111000000111",
        "111000000111",
        "111000000111",
        "111000000111",
        "111000000111",
        "111000000111",
        "111000000111",
        "111111111111",
        "011111111110"
      ],
      // 9
      [
        "011111111110",
        "111111111111",
        "111000000111",
        "111000000111",
        "111000000111",
        "111000000111",
        "111000000111",
        "111111111111",
        "011111111111",
        "000000000111",
        "000000000111",
        "000000000111",
        "000000000111",
        "000000000111",
        "000000000111",
        "111000000111",
        "111111111111",
        "011111111110"
      ]
    ];

    for (let d = 0; d < 10; d++) {
      const dig = createOffscreen(12, 18);
      if (dig) {
        const c = dig.ctx;
        c.fillStyle = PALETTE.scoreWhite;
        const pattern = DIGIT_FONT[d];
        for (let y = 0; y < 18; y++) {
          const row = pattern[y];
          for (let x = 0; x < 12; x++) {
            if (row[x] === '1') {
              c.fillRect(x, y, 1, 1);
            }
          }
        }
        digits.push(dig.canvas);
      }
    }

    cachedSprites = {
      brick: brick ? brick.canvas : null,
      slopeUp: slopeUp ? slopeUp.canvas : null,
      slopeDown: slopeDown ? slopeDown.canvas : null,
      stripedPillar: stripedPillar ? stripedPillar.canvas : null,
      silverHoop: silverHoop ? silverHoop.canvas : null,
      ball: ball ? ball.canvas : null,
      miniBall: miniBall ? miniBall.canvas : null,
      digits: digits
    };

    return cachedSprites;
  }

  /**
   * Main Render Pipeline called every frame.
   * Renders at fixed 240 x 180 logical dimensions.
   */
  function render(ctx, level, player, camera, viewportWidth, viewportHeight, animTime = 0) {
    if (!ctx || !level || !player) return;

    const sprites = initSprites();

    ctx.save();
    // 1. Force strict pixelated rendering (disable anti-aliasing)
    ctx.imageSmoothingEnabled = false;

    // 2. Draw Solid Flat Sky Background (exact #98cbdf cyan-blue)
    ctx.fillStyle = PALETTE.sky;
    ctx.fillRect(0, 0, LOGICAL_WIDTH, PLAYFIELD_HEIGHT);

    // 3. Camera Transform for World Elements
    ctx.save();
    // Snap camera strictly to integer pixel coordinates to prevent fractional blur
    const camX = Math.floor(camera.x || 0);
    const camY = Math.floor(camera.y || 0);
    ctx.translate(-camX, -camY);

    // 4. Draw Platforms, Walls, Ceilings, Slopes & Pillars
    renderWorldPlatforms(ctx, level, sprites);

    // 5. Draw Interactive Elements (Hoops, Exit Gate, Trampolines, Pumps, Checkpoints)
    renderWorldInteractive(ctx, level, sprites, animTime);

    // 6. Draw Hazards (Spikes)
    renderWorldHazards(ctx, level);

    // 7. Draw Player Ball
    renderPixelPlayer(ctx, player, sprites, animTime);

    ctx.restore(); // Restore camera transform

    // 8. Draw Authentic Bottom Blue HUD (y = 144 to 180)
    renderNokiaHud(ctx, level, player, sprites);

    ctx.restore();
  }

  /**
   * Render all solid tiles, slopes, and striped pillars.
   */
  function renderWorldPlatforms(ctx, level, sprites) {
    const plats = level.platforms || [];
    for (let i = 0; i < plats.length; i++) {
      const p = plats[i];
      const px = Math.floor(p.x);
      const py = Math.floor(p.y);
      const pw = Math.floor(p.width);
      const ph = Math.floor(p.height);

      if (p.type === 'slope_up') {
        // Draw 45-degree slope up
        if (sprites.slopeUp) {
          for (let x = px; x < px + pw; x += TILE_SIZE) {
            ctx.drawImage(sprites.slopeUp, x, py, TILE_SIZE, TILE_SIZE);
          }
        } else {
          ctx.fillStyle = PALETTE.brickBody;
          ctx.beginPath();
          ctx.moveTo(px, py + ph);
          ctx.lineTo(px + pw, py);
          ctx.lineTo(px + pw, py + ph);
          ctx.closePath();
          ctx.fill();
        }
      } else if (p.type === 'slope_down') {
        // Draw 45-degree slope down
        if (sprites.slopeDown) {
          for (let x = px; x < px + pw; x += TILE_SIZE) {
            ctx.drawImage(sprites.slopeDown, x, py, TILE_SIZE, TILE_SIZE);
          }
        }
      } else if (p.type === 'striped_pillar' || p.isPillar) {
        // Draw vertical striped support pillar
        if (sprites.stripedPillar) {
          for (let y = py; y < py + ph; y += TILE_SIZE) {
            for (let x = px; x < px + pw; x += TILE_SIZE) {
              ctx.drawImage(sprites.stripedPillar, x, y, TILE_SIZE, TILE_SIZE);
            }
          }
        }
      } else {
        // Standard Red Brick Tile Fill
        if (sprites.brick) {
          for (let y = py; y < py + ph; y += TILE_SIZE) {
            for (let x = px; x < px + pw; x += TILE_SIZE) {
              const drawW = Math.min(TILE_SIZE, px + pw - x);
              const drawH = Math.min(TILE_SIZE, py + ph - y);
              ctx.drawImage(sprites.brick, 0, 0, drawW, drawH, x, y, drawW, drawH);
            }
          }
        } else {
          ctx.fillStyle = PALETTE.brickBody;
          ctx.fillRect(px, py, pw, ph);
        }
      }
    }
  }

  /**
   * Render interactive hoops, exit gate, trampolines, and checkpoints.
   */
  function renderWorldInteractive(ctx, level, sprites, animTime) {
    // 1. Hoops (Vertical Silver Elongated Loops)
    const hoops = level.hoops || [];
    for (let i = 0; i < hoops.length; i++) {
      const h = hoops[i];
      const hx = Math.floor(h.x);
      const hy = Math.floor(h.y);

      if (h.collected) {
        // Faint collected sparkle ring
        ctx.strokeStyle = 'rgba(255, 215, 0, 0.4)';
        ctx.lineWidth = 1;
        ctx.strokeRect(hx - 4, hy - 12, 8, 24);
      } else {
        // Draw authentic vertical silver hoop
        if (sprites.silverHoop) {
          // Centered on (hx, hy)
          ctx.drawImage(sprites.silverHoop, hx - 5, hy - 14);
        } else {
          ctx.strokeStyle = PALETTE.hoopSilver;
          ctx.lineWidth = 2;
          ctx.strokeRect(hx - 4, hy - 12, 8, 24);
        }
      }
    }

    // 2. Trampolines
    const tramps = level.trampolines || [];
    for (let i = 0; i < tramps.length; i++) {
      const t = tramps[i];
      const tx = Math.floor(t.x);
      const ty = Math.floor(t.y);
      const tw = t.width || 32;

      // Yellow top bounce bar
      ctx.fillStyle = '#f59e0b';
      ctx.fillRect(tx, ty, tw, 3);
      // Red spring coils
      ctx.fillStyle = '#dc2810';
      ctx.fillRect(tx + 4, ty + 3, 4, 5);
      ctx.fillRect(tx + tw - 8, ty + 3, 4, 5);
    }

    // 3. Transformation Pumps
    const pumps = level.pumps || [];
    for (let i = 0; i < pumps.length; i++) {
      const p = pumps[i];
      const px = Math.floor(p.x);
      const py = Math.floor(p.y);
      const isBig = p.targetState === 'BIG';

      // Base
      ctx.fillStyle = '#280400';
      ctx.fillRect(px - 7, py + 3, 14, 5);
      // Bellows
      ctx.fillStyle = isBig ? '#38bdf8' : '#eab308';
      ctx.fillRect(px - 5, py - 6, 10, 9);
      // Indicator pixel
      ctx.fillStyle = '#ffffff';
      ctx.fillRect(px - 1, py - 3, 2, 2);
    }

    // 4. Checkpoints
    const cps = level.checkpoints || [];
    for (let i = 0; i < cps.length; i++) {
      const cp = cps[i];
      const cx = Math.floor(cp.x);
      const cy = Math.floor(cp.y);

      // Steel mast
      ctx.fillStyle = '#475569';
      ctx.fillRect(cx - 1, cy - 24, 2, 24);
      // Flag pennant
      ctx.fillStyle = cp.active ? '#22c55e' : '#dc2810';
      ctx.beginPath();
      ctx.moveTo(cx + 1, cy - 24);
      ctx.lineTo(cx + 12, cy - 19);
      ctx.lineTo(cx + 1, cy - 14);
      ctx.closePath();
      ctx.fill();
    }

    // 5. Exit Gate
    if (level.exit) {
      const ex = level.exit;
      const exX = Math.floor(ex.x);
      const exY = Math.floor(ex.y);
      const exW = Math.floor(ex.width);
      const exH = Math.floor(ex.height);

      ctx.fillStyle = '#300400';
      ctx.fillRect(exX, exY, exW, exH);

      if (level.exitOpen) {
        // Open exit portal beam
        ctx.fillStyle = '#fef08a';
        ctx.fillRect(exX + 2, exY + 2, exW - 4, exH - 2);
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(exX + exW / 2 - 2, exY + exH / 2 - 2, 4, 4);
      } else {
        // Locked door with red ruby lock
        ctx.fillStyle = '#1e0404';
        ctx.fillRect(exX + 2, exY + 2, exW - 4, exH - 2);
        ctx.fillStyle = '#dc2810';
        ctx.fillRect(exX + exW / 2 - 2, exY + exH / 2 - 2, 4, 4);
      }
    }
  }

  /**
   * Render hazards (spikes).
   */
  function renderWorldHazards(ctx, level) {
    const hazards = level.hazards || [];
    for (let i = 0; i < hazards.length; i++) {
      const h = hazards[i];
      if (h.type === 'spike') {
        const hx = Math.floor(h.x);
        const hy = Math.floor(h.y);
        const hw = h.width || 16;
        const count = Math.max(1, Math.floor(hw / 8));
        const segW = hw / count;

        ctx.fillStyle = '#bdc6cf';
        for (let s = 0; s < count; s++) {
          const sx = hx + s * segW;
          ctx.beginPath();
          ctx.moveTo(sx, hy);
          ctx.lineTo(sx + segW / 2, hy - (h.height || 12));
          ctx.lineTo(sx + segW, hy);
          ctx.closePath();
          ctx.fill();
        }
      }
    }
  }

  /**
   * Render the player ball with authentic pixel shading and rolling illusion.
   */
  function renderPixelPlayer(ctx, player, sprites, animTime) {
    if (player.isDead) {
      // Pop death particles
      ctx.fillStyle = PALETTE.ballRed;
      const count = 6;
      for (let i = 0; i < count; i++) {
        const ang = (i / count) * Math.PI * 2;
        const dist = (1.0 - player.deathTimer) * 20;
        const px = Math.floor(player.x + Math.cos(ang) * dist);
        const py = Math.floor(player.y + Math.sin(ang) * dist);
        ctx.fillRect(px - 1, py - 1, 3, 3);
      }
      return;
    }

    // Invulnerability flashing
    if (player.invulnerableTimer > 0 && Math.floor(animTime * 14) % 2 === 0) {
      return;
    }

    const px = Math.floor(player.x);
    const py = Math.floor(player.y);

    if (player.state === 'NORMAL' && sprites.ball) {
      // Draw 14x14 pixel ball centered at (px, py)
      ctx.drawImage(sprites.ball, px - 7, py - 7);
    } else {
      // Fallback for BIG / SMALL states
      const r = player.state === 'BIG' ? 11 : (player.state === 'SMALL' ? 4.5 : 7);
      ctx.fillStyle = PALETTE.ballOutline;
      ctx.beginPath();
      ctx.arc(px, py, r, 0, Math.PI * 2);
      ctx.fill();

      ctx.fillStyle = PALETTE.ballRed;
      ctx.beginPath();
      ctx.arc(px, py, r - 1, 0, Math.PI * 2);
      ctx.fill();

      // Specular highlight dot
      ctx.fillStyle = PALETTE.ballWhite;
      ctx.fillRect(px + r * 0.25, py - r * 0.55, 2, 2);
    }
  }

  /**
   * Render Authentic Nokia Blue HUD (y = 144 to 180).
   * Exact layout from reference screenshot:
   * - Left: Mini red ball life counters
   * - Right: Large white 7-digit pixel score (e.g. 0001730)
   */
  function renderNokiaHud(ctx, level, player, sprites) {
    // 1. Solid Blue Bar Background (#0b5cb8)
    ctx.fillStyle = PALETTE.hudBlue;
    ctx.fillRect(0, PLAYFIELD_HEIGHT, LOGICAL_WIDTH, HUD_HEIGHT);

    // 2. Top seam divider line
    ctx.fillStyle = PALETTE.hudTopLine;
    ctx.fillRect(0, PLAYFIELD_HEIGHT, LOGICAL_WIDTH, 1);

    // 3. Life Indicators (Left)
    const lives = Math.max(0, player.lives || 0);
    const lifeBall = sprites.miniBall;
    const startX = 12;
    const lifeY = 155;
    const spacing = 16;

    for (let i = 0; i < Math.min(lives, 5); i++) {
      if (lifeBall) {
        ctx.drawImage(lifeBall, startX + i * spacing, lifeY);
      } else {
        ctx.fillStyle = PALETTE.ballRed;
        ctx.beginPath();
        ctx.arc(startX + i * spacing + 4, lifeY + 4, 4, 0, Math.PI * 2);
        ctx.fill();
      }
    }

    // 4. Large White 7-Digit Pixel Score (Right: e.g. 0001730)
    const scoreVal = Math.max(0, Math.floor(player.score || 0));
    const scoreStr = String(scoreVal).padStart(7, '0'); // exact 7 digits!

    // Each digit is 12px wide with 1px space between digits
    const digitW = 12;
    const digitH = 18;
    const digitSpacing = 1;
    const totalScoreW = 7 * digitW + 6 * digitSpacing; // 7*12 + 6 = 90px
    const scoreX = LOGICAL_WIDTH - totalScoreW - 14; // Right-aligned with 14px padding
    const scoreY = 153;

    if (sprites.digits && sprites.digits.length === 10) {
      for (let i = 0; i < 7; i++) {
        const d = parseInt(scoreStr[i], 10);
        const digSprite = sprites.digits[d];
        if (digSprite) {
          ctx.drawImage(digSprite, scoreX + i * (digitW + digitSpacing), scoreY);
        }
      }
    } else {
      // Fallback font
      ctx.fillStyle = PALETTE.scoreWhite;
      ctx.font = 'bold 18px monospace';
      ctx.fillText(scoreStr, scoreX, scoreY + 14);
    }
  }

  return {
    LOGICAL_WIDTH,
    LOGICAL_HEIGHT,
    PLAYFIELD_HEIGHT,
    HUD_HEIGHT,
    PALETTE,
    render
  };
}));
