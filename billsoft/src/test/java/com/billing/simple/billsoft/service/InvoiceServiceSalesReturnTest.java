package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dtos.SalesReturnRequest;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InvoiceServiceSalesReturnTest {

    @Mock
    private InvoiceRepository invoiceRepo;
    @Mock
    private SalesReturnRepository salesReturnRepo;
    @Mock
    private ProductRepository productRepo;
    @Mock
    private StockMovementRepository stockMovementRepo;
    @Mock
    private CustomerRepository customerRepo;
    @Mock
    private FirmDetailsRepository firmRepo;
    @Mock
    private ProductService productService;
    @Mock
    private AppConfigRepository appConfigRepo;
    @Mock
    private SalesReturnItemRepository salesReturnItemRepo;

    @InjectMocks
    private InvoiceService invoiceService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCancelInvoiceRestoresStock() {
        Long invoiceId = 100L;
        Product product = new Product();
        product.setId(1L);
        product.setName("Test Product");
        product.setStockQuantity(BigDecimal.valueOf(10));

        InvoiceItem item = new InvoiceItem();
        item.setId(10L);
        item.setProduct(product);
        item.setQty(5);
        item.setPricePerUnit(BigDecimal.valueOf(100));

        Invoice invoice = new Invoice();
        invoice.setId(invoiceId);
        invoice.setFirmId(1L);
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaid(true);
        invoice.setItems(Collections.singletonList(item));

        when(invoiceRepo.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceRepo.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Invoice updated = invoiceService.updateStatus(invoiceId, InvoiceStatus.CANCELLED);

        assertEquals(InvoiceStatus.CANCELLED, updated.getStatus());
        assertFalse(updated.getPaid());
        verify(productService, times(1)).recordStockMovement(
                eq(1L), eq(1L), eq("INVOICE_CANCELLED"), eq(BigDecimal.valueOf(5)), any(), any(), any());
    }

    @Test
    void testCreateSalesReturnPartial() {
        Long invoiceId = 200L;
        Customer customer = new Customer();
        customer.setId(5L);
        customer.setName("Acme Corp");

        Product product = new Product();
        product.setId(2L);
        product.setName("Widget A");
        product.setStockQuantity(BigDecimal.valueOf(20));

        InvoiceItem item = new InvoiceItem();
        item.setId(20L);
        item.setProduct(product);
        item.setQty(10);
        item.setPricePerUnit(BigDecimal.valueOf(50));
        item.setGstPercent(BigDecimal.valueOf(18));

        Invoice invoice = new Invoice();
        invoice.setId(invoiceId);
        invoice.setCustomer(customer);
        invoice.setFirmId(1L);
        invoice.setItems(Collections.singletonList(item));

        when(invoiceRepo.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(salesReturnRepo.save(any(SalesReturn.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SalesReturnRequest request = new SalesReturnRequest();
        request.setReturnDate(LocalDate.now());
        request.setReason("Goods Damaged");
        request.setRefundMode("CASH");

        SalesReturnRequest.SalesReturnItemRequest itemReq = new SalesReturnRequest.SalesReturnItemRequest();
        itemReq.setInvoiceItemId(20L);
        itemReq.setProductId(2L);
        itemReq.setReturnQty(4);
        itemReq.setUnitPrice(BigDecimal.valueOf(50));
        itemReq.setGstPercent(BigDecimal.valueOf(18));

        request.setItems(Collections.singletonList(itemReq));

        SalesReturn salesReturn = invoiceService.createSalesReturn(invoiceId, request);

        assertNotNull(salesReturn);
        assertNotNull(salesReturn.getReturnNumber());
        assertEquals(1, salesReturn.getItems().size());
        assertEquals(0, salesReturn.getSubtotal().compareTo(BigDecimal.valueOf(200))); // 4 * 50
        assertEquals(0, salesReturn.getTaxAmount().compareTo(BigDecimal.valueOf(36))); // 200 * 0.18
        assertEquals(0, salesReturn.getTotalRefundAmount().compareTo(BigDecimal.valueOf(236)));

        // Verify stock movement recording
        verify(productService, times(1)).recordStockMovement(
                eq(2L), eq(1L), eq("SALE_RETURN"), eq(BigDecimal.valueOf(4)), any(), any(), any());
    }

    @Test
    void testCreateSalesReturnNullFirmIdAndCustomNumber() {
        Long invoiceId = 201L;
        Invoice invoice = new Invoice();
        invoice.setId(invoiceId);
        invoice.setFirmId(null); // null firmId
        invoice.setStatus(InvoiceStatus.FINAL);

        InvoiceItem item = new InvoiceItem();
        item.setId(21L);
        item.setQty(5);
        item.setPricePerUnit(BigDecimal.valueOf(100));
        invoice.setItems(Collections.singletonList(item));

        when(invoiceRepo.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(salesReturnRepo.save(any(SalesReturn.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SalesReturnRequest request = new SalesReturnRequest();
        request.setReturnNumber("RET-CUSTOM-999");
        request.setItems(Collections.singletonList(
                SalesReturnRequest.SalesReturnItemRequest.builder()
                        .invoiceItemId(21L)
                        .returnQty(2)
                        .unitPrice(BigDecimal.valueOf(100))
                        .build()));

        SalesReturn salesReturn = invoiceService.createSalesReturn(invoiceId, request);
        assertNotNull(salesReturn);
        assertEquals("RET-CUSTOM-999", salesReturn.getReturnNumber());
        assertEquals(1L, salesReturn.getFirmId());
        assertEquals(0, salesReturn.getTotalRefundAmount().compareTo(BigDecimal.valueOf(200)));
    }

    @Test
    void testCreateSalesReturnZeroQtyThrows() {
        Long invoiceId = 202L;
        Invoice invoice = new Invoice();
        invoice.setId(invoiceId);
        invoice.setStatus(InvoiceStatus.FINAL);
        when(invoiceRepo.findById(invoiceId)).thenReturn(Optional.of(invoice));

        SalesReturnRequest request = new SalesReturnRequest();
        request.setItems(Collections.singletonList(
                SalesReturnRequest.SalesReturnItemRequest.builder()
                        .invoiceItemId(1L)
                        .returnQty(0)
                        .build()));

        assertThrows(IllegalArgumentException.class, () -> invoiceService.createSalesReturn(invoiceId, request));
    }

    @Test
    void testUpdateFullInvoiceNullifiesSalesReturnItemReferences() {
        Long invoiceId = 300L;
        Invoice invoice = new Invoice();
        invoice.setId(invoiceId);
        invoice.setFirmId(1L);
        invoice.setStatus(InvoiceStatus.FINAL);

        Customer customer = new Customer();
        customer.setId(10L);
        customer.setName("Test Customer");
        invoice.setCustomer(customer);

        InvoiceItem item = new InvoiceItem();
        item.setId(50L);
        item.setQty(2);
        item.setPricePerUnit(BigDecimal.valueOf(100));
        invoice.setItems(new java.util.ArrayList<>(Collections.singletonList(item)));

        when(invoiceRepo.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(customerRepo.findById(10L)).thenReturn(Optional.of(customer));
        when(customerRepo.findByIdAndFirmId(10L, 1L)).thenReturn(Optional.of(customer));
        when(invoiceRepo.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        com.billing.simple.billsoft.dtos.InvoiceUpdateRequest updateReq = new com.billing.simple.billsoft.dtos.InvoiceUpdateRequest();
        updateReq.setCustomerId(10L);

        Invoice updated = invoiceService.updateFullInvoice(invoiceId, updateReq);

        assertNotNull(updated);
        verify(salesReturnItemRepo, times(1)).nullifyInvoiceItemReferencesByInvoiceId(invoiceId);
    }

    @Test
    void testCreateSalesReturnWithExcludeTax() {
        Long invoiceId = 203L;
        Invoice invoice = new Invoice();
        invoice.setId(invoiceId);
        invoice.setStatus(InvoiceStatus.FINAL);
        invoice.setFirmId(1L);

        InvoiceItem item = new InvoiceItem();
        item.setId(31L);
        item.setQty(10);
        item.setPricePerUnit(BigDecimal.valueOf(100));
        item.setGstPercent(BigDecimal.valueOf(18));
        invoice.setItems(Collections.singletonList(item));

        when(invoiceRepo.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(salesReturnRepo.save(any(SalesReturn.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SalesReturnRequest request = SalesReturnRequest.builder()
                .excludeTax(true)
                .items(Collections.singletonList(
                        SalesReturnRequest.SalesReturnItemRequest.builder()
                                .invoiceItemId(31L)
                                .returnQty(2)
                                .unitPrice(BigDecimal.valueOf(100))
                                .gstPercent(BigDecimal.valueOf(18))
                                .build()))
                .build();

        SalesReturn salesReturn = invoiceService.createSalesReturn(invoiceId, request);
        assertNotNull(salesReturn);
        assertEquals(0, salesReturn.getSubtotal().compareTo(BigDecimal.valueOf(200)));
        assertEquals(0, salesReturn.getTaxAmount().compareTo(BigDecimal.ZERO)); // Tax excluded
        assertEquals(0, salesReturn.getExcludedTaxAmount().compareTo(BigDecimal.valueOf(36))); // 18% of 200 = 36 excluded
        assertEquals(0, salesReturn.getTotalRefundAmount().compareTo(BigDecimal.valueOf(200))); // Only base price refunded
    }

    @Test
    void testCreateSalesReturnWithPenalty() {
        Long invoiceId = 204L;
        Invoice invoice = new Invoice();
        invoice.setId(invoiceId);
        invoice.setStatus(InvoiceStatus.FINAL);
        invoice.setFirmId(1L);

        InvoiceItem item = new InvoiceItem();
        item.setId(32L);
        item.setQty(5);
        item.setPricePerUnit(BigDecimal.valueOf(200));
        item.setGstPercent(BigDecimal.valueOf(18));
        invoice.setItems(Collections.singletonList(item));

        when(invoiceRepo.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(salesReturnRepo.save(any(SalesReturn.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SalesReturnRequest request = SalesReturnRequest.builder()
                .penaltyAmount(BigDecimal.valueOf(50))
                .penaltyReason("Restocking Fee")
                .items(Collections.singletonList(
                        SalesReturnRequest.SalesReturnItemRequest.builder()
                                .invoiceItemId(32L)
                                .returnQty(2)
                                .unitPrice(BigDecimal.valueOf(200))
                                .gstPercent(BigDecimal.valueOf(18))
                                .build()))
                .build();

        // subtotal = 400, tax = 72, gross = 472, penalty = 50 -> net = 422
        SalesReturn salesReturn = invoiceService.createSalesReturn(invoiceId, request);
        assertNotNull(salesReturn);
        assertEquals(0, salesReturn.getSubtotal().compareTo(BigDecimal.valueOf(400)));
        assertEquals(0, salesReturn.getTaxAmount().compareTo(BigDecimal.valueOf(72)));
        assertEquals(0, salesReturn.getPenaltyAmount().compareTo(BigDecimal.valueOf(50)));
        assertEquals("Restocking Fee", salesReturn.getPenaltyReason());
        assertEquals(0, salesReturn.getTotalRefundAmount().compareTo(BigDecimal.valueOf(422)));
    }

    @Test
    void testCreateSalesReturnWithExcessPenaltyBoundsToZero() {
        Long invoiceId = 205L;
        Invoice invoice = new Invoice();
        invoice.setId(invoiceId);
        invoice.setStatus(InvoiceStatus.FINAL);
        invoice.setFirmId(1L);

        InvoiceItem item = new InvoiceItem();
        item.setId(33L);
        item.setQty(1);
        item.setPricePerUnit(BigDecimal.valueOf(100));
        item.setGstPercent(BigDecimal.ZERO);
        invoice.setItems(Collections.singletonList(item));

        when(invoiceRepo.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(salesReturnRepo.save(any(SalesReturn.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SalesReturnRequest request = SalesReturnRequest.builder()
                .penaltyAmount(BigDecimal.valueOf(500)) // exceeds gross total of 100
                .penaltyReason("Severe Damage Fee")
                .items(Collections.singletonList(
                        SalesReturnRequest.SalesReturnItemRequest.builder()
                                .invoiceItemId(33L)
                                .returnQty(1)
                                .unitPrice(BigDecimal.valueOf(100))
                                .build()))
                .build();

        SalesReturn salesReturn = invoiceService.createSalesReturn(invoiceId, request);
        assertNotNull(salesReturn);
        assertEquals(0, salesReturn.getTotalRefundAmount().compareTo(BigDecimal.ZERO));
    }
}
