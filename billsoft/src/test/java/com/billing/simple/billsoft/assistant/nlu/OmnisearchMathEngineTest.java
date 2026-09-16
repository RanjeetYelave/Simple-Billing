package com.billing.simple.billsoft.assistant.nlu;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OmnisearchMathEngineTest {

    private OmnisearchMathEngine mathEngine;

    @BeforeEach
    void setUp() {
        mathEngine = new OmnisearchMathEngine();
    }

    @Test
    void testBasicArithmetic() {
        assertThat(mathEngine.evaluate("250 + 750"))
                .isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(mathEngine.evaluate("1000 - 350"))
                .isEqualByComparingTo(new BigDecimal("650.00"));
        assertThat(mathEngine.evaluate("50 * 12"))
                .isEqualByComparingTo(new BigDecimal("600.00"));
        assertThat(mathEngine.evaluate("1000 / 4"))
                .isEqualByComparingTo(new BigDecimal("250.00"));
    }

    @Test
    void testPercentages() {
        assertThat(mathEngine.evaluate("17% of 5633"))
                .isEqualByComparingTo(new BigDecimal("957.61"));
        assertThat(mathEngine.evaluate("10% of 7900"))
                .isEqualByComparingTo(new BigDecimal("790.00"));
    }

    @Test
    void testGstCalculations() {
        assertThat(mathEngine.evaluate("5000 + 18% GST"))
                .isEqualByComparingTo(new BigDecimal("5900.00"));
        assertThat(mathEngine.evaluate("10000 plus 5% tax"))
                .isEqualByComparingTo(new BigDecimal("10500.00"));
    }

    @Test
    void testDiscountCalculations() {
        assertThat(mathEngine.evaluate("7900 less 10%"))
                .isEqualByComparingTo(new BigDecimal("7110.00"));
        assertThat(mathEngine.evaluate("2000 - 25% discount"))
                .isEqualByComparingTo(new BigDecimal("1500.00"));
    }

    @Test
    void testIndianUnits() {
        assertThat(mathEngine.evaluate("2.5 lakh + 50000"))
                .isEqualByComparingTo(new BigDecimal("300000.00"));
        assertThat(mathEngine.evaluate("1.2 crore - 20 lakh"))
                .isEqualByComparingTo(new BigDecimal("10000000.00"));
    }
}
