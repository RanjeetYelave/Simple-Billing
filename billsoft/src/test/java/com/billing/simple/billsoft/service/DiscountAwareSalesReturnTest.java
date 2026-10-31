package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dtos.SalesReturnRequest;
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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DiscountAwareSalesReturnTest {

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

    private Long firmId = 1L;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TenantContext.setCurrentFirmId(firmId);
        when(salesReturnRepo.save(any(SalesReturn.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Invoice createMockInvoice(Long invoiceId, List<InvoiceItem> items, boolean isInterState) {
        Invoice invoice = new Invoice();
        invoice.setId(invoiceId);
        invoice.setFirmId(firmId);
        invoice.setStatus(InvoiceStatus.FINAL);
        invoice.setInvoiceNumber("INV-" + invoiceId);
        invoice.setItems(items);
        if (isInterState) {
            invoice.setIgstAmount(BigDecimal.valueOf(100)); // mark as inter-state
        } else {
            invoice.setCgstAmount(BigDecimal.valueOf(50));
            invoice.setSgstAmount(BigDecimal.valueOf(50));
        }

        when(invoiceRepo.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceRepo.findByIdAndFirmId(invoiceId, firmId)).thenReturn(Optional.of(invoice));
        when(invoiceRepo.findWithLockById(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceRepo.findWithLockByIdAndFirmId(invoiceId, firmId)).thenReturn(Optional.of(invoice));
        when(salesReturnItemRepo.sumReturnedQtyByInvoiceItemIdForInvoice(invoiceId)).thenReturn(Collections.emptyList());
        when(salesReturnItemRepo.sumReturnedQtyByProductIdForInvoice(invoiceId)).thenReturn(Collections.emptyList());
        when(salesReturnItemRepo.sumAllocatedAmountsByInvoiceItemIdForInvoice(invoiceId)).thenReturn(Collections.emptyList());
        return invoice;
    }

    private InvoiceItem createMockItem(Long itemId, String name, int qty, BigDecimal price,
                                       BigDecimal prodDisc, BigDecimal taxable, BigDecimal gstPct, BigDecimal gstAmt) {
        Product prod = new Product();
        prod.setId(itemId * 10);
        prod.setName(name);
        prod.setStockQuantity(BigDecimal.valueOf(100));

        InvoiceItem item = new InvoiceItem();
        item.setId(itemId);
        item.setProduct(prod);
        item.setProductName(name);
        item.setQty(qty);
        item.setPricePerUnit(price);
        item.setAmountWithoutTax(price.multiply(BigDecimal.valueOf(qty)));
        item.setDiscountValue(prodDisc);
        item.setTaxableAmount(taxable);
        item.setGstPercent(gstPct);
        item.setGstAmount(gstAmt);
        item.setLineTotal(taxable.add(gstAmt));
        return item;
    }

    // 1. Backward compatibility: checkbox false preserves existing behavior
    @Test
    void testLegacyBehaviorWhenFlagIsFalse() {
        Long invoiceId = 101L;
        // Item: 10 units @ 100 = 1000 gross. Discount 100. Taxable = 900. GST 18% = 162.
        InvoiceItem item = createMockItem(1L, "Widget", 10, BigDecimal.valueOf(100),
                BigDecimal.valueOf(100), BigDecimal.valueOf(900), BigDecimal.valueOf(18), BigDecimal.valueOf(162));
        createMockInvoice(invoiceId, Collections.singletonList(item), false);

        SalesReturnRequest req = SalesReturnRequest.builder()
                .includeOriginalDiscounts(false)
                .items(Collections.singletonList(
                        SalesReturnRequest.SalesReturnItemRequest.builder()
                                .invoiceItemId(1L)
                                .returnQty(5)
                                .unitPrice(BigDecimal.valueOf(100))
                                .gstPercent(BigDecimal.valueOf(18))
                                .build()))
                .build();

        SalesReturn sr = invoiceService.createSalesReturn(invoiceId, req);

        assertNotNull(sr);
        assertFalse(sr.getIncludeDiscounts());
        assertEquals(0, sr.getGrossAmount().compareTo(BigDecimal.ZERO));
        assertEquals(0, sr.getProductDiscountAmount().compareTo(BigDecimal.ZERO));
        assertEquals(0, sr.getInvoiceDiscountAmount().compareTo(BigDecimal.ZERO));
        // In legacy mode, effectiveUnitPrice = 900 / 10 = 90. 5 units * 90 = 450.
        assertEquals(0, sr.getSubtotal().compareTo(BigDecimal.valueOf(450.00).setScale(2)));
        assertEquals(0, sr.getTaxAmount().compareTo(BigDecimal.valueOf(81.00).setScale(2)));
        assertEquals(0, sr.getTotalRefundAmount().compareTo(BigDecimal.valueOf(531.00).setScale(2)));
    }

    // 2. Backward compatibility: missing/null flag preserves existing behavior
    @Test
    void testLegacyBehaviorWhenFlagIsNull() {
        Long invoiceId = 102L;
        InvoiceItem item = createMockItem(1L, "Widget", 10, BigDecimal.valueOf(100),
                BigDecimal.valueOf(100), BigDecimal.valueOf(900), BigDecimal.valueOf(18), BigDecimal.valueOf(162));
        createMockInvoice(invoiceId, Collections.singletonList(item), false);

        SalesReturnRequest req = SalesReturnRequest.builder()
                .includeOriginalDiscounts(null)
                .items(Collections.singletonList(
                        SalesReturnRequest.SalesReturnItemRequest.builder()
                                .invoiceItemId(1L)
                                .returnQty(10)
                                .build()))
                .build();

        SalesReturn sr = invoiceService.createSalesReturn(invoiceId, req);

        assertNotNull(sr);
        assertFalse(sr.getIncludeDiscounts());
        assertEquals(0, sr.getSubtotal().compareTo(BigDecimal.valueOf(900.00).setScale(2)));
        assertEquals(0, sr.getTaxAmount().compareTo(BigDecimal.valueOf(162.00).setScale(2)));
        assertEquals(0, sr.getTotalRefundAmount().compareTo(BigDecimal.valueOf(1062.00).setScale(2)));
    }

    // 5. Product-level percentage discount, full return
    @Test
    void testProductLevelDiscountFullReturn() {
        Long invoiceId = 105L;
        // 10 units @ 100 = 1000. 10% discount = 100. Taxable = 900. GST 18% = 162.
        InvoiceItem item = createMockItem(1L, "Widget", 10, BigDecimal.valueOf(100),
                BigDecimal.valueOf(100), BigDecimal.valueOf(900), BigDecimal.valueOf(18), BigDecimal.valueOf(162));
        createMockInvoice(invoiceId, Collections.singletonList(item), false);

        SalesReturnRequest req = SalesReturnRequest.builder()
                .includeOriginalDiscounts(true)
                .items(Collections.singletonList(
                        SalesReturnRequest.SalesReturnItemRequest.builder()
                                .invoiceItemId(1L)
                                .returnQty(10)
                                .build()))
                .build();

        SalesReturn sr = invoiceService.createSalesReturn(invoiceId, req);

        assertTrue(sr.getIncludeDiscounts());
        assertEquals(0, sr.getGrossAmount().compareTo(BigDecimal.valueOf(1000.00).setScale(2)));
        assertEquals(0, sr.getProductDiscountAmount().compareTo(BigDecimal.valueOf(100.00).setScale(2)));
        assertEquals(0, sr.getInvoiceDiscountAmount().compareTo(BigDecimal.ZERO.setScale(2)));
        assertEquals(0, sr.getSubtotal().compareTo(BigDecimal.valueOf(900.00).setScale(2)));
        assertEquals(0, sr.getTaxAmount().compareTo(BigDecimal.valueOf(162.00).setScale(2)));
        assertEquals(0, sr.getTotalRefundAmount().compareTo(BigDecimal.valueOf(1062.00).setScale(2)));
    }

    // 6. Product-level fixed discount, full return
    @Test
    void testProductLevelFixedDiscountFullReturn() {
        Long invoiceId = 106L;
        // 5 units @ 200 = 1000. Fixed discount = 150. Taxable = 850. GST 12% = 102.
        InvoiceItem item = createMockItem(1L, "Gadget", 5, BigDecimal.valueOf(200),
                BigDecimal.valueOf(150), BigDecimal.valueOf(850), BigDecimal.valueOf(12), BigDecimal.valueOf(102));
        createMockInvoice(invoiceId, Collections.singletonList(item), false);

        SalesReturnRequest req = SalesReturnRequest.builder()
                .includeOriginalDiscounts(true)
                .items(Collections.singletonList(
                        SalesReturnRequest.SalesReturnItemRequest.builder()
                                .invoiceItemId(1L)
                                .returnQty(5)
                                .build()))
                .build();

        SalesReturn sr = invoiceService.createSalesReturn(invoiceId, req);

        assertTrue(sr.getIncludeDiscounts());
        assertEquals(0, sr.getGrossAmount().compareTo(BigDecimal.valueOf(1000.00).setScale(2)));
        assertEquals(0, sr.getProductDiscountAmount().compareTo(BigDecimal.valueOf(150.00).setScale(2)));
        assertEquals(0, sr.getInvoiceDiscountAmount().compareTo(BigDecimal.ZERO.setScale(2)));
        assertEquals(0, sr.getSubtotal().compareTo(BigDecimal.valueOf(850.00).setScale(2)));
        assertEquals(0, sr.getTaxAmount().compareTo(BigDecimal.valueOf(102.00).setScale(2)));
        assertEquals(0, sr.getTotalRefundAmount().compareTo(BigDecimal.valueOf(952.00).setScale(2)));
    }

    // 7. Product-level discount, partial return
    @Test
    void testProductLevelDiscountPartialReturn() {
        Long invoiceId = 107L;
        // 10 units @ 100 = 1000. Discount = 100. Taxable = 900. GST 18% = 162.
        InvoiceItem item = createMockItem(1L, "Widget", 10, BigDecimal.valueOf(100),
                BigDecimal.valueOf(100), BigDecimal.valueOf(900), BigDecimal.valueOf(18), BigDecimal.valueOf(162));
        createMockInvoice(invoiceId, Collections.singletonList(item), false);

        // Return 4 units
        SalesReturnRequest req = SalesReturnRequest.builder()
                .includeOriginalDiscounts(true)
                .items(Collections.singletonList(
                        SalesReturnRequest.SalesReturnItemRequest.builder()
                                .invoiceItemId(1L)
                                .returnQty(4)
                                .build()))
                .build();

        SalesReturn sr = invoiceService.createSalesReturn(invoiceId, req);

        assertTrue(sr.getIncludeDiscounts());
        assertEquals(0, sr.getGrossAmount().compareTo(BigDecimal.valueOf(400.00).setScale(2)));
        assertEquals(0, sr.getProductDiscountAmount().compareTo(BigDecimal.valueOf(40.00).setScale(2)));
        assertEquals(0, sr.getInvoiceDiscountAmount().compareTo(BigDecimal.ZERO.setScale(2)));
        assertEquals(0, sr.getSubtotal().compareTo(BigDecimal.valueOf(360.00).setScale(2)));
        assertEquals(0, sr.getTaxAmount().compareTo(BigDecimal.valueOf(64.80).setScale(2)));
        assertEquals(0, sr.getTotalRefundAmount().compareTo(BigDecimal.valueOf(424.80).setScale(2)));
    }

    // 8 & 9. Invoice-level discount, full return
    @Test
    void testInvoiceLevelDiscountFullReturn() {
        Long invoiceId = 108L;
        // 5 units @ 100 = 500 gross. Product disc = 0. Invoice disc allocated = 50. Taxable = 450. GST 18% = 81.
        InvoiceItem item = createMockItem(1L, "Widget", 5, BigDecimal.valueOf(100),
                BigDecimal.ZERO, BigDecimal.valueOf(450), BigDecimal.valueOf(18), BigDecimal.valueOf(81));
        createMockInvoice(invoiceId, Collections.singletonList(item), false);

        SalesReturnRequest req = SalesReturnRequest.builder()
                .includeOriginalDiscounts(true)
                .items(Collections.singletonList(
                        SalesReturnRequest.SalesReturnItemRequest.builder()
                                .invoiceItemId(1L)
                                .returnQty(5)
                                .build()))
                .build();

        SalesReturn sr = invoiceService.createSalesReturn(invoiceId, req);

        assertTrue(sr.getIncludeDiscounts());
        assertEquals(0, sr.getGrossAmount().compareTo(BigDecimal.valueOf(500.00).setScale(2)));
        assertEquals(0, sr.getProductDiscountAmount().compareTo(BigDecimal.ZERO.setScale(2)));
        assertEquals(0, sr.getInvoiceDiscountAmount().compareTo(BigDecimal.valueOf(50.00).setScale(2)));
        assertEquals(0, sr.getSubtotal().compareTo(BigDecimal.valueOf(450.00).setScale(2)));
        assertEquals(0, sr.getTaxAmount().compareTo(BigDecimal.valueOf(81.00).setScale(2)));
        assertEquals(0, sr.getTotalRefundAmount().compareTo(BigDecimal.valueOf(531.00).setScale(2)));
    }

    // 10. Invoice-level discount, partial return
    @Test
    void testInvoiceLevelDiscountPartialReturn() {
        Long invoiceId = 110L;
        // 10 units @ 100 = 1000 gross. Inv disc = 200. Taxable = 800. GST 18% = 144.
        InvoiceItem item = createMockItem(1L, "Widget", 10, BigDecimal.valueOf(100),
                BigDecimal.ZERO, BigDecimal.valueOf(800), BigDecimal.valueOf(18), BigDecimal.valueOf(144));
        createMockInvoice(invoiceId, Collections.singletonList(item), false);

        // Return 3 units
        SalesReturnRequest req = SalesReturnRequest.builder()
                .includeOriginalDiscounts(true)
                .items(Collections.singletonList(
                        SalesReturnRequest.SalesReturnItemRequest.builder()
                                .invoiceItemId(1L)
                                .returnQty(3)
                                .build()))
                .build();

        SalesReturn sr = invoiceService.createSalesReturn(invoiceId, req);

        assertTrue(sr.getIncludeDiscounts());
        assertEquals(0, sr.getGrossAmount().compareTo(BigDecimal.valueOf(300.00).setScale(2)));
        assertEquals(0, sr.getProductDiscountAmount().compareTo(BigDecimal.ZERO.setScale(2)));
        assertEquals(0, sr.getInvoiceDiscountAmount().compareTo(BigDecimal.valueOf(60.00).setScale(2)));
        assertEquals(0, sr.getSubtotal().compareTo(BigDecimal.valueOf(240.00).setScale(2)));
        assertEquals(0, sr.getTaxAmount().compareTo(BigDecimal.valueOf(43.20).setScale(2)));
        assertEquals(0, sr.getTotalRefundAmount().compareTo(BigDecimal.valueOf(283.20).setScale(2)));
    }

    // 11. Both discount levels applied to original invoice
    @Test
    void testBothDiscountLevelsAppliedFullAndPartialReturn() {
        Long invoiceId = 111L;
        // 10 units @ 100 = 1000 gross.
        // Product disc = 100. Taxable after prod disc = 900.
        // Invoice disc = 90. Final taxable = 810. GST 18% = 145.80.
        InvoiceItem item = createMockItem(1L, "Widget", 10, BigDecimal.valueOf(100),
                BigDecimal.valueOf(100), BigDecimal.valueOf(810), BigDecimal.valueOf(18), BigDecimal.valueOf(145.80));
        createMockInvoice(invoiceId, Collections.singletonList(item), false);

        // First partial return: 5 units
        SalesReturnRequest req = SalesReturnRequest.builder()
                .includeOriginalDiscounts(true)
                .items(Collections.singletonList(
                        SalesReturnRequest.SalesReturnItemRequest.builder()
                                .invoiceItemId(1L)
                                .returnQty(5)
                                .build()))
                .build();

        SalesReturn sr = invoiceService.createSalesReturn(invoiceId, req);

        assertTrue(sr.getIncludeDiscounts());
        assertEquals(0, sr.getGrossAmount().compareTo(BigDecimal.valueOf(500.00).setScale(2)));
        assertEquals(0, sr.getProductDiscountAmount().compareTo(BigDecimal.valueOf(50.00).setScale(2)));
        assertEquals(0, sr.getInvoiceDiscountAmount().compareTo(BigDecimal.valueOf(45.00).setScale(2)));
        assertEquals(0, sr.getSubtotal().compareTo(BigDecimal.valueOf(405.00).setScale(2)));
        assertEquals(0, sr.getTaxAmount().compareTo(BigDecimal.valueOf(72.90).setScale(2)));
        assertEquals(0, sr.getTotalRefundAmount().compareTo(BigDecimal.valueOf(477.90).setScale(2)));
    }

    // 12. No-discount invoice
    @Test
    void testNoDiscountInvoice() {
        Long invoiceId = 112L;
        // 5 units @ 100 = 500 gross. Disc = 0. Taxable = 500. GST 18% = 90.
        InvoiceItem item = createMockItem(1L, "Widget", 5, BigDecimal.valueOf(100),
                BigDecimal.ZERO, BigDecimal.valueOf(500), BigDecimal.valueOf(18), BigDecimal.valueOf(90));
        createMockInvoice(invoiceId, Collections.singletonList(item), false);

        SalesReturnRequest req = SalesReturnRequest.builder()
                .includeOriginalDiscounts(true)
                .items(Collections.singletonList(
                        SalesReturnRequest.SalesReturnItemRequest.builder()
                                .invoiceItemId(1L)
                                .returnQty(2)
                                .build()))
                .build();

        SalesReturn sr = invoiceService.createSalesReturn(invoiceId, req);

        assertTrue(sr.getIncludeDiscounts());
        assertEquals(0, sr.getGrossAmount().compareTo(BigDecimal.valueOf(200.00).setScale(2)));
        assertEquals(0, sr.getProductDiscountAmount().compareTo(BigDecimal.ZERO.setScale(2)));
        assertEquals(0, sr.getInvoiceDiscountAmount().compareTo(BigDecimal.ZERO.setScale(2)));
        assertEquals(0, sr.getSubtotal().compareTo(BigDecimal.valueOf(200.00).setScale(2)));
        assertEquals(0, sr.getTaxAmount().compareTo(BigDecimal.valueOf(36.00).setScale(2)));
        assertEquals(0, sr.getTotalRefundAmount().compareTo(BigDecimal.valueOf(236.00).setScale(2)));
    }

    // 13. Mixed GST rates within one return
    @Test
    void testMixedGSTRatesWithinOneReturn() {
        Long invoiceId = 113L;
        // Item 1: 5% GST, Item 2: 18% GST
        InvoiceItem item1 = createMockItem(1L, "Food", 2, BigDecimal.valueOf(100),
                BigDecimal.valueOf(20), BigDecimal.valueOf(180), BigDecimal.valueOf(5), BigDecimal.valueOf(9.00));
        InvoiceItem item2 = createMockItem(2L, "Stationery", 4, BigDecimal.valueOf(50),
                BigDecimal.valueOf(20), BigDecimal.valueOf(180), BigDecimal.valueOf(18), BigDecimal.valueOf(32.40));
        createMockInvoice(invoiceId, Arrays.asList(item1, item2), false);

        SalesReturnRequest req = SalesReturnRequest.builder()
                .includeOriginalDiscounts(true)
                .items(Arrays.asList(
                        SalesReturnRequest.SalesReturnItemRequest.builder().invoiceItemId(1L).returnQty(2).build(),
                        SalesReturnRequest.SalesReturnItemRequest.builder().invoiceItemId(2L).returnQty(4).build()
                ))
                .build();

        SalesReturn sr = invoiceService.createSalesReturn(invoiceId, req);

        assertTrue(sr.getIncludeDiscounts());
        // Gross = 200 + 200 = 400
        assertEquals(0, sr.getGrossAmount().compareTo(BigDecimal.valueOf(400.00).setScale(2)));
        // Prod disc = 20 + 20 = 40
        assertEquals(0, sr.getProductDiscountAmount().compareTo(BigDecimal.valueOf(40.00).setScale(2)));
        // Subtotal = 180 + 180 = 360
        assertEquals(0, sr.getSubtotal().compareTo(BigDecimal.valueOf(360.00).setScale(2)));
        // Tax = 9.00 + 32.40 = 41.40
        assertEquals(0, sr.getTaxAmount().compareTo(BigDecimal.valueOf(41.40).setScale(2)));
        assertEquals(0, sr.getTotalRefundAmount().compareTo(BigDecimal.valueOf(401.40).setScale(2)));
    }

    // 14. Intra-state CGST/SGST vs Inter-state IGST
    @Test
    void testIntraStateAndInterStateGSTPlaceOfSupply() {
        // Intra-state test
        Long invIntraId = 114L;
        InvoiceItem itemIntra = createMockItem(1L, "Widget", 1, BigDecimal.valueOf(100),
                BigDecimal.ZERO, BigDecimal.valueOf(100), BigDecimal.valueOf(18), BigDecimal.valueOf(18.00));
        createMockInvoice(invIntraId, Collections.singletonList(itemIntra), false);

        SalesReturnRequest reqIntra = SalesReturnRequest.builder()
                .includeOriginalDiscounts(true)
                .items(Collections.singletonList(
                        SalesReturnRequest.SalesReturnItemRequest.builder().invoiceItemId(1L).returnQty(1).build()))
                .build();

        SalesReturn srIntra = invoiceService.createSalesReturn(invIntraId, reqIntra);
        assertEquals(0, srIntra.getCgstAmount().compareTo(BigDecimal.valueOf(9.00).setScale(2)));
        assertEquals(0, srIntra.getSgstAmount().compareTo(BigDecimal.valueOf(9.00).setScale(2)));
        assertEquals(0, srIntra.getIgstAmount().compareTo(BigDecimal.ZERO.setScale(2)));

        // Inter-state test
        Long invInterId = 115L;
        InvoiceItem itemInter = createMockItem(2L, "Widget Inter", 1, BigDecimal.valueOf(100),
                BigDecimal.ZERO, BigDecimal.valueOf(100), BigDecimal.valueOf(18), BigDecimal.valueOf(18.00));
        createMockInvoice(invInterId, Collections.singletonList(itemInter), true);

        SalesReturnRequest reqInter = SalesReturnRequest.builder()
                .includeOriginalDiscounts(true)
                .items(Collections.singletonList(
                        SalesReturnRequest.SalesReturnItemRequest.builder().invoiceItemId(2L).returnQty(1).build()))
                .build();

        SalesReturn srInter = invoiceService.createSalesReturn(invInterId, reqInter);
        assertEquals(0, srInter.getCgstAmount().compareTo(BigDecimal.ZERO.setScale(2)));
        assertEquals(0, srInter.getSgstAmount().compareTo(BigDecimal.ZERO.setScale(2)));
        assertEquals(0, srInter.getIgstAmount().compareTo(BigDecimal.valueOf(18.00).setScale(2)));
    }

    // 15. Fixed invoice discount with rounding residuals
    @Test
    void testFixedInvoiceDiscountRoundingResidualsAbsorption() {
        Long invoiceId = 116L;
        // Line 1: 1 unit @ 100, inv disc share = 33.33, taxable = 66.67
        InvoiceItem item1 = createMockItem(1L, "A", 1, BigDecimal.valueOf(100),
                BigDecimal.ZERO, BigDecimal.valueOf(66.67), BigDecimal.valueOf(18), BigDecimal.valueOf(12.00));
        // Line 2: 1 unit @ 100, inv disc share = 33.33, taxable = 66.67
        InvoiceItem item2 = createMockItem(2L, "B", 1, BigDecimal.valueOf(100),
                BigDecimal.ZERO, BigDecimal.valueOf(66.67), BigDecimal.valueOf(18), BigDecimal.valueOf(12.00));
        // Line 3: 1 unit @ 100, inv disc share = 33.34 (absorbed 1 paisa residual), taxable = 66.66
        InvoiceItem item3 = createMockItem(3L, "C", 1, BigDecimal.valueOf(100),
                BigDecimal.ZERO, BigDecimal.valueOf(66.66), BigDecimal.valueOf(18), BigDecimal.valueOf(12.00));
        createMockInvoice(invoiceId, Arrays.asList(item1, item2, item3), false);

        SalesReturnRequest req = SalesReturnRequest.builder()
                .includeOriginalDiscounts(true)
                .items(Arrays.asList(
                        SalesReturnRequest.SalesReturnItemRequest.builder().invoiceItemId(1L).returnQty(1).build(),
                        SalesReturnRequest.SalesReturnItemRequest.builder().invoiceItemId(2L).returnQty(1).build(),
                        SalesReturnRequest.SalesReturnItemRequest.builder().invoiceItemId(3L).returnQty(1).build()
                ))
                .build();

        SalesReturn sr = invoiceService.createSalesReturn(invoiceId, req);

        assertEquals(0, sr.getGrossAmount().compareTo(BigDecimal.valueOf(300.00).setScale(2)));
        // Total invoice discount = 33.33 + 33.33 + 33.34 = 100.00 exactly!
        assertEquals(0, sr.getInvoiceDiscountAmount().compareTo(BigDecimal.valueOf(100.00).setScale(2)));
        // Subtotal = 66.67 + 66.67 + 66.66 = 200.00 exactly!
        assertEquals(0, sr.getSubtotal().compareTo(BigDecimal.valueOf(200.00).setScale(2)));
    }

    // 17. Legacy invoices with null or inconsistent discount fields
    @Test
    void testLegacyInvoicesWithNullDiscountFieldsHandledGracefully() {
        Long invoiceId = 117L;
        InvoiceItem item = new InvoiceItem();
        item.setId(1L);
        item.setQty(5);
        item.setPricePerUnit(BigDecimal.valueOf(50));
        // Null amountWithoutTax, null discountValue, null taxableAmount
        createMockInvoice(invoiceId, Collections.singletonList(item), false);

        SalesReturnRequest req = SalesReturnRequest.builder()
                .includeOriginalDiscounts(true)
                .items(Collections.singletonList(
                        SalesReturnRequest.SalesReturnItemRequest.builder()
                                .invoiceItemId(1L)
                                .returnQty(2)
                                .build()))
                .build();

        SalesReturn sr = invoiceService.createSalesReturn(invoiceId, req);

        assertNotNull(sr);
        assertEquals(0, sr.getGrossAmount().compareTo(BigDecimal.valueOf(100.00).setScale(2)));
        assertEquals(0, sr.getProductDiscountAmount().compareTo(BigDecimal.ZERO.setScale(2)));
        assertEquals(0, sr.getInvoiceDiscountAmount().compareTo(BigDecimal.ZERO.setScale(2)));
        assertEquals(0, sr.getSubtotal().compareTo(BigDecimal.valueOf(100.00).setScale(2)));
    }

    // 18. Repeated partial returns until full quantity returned
    @Test
    void testRepeatedPartialReturnsReconciliation() {
        Long invoiceId = 118L;
        // 10 units @ 100 = 1000. Prod disc = 100. Inv disc = 100. Taxable = 800. GST 18% = 144. Total = 944.
        InvoiceItem item = createMockItem(1L, "Widget", 10, BigDecimal.valueOf(100),
                BigDecimal.valueOf(100), BigDecimal.valueOf(800), BigDecimal.valueOf(18), BigDecimal.valueOf(144));
        Invoice invoice = createMockInvoice(invoiceId, Collections.singletonList(item), false);

        // Step 1: Return 3 units
        SalesReturnRequest req1 = SalesReturnRequest.builder()
                .includeOriginalDiscounts(true)
                .items(Collections.singletonList(
                        SalesReturnRequest.SalesReturnItemRequest.builder().invoiceItemId(1L).returnQty(3).build()))
                .build();

        SalesReturn sr1 = invoiceService.createSalesReturn(invoiceId, req1);
        assertEquals(0, sr1.getGrossAmount().compareTo(BigDecimal.valueOf(300.00).setScale(2)));
        assertEquals(0, sr1.getProductDiscountAmount().compareTo(BigDecimal.valueOf(30.00).setScale(2)));
        assertEquals(0, sr1.getInvoiceDiscountAmount().compareTo(BigDecimal.valueOf(30.00).setScale(2)));
        assertEquals(0, sr1.getSubtotal().compareTo(BigDecimal.valueOf(240.00).setScale(2)));
        assertEquals(0, sr1.getTaxAmount().compareTo(BigDecimal.valueOf(43.20).setScale(2)));

        // Mock prior return item from sr1 in salesReturnItemRepo
        SalesReturnItem sri1 = sr1.getItems().get(0);
        sri1.setSalesReturn(sr1);
        sr1.setInvoice(invoice);

        // Step 2: Return 3 units (3 units already returned in step 1)
        List<Object[]> step1QtyAgg = Collections.singletonList(new Object[]{1L, 3});
        List<Object[]> step1ProdQtyAgg = Collections.singletonList(new Object[]{10L, 3});
        List<Object[]> step1AmountAgg = Collections.singletonList(new Object[]{
                1L,
                sr1.getGrossAmount(),
                sr1.getProductDiscountAmount(),
                sr1.getInvoiceDiscountAmount(),
                sr1.getTaxAmount()
        });
        when(salesReturnItemRepo.sumReturnedQtyByInvoiceItemIdForInvoice(invoiceId)).thenReturn(step1QtyAgg);
        when(salesReturnItemRepo.sumReturnedQtyByProductIdForInvoice(invoiceId)).thenReturn(step1ProdQtyAgg);
        when(salesReturnItemRepo.sumAllocatedAmountsByInvoiceItemIdForInvoice(invoiceId)).thenReturn(step1AmountAgg);

        SalesReturnRequest req2 = SalesReturnRequest.builder()
                .includeOriginalDiscounts(true)
                .items(Collections.singletonList(
                        SalesReturnRequest.SalesReturnItemRequest.builder().invoiceItemId(1L).returnQty(3).build()))
                .build();

        SalesReturn sr2 = invoiceService.createSalesReturn(invoiceId, req2);
        assertEquals(0, sr2.getGrossAmount().compareTo(BigDecimal.valueOf(300.00).setScale(2)));
        assertEquals(0, sr2.getProductDiscountAmount().compareTo(BigDecimal.valueOf(30.00).setScale(2)));
        assertEquals(0, sr2.getInvoiceDiscountAmount().compareTo(BigDecimal.valueOf(30.00).setScale(2)));
        assertEquals(0, sr2.getSubtotal().compareTo(BigDecimal.valueOf(240.00).setScale(2)));

        // Step 3: Return final 4 units (6 units already returned across step 1 & 2)
        List<Object[]> step2QtyAgg = Collections.singletonList(new Object[]{1L, 6});
        List<Object[]> step2ProdQtyAgg = Collections.singletonList(new Object[]{10L, 6});
        List<Object[]> step2AmountAgg = Collections.singletonList(new Object[]{
                1L,
                sr1.getGrossAmount().add(sr2.getGrossAmount()),
                sr1.getProductDiscountAmount().add(sr2.getProductDiscountAmount()),
                sr1.getInvoiceDiscountAmount().add(sr2.getInvoiceDiscountAmount()),
                sr1.getTaxAmount().add(sr2.getTaxAmount())
        });
        when(salesReturnItemRepo.sumReturnedQtyByInvoiceItemIdForInvoice(invoiceId)).thenReturn(step2QtyAgg);
        when(salesReturnItemRepo.sumReturnedQtyByProductIdForInvoice(invoiceId)).thenReturn(step2ProdQtyAgg);
        when(salesReturnItemRepo.sumAllocatedAmountsByInvoiceItemIdForInvoice(invoiceId)).thenReturn(step2AmountAgg);

        SalesReturnRequest req3 = SalesReturnRequest.builder()
                .includeOriginalDiscounts(true)
                .items(Collections.singletonList(
                        SalesReturnRequest.SalesReturnItemRequest.builder().invoiceItemId(1L).returnQty(4).build()))
                .build();

        SalesReturn sr3 = invoiceService.createSalesReturn(invoiceId, req3);
        assertEquals(0, sr3.getGrossAmount().compareTo(BigDecimal.valueOf(400.00).setScale(2)));
        assertEquals(0, sr3.getProductDiscountAmount().compareTo(BigDecimal.valueOf(40.00).setScale(2)));
        assertEquals(0, sr3.getInvoiceDiscountAmount().compareTo(BigDecimal.valueOf(40.00).setScale(2)));
        assertEquals(0, sr3.getSubtotal().compareTo(BigDecimal.valueOf(320.00).setScale(2)));

        // Cumulative verification:
        BigDecimal totalGross = sr1.getGrossAmount().add(sr2.getGrossAmount()).add(sr3.getGrossAmount());
        BigDecimal totalProdDisc = sr1.getProductDiscountAmount().add(sr2.getProductDiscountAmount()).add(sr3.getProductDiscountAmount());
        BigDecimal totalInvDisc = sr1.getInvoiceDiscountAmount().add(sr2.getInvoiceDiscountAmount()).add(sr3.getInvoiceDiscountAmount());
        BigDecimal totalSubtotal = sr1.getSubtotal().add(sr2.getSubtotal()).add(sr3.getSubtotal());
        BigDecimal totalTax = sr1.getTaxAmount().add(sr2.getTaxAmount()).add(sr3.getTaxAmount());

        assertEquals(0, totalGross.compareTo(BigDecimal.valueOf(1000.00).setScale(2)));
        assertEquals(0, totalProdDisc.compareTo(BigDecimal.valueOf(100.00).setScale(2)));
        assertEquals(0, totalInvDisc.compareTo(BigDecimal.valueOf(100.00).setScale(2)));
        assertEquals(0, totalSubtotal.compareTo(BigDecimal.valueOf(800.00).setScale(2)));
        assertEquals(0, totalTax.compareTo(BigDecimal.valueOf(144.00).setScale(2)));
    }

    // 20. Attempted over-return rejected
    @Test
    void testAttemptedOverReturnRejected() {
        Long invoiceId = 120L;
        InvoiceItem item = createMockItem(1L, "Widget", 5, BigDecimal.valueOf(100),
                BigDecimal.ZERO, BigDecimal.valueOf(500), BigDecimal.valueOf(18), BigDecimal.valueOf(90));
        createMockInvoice(invoiceId, Collections.singletonList(item), false);

        SalesReturnRequest req = SalesReturnRequest.builder()
                .includeOriginalDiscounts(true)
                .items(Collections.singletonList(
                        SalesReturnRequest.SalesReturnItemRequest.builder()
                                .invoiceItemId(1L)
                                .returnQty(6) // 6 > 5
                                .build()))
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> invoiceService.createSalesReturn(invoiceId, req));
        assertTrue(ex.getMessage().contains("exceeds remaining returnable quantity"));
    }

    // Invariant test: Gross - ProdDisc - InvDisc == Subtotal && Subtotal + Tax == TotalRefund
    @Test
    void testReconciliationInvariants() {
        Long invoiceId = 121L;
        // Complex line: 7 units @ 142.50 = 997.50 gross. Prod disc = 75.25. Inv disc = 42.15.
        // Taxable = 997.50 - 75.25 - 42.15 = 880.10. GST 18% = 158.42. Total = 1038.52.
        InvoiceItem item = createMockItem(1L, "Complex Item", 7, new BigDecimal("142.50"),
                new BigDecimal("75.25"), new BigDecimal("880.10"), new BigDecimal("18"), new BigDecimal("158.42"));
        createMockInvoice(invoiceId, Collections.singletonList(item), false);

        SalesReturnRequest req = SalesReturnRequest.builder()
                .includeOriginalDiscounts(true)
                .items(Collections.singletonList(
                        SalesReturnRequest.SalesReturnItemRequest.builder().invoiceItemId(1L).returnQty(3).build()))
                .build();

        SalesReturn sr = invoiceService.createSalesReturn(invoiceId, req);

        // Invariant 1: Gross - ProdDisc - InvDisc == Subtotal
        BigDecimal calculatedSubtotal = sr.getGrossAmount()
                .subtract(sr.getProductDiscountAmount())
                .subtract(sr.getInvoiceDiscountAmount());
        assertEquals(0, calculatedSubtotal.compareTo(sr.getSubtotal()), "Invariant violated: Gross - Discounts != Subtotal");

        // Invariant 2: Subtotal + Tax == TotalRefund (no penalty)
        BigDecimal calculatedRefund = sr.getSubtotal().add(sr.getTaxAmount());
        assertEquals(0, calculatedRefund.compareTo(sr.getTotalRefundAmount()), "Invariant violated: Subtotal + Tax != TotalRefund");

        // Invariant 3: Discounts are non-negative
        assertTrue(sr.getProductDiscountAmount().compareTo(BigDecimal.ZERO) >= 0);
        assertTrue(sr.getInvoiceDiscountAmount().compareTo(BigDecimal.ZERO) >= 0);
    }
}
