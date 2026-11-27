package com.billing.simple.billsoft.telegram.service;

import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repo.EmployeeRepository;
import com.billing.simple.billsoft.repositories.PartyRepository;
import com.billing.simple.billsoft.repo.SalaryRecordRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.service.*;
import com.billing.simple.billsoft.telegram.client.TelegramApiClient;
import com.billing.simple.billsoft.telegram.entities.TelegramDocumentDelivery;
import com.billing.simple.billsoft.telegram.repo.TelegramConfigRepository;
import com.billing.simple.billsoft.telegram.repo.TelegramDocumentDeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TelegramDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(TelegramDeliveryService.class);

    private final TelegramDocumentDeliveryRepository deliveryRepo;
    private final TelegramSecurityManager securityManager;
    private final TelegramApiClient apiClient;
    private final InvoiceService invoiceService;
    private final InvoicePdfService invoicePdfService;
    private final PurchaseOrderService poService;
    private final StatementService statementService;
    private final EmployeePdfService employeePdfService;
    private final EmployeeRepository employeeRepository;
    private final SalaryRecordRepository salaryRecordRepository;
    private final FirmDetailsService firmDetailsService;
    private final TelegramReadService readService;
    private final CustomerRepository customerRepository;
    private final PartyRepository partyRepository;

    public TelegramDeliveryService(TelegramDocumentDeliveryRepository deliveryRepo,
            TelegramSecurityManager securityManager,
            TelegramApiClient apiClient,
            InvoiceService invoiceService,
            InvoicePdfService invoicePdfService,
            PurchaseOrderService poService,
            StatementService statementService,
            EmployeePdfService employeePdfService,
            EmployeeRepository employeeRepository,
            SalaryRecordRepository salaryRecordRepository,
            FirmDetailsService firmDetailsService,
            TelegramReadService readService,
            CustomerRepository customerRepository,
            PartyRepository partyRepository) {
        this.deliveryRepo = deliveryRepo;
        this.securityManager = securityManager;
        this.apiClient = apiClient;
        this.invoiceService = invoiceService;
        this.invoicePdfService = invoicePdfService;
        this.poService = poService;
        this.statementService = statementService;
        this.employeePdfService = employeePdfService;
        this.employeeRepository = employeeRepository;
        this.salaryRecordRepository = salaryRecordRepository;
        this.firmDetailsService = firmDetailsService;
        this.readService = readService;
        this.customerRepository = customerRepository;
        this.partyRepository = partyRepository;
    }

    @Transactional
    public TelegramDocumentDelivery queueDelivery(String installationId, Long userId, Long chatId, Long firmId,
            String docType, Long docId, String docNumber) {
        return queueDelivery(installationId, userId, chatId, firmId, docType, docId, docNumber, null, null, null);
    }

    @Transactional
    public TelegramDocumentDelivery queueDelivery(String installationId, Long userId, Long chatId, Long firmId,
            String docType, Long docId, String docNumber, String operationType, String recipientName, String recipientPhone) {
        TelegramDocumentDelivery delivery = new TelegramDocumentDelivery();
        delivery.setInstallationId(installationId != null ? installationId : "LOCAL");
        delivery.setUserId(userId);
        delivery.setChatId(chatId);
        delivery.setFirmId(firmId);
        delivery.setDocumentType(docType);
        delivery.setDocumentId(docId);
        delivery.setDocumentNumber(docNumber);
        delivery.setOperationType(operationType);
        delivery.setRecipientName(recipientName);
        delivery.setRecipientPhone(recipientPhone);
        delivery.setDeliveryState("QUEUED");
        delivery.setRetryCount(0);
        delivery.setCreatedAt(LocalDateTime.now());
        return deliveryRepo.save(delivery);
    }

    public void processPendingDeliveries(String botToken) {
        if (botToken == null || botToken.isBlank())
            return;

        List<TelegramDocumentDelivery> queued = deliveryRepo.findTop20ByDeliveryStateOrderByCreatedAtAsc("QUEUED");
        for (TelegramDocumentDelivery d : queued) {
            processSingleDelivery(d, botToken);
        }
    }

    @Transactional
    public void processSingleDelivery(TelegramDocumentDelivery d, String botToken) {
        // 1. Mandatory pre-flight verification: check if user is active
        if (!securityManager.isUserAuthorized(d.getUserId())) {
            d.setDeliveryState("CANCELLED");
            d.setLastError("User access revoked prior to document upload.");
            deliveryRepo.save(d);
            return;
        }

        d.setDeliveryState("SENDING");
        deliveryRepo.save(d);

        Long prev = TenantContext.getCurrentFirmId();
        try {
            TenantContext.setCurrentFirmId(d.getFirmId());
            byte[] pdfBytes;
            String filename;
            String caption;

            String type = d.getDocumentType();
            if ("INVOICE_PDF".equalsIgnoreCase(type) || "ESTIMATE_PDF".equalsIgnoreCase(type)) {
                Invoice invoice = invoiceService.getById(d.getDocumentId());
                if (invoice == null || !d.getFirmId().equals(invoice.getFirmId())) {
                    d.setDeliveryState("FAILED");
                    d.setLastError("Invoice record not found or cross-firm mismatch.");
                    deliveryRepo.save(d);
                    return;
                }
                pdfBytes = invoicePdfService.generatePdf(invoice, "A4");
                String prefix = "ESTIMATE_PDF".equalsIgnoreCase(type) ? "Estimate" : "Invoice";
                filename = prefix + "_" + (d.getDocumentNumber() != null ? d.getDocumentNumber() : d.getDocumentId())
                        + ".pdf";
                caption = "📄 *" + prefix + " #"
                        + (d.getDocumentNumber() != null ? d.getDocumentNumber() : d.getDocumentId()) + "*\nCustomer: "
                        + (invoice.getCustomer() != null ? invoice.getCustomer().getName() : "Customer");

            } else if ("PO_PDF".equalsIgnoreCase(type)) {
                PurchaseOrder po = poService.getPurchaseOrderById(d.getDocumentId(), d.getFirmId()).orElse(null);
                if (po == null) {
                    d.setDeliveryState("FAILED");
                    d.setLastError("PO record not found.");
                    deliveryRepo.save(d);
                    return;
                }
                pdfBytes = poService.generatePoPdf(d.getDocumentId(), d.getFirmId());
                filename = "PO_" + (d.getDocumentNumber() != null ? d.getDocumentNumber() : d.getDocumentId()) + ".pdf";
                caption = "📦 *Purchase Order #"
                        + (d.getDocumentNumber() != null ? d.getDocumentNumber() : d.getDocumentId()) + "*";

            } else if ("STMT_CUST_PDF".equalsIgnoreCase(type)) {
                LocalDate to = LocalDate.now();
                LocalDate from = to.minusDays(30);
                if (d.getDocumentNumber() != null && d.getDocumentNumber().contains(":")) {
                    String[] dp = d.getDocumentNumber().split(":");
                    try {
                        from = LocalDate.parse(dp[0]);
                        to = LocalDate.parse(dp[1]);
                    } catch (Exception ignored) {
                    }
                }
                pdfBytes = statementService.generateCustomerStatementPdf(d.getFirmId(), d.getDocumentId(), from, to);
                filename = "Customer_Statement_" + d.getDocumentId() + ".pdf";
                caption = "👥 *Customer Statement*\nPeriod: " + TelegramDateUtils.formatHuman(from) + " to "
                        + TelegramDateUtils.formatHuman(to);

            } else if ("STMT_PARTY_PDF".equalsIgnoreCase(type)) {
                LocalDate to = LocalDate.now();
                LocalDate from = to.minusDays(30);
                if (d.getDocumentNumber() != null && d.getDocumentNumber().contains(":")) {
                    String[] dp = d.getDocumentNumber().split(":");
                    try {
                        from = LocalDate.parse(dp[0]);
                        to = LocalDate.parse(dp[1]);
                    } catch (Exception ignored) {
                    }
                }
                pdfBytes = statementService.generatePartyStatementPdf(d.getFirmId(), d.getDocumentId(), from, to);
                filename = "Party_Statement_" + d.getDocumentId() + ".pdf";
                caption = "👤 *Vendor / Party Statement*\nPeriod: " + TelegramDateUtils.formatHuman(from) + " to "
                        + TelegramDateUtils.formatHuman(to);

            } else if ("STMT_FIRM_PDF".equalsIgnoreCase(type)) {
                LocalDate to = LocalDate.now();
                LocalDate from = to.withDayOfMonth(1);
                if (d.getDocumentNumber() != null && d.getDocumentNumber().contains(":")) {
                    String[] dp = d.getDocumentNumber().split(":");
                    try {
                        from = LocalDate.parse(dp[0]);
                        to = LocalDate.parse(dp[1]);
                    } catch (Exception ignored) {
                    }
                }
                pdfBytes = statementService.generateFirmStatementPdf(d.getFirmId(), from, to);
                filename = "Firm_Financial_Summary.pdf";
                caption = "🏢 *Firm Financial Summary*\nPeriod: " + TelegramDateUtils.formatHuman(from) + " to "
                        + TelegramDateUtils.formatHuman(to);

            } else if ("STMT_FORECAST_PDF".equalsIgnoreCase(type)) {
                int horizon = d.getDocumentId() != null && d.getDocumentId() > 0 ? d.getDocumentId().intValue() : 30;
                pdfBytes = statementService.generateForecastAnalyticsStatementPdf(d.getFirmId(), horizon, null, null,
                        null, null, null);
                filename = "Forecast_Replenishment_" + horizon + "d.pdf";
                caption = "📈 *Forecast & Replenishment Analytics* (" + horizon + "-day horizon)";

            } else if ("STMT_STOCK_PDF".equalsIgnoreCase(type)) {
                LocalDate to = LocalDate.now();
                LocalDate from = to.withDayOfMonth(1);
                if (d.getDocumentNumber() != null && d.getDocumentNumber().contains(":")) {
                    String[] dp = d.getDocumentNumber().split(":");
                    try {
                        from = LocalDate.parse(dp[0]);
                        to = LocalDate.parse(dp[1]);
                    } catch (Exception ignored) {
                    }
                }
                pdfBytes = readService.generateStockMovementPdf(d.getFirmId(), from, to);
                filename = "Stock_Movement_Statement.pdf";
                caption = "📦 *Stock Movement Statement*\nPeriod: " + TelegramDateUtils.formatHuman(from) + " to "
                        + TelegramDateUtils.formatHuman(to);

            } else if ("PAYSLIP_PDF".equalsIgnoreCase(type)) {
                SalaryRecord salary = salaryRecordRepository.findById(d.getDocumentId()).orElse(null);
                if (salary == null) {
                    d.setDeliveryState("FAILED");
                    d.setLastError("Salary record not found for payslip.");
                    deliveryRepo.save(d);
                    return;
                }
                Employee emp = salary.getEmployee();
                FirmDetails firm = firmDetailsService.get(d.getFirmId());
                List<SalaryRecord> allEmpSalaries = salaryRecordRepository
                        .findByEmployeeIdOrderByPaymentDateDesc(emp.getId());
                pdfBytes = employeePdfService.generatePayslip(emp, salary, firm, allEmpSalaries);
                filename = "Payslip_" + (emp.getName() != null ? emp.getName().replaceAll("\\s+", "_") : "Employee")
                        + "_" + salary.getMonthYear() + ".pdf";
                caption = "📑 *Official Payslip — " + salary.getMonthYear() + "*\nEmployee: *"
                        + (emp.getName() != null ? emp.getName() : "") + "*\nNet Paid: *₹" + salary.getNetPaid() + "*";

            } else {
                d.setDeliveryState("FAILED");
                d.setLastError("Unsupported document type: " + type);
                deliveryRepo.save(d);
                return;
            }

            // Upload via Telegram Bot API
            apiClient.sendDocument(botToken, d.getChatId(), pdfBytes, filename, caption);

            d.setDeliveryState("SENT");
            d.setCompletedAt(LocalDateTime.now());
            deliveryRepo.save(d);
            log.info("Successfully delivered {} (ID: {}) to chat {}", d.getDocumentType(), d.getDocumentId(),
                    d.getChatId());

            // Offer WhatsApp handoff if eligible
            if (TelegramWhatsAppUtils.isEligible(d.getDocumentType(), d.getOperationType())) {
                try {
                    sendWhatsAppHandoff(d, botToken);
                } catch (Exception ex) {
                    log.warn("Failed sending WhatsApp handoff for delivery {}: {}", d.getDeliveryId(), ex.getMessage());
                }
            }

        } catch (Exception e) {
            log.warn("Failed delivering document {} to chat {}: {}", d.getDeliveryId(), d.getChatId(), e.getMessage());
            d.setRetryCount(d.getRetryCount() + 1);
            if (d.getRetryCount() >= 5) {
                d.setDeliveryState("FAILED");
            } else {
                d.setDeliveryState("QUEUED"); // Re-queue for next retry
            }
            d.setLastError(e.getMessage() != null
                    ? (e.getMessage().length() > 950 ? e.getMessage().substring(0, 950) : e.getMessage())
                    : "Upload error");
            deliveryRepo.save(d);
        } finally {
            if (prev != null)
                TenantContext.setCurrentFirmId(prev);
            else
                TenantContext.clear();
        }
    }

    @Transactional
    public void sendWhatsAppHandoff(TelegramDocumentDelivery d, String botToken) throws Exception {
        if (!TelegramWhatsAppUtils.isEligible(d.getDocumentType(), d.getOperationType())) {
            return;
        }

        resolveRecipientDetails(d);

        String rawPhone = d.getRecipientPhone();
        TelegramWhatsAppUtils.PhoneResult phoneResult = (rawPhone != null && !rawPhone.isBlank())
                ? TelegramWhatsAppUtils.normalizePhone(rawPhone)
                : null;

        String recipientDisplay = (d.getRecipientName() != null && !d.getRecipientName().isBlank())
                ? d.getRecipientName()
                : "Recipient";

        String docLabel = TelegramWhatsAppUtils.getDocumentDisplayLabel(d.getDocumentType(), d.getDocumentNumber());

        if (phoneResult != null && phoneResult.isValid()) {
            String preparedMsg = TelegramWhatsAppUtils.buildPreparedMessage(d.getDocumentType(), d.getDocumentNumber(), d.getRecipientName());
            String waUrl = TelegramWhatsAppUtils.buildWhatsAppUrl(phoneResult.getNormalizedNumber(), preparedMsg);

            String text = "💬 *Send on WhatsApp*\n" +
                    "Document: *" + docLabel + "*\n" +
                    "Recipient: *" + recipientDisplay + "* (" + phoneResult.getHumanFormatted() + ")\n\n" +
                    "_WhatsApp will open with a prepared message. Please attach the PDF from this Telegram conversation before sending._";

            Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                    List.of(Map.of("text", "📲 Open in WhatsApp", "url", waUrl)),
                    List.of(Map.of("text", "✏️ Send to another number", "callback_data", "WA_ALT|" + d.getDeliveryId()))
            ));

            apiClient.sendMessage(botToken, d.getChatId(), text, "Markdown", keyboard);
        } else {
            // Missing or invalid saved phone
            String text = "💬 *Send on WhatsApp*\n" +
                    "Document: *" + docLabel + "*\n" +
                    "No mobile number saved for *" + recipientDisplay + "*.\n\n" +
                    "Would you like to enter a mobile number to open WhatsApp?";

            Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                    List.of(
                            Map.of("text", "📱 Enter Mobile Number", "callback_data", "WA_ENTER|" + d.getDeliveryId()),
                            Map.of("text", "⏭ Skip", "callback_data", "WA_SKIP|" + d.getDeliveryId())
                    )
            ));

            apiClient.sendMessage(botToken, d.getChatId(), text, "Markdown", keyboard);
        }
    }

    @Transactional
    public void sendAlternateWhatsAppLink(Long deliveryId, String newRawPhone, String botToken) throws Exception {
        Optional<TelegramDocumentDelivery> opt = deliveryRepo.findById(deliveryId);
        if (opt.isEmpty()) {
            return;
        }
        TelegramDocumentDelivery d = opt.get();
        if (!TelegramWhatsAppUtils.isEligible(d.getDocumentType(), d.getOperationType())) {
            return;
        }

        TelegramWhatsAppUtils.PhoneResult phoneResult = TelegramWhatsAppUtils.normalizePhone(newRawPhone);
        if (!phoneResult.isValid()) {
            Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                    List.of(Map.of("text", "📱 Try Again", "callback_data", "WA_ENTER|" + d.getDeliveryId())),
                    List.of(Map.of("text", "⏭ Skip", "callback_data", "WA_SKIP|" + d.getDeliveryId()))
            ));
            apiClient.sendMessage(botToken, d.getChatId(),
                    "⚠️ " + phoneResult.getErrorMessage(), null, keyboard);
            return;
        }

        String docLabel = TelegramWhatsAppUtils.getDocumentDisplayLabel(d.getDocumentType(), d.getDocumentNumber());
        String recipientDisplay = (d.getRecipientName() != null && !d.getRecipientName().isBlank())
                ? d.getRecipientName()
                : "Recipient";

        String preparedMsg = TelegramWhatsAppUtils.buildPreparedMessage(d.getDocumentType(), d.getDocumentNumber(), d.getRecipientName());
        String waUrl = TelegramWhatsAppUtils.buildWhatsAppUrl(phoneResult.getNormalizedNumber(), preparedMsg);

        String text = "💬 *WhatsApp Link Ready*\n" +
                "Document: *" + docLabel + "*\n" +
                "Recipient: *" + recipientDisplay + "* (" + phoneResult.getHumanFormatted() + ")\n\n" +
                "_WhatsApp will open with a prepared message. Please attach the PDF from this Telegram conversation before sending._";

        Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(
                List.of(Map.of("text", "📲 Open in WhatsApp", "url", waUrl)),
                List.of(Map.of("text", "✏️ Try different number", "callback_data", "WA_ALT|" + d.getDeliveryId()))
        ));

        apiClient.sendMessage(botToken, d.getChatId(), text, "Markdown", keyboard);
    }

    private void resolveRecipientDetails(TelegramDocumentDelivery d) {
        if (d.getRecipientName() != null && d.getRecipientPhone() != null) {
            return;
        }
        try {
            String type = d.getDocumentType() != null ? d.getDocumentType().toUpperCase() : "";
            if (("INVOICE_PDF".equals(type) || "ESTIMATE_PDF".equals(type)) && d.getDocumentId() != null) {
                Invoice inv = invoiceService.getById(d.getDocumentId());
                if (inv != null && inv.getCustomer() != null) {
                    if (d.getRecipientName() == null) d.setRecipientName(inv.getCustomer().getName());
                    if (d.getRecipientPhone() == null) d.setRecipientPhone(inv.getCustomer().getPhone());
                    deliveryRepo.save(d);
                }
            } else if ("PO_PDF".equals(type) && d.getDocumentId() != null) {
                poService.getPurchaseOrderById(d.getDocumentId(), d.getFirmId()).ifPresent(po -> {
                    if (d.getRecipientName() == null) d.setRecipientName(po.getParty() != null ? po.getParty().getName() : po.getPartyName());
                    if (d.getRecipientPhone() == null && po.getParty() != null) d.setRecipientPhone(po.getParty().getPhone());
                    deliveryRepo.save(d);
                });
            } else if ("STMT_CUST_PDF".equals(type) && d.getDocumentId() != null) {
                customerRepository.findById(d.getDocumentId()).ifPresent(c -> {
                    if (d.getRecipientName() == null) d.setRecipientName(c.getName());
                    if (d.getRecipientPhone() == null) d.setRecipientPhone(c.getPhone());
                    deliveryRepo.save(d);
                });
            } else if ("STMT_PARTY_PDF".equals(type) && d.getDocumentId() != null) {
                partyRepository.findById(d.getDocumentId()).ifPresent(p -> {
                    if (d.getRecipientName() == null) d.setRecipientName(p.getName());
                    if (d.getRecipientPhone() == null) d.setRecipientPhone(p.getPhone());
                    deliveryRepo.save(d);
                });
            } else if ("PAYSLIP_PDF".equals(type) && d.getDocumentId() != null) {
                salaryRecordRepository.findById(d.getDocumentId()).ifPresent(sal -> {
                    if (sal.getEmployee() != null) {
                        if (d.getRecipientName() == null) d.setRecipientName(sal.getEmployee().getName());
                        if (d.getRecipientPhone() == null) d.setRecipientPhone(sal.getEmployee().getPhone());
                        deliveryRepo.save(d);
                    }
                });
            }
        } catch (Exception ex) {
            log.debug("Could not resolve recipient details for delivery {}: {}", d.getDeliveryId(), ex.getMessage());
        }
    }
}
