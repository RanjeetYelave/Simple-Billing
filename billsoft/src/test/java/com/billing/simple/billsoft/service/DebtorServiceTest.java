package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dtos.BusinessPartyLinkResponse;
import com.billing.simple.billsoft.dtos.CustomerBalanceSummary;
import com.billing.simple.billsoft.dtos.debtor.*;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.service.impl.DebtorServiceImpl;
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

public class DebtorServiceTest {

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

    @InjectMocks
    private DebtorServiceImpl debtorService;

    private final Long firmId = 1L;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetOverviewAndAgingCalculation() {
        Customer c1 = Customer.builder().id(10L).firmId(firmId).name("Acme Corp").phone("9876543210").build();
        Customer c2 = Customer.builder().id(20L).firmId(firmId).name("Beta Ltd").phone("9876543211").build();
        when(customerRepo.findByFirmId(firmId)).thenReturn(List.of(c1, c2));

        // c1 has positive closing balance 5000 (debtor)
        when(balanceService.getCustomerBalance(firmId, 10L)).thenReturn(CustomerBalanceSummary.builder()
                .customerId(10L).firmId(firmId)
                .totalBilled(new BigDecimal("10000.00"))
                .totalPaid(new BigDecimal("5000.00"))
                .totalReturns(BigDecimal.ZERO)
                .closingBalance(new BigDecimal("5000.00"))
                .build());

        // c2 has advance credit -2000 (held credit)
        when(balanceService.getCustomerBalance(firmId, 20L)).thenReturn(CustomerBalanceSummary.builder()
                .customerId(20L).firmId(firmId)
                .totalBilled(new BigDecimal("3000.00"))
                .totalPaid(new BigDecimal("5000.00"))
                .totalReturns(BigDecimal.ZERO)
                .closingBalance(new BigDecimal("-2000.00"))
                .build());

        // Invoices: c1 has an overdue invoice due 45 days ago (in 31-60 bucket) for 3000
        Invoice inv1 = Invoice.builder()
                .id(101L).firmId(firmId).customer(c1)
                .invoiceNumber("INV-101")
                .invoiceDate(LocalDateTime.now().minusDays(50))
                .dueDate(LocalDate.now().minusDays(45))
                .totalAmount(new BigDecimal("3000.00"))
                .status(InvoiceStatus.UNPAID)
                .build();

        when(invoiceRepo.findByFirmId(firmId)).thenReturn(List.of(inv1));
        when(invoicePaymentRepo.findByFirmId(firmId)).thenReturn(Collections.emptyList());
        when(salesReturnRepo.findByFirmId(firmId)).thenReturn(Collections.emptyList());
        when(promiseRepo.findByFirmId(firmId)).thenReturn(Collections.emptyList());
        when(reminderRepo.findByFirmId(firmId)).thenReturn(Collections.emptyList());

        DebtorsOverviewDto overview = debtorService.getOverview(firmId);

        assertNotNull(overview);
        // Total outstanding is only from positive balances (5000.00)
        assertEquals(new BigDecimal("5000.00"), overview.getTotalOutstanding());
        // Advance credit is separately tracked (2000.00) and NOT subtracted from total outstanding
        assertEquals(new BigDecimal("2000.00"), overview.getAdvanceCredit());
        assertEquals(1, overview.getTotalDebtorsCount());
        assertEquals(1, overview.getOverdueDebtorsCount());

        // Aging: 3000 in 31-60 bucket
        assertEquals(0, new BigDecimal("3000.00").compareTo(overview.getAging31To60()));
        assertEquals(0, BigDecimal.ZERO.compareTo(overview.getAging0To30()));
        assertEquals(0, BigDecimal.ZERO.compareTo(overview.getAging61To90()));
        assertEquals(0, BigDecimal.ZERO.compareTo(overview.getAging90Plus()));
    }

    @Test
    void testCounterpartyLinkDoesNotNetReceivables() {
        Customer c1 = Customer.builder().id(10L).firmId(firmId).name("Dual Entity").phone("9876543210").build();
        when(customerRepo.findByFirmId(firmId)).thenReturn(List.of(c1));

        when(balanceService.getCustomerBalance(firmId, 10L)).thenReturn(CustomerBalanceSummary.builder()
                .customerId(10L).firmId(firmId)
                .totalBilled(new BigDecimal("15000.00"))
                .totalPaid(BigDecimal.ZERO)
                .totalReturns(BigDecimal.ZERO)
                .closingBalance(new BigDecimal("15000.00"))
                .build());

        when(invoiceRepo.findByFirmId(firmId)).thenReturn(Collections.emptyList());
        when(invoicePaymentRepo.findByFirmId(firmId)).thenReturn(Collections.emptyList());
        when(salesReturnRepo.findByFirmId(firmId)).thenReturn(Collections.emptyList());
        when(promiseRepo.findByFirmId(firmId)).thenReturn(Collections.emptyList());

        // c1 is linked to a supplier with 20000 payable
        when(businessPartyLinkService.getLinkedPartyForCustomer(10L)).thenReturn(BusinessPartyLinkResponse.builder()
                .customerId(10L)
                .partyId(99L)
                .partyName("Dual Supplier")
                .partyNetPayable(20000.0)
                .build());

        List<DebtorListItemDto> debtors = debtorService.getDebtors(firmId, null, "all", "default", "desc");

        assertEquals(1, debtors.size());
        DebtorListItemDto debtor = debtors.get(0);
        // Authoritative receivable remains 15000.00 (NOT netted to -5000)
        assertEquals(new BigDecimal("15000.00"), debtor.getOutstandingAmount());
        assertTrue(debtor.isHasSupplierCounterpart());
        assertEquals(new BigDecimal("20000.00"), debtor.getSupplierPayableAmount());
    }

    @Test
    void testPromiseToPayCreationAndReconciliation() {
        Customer c1 = Customer.builder().id(10L).firmId(firmId).name("Promising Customer").build();
        when(customerRepo.findByIdAndFirmId(10L, firmId)).thenReturn(Optional.of(c1));

        // Create promise
        CreatePromiseRequest req = CreatePromiseRequest.builder()
                .customerId(10L)
                .promisedAmount(new BigDecimal("7500.00"))
                .promisedDate(LocalDate.now().plusDays(5))
                .notes("Will clear next Tuesday")
                .build();

        when(promiseRepo.save(any(CustomerPromiseToPay.class))).thenAnswer(invocation -> {
            CustomerPromiseToPay p = invocation.getArgument(0);
            p.setId(555L);
            return p;
        });

        CustomerPromiseToPay created = debtorService.createPromise(firmId, req, "TestAdmin");
        assertNotNull(created);
        assertEquals(new BigDecimal("7500.00"), created.getPromisedAmount());
        assertEquals(PromiseStatus.PROMISED, created.getStatus());

        // Verify collection activity was recorded
        verify(activityRepo, times(1)).save(argThat(a ->
                a.getCustomerId().equals(10L) &&
                a.getActivityType() == CollectionActivityType.PROMISE_CREATED
        ));

        // Reconcile with incoming payment of 5000 (partial)
        CustomerPromiseToPay activePromise = CustomerPromiseToPay.builder()
                .id(555L)
                .firmId(firmId)
                .customerId(10L)
                .promisedAmount(new BigDecimal("7500.00"))
                .promisedDate(LocalDate.now().plusDays(5))
                .fulfilledAmount(BigDecimal.ZERO)
                .remainingAmount(new BigDecimal("7500.00"))
                .status(PromiseStatus.PROMISED)
                .build();

        when(promiseRepo.findByFirmIdAndCustomerIdAndStatusIn(eq(firmId), eq(10L), anyList()))
                .thenReturn(List.of(activePromise));

        debtorService.reconcilePaymentWithPromises(firmId, 10L, new BigDecimal("5000.00"), 999L);

        assertEquals(PromiseStatus.PARTIALLY_FULFILLED, activePromise.getStatus());
        assertEquals(new BigDecimal("5000.00"), activePromise.getFulfilledAmount());
        assertEquals(new BigDecimal("2500.00"), activePromise.getRemainingAmount());

        // Now pay remaining 2500
        debtorService.reconcilePaymentWithPromises(firmId, 10L, new BigDecimal("2500.00"), 1000L);
        assertEquals(PromiseStatus.FULFILLED, activePromise.getStatus());
        assertEquals(new BigDecimal("7500.00"), activePromise.getFulfilledAmount());
        assertEquals(BigDecimal.ZERO, activePromise.getRemainingAmount());
    }

    @Test
    void testCustomerRiskClassificationAndDelayMetric() {
        Customer c1 = Customer.builder().id(10L).firmId(firmId).name("Delayed Debtor").build();
        when(customerRepo.findByIdAndFirmId(10L, firmId)).thenReturn(Optional.of(c1));

        when(balanceService.getCustomerBalance(firmId, 10L)).thenReturn(CustomerBalanceSummary.builder()
                .customerId(10L).firmId(firmId)
                .totalBilled(new BigDecimal("20000.00"))
                .totalPaid(new BigDecimal("5000.00"))
                .totalReturns(BigDecimal.ZERO)
                .closingBalance(new BigDecimal("15000.00"))
                .build());

        // Invoice overdue by 100 days (critical bucket 90+)
        Invoice inv = Invoice.builder()
                .id(101L).firmId(firmId).customer(c1)
                .invoiceNumber("INV-101")
                .invoiceDate(LocalDateTime.now().minusDays(110))
                .dueDate(LocalDate.now().minusDays(100))
                .totalAmount(new BigDecimal("15000.00"))
                .status(InvoiceStatus.UNPAID)
                .build();

        when(invoiceRepo.findByFirmIdAndCustomer_Id(firmId, 10L)).thenReturn(List.of(inv));
        when(invoicePaymentRepo.findByFirmIdAndCustomerIdOrderByPaymentDateAscIdAsc(firmId, 10L)).thenReturn(Collections.emptyList());
        when(salesReturnRepo.findByFirmIdAndCustomerIdOrderByReturnDateAscIdAsc(firmId, 10L)).thenReturn(Collections.emptyList());
        when(promiseRepo.findByFirmIdAndCustomerIdOrderByPromisedDateDesc(firmId, 10L)).thenReturn(Collections.emptyList());
        when(activityRepo.findByFirmIdAndCustomerIdOrderByCreatedAtDesc(firmId, 10L)).thenReturn(Collections.emptyList());

        DebtorDetailDto detail = debtorService.getDebtorDetail(firmId, 10L);

        assertNotNull(detail);
        assertEquals("HIGH", detail.getRiskLevel());
        assertTrue(detail.getRiskReason().contains("90+"));
        assertEquals(new BigDecimal("15000.00"), detail.getAging90Plus());
    }

    @Test
    void testDebtorsRegisterCsvExport() {
        Customer c1 = Customer.builder().id(10L).firmId(firmId).name("Export Corp").phone("9988776655").build();
        when(customerRepo.findByFirmId(firmId)).thenReturn(List.of(c1));

        when(balanceService.getCustomerBalance(firmId, 10L)).thenReturn(CustomerBalanceSummary.builder()
                .customerId(10L).firmId(firmId)
                .totalBilled(new BigDecimal("12000.00"))
                .totalPaid(new BigDecimal("2000.00"))
                .totalReturns(BigDecimal.ZERO)
                .closingBalance(new BigDecimal("10000.00"))
                .build());

        when(invoiceRepo.findByFirmId(firmId)).thenReturn(Collections.emptyList());
        when(invoicePaymentRepo.findByFirmId(firmId)).thenReturn(Collections.emptyList());
        when(salesReturnRepo.findByFirmId(firmId)).thenReturn(Collections.emptyList());
        when(promiseRepo.findByFirmId(firmId)).thenReturn(Collections.emptyList());

        byte[] csvBytes = debtorService.exportDebtorsCsv(firmId);
        assertNotNull(csvBytes);
        String csv = new String(csvBytes);
        assertTrue(csv.contains("Customer Name,Phone,Email,Outstanding Balance"));
        assertTrue(csv.contains("Export Corp"));
        assertTrue(csv.contains("10000.00"));
    }
}
