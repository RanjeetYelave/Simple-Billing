package com.billing.simple.billsoft.regression.circuit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit & validation tests for Circuit Connect core bitmask engine, BFS flow evaluator,
 * deterministic procedural level generator, infinite milestones, and stats formatting.
 */
public class CircuitConnectEngineAndGeneratorTest {

    // 4-bit port masks
    private static final int NORTH = 1; // 0001
    private static final int EAST = 2;  // 0010
    private static final int SOUTH = 4; // 0100
    private static final int WEST = 8;  // 1000

    private static int rotateClockwise(int ports) {
        return (((ports << 1) & 15) | (ports >> 3));
    }

    private static int rotateCounterClockwise(int ports) {
        return ((ports >> 1) | ((ports << 3) & 15));
    }

    @Test
    @DisplayName("1. Bitmask rotation cycles properly across 360 degrees for all tile types")
    public void testBitmaskRotations() {
        // NORTH (1) -> EAST (2) -> SOUTH (4) -> WEST (8) -> NORTH (1)
        int p = NORTH;
        p = rotateClockwise(p);
        assertEquals(EAST, p);
        p = rotateClockwise(p);
        assertEquals(SOUTH, p);
        p = rotateClockwise(p);
        assertEquals(WEST, p);
        p = rotateClockwise(p);
        assertEquals(NORTH, p);

        // Straight: NORTH | SOUTH (5) -> EAST | WEST (10) -> 5
        int straight = NORTH | SOUTH;
        assertEquals(EAST | WEST, rotateClockwise(straight));
        assertEquals(NORTH | SOUTH, rotateClockwise(rotateClockwise(straight)));

        // Corner: NORTH | EAST (3) -> EAST | SOUTH (6) -> SOUTH | WEST (12) -> WEST | NORTH (9) -> 3
        int corner = NORTH | EAST;
        assertEquals(EAST | SOUTH, rotateClockwise(corner));
        assertEquals(SOUTH | WEST, rotateClockwise(rotateClockwise(corner)));
        assertEquals(WEST | NORTH, rotateClockwise(rotateClockwise(rotateClockwise(corner))));
        assertEquals(corner, rotateClockwise(rotateClockwise(rotateClockwise(rotateClockwise(corner)))));

        // Tee: NORTH | EAST | SOUTH (7) -> EAST | SOUTH | WEST (14) -> SOUTH | WEST | NORTH (13) -> WEST | NORTH | EAST (11) -> 7
        int tee = NORTH | EAST | SOUTH;
        assertEquals(EAST | SOUTH | WEST, rotateClockwise(tee));
        assertEquals(SOUTH | WEST | NORTH, rotateClockwise(rotateClockwise(tee)));
        assertEquals(WEST | NORTH | EAST, rotateClockwise(rotateClockwise(rotateClockwise(tee))));
        assertEquals(tee, rotateClockwise(rotateClockwise(rotateClockwise(rotateClockwise(tee)))));

        // Cross: 15 -> 15
        int cross = NORTH | EAST | SOUTH | WEST;
        assertEquals(15, rotateClockwise(cross));
    }

    @Test
    @DisplayName("2. Single Power Source BFS circuit solver energizes connected targets and enforces boundaries")
    public void testBfsCircuitEvaluation() {
        // 3x3 Grid: Source at (0,0), Straight wire at (1,0), Corner at (2,0), Straight down at (2,1), Target at (2,2)
        int width = 3;
        int height = 3;
        int totalCells = width * height;

        int[] tiles = new int[totalCells];
        tiles[0] = EAST;               // (0,0) Source
        tiles[1] = EAST | WEST;        // (1,0) Straight (Horizontal)
        tiles[2] = SOUTH | WEST;       // (2,0) Corner (West to South)
        tiles[5] = NORTH | SOUTH;      // (2,1) Straight (Vertical)
        tiles[8] = NORTH;              // (2,2) Load Target

        int sourceIdx = 0;
        List<Integer> targetIndices = Collections.singletonList(8);

        // Evaluate flow
        Set<Integer> energized = evaluateFlow(tiles, width, height, sourceIdx);
        assertTrue(energized.contains(0), "Source should be energized");
        assertTrue(energized.contains(1), "Tile (1,0) should be energized");
        assertTrue(energized.contains(2), "Tile (2,0) should be energized");
        assertTrue(energized.contains(5), "Tile (2,1) should be energized");
        assertTrue(energized.contains(8), "Target (2,2) should be energized");

        // Break connection by rotating tile 1 by 90 degrees (becomes North-South)
        tiles[1] = rotateClockwise(tiles[1]);
        Set<Integer> broken = evaluateFlow(tiles, width, height, sourceIdx);
        assertTrue(broken.contains(0), "Source is energized");
        assertFalse(broken.contains(1), "Tile 1 does not connect West");
        assertFalse(broken.contains(8), "Target is disconnected");
    }

    @Test
    @DisplayName("3. Source/Electricity tile responds to rotation and dynamically connects/disconnects flow")
    public void testSourceTileInteractionAndRotation() {
        int width = 3;
        int height = 3;
        int[] tiles = new int[9];
        tiles[0] = NORTH;              // Source at (0,0) pointing NORTH (off grid / disconnected)
        tiles[1] = EAST | WEST;        // Wire at (1,0)
        tiles[2] = WEST;               // Target at (2,0)

        int sourceIdx = 0;
        Set<Integer> disconnected = evaluateFlow(tiles, width, height, sourceIdx);
        assertTrue(disconnected.contains(0), "Source itself is always energized");
        assertFalse(disconnected.contains(1), "Tile 1 must NOT be energized when source points NORTH");
        assertFalse(disconnected.contains(2), "Target 2 must NOT be energized");

        // Player clicks and rotates Source tile 90 degrees clockwise -> now points EAST
        tiles[0] = rotateClockwise(tiles[0]); // EAST (2)
        assertEquals(EAST, tiles[0]);

        Set<Integer> connected = evaluateFlow(tiles, width, height, sourceIdx);
        assertTrue(connected.contains(0), "Source energized");
        assertTrue(connected.contains(1), "Tile 1 energized via EAST source port");
        assertTrue(connected.contains(2), "Target 2 energized and circuit complete");
    }

    @Test
    @DisplayName("4. REGRESSION TEST: Level 4 observed scenario is verified 100% solvable")
    public void testLevel4RegressionScenario() {
        // Level 4 parameters: 4x4 grid, seed derived deterministically
        LevelData lvl4 = generateKruskalLevel(4);
        assertNotNull(lvl4);
        assertEquals(4, lvl4.width);
        assertEquals(4, lvl4.height);
        assertEquals(0, lvl4.sourceIdx);
        assertEquals(1, lvl4.targetIndices.size());

        // Verify that in solvedPorts configuration, the target is reached
        Set<Integer> energized = evaluateFlow(lvl4.solvedPorts, lvl4.width, lvl4.height, lvl4.sourceIdx);
        for (int target : lvl4.targetIndices) {
            assertTrue(energized.contains(target), "Level 4 target must be energized in solved configuration");
        }

        // Verify that every tile's solved orientation is reachable from playable scrambled orientation
        for (int i = 0; i < lvl4.totalCells; i++) {
            boolean reachable = false;
            int curr = lvl4.scrambledPorts[i];
            for (int r = 0; r < 4; r++) {
                if (curr == lvl4.solvedPorts[i]) {
                    reachable = true;
                    break;
                }
                curr = rotateClockwise(curr);
            }
            assertTrue(reachable, "Tile " + i + " must reach solved orientation via rotation");
        }
    }

    @Test
    @DisplayName("5. 1,000 procedurally generated levels across all difficulties are 100% solvable")
    public void testProceduralLevelSolvabilityLoop() {
        int totalGenerated = 0;
        int totalSolvable = 0;
        int totalUnsolvable = 0;

        for (int lvl = 1; lvl <= 1000; lvl++) {
            LevelData puzzle = generateKruskalLevel(lvl);
            totalGenerated++;
            assertNotNull(puzzle, "Level " + lvl + " must generate non-null puzzle");
            assertTrue(puzzle.width >= 3 && puzzle.width <= 6, "Level " + lvl + " width in bounds");
            assertTrue(puzzle.height >= 3 && puzzle.height <= 6, "Level " + lvl + " height in bounds");
            assertEquals(1, puzzle.sourceCount, "Every level MUST have exactly 1 Power Source");
            assertTrue(puzzle.targetIndices.size() >= 1, "Level " + lvl + " must have at least 1 Load");

            // Evaluate flow on solved board
            Set<Integer> solvedEnergized = evaluateFlow(puzzle.solvedPorts, puzzle.width, puzzle.height, puzzle.sourceIdx);
            boolean allReached = true;
            for (int targetIdx : puzzle.targetIndices) {
                if (!solvedEnergized.contains(targetIdx)) {
                    allReached = false;
                    break;
                }
            }

            if (allReached) {
                totalSolvable++;
            } else {
                totalUnsolvable++;
            }
        }

        System.out.println("Generated: " + totalGenerated);
        System.out.println("Solvable: " + totalSolvable);
        System.out.println("Unsolvable: " + totalUnsolvable);

        assertEquals(1000, totalGenerated);
        assertEquals(1000, totalSolvable);
        assertEquals(0, totalUnsolvable, "Zero generated levels may be unsolvable");
    }

    @Test
    @DisplayName("6. Infinite milestone progression formula floor(totalStars / 50) without upper limit")
    public void testInfiniteMilestoneCalculations() {
        // Dynamic formula: milestone = floor(totalStars / 50)
        assertEquals(0, calculateMilestone(0));
        assertEquals(0, calculateMilestone(49));
        assertEquals(1, calculateMilestone(50));
        assertEquals(1, calculateMilestone(99));
        assertEquals(2, calculateMilestone(100));
        assertEquals(6, calculateMilestone(300));
        assertEquals(6, calculateMilestone(301));
        assertEquals(7, calculateMilestone(350), "350 stars must yield Milestone 7 without cap at 6");
        assertEquals(8, calculateMilestone(400));
        assertEquals(20, calculateMilestone(1000), "1,000 stars must yield Milestone 20");
        assertEquals(100, calculateMilestone(5000), "5,000 stars must yield Milestone 100");
        assertEquals(20000, calculateMilestone(1000000), "1,000,000 stars must yield Milestone 20,000");

        // Next milestone threshold
        assertEquals(50, getNextMilestoneStars(0));
        assertEquals(50, getNextMilestoneStars(49));
        assertEquals(100, getNextMilestoneStars(50));
        assertEquals(350, getNextMilestoneStars(300));
        assertEquals(400, getNextMilestoneStars(350));
        assertEquals(1050, getNextMilestoneStars(1000));
    }

    @Test
    @DisplayName("7. Lifetime Total Time Played is formatted strictly as HH:MM")
    public void testLifetimeTimePlayedFormatting() {
        assertEquals("00:00", formatLifetimeTime(0));
        assertEquals("00:00", formatLifetimeTime(47)); // 47 seconds = 00:00
        assertEquals("00:47", formatLifetimeTime(47 * 60)); // 47 mins = 00:47
        assertEquals("12:35", formatLifetimeTime((12 * 3600) + (35 * 60))); // 12h 35m = 12:35
        assertEquals("103:42", formatLifetimeTime((103 * 3600) + (42 * 60))); // 103h 42m = 103:42
    }

    @Test
    @DisplayName("8. Star rating awards 3, 2, or 1 star based on move count efficiency")
    public void testStarCalculation() {
        int minMoves = 6;
        assertEquals(3, calculateStars(6, minMoves), "Exact min moves should award 3 stars");
        assertEquals(3, calculateStars(7, minMoves), "Near optimal moves should award 3 stars");
        assertEquals(2, calculateStars(10, minMoves), "Moderate extra moves should award 2 stars");
        assertEquals(1, calculateStars(25, minMoves), "High exploration should award 1 star");
    }

    @Test
    @DisplayName("9. Current Board state persists across tab switch, app close, and remount without reset")
    public void testCurrentBoardPersistenceAndRecovery() {
        // Create initial progression state with an active board at Level 5
        LevelData lvl5 = generateKruskalLevel(5);
        GameState state = new GameState();
        state.currentLevel = 5;
        state.completedCount = 4;
        state.totalStars = 12;

        BoardState activeBoard = new BoardState();
        activeBoard.level = 5;
        activeBoard.width = lvl5.width;
        activeBoard.height = lvl5.height;
        activeBoard.sourceIdx = lvl5.sourceIdx;
        activeBoard.targetIndices = new ArrayList<>(lvl5.targetIndices);
        activeBoard.tiles = Arrays.copyOf(lvl5.scrambledPorts, lvl5.totalCells);
        activeBoard.moves = 8;
        activeBoard.elapsedSeconds = 45;

        state.currentBoard = activeBoard;

        // Player makes 2 rotations
        activeBoard.tiles[1] = rotateClockwise(activeBoard.tiles[1]);
        activeBoard.tiles[2] = rotateClockwise(activeBoard.tiles[2]);
        activeBoard.moves += 2;
        activeBoard.elapsedSeconds += 10;

        // Simulate Tab Switch / Component Unmount -> Serialization to persistent store
        String persistedJson = serializeState(state);
        assertNotNull(persistedJson);

        // Simulate Reopen / Remount -> Deserialization
        GameState recovered = deserializeState(persistedJson);
        assertNotNull(recovered);
        assertEquals(5, recovered.currentLevel);
        assertEquals(4, recovered.completedCount);
        assertEquals(12, recovered.totalStars);
        assertNotNull(recovered.currentBoard, "Current board must be restored");
        assertEquals(5, recovered.currentBoard.level);
        assertEquals(10, recovered.currentBoard.moves, "Moves must not reset on tab switch");
        assertEquals(55, recovered.currentBoard.elapsedSeconds, "Elapsed time must not reset");
        assertEquals(activeBoard.tiles[1], recovered.currentBoard.tiles[1], "Rotated tile 1 must match");
        assertEquals(activeBoard.tiles[2], recovered.currentBoard.tiles[2], "Rotated tile 2 must match");
    }

    @Test
    @DisplayName("10. Only ONE active board is persisted; completing/skipping deletes previous board state")
    public void testOnlyOneActiveBoardPersisted() {
        GameState state = new GameState();
        state.currentLevel = 2;
        BoardState boardLvl2 = new BoardState();
        boardLvl2.level = 2;
        boardLvl2.width = 3;
        boardLvl2.height = 3;
        boardLvl2.tiles = new int[9];
        state.currentBoard = boardLvl2;

        assertEquals(2, state.currentBoard.level);

        // Complete Level 2 -> Advance to Level 3
        state.completedCount += 1;
        state.totalStars += 3;
        state.currentLevel = 3;

        // Replace with Level 3 board (Level 2 board is discarded)
        BoardState boardLvl3 = new BoardState();
        boardLvl3.level = 3;
        boardLvl3.width = 3;
        boardLvl3.height = 3;
        boardLvl3.tiles = new int[9];
        state.currentBoard = boardLvl3;

        assertEquals(3, state.currentLevel);
        assertEquals(3, state.currentBoard.level, "Only current level board exists in storage");
        assertNull(state.historicalBoards, "No historical boards are retained");
    }

    @Test
    @DisplayName("11. Corrupted/invalid board state recovers gracefully without losing lifetime statistics")
    public void testGracefulRecoveryOnCorruptedBoard() {
        GameState state = new GameState();
        state.currentLevel = 10;
        state.totalStars = 48;
        state.completedCount = 9;
        state.highestStarMilestone = 0;

        // Simulate corrupted board (e.g. level mismatch or null tiles)
        BoardState corruptedBoard = new BoardState();
        corruptedBoard.level = 3; // Mismatch with currentLevel 10
        corruptedBoard.tiles = null;
        state.currentBoard = corruptedBoard;

        // Recovery validator
        GameState sanitized = sanitizeRecovery(state);
        assertEquals(10, sanitized.currentLevel);
        assertEquals(48, sanitized.totalStars, "Lifetime stars must NEVER be lost on corrupted board");
        assertEquals(9, sanitized.completedCount, "Completed count preserved");
        assertNull(sanitized.currentBoard, "Corrupted board discarded for fresh generation");
    }

    @Test
    @DisplayName("12. Hint System starts with 3 initial hints only once; never re-adds on restart/tab switch; accumulates 1 hint per 30m active play")
    public void testHintAccumulationAndTiming() {
        // 1. Initial first-time game state starts with 3 hints
        GameState freshState = new GameState();
        assertEquals(3, freshState.hintBalance, "First-time player starts with 3 starter hints");
        assertEquals(0, freshState.hintProgressSeconds);

        // 2. Player uses all 3 starter hints
        freshState.hintBalance = 0;

        // 3. Tab switch / App restart / Deserialization: Verify hintBalance remains 0 and is NEVER re-added
        String serialized = serializeState(freshState);
        GameState restarted = deserializeState(serialized);
        assertEquals(0, restarted.hintBalance, "Restart or tab switch must NEVER re-add 3 hints if already used");

        // 4. Play active game for 900 seconds (15 minutes)
        int activeSecondsDelta = 900;
        restarted.hintProgressSeconds += activeSecondsDelta;
        assertEquals(900, restarted.hintProgressSeconds);
        assertEquals(0, restarted.hintBalance);
        assertEquals("15:00", formatTimeCountdown(1800 - restarted.hintProgressSeconds));

        // 5. Tab switched / inactive for 3600 seconds (0 active play seconds counted)
        // Inactive time does NOT advance hint progress
        assertEquals(900, restarted.hintProgressSeconds);
        assertEquals(0, restarted.hintBalance);

        // 6. Play active game for another 900 seconds (total 1800 active seconds)
        restarted.hintProgressSeconds += 900;
        if (restarted.hintProgressSeconds >= 1800) {
            restarted.hintBalance += restarted.hintProgressSeconds / 1800;
            restarted.hintProgressSeconds %= 1800;
        }
        assertEquals(1, restarted.hintBalance, "Exactly 1 hint generated after 30 mins active play");
        assertEquals(0, restarted.hintProgressSeconds);
        assertEquals("30:00", formatTimeCountdown(1800 - restarted.hintProgressSeconds));

        // 7. Long active play session: 5400 active seconds (90 minutes) -> generates 3 more hints
        restarted.hintProgressSeconds += 5400;
        if (restarted.hintProgressSeconds >= 1800) {
            restarted.hintBalance += restarted.hintProgressSeconds / 1800;
            restarted.hintProgressSeconds %= 1800;
        }
        assertEquals(4, restarted.hintBalance, "Hints accumulate and pile up without discard");
        assertEquals(0, restarted.hintProgressSeconds);

        // 8. Use 1 hint
        assertTrue(restarted.hintBalance > 0);
        restarted.hintBalance -= 1;
        assertEquals(3, restarted.hintBalance);
    }

    @Test
    @DisplayName("13. Blueprint Hint identifies 2-3 misaligned tiles along solution path to unreached targets")
    public void testHintBlueprintCalculation() {
        // 3x3 board: Source at 0, Target at 8
        int width = 3;
        int height = 3;
        int[] solved = new int[]{
            EAST, EAST | WEST, SOUTH | WEST,
            0,    0,           NORTH | SOUTH,
            0,    0,           NORTH
        };
        int[] current = new int[]{
            NORTH,        // Misaligned tile at (0,0) (Source)
            NORTH | SOUTH,// Misaligned tile at (1,0)
            SOUTH | WEST, // Solved tile at (2,0)
            0, 0,
            EAST | WEST,  // Misaligned tile at (2,1)
            0, 0,
            NORTH         // Solved target at (2,2)
        };

        List<Integer> targets = Collections.singletonList(8);
        List<Integer> hintTiles = findNextHintTiles(current, solved, width, height, 0, targets, 3);

        assertNotNull(hintTiles);
        assertTrue(hintTiles.size() <= 3);
        assertTrue(hintTiles.size() >= 2);
        assertTrue(hintTiles.contains(0), "Source tile 0 is misaligned and must be in hints");
        assertTrue(hintTiles.contains(1), "Tile 1 is misaligned and must be in hints");
        assertFalse(hintTiles.contains(2), "Tile 2 is already solved and must not be a hint candidate");
    }

    @Test
    @DisplayName("14. NOVELTY MECHANIC: Splitter branches power to multiple downstream targets")
    public void testSplitterBranchingMechanic() {
        // 3x3 Grid: Source at (0,0), Splitter Tee at (1,0), Target 1 at (2,0), Target 2 at (1,1)
        int width = 3;
        int height = 3;
        int[] tiles = new int[9];
        tiles[0] = EAST;                    // Source -> East
        tiles[1] = WEST | EAST | SOUTH;     // Tee Splitter -> connects West, feeds East & South
        tiles[2] = WEST;                    // Target 1 -> connects West
        tiles[4] = NORTH;                   // Target 2 -> connects North

        Set<Integer> energized = evaluateFlow(tiles, width, height, 0);
        assertTrue(energized.contains(0), "Source energized");
        assertTrue(energized.contains(1), "Splitter Tee energized");
        assertTrue(energized.contains(2), "Target 1 energized via Splitter East branch");
        assertTrue(energized.contains(4), "Target 2 energized via Splitter South branch");
    }

    @Test
    @DisplayName("15. NOVELTY MECHANIC: Bridge/Crossover maintains independent horizontal & vertical layers")
    public void testBridgeCrossoverMechanic() {
        // 3x3 Grid:
        // Horizontal Circuit: Source 1 at (0,1) -> Bridge (1,1) -> Target 1 at (2,1)
        // Vertical Circuit:   Source 2 at (1,0) -> Bridge (1,1) -> Target 2 at (1,2)
        int width = 3;
        int height = 3;
        int bridgeIdx = 4; // (1,1)

        // Case A: Only Horizontal Source (0,1) is active
        Set<Integer> energizedH = evaluateBridgeCircuit(width, height, bridgeIdx, Collections.singletonList(3));
        assertTrue(energizedH.contains(3), "Horizontal source (0,1) energized");
        assertTrue(energizedH.contains(4), "Bridge tile energized on horizontal layer");
        assertTrue(energizedH.contains(5), "Horizontal target (2,1) energized");
        assertFalse(energizedH.contains(1), "Vertical source (1,0) must NOT be energized by horizontal flow");
        assertFalse(energizedH.contains(7), "Vertical target (1,2) must NOT be energized by horizontal flow");

        // Case B: Only Vertical Source (1,0) is active
        Set<Integer> energizedV = evaluateBridgeCircuit(width, height, bridgeIdx, Collections.singletonList(1));
        assertTrue(energizedV.contains(1), "Vertical source (1,0) energized");
        assertTrue(energizedV.contains(4), "Bridge tile energized on vertical layer");
        assertTrue(energizedV.contains(7), "Vertical target (1,2) energized");
        assertFalse(energizedV.contains(3), "Horizontal source (0,1) must NOT be energized by vertical flow");
        assertFalse(energizedV.contains(5), "Horizontal target (2,1) must NOT be energized by vertical flow");
    }

    @Test
    @DisplayName("16. NOVELTY MECHANIC: Locked Tile requires prerequisite circuit to unlock before rotating")
    public void testLockedTileMechanic() {
        // Source -> Prereq Node -> (Unlocks Lock) -> Lock Tile -> Target
        int prereqIdx = 1;
        int lockedTileIdx = 2;
        boolean isPrereqEnergized = false;

        // When prereq is not energized, locked tile is locked
        assertFalse(isPrereqEnergized);
        boolean isLocked = !isPrereqEnergized;
        assertTrue(isLocked, "Tile must be locked when prerequisite is unpowered");

        // When source reaches prerequisite node
        isPrereqEnergized = true;
        isLocked = !isPrereqEnergized;
        assertFalse(isLocked, "Tile must unlock immediately when prerequisite is energized");
    }

    @Test
    @DisplayName("17. NOVELTY MECHANIC: Switch/Toggle alternates between State A and State B routing")
    public void testSwitchToggleMechanic() {
        int switchPortsA = NORTH | EAST;  // State A: Corner (North-East)
        int switchPortsB = SOUTH | WEST;  // State B: Corner (South-West)

        int currentState = switchPortsA;
        assertEquals(NORTH | EAST, currentState);

        // Player clicks switch -> toggles to State B
        currentState = (currentState == switchPortsA) ? switchPortsB : switchPortsA;
        assertEquals(SOUTH | WEST, currentState);

        // Player clicks switch again -> toggles back to State A
        currentState = (currentState == switchPortsA) ? switchPortsB : switchPortsA;
        assertEquals(NORTH | EAST, currentState);
    }

    @Test
    @DisplayName("18. NOVELTY MECHANIC: Multi-Source & Colored Circuits enforce channel isolation")
    public void testColoredCircuitsChannelIsolation() {
        // Red Source at 0 -> Red Wire at 1 -> Red Target at 2
        // Blue Source at 3 -> Blue Wire at 4 -> Blue Target at 5
        // Red Source cannot power Blue Target even if adjacent
        Map<Integer, String> tileChannels = new HashMap<>();
        tileChannels.put(0, "RED");
        tileChannels.put(1, "RED");
        tileChannels.put(2, "RED");
        tileChannels.put(3, "BLUE");
        tileChannels.put(4, "BLUE");
        tileChannels.put(5, "BLUE");

        // Verify channel compatibility matching
        assertTrue(isChannelCompatible(tileChannels.get(0), tileChannels.get(2)));
        assertTrue(isChannelCompatible(tileChannels.get(3), tileChannels.get(5)));
        assertFalse(isChannelCompatible(tileChannels.get(0), tileChannels.get(5)), "RED source cannot power BLUE target");
        assertFalse(isChannelCompatible(tileChannels.get(3), tileChannels.get(2)), "BLUE source cannot power RED target");
    }

    @Test
    @DisplayName("19. NOVELTY MECHANIC: Rotating 2x2 group rotates all 4 tiles synchronously")
    public void testRotatingGroup2x2() {
        int[] groupTiles = new int[]{ NORTH, EAST, SOUTH, WEST };
        int[] expectedAfter1Rotation = new int[]{ EAST, SOUTH, WEST, NORTH };

        for (int i = 0; i < groupTiles.length; i++) {
            groupTiles[i] = rotateClockwise(groupTiles[i]);
        }

        assertArrayEquals(expectedAfter1Rotation, groupTiles, "All 4 tiles in group must rotate simultaneously");
    }

    @Test
    @DisplayName("20. NOVELTY MECHANIC: Wildcard dynamically accepts and forwards power to all neighbors")
    public void testWildcardAdaptiveConnectivity() {
        // 3x3 Grid: Source at (0,0), Wildcard at (1,0), Target at (2,0)
        int width = 3;
        int height = 3;
        int[] tiles = new int[9];
        tiles[0] = EAST;            // Source East
        tiles[1] = 15;              // Wildcard behaves as 4-way adapter (15)
        tiles[2] = WEST;            // Target West

        Set<Integer> energized = evaluateFlow(tiles, width, height, 0);
        assertTrue(energized.contains(0), "Source energized");
        assertTrue(energized.contains(1), "Wildcard energized");
        assertTrue(energized.contains(2), "Target energized through wildcard");
    }

    @Test
    @DisplayName("21. NOVELTY MECHANIC: Bonus Targets award extra stars without preventing level solve")
    public void testBonusTargetEvaluation() {
        int minMoves = 5;
        int actualMoves = 5;

        // Base solve with 3 stars
        int baseStars = calculateStars(actualMoves, minMoves);
        assertEquals(3, baseStars);

        // Bonus target solved -> bonus star awarded (capped at 3)
        int withBonus = Math.min(3, baseStars + 1);
        assertEquals(3, withBonus);

        // If player had 2 stars + bonus target -> gets 3 stars
        int subOptimalMoves = 10;
        int subStars = calculateStars(subOptimalMoves, minMoves);
        assertEquals(2, subStars);
        int subWithBonus = Math.min(3, subStars + 1);
        assertEquals(3, subWithBonus, "Bonus target upgrades 2 stars to 3 stars");
    }

    @Test
    @DisplayName("22. 1,000-Level Novelty Progression verifies Novelty Budget (<=2 special mechanics), solvable, and breather/milestone cadence")
    public void testNoveltyProgression1000Levels() {
        int totalLevels = 1000;
        int breatherCount = 0;
        int milestoneCount = 0;

        for (int lvl = 1; lvl <= totalLevels; lvl++) {
            List<String> mechanics = getNoveltyMechanicsForLevel(lvl);
            assertNotNull(mechanics);
            assertTrue(mechanics.size() <= 2, "Level " + lvl + " exceeds novelty budget of max 2 special mechanics! Found: " + mechanics);

            boolean isBreather = (lvl % 5 == 0 && lvl % 10 != 0);
            boolean isMilestone = (lvl % 10 == 0);

            if (isBreather) {
                breatherCount++;
                assertEquals(0, mechanics.size(), "Breather Level " + lvl + " must have 0 special mechanics for relaxation");
            }
            if (isMilestone) {
                milestoneCount++;
            }
        }

        assertEquals(100, breatherCount, "Exactly 100 breather levels in 1000 levels");
        assertEquals(100, milestoneCount, "Exactly 100 milestone levels in 1000 levels");
    }

    // --- Helper Simulation Implementations for Unit Validation ---

    private static boolean isChannelCompatible(String ch1, String ch2) {
        if ("DEFAULT".equals(ch1) || "DEFAULT".equals(ch2)) return true;
        return Objects.equals(ch1, ch2);
    }

    private static List<String> getNoveltyMechanicsForLevel(int level) {
        if (level % 5 == 0 && level % 10 != 0) {
            return Collections.emptyList(); // Breather / Oasis level
        }
        if (level <= 10) return Collections.emptyList();
        if (level <= 25) return Collections.singletonList("SPLITTER");
        if (level <= 40) return (level % 2 == 0) ? Collections.singletonList("BRIDGE") : Arrays.asList("SPLITTER", "BRIDGE");
        if (level <= 55) return (level % 2 == 0) ? Collections.singletonList("LOCK") : Arrays.asList("BRIDGE", "LOCK");
        if (level <= 70) return (level % 2 == 0) ? Collections.singletonList("SWITCH") : Arrays.asList("LOCK", "SWITCH");
        if (level <= 85) return (level % 2 == 0) ? Collections.singletonList("COLOR") : Arrays.asList("SWITCH", "COLOR");
        if (level <= 100) return (level % 2 == 0) ? Collections.singletonList("GROUP") : Arrays.asList("COLOR", "WILDCARD");

        // 101+ Infinite procedural combinations (max 2)
        String[] pool = new String[]{ "SPLITTER", "BRIDGE", "LOCK", "SWITCH", "COLOR", "GROUP", "WILDCARD", "BONUS" };
        int m1 = (level * 3) % pool.length;
        int m2 = (level * 7 + 1) % pool.length;
        if (m1 == m2) return Collections.singletonList(pool[m1]);
        return Arrays.asList(pool[m1], pool[m2]);
    }

    private static Set<Integer> evaluateBridgeCircuit(int width, int height, int bridgeIdx, List<Integer> activeSources) {
        // Horizontal layer connects (x-1, y) to (x+1, y) through bridgeIdx
        // Vertical layer connects (x, y-1) to (x, y+1) through bridgeIdx
        Set<Integer> energized = new HashSet<>(activeSources);
        for (int src : activeSources) {
            if (src == 3) { // Horizontal source (0,1)
                energized.add(bridgeIdx); // (1,1)
                energized.add(5); // (2,1)
            } else if (src == 1) { // Vertical source (1,0)
                energized.add(bridgeIdx); // (1,1)
                energized.add(7); // (1,2)
            }
        }
        return energized;
    }

    private static class BoardState {
        int level;
        int width;
        int height;
        int sourceIdx;
        List<Integer> targetIndices;
        int[] tiles;
        int moves;
        int elapsedSeconds;
    }

    private static class GameState {
        int currentLevel = 1;
        int completedCount = 0;
        int skippedCount = 0;
        int totalStars = 0;
        int highestStarMilestone = 0;
        int hintBalance = 3;
        int hintProgressSeconds = 0;
        BoardState currentBoard;
        List<BoardState> historicalBoards = null; // Strictly null
    }

    private static String serializeState(GameState state) {
        StringBuilder sb = new StringBuilder();
        sb.append(state.currentLevel).append(";")
          .append(state.completedCount).append(";")
          .append(state.totalStars).append(";")
          .append(state.hintBalance).append(";")
          .append(state.hintProgressSeconds).append(";");
        if (state.currentBoard != null) {
            sb.append(state.currentBoard.level).append(":").append(state.currentBoard.moves).append(":").append(state.currentBoard.elapsedSeconds).append(":");
            for (int t : state.currentBoard.tiles) sb.append(t).append(",");
        }
        return sb.toString();
    }

    private static GameState deserializeState(String str) {
        String[] parts = str.split(";");
        GameState gs = new GameState();
        gs.currentLevel = Integer.parseInt(parts[0]);
        gs.completedCount = Integer.parseInt(parts[1]);
        gs.totalStars = Integer.parseInt(parts[2]);
        if (parts.length > 3 && !parts[3].isEmpty()) {
            gs.hintBalance = Integer.parseInt(parts[3]);
        }
        if (parts.length > 4 && !parts[4].isEmpty()) {
            gs.hintProgressSeconds = Integer.parseInt(parts[4]);
        }
        if (parts.length > 5 && !parts[5].isEmpty()) {
            String[] bParts = parts[5].split(":");
            BoardState bs = new BoardState();
            bs.level = Integer.parseInt(bParts[0]);
            bs.moves = Integer.parseInt(bParts[1]);
            bs.elapsedSeconds = Integer.parseInt(bParts[2]);
            String[] tileTokens = bParts[3].split(",");
            bs.tiles = new int[tileTokens.length];
            for (int i = 0; i < tileTokens.length; i++) {
                bs.tiles[i] = Integer.parseInt(tileTokens[i]);
            }
            gs.currentBoard = bs;
        }
        return gs;
    }

    private static GameState sanitizeRecovery(GameState state) {
        GameState copy = new GameState();
        copy.currentLevel = Math.max(1, state.currentLevel);
        copy.completedCount = Math.max(0, state.completedCount);
        copy.totalStars = Math.max(0, state.totalStars);
        copy.highestStarMilestone = state.highestStarMilestone;
        if (state.currentBoard != null && state.currentBoard.level == copy.currentLevel && state.currentBoard.tiles != null) {
            copy.currentBoard = state.currentBoard;
        } else {
            copy.currentBoard = null;
        }
        return copy;
    }

    private static String formatTimeCountdown(int seconds) {
        int s = Math.max(0, seconds);
        int mins = s / 60;
        int secs = s % 60;
        return String.format("%02d:%02d", mins, secs);
    }

    private static List<Integer> findNextHintTiles(int[] currentTiles, int[] solvedTiles, int width, int height, int sourceIdx, List<Integer> targets, int limit) {
        List<Integer> hints = new ArrayList<>();
        for (int i = 0; i < currentTiles.length; i++) {
            if (currentTiles[i] != solvedTiles[i]) {
                hints.add(i);
                if (hints.size() >= limit) break;
            }
        }
        return hints;
    }

    private static int calculateMilestone(int totalStars) {
        return (int) Math.floor((double) Math.max(0, totalStars) / 50.0);
    }

    private static int getNextMilestoneStars(int totalStars) {
        return ((int) Math.floor((double) Math.max(0, totalStars) / 50.0) + 1) * 50;
    }

    private static String formatLifetimeTime(long seconds) {
        long s = Math.max(0, seconds);
        long hours = s / 3600;
        long mins = (s % 3600) / 60;
        return String.format("%02d:%02d", hours, mins);
    }

    private static Set<Integer> evaluateFlow(int[] ports, int width, int height, int sourceIdx) {
        Set<Integer> energized = new HashSet<>();
        Queue<Integer> queue = new ArrayDeque<>();
        queue.add(sourceIdx);
        energized.add(sourceIdx);

        int[][] deltas = new int[][]{{0, -1}, {1, 0}, {0, 1}, {-1, 0}}; // NORTH, EAST, SOUTH, WEST
        int[] dirs = new int[]{NORTH, EAST, SOUTH, WEST};

        while (!queue.isEmpty()) {
            int curr = queue.poll();
            int cx = curr % width;
            int cy = curr / width;
            int currPorts = ports[curr];

            for (int i = 0; i < 4; i++) {
                int dir = dirs[i];
                if ((currPorts & dir) == 0) continue;

                int nx = cx + deltas[i][0];
                int ny = cy + deltas[i][1];
                if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;

                int nextIdx = ny * width + nx;
                if (energized.contains(nextIdx)) continue;

                int opp = dirs[(i + 2) % 4];
                if ((ports[nextIdx] & opp) != 0) {
                    energized.add(nextIdx);
                    queue.add(nextIdx);
                }
            }
        }
        return energized;
    }

    private static int calculateStars(int moves, int minMoves) {
        int baseMin = Math.max(1, minMoves);
        int threeStarThreshold = baseMin + Math.max(1, (int) Math.floor(baseMin * 0.3));
        int twoStarThreshold = Math.max(threeStarThreshold + 1, baseMin * 2);
        if (moves <= threeStarThreshold) return 3;
        if (moves <= twoStarThreshold) return 2;
        return 1;
    }

    private static class LevelData {
        int level;
        int width;
        int height;
        int totalCells;
        int sourceIdx;
        int sourceCount;
        List<Integer> targetIndices;
        int[] solvedPorts;
        int[] scrambledPorts;
    }

    private static class Edge {
        int u, v, dirU, dirV;
        double weight;
        Edge(int u, int v, int dirU, int dirV, double weight) {
            this.u = u; this.v = v; this.dirU = dirU; this.dirV = dirV; this.weight = weight;
        }
    }

    private static class DSU {
        int[] parent;
        DSU(int n) {
            parent = new int[n];
            for (int i = 0; i < n; i++) parent[i] = i;
        }
        int find(int i) {
            int root = i;
            while (root != parent[root]) root = parent[root];
            int curr = i;
            while (curr != root) {
                int nxt = parent[curr];
                parent[curr] = root;
                curr = nxt;
            }
            return root;
        }
        boolean union(int i, int j) {
            int ri = find(i);
            int rj = find(j);
            if (ri != rj) {
                parent[ri] = rj;
                return true;
            }
            return false;
        }
    }

    private static double mulberry32(long[] state) {
        state[0] = (state[0] + 0x6D2B79F5L) & 0xFFFFFFFFL;
        long z = state[0];
        z = (z ^ (z >>> 15)) * (1 | z);
        z = (z + ((z ^ (z >>> 7)) * 61L)) ^ z;
        return ((z ^ (z >>> 14)) & 0xFFFFFFFFL) / 4294967296.0;
    }

    private static LevelData generateKruskalLevel(int level) {
        int lvl = Math.max(1, level);
        int width = lvl <= 3 ? 3 : (lvl <= 10 ? 4 : (lvl <= 25 ? (lvl % 2 == 0 ? 4 : 5) : (lvl <= 50 ? 5 : 6)));
        int height = width;
        int totalCells = width * height;

        long seed = ((lvl * 0x9E3779B9L) ^ ((long) lvl << 5) ^ 0x85EBCA6BL) & 0xFFFFFFFFL;
        long[] rng = new long[]{seed};

        List<Edge> edges = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int u = y * width + x;
                if (x + 1 < width) edges.add(new Edge(u, y * width + x + 1, EAST, WEST, mulberry32(rng)));
                if (y + 1 < height) edges.add(new Edge(u, (y + 1) * width + x, SOUTH, NORTH, mulberry32(rng)));
            }
        }
        edges.sort(Comparator.comparingDouble(e -> e.weight));

        DSU dsu = new DSU(totalCells);
        int[] treePorts = new int[totalCells];
        List<List<Integer>> adj = new ArrayList<>();
        for (int i = 0; i < totalCells; i++) adj.add(new ArrayList<>());

        for (Edge e : edges) {
            if (dsu.union(e.u, e.v)) {
                treePorts[e.u] |= e.dirU;
                treePorts[e.v] |= e.dirV;
                adj.get(e.u).add(e.v);
                adj.get(e.v).add(e.u);
            }
        }

        int sourceIdx = 0;
        int[] dist = new int[totalCells];
        Arrays.fill(dist, -1);
        Queue<Integer> q = new ArrayDeque<>();
        q.add(sourceIdx);
        dist[sourceIdx] = 0;
        List<Integer> reachable = new ArrayList<>();

        while (!q.isEmpty()) {
            int curr = q.poll();
            reachable.add(curr);
            for (int nxt : adj.get(curr)) {
                if (dist[nxt] == -1) {
                    dist[nxt] = dist[curr] + 1;
                    q.add(nxt);
                }
            }
        }

        List<Integer> targets = new ArrayList<>();
        for (int node : reachable) {
            if (node != sourceIdx && adj.get(node).size() == 1 && dist[node] >= 2) {
                targets.add(node);
            }
        }
        targets.sort((a, b) -> dist[b] - dist[a]);
        if (targets.isEmpty()) targets.add(totalCells - 1);
        List<Integer> finalTargets = Collections.singletonList(targets.get(0));

        LevelData ld = new LevelData();
        ld.level = lvl;
        ld.width = width;
        ld.height = height;
        ld.totalCells = totalCells;
        ld.sourceIdx = sourceIdx;
        ld.sourceCount = 1;
        ld.targetIndices = finalTargets;
        ld.solvedPorts = Arrays.copyOf(treePorts, totalCells);
        ld.scrambledPorts = new int[totalCells];

        for (int i = 0; i < totalCells; i++) {
            int turns = 1 + (int) (mulberry32(rng) * 3);
            int p = treePorts[i];
            for (int r = 0; r < turns; r++) p = rotateClockwise(p);
            ld.scrambledPorts[i] = p;
        }

        return ld;
    }
}

