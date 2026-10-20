package com.billing.simple.billsoft.dtos;

import com.billing.simple.billsoft.entities.Product;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductBulkUpdateResponseDto {
    private int updatedCount;
    private List<Product> products;
    private String message;
}
