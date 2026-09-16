package com.billing.simple.billsoft.assistant.nlu;

import com.billing.simple.billsoft.assistant.dto.OmnisearchNluDTOs.*;
import com.billing.simple.billsoft.assistant.repo.*;
import com.billing.simple.billsoft.assistant.service.OmnisearchQuickHelpService;
import com.billing.simple.billsoft.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class OmnisearchNluServiceTest {

    private OmnisearchNluService nluService;
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
        OmnisearchMathEngine mathEngine = new OmnisearchMathEngine();
        OmnisearchLocalQrService qrService = new OmnisearchLocalQrService();

        customerRepo = Mockito.mock(OmnisearchCustomerQueryRepository.class);
        partyRepo = Mockito.mock(OmnisearchPartyQueryRepository.class);
        productRepo = Mockito.mock(OmnisearchProductQueryRepository.class);
        employeeRepo = Mockito.mock(OmnisearchEmployeeQueryRepository.class);
        quickHelpService = Mockito.mock(OmnisearchQuickHelpService.class);

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

    @Test
    void testDeterministicMathQuery() {
        NluEvaluationResponse response = nluService.evaluateQuery("17% of 5633");
        assertThat(response.getCapabilityId()).isEqualTo("SPEC_MATH_CALC");
        assertThat(response.getGroup()).isEqualTo(CapabilityGroup.SPECIAL);
        assertThat(response.getExecutionStatus()).isEqualTo("EXECUTE");
        assertThat(response.getDirectAnswerText()).isEqualTo("₹ 957.61");
    }

    @Test
    void testDeterministicUpiQrQuery() {
        NluEvaluationResponse response = nluService.evaluateQuery("upi 900");
        assertThat(response.getCapabilityId()).isEqualTo("SPEC_PAY_UPI_QR");
        assertThat(response.getGroup()).isEqualTo(CapabilityGroup.SPECIAL);
        assertThat(response.getExecutionStatus()).isEqualTo("EXECUTE");
        assertThat(response.getQrCodeBase64()).startsWith("data:image/png;base64,");
    }

    @Test
    void testActionContractGeneration() {
        OmnisearchCustomerQueryRepository.CustomerCandidateProjection c = Mockito.mock(OmnisearchCustomerQueryRepository.CustomerCandidateProjection.class);
        Mockito.when(c.getId()).thenReturn(10L);
        Mockito.when(c.getName()).thenReturn("Gauri");
        Mockito.when(customerRepo.searchCandidates(eq(1L), eq("Gauri"))).thenReturn(List.of(c));

        NluEvaluationResponse response = nluService.evaluateQuery("receive payment 5000 from Gauri");
        assertThat(response.getCapabilityId()).isEqualTo("ACT_RECEIVE_PAYMENT");
        assertThat(response.getGroup()).isEqualTo(CapabilityGroup.ACTION);
        assertThat(response.getActionContract()).isNotNull();
    }
}
