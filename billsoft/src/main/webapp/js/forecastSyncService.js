/**
 * RupeeCRM Forecast Synchronisation & Login Frequency Service
 * Manages automatic and manual forecast check & synchronization pipelines,
 * guarantees firm-level data isolation, deduplication across login events,
 * and concurrency safeguards.
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  } else {
    root.ForecastSyncService = factory();
  }
}(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  // Active in-flight synchronisation promises keyed by firmId
  const _activePromises = {};
  // Per-firm synchronization status flags
  const _syncingState = {};
  // Deduplication tracker: records timestamp of recent trigger per firmId
  const _lastTriggerTime = {};
  // Registered listeners
  const _listeners = new Set();

  function notify(firmId, isSyncing) {
    _listeners.forEach(fn => {
      try { fn(firmId, isSyncing); } catch (e) { console.error('[ForecastSyncService] Listener error:', e); }
    });
    if (typeof window !== 'undefined' && typeof window.dispatchEvent === 'function') {
      window.dispatchEvent(new CustomEvent('billsoft:forecast-sync-state', {
        detail: { firmId, isSyncing }
      }));
    }
  }

  const ForecastSyncService = {
    /**
     * Check if a synchronisation job is currently executing for the specified firm.
     */
    isSyncing(firmId) {
      const fid = firmId || (typeof API !== 'undefined' ? API.firmId : null);
      return Boolean(_syncingState[fid]);
    },

    /**
     * Subscribe to synchronization state changes.
     */
    subscribe(listener) {
      _listeners.add(listener);
      return () => _listeners.delete(listener);
    },

    /**
     * Core forecast synchronization method.
     * Reuses the existing backend API: POST /api/forecast/admin/sync
     * Guarantees firm-level isolation, deduplication, and safe concurrency handling.
     *
     * @param {number|string} targetFirmId Firm ID to synchronize
     * @param {object} options { isAutomatic: boolean, force: boolean }
     */
    triggerSync(targetFirmId, options = {}) {
      const firmId = targetFirmId || (typeof API !== 'undefined' ? API.firmId : null);
      if (!firmId) {
        console.warn('[ForecastSyncService] No active firm ID specified for forecast sync.');
        return Promise.resolve(null);
      }

      // Concurrency check: If a sync is already running for this firm, reuse the in-flight job
      if (_activePromises[firmId]) {
        console.log(`[ForecastSyncService] Synchronisation already running for firm ${firmId}; reusing in-flight job.`);
        return _activePromises[firmId];
      }

      const isAutomatic = Boolean(options.isAutomatic);
      const now = Date.now();

      // Deduplication: prevent rapid duplicate triggers (e.g., login + session init within 2000ms)
      if (isAutomatic && _lastTriggerTime[firmId] && (now - _lastTriggerTime[firmId] < 2000)) {
        console.log(`[ForecastSyncService] Deduplicating automatic sync trigger for firm ${firmId} (<2000ms since last trigger).`);
        return Promise.resolve(null);
      }
      _lastTriggerTime[firmId] = now;

      _syncingState[firmId] = true;
      notify(firmId, true);

      const syncJob = (async () => {
        try {
          console.log(`[ForecastSyncService] Checking forecast freshness for firm ${firmId} (automatic=${isAutomatic}, force=${Boolean(options.force)})...`);
          
          // 1. Freshness check: if automatic and fresh, bypass heavy recalculation
          if (isAutomatic && !options.force && typeof API !== 'undefined' && API.forecast && typeof API.forecast.getSyncStatus === 'function') {
            try {
              const statusDto = await API.forecast.getSyncStatus(firmId);
              if (statusDto && statusDto.isFresh && !statusDto.inProgress) {
                console.log(`[ForecastSyncService] Forecast for firm ${firmId} is already fresh (<6h). Bypassing startup retrain.`);
                if (typeof window !== 'undefined' && typeof window.dispatchEvent === 'function') {
                  window.dispatchEvent(new CustomEvent('billsoft:forecast-synced', {
                    detail: { firmId, res: statusDto, isAutomatic, bypassedFresh: true }
                  }));
                }
                return statusDto;
              }
            } catch (freshErr) {
              console.debug('[ForecastSyncService] Freshness check fallback to sync:', freshErr);
            }
          }

          console.log(`[ForecastSyncService] Dispatching background forecast sync for firm ${firmId}...`);
          let res;
          if (typeof API !== 'undefined' && API.forecast && typeof API.forecast.adminSync === 'function') {
            res = await API.forecast.adminSync(firmId, { force: Boolean(options.force), async: isAutomatic });
          } else {
            const url = `/api/forecast/admin/sync?firmId=${encodeURIComponent(firmId)}&async=${isAutomatic}&force=${Boolean(options.force)}`;
            const response = await fetch(url, {
              method: 'POST',
              headers: {
                'Content-Type': 'application/json',
                'X-Firm-Id': String(firmId)
              }
            });
            if (!response.ok) {
              const text = await response.text();
              throw new Error(`Sync failed (${response.status}): ${text}`);
            }
            res = await response.json();
          }

          console.log(`[ForecastSyncService] Forecast sync dispatched/completed for firm ${firmId}:`, res);

          // Dispatched so Order Products workspace and Product Master refresh recommendations
          if (typeof window !== 'undefined' && typeof window.dispatchEvent === 'function') {
            window.dispatchEvent(new CustomEvent('billsoft:forecast-synced', {
              detail: { firmId, res, isAutomatic }
            }));
          }

          return res;
        } catch (err) {
          console.warn(`[ForecastSyncService] Forecast sync error for firm ${firmId}:`, err);
          // For automatic sync: do NOT disrupt user, do NOT wipe data. Existing forecasts remain safe.
          throw err;
        } finally {
          delete _activePromises[firmId];
          _syncingState[firmId] = false;
          notify(firmId, false);
        }
      })();

      _activePromises[firmId] = syncJob;
      // Suppress unhandled rejection on the stored promise so background callers don't throw uncaught exceptions
      syncJob.catch(() => {});
      return syncJob;
    },

    /**
     * Invoked automatically on EVERY successful user login.
     * Triggers asynchronous, non-blocking forecast resynchronisation for the active firm.
     */
    onLogin(firmId) {
      const fid = firmId || (typeof API !== 'undefined' ? API.firmId : null);
      if (!fid) {
        console.log('[ForecastSyncService] onLogin skipped: active firm ID not yet available.');
        return;
      }
      console.log(`[ForecastSyncService] Successful user login detected for firm ${fid}. Executing automatic forecast check.`);
      this.triggerSync(fid, { isAutomatic: true }).catch(err => {
        console.warn(`[ForecastSyncService] Automatic login forecast check failed gracefully:`, err.message || err);
      });
    },

    /**
     * Invoked when the user logs out. Clears deduplication memory so next login triggers fresh sync.
     */
    onLogout() {
      console.log('[ForecastSyncService] User logged out. Resetting login session sync tracker.');
      Object.keys(_lastTriggerTime).forEach(k => delete _lastTriggerTime[k]);
    },

    /**
     * Invoked when switching active firm. Triggers forecast check for the newly active firm.
     */
    onFirmChanged(newFirmId) {
      if (!newFirmId) return;
      console.log(`[ForecastSyncService] Active firm switched to ${newFirmId}. Executing firm forecast check.`);
      this.triggerSync(newFirmId, { isAutomatic: true }).catch(err => {
        console.warn(`[ForecastSyncService] Firm switch forecast check failed gracefully:`, err.message || err);
      });
    }
  };

  return ForecastSyncService;
}));
