package com.billing.simple.billsoft.telegram.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Shared utility for Telegram-to-WhatsApp document handoff.
 *
 * Responsibilities:
 * - Strict eligibility checking based on document type and operation mode
 * - Phone number validation and normalization into international format (wa.me compatible)
 * - Safe URL generation without logging private data
 * - Human-readable phone and document formatting
 */
public final class TelegramWhatsAppUtils {

    private static final Pattern DIGITS_ONLY = Pattern.compile("^\\d+$");

    private TelegramWhatsAppUtils() {}

    /**
     * Checks whether an operation and document type are eligible for WhatsApp handoff.
     *
     * Explicit exclusions:
     * - Firm Financial Summary (STMT_FIRM_PDF)
     * - Forecast & Replenishment (STMT_FORECAST_PDF)
     * - Stock Movement Statement (STMT_STOCK_PDF)
     * - Bulk Monthly Payroll (operationType == "BULK")
     */
    public static boolean isEligible(String docType, String operationType) {
        if ("BULK".equalsIgnoreCase(operationType)) {
            return false;
        }
        if (docType == null) {
            return false;
        }

        String dt = docType.toUpperCase();
        if ("STMT_FIRM_PDF".equals(dt) || "STMT_FORECAST_PDF".equals(dt) || "STMT_STOCK_PDF".equals(dt)) {
            return false;
        }

        return "INVOICE_PDF".equals(dt)
                || "ESTIMATE_PDF".equals(dt)
                || "PO_PDF".equals(dt)
                || "STMT_CUST_PDF".equals(dt)
                || "STMT_PARTY_PDF".equals(dt)
                || "PAYSLIP_PDF".equals(dt);
    }

    /**
     * Phone normalization result containing normalized digits, human display string, and error if invalid.
     */
    public static class PhoneResult {
        private final boolean valid;
        private final String normalizedNumber; // Digits only with international country code (no leading +)
        private final String humanFormatted;    // e.g. +91 98765 43210
        private final String errorMessage;

        public PhoneResult(boolean valid, String normalizedNumber, String humanFormatted, String errorMessage) {
            this.valid = valid;
            this.normalizedNumber = normalizedNumber;
            this.humanFormatted = humanFormatted;
            this.errorMessage = errorMessage;
        }

        public static PhoneResult success(String normalizedNumber, String humanFormatted) {
            return new PhoneResult(true, normalizedNumber, humanFormatted, null);
        }

        public static PhoneResult failure(String errorMessage) {
            return new PhoneResult(false, null, null, errorMessage);
        }

        public boolean isValid() { return valid; }
        public String getNormalizedNumber() { return normalizedNumber; }
        public String getHumanFormatted() { return humanFormatted; }
        public String getErrorMessage() { return errorMessage; }
    }

    /**
     * Normalizes a raw phone string into an international number suitable for wa.me links.
     *
     * Rules:
     * 1. Strips spaces, dashes, brackets, periods, and leading '+' or '00'.
     * 2. Rejects any alphabetic or illegal characters.
     * 3. For 10-digit numbers starting with 6-9 (standard Indian mobile format), prepends '91'.
     * 4. For 11-digit numbers starting with '0' followed by 6-9, strips '0' and prepends '91'.
     * 5. For 12-digit numbers already starting with '91' and 6-9, preserves without double-prepending.
     * 6. For international numbers starting with '+', preserves country code without adding '91'.
     * 7. Rejects ambiguous 10-digit numbers starting with 1-5 without an explicit country code.
     * 8. Rejects numbers shorter than 10 digits or longer than 15 digits (ITU-T E.164).
     */
    public static PhoneResult normalizePhone(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            return PhoneResult.failure("Phone number is required.");
        }

        String trimmed = rawPhone.trim();
        boolean hadLeadingPlus = trimmed.startsWith("+");

        // Clean out common formatting characters: spaces, hyphens, brackets, dots
        String cleaned = trimmed.replaceAll("[\\s\\-\\(\\)\\.]", "");

        if (cleaned.startsWith("+")) {
            cleaned = cleaned.substring(1);
            hadLeadingPlus = true;
        } else if (cleaned.startsWith("00")) {
            cleaned = cleaned.substring(2);
            hadLeadingPlus = true;
        }

        if (cleaned.isEmpty() || !DIGITS_ONLY.matcher(cleaned).matches()) {
            return PhoneResult.failure("Phone number contains invalid characters. Only digits and formatting symbols (+, -, space) are allowed.");
        }

        int len = cleaned.length();
        if (len < 10) {
            return PhoneResult.failure("Phone number is too short (minimum 10 digits required).");
        }
        if (len > 15) {
            return PhoneResult.failure("Phone number exceeds the international limit of 15 digits.");
        }

        String normalized;

        if (len == 10) {
            char first = cleaned.charAt(0);
            if (first >= '6' && first <= '9') {
                // Standard Indian mobile number
                normalized = "91" + cleaned;
            } else if (hadLeadingPlus) {
                // Explicit international 10-digit number (e.g. +1 415 555 2671)
                normalized = cleaned;
            } else {
                // Ambiguous 10-digit number
                return PhoneResult.failure("Ambiguous mobile number. For Indian numbers, please enter 10 digits starting with 6-9. For international numbers, please include country code with a leading '+' (e.g. +1 415 555 2671).");
            }
        } else if (len == 11 && cleaned.startsWith("0")) {
            char second = cleaned.charAt(1);
            if (second >= '6' && second <= '9') {
                normalized = "91" + cleaned.substring(1);
            } else if (hadLeadingPlus) {
                normalized = cleaned;
            } else {
                return PhoneResult.failure("Invalid mobile prefix. Please enter a valid number or include international country code.");
            }
        } else if (len == 12 && cleaned.startsWith("91")) {
            char third = cleaned.charAt(2);
            if (third >= '6' && third <= '9') {
                normalized = cleaned; // Already international Indian format
            } else if (hadLeadingPlus) {
                normalized = cleaned;
            } else {
                normalized = cleaned;
            }
        } else {
            // 11 to 15 digits with or without plus
            normalized = cleaned;
        }

        // Format human display string
        String human;
        if (normalized.startsWith("91") && normalized.length() == 12) {
            human = "+91 " + normalized.substring(2, 7) + " " + normalized.substring(7);
        } else {
            human = "+" + normalized;
        }

        return PhoneResult.success(normalized, human);
    }

    /**
     * Builds a WhatsApp wa.me link with URL-encoded message text.
     */
    public static String buildWhatsAppUrl(String normalizedPhone, String message) {
        if (normalizedPhone == null || normalizedPhone.isBlank()) {
            return null;
        }
        String cleanPhone = normalizedPhone.replaceAll("[^0-9]", "");
        String encodedMessage = message != null
                ? URLEncoder.encode(message, StandardCharsets.UTF_8).replace("+", "%20")
                : "";
        return "https://wa.me/" + cleanPhone + "?text=" + encodedMessage;
    }

    /**
     * Generates a prepared message identifying the exact document and recipient.
     */
    public static String buildPreparedMessage(String docType, String docNumber, String recipientName) {
        String cleanName = recipientName != null && !recipientName.isBlank() ? recipientName.trim() : "Valued Partner";
        String cleanNum = docNumber != null && !docNumber.isBlank() ? docNumber.trim() : "";

        if (docType == null) {
            return "Hello " + cleanName + ", please find the attached document.";
        }

        String dt = docType.toUpperCase();
        if ("INVOICE_PDF".equals(dt)) {
            return "Hello " + cleanName + ", please find invoice " + cleanNum + " attached.";
        } else if ("ESTIMATE_PDF".equals(dt)) {
            return "Hello " + cleanName + ", please find quotation " + cleanNum + " attached.";
        } else if ("PO_PDF".equals(dt)) {
            return "Hello " + cleanName + ", please find purchase order " + cleanNum + " attached.";
        } else if ("STMT_CUST_PDF".equals(dt)) {
            return "Hello " + cleanName + ", please find your account statement attached.";
        } else if ("STMT_PARTY_PDF".equals(dt)) {
            return "Hello " + cleanName + ", please find your vendor statement attached.";
        } else if ("PAYSLIP_PDF".equals(dt)) {
            return "Hello " + cleanName + ", please find your payslip" + (!cleanNum.isEmpty() ? " for " + cleanNum : "") + " attached.";
        }
        return "Hello " + cleanName + ", please find the attached document.";
    }

    /**
     * Gets a human-readable title for the document type and number.
     */
    public static String getDocumentDisplayLabel(String docType, String docNumber) {
        String cleanNum = docNumber != null && !docNumber.isBlank() ? docNumber.trim() : "";
        if (docType == null) return "Document";

        String dt = docType.toUpperCase();
        if ("INVOICE_PDF".equals(dt)) {
            return "Invoice " + cleanNum;
        } else if ("ESTIMATE_PDF".equals(dt)) {
            return "Quotation " + cleanNum;
        } else if ("PO_PDF".equals(dt)) {
            return "Purchase Order " + cleanNum;
        } else if ("STMT_CUST_PDF".equals(dt)) {
            return "Customer Statement";
        } else if ("STMT_PARTY_PDF".equals(dt)) {
            return "Vendor Statement";
        } else if ("PAYSLIP_PDF".equals(dt)) {
            return "Payslip " + cleanNum;
        }
        return "Document " + cleanNum;
    }
}
