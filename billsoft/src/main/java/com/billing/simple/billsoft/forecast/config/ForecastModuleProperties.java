package com.billing.simple.billsoft.forecast.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "billsoft.forecast")
public class ForecastModuleProperties {

    // Technical fallbacks for replenishment
    private int defaultLeadTimeDays = 7;
    private int defaultReviewPeriodDays = 7;
    private BigDecimal defaultMoq = new BigDecimal("1.000");
    private BigDecimal defaultPackSize = new BigDecimal("1.000");
    private double defaultSafetyStockZ = 1.65; // 95% service level

    // Scheduling cron expressions
    private String syncCron = "0 */15 * * * *"; // Every 15 minutes
    private String nightlyCron = "0 0 2 * * *";   // Nightly at 2:00 AM
    private boolean schedulerEnabled = true;
}
