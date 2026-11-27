package com.billing.simple.billsoft.telegram.service;

import com.billing.simple.billsoft.dtos.InvoiceRequest;
import com.billing.simple.billsoft.dtos.InvoiceRequestItem;
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.InvoiceStatus;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.service.CustomerService;
import com.billing.simple.billsoft.service.InvoiceService;
import com.billing.simple.billsoft.service.ProductService;
import com.billing.simple.billsoft.telegram.entities.TelegramDocumentDelivery;
import com.billing.simple.billsoft.telegram.entities.TelegramDraft;
import com.billing.simple.billsoft.telegram.entities.TelegramDraftItem;
import com.billing.simple.billsoft.telegram.repo.TelegramDocumentDeliveryRepository;
import com.billing.simple.billsoft.telegram.repo.TelegramDraftItemRepository;
import com.billing.simple.billsoft.telegram.repo.TelegramDraftRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class TelegramDraftService {

    private static final Logger log = LoggerFactory.getLogger(TelegramDraftService.class);

    private final TelegramDraftRepository draftRepo;
    private final TelegramDraftItemRepository itemRepo;
    private final TelegramDocumentDeliveryRepository deliveryRepo;
    private final InvoiceService invoiceService;
    private final CustomerService customerService;
    private final ProductService productService;

    public TelegramDraftService(TelegramDraftRepository draftRepo,
                                TelegramDraftItemRepository itemRepo,
                                TelegramDocumentDeliveryRepository deliveryRepo,
                                InvoiceService invoiceService,
                                CustomerService customerService,
                                ProductService productService) {
        this.draftRepo = draftRepo;
        this.itemRepo = itemRepo;
        this.deliveryRepo = deliveryRepo;
        this.invoiceService = invoiceService;
        this.customerService = customerService;
        this.productService = productService;
    }

    @Transactional
    public TelegramDraft startDraft(Long chatId, Long userId, Long firmId, String documentType) {
        // Cancel any active draft for this chat
        draftRepo.findByChatIdAndDraftStateNot(chatId, "EXECUTED").ifPresent(d -> {
            if ("BUILDING".equals(d.getDraftState()) || "PREVIEW".equals(d.getDraftState())) {
                d.setDraftState("CANCELLED");
                draftRepo.save(d);
            }
        });

        TelegramDraft draft = new TelegramDraft();
        draft.setChatId(chatId);
        draft.setUserId(userId);
        draft.setFirmId(firmId);
        draft.setDocumentType(documentType != null ? documentType : "INVOICE");
        draft.setIdempotencyKey(UUID.randomUUID().toString());
        draft.setDraftState("BUILDING");
        draft.setVersion(1);
        draft.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        draft.setCreatedAt(LocalDateTime.now());
        draft.setUpdatedAt(LocalDateTime.now());
        return draftRepo.save(draft);
    }

    public Optional<TelegramDraft> getDraft(Long draftId) {
        return draftRepo.findById(draftId);
    }

    public List<TelegramDraftItem> getDraftItems(Long draftId) {
        return itemRepo.findByDraftIdOrderByItemOrderAsc(draftId);
    }

    @Transactional
    public void setCustomer(Long draftId, Long customerId, String customerName, String customerPhone) {
        draftRepo.findById(draftId).ifPresent(d -> {
            d.setCustomerId(customerId);
            d.setCustomerName(customerName);
            d.setCustomerPhone(customerPhone);
            d.setVersion(d.getVersion() + 1);
            d.setUpdatedAt(LocalDateTime.now());
            draftRepo.save(d);
        });
    }

    @Transactional
    public TelegramDraftItem addItem(Long draftId, Long productId, String itemName, String variantDetails,
                                     String unit, BigDecimal quantity, BigDecimal unitRate, BigDecimal taxRate) {
        TelegramDraft draft = draftRepo.findById(draftId)
                .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + draftId));

        List<TelegramDraftItem> existingItems = itemRepo.findByDraftIdOrderByItemOrderAsc(draftId);
        TelegramDraftItem item = new TelegramDraftItem();
        item.setDraftId(draftId);
        item.setItemOrder(existingItems.size() + 1);
        item.setProductId(productId);
        item.setItemName(itemName != null ? itemName : "Item");
        item.setVariantDetails(variantDetails);
        item.setUnit(unit != null ? unit : "Piece");
        item.setQuantity(quantity != null ? quantity : BigDecimal.ONE);
        item.setUnitRate(unitRate != null ? unitRate : BigDecimal.ZERO);
        item.setTaxRate(taxRate != null ? taxRate : BigDecimal.ZERO);

        BigDecimal lineSubtotal = item.getQuantity().multiply(item.getUnitRate());
        BigDecimal taxAmount = lineSubtotal.multiply(item.getTaxRate()).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        item.setTaxAmount(taxAmount);
        item.setLineTotal(lineSubtotal.add(taxAmount));

        TelegramDraftItem savedItem = itemRepo.save(item);

        draft.setVersion(draft.getVersion() + 1);
        draft.setUpdatedAt(LocalDateTime.now());
        draftRepo.save(draft);

        return savedItem;
    }

    @Transactional
    public void removeItem(Long draftId, Long itemId) {
        itemRepo.deleteById(itemId);
        draftRepo.findById(draftId).ifPresent(d -> {
            d.setVersion(d.getVersion() + 1);
            d.setUpdatedAt(LocalDateTime.now());
            draftRepo.save(d);
        });
    }

    @Transactional
    public void cancelDraft(Long draftId) {
        draftRepo.findById(draftId).ifPresent(d -> {
            d.setDraftState("CANCELLED");
            d.setUpdatedAt(LocalDateTime.now());
            draftRepo.save(d);
        });
    }

    @Transactional
    public Invoice previewDraft(Long draftId) {
        TelegramDraft draft = draftRepo.findById(draftId)
                .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + draftId));

        List<TelegramDraftItem> items = itemRepo.findByDraftIdOrderByItemOrderAsc(draftId);
        if (items.isEmpty()) {
            throw new IllegalStateException("Cannot preview draft with no items.");
        }

        InvoiceRequest request = buildInvoiceRequest(draft, items);

        Long prev = TenantContext.getCurrentFirmId();
        try {
            TenantContext.setCurrentFirmId(draft.getFirmId());
            Invoice preview = invoiceService.previewInvoice(request);

            draft.setSubtotal(preview.getSubtotalWithoutTax());
            draft.setTaxAmount(preview.getTotalTax());
            draft.setTotalAmount(preview.getTotalAmount());
            draft.setDraftState("PREVIEW");
            draft.setExpiresAt(LocalDateTime.now().plusMinutes(15));
            draft.setVersion(draft.getVersion() + 1);
            draft.setUpdatedAt(LocalDateTime.now());
            draftRepo.save(draft);

            return preview;
        } finally {
            if (prev != null) TenantContext.setCurrentFirmId(prev);
            else TenantContext.clear();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Invoice executeDraft(Long draftId, Integer expectedVersion, String installationId) {
        // Pessimistic lock for atomic execution claim
        TelegramDraft draft = draftRepo.findByIdForUpdate(draftId)
                .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + draftId));

        // Idempotency: if already executed, load and return
        if ("EXECUTED".equals(draft.getDraftState()) && draft.getCreatedDocumentId() != null) {
            log.info("Draft {} already executed. Returning existing document ID {}.", draftId, draft.getCreatedDocumentId());
            return invoiceService.getById(draft.getCreatedDocumentId());
        }

        if (!"PREVIEW".equals(draft.getDraftState())) {
            throw new IllegalStateException("Draft cannot be executed in state: " + draft.getDraftState());
        }

        if (!draft.getVersion().equals(expectedVersion)) {
            throw new IllegalStateException("Draft version mismatch (expected " + expectedVersion + ", current " + draft.getVersion() + "). Please preview again.");
        }

        if (draft.getExpiresAt().isBefore(LocalDateTime.now())) {
            draft.setDraftState("EXPIRED");
            draftRepo.save(draft);
            throw new IllegalStateException("Draft preview has expired. Please preview and confirm again.");
        }

        List<TelegramDraftItem> items = itemRepo.findByDraftIdOrderByItemOrderAsc(draftId);
        if (items.isEmpty()) {
            throw new IllegalStateException("Cannot execute draft with zero items.");
        }

        InvoiceRequest request = buildInvoiceRequest(draft, items);
        request.setIdempotencyKey(draft.getIdempotencyKey());

        Long prev = TenantContext.getCurrentFirmId();
        Invoice saved;
        try {
            TenantContext.setCurrentFirmId(draft.getFirmId());
            if ("ESTIMATE".equalsIgnoreCase(draft.getDocumentType())) {
                saved = invoiceService.createEstimate(request);
            } else {
                saved = invoiceService.createInvoice(request);
            }
        } finally {
            if (prev != null) TenantContext.setCurrentFirmId(prev);
            else TenantContext.clear();
        }

        String docNumber = saved.getInvoiceNumber() != null ? saved.getInvoiceNumber() : saved.getEstimateNumber();
        draft.setDraftState("EXECUTED");
        draft.setCreatedDocumentId(saved.getId());
        draft.setCreatedDocumentNumber(docNumber);
        draft.setUpdatedAt(LocalDateTime.now());
        draftRepo.save(draft);

        // Transactional outbox: persist delivery intent in the same transaction
        TelegramDocumentDelivery delivery = new TelegramDocumentDelivery();
        delivery.setInstallationId(installationId != null ? installationId : "LOCAL");
        delivery.setUserId(draft.getUserId());
        delivery.setChatId(draft.getChatId());
        delivery.setFirmId(draft.getFirmId());
        delivery.setDocumentType("ESTIMATE".equalsIgnoreCase(draft.getDocumentType()) ? "ESTIMATE_PDF" : "INVOICE_PDF");
        delivery.setDocumentId(saved.getId());
        delivery.setDraftId(draft.getDraftId());
        delivery.setDocumentNumber(docNumber);
        delivery.setDeliveryState("QUEUED");
        delivery.setRetryCount(0);
        delivery.setCreatedAt(LocalDateTime.now());
        deliveryRepo.save(delivery);

        return saved;
    }

    private InvoiceRequest buildInvoiceRequest(TelegramDraft draft, List<TelegramDraftItem> items) {
        InvoiceRequest request = new InvoiceRequest();
        request.setFirmId(draft.getFirmId());
        request.setCustomerId(draft.getCustomerId());
        request.setCustomerName(draft.getCustomerName());
        request.setCustomerPhone(draft.getCustomerPhone());

        if ("ESTIMATE".equalsIgnoreCase(draft.getDocumentType())) {
            request.setStatus(InvoiceStatus.ESTIMATE);
        } else {
            request.setStatus(InvoiceStatus.UNPAID);
        }

        List<InvoiceRequestItem> reqItems = new ArrayList<>();
        for (TelegramDraftItem di : items) {
            InvoiceRequestItem ri = new InvoiceRequestItem();
            ri.setProductId(di.getProductId());
            ri.setProductName(di.getItemName());
            ri.setQty(di.getQuantity() != null ? di.getQuantity().intValue() : 1);
            ri.setUnit(di.getUnit());
            ri.setPricePerUnit(di.getUnitRate());
            ri.setGstPercent(di.getTaxRate());
            reqItems.add(ri);
        }
        request.setItems(reqItems);
        return request;
    }
}
