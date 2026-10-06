package com.billing.simple.billsoft.recovery;

import com.billing.simple.billsoft.util.DataDirectoryResolver;

import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Strict path traversal prevention and recovery ID resolver.
 * Ensures recovery IDs strictly map to safe, approved files inside the application data directory.
 */
public final class DiagnosticRecoveryPathValidator {

    private static final Pattern SAFE_FILENAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]+$");

    private DiagnosticRecoveryPathValidator() {
    }

    /**
     * Resolves and strictly validates the local filesystem target for a given recovery ID.
     * Returns null if the recovery ID represents OFFSITE (cloud) storage.
     * Throws IllegalArgumentException on any invalid format, traversal attempt, or path violation.
     */
    public static File resolveAndValidateFile(String recoveryId) {
        if (recoveryId == null || recoveryId.trim().isEmpty()) {
            throw new IllegalArgumentException("Recovery ID must not be blank");
        }

        String id = recoveryId.trim();

        if ("OFFSITE".equalsIgnoreCase(id)) {
            return null; // Valid offsite identifier, handled via DataProtectionService
        }

        if ("AUTO_LATEST".equalsIgnoreCase(id) || "AUTOBACKUP:autobackup_latest.json".equalsIgnoreCase(id)) {
            File baseDir = DataDirectoryResolver.resolveBackupsDirectory();
            File target = new File(baseDir, "autobackup_latest.json");
            validateCanonicalContainment(baseDir, target);
            validateRegularFile(target);
            return target;
        }

        if (id.startsWith("AUTOBACKUP:")) {
            String filename = id.substring("AUTOBACKUP:".length()).trim();
            validateFilename(filename);
            if (!filename.toLowerCase().endsWith(".json")) {
                throw new IllegalArgumentException("Auto backup must be a .json file: " + filename);
            }
            File baseDir = DataDirectoryResolver.resolveBackupsDirectory();
            File target = new File(baseDir, filename);
            validateCanonicalContainment(baseDir, target);
            validateRegularFile(target);
            return target;
        }

        if (id.startsWith("DIAGNOSTIC:")) {
            String filename = id.substring("DIAGNOSTIC:".length()).trim();
            validateFilename(filename);

            String lower = filename.toLowerCase();
            if (!lower.endsWith(".json") && !lower.endsWith(".zip")) {
                throw new IllegalArgumentException("Unsupported snapshot file extension: " + filename);
            }

            File baseDir = DataDirectoryResolver.resolveDiagnosticSnapshotsDirectory();
            File target = new File(baseDir, filename);
            validateCanonicalContainment(baseDir, target);
            validateRegularFile(target);
            return target;
        }

        if (id.startsWith("H2:")) {
            String filename = id.substring("H2:".length()).trim();
            validateFilename(filename);

            if (!filename.toLowerCase().endsWith(".zip")) {
                throw new IllegalArgumentException("H2 snapshot must be a .zip file: " + filename);
            }

            File baseDir = DataDirectoryResolver.resolveDiagnosticSnapshotsDirectory();
            File target = new File(baseDir, filename);
            validateCanonicalContainment(baseDir, target);
            validateRegularFile(target);
            return target;
        }

        throw new IllegalArgumentException("Unrecognized recovery ID format: " + id);
    }

    private static void validateFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Snapshot filename cannot be empty");
        }
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\") || filename.contains("\0")) {
            throw new IllegalArgumentException("Path traversal characters prohibited in filename: " + filename);
        }
        if (!SAFE_FILENAME_PATTERN.matcher(filename).matches()) {
            throw new IllegalArgumentException("Filename contains illegal characters: " + filename);
        }
    }

    private static void validateRegularFile(File target) {
        if (target.exists() && target.isDirectory()) {
            throw new IllegalArgumentException("Target is a directory, not a valid backup file: " + target.getName());
        }
    }

    private static void validateCanonicalContainment(File baseDir, File target) {
        try {
            if (!baseDir.exists()) {
                baseDir.mkdirs();
            }
            java.nio.file.Path basePath = baseDir.toPath().toRealPath();
            java.nio.file.Path targetPath;
            if (target.exists()) {
                targetPath = target.toPath().toRealPath();
            } else {
                targetPath = target.toPath().toAbsolutePath().normalize();
            }

            if (!targetPath.startsWith(basePath)) {
                throw new SecurityException("Access denied: Target path escapes allowed directory boundary: " + targetPath);
            }
        } catch (IOException e) {
            throw new SecurityException("Failed to verify path safety: " + e.getMessage(), e);
        }
    }
}
