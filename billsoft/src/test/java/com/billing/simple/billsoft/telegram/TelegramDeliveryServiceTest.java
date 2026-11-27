package com.billing.simple.billsoft.telegram;

import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.PurchaseOrder;
import com.billing.simple.billsoft.repo.EmployeeRepository;
import com.billing.simple.billsoft.repo.SalaryRecordRepository;
import com.billing.simple.billsoft.service.*;
import com.billing.simple.billsoft.telegram.client.TelegramApiClient;
import com.billing.simple.billsoft.telegram.entities.TelegramDocumentDelivery;
import com.billing.simple.billsoft.telegram.repo.TelegramDocumentDeliveryRepository;
import com.billing.simple.billsoft.telegram.service.TelegramDeliveryService;
import com.billing.simple.billsoft.telegram.service.TelegramReadService;
import com.billing.simple.billsoft.telegram.service.TelegramSecurityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TelegramDeliveryServiceTest {

    @Mock
    private TelegramDocumentDeliveryRepository deliveryRepo;

    @Mock
    private TelegramSecurityManager securityManager;

    @Mock
    private TelegramApiClient apiClient;

    @Mock
    private InvoiceService invoiceService;

    @Mock
    private InvoicePdfService invoicePdfService;

    @Mock
    private PurchaseOrderService poService;

    @Mock
    private StatementService statementService;

    @Mock
    private EmployeePdfService employeePdfService;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private SalaryRecordRepository salaryRecordRepository;

    @Mock
    private FirmDetailsService firmDetailsService;

    @Mock
    private TelegramReadService readService;

    @Mock
    private com.billing.simple.billsoft.repo.CustomerRepository customerRepository;

    @Mock
    private com.billing.simple.billsoft.repositories.PartyRepository partyRepository;

    private TelegramDeliveryService deliveryService;

    @BeforeEach
    void setUp() {
        deliveryService = new TelegramDeliveryService(
                deliveryRepo, securityManager, apiClient, invoiceService, invoicePdfService, poService,
                statementService, employeePdfService, employeeRepository, salaryRecordRepository, firmDetailsService, readService,
                customerRepository, partyRepository
        );
        lenient().when(deliveryRepo.save(any(TelegramDocumentDelivery.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void testPreFlightRevocationCheckCancelsDeliveryImmediately() throws Exception {
        TelegramDocumentDelivery delivery = new TelegramDocumentDelivery();
        delivery.setDeliveryId(101L);
        delivery.setUserId(99999L);
        delivery.setChatId(12345L);
        delivery.setFirmId(1L);
        delivery.setDocumentType("INVOICE_PDF");
        delivery.setDocumentId(500L);
        delivery.setDeliveryState("QUEUED");

        // User is revoked / not authorized
        when(securityManager.isUserAuthorized(99999L)).thenReturn(false);

        deliveryService.processSingleDelivery(delivery, "MOCK-BOT-TOKEN");

        assertEquals("CANCELLED", delivery.getDeliveryState());
        assertTrue(delivery.getLastError().contains("revoked"));
        // Ensure sensitive document was NEVER generated or uploaded
        verifyNoInteractions(invoicePdfService);
        verifyNoInteractions(apiClient);
    }

    @Test
    void testSuccessfulInvoicePdfDelivery() throws Exception {
        Long userId = 123456L;
        Long firmId = 1L;
        Long invoiceId = 500L;

        TelegramDocumentDelivery delivery = new TelegramDocumentDelivery();
        delivery.setDeliveryId(102L);
        delivery.setUserId(userId);
        delivery.setChatId(7890L);
        delivery.setFirmId(firmId);
        delivery.setDocumentType("INVOICE_PDF");
        delivery.setDocumentId(invoiceId);
        delivery.setDocumentNumber("INV-2026-001");
        delivery.setDeliveryState("QUEUED");

        when(securityManager.isUserAuthorized(userId)).thenReturn(true);

        Invoice inv = new Invoice();
        inv.setId(invoiceId);
        inv.setFirmId(firmId);
        inv.setInvoiceNumber("INV-2026-001");
        inv.setTotalAmount(BigDecimal.valueOf(1500.00));
        Customer c = new Customer();
        c.setName("Test Customer");
        inv.setCustomer(c);

        when(invoiceService.getById(invoiceId)).thenReturn(inv);
        when(invoicePdfService.generatePdf(any(), eq("A4"))).thenReturn(new byte[]{1, 2, 3});

        deliveryService.processSingleDelivery(delivery, "MOCK-BOT-TOKEN");

        assertEquals("SENT", delivery.getDeliveryState());
        assertNotNull(delivery.getCompletedAt());
        verify(apiClient, times(1)).sendDocument(eq("MOCK-BOT-TOKEN"), eq(7890L), any(byte[].class), eq("Invoice_INV-2026-001.pdf"), anyString());
    }

    @Test
    void testSuccessfulCustomerStatementDelivery() throws Exception {
        Long userId = 123456L;
        Long firmId = 1L;
        Long customerId = 55L;

        TelegramDocumentDelivery delivery = new TelegramDocumentDelivery();
        delivery.setDeliveryId(103L);
        delivery.setUserId(userId);
        delivery.setChatId(7890L);
        delivery.setFirmId(firmId);
        delivery.setDocumentType("STMT_CUST_PDF");
        delivery.setDocumentId(customerId);
        delivery.setDeliveryState("QUEUED");

        when(securityManager.isUserAuthorized(userId)).thenReturn(true);
        when(statementService.generateCustomerStatementPdf(eq(firmId), eq(customerId), any(), any()))
                .thenReturn(new byte[]{4, 5, 6});

        deliveryService.processSingleDelivery(delivery, "MOCK-BOT-TOKEN");

        assertEquals("SENT", delivery.getDeliveryState());
        verify(apiClient, times(1)).sendDocument(eq("MOCK-BOT-TOKEN"), eq(7890L), any(byte[].class), eq("Customer_Statement_55.pdf"), anyString());
    }
}
