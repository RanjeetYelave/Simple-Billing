/**
 * MaharashtraJourney.js
 * Retained lightweight adapter querying MaharashtraWorld progression metadata.
 * Expedition UI, interactive cartography maps, and fog-of-war layers removed.
 */

(function (root, factory) {
  if (typeof define === 'function' && define.amd) {
    define(['react', '../maharashtraWorld'], factory);
  } else if (typeof module === 'object' && module.exports) {
    let R = null;
    try { R = require('react'); } catch (e) { R = (typeof global !== 'undefined' && global.React) ? global.React : (root && root.React ? root.React : null); }
    module.exports = factory(R, require('../maharashtraWorld'));
  } else {
    root.MaharashtraJourney = factory(root.React, root.MaharashtraWorld);
  }
}(typeof self !== 'undefined' ? self : (typeof global !== 'undefined' ? global : this), function (React, MaharashtraWorld) {
  'use strict';

  const WorldEngine = MaharashtraWorld || (typeof window !== 'undefined' ? window.MaharashtraWorld : null);

  function MaharashtraJourney({ currentLevel = 1 }) {
    if (WorldEngine && typeof WorldEngine.getWorldForLevel === 'function') {
      WorldEngine.getWorldForLevel('classic', currentLevel);
    }
    if (WorldEngine && typeof WorldEngine.getJourneyTimeline === 'function') {
      WorldEngine.getJourneyTimeline('classic', currentLevel, 15, 'all');
    }
    return null;
  }

  return MaharashtraJourney;
}));
