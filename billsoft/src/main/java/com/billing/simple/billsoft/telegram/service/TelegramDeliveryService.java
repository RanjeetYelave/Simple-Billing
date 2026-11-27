package com.billing.simple.billsoft.telegram.service;

import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.PurchaseOrder;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.service.InvoicePdfService;
import com.billing.simple.billsoft.service.InvoiceService;
import com.billing.simple.billsoft.service.PurchaseOrderService;
import com.billing.simple.billsoft.telegram.client.TelegramApiClient;
import com.billing.simple.billsoft.telegram.entities.TelegramConfig;
import com.billing.simple.billsoft.telegram.entities.TelegramDocumentDelivery;
import com.billing.simple.billsoft.telegram.repo.TelegramConfigRepository;
import com.billing.simple.billsoft.telegram.repo.TelegramDocumentDeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TelegramDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(TelegramDeliveryService.class);

    private final TelegramDocumentDeliveryRepository deliveryRepo;
    private final TelegramSecurityManager securityManager;
    private final TelegramApiClient apiClient;
    private final InvoiceService invoiceService;
    private final InvoicePdfService invoicePdfService;
    private final PurchaseOrderService poService;
    private final TelegramConfigRepository configRepo;

    public TelegramDeliveryService(TelegramDocumentDeliveryRepository deliveryRepo,
                                   TelegramSecurityManager securityManager,
                                   TelegramApiClient apiClient,
                                   InvoiceService invoiceService,
                                   InvoicePdfService invoicePdfService,
                                   PurchaseOrderService poService,
                                   TelegramConfigRepository configRepo) {
        this.deliveryRepo = deliveryRepo;
        this.securityManager = securityManager;
        this.apiClient = apiClient;
        this.invoiceService = invoiceService;
        this.invoicePdfService = invoicePdfService;
        this.poService = poService;
        this.configRepo = configRepo;
    }

    @Transactional
    public TelegramDocumentDelivery queueDelivery(String installationId, Long userId, Long chatId, Long firmId,
                                                  String docType, Long docId, String docNumber) {
        TelegramDocumentDelivery delivery = new TelegramDocumentDelivery();
        delivery.setInstallationId(installationId != null ? installationId : "LOCAL");
        delivery.setUserId(userId);
        delivery.setChatId(chatId);
        delivery.setFirmId(firmId);
        delivery.setDocumentType(docType);
        delivery.setDocumentId(docId);
        delivery.setDocumentNumber(docNumber);
        delivery.setDeliveryState("QUEUED");
        delivery.setRetryCount(0);
        delivery.setCreatedAt(LocalDateTime.now());
        return deliveryRepo.save(delivery);
    }

    public void processPendingDeliveries(String botToken) {
        if (botToken == null || botToken.isBlank()) return;

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

            if ("INVOICE_PDF".equalsIgnoreCase(d.getDocumentType()) || "ESTIMATE_PDF".equalsIgnoreCase(d.getDocumentType())) {
                Invoice invoice = invoiceService.getById(d.getDocumentId());
                if (invoice == null || !d.getFirmId().equals(invoice.getFirmId())) {
                    d.setDeliveryState("FAILED");
                    d.setLastError("Invoice record not found or cross-firm mismatch.");
                    deliveryRepo.save(d);
                    return;
                }
                pdfBytes = invoicePdfService.generatePdf(invoice, "A4");
                String prefix = "ESTIMATE_PDF".equalsIgnoreCase(d.getDocumentType()) ? "Estimate" : "Invoice";
                filename = prefix + "_" + (d.getDocumentNumber() != null ? d.getDocumentNumber() : d.getDocumentId()) + ".pdf";
                caption = "📄 *" + prefix + " #" + (d.getDocumentNumber() != null ? d.getDocumentNumber() : d.getDocumentId()) + "*\nCustomer: " + (invoice.getCustomer() != null ? invoice.getCustomer().getName() : "Customer");
            } else if ("PO_PDF".equalsIgnoreCase(d.getDocumentType())) {
                PurchaseOrder po = poService.getPurchaseOrderById(d.getDocumentId(), d.getFirmId()).orElse(null);
                if (po == null) {
                    d.setDeliveryState("FAILED");
                    d.setLastError("PO record not found.");
                    deliveryRepo.save(d);
                    return;
                }
                pdfBytes = poService.generatePoPdf(d.getDocumentId(), d.getFirmId());
                filename = "PO_" + (d.getDocumentNumber() != null ? d.getDocumentNumber() : d.getDocumentId()) + ".pdf";
                caption = "📦 *Purchase Order #" + (d.getDocumentNumber() != null ? d.getDocumentNumber() : d.getDocumentId()) + "*";
            } else {
                d.setDeliveryState("FAILED");
                d.setLastError("Unsupported document type: " + d.getDocumentType());
                deliveryRepo.save(d);
                return;
            }

            // Upload via Telegram Bot API
            apiClient.sendDocument(botToken, d.getChatId(), pdfBytes, filename, caption);

            d.setDeliveryState("SENT");
            d.setCompletedAt(LocalDateTime.now());
            deliveryRepo.save(d);
            log.info("Successfully delivered {} (ID: {}) to chat {}", d.getDocumentType(), d.getDocumentId(), d.getChatId());

        } catch (Exception e) {
            log.warn("Failed delivering document {} to chat {}: {}", d.getDeliveryId(), d.getChatId(), e.getMessage());
            d.setRetryCount(d.getRetryCount() + 1);
            if (d.getRetryCount() >= 5) {
                d.setDeliveryState("FAILED");
            } else {
                d.setDeliveryState("QUEUED"); // Re-queue for next retry
            }
            d.setLastError(e.getMessage() != null ? (e.getMessage().length() > 950 ? e.getMessage().substring(0, 950) : e.getMessage()) : "Upload error");
            deliveryRepo.save(d);
        } finally {
            if (prev != null) TenantContext.setCurrentFirmId(prev);
            else TenantContext.clear();
        }
    }
}
