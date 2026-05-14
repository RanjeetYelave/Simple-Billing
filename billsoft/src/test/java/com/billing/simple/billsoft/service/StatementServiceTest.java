package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dtos.CustomerStatementResponse;
import com.billing.simple.billsoft.dtos.FirmStatementResponse;
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.InvoiceStatus;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;
import com.billing.simple.billsoft.repo.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StatementServiceTest {

    @Mock
    private InvoiceRepository invoiceRepo;
    @Mock
    private CustomerRepository customerRepo;
    @Mock
    private FirmDetailsRepository firmRepo;

    @InjectMocks
    private StatementServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetCustomerStatement() {
        Long firmId = 1L;
        Long customerId = 1L;
        Customer customer = new Customer();
        customer.setName("Test Customer");
        when(customerRepo.findById(customerId)).thenReturn(Optional.of(customer));

        Invoice inv = new Invoice();
        inv.setInvoiceNumber("INV-001");
        inv.setInvoiceDate(LocalDateTime.now());
        inv.setTotalAmount(new BigDecimal("1000.00"));
        inv.setStatus(InvoiceStatus.FINAL);
        inv.setPaid(true);

        when(invoiceRepo.findByFirmIdAndCustomer_Id(firmId, customerId)).thenReturn(Arrays.asList(inv));

        CustomerStatementResponse result = service.getCustomerStatement(firmId, customerId, null, null);

        assertNotNull(result);
        assertEquals("Test Customer", result.getCustomerName());
        assertEquals(1000.0, result.getTotalBilled());
        assertEquals(1000.0, result.getTotalPaid());
        assertEquals(0.0, result.getClosingBalance());
        assertEquals(2, result.getEntries().size()); // 1 invoice + 1 payment
    }

    @Test
    void testGetFirmStatement() {
        Long firmId = 1L;
        LocalDate from = LocalDate.now().minusMonths(1);
        LocalDate to = LocalDate.now();

        Invoice inv = new Invoice();
        inv.setInvoiceNumber("INV-001");
        inv.setInvoiceDate(LocalDateTime.now());
        inv.setTotalAmount(new BigDecimal("5000.00"));
        inv.setTotalTax(new BigDecimal("500.00"));
        inv.setStatus(InvoiceStatus.FINAL);
        inv.setPaid(false);
        inv.setItems(new ArrayList<>());

        when(invoiceRepo.findAllByFirmIdAndInvoiceDateBetweenOrderByInvoiceDateAsc(any(), any(), any()))
                .thenReturn(Arrays.asList(inv));

        FirmStatementResponse result = service.getFirmStatement(firmId, from, to);

        assertNotNull(result);
        assertEquals(5000.0, result.getTotalBilled());
        assertEquals(0.0, result.getTotalPaid());
        assertEquals(500.0, result.getTotalTax());
    }

    @Test
    void testGenerateCustomerStatementPdf() throws Exception {
        Long firmId = 1L;
        Long customerId = 1L;
        Customer customer = new Customer();
        customer.setName("Test Customer");
        when(customerRepo.findById(customerId)).thenReturn(Optional.of(customer));
        when(firmRepo.findById(firmId)).thenReturn(Optional.of(new FirmDetails()));
        
        Invoice inv = new Invoice();
        inv.setInvoiceDate(LocalDateTime.now());
        inv.setTotalAmount(BigDecimal.TEN);
        when(invoiceRepo.findByFirmIdAndCustomer_Id(any(), any())).thenReturn(Arrays.asList(inv));

        byte[] pdf = service.generateCustomerStatementPdf(firmId, customerId, null, null);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    void testGenerateFirmStatementPdf() throws Exception {
        Long firmId = 1L;
        when(firmRepo.findById(firmId)).thenReturn(Optional.of(new FirmDetails()));
        
        Invoice inv = new Invoice();
        inv.setInvoiceDate(LocalDateTime.now());
        inv.setTotalAmount(BigDecimal.TEN);
        inv.setItems(new ArrayList<>());
        when(invoiceRepo.findAllByFirmIdAndInvoiceDateBetweenOrderByInvoiceDateAsc(any(), any(), any()))
                .thenReturn(Arrays.asList(inv));

        byte[] pdf = service.generateFirmStatementPdf(firmId, null, null);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }
}
