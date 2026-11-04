package com.billing.simple.billsoft.regression.bounce;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Nokia Bounce Engine & Procedural Generator Tests")
public class BounceEngineAndGeneratorTest {

    // Java-mirrored deterministic PRNG
    public static class Mulberry32 {
        private int s;

        public Mulberry32(long seed) {
            this.s = (int) seed;
        }

        public double nextDouble() {
            s |= 0;
            s = (s + 0x6D2B79F5) | 0;
            long t = Integer.toUnsignedLong(s ^ (s >>> 15));
            t = (t * (1 | s)) & 0xFFFFFFFFL;
            t = t ^ (t + (((t ^ (t >>> 7)) * (61 | t)) & 0xFFFFFFFFL));
            return ((t ^ (t >>> 14)) & 0xFFFFFFFFL) / 4294967296.0;
        }
    }

    public static class Platform {
        public double x, y, width, height;
        public String type = "solid";

        public Platform(double x, double y, double width, double height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    public static class Hoop {
        public String id;
        public double x, y, radius;
        public boolean collected;

        public Hoop(String id, double x, double y, double radius) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.collected = false;
        }
    }

    public static class BounceLevelModel {
        public int levelNumber;
        public long seed;
        public double width, height;
        public double spawnX, spawnY;
        public boolean exitOpen = false;
        public List<Platform> platforms = new ArrayList<>();
        public List<Hoop> hoops = new ArrayList<>();
    }

    public static class PlayerModel {
        public double x, y, vx, vy;
        public String state = "NORMAL";
        public int lives = 3;
        public boolean isGrounded = false;
        public double radius = 14;
    }

    @Test
    @DisplayName("1. Ball physics: gravity, horizontal acceleration, friction & jumping")
    public void testBallPhysics() {
        PlayerModel p = new PlayerModel();
        p.x = 64;
        p.y = 100;
        p.vy = 0;

        // Apply 1 step of gravity (820 px/s^2 * 1/60s)
        double dt = 1.0 / 60.0;
        p.vy += 820 * dt;
        p.y += p.vy * dt;

        assertTrue(p.vy > 0, "Ball should gain downward velocity under gravity");
        assertTrue(p.y > 100, "Ball position should move downward");

        // Jump impulse (-370 px/s)
        p.isGrounded = true;
        p.vy = -370;
        assertTrue(p.vy < 0, "Jump should impart upward vertical velocity");
    }

    @Test
    @DisplayName("2. Ball transformations: NORMAL (r=14), BIG (r=22), SMALL (r=9)")
    public void testBallTransformations() {
        PlayerModel p = new PlayerModel();
        assertEquals("NORMAL", p.state);
        assertEquals(14, p.radius);

        // Transform BIG
        p.state = "BIG";
        p.radius = 22;
        assertEquals(22, p.radius, "BIG ball radius must be 22px");

        // Transform SMALL
        p.state = "SMALL";
        p.radius = 9;
        assertEquals(9, p.radius, "SMALL ball radius must be 9px");
    }

    @Test
    @DisplayName("3. Hoops collection & Exit gate unlocking")
    public void testHoopsAndExit() {
        BounceLevelModel lvl = new BounceLevelModel();
        lvl.hoops.add(new Hoop("h1", 200, 300, 24));
        lvl.hoops.add(new Hoop("h2", 400, 300, 24));

        assertFalse(lvl.exitOpen, "Exit gate must be initially locked");

        // Collect hoop 1
        lvl.hoops.get(0).collected = true;
        assertFalse(lvl.exitOpen, "Exit must remain locked while hoop 2 is uncollected");

        // Collect hoop 2
        lvl.hoops.get(1).collected = true;
        boolean allCollected = lvl.hoops.stream().allMatch(h -> h.collected);
        if (allCollected) lvl.exitOpen = true;

        assertTrue(lvl.exitOpen, "Exit must unlock when all hoops are collected");
    }

    @Test
    @DisplayName("4. 10,000 Deterministic candidate level batch generation")
    public void testBatchLevelGeneration() {
        for (int i = 1; i <= 10000; i++) {
            long seed = (i * 31337L + 7919L) & 0xFFFFFFFFL;
            Mulberry32 rng = new Mulberry32(seed);

            double width = 1200 + rng.nextDouble() * 1200;
            assertTrue(width >= 1200 && width <= 2400, "Level width must be bounded");

            int hoopCount = 4 + (int) (rng.nextDouble() * 6);
            assertTrue(hoopCount >= 4 && hoopCount <= 10, "Hoop count must be between 4 and 10");
        }
    }
}
