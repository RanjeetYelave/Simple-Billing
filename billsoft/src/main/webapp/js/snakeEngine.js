/**
 * snakeEngine.js
 * Core mathematical grid, toroidal edge-wrapping, collision detection,
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
    GOLDEN_APPLE: 'GOLDEN_APPLE',
    CHILL_BERRY: 'CHILL_BERRY',
    SPEED_BOOST: 'SPEED_BOOST',
    MYSTERY: 'MYSTERY',
    MAGNET: 'MAGNET',
    HOPPING_FRUIT: 'HOPPING_FRUIT'
  };

  /**
   * Wrap coordinate toroidally around board boundary.
   * Left <-> Right, Top <-> Bottom
   */
  function wrapCoordinate(x, y, width, height) {
    const wrappedX = ((x % width) + width) % width;
    const wrappedY = ((y % height) + height) % height;
    return { x: wrappedX, y: wrappedY };
  }

  /**
   * Check if a 2D coordinate is inside an obstacle set.
   */
  function isObstacle(x, y, obstacleSet, width) {
    if (!obstacleSet) return false;
    const idx = y * width + x;
    return obstacleSet.has(idx);
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

    const { width, height, direction, targetFruits, obstacles } = state;
    const snake = state.snake.map(seg => [...seg]);
    const obstacleSet = obstacles instanceof Set ? obstacles : new Set(obstacles || []);
    const dirVector = DIRS[direction] || DIRS.RIGHT;

    // Calculate next head position with toroidal wrapping
    const head = snake[0];
    const rawX = head[0] + dirVector.dx;
    const rawY = head[1] + dirVector.dy;
    const { x: nextX, y: nextY } = wrapCoordinate(rawX, rawY, width, height);

    // 1. Check Obstacle Collision
    if (isObstacle(nextX, nextY, obstacleSet, width)) {
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
        finalX = (g2.x + dirVector.dx + width) % width;
        finalY = (g2.y + dirVector.dy + height) % height;
        didTeleport = true;
      } else if (nextX === g2.x && nextY === g2.y) {
        finalX = (g1.x + dirVector.dx + width) % width;
        finalY = (g1.y + dirVector.dy + height) % height;
        didTeleport = true;
      }
    }

    // 3. Check Self-Collision
    let food = state.food;
    const isDirectFoodContact = food && (finalX === food.x && finalY === food.y);
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
    let noveltyEffect = null;
    let speedModifier = 1.0;
    let chillTimer = Math.max(0, (state.chillTimer || 0) - (state.currentTickMs || 150));
    let boostTimer = Math.max(0, (state.boostTimer || 0) - (state.currentTickMs || 150));
    let magnetTimer = Math.max(0, (state.magnetTimer || 0) - (state.currentTickMs || 150));
    let comboCount = state.comboCount || 0;
    let consecutiveSpecials = state.consecutiveSpecials || 0;
    let noveltyCooldown = Math.max(0, (state.noveltyCooldown || 0) - 1);

    // 4. Food Collection & Magnet Attraction Logic
    let isEatingFruit = false;

    // Check direct head contact with food
    if (food && food.x === finalX && food.y === finalY) {
      isEatingFruit = true;
    }

    // If Magnet is active and food exists, perform attraction step
    if (magnetTimer > 0 && food && !isEatingFruit) {
      const dx = finalX - food.x;
      const dy = finalY - food.y;
      const dist = Math.abs(dx) + Math.abs(dy);

      // If within magnetic pull radius (<= 10 cells)
      if (dist <= 10) {
        // Step 1 grid cell closer to snake head
        let stepX = food.x;
        let stepY = food.y;

        if (Math.abs(dx) >= Math.abs(dy) && dx !== 0) {
          stepX += (dx > 0 ? 1 : -1);
        } else if (dy !== 0) {
          stepY += (dy > 0 ? 1 : -1);
        } else if (dx !== 0) {
          stepX += (dx > 0 ? 1 : -1);
        }

        stepX = ((stepX % width) + width) % width;
        stepY = ((stepY % height) + height) % height;

        const targetIdx = stepY * width + stepX;
        if (!obstacleSet.has(targetIdx)) {
          food = { ...food, x: stepX, y: stepY };
        }

        // Instant Consumption: If pulled directly onto head or adjacent collection radius (dist <= 1)
        if (food.x === finalX && food.y === finalY) {
          isEatingFruit = true;
        } else {
          const newDist = Math.abs(finalX - food.x) + Math.abs(finalY - food.y);
          if (newDist <= 1) {
            // Immediate magnetic capture: pull directly onto head and consume
            food = { ...food, x: finalX, y: finalY };
            isEatingFruit = true;
          }
        }
      }
    }

    // Handle Food / Power-Up Consumption
    if (isEatingFruit && food) {
      eatenFruitType = food.type || FRUIT_TYPES.APPLE;
      comboCount += 1;

      const isSpecial = eatenFruitType !== FRUIT_TYPES.APPLE;
      if (isSpecial) {
        consecutiveSpecials += 1;
        noveltyCooldown = 1; // Require normal apple after a special item
      } else {
        consecutiveSpecials = 0;
      }

      // Handle Novelty Items:
      if (eatenFruitType === FRUIT_TYPES.GOLDEN_APPLE) {
        fruitsEaten += 2; // Golden apple advances 2 fruits
        noveltyEffect = { type: 'GOLDEN_APPLE', text: '⭐ GOLDEN APPLE +2!' };
      } else if (eatenFruitType === FRUIT_TYPES.CHILL_BERRY) {
        fruitsEaten += 1;
        chillTimer = 7000; // 7 seconds slow-mo
        boostTimer = 0;
        noveltyEffect = { type: 'CHILL_BERRY', text: '❄️ SLOW-MO BRAKE (7s)' };
      } else if (eatenFruitType === FRUIT_TYPES.SPEED_BOOST) {
        fruitsEaten += 1;
        boostTimer = 5000; // 5 seconds speed boost
        chillTimer = 0;
        noveltyEffect = { type: 'SPEED_BOOST', text: '⚡ SPEED RUSH (5s)!' };
      } else if (eatenFruitType === FRUIT_TYPES.MAGNET) {
        fruitsEaten += 1;
        magnetTimer = 8000; // 8 seconds magnet pull
        noveltyEffect = { type: 'MAGNET', text: '🧲 MAGNET ATTRACTOR (8s)!' };
      } else if (eatenFruitType === FRUIT_TYPES.MYSTERY) {
        // Random mystery roll
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
          noveltyEffect = { type: 'COMBO', text: '🔥 COMBO x3!' };
        } else if (comboCount === 5) {
          noveltyEffect = { type: 'COMBO', text: '⚡ SUPER STREAK x5!' };
        } else if (comboCount >= 8 && comboCount % 4 === 0) {
          noveltyEffect = { type: 'COMBO', text: `👑 MEGA STREAK x${comboCount}!` };
        }
      }

      // Consumed food is permanently cleared
      food = null;
    } else {
      // Normal movement -> pop tail segment to maintain length
      snake.pop();

      // Check food expiration (e.g. golden apple lifetime)
      if (food && food.lifetimeMs && food.spawnTime) {
        if (Date.now() - food.spawnTime > food.lifetimeMs) {
          food = null; // Expired cleanly
          consecutiveSpecials = 0;
        }
      }
    }

    // Speed modifier calculation
    if (chillTimer > 0) {
      speedModifier = 1.35; // 35% slower tick (relaxing)
    } else if (boostTimer > 0) {
      speedModifier = 0.72; // 28% faster tick (sprint)
    } else {
      speedModifier = 1.0;
    }

    const activeFood = food;
    const needsFoodSpawn = isEatingFruit || !activeFood;

    // 5. Check Level Win Condition
    const isLevelComplete = fruitsEaten >= targetFruits;

    const nextState = {
      ...state,
      snake,
      food: activeFood,
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
      noveltyEffect,
      needsFoodSpawn
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
    wrapCoordinate,
    isObstacle,
    tick,
    changeDirection,
    calculateStars
  };
}));
