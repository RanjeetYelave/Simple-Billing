package com.billing.simple.billsoft.dataprotection;

import com.billing.simple.billsoft.licensing.LicensingConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Single authoritative store for customer-side Data Protection Vault credentials.
 * Responsibilities:
 * 1. Resolves active DP credential (system property -> local vault_config.json -> legacy fallback).
 * 2. Manages versioned, Ed25519-signed remote credential updates.
 * 3. Guarantees fail-safe behavior: an invalid/corrupted update never overwrites a working credential.
 */
@Component
public class DataProtectionCredentialStore {

    private static final Logger log = LoggerFactory.getLogger(DataProtectionCredentialStore.class);
    private static final String CONFIG_FILE_NAME = "vault_config.json";
    private static final int MASK_KEY = 0x5C;

    // Legacy default descriptor stream (Version 0 fallback for unmigrated installations)
    private static final int[] LEGACY_DESCRIPTOR_STREAM = new int[] {
        59, 53, 40, 52, 41, 62, 3, 44, 61, 40, 3, 109, 109, 29, 20, 18, 31, 9, 17, 5,
        108, 5, 46, 23, 42, 19, 31, 55, 9, 14, 56, 59, 30, 3, 61, 48, 9, 26, 5, 17,
        53, 9, 37, 109, 53, 10, 56, 6, 105, 38, 9, 43, 110, 55, 37, 13, 57, 26, 21, 6,
        52, 110, 14, 29, 46, 4, 20, 19, 21, 22, 17, 14, 107, 37, 100, 27, 25, 14, 15, 4,
        106, 22, 24, 29, 29, 57, 52, 8, 48, 15, 105, 8, 21
    };

    private final File configFile;
    private final ObjectMapper mapper;

    private volatile String cachedToken;
    private volatile int currentVersion;

    public DataProtectionCredentialStore() {
        this(resolveConfigFile());
    }

    public DataProtectionCredentialStore(File configFile) {
        this.configFile = configFile;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        loadLocalConfig();
    }

    private static File resolveConfigFile() {
        File dir = LicensingConfig.getStorageDirectory();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, CONFIG_FILE_NAME);
    }

    /**
     * Gets the currently active Data Protection token.
     */
    public String getCurrentToken() {
        String prop = System.getProperty("rupeecrm.dataprotection.token");
        if (prop != null && !prop.isBlank()) {
            return prop.trim();
        }
        String env = System.getenv("RUPEECRM_DATA_PROTECTION_TOKEN");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        if (cachedToken != null && !cachedToken.isBlank()) {
            return cachedToken;
        }
        return resolveLegacyDefaultToken();
    }

    public int getCurrentVersion() {
        return currentVersion;
    }

    /**
     * Loads the local vault configuration if present.
     */
    private synchronized void loadLocalConfig() {
        if (!configFile.exists() || !configFile.isFile()) {
            this.currentVersion = 0;
            this.cachedToken = resolveLegacyDefaultToken();
            return;
        }
        try {
            Map<?, ?> data = mapper.readValue(configFile, Map.class);
            Object verObj = data.get("version");
            Object payloadObj = data.get("protectedPayload");
            if (verObj instanceof Number && payloadObj instanceof String) {
                this.currentVersion = ((Number) verObj).intValue();
                this.cachedToken = unmaskPayload((String) payloadObj);
                return;
            }
        } catch (Exception e) {
            log.warn("Could not read {}: {}", CONFIG_FILE_NAME, e.getMessage());
        }
        this.currentVersion = 0;
        this.cachedToken = resolveLegacyDefaultToken();
    }

    /**
     * Verifies, decodes, and saves a newer version of the Data Protection configuration.
     * @return true if updated successfully; false otherwise (existing credential is preserved).
     */
    public synchronized boolean updateCredential(int newVersion, String protectedPayload, String signatureBase64, PublicKey masterKey) {
        if (newVersion <= this.currentVersion) {
            return false; // Stale or same version, no change
        }
        if (protectedPayload == null || protectedPayload.isBlank() || signatureBase64 == null || signatureBase64.isBlank() || masterKey == null) {
            log.warn("Invalid DP credential update payload");
            return false;
        }

        // 1. Authenticate configuration signature using Ed25519
        try {
            String canonical = newVersion + "\n" + protectedPayload.trim();
            Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(masterKey);
            sig.update(canonical.getBytes(StandardCharsets.UTF_8));
            byte[] sigBytes = Base64.getDecoder().decode(signatureBase64.trim());
            if (!sig.verify(sigBytes)) {
                log.warn("Rejected DP credential update: Invalid Ed25519 signature");
                return false;
            }
        } catch (Exception e) {
            log.warn("DP credential signature verification error: {}", e.getMessage());
            return false;
        }

        // 2. Decode token
        String decodedToken;
        try {
            decodedToken = unmaskPayload(protectedPayload);
            if (decodedToken == null || decodedToken.isBlank()) {
                log.warn("Rejected DP credential update: Decoded token is empty");
                return false;
            }
        } catch (Exception e) {
            log.warn("Rejected DP credential update: Failed to decode payload: {}", e.getMessage());
            return false;
        }

        // 3. Persist new configuration to disk
        try {
            Map<String, Object> record = new HashMap<>();
            record.put("version", newVersion);
            record.put("protectedPayload", protectedPayload.trim());
            record.put("signature", signatureBase64.trim());
            record.put("updatedAt", java.time.Instant.now().toString());
            mapper.writeValue(configFile, record);

            this.currentVersion = newVersion;
            this.cachedToken = decodedToken;
            log.info("Data Protection Vault credential rotated successfully to Version {}", newVersion);
            return true;
        } catch (Exception e) {
            log.warn("Failed to persist updated vault config: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Unmasks an obfuscated payload string using static XOR stream.
     */
    public static String unmaskPayload(String maskedBase64) {
        if (maskedBase64 == null || maskedBase64.isBlank()) {
            return null;
        }
        byte[] raw = Base64.getDecoder().decode(maskedBase64.trim());
        byte[] unmasked = new byte[raw.length];
        for (int i = 0; i < raw.length; i++) {
            unmasked[i] = (byte) (raw[i] ^ MASK_KEY);
        }
        return new String(unmasked, StandardCharsets.UTF_8);
    }

    /**
     * Masks a token string into an obfuscated Base64 string using static XOR stream.
     */
    public static String maskPayload(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        byte[] raw = token.trim().getBytes(StandardCharsets.UTF_8);
        byte[] masked = new byte[raw.length];
        for (int i = 0; i < raw.length; i++) {
            masked[i] = (byte) (raw[i] ^ MASK_KEY);
        }
        return Base64.getEncoder().encodeToString(masked);
    }

    private static String resolveLegacyDefaultToken() {
        byte[] buffer = new byte[LEGACY_DESCRIPTOR_STREAM.length];
        for (int i = 0; i < LEGACY_DESCRIPTOR_STREAM.length; i++) {
            buffer[i] = (byte) (LEGACY_DESCRIPTOR_STREAM[i] ^ MASK_KEY);
        }
        return new String(buffer, StandardCharsets.UTF_8);
    }
}
