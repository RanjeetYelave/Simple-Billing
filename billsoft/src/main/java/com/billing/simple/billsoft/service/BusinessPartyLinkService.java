package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dtos.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface BusinessPartyLinkService {

    BusinessPartyLinkResponse link(BusinessPartyLinkRequest request);

    BusinessPartyLinkResponse enableCustomerRole(EnableCustomerRoleRequest request);

    BusinessPartyLinkResponse enablePartyRole(EnablePartyRoleRequest request);

    RoleMatchPreviewResponse previewEnableCustomerRole(Long partyId, String gstin, String phone);

    RoleMatchPreviewResponse previewEnablePartyRole(Long customerId, String gstin, String phone);

    BusinessPartyLinkResponse disableRole(Long linkId, RoleLifecycleRequest request);

    BusinessPartyLinkResponse reenableRole(Long linkId, RoleLifecycleRequest request);

    BusinessPartyLinkResponse syncProfiles(Long linkId, SyncProfileRequest request);

    BusinessPartyLinkResponse updateCanonicalName(Long linkId, CanonicalNameUpdateRequest request);

    BusinessPartyLinkResponse softUnlink(Long linkId, UnlinkRequest request);

    void unlink(Long customerId, Long partyId);

    void unlink(Long linkId);

    BusinessPartyLinkResponse getLinkedPartyForCustomer(Long customerId);

    BusinessPartyLinkResponse getLinkedCustomerForParty(Long partyId);

    List<PartyLinkCandidateDto> searchCandidates(String type, String query);

    List<UnifiedCounterpartySearchResult> searchUnified(String targetRole, String query, Integer limit);

    AliasDto addAlias(Long linkId, CreateAliasRequest request);

    void removeAlias(Long linkId, Long aliasId, String reason, String actor);

    List<AliasDto> getAliases(Long linkId);

    List<AuditTrailEventDto> getAuditEvents(Long linkId);

    boolean isAdminPinConfigured(Long firmId);

    void setAdminPin(Long firmId, AdminPinSetRequest request);

    Map<String, Object> getFirmLinksMap(Long firmId);

    UnifiedCounterpartyStatementResponse getUnifiedStatement(Long customerId, Long partyId, LocalDate from, LocalDate to);
}
