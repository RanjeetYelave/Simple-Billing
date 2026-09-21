package com.billing.simple.billsoft.dtos;

import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchMergeRequestDto {
    private List<MergeRequestDto> merges;
}
