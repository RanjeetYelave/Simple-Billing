package com.billing.simple.billsoft.assistant.nlu;

import com.billing.simple.billsoft.assistant.dto.OmnisearchNluDTOs.ExtractedSlots;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic Slot Extraction Engine for amounts, currencies, date ranges, and entity token boundaries.
 */
@Component
public class OmnisearchSlotExtractor {

    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?i)(?:₹|rs\\.?|inr)?\\s*(\\d+(?:,\\d+)*(?:\\.\\d+)?)\\s*(?:lakh|crore|k)?");
    private static final Pattern INVOICE_CODE_PATTERN = Pattern.compile("(?i)\\b(INV-[A-Z0-9-]+|PO-[A-Z0-9-]+|#\\d+)\\b");
    private static final Pattern PERCENT_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:%|percent|pratishat|takke)");

    public ExtractedSlots extractSlots(String text) {
        ExtractedSlots slots = new ExtractedSlots();
        if (text == null || text.isBlank()) return slots;

        String query = text.trim();

        // 1. Extract Invoice / PO Code
        Matcher codeMatcher = INVOICE_CODE_PATTERN.matcher(query);
        if (codeMatcher.find()) {
            slots.setInvoiceNumber(codeMatcher.group(1));
        }

        // 2. Extract Percentage
        Matcher pctMatcher = PERCENT_PATTERN.matcher(query);
        if (pctMatcher.find()) {
            try {
                slots.setPercentage(new BigDecimal(pctMatcher.group(1)));
            } catch (Exception ignored) {}
        }

        // 3. Extract Amount
        extractAmount(query, slots);

        // 4. Extract Date Range (Indian timezone & fiscal defaults)
        extractDateRange(query, slots);

        // 5. Clean Entity Token (Candidate name token)
        extractRawEntityToken(query, slots);

        return slots;
    }

    private void extractAmount(String query, ExtractedSlots slots) {
        Pattern p = Pattern.compile("(?i)(?:₹|rs\\.?|inr)?\\s*(\\d+(?:,\\d+)*(?:\\.\\d+)?)\\s*(lakh|crore|k\\b|rupees|paise)?");
        Matcher m = p.matcher(query);
        while (m.find()) {
            String valStr = m.group(1);
            String unit = m.group(2);
            if (valStr != null && !valStr.isBlank()) {
                try {
                    String clean = valStr.replace(",", "");
                    BigDecimal amt = new BigDecimal(clean);
                    if (unit != null) {
                        String u = unit.toLowerCase();
                        if (u.contains("lakh")) amt = amt.multiply(new BigDecimal("100000"));
                        else if (u.contains("crore")) amt = amt.multiply(new BigDecimal("10000000"));
                        else if (u.contains("k")) amt = amt.multiply(new BigDecimal("1000"));
                    }
                    slots.setAmount(amt);
                    slots.setCurrency("INR");
                    break;
                } catch (Exception ignored) {}
            }
        }
    }

    private void extractDateRange(String query, ExtractedSlots slots) {
        String lower = query.toLowerCase();
        LocalDate now = LocalDate.now();

        if (lower.contains("today") || lower.contains("aaj") || lower.contains("aajcha")) {
            slots.setStartDate(now);
            slots.setEndDate(now);
            slots.setDateRangeLabel("Today");
        } else if (lower.contains("yesterday") || lower.contains("kal") || lower.contains("kalcha")) {
            LocalDate yest = now.minusDays(1);
            slots.setStartDate(yest);
            slots.setEndDate(yest);
            slots.setDateRangeLabel("Yesterday");
        } else if (lower.contains("this month") || lower.contains("is mahine") || lower.contains("ya mahinyat")) {
            slots.setStartDate(now.with(TemporalAdjusters.firstDayOfMonth()));
            slots.setEndDate(now.with(TemporalAdjusters.lastDayOfMonth()));
            slots.setDateRangeLabel("This Month");
        } else if (lower.contains("last month") || lower.contains("pichle mahine") || lower.contains("magil mahinyat")) {
            LocalDate prev = now.minusMonths(1);
            slots.setStartDate(prev.with(TemporalAdjusters.firstDayOfMonth()));
            slots.setEndDate(prev.with(TemporalAdjusters.lastDayOfMonth()));
            slots.setDateRangeLabel("Last Month");
        }
    }

    private void extractRawEntityToken(String query, ExtractedSlots slots) {
        // Remove common stopwords and domain action words
        String cleaned = query.replaceAll("(?i)\\b(show|get|find|search|check|receive|payment|paid|give|advance|salary|pagar|attendance|hajiri|stock|maal|create|new|bill|invoice|expenses|kharcha|record|balance|outstanding|dues|udhari|baki|kitna|kitni|hai|ahe|kiti|batao|dakhva|today|yesterday|this|month|for|of|ka|ki|ke|ko|se|from|to|rupees|rs|inr|₹|lakh|crore|k|\\d+(?:\\.\\d+)?)\\b", " ")
                .replaceAll("[^a-zA-Z0-9\\u0900-\\u097F\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (!cleaned.isBlank() && cleaned.length() > 1) {
            slots.setRawEntityToken(cleaned);
        }
    }
}
