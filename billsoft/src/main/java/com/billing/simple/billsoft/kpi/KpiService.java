package com.billing.simple.billsoft.kpi;

import com.billing.simple.billsoft.kpi.dto.CustomerKpiSummary;
import com.billing.simple.billsoft.kpi.dto.KpiDashboardResponse;

import java.util.List;

/**
 * Standalone read-only KPI aggregation layer.
 * Acts as a facade orchestrating existing authoritative domain service calculations
 * and batch-aggregating customer ledger metrics with strict firm isolation.
 */
public interface KpiService {

    /**
     * Aggregates authoritative metrics for Dashboard KPI cards.
     *
     * @param firmId Authoritative firm ID from tenant security context
     * @return Consolidated dashboard metrics
     */
    KpiDashboardResponse getDashboardKpis(Long firmId);

    /**
     * Batch aggregates customer financial standings (billed, paid, returns, net dues)
     * matching Customer Statement semantics in O(customers + records) without N+1 queries.
     *
     * @param firmId Authoritative firm ID from tenant security context
     * @return List of customer KPI summaries
     */
    List<CustomerKpiSummary> getCustomerKpiSummaries(Long firmId);
}
