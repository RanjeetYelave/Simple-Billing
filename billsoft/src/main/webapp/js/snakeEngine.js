/**
 * snakeEngine.js
 * Core mathematical grid, bounded & toroidal edge-wrapping, collision detection,
 * power-up mechanics (Gold Apple, Chill Berry, Speed Boost, Mystery Box, Magnet),
 * combo streaks, and scoring state machine for Snake (Take a Break -> Classic).
 * 100% self-contained, zero-dependency, and independently testable.
 */

(function (root, factory) {
  if (typeof define === 'function' && define.amd) {
    define([], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  } else {
    root.SnakeEngine = factory();
  }
}(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  // Direction Vectors
  const DIRS = {
    UP:    { dx: 0, dy: -1, opp: 'DOWN' },
    DOWN:  { dx: 0, dy: 1,  opp: 'UP' },
    LEFT:  { dx: -1, dy: 0, opp: 'RIGHT' },
    RIGHT: { dx: 1, dy: 0,  opp: 'LEFT' }
  };

  const FRUIT_TYPES = {
    APPLE: 'APPLE',
    STRAWBERRY: 'STRAWBERRY',
    ALPHONSO_MANGO: 'ALPHONSO_MANGO',
    NAGPUR_ORANGE: 'NAGPUR_ORANGE',
    PURPLE_GRAPES: 'PURPLE_GRAPES',
    MODAK: 'MODAK',
    TENDER_COCONUT: 'TENDER_COCONUT',
    KOLHAPURI_JAGGERY: 'KOLHAPURI_JAGGERY',
    CUSTARD_APPLE: 'CUSTARD_APPLE',
    JALGAON_BANANA: 'JALGAON_BANANA',
    PRASAAD_PEDA: 'PRASAAD_PEDA',
    BHAKRI_POHA: 'BHAKRI_POHA',
    WAI_GUAVA: 'WAI_GUAVA',
    POMEGRANATE: 'POMEGRANATE',
    JAMUN_FRUIT: 'JAMUN_FRUIT',
    CASHEW_FRUIT: 'CASHEW_FRUIT',
    SUGARCANE_BUNDLE: 'SUGARCANE_BUNDLE',
    GOLDEN_APPLE: 'GOLDEN_APPLE',
    CHILL_BERRY: 'CHILL_BERRY',
    SPEED_BOOST: 'SPEED_BOOST',
    MYSTERY: 'MYSTERY',
    MAGNET: 'MAGNET',
    HOPPING_FRUIT: 'HOPPING_FRUIT',
    SAHYADRI_GEM: 'SAHYADRI_GEM',
    KONKAN_PEARL: 'KONKAN_PEARL',
    CAVE_RELIC: 'CAVE_RELIC',
    ROYAL_WADA_TOKEN: 'ROYAL_WADA_TOKEN',
    FOREST_SPIRIT: 'FOREST_SPIRIT',
    EMBER_RELIC: 'EMBER_RELIC',
    DECCAN_CRYSTAL: 'DECCAN_CRYSTAL',
    EXPEDITION_RELIC: 'EXPEDITION_RELIC',
    SOVEREIGN_CREST: 'SOVEREIGN_CREST'
  };

  /**
   * Non-compounding intra-level speed progression formula.
   * progressRatio = fruitsEatenInLevel / targetFruits
   * intraMultiplier = 1.0 - (0.10 * progressRatio)
   * effectiveTickMs = max(75, round(baseTickMs * intraMultiplier * speedModifier))
   */
  function calculateIntraLevelTickMs(baseTickMs, fruitsEaten, targetFruits, speedModifier) {
    const bMs = Math.max(75, baseTickMs || 200);
    const target = Math.max(1, targetFruits || 5);
    const eaten = Math.max(0, Math.min(target, fruitsEaten || 0));
    const progressRatio = eaten / target;
    const intraMultiplier = 1.0 - (0.10 * progressRatio);
    const modifier = typeof speedModifier === 'number' ? speedModifier : 1.0;
    const effectiveTickMs = Math.max(75, Math.round(bMs * intraMultiplier * modifier));
    return effectiveTickMs;
  }

  /**
   * Extract normalized integer logical territory bounds from state.
   */
  function getEffectiveBounds(state) {
    const src = (state && state.bounds) ? state.bounds : (state || {});
    const minX = src.minX !== undefined ? src.minX : 0;
    const minY = src.minY !== undefined ? src.minY : 0;
    const maxX = src.maxX !== undefined ? src.maxX : (src.width !== undefined ? minX + src.width - 1 : minX + 15);
    const maxY = src.maxY !== undefined ? src.maxY : (src.height !== undefined ? minY + src.height - 1 : minY + 11);
    const spanX = Math.max(1, maxX - minX + 1);
    const spanY = Math.max(1, maxY - minY + 1);
    const totalWidth = (state && typeof state.totalWidth === 'number') ? state.totalWidth : (src.totalWidth || src.width || spanX);
    const totalHeight = (state && typeof state.totalHeight === 'number') ? state.totalHeight : (src.totalHeight || src.height || spanY);
    return { minX, minY, maxX, maxY, spanX, spanY, totalWidth, totalHeight };
  }

  /**
   * Wrap coordinate toroidally around logical territory boundary.
   * Supports both bounded objects ({ minX, maxX, minY, maxY }) and raw (width, height).
   */
  function wrapCoordinate(x, y, widthOrBounds, maybeHeight) {
    if (typeof widthOrBounds === 'object' && widthOrBounds !== null) {
      const bounds = getEffectiveBounds(widthOrBounds);
      const wrappedX = bounds.minX + ((((x - bounds.minX) % bounds.spanX) + bounds.spanX) % bounds.spanX);
      const wrappedY = bounds.minY + ((((y - bounds.minY) % bounds.spanY) + bounds.spanY) % bounds.spanY);
      return { x: wrappedX, y: wrappedY };
    }
    const width = typeof widthOrBounds === 'number' ? Math.max(1, widthOrBounds) : 20;
    const height = typeof maybeHeight === 'number' ? Math.max(1, maybeHeight) : width;
    const wrappedX = ((x % width) + width) % width;
    const wrappedY = ((y % height) + height) % height;
    return { x: wrappedX, y: wrappedY };
  }

  /**
   * Check if a 2D coordinate is inside an obstacle set or array.
   */
  function isObstacle(x, y, obstacleSet, width) {
    if (!obstacleSet) return false;
    if (obstacleSet instanceof Set) {
      if (obstacleSet.has(`${x},${y}`)) return true;
      if (typeof width === 'number' && obstacleSet.has(y * width + x)) return true;
      for (let obs of obstacleSet) {
        if (obs && typeof obs === 'object') {
          if (obs.x === x && obs.y === y) return true;
        }
      }
      return false;
    }
    if (Array.isArray(obstacleSet)) {
      const coordStr = `${x},${y}`;
      for (let i = 0; i < obstacleSet.length; i++) {
        const obs = obstacleSet[i];
        if (typeof obs === 'number' && typeof width === 'number' && obs === y * width + x) return true;
        if (typeof obs === 'string' && obs === coordStr) return true;
        if (obs && typeof obs === 'object') {
          if (Array.isArray(obs) && obs[0] === x && obs[1] === y) return true;
          if (obs.x === x && obs.y === y) return true;
        }
      }
    }
    return false;
  }

  /**
   * Execute a single tick of Snake movement.
   * @param {Object} state Current active board state
   * @returns {Object} Result of tick { nextState, event: 'MOVE'|'GROW'|'WIN'|'DEATH'|'TELEPORT', fruitEaten, noveltyEffect }
   */
  function tick(state) {
    if (!state || state.isPaused || state.isGameOver || state.isLevelComplete) {
      return { nextState: state, event: 'IDLE' };
    }

    const bounds = getEffectiveBounds(state);
    const { direction, targetFruits, obstacles } = state;
    const snake = state.snake.map(seg => [...seg]);
    let obstacleSet;
    if (obstacles instanceof Set) {
      obstacleSet = obstacles;
    } else if (Array.isArray(obstacles)) {
      obstacleSet = new Set();
      for (let i = 0; i < obstacles.length; i++) {
        const obs = obstacles[i];
        if (typeof obs === 'string') obstacleSet.add(obs);
        else if (typeof obs === 'number') {
          const stride = bounds.totalWidth || bounds.spanX;
          obstacleSet.add(`${obs % stride},${Math.floor(obs / stride)}`);
        } else if (obs && typeof obs === 'object') {
          if (Array.isArray(obs)) obstacleSet.add(`${obs[0]},${obs[1]}`);
          else if (obs.x !== undefined && obs.y !== undefined) obstacleSet.add(`${obs.x},${obs.y}`);
        }
      }
    } else {
      obstacleSet = new Set();
    }
    const dirVector = DIRS[direction] || DIRS.RIGHT;

    // Calculate next head position with bounded wrapping
    const head = snake[0];
    const rawX = head[0] + dirVector.dx;
    const rawY = head[1] + dirVector.dy;
    const { x: nextX, y: nextY } = wrapCoordinate(rawX, rawY, bounds);

    // 1. Check Obstacle Collision
    const strideWidth = bounds.totalWidth || bounds.spanX;
    if (isObstacle(nextX, nextY, obstacleSet, strideWidth)) {
      return {
        nextState: {
          ...state,
          isGameOver: true,
          comboCount: 0,
          restartsCount: (state.restartsCount || 0) + 1
        },
        event: 'DEATH',
        deathReason: 'OBSTACLE_COLLISION'
      };
    }

    // 2. Check Quantum Gateway Teleportation
    let finalX = nextX;
    let finalY = nextY;
    let didTeleport = false;

    if (state.gateways && state.gateways.length >= 2) {
      const g1 = state.gateways[0];
      const g2 = state.gateways[1];
      if (nextX === g1.x && nextY === g1.y) {
        finalX = bounds.minX + ((((g2.x + dirVector.dx - bounds.minX) % bounds.spanX) + bounds.spanX) % bounds.spanX);
        finalY = bounds.minY + ((((g2.y + dirVector.dy - bounds.minY) % bounds.spanY) + bounds.spanY) % bounds.spanY);
        didTeleport = true;
      } else if (nextX === g2.x && nextY === g2.y) {
        finalX = bounds.minX + ((((g1.x + dirVector.dx - bounds.minX) % bounds.spanX) + bounds.spanX) % bounds.spanX);
        finalY = bounds.minY + ((((g1.y + dirVector.dy - bounds.minY) % bounds.spanY) + bounds.spanY) % bounds.spanY);
        didTeleport = true;
      }
    }

    // 3. Check Self-Collision
    let foods = (Array.isArray(state.foods) && state.foods.length > 0)
      ? state.foods.map(f => ({ ...f }))
      : (state.food ? [{ ...state.food }] : []);
    let isDirectFoodContact = false;
    for (let f of foods) {
      if (f && f.x === finalX && f.y === finalY) {
        isDirectFoodContact = true;
        break;
      }
    }
    const bodyToCheck = isDirectFoodContact ? snake : snake.slice(0, snake.length - 1);

    for (let i = 0; i < bodyToCheck.length; i++) {
      if (bodyToCheck[i][0] === finalX && bodyToCheck[i][1] === finalY) {
        return {
          nextState: {
            ...state,
            isGameOver: true,
            comboCount: 0,
            restartsCount: (state.restartsCount || 0) + 1
          },
          event: 'DEATH',
          deathReason: 'SELF_COLLISION'
        };
      }
    }

    // 4. Move Snake Head
    snake.unshift([finalX, finalY]);

    let fruitsEaten = state.fruitsEatenInLevel || 0;
    let eatenFruitType = null;
    let eatenFoodIndex = -1;
    let noveltyEffect = null;
    let speedModifier = 1.0;
    let chillTimer = Math.max(0, (state.chillTimer || 0) - (state.currentTickMs || 150));
    let boostTimer = Math.max(0, (state.boostTimer || 0) - (state.currentTickMs || 150));
    let magnetTimer = Math.max(0, (state.magnetTimer || 0) - (state.currentTickMs || 150));
    let comboCount = state.comboCount || 0;
    let consecutiveSpecials = state.consecutiveSpecials || 0;
    let noveltyCooldown = Math.max(0, (state.noveltyCooldown || 0) - 1);

    // Food Collection & Magnet Attraction Logic
    let isEatingFruit = false;
    let eatenFoodItem = null;

    // Check direct head contact with any active food
    for (let fIdx = 0; fIdx < foods.length; fIdx++) {
      const f = foods[fIdx];
      if (f && f.x === finalX && f.y === finalY) {
        isEatingFruit = true;
        eatenFoodIndex = fIdx;
        eatenFoodItem = f;
        break;
      }
    }

    // If Magnet is active and foods exist, perform attraction step
    if (magnetTimer > 0 && !isEatingFruit) {
      const obstacleSet = new Set((state.obstacles || []).map(o => typeof o === 'string' ? o : (typeof o === 'object' && o !== null ? `${o.x},${o.y}` : `${o % width},${Math.floor(o / width)}`)));
      for (let fIdx = 0; fIdx < foods.length; fIdx++) {
        let f = foods[fIdx];
        if (!f) continue;
        const dx = finalX - f.x;
        const dy = finalY - f.y;
        const dist = Math.abs(dx) + Math.abs(dy);
        if (dist <= 10) {
          let stepX = f.x;
          let stepY = f.y;
          if (Math.abs(dx) >= Math.abs(dy) && dx !== 0) {
            stepX += (dx > 0 ? 1 : -1);
          } else if (dy !== 0) {
            stepY += (dy > 0 ? 1 : -1);
          } else if (dx !== 0) {
            stepX += (dx > 0 ? 1 : -1);
          }
          stepX = bounds.minX + ((((stepX - bounds.minX) % bounds.spanX) + bounds.spanX) % bounds.spanX);
          stepY = bounds.minY + ((((stepY - bounds.minY) % bounds.spanY) + bounds.spanY) % bounds.spanY);
          const cellKey = `${stepX},${stepY}`;
          if (!obstacleSet.has(cellKey)) {
            f = { ...f, x: stepX, y: stepY };
            foods[fIdx] = f;
          }
          if (f.x === finalX && f.y === finalY) {
            isEatingFruit = true;
            eatenFoodIndex = fIdx;
            eatenFoodItem = f;
            break;
          } else {
            const newDist = Math.abs(finalX - f.x) + Math.abs(finalY - f.y);
            if (newDist <= 1) {
              f = { ...f, x: finalX, y: finalY };
              foods[fIdx] = f;
              isEatingFruit = true;
              eatenFoodIndex = fIdx;
              eatenFoodItem = f;
              break;
            }
          }
        }
      }
    }

    // Handle Food / Power-Up Consumption
    if (isEatingFruit && eatenFoodItem) {
      eatenFruitType = eatenFoodItem.type || FRUIT_TYPES.APPLE;
      comboCount += 1;

      const isSpecial = eatenFruitType !== FRUIT_TYPES.APPLE;
      if (isSpecial) {
        consecutiveSpecials += 1;
        noveltyCooldown = 1;
      } else {
        consecutiveSpecials = 0;
      }

      // Handle Food types (Standard Apple vs Golden Fig / Regional Relics)
      if (eatenFruitType === FRUIT_TYPES.GOLDEN_APPLE) {
        fruitsEaten += 2;
        noveltyEffect = { type: 'GOLDEN_APPLE', text: '⭐ GOLDEN FIG (+150 PTS)!' };
      } else if (eatenFruitType === FRUIT_TYPES.CHILL_BERRY) {
        fruitsEaten += 1;
        chillTimer = 7000;
        boostTimer = 0;
        noveltyEffect = { type: 'CHILL_BERRY', text: '❄️ SLOW-MO BRAKE (7s)' };
      } else if (eatenFruitType === FRUIT_TYPES.SPEED_BOOST) {
        fruitsEaten += 1;
        boostTimer = 5000;
        chillTimer = 0;
        noveltyEffect = { type: 'SPEED_BOOST', text: '⚡ SPEED RUSH (5s)!' };
      } else if (eatenFruitType === FRUIT_TYPES.MAGNET) {
        fruitsEaten += 1;
        magnetTimer = 8000;
        noveltyEffect = { type: 'MAGNET', text: '🧲 MAGNET ATTRACTOR (8s)!' };
      } else if (eatenFruitType === FRUIT_TYPES.HOPPING_FRUIT) {
        fruitsEaten += 2;
        noveltyEffect = { type: 'HOPPING_FRUIT', text: '🐇 WILD HOPPING FRUIT (+2)!' };
      } else if (eatenFruitType === FRUIT_TYPES.SAHYADRI_GEM) {
        fruitsEaten += 2;
        magnetTimer = 10000;
        noveltyEffect = { type: 'SAHYADRI_GEM', text: '💎 SAHYADRI GEM (+2 & MAGNET 10s)!' };
      } else if (eatenFruitType === FRUIT_TYPES.KONKAN_PEARL) {
        fruitsEaten += 2;
        noveltyEffect = { type: 'KONKAN_PEARL', text: '🦪 KONKAN PEARL (+200 PTS)!' };
      } else if (eatenFruitType === FRUIT_TYPES.CAVE_RELIC) {
        fruitsEaten += 3;
        noveltyEffect = { type: 'CAVE_RELIC', text: '🏺 ANCIENT CAVE RELIC (+3 SURGE)!' };
      } else if (eatenFruitType === FRUIT_TYPES.ROYAL_WADA_TOKEN) {
        fruitsEaten += 2;
        noveltyEffect = { type: 'ROYAL_WADA_TOKEN', text: '🏛️ ROYAL WADA CREST (+250 PTS)!' };
      } else if (eatenFruitType === FRUIT_TYPES.FOREST_SPIRIT) {
        fruitsEaten += 2;
        chillTimer = 6000;
        noveltyEffect = { type: 'FOREST_SPIRIT', text: '🌿 FOREST SPIRIT (+2 & CALM 6s)!' };
      } else if (eatenFruitType === FRUIT_TYPES.EMBER_RELIC) {
        fruitsEaten += 3;
        boostTimer = 4000;
        noveltyEffect = { type: 'EMBER_RELIC', text: '🔥 EMBER RELIC (+3 & SURGE)!' };
      } else if (eatenFruitType === FRUIT_TYPES.DECCAN_CRYSTAL) {
        fruitsEaten += 2;
        noveltyEffect = { type: 'DECCAN_CRYSTAL', text: '💠 DECCAN CRYSTAL (+350 PTS)!' };
      } else if (eatenFruitType === FRUIT_TYPES.EXPEDITION_RELIC) {
        fruitsEaten += 3;
        noveltyEffect = { type: 'EXPEDITION_RELIC', text: '🚩 EXPEDITION RELIC (+500 PTS)!' };
      } else if (eatenFruitType === FRUIT_TYPES.SOVEREIGN_CREST) {
        fruitsEaten += 5;
        noveltyEffect = { type: 'SOVEREIGN_CREST', text: '👑 SOVEREIGN MAHARASHTRA CREST!' };
      } else if (eatenFruitType === FRUIT_TYPES.MYSTERY) {
        const rolls = ['BONUS_3', 'FREEZE', 'SPEED', 'MAGNET'];
        const chosen = rolls[Math.floor(Math.random() * rolls.length)];
        if (chosen === 'BONUS_3') {
          fruitsEaten += 3;
          noveltyEffect = { type: 'MYSTERY', text: '🎁 MYSTERY: +3 FRUITS SURGE!' };
        } else if (chosen === 'FREEZE') {
          fruitsEaten += 1;
          chillTimer = 7000;
          boostTimer = 0;
          noveltyEffect = { type: 'MYSTERY', text: '🎁 MYSTERY: SLOW-MO FREEZE!' };
        } else if (chosen === 'SPEED') {
          fruitsEaten += 1;
          boostTimer = 5000;
          chillTimer = 0;
          noveltyEffect = { type: 'MYSTERY', text: '🎁 MYSTERY: SPEED HYPERDRIVE!' };
        } else {
          fruitsEaten += 1;
          magnetTimer = 8000;
          noveltyEffect = { type: 'MYSTERY', text: '🎁 MYSTERY: MAGNET SURGE!' };
        }
      } else {
        fruitsEaten += 1;
        if (comboCount === 3) {
          noveltyEffect = { type: 'COMBO', text: '🔥 STREAK x3!' };
        } else if (comboCount === 5) {
          noveltyEffect = { type: 'COMBO', text: '⚡ STREAK x5!' };
        } else if (comboCount >= 8 && comboCount % 4 === 0) {
          noveltyEffect = { type: 'COMBO', text: `👑 STREAK x${comboCount}!` };
        }
      }

      // Consumed food is removed from foods array
      if (eatenFoodIndex >= 0 && eatenFoodIndex < foods.length) {
        foods.splice(eatenFoodIndex, 1);
      }
    } else {
      // Normal movement -> pop tail segment
      snake.pop();

      // Check food expiration
      foods = foods.filter(f => {
        if (f && f.lifetimeMs && f.spawnTime) {
          return (Date.now() - f.spawnTime <= f.lifetimeMs);
        }
        return true;
      });
    }

    // Speed modifier calculation
    if (chillTimer > 0) {
      speedModifier = 1.35; // 35% slower tick
    } else if (boostTimer > 0) {
      speedModifier = 0.72; // 28% faster tick
    } else {
      speedModifier = 1.0;
    }

    const maxTargets = (state.level >= 10 && bounds.spanX >= 24) ? 2 : 1;
    const needsFoodSpawn = (foods.length < maxTargets);

    // 5. Check Level Win Condition
    const isLevelComplete = fruitsEaten >= targetFruits;

    const nextState = {
      ...state,
      snake,
      foods,
      food: foods[0] || null,
      fruitsEatenInLevel: fruitsEaten,
      isLevelComplete,
      speedModifier,
      chillTimer,
      boostTimer,
      magnetTimer,
      comboCount,
      consecutiveSpecials,
      noveltyCooldown,
      longestSnakeInLevel: Math.max(state.longestSnakeInLevel || snake.length, snake.length)
    };

    let event = 'MOVE';
    if (isLevelComplete) event = 'WIN';
    else if (isEatingFruit) event = 'GROW';
    else if (didTeleport) event = 'TELEPORT';

    return {
      nextState,
      event,
      fruitEaten: eatenFruitType,
      eatenFoodIndex,
      eatenFoodItem,
      needsFoodSpawn,
      noveltyEffect
    };
  }

  /**
   * Determine new direction, rejecting illegal 180-degree instant self-reversals.
   */
  function changeDirection(currentDirection, requestedDirection, snakeLength) {
    if (!requestedDirection || !DIRS[requestedDirection]) return currentDirection;
    if (snakeLength > 1 && DIRS[requestedDirection].opp === currentDirection) {
      return currentDirection;
    }
    return requestedDirection;
  }

  /**
   * Calculate Stars (1, 2, or 3) for completing a level.
   * 3 Stars: 0 restarts/deaths on this level
   * 2 Stars: 1 restart
   * 1 Star: 2+ restarts
   */
  function calculateStars(restartsCount) {
    const r = Math.max(0, restartsCount || 0);
    if (r === 0) return 3;
    if (r === 1) return 2;
    return 1;
  }

  return {
    DIRS,
    FRUIT_TYPES,
    getEffectiveBounds,
    wrapCoordinate,
    isObstacle,
    calculateIntraLevelTickMs,
    tick,
    changeDirection,
    calculateStars
  };
}));
