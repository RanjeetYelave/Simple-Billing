package com.billing.simple.billsoft.assistant.nlu;

import com.billing.simple.billsoft.assistant.dto.OmnisearchNluDTOs.*;
import com.billing.simple.billsoft.assistant.dto.OmnisearchQuickHelpDTOs.QuickHelpResponse;
import com.billing.simple.billsoft.assistant.service.OmnisearchQuickHelpService;
import com.billing.simple.billsoft.security.TenantContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * Authoritative OmniSearch NLU Orchestration Service.
 * Combines language detection, semantic concept normalization, deterministic arithmetic AST,
 * OpenNLP MaxEnt classification, capability-aware entity resolution, composite scoring, and contract generation.
 */
@Service
public class OmnisearchNluService {

    private final OmnisearchLanguageDetector languageDetector;
    private final OmnisearchSemanticNormalizer semanticNormalizer;
    private final OpenNlpIntentClassifier intentClassifier;
    private final OmnisearchSlotExtractor slotExtractor;
    private final OmnisearchMathEngine mathEngine;
    private final OmnisearchLocalQrService qrService;
    private final OmnisearchEntityResolver entityResolver;
    private final OmnisearchConfidenceScorer confidenceScorer;
    private final OmnisearchQuickHelpService quickHelpService;

    public OmnisearchNluService(
            OmnisearchLanguageDetector languageDetector,
            OmnisearchSemanticNormalizer semanticNormalizer,
            OpenNlpIntentClassifier intentClassifier,
            OmnisearchSlotExtractor slotExtractor,
            OmnisearchMathEngine mathEngine,
            OmnisearchLocalQrService qrService,
            OmnisearchEntityResolver entityResolver,
            OmnisearchConfidenceScorer confidenceScorer,
            OmnisearchQuickHelpService quickHelpService
    ) {
        this.languageDetector = languageDetector;
        this.semanticNormalizer = semanticNormalizer;
        this.intentClassifier = intentClassifier;
        this.slotExtractor = slotExtractor;
        this.mathEngine = mathEngine;
        this.qrService = qrService;
        this.entityResolver = entityResolver;
        this.confidenceScorer = confidenceScorer;
        this.quickHelpService = quickHelpService;
    }

    public NluEvaluationResponse evaluateQuery(String rawQuery) {
        Long firmId = TenantContext.getRequiredFirmId();
        if (rawQuery == null || rawQuery.isBlank()) {
            return NluEvaluationResponse.builder()
                    .query("")
                    .executionStatus("FALLBACK")
                    .group(CapabilityGroup.FALLBACK)
                    .build();
        }

        String query = rawQuery.trim();
        var detectedLang = languageDetector.detectLanguage(query);

        // 1. DETERMINISTIC PRE-FILTER: Semantic Arithmetic AST Parser
        if (mathEngine.isArithmeticExpression(query)) {
            try {
                BigDecimal result = mathEngine.evaluate(query);
                return NluEvaluationResponse.builder()
                        .query(query)
                        .normalizedQuery(query)
                        .detectedLanguage(detectedLang.name())
                        .capabilityId("SPEC_MATH_CALC")
                        .group(CapabilityGroup.SPECIAL)
                        .executionType(ExecutionType.DETERMINISTIC_SPECIAL)
                        .intentConfidence(1.0)
                        .compositeConfidence(1.0)
                        .executionStatus("EXECUTE")
                        .directAnswerText("₹ " + result.toPlainString())
                        .extractedSlots(Map.of("result", result, "expression", query))
                        .build();
            } catch (Exception ignored) {
                // If arithmetic parse fails, continue down NLU pipeline
            }
        }

        // 2. DETERMINISTIC PRE-FILTER: Offline UPI QR Code (e.g. "upi 900", "qr 1500")
        if (query.toLowerCase().matches("^(?:upi|qr|pay)\\s*(\\d+(?:\\.\\d+)?)$")) {
            String[] parts = query.split("\\s+");
            BigDecimal amt = new BigDecimal(parts[1]);
            String qrBase64 = qrService.generateUpiQrBase64("merchant@upi", "RupeeCRM Merchant", amt, "OmniSearch Quick Pay");
            return NluEvaluationResponse.builder()
                    .query(query)
                    .normalizedQuery(query)
                    .detectedLanguage(detectedLang.name())
                    .capabilityId("SPEC_PAY_UPI_QR")
                    .group(CapabilityGroup.SPECIAL)
                    .executionType(ExecutionType.DETERMINISTIC_SPECIAL)
                    .intentConfidence(1.0)
                    .compositeConfidence(1.0)
                    .executionStatus("EXECUTE")
                    .directAnswerText("Scan to pay ₹" + amt.toPlainString())
                    .qrCodeBase64(qrBase64)
                    .extractedSlots(Map.of("amount", amt))
                    .build();
        }

        // 3. SEMANTIC CONCEPT NORMALIZATION
        var normalized = semanticNormalizer.normalize(query);
        ExtractedSlots slots = slotExtractor.extractSlots(query);

        if (!normalized.getIsolatedEntityToken().isBlank()) {
            slots.setRawEntityToken(normalized.getIsolatedEntityToken());
        } else {
            slots.setRawEntityToken(null);
        }

        // 4. STATISTICAL NLU: OpenNLP MaxEnt Categorization
        var classification = intentClassifier.classify(normalized.getCanonicalString().isBlank() ? query : normalized.getCanonicalString());
        String capabilityId = classification.getTopCategory();
        double intentConf = classification.getConfidence();

        // High-confidence concept override if strong semantic concept is detected
        if (normalized.getRecognizedConcepts().contains("CONCEPT_SALARY")) {
            capabilityId = "QH_SALARY_STATEMENT";
            intentConf = 0.95;
        } else if (normalized.getRecognizedConcepts().contains("CONCEPT_ATTENDANCE")) {
            capabilityId = "QH_EMPLOYEE_ATTENDANCE";
            intentConf = 0.95;
        } else if (normalized.getRecognizedConcepts().contains("CONCEPT_ADVANCE")) {
            capabilityId = (slots.getAmount() != null) ? "ACT_RECORD_ADVANCE" : "QH_SALARY_STATEMENT";
            intentConf = 0.95;
        } else if (normalized.getRecognizedConcepts().contains("CONCEPT_OUTSTANDING")) {
            boolean isParty = query.toLowerCase().matches(".*(party|vendor|supplier|kharidi|purchase|advance\\s+traders).*");
            capabilityId = isParty ? "QH_PARTY_OUTSTANDING" : "QH_CUSTOMER_OUTSTANDING";
            intentConf = 0.95;
        } else if (normalized.getRecognizedConcepts().contains("CONCEPT_EXPENSE")) {
            capabilityId = (slots.getAmount() != null && query.toLowerCase().matches(".*(add|record|spent|likho).*")) ? "ACT_RECORD_EXPENSE" : "QH_EXPENSE_SUMMARY";
            intentConf = 0.95;
        } else if (normalized.getRecognizedConcepts().contains("CONCEPT_STOCK")) {
            capabilityId = "QH_STOCK_STATUS";
            intentConf = 0.95;
        } else if (normalized.getRecognizedConcepts().contains("CONCEPT_INVOICE")) {
            capabilityId = (query.toLowerCase().matches(".*(create|make|naya|new|banao).*")) ? "ACT_CREATE_INVOICE" : "QH_INVOICE_LOOKUP";
            intentConf = 0.95;
        } else if (normalized.getRecognizedConcepts().contains("CONCEPT_SALES")) {
            capabilityId = "QH_DAILY_SALES";
            intentConf = 0.95;
        } else if (normalized.getRecognizedConcepts().contains("CONCEPT_PROFILE")) {
            capabilityId = "QH_CUSTOMER_PROFILE";
            intentConf = 0.95;
        }

        // 5. CAPABILITY-AWARE ENTITY RESOLUTION
        List<ResolvedEntityCandidate> entityCandidates = Collections.emptyList();
        String entityTokenToResolve = slots.getRawEntityToken() != null ? slots.getRawEntityToken() : normalized.getIsolatedEntityToken();
        if (entityTokenToResolve != null && !entityTokenToResolve.isBlank()) {
            entityCandidates = entityResolver.resolve(firmId, capabilityId, entityTokenToResolve);
        }

        // 6. MULTI-FACTOR COMPOSITE SCORING
        var decision = confidenceScorer.evaluate(capabilityId, intentConf, entityCandidates, slots, false);

        CapabilityGroup group = resolveGroup(capabilityId);
        ExecutionType execType = resolveExecutionType(capabilityId);

        NluEvaluationResponse response = NluEvaluationResponse.builder()
                .query(query)
                .normalizedQuery(normalized.getCanonicalString())
                .detectedLanguage(detectedLang.name())
                .capabilityId(capabilityId)
                .group(group)
                .executionType(execType)
                .intentConfidence(intentConf)
                .compositeConfidence(decision.getCompositeScore())
                .executionStatus(decision.getExecutionStatus())
                .entityCandidates(entityCandidates)
                .extractedSlots(buildSlotsMap(slots))
                .build();

        // 7. CONTRACT DISPATCH
        if ("EXECUTE".equals(decision.getExecutionStatus())) {
            if (group == CapabilityGroup.QUICK_HELP) {
                String entityName = (entityCandidates.size() == 1) ? entityCandidates.get(0).getName() : (entityTokenToResolve != null ? entityTokenToResolve : "");
                Object qhResult = dispatchQuickHelp(capabilityId, entityName, slots);
                response.setQuickHelpResult(qhResult);
            } else if (group == CapabilityGroup.ACTION) {
                response.setActionContract(buildActionContract(capabilityId, slots, entityCandidates));
            }
        }

        return response;
    }

    private Object dispatchQuickHelp(String capabilityId, String entityName, ExtractedSlots slots) {
        if (capabilityId == null) return null;
        switch (capabilityId) {
            case "QH_CUSTOMER_OUTSTANDING":
            case "QH_CUSTOMER_PROFILE":
                return quickHelpService.getCustomerQuickHelp(entityName);
            case "QH_PARTY_OUTSTANDING":
                return quickHelpService.getVendorQuickHelp(entityName);
            case "QH_INVOICE_LOOKUP":
                String inv = slots.getInvoiceNumber() != null ? slots.getInvoiceNumber() : entityName;
                return quickHelpService.getInvoiceQuickHelp(inv);
            case "QH_EXPENSE_SUMMARY":
                return quickHelpService.getExpenseSummary(slots.getDateRangeLabel() != null ? slots.getDateRangeLabel().toLowerCase() : "this_month", slots.getCategory(), slots.getStartDate(), slots.getEndDate());
            case "QH_STOCK_STATUS":
                return quickHelpService.getInventoryQuickHelp(entityName, false);
            case "QH_EMPLOYEE_ATTENDANCE":
                return quickHelpService.getHrStaffQuickHelp(entityName);
            case "QH_SALARY_STATEMENT":
                return quickHelpService.getSalaryQuickHelp(entityName, slots.getDateRangeLabel());
            case "QH_DAILY_SALES":
                return quickHelpService.getSalesSummary(slots.getDateRangeLabel() != null ? slots.getDateRangeLabel().toLowerCase() : "today", slots.getStartDate(), slots.getEndDate());
            default:
                return quickHelpService.searchAllEntities(entityName);
        }
    }

    private CapabilityGroup resolveGroup(String capabilityId) {
        if (capabilityId == null) return CapabilityGroup.FALLBACK;
        if (capabilityId.startsWith("ACT_")) return CapabilityGroup.ACTION;
        if (capabilityId.startsWith("SPEC_")) return CapabilityGroup.SPECIAL;
        if (capabilityId.startsWith("QH_")) return CapabilityGroup.QUICK_HELP;
        return CapabilityGroup.FALLBACK;
    }

    private ExecutionType resolveExecutionType(String capabilityId) {
        if (capabilityId == null) return ExecutionType.FALLBACK_SEARCH;
        if (capabilityId.startsWith("ACT_")) return ExecutionType.UI_MODAL_ACTION;
        if (capabilityId.startsWith("SPEC_")) return ExecutionType.DETERMINISTIC_SPECIAL;
        if (capabilityId.startsWith("QH_")) return ExecutionType.READ_ONLY_CALCULATION;
        return ExecutionType.FALLBACK_SEARCH;
    }

    private Map<String, Object> buildSlotsMap(ExtractedSlots slots) {
        Map<String, Object> map = new HashMap<>();
        if (slots.getAmount() != null) map.put("amount", slots.getAmount());
        if (slots.getPercentage() != null) map.put("percentage", slots.getPercentage());
        if (slots.getDateRangeLabel() != null) map.put("dateRange", slots.getDateRangeLabel());
        if (slots.getInvoiceNumber() != null) map.put("invoiceNumber", slots.getInvoiceNumber());
        if (slots.getRawEntityToken() != null) map.put("entityToken", slots.getRawEntityToken());
        return map;
    }

    private ActionContractPayload buildActionContract(String capabilityId, ExtractedSlots slots, List<ResolvedEntityCandidate> candidates) {
        String entityName = candidates.size() == 1 ? candidates.get(0).getName() : (slots.getRawEntityToken() != null ? slots.getRawEntityToken() : "");
        Map<String, Object> prefill = new HashMap<>();
        if (slots.getAmount() != null) prefill.put("amount", slots.getAmount());
        if (!entityName.isBlank()) prefill.put("customerName", entityName);

        switch (capabilityId) {
            case "ACT_CREATE_INVOICE":
                return ActionContractPayload.builder()
                        .actionId("ACT_CREATE_INVOICE")
                        .targetModal("INVOICE_CREATE")
                        .title("➕ Create New Invoice")
                        .description("Pre-filling invoice for " + (entityName.isBlank() ? "Customer" : entityName))
                        .prefillFields(prefill)
                        .requiresUserConfirmation(true)
                        .build();
            case "ACT_RECEIVE_PAYMENT":
                return ActionContractPayload.builder()
                        .actionId("ACT_RECEIVE_PAYMENT")
                        .targetModal("PAYMENT_RECEIVE")
                        .title("💵 Receive Payment")
                        .description("Collect payment of ₹" + (slots.getAmount() != null ? slots.getAmount() : "0") + " from " + entityName)
                        .prefillFields(prefill)
                        .requiresUserConfirmation(true)
                        .build();
            case "ACT_RECORD_EXPENSE":
                return ActionContractPayload.builder()
                        .actionId("ACT_RECORD_EXPENSE")
                        .targetModal("EXPENSE_CREATE")
                        .title("📉 Record Expense")
                        .description("Add business expense voucher for ₹" + (slots.getAmount() != null ? slots.getAmount() : "0"))
                        .prefillFields(prefill)
                        .requiresUserConfirmation(true)
                        .build();
            case "ACT_RECORD_ADVANCE":
                return ActionContractPayload.builder()
                        .actionId("ACT_RECORD_ADVANCE")
                        .targetModal("ADVANCE_RECORD")
                        .title("💰 Record Staff Advance")
                        .description("Record salary advance of ₹" + (slots.getAmount() != null ? slots.getAmount() : "0") + " for " + entityName)
                        .prefillFields(prefill)
                        .requiresUserConfirmation(true)
                        .build();
            default:
                return ActionContractPayload.builder()
                        .actionId(capabilityId)
                        .targetModal("GENERIC_ACTION")
                        .title("⚡ OmniSearch Action")
                        .prefillFields(prefill)
                        .requiresUserConfirmation(true)
                        .build();
        }
    }
}
