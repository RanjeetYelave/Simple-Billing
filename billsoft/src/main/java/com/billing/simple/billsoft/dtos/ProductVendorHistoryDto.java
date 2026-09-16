package com.billing.simple.billsoft.dtos;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVendorHistoryDto {

    private Long productId;
    private Long latestPartyId;
    private String latestPartyName;
    private String latestPoNumber;
    private LocalDate latestPoDate;
    private BigDecimal totalQuantityOrdered;
}
