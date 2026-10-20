package com.billing.simple.billsoft.dtos.migration;

import com.billing.simple.billsoft.entities.InvoiceStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceSnapshotDTO {

    @Builder.Default
    private int snapshotVersion = 1;

    private LocalDateTime snapshotTimestamp;
    private Long sourceFirmId;
    private Long invoiceId;
    private String invoiceNumber;
    private String estimateNumber;
    private InvoiceStatus status;
    private LocalDateTime invoiceDate;
    private LocalDate dueDate;
    private Long convertedInvoiceId;
    private Boolean paid;

    private String customerNote;
    private String termsAndConditions;
    private String paymentMethod;
    private String currency;
    private BigDecimal roundOff;
    private String tags;

    private String invoiceDiscountType;
    private BigDecimal invoiceDiscountValue;
    private BigDecimal subtotalWithoutTax;
    private BigDecimal totalTax;
    private BigDecimal totalDiscount;
    private BigDecimal totalAmount;

    private CustomerSnapshotDTO customer;

    @Builder.Default
    private List<InvoiceItemSnapshotDTO> items = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CustomerSnapshotDTO {
        private Long customerId;
        private String name;
        private String phone;
        private String email;
        private String address;
        private String gstin;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InvoiceItemSnapshotDTO {
        private Long itemId;
        private Long productId;
        private String productName;
        private Integer qty;
        private String unit;
        private String hsnCode;
        private BigDecimal pricePerUnit;
        private BigDecimal amountWithoutTax;
        private String discountType;
        private BigDecimal discountValue;
        private BigDecimal discountPercent;
        private BigDecimal taxableAmount;
        private BigDecimal gstPercent;
        private BigDecimal gstAmount;
        private BigDecimal lineTotal;
        private String itemType;
        private BigDecimal physicalStockDeducted;
    }
}
