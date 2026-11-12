package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dtos.*;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repo.InvoiceRepository;
import com.billing.simple.billsoft.repositories.BusinessPartyLinkRepository;
import com.billing.simple.billsoft.repositories.PartyPaymentRepository;
import com.billing.simple.billsoft.repositories.PartyRepository;
import com.billing.simple.billsoft.repositories.PurchaseOrderRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:billsoft-bpl-int;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.show-sql=false"
        }
)
@ActiveProfiles("test")
@Transactional
class BusinessPartyLinkIntegrationTest {

    private static final Long FIRM_1 = 101L;
    private static final Long FIRM_2 = 202L;

    @Autowired private BusinessPartyLinkService linkService;
    @Autowired private BusinessPartyLinkRepository linkRepo;
    @Autowired private CustomerRepository customerRepo;
    @Autowired private PartyRepository partyRepo;
    @Autowired private InvoiceRepository invoiceRepo;
    @Autowired private PurchaseOrderRepository poRepo;
    @Autowired private PartyPaymentRepository partyPaymentRepo;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentFirmId(FIRM_1);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("End-to-end DB: Customer -> Enable Supplier Role -> Match Preview Reuse -> Ledger Isolation -> Sync -> Lifecycle")
    void testEndToEndMultiRoleLifecycleWithRealPersistence() {
        // 1. Existing Customer in Firm 1 with an unpaid Invoice of 5000
        Customer cust = customerRepo.save(Customer.builder()
                .firmId(FIRM_1)
                .name("Surya Distributors")
                .phone("9876500001")
                .email("sales@surya.in")
                .address("10 MG Road")
                .gstin("27SURYA1234A1Z9")
                .build());

        Invoice inv = invoiceRepo.save(Invoice.builder()
                .firmId(FIRM_1)
                .customer(cust)
                .invoiceNumber("INV-INT-1001")
                .invoiceDate(LocalDateTime.now())
                .status(InvoiceStatus.UNPAID)
                .totalAmount(new BigDecimal("5000.00"))
                .subtotalWithoutTax(new BigDecimal("5000.00"))
                .totalTax(BigDecimal.ZERO)
                .build());

        // 2. Preview shows CREATE_NEW initially
        RoleMatchPreviewResponse preNew = linkService.previewEnablePartyRole(cust.getId(), null, null);
        assertEquals("CREATE_NEW", preNew.getResolution());

        // 3. Enable Supplier role with bank + opening balance 3000 PAYABLE
        BusinessPartyLinkResponse enabled = linkService.enablePartyRole(EnablePartyRoleRequest.builder()
                .customerId(cust.getId())
                .bankName("ICICI Bank")
                .bankAccount("9988001122")
                .bankIfsc("ICIC0000111")
                .pan("SURYA1234A")
                .openingBalance(new BigDecimal("3000.00"))
                .openingBalanceType("PAYABLE")
                .build());

        assertNotNull(enabled.getId());
        assertEquals(cust.getId(), enabled.getCustomerId());
        assertNotNull(enabled.getPartyId());
        assertEquals("ACTIVE", enabled.getStatus());
        // Ledgers strictly separate: Customer receivable 5000, Party payable 3000
        assertEquals(5000.0, enabled.getCustomerNetReceivable(), 0.01);
        assertEquals(3000.0, enabled.getPartyNetPayable(), 0.01);

        // 4. Duplicate enablePartyRole rejected (1:1 constraint preserved)
        assertThrows(IllegalStateException.class, () ->
                linkService.enablePartyRole(EnablePartyRoleRequest.builder().customerId(cust.getId()).build()));

        // 5. Sync Customer -> Supplier updates shared contact/tax without overwriting Party bank/opening balance
        cust.setEmail("updated@surya.in");
        cust.setPhone("9876599999");
        customerRepo.save(cust);

        BusinessPartyLinkResponse synced = linkService.syncProfiles(enabled.getId(), SyncProfileRequest.builder()
                .source("CUSTOMER")
                .name(cust.getName())
                .phone("9876599999")
                .email("updated@surya.in")
                .address(cust.getAddress())
                .gstin(cust.getGstin())
                .build());

        Party party = partyRepo.findByIdAndFirmId(enabled.getPartyId(), FIRM_1).orElseThrow();
        assertEquals("updated@surya.in", party.getEmail());
        assertEquals("9876599999", party.getPhone());
        assertEquals("ICICI Bank", party.getBankName());
        assertEquals(0, new BigDecimal("3000.00").compareTo(party.getOpeningBalance()));

        // 6. Disabling Customer role blocked because Customer has 5000 unpaid receivable
        IllegalStateException custBlock = assertThrows(IllegalStateException.class, () ->
                linkService.disableRole(enabled.getId(), RoleLifecycleRequest.builder().role("CUSTOMER").build()));
        assertTrue(custBlock.getMessage().contains("5000"));

        // 7. Disabling Supplier role blocked because Party has 3000 payable
        IllegalStateException partyBlock = assertThrows(IllegalStateException.class, () ->
                linkService.disableRole(enabled.getId(), RoleLifecycleRequest.builder().role("PARTY").build()));
        assertTrue(partyBlock.getMessage().contains("3000"));

        // 8. Settle Supplier payable with PartyPayment of 3000 -> disable Supplier role succeeds
        partyPaymentRepo.save(PartyPayment.builder()
                .firmId(FIRM_1)
                .partyId(party.getId())
                .amount(new BigDecimal("3000.00"))
                .paymentDate(LocalDate.now())
                .paymentMode("BANK_TRANSFER")
                .build());

        BusinessPartyLinkResponse paused = linkService.disableRole(enabled.getId(),
                RoleLifecycleRequest.builder().role("PARTY").reason("Stopped purchasing").build());
        assertEquals("PARTY_DISABLED", paused.getStatus());
        assertFalse(paused.getPartyRoleActive());
        assertTrue(partyRepo.findByIdAndFirmId(party.getId(), FIRM_1).isPresent());
        assertTrue(invoiceRepo.findById(inv.getId()).isPresent());

        // 9. Re-enable Supplier role reuses SAME Party record (no duplicate Party created)
        long countBefore = partyRepo.findByFirmIdOrderByNameAsc(FIRM_1).size();
        BusinessPartyLinkResponse reenabled = linkService.reenableRole(enabled.getId(),
                RoleLifecycleRequest.builder().role("PARTY").build());
        long countAfter = partyRepo.findByFirmIdOrderByNameAsc(FIRM_1).size();
        assertEquals(countBefore, countAfter);
        assertEquals(party.getId(), reenabled.getPartyId());
        assertEquals("ACTIVE", reenabled.getStatus());

        // 10. Unlink deletes only link, preserves Customer, Party, Invoice, Payment
        linkService.unlink(enabled.getId());
        assertNull(linkService.getLinkedPartyForCustomer(cust.getId()));
        assertTrue(customerRepo.findByIdAndFirmId(cust.getId(), FIRM_1).isPresent());
        assertTrue(partyRepo.findByIdAndFirmId(party.getId(), FIRM_1).isPresent());
        assertTrue(invoiceRepo.findById(inv.getId()).isPresent());

        // 11. Now previewEnablePartyRole sees existing unlinked Party by GSTIN -> REUSE_EXISTING!
        RoleMatchPreviewResponse reusePreview = linkService.previewEnablePartyRole(cust.getId(), null, null);
        assertEquals("REUSE_EXISTING", reusePreview.getResolution());
        assertEquals(party.getId(), reusePreview.getExistingEntityId());
        assertEquals("GSTIN", reusePreview.getMatchedBy());

        // 12. Cross-firm isolation: Firm 2 cannot see or link Firm 1 records
        TenantContext.setCurrentFirmId(FIRM_2);
        assertThrows(IllegalArgumentException.class, () ->
                linkService.previewEnablePartyRole(cust.getId(), null, null));
    }

    @Test
    @DisplayName("End-to-end DB: Supplier with Pending Purchase Order cannot disable Supplier Role")
    void testSupplierWithPendingPoCannotDisableRole() {
        Party supplier = partyRepo.save(Party.builder()
                .firmId(FIRM_1)
                .name("Omega Wholesalers")
                .phone("9000011111")
                .gstin("27OMEGA1234A1Z1")
                .openingBalance(BigDecimal.ZERO)
                .build());

        BusinessPartyLinkResponse linked = linkService.enableCustomerRole(EnableCustomerRoleRequest.builder()
                .partyId(supplier.getId())
                .build());

        poRepo.save(PurchaseOrder.builder()
                .firmId(FIRM_1)
                .party(supplier)
                .poNumber("PO-INT-9001")
                .poDate(LocalDate.now())
                .status(PurchaseOrderStatus.ISSUED)
                .totalAmount(new BigDecimal("12000.00"))
                .build());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                linkService.disableRole(linked.getId(), RoleLifecycleRequest.builder().role("PARTY").build()));
        assertTrue(ex.getMessage().contains("Purchase Order"));
    }
}