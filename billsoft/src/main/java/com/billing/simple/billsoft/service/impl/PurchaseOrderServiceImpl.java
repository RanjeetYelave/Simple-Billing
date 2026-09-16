package com.billing.simple.billsoft.service.impl;

import com.billing.simple.billsoft.entities.Party;
import com.billing.simple.billsoft.entities.PartyPayment;
import com.billing.simple.billsoft.entities.PurchaseOrder;
import com.billing.simple.billsoft.entities.PurchaseOrderItem;
import com.billing.simple.billsoft.entities.PurchaseOrderStatus;
import com.billing.simple.billsoft.repositories.PartyPaymentRepository;
import com.billing.simple.billsoft.repositories.PartyRepository;
import com.billing.simple.billsoft.repositories.PurchaseOrderItemRepository;
import com.billing.simple.billsoft.repositories.PurchaseOrderRepository;
import com.billing.simple.billsoft.service.PurchaseOrderPdfService;
import com.billing.simple.billsoft.service.PurchaseOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.entities.Product;

@Service
@Transactional
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

    private final PurchaseOrderRepository poRepository;
    private final PurchaseOrderItemRepository poItemRepository;
    private final PartyRepository partyRepository;
    private final PartyPaymentRepository partyPaymentRepository;
    private final PurchaseOrderPdfService pdfService;
    private final com.billing.simple.billsoft.service.ProductService productService;
    private final ProductRepository productRepository;

    public PurchaseOrderServiceImpl(PurchaseOrderRepository poRepository,
                                    PurchaseOrderItemRepository poItemRepository,
                                    PartyRepository partyRepository,
                                    PartyPaymentRepository partyPaymentRepository,
                                    PurchaseOrderPdfService pdfService,
                                    com.billing.simple.billsoft.service.ProductService productService,
                                    ProductRepository productRepository) {
        this.poRepository = poRepository;
        this.poItemRepository = poItemRepository;
        this.partyRepository = partyRepository;
        this.partyPaymentRepository = partyPaymentRepository;
        this.pdfService = pdfService;
        this.productService = productService;
        this.productRepository = productRepository;
    }

    @Override
    public PurchaseOrder createPurchaseOrder(PurchaseOrder po) {
        Long authoritativeFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        if (authoritativeFirmId != null) {
            po.setFirmId(authoritativeFirmId);
        }
        if (po.getFirmId() == null) {
            throw new IllegalArgumentException("Firm ID is required");
        }
        if (po.getParty() == null || po.getParty().getId() == null) {
            throw new IllegalArgumentException("Party is required");
        }

        Party party = partyRepository.findByIdAndFirmId(po.getParty().getId(), po.getFirmId())
                .orElseThrow(() -> new com.billing.simple.billsoft.security.TenantSecurityException("Party not found with id: " + po.getParty().getId()));

        po.setParty(party);
        // Save party snapshot
        po.setPartyName(party.getName());
        po.setPartyContactPerson(party.getContactPerson());
        po.setPartyPhone(party.getPhone());
        po.setPartyEmail(party.getEmail());
        po.setPartyGstin(party.getGstin());
        po.setPartyPan(party.getPan());
        po.setPartyAddress(party.getAddress());

        if (po.getPoNumber() == null || po.getPoNumber().trim().isEmpty()) {
            po.setPoNumber(generateNextPoNumber(po.getFirmId()));
        }

        if (po.getItems() != null) {
            for (PurchaseOrderItem item : po.getItems()) {
                if (item.getProductId() != null) {
                    if (!productRepository.existsByIdAndFirmId(item.getProductId(), po.getFirmId())) {
                        throw new com.billing.simple.billsoft.security.TenantSecurityException("Referenced product does not belong to the current firm: " + item.getProductId());
                    }
                }
                item.setPurchaseOrder(po);
            }
        }

        po.recalculateTotals();

        // Adjust payment status if fully paid or partial
        if (po.getPaidAmount() == null) {
            po.setPaidAmount(BigDecimal.ZERO);
        }
        if ("PAID".equalsIgnoreCase(po.getPaymentStatus())) {
            po.setPaidAmount(po.getTotalAmount());
        }

        PurchaseOrder savedPo = poRepository.save(po);

        // Sync with party payments if paid amount > 0
        syncPartyPayment(savedPo);

        // If created directly in RECEIVED status, adjust inventory stock
        if (savedPo.getStatus() == PurchaseOrderStatus.RECEIVED) {
            handleStockAdjustment(savedPo, null, PurchaseOrderStatus.RECEIVED);
        }

        return savedPo;
    }

    @Override
    public PurchaseOrder updatePurchaseOrder(Long id, PurchaseOrder updated) {
        Long authoritativeFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        if (authoritativeFirmId == null) authoritativeFirmId = updated.getFirmId();

        PurchaseOrder existing = (authoritativeFirmId != null
                ? poRepository.findByIdAndFirmId(id, authoritativeFirmId)
                : poRepository.findById(id))
                .orElseThrow(() -> new IllegalArgumentException("Purchase Order not found with id: " + id));

        PurchaseOrderStatus oldStatus = existing.getStatus();

        if (updated.getParty() != null && updated.getParty().getId() != null) {
            Party party = partyRepository.findByIdAndFirmId(updated.getParty().getId(), existing.getFirmId())
                    .orElseThrow(() -> new com.billing.simple.billsoft.security.TenantSecurityException("Party not found with id: " + updated.getParty().getId()));
            existing.setParty(party);
            existing.setPartyName(party.getName());
            existing.setPartyContactPerson(party.getContactPerson());
            existing.setPartyPhone(party.getPhone());
            existing.setPartyEmail(party.getEmail());
            existing.setPartyGstin(party.getGstin());
            existing.setPartyPan(party.getPan());
            existing.setPartyAddress(party.getAddress());
        }

        if (updated.getPoNumber() != null && !updated.getPoNumber().trim().isEmpty()) {
            existing.setPoNumber(updated.getPoNumber().trim());
        }
        if (updated.getPoDate() != null) {
            existing.setPoDate(updated.getPoDate());
        }
        existing.setExpectedDeliveryDate(updated.getExpectedDeliveryDate());
        if (updated.getStatus() != null) {
            existing.setStatus(updated.getStatus());
        }
        if (updated.getPaymentStatus() != null) {
            existing.setPaymentStatus(updated.getPaymentStatus());
        }
        if (updated.getPaidAmount() != null) {
            existing.setPaidAmount(updated.getPaidAmount());
        }
        if (updated.getPaymentMethod() != null) {
            existing.setPaymentMethod(updated.getPaymentMethod());
        }
        existing.setPaymentTerms(updated.getPaymentTerms());
        existing.setReferenceNumber(updated.getReferenceNumber());
        existing.setShippingAddress(updated.getShippingAddress());
        existing.setNotes(updated.getNotes());
        existing.setTermsAndConditions(updated.getTermsAndConditions());

        // Update line items
        if (existing.getItems() == null) {
            existing.setItems(new ArrayList<>());
        } else {
            existing.getItems().clear();
        }

        if (updated.getItems() != null) {
            for (PurchaseOrderItem item : updated.getItems()) {
                if (item.getProductId() != null) {
                    if (!productRepository.existsByIdAndFirmId(item.getProductId(), existing.getFirmId())) {
                        throw new com.billing.simple.billsoft.security.TenantSecurityException("Referenced product does not belong to the current firm: " + item.getProductId());
                    }
                }
                item.setPurchaseOrder(existing);
                existing.getItems().add(item);
            }
        }

        existing.recalculateTotals();

        if ("PAID".equalsIgnoreCase(existing.getPaymentStatus())) {
            existing.setPaidAmount(existing.getTotalAmount());
        }

        PurchaseOrder savedPo = poRepository.save(existing);
        syncPartyPayment(savedPo);
        handleStockAdjustment(savedPo, oldStatus, savedPo.getStatus());
        return savedPo;
    }

    private void syncPartyPayment(PurchaseOrder po) {
        if (po == null || po.getId() == null || po.getParty() == null) return;

        List<PartyPayment> existingPayments = partyPaymentRepository.findByPurchaseOrderId(po.getId());

        if (po.getPaidAmount() != null && po.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            PartyPayment payment = existingPayments.isEmpty() ? new PartyPayment() : existingPayments.get(0);
            payment.setPartyId(po.getParty().getId());
            payment.setFirmId(po.getFirmId());
            payment.setPurchaseOrderId(po.getId());
            payment.setAmount(po.getPaidAmount());
            payment.setPaymentDate(po.getPoDate() != null ? po.getPoDate() : LocalDate.now());
            payment.setPaymentMode(po.getPaymentMethod() != null ? po.getPaymentMethod() : "Bank Transfer");
            payment.setReferenceNumber(po.getPoNumber());
            payment.setNotes("Payment for Purchase Order " + po.getPoNumber());
            partyPaymentRepository.save(payment);
        } else {
            // Remove linked payment if paid amount is 0
            if (!existingPayments.isEmpty()) {
                partyPaymentRepository.deleteByPurchaseOrderId(po.getId());
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseOrder> getPurchaseOrdersByFirm(Long firmId) {
        return poRepository.findByFirmIdOrderByPoDateDescIdDesc(firmId);
    }

    @Override
    @Transactional(readOnly = true)
    public com.billing.simple.billsoft.dtos.PageResponse<PurchaseOrder> getPaginatedPurchaseOrders(Long firmId, org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Page<PurchaseOrder> page = poRepository.findByFirmId(firmId, pageable);
        return com.billing.simple.billsoft.dtos.PageResponse.of(page);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseOrder> getPurchaseOrdersByParty(Long firmId, Long partyId) {
        return poRepository.findByFirmIdAndPartyIdOrderByPoDateDescIdDesc(firmId, partyId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PurchaseOrder> getPurchaseOrderById(Long id, Long firmId) {
        if (firmId != null) {
            return poRepository.findByIdAndFirmId(id, firmId);
        }
        return poRepository.findById(id);
    }

    @Override
    public PurchaseOrder updateStatus(Long id, Long firmId, PurchaseOrderStatus status) {
        PurchaseOrder po = (firmId != null ? poRepository.findByIdAndFirmId(id, firmId) : poRepository.findById(id))
                .orElseThrow(() -> new IllegalArgumentException("Purchase Order not found with id: " + id));
        PurchaseOrderStatus oldStatus = po.getStatus();
        po.setStatus(status);
        PurchaseOrder saved = poRepository.save(po);

        handleStockAdjustment(saved, oldStatus, status);
        return saved;
    }

    @Override
    public void deletePurchaseOrder(Long id, Long firmId) {
        PurchaseOrder po = (firmId != null ? poRepository.findByIdAndFirmId(id, firmId) : poRepository.findById(id))
                .orElseThrow(() -> new IllegalArgumentException("Purchase Order not found with id: " + id));

        // If PO was RECEIVED, reverse stock upon deletion
        if (po.getStatus() == PurchaseOrderStatus.RECEIVED) {
            handleStockAdjustment(po, PurchaseOrderStatus.RECEIVED, PurchaseOrderStatus.CANCELLED);
        }

        partyPaymentRepository.deleteByPurchaseOrderId(po.getId());
        poRepository.delete(po);
    }

    private void handleStockAdjustment(PurchaseOrder po, PurchaseOrderStatus oldStatus, PurchaseOrderStatus newStatus) {
        if (po == null || po.getItems() == null) return;

        // If transitioning to RECEIVED from another status (or on initial creation as RECEIVED), increase inventory stock
        if (newStatus == PurchaseOrderStatus.RECEIVED && oldStatus != PurchaseOrderStatus.RECEIVED) {
            for (PurchaseOrderItem item : po.getItems()) {
                Long prodId = resolveOrCreateProduct(item, po.getFirmId(), true);

                if (prodId != null && item.getQuantity() != null && item.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
                    productService.recordStockMovement(
                            prodId,
                            po.getFirmId(),
                            "PURCHASE_RECEIPT",
                            item.getQuantity(),
                            "PURCHASE_ORDER",
                            po.getPoNumber(),
                            "Vendor goods received from PO " + po.getPoNumber() + " (" + (po.getParty() != null ? po.getParty().getName() : "Vendor") + ")"
                    );
                    if (item.getId() != null) {
                        poItemRepository.save(item);
                    }
                }
            }
        }
        // If transitioning away from RECEIVED to another status (e.g. CANCELLED, DRAFT) or upon deletion, reverse stock
        else if (oldStatus == PurchaseOrderStatus.RECEIVED && newStatus != PurchaseOrderStatus.RECEIVED) {
            for (PurchaseOrderItem item : po.getItems()) {
                Long prodId = resolveOrCreateProduct(item, po.getFirmId(), false);

                if (prodId != null && item.getQuantity() != null && item.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
                    productService.recordStockMovement(
                            prodId,
                            po.getFirmId(),
                            "PURCHASE_CANCELLED",
                            item.getQuantity().negate(),
                            "PURCHASE_ORDER",
                            po.getPoNumber(),
                            "PO " + po.getPoNumber() + " changed from RECEIVED to " + newStatus + " - stock reversed"
                    );
                }
            }
        }
    }

    private Long resolveOrCreateProduct(PurchaseOrderItem item, Long firmId, boolean autoCreate) {
        if (item.getProductId() != null) {
            return item.getProductId();
        }
        if (item.getProductName() == null || item.getProductName().trim().isEmpty()) {
            return null;
        }

        String name = item.getProductName().trim();
        List<Product> prods = productRepository.findByFirmId(firmId);
        for (Product p : prods) {
            if (p.getName() != null && p.getName().trim().equalsIgnoreCase(name)) {
                item.setProductId(p.getId());
                return p.getId();
            }
        }

        if (autoCreate) {
            Product newProd = Product.builder()
                    .firmId(firmId)
                    .name(name)
                    .description(item.getDescription())
                    .hsnCode(item.getHsnCode())
                    .unit(item.getUnit() != null && !item.getUnit().trim().isEmpty() ? item.getUnit() : "pcs")
                    .itemType("GOODS")
                    .price(item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO)
                    .costPrice(item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO)
                    .gstPercentage(item.getGstPercent() != null ? item.getGstPercent() : BigDecimal.ZERO)
                    .stockQuantity(BigDecimal.ZERO)
                    .minStockLevel(new BigDecimal("5.000"))
                    .build();
            Product created = productService.create(newProd);
            item.setProductId(created.getId());
            return created.getId();
        }

        return null;
    }

    @Override
    public PurchaseOrder recordPoPayment(Long id, Long firmId, BigDecimal amount, LocalDate paymentDate, String paymentMode, String referenceNumber, String notes) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }

        PurchaseOrder po = (firmId != null
                ? poRepository.findByIdAndFirmId(id, firmId)
                : poRepository.findById(id))
                .orElseThrow(() -> new IllegalArgumentException("Purchase Order not found with id: " + id));

        BigDecimal currentPaid = po.getPaidAmount() != null ? po.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal newPaid = currentPaid.add(amount);
        po.setPaidAmount(newPaid);

        if (po.getTotalAmount() != null && newPaid.compareTo(po.getTotalAmount()) >= 0) {
            po.setPaymentStatus("PAID");
        } else {
            po.setPaymentStatus("PARTIAL");
        }

        if (paymentMode != null && !paymentMode.trim().isEmpty()) {
            po.setPaymentMethod(paymentMode.trim());
        }

        PurchaseOrder savedPo = poRepository.save(po);

        // Record linked party payment entry
        if (savedPo.getParty() != null) {
            PartyPayment payment = new PartyPayment();
            payment.setPartyId(savedPo.getParty().getId());
            payment.setFirmId(savedPo.getFirmId());
            payment.setPurchaseOrderId(savedPo.getId());
            payment.setAmount(amount);
            payment.setPaymentDate(paymentDate != null ? paymentDate : LocalDate.now());
            payment.setPaymentMode(paymentMode != null ? paymentMode : "Bank Transfer");
            payment.setReferenceNumber(referenceNumber != null ? referenceNumber : savedPo.getPoNumber());
            payment.setNotes(notes != null ? notes : "Payment for Purchase Order " + savedPo.getPoNumber());
            partyPaymentRepository.save(payment);
        }

        return savedPo;
    }

    @Override
    @Transactional(readOnly = true)
    public String generateNextPoNumber(Long firmId) {
        long count = poRepository.countByFirmId(firmId);
        int currentYear = LocalDate.now().getYear();
        return String.format("PO-%d-%04d", currentYear, count + 1);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] generatePoPdf(Long id, Long firmId) throws Exception {
        PurchaseOrder po = (firmId != null
                ? poRepository.findByIdAndFirmId(id, firmId)
                : poRepository.findById(id))
                .orElseThrow(() -> new IllegalArgumentException("Purchase Order not found with id: " + id));
        return pdfService.generatePdf(po);
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, Long> PROCESSED_CLIENT_REQUESTS = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<PurchaseOrder> createPurchaseOrdersBatch(com.billing.simple.billsoft.dtos.BatchPurchaseOrderRequest request, Long firmId) {
        Long authoritativeFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        if (authoritativeFirmId != null) {
            firmId = authoritativeFirmId;
        }
        if (firmId == null) {
            throw new IllegalArgumentException("Authoritative Firm ID is required");
        }
        if (request == null || request.getOrders() == null || request.getOrders().isEmpty()) {
            throw new IllegalArgumentException("At least one purchase order is required for batch creation");
        }

        // 1. Short-lived in-memory duplicate submission / rapid double-click guard
        if (request.getClientRequestId() != null && !request.getClientRequestId().trim().isEmpty()) {
            String reqId = request.getClientRequestId().trim();
            long now = System.currentTimeMillis();
            // Prune entries older than 5 minutes
            PROCESSED_CLIENT_REQUESTS.entrySet().removeIf(e -> now - e.getValue() > 300_000L);
            if (PROCESSED_CLIENT_REQUESTS.putIfAbsent(reqId, now) != null) {
                throw new IllegalStateException("Duplicate batch submission detected. This batch has already been processed.");
            }
        }

        // 2. Process all orders atomically
        List<PurchaseOrder> createdOrders = new ArrayList<>();
        for (PurchaseOrder poInput : request.getOrders()) {
            poInput.setFirmId(firmId);
            if (poInput.getHidePricesOnPo() == null && request.getHidePricesOnPo() != null) {
                poInput.setHidePricesOnPo(request.getHidePricesOnPo());
            }
            if (poInput.getExpectedDeliveryDate() == null && request.getExpectedDeliveryDate() != null) {
                poInput.setExpectedDeliveryDate(request.getExpectedDeliveryDate());
            }
            if ((poInput.getNotes() == null || poInput.getNotes().trim().isEmpty()) && request.getNotes() != null) {
                poInput.setNotes(request.getNotes());
            }
            if (poInput.getPoDate() == null) {
                poInput.setPoDate(LocalDate.now());
            }
            PurchaseOrder created = createPurchaseOrder(poInput);
            createdOrders.add(created);
        }

        // 3. Update preferred vendors if requested
        if (request.getUpdatePreferredVendors() != null) {
            for (com.billing.simple.billsoft.dtos.PreferredVendorUpdate update : request.getUpdatePreferredVendors()) {
                if (update.getProductId() != null && update.getPartyId() != null) {
                    if (productRepository.existsByIdAndFirmId(update.getProductId(), firmId) &&
                            partyRepository.existsByIdAndFirmId(update.getPartyId(), firmId)) {
                        productRepository.updatePreferredPartyId(update.getProductId(), update.getPartyId(), firmId);
                    }
                }
            }
        }

        return createdOrders;
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] generateMergedPoPdf(List<Long> poIds, Long firmId) throws Exception {
        Long authoritativeFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        if (authoritativeFirmId != null) {
            firmId = authoritativeFirmId;
        }
        if (poIds == null || poIds.isEmpty()) {
            return new byte[0];
        }

        List<PurchaseOrder> orders = new ArrayList<>();
        for (Long id : poIds) {
            Optional<PurchaseOrder> poOpt = (firmId != null)
                    ? poRepository.findByIdAndFirmId(id, firmId)
                    : poRepository.findById(id);
            poOpt.ifPresent(orders::add);
        }

        return pdfService.generateMergedPdf(orders);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] generateZipBundle(List<Long> poIds, Long firmId) throws Exception {
        Long authoritativeFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        if (authoritativeFirmId != null) {
            firmId = authoritativeFirmId;
        }
        if (poIds == null || poIds.isEmpty()) {
            return new byte[0];
        }

        List<PurchaseOrder> orders = new ArrayList<>();
        for (Long id : poIds) {
            Optional<PurchaseOrder> poOpt = (firmId != null)
                    ? poRepository.findByIdAndFirmId(id, firmId)
                    : poRepository.findById(id);
            poOpt.ifPresent(orders::add);
        }

        return pdfService.generateZipBundle(orders);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Map<Long, com.billing.simple.billsoft.dtos.ProductVendorHistoryDto> getProductVendorHistory(Long firmId) {
        Long authoritativeFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        if (authoritativeFirmId != null) {
            firmId = authoritativeFirmId;
        }
        if (firmId == null) {
            return java.util.Collections.emptyMap();
        }

        List<Object[]> rawList = poRepository.findProductVendorHistoryRaw(firmId);
        java.util.Map<Long, com.billing.simple.billsoft.dtos.ProductVendorHistoryDto> historyMap = new java.util.LinkedHashMap<>();

        for (Object[] row : rawList) {
            if (row == null || row.length < 5 || row[0] == null) continue;
            Long productId = ((Number) row[0]).longValue();
            Long partyId = row[1] != null ? ((Number) row[1]).longValue() : null;
            String partyName = row[2] != null ? row[2].toString() : null;
            String poNumber = row[3] != null ? row[3].toString() : null;
            LocalDate poDate = (row[4] instanceof LocalDate) ? (LocalDate) row[4] : null;
            BigDecimal qty = (row.length > 5 && row[5] instanceof BigDecimal) ? (BigDecimal) row[5] : BigDecimal.ONE;

            if (!historyMap.containsKey(productId)) {
                // First encountered entry is the latest because of ORDER BY poDate DESC, id DESC
                com.billing.simple.billsoft.dtos.ProductVendorHistoryDto dto = com.billing.simple.billsoft.dtos.ProductVendorHistoryDto.builder()
                        .productId(productId)
                        .latestPartyId(partyId)
                        .latestPartyName(partyName)
                        .latestPoNumber(poNumber)
                        .latestPoDate(poDate)
                        .totalQuantityOrdered(qty)
                        .build();
                historyMap.put(productId, dto);
            } else {
                com.billing.simple.billsoft.dtos.ProductVendorHistoryDto existing = historyMap.get(productId);
                if (qty != null && existing.getTotalQuantityOrdered() != null) {
                    existing.setTotalQuantityOrdered(existing.getTotalQuantityOrdered().add(qty));
                }
            }
        }

        return historyMap;
    }
}
