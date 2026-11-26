package com.billing.simple.billsoft.dtos.debtor;

import com.billing.simple.billsoft.dtos.CustomerBalanceSummary;
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.CustomerCollectionActivity;
import com.billing.simple.billsoft.entities.CustomerPromiseToPay;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DebtorDetailDto {
    private Customer customer;
    private CustomerBalanceSummary balanceSummary;

    private BigDecimal outstandingAmount;
    private BigDecimal overdueAmount;
    private BigDecimal advanceCredit;

    private BigDecimal aging0To30;
    private BigDecimal aging31To60;
    private BigDecimal aging61To90;
    private BigDecimal aging90Plus;

    private LocalDate lastPaymentDate;
    private BigDecimal lastPaymentAmount;

    private double averagePaymentDelayDays;
    private double promiseFulfilmentRate;

    private String riskLevel;
    private String riskReason;

    private boolean hasSupplierCounterpart;
    private Long linkedPartyId;
    private String linkedPartyName;
    private BigDecimal supplierPayableAmount;

    private List<CustomerPromiseToPay> promises;
    private List<DebtorOverdueInvoiceDto> overdueInvoices;
    private List<CustomerCollectionActivity> recentActivities;
}
