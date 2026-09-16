package com.billing.simple.billsoft.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesReturnRequest {

    private Long firmId;
    private Long invoiceId;
    private String returnNumber;
    private LocalDate returnDate;
    private String reason;
    private String refundMode; // "CASH", "BANK_TRANSFER", "UPI", "CUSTOMER_CREDIT"
    private String notes;
    private Boolean excludeTax;
    private BigDecimal penaltyAmount;
    private String penaltyReason;
    private List<SalesReturnItemRequest> items;

    public Long getFirmId() { return firmId; }
    public void setFirmId(Long firmId) { this.firmId = firmId; }

    public Long getInvoiceId() { return invoiceId; }
    public void setInvoiceId(Long invoiceId) { this.invoiceId = invoiceId; }

    public String getReturnNumber() { return returnNumber; }
    public void setReturnNumber(String returnNumber) { this.returnNumber = returnNumber; }

    public LocalDate getReturnDate() { return returnDate; }
    public void setReturnDate(LocalDate returnDate) { this.returnDate = returnDate; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getRefundMode() { return refundMode; }
    public void setRefundMode(String refundMode) { this.refundMode = refundMode; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Boolean getExcludeTax() { return excludeTax; }
    public void setExcludeTax(Boolean excludeTax) { this.excludeTax = excludeTax; }

    public BigDecimal getPenaltyAmount() { return penaltyAmount; }
    public void setPenaltyAmount(BigDecimal penaltyAmount) { this.penaltyAmount = penaltyAmount; }

    public String getPenaltyReason() { return penaltyReason; }
    public void setPenaltyReason(String penaltyReason) { this.penaltyReason = penaltyReason; }

    public List<SalesReturnItemRequest> getItems() { return items; }
    public void setItems(List<SalesReturnItemRequest> items) { this.items = items; }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SalesReturnItemRequest {
        private Long invoiceItemId;
        private Long productId;
        private String productName;
        private Integer returnQty;
        private BigDecimal unitPrice;
        private BigDecimal discountValue;
        private BigDecimal gstPercent;

        public Long getInvoiceItemId() { return invoiceItemId; }
        public void setInvoiceItemId(Long invoiceItemId) { this.invoiceItemId = invoiceItemId; }

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }

        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }

        public Integer getReturnQty() { return returnQty; }
        public void setReturnQty(Integer returnQty) { this.returnQty = returnQty; }

        public BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

        public BigDecimal getDiscountValue() { return discountValue; }
        public void setDiscountValue(BigDecimal discountValue) { this.discountValue = discountValue; }

        public BigDecimal getGstPercent() { return gstPercent; }
        public void setGstPercent(BigDecimal gstPercent) { this.gstPercent = gstPercent; }
    }
}

