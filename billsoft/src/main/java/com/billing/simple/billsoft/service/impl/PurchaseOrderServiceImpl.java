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

@Service
@Transactional
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

    private final PurchaseOrderRepository poRepository;
    private final PurchaseOrderItemRepository poItemRepository;
    private final PartyRepository partyRepository;
    private final PartyPaymentRepository partyPaymentRepository;
    private final PurchaseOrderPdfService pdfService;
    private final com.billing.simple.billsoft.service.ProductService productService;

    public PurchaseOrderServiceImpl(PurchaseOrderRepository poRepository,
                                    PurchaseOrderItemRepository poItemRepository,
                                    PartyRepository partyRepository,
                                    PartyPaymentRepository partyPaymentRepository,
                                    PurchaseOrderPdfService pdfService,
                                    com.billing.simple.billsoft.service.ProductService productService) {
        this.poRepository = poRepository;
        this.poItemRepository = poItemRepository;
        this.partyRepository = partyRepository;
        this.partyPaymentRepository = partyPaymentRepository;
        this.pdfService = pdfService;
        this.productService = productService;
    }

    @Override
    public PurchaseOrder createPurchaseOrder(PurchaseOrder po) {
        if (po.getFirmId() == null) {
            throw new IllegalArgumentException("Firm ID is required");
        }
        if (po.getParty() == null || po.getParty().getId() == null) {
            throw new IllegalArgumentException("Party is required");
        }

        Party party = partyRepository.findByIdAndFirmId(po.getParty().getId(), po.getFirmId())
                .orElseThrow(() -> new IllegalArgumentException("Party not found with id: " + po.getParty().getId()));

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

        return savedPo;
    }

    @Override
    public PurchaseOrder updatePurchaseOrder(Long id, PurchaseOrder updated) {
        PurchaseOrder existing = poRepository.findByIdAndFirmId(id, updated.getFirmId())
                .orElseThrow(() -> new IllegalArgumentException("Purchase Order not found with id: " + id));

        if (updated.getParty() != null && updated.getParty().getId() != null) {
            Party party = partyRepository.findByIdAndFirmId(updated.getParty().getId(), updated.getFirmId())
                    .orElseThrow(() -> new IllegalArgumentException("Party not found with id: " + updated.getParty().getId()));
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

        // If transitioning to RECEIVED from another status, increase inventory stock
        if (status == PurchaseOrderStatus.RECEIVED && oldStatus != PurchaseOrderStatus.RECEIVED) {
            if (saved.getItems() != null) {
                for (PurchaseOrderItem item : saved.getItems()) {
                    if (item.getProductId() != null && item.getQuantity() != null && item.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
                        productService.recordStockMovement(
                                item.getProductId(),
                                saved.getFirmId(),
                                "PURCHASE_RECEIPT",
                                item.getQuantity(),
                                "PURCHASE_ORDER",
                                saved.getPoNumber(),
                                "Vendor goods received from PO " + saved.getPoNumber() + " (" + (saved.getParty() != null ? saved.getParty().getName() : "Vendor") + ")"
                        );
                    }
                }
            }
        }
        return saved;
    }

    @Override
    public void deletePurchaseOrder(Long id, Long firmId) {
        PurchaseOrder po = (firmId != null ? poRepository.findByIdAndFirmId(id, firmId) : poRepository.findById(id))
                .orElseThrow(() -> new IllegalArgumentException("Purchase Order not found with id: " + id));
        partyPaymentRepository.deleteByPurchaseOrderId(po.getId());
        poRepository.delete(po);
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
}
