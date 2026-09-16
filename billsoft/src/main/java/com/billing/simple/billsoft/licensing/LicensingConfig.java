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
            "MCowBQYDK2VwAyEAdmc4i0VRQ4Whs4OqCfxuOWSiiLQiiyp8VlTRhBTGRZo=";

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
     * Resolves the local application storage directory (~/.rupeecrm).
     * Creates the directory if it does not already exist.
     */
    public static File getStorageDirectory() {
        String userHome = System.getProperty("user.home");
        File dir = new File(userHome, APP_DIR_NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static String getRegistryBaseUrl() {
        return System.getProperty("rupeecrm.licensing.url", DEFAULT_REGISTRY_BASE_URL);
    }
}
