package com.billing.simple.billsoft.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.billing.simple.billsoft.entities.InvoiceStatus;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InvoiceUpdateRequest {

    /* ============================
       BASIC EXISTING FIELDS
       ============================ */
    private Long customerId;

    /**
     * Optional ISO local date-time string
     * Example: "2025-11-21T16:30" or "2025-11-21T16:30:00"
     */
    private String invoiceDate;

    private String notes;

    /**
     * Optional invoice-level discount (PERCENT or VALUE)
     * Uses the same BigDecimal-based inner class from InvoiceRequest.
     */
    private InvoiceRequest.Discount invoiceDiscount;

    /**
     * Paid flag – if null, keep existing stored value.
     */
    private Boolean paid;

    /**
     * Full replacement of invoice items.
     * Backend recalculates totals automatically.
     */
    private List<InvoiceRequestItem> items;

    /* ============================
       ADVANCED INVOICE LIFECYCLE
       ============================ */

    /**
     * Invoice status:
     * DRAFT, ESTIMATE, FINAL, SENT, PAID, OVERDUE, CANCELLED
     */
    private InvoiceStatus status;

    /**
     * Used only when updating an estimate.
     */
    private String estimateNumber;

    /**
     * Used internally when converting Estimate → Invoice.
     */
    private Long convertedInvoiceId;

    /**
     * Optional due date
     */
    private LocalDate dueDate;

    /* ============================
       PROFESSIONAL FIELDS
       ============================ */

    private String customerNote;
    private String termsAndConditions;

    /* ============================
       METADATA
       ============================ */

    private String paymentMethod;
    private String currency;   // if null, backend keeps existing

    /**
     * Optional round-off request.
     * If present → backend applies final rounding.
     */
    private Boolean roundOff;

    /**
     * Optional comma-separated tags
     */
    private String tags;
}
