package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dtos.ProductBulkUpdateItemDto;
import com.billing.simple.billsoft.dtos.ProductBulkUpdateResponseDto;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.entities.StockMovement;
import com.billing.simple.billsoft.repo.InvoiceItemRepository;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.repo.SalesReturnItemRepository;
import com.billing.simple.billsoft.repo.StockMovementRepository;
import com.billing.simple.billsoft.repositories.PartyRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductMasterEditRegressionTest {

    @Mock
    private ProductRepository repo;

    @Mock
    private StockMovementRepository stockMovementRepo;

    @Mock
    private InvoiceItemRepository invoiceItemRepo;

    @Mock
    private SalesReturnItemRepository salesReturnItemRepo;

    @Mock
    private PartyRepository partyRepo;

    @InjectMocks
    private ProductService service;

    private AutoCloseable closeable;
    private final Long FIRM_ID = 100L;
    private final Long OTHER_FIRM_ID = 200L;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        TenantContext.setCurrentFirmId(FIRM_ID);
    }

    @AfterEach
    void tearDown() throws Exception {
        TenantContext.clear();
        if (closeable != null) {
            closeable.close();
        }
    }

    @Test
    void testSingleProductCategoryAndPriceUpdate() {
        Long prodId = 1L;
        Product existing = Product.builder()
                .id(prodId)
                .firmId(FIRM_ID)
                .name("PVC Pipe")
                .category("UNCATEGORIZED")
                .price(new BigDecimal("100.00"))
                .costPrice(new BigDecimal("70.00"))
                .minStockLevel(new BigDecimal("5.000"))
                .inInventory(true)
                .stockQuantity(new BigDecimal("50.000"))
                .build();

        when(repo.findByIdAndFirmId(prodId, FIRM_ID)).thenReturn(Optional.of(existing));
        when(repo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product updateRequest = new Product();
        updateRequest.setCategory("Plumbing");
        updateRequest.setPrice(new BigDecimal("120.00"));
        updateRequest.setCostPrice(new BigDecimal("85.00"));
        updateRequest.setMinStockLevel(new BigDecimal("15.000"));

        Product result = service.update(prodId, updateRequest);

        assertNotNull(result);
        assertEquals("Plumbing", result.getCategory());
        assertEquals(new BigDecimal("120.00"), result.getPrice());
        assertEquals(new BigDecimal("85.00"), result.getCostPrice());
        assertEquals(new BigDecimal("15.000"), result.getMinStockLevel());
        assertEquals(new BigDecimal("50.000"), result.getStockQuantity(), "Stock quantity must remain unchanged");
        verify(repo, times(1)).save(existing);
    }

    @Test
    void testSingleProductSkuUpdateAllowedAndSelfConflictIgnored() {
        Long prodId = 1L;
        Product existing = Product.builder()
                .id(prodId)
                .firmId(FIRM_ID)
                .name("PVC Pipe")
                .sku("OLD-SKU")
                .build();

        when(repo.findByIdAndFirmId(prodId, FIRM_ID)).thenReturn(Optional.of(existing));
        when(repo.findFirstByFirmIdAndSku(FIRM_ID, "NEW-SKU")).thenReturn(Optional.empty());
        when(repo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product updateRequest = new Product();
        updateRequest.setSku("NEW-SKU");

        Product result = service.update(prodId, updateRequest);

        assertNotNull(result);
        assertEquals("NEW-SKU", result.getSku());

        // Self SKU update
        when(repo.findFirstByFirmIdAndSku(FIRM_ID, "NEW-SKU")).thenReturn(Optional.of(existing));
        Product selfResult = service.update(prodId, updateRequest);
        assertEquals("NEW-SKU", selfResult.getSku());
    }

    @Test
    void testSingleProductDuplicateSkuRejection() {
        Long prodId = 1L;
        Product existing = Product.builder().id(prodId).firmId(FIRM_ID).name("PVC Pipe").build();
        Product otherProduct = Product.builder().id(2L).firmId(FIRM_ID).name("CPVC Pipe").sku("COLLIDE-SKU").build();

        when(repo.findByIdAndFirmId(prodId, FIRM_ID)).thenReturn(Optional.of(existing));
        when(repo.findFirstByFirmIdAndSku(FIRM_ID, "COLLIDE-SKU")).thenReturn(Optional.of(otherProduct));

        Product updateRequest = new Product();
        updateRequest.setSku("COLLIDE-SKU");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            service.update(prodId, updateRequest);
        });
        assertTrue(ex.getMessage().contains("already exists for this firm"));
        verify(repo, never()).save(any(Product.class));
    }

    @Test
    void testSingleProductDuplicateBarcodeRejection() {
        Long prodId = 1L;
        Product existing = Product.builder().id(prodId).firmId(FIRM_ID).name("PVC Pipe").build();
        Product otherProduct = Product.builder().id(2L).firmId(FIRM_ID).name("CPVC Pipe").barcode("8901234567890").build();

        when(repo.findByIdAndFirmId(prodId, FIRM_ID)).thenReturn(Optional.of(existing));
        when(repo.findFirstByFirmIdAndBarcode(FIRM_ID, "8901234567890")).thenReturn(Optional.of(otherProduct));

        Product updateRequest = new Product();
        updateRequest.setBarcode("8901234567890");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            service.update(prodId, updateRequest);
        });
        assertTrue(ex.getMessage().contains("already exists for this firm"));
        verify(repo, never()).save(any(Product.class));
    }

    @Test
    void testSingleProductPreferredVendorValidation() {
        Long prodId = 1L;
        Product existing = Product.builder().id(prodId).firmId(FIRM_ID).name("PVC Pipe").build();

        when(repo.findByIdAndFirmId(prodId, FIRM_ID)).thenReturn(Optional.of(existing));
        when(partyRepo.existsByIdAndFirmId(50L, FIRM_ID)).thenReturn(true);
        when(partyRepo.existsByIdAndFirmId(999L, FIRM_ID)).thenReturn(false);
        when(repo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        // Valid vendor
        Product updateRequest = new Product();
        updateRequest.setPreferredPartyId(50L);
        Product result = service.update(prodId, updateRequest);
        assertEquals(50L, result.getPreferredPartyId());

        // Invalid vendor
        updateRequest.setPreferredPartyId(999L);
        assertThrows(IllegalArgumentException.class, () -> {
            service.update(prodId, updateRequest);
        });
    }

    @Test
    void testSingleProductInventoryPromotionWithOpeningStock() {
        Long prodId = 1L;
        Product untracked = Product.builder()
                .id(prodId)
                .firmId(FIRM_ID)
                .name("Imported Widget")
                .category("UNCATEGORIZED")
                .inInventory(false)
                .stockQuantity(BigDecimal.ZERO)
                .itemType("GOODS")
                .build();

        when(repo.findByIdAndFirmId(prodId, FIRM_ID)).thenReturn(Optional.of(untracked));
        when(repo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product updateRequest = new Product();
        updateRequest.setCategory("Hardware");
        updateRequest.setInInventory(true);
        updateRequest.setStockQuantity(new BigDecimal("40.000"));

        Product result = service.update(prodId, updateRequest);

        assertTrue(result.getInInventory());
        assertEquals(new BigDecimal("40.000"), result.getStockQuantity());

        ArgumentCaptor<StockMovement> movCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepo, times(1)).save(movCaptor.capture());
        StockMovement movement = movCaptor.getValue();
        assertEquals("INITIAL_STOCK", movement.getMovementType());
        assertEquals(new BigDecimal("40.000"), movement.getQuantityChange());
        assertEquals(new BigDecimal("40.000"), movement.getNewStock());
        assertEquals(FIRM_ID, movement.getFirmId());
    }

    @Test
    void testSingleProductInventoryPromotionWithZeroStockCreatesNoMovement() {
        Long prodId = 1L;
        Product untracked = Product.builder()
                .id(prodId)
                .firmId(FIRM_ID)
                .name("Imported Widget")
                .inInventory(false)
                .stockQuantity(BigDecimal.ZERO)
                .build();

        when(repo.findByIdAndFirmId(prodId, FIRM_ID)).thenReturn(Optional.of(untracked));
        when(repo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product updateRequest = new Product();
        updateRequest.setInInventory(true);
        updateRequest.setStockQuantity(BigDecimal.ZERO);

        Product result = service.update(prodId, updateRequest);

        assertTrue(result.getInInventory());
        assertEquals(BigDecimal.ZERO, result.getStockQuantity());
        verify(stockMovementRepo, never()).save(any(StockMovement.class));
    }

    @Test
    void testExistingTrackedProductDoesNotOverwriteStockOnEdit() {
        Long prodId = 1L;
        Product tracked = Product.builder()
                .id(prodId)
                .firmId(FIRM_ID)
                .name("Tracked Item")
                .inInventory(true)
                .stockQuantity(new BigDecimal("75.000"))
                .build();

        when(repo.findByIdAndFirmId(prodId, FIRM_ID)).thenReturn(Optional.of(tracked));
        when(repo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product updateRequest = new Product();
        updateRequest.setName("Renamed Item");
        updateRequest.setStockQuantity(new BigDecimal("0.000")); // Attempted accidental zero stock
        updateRequest.setInInventory(true);

        Product result = service.update(prodId, updateRequest);

        assertEquals("Renamed Item", result.getName());
        assertEquals(new BigDecimal("75.000"), result.getStockQuantity(), "Stock must not be overwritten for already tracked product");
        verify(stockMovementRepo, never()).save(any(StockMovement.class));
    }

    @Test
    void testBulkUpdateSuccess() {
        Product p1 = Product.builder().id(101L).firmId(FIRM_ID).name("Item 1").category("UNCATEGORIZED").price(new BigDecimal("100")).inInventory(true).build();
        Product p2 = Product.builder().id(102L).firmId(FIRM_ID).name("Item 2").category("UNCATEGORIZED").costPrice(new BigDecimal("50")).inInventory(false).build();

        when(repo.findByIdInAndFirmId(anyCollection(), eq(FIRM_ID))).thenReturn(Arrays.asList(p1, p2));
        when(repo.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<ProductBulkUpdateItemDto> dtos = Arrays.asList(
                ProductBulkUpdateItemDto.builder()
                        .productId(101L)
                        .category("Plumbing")
                        .price(new BigDecimal("120.00"))
                        .sku("PL-101")
                        .build(),
                ProductBulkUpdateItemDto.builder()
                        .productId(102L)
                        .category("Electrical")
                        .costPrice(new BigDecimal("60.00"))
                        .trackInInventory(true)
                        .openingStock(new BigDecimal("30.000"))
                        .build()
        );

        ProductBulkUpdateResponseDto response = service.updateBulk(dtos, FIRM_ID);

        assertNotNull(response);
        assertEquals(2, response.getUpdatedCount());
        assertEquals("Plumbing", p1.getCategory());
        assertEquals(new BigDecimal("120.00"), p1.getPrice());
        assertEquals("PL-101", p1.getSku());

        assertEquals("Electrical", p2.getCategory());
        assertEquals(new BigDecimal("60.00"), p2.getCostPrice());
        assertTrue(p2.getInInventory());
        assertEquals(new BigDecimal("30.000"), p2.getStockQuantity());

        verify(stockMovementRepo, times(1)).save(any(StockMovement.class));
    }

    @Test
    void testBulkUpdateIntraBatchSkuConflictRejection() {
        List<ProductBulkUpdateItemDto> dtos = Arrays.asList(
                ProductBulkUpdateItemDto.builder().productId(101L).sku("SAME-SKU").build(),
                ProductBulkUpdateItemDto.builder().productId(102L).sku("same-sku").build()
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            service.updateBulk(dtos, FIRM_ID);
        });
        assertTrue(ex.getMessage().contains("Duplicate SKU in batch"));
        verify(repo, never()).saveAll(anyList());
    }

    @Test
    void testBulkUpdateIntraBatchBarcodeConflictRejection() {
        List<ProductBulkUpdateItemDto> dtos = Arrays.asList(
                ProductBulkUpdateItemDto.builder().productId(101L).barcode("8901111111111").build(),
                ProductBulkUpdateItemDto.builder().productId(102L).barcode("8901111111111").build()
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            service.updateBulk(dtos, FIRM_ID);
        });
        assertTrue(ex.getMessage().contains("Duplicate Barcode in batch"));
        verify(repo, never()).saveAll(anyList());
    }

    @Test
    void testBulkUpdateCrossFirmSecurityRejection() {
        List<ProductBulkUpdateItemDto> dtos = Collections.singletonList(
                ProductBulkUpdateItemDto.builder().productId(101L).name("Tampered").build()
        );

        // Client specifies a different firmId than authenticated TenantContext
        assertThrows(TenantSecurityException.class, () -> {
            service.updateBulk(dtos, OTHER_FIRM_ID);
        });
    }

    @Test
    void testBulkUpdateNegativeValuesRejection() {
        List<ProductBulkUpdateItemDto> dtos = Collections.singletonList(
                ProductBulkUpdateItemDto.builder().productId(101L).price(new BigDecimal("-10.00")).build()
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            service.updateBulk(dtos, FIRM_ID);
        });
        assertTrue(ex.getMessage().contains("Selling price cannot be negative"));
    }
}
