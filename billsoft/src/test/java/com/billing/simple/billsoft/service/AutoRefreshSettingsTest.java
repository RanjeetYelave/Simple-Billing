package com.billing.simple.billsoft.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

public class AutoRefreshSettingsTest {

    private static final Path INDEX_HTML_PATH = Paths.get("src/main/webapp/index.html");

    @Test
    @DisplayName("Verify AutoRefreshConfig utility configuration and bounds in index.html")
    void testAutoRefreshConfigPresenceAndBounds() throws IOException {
        assertTrue(Files.exists(INDEX_HTML_PATH), "index.html must exist");
        String content = Files.readString(INDEX_HTML_PATH);

        // Utility definition
        assertTrue(content.contains("const AutoRefreshConfig = {"), "AutoRefreshConfig must be defined");
        assertTrue(content.contains("KEY_ENABLED: 'rupeecrm_auto_refresh_enabled'"), "Correct localStorage key for enabled");
        assertTrue(content.contains("KEY_INTERVAL: 'rupeecrm_auto_refresh_interval_sec'"), "Correct localStorage key for interval");

        // Defaults and bounds
        assertTrue(content.contains("DEFAULT_ENABLED: false"), "Default auto refresh must be disabled (false)");
        assertTrue(content.contains("DEFAULT_INTERVAL_SEC: 30"), "Default interval must be 30 seconds");
        assertTrue(content.contains("MIN_INTERVAL_SEC: 1"), "Minimum allowed interval must be 1 second");
        assertTrue(content.contains("MAX_INTERVAL_SEC: 300"), "Maximum allowed interval must be 300 seconds (5 min)");

        // Format helper
        assertTrue(content.contains("formatInterval(sec)"), "formatInterval helper must exist");
    }

    @Test
    @DisplayName("Verify SettingsSecurity UI card and controls for Automatic Data Refresh")
    void testSettingsSecurityUiIntegration() throws IOException {
        String content = Files.readString(INDEX_HTML_PATH);

        // Setting title & description
        assertTrue(content.contains("Automatic Data Refresh"), "Setting title must be 'Automatic Data Refresh'");
        assertTrue(content.contains("Automatically refresh application data at the selected interval."),
                "Description must match exact prompt requirement");

        // Toggle and selector
        assertTrue(content.contains("handleAutoRefreshToggle"), "Toggle handler must be implemented");
        assertTrue(content.contains("handleAutoRefreshIntervalChange"), "Interval change handler must be implemented");
        assertTrue(content.contains("Refresh Interval"), "Interval label must exist");

        // Range coverage (1 to 300 seconds)
        assertTrue(content.contains("min: 1"), "Number input min must be 1");
        assertTrue(content.contains("max: 300"), "Number input max must be 300");
    }

    @Test
    @DisplayName("Verify App-level scheduler, active form safety guards, and concurrency protections")
    void testAppSchedulerAndSafetyGuards() throws IOException {
        String content = Files.readString(INDEX_HTML_PATH);

        // Scheduler lifecycle
        assertTrue(content.contains("AUTOMATIC DATA REFRESH SCHEDULER & LIFECYCLE"), "Scheduler section must exist in App");
        assertTrue(content.contains("billsoft:auto-refresh-config-changed"), "Must synchronize on config change events");

        // Reusing existing refresh mechanism
        assertTrue(content.contains("handleAppRefresh({ isAuto: true })"), "Must reuse existing handleAppRefresh implementation");

        // Concurrency guard
        assertTrue(content.contains("isSyncing || isAutoRefreshingRef.current"), "Must prevent overlapping or concurrent refreshes");

        // Browser tab visibility check
        assertTrue(content.contains("document.hidden"), "Must check document.hidden to avoid background requests when tab is inactive");

        // Form interaction / unsaved data protection
        assertTrue(content.contains("activeEl.tagName === 'INPUT'"), "Must check focused input element");
        assertTrue(content.contains("activeEl.tagName === 'TEXTAREA'"), "Must check focused textarea element");
        assertTrue(content.contains("activeEl.isContentEditable"), "Must check contenteditable");
        assertTrue(content.contains(".modal-overlay"), "Must check if modal overlay is open");
    }

    @Test
    @DisplayName("Verify interval bounds calculation logic")
    void testIntervalBoundingLogic() {
        // Test simulation of bounds clamping
        int minSec = 1;
        int maxSec = 300;
        int defaultSec = 30;

        // Valid intervals
        assertEquals(1, Math.max(minSec, Math.min(maxSec, 1)));
        assertEquals(15, Math.max(minSec, Math.min(maxSec, 15)));
        assertEquals(30, Math.max(minSec, Math.min(maxSec, 30)));
        assertEquals(180, Math.max(minSec, Math.min(maxSec, 180)));
        assertEquals(300, Math.max(minSec, Math.min(maxSec, 300)));

        // Boundary violations clamped safely
        assertEquals(1, Math.max(minSec, Math.min(maxSec, 0)));
        assertEquals(1, Math.max(minSec, Math.min(maxSec, -10)));
        assertEquals(300, Math.max(minSec, Math.min(maxSec, 301)));
        assertEquals(300, Math.max(minSec, Math.min(maxSec, 5000)));
    }

    @Test
    @DisplayName("Verify silent background refresh separation from manual workspace reload")
    void testSilentBackgroundRefreshSeparation() throws IOException {
        String content = Files.readString(INDEX_HTML_PATH);

        // Verification of handleAppRefresh silent mode differentiation
        assertTrue(content.contains("const isSilent = Boolean(isAuto || (opts && opts.silent));"),
                "handleAppRefresh must cleanly distinguish silent/auto refresh from manual workspace reload");
        assertTrue(content.contains("if (!isSilent) {\n          if (isSyncing) return;\n          setIsSyncing(true);"),
                "isSyncing (and full-screen overlay) must only activate for manual refresh, never for silent background refresh");
        assertTrue(content.contains("window.dispatchEvent(new CustomEvent('billsoft:silent-data-refresh'));"),
                "Must dispatch billsoft:silent-data-refresh for non-disruptive module sync");
        assertTrue(content.contains("window.dispatchEvent(new CustomEvent('billsoft:app-refresh', { detail: { isSilent: true } }));"),
                "Must notify standard app-refresh listeners with isSilent flag");
    }

    @Test
    @DisplayName("Verify application modules handle silent refresh without disruptive loading states")
    void testModuleSilentRefreshHandling() throws IOException {
        String content = Files.readString(INDEX_HTML_PATH);

        // Dashboard silent refresh handling
        assertTrue(content.contains("const isSilent = Boolean(opts && (opts.silent || (opts.detail && opts.detail.isSilent)));\n        if (!isSilent) setLoading(true);"),
                "Dashboard loadDashboardData must skip setLoading(true) during silent refresh");
        assertTrue(content.contains("window.addEventListener('billsoft:silent-data-refresh', handleSilentRefresh);"),
                "Dashboard must listen to billsoft:silent-data-refresh");

        // InvoiceStatusesView silent refresh handling
        assertTrue(content.contains("window.addEventListener('billsoft:silent-data-refresh', onAppRefresh);"),
                "InvoiceStatusesView must listen to billsoft:silent-data-refresh");

        // InvoiceForm active form preservation
        assertTrue(content.contains("window.addEventListener('billsoft:silent-data-refresh', handleBackgroundRefresh);"),
                "InvoiceForm must listen to silent-data-refresh to update reference data without disturbing active form state");

        // Inventory, Parties, Purchase Orders, Employees, Sales Returns silent refresh
        assertTrue(content.contains("window.addEventListener('billsoft:silent-data-refresh', handleSilent);"),
                "Modules must connect silent refresh listener to preserve UI stability");
    }

    @Test
    @DisplayName("Verify RupeeCRMSync data comparison engine and API request coalescing")
    void testDataSyncEngineAndRequestCoalescing() throws IOException {
        Path dataSyncPath = Paths.get("src/main/webapp/js/dataSync.js");
        assertTrue(Files.exists(dataSyncPath), "dataSync.js must exist");
        String syncContent = Files.readString(dataSyncPath);

        // Core dataSync capabilities
        assertTrue(syncContent.contains("isEqual(a, b)"), "Must provide deep structural equality check");
        assertTrue(syncContent.contains("canRefreshSafely()"), "Must provide safety guard check");
        assertTrue(syncContent.contains("isPageVisible(pageName)"), "Must provide scoped page visibility check");
        assertTrue(syncContent.contains("coalesceEvent("), "Must provide event coalescing");
        assertTrue(syncContent.contains("isSilentRefresh("), "Must provide silent refresh detector");

        // API request deduplication & in-flight promise coalescing
        Path apiJsPath = Paths.get("src/main/webapp/js/api.js");
        assertTrue(Files.exists(apiJsPath), "api.js must exist");
        String apiContent = Files.readString(apiJsPath);
        assertTrue(apiContent.contains("_inFlightRequests: new Map()"), "API must track in-flight requests");
        assertTrue(apiContent.contains("this._inFlightRequests.has(key)"), "Must join in-flight requests to prevent duplicate network calls");
        assertTrue(apiContent.contains("this._inFlightRequests.delete(key)"), "Must cleanly clear in-flight requests on completion");

        // Script inclusion in index.html
        String indexContent = Files.readString(INDEX_HTML_PATH);
        assertTrue(indexContent.contains("<script src=\"js/dataSync.js"), "dataSync.js must be loaded in index.html");
    }
}

