/**
 * snakePersistence.js
 * Lightweight, robust localStorage-only persistence engine for Snake (Take a Break).
 * Persists strictly logical progression, high score, active board, and statistics.
 * 100% self-contained, zero backend REST dependency during active gameplay.
 */

(function (root, factory) {
  if (typeof define === 'function' && define.amd) {
    define([], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  } else {
    root.SnakePersistence = factory();
  }
}(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  const STORAGE_KEY = 'SNAKE_GAME_STATE';
  const BACKUP_V1_KEY = 'SNAKE_GAME_STATE_V1_BACKUP';
  const CONFIG_API_URL = '/api/app-config/SNAKE_GAME_STATE';
  const CURRENT_SCHEMA_VERSION = 2;

  let debounceTimer = null;
  let pendingState = null;

  /**
   * Return a default clean progression state starting at Level 1 with no active board.
   */
  function createDefaultState() {
    return {
      schemaVersion: CURRENT_SCHEMA_VERSION,
      stateRevision: 1,
      currentLevel: 1,
      score: 0,
      highScore: 0,
      totalPlayTimeSeconds: 0,
      currentBoard: null,
      // Backward-compatible fields
      completedCount: 0,
      totalStars: 0,
      highestStarMilestone: 0,
      totalFruitsEaten: 0,
      longestSnake: 3,
      bestStreak: 0,
      currentStreak: 0,
      updatedAt: Date.now()
    };
  }

  /**
   * Validate that a board object is structurally sound and matches the expected level.
   */
  function validateBoard(board, expectedLevel) {
    if (!board || typeof board !== 'object') return false;
    if (typeof board.level !== 'number' || board.level !== expectedLevel) return false;
    if (!Array.isArray(board.snake) || board.snake.length < 1) return false;
    if (typeof board.direction !== 'string') return false;
    if (typeof board.targetFruits !== 'number' || board.targetFruits < 1) return false;

    // Snake coordinates check
    for (let i = 0; i < board.snake.length; i++) {
      const seg = board.snake[i];
      if (!Array.isArray(seg) || seg.length < 2) return false;
      if (typeof seg[0] !== 'number' || typeof seg[1] !== 'number') return false;
    }

    return true;
  }

  /**
   * Validate state integrity and enforce invariants.
   */
  function validateState(state) {
    if (!state || typeof state !== 'object') return false;
    if (typeof state.currentLevel !== 'number' || state.currentLevel < 1) return false;
    return true;
  }

  /**
   * Sanitize and migrate state.
   * Strips viewport/pixel dimensions to maintain pure logical persistence.
   */
  function sanitizeState(state) {
    if (!state) return createDefaultState();

    const currentLevel = Math.max(1, Math.floor(state.currentLevel || 1));
    const completedCount = Math.max(0, Math.floor(state.completedCount || 0));
    const totalStars = Math.max(0, Math.floor(state.totalStars || 0));
    const totalFruitsEaten = Math.max(0, Math.floor(state.totalFruitsEaten || 0));

    // Migrate or derive high score
    const legacyDerivedScore = Math.max(totalFruitsEaten * 10, totalStars * 10);
    const highScore = Math.max(0, Math.floor(state.highScore || legacyDerivedScore || 0));
    const score = Math.max(0, Math.floor(state.score || 0));
    const totalPlayTimeSeconds = Math.max(0, Math.floor(state.totalPlayTimeSeconds || 0));

    let sanitizedBoard = null;

    if (state.currentBoard && validateBoard(state.currentBoard, currentLevel)) {
      const cb = state.currentBoard;
      sanitizedBoard = {
        level: currentLevel,
        seed: cb.seed != null ? cb.seed : 0,
        bounds: cb.bounds ? {
          minX: Math.floor(cb.bounds.minX || 0),
          minY: Math.floor(cb.bounds.minY || 0),
          maxX: Math.floor(cb.bounds.maxX || 19),
          maxY: Math.floor(cb.bounds.maxY || 19),
          spanX: Math.max(1, Math.floor(cb.bounds.spanX || 20)),
          spanY: Math.max(1, Math.floor(cb.bounds.spanY || 20)),
          totalCols: Math.floor(cb.bounds.totalCols || cb.totalCols || 24),
          totalRows: Math.floor(cb.bounds.totalRows || cb.totalRows || 24)
        } : null,
        width: Math.max(1, Math.floor(cb.width || 20)),
        height: Math.max(1, Math.floor(cb.height || 20)),
        totalCols: Math.floor(cb.totalCols || 24),
        totalRows: Math.floor(cb.totalRows || 24),
        targetFruits: Math.max(1, Math.floor(cb.targetFruits || 5)),
        baseTickMs: Math.max(75, Math.floor(cb.baseTickMs || 200)),
        layoutType: cb.layoutType || 'CLEAN',
        isMilestone: !!cb.isMilestone,
        isOasis: !!cb.isOasis,
        snake: cb.snake.map(seg => [Math.floor(seg[0]), Math.floor(seg[1])]),
        direction: cb.direction || 'UP',
        obstacles: Array.isArray(cb.obstacles) ? cb.obstacles.map(o => typeof o === 'string' ? o : (typeof o === 'number' ? Math.floor(o) : (o && o.x !== undefined ? `${o.x},${o.y}` : o))) : [],
        gateways: Array.isArray(cb.gateways) ? cb.gateways.map(g => ({ x: Math.floor(g.x), y: Math.floor(g.y), color: g.color || '#00e5ff' })) : [],
        fruitsEatenInLevel: Math.max(0, Math.floor(cb.fruitsEatenInLevel || 0)),
        elapsedSeconds: Math.max(0, Math.floor(cb.elapsedSeconds || 0)),
        restartsCount: Math.max(0, Math.floor(cb.restartsCount || 0)),
        food: cb.food ? {
          x: Math.floor(cb.food.x),
          y: Math.floor(cb.food.y),
          type: cb.food.type || 'APPLE',
          spawnTime: cb.food.spawnTime || Date.now(),
          lifetimeMs: cb.food.lifetimeMs || null
        } : null,
        foods: Array.isArray(cb.foods) ? cb.foods.map(f => ({
          x: Math.floor(f.x),
          y: Math.floor(f.y),
          type: f.type || 'APPLE',
          spawnTime: f.spawnTime || Date.now(),
          lifetimeMs: f.lifetimeMs || null
        })) : (cb.food ? [{
          x: Math.floor(cb.food.x),
          y: Math.floor(cb.food.y),
          type: cb.food.type || 'APPLE',
          spawnTime: cb.food.spawnTime || Date.now(),
          lifetimeMs: cb.food.lifetimeMs || null
        }] : []),
        chillTimer: Math.max(0, Math.floor(cb.chillTimer || 0)),
        boostTimer: Math.max(0, Math.floor(cb.boostTimer || 0)),
        magnetTimer: Math.max(0, Math.floor(cb.magnetTimer || 0)),
        comboCount: Math.max(0, Math.floor(cb.comboCount || 0)),
        consecutiveSpecials: Math.max(0, Math.floor(cb.consecutiveSpecials || 0)),
        noveltyCooldown: Math.max(0, Math.floor(cb.noveltyCooldown || 0)),
        speedModifier: typeof cb.speedModifier === 'number' ? cb.speedModifier : 1.0,
        longestSnakeInLevel: Math.max(3, Math.floor(cb.longestSnakeInLevel || (cb.snake ? cb.snake.length : 3))),
        isGameOver: !!cb.isGameOver,
        isPaused: true // Always restore in paused state
      };
    }

    return {
      schemaVersion: CURRENT_SCHEMA_VERSION,
      stateRevision: Math.max(1, (state.stateRevision || 1)),
      currentLevel,
      score,
      highScore,
      totalPlayTimeSeconds,
      currentBoard: sanitizedBoard,
      // Backward compatibility fields
      completedCount,
      totalStars,
      highestStarMilestone: Math.max(0, Math.floor(state.highestStarMilestone || 0)),
      totalFruitsEaten,
      longestSnake: Math.max(3, Math.floor(state.longestSnake || 3)),
      bestStreak: Math.max(0, Math.floor(state.bestStreak || 0)),
      currentStreak: Math.max(0, Math.floor(state.currentStreak || 0)),
      updatedAt: state.updatedAt || Date.now()
    };
  }

  /**
   * Load state synchronously from localStorage with versioned schema migration.
   */
  function loadFromLocalStorage() {
    try {
      if (typeof localStorage === 'undefined') return null;
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return null;
      const parsed = JSON.parse(raw);
      if (!validateState(parsed)) return null;

      // Schema migration: backup v1 before upgrading
      if (!parsed.schemaVersion || parsed.schemaVersion < CURRENT_SCHEMA_VERSION) {
        try {
          localStorage.setItem(BACKUP_V1_KEY, raw);
        } catch (e) {}
      }

      return sanitizeState(parsed);
    } catch (e) {
      console.warn('[SnakePersistence] Failed to read localStorage:', e);
      return null;
    }
  }

  /**
   * Write state synchronously to localStorage.
   */
  function saveToLocalStorage(state) {
    try {
      if (typeof localStorage === 'undefined') return;
      const sanitized = sanitizeState(state);
      localStorage.setItem(STORAGE_KEY, JSON.stringify(sanitized));
    } catch (e) {
      console.warn('[SnakePersistence] Failed to write localStorage:', e);
    }
  }

  /**
   * Save working state directly to localStorage (pure client-side).
   */
  function saveWorkingState(state) {
    if (!state) return;
    state.stateRevision = (state.stateRevision || 0) + 1;
    state.updatedAt = Date.now();
    pendingState = state;
    saveToLocalStorage(state);
  }

  /**
   * Save progression immediately to localStorage.
   */
  function saveProgressionImmediate(state) {
    if (!state) return;
    state.stateRevision = (state.stateRevision || 0) + 1;
    state.updatedAt = Date.now();
    pendingState = null;
    saveToLocalStorage(state);
  }

  /**
   * Flush pending state immediately.
   */
  function flushPending() {
    if (pendingState) {
      saveToLocalStorage(pendingState);
      pendingState = null;
    }
  }

  /**
   * Asynchronous load and recover progression state from localStorage.
   */
  async function loadAndRecoverState() {
    const local = loadFromLocalStorage();
    const resolved = local || createDefaultState();
    saveToLocalStorage(resolved);
    return resolved;
  }

  /**
   * Backend AppConfig helper methods (for tests and diagnostic compatibility).
   */
  async function loadFromBackend() {
    if (typeof fetch !== 'function') return null;
    try {
      const authHeaders = (typeof API !== 'undefined' && API && API.token)
        ? { 'X-Auth-Token': API.token, 'Authorization': 'Bearer ' + API.token }
        : {};
      const res = await fetch(CONFIG_API_URL, {
        headers: { 'Accept': 'application/json', ...authHeaders }
      });
      if (!res.ok) return null;
      const data = await res.json();
      if (!data || !data.value) return null;
      const parsed = typeof data.value === 'string' ? JSON.parse(data.value) : data.value;
      return validateState(parsed) ? sanitizeState(parsed) : null;
    } catch (e) {
      return null;
    }
  }

  async function flushToBackend(state) {
    if (!state || typeof fetch !== 'function') return;
    try {
      const authHeaders = (typeof API !== 'undefined' && API && API.token)
        ? { 'X-Auth-Token': API.token, 'Authorization': 'Bearer ' + API.token }
        : {};
      await fetch(CONFIG_API_URL, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', ...authHeaders },
        body: JSON.stringify({ value: JSON.stringify(state) })
      });
    } catch (e) {}
  }

  /**
   * Authoritative, isolated reset of Snake game progress in localStorage.
   */
  async function resetGameProgress() {
    try {
      pendingState = null;
      const cleanState = createDefaultState();
      cleanState.stateRevision = 1;
      cleanState.updatedAt = Date.now();

      // Reset in LocalStorage
      saveToLocalStorage(cleanState);

      if (typeof localStorage !== 'undefined') {
        try {
          localStorage.removeItem('TAKE_A_BREAK_SHOWN_LOCATION_UNLOCKS');
          localStorage.removeItem('TAKE_A_BREAK_LAST_PRESENTED_LOC');
          localStorage.removeItem('rcrm_take_a_break_ui_state');
        } catch (e) {}
      }

      // Best-effort optional backend sync if fetch is available
      if (typeof fetch === 'function') {
        await flushToBackend(cleanState);
      }

      // Dispatch event for UI reactivity
      if (typeof window !== 'undefined' && typeof window.dispatchEvent === 'function') {
        window.dispatchEvent(new CustomEvent('billsoft:snake-progress-reset', {
          detail: { state: cleanState }
        }));
        window.dispatchEvent(new CustomEvent('billsoft:game-progression', {
          detail: { gameType: 'classic', level: 1, totalStars: 0, completedCount: 0 }
        }));
      }

      return { success: true, state: cleanState };
    } catch (err) {
      console.error('[SnakePersistence] Reset game progress failed:', err);
      return { success: false, error: err };
    }
  }

  return {
    STORAGE_KEY,
    createDefaultState,
    validateBoard,
    validateState,
    sanitizeState,
    loadFromLocalStorage,
    saveToLocalStorage,
    loadFromBackend,
    flushToBackend,
    saveWorkingState,
    saveProgressionImmediate,
    flushPending,
    loadAndRecoverState,
    resetGameProgress
  };
}));
