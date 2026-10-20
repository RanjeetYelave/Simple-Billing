package com.billing.simple.billsoft.forecast;

import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.forecast.entity.ForecastSalesTransaction;
import com.billing.simple.billsoft.forecast.repository.ForecastProductRepository;
import com.billing.simple.billsoft.forecast.repository.ForecastSalesTransactionRepository;
import com.billing.simple.billsoft.forecast.service.ForecastAggregationService;
import com.billing.simple.billsoft.forecast.service.ForecastIngestionService;
import com.billing.simple.billsoft.repo.InvoiceRepository;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.repo.SalesReturnRepository;
import com.billing.simple.billsoft.repo.StockMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ForecastIngestionServiceTest {

    @Mock
    private ForecastSalesTransactionRepository transactionRepo;
    @Mock
    private ForecastProductRepository forecastProductRepo;
    @Mock
    private StockMovementRepository stockMovementRepo;
    @Mock
    private InvoiceRepository invoiceRepo;
    @Mock
    private SalesReturnRepository salesReturnRepo;
    @Mock
    private ProductRepository productRepo;
    @Mock
    private ForecastAggregationService aggregationService;

    @InjectMocks
    private ForecastIngestionService ingestionService;

    private Product goodsProductA;
    private Product goodsProductB;
    private Product serviceProduct;

    @BeforeEach
    void setUp() {
        goodsProductA = new Product();
        goodsProductA.setId(101L);
        goodsProductA.setName("Widget A");
        goodsProductA.setItemType("GOODS");
        goodsProductA.setPrice(BigDecimal.valueOf(100.0));
        goodsProductA.setGstPercentage(BigDecimal.valueOf(18.0));

        goodsProductB = new Product();
        goodsProductB.setId(102L);
        goodsProductB.setName("Widget B");
        goodsProductB.setItemType("GOODS");
        goodsProductB.setPrice(BigDecimal.valueOf(200.0));
        goodsProductB.setGstPercentage(BigDecimal.valueOf(18.0));

        serviceProduct = new Product();
        serviceProduct.setId(201L);
        serviceProduct.setName("Consulting Service");
        serviceProduct.setItemType("SERVICE");
        serviceProduct.setPrice(BigDecimal.valueOf(500.0));
        serviceProduct.setGstPercentage(BigDecimal.valueOf(18.0));
    }

    @Test
    @DisplayName("Scenario 1: Single line, full stock -> 100% fulfilled, 0 shortage")
    void testScenario1_SingleLineFullStock() {
        Long firmId = 1L;
        Invoice inv = new Invoice();
        inv.setId(10L);
        inv.setFirmId(firmId);
        inv.setInvoiceNumber("INV-001");
        inv.setInvoiceDate(LocalDateTime.now());
        inv.setStatus(InvoiceStatus.PAID);

        InvoiceItem item = new InvoiceItem();
        item.setProduct(goodsProductA);
        item.setQty(5);
        item.setPricePerUnit(BigDecimal.valueOf(100.0));
        item.setTaxableAmount(BigDecimal.valueOf(500.0));
        item.setGstAmount(BigDecimal.valueOf(90.0));
        item.setLineTotal(BigDecimal.valueOf(590.0));
        inv.setItems(Collections.singletonList(item));

        when(productRepo.findById(101L)).thenReturn(Optional.of(goodsProductA));
        when(stockMovementRepo.sumQuantityChangeByProductIdForInvoice(eq(firmId), eq("INV-001"), anyString()))
                .thenReturn(Collections.singletonList(new Object[]{101L, BigDecimal.valueOf(-5.0)}));

        ingestionService.syncInvoice(inv);

        ArgumentCaptor<List<ForecastSalesTransaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactionRepo).saveAll(captor.capture());

        List<ForecastSalesTransaction> saved = captor.getValue();
        assertEquals(1, saved.size());
        ForecastSalesTransaction tx = saved.get(0);
        assertEquals(5.0, tx.getRequestedQty().doubleValue(), 1e-6);
        assertEquals(5.0, tx.getFulfilledQty().doubleValue(), 1e-6);
        assertEquals(0.0, tx.getShortageQty().doubleValue(), 1e-6);
        assertEquals(590.0, tx.getLineTotal().doubleValue(), 1e-6);
        assertEquals("SALE", tx.getSourceType());
        assertTrue(tx.getFulfilledQty().compareTo(BigDecimal.ZERO) >= 0);
        assertTrue(tx.getShortageQty().compareTo(BigDecimal.ZERO) >= 0);
        assertEquals(0, tx.getRequestedQty().compareTo(tx.getFulfilledQty().add(tx.getShortageQty())));
    }

    @Test
    @DisplayName("Scenario 2: Single line, partial stock -> fulfilled equals stock movement, remainder is shortage")
    void testScenario2_SingleLinePartialStock() {
        Long firmId = 1L;
        Invoice inv = new Invoice();
        inv.setId(11L);
        inv.setFirmId(firmId);
        inv.setInvoiceNumber("INV-002");
        inv.setInvoiceDate(LocalDateTime.now());
        inv.setStatus(InvoiceStatus.PAID);

        InvoiceItem item = new InvoiceItem();
        item.setProduct(goodsProductA);
        item.setQty(10);
        item.setPricePerUnit(BigDecimal.valueOf(100.0));
        item.setTaxableAmount(BigDecimal.valueOf(1000.0));
        item.setGstAmount(BigDecimal.valueOf(180.0));
        item.setLineTotal(BigDecimal.valueOf(1180.0));
        inv.setItems(Collections.singletonList(item));

        when(productRepo.findById(101L)).thenReturn(Optional.of(goodsProductA));
        when(stockMovementRepo.sumQuantityChangeByProductIdForInvoice(eq(firmId), eq("INV-002"), anyString()))
                .thenReturn(Collections.singletonList(new Object[]{101L, BigDecimal.valueOf(-6.0)}));

        ingestionService.syncInvoice(inv);

        ArgumentCaptor<List<ForecastSalesTransaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactionRepo).saveAll(captor.capture());

        List<ForecastSalesTransaction> saved = captor.getValue();
        assertEquals(1, saved.size());
        ForecastSalesTransaction tx = saved.get(0);
        assertEquals(10.0, tx.getRequestedQty().doubleValue(), 1e-6);
        assertEquals(6.0, tx.getFulfilledQty().doubleValue(), 1e-6);
        assertEquals(4.0, tx.getShortageQty().doubleValue(), 1e-6);
        assertEquals(0, tx.getRequestedQty().compareTo(tx.getFulfilledQty().add(tx.getShortageQty())));
    }

    @Test
    @DisplayName("Scenario 3: Single line, zero stock GOODS -> 0 fulfilled, 100% shortage")
    void testScenario3_SingleLineZeroStockGoods() {
        Long firmId = 1L;
        Invoice inv = new Invoice();
        inv.setId(12L);
        inv.setFirmId(firmId);
        inv.setInvoiceNumber("INV-003");
        inv.setInvoiceDate(LocalDateTime.now());
        inv.setStatus(InvoiceStatus.PAID);

        InvoiceItem item = new InvoiceItem();
        item.setProduct(goodsProductA);
        item.setQty(4);
        item.setPricePerUnit(BigDecimal.valueOf(100.0));
        item.setTaxableAmount(BigDecimal.valueOf(400.0));
        item.setGstAmount(BigDecimal.valueOf(72.0));
        item.setLineTotal(BigDecimal.valueOf(472.0));
        inv.setItems(Collections.singletonList(item));

        when(productRepo.findById(101L)).thenReturn(Optional.of(goodsProductA));
        when(stockMovementRepo.sumQuantityChangeByProductIdForInvoice(eq(firmId), eq("INV-003"), anyString()))
                .thenReturn(Collections.emptyList());

        ingestionService.syncInvoice(inv);

        ArgumentCaptor<List<ForecastSalesTransaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactionRepo).saveAll(captor.capture());

        List<ForecastSalesTransaction> saved = captor.getValue();
        assertEquals(1, saved.size());
        ForecastSalesTransaction tx = saved.get(0);
        assertEquals(4.0, tx.getRequestedQty().doubleValue(), 1e-6);
        assertEquals(0.0, tx.getFulfilledQty().doubleValue(), 1e-6);
        assertEquals(4.0, tx.getShortageQty().doubleValue(), 1e-6);
        assertEquals(0, tx.getRequestedQty().compareTo(tx.getFulfilledQty().add(tx.getShortageQty())));
    }

    @Test
    @DisplayName("Scenario 4: Same product, two lines, full fulfillment -> both lines fully fulfilled")
    void testScenario4_SameProductTwoLinesFullFulfillment() {
        Long firmId = 1L;
        Invoice inv = new Invoice();
        inv.setId(13L);
        inv.setFirmId(firmId);
        inv.setInvoiceNumber("INV-004");
        inv.setInvoiceDate(LocalDateTime.now());
        inv.setStatus(InvoiceStatus.PAID);

        InvoiceItem line1 = new InvoiceItem();
        line1.setProduct(goodsProductA);
        line1.setQty(3);
        line1.setLineTotal(BigDecimal.valueOf(354.0));

        InvoiceItem line2 = new InvoiceItem();
        line2.setProduct(goodsProductA);
        line2.setQty(7);
        line2.setLineTotal(BigDecimal.valueOf(826.0));

        inv.setItems(Arrays.asList(line1, line2));

        when(productRepo.findById(101L)).thenReturn(Optional.of(goodsProductA));
        when(stockMovementRepo.sumQuantityChangeByProductIdForInvoice(eq(firmId), eq("INV-004"), anyString()))
                .thenReturn(Collections.singletonList(new Object[]{101L, BigDecimal.valueOf(-10.0)}));

        ingestionService.syncInvoice(inv);

        ArgumentCaptor<List<ForecastSalesTransaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactionRepo).saveAll(captor.capture());

        List<ForecastSalesTransaction> saved = captor.getValue();
        assertEquals(2, saved.size());
        assertEquals(3.0, saved.get(0).getFulfilledQty().doubleValue(), 1e-6);
        assertEquals(0.0, saved.get(0).getShortageQty().doubleValue(), 1e-6);
        assertEquals(7.0, saved.get(1).getFulfilledQty().doubleValue(), 1e-6);
        assertEquals(0.0, saved.get(1).getShortageQty().doubleValue(), 1e-6);
    }

    @Test
    @DisplayName("Scenario 5: Same product, two lines, partial fulfillment -> Sequential FIFO line allocation")
    void testScenario5_SameProductTwoLinesPartialFulfillment() {
        Long firmId = 1L;
        Invoice inv = new Invoice();
        inv.setId(14L);
        inv.setFirmId(firmId);
        inv.setInvoiceNumber("INV-005");
        inv.setInvoiceDate(LocalDateTime.now());
        inv.setStatus(InvoiceStatus.PAID);

        InvoiceItem line1 = new InvoiceItem();
        line1.setProduct(goodsProductA);
        line1.setQty(5);
        line1.setLineTotal(BigDecimal.valueOf(590.0));

        InvoiceItem line2 = new InvoiceItem();
        line2.setProduct(goodsProductA);
        line2.setQty(10);
        line2.setLineTotal(BigDecimal.valueOf(1180.0));

        inv.setItems(Arrays.asList(line1, line2));

        when(productRepo.findById(101L)).thenReturn(Optional.of(goodsProductA));
        when(stockMovementRepo.sumQuantityChangeByProductIdForInvoice(eq(firmId), eq("INV-005"), anyString()))
                .thenReturn(Collections.singletonList(new Object[]{101L, BigDecimal.valueOf(-12.0)}));

        ingestionService.syncInvoice(inv);

        ArgumentCaptor<List<ForecastSalesTransaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactionRepo).saveAll(captor.capture());

        List<ForecastSalesTransaction> saved = captor.getValue();
        assertEquals(2, saved.size());

        ForecastSalesTransaction tx1 = saved.get(0);
        assertEquals(5.0, tx1.getRequestedQty().doubleValue(), 1e-6);
        assertEquals(5.0, tx1.getFulfilledQty().doubleValue(), 1e-6);
        assertEquals(0.0, tx1.getShortageQty().doubleValue(), 1e-6);

        ForecastSalesTransaction tx2 = saved.get(1);
        assertEquals(10.0, tx2.getRequestedQty().doubleValue(), 1e-6);
        assertEquals(7.0, tx2.getFulfilledQty().doubleValue(), 1e-6);
        assertEquals(3.0, tx2.getShortageQty().doubleValue(), 1e-6);

        // Verify invariant: sum(fulfilled) == 12, sum(shortage) == 3, sum(requested) == 15
        BigDecimal totalFulfilled = tx1.getFulfilledQty().add(tx2.getFulfilledQty());
        BigDecimal totalShortage = tx1.getShortageQty().add(tx2.getShortageQty());
        assertEquals(new BigDecimal("12.000"), totalFulfilled.setScale(3));
        assertEquals(new BigDecimal("3.000"), totalShortage.setScale(3));
    }

    @Test
    @DisplayName("Scenario 6: Same product, two lines, zero fulfillment -> both lines have 0 fulfilled, 100% shortage")
    void testScenario6_SameProductTwoLinesZeroFulfillment() {
        Long firmId = 1L;
        Invoice inv = new Invoice();
        inv.setId(15L);
        inv.setFirmId(firmId);
        inv.setInvoiceNumber("INV-006");
        inv.setInvoiceDate(LocalDateTime.now());
        inv.setStatus(InvoiceStatus.PAID);

        InvoiceItem line1 = new InvoiceItem();
        line1.setProduct(goodsProductA);
        line1.setQty(4);
        line1.setLineTotal(BigDecimal.valueOf(472.0));

        InvoiceItem line2 = new InvoiceItem();
        line2.setProduct(goodsProductA);
        line2.setQty(6);
        line2.setLineTotal(BigDecimal.valueOf(708.0));

        inv.setItems(Arrays.asList(line1, line2));

        when(productRepo.findById(101L)).thenReturn(Optional.of(goodsProductA));
        when(stockMovementRepo.sumQuantityChangeByProductIdForInvoice(eq(firmId), eq("INV-006"), anyString()))
                .thenReturn(Collections.emptyList());

        ingestionService.syncInvoice(inv);

        ArgumentCaptor<List<ForecastSalesTransaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactionRepo).saveAll(captor.capture());

        List<ForecastSalesTransaction> saved = captor.getValue();
        assertEquals(2, saved.size());
        assertEquals(0.0, saved.get(0).getFulfilledQty().doubleValue(), 1e-6);
        assertEquals(4.0, saved.get(0).getShortageQty().doubleValue(), 1e-6);
        assertEquals(0.0, saved.get(1).getFulfilledQty().doubleValue(), 1e-6);
        assertEquals(6.0, saved.get(1).getShortageQty().doubleValue(), 1e-6);
    }

    @Test
    @DisplayName("Scenario 7: Multiple INVOICE_SALE delta movements -> correctly aggregates net delta")
    void testScenario7_MultipleDeltaMovementsReconstruction() {
        Long firmId = 1L;
        Invoice inv = new Invoice();
        inv.setId(16L);
        inv.setFirmId(firmId);
        inv.setInvoiceNumber("INV-007");
        inv.setInvoiceDate(LocalDateTime.now());
        inv.setStatus(InvoiceStatus.PAID);

        InvoiceItem item = new InvoiceItem();
        item.setProduct(goodsProductA);
        item.setQty(10);
        item.setLineTotal(BigDecimal.valueOf(1180.0));
        inv.setItems(Collections.singletonList(item));

        when(productRepo.findById(101L)).thenReturn(Optional.of(goodsProductA));
        // E.g. -10 and +4 net to -6
        when(stockMovementRepo.sumQuantityChangeByProductIdForInvoice(eq(firmId), eq("INV-007"), anyString()))
                .thenReturn(Collections.singletonList(new Object[]{101L, BigDecimal.valueOf(-6.0)}));

        ingestionService.syncInvoice(inv);

        ArgumentCaptor<List<ForecastSalesTransaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactionRepo).saveAll(captor.capture());

        List<ForecastSalesTransaction> saved = captor.getValue();
        assertEquals(1, saved.size());
        assertEquals(6.0, saved.get(0).getFulfilledQty().doubleValue(), 1e-6);
        assertEquals(4.0, saved.get(0).getShortageQty().doubleValue(), 1e-6);
    }

    @Test
    @DisplayName("Scenario 8: Invoice quantity edited after sale -> previous rows deleted and new lines inserted")
    void testScenario8_InvoiceQuantityEditedAfterSale() {
        Long firmId = 1L;
        Invoice inv = new Invoice();
        inv.setId(20L);
        inv.setFirmId(firmId);
        inv.setInvoiceNumber("INV-020");
        inv.setInvoiceDate(LocalDateTime.now());
        inv.setStatus(InvoiceStatus.PAID);

        InvoiceItem item = new InvoiceItem();
        item.setProduct(goodsProductA);
        item.setQty(8);
        item.setPricePerUnit(BigDecimal.valueOf(100.0));
        item.setTaxableAmount(BigDecimal.valueOf(800.0));
        item.setGstAmount(BigDecimal.valueOf(144.0));
        item.setLineTotal(BigDecimal.valueOf(944.0));
        inv.setItems(Collections.singletonList(item));

        when(productRepo.findById(101L)).thenReturn(Optional.of(goodsProductA));
        when(stockMovementRepo.sumQuantityChangeByProductIdForInvoice(eq(firmId), eq("INV-020"), anyString()))
                .thenReturn(Collections.singletonList(new Object[]{101L, BigDecimal.valueOf(-8.0)}));

        ingestionService.syncInvoice(inv);

        // Verify delete was executed for this invoice before insertion
        verify(transactionRepo).deleteAllByFirmIdAndSourceTypeAndSourceHeaderId(firmId, "SALE", 20L);
        verify(transactionRepo).saveAll(anyList());
    }

    @Test
    @DisplayName("Scenario 9: Invoice cancelled -> previous rows deleted and no new SALE rows created")
    void testScenario9_InvoiceCancelled() {
        Long firmId = 1L;
        Invoice inv = new Invoice();
        inv.setId(30L);
        inv.setFirmId(firmId);
        inv.setInvoiceNumber("INV-030");
        inv.setInvoiceDate(LocalDateTime.now());
        inv.setStatus(InvoiceStatus.CANCELLED);

        ingestionService.syncInvoice(inv);

        // Must delete existing SALE facts and not insert new ones
        verify(transactionRepo).deleteAllByFirmIdAndSourceTypeAndSourceHeaderId(firmId, "SALE", 30L);
        verify(transactionRepo, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("Scenario 10: SERVICE item with no movement -> 100% fulfilled without stock movement")
    void testScenario10_ServiceItemWithNoMovement() {
        Long firmId = 1L;
        Invoice inv = new Invoice();
        inv.setId(40L);
        inv.setFirmId(firmId);
        inv.setInvoiceNumber("INV-040");
        inv.setInvoiceDate(LocalDateTime.now());
        inv.setStatus(InvoiceStatus.PAID);

        InvoiceItem item = new InvoiceItem();
        item.setProduct(serviceProduct);
        item.setQty(3);
        item.setPricePerUnit(BigDecimal.valueOf(500.0));
        item.setTaxableAmount(BigDecimal.valueOf(1500.0));
        item.setGstAmount(BigDecimal.valueOf(270.0));
        item.setLineTotal(BigDecimal.valueOf(1770.0));
        inv.setItems(Collections.singletonList(item));

        when(productRepo.findById(201L)).thenReturn(Optional.of(serviceProduct));
        when(stockMovementRepo.sumQuantityChangeByProductIdForInvoice(eq(firmId), eq("INV-040"), anyString()))
                .thenReturn(Collections.emptyList());

        ingestionService.syncInvoice(inv);

        ArgumentCaptor<List<ForecastSalesTransaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactionRepo).saveAll(captor.capture());

        List<ForecastSalesTransaction> saved = captor.getValue();
        assertEquals(1, saved.size());
        ForecastSalesTransaction tx = saved.get(0);
        assertEquals(3.0, tx.getRequestedQty().doubleValue(), 1e-6);
        assertEquals(3.0, tx.getFulfilledQty().doubleValue(), 1e-6);
        assertEquals(0.0, tx.getShortageQty().doubleValue(), 1e-6);
    }

    @Test
    @DisplayName("Scenario 11: Multiple products on one invoice -> independent stock allocation per product")
    void testScenario11_MultipleProductsOnOneInvoice() {
        Long firmId = 1L;
        Invoice inv = new Invoice();
        inv.setId(45L);
        inv.setFirmId(firmId);
        inv.setInvoiceNumber("INV-045");
        inv.setInvoiceDate(LocalDateTime.now());
        inv.setStatus(InvoiceStatus.PAID);

        InvoiceItem itemA = new InvoiceItem();
        itemA.setProduct(goodsProductA);
        itemA.setQty(5);
        itemA.setLineTotal(BigDecimal.valueOf(590.0));

        InvoiceItem itemB = new InvoiceItem();
        itemB.setProduct(goodsProductB);
        itemB.setQty(2);
        itemB.setLineTotal(BigDecimal.valueOf(472.0));

        inv.setItems(Arrays.asList(itemA, itemB));

        when(productRepo.findById(101L)).thenReturn(Optional.of(goodsProductA));
        when(productRepo.findById(102L)).thenReturn(Optional.of(goodsProductB));
        when(stockMovementRepo.sumQuantityChangeByProductIdForInvoice(eq(firmId), eq("INV-045"), anyString()))
                .thenReturn(Arrays.asList(
                        new Object[]{101L, BigDecimal.valueOf(-5.0)},
                        new Object[]{102L, BigDecimal.valueOf(-1.0)} // partial fulfillment for B
                ));

        ingestionService.syncInvoice(inv);

        ArgumentCaptor<List<ForecastSalesTransaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactionRepo).saveAll(captor.capture());

        List<ForecastSalesTransaction> saved = captor.getValue();
        assertEquals(2, saved.size());

        // Product A: 5 requested, 5 fulfilled, 0 shortage
        assertEquals(5.0, saved.get(0).getFulfilledQty().doubleValue(), 1e-6);
        assertEquals(0.0, saved.get(0).getShortageQty().doubleValue(), 1e-6);

        // Product B: 2 requested, 1 fulfilled, 1 shortage
        assertEquals(1.0, saved.get(1).getFulfilledQty().doubleValue(), 1e-6);
        assertEquals(1.0, saved.get(1).getShortageQty().doubleValue(), 1e-6);
    }

    @Test
    @DisplayName("Scenario 12: Sales return after sale -> separate immutable RETURN fact recorded")
    void testScenario12_SalesReturnAfterSale() {
        Long firmId = 1L;
        SalesReturn ret = new SalesReturn();
        ret.setId(50L);
        ret.setFirmId(firmId);
        ret.setReturnNumber("RET-001");
        ret.setReturnDate(LocalDate.now());
        ret.setCreatedAt(LocalDateTime.now());

        SalesReturnItem retItem = new SalesReturnItem();
        retItem.setProduct(goodsProductA);
        retItem.setReturnQty(2);
        retItem.setRefundTotal(BigDecimal.valueOf(236.0));
        retItem.setGstAmount(BigDecimal.valueOf(36.0));
        ret.setItems(Collections.singletonList(retItem));

        when(transactionRepo.existsByFirmIdAndSourceTypeAndSourceHeaderIdAndSourceLineOrdinal(firmId, "RETURN", 50L, 0))
                .thenReturn(false);

        ingestionService.syncSalesReturn(ret);

        ArgumentCaptor<ForecastSalesTransaction> captor = ArgumentCaptor.forClass(ForecastSalesTransaction.class);
        verify(transactionRepo).save(captor.capture());

        ForecastSalesTransaction tx = captor.getValue();
        assertEquals(2.0, tx.getRequestedQty().doubleValue(), 1e-6);
        assertEquals(236.0, tx.getLineTotal().doubleValue(), 1e-6);
        assertEquals("RETURN", tx.getSourceType());
        assertEquals(0.0, tx.getFulfilledQty().doubleValue(), 1e-6);
        assertEquals(0.0, tx.getShortageQty().doubleValue(), 1e-6);
    }
}
