/**
 * snakeAudio.js
 * Billsoft Snake Synthesized Audio Subsystem (Web Audio API)
 * Pure procedural audio synthesis - 0 external assets, 0 network requests.
 */
(function (root, factory) {
  if (typeof define === 'function' && define.amd) {
    define([], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  } else {
    root.SnakeAudio = factory();
  }
}(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  const STORAGE_KEY_MUTED = 'SNAKE_AUDIO_MUTED';
  let audioCtx = null;
  let muted = false;

  // Initialize mute state safely from storage
  try {
    if (typeof localStorage !== 'undefined') {
      const stored = localStorage.getItem(STORAGE_KEY_MUTED);
      if (stored !== null) {
        muted = stored === 'true' || stored === '1';
      }
    }
  } catch (e) {
    muted = false;
  }

  function getAudioContext() {
    if (typeof window === 'undefined') return null;
    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    if (!AudioContextClass) return null;

    if (!audioCtx) {
      try {
        audioCtx = new AudioContextClass();
      } catch (e) {
        return null;
      }
    }
    if (audioCtx && audioCtx.state === 'suspended') {
      audioCtx.resume().catch(function () {});
    }
    return audioCtx;
  }

  function playTone(freq, type, duration, startGain, endGain, freqEnd) {
    if (muted) return;
    const ctx = getAudioContext();
    if (!ctx) return;

    try {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();

      osc.type = type || 'sine';
      osc.frequency.setValueAtTime(freq, ctx.currentTime);
      if (freqEnd && freqEnd !== freq) {
        osc.frequency.exponentialRampToValueAtTime(Math.max(10, freqEnd), ctx.currentTime + duration);
      }

      gain.gain.setValueAtTime(startGain !== undefined ? startGain : 0.15, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(endGain !== undefined ? Math.max(0.0001, endGain) : 0.0001, ctx.currentTime + duration);

      osc.connect(gain);
      gain.connect(ctx.destination);

      osc.start(ctx.currentTime);
      osc.stop(ctx.currentTime + duration);
    } catch (e) {}
  }

  const SnakeAudio = {
    isMuted: function () {
      return muted;
    },

    setMuted: function (val) {
      muted = !!val;
      try {
        if (typeof localStorage !== 'undefined') {
          localStorage.setItem(STORAGE_KEY_MUTED, muted ? 'true' : 'false');
        }
      } catch (e) {}
      return muted;
    },

    playEat: function () {
      if (muted) return;
      playTone(520, 'sine', 0.08, 0.15, 0.01, 880);
    },

    playCrash: function () {
      if (muted) return;
      const ctx = getAudioContext();
      if (!ctx) return;
      try {
        playTone(180, 'sawtooth', 0.25, 0.25, 0.01, 40);
      } catch (e) {}
    },

    playVictory: function () {
      if (muted) return;
      const notes = [440, 554.37, 659.25, 880];
      notes.forEach(function (freq, index) {
        setTimeout(function () {
          playTone(freq, 'triangle', 0.18, 0.18, 0.01);
        }, index * 80);
      });
    },

    playMilestone: function () {
      if (muted) return;
      const chords = [523.25, 659.25, 783.99, 1046.50];
      chords.forEach(function (freq, i) {
        setTimeout(function () {
          playTone(freq, 'sine', 0.25, 0.2, 0.01);
        }, i * 60);
      });
    },

    playPortal: function () {
      if (muted) return;
      playTone(300, 'sine', 0.15, 0.15, 0.01, 600);
    }
  };

  return SnakeAudio;
}));
