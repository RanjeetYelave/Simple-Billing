package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dtos.migration.InvoiceMigrationDTOs.*;
import com.billing.simple.billsoft.dtos.migration.InvoiceSnapshotDTO;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.forecast.repository.ForecastSalesTransactionRepository;
import com.billing.simple.billsoft.forecast.service.ForecastAggregationService;
import com.billing.simple.billsoft.forecast.service.ForecastIngestionService;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Invoice Migration Service Unit Tests")
public class InvoiceMigrationServiceUnitTest {

    @Mock private InvoiceRepository invoiceRepo;
    @Mock private CustomerRepository customerRepo;
    @Mock private ProductRepository productRepo;
    @Mock private SavedItemRepository savedItemRepo;
    @Mock private StockMovementRepository stockMovementRepo;
    @Mock private InvoicePaymentRepository invoicePaymentRepo;
    @Mock private SalesReturnRepository salesReturnRepo;
    @Mock private SalesReturnItemRepository salesReturnItemRepo;
    @Mock private FirmDetailsRepository firmDetailsRepo;
    @Mock private InvoiceMigrationRecordRepository migrationRecordRepo;
    @Mock private ProductService productService;
    @Mock private InvoiceService invoiceService;
    @Mock private ForecastIngestionService forecastIngestionService;
    @Mock private ForecastAggregationService forecastAggregationService;
    @Mock private ForecastSalesTransactionRepository forecastTransactionRepo;
    @Mock private ReminderRepository reminderRepo;

    @InjectMocks
    private InvoiceMigrationService migrationService;

    private Long sourceFirmId = 1L;
    private Long targetFirmId = 2L;
    private Long invoiceId = 100L;

    private Invoice mockInvoice;
    private Customer mockCustomerA;
    private Product mockProductA;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentFirmId(sourceFirmId);

        mockCustomerA = Customer.builder()
                .id(10L)
                .name("Acme Corp")
                .phone("9876543210")
                .gstin("27AAACA1234A1Z5")
                .firmId(sourceFirmId)
                .build();

        mockProductA = Product.builder()
                .id(50L)
                .name("Industrial Widget")
                .sku("IND-WID-01")
                .price(new BigDecimal("1000.00"))
                .stockQuantity(new BigDecimal("50.000"))
                .firmId(sourceFirmId)
                .itemType("GOODS")
                .build();

        InvoiceItem item = InvoiceItem.builder()
                .id(1L)
                .product(mockProductA)
                .productName(mockProductA.getName())
                .qty(5)
                .pricePerUnit(new BigDecimal("1000.00"))
                .amountWithoutTax(new BigDecimal("5000.00"))
                .taxableAmount(new BigDecimal("5000.00"))
                .gstPercent(new BigDecimal("18.00"))
                .gstAmount(new BigDecimal("900.00"))
                .lineTotal(new BigDecimal("5900.00"))
                .build();

        mockInvoice = Invoice.builder()
                .id(invoiceId)
                .firmId(sourceFirmId)
                .invoiceNumber("INV-A-1001")
                .invoiceDate(LocalDateTime.now())
                .customer(mockCustomerA)
                .items(new ArrayList<>(Collections.singletonList(item)))
                .status(InvoiceStatus.UNPAID)
                .paid(false)
                .totalAmount(new BigDecimal("5900.00"))
                .convertedInvoiceId(555L) // Quotation link
                .build();

        item.setInvoice(mockInvoice);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private FirmDetails createFirmDetails(Long id, String name) {
        FirmDetails fd = new FirmDetails();
        fd.setId(id);
        fd.setFirmName(name);
        return fd;
    }

    @Test
    @DisplayName("Preview migration: Eligible invoice with matching customer and product")
    void testPreviewMigration_Eligible() {
        when(invoiceRepo.findById(invoiceId)).thenReturn(Optional.of(mockInvoice));
        when(firmDetailsRepo.findById(sourceFirmId)).thenReturn(Optional.of(createFirmDetails(sourceFirmId, "Firm A")));
        when(firmDetailsRepo.findById(targetFirmId)).thenReturn(Optional.of(createFirmDetails(targetFirmId, "Firm B")));
        when(invoiceService.peekNextInvoiceNumber(targetFirmId)).thenReturn("INV-B-2001");
        when(invoicePaymentRepo.findByInvoiceIdOrderByPaymentDateAscIdAsc(invoiceId)).thenReturn(Collections.emptyList());
        when(salesReturnRepo.findByInvoiceIdOrderByCreatedAtDesc(invoiceId)).thenReturn(Collections.emptyList());

        // Matching customer in B
        Customer customerB = Customer.builder().id(20L).name("Acme Corp").phone("9876543210").gstin("27AAACA1234A1Z5").firmId(targetFirmId).build();
        when(customerRepo.findByFirmIdAndGstinIgnoreCase(targetFirmId, "27AAACA1234A1Z5")).thenReturn(Collections.singletonList(customerB));

        // Matching product in B
        Product productB = Product.builder().id(60L).name("Industrial Widget").sku("IND-WID-01").price(new BigDecimal("1100.00")).stockQuantity(new BigDecimal("100.000")).firmId(targetFirmId).itemType("GOODS").build();
        when(productRepo.findByFirmIdAndSku(targetFirmId, "IND-WID-01")).thenReturn(Collections.singletonList(productB));

        InvoiceMigrationPreviewResponse preview = migrationService.previewMigration(invoiceId, targetFirmId);

        assertNotNull(preview);
        assertTrue(preview.isEligible());
        assertEquals("INV-B-2001", preview.getEstimatedTargetInvoiceNumber());
        assertEquals("EXACT_MATCH", preview.getCustomerResolution().getMatchType());
        assertEquals(20L, preview.getCustomerResolution().getMatchedCustomer().getCustomerId());
        assertEquals(1, preview.getProductResolutions().size());
        assertEquals("INVENTORY_PRODUCT", preview.getProductResolutions().get(0).getResolutionType());
        assertEquals(new BigDecimal("5"), preview.getProductResolutions().get(0).getStockDeduction());
    }

    @Test
    @DisplayName("Preview migration: Unmatched product reports AUTO_CREATE_PRODUCT without DB mutations")
    void testPreviewMigration_UnmatchedProduct_ShowsAutoCreateProduct() {
        when(invoiceRepo.findById(invoiceId)).thenReturn(Optional.of(mockInvoice));
        when(firmDetailsRepo.findById(sourceFirmId)).thenReturn(Optional.of(createFirmDetails(sourceFirmId, "Firm A")));
        when(firmDetailsRepo.findById(targetFirmId)).thenReturn(Optional.of(createFirmDetails(targetFirmId, "Firm B")));
        when(invoiceService.peekNextInvoiceNumber(targetFirmId)).thenReturn("INV-B-2001");
        when(invoicePaymentRepo.findByInvoiceIdOrderByPaymentDateAscIdAsc(invoiceId)).thenReturn(Collections.emptyList());
        when(salesReturnRepo.findByInvoiceIdOrderByCreatedAtDesc(invoiceId)).thenReturn(Collections.emptyList());

        // Matching customer in B
        Customer customerB = Customer.builder().id(20L).name("Acme Corp").phone("9876543210").gstin("27AAACA1234A1Z5").firmId(targetFirmId).build();
        when(customerRepo.findByFirmIdAndGstinIgnoreCase(targetFirmId, "27AAACA1234A1Z5")).thenReturn(Collections.singletonList(customerB));

        // No product match in B
        when(productRepo.findByFirmIdAndSku(targetFirmId, "IND-WID-01")).thenReturn(Collections.emptyList());
        when(productRepo.findByFirmIdAndNameIgnoreCase(targetFirmId, "Industrial Widget")).thenReturn(Collections.emptyList());

        InvoiceMigrationPreviewResponse preview = migrationService.previewMigration(invoiceId, targetFirmId);

        assertNotNull(preview);
        assertTrue(preview.isEligible());
        assertEquals(1, preview.getProductResolutions().size());
        assertEquals("AUTO_CREATE_PRODUCT", preview.getProductResolutions().get(0).getResolutionType());
        assertEquals(new BigDecimal("5"), preview.getProductResolutions().get(0).getStockDeduction());
        assertEquals("Industrial Widget", preview.getProductResolutions().get(0).getOriginalProductName());

        // Verify no mutations happened
        verify(productRepo, never()).save(any());
        verify(invoiceRepo, never()).save(any());
    }

    @Test
    @DisplayName("Preview migration: Blocked when invoice has existing payments")
    void testPreviewMigration_BlockedByPayments() {
        when(invoiceRepo.findById(invoiceId)).thenReturn(Optional.of(mockInvoice));
        when(invoicePaymentRepo.findByInvoiceIdOrderByPaymentDateAscIdAsc(invoiceId))
                .thenReturn(Collections.singletonList(InvoicePayment.builder().id(1L).amount(new BigDecimal("500.00")).build()));

        InvoiceMigrationPreviewResponse preview = migrationService.previewMigration(invoiceId, targetFirmId);

        assertNotNull(preview);
        assertFalse(preview.isEligible());
        assertTrue(preview.isHasPayments());
        assertTrue(preview.getIneligibilityReason().contains("payments"));
    }

    @Test
    @DisplayName("Preview migration: Blocked when invoice has existing sales returns")
    void testPreviewMigration_BlockedByReturns() {
        when(invoiceRepo.findById(invoiceId)).thenReturn(Optional.of(mockInvoice));
        when(invoicePaymentRepo.findByInvoiceIdOrderByPaymentDateAscIdAsc(invoiceId)).thenReturn(Collections.emptyList());
        when(salesReturnRepo.findByInvoiceIdOrderByCreatedAtDesc(invoiceId))
                .thenReturn(Collections.singletonList(SalesReturn.builder().id(1L).returnNumber("RET-01").build()));

        InvoiceMigrationPreviewResponse preview = migrationService.previewMigration(invoiceId, targetFirmId);

        assertNotNull(preview);
        assertFalse(preview.isEligible());
        assertTrue(preview.isHasReturns());
        assertTrue(preview.getIneligibilityReason().contains("returns"));
    }

    @Test
    @DisplayName("Execute Forward Migration: Successful A -> B with Stock Inversion in A and Deduction in B")
    void testMigrateInvoice_Success() {
        when(invoiceRepo.findByIdForUpdate(invoiceId)).thenReturn(Optional.of(mockInvoice));
        when(firmDetailsRepo.findById(targetFirmId)).thenReturn(Optional.of(createFirmDetails(targetFirmId, "Firm B")));
        when(invoicePaymentRepo.findByInvoiceIdOrderByPaymentDateAscIdAsc(invoiceId)).thenReturn(Collections.emptyList());
        when(salesReturnRepo.findByInvoiceIdOrderByCreatedAtDesc(invoiceId)).thenReturn(Collections.emptyList());

        // Stock movement inversion in A (5 units previously deducted)
        List<Object[]> movList = new ArrayList<>();
        movList.add(new Object[]{50L, new BigDecimal("-5.000")});
        when(stockMovementRepo.sumQuantityChangeByProductIdForInvoice(eq(sourceFirmId), anyString(), anyString())).thenReturn(movList);

        // Target customer resolution (auto-create)
        Customer createdCustomerB = Customer.builder().id(30L).name("Acme Corp").firmId(targetFirmId).build();
        when(customerRepo.save(any(Customer.class))).thenReturn(createdCustomerB);

        // Target product match
        Product productB = Product.builder().id(70L).name("Industrial Widget").sku("IND-WID-01").firmId(targetFirmId).itemType("GOODS").build();
        when(productRepo.findByFirmIdAndSku(targetFirmId, "IND-WID-01")).thenReturn(Collections.singletonList(productB));

        // Generate target invoice number
        when(invoiceService.generateInvoiceNumber(targetFirmId)).thenReturn("INV-B-5001");
        when(invoiceRepo.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));
        when(migrationRecordRepo.save(any(InvoiceMigrationRecord.class))).thenAnswer(i -> {
            InvoiceMigrationRecord r = i.getArgument(0);
            r.setId(999L);
            return r;
        });

        InvoiceMigrationRequest request = InvoiceMigrationRequest.builder()
                .targetFirmId(targetFirmId)
                .createCustomerIfMissing(true)
                .build();

        InvoiceMigrationResponse response = migrationService.migrateInvoice(invoiceId, request);

        assertNotNull(response);
        assertEquals(targetFirmId, response.getTargetFirmId());
        assertEquals("INV-B-5001", response.getTargetInvoiceNumber());

        // Verify Firm A stock was restored (+5.000)
        verify(productService).recordStockMovement(
                eq(50L), eq(sourceFirmId), eq("INVOICE_MIGRATION_REVERSAL"), eq(new BigDecimal("5.000")), eq("INVOICE"), anyString(), anyString()
        );

        // Verify Firm B stock was deducted (-5.000)
        verify(productService).recordStockMovement(
                eq(70L), eq(targetFirmId), eq("INVOICE_SALE"), eq(new BigDecimal("-5")), eq("INVOICE"), eq("INV-B-5001"), anyString()
        );

        // Verify convertedInvoiceId quotation detached
        assertNull(mockInvoice.getConvertedInvoiceId());

        // Verify forecast synchronization
        verify(forecastTransactionRepo).deleteAllByFirmIdAndSourceTypeAndSourceHeaderId(sourceFirmId, "SALE", invoiceId);
        verify(forecastIngestionService).syncInvoice(mockInvoice);
        verify(forecastAggregationService).aggregateProductDailyMetrics(eq(sourceFirmId), eq(50L), any());
        verify(forecastAggregationService).aggregateProductDailyMetrics(eq(targetFirmId), eq(70L), any());
    }

    @Test
    @DisplayName("Execute Revert Migration: Successful B -> A restoring snapshot and sequence number")
    void testRevertMigration_Success() throws Exception {
        // Prepare migrated invoice in Firm B
        mockInvoice.setFirmId(targetFirmId);
        mockInvoice.setInvoiceNumber("INV-B-5001");
        mockInvoice.setConvertedInvoiceId(null);

        when(invoiceRepo.findByIdForUpdate(invoiceId)).thenReturn(Optional.of(mockInvoice));
        when(firmDetailsRepo.findById(sourceFirmId)).thenReturn(Optional.of(createFirmDetails(sourceFirmId, "Firm A")));
        when(invoicePaymentRepo.findByInvoiceIdOrderByPaymentDateAscIdAsc(invoiceId)).thenReturn(Collections.emptyList());
        when(salesReturnRepo.findByInvoiceIdOrderByCreatedAtDesc(invoiceId)).thenReturn(Collections.emptyList());

        // Active migration record with snapshot of original Firm A state
        InvoiceSnapshotDTO snapshot = InvoiceSnapshotDTO.builder()
                .invoiceId(invoiceId)
                .sourceFirmId(sourceFirmId)
                .invoiceNumber("INV-A-1001")
                .customer(InvoiceSnapshotDTO.CustomerSnapshotDTO.builder().customerId(10L).name("Acme Corp").build())
                .status(InvoiceStatus.UNPAID)
                .convertedInvoiceId(555L)
                .items(Collections.singletonList(InvoiceSnapshotDTO.InvoiceItemSnapshotDTO.builder()
                        .productId(50L)
                        .productName("Industrial Widget")
                        .qty(5)
                        .pricePerUnit(new BigDecimal("1000.00"))
                        .taxableAmount(new BigDecimal("5000.00"))
                        .gstPercent(new BigDecimal("18.00"))
                        .lineTotal(new BigDecimal("5900.00"))
                        .build()))
                .build();

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        String snapshotJson = mapper.writeValueAsString(snapshot);

        InvoiceMigrationRecord activeRecord = InvoiceMigrationRecord.builder()
                .id(101L)
                .invoiceId(invoiceId)
                .sourceFirmId(sourceFirmId)
                .targetFirmId(targetFirmId)
                .sourceInvoiceNumber("INV-A-1001")
                .targetInvoiceNumber("INV-B-5001")
                .status("ACTIVE")
                .preMigrationSnapshot(snapshotJson)
                .build();

        when(migrationRecordRepo.findFirstByInvoiceIdAndStatusOrderByCreatedAtDesc(invoiceId, "ACTIVE"))
                .thenReturn(Optional.of(activeRecord));

        // Restore Firm A customer
        when(customerRepo.findByIdAndFirmId(10L, sourceFirmId)).thenReturn(Optional.of(mockCustomerA));
        // Restore Firm A product
        when(productRepo.findByIdAndFirmId(50L, sourceFirmId)).thenReturn(Optional.of(mockProductA));
        // Quotation in Firm A exists
        when(invoiceRepo.existsByIdAndFirmId(555L, sourceFirmId)).thenReturn(true);

        // New monotonic sequence in Firm A
        when(invoiceService.generateInvoiceNumber(sourceFirmId)).thenReturn("INV-A-1002");
        when(invoiceRepo.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));
        when(migrationRecordRepo.save(any(InvoiceMigrationRecord.class))).thenAnswer(i -> i.getArgument(0));

        InvoiceMigrationResponse revertResponse = migrationService.revertMigration(invoiceId);

        assertNotNull(revertResponse);
        assertEquals(sourceFirmId, revertResponse.getTargetFirmId());
        assertEquals("INV-A-1002", revertResponse.getTargetInvoiceNumber());
        assertTrue(revertResponse.getIsRevert());

        // Verify Firm B status marked REVERTED
        assertEquals("REVERTED", activeRecord.getStatus());

        // Verify quotation re-linked
        assertEquals(555L, mockInvoice.getConvertedInvoiceId());

        // Verify Firm A stock deducted on revert
        verify(productService).recordStockMovement(
                eq(50L), eq(sourceFirmId), eq("INVOICE_SALE"), eq(new BigDecimal("-5")), eq("INVOICE"), eq("INV-A-1002"), anyString()
        );
    }

    @Test
    @DisplayName("Forward migration: Missing product falls back to Saved Item (no stock movement in target)")
    void testMigrateInvoice_SavedItemFallback() {
        when(invoiceRepo.findByIdForUpdate(invoiceId)).thenReturn(Optional.of(mockInvoice));
        when(firmDetailsRepo.findById(targetFirmId)).thenReturn(Optional.of(createFirmDetails(targetFirmId, "Firm B")));
        when(invoicePaymentRepo.findByInvoiceIdOrderByPaymentDateAscIdAsc(invoiceId)).thenReturn(Collections.emptyList());
        when(salesReturnRepo.findByInvoiceIdOrderByCreatedAtDesc(invoiceId)).thenReturn(Collections.emptyList());

        // Stock movement inversion in A (5 units)
        List<Object[]> movList = new ArrayList<>();
        movList.add(new Object[]{50L, new BigDecimal("-5.000")});
        when(stockMovementRepo.sumQuantityChangeByProductIdForInvoice(eq(sourceFirmId), anyString(), anyString())).thenReturn(movList);

        // Auto-create customer
        Customer createdCustomerB = Customer.builder().id(30L).name("Acme Corp").firmId(targetFirmId).build();
        when(customerRepo.save(any(Customer.class))).thenReturn(createdCustomerB);

        // Product NOT found in target firm by SKU or Name -> Auto-creates UNCATEGORIZED product
        when(productRepo.findByFirmIdAndSku(targetFirmId, "IND-WID-01")).thenReturn(Collections.emptyList());
        when(productRepo.findByFirmIdAndNameIgnoreCase(targetFirmId, "Industrial Widget")).thenReturn(Collections.emptyList());
        when(productRepo.findFirstByFirmIdAndSku(targetFirmId, "IND-WID-01")).thenReturn(Optional.empty());

        Product createdProdB = Product.builder()
                .id(75L)
                .name("Industrial Widget")
                .category("UNCATEGORIZED")
                .sku("IND-WID-01")
                .firmId(targetFirmId)
                .price(new BigDecimal("1000.00"))
                .stockQuantity(BigDecimal.ZERO)
                .itemType("GOODS")
                .build();
        when(productRepo.save(any(Product.class))).thenReturn(createdProdB);

        when(invoiceService.generateInvoiceNumber(targetFirmId)).thenReturn("INV-B-5002");
        when(invoiceRepo.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));
        when(migrationRecordRepo.save(any(InvoiceMigrationRecord.class))).thenAnswer(i -> {
            InvoiceMigrationRecord r = i.getArgument(0);
            r.setId(1000L);
            return r;
        });

        InvoiceMigrationRequest request = InvoiceMigrationRequest.builder()
                .targetFirmId(targetFirmId)
                .createCustomerIfMissing(true)
                .build();

        InvoiceMigrationResponse response = migrationService.migrateInvoice(invoiceId, request);

        assertNotNull(response);
        assertEquals("INV-B-5002", response.getTargetInvoiceNumber());

        // Firm A stock restored
        verify(productService).recordStockMovement(
                eq(50L), eq(sourceFirmId), eq("INVOICE_MIGRATION_REVERSAL"), eq(new BigDecimal("5.000")), eq("INVOICE"), anyString(), anyString()
        );

        // Item product is linked to newly created Firm B product
        assertNotNull(mockInvoice.getItems().get(0).getProduct());
        assertEquals(75L, mockInvoice.getItems().get(0).getProduct().getId());
        assertEquals("UNCATEGORIZED", mockInvoice.getItems().get(0).getProduct().getCategory());
        assertEquals("Industrial Widget", mockInvoice.getItems().get(0).getProductName());

        // Inventory deduction applied in target firm B on newly created product
        verify(productService).recordStockMovement(
                eq(75L), eq(targetFirmId), eq("INVOICE_SALE"), eq(new BigDecimal("-5")), eq("INVOICE"), anyString(), anyString()
        );
    }

    @Test
    @DisplayName("Forward migration: Blocked when invoice is CANCELLED")
    void testMigrateInvoice_BlockedWhenCancelled() {
        mockInvoice.setStatus(InvoiceStatus.CANCELLED);
        when(invoiceRepo.findByIdForUpdate(invoiceId)).thenReturn(Optional.of(mockInvoice));
        when(firmDetailsRepo.findById(targetFirmId)).thenReturn(Optional.of(createFirmDetails(targetFirmId, "Firm B")));

        InvoiceMigrationRequest request = InvoiceMigrationRequest.builder().targetFirmId(targetFirmId).build();
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> migrationService.migrateInvoice(invoiceId, request));
        assertTrue(ex.getMessage().toLowerCase().contains("cancelled"));
    }

    @Test
    @DisplayName("Forward migration: Blocked when targetFirmId equals sourceFirmId")
    void testMigrateInvoice_BlockedWhenSameFirm() {
        when(invoiceRepo.findByIdForUpdate(invoiceId)).thenReturn(Optional.of(mockInvoice));

        InvoiceMigrationRequest request = InvoiceMigrationRequest.builder().targetFirmId(sourceFirmId).build();
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> migrationService.migrateInvoice(invoiceId, request));
        assertTrue(ex.getMessage().contains("already belongs to firm") || ex.getMessage().contains("already belongs to target firm"));
    }

    @Test
    @DisplayName("Forward migration: Reminders associated with invoice are migrated to target firm")
    void testMigrateInvoice_RemindersMigrated() {
        when(invoiceRepo.findByIdForUpdate(invoiceId)).thenReturn(Optional.of(mockInvoice));
        when(firmDetailsRepo.findById(targetFirmId)).thenReturn(Optional.of(createFirmDetails(targetFirmId, "Firm B")));
        when(invoicePaymentRepo.findByInvoiceIdOrderByPaymentDateAscIdAsc(invoiceId)).thenReturn(Collections.emptyList());
        when(salesReturnRepo.findByInvoiceIdOrderByCreatedAtDesc(invoiceId)).thenReturn(Collections.emptyList());

        Customer customerB = Customer.builder().id(30L).name("Acme Corp").firmId(targetFirmId).build();
        when(customerRepo.save(any(Customer.class))).thenReturn(customerB);
        when(productRepo.findByFirmIdAndSku(targetFirmId, "IND-WID-01")).thenReturn(Collections.emptyList());
        when(invoiceService.generateInvoiceNumber(targetFirmId)).thenReturn("INV-B-5003");
        when(invoiceRepo.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));
        when(migrationRecordRepo.save(any(InvoiceMigrationRecord.class))).thenAnswer(i -> i.getArgument(0));

        Reminder reminder = Reminder.builder().id(201L).invoiceId(invoiceId).firmId(sourceFirmId).customerId(10L).build();
        when(reminderRepo.findByFirmIdAndInvoiceId(sourceFirmId, invoiceId)).thenReturn(Collections.singletonList(reminder));

        InvoiceMigrationRequest request = InvoiceMigrationRequest.builder().targetFirmId(targetFirmId).createCustomerIfMissing(true).build();
        migrationService.migrateInvoice(invoiceId, request);

        // Reminder re-homed to target firm and target customer
        assertEquals(targetFirmId, reminder.getFirmId());
        assertEquals(30L, reminder.getCustomerId());
        verify(reminderRepo).save(reminder);
    }

    @Test
    @DisplayName("Customer matching: GSTIN-only match returns EXACT_MATCH with GSTIN reason")
    void testCustomerResolution_GstinOnlyMatch() {
        when(invoiceRepo.findById(invoiceId)).thenReturn(Optional.of(mockInvoice));
        when(firmDetailsRepo.findById(sourceFirmId)).thenReturn(Optional.of(createFirmDetails(sourceFirmId, "Firm A")));
        when(firmDetailsRepo.findById(targetFirmId)).thenReturn(Optional.of(createFirmDetails(targetFirmId, "Firm B")));
        when(invoiceService.peekNextInvoiceNumber(targetFirmId)).thenReturn("INV-B-2001");
        when(invoicePaymentRepo.findByInvoiceIdOrderByPaymentDateAscIdAsc(invoiceId)).thenReturn(Collections.emptyList());
        when(salesReturnRepo.findByInvoiceIdOrderByCreatedAtDesc(invoiceId)).thenReturn(Collections.emptyList());

        Customer customerB = Customer.builder().id(25L).name("Different Name Ltd").phone("9000000000").gstin("27AAACA1234A1Z5").firmId(targetFirmId).build();
        when(customerRepo.findByFirmIdAndGstinIgnoreCase(targetFirmId, "27AAACA1234A1Z5")).thenReturn(Collections.singletonList(customerB));
        when(customerRepo.findByFirmIdAndPhone(targetFirmId, "9876543210")).thenReturn(Collections.emptyList());
        when(customerRepo.findByFirmIdAndNameIgnoreCase(targetFirmId, "Acme Corp")).thenReturn(Collections.emptyList());

        InvoiceMigrationPreviewResponse preview = migrationService.previewMigration(invoiceId, targetFirmId);

        assertEquals("EXACT_MATCH", preview.getCustomerResolution().getMatchType());
        assertEquals(25L, preview.getCustomerResolution().getMatchedCustomer().getCustomerId());
        assertEquals("GSTIN", preview.getCustomerResolution().getMatchedCustomer().getMatchReason());
    }

    @Test
    @DisplayName("Customer matching: Phone-only match returns EXACT_MATCH with PHONE reason")
    void testCustomerResolution_PhoneOnlyMatch() {
        when(invoiceRepo.findById(invoiceId)).thenReturn(Optional.of(mockInvoice));
        when(firmDetailsRepo.findById(sourceFirmId)).thenReturn(Optional.of(createFirmDetails(sourceFirmId, "Firm A")));
        when(firmDetailsRepo.findById(targetFirmId)).thenReturn(Optional.of(createFirmDetails(targetFirmId, "Firm B")));
        when(invoiceService.peekNextInvoiceNumber(targetFirmId)).thenReturn("INV-B-2001");
        when(invoicePaymentRepo.findByInvoiceIdOrderByPaymentDateAscIdAsc(invoiceId)).thenReturn(Collections.emptyList());
        when(salesReturnRepo.findByInvoiceIdOrderByCreatedAtDesc(invoiceId)).thenReturn(Collections.emptyList());

        Customer customerB = Customer.builder().id(26L).name("Other Name").phone("9876543210").gstin("27OTHER0000A1Z0").firmId(targetFirmId).build();
        when(customerRepo.findByFirmIdAndGstinIgnoreCase(targetFirmId, "27AAACA1234A1Z5")).thenReturn(Collections.emptyList());
        when(customerRepo.findByFirmIdAndPhone(targetFirmId, "9876543210")).thenReturn(Collections.singletonList(customerB));
        when(customerRepo.findByFirmIdAndNameIgnoreCase(targetFirmId, "Acme Corp")).thenReturn(Collections.emptyList());

        InvoiceMigrationPreviewResponse preview = migrationService.previewMigration(invoiceId, targetFirmId);

        assertEquals("EXACT_MATCH", preview.getCustomerResolution().getMatchType());
        assertEquals(26L, preview.getCustomerResolution().getMatchedCustomer().getCustomerId());
        assertEquals("PHONE", preview.getCustomerResolution().getMatchedCustomer().getMatchReason());
    }

    @Test
    @DisplayName("Customer matching: Multiple matches across dimensions return AMBIGUOUS")
    void testCustomerResolution_MultipleMatchesAmbiguous() {
        when(invoiceRepo.findById(invoiceId)).thenReturn(Optional.of(mockInvoice));
        when(firmDetailsRepo.findById(sourceFirmId)).thenReturn(Optional.of(createFirmDetails(sourceFirmId, "Firm A")));
        when(firmDetailsRepo.findById(targetFirmId)).thenReturn(Optional.of(createFirmDetails(targetFirmId, "Firm B")));
        when(invoiceService.peekNextInvoiceNumber(targetFirmId)).thenReturn("INV-B-2001");
        when(invoicePaymentRepo.findByInvoiceIdOrderByPaymentDateAscIdAsc(invoiceId)).thenReturn(Collections.emptyList());
        when(salesReturnRepo.findByInvoiceIdOrderByCreatedAtDesc(invoiceId)).thenReturn(Collections.emptyList());

        Customer c1 = Customer.builder().id(21L).name("Acme Corp Branch 1").phone("9876543210").firmId(targetFirmId).build();
        Customer c2 = Customer.builder().id(22L).name("Acme Corp").phone("9111111111").firmId(targetFirmId).build();

        when(customerRepo.findByFirmIdAndGstinIgnoreCase(targetFirmId, "27AAACA1234A1Z5")).thenReturn(Collections.emptyList());
        when(customerRepo.findByFirmIdAndPhone(targetFirmId, "9876543210")).thenReturn(Collections.singletonList(c1));
        when(customerRepo.findByFirmIdAndNameIgnoreCase(targetFirmId, "Acme Corp")).thenReturn(Collections.singletonList(c2));

        InvoiceMigrationPreviewResponse preview = migrationService.previewMigration(invoiceId, targetFirmId);

        assertEquals("AMBIGUOUS", preview.getCustomerResolution().getMatchType());
        assertEquals(2, preview.getCustomerResolution().getCandidates().size());
    }

    @Test
    @DisplayName("Product matching: Multiple product matches return AMBIGUOUS")
    void testProductResolution_MultipleCandidatesAmbiguous() {
        when(invoiceRepo.findById(invoiceId)).thenReturn(Optional.of(mockInvoice));
        when(firmDetailsRepo.findById(sourceFirmId)).thenReturn(Optional.of(createFirmDetails(sourceFirmId, "Firm A")));
        when(firmDetailsRepo.findById(targetFirmId)).thenReturn(Optional.of(createFirmDetails(targetFirmId, "Firm B")));
        when(invoiceService.peekNextInvoiceNumber(targetFirmId)).thenReturn("INV-B-2001");
        when(invoicePaymentRepo.findByInvoiceIdOrderByPaymentDateAscIdAsc(invoiceId)).thenReturn(Collections.emptyList());
        when(salesReturnRepo.findByInvoiceIdOrderByCreatedAtDesc(invoiceId)).thenReturn(Collections.emptyList());

        Product p1 = Product.builder().id(81L).name("Industrial Widget (Old)").sku("IND-WID-01").firmId(targetFirmId).build();
        Product p2 = Product.builder().id(82L).name("Industrial Widget").sku("IND-WID-02").firmId(targetFirmId).build();

        when(productRepo.findByFirmIdAndSku(targetFirmId, "IND-WID-01")).thenReturn(Collections.singletonList(p1));
        when(productRepo.findByFirmIdAndNameIgnoreCase(targetFirmId, "Industrial Widget")).thenReturn(Collections.singletonList(p2));

        InvoiceMigrationPreviewResponse preview = migrationService.previewMigration(invoiceId, targetFirmId);

        assertEquals("AMBIGUOUS", preview.getProductResolutions().get(0).getResolutionType());
        assertEquals(2, preview.getProductResolutions().get(0).getCandidates().size());
    }

    @Test
    @DisplayName("Inventory: Source stock shortage/clamping (reverses only actual physical deduction)")
    void testInventoryRestoration_SourceShortageClamping() {
        when(invoiceRepo.findByIdForUpdate(invoiceId)).thenReturn(Optional.of(mockInvoice));
        when(firmDetailsRepo.findById(targetFirmId)).thenReturn(Optional.of(createFirmDetails(targetFirmId, "Firm B")));
        when(invoicePaymentRepo.findByInvoiceIdOrderByPaymentDateAscIdAsc(invoiceId)).thenReturn(Collections.emptyList());
        when(salesReturnRepo.findByInvoiceIdOrderByCreatedAtDesc(invoiceId)).thenReturn(Collections.emptyList());

        // Even though invoice qty is 5, historical stock movement was clamped to 2 units due to shortage at time of creation!
        List<Object[]> movList = new ArrayList<>();
        movList.add(new Object[]{50L, new BigDecimal("-2.000")});
        when(stockMovementRepo.sumQuantityChangeByProductIdForInvoice(eq(sourceFirmId), anyString(), anyString())).thenReturn(movList);

        Customer customerB = Customer.builder().id(30L).name("Acme Corp").firmId(targetFirmId).build();
        when(customerRepo.save(any(Customer.class))).thenReturn(customerB);
        when(productRepo.findByFirmIdAndSku(targetFirmId, "IND-WID-01")).thenReturn(Collections.emptyList());
        when(invoiceService.generateInvoiceNumber(targetFirmId)).thenReturn("INV-B-5004");
        when(invoiceRepo.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));
        when(migrationRecordRepo.save(any(InvoiceMigrationRecord.class))).thenAnswer(i -> i.getArgument(0));

        InvoiceMigrationRequest request = InvoiceMigrationRequest.builder().targetFirmId(targetFirmId).createCustomerIfMissing(true).build();
        migrationService.migrateInvoice(invoiceId, request);

        // Verify ONLY the 2.000 units actually deducted are reversed in Firm A!
        verify(productService).recordStockMovement(
                eq(50L), eq(sourceFirmId), eq("INVOICE_MIGRATION_REVERSAL"), eq(new BigDecimal("2.000")), eq("INVOICE"), anyString(), anyString()
        );
    }

    @Test
    @DisplayName("Revert: Blocked when payment recorded in target firm")
    void testRevert_BlockedByPaymentInTarget() {
        mockInvoice.setFirmId(targetFirmId);
        when(invoiceRepo.findByIdForUpdate(invoiceId)).thenReturn(Optional.of(mockInvoice));

        InvoiceMigrationRecord activeRecord = InvoiceMigrationRecord.builder()
                .id(101L)
                .invoiceId(invoiceId)
                .sourceFirmId(sourceFirmId)
                .targetFirmId(targetFirmId)
                .status("ACTIVE")
                .preMigrationSnapshot("{}")
                .build();
        when(migrationRecordRepo.findFirstByInvoiceIdAndStatusOrderByCreatedAtDesc(invoiceId, "ACTIVE"))
                .thenReturn(Optional.of(activeRecord));

        when(invoicePaymentRepo.findByInvoiceIdOrderByPaymentDateAscIdAsc(invoiceId))
                .thenReturn(Collections.singletonList(InvoicePayment.builder().id(10L).amount(new BigDecimal("100.00")).build()));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> migrationService.revertMigration(invoiceId));
        assertTrue(ex.getMessage().toLowerCase().contains("payment"));
    }

    @Test
    @DisplayName("Revert: Blocked when sales return recorded in target firm")
    void testRevert_BlockedByReturnInTarget() {
        mockInvoice.setFirmId(targetFirmId);
        when(invoiceRepo.findByIdForUpdate(invoiceId)).thenReturn(Optional.of(mockInvoice));

        InvoiceMigrationRecord activeRecord = InvoiceMigrationRecord.builder()
                .id(101L)
                .invoiceId(invoiceId)
                .sourceFirmId(sourceFirmId)
                .targetFirmId(targetFirmId)
                .status("ACTIVE")
                .preMigrationSnapshot("{}")
                .build();
        when(migrationRecordRepo.findFirstByInvoiceIdAndStatusOrderByCreatedAtDesc(invoiceId, "ACTIVE"))
                .thenReturn(Optional.of(activeRecord));

        when(invoicePaymentRepo.findByInvoiceIdOrderByPaymentDateAscIdAsc(invoiceId)).thenReturn(Collections.emptyList());
        when(salesReturnRepo.findByInvoiceIdOrderByCreatedAtDesc(invoiceId))
                .thenReturn(Collections.singletonList(SalesReturn.builder().id(10L).returnNumber("RET-01").build()));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> migrationService.revertMigration(invoiceId));
        assertTrue(ex.getMessage().toLowerCase().contains("sales return"));
    }

    @Test
    @DisplayName("Migration History: Retrieval returns ordered DTO list")
    void testMigrationHistory_Retrieval() {
        InvoiceMigrationRecord r1 = InvoiceMigrationRecord.builder()
                .id(1L)
                .invoiceId(invoiceId)
                .sourceFirmId(sourceFirmId)
                .targetFirmId(targetFirmId)
                .sourceInvoiceNumber("INV-A-01")
                .targetInvoiceNumber("INV-B-01")
                .status("ACTIVE")
                .isRevert(false)
                .createdAt(LocalDateTime.now())
                .build();

        when(invoiceRepo.findById(invoiceId)).thenReturn(Optional.of(mockInvoice));
        when(migrationRecordRepo.findByInvoiceIdOrderByCreatedAtDesc(invoiceId)).thenReturn(Collections.singletonList(r1));
        when(firmDetailsRepo.findById(sourceFirmId)).thenReturn(Optional.of(createFirmDetails(sourceFirmId, "Firm A")));
        when(firmDetailsRepo.findById(targetFirmId)).thenReturn(Optional.of(createFirmDetails(targetFirmId, "Firm B")));

        List<InvoiceMigrationRecordDTO> history = migrationService.getMigrationHistory(invoiceId);

        assertNotNull(history);
        assertEquals(1, history.size());
        assertEquals("INV-A-01", history.get(0).getSourceInvoiceNumber());
        assertEquals("INV-B-01", history.get(0).getTargetInvoiceNumber());
    }

    @Test
    @DisplayName("MIG-AUTH-001: Preview migration with foreign firm context throws EntityNotFoundException (404)")
    void testPreviewMigration_ForeignFirm_ThrowsEntityNotFoundException() {
        Long foreignFirmId = 99L;
        TenantContext.setCurrentFirmId(foreignFirmId);

        when(invoiceRepo.findByIdAndFirmId(invoiceId, foreignFirmId)).thenReturn(Optional.empty());
        when(invoiceRepo.findById(invoiceId)).thenReturn(Optional.of(mockInvoice)); // mockInvoice belongs to firm 1

        assertThrows(jakarta.persistence.EntityNotFoundException.class, () ->
                migrationService.previewMigration(invoiceId, targetFirmId));
    }

    @Test
    @DisplayName("MIG-AUTH-002: Migrate invoice without TenantContext throws TenantSecurityException")
    void testMigrateInvoice_MissingTenantContext_ThrowsTenantSecurityException() {
        TenantContext.clear();

        InvoiceMigrationRequest request = InvoiceMigrationRequest.builder()
                .targetFirmId(targetFirmId)
                .build();

        assertThrows(TenantSecurityException.class, () ->
                migrationService.migrateInvoice(invoiceId, request));
    }

    @Test
    @DisplayName("MIG-AUTH-002: Migrate invoice with cross-firm context throws TenantSecurityException")
    void testMigrateInvoice_CrossFirmAttempt_ThrowsTenantSecurityException() {
        Long foreignFirmId = 3L;
        TenantContext.setCurrentFirmId(foreignFirmId);

        when(invoiceRepo.findByIdAndFirmIdForUpdate(invoiceId, foreignFirmId)).thenReturn(Optional.empty());
        when(invoiceRepo.findByIdForUpdate(invoiceId)).thenReturn(Optional.of(mockInvoice)); // mockInvoice belongs to firm 1

        InvoiceMigrationRequest request = InvoiceMigrationRequest.builder()
                .targetFirmId(targetFirmId)
                .build();

        TenantSecurityException ex = assertThrows(TenantSecurityException.class, () ->
                migrationService.migrateInvoice(invoiceId, request));
        assertTrue(ex.getMessage().contains("Cross-firm migration blocked"));
    }

    @Test
    @DisplayName("MIG-AUTH-003: Revert migration without TenantContext throws TenantSecurityException")
    void testRevertMigration_MissingTenantContext_ThrowsTenantSecurityException() {
        TenantContext.clear();

        assertThrows(TenantSecurityException.class, () ->
                migrationService.revertMigration(invoiceId));
    }

    @Test
    @DisplayName("MIG-AUTH-003: Revert migration by unauthorized third-party firm throws TenantSecurityException")
    void testRevertMigration_UnauthorizedThirdParty_ThrowsTenantSecurityException() {
        Long thirdPartyFirmId = 3L;
        TenantContext.setCurrentFirmId(thirdPartyFirmId);

        mockInvoice.setFirmId(targetFirmId); // currently in Firm 2
        when(invoiceRepo.findByIdForUpdate(invoiceId)).thenReturn(Optional.of(mockInvoice));

        InvoiceMigrationRecord activeMigration = InvoiceMigrationRecord.builder()
                .id(50L)
                .invoiceId(invoiceId)
                .sourceFirmId(sourceFirmId) // firm 1
                .targetFirmId(targetFirmId) // firm 2
                .status("ACTIVE")
                .build();

        when(migrationRecordRepo.findFirstByInvoiceIdAndStatusOrderByCreatedAtDesc(invoiceId, "ACTIVE"))
                .thenReturn(Optional.of(activeMigration));

        TenantSecurityException ex = assertThrows(TenantSecurityException.class, () ->
                migrationService.revertMigration(invoiceId));
        assertTrue(ex.getMessage().contains("Cross-firm revert blocked"));
    }

    @Test
    @DisplayName("MIG-AUTH-004: Migration history lookup by foreign firm throws EntityNotFoundException (404)")
    void testMigrationHistory_ForeignFirm_ThrowsEntityNotFoundException() {
        Long foreignFirmId = 99L;
        TenantContext.setCurrentFirmId(foreignFirmId);

        when(invoiceRepo.findById(invoiceId)).thenReturn(Optional.of(mockInvoice)); // firm 1

        InvoiceMigrationRecord r1 = InvoiceMigrationRecord.builder()
                .id(1L)
                .invoiceId(invoiceId)
                .sourceFirmId(sourceFirmId) // firm 1
                .targetFirmId(targetFirmId) // firm 2
                .status("ACTIVE")
                .build();

        when(migrationRecordRepo.findByInvoiceIdOrderByCreatedAtDesc(invoiceId)).thenReturn(Collections.singletonList(r1));

        assertThrows(jakarta.persistence.EntityNotFoundException.class, () ->
                migrationService.getMigrationHistory(invoiceId));
    }
}
