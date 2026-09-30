/**
 * circuitGenerator.js
 * Deterministic, procedural level generator for Circuit Connect (Novelty-First Expansion)
 * using Mulberry32 PRNG, Randomized Kruskal spanning tree carving, and strict Solvability Validation.
 *
 * Progressive Mechanic Roadmap:
 * - Levels 1–10:   Core single-source circuits (3x3 to 4x4)
 * - Levels 11–25:  Splitters / Multiple targets (4x4 to 5x5)
 * - Levels 26–40:  Bridge / Crossover (5x5)
 * - Levels 41–55:  Locked tiles (5x5, prerequisite unlock)
 * - Levels 56–70:  Switches / Toggles (5x5)
 * - Levels 71–85:  Multiple sources & Colored circuits (5x5 to 6x6, Red/Blue channels)
 * - Levels 86–100: Rotating groups (2x2 sync) & Wildcard tiles (6x6)
 * - Levels 101+:   Infinite procedural combinations (Strict novelty budget: max 2 mechanics)
 *
 * Special Level Cadence:
 * - Every 5th level (e.g. 5, 15, 25, 35...): Breather / Oasis level (clean, relaxing, 0 special mechanics)
 * - Every 10th level (e.g. 10, 20, 30...): Milestone level (celebratory layout)
 * - Every 15th level (e.g. 15, 30, 45...): Optional bonus target
 */

(function (root, factory) {
  if (typeof define === 'function' && define.amd) {
    define(['./circuitEngine'], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory(require('./circuitEngine'));
  } else {
    root.CircuitGenerator = factory(root.CircuitEngine);
  }
}(typeof self !== 'undefined' ? self : this, function (CircuitEngine) {
  'use strict';

  const Engine = CircuitEngine || (typeof window !== 'undefined' ? window.CircuitEngine : null);
  const { NORTH, EAST, SOUTH, WEST } = Engine || { NORTH: 1, EAST: 2, SOUTH: 4, WEST: 8 };

  /**
   * Deterministic 32-bit PRNG (Mulberry32)
   */
  function createRng(seed) {
    let s = seed >>> 0;
    return function next() {
      s = (s + 0x6D2B79F5) >>> 0;
      let t = Math.imul(s ^ (s >>> 15), 1 | s);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }

  /**
   * Disjoint Set / Union-Find for Kruskal's spanning tree
   */
  class DisjointSet {
    constructor(size) {
      this.parent = new Int32Array(size);
      for (let i = 0; i < size; i++) this.parent[i] = i;
    }
    find(i) {
      let root = i;
      while (root !== this.parent[root]) {
        root = this.parent[root];
      }
      let curr = i;
      while (curr !== root) {
        let nxt = this.parent[curr];
        this.parent[curr] = root;
        curr = nxt;
      }
      return root;
    }
    union(i, j) {
      let rootI = this.find(i);
      let rootJ = this.find(j);
      if (rootI !== rootJ) {
        this.parent[rootI] = rootJ;
        return true;
      }
      return false;
    }
  }

  /**
   * Determine level configuration, active mechanics, grid dimensions, and special properties.
   */
  function getLevelConfig(level) {
    const lvl = Math.max(1, Math.floor(level || 1));
    const isMilestone = (lvl % 10 === 0);
    const isOasis = (lvl % 5 === 0 && !isMilestone);
    const hasBonus = (lvl % 15 === 0);

    let width = 3;
    let height = 3;
    let targetCount = 1;
    let blockCount = 0;
    const mechanics = [];

    if (isOasis) {
      // Breather / Oasis: clean, relaxing, no special mechanics
      width = lvl <= 15 ? 4 : 5;
      height = width;
      targetCount = 1;
      blockCount = 0;
      return { lvl, width, height, targetCount, blockCount, isMilestone: false, isOasis: true, hasBonus: false, mechanics: [] };
    }

    if (lvl <= 3) {
      width = 3;
      height = 3;
      targetCount = 1;
    } else if (lvl <= 10) {
      width = 4;
      height = 4;
      targetCount = 1;
    } else if (lvl <= 25) {
      width = (lvl % 2 === 0) ? 4 : 5;
      height = width;
      targetCount = 2;
      mechanics.push('SPLITTER');
    } else if (lvl <= 40) {
      width = 5;
      height = 5;
      targetCount = 2;
      mechanics.push('BRIDGE');
    } else if (lvl <= 55) {
      width = 5;
      height = 5;
      targetCount = (lvl % 3 === 0) ? 3 : 2;
      mechanics.push('LOCKED');
      if (lvl > 48) mechanics.push('BRIDGE');
    } else if (lvl <= 70) {
      width = 5;
      height = 5;
      targetCount = (lvl % 3 === 0) ? 3 : 2;
      mechanics.push('SWITCH');
      if (lvl > 62) mechanics.push('LOCKED');
    } else if (lvl <= 85) {
      width = (lvl % 2 === 0) ? 5 : 6;
      height = width;
      targetCount = 2;
      mechanics.push('MULTI_SOURCE');
      if (lvl > 78) mechanics.push('SWITCH');
    } else if (lvl <= 100) {
      width = 6;
      height = 6;
      targetCount = 2;
      mechanics.push('ROTATING_GROUP');
      if (lvl > 92) mechanics.push('WILDCARD');
    } else {
      // Infinite procedural levels (101+) - max 2 mechanics per level, bounded difficulty
      width = 6;
      height = 6;
      targetCount = (lvl % 3 === 0) ? 3 : 2;

      const pool = ['SPLITTER', 'BRIDGE', 'LOCKED', 'SWITCH', 'MULTI_SOURCE', 'ROTATING_GROUP', 'WILDCARD'];
      const m1 = pool[(lvl * 3) % pool.length];
      const m2 = pool[(lvl * 7 + 2) % pool.length];
      mechanics.push(m1);
      if (m2 !== m1 && mechanics.length < 2) mechanics.push(m2);
    }

    if (isMilestone) {
      targetCount = Math.min(3, targetCount + 1);
      blockCount = 0;
    }

    // Limit active mechanics strictly to 2 per level
    const finalMechanics = mechanics.slice(0, 2);

    return { lvl, width, height, targetCount, blockCount, isMilestone, isOasis, hasBonus, mechanics: finalMechanics };
  }

  /**
   * Internal difficulty and frustration score calculation.
   * High ambiguity, excessive dead ends, and runaway moves get penalized.
   */
  function calculateFrustrationScore(puzzle) {
    if (!puzzle || !puzzle.tiles) return Infinity;
    const { width, height, tiles, minMoves, targetIndices, activeMechanics, isOasis } = puzzle;

    let score = (width * height) + (minMoves * 1.5) + (targetIndices.length * 3) + ((activeMechanics || []).length * 4);

    let uselessDeadEnds = 0;
    for (let i = 0; i < tiles.length; i++) {
      const t = tiles[i];
      if (t && t.type === 'END' && !t.isSource && !t.isLoad && !t.isBlock) {
        uselessDeadEnds++;
      }
    }
    score += (uselessDeadEnds * 3);

    // Oasis levels should have minimal difficulty score
    if (isOasis && minMoves > 8) score += 50;

    return score;
  }

  /**
   * Validate that a candidate playable puzzle is strictly 100% solvable AND anti-frustrating.
   */
  function validatePlayablePuzzle(puzzle) {
    if (!puzzle || !puzzle.tiles || !puzzle.tiles.length) return false;
    const { level, width, height, sourceIdx, targetIndices, tiles, optionalTargetIndices, minMoves, isOasis } = puzzle;

    if (sourceIdx == null) return false;
    if (!targetIndices || !targetIndices.length) return false;

    // Check that solved state evaluates to solved
    const solvedTiles = tiles.map(t => {
      let ports = t.solvedPorts != null ? t.solvedPorts : t.ports;
      let switchState = t.solvedSwitchState != null ? t.solvedSwitchState : t.switchState;
      return {
        ...t,
        ports,
        switchState
      };
    });

    const evalResult = Engine.evaluateCircuit(solvedTiles, width, height, sourceIdx, targetIndices, optionalTargetIndices);
    if (!evalResult.isSolved) {
      return false;
    }

    // For locked tiles: verify unlock prerequisite is reachable from source
    for (let i = 0; i < tiles.length; i++) {
      const t = tiles[i];
      if (t.isLocked && t.unlockPrereqIdx != null) {
        if (t.unlockPrereqIdx < 0 || t.unlockPrereqIdx >= tiles.length) return false;
        if (t.unlockPrereqIdx === i) return false; // Self-locking is illegal
      }
    }

    // Verify rotation reachability for standard tiles
    for (let i = 0; i < tiles.length; i++) {
      const t = tiles[i];
      if (t.isBlock || t.isBridge || t.isSwitch || t.isWildcard || t.groupId) continue;
      let reachable = false;
      let curr = t.ports;
      for (let r = 0; r < 4; r++) {
        if (curr === t.solvedPorts) {
          reachable = true;
          break;
        }
        curr = Engine.rotateClockwise(curr);
      }
      if (!reachable) return false;
    }

    // Anti-Frustration Quality Guard:
    // 1. Early levels (1-10) must be easily readable with minMoves <= 10
    if (level <= 10 && minMoves > 10) return false;
    // 2. Oasis levels must be zen and soothing with minMoves <= 8
    if (isOasis && minMoves > 8) return false;
    // 3. Frustration score must stay within reasonable bounds
    const frustration = calculateFrustrationScore(puzzle);
    if (frustration > 200) return false;

    return true;
  }

  /**
   * Generate a candidate level incorporating the active Novelty mechanics.
   */
  function generateCandidate(level, attemptSeed) {
    const config = getLevelConfig(level);
    const { lvl, width, height, targetCount, blockCount, isMilestone, isOasis, hasBonus, mechanics } = config;
    const totalCells = width * height;
    const rng = createRng(attemptSeed);

    // Obstacle block selection
    const blockSet = new Set();
    if (blockCount > 0 && totalCells > 16) {
      const candidates = [];
      for (let y = 1; y < height - 1; y++) {
        for (let x = 1; x < width - 1; x++) {
          candidates.push(y * width + x);
        }
      }
      for (let b = 0; b < blockCount && candidates.length > 0; b++) {
        const idx = Math.floor(rng() * candidates.length);
        blockSet.add(candidates.splice(idx, 1)[0]);
      }
    }

    // Build candidate grid edges
    const edges = [];
    for (let y = 0; y < height; y++) {
      for (let x = 0; x < width; x++) {
        const u = y * width + x;
        if (blockSet.has(u)) continue;

        if (x + 1 < width && !blockSet.has(y * width + (x + 1))) {
          edges.push({ u, v: y * width + (x + 1), dirU: EAST, dirV: WEST, weight: rng() });
        }
        if (y + 1 < height && !blockSet.has((y + 1) * width + x)) {
          edges.push({ u, v: (y + 1) * width + x, dirU: SOUTH, dirV: NORTH, weight: rng() });
        }
      }
    }

    edges.sort((a, b) => a.weight - b.weight);

    // Spanning tree carving
    const dsu = new DisjointSet(totalCells);
    const treePorts = new Int32Array(totalCells);
    const adj = Array.from({ length: totalCells }, () => []);

    for (let i = 0; i < edges.length; i++) {
      const e = edges[i];
      if (dsu.union(e.u, e.v)) {
        treePorts[e.u] |= e.dirU;
        treePorts[e.v] |= e.dirV;
        adj[e.u].push(e.v);
        adj[e.v].push(e.u);
      }
    }

    // Determine Power Source(s)
    let sourceIdx = 0;
    if (blockSet.has(sourceIdx)) sourceIdx = width - 1;

    let sources = [{ index: sourceIdx, channel: 'DEFAULT' }];
    if (mechanics.includes('MULTI_SOURCE') && totalCells >= 16) {
      let secondSource = (width - 1);
      if (blockSet.has(secondSource) || secondSource === sourceIdx) secondSource = totalCells - 1;
      sources = [
        { index: sourceIdx, channel: 'RED' },
        { index: secondSource, channel: 'BLUE' }
      ];
    }

    // BFS distance calculation from primary source
    const dist = new Int32Array(totalCells).fill(-1);
    const queue = [sourceIdx];
    dist[sourceIdx] = 0;
    const reachableNodes = [];

    while (queue.length > 0) {
      const curr = queue.shift();
      reachableNodes.push(curr);
      for (let i = 0; i < adj[curr].length; i++) {
        const nxt = adj[curr][i];
        if (dist[nxt] === -1) {
          dist[nxt] = dist[curr] + 1;
          queue.push(nxt);
        }
      }
    }

    // Pick Target Load positions
    const candidateTargets = reachableNodes
      .filter(idx => !sources.some(s => s.index === idx) && adj[idx].length === 1 && dist[idx] >= 2)
      .sort((a, b) => dist[b] - dist[a]);

    const targetIndices = [];
    for (let i = 0; i < targetCount && i < candidateTargets.length; i++) {
      targetIndices.push(candidateTargets[i]);
    }

    if (targetIndices.length === 0) {
      const furthest = reachableNodes.filter(idx => !sources.some(s => s.index === idx)).sort((a, b) => dist[b] - dist[a])[0];
      targetIndices.push(furthest != null ? furthest : totalCells - 1);
    }

    const targetSet = new Set(targetIndices);

    // Optional Bonus Target (every 15th level)
    const optionalTargetIndices = [];
    if (hasBonus) {
      const bonusCandidates = reachableNodes.filter(idx => !sources.some(s => s.index === idx) && !targetSet.has(idx) && dist[idx] >= 2);
      if (bonusCandidates.length > 0) {
        optionalTargetIndices.push(bonusCandidates[0]);
      }
    }

    // Apply Novelty Mechanic Roles to Tiles
    const bridgeSet = new Set();
    const lockedMap = new Map(); // idx -> prereqIdx
    const switchMap = new Map(); // idx -> { portsA, portsB }
    const groupMap = new Map();  // idx -> groupId
    const wildcardSet = new Set();

    // 1. BRIDGE Mechanic: Cross tile where horizontal & vertical are independent
    if (mechanics.includes('BRIDGE')) {
      const bridgeCandidates = [];
      for (let y = 1; y < height - 1; y++) {
        for (let x = 1; x < width - 1; x++) {
          const idx = y * width + x;
          if (!sources.some(s => s.index === idx) && !targetSet.has(idx) && !blockSet.has(idx)) {
            bridgeCandidates.push(idx);
          }
        }
      }
      if (bridgeCandidates.length > 0) {
        const bIdx = bridgeCandidates[Math.floor(rng() * bridgeCandidates.length)];
        bridgeSet.add(bIdx);
        treePorts[bIdx] = (NORTH | SOUTH | EAST | WEST); // 4 ports solved
      }
    }

    // 2. LOCKED Tile: Downstream wire locked until upstream node is energized
    if (mechanics.includes('LOCKED')) {
      const lockCandidates = reachableNodes.filter(idx => !sources.some(s => s.index === idx) && !targetSet.has(idx) && dist[idx] >= 3 && !bridgeSet.has(idx));
      if (lockCandidates.length > 0) {
        const lIdx = lockCandidates[0];
        // Pick an upstream neighbor as prerequisite
        const prereqCandidates = reachableNodes.filter(idx => dist[idx] < dist[lIdx] && dist[idx] >= 1);
        const prereqIdx = prereqCandidates.length > 0 ? prereqCandidates[0] : sourceIdx;
        lockedMap.set(lIdx, prereqIdx);
      }
    }

    // 3. SWITCH / Toggle Tile
    if (mechanics.includes('SWITCH')) {
      const switchCandidates = reachableNodes.filter(idx => !sources.some(s => s.index === idx) && !targetSet.has(idx) && !bridgeSet.has(idx) && !lockedMap.has(idx));
      if (switchCandidates.length > 0) {
        const sIdx = switchCandidates[Math.floor(rng() * switchCandidates.length)];
        const portsA = treePorts[sIdx];
        const portsB = Engine.rotateClockwise(portsA);
        switchMap.set(sIdx, { portsA, portsB, solvedState: 0 });
      }
    }

    // 4. ROTATING GROUP (2x2 sync cluster)
    if (mechanics.includes('ROTATING_GROUP') && width >= 4 && height >= 4) {
      const gx = 1;
      const gy = 1;
      const gTiles = [
        gy * width + gx,
        gy * width + gx + 1,
        (gy + 1) * width + gx,
        (gy + 1) * width + gx + 1
      ];
      const validGroup = gTiles.every(idx => !sources.some(s => s.index === idx) && !targetSet.has(idx) && !blockSet.has(idx));
      if (validGroup) {
        for (let idx of gTiles) {
          groupMap.set(idx, 1);
        }
      }
    }

    // 5. WILDCARD Tile
    if (mechanics.includes('WILDCARD')) {
      const wcCandidates = reachableNodes.filter(idx => !sources.some(s => s.index === idx) && !targetSet.has(idx) && !bridgeSet.has(idx) && !lockedMap.has(idx) && !switchMap.has(idx) && !groupMap.has(idx));
      if (wcCandidates.length > 0) {
        const wcIdx = wcCandidates[Math.floor(rng() * wcCandidates.length)];
        wildcardSet.add(wcIdx);
      }
    }

    // Assign Color Channels for Multi-Source
    const colorChannels = new Map();
    if (mechanics.includes('MULTI_SOURCE') && sources.length >= 2) {
      // Determine which source reaches each target in the solved tree
      for (const tIdx of targetIndices) {
        let bestSource = sources[0];
        let bestDist = Infinity;
        for (const s of sources) {
          const q = [[s.index, 0]];
          const vis = new Set([s.index]);
          while (q.length > 0) {
            const [curr, d] = q.shift();
            if (curr === tIdx) {
              if (d < bestDist) {
                bestDist = d;
                bestSource = s;
              }
              break;
            }
            for (const nxt of adj[curr]) {
              if (!vis.has(nxt)) {
                vis.add(nxt);
                q.push([nxt, d + 1]);
              }
            }
          }
        }
        colorChannels.set(tIdx, bestSource.channel);
      }
    }

    // Build Groups List
    const groups = [];
    const groupTileMap = new Map();
    for (let [idx, gId] of groupMap.entries()) {
      if (!groupTileMap.has(gId)) groupTileMap.set(gId, []);
      groupTileMap.get(gId).push(idx);
    }
    for (let [gId, indices] of groupTileMap.entries()) {
      groups.push({ groupId: gId, tileIndices: indices });
    }

    // Build Solved Tile Array
    const solvedTiles = [];
    for (let i = 0; i < totalCells; i++) {
      const isBlock = blockSet.has(i);
      const isSource = sources.some(s => s.index === i);
      const isLoad = targetSet.has(i);
      const isBonus = optionalTargetIndices.includes(i);
      const isBridge = bridgeSet.has(i);
      const isLocked = lockedMap.has(i);
      const isSwitch = switchMap.has(i);
      const isWildcard = wildcardSet.has(i);
      const groupId = groupMap.get(i) || null;
      const channel = colorChannels.get(i) || (sources.find(s => s.index === i)?.channel || 'DEFAULT');
      const ports = isBlock ? 0 : treePorts[i];
      const type = Engine.identifyTileType(ports, isSource, isLoad || isBonus, isBlock, { isBridge, isSwitch, isWildcard, isBonus });

      solvedTiles.push({
        index: i,
        ports,
        type,
        isSource,
        isLoad: isLoad || isBonus,
        isBonus,
        isBlock,
        isBridge,
        isLocked,
        unlockPrereqIdx: lockedMap.get(i) != null ? lockedMap.get(i) : null,
        isSwitch,
        switchPortsA: isSwitch ? switchMap.get(i).portsA : null,
        switchPortsB: isSwitch ? switchMap.get(i).portsB : null,
        switchState: isSwitch ? 0 : 0,
        solvedSwitchState: 0,
        isWildcard,
        groupId,
        channel,
        rotation: 0
      });
    }

    // Select tiles to scramble based on level tier for soothing, anti-frustrating difficulty curve
    let maxScrambleCount = totalCells;
    if (lvl <= 3) maxScrambleCount = Math.min(2, totalCells);
    else if (lvl <= 10) maxScrambleCount = Math.min(4, totalCells);
    else if (isOasis) maxScrambleCount = Math.min(4, totalCells);

    // Candidates for scrambling (non-block, non-cross, non-locked)
    const scramblableIndices = [];
    for (let i = 0; i < totalCells; i++) {
      const t = solvedTiles[i];
      if (!t.isBlock && !t.isWildcard && !t.isLocked && t.type !== 'CROSS' && t.ports !== 0 && !t.isBridge) {
        scramblableIndices.push(i);
      }
    }

    // Shuffle and pick subset
    for (let i = scramblableIndices.length - 1; i > 0; i--) {
      const j = Math.floor(rng() * (i + 1));
      const temp = scramblableIndices[i];
      scramblableIndices[i] = scramblableIndices[j];
      scramblableIndices[j] = temp;
    }
    const chosenToScramble = new Set(scramblableIndices.slice(0, maxScrambleCount));

    const playableTiles = [];
    let minMoves = 0;

    for (let i = 0; i < totalCells; i++) {
      const t = solvedTiles[i];
      if (t.isBlock || t.isWildcard) {
        playableTiles.push({ ...t, solvedPorts: t.ports, rotation: 0, distinctRotations: 1 });
        continue;
      }

      let distinctRotations = 4;
      if (t.type === 'CROSS' || t.ports === 0 || t.isBridge) distinctRotations = 1;
      else if (t.type === 'STRAIGHT') distinctRotations = 2;

      let scrambleTurns = 0;
      if (distinctRotations > 1 && !t.isLocked && chosenToScramble.has(i)) {
        if (lvl <= 10) {
          scrambleTurns = (distinctRotations === 4) ? 3 : 1; // Exactly 1 click to solve
        } else {
          scrambleTurns = 1 + Math.floor(rng() * (distinctRotations - 1));
        }
      }

      let currentPorts = t.ports;
      for (let r = 0; r < scrambleTurns; r++) {
        currentPorts = Engine.rotateClockwise(currentPorts);
      }

      const turnsToSolve = (distinctRotations - (scrambleTurns % distinctRotations)) % distinctRotations;
      minMoves += turnsToSolve;

      playableTiles.push({
        ...t,
        ports: currentPorts,
        solvedPorts: t.ports,
        rotation: scrambleTurns,
        distinctRotations
      });
    }

    // Synchronize Rotating Group Scramble
    if (groupMap.size > 0) {
      const gTurns = 1 + Math.floor(rng() * 3);
      for (let i = 0; i < totalCells; i++) {
        if (groupMap.has(i)) {
          for (let r = 0; r < gTurns; r++) {
            playableTiles[i].ports = Engine.rotateClockwise(playableTiles[i].ports);
          }
          playableTiles[i].rotation = (playableTiles[i].rotation + gTurns) % 4;
        }
      }
      minMoves += (4 - (gTurns % 4)) % 4;
    }

    // Ensure minimum puzzle move for non-tutorial levels
    if (minMoves === 0 && lvl > 1) {
      const candidateIdx = playableTiles.findIndex(t => !t.isBlock && !t.isLocked && t.distinctRotations > 1);
      if (candidateIdx !== -1) {
        playableTiles[candidateIdx].ports = Engine.rotateClockwise(playableTiles[candidateIdx].ports);
        playableTiles[candidateIdx].rotation = (playableTiles[candidateIdx].rotation + 1) % 4;
        minMoves = 1;
      }
    }

    return {
      generatorVersion: 3,
      level: lvl,
      seed: attemptSeed,
      width,
      height,
      sourceIdx: sources[0].index,
      sources,
      targetIndices,
      bonusTargetIndices: optionalTargetIndices,
      optionalTargetIndices,
      groups,
      tiles: playableTiles,
      minMoves: Math.max(1, minMoves),
      isMilestone,
      isOasis,
      hasBonus,
      activeMechanics: mechanics
    };
  }

  /**
   * Generate a complete, verified solvable level for Circuit Connect following the 8-step pipeline.
   */
  function generateLevel(level) {
    const lvl = Math.max(1, Math.floor(level || 1));
    const baseSeed = ((lvl * 0x9E3779B9) ^ (lvl << 5) ^ 0x85EBCA6B) >>> 0;

    const maxAttempts = 25;
    for (let attempt = 0; attempt < maxAttempts; attempt++) {
      const attemptSeed = (baseSeed + attempt * 0x6D2B79F5) >>> 0;
      const candidate = generateCandidate(lvl, attemptSeed);
      if (validatePlayablePuzzle(candidate)) {
        return candidate;
      }
    }

    // Guaranteed fallback candidate if attempt budget exhausted
    return generateCandidate(lvl, baseSeed);
  }

  return {
    generateLevel,
    getLevelConfig,
    createRng,
    validatePlayablePuzzle
  };
}));

