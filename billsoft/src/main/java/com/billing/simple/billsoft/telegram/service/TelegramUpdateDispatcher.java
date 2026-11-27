package com.billing.simple.billsoft.telegram.service;

import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repo.InvoiceRepository;
import com.billing.simple.billsoft.repositories.PartyRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.service.InvoiceService;
import com.billing.simple.billsoft.telegram.client.TelegramApiClient;
import com.billing.simple.billsoft.telegram.entities.TelegramDraft;
import com.billing.simple.billsoft.telegram.entities.TelegramDraftItem;
import com.billing.simple.billsoft.telegram.entities.TelegramUserSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TelegramUpdateDispatcher {

    private static final Logger log = LoggerFactory.getLogger(TelegramUpdateDispatcher.class);
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final TelegramSecurityManager securityManager;
    private final TelegramSessionManager sessionManager;
    private final TelegramReadService readService;
    private final TelegramDraftService draftService;
    private final TelegramDeliveryService deliveryService;
    private final TelegramApiClient apiClient;
    private final ObjectMapper objectMapper;
    private final CustomerRepository customerRepository;
    private final PartyRepository partyRepository;
    private final InvoiceService invoiceService;
    private final InvoiceRepository invoiceRepository;

    // Line item staging during draft creation: draftId -> StagedItemState
    private final Map<Long, StagedItemState> stagedItems = new ConcurrentHashMap<>();

    // Multi-step conversational session context keyed by chatId
    private final Map<Long, ConversationContext> contexts = new ConcurrentHashMap<>();

    private static final Pattern FAST_EXPENSE_PATTERN = Pattern
            .compile("(?i)^expense\\s+(\\d+(?:\\.\\d{1,2})?)(?:\\s+(.*))?$");
    private static final Pattern FAST_SAVING_PATTERN = Pattern
            .compile("(?i)^saved?\\s+(\\d+(?:\\.\\d{1,2})?)(?:\\s+(.*))?$");

    public TelegramUpdateDispatcher(TelegramSecurityManager securityManager,
                                    TelegramSessionManager sessionManager,
                                    TelegramReadService readService,
                                    TelegramDraftService draftService,
                                    TelegramDeliveryService deliveryService,
                                    TelegramApiClient apiClient,
                                    ObjectMapper objectMapper,
                                    CustomerRepository customerRepository,
                                    PartyRepository partyRepository,
                                    InvoiceService invoiceService,
                                    InvoiceRepository invoiceRepository) {
        this.securityManager = securityManager;
        this.sessionManager = sessionManager;
        this.readService = readService;
        this.draftService = draftService;
        this.deliveryService = deliveryService;
        this.apiClient = apiClient;
        this.objectMapper = objectMapper;
        this.customerRepository = customerRepository;
        this.partyRepository = partyRepository;
        this.invoiceService = invoiceService;
        this.invoiceRepository = invoiceRepository;
    }

    public void dispatch(JsonNode update, String botToken, String installationId) {
        try {
            if (update.has("message")) {
                handleMessage(update.get("message"), botToken, installationId);
            } else if (update.has("callback_query")) {
                handleCallbackQuery(update.get("callback_query"), botToken, installationId);
            }
        } catch (Exception e) {
            log.error("Error processing Telegram update: {}", e.getMessage(), e);
        }
    }

    // =========================================================================
    // Text Message Handling
    // =========================================================================

    private void handleMessage(JsonNode msg, String botToken, String installationId) throws Exception {
        if (!msg.has("from") || !msg.has("chat"))
            return;

        JsonNode chat = msg.get("chat");
        String chatType = chat.path("type").asText("private");
        Long chatId = chat.path("id").asLong();
        Long userId = msg.get("from").path("id").asLong();
        String text = msg.path("text").asText("").trim();
        String firstName = msg.get("from").path("first_name").asText("");
        String username = msg.get("from").path("username").asText(null);

        // V1 restriction: private chats only
        if (!"private".equalsIgnoreCase(chatType)) {
            apiClient.sendMessage(botToken, chatId, "⛔ RupeeCRM Assistant operates strictly in private chats.", null,
                    null);
            return;
        }

        // Parse command tokens and strip optional @botname suffix
        String[] tokens = text.split("\\s+");
        String rawCmd = tokens.length > 0 ? tokens[0].trim().toLowerCase() : "";
        int atIdx = rawCmd.indexOf('@');
        String cmd = atIdx > 0 ? rawCmd.substring(0, atIdx) : rawCmd;
        String arg = tokens.length > 1 ? tokens[1].trim() : null;

        // Account pairing command: /start [CODE]
        if ("/start".equals(cmd) && arg != null && !arg.isBlank()) {
            TelegramSecurityManager.PairingResult result = securityManager.attemptPairing(userId, firstName, username,
                    arg);
            if (result.isSuccess()) {
                sessionManager.getOrCreateSession(chatId, userId);
                sendFirmSelectionMenu(chatId, botToken,
                        "✅ *Device Paired Successfully!*\nWelcome, " + firstName + ".\nPlease select an active firm:");
            } else if (result.isPendingApproval()) {
                apiClient.sendMessage(botToken, chatId,
                        "⏳ *Pairing Code Accepted — Pending Desktop Approval*\n\n" +
                                "Welcome, *" + firstName + "* (Telegram ID: `" + userId + "`).\n\n" +
                                "Your pairing request has been registered.\n" +
                                "Please ask the desktop owner to click *Approve* under *Settings → Telegram Bot* on the desktop to activate your access.",
                        "Markdown", null);
            } else {
                apiClient.sendMessage(botToken, chatId, "❌ " + result.getMessage(), null, null);
            }
            return;
        }

        // Bare /start command
        if ("/start".equals(cmd)) {
            if (securityManager.isUserAuthorized(userId)) {
                securityManager.updateLastActive(userId);
                TelegramUserSession session = sessionManager.getOrCreateSession(chatId, userId);
                sendMainMenu(chatId, botToken, session);
                return;
            } else {
                Optional<com.billing.simple.billsoft.telegram.entities.TelegramAuthorizedUser> userOpt = securityManager
                        .getUser(userId);
                if (userOpt.isPresent() && "PENDING_APPROVAL".equalsIgnoreCase(userOpt.get().getStatus())) {
                    apiClient.sendMessage(botToken, chatId,
                            "⏳ *Account Pending Desktop Approval*\n\n" +
                                    "Your Telegram account (ID: `" + userId
                                    + "`) is awaiting approval by the RupeeCRM desktop owner.\n" +
                                    "Please click *Approve* under *Settings → Telegram Bot* on the desktop to activate access.",
                            "Markdown", null);
                    return;
                } else {
                    apiClient.sendMessage(botToken, chatId,
                            "👋 *Welcome to RupeeCRM Assistant*\n\n" +
                                    "Your Telegram User ID is: `" + userId + "`\n\n" +
                                    "To link your account:\n" +
                                    "1. Open RupeeCRM on your desktop.\n" +
                                    "2. Navigate to *Settings → Telegram Bot*.\n" +
                                    "3. Click *➕ Generate Pairing Code*.\n" +
                                    "4. Send `/start YOUR-CODE` here.",
                            "Markdown", null);
                    return;
                }
            }
        }

        // Validate user authorization for all other interactions
        if (!securityManager.isUserAuthorized(userId)) {
            apiClient.sendMessage(botToken, chatId,
                    "⛔ *Unauthorized Device*\n\n" +
                            "Your Telegram User ID is: `" + userId + "`\n\n" +
                            "This Telegram account is not linked to RupeeCRM.\n" +
                            "Ask the desktop owner to generate a pairing code under *Settings → Telegram Bot*, then send `/start <CODE>` here.",
                    "Markdown", null);
            return;
        }

        securityManager.updateLastActive(userId);
        TelegramUserSession session = sessionManager.getOrCreateSession(chatId, userId);

        // Global commands
        if ("/cancel".equals(cmd)) {
            sessionManager.clearSessionState(chatId);
            stagedItems.remove(session.getActiveDraftId());
            contexts.remove(chatId);
            apiClient.sendMessage(botToken, chatId, "Cancelled current operation.", null, null);
            sendMainMenu(chatId, botToken, session);
            return;
        }

        if ("/firm".equals(cmd)) {
            sendFirmSelectionMenu(chatId, botToken, "🏢 *Select Active Firm:*");
            return;
        }

        if ("/menu".equals(cmd) || "/help".equals(cmd)) {
            contexts.remove(chatId);
            sessionManager.clearSessionState(chatId);
            sendMainMenu(chatId, botToken, session);
            return;
        }

        // Verify active firm selected
        if (session.getActiveFirmId() == null) {
            sendFirmSelectionMenu(chatId, botToken, "⚠️ *No firm selected.* Please choose an active firm to begin:");
            return;
        }

        Long firmId = session.getActiveFirmId();

        // ---------------------------------------------------------------------
        // Fast Natural-Language Expense/Savings inputs
        // ---------------------------------------------------------------------
        Matcher expenseMatcher = FAST_EXPENSE_PATTERN.matcher(text);
        if (expenseMatcher.matches()) {
            BigDecimal amt = new BigDecimal(expenseMatcher.group(1));
            String desc = expenseMatcher.group(2) != null && !expenseMatcher.group(2).isBlank()
                    ? expenseMatcher.group(2).trim()
                    : "General Expense";
            Expense saved = draftService.recordExpense(firmId, amt, desc, "General");
            apiClient.sendMessage(botToken, chatId,
                    "✅ *Expense Recorded Successfully!*\n\n" +
                            "Amount: *₹" + saved.getAmount() + "*\n" +
                            "Description: *" + saved.getTitle() + "*\n" +
                            "Date: *" + saved.getExpenseDate() + "*",
                    "Markdown", null);
            return;
        }

        Matcher savingMatcher = FAST_SAVING_PATTERN.matcher(text);
        if (savingMatcher.matches()) {
            BigDecimal amt = new BigDecimal(savingMatcher.group(1));
            String desc = savingMatcher.group(2) != null && !savingMatcher.group(2).isBlank()
                    ? savingMatcher.group(2).trim()
                    : "Savings Deposit";
            SavingRecord saved = draftService.recordSaving(firmId, amt, desc);
            apiClient.sendMessage(botToken, chatId,
                    "✅ *Savings Recorded Successfully!*\n\n" +
                            "Amount: *₹" + saved.getAmount() + "*\n" +
                            "Purpose: *" + saved.getTitle() + "*\n" +
                            "Date: *" + saved.getSavingDate() + "*",
                    "Markdown", null);
            return;
        }

        // ---------------------------------------------------------------------
        // Contextual Conversational State Routing
        // ---------------------------------------------------------------------
        String state = session.getConversationState();
        ConversationContext ctx = contexts.get(chatId);

        if (!text.startsWith("/")) {
            // WhatsApp alternate / manual number entry
            if (state != null && state.startsWith("WA_INPUT|")) {
                Long deliveryId = Long.parseLong(state.substring("WA_INPUT|".length()));
                sessionManager.clearSessionState(chatId);
                deliveryService.sendAlternateWhatsAppLink(deliveryId, text, botToken);
                return;
            } else if ("DRAFT_CUST_PHONE".equals(state)) {
                handleDraftCustomerPhoneInput(chatId, session, text, botToken);
                return;
            } else if ("PO_VENDOR_PHONE".equals(state)) {
                handleDraftVendorPhoneInput(chatId, session, text, botToken);
                return;
            }

            // Estimate-to-Invoice Conversion Handlers
            if ("CONV_SEARCH_CUST".equals(state)) {
                handleConvertCustomerSearch(chatId, firmId, text, botToken);
                return;
            } else if ("CONV_CUST_PHONE".equals(state)) {
                handleConvertCustomerPhoneInput(chatId, session, text, botToken);
                return;
            } else if ("CONV_CUSTOM_START".equals(state)) {
                handleConvertCustomDateStart(chatId, firmId, text, botToken);
                return;
            } else if ("CONV_CUSTOM_END".equals(state)) {
                handleConvertCustomDateEnd(chatId, firmId, text, botToken);
                return;
            }

            // 1. Create Invoice / Quotation / PO Draft Input Handlers
            if ("AWAITING_CUSTOMER".equals(state)) {
                handleCustomerSearchInput(chatId, session, text, botToken);
                return;
            } else if ("ADDING_ITEM_SEARCH".equals(state)) {
                handleItemSearchInput(chatId, session, text, botToken);
                return;
            } else if ("ENTERING_QTY".equals(state)) {
                handleItemQtyInput(chatId, session, text, botToken);
                return;
            } else if ("ENTERING_RATE".equals(state)) {
                handleItemRateInput(chatId, session, text, botToken);
                return;
            }

            // 2. Download Search Handlers
            if ("DL_SEARCH_CUST".equals(state)) {
                handleDownloadCustomerSearch(chatId, firmId, text, botToken);
                return;
            } else if ("DL_CUSTOM_START".equals(state)) {
                handleDownloadCustomDateStart(chatId, firmId, text, botToken);
                return;
            } else if ("DL_CUSTOM_END".equals(state)) {
                handleDownloadCustomDateEnd(chatId, firmId, text, botToken);
                return;
            } else if ("PO_DL_SEARCH_VENDOR".equals(state)) {
                handlePoDownloadVendorSearch(chatId, firmId, text, botToken);
                return;
            } else if ("PO_DL_CUSTOM_START".equals(state)) {
                handlePoDownloadCustomDateStart(chatId, firmId, text, botToken);
                return;
            } else if ("PO_DL_CUSTOM_END".equals(state)) {
                handlePoDownloadCustomDateEnd(chatId, firmId, text, botToken);
                return;
            }

            // 3. Create PO Search
            if ("PO_CREATE_SEARCH_VENDOR".equals(state)) {
                handlePoCreateVendorSearch(chatId, firmId, userId, text, botToken);
                return;
            }

            // 4. Statements Search & Custom Date Handlers
            if ("RPT_SEARCH_CUST".equals(state)) {
                handleReportCustomerSearch(chatId, firmId, text, botToken);
                return;
            } else if ("RPT_SEARCH_PARTY".equals(state)) {
                handleReportPartySearch(chatId, firmId, text, botToken);
                return;
            } else if ("RPT_CUSTOM_START".equals(state)) {
                handleReportCustomDateStart(chatId, text, botToken);
                return;
            } else if ("RPT_CUSTOM_END".equals(state)) {
                handleReportCustomDateEnd(chatId, text, botToken);
                return;
            }

            // 5. HR Conversational Inputs
            if ("HR_SEARCH_EMP".equals(state)) {
                handleHrEmployeeSearch(chatId, firmId, text, botToken);
                return;
            } else if ("HR_LEAVE_DATES".equals(state)) {
                handleHrLeaveDatesInput(chatId, text, botToken);
                return;
            } else if ("HR_LEAVE_REASON".equals(state)) {
                handleHrLeaveReasonInput(chatId, text, botToken);
                return;
            } else if ("HR_ADVANCE_AMOUNT".equals(state)) {
                handleHrAdvanceAmountInput(chatId, text, botToken);
                return;
            } else if ("HR_ADVANCE_REASON".equals(state)) {
                handleHrAdvanceReasonInput(chatId, text, botToken);
                return;
            } else if ("HR_PAYROLL_MONTH".equals(state)) {
                handleHrPayrollMonthInput(chatId, firmId, text, botToken);
                return;
            } else if ("HR_PAYROLL_ADV_DEDUCT".equals(state)) {
                handleHrPayrollAdvanceDeductInput(chatId, firmId, text, botToken);
                return;
            }

            // 6. Planner Form Inputs
            if ("PL_AWAITING_EXPENSE_INPUT".equals(state)) {
                handlePlannerExpenseInput(chatId, firmId, text, botToken);
                return;
            } else if ("PL_AWAITING_SAVING_INPUT".equals(state)) {
                handlePlannerSavingInput(chatId, firmId, text, botToken);
                return;
            }
        }

        // Explicit command routes
        if ("/newinvoice".equals(cmd) || "/create".equals(cmd)) {
            handleStartNewDocument(chatId, userId, firmId, "INVOICE", botToken);
        } else if ("/newestimate".equals(cmd) || "/newquote".equals(cmd)) {
            handleStartNewDocument(chatId, userId, firmId, "ESTIMATE", botToken);
        } else if ("/convert".equals(cmd) || "/convertestimate".equals(cmd) || "/convertquote".equals(cmd)) {
            handleStartEstimateConversion(chatId, botToken);
        } else if ("/download".equals(cmd) || "/invoices".equals(cmd)) {
            sendDownloadSubMenu(chatId, botToken);
        } else if ("/reports".equals(cmd)) {
            sendReportsSubMenu(chatId, botToken);
        } else if ("/po".equals(cmd)) {
            sendPoSubMenu(chatId, botToken);
        } else if ("/hr".equals(cmd)) {
            sendHrSubMenu(chatId, botToken);
        } else if ("/planner".equals(cmd) || "/record".equals(cmd)) {
            sendPlannerSubMenu(chatId, botToken);
        } else if ("/dashboard".equals(cmd) || "/customers".equals(cmd) || "/stock".equals(cmd)
                || "/debtors".equals(cmd)) {
            apiClient.sendMessage(botToken, chatId,
                    "ℹ️ Standalone queries are now organized under the 6 primary business functions.\n\n" +
                            "• For Statements & Financial Reports: send /reports\n" +
                            "• For Invoices & Quotations: send /download\n" +
                            "• Send /menu to view all 6 functions.",
                    "Markdown", null);
        } else {
            apiClient.sendMessage(botToken, chatId, "Unrecognized command. Send /menu for available options.", null,
                    null);
        }
    }

    // =========================================================================
    // Callback Query Handling
    // =========================================================================

    private void handleCallbackQuery(JsonNode cb, String botToken, String installationId) throws Exception {
        String callbackId = cb.path("id").asText();
        String data = cb.path("data").asText();
        JsonNode msg = cb.path("message");
        Long chatId = msg.path("chat").path("id").asLong();
        Long userId = cb.path("from").path("id").asLong();

        if (!securityManager.isUserAuthorized(userId)) {
            apiClient.answerCallbackQuery(botToken, callbackId, "Unauthorized device");
            return;
        }

        securityManager.updateLastActive(userId);
        TelegramUserSession session = sessionManager.getOrCreateSession(chatId, userId);
        apiClient.answerCallbackQuery(botToken, callbackId, null);

        String[] parts = data.split("\\|");
        String action = parts[0];
        Long firmId = session.getActiveFirmId();

        switch (action) {
            case "NAV_MENU":
                contexts.remove(chatId);
                sessionManager.clearSessionState(chatId);
                sendMainMenu(chatId, botToken, session);
                break;

            case "NAV_FIRM":
                sendFirmSelectionMenu(chatId, botToken, "🏢 *Select Active Firm:*");
                break;

            case "F": // Select Firm: F|{firmId}
                if (parts.length > 1) {
                    Long selectedFirmId = Long.parseLong(parts[1]);
                    sessionManager.setChatFirm(chatId, selectedFirmId);
                    Optional<FirmDetails> fd = readService.getFirm(selectedFirmId);
                    String firmName = fd.map(FirmDetails::getFirmName).orElse("Firm #" + selectedFirmId);
                    apiClient.sendMessage(botToken, chatId, "✅ Active firm set to: *" + firmName + "*", "Markdown",
                            null);
                    session.setActiveFirmId(selectedFirmId);
                    sendMainMenu(chatId, botToken, session);
                }
                break;

            // =================================================================
            // 1. Create Invoice / Quotation
            // =================================================================
            case "M_CREATE":
                sendCreateChoiceMenu(chatId, botToken);
                break;

            case "M_CREATE_INV":
                handleStartNewDocument(chatId, userId, firmId, "INVOICE", botToken);
                break;

            case "M_CREATE_EST":
                handleStartNewDocument(chatId, userId, firmId, "ESTIMATE", botToken);
                break;

            case "M_CONVERT_EST":
                handleStartEstimateConversion(chatId, botToken);
                break;

            case "CONV_CUST_SEL":
                if (parts.length > 1) {
                    Long custId = Long.parseLong(parts[1]);
                    handleConvertCustomerSelected(chatId, firmId, custId, botToken);
                }
                break;

            case "CONV_CUST_PHONE_SKIP":
                handleConvertCustomerPhoneSkip(chatId, firmId, botToken);
                break;

            case "CONV_PAGE":
                if (parts.length > 1) {
                    ConversationContext convCtx = contexts.get(chatId);
                    if (convCtx != null) {
                        convCtx.pageNumber = Integer.parseInt(parts[1]);
                        renderConvertEstimateList(chatId, firmId, convCtx, botToken);
                    }
                }
                break;

            case "CONV_EST_PICK":
                if (parts.length > 1) {
                    Long estimateId = Long.parseLong(parts[1]);
                    handleConvertEstimatePick(chatId, firmId, estimateId, botToken);
                }
                break;

            case "CONV_CONFIRM":
                if (parts.length > 1) {
                    Long estimateId = Long.parseLong(parts[1]);
                    handleExecuteEstimateConversion(chatId, userId, firmId, estimateId, installationId, botToken);
                }
                break;

            case "CONV_EST_BACK": {
                ConversationContext convCtx = contexts.get(chatId);
                if (convCtx != null) {
                    renderConvertEstimateList(chatId, firmId, convCtx, botToken);
                }
                break;
            }

            case "CONV_RETRY_CUST": {
                ConversationContext convCtx = contexts.computeIfAbsent(chatId, k -> new ConversationContext());
                convCtx.flow = "CONVERT_EST";
                convCtx.pageNumber = 0;
                convCtx.filterFrom = null;
                convCtx.filterTo = null;
                sessionManager.setConversationState(chatId, "CONV_SEARCH_CUST");
                apiClient.sendMessage(botToken, chatId, "🔍 Enter customer name to search for estimates:", null,
                        Map.of("inline_keyboard", List.of(List.of(Map.of("text", "🔙 Back to Menu", "callback_data", "NAV_MENU")))));
                break;
            }

            case "CONV_DATE_FILTER": {
                ConversationContext convCtx = contexts.get(chatId);
                if (convCtx != null) {
                    sendConvertDateFilterMenu(chatId, botToken);
                }
                break;
            }

            case "CONV_RANGE":
                if (parts.length > 1) {
                    handleConvertDateRangeFilter(chatId, firmId, parts[1], botToken);
                }
                break;

            case "CUST": // Select Customer for Draft: CUST|{customerId}
                if (parts.length > 1 && session.getActiveDraftId() != null) {
                    Long custId = Long.parseLong(parts[1]);
                    Optional<Customer> cust = readService.getCustomerById(firmId, custId);
                    if (cust.isPresent()) {
                        String phone = cust.get().getPhone();
                        TelegramWhatsAppUtils.PhoneResult phoneRes = (phone != null && !phone.isBlank())
                                ? TelegramWhatsAppUtils.normalizePhone(phone)
                                : null;
                        if (phoneRes != null && phoneRes.isValid()) {
                            draftService.setCustomer(session.getActiveDraftId(), custId, cust.get().getName(),
                                    phoneRes.getNormalizedNumber());
                            sessionManager.setConversationState(chatId, "ADDING_ITEM_SEARCH");
                            apiClient.sendMessage(botToken, chatId,
                                    "Customer: *" + cust.get().getName() + "* (" + phoneRes.getHumanFormatted()
                                            + ")\n\n🔍 *Step 2:* Enter product name or barcode to search:",
                                    "Markdown", null);
                        } else {
                            draftService.setCustomer(session.getActiveDraftId(), custId, cust.get().getName(), null);
                            sessionManager.setConversationState(chatId, "DRAFT_CUST_PHONE");
                            Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                                    List.of(Map.of("text", "⏭ Skip Mobile Number", "callback_data",
                                            "DRAFT_CUST_PHONE_SKIP"))));
                            apiClient.sendMessage(botToken, chatId,
                                    "Customer: *" + cust.get().getName()
                                            + "*\n\n📱 Enter customer mobile number (for WhatsApp sharing), or tap Skip:",
                                    "Markdown", keyboard);
                        }
                    }
                }
                break;

            case "CUST_NEW": // New customer creation confirmation
                if (session.getActiveDraftId() != null && parts.length > 1) {
                    String custName = parts[1];
                    draftService.setCustomer(session.getActiveDraftId(), null, custName, null);
                    sessionManager.setConversationState(chatId, "DRAFT_CUST_PHONE");
                    Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                            List.of(Map.of("text", "⏭ Skip Mobile Number", "callback_data", "DRAFT_CUST_PHONE_SKIP"))));
                    apiClient.sendMessage(botToken, chatId,
                            "New Customer: *" + custName
                                    + "*\n\n📱 Enter customer mobile number (for WhatsApp sharing), or tap Skip:",
                            "Markdown", keyboard);
                }
                break;

            case "PROD": // Select Product for Draft: PROD|{productId}
                if (parts.length > 1 && session.getActiveDraftId() != null) {
                    Long prodId = Long.parseLong(parts[1]);
                    Optional<Product> prod = readService.getProductById(firmId, prodId);
                    if (prod.isPresent()) {
                        Product p = prod.get();
                        StagedItemState staged = new StagedItemState();
                        staged.productId = p.getId();
                        staged.itemName = p.getName();
                        staged.unit = p.getUnit() != null ? p.getUnit() : "Piece";
                        staged.rate = p.getPrice() != null ? p.getPrice() : BigDecimal.ZERO;
                        stagedItems.put(session.getActiveDraftId(), staged);

                        sessionManager.setConversationState(chatId, "ENTERING_QTY");
                        apiClient.sendMessage(botToken, chatId,
                                "Selected: *" + p.getName() + "*\nConfigured Unit: *" + staged.unit
                                        + "*\nCatalogue Rate: *₹" + staged.rate + "*\n\n👉 Enter Quantity (e.g. 5):",
                                "Markdown", null);
                    }
                }
                break;

            case "RATE_DEF": // Use catalogue rate
                if (session.getActiveDraftId() != null) {
                    StagedItemState staged = stagedItems.get(session.getActiveDraftId());
                    if (staged != null && staged.qty != null) {
                        finalizeStagedItem(chatId, session, staged, botToken);
                    }
                }
                break;

            case "ITEM_CONFIRM": // Confirmed line item
                if (session.getActiveDraftId() != null) {
                    StagedItemState staged = stagedItems.remove(session.getActiveDraftId());
                    if (staged != null) {
                        draftService.addItem(session.getActiveDraftId(), staged.productId, staged.itemName,
                                staged.variantDetails, staged.unit, staged.qty, staged.rate, BigDecimal.ZERO);
                        promptNextItemAction(chatId, session.getActiveDraftId(), botToken);
                    }
                }
                break;

            case "ITEM_NEXT": // Add another item
                sessionManager.setConversationState(chatId, "ADDING_ITEM_SEARCH");
                apiClient.sendMessage(botToken, chatId, "🔍 Enter product name or barcode to search:", null, null);
                break;

            case "ITEM_PREVIEW": // Finish & preview draft
                TelegramDraft draft = draftService.getDraft(session.getActiveDraftId()).orElse(null);
                if (draft != null && "PURCHASE_ORDER".equalsIgnoreCase(draft.getDocumentType())) {
                    handlePoDraftPreview(chatId, draft.getDraftId(), botToken);
                } else {
                    handleDraftPreview(chatId, session.getActiveDraftId(), botToken);
                }
                break;

            case "DRAFT_EXEC": // Confirm & execute invoice/estimate: DRAFT_EXEC|{draftId}|{version}
                if (parts.length > 2) {
                    Long draftId = Long.parseLong(parts[1]);
                    int version = Integer.parseInt(parts[2]);
                    handleDraftExecution(chatId, draftId, version, installationId, botToken);
                }
                break;

            case "PO_DRAFT_EXEC": // Confirm & execute PO: PO_DRAFT_EXEC|{draftId}|{version}
                if (parts.length > 2) {
                    Long draftId = Long.parseLong(parts[1]);
                    int version = Integer.parseInt(parts[2]);
                    handlePoDraftExecution(chatId, draftId, version, installationId, botToken);
                }
                break;

            case "DRAFT_CANCEL":
                if (session.getActiveDraftId() != null) {
                    draftService.cancelDraft(session.getActiveDraftId());
                }
                sessionManager.clearSessionState(chatId);
                stagedItems.remove(session.getActiveDraftId());
                contexts.remove(chatId);
                apiClient.sendMessage(botToken, chatId, "Operation cancelled.", null, null);
                sendMainMenu(chatId, botToken, session);
                break;

            // =================================================================
            // 2. Download Invoice / Quotation
            // =================================================================
            case "M_DOWNLOAD":
                sendDownloadSubMenu(chatId, botToken);
                break;

            case "DL_TYPE": // DL_TYPE|INVOICE or DL_TYPE|ESTIMATE
                if (parts.length > 1) {
                    String docType = parts[1];
                    ConversationContext dlCtx = new ConversationContext();
                    dlCtx.flow = "DL_DOC";
                    dlCtx.docType = docType;
                    contexts.put(chatId, dlCtx);

                    sessionManager.setConversationState(chatId, "DL_SEARCH_CUST");
                    String label = "ESTIMATE".equalsIgnoreCase(docType) ? "Quotation" : "Invoice";
                    apiClient.sendMessage(botToken, chatId,
                            "📄 *Download " + label + "*\n\n👉 Enter the customer name (or mobile number) to search:",
                            "Markdown", null);
                }
                break;

            case "DL_RETRY_CUST":
                sessionManager.setConversationState(chatId, "DL_SEARCH_CUST");
                apiClient.sendMessage(botToken, chatId, "👉 Enter customer name (or mobile number) to search:", null,
                        null);
                break;

            case "DL_CUST_SEL": // DL_CUST_SEL|{customerId}
                if (parts.length > 1) {
                    Long custId = Long.parseLong(parts[1]);
                    Optional<Customer> cust = readService.getCustomerById(firmId, custId);
                    if (cust.isPresent()) {
                        ConversationContext dlCtx = contexts.computeIfAbsent(chatId, k -> new ConversationContext());
                        dlCtx.selectedEntityId = custId;
                        dlCtx.selectedEntityName = cust.get().getName();
                        dlCtx.selectedEntityPhone = cust.get().getPhone();
                        dlCtx.pageNumber = 0;
                        dlCtx.filterFrom = null;
                        dlCtx.filterTo = null;
                        renderCustomerDocumentList(chatId, firmId, dlCtx, botToken);
                    }
                }
                break;

            case "DL_PAGE": // DL_PAGE|{pageNumber}
                if (parts.length > 1) {
                    int targetPage = Integer.parseInt(parts[1]);
                    ConversationContext dlCtx = contexts.get(chatId);
                    if (dlCtx != null && dlCtx.selectedEntityId != null) {
                        dlCtx.pageNumber = Math.max(0, targetPage);
                        renderCustomerDocumentList(chatId, firmId, dlCtx, botToken);
                    }
                }
                break;

            case "DL_DATE_FILTER":
                sendDateFilterMenu(chatId, "DL_DATE_PICK", botToken);
                break;

            case "DL_DATE_PICK": // DL_DATE_PICK|{filterKey}
                if (parts.length > 1) {
                    String filterKey = parts[1];
                    ConversationContext dlCtx = contexts.get(chatId);
                    if (dlCtx != null) {
                        if ("CUSTOM".equalsIgnoreCase(filterKey)) {
                            sessionManager.setConversationState(chatId, "DL_CUSTOM_START");
                            apiClient.sendMessage(botToken, chatId,
                                    "📅 *Custom Date Filter*\n\n👉 Enter start date in format `YYYY-MM-DD` (e.g. `2026-04-01`):",
                                    "Markdown", null);
                        } else if ("CLEAR".equalsIgnoreCase(filterKey)) {
                            dlCtx.filterFrom = null;
                            dlCtx.filterTo = null;
                            dlCtx.pageNumber = 0;
                            renderCustomerDocumentList(chatId, firmId, dlCtx, botToken);
                        } else {
                            TelegramDateUtils.DateRange dr = TelegramDateUtils.resolveQuickFilter(filterKey);
                            dlCtx.filterFrom = dr.getFrom();
                            dlCtx.filterTo = dr.getTo();
                            dlCtx.pageNumber = 0;
                            renderCustomerDocumentList(chatId, firmId, dlCtx, botToken);
                        }
                    }
                }
                break;

            case "DL_DOC_SEL": // DL_DOC_SEL|{invoiceId}
                if (parts.length > 1) {
                    Long invId = Long.parseLong(parts[1]);
                    renderDocumentDetailsPreview(chatId, firmId, invId, botToken);
                }
                break;

            case "DL_DOC_BACK":
                ConversationContext dlCtxBack = contexts.get(chatId);
                if (dlCtxBack != null && dlCtxBack.selectedEntityId != null) {
                    renderCustomerDocumentList(chatId, firmId, dlCtxBack, botToken);
                } else {
                    sendDownloadSubMenu(chatId, botToken);
                }
                break;

            case "CONFIRM_DL_PDF": // CONFIRM_DL_PDF|{invoiceId}
                if (parts.length > 1) {
                    Long invId = Long.parseLong(parts[1]);
                    Optional<Invoice> invOpt = readService.getInvoiceById(firmId, invId);
                    if (invOpt.isPresent()) {
                        Invoice inv = invOpt.get();
                        String type = InvoiceStatus.ESTIMATE.equals(inv.getStatus()) ? "ESTIMATE_PDF" : "INVOICE_PDF";
                        String docNum = inv.getInvoiceNumber() != null ? inv.getInvoiceNumber()
                                : inv.getEstimateNumber();
                        Customer c = inv.getCustomer();
                        String rName = c != null ? c.getName() : null;
                        String rPhone = c != null ? c.getPhone() : null;
                        deliveryService.queueDelivery(installationId, userId, chatId, firmId, type, invId, docNum,
                                "DOWNLOAD", rName, rPhone);
                        apiClient.sendMessage(botToken, chatId,
                                "📄 Preparing PDF for *" + docNum + "*...\nUploading shortly.", "Markdown", null);
                        deliveryService.processPendingDeliveries(botToken);
                    }
                }
                break;

            // =================================================================
            // 3. Statements & Business Reports
            // =================================================================
            case "M_REPORTS":
                sendReportsSubMenu(chatId, botToken);
                break;

            case "RPT_START": // RPT_START|CUST, RPT_START|PARTY, RPT_START|FIRM, RPT_START|FORECAST,
                              // RPT_START|STOCK
                if (parts.length > 1) {
                    String rptType = parts[1];
                    ConversationContext rptCtx = new ConversationContext();
                    rptCtx.flow = "REPORT";
                    rptCtx.docType = rptType;
                    contexts.put(chatId, rptCtx);

                    if ("CUST".equalsIgnoreCase(rptType)) {
                        sessionManager.setConversationState(chatId, "RPT_SEARCH_CUST");
                        apiClient.sendMessage(botToken, chatId,
                                "👥 *Customer Statement*\n\n👉 Enter customer name (or mobile number) to search:",
                                "Markdown", null);
                    } else if ("PARTY".equalsIgnoreCase(rptType)) {
                        sessionManager.setConversationState(chatId, "RPT_SEARCH_PARTY");
                        apiClient.sendMessage(botToken, chatId,
                                "👤 *Vendor / Party Statement*\n\n👉 Enter party or vendor name to search:",
                                "Markdown", null);
                    } else if ("FIRM".equalsIgnoreCase(rptType)) {
                        sendDateFilterMenu(chatId, "RPT_PERIOD_PICK", botToken);
                    } else if ("STOCK".equalsIgnoreCase(rptType)) {
                        sendDateFilterMenu(chatId, "RPT_PERIOD_PICK", botToken);
                    } else if ("FORECAST".equalsIgnoreCase(rptType)) {
                        sendForecastHorizonMenu(chatId, botToken);
                    }
                }
                break;

            case "RPT_CUST_PICK": // RPT_CUST_PICK|{customerId}
                if (parts.length > 1) {
                    Long custId = Long.parseLong(parts[1]);
                    Optional<Customer> cust = readService.getCustomerById(firmId, custId);
                    if (cust.isPresent()) {
                        ConversationContext rptCtx = contexts.computeIfAbsent(chatId, k -> new ConversationContext());
                        rptCtx.selectedEntityId = custId;
                        rptCtx.selectedEntityName = cust.get().getName();
                        sendDateFilterMenu(chatId, "RPT_PERIOD_PICK", botToken);
                    }
                }
                break;

            case "RPT_PARTY_PICK": // RPT_PARTY_PICK|{partyId}
                if (parts.length > 1) {
                    Long partyId = Long.parseLong(parts[1]);
                    Optional<Party> party = readService.getPartyById(firmId, partyId);
                    if (party.isPresent()) {
                        ConversationContext rptCtx = contexts.computeIfAbsent(chatId, k -> new ConversationContext());
                        rptCtx.selectedEntityId = partyId;
                        rptCtx.selectedEntityName = party.get().getName();
                        sendDateFilterMenu(chatId, "RPT_PERIOD_PICK", botToken);
                    }
                }
                break;

            case "RPT_PERIOD_PICK": // RPT_PERIOD_PICK|{filterKey}
                if (parts.length > 1) {
                    String filterKey = parts[1];
                    ConversationContext rptCtx = contexts.get(chatId);
                    if (rptCtx != null) {
                        if ("CUSTOM".equalsIgnoreCase(filterKey)) {
                            sessionManager.setConversationState(chatId, "RPT_CUSTOM_START");
                            apiClient.sendMessage(botToken, chatId,
                                    "📅 *Custom Report Period*\n\n👉 Enter start date in format `YYYY-MM-DD` (e.g. `2026-04-01`):",
                                    "Markdown", null);
                        } else {
                            TelegramDateUtils.DateRange dr = TelegramDateUtils.resolveQuickFilter(filterKey);
                            rptCtx.filterFrom = dr.getFrom();
                            rptCtx.filterTo = dr.getTo();
                            renderReportConfirmationPreview(chatId, firmId, rptCtx, botToken);
                        }
                    }
                }
                break;

            case "RPT_FC_HORIZON": // RPT_FC_HORIZON|{days}
                if (parts.length > 1) {
                    int horizon = Integer.parseInt(parts[1]);
                    ConversationContext rptCtx = contexts.computeIfAbsent(chatId, k -> new ConversationContext());
                    rptCtx.docType = "FORECAST";
                    rptCtx.selectedDocId = (long) horizon;
                    renderForecastConfirmationPreview(chatId, firmId, horizon, botToken);
                }
                break;

            case "RPT_CONFIRM_GEN":
                ConversationContext genCtx = contexts.get(chatId);
                if (genCtx != null) {
                    handleExecuteReportGeneration(chatId, firmId, userId, installationId, genCtx, botToken);
                }
                break;

            // =================================================================
            // 4. Create / Download Purchase Order
            // =================================================================
            case "M_PO":
                sendPoSubMenu(chatId, botToken);
                break;

            case "PO_CREATE_START":
                sessionManager.setConversationState(chatId, "PO_CREATE_SEARCH_VENDOR");
                apiClient.sendMessage(botToken, chatId,
                        "📦 *Create Purchase Order*\n\n👉 Enter vendor name (or phone number) to search:",
                        "Markdown", null);
                break;

            case "PO_CREATE_PARTY_PICK": // PO_CREATE_PARTY_PICK|{partyId}
                if (parts.length > 1) {
                    Long partyId = Long.parseLong(parts[1]);
                    Optional<Party> party = readService.getPartyById(firmId, partyId);
                    if (party.isPresent()) {
                        TelegramDraft poDraft = draftService.startDraft(chatId, userId, firmId, "PURCHASE_ORDER");
                        sessionManager.setActiveDraftId(chatId, poDraft.getDraftId());
                        String phone = party.get().getPhone();
                        TelegramWhatsAppUtils.PhoneResult phoneRes = (phone != null && !phone.isBlank())
                                ? TelegramWhatsAppUtils.normalizePhone(phone)
                                : null;
                        if (phoneRes != null && phoneRes.isValid()) {
                            draftService.setCustomer(poDraft.getDraftId(), partyId, party.get().getName(),
                                    phoneRes.getNormalizedNumber());
                            sessionManager.setConversationState(chatId, "ADDING_ITEM_SEARCH");
                            apiClient.sendMessage(botToken, chatId,
                                    "Vendor: *" + party.get().getName() + "* (" + phoneRes.getHumanFormatted()
                                            + ")\n\n🔍 *Step 2:* Enter product name or barcode to search:",
                                    "Markdown", null);
                        } else {
                            draftService.setCustomer(poDraft.getDraftId(), partyId, party.get().getName(), null);
                            sessionManager.setConversationState(chatId, "PO_VENDOR_PHONE");
                            Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                                    List.of(Map.of("text", "⏭ Skip Mobile Number", "callback_data",
                                            "PO_VENDOR_PHONE_SKIP"))));
                            apiClient.sendMessage(botToken, chatId,
                                    "Vendor: *" + party.get().getName()
                                            + "*\n\n📱 Enter vendor mobile number (for WhatsApp sharing), or tap Skip:",
                                    "Markdown", keyboard);
                        }
                    }
                }
                break;

            case "PO_DL_START":
                sessionManager.setConversationState(chatId, "PO_DL_SEARCH_VENDOR");
                apiClient.sendMessage(botToken, chatId,
                        "📦 *Download Purchase Order*\n\n👉 Enter vendor name (or phone number) to search:",
                        "Markdown", null);
                break;

            case "PO_DL_VENDOR_PICK": // PO_DL_VENDOR_PICK|{partyId}
                if (parts.length > 1) {
                    Long partyId = Long.parseLong(parts[1]);
                    Optional<Party> party = readService.getPartyById(firmId, partyId);
                    if (party.isPresent()) {
                        ConversationContext poCtx = contexts.computeIfAbsent(chatId, k -> new ConversationContext());
                        poCtx.flow = "DL_PO";
                        poCtx.selectedEntityId = partyId;
                        poCtx.selectedEntityName = party.get().getName();
                        poCtx.selectedEntityPhone = party.get().getPhone();
                        poCtx.pageNumber = 0;
                        poCtx.filterFrom = null;
                        poCtx.filterTo = null;
                        renderPartyPoList(chatId, firmId, poCtx, botToken);
                    }
                }
                break;

            case "PO_DL_PAGE": // PO_DL_PAGE|{pageNumber}
                if (parts.length > 1) {
                    int targetPage = Integer.parseInt(parts[1]);
                    ConversationContext poCtx = contexts.get(chatId);
                    if (poCtx != null && poCtx.selectedEntityId != null) {
                        poCtx.pageNumber = Math.max(0, targetPage);
                        renderPartyPoList(chatId, firmId, poCtx, botToken);
                    }
                }
                break;

            case "PO_DL_DATE_FILTER":
                sendDateFilterMenu(chatId, "PO_DL_DATE_PICK", botToken);
                break;

            case "PO_DL_DATE_PICK": // PO_DL_DATE_PICK|{filterKey}
                if (parts.length > 1) {
                    String filterKey = parts[1];
                    ConversationContext poCtx = contexts.get(chatId);
                    if (poCtx != null) {
                        if ("CUSTOM".equalsIgnoreCase(filterKey)) {
                            sessionManager.setConversationState(chatId, "PO_DL_CUSTOM_START");
                            apiClient.sendMessage(botToken, chatId,
                                    "📅 *Custom PO Date Filter*\n\n👉 Enter start date in format `YYYY-MM-DD` (e.g. `2026-04-01`):",
                                    "Markdown", null);
                        } else if ("CLEAR".equalsIgnoreCase(filterKey)) {
                            poCtx.filterFrom = null;
                            poCtx.filterTo = null;
                            poCtx.pageNumber = 0;
                            renderPartyPoList(chatId, firmId, poCtx, botToken);
                        } else {
                            TelegramDateUtils.DateRange dr = TelegramDateUtils.resolveQuickFilter(filterKey);
                            poCtx.filterFrom = dr.getFrom();
                            poCtx.filterTo = dr.getTo();
                            poCtx.pageNumber = 0;
                            renderPartyPoList(chatId, firmId, poCtx, botToken);
                        }
                    }
                }
                break;

            case "PO_DL_SEL": // PO_DL_SEL|{poId}
                if (parts.length > 1) {
                    Long poId = Long.parseLong(parts[1]);
                    renderPoDetailsPreview(chatId, firmId, poId, botToken);
                }
                break;

            case "PO_DL_BACK":
                ConversationContext poCtxBack = contexts.get(chatId);
                if (poCtxBack != null && poCtxBack.selectedEntityId != null) {
                    renderPartyPoList(chatId, firmId, poCtxBack, botToken);
                } else {
                    sendPoSubMenu(chatId, botToken);
                }
                break;

            case "CONFIRM_PO_DL_PDF": // CONFIRM_PO_DL_PDF|{poId}
                if (parts.length > 1) {
                    Long poId = Long.parseLong(parts[1]);
                    Optional<PurchaseOrder> poOpt = readService.getPurchaseOrderById(firmId, poId);
                    if (poOpt.isPresent()) {
                        PurchaseOrder po = poOpt.get();
                        Party party = po.getParty();
                        String rName = party != null ? party.getName() : po.getPartyName();
                        String rPhone = party != null ? party.getPhone() : null;
                        deliveryService.queueDelivery(installationId, userId, chatId, firmId, "PO_PDF", poId,
                                po.getPoNumber(), "DOWNLOAD", rName, rPhone);
                        apiClient.sendMessage(botToken, chatId,
                                "📦 Preparing PO PDF for *" + po.getPoNumber() + "*...\nUploading shortly.", "Markdown",
                                null);
                        deliveryService.processPendingDeliveries(botToken);
                    }
                }
                break;

            // =================================================================
            // 5. Employee Leaves, Advances & Payroll
            // =================================================================
            case "M_HR":
                sendHrSubMenu(chatId, botToken);
                break;

            case "HR_LEAVE_START":
                startHrFlow(chatId, "LEAVE",
                        "🏖️ *Record Leave*\n\n👉 Enter employee name (or phone number) to search:", botToken);
                break;

            case "HR_VIEW_LEAVE_START":
                startHrFlow(chatId, "VIEW_LEAVE",
                        "📋 *View Leaves*\n\n👉 Enter employee name (or phone number) to search:", botToken);
                break;

            case "HR_ADV_START":
                startHrFlow(chatId, "ADVANCE",
                        "💵 *Record Salary Advance*\n\n👉 Enter employee name (or phone number) to search:", botToken);
                break;

            case "HR_VIEW_ADV_START":
                startHrFlow(chatId, "VIEW_ADV",
                        "📋 *View Salary Advances*\n\n👉 Enter employee name (or phone number) to search:", botToken);
                break;

            case "HR_PAYROLL_START":
                sendPayrollTargetMenu(chatId, botToken);
                break;

            case "HR_PAY_ALL": {
                ConversationContext hrCtx = new ConversationContext();
                hrCtx.flow = "HR";
                hrCtx.step = "PAYROLL";
                hrCtx.payrollTargetMode = "ALL";
                contexts.put(chatId, hrCtx);

                String curMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("MM-yyyy"));
                Map<String, Object> kb = Map.of("inline_keyboard", List.of(
                        List.of(Map.of("text", "📅 Current Month (" + curMonth + ")", "callback_data",
                                "HR_PAY_MONTH|" + curMonth)),
                        List.of(Map.of("text", "🔙 Back", "callback_data", "M_HR"))));
                sessionManager.setConversationState(chatId, "HR_PAYROLL_MONTH");
                apiClient.sendMessage(botToken, chatId,
                        "📑 *Process All Employees Payroll*\n\nTap button below or reply with payroll month (`MM-YYYY`, e.g. `"
                                + curMonth + "`):",
                        "Markdown", kb);
                break;
            }

            case "HR_PAY_INDIV_SEARCH":
                startHrFlow(chatId, "PAYROLL", "📑 *Process Individual Payroll*\n\n👉 Enter employee name to search:",
                        botToken);
                break;

            case "HR_EMP_PICK": // HR_EMP_PICK|{employeeId}
                if (parts.length > 1) {
                    Long empId = Long.parseLong(parts[1]);
                    Optional<Employee> empOpt = readService.getEmployeeById(firmId, empId);
                    if (empOpt.isPresent()) {
                        Employee emp = empOpt.get();
                        ConversationContext hrCtx = contexts.computeIfAbsent(chatId, k -> new ConversationContext());
                        hrCtx.selectedEntityId = empId;
                        hrCtx.selectedEntityName = emp.getName();
                        hrCtx.selectedEntityPhone = emp.getPhone();

                        String subFlow = hrCtx.step != null ? hrCtx.step : "LEAVE";
                        if ("LEAVE".equals(subFlow)) {
                            sessionManager.setConversationState(chatId, "HR_LEAVE_DATES");
                            apiClient.sendMessage(botToken, chatId,
                                    "Employee: *" + emp.getName() + "*\n\n" +
                                            "👉 Reply with leave dates (e.g. `2026-11-28 to 2026-11-29` or reply `today`):",
                                    "Markdown", null);
                        } else if ("VIEW_LEAVE".equals(subFlow)) {
                            renderEmployeeLeavesView(chatId, emp, botToken);
                        } else if ("ADVANCE".equals(subFlow)) {
                            sessionManager.setConversationState(chatId, "HR_ADVANCE_AMOUNT");
                            double currentAdv = emp.getCurrentAdvanceBalance() != null ? emp.getCurrentAdvanceBalance()
                                    : 0.0;
                            apiClient.sendMessage(botToken, chatId,
                                    "Employee: *" + emp.getName() + "*\n" +
                                            "Current Advance Balance: *₹" + currentAdv + "*\n\n" +
                                            "👉 Enter advance amount (e.g. `5000`):",
                                    "Markdown", null);
                        } else if ("VIEW_ADV".equals(subFlow)) {
                            renderEmployeeAdvancesView(chatId, emp, botToken);
                        } else if ("PAYROLL".equals(subFlow)) {
                            hrCtx.payrollTargetMode = "INDIVIDUAL";
                            String curMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("MM-yyyy"));
                            Map<String, Object> kb = Map.of("inline_keyboard", List.of(
                                    List.of(Map.of("text", "📅 Current Month (" + curMonth + ")", "callback_data",
                                            "HR_PAY_MONTH|" + curMonth)),
                                    List.of(Map.of("text", "🔙 Back", "callback_data", "M_HR"))));
                            sessionManager.setConversationState(chatId, "HR_PAYROLL_MONTH");
                            apiClient.sendMessage(botToken, chatId,
                                    "Employee: *" + emp.getName() + "*\nBase Salary: *₹"
                                            + (emp.getMonthlyBaseSalary() != null ? emp.getMonthlyBaseSalary() : 0.0)
                                            + "*\n\n" +
                                            "Tap button below or reply with payroll month (`MM-YYYY`):",
                                    "Markdown", kb);
                        }
                    }
                }
                break;

            case "HR_LEAVE_TYPE_PICK": // HR_LEAVE_TYPE_PICK|{CASUAL|SICK|EARNED|UNPAID}
                if (parts.length > 1) {
                    String leaveType = parts[1];
                    ConversationContext hrCtx = contexts.get(chatId);
                    if (hrCtx != null) {
                        hrCtx.leaveType = leaveType;
                        sessionManager.setConversationState(chatId, "HR_LEAVE_REASON");
                        apiClient.sendMessage(botToken, chatId,
                                "Leave Type: *" + leaveType
                                        + "*\n\n👉 Enter optional reason for leave (or reply `none`):",
                                "Markdown", null);
                    }
                }
                break;

            case "HR_CONFIRM_LEAVE":
                ConversationContext lCtx = contexts.get(chatId);
                if (lCtx != null && lCtx.selectedEntityId != null) {
                    LeaveRecord lr = draftService.recordLeave(firmId, lCtx.selectedEntityId, lCtx.leaveStart,
                            lCtx.leaveEnd, lCtx.leaveType, lCtx.leaveReason);
                    sessionManager.clearSessionState(chatId);
                    contexts.remove(chatId);
                    apiClient.sendMessage(botToken, chatId,
                            "✅ *Leave Recorded Successfully!*\n\n" +
                                    "• Employee: *" + lCtx.selectedEntityName + "*\n" +
                                    "• Type: *" + lr.getType() + "*\n" +
                                    "• Dates: *" + lr.getStartDate() + " to " + lr.getEndDate() + "*\n" +
                                    "• Reason: *" + (lr.getReason() != null ? lr.getReason() : "None") + "*",
                            "Markdown", null);
                }
                break;

            case "HR_CONFIRM_ADV":
                ConversationContext aCtx = contexts.get(chatId);
                if (aCtx != null && aCtx.selectedEntityId != null && aCtx.advanceAmount != null) {
                    EmployeeAdvance adv = draftService.recordAdvance(firmId, aCtx.selectedEntityId, aCtx.advanceAmount,
                            aCtx.advanceReason);
                    sessionManager.clearSessionState(chatId);
                    contexts.remove(chatId);
                    apiClient.sendMessage(botToken, chatId,
                            "✅ *Salary Advance Recorded Successfully!*\n\n" +
                                    "• Employee: *" + aCtx.selectedEntityName + "*\n" +
                                    "• Advance Amount: *₹" + adv.getAmount() + "*\n" +
                                    "• Reason / Notes: *" + adv.getDescription() + "*",
                            "Markdown", null);
                }
                break;

            case "HR_PAY_MONTH": // HR_PAY_MONTH|{MM-YYYY}
                if (parts.length > 1) {
                    String monthYear = parts[1];
                    handleHrPayrollMonthInput(chatId, firmId, monthYear, botToken);
                }
                break;

            case "HR_CONFIRM_PAYROLL":
                ConversationContext pCtx = contexts.get(chatId);
                if (pCtx != null) {
                    handleExecutePayroll(chatId, firmId, userId, installationId, pCtx, botToken);
                }
                break;

            // =================================================================
            // 6. Record Expenses / Savings
            // =================================================================
            case "M_PLANNER":
                sendPlannerSubMenu(chatId, botToken);
                break;

            case "PL_EXP":
                sessionManager.setConversationState(chatId, "PL_AWAITING_EXPENSE_INPUT");
                apiClient.sendMessage(botToken, chatId,
                        "💸 *Record Business Expense*\n\n" +
                                "👉 Reply with amount and description:\n" +
                                "Example: `450 Diesel fuel` or `1200 Office stationery`",
                        "Markdown", null);
                break;

            case "PL_SAV":
                sessionManager.setConversationState(chatId, "PL_AWAITING_SAVING_INPUT");
                apiClient.sendMessage(botToken, chatId,
                        "🐖 *Record Savings Deposit*\n\n" +
                                "👉 Reply with amount and purpose:\n" +
                                "Example: `5000 Emergency Fund` or `10000 SIP Reserve`",
                        "Markdown", null);
                break;

            case "DRAFT_CUST_PHONE_SKIP":
                sessionManager.setConversationState(chatId, "ADDING_ITEM_SEARCH");
                apiClient.sendMessage(botToken, chatId,
                        "🔍 *Step 2:* Enter product name or barcode to search:",
                        "Markdown", null);
                break;

            case "PO_VENDOR_PHONE_SKIP":
                sessionManager.setConversationState(chatId, "ADDING_ITEM_SEARCH");
                apiClient.sendMessage(botToken, chatId,
                        "🔍 *Step 2:* Enter product name or barcode to search:",
                        "Markdown", null);
                break;

            case "WA_ENTER": // WA_ENTER|{deliveryId}
                if (parts.length > 1) {
                    String delId = parts[1];
                    sessionManager.setConversationState(chatId, "WA_INPUT|" + delId);
                    Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                            List.of(Map.of("text", "❌ Cancel", "callback_data", "WA_SKIP|" + delId))));
                    apiClient.sendMessage(botToken, chatId,
                            "📱 Enter the 10-digit mobile number for WhatsApp sharing:", null, keyboard);
                }
                break;

            case "WA_ALT": // WA_ALT|{deliveryId}
                if (parts.length > 1) {
                    String delId = parts[1];
                    sessionManager.setConversationState(chatId, "WA_INPUT|" + delId);
                    Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                            List.of(Map.of("text", "❌ Cancel", "callback_data", "WA_SKIP|" + delId))));
                    apiClient.sendMessage(botToken, chatId,
                            "📱 Enter the alternate mobile number for WhatsApp sharing (this will not modify saved contact details):",
                            null, keyboard);
                }
                break;

            case "WA_SKIP": // WA_SKIP|{deliveryId}
                sessionManager.clearSessionState(chatId);
                apiClient.sendMessage(botToken, chatId, "WhatsApp handoff skipped.", null, null);
                break;
        }
    }

    // =========================================================================
    // Core Sub-menu Renderers
    // =========================================================================

    private void sendMainMenu(Long chatId, String botToken, TelegramUserSession session) throws Exception {
        String firmLabel = "None";
        if (session.getActiveFirmId() != null) {
            firmLabel = readService.getFirm(session.getActiveFirmId()).map(FirmDetails::getFirmName)
                    .orElse("Firm #" + session.getActiveFirmId());
        }

        String menu = "🤖 *RupeeCRM Business Assistant*\n" +
                "Active Firm: *" + firmLabel + "*\n\n" +
                "Select one of the 6 authorized business operations:";

        Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                List.of(Map.of("text", "1️⃣ 📄 Create Invoice / Quotation", "callback_data", "M_CREATE")),
                List.of(Map.of("text", "2️⃣ 📥 Download Invoice / Quotation", "callback_data", "M_DOWNLOAD")),
                List.of(Map.of("text", "3️⃣ 📊 Statements & Business Reports", "callback_data", "M_REPORTS")),
                List.of(Map.of("text", "4️⃣ 📦 Create / Download Purchase Order", "callback_data", "M_PO")),
                List.of(Map.of("text", "5️⃣ 👥 Employee Leaves, Advances & Payroll", "callback_data", "M_HR")),
                List.of(Map.of("text", "6️⃣ 💰 Record Expenses / Savings", "callback_data", "M_PLANNER"))));

        apiClient.sendMessage(botToken, chatId, menu, "Markdown", keyboard);
    }

    private void sendCreateChoiceMenu(Long chatId, String botToken) throws Exception {
        Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                List.of(Map.of("text", "🧾 Create New Invoice", "callback_data", "M_CREATE_INV")),
                List.of(Map.of("text", "📝 Create New Quotation", "callback_data", "M_CREATE_EST")),
                List.of(Map.of("text", "🔄 Convert Estimate to Invoice", "callback_data", "M_CONVERT_EST")),
                List.of(Map.of("text", "🔙 Back to Menu", "callback_data", "NAV_MENU"))
        ));
        apiClient.sendMessage(botToken, chatId, "📄 *Create Invoice / Quotation*\n\nSelect operation:", "Markdown", keyboard);
    }

    private void sendDownloadSubMenu(Long chatId, String botToken) throws Exception {
        Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                List.of(Map.of("text", "📄 Download Invoice", "callback_data", "DL_TYPE|INVOICE")),
                List.of(Map.of("text", "📝 Download Quotation", "callback_data", "DL_TYPE|ESTIMATE")),
                List.of(Map.of("text", "🔙 Back to Menu", "callback_data", "NAV_MENU"))));
        apiClient.sendMessage(botToken, chatId, "📥 *Download Documents*\n\nWhat would you like to download?",
                "Markdown", keyboard);
    }

    private void sendReportsSubMenu(Long chatId, String botToken) throws Exception {
        Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                List.of(Map.of("text", "👥 Customer Statement", "callback_data", "RPT_START|CUST")),
                List.of(Map.of("text", "👤 Party / Vendor Statement", "callback_data", "RPT_START|PARTY")),
                List.of(Map.of("text", "🏢 Firm Financial Summary", "callback_data", "RPT_START|FIRM")),
                List.of(Map.of("text", "📈 Forecast & Replenishment", "callback_data", "RPT_START|FORECAST")),
                List.of(Map.of("text", "📦 Stock Movement Statement", "callback_data", "RPT_START|STOCK")),
                List.of(Map.of("text", "🔙 Back to Menu", "callback_data", "NAV_MENU"))));
        apiClient.sendMessage(botToken, chatId,
                "📊 *Statements & Business Reports*\n\nSelect statement or report to generate:", "Markdown", keyboard);
    }

    private void sendPoSubMenu(Long chatId, String botToken) throws Exception {
        Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                List.of(Map.of("text", "➕ Create Purchase Order", "callback_data", "PO_CREATE_START")),
                List.of(Map.of("text", "📥 Download Purchase Order", "callback_data", "PO_DL_START")),
                List.of(Map.of("text", "🔙 Back to Menu", "callback_data", "NAV_MENU"))));
        apiClient.sendMessage(botToken, chatId, "📦 *Purchase Orders*\n\nSelect operation:", "Markdown", keyboard);
    }

    private void sendHrSubMenu(Long chatId, String botToken) throws Exception {
        Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                List.of(Map.of("text", "🏖️ Record Employee Leave", "callback_data", "HR_LEAVE_START")),
                List.of(Map.of("text", "📋 View Leave Records", "callback_data", "HR_VIEW_LEAVE_START")),
                List.of(Map.of("text", "💵 Record Salary Advance", "callback_data", "HR_ADV_START")),
                List.of(Map.of("text", "📋 View Salary Advances", "callback_data", "HR_VIEW_ADV_START")),
                List.of(Map.of("text", "📑 Monthly Payroll & Payslip", "callback_data", "HR_PAYROLL_START")),
                List.of(Map.of("text", "🔙 Back to Menu", "callback_data", "NAV_MENU"))));
        apiClient.sendMessage(botToken, chatId, "👥 *Employee Leaves, Advances & Payroll*\n\nSelect HR operation:",
                "Markdown", keyboard);
    }

    private void sendPlannerSubMenu(Long chatId, String botToken) throws Exception {
        Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                List.of(Map.of("text", "💸 Record Business Expense", "callback_data", "PL_EXP")),
                List.of(Map.of("text", "🐖 Record Savings Deposit", "callback_data", "PL_SAV")),
                List.of(Map.of("text", "🔙 Back to Menu", "callback_data", "NAV_MENU"))));
        apiClient.sendMessage(botToken, chatId,
                "💰 *Record Expenses / Savings*\n\n" +
                        "Tip: You can also record directly anytime without menus, e.g.:\n" +
                        "• `Expense 450 Fuel`\n" +
                        "• `Saved 2000 Emergency Reserve`",
                "Markdown", keyboard);
    }

    private void sendDateFilterMenu(Long chatId, String callbackPrefix, String botToken) throws Exception {
        Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                List.of(Map.of("text", "📅 This Month", "callback_data", callbackPrefix + "|THIS_MONTH"),
                        Map.of("text", "📅 Last Month", "callback_data", callbackPrefix + "|LAST_MONTH")),
                List.of(Map.of("text", "📅 This Quarter", "callback_data", callbackPrefix + "|THIS_QUARTER"),
                        Map.of("text", "📅 Last Quarter", "callback_data", callbackPrefix + "|LAST_QUARTER")),
                List.of(Map.of("text", "📅 This Year", "callback_data", callbackPrefix + "|THIS_YEAR"),
                        Map.of("text", "📅 Last Year", "callback_data", callbackPrefix + "|LAST_YEAR")),
                List.of(Map.of("text", "🏛️ This Financial Year", "callback_data", callbackPrefix + "|THIS_FY")),
                List.of(Map.of("text", "🏛️ Last Financial Year", "callback_data", callbackPrefix + "|LAST_FY")),
                List.of(Map.of("text", "✏️ Custom Date Range", "callback_data", callbackPrefix + "|CUSTOM")),
                List.of(Map.of("text", "🔙 Cancel", "callback_data", "NAV_MENU"))));
        apiClient.sendMessage(botToken, chatId, "📅 *Select Period / Date Range:*", "Markdown", keyboard);
    }

    private void sendForecastHorizonMenu(Long chatId, String botToken) throws Exception {
        Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                List.of(Map.of("text", "⚡ 7 Days Horizon", "callback_data", "RPT_FC_HORIZON|7"),
                        Map.of("text", "📅 14 Days Horizon", "callback_data", "RPT_FC_HORIZON|14")),
                List.of(Map.of("text", "📈 30 Days (Standard)", "callback_data", "RPT_FC_HORIZON|30"),
                        Map.of("text", "📊 60 Days Horizon", "callback_data", "RPT_FC_HORIZON|60")),
                List.of(Map.of("text", "🏛️ 90 Days (Quarterly)", "callback_data", "RPT_FC_HORIZON|90")),
                List.of(Map.of("text", "🔙 Back to Reports", "callback_data", "M_REPORTS"))));
        apiClient.sendMessage(botToken, chatId, "📈 *Forecast & Replenishment Analytics*\n\nSelect forecast horizon:",
                "Markdown", keyboard);
    }

    private void sendPayrollTargetMenu(Long chatId, String botToken) throws Exception {
        Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                List.of(Map.of("text", "🏢 Process All Employees", "callback_data", "HR_PAY_ALL")),
                List.of(Map.of("text", "👤 Process Individual Employee", "callback_data", "HR_PAY_INDIV_SEARCH")),
                List.of(Map.of("text", "🔙 Back to HR", "callback_data", "M_HR"))));
        apiClient.sendMessage(botToken, chatId, "📑 *Monthly Payroll*\n\nSelect payroll target:", "Markdown", keyboard);
    }

    private void sendFirmSelectionMenu(Long chatId, String botToken, String title) throws Exception {
        List<FirmDetails> firms = readService.getActiveFirms();
        if (firms.isEmpty()) {
            apiClient.sendMessage(botToken, chatId,
                    "🏢 *No Active Firms Configured*\n\nPlease configure at least one active firm in RupeeCRM Desktop under Company Settings.",
                    "Markdown", null);
            return;
        }
        List<List<Map<String, String>>> rows = new ArrayList<>();
        for (FirmDetails f : firms) {
            rows.add(List.of(Map.of("text", "🏢 " + f.getFirmName(), "callback_data", "F|" + f.getId())));
        }
        apiClient.sendMessage(botToken, chatId, title, "Markdown", Map.of("inline_keyboard", rows));
    }

    // =========================================================================
    // 1. Create Invoice / Quotation / PO Implementation
    // =========================================================================

    private void handleStartNewDocument(Long chatId, Long userId, Long firmId, String docType, String botToken)
            throws Exception {
        TelegramDraft draft = draftService.startDraft(chatId, userId, firmId, docType);
        sessionManager.setActiveDraftId(chatId, draft.getDraftId());
        sessionManager.setConversationState(chatId, "AWAITING_CUSTOMER");

        String docLabel = "ESTIMATE".equalsIgnoreCase(docType) ? "Quotation" : "Tax Invoice";
        apiClient.sendMessage(botToken, chatId,
                "📄 *New " + docLabel + "*\n\n*Step 1:* Enter customer name (or phone number) to search:",
                "Markdown", null);
    }

    private void handleCustomerSearchInput(Long chatId, TelegramUserSession session, String text, String botToken)
            throws Exception {
        List<Customer> matches = readService.searchCustomers(session.getActiveFirmId(), text);
        String safeName = text.trim();
        if (safeName.length() > 30)
            safeName = safeName.substring(0, 30);

        if (matches.isEmpty()) {
            Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                    List.of(Map.of("text", "➕ Create '" + safeName + "' as New Customer", "callback_data",
                            "CUST_NEW|" + safeName)),
                    List.of(Map.of("text", "🔍 Search Again", "callback_data", "DRAFT_CANCEL")),
                    List.of(Map.of("text", "❌ Cancel", "callback_data", "DRAFT_CANCEL"))));
            apiClient.sendMessage(botToken, chatId, "❌ No customer found matching '" + text + "'.", null, keyboard);
        } else {
            List<List<Map<String, String>>> rows = new ArrayList<>();
            for (Customer c : matches.subList(0, Math.min(6, matches.size()))) {
                String label = c.getName()
                        + (c.getPhone() != null && !c.getPhone().isBlank() ? " (" + c.getPhone() + ")" : "");
                rows.add(List.of(Map.of("text", label, "callback_data", "CUST|" + c.getId())));
            }
            rows.add(List.of(Map.of("text", "➕ Create '" + safeName + "' as New Customer", "callback_data",
                    "CUST_NEW|" + safeName)));
            rows.add(List.of(Map.of("text", "❌ Cancel", "callback_data", "DRAFT_CANCEL")));

            apiClient.sendMessage(botToken, chatId, "Select matching customer:", null, Map.of("inline_keyboard", rows));
        }
    }

    private void handleItemSearchInput(Long chatId, TelegramUserSession session, String text, String botToken)
            throws Exception {
        List<Product> matches = readService.searchProducts(session.getActiveFirmId(), text);

        if (matches.isEmpty()) {
            apiClient.sendMessage(botToken, chatId,
                    "❌ No products matched '" + text + "'. Please enter another search term:", null, null);
            return;
        }

        List<List<Map<String, String>>> rows = new ArrayList<>();
        for (Product p : matches.subList(0, Math.min(6, matches.size()))) {
            String skuInfo = (p.getSku() != null && !p.getSku().isBlank()) ? (" [" + p.getSku() + "]") : "";
            String label = p.getName() + skuInfo + " — ₹" + (p.getPrice() != null ? p.getPrice() : 0) + "/"
                    + (p.getUnit() != null ? p.getUnit() : "Pc");
            rows.add(List.of(Map.of("text", label, "callback_data", "PROD|" + p.getId())));
        }
        rows.add(List.of(Map.of("text", "❌ Cancel", "callback_data", "DRAFT_CANCEL")));

        apiClient.sendMessage(botToken, chatId, "Select product:", null, Map.of("inline_keyboard", rows));
    }

    private void handleItemQtyInput(Long chatId, TelegramUserSession session, String text, String botToken)
            throws Exception {
        try {
            BigDecimal qty = new BigDecimal(text.trim());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                apiClient.sendMessage(botToken, chatId, "Quantity must be greater than zero. Enter quantity:", null,
                        null);
                return;
            }
            StagedItemState staged = stagedItems.get(session.getActiveDraftId());
            if (staged != null) {
                staged.qty = qty;
                sessionManager.setConversationState(chatId, "ENTERING_RATE");

                Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                        List.of(Map.of("text", "✅ Use Catalogue Rate (₹" + staged.rate + ")", "callback_data",
                                "RATE_DEF"))));

                apiClient.sendMessage(botToken, chatId,
                        "Quantity: *" + qty + " " + staged.unit + "*\nCatalogue Rate: *₹" + staged.rate
                                + "*\n\nTap button below or reply with override rate:",
                        "Markdown", keyboard);
            }
        } catch (NumberFormatException e) {
            apiClient.sendMessage(botToken, chatId, "Invalid number. Please enter a valid quantity (e.g. 5 or 2.5):",
                    null, null);
        }
    }

    private void handleItemRateInput(Long chatId, TelegramUserSession session, String text, String botToken)
            throws Exception {
        try {
            BigDecimal rate = new BigDecimal(text.trim());
            if (rate.compareTo(BigDecimal.ZERO) < 0) {
                apiClient.sendMessage(botToken, chatId, "Rate cannot be negative. Enter rate:", null, null);
                return;
            }
            StagedItemState staged = stagedItems.get(session.getActiveDraftId());
            if (staged != null) {
                staged.rate = rate;
                finalizeStagedItem(chatId, session, staged, botToken);
            }
        } catch (NumberFormatException e) {
            apiClient.sendMessage(botToken, chatId, "Invalid number. Please enter a valid rate (e.g. 275.00):", null,
                    null);
        }
    }

    private void finalizeStagedItem(Long chatId, TelegramUserSession session, StagedItemState staged, String botToken)
            throws Exception {
        BigDecimal lineAmount = staged.qty.multiply(staged.rate);
        sessionManager.setConversationState(chatId, "CONFIRMING_ITEM");

        String msg = "📋 *Confirm Line Item*\n" +
                "Item: *" + staged.itemName + "*\n" +
                "Quantity: *" + staged.qty + " " + staged.unit + "*\n" +
                "Rate: *₹" + staged.rate + " per " + staged.unit + "*\n" +
                "Line Total: *₹" + lineAmount + "*";

        Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                List.of(Map.of("text", "✅ Confirm Item", "callback_data", "ITEM_CONFIRM")),
                List.of(Map.of("text", "❌ Cancel Item", "callback_data", "DRAFT_CANCEL"))));

        apiClient.sendMessage(botToken, chatId, msg, "Markdown", keyboard);
    }

    private void promptNextItemAction(Long chatId, Long draftId, String botToken) throws Exception {
        List<TelegramDraftItem> items = draftService.getDraftItems(draftId);
        TelegramDraft draft = draftService.getDraft(draftId).orElse(null);
        boolean isPo = draft != null && "PURCHASE_ORDER".equalsIgnoreCase(draft.getDocumentType());

        StringBuilder sb = new StringBuilder("✅ *Item Added Successfully!*\nTotal items: *" + items.size() + "*\n\n");
        for (int i = 0; i < items.size(); i++) {
            TelegramDraftItem item = items.get(i);
            sb.append(i + 1).append(". ").append(item.getItemName()).append(" — ")
                    .append(item.getQuantity()).append(" ").append(item.getUnit())
                    .append(" @ ₹").append(item.getUnitRate()).append(" = ₹").append(item.getLineTotal()).append("\n");
        }
        sb.append("\nWhat would you like to do next?");

        Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                List.of(Map.of("text", "➕ Add Another Item", "callback_data", "ITEM_NEXT")),
                List.of(Map.of("text", isPo ? "📦 Finish & Preview PO" : "📄 Finish & Preview", "callback_data",
                        "ITEM_PREVIEW")),
                List.of(Map.of("text", "❌ Cancel", "callback_data", "DRAFT_CANCEL"))));

        apiClient.sendMessage(botToken, chatId, sb.toString(), "Markdown", keyboard);
    }

    private void handleDraftPreview(Long chatId, Long draftId, String botToken) throws Exception {
        Invoice preview = draftService.previewDraft(draftId);
        TelegramDraft draft = draftService.getDraft(draftId).orElseThrow();

        String docLabel = "ESTIMATE".equalsIgnoreCase(draft.getDocumentType()) ? "QUOTATION" : "INVOICE";
        StringBuilder sb = new StringBuilder("📄 *" + docLabel + " PREVIEW*\n");
        sb.append("Customer: *").append(draft.getCustomerName() != null ? draft.getCustomerName() : "Cash Customer")
                .append("*\n");
        sb.append("────────────────────────\n");
        List<TelegramDraftItem> items = draftService.getDraftItems(draftId);
        for (int i = 0; i < items.size(); i++) {
            TelegramDraftItem item = items.get(i);
            sb.append(i + 1).append(". ").append(item.getItemName()).append("\n   ")
                    .append(item.getQuantity()).append(" ").append(item.getUnit())
                    .append(" × ₹").append(item.getUnitRate())
                    .append(" = ₹").append(item.getLineTotal()).append("\n");
        }
        sb.append("────────────────────────\n");
        sb.append("Subtotal: ₹").append(preview.getSubtotalWithoutTax()).append("\n");
        sb.append("Tax (GST): ₹").append(preview.getTotalTax()).append("\n");
        sb.append("Grand Total: *₹").append(preview.getTotalAmount()).append("*\n");
        sb.append("\n⚠️ Confirmation expires in 15 minutes.");

        Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                List.of(Map.of("text", "✅ Confirm & Save " + docLabel, "callback_data",
                        "DRAFT_EXEC|" + draftId + "|" + draft.getVersion())),
                List.of(Map.of("text", "➕ Add More Items", "callback_data", "ITEM_NEXT")),
                List.of(Map.of("text", "❌ Cancel", "callback_data", "DRAFT_CANCEL"))));

        apiClient.sendMessage(botToken, chatId, sb.toString(), "Markdown", keyboard);
    }

    private void handlePoDraftPreview(Long chatId, Long draftId, String botToken) throws Exception {
        PurchaseOrder preview = draftService.previewPoDraft(draftId);
        TelegramDraft draft = draftService.getDraft(draftId).orElseThrow();

        StringBuilder sb = new StringBuilder("📦 *PURCHASE ORDER PREVIEW*\n");
        sb.append("Vendor / Party: *").append(draft.getCustomerName()).append("*\n");
        sb.append("────────────────────────\n");
        List<TelegramDraftItem> items = draftService.getDraftItems(draftId);
        for (int i = 0; i < items.size(); i++) {
            TelegramDraftItem item = items.get(i);
            sb.append(i + 1).append(". ").append(item.getItemName()).append("\n   ")
                    .append(item.getQuantity()).append(" ").append(item.getUnit())
                    .append(" × ₹").append(item.getUnitRate())
                    .append(" = ₹").append(item.getLineTotal()).append("\n");
        }
        sb.append("────────────────────────\n");
        sb.append("Order Total: *₹").append(preview.getTotalAmount()).append("*\n");
        sb.append("\n⚠️ Confirmation expires in 15 minutes.");

        Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                List.of(Map.of("text", "✅ Confirm & Issue PO", "callback_data",
                        "PO_DRAFT_EXEC|" + draftId + "|" + draft.getVersion())),
                List.of(Map.of("text", "➕ Add More Items", "callback_data", "ITEM_NEXT")),
                List.of(Map.of("text", "❌ Cancel PO", "callback_data", "DRAFT_CANCEL"))));

        apiClient.sendMessage(botToken, chatId, sb.toString(), "Markdown", keyboard);
    }

    private void handleDraftExecution(Long chatId, Long draftId, int version, String installationId, String botToken)
            throws Exception {
        try {
            Invoice saved = draftService.executeDraft(draftId, version, installationId);
            sessionManager.clearSessionState(chatId);

            String docNum = saved.getInvoiceNumber() != null ? saved.getInvoiceNumber() : saved.getEstimateNumber();
            apiClient.sendMessage(botToken, chatId,
                    "🎉 *Document Created Successfully!*\n" +
                            "Document #: *" + docNum + "*\n" +
                            "Grand Total: *₹" + saved.getTotalAmount() + "*\n\n" +
                            "Generating and uploading official PDF...",
                    "Markdown", null);

            deliveryService.processPendingDeliveries(botToken);
        } catch (Exception e) {
            apiClient.sendMessage(botToken, chatId, "❌ Execution failed: " + e.getMessage(), null, null);
        }
    }

    private void handlePoDraftExecution(Long chatId, Long draftId, int version, String installationId, String botToken)
            throws Exception {
        try {
            PurchaseOrder saved = draftService.executePoDraft(draftId, version, installationId);
            sessionManager.clearSessionState(chatId);

            apiClient.sendMessage(botToken, chatId,
                    "🎉 *Purchase Order Issued Successfully!*\n" +
                            "PO #: *" + saved.getPoNumber() + "*\n" +
                            "Vendor: *" + saved.getPartyName() + "*\n" +
                            "Total Amount: *₹" + saved.getTotalAmount() + "*\n\n" +
                            "Generating and uploading official PO PDF...",
                    "Markdown", null);

            deliveryService.processPendingDeliveries(botToken);
        } catch (Exception e) {
            apiClient.sendMessage(botToken, chatId, "❌ PO Issue failed: " + e.getMessage(), null, null);
        }
    }

    // =========================================================================
    // 1b. Convert Estimate to Invoice Implementation
    // =========================================================================

    private void handleStartEstimateConversion(Long chatId, String botToken) throws Exception {
        ConversationContext ctx = new ConversationContext();
        ctx.flow = "CONVERT_EST";
        ctx.pageNumber = 0;
        ctx.filterFrom = null;
        ctx.filterTo = null;
        contexts.put(chatId, ctx);
        sessionManager.setConversationState(chatId, "CONV_SEARCH_CUST");

        apiClient.sendMessage(botToken, chatId,
                "🔄 *Convert Estimate to Invoice*\n\n*Step 1:* Enter the customer name:",
                "Markdown",
                Map.of("inline_keyboard", List.of(
                        List.of(Map.of("text", "🔙 Back to Menu", "callback_data", "NAV_MENU"))
                )));
    }

    private void handleConvertCustomerSearch(Long chatId, Long firmId, String text, String botToken) throws Exception {
        List<Customer> matches = readService.searchCustomers(firmId, text);
        if (matches.isEmpty()) {
            Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                    List.of(Map.of("text", "🔍 Search Another Name", "callback_data", "CONV_RETRY_CUST")),
                    List.of(Map.of("text", "🔙 Back to Menu", "callback_data", "NAV_MENU"))
            ));
            apiClient.sendMessage(botToken, chatId, "❌ No customer found matching '" + text + "'.\n\n(Note: Only existing customers with saved estimates can be converted).", null, keyboard);
            return;
        }

        List<List<Map<String, String>>> rows = new ArrayList<>();
        for (Customer c : matches.subList(0, Math.min(6, matches.size()))) {
            String label = c.getName() + (c.getPhone() != null && !c.getPhone().isBlank() ? " (" + c.getPhone() + ")" : "");
            rows.add(List.of(Map.of("text", label, "callback_data", "CONV_CUST_SEL|" + c.getId())));
        }
        rows.add(List.of(
                Map.of("text", "🔍 Search Again", "callback_data", "CONV_RETRY_CUST"),
                Map.of("text", "🔙 Back to Menu", "callback_data", "NAV_MENU")
        ));

        apiClient.sendMessage(botToken, chatId, "Select customer to view estimates:", null, Map.of("inline_keyboard", rows));
    }

    private void handleConvertCustomerSelected(Long chatId, Long firmId, Long custId, String botToken) throws Exception {
        Optional<Customer> cust = readService.getCustomerById(firmId, custId);
        if (cust.isEmpty()) {
            apiClient.sendMessage(botToken, chatId, "❌ Customer record not found.", null, null);
            return;
        }

        ConversationContext ctx = contexts.computeIfAbsent(chatId, k -> new ConversationContext());
        ctx.flow = "CONVERT_EST";
        ctx.selectedEntityId = custId;
        ctx.selectedEntityName = cust.get().getName();
        ctx.pageNumber = 0;
        ctx.filterFrom = null;
        ctx.filterTo = null;

        String phone = cust.get().getPhone();
        TelegramWhatsAppUtils.PhoneResult phoneRes = (phone != null && !phone.isBlank())
                ? TelegramWhatsAppUtils.normalizePhone(phone)
                : null;

        if (phoneRes != null && phoneRes.isValid()) {
            ctx.selectedEntityPhone = phoneRes.getNormalizedNumber();
            sessionManager.setConversationState(chatId, "IDLE");
            renderConvertEstimateList(chatId, firmId, ctx, botToken);
        } else {
            ctx.selectedEntityPhone = null;
            sessionManager.setConversationState(chatId, "CONV_CUST_PHONE");
            Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                    List.of(Map.of("text", "⏭ Skip Mobile Number", "callback_data", "CONV_CUST_PHONE_SKIP"))
            ));
            apiClient.sendMessage(botToken, chatId,
                    "Customer: *" + cust.get().getName() + "*\n\n📱 *Customer mobile number (optional)*. You can skip this:\n_Used to generate a WhatsApp handoff link for the converted invoice._",
                    "Markdown", keyboard);
        }
    }

    private void handleConvertCustomerPhoneInput(Long chatId, TelegramUserSession session, String text, String botToken) throws Exception {
        TelegramWhatsAppUtils.PhoneResult res = TelegramWhatsAppUtils.normalizePhone(text);
        if (!res.isValid()) {
            Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                    List.of(Map.of("text", "⏭ Skip Mobile Number", "callback_data", "CONV_CUST_PHONE_SKIP"))
            ));
            apiClient.sendMessage(botToken, chatId,
                    "⚠️ " + res.getErrorMessage() + "\n\nPlease enter a valid mobile number or tap Skip:", null, keyboard);
            return;
        }

        ConversationContext ctx = contexts.get(chatId);
        if (ctx != null && ctx.selectedEntityId != null) {
            customerRepository.findById(ctx.selectedEntityId).ifPresent(c -> {
                c.setPhone(res.getNormalizedNumber());
                customerRepository.save(c);
            });
            ctx.selectedEntityPhone = res.getNormalizedNumber();
        }

        sessionManager.setConversationState(chatId, "IDLE");
        apiClient.sendMessage(botToken, chatId,
                "✅ Mobile number saved: *" + res.getHumanFormatted() + "*",
                "Markdown", null);

        if (ctx != null) {
            renderConvertEstimateList(chatId, session.getActiveFirmId(), ctx, botToken);
        }
    }

    private void handleConvertCustomerPhoneSkip(Long chatId, Long firmId, String botToken) throws Exception {
        ConversationContext ctx = contexts.get(chatId);
        if (ctx != null) {
            ctx.selectedEntityPhone = null;
        }
        sessionManager.setConversationState(chatId, "IDLE");
        if (ctx != null) {
            renderConvertEstimateList(chatId, firmId, ctx, botToken);
        }
    }

    private void renderConvertEstimateList(Long chatId, Long firmId, ConversationContext ctx, String botToken) throws Exception {
        int page = ctx.pageNumber;
        int size = 5;

        Page<Invoice> docsPage = readService.getCustomerEstimates(firmId, ctx.selectedEntityId, ctx.filterFrom, ctx.filterTo, page, size);

        StringBuilder sb = new StringBuilder();
        sb.append("📝 *Select Estimate to Convert*\n");
        sb.append("Customer: *").append(ctx.selectedEntityName).append("*\n");
        if (ctx.filterFrom != null && ctx.filterTo != null) {
            sb.append("Period: _").append(TelegramDateUtils.formatHuman(ctx.filterFrom)).append(" to ")
                    .append(TelegramDateUtils.formatHuman(ctx.filterTo)).append("_\n");
        }
        sb.append("────────────────────────\n\n");

        List<List<Map<String, String>>> rows = new ArrayList<>();

        if (docsPage.isEmpty()) {
            sb.append("No estimates/quotations found for this customer");
            if (ctx.filterFrom != null) sb.append(" in the selected period.");
            else sb.append(".");
        } else {
            for (Invoice inv : docsPage.getContent()) {
                String docNum = inv.getEstimateNumber() != null ? inv.getEstimateNumber() : "EST-" + inv.getId();
                String dateStr = inv.getInvoiceDate() != null ? inv.getInvoiceDate().format(DISPLAY_DATE) : "—";
                String statusStr = inv.getConversionStatus() != null ? inv.getConversionStatus() : (inv.getStatus() != null ? inv.getStatus().name() : "ESTIMATE");

                sb.append("• *").append(docNum).append("*\n");
                sb.append("  Date: ").append(dateStr).append("\n");
                sb.append("  Amount: ₹").append(inv.getTotalAmount()).append("\n");
                sb.append("  Status: ").append(statusStr).append("\n\n");

                rows.add(List.of(Map.of("text", "📝 " + docNum + " (₹" + inv.getTotalAmount() + ")", "callback_data", "CONV_EST_PICK|" + inv.getId())));
            }
        }

        List<Map<String, String>> navRow = new ArrayList<>();
        if (page > 0) {
            navRow.add(Map.of("text", "⏪ Newer", "callback_data", "CONV_PAGE|" + (page - 1)));
        }
        if (docsPage.hasNext()) {
            navRow.add(Map.of("text", "⏩ Older", "callback_data", "CONV_PAGE|" + (page + 1)));
        }
        if (!navRow.isEmpty()) {
            rows.add(navRow);
        }

        rows.add(List.of(
                Map.of("text", "📅 Filter by Date", "callback_data", "CONV_DATE_FILTER"),
                Map.of("text", "👤 Change Customer", "callback_data", "CONV_RETRY_CUST")
        ));
        rows.add(List.of(Map.of("text", "🔙 Back to Menu", "callback_data", "NAV_MENU")));

        apiClient.sendMessage(botToken, chatId, sb.toString(), "Markdown", Map.of("inline_keyboard", rows));
    }

    private void sendConvertDateFilterMenu(Long chatId, String botToken) throws Exception {
        Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                List.of(Map.of("text", "📅 This Month", "callback_data", "CONV_RANGE|THIS_MONTH")),
                List.of(Map.of("text", "📅 Last Month", "callback_data", "CONV_RANGE|LAST_MONTH")),
                List.of(Map.of("text", "📅 Last 90 Days", "callback_data", "CONV_RANGE|LAST_90_DAYS")),
                List.of(Map.of("text", "📅 Custom Date Range", "callback_data", "CONV_RANGE|CUSTOM")),
                List.of(Map.of("text", "🔄 Clear Date Filter", "callback_data", "CONV_RANGE|CLEAR")),
                List.of(Map.of("text", "🔙 Back to Estimates", "callback_data", "CONV_EST_BACK"))
        ));
        apiClient.sendMessage(botToken, chatId, "📅 *Filter Estimates by Date*\nSelect period:", "Markdown", keyboard);
    }

    private void handleConvertDateRangeFilter(Long chatId, Long firmId, String rangeKey, String botToken) throws Exception {
        ConversationContext ctx = contexts.get(chatId);
        if (ctx == null) {
            sendMainMenu(chatId, botToken, sessionManager.getSession(chatId).orElse(null));
            return;
        }

        LocalDate now = LocalDate.now();
        switch (rangeKey) {
            case "THIS_MONTH":
                ctx.filterFrom = now.withDayOfMonth(1);
                ctx.filterTo = now;
                ctx.pageNumber = 0;
                renderConvertEstimateList(chatId, firmId, ctx, botToken);
                break;
            case "LAST_MONTH":
                LocalDate prevM = now.minusMonths(1);
                ctx.filterFrom = prevM.withDayOfMonth(1);
                ctx.filterTo = prevM.withDayOfMonth(prevM.lengthOfMonth());
                ctx.pageNumber = 0;
                renderConvertEstimateList(chatId, firmId, ctx, botToken);
                break;
            case "LAST_90_DAYS":
                ctx.filterFrom = now.minusDays(90);
                ctx.filterTo = now;
                ctx.pageNumber = 0;
                renderConvertEstimateList(chatId, firmId, ctx, botToken);
                break;
            case "CLEAR":
                ctx.filterFrom = null;
                ctx.filterTo = null;
                ctx.pageNumber = 0;
                renderConvertEstimateList(chatId, firmId, ctx, botToken);
                break;
            case "CUSTOM":
                sessionManager.setConversationState(chatId, "CONV_CUSTOM_START");
                apiClient.sendMessage(botToken, chatId,
                        "📅 *Custom Date Range*\n\nEnter *start date* (e.g. `2026-04-01` or `01-04-2026`):",
                        "Markdown",
                        Map.of("inline_keyboard", List.of(List.of(Map.of("text", "🔙 Back", "callback_data", "CONV_DATE_FILTER")))));
                break;
        }
    }

    private void handleConvertCustomDateStart(Long chatId, Long firmId, String text, String botToken) throws Exception {
        LocalDate start = TelegramDateUtils.parseFlexibleDate(text);
        if (start == null) {
            apiClient.sendMessage(botToken, chatId, "❌ Invalid start date format. Please reply with date like `2026-04-01` or `01-04-2026`:", null, null);
            return;
        }
        ConversationContext ctx = contexts.get(chatId);
        if (ctx != null) {
            ctx.filterFrom = start;
            sessionManager.setConversationState(chatId, "CONV_CUSTOM_END");
            apiClient.sendMessage(botToken, chatId,
                    "Start date: *" + TelegramDateUtils.formatHuman(start) + "*\n\nNow enter *end date* (e.g. `2026-04-30`):",
                    "Markdown",
                    Map.of("inline_keyboard", List.of(List.of(Map.of("text", "🔙 Back", "callback_data", "CONV_DATE_FILTER")))));
        }
    }

    private void handleConvertCustomDateEnd(Long chatId, Long firmId, String text, String botToken) throws Exception {
        LocalDate end = TelegramDateUtils.parseFlexibleDate(text);
        if (end == null) {
            apiClient.sendMessage(botToken, chatId, "❌ Invalid end date format. Please reply with date like `2026-04-30` or `30-04-2026`:", null, null);
            return;
        }
        ConversationContext ctx = contexts.get(chatId);
        if (ctx != null) {
            ctx.filterTo = end;
            ctx.pageNumber = 0;
            sessionManager.setConversationState(chatId, "IDLE");
            renderConvertEstimateList(chatId, firmId, ctx, botToken);
        }
    }

    private void handleConvertEstimatePick(Long chatId, Long firmId, Long estimateId, String botToken) throws Exception {
        Optional<Invoice> estOpt = readService.getInvoiceById(firmId, estimateId);
        if (estOpt.isEmpty()) {
            apiClient.sendMessage(botToken, chatId, "❌ Estimate record not found or firm mismatch.", null, null);
            return;
        }

        Invoice estimate = estOpt.get();
        String estNum = estimate.getEstimateNumber() != null ? estimate.getEstimateNumber() : "EST-" + estimate.getId();

        // 1. Check if already converted
        if (estimate.getConvertedInvoiceId() != null || "CONVERTED".equalsIgnoreCase(estimate.getConversionStatus())) {
            Long convId = estimate.getConvertedInvoiceId();
            Optional<Invoice> invOpt = convId != null ? invoiceRepository.findById(convId) : Optional.empty();
            String invNum = invOpt.map(i -> i.getInvoiceNumber() != null ? i.getInvoiceNumber() : "#" + i.getId()).orElse("#" + convId);
            String dateStr = invOpt.map(i -> i.getInvoiceDate() != null ? i.getInvoiceDate().format(DISPLAY_DATE) : "—").orElse("—");

            String msg = "⚠️ *Estimate Already Converted*\n\n" +
                    "Estimate *" + estNum + "* has already been converted to Invoice *" + invNum + "* (" + dateStr + ").\n\n" +
                    "Under RupeeCRM financial integrity rules, a quotation cannot be converted more than once.";

            List<List<Map<String, String>>> rows = new ArrayList<>();
            if (convId != null) {
                rows.add(List.of(Map.of("text", "📥 Download Existing Invoice (" + invNum + ")", "callback_data", "CONFIRM_DL_PDF|" + convId)));
            }
            rows.add(List.of(Map.of("text", "🔙 Back to Estimates", "callback_data", "CONV_EST_BACK")));
            rows.add(List.of(Map.of("text", "❌ Cancel", "callback_data", "NAV_MENU")));

            apiClient.sendMessage(botToken, chatId, msg, "Markdown", Map.of("inline_keyboard", rows));
            return;
        }

        // 2. Check if ineligible/cancelled
        if (estimate.getStatus() != InvoiceStatus.ESTIMATE && estimate.getStatus() != InvoiceStatus.DRAFT) {
            String msg = "⚠️ *Estimate Ineligible for Conversion*\n\n" +
                    "Estimate *" + estNum + "* is currently *" + estimate.getStatus() + "*.\n" +
                    "Only active estimates and drafts can be converted to tax invoices.";

            Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                    List.of(Map.of("text", "🔙 Back to Estimates", "callback_data", "CONV_EST_BACK")),
                    List.of(Map.of("text", "❌ Cancel", "callback_data", "NAV_MENU"))
            ));
            apiClient.sendMessage(botToken, chatId, msg, "Markdown", keyboard);
            return;
        }

        // 3. Render full human-readable details and conversion preview
        renderConvertEstimateDetails(chatId, firmId, estimate, botToken);
    }

    private void renderConvertEstimateDetails(Long chatId, Long firmId, Invoice estimate, String botToken) throws Exception {
        String estNum = estimate.getEstimateNumber() != null ? estimate.getEstimateNumber() : "EST-" + estimate.getId();
        String custName = estimate.getCustomer() != null ? estimate.getCustomer().getName() : "Customer";
        String dateStr = estimate.getInvoiceDate() != null ? estimate.getInvoiceDate().format(DISPLAY_DATE) : "—";

        StringBuilder sb = new StringBuilder("🔄 *Estimate Details & Conversion Preview*\n\n");
        sb.append("• Customer: *").append(custName).append("*\n");
        sb.append("• Source Estimate #: *").append(estNum).append("*\n");
        sb.append("• Estimate Date: *").append(dateStr).append("*\n");
        sb.append("────────────────────────\n");
        sb.append("📋 *Line Items:*\n");

        if (estimate.getItems() != null && !estimate.getItems().isEmpty()) {
            int idx = 1;
            for (InvoiceItem it : estimate.getItems()) {
                sb.append(idx++).append(". *").append(it.getProductName()).append("*\n   ")
                        .append(it.getQty() != null ? it.getQty() : 1).append(" ")
                        .append(it.getUnit() != null ? it.getUnit() : "Units")
                        .append(" × ₹").append(it.getPricePerUnit() != null ? it.getPricePerUnit() : BigDecimal.ZERO)
                        .append(" = ₹").append(it.getLineTotal() != null ? it.getLineTotal() : BigDecimal.ZERO);
                if (it.getGstPercent() != null && it.getGstPercent().compareTo(BigDecimal.ZERO) > 0) {
                    sb.append(" (GST ").append(it.getGstPercent()).append("%)");
                }
                sb.append("\n");
            }
        } else {
            sb.append("No line items.\n");
        }

        sb.append("────────────────────────\n");
        sb.append("Subtotal (Taxable): ₹").append(estimate.getSubtotalWithoutTax() != null ? estimate.getSubtotalWithoutTax() : BigDecimal.ZERO).append("\n");
        sb.append("Tax (GST): ₹").append(estimate.getTotalTax() != null ? estimate.getTotalTax() : BigDecimal.ZERO).append("\n");
        if (estimate.getRoundOff() != null && estimate.getRoundOff().compareTo(BigDecimal.ZERO) != 0) {
            sb.append("Roundoff: ₹").append(estimate.getRoundOff()).append("\n");
        }
        sb.append("Invoice Grand Total: *₹").append(estimate.getTotalAmount() != null ? estimate.getTotalAmount() : BigDecimal.ZERO).append("*\n");
        if (estimate.getCustomerNote() != null && !estimate.getCustomerNote().isBlank()) {
            sb.append("Note: _").append(estimate.getCustomerNote()).append("_\n");
        }

        sb.append("\n*Convert Estimate ").append(estNum).append(" into an Invoice?*");

        Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                List.of(Map.of("text", "✅ Confirm & Convert", "callback_data", "CONV_CONFIRM|" + estimate.getId())),
                List.of(Map.of("text", "🔙 Back to Estimates", "callback_data", "CONV_EST_BACK")),
                List.of(Map.of("text", "❌ Cancel", "callback_data", "NAV_MENU"))
        ));

        apiClient.sendMessage(botToken, chatId, sb.toString(), "Markdown", keyboard);
    }

    private void handleExecuteEstimateConversion(Long chatId, Long userId, Long firmId, Long estimateId, String installationId, String botToken) throws Exception {
        Long prev = TenantContext.getCurrentFirmId();
        try {
            TenantContext.setCurrentFirmId(firmId);
            Invoice saved = invoiceService.convertEstimateToInvoice(estimateId, null);
            sessionManager.clearSessionState(chatId);

            String custName = saved.getCustomer() != null ? saved.getCustomer().getName() : "";
            String custPhone = saved.getCustomer() != null ? saved.getCustomer().getPhone() : "";

            // Queue document delivery with CONVERT operation type for WhatsApp handoff
            deliveryService.queueDelivery(
                    installationId, userId, chatId, firmId,
                    "INVOICE_PDF", saved.getId(), saved.getInvoiceNumber(),
                    "CONVERT", custName, custPhone
            );

            apiClient.sendMessage(botToken, chatId,
                    "🎉 *Estimate Converted Successfully!*\n\n" +
                            "Invoice #: *" + saved.getInvoiceNumber() + "*\n" +
                            "Customer: *" + custName + "*\n" +
                            "Grand Total: *₹" + saved.getTotalAmount() + "*\n\n" +
                            "Generating and uploading official Tax Invoice PDF...",
                    "Markdown", null);

            deliveryService.processPendingDeliveries(botToken);
        } catch (Exception e) {
            log.warn("Estimate conversion failed for estimate {}: {}", estimateId, e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("already been converted")) {
                Optional<Invoice> estOpt = readService.getInvoiceById(firmId, estimateId);
                Long convId = estOpt.map(Invoice::getConvertedInvoiceId).orElse(null);
                List<List<Map<String, String>>> rows = new ArrayList<>();
                if (convId != null) {
                    rows.add(List.of(Map.of("text", "📥 Download Existing Invoice", "callback_data", "CONFIRM_DL_PDF|" + convId)));
                }
                rows.add(List.of(Map.of("text", "🔙 Back to Menu", "callback_data", "NAV_MENU")));
                apiClient.sendMessage(botToken, chatId, "⚠️ " + e.getMessage(), "Markdown", Map.of("inline_keyboard", rows));
            } else {
                Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                        List.of(Map.of("text", "🔙 Back to Menu", "callback_data", "NAV_MENU"))
                ));
                apiClient.sendMessage(botToken, chatId, "❌ Conversion failed: " + e.getMessage(), null, keyboard);
            }
        } finally {
            if (prev != null) TenantContext.setCurrentFirmId(prev);
            else TenantContext.clear();
        }
    }

    // =========================================================================
    // 2. Download Invoices / Quotations (Search-First & Human-Readable)
    // =========================================================================

    private void handleDownloadCustomerSearch(Long chatId, Long firmId, String text, String botToken) throws Exception {
        List<Customer> matches = readService.searchCustomers(firmId, text);
        if (matches.isEmpty()) {
            Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                    List.of(Map.of("text", "🔍 Search Another Name", "callback_data", "DL_RETRY_CUST")),
                    List.of(Map.of("text", "🔙 Back to Menu", "callback_data", "NAV_MENU"))));
            apiClient.sendMessage(botToken, chatId, "❌ No customer found matching '" + text + "'.", null, keyboard);
            return;
        }

        List<List<Map<String, String>>> rows = new ArrayList<>();
        for (Customer c : matches.subList(0, Math.min(6, matches.size()))) {
            String label = c.getName()
                    + (c.getPhone() != null && !c.getPhone().isBlank() ? " (" + c.getPhone() + ")" : "");
            rows.add(List.of(Map.of("text", label, "callback_data", "DL_CUST_SEL|" + c.getId())));
        }
        rows.add(List.of(Map.of("text", "🔍 Search Again", "callback_data", "DL_RETRY_CUST"),
                Map.of("text", "🔙 Back", "callback_data", "M_DOWNLOAD")));

        apiClient.sendMessage(botToken, chatId, "Select customer to view documents:", null,
                Map.of("inline_keyboard", rows));
    }

    private void renderCustomerDocumentList(Long chatId, Long firmId, ConversationContext dlCtx, String botToken)
            throws Exception {
        boolean isEstimate = "ESTIMATE".equalsIgnoreCase(dlCtx.docType);
        String docTypeLabel = isEstimate ? "Quotations" : "Invoices";
        int page = dlCtx.pageNumber;
        int size = 5;

        Page<Invoice> docsPage;
        if (isEstimate) {
            docsPage = readService.getCustomerEstimates(firmId, dlCtx.selectedEntityId, dlCtx.filterFrom,
                    dlCtx.filterTo, page, size);
        } else {
            docsPage = readService.getCustomerInvoices(firmId, dlCtx.selectedEntityId, dlCtx.filterFrom, dlCtx.filterTo,
                    page, size);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📄 *").append(docTypeLabel).append(" for ").append(dlCtx.selectedEntityName).append("*\n");
        if (dlCtx.filterFrom != null && dlCtx.filterTo != null) {
            sb.append("Period: _").append(TelegramDateUtils.formatHuman(dlCtx.filterFrom)).append(" to ")
                    .append(TelegramDateUtils.formatHuman(dlCtx.filterTo)).append("_\n");
        }
        sb.append("────────────────────────\n\n");

        List<List<Map<String, String>>> rows = new ArrayList<>();

        if (docsPage.isEmpty()) {
            sb.append("No ").append(docTypeLabel.toLowerCase()).append(" found for this customer");
            if (dlCtx.filterFrom != null)
                sb.append(" in the selected period.");
            else
                sb.append(".");
        } else {
            for (Invoice inv : docsPage.getContent()) {
                String docNum = isEstimate
                        ? (inv.getEstimateNumber() != null ? inv.getEstimateNumber() : "EST-" + inv.getId())
                        : (inv.getInvoiceNumber() != null ? inv.getInvoiceNumber() : "#" + inv.getId());
                String dateStr = inv.getInvoiceDate() != null ? inv.getInvoiceDate().format(DISPLAY_DATE) : "—";
                String statusStr = inv.getStatus() != null ? inv.getStatus().name() : "PAID";

                sb.append("• *").append(docNum).append("*\n");
                sb.append("  Date: ").append(dateStr).append("\n");
                sb.append("  Amount: ₹").append(inv.getTotalAmount()).append("\n");
                sb.append("  Status: ").append(statusStr).append("\n\n");

                rows.add(List.of(Map.of("text", "📄 " + docNum + " (₹" + inv.getTotalAmount() + ")", "callback_data",
                        "DL_DOC_SEL|" + inv.getId())));
            }
        }

        // Action & Navigation buttons
        List<Map<String, String>> navRow = new ArrayList<>();
        if (page > 0) {
            navRow.add(Map.of("text", "⏪ Newer", "callback_data", "DL_PAGE|" + (page - 1)));
        }
        if (docsPage.hasNext()) {
            navRow.add(Map.of("text", "⏩ Older", "callback_data", "DL_PAGE|" + (page + 1)));
        }
        if (!navRow.isEmpty()) {
            rows.add(navRow);
        }

        rows.add(List.of(
                Map.of("text", "📅 Filter by Date", "callback_data", "DL_DATE_FILTER"),
                Map.of("text", "👤 Change Customer", "callback_data", "DL_RETRY_CUST")));
        rows.add(List.of(Map.of("text", "🔙 Back to Menu", "callback_data", "NAV_MENU")));

        apiClient.sendMessage(botToken, chatId, sb.toString(), "Markdown", Map.of("inline_keyboard", rows));
    }

    private void renderDocumentDetailsPreview(Long chatId, Long firmId, Long invoiceId, String botToken)
            throws Exception {
        Optional<Invoice> invOpt = readService.getInvoiceById(firmId, invoiceId);
        if (invOpt.isEmpty()) {
            apiClient.sendMessage(botToken, chatId, "❌ Document not found or firm mismatch.", null, null);
            return;
        }

        Invoice inv = invOpt.get();
        boolean isEstimate = InvoiceStatus.ESTIMATE.equals(inv.getStatus());
        String typeLabel = isEstimate ? "Quotation" : "Tax Invoice";
        String docNum = isEstimate ? (inv.getEstimateNumber() != null ? inv.getEstimateNumber() : "EST-" + inv.getId())
                : (inv.getInvoiceNumber() != null ? inv.getInvoiceNumber() : "#" + inv.getId());
        String custName = inv.getCustomer() != null ? inv.getCustomer().getName() : "Customer";
        String dateStr = inv.getInvoiceDate() != null ? inv.getInvoiceDate().format(DISPLAY_DATE) : "—";
        String statusStr = inv.getStatus() != null ? inv.getStatus().name() : "PAID";

        String msg = "📄 *" + typeLabel + " Details*\n\n" +
                "• Document #: *" + docNum + "*\n" +
                "• Date: *" + dateStr + "*\n" +
                "• Customer: *" + custName + "*\n" +
                "• Grand Total: *₹" + inv.getTotalAmount() + "*\n" +
                "• Status: *" + statusStr + "*\n\n" +
                "Download official PDF document?";

        Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                List.of(Map.of("text", "📥 Download PDF", "callback_data", "CONFIRM_DL_PDF|" + invoiceId)),
                List.of(Map.of("text", "🔙 Back to List", "callback_data", "DL_DOC_BACK")),
                List.of(Map.of("text", "❌ Cancel", "callback_data", "NAV_MENU"))));

        apiClient.sendMessage(botToken, chatId, msg, "Markdown", keyboard);
    }

    private void handleDownloadCustomDateStart(Long chatId, Long firmId, String text, String botToken)
            throws Exception {
        LocalDate start = TelegramDateUtils.parseFlexibleDate(text);
        if (start == null) {
            apiClient.sendMessage(botToken, chatId,
                    "❌ Invalid start date format. Please reply with date like `2026-04-01` or `01-04-2026`:", null,
                    null);
            return;
        }
        ConversationContext dlCtx = contexts.get(chatId);
        if (dlCtx != null) {
            dlCtx.filterFrom = start;
            sessionManager.setConversationState(chatId, "DL_CUSTOM_END");
            apiClient.sendMessage(botToken, chatId,
                    "Start Date: *" + TelegramDateUtils.formatHuman(start)
                            + "*\n\n👉 Enter end date in format `YYYY-MM-DD` (e.g. `2026-11-27`):",
                    "Markdown", null);
        }
    }

    private void handleDownloadCustomDateEnd(Long chatId, Long firmId, String text, String botToken) throws Exception {
        LocalDate end = TelegramDateUtils.parseFlexibleDate(text);
        if (end == null) {
            apiClient.sendMessage(botToken, chatId,
                    "❌ Invalid end date format. Please reply with date like `2026-11-27` or `27-11-2026`:", null, null);
            return;
        }
        ConversationContext dlCtx = contexts.get(chatId);
        if (dlCtx != null) {
            dlCtx.filterTo = end;
            dlCtx.pageNumber = 0;
            sessionManager.clearSessionState(chatId);
            renderCustomerDocumentList(chatId, firmId, dlCtx, botToken);
        }
    }

    // =========================================================================
    // 3. Statements & Business Reports Implementation
    // =========================================================================

    private void handleReportCustomerSearch(Long chatId, Long firmId, String text, String botToken) throws Exception {
        List<Customer> matches = readService.searchCustomers(firmId, text);
        if (matches.isEmpty()) {
            Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                    List.of(Map.of("text", "🔍 Try Another Name", "callback_data", "RPT_START|CUST")),
                    List.of(Map.of("text", "🔙 Back to Reports", "callback_data", "M_REPORTS"))));
            apiClient.sendMessage(botToken, chatId, "❌ No customer found matching '" + text + "'.", null, keyboard);
            return;
        }

        List<List<Map<String, String>>> rows = new ArrayList<>();
        for (Customer c : matches.subList(0, Math.min(6, matches.size()))) {
            String label = c.getName()
                    + (c.getPhone() != null && !c.getPhone().isBlank() ? " (" + c.getPhone() + ")" : "");
            rows.add(List.of(Map.of("text", label, "callback_data", "RPT_CUST_PICK|" + c.getId())));
        }
        rows.add(List.of(Map.of("text", "🔍 Search Again", "callback_data", "RPT_START|CUST"),
                Map.of("text", "🔙 Back", "callback_data", "M_REPORTS")));

        apiClient.sendMessage(botToken, chatId, "Select customer for statement:", null,
                Map.of("inline_keyboard", rows));
    }

    private void handleReportPartySearch(Long chatId, Long firmId, String text, String botToken) throws Exception {
        List<Party> matches = readService.searchParties(firmId, text);
        if (matches.isEmpty()) {
            Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                    List.of(Map.of("text", "🔍 Try Another Name", "callback_data", "RPT_START|PARTY")),
                    List.of(Map.of("text", "🔙 Back to Reports", "callback_data", "M_REPORTS"))));
            apiClient.sendMessage(botToken, chatId, "❌ No party or vendor found matching '" + text + "'.", null,
                    keyboard);
            return;
        }

        List<List<Map<String, String>>> rows = new ArrayList<>();
        for (Party p : matches.subList(0, Math.min(6, matches.size()))) {
            String label = p.getName()
                    + (p.getPhone() != null && !p.getPhone().isBlank() ? " (" + p.getPhone() + ")" : "");
            rows.add(List.of(Map.of("text", label, "callback_data", "RPT_PARTY_PICK|" + p.getId())));
        }
        rows.add(List.of(Map.of("text", "🔍 Search Again", "callback_data", "RPT_START|PARTY"),
                Map.of("text", "🔙 Back", "callback_data", "M_REPORTS")));

        apiClient.sendMessage(botToken, chatId, "Select Party / Vendor for statement:", null,
                Map.of("inline_keyboard", rows));
    }

    private void handleReportCustomDateStart(Long chatId, String text, String botToken) throws Exception {
        LocalDate start = TelegramDateUtils.parseFlexibleDate(text);
        if (start == null) {
            apiClient.sendMessage(botToken, chatId,
                    "❌ Invalid start date format. Please reply with date like `2026-04-01` or `01-04-2026`:", null,
                    null);
            return;
        }
        ConversationContext rptCtx = contexts.get(chatId);
        if (rptCtx != null) {
            rptCtx.filterFrom = start;
            sessionManager.setConversationState(chatId, "RPT_CUSTOM_END");
            apiClient.sendMessage(botToken, chatId,
                    "Start Date: *" + TelegramDateUtils.formatHuman(start)
                            + "*\n\n👉 Enter end date in format `YYYY-MM-DD` (e.g. `2026-11-27`):",
                    "Markdown", null);
        }
    }

    private void handleReportCustomDateEnd(Long chatId, String text, String botToken) throws Exception {
        LocalDate end = TelegramDateUtils.parseFlexibleDate(text);
        if (end == null) {
            apiClient.sendMessage(botToken, chatId,
                    "❌ Invalid end date format. Please reply with date like `2026-11-27` or `27-11-2026`:", null, null);
            return;
        }
        ConversationContext rptCtx = contexts.get(chatId);
        if (rptCtx != null) {
            rptCtx.filterTo = end;
            sessionManager.clearSessionState(chatId);
            Long firmId = sessionManager.getSession(chatId).map(TelegramUserSession::getActiveFirmId).orElse(1L);
            renderReportConfirmationPreview(chatId, firmId, rptCtx, botToken);
        }
    }

    private void renderReportConfirmationPreview(Long chatId, Long firmId, ConversationContext rptCtx, String botToken)
            throws Exception {
        String reportName;
        String entityLine = "";
        String changeEntityAction = null;

        if ("CUST".equalsIgnoreCase(rptCtx.docType)) {
            reportName = "Customer Statement";
            entityLine = "• Customer: *" + rptCtx.selectedEntityName + "*\n";
            changeEntityAction = "RPT_START|CUST";
        } else if ("PARTY".equalsIgnoreCase(rptCtx.docType)) {
            reportName = "Vendor / Party Statement";
            entityLine = "• Vendor: *" + rptCtx.selectedEntityName + "*\n";
            changeEntityAction = "RPT_START|PARTY";
        } else if ("FIRM".equalsIgnoreCase(rptCtx.docType)) {
            reportName = "Firm Financial Summary";
        } else if ("STOCK".equalsIgnoreCase(rptCtx.docType)) {
            reportName = "Stock Movement Statement";
        } else {
            reportName = "Business Statement";
        }

        String msg = "📋 *Statement Preview*\n\n" +
                "• Report: *" + reportName + "*\n" +
                entityLine +
                "• From: *" + TelegramDateUtils.formatHuman(rptCtx.filterFrom) + "*\n" +
                "• To: *" + TelegramDateUtils.formatHuman(rptCtx.filterTo) + "*\n\n" +
                "Ready to generate official statement PDF?";

        List<List<Map<String, String>>> rows = new ArrayList<>();
        rows.add(List.of(Map.of("text", "📄 Generate Statement", "callback_data", "RPT_CONFIRM_GEN")));

        List<Map<String, String>> changeRow = new ArrayList<>();
        if (changeEntityAction != null) {
            changeRow.add(Map.of("text", "👤 Change Name", "callback_data", changeEntityAction));
        }
        changeRow.add(Map.of("text", "📅 Change Date Range", "callback_data", "RPT_START|" + rptCtx.docType));
        rows.add(changeRow);

        rows.add(List.of(Map.of("text", "❌ Cancel", "callback_data", "NAV_MENU")));

        apiClient.sendMessage(botToken, chatId, msg, "Markdown", Map.of("inline_keyboard", rows));
    }

    private void renderForecastConfirmationPreview(Long chatId, Long firmId, int horizon, String botToken)
            throws Exception {
        String firmName = readService.getFirm(firmId).map(FirmDetails::getFirmName).orElse("RupeeCRM");
        String msg = "📋 *Report Preview*\n\n" +
                "• Report: *Forecast & Replenishment Analytics*\n" +
                "• Forecast Horizon: *" + horizon + " Days*\n" +
                "• Firm: *" + firmName + "*\n\n" +
                "Generate and send analytics report PDF?";

        Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                List.of(Map.of("text", "📈 Generate Report", "callback_data", "RPT_CONFIRM_GEN")),
                List.of(Map.of("text", "🔄 Change Horizon", "callback_data", "RPT_START|FORECAST")),
                List.of(Map.of("text", "❌ Cancel", "callback_data", "NAV_MENU"))));

        apiClient.sendMessage(botToken, chatId, msg, "Markdown", keyboard);
    }

    private void handleExecuteReportGeneration(Long chatId, Long firmId, Long userId, String installationId,
            ConversationContext rptCtx, String botToken) throws Exception {
        String dateParam = (rptCtx.filterFrom != null && rptCtx.filterTo != null)
                ? (rptCtx.filterFrom.toString() + ":" + rptCtx.filterTo.toString())
                : null;

        if ("CUST".equalsIgnoreCase(rptCtx.docType)) {
            Customer c = customerRepository.findById(rptCtx.selectedEntityId).orElse(null);
            String rName = c != null ? c.getName() : rptCtx.selectedEntityName;
            String rPhone = c != null ? c.getPhone() : null;
            deliveryService.queueDelivery(installationId, userId, chatId, firmId, "STMT_CUST_PDF",
                    rptCtx.selectedEntityId, dateParam, "DOWNLOAD", rName, rPhone);
            apiClient.sendMessage(botToken, chatId, "👥 Generating Customer Statement PDF...", null, null);
        } else if ("PARTY".equalsIgnoreCase(rptCtx.docType)) {
            Party p = partyRepository.findById(rptCtx.selectedEntityId).orElse(null);
            String rName = p != null ? p.getName() : rptCtx.selectedEntityName;
            String rPhone = p != null ? p.getPhone() : null;
            deliveryService.queueDelivery(installationId, userId, chatId, firmId, "STMT_PARTY_PDF",
                    rptCtx.selectedEntityId, dateParam, "DOWNLOAD", rName, rPhone);
            apiClient.sendMessage(botToken, chatId, "👤 Generating Party Statement PDF...", null, null);
        } else if ("FIRM".equalsIgnoreCase(rptCtx.docType)) {
            deliveryService.queueDelivery(installationId, userId, chatId, firmId, "STMT_FIRM_PDF", 0L, dateParam,
                    "REPORT", null, null);
            apiClient.sendMessage(botToken, chatId, "🏢 Generating Firm Financial Summary PDF...", null, null);
        } else if ("STOCK".equalsIgnoreCase(rptCtx.docType)) {
            deliveryService.queueDelivery(installationId, userId, chatId, firmId, "STMT_STOCK_PDF", 0L, dateParam,
                    "REPORT", null, null);
            apiClient.sendMessage(botToken, chatId, "📦 Generating Stock Movement Statement PDF...", null, null);
        } else if ("FORECAST".equalsIgnoreCase(rptCtx.docType)) {
            Long horizon = rptCtx.selectedDocId != null ? rptCtx.selectedDocId : 30L;
            deliveryService.queueDelivery(installationId, userId, chatId, firmId, "STMT_FORECAST_PDF", horizon, null,
                    "REPORT", null, null);
            apiClient.sendMessage(botToken, chatId, "📈 Generating " + horizon + "-Day Forecast Analytics...", null,
                    null);
        }

        contexts.remove(chatId);
        deliveryService.processPendingDeliveries(botToken);
    }

    // =========================================================================
    // 4. Create / Download Purchase Order Implementation
    // =========================================================================

    private void handlePoCreateVendorSearch(Long chatId, Long firmId, Long userId, String text, String botToken)
            throws Exception {
        List<Party> matches = readService.searchParties(firmId, text);
        if (matches.isEmpty()) {
            Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                    List.of(Map.of("text", "🔍 Try Another Name", "callback_data", "PO_CREATE_START")),
                    List.of(Map.of("text", "🔙 Back to Menu", "callback_data", "NAV_MENU"))));
            apiClient.sendMessage(botToken, chatId, "❌ No vendor found matching '" + text + "'.", null, keyboard);
            return;
        }

        List<List<Map<String, String>>> rows = new ArrayList<>();
        for (Party p : matches.subList(0, Math.min(6, matches.size()))) {
            String label = p.getName()
                    + (p.getPhone() != null && !p.getPhone().isBlank() ? " (" + p.getPhone() + ")" : "");
            rows.add(List.of(Map.of("text", label, "callback_data", "PO_CREATE_PARTY_PICK|" + p.getId())));
        }
        rows.add(List.of(Map.of("text", "🔍 Search Again", "callback_data", "PO_CREATE_START"),
                Map.of("text", "🔙 Back", "callback_data", "M_PO")));

        apiClient.sendMessage(botToken, chatId, "Select vendor for purchase order:", null,
                Map.of("inline_keyboard", rows));
    }

    private void handlePoDownloadVendorSearch(Long chatId, Long firmId, String text, String botToken) throws Exception {
        List<Party> matches = readService.searchParties(firmId, text);
        if (matches.isEmpty()) {
            Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                    List.of(Map.of("text", "🔍 Try Another Name", "callback_data", "PO_DL_START")),
                    List.of(Map.of("text", "🔙 Back to Menu", "callback_data", "NAV_MENU"))));
            apiClient.sendMessage(botToken, chatId, "❌ No vendor found matching '" + text + "'.", null, keyboard);
            return;
        }

        List<List<Map<String, String>>> rows = new ArrayList<>();
        for (Party p : matches.subList(0, Math.min(6, matches.size()))) {
            String label = p.getName()
                    + (p.getPhone() != null && !p.getPhone().isBlank() ? " (" + p.getPhone() + ")" : "");
            rows.add(List.of(Map.of("text", label, "callback_data", "PO_DL_VENDOR_PICK|" + p.getId())));
        }
        rows.add(List.of(Map.of("text", "🔍 Search Again", "callback_data", "PO_DL_START"),
                Map.of("text", "🔙 Back", "callback_data", "M_PO")));

        apiClient.sendMessage(botToken, chatId, "Select vendor to view purchase orders:", null,
                Map.of("inline_keyboard", rows));
    }

    private void renderPartyPoList(Long chatId, Long firmId, ConversationContext poCtx, String botToken)
            throws Exception {
        int page = poCtx.pageNumber;
        int size = 5;

        Page<PurchaseOrder> posPage = readService.getPartyPurchaseOrders(firmId, poCtx.selectedEntityId,
                poCtx.filterFrom, poCtx.filterTo, page, size);

        StringBuilder sb = new StringBuilder();
        sb.append("📦 *Purchase Orders for ").append(poCtx.selectedEntityName).append("*\n");
        if (poCtx.filterFrom != null && poCtx.filterTo != null) {
            sb.append("Period: _").append(TelegramDateUtils.formatHuman(poCtx.filterFrom)).append(" to ")
                    .append(TelegramDateUtils.formatHuman(poCtx.filterTo)).append("_\n");
        }
        sb.append("────────────────────────\n\n");

        List<List<Map<String, String>>> rows = new ArrayList<>();

        if (posPage.isEmpty()) {
            sb.append("No purchase orders found for this vendor");
            if (poCtx.filterFrom != null)
                sb.append(" in the selected period.");
            else
                sb.append(".");
        } else {
            for (PurchaseOrder po : posPage.getContent()) {
                String poNum = po.getPoNumber() != null ? po.getPoNumber() : "#" + po.getId();
                String dateStr = po.getPoDate() != null ? po.getPoDate().format(DISPLAY_DATE) : "—";
                String statusStr = po.getStatus() != null ? po.getStatus().name() : "ISSUED";

                sb.append("• *").append(poNum).append("*\n");
                sb.append("  Date: ").append(dateStr).append("\n");
                sb.append("  Amount: ₹").append(po.getTotalAmount()).append("\n");
                sb.append("  Status: ").append(statusStr).append("\n\n");

                rows.add(List.of(Map.of("text", "📦 " + poNum + " (₹" + po.getTotalAmount() + ")", "callback_data",
                        "PO_DL_SEL|" + po.getId())));
            }
        }

        // Action & Navigation buttons
        List<Map<String, String>> navRow = new ArrayList<>();
        if (page > 0) {
            navRow.add(Map.of("text", "⏪ Newer", "callback_data", "PO_DL_PAGE|" + (page - 1)));
        }
        if (posPage.hasNext()) {
            navRow.add(Map.of("text", "⏩ Older", "callback_data", "PO_DL_PAGE|" + (page + 1)));
        }
        if (!navRow.isEmpty()) {
            rows.add(navRow);
        }

        rows.add(List.of(
                Map.of("text", "📅 Filter by Date", "callback_data", "PO_DL_DATE_FILTER"),
                Map.of("text", "👤 Change Vendor", "callback_data", "PO_DL_START")));
        rows.add(List.of(Map.of("text", "🔙 Back to Menu", "callback_data", "NAV_MENU")));

        apiClient.sendMessage(botToken, chatId, sb.toString(), "Markdown", Map.of("inline_keyboard", rows));
    }

    private void renderPoDetailsPreview(Long chatId, Long firmId, Long poId, String botToken) throws Exception {
        Optional<PurchaseOrder> poOpt = readService.getPurchaseOrderById(firmId, poId);
        if (poOpt.isEmpty()) {
            apiClient.sendMessage(botToken, chatId, "❌ Purchase Order not found or firm mismatch.", null, null);
            return;
        }

        PurchaseOrder po = poOpt.get();
        String poNum = po.getPoNumber() != null ? po.getPoNumber() : "#" + po.getId();
        String vendorName = po.getPartyName() != null ? po.getPartyName() : "Vendor";
        String dateStr = po.getPoDate() != null ? po.getPoDate().format(DISPLAY_DATE) : "—";
        String statusStr = po.getStatus() != null ? po.getStatus().name() : "ISSUED";

        String msg = "📦 *Purchase Order Details*\n\n" +
                "• PO Number: *" + poNum + "*\n" +
                "• Date: *" + dateStr + "*\n" +
                "• Vendor: *" + vendorName + "*\n" +
                "• Total Amount: *₹" + po.getTotalAmount() + "*\n" +
                "• Status: *" + statusStr + "*\n\n" +
                "Download official Purchase Order PDF?";

        Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                List.of(Map.of("text", "📥 Download PDF", "callback_data", "CONFIRM_PO_DL_PDF|" + poId)),
                List.of(Map.of("text", "🔙 Back to List", "callback_data", "PO_DL_BACK")),
                List.of(Map.of("text", "❌ Cancel", "callback_data", "NAV_MENU"))));

        apiClient.sendMessage(botToken, chatId, msg, "Markdown", keyboard);
    }

    private void handlePoDownloadCustomDateStart(Long chatId, Long firmId, String text, String botToken)
            throws Exception {
        LocalDate start = TelegramDateUtils.parseFlexibleDate(text);
        if (start == null) {
            apiClient.sendMessage(botToken, chatId,
                    "❌ Invalid start date format. Please reply with date like `2026-04-01` or `01-04-2026`:", null,
                    null);
            return;
        }
        ConversationContext poCtx = contexts.get(chatId);
        if (poCtx != null) {
            poCtx.filterFrom = start;
            sessionManager.setConversationState(chatId, "PO_DL_CUSTOM_END");
            apiClient.sendMessage(botToken, chatId,
                    "Start Date: *" + TelegramDateUtils.formatHuman(start)
                            + "*\n\n👉 Enter end date in format `YYYY-MM-DD` (e.g. `2026-11-27`):",
                    "Markdown", null);
        }
    }

    private void handlePoDownloadCustomDateEnd(Long chatId, Long firmId, String text, String botToken)
            throws Exception {
        LocalDate end = TelegramDateUtils.parseFlexibleDate(text);
        if (end == null) {
            apiClient.sendMessage(botToken, chatId,
                    "❌ Invalid end date format. Please reply with date like `2026-11-27` or `27-11-2026`:", null, null);
            return;
        }
        ConversationContext poCtx = contexts.get(chatId);
        if (poCtx != null) {
            poCtx.filterTo = end;
            poCtx.pageNumber = 0;
            sessionManager.clearSessionState(chatId);
            renderPartyPoList(chatId, firmId, poCtx, botToken);
        }
    }

    // =========================================================================
    // 5. Employee Leaves, Advances & Payroll Implementation
    // =========================================================================

    private void startHrFlow(Long chatId, String subFlow, String prompt, String botToken) throws Exception {
        ConversationContext hrCtx = new ConversationContext();
        hrCtx.flow = "HR";
        hrCtx.step = subFlow;
        contexts.put(chatId, hrCtx);

        sessionManager.setConversationState(chatId, "HR_SEARCH_EMP");
        apiClient.sendMessage(botToken, chatId, prompt, "Markdown", null);
    }

    private void handleHrEmployeeSearch(Long chatId, Long firmId, String text, String botToken) throws Exception {
        List<Employee> matches = readService.searchEmployees(firmId, text);
        if (matches.isEmpty()) {
            Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                    List.of(Map.of("text", "🔍 Try Another Name", "callback_data", "M_HR")),
                    List.of(Map.of("text", "🔙 Back to Menu", "callback_data", "NAV_MENU"))));
            apiClient.sendMessage(botToken, chatId, "❌ No employee found matching '" + text + "'.", null, keyboard);
            return;
        }

        List<List<Map<String, String>>> rows = new ArrayList<>();
        for (Employee e : matches.subList(0, Math.min(6, matches.size()))) {
            String label = e.getName()
                    + (e.getPhone() != null && !e.getPhone().isBlank() ? " (" + e.getPhone() + ")" : "");
            rows.add(List.of(Map.of("text", label, "callback_data", "HR_EMP_PICK|" + e.getId())));
        }
        rows.add(List.of(Map.of("text", "🔙 Back", "callback_data", "M_HR")));

        apiClient.sendMessage(botToken, chatId, "Select employee:", null, Map.of("inline_keyboard", rows));
    }

    private void handleHrLeaveDatesInput(Long chatId, String text, String botToken) throws Exception {
        ConversationContext hrCtx = contexts.get(chatId);
        if (hrCtx == null) {
            sessionManager.clearSessionState(chatId);
            return;
        }

        LocalDate start = LocalDate.now();
        LocalDate end = LocalDate.now();

        if (text.toLowerCase().contains(" to ")) {
            String[] dp = text.toLowerCase().split(" to ", 2);
            LocalDate s = TelegramDateUtils.parseFlexibleDate(dp[0]);
            LocalDate e = TelegramDateUtils.parseFlexibleDate(dp[1]);
            if (s != null)
                start = s;
            if (e != null)
                end = e;
        } else {
            LocalDate s = TelegramDateUtils.parseFlexibleDate(text);
            if (s != null) {
                start = s;
                end = s;
            }
        }

        hrCtx.leaveStart = start;
        hrCtx.leaveEnd = end;

        Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                List.of(Map.of("text", "🟢 Casual Leave", "callback_data", "HR_LEAVE_TYPE_PICK|CASUAL"),
                        Map.of("text", "🟡 Sick Leave", "callback_data", "HR_LEAVE_TYPE_PICK|SICK")),
                List.of(Map.of("text", "🔵 Earned Leave", "callback_data", "HR_LEAVE_TYPE_PICK|EARNED"),
                        Map.of("text", "⚪ Unpaid Leave", "callback_data", "HR_LEAVE_TYPE_PICK|UNPAID")),
                List.of(Map.of("text", "❌ Cancel", "callback_data", "NAV_MENU"))));

        apiClient.sendMessage(botToken, chatId,
                "Dates: *" + TelegramDateUtils.formatHuman(start) + " to " + TelegramDateUtils.formatHuman(end)
                        + "*\n\nSelect leave type:",
                "Markdown", keyboard);
    }

    private void handleHrLeaveReasonInput(Long chatId, String text, String botToken) throws Exception {
        ConversationContext hrCtx = contexts.get(chatId);
        if (hrCtx == null) {
            sessionManager.clearSessionState(chatId);
            return;
        }

        String reason = text.trim();
        if ("none".equalsIgnoreCase(reason))
            reason = null;
        hrCtx.leaveReason = reason;

        String msg = "📋 *Confirm Employee Leave*\n\n" +
                "• Employee: *" + hrCtx.selectedEntityName + "*\n" +
                "• Leave Type: *" + hrCtx.leaveType + "*\n" +
                "• From: *" + TelegramDateUtils.formatHuman(hrCtx.leaveStart) + "*\n" +
                "• To: *" + TelegramDateUtils.formatHuman(hrCtx.leaveEnd) + "*\n" +
                "• Reason: *" + (reason != null ? reason : "None") + "*\n\n" +
                "Confirm and save leave record?";

        Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                List.of(Map.of("text", "✅ Confirm & Save Leave", "callback_data", "HR_CONFIRM_LEAVE")),
                List.of(Map.of("text", "❌ Cancel", "callback_data", "NAV_MENU"))));

        sessionManager.clearSessionState(chatId);
        apiClient.sendMessage(botToken, chatId, msg, "Markdown", keyboard);
    }

    private void renderEmployeeLeavesView(Long chatId, Employee emp, String botToken) throws Exception {
        List<LeaveRecord> leaves = readService.getEmployeeLeaves(emp.getId());
        StringBuilder sb = new StringBuilder("🏖️ *Leave Records for " + emp.getName() + "*\n\n");

        if (leaves.isEmpty()) {
            sb.append("No leave records found for this employee.");
        } else {
            for (LeaveRecord lr : leaves.subList(0, Math.min(8, leaves.size()))) {
                String s = lr.getStartDate() != null ? lr.getStartDate().format(DISPLAY_DATE) : "—";
                String e = lr.getEndDate() != null ? lr.getEndDate().format(DISPLAY_DATE) : "—";
                sb.append("• *").append(lr.getType() != null ? lr.getType() : "LEAVE").append("*: ")
                        .append(s).append(" to ").append(e)
                        .append(" (").append(lr.getTotalDays() != null ? lr.getTotalDays() : 1).append(" days)\n");
                if (lr.getReason() != null && !lr.getReason().isBlank()) {
                    sb.append("  Reason: _").append(lr.getReason()).append("_\n");
                }
            }
        }

        Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                List.of(Map.of("text", "👤 Change Employee", "callback_data", "HR_VIEW_LEAVE_START")),
                List.of(Map.of("text", "🔙 Back to HR", "callback_data", "M_HR"))));

        apiClient.sendMessage(botToken, chatId, sb.toString(), "Markdown", keyboard);
    }

    private void handleHrAdvanceAmountInput(Long chatId, String text, String botToken) throws Exception {
        try {
            double amt = Double.parseDouble(text.trim());
            if (amt <= 0) {
                apiClient.sendMessage(botToken, chatId, "Advance amount must be greater than zero. Enter amount:", null,
                        null);
                return;
            }
            ConversationContext hrCtx = contexts.get(chatId);
            if (hrCtx != null) {
                hrCtx.advanceAmount = amt;
                sessionManager.setConversationState(chatId, "HR_ADVANCE_REASON");
                apiClient.sendMessage(botToken, chatId,
                        "Amount: *₹" + amt
                                + "*\n\n👉 Enter repayment instruction / reason (e.g. `Emergency medical — deduct 1000/mo`):",
                        "Markdown", null);
            }
        } catch (NumberFormatException e) {
            apiClient.sendMessage(botToken, chatId,
                    "Invalid number. Please enter a valid advance amount (e.g. `5000`):", null, null);
        }
    }

    private void handleHrAdvanceReasonInput(Long chatId, String text, String botToken) throws Exception {
        ConversationContext hrCtx = contexts.get(chatId);
        if (hrCtx == null) {
            sessionManager.clearSessionState(chatId);
            return;
        }

        hrCtx.advanceReason = text.trim();

        String msg = "📋 *Confirm Salary Advance*\n\n" +
                "• Employee: *" + hrCtx.selectedEntityName + "*\n" +
                "• Advance Amount: *₹" + hrCtx.advanceAmount + "*\n" +
                "• Repayment / Notes: *" + hrCtx.advanceReason + "*\n\n" +
                "Confirm and record salary advance?";

        Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                List.of(Map.of("text", "✅ Confirm & Record Advance", "callback_data", "HR_CONFIRM_ADV")),
                List.of(Map.of("text", "❌ Cancel", "callback_data", "NAV_MENU"))));

        sessionManager.clearSessionState(chatId);
        apiClient.sendMessage(botToken, chatId, msg, "Markdown", keyboard);
    }

    private void renderEmployeeAdvancesView(Long chatId, Employee emp, String botToken) throws Exception {
        List<EmployeeAdvance> advances = readService.getEmployeeAdvances(emp.getId());
        double curBal = emp.getCurrentAdvanceBalance() != null ? emp.getCurrentAdvanceBalance() : 0.0;
        StringBuilder sb = new StringBuilder("💵 *Salary Advances for " + emp.getName() + "*\n");
        sb.append("Current Outstanding Advance: *₹").append(curBal).append("*\n────────────────────────\n\n");

        if (advances.isEmpty()) {
            sb.append("No salary advances recorded for this employee.");
        } else {
            for (EmployeeAdvance a : advances.subList(0, Math.min(8, advances.size()))) {
                String dateStr = a.getDate() != null ? a.getDate().format(DISPLAY_DATE) : "—";
                sb.append("• *₹").append(a.getAmount()).append("* on ").append(dateStr).append("\n");
                if (a.getDescription() != null && !a.getDescription().isBlank()) {
                    sb.append("  Notes: _").append(a.getDescription()).append("_\n");
                }
            }
        }

        Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                List.of(Map.of("text", "👤 Change Employee", "callback_data", "HR_VIEW_ADV_START")),
                List.of(Map.of("text", "🔙 Back to HR", "callback_data", "M_HR"))));

        apiClient.sendMessage(botToken, chatId, sb.toString(), "Markdown", keyboard);
    }

    private void handleHrPayrollMonthInput(Long chatId, Long firmId, String text, String botToken) throws Exception {
        String monthYear = text.trim();
        ConversationContext hrCtx = contexts.get(chatId);
        if (hrCtx == null) {
            sessionManager.clearSessionState(chatId);
            return;
        }

        hrCtx.payrollMonth = monthYear;

        if ("INDIVIDUAL".equalsIgnoreCase(hrCtx.payrollTargetMode)) {
            sessionManager.setConversationState(chatId, "HR_PAYROLL_ADV_DEDUCT");
            Optional<Employee> empOpt = readService.getEmployeeById(firmId, hrCtx.selectedEntityId);
            double curAdv = (empOpt.isPresent() && empOpt.get().getCurrentAdvanceBalance() != null)
                    ? empOpt.get().getCurrentAdvanceBalance()
                    : 0.0;
            apiClient.sendMessage(botToken, chatId,
                    "Month: *" + monthYear + "*\nCurrent Advance Balance: *₹" + curAdv + "*\n\n" +
                            "👉 Enter advance amount to deduct from salary (e.g. `0` or `1000`):",
                    "Markdown", null);
        } else {
            // Process All Employees preview
            hrCtx.payrollAdvanceDeduct = 0.0;
            renderPayrollConfirmationPreview(chatId, firmId, hrCtx, botToken);
        }
    }

    private void handleHrPayrollAdvanceDeductInput(Long chatId, Long firmId, String text, String botToken)
            throws Exception {
        try {
            double deduct = Double.parseDouble(text.trim());
            ConversationContext hrCtx = contexts.get(chatId);
            if (hrCtx != null) {
                hrCtx.payrollAdvanceDeduct = Math.max(0.0, deduct);
                sessionManager.clearSessionState(chatId);
                renderPayrollConfirmationPreview(chatId, firmId, hrCtx, botToken);
            }
        } catch (NumberFormatException e) {
            apiClient.sendMessage(botToken, chatId,
                    "Invalid number. Enter advance deduction amount (e.g. `0` or `2000`):", null, null);
        }
    }

    private void renderPayrollConfirmationPreview(Long chatId, Long firmId, ConversationContext hrCtx, String botToken)
            throws Exception {
        StringBuilder sb = new StringBuilder("📑 *Payroll Confirmation Preview*\n\n");
        sb.append("• Month: *").append(hrCtx.payrollMonth).append("*\n");

        if ("INDIVIDUAL".equalsIgnoreCase(hrCtx.payrollTargetMode)) {
            Optional<Employee> empOpt = readService.getEmployeeById(firmId, hrCtx.selectedEntityId);
            Employee emp = empOpt.orElseThrow();
            double base = emp.getMonthlyBaseSalary() != null ? emp.getMonthlyBaseSalary() : 0.0;
            double advDeduct = hrCtx.payrollAdvanceDeduct != null ? hrCtx.payrollAdvanceDeduct : 0.0;
            double net = Math.max(0.0, base - advDeduct);

            sb.append("• Employee: *").append(emp.getName()).append("*\n");
            sb.append("• Monthly Base Salary: *₹").append(base).append("*\n");
            sb.append("• Advance Deducted: *₹").append(advDeduct).append("*\n");
            sb.append("• Net Payable: *₹").append(net).append("*\n\n");
        } else {
            List<Employee> emps = readService.getEmployees(firmId);
            double totalBase = 0.0;
            for (Employee e : emps) {
                totalBase += (e.getMonthlyBaseSalary() != null ? e.getMonthlyBaseSalary() : 0.0);
            }
            sb.append("• Scope: *All Active Employees (").append(emps.size()).append(" staff)*\n");
            sb.append("• Estimated Total Salary: *₹").append(totalBase).append("*\n\n");
        }

        sb.append("Ready to finalize payroll and generate official payslip documents?");

        Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                List.of(Map.of("text", "✅ Confirm & Finalize Payroll", "callback_data", "HR_CONFIRM_PAYROLL")),
                List.of(Map.of("text", "❌ Cancel", "callback_data", "NAV_MENU"))));

        apiClient.sendMessage(botToken, chatId, sb.toString(), "Markdown", keyboard);
    }

    private void handleExecutePayroll(Long chatId, Long firmId, Long userId, String installationId,
            ConversationContext pCtx, String botToken) throws Exception {
        try {
            if ("INDIVIDUAL".equalsIgnoreCase(pCtx.payrollTargetMode)) {
                SalaryRecord sal = draftService.processSalary(firmId, pCtx.selectedEntityId, pCtx.payrollMonth,
                        pCtx.payrollAdvanceDeduct != null ? pCtx.payrollAdvanceDeduct : 0.0,
                        installationId, userId, chatId, "INDIVIDUAL");

                apiClient.sendMessage(botToken, chatId,
                        "🎉 *Payroll Finalized Successfully!*\n\n" +
                                "• Employee: *" + pCtx.selectedEntityName + "*\n" +
                                "• Month: *" + sal.getMonthYear() + "*\n" +
                                "• Net Paid: *₹" + sal.getNetPaid() + "*\n\n" +
                                "Uploading official payslip PDF...",
                        "Markdown", null);

                deliveryService.processPendingDeliveries(botToken);
            } else {
                List<Employee> emps = readService.getEmployees(firmId);
                int count = 0;
                for (Employee e : emps) {
                    try {
                        draftService.processSalary(firmId, e.getId(), pCtx.payrollMonth, 0.0, installationId, userId,
                                chatId, "BULK");
                        count++;
                    } catch (Exception ignored) {
                    }
                }

                apiClient.sendMessage(botToken, chatId,
                        "🎉 *Company-Wide Payroll Processed!*\n\n" +
                                "Successfully finalized salaries for *" + count + " employees* (" + pCtx.payrollMonth
                                + ").\n" +
                                "Uploading payslip PDF documents...",
                        "Markdown", null);

                deliveryService.processPendingDeliveries(botToken);
            }
        } catch (Exception e) {
            apiClient.sendMessage(botToken, chatId, "❌ Payroll execution failed: " + e.getMessage(), null, null);
        } finally {
            contexts.remove(chatId);
            sessionManager.clearSessionState(chatId);
        }
    }

    // =========================================================================
    // 6. Record Expenses / Savings Guided Handlers
    // =========================================================================

    private void handlePlannerExpenseInput(Long chatId, Long firmId, String text, String botToken) throws Exception {
        try {
            String[] tokens = text.trim().split("\\s+", 2);
            BigDecimal amt = new BigDecimal(tokens[0]);
            String desc = tokens.length > 1 && !tokens[1].isBlank() ? tokens[1].trim() : "General Expense";

            Expense saved = draftService.recordExpense(firmId, amt, desc, "General");
            sessionManager.clearSessionState(chatId);

            apiClient.sendMessage(botToken, chatId,
                    "✅ *Expense Recorded Successfully!*\n\n" +
                            "• Amount: *₹" + saved.getAmount() + "*\n" +
                            "• Description: *" + saved.getTitle() + "*\n" +
                            "• Date: *" + saved.getExpenseDate() + "*",
                    "Markdown", null);
        } catch (Exception e) {
            apiClient.sendMessage(botToken, chatId,
                    "❌ Invalid input. Please send amount followed by description (e.g. `450 Diesel fuel`): "
                            + e.getMessage(),
                    null, null);
        }
    }

    private void handlePlannerSavingInput(Long chatId, Long firmId, String text, String botToken) throws Exception {
        try {
            String[] tokens = text.trim().split("\\s+", 2);
            BigDecimal amt = new BigDecimal(tokens[0]);
            String desc = tokens.length > 1 && !tokens[1].isBlank() ? tokens[1].trim() : "Savings Deposit";

            SavingRecord saved = draftService.recordSaving(firmId, amt, desc);
            sessionManager.clearSessionState(chatId);

            apiClient.sendMessage(botToken, chatId,
                    "✅ *Savings Recorded Successfully!*\n\n" +
                            "• Amount: *₹" + saved.getAmount() + "*\n" +
                            "• Purpose: *" + saved.getTitle() + "*\n" +
                            "• Date: *" + saved.getSavingDate() + "*",
                    "Markdown", null);
        } catch (Exception e) {
            apiClient.sendMessage(botToken, chatId,
                    "❌ Invalid input. Please send amount followed by purpose (e.g. `2000 Emergency Reserve`): "
                            + e.getMessage(),
                    null, null);
        }
    }

    private void handleDraftCustomerPhoneInput(Long chatId, TelegramUserSession session, String text, String botToken)
            throws Exception {
        TelegramWhatsAppUtils.PhoneResult res = TelegramWhatsAppUtils.normalizePhone(text);
        if (!res.isValid()) {
            Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                    List.of(Map.of("text", "⏭ Skip Mobile Number", "callback_data", "DRAFT_CUST_PHONE_SKIP"))));
            apiClient.sendMessage(botToken, chatId,
                    "⚠️ " + res.getErrorMessage() + "\n\nPlease enter a valid mobile number or tap Skip:", null,
                    keyboard);
            return;
        }

        Long draftId = session.getActiveDraftId();
        if (draftId != null) {
            TelegramDraft draft = draftService.getDraft(draftId).orElse(null);
            if (draft != null) {
                if (draft.getCustomerId() != null) {
                    customerRepository.findById(draft.getCustomerId()).ifPresent(c -> {
                        c.setPhone(res.getNormalizedNumber());
                        customerRepository.save(c);
                    });
                }
                draftService.setCustomer(draftId, draft.getCustomerId(), draft.getCustomerName(),
                        res.getNormalizedNumber());
            }
        }

        sessionManager.setConversationState(chatId, "ADDING_ITEM_SEARCH");
        apiClient.sendMessage(botToken, chatId,
                "✅ Mobile number saved: *" + res.getHumanFormatted()
                        + "*\n\n🔍 *Step 2:* Enter product name or barcode to search:",
                "Markdown", null);
    }

    private void handleDraftVendorPhoneInput(Long chatId, TelegramUserSession session, String text, String botToken)
            throws Exception {
        TelegramWhatsAppUtils.PhoneResult res = TelegramWhatsAppUtils.normalizePhone(text);
        if (!res.isValid()) {
            Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                    List.of(Map.of("text", "⏭ Skip Mobile Number", "callback_data", "PO_VENDOR_PHONE_SKIP"))));
            apiClient.sendMessage(botToken, chatId,
                    "⚠️ " + res.getErrorMessage() + "\n\nPlease enter a valid mobile number or tap Skip:", null,
                    keyboard);
            return;
        }

        Long draftId = session.getActiveDraftId();
        if (draftId != null) {
            TelegramDraft draft = draftService.getDraft(draftId).orElse(null);
            if (draft != null) {
                if (draft.getCustomerId() != null) {
                    partyRepository.findById(draft.getCustomerId()).ifPresent(p -> {
                        p.setPhone(res.getNormalizedNumber());
                        partyRepository.save(p);
                    });
                }
                draftService.setCustomer(draftId, draft.getCustomerId(), draft.getCustomerName(),
                        res.getNormalizedNumber());
            }
        }

        sessionManager.setConversationState(chatId, "ADDING_ITEM_SEARCH");
        apiClient.sendMessage(botToken, chatId,
                "✅ Vendor mobile number saved: *" + res.getHumanFormatted()
                        + "*\n\n🔍 *Step 2:* Enter product name or barcode to search:",
                "Markdown", null);
    }

    // =========================================================================
    // Internal State Helper Classes
    // =========================================================================

    private static class StagedItemState {
        Long productId;
        String itemName;
        String variantDetails;
        String unit;
        BigDecimal qty;
        BigDecimal rate;
    }

    public static class ConversationContext {
        public String flow; // DL_DOC, REPORT, DL_PO, HR
        public String docType; // INVOICE, ESTIMATE, CUST, PARTY, FIRM, STOCK, FORECAST
        public String step; // Sub-flow or conversational step
        public Long selectedEntityId; // customerId, partyId, employeeId
        public String selectedEntityName;
        public String selectedEntityPhone;
        public LocalDate filterFrom;
        public LocalDate filterTo;
        public int pageNumber = 0;
        public Long selectedDocId;

        // HR Fields
        public String leaveType;
        public LocalDate leaveStart;
        public LocalDate leaveEnd;
        public String leaveReason;
        public Double advanceAmount;
        public String advanceReason;
        public String payrollMonth;
        public String payrollTargetMode; // ALL, INDIVIDUAL
        public Double payrollAdvanceDeduct;
    }
}
