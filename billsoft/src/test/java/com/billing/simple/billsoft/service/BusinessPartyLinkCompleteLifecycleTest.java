package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dtos.*;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repositories.BusinessIdentityAliasRepository;
import com.billing.simple.billsoft.repositories.BusinessPartyLinkEventRepository;
import com.billing.simple.billsoft.repositories.BusinessPartyLinkRepository;
import com.billing.simple.billsoft.repositories.PartyRepository;
import com.billing.simple.billsoft.security.AdminAuthorizationException;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class BusinessPartyLinkCompleteLifecycleTest {

    private static final Long FIRM_1 = 1001L;
    private static final Long FIRM_2 = 1002L;

    @Autowired
    private BusinessPartyLinkService linkService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private PartyService partyService;

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private PartyRepository partyRepo;

    @Autowired
    private BusinessPartyLinkRepository linkRepo;

    @Autowired
    private BusinessIdentityAliasRepository aliasRepo;

    @Autowired
    private BusinessPartyLinkEventRepository eventRepo;

    @Autowired
    private AppConfigRepository appConfigRepo;

    @Autowired
    private CustomerBalanceService customerBalanceService;

    @Autowired
    private StatementService statementService;

    @Autowired
    private com.billing.simple.billsoft.repo.InvoiceRepository invoiceRepo;

    @Autowired
    private com.billing.simple.billsoft.repo.InvoicePaymentRepository invoicePaymentRepo;

    @Autowired
    private com.billing.simple.billsoft.repositories.PurchaseOrderRepository purchaseOrderRepo;

    @Autowired
    private com.billing.simple.billsoft.repositories.PartyPaymentRepository partyPaymentRepo;

    @Autowired
    private com.billing.simple.billsoft.repo.SalesReturnRepository salesReturnRepo;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentFirmId(FIRM_1);
        // Configure admin pin for firm 1
        appConfigRepo.save(new AppConfig("ADMIN_PIN_" + FIRM_1, "9876"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Canonical rename updates both Customer and Party, retains old name as alias, logs event")
    void testCanonicalRenameUpdatesBothAndRetainsAlias() {
        // 1. Create Customer and Party
        Customer cust = customerRepo.save(Customer.builder()
                .firmId(FIRM_1)
                .name("Alpha Enterprises")
                .phone("9800011111")
                .gstin("27AAAAA0001A1Z1")
                .build());

        Party party = partyRepo.save(Party.builder()
                .firmId(FIRM_1)
                .name("Alpha Enterprises")
                .phone("9800011111")
                .gstin("27AAAAA0001A1Z1")
                .build());

        // 2. Link them
        BusinessPartyLinkResponse linkRes = linkService.link(BusinessPartyLinkRequest.builder()
                .customerId(cust.getId())
                .partyId(party.getId())
                .linkedBy("Admin")
                .build());

        Long linkId = linkRes.getId();
        assertNotNull(linkId);

        // 3. Update canonical name
        BusinessPartyLinkResponse updated = linkService.updateCanonicalName(linkId, CanonicalNameUpdateRequest.builder()
                .canonicalName("Alpha Global Corp")
                .reason("Rebranding")
                .updatedBy("Legal Admin")
                .build());

        assertEquals("Alpha Global Corp", updated.getCanonicalName());

        // Verify both database entities were updated
        Customer reloadedCust = customerRepo.findById(cust.getId()).orElseThrow();
        Party reloadedParty = partyRepo.findById(party.getId()).orElseThrow();
        assertEquals("Alpha Global Corp", reloadedCust.getName());
        assertEquals("Alpha Global Corp", reloadedParty.getName());

        // Verify previous name was recorded as searchable alias
        List<AliasDto> aliases = linkService.getAliases(linkId);
        assertFalse(aliases.isEmpty());
        assertTrue(aliases.stream().anyMatch(a -> "Alpha Enterprises".equalsIgnoreCase(a.getAliasValue())));

        // Verify audit event
        List<AuditTrailEventDto> events = linkService.getAuditEvents(linkId);
        assertTrue(events.stream().anyMatch(e -> "CANONICAL_NAME_CHANGED".equals(e.getAction())));
    }

    @Test
    @DisplayName("Independent name edit is blocked when Customer is linked to a Supplier")
    void testIndependentNameEditBlockedForLinkedCustomer() {
        Customer cust = customerRepo.save(Customer.builder()
                .firmId(FIRM_1)
                .name("Beta Logistics")
                .phone("9800022222")
                .build());

        Party party = partyRepo.save(Party.builder()
                .firmId(FIRM_1)
                .name("Beta Logistics")
                .phone("9800022222")
                .build());

        linkService.link(BusinessPartyLinkRequest.builder()
                .customerId(cust.getId())
                .partyId(party.getId())
                .build());

        // Attempting to update customer name independently via CustomerService should be rejected
        CustomerRequest updateReq = new CustomerRequest();
        updateReq.setName("Beta Rogue Name");
        updateReq.setPhone("9800022222");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                customerService.update(cust.getId(), updateReq));

        assertTrue(ex.getMessage().contains("Canonical Name Management"));
    }

    @Test
    @DisplayName("Independent name edit is blocked when Supplier is linked to a Customer")
    void testIndependentNameEditBlockedForLinkedSupplier() {
        Customer cust = customerRepo.save(Customer.builder()
                .firmId(FIRM_1)
                .name("Gamma Supplies")
                .phone("9800033333")
                .build());

        Party party = partyRepo.save(Party.builder()
                .firmId(FIRM_1)
                .name("Gamma Supplies")
                .phone("9800033333")
                .build());

        linkService.link(BusinessPartyLinkRequest.builder()
                .customerId(cust.getId())
                .partyId(party.getId())
                .build());

        Party updateReq = Party.builder()
                .firmId(FIRM_1)
                .name("Gamma Rogue Name")
                .phone("9800033333")
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                partyService.updateParty(party.getId(), updateReq));

        assertTrue(ex.getMessage().contains("Canonical Name Management"));
    }

    @Test
    @DisplayName("Controlled soft-unlink requires Admin PIN and preserves records & historical link")
    void testControlledSoftUnlinkWorkflow() {
        Customer cust = customerRepo.save(Customer.builder()
                .firmId(FIRM_1)
                .name("Delta Trading")
                .phone("9800044444")
                .build());

        Party party = partyRepo.save(Party.builder()
                .firmId(FIRM_1)
                .name("Delta Trading")
                .phone("9800044444")
                .build());

        BusinessPartyLinkResponse linkRes = linkService.link(BusinessPartyLinkRequest.builder()
                .customerId(cust.getId())
                .partyId(party.getId())
                .build());

        Long linkId = linkRes.getId();

        // 1. Invalid PIN rejected with HTTP 403 AdminAuthorizationException
        assertThrows(AdminAuthorizationException.class, () ->
                linkService.softUnlink(linkId, UnlinkRequest.builder()
                        .adminPin("0000") // Wrong PIN
                        .reason("Testing security")
                        .build()));

        // 2. Valid PIN succeeds with soft unlinking
        BusinessPartyLinkResponse unlinkedRes = linkService.softUnlink(linkId, UnlinkRequest.builder()
                .adminPin("9876")
                .reason("Business separation")
                .unlinkedBy("Chief Admin")
                .build());

        assertEquals("UNLINKED", unlinkedRes.getStatus());
        assertNotNull(unlinkedRes.getUnlinkedAt());

        // 3. Both Customer and Party records remain in database intact
        assertTrue(customerRepo.findById(cust.getId()).isPresent());
        assertTrue(partyRepo.findById(party.getId()).isPresent());

        // 4. Link record is preserved in database with activeCustomerId set to null
        BusinessPartyLink preservedLink = linkRepo.findById(linkId).orElseThrow();
        assertEquals("UNLINKED", preservedLink.getStatus());
        assertNull(preservedLink.getActiveCustomerId());
        assertNull(preservedLink.getActivePartyId());

        // 5. Customer and Party are now free to be re-linked in a version 2 relationship
        BusinessPartyLinkResponse reLinked = linkService.link(BusinessPartyLinkRequest.builder()
                .customerId(cust.getId())
                .partyId(party.getId())
                .notes("Re-linked version 2")
                .build());

        assertEquals(2, reLinked.getRelationshipVersion());
        assertEquals("ACTIVE", reLinked.getStatus());
        assertNotEquals(linkId, reLinked.getId()); // New row created, old row preserved!
    }

    @Test
    @DisplayName("Unified counterparty search deduplicates linked businesses and surfaces alias matches")
    void testUnifiedCounterpartySearch() {
        Customer cust = customerRepo.save(Customer.builder()
                .firmId(FIRM_1)
                .name("Epsilon Corp")
                .phone("9800055555")
                .build());

        Party party = partyRepo.save(Party.builder()
                .firmId(FIRM_1)
                .name("Epsilon Corp")
                .phone("9800055555")
                .build());

        BusinessPartyLinkResponse link = linkService.link(BusinessPartyLinkRequest.builder()
                .customerId(cust.getId())
                .partyId(party.getId())
                .build());

        // Add alias "Old Epsilon Brand"
        linkService.addAlias(link.getId(), CreateAliasRequest.builder()
                .aliasValue("Old Epsilon Brand")
                .build());

        // Search for "Epsilon"
        List<UnifiedCounterpartySearchResult> results = linkService.searchUnified("CUSTOMER", "Epsilon", 10);
        // Must appear ONCE as LINKED_BUSINESS
        long count = results.stream().filter(r -> "LINKED_BUSINESS".equals(r.getResultType()) && cust.getId().equals(r.getCustomerId())).count();
        assertEquals(1, count, "Linked business should appear exactly once in search results");

        // Search by alias "Old Epsilon"
        List<UnifiedCounterpartySearchResult> aliasResults = linkService.searchUnified("CUSTOMER", "Old Epsilon", 10);
        assertFalse(aliasResults.isEmpty());
        assertEquals("LINKED_BUSINESS", aliasResults.get(0).getResultType());
        assertEquals("Old Epsilon Brand", aliasResults.get(0).getMatchedAlias());
    }

    @Test
    @DisplayName("Multi-firm isolation: Search, Linking and Renaming cannot cross firm boundaries")
    void testMultiFirmIsolation() {
        // Firm 1 record
        Customer cust1 = customerRepo.save(Customer.builder()
                .firmId(FIRM_1)
                .name("Zeta Firm1 Customer")
                .phone("9900011111")
                .build());

        // Switch to Firm 2
        TenantContext.setCurrentFirmId(FIRM_2);

        // Searching in Firm 2 should not find Firm 1 customer
        List<UnifiedCounterpartySearchResult> f2Results = linkService.searchUnified("CUSTOMER", "Zeta", 10);
        assertTrue(f2Results.isEmpty(), "Firm 2 search must not disclose Firm 1 records");

        // Attempting to link Firm 1 customer from Firm 2 context should fail
        Party party2 = partyRepo.save(Party.builder()
                .firmId(FIRM_2)
                .name("Zeta Firm2 Party")
                .phone("9900022222")
                .build());

        assertThrows(IllegalArgumentException.class, () ->
                linkService.link(BusinessPartyLinkRequest.builder()
                        .customerId(cust1.getId())
                        .partyId(party2.getId())
                        .build()));
    }

    @Test
    @DisplayName("Critical: Complete Accounting Separation & Numerical Reconciliation Test")
    void testNumericalAccountingSeparationAndReconciliation() {
        // Step 1: Customer (opening balance in RupeeCRM customer module is derived from historical transactions/invoices)
        Customer cust = customerRepo.save(Customer.builder()
                .firmId(FIRM_1)
                .name("Alpha Enterprises")
                .phone("9911223344")
                .build());

        // Step 2: Party Opening Balance = 20,000 (PAYABLE)
        Party party = partyRepo.save(Party.builder()
                .firmId(FIRM_1)
                .name("Alpha Enterprises")
                .phone("9911223344")
                .openingBalance(BigDecimal.valueOf(20000.00))
                .openingBalanceType("PAYABLE")
                .build());

        // Step 3: Transactions before linking
        // Customer Invoice: 35,000
        Invoice inv1 = invoiceRepo.save(Invoice.builder()
                .firmId(FIRM_1)
                .customer(cust)
                .invoiceNumber("INV-101")
                .invoiceDate(LocalDateTime.now())
                .status(InvoiceStatus.UNPAID)
                .totalAmount(BigDecimal.valueOf(35000.00))
                .build());

        // Customer Receipt / Payment: 5,000
        invoicePaymentRepo.save(InvoicePayment.builder()
                .firmId(FIRM_1)
                .customerId(cust.getId())
                .invoiceId(inv1.getId())
                .amount(BigDecimal.valueOf(5000.00))
                .paymentDate(LocalDate.now())
                .build());

        // Party Purchase Order: 40,000
        PurchaseOrder po1 = PurchaseOrder.builder()
                .firmId(FIRM_1)
                .party(party)
                .partyName(party.getName())
                .poNumber("PO-201")
                .poDate(LocalDate.now())
                .status(PurchaseOrderStatus.ISSUED)
                .build();
        PurchaseOrderItem item1 = PurchaseOrderItem.builder()
                .purchaseOrder(po1)
                .productName("Stock Item A")
                .quantity(BigDecimal.ONE)
                .unitPrice(BigDecimal.valueOf(40000.00))
                .taxableAmount(BigDecimal.valueOf(40000.00))
                .totalAmount(BigDecimal.valueOf(40000.00))
                .build();
        po1.setItems(new java.util.ArrayList<>(List.of(item1)));
        po1 = purchaseOrderRepo.save(po1);

        // Supplier Payment: 10,000
        partyPaymentRepo.save(PartyPayment.builder()
                .firmId(FIRM_1)
                .partyId(party.getId())
                .purchaseOrderId(po1.getId())
                .amount(BigDecimal.valueOf(10000.00))
                .paymentDate(LocalDate.now())
                .build());

        invoiceRepo.flush();
        invoicePaymentRepo.flush();
        purchaseOrderRepo.flush();
        partyPaymentRepo.flush();

        // Verify Pre-Link Calculations:
        // Customer closing balance = 35,000 (invoice) - 5,000 (payment) = 30,000
        CustomerBalanceSummary custPre = customerBalanceService.getCustomerBalance(FIRM_1, cust.getId());
        assertEquals(0, BigDecimal.valueOf(30000.00).compareTo(custPre.getClosingBalance()));

        // Party financial summary = 20,000 (opening) + 40,000 (PO) - 10,000 (paid) = 50,000
        PartyFinancialSummary partyPre = partyService.getFinancialSummary(party.getId(), FIRM_1);
        System.out.println("DEBUG PARTY PRE: opening=" + partyPre.getOpeningBalance() + ", purchases=" + partyPre.getTotalPurchases() + ", paid=" + partyPre.getTotalPaid() + ", net=" + partyPre.getNetBalance());
        assertEquals(0, BigDecimal.valueOf(50000.00).compareTo(partyPre.getNetBalance()));

        // Step 4: Link Customer and Party
        BusinessPartyLinkResponse link = linkService.link(BusinessPartyLinkRequest.builder()
                .customerId(cust.getId())
                .partyId(party.getId())
                .build());
        assertNotNull(link);

        // Verify Post-Link Calculations (Accounting Neutrality):
        CustomerBalanceSummary custPostLink = customerBalanceService.getCustomerBalance(FIRM_1, cust.getId());
        assertEquals(0, BigDecimal.valueOf(30000.00).compareTo(custPostLink.getClosingBalance()), "Customer balance must be untouched by linking");

        PartyFinancialSummary partyPostLink = partyService.getFinancialSummary(party.getId(), FIRM_1);
        assertEquals(0, BigDecimal.valueOf(50000.00).compareTo(partyPostLink.getNetBalance()), "Party liability must be untouched by linking");

        // Step 5: Post new transactions while linked
        // Another Customer Invoice: 10,000
        Invoice inv2 = invoiceRepo.save(Invoice.builder()
                .firmId(FIRM_1)
                .customer(cust)
                .invoiceNumber("INV-102")
                .invoiceDate(LocalDateTime.now())
                .status(InvoiceStatus.UNPAID)
                .totalAmount(BigDecimal.valueOf(10000.00))
                .build());

        // Another Supplier Payment: 5,000
        partyPaymentRepo.save(PartyPayment.builder()
                .firmId(FIRM_1)
                .partyId(party.getId())
                .purchaseOrderId(po1.getId())
                .amount(BigDecimal.valueOf(5000.00))
                .paymentDate(LocalDate.now())
                .build());

        invoiceRepo.flush();
        partyPaymentRepo.flush();

        // Expected:
        // Customer: 30,000 + 10,000 = 40,000
        // Party: 50,000 - 5,000 = 45,000
        CustomerBalanceSummary custMid = customerBalanceService.getCustomerBalance(FIRM_1, cust.getId());
        assertEquals(0, BigDecimal.valueOf(40000.00).compareTo(custMid.getClosingBalance()));

        PartyFinancialSummary partyMid = partyService.getFinancialSummary(party.getId(), FIRM_1);
        assertEquals(0, BigDecimal.valueOf(45000.00).compareTo(partyMid.getNetBalance()));

        // Step 6: Statements Verification
        CustomerStatementResponse custStmt = statementService.getCustomerStatement(FIRM_1, cust.getId(), null, null);
        assertEquals(40000.00, custStmt.getClosingBalance(), 0.01);
        // Ensure no POs in customer statement
        assertTrue(custStmt.getEntries().stream().noneMatch(e -> "PO".equalsIgnoreCase(e.getType()) || (e.getRef() != null && e.getRef().startsWith("PO-"))));

        PartyStatementResponse partyStmt = statementService.getPartyStatement(FIRM_1, party.getId(), null, null);
        assertEquals(45000.00, partyStmt.getClosingBalance(), 0.01);
        // Ensure no Invoices in party statement
        assertTrue(partyStmt.getEntries().stream().noneMatch(e -> "INVOICE".equalsIgnoreCase(e.getType()) || (e.getRef() != null && e.getRef().startsWith("INV-"))));

        // Step 7: Controlled Unlinking with Admin PIN
        linkService.softUnlink(link.getId(), UnlinkRequest.builder()
                .adminPin("9876")
                .reason("Test Unlink")
                .unlinkedBy("Admin")
                .build());

        // Assert balances are STILL 40,000 and 45,000
        CustomerBalanceSummary custPostUnlink = customerBalanceService.getCustomerBalance(FIRM_1, cust.getId());
        assertEquals(0, BigDecimal.valueOf(40000.00).compareTo(custPostUnlink.getClosingBalance()));

        PartyFinancialSummary partyPostUnlink = partyService.getFinancialSummary(party.getId(), FIRM_1);
        assertEquals(0, BigDecimal.valueOf(45000.00).compareTo(partyPostUnlink.getNetBalance()));

        // Step 8: Re-link Customer and Party (Creates Version 2)
        BusinessPartyLinkResponse relink = linkService.link(BusinessPartyLinkRequest.builder()
                .customerId(cust.getId())
                .partyId(party.getId())
                .build());
        assertEquals(2, relink.getRelationshipVersion());

        // Balances must remain identical with zero transaction duplication
        CustomerBalanceSummary custRelink = customerBalanceService.getCustomerBalance(FIRM_1, cust.getId());
        assertEquals(0, BigDecimal.valueOf(40000.00).compareTo(custRelink.getClosingBalance()));

        PartyFinancialSummary partyRelink = partyService.getFinancialSummary(party.getId(), FIRM_1);
        assertEquals(0, BigDecimal.valueOf(45000.00).compareTo(partyRelink.getNetBalance()));
    }

    @Test
    @DisplayName("Active link prevents deletion of Customer and Party; soft-unlinked record allows clean deletion")
    void testActiveLinkPreventsDeletion_UnlinkedAllowsDeletion() {
        Customer cust = customerRepo.save(Customer.builder()
                .firmId(FIRM_1)
                .name("Zeta Supplies")
                .phone("9911991199")
                .build());

        Party party = partyRepo.save(Party.builder()
                .firmId(FIRM_1)
                .name("Zeta Supplies")
                .phone("9911991199")
                .build());

        BusinessPartyLinkResponse link = linkService.link(BusinessPartyLinkRequest.builder()
                .customerId(cust.getId())
                .partyId(party.getId())
                .build());
        assertNotNull(link);

        // 1. Actively linked: deletion must be blocked
        IllegalStateException custEx = assertThrows(IllegalStateException.class, () -> customerService.delete(cust.getId()));
        assertTrue(custEx.getMessage().contains("is linked to a Supplier. Please unlink first."));

        IllegalStateException partyEx = assertThrows(IllegalStateException.class, () -> partyService.deleteParty(party.getId(), FIRM_1));
        assertTrue(partyEx.getMessage().contains("is linked to a Customer. Please unlink first."));

        // 2. Soft-unlink with Admin PIN
        linkService.softUnlink(link.getId(), UnlinkRequest.builder()
                .adminPin("9876")
                .reason("Dissolving partnership")
                .build());

        // 3. Unlinked: Deletion of standalone records must now succeed!
        boolean custDeleted = customerService.delete(cust.getId());
        assertTrue(custDeleted, "Unlinked customer must be deletable");
        assertTrue(customerRepo.findById(cust.getId()).isEmpty());

        assertDoesNotThrow(() -> partyService.deleteParty(party.getId(), FIRM_1), "Unlinked supplier must be deletable");
        assertTrue(partyRepo.findById(party.getId()).isEmpty());
    }

    @Test
    @DisplayName("Creation & Linking Lifecycle: Creating a Party and linking to an existing Customer persists authoritative relationship")
    void testPartyCreationAndDirectLinkingLifecycle() {
        // Given an existing customer
        Customer cust = customerRepo.save(Customer.builder()
                .firmId(FIRM_1)
                .name("Apex Tools & Hardware")
                .phone("9887766554")
                .gstin("27AAACA9999A1Z9")
                .build());

        // When a new party is created
        Party party = partyRepo.save(Party.builder()
                .firmId(FIRM_1)
                .name("Apex Tools & Hardware")
                .phone("9887766554")
                .gstin("27AAACA9999A1Z9")
                .openingBalance(BigDecimal.valueOf(5000.00))
                .openingBalanceType("PAYABLE")
                .build());

        // And linked
        BusinessPartyLinkResponse linkResp = linkService.link(BusinessPartyLinkRequest.builder()
                .customerId(cust.getId())
                .partyId(party.getId())
                .notes("Linked upon party creation")
                .build());

        assertNotNull(linkResp);
        assertEquals("ACTIVE", linkResp.getStatus());
        assertEquals(cust.getId(), linkResp.getCustomerId());
        assertEquals(party.getId(), linkResp.getPartyId());

        // Then verify getFirmLinksMap returns the authoritative active link
        Map<String, Object> map = linkService.getFirmLinksMap(FIRM_1);
        @SuppressWarnings("unchecked")
        Map<Long, Map<String, Object>> partyToCust = (Map<Long, Map<String, Object>>) map.get("partyToCustomer");
        assertNotNull(partyToCust);
        assertTrue(partyToCust.containsKey(party.getId()));
        Map<String, Object> cInfo = partyToCust.get(party.getId());
        assertEquals(cust.getId(), cInfo.get("customerId"));
        assertEquals("Apex Tools & Hardware", cInfo.get("customerName"));
        assertEquals("ACTIVE", cInfo.get("status"));
    }

    @Test
    @DisplayName("Creation & Linking Lifecycle: Attempting duplicate link to already-linked counterpart is blocked")
    void testPartyCreationDuplicateLinkBlocked() {
        Customer cust = customerRepo.save(Customer.builder()
                .firmId(FIRM_1)
                .name("Beta Supplies")
                .phone("9881122334")
                .build());

        Party party1 = partyRepo.save(Party.builder()
                .firmId(FIRM_1)
                .name("Beta Supplies")
                .phone("9881122334")
                .build());

        linkService.link(BusinessPartyLinkRequest.builder()
                .customerId(cust.getId())
                .partyId(party1.getId())
                .build());

        // Creating party 2 and attempting to link to same customer must throw IllegalStateException
        Party party2 = partyRepo.save(Party.builder()
                .firmId(FIRM_1)
                .name("Beta Supplies Duplicate")
                .phone("9881122334")
                .build());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                linkService.link(BusinessPartyLinkRequest.builder()
                        .customerId(cust.getId())
                        .partyId(party2.getId())
                        .build())
        );
        assertTrue(ex.getMessage().contains("already linked"));
    }

    @Test
    @DisplayName("Mandatory Phase 2: Numerical Reconciliation and Accounting Neutrality (Customer ₹15,000 / Supplier ₹35,000)")
    void testMandatoryPhase2NumericalReconciliation() {
        Customer cust = customerRepo.save(Customer.builder()
                .firmId(FIRM_1)
                .name("Reconciliation Client")
                .phone("9811223344")
                .build());

        Invoice inv1 = invoiceRepo.save(Invoice.builder()
                .firmId(FIRM_1)
                .customer(cust)
                .invoiceNumber("INV-REC-1")
                .invoiceDate(LocalDateTime.now())
                .status(InvoiceStatus.UNPAID)
                .totalAmount(BigDecimal.valueOf(20000.00))
                .build());

        Invoice inv2 = invoiceRepo.save(Invoice.builder()
                .firmId(FIRM_1)
                .customer(cust)
                .invoiceNumber("INV-REC-2")
                .invoiceDate(LocalDateTime.now())
                .status(InvoiceStatus.UNPAID)
                .totalAmount(BigDecimal.valueOf(10000.00))
                .build());

        invoicePaymentRepo.save(InvoicePayment.builder()
                .firmId(FIRM_1)
                .customerId(cust.getId())
                .invoiceId(inv1.getId())
                .amount(BigDecimal.valueOf(12000.00))
                .paymentDate(LocalDate.now())
                .build());

        salesReturnRepo.save(SalesReturn.builder()
                .firmId(FIRM_1)
                .customer(cust)
                .invoice(inv1)
                .returnNumber("RET-REC-1")
                .returnDate(LocalDate.now())
                .totalRefundAmount(BigDecimal.valueOf(3000.00))
                .build());

        Party party = partyRepo.save(Party.builder()
                .firmId(FIRM_1)
                .name("Reconciliation Supplier")
                .phone("9811223344")
                .openingBalance(BigDecimal.valueOf(10000.00))
                .openingBalanceType("PAYABLE")
                .build());

        PurchaseOrder po1 = PurchaseOrder.builder()
                .firmId(FIRM_1)
                .party(party)
                .partyName(party.getName())
                .poNumber("PO-REC-1")
                .poDate(LocalDate.now())
                .status(PurchaseOrderStatus.ISSUED)
                .build();
        PurchaseOrderItem item1 = PurchaseOrderItem.builder()
                .purchaseOrder(po1)
                .productName("Heavy Machinery Component A")
                .quantity(BigDecimal.ONE)
                .unitPrice(BigDecimal.valueOf(30000.00))
                .taxableAmount(BigDecimal.valueOf(30000.00))
                .totalAmount(BigDecimal.valueOf(30000.00))
                .build();
        po1.setItems(new java.util.ArrayList<>(List.of(item1)));
        purchaseOrderRepo.save(po1);

        PurchaseOrder po2 = PurchaseOrder.builder()
                .firmId(FIRM_1)
                .party(party)
                .partyName(party.getName())
                .poNumber("PO-REC-2")
                .poDate(LocalDate.now())
                .status(PurchaseOrderStatus.ISSUED)
                .build();
        PurchaseOrderItem item2 = PurchaseOrderItem.builder()
                .purchaseOrder(po2)
                .productName("Heavy Machinery Component B")
                .quantity(BigDecimal.ONE)
                .unitPrice(BigDecimal.valueOf(20000.00))
                .taxableAmount(BigDecimal.valueOf(20000.00))
                .totalAmount(BigDecimal.valueOf(20000.00))
                .build();
        po2.setItems(new java.util.ArrayList<>(List.of(item2)));
        purchaseOrderRepo.save(po2);

        partyPaymentRepo.save(PartyPayment.builder()
                .firmId(FIRM_1)
                .partyId(party.getId())
                .purchaseOrderId(po1.getId())
                .amount(BigDecimal.valueOf(25000.00))
                .paymentDate(LocalDate.now())
                .build());

        invoiceRepo.flush();
        invoicePaymentRepo.flush();
        salesReturnRepo.flush();
        purchaseOrderRepo.flush();
        partyPaymentRepo.flush();

        // 1. Verify PRE-LINK calculations
        CustomerBalanceSummary custPre = customerBalanceService.getCustomerBalance(FIRM_1, cust.getId());
        assertEquals(0, BigDecimal.valueOf(15000.00).compareTo(custPre.getClosingBalance()));

        PartyFinancialSummary partyPre = partyService.getFinancialSummary(party.getId(), FIRM_1);
        assertEquals(0, BigDecimal.valueOf(35000.00).compareTo(partyPre.getNetBalance()));

        CustomerStatementResponse custStmtPre = statementService.getCustomerStatement(FIRM_1, cust.getId(), null, null);
        assertEquals(15000.00, custStmtPre.getClosingBalance(), 0.01);

        PartyStatementResponse partyStmtPre = statementService.getPartyStatement(FIRM_1, party.getId(), null, null);
        assertEquals(35000.00, partyStmtPre.getClosingBalance(), 0.01);

        // 2. LINK Customer and Supplier
        BusinessPartyLinkResponse link = linkService.link(BusinessPartyLinkRequest.builder()
                .customerId(cust.getId())
                .partyId(party.getId())
                .notes("Phase 2 Numerical Reconciliation Link")
                .build());
        assertNotNull(link);

        // 3. Verify POST-LINK calculations (Complete Neutrality)
        CustomerBalanceSummary custPost = customerBalanceService.getCustomerBalance(FIRM_1, cust.getId());
        assertEquals(0, BigDecimal.valueOf(15000.00).compareTo(custPost.getClosingBalance()), "Customer balance must remain 15,000 post-link");

        PartyFinancialSummary partyPost = partyService.getFinancialSummary(party.getId(), FIRM_1);
        assertEquals(0, BigDecimal.valueOf(35000.00).compareTo(partyPost.getNetBalance()), "Supplier balance must remain 35,000 post-link");

        CustomerStatementResponse custStmtPost = statementService.getCustomerStatement(FIRM_1, cust.getId(), null, null);
        assertEquals(15000.00, custStmtPost.getClosingBalance(), 0.01);

        PartyStatementResponse partyStmtPost = statementService.getPartyStatement(FIRM_1, party.getId(), null, null);
        assertEquals(35000.00, partyStmtPost.getClosingBalance(), 0.01);

        // 4. UNLINK and verify balances are STILL 15,000 and 35,000
        linkService.softUnlink(link.getId(), UnlinkRequest.builder()
                .adminPin("9876")
                .reason("Reconciliation test complete")
                .unlinkedBy("AuditAdmin")
                .build());

        CustomerBalanceSummary custUnlinked = customerBalanceService.getCustomerBalance(FIRM_1, cust.getId());
        assertEquals(0, BigDecimal.valueOf(15000.00).compareTo(custUnlinked.getClosingBalance()), "Customer balance must remain 15,000 post-unlink");

        PartyFinancialSummary partyUnlinked = partyService.getFinancialSummary(party.getId(), FIRM_1);
        assertEquals(0, BigDecimal.valueOf(35000.00).compareTo(partyUnlinked.getNetBalance()), "Supplier balance must remain 35,000 post-unlink");
    }
}
