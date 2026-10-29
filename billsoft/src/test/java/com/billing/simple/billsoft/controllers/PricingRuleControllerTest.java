package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.dtos.CategoryPricingRuleDto;
import com.billing.simple.billsoft.dtos.PricingProfileDto;
import com.billing.simple.billsoft.service.ProductPricingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PricingRuleControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ProductPricingService pricingService;

    @InjectMocks
    private PricingRuleController controller;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/pricing-rules/categories returns category rules with operators")
    void testGetCategoryRules() throws Exception {
        CategoryPricingRuleDto rule = CategoryPricingRuleDto.builder()
                .id(1L)
                .firmId(10L)
                .categoryName("Electronics")
                .pricingProfileId(2L)
                .operator("*")
                .adjustmentType("FIXED")
                .adjustmentValue(new BigDecimal("1.50"))
                .build();

        when(pricingService.getCategoryRules(10L)).thenReturn(List.of(rule));

        mockMvc.perform(get("/api/pricing-rules/categories")
                        .param("firmId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryName").value("Electronics"))
                .andExpect(jsonPath("$[0].operator").value("*"))
                .andExpect(jsonPath("$[0].adjustmentType").value("FIXED"))
                .andExpect(jsonPath("$[0].adjustmentValue").value(1.50));
    }

    @Test
    @DisplayName("PUT /api/pricing-rules/categories saves rules with operators")
    void testSaveCategoryRules() throws Exception {
        CategoryPricingRuleDto rule = CategoryPricingRuleDto.builder()
                .categoryName("Furniture")
                .pricingProfileId(2L)
                .operator("/")
                .adjustmentType("PERCENT")
                .adjustmentValue(new BigDecimal("80.00"))
                .build();

        when(pricingService.saveCategoryRules(eq(10L), any())).thenReturn(List.of(rule));

        mockMvc.perform(put("/api/pricing-rules/categories")
                        .param("firmId", "10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(rule))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryName").value("Furniture"))
                .andExpect(jsonPath("$[0].operator").value("/"))
                .andExpect(jsonPath("$[0].adjustmentType").value("PERCENT"))
                .andExpect(jsonPath("$[0].adjustmentValue").value(80.00));
    }

    @Test
    @DisplayName("GET /api/products/effective-prices returns effective price mapping")
    void testGetEffectivePrices() throws Exception {
        when(pricingService.getEffectivePricesForFirm(10L, 2L))
                .thenReturn(Map.of(101L, new BigDecimal("450.00")));

        mockMvc.perform(get("/api/products/effective-prices")
                        .param("firmId", "10")
                        .param("profileId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pricingProfileId").value(2))
                .andExpect(jsonPath("$.effectivePrices['101']").value(450.00));
    }
}
