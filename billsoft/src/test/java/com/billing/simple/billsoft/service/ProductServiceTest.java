package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.entities.StockMovement;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.repo.StockMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductServiceTest {

    @Mock
    private ProductRepository repo;

    @Mock
    private StockMovementRepository stockMovementRepo;

    @InjectMocks
    private ProductService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCreate() {
        Product product = new Product();
        product.setName("Test Product");
        product.setStockQuantity(BigDecimal.TEN);
        when(repo.save(product)).thenReturn(product);

        Product result = service.create(product);

        assertEquals("Test Product", result.getName());
        verify(repo, times(1)).save(product);
        verify(stockMovementRepo, times(1)).save(any(StockMovement.class));
    }

    @Test
    void testGetAll() {
        Long firmId = 1L;
        List<Product> products = Arrays.asList(new Product(), new Product());
        when(repo.findByFirmId(firmId)).thenReturn(products);

        List<Product> result = service.getAll(firmId);

        assertEquals(2, result.size());
        verify(repo, times(1)).findByFirmId(firmId);
    }

    @Test
    void testGetById() {
        Long id = 1L;
        Product product = new Product();
        product.setId(id);
        when(repo.findById(id)).thenReturn(Optional.of(product));

        Product result = service.getById(id);

        assertNotNull(result);
        assertEquals(id, result.getId());
    }

    @Test
    void testUpdate() {
        Long id = 1L;
        Product existing = new Product();
        existing.setId(id);
        existing.setName("Old Product");

        Product updated = new Product();
        updated.setName("New Product");
        updated.setPrice(new BigDecimal("100.00"));

        when(repo.findById(id)).thenReturn(Optional.of(existing));
        when(repo.save(existing)).thenReturn(existing);

        Product result = service.update(id, updated);

        assertNotNull(result);
        assertEquals("New Product", result.getName());
        assertEquals(new BigDecimal("100.00"), result.getPrice());
    }

    @Test
    void testUpdateNotFound() {
        Long id = 1L;
        when(repo.findById(id)).thenReturn(Optional.empty());

        Product result = service.update(id, new Product());

        assertNull(result);
    }

    @Test
    void testAdjustStock() {
        Long id = 1L;
        Product product = new Product();
        product.setId(id);
        product.setStockQuantity(new BigDecimal("10.000"));
        when(repo.findById(id)).thenReturn(Optional.of(product));
        when(repo.save(any(Product.class))).thenReturn(product);

        Product result = service.adjustStock(id, new BigDecimal("5.000"), "ADD", "Restocked");

        assertNotNull(result);
        assertEquals(new BigDecimal("15.000"), result.getStockQuantity());
        verify(stockMovementRepo, times(1)).save(any(StockMovement.class));
    }

    @Test
    void testGetInventorySummary() {
        Long firmId = 1L;
        Product p1 = Product.builder().id(1L).firmId(firmId).name("P1").price(new BigDecimal("100.00")).costPrice(new BigDecimal("60.00")).stockQuantity(new BigDecimal("10")).minStockLevel(new BigDecimal("5")).itemType("GOODS").category("Electronics").build();
        Product p2 = Product.builder().id(2L).firmId(firmId).name("P2").price(new BigDecimal("50.00")).costPrice(new BigDecimal("30.00")).stockQuantity(new BigDecimal("2")).minStockLevel(new BigDecimal("5")).itemType("GOODS").category("Electronics").build();
        when(repo.findByFirmId(firmId)).thenReturn(Arrays.asList(p1, p2));

        Map<String, Object> summary = service.getInventorySummary(firmId);

        assertNotNull(summary);
        assertEquals(2L, summary.get("totalProducts"));
        assertEquals(1L, summary.get("lowStockCount"));
    }

    @Test
    void testDelete() {
        Long id = 1L;
        when(repo.existsById(id)).thenReturn(true);

        boolean result = service.delete(id);

        assertTrue(result);
        verify(repo, times(1)).deleteById(id);
    }

    @Test
    void testDeleteNotFound() {
        Long id = 1L;
        when(repo.existsById(id)).thenReturn(false);

        boolean result = service.delete(id);

        assertFalse(result);
    }
}
