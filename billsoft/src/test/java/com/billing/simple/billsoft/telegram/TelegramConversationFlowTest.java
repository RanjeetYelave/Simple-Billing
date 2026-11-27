package com.billing.simple.billsoft.telegram;

import com.billing.simple.billsoft.BillsoftApplication;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.repositories.PartyRepository;
import com.billing.simple.billsoft.repositories.PurchaseOrderRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.telegram.client.TelegramApiClient;
import com.billing.simple.billsoft.telegram.entities.TelegramAuthorizedUser;
import com.billing.simple.billsoft.telegram.entities.TelegramDocumentDelivery;
import com.billing.simple.billsoft.telegram.entities.TelegramUserSession;
import com.billing.simple.billsoft.telegram.repo.TelegramAuthorizedUserRepository;
import com.billing.simple.billsoft.telegram.repo.TelegramDocumentDeliveryRepository;
import com.billing.simple.billsoft.telegram.repo.TelegramUserSessionRepository;
import com.billing.simple.billsoft.telegram.service.TelegramDateUtils;
import com.billing.simple.billsoft.telegram.service.TelegramReadService;
import com.billing.simple.billsoft.telegram.service.TelegramUpdateDispatcher;
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
public class TelegramConversationFlowTest {

    @Autowired
    private TelegramUpdateDispatcher dispatcher;

    @Autowired
    private TelegramReadService readService;

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private InvoiceRepository invoiceRepo;

    @Autowired
    private PartyRepository partyRepo;

    @Autowired
    private PurchaseOrderRepository poRepo;

    @Autowired
    private EmployeeRepository employeeRepo;

    @Autowired
    private LeaveRecordRepository leaveRepo;

    @Autowired
    private EmployeeAdvanceRepository advanceRepo;

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
    private final Long testUserId = 889900L;
    private final Long testChatId = 654321L;
    private final String botToken = "TEST_BOT_TOKEN";
    private final String installId = "LOCAL_TEST_INSTALL";

    private Customer customer;
    private Party party;
    private Employee employee;
    private Invoice invoice;
    private PurchaseOrder purchaseOrder;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentFirmId(testFirmId);

        // Authorize test Telegram user
        TelegramAuthorizedUser authUser = new TelegramAuthorizedUser();
        authUser.setUserId(testUserId);
        authUser.setDisplayName("Test Operator");
        authUser.setStatus("ACTIVE");
        authUser.setAuthorizedAt(LocalDateTime.now());
        userRepo.save(authUser);

        // Establish session with active firm 1
        TelegramUserSession session = new TelegramUserSession(testChatId, testUserId);
        session.setActiveFirmId(testFirmId);
        session.setConversationState("IDLE");
        sessionRepo.save(session);

        // Seed customer
        customer = new Customer();
        customer.setName("Vikram Malhotra");
        customer.setPhone("9876543210");
        customer.setFirmId(testFirmId);
        customer = customerRepo.save(customer);

        // Seed invoice
        invoice = new Invoice();
        invoice.setFirmId(testFirmId);
        invoice.setCustomer(customer);
        invoice.setInvoiceNumber("INV-1042");
        invoice.setInvoiceDate(LocalDateTime.now().minusDays(2));
        invoice.setTotalAmount(BigDecimal.valueOf(18450.0));
        invoice.setStatus(InvoiceStatus.PAID);
        invoice = invoiceRepo.save(invoice);

        // Seed quotation
        Invoice estimate = new Invoice();
        estimate.setFirmId(testFirmId);
        estimate.setCustomer(customer);
        estimate.setEstimateNumber("EST-2005");
        estimate.setInvoiceDate(LocalDateTime.now().minusDays(1));
        estimate.setTotalAmount(BigDecimal.valueOf(5200.0));
        estimate.setStatus(InvoiceStatus.ESTIMATE);
        invoiceRepo.save(estimate);

        // Seed party / vendor
        party = new Party();
        party.setName("Sharma Traders");
        party.setPhone("9123456780");
        party.setFirmId(testFirmId);
        party = partyRepo.save(party);

        // Seed purchase order
        purchaseOrder = new PurchaseOrder();
        purchaseOrder.setFirmId(testFirmId);
        purchaseOrder.setParty(party);
        purchaseOrder.setPartyName(party.getName());
        purchaseOrder.setPoNumber("PO-2026-014");
        purchaseOrder.setPoDate(LocalDate.now().minusDays(3));
        purchaseOrder.setStatus(PurchaseOrderStatus.ISSUED);

        PurchaseOrderItem poItem = new PurchaseOrderItem();
        poItem.setPurchaseOrder(purchaseOrder);
        poItem.setProductName("Copper Wiring Bundle");
        poItem.setQuantity(BigDecimal.valueOf(10));
        poItem.setUnitPrice(BigDecimal.valueOf(4250.00));
        purchaseOrder.getItems().add(poItem);
        purchaseOrder.recalculateTotals();
        purchaseOrder = poRepo.save(purchaseOrder);

        // Seed employee
        employee = new Employee();
        employee.setName("Ramesh Kumar");
        employee.setPhone("9811223344");
        employee.setMonthlyBaseSalary(35000.0);
        employee.setCurrentAdvanceBalance(5000.0);
        employee.setFirmId(testFirmId);
        employee = employeeRepo.save(employee);
    }

    // =========================================================================
    // 1. TelegramDateUtils Tests
    // =========================================================================

    @Test
    void testTelegramDateUtils_QuickFilters() {
        TelegramDateUtils.DateRange thisMonth = TelegramDateUtils.resolveQuickFilter("THIS_MONTH");
        assertNotNull(thisMonth.getFrom());
        assertNotNull(thisMonth.getTo());
        assertEquals("This Month", thisMonth.getLabel());

        TelegramDateUtils.DateRange thisFy = TelegramDateUtils.resolveQuickFilter("THIS_FY");
        assertNotNull(thisFy.getFrom());
        assertEquals(4, thisFy.getFrom().getMonthValue());
        assertEquals(1, thisFy.getFrom().getDayOfMonth());
        assertEquals(3, thisFy.getTo().getMonthValue());
        assertEquals(31, thisFy.getTo().getDayOfMonth());

        TelegramDateUtils.DateRange lastFy = TelegramDateUtils.resolveQuickFilter("LAST_FY");
        assertNotNull(lastFy.getFrom());
        assertTrue(lastFy.getFrom().isBefore(thisFy.getFrom()));
    }

    @Test
    void testTelegramDateUtils_FlexibleDateParsing() {
        assertEquals(LocalDate.of(2026, 11, 27), TelegramDateUtils.parseFlexibleDate("2026-11-27"));
        assertEquals(LocalDate.of(2026, 11, 27), TelegramDateUtils.parseFlexibleDate("27-11-2026"));
        assertEquals(LocalDate.of(2026, 11, 27), TelegramDateUtils.parseFlexibleDate("27/11/2026"));
        assertEquals(LocalDate.now(), TelegramDateUtils.parseFlexibleDate("today"));
        assertEquals(LocalDate.now().plusDays(1), TelegramDateUtils.parseFlexibleDate("tomorrow"));
        assertNull(TelegramDateUtils.parseFlexibleDate("not-a-date"));
    }

    // =========================================================================
    // 2. Download Invoices / Quotation Flow Tests
    // =========================================================================

    @Test
    void testDownloadInvoiceFlow_SearchFirstAndHumanReadableDisplay() throws Exception {
        // Step 1: User taps "Download Invoice"
        ObjectNode cb1 = createCallbackUpdate("DL_TYPE|INVOICE");
        dispatcher.dispatch(cb1, botToken, installId);

        TelegramUserSession sess = sessionRepo.findByChatId(testChatId).orElseThrow();
        assertEquals("DL_SEARCH_CUST", sess.getConversationState());

        // Step 2: User types customer name "Vikram"
        ObjectNode msg1 = createMessageUpdate("Vikram");
        dispatcher.dispatch(msg1, botToken, installId);

        ArgumentCaptor<Map> kbCaptor = ArgumentCaptor.forClass(Map.class);
        verify(apiClient, atLeastOnce()).sendMessage(eq(botToken), eq(testChatId), contains("Select customer"), isNull(), kbCaptor.capture());

        Map<String, Object> kb = kbCaptor.getValue();
        List<List<Map<String, String>>> rows = (List<List<Map<String, String>>>) kb.get("inline_keyboard");
        assertFalse(rows.isEmpty());
        assertTrue(rows.get(0).get(0).get("text").contains("Vikram Malhotra"));
        assertEquals("DL_CUST_SEL|" + customer.getId(), rows.get(0).get(0).get("callback_data"));

        // Step 3: User selects customer -> should display 5 latest human-readable invoices
        ObjectNode cb2 = createCallbackUpdate("DL_CUST_SEL|" + customer.getId());
        dispatcher.dispatch(cb2, botToken, installId);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiClient, atLeastOnce()).sendMessage(eq(botToken), eq(testChatId), textCaptor.capture(), eq("Markdown"), any());

        String listText = textCaptor.getValue();
        assertTrue(listText.contains("INV-1042"), "Must show document number INV-1042");
        assertTrue(listText.contains("₹18450"), "Must show grand total amount");
        assertTrue(listText.contains("PAID"), "Must show status");
        assertFalse(listText.contains(".pdf"), "Must NOT display internal .pdf filename");

        // Step 4: User selects document to preview details before downloading
        ObjectNode cb3 = createCallbackUpdate("DL_DOC_SEL|" + invoice.getId());
        dispatcher.dispatch(cb3, botToken, installId);

        ArgumentCaptor<String> previewTextCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> previewKbCaptor = ArgumentCaptor.forClass(Map.class);
        verify(apiClient, atLeastOnce()).sendMessage(eq(botToken), eq(testChatId), previewTextCaptor.capture(), eq("Markdown"), previewKbCaptor.capture());

        String previewMsg = previewTextCaptor.getValue();
        assertTrue(previewMsg.contains("INV-1042"));
        assertTrue(previewMsg.contains("Vikram Malhotra"));
        assertTrue(previewMsg.contains("Download official PDF document?"));

        // Step 5: User confirms explicit Download PDF
        ObjectNode cb4 = createCallbackUpdate("CONFIRM_DL_PDF|" + invoice.getId());
        dispatcher.dispatch(cb4, botToken, installId);

        List<TelegramDocumentDelivery> queued = deliveryRepo.findByChatIdOrderByCreatedAtDesc(testChatId);
        assertFalse(queued.isEmpty());
        assertEquals("INVOICE_PDF", queued.get(0).getDocumentType());
        assertEquals(invoice.getId(), queued.get(0).getDocumentId());
        assertEquals("INV-1042", queued.get(0).getDocumentNumber());
    }

    // =========================================================================
    // 3. Statements & Business Reports Flow Tests
    // =========================================================================

    @Test
    void testCustomerStatementFlow_SearchPeriodAndPreviewConfirmation() throws Exception {
        // Step 1: User initiates Customer Statement
        ObjectNode cb1 = createCallbackUpdate("RPT_START|CUST");
        dispatcher.dispatch(cb1, botToken, installId);

        assertEquals("RPT_SEARCH_CUST", sessionRepo.findByChatId(testChatId).get().getConversationState());

        // Step 2: User searches customer
        ObjectNode msg1 = createMessageUpdate("Vikram");
        dispatcher.dispatch(msg1, botToken, installId);

        // Step 3: User picks customer
        ObjectNode cb2 = createCallbackUpdate("RPT_CUST_PICK|" + customer.getId());
        dispatcher.dispatch(cb2, botToken, installId);

        // Step 4: User selects quick filter "THIS_FY"
        ObjectNode cb3 = createCallbackUpdate("RPT_PERIOD_PICK|THIS_FY");
        dispatcher.dispatch(cb3, botToken, installId);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> kbCaptor = ArgumentCaptor.forClass(Map.class);
        verify(apiClient, atLeastOnce()).sendMessage(eq(botToken), eq(testChatId), textCaptor.capture(), eq("Markdown"), kbCaptor.capture());

        String previewText = textCaptor.getValue();
        assertTrue(previewText.contains("Customer Statement"));
        assertTrue(previewText.contains("Vikram Malhotra"));
        assertTrue(previewText.contains("From:"));
        assertTrue(previewText.contains("To:"));
        assertTrue(previewText.contains("Ready to generate official statement PDF?"));

        // Step 5: Explicit Generate Statement confirmation
        ObjectNode cb4 = createCallbackUpdate("RPT_CONFIRM_GEN");
        dispatcher.dispatch(cb4, botToken, installId);

        List<TelegramDocumentDelivery> queued = deliveryRepo.findByChatIdOrderByCreatedAtDesc(testChatId);
        assertFalse(queued.isEmpty());
        TelegramDocumentDelivery del = queued.get(0);
        assertEquals("STMT_CUST_PDF", del.getDocumentType());
        assertEquals(customer.getId(), del.getDocumentId());
        assertNotNull(del.getDocumentNumber());
        assertTrue(del.getDocumentNumber().contains(":"), "Document number must contain from:to dates");
    }

    @Test
    void testForecastAnalyticsStatement_HorizonSelectionAndPreview() throws Exception {
        // Step 1: User initiates Forecast & Replenishment
        ObjectNode cb1 = createCallbackUpdate("RPT_START|FORECAST");
        dispatcher.dispatch(cb1, botToken, installId);

        // Step 2: Select 30-day horizon
        ObjectNode cb2 = createCallbackUpdate("RPT_FC_HORIZON|30");
        dispatcher.dispatch(cb2, botToken, installId);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiClient, atLeastOnce()).sendMessage(eq(botToken), eq(testChatId), textCaptor.capture(), eq("Markdown"), any());

        String preview = textCaptor.getValue();
        assertTrue(preview.contains("Forecast & Replenishment Analytics"));
        assertTrue(preview.contains("30 Days"));
        assertTrue(preview.contains("Generate and send analytics report PDF?"));

        // Step 3: Explicit confirmation
        ObjectNode cb3 = createCallbackUpdate("RPT_CONFIRM_GEN");
        dispatcher.dispatch(cb3, botToken, installId);

        List<TelegramDocumentDelivery> queued = deliveryRepo.findByChatIdOrderByCreatedAtDesc(testChatId);
        assertFalse(queued.isEmpty());
        assertEquals("STMT_FORECAST_PDF", queued.get(0).getDocumentType());
        assertEquals(30L, queued.get(0).getDocumentId());
    }

    // =========================================================================
    // 4. Purchase Order Download Flow Tests
    // =========================================================================

    @Test
    void testPurchaseOrderDownloadFlow_VendorSearchAndDetailsPreview() throws Exception {
        // Step 1: User initiates PO download
        ObjectNode cb1 = createCallbackUpdate("PO_DL_START");
        dispatcher.dispatch(cb1, botToken, installId);

        assertEquals("PO_DL_SEARCH_VENDOR", sessionRepo.findByChatId(testChatId).get().getConversationState());

        // Step 2: Search vendor name
        ObjectNode msg1 = createMessageUpdate("Sharma");
        dispatcher.dispatch(msg1, botToken, installId);

        // Step 3: Select matching vendor -> renders latest POs
        ObjectNode cb2 = createCallbackUpdate("PO_DL_VENDOR_PICK|" + party.getId());
        dispatcher.dispatch(cb2, botToken, installId);

        ArgumentCaptor<String> listCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiClient, atLeastOnce()).sendMessage(eq(botToken), eq(testChatId), listCaptor.capture(), eq("Markdown"), any());

        String poListText = listCaptor.getValue();
        assertTrue(poListText.contains("PO-2026-014"));
        assertTrue(poListText.contains("42500"));
        assertTrue(poListText.contains("ISSUED"));
        assertFalse(poListText.contains(".pdf"));

        // Step 4: Preview PO details
        ObjectNode cb3 = createCallbackUpdate("PO_DL_SEL|" + purchaseOrder.getId());
        dispatcher.dispatch(cb3, botToken, installId);

        ArgumentCaptor<String> poPreviewCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiClient, atLeastOnce()).sendMessage(eq(botToken), eq(testChatId), poPreviewCaptor.capture(), eq("Markdown"), any());

        String previewMsg = poPreviewCaptor.getValue();
        assertTrue(previewMsg.contains("PO-2026-014"));
        assertTrue(previewMsg.contains("Sharma Traders"));
        assertTrue(previewMsg.contains("Download official Purchase Order PDF?"));

        // Step 5: Explicit Download PDF confirmation
        ObjectNode cb4 = createCallbackUpdate("CONFIRM_PO_DL_PDF|" + purchaseOrder.getId());
        dispatcher.dispatch(cb4, botToken, installId);

        List<TelegramDocumentDelivery> queued = deliveryRepo.findByChatIdOrderByCreatedAtDesc(testChatId);
        assertFalse(queued.isEmpty());
        assertEquals("PO_PDF", queued.get(0).getDocumentType());
        assertEquals(purchaseOrder.getId(), queued.get(0).getDocumentId());
        assertEquals("PO-2026-014", queued.get(0).getDocumentNumber());
    }

    // =========================================================================
    // 5. Employee Leaves, Advances & Payroll Flow Tests
    // =========================================================================

    @Test
    void testEmployeeLeaveFlow_SearchDatesTypeAndConfirmation() throws Exception {
        // Step 1: Initiate leave flow
        ObjectNode cb1 = createCallbackUpdate("HR_LEAVE_START");
        dispatcher.dispatch(cb1, botToken, installId);

        assertEquals("HR_SEARCH_EMP", sessionRepo.findByChatId(testChatId).get().getConversationState());

        // Step 2: Search employee
        ObjectNode msg1 = createMessageUpdate("Ramesh");
        dispatcher.dispatch(msg1, botToken, installId);

        // Step 3: Select employee
        ObjectNode cb2 = createCallbackUpdate("HR_EMP_PICK|" + employee.getId());
        dispatcher.dispatch(cb2, botToken, installId);

        assertEquals("HR_LEAVE_DATES", sessionRepo.findByChatId(testChatId).get().getConversationState());

        // Step 4: Enter leave dates
        ObjectNode msg2 = createMessageUpdate("2026-11-28 to 2026-11-29");
        dispatcher.dispatch(msg2, botToken, installId);

        // Step 5: Select leave type CASUAL
        ObjectNode cb3 = createCallbackUpdate("HR_LEAVE_TYPE_PICK|CASUAL");
        dispatcher.dispatch(cb3, botToken, installId);

        assertEquals("HR_LEAVE_REASON", sessionRepo.findByChatId(testChatId).get().getConversationState());

        // Step 6: Enter reason
        ObjectNode msg3 = createMessageUpdate("Family function");
        dispatcher.dispatch(msg3, botToken, installId);

        ArgumentCaptor<String> previewCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiClient, atLeastOnce()).sendMessage(eq(botToken), eq(testChatId), previewCaptor.capture(), eq("Markdown"), any());

        String preview = previewCaptor.getValue();
        assertTrue(preview.contains("Confirm Employee Leave"));
        assertTrue(preview.contains("Ramesh Kumar"));
        assertTrue(preview.contains("CASUAL"));
        assertTrue(preview.contains("Family function"));

        // Step 7: Confirm & save leave
        ObjectNode cb4 = createCallbackUpdate("HR_CONFIRM_LEAVE");
        dispatcher.dispatch(cb4, botToken, installId);

        List<LeaveRecord> leaves = leaveRepo.findByEmployeeIdOrderByStartDateDesc(employee.getId());
        assertFalse(leaves.isEmpty());
        assertEquals("CASUAL", leaves.get(0).getType());
        assertEquals(LocalDate.of(2026, 11, 28), leaves.get(0).getStartDate());
        assertEquals(LocalDate.of(2026, 11, 29), leaves.get(0).getEndDate());
    }

    @Test
    void testSalaryAdvanceFlow_SearchAmountReasonAndConfirmation() throws Exception {
        // Step 1: Initiate advance
        ObjectNode cb1 = createCallbackUpdate("HR_ADV_START");
        dispatcher.dispatch(cb1, botToken, installId);

        // Step 2: Search employee
        ObjectNode msg1 = createMessageUpdate("Ramesh");
        dispatcher.dispatch(msg1, botToken, installId);

        // Step 3: Pick employee
        ObjectNode cb2 = createCallbackUpdate("HR_EMP_PICK|" + employee.getId());
        dispatcher.dispatch(cb2, botToken, installId);

        assertEquals("HR_ADVANCE_AMOUNT", sessionRepo.findByChatId(testChatId).get().getConversationState());

        // Step 4: Enter amount
        ObjectNode msg2 = createMessageUpdate("4000");
        dispatcher.dispatch(msg2, botToken, installId);

        assertEquals("HR_ADVANCE_REASON", sessionRepo.findByChatId(testChatId).get().getConversationState());

        // Step 5: Enter reason / repayment
        ObjectNode msg3 = createMessageUpdate("Medical expense - deduct 1000/mo");
        dispatcher.dispatch(msg3, botToken, installId);

        // Step 6: Confirm & record advance
        ObjectNode cb3 = createCallbackUpdate("HR_CONFIRM_ADV");
        dispatcher.dispatch(cb3, botToken, installId);

        List<EmployeeAdvance> advances = advanceRepo.findByEmployeeIdOrderByDateDesc(employee.getId());
        assertFalse(advances.isEmpty());
        assertEquals(4000.0, advances.get(0).getAmount());
        assertTrue(advances.get(0).getDescription().contains("Medical expense"));
    }

    // =========================================================================
    // 6. Fast Expense / Savings Parsing Tests
    // =========================================================================

    @Test
    void testFastExpenseAndSavingInputs() throws Exception {
        ObjectNode msg1 = createMessageUpdate("expense 450 Diesel fuel for delivery van");
        dispatcher.dispatch(msg1, botToken, installId);

        ArgumentCaptor<String> expCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiClient, atLeastOnce()).sendMessage(eq(botToken), eq(testChatId), expCaptor.capture(), eq("Markdown"), isNull());

        String expResp = expCaptor.getValue();
        assertTrue(expResp.contains("Expense Recorded Successfully"));
        assertTrue(expResp.contains("₹450"));
        assertTrue(expResp.contains("Diesel fuel for delivery van"));

        ObjectNode msg2 = createMessageUpdate("saved 2500 Emergency Reserve");
        dispatcher.dispatch(msg2, botToken, installId);

        ArgumentCaptor<String> savCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiClient, atLeastOnce()).sendMessage(eq(botToken), eq(testChatId), savCaptor.capture(), eq("Markdown"), isNull());

        String savResp = savCaptor.getValue();
        assertTrue(savResp.contains("Savings Recorded Successfully"));
        assertTrue(savResp.contains("₹2500"));
        assertTrue(savResp.contains("Emergency Reserve"));
    }

    // =========================================================================
    // Helper Methods for Simulating Telegram Updates
    // =========================================================================

    private ObjectNode createCallbackUpdate(String data) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode cb = root.putObject("callback_query");
        cb.put("id", "CB_" + System.currentTimeMillis());
        cb.put("data", data);

        ObjectNode from = cb.putObject("from");
        from.put("id", testUserId);
        from.put("first_name", "Test");
        from.put("username", "testoperator");

        ObjectNode msg = cb.putObject("message");
        msg.put("message_id", 1001);
        ObjectNode chat = msg.putObject("chat");
        chat.put("id", testChatId);
        chat.put("type", "private");

        return root;
    }

    private ObjectNode createMessageUpdate(String text) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode msg = root.putObject("message");
        msg.put("message_id", 2002);
        msg.put("text", text);

        ObjectNode from = msg.putObject("from");
        from.put("id", testUserId);
        from.put("first_name", "Test");
        from.put("username", "testoperator");

        ObjectNode chat = msg.putObject("chat");
        chat.put("id", testChatId);
        chat.put("type", "private");

        return root;
    }
}
