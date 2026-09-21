package com.billing.simple.billsoft.dtos;

import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DuplicateCandidateDto {
    private String candidateKey; // e.g. "PRODUCT:101_PRODUCT:108"
    private ItemSummaryDto candidateA;
    private ItemSummaryDto candidateB;
    private int similarityScore; // 0 to 100
    private boolean isSafe; // true if no business-critical conflicts
    
    @Builder.Default
    private List<String> reasons = new ArrayList<>();
    
    @Builder.Default
    private List<String> conflicts = new ArrayList<>();
    
    @Builder.Default
    private List<String> warnings = new ArrayList<>();
}
