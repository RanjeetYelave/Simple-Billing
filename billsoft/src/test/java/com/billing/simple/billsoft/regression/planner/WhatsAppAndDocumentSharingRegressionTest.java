package com.billing.simple.billsoft.regression.planner;

import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.entities.Party;
import com.billing.simple.billsoft.entities.PurchaseOrder;
import com.billing.simple.billsoft.entities.PurchaseOrderStatus;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;
import com.billing.simple.billsoft.repositories.PartyRepository;
import com.billing.simple.billsoft.repositories.PurchaseOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class WhatsAppAndDocumentSharingRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FirmDetailsRepository firmRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private PurchaseOrderRepository poRepository;

    private Long firmId;
    private Long customerId;
    private Long partyId;
    private Long poId;

    @BeforeEach
    void setUp() {
        FirmDetails firm = new FirmDetails();
        firm.setFirmName("Apex Enterprises Test");
        firm.setPhone("9876543210");
        firm.setEmail("test@apex.com");
        firm.setUpiId("apex@upi");
        firm = firmRepository.save(firm);
        this.firmId = firm.getId();

        Customer customer = customerRepository.save(Customer.builder()
                .name("Ranjeet Yelave")
                .phone("9123456780")
                .firmId(this.firmId)
                .build());
        this.customerId = customer.getId();

        Party party = partyRepository.save(Party.builder()
                .name("Steel Corp Supplier")
                .phone("9876500000")
                .firmId(this.firmId)
                .openingBalance(BigDecimal.valueOf(1500))
                .openingBalanceType("PAYABLE")
                .build());
        this.partyId = party.getId();

        PurchaseOrder po = poRepository.save(PurchaseOrder.builder()
                .poNumber("PO-TEST-001")
                .party(party)
                .partyName("Steel Corp Supplier")
                .firmId(this.firmId)
                .poDate(LocalDate.now())
                .totalAmount(BigDecimal.valueOf(12500))
                .status(PurchaseOrderStatus.ISSUED)
                .build());
        this.poId = po.getId();
    }

    @Test
    void testFirmEndpoints() throws Exception {
        mockMvc.perform(get("/api/firm")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/firm/" + this.firmId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void testCustomerStatementRetrieval() throws Exception {
        mockMvc.perform(get("/api/statements/customer/" + this.customerId)
                .header("X-Firm-Id", this.firmId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void testPartyStatementRetrieval() throws Exception {
        mockMvc.perform(get("/api/statements/party/" + this.partyId)
                .header("X-Firm-Id", this.firmId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void testPurchaseOrderRetrieval() throws Exception {
        mockMvc.perform(get("/api/purchase-orders/" + this.poId)
                .header("X-Firm-Id", this.firmId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void testFirmStatementRetrieval() throws Exception {
        mockMvc.perform(get("/api/statements/firm")
                .header("X-Firm-Id", this.firmId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
