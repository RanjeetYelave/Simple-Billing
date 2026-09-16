package com.billing.simple.billsoft.assistant.nlu;

import com.billing.simple.billsoft.assistant.dto.OmnisearchNluDTOs.*;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Composite Multi-Factor Confidence & Ambiguity Evaluator.
 * Combines Intent Posterior, Entity Match Quality, Slot Completeness, and Ambiguity Penalties.
 */
@Component
public class OmnisearchConfidenceScorer {

    public static class EvaluationDecision {
        private final double compositeScore;
        private final String executionStatus; // "EXECUTE", "DISAMBIGUATE", "FALLBACK"
        private final boolean isAmbiguous;

        public EvaluationDecision(double compositeScore, String executionStatus, boolean isAmbiguous) {
            this.compositeScore = compositeScore;
            this.executionStatus = executionStatus;
            this.isAmbiguous = isAmbiguous;
        }

        public double getCompositeScore() { return compositeScore; }
        public String getExecutionStatus() { return executionStatus; }
        public boolean isAmbiguous() { return isAmbiguous; }
    }

    public EvaluationDecision evaluate(
            String capabilityId,
            double intentConfidence,
            List<ResolvedEntityCandidate> candidates,
            ExtractedSlots slots,
            boolean isDeterministic
    ) {
        if (isDeterministic) {
            return new EvaluationDecision(1.0, "EXECUTE", false);
        }

        double entityScore = 0.0;
        boolean hasExactEntity = false;
        boolean isAmbiguous = false;

        if (candidates != null && !candidates.isEmpty()) {
            hasExactEntity = candidates.stream().anyMatch(ResolvedEntityCandidate::getExactMatch);
            if (hasExactEntity) {
                entityScore = 1.0;
            } else if (candidates.size() == 1) {
                entityScore = 0.95;
            } else {
                // Multiple candidates with no exact match -> Ambiguous
                entityScore = 0.4;
                isAmbiguous = true;
            }
        } else if (slots != null && (slots.getRawEntityToken() == null || slots.getRawEntityToken().isBlank())) {
            // General capability query not requiring specific entity (e.g. "today sales", "monthly expenses", "record expense of 5000")
            entityScore = 0.95;
        } else if (slots != null && slots.getRawEntityToken() != null && !slots.getRawEntityToken().isBlank()) {
            entityScore = 0.3;
        }

        double slotScore = (slots != null && (slots.getAmount() != null || slots.getDateRangeLabel() != null || slots.getInvoiceNumber() != null)) ? 0.9 : 0.7;
        double specificityScore = 0.8;

        // Composite formula
        double composite = (0.35 * intentConfidence) + (0.30 * entityScore) + (0.20 * slotScore) + (0.15 * specificityScore);

        if (isAmbiguous) {
            composite -= 0.30;
        }

        String status;
        if (isAmbiguous) {
            status = "DISAMBIGUATE";
        } else if (composite >= 0.65) {
            status = "EXECUTE";
        } else if (composite >= 0.40) {
            status = "DISAMBIGUATE";
        } else {
            status = "FALLBACK";
        }

        return new EvaluationDecision(Math.max(0.0, Math.min(1.0, composite)), status, isAmbiguous);
    }
}
