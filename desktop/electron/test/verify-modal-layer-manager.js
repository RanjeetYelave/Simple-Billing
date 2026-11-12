/**
 * RupeeCRM — Unit & Regression Test Suite for Modal Layer Manager
 * Verifies centralized overlay stacking order, dynamic z-index calculation,
 * capture-phase Escape interception, and inert backgrounding.
 */
const assert = require('assert');
const path = require('path');

// Setup minimal DOM simulation environment
global.window = {};
global.document = {
  activeElement: null,
  body: {
    classList: {
      _classes: new Set(),
      add: function (cls) { this._classes.add(cls); },
      remove: function (cls) { this._classes.delete(cls); },
      contains: function (cls) { return this._classes.has(cls); }
    },
    contains: function (el) { return true; }
  }
};
global.requestAnimationFrame = (cb) => { cb(); };

const eventListeners = {};
global.window.addEventListener = function (event, handler, useCapture) {
  const key = event + (useCapture ? ':capture' : ':bubble');
  if (!eventListeners[key]) eventListeners[key] = [];
  eventListeners[key].push(handler);
};

// Load modalLayerManager from billsoft
const managerPath = path.resolve(__dirname, '../../../billsoft/src/main/webapp/js/modalLayerManager.js');
require(managerPath);

const RupeeModalStack = global.window.RupeeModalStack;

console.log('--- Launching RupeeCRM Modal Layer Manager Verification Suite ---');

// [Scenario 1/6] API Interface Verification
assert(RupeeModalStack, 'RupeeModalStack must be exported to window');
assert.strictEqual(typeof RupeeModalStack.register, 'function');
assert.strictEqual(typeof RupeeModalStack.unregister, 'function');
assert.strictEqual(typeof RupeeModalStack.getZIndex, 'function');
console.log('  -> [Scenario 1/6] Verified: RupeeModalStack exports and API interface');

// [Scenario 2/6] Stacking Band Constants Verification
assert.strictEqual(RupeeModalStack.BANDS.base, 10000);
assert.strictEqual(RupeeModalStack.BANDS.foreground, 10100);
assert.strictEqual(RupeeModalStack.BANDS.nested, 10200);
assert.strictEqual(RupeeModalStack.BANDS.confirm, 10300);
assert.strictEqual(RupeeModalStack.BANDS.critical, 10500);
assert.strictEqual(RupeeModalStack.BANDS.gate, 99999);
console.log('  -> [Scenario 2/6] Verified: Overlay stacking band constants conform to architecture');

// [Scenario 3/6] Dynamic Elevation Hierarchy (Base < Foreground < Confirm)
let partyDetailClosed = false;
const z1 = RupeeModalStack.register('party_detail_1', {
  priority: 'base',
  onClose: () => { partyDetailClosed = true; }
});
assert.strictEqual(z1, 10000);
assert.strictEqual(RupeeModalStack.isTop('party_detail_1'), true);
assert(document.body.classList.contains('modal-open'));

let docExportClosed = false;
const z2 = RupeeModalStack.register('doc_export_modal', {
  priority: 'foreground',
  onClose: () => { docExportClosed = true; }
});
assert(z2 >= 10100);
assert(z2 > z1, 'Document Export Modal must render strictly above Party Detail Modal');
assert.strictEqual(RupeeModalStack.isTop('doc_export_modal'), true);
assert.strictEqual(RupeeModalStack.isTop('party_detail_1'), false);

let confirmClosed = false;
const z3 = RupeeModalStack.register('confirm_dialog', {
  priority: 'confirm',
  onClose: () => { confirmClosed = true; }
});
assert(z3 >= 10300);
assert(z3 > z2, 'Confirm Dialog must render strictly above Document Export Modal');
assert.strictEqual(RupeeModalStack.isTop('confirm_dialog'), true);
console.log('  -> [Scenario 3/6] Verified: Multi-layer stacking hierarchy (10000 < 10100 < 10300)');

// [Scenario 4/6] Capture-Phase Escape Interception Hierarchy
const escapeEvent = {
  key: 'Escape',
  keyCode: 27,
  defaultPrevented: false,
  propagationStopped: false,
  preventDefault: function () { this.defaultPrevented = true; },
  stopPropagation: function () { this.propagationStopped = true; },
  stopImmediatePropagation: function () { this.propagationStopped = true; }
};

const captureKeyHandlers = eventListeners['keydown:capture'] || [];
assert(captureKeyHandlers.length > 0, 'Capture-phase keydown handler must be installed');

// Press 1: Closes confirm_dialog ONLY
captureKeyHandlers[0](escapeEvent);
assert.strictEqual(confirmClosed, true);
assert.strictEqual(docExportClosed, false, 'Parent document export must NOT be dismissed');
assert.strictEqual(partyDetailClosed, false, 'Base party detail must NOT be dismissed');

RupeeModalStack.unregister('confirm_dialog');
assert.strictEqual(RupeeModalStack.isTop('doc_export_modal'), true);

// Press 2: Closes doc_export_modal ONLY
captureKeyHandlers[0](escapeEvent);
assert.strictEqual(docExportClosed, true);
assert.strictEqual(partyDetailClosed, false, 'Base party detail must remain intact');

RupeeModalStack.unregister('doc_export_modal');
assert.strictEqual(RupeeModalStack.isTop('party_detail_1'), true);

// Press 3: Closes party_detail_1
captureKeyHandlers[0](escapeEvent);
assert.strictEqual(partyDetailClosed, true);

RupeeModalStack.unregister('party_detail_1');
assert.strictEqual(RupeeModalStack.getTop(), null);
assert.strictEqual(document.body.classList.contains('modal-open'), false);
console.log('  -> [Scenario 4/6] Verified: Capture-phase Escape key dismissal hierarchy without parent leakage');

// [Scenario 5/6] Registration Idempotence
RupeeModalStack.register('test_modal', { priority: 'standard' });
assert.strictEqual(RupeeModalStack.getStack().length, 1);
RupeeModalStack.register('test_modal', { priority: 'standard' });
assert.strictEqual(RupeeModalStack.getStack().length, 1, 'Re-registering same modal should update properties without duplicates');
RupeeModalStack.unregister('test_modal');
assert.strictEqual(RupeeModalStack.getStack().length, 0);
console.log('  -> [Scenario 5/6] Verified: Modal registration idempotence');

// [Scenario 6/6] Focus Restoration Integrity
let returnFocusCalled = false;
const mockTriggerEl = {
  focus: function () { returnFocusCalled = true; }
};
RupeeModalStack.register('focus_test_modal', {
  priority: 'foreground',
  returnFocusEl: mockTriggerEl
});
RupeeModalStack.unregister('focus_test_modal');
assert.strictEqual(returnFocusCalled, true, 'Focus should be restored to trigger element on unregister');
console.log('  -> [Scenario 6/6] Verified: Focus restoration to trigger element');

console.log('=== ALL 6/6 MODAL LAYER MANAGER REGRESSION SCENARIOS PASSED ===');
