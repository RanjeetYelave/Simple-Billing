package com.billing.simple.billsoft.util;

import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.FirmDetails;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility for Indian GST State Code resolution, Place of Supply determination,
 * and Inter-State vs. Intra-State tax classification.
 */
public final class GstStateUtils {

    private GstStateUtils() {}

    public static final String DEFAULT_STATE_CODE = "27"; // Maharashtra (default store state)

    private static final Map<String, String> STATE_NAME_TO_CODE;
    private static final Map<String, String> CODE_TO_STATE_NAME;

    static {
        Map<String, String> nameMap = new HashMap<>();
        Map<String, String> codeMap = new HashMap<>();

        register(nameMap, codeMap, "01", "JAMMU AND KASHMIR", "JK");
        register(nameMap, codeMap, "02", "HIMACHAL PRADESH", "HP");
        register(nameMap, codeMap, "03", "PUNJAB", "PB");
        register(nameMap, codeMap, "04", "CHANDIGARH", "CH");
        register(nameMap, codeMap, "05", "UTTARAKHAND", "UK");
        register(nameMap, codeMap, "06", "HARYANA", "HR");
        register(nameMap, codeMap, "07", "DELHI", "DL");
        register(nameMap, codeMap, "08", "RAJASTHAN", "RJ");
        register(nameMap, codeMap, "09", "UTTAR PRADESH", "UP");
        register(nameMap, codeMap, "10", "BIHAR", "BR");
        register(nameMap, codeMap, "11", "SIKKIM", "SK");
        register(nameMap, codeMap, "12", "ARUNACHAL PRADESH", "AR");
        register(nameMap, codeMap, "13", "NAGALAND", "NL");
        register(nameMap, codeMap, "14", "MANIPUR", "MN");
        register(nameMap, codeMap, "15", "MIZORAM", "MZ");
        register(nameMap, codeMap, "16", "TRIPURA", "TR");
        register(nameMap, codeMap, "17", "MEGHALAYA", "ML");
        register(nameMap, codeMap, "18", "ASSAM", "AS");
        register(nameMap, codeMap, "19", "WEST BENGAL", "WB");
        register(nameMap, codeMap, "20", "JHARKHAND", "JH");
        register(nameMap, codeMap, "21", "ODISHA", "OR", "OD");
        register(nameMap, codeMap, "22", "CHHATTISGARH", "CG", "CT");
        register(nameMap, codeMap, "23", "MADHYA PRADESH", "MP");
        register(nameMap, codeMap, "24", "GUJARAT", "GJ");
        register(nameMap, codeMap, "26", "DADRA AND NAGAR HAVELI AND DAMAN AND DIU", "DN", "DD");
        register(nameMap, codeMap, "27", "MAHARASHTRA", "MH");
        register(nameMap, codeMap, "29", "KARNATAKA", "KA");
        register(nameMap, codeMap, "30", "GOA", "GA");
        register(nameMap, codeMap, "31", "LAKSHADWEEP", "LD");
        register(nameMap, codeMap, "32", "KERALA", "KL");
        register(nameMap, codeMap, "33", "TAMIL NADU", "TN");
        register(nameMap, codeMap, "34", "PUDUCHERRY", "PY");
        register(nameMap, codeMap, "35", "ANDAMAN AND NICOBAR ISLANDS", "AN");
        register(nameMap, codeMap, "36", "TELANGANA", "TS", "TG");
        register(nameMap, codeMap, "37", "ANDHRA PRADESH", "AP");
        register(nameMap, codeMap, "38", "LADAKH", "LA");

        STATE_NAME_TO_CODE = Collections.unmodifiableMap(nameMap);
        CODE_TO_STATE_NAME = Collections.unmodifiableMap(codeMap);
    }

    private static void register(Map<String, String> nameMap, Map<String, String> codeMap, String code, String primaryName, String... aliases) {
        nameMap.put(primaryName, code);
        codeMap.put(code, primaryName);
        for (String alias : aliases) {
            nameMap.put(alias, code);
        }
    }

    public static String normalizeStateCode(String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        if (trimmed.isEmpty()) return null;

        // Check if starts with 2 digits
        if (trimmed.length() >= 2 && Character.isDigit(trimmed.charAt(0)) && Character.isDigit(trimmed.charAt(1))) {
            return trimmed.substring(0, 2);
        }

        String upper = trimmed.toUpperCase();
        if (STATE_NAME_TO_CODE.containsKey(upper)) {
            return STATE_NAME_TO_CODE.get(upper);
        }

        // Check partial string matching against known state names
        for (Map.Entry<String, String> entry : STATE_NAME_TO_CODE.entrySet()) {
            if (upper.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return trimmed;
    }

    public static String getStateName(String code) {
        if (code == null) return "Unknown";
        String norm = normalizeStateCode(code);
        return CODE_TO_STATE_NAME.getOrDefault(norm, norm);
    }

    public static String resolveSellerStateCode(FirmDetails firm) {
        if (firm == null) return DEFAULT_STATE_CODE;
        if (firm.getGstin() != null && !firm.getGstin().isBlank()) {
            String code = normalizeStateCode(firm.getGstin());
            if (code != null && code.length() == 2) return code;
        }
        if (firm.getState() != null && !firm.getState().isBlank()) {
            String code = normalizeStateCode(firm.getState());
            if (code != null) return code;
        }
        return DEFAULT_STATE_CODE;
    }

    public static String resolvePlaceOfSupply(String requestPlaceOfSupply, Customer customer, String fallbackSellerState) {
        // 1. Explicit request place of supply
        if (requestPlaceOfSupply != null && !requestPlaceOfSupply.isBlank()) {
            String code = normalizeStateCode(requestPlaceOfSupply);
            if (code != null) return code;
        }

        // 2. Customer GSTIN state code
        if (customer != null && customer.getGstin() != null && !customer.getGstin().isBlank()) {
            String code = normalizeStateCode(customer.getGstin());
            if (code != null && code.length() == 2) return code;
        }

        // 3. Customer address state mapping
        if (customer != null && customer.getAddress() != null && !customer.getAddress().isBlank()) {
            String code = normalizeStateCode(customer.getAddress());
            if (code != null && code.length() == 2) return code;
        }

        // 4. Safe intra-state fallback to seller state
        return fallbackSellerState != null ? fallbackSellerState : DEFAULT_STATE_CODE;
    }

    public static boolean isInterState(String sellerStateCode, String placeOfSupply) {
        if (sellerStateCode == null || placeOfSupply == null) return false;
        String normSeller = normalizeStateCode(sellerStateCode);
        String normPos = normalizeStateCode(placeOfSupply);
        return normSeller != null && normPos != null && !normSeller.equalsIgnoreCase(normPos);
    }
}
