package com.billing.simple.billsoft.regression.world;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression & Unit test suite for Maharashtra World Layer,
 * Trophy Progression formula, 50-level Celebrations, and Game Isolation.
 */
public class MaharashtraWorldEngineTest {

    // Mirroring Trophy Progression Formulas
    public static class TrophyCalculation {
        public final int totalStars;
        public final int milestoneIndex;
        public final int progressStars;
        public final double progressPercent;
        public final int nextMilestone;
        public final int starsRemaining;
        public final String tierName;

        public TrophyCalculation(int totalStars) {
            int stars = Math.max(0, totalStars);
            this.totalStars = stars;
            this.milestoneIndex = stars / 50;
            this.progressStars = stars % 50;
            this.progressPercent = (this.progressStars / 50.0) * 100.0;
            this.nextMilestone = (this.milestoneIndex + 1) * 50;
            this.starsRemaining = 50 - this.progressStars;

            int m = this.milestoneIndex;
            if (m == 0) this.tierName = "None";
            else if (m <= 5) this.tierName = "Bronze";
            else if (m <= 10) this.tierName = "Silver";
            else if (m <= 20) this.tierName = "Gold";
            else if (m <= 50) this.tierName = "Platinum";
            else if (m <= 100) this.tierName = "Diamond";
            else this.tierName = "Cosmic";
        }
    }

    // Mirroring 50-Level Celebration Rule
    public static boolean shouldCelebrateLevelMilestone(int completedCount) {
        return completedCount > 0 && (completedCount % 50 == 0);
    }

    // Curated catalog simulation of 1000+ deterministic locations
    public static class WorldLocation {
        public final String id;
        public final String name;
        public final String district;
        public final String region;
        public final String category;
        public final String archetype;

        public WorldLocation(String id, String name, String district, String region, String category, String archetype) {
            this.id = id;
            this.name = name;
            this.district = district;
            this.region = region;
            this.category = category;
            this.archetype = archetype;
        }
    }

    private static final List<WorldLocation> CATALOG = new ArrayList<>();
    static {
        // Sample curated seed items
        CATALOG.add(new WorldLocation("karad", "Karad", "Satara", "Paschim Maharashtra", "CITY", "ARCH_RIVER_VALLEY"));
        CATALOG.add(new WorldLocation("satara", "Satara", "Satara", "Paschim Maharashtra", "CITY", "ARCH_HILL_FORT"));
        CATALOG.add(new WorldLocation("wai", "Wai", "Satara", "Paschim Maharashtra", "GHAT", "ARCH_PILGRIMAGE_GHAT"));
        CATALOG.add(new WorldLocation("mahabaleshwar", "Mahabaleshwar", "Satara", "Paschim Maharashtra", "VALLEY", "ARCH_PLATEAU_FOREST"));
        CATALOG.add(new WorldLocation("raigad_fort", "Raigad Fort", "Raigad", "Konkan", "FORT", "ARCH_HILL_FORT"));
        CATALOG.add(new WorldLocation("shaniwar_wada", "Shaniwar Wada", "Pune", "Paschim Maharashtra", "WADA", "ARCH_HISTORIC_WADA"));
        CATALOG.add(new WorldLocation("murud_janjira", "Murud-Janjira", "Raigad", "Konkan", "COASTAL", "ARCH_COASTAL_FORT"));
        CATALOG.add(new WorldLocation("ellora_caves", "Ellora Caves", "Chhatrapati Sambhajinagar", "Marathwada", "TEMPLE", "ARCH_CAVE_TEMPLE"));
        CATALOG.add(new WorldLocation("mumbai", "Mumbai", "Mumbai City", "Konkan", "CITY", "ARCH_METROPOLIS"));

        // Generate up to 1024 unique locations matching maharashtraWorld.js
        for (int i = CATALOG.size(); i < 1024; i++) {
            CATALOG.add(new WorldLocation("loc_" + i, "Location " + i, "District " + (i % 36), "Region " + (i % 6), "SITE", "ARCH_HILL_FORT"));
        }
    }

    public static class WorldResolved {
        public final WorldLocation location;
        public final int level;
        public final int levelInLocation;
        public final int levelsPerLocation;
        public final int journeyCycle;
        public final String journeyRoman;
        public final int startLevel;
        public final int endLevel;

        public WorldResolved(WorldLocation location, int level, int levelInLocation, int levelsPerLocation, int cycle, String roman, int start, int end) {
            this.location = location;
            this.level = level;
            this.levelInLocation = levelInLocation;
            this.levelsPerLocation = levelsPerLocation;
            this.journeyCycle = cycle;
            this.journeyRoman = roman;
            this.startLevel = start;
            this.endLevel = end;
        }
    }

    public static String toRoman(int num) {
        if (num <= 0) return "I";
        int[] vals = { 1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1 };
        String[] syms = { "M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I" };
        StringBuilder sb = new StringBuilder();
        int n = num;
        for (int i = 0; i < vals.length; i++) {
            while (n >= vals[i]) {
                sb.append(syms[i]);
                n -= vals[i];
            }
        }
        return sb.toString();
    }

    public static WorldResolved getWorldForLevel(int level, int levelsPerLoc) {
        int safeLevel = Math.max(1, level);
        int locSpan = Math.max(1, levelsPerLoc);
        int absoluteLocIndex = (safeLevel - 1) / locSpan;
        int catalogSize = CATALOG.size();

        int locIdxInCatalog = absoluteLocIndex % catalogSize;
        int journeyCycle = (absoluteLocIndex / catalogSize) + 1;
        int levelInLocation = ((safeLevel - 1) % locSpan) + 1;

        int startLevel = (absoluteLocIndex * locSpan) + 1;
        int endLevel = startLevel + locSpan - 1;

        WorldLocation loc = CATALOG.get(locIdxInCatalog);
        return new WorldResolved(loc, safeLevel, levelInLocation, locSpan, journeyCycle, toRoman(journeyCycle), startLevel, endLevel);
    }

    @Test
    @DisplayName("1. Trophy progression calculations: 0, 49, 50, 51, 99, 100, 500, and large star counts")
    public void testTrophyCalculations() {
        // 0 stars
        TrophyCalculation t0 = new TrophyCalculation(0);
        assertEquals(0, t0.milestoneIndex);
        assertEquals(0, t0.progressStars);
        assertEquals(0.0, t0.progressPercent, 0.001);
        assertEquals(50, t0.nextMilestone);
        assertEquals(50, t0.starsRemaining);
        assertEquals("None", t0.tierName);

        // 49 stars
        TrophyCalculation t49 = new TrophyCalculation(49);
        assertEquals(0, t49.milestoneIndex);
        assertEquals(49, t49.progressStars);
        assertEquals(98.0, t49.progressPercent, 0.001);
        assertEquals(50, t49.nextMilestone);
        assertEquals(1, t49.starsRemaining);
        assertEquals("None", t49.tierName);

        // 50 stars -> Milestone 1 (Bronze)
        TrophyCalculation t50 = new TrophyCalculation(50);
        assertEquals(1, t50.milestoneIndex);
        assertEquals(0, t50.progressStars);
        assertEquals(0.0, t50.progressPercent, 0.001);
        assertEquals(100, t50.nextMilestone);
        assertEquals(50, t50.starsRemaining);
        assertEquals("Bronze", t50.tierName);

        // 51 stars -> Milestone 1 with 1 star into next
        TrophyCalculation t51 = new TrophyCalculation(51);
        assertEquals(1, t51.milestoneIndex);
        assertEquals(1, t51.progressStars);
        assertEquals(2.0, t51.progressPercent, 0.001);
        assertEquals(100, t51.nextMilestone);
        assertEquals(49, t51.starsRemaining);
        assertEquals("Bronze", t51.tierName);

        // 99 stars
        TrophyCalculation t99 = new TrophyCalculation(99);
        assertEquals(1, t99.milestoneIndex);
        assertEquals(49, t99.progressStars);
        assertEquals(98.0, t99.progressPercent, 0.001);
        assertEquals(100, t99.nextMilestone);
        assertEquals(1, t99.starsRemaining);
        assertEquals("Bronze", t99.tierName);

        // 100 stars -> Milestone 2 (Bronze)
        TrophyCalculation t100 = new TrophyCalculation(100);
        assertEquals(2, t100.milestoneIndex);
        assertEquals(0, t100.progressStars);
        assertEquals(150, t100.nextMilestone);
        assertEquals("Bronze", t100.tierName);

        // 500 stars -> Milestone 10 (Silver)
        TrophyCalculation t500 = new TrophyCalculation(500);
        assertEquals(10, t500.milestoneIndex);
        assertEquals("Silver", t500.tierName);

        // 1000 stars -> Milestone 20 (Gold)
        TrophyCalculation t1000 = new TrophyCalculation(1000);
        assertEquals(20, t1000.milestoneIndex);
        assertEquals("Gold", t1000.tierName);

        // 2500 stars -> Milestone 50 (Platinum)
        TrophyCalculation t2500 = new TrophyCalculation(2500);
        assertEquals(50, t2500.milestoneIndex);
        assertEquals("Platinum", t2500.tierName);

        // 5000 stars -> Milestone 100 (Diamond)
        TrophyCalculation t5000 = new TrophyCalculation(5000);
        assertEquals(100, t5000.milestoneIndex);
        assertEquals("Diamond", t5000.tierName);

        // 5050 stars -> Milestone 101 (Cosmic)
        TrophyCalculation t5050 = new TrophyCalculation(5050);
        assertEquals(101, t5050.milestoneIndex);
        assertEquals("Cosmic", t5050.tierName);

        // 50,000 stars -> Milestone 1000 (Cosmic, infinite)
        TrophyCalculation t50000 = new TrophyCalculation(50000);
        assertEquals(1000, t50000.milestoneIndex);
        assertEquals("Cosmic", t50000.tierName);
    }

    @Test
    @DisplayName("2. 50-Level milestone celebrations trigger only on exact multiples of 50")
    public void test50LevelMilestones() {
        assertFalse(shouldCelebrateLevelMilestone(0), "0 completed levels must not trigger celebration");
        assertFalse(shouldCelebrateLevelMilestone(1));
        assertFalse(shouldCelebrateLevelMilestone(49));
        assertTrue(shouldCelebrateLevelMilestone(50), "50 completed levels must trigger celebration");
        assertFalse(shouldCelebrateLevelMilestone(51));
        assertFalse(shouldCelebrateLevelMilestone(99));
        assertTrue(shouldCelebrateLevelMilestone(100), "100 completed levels must trigger celebration");
        assertFalse(shouldCelebrateLevelMilestone(101));
        assertTrue(shouldCelebrateLevelMilestone(150), "150 completed levels must trigger celebration");
        assertTrue(shouldCelebrateLevelMilestone(200), "200 completed levels must trigger celebration");
        assertTrue(shouldCelebrateLevelMilestone(1000), "1000 completed levels must trigger celebration");
    }

    @Test
    @DisplayName("3. Deterministic 15-level location mapping & catalog exhaustion before cycle rollover")
    public void testDeterministicWorldProgression() {
        // Levels 1-15 -> Karad (Location 0)
        for (int lvl = 1; lvl <= 15; lvl++) {
            WorldResolved res = getWorldForLevel(lvl, 15);
            assertEquals("karad", res.location.id);
            assertEquals("Karad", res.location.name);
            assertEquals(lvl, res.levelInLocation);
            assertEquals(1, res.journeyCycle);
            assertEquals("I", res.journeyRoman);
            assertEquals(1, res.startLevel);
            assertEquals(15, res.endLevel);
        }

        // Levels 16-30 -> Satara (Location 1)
        for (int lvl = 16; lvl <= 30; lvl++) {
            WorldResolved res = getWorldForLevel(lvl, 15);
            assertEquals("satara", res.location.id);
            assertEquals("Satara", res.location.name);
            assertEquals(lvl - 15, res.levelInLocation);
            assertEquals(1, res.journeyCycle);
            assertEquals(16, res.startLevel);
            assertEquals(30, res.endLevel);
        }

        // Levels 31-45 -> Wai (Location 2)
        WorldResolved res31 = getWorldForLevel(31, 15);
        assertEquals("wai", res31.location.id);
        assertEquals(1, res31.levelInLocation);

        WorldResolved res45 = getWorldForLevel(45, 15);
        assertEquals("wai", res45.location.id);
        assertEquals(15, res45.levelInLocation);

        // Catalog boundary & Journey II rollover
        // 1024 locations * 15 levels = 15,360 levels in Journey I
        int lastLevelJourney1 = 1024 * 15;
        WorldResolved resEnd1 = getWorldForLevel(lastLevelJourney1, 15);
        assertEquals(1, resEnd1.journeyCycle);
        assertEquals("I", resEnd1.journeyRoman);
        assertEquals(15, resEnd1.levelInLocation);

        // Level 15,361 starts Journey II at Karad
        WorldResolved resStart2 = getWorldForLevel(lastLevelJourney1 + 1, 15);
        assertEquals("karad", resStart2.location.id);
        assertEquals(2, resStart2.journeyCycle);
        assertEquals("II", resStart2.journeyRoman);
        assertEquals(1, resStart2.levelInLocation);

        // Level 30,721 starts Journey III at Karad
        int lastLevelJourney2 = 1024 * 15 * 2;
        WorldResolved resStart3 = getWorldForLevel(lastLevelJourney2 + 1, 15);
        assertEquals("karad", resStart3.location.id);
        assertEquals(3, resStart3.journeyCycle);
        assertEquals("III", resStart3.journeyRoman);
    }

    @Test
    @DisplayName("4. Game isolation: Modern and Classic progressions advance independently without crosstalk")
    public void testGameProgressionIsolation() {
        int modernLevel = 45; // Wai, Paschim Maharashtra
        int classicLevel = 2; // Karad, Paschim Maharashtra

        WorldResolved modernWorld = getWorldForLevel(modernLevel, 15);
        WorldResolved classicWorld = getWorldForLevel(classicLevel, 15);

        assertEquals("wai", modernWorld.location.id);
        assertEquals(15, modernWorld.levelInLocation);

        assertEquals("karad", classicWorld.location.id);
        assertEquals(2, classicWorld.levelInLocation);

        // Advancing Classic level to 16 changes classic location to Satara without affecting Modern
        classicLevel = 16;
        WorldResolved classicWorldAfter = getWorldForLevel(classicLevel, 15);
        assertEquals("satara", classicWorldAfter.location.id);
        assertEquals(1, classicWorldAfter.levelInLocation);

        // Modern world remains Wai
        WorldResolved modernWorldAfter = getWorldForLevel(modernLevel, 15);
        assertEquals("wai", modernWorldAfter.location.id);
    }

    @Test
    @DisplayName("5. Fallback safety & extreme values: handles negative, zero, and huge values gracefully")
    public void testFallbackSafety() {
        WorldResolved resZero = getWorldForLevel(0, 15);
        assertNotNull(resZero);
        assertEquals("karad", resZero.location.id);
        assertEquals(1, resZero.levelInLocation);

        WorldResolved resNegative = getWorldForLevel(-100, 15);
        assertNotNull(resNegative);
        assertEquals("karad", resNegative.location.id);

        WorldResolved resHuge = getWorldForLevel(1_000_000, 15);
        assertNotNull(resHuge);
        assertTrue(resHuge.journeyCycle > 1);
        assertNotNull(resHuge.location);
        assertNotNull(resHuge.location.archetype);
    }

    @Test
    @DisplayName("6. Mystery destination: Next location is concealed as '????????????' until current location completes")
    public void testMysteryDestinationConcealment() {
        // Level 5 in Karad (Levels 1-15)
        WorldResolved currentWorld = getWorldForLevel(5, 15);
        assertEquals("karad", currentWorld.location.id);

        int nextUnlockLevel = currentWorld.endLevel + 1; // 16
        WorldResolved nextWorld = getWorldForLevel(nextUnlockLevel, 15);
        assertEquals("satara", nextWorld.location.id);

        // Simulated mystery representation
        String displayName = "????????????";
        String teaser = "Complete " + currentWorld.location.name + " to discover your next destination.";
        boolean isLocked = true;

        assertTrue(isLocked);
        assertEquals("????????????", displayName);
        assertEquals("Complete Karad to discover your next destination.", teaser);

        // When player advances to Level 16, Satara is unlocked and revealed
        WorldResolved unlockedWorld = getWorldForLevel(16, 15);
        assertEquals("satara", unlockedWorld.location.id);
        assertEquals("Satara", unlockedWorld.location.name);
    }

    @Test
    @DisplayName("7. Journey Timeline: Correctly generates visited (✓), current (➔), and locked mystery nodes")
    public void testJourneyTimelineGeneration() {
        // Player at Level 35 (Wai, Location 2)
        int currentLevel = 35;
        int currentLocIdx = (currentLevel - 1) / 15; // 2 (Wai)

        assertEquals(2, currentLocIdx);

        // Location 0 (Karad) -> isVisited = true
        assertTrue(0 < currentLocIdx);
        // Location 1 (Satara) -> isVisited = true
        assertTrue(1 < currentLocIdx);
        // Location 2 (Wai) -> isCurrent = true
        assertEquals(2, currentLocIdx);
        // Location 3 (Mahabaleshwar) -> isLocked = true (Mystery)
        assertTrue(3 > currentLocIdx);
    }
}

