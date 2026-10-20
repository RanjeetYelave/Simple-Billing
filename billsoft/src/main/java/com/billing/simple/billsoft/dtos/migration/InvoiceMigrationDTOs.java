package com.billing.simple.billsoft.dtos.migration;

import com.billing.simple.billsoft.entities.Invoice;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class InvoiceMigrationDTOs {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CustomerCandidateDTO {
        private Long customerId;
        private String name;
        private String phone;
        private String email;
        private String address;
        private String gstin;
        private String matchReason; // e.g. "GSTIN", "PHONE", "NAME"
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CustomerResolutionDTO {
        // EXACT_MATCH, AMBIGUOUS, NO_MATCH
        private String matchType;
        private CustomerCandidateDTO matchedCustomer;
        @Builder.Default
        private List<CustomerCandidateDTO> candidates = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProductCandidateDTO {
        private Long productId;
        private String name;
        private String sku;
        private BigDecimal currentStock;
        private BigDecimal price;
        private String itemType;
        private String matchReason;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProductResolutionDTO {
        private Long itemId;
        private String originalProductName;
        private Integer qty;
        private BigDecimal pricePerUnit;
        private BigDecimal lineTotal;
        // INVENTORY_PRODUCT, SAVED_ITEM, AMBIGUOUS
        private String resolutionType;
        private ProductCandidateDTO matchedProduct;
        @Builder.Default
        private List<ProductCandidateDTO> candidates = new ArrayList<>();
        private BigDecimal stockDeduction;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InvoiceMigrationPreviewResponse {
        private Long invoiceId;
        private Long sourceFirmId;
        private String sourceFirmName;
        private Long targetFirmId;
        private String targetFirmName;
        private String currentInvoiceNumber;
        private String estimatedTargetInvoiceNumber;
        private boolean eligible;
        private String ineligibilityReason;

        private CustomerResolutionDTO customerResolution;
        @Builder.Default
        private List<ProductResolutionDTO> productResolutions = new ArrayList<>();

        private BigDecimal sourceStockRestoration;
        private BigDecimal targetStockDeduction;
        private boolean forecastAffected;
        private boolean hasPayments;
        private boolean hasReturns;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProductResolutionSelection {
        private Long itemId;
        private Long targetProductId;
        private Boolean asSavedItem;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InvoiceMigrationRequest {
        private Long targetFirmId;
        private Long resolvedCustomerId;
        @Builder.Default
        private Boolean createCustomerIfMissing = true;
        @Builder.Default
        private List<ProductResolutionSelection> productSelections = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InvoiceMigrationResponse {
        private Long invoiceId;
        private Long migrationRecordId;
        private Long sourceFirmId;
        private String sourceFirmName;
        private Long targetFirmId;
        private String targetFirmName;
        private String sourceInvoiceNumber;
        private String targetInvoiceNumber;
        private Boolean isRevert;
        private String status;
        private LocalDateTime migratedAt;
        private Invoice migratedInvoice;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InvoiceMigrationRecordDTO {
        private Long id;
        private Long invoiceId;
        private Long sourceFirmId;
        private String sourceFirmName;
        private Long targetFirmId;
        private String targetFirmName;
        private String sourceInvoiceNumber;
        private String targetInvoiceNumber;
        private Boolean isRevert;
        private Long parentMigrationId;
        private String status;
        private String migratedBy;
        private LocalDateTime createdAt;
    }
}
