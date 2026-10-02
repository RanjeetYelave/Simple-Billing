/**
 * snakePersistence.js
 * Lightweight two-tier persistence (localStorage + AppConfig DB) and crash recovery engine
 * for Snake (Take a Break -> Classic).
 * Persists strictly logical progression without physical canvas or viewport dimensions.
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
  const CONFIG_API_URL = '/api/app-config/SNAKE_GAME_STATE';
  const CURRENT_SCHEMA_VERSION = 1;

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
      completedCount: 0,
      totalStars: 0,
      highestStarMilestone: 0,
      totalFruitsEaten: 0,
      longestSnake: 3,
      totalPlayTimeSeconds: 0,
      bestStreak: 0,
      currentStreak: 0,
      currentBoard: null,
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
    if (typeof state.totalStars !== 'number' || state.totalStars < 0) return false;
    if (typeof state.completedCount !== 'number' || state.completedCount < 0) return false;
    return true;
  }

  /**
   * Sanitize and fill defaults for any missing optional fields while protecting lifetime statistics.
   * Strips any viewport/pixel dimensions to maintain pure logical persistence.
   */
  function sanitizeState(state) {
    if (!state) return createDefaultState();
    let rawMilestone = Math.max(0, Math.floor(state.highestStarMilestone || 0));
    if (rawMilestone >= 50 && rawMilestone % 50 === 0) {
      rawMilestone = Math.floor(rawMilestone / 50);
    }

    const currentLevel = Math.max(1, Math.floor(state.currentLevel || 1));
    let sanitizedBoard = null;

    if (state.currentBoard && validateBoard(state.currentBoard, currentLevel)) {
      const cb = state.currentBoard;
      sanitizedBoard = {
        level: currentLevel,
        seed: cb.seed != null ? cb.seed : 0,
        bounds: cb.bounds ? {
          minX: Math.floor(cb.bounds.minX || 0),
          minY: Math.floor(cb.bounds.minY || 0),
          maxX: Math.floor(cb.bounds.maxX || 15),
          maxY: Math.floor(cb.bounds.maxY || 11),
          spanX: Math.max(1, Math.floor(cb.bounds.spanX || 16)),
          spanY: Math.max(1, Math.floor(cb.bounds.spanY || 12)),
          totalCols: Math.floor(cb.bounds.totalCols || cb.totalCols || 24),
          totalRows: Math.floor(cb.bounds.totalRows || cb.totalRows || 16)
        } : null,
        width: Math.max(1, Math.floor(cb.width || 16)),
        height: Math.max(1, Math.floor(cb.height || 12)),
        totalCols: Math.floor(cb.totalCols || 24),
        totalRows: Math.floor(cb.totalRows || 16),
        targetFruits: Math.max(1, Math.floor(cb.targetFruits || 5)),
        baseTickMs: Math.max(115, Math.floor(cb.baseTickMs || 150)),
        layoutType: cb.layoutType || 'CLEAN',
        isMilestone: !!cb.isMilestone,
        isOasis: !!cb.isOasis,
        snake: cb.snake.map(seg => [Math.floor(seg[0]), Math.floor(seg[1])]),
        direction: cb.direction || 'RIGHT',
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
        chillTimer: Math.max(0, Math.floor(cb.chillTimer || 0)),
        boostTimer: Math.max(0, Math.floor(cb.boostTimer || 0)),
        magnetTimer: Math.max(0, Math.floor(cb.magnetTimer || 0)),
        comboCount: Math.max(0, Math.floor(cb.comboCount || 0)),
        consecutiveSpecials: Math.max(0, Math.floor(cb.consecutiveSpecials || 0)),
        noveltyCooldown: Math.max(0, Math.floor(cb.noveltyCooldown || 0)),
        speedModifier: typeof cb.speedModifier === 'number' ? cb.speedModifier : 1.0,
        longestSnakeInLevel: Math.max(3, Math.floor(cb.longestSnakeInLevel || (cb.snake ? cb.snake.length : 3)))
      };
    }

    return {
      schemaVersion: state.schemaVersion || CURRENT_SCHEMA_VERSION,
      stateRevision: Math.max(1, state.stateRevision || 1),
      currentLevel,
      completedCount: Math.max(0, Math.floor(state.completedCount || 0)),
      totalStars: Math.max(0, Math.floor(state.totalStars || 0)),
      highestStarMilestone: rawMilestone,
      totalFruitsEaten: Math.max(0, Math.floor(state.totalFruitsEaten || 0)),
      longestSnake: Math.max(3, Math.floor(state.longestSnake || 3)),
      totalPlayTimeSeconds: Math.max(0, Math.floor(state.totalPlayTimeSeconds || 0)),
      bestStreak: Math.max(0, Math.floor(state.bestStreak || 0)),
      currentStreak: Math.max(0, Math.floor(state.currentStreak || 0)),
      currentBoard: sanitizedBoard,
      updatedAt: state.updatedAt || Date.now()
    };
  }

  /**
   * Load state synchronously from localStorage.
   */
  function loadFromLocalStorage() {
    try {
      if (typeof localStorage === 'undefined') return null;
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return null;
      const parsed = JSON.parse(raw);
      return validateState(parsed) ? sanitizeState(parsed) : null;
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
      localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    } catch (e) {
      console.warn('[SnakePersistence] Failed to write localStorage:', e);
    }
  }

  /**
   * Load durable state from backend AppConfig endpoint asynchronously.
   */
  async function loadFromBackend() {
    try {
      const res = await fetch(CONFIG_API_URL, {
        headers: { 'Accept': 'application/json' }
      });
      if (!res.ok) return null;
      const data = await res.json();
      if (!data || !data.value) return null;
      const parsed = typeof data.value === 'string' ? JSON.parse(data.value) : data.value;
      return validateState(parsed) ? sanitizeState(parsed) : null;
    } catch (e) {
      console.warn('[SnakePersistence] Failed to fetch AppConfig from backend:', e);
      return null;
    }
  }

  /**
   * Flush state directly to backend AppConfig without debounce.
   */
  async function flushToBackend(state) {
    if (!state) return;
    try {
      await fetch(CONFIG_API_URL, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ value: JSON.stringify(state) })
      });
    } catch (e) {
      console.warn('[SnakePersistence] Failed to save AppConfig to backend:', e);
    }
  }

  /**
   * Save working state: Instant localStorage write + 500ms debounced backend sync.
   */
  function saveWorkingState(state) {
    if (!state) return;
    state.stateRevision = (state.stateRevision || 0) + 1;
    state.updatedAt = Date.now();
    pendingState = state;

    // Tier 1: Instant local write
    saveToLocalStorage(state);

    // Tier 2: Debounced backend sync
    if (debounceTimer) clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => {
      if (pendingState) {
        flushToBackend(pendingState);
        pendingState = null;
      }
    }, 500);
  }

  /**
   * Save progression immediately (on Level Solve or Milestone) bypassing debounce.
   */
  function saveProgressionImmediate(state) {
    if (!state) return;
    if (debounceTimer) {
      clearTimeout(debounceTimer);
      debounceTimer = null;
    }
    state.stateRevision = (state.stateRevision || 0) + 1;
    state.updatedAt = Date.now();
    pendingState = null;

    saveToLocalStorage(state);
    flushToBackend(state);
  }

  /**
   * Flush any pending state immediately (e.g. on component unmount or window beforeunload).
   */
  function flushPending() {
    if (debounceTimer) {
      clearTimeout(debounceTimer);
      debounceTimer = null;
    }
    if (pendingState) {
      saveToLocalStorage(pendingState);
      flushToBackend(pendingState);
      pendingState = null;
    }
  }

  /**
   * Load and recover progression state using the recovery hierarchy:
   * 1. Check localStorage and AppConfig.
   * 2. Reconcile by stateRevision.
   * 3. Fallback to valid copy or clean default state.
   */
  async function loadAndRecoverState() {
    const local = loadFromLocalStorage();
    let remote = null;

    try {
      remote = await loadFromBackend();
    } catch (e) {
      // Backend unavailable; proceed with local
    }

    let resolvedState = null;

    if (local && remote) {
      resolvedState = (local.stateRevision >= remote.stateRevision) ? local : remote;
    } else if (local) {
      resolvedState = local;
    } else if (remote) {
      resolvedState = remote;
    } else {
      resolvedState = createDefaultState();
    }

    // Keep storage in sync
    saveToLocalStorage(resolvedState);
    return resolvedState;
  }

  /**
   * Authoritative, isolated reset of Snake Classic game progress.
   * Resets Tier 1 (localStorage) and Tier 2 (AppConfig backend) to canonical Level 1 clean state.
   * Clears acknowledged location unlocks and dispatches 'billsoft:snake-progress-reset'.
   */
  async function resetGameProgress() {
    try {
      if (debounceTimer) {
        clearTimeout(debounceTimer);
        debounceTimer = null;
      }
      pendingState = null;

      const cleanState = createDefaultState();
      cleanState.stateRevision = 1;
      cleanState.updatedAt = Date.now();

      // 1. Tier 1: LocalStorage
      saveToLocalStorage(cleanState);
      if (typeof localStorage !== 'undefined') {
        try {
          localStorage.removeItem('TAKE_A_BREAK_SHOWN_LOCATION_UNLOCKS');
        } catch (e) {}
      }

      // 2. Tier 2: Backend AppConfig
      await flushToBackend(cleanState);

      // 3. Dispatch system event for real-time reactivity
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
