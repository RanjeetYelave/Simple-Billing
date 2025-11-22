package com.billing.simple.billsoft.dtos;

import java.util.List;
import lombok.Data;

@Data
public class FirmAnalyticsResponse {

    private Double totalBusiness;
    private Double totalPaid;
    private Double totalPending;

    private Double todayBusiness;
    private Double thisWeekBusiness;
    private Double thisMonthBusiness;
    private Double thisYearBusiness;

    private List<FirmInvoiceSummary> invoices;
}
