package com.billing.simple.billsoft.regression;

import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
public class FirmLifecyclePersistenceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FirmDetailsRepository firmRepo;

    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        TenantContext.clear();
        productRepo.deleteAll();
        firmRepo.deleteAll();
    }

    @Test
    @DisplayName("End-to-End Firm Lifecycle: First-time setup, persistence, multi-firm creation, and tenant isolation")
    void testFullFirmLifecycleAndPersistence() throws Exception {
        // Step 1: Initial empty state
        mockMvc.perform(get("/api/firm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Step 2: First-time setup - creating primary firm
        FirmDetails primaryFirm = new FirmDetails();
        primaryFirm.setFirmName("Acme Supermarket");
        primaryFirm.setOwnerName("John Doe");
        primaryFirm.setPhone("9876543210");
        primaryFirm.setEmail("billing@acme.com");
        primaryFirm.setAddressLine1("123 Market Street");
        primaryFirm.setCity("Mumbai");
        primaryFirm.setState("Maharashtra");
        primaryFirm.setPincode("400001");
        primaryFirm.setGstin("27AAAAA0000A1Z5");
        primaryFirm.setBankName("HDFC Bank");
        primaryFirm.setBankAccount("50200012345678");
        primaryFirm.setBankIfsc("HDFC0001234");
        primaryFirm.setUpiId("acme@hdfc");

        String createFirm1Json = mockMvc.perform(post("/api/firm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(primaryFirm)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.firmName").value("Acme Supermarket"))
                .andExpect(jsonPath("$.ownerName").value("John Doe"))
                .andExpect(jsonPath("$.city").value("Mumbai"))
                .andExpect(jsonPath("$.upiId").value("acme@hdfc"))
                .andReturn().getResponse().getContentAsString();

        FirmDetails savedFirm1 = objectMapper.readValue(createFirm1Json, FirmDetails.class);
        Long firm1Id = savedFirm1.getId();
        assertThat(firm1Id).isNotNull();

        // Step 3: Verify GET /api/firm lists the primary firm
        mockMvc.perform(get("/api/firm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(firm1Id))
                .andExpect(jsonPath("$[0].firmName").value("Acme Supermarket"));

        // Step 4: Add Second Firm from UI (explicit firm name payload)
        FirmDetails secondFirm = new FirmDetails();
        secondFirm.setFirmName("Apex Logistics");

        String createFirm2Json = mockMvc.perform(post("/api/firm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondFirm)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.firmName").value("Apex Logistics"))
                .andReturn().getResponse().getContentAsString();

        FirmDetails savedFirm2 = objectMapper.readValue(createFirm2Json, FirmDetails.class);
        Long firm2Id = savedFirm2.getId();
        assertThat(firm2Id).isNotNull().isNotEqualTo(firm1Id);

        // Step 5: Verify POST /api/firm with empty JSON "{}" works safely
        mockMvc.perform(post("/api/firm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.firmName").value("New Firm"));

        // Step 6: Verify all 3 firms are returned and persisted in database
        List<FirmDetails> allFirms = firmRepo.findAll();
        assertThat(allFirms).hasSize(3);

        // Step 7: Tenant Isolation - Create product for Firm 1
        TenantContext.setCurrentFirmId(firm1Id);
        Product productFirm1 = new Product();
        productFirm1.setName("Basmati Rice 5kg");
        productFirm1.setPrice(new java.math.BigDecimal("450.0"));
        productFirm1.setFirmId(firm1Id);
        productRepo.save(productFirm1);

        // Verify product is visible under Firm 1 context
        mockMvc.perform(get("/api/products").header("X-Firm-Id", firm1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Basmati Rice 5kg"));

        // Switch to Firm 2 context - verify product from Firm 1 is NOT visible
        TenantContext.setCurrentFirmId(firm2Id);
        mockMvc.perform(get("/api/products").header("X-Firm-Id", firm2Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Step 8: Update Firm 1 details
        primaryFirm.setAddressLine1("456 New Road");
        mockMvc.perform(put("/api/firm/" + firm1Id)
                        .header("X-Firm-Id", firm1Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(primaryFirm)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addressLine1").value("456 New Road"));

        // Step 9: Re-query database to guarantee direct persistence
        FirmDetails directCheck = firmRepo.findById(firm1Id).orElseThrow();
        assertThat(directCheck.getAddressLine1()).isEqualTo("456 New Road");
        assertThat(directCheck.getFirmName()).isEqualTo("Acme Supermarket");
    }
}
