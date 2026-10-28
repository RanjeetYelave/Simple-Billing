package com.billing.simple.billsoft.security;

import com.billing.simple.billsoft.util.DataDirectoryResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.File;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

/**
 * Authoritative Database Safety Guard for RupeeCRM.
 *
 * Enforces strict database isolation invariants:
 * 1. Test executions (JUnit, Surefire, IDE runners, test profile) CANNOT connect to production database files.
 * 2. If a test datasource resolves to a production file-based database, initialization FAILS FAST before
 *    any JPA/DDL or test logic can execute.
 * 3. Destructive operations (such as {@code BackupService.factoryReset()}) are strictly rejected if targeted
 *    at a production database file during test runs.
 * 4. Ensures test executions do not write or pollute the host user's application data directories.
 */
public final class DatabaseSafetyGuard {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSafetyGuard.class);

    private static volatile Boolean forcedTestMode = null;

    private DatabaseSafetyGuard() {
    }

    /**
     * For unit-testing the safety guard itself.
     */
    public static void setForcedTestMode(Boolean forced) {
        forcedTestMode = forced;
    }

    /**
     * Determines whether the current execution is within a test context.
     */
    public static boolean isTestEnvironment() {
        if (forcedTestMode != null) {
            return forcedTestMode;
        }

        String isolatedProp = System.getProperty("rupeecrm.test.isolated");
        if ("true".equalsIgnoreCase(isolatedProp)) {
            return true;
        }

        String appName = System.getProperty("spring.application.name");
        if (appName != null && appName.toLowerCase().contains("test")) {
            return true;
        }

        String profiles = System.getProperty("spring.profiles.active");
        if (profiles != null && profiles.toLowerCase().contains("test")) {
            return true;
        }

        // Check call stack for test runners
        for (StackTraceElement elem : Thread.currentThread().getStackTrace()) {
            String cls = elem.getClassName();
            if (cls.startsWith("org.junit.") ||
                cls.startsWith("org.testng.") ||
                cls.startsWith("org.apache.maven.surefire.") ||
                cls.startsWith("com.intellij.rt.junit.") ||
                cls.startsWith("org.eclipse.jdt.internal.junit.") ||
                cls.startsWith("org.springframework.boot.test.") ||
                cls.startsWith("org.springframework.test.")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extracts JDBC URL from DataSource without necessarily opening an exclusive connection.
     * Tries reflection getters first (JdbcDataSource, HikariDataSource), then falls back to metadata.
     */
    public static String getJdbcUrl(DataSource dataSource) {
        if (dataSource == null) {
            return null;
        }

        // 1. Try common getter methods via reflection to avoid acquiring file locks on already-open DBs
        for (String methodName : new String[]{"getURL", "getUrl", "getJdbcUrl"}) {
            try {
                Method m = dataSource.getClass().getMethod(methodName);
                Object val = m.invoke(dataSource);
                if (val instanceof String s && !s.isBlank()) {
                    return s.trim();
                }
            } catch (Exception ignored) {
            }
        }

        // 2. Fall back to opening a connection and querying metadata
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            return metaData != null ? metaData.getURL() : null;
        } catch (SQLException e) {
            String msg = e.getMessage();
            log.warn("Could not determine JDBC URL via connection: {}", msg);
            // If the SQLException contains a database path in its message (e.g. file lock error), extract and return it!
            if (msg != null && (msg.contains(".mv.db") || msg.contains("Database may be already in use") || msg.contains("database"))) {
                return "jdbc:h2:file:" + msg;
            }
            return null;
        }
    }

    /**
     * Normalizes a JDBC URL or file path for comparison.
     */
    public static String extractDatabaseFilePath(String jdbcUrl) {
        if (jdbcUrl == null) {
            return "";
        }
        String clean = jdbcUrl.trim();
        if (clean.toLowerCase().startsWith("jdbc:h2:file:")) {
            clean = clean.substring("jdbc:h2:file:".length());
        } else if (clean.toLowerCase().startsWith("jdbc:h2:")) {
            clean = clean.substring("jdbc:h2:".length());
        }
        int semicolon = clean.indexOf(';');
        if (semicolon != -1) {
            clean = clean.substring(0, semicolon);
        }
        return clean.replace('\\', '/').trim();
    }

    /**
     * Checks if a JDBC URL points to an isolated in-memory or disposable test database.
     */
    public static boolean isIsolatedTestDatabase(String jdbcUrl) {
        if (jdbcUrl == null) {
            return false;
        }
        String lower = jdbcUrl.toLowerCase().trim();
        if (lower.startsWith("jdbc:h2:mem:")) {
            return true;
        }
        if (lower.startsWith("jdbc:h2:file:")) {
            // Must NOT be a production path
            if (isProductionDatabasePath(jdbcUrl)) {
                return false;
            }
            String filePath = extractDatabaseFilePath(jdbcUrl).toLowerCase();
            // Must reside in temp or target or contain test marker
            String tempDir = System.getProperty("java.io.tmpdir", "/tmp").replace('\\', '/').toLowerCase();
            if (filePath.contains(tempDir) ||
                filePath.contains("/target/") ||
                filePath.contains("test-data") ||
                filePath.contains("testdb") ||
                filePath.contains("test_data") ||
                filePath.contains("disposable") ||
                filePath.contains("rehearsal") ||
                filePath.contains("database-test")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Identifies whether a given JDBC URL or file path resolves to the real RupeeCRM application database.
     */
    public static boolean isProductionDatabasePath(String jdbcUrl) {
        if (jdbcUrl == null) {
            return false;
        }
        String lower = jdbcUrl.toLowerCase().trim();
        if (lower.startsWith("jdbc:h2:mem:")) {
            return false;
        }

        String normalizedUrl = lower.replace('\\', '/');

        // Check user home directory paths
        String userHome = System.getProperty("user.home", "").replace('\\', '/').toLowerCase();

        // macOS standard path: ~/Library/Application Support/RupeeCRM/data
        String macProd = (userHome + "/library/application support/rupeecrm/data").toLowerCase();
        // Linux / fallback path: ~/.rupeecrm/data
        String linuxProd = (userHome + "/.rupeecrm/data").toLowerCase();
        // Windows path
        String localAppData = System.getenv("LOCALAPPDATA");
        String winProd = localAppData != null ? (localAppData.replace('\\', '/').toLowerCase() + "/rupeecrm/data") : "";

        // Check if normalized points to or matches production directories
        if (normalizedUrl.contains(macProd) || normalizedUrl.contains(linuxProd) ||
            (!winProd.isEmpty() && normalizedUrl.contains(winProd)) ||
            normalizedUrl.contains("/library/application support/rupeecrm/data") ||
            normalizedUrl.contains("/.rupeecrm/data") ||
            (normalizedUrl.contains("/rupeecrm/data") && !normalizedUrl.contains("target") && !normalizedUrl.contains("temp") && !normalizedUrl.contains("tmp"))) {
            return true;
        }

        // Check against DataDirectoryResolver's non-sandboxed resolution
        try {
            File prodDataDir = DataDirectoryResolver.resolveDataDirectory();
            String prodPath = prodDataDir.getAbsolutePath().replace('\\', '/').toLowerCase();
            if (!prodPath.contains("target/test-data") && normalizedUrl.contains(prodPath)) {
                return true;
            }
        } catch (Exception ignored) {
        }

        return false;
    }

    /**
     * Validates that the DataSource is safe for the current execution context.
     * Throws SecurityException if a test attempts to connect to production database.
     */
    public static void assertSafeDataSource(DataSource dataSource) {
        if (dataSource == null) {
            return;
        }
        String url = getJdbcUrl(dataSource);

        boolean testCtx = isTestEnvironment();
        if (testCtx) {
            if (url == null) {
                String error = "CRITICAL SAFETY BLOCK: Unable to determine DataSource JDBC URL in test context. "
                        + "Execution HALTED immediately to preserve database safety!";
                log.error(error);
                throw new SecurityException(error);
            }
            if (isProductionDatabasePath(url)) {
                String error = "CRITICAL DATABASE SAFETY VIOLATION: Test suite attempted to connect to production database URL: "
                        + url + ". Execution HALTED immediately to preserve real customer and business records!";
                log.error(error);
                throw new SecurityException(error);
            }
            if (!isIsolatedTestDatabase(url)) {
                String error = "UNSAFE TEST DATASOURCE: Test execution requires an isolated in-memory database "
                        + "(jdbc:h2:mem:...) or a designated disposable temporary file database. Target URL: " + url;
                log.error(error);
                throw new SecurityException(error);
            }
            log.info("Test Database Safety Guard verified isolated datasource: {}", url);
        }
    }

    /**
     * Asserts safety of a manually constructed test JDBC URL.
     */
    public static void assertSafeTestJdbcUrl(String jdbcUrl) {
        if (isTestEnvironment()) {
            if (isProductionDatabasePath(jdbcUrl)) {
                throw new SecurityException("CRITICAL: Test JDBC connection targets production database URL: " + jdbcUrl);
            }
            if (!isIsolatedTestDatabase(jdbcUrl)) {
                throw new SecurityException("CRITICAL: Test JDBC connection is not an isolated test database: " + jdbcUrl);
            }
        }
    }

    /**
     * Validates that factoryReset() is permitted on the current DataSource.
     */
    public static void validateFactoryResetSafety(DataSource dataSource) {
        if (dataSource == null) {
            return;
        }
        String url = getJdbcUrl(dataSource);

        if (isTestEnvironment()) {
            if (url == null || isProductionDatabasePath(url)) {
                String msg = "CRITICAL SAFETY BLOCK: Destructive factoryReset() rejected: production database URL detected during test execution: " + url;
                log.error(msg);
                throw new SecurityException(msg);
            }
            if (!isIsolatedTestDatabase(url)) {
                String msg = "CRITICAL SAFETY BLOCK: Destructive factoryReset() rejected: target database is not an isolated test database: " + url;
                log.error(msg);
                throw new SecurityException(msg);
            }
        } else {
            // Production execution: log high-severity audit marker
            log.warn("FACTORY RESET EXECUTED on datasource: {}", url);
        }
    }

    /**
     * Asserts that a directory resolved during tests does not point to the user's real application directory.
     */
    public static void assertSafeDataDirectory(File dir) {
        if (dir == null || !isTestEnvironment()) {
            return;
        }
        String path = dir.getAbsolutePath().replace('\\', '/').toLowerCase();
        String userHome = System.getProperty("user.home", "").replace('\\', '/').toLowerCase();
        String macProd = (userHome + "/library/application support/rupeecrm").toLowerCase();
        String linuxProd = (userHome + "/.rupeecrm").toLowerCase();

        if (path.startsWith(macProd) || path.startsWith(linuxProd)) {
            throw new SecurityException("CRITICAL SAFETY BLOCK: Test data directory resolved to user production directory: " + path);
        }
    }
}
