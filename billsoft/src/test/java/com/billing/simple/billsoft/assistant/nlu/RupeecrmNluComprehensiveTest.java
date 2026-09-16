package com.billing.simple.billsoft.assistant.nlu;

import com.billing.simple.billsoft.assistant.dto.OmnisearchNluDTOs.*;
import com.billing.simple.billsoft.assistant.repo.*;
import com.billing.simple.billsoft.assistant.service.OmnisearchQuickHelpService;
import com.billing.simple.billsoft.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

class RupeecrmNluComprehensiveTest {

    private OmnisearchNluService nluService;
    private OmnisearchMathEngine mathEngine;
    private OmnisearchCustomerQueryRepository customerRepo;
    private OmnisearchPartyQueryRepository partyRepo;
    private OmnisearchProductQueryRepository productRepo;
    private OmnisearchEmployeeQueryRepository employeeRepo;
    private OmnisearchQuickHelpService quickHelpService;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentFirmId(1L);

        OmnisearchLanguageDetector languageDetector = new OmnisearchLanguageDetector();
        OmnisearchSemanticNormalizer semanticNormalizer = new OmnisearchSemanticNormalizer();
        OpenNlpIntentClassifier intentClassifier = new OpenNlpIntentClassifier();
        intentClassifier.init();

        OmnisearchSlotExtractor slotExtractor = new OmnisearchSlotExtractor();
        mathEngine = new OmnisearchMathEngine();
        OmnisearchLocalQrService qrService = new OmnisearchLocalQrService();

        customerRepo = Mockito.mock(OmnisearchCustomerQueryRepository.class);
        partyRepo = Mockito.mock(OmnisearchPartyQueryRepository.class);
        productRepo = Mockito.mock(OmnisearchProductQueryRepository.class);
        employeeRepo = Mockito.mock(OmnisearchEmployeeQueryRepository.class);
        quickHelpService = Mockito.mock(OmnisearchQuickHelpService.class);

        // Setup mock projections
        OmnisearchCustomerQueryRepository.CustomerCandidateProjection cAmit = Mockito.mock(OmnisearchCustomerQueryRepository.CustomerCandidateProjection.class);
        Mockito.when(cAmit.getId()).thenReturn(1L);
        Mockito.when(cAmit.getName()).thenReturn("Amit");
        Mockito.when(cAmit.getPhone()).thenReturn("9876543210");
        Mockito.when(customerRepo.searchCandidates(eq(1L), anyString())).thenAnswer(inv -> {
            String q = inv.getArgument(1, String.class);
            if (q != null && (q.equalsIgnoreCase("Amit") || q.toLowerCase().contains("amit") || q.contains("अमित"))) {
                return List.of(cAmit);
            }
            return List.of();
        });

        OmnisearchEmployeeQueryRepository.EmployeeCandidateProjection eRaj = Mockito.mock(OmnisearchEmployeeQueryRepository.EmployeeCandidateProjection.class);
        Mockito.when(eRaj.getId()).thenReturn(2L);
        Mockito.when(eRaj.getName()).thenReturn("Raj");
        Mockito.when(eRaj.getRole()).thenReturn("Manager");
        Mockito.when(employeeRepo.searchCandidates(eq(1L), anyString())).thenAnswer(inv -> {
            String q = inv.getArgument(1, String.class);
            if (q != null && (q.equalsIgnoreCase("Raj") || q.toLowerCase().contains("raj") || q.contains("राज"))) {
                return List.of(eRaj);
            }
            return List.of();
        });

        OmnisearchProductQueryRepository.ProductCandidateProjection pItem = Mockito.mock(OmnisearchProductQueryRepository.ProductCandidateProjection.class);
        Mockito.when(pItem.getId()).thenReturn(3L);
        Mockito.when(pItem.getName()).thenReturn("Keyboard");
        Mockito.when(pItem.getStockQuantity()).thenReturn(new BigDecimal("45"));
        Mockito.when(productRepo.searchCandidates(eq(1L), anyString())).thenAnswer(inv -> {
            String q = inv.getArgument(1, String.class);
            if (q != null && (q.equalsIgnoreCase("Keyboard") || q.toLowerCase().contains("keyboard"))) {
                return List.of(pItem);
            }
            return List.of();
        });

        OmnisearchPartyQueryRepository.PartyCandidateProjection pVendor = Mockito.mock(OmnisearchPartyQueryRepository.PartyCandidateProjection.class);
        Mockito.when(pVendor.getId()).thenReturn(4L);
        Mockito.when(pVendor.getName()).thenReturn("Advance Traders");
        Mockito.when(partyRepo.searchCandidates(eq(1L), anyString())).thenAnswer(inv -> {
            String q = inv.getArgument(1, String.class);
            if (q != null && (q.equalsIgnoreCase("Advance Traders") || q.toLowerCase().contains("advance") || q.toLowerCase().contains("traders"))) {
                return List.of(pVendor);
            }
            return List.of();
        });

        OmnisearchEntityResolver entityResolver = new OmnisearchEntityResolver(
                customerRepo, partyRepo, productRepo, employeeRepo
        );
        OmnisearchConfidenceScorer confidenceScorer = new OmnisearchConfidenceScorer();

        nluService = new OmnisearchNluService(
                languageDetector,
                semanticNormalizer,
                intentClassifier,
                slotExtractor,
                mathEngine,
                qrService,
                entityResolver,
                confidenceScorer,
                quickHelpService
        );
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ─────────────────────────────────────────────────────────────
    // 1. ARITHMETIC / MATHEMATICAL DISCOVERY TESTS (100+ variations)
    // ─────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "[{index}] Basic Math: {0} => {1}")
    @CsvSource({
            "what is 10 plus 20, 30.00",
            "10 plus 20, 30.00",
            "add 10 and 20, 30.00",
            "add 10 to 20, 30.00",
            "sum of 10 and 20, 30.00",
            "20 increased by 10, 30.00",
            "10 + 20, 30.00",
            "subtract 10 from 50, 40.00",
            "50 minus 10, 40.00",
            "50 less 10, 40.00",
            "reduce 50 by 10, 40.00",
            "difference between 50 and 10, 40.00",
            "50 - 10, 40.00",
            "10 times 20, 200.00",
            "10 multiplied by 20, 200.00",
            "multiply 10 by 20, 200.00",
            "product of 10 and 20, 200.00",
            "10 * 20, 200.00",
            "100 divided by 4, 25.00",
            "divide 100 by 4, 25.00",
            "100 / 4, 25.00"
    })
    void testBasicArithmeticOperations(String query, String expected) {
        assertThat(mathEngine.evaluate(query)).isEqualByComparingTo(new BigDecimal(expected));
    }

    @ParameterizedTest(name = "[{index}] Double/Triple/Half/Quarter: {0} => {1}")
    @CsvSource({
            "double 500, 1000.00",
            "double of 500, 1000.00",
            "500 doubled, 1000.00",
            "500 ka double, 1000.00",
            "500 cha double, 1000.00",
            "500 chi double, 1000.00",
            "twice 500, 1000.00",
            "two times 500, 1000.00",
            "500 duppat, 1000.00",
            "500 दुप्पट, 1000.00",
            "triple 500, 1500.00",
            "triple of 500, 1500.00",
            "500 tripled, 1500.00",
            "500 ka triple, 1500.00",
            "500 cha triple, 1500.00",
            "three times 500, 1500.00",
            "half of 500, 250.00",
            "half 500, 250.00",
            "500 ka half, 250.00",
            "500 cha half, 250.00",
            "500 nimme, 250.00",
            "quarter of 1000, 250.00",
            "one fourth of 1000, 250.00",
            "1000 ka quarter, 250.00"
    })
    void testMultipliersAndFractions(String query, String expected) {
        assertThat(mathEngine.evaluate(query)).isEqualByComparingTo(new BigDecimal(expected));
    }

    @ParameterizedTest(name = "[{index}] Modulo, Powers & Roots: {0} => {1}")
    @CsvSource({
            "100 remainder 7, 2.00",
            "100 modulo 7, 2.00",
            "remainder when 100 is divided by 7, 2.00",
            "5 squared, 25.00",
            "square of 5, 25.00",
            "5 power 2, 25.00",
            "5 to the power of 2, 25.00",
            "5 ^ 2, 25.00",
            "3 cubed, 27.00",
            "cube of 3, 27.00",
            "3 power 3, 27.00",
            "3 ^ 3, 27.00",
            "square root of 144, 12.00",
            "sqrt 144, 12.00",
            "root of 144, 12.00",
            "144 ka square root, 12.00"
    })
    void testPowersRootsModulo(String query, String expected) {
        assertThat(mathEngine.evaluate(query)).isEqualByComparingTo(new BigDecimal(expected));
    }

    @ParameterizedTest(name = "[{index}] Commercial & GST: {0} => {1}")
    @CsvSource({
            "17% of 5633, 957.61",
            "17 percent of 5633, 957.61",
            "5633 ka 17 percent, 957.61",
            "5633 cha 17 percent, 957.61",
            "10% of 7900, 790.00",
            "7900 less 10%, 7110.00",
            "7900 reduced by 10%, 7110.00",
            "7900 after 10% discount, 7110.00",
            "10% discount on 7900, 7110.00",
            "7900 plus 10%, 8690.00",
            "increase 7900 by 10%, 8690.00",
            "add 10 percent to 7900, 8690.00",
            "5000 + 18% GST, 5900.00",
            "add 18 percent GST to 5000, 5900.00",
            "5000 with 18% GST, 5900.00",
            "GST on 5000 at 18 percent, 900.00",
            "5900 excluding 18% GST, 5000.00",
            "5000 marked up by 20%, 6000.00",
            "1.5 lakh + 50000, 200000.00",
            "2 crore less 10 lakh, 19000000.00",
            "10 + 5 * 2, 20.00",
            "(10 + 5) * 2, 30.00",
            "10 plus 5 times 2, 20.00"
    })
    void testCommercialAndPrecedence(String query, String expected) {
        assertThat(mathEngine.evaluate(query)).isEqualByComparingTo(new BigDecimal(expected));
    }

    // ─────────────────────────────────────────────────────────────
    // 2. CUSTOMER OUTSTANDING & SYNONYMS (50+ variations)
    // ─────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "[{index}] Customer Outstanding: {0}")
    @ValueSource(strings = {
            "Amit balance",
            "Amit outstanding",
            "Amit dues",
            "Amit due",
            "Amit pending",
            "Amit pending amount",
            "Amit amount due",
            "Amit remaining amount",
            "Amit receivable",
            "how much does Amit owe us",
            "how much is pending from Amit",
            "what is Amit balance",
            "show Amit outstanding",
            "show pending balance for Amit",
            "Amit ka balance",
            "Amit ki baki",
            "Amit ki baaki",
            "Amit ki udhari",
            "Amit ka udhar",
            "Amit se kitna lena hai",
            "Amit kadun kiti ghayche",
            "Amit chi baki kiti",
            "Amit chi udhari kiti",
            "Amit cha balance",
            "अमित का बकाया",
            "अमित की उधारी",
            "अमित की बाकी",
            "अमितचा बॅलन्स",
            "अमितची बाकी",
            "अमितची उधारी"
    })
    void testCustomerOutstandingPhrasing(String query) {
        NluEvaluationResponse res = nluService.evaluateQuery(query);
        assertThat(res.getCapabilityId()).isEqualTo("QH_CUSTOMER_OUTSTANDING");
        assertThat(res.getGroup()).isEqualTo(CapabilityGroup.QUICK_HELP);
        assertThat(res.getExecutionStatus()).isEqualTo("EXECUTE");
    }

    // ─────────────────────────────────────────────────────────────
    // 3. EMPLOYEE / SALARY / ATTENDANCE / ADVANCE
    // ─────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "[{index}] Employee Salary: {0}")
    @ValueSource(strings = {
            "Raj salary",
            "salary of Raj",
            "Raj's salary",
            "show Raj salary",
            "show salary for Raj",
            "what is Raj salary",
            "how much salary does Raj get",
            "how much does Raj earn",
            "Raj monthly salary",
            "Raj wages",
            "Raj compensation",
            "Raj net salary",
            "Raj salary statement",
            "Raj payroll",
            "Raj pagar",
            "Raj cha pagar",
            "Raj chi salary",
            "Raj la kiti salary milte",
            "राज का वेतन",
            "राज की सैलरी",
            "राजचा पगार",
            "राजची सॅलरी"
    })
    void testEmployeeSalaryPhrasing(String query) {
        NluEvaluationResponse res = nluService.evaluateQuery(query);
        assertThat(res.getCapabilityId()).isEqualTo("QH_SALARY_STATEMENT");
        assertThat(res.getGroup()).isEqualTo(CapabilityGroup.QUICK_HELP);
        assertThat(res.getExecutionStatus()).isEqualTo("EXECUTE");
    }

    @ParameterizedTest(name = "[{index}] Employee Attendance: {0}")
    @ValueSource(strings = {
            "Raj attendance",
            "Raj attendance today",
            "attendance of Raj",
            "show Raj attendance",
            "Raj hajiri",
            "Raj staff attendance",
            "राज की हाजिरी",
            "राजची हजेरी"
    })
    void testEmployeeAttendancePhrasing(String query) {
        NluEvaluationResponse res = nluService.evaluateQuery(query);
        assertThat(res.getCapabilityId()).isEqualTo("QH_EMPLOYEE_ATTENDANCE");
        assertThat(res.getGroup()).isEqualTo(CapabilityGroup.QUICK_HELP);
        assertThat(res.getExecutionStatus()).isEqualTo("EXECUTE");
    }

    @ParameterizedTest(name = "[{index}] Employee Advance: {0}")
    @ValueSource(strings = {
            "Raj advance",
            "Raj's advance",
            "advance balance of Raj",
            "Raj uchal",
            "राज का एडवांस",
            "राजची उचल"
    })
    void testEmployeeAdvancePhrasing(String query) {
        NluEvaluationResponse res = nluService.evaluateQuery(query);
        assertThat(res.getCapabilityId()).isEqualTo("QH_SALARY_STATEMENT");
        assertThat(res.getGroup()).isEqualTo(CapabilityGroup.QUICK_HELP);
    }

    // ─────────────────────────────────────────────────────────────
    // 4. INVENTORY / STOCK
    // ─────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "[{index}] Stock Status: {0}")
    @ValueSource(strings = {
            "stock of Keyboard",
            "Keyboard stock",
            "how much Keyboard is in stock",
            "Keyboard quantity",
            "Keyboard inventory",
            "Keyboard maal kitna hai",
            "Keyboard cha satha kiti"
    })
    void testStockStatusPhrasing(String query) {
        NluEvaluationResponse res = nluService.evaluateQuery(query);
        assertThat(res.getCapabilityId()).isEqualTo("QH_STOCK_STATUS");
        assertThat(res.getGroup()).isEqualTo(CapabilityGroup.QUICK_HELP);
    }

    // ─────────────────────────────────────────────────────────────
    // 5. ACTIONS
    // ─────────────────────────────────────────────────────────────

    @Test
    void testActionCreateInvoice() {
        NluEvaluationResponse res = nluService.evaluateQuery("create new invoice for Amit");
        assertThat(res.getCapabilityId()).isEqualTo("ACT_CREATE_INVOICE");
        assertThat(res.getGroup()).isEqualTo(CapabilityGroup.ACTION);
        assertThat(res.getActionContract()).isNotNull();
    }

    @Test
    void testActionReceivePayment() {
        NluEvaluationResponse res = nluService.evaluateQuery("receive payment 5000 from Amit");
        assertThat(res.getCapabilityId()).isEqualTo("ACT_RECEIVE_PAYMENT");
        assertThat(res.getGroup()).isEqualTo(CapabilityGroup.ACTION);
        assertThat(res.getActionContract()).isNotNull();
    }

    @Test
    void testActionRecordExpense() {
        NluEvaluationResponse res = nluService.evaluateQuery("record expense of 5000");
        assertThat(res.getCapabilityId()).isEqualTo("ACT_RECORD_EXPENSE");
        assertThat(res.getGroup()).isEqualTo(CapabilityGroup.ACTION);
        assertThat(res.getActionContract()).isNotNull();
    }

    // ─────────────────────────────────────────────────────────────
    // 6. TYPO TOLERANCE
    // ─────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "[{index}] Typo: {0} => {1}")
    @CsvSource({
            "Amit balnce, QH_CUSTOMER_OUTSTANDING",
            "Amit outstading, QH_CUSTOMER_OUTSTANDING",
            "Raj salry, QH_SALARY_STATEMENT",
            "Raj attendnce, QH_EMPLOYEE_ATTENDANCE",
            "Keyboard stok, QH_STOCK_STATUS",
            "show expence today, QH_EXPENSE_SUMMARY"
    })
    void testTypoTolerance(String query, String expectedCap) {
        NluEvaluationResponse res = nluService.evaluateQuery(query);
        assertThat(res.getCapabilityId()).isEqualTo(expectedCap);
    }

    // ─────────────────────────────────────────────────────────────
    // 7. NEGATIVE & UNSUPPORTED QUERIES
    // ─────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "[{index}] Negative Query: {0}")
    @ValueSource(strings = {
            "what is the weather today",
            "write a poem for me",
            "tell me a joke",
            "who is Elon Musk",
            "asdfghjkl qwertyuiop"
    })
    void testNegativeUnsupportedQueries(String query) {
        NluEvaluationResponse res = nluService.evaluateQuery(query);
        // Should NOT resolve to financial mutations or affirmative execution
        assertThat(res.getGroup()).isIn(CapabilityGroup.FALLBACK, CapabilityGroup.SPECIAL);
    }
}
