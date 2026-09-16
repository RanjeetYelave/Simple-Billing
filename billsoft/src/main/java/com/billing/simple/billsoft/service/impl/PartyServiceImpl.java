package com.billing.simple.billsoft.service.impl;

import com.billing.simple.billsoft.dtos.PartyFinancialSummary;
import com.billing.simple.billsoft.entities.Party;
import com.billing.simple.billsoft.entities.PartyPayment;
import com.billing.simple.billsoft.entities.PurchaseOrder;
import com.billing.simple.billsoft.entities.PurchaseOrderStatus;
import com.billing.simple.billsoft.repositories.PartyPaymentRepository;
import com.billing.simple.billsoft.repositories.PartyRepository;
import com.billing.simple.billsoft.repositories.PurchaseOrderRepository;
import com.billing.simple.billsoft.service.PartyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class PartyServiceImpl implements PartyService {

    private final PartyRepository partyRepository;
    private final PartyPaymentRepository partyPaymentRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;

    public PartyServiceImpl(PartyRepository partyRepository,
                            PartyPaymentRepository partyPaymentRepository,
                            PurchaseOrderRepository purchaseOrderRepository) {
        this.partyRepository = partyRepository;
        this.partyPaymentRepository = partyPaymentRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
    }

    @Override
    public Party createParty(Party party) {
        if (party.getName() == null || party.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Party name is required");
        }
        if (party.getOpeningBalance() == null) {
            party.setOpeningBalance(BigDecimal.ZERO);
        }
        if (party.getOpeningBalanceType() == null || party.getOpeningBalanceType().trim().isEmpty()) {
            party.setOpeningBalanceType("PAYABLE");
        }
        return partyRepository.save(party);
    }

    @Override
    public Party updateParty(Long id, Party updated) {
        Party existing = partyRepository.findByIdAndFirmId(id, updated.getFirmId())
                .orElseThrow(() -> new IllegalArgumentException("Party not found with id: " + id));

        existing.setName(updated.getName());
        existing.setContactPerson(updated.getContactPerson());
        existing.setPhone(updated.getPhone());
        existing.setEmail(updated.getEmail());
        existing.setAddress(updated.getAddress());
        existing.setCity(updated.getCity());
        existing.setState(updated.getState());
        existing.setPincode(updated.getPincode());
        existing.setGstin(updated.getGstin());
        existing.setPan(updated.getPan());
        existing.setBankName(updated.getBankName());
        existing.setBankAccount(updated.getBankAccount());
        existing.setBankIfsc(updated.getBankIfsc());
        existing.setUpiId(updated.getUpiId());
        if (updated.getOpeningBalance() != null) {
            existing.setOpeningBalance(updated.getOpeningBalance());
        }
        if (updated.getOpeningBalanceType() != null) {
            existing.setOpeningBalanceType(updated.getOpeningBalanceType());
        }
        existing.setNotes(updated.getNotes());

        return partyRepository.save(existing);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Party> getPartiesByFirm(Long firmId) {
        return partyRepository.findByFirmIdOrderByNameAsc(firmId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Party> getPartyById(Long id, Long firmId) {
        return partyRepository.findByIdAndFirmId(id, firmId);
    }

    @Override
    public void deleteParty(Long id, Long firmId) {
        Party party = partyRepository.findByIdAndFirmId(id, firmId)
                .orElseThrow(() -> new IllegalArgumentException("Party not found with id: " + id));

        // Delete associated payments
        partyPaymentRepository.deleteByPartyId(party.getId());
        partyRepository.delete(party);
    }

    @Override
    @Transactional(readOnly = true)
    public PartyFinancialSummary getFinancialSummary(Long partyId, Long firmId) {
        Party party = partyRepository.findByIdAndFirmId(partyId, firmId)
                .orElseThrow(() -> new IllegalArgumentException("Party not found with id: " + partyId));

        return computeSummary(party, firmId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PartyFinancialSummary> getAllPartiesWithFinancialSummaries(Long firmId) {
        List<Party> parties = partyRepository.findByFirmIdOrderByNameAsc(firmId);
        return computeSummariesBatch(parties, firmId);
    }

    @Override
    @Transactional(readOnly = true)
    public com.billing.simple.billsoft.dtos.PageResponse<PartyFinancialSummary> getPaginatedPartiesWithFinancialSummaries(Long firmId, org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Page<Party> partyPage = partyRepository.findByFirmId(firmId, pageable);
        List<PartyFinancialSummary> summaries = computeSummariesBatch(partyPage.getContent(), firmId);
        return com.billing.simple.billsoft.dtos.PageResponse.of(partyPage, summaries);
    }

    private List<PartyFinancialSummary> computeSummariesBatch(List<Party> parties, Long firmId) {
        if (parties == null || parties.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<Long> partyIds = parties.stream().map(Party::getId).filter(java.util.Objects::nonNull).toList();
        if (partyIds.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        // 1. Batch PO totals: [partyId, totalPurchases, totalPOCount]
        java.util.Map<Long, BigDecimal> poTotals = new java.util.HashMap<>();
        java.util.Map<Long, Long> poCounts = new java.util.HashMap<>();
        List<Object[]> poAggs = purchaseOrderRepository.sumTotalsByPartyIds(firmId, partyIds);
        if (poAggs != null) {
            for (Object[] row : poAggs) {
                Long pId = ((Number) row[0]).longValue();
                BigDecimal sum = row[1] != null ? (BigDecimal) row[1] : BigDecimal.ZERO;
                Long count = row[2] != null ? ((Number) row[2]).longValue() : 0L;
                poTotals.put(pId, sum);
                poCounts.put(pId, count);
            }
        }

        // 2. Batch Pending PO counts: [partyId, pendingPOCount]
        java.util.Map<Long, Long> pendingCounts = new java.util.HashMap<>();
        List<Object[]> pendingAggs = purchaseOrderRepository.countPendingByPartyIds(firmId, partyIds);
        if (pendingAggs != null) {
            for (Object[] row : pendingAggs) {
                Long pId = ((Number) row[0]).longValue();
                Long count = row[1] != null ? ((Number) row[1]).longValue() : 0L;
                pendingCounts.put(pId, count);
            }
        }

        // 3. Batch Payment totals: [partyId, totalPaid]
        java.util.Map<Long, BigDecimal> paymentTotals = new java.util.HashMap<>();
        List<Object[]> payAggs = partyPaymentRepository.sumPaidByPartyIds(firmId, partyIds);
        if (payAggs != null) {
            for (Object[] row : payAggs) {
                Long pId = ((Number) row[0]).longValue();
                BigDecimal sum = row[1] != null ? (BigDecimal) row[1] : BigDecimal.ZERO;
                paymentTotals.put(pId, sum);
            }
        }

        // Build summaries in O(N) memory
        List<PartyFinancialSummary> summaries = new java.util.ArrayList<>(parties.size());
        for (Party party : parties) {
            BigDecimal opening = party.getOpeningBalance() != null ? party.getOpeningBalance() : BigDecimal.ZERO;
            String type = party.getOpeningBalanceType() != null ? party.getOpeningBalanceType().toUpperCase() : "PAYABLE";
            BigDecimal net = "ADVANCE".equals(type) ? opening.negate() : opening;

            BigDecimal totalPurchases = poTotals.getOrDefault(party.getId(), BigDecimal.ZERO);
            long totalPOCount = poCounts.getOrDefault(party.getId(), 0L);
            long pendingPOCount = pendingCounts.getOrDefault(party.getId(), 0L);
            BigDecimal totalPaid = paymentTotals.getOrDefault(party.getId(), BigDecimal.ZERO);

            BigDecimal finalNet = net.add(totalPurchases).subtract(totalPaid);
            String status;
            if (finalNet.compareTo(BigDecimal.ZERO) > 0) {
                status = "PAYABLE";
            } else if (finalNet.compareTo(BigDecimal.ZERO) < 0) {
                status = "ADVANCE";
            } else {
                status = "SETTLED";
            }

            summaries.add(PartyFinancialSummary.builder()
                    .partyId(party.getId())
                    .partyName(party.getName())
                    .phone(party.getPhone())
                    .gstin(party.getGstin())
                    .openingBalance(opening)
                    .openingBalanceType(type)
                    .totalPurchases(totalPurchases)
                    .totalPaid(totalPaid)
                    .netBalance(finalNet)
                    .balanceStatus(status)
                    .totalPurchaseOrders(totalPOCount)
                    .pendingPurchaseOrders(pendingPOCount)
                    .build());
        }
        return summaries;
    }

    private PartyFinancialSummary computeSummary(Party party, Long firmId) {
        return computeSummariesBatch(java.util.Collections.singletonList(party), firmId).stream().findFirst().orElse(null);
    }

    @Override
    public PartyPayment recordPayment(PartyPayment payment) {
        if (payment.getAmount() == null || payment.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }
        if (payment.getPartyId() == null) {
            throw new IllegalArgumentException("Party ID is required");
        }
        if (payment.getPaymentDate() == null) {
            payment.setPaymentDate(java.time.LocalDate.now());
        }
        PartyPayment saved = partyPaymentRepository.save(payment);

        // If payment is linked to a Purchase Order, auto-update the PO's paidAmount & paymentStatus
        if (saved.getPurchaseOrderId() != null && saved.getFirmId() != null) {
            purchaseOrderRepository.findByIdAndFirmId(saved.getPurchaseOrderId(), saved.getFirmId()).ifPresent(po -> {
                List<PartyPayment> poPayments = partyPaymentRepository.findByPurchaseOrderId(po.getId());
                BigDecimal totalPaid = poPayments.stream()
                        .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                po.setPaidAmount(totalPaid);
                BigDecimal poTotal = po.getTotalAmount() != null ? po.getTotalAmount() : BigDecimal.ZERO;
                if (poTotal.compareTo(BigDecimal.ZERO) > 0 && totalPaid.compareTo(poTotal) >= 0) {
                    po.setPaymentStatus("PAID");
                } else if (totalPaid.compareTo(BigDecimal.ZERO) > 0) {
                    po.setPaymentStatus("PARTIAL");
                } else {
                    po.setPaymentStatus("YET_TO_PAY");
                }
                if (saved.getPaymentMode() != null) {
                    po.setPaymentMethod(saved.getPaymentMode());
                }
                purchaseOrderRepository.save(po);
            });
        }

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PartyPayment> getPaymentsByParty(Long partyId, Long firmId) {
        return partyPaymentRepository.findByFirmIdAndPartyIdOrderByPaymentDateDescIdDesc(firmId, partyId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PartyPayment> getUnallocatedPayments(Long partyId, Long firmId) {
        if (partyId != null) {
            return partyPaymentRepository.findByFirmIdAndPartyIdAndPurchaseOrderIdIsNullOrderByPaymentDateDescIdDesc(firmId, partyId);
        }
        return partyPaymentRepository.findByFirmIdAndPurchaseOrderIdIsNullOrderByPaymentDateDescIdDesc(firmId);
    }

    @Override
    public PurchaseOrder adjustAdvancePayment(Long poId, Long paymentId, BigDecimal amount, String notes, Long firmId) {
        if (poId == null || paymentId == null || firmId == null) {
            throw new IllegalArgumentException("Purchase Order ID, Payment ID, and Firm ID are required");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Adjustment amount must be greater than zero");
        }

        PurchaseOrder po = purchaseOrderRepository.findByIdAndFirmId(poId, firmId)
                .orElseThrow(() -> new IllegalArgumentException("Purchase Order not found with id: " + poId));

        PartyPayment payment = partyPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Party payment not found with id: " + paymentId));

        if (!payment.getFirmId().equals(firmId)) {
            throw new IllegalArgumentException("Unauthorized: Payment firm mismatch");
        }

        if (po.getParty() == null || !po.getParty().getId().equals(payment.getPartyId())) {
            throw new IllegalArgumentException("Payment does not belong to the vendor of this Purchase Order");
        }

        if (payment.getPurchaseOrderId() != null) {
            throw new IllegalArgumentException("Payment is already linked to Purchase Order #" + payment.getPurchaseOrderId());
        }

        if (amount.compareTo(payment.getAmount()) > 0) {
            throw new IllegalArgumentException("Adjustment amount (" + amount + ") cannot exceed available advance (" + payment.getAmount() + ")");
        }

        if (amount.compareTo(payment.getAmount()) == 0) {
            // Full allocation of this advance payment
            payment.setPurchaseOrderId(po.getId());
            if (notes != null && !notes.isBlank()) {
                payment.setNotes(notes.trim());
            } else if (payment.getNotes() == null || payment.getNotes().isBlank()) {
                payment.setNotes("Adjusted from advance for PO " + po.getPoNumber());
            }
            partyPaymentRepository.save(payment);
        } else {
            // Partial allocation: reduce advance payment amount and create allocated split entry
            BigDecimal remainingAdvance = payment.getAmount().subtract(amount);
            payment.setAmount(remainingAdvance);
            partyPaymentRepository.save(payment);

            PartyPayment allocated = PartyPayment.builder()
                    .firmId(firmId)
                    .partyId(po.getParty().getId())
                    .purchaseOrderId(po.getId())
                    .amount(amount)
                    .paymentDate(payment.getPaymentDate() != null ? payment.getPaymentDate() : java.time.LocalDate.now())
                    .paymentMode(payment.getPaymentMode() != null ? payment.getPaymentMode() : "BANK_TRANSFER")
                    .referenceNumber(payment.getReferenceNumber() != null ? payment.getReferenceNumber() : ("ADV-ADJ-" + payment.getId()))
                    .notes(notes != null && !notes.isBlank() ? notes.trim() : ("Adjusted from Advance Payment #" + payment.getId() + " for PO " + po.getPoNumber()))
                    .build();
            partyPaymentRepository.save(allocated);
        }

        // Recalculate PO paidAmount and status
        List<PartyPayment> poPayments = partyPaymentRepository.findByPurchaseOrderId(po.getId());
        BigDecimal totalPaid = poPayments.stream()
                .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        po.setPaidAmount(totalPaid);
        BigDecimal poTotal = po.getTotalAmount() != null ? po.getTotalAmount() : BigDecimal.ZERO;
        if (poTotal.compareTo(BigDecimal.ZERO) > 0 && totalPaid.compareTo(poTotal) >= 0) {
            po.setPaymentStatus("PAID");
        } else if (totalPaid.compareTo(BigDecimal.ZERO) > 0) {
            po.setPaymentStatus("PARTIAL");
        } else {
            po.setPaymentStatus("YET_TO_PAY");
        }

        return purchaseOrderRepository.save(po);
    }

    @Override
    public PurchaseOrder unadjustPayment(Long paymentId, Long firmId) {
        if (paymentId == null || firmId == null) {
            throw new IllegalArgumentException("Payment ID and Firm ID are required");
        }

        PartyPayment payment = partyPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found with id: " + paymentId));

        if (!payment.getFirmId().equals(firmId)) {
            throw new IllegalArgumentException("Unauthorized: Payment firm mismatch");
        }

        if (payment.getPurchaseOrderId() == null) {
            throw new IllegalArgumentException("Payment is already unallocated (advance)");
        }

        Long poId = payment.getPurchaseOrderId();
        payment.setPurchaseOrderId(null);
        partyPaymentRepository.save(payment);

        PurchaseOrder po = purchaseOrderRepository.findByIdAndFirmId(poId, firmId)
                .orElseThrow(() -> new IllegalArgumentException("Purchase Order not found with id: " + poId));

        List<PartyPayment> poPayments = partyPaymentRepository.findByPurchaseOrderId(po.getId());
        BigDecimal totalPaid = poPayments.stream()
                .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        po.setPaidAmount(totalPaid);
        BigDecimal poTotal = po.getTotalAmount() != null ? po.getTotalAmount() : BigDecimal.ZERO;
        if (poTotal.compareTo(BigDecimal.ZERO) > 0 && totalPaid.compareTo(poTotal) >= 0) {
            po.setPaymentStatus("PAID");
        } else if (totalPaid.compareTo(BigDecimal.ZERO) > 0) {
            po.setPaymentStatus("PARTIAL");
        } else {
            po.setPaymentStatus("YET_TO_PAY");
        }

        return purchaseOrderRepository.save(po);
    }

    @Override
    public void deletePayment(Long paymentId, Long firmId) {
        PartyPayment payment = partyPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found with id: " + paymentId));
        if (!payment.getFirmId().equals(firmId)) {
            throw new IllegalArgumentException("Unauthorized to delete payment");
        }
        Long poId = payment.getPurchaseOrderId();
        partyPaymentRepository.delete(payment);

        // If payment was linked to a PO, sync the PO's remaining paid balance and status
        if (poId != null) {
            purchaseOrderRepository.findByIdAndFirmId(poId, firmId).ifPresent(po -> {
                List<PartyPayment> poPayments = partyPaymentRepository.findByPurchaseOrderId(po.getId());
                BigDecimal totalPaid = poPayments.stream()
                        .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                po.setPaidAmount(totalPaid);
                BigDecimal poTotal = po.getTotalAmount() != null ? po.getTotalAmount() : BigDecimal.ZERO;
                if (poTotal.compareTo(BigDecimal.ZERO) > 0 && totalPaid.compareTo(poTotal) >= 0) {
                    po.setPaymentStatus("PAID");
                } else if (totalPaid.compareTo(BigDecimal.ZERO) > 0) {
                    po.setPaymentStatus("PARTIAL");
                } else {
                    po.setPaymentStatus("YET_TO_PAY");
                }
                purchaseOrderRepository.save(po);
            });
        }
    }
}
