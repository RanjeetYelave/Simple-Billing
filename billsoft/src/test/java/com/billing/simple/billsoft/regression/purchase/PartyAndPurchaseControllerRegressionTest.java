package com.billing.simple.billsoft.regression.purchase;

import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.entities.Party;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.entities.PurchaseOrder;
import com.billing.simple.billsoft.entities.PurchaseOrderItem;
import com.billing.simple.billsoft.entities.PurchaseOrderStatus;
import com.billing.simple.billsoft.service.BackupService;
import com.billing.simple.billsoft.service.FirmDetailsService;
import com.billing.simple.billsoft.service.PartyService;
import com.billing.simple.billsoft.service.ProductService;
import com.billing.simple.billsoft.service.PurchaseOrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Tag("regression")
@Tag("integration")
@DisplayName("Party & Purchase Order Controller REST API Regression Tests")
class PartyAndPurchaseControllerRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PartyService partyService;

    @Autowired
    private ProductService productService;

    @Autowired
    private PurchaseOrderService poService;

    @Autowired
    private FirmDetailsService firmService;

    @Autowired
    private BackupService backupService;

    @Autowired
    private ObjectMapper objectMapper;

    private final Long testFirmId = 1L;
    private Party testParty;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        backupService.factoryReset();

        FirmDetails firm = new FirmDetails();
        firm.setFirmName("Precision Engineering Hub");
        firm.setGstin("27AAACB5566G1Z9");
        firm.setAddressLine1("Industrial Corridor, Pune");
        firmService.create(firm);

        testParty = partyService.createParty(Party.builder()
                .name("Standard Steel Suppliers")
                .phone("9822339900")
                .email("sales@standardsteel.com")
                .gstin("27AAACS1122D1Z3")
                .firmId(testFirmId)
                .build());

        testProduct = productService.create(Product.builder()
                .name("Steel Rod 12mm")
                .price(BigDecimal.valueOf(450.00))
                .costPrice(BigDecimal.valueOf(350.00))
                .stockQuantity(BigDecimal.valueOf(20.0))
                .firmId(testFirmId)
                .build());
    }

    @Test
    @DisplayName("Should perform Party CRUD, financial summary, and ledger payment via API")
    void testPartyRestEndpoints() throws Exception {
        // 1. List parties
        mockMvc.perform(get("/api/parties?firmId=" + testFirmId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Standard Steel Suppliers"));

        // 2. Party financial summary
        mockMvc.perform(get("/api/parties/" + testParty.getId() + "/financial-summary?firmId=" + testFirmId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partyId").value(testParty.getId()));

        // 3. Record party payment
        Map<String, Object> paymentReq = Map.of(
                "amount", 15000.0,
                "paymentDate", LocalDate.now().toString(),
                "paymentMode", "BANK_TRANSFER",
                "reference", "NEFT-776655",
                "notes", "Material advance"
        );

        mockMvc.perform(post("/api/parties/" + testParty.getId() + "/payments")
                        .header("X-Firm-Id", testFirmId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(15000.0));

        // 4. List party payment history
        mockMvc.perform(get("/api/parties/" + testParty.getId() + "/payments?firmId=" + testFirmId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].amount").value(15000.0));
    }

    @Test
    @DisplayName("Should perform PO lifecycle and PDF download via REST API")
    void testPurchaseOrderRestEndpoints() throws Exception {
        PurchaseOrder po = PurchaseOrder.builder()
                .party(testParty)
                .poDate(LocalDate.now())
                .firmId(testFirmId)
                .status(PurchaseOrderStatus.ISSUED)
                .items(List.of(PurchaseOrderItem.builder()
                        .productId(testProduct.getId())
                        .productName(testProduct.getName())
                        .quantity(BigDecimal.valueOf(30.0))
                        .unitPrice(BigDecimal.valueOf(350.00))
                        .gstPercent(BigDecimal.valueOf(18.00))
                        .build()))
                .build();

        PurchaseOrder created = poService.createPurchaseOrder(po);

        // 1. Query PO by ID
        mockMvc.perform(get("/api/purchase-orders/" + created.getId() + "?firmId=" + testFirmId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.getId()));

        // 2. Query PO PDF
        mockMvc.perform(get("/api/purchase-orders/" + created.getId() + "/pdf?firmId=" + testFirmId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));

        // 3. Update PO Status to RECEIVED
        mockMvc.perform(patch("/api/purchase-orders/" + created.getId() + "/status?firmId=" + testFirmId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "RECEIVED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"));
    }
}
