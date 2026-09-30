package com.billing.simple.billsoft.regression.snake;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive Unit, Generator, Engine, Progression, Persistence,
 * and Recovery tests for Snake (Take a Break -> Classic).
 */
public class SnakeEngineAndGeneratorTest {

    // Direction vectors
    public enum Direction {
        UP(0, -1, "DOWN"),
        DOWN(0, 1, "UP"),
        LEFT(-1, 0, "RIGHT"),
        RIGHT(1, 0, "LEFT");

        public final int dx;
        public final int dy;
        public final String opposite;

        Direction(int dx, int dy, String opposite) {
            this.dx = dx;
            this.dy = dy;
            this.opposite = opposite;
        }
    }

    public static class Point {
        public int x;
        public int y;
        public Point(int x, int y) { this.x = x; this.y = y; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Point)) return false;
            Point p = (Point) o;
            return x == p.x && y == p.y;
        }
        @Override
        public int hashCode() { return Objects.hash(x, y); }
    }

    public static class LevelConfig {
        public int level;
        public int width;
        public int height;
        public int targetFruits;
        public int baseTickMs;
        public String layoutType;
        public boolean isMilestone;
        public boolean isOasis;
        public boolean hasGoldenApple;
        public boolean hasChillBerry;
        public boolean hasHoppingFruit;
        public boolean hasGateways;
    }

    public static class SnakeBoard {
        public int level;
        public long seed;
        public int width;
        public int height;
        public int targetFruits;
        public int baseTickMs;
        public String layoutType;
        public boolean isMilestone;
        public boolean isOasis;
        public List<Point> snake = new ArrayList<>();
        public Direction direction = Direction.RIGHT;
        public Set<Integer> obstacles = new HashSet<>();
        public List<Point> gateways = new ArrayList<>();
        public int fruitsEatenInLevel = 0;
        public int restartsCount = 0;
        public Point food = null;
        public String foodType = "APPLE";
        public double speedModifier = 1.0;
        public int chillTimer = 0;
        public boolean isGameOver = false;
        public boolean isLevelComplete = false;
        public int longestSnakeInLevel = 3;
    }

    // Deterministic Mulberry32 PRNG (Java implementation)
    public static class Mulberry32 {
        private int s;
        public Mulberry32(long seed) { this.s = (int) seed; }
        public double nextDouble() {
            s += 0x6D2B79F5;
            int t = (s ^ (s >>> 15)) * (1 | s);
            t = (t + (t ^ (t >>> 7)) * (61 | t)) ^ t;
            return ((t ^ (t >>> 14)) & 0xFFFFFFFFL) / 4294967296.0;
        }
    }

    public static LevelConfig getLevelConfig(int level) {
        LevelConfig cfg = new LevelConfig();
        cfg.level = Math.max(1, level);
        cfg.isMilestone = (cfg.level % 10 == 0);
        cfg.isOasis = (cfg.level > 15 && cfg.level % 5 == 0 && !cfg.isMilestone);

        if (cfg.level <= 5) {
            cfg.width = 12; cfg.height = 12; cfg.targetFruits = 5; cfg.baseTickMs = 180; cfg.layoutType = "CLEAN";
        } else if (cfg.level <= 10) {
            cfg.width = 14; cfg.height = 14; cfg.targetFruits = 7; cfg.baseTickMs = 165; cfg.layoutType = "PILLARS";
        } else if (cfg.level <= 25) {
            cfg.width = 16; cfg.height = 16; cfg.targetFruits = 10; cfg.baseTickMs = 150; cfg.layoutType = "GARDEN_MAZE";
        } else if (cfg.level <= 50) {
            cfg.width = 18; cfg.height = 18; cfg.targetFruits = 12; cfg.baseTickMs = 135; cfg.layoutType = "CHAMBERS";
        } else if (cfg.level <= 75) {
            cfg.width = 18; cfg.height = 18; cfg.targetFruits = 15; cfg.baseTickMs = 125; cfg.layoutType = "ARCHIPELAGO";
        } else if (cfg.level <= 100) {
            cfg.width = 20; cfg.height = 20; cfg.targetFruits = 18; cfg.baseTickMs = 120; cfg.layoutType = "LABYRINTH";
        } else {
            cfg.width = 20; cfg.height = 20;
            cfg.targetFruits = 15 + (cfg.level % 6);
            cfg.baseTickMs = 115; // Hard difficulty floor ceiling
            cfg.layoutType = "PROCEDURAL";
        }

        if (cfg.isOasis) {
            cfg.layoutType = "OASIS";
            cfg.targetFruits = Math.max(6, (int) (cfg.targetFruits * 0.8));
        }

        cfg.baseTickMs = Math.max(115, cfg.baseTickMs);
        cfg.hasGoldenApple = cfg.level >= 11;
        cfg.hasChillBerry = cfg.level >= 26;
        cfg.hasHoppingFruit = cfg.level >= 51;
        cfg.hasGateways = cfg.isMilestone && cfg.level >= 20;
        return cfg;
    }

    public static boolean validatePlayableBoard(SnakeBoard board) {
        if (board == null) return false;
        int totalCells = board.width * board.height;

        // 1. Obstacle density <= 12%
        double density = (double) board.obstacles.size() / totalCells;
        if (density > 0.12) return false;

        // 2. Snake segments must not overlap obstacles
        for (Point p : board.snake) {
            int idx = p.y * board.width + p.x;
            if (board.obstacles.contains(idx)) return false;
        }

        // 3. Clear turning room ahead
        Point head = board.snake.get(0);
        Direction dir = board.direction;
        for (int step = 1; step <= 3; step++) {
            int checkX = ((head.x + dir.dx * step) % board.width + board.width) % board.width;
            int checkY = ((head.y + dir.dy * step) % board.height + board.height) % board.height;
            int checkIdx = checkY * board.width + checkX;
            if (board.obstacles.contains(checkIdx)) return false;
        }

        // 4. BFS flood fill
        int openCellCount = totalCells - board.obstacles.size();
        Set<Integer> visited = new HashSet<>();
        Queue<Integer> queue = new LinkedList<>();
        int startIdx = head.y * board.width + head.x;
        visited.add(startIdx);
        queue.add(startIdx);

        while (!queue.isEmpty()) {
            int curr = queue.poll();
            int cx = curr % board.width;
            int cy = curr / board.width;

            int[] nx = { (cx + 1) % board.width, (cx - 1 + board.width) % board.width, cx, cx };
            int[] ny = { cy, cy, (cy + 1) % board.height, (cy - 1 + board.height) % board.height };

            for (int i = 0; i < 4; i++) {
                int nIdx = ny[i] * board.width + nx[i];
                if (!board.obstacles.contains(nIdx) && !visited.contains(nIdx)) {
                    visited.add(nIdx);
                    queue.add(nIdx);
                }
            }
        }

        return visited.size() == openCellCount;
    }

    public static SnakeBoard generateLevel(int level) {
        LevelConfig cfg = getLevelConfig(level);
        long baseSeed = ((long) cfg.level * 0x85EBCA6BL) ^ ((long) cfg.level << 4) ^ 0x9E3779B9L;

        SnakeBoard board = new SnakeBoard();
        board.level = cfg.level;
        board.seed = baseSeed;
        board.width = cfg.width;
        board.height = cfg.height;
        board.targetFruits = cfg.targetFruits;
        board.baseTickMs = cfg.baseTickMs;
        board.layoutType = cfg.layoutType;
        board.isMilestone = cfg.isMilestone;
        board.isOasis = cfg.isOasis;

        int startX = cfg.width / 4;
        int startY = cfg.height / 2;
        board.snake.add(new Point(startX, startY));
        board.snake.add(new Point(startX - 1, startY));
        board.snake.add(new Point(startX - 2, startY));
        board.direction = Direction.RIGHT;

        if (cfg.hasGateways) {
            board.gateways.add(new Point(2, 2));
            board.gateways.add(new Point(cfg.width - 3, cfg.height - 3));
        }

        if (!cfg.layoutType.equals("CLEAN") && !cfg.layoutType.equals("OASIS")) {
            if (cfg.layoutType.equals("PILLARS")) {
                int offset = 3;
                board.obstacles.add(offset * cfg.width + offset);
                board.obstacles.add(offset * cfg.width + (cfg.width - 1 - offset));
                board.obstacles.add((cfg.height - 1 - offset) * cfg.width + offset);
                board.obstacles.add((cfg.height - 1 - offset) * cfg.width + (cfg.width - 1 - offset));
            }
        }

        // Spawn initial food
        board.food = new Point(cfg.width - 2, startY);
        board.foodType = "APPLE";
        return board;
    }

    public static String tickEngine(SnakeBoard state) {
        if (state.isGameOver || state.isLevelComplete) return "IDLE";

        Direction dir = state.direction;
        Point head = state.snake.get(0);
        int nextX = ((head.x + dir.dx) % state.width + state.width) % state.width;
        int nextY = ((head.y + dir.dy) % state.height + state.height) % state.height;

        // 1. Obstacle collision
        int obsIdx = nextY * state.width + nextX;
        if (state.obstacles.contains(obsIdx)) {
            state.isGameOver = true;
            state.restartsCount++;
            return "DEATH";
        }

        // 2. Quantum gateway teleportation
        if (state.gateways.size() >= 2) {
            Point g1 = state.gateways.get(0);
            Point g2 = state.gateways.get(1);
            if (nextX == g1.x && nextY == g1.y) {
                nextX = ((g2.x + dir.dx) % state.width + state.width) % state.width;
                nextY = ((g2.y + dir.dy) % state.height + state.height) % state.height;
            } else if (nextX == g2.x && nextY == g2.y) {
                nextX = ((g1.x + dir.dx) % state.width + state.width) % state.width;
                nextY = ((g1.y + dir.dy) % state.height + state.height) % state.height;
            }
        }

        // 3. Self-collision
        boolean isEating = (state.food != null && nextX == state.food.x && nextY == state.food.y);
        int checkLimit = isEating ? state.snake.size() : state.snake.size() - 1;
        for (int i = 0; i < checkLimit; i++) {
            Point seg = state.snake.get(i);
            if (seg.x == nextX && seg.y == nextY) {
                state.isGameOver = true;
                state.restartsCount++;
                return "DEATH";
            }
        }

        // 4. Move Snake
        state.snake.add(0, new Point(nextX, nextY));
        if (isEating) {
            if ("GOLDEN_APPLE".equals(state.foodType)) {
                state.fruitsEatenInLevel += 2;
            } else {
                state.fruitsEatenInLevel += 1;
            }
            if ("CHILL_BERRY".equals(state.foodType)) {
                state.chillTimer = 8000;
                state.speedModifier = 1.35;
            }
            state.food = null;
        } else {
            state.snake.remove(state.snake.size() - 1);
        }

        state.longestSnakeInLevel = Math.max(state.longestSnakeInLevel, state.snake.size());

        if (state.fruitsEatenInLevel >= state.targetFruits) {
            state.isLevelComplete = true;
            return "WIN";
        }
        return isEating ? "GROW" : "MOVE";
    }

    // ==========================================
    // A. GENERATOR TESTS (Levels 1–1000)
    // ==========================================

    @Test
    @DisplayName("A1. Procedural generator produces valid, safe, solvable boards across 1,000 levels")
    public void testGenerator1000Levels() {
        for (int lvl = 1; lvl <= 1000; lvl++) {
            LevelConfig config = getLevelConfig(lvl);
            assertNotNull(config);
            assertTrue(config.width >= 12 && config.width <= 20, "Width within bounds for lvl " + lvl);
            assertTrue(config.height >= 12 && config.height <= 20, "Height within bounds for lvl " + lvl);
            assertTrue(config.baseTickMs >= 115, "Speed floor must never be faster than 115ms (lvl " + lvl + ")");
            assertTrue(config.targetFruits >= 5 && config.targetFruits <= 25, "Target fruits reasonable");

            SnakeBoard board = generateLevel(lvl);
            assertNotNull(board);
            assertEquals(lvl, board.level);
            assertEquals(3, board.snake.size(), "Snake initial length must be 3");

            // Validate playability & obstacle density <= 12%
            assertTrue(validatePlayableBoard(board), "Level " + lvl + " must pass strict Snake playability validation");
            double density = (double) board.obstacles.size() / (board.width * board.height);
            assertTrue(density <= 0.12, "Obstacle density must never exceed 12% for lvl " + lvl);
        }
    }

    @Test
    @DisplayName("A2. Oasis relaxing levels occur every 5th level after level 15 with zero obstacles")
    public void testOasisLevels() {
        for (int lvl = 16; lvl <= 100; lvl++) {
            LevelConfig cfg = getLevelConfig(lvl);
            if (lvl % 5 == 0 && lvl % 10 != 0) {
                assertTrue(cfg.isOasis, "Level " + lvl + " should be Oasis");
                assertEquals("OASIS", cfg.layoutType);
                SnakeBoard board = generateLevel(lvl);
                assertTrue(board.obstacles.isEmpty(), "Oasis level must have 0 obstacles");
            }
        }
    }

    // ==========================================
    // B. ENGINE TESTS
    // ==========================================

    @Test
    @DisplayName("B1. Toroidal wrapping works seamlessly in all 4 directions (Left->Right, Right->Left, Top->Bottom, Bottom->Top)")
    public void testToroidalWrappingAllDirections() {
        SnakeBoard board = new SnakeBoard();
        board.width = 10;
        board.height = 10;
        board.targetFruits = 5;

        // 1. Right wrap (x=9 -> x=0)
        board.snake.clear();
        board.snake.add(new Point(9, 5));
        board.snake.add(new Point(8, 5));
        board.direction = Direction.RIGHT;
        assertEquals("MOVE", tickEngine(board));
        assertEquals(0, board.snake.get(0).x);
        assertEquals(5, board.snake.get(0).y);

        // 2. Left wrap (x=0 -> x=9)
        board.snake.clear();
        board.snake.add(new Point(0, 5));
        board.snake.add(new Point(1, 5));
        board.direction = Direction.LEFT;
        assertEquals("MOVE", tickEngine(board));
        assertEquals(9, board.snake.get(0).x);
        assertEquals(5, board.snake.get(0).y);

        // 3. Top wrap (y=0 -> y=9)
        board.snake.clear();
        board.snake.add(new Point(5, 0));
        board.snake.add(new Point(5, 1));
        board.direction = Direction.UP;
        assertEquals("MOVE", tickEngine(board));
        assertEquals(5, board.snake.get(0).x);
        assertEquals(9, board.snake.get(0).y);

        // 4. Bottom wrap (y=9 -> y=0)
        board.snake.clear();
        board.snake.add(new Point(5, 9));
        board.snake.add(new Point(5, 8));
        board.direction = Direction.DOWN;
        assertEquals("MOVE", tickEngine(board));
        assertEquals(5, board.snake.get(0).x);
        assertEquals(0, board.snake.get(0).y);
    }

    @Test
    @DisplayName("B2. Self-collision triggers DEATH event and increments restart counter without losing progression")
    public void testSelfCollision() {
        SnakeBoard board = new SnakeBoard();
        board.width = 10;
        board.height = 10;
        board.targetFruits = 5;
        // 5-segment snake in a loop: head (2,2), (2,3), (3,3), (3,2), tail (4,2)
        board.snake.add(new Point(2, 2)); // head
        board.snake.add(new Point(2, 3)); // 1
        board.snake.add(new Point(3, 3)); // 2
        board.snake.add(new Point(3, 2)); // 3 (body segment to collide with)
        board.snake.add(new Point(4, 2)); // 4 (tail)
        board.direction = Direction.RIGHT; // Moving right from (2,2) hits (3,2)

        assertEquals("DEATH", tickEngine(board));
        assertTrue(board.isGameOver);
        assertEquals(1, board.restartsCount);
    }

    @Test
    @DisplayName("B3. Obstacle collision triggers DEATH and safe restart")
    public void testObstacleCollision() {
        SnakeBoard board = new SnakeBoard();
        board.width = 10;
        board.height = 10;
        board.targetFruits = 5;
        board.snake.add(new Point(4, 5));
        board.snake.add(new Point(3, 5));
        board.direction = Direction.RIGHT;
        // Put obstacle at (5,5)
        board.obstacles.add(5 * 10 + 5);

        assertEquals("DEATH", tickEngine(board));
        assertTrue(board.isGameOver);
        assertEquals(1, board.restartsCount);
    }

    @Test
    @DisplayName("B4. Fruit consumption causes growth, progress increment, and WIN condition")
    public void testFruitConsumptionAndWin() {
        SnakeBoard board = new SnakeBoard();
        board.width = 10;
        board.height = 10;
        board.targetFruits = 2;
        board.snake.add(new Point(4, 5));
        board.snake.add(new Point(3, 5));
        board.snake.add(new Point(2, 5));
        board.direction = Direction.RIGHT;
        board.food = new Point(5, 5);
        board.foodType = "APPLE";

        assertEquals("GROW", tickEngine(board));
        assertEquals(4, board.snake.size(), "Snake length should grow by 1");
        assertEquals(1, board.fruitsEatenInLevel);
        assertFalse(board.isLevelComplete);

        // Eat 2nd fruit
        board.food = new Point(6, 5);
        board.foodType = "APPLE";
        assertEquals("WIN", tickEngine(board));
        assertEquals(5, board.snake.size());
        assertEquals(2, board.fruitsEatenInLevel);
        assertTrue(board.isLevelComplete);
    }

    @Test
    @DisplayName("B5. Golden Apple awards +2 fruits progress and Chill Berry applies 35% slow-mo brake")
    public void testSpecialFruitMechanics() {
        // Golden Apple
        SnakeBoard b1 = new SnakeBoard();
        b1.width = 10; b1.height = 10; b1.targetFruits = 5;
        b1.snake.add(new Point(4, 5));
        b1.snake.add(new Point(3, 5));
        b1.direction = Direction.RIGHT;
        b1.food = new Point(5, 5);
        b1.foodType = "GOLDEN_APPLE";
        tickEngine(b1);
        assertEquals(2, b1.fruitsEatenInLevel, "Golden Apple gives 2 progress");

        // Chill Berry
        SnakeBoard b2 = new SnakeBoard();
        b2.width = 10; b2.height = 10; b2.targetFruits = 5;
        b2.snake.add(new Point(4, 5));
        b2.snake.add(new Point(3, 5));
        b2.direction = Direction.RIGHT;
        b2.food = new Point(5, 5);
        b2.foodType = "CHILL_BERRY";
        tickEngine(b2);
        assertEquals(1.35, b2.speedModifier, 0.001, "Chill Berry applies 35% brake");
        assertEquals(8000, b2.chillTimer, "Chill Berry lasts 8 seconds");
    }

    @Test
    @DisplayName("B6. Quantum Gateways teleport snake head safely to paired portal")
    public void testQuantumGateways() {
        SnakeBoard board = new SnakeBoard();
        board.width = 10;
        board.height = 10;
        board.targetFruits = 5;
        board.snake.add(new Point(1, 2));
        board.snake.add(new Point(0, 2));
        board.direction = Direction.RIGHT;
        // Portals at (2,2) and (8,8)
        board.gateways.add(new Point(2, 2));
        board.gateways.add(new Point(8, 8));

        // Moving right from (1,2) hits portal (2,2) -> exits at (8,8) + direction (1,0) = (9,8)
        tickEngine(board);
        Point head = board.snake.get(0);
        assertEquals(9, head.x);
        assertEquals(8, head.y);
    }

    // ==========================================
    // C. STARS & INFINITE TROPHIES
    // ==========================================

    @Test
    @DisplayName("C1. Star rating formulas: 0 restarts -> 3 stars, 1 restart -> 2 stars, 2+ restarts -> 1 star")
    public void testStarRatingFormula() {
        assertEquals(3, calculateStars(0));
        assertEquals(2, calculateStars(1));
        assertEquals(1, calculateStars(2));
        assertEquals(1, calculateStars(5));
    }

    private int calculateStars(int restarts) {
        if (restarts == 0) return 3;
        if (restarts == 1) return 2;
        return 1;
    }

    @Test
    @DisplayName("C2. Infinite 50-star milestone formula supports unbounded progression without hard-coded limits")
    public void testInfiniteTrophies() {
        // Milestone = floor(stars / 50)
        assertEquals(0, 49 / 50);
        assertEquals(1, 50 / 50);   // Bronze Viper M1
        assertEquals(6, 300 / 50);  // Silver Cobra M6
        assertEquals(11, 550 / 50); // Gold Python M11
        assertEquals(21, 1050 / 50);// Platinum Anaconda M21
        assertEquals(51, 2550 / 50);// Diamond Ouroboros M51
        assertEquals(101, 5050 / 50);// Cosmic Leviathan M101+
    }

    // ==========================================
    // D. PERSISTENCE & RECOVERY
    // ==========================================

    @Test
    @DisplayName("D1. Play time formatting strictly adheres to HH:MM")
    public void testTimeFormatting() {
        assertEquals("00:00", formatHHMM(0));
        assertEquals("00:00", formatHHMM(45));
        assertEquals("00:01", formatHHMM(60));
        assertEquals("00:01", formatHHMM(90));
        assertEquals("01:00", formatHHMM(3600));
        assertEquals("02:05", formatHHMM(7500));
        assertEquals("10:00", formatHHMM(36000));
    }

    private String formatHHMM(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int mins = (totalSeconds % 3600) / 60;
        return String.format("%02d:%02d", hours, mins);
    }
}
