package com.billing.simple.billsoft.demo;

import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.service.BackupArchiveService;
import com.billing.simple.billsoft.service.BackupService;
import com.billing.simple.billsoft.util.DataDirectoryResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.File;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.*;

@Service
public class DemoModeService {

    private static final Logger log = LoggerFactory.getLogger(DemoModeService.class);
    private static final String FIXED_DEMO_PIN = "0000";

    private final DynamicRoutingDataSource routingDataSource;
    private final BackupService backupService;
    private final BackupArchiveService backupArchiveService;
    private final FirmDetailsRepository firmDetailsRepo;
    private final ObjectMapper objectMapper;

    private DemoState currentState = new DemoState();

    @Autowired
    public DemoModeService(DataSource dataSource,
                           BackupService backupService,
                           BackupArchiveService backupArchiveService,
                           FirmDetailsRepository firmDetailsRepo,
                           ObjectMapper objectMapper) {
        if (dataSource instanceof DynamicRoutingDataSource dynamicDs) {
            this.routingDataSource = dynamicDs;
        } else {
            this.routingDataSource = null;
            log.warn("DataSource is not an instance of DynamicRoutingDataSource: {}", dataSource.getClass());
        }
        this.backupService = backupService;
        this.backupArchiveService = backupArchiveService;
        this.firmDetailsRepo = firmDetailsRepo;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void onStartup() {
        loadPersistedState();
        checkStartupCrashRecovery();
    }

    public boolean verifyPin(String pin) {
        return FIXED_DEMO_PIN.equals(pin != null ? pin.trim() : "");
    }

    public synchronized void checkStartupCrashRecovery() {
        if (currentState != null && "DEMO".equalsIgnoreCase(currentState.getActiveMode()) && !currentState.isCleanExit()) {
            log.warn("CRASH RECOVERY: Previous RupeeCRM session ended unexpectedly while in DEMO mode. "
                    + "Safely defaulting to NORMAL mode to ensure real customer data protection.");
            EnvironmentModeContext.setGlobalMode(EnvironmentMode.NORMAL);
            currentState.setActiveMode("NORMAL");
            currentState.setCleanExit(true);
            currentState.setCrashRecovered(true);
            currentState.setLastExitedAt(Instant.now());
            savePersistedState();

            // Safely reset demo database so partial demo writes from crash are discarded
            try {
                if (routingDataSource != null) {
                    resetDemoDatabase();
                }
            } catch (Exception e) {
                log.warn("Post-crash demo database reset failed: {}", e.getMessage());
            }
        } else {
            EnvironmentModeContext.setGlobalMode(EnvironmentMode.NORMAL);
        }
    }

    /**
     * Initializes the demo database schema and pristine template if not already present.
     */
    public synchronized void initializeDemoDatabase() {
        if (routingDataSource == null) {
            throw new IllegalStateException("Dynamic routing data source is not configured");
        }

        HikariDataSource demoDs = routingDataSource.getDemoDataSource();
        HikariDataSource normalDs = routingDataSource.getNormalDataSource();

        File demoDir = DataDirectoryResolver.resolveDemoDirectory();
        File templateSql = new File(demoDir, "demo_seed_template.sql");

        try {
            // 1. Check if demo database already has tables
            boolean hasTables = false;
            try (Connection conn = demoDs.getConnection()) {
                DatabaseMetaData meta = conn.getMetaData();
                try (ResultSet rs = meta.getTables(null, "PUBLIC", "FIRMS", null)) {
                    hasTables = rs.next();
                }
            }

            // 2. If tables don't exist, clone schema from normal database
            if (!hasTables) {
                log.info("Cloning database schema from Normal DataSource to Demo DataSource...");
                List<String> ddlStatements = new ArrayList<>();
                try (Connection normConn = normalDs.getConnection();
                     Statement normStmt = normConn.createStatement();
                     ResultSet rs = normStmt.executeQuery("SCRIPT NODATA NOPASSWORDS NOSETTINGS")) {
                    while (rs.next()) {
                        String sql = rs.getString(1);
                        if (sql != null && !sql.isBlank()) {
                            ddlStatements.add(sql);
                        }
                    }
                }

                try (Connection demoConn = demoDs.getConnection();
                     Statement demoStmt = demoConn.createStatement()) {
                    for (String sql : ddlStatements) {
                        try {
                            demoStmt.execute(sql);
                        } catch (Exception ignored) {
                        }
                    }
                }
                log.info("Successfully initialized demo database schema with {} DDL statements.", ddlStatements.size());
            }

            // 3. Check if template SQL exists. If yes, reset from template.
            if (templateSql.exists() && templateSql.length() > 0) {
                log.info("Applying existing demo seed template: {}", templateSql.getAbsolutePath());
                resetFromTemplate(templateSql);
                return;
            }

            // 4. If template SQL does not exist, load demo_seed_dataset.json from classpath
            log.info("Loading demo_seed_dataset.json into pristine demo database...");
            InputStream is = getClass().getResourceAsStream("/demo/demo_seed_dataset.json");
            if (is == null) {
                File localJson = new File("src/main/resources/demo/demo_seed_dataset.json");
                if (localJson.exists()) {
                    is = new java.io.FileInputStream(localJson);
                }
            }

            if (is == null) {
                throw new IllegalStateException("Pristine demo dataset file demo_seed_dataset.json not found in classpath!");
            }

            BackupDTO demoBackup;
            try (InputStream stream = is) {
                demoBackup = objectMapper.readValue(stream, BackupDTO.class);
            }

            // Import into demo database under thread-local DEMO mode
            EnvironmentModeContext.setThreadOverride(EnvironmentMode.DEMO);
            try {
                backupService.importSelectiveData(demoBackup, null, "clone", null);
            } finally {
                EnvironmentModeContext.clearThreadOverride();
            }

            // Export pristine SQL template for instantaneous, deterministic resets
            log.info("Exporting pristine demo template to: {}", templateSql.getAbsolutePath());
            try (Connection demoConn = demoDs.getConnection();
                 Statement demoStmt = demoConn.createStatement()) {
                demoStmt.execute("SCRIPT TO '" + templateSql.getAbsolutePath() + "'");
            }

            if (demoDs.getHikariPoolMXBean() != null) {
                demoDs.getHikariPoolMXBean().softEvictConnections();
            }
            log.info("Demo database initialized and pristine template successfully created.");

        } catch (Exception e) {
            log.error("Failed to initialize demo database: {}", e.getMessage(), e);
            throw new RuntimeException("Demo database initialization failed: " + e.getMessage(), e);
        }
    }

    /**
     * Resets the demo database back to its identical predefined state using the SQL template.
     */
    public synchronized void resetDemoDatabase() {
        if (routingDataSource == null) return;
        File demoDir = DataDirectoryResolver.resolveDemoDirectory();
        File templateSql = new File(demoDir, "demo_seed_template.sql");

        if (!templateSql.exists() || templateSql.length() == 0) {
            initializeDemoDatabase();
            return;
        }

        resetFromTemplate(templateSql);
    }

    private void resetFromTemplate(File templateSql) {
        HikariDataSource demoDs = routingDataSource.getDemoDataSource();
        try (Connection conn = demoDs.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP ALL OBJECTS");
            stmt.execute("RUNSCRIPT FROM '" + templateSql.getAbsolutePath() + "'");
        } catch (Exception e) {
            log.error("Failed to reset demo database from template: {}", e.getMessage(), e);
            throw new RuntimeException("Demo database reset failed: " + e.getMessage(), e);
        }

        try {
            if (demoDs.getHikariPoolMXBean() != null) {
                demoDs.getHikariPoolMXBean().softEvictConnections();
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Enters Demo Mode with verification of PIN 0000.
     */
    public synchronized Map<String, Object> enterDemoMode(String pin) {
        if (!verifyPin(pin)) {
            throw new IllegalArgumentException("Invalid Demo PIN. Please use PIN 0000 to enter Demo Mode.");
        }

        Long preservedNormalFirm = TenantContext.getCurrentFirmId();

        // 1. Reset demo database to pristine state
        resetDemoDatabase();

        // 2. Switch global active environment to DEMO
        EnvironmentModeContext.setGlobalMode(EnvironmentMode.DEMO);

        // 3. Find demo firms
        List<FirmDetails> demoFirms = firmDetailsRepo.findAll();
        FirmDetails activeDemoFirm = null;
        for (FirmDetails f : demoFirms) {
            if ("Apex Plumbing Solutions".equalsIgnoreCase(f.getFirmName())) {
                activeDemoFirm = f;
                break;
            }
        }
        if (activeDemoFirm == null && !demoFirms.isEmpty()) {
            activeDemoFirm = demoFirms.get(0);
        }

        Long demoFirmId = activeDemoFirm != null ? activeDemoFirm.getId() : 1L;
        TenantContext.setCurrentFirmId(demoFirmId);

        // 4. Update state
        currentState.setActiveMode("DEMO");
        currentState.setCleanExit(false);
        currentState.setLastEnteredAt(Instant.now());
        currentState.setActiveDemoFirmId(demoFirmId);
        currentState.setPreservedNormalFirmId(preservedNormalFirm);
        currentState.setCrashRecovered(false);
        savePersistedState();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("mode", "DEMO");
        result.put("activeFirmId", demoFirmId);
        result.put("activeFirmName", activeDemoFirm != null ? activeDemoFirm.getFirmName() : "Apex Plumbing Solutions");
        result.put("demoFirms", demoFirms);
        result.put("message", "Demo Mode activated successfully. Fictional business environment is ready.");
        return result;
    }

    /**
     * Exits Demo Mode and restores the normal production environment.
     */
    public synchronized Map<String, Object> exitDemoMode() {
        log.info("Exiting Demo Mode and restoring Normal production environment...");

        Long preservedNormalFirm = currentState.getPreservedNormalFirmId();

        // 1. Clear TenantContext
        TenantContext.clear();

        // 2. Switch global active environment back to NORMAL
        EnvironmentModeContext.setGlobalMode(EnvironmentMode.NORMAL);

        // 3. Verify normal database is accessible
        List<FirmDetails> normalFirms = firmDetailsRepo.findAll();
        Long activeNormalFirm = null;
        if (preservedNormalFirm != null) {
            for (FirmDetails f : normalFirms) {
                if (preservedNormalFirm.equals(f.getId())) {
                    activeNormalFirm = f.getId();
                    break;
                }
            }
        }
        if (activeNormalFirm == null && !normalFirms.isEmpty()) {
            activeNormalFirm = normalFirms.get(0).getId();
        }

        if (activeNormalFirm != null) {
            TenantContext.setCurrentFirmId(activeNormalFirm);
        }

        // 4. Reset demo database to pristine state for next launch
        try {
            resetDemoDatabase();
        } catch (Exception e) {
            log.warn("Background demo reset during exit encountered error: {}", e.getMessage());
        }

        // 5. Update state
        currentState.setActiveMode("NORMAL");
        currentState.setCleanExit(true);
        currentState.setLastExitedAt(Instant.now());
        currentState.setPreservedNormalFirmId(null);
        currentState.setActiveDemoFirmId(null);
        savePersistedState();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("mode", "NORMAL");
        result.put("activeFirmId", activeNormalFirm);
        result.put("normalFirmsCount", normalFirms.size());
        result.put("message", "Exited Demo Mode. Your original business environment has been cleanly restored.");
        return result;
    }

    public Map<String, Object> getStatus() {
        boolean isDemo = EnvironmentModeContext.isDemoMode();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("isDemoMode", isDemo);
        status.put("activeMode", isDemo ? "DEMO" : "NORMAL");
        status.put("activeFirmId", TenantContext.getCurrentFirmId());
        status.put("crashRecovered", currentState.isCrashRecovered());

        if (isDemo) {
            status.put("demoFirms", firmDetailsRepo.findAll());
        }
        return status;
    }

    private void loadPersistedState() {
        File stateFile = DataDirectoryResolver.resolveDemoStateFile();
        if (stateFile.exists() && stateFile.length() > 0) {
            try {
                this.currentState = objectMapper.readValue(stateFile, DemoState.class);
            } catch (Exception e) {
                log.warn("Could not read demo_state.json: {}", e.getMessage());
                this.currentState = new DemoState();
            }
        } else {
            this.currentState = new DemoState();
        }
    }

    private void savePersistedState() {
        File stateFile = DataDirectoryResolver.resolveDemoStateFile();
        try {
            objectMapper.writeValue(stateFile, this.currentState);
        } catch (Exception e) {
            log.error("Failed to write demo_state.json: {}", e.getMessage());
        }
    }
}
