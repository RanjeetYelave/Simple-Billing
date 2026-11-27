package com.billing.simple.billsoft.telegram;

import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.service.InvoicePdfService;
import com.billing.simple.billsoft.service.InvoiceService;
import com.billing.simple.billsoft.service.PurchaseOrderService;
import com.billing.simple.billsoft.telegram.client.TelegramApiClient;
import com.billing.simple.billsoft.telegram.entities.TelegramDocumentDelivery;
import com.billing.simple.billsoft.telegram.repo.TelegramConfigRepository;
import com.billing.simple.billsoft.telegram.repo.TelegramDocumentDeliveryRepository;
import com.billing.simple.billsoft.telegram.service.TelegramDeliveryService;
import com.billing.simple.billsoft.telegram.service.TelegramSecurityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
    private TelegramConfigRepository configRepo;

    private TelegramDeliveryService deliveryService;

    @BeforeEach
    void setUp() {
        deliveryService = new TelegramDeliveryService(
                deliveryRepo, securityManager, apiClient, invoiceService, invoicePdfService, poService, configRepo
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

        Invoice mockInvoice = new Invoice();
        mockInvoice.setId(invoiceId);
        mockInvoice.setFirmId(firmId);
        mockInvoice.setInvoiceNumber("INV-2026-001");
        mockInvoice.setTotalAmount(BigDecimal.valueOf(1500.00));
        Customer c = new Customer();
        c.setName("Test Customer");
        mockInvoice.setCustomer(c);

        when(invoiceService.getById(invoiceId)).thenReturn(mockInvoice);
        byte[] fakePdf = "%PDF-1.4 mock".getBytes();
        when(invoicePdfService.generatePdf(mockInvoice, "A4")).thenReturn(fakePdf);

        deliveryService.processSingleDelivery(delivery, "MOCK-BOT-TOKEN");

        verify(apiClient).sendDocument(eq("MOCK-BOT-TOKEN"), eq(7890L), eq(fakePdf), eq("Invoice_INV-2026-001.pdf"), anyString());
        assertEquals("SENT", delivery.getDeliveryState());
        assertNotNull(delivery.getCompletedAt());
    }

    @Test
    void testCrossFirmMismatchAbortsDelivery() {
        Long userId = 123456L;
        TelegramDocumentDelivery delivery = new TelegramDocumentDelivery();
        delivery.setDeliveryId(103L);
        delivery.setUserId(userId);
        delivery.setChatId(7890L);
        delivery.setFirmId(1L); // Queued for Firm 1
        delivery.setDocumentType("INVOICE_PDF");
        delivery.setDocumentId(500L);
        delivery.setDeliveryState("QUEUED");

        when(securityManager.isUserAuthorized(userId)).thenReturn(true);

        Invoice mockInvoice = new Invoice();
        mockInvoice.setId(500L);
        mockInvoice.setFirmId(2L); // Belongs to Firm 2!
        when(invoiceService.getById(500L)).thenReturn(mockInvoice);

        deliveryService.processSingleDelivery(delivery, "MOCK-BOT-TOKEN");

        assertEquals("FAILED", delivery.getDeliveryState());
        assertTrue(delivery.getLastError().contains("cross-firm mismatch"));
        verifyNoInteractions(invoicePdfService);
        verifyNoInteractions(apiClient);
    }
}
