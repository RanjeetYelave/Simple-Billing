/**
 * ProgressionExperience.js
 * Trophy, achievement, and travel cutscene reward layers removed.
 * Lightweight stub preserved for module compatibility.
 */

(function (root, factory) {
  if (typeof define === 'function' && define.amd) {
    define(['react'], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory(require('react'));
  } else {
    root.ProgressionExperience = factory(root.React);
  }
}(typeof self !== 'undefined' ? self : this, function (React) {
  'use strict';

  function ProgressionExperienceController() {
    return null;
  }

  return {
    LocationTravelTransition: () => null,
    TrophyCelebrationModal: () => null,
    MajorMilestoneModal: () => null,
    ProgressionExperienceController
  };
}));
