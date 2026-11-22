package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dtos.SmartDiscoveryResponse;
import com.billing.simple.billsoft.entities.AppConfig;
import com.billing.simple.billsoft.entities.InvoiceStatus;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import com.billing.simple.billsoft.repo.InvoiceItemRepository;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ProductSmartDiscoveryServiceTest {

    @Mock
    private ProductRepository repo;

    @Mock
    private InvoiceItemRepository invoiceItemRepo;

    @Mock
    private AppConfigRepository appConfigRepo;

    @InjectMocks
    private ProductService service;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        TenantContext.setCurrentFirmId(1L);
    }

    @AfterEach
    void tearDown() throws Exception {
        TenantContext.clear();
        if (closeable != null) {
            closeable.close();
        }
    }

    @Test
    @DisplayName("getSmartDiscovery calculates 90-day frequency and excludes draft/cancelled invoices")
    void testGetSmartDiscovery_Default90DaysAndActiveStatuses() {
        Long firmId = 1L;
        LocalDateTime now = LocalDateTime.now();

        List<Object[]> mockFreqRows = new ArrayList<>();
        mockFreqRows.add(new Object[]{101L, 45L, now.minusDays(1)});
        mockFreqRows.add(new Object[]{102L, 20L, now.minusDays(3)});

        List<Object[]> mockRecentRows = new ArrayList<>();
        mockRecentRows.add(new Object[]{103L, now.minusMinutes(15)});
        mockRecentRows.add(new Object[]{101L, now.minusDays(1)});

        when(invoiceItemRepo.findFrequentlyUsedProductIdsByFirmId(
                eq(firmId),
                any(),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).thenReturn(mockFreqRows);

        when(invoiceItemRepo.findRecentlyUsedProductIdsByFirmId(
                eq(firmId),
                any(),
                any(Pageable.class)
        )).thenReturn(mockRecentRows);

        when(appConfigRepo.findById("QUICK_ADD_FAVORITES_1"))
                .thenReturn(Optional.of(new AppConfig("QUICK_ADD_FAVORITES_1", "101,105")));

        SmartDiscoveryResponse response = service.getSmartDiscovery(firmId, null);

        assertNotNull(response);
        assertEquals(firmId, response.getFirmId());
        assertEquals(90, response.getPeriodDays());

        // Verify Frequently Used
        assertEquals(2, response.getFrequentlyUsed().size());
        assertEquals(101L, response.getFrequentlyUsed().get(0).getProductId());
        assertEquals(45L, response.getFrequentlyUsed().get(0).getDistinctInvoiceCount());
        assertEquals(102L, response.getFrequentlyUsed().get(1).getProductId());
        assertEquals(20L, response.getFrequentlyUsed().get(1).getDistinctInvoiceCount());

        // Verify Recently Used
        assertEquals(2, response.getRecentlyUsed().size());
        assertEquals(103L, response.getRecentlyUsed().get(0).getProductId());

        // Verify Favorites
        assertEquals(List.of(101L, 105L), response.getFavorites());

        // Verify invoice statuses strictly passed: only active ones, CANCELLED/DRAFT/ESTIMATE excluded
        ArgumentCaptor<List<InvoiceStatus>> statusCaptor = ArgumentCaptor.forClass(List.class);
        verify(invoiceItemRepo).findFrequentlyUsedProductIdsByFirmId(eq(firmId), statusCaptor.capture(), any(), any());
        List<InvoiceStatus> capturedStatuses = statusCaptor.getValue();
        assertTrue(capturedStatuses.contains(InvoiceStatus.PAID));
        assertTrue(capturedStatuses.contains(InvoiceStatus.UNPAID));
        assertTrue(capturedStatuses.contains(InvoiceStatus.FINAL));
        assertTrue(capturedStatuses.contains(InvoiceStatus.SENT));
        assertTrue(capturedStatuses.contains(InvoiceStatus.OVERDUE));
        assertFalse(capturedStatuses.contains(InvoiceStatus.CANCELLED));
        assertFalse(capturedStatuses.contains(InvoiceStatus.DRAFT));
        assertFalse(capturedStatuses.contains(InvoiceStatus.ESTIMATE));
    }

    @Test
    @DisplayName("getSmartDiscovery supports custom periodDays")
    void testGetSmartDiscovery_CustomPeriodDays() {
        Long firmId = 1L;
        when(invoiceItemRepo.findFrequentlyUsedProductIdsByFirmId(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(invoiceItemRepo.findRecentlyUsedProductIdsByFirmId(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(appConfigRepo.findById("QUICK_ADD_FAVORITES_1"))
                .thenReturn(Optional.empty());

        SmartDiscoveryResponse response = service.getSmartDiscovery(firmId, 30);
        assertEquals(30, response.getPeriodDays());
    }

    @Test
    @DisplayName("toggleFavorite adds product if not present and persists to firm config")
    void testToggleFavorite_AddFavorite() {
        Long firmId = 1L;
        Long productId = 205L;

        when(repo.existsByIdAndFirmId(productId, firmId)).thenReturn(true);
        when(appConfigRepo.findById("QUICK_ADD_FAVORITES_1"))
                .thenReturn(Optional.of(new AppConfig("QUICK_ADD_FAVORITES_1", "101,102")));

        List<Long> updated = service.toggleFavorite(productId, firmId);

        assertTrue(updated.contains(205L));
        assertTrue(updated.contains(101L));
        assertTrue(updated.contains(102L));
        assertEquals(3, updated.size());

        ArgumentCaptor<AppConfig> configCaptor = ArgumentCaptor.forClass(AppConfig.class);
        verify(appConfigRepo).save(configCaptor.capture());
        assertEquals("QUICK_ADD_FAVORITES_1", configCaptor.getValue().getConfigKey());
        assertEquals("101,102,205", configCaptor.getValue().getConfigValue());
    }

    @Test
    @DisplayName("toggleFavorite removes product if already in favorites")
    void testToggleFavorite_RemoveFavorite() {
        Long firmId = 1L;
        Long productId = 102L;

        when(repo.existsByIdAndFirmId(productId, firmId)).thenReturn(true);
        when(appConfigRepo.findById("QUICK_ADD_FAVORITES_1"))
                .thenReturn(Optional.of(new AppConfig("QUICK_ADD_FAVORITES_1", "101,102,103")));

        List<Long> updated = service.toggleFavorite(productId, firmId);

        assertFalse(updated.contains(102L));
        assertTrue(updated.contains(101L));
        assertTrue(updated.contains(103L));
        assertEquals(2, updated.size());

        ArgumentCaptor<AppConfig> configCaptor = ArgumentCaptor.forClass(AppConfig.class);
        verify(appConfigRepo).save(configCaptor.capture());
        assertEquals("101,103", configCaptor.getValue().getConfigValue());
    }

    @Test
    @DisplayName("toggleFavorite rejects product that does not belong to firm (IDOR protection)")
    void testToggleFavorite_RejectsOtherFirmProduct() {
        Long firmId = 1L;
        Long productId = 999L;

        when(repo.existsByIdAndFirmId(productId, firmId)).thenReturn(false);

        assertThrows(TenantSecurityException.class, () -> {
            service.toggleFavorite(productId, firmId);
        });

        verify(appConfigRepo, never()).save(any());
    }

    @Test
    @DisplayName("Multi-firm isolation: cross-firm requestedFirmId throws TenantSecurityException")
    void testCrossFirmSecurityCheck() {
        TenantContext.setCurrentFirmId(1L);

        assertThrows(TenantSecurityException.class, () -> {
            service.getSmartDiscovery(2L, 90);
        });

        assertThrows(TenantSecurityException.class, () -> {
            service.getFavorites(2L);
        });

        assertThrows(TenantSecurityException.class, () -> {
            service.toggleFavorite(101L, 2L);
        });

        assertThrows(TenantSecurityException.class, () -> {
            service.updateFavorites(List.of(101L), 2L);
        });
    }

    @Test
    @DisplayName("updateFavorites filters out IDs not belonging to firm")
    void testUpdateFavorites_FiltersAlienIds() {
        Long firmId = 1L;
        when(repo.existsByIdAndFirmId(101L, firmId)).thenReturn(true);
        when(repo.existsByIdAndFirmId(102L, firmId)).thenReturn(false); // belongs to firm 2
        when(repo.existsByIdAndFirmId(103L, firmId)).thenReturn(true);

        List<Long> result = service.updateFavorites(List.of(101L, 102L, 103L), firmId);

        assertEquals(List.of(101L, 103L), result);
        ArgumentCaptor<AppConfig> configCaptor = ArgumentCaptor.forClass(AppConfig.class);
        verify(appConfigRepo).save(configCaptor.capture());
        assertEquals("101,103", configCaptor.getValue().getConfigValue());
    }
}
