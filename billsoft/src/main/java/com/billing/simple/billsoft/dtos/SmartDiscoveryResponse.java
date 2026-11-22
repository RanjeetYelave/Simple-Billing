package com.billing.simple.billsoft.dtos;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SmartDiscoveryResponse {

    private Long firmId;
    private int periodDays;
    private List<ProductUsageStat> frequentlyUsed = new ArrayList<>();
    private List<ProductUsageStat> recentlyUsed = new ArrayList<>();
    private List<Long> favorites = new ArrayList<>();

    public static class ProductUsageStat {
        private Long productId;
        private Long distinctInvoiceCount;
        private LocalDateTime lastInvoicedAt;

        public ProductUsageStat() {}

        public ProductUsageStat(Long productId, Long distinctInvoiceCount, LocalDateTime lastInvoicedAt) {
            this.productId = productId;
            this.distinctInvoiceCount = distinctInvoiceCount;
            this.lastInvoicedAt = lastInvoicedAt;
        }

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }

        public Long getDistinctInvoiceCount() { return distinctInvoiceCount; }
        public void setDistinctInvoiceCount(Long distinctInvoiceCount) { this.distinctInvoiceCount = distinctInvoiceCount; }

        public LocalDateTime getLastInvoicedAt() { return lastInvoicedAt; }
        public void setLastInvoicedAt(LocalDateTime lastInvoicedAt) { this.lastInvoicedAt = lastInvoicedAt; }
    }

    public SmartDiscoveryResponse() {}

    public Long getFirmId() { return firmId; }
    public void setFirmId(Long firmId) { this.firmId = firmId; }

    public int getPeriodDays() { return periodDays; }
    public void setPeriodDays(int periodDays) { this.periodDays = periodDays; }

    public List<ProductUsageStat> getFrequentlyUsed() { return frequentlyUsed; }
    public void setFrequentlyUsed(List<ProductUsageStat> frequentlyUsed) { this.frequentlyUsed = frequentlyUsed; }

    public List<ProductUsageStat> getRecentlyUsed() { return recentlyUsed; }
    public void setRecentlyUsed(List<ProductUsageStat> recentlyUsed) { this.recentlyUsed = recentlyUsed; }

    public List<Long> getFavorites() { return favorites; }
    public void setFavorites(List<Long> favorites) { this.favorites = favorites; }
}
