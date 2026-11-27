package com.billing.simple.billsoft.telegram;

import com.billing.simple.billsoft.BillsoftApplication;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.repositories.PartyRepository;
import com.billing.simple.billsoft.telegram.client.TelegramApiClient;
import com.billing.simple.billsoft.telegram.entities.*;
import com.billing.simple.billsoft.telegram.repo.*;
import com.billing.simple.billsoft.telegram.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = BillsoftApplication.class)
@ActiveProfiles("test")
@Transactional
public class TelegramWhatsAppHandoffTest {

    @Autowired
    private TelegramDeliveryService deliveryService;

    @Autowired
    private TelegramDocumentDeliveryRepository deliveryRepo;

    @Autowired
    private TelegramAuthorizedUserRepository authUserRepo;

    @Autowired
    private TelegramUserSessionRepository sessionRepo;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private SalaryRecordRepository salaryRecordRepository;

    @MockBean
    private TelegramApiClient apiClient;

    private final Long testChatId = 987654321L;
    private final Long testUserId = 555666777L;
    private final String botToken = "TEST_BOT_TOKEN";

    @BeforeEach
    void setUp() {
        TelegramAuthorizedUser user = new TelegramAuthorizedUser(testUserId, "Test Operator", "testoperator", "DESKTOP_OWNER");
        user.setStatus("ACTIVE");
        user.setAuthorizedAt(LocalDateTime.now());
        authUserRepo.save(user);

        TelegramUserSession session = new TelegramUserSession(testChatId, testUserId);
        session.setActiveFirmId(1L);
        session.setLastActivityAt(LocalDateTime.now());
        sessionRepo.save(session);
    }

    // =========================================================================
    // 1. WhatsApp Phone Normalization Rules
    // =========================================================================

    @Test
    void testPhoneNormalizationRules() {
        // 10 digits starting with 6-9
        TelegramWhatsAppUtils.PhoneResult r1 = TelegramWhatsAppUtils.normalizePhone("9876543210");
        assertTrue(r1.isValid());
        assertEquals("919876543210", r1.getNormalizedNumber());
        assertEquals("+91 98765 43210", r1.getHumanFormatted());

        // 10 digits with spaces/hyphens
        TelegramWhatsAppUtils.PhoneResult r2 = TelegramWhatsAppUtils.normalizePhone("  98765-43210 ");
        assertTrue(r2.isValid());
        assertEquals("919876543210", r2.getNormalizedNumber());

        // 11 digits starting with 0 followed by 6-9
        TelegramWhatsAppUtils.PhoneResult r3 = TelegramWhatsAppUtils.normalizePhone("09876543210");
        assertTrue(r3.isValid());
        assertEquals("919876543210", r3.getNormalizedNumber());

        // 12 digits starting with 91
        TelegramWhatsAppUtils.PhoneResult r4 = TelegramWhatsAppUtils.normalizePhone("919876543210");
        assertTrue(r4.isValid());
        assertEquals("919876543210", r4.getNormalizedNumber());

        // International number with leading +
        TelegramWhatsAppUtils.PhoneResult r5 = TelegramWhatsAppUtils.normalizePhone("+14155552671");
        assertTrue(r5.isValid());
        assertEquals("14155552671", r5.getNormalizedNumber());
        assertEquals("+14155552671", r5.getHumanFormatted());

        // Ambiguous 10-digit number (starts with 1-5 without country code) -> must reject
        TelegramWhatsAppUtils.PhoneResult r6 = TelegramWhatsAppUtils.normalizePhone("2345678901");
        assertFalse(r6.isValid());
        assertTrue(r6.getErrorMessage().contains("Ambiguous"));

        // Alphanumeric / invalid characters -> must reject
        TelegramWhatsAppUtils.PhoneResult r7 = TelegramWhatsAppUtils.normalizePhone("98765abcde");
        assertFalse(r7.isValid());

        // Too short (< 10 digits) -> must reject
        TelegramWhatsAppUtils.PhoneResult r8 = TelegramWhatsAppUtils.normalizePhone("98765");
        assertFalse(r8.isValid());
    }

    // =========================================================================
    // 2. WhatsApp Eligibility Matrix
    // =========================================================================

    @Test
    void testEligibilityMatrix() {
        // Eligible document types
        assertTrue(TelegramWhatsAppUtils.isEligible("INVOICE_PDF", "CREATE"));
        assertTrue(TelegramWhatsAppUtils.isEligible("ESTIMATE_PDF", "CREATE"));
        assertTrue(TelegramWhatsAppUtils.isEligible("PO_PDF", "CREATE"));
        assertTrue(TelegramWhatsAppUtils.isEligible("INVOICE_PDF", "DOWNLOAD"));
        assertTrue(TelegramWhatsAppUtils.isEligible("ESTIMATE_PDF", "DOWNLOAD"));
        assertTrue(TelegramWhatsAppUtils.isEligible("PO_PDF", "DOWNLOAD"));
        assertTrue(TelegramWhatsAppUtils.isEligible("STMT_CUST_PDF", "DOWNLOAD"));
        assertTrue(TelegramWhatsAppUtils.isEligible("STMT_PARTY_PDF", "DOWNLOAD"));
        assertTrue(TelegramWhatsAppUtils.isEligible("PAYSLIP_PDF", "INDIVIDUAL"));

        // Explicit Exclusions
        assertFalse(TelegramWhatsAppUtils.isEligible("STMT_FIRM_PDF", "REPORT"));
        assertFalse(TelegramWhatsAppUtils.isEligible("STMT_FORECAST_PDF", "REPORT"));
        assertFalse(TelegramWhatsAppUtils.isEligible("STMT_STOCK_PDF", "REPORT"));
        assertFalse(TelegramWhatsAppUtils.isEligible("PAYSLIP_PDF", "BULK"));
    }

    // =========================================================================
    // 3. Prepared Message and URL Generation
    // =========================================================================

    @Test
    void testPreparedMessageAndUrlGeneration() {
        String msg = TelegramWhatsAppUtils.buildPreparedMessage("INVOICE_PDF", "INV-2026-101", "Rohit Sharma");
        assertEquals("Hello Rohit Sharma, please find invoice INV-2026-101 attached.", msg);

        String url = TelegramWhatsAppUtils.buildWhatsAppUrl("919876543210", msg);
        assertTrue(url.startsWith("https://wa.me/919876543210?text=Hello%20Rohit%20Sharma"));
    }

    // =========================================================================
    // 4. WhatsApp Handoff Execution with Saved Valid Number
    // =========================================================================

    @Test
    void testSendWhatsAppHandoffWithSavedNumber() throws Exception {
        TelegramDocumentDelivery delivery = new TelegramDocumentDelivery();
        delivery.setInstallationId("LOCAL");
        delivery.setUserId(testUserId);
        delivery.setChatId(testChatId);
        delivery.setFirmId(1L);
        delivery.setDocumentType("INVOICE_PDF");
        delivery.setDocumentId(100L);
        delivery.setDocumentNumber("INV-001");
        delivery.setOperationType("CREATE");
        delivery.setRecipientName("Sharma Traders");
        delivery.setRecipientPhone("9876543210");
        delivery.setDeliveryState("SENT");
        delivery.setCreatedAt(LocalDateTime.now());
        delivery = deliveryRepo.save(delivery);

        deliveryService.sendWhatsAppHandoff(delivery, botToken);

        ArgumentCaptor<String> textCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> kbCap = ArgumentCaptor.forClass(Map.class);
        verify(apiClient).sendMessage(eq(botToken), eq(testChatId), textCap.capture(), eq("Markdown"), kbCap.capture());

        String text = textCap.getValue();
        assertTrue(text.contains("Send on WhatsApp"));
        assertTrue(text.contains("Sharma Traders"));
        assertTrue(text.contains("+91 98765 43210"));
        assertTrue(text.contains("Please attach the PDF"));

        Map keyboard = kbCap.getValue();
        assertNotNull(keyboard);
        List<List<Map<String, String>>> rows = (List<List<Map<String, String>>>) keyboard.get("inline_keyboard");
        assertEquals(2, rows.size());

        // Button 1: Open in WhatsApp link
        Map<String, String> btn1 = rows.get(0).get(0);
        assertEquals("📲 Open in WhatsApp", btn1.get("text"));
        assertTrue(btn1.get("url").contains("https://wa.me/919876543210"));

        // Button 2: Send to another number
        Map<String, String> btn2 = rows.get(1).get(0);
        assertEquals("✏️ Send to another number", btn2.get("text"));
        assertEquals("WA_ALT|" + delivery.getDeliveryId(), btn2.get("callback_data"));
    }

    // =========================================================================
    // 5. WhatsApp Handoff Execution with Missing Number
    // =========================================================================

    @Test
    void testSendWhatsAppHandoffWithMissingNumber() throws Exception {
        TelegramDocumentDelivery delivery = new TelegramDocumentDelivery();
        delivery.setInstallationId("LOCAL");
        delivery.setUserId(testUserId);
        delivery.setChatId(testChatId);
        delivery.setFirmId(1L);
        delivery.setDocumentType("PO_PDF");
        delivery.setDocumentId(200L);
        delivery.setDocumentNumber("PO-001");
        delivery.setOperationType("CREATE");
        delivery.setRecipientName("Acme Supplies");
        delivery.setRecipientPhone(null); // Missing phone
        delivery.setDeliveryState("SENT");
        delivery.setCreatedAt(LocalDateTime.now());
        delivery = deliveryRepo.save(delivery);

        deliveryService.sendWhatsAppHandoff(delivery, botToken);

        ArgumentCaptor<String> textCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> kbCap = ArgumentCaptor.forClass(Map.class);
        verify(apiClient).sendMessage(eq(botToken), eq(testChatId), textCap.capture(), eq("Markdown"), kbCap.capture());

        String text = textCap.getValue();
        assertTrue(text.contains("No mobile number saved for *Acme Supplies*"));

        Map keyboard = kbCap.getValue();
        List<List<Map<String, String>>> rows = (List<List<Map<String, String>>>) keyboard.get("inline_keyboard");
        assertEquals(1, rows.size());
        assertEquals("📱 Enter Mobile Number", rows.get(0).get(0).get("text"));
        assertEquals("WA_ENTER|" + delivery.getDeliveryId(), rows.get(0).get(0).get("callback_data"));
        assertEquals("⏭ Skip", rows.get(0).get(1).get("text"));
        assertEquals("WA_SKIP|" + delivery.getDeliveryId(), rows.get(0).get(1).get("callback_data"));
    }

    // =========================================================================
    // 6. One-Time Alternate Number does NOT Overwrite Contact Master
    // =========================================================================

    @Test
    void testAlternateWhatsAppNumberDoesNotOverwriteMaster() throws Exception {
        Customer cust = new Customer();
        cust.setFirmId(1L);
        cust.setName("Persistent Customer");
        cust.setPhone("9111111111");
        cust = customerRepository.save(cust);

        TelegramDocumentDelivery delivery = new TelegramDocumentDelivery();
        delivery.setInstallationId("LOCAL");
        delivery.setUserId(testUserId);
        delivery.setChatId(testChatId);
        delivery.setFirmId(1L);
        delivery.setDocumentType("INVOICE_PDF");
        delivery.setDocumentId(300L);
        delivery.setDocumentNumber("INV-300");
        delivery.setOperationType("DOWNLOAD");
        delivery.setRecipientName(cust.getName());
        delivery.setRecipientPhone(cust.getPhone());
        delivery.setDeliveryState("SENT");
        delivery.setCreatedAt(LocalDateTime.now());
        delivery = deliveryRepo.save(delivery);

        // Operator provides alternate number 9999988888 for one-time sending
        deliveryService.sendAlternateWhatsAppLink(delivery.getDeliveryId(), "9999988888", botToken);

        ArgumentCaptor<String> textCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> kbCap = ArgumentCaptor.forClass(Map.class);
        verify(apiClient).sendMessage(eq(botToken), eq(testChatId), textCap.capture(), eq("Markdown"), kbCap.capture());

        assertTrue(textCap.getValue().contains("+91 99999 88888"));

        Map keyboard = kbCap.getValue();
        List<List<Map<String, String>>> rows = (List<List<Map<String, String>>>) keyboard.get("inline_keyboard");
        assertTrue(rows.get(0).get(0).get("url").contains("919999988888"));

        // Crucial data integrity verification: customer master MUST retain original phone
        Customer persistedCust = customerRepository.findById(cust.getId()).orElseThrow();
        assertEquals("9111111111", persistedCust.getPhone(), "Customer master phone must NOT be overwritten by one-time alternate number!");
    }

    // =========================================================================
    // 7. Excluded Operations Never Trigger WhatsApp Handoff
    // =========================================================================

    @Test
    void testExcludedOperationsNeverTriggerWhatsAppHandoff() throws Exception {
        // Firm statement
        TelegramDocumentDelivery firmDel = new TelegramDocumentDelivery();
        firmDel.setDocumentType("STMT_FIRM_PDF");
        firmDel.setOperationType("REPORT");
        firmDel.setChatId(testChatId);
        deliveryService.sendWhatsAppHandoff(firmDel, botToken);

        // Forecast statement
        TelegramDocumentDelivery fcDel = new TelegramDocumentDelivery();
        fcDel.setDocumentType("STMT_FORECAST_PDF");
        fcDel.setOperationType("REPORT");
        fcDel.setChatId(testChatId);
        deliveryService.sendWhatsAppHandoff(fcDel, botToken);

        // Stock movement statement
        TelegramDocumentDelivery stDel = new TelegramDocumentDelivery();
        stDel.setDocumentType("STMT_STOCK_PDF");
        stDel.setOperationType("REPORT");
        stDel.setChatId(testChatId);
        deliveryService.sendWhatsAppHandoff(stDel, botToken);

        // Bulk payroll
        TelegramDocumentDelivery bulkDel = new TelegramDocumentDelivery();
        bulkDel.setDocumentType("PAYSLIP_PDF");
        bulkDel.setOperationType("BULK");
        bulkDel.setChatId(testChatId);
        deliveryService.sendWhatsAppHandoff(bulkDel, botToken);

        // Verify zero interactions with apiClient for sending WhatsApp handoffs
        verifyNoInteractions(apiClient);
    }
}
