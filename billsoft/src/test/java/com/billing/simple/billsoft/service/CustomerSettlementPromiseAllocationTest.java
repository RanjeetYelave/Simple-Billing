package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dtos.*;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class CustomerSettlementPromiseAllocationTest {

    @Mock
    private CustomerRepository customerRepo;
    @Mock
    private InvoiceRepository invoiceRepo;
    @Mock
    private InvoicePaymentRepository invoicePaymentRepo;
    @Mock
    private SalesReturnRepository salesReturnRepo;
    @Mock
    private CustomerBalanceService balanceService;
    @Mock
    private CustomerPromiseToPayRepository promiseRepo;
    @Mock
    private PromisePaymentAllocationRepository promiseAllocationRepo;
    @Mock
    private CustomerPromiseHistoryRepository promiseHistoryRepo;
    @Mock
    private CustomerCollectionActivityRepository activityRepo;

    @InjectMocks
    private CustomerSettlementService settlementService;

    private final Long firmId = 1L;
    private final Long customerId = 100L;
    private final Long invoiceId = 200L;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TenantContext.setCurrentFirmId(firmId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testManualPromiseAllocationOnSettlement() {
        Customer cust = Customer.builder().id(customerId).firmId(firmId).name("Atlas Corp").build();
        Invoice inv = Invoice.builder()
                .id(invoiceId).firmId(firmId).customer(cust)
                .invoiceNumber("INV-200")
                .totalAmount(new BigDecimal("30000.00"))
                .status(InvoiceStatus.UNPAID)
                .build();

        // Promise 1 for 10,000 (remaining 10,000)
        CustomerPromiseToPay p1 = CustomerPromiseToPay.builder()
                .id(10L).firmId(firmId).customerId(customerId).invoiceId(invoiceId)
                .promisedAmount(new BigDecimal("10000.00"))
                .fulfilledAmount(BigDecimal.ZERO)
                .remainingAmount(new BigDecimal("10000.00"))
                .promisedDate(LocalDate.now().plusDays(2))
                .status(PromiseStatus.PROMISED)
                .build();

        // Promise 2 for 15,000 (remaining 15,000)
        CustomerPromiseToPay p2 = CustomerPromiseToPay.builder()
                .id(20L).firmId(firmId).customerId(customerId).invoiceId(invoiceId)
                .promisedAmount(new BigDecimal("15000.00"))
                .fulfilledAmount(BigDecimal.ZERO)
                .remainingAmount(new BigDecimal("15000.00"))
                .promisedDate(LocalDate.now().plusDays(5))
                .status(PromiseStatus.PROMISED)
                .build();

        when(customerRepo.findByIdAndFirmId(customerId, firmId)).thenReturn(Optional.of(cust));
        when(invoiceRepo.findByIdAndFirmId(invoiceId, firmId)).thenReturn(Optional.of(inv));
        when(invoicePaymentRepo.findByInvoiceIdAndFirmIdOrderByPaymentDateAscIdAsc(invoiceId, firmId)).thenReturn(Collections.emptyList());
        when(salesReturnRepo.findByInvoiceIdAndFirmIdOrderByCreatedAtDesc(invoiceId, firmId)).thenReturn(Collections.emptyList());

        when(promiseRepo.findByIdAndFirmId(10L, firmId)).thenReturn(Optional.of(p1));
        when(promiseRepo.findByIdAndFirmId(20L, firmId)).thenReturn(Optional.of(p2));

        when(invoicePaymentRepo.save(any(InvoicePayment.class))).thenAnswer(i -> {
            InvoicePayment p = i.getArgument(0);
            p.setId(500L);
            return p;
        });

        // Customer pays 15,000. User EXPLICITLY allocates 10,000 to Promise 1 and 5,000 to Promise 2
        CustomerSettlementRequest req = CustomerSettlementRequest.builder()
                .amount(new BigDecimal("15000.00"))
                .paymentDate(LocalDate.now())
                .paymentMode("Bank Transfer")
                .allocations(List.of(
                        InvoiceAllocationItem.builder().invoiceId(invoiceId).amount(new BigDecimal("15000.00")).build()
                ))
                .promiseAllocations(List.of(
                        PromiseAllocationItem.builder().promiseId(10L).amount(new BigDecimal("10000.00")).build(),
                        PromiseAllocationItem.builder().promiseId(20L).amount(new BigDecimal("5000.00")).build()
                ))
                .build();

        CustomerSettlementResponse res = settlementService.settleInvoices(customerId, req);
        assertNotNull(res);
        assertEquals(new BigDecimal("15000.00"), res.getTotalAllocated());

        // Verify Promise 1 is FULFILLED (remaining 0)
        assertEquals(PromiseStatus.FULFILLED, p1.getStatus());
        assertEquals(new BigDecimal("10000.00"), p1.getFulfilledAmount());
        assertEquals(BigDecimal.ZERO, p1.getRemainingAmount());

        // Verify Promise 2 is PARTIALLY_FULFILLED (fulfilled 5000, remaining 10000)
        assertEquals(PromiseStatus.PARTIALLY_FULFILLED, p2.getStatus());
        assertEquals(new BigDecimal("5000.00"), p2.getFulfilledAmount());
        assertEquals(new BigDecimal("10000.00"), p2.getRemainingAmount());

        // Verify PromisePaymentAllocation records saved
        verify(promiseAllocationRepo, times(1)).save(argThat(a ->
                a.getPromiseId().equals(10L) &&
                a.getAllocatedAmount().compareTo(new BigDecimal("10000.00")) == 0
        ));
        verify(promiseAllocationRepo, times(1)).save(argThat(a ->
                a.getPromiseId().equals(20L) &&
                a.getAllocatedAmount().compareTo(new BigDecimal("5000.00")) == 0
        ));

        // Verify audit history saved
        verify(promiseHistoryRepo, times(2)).save(argThat(h ->
                "ALLOCATED".equals(h.getActionType())
        ));
    }

    @Test
    void testReversalOfPromiseAllocationsOnPaymentDeletion() {
        InvoicePayment payment = InvoicePayment.builder()
                .id(777L).firmId(firmId).customerId(customerId).invoiceId(invoiceId)
                .amount(new BigDecimal("6000.00"))
                .build();

        CustomerPromiseToPay promise = CustomerPromiseToPay.builder()
                .id(10L).firmId(firmId).customerId(customerId).invoiceId(invoiceId)
                .promisedAmount(new BigDecimal("10000.00"))
                .fulfilledAmount(new BigDecimal("6000.00"))
                .remainingAmount(new BigDecimal("4000.00"))
                .status(PromiseStatus.PARTIALLY_FULFILLED)
                .build();

        PromisePaymentAllocation alloc = PromisePaymentAllocation.builder()
                .id(1L).firmId(firmId).promiseId(10L).paymentId(777L)
                .allocatedAmount(new BigDecimal("6000.00"))
                .build();

        when(invoicePaymentRepo.findByIdAndFirmId(777L, firmId)).thenReturn(Optional.of(payment));
        when(promiseAllocationRepo.findByFirmIdAndPaymentId(firmId, 777L)).thenReturn(List.of(alloc));
        when(promiseRepo.findByIdAndFirmId(10L, firmId)).thenReturn(Optional.of(promise));
        when(invoiceRepo.findByIdAndFirmId(invoiceId, firmId)).thenReturn(Optional.of(Invoice.builder().id(invoiceId).firmId(firmId).totalAmount(new BigDecimal("30000.00")).status(InvoiceStatus.UNPAID).build()));
        when(invoicePaymentRepo.findByInvoiceIdAndFirmIdOrderByPaymentDateAscIdAsc(invoiceId, firmId)).thenReturn(Collections.emptyList());
        when(salesReturnRepo.findByInvoiceIdAndFirmIdOrderByCreatedAtDesc(invoiceId, firmId)).thenReturn(Collections.emptyList());

        boolean deleted = settlementService.deletePayment(777L);
        assertTrue(deleted);

        // Verify fulfillment was revoked
        assertEquals(BigDecimal.ZERO, promise.getFulfilledAmount());
        assertEquals(new BigDecimal("10000.00"), promise.getRemainingAmount());
        assertEquals(PromiseStatus.PROMISED, promise.getStatus());

        // Verify allocation record was deleted
        verify(promiseAllocationRepo, times(1)).deleteByFirmIdAndPaymentId(firmId, 777L);

        // Verify history logged ALLOCATION_REVERSED
        verify(promiseHistoryRepo, times(1)).save(argThat(h ->
                h.getPromiseId().equals(10L) &&
                "ALLOCATION_REVERSED".equals(h.getActionType())
        ));
    }
}
