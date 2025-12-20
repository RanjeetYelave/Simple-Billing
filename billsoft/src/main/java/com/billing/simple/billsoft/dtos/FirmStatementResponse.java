package com.billing.simple.billsoft.dtos;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

/**
 * Firm-wide statement summary used for reports and PDF generation.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FirmStatementResponse {

    private LocalDate from;
    private LocalDate to;

    private Double totalBilled;
    private Double totalPaid;
    private Double outstanding;
    private Double totalTax;

    private Integer invoiceCount;

    private List<GstSummaryItem> gstSummary;
}
