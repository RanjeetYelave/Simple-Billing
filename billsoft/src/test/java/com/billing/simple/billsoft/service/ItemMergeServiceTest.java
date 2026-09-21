package com.billing.simple.billsoft.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.billing.simple.billsoft.dtos.BatchMergeRequestDto;
import com.billing.simple.billsoft.dtos.MergeRequestDto;
import com.billing.simple.billsoft.entities.ItemMergeAudit;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.entities.SavedItem;
import com.billing.simple.billsoft.entities.StockMovement;
import com.billing.simple.billsoft.repo.ItemMergeAuditRepository;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.repo.SavedItemRepository;
import com.billing.simple.billsoft.repo.StockMovementRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ItemMergeServiceTest {

    @Mock
    private ProductRepository productRepo;

    @Mock
    private SavedItemRepository savedItemRepo;

    @Mock
    private StockMovementRepository stockMovementRepo;

    @Mock
    private ItemMergeAuditRepository mergeAuditRepo;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ItemMergeService mergeService;

    private Long firmA = 3001L;
    private Long firmB = 3002L;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        TenantContext.setCurrentFirmId(firmA);
    }

    @AfterEach
    public void tearDown() {
        TenantContext.clear();
    }

    @Test
    public void testMergeProductIntoProductCombinesStockAndLedger() {
        Product target = Product.builder()
                .id(10L)
                .firmId(firmA)
                .name("Standard Cement 50kg")
                .unit("bag")
                .stockQuantity(new BigDecimal("100"))
                .price(new BigDecimal("350.00"))
                .isArchived(false)
                .build();

        Product source = Product.builder()
                .id(11L)
                .firmId(firmA)
                .name("Std Cement 50kg (Dup)")
                .unit("bag")
                .stockQuantity(new BigDecimal("40"))
                .price(new BigDecimal("350.00"))
                .isArchived(false)
                .build();

        when(productRepo.findByIdAndFirmId(10L, firmA)).thenReturn(Optional.of(target));
        when(productRepo.findByIdAndFirmId(11L, firmA)).thenReturn(Optional.of(source));
        when(productRepo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        MergeRequestDto req = MergeRequestDto.builder()
                .primaryType("PRODUCT")
                .primaryId(10L)
                .secondaryType("PRODUCT")
                .secondaryId(11L)
                .nameResolution("KEEP_PRIMARY")
                .sellingPriceResolution("KEEP_PRIMARY")
                .stockStrategy("ADD")
                .build();

        Map<String, Object> result = mergeService.mergeItems(firmA, req);

        assertEquals("SUCCESS", result.get("status"));
        assertEquals(new BigDecimal("140"), target.getStockQuantity());
        assertEquals(BigDecimal.ZERO, source.getStockQuantity());
        assertTrue(source.getIsArchived());
        assertEquals(10L, source.getCanonicalProductId());

        // Verify stock movements recorded: 1 OUT from source, 1 IN to target
        verify(stockMovementRepo, times(2)).save(any(StockMovement.class));
        verify(mergeAuditRepo, times(1)).save(any(ItemMergeAudit.class));
    }

    @Test
    public void testMergeSavedItemIntoProduct() {
        Product target = Product.builder()
                .id(20L)
                .firmId(firmA)
                .name("Hex Nut M8")
                .unit("pcs")
                .stockQuantity(new BigDecimal("500"))
                .price(new BigDecimal("2.00"))
                .isArchived(false)
                .build();

        SavedItem source = SavedItem.builder()
                .id(21L)
                .firmId(firmA)
                .name("Hex Nut M8 (Uncatalogued)")
                .unit("pcs")
                .sellingPrice(new BigDecimal("2.00"))
                .usageCount(8)
                .isArchived(false)
                .build();

        when(productRepo.findByIdAndFirmId(20L, firmA)).thenReturn(Optional.of(target));
        when(savedItemRepo.findByIdAndFirmId(21L, firmA)).thenReturn(Optional.of(source));

        MergeRequestDto req = MergeRequestDto.builder()
                .primaryType("PRODUCT")
                .primaryId(20L)
                .secondaryType("SAVED_ITEM")
                .secondaryId(21L)
                .nameResolution("KEEP_PRIMARY")
                .sellingPriceResolution("KEEP_PRIMARY")
                .stockStrategy("KEEP_PRIMARY")
                .build();

        Map<String, Object> result = mergeService.mergeItems(firmA, req);

        assertEquals("SUCCESS", result.get("status"));
        assertEquals(20L, source.getCanonicalProductId());
        verify(savedItemRepo, times(1)).save(source);
        verify(mergeAuditRepo, times(1)).save(any(ItemMergeAudit.class));
    }

    @Test
    public void testMergeSavedItemIntoSavedItem() {
        SavedItem target = SavedItem.builder()
                .id(30L)
                .firmId(firmA)
                .name("Wire Clamp 10mm")
                .unit("pcs")
                .sellingPrice(new BigDecimal("15.00"))
                .usageCount(3)
                .isArchived(false)
                .build();

        SavedItem source = SavedItem.builder()
                .id(31L)
                .firmId(firmA)
                .name("Wire Clamp 10mm Steel")
                .unit("pcs")
                .sellingPrice(new BigDecimal("15.00"))
                .usageCount(2)
                .isArchived(false)
                .build();

        when(savedItemRepo.findByIdAndFirmId(30L, firmA)).thenReturn(Optional.of(target));
        when(savedItemRepo.findByIdAndFirmId(31L, firmA)).thenReturn(Optional.of(source));

        MergeRequestDto req = MergeRequestDto.builder()
                .primaryType("SAVED_ITEM")
                .primaryId(30L)
                .secondaryType("SAVED_ITEM")
                .secondaryId(31L)
                .nameResolution("KEEP_PRIMARY")
                .sellingPriceResolution("KEEP_PRIMARY")
                .stockStrategy("KEEP_PRIMARY")
                .build();

        Map<String, Object> result = mergeService.mergeItems(firmA, req);

        assertEquals("SUCCESS", result.get("status"));
        assertEquals(5, target.getUsageCount());
        assertTrue(source.getIsArchived());
        verify(savedItemRepo, times(1)).save(target);
        verify(savedItemRepo, times(1)).save(source);
    }

    @Test
    public void testBatchMergeExecutesMultiplePairs() {
        Product p1 = Product.builder().id(40L).firmId(firmA).name("P1").stockQuantity(BigDecimal.TEN).build();
        Product p2 = Product.builder().id(41L).firmId(firmA).name("P2").stockQuantity(BigDecimal.ONE).build();
        Product p3 = Product.builder().id(42L).firmId(firmA).name("P3").stockQuantity(BigDecimal.TEN).build();
        Product p4 = Product.builder().id(43L).firmId(firmA).name("P4").stockQuantity(BigDecimal.ONE).build();

        when(productRepo.findByIdAndFirmId(40L, firmA)).thenReturn(Optional.of(p1));
        when(productRepo.findByIdAndFirmId(41L, firmA)).thenReturn(Optional.of(p2));
        when(productRepo.findByIdAndFirmId(42L, firmA)).thenReturn(Optional.of(p3));
        when(productRepo.findByIdAndFirmId(43L, firmA)).thenReturn(Optional.of(p4));

        MergeRequestDto m1 = MergeRequestDto.builder()
                .primaryType("PRODUCT").primaryId(40L).secondaryType("PRODUCT").secondaryId(41L).stockStrategy("ADD").build();
        MergeRequestDto m2 = MergeRequestDto.builder()
                .primaryType("PRODUCT").primaryId(42L).secondaryType("PRODUCT").secondaryId(43L).stockStrategy("ADD").build();

        BatchMergeRequestDto batchReq = BatchMergeRequestDto.builder()
                .merges(Arrays.asList(m1, m2))
                .build();

        Map<String, Object> batchResult = mergeService.batchMerge(firmA, batchReq);

        assertEquals(2, batchResult.get("successfulMerges"));
    }

    @Test
    public void testCrossFirmMergeThrowsSecurityException() {
        Product targetA = Product.builder().id(50L).firmId(firmA).name("Target A").build();
        Product secondaryB = Product.builder().id(51L).firmId(firmB).name("Secondary B").build();

        when(productRepo.findByIdAndFirmId(50L, firmA)).thenReturn(Optional.of(targetA));
        when(productRepo.findByIdAndFirmId(51L, firmA)).thenReturn(Optional.of(secondaryB));

        MergeRequestDto req = MergeRequestDto.builder()
                .primaryType("PRODUCT")
                .primaryId(50L)
                .secondaryType("PRODUCT")
                .secondaryId(51L)
                .stockStrategy("ADD")
                .build();

        assertThrows(TenantSecurityException.class, () -> {
            mergeService.mergeItems(firmA, req);
        });
    }
}
