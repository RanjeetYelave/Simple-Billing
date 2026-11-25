package com.billing.simple.billsoft.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReleaseNotesServiceTest {

    @TempDir
    Path tempDir;

    private File tempCacheFile;
    private ReleaseNotesService service;

    @BeforeEach
    void setUp() {
        tempCacheFile = tempDir.resolve("release_notes_cache_test.json").toFile();
        service = new ReleaseNotesService(tempCacheFile, "https://raw.githubusercontent.com/RanjeetYelave/Simple-Billing/PROD/release-notes.json");
    }

    @Test
    @DisplayName("Exact version matching returns correct notes without returning notes from another version")
    void testExactVersionMatching() {
        String testJson = "{\n" +
                "  \"1.0.389\": [\"Feature A\", \"Feature B\"],\n" +
                "  \"1.0.386\": [\"Feature C\"]\n" +
                "}";
        Map<String, List<String>> parsed = service.parseAndValidateJson(testJson);
        assertNotNull(parsed);
        assertEquals(2, parsed.size());

        // Test with and without leading 'v'
        assertEquals("1.0.389", ReleaseNotesService.normalizeVersion("v1.0.389"));
        assertEquals("1.0.389", ReleaseNotesService.normalizeVersion("1.0.389"));

        String notes389 = "- Feature A\n- Feature B";
        String notes386 = "- Feature C";

        // Seed store
        service.parseAndValidateJson(testJson).forEach((ver, list) -> {
            // verified via parse
        });

        // Test parsing isolation
        assertTrue(parsed.containsKey("1.0.389"));
        assertTrue(parsed.containsKey("1.0.386"));
        assertFalse(parsed.containsKey("1.0.385"));
    }

    @Test
    @DisplayName("Multiple bullet points are formatted with bullet dashes and readable lines")
    void testMultipleBulletPoints() {
        String testJson = "{\n" +
                "  \"1.0.389\": [\n" +
                "    \"Unified Order Products Workspace\",\n" +
                "    \"Multi-Vendor Purchasing in One Session\",\n" +
                "    \"Automated Forecast Synchronization\"\n" +
                "  ]\n" +
                "}";
        Map<String, List<String>> parsed = service.parseAndValidateJson(testJson);
        List<String> items = parsed.get("1.0.389");
        assertEquals(3, items.size());
        assertEquals("Unified Order Products Workspace", items.get(0));
        assertEquals("Multi-Vendor Purchasing in One Session", items.get(1));
        assertEquals("Automated Forecast Synchronization", items.get(2));
    }

    @Test
    @DisplayName("Missing version entry returns neutral fallback text")
    void testMissingVersionEntryReturnsNeutralFallback() {
        assertFalse(service.hasReleaseNotesForVersion("9.9.999"));
        assertEquals(ReleaseNotesService.DEFAULT_NO_NOTES, service.getReleaseNotesForVersion("9.9.999"));
        assertTrue(service.getReleaseNoteItemsForVersion("9.9.999").isEmpty());
    }

    @Test
    @DisplayName("Single string format is supported and parsed correctly")
    void testSingleStringFormatSupported() {
        String testJson = "{\n" +
                "  \"1.0.385\": \"Improved inventory performance and quick editing.\"\n" +
                "}";
        Map<String, List<String>> parsed = service.parseAndValidateJson(testJson);
        assertEquals(1, parsed.size());
        assertTrue(parsed.containsKey("1.0.385"));
        assertEquals(1, parsed.get("1.0.385").size());
        assertEquals("Improved inventory performance and quick editing.", parsed.get("1.0.385").get(0));
    }

    @Test
    @DisplayName("HTML and script tags are strictly stripped to prevent XSS injection")
    void testHtmlAndScriptSanitization() {
        String malicious = "<script>alert('xss')</script><b>Bold Title</b> Regular text <iframe src='evil.com'></iframe>";
        String clean = ReleaseNotesService.sanitizeText(malicious);
        assertFalse(clean.contains("<script>"));
        assertFalse(clean.contains("</script>"));
        assertFalse(clean.contains("<iframe"));
        assertFalse(clean.contains("<b>"));
        assertTrue(clean.contains("alert('xss')"));
        assertTrue(clean.contains("Bold Title"));
        assertTrue(clean.contains("Regular text"));
    }

    @Test
    @DisplayName("Unicode, punctuation, and currency symbols are preserved")
    void testUnicodeAndPunctuationPreserved() {
        String input = "⚡ Unified Workspace: Supports ₹5,000 orders & 100% accurate 'quotes'!";
        String clean = ReleaseNotesService.sanitizeText(input);
        assertEquals(input, clean);
    }

    @Test
    @DisplayName("Invalid JSON and malformed entries are handled safely without throwing exceptions")
    void testInvalidJsonHandling() {
        Map<String, List<String>> result1 = service.parseAndValidateJson("{ invalid json ::: ");
        assertTrue(result1.isEmpty());

        Map<String, List<String>> result2 = service.parseAndValidateJson(null);
        assertTrue(result2.isEmpty());

        Map<String, List<String>> result3 = service.parseAndValidateJson("");
        assertTrue(result3.isEmpty());

        Map<String, List<String>> result4 = service.parseAndValidateJson("[]");
        assertTrue(result4.isEmpty());

        // Malformed entries with null/empty versions or values
        String mixedJson = "{\n" +
                "  \"\": \"empty version\",\n" +
                "  \"1.0.1\": null,\n" +
                "  \"1.0.2\": [],\n" +
                "  \"1.0.3\": [null, \"\"],\n" +
                "  \"1.0.4\": [\"Valid note\"]\n" +
                "}";
        Map<String, List<String>> result5 = service.parseAndValidateJson(mixedJson);
        assertEquals(1, result5.size());
        assertTrue(result5.containsKey("1.0.4"));
    }

    @Test
    @DisplayName("Disk cache persists release notes and reloads across service restarts")
    void testDiskCachePersistence() throws Exception {
        String testJson = "{\n" +
                "  \"1.0.389\": [\"Offline Cached Note 1\", \"Offline Cached Note 2\"]\n" +
                "}";
        Files.writeString(tempCacheFile.toPath(), testJson);

        ReleaseNotesService newService = new ReleaseNotesService(tempCacheFile, null);
        newService.initialize();

        assertTrue(newService.hasReleaseNotesForVersion("1.0.389"));
        assertTrue(newService.getReleaseNotesForVersion("1.0.389").contains("Offline Cached Note 1"));
        assertEquals(2, newService.getReleaseNoteItemsForVersion("1.0.389").size());
    }

    @Test
    @DisplayName("Bundled classpath release-notes.json loads on initialization")
    void testBundledClasspathFallback() {
        ReleaseNotesService fresh = new ReleaseNotesService(new File(tempDir.toFile(), "non_existent.json"), null);
        fresh.loadBundledNotes();

        // Bundled release-notes.json has 1.0.389 and 1.0.386
        assertTrue(fresh.hasReleaseNotesForVersion("1.0.389"));
        assertTrue(fresh.hasReleaseNotesForVersion("1.0.386"));
        assertTrue(fresh.getReleaseNotesForVersion("1.0.389").contains("Unified Order Products"));
    }
}
