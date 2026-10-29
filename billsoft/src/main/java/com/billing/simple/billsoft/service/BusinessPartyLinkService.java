package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dtos.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface BusinessPartyLinkService {

    BusinessPartyLinkResponse link(BusinessPartyLinkRequest request);

    void unlink(Long customerId, Long partyId);

    void unlink(Long linkId);

    BusinessPartyLinkResponse getLinkedPartyForCustomer(Long customerId);

    BusinessPartyLinkResponse getLinkedCustomerForParty(Long partyId);

    List<PartyLinkCandidateDto> searchCandidates(String type, String query);

    Map<String, Object> getFirmLinksMap(Long firmId);

    UnifiedCounterpartyStatementResponse getUnifiedStatement(Long customerId, Long partyId, LocalDate from, LocalDate to);
}
