package com.billing.simple.billsoft.theme;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

public class MinimalThemeSystemTest {

    private static final Path THEME_TOGGLE_JS = Paths.get("src/main/webapp/js/themeToggle.js");
    private static final Path INDEX_HTML = Paths.get("src/main/webapp/index.html");
    private static final Path STYLE_CSS = Paths.get("src/main/webapp/css/style.css");

    @Test
    @DisplayName("Verify themeToggle.js Minimal Theme support and state machine")
    void testThemeToggleMinimalSupport() throws IOException {
        assertTrue(Files.exists(THEME_TOGGLE_JS), "themeToggle.js must exist");
        String js = Files.readString(THEME_TOGGLE_JS);

        // Class registration
        assertTrue(js.contains("'minimal-light'"), "ALL_THEME_CLASSES must include minimal-light");
        assertTrue(js.contains("'minimal-dark'"), "ALL_THEME_CLASSES must include minimal-dark");

        // Theme family parsing
        assertTrue(js.contains("family !== 'minimal'"), "Validation must accept minimal family");
        assertTrue(js.contains("legacyBase === 'minimal'"), "Legacy migration must handle minimal");

        // Theme class resolution
        assertTrue(js.contains("if (family === 'minimal') {"), "computeThemeClass must handle minimal");
        assertTrue(js.contains("isDark ? 'minimal-dark' : 'minimal-light'"), "computeThemeClass must resolve minimal light/dark");

        // DOM element classes
        assertTrue(js.contains("root.classList.remove('minimal', 'classic', 'modern')"), "DOM class list reset must handle minimal");
        assertTrue(js.contains("state.family === 'minimal'"), "Family class must be added to root");

        // Public APIs
        assertTrue(js.contains("setThemeFamily"), "setThemeFamily API must exist");
        assertTrue(js.contains("setAppTheme"), "setAppTheme API must exist");
    }

    @Test
    @DisplayName("Verify SettingsAppearance in index.html displays Minimal theme family")
    void testSettingsAppearanceMinimalCard() throws IOException {
        assertTrue(Files.exists(INDEX_HTML), "index.html must exist");
        String html = Files.readString(INDEX_HTML);

        // 3 Theme Family cards
        assertTrue(html.contains("Minimal SaaS"), "Minimal SaaS family card must be present in Settings");
        assertTrue(html.contains("Modern System"), "Modern System family card must be preserved");
        assertTrue(html.contains("Classic Retro '98"), "Classic Retro family card must be preserved");

        // Selection handler
        assertTrue(html.contains("selectFamily('minimal')"), "Click handler for Minimal theme must exist");

        // Active badge label
        assertTrue(html.contains("Minimal Dark"), "Badge text for Minimal Dark must be present");
        assertTrue(html.contains("Minimal Light"), "Badge text for Minimal Light must be present");

        // Logo resolution for minimal-dark
        assertTrue(html.contains("theme === 'minimal-dark'"), "RupeeCRMLogo must resolve minimal-dark as dark background");
    }

    @Test
    @DisplayName("Verify style.css design tokens and component geometry for Minimal theme")
    void testStyleCssMinimalThemeTokens() throws IOException {
        assertTrue(Files.exists(STYLE_CSS), "style.css must exist");
        String css = Files.readString(STYLE_CSS);

        // Header and token definitions
        assertTrue(css.contains("RUPEECRM MINIMAL SAAS THEME"), "Minimal theme section header must exist");
        assertTrue(css.contains(":root.minimal-light"), "Minimal light selector must be defined");
        assertTrue(css.contains(":root.minimal-dark"), "Minimal dark selector must be defined");

        // Minimal Light tokens
        assertTrue(css.contains("--bg:                      #F6F6F4;"), "Minimal light canvas must be #F6F6F4");
        assertTrue(css.contains("--bg-card:                 #FFFFFF;"), "Minimal light card must be #FFFFFF");
        assertTrue(css.contains("--border:                  #EBEBE9;"), "Minimal light border must be #EBEBE9");
        assertTrue(css.contains("--primary:                 #168B45;"), "Minimal light primary must be #168B45");

        // Minimal Dark tokens
        assertTrue(css.contains("--bg:                      #0F1111;"), "Minimal dark canvas must be #0F1111");
        assertTrue(css.contains("--bg-card:                 #181B1B;"), "Minimal dark card must be #181B1B");
        assertTrue(css.contains("--border:                  #272C2C;"), "Minimal dark border must be #272C2C");
        assertTrue(css.contains("--primary:                 #22C55E;"), "Minimal dark primary must be #22C55E");

        // Geometry (24px cards, 9999px pills)
        assertTrue(css.contains("--radius-card:        24px;"), "Card radius must be 24px");
        assertTrue(css.contains("--radius-pill:        9999px;"), "Pill radius must be 9999px");

        // Component rules
        assertTrue(css.contains(":root.minimal .kpi-card"), "Minimal KPI card rules must exist");
        assertTrue(css.contains(":root.minimal .nav-item"), "Minimal sidebar nav rules must exist");
        assertTrue(css.contains(":root.minimal .btn-primary"), "Minimal primary button rules must exist");
        assertTrue(css.contains(":root.minimal .table-container"), "Minimal table container rules must exist");
    }
}

