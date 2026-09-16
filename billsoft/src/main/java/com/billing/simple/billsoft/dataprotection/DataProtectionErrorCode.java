package com.billing.simple.billsoft.dataprotection;

/**
 * Standard internal error codes for Cloud Data Protection Vault operations.
 * Exposes clean, diagnostic-friendly error codes to the user without leaking
 * secrets, internal repository URLs, or raw API response structures.
 */
public enum DataProtectionErrorCode {
    DP_001("DP-001", "Local backup file not found. Please run a local backup first"),
    DP_002("DP-002", "Cloud vault authentication failed. Please contact support"),
    DP_003("DP-003", "Cloud backup vault unavailable. Please contact support"),
    DP_004("DP-004", "Network connection to cloud vault timed out. Please check your internet connection"),
    DP_005("DP-005", "Backup encryption failed. Please verify license status"),
    DP_006("DP-006", "Cloud upload rate limit reached. Automatic sync will retry later"),
    DP_007("DP-007", "Cloud backup service temporarily unavailable. Please try again later"),
    DP_008("DP-008", "Data Protection is not active for the current license");

    private final String code;
    private final String userMessage;

    DataProtectionErrorCode(String code, String userMessage) {
        this.code = code;
        this.userMessage = userMessage;
    }

    public String getCode() {
        return code;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public String formatMessage() {
        return userMessage + " (" + code + ").";
    }

    public static DataProtectionErrorCode fromHttpStatus(int httpStatus) {
        switch (httpStatus) {
            case 401:
            case 403:
                return DP_002;
            case 404:
                return DP_003;
            case 429:
                return DP_006;
            case 500:
            case 502:
            case 503:
            case 504:
                return DP_007;
            default:
                if (httpStatus >= 400 && httpStatus < 500) return DP_002;
                return DP_007;
        }
    }
}
