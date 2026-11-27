package com.billing.simple.billsoft.telegram.service;

import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.dtos.FirmAnalyticsResponse;
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
import java.util.*;

@Service
public class TelegramUpdateDispatcher {

    private static final Logger log = LoggerFactory.getLogger(TelegramUpdateDispatcher.class);

    private final TelegramSecurityManager securityManager;
    private final TelegramSessionManager sessionManager;
    private final TelegramReadService readService;
    private final TelegramDraftService draftService;
    private final TelegramDeliveryService deliveryService;
    private final TelegramApiClient apiClient;
    private final ObjectMapper objectMapper;

    // Temporary staging for item currently being added: draftId -> StagedItemState
    private final Map<Long, StagedItemState> stagedItems = new HashMap<>();

    public TelegramUpdateDispatcher(TelegramSecurityManager securityManager,
                                    TelegramSessionManager sessionManager,
                                    TelegramReadService readService,
                                    TelegramDraftService draftService,
                                    TelegramDeliveryService deliveryService,
                                    TelegramApiClient apiClient,
                                    ObjectMapper objectMapper) {
        this.securityManager = securityManager;
        this.sessionManager = sessionManager;
        this.readService = readService;
        this.draftService = draftService;
        this.deliveryService = deliveryService;
        this.apiClient = apiClient;
        this.objectMapper = objectMapper;
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

    private void handleMessage(JsonNode msg, String botToken, String installationId) throws Exception {
        if (!msg.has("from") || !msg.has("chat")) return;

        JsonNode chat = msg.get("chat");
        String chatType = chat.path("type").asText("private");
        Long chatId = chat.path("id").asLong();
        Long userId = msg.get("from").path("id").asLong();
        String text = msg.path("text").asText("").trim();
        String firstName = msg.get("from").path("first_name").asText("");
        String username = msg.get("from").path("username").asText(null);

        // V1 restriction: private chats only
        if (!"private".equalsIgnoreCase(chatType)) {
            apiClient.sendMessage(botToken, chatId, "⛔ RupeeCRM Assistant operates strictly in private chats.", null, null);
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
            TelegramSecurityManager.PairingResult result = securityManager.attemptPairing(userId, firstName, username, arg);
            if (result.isSuccess()) {
                sessionManager.getOrCreateSession(chatId, userId);
                sendFirmSelectionMenu(chatId, botToken, "✅ *Device Paired Successfully!*\nWelcome, " + firstName + ".\nPlease select an active firm:");
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
                Optional<com.billing.simple.billsoft.telegram.entities.TelegramAuthorizedUser> userOpt = securityManager.getUser(userId);
                if (userOpt.isPresent() && "PENDING_APPROVAL".equalsIgnoreCase(userOpt.get().getStatus())) {
                    apiClient.sendMessage(botToken, chatId,
                            "⏳ *Account Pending Desktop Approval*\n\n" +
                            "Your Telegram account (ID: `" + userId + "`) has submitted a pairing code and is awaiting approval by the RupeeCRM desktop owner.\n\n" +
                            "Please click *Approve* under *Settings → Telegram Bot* on the desktop to activate access.",
                            "Markdown", null);
                    return;
                } else {
                    apiClient.sendMessage(botToken, chatId,
                            "👋 *Welcome to RupeeCRM Assistant*\n\n" +
                            "Your Telegram User ID is: `" + userId + "`\n\n" +
                            "This bot allows authorized team members to query RupeeCRM and create invoices remotely.\n\n" +
                            "To link your account:\n" +
                            "1. Open RupeeCRM on your desktop.\n" +
                            "2. Navigate to *Settings → Telegram Bot*.\n" +
                            "3. Click *Link New Telegram Account* (optional: enter ID `" + userId + "`).\n" +
                            "4. Send `/start YOUR-CODE` here.",
                            "Markdown", null);
                    return;
                }
            }
        }

        // Validate user authorization for all other interactions
        if (!securityManager.isUserAuthorized(userId)) {
            Optional<com.billing.simple.billsoft.telegram.entities.TelegramAuthorizedUser> userOpt = securityManager.getUser(userId);
            if (userOpt.isPresent() && "PENDING_APPROVAL".equalsIgnoreCase(userOpt.get().getStatus())) {
                apiClient.sendMessage(botToken, chatId,
                        "⏳ *Account Pending Desktop Approval*\n\n" +
                        "Your Telegram account (ID: `" + userId + "`) is awaiting approval by the RupeeCRM desktop owner.\n" +
                        "Please click *Approve* under *Settings → Telegram Bot* on the desktop to activate access.",
                        "Markdown", null);
            } else {
                apiClient.sendMessage(botToken, chatId,
                        "⛔ *Unauthorized Device*\n\n" +
                        "Your Telegram User ID is: `" + userId + "`\n\n" +
                        "This Telegram account is not linked to RupeeCRM.\n" +
                        "Ask the desktop owner to generate a pairing code under *Settings → Telegram Bot*, then send `/start <CODE>` here.",
                        "Markdown", null);
            }
            return;
        }

        securityManager.updateLastActive(userId);
        TelegramUserSession session = sessionManager.getOrCreateSession(chatId, userId);

        // Global commands
        if ("/cancel".equals(cmd)) {
            sessionManager.clearSessionState(chatId);
            stagedItems.remove(session.getActiveDraftId());
            apiClient.sendMessage(botToken, chatId, "Cancelled current operation. Returning to menu.", null, null);
            sendMainMenu(chatId, botToken, session);
            return;
        }

        if ("/firm".equals(cmd)) {
            sendFirmSelectionMenu(chatId, botToken, "🏢 *Select Active Firm:*");
            return;
        }

        if ("/menu".equals(cmd) || "/help".equals(cmd)) {
            sendMainMenu(chatId, botToken, session);
            return;
        }

        // Verify active firm selected
        if (session.getActiveFirmId() == null) {
            sendFirmSelectionMenu(chatId, botToken, "⚠️ *No firm selected.* Please choose an active firm to begin:");
            return;
        }

        Long firmId = session.getActiveFirmId();

        // Check if user is in an active conversational workflow state
        String state = session.getConversationState();
        if ("AWAITING_CUSTOMER".equals(state) && !text.startsWith("/")) {
            handleCustomerInput(chatId, userId, session, text, botToken);
            return;
        } else if ("ADDING_ITEM_SEARCH".equals(state) && !text.startsWith("/")) {
            handleItemSearchInput(chatId, session, text, botToken);
            return;
        } else if ("ENTERING_QTY".equals(state) && !text.startsWith("/")) {
            handleItemQtyInput(chatId, session, text, botToken);
            return;
        } else if ("ENTERING_RATE".equals(state) && !text.startsWith("/")) {
            handleItemRateInput(chatId, session, text, botToken);
            return;
        }

        // Read and write commands
        if ("/dashboard".equals(cmd)) {
            handleDashboardCommand(chatId, firmId, botToken);
        } else if ("/customers".equals(cmd)) {
            handleCustomersCommand(chatId, firmId, text, botToken);
        } else if ("/invoices".equals(cmd)) {
            handleInvoicesCommand(chatId, firmId, botToken);
        } else if ("/quotations".equals(cmd)) {
            handleQuotationsCommand(chatId, firmId, botToken);
        } else if ("/stock".equals(cmd)) {
            handleStockCommand(chatId, firmId, text, botToken);
        } else if ("/debtors".equals(cmd)) {
            handleDebtorsCommand(chatId, firmId, botToken);
        } else if ("/newinvoice".equals(cmd)) {
            handleStartNewDocument(chatId, userId, firmId, "INVOICE", botToken);
        } else if ("/newestimate".equals(cmd) || "/newquote".equals(cmd)) {
            handleStartNewDocument(chatId, userId, firmId, "ESTIMATE", botToken);
        } else {
            apiClient.sendMessage(botToken, chatId, "Unrecognized command. Send /menu for available options.", null, null);
        }
    }

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

        switch (action) {
            case "F": // Select Firm: F|{firmId}
                if (parts.length > 1) {
                    Long firmId = Long.parseLong(parts[1]);
                    sessionManager.setChatFirm(chatId, firmId);
                    Optional<FirmDetails> fd = readService.getFirm(firmId);
                    String firmName = fd.map(FirmDetails::getFirmName).orElse("Firm #" + firmId);
                    apiClient.sendMessage(botToken, chatId, "✅ Active firm set to: *" + firmName + "*", "Markdown", null);
                    session.setActiveFirmId(firmId);
                    sendMainMenu(chatId, botToken, session);
                }
                break;

            case "CUST": // Select Customer: CUST|{customerId}
                if (parts.length > 1 && session.getActiveDraftId() != null) {
                    Long custId = Long.parseLong(parts[1]);
                    Optional<Customer> cust = readService.getCustomerById(session.getActiveFirmId(), custId);
                    if (cust.isPresent()) {
                        draftService.setCustomer(session.getActiveDraftId(), custId, cust.get().getName(), cust.get().getPhone());
                        sessionManager.setConversationState(chatId, "ADDING_ITEM_SEARCH");
                        apiClient.sendMessage(botToken, chatId,
                                "Customer: *" + cust.get().getName() + "*\n\n🔍 *Step 2:* Search catalogue for first item (e.g. 'Basmati Rice' or barcode):",
                                "Markdown", null);
                    }
                }
                break;

            case "CUST_NEW": // New customer creation confirmation
                if (session.getActiveDraftId() != null && parts.length > 1) {
                    String custName = parts[1];
                    draftService.setCustomer(session.getActiveDraftId(), null, custName, null);
                    sessionManager.setConversationState(chatId, "ADDING_ITEM_SEARCH");
                    apiClient.sendMessage(botToken, chatId,
                            "New Customer: *" + custName + "*\n\n🔍 *Step 2:* Search catalogue for first item:",
                            "Markdown", null);
                }
                break;

            case "PROD": // Select Product: PROD|{productId}
                if (parts.length > 1 && session.getActiveDraftId() != null) {
                    Long prodId = Long.parseLong(parts[1]);
                    Optional<Product> prod = readService.getProductById(session.getActiveFirmId(), prodId);
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
                                "Selected: *" + p.getName() + "*\nConfigured Unit: *" + staged.unit + "*\nCatalogue Rate: *₹" + staged.rate + "*\n\n👉 Enter Quantity (e.g. 5):",
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
                apiClient.sendMessage(botToken, chatId, "🔍 Search catalogue for next item:", null, null);
                break;

            case "ITEM_PREVIEW": // Finish & preview draft
                handleDraftPreview(chatId, session.getActiveDraftId(), botToken);
                break;

            case "DRAFT_EXEC": // Confirm & execute: DRAFT_EXEC|{draftId}|{version}
                if (parts.length > 2) {
                    Long draftId = Long.parseLong(parts[1]);
                    int version = Integer.parseInt(parts[2]);
                    handleDraftExecution(chatId, draftId, version, installationId, botToken);
                }
                break;

            case "DRAFT_CANCEL":
                if (session.getActiveDraftId() != null) {
                    draftService.cancelDraft(session.getActiveDraftId());
                    sessionManager.clearSessionState(chatId);
                    stagedItems.remove(session.getActiveDraftId());
                    apiClient.sendMessage(botToken, chatId, "Draft cancelled.", null, null);
                    sendMainMenu(chatId, botToken, session);
                }
                break;

            case "INV_PDF": // Download invoice PDF: INV_PDF|{invoiceId}
                if (parts.length > 1) {
                    Long invId = Long.parseLong(parts[1]);
                    deliveryService.queueDelivery(installationId, userId, chatId, session.getActiveFirmId(), "INVOICE_PDF", invId, null);
                    apiClient.sendMessage(botToken, chatId, "📄 Generating Invoice PDF, uploading shortly...", null, null);
                    deliveryService.processPendingDeliveries(botToken);
                }
                break;
        }
    }

    private void handleStartNewDocument(Long chatId, Long userId, Long firmId, String docType, String botToken) throws Exception {
        TelegramDraft draft = draftService.startDraft(chatId, userId, firmId, docType);
        sessionManager.setActiveDraftId(chatId, draft.getDraftId());
        sessionManager.setConversationState(chatId, "AWAITING_CUSTOMER");

        String docLabel = "ESTIMATE".equalsIgnoreCase(docType) ? "Quotation" : "Invoice";
        apiClient.sendMessage(botToken, chatId,
                "📄 *New " + docLabel + "*\n\n*Step 1:* Send customer name or mobile number to search:",
                "Markdown", null);
    }

    private void handleCustomerInput(Long chatId, Long userId, TelegramUserSession session, String text, String botToken) throws Exception {
        List<Customer> matches = readService.searchCustomers(session.getActiveFirmId(), text);
        String safeName = text.trim();
        if (safeName.length() > 30) {
            safeName = safeName.substring(0, 30);
        }

        if (matches.isEmpty()) {
            // Offer to create as new customer
            Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                    List.of(Map.of("text", "➕ Create '" + safeName + "' as New Customer", "callback_data", "CUST_NEW|" + safeName)),
                    List.of(Map.of("text", "🔍 Search Again", "callback_data", "DRAFT_CANCEL"))
            ));
            apiClient.sendMessage(botToken, chatId, "No customer found for '" + text + "'.", null, keyboard);
        } else {
            List<List<Map<String, String>>> rows = new ArrayList<>();
            for (Customer c : matches.subList(0, Math.min(5, matches.size()))) {
                String label = c.getName() + (c.getPhone() != null ? " (" + c.getPhone() + ")" : "");
                rows.add(List.of(Map.of("text", label, "callback_data", "CUST|" + c.getId())));
            }
            rows.add(List.of(Map.of("text", "➕ Create '" + safeName + "' as New Customer", "callback_data", "CUST_NEW|" + safeName)));

            apiClient.sendMessage(botToken, chatId, "Select matching customer:", null, Map.of("inline_keyboard", rows));
        }
    }

    private void handleItemSearchInput(Long chatId, TelegramUserSession session, String text, String botToken) throws Exception {
        List<Product> matches = readService.searchProducts(session.getActiveFirmId(), text);

        if (matches.isEmpty()) {
            apiClient.sendMessage(botToken, chatId, "No products matched '" + text + "'. Please try another search term:", null, null);
            return;
        }

        List<List<Map<String, String>>> rows = new ArrayList<>();
        for (Product p : matches.subList(0, Math.min(6, matches.size()))) {
            String label = p.getName() + " — ₹" + (p.getPrice() != null ? p.getPrice() : 0) + "/" + (p.getUnit() != null ? p.getUnit() : "Pc");
            rows.add(List.of(Map.of("text", label, "callback_data", "PROD|" + p.getId())));
        }
        rows.add(List.of(Map.of("text", "❌ Cancel Item", "callback_data", "DRAFT_CANCEL")));

        apiClient.sendMessage(botToken, chatId, "Select exact product:", null, Map.of("inline_keyboard", rows));
    }

    private void handleItemQtyInput(Long chatId, TelegramUserSession session, String text, String botToken) throws Exception {
        try {
            BigDecimal qty = new BigDecimal(text.trim());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                apiClient.sendMessage(botToken, chatId, "Quantity must be greater than zero. Enter quantity:", null, null);
                return;
            }
            StagedItemState staged = stagedItems.get(session.getActiveDraftId());
            if (staged != null) {
                staged.qty = qty;
                sessionManager.setConversationState(chatId, "ENTERING_RATE");

                Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                        List.of(Map.of("text", "✅ Use Catalogue Rate (₹" + staged.rate + ")", "callback_data", "RATE_DEF"))
                ));

                apiClient.sendMessage(botToken, chatId,
                        "Quantity: *" + qty + " " + staged.unit + "*\nCatalogue Rate: *₹" + staged.rate + "*\n\nTap button below or reply with override rate:",
                        "Markdown", keyboard);
            }
        } catch (NumberFormatException e) {
            apiClient.sendMessage(botToken, chatId, "Invalid number. Please enter a valid quantity (e.g. 5 or 2.5):", null, null);
        }
    }

    private void handleItemRateInput(Long chatId, TelegramUserSession session, String text, String botToken) throws Exception {
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
            apiClient.sendMessage(botToken, chatId, "Invalid number. Please enter a valid rate (e.g. 275.00):", null, null);
        }
    }

    private void finalizeStagedItem(Long chatId, TelegramUserSession session, StagedItemState staged, String botToken) throws Exception {
        BigDecimal lineAmount = staged.qty.multiply(staged.rate);
        sessionManager.setConversationState(chatId, "CONFIRMING_ITEM");

        String msg = "📋 *Confirm Line Item*\n" +
                "Item: *" + staged.itemName + "*\n" +
                "Quantity: *" + staged.qty + " " + staged.unit + "*\n" +
                "Rate: *₹" + staged.rate + " per " + staged.unit + "*\n" +
                "Line Total: *₹" + lineAmount + "*";

        Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                List.of(Map.of("text", "✅ Confirm Item", "callback_data", "ITEM_CONFIRM")),
                List.of(Map.of("text", "❌ Cancel Item", "callback_data", "DRAFT_CANCEL"))
        ));

        apiClient.sendMessage(botToken, chatId, msg, "Markdown", keyboard);
    }

    private void promptNextItemAction(Long chatId, Long draftId, String botToken) throws Exception {
        List<TelegramDraftItem> items = draftService.getDraftItems(draftId);

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
                List.of(Map.of("text", "📄 Finish & Preview Invoice", "callback_data", "ITEM_PREVIEW")),
                List.of(Map.of("text", "❌ Cancel", "callback_data", "DRAFT_CANCEL"))
        ));

        apiClient.sendMessage(botToken, chatId, sb.toString(), "Markdown", keyboard);
    }

    private void handleDraftPreview(Long chatId, Long draftId, String botToken) throws Exception {
        Invoice preview = draftService.previewDraft(draftId);
        TelegramDraft draft = draftService.getDraft(draftId).orElseThrow();

        StringBuilder sb = new StringBuilder("📄 *INVOICE PREVIEW*\n");
        sb.append("Customer: *").append(draft.getCustomerName() != null ? draft.getCustomerName() : "Cash Customer").append("*\n");
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
                List.of(Map.of("text", "✅ Confirm & Save Invoice", "callback_data", "DRAFT_EXEC|" + draftId + "|" + draft.getVersion())),
                List.of(Map.of("text", "➕ Add More Items", "callback_data", "ITEM_NEXT")),
                List.of(Map.of("text", "❌ Cancel Invoice", "callback_data", "DRAFT_CANCEL"))
        ));

        apiClient.sendMessage(botToken, chatId, sb.toString(), "Markdown", keyboard);
    }

    private void handleDraftExecution(Long chatId, Long draftId, int version, String installationId, String botToken) throws Exception {
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

            // Trigger immediate background document delivery
            deliveryService.processPendingDeliveries(botToken);

        } catch (Exception e) {
            apiClient.sendMessage(botToken, chatId, "❌ Execution failed: " + e.getMessage(), null, null);
        }
    }

    private void handleDashboardCommand(Long chatId, Long firmId, String botToken) throws Exception {
        FirmAnalyticsResponse analytics = readService.getDashboard(firmId);
        StringBuilder sb = new StringBuilder("📊 *Firm Dashboard Summary*\n\n");
        sb.append("Today's Business: *₹").append(analytics.getBusinessToday() != null ? analytics.getBusinessToday() : 0.0).append("*\n");
        sb.append("This Month: *₹").append(analytics.getBusinessThisMonth() != null ? analytics.getBusinessThisMonth() : 0.0).append("*\n");
        sb.append("This Year: *₹").append(analytics.getBusinessThisYear() != null ? analytics.getBusinessThisYear() : 0.0).append("*\n");
        sb.append("Total Pending Receivables: *₹").append(analytics.getTotalPending() != null ? analytics.getTotalPending() : 0.0).append("*\n");
        sb.append("Total Invoices: *").append(analytics.getTotalInvoiceCount() != null ? analytics.getTotalInvoiceCount() : 0).append("*\n");
        apiClient.sendMessage(botToken, chatId, sb.toString(), "Markdown", null);
    }

    private void handleCustomersCommand(Long chatId, Long firmId, String text, String botToken) throws Exception {
        String query = text.replace("/customers", "").trim();
        List<Customer> customers = readService.searchCustomers(firmId, query);
        if (customers.isEmpty()) {
            apiClient.sendMessage(botToken, chatId, "No customers found.", null, null);
            return;
        }
        StringBuilder sb = new StringBuilder("👥 *Customers (" + customers.size() + ")*\n\n");
        for (Customer c : customers.subList(0, Math.min(10, customers.size()))) {
            sb.append("• *").append(c.getName()).append("*");
            if (c.getPhone() != null) sb.append(" — 📞 ").append(c.getPhone());
            sb.append("\n");
        }
        apiClient.sendMessage(botToken, chatId, sb.toString(), "Markdown", null);
    }

    private void handleInvoicesCommand(Long chatId, Long firmId, String botToken) throws Exception {
        Page<Invoice> invoices = readService.getRecentInvoices(firmId, 5);
        if (invoices.isEmpty()) {
            apiClient.sendMessage(botToken, chatId, "No invoices found for this firm.", null, null);
            return;
        }
        StringBuilder sb = new StringBuilder("📄 *Recent Invoices*\n\n");
        List<List<Map<String, String>>> rows = new ArrayList<>();
        for (Invoice inv : invoices) {
            String num = inv.getInvoiceNumber() != null ? inv.getInvoiceNumber() : "#" + inv.getId();
            sb.append("• *").append(num).append("* — ₹").append(inv.getTotalAmount())
                    .append(" (").append(inv.getStatus()).append(")\n");
            rows.add(List.of(Map.of("text", "📥 Download " + num, "callback_data", "INV_PDF|" + inv.getId())));
        }
        apiClient.sendMessage(botToken, chatId, sb.toString(), "Markdown", Map.of("inline_keyboard", rows));
    }

    private void handleQuotationsCommand(Long chatId, Long firmId, String botToken) throws Exception {
        Page<Invoice> estimates = readService.getRecentEstimates(firmId, 5);
        if (estimates.isEmpty()) {
            apiClient.sendMessage(botToken, chatId, "No quotations found for this firm.", null, null);
            return;
        }
        StringBuilder sb = new StringBuilder("📝 *Recent Quotations*\n\n");
        for (Invoice est : estimates) {
            String num = est.getEstimateNumber() != null ? est.getEstimateNumber() : "EST-" + est.getId();
            sb.append("• *").append(num).append("* — ₹").append(est.getTotalAmount()).append("\n");
        }
        apiClient.sendMessage(botToken, chatId, sb.toString(), "Markdown", null);
    }

    private void handleStockCommand(Long chatId, Long firmId, String text, String botToken) throws Exception {
        String query = text.replace("/stock", "").trim();
        List<Product> products = readService.searchProducts(firmId, query);
        if (products.isEmpty()) {
            apiClient.sendMessage(botToken, chatId, "No products found matching stock query.", null, null);
            return;
        }
        StringBuilder sb = new StringBuilder("📦 *Inventory Stock Lookup*\n\n");
        for (Product p : products.subList(0, Math.min(10, products.size()))) {
            sb.append("• *").append(p.getName()).append("*\n  Rate: ₹").append(p.getPrice())
                    .append(" | Unit: ").append(p.getUnit() != null ? p.getUnit() : "Pc")
                    .append("\n");
        }
        apiClient.sendMessage(botToken, chatId, sb.toString(), "Markdown", null);
    }

    private void handleDebtorsCommand(Long chatId, Long firmId, String botToken) throws Exception {
        Object summary = readService.getDebtorsSummary(firmId);
        apiClient.sendMessage(botToken, chatId, "💰 *Debtors & Receivables Summary*\nRetrieved firm debtors ledger.", "Markdown", null);
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

    private void sendMainMenu(Long chatId, String botToken, TelegramUserSession session) throws Exception {
        String firmLabel = "None";
        if (session.getActiveFirmId() != null) {
            firmLabel = readService.getFirm(session.getActiveFirmId()).map(FirmDetails::getFirmName).orElse("Firm #" + session.getActiveFirmId());
        }

        String menu = "🤖 *RupeeCRM Assistant Menu*\n" +
                "Active Firm: *" + firmLabel + "*\n\n" +
                "• /newinvoice — Create Tax Invoice\n" +
                "• /newestimate — Create Quotation\n" +
                "• /dashboard — Sales & Business Analytics\n" +
                "• /invoices — Recent Invoices & PDFs\n" +
                "• /quotations — Recent Quotations\n" +
                "• /stock — Inventory Lookup\n" +
                "• /customers — Customer Directory\n" +
                "• /debtors — Receivables & Aging\n" +
                "• /firm — Switch Active Firm\n" +
                "• /cancel — Cancel Active Operation";

        apiClient.sendMessage(botToken, chatId, menu, "Markdown", null);
    }

    private static class StagedItemState {
        Long productId;
        String itemName;
        String variantDetails;
        String unit;
        BigDecimal qty;
        BigDecimal rate;
    }
}
