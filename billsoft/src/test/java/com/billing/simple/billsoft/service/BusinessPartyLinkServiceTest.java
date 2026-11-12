package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dtos.*;
import com.billing.simple.billsoft.entities.BusinessPartyLink;
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.Party;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repositories.BusinessPartyLinkRepository;
import com.billing.simple.billsoft.repositories.PartyPaymentRepository;
import com.billing.simple.billsoft.repositories.PartyRepository;
import com.billing.simple.billsoft.repositories.PurchaseOrderRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;
import com.billing.simple.billsoft.service.impl.BusinessPartyLinkServiceImpl;
import com.billing.simple.billsoft.service.impl.PartyServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BusinessPartyLinkServiceTest {

    private static final Long FIRM_A = 1L;
    private static final Long FIRM_B = 2L;

    @Mock
    private BusinessPartyLinkRepository linkRepo;

    @Mock
    private CustomerRepository customerRepo;

    @Mock
    private PartyRepository partyRepo;

    @Mock
    private CustomerBalanceService customerBalanceService;

    @Mock
    private PartyService partyService;

    @Mock
    private StatementService statementService;

    @Mock
    private PartyPaymentRepository partyPaymentRepo;

    @Mock
    private com.billing.simple.billsoft.repositories.BusinessIdentityAliasRepository aliasRepo;

    @Mock
    private com.billing.simple.billsoft.repositories.BusinessPartyLinkEventRepository eventRepo;

    @Mock
    private com.billing.simple.billsoft.repo.AppConfigRepository appConfigRepo;

    @InjectMocks
    private BusinessPartyLinkServiceImpl linkService;

    private Customer customerA;
    private Party partyA;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentFirmId(FIRM_A);

        customerA = new Customer();
        customerA.setId(100L);
        customerA.setName("Acme Retailer");
        customerA.setFirmId(FIRM_A);
        customerA.setPhone("9876543210");
        customerA.setGstin("27AAAAA0000A1Z5");

        partyA = new Party();
        partyA.setId(200L);
        partyA.setName("Acme Wholesaler");
        partyA.setFirmId(FIRM_A);
        partyA.setPhone("9876543210");
        partyA.setGstin("27AAAAA0000A1Z5");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Successfully links valid Customer and Party in the same firm")
    void testLinkSuccess() {
        when(customerRepo.findByIdAndFirmId(100L, FIRM_A)).thenReturn(Optional.of(customerA));
        when(partyRepo.findByIdAndFirmId(200L, FIRM_A)).thenReturn(Optional.of(partyA));
        when(linkRepo.existsByFirmIdAndCustomerId(FIRM_A, 100L)).thenReturn(false);
        when(linkRepo.existsByFirmIdAndPartyId(FIRM_A, 200L)).thenReturn(false);

        BusinessPartyLink savedLink = BusinessPartyLink.builder()
                .id(1L)
                .firmId(FIRM_A)
                .customerId(100L)
                .partyId(200L)
                .linkedAt(LocalDateTime.now())
                .notes("Dual entity link")
                .build();
        when(linkRepo.save(any(BusinessPartyLink.class))).thenReturn(savedLink);

        BusinessPartyLinkRequest req = new BusinessPartyLinkRequest();
        req.setCustomerId(100L);
        req.setPartyId(200L);
        req.setNotes("Dual entity link");

        BusinessPartyLinkResponse response = linkService.link(req);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals(100L, response.getCustomerId());
        assertEquals(200L, response.getPartyId());
        assertEquals("Acme Retailer", response.getCustomerName());
        assertEquals("Acme Retailer", response.getPartyName());
        verify(linkRepo, times(1)).save(any(BusinessPartyLink.class));
    }

    @Test
    @DisplayName("Rejects linking when active firm context is absent")
    void testLinkContextRejection_NoFirmContext() {
        TenantContext.clear(); // No active firm

        BusinessPartyLinkRequest req = new BusinessPartyLinkRequest();
        req.setCustomerId(100L);
        req.setPartyId(200L);

        TenantSecurityException ex = assertThrows(TenantSecurityException.class, () -> linkService.link(req));
        assertTrue(ex.getMessage().contains("Active firm context is required"));
        verify(linkRepo, never()).save(any());
    }

    @Test
    @DisplayName("Rejects linking when Customer and Party belong to different firms")
    void testLinkCrossFirmRejection_EntitiesFromDifferentFirms() {
        Party partyB = new Party();
        partyB.setId(201L);
        partyB.setName("Other Firm Supplier");
        partyB.setFirmId(FIRM_B); // Different firm

        when(customerRepo.findByIdAndFirmId(100L, FIRM_A)).thenReturn(Optional.of(customerA));
        when(partyRepo.findByIdAndFirmId(201L, FIRM_A)).thenReturn(Optional.of(partyB));

        BusinessPartyLinkRequest req = new BusinessPartyLinkRequest();
        req.setCustomerId(100L);
        req.setPartyId(201L);

        TenantSecurityException ex = assertThrows(TenantSecurityException.class, () -> linkService.link(req));
        assertTrue(ex.getMessage().contains("Cross-firm link rejected"));
        verify(linkRepo, never()).save(any());
    }

    @Test
    @DisplayName("Rejects linking when Customer belongs to another firm or does not exist")
    void testLinkCrossFirmRejection_CustomerNotInFirm() {
        when(customerRepo.findByIdAndFirmId(999L, FIRM_A)).thenReturn(Optional.empty());

        BusinessPartyLinkRequest req = new BusinessPartyLinkRequest();
        req.setCustomerId(999L);
        req.setPartyId(200L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> linkService.link(req));
        assertTrue(ex.getMessage().contains("Customer #999 not found in current firm"));
        verify(linkRepo, never()).save(any());
    }

    @Test
    @DisplayName("Rejects linking when Customer is already linked to another Party in firm")
    void testLinkRejection_CustomerAlreadyLinked() {
        when(customerRepo.findByIdAndFirmId(100L, FIRM_A)).thenReturn(Optional.of(customerA));
        when(partyRepo.findByIdAndFirmId(200L, FIRM_A)).thenReturn(Optional.of(partyA));
        when(linkRepo.existsByFirmIdAndCustomerId(FIRM_A, 100L)).thenReturn(true);

        BusinessPartyLinkRequest req = new BusinessPartyLinkRequest();
        req.setCustomerId(100L);
        req.setPartyId(200L);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> linkService.link(req));
        assertTrue(ex.getMessage().contains("Customer #100 is already linked"));
        verify(linkRepo, never()).save(any());
    }

    @Test
    @DisplayName("Rejects linking when Party is already linked to another Customer in firm")
    void testLinkRejection_PartyAlreadyLinked() {
        when(customerRepo.findByIdAndFirmId(100L, FIRM_A)).thenReturn(Optional.of(customerA));
        when(partyRepo.findByIdAndFirmId(200L, FIRM_A)).thenReturn(Optional.of(partyA));
        when(linkRepo.existsByFirmIdAndCustomerId(FIRM_A, 100L)).thenReturn(false);
        when(linkRepo.existsByFirmIdAndPartyId(FIRM_A, 200L)).thenReturn(true);

        BusinessPartyLinkRequest req = new BusinessPartyLinkRequest();
        req.setCustomerId(100L);
        req.setPartyId(200L);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> linkService.link(req));
        assertTrue(ex.getMessage().contains("Supplier #200 is already linked"));
        verify(linkRepo, never()).save(any());
    }

    @Test
    @DisplayName("Rejects unauthorized cross-firm unlink operation")
    void testUnlinkCrossFirmRejection() {
        BusinessPartyLink linkFirmB = BusinessPartyLink.builder()
                .id(99L)
                .firmId(FIRM_B) // Belongs to FIRM_B
                .customerId(100L)
                .partyId(200L)
                .build();

        when(linkRepo.findById(99L)).thenReturn(Optional.of(linkFirmB));

        TenantSecurityException ex = assertThrows(TenantSecurityException.class, () -> linkService.unlink(99L));
        assertTrue(ex.getMessage().contains("Cross-firm access prohibited"));
        verify(linkRepo, never()).delete(any());
    }

    @Test
    @DisplayName("Successfully unlinks when link belongs to current firm")
    void testUnlinkSuccess() {
        BusinessPartyLink linkFirmA = BusinessPartyLink.builder()
                .id(1L)
                .firmId(FIRM_A)
                .customerId(100L)
                .partyId(200L)
                .build();

        when(linkRepo.findById(1L)).thenReturn(Optional.of(linkFirmA));

        linkService.unlink(1L);

        verify(linkRepo, times(1)).save(linkFirmA);
        assertEquals("UNLINKED", linkFirmA.getStatus());
    }

    @Test
    @DisplayName("Prevents deletion of Customer when customer is linked to a Supplier")
    void testPreventDeletionOfLinkedCustomer() {
        CustomerRepository mockCustRepo = mock(CustomerRepository.class);
        BusinessPartyLinkRepository mockLinkRepo = mock(BusinessPartyLinkRepository.class);
        CustomerService customerService = new CustomerService(mockCustRepo, mockLinkRepo);

        when(mockLinkRepo.existsByFirmIdAndActiveCustomerId(any(), eq(100L))).thenReturn(true);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> customerService.delete(100L));
        assertTrue(ex.getMessage().contains("Cannot delete Customer #100 because it is linked to a Supplier"));
        verify(mockCustRepo, never()).deleteByIdAndFirmId(any(), any());
    }

    @Test
    @DisplayName("Prevents deletion of Party when party is linked to a Customer")
    void testPreventDeletionOfLinkedParty() {
        PartyRepository mockPartyRepo = mock(PartyRepository.class);
        PartyPaymentRepository mockPaymentRepo = mock(PartyPaymentRepository.class);
        PurchaseOrderRepository mockPoRepo = mock(PurchaseOrderRepository.class);
        BusinessPartyLinkRepository mockLinkRepo = mock(BusinessPartyLinkRepository.class);
        PartyServiceImpl partyServiceImpl = new PartyServiceImpl(mockPartyRepo, mockPaymentRepo, mockPoRepo, mockLinkRepo);

        when(mockLinkRepo.existsByFirmIdAndActivePartyId(any(), eq(200L))).thenReturn(true);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> partyServiceImpl.deleteParty(200L, FIRM_A));
        assertTrue(ex.getMessage().contains("Cannot delete Supplier #200 because it is linked to a Customer"));
        verify(mockPartyRepo, never()).delete(any());
    }

    @Test
    @DisplayName("Verifies database uniqueness constraints throw DataIntegrityViolationException on duplicate link save")
    void testDatabaseUniquenessConstraintEnforcement() {
        when(customerRepo.findByIdAndFirmId(100L, FIRM_A)).thenReturn(Optional.of(customerA));
        when(partyRepo.findByIdAndFirmId(200L, FIRM_A)).thenReturn(Optional.of(partyA));
        when(linkRepo.existsByFirmIdAndCustomerId(FIRM_A, 100L)).thenReturn(false);
        when(linkRepo.existsByFirmIdAndPartyId(FIRM_A, 200L)).thenReturn(false);

        // Simulate database unique index constraint violation (e.g. concurrent insert race condition)
        when(linkRepo.save(any(BusinessPartyLink.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry '1-100' for key 'uk_bpl_firm_customer'"));

        BusinessPartyLinkRequest req = new BusinessPartyLinkRequest();
        req.setCustomerId(100L);
        req.setPartyId(200L);

        assertThrows(DataIntegrityViolationException.class, () -> linkService.link(req));
    }

    @Test
    @DisplayName("Rejects unified statement request if no active link exists between Customer and Supplier")
    void testGetUnifiedStatement_UnlinkedPairRejected() {
        when(linkRepo.findByFirmIdAndCustomerIdAndPartyId(FIRM_A, 100L, 200L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                linkService.getUnifiedStatement(100L, 200L, LocalDate.now().minusDays(30), LocalDate.now()));

        assertTrue(ex.getMessage().contains("No active link found"));
        verify(statementService, never()).getCustomerStatement(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Rejects cross-firm lookup for customer linked party")
    void testGetLinkedPartyForCustomer_UnauthorizedCrossFirm() {
        when(customerRepo.findByIdAndFirmId(999L, FIRM_A)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                linkService.getLinkedPartyForCustomer(999L));

        assertTrue(ex.getMessage().contains("Customer #999 not found in current firm"));
    }

    @Test
    @DisplayName("Enable Customer Role: Creates new Customer and links to Supplier")
    void testEnableCustomerRole_CreatesNewCustomerAndLinks() {
        when(partyRepo.findByIdAndFirmId(200L, FIRM_A)).thenReturn(Optional.of(partyA));
        when(linkRepo.existsByFirmIdAndPartyId(FIRM_A, 200L)).thenReturn(false);
        when(customerRepo.findByFirmIdAndGstinIgnoreCase(FIRM_A, "27AAAAA0000A1Z5")).thenReturn(java.util.Collections.emptyList());
        when(customerRepo.findByFirmIdAndPhone(FIRM_A, "9876543210")).thenReturn(java.util.Collections.emptyList());

        Customer savedCustomer = new Customer();
        savedCustomer.setId(101L);
        savedCustomer.setName("Acme Wholesaler");
        savedCustomer.setFirmId(FIRM_A);
        savedCustomer.setPhone("9876543210");
        savedCustomer.setGstin("27AAAAA0000A1Z5");
        when(customerRepo.save(any(Customer.class))).thenReturn(savedCustomer);

        BusinessPartyLink savedLink = BusinessPartyLink.builder()
                .id(10L)
                .firmId(FIRM_A)
                .customerId(101L)
                .partyId(200L)
                .linkedAt(LocalDateTime.now())
                .notes("Enabled customer role from supplier")
                .build();
        when(linkRepo.save(any(BusinessPartyLink.class))).thenReturn(savedLink);

        EnableCustomerRoleRequest req = EnableCustomerRoleRequest.builder()
                .partyId(200L)
                .build();

        BusinessPartyLinkResponse response = linkService.enableCustomerRole(req);

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals(101L, response.getCustomerId());
        assertEquals(200L, response.getPartyId());
        verify(customerRepo, times(1)).save(any(Customer.class));
        verify(linkRepo, times(1)).save(any(BusinessPartyLink.class));
    }

    @Test
    @DisplayName("Enable Customer Role: Links existing unlinked Customer matching GSTIN")
    void testEnableCustomerRole_LinksExistingCustomerByGstin() {
        when(partyRepo.findByIdAndFirmId(200L, FIRM_A)).thenReturn(Optional.of(partyA));
        when(linkRepo.existsByFirmIdAndPartyId(FIRM_A, 200L)).thenReturn(false);
        when(customerRepo.findByFirmIdAndGstinIgnoreCase(FIRM_A, "27AAAAA0000A1Z5")).thenReturn(List.of(customerA));
        when(customerRepo.findByFirmIdAndPhone(FIRM_A, "9876543210")).thenReturn(List.of(customerA));
        when(linkRepo.existsByFirmIdAndCustomerId(FIRM_A, 100L)).thenReturn(false);

        BusinessPartyLink savedLink = BusinessPartyLink.builder()
                .id(11L)
                .firmId(FIRM_A)
                .customerId(100L)
                .partyId(200L)
                .linkedAt(LocalDateTime.now())
                .build();
        when(linkRepo.save(any(BusinessPartyLink.class))).thenReturn(savedLink);

        EnableCustomerRoleRequest req = EnableCustomerRoleRequest.builder()
                .partyId(200L)
                .build();

        BusinessPartyLinkResponse response = linkService.enableCustomerRole(req);

        assertNotNull(response);
        assertEquals(11L, response.getId());
        assertEquals(100L, response.getCustomerId());
        assertEquals(200L, response.getPartyId());
        verify(customerRepo, times(1)).save(customerA); // Synced canonical name to existing customer
        verify(linkRepo, times(1)).save(any(BusinessPartyLink.class));
    }

    @Test
    @DisplayName("Enable Customer Role: Rejects when Party already linked")
    void testEnableCustomerRole_PartyAlreadyLinked_ThrowsIllegalStateException() {
        when(partyRepo.findByIdAndFirmId(200L, FIRM_A)).thenReturn(Optional.of(partyA));
        when(linkRepo.existsByFirmIdAndPartyId(FIRM_A, 200L)).thenReturn(true);

        EnableCustomerRoleRequest req = EnableCustomerRoleRequest.builder()
                .partyId(200L)
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> linkService.enableCustomerRole(req));
        assertTrue(ex.getMessage().contains("already linked to a Customer"));
        verify(linkRepo, never()).save(any());
    }

    @Test
    @DisplayName("Enable Supplier Role: Creates new Party and links to Customer")
    void testEnablePartyRole_CreatesNewPartyAndLinks() {
        when(customerRepo.findByIdAndFirmId(100L, FIRM_A)).thenReturn(Optional.of(customerA));
        when(linkRepo.existsByFirmIdAndCustomerId(FIRM_A, 100L)).thenReturn(false);
        when(partyRepo.findFirstByFirmIdAndGstinIgnoreCase(FIRM_A, "27AAAAA0000A1Z5")).thenReturn(Optional.empty());
        when(partyRepo.findFirstByFirmIdAndPhone(FIRM_A, "9876543210")).thenReturn(Optional.empty());

        Party savedParty = new Party();
        savedParty.setId(205L);
        savedParty.setName("Acme Retailer");
        savedParty.setFirmId(FIRM_A);
        savedParty.setPhone("9876543210");
        savedParty.setGstin("27AAAAA0000A1Z5");
        when(partyRepo.save(any(Party.class))).thenReturn(savedParty);

        BusinessPartyLink savedLink = BusinessPartyLink.builder()
                .id(12L)
                .firmId(FIRM_A)
                .customerId(100L)
                .partyId(205L)
                .linkedAt(LocalDateTime.now())
                .build();
        when(linkRepo.save(any(BusinessPartyLink.class))).thenReturn(savedLink);

        EnablePartyRoleRequest req = EnablePartyRoleRequest.builder()
                .customerId(100L)
                .build();

        BusinessPartyLinkResponse response = linkService.enablePartyRole(req);

        assertNotNull(response);
        assertEquals(12L, response.getId());
        assertEquals(100L, response.getCustomerId());
        assertEquals(205L, response.getPartyId());
        verify(partyRepo, times(1)).save(any(Party.class));
        verify(linkRepo, times(1)).save(any(BusinessPartyLink.class));
    }

    @Test
    @DisplayName("Enable Supplier Role: Rejects when Customer already linked")
    void testEnablePartyRole_CustomerAlreadyLinked_ThrowsIllegalStateException() {
        when(customerRepo.findByIdAndFirmId(100L, FIRM_A)).thenReturn(Optional.of(customerA));
        when(linkRepo.existsByFirmIdAndCustomerId(FIRM_A, 100L)).thenReturn(true);

        EnablePartyRoleRequest req = EnablePartyRoleRequest.builder()
                .customerId(100L)
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> linkService.enablePartyRole(req));
        assertTrue(ex.getMessage().contains("already linked to a Supplier"));
        verify(linkRepo, never()).save(any());
    }

    @Test
    @DisplayName("Sync Profiles: Pushes Customer updates to linked Supplier")
    void testSyncProfiles_FromCustomerToParty() {
        BusinessPartyLink link = BusinessPartyLink.builder()
                .id(50L)
                .firmId(FIRM_A)
                .customerId(100L)
                .partyId(200L)
                .build();
        when(linkRepo.findById(50L)).thenReturn(Optional.of(link));
        when(customerRepo.findByIdAndFirmId(100L, FIRM_A)).thenReturn(Optional.of(customerA));
        when(partyRepo.findByIdAndFirmId(200L, FIRM_A)).thenReturn(Optional.of(partyA));
        when(partyRepo.save(any(Party.class))).thenAnswer(inv -> inv.getArgument(0));

        SyncProfileRequest req = SyncProfileRequest.builder()
                .source("CUSTOMER")
                .name("Acme Group Worldwide")
                .phone("9999999999")
                .address("New Tower, Pune")
                .build();

        BusinessPartyLinkResponse res = linkService.syncProfiles(50L, req);

        assertNotNull(res);
        assertEquals("Acme Group Worldwide", partyA.getName());
        assertEquals("9999999999", partyA.getPhone());
        assertEquals("New Tower, Pune", partyA.getAddress());
        verify(partyRepo, times(1)).save(partyA);
    }

    @Test
    @DisplayName("Sync Profiles: Rejects cross-firm access")
    void testSyncProfiles_CrossFirmForbidden() {
        BusinessPartyLink linkOtherFirm = BusinessPartyLink.builder()
                .id(51L)
                .firmId(FIRM_B)
                .customerId(100L)
                .partyId(200L)
                .build();
        when(linkRepo.findById(51L)).thenReturn(Optional.of(linkOtherFirm));

        SyncProfileRequest req = SyncProfileRequest.builder()
                .source("CUSTOMER")
                .build();

        assertThrows(TenantSecurityException.class, () -> linkService.syncProfiles(51L, req));
        verify(partyRepo, never()).save(any());
    }
}
