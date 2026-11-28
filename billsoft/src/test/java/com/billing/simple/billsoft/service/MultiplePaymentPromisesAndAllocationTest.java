package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dtos.*;
import com.billing.simple.billsoft.dtos.debtor.*;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;
import com.billing.simple.billsoft.service.impl.DebtorServiceImpl;
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

public class MultiplePaymentPromisesAndAllocationTest {

    @Mock
    private CustomerRepository customerRepo;
    @Mock
    private InvoiceRepository invoiceRepo;
    @Mock
    private InvoicePaymentRepository invoicePaymentRepo;
    @Mock
    private SalesReturnRepository salesReturnRepo;
    @Mock
    private CustomerPromiseToPayRepository promiseRepo;
    @Mock
    private CustomerCollectionActivityRepository activityRepo;
    @Mock
    private ReminderRepository reminderRepo;
    @Mock
    private CustomerBalanceService balanceService;
    @Mock
    private BusinessPartyLinkService businessPartyLinkService;
    @Mock
    private FirmDetailsRepository firmRepo;
    @Mock
    private PromisePaymentAllocationRepository promiseAllocationRepo;
    @Mock
    private CustomerPromiseHistoryRepository promiseHistoryRepo;

    @InjectMocks
    private DebtorServiceImpl debtorService;

    private final Long firmId = 1L;
    private final Long otherFirmId = 2L;

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
    void testMultipleIndependentPromisesAgainstOneInvoice() {
        Customer cust = Customer.builder().id(100L).firmId(firmId).name("Rajesh Traders").build();
        Invoice inv = Invoice.builder()
                .id(200L).firmId(firmId).customer(cust)
                .invoiceNumber("AFS-2026-0001")
                .totalAmount(new BigDecimal("35000.00"))
                .status(InvoiceStatus.UNPAID)
                .build();

        when(customerRepo.findByIdAndFirmId(100L, firmId)).thenReturn(Optional.of(cust));
        when(invoiceRepo.findByIdAndFirmId(200L, firmId)).thenReturn(Optional.of(inv));
        when(invoicePaymentRepo.findByInvoiceIdAndFirmIdOrderByPaymentDateAscIdAsc(200L, firmId)).thenReturn(Collections.emptyList());
        when(salesReturnRepo.findByInvoiceIdAndFirmIdOrderByCreatedAtDesc(200L, firmId)).thenReturn(Collections.emptyList());

        // Existing active promises: Promise 1 (10,000) and Promise 2 (15,000) = 25,000 committed
        CustomerPromiseToPay p1 = CustomerPromiseToPay.builder()
                .id(1L).firmId(firmId).customerId(100L).invoiceId(200L)
                .promisedAmount(new BigDecimal("10000.00")).remainingAmount(new BigDecimal("10000.00"))
                .promisedDate(LocalDate.now().plusDays(2)).status(PromiseStatus.PROMISED).build();
        CustomerPromiseToPay p2 = CustomerPromiseToPay.builder()
                .id(2L).firmId(firmId).customerId(100L).invoiceId(200L)
                .promisedAmount(new BigDecimal("15000.00")).remainingAmount(new BigDecimal("15000.00"))
                .promisedDate(LocalDate.now().plusDays(5)).status(PromiseStatus.PROMISED).build();

        when(promiseRepo.findByFirmIdAndInvoiceIdAndStatusNotIn(eq(firmId), eq(200L), anyCollection()))
                .thenReturn(List.of(p1, p2));

        when(promiseRepo.save(any(CustomerPromiseToPay.class))).thenAnswer(i -> {
            CustomerPromiseToPay p = i.getArgument(0);
            p.setId(3L);
            return p;
        });

        // Promise 3 for 10,000: Total committed becomes 35,000 <= 35,000. Should SUCCEED!
        CreatePromiseRequest req3 = CreatePromiseRequest.builder()
                .customerId(100L)
                .invoiceId(200L)
                .promisedAmount(new BigDecimal("10000.00"))
                .promisedDate(LocalDate.now().plusDays(10))
                .notes("Promise 3 final installment")
                .build();

        CustomerPromiseToPay created = debtorService.createPromise(firmId, req3, "Admin");
        assertNotNull(created);
        assertEquals(new BigDecimal("10000.00"), created.getPromisedAmount());
        assertEquals(new BigDecimal("10000.00"), created.getRemainingAmount());
        assertEquals(PromiseStatus.PROMISED, created.getStatus());

        // Now attempt Promise 4 for 5,000: Available is 35,000 - 35,000 = 0. Should be REJECTED!
        when(promiseRepo.findByFirmIdAndInvoiceIdAndStatusNotIn(eq(firmId), eq(200L), anyCollection()))
                .thenReturn(List.of(p1, p2, created));

        CreatePromiseRequest req4 = CreatePromiseRequest.builder()
                .customerId(100L)
                .invoiceId(200L)
                .promisedAmount(new BigDecimal("5000.00"))
                .promisedDate(LocalDate.now().plusDays(12))
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                debtorService.createPromise(firmId, req4, "Admin")
        );
        assertTrue(ex.getMessage().contains("exceeds available invoice balance"));
    }

    @Test
    void testCrossFirmPromiseCreationRejection() {
        // Customer does not belong to requested firm
        when(customerRepo.findByIdAndFirmId(999L, firmId)).thenReturn(Optional.empty());

        CreatePromiseRequest req = CreatePromiseRequest.builder()
                .customerId(999L)
                .promisedAmount(new BigDecimal("5000.00"))
                .promisedDate(LocalDate.now().plusDays(3))
                .build();

        assertThrows(TenantSecurityException.class, () ->
                debtorService.createPromise(firmId, req, "Admin")
        );
    }

    @Test
    void testEditAndCancelPromiseWithHistory() {
        CustomerPromiseToPay pr = CustomerPromiseToPay.builder()
                .id(50L).firmId(firmId).customerId(100L).invoiceId(200L)
                .promisedAmount(new BigDecimal("10000.00"))
                .fulfilledAmount(new BigDecimal("2000.00"))
                .remainingAmount(new BigDecimal("8000.00"))
                .promisedDate(LocalDate.now().plusDays(3))
                .status(PromiseStatus.PARTIALLY_FULFILLED)
                .build();

        when(promiseRepo.findByIdAndFirmId(50L, firmId)).thenReturn(Optional.of(pr));
        when(promiseRepo.save(any(CustomerPromiseToPay.class))).thenAnswer(i -> i.getArgument(0));

        // 1. Edit promise: cannot reduce below fulfilled amount (2000)
        EditPromiseRequest badEdit = EditPromiseRequest.builder()
                .promisedAmount(new BigDecimal("1500.00"))
                .build();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                debtorService.editPromise(firmId, 50L, badEdit, "Admin")
        );
        assertTrue(ex.getMessage().contains("cannot be less than already fulfilled"));

        // 2. Valid edit: change date and amount
        EditPromiseRequest goodEdit = EditPromiseRequest.builder()
                .promisedAmount(new BigDecimal("12000.00"))
                .promisedDate(LocalDate.now().plusDays(7))
                .notes("Extended deadline by customer request")
                .build();

        CustomerPromiseToPay updated = debtorService.editPromise(firmId, 50L, goodEdit, "Admin");
        assertEquals(new BigDecimal("12000.00"), updated.getPromisedAmount());
        assertEquals(new BigDecimal("10000.00"), updated.getRemainingAmount());
        assertEquals(LocalDate.now().plusDays(7), updated.getPromisedDate());

        // Verify history logged
        verify(promiseHistoryRepo, times(1)).save(argThat(h ->
                h.getPromiseId().equals(50L) &&
                "EDITED".equals(h.getActionType()) &&
                new BigDecimal("10000.00").compareTo(h.getPreviousAmount()) == 0 &&
                new BigDecimal("12000.00").compareTo(h.getNewAmount()) == 0
        ));

        // 3. Cancel promise
        CustomerPromiseToPay cancelled = debtorService.cancelPromise(firmId, 50L, "Customer disputed invoice", "Admin");
        assertEquals(PromiseStatus.CANCELLED, cancelled.getStatus());
        verify(promiseHistoryRepo, times(1)).save(argThat(h ->
                h.getPromiseId().equals(50L) &&
                "CANCELLED".equals(h.getActionType())
        ));
    }

    @Test
    void testReconciliationFlagWhenPaymentUnallocatedToPromises() {
        Customer cust = Customer.builder().id(100L).firmId(firmId).name("Acme Corp").phone("9876543210").build();
        Invoice inv = Invoice.builder()
                .id(200L).firmId(firmId).customer(cust)
                .invoiceNumber("INV-200")
                .totalAmount(new BigDecimal("20000.00"))
                .status(InvoiceStatus.UNPAID)
                .build();

        // Active promise on invoice: ₹18,000
        CustomerPromiseToPay pr = CustomerPromiseToPay.builder()
                .id(1L).firmId(firmId).customerId(100L).invoiceId(200L)
                .promisedAmount(new BigDecimal("18000.00"))
                .fulfilledAmount(BigDecimal.ZERO)
                .remainingAmount(new BigDecimal("18000.00"))
                .promisedDate(LocalDate.now().plusDays(4))
                .status(PromiseStatus.PROMISED)
                .build();

        // Customer made an unallocated payment of ₹10,000 directly against the invoice
        // Remaining invoice outstanding is now 20,000 - 10,000 = ₹10,000.
        // But the active promise remaining is ₹18,000!
        // Excess promise commitment = 18,000 - 10,000 = ₹8,000.
        InvoicePayment unallocPmt = InvoicePayment.builder()
                .id(901L).firmId(firmId).invoiceId(200L).customerId(100L)
                .amount(new BigDecimal("10000.00"))
                .paymentDate(LocalDate.now())
                .build();

        when(customerRepo.findByFirmId(firmId)).thenReturn(List.of(cust));
        when(invoiceRepo.findByFirmId(firmId)).thenReturn(List.of(inv));
        when(invoicePaymentRepo.findByFirmId(firmId)).thenReturn(List.of(unallocPmt));
        when(salesReturnRepo.findByFirmId(firmId)).thenReturn(Collections.emptyList());
        when(promiseRepo.findByFirmIdOrderByPromisedDateDesc(firmId)).thenReturn(List.of(pr));

        PromiseWorkspaceDto ws = debtorService.getPromisesWorkspace(firmId, "ALL", null);
        assertNotNull(ws);
        assertEquals(1, ws.getPromises().size());
        DebtorPromiseDetailDto dto = ws.getPromises().get(0);

        assertEquals(new BigDecimal("10000.00"), dto.getInvoiceOutstanding());
        assertEquals(new BigDecimal("18000.00"), dto.getInvoiceRemainingPromisesTotal());
        assertTrue(dto.isReconciliationRequired());
        assertEquals(new BigDecimal("8000.00"), dto.getExcessPromisedAmount());
    }
}
