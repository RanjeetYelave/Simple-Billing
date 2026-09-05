package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dtos.PartyFinancialSummary;
import com.billing.simple.billsoft.entities.Party;
import com.billing.simple.billsoft.entities.PartyPayment;
import com.billing.simple.billsoft.entities.PurchaseOrder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PartyService {

    Party createParty(Party party);

    Party updateParty(Long id, Party updated);

    List<Party> getPartiesByFirm(Long firmId);

    Optional<Party> getPartyById(Long id, Long firmId);

    void deleteParty(Long id, Long firmId);

    PartyFinancialSummary getFinancialSummary(Long partyId, Long firmId);

    List<PartyFinancialSummary> getAllPartiesWithFinancialSummaries(Long firmId);

    PartyPayment recordPayment(PartyPayment payment);

    List<PartyPayment> getPaymentsByParty(Long partyId, Long firmId);

    List<PartyPayment> getUnallocatedPayments(Long partyId, Long firmId);

    PurchaseOrder adjustAdvancePayment(Long poId, Long paymentId, BigDecimal amount, String notes, Long firmId);

    PurchaseOrder unadjustPayment(Long paymentId, Long firmId);

    void deletePayment(Long paymentId, Long firmId);
}
