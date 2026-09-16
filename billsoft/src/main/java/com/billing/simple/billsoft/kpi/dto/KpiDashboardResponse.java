package com.billing.simple.billsoft.kpi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Consolidated, read-only KPI dashboard response aggregating authoritative domain calculations.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KpiDashboardResponse {

    // Revenue & Receivables (Source: InvoiceService.getFirmAnalytics)
    private Double totalBusiness;
    private Double businessToday;
    private Double businessThisWeek;
    private Double businessThisMonth;
    private Double businessThisYear;
    private Double totalPaid;
    private Double totalPending;
    private Long unpaidInvoiceCount;
    private Long overdueInvoiceCount;
    private Double quotePipelineValue;
    private Long quoteCount;

    // Vendor Payables (Source: PartyService.getAllPartiesWithFinancialSummaries)
    private Double vendorPayables;
    private Long openPoCount;
    private Long totalVendors;

    // Inventory Valuation & Health (Source: ProductService.getInventorySummary)
    private Double totalInventoryCostValue;
    private Double totalInventoryRetailValue;
    private Long lowStockCount;
    private Long outOfStockCount;
    private Long totalProducts;

    // Expenses (Source: ExpenseService.getSummaryByFirm)
    private Double totalExpenses;
    private Double currentMonthExpenses;

    // Savings (Source: SavingService.getSummaryByFirm)
    private Double totalSavings;
    private Double currentMonthSavings;
}
