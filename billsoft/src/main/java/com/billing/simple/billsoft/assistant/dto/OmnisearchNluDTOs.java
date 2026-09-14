package com.billing.simple.billsoft.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OmnisearchNluDTOs {

    public enum CapabilityGroup {
        QUICK_HELP,
        ACTION,
        SPECIAL,
        FALLBACK
    }

    public enum ExecutionType {
        READ_ONLY_QUERY,
        READ_ONLY_CALCULATION,
        UI_MODAL_ACTION,
        DETERMINISTIC_SPECIAL,
        FALLBACK_SEARCH
    }

    public enum EntityType {
        CUSTOMER,
        PARTY,
        PRODUCT,
        EMPLOYEE,
        INVOICE,
        EXPENSE
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NluQueryRequest {
        private String query;
        private Long firmId;
        private String clientContext;
        private Map<String, Object> extraParams;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NluEvaluationResponse {
        private String query;
        private String normalizedQuery;
        private String detectedLanguage;
        private String capabilityId;
        private CapabilityGroup group;
        private ExecutionType executionType;
        private Double intentConfidence;
        private Double entityConfidence;
        private Double compositeConfidence;
        private String executionStatus; // "EXECUTE", "DISAMBIGUATE", "FALLBACK"
        
        @Builder.Default
        private Map<String, Object> extractedSlots = new HashMap<>();
        
        @Builder.Default
        private List<ResolvedEntityCandidate> entityCandidates = new ArrayList<>();
        
        private Object actionContract;
        private Object quickHelpResult;
        private String directAnswerText;
        private String qrCodeBase64;
        
        @Builder.Default
        private List<String> suggestions = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResolvedEntityCandidate {
        private Long id;
        private String name;
        private EntityType type;
        private String phone;
        private String extraInfo;
        private Double matchScore;
        private Boolean exactMatch;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtractedSlots {
        private BigDecimal amount;
        private String currency;
        private BigDecimal percentage;
        private LocalDate startDate;
        private LocalDate endDate;
        private String dateRangeLabel;
        private String invoiceNumber;
        private String category;
        private String rawEntityToken;
        private Integer limit;
        private String mathExpression;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionContractPayload {
        private String actionId;
        private String targetModal; // e.g. "INVOICE_CREATE", "PAYMENT_RECEIVE", "EXPENSE_CREATE", "ADVANCE_RECORD"
        private String title;
        private String description;
        private Map<String, Object> prefillFields;
        private Boolean requiresUserConfirmation;
    }
}
