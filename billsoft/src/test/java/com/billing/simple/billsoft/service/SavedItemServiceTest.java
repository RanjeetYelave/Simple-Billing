package com.billing.simple.billsoft.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.billing.simple.billsoft.dtos.PromoteSavedItemRequestDto;
import com.billing.simple.billsoft.dtos.UnifiedItemSuggestionDto;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.InvoiceItem;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.entities.SavedItem;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.repo.SavedItemRepository;
import com.billing.simple.billsoft.security.TenantContext;

public class SavedItemServiceTest {

    @Mock
    private SavedItemRepository savedItemRepo;

    @Mock
    private ProductRepository productRepo;

    @Mock
    private ProductService productService;

    @InjectMocks
    private SavedItemService savedItemService;

    private Long firmA = 1001L;
    private Long firmB = 1002L;

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
    public void testRecordOrUpdateManualItemCreatesNewSavedItem() {
        InvoiceItem item = InvoiceItem.builder()
                .product(null) // Uncatalogued item
                .productName("Custom Gasket 50mm")
                .unit("nos")
                .pricePerUnit(new BigDecimal("150.00"))
                .gstPercent(new BigDecimal("18.00"))
                .hsnCode("8484")
                .build();

        Invoice invoice = Invoice.builder()
                .firmId(firmA)
                .items(Collections.singletonList(item))
                .build();

        when(savedItemRepo.findByFirmIdAndNormalizedNameAndIsArchivedFalse(firmA, "custom gasket 50mm")).thenReturn(Optional.empty());
        when(savedItemRepo.save(any(SavedItem.class))).thenAnswer(inv -> {
            SavedItem si = inv.getArgument(0);
            si.setId(10L);
            return si;
        });

        savedItemService.recordOrUpdateFromInvoice(invoice);

        verify(savedItemRepo, times(1)).save(any(SavedItem.class));
    }

    @Test
    public void testRecordOrUpdateIncrementsUsageCountAndUpdatesLastUsed() {
        InvoiceItem item = InvoiceItem.builder()
                .product(null)
                .productName("Custom Gasket 50mm")
                .unit("nos")
                .pricePerUnit(new BigDecimal("160.00"))
                .gstPercent(new BigDecimal("18.00"))
                .hsnCode("8484")
                .build();

        Invoice invoice = Invoice.builder()
                .firmId(firmA)
                .items(Collections.singletonList(item))
                .build();

        SavedItem existing = SavedItem.builder()
                .id(20L)
                .firmId(firmA)
                .name("Custom Gasket 50mm")
                .normalizedName("custom gasket 50mm")
                .unit("nos")
                .sellingPrice(new BigDecimal("140.00"))
                .usageCount(3)
                .build();

        when(savedItemRepo.findByFirmIdAndNormalizedNameAndIsArchivedFalse(firmA, "custom gasket 50mm")).thenReturn(Optional.of(existing));
        when(savedItemRepo.save(any(SavedItem.class))).thenAnswer(inv -> inv.getArgument(0));

        savedItemService.recordOrUpdateFromInvoice(invoice);

        assertEquals(4, existing.getUsageCount());
        assertEquals(new BigDecimal("160.00"), existing.getSellingPrice());
        verify(savedItemRepo, times(1)).save(existing);
    }

    @Test
    public void testUnifiedSearchReturnsBothInventoryAndSavedItems() {
        Product prod = Product.builder()
                .id(1L)
                .firmId(firmA)
                .name("Copper Wire 1mm")
                .price(new BigDecimal("500.00"))
                .stockQuantity(new BigDecimal("25"))
                .unit("meter")
                .isArchived(false)
                .build();

        SavedItem savedItem = SavedItem.builder()
                .id(2L)
                .firmId(firmA)
                .name("Copper Pipe 15mm")
                .sellingPrice(new BigDecimal("350.00"))
                .unit("meter")
                .usageCount(5)
                .isArchived(false)
                .build();

        when(productRepo.findByFirmId(firmA)).thenReturn(Collections.singletonList(prod));
        when(savedItemRepo.findActiveUnpromotedByFirmId(firmA))
                .thenReturn(Collections.singletonList(savedItem));

        List<UnifiedItemSuggestionDto> results = savedItemService.searchUnifiedItems(firmA, "copper");

        assertEquals(2, results.size());
        UnifiedItemSuggestionDto first = results.get(0);
        assertEquals("INVENTORY", first.getSource());
        assertEquals("Copper Wire 1mm", first.getName());
        assertEquals(new BigDecimal("25"), first.getStockQuantity());

        UnifiedItemSuggestionDto second = results.get(1);
        assertEquals("SAVED_ITEM", second.getSource());
        assertEquals("Copper Pipe 15mm", second.getName());
        assertEquals("Saved Item · Not in Inventory", second.getBadge());
    }

    @Test
    public void testPromoteSavedItemCreatesProductAndLinksSavedItem() {
        SavedItem saved = SavedItem.builder()
                .id(30L)
                .firmId(firmA)
                .name("Special Brass Valve")
                .unit("pcs")
                .sellingPrice(new BigDecimal("800.00"))
                .hsnCode("8481")
                .isArchived(false)
                .build();

        when(savedItemRepo.findByIdAndFirmId(30L, firmA)).thenReturn(Optional.of(saved));
        when(productService.create(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(101L);
            return p;
        });

        PromoteSavedItemRequestDto req = PromoteSavedItemRequestDto.builder()
                .savedItemId(30L)
                .sku("VALVE-BRASS-01")
                .category("Plumbing")
                .stockQuantity(new BigDecimal("15"))
                .costPrice(new BigDecimal("600.00"))
                .price(new BigDecimal("850.00"))
                .gstPercentage(new BigDecimal("18.00"))
                .build();

        Product created = savedItemService.promoteToProduct(firmA, req);

        assertNotNull(created);
        assertEquals(101L, created.getId());
        assertEquals("VALVE-BRASS-01", created.getSku());
        assertEquals(new BigDecimal("15"), created.getStockQuantity());
        assertEquals(101L, saved.getCanonicalProductId());

        verify(productService, times(1)).create(any(Product.class));
        verify(savedItemRepo, times(1)).save(saved);
    }
}
