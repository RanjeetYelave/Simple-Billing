package com.billing.simple.billsoft.security;

import com.billing.simple.billsoft.util.DataDirectoryResolver;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.io.File;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@DisplayName("Database Safety Guard & Isolation Architecture Regression Test")
class DatabaseSafetyGuardRegressionTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        DatabaseSafetyGuard.setForcedTestMode(true);
    }

    @AfterEach
    void tearDown() {
        DatabaseSafetyGuard.setForcedTestMode(null);
    }

    private DataSource createDataSource(String url) {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL(url);
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }

    @Test
    @DisplayName("Test 1: Rejects Production macOS Database URL in Test Context")
    void testRejectsProductionMacOsDatabaseUrl() {
        String userHome = System.getProperty("user.home");
        String prodMacUrl = "jdbc:h2:file:" + userHome + "/Library/Application Support/RupeeCRM/data/database;DB_CLOSE_DELAY=-1";
        DataSource ds = createDataSource(prodMacUrl);

        assertThatThrownBy(() -> DatabaseSafetyGuard.assertSafeDataSource(ds))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("CRITICAL DATABASE SAFETY VIOLATION")
                .hasMessageContaining("Test suite attempted to connect to production database URL");
    }

    @Test
    @DisplayName("Test 2: Rejects Production Linux/Fallback Database URL in Test Context")
    void testRejectsProductionLinuxDatabaseUrl() {
        String userHome = System.getProperty("user.home");
        String prodLinuxUrl = "jdbc:h2:file:" + userHome + "/.rupeecrm/data/database;DB_CLOSE_DELAY=-1";
        DataSource ds = createDataSource(prodLinuxUrl);

        assertThatThrownBy(() -> DatabaseSafetyGuard.assertSafeDataSource(ds))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("CRITICAL DATABASE SAFETY VIOLATION");
    }

    @Test
    @DisplayName("Test 3: Allows In-Memory H2 Database in Test Context")
    void testAllowsInMemoryH2Database() {
        DataSource ds = createDataSource("jdbc:h2:mem:safetestdb;DB_CLOSE_DELAY=-1");

        assertThatCode(() -> DatabaseSafetyGuard.assertSafeDataSource(ds))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Test 4: Allows Temporary Disposable File Database in Test Context")
    void testAllowsTemporaryDisposableFileDatabase() {
        Path testDbFile = tempDir.resolve("target/test-data/disposable_db");
        DataSource ds = createDataSource("jdbc:h2:file:" + testDbFile.toAbsolutePath() + ";DB_CLOSE_DELAY=-1");

        assertThatCode(() -> DatabaseSafetyGuard.assertSafeDataSource(ds))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Test 5: Rejects Arbitrary Non-Test Persistent File Database in Test Context")
    void testRejectsArbitraryNonTestPersistentDatabase() {
        File unsafeDir = new File(System.getProperty("user.home"), "Desktop/my-real-database");
        DataSource ds = createDataSource("jdbc:h2:file:" + unsafeDir.getAbsolutePath());

        assertThatThrownBy(() -> DatabaseSafetyGuard.assertSafeDataSource(ds))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("UNSAFE TEST DATASOURCE");
    }

    @Test
    @DisplayName("Test 6: factoryReset() Safety Guard Blocks Destructive Reset on Production URL")
    void testFactoryResetSafetyBlocksProductionDatabase() {
        String userHome = System.getProperty("user.home");
        String prodUrl = "jdbc:h2:file:" + userHome + "/Library/Application Support/RupeeCRM/data/database;DB_CLOSE_DELAY=-1";
        DataSource ds = createDataSource(prodUrl);

        assertThatThrownBy(() -> DatabaseSafetyGuard.validateFactoryResetSafety(ds))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("CRITICAL SAFETY BLOCK: Destructive factoryReset() rejected: production database URL detected");
    }

    @Test
    @DisplayName("Test 7: factoryReset() Safety Guard Allows Isolated In-Memory Database")
    void testFactoryResetSafetyAllowsInMemoryDatabase() {
        DataSource ds = createDataSource("jdbc:h2:mem:isolated_reset_db;DB_CLOSE_DELAY=-1");

        assertThatCode(() -> DatabaseSafetyGuard.validateFactoryResetSafety(ds))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Test 8: DataDirectoryResolver Sandboxes to target/test-data/rupeecrm Under Test")
    void testDataDirectoryResolverSandboxesUnderTests() {
        File baseDir = DataDirectoryResolver.resolveBaseDirectory();
        File dataDir = DataDirectoryResolver.resolveDataDirectory();
        File snapshotsDir = DataDirectoryResolver.resolveDiagnosticSnapshotsDirectory();
        File backupsDir = DataDirectoryResolver.resolveBackupsDirectory();

        String userHome = System.getProperty("user.home");
        String prodMacPath = userHome + "/Library/Application Support/RupeeCRM";
        String prodLinuxPath = userHome + "/.rupeecrm";

        // Must NOT match user home production paths
        assertThat(baseDir.getAbsolutePath()).doesNotContain(prodMacPath);
        assertThat(baseDir.getAbsolutePath()).doesNotContain(prodLinuxPath);

        // Must be cleanly contained in test-data sandbox
        assertThat(baseDir.getAbsolutePath()).contains("target/test-data/rupeecrm");
        assertThat(dataDir.getAbsolutePath()).contains("target/test-data/rupeecrm/data");
        assertThat(snapshotsDir.getAbsolutePath()).contains("target/test-data/rupeecrm/diagnostic-snapshots");
        assertThat(backupsDir.getAbsolutePath()).contains("target/test-data/rupeecrm/backups");
    }

    @Test
    @DisplayName("Test 9: DatabaseSafetyGuard Correctly Recognizes Production Paths")
    void testProductionPathRecognition() {
        String userHome = System.getProperty("user.home").replace('\\', '/');

        assertThat(DatabaseSafetyGuard.isProductionDatabasePath("jdbc:h2:file:" + userHome + "/Library/Application Support/RupeeCRM/data/database")).isTrue();
        assertThat(DatabaseSafetyGuard.isProductionDatabasePath("jdbc:h2:file:" + userHome + "/.rupeecrm/data/database")).isTrue();
        assertThat(DatabaseSafetyGuard.isProductionDatabasePath("jdbc:h2:mem:testdb")).isFalse();
        assertThat(DatabaseSafetyGuard.isProductionDatabasePath("jdbc:h2:file:/tmp/test-data/database-test")).isFalse();
    }

    @Test
    @DisplayName("Test 10: assertSafeDataDirectory Blocks User Production Directory")
    void testAssertSafeDataDirectory() {
        File realUserDir = new File(System.getProperty("user.home"), "Library/Application Support/RupeeCRM");
        assertThatThrownBy(() -> DatabaseSafetyGuard.assertSafeDataDirectory(realUserDir))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("CRITICAL SAFETY BLOCK: Test data directory resolved to user production directory");

        File sandboxedDir = new File("target/test-data/rupeecrm");
        assertThatCode(() -> DatabaseSafetyGuard.assertSafeDataDirectory(sandboxedDir))
                .doesNotThrowAnyException();
    }
}
