package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dtos.CustomerBalanceSummary;

/**
 * Authoritative Customer Balance Service interface.
 * Enforces single-source-of-truth financial calculation for customer debt and receivables.
 */
public interface CustomerBalanceService {

    /**
     * Compute authoritative customer balance with explicit firm validation.
     */
    CustomerBalanceSummary getCustomerBalance(Long firmId, Long customerId);

    /**
     * Compute authoritative customer balance using active tenant context.
     */
    CustomerBalanceSummary getCustomerBalance(Long customerId);
}
