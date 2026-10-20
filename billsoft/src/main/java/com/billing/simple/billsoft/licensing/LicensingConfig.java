package com.billing.simple.billsoft.licensing;

import java.io.File;

/**
 * Licensing configuration constants and runtime environment defaults.
 */
public final class LicensingConfig {

    // Application identity constants
    public static final String PRODUCT_NAME = "RupeeCRM";
    public static final String DEFAULT_EDITION = "PRO";
    public static final int QR_VERSION = 1;

    // Default Master Ed25519 Public Key in X.509 Base64 format (44 bytes encoded)
    public static final String MASTER_PUBLIC_KEY_X509_BASE64 =
            "MCowBQYDK2VwAyEAtdG8eJgrX0L7jtx13wOGnqSeeID2B/aRv1+OZQ7v3C8=";

    // Default static GitHub registry raw URL base
    public static final String DEFAULT_REGISTRY_BASE_URL =
            "https://raw.githubusercontent.com/RanjeetYelave/license-registry/main";

    // Default GitHub REST API contents URL base (resilient against ISP blocks)
    public static final String DEFAULT_API_BASE_URL =
            "https://api.github.com/repos/RanjeetYelave/license-registry/contents";

    // Storage path directory name
    private static final String APP_DIR_NAME = ".rupeecrm";

    private LicensingConfig() {
    }

    /**
     * Resolves the authoritative application data storage directory (%LOCALAPPDATA%\RupeeCRM\data on Windows).
     */
    public static File getStorageDirectory() {
        return com.billing.simple.billsoft.util.DataDirectoryResolver.resolveDataDirectory();
    }

    public static String getBranch() {
        return System.getProperty("rupeecrm.licensing.branch", System.getenv().getOrDefault("RUPEECRM_LICENSING_BRANCH", "preprod"));
    }

    public static String getRegistryBaseUrl() {
        String configured = System.getProperty("rupeecrm.licensing.url", System.getenv("RUPEECRM_LICENSING_URL"));
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return "https://raw.githubusercontent.com/RanjeetYelave/license-registry/" + getBranch();
    }

    public static String getApiBaseUrl() {
        String configured = System.getProperty("rupeecrm.licensing.api.url", System.getenv("RUPEECRM_LICENSING_API_URL"));
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return "https://api.github.com/repos/RanjeetYelave/license-registry/contents";
    }

    public static String getLicenseAuthToken() {
        String prop = System.getProperty("rupeecrm.licensing.token");
        if (prop != null && !prop.isBlank()) {
            return prop.trim();
        }
        String env = System.getenv("RUPEECRM_LICENSE_GITHUB_TOKEN");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String legacyEnv = System.getenv("RUPEECRM_LICENSING_TOKEN");
        if (legacyEnv != null && !legacyEnv.isBlank()) {
            return legacyEnv.trim();
        }
        return null;
    }
}
