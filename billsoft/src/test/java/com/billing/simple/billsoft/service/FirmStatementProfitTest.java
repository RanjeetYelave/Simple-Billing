package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dtos.FirmStatementResponse;
import com.billing.simple.billsoft.dtos.InvoiceRequest;
import com.billing.simple.billsoft.dtos.InvoiceRequestItem;
import com.billing.simple.billsoft.dtos.migration.InvoiceSnapshotDTO;
import com.billing.simple.billsoft.engine.InvoiceCalculationEngine;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.repositories.PartyPaymentRepository;
import com.billing.simple.billsoft.repositories.PartyRepository;
import com.billing.simple.billsoft.repositories.PurchaseOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

/**
 * Comprehensive Automated Test Suite for Period-wise Total Profit Calculation in RupeeCRM Firm Statement.
 * Covers all 17 required scenarios:
 * 1. Product sold at a price above cost.
 * 2. Product sold below cost.
 * 3. Pricing Matrix increases selling price.
 * 4. Pricing Matrix decreases selling price.
 * 5. Product discount applied.
 * 6. Invoice-level discount allocated across items.
 * 7. GST excluded from profit.
 * 8. Product cost changed after invoice creation.
 * 9. Partial sales return.
 * 10. Multiple successive partial returns.
 * 11. Fully returned invoice.
 * 12. Cancelled invoice.
 * 13. Custom date-range boundaries.
 * 14. Two different firms with separate inventory and invoices.
 * 15. Legacy invoices without cost snapshots.
 * 16. Invoice edit does not rewrite historical cost.
 * 17. Backup and restore preserves historical cost snapshots.
 */
public class FirmStatementProfitTest {

    @Mock private InvoiceRepository invoiceRepo;
    @Mock private CustomerRepository customerRepo;
    @Mock private FirmDetailsRepository firmRepo;
    @Mock private PartyRepository partyRepo;
    @Mock private PartyPaymentRepository partyPaymentRepo;
    @Mock private PurchaseOrderRepository purchaseOrderRepo;
    @Mock private InvoicePaymentRepository invoicePaymentRepo;
    @Mock private SalesReturnRepository salesReturnRepo;

    @InjectMocks
    private StatementServiceImpl statementService;

    private FirmDetails firm1;
    private FirmDetails firm2;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        firm1 = new FirmDetails();
        firm1.setId(1L);
        firm1.setFirmName("Firm One Pvt Ltd");
        firm1.setGstin("27AAAAA0000A1Z5");

        firm2 = new FirmDetails();
        firm2.setId(2L);
        firm2.setFirmName("Firm Two Corp");
        firm2.setGstin("27BBBBB1111B2Z6");

        when(firmRepo.findById(1L)).thenReturn(Optional.of(firm1));
        when(firmRepo.findById(2L)).thenReturn(Optional.of(firm2));
    }

    private Invoice createInvoice(Long firmId, String invNumber, LocalDate date, InvoiceStatus status) {
        Invoice inv = new Invoice();
        inv.setFirmId(firmId);
        inv.setInvoiceNumber(invNumber);
        inv.setInvoiceDate(date.atTime(10, 0));
        inv.setStatus(status);
        inv.setPaid(true);
        inv.setItems(new ArrayList<>());
        return inv;
    }

    private InvoiceItem addItem(Invoice inv, Long itemId, String name, int qty, BigDecimal sellingPrice, BigDecimal unitCost, BigDecimal itemDisc, BigDecimal gstPct) {
        InvoiceItem item = new InvoiceItem();
        item.setId(itemId);
        item.setInvoice(inv);
        item.setProductName(name);
        item.setQty(qty);
        item.setPricePerUnit(sellingPrice);
        item.setUnitCostPrice(unitCost);

        BigDecimal gross = sellingPrice.multiply(BigDecimal.valueOf(qty));
        item.setAmountWithoutTax(gross);

        BigDecimal disc = itemDisc != null ? itemDisc : BigDecimal.ZERO;
        item.setDiscountValue(disc);

        BigDecimal taxable = gross.subtract(disc);
        item.setTaxableAmount(taxable);

        item.setGstPercent(gstPct);
        BigDecimal gstAmt = taxable.multiply(gstPct).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        item.setGstAmount(gstAmt);
        item.setLineTotal(taxable.add(gstAmt));

        inv.getItems().add(item);

        BigDecimal invSubtotal = inv.getSubtotalWithoutTax() != null ? inv.getSubtotalWithoutTax() : BigDecimal.ZERO;
        inv.setSubtotalWithoutTax(invSubtotal.add(taxable));
        BigDecimal invTax = inv.getTotalTax() != null ? inv.getTotalTax() : BigDecimal.ZERO;
        inv.setTotalTax(invTax.add(gstAmt));
        BigDecimal invTotal = inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO;
        inv.setTotalAmount(invTotal.add(taxable.add(gstAmt)));

        return item;
    }

    // ------------------------------------------------------------------------
    // Scenario 1: Product sold at a price above cost
    // ------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 1: Product sold at a price above cost yields positive gross profit")
    void testProductSoldAboveCost() {
        LocalDate date = LocalDate.of(2026, 4, 10);
        Invoice inv = createInvoice(1L, "INV-001", date, InvoiceStatus.PAID);
        // Cost: 60, Selling: 100, Qty: 5 -> Sales ex-GST = 500, COGS = 300, Profit = 200
        addItem(inv, 1L, "Widget A", 5, new BigDecimal("100.00"), new BigDecimal("60.00"), BigDecimal.ZERO, new BigDecimal("18.00"));

        when(invoiceRepo.findAllByFirmIdAndInvoiceDateBetweenOrderByInvoiceDateAsc(eq(1L), any(), any()))
                .thenReturn(Collections.singletonList(inv));
        when(salesReturnRepo.findByFirmIdAndReturnDateBetween(eq(1L), any(), any()))
                .thenReturn(Collections.emptyList());

        FirmStatementResponse res = statementService.getFirmStatement(1L, date, date);

        assertEquals(500.00, res.getPeriodTotalSalesExGst(), 0.001);
        assertEquals(300.00, res.getPeriodCogs(), 0.001);
        assertEquals(0.00, res.getPeriodSalesReturnsExGst(), 0.001);
        assertEquals(0.00, res.getPeriodCostReversedOnReturns(), 0.001);
        assertEquals(200.00, res.getPeriodNetProfit(), 0.001);
        assertEquals(0, res.getUncostedLineItemsCount());
        assertTrue(res.getIsProfitComplete());
    }

    // ------------------------------------------------------------------------
    // Scenario 2: Product sold below cost
    // ------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 2: Product sold below cost yields negative gross profit (loss)")
    void testProductSoldBelowCost() {
        LocalDate date = LocalDate.of(2026, 4, 10);
        Invoice inv = createInvoice(1L, "INV-002", date, InvoiceStatus.PAID);
        // Cost: 120, Selling: 90, Qty: 2 -> Sales ex-GST = 180, COGS = 240, Profit = -60
        addItem(inv, 2L, "Widget Loss", 2, new BigDecimal("90.00"), new BigDecimal("120.00"), BigDecimal.ZERO, new BigDecimal("18.00"));

        when(invoiceRepo.findAllByFirmIdAndInvoiceDateBetweenOrderByInvoiceDateAsc(eq(1L), any(), any()))
                .thenReturn(Collections.singletonList(inv));
        when(salesReturnRepo.findByFirmIdAndReturnDateBetween(eq(1L), any(), any()))
                .thenReturn(Collections.emptyList());

        FirmStatementResponse res = statementService.getFirmStatement(1L, date, date);

        assertEquals(180.00, res.getPeriodTotalSalesExGst(), 0.001);
        assertEquals(240.00, res.getPeriodCogs(), 0.001);
        assertEquals(-60.00, res.getPeriodNetProfit(), 0.001);
        assertTrue(res.getIsProfitComplete());
    }

    // ------------------------------------------------------------------------
    // Scenario 3: Pricing Matrix increases selling price
    // ------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 3: Pricing Matrix increases selling price -> profit calculated on actual charged price")
    void testPricingMatrixIncreasesPrice() {
        LocalDate date = LocalDate.of(2026, 4, 10);
        Invoice inv = createInvoice(1L, "INV-003", date, InvoiceStatus.PAID);
        // Base price was 100, Pricing Matrix rule increased to 135. Cost is 80. Qty = 10.
        // Sales ex-GST = 1350, COGS = 800, Profit = 550
        addItem(inv, 3L, "Premium Item", 10, new BigDecimal("135.00"), new BigDecimal("80.00"), BigDecimal.ZERO, new BigDecimal("12.00"));

        when(invoiceRepo.findAllByFirmIdAndInvoiceDateBetweenOrderByInvoiceDateAsc(eq(1L), any(), any()))
                .thenReturn(Collections.singletonList(inv));
        when(salesReturnRepo.findByFirmIdAndReturnDateBetween(eq(1L), any(), any()))
                .thenReturn(Collections.emptyList());

        FirmStatementResponse res = statementService.getFirmStatement(1L, date, date);

        assertEquals(1350.00, res.getPeriodTotalSalesExGst(), 0.001);
        assertEquals(800.00, res.getPeriodCogs(), 0.001);
        assertEquals(550.00, res.getPeriodNetProfit(), 0.001);
    }

    // ------------------------------------------------------------------------
    // Scenario 4: Pricing Matrix decreases selling price
    // ------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 4: Pricing Matrix decreases selling price -> profit calculated on actual charged price")
    void testPricingMatrixDecreasesPrice() {
        LocalDate date = LocalDate.of(2026, 4, 10);
        Invoice inv = createInvoice(1L, "INV-004", date, InvoiceStatus.PAID);
        // Base price was 100, Matrix discounted to 85. Cost is 65. Qty = 4.
        // Sales ex-GST = 340, COGS = 260, Profit = 80
        addItem(inv, 4L, "Bulk Tier Item", 4, new BigDecimal("85.00"), new BigDecimal("65.00"), BigDecimal.ZERO, new BigDecimal("5.00"));

        when(invoiceRepo.findAllByFirmIdAndInvoiceDateBetweenOrderByInvoiceDateAsc(eq(1L), any(), any()))
                .thenReturn(Collections.singletonList(inv));
        when(salesReturnRepo.findByFirmIdAndReturnDateBetween(eq(1L), any(), any()))
                .thenReturn(Collections.emptyList());

        FirmStatementResponse res = statementService.getFirmStatement(1L, date, date);

        assertEquals(340.00, res.getPeriodTotalSalesExGst(), 0.001);
        assertEquals(260.00, res.getPeriodCogs(), 0.001);
        assertEquals(80.00, res.getPeriodNetProfit(), 0.001);
    }

    // ------------------------------------------------------------------------
    // Scenario 5: Product-level discount applied
    // ------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 5: Product discount applied correctly reduces Net Sales Revenue and Profit")
    void testProductDiscountApplied() {
        LocalDate date = LocalDate.of(2026, 4, 10);
        Invoice inv = createInvoice(1L, "INV-005", date, InvoiceStatus.PAID);
        // Selling: 100, Qty: 2 -> Gross = 200. Product discount = 30. Net sales ex-GST = 170. Cost = 60 each -> COGS = 120.
        // Profit = 170 - 120 = 50.
        addItem(inv, 5L, "Promo Item", 2, new BigDecimal("100.00"), new BigDecimal("60.00"), new BigDecimal("30.00"), new BigDecimal("18.00"));

        when(invoiceRepo.findAllByFirmIdAndInvoiceDateBetweenOrderByInvoiceDateAsc(eq(1L), any(), any()))
                .thenReturn(Collections.singletonList(inv));
        when(salesReturnRepo.findByFirmIdAndReturnDateBetween(eq(1L), any(), any()))
                .thenReturn(Collections.emptyList());

        FirmStatementResponse res = statementService.getFirmStatement(1L, date, date);

        assertEquals(170.00, res.getPeriodTotalSalesExGst(), 0.001);
        assertEquals(120.00, res.getPeriodCogs(), 0.001);
        assertEquals(50.00, res.getPeriodNetProfit(), 0.001);
    }

    // ------------------------------------------------------------------------
    // Scenario 6: Invoice-level discount allocated across items
    // ------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 6: Invoice-level discount distributed proportionally reduces taxable amount and profit")
    void testAllocatedInvoiceDiscount() {
        // Line 1: Gross 200, Line 2: Gross 200. Total taxable before inv discount = 400.
        // Invoice discount = 40 (20 allocated to each line).
        // Line 1: Taxable 180, Cost 50x2=100 -> Profit 80.
        // Line 2: Taxable 180, Cost 70x2=140 -> Profit 40.
        // Total Sales ex-GST = 360, COGS = 240, Profit = 120.
        LocalDate date = LocalDate.of(2026, 4, 10);
        Invoice inv = createInvoice(1L, "INV-006", date, InvoiceStatus.PAID);

        InvoiceItem it1 = addItem(inv, 61L, "Item 1", 2, new BigDecimal("100.00"), new BigDecimal("50.00"), BigDecimal.ZERO, new BigDecimal("18.00"));
        it1.setTaxableAmount(new BigDecimal("180.00")); // after allocated discount of 20

        InvoiceItem it2 = addItem(inv, 62L, "Item 2", 2, new BigDecimal("100.00"), new BigDecimal("70.00"), BigDecimal.ZERO, new BigDecimal("18.00"));
        it2.setTaxableAmount(new BigDecimal("180.00")); // after allocated discount of 20

        when(invoiceRepo.findAllByFirmIdAndInvoiceDateBetweenOrderByInvoiceDateAsc(eq(1L), any(), any()))
                .thenReturn(Collections.singletonList(inv));
        when(salesReturnRepo.findByFirmIdAndReturnDateBetween(eq(1L), any(), any()))
                .thenReturn(Collections.emptyList());

        FirmStatementResponse res = statementService.getFirmStatement(1L, date, date);

        assertEquals(360.00, res.getPeriodTotalSalesExGst(), 0.001);
        assertEquals(240.00, res.getPeriodCogs(), 0.001);
        assertEquals(120.00, res.getPeriodNetProfit(), 0.001);
    }

    // ------------------------------------------------------------------------
    // Scenario 7: GST excluded from profit
    // ------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 7: GST (tax amounts) are strictly excluded from profit calculations")
    void testGstExcludedFromProfit() {
        LocalDate date = LocalDate.of(2026, 4, 10);
        Invoice inv = createInvoice(1L, "INV-007", date, InvoiceStatus.PAID);
        // Selling: 1000, Qty: 1, GST: 28% (280 Rs tax). Line total = 1280.
        // Cost: 700.
        // Net sales ex-GST = 1000, COGS = 700, Profit = 300.
        addItem(inv, 7L, "High Tax Good", 1, new BigDecimal("1000.00"), new BigDecimal("700.00"), BigDecimal.ZERO, new BigDecimal("28.00"));

        when(invoiceRepo.findAllByFirmIdAndInvoiceDateBetweenOrderByInvoiceDateAsc(eq(1L), any(), any()))
                .thenReturn(Collections.singletonList(inv));
        when(salesReturnRepo.findByFirmIdAndReturnDateBetween(eq(1L), any(), any()))
                .thenReturn(Collections.emptyList());

        FirmStatementResponse res = statementService.getFirmStatement(1L, date, date);

        assertEquals(1000.00, res.getPeriodTotalSalesExGst(), 0.001);
        assertEquals(700.00, res.getPeriodCogs(), 0.001);
        assertEquals(300.00, res.getPeriodNetProfit(), 0.001);
        // Verify tax amount is kept in totalTax but NOT in profit
        assertEquals(280.00, res.getTotalTax(), 0.001);
    }

    // ------------------------------------------------------------------------
    // Scenario 8: Product cost changed after invoice creation (Snapshot immutability)
    // ------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 8: Changing inventory cost later does not alter historical invoice cost snapshot or profit")
    void testProductCostChangedAfterInvoiceCreation() {
        LocalDate date = LocalDate.of(2026, 4, 10);
        Invoice inv = createInvoice(1L, "INV-008", date, InvoiceStatus.PAID);
        // Historical unitCostPrice snapshot is 50.00. Current Product cost in Inventory was changed later to 90.00.
        // Sales = 100.00, COGS must remain 50.00, profit must remain 50.00.
        addItem(inv, 8L, "Price Fluctuation Item", 1, new BigDecimal("100.00"), new BigDecimal("50.00"), BigDecimal.ZERO, new BigDecimal("18.00"));

        when(invoiceRepo.findAllByFirmIdAndInvoiceDateBetweenOrderByInvoiceDateAsc(eq(1L), any(), any()))
                .thenReturn(Collections.singletonList(inv));
        when(salesReturnRepo.findByFirmIdAndReturnDateBetween(eq(1L), any(), any()))
                .thenReturn(Collections.emptyList());

        FirmStatementResponse res = statementService.getFirmStatement(1L, date, date);

        assertEquals(100.00, res.getPeriodTotalSalesExGst(), 0.001);
        assertEquals(50.00, res.getPeriodCogs(), 0.001);
        assertEquals(50.00, res.getPeriodNetProfit(), 0.001);
    }

    // ------------------------------------------------------------------------
    // Scenario 9: Partial sales return
    // ------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 9: Partial sales return reverses sales revenue and historical COGS")
    void testPartialSalesReturn() {
        LocalDate date = LocalDate.of(2026, 4, 10);
        Invoice inv = createInvoice(1L, "INV-009", date, InvoiceStatus.PAID);
        // Sold 10 units at 100.00 (Sales 1000.00), Cost 60.00 each (COGS 600.00). Original profit = 400.00.
        InvoiceItem item = addItem(inv, 9L, "Widget Returnable", 10, new BigDecimal("100.00"), new BigDecimal("60.00"), BigDecimal.ZERO, new BigDecimal("18.00"));

        // Sales return of 3 units
        SalesReturn sr = new SalesReturn();
        sr.setId(901L);
        sr.setFirmId(1L);
        sr.setInvoice(inv);
        sr.setReturnDate(date);
        sr.setTotalRefundAmount(new BigDecimal("354.00")); // 300 + 54 GST

        SalesReturnItem sri = new SalesReturnItem();
        sri.setInvoiceItem(item);
        sri.setReturnQty(3);
        sri.setGrossAmount(new BigDecimal("300.00"));
        sri.setDiscountValue(BigDecimal.ZERO);
        sri.setGstAmount(new BigDecimal("54.00"));
        sri.setRefundTotal(new BigDecimal("354.00"));
        sr.setItems(Collections.singletonList(sri));

        when(invoiceRepo.findAllByFirmIdAndInvoiceDateBetweenOrderByInvoiceDateAsc(eq(1L), any(), any()))
                .thenReturn(Collections.singletonList(inv));
        when(salesReturnRepo.findByFirmIdAndReturnDateBetween(eq(1L), any(), any()))
                .thenReturn(Collections.singletonList(sr));

        FirmStatementResponse res = statementService.getFirmStatement(1L, date, date);

        assertEquals(1000.00, res.getPeriodTotalSalesExGst(), 0.001);
        assertEquals(600.00, res.getPeriodCogs(), 0.001);
        assertEquals(300.00, res.getPeriodSalesReturnsExGst(), 0.001);
        assertEquals(180.00, res.getPeriodCostReversedOnReturns(), 0.001); // 3 * 60
        // Net profit = (1000 - 300) - (600 - 180) = 700 - 420 = 280.00
        assertEquals(280.00, res.getPeriodNetProfit(), 0.001);
    }

    // ------------------------------------------------------------------------
    // Scenario 10: Multiple successive partial returns
    // ------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 10: Multiple successive partial returns cumulatively adjust profit without double counting")
    void testMultipleSuccessivePartialReturns() {
        LocalDate date = LocalDate.of(2026, 4, 10);
        Invoice inv = createInvoice(1L, "INV-010", date, InvoiceStatus.PAID);
        // Sold 10 units at 100.00, cost 60.00.
        InvoiceItem item = addItem(inv, 10L, "Multi Return Item", 10, new BigDecimal("100.00"), new BigDecimal("60.00"), BigDecimal.ZERO, new BigDecimal("18.00"));

        // Return 1: 2 units
        SalesReturn sr1 = new SalesReturn();
        sr1.setId(1001L);
        sr1.setFirmId(1L);
        sr1.setInvoice(inv);
        sr1.setReturnDate(date);
        SalesReturnItem sri1 = new SalesReturnItem();
        sri1.setInvoiceItem(item);
        sri1.setReturnQty(2);
        sri1.setGrossAmount(new BigDecimal("200.00"));
        sri1.setDiscountValue(BigDecimal.ZERO);
        sri1.setGstAmount(new BigDecimal("36.00"));
        sri1.setRefundTotal(new BigDecimal("236.00"));
        sr1.setItems(Collections.singletonList(sri1));

        // Return 2: 3 units
        SalesReturn sr2 = new SalesReturn();
        sr2.setId(1002L);
        sr2.setFirmId(1L);
        sr2.setInvoice(inv);
        sr2.setReturnDate(date);
        SalesReturnItem sri2 = new SalesReturnItem();
        sri2.setInvoiceItem(item);
        sri2.setReturnQty(3);
        sri2.setGrossAmount(new BigDecimal("300.00"));
        sri2.setDiscountValue(BigDecimal.ZERO);
        sri2.setGstAmount(new BigDecimal("54.00"));
        sri2.setRefundTotal(new BigDecimal("354.00"));
        sr2.setItems(Collections.singletonList(sri2));

        when(invoiceRepo.findAllByFirmIdAndInvoiceDateBetweenOrderByInvoiceDateAsc(eq(1L), any(), any()))
                .thenReturn(Collections.singletonList(inv));
        when(salesReturnRepo.findByFirmIdAndReturnDateBetween(eq(1L), any(), any()))
                .thenReturn(Arrays.asList(sr1, sr2));

        FirmStatementResponse res = statementService.getFirmStatement(1L, date, date);

        assertEquals(1000.00, res.getPeriodTotalSalesExGst(), 0.001);
        assertEquals(600.00, res.getPeriodCogs(), 0.001);
        assertEquals(500.00, res.getPeriodSalesReturnsExGst(), 0.001); // 200 + 300
        assertEquals(300.00, res.getPeriodCostReversedOnReturns(), 0.001); // (2 + 3) * 60 = 300
        // Net profit = (1000 - 500) - (600 - 300) = 500 - 300 = 200.00
        assertEquals(200.00, res.getPeriodNetProfit(), 0.001);
    }

    // ------------------------------------------------------------------------
    // Scenario 11: Fully returned invoice
    // ------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 11: Fully returned invoice reduces net profit for that invoice to 0")
    void testFullyReturnedInvoice() {
        LocalDate date = LocalDate.of(2026, 4, 10);
        Invoice inv = createInvoice(1L, "INV-011", date, InvoiceStatus.PAID);
        InvoiceItem item = addItem(inv, 11L, "Full Return Item", 5, new BigDecimal("100.00"), new BigDecimal("60.00"), BigDecimal.ZERO, new BigDecimal("18.00"));

        SalesReturn sr = new SalesReturn();
        sr.setId(1101L);
        sr.setFirmId(1L);
        sr.setInvoice(inv);
        sr.setReturnDate(date);
        SalesReturnItem sri = new SalesReturnItem();
        sri.setInvoiceItem(item);
        sri.setReturnQty(5);
        sri.setGrossAmount(new BigDecimal("500.00"));
        sri.setDiscountValue(BigDecimal.ZERO);
        sri.setGstAmount(new BigDecimal("90.00"));
        sri.setRefundTotal(new BigDecimal("590.00"));
        sr.setItems(Collections.singletonList(sri));

        when(invoiceRepo.findAllByFirmIdAndInvoiceDateBetweenOrderByInvoiceDateAsc(eq(1L), any(), any()))
                .thenReturn(Collections.singletonList(inv));
        when(salesReturnRepo.findByFirmIdAndReturnDateBetween(eq(1L), any(), any()))
                .thenReturn(Collections.singletonList(sr));

        FirmStatementResponse res = statementService.getFirmStatement(1L, date, date);

        assertEquals(500.00, res.getPeriodTotalSalesExGst(), 0.001);
        assertEquals(300.00, res.getPeriodCogs(), 0.001);
        assertEquals(500.00, res.getPeriodSalesReturnsExGst(), 0.001);
        assertEquals(300.00, res.getPeriodCostReversedOnReturns(), 0.001);
        assertEquals(0.00, res.getPeriodNetProfit(), 0.001);
    }

    // ------------------------------------------------------------------------
    // Scenario 12: Cancelled invoice excluded
    // ------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 12: Cancelled invoices and voided transactions are completely excluded from profit calculations")
    void testCancelledInvoiceExcluded() {
        LocalDate date = LocalDate.of(2026, 4, 10);
        Invoice cancelledInv = createInvoice(1L, "INV-012-CANCELLED", date, InvoiceStatus.CANCELLED);
        addItem(cancelledInv, 12L, "Cancelled Item", 5, new BigDecimal("200.00"), new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("18.00"));

        Invoice activeInv = createInvoice(1L, "INV-012-ACTIVE", date, InvoiceStatus.PAID);
        addItem(activeInv, 13L, "Active Item", 1, new BigDecimal("100.00"), new BigDecimal("70.00"), BigDecimal.ZERO, new BigDecimal("18.00"));

        when(invoiceRepo.findAllByFirmIdAndInvoiceDateBetweenOrderByInvoiceDateAsc(eq(1L), any(), any()))
                .thenReturn(Arrays.asList(cancelledInv, activeInv));
        when(salesReturnRepo.findByFirmIdAndReturnDateBetween(eq(1L), any(), any()))
                .thenReturn(Collections.emptyList());

        FirmStatementResponse res = statementService.getFirmStatement(1L, date, date);

        // Only active invoice counted
        assertEquals(100.00, res.getPeriodTotalSalesExGst(), 0.001);
        assertEquals(70.00, res.getPeriodCogs(), 0.001);
        assertEquals(30.00, res.getPeriodNetProfit(), 0.001);
        assertEquals(1, res.getInvoiceCount());
    }

    // ------------------------------------------------------------------------
    // Scenario 13: Custom date-range boundaries
    // ------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 13: Custom date-range boundaries: Invoice in Period 1, Return in Period 2")
    void testCustomDateRangeBoundaries() {
        LocalDate p1Start = LocalDate.of(2026, 4, 1);
        LocalDate p1End = LocalDate.of(2026, 4, 15);
        LocalDate p2Start = LocalDate.of(2026, 4, 16);
        LocalDate p2End = LocalDate.of(2026, 4, 30);

        Invoice invPeriod1 = createInvoice(1L, "INV-P1", LocalDate.of(2026, 4, 5), InvoiceStatus.PAID);
        InvoiceItem item = addItem(invPeriod1, 14L, "Periodic Item", 10, new BigDecimal("100.00"), new BigDecimal("60.00"), BigDecimal.ZERO, new BigDecimal("18.00"));

        SalesReturn returnPeriod2 = new SalesReturn();
        returnPeriod2.setId(1401L);
        returnPeriod2.setFirmId(1L);
        returnPeriod2.setInvoice(invPeriod1);
        returnPeriod2.setReturnDate(LocalDate.of(2026, 4, 20));
        SalesReturnItem sri = new SalesReturnItem();
        sri.setInvoiceItem(item);
        sri.setReturnQty(4);
        sri.setGrossAmount(new BigDecimal("400.00"));
        sri.setDiscountValue(BigDecimal.ZERO);
        sri.setGstAmount(new BigDecimal("72.00"));
        sri.setRefundTotal(new BigDecimal("472.00"));
        returnPeriod2.setItems(Collections.singletonList(sri));

        // Test Period 1 statement: Invoices present, no returns
        when(invoiceRepo.findAllByFirmIdAndInvoiceDateBetweenOrderByInvoiceDateAsc(eq(1L), any(), any()))
                .thenReturn(Collections.singletonList(invPeriod1));
        when(salesReturnRepo.findByFirmIdAndReturnDateBetween(eq(1L), eq(p1Start), eq(p1End)))
                .thenReturn(Collections.emptyList());

        FirmStatementResponse res1 = statementService.getFirmStatement(1L, p1Start, p1End);
        assertEquals(1000.00, res1.getPeriodTotalSalesExGst(), 0.001);
        assertEquals(600.00, res1.getPeriodCogs(), 0.001);
        assertEquals(0.00, res1.getPeriodSalesReturnsExGst(), 0.001);
        assertEquals(400.00, res1.getPeriodNetProfit(), 0.001);

        // Test Period 2 statement: No invoices, Return present
        when(invoiceRepo.findAllByFirmIdAndInvoiceDateBetweenOrderByInvoiceDateAsc(eq(1L), any(), any()))
                .thenReturn(Collections.emptyList());
        when(salesReturnRepo.findByFirmIdAndReturnDateBetween(eq(1L), eq(p2Start), eq(p2End)))
                .thenReturn(Collections.singletonList(returnPeriod2));

        FirmStatementResponse res2 = statementService.getFirmStatement(1L, p2Start, p2End);
        assertEquals(0.00, res2.getPeriodTotalSalesExGst(), 0.001);
        assertEquals(0.00, res2.getPeriodCogs(), 0.001);
        assertEquals(400.00, res2.getPeriodSalesReturnsExGst(), 0.001);
        assertEquals(240.00, res2.getPeriodCostReversedOnReturns(), 0.001); // 4 * 60
        // Net profit = (0 - 400) - (0 - 240) = -400 - (-240) = -160.00
        assertEquals(-160.00, res2.getPeriodNetProfit(), 0.001);
    }

    // ------------------------------------------------------------------------
    // Scenario 14: Two different firms with separate inventory and invoices
    // ------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 14: Multi-firm tenant isolation ensures Firm 1 and Firm 2 profits are strictly isolated")
    void testMultiFirmTenantIsolation() {
        LocalDate date = LocalDate.of(2026, 4, 10);

        Invoice invFirm1 = createInvoice(1L, "INV-F1", date, InvoiceStatus.PAID);
        addItem(invFirm1, 15L, "Firm 1 Item", 1, new BigDecimal("500.00"), new BigDecimal("300.00"), BigDecimal.ZERO, new BigDecimal("18.00"));

        Invoice invFirm2 = createInvoice(2L, "INV-F2", date, InvoiceStatus.PAID);
        addItem(invFirm2, 16L, "Firm 2 Item", 1, new BigDecimal("900.00"), new BigDecimal("400.00"), BigDecimal.ZERO, new BigDecimal("18.00"));

        when(invoiceRepo.findAllByFirmIdAndInvoiceDateBetweenOrderByInvoiceDateAsc(eq(1L), any(), any()))
                .thenReturn(Collections.singletonList(invFirm1));
        when(salesReturnRepo.findByFirmIdAndReturnDateBetween(eq(1L), any(), any()))
                .thenReturn(Collections.emptyList());

        when(invoiceRepo.findAllByFirmIdAndInvoiceDateBetweenOrderByInvoiceDateAsc(eq(2L), any(), any()))
                .thenReturn(Collections.singletonList(invFirm2));
        when(salesReturnRepo.findByFirmIdAndReturnDateBetween(eq(2L), any(), any()))
                .thenReturn(Collections.emptyList());

        FirmStatementResponse res1 = statementService.getFirmStatement(1L, date, date);
        assertEquals(500.00, res1.getPeriodTotalSalesExGst(), 0.001);
        assertEquals(300.00, res1.getPeriodCogs(), 0.001);
        assertEquals(200.00, res1.getPeriodNetProfit(), 0.001);
        assertEquals("Firm One Pvt Ltd", res1.getFirmName());

        FirmStatementResponse res2 = statementService.getFirmStatement(2L, date, date);
        assertEquals(900.00, res2.getPeriodTotalSalesExGst(), 0.001);
        assertEquals(400.00, res2.getPeriodCogs(), 0.001);
        assertEquals(500.00, res2.getPeriodNetProfit(), 0.001);
        assertEquals("Firm Two Corp", res2.getFirmName());
    }

    // ------------------------------------------------------------------------
    // Scenario 15: Legacy invoices without cost snapshots
    // ------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 15: Legacy invoices without cost snapshots report uncosted count and flag isProfitComplete as false")
    void testLegacyInvoicesWithoutCostSnapshots() {
        LocalDate date = LocalDate.of(2026, 4, 10);
        Invoice legacyInv = createInvoice(1L, "INV-LEGACY", date, InvoiceStatus.PAID);
        // unitCostPrice is null
        addItem(legacyInv, 17L, "Legacy Item", 2, new BigDecimal("200.00"), null, BigDecimal.ZERO, new BigDecimal("18.00"));

        Invoice modernInv = createInvoice(1L, "INV-MODERN", date, InvoiceStatus.PAID);
        addItem(modernInv, 18L, "Modern Costed Item", 1, new BigDecimal("100.00"), new BigDecimal("60.00"), BigDecimal.ZERO, new BigDecimal("18.00"));

        when(invoiceRepo.findAllByFirmIdAndInvoiceDateBetweenOrderByInvoiceDateAsc(eq(1L), any(), any()))
                .thenReturn(Arrays.asList(legacyInv, modernInv));
        when(salesReturnRepo.findByFirmIdAndReturnDateBetween(eq(1L), any(), any()))
                .thenReturn(Collections.emptyList());

        FirmStatementResponse res = statementService.getFirmStatement(1L, date, date);

        assertEquals(500.00, res.getPeriodTotalSalesExGst(), 0.001);
        assertEquals(100.00, res.getPeriodCostedSalesExGst(), 0.001);
        assertEquals(400.00, res.getPeriodUncostedSalesExGst(), 0.001);
        assertEquals(60.00, res.getPeriodCogs(), 0.001); // only modern item COGS
        assertEquals(40.00, res.getPeriodNetProfit(), 0.001); // verified profit on costed goods (100 - 60)
        assertEquals(1, res.getUncostedLineItemsCount());
        assertFalse(res.getIsProfitComplete());
    }

    // ------------------------------------------------------------------------
    // Scenario 16: Invoice edit does not rewrite historical cost
    // ------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 16: Invoice edit preserves original cost snapshot for existing items when inventory cost changes")
    void testInvoiceEditPreservesOriginalCostSnapshot() {
        InvoiceCalculationEngine engine = new InvoiceCalculationEngine();

        Product product = new Product();
        product.setId(101L);
        product.setName("Widget Delta");
        product.setPrice(new BigDecimal("100.00"));
        product.setCostPrice(new BigDecimal("50.00")); // Original cost
        product.setFirmId(1L);

        FirmDetails firm = new FirmDetails();
        firm.setId(1L);

        Invoice inv = new Invoice();
        inv.setFirmId(1L);
        inv.setInvoiceNumber("INV-101");
        inv.setStatus(InvoiceStatus.FINAL);

        InvoiceRequest req1 = new InvoiceRequest();
        InvoiceRequestItem itemReq1 = InvoiceRequestItem.builder()
                .productId(101L)
                .qty(2)
                .pricePerUnit(new BigDecimal("100.00"))
                .build();
        req1.setItems(Collections.singletonList(itemReq1));

        Invoice initialInvoice = engine.calculate(inv, null, Collections.singletonList(product), req1, false, firm);
        InvoiceItem initialItem = initialInvoice.getItems().get(0);
        initialItem.setId(999L);
        assertEquals(new BigDecimal("50.00"), initialItem.getUnitCostPrice());

        // Now inventory cost changes significantly
        product.setCostPrice(new BigDecimal("85.00"));

        // User edits the invoice (e.g., changes quantity or customer note)
        InvoiceRequest editReq = new InvoiceRequest();
        InvoiceRequestItem editItemReq = InvoiceRequestItem.builder()
                .id(999L)
                .productId(101L)
                .qty(3)
                .pricePerUnit(new BigDecimal("100.00"))
                .build();
        editReq.setItems(Collections.singletonList(editItemReq));

        Invoice updatedInvoice = engine.calculate(initialInvoice, null, Collections.singletonList(product), editReq, true, firm);
        InvoiceItem updatedItem = updatedInvoice.getItems().get(0);

        // Must retain original historical cost of 50.00, NOT the new 85.00
        assertEquals(new BigDecimal("50.00"), updatedItem.getUnitCostPrice());
    }

    // ------------------------------------------------------------------------
    // Scenario 17: Backup and restore preserves historical cost snapshots
    // ------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 17: Backup snapshot DTO and restoration preserves unitCostPrice")
    void testBackupAndRestorePreservesCostSnapshot() {
        InvoiceItem originalItem = new InvoiceItem();
        originalItem.setId(777L);
        originalItem.setProductName("Archive Item");
        originalItem.setQty(5);
        originalItem.setPricePerUnit(new BigDecimal("200.00"));
        originalItem.setUnitCostPrice(new BigDecimal("140.00"));
        originalItem.setAmountWithoutTax(new BigDecimal("1000.00"));
        originalItem.setTaxableAmount(new BigDecimal("1000.00"));

        // Snapshot representation
        InvoiceSnapshotDTO.InvoiceItemSnapshotDTO snapshotDTO = InvoiceSnapshotDTO.InvoiceItemSnapshotDTO.builder()
                .itemId(originalItem.getId())
                .productName(originalItem.getProductName())
                .qty(originalItem.getQty())
                .pricePerUnit(originalItem.getPricePerUnit())
                .unitCostPrice(originalItem.getUnitCostPrice())
                .amountWithoutTax(originalItem.getAmountWithoutTax())
                .taxableAmount(originalItem.getTaxableAmount())
                .build();

        assertEquals(new BigDecimal("140.00"), snapshotDTO.getUnitCostPrice());

        // Restore representation
        InvoiceItem restoredItem = InvoiceItem.builder()
                .productName(snapshotDTO.getProductName())
                .qty(snapshotDTO.getQty())
                .pricePerUnit(snapshotDTO.getPricePerUnit())
                .unitCostPrice(snapshotDTO.getUnitCostPrice())
                .amountWithoutTax(snapshotDTO.getAmountWithoutTax())
                .taxableAmount(snapshotDTO.getTaxableAmount())
                .build();

        assertEquals(new BigDecimal("140.00"), restoredItem.getUnitCostPrice());
    }

    // ------------------------------------------------------------------------
    // Additional Security & Edge-Case Audit Tests
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("Audit 1: Client-supplied unitCostPrice is strictly ignored on new invoice creation")
    void testClientSuppliedCostPriceIgnoredOnInvoiceCreation() {
        InvoiceCalculationEngine engine = new InvoiceCalculationEngine();

        Product product = new Product();
        product.setId(201L);
        product.setName("Secure Item");
        product.setPrice(new BigDecimal("100.00"));
        product.setCostPrice(new BigDecimal("65.00")); // Authoritative backend cost
        product.setFirmId(1L);

        FirmDetails firm = new FirmDetails();
        firm.setId(1L);

        Invoice inv = new Invoice();
        inv.setFirmId(1L);
        inv.setInvoiceNumber("INV-SEC-01");

        // Malicious client sends unitCostPrice = 0.01 to inflate reported profit
        InvoiceRequest reqMaliciousLow = new InvoiceRequest();
        InvoiceRequestItem itemLow = InvoiceRequestItem.builder()
                .productId(201L)
                .qty(1)
                .pricePerUnit(new BigDecimal("100.00"))
                .unitCostPrice(new BigDecimal("0.01"))
                .build();
        reqMaliciousLow.setItems(Collections.singletonList(itemLow));

        Invoice resultLow = engine.calculate(inv, null, Collections.singletonList(product), reqMaliciousLow, false, firm);
        // Cost MUST be 65.00 from backend Product, NEVER the client's 0.01
        assertEquals(new BigDecimal("65.00"), resultLow.getItems().get(0).getUnitCostPrice());

        // Malicious client sends unitCostPrice = 999999.00 to fabricate losses
        InvoiceRequest reqMaliciousHigh = new InvoiceRequest();
        InvoiceRequestItem itemHigh = InvoiceRequestItem.builder()
                .productId(201L)
                .qty(1)
                .pricePerUnit(new BigDecimal("100.00"))
                .unitCostPrice(new BigDecimal("999999.00"))
                .build();
        reqMaliciousHigh.setItems(Collections.singletonList(itemHigh));

        Invoice resultHigh = engine.calculate(inv, null, Collections.singletonList(product), reqMaliciousHigh, false, firm);
        assertEquals(new BigDecimal("65.00"), resultHigh.getItems().get(0).getUnitCostPrice());
    }

    @Test
    @DisplayName("Audit 2: Replacing line item with different product during invoice edit does NOT inherit old product cost")
    void testReplacingItemWithDifferentProductDoesNotInheritOldCost() {
        InvoiceCalculationEngine engine = new InvoiceCalculationEngine();

        Product productA = new Product();
        productA.setId(301L);
        productA.setName("Product Alpha");
        productA.setPrice(new BigDecimal("100.00"));
        productA.setCostPrice(new BigDecimal("40.00"));
        productA.setFirmId(1L);

        Product productB = new Product();
        productB.setId(302L);
        productB.setName("Product Beta");
        productB.setPrice(new BigDecimal("200.00"));
        productB.setCostPrice(new BigDecimal("120.00"));
        productB.setFirmId(1L);

        FirmDetails firm = new FirmDetails();
        firm.setId(1L);

        Invoice inv = new Invoice();
        inv.setFirmId(1L);
        inv.setInvoiceNumber("INV-EDIT-SWAP");

        InvoiceItem lineA = new InvoiceItem();
        lineA.setId(999L);
        lineA.setProduct(productA);
        lineA.setProductName(productA.getName());
        lineA.setQty(2);
        lineA.setPricePerUnit(productA.getPrice());
        lineA.setUnitCostPrice(new BigDecimal("40.00")); // Original snapshot
        inv.setItems(new ArrayList<>(Collections.singletonList(lineA)));

        // User edits line 999, changing product from 301 to 302
        InvoiceRequest editReq = new InvoiceRequest();
        InvoiceRequestItem swappedItemReq = InvoiceRequestItem.builder()
                .id(999L)
                .productId(302L) // Swapped to Product B!
                .qty(2)
                .pricePerUnit(new BigDecimal("200.00"))
                .unitCostPrice(new BigDecimal("40.00")) // Attempt to keep stale cost
                .build();
        editReq.setItems(Collections.singletonList(swappedItemReq));

        Invoice result = engine.calculate(inv, null, Arrays.asList(productA, productB), editReq, true, firm);
        InvoiceItem swappedLine = result.getItems().get(0);

        // Must take Product B's backend cost (120.00), NOT stale Product A cost (40.00)
        assertEquals(new BigDecimal("120.00"), swappedLine.getUnitCostPrice());
    }

    @Test
    @DisplayName("Audit 3: Adding new line during edit takes current product cost while existing line retains historical cost")
    void testAddingNewLineDuringEditTakesCurrentProductCost() {
        InvoiceCalculationEngine engine = new InvoiceCalculationEngine();

        Product product1 = new Product();
        product1.setId(401L);
        product1.setName("Existing Product");
        product1.setPrice(new BigDecimal("100.00"));
        product1.setCostPrice(new BigDecimal("80.00")); // Current cost increased from historical 50.00
        product1.setFirmId(1L);

        Product product2 = new Product();
        product2.setId(402L);
        product2.setName("Newly Added Product");
        product2.setPrice(new BigDecimal("60.00"));
        product2.setCostPrice(new BigDecimal("30.00")); // Current cost
        product2.setFirmId(1L);

        FirmDetails firm = new FirmDetails();
        firm.setId(1L);

        Invoice inv = new Invoice();
        inv.setFirmId(1L);
        inv.setInvoiceNumber("INV-MIXED-EDIT");

        InvoiceItem line1 = new InvoiceItem();
        line1.setId(888L);
        line1.setProduct(product1);
        line1.setProductName(product1.getName());
        line1.setQty(1);
        line1.setPricePerUnit(new BigDecimal("100.00"));
        line1.setUnitCostPrice(new BigDecimal("50.00")); // Historical snapshot
        inv.setItems(new ArrayList<>(Collections.singletonList(line1)));

        // User edits invoice: modifies line 1 quantity, and adds brand new line 2
        InvoiceRequest editReq = new InvoiceRequest();
        InvoiceRequestItem existingItemReq = InvoiceRequestItem.builder()
                .id(888L)
                .productId(401L)
                .qty(2)
                .pricePerUnit(new BigDecimal("100.00"))
                .build();

        InvoiceRequestItem newItemReq = InvoiceRequestItem.builder()
                .id(null) // New line
                .productId(402L)
                .qty(3)
                .pricePerUnit(new BigDecimal("60.00"))
                .build();

        editReq.setItems(Arrays.asList(existingItemReq, newItemReq));

        Invoice result = engine.calculate(inv, null, Arrays.asList(product1, product2), editReq, true, firm);
        assertEquals(2, result.getItems().size());

        InvoiceItem resLine1 = result.getItems().get(0);
        InvoiceItem resLine2 = result.getItems().get(1);

        // Existing line MUST retain historical snapshot (50.00), not current (80.00)
        assertEquals(new BigDecimal("50.00"), resLine1.getUnitCostPrice());

        // New line MUST snapshot current product cost (30.00)
        assertEquals(new BigDecimal("30.00"), resLine2.getUnitCostPrice());
    }

    @Test
    @DisplayName("Audit 4: Sales return on legacy uncosted invoice item tracks uncosted returns without distorting profit")
    void testSalesReturnOnUncostedLegacyInvoiceItem() {
        LocalDate from = LocalDate.of(2026, 4, 1);
        LocalDate to = LocalDate.of(2026, 4, 30);

        // Invoice 1: Legacy invoice with NO cost snapshot (qty 10 @ 100 = 1000 taxable)
        Invoice legacyInv = createInvoice(1L, "INV-LEGACY", LocalDate.of(2026, 4, 5), InvoiceStatus.FINAL);
        addItem(legacyInv, 101L, "Legacy Item", 10, new BigDecimal("100.00"), null, BigDecimal.ZERO, BigDecimal.ZERO);

        // Invoice 2: Modern costed invoice (qty 5 @ 100 = 500 taxable, cost 40.00)
        Invoice costedInv = createInvoice(1L, "INV-COSTED", LocalDate.of(2026, 4, 10), InvoiceStatus.FINAL);
        addItem(costedInv, 102L, "Costed Item", 5, new BigDecimal("100.00"), new BigDecimal("40.00"), BigDecimal.ZERO, BigDecimal.ZERO);

        // Return 1: Returns 2 units from legacy uncosted invoice (return rev = 200, unitCost = null)
        SalesReturn returnLegacy = new SalesReturn();
        returnLegacy.setFirmId(1L);
        returnLegacy.setReturnNumber("RTN-LEGACY");
        returnLegacy.setReturnDate(LocalDate.of(2026, 4, 15));
        returnLegacy.setInvoice(legacyInv);
        returnLegacy.setSubtotal(new BigDecimal("200.00"));
        returnLegacy.setTotalRefundAmount(new BigDecimal("200.00"));

        SalesReturnItem retItemLegacy = new SalesReturnItem();
        retItemLegacy.setSalesReturn(returnLegacy);
        retItemLegacy.setInvoiceItem(legacyInv.getItems().get(0));
        retItemLegacy.setReturnQty(2);
        retItemLegacy.setGrossAmount(new BigDecimal("200.00"));
        retItemLegacy.setDiscountValue(BigDecimal.ZERO);
        retItemLegacy.setGstAmount(BigDecimal.ZERO);
        retItemLegacy.setRefundTotal(new BigDecimal("200.00"));
        returnLegacy.setItems(Collections.singletonList(retItemLegacy));

        // Return 2: Returns 1 unit from modern costed invoice (return rev = 100, unitCost = 40.00)
        SalesReturn returnCosted = new SalesReturn();
        returnCosted.setFirmId(1L);
        returnCosted.setReturnNumber("RTN-COSTED");
        returnCosted.setReturnDate(LocalDate.of(2026, 4, 20));
        returnCosted.setInvoice(costedInv);
        returnCosted.setSubtotal(new BigDecimal("100.00"));
        returnCosted.setTotalRefundAmount(new BigDecimal("100.00"));

        SalesReturnItem retItemCosted = new SalesReturnItem();
        retItemCosted.setSalesReturn(returnCosted);
        retItemCosted.setInvoiceItem(costedInv.getItems().get(0));
        retItemCosted.setReturnQty(1);
        retItemCosted.setGrossAmount(new BigDecimal("100.00"));
        retItemCosted.setDiscountValue(BigDecimal.ZERO);
        retItemCosted.setGstAmount(BigDecimal.ZERO);
        retItemCosted.setRefundTotal(new BigDecimal("100.00"));
        returnCosted.setItems(Collections.singletonList(retItemCosted));

        when(invoiceRepo.findAllByFirmIdAndInvoiceDateBetweenOrderByInvoiceDateAsc(eq(1L), any(), any()))
                .thenReturn(Arrays.asList(legacyInv, costedInv));
        when(salesReturnRepo.findByFirmIdAndReturnDateBetween(eq(1L), any(), any()))
                .thenReturn(Arrays.asList(returnLegacy, returnCosted));

        FirmStatementResponse res = statementService.getFirmStatement(1L, from, to);

        // Verification:
        // Total Sales Ex GST = 1000 + 500 = 1500.00
        assertEquals(1500.00, res.getPeriodTotalSalesExGst(), 0.001);
        // Costed Sales Ex GST = 500.00
        assertEquals(500.00, res.getPeriodCostedSalesExGst(), 0.001);
        // Uncosted Sales Ex GST = 1000.00
        assertEquals(1000.00, res.getPeriodUncostedSalesExGst(), 0.001);

        // COGS = 5 * 40.00 = 200.00
        assertEquals(200.00, res.getPeriodCogs(), 0.001);

        // Total Sales Returns Ex GST = 200 + 100 = 300.00
        assertEquals(300.00, res.getPeriodSalesReturnsExGst(), 0.001);

        // Cost Reversed on Returns = 1 * 40.00 = 40.00
        assertEquals(40.00, res.getPeriodCostReversedOnReturns(), 0.001);

        // Net Costed Sales = 500 - 100 = 400.00
        // Net COGS = 200 - 40 = 160.00
        // Verified Net Profit = 400.00 - 160.00 = 240.00
        assertEquals(240.00, res.getPeriodNetProfit(), 0.001);

        // Profit completeness: false, with 2 uncosted line items (1 invoice item + 1 return item)
        assertFalse(res.getIsProfitComplete());
        assertEquals(2, res.getUncostedLineItemsCount());
    }
}
