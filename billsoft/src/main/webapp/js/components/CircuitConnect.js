/**
 * CircuitConnect.js
 * Minimal, polished, embedded puzzle game React component for RupeeCRM.
 * Novelty-First Expansion: Splitters, Bridges, Locks, Switches, Multi-Colors/Sources,
 * Rotating Groups, Wildcards, and Bonus Targets.
 * Pure React 18 (React.createElement) and zero external game engines.
 */

(function (root, factory) {
  if (typeof define === 'function' && define.amd) {
    define(['react', '../circuitEngine', '../circuitGenerator', '../circuitPersistence', '../circuitAudio'], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory(
      require('react'),
      require('../circuitEngine'),
      require('../circuitGenerator'),
      require('../circuitPersistence'),
      require('../circuitAudio')
    );
  } else {
    root.CircuitConnect = factory(
      root.React,
      root.CircuitEngine,
      root.CircuitGenerator,
      root.CircuitPersistence,
      root.CircuitAudio
    );
  }
}(typeof self !== 'undefined' ? self : this, function (React, CircuitEngine, CircuitGenerator, CircuitPersistence, CircuitAudio) {
  'use strict';

  const { useState, useEffect, useRef, useCallback } = React;
  const Engine = CircuitEngine || (typeof window !== 'undefined' ? window.CircuitEngine : null);
  const Generator = CircuitGenerator || (typeof window !== 'undefined' ? window.CircuitGenerator : null);
  const Persistence = CircuitPersistence || (typeof window !== 'undefined' ? window.CircuitPersistence : null);
  const Audio = CircuitAudio || (typeof window !== 'undefined' ? window.CircuitAudio : null);

  function renderModalPortal(element) {
    if (typeof document !== 'undefined' && document.body && typeof ReactDOM !== 'undefined' && ReactDOM.createPortal) {
      return ReactDOM.createPortal(element, document.body);
    }
    return element;
  }

  const HINT_INTERVAL_SECONDS = 1800; // 30 minutes of active play per hint

  // Mechanic introduction cards metadata
  const MECHANIC_INFO = {
    SPLITTER: {
      key: 'SPLITTER',
      name: 'Splitter',
      icon: '⚡',
      tagline: 'Branching Power',
      desc: 'A directional branching circuit that feeds multiple downstream targets simultaneously.'
    },
    BRIDGE: {
      key: 'BRIDGE',
      name: 'Bridge / Crossover',
      icon: '🔀',
      tagline: 'Layered Circuits',
      desc: 'Two independent circuits cross over each other without electrically connecting.'
    },
    LOCK: {
      key: 'LOCK',
      name: 'Locked Tile',
      icon: '🔒',
      tagline: 'Prerequisite Required',
      desc: 'This tile is locked and cannot rotate until its prerequisite circuit path is powered.'
    },
    SWITCH: {
      key: 'SWITCH',
      name: 'Switch / Toggle',
      icon: '🎚',
      tagline: 'Dual State Routing',
      desc: 'Clicking this switch toggles between two predefined circuit paths.'
    },
    COLOR: {
      key: 'COLOR',
      name: 'Colored Circuits',
      icon: '🔴',
      tagline: 'Color-Coded Sources',
      desc: 'Power sources only activate matching color targets (Red 🔴, Blue 🔷, Amber ⭐).'
    },
    GROUP: {
      key: 'GROUP',
      name: 'Rotating Group',
      icon: '🔄',
      tagline: 'Synchronized Cluster',
      desc: 'Rotating any tile in this highlighted 2×2 group rotates the entire cluster together.'
    },
    WILDCARD: {
      key: 'WILDCARD',
      name: 'Wildcard Tile',
      icon: '✨',
      tagline: 'Universal Adapter',
      desc: 'This universal tile dynamically connects to all compatible energized neighbors.'
    },
    BONUS: {
      key: 'BONUS',
      name: 'Bonus Objective',
      icon: '💎',
      tagline: 'Optional Star Crystals',
      desc: 'Powering optional bonus crystals awards bonus stars without blocking level completion.'
    }
  };

  // Deterministic progressive trophy tiers supporting infinite progression
  const TROPHY_TIERS = [
    { maxMilestone: 5,   name: 'Bronze Apprentice',   icon: '🥉', color: '#cd7f32', tier: 'Bronze' },
    { maxMilestone: 10,  name: 'Silver Technician',   icon: '🥈', color: '#c0c0c0', tier: 'Silver' },
    { maxMilestone: 20,  name: 'Gold Engineer',       icon: '🥇', color: '#ffd700', tier: 'Gold' },
    { maxMilestone: 50,  name: 'Platinum Specialist', icon: '💎', color: '#00e5ff', tier: 'Platinum' },
    { maxMilestone: 100, name: 'Diamond Architect',   icon: '👑', color: '#e040fb', tier: 'Diamond' },
    { maxMilestone: Infinity, name: 'Cosmic Grandmaster', icon: '⚡', color: '#ff9100', tier: 'Cosmic' }
  ];

  function getTrophyForMilestone(milestoneNumber) {
    if (!milestoneNumber || milestoneNumber < 1) return null;
    for (let i = 0; i < TROPHY_TIERS.length; i++) {
      if (milestoneNumber <= TROPHY_TIERS[i].maxMilestone) {
        const t = TROPHY_TIERS[i];
        return {
          milestoneNumber,
          starThreshold: milestoneNumber * 50,
          tier: t.tier,
          name: `${t.name} (M${milestoneNumber})`,
          icon: t.icon,
          color: t.color
        };
      }
    }
    const last = TROPHY_TIERS[TROPHY_TIERS.length - 1];
    return {
      milestoneNumber,
      starThreshold: milestoneNumber * 50,
      tier: last.tier,
      name: `${last.name} (M${milestoneNumber})`,
      icon: last.icon,
      color: last.color
    };
  }

  function getNextMilestoneStars(totalStars) {
    const stars = Math.max(0, Math.floor(totalStars || 0));
    return (Math.floor(stars / 50) + 1) * 50;
  }

  // Active level elapsed time (MM:SS)
  function formatActiveTime(seconds) {
    const s = Math.max(0, Math.floor(seconds || 0));
    const mins = Math.floor(s / 60);
    const secs = s % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  }

  // Lifetime cumulative play time strictly formatted as HH:MM
  function formatLifetimeTime(seconds) {
    const s = Math.max(0, Math.floor(seconds || 0));
    const hours = Math.floor(s / 3600);
    const mins = Math.floor((s % 3600) / 60);
    return `${hours.toString().padStart(2, '0')}:${mins.toString().padStart(2, '0')}`;
  }

  function CircuitConnect() {
    const [progression, setProgression] = useState(null);
    const [currentPuzzle, setCurrentPuzzle] = useState(null);
    const [energizedSet, setEnergizedSet] = useState(new Set());
    const [unlockedSet, setUnlockedSet] = useState(new Set());
    const [energizedDetails, setEnergizedDetails] = useState(new Map());
    const [depthMap, setDepthMap] = useState(new Map());
    const [hintHighlights, setHintHighlights] = useState([]);
    const [activeHintData, setActiveHintData] = useState(null);
    const [hintBalance, setHintBalance] = useState(0);
    const [hintProgressSeconds, setHintProgressSeconds] = useState(0);
    const [isSolved, setIsSolved] = useState(false);
    const [boardTransition, setBoardTransition] = useState('idle'); // 'idle' | 'exiting' | 'entering'
    const [moves, setMoves] = useState(0);
    const [elapsedSeconds, setElapsedSeconds] = useState(0);
    const [showInfoModal, setShowInfoModal] = useState(false);
    const [showSkipModal, setShowSkipModal] = useState(false);
    const [activeIntroModal, setActiveIntroModal] = useState(null); // Mechanic card to show
    const [milestoneCelebration, setMilestoneCelebration] = useState(null);
    const [levelMilestoneCelebration, setLevelMilestoneCelebration] = useState(null);
    const [levelWonStars, setLevelWonStars] = useState(null);
    const [lockedRattleIdx, setLockedRattleIdx] = useState(null);
    const [containerDimensions, setContainerDimensions] = useState({ width: 600, height: 400 });

    const containerRef = useRef(null);
    const timerRef = useRef(null);
    const levelMilestoneTimerRef = useRef(null);
    const currentPuzzleRef = useRef(null);
    const movesRef = useRef(0);
    const elapsedSecondsRef = useRef(0);
    const hintHighlightsRef = useRef([]);
    const hintBalanceRef = useRef(0);
    const hintProgressSecondsRef = useRef(0);
    const progressionRef = useRef(null);
    const isSolvedRef = useRef(false);
    const advanceTimeoutRef = useRef(null);

    currentPuzzleRef.current = currentPuzzle;
    movesRef.current = moves;
    elapsedSecondsRef.current = elapsedSeconds;
    hintHighlightsRef.current = hintHighlights;
    hintBalanceRef.current = hintBalance;
    hintProgressSecondsRef.current = hintProgressSeconds;
    progressionRef.current = progression;
    isSolvedRef.current = isSolved;

    // Helper: Check and queue one-time mechanic intro cards for new mechanics
    const checkMechanicIntroductions = useCallback((board, userProgression) => {
      if (!board || !board.activeMechanics || !board.activeMechanics.length) return;
      const introduced = new Set(userProgression.introducedMechanics || []);
      for (const mech of board.activeMechanics) {
        if (!introduced.has(mech) && MECHANIC_INFO[mech]) {
          setActiveIntroModal(MECHANIC_INFO[mech]);
          break; // Show one at a time
        }
      }
    }, []);

    // Helper: Restore an existing verified board
    const restoreBoard = useCallback((boardData, userProg) => {
      setCurrentPuzzle(boardData);
      setMoves(boardData.moves || 0);
      setElapsedSeconds(boardData.elapsedSeconds || 0);
      setHintHighlights(boardData.hintHighlights || []);
      setIsSolved(false);
      setLevelWonStars(null);
      setBoardTransition('entering');

      const evalResult = Engine.evaluateCircuit(
        boardData.tiles,
        boardData.width,
        boardData.height,
        boardData.sources || boardData.sourceIdx,
        boardData.targetIndices,
        boardData.bonusTargetIndices || []
      );
      setEnergizedSet(evalResult.energizedSet);
      setUnlockedSet(evalResult.unlockedSet || new Set());
      setEnergizedDetails(evalResult.energizedDetails || new Map());
      setDepthMap(evalResult.depthMap || new Map());

      if (userProg) {
        checkMechanicIntroductions(boardData, userProg);
      }

      setTimeout(() => {
        setBoardTransition('idle');
      }, 250);
    }, [checkMechanicIntroductions]);

    // Helper: Generate a brand-new board for a level and persist as the single active board
    const generateAndPersistBoard = useCallback((levelNum, baseState) => {
      const generated = Generator.generateLevel(levelNum);
      const newBoard = {
        level: levelNum,
        seed: generated.seed,
        width: generated.width,
        height: generated.height,
        sourceIdx: generated.sourceIdx,
        sources: generated.sources,
        targetIndices: generated.targetIndices,
        bonusTargetIndices: generated.bonusTargetIndices || [],
        groups: generated.groups || [],
        activeMechanics: generated.activeMechanics || [],
        isMilestone: generated.isMilestone,
        minMoves: generated.minMoves,
        tiles: generated.tiles,
        moves: 0,
        elapsedSeconds: 0,
        hintHighlights: []
      };

      const updatedState = {
        ...(baseState || progressionRef.current || Persistence.createDefaultState()),
        currentLevel: levelNum,
        currentBoard: newBoard
      };

      setProgression(updatedState);
      Persistence.saveWorkingState(updatedState);
      restoreBoard(newBoard, updatedState);
    }, [restoreBoard]);

    // Load or recover state on initial mount
    useEffect(() => {
      async function init() {
        const state = await Persistence.loadAndRecoverState();
        setProgression(state);
        setHintBalance(state.hintBalance || 0);
        setHintProgressSeconds(state.hintProgressSeconds || 0);

        if (state.currentBoard && Persistence.validateBoard(state.currentBoard, state.currentLevel)) {
          restoreBoard(state.currentBoard, state);
        } else {
          generateAndPersistBoard(state.currentLevel, state);
        }

        try {
          window.dispatchEvent(new CustomEvent('billsoft:game-progression', {
            detail: { gameType: 'modern', level: state.currentLevel || 1, totalStars: state.totalStars || 0, completedCount: state.completedCount || 0 }
          }));
        } catch (e) {}
      }
      init();

      // Flush on visibility change / window beforeunload
      const handleFlushState = () => {
        if (progressionRef.current && currentPuzzleRef.current && !isSolvedRef.current) {
          const updatedBoard = {
            ...currentPuzzleRef.current,
            moves: movesRef.current,
            elapsedSeconds: elapsedSecondsRef.current,
            hintHighlights: hintHighlightsRef.current
          };
          const updatedState = {
            ...progressionRef.current,
            hintBalance: hintBalanceRef.current,
            hintProgressSeconds: hintProgressSecondsRef.current,
            currentBoard: updatedBoard
          };
          Persistence.saveWorkingState(updatedState);
        }
        Persistence.flushPending();
      };

      const handleVisibilityChange = () => {
        if (document.visibilityState === 'hidden') {
          handleFlushState();
        }
      };

      document.addEventListener('visibilitychange', handleVisibilityChange);
      window.addEventListener('beforeunload', handleFlushState);

      return () => {
        document.removeEventListener('visibilitychange', handleVisibilityChange);
        window.removeEventListener('beforeunload', handleFlushState);
        if (timerRef.current) clearInterval(timerRef.current);
        if (advanceTimeoutRef.current) clearTimeout(advanceTimeoutRef.current);
        handleFlushState();
      };
    }, [restoreBoard, generateAndPersistBoard]);

    // Active gameplay & hint accumulation timer
    useEffect(() => {
      if (isSolved || !currentPuzzle) {
        if (timerRef.current) clearInterval(timerRef.current);
        return;
      }

      timerRef.current = setInterval(() => {
        if (document.visibilityState === 'visible' && !activeIntroModal && !showInfoModal && !showSkipModal) {
          setElapsedSeconds(s => s + 1);

          setHintProgressSeconds(prev => {
            const next = prev + 1;
            if (next >= HINT_INTERVAL_SECONDS) {
              setHintBalance(b => b + 1);
              Audio.playHint();
              return 0;
            }
            return next;
          });

          if (progressionRef.current) {
            progressionRef.current.totalPlayTimeSeconds = (progressionRef.current.totalPlayTimeSeconds || 0) + 1;
          }
        }
      }, 1000);

      return () => {
        if (timerRef.current) clearInterval(timerRef.current);
      };
    }, [isSolved, currentPuzzle, activeIntroModal, showInfoModal, showSkipModal]);

    // 50-Level milestone auto-dismiss timer
    useEffect(() => {
      if (levelMilestoneCelebration) {
        if (levelMilestoneTimerRef.current) clearTimeout(levelMilestoneTimerRef.current);
        levelMilestoneTimerRef.current = setTimeout(() => {
          setLevelMilestoneCelebration(null);
        }, 1800);
        return () => {
          if (levelMilestoneTimerRef.current) clearTimeout(levelMilestoneTimerRef.current);
        };
      }
    }, [levelMilestoneCelebration]);

    // Dismiss one-time mechanic intro modal
    const handleDismissIntroModal = useCallback(() => {
      if (!activeIntroModal || !progression) {
        setActiveIntroModal(null);
        return;
      }

      const dismissedKey = activeIntroModal.key;
      setActiveIntroModal(null);

      const existingIntroduced = new Set(progression.introducedMechanics || []);
      existingIntroduced.add(dismissedKey);

      const updated = {
        ...progression,
        introducedMechanics: Array.from(existingIntroduced)
      };
      setProgression(updated);
      Persistence.saveProgressionImmediate(updated);

      // Check if there's another unintroduced mechanic on the current board
      if (currentPuzzle) {
        checkMechanicIntroductions(currentPuzzle, updated);
      }
    }, [activeIntroModal, progression, currentPuzzle, checkMechanicIntroductions]);

    // Tile Click Handler: Supports Normal rotation, Switch toggle, 2x2 Group rotation, and Lock constraints
    const handleTileClick = useCallback((tileIndex) => {
      if (isSolved || !currentPuzzle || activeIntroModal) return;
      const tile = currentPuzzle.tiles[tileIndex];
      if (!tile || tile.isBlock) return;

      // Handle Locked Tile: If tile is locked and condition not met, rattle lock and block interaction
      if (tile.isLocked && !unlockedSet.has(tileIndex)) {
        setLockedRattleIdx(tileIndex);
        setTimeout(() => setLockedRattleIdx(null), 400);
        return;
      }

      let newTiles;
      let soundToPlay = 'rotate';

      // 1. SWITCH / TOGGLE TILE
      if (tile.isSwitch) {
        const nextState = tile.switchState === 'A' ? 'B' : 'A';
        const nextPorts = nextState === 'A' ? tile.switchPortsA : tile.switchPortsB;
        newTiles = currentPuzzle.tiles.map((t, idx) => {
          if (idx === tileIndex) {
            return { ...t, switchState: nextState, ports: nextPorts };
          }
          return t;
        });
        soundToPlay = 'switch';
      }
      // 2. ROTATING 2x2 GROUP
      else if (tile.groupId) {
        const matchingGroup = currentPuzzle.groups.find(g => g.groupId === tile.groupId);
        const groupIndices = new Set(matchingGroup ? matchingGroup.tileIndices : [tileIndex]);

        newTiles = currentPuzzle.tiles.map((t, idx) => {
          if (groupIndices.has(idx) && !t.isBlock) {
            const nextPorts = Engine.rotateClockwise(t.ports);
            const nextRotation = (t.rotation + 1) % 4;
            return { ...t, ports: nextPorts, rotation: nextRotation };
          }
          return t;
        });
        soundToPlay = 'rotate';
      }
      // 3. NORMAL TILE
      else {
        newTiles = currentPuzzle.tiles.map((t, idx) => {
          if (idx === tileIndex) {
            const nextPorts = Engine.rotateClockwise(t.ports);
            const nextRotation = (t.rotation + 1) % 4;
            return { ...t, ports: nextPorts, rotation: nextRotation };
          }
          return t;
        });
        soundToPlay = 'rotate';
      }

      if (soundToPlay === 'switch') {
        Audio.playSwitch();
      } else {
        Audio.playRotate();
      }

      const nextMoves = moves + 1;
      setMoves(nextMoves);

      // Hint cleanup if tile was solved
      let nextHints = hintHighlights;
      if (hintHighlights.includes(tileIndex)) {
        const rotatedTile = newTiles[tileIndex];
        if (rotatedTile.ports === rotatedTile.solvedPorts) {
          nextHints = hintHighlights.filter(idx => idx !== tileIndex);
          setHintHighlights(nextHints);
        }
      }

      const nextPuzzle = {
        ...currentPuzzle,
        tiles: newTiles,
        moves: nextMoves,
        elapsedSeconds,
        hintHighlights: nextHints
      };
      setCurrentPuzzle(nextPuzzle);

      // Evaluate new flow across all mechanics
      const evalResult = Engine.evaluateCircuit(
        nextPuzzle.tiles,
        nextPuzzle.width,
        nextPuzzle.height,
        nextPuzzle.sources || nextPuzzle.sourceIdx,
        nextPuzzle.targetIndices,
        nextPuzzle.bonusTargetIndices || []
      );

      // Check if any new locks were unlocked by this move
      evalResult.unlockedSet.forEach(unlockedIdx => {
        if (!unlockedSet.has(unlockedIdx)) {
          Audio.playUnlock();
        }
      });

      // Check if bridge tile was energized
      if (tile.isBridge && evalResult.energizedSet.has(tileIndex) && !energizedSet.has(tileIndex)) {
        Audio.playBridge();
      }

      setEnergizedSet(evalResult.energizedSet);
      // Check if player executed the hinted move
      if (activeHintData && (activeHintData.primaryIdx === tileIndex || activeHintData.chainIndices.includes(tileIndex))) {
        const clickedT = updatedTiles[tileIndex];
        const sPorts = clickedT.solvedPorts != null ? clickedT.solvedPorts : clickedT.ports;
        if (clickedT.ports === sPorts) {
          setActiveHintData(null);
          setHintHighlights([]);
        }
      }

      setUnlockedSet(evalResult.unlockedSet);
      setEnergizedDetails(evalResult.energizedDetails || new Map());
      setDepthMap(evalResult.depthMap || new Map());

      if (evalResult.energizedSet.has(tileIndex) && soundToPlay === 'rotate') {
        Audio.playConnect();
      }

      if (evalResult.isSolved) {
        setActiveHintData(null);
        setHintHighlights([]);
        handleLevelComplete(nextMoves, nextPuzzle, evalResult.bonusSolved);
      } else {
        const updatedProg = {
          ...progressionRef.current,
          hintBalance: hintBalanceRef.current,
          hintProgressSeconds: hintProgressSecondsRef.current,
          currentBoard: nextPuzzle
        };
        setProgression(updatedProg);
        Persistence.saveWorkingState(updatedProg);
      }
    }, [isSolved, currentPuzzle, activeIntroModal, activeHintData, unlockedSet, energizedSet, moves, elapsedSeconds, hintHighlights]);

    // Hint trigger handler (Intelligent 2-Tier Hint)
    const handleUseHint = useCallback(() => {
      if (isSolved || !currentPuzzle || hintBalance < 1 || activeIntroModal) return;

      const newBalance = hintBalance - 1;
      setHintBalance(newBalance);

      const tier = (activeHintData && activeHintData.primaryIdx != null) ? 2 : 1;
      const hintResult = Engine.findIntelligentHint(
        currentPuzzle.tiles,
        currentPuzzle.width,
        currentPuzzle.height,
        currentPuzzle.sources || currentPuzzle.sourceIdx,
        currentPuzzle.targetIndices,
        currentPuzzle.bonusTargetIndices || [],
        tier
      );

      const highlightArray = hintResult ? (hintResult.chainIndices || [hintResult.primaryIdx]) : [];
      setActiveHintData(hintResult);
      setHintHighlights(highlightArray);
      Audio.playHint();

      const updatedBoard = {
        ...currentPuzzle,
        moves,
        elapsedSeconds,
        hintHighlights: highlightArray
      };
      const updatedProg = {
        ...progressionRef.current,
        hintBalance: newBalance,
        hintProgressSeconds,
        currentBoard: updatedBoard
      };
      setProgression(updatedProg);
      Persistence.saveWorkingState(updatedProg);
    }, [isSolved, currentPuzzle, hintBalance, activeIntroModal, activeHintData, moves, elapsedSeconds, hintProgressSeconds]);

    // Level Completion Handler
    const handleLevelComplete = useCallback((totalMoves, puzzle, bonusSolved) => {
      setIsSolved(true);
      if (bonusSolved) {
        Audio.playBonus();
      } else {
        Audio.playVictory();
      }

      const starsEarned = Engine.calculateStars(totalMoves, puzzle.minMoves, bonusSolved);
      setLevelWonStars(starsEarned);

      setProgression(prev => {
        const currentProg = prev || Persistence.createDefaultState();
        const nextStars = currentProg.totalStars + starsEarned;
        const nextCompleted = currentProg.completedCount + 1;
        const nextStreak = currentProg.currentStreak + 1;
        const bestStreak = Math.max(currentProg.bestStreak || 0, nextStreak);
        const nextLevel = currentProg.currentLevel + 1;

        const currentMilestoneNumber = Math.floor(nextStars / 50);
        const rawPrevMilestone = currentProg.highestStarMilestone || 0;
        const prevMilestoneNumber = rawPrevMilestone >= 50 ? Math.floor(rawPrevMilestone / 50) : rawPrevMilestone;

        let newMilestoneData = null;
        if (currentMilestoneNumber > prevMilestoneNumber && currentMilestoneNumber >= 1) {
          newMilestoneData = {
            milestoneNumber: currentMilestoneNumber,
            starThreshold: currentMilestoneNumber * 50,
            trophy: getTrophyForMilestone(currentMilestoneNumber)
          };
          Audio.playMilestone();
          try {
            window.dispatchEvent(new CustomEvent('billsoft:trophy-unlocked', { detail: newMilestoneData }));
          } catch (e) {}
        }

        if (nextCompleted > 0 && nextCompleted % 50 === 0) {
          setLevelMilestoneCelebration(nextCompleted);
          try {
            window.dispatchEvent(new CustomEvent('billsoft:level-milestone-reached', { detail: { levelCount: nextCompleted } }));
          } catch (e) {}
        }

        // Check for 15-level Location Unlock Transition (e.g. Level 15 completed -> Location 2 unlocked)
        if (WorldEngine && typeof WorldEngine.checkAndTriggerLocationUnlock === 'function') {
          WorldEngine.checkAndTriggerLocationUnlock(currentProg.currentLevel, 'modern');
        }

        try {
          window.dispatchEvent(new CustomEvent('billsoft:game-progression', {
            detail: { gameType: 'modern', level: nextLevel, totalStars: nextStars, completedCount: nextCompleted }
          }));
        } catch (e) {}

        const nextGen = Generator.generateLevel(nextLevel);
        const nextBoard = {
          level: nextLevel,
          seed: nextGen.seed,
          width: nextGen.width,
          height: nextGen.height,
          sourceIdx: nextGen.sourceIdx,
          sources: nextGen.sources,
          targetIndices: nextGen.targetIndices,
          bonusTargetIndices: nextGen.bonusTargetIndices || [],
          groups: nextGen.groups || [],
          activeMechanics: nextGen.activeMechanics || [],
          isMilestone: nextGen.isMilestone,
          minMoves: nextGen.minMoves,
          tiles: nextGen.tiles,
          moves: 0,
          elapsedSeconds: 0,
          hintHighlights: []
        };

        const updated = {
          ...currentProg,
          currentLevel: nextLevel,
          completedCount: nextCompleted,
          totalStars: nextStars,
          highestStarMilestone: Math.max(prevMilestoneNumber, currentMilestoneNumber),
          totalPlayTimeSeconds: (currentProg.totalPlayTimeSeconds || 0) + elapsedSeconds,
          totalMoves: (currentProg.totalMoves || 0) + totalMoves,
          bestStreak,
          currentStreak: nextStreak,
          hintBalance: hintBalanceRef.current,
          hintProgressSeconds: hintProgressSecondsRef.current,
          currentBoard: nextBoard
        };

        Persistence.saveProgressionImmediate(updated);
        return updated;
      });

      if (advanceTimeoutRef.current) clearTimeout(advanceTimeoutRef.current);
      advanceTimeoutRef.current = setTimeout(() => {
        if (!milestoneCelebration) {
          setBoardTransition('exiting');
          setTimeout(() => {
            setProgression(curr => {
              if (curr && curr.currentBoard) {
                restoreBoard(curr.currentBoard, curr);
              }
              return curr;
            });
          }, 180);
        }
      }, 1600);
    }, [elapsedSeconds, milestoneCelebration, restoreBoard]);

    // Skip Handler
    const handleConfirmSkip = useCallback(() => {
      setShowSkipModal(false);
      if (!progression) return;

      setBoardTransition('exiting');
      setTimeout(() => {
        setProgression(prev => {
          const nextLevel = prev.currentLevel + 1;
          const nextGen = Generator.generateLevel(nextLevel);
          const nextBoard = {
            level: nextLevel,
            seed: nextGen.seed,
            width: nextGen.width,
            height: nextGen.height,
            sourceIdx: nextGen.sourceIdx,
            sources: nextGen.sources,
            targetIndices: nextGen.targetIndices,
            bonusTargetIndices: nextGen.bonusTargetIndices || [],
            groups: nextGen.groups || [],
            activeMechanics: nextGen.activeMechanics || [],
            isMilestone: nextGen.isMilestone,
            minMoves: nextGen.minMoves,
            tiles: nextGen.tiles,
            moves: 0,
            elapsedSeconds: 0,
            hintHighlights: []
          };

          const updated = {
            ...prev,
            currentLevel: nextLevel,
            skippedCount: prev.skippedCount + 1,
            currentStreak: 0,
            totalPlayTimeSeconds: (prev.totalPlayTimeSeconds || 0) + elapsedSeconds,
            totalMoves: (prev.totalMoves || 0) + moves,
            hintBalance: hintBalanceRef.current,
            hintProgressSeconds: hintProgressSecondsRef.current,
            currentBoard: nextBoard
          };

          Persistence.saveProgressionImmediate(updated);
          restoreBoard(nextBoard, updated);
          return updated;
        });
      }, 150);
    }, [progression, elapsedSeconds, moves, restoreBoard]);

    // Reset Level Handler
    const handleResetLevel = useCallback(() => {
      if (!progression || !currentPuzzle || isSolved) return;

      const currLevel = progression.currentLevel;
      const regenerated = Generator.generateLevel(currLevel);
      const resetBoard = {
        level: currLevel,
        seed: regenerated.seed,
        width: regenerated.width,
        height: regenerated.height,
        sourceIdx: regenerated.sourceIdx,
        sources: regenerated.sources,
        targetIndices: regenerated.targetIndices,
        bonusTargetIndices: regenerated.bonusTargetIndices || [],
        groups: regenerated.groups || [],
        activeMechanics: regenerated.activeMechanics || [],
        isMilestone: regenerated.isMilestone,
        minMoves: regenerated.minMoves,
        tiles: regenerated.tiles,
        moves: 0,
        elapsedSeconds: 0,
        hintHighlights: []
      };

      setMoves(0);
      setElapsedSeconds(0);
      setHintHighlights([]);
      setIsSolved(false);
      Audio.playRotate();

      const updated = {
        ...progression,
        currentBoard: resetBoard
      };
      setProgression(updated);
      Persistence.saveWorkingState(updated);
      restoreBoard(resetBoard, updated);
    }, [progression, currentPuzzle, isSolved, restoreBoard]);

    const handleDismissMilestone = () => {
      setMilestoneCelebration(null);
      if (progression && progression.currentBoard) {
        setBoardTransition('exiting');
        setTimeout(() => {
          restoreBoard(progression.currentBoard, progression);
        }, 150);
      }
    };

    // Keyboard navigation and shortcuts for modals
    useEffect(() => {
      const handleKeyDown = (e) => {
        if (activeIntroModal) {
          if (e.key === 'Enter' || e.key === 'Escape' || e.key === ' ') {
            e.preventDefault();
            handleDismissIntroModal();
          }
        } else if (showSkipModal) {
          if (e.key === 'Escape') {
            e.preventDefault();
            setShowSkipModal(false);
          } else if (e.key === 'Enter') {
            e.preventDefault();
            handleConfirmSkip();
          }
        } else if (showInfoModal) {
          if (e.key === 'Escape') {
            e.preventDefault();
            setShowInfoModal(false);
          }
        } else if (milestoneCelebration) {
          if (e.key === 'Escape' || e.key === 'Enter') {
            e.preventDefault();
            handleDismissMilestone();
          }
        } else if (levelMilestoneCelebration) {
          if (e.key === 'Escape' || e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            setLevelMilestoneCelebration(null);
          }
        }
      };
      window.addEventListener('keydown', handleKeyDown);
      return () => window.removeEventListener('keydown', handleKeyDown);
    }, [activeIntroModal, showSkipModal, showInfoModal, milestoneCelebration, levelMilestoneCelebration, handleDismissIntroModal, handleConfirmSkip]);

    // Responsive ResizeObserver measuring actual container dimensions
    useEffect(() => {
      const container = containerRef.current;
      if (!container || typeof ResizeObserver === 'undefined') return;

      const observer = new ResizeObserver((entries) => {
        if (!entries || entries.length === 0) return;
        const entry = entries[0];
        const rect = entry.contentRect;
        const width = Math.floor(rect.width);
        const height = Math.floor(rect.height);
        if (width > 0 && height > 0) {
          setContainerDimensions({ width, height });
        }
      });

      observer.observe(container);
      return () => observer.disconnect();
    }, []);

    if (!progression || !currentPuzzle) {
      return React.createElement('div', {
        style: { display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '60vh', color: 'var(--text-secondary)' }
      }, '⚡ Powering Circuit Connect...');
    }

    const stars = Math.max(0, Math.floor(progression.totalStars || 0));
    const currentMilestoneCount = Math.floor(stars / 50);
    const progressInMilestone = stars % 50;
    const currentTrophy = getTrophyForMilestone(currentMilestoneCount);
    const nextMilestoneStars = getNextMilestoneStars(progression.totalStars);
    const secondsToNextHint = Math.max(0, HINT_INTERVAL_SECONDS - (hintProgressSeconds % HINT_INTERVAL_SECONDS));
    const WorldEngine = typeof window !== 'undefined' ? window.MaharashtraWorld : null;
    const worldData = WorldEngine ? WorldEngine.getWorldForLevel('modern', progression.currentLevel) : null;

    // Responsive Tile Sizing calculation (maximizes board area within zero-scroll viewport)
    const cols = currentPuzzle ? currentPuzzle.width : 5;
    const rows = currentPuzzle ? currentPuzzle.height : 5;
    const tileGap = 4;
    const availMaxH = Math.max(180, containerDimensions.height - 110);
    const availMaxW = Math.max(220, containerDimensions.width - 24);
    const tileSize = Math.min(62, Math.max(28, Math.floor(Math.min(
      (availMaxW - (tileGap * (cols - 1)) - 20) / cols,
      (availMaxH - (tileGap * (rows - 1)) - 20) / rows
    ))));

    return React.createElement('div', {
      ref: containerRef,
      className: 'circuit-connect-container',
      style: {
        width: '100%',
        maxWidth: 860,
        height: '100%',
        margin: '0 auto',
        padding: '0 8px',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        flex: 1,
        minHeight: 0,
        boxSizing: 'border-box',
        overflow: 'hidden'
      }
    },
      // Centered Compact Game Header Bar
      React.createElement('div', {
        className: 'circuit-game-header',
        style: {
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 12,
          marginBottom: 6,
          fontSize: '0.84rem',
          fontWeight: 800,
          color: 'var(--text, #1e293b)'
        }
      },
        React.createElement('span', { style: { color: 'var(--primary, #4f46e5)' } },
          `⚡ LEVEL ${progression.currentLevel}`
        ),
        React.createElement('span', { style: { color: 'var(--border, #cbd5e1)' } }, '·'),
        React.createElement('span', { style: { color: 'var(--text-secondary, #64748b)', fontSize: '0.78rem', fontWeight: 700 } },
          `${cols}×${rows} GRID · MIN ${currentPuzzle ? currentPuzzle.minMoves : 4} MOVES`
        ),
        currentPuzzle.isMilestone && React.createElement('span', {
          style: {
            fontSize: '0.68rem',
            fontWeight: 800,
            padding: '2px 6px',
            borderRadius: 4,
            background: 'var(--warning-light, #fef3c7)',
            color: 'var(--warning, #d97706)',
            border: '1px solid var(--border, #e2e8f0)'
          }
        }, '★ MILESTONE')
      ),

      // Puzzle Grid Viewport (Centered, Prominent Native Surface)
      React.createElement('div', {
        className: `circuit-board-wrapper${isSolved ? ' overcharge' : ''}`,
        style: {
          position: 'relative',
          padding: 8,
          background: 'var(--bg-card, #ffffff)',
          border: currentPuzzle.isMilestone ? '2px solid #f59e0b' : '1px solid var(--border, #e2e8f0)',
          borderRadius: 'var(--radius-lg, 12px)',
          boxShadow: 'var(--shadow-md, 0 4px 6px -1px rgba(0, 0, 0, 0.07))',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          opacity: boardTransition === 'exiting' ? 0 : 1,
          transform: boardTransition === 'exiting' ? 'scale(0.96) translateY(-8px)' : (boardTransition === 'entering' ? 'scale(0.96) translateY(8px)' : 'scale(1) translateY(0)'),
          transition: 'opacity 0.2s ease, transform 0.2s cubic-bezier(0.34, 1.56, 0.64, 1), box-shadow 0.3s ease, border-color 0.3s ease',
          boxSizing: 'border-box'
        }
      },
        // Intelligent Hint Contextual Banner
        activeHintData && React.createElement('div', {
          className: 'circuit-hint-banner',
          style: {
            background: 'rgba(245, 158, 11, 0.12)',
            border: '1px solid rgba(245, 158, 11, 0.4)',
            borderRadius: '8px',
            padding: '4px 12px',
            marginBottom: '6px',
            fontSize: '0.82rem',
            fontWeight: 600,
            color: '#d97706',
            display: 'flex',
            alignItems: 'center',
            gap: '6px'
          }
        }, activeHintData.message),

        // Grid Table
        React.createElement('div', {
          style: {
            display: 'grid',
            gridTemplateColumns: `repeat(${currentPuzzle.width}, ${tileSize}px)`,
            gridTemplateRows: `repeat(${currentPuzzle.height}, ${tileSize}px)`,
            gap: tileGap
          }
        },
          currentPuzzle.tiles.map((tile, idx) => {
            const isEnergized = energizedSet.has(idx);
            const isUnlocked = unlockedSet.has(idx);
            const isTarget = tile.isLoad;
            const isSource = tile.isSource;
            const isBonus = tile.isBonus;
            const isBridge = tile.isBridge;
            const isLocked = tile.isLocked;
            const isSwitch = tile.isSwitch;
            const isWildcard = tile.isWildcard;
            const isGrouped = !!tile.groupId;
            const isHinted = hintHighlights.includes(idx);
            const isRattling = lockedRattleIdx === idx;
            const depth = depthMap.get(idx) != null ? depthMap.get(idx) : 0;
            const cascadeDelayMs = isEnergized ? Math.min(depth * 45, 450) : 0;

            const channelId = tile.channel || 'DEFAULT';
            const channel = (Engine && Engine.CHANNELS && Engine.CHANNELS[channelId]) || { color: '#4f46e5', glow: 'rgba(79,70,229,0.3)', symbol: '⚡' };

            let bgColor = 'var(--surface-sunken, #f8fafc)';
            let borderColor = isGrouped ? 'rgba(168, 85, 247, 0.5)' : 'var(--border, #e2e8f0)';

            if (isEnergized) {
              bgColor = currentPuzzle.isMilestone ? 'var(--warning-light, #fef3c7)' : 'var(--primary-light, #eef2ff)';
              borderColor = currentPuzzle.isMilestone ? '#d97706' : (channel.color || 'var(--primary, #4f46e5)');
            }
            if (tile.isBlock) {
              bgColor = 'var(--border, #cbd5e1)';
              borderColor = 'transparent';
            }

            return React.createElement('div', {
              key: idx,
              className: `circuit-tile${isHinted ? ' hint-highlight' : ''}${isRattling ? ' locked-rattle' : ''}`,
              onClick: () => handleTileClick(idx),
              title: isLocked && !isUnlocked ? 'Locked: Power prerequisite circuit to unlock' : (isSwitch ? 'Switch: Click to toggle circuit' : (isGrouped ? 'Group Tile: Rotates cluster' : '')),
              style: {
                width: tileSize,
                height: tileSize,
                background: bgColor,
                border: `1.5px solid ${borderColor}`,
                borderRadius: 'var(--radius, 8px)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                cursor: (tile.isBlock || isSolved) ? 'default' : (isLocked && !isUnlocked ? 'not-allowed' : 'pointer'),
                userSelect: 'none',
                transition: 'transform 0.18s cubic-bezier(0.34, 1.56, 0.64, 1), background 0.25s ease, border-color 0.25s ease, box-shadow 0.25s ease',
                transitionDelay: `${cascadeDelayMs}ms`,
                position: 'relative',
                boxShadow: isEnergized ? (currentPuzzle.isMilestone ? '0 0 10px rgba(245,158,11,0.35)' : '0 0 10px rgba(79,70,229,0.25)') : (isGrouped ? '0 0 6px rgba(168, 85, 247, 0.2)' : 'none')
              }
            },
              // Render Tile SVG Wire Path
              renderTileSvg(tile, isEnergized, currentPuzzle.isMilestone, cascadeDelayMs, energizedDetails.get(idx)),

              // Source Icon & Channel Symbol
              isSource && React.createElement('span', {
                style: { position: 'absolute', fontSize: '1.05rem', filter: `drop-shadow(0 0 4px ${channel.color})`, pointerEvents: 'none' }
              }, channel.symbol || '⚡'),

              // Target Bulb Icon (Required)
              isTarget && !isBonus && React.createElement('span', {
                className: isEnergized ? 'circuit-bulb-icon energized' : 'circuit-bulb-icon',
                style: {
                  position: 'absolute',
                  fontSize: '1.1rem',
                  filter: isEnergized ? (currentPuzzle.isMilestone ? 'drop-shadow(0 0 10px #f59e0b)' : `drop-shadow(0 0 10px ${channel.color})`) : 'none',
                  opacity: isEnergized ? 1 : 0.45,
                  pointerEvents: 'none',
                  transition: 'filter 0.3s ease, opacity 0.3s ease, transform 0.3s ease',
                  transitionDelay: `${cascadeDelayMs}ms`
                }
              }, channel.symbol && channel.symbol !== '⚡' ? channel.symbol : '💡'),

              // Bonus Optional Target Crystal
              isBonus && React.createElement('span', {
                className: isEnergized ? 'circuit-bonus-icon energized' : 'circuit-bonus-icon',
                style: {
                  position: 'absolute',
                  fontSize: '1.1rem',
                  filter: isEnergized ? 'drop-shadow(0 0 12px #e040fb)' : 'none',
                  opacity: isEnergized ? 1 : 0.45,
                  pointerEvents: 'none',
                  transition: 'filter 0.3s ease, opacity 0.3s ease, transform 0.3s ease'
                }
              }, '💎'),

              // Locked Tile Badge (Padlock)
              isLocked && React.createElement('span', {
                style: {
                  position: 'absolute',
                  top: 2,
                  right: 2,
                  fontSize: '0.75rem',
                  background: isUnlocked ? 'rgba(34, 197, 94, 0.85)' : 'rgba(239, 68, 68, 0.85)',
                  padding: '1px 3px',
                  borderRadius: 4,
                  lineHeight: 1,
                  pointerEvents: 'none',
                  transition: 'background 0.3s ease'
                }
              }, isUnlocked ? '🔓' : '🔒'),

              // Switch / Toggle Indicator
              isSwitch && React.createElement('span', {
                style: {
                  position: 'absolute',
                  bottom: 2,
                  right: 2,
                  fontSize: '0.7rem',
                  background: 'var(--primary, #4f46e5)',
                  padding: '1px 3px',
                  borderRadius: 4,
                  color: '#ffffff',
                  fontWeight: 700,
                  lineHeight: 1,
                  pointerEvents: 'none'
                }
              }, tile.switchState || 'A'),

              // Wildcard Center Emblem
              isWildcard && React.createElement('span', {
                style: {
                  position: 'absolute',
                  fontSize: '1rem',
                  filter: 'drop-shadow(0 0 6px #00e5ff)',
                  pointerEvents: 'none'
                }
              }, '✨'),

              // Blueprint Hint Badge Indicator
              isHinted && React.createElement('span', {
                className: 'circuit-hint-badge',
                title: 'Blueprint Hint Move',
                style: {
                  position: 'absolute',
                  bottom: 2,
                  right: 2,
                  fontSize: '0.75rem',
                  fontWeight: 800,
                  background: 'rgba(245, 158, 11, 0.95)',
                  color: '#ffffff',
                  borderRadius: '4px',
                  padding: '1px 4px',
                  lineHeight: 1,
                  pointerEvents: 'none',
                  boxShadow: '0 0 6px rgba(245, 158, 11, 0.6)'
                }
              }, activeHintData && activeHintData.primaryIdx === idx ? (activeHintData.glyph || '↻') : '💡')
            );
          })
        ),

        // Solved Victory Overlay Banner
        isSolved && React.createElement('div', {
          style: {
            position: 'absolute',
            inset: 0,
            background: 'rgba(15, 23, 42, 0.9)',
            backdropFilter: 'blur(5px)',
            borderRadius: 'var(--radius-lg, 12px)',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 8,
            color: '#ffffff',
            animation: 'circuitVictoryEnter 0.4s cubic-bezier(0.34, 1.56, 0.64, 1)'
          }
        },
          React.createElement('div', { style: { fontSize: '2.2rem' } }, '✨ ⚡ ✨'),
          React.createElement('h3', { style: { margin: 0, fontSize: '1.3rem', fontWeight: 800, color: '#38bdf8' } }, 'Circuit Connected!'),
          React.createElement('div', { style: { display: 'flex', gap: 6, fontSize: '1.6rem', letterSpacing: '4px', margin: '2px 0' } },
            [1, 2, 3].map(sIdx => {
              const earned = (levelWonStars || 1) >= sIdx;
              return React.createElement('span', {
                key: sIdx,
                className: earned ? 'circuit-star-item' : '',
                style: {
                  animationDelay: `${sIdx * 140}ms`,
                  color: earned ? '#fbbf24' : 'rgba(255,255,255,0.2)',
                  filter: earned ? 'drop-shadow(0 0 8px rgba(251, 191, 36, 0.7))' : 'none'
                }
              }, earned ? '★' : '☆');
            })
          ),
          React.createElement('div', { style: { fontSize: '0.82rem', color: 'rgba(255,255,255,0.75)' } },
            `Moves: ${moves} • Time: ${formatActiveTime(elapsedSeconds)}`
          )
        )
      ),

      // Unified Compact Control & Status Rail (Directly Under Game Board)
      React.createElement('div', {
        className: 'take-a-break-control-rail',
        style: {
          width: '100%',
          maxWidth: 520,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: 6,
          marginTop: 6
        }
      },
        // Row 1: Action Controls Buttons
        React.createElement('div', {
          style: {
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 8,
            width: '100%'
          }
        },
          // Hint Button with Live Active-Play Countdown
          React.createElement('button', {
            type: 'button',
            className: hintBalance > 0 ? 'btn btn-primary btn-sm' : 'btn btn-outline btn-sm',
            onClick: handleUseHint,
            disabled: hintBalance < 1 || isSolved,
            title: hintBalance > 0 ? `Use 1 of ${hintBalance} available hints (Next hint in ${formatActiveTime(secondsToNextHint)})` : `Next hint ready in ${formatActiveTime(secondsToNextHint)} of active play`,
            style: {
              padding: '4px 14px',
              fontSize: '0.82rem',
              fontWeight: 700,
              borderRadius: 'var(--radius-sm, 6px)',
              display: 'flex',
              alignItems: 'center',
              gap: 6
            }
          },
            React.createElement('span', null, `💡 Hint (${hintBalance})`),
            React.createElement('span', {
              style: {
                fontSize: '0.72rem',
                fontWeight: 600,
                opacity: 0.88
              }
            }, `• ${formatActiveTime(secondsToNextHint)}`)
          ),

          // Skip Button
          React.createElement('button', {
            type: 'button',
            className: 'btn btn-outline btn-sm',
            onClick: () => setShowSkipModal(true),
            title: 'Skip current level (0 stars awarded)',
            style: {
              padding: '4px 12px',
              fontSize: '0.82rem',
              fontWeight: 600,
              borderRadius: 'var(--radius-sm, 6px)',
              display: 'flex',
              alignItems: 'center',
              gap: 4
            }
          }, '⏭ Skip'),

          // Reset Level Button
          React.createElement('button', {
            type: 'button',
            className: 'btn btn-outline btn-sm',
            onClick: () => handleResetLevel(),
            title: 'Reset puzzle to original orientation',
            style: {
              padding: '4px 12px',
              fontSize: '0.82rem',
              fontWeight: 600,
              borderRadius: 'var(--radius-sm, 6px)',
              display: 'flex',
              alignItems: 'center',
              gap: 4
            }
          }, '↺ Reset'),

          // Stats Modal Button
          React.createElement('button', {
            type: 'button',
            className: 'btn btn-outline btn-sm',
            onClick: () => setShowInfoModal(true),
            title: 'View Lifetime Statistics & Milestone Trophies',
            style: {
              padding: '4px 12px',
              fontSize: '0.82rem',
              fontWeight: 600,
              borderRadius: 'var(--radius-sm, 6px)',
              display: 'flex',
              alignItems: 'center',
              gap: 4
            }
          }, '📊 Stats')
        ),

        // Row 2: Unified Supporting Status Bar
        React.createElement('div', {
          style: {
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 8,
            fontSize: '0.74rem',
            fontWeight: 600,
            color: 'var(--text-secondary, #64748b)',
            background: 'var(--surface-sunken, #f8fafc)',
            border: '1px solid var(--border, #e2e8f0)',
            borderRadius: 'var(--radius, 8px)',
            padding: '4px 14px',
            width: '100%',
            boxSizing: 'border-box',
            flexWrap: 'wrap'
          }
        },
          React.createElement('span', { style: { color: 'var(--text, #1e293b)', fontWeight: 700 } },
            `⭐ ${progression.totalStars} Stars`
          ),
          React.createElement('span', { style: { color: 'var(--border, #cbd5e1)' } }, '•'),
          React.createElement('span', { style: { color: currentTrophy ? currentTrophy.color : '#d97706', fontWeight: 700 } },
            `${currentTrophy ? `${currentTrophy.icon} ${currentTrophy.tier}` : '🥉 Bronze'} (${progressInMilestone}/50)`
          ),
          React.createElement('span', { style: { color: 'var(--border, #cbd5e1)' } }, '•'),
          React.createElement('span', {
            style: { color: 'var(--primary, #4f46e5)', fontWeight: 700 },
            title: '1 hint awarded every 30m of active play'
          }, `💡 Next Hint in ${formatActiveTime(secondsToNextHint)}`),
          worldData && worldData.location && React.createElement(React.Fragment, null,
            React.createElement('span', { style: { color: 'var(--border, #cbd5e1)' } }, '•'),
            React.createElement('span', { style: { color: 'var(--text-secondary, #64748b)' } },
              `📍 ${worldData.location.name} (${worldData.progressInLocation}/${worldData.levelsPerLocation})`
            )
          ),
          React.createElement('span', { style: { color: 'var(--border, #cbd5e1)' } }, '•'),
          React.createElement('span', null, `Moves: ${moves}`)
        )
      ),

      // One-Time Mechanic Introduction Card Modal
      activeIntroModal && renderModalPortal(React.createElement('div', {
        className: 'modal-overlay',
        style: {
          position: 'fixed',
          inset: 0,
          background: 'rgba(15, 23, 42, 0.75)',
          backdropFilter: 'blur(8px)',
          WebkitBackdropFilter: 'blur(8px)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 99999,
          padding: 16
        },
        onClick: handleDismissIntroModal
      },
        React.createElement('div', {
          className: 'modal-card',
          style: {
            background: 'var(--bg-card, #1e293b)',
            border: '2px solid #38bdf8',
            borderRadius: 16,
            padding: '26px 28px',
            maxWidth: 380,
            width: '90%',
            textAlign: 'center',
            boxShadow: '0 0 30px rgba(56, 189, 248, 0.3)',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: 12
          },
          onClick: e => e.stopPropagation()
        },
          React.createElement('div', { style: { fontSize: '2.6rem' } }, activeIntroModal.icon),
          React.createElement('div', null,
            React.createElement('h3', { style: { margin: 0, fontSize: '1.25rem', fontWeight: 800, color: '#38bdf8' } }, activeIntroModal.name.toUpperCase()),
            React.createElement('div', { style: { fontSize: '0.8rem', fontWeight: 600, color: '#fbbf24', marginTop: 2 } }, activeIntroModal.tagline)
          ),
          React.createElement('p', { style: { margin: '4px 0 10px', fontSize: '0.92rem', color: 'var(--text)', lineHeight: 1.45 } },
            activeIntroModal.desc
          ),
          React.createElement('button', {
            type: 'button',
            className: 'btn btn-primary',
            onClick: handleDismissIntroModal,
            style: { padding: '8px 24px', fontSize: '0.95rem', fontWeight: 700, borderRadius: 8 }
          }, 'Got it')
        )
      )),

      // Native In-App Skip Level Confirmation Modal
      showSkipModal && renderModalPortal(React.createElement('div', {
        className: 'modal-overlay',
        style: {
          position: 'fixed',
          inset: 0,
          background: 'rgba(15, 23, 42, 0.75)',
          backdropFilter: 'blur(8px)',
          WebkitBackdropFilter: 'blur(8px)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 99999,
          padding: 16
        },
        onClick: () => setShowSkipModal(false)
      },
        React.createElement('div', {
          className: 'modal-card',
          style: {
            background: 'var(--bg-card, #1e293b)',
            border: '1px solid var(--border)',
            borderRadius: 14,
            padding: '24px 26px',
            maxWidth: 400,
            width: '90%',
            display: 'flex',
            flexDirection: 'column',
            gap: 14,
            boxShadow: '0 10px 30px rgba(0,0,0,0.45)'
          },
          onClick: e => e.stopPropagation()
        },
          React.createElement('h3', { style: { margin: 0, fontSize: '1.2rem', fontWeight: 700, color: 'var(--text)' } }, 'Skip Level?'),
          React.createElement('p', { style: { margin: 0, fontSize: '0.92rem', color: 'var(--text-secondary)', lineHeight: 1.5 } },
            'Are you sure you want to skip this level? Skipped levels cannot be played again.'
          ),
          React.createElement('div', { style: { display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 6 } },
            React.createElement('button', {
              type: 'button',
              className: 'btn btn-ghost btn-sm',
              onClick: () => setShowSkipModal(false),
              style: { padding: '8px 16px', fontSize: '0.9rem' }
            }, 'Cancel'),
            React.createElement('button', {
              type: 'button',
              className: 'btn btn-danger btn-sm',
              onClick: handleConfirmSkip,
              style: { padding: '8px 18px', fontSize: '0.9rem', fontWeight: 600 }
            }, 'Skip Level')
          )
        )
      )),

      // 50-Level Celebration Modal
      levelMilestoneCelebration && renderModalPortal(React.createElement('div', {
        className: 'level-milestone-modal-overlay',
        style: {
          position: 'fixed',
          inset: 0,
          background: 'rgba(0,0,0,0.75)',
          backdropFilter: 'blur(8px)',
          WebkitBackdropFilter: 'blur(8px)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 99999,
          animation: 'milestoneFadeIn 0.3s cubic-bezier(0.16, 1, 0.3, 1)'
        },
        onClick: () => setLevelMilestoneCelebration(null)
      },
        React.createElement('div', {
          className: 'level-milestone-modal-card',
          style: {
            background: 'linear-gradient(135deg, rgba(30, 41, 59, 0.96), rgba(15, 23, 42, 0.98))',
            border: '2px solid rgba(255, 215, 0, 0.7)',
            borderRadius: 18,
            padding: '28px 32px',
            maxWidth: 440,
            width: '90%',
            textAlign: 'center',
            boxShadow: '0 0 50px rgba(255, 215, 0, 0.4)',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: 12,
            animation: 'milestonePop 0.4s cubic-bezier(0.16, 1, 0.3, 1)'
          },
          onClick: e => e.stopPropagation()
        },
          React.createElement('div', { style: { fontSize: '3.2rem', filter: 'drop-shadow(0 0 16px gold)' } }, '👑'),
          React.createElement('h2', { style: { margin: 0, fontSize: '1.45rem', fontWeight: 900, color: '#ffd700', letterSpacing: '0.04em' } },
            `✨ ${levelMilestoneCelebration} LEVELS COMPLETE! ✨`
          ),
          React.createElement('p', { style: { margin: 0, fontSize: '0.92rem', color: 'var(--text-secondary, #94a3b8)' } },
            'Superb mastery! Maharashtra world level milestone reached.'
          ),
          React.createElement('button', {
            type: 'button',
            className: 'btn btn-primary',
            onClick: () => setLevelMilestoneCelebration(null),
            style: { marginTop: 8, padding: '8px 24px', fontWeight: 700, borderRadius: 8 }
          }, 'Continue Playing ➔')
        )
      )),

      // Info & Statistics Modal
      showInfoModal && renderModalPortal(React.createElement('div', {
        className: 'modal-overlay take-a-break-stats-overlay',
        style: {
          position: 'fixed',
          inset: 0,
          background: 'rgba(15, 23, 42, 0.75)',
          backdropFilter: 'blur(8px)',
          WebkitBackdropFilter: 'blur(8px)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 99999,
          padding: 16
        },
        onClick: () => setShowInfoModal(false)
      },
        React.createElement('div', {
          className: 'modal-card take-a-break-stats-card',
          style: {
            width: 480,
            maxWidth: '92vw',
            maxHeight: '90vh',
            overflowY: 'auto',
            padding: '24px 28px',
            borderRadius: 16,
            background: 'var(--bg-card, #ffffff)',
            color: 'var(--text, #1e293b)',
            border: '1px solid var(--border, #e2e8f0)',
            boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.5), 0 0 0 1px rgba(255,255,255,0.05)',
            display: 'flex',
            flexDirection: 'column',
            gap: 16,
            boxSizing: 'border-box'
          },
          onClick: e => e.stopPropagation()
        },
          React.createElement('div', { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center' } },
            React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 8 } },
              React.createElement('span', { style: { fontSize: '1.4rem' } }, '⚡'),
              React.createElement('h3', { style: { margin: 0, fontSize: '1.2rem', fontWeight: 800, color: 'var(--text)' } }, 'Circuit Connect Statistics')
            ),
            React.createElement('button', {
              type: 'button',
              className: 'btn btn-ghost btn-sm',
              onClick: () => setShowInfoModal(false),
              style: { padding: '4px 8px', fontSize: '1.1rem', cursor: 'pointer', borderRadius: 6 }
            }, '✕')
          ),

          React.createElement('div', { style: { display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 10 } },
            React.createElement(StatBox, { label: 'Current Level', val: progression.currentLevel }),
            React.createElement(StatBox, { label: 'Levels Solved', val: progression.completedCount }),
            React.createElement(StatBox, { label: 'Total Stars', val: `⭐ ${progression.totalStars}`, color: '#eab308' }),
            React.createElement(StatBox, { label: 'Milestone Trophies', val: `🏆 ${currentMilestoneCount}`, color: '#f59e0b' }),
            React.createElement(StatBox, { label: 'Best Streak', val: `🔥 ${progression.bestStreak || 0} levels`, color: '#f97316' }),
            React.createElement(StatBox, { label: 'Total Moves', val: (progression.totalMoves || 0) + moves }),
            React.createElement(StatBox, { label: 'Levels Skipped', val: progression.skippedCount || 0 }),
            React.createElement(StatBox, { label: 'Total Time Played', val: formatLifetimeTime(progression.totalPlayTimeSeconds), span: 2 })
          ),

          // Shared Animated Trophy Progress Bar Component
          React.createElement(window.TrophyProgressBar || TrophyProgressBar, {
            totalStars: progression.totalStars,
            currentLevel: progression.currentLevel,
            gameType: 'modern'
          }),

          React.createElement('div', { style: { marginTop: 4, textAlign: 'right' } },
            React.createElement('button', {
              type: 'button',
              className: 'btn btn-primary',
              onClick: () => setShowInfoModal(false),
              style: { padding: '8px 24px', fontWeight: 700, borderRadius: 8 }
            }, 'Close')
          )
        )
      ))
    );
  }

  function StatBox({ label, val, color, span }) {
    return React.createElement('div', {
      style: {
        background: 'var(--surface-sunken, #f8fafc)',
        borderRadius: 10,
        padding: '10px 14px',
        border: '1px solid var(--border)',
        gridColumn: span ? `span ${span}` : undefined
      }
    },
      React.createElement('div', { style: { fontSize: '0.75rem', color: 'var(--text-secondary)', fontWeight: 600 } }, label),
      React.createElement('div', { style: { fontSize: '1.25rem', fontWeight: 800, color: color || 'var(--text)', marginTop: 2 } }, val)
    );
  }

  /**
   * Render SVG wire traces inside a grid tile with support for:
   * - Standard Straight, Corner, Tee, Cross, End wires
   * - Bridge Crossover (independent orthogonal layers with arch)
   * - Wildcards (4-way omnidirectional)
   * - Color-coded channel lines
   */
  function renderTileSvg(tile, isEnergized, isMilestone, cascadeDelayMs = 0, detail = null) {
    if (tile.isBlock) return null;

    const channelId = tile.channel || 'DEFAULT';
    const channelDef = (Engine && Engine.CHANNELS && Engine.CHANNELS[channelId]) || { color: '#4f46e5' };
    const baseColor = isMilestone ? '#f59e0b' : (channelDef.color || '#4f46e5');
    const defaultColor = isEnergized ? baseColor : 'var(--text-muted, #94a3b8)';
    const strokeWidth = 5;
    const center = 32;

    const wireStyle = {
      transition: 'stroke 0.3s ease, fill 0.3s ease',
      transitionDelay: `${cascadeDelayMs}ms`
    };

    const elements = [];

    // BRIDGE / CROSSOVER TILE: Render independent vertical and horizontal layers
    if (tile.isBridge) {
      const isVEnergized = isEnergized && (!detail || !detail.layers || detail.layers.v);
      const isHEnergized = isEnergized && (!detail || !detail.layers || detail.layers.h);

      const colorV = isVEnergized ? baseColor : 'var(--text-muted, #94a3b8)';
      const colorH = isHEnergized ? baseColor : 'var(--text-muted, #94a3b8)';

      // Layer 1: Horizontal line underneath
      elements.push(React.createElement('line', {
        key: 'br-h',
        x1: -3,
        y1: center,
        x2: 67,
        y2: center,
        stroke: colorH,
        strokeWidth,
        strokeLinecap: 'round',
        style: wireStyle
      }));

      // Insulation bridge pad in center
      elements.push(React.createElement('circle', {
        key: 'br-pad',
        cx: center,
        cy: center,
        r: 8,
        fill: 'var(--bg-card, #ffffff)',
        stroke: 'var(--border, #e2e8f0)',
        strokeWidth: 1.5
      }));

      // Layer 2: Vertical bridge jumping over horizontal
      elements.push(React.createElement('line', {
        key: 'br-v-top',
        x1: center,
        y1: -3,
        x2: center,
        y2: center - 6,
        stroke: colorV,
        strokeWidth,
        strokeLinecap: 'round',
        style: wireStyle
      }));
      elements.push(React.createElement('line', {
        key: 'br-v-bot',
        x1: center,
        y1: center + 6,
        x2: center,
        y2: 67,
        stroke: colorV,
        strokeWidth,
        strokeLinecap: 'round',
        style: wireStyle
      }));
      elements.push(React.createElement('path', {
        key: 'br-v-arch',
        d: `M ${center - 4} ${center - 6} Q ${center + 6} ${center} ${center - 4} ${center + 6}`,
        stroke: colorV,
        strokeWidth: 3,
        fill: 'none',
        style: wireStyle
      }));

      return React.createElement('svg', {
        viewBox: '0 0 64 64',
        style: { position: 'absolute', inset: 0, width: '100%', height: '100%', overflow: 'visible', pointerEvents: 'none' }
      }, elements);
    }

    // WILDCARD TILE: Render adaptive 4-way glowing trace with center prism
    if (tile.isWildcard) {
      const wildcardColor = isEnergized ? '#00e5ff' : 'rgba(0, 229, 255, 0.4)';
      return React.createElement('svg', {
        viewBox: '0 0 64 64',
        style: { position: 'absolute', inset: 0, width: '100%', height: '100%', overflow: 'visible', pointerEvents: 'none' }
      },
        React.createElement('line', { key: 'n', x1: center, y1: center, x2: center, y2: -3, stroke: wildcardColor, strokeWidth, strokeLinecap: 'round', strokeDasharray: '2,2' }),
        React.createElement('line', { key: 'e', x1: center, y1: center, x2: 67, y2: center, stroke: wildcardColor, strokeWidth, strokeLinecap: 'round', strokeDasharray: '2,2' }),
        React.createElement('line', { key: 's', x1: center, y1: center, x2: center, y2: 67, stroke: wildcardColor, strokeWidth, strokeLinecap: 'round', strokeDasharray: '2,2' }),
        React.createElement('line', { key: 'w', x1: center, y1: center, x2: -3, y2: center, stroke: wildcardColor, strokeWidth, strokeLinecap: 'round', strokeDasharray: '2,2' }),
        React.createElement('polygon', {
          points: `${center},${center - 8} ${center + 8},${center} ${center},${center + 8} ${center - 8},${center}`,
          fill: isEnergized ? '#00e5ff' : 'rgba(0, 229, 255, 0.3)',
          stroke: '#ffffff',
          strokeWidth: 1.5
        })
      );
    }

    // STANDARD TILE (Straight, Corner, Tee, Cross, End, Switch)
    const p = tile.ports || 0;

    if (p > 0) {
      elements.push(React.createElement('circle', {
        key: 'hub',
        cx: center,
        cy: center,
        r: strokeWidth / 2,
        fill: defaultColor,
        style: wireStyle
      }));
    }

    if (p & 1) { // North
      elements.push(React.createElement('line', { key: 'n', x1: center, y1: center, x2: center, y2: -3, stroke: defaultColor, strokeWidth, strokeLinecap: 'round', style: wireStyle }));
    }
    if (p & 2) { // East
      elements.push(React.createElement('line', { key: 'e', x1: center, y1: center, x2: 67, y2: center, stroke: defaultColor, strokeWidth, strokeLinecap: 'round', style: wireStyle }));
    }
    if (p & 4) { // South
      elements.push(React.createElement('line', { key: 's', x1: center, y1: center, x2: center, y2: 67, stroke: defaultColor, strokeWidth, strokeLinecap: 'round', style: wireStyle }));
    }
    if (p & 8) { // West
      elements.push(React.createElement('line', { key: 'w', x1: center, y1: center, x2: -3, y2: center, stroke: defaultColor, strokeWidth, strokeLinecap: 'round', style: wireStyle }));
    }

    return React.createElement('svg', {
      viewBox: '0 0 64 64',
      style: { position: 'absolute', inset: 0, width: '100%', height: '100%', overflow: 'visible', pointerEvents: 'none' }
    }, elements);
  }

  return CircuitConnect;
}));

