package com.billing.simple.billsoft.licensing;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;

/**
 * Machine identity generator and storage.
 * Generates a persistent, random 16-character Crockford Base32 ID formatted as XXXX-XXXX-XXXX-XXXX.
 */
public class MachineIdentity {

    private static final String CROCKFORD_BASE32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final String IDENTITY_FILE_NAME = "mid.dat";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final File identityFile;
    private String cachedMachineId;

    public MachineIdentity() {
        this(new File(LicensingConfig.getStorageDirectory(), IDENTITY_FILE_NAME));
    }

    public MachineIdentity(File identityFile) {
        this.identityFile = identityFile;
    }

    /**
     * Gets the persistent Machine ID.
     * Reads from disk if present, otherwise generates a new random ID and saves it.
     */
    public synchronized String getMachineId() {
        if (cachedMachineId != null && isValidFormat(cachedMachineId)) {
            return cachedMachineId;
        }

        if (identityFile.exists() && identityFile.isFile()) {
            try {
                String content = Files.readString(identityFile.toPath(), StandardCharsets.UTF_8).trim();
                if (isValidFormat(content)) {
                    cachedMachineId = content;
                    return cachedMachineId;
                }
            } catch (IOException ignored) {
            }
        }

        // Generate and persist new ID
        String newId = generateRandomMachineId();
        try {
            if (identityFile.getParentFile() != null && !identityFile.getParentFile().exists()) {
                identityFile.getParentFile().mkdirs();
            }
            Files.writeString(identityFile.toPath(), newId, StandardCharsets.UTF_8);
            cachedMachineId = newId;
        } catch (IOException e) {
            cachedMachineId = newId; // Keep in memory if write fails
        }

        return cachedMachineId;
    }

    /**
     * Generates a random 16-character Crockford Base32 ID grouped with hyphens (XXXX-XXXX-XXXX-XXXX).
     */
    public static String generateRandomMachineId() {
        StringBuilder raw = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            int idx = RANDOM.nextInt(CROCKFORD_BASE32.length());
            raw.append(CROCKFORD_BASE32.charAt(idx));
        }

        return raw.substring(0, 4) + "-" +
                raw.substring(4, 8) + "-" +
                raw.substring(8, 12) + "-" +
                raw.substring(12, 16);
    }

    /**
     * Validates that the ID matches the expected 19-char format (16 Base32 chars + 3 hyphens).
     */
    public static boolean isValidFormat(String machineId) {
        if (machineId == null || machineId.length() != 19) {
            return false;
        }
        String clean = machineId.replace("-", "").toUpperCase();
        if (clean.length() != 16) {
            return false;
        }
        for (int i = 0; i < clean.length(); i++) {
            if (CROCKFORD_BASE32.indexOf(clean.charAt(i)) == -1) {
                return false;
            }
        }
        return machineId.charAt(4) == '-' && machineId.charAt(9) == '-' && machineId.charAt(14) == '-';
    }
}
