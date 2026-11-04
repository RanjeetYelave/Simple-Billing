/**
 * BounceGame.js
 * Main React component for Nokia Bounce in RupeeCRM.
 * Implements game lifecycle, fixed-timestep physics loop, key trapping,
 * focus/visibility auto-pause, menus, overlays, and H2 persistence.
 */

(function (root, factory) {
  if (typeof define === 'function' && define.amd) {
    define(['react', '../bounce/bounceEngine', '../bounce/bounceLevelGenerator', '../bounce/bounceRenderer', '../bounce/bounceAudio', '../bounce/bouncePersistence'], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory(
      require('react'),
      require('../bounce/bounceEngine'),
      require('../bounce/bounceLevelGenerator'),
      require('../bounce/bounceRenderer'),
      require('../bounce/bounceAudio'),
      require('../bounce/bouncePersistence')
    );
  } else {
    root.BounceGame = factory(
      root.React,
      root.BounceEngine,
      root.BounceLevelGenerator,
      root.BounceRenderer,
      root.BounceAudio,
      root.BouncePersistence
    );
  }
}(typeof self !== 'undefined' ? self : this, function (React, BounceEngine, BounceLevelGenerator, BounceRenderer, BounceAudio, BouncePersistence) {
  'use strict';

  const { useState, useEffect, useRef, useCallback } = React;

  const Engine = BounceEngine || (typeof window !== 'undefined' ? window.BounceEngine : null);
  const Generator = BounceLevelGenerator || (typeof window !== 'undefined' ? window.BounceLevelGenerator : null);
  const Renderer = BounceRenderer || (typeof window !== 'undefined' ? window.BounceRenderer : null);
  const Audio = BounceAudio || (typeof window !== 'undefined' ? window.BounceAudio : null);
  const Persistence = BouncePersistence || (typeof window !== 'undefined' ? window.BouncePersistence : null);

  function BounceGame() {
    // High-level Game States: 'LOADING', 'TITLE', 'PLAYING', 'PAUSED', 'LEVEL_COMPLETE', 'GAME_OVER', 'SETTINGS', 'RECORDS'
    const [gameState, setGameState] = useState('LOADING');
    const [campaign, setCampaign] = useState(null);
    const [level, setLevel] = useState(null);
    const [player, setPlayer] = useState(null);
    const [records, setRecords] = useState([]);
    const [isMuted, setIsMuted] = useState(() => (Audio ? Audio.isMuted() : false));
    const [musicVol, setMusicVol] = useState(() => (Audio ? Audio.getMusicVolume() : 0.5));
    const [soundVol, setSoundVol] = useState(() => (Audio ? Audio.getSoundVolume() : 0.7));

    const canvasRef = useRef(null);
    const containerRef = useRef(null);
    const animFrameRef = useRef(null);
    const lastTimeRef = useRef(0);
    const accumulatorRef = useRef(0);

    // Live mutable state refs for 60Hz loop
    const stateRef = useRef({
      level: null,
      player: null,
      camera: { x: 0, y: 0 },
      input: { left: false, right: false, jump: false },
      gameState: 'LOADING'
    });

    // 1. Initial State Load from H2 Persistence
    useEffect(() => {
      let active = true;
      async function initGame() {
        if (!Persistence) return;
        try {
          const loaded = await Persistence.loadGameState();
          if (!active) return;

          if (loaded && loaded.campaign) {
            setCampaign(loaded.campaign);
            setRecords(loaded.records || []);

            let activeLvl = null;
            if (loaded.currentLevel && loaded.currentLevel.levelDefinition) {
              try {
                activeLvl = typeof loaded.currentLevel.levelDefinition === 'string'
                  ? JSON.parse(loaded.currentLevel.levelDefinition)
                  : loaded.currentLevel.levelDefinition;
              } catch (e) {}
            }

            if (!activeLvl && Generator) {
              activeLvl = Generator.generateLevel(loaded.campaign.currentLevelNumber || 1, { seed: loaded.campaign.seed });
            }

            setLevel(activeLvl);
            stateRef.current.level = activeLvl;

            // Initialize player
            const p = Engine ? Engine.createPlayerState(activeLvl.spawnX, activeLvl.spawnY, loaded.progress ? loaded.progress.remainingLives : 3) : null;
            if (p && loaded.progress) {
              if (loaded.progress.playerX) p.x = loaded.progress.playerX;
              if (loaded.progress.playerY) p.y = loaded.progress.playerY;
              if (loaded.progress.ballState) p.state = loaded.progress.ballState;
              if (loaded.progress.currentScore) p.score = loaded.progress.currentScore;
              if (loaded.progress.checkpointId && activeLvl.checkpoints) {
                const cp = activeLvl.checkpoints.find(c => c.id === loaded.progress.checkpointId);
                if (cp) {
                  cp.active = true;
                  p.lastCheckpoint = { x: cp.x, y: cp.y, id: cp.id };
                }
              }
            }

            setPlayer(p);
            stateRef.current.player = p;

            if (loaded.settings) {
              setIsMuted(loaded.settings.muted);
              setMusicVol(loaded.settings.musicVolume);
              setSoundVol(loaded.settings.soundVolume);
              if (Audio) {
                Audio.setMuted(loaded.settings.muted);
                Audio.setMusicVolume(loaded.settings.musicVolume);
                Audio.setSoundVolume(loaded.settings.soundVolume);
              }
            }
          }

          setGameState('TITLE');
          stateRef.current.gameState = 'TITLE';
        } catch (err) {
          console.error('[BounceGame] Failed to load state:', err);
          setGameState('TITLE');
          stateRef.current.gameState = 'TITLE';
        }
      }

      initGame();
      return () => { active = false; };
    }, []);

    // 2. Keyboard Input Isolation & Trapping
    useEffect(() => {
      const handleKeyDown = (e) => {
        // Intercept game keys only when on the Bounce screen
        const gameKeys = ['ArrowLeft', 'ArrowRight', 'ArrowUp', 'Space', 'KeyA', 'KeyD', 'KeyW', 'Escape'];
        if (gameKeys.includes(e.code) || gameKeys.includes(e.key)) {
          // If playing, prevent browser scroll or shortcut bubbling
          if (stateRef.current.gameState === 'PLAYING') {
            e.preventDefault();
            e.stopPropagation();
          }

          if (e.code === 'ArrowLeft' || e.key === 'ArrowLeft' || e.code === 'KeyA') {
            stateRef.current.input.left = true;
          }
          if (e.code === 'ArrowRight' || e.key === 'ArrowRight' || e.code === 'KeyD') {
            stateRef.current.input.right = true;
          }
          if (e.code === 'ArrowUp' || e.key === 'ArrowUp' || e.code === 'Space' || e.code === 'KeyW') {
            stateRef.current.input.jump = true;
          }
          if (e.code === 'Escape' || e.key === 'Escape') {
            if (stateRef.current.gameState === 'PLAYING') {
              handlePause();
            } else if (stateRef.current.gameState === 'PAUSED') {
              handleResume();
            }
          }
        }
      };

      const handleKeyUp = (e) => {
        if (e.code === 'ArrowLeft' || e.key === 'ArrowLeft' || e.code === 'KeyA') {
          stateRef.current.input.left = false;
        }
        if (e.code === 'ArrowRight' || e.key === 'ArrowRight' || e.code === 'KeyD') {
          stateRef.current.input.right = false;
        }
        if (e.code === 'ArrowUp' || e.key === 'ArrowUp' || e.code === 'Space' || e.code === 'KeyW') {
          stateRef.current.input.jump = false;
        }
      };

      window.addEventListener('keydown', handleKeyDown, { passive: false });
      window.addEventListener('keyup', handleKeyUp, { passive: false });

      return () => {
        window.removeEventListener('keydown', handleKeyDown);
        window.removeEventListener('keyup', handleKeyUp);
      };
    }, []);

    // 3. Tab Visibility & Focus Auto-Pause Lifecycle
    useEffect(() => {
      const handleVisibilityChange = () => {
        if (document.hidden && stateRef.current.gameState === 'PLAYING') {
          handlePause();
        }
      };

      const handleBlur = () => {
        if (stateRef.current.gameState === 'PLAYING') {
          handlePause();
        }
      };

      document.addEventListener('visibilitychange', handleVisibilityChange);
      window.addEventListener('blur', handleBlur);

      return () => {
        document.removeEventListener('visibilitychange', handleVisibilityChange);
        window.removeEventListener('blur', handleBlur);
      };
    }, []);

    // 4. Fixed-Timestep Physics Simulation & Render Loop
    useEffect(() => {
      let animId;

      const mainLoop = (timestamp) => {
        if (!lastTimeRef.current) lastTimeRef.current = timestamp;
        let delta = (timestamp - lastTimeRef.current) / 1000;
        lastTimeRef.current = timestamp;

        // Cap maximum frame delta to prevent spiral of death on lag spikes
        if (delta > 0.1) delta = 0.1;

        if (stateRef.current.gameState === 'PLAYING' && Engine) {
          accumulatorRef.current += delta;
          while (accumulatorRef.current >= Engine.FIXED_DT) {
            const { player: updatedP, events } = Engine.tick(
              stateRef.current.level,
              stateRef.current.player,
              stateRef.current.input,
              Engine.FIXED_DT
            );

            stateRef.current.player = updatedP;
            accumulatorRef.current -= Engine.FIXED_DT;

            // Handle gameplay events
            if (events && events.length > 0) {
              events.forEach(ev => {
                if (ev.sound && Audio) Audio.playSound(ev.sound);

                if (ev.type === 'DEATH') {
                  if (updatedP.lives <= 0) {
                    setGameState('GAME_OVER');
                    stateRef.current.gameState = 'GAME_OVER';
                    if (Audio) Audio.stopMusic();
                  }
                } else if (ev.type === 'LEVEL_COMPLETE') {
                  handleLevelCompleteSuccess();
                } else if (ev.type === 'CHECKPOINT') {
                  saveProgressSnapshot(true);
                }
              });
            }
          }
        }

        // Camera Tracking (240x180 logical canvas)
        const p = stateRef.current.player;
        const lvl = stateRef.current.level;
        if (p && lvl && canvasRef.current) {
          const cw = 240;
          // When player is in Chamber 1 (x <= 120), keep camera.x = 0 to frame the reference screenshot perfectly
          const targetCamX = Math.max(0, p.x - 120);
          const maxCamX = Math.max(0, (lvl.width || 960) - cw);
          stateRef.current.camera.x += (Math.max(0, Math.min(maxCamX, targetCamX)) - stateRef.current.camera.x) * 0.15;
          stateRef.current.camera.y = 0;
        }

        // Render Frame
        const canvas = canvasRef.current;
        if (canvas && Renderer && stateRef.current.level && stateRef.current.player) {
          const ctx = canvas.getContext('2d');
          Renderer.render(
            ctx,
            stateRef.current.level,
            stateRef.current.player,
            stateRef.current.camera,
            canvas.width,
            canvas.height,
            timestamp / 1000
          );
        }

        animId = requestAnimationFrame(mainLoop);
      };

      animId = requestAnimationFrame(mainLoop);
      return () => {
        cancelAnimationFrame(animId);
        if (Audio) Audio.stopMusic();
      };
    }, []);

    // 5. Actions & Handlers
    const handleStartPlaying = () => {
      setGameState('PLAYING');
      stateRef.current.gameState = 'PLAYING';
      if (Audio) Audio.startMusic();
    };

    const handlePause = () => {
      setGameState('PAUSED');
      stateRef.current.gameState = 'PAUSED';
      if (Audio) Audio.stopMusic();
      saveProgressSnapshot(true);
    };

    const handleResume = () => {
      setGameState('PLAYING');
      stateRef.current.gameState = 'PLAYING';
      if (Audio) Audio.startMusic();
    };

    const handleRestartLevel = () => {
      if (!level || !Generator || !campaign) return;
      const freshLvl = Generator.generateLevel(campaign.currentLevelNumber, { seed: campaign.seed + campaign.currentLevelNumber * 31 });
      const freshP = Engine ? Engine.createPlayerState(freshLvl.spawnX, freshLvl.spawnY, 3) : null;

      setLevel(freshLvl);
      setPlayer(freshP);
      stateRef.current.level = freshLvl;
      stateRef.current.player = freshP;
      stateRef.current.camera = { x: 0, y: 0 };

      setGameState('PLAYING');
      stateRef.current.gameState = 'PLAYING';
      if (Audio) Audio.startMusic();
      saveProgressSnapshot(true);
    };

    const handleNewGame = async () => {
      if (!Persistence || !Generator) return;
      const freshSeed = 100000 + Math.floor(Math.random() * 900000);
      const lvl1 = Generator.generateLevel(1, { seed: freshSeed });

      const state = await Persistence.startNewCampaign(freshSeed);
      if (state && state.campaign) {
        setCampaign(state.campaign);
        setLevel(lvl1);
        stateRef.current.level = lvl1;

        const p = Engine ? Engine.createPlayerState(lvl1.spawnX, lvl1.spawnY, 3) : null;
        setPlayer(p);
        stateRef.current.player = p;
        stateRef.current.camera = { x: 0, y: 0 };

        setGameState('PLAYING');
        stateRef.current.gameState = 'PLAYING';
        if (Audio) Audio.startMusic();
      }
    };

    const handleResetProgress = async () => {
      if (!window.confirm('Reset all Bounce campaign progress to Level 1? (Records will be preserved)')) return;
      if (!Persistence || !Generator) return;

      const state = await Persistence.resetCampaign();
      if (state && state.campaign) {
        setCampaign(state.campaign);
        const lvl1 = Generator.generateLevel(1, { seed: state.campaign.seed });
        setLevel(lvl1);
        stateRef.current.level = lvl1;

        const p = Engine ? Engine.createPlayerState(lvl1.spawnX, lvl1.spawnY, 3) : null;
        setPlayer(p);
        stateRef.current.player = p;
        stateRef.current.camera = { x: 0, y: 0 };

        setGameState('TITLE');
        stateRef.current.gameState = 'TITLE';
        if (Audio) Audio.stopMusic();
      }
    };

    const handleLevelCompleteSuccess = async () => {
      setGameState('LEVEL_COMPLETE');
      stateRef.current.gameState = 'LEVEL_COMPLETE';
      if (Audio) {
        Audio.stopMusic();
        Audio.playSound('victory');
      }

      const p = stateRef.current.player;
      const c = campaign;
      if (!p || !c || !Persistence || !Generator) return;

      const currentLvlNum = c.currentLevelNumber || 1;
      const nextLvlNum = currentLvlNum + 1;
      const nextLvlSeed = c.seed + nextLvlNum * 47;
      const nextLvl = Generator.generateLevel(nextLvlNum, { seed: nextLvlSeed });

      const completionData = {
        campaignId: c.id,
        levelNumber: currentLvlNum,
        completionTimeSeconds: p.gameplayTime,
        score: p.score,
        hoopsCollected: (stateRef.current.level.hoops || []).filter(h => h.collected).length,
        totalHoops: (stateRef.current.level.hoops || []).length,
        nextLevelDefinition: JSON.stringify(nextLvl),
        nextLevelSeed: nextLvlSeed,
        nextEnvironment: nextLvl.environment,
        nextDifficulty: nextLvl.difficulty
      };

      const res = await Persistence.completeLevel(completionData);
      if (res && res.campaign) {
        setCampaign(res.campaign);
        setRecords(res.records || []);
        setLevel(nextLvl);
        stateRef.current.level = nextLvl;

        const nextP = Engine ? Engine.createPlayerState(nextLvl.spawnX, nextLvl.spawnY, p.lives) : null;
        if (nextP) nextP.score = p.score;
        setPlayer(nextP);
        stateRef.current.player = nextP;
        stateRef.current.camera = { x: 0, y: 0 };
      }
    };

    const saveProgressSnapshot = (immediate = false) => {
      const p = stateRef.current.player;
      const c = campaign;
      if (!p || !c || !Persistence) return;

      const snapshot = {
        campaignId: c.id,
        levelNumber: c.currentLevelNumber,
        playerX: p.x,
        playerY: p.y,
        playerVx: p.vx,
        playerVy: p.vy,
        ballState: p.state,
        remainingLives: p.lives,
        checkpointId: p.lastCheckpoint ? p.lastCheckpoint.id : null,
        currentScore: p.score,
        gameplayTimeSeconds: p.gameplayTime,
        snapshotVersion: 1
      };

      Persistence.saveProgress(snapshot, immediate);
    };

    const handleToggleMute = () => {
      const next = !isMuted;
      setIsMuted(next);
      if (Audio) Audio.setMuted(next);
      if (Persistence) Persistence.updateSettings({ musicVolume: musicVol, soundVolume: soundVol, muted: next });
    };

    return React.createElement('div', {
      className: 'bounce-container',
      ref: containerRef,
      style: {
        width: '100%',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        background: '#090d16',
        color: '#f8fafc',
        position: 'relative',
        userSelect: 'none',
        overflow: 'hidden',
        boxSizing: 'border-box'
      }
    }, [
      // 1. Interactive Canvas Stage (Fixed 240 x 180 Native Nokia Resolution)
      React.createElement('canvas', {
        key: 'bounce-canvas',
        ref: canvasRef,
        width: 240,
        height: 180,
        style: {
          display: 'block',
          width: '100%',
          maxWidth: '640px',
          height: 'auto',
          aspectRatio: '240 / 180',
          background: '#98cbdf',
          borderRadius: 4,
          boxShadow: '0 12px 36px rgba(0,0,0,0.7)',
          border: '3px solid #07448c',
          imageRendering: 'pixelated'
        }
      }),

      // 2. Title Screen Overlay
      gameState === 'TITLE' ? React.createElement('div', {
        key: 'title-overlay',
        style: {
          position: 'absolute',
          inset: 0,
          background: 'rgba(9, 13, 22, 0.88)',
          backdropFilter: 'blur(8px)',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 16,
          zIndex: 20
        }
      }, [
        React.createElement('div', { key: 't-icon', style: { fontSize: '48px', marginBottom: -10 } }, '🔴'),
        React.createElement('h1', {
          key: 't-title',
          style: {
            fontSize: '36px',
            fontWeight: 900,
            letterSpacing: '0.08em',
            color: '#ef4444',
            textShadow: '0 0 20px rgba(239, 68, 68, 0.6)',
            margin: 0
          }
        }, 'NOKIA BOUNCE'),
        React.createElement('div', {
          key: 't-sub',
          style: { fontSize: '14px', color: '#94a3b8', marginBottom: 12 }
        }, `Classic 7210 Arcade Edition • Level ${campaign ? campaign.currentLevelNumber : 1}`),

        // Action Buttons
        React.createElement('button', {
          key: 'btn-play',
          onClick: handleStartPlaying,
          className: 'btn btn-primary',
          style: { width: 220, padding: '12px 24px', fontSize: '16px', fontWeight: 800, borderRadius: 8 }
        }, '▶ PLAY'),

        React.createElement('button', {
          key: 'btn-new',
          onClick: handleNewGame,
          className: 'btn btn-secondary',
          style: { width: 220, padding: '10px 24px', fontSize: '14px', fontWeight: 700, borderRadius: 8 }
        }, '🌟 NEW CAMPAIGN'),

        React.createElement('button', {
          key: 'btn-records',
          onClick: () => setGameState('RECORDS'),
          className: 'btn btn-secondary',
          style: { width: 220, padding: '10px 24px', fontSize: '14px', fontWeight: 700, borderRadius: 8 }
        }, '🏆 HIGH SCORES'),

        React.createElement('button', {
          key: 'btn-mute',
          onClick: handleToggleMute,
          className: 'btn btn-secondary',
          style: { width: 220, padding: '8px 24px', fontSize: '13px', borderRadius: 8 }
        }, isMuted ? '🔇 Audio: Muted' : '🔊 Audio: On')
      ]) : null,

      // 3. Pause Screen Overlay
      gameState === 'PAUSED' ? React.createElement('div', {
        key: 'pause-overlay',
        style: {
          position: 'absolute',
          inset: 0,
          background: 'rgba(9, 13, 22, 0.85)',
          backdropFilter: 'blur(6px)',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 14,
          zIndex: 20
        }
      }, [
        React.createElement('h2', { key: 'p-title', style: { fontSize: '28px', fontWeight: 800, color: '#38bdf8', margin: 0 } }, 'GAME PAUSED'),
        React.createElement('p', { key: 'p-tip', style: { color: '#94a3b8', fontSize: '13px', margin: 0 } }, 'Use Arrow Keys to Roll • Space to Jump'),

        React.createElement('button', {
          key: 'p-resume',
          onClick: handleResume,
          className: 'btn btn-primary',
          style: { width: 200, padding: '10px 24px', fontWeight: 700, borderRadius: 8 }
        }, '▶ RESUME'),

        React.createElement('button', {
          key: 'p-restart',
          onClick: handleRestartLevel,
          className: 'btn btn-secondary',
          style: { width: 200, padding: '10px 24px', fontWeight: 700, borderRadius: 8 }
        }, '🔄 RESTART LEVEL'),

        React.createElement('button', {
          key: 'p-title-btn',
          onClick: () => { setGameState('TITLE'); stateRef.current.gameState = 'TITLE'; },
          className: 'btn btn-secondary',
          style: { width: 200, padding: '10px 24px', fontWeight: 700, borderRadius: 8 }
        }, '🚪 TITLE MENU')
      ]) : null,

      // 4. Level Complete Overlay
      gameState === 'LEVEL_COMPLETE' ? React.createElement('div', {
        key: 'complete-overlay',
        style: {
          position: 'absolute',
          inset: 0,
          background: 'rgba(9, 13, 22, 0.9)',
          backdropFilter: 'blur(8px)',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 16,
          zIndex: 20
        }
      }, [
        React.createElement('div', { key: 'c-icon', style: { fontSize: '42px' } }, '🎉'),
        React.createElement('h2', { key: 'c-title', style: { fontSize: '28px', fontWeight: 900, color: '#4ade80', margin: 0 } }, 'LEVEL MASTERED!'),
        React.createElement('div', { key: 'c-score', style: { fontSize: '16px', color: '#fef08a' } }, `Score: ${(player ? player.score : 0).toLocaleString()} PTS`),

        React.createElement('button', {
          key: 'c-next',
          onClick: handleStartPlaying,
          className: 'btn btn-primary',
          style: { width: 220, padding: '12px 24px', fontSize: '16px', fontWeight: 800, borderRadius: 8, marginTop: 12 }
        }, '⏩ NEXT LEVEL')
      ]) : null,

      // 5. Game Over Overlay
      gameState === 'GAME_OVER' ? React.createElement('div', {
        key: 'gameover-overlay',
        style: {
          position: 'absolute',
          inset: 0,
          background: 'rgba(9, 13, 22, 0.92)',
          backdropFilter: 'blur(8px)',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 16,
          zIndex: 20
        }
      }, [
        React.createElement('div', { key: 'go-icon', style: { fontSize: '44px' } }, '💥'),
        React.createElement('h2', { key: 'go-title', style: { fontSize: '30px', fontWeight: 900, color: '#ef4444', margin: 0 } }, 'GAME OVER'),
        React.createElement('p', { key: 'go-score', style: { color: '#cbd5e1', fontSize: '15px', margin: 0 } }, `Final Score: ${(player ? player.score : 0).toLocaleString()}`),

        React.createElement('button', {
          key: 'go-retry',
          onClick: handleRestartLevel,
          className: 'btn btn-primary',
          style: { width: 200, padding: '10px 24px', fontWeight: 700, borderRadius: 8, marginTop: 12 }
        }, '🔄 TRY AGAIN'),

        React.createElement('button', {
          key: 'go-title-btn',
          onClick: () => { setGameState('TITLE'); stateRef.current.gameState = 'TITLE'; },
          className: 'btn btn-secondary',
          style: { width: 200, padding: '10px 24px', fontWeight: 700, borderRadius: 8 }
        }, '🚪 TITLE MENU')
      ]) : null,

      // 6. High Scores / Records Overlay
      gameState === 'RECORDS' ? React.createElement('div', {
        key: 'records-overlay',
        style: {
          position: 'absolute',
          inset: 0,
          background: 'rgba(9, 13, 22, 0.94)',
          backdropFilter: 'blur(8px)',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          padding: 24,
          zIndex: 20
        }
      }, [
        React.createElement('h2', { key: 'r-title', style: { fontSize: '24px', fontWeight: 800, color: '#f59e0b', marginBottom: 16 } }, '🏆 HALL OF RECORDS'),
        React.createElement('div', {
          key: 'r-table-wrap',
          style: { width: '100%', maxWidth: 440, maxHeight: 260, overflowY: 'auto', marginBottom: 20 }
        }, [
          React.createElement('table', { key: 'r-table', style: { width: '100%', borderCollapse: 'collapse', fontSize: '13px' } }, [
            React.createElement('thead', { key: 'r-thead' },
              React.createElement('tr', { style: { borderBottom: '1px solid #334155', color: '#94a3b8' } }, [
                React.createElement('th', { style: { padding: '6px 8px', textAlign: 'left' } }, 'Level'),
                React.createElement('th', { style: { padding: '6px 8px', textAlign: 'right' } }, 'Best Score'),
                React.createElement('th', { style: { padding: '6px 8px', textAlign: 'right' } }, 'Best Time')
              ])
            ),
            React.createElement('tbody', { key: 'r-tbody' },
              (records && records.length > 0) ? records.map(r =>
                React.createElement('tr', { key: `rec-${r.levelNumber}`, style: { borderBottom: '1px solid #1e293b' } }, [
                  React.createElement('td', { style: { padding: '6px 8px', fontWeight: 700, color: '#38bdf8' } }, `Level ${r.levelNumber}`),
                  React.createElement('td', { style: { padding: '6px 8px', textAlign: 'right' } }, (r.bestScore || 0).toLocaleString()),
                  React.createElement('td', { style: { padding: '6px 8px', textAlign: 'right', color: '#4ade80' } }, `${(r.bestTimeSeconds || 0).toFixed(1)}s`)
                ])
              ) : React.createElement('tr', null,
                React.createElement('td', { colSpan: 3, style: { padding: 16, textAlign: 'center', color: '#64748b' } }, 'No completed level records yet')
              )
            )
          ])
        ]),

        React.createElement('button', {
          key: 'r-back',
          onClick: () => { setGameState('TITLE'); stateRef.current.gameState = 'TITLE'; },
          className: 'btn btn-secondary',
          style: { width: 180, padding: '8px 20px', borderRadius: 8 }
        }, '← Back')
      ]) : null,

      // 7. Bottom Navigation & Action Strip
      React.createElement('div', {
        key: 'bounce-bottom-strip',
        style: {
          width: '100%',
          maxWidth: '960px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '8px 16px',
          marginTop: 8,
          fontSize: '12px',
          color: '#94a3b8'
        }
      }, [
        React.createElement('div', { key: 'str-left' }, 'Controls: ← / → Roll • Space / ↑ Jump • Esc Pause'),
        React.createElement('div', { key: 'str-right', style: { display: 'flex', gap: 10 } }, [
          gameState === 'PLAYING' ? React.createElement('button', {
            key: 'btn-strip-pause',
            onClick: handlePause,
            className: 'btn btn-sm btn-secondary',
            style: { padding: '4px 12px', fontSize: '11px', borderRadius: 4 }
          }, '⏸ Pause') : null,
          React.createElement('button', {
            key: 'btn-strip-reset',
            onClick: handleResetProgress,
            className: 'btn btn-sm btn-secondary',
            style: { padding: '4px 12px', fontSize: '11px', borderRadius: 4, color: '#f87171' }
          }, 'Reset Progress')
        ])
      ])
    ]);
  }

  return BounceGame;
}));
