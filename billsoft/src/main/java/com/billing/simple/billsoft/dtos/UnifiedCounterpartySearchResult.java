package com.billing.simple.billsoft.dtos;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnifiedCounterpartySearchResult {
    private String resultType; // EXISTING_CUSTOMER, EXISTING_PARTY, PARTY_ONLY, CUSTOMER_ONLY, LINKED_BUSINESS
    private Long customerId;
    private Long partyId;
    private Long linkId;
    private String canonicalName;
    private String matchedAlias;
    private String gstin;
    private String phone;
    private String email;
    private String address;
    private List<String> availableRoles; // ["CUSTOMER"], ["PARTY"], or ["CUSTOMER", "PARTY"]
    private String linkStatus; // "ACTIVE", "CUSTOMER_DISABLED", "PARTY_DISABLED", "BOTH_DISABLED", "NONE"
    private String matchReason; // "EXACT_NAME", "PHONE", "GSTIN", "ALIAS", "PARTIAL_NAME"
    private String availableAction; // "SELECT_CUSTOMER", "SELECT_PARTY", "ACTIVATE_CUSTOMER_ROLE", "ACTIVATE_PARTY_ROLE"
}

