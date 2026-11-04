/**
 * bouncePersistence.js
 * Dual-tier persistence adapter for Nokia Bounce in RupeeCRM.
 * Communicates with Spring Boot H2 endpoints (/api/bounce/...) and maintains
 * local fallback caching for 100% offline operation.
 */

(function (root, factory) {
  if (typeof define === 'function' && define.amd) {
    define([], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  } else {
    root.BouncePersistence = factory();
  }
}(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  const STORAGE_KEY = 'rcrm_bounce_v1_state';
  const BASE_API = '/api/bounce';

  let debounceTimer = null;
  let pendingSnapshot = null;

  function getAuthHeaders() {
    const headers = { 'Content-Type': 'application/json', 'Accept': 'application/json' };
    try {
      const token = (typeof API !== 'undefined' && API && API.token)
        ? API.token
        : (typeof localStorage !== 'undefined' ? localStorage.getItem('billsoft_auth_token') : null);
      if (token) {
        headers['Authorization'] = 'Bearer ' + token;
        headers['X-Auth-Token'] = token;
      }
    } catch (e) {}
    return headers;
  }

  /**
   * Load entire game state from backend H2 or fallback to localStorage.
   */
  async function loadGameState() {
    let remoteState = null;
    try {
      if (typeof fetch === 'function') {
        const res = await fetch(`${BASE_API}/state`, {
          method: 'GET',
          headers: getAuthHeaders()
        });
        if (res.ok) {
          remoteState = await res.json();
          saveLocalCache(remoteState);
          return remoteState;
        }
      }
    } catch (e) {
      console.warn('[BouncePersistence] Backend API unavailable, falling back to local storage:', e);
    }

    // Fallback: local storage
    return loadLocalCache();
  }

  /**
   * Start a new campaign.
   */
  async function startNewCampaign(seed) {
    try {
      if (typeof fetch === 'function') {
        const res = await fetch(`${BASE_API}/campaign/new`, {
          method: 'POST',
          headers: getAuthHeaders(),
          body: JSON.stringify({ seed })
        });
        if (res.ok) {
          const state = await res.json();
          saveLocalCache(state);
          return state;
        }
      }
    } catch (e) {
      console.warn('[BouncePersistence] Failed to start new campaign via backend:', e);
    }

    // Local fallback
    const fallback = {
      campaign: { id: Date.now(), currentLevelNumber: 1, seed: seed || 427210, campaignStatus: 'ACTIVE' },
      progress: { levelNumber: 1, remainingLives: 3, currentScore: 0, playerX: 64, playerY: 350 },
      settings: { musicVolume: 0.7, soundVolume: 0.8, muted: false },
      records: []
    };
    saveLocalCache(fallback);
    return fallback;
  }

  /**
   * Reset campaign progress while preserving records and settings.
   */
  async function resetCampaign() {
    try {
      if (typeof fetch === 'function') {
        const res = await fetch(`${BASE_API}/campaign/reset`, {
          method: 'POST',
          headers: getAuthHeaders()
        });
        if (res.ok) {
          const state = await res.json();
          saveLocalCache(state);
          return state;
        }
      }
    } catch (e) {
      console.warn('[BouncePersistence] Failed to reset campaign via backend:', e);
    }

    return startNewCampaign(null);
  }

  /**
   * Save progress snapshot (debounced for periodic 15s saves, immediate on important events).
   */
  function saveProgress(snapshot, immediate = false) {
    if (!snapshot) return;
    pendingSnapshot = snapshot;

    // Always update local cache instantly
    const local = loadLocalCache() || {};
    local.progress = snapshot;
    saveLocalCache(local);

    if (immediate) {
      if (debounceTimer) {
        clearTimeout(debounceTimer);
        debounceTimer = null;
      }
      flushPendingProgress();
    } else if (!debounceTimer) {
      debounceTimer = setTimeout(() => {
        debounceTimer = null;
        flushPendingProgress();
      }, 15000); // 15-second default periodic save interval
    }
  }

  async function flushPendingProgress() {
    if (!pendingSnapshot) return;
    const toSend = pendingSnapshot;
    pendingSnapshot = null;

    try {
      if (typeof fetch === 'function') {
        await fetch(`${BASE_API}/progress/save`, {
          method: 'POST',
          headers: getAuthHeaders(),
          body: JSON.stringify(toSend)
        });
      }
    } catch (e) {
      console.warn('[BouncePersistence] Failed to flush progress snapshot to backend:', e);
    }
  }

  /**
   * Complete level, record score/time, and generate/fetch next level.
   */
  async function completeLevel(completionData) {
    try {
      if (typeof fetch === 'function') {
        const res = await fetch(`${BASE_API}/level/complete`, {
          method: 'POST',
          headers: getAuthHeaders(),
          body: JSON.stringify(completionData)
        });
        if (res.ok) {
          const state = await res.json();
          saveLocalCache(state);
          return state;
        }
      }
    } catch (e) {
      console.warn('[BouncePersistence] Failed to complete level via backend:', e);
    }

    return null;
  }

  /**
   * Update audio and mute settings.
   */
  async function updateSettings(settings) {
    if (!settings) return;
    try {
      if (typeof fetch === 'function') {
        await fetch(`${BASE_API}/settings`, {
          method: 'PUT',
          headers: getAuthHeaders(),
          body: JSON.stringify(settings)
        });
      }
    } catch (e) {}

    const local = loadLocalCache() || {};
    local.settings = settings;
    saveLocalCache(local);
  }

  function saveLocalCache(state) {
    try {
      if (typeof localStorage !== 'undefined' && state) {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
      }
    } catch (e) {}
  }

  function loadLocalCache() {
    try {
      if (typeof localStorage !== 'undefined') {
        const raw = localStorage.getItem(STORAGE_KEY);
        if (raw) return JSON.parse(raw);
      }
    } catch (e) {}
    return null;
  }

  return {
    loadGameState,
    startNewCampaign,
    resetCampaign,
    saveProgress,
    completeLevel,
    updateSettings,
    flushPendingProgress
  };
}));
