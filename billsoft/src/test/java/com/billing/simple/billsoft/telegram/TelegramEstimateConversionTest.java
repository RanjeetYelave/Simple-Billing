package com.billing.simple.billsoft.telegram;

import com.billing.simple.billsoft.BillsoftApplication;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.service.InvoiceService;
import com.billing.simple.billsoft.telegram.client.TelegramApiClient;
import com.billing.simple.billsoft.telegram.entities.TelegramAuthorizedUser;
import com.billing.simple.billsoft.telegram.entities.TelegramDocumentDelivery;
import com.billing.simple.billsoft.telegram.entities.TelegramUserSession;
import com.billing.simple.billsoft.telegram.repo.TelegramAuthorizedUserRepository;
import com.billing.simple.billsoft.telegram.repo.TelegramDocumentDeliveryRepository;
import com.billing.simple.billsoft.telegram.repo.TelegramUserSessionRepository;
import com.billing.simple.billsoft.telegram.service.TelegramDeliveryService;
import com.billing.simple.billsoft.telegram.service.TelegramReadService;
import com.billing.simple.billsoft.telegram.service.TelegramUpdateDispatcher;
import com.billing.simple.billsoft.telegram.service.TelegramWhatsAppUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = BillsoftApplication.class)
@ActiveProfiles("test")
@Transactional
public class TelegramEstimateConversionTest {

    @Autowired
    private TelegramUpdateDispatcher dispatcher;

    @Autowired
    private TelegramReadService readService;

    @Autowired
    private TelegramDeliveryService deliveryService;

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private InvoiceRepository invoiceRepo;

    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private TelegramAuthorizedUserRepository userRepo;

    @Autowired
    private TelegramUserSessionRepository sessionRepo;

    @Autowired
    private TelegramDocumentDeliveryRepository deliveryRepo;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TelegramApiClient apiClient;

    private final Long testFirmId = 1L;
    private final Long testUserId = 771122L;
    private final Long testChatId = 998877L;
    private final String botToken = "CONV_TEST_BOT_TOKEN";
    private final String installId = "LOCAL_CONV_TEST_INSTALL";

    private Customer customerWithPhone;
    private Customer customerWithoutPhone;
    private Product testProduct;
    private Invoice validEstimate;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentFirmId(testFirmId);

        // 1. Authorized Telegram User & Active Session
        TelegramAuthorizedUser authUser = new TelegramAuthorizedUser();
        authUser.setUserId(testUserId);
        authUser.setDisplayName("Operator");
        authUser.setStatus("ACTIVE");
        authUser.setAuthorizedAt(LocalDateTime.now());
        userRepo.save(authUser);

        TelegramUserSession session = new TelegramUserSession(testChatId, testUserId);
        session.setActiveFirmId(testFirmId);
        session.setConversationState("IDLE");
        sessionRepo.save(session);

        // 2. Customers
        customerWithPhone = new Customer();
        customerWithPhone.setName("Acme Industrial Supplies");
        customerWithPhone.setPhone("9876543210");
        customerWithPhone.setFirmId(testFirmId);
        customerWithPhone = customerRepo.save(customerWithPhone);

        customerWithoutPhone = new Customer();
        customerWithoutPhone.setName("Bharat Traders");
        customerWithoutPhone.setPhone(null);
        customerWithoutPhone.setFirmId(testFirmId);
        customerWithoutPhone = customerRepo.save(customerWithoutPhone);

        // 3. Product with Stock
        testProduct = new Product();
        testProduct.setName("Heavy Duty Drill Bit");
        testProduct.setPrice(new BigDecimal("500.00"));
        testProduct.setStockQuantity(new BigDecimal("100"));
        testProduct.setUnit("PCS");
        testProduct.setGstPercentage(new BigDecimal("18.00"));
        testProduct.setFirmId(testFirmId);
        testProduct = productRepo.save(testProduct);

        // 4. Valid Estimate
        validEstimate = new Invoice();
        validEstimate.setFirmId(testFirmId);
        validEstimate.setCustomer(customerWithPhone);
        validEstimate.setStatus(InvoiceStatus.ESTIMATE);
        validEstimate.setEstimateNumber("EST-2026-001");
        validEstimate.setInvoiceDate(LocalDateTime.now().minusDays(2));
        validEstimate.setSubtotalWithoutTax(new BigDecimal("1000.00"));
        validEstimate.setTotalTax(new BigDecimal("180.00"));
        validEstimate.setTotalAmount(new BigDecimal("1180.00"));
        validEstimate.setConversionStatus("PENDING");

        InvoiceItem item = new InvoiceItem();
        item.setInvoice(validEstimate);
        item.setProduct(testProduct);
        item.setProductName(testProduct.getName());
        item.setQty(2);
        item.setUnit("PCS");
        item.setPricePerUnit(new BigDecimal("500.00"));
        item.setTaxableAmount(new BigDecimal("1000.00"));
        item.setGstPercent(new BigDecimal("18.00"));
        item.setGstAmount(new BigDecimal("180.00"));
        item.setLineTotal(new BigDecimal("1180.00"));
        validEstimate.getItems().add(item);

        validEstimate = invoiceRepo.save(validEstimate);

        reset(apiClient);
    }

    private ObjectNode createCallbackUpdate(String callbackData) {
        ObjectNode update = objectMapper.createObjectNode();
        ObjectNode callback = update.putObject("callback_query");
        callback.put("data", callbackData);
        callback.putObject("from").put("id", testUserId).put("first_name", "Operator");
        callback.putObject("message").put("message_id", 100).putObject("chat").put("id", testChatId).put("type", "private");
        return update;
    }

    private ObjectNode createMessageUpdate(String text) {
        ObjectNode update = objectMapper.createObjectNode();
        ObjectNode msg = update.putObject("message");
        msg.put("text", text);
        msg.putObject("from").put("id", testUserId).put("first_name", "Operator");
        msg.putObject("chat").put("id", testChatId).put("type", "private");
        return update;
    }

    @Test
    void testMenuContainsConvertEstimateOption() throws Exception {
        // Tapping Create Invoice / Quotation (M_CREATE)
        dispatcher.dispatch(createCallbackUpdate("M_CREATE"), botToken, installId);

        ArgumentCaptor<Map<String, Object>> kbCaptor = ArgumentCaptor.forClass(Map.class);
        verify(apiClient).sendMessage(eq(botToken), eq(testChatId), contains("Create Invoice / Quotation"), eq("Markdown"), kbCaptor.capture());

        Map<String, Object> keyboard = kbCaptor.getValue();
        List<List<Map<String, String>>> rows = (List<List<Map<String, String>>>) keyboard.get("inline_keyboard");
        assertNotNull(rows);

        // Verify all 3 options are present
        boolean hasCreateInv = rows.stream().flatMap(List::stream).anyMatch(b -> "M_CREATE_INV".equals(b.get("callback_data")));
        boolean hasCreateEst = rows.stream().flatMap(List::stream).anyMatch(b -> "M_CREATE_EST".equals(b.get("callback_data")));
        boolean hasConvertEst = rows.stream().flatMap(List::stream).anyMatch(b -> "M_CONVERT_EST".equals(b.get("callback_data")));

        assertTrue(hasCreateInv, "Must have Create New Invoice option");
        assertTrue(hasCreateEst, "Must have Create New Quotation option");
        assertTrue(hasConvertEst, "Must have Convert Estimate to Invoice option");
    }

    @Test
    void testStartEstimateConversionPromptsCustomerSearch() throws Exception {
        // Selecting Convert Estimate to Invoice
        dispatcher.dispatch(createCallbackUpdate("M_CONVERT_EST"), botToken, installId);

        verify(apiClient).sendMessage(eq(botToken), eq(testChatId), contains("Enter the customer name"), eq("Markdown"), any());

        TelegramUserSession session = sessionRepo.findByChatId(testChatId).orElseThrow();
        assertEquals("CONV_SEARCH_CUST", session.getConversationState());
    }

    @Test
    void testConvertCustomerSearchFindsExistingAndRejectsNonExistent() throws Exception {
        sessionRepo.findByChatId(testChatId).ifPresent(s -> {
            s.setConversationState("CONV_SEARCH_CUST");
            sessionRepo.save(s);
        });

        // 1. Unknown customer name
        dispatcher.dispatch(createMessageUpdate("NonExistentCorporationXYZ"), botToken, installId);
        verify(apiClient).sendMessage(eq(botToken), eq(testChatId), contains("No customer found matching"), isNull(), any());

        // 2. Existing customer
        reset(apiClient);
        dispatcher.dispatch(createMessageUpdate("Acme Industrial"), botToken, installId);

        ArgumentCaptor<Map<String, Object>> kbCaptor = ArgumentCaptor.forClass(Map.class);
        verify(apiClient).sendMessage(eq(botToken), eq(testChatId), contains("Select customer"), isNull(), kbCaptor.capture());

        List<List<Map<String, String>>> rows = (List<List<Map<String, String>>>) kbCaptor.getValue().get("inline_keyboard");
        assertTrue(rows.stream().flatMap(List::stream).anyMatch(b -> ("CONV_CUST_SEL|" + customerWithPhone.getId()).equals(b.get("callback_data"))));
    }

    @Test
    void testCustomerSelectionWithSavedPhoneDirectlyListsEstimates() throws Exception {
        dispatcher.dispatch(createCallbackUpdate("CONV_CUST_SEL|" + customerWithPhone.getId()), botToken, installId);

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> kbCaptor = ArgumentCaptor.forClass(Map.class);
        verify(apiClient).sendMessage(eq(botToken), eq(testChatId), msgCaptor.capture(), eq("Markdown"), kbCaptor.capture());

        String message = msgCaptor.getValue();
        assertTrue(message.contains("Select Estimate to Convert"), "Should header select estimate");
        assertTrue(message.contains("EST-2026-001"), "Should display estimate number");
        assertTrue(message.contains("1180"), "Should display estimate total amount");

        List<List<Map<String, String>>> rows = (List<List<Map<String, String>>>) kbCaptor.getValue().get("inline_keyboard");
        assertTrue(rows.stream().flatMap(List::stream).anyMatch(b -> ("CONV_EST_PICK|" + validEstimate.getId()).equals(b.get("callback_data"))));
    }

    @Test
    void testCustomerSelectionWithoutPhoneOffersOptionalEntryAndSkip() throws Exception {
        dispatcher.dispatch(createCallbackUpdate("CONV_CUST_SEL|" + customerWithoutPhone.getId()), botToken, installId);

        // Expect prompt for optional mobile number with Skip button
        ArgumentCaptor<Map<String, Object>> kbCaptor = ArgumentCaptor.forClass(Map.class);
        verify(apiClient).sendMessage(eq(botToken), eq(testChatId), contains("Customer mobile number (optional)"), eq("Markdown"), kbCaptor.capture());

        List<List<Map<String, String>>> rows = (List<List<Map<String, String>>>) kbCaptor.getValue().get("inline_keyboard");
        assertTrue(rows.stream().flatMap(List::stream).anyMatch(b -> "CONV_CUST_PHONE_SKIP".equals(b.get("callback_data"))));

        // Test Skip action
        reset(apiClient);
        dispatcher.dispatch(createCallbackUpdate("CONV_CUST_PHONE_SKIP"), botToken, installId);
        verify(apiClient).sendMessage(eq(botToken), eq(testChatId), contains("Select Estimate to Convert"), eq("Markdown"), any());
    }

    @Test
    void testAlreadyConvertedEstimateIsBlocked() throws Exception {
        // Mark estimate as already converted to invoice #999
        validEstimate.setConvertedInvoiceId(999L);
        validEstimate.setConversionStatus("CONVERTED");
        invoiceRepo.save(validEstimate);

        dispatcher.dispatch(createCallbackUpdate("CONV_EST_PICK|" + validEstimate.getId()), botToken, installId);

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> kbCaptor = ArgumentCaptor.forClass(Map.class);
        verify(apiClient).sendMessage(eq(botToken), eq(testChatId), msgCaptor.capture(), eq("Markdown"), kbCaptor.capture());

        assertTrue(msgCaptor.getValue().contains("Estimate Already Converted"));
        List<List<Map<String, String>>> rows = (List<List<Map<String, String>>>) kbCaptor.getValue().get("inline_keyboard");
        assertTrue(rows.stream().flatMap(List::stream).anyMatch(b -> "CONFIRM_DL_PDF|999".equals(b.get("callback_data"))));
    }

    @Test
    void testIneligibleEstimateIsBlocked() throws Exception {
        validEstimate.setStatus(InvoiceStatus.CANCELLED);
        invoiceRepo.save(validEstimate);

        dispatcher.dispatch(createCallbackUpdate("CONV_EST_PICK|" + validEstimate.getId()), botToken, installId);

        verify(apiClient).sendMessage(eq(botToken), eq(testChatId), contains("Estimate Ineligible for Conversion"), eq("Markdown"), any());
    }

    @Test
    void testEstimateConversionPreviewShowsCompletePersistedDetails() throws Exception {
        dispatcher.dispatch(createCallbackUpdate("CONV_EST_PICK|" + validEstimate.getId()), botToken, installId);

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> kbCaptor = ArgumentCaptor.forClass(Map.class);
        verify(apiClient).sendMessage(eq(botToken), eq(testChatId), msgCaptor.capture(), eq("Markdown"), kbCaptor.capture());

        String text = msgCaptor.getValue();
        assertTrue(text.contains("Acme Industrial Supplies"));
        assertTrue(text.contains("EST-2026-001"));
        assertTrue(text.contains("Heavy Duty Drill Bit"));
        assertTrue(text.contains("₹1000"));
        assertTrue(text.contains("₹180"));
        assertTrue(text.contains("₹1180"));
        assertTrue(text.contains("Convert Estimate EST-2026-001 into an Invoice?"));

        List<List<Map<String, String>>> rows = (List<List<Map<String, String>>>) kbCaptor.getValue().get("inline_keyboard");
        assertTrue(rows.stream().flatMap(List::stream).anyMatch(b -> ("CONV_CONFIRM|" + validEstimate.getId()).equals(b.get("callback_data"))));
    }

    @Test
    void testExecuteConversionReusesDesktopServiceAndTriggersWhatsAppDelivery() throws Exception {
        BigDecimal initialStock = testProduct.getStockQuantity();

        // Confirm conversion
        dispatcher.dispatch(createCallbackUpdate("CONV_CONFIRM|" + validEstimate.getId()), botToken, installId);

        // 1. Verify notification sent to Telegram
        verify(apiClient).sendMessage(eq(botToken), eq(testChatId), contains("Estimate Converted Successfully"), eq("Markdown"), any());

        // 2. Verify source estimate updated
        Invoice reloadedEst = invoiceRepo.findById(validEstimate.getId()).orElseThrow();
        assertEquals("CONVERTED", reloadedEst.getConversionStatus());
        assertNotNull(reloadedEst.getConvertedInvoiceId(), "Must link to resulting invoice");

        // 3. Verify created invoice
        Invoice createdInv = invoiceRepo.findById(reloadedEst.getConvertedInvoiceId()).orElseThrow();
        assertEquals(InvoiceStatus.UNPAID, createdInv.getStatus());
        assertNotNull(createdInv.getInvoiceNumber());
        assertEquals(new BigDecimal("1180.00"), createdInv.getTotalAmount());
        assertEquals(1, createdInv.getItems().size());

        // 4. Verify stock deducted by 2 units (sales invoice stock movement)
        Product reloadedProduct = productRepo.findById(testProduct.getId()).orElseThrow();
        assertEquals(0, initialStock.subtract(new BigDecimal("2")).compareTo(reloadedProduct.getStockQuantity()));

        // 5. Verify queued delivery and WhatsApp eligibility
        List<TelegramDocumentDelivery> deliveries = deliveryRepo.findAll();
        assertFalse(deliveries.isEmpty());
        TelegramDocumentDelivery del = deliveries.get(deliveries.size() - 1);
        assertEquals("INVOICE_PDF", del.getDocumentType());
        assertEquals("CONVERT", del.getOperationType());
        assertEquals(createdInv.getId(), del.getDocumentId());
        assertTrue(TelegramWhatsAppUtils.isEligible(del.getDocumentType(), del.getOperationType()));
    }

    @Test
    void testIdempotencyDoubleConfirmationDoesNotCreateDuplicateInvoice() throws Exception {
        // First conversion
        dispatcher.dispatch(createCallbackUpdate("CONV_CONFIRM|" + validEstimate.getId()), botToken, installId);

        Invoice reloadedEst = invoiceRepo.findById(validEstimate.getId()).orElseThrow();
        Long firstInvoiceId = reloadedEst.getConvertedInvoiceId();
        assertNotNull(firstInvoiceId);

        long invoiceCountBefore = invoiceRepo.count();

        // Simulate immediate second confirmation callback (e.g. double tap or retry)
        reset(apiClient);
        dispatcher.dispatch(createCallbackUpdate("CONV_CONFIRM|" + validEstimate.getId()), botToken, installId);

        // Verify count didn't increase
        assertEquals(invoiceCountBefore, invoiceRepo.count(), "No duplicate invoice may be created");

        // Verify user was informed of existing conversion
        verify(apiClient).sendMessage(eq(botToken), eq(testChatId), contains("already been converted"), eq("Markdown"), any());
    }
}
