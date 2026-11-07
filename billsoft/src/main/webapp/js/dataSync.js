/**
 * RupeeCRM Background Data Synchronization Engine (RupeeCRMSync)
 *
 * Provides:
 * 1. Deep structural equality comparison (isEqual) to avoid redundant React state updates & re-renders.
 * 2. In-flight API request coalescing & deduplication to eliminate duplicate parallel network traffic.
 * 3. Safe user-interaction guards (preventing background refreshes from disrupting typing, open modals, or active forms).
 * 4. Silent/background refresh event standardization.
 * 5. Event coalescing so dual event listeners don't execute twice per refresh tick.
 */
(function (root) {
  'use strict';

  const RupeeCRMSync = {
    /**
     * Fast, deep structural equality check for primitives, arrays, objects, and dates.
     * Returns true if two data structures represent identical content.
     */
    isEqual(a, b) {
      if (a === b) return true;
      if (a === null || b === null || a === undefined || b === undefined) return a === b;

      // Handle Date objects
      if (a instanceof Date && b instanceof Date) {
        return a.getTime() === b.getTime();
      }

      // Check primitive types
      const typeA = typeof a;
      const typeB = typeof b;
      if (typeA !== typeB) return false;
      if (typeA !== 'object') return false;

      // Check array vs non-array
      const isArrA = Array.isArray(a);
      const isArrB = Array.isArray(b);
      if (isArrA !== isArrB) return false;

      if (isArrA) {
        if (a.length !== b.length) return false;
        for (let i = 0; i < a.length; i++) {
          if (!this.isEqual(a[i], b[i])) return false;
        }
        return true;
      }

      // Plain objects
      const keysA = Object.keys(a);
      const keysB = Object.keys(b);
      if (keysA.length !== keysB.length) return false;

      for (let i = 0; i < keysA.length; i++) {
        const key = keysA[i];
        if (!Object.prototype.hasOwnProperty.call(b, key)) return false;
        if (!this.isEqual(a[key], b[key])) return false;
      }

      return true;
    },

    /**
     * Safely updates a React state setter only when data has genuinely changed.
     * Prevents unnecessary re-renders.
     */
    safeUpdate(setter, freshData) {
      if (typeof setter !== 'function') return false;
      let changed = false;
      setter(prev => {
        if (this.isEqual(prev, freshData)) {
          return prev; // bail out of React re-render
        }
        changed = true;
        return freshData;
      });
      return changed;
    },

    /**
     * Identifies whether a refresh option or CustomEvent represents a silent background sync.
     */
    isSilentRefresh(optsOrEvent) {
      if (!optsOrEvent) return false;
      if (optsOrEvent.silent === true || optsOrEvent.isSilent === true || optsOrEvent.isBackground === true) {
        return true;
      }
      if (optsOrEvent.detail) {
        const d = optsOrEvent.detail;
        if (d.silent === true || d.isSilent === true || d.isBackground === true) {
          return true;
        }
      }
      if (optsOrEvent.type === 'billsoft:silent-data-refresh') {
        return true;
      }
      return false;
    },

    /**
     * Checks whether the current UI state is safe for a non-destructive background data update.
     * Returns false if the user is typing, has an active dialog/modal, or is selecting text.
     */
    canRefreshSafely() {
      if (typeof document === 'undefined') return true;

      // 1. Is the active element an editable input or textarea?
      const el = document.activeElement;
      if (el) {
        const tag = el.tagName ? el.tagName.toUpperCase() : '';
        if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || el.isContentEditable) {
          return false;
        }
      }

      // 2. Is any modal, dialog, or confirmation prompt open?
      const modal = document.querySelector('.modal-overlay, .modal-card, [role="dialog"], .swal2-container');
      if (modal) {
        return false;
      }

      // 3. Is the document currently hidden?
      if (document.hidden) {
        return false;
      }

      return true;
    },

    /**
     * Determines if a specific view or page is visible to the user.
     * Used by module data loaders to avoid fetching large datasets for dormant/hidden views.
     */
    isPageVisible(pageName) {
      if (!pageName) return true;
      if (typeof window !== 'undefined' && typeof window.getCurrentPage === 'function') {
        try {
          const cur = window.getCurrentPage();
          if (cur) return cur === pageName;
        } catch (e) { }
      }
      return true;
    },

    /**
     * Coalesces and debounces refresh events arriving in the same execution tick (e.g., within 60ms).
     * Prevents dual events (billsoft:app-refresh + billsoft:silent-data-refresh) from executing twice.
     */
    coalesceEvent(callback, delayMs = 60) {
      let pendingTimer = null;
      let lastEvent = null;

      return function (e) {
        lastEvent = e;
        if (pendingTimer) return;
        pendingTimer = setTimeout(() => {
          pendingTimer = null;
          try {
            callback(lastEvent);
          } catch (err) {
            console.debug('[RupeeCRMSync] Coalesced handler error:', err);
          }
        }, delayMs);
      };
    }
  };

  root.RupeeCRMSync = RupeeCRMSync;
})(typeof window !== 'undefined' ? window : this);
