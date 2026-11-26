package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dtos.debtor.*;
import com.billing.simple.billsoft.entities.CustomerCollectionActivity;
import com.billing.simple.billsoft.entities.CustomerPromiseToPay;

import java.math.BigDecimal;
import java.util.List;

public interface DebtorService {

    DebtorsOverviewDto getOverview(Long firmId);

    List<DebtorListItemDto> getDebtors(Long firmId, String search, String filter, String sortBy, String sortDir);

    DebtorDetailDto getDebtorDetail(Long firmId, Long customerId);

    List<DebtorOverdueInvoiceDto> getCustomerOverdueInvoices(Long firmId, Long customerId);

    List<DebtorOverdueInvoiceDto> getFirmOverdueInvoices(Long firmId);

    List<CustomerCollectionActivity> getCustomerTimeline(Long firmId, Long customerId);

    List<DebtorPaymentHistoryItemDto> getCustomerPaymentHistory(Long firmId, Long customerId);

    List<DebtorFollowUpDto> getFollowUps(Long firmId, String status);

    CustomerPromiseToPay createPromise(Long firmId, CreatePromiseRequest request, String createdBy);

    CustomerPromiseToPay updatePromiseStatus(Long firmId, Long promiseId, UpdatePromiseStatusRequest request);

    CustomerCollectionActivity logActivity(Long firmId, LogActivityRequest request, String performedBy);

    void reconcilePaymentWithPromises(Long firmId, Long customerId, BigDecimal paymentAmount, Long paymentId);

    byte[] exportDebtorsCsv(Long firmId);

    byte[] exportDebtorsExcel(Long firmId);

    byte[] exportDebtorsPdf(Long firmId) throws Exception;
}
