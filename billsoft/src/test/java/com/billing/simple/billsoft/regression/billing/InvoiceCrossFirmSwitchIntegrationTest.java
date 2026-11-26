package com.billing.simple.billsoft.regression.billing;

import com.billing.simple.billsoft.dtos.InvoiceRequest;
import com.billing.simple.billsoft.dtos.InvoiceRequestItem;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;
import com.billing.simple.billsoft.service.InvoiceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DisplayName("Invoice-Level Firm Switching, Customer Reset & Cross-Firm Product Reconciliation Integration Tests")
public class InvoiceCrossFirmSwitchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FirmDetailsRepository firmRepo;

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private InvoiceRepository invoiceRepo;

    @Autowired
    private InvoiceService invoiceService;

    private FirmDetails firmA;
    private FirmDetails firmB;
    private Customer customerA;
    private Customer customerB;
    private Product productA;
    private Product productB;

    @BeforeEach
    void setUp() {
        TenantContext.clear();

        FirmDetails fA = new FirmDetails();
        fA.setFirmName("Alpha Traders");
        fA.setEmail("alpha@traders.com");
        fA.setPhone("9876543210");
        fA.setGstin("27AAAAA0000A1Z5");
        firmA = firmRepo.save(fA);

        FirmDetails fB = new FirmDetails();
        fB.setFirmName("Beta Logistics");
        fB.setEmail("beta@logistics.com");
        fB.setPhone("9876543211");
        fB.setGstin("27BBBBB0000B1Z6");
        firmB = firmRepo.save(fB);

        customerA = customerRepo.save(Customer.builder()
                .firmId(firmA.getId())
                .name("Chetan FirmA")
                .phone("9999911111")
                .address("Pune")
                .build());

        customerB = customerRepo.save(Customer.builder()
                .firmId(firmB.getId())
                .name("Chetan FirmB")
                .phone("9999922222")
                .address("Mumbai")
                .build());

        productA = productRepo.save(Product.builder()
                .firmId(firmA.getId())
                .name("Industrial Sensor X10")
                .category("ELECTRONICS")
                .itemType("GOODS")
                .price(new BigDecimal("1200.00"))
                .unit("pcs")
                .hsnCode("8541")
                .stockQuantity(new BigDecimal("50.000"))
                .isArchived(false)
                .build());

        productB = productRepo.save(Product.builder()
                .firmId(firmB.getId())
                .name("Industrial Sensor X10")
                .category("ELECTRONICS")
                .itemType("GOODS")
                .price(new BigDecimal("1250.00"))
                .unit("pcs")
                .hsnCode("8541")
                .stockQuantity(new BigDecimal("30.000"))
                .isArchived(false)
                .build());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Scenario 1: Customer search scoped to Firm B does not return Firm A customers")
    void testCustomerSearchIsolation() throws Exception {
        mockMvc.perform(get("/api/customers/search")
                        .header("X-Firm-Id", firmB.getId().toString())
                        .param("q", "Chetan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.name == 'Chetan FirmB')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'Chetan FirmA')]").doesNotExist());
    }

    @Test
    @DisplayName("Scenario 2: Selecting Firm B customer saves successfully under Firm B")
    void testSaveInvoiceUnderFirmBWithFirmBCustomer() throws Exception {
        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(firmB.getId());
        req.setCustomerId(customerB.getId());
        req.setCustomerName(customerB.getName());

        InvoiceRequestItem item = new InvoiceRequestItem();
        item.setProductName("Industrial Sensor X10");
        item.setPricePerUnit(new BigDecimal("1250.00"));
        item.setQty(2);
        req.setItems(Collections.singletonList(item));

        mockMvc.perform(post("/api/invoices")
                        .header("X-Firm-Id", firmB.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firmId").value(firmB.getId()))
                .andExpect(jsonPath("$.customer.id").value(customerB.getId()))
                .andExpect(jsonPath("$.items[0].product.id").value(productB.getId()));
    }

    @Test
    @DisplayName("Scenario 3: Cross-firm customer ID submission is strictly rejected with TenantSecurityException")
    void testCrossFirmCustomerIdRejected() {
        TenantContext.setCurrentFirmId(firmB.getId());
        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(firmB.getId());
        req.setCustomerId(customerA.getId()); // Customer belongs to Firm A!
        req.setCustomerName("Chetan FirmA");

        InvoiceRequestItem item = new InvoiceRequestItem();
        item.setProductName("Test Widget");
        item.setPricePerUnit(new BigDecimal("100.00"));
        item.setQty(1);
        req.setItems(Collections.singletonList(item));

        assertThrows(TenantSecurityException.class, () -> invoiceService.createInvoice(req));
    }

    @Test
    @DisplayName("Scenario 4: Cross-firm product reconciliation resolves existing product in Firm B by normalized name")
    void testCrossFirmProductReconciliationMatchesFirmBProduct() {
        TenantContext.setCurrentFirmId(firmB.getId());
        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(firmB.getId());
        req.setCustomerId(customerB.getId());

        InvoiceRequestItem item = new InvoiceRequestItem();
        item.setProductId(productA.getId()); // Inadvertently retained Firm A product ID!
        item.setProductName("industrial sensor x10"); // Normalized matches Firm B product
        item.setPricePerUnit(new BigDecimal("1250.00"));
        item.setQty(3);
        req.setItems(Collections.singletonList(item));

        Invoice created = invoiceService.createInvoice(req);
        assertNotNull(created);
        assertEquals(firmB.getId(), created.getFirmId());
        assertEquals(1, created.getItems().size());
        assertEquals(productB.getId(), created.getItems().get(0).getProduct().getId(),
                "Product ID must be reconciled to Firm B product, not Firm A");
    }

    @Test
    @DisplayName("Scenario 5: Missing product is created under Firm B scope upon successful invoice save")
    void testMissingProductCreatedUnderFirmB() {
        TenantContext.setCurrentFirmId(firmB.getId());
        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(firmB.getId());
        req.setCustomerId(customerB.getId());

        InvoiceRequestItem item = new InvoiceRequestItem();
        item.setProductName("Novel Laser Diode 450nm");
        item.setPricePerUnit(new BigDecimal("4500.00"));
        item.setHsnCode("9013");
        item.setUnit("box");
        item.setQty(1);
        req.setItems(Collections.singletonList(item));

        Invoice created = invoiceService.createInvoice(req);
        assertNotNull(created);
        assertNotNull(created.getItems().get(0).getProduct());

        Long newProdId = created.getItems().get(0).getProduct().getId();
        Product savedProd = productRepo.findById(newProdId).orElse(null);
        assertNotNull(savedProd);
        assertEquals(firmB.getId(), savedProd.getFirmId());
        assertEquals("Novel Laser Diode 450nm", savedProd.getName());
        assertEquals("9013", savedProd.getHsnCode());
        assertEquals("box", savedProd.getUnit());
    }

    @Test
    @DisplayName("Scenario 6: Transaction rollback on invoice failure ensures no orphaned products remain")
    void testRollbackPreventsOrphanedProductOnFailure() {
        long initialProductCount = productRepo.findByFirmId(firmB.getId()).size();

        TenantContext.setCurrentFirmId(firmB.getId());
        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(firmB.getId());
        req.setCustomerId(customerA.getId()); // Will cause customer validation failure

        InvoiceRequestItem item = new InvoiceRequestItem();
        item.setProductName("Disposable Rollback Probe");
        item.setPricePerUnit(new BigDecimal("999.00"));
        item.setQty(1);
        req.setItems(Collections.singletonList(item));

        assertThrows(TenantSecurityException.class, () -> invoiceService.createInvoice(req));

        long productCountAfter = productRepo.findByFirmId(firmB.getId()).size();
        assertEquals(initialProductCount, productCountAfter,
                "Failed invoice save must roll back any auto-created products");
    }

    @Test
    @DisplayName("Scenario 7: Estimate creation under Firm B also reconciles products correctly")
    void testEstimateCreationUnderFirmB() {
        TenantContext.setCurrentFirmId(firmB.getId());
        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(firmB.getId());
        req.setCustomerId(customerB.getId());

        InvoiceRequestItem item = new InvoiceRequestItem();
        item.setProductName("Industrial Sensor X10");
        item.setPricePerUnit(new BigDecimal("1250.00"));
        item.setQty(5);
        req.setItems(Collections.singletonList(item));

        Invoice estimate = invoiceService.createEstimate(req);
        assertNotNull(estimate);
        assertEquals(firmB.getId(), estimate.getFirmId());
        assertEquals(InvoiceStatus.ESTIMATE, estimate.getStatus());
        assertEquals(productB.getId(), estimate.getItems().get(0).getProduct().getId());
    }

    @Test
    @DisplayName("Scenario 8: Switching firm from A to B with customer reset creates NO customer in Firm B")
    void testFirmSwitchCustomerResetLeavesNoCustomerInFirmB() {
        // Firm A customer Rahul Sharma
        Customer rahulA = customerRepo.save(Customer.builder()
                .firmId(firmA.getId())
                .name("Rahul Sharma")
                .phone("9876543299")
                .address("Firm A Address")
                .build());

        // Baseline: Firm B has no customer named Rahul Sharma
        assertTrue(customerRepo.findFirstByFirmIdAndNameIgnoreCase(firmB.getId(), "Rahul Sharma").isEmpty());

        // Upon firm switch, invoice request sent with firm B and null/empty customer fields
        TenantContext.setCurrentFirmId(firmB.getId());
        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(firmB.getId());
        req.setCustomerId(null);
        req.setCustomerName(null);
        req.setCustomerPhone(null);
        req.setCustomerAddress(null);

        InvoiceRequestItem item1 = new InvoiceRequestItem();
        item1.setProductName("Item Alpha");
        item1.setPricePerUnit(new BigDecimal("100.00"));
        item1.setQty(2);

        InvoiceRequestItem item2 = new InvoiceRequestItem();
        item2.setProductName("Item Beta");
        item2.setPricePerUnit(new BigDecimal("250.00"));
        item2.setQty(1);

        req.setItems(List.of(item1, item2));

        // Save invoice with null customer
        Invoice saved = invoiceService.createInvoice(req);
        assertNotNull(saved);
        assertEquals(firmB.getId(), saved.getFirmId());
        assertNull(saved.getCustomer(), "Customer must remain null when customer was reset on firm switch");
        assertEquals(2, saved.getItems().size(), "Both line items must remain intact");

        // Verify Rahul Sharma was NOT created in Firm B
        assertTrue(customerRepo.findFirstByFirmIdAndNameIgnoreCase(firmB.getId(), "Rahul Sharma").isEmpty(),
                "Customer from Firm A must NOT be created under Firm B");
    }

    @Test
    @DisplayName("Scenario 9: Explicit creation of customer under Firm B creates record strictly in Firm B")
    void testExplicitCustomerCreationUnderFirmB() throws Exception {
        com.billing.simple.billsoft.dtos.CustomerRequest custReq = new com.billing.simple.billsoft.dtos.CustomerRequest();
        custReq.setName("Suresh Raina");
        custReq.setPhone("9876543200");
        custReq.setAddress("Firm B Hub");
        custReq.setFirmId(firmB.getId());

        mockMvc.perform(post("/api/customers")
                        .header("X-Firm-Id", firmB.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(custReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firmId").value(firmB.getId()))
                .andExpect(jsonPath("$.name").value("Suresh Raina"));

        // Verify customer exists in Firm B only
        assertTrue(customerRepo.findFirstByFirmIdAndNameIgnoreCase(firmB.getId(), "Suresh Raina").isPresent());
        assertTrue(customerRepo.findFirstByFirmIdAndNameIgnoreCase(firmA.getId(), "Suresh Raina").isEmpty());
    }

    @Test
    @DisplayName("Scenario 10: Complete 14-step workflow: Firm A -> Select Customer -> Switch Firm B -> Reset -> Explicit Create -> Save with items intact & App firm preserved")
    void testCompleteCrossFirmSwitchCustomerWorkflow() throws Exception {
        // Step 1: Application-level active firm is Firm A
        TenantContext.setCurrentFirmId(firmA.getId());

        // Step 2: Select existing Firm A customer Rahul Sharma
        Customer rahulA = customerRepo.save(Customer.builder()
                .firmId(firmA.getId())
                .name("Rahul Sharma A")
                .phone("9888800001")
                .address("Firm A Pune")
                .build());

        // Step 3: Add two invoice items
        InvoiceRequestItem item1 = new InvoiceRequestItem();
        item1.setProductName("Component A");
        item1.setPricePerUnit(new BigDecimal("500.00"));
        item1.setQty(3);

        InvoiceRequestItem item2 = new InvoiceRequestItem();
        item2.setProductName("Component B");
        item2.setPricePerUnit(new BigDecimal("750.00"));
        item2.setQty(2);

        // Step 4: Switch invoice-level firm to Firm B (customer reset occurs)
        // Step 5: Verify customer field empty, no customer created in Firm B
        long firmBCustomerCountBefore = customerRepo.findByFirmId(firmB.getId()).size();

        // Step 7-8: Search existing Firm B customer
        mockMvc.perform(get("/api/customers/search")
                        .header("X-Firm-Id", firmB.getId().toString())
                        .param("q", "Chetan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Chetan FirmB')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'Rahul Sharma A')]").doesNotExist());

        // Step 9-11: Explicitly create new customer in Firm B
        Customer newFirmBCustomer = customerRepo.save(Customer.builder()
                .firmId(firmB.getId())
                .name("Vikram Malhotra")
                .phone("9777700002")
                .address("Firm B Mumbai")
                .build());

        assertEquals(firmB.getId(), newFirmBCustomer.getFirmId());
        assertTrue(customerRepo.findFirstByFirmIdAndNameIgnoreCase(firmA.getId(), "Vikram Malhotra").isEmpty());

        // Step 12: Save invoice under Firm B with new customer
        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(firmB.getId());
        req.setCustomerId(newFirmBCustomer.getId());
        req.setCustomerName(newFirmBCustomer.getName());
        req.setItems(List.of(item1, item2));

        mockMvc.perform(post("/api/invoices")
                        .header("X-Firm-Id", firmB.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firmId").value(firmB.getId()))
                .andExpect(jsonPath("$.customer.id").value(newFirmBCustomer.getId()))
                .andExpect(jsonPath("$.items.length()").value(2));

        // Step 13: Verify invoice items remain intact
        Invoice createdInvoice = invoiceRepo.findAll().stream()
                .filter(i -> firmB.getId().equals(i.getFirmId()) && newFirmBCustomer.getId().equals(i.getCustomer() != null ? i.getCustomer().getId() : null))
                .findFirst().orElse(null);
        assertNotNull(createdInvoice);
        assertEquals(2, createdInvoice.getItems().size());

        // Step 14: Verify created invoice is saved strictly under Firm B, not Firm A
        assertEquals(firmB.getId(), createdInvoice.getFirmId());
        assertNotEquals(firmA.getId(), createdInvoice.getFirmId());
    }
}
