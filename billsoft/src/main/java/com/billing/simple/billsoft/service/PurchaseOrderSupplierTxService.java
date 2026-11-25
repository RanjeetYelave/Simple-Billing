package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.events.PurchaseOrderUpdatedEvent;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.repositories.PartyPaymentRepository;
import com.billing.simple.billsoft.repositories.PartyRepository;
import com.billing.simple.billsoft.repositories.PurchaseOrderRepository;
import com.billing.simple.billsoft.security.TenantSecurityException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dedicated transactional worker providing isolated, independent transaction boundaries
 * for each supplier purchase order in a batch. A failure in one supplier's order rolls back
 * that supplier exclusively without affecting other committed suppliers in the batch.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseOrderSupplierTxService {

    private final PurchaseOrderRepository poRepository;
    private final PartyRepository partyRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final PartyPaymentRepository partyPaymentRepository;

    @Autowired(required = false)
    private DocumentSequenceService sequenceService;

    @Autowired(required = false)
    private ApplicationEventPublisher eventPublisher;

    /**
     * Executes single supplier PO creation in an independent transaction.
     * Propagation.REQUIRES_NEW ensures isolated commit/rollback per supplier.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public PurchaseOrder createSupplierPoInNewTransaction(PurchaseOrder po, Long firmId) {
        if (firmId == null) {
            throw new IllegalArgumentException("Firm ID is required");
        }
        po.setFirmId(firmId);

        if (po.getParty() == null || po.getParty().getId() == null) {
            throw new IllegalArgumentException("Supplier party reference is required");
        }

        Party party = partyRepository.findByIdAndFirmId(po.getParty().getId(), firmId)
                .orElseThrow(() -> new TenantSecurityException("Supplier not found or does not belong to active firm: " + po.getParty().getId()));

        po.setParty(party);
        po.setPartyName(party.getName());
        po.setPartyContactPerson(party.getContactPerson());
        po.setPartyPhone(party.getPhone());
        po.setPartyEmail(party.getEmail());
        po.setPartyGstin(party.getGstin());
        po.setPartyPan(party.getPan());
        po.setPartyAddress(party.getAddress());

        if (po.getPoDate() == null) {
            po.setPoDate(LocalDate.now());
        }

        if (po.getPoNumber() == null || po.getPoNumber().trim().isEmpty()) {
            po.setPoNumber(generateNextPoNumber(firmId));
        } else if (sequenceService != null) {
            sequenceService.syncSequenceIfApplicable(DocumentType.PURCHASE_ORDER, firmId, po.getPoNumber(), po.getPoDate());
        }

        if (po.getItems() != null && !po.getItems().isEmpty()) {
            po.setItems(new ArrayList<>(po.getItems()));
            for (PurchaseOrderItem item : po.getItems()) {
                if (item.getProductId() != null) {
                    if (!productRepository.existsByIdAndFirmId(item.getProductId(), firmId)) {
                        throw new TenantSecurityException("Referenced product ID " + item.getProductId() + " does not belong to firm " + firmId);
                    }
                }
                item.setPurchaseOrder(po);
            }
        }

        po.recalculateTotals();

        if (po.getPaidAmount() == null) {
            po.setPaidAmount(BigDecimal.ZERO);
        }
        if ("PAID".equalsIgnoreCase(po.getPaymentStatus())) {
            po.setPaidAmount(po.getTotalAmount());
        }

        PurchaseOrder savedPo = poRepository.save(po);

        // Remember preferred vendor for products upon successful PO creation for this supplier
        if (savedPo.getItems() != null && party.getId() != null) {
            for (PurchaseOrderItem item : savedPo.getItems()) {
                if (item.getProductId() != null) {
                    try {
                        productRepository.updatePreferredPartyId(item.getProductId(), party.getId(), firmId);
                    } catch (Exception e) {
                        log.warn("Failed to update preferredPartyId for product {} to vendor {}: {}", item.getProductId(), party.getId(), e.getMessage());
                    }
                }
            }
        }

        // Adjust party payment balance if paidAmount > 0
        syncPartyPayment(savedPo);

        // Stock adjustment if created directly in RECEIVED status
        if (savedPo.getStatus() == PurchaseOrderStatus.RECEIVED) {
            handleStockAdjustment(savedPo, null, PurchaseOrderStatus.RECEIVED);
        }

        // Publish event for restock advisory listener
        publishPoUpdatedEvent(savedPo, null, savedPo.getStatus());

        log.info("Committed independent supplier purchase order {} (PO #{}) for party '{}' in firm {}",
                savedPo.getId(), savedPo.getPoNumber(), party.getName(), firmId);

        return savedPo;
    }

    private String generateNextPoNumber(Long firmId) {
        if (sequenceService != null) {
            try {
                return sequenceService.generateNextNumber(DocumentType.PURCHASE_ORDER, firmId);
            } catch (Exception e) {
                log.warn("SequenceService failed for PURCHASE_ORDER in firm {}, falling back: {}", firmId, e.getMessage());
            }
        }
        long count = poRepository.countByFirmId(firmId) + 1;
        String year = String.valueOf(LocalDate.now().getYear());
        return String.format("PO-%s-%04d", year, count);
    }

    private void syncPartyPayment(PurchaseOrder po) {
        if (po == null || po.getId() == null || po.getParty() == null) return;
        if (po.getPaidAmount() != null && po.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            List<PartyPayment> existingPayments = partyPaymentRepository.findByPurchaseOrderId(po.getId());
            PartyPayment payment = existingPayments.isEmpty() ? new PartyPayment() : existingPayments.get(0);
            payment.setPartyId(po.getParty().getId());
            payment.setFirmId(po.getFirmId());
            payment.setPurchaseOrderId(po.getId());
            payment.setAmount(po.getPaidAmount());
            payment.setPaymentDate(po.getPoDate() != null ? po.getPoDate() : LocalDate.now());
            payment.setPaymentMode(po.getPaymentMethod() != null ? po.getPaymentMethod() : "Bank Transfer");
            payment.setReferenceNumber("PO-PAY-" + po.getPoNumber());
            payment.setNotes("Payment recorded during PO creation #" + po.getPoNumber());
            partyPaymentRepository.save(payment);
        }
    }

    private void handleStockAdjustment(PurchaseOrder po, PurchaseOrderStatus oldStatus, PurchaseOrderStatus newStatus) {
        if (productService == null || po.getItems() == null || po.getItems().isEmpty()) {
            return;
        }
        for (PurchaseOrderItem item : po.getItems()) {
            if (item.getProductId() != null && item.getQuantity() != null && item.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
                productService.recordStockMovement(
                        item.getProductId(),
                        po.getFirmId(),
                        "PURCHASE_RECEIPT",
                        item.getQuantity(),
                        "PURCHASE_ORDER",
                        po.getPoNumber(),
                        "Stock received from Purchase Order #" + po.getPoNumber()
                );
            }
        }
    }

    private void publishPoUpdatedEvent(PurchaseOrder po, PurchaseOrderStatus oldStatus, PurchaseOrderStatus newStatus) {
        if (eventPublisher == null || po == null) return;
        Set<Long> productIds = (po.getItems() != null)
                ? po.getItems().stream().map(PurchaseOrderItem::getProductId).filter(Objects::nonNull).collect(Collectors.toSet())
                : Collections.emptySet();
        eventPublisher.publishEvent(new PurchaseOrderUpdatedEvent(po.getId(), po.getFirmId(), newStatus, oldStatus, productIds));
    }
}
