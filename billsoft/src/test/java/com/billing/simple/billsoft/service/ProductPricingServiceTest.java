package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.entities.CategoryPricingRule;
import com.billing.simple.billsoft.entities.PricingProfile;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.repo.CategoryPricingRuleRepository;
import com.billing.simple.billsoft.repo.PricingProfileRepository;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Focused unit test suite for ProductPricingService arithmetic operator enhancements:
 * Covers Addition (+), Subtraction (-), Multiplication (*), Division (/),
 * in both PERCENT and FIXED modes, with decimal values, zero values,
 * division by zero guards, edge cases, and backward compatibility.
 */
class ProductPricingServiceTest {

    @Mock
    private PricingProfileRepository profileRepo;

    @Mock
    private CategoryPricingRuleRepository categoryRuleRepo;

    @Mock
    private ProductRepository productRepo;

    @InjectMocks
    private ProductPricingService pricingService;

    private final Long FIRM_ID = 100L;
    private final Long PROFILE_ID = 1L;
    private PricingProfile activeProfile;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TenantContext.setCurrentFirmId(FIRM_ID);

        activeProfile = PricingProfile.builder()
                .id(PROFILE_ID)
                .firmId(FIRM_ID)
                .name("Wholesale")
                .code("WHOLESALE")
                .isActive(true)
                .isDefault(false)
                .build();

        when(profileRepo.findByIdAndFirmId(PROFILE_ID, FIRM_ID))
                .thenReturn(Optional.of(activeProfile));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Product createProduct(String category, String price) {
        Product p = new Product();
        p.setId(10L);
        p.setName("Test Product");
        p.setCategory(category);
        p.setPrice(price != null ? new BigDecimal(price) : null);
        p.setFirmId(FIRM_ID);
        return p;
    }

    private void mockRule(String category, String operator, String adjType, String adjValue) {
        CategoryPricingRule rule = CategoryPricingRule.builder()
                .id(99L)
                .firmId(FIRM_ID)
                .pricingProfileId(PROFILE_ID)
                .categoryName(category)
                .operator(operator)
                .adjustmentType(adjType)
                .adjustmentValue(adjValue != null ? new BigDecimal(adjValue) : null)
                .build();

        when(categoryRuleRepo.findByFirmIdAndCategoryNameIgnoreCaseAndPricingProfileId(
                eq(FIRM_ID), eq(category), eq(PROFILE_ID)))
                .thenReturn(Optional.of(rule));
    }

    // =========================================================================
    // 1. ADDITION (+) TESTS
    // =========================================================================

    @Test
    @DisplayName("Addition (+): PERCENT mode adds percentage of base price")
    void testAddition_Percent() {
        mockRule("Hardware", "+", "PERCENT", "20.00");
        Product p = createProduct("Hardware", "1000.00");

        BigDecimal effective = pricingService.calculateEffectivePrice(p, PROFILE_ID, FIRM_ID);
        assertEquals(new BigDecimal("1200.00"), effective);
    }

    @Test
    @DisplayName("Addition (+): PERCENT mode with decimal percentage")
    void testAddition_Percent_Decimal() {
        mockRule("Hardware", "+", "PERCENT", "12.50");
        Product p = createProduct("Hardware", "100.00");

        BigDecimal effective = pricingService.calculateEffectivePrice(p, PROFILE_ID, FIRM_ID);
        assertEquals(new BigDecimal("112.50"), effective);
    }

    @Test
    @DisplayName("Addition (+): FIXED mode adds rupee amount")
    void testAddition_Fixed() {
        mockRule("Hardware", "+", "FIXED", "25.75");
        Product p = createProduct("Hardware", "100.00");

        BigDecimal effective = pricingService.calculateEffectivePrice(p, PROFILE_ID, FIRM_ID);
        assertEquals(new BigDecimal("125.75"), effective);
    }

    @Test
    @DisplayName("Addition (+): Backward compatibility with negative values produces discount")
    void testAddition_BackwardCompatibility_NegativeValues() {
        mockRule("Hardware", "+", "PERCENT", "-20.00");
        Product p = createProduct("Hardware", "1000.00");

        BigDecimal effective = pricingService.calculateEffectivePrice(p, PROFILE_ID, FIRM_ID);
        assertEquals(new BigDecimal("800.00"), effective);
    }

    // =========================================================================
    // 2. SUBTRACTION (-) TESTS
    // =========================================================================

    @Test
    @DisplayName("Subtraction (-): PERCENT mode subtracts percentage of base price")
    void testSubtraction_Percent() {
        mockRule("Hardware", "-", "PERCENT", "20.00");
        Product p = createProduct("Hardware", "1000.00");

        BigDecimal effective = pricingService.calculateEffectivePrice(p, PROFILE_ID, FIRM_ID);
        assertEquals(new BigDecimal("800.00"), effective);
    }

    @Test
    @DisplayName("Subtraction (-): PERCENT mode with decimal percentage")
    void testSubtraction_Percent_Decimal() {
        mockRule("Hardware", "-", "PERCENT", "15.50");
        Product p = createProduct("Hardware", "100.00");

        BigDecimal effective = pricingService.calculateEffectivePrice(p, PROFILE_ID, FIRM_ID);
        assertEquals(new BigDecimal("84.50"), effective);
    }

    @Test
    @DisplayName("Subtraction (-): FIXED mode subtracts rupee amount")
    void testSubtraction_Fixed() {
        mockRule("Hardware", "-", "FIXED", "25.50");
        Product p = createProduct("Hardware", "100.00");

        BigDecimal effective = pricingService.calculateEffectivePrice(p, PROFILE_ID, FIRM_ID);
        assertEquals(new BigDecimal("74.50"), effective);
    }

    @Test
    @DisplayName("Subtraction (-): Result below zero is clamped to 0.00")
    void testSubtraction_ClampedToZero() {
        mockRule("Hardware", "-", "FIXED", "150.00");
        Product p = createProduct("Hardware", "100.00");

        BigDecimal effective = pricingService.calculateEffectivePrice(p, PROFILE_ID, FIRM_ID);
        assertEquals(new BigDecimal("0.00"), effective);
    }

    // =========================================================================
    // 3. MULTIPLICATION (*) TESTS
    // =========================================================================

    @Test
    @DisplayName("Multiplication (*): FIXED mode multiplies base price by configured factor")
    void testMultiplication_Fixed_Factor() {
        mockRule("Hardware", "*", "FIXED", "1.50");
        Product p = createProduct("Hardware", "100.00");

        BigDecimal effective = pricingService.calculateEffectivePrice(p, PROFILE_ID, FIRM_ID);
        assertEquals(new BigDecimal("150.00"), effective);
    }

    @Test
    @DisplayName("Multiplication (*): FIXED mode with factor less than 1")
    void testMultiplication_Fixed_DiscountFactor() {
        mockRule("Hardware", "*", "FIXED", "0.75");
        Product p = createProduct("Hardware", "100.00");

        BigDecimal effective = pricingService.calculateEffectivePrice(p, PROFILE_ID, FIRM_ID);
        assertEquals(new BigDecimal("75.00"), effective);
    }

    @Test
    @DisplayName("Multiplication (*): PERCENT mode applies percentage factor (e.g. 150% -> 1.5x)")
    void testMultiplication_Percent_Markup() {
        mockRule("Hardware", "*", "PERCENT", "150.00");
        Product p = createProduct("Hardware", "100.00");

        BigDecimal effective = pricingService.calculateEffectivePrice(p, PROFILE_ID, FIRM_ID);
        assertEquals(new BigDecimal("150.00"), effective);
    }

    @Test
    @DisplayName("Multiplication (*): PERCENT mode applies fractional percentage (e.g. 80% -> 0.8x)")
    void testMultiplication_Percent_Discount() {
        mockRule("Hardware", "*", "PERCENT", "80.00");
        Product p = createProduct("Hardware", "100.00");

        BigDecimal effective = pricingService.calculateEffectivePrice(p, PROFILE_ID, FIRM_ID);
        assertEquals(new BigDecimal("80.00"), effective);
    }

    @Test
    @DisplayName("Multiplication (*): Zero factor results in 0.00")
    void testMultiplication_Zero() {
        mockRule("Hardware", "*", "FIXED", "0.00");
        Product p = createProduct("Hardware", "100.00");

        BigDecimal effective = pricingService.calculateEffectivePrice(p, PROFILE_ID, FIRM_ID);
        assertEquals(new BigDecimal("0.00"), effective);
    }

    // =========================================================================
    // 4. DIVISION (/) TESTS
    // =========================================================================

    @Test
    @DisplayName("Division (/): FIXED mode divides base price by configured divisor")
    void testDivision_Fixed() {
        mockRule("Hardware", "/", "FIXED", "2.00");
        Product p = createProduct("Hardware", "100.00");

        BigDecimal effective = pricingService.calculateEffectivePrice(p, PROFILE_ID, FIRM_ID);
        assertEquals(new BigDecimal("50.00"), effective);
    }

    @Test
    @DisplayName("Division (/): FIXED mode handles repeating decimals with HALF_UP rounding (e.g. 100 / 3 = 33.33)")
    void testDivision_Fixed_RepeatingDecimal() {
        mockRule("Hardware", "/", "FIXED", "3.00");
        Product p = createProduct("Hardware", "100.00");

        BigDecimal effective = pricingService.calculateEffectivePrice(p, PROFILE_ID, FIRM_ID);
        assertEquals(new BigDecimal("33.33"), effective);
    }

    @Test
    @DisplayName("Division (/): PERCENT mode divides base price by percentage factor (e.g. 100 / 80% = 125.00)")
    void testDivision_Percent_Margin() {
        mockRule("Hardware", "/", "PERCENT", "80.00");
        Product p = createProduct("Hardware", "100.00");

        BigDecimal effective = pricingService.calculateEffectivePrice(p, PROFILE_ID, FIRM_ID);
        assertEquals(new BigDecimal("125.00"), effective);
    }

    @Test
    @DisplayName("Division (/): PERCENT mode with 50% divides by 0.5 (doubling the price)")
    void testDivision_Percent_Doubling() {
        mockRule("Hardware", "/", "PERCENT", "50.00");
        Product p = createProduct("Hardware", "100.00");

        BigDecimal effective = pricingService.calculateEffectivePrice(p, PROFILE_ID, FIRM_ID);
        assertEquals(new BigDecimal("200.00"), effective);
    }

    @Test
    @DisplayName("Division (/): Zero divisor safely falls back to base price without throwing ArithmeticException")
    void testDivision_ZeroDivisor_SafeFallback() {
        mockRule("Hardware", "/", "FIXED", "0.00");
        Product p = createProduct("Hardware", "100.00");

        assertDoesNotThrow(() -> {
            BigDecimal effective = pricingService.calculateEffectivePrice(p, PROFILE_ID, FIRM_ID);
            assertEquals(new BigDecimal("100.00"), effective);
        });
    }

    @Test
    @DisplayName("Division (/): Zero percentage divisor safely falls back to base price")
    void testDivision_ZeroPercent_SafeFallback() {
        mockRule("Hardware", "/", "PERCENT", "0.00");
        Product p = createProduct("Hardware", "100.00");

        assertDoesNotThrow(() -> {
            BigDecimal effective = pricingService.calculateEffectivePrice(p, PROFILE_ID, FIRM_ID);
            assertEquals(new BigDecimal("100.00"), effective);
        });
    }

    // =========================================================================
    // 5. OPERATOR NORMALIZATION & BACKWARD COMPATIBILITY
    // =========================================================================

    @ParameterizedTest
    @ValueSource(strings = {"+", "ADD", "addition", "PLUS", "  +  "})
    @DisplayName("Normalization: Addition aliases normalize to '+'")
    void testNormalization_Addition(String op) {
        assertEquals("+", ProductPricingService.normalizeOperator(op));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-", "SUB", "subtract", "SUBTRACTION", "minus"})
    @DisplayName("Normalization: Subtraction aliases normalize to '-'")
    void testNormalization_Subtraction(String op) {
        assertEquals("-", ProductPricingService.normalizeOperator(op));
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "x", "X", "MUL", "multiply", "TIMES"})
    @DisplayName("Normalization: Multiplication aliases normalize to '*'")
    void testNormalization_Multiplication(String op) {
        assertEquals("*", ProductPricingService.normalizeOperator(op));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "DIV", "divide", "DIVISION"})
    @DisplayName("Normalization: Division aliases normalize to '/'")
    void testNormalization_Division(String op) {
        assertEquals("/", ProductPricingService.normalizeOperator(op));
    }

    @Test
    @DisplayName("Normalization: null, empty, or unknown operators default to '+'")
    void testNormalization_Defaults() {
        assertEquals("+", ProductPricingService.normalizeOperator(null));
        assertEquals("+", ProductPricingService.normalizeOperator(""));
        assertEquals("+", ProductPricingService.normalizeOperator("   "));
        assertEquals("+", ProductPricingService.normalizeOperator("UNKNOWN_OP"));
    }

    @Test
    @DisplayName("Backward Compatibility: Rule with null operator defaults to '+' addition")
    void testBackwardCompatibility_NullOperator() {
        mockRule("Hardware", null, "PERCENT", "10.00");
        Product p = createProduct("Hardware", "100.00");

        BigDecimal effective = pricingService.calculateEffectivePrice(p, PROFILE_ID, FIRM_ID);
        assertEquals(new BigDecimal("110.00"), effective);
    }

    // =========================================================================
    // 6. EDGE CASES & SAFE GUARDS
    // =========================================================================

    @Test
    @DisplayName("Edge Case: null product returns 0.00")
    void testEdgeCase_NullProduct() {
        BigDecimal effective = pricingService.calculateEffectivePrice(null, PROFILE_ID, FIRM_ID);
        assertEquals(new BigDecimal("0.00"), effective);
    }

    @Test
    @DisplayName("Edge Case: product without category returns base price")
    void testEdgeCase_NoCategory() {
        Product p = createProduct(null, "75.00");
        BigDecimal effective = pricingService.calculateEffectivePrice(p, PROFILE_ID, FIRM_ID);
        assertEquals(new BigDecimal("75.00"), effective);
    }

    @Test
    @DisplayName("Edge Case: product with no matching rule returns base price")
    void testEdgeCase_NoMatchingRule() {
        when(categoryRuleRepo.findByFirmIdAndCategoryNameIgnoreCaseAndPricingProfileId(anyLong(), anyString(), anyLong()))
                .thenReturn(Optional.empty());

        Product p = createProduct("Unmatched", "45.00");
        BigDecimal effective = pricingService.calculateEffectivePrice(p, PROFILE_ID, FIRM_ID);
        assertEquals(new BigDecimal("45.00"), effective);
    }

    @Test
    @DisplayName("Edge Case: inactive profile returns base price")
    void testEdgeCase_InactiveProfile() {
        activeProfile.setIsActive(false);
        Product p = createProduct("Hardware", "100.00");

        BigDecimal effective = pricingService.calculateEffectivePrice(p, PROFILE_ID, FIRM_ID);
        assertEquals(new BigDecimal("100.00"), effective);
    }

    @Test
    @DisplayName("Edge Case: null adjustment value is treated as zero")
    void testEdgeCase_NullAdjustmentValue() {
        mockRule("Hardware", "+", "PERCENT", null);
        Product p = createProduct("Hardware", "100.00");

        BigDecimal effective = pricingService.calculateEffectivePrice(p, PROFILE_ID, FIRM_ID);
        assertEquals(new BigDecimal("100.00"), effective);
    }
}
