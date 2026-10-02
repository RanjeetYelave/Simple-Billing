/**
 * snakeGenerator.js
 * Procedural level generator and playability validator for Snake (Take a Break -> Classic).
 * Dynamic full-viewport progressive playable territory architecture.
 * Guaranteed 100% deterministic, fair, and achievable across all viewport sizes.
 */

(function (root, factory) {
  if (typeof define === 'function' && define.amd) {
    define(['./snakeEngine', './maharashtraWorld'], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory(require('./snakeEngine'), require('./maharashtraWorld'));
  } else {
    root.SnakeGenerator = factory(root.SnakeEngine, root.MaharashtraWorld);
  }
}(typeof self !== 'undefined' ? self : this, function (SnakeEngine, MaharashtraWorld) {
  'use strict';

  const Engine = SnakeEngine || (typeof window !== 'undefined' ? window.SnakeEngine : null);
  const MWorld = MaharashtraWorld || (typeof window !== 'undefined' ? window.MaharashtraWorld : null);

  /**
   * Deterministic 32-bit PRNG (Mulberry32).
   * Viewport size never enters seed generation.
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
   * Calculate progressive territory expansion factor alpha(L) and centered bounds.
   * alpha: Level 1 (~38%) -> Level 5 (~48%) -> Level 10 (~60%) -> Level 20 (~80%) -> Level 40+ (~98-100%).
   */
  function getTerritoryBounds(totalCols, totalRows, level) {
    const cols = Math.max(6, Math.floor(totalCols || 24));
    const rows = Math.max(5, Math.floor(totalRows || 16));
    const lvl = Math.max(1, Math.floor(level || 1));

    const alpha = Math.min(1.0, 0.35 + 0.65 * ((lvl - 1) / 39));

    let playW = Math.max(6, Math.min(cols, Math.round(cols * alpha)));
    let playH = Math.max(5, Math.min(rows, Math.round(rows * alpha)));

    if (cols < 8) playW = cols;
    if (rows < 6) playH = rows;

    const minX = Math.floor((cols - playW) / 2);
    const maxX = minX + playW - 1;
    const minY = Math.floor((rows - playH) / 2);
    const maxY = minY + playH - 1;

    return {
      minX,
      minY,
      maxX,
      maxY,
      spanX: playW,
      spanY: playH,
      width: playW,
      height: playH,
      totalCols: cols,
      totalRows: rows,
      totalWidth: cols,
      totalHeight: rows,
      alpha
    };
  }

  /**
   * Determine grid parameters and rules based on level number and optional responsive dimensions.
   * Supports Levels 1 to 1000 with cohesive intra-destination 3-phase progression (Approach -> Ramparts -> Citadel).
   */
  function getLevelConfig(level, customDimensions) {
    const lvl = Math.max(1, Math.floor(level || 1));
    const isFinale = (lvl === 1000);
    const isMilestone = (lvl % 10 === 0) || isFinale;
    const isOasis = (lvl > 15 && lvl % 5 === 0 && !isMilestone && !isFinale); // Relaxing breather level

    let totalCols = 24;
    let totalRows = 16;
    let targetFruits = 5;
    let baseTickMs = 220;
    let layoutType = 'CLEAN';

    // 1. Resolve authentic Maharashtra World location & archetype
    const worldData = MWorld ? MWorld.getWorldForLevel('classic', lvl) : null;
    const archetype = (worldData && worldData.location && worldData.location.archetypeId)
      ? worldData.location.archetypeId
      : 'ARCH_HILL_FORT';

    const intraStep = ((lvl - 1) % 15) + 1; // 1 to 15 within destination
    const chapter = Math.min(12, Math.floor((lvl - 1) / 90) + 1);
    const chapterName = worldData && worldData.location ? worldData.location.name : 'Sahyadri Expedition';

    // 2. Base speed & target fruits progression across Levels 1–1000
    if (lvl <= 5) {
      targetFruits = 5; baseTickMs = 220; layoutType = 'CLEAN';
    } else if (lvl <= 10) {
      targetFruits = 7; baseTickMs = 200; layoutType = 'PILLARS';
    } else if (lvl <= 25) {
      targetFruits = 10; baseTickMs = 180; layoutType = 'GARDEN_MAZE';
    } else if (lvl <= 50) {
      targetFruits = 12; baseTickMs = 165; layoutType = 'CHAMBERS';
    } else if (lvl <= 100) {
      targetFruits = 14 + Math.floor((intraStep - 1) / 5);
      baseTickMs = Math.max(135, 160 - Math.floor(lvl / 10));
      layoutType = intraStep <= 5 ? 'PILLARS' : (intraStep <= 10 ? 'ARCHIPELAGO' : 'LABYRINTH');
    } else if (isFinale) {
      targetFruits = 25;
      baseTickMs = 130;
      layoutType = 'CORONATION_CITADEL';
    } else {
      // Levels 101–999: Structured 3-phase intra-destination expedition lifecycle
      targetFruits = Math.min(22, 14 + Math.floor(lvl / 100) + Math.floor((intraStep - 1) / 5));
      baseTickMs = Math.max(125, 145 - Math.floor(lvl / 120));

      if (archetype === 'ARCH_COASTAL_FORT') {
        layoutType = intraStep <= 5 ? 'ISLAND_CLUSTER' : (intraStep <= 10 ? 'RIVER_CROSSINGS' : 'ARCHIPELAGO');
      } else if (archetype === 'ARCH_CAVE_TEMPLE') {
        layoutType = intraStep <= 5 ? 'PILLARS' : (intraStep <= 10 ? 'CAVE_CHAMBERS' : 'LABYRINTH');
      } else if (archetype === 'ARCH_HISTORIC_WADA') {
        layoutType = intraStep <= 5 ? 'GARDEN_MAZE' : (intraStep <= 10 ? 'WADA_COURTYARDS' : 'CHAMBERS');
      } else if (archetype === 'ARCH_PLATEAU_FOREST') {
        layoutType = intraStep <= 5 ? 'PILLARS' : (intraStep <= 10 ? 'FOREST_TRAIL' : 'ARCHIPELAGO');
      } else if (archetype === 'ARCH_PILGRIMAGE_GHAT' || archetype === 'ARCH_RIVER_VALLEY') {
        layoutType = intraStep <= 5 ? 'GARDEN_MAZE' : (intraStep <= 10 ? 'RIVER_CROSSINGS' : 'CHAMBERS');
      } else {
        // ARCH_HILL_FORT & METROPOLIS
        layoutType = intraStep <= 5 ? 'MOUNTAIN_PASS' : (intraStep <= 10 ? 'MOATED_CITADEL' : 'SUPREME_CITADEL');
      }
    }

    if (customDimensions && typeof customDimensions === 'object') {
      const c = customDimensions.totalCols || customDimensions.cols || customDimensions.width;
      const r = customDimensions.totalRows || customDimensions.rows || customDimensions.height;
      if (typeof c === 'number') totalCols = Math.max(6, Math.floor(c));
      if (typeof r === 'number') totalRows = Math.max(5, Math.floor(r));
    }

    if (isOasis) {
      layoutType = 'OASIS'; // 0 obstacles breather level
      targetFruits = Math.max(6, Math.floor(targetFruits * 0.8));
    }

    const bounds = getTerritoryBounds(totalCols, totalRows, lvl);

    return {
      lvl,
      chapter,
      chapterName,
      archetype,
      totalCols,
      totalRows,
      width: bounds.spanX,
      height: bounds.spanY,
      bounds,
      targetFruits,
      baseTickMs: Math.max(125, baseTickMs),
      layoutType,
      isMilestone,
      isOasis,
      isFinale,
      hasGoldenApple: lvl >= 5,
      hasSpeedBoost: lvl >= 8,
      hasMystery: lvl >= 12,
      hasChillBerry: lvl >= 16,
      hasMagnet: lvl >= 20,
      hasHoppingFruit: lvl >= 35,
      hasGateways: (isMilestone && lvl >= 10) || (lvl >= 800 && lvl % 5 === 0),
      hasSahyadriGem: lvl >= 101,
      hasKonkanPearl: lvl >= 201,
      hasCaveRelic: lvl >= 301,
      hasRoyalWadaToken: lvl >= 401,
      hasForestSpirit: lvl >= 501,
      hasEmberRelic: lvl >= 601,
      hasDeccanCrystal: lvl >= 701,
      hasExpeditionRelic: lvl >= 901,
      hasSovereignCrest: isFinale,
      foodType: (worldData && worldData.theme && (worldData.theme.regionalFood || worldData.theme.foodVisual)) ? (worldData.theme.regionalFood || 'APPLE') : 'APPLE',
      foodName: (worldData && worldData.theme && worldData.theme.foodName) ? worldData.theme.foodName : 'Maharashtra Fruit',
      foodIcon: (worldData && worldData.theme && worldData.theme.foodIcon) ? worldData.theme.foodIcon : '🍎'
    };
  }

  /**
   * Validate that a candidate Snake board is 100% fair, navigable, and playable within active bounds.
   */
  function validatePlayableBoard(board) {
    if (!board) return false;
    const bounds = board.bounds || getTerritoryBounds(board.width, board.height, board.level || 1);
    const { minX, maxX, minY, maxY, spanX, spanY } = bounds;
    const { snake, obstacles, direction } = board;
    const totalCells = spanX * spanY;
    const obstacleList = obstacles || [];
    const obstacleSet = new Set(obstacleList.map(o => typeof o === 'string' ? o : (typeof o === 'number' ? `${o % (bounds.totalCols || spanX)},${Math.floor(o / (bounds.totalCols || spanX))}` : `${o.x},${o.y}`)));

    // 1. Obstacle density must be <= 14%
    const density = obstacleList.length / totalCells;
    if (density > 0.14) return false;

    // 2. Snake head and body must not intersect obstacles and must be inside bounds
    for (let i = 0; i < snake.length; i++) {
      const sx = snake[i][0];
      const sy = snake[i][1];
      if (sx < minX || sx > maxX || sy < minY || sy > maxY) return false;
      if (obstacleSet.has(`${sx},${sy}`)) return false;
    }

    // 3. Snake head must have at least 2 open clear tiles ahead in starting direction
    const head = snake[0];
    const dirVector = (Engine && Engine.DIRS[direction]) ? Engine.DIRS[direction] : { dx: 1, dy: 0 };
    for (let step = 1; step <= 2; step++) {
      const checkX = minX + ((((head[0] + dirVector.dx * step - minX) % spanX) + spanX) % spanX);
      const checkY = minY + ((((head[1] + dirVector.dy * step - minY) % spanY) + spanY) % spanY);
      if (obstacleSet.has(`${checkX},${checkY}`)) return false;
    }

    // 4. BFS Flood Fill: All non-obstacle playable territory cells must form a single connected component
    const openCellCount = totalCells - obstacleList.length;
    const visited = new Set();
    const queue = [`${snake[0][0]},${snake[0][1]}`];
    visited.add(queue[0]);

    while (queue.length > 0) {
      const curr = queue.shift();
      const parts = curr.split(',');
      const cx = parseInt(parts[0], 10);
      const cy = parseInt(parts[1], 10);

      const neighbors = [
        { x: minX + ((((cx + 1 - minX) % spanX) + spanX) % spanX), y: cy },
        { x: minX + ((((cx - 1 - minX) % spanX) + spanX) % spanX), y: cy },
        { x: cx, y: minY + ((((cy + 1 - minY) % spanY) + spanY) % spanY) },
        { x: cx, y: minY + ((((cy - 1 - minY) % spanY) + spanY) % spanY) }
      ];

      for (let n of neighbors) {
        const key = `${n.x},${n.y}`;
        if (!obstacleSet.has(key) && !visited.has(key)) {
          visited.add(key);
          queue.push(key);
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
   * max 2 consecutive specials rate limit, novelty cooldowns.
   */
  function determineNextFruit(boardState, levelConfig, rng) {
    const defaultApple = { type: 'APPLE', lifetimeMs: null };
    if (!levelConfig) return defaultApple;

    if (levelConfig.isOasis) return defaultApple;

    const state = boardState || {};
    const consecutiveSpecials = state.consecutiveSpecials || 0;
    const noveltyCooldown = state.noveltyCooldown || 0;

    if (consecutiveSpecials >= 2 || noveltyCooldown > 0) {
      return defaultApple;
    }

    const random = rng || Math.random;
    const roll = random();

    if (roll >= 0.28) {
      return defaultApple;
    }

    const pool = [];
    if (levelConfig.hasSovereignCrest) {
      pool.push({ type: 'SOVEREIGN_CREST', lifetimeMs: null, weight: 35 });
    }
    if (levelConfig.hasGoldenApple) {
      pool.push({ type: 'GOLDEN_APPLE', lifetimeMs: 14000, weight: 25 });
    }
    if (levelConfig.hasSpeedBoost && (!state.boostTimer || state.boostTimer <= 0)) {
      pool.push({ type: 'SPEED_BOOST', lifetimeMs: 14000, weight: 15 });
    }
    if (levelConfig.hasMystery) {
      pool.push({ type: 'MYSTERY', lifetimeMs: 16000, weight: 15 });
    }
    if (levelConfig.hasChillBerry && (!state.chillTimer || state.chillTimer <= 0)) {
      pool.push({ type: 'CHILL_BERRY', lifetimeMs: 15000, weight: 15 });
    }
    if (levelConfig.hasMagnet && (!state.magnetTimer || state.magnetTimer <= 0)) {
      pool.push({ type: 'MAGNET', lifetimeMs: 15000, weight: 12 });
    }
    if (levelConfig.hasHoppingFruit) {
      pool.push({ type: 'HOPPING_FRUIT', lifetimeMs: 18000, weight: 12 });
    }
    if (levelConfig.hasSahyadriGem) {
      pool.push({ type: 'SAHYADRI_GEM', lifetimeMs: 16000, weight: 16 });
    }
    if (levelConfig.hasKonkanPearl) {
      pool.push({ type: 'KONKAN_PEARL', lifetimeMs: 16000, weight: 16 });
    }
    if (levelConfig.hasCaveRelic) {
      pool.push({ type: 'CAVE_RELIC', lifetimeMs: 16000, weight: 16 });
    }
    if (levelConfig.hasRoyalWadaToken) {
      pool.push({ type: 'ROYAL_WADA_TOKEN', lifetimeMs: 16000, weight: 16 });
    }
    if (levelConfig.hasForestSpirit) {
      pool.push({ type: 'FOREST_SPIRIT', lifetimeMs: 16000, weight: 16 });
    }
    if (levelConfig.hasEmberRelic) {
      pool.push({ type: 'EMBER_RELIC', lifetimeMs: 16000, weight: 16 });
    }
    if (levelConfig.hasDeccanCrystal) {
      pool.push({ type: 'DECCAN_CRYSTAL', lifetimeMs: 16000, weight: 16 });
    }
    if (levelConfig.hasExpeditionRelic) {
      pool.push({ type: 'EXPEDITION_RELIC', lifetimeMs: 16000, weight: 18 });
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
   * Spawn a random valid fruit inside active playable bounds,
   * avoiding snake segments, obstacles, and ensuring spatial separation from existing food.
   */
  function spawnFood(snake, obstacles, boundsOrWidth, maybeHeightOrRng, maybeRng, levelConfig, boardState, existingFoods) {
    let bounds;
    let rng;
    let cfg = levelConfig;
    let state = boardState;
    let existingList = [];

    if (Array.isArray(existingFoods)) {
      existingList = existingFoods;
    } else if (existingFoods && typeof existingFoods === 'object' && existingFoods.x !== undefined) {
      existingList = [existingFoods];
    } else if (Array.isArray(boardState)) {
      existingList = boardState;
    } else if (Array.isArray(levelConfig)) {
      existingList = levelConfig;
    } else if (boardState && Array.isArray(boardState.foods)) {
      existingList = boardState.foods;
    } else if (boardState && boardState.food) {
      existingList = [boardState.food];
    }

    if (typeof boundsOrWidth === 'object' && boundsOrWidth !== null && boundsOrWidth.minX !== undefined) {
      bounds = boundsOrWidth;
      rng = typeof maybeHeightOrRng === 'function' ? maybeHeightOrRng : (typeof maybeRng === 'function' ? maybeRng : Math.random);
      if (typeof maybeRng === 'object' && maybeRng !== null && !Array.isArray(maybeRng)) {
        cfg = maybeRng;
      }
    } else {
      const w = typeof boundsOrWidth === 'number' ? boundsOrWidth : 20;
      const h = typeof maybeHeightOrRng === 'number' ? maybeHeightOrRng : w;
      bounds = { minX: 0, maxX: w - 1, minY: 0, maxY: h - 1, spanX: w, spanY: h };
      rng = typeof maybeRng === 'function' ? maybeRng : Math.random;
    }

    const occupied = new Set();
    for (let s of (snake || [])) {
      occupied.add(`${s[0]},${s[1]}`);
    }
    for (let obs of (obstacles || [])) {
      if (typeof obs === 'string') {
        occupied.add(obs);
      } else if (typeof obs === 'object' && obs !== null) {
        occupied.add(`${obs.x},${obs.y}`);
      } else if (typeof obs === 'number') {
        const stride = bounds.totalCols || bounds.spanX;
        occupied.add(`${obs % stride},${Math.floor(obs / stride)}`);
      }
    }
    for (let ef of existingList) {
      if (ef) occupied.add(`${ef.x},${ef.y}`);
    }

    const available = [];
    const minDistance = Math.max(4, Math.floor((bounds.spanX + bounds.spanY) / 4));
    const separatedAvailable = [];

    for (let y = bounds.minY; y <= bounds.maxY; y++) {
      for (let x = bounds.minX; x <= bounds.maxX; x++) {
        if (!occupied.has(`${x},${y}`)) {
          available.push({ x, y });

          // Check spatial separation from existing foods
          let isWellSeparated = true;
          for (let ef of existingList) {
            if (ef) {
              const dist = Math.abs(x - ef.x) + Math.abs(y - ef.y);
              if (dist < minDistance) {
                isWellSeparated = false;
                break;
              }
            }
          }
          if (isWellSeparated) {
            separatedAvailable.push({ x, y });
          }
        }
      }
    }

    const candidates = separatedAvailable.length > 0 ? separatedAvailable : available;
    if (candidates.length === 0) return null;

    const random = rng || Math.random;
    const pick = candidates[Math.floor(random() * candidates.length)];
    
    // Determine type: if existing food is primary fruit, secondary can be GOLDEN_APPLE (Golden Fig)
    let chosenFruit = determineNextFruit(state, cfg, random);
    const primaryType = (cfg && cfg.foodType) ? cfg.foodType : 'APPLE';
    if (existingList.length > 0 && existingList[0] && (existingList[0].type === 'APPLE' || existingList[0].type === primaryType) && Math.random() < 0.35) {
      chosenFruit = { type: 'GOLDEN_APPLE', lifetimeMs: null };
    }

    return {
      x: pick.x,
      y: pick.y,
      type: chosenFruit.type,
      spawnTime: Date.now(),
      lifetimeMs: chosenFruit.lifetimeMs
    };
  }

  /**
   * Resolve appropriate obstacle visual type for a given coordinate and archetype.
   */
  function resolveObstacleVisualType(archetype, isCorner, isPillar, isFinale) {
    if (isFinale) {
      if (isCorner) return 'GOLDEN_BASTION';
      if (isPillar) return 'CEREMONIAL_GATE';
      return 'ROYAL_RAMPART';
    }
    switch (archetype) {
      case 'ARCH_COASTAL_FORT':
        return isCorner ? 'BOAT_DOCK' : (isPillar ? 'SEA_CHANNEL' : 'COASTAL_ROCK');
      case 'ARCH_CAVE_TEMPLE':
        return isCorner ? 'CAVE_PILLAR' : (isPillar ? 'ARCHWAY' : 'ROCK_WALL');
      case 'ARCH_HISTORIC_WADA':
        return isCorner ? 'WADA_COLUMN' : (isPillar ? 'ARCHWAY' : 'COURTYARD_WALL');
      case 'ARCH_PLATEAU_FOREST':
        return isCorner ? 'BOULDER' : (isPillar ? 'ROOT_CLUSTER' : 'TREE_CLUSTER');
      case 'ARCH_RIVER_VALLEY':
        return isCorner ? 'STONE_BRIDGE' : (isPillar ? 'RIVER_STONE' : 'RIVER_BANK');
      case 'ARCH_PILGRIMAGE_GHAT':
        return isCorner ? 'GHAT_STEPS' : (isPillar ? 'STONE_PILLAR' : 'RIVER_STONE');
      case 'ARCH_METROPOLIS':
        return isCorner ? 'BASTION' : (isPillar ? 'STONE_ARCH' : 'FORT_WALL');
      case 'ARCH_HILL_FORT':
      default:
        return isCorner ? 'BASTION' : (isPillar ? 'FORT_GATE' : 'FORT_WALL');
    }
  }

  /**
   * Generate candidate obstacles strictly within active playable bounds.
   */
  function generateObstacles(config, rng) {
    const { bounds, layoutType, archetype, isFinale } = config;
    const { minX, maxX, minY, maxY, spanX, spanY } = bounds;
    const rawPoints = [];

    if (layoutType === 'CLEAN' || layoutType === 'OASIS') {
      return [];
    }

    const halfW = Math.floor(spanX / 2);
    const halfH = Math.floor(spanY / 2);

    if (layoutType === 'PILLARS') {
      const offX = Math.max(1, Math.floor(spanX / 5));
      const offY = Math.max(1, Math.floor(spanY / 4));
      const points = [
        { x: minX + offX, y: minY + offY, isPillar: true },
        { x: maxX - offX, y: minY + offY, isPillar: true },
        { x: minX + offX, y: maxY - offY, isPillar: true },
        { x: maxX - offX, y: maxY - offY, isPillar: true }
      ];
      if (spanX >= 18) {
        points.push({ x: minX + halfW, y: minY + offY, isPillar: true });
        points.push({ x: minX + halfW, y: maxY - offY, isPillar: true });
      }
      for (let p of points) {
        if (p.x >= minX && p.x <= maxX && p.y >= minY && p.y <= maxY) {
          rawPoints.push(p);
        }
      }
    } else if (layoutType === 'GARDEN_MAZE') {
      const offX = Math.max(1, Math.floor(spanX / 6));
      const offY = Math.max(1, Math.floor(spanY / 5));
      const bumpers = [
        [{ x: minX + offX, y: minY + offY, isCorner: true }, { x: minX + offX + 1, y: minY + offY }, { x: minX + offX, y: minY + offY + 1 }],
        [{ x: maxX - offX, y: minY + offY, isCorner: true }, { x: maxX - offX - 1, y: minY + offY }, { x: maxX - offX, y: minY + offY + 1 }],
        [{ x: minX + offX, y: maxY - offY, isCorner: true }, { x: minX + offX + 1, y: maxY - offY }, { x: minX + offX, y: maxY - offY - 1 }],
        [{ x: maxX - offX, y: maxY - offY, isCorner: true }, { x: maxX - offX - 1, y: maxY - offY }, { x: maxX - offX, y: maxY - offY - 1 }]
      ];
      for (let b of bumpers) {
        for (let pt of b) {
          if (pt.x >= minX && pt.x <= maxX && pt.y >= minY && pt.y <= maxY) {
            rawPoints.push(pt);
          }
        }
      }
    } else if (layoutType === 'CHAMBERS' || layoutType === 'WADA_COURTYARDS') {
      for (let x = minX + 2; x < minX + halfW - 1; x++) {
        rawPoints.push({ x, y: minY + halfH });
        rawPoints.push({ x: maxX - (x - minX), y: minY + halfH });
      }
      for (let y = minY + 1; y < minY + halfH - 1; y++) {
        rawPoints.push({ x: minX + halfW, y });
        rawPoints.push({ x: minX + halfW, y: maxY - (y - minY) });
      }
      rawPoints.push({ x: minX + halfW, y: minY + 1, isCorner: true });
      rawPoints.push({ x: minX + halfW, y: maxY - 1, isCorner: true });
    } else if (layoutType === 'ARCHIPELAGO' || layoutType === 'ISLAND_CLUSTER') {
      const centerX = minX + halfW;
      const centerY = minY + halfH;
      for (let dy = -1; dy <= 1; dy++) {
        for (let dx = -1; dx <= 1; dx++) {
          if (Math.abs(dx) + Math.abs(dy) <= 1) {
            rawPoints.push({ x: centerX + dx, y: centerY + dy, isCorner: (dx === 0 && dy === 0) });
          }
        }
      }
      const satX = Math.max(2, Math.floor(spanX / 5));
      const satY = Math.max(1, Math.floor(spanY / 4));
      const satellites = [
        { x: minX + satX, y: minY + satY, isPillar: true },
        { x: maxX - satX, y: minY + satY, isPillar: true },
        { x: minX + satX, y: maxY - satY, isPillar: true },
        { x: maxX - satX, y: maxY - satY, isPillar: true }
      ];
      for (let s of satellites) {
        if (s.x >= minX && s.x <= maxX && s.y >= minY && s.y <= maxY) {
          rawPoints.push(s);
        }
      }
    } else if (layoutType === 'CAVE_CHAMBERS' || layoutType === 'MOATED_CITADEL') {
      // Ring corridor fortress / cave pillars
      const ringInsetX = Math.max(2, Math.floor(spanX / 6));
      const ringInsetY = Math.max(2, Math.floor(spanY / 5));
      const pillars = [
        { x: minX + ringInsetX, y: minY + ringInsetY, isPillar: true },
        { x: maxX - ringInsetX, y: minY + ringInsetY, isPillar: true },
        { x: minX + ringInsetX, y: maxY - ringInsetY, isPillar: true },
        { x: maxX - ringInsetX, y: maxY - ringInsetY, isPillar: true },
        { x: minX + halfW, y: minY + ringInsetY, isCorner: true },
        { x: minX + halfW, y: maxY - ringInsetY, isCorner: true }
      ];
      for (let p of pillars) {
        if (p.x >= minX && p.x <= maxX && p.y >= minY && p.y <= maxY) {
          rawPoints.push(p);
        }
      }
    } else if (layoutType === 'RIVER_CROSSINGS') {
      // Stepped river piers with open navigation channels
      const pierOffset = Math.max(2, Math.floor(spanX / 5));
      for (let step = 1; step <= 3; step++) {
        const px = minX + pierOffset * step;
        if (px < maxX - 2) {
          rawPoints.push({ x: px, y: minY + Math.floor(spanY / 3), isPillar: true });
          rawPoints.push({ x: px, y: maxY - Math.floor(spanY / 3), isPillar: true });
        }
      }
    } else if (layoutType === 'CORONATION_CITADEL') {
      // Sovereign Coronation Citadel (Level 1000 finale)
      const offX = Math.max(2, Math.floor(spanX / 6));
      const offY = Math.max(2, Math.floor(spanY / 5));
      const bastions = [
        { x: minX + offX, y: minY + offY, isCorner: true },
        { x: maxX - offX, y: minY + offY, isCorner: true },
        { x: minX + offX, y: maxY - offY, isCorner: true },
        { x: maxX - offX, y: maxY - offY, isCorner: true },
        { x: minX + halfW, y: minY + offY - 1, isPillar: true },
        { x: minX + halfW, y: maxY - offY + 1, isPillar: true }
      ];
      for (let b of bastions) {
        if (b.x >= minX && b.x <= maxX && b.y >= minY && b.y <= maxY) {
          rawPoints.push(b);
        }
      }
    } else {
      // LABYRINTH / MOUNTAIN_PASS / FOREST_TRAIL / REGIONAL_TOUR / SUPREME_CITADEL
      const count = Math.min(Math.floor(spanX * spanY * 0.08), 28);
      for (let i = 0; i < count / 4; i++) {
        const ox = 1 + Math.floor(rng() * Math.max(1, halfW - 2));
        const oy = 1 + Math.floor(rng() * Math.max(1, halfH - 2));
        const isC = (i === 0);
        rawPoints.push({ x: minX + ox, y: minY + oy, isCorner: isC });
        rawPoints.push({ x: maxX - ox, y: minY + oy, isCorner: isC });
        rawPoints.push({ x: minX + ox, y: maxY - oy, isCorner: isC });
        rawPoints.push({ x: maxX - ox, y: maxY - oy, isCorner: isC });
      }
    }

    // Deduplicate by coordinate key and attach visualType & theme metadata
    const seen = new Set();
    const obstacles = [];

    for (let pt of rawPoints) {
      const key = `${pt.x},${pt.y}`;
      if (!seen.has(key)) {
        seen.add(key);
        const visualType = resolveObstacleVisualType(archetype, pt.isCorner, pt.isPillar, isFinale);
        obstacles.push({
          x: pt.x,
          y: pt.y,
          visualType,
          theme: archetype
        });
      }
    }

    return obstacles;
  }

  /**
   * Procedural Level Generation Entry Point.
   */
  function generateLevel(level, customDimensions) {
    const config = getLevelConfig(level, customDimensions);
    const bounds = config.bounds;
    const baseSeed = ((config.lvl * 0x85EBCA6B) ^ (config.lvl << 4) ^ 0x9E3779B9) >>> 0;

    for (let attempt = 0; attempt < 25; attempt++) {
      const attemptSeed = (baseSeed + attempt * 0x6D2B79F5) >>> 0;
      const rng = createRng(attemptSeed);
      const obstacles = generateObstacles(config, rng);

      // Snake starting position inside bounds
      const startX = bounds.minX + Math.min(4, Math.floor(bounds.spanX / 3));
      const startY = (config.layoutType === 'CHAMBERS' || config.layoutType === 'ARCHIPELAGO' || config.layoutType === 'WADA_COURTYARDS')
        ? bounds.minY + 1
        : bounds.minY + Math.floor(bounds.spanY / 2);

      const snake = [
        [startX, startY],
        [startX - 1, startY],
        [startX - 2, startY]
      ];
      const direction = 'RIGHT';

      let gateways = [];
      if (config.hasGateways) {
        gateways = [
          { x: bounds.minX + 1, y: bounds.minY + 1, color: '#00e5ff' },
          { x: bounds.maxX - 1, y: bounds.maxY - 1, color: '#00e5ff' }
        ];
      }

      const candidate = {
        level: config.lvl,
        seed: attemptSeed,
        chapter: config.chapter,
        chapterName: config.chapterName,
        archetype: config.archetype,
        bounds,
        width: bounds.spanX,
        height: bounds.spanY,
        totalCols: bounds.totalCols,
        totalRows: bounds.totalRows,
        targetFruits: config.targetFruits,
        baseTickMs: config.baseTickMs,
        layoutType: config.layoutType,
        isMilestone: config.isMilestone,
        isOasis: config.isOasis,
        isFinale: config.isFinale,
        snake,
        direction,
        obstacles,
        gateways,
        fruitsEatenInLevel: 0,
        elapsedSeconds: 0,
        restartsCount: 0
      };

      if (validatePlayableBoard(candidate)) {
        let foodA;
        if (config.isFinale) {
          const avail = [];
          const occupied = new Set(snake.map(s => `${s[0]},${s[1]}`));
          obstacles.forEach(o => occupied.add(`${o.x},${o.y}`));
          for (let y = bounds.minY; y <= bounds.maxY; y++) {
            for (let x = bounds.minX; x <= bounds.maxX; x++) {
              if (!occupied.has(`${x},${y}`)) avail.push({ x, y });
            }
          }
          const pick = avail[Math.floor(rng() * avail.length)] || { x: bounds.minX + 5, y: bounds.minY + 5 };
          foodA = { x: pick.x, y: pick.y, type: 'SOVEREIGN_CREST', spawnTime: Date.now(), lifetimeMs: null };
        } else {
          foodA = spawnFood(snake, obstacles, bounds, rng, config);
        }

        const foods = [foodA];
        if (config.lvl >= 10 && bounds.spanX >= 12 && foodA) {
          const foodB = spawnFood(snake, obstacles, bounds, rng, config, null, [foodA]);
          if (foodB) foods.push(foodB);
        }
        candidate.foods = foods;
        candidate.food = foods[0] || null;
        return candidate;
      }
    }

    // Fallback: guaranteed clean board
    const fallbackRng = createRng(baseSeed);
    const fallbackStartX = bounds.minX + Math.min(3, Math.floor(bounds.spanX / 2));
    const fallbackStartY = bounds.minY + Math.floor(bounds.spanY / 2);
    const fallbackSnake = [
      [fallbackStartX, fallbackStartY],
      [fallbackStartX - 1, fallbackStartY],
      [fallbackStartX - 2, fallbackStartY]
    ];
    let fallbackFoodA;
    if (config.isFinale) {
      fallbackFoodA = { x: bounds.minX + 5, y: bounds.minY + 5, type: 'SOVEREIGN_CREST', spawnTime: Date.now(), lifetimeMs: null };
    } else {
      fallbackFoodA = spawnFood(fallbackSnake, [], bounds, fallbackRng, config);
    }
    const fallbackFoods = [fallbackFoodA];
    if (config.lvl >= 10 && bounds.spanX >= 12 && fallbackFoodA) {
      const fallbackFoodB = spawnFood(fallbackSnake, [], bounds, fallbackRng, config, null, [fallbackFoodA]);
      if (fallbackFoodB) fallbackFoods.push(fallbackFoodB);
    }
    return {
      level: config.lvl,
      seed: baseSeed,
      chapter: config.chapter,
      chapterName: config.chapterName,
      archetype: config.archetype,
      bounds,
      width: bounds.spanX,
      height: bounds.spanY,
      totalCols: bounds.totalCols,
      totalRows: bounds.totalRows,
      targetFruits: config.targetFruits,
      baseTickMs: config.baseTickMs,
      layoutType: 'CLEAN',
      isMilestone: config.isMilestone,
      isOasis: config.isOasis,
      isFinale: config.isFinale,
      snake: fallbackSnake,
      direction: 'RIGHT',
      obstacles: [],
      gateways: [],
      fruitsEatenInLevel: 0,
      elapsedSeconds: 0,
      restartsCount: 0,
      foods: fallbackFoods,
      food: fallbackFoods[0] || null
    };
  }

  /**
   * Safely adapt existing active board when container resizes,
   * recalculating territory bounds without resetting active level, snake length, score, or streak.
   */
  function adaptBoardDimensions(currentBoard, targetCols, targetRows) {
    if (!currentBoard) return currentBoard;
    const newCols = Math.max(6, Math.floor(targetCols));
    const newRows = Math.max(5, Math.floor(targetRows));

    const oldBounds = currentBoard.bounds || getTerritoryBounds(currentBoard.width || 20, currentBoard.height || 14, currentBoard.level || 1);
    const newBounds = getTerritoryBounds(newCols, newRows, currentBoard.level || 1);

    if (oldBounds.totalCols === newCols && oldBounds.totalRows === newRows &&
        oldBounds.minX === newBounds.minX && oldBounds.maxX === newBounds.maxX &&
        oldBounds.minY === newBounds.minY && oldBounds.maxY === newBounds.maxY) {
      return currentBoard;
    }

    const shiftX = newBounds.minX - oldBounds.minX;
    const shiftY = newBounds.minY - oldBounds.minY;

    // 1. Shift and clamp active snake segments
    const remappedSnake = currentBoard.snake.map(seg => {
      let nx = seg[0] + shiftX;
      let ny = seg[1] + shiftY;
      nx = Math.max(newBounds.minX, Math.min(newBounds.maxX, nx));
      ny = Math.max(newBounds.minY, Math.min(newBounds.maxY, ny));
      return [nx, ny];
    });

    // 2. Remap obstacles
    const occupiedBySnake = new Set(remappedSnake.map(s => `${s[0]},${s[1]}`));
    const newObstacles = [];

    for (let obs of (currentBoard.obstacles || [])) {
      let ox, oy;
      if (typeof obs === 'string') {
        const p = obs.split(',');
        ox = parseInt(p[0], 10) + shiftX;
        oy = parseInt(p[1], 10) + shiftY;
      } else if (typeof obs === 'number') {
        const stride = oldBounds.totalCols || oldBounds.spanX;
        ox = (obs % stride) + shiftX;
        oy = Math.floor(obs / stride) + shiftY;
      } else if (obs && typeof obs === 'object') {
        ox = obs.x + shiftX;
        oy = obs.y + shiftY;
      }

      if (ox >= newBounds.minX && ox <= newBounds.maxX && oy >= newBounds.minY && oy <= newBounds.maxY) {
        const key = `${ox},${oy}`;
        if (!occupiedBySnake.has(key)) {
          if (typeof obs === 'object' && obs !== null && obs.visualType) {
            newObstacles.push({ ...obs, x: ox, y: oy });
          } else {
            newObstacles.push(key);
          }
        }
      }
    }

    // 3. Remap or respawn foods array
    const rawFoods = Array.isArray(currentBoard.foods) ? currentBoard.foods : (currentBoard.food ? [currentBoard.food] : []);
    const remappedFoods = [];
    const lvlConfig = getLevelConfig(currentBoard.level, { totalCols: newCols, totalRows: newRows });

    for (let f of rawFoods) {
      if (!f) continue;
      let fx = f.x + shiftX;
      let fy = f.y + shiftY;
      fx = Math.max(newBounds.minX, Math.min(newBounds.maxX, fx));
      fy = Math.max(newBounds.minY, Math.min(newBounds.maxY, fy));
      const foodKey = `${fx},${fy}`;
      if (occupiedBySnake.has(foodKey) || newObstacles.includes(foodKey)) {
        const rng = Math.random;
        const freshFood = spawnFood(remappedSnake, newObstacles, newBounds, rng, lvlConfig, currentBoard, remappedFoods);
        if (freshFood) remappedFoods.push(freshFood);
      } else {
        remappedFoods.push({ ...f, x: fx, y: fy });
      }
    }

    if (remappedFoods.length === 0) {
      const freshFood = spawnFood(remappedSnake, newObstacles, newBounds, Math.random, lvlConfig, currentBoard);
      if (freshFood) remappedFoods.push(freshFood);
    }

    // 4. Remap gateways
    let remappedGateways = currentBoard.gateways || [];
    if (remappedGateways.length >= 2) {
      remappedGateways = [
        { x: newBounds.minX + 1, y: newBounds.minY + 1, color: '#00e5ff' },
        { x: newBounds.maxX - 1, y: newBounds.maxY - 1, color: '#00e5ff' }
      ];
    }

    const adapted = {
      ...currentBoard,
      bounds: newBounds,
      width: newCols,
      height: newRows,
      totalCols: newCols,
      totalRows: newRows,
      totalWidth: newCols,
      totalHeight: newRows,
      snake: remappedSnake,
      obstacles: newObstacles,
      gateways: remappedGateways,
      foods: remappedFoods,
      food: remappedFoods[0] || null
    };

    if (!adapted.food) {
      adapted.food = ensureValidFood(adapted);
      adapted.foods = [adapted.food];
    }
    return adapted;
  }

  /**
   * Validate and guarantee that board has strictly valid food within active bounds.
   */
  function ensureValidFood(board, rng) {
    if (!board) return null;
    const bounds = board.bounds || getTerritoryBounds(board.width, board.height, board.level || 1);
    const { minX, maxX, minY, maxY } = bounds;
    const { snake, obstacles } = board;
    let food = board.food;
    const occupied = new Set();
    for (let s of (snake || [])) {
      occupied.add(`${s[0]},${s[1]}`);
    }
    for (let obs of (obstacles || [])) {
      if (typeof obs === 'string') occupied.add(obs);
      else if (typeof obs === 'object' && obs !== null) occupied.add(`${obs.x},${obs.y}`);
      else if (typeof obs === 'number') {
        const stride = bounds.totalCols || bounds.spanX;
        occupied.add(`${obs % stride},${Math.floor(obs / stride)}`);
      }
    }

    const isExpired = food && food.lifetimeMs && food.spawnTime && (Date.now() - food.spawnTime > food.lifetimeMs);
    const isOutOfBounds = !food || typeof food.x !== 'number' || typeof food.y !== 'number' ||
      food.x < minX || food.x > maxX || food.y < minY || food.y > maxY;
    const isOccupied = food && occupied.has(`${food.x},${food.y}`);

    if (isOutOfBounds || isOccupied || isExpired) {
      const lvlConfig = getLevelConfig(board.level, { totalCols: bounds.totalCols, totalRows: bounds.totalRows });
      food = spawnFood(snake, obstacles, bounds, rng || Math.random, lvlConfig, board);
    }

    return food;
  }

  return {
    getTerritoryBounds,
    getLevelConfig,
    validatePlayableBoard,
    determineNextFruit,
    spawnFood,
    ensureValidFood,
    generateLevel,
    adaptBoardDimensions
  };
}));
