package com.billing.simple.billsoft.exception;

import java.math.BigDecimal;

/**
 * Thrown when an inventory deduction exceeds available stock and negative inventory is not allowed.
 */
public class InsufficientStockException extends RuntimeException {

    private final Long productId;
    private final String productName;
    private final BigDecimal availableStock;
    private final BigDecimal requestedQuantity;

    public InsufficientStockException(String message) {
        super(message);
        this.productId = null;
        this.productName = null;
        this.availableStock = null;
        this.requestedQuantity = null;
    }

    public InsufficientStockException(String message, Long productId, String productName,
                                     BigDecimal availableStock, BigDecimal requestedQuantity) {
        super(message);
        this.productId = productId;
        this.productName = productName;
        this.availableStock = availableStock;
        this.requestedQuantity = requestedQuantity;
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public BigDecimal getAvailableStock() {
        return availableStock;
    }

    public BigDecimal getRequestedQuantity() {
        return requestedQuantity;
    }
}
