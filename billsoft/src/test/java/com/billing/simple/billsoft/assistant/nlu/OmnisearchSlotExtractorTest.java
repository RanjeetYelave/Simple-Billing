package com.billing.simple.billsoft.assistant.nlu;

import com.billing.simple.billsoft.assistant.dto.OmnisearchNluDTOs.ExtractedSlots;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class OmnisearchSlotExtractorTest {

    private OmnisearchSlotExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new OmnisearchSlotExtractor();
    }

    @Test
    void testExtractAmountAndEntity() {
        ExtractedSlots slots = extractor.extractSlots("receive payment 5000 from Amit");
        assertThat(slots.getAmount()).isEqualByComparingTo(new BigDecimal("5000"));
        assertThat(slots.getCurrency()).isEqualTo("INR");
        assertThat(slots.getRawEntityToken()).isEqualTo("Amit");
    }

    @Test
    void testExtractIndianUnits() {
        ExtractedSlots slots = extractor.extractSlots("give advance 2.5 lakh to Suresh");
        assertThat(slots.getAmount()).isEqualByComparingTo(new BigDecimal("250000"));
        assertThat(slots.getRawEntityToken()).isEqualTo("Suresh");
    }

    @Test
    void testExtractDateRangeToday() {
        ExtractedSlots slots = extractor.extractSlots("show expenses today");
        assertThat(slots.getDateRangeLabel()).isEqualTo("Today");
        assertThat(slots.getStartDate()).isEqualTo(LocalDate.now());
        assertThat(slots.getEndDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void testExtractInvoiceCode() {
        ExtractedSlots slots = extractor.extractSlots("find bill INV-2026-001");
        assertThat(slots.getInvoiceNumber()).isEqualTo("INV-2026-001");
    }
}
