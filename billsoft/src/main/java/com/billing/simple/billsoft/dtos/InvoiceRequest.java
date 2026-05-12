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
    private Long firmId;
    private Long customerId;
    private String notes;

    private List<InvoiceRequestItem> items;

    /**
     * Paid flag – if null, service defaults to false.
     */
    private Boolean paid;

    /* ============================
       DISCOUNT HANDLING
       ============================ */
    private Discount invoiceDiscount;

    /* ============================
       INVOICE / ESTIMATE NUMBERS
       ============================ */

    /**
     * Only used for FINAL invoices.
     * UI can send null → backend will auto-generate.
     */
    private String invoiceNumber;

    /**
     * Only used for ESTIMATES.
     * UI can send null → backend will auto-generate.
     */
    private String estimateNumber;

    /* ============================
       STATUS CONTROL
       ============================ */

    /**
     * DRAFT, ESTIMATE, FINAL, SENT, PAID, OVERDUE, CANCELLED
     */
    private InvoiceStatus status;

    /**
     * Used only when estimate converts to invoice.
     */
    private Long convertedInvoiceId;

    private LocalDate dueDate;

    /* ============================
       PROFESSIONAL FIELDS
       ============================ */
    private String customerNote;
    private String termsAndConditions;
    private String paymentMethod;
    private String currency = "INR";
    private BigDecimal roundOff;
    private String tags;

    /* ============================
       DATE — FROM UI (OPTIONAL)
       ============================ */
    private String invoiceDate; // format: yyyy-MM-dd or yyyy-MM-ddTHH:mm

    /* ============================
       INNER CLASS – Discount
       ============================ */
    @Getter
    @Setter
    public static class Discount {
        private String type;      // "PERCENT" or "VALUE"
        private BigDecimal value; // percent or amount (as per type)
    }
}
