package com.billing.simple.billsoft.dtos;

import com.billing.simple.billsoft.entities.Product;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductImportResponseDto {
    private int totalRows;
    private int importedCount;
    @Builder.Default
    private List<Product> products = new ArrayList<>();
    @Builder.Default
    private List<String> warnings = new ArrayList<>();
    @Builder.Default
    private List<String> errors = new ArrayList<>();
}
