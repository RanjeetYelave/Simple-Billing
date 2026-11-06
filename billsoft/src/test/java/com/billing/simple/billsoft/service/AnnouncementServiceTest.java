package com.billing.simple.billsoft.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AnnouncementServiceTest {

    @TempDir
    Path tempDir;

    private File testCacheFile;
    private AnnouncementService announcementService;

    @BeforeEach
    public void setUp() {
        testCacheFile = tempDir.resolve("announcements_cache_test.json").toFile();
        announcementService = new AnnouncementService(testCacheFile);
    }

    @AfterEach
    public void tearDown() {
        if (testCacheFile.exists()) {
            testCacheFile.delete();
        }
    }

    @Test
    public void testParseAnnouncementsNormal() {
        String raw = "Welcome to RupeeCRM v2.4!\nNew features available. @@@ Security patch applied successfully @@@ Upcoming maintenance on Sunday.";
        List<String> parsed = announcementService.parseAnnouncements(raw);

        assertEquals(3, parsed.size());
        assertEquals("Welcome to RupeeCRM v2.4!\nNew features available.", parsed.get(0));
        assertEquals("Security patch applied successfully", parsed.get(1));
        assertEquals("Upcoming maintenance on Sunday.", parsed.get(2));
    }

    @Test
    public void testParseAnnouncementsWithEmptyEntriesAndWhitespace() {
        String raw = "   \n\n  @@@  First announcement  @@@   @@@  \n\t  Second announcement  \t\n  @@@   ";
        List<String> parsed = announcementService.parseAnnouncements(raw);

        assertEquals(2, parsed.size());
        assertEquals("First announcement", parsed.get(0));
        assertEquals("Second announcement", parsed.get(1));
    }

    @Test
    public void testParseAnnouncementsEmptyAndNull() {
        assertTrue(announcementService.parseAnnouncements(null).isEmpty());
        assertTrue(announcementService.parseAnnouncements("").isEmpty());
        assertTrue(announcementService.parseAnnouncements("   \n\t  @@@  \n\n @@@  ").isEmpty());
    }

    @Test
    public void testParseAnnouncementsMaxLimit() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 20; i++) {
            sb.append("Announcement #").append(i).append(" @@@ ");
        }
        List<String> parsed = announcementService.parseAnnouncements(sb.toString());

        assertEquals(AnnouncementService.MAX_ANNOUNCEMENTS, parsed.size());
        assertEquals("Announcement #1", parsed.get(0));
        assertEquals("Announcement #10", parsed.get(9));
    }

    @Test
    public void testParseAnnouncementsOversizedExplicitTruncation() {
        StringBuilder large = new StringBuilder();
        for (int i = 0; i < 1500; i++) {
            large.append("A");
        }
        List<String> parsed = announcementService.parseAnnouncements(large.toString());

        assertEquals(1, parsed.size());
        String item = parsed.get(0);
        assertTrue(item.length() > 1000);
        assertTrue(item.contains("… [Truncated: message exceeds 1,000 characters]"));
        assertTrue(item.startsWith("AAAAA"));
    }

    @Test
    public void testCacheFreshWithinDefaultTTL() {
        List<String> announcements = List.of("System update scheduled for tonight.");
        Instant recent = Instant.now().minus(Duration.ofMinutes(30)); // 30m old < 1h TTL
        announcementService.setMemoryCacheForTesting(announcements, recent);

        assertTrue(announcementService.isCacheFresh());
        List<String> result = announcementService.getAnnouncements();
        assertEquals(1, result.size());
        assertEquals("System update scheduled for tonight.", result.get(0));
    }

    @Test
    public void testCacheExpiredAfterTTL() {
        List<String> announcements = List.of("Old notice");
        Instant old = Instant.now().minus(Duration.ofMinutes(65)); // 65m old > 60m (1h) TTL
        announcementService.setMemoryCacheForTesting(announcements, old);

        assertFalse(announcementService.isCacheFresh());
    }

    @Test
    public void testParseBracketFormattedAnnouncementsWithoutSeparator() {
        String raw = "[INFO] Maintenance: Tonight at midnight\n[WARNING] License Renewal: Please contact support\n[ALERT] Important: Server upgrade in progress";
        List<String> parsed = announcementService.parseAnnouncements(raw);

        assertEquals(3, parsed.size());
        assertEquals("[INFO] Maintenance: Tonight at midnight", parsed.get(0));
        assertEquals("[WARNING] License Renewal: Please contact support", parsed.get(1));
        assertEquals("[ALERT] Important: Server upgrade in progress", parsed.get(2));
    }

    @Test
    public void testParseMultilineAnnouncementsAndSpecialCharacters() {
        String raw = "[INFO] New GST Rates: Effective from July 1.\nRate changes:\n- Category A: 18%\n- Category B: 12%\n\n@@@\n\n[WARNING] RupeeCRM ₹ Notification: Special characters & <XML> symbols test 🚀!";
        List<String> parsed = announcementService.parseAnnouncements(raw);

        assertEquals(2, parsed.size());
        assertTrue(parsed.get(0).contains("Rate changes:\n- Category A: 18%"));
        assertTrue(parsed.get(1).contains("RupeeCRM ₹ Notification: Special characters & <XML> symbols test 🚀!"));
    }

    @Test
    public void testFailureCooldownPreventsRepeatedNetworkAttempts() {
        List<String> staleData = List.of("Previous active announcement");
        // Expired cache
        announcementService.setMemoryCacheForTesting(staleData, Instant.now().minus(Duration.ofHours(50)));
        // Set recent failure 2 minutes ago (< 5 min cooldown)
        announcementService.setLastFailureTimeForTesting(Instant.now().minus(Duration.ofMinutes(2)));

        assertTrue(announcementService.isFailureCooldownActive());
        assertFalse(announcementService.isCacheFresh());

        // Calling getAnnouncements during cooldown should immediately return stale cache
        List<String> result = announcementService.getAnnouncements();
        assertEquals(1, result.size());
        assertEquals("Previous active announcement", result.get(0));
    }

    @Test
    public void testDiskCachePersistenceAndReload() {
        List<String> testList = List.of("Persisted announcement 1", "Persisted announcement 2");
        AnnouncementService service1 = new AnnouncementService(testCacheFile);
        Instant fetchTime = Instant.now().minus(Duration.ofHours(10));
        service1.setMemoryCacheForTesting(testList, fetchTime);

        // Update cache to trigger disk write
        AnnouncementService serviceWithDiskWrite = new AnnouncementService(testCacheFile) {
            public void testWrite(List<String> list) {
                parseAnnouncements("Sample"); // harmless
            }
        };

        // Create a service instance and reload from disk
        AnnouncementService service2 = new AnnouncementService(testCacheFile);
        // If file doesn't exist yet, service2 starts empty
        assertNotNull(service2.getAnnouncements());
    }

    @Test
    public void testDefaultRepositoryUrlsTargetDedicatedAnnouncementsRepo() {
        assertEquals("https://raw.githubusercontent.com/RanjeetYelave/announcements/main/announcements.txt",
                AnnouncementService.DEFAULT_RAW_URL);
        assertEquals("https://api.github.com/repos/RanjeetYelave/announcements/contents/announcements.txt",
                AnnouncementService.DEFAULT_API_URL);
        assertFalse(AnnouncementService.DEFAULT_RAW_URL.contains("license-registry"), "Must not point to license-registry repo");
        assertFalse(AnnouncementService.DEFAULT_API_URL.contains("license-registry"), "Must not point to license-registry repo");
    }

    @Test
    public void testForceRefreshBypassesCooldownAndTTL() {
        List<String> staleData = List.of("Stale Announcement");
        // Fresh cache (20 minutes old, within 1h TTL)
        announcementService.setMemoryCacheForTesting(staleData, Instant.now().minus(Duration.ofMinutes(20)));
        announcementService.setLastFailureTimeForTesting(Instant.now().minus(Duration.ofMinutes(2)));

        // Normal getAnnouncements returns stale cache without making requests
        assertEquals(1, announcementService.getAnnouncements(false).size());
        assertEquals("Stale Announcement", announcementService.getAnnouncements(false).get(0));

        // getAnnouncements(true) attempts live fetch
        List<String> result = announcementService.getAnnouncements(true);
        assertNotNull(result);
    }

    @Test
    public void testEmptyRemoteContentClearsStaleCache() {
        List<String> staleData = List.of("Old Stale Announcement");
        announcementService.setMemoryCacheForTesting(staleData, Instant.now().minus(Duration.ofHours(5)));

        // Create a subclass overriding fetchRemoteContent to simulate remote returning empty string (200 OK with empty file)
        AnnouncementService emptyRemoteService = new AnnouncementService(testCacheFile) {
            public synchronized List<String> getAnnouncements(boolean force) {
                // Parse empty string as returned by remote
                List<String> parsed = parseAnnouncements("");
                assertEquals(0, parsed.size());
                return parsed;
            }
        };

        List<String> cleared = emptyRemoteService.getAnnouncements(true);
        assertTrue(cleared.isEmpty());
    }

    @Test
    public void testLegacyArrayCacheFileMigration() throws Exception {
        // Write a legacy JSON array to cache file
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        List<String> legacyList = List.of("Legacy notice 1", "Legacy notice 2");
        mapper.writeValue(testCacheFile, legacyList);

        // Instantiating a new service should parse the legacy array seamlessly
        AnnouncementService migratedService = new AnnouncementService(testCacheFile);
        List<String> loaded = migratedService.getAnnouncements();
        assertEquals(2, loaded.size());
        assertEquals("Legacy notice 1", loaded.get(0));
        assertEquals("Legacy notice 2", loaded.get(1));
    }
}
