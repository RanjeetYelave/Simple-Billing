package com.billing.simple.billsoft.util;

import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Authoritative runtime data directory resolver for RupeeCRM.
 * 
 * Standard Windows Layout:
 *   - Base Directory:    %LOCALAPPDATA%\RupeeCRM
 *   - Data Directory:    %LOCALAPPDATA%\RupeeCRM\data  (contains database.mv.db, mid.dat, license.lic, settings.json)
 *   - Backups Directory: %LOCALAPPDATA%\RupeeCRM\backups (contains automated snapshots)
 *   - Logs Directory:    %LOCALAPPDATA%\RupeeCRM\logs (contains supervisor and server logs)
 *   - Staging Directory: %LOCALAPPDATA%\RupeeCRM\staging (contains verified download artifacts)
 *
 * macOS Layout:
 *   - ~/Library/Application Support/RupeeCRM/data
 *
 * Linux Layout:
 *   - ~/.rupeecrm/data
 */
public final class DataDirectoryResolver {

    private static final Logger log = LoggerFactory.getLogger(DataDirectoryResolver.class);

    private DataDirectoryResolver() {
    }

    /**
     * Resolves the top-level persistent base directory (%LOCALAPPDATA%\RupeeCRM on Windows).
     */
    public static File resolveBaseDirectory() {
        String custom = System.getProperty("RUPEECRM_BASE_DIR");
        if (custom == null || custom.trim().isEmpty()) {
            custom = System.getenv("RUPEECRM_BASE_DIR");
        }

        if (custom != null && !custom.trim().isEmpty()) {
            File dir = new File(custom.trim());
            if (!dir.exists()) {
                dir.mkdirs();
            }
            return dir;
        }

        // If custom data dir is set but base dir is not, derive base directory to maintain isolation
        String customData = System.getProperty("RUPEECRM_DATA_DIR");
        if (customData == null || customData.trim().isEmpty()) {
            customData = System.getenv("RUPEECRM_DATA_DIR");
        }
        if (customData == null || customData.trim().isEmpty()) {
            customData = System.getProperty("BILLSOFT_DATA_DIR");
        }
        if (customData == null || customData.trim().isEmpty()) {
            customData = System.getenv("BILLSOFT_DATA_DIR");
        }
        if (customData != null && !customData.trim().isEmpty()) {
            File dataDir = new File(customData.trim());
            File baseDir = dataDir.getName().equalsIgnoreCase("data") && dataDir.getParentFile() != null
                    ? dataDir.getParentFile()
                    : dataDir;
            if (!baseDir.exists()) {
                baseDir.mkdirs();
            }
            return baseDir;
        }

        String os = System.getProperty("os.name").toLowerCase();
        File baseDir;

        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isEmpty()) {
                baseDir = new File(localAppData, "RupeeCRM");
            } else {
                baseDir = new File(System.getProperty("user.home"), ".rupeecrm");
            }
        } else if (os.contains("mac")) {
            baseDir = new File(System.getProperty("user.home"), "Library/Application Support/RupeeCRM");
        } else {
            baseDir = new File(System.getProperty("user.home"), ".rupeecrm");
        }

        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
        return baseDir;
    }

    /**
     * Resolves the persistent customer data directory (%LOCALAPPDATA%\RupeeCRM\data on Windows).
     */
    public static File resolveDataDirectory() {
        String custom = System.getProperty("RUPEECRM_DATA_DIR");
        if (custom == null || custom.trim().isEmpty()) {
            custom = System.getenv("RUPEECRM_DATA_DIR");
        }
        if (custom == null || custom.trim().isEmpty()) {
            custom = System.getProperty("BILLSOFT_DATA_DIR");
        }
        if (custom == null || custom.trim().isEmpty()) {
            custom = System.getenv("BILLSOFT_DATA_DIR");
        }

        if (custom != null && !custom.trim().isEmpty()) {
            File dir = new File(custom.trim());
            if (!dir.exists()) {
                dir.mkdirs();
            }
            return dir;
        }

        File baseDir = resolveBaseDirectory();
        File dataDir = new File(baseDir, "data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        return dataDir;
    }

    public static String resolveDataDirectoryPath() {
        return resolveDataDirectory().getAbsolutePath();
    }

    /**
     * Resolves the persistent backups directory (%LOCALAPPDATA%\RupeeCRM\backups).
     */
    public static File resolveBackupsDirectory() {
        File baseDir = resolveBaseDirectory();
        File backupsDir = new File(baseDir, "backups");
        if (!backupsDir.exists()) {
            backupsDir.mkdirs();
        }
        return backupsDir;
    }

    /**
     * Resolves the logs directory (%LOCALAPPDATA%\RupeeCRM\logs).
     */
    public static File resolveLogsDirectory() {
        File baseDir = resolveBaseDirectory();
        File logsDir = new File(baseDir, "logs");
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }
        return logsDir;
    }

    /**
     * Resolves the atomic staging directory (%LOCALAPPDATA%\RupeeCRM\staging).
     */
    public static File resolveStagingDirectory() {
        File baseDir = resolveBaseDirectory();
        File stagingDir = new File(baseDir, "staging");
        if (!stagingDir.exists()) {
            stagingDir.mkdirs();
        }
        return stagingDir;
    }

    /**
     * Resolves the diagnostic snapshots directory (%LOCALAPPDATA%\RupeeCRM\diagnostic-snapshots).
     */
    public static File resolveDiagnosticSnapshotsDirectory() {
        File baseDir = resolveBaseDirectory();
        File snapshotsDir = new File(baseDir, "diagnostic-snapshots");
        if (!snapshotsDir.exists()) {
            snapshotsDir.mkdirs();
        }
        return snapshotsDir;
    }
}
