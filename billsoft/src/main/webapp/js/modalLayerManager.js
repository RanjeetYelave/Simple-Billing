/**
 * RupeeCRM — Centralized Modal Layer & Overlay Manager
 * Enforces strict, logical z-index hierarchies, foreground elevation, escape-key coordination,
 * tab focus trapping, and focus restoration across all desktop workflows.
 */
(function (window) {
  'use strict';

  // Logical overlay stacking bands
  var BANDS = {
    base: 10000,        // Standard detail views & primary modals (PartyDetailModal, Customer360Modal, etc.)
    standard: 10000,    // Standard forms, lists, catalog editors
    foreground: 10100,  // Secondary overlays opened from or over existing modals (DocumentExportModal, Record Payment)
    nested: 10200,      // Sub-dialogs (Add Vendor in PO, Advance Adjustment, Conflict Resolution)
    confirm: 10300,     // Confirmation dialogs, delete alerts, Admin PIN prompts
    critical: 10500,    // Critical system prompts & notifications
    gate: 99999         // Mandatory EULA gate / Disaster recovery
  };

  var stack = [];
  var isInitialized = false;

  var RupeeModalStack = {
    BANDS: BANDS,

    /**
     * Register an active modal on mount or render
     * @param {string} id Unique identifier for the modal
     * @param {Object} options Configuration options
     * @returns {number} The dynamically calculated z-index
     */
    register: function (id, options) {
      if (!id) id = 'modal_' + Math.random().toString(36).substring(2, 9);
      options = options || {};
      var priority = options.priority || 'standard';
      var baseBand = BANDS[priority] || BANDS.standard;

      // If already in stack, update properties
      for (var i = 0; i < stack.length; i++) {
        if (stack[i].id === id) {
          stack[i].onClose = options.onClose || stack[i].onClose;
          stack[i].getContainer = options.getContainer || stack[i].getContainer;
          this._syncStack();
          return stack[i].zIndex;
        }
      }

      // Calculate zIndex: baseBand + offset based on current depth
      var countInBand = 0;
      for (var j = 0; j < stack.length; j++) {
        var pBand = BANDS[stack[j].priority] || BANDS.standard;
        if (pBand <= baseBand) countInBand++;
      }
      var zIndex = baseBand + (countInBand * 20);

      var entry = {
        id: id,
        priority: priority,
        zIndex: zIndex,
        onClose: options.onClose || null,
        getContainer: options.getContainer || null,
        returnFocusEl: options.returnFocusEl || (typeof document !== 'undefined' ? document.activeElement : null),
        autoFocus: options.autoFocus !== false
      };

      stack.push(entry);
      this._syncStack();

      // Automatically focus first interactive element in container
      if (entry.autoFocus && typeof requestAnimationFrame !== 'undefined') {
        requestAnimationFrame(function () {
          RupeeModalStack.focusTop();
        });
      }

      return zIndex;
    },

    /**
     * Unregister a modal when closing or unmounting
     * @param {string} id Unique identifier of the modal
     */
    unregister: function (id) {
      if (!id) return;
      var foundIdx = -1;
      for (var i = 0; i < stack.length; i++) {
        if (stack[i].id === id) {
          foundIdx = i;
          break;
        }
      }
      if (foundIdx === -1) return;

      var entry = stack.splice(foundIdx, 1)[0];
      this._syncStack();

      // Restore focus to triggering element if valid
      if (entry && entry.returnFocusEl && typeof entry.returnFocusEl.focus === 'function') {
        try {
          if (document.body && document.body.contains(entry.returnFocusEl)) {
            entry.returnFocusEl.focus();
          } else {
            this.focusTop();
          }
        } catch (e) {
          this.focusTop();
        }
      } else {
        this.focusTop();
      }
    },

    /**
     * Get the active or assigned z-index for a modal id
     */
    getZIndex: function (id, priority) {
      for (var i = 0; i < stack.length; i++) {
        if (stack[i].id === id) return stack[i].zIndex;
      }
      var baseBand = BANDS[priority || 'standard'] || BANDS.standard;
      return baseBand + (stack.length * 20);
    },

    /**
     * Get the topmost active modal entry
     */
    getTop: function () {
      return stack.length > 0 ? stack[stack.length - 1] : null;
    },

    /**
     * Check if a specific modal id is currently the topmost foreground modal
     */
    isTop: function (id) {
      var top = this.getTop();
      return top ? top.id === id : false;
    },

    /**
     * Focus the first interactive element of the topmost modal
     */
    focusTop: function () {
      var top = this.getTop();
      if (!top || !top.getContainer) return;
      try {
        var container = top.getContainer();
        if (!container) return;
        var target = container.querySelector(
          '[autofocus], input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled]), button.btn-primary:not([disabled]), button:not([disabled])'
        );
        if (target && typeof target.focus === 'function') {
          target.focus();
        } else if (typeof container.focus === 'function') {
          if (!container.getAttribute('tabindex')) {
            container.setAttribute('tabindex', '-1');
          }
          container.focus();
        }
      } catch (e) { }
    },

    /**
     * Synchronize body classes, aria attributes and background dimming states
     */
    _syncStack: function () {
      if (typeof document === 'undefined' || !document.body) return;

      if (stack.length > 0) {
        document.body.classList.add('modal-open');
      } else {
        document.body.classList.remove('modal-open');
      }

      for (var i = 0; i < stack.length; i++) {
        var entry = stack[i];
        if (!entry.getContainer) continue;
        try {
          var el = entry.getContainer();
          if (!el) continue;
          var overlay = el.classList.contains('modal-overlay') ? el : el.closest('.modal-overlay');
          if (overlay) {
            var isTop = (i === stack.length - 1);
            if (isTop) {
              overlay.classList.remove('is-backgrounded', 'is-backgrounded-dim');
              overlay.setAttribute('aria-hidden', 'false');
              overlay.style.pointerEvents = 'auto';
            } else {
              overlay.classList.add('is-backgrounded', 'is-backgrounded-dim');
              overlay.setAttribute('aria-hidden', 'true');
              overlay.style.pointerEvents = 'none';
            }
          }
        } catch (e) { }
      }
    },

    /**
     * Diagnostic inspection of active modal stack
     */
    getStack: function () {
      return stack.slice();
    },

    /**
     * Initialize global event interceptors
     */
    init: function () {
      if (isInitialized || typeof window === 'undefined') return;
      isInitialized = true;

      // 1. Capture-phase Escape key handler
      // Intercepts before any component-level uncoordinated listeners and closes ONLY the true topmost modal
      window.addEventListener('keydown', function (e) {
        if (e.key === 'Escape' || e.keyCode === 27) {
          var top = RupeeModalStack.getTop();
          if (top && typeof top.onClose === 'function') {
            e.preventDefault();
            e.stopPropagation();
            e.stopImmediatePropagation();
            top.onClose();
          }
        }
      }, true);

      // 2. Tab key focus trap inside the topmost modal
      window.addEventListener('keydown', function (e) {
        if (e.key === 'Tab') {
          var top = RupeeModalStack.getTop();
          if (!top || !top.getContainer) return;
          var container = top.getContainer();
          if (!container) return;
          var focusables = container.querySelectorAll(
            'a[href], button:not([disabled]), input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
          );
          if (!focusables || focusables.length === 0) return;
          var first = focusables[0];
          var last = focusables[focusables.length - 1];

          if (e.shiftKey && document.activeElement === first) {
            e.preventDefault();
            last.focus();
          } else if (!e.shiftKey && document.activeElement === last) {
            e.preventDefault();
            first.focus();
          }
        }
      }, false);
    }
  };

  RupeeModalStack.init();
  window.RupeeModalStack = RupeeModalStack;

})(typeof window !== 'undefined' ? window : globalThis);
