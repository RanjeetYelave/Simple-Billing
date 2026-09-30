/**
 * circuitPersistence.js
 * Lightweight two-tier persistence (localStorage + AppConfig DB) and crash recovery engine
 * for Circuit Connect.
 * Persists PROGRESSION + ACTIVE CURRENT BOARD rather than historical boards.
 */

(function (root, factory) {
  if (typeof define === 'function' && define.amd) {
    define([], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  } else {
    root.CircuitPersistence = factory();
  }
}(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  const STORAGE_KEY = 'CIRCUIT_CONNECT_STATE';
  const CONFIG_API_URL = '/api/app-config/CIRCUIT_CONNECT_STATE';
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
      skippedCount: 0,
      totalStars: 0,
      highestStarMilestone: 0,
      totalPlayTimeSeconds: 0,
      totalMoves: 0,
      bestStreak: 0,
      currentStreak: 0,
      hintBalance: 3,
      hintProgressSeconds: 0,
      introducedMechanics: [],
      currentBoard: null,
      updatedAt: Date.now()
    };
  }

  /**
   * Validate that a board object is structurally sound and matches the expected level.
   * @param {Object} board
   * @param {number} expectedLevel
   * @returns {boolean} True if board is valid
   */
  function validateBoard(board, expectedLevel) {
    if (!board || typeof board !== 'object') return false;
    if (typeof board.level !== 'number' || board.level !== expectedLevel) return false;
    if (typeof board.width !== 'number' || board.width < 3 || board.width > 10) return false;
    if (typeof board.height !== 'number' || board.height < 3 || board.height > 10) return false;
    const totalCells = board.width * board.height;
    if (!Array.isArray(board.tiles) || board.tiles.length !== totalCells) return false;
    if (typeof board.sourceIdx !== 'number' || board.sourceIdx < 0 || board.sourceIdx >= totalCells) return false;
    if (!Array.isArray(board.targetIndices) || board.targetIndices.length === 0) return false;

    for (let i = 0; i < board.tiles.length; i++) {
      const t = board.tiles[i];
      if (!t || typeof t !== 'object') return false;
      if (typeof t.ports !== 'number' || typeof t.solvedPorts !== 'number') return false;
    }
    return true;
  }

  /**
   * Validate state integrity and enforce invariants.
   * @param {Object} state
   * @returns {boolean} True if valid
   */
  function validateState(state) {
    if (!state || typeof state !== 'object') return false;
    if (typeof state.currentLevel !== 'number' || state.currentLevel < 1) return false;
    if (typeof state.totalStars !== 'number' || state.totalStars < 0) return false;
    if (typeof state.completedCount !== 'number' || state.completedCount < 0) return false;
    if (typeof state.skippedCount !== 'number' || state.skippedCount < 0) return false;
    return true;
  }

  /**
   * Sanitize and fill defaults for any missing optional fields.
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
        width: cb.width,
        height: cb.height,
        sourceIdx: cb.sourceIdx,
        targetIndices: Array.from(cb.targetIndices),
        sources: Array.isArray(cb.sources) ? cb.sources.map(s => ({
          index: s.index,
          channel: s.channel || 'DEFAULT',
          symbol: s.symbol || '⚡'
        })) : [{ index: cb.sourceIdx, channel: 'DEFAULT', symbol: '⚡' }],
        bonusTargetIndices: Array.isArray(cb.bonusTargetIndices) ? Array.from(cb.bonusTargetIndices) : [],
        groups: Array.isArray(cb.groups) ? cb.groups.map(g => ({
          groupId: g.groupId,
          tileIndices: Array.from(g.tileIndices)
        })) : [],
        activeMechanics: Array.isArray(cb.activeMechanics) ? Array.from(cb.activeMechanics) : [],
        isMilestone: !!cb.isMilestone,
        minMoves: Math.max(1, Math.floor(cb.minMoves || 1)),
        tiles: cb.tiles.map((t, idx) => ({
          index: idx,
          ports: typeof t.ports === 'number' ? t.ports : 0,
          solvedPorts: typeof t.solvedPorts === 'number' ? t.solvedPorts : 0,
          type: t.type || 'UNKNOWN',
          isSource: !!t.isSource,
          isLoad: !!t.isLoad,
          isBlock: !!t.isBlock,
          channel: t.channel || 'DEFAULT',
          channelSymbol: t.channelSymbol || (t.isSource ? '⚡' : ''),
          isBridge: !!t.isBridge,
          isLocked: !!t.isLocked,
          unlockPrereqIdx: typeof t.unlockPrereqIdx === 'number' ? t.unlockPrereqIdx : null,
          isSwitch: !!t.isSwitch,
          switchPortsA: typeof t.switchPortsA === 'number' ? t.switchPortsA : 0,
          switchPortsB: typeof t.switchPortsB === 'number' ? t.switchPortsB : 0,
          switchState: t.switchState === 'B' ? 'B' : 'A',
          groupId: t.groupId || null,
          isWildcard: !!t.isWildcard,
          isBonus: !!t.isBonus,
          rotation: typeof t.rotation === 'number' ? (t.rotation % 4) : 0,
          distinctRotations: typeof t.distinctRotations === 'number' ? t.distinctRotations : 4
        })),
        moves: Math.max(0, Math.floor(cb.moves || 0)),
        elapsedSeconds: Math.max(0, Math.floor(cb.elapsedSeconds || 0)),
        hintHighlights: Array.isArray(cb.hintHighlights) ? cb.hintHighlights.map(n => Math.floor(n)) : []
      };
    }

    const introducedMechanics = Array.isArray(state.introducedMechanics)
      ? Array.from(new Set(state.introducedMechanics.filter(m => typeof m === 'string')))
      : [];

    return {
      schemaVersion: state.schemaVersion || CURRENT_SCHEMA_VERSION,
      stateRevision: Math.max(1, state.stateRevision || 1),
      currentLevel,
      completedCount: Math.max(0, Math.floor(state.completedCount || 0)),
      skippedCount: Math.max(0, Math.floor(state.skippedCount || 0)),
      totalStars: Math.max(0, Math.floor(state.totalStars || 0)),
      highestStarMilestone: rawMilestone,
      totalPlayTimeSeconds: Math.max(0, Math.floor(state.totalPlayTimeSeconds || 0)),
      totalMoves: Math.max(0, Math.floor(state.totalMoves || 0)),
      bestStreak: Math.max(0, Math.floor(state.bestStreak || 0)),
      hintBalance: state.hintBalance != null ? Math.max(0, Math.floor(state.hintBalance)) : 3,
      hintProgressSeconds: Math.max(0, Math.min(1799, Math.floor(state.hintProgressSeconds || 0))),
      introducedMechanics,
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
      console.warn('[CircuitPersistence] Failed to read localStorage:', e);
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
      console.warn('[CircuitPersistence] Failed to write localStorage:', e);
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
      console.warn('[CircuitPersistence] Failed to fetch AppConfig from backend:', e);
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
      console.warn('[CircuitPersistence] Failed to save AppConfig to backend:', e);
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
   * Save progression immediately (on Level Solve, Skip, or Milestone) bypassing debounce.
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
    loadAndRecoverState
  };
}));
