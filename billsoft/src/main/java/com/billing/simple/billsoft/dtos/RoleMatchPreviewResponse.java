package com.billing.simple.billsoft.dtos;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleMatchPreviewResponse {
    private String targetRole; // "CUSTOMER" or "PARTY"
    private String resolution; // "REUSE_EXISTING", "CREATE_NEW", "ALREADY_LINKED", "AMBIGUOUS_MATCH", "DIFFERENT_GSTIN"
    private String matchedBy;  // "LINKED_RECORD", "GSTIN", "PHONE", "NAME", "NONE"
    private Long existingEntityId;
    private String existingEntityName;
    private String existingEntityPhone;
    private String existingEntityGstin;
    private String existingEntityEmail;
    private String existingEntityAddress;
    private String message;
    private java.util.List<PartyLinkCandidateDto> candidates;
    private Boolean gstinMismatch;
}

