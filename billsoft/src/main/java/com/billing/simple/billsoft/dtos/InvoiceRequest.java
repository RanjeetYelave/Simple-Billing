package com.billing.simple.billsoft.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.billing.simple.billsoft.entities.InvoiceStatus;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InvoiceRequest {

    /* ============================
       BASIC FIELDS
       ============================ */
    private Long customerId;
    private String notes;

    /**
     * Optional invoice-level discount (PERCENT or VALUE).
     * Applied after per-item discounts.
     */
    private Discount invoiceDiscount;

    /**
     * Paid flag – if null, service defaults to false.
     */
    private Boolean paid;

    private List<InvoiceRequestItem> items;

    /* ============================
       NEW FIELDS FOR ESTIMATES
       ============================ */

    /**
     * DRAFT, ESTIMATE, FINAL, SENT, PAID, OVERDUE, CANCELLED
     */
    private InvoiceStatus status;

    /**
     * Used when creating estimates.
     */
    private String estimateNumber;

    /**
     * Only set by system during estimate → invoice conversion.
     */
    private Long convertedInvoiceId;

    /**
     * Optional due date.
     */
    private LocalDate dueDate;

    /* ============================
       PROFESSIONAL FIELDS
       ============================ */

    private String customerNote;
    private String termsAndConditions;

    private String paymentMethod;
    private String currency = "INR";

    /**
     * Optional round-off flag.
     * If present, InvoiceService applies final rounding.
     */
    private BigDecimal roundOff;

    /**
     * Tags (comma separated)
     */
    private String tags;

    /* ============================
       INNER CLASS – Discount
       ============================ */
    @Getter
    @Setter
    public static class Discount {
        private String type;            // "PERCENT" or "VALUE"
        private BigDecimal value;       // value or percent (BigDecimal)
    }
}
