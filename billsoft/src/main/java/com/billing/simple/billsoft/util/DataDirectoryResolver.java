package com.billing.simple.billsoft.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Standardized runtime application data directory resolver across macOS, Windows, and Linux.
 * Dynamic user home resolution:
 *   - macOS:   ~/Library/Application Support/RupeeCRM
 *   - Windows: %APPDATA%\SimpleBilling (or ~/.simplebilling)
 *   - Linux:   ~/.rupeecrm
 *
 * Implements safe, idempotent one-time legacy migration from SimpleBilling to RupeeCRM on macOS.
 * Only migrates if the target database does NOT exist and the legacy database DOES exist.
 * Never overwrites existing target customer data.
 */
public final class DataDirectoryResolver {

    private static final Logger log = LoggerFactory.getLogger(DataDirectoryResolver.class);

    private DataDirectoryResolver() {
    }

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

        String os = System.getProperty("os.name").toLowerCase();
        File targetDir;
        File legacyDir = null;

        if (os.contains("mac")) {
            targetDir = new File(System.getProperty("user.home"), "Library/Application Support/RupeeCRM");
            legacyDir = new File(System.getProperty("user.home"), "Library/Application Support/SimpleBilling");
        } else if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isEmpty()) {
                targetDir = new File(appData, "SimpleBilling");
            } else {
                targetDir = new File(System.getProperty("user.home"), ".simplebilling");
            }
        } else {
            targetDir = new File(System.getProperty("user.home"), ".rupeecrm");
            legacyDir = new File(System.getProperty("user.home"), ".simplebilling");
        }

        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }

        // Safe One-Time Migration: Only if target database doesn't exist and legacy database DOES exist
        if (legacyDir != null && legacyDir.exists()) {
            migrateLegacyDataSafely(legacyDir, targetDir);
        }

        return targetDir;
    }

    public static String resolveDataDirectoryPath() {
        return resolveDataDirectory().getAbsolutePath();
    }

    private static void migrateLegacyDataSafely(File legacyDir, File targetDir) {
        File targetDb = new File(targetDir, "database.mv.db");
        File legacyDb = new File(legacyDir, "database.mv.db");

        if (!targetDb.exists() && legacyDb.exists()) {
            log.info("Detected legacy database at {}. Performing safe one-time migration to {}", legacyDir.getAbsolutePath(), targetDir.getAbsolutePath());
            try {
                Path sourcePath = legacyDir.toPath();
                Path targetPath = targetDir.toPath();

                Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        Path rel = sourcePath.relativize(dir);
                        Path dest = targetPath.resolve(rel.toString());
                        if (!Files.exists(dest)) {
                            Files.createDirectories(dest);
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Path rel = sourcePath.relativize(file);
                        Path dest = targetPath.resolve(rel.toString());
                        // Strict rule: never overwrite any file that already exists in target
                        if (!Files.exists(dest)) {
                            Files.copy(file, dest, StandardCopyOption.COPY_ATTRIBUTES);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
                log.info("Legacy data migration completed successfully.");
            } catch (Exception e) {
                log.warn("Non-fatal note: Legacy data migration encountered an issue: {}", e.getMessage());
            }
        }
    }
}
