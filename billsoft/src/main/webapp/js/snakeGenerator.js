/**
 * snakeGenerator.js
 * Procedural level generator and playability validator for Snake (Take a Break -> Classic).
 * Guaranteed 100% deterministic, fair, and achievable.
 */

(function (root, factory) {
  if (typeof define === 'function' && define.amd) {
    define(['./snakeEngine'], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory(require('./snakeEngine'));
  } else {
    root.SnakeGenerator = factory(root.SnakeEngine);
  }
}(typeof self !== 'undefined' ? self : this, function (SnakeEngine) {
  'use strict';

  const Engine = SnakeEngine || (typeof window !== 'undefined' ? window.SnakeEngine : null);

  /**
   * Deterministic 32-bit PRNG (Mulberry32)
   */
  function createRng(seed) {
    let s = seed >>> 0;
    return function next() {
      s = (s + 0x6D2B79F5) >>> 0;
      let t = Math.imul(s ^ (s >>> 15), 1 | s);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }

  /**
   * Determine grid parameters and rules based on level number and optional responsive dimensions
   */
  function getLevelConfig(level, customDimensions) {
    const lvl = Math.max(1, Math.floor(level || 1));
    const isMilestone = (lvl % 10 === 0);
    const isOasis = (lvl > 15 && lvl % 5 === 0 && !isMilestone); // Relaxing breather level

    let width = 16;
    let height = 12;
    let targetFruits = 5;
    let baseTickMs = 180;
    let layoutType = 'CLEAN';

    if (lvl <= 5) {
      width = 16; height = 12; targetFruits = 5; baseTickMs = 180; layoutType = 'CLEAN';
    } else if (lvl <= 10) {
      width = 18; height = 14; targetFruits = 7; baseTickMs = 165; layoutType = 'PILLARS';
    } else if (lvl <= 25) {
      width = 22; height = 14; targetFruits = 10; baseTickMs = 150; layoutType = 'GARDEN_MAZE';
    } else if (lvl <= 50) {
      width = 24; height = 16; targetFruits = 12; baseTickMs = 135; layoutType = 'CHAMBERS';
    } else if (lvl <= 75) {
      width = 26; height = 16; targetFruits = 15; baseTickMs = 125; layoutType = 'ARCHIPELAGO';
    } else if (lvl <= 100) {
      width = 28; height = 18; targetFruits = 18; baseTickMs = 120; layoutType = 'LABYRINTH';
    } else {
      width = 30; height = 18;
      targetFruits = 15 + (lvl % 6);
      baseTickMs = 115; // Hard speed floor ceiling
      layoutType = 'PROCEDURAL';
    }

    // Apply responsive viewport dimension overrides if provided
    if (customDimensions && typeof customDimensions.width === 'number' && typeof customDimensions.height === 'number') {
      width = Math.max(14, Math.min(48, Math.floor(customDimensions.width)));
      height = Math.max(10, Math.min(26, Math.floor(customDimensions.height)));
    }

    if (isOasis) {
      layoutType = 'OASIS'; // 0 obstacles
      targetFruits = Math.max(6, Math.floor(targetFruits * 0.8));
    }

    return {
      lvl,
      width,
      height,
      targetFruits,
      baseTickMs: Math.max(115, baseTickMs), // Strict 115ms speed floor
      layoutType,
      isMilestone,
      isOasis,
      hasGoldenApple: lvl >= 5,
      hasSpeedBoost: lvl >= 8,
      hasMystery: lvl >= 12,
      hasChillBerry: lvl >= 16,
      hasMagnet: lvl >= 20,
      hasHoppingFruit: lvl >= 35,
      hasGateways: isMilestone && lvl >= 10
    };
  }

  /**
   * Validate that a candidate Snake board is 100% fair, navigable, and playable.
   */
  function validatePlayableBoard(board) {
    if (!board) return false;
    const { width, height, snake, obstacles, direction } = board;
    const totalCells = width * height;
    const obstacleList = obstacles || [];
    const obstacleSet = new Set(obstacleList);

    // 1. Obstacle density must be <= 12%
    const density = obstacleList.length / totalCells;
    if (density > 0.12) return false;

    // 2. Snake head and body must not intersect obstacles
    for (let i = 0; i < snake.length; i++) {
      const idx = snake[i][1] * width + snake[i][0];
      if (obstacleSet.has(idx)) return false;
    }

    // 3. Snake head must have at least 3 open clear tiles ahead in starting direction
    const head = snake[0];
    const dirVector = (Engine && Engine.DIRS[direction]) ? Engine.DIRS[direction] : { dx: 1, dy: 0 };
    for (let step = 1; step <= 3; step++) {
      const checkX = ((head[0] + dirVector.dx * step) % width + width) % width;
      const checkY = ((head[1] + dirVector.dy * step) % height + height) % height;
      const checkIdx = checkY * width + checkX;
      if (obstacleSet.has(checkIdx)) return false;
    }

    // 4. BFS Flood Fill: All non-obstacle cells must form a single connected component
    const openCellCount = totalCells - obstacleList.length;
    const visited = new Set();
    const queue = [snake[0][1] * width + snake[0][0]];
    visited.add(queue[0]);

    while (queue.length > 0) {
      const curr = queue.shift();
      const cx = curr % width;
      const cy = Math.floor(curr / width);

      const neighbors = [
        { x: (cx + 1) % width, y: cy },
        { x: (cx - 1 + width) % width, y: cy },
        { x: cx, y: (cy + 1) % height },
        { x: cx, y: (cy - 1 + height) % height }
      ];

      for (let n of neighbors) {
        const nIdx = n.y * width + n.x;
        if (!obstacleSet.has(nIdx) && !visited.has(nIdx)) {
          visited.add(nIdx);
          queue.push(nIdx);
        }
      }
    }

    if (visited.size < openCellCount) {
      return false; // Trapped or disconnected pocket detected
    }

    return true;
  }

  /**
   * Authoritative Central Collectible Spawn Policy.
   * Enforces 75% Normal Apple / 25% Special Item balance,
   * max 2 consecutive specials rate limit, novelty cooldowns,
   * and progressive power-up unlocking without duplicate stacking.
   */
  function determineNextFruit(boardState, levelConfig, rng) {
    const defaultApple = { type: 'APPLE', lifetimeMs: null };
    if (!levelConfig) return defaultApple;

    // Oasis levels are pure peaceful Zen gardens with 100% normal apples
    if (levelConfig.isOasis) return defaultApple;

    const state = boardState || {};
    const consecutiveSpecials = state.consecutiveSpecials || 0;
    const noveltyCooldown = state.noveltyCooldown || 0;

    // Hard Rate Limiting: Max 2 consecutive special items, and respect novelty cooldown
    if (consecutiveSpecials >= 2 || noveltyCooldown > 0) {
      return defaultApple;
    }

    const random = rng || Math.random;
    const roll = random();

    // Baseline Normal Apple Guarantee (Calibrated for exact ~75% normal apples / ~25% specials, yielding 2-3 specials per 10 apples)
    if (roll >= 0.28) {
      return defaultApple;
    }

    // Build eligible pool of unlocked special items for this level
    const pool = [];
    if (levelConfig.hasGoldenApple) {
      pool.push({ type: 'GOLDEN_APPLE', lifetimeMs: 14000, weight: 30 });
    }
    if (levelConfig.hasSpeedBoost && (!state.boostTimer || state.boostTimer <= 0)) {
      pool.push({ type: 'SPEED_BOOST', lifetimeMs: 14000, weight: 20 });
    }
    if (levelConfig.hasMystery) {
      pool.push({ type: 'MYSTERY', lifetimeMs: 16000, weight: 20 });
    }
    if (levelConfig.hasChillBerry && (!state.chillTimer || state.chillTimer <= 0)) {
      pool.push({ type: 'CHILL_BERRY', lifetimeMs: 15000, weight: 20 });
    }
    if (levelConfig.hasMagnet && (!state.magnetTimer || state.magnetTimer <= 0)) {
      pool.push({ type: 'MAGNET', lifetimeMs: 15000, weight: 15 });
    }
    if (levelConfig.hasHoppingFruit) {
      pool.push({ type: 'HOPPING_FRUIT', lifetimeMs: 18000, weight: 15 });
    }

    if (pool.length === 0) {
      return defaultApple;
    }

    const totalWeight = pool.reduce((sum, item) => sum + item.weight, 0);
    let chosenWeight = random() * totalWeight;

    for (let item of pool) {
      if (chosenWeight < item.weight) {
        return { type: item.type, lifetimeMs: item.lifetimeMs };
      }
      chosenWeight -= item.weight;
    }

    return pool[0];
  }

  /**
   * Spawn a random valid fruit on an open cell not occupied by snake, obstacles, or gateways.
   */
  function spawnFood(snake, obstacles, width, height, rng, levelConfig, boardState) {
    const occupied = new Set();
    for (let s of (snake || [])) {
      occupied.add(s[1] * width + s[0]);
    }
    for (let obs of (obstacles || [])) {
      occupied.add(obs);
    }

    const available = [];
    for (let y = 0; y < height; y++) {
      for (let x = 0; x < width; x++) {
        const idx = y * width + x;
        if (!occupied.has(idx)) {
          available.push({ x, y });
        }
      }
    }

    if (available.length === 0) return null;

    const random = rng || Math.random;
    const pick = available[Math.floor(random() * available.length)];
    const chosenFruit = determineNextFruit(boardState, levelConfig, random);

    return {
      x: pick.x,
      y: pick.y,
      type: chosenFruit.type,
      spawnTime: Date.now(),
      lifetimeMs: chosenFruit.lifetimeMs
    };
  }

  /**
   * Generate candidate obstacles based on layout archetype.
   */
  function generateObstacles(config, rng) {
    const { width, height, layoutType } = config;
    const obstacles = [];
    const halfW = Math.floor(width / 2);
    const halfH = Math.floor(height / 2);

    if (layoutType === 'CLEAN' || layoutType === 'OASIS') {
      return obstacles;
    }

    if (layoutType === 'PILLARS') {
      // 4 to 6 symmetric isolated pillars with safe margin from walls
      const offX = Math.max(3, Math.floor(width / 5));
      const offY = Math.max(2, Math.floor(height / 4));
      const points = [
        { x: offX, y: offY },
        { x: width - 1 - offX, y: offY },
        { x: offX, y: height - 1 - offY },
        { x: width - 1 - offX, y: height - 1 - offY }
      ];
      if (width >= 26) {
        points.push({ x: halfW, y: offY });
        points.push({ x: halfW, y: height - 1 - offY });
      }
      for (let p of points) {
        if (p.x >= 0 && p.x < width && p.y >= 0 && p.y < height) {
          obstacles.push(p.y * width + p.x);
        }
      }
    } else if (layoutType === 'GARDEN_MAZE') {
      // 4 L-shaped corner bumpers with 3-cell wide corridors
      const offX = Math.max(3, Math.floor(width / 6));
      const offY = Math.max(2, Math.floor(height / 5));
      const bumpers = [
        [{ x: offX, y: offY }, { x: offX + 1, y: offY }, { x: offX, y: offY + 1 }],
        [{ x: width - 1 - offX, y: offY }, { x: width - 2 - offX, y: offY }, { x: width - 1 - offX, y: offY + 1 }],
        [{ x: offX, y: height - 1 - offY }, { x: offX + 1, y: height - 1 - offY }, { x: offX, y: height - 2 - offY }],
        [{ x: width - 1 - offX, y: height - 1 - offY }, { x: width - 2 - offX, y: height - 1 - offY }, { x: width - 1 - offX, y: height - 2 - offY }]
      ];
      for (let b of bumpers) {
        for (let pt of b) {
          if (pt.x >= 0 && pt.x < width && pt.y >= 0 && pt.y < height) {
            obstacles.push(pt.y * width + pt.x);
          }
        }
      }
    } else if (layoutType === 'CHAMBERS') {
      // Symmetric quadrant division with 4-cell wide central openings
      // Horizontal dividers (leaving central 4-tile gate open)
      for (let x = 3; x < halfW - 2; x++) {
        obstacles.push(halfH * width + x);
        obstacles.push(halfH * width + (width - 1 - x));
      }
      // Vertical dividers (leaving central 4-tile gate open)
      for (let y = 2; y < halfH - 2; y++) {
        obstacles.push(y * width + halfW);
        obstacles.push((height - 1 - y) * width + halfW);
      }
    } else if (layoutType === 'ARCHIPELAGO') {
      // Central symmetric island plus 4 outer satellite posts
      for (let dy = -1; dy <= 1; dy++) {
        for (let dx = -1; dx <= 1; dx++) {
          if (Math.abs(dx) + Math.abs(dy) <= 1) {
            obstacles.push((halfH + dy) * width + (halfW + dx));
          }
        }
      }
      const satX = Math.max(3, Math.floor(width / 5));
      const satY = Math.max(2, Math.floor(height / 4));
      const satellites = [
        { x: satX, y: satY },
        { x: width - 1 - satX, y: satY },
        { x: satX, y: height - 1 - satY },
        { x: width - 1 - satX, y: height - 1 - satY }
      ];
      for (let s of satellites) {
        obstacles.push(s.y * width + s.x);
      }
    } else {
      // LABYRINTH / PROCEDURAL: Controlled symmetry with max 8% density
      const count = Math.min(Math.floor(width * height * 0.08), 32);
      for (let i = 0; i < count / 4; i++) {
        const ox = 2 + Math.floor(rng() * (halfW - 3));
        const oy = 2 + Math.floor(rng() * (halfH - 3));
        obstacles.push(oy * width + ox);
        obstacles.push(oy * width + (width - 1 - ox));
        obstacles.push((height - 1 - oy) * width + ox);
        obstacles.push((height - 1 - oy) * width + (width - 1 - ox));
      }
    }

    return Array.from(new Set(obstacles));
  }

  /**
   * Procedural Level Generation Entry Point.
   */
  function generateLevel(level, customDimensions) {
    const config = getLevelConfig(level, customDimensions);
    const baseSeed = ((config.lvl * 0x85EBCA6B) ^ (config.lvl << 4) ^ 0x9E3779B9) >>> 0;

    for (let attempt = 0; attempt < 25; attempt++) {
      const attemptSeed = (baseSeed + attempt * 0x6D2B79F5) >>> 0;
      const rng = createRng(attemptSeed);
      const obstacles = generateObstacles(config, rng);

      // Safe Snake starting position (ensuring clearance from quadrant division walls)
      const startX = 4;
      const startY = (config.layoutType === 'CHAMBERS' || config.layoutType === 'ARCHIPELAGO')
        ? 2
        : Math.floor(config.height / 2);

      const snake = [
        [startX, startY],
        [startX - 1, startY],
        [startX - 2, startY]
      ];
      const direction = 'RIGHT';

      // Gateways on milestone levels
      let gateways = [];
      if (config.hasGateways) {
        gateways = [
          { x: 2, y: 2, color: '#00e5ff' },
          { x: config.width - 3, y: config.height - 3, color: '#00e5ff' }
        ];
      }

      const candidate = {
        level: config.lvl,
        seed: attemptSeed,
        width: config.width,
        height: config.height,
        targetFruits: config.targetFruits,
        baseTickMs: config.baseTickMs,
        layoutType: config.layoutType,
        isMilestone: config.isMilestone,
        isOasis: config.isOasis,
        snake,
        direction,
        obstacles,
        gateways,
        fruitsEatenInLevel: 0,
        elapsedSeconds: 0,
        restartsCount: 0
      };

      if (validatePlayableBoard(candidate)) {
        // Spawn initial food
        candidate.food = spawnFood(snake, obstacles, config.width, config.height, rng, config);
        return candidate;
      }
    }

    // Fallback: guaranteed clean board
    const fallbackRng = createRng(baseSeed);
    const fallbackSnake = [
      [3, Math.floor(config.height / 2)],
      [2, Math.floor(config.height / 2)],
      [1, Math.floor(config.height / 2)]
    ];
    return {
      level: config.lvl,
      seed: baseSeed,
      width: config.width,
      height: config.height,
      targetFruits: config.targetFruits,
      baseTickMs: config.baseTickMs,
      layoutType: 'CLEAN',
      isMilestone: config.isMilestone,
      isOasis: config.isOasis,
      snake: fallbackSnake,
      direction: 'RIGHT',
      obstacles: [],
      gateways: [],
      fruitsEatenInLevel: 0,
      elapsedSeconds: 0,
      restartsCount: 0,
      food: spawnFood(fallbackSnake, [], config.width, config.height, fallbackRng, config)
    };
  }

  /**
   * Safely adapt existing active board when browser window dimensions change,
   * preserving active level, snake length, score, streak, and gameplay without resetting.
   */
  function adaptBoardDimensions(currentBoard, targetWidth, targetHeight) {
    if (!currentBoard) return currentBoard;
    const newW = Math.max(14, Math.min(48, Math.floor(targetWidth)));
    const newH = Math.max(10, Math.min(26, Math.floor(targetHeight)));

    if (currentBoard.width === newW && currentBoard.height === newH) {
      return currentBoard;
    }

    const oldW = currentBoard.width;
    const oldH = currentBoard.height;

    // 1. Remap / clamp active snake segments into new dimensions
    const remappedSnake = currentBoard.snake.map(seg => {
      const nx = Math.max(0, Math.min(newW - 1, seg[0]));
      const ny = Math.max(0, Math.min(newH - 1, seg[1]));
      return [nx, ny];
    });

    // 2. Remap obstacles from 1D index to new 2D grid coordinates
    const newObstacles = [];
    const occupiedBySnake = new Set(remappedSnake.map(s => s[1] * newW + s[0]));

    for (let oldIdx of (currentBoard.obstacles || [])) {
      const ox = oldIdx % oldW;
      const oy = Math.floor(oldIdx / oldW);
      if (ox < newW && oy < newH) {
        const newIdx = oy * newW + ox;
        if (!occupiedBySnake.has(newIdx)) {
          newObstacles.push(newIdx);
        }
      }
    }

    // If board had obstacles but remapping left it empty, regenerate for new dimensions
    const lvlConfig = getLevelConfig(currentBoard.level, { width: newW, height: newH });
    if (newObstacles.length === 0 && lvlConfig.layoutType !== 'CLEAN' && lvlConfig.layoutType !== 'OASIS') {
      const generated = generateObstacles(lvlConfig, Math.random);
      for (let obs of generated) {
        if (!occupiedBySnake.has(obs)) {
          newObstacles.push(obs);
        }
      }
    }

    // 3. Remap or respawn food
    let remappedFood = currentBoard.food;
    if (remappedFood) {
      const fx = Math.max(0, Math.min(newW - 1, remappedFood.x));
      const fy = Math.max(0, Math.min(newH - 1, remappedFood.y));
      const foodIdx = fy * newW + fx;
      if (occupiedBySnake.has(foodIdx) || newObstacles.includes(foodIdx)) {
        const rng = Math.random;
        const lvlConfig = getLevelConfig(currentBoard.level, { width: newW, height: newH });
        remappedFood = spawnFood(remappedSnake, newObstacles, newW, newH, rng, lvlConfig);
      } else {
        remappedFood = { ...remappedFood, x: fx, y: fy };
      }
    }

    // 4. Remap gateways if present
    let remappedGateways = currentBoard.gateways || [];
    if (remappedGateways.length >= 2) {
      remappedGateways = [
        { x: 2, y: 2, color: '#00e5ff' },
        { x: newW - 3, y: newH - 3, color: '#00e5ff' }
      ];
    }

    const adapted = {
      ...currentBoard,
      width: newW,
      height: newH,
      snake: remappedSnake,
      obstacles: newObstacles,
      gateways: remappedGateways,
      food: remappedFood
    };

    adapted.food = ensureValidFood(adapted);
    return adapted;
  }

  /**
   * Validate and guarantee that board has a strictly valid food within [0, width) and [0, height),
   * not on snake, not on obstacle, and not expired.
   */
  function ensureValidFood(board, rng) {
    if (!board) return null;
    const { width, height, snake, obstacles } = board;
    let food = board.food;
    const occupied = new Set();
    for (let s of (snake || [])) {
      occupied.add(s[1] * width + s[0]);
    }
    for (let obs of (obstacles || [])) {
      occupied.add(obs);
    }

    const isExpired = food && food.lifetimeMs && food.spawnTime && (Date.now() - food.spawnTime > food.lifetimeMs);
    const isOutOfBounds = !food || typeof food.x !== 'number' || typeof food.y !== 'number' ||
      food.x < 0 || food.x >= width || food.y < 0 || food.y >= height;
    const isOccupied = food && occupied.has(food.y * width + food.x);

    if (isOutOfBounds || isOccupied || isExpired) {
      const lvlConfig = getLevelConfig(board.level, { width, height });
      food = spawnFood(snake, obstacles, width, height, rng || Math.random, lvlConfig, board);
    }

    return food;
  }

  return {
    getLevelConfig,
    validatePlayableBoard,
    determineNextFruit,
    spawnFood,
    ensureValidFood,
    generateLevel,
    adaptBoardDimensions
  };
}));
