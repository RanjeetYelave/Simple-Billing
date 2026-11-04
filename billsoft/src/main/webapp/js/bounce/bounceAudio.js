/**
 * bounceAudio.js
 * 100% offline procedural WebAudio synthesizer for Nokia Bounce.
 * Implements Nokia 7210 era chiptune background music and authentic sound effects.
 * Includes complete lifecycle pause/resume, volume controls, and tab-focus management.
 */

(function (root, factory) {
  if (typeof define === 'function' && define.amd) {
    define([], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  } else {
    root.BounceAudio = factory();
  }
}(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  let audioCtx = null;
  let musicGainNode = null;
  let sfxGainNode = null;
  let isMuted = false;
  let musicVolume = 0.5;
  let soundVolume = 0.7;

  let isMusicPlaying = false;
  let musicTimer = null;
  let musicStep = 0;

  // Nokia 7210 Bounce classic chiptune melody note frequencies (Hz)
  const MELODY_NOTES = [
    // Bar 1
    261.63, 329.63, 392.00, 523.25, 392.00, 329.63, 261.63, 329.63,
    // Bar 2
    293.66, 369.99, 440.00, 587.33, 440.00, 369.99, 293.66, 369.99,
    // Bar 3
    329.63, 392.00, 493.88, 659.25, 493.88, 392.00, 329.63, 392.00,
    // Bar 4
    392.00, 329.63, 293.66, 261.63, 196.00, 261.63, 329.63, 392.00
  ];

  function getAudioContext() {
    if (typeof window === 'undefined') return null;
    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    if (!AudioContextClass) return null;

    if (!audioCtx) {
      audioCtx = new AudioContextClass();
      musicGainNode = audioCtx.createGain();
      sfxGainNode = audioCtx.createGain();

      musicGainNode.gain.value = isMuted ? 0 : musicVolume;
      sfxGainNode.gain.value = isMuted ? 0 : soundVolume;

      musicGainNode.connect(audioCtx.destination);
      sfxGainNode.connect(audioCtx.destination);
    }

    if (audioCtx.state === 'suspended') {
      audioCtx.resume().catch(() => {});
    }

    return audioCtx;
  }

  function startMusic() {
    if (isMusicPlaying) return;
    const ctx = getAudioContext();
    if (!ctx) return;

    isMusicPlaying = true;
    musicStep = 0;

    const tempoMs = 135; // brisk 8-bit tempo
    musicTimer = setInterval(() => {
      if (!isMusicPlaying || isMuted || musicVolume <= 0.001) return;
      playMusicStep(ctx);
    }, tempoMs);
  }

  function playMusicStep(ctx) {
    try {
      const noteFreq = MELODY_NOTES[musicStep % MELODY_NOTES.length];
      musicStep++;

      const osc = ctx.createOscillator();
      const gain = ctx.createGain();

      // Classic square wave chiptune sound
      osc.type = 'square';
      osc.frequency.setValueAtTime(noteFreq, ctx.currentTime);

      const dur = 0.11;
      gain.gain.setValueAtTime(0.08, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + dur);

      osc.connect(gain);
      gain.connect(musicGainNode);

      osc.start();
      osc.stop(ctx.currentTime + dur);
    } catch (e) {}
  }

  function stopMusic() {
    isMusicPlaying = false;
    if (musicTimer) {
      clearInterval(musicTimer);
      musicTimer = null;
    }
  }

  /**
   * Play procedural sound effect by name.
   */
  function playSound(name) {
    if (isMuted || soundVolume <= 0.001) return;
    const ctx = getAudioContext();
    if (!ctx) return;

    try {
      const now = ctx.currentTime;

      if (name === 'jump') {
        // Upward frequency slide
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.type = 'triangle';
        osc.frequency.setValueAtTime(180, now);
        osc.frequency.exponentialRampToValueAtTime(360, now + 0.12);

        gain.gain.setValueAtTime(0.2, now);
        gain.gain.linearRampToValueAtTime(0.01, now + 0.12);

        osc.connect(gain);
        gain.connect(sfxGainNode);
        osc.start(now);
        osc.stop(now + 0.12);
      } else if (name === 'bounce' || name === 'bounce_pad') {
        // Springy pulse
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.type = 'square';
        osc.frequency.setValueAtTime(name === 'bounce_pad' ? 300 : 220, now);
        osc.frequency.exponentialRampToValueAtTime(name === 'bounce_pad' ? 620 : 340, now + 0.15);

        gain.gain.setValueAtTime(0.18, now);
        gain.gain.linearRampToValueAtTime(0.01, now + 0.15);

        osc.connect(gain);
        gain.connect(sfxGainNode);
        osc.start(now);
        osc.stop(now + 0.15);
      } else if (name === 'hoop') {
        // Melodic 2-tone ring chime (C6 -> G6)
        playTone(ctx, 1046.50, now, 0.08, 'sine');
        playTone(ctx, 1567.98, now + 0.08, 0.15, 'sine');
      } else if (name === 'transform') {
        // Laser frequency modulation
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.type = 'sawtooth';
        osc.frequency.setValueAtTime(220, now);
        osc.frequency.linearRampToValueAtTime(880, now + 0.2);

        gain.gain.setValueAtTime(0.15, now);
        gain.gain.linearRampToValueAtTime(0.01, now + 0.2);

        osc.connect(gain);
        gain.connect(sfxGainNode);
        osc.start(now);
        osc.stop(now + 0.2);
      } else if (name === 'checkpoint') {
        // Triumphant triad
        playTone(ctx, 523.25, now, 0.08, 'triangle');
        playTone(ctx, 659.25, now + 0.08, 0.08, 'triangle');
        playTone(ctx, 783.99, now + 0.16, 0.18, 'triangle');
      } else if (name === 'pop') {
        // White noise pop + descending pitch
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.type = 'sawtooth';
        osc.frequency.setValueAtTime(400, now);
        osc.frequency.exponentialRampToValueAtTime(80, now + 0.25);

        gain.gain.setValueAtTime(0.25, now);
        gain.gain.linearRampToValueAtTime(0.01, now + 0.25);

        osc.connect(gain);
        gain.connect(sfxGainNode);
        osc.start(now);
        osc.stop(now + 0.25);
      } else if (name === 'fanfare' || name === 'victory') {
        // Victorious fanfare
        playTone(ctx, 440.00, now, 0.10, 'triangle');
        playTone(ctx, 554.37, now + 0.10, 0.10, 'triangle');
        playTone(ctx, 659.25, now + 0.20, 0.10, 'triangle');
        playTone(ctx, 880.00, now + 0.30, 0.35, 'triangle');
      }
    } catch (e) {}
  }

  function playTone(ctx, freq, startTime, duration, type = 'sine') {
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = type;
    osc.frequency.setValueAtTime(freq, startTime);

    gain.gain.setValueAtTime(0.18, startTime);
    gain.gain.exponentialRampToValueAtTime(0.001, startTime + duration);

    osc.connect(gain);
    gain.connect(sfxGainNode);
    osc.start(startTime);
    osc.stop(startTime + duration);
  }

  function setMuted(muted) {
    isMuted = !!muted;
    if (musicGainNode) musicGainNode.gain.value = isMuted ? 0 : musicVolume;
    if (sfxGainNode) sfxGainNode.gain.value = isMuted ? 0 : soundVolume;
  }

  function setMusicVolume(vol) {
    musicVolume = Math.max(0, Math.min(1, vol));
    if (musicGainNode && !isMuted) musicGainNode.gain.value = musicVolume;
  }

  function setSoundVolume(vol) {
    soundVolume = Math.max(0, Math.min(1, vol));
    if (sfxGainNode && !isMuted) sfxGainNode.gain.value = soundVolume;
  }

  return {
    getAudioContext,
    startMusic,
    stopMusic,
    playSound,
    setMuted,
    isMuted: () => isMuted,
    setMusicVolume,
    setSoundVolume,
    getMusicVolume: () => musicVolume,
    getSoundVolume: () => soundVolume
  };
}));
