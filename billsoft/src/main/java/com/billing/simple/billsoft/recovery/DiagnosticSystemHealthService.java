package com.billing.simple.billsoft.recovery;

import com.billing.simple.billsoft.util.DataDirectoryResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringBootVersion;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

@Service
public class DiagnosticSystemHealthService {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticSystemHealthService.class);

    private final DataSource dataSource;

    public DiagnosticSystemHealthService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Map<String, Object> getHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());

        // 1. Application info
        Map<String, Object> appInfo = new HashMap<>();
        try {
            RuntimeMXBean runtimeMx = ManagementFactory.getRuntimeMXBean();
            appInfo.put("status", "UP");
            appInfo.put("jvmUptimeMs", runtimeMx.getUptime());
            appInfo.put("jvmStartTime", runtimeMx.getStartTime());
            appInfo.put("javaVersion", System.getProperty("java.version"));
            appInfo.put("javaVendor", System.getProperty("java.vendor"));
            appInfo.put("osName", System.getProperty("os.name"));
            appInfo.put("osArch", System.getProperty("os.arch"));
            appInfo.put("springBootVersion", SpringBootVersion.getVersion());
        } catch (Exception e) {
            log.warn("Failed to collect application metrics: {}", e.getMessage());
            appInfo.put("status", "DEGRADED");
            appInfo.put("error", e.getMessage());
        }
        health.put("application", appInfo);

        // 2. Database probe
        Map<String, Object> dbInfo = new HashMap<>();
        long startProbe = System.currentTimeMillis();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            
            stmt.execute("SELECT 1");
            long latencyMs = System.currentTimeMillis() - startProbe;

            DatabaseMetaData meta = conn.getMetaData();
            dbInfo.put("status", "CONNECTED");
            dbInfo.put("latencyMs", latencyMs);
            dbInfo.put("databaseProductName", meta.getDatabaseProductName());
            dbInfo.put("databaseProductVersion", meta.getDatabaseProductVersion());
            dbInfo.put("driverName", meta.getDriverName());
            dbInfo.put("driverVersion", meta.getDriverVersion());
            
            String rawUrl = meta.getURL();
            dbInfo.put("databaseUrl", sanitizeJdbcUrl(rawUrl));
        } catch (Exception e) {
            log.warn("Database health check probe failed: {}", e.getMessage());
            dbInfo.put("status", "DISCONNECTED");
            dbInfo.put("latencyMs", System.currentTimeMillis() - startProbe);
            dbInfo.put("error", e.getMessage());
        }
        health.put("database", dbInfo);

        // 3. JVM Memory probe
        Map<String, Object> jvmInfo = new HashMap<>();
        try {
            Runtime runtime = Runtime.getRuntime();
            long totalMem = runtime.totalMemory();
            long freeMem = runtime.freeMemory();
            long maxMem = runtime.maxMemory();
            long usedMem = totalMem - freeMem;

            jvmInfo.put("status", "HEALTHY");
            jvmInfo.put("usedMemoryBytes", usedMem);
            jvmInfo.put("usedMemoryMB", usedMem / (1024 * 1024));
            jvmInfo.put("maxMemoryBytes", maxMem);
            jvmInfo.put("maxMemoryMB", maxMem / (1024 * 1024));
            jvmInfo.put("freeMemoryBytes", freeMem);
            jvmInfo.put("freeMemoryMB", freeMem / (1024 * 1024));
            jvmInfo.put("availableProcessors", runtime.availableProcessors());
        } catch (Exception e) {
            log.warn("JVM memory health probe failed: {}", e.getMessage());
            jvmInfo.put("status", "ERROR");
            jvmInfo.put("error", e.getMessage());
        }
        health.put("jvm", jvmInfo);

        // 4. Storage probe
        Map<String, Object> storageInfo = new HashMap<>();
        try {
            File baseDir = DataDirectoryResolver.resolveBaseDirectory();
            long totalSpace = baseDir.getTotalSpace();
            long freeSpace = baseDir.getFreeSpace();
            long usableSpace = baseDir.getUsableSpace();

            storageInfo.put("status", "HEALTHY");
            storageInfo.put("baseDirectory", baseDir.getAbsolutePath());
            storageInfo.put("totalSpaceBytes", totalSpace);
            storageInfo.put("totalSpaceGB", String.format("%.2f GB", totalSpace / (1024.0 * 1024.0 * 1024.0)));
            storageInfo.put("freeSpaceBytes", freeSpace);
            storageInfo.put("freeSpaceGB", String.format("%.2f GB", freeSpace / (1024.0 * 1024.0 * 1024.0)));
            storageInfo.put("usableSpaceBytes", usableSpace);
            storageInfo.put("usableSpaceGB", String.format("%.2f GB", usableSpace / (1024.0 * 1024.0 * 1024.0)));
        } catch (Exception e) {
            log.warn("Storage health probe failed: {}", e.getMessage());
            storageInfo.put("status", "ERROR");
            storageInfo.put("error", e.getMessage());
        }
        health.put("storage", storageInfo);

        return health;
    }

    private String sanitizeJdbcUrl(String url) {
        if (url == null) return null;
        // Hide password if in query string or URL
        return url.replaceAll("(?i)(password=)[^;&]*", "$1*****");
    }
}
