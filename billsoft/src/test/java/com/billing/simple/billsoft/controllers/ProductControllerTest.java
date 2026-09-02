package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.entities.StockMovement;
import com.billing.simple.billsoft.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService service;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testCreate() throws Exception {
        Product product = new Product();
        product.setName("Test Product");
        when(service.create(any(Product.class))).thenReturn(product);

        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(product)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Product"));
    }

    @Test
    void testGetAll() throws Exception {
        when(service.getAll(any())).thenReturn(Arrays.asList(new Product(), new Product()));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void testGetSummary() throws Exception {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalProducts", 5L);
        summary.put("lowStockCount", 1L);
        when(service.getInventorySummary(any())).thenReturn(summary);

        mockMvc.perform(get("/api/products/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProducts").value(5))
                .andExpect(jsonPath("$.lowStockCount").value(1));
    }

    @Test
    void testAdjustStock() throws Exception {
        Product p = new Product();
        p.setId(1L);
        p.setStockQuantity(new BigDecimal("25.000"));
        when(service.adjustStock(eq(1L), any(), any(), any())).thenReturn(p);

        Map<String, Object> req = new HashMap<>();
        req.put("quantity", 25);
        req.put("mode", "SET");
        req.put("note", "Count audit");

        mockMvc.perform(post("/api/products/1/adjust-stock")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity").value(25.0));
    }

    @Test
    void testGetMovements() throws Exception {
        StockMovement m = StockMovement.builder().id(10L).movementType("INVOICE_SALE").build();
        when(service.getStockMovements(eq(1L), any())).thenReturn(Collections.singletonList(m));

        mockMvc.perform(get("/api/products/1/movements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void testGetById() throws Exception {
        Product p = new Product();
        p.setId(1L);
        when(service.getById(1L)).thenReturn(p);

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void testGetByIdNotFound() throws Exception {
        when(service.getById(1L)).thenReturn(null);

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testUpdate() throws Exception {
        Product p = new Product();
        p.setName("Updated");
        when(service.update(eq(1L), any(Product.class))).thenReturn(p);

        mockMvc.perform(put("/api/products/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated"));
    }

    @Test
    void testDelete() throws Exception {
        when(service.delete(1L)).thenReturn(true);

        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isNoContent());
    }
}
