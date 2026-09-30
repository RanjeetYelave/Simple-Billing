/**
 * circuitEngine.js
 * Core bitmask arithmetic, graph adjacency, and multi-channel BFS circuit flow evaluator
 * for Circuit Connect (Novelty-First Expansion).
 * Supports:
 * - Splitter / Branching
 * - Bridge / Crossover (independent orthogonal layers)
 * - Locked tiles (dynamic prerequisite unlock)
 * - Switch / Toggle tiles (multi-state routing)
 * - Multiple power sources & Colored circuits (channel-isolated flow with shape accessibility)
 * - Rotating groups (multi-tile synchronized rotation)
 * - Wildcards (adaptive connectivity)
 * - Bonus / Optional targets
 * Completely self-contained and zero-dependency.
 */

(function (root, factory) {
  if (typeof define === 'function' && define.amd) {
    define([], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  } else {
    root.CircuitEngine = factory();
  }
}(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  // 4-bit Direction Bitmasks
  const NORTH = 1; // 0001
  const EAST = 2;  // 0010
  const SOUTH = 4; // 0100
  const WEST = 8;  // 1000

  const OPPOSITE = {
    [NORTH]: SOUTH,
    [SOUTH]: NORTH,
    [EAST]: WEST,
    [WEST]: EAST
  };

  const DELTAS = {
    [NORTH]: { dx: 0, dy: -1 },
    [EAST]:  { dx: 1, dy: 0 },
    [SOUTH]: { dx: 0, dy: 1 },
    [WEST]:  { dx: -1, dy: 0 }
  };

  const DIRS = [NORTH, EAST, SOUTH, WEST];

  // Circuit Channels / Colors for Multi-Source & Accessibility
  const CHANNELS = {
    DEFAULT: { id: 'DEFAULT', name: 'Power', color: '#38bdf8', glow: 'rgba(56,189,248,0.45)', symbol: '⚡' },
    RED:     { id: 'RED',     name: 'Red Circuit',   color: '#ef4444', glow: 'rgba(239,68,68,0.45)',   symbol: '🔴' },
    BLUE:    { id: 'BLUE',    name: 'Blue Circuit',  color: '#00e5ff', glow: 'rgba(0,229,255,0.45)',   symbol: '🔷' },
    AMBER:   { id: 'AMBER',   name: 'Amber Circuit', color: '#f59e0b', glow: 'rgba(245,158,11,0.45)',  symbol: '⭐' }
  };

  /**
   * Rotate a 4-bit port mask 90 degrees clockwise.
   */
  function rotateClockwise(ports) {
    return (((ports << 1) & 15) | (ports >> 3));
  }

  /**
   * Rotate a 4-bit port mask 90 degrees counter-clockwise.
   */
  function rotateCounterClockwise(ports) {
    return ((ports >> 1) | ((ports << 3) & 15));
  }

  /**
   * Determine tile type based on number of ports and special attributes.
   */
  function identifyTileType(ports, isSource, isLoad, isBlock, extra = {}) {
    if (isBlock) return 'BLOCK';
    if (extra.isWildcard) return 'WILDCARD';
    if (extra.isBridge) return 'BRIDGE';
    if (extra.isSwitch) return 'SWITCH';
    if (isSource) return 'SOURCE';
    if (isLoad) return extra.isBonus ? 'BONUS_LOAD' : 'LOAD';
    if (!ports) return 'EMPTY';
    const count = ((ports & 1) ? 1 : 0) + ((ports & 2) ? 1 : 0) + ((ports & 4) ? 1 : 0) + ((ports & 8) ? 1 : 0);
    if (count === 1) return 'END';
    if (count === 4) return 'CROSS';
    if (count === 3) return 'TEE';
    if (count === 2) {
      if ((ports === (NORTH | SOUTH)) || (ports === (EAST | WEST))) {
        return 'STRAIGHT';
      }
      return 'CORNER';
    }
    return 'UNKNOWN';
  }

  /**
   * Evaluates the circuit flow across all channels/sources using multi-root BFS.
   * Understands Bridge crossovers (independent layers), Lock unlocking prerequisites,
   * Switches, Wildcards, and Multi-Color Channels.
   *
   * @param {Array} tiles 1D array of tile objects
   * @param {number} width Grid width
   * @param {number} height Grid height
   * @param {number|Array} sourceIdx Single power source index OR array of source objects [{ index, channel }]
   * @param {Array<number>} targetIndices Indices of required load/target bulbs
   * @param {Array<number>} [optionalTargetIndices] Optional bonus target indices
   * @returns {Object} Comprehensive evaluation report
   */
  function evaluateCircuit(tiles, width, height, sourceIdx, targetIndices, optionalTargetIndices = []) {
    if (!tiles || !tiles.length) {
      return {
        isSolved: false,
        bonusSolved: false,
        energizedSet: new Set(),
        energizedDetails: new Map(), // idx -> { channels: Set, layers: { v: bool, h: bool } }
        unlockedSet: new Set(),
        depthMap: new Map(),
        reachedTargets: 0,
        totalTargets: targetIndices ? targetIndices.length : 0,
        reachedBonusTargets: 0,
        totalBonusTargets: optionalTargetIndices ? optionalTargetIndices.length : 0
      };
    }

    // Normalize sources array: [{ index, channel }]
    const sources = [];
    if (Array.isArray(sourceIdx)) {
      for (let s of sourceIdx) {
        if (typeof s === 'number') sources.push({ index: s, channel: (tiles[s] && tiles[s].channel) || 'DEFAULT' });
        else if (s && typeof s.index === 'number') sources.push({ index: s.index, channel: s.channel || (tiles[s.index] && tiles[s.index].channel) || 'DEFAULT' });
      }
    } else if (typeof sourceIdx === 'number' && sourceIdx >= 0 && sourceIdx < tiles.length) {
      sources.push({ index: sourceIdx, channel: (tiles[sourceIdx] && tiles[sourceIdx].channel) || 'DEFAULT' });
    }

    // Fallback: If no sources supplied explicitly, scan tiles for isSource
    if (sources.length === 0) {
      for (let i = 0; i < tiles.length; i++) {
        if (tiles[i] && tiles[i].isSource) {
          sources.push({ index: i, channel: tiles[i].channel || 'DEFAULT' });
        }
      }
    }

    // Two-pass dynamic Lock evaluation:
    // 1. First run BFS to find which tiles are energized from sources
    // 2. Determine which locked tiles have their prerequisites energized -> unlock them
    // 3. Complete BFS propagation through any newly unlocked or activated paths

    const energizedSet = new Set();
    const depthMap = new Map();
    const energizedDetails = new Map(); // idx -> { channels: Set, layers: { v: bool, h: bool }, depth: number }
    const unlockedSet = new Set();

    // Helper: Initialize tile detail
    function getTileDetail(idx) {
      if (!energizedDetails.has(idx)) {
        energizedDetails.set(idx, { channels: new Set(), layers: { v: false, h: false }, depth: 0 });
      }
      return energizedDetails.get(idx);
    }

    // Multi-root Queue: items are { idx, channel, enterDir, depth }
    // enterDir is the direction from which signal enters this tile (e.g. SOUTH if coming from NORTH neighbor moving down)
    const queue = [];

    // Initialize sources
    for (let s of sources) {
      if (s.index >= 0 && s.index < tiles.length) {
        const sTile = tiles[s.index];
        if (sTile && !sTile.isBlock) {
          energizedSet.add(s.index);
          depthMap.set(s.index, 0);
          const detail = getTileDetail(s.index);
          detail.channels.add(s.channel || 'DEFAULT');
          detail.depth = 0;
          queue.push({ idx: s.index, channel: s.channel || 'DEFAULT', enterDir: 0, depth: 0 });
        }
      }
    }

    // Pre-pass: Mark locks whose prerequisite is already energized
    function updateUnlockedLocks() {
      let newlyUnlocked = false;
      for (let i = 0; i < tiles.length; i++) {
        const t = tiles[i];
        if (t && t.isLocked && !unlockedSet.has(i)) {
          const prereqIdx = t.unlockPrereqIdx != null ? t.unlockPrereqIdx : -1;
          if (prereqIdx >= 0 && energizedSet.has(prereqIdx)) {
            unlockedSet.add(i);
            newlyUnlocked = true;
          }
        }
      }
      return newlyUnlocked;
    }

    // BFS propagation loop with multi-channel and layer awareness
    const visitedTransitions = new Set(); // Key: `${idx}:${channel}:${enterDir}`

    while (queue.length > 0) {
      const { idx, channel, enterDir, depth } = queue.shift();
      const currTile = tiles[idx];
      if (!currTile || currTile.isBlock) continue;

      const transKey = `${idx}:${channel}:${enterDir}`;
      if (visitedTransitions.has(transKey)) continue;
      visitedTransitions.add(transKey);

      energizedSet.add(idx);
      const detail = getTileDetail(idx);
      detail.channels.add(channel);
      if (!depthMap.has(idx) || depth < depthMap.get(idx)) {
        depthMap.set(idx, depth);
        detail.depth = depth;
      }

      const cx = idx % width;
      const cy = Math.floor(idx / width);

      // Determine available outgoing ports for this tile based on type and entry direction
      let outPorts = 0;

      if (currTile.isBridge) {
        // BRIDGE MECHANIC: Vertical (N-S) and Horizontal (E-W) are electrically independent!
        if (enterDir === 0) {
          // Source tile bridge - feeds all ports
          outPorts = currTile.ports || (NORTH | SOUTH | EAST | WEST);
          detail.layers.v = true;
          detail.layers.h = true;
        } else if (enterDir === NORTH || enterDir === SOUTH) {
          // Signal came along vertical track
          detail.layers.v = true;
          outPorts = (currTile.ports & (NORTH | SOUTH));
        } else if (enterDir === EAST || enterDir === WEST) {
          // Signal came along horizontal track
          detail.layers.h = true;
          outPorts = (currTile.ports & (EAST | WEST));
        }
      } else if (currTile.isWildcard) {
        // WILDCARD: Adaptive tile that connects in all 4 directions if adjacent to an energized neighbor
        outPorts = (NORTH | EAST | SOUTH | WEST);
      } else if (currTile.isSwitch) {
        // SWITCH: Uses ports of current active state (state 0 -> switchPortsA, state 1 -> switchPortsB)
        const sState = currTile.switchState || 0;
        outPorts = (sState === 1 && currTile.switchPortsB != null) ? currTile.switchPortsB : (currTile.switchPortsA != null ? currTile.switchPortsA : currTile.ports);
      } else {
        // Standard tile (including Splitters, Corners, Straights, Tees, Crosses)
        outPorts = currTile.ports || 0;
      }

      // Propagate across valid outgoing ports
      for (let i = 0; i < DIRS.length; i++) {
        const dir = DIRS[i];
        if ((outPorts & dir) === 0) continue;

        const delta = DELTAS[dir];
        const nx = cx + delta.dx;
        const ny = cy + delta.dy;

        // Grid boundaries
        if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;

        const nIdx = ny * width + nx;
        const nTile = tiles[nIdx];
        if (!nTile || nTile.isBlock) continue;

        // Check channel compatibility if neighboring tile has a dedicated color channel
        if (nTile.channel && nTile.channel !== 'DEFAULT' && nTile.channel !== channel) {
          continue; // Incompatible color circuit channel
        }

        const oppDir = OPPOSITE[dir];

        // Check neighbor port acceptance
        let neighborAccepts = false;
        if (nTile.isBridge) {
          // Neighbor Bridge accepts if it has port in oppDir
          const nbPorts = nTile.ports || (NORTH | SOUTH | EAST | WEST);
          if ((nbPorts & oppDir) !== 0) neighborAccepts = true;
        } else if (nTile.isWildcard) {
          neighborAccepts = true;
        } else if (nTile.isSwitch) {
          const sState = nTile.switchState || 0;
          const nbPorts = (sState === 1 && nTile.switchPortsB != null) ? nTile.switchPortsB : (nTile.switchPortsA != null ? nTile.switchPortsA : nTile.ports);
          if ((nbPorts & oppDir) !== 0) neighborAccepts = true;
        } else {
          const nbPorts = nTile.ports || 0;
          if ((nbPorts & oppDir) !== 0) neighborAccepts = true;
        }

        if (neighborAccepts) {
          queue.push({ idx: nIdx, channel, enterDir: oppDir, depth: depth + 1 });
        }
      }

      // If this step energized a prerequisite for a locked tile, update unlockedSet
      updateUnlockedLocks();
    }

    // Check required targets (must be energized and match required channel if color-coded)
    const targets = targetIndices || [];
    let reachedTargets = 0;
    for (let i = 0; i < targets.length; i++) {
      const tIdx = targets[i];
      if (energizedSet.has(tIdx)) {
        const tTile = tiles[tIdx];
        const details = energizedDetails.get(tIdx);
        if (!tTile || !tTile.channel || tTile.channel === 'DEFAULT') {
          reachedTargets++;
        } else if (details && details.channels.has(tTile.channel)) {
          reachedTargets++;
        }
      }
    }

    // Check optional bonus targets
    const bonusTargets = optionalTargetIndices || [];
    let reachedBonusTargets = 0;
    for (let i = 0; i < bonusTargets.length; i++) {
      const bIdx = bonusTargets[i];
      if (energizedSet.has(bIdx)) {
        reachedBonusTargets++;
      }
    }

    const isSolved = targets.length > 0 && reachedTargets === targets.length;
    const bonusSolved = bonusTargets.length > 0 && reachedBonusTargets === bonusTargets.length;

    return {
      isSolved,
      bonusSolved,
      energizedSet,
      energizedDetails,
      unlockedSet,
      depthMap,
      reachedTargets,
      totalTargets: targets.length,
      reachedBonusTargets,
      totalBonusTargets: bonusTargets.length
    };
  }

  /**
   * Calculate 1, 2, or 3 star rating based on move efficiency and bonus objective.
   */
  function calculateStars(moves, minMoves, bonusCompleted = false) {
    const baseMin = Math.max(1, minMoves || 1);
    const threeStarThreshold = baseMin + Math.max(1, Math.floor(baseMin * 0.3));
    const twoStarThreshold = Math.max(threeStarThreshold + 1, baseMin * 2);

    let stars = 1;
    if (moves <= threeStarThreshold) {
      stars = 3;
    } else if (moves <= twoStarThreshold) {
      stars = 2;
    }

    // Bonus objective can lift a 2-star rating to 3-star (capped strictly at 3)
    if (bonusCompleted && stars < 3) {
      stars = Math.min(3, stars + 1);
    }
    return stars;
  }

  /**
   * Calculate minimum rotation steps and direction from currPorts to target solvedPorts.
   */
  function calculateRotationStep(currPorts, targetPorts) {
    if (currPorts === targetPorts) return { steps: 0, dir: 'NONE', glyph: '' };
    let cw = currPorts;
    for (let i = 1; i <= 3; i++) {
      cw = rotateClockwise(cw);
      if (cw === targetPorts) {
        if (i === 1) return { steps: 1, dir: 'CW', glyph: '↻' };
        if (i === 2) return { steps: 2, dir: '180', glyph: '↻↻' };
        if (i === 3) return { steps: 1, dir: 'CCW', glyph: '↺' };
      }
    }
    return { steps: 1, dir: 'CW', glyph: '↻' };
  }

  /**
   * 💡 INTELLIGENT HINT SOLVER (Tier 1 & Tier 2)
   * Analyzes active circuit state against solution blueprint and identifies
   * highest-leverage next move with contextual explanation.
   */
  function findIntelligentHint(tiles, width, height, sourceIdx, targetIndices, optionalTargetIndices, tier = 1) {
    if (!tiles || !tiles.length || sourceIdx == null) return null;

    const evalResult = evaluateCircuit(tiles, width, height, sourceIdx, targetIndices, optionalTargetIndices);
    const energizedSet = evalResult.energizedSet;
    const targets = targetIndices || [];

    // Helper: Find unreached targets
    const unreachedTargets = targets.filter(t => !energizedSet.has(t));
    const targetSet = new Set(unreachedTargets.length ? unreachedTargets : targets);

    // 1. Check for locked tile prerequisites that need power
    for (let i = 0; i < tiles.length; i++) {
      const t = tiles[i];
      if (t && t.isLocked && t.unlockPrereqIdx != null && !energizedSet.has(t.unlockPrereqIdx)) {
        const prereqIdx = t.unlockPrereqIdx;
        const pt = tiles[prereqIdx];
        if (pt && !pt.isBlock) {
          const sPorts = pt.solvedPorts != null ? pt.solvedPorts : pt.ports;
          if (pt.ports !== sPorts) {
            const rot = calculateRotationStep(pt.ports, sPorts);
            return {
              tier: 1,
              primaryIdx: prereqIdx,
              chainIndices: [prereqIdx],
              action: pt.isSwitch ? 'TOGGLE' : 'ROTATE',
              dir: rot.dir,
              glyph: pt.isSwitch ? '⚡' : rot.glyph,
              solvedPorts: sPorts,
              message: pt.isSwitch
                ? '💡 Activate this switch to unlock the prerequisite barrier.'
                : '💡 Power this connection to unlock the barrier.',
              targetTileType: identifyTileType(pt.ports, pt.isSource, pt.isLoad, pt.isBlock, pt)
            };
          }
        }
      }
    }

    // 2. Find the energized frontier tile leading towards unreached target
    let bestCandidate = null;
    let bestDist = Infinity;
    const candidates = [];

    for (let i = 0; i < tiles.length; i++) {
      const t = tiles[i];
      if (!t || t.isBlock) continue;
      const sPorts = t.solvedPorts != null ? t.solvedPorts : t.ports;
      const sSwitch = t.solvedSwitchState != null ? t.solvedSwitchState : t.switchState;

      const isMisaligned = (t.ports !== sPorts) || (t.isSwitch && t.switchState !== sSwitch);
      if (!isMisaligned) continue;

      // Calculate distance to nearest energized tile
      const tx = i % width;
      const ty = Math.floor(i / width);
      let isAdjacentToPower = false;

      for (const dir of DIRS) {
        const delta = DELTAS[dir];
        const nx = tx + delta.dx;
        const ny = ty + delta.dy;
        if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
          const nIdx = ny * width + nx;
          if (energizedSet.has(nIdx)) {
            isAdjacentToPower = true;
            break;
          }
        }
      }

      // Distance to target
      let minTargetDist = Infinity;
      targetSet.forEach(tIdx => {
        const tgx = tIdx % width;
        const tgy = Math.floor(tIdx / width);
        const dist = Math.abs(tx - tgx) + Math.abs(ty - tgy);
        if (dist < minTargetDist) minTargetDist = dist;
      });

      const priorityScore = (isAdjacentToPower ? 0 : 100) + minTargetDist;
      candidates.push({ idx: i, priorityScore, isAdjacentToPower, tile: t, sPorts, sSwitch });
    }

    candidates.sort((a, b) => a.priorityScore - b.priorityScore);

    if (candidates.length === 0) {
      return null;
    }

    const top = candidates[0];
    const topTile = top.tile;
    const rot = calculateRotationStep(topTile.ports, top.sPorts);

    // Build smart contextual message
    let message = `💡 Try rotating this tile ${rot.glyph}`;
    if (topTile.isSwitch) {
      message = '💡 Activate this switch to open the powered path.';
    } else {
      const type = identifyTileType(topTile.ports, topTile.isSource, topTile.isLoad, topTile.isBlock, topTile);
      if (type === 'CORNER') {
        message = `💡 Rotate this corner ${rot.glyph} to continue the powered path.`;
      } else if (type === 'STRAIGHT') {
        message = `💡 Align this straight wire ${rot.glyph} to bridge the circuit.`;
      } else if (type === 'TEE') {
        message = `💡 Rotate this splitter branch ${rot.glyph} toward the target.`;
      } else if (type === 'BRIDGE') {
        message = `💡 Align this bridge crossover ${rot.glyph} for multi-layer routing.`;
      }
    }

    // Tier 2: include chain of 2-3 consecutive tiles
    const chainIndices = candidates.slice(0, tier >= 2 ? 3 : 1).map(c => c.idx);
    if (tier >= 2 && chainIndices.length > 1) {
      message = '💡 These connections form the next path toward the target.';
    }

    return {
      tier,
      primaryIdx: top.idx,
      chainIndices,
      action: topTile.isSwitch ? 'TOGGLE' : 'ROTATE',
      dir: rot.dir,
      glyph: topTile.isSwitch ? '⚡' : rot.glyph,
      solvedPorts: top.sPorts,
      message,
      targetTileType: identifyTileType(topTile.ports, topTile.isSource, topTile.isLoad, topTile.isBlock, topTile)
    };
  }

  /**
   * Legacy / array-based wrapper for findIntelligentHint
   */
  function findNextHintTiles(tiles, width, height, sourceIdx, targetIndices, maxCount = 3) {
    const hint = findIntelligentHint(tiles, width, height, sourceIdx, targetIndices, [], maxCount >= 2 ? 2 : 1);
    if (!hint) return [];
    return hint.chainIndices || [hint.primaryIdx];
  }

  return {
    NORTH,
    EAST,
    SOUTH,
    WEST,
    OPPOSITE,
    DELTAS,
    DIRS,
    CHANNELS,
    rotateClockwise,
    rotateCounterClockwise,
    identifyTileType,
    evaluateCircuit,
    calculateStars,
    calculateRotationStep,
    findIntelligentHint,
    findNextHintTiles
  };
}));

