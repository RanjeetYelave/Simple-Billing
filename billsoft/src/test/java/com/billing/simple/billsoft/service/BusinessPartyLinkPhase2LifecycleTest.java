package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dtos.*;
import com.billing.simple.billsoft.entities.BusinessPartyLink;
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.Party;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repositories.BusinessPartyLinkRepository;
import com.billing.simple.billsoft.repositories.PartyPaymentRepository;
import com.billing.simple.billsoft.repositories.PartyRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;
import com.billing.simple.billsoft.service.impl.BusinessPartyLinkServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BusinessPartyLinkPhase2LifecycleTest {

    private static final Long FIRM_A = 1L;
    private static final Long FIRM_B = 2L;

    @Mock private BusinessPartyLinkRepository linkRepo;
    @Mock private CustomerRepository customerRepo;
    @Mock private PartyRepository partyRepo;
    @Mock private CustomerBalanceService customerBalanceService;
    @Mock private PartyService partyService;
    @Mock private StatementService statementService;
    @Mock private PartyPaymentRepository partyPaymentRepo;
    @Mock private com.billing.simple.billsoft.repositories.BusinessIdentityAliasRepository aliasRepo;
    @Mock private com.billing.simple.billsoft.repositories.BusinessPartyLinkEventRepository eventRepo;
    @Mock private com.billing.simple.billsoft.repo.AppConfigRepository appConfigRepo;

    @InjectMocks
    private BusinessPartyLinkServiceImpl linkService;

    private Customer customer;
    private Party party;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentFirmId(FIRM_A);

        customer = Customer.builder()
                .id(10L)
                .firmId(FIRM_A)
                .name("Alpha Traders Customer")
                .phone("9988776655")
                .email("alpha.cust@example.com")
                .address("12 Market Road")
                .gstin("27ABCDE1234F1Z5")
                .build();

        party = Party.builder()
                .id(20L)
                .firmId(FIRM_A)
                .name("Alpha Traders Supplier")
                .contactPerson("Rajesh Alpha")
                .phone("9988776655")
                .email("alpha.supp@example.com")
                .address("99 Industrial Area")
                .city("Pune")
                .state("Maharashtra")
                .pincode("411001")
                .gstin("27ABCDE1234F1Z5")
                .pan("ABCDE1234F")
                .bankName("HDFC Bank")
                .bankAccount("1234567890")
                .bankIfsc("HDFC0000123")
                .upiId("alpha@hdfcbank")
                .openingBalance(new BigDecimal("15000.00"))
                .openingBalanceType("PAYABLE")
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Preview Enable Customer Role: Detects existing unlinked Customer by GSTIN and Phone")
    void testPreviewEnableCustomerRole_MatchesExistingOrNew() {
        when(partyRepo.findByIdAndFirmId(20L, FIRM_A)).thenReturn(Optional.of(party));
        when(linkRepo.findByFirmIdAndPartyId(FIRM_A, 20L)).thenReturn(Optional.empty());
        when(customerRepo.findByFirmIdAndGstinIgnoreCase(FIRM_A, "27ABCDE1234F1Z5")).thenReturn(List.of(customer));
        when(linkRepo.existsByFirmIdAndCustomerId(FIRM_A, 10L)).thenReturn(false);

        RoleMatchPreviewResponse matchRes = linkService.previewEnableCustomerRole(20L, null, null);
        assertEquals("REUSE_EXISTING", matchRes.getResolution());
        assertEquals("GSTIN", matchRes.getMatchedBy());
        assertEquals(10L, matchRes.getExistingEntityId());

        // Phone fallback when GSTIN doesn't match
        when(customerRepo.findByFirmIdAndGstinIgnoreCase(FIRM_A, "99ZZZZZ9999Z1Z1")).thenReturn(List.of());
        when(customerRepo.findByFirmIdAndPhone(FIRM_A, "9988776655")).thenReturn(List.of(customer));
        RoleMatchPreviewResponse phoneRes = linkService.previewEnableCustomerRole(20L, "99ZZZZZ9999Z1Z1", "9988776655");
        assertEquals("REUSE_EXISTING", phoneRes.getResolution());
        assertEquals("PHONE", phoneRes.getMatchedBy());

        // CREATE_NEW when neither matches
        when(customerRepo.findByFirmIdAndPhone(FIRM_A, "1111111111")).thenReturn(List.of());
        RoleMatchPreviewResponse newRes = linkService.previewEnableCustomerRole(20L, "99ZZZZZ9999Z1Z1", "1111111111");
        assertEquals("CREATE_NEW", newRes.getResolution());
        assertEquals("NONE", newRes.getMatchedBy());
    }

    @Test
    @DisplayName("Sync Profiles preserves Party-only bank, PAN, city/state, and opening balance")
    void testSyncProfiles_PreservesSupplierFinancialAndBankFields() {
        BusinessPartyLink link = BusinessPartyLink.builder()
                .id(55L).firmId(FIRM_A).customerId(10L).partyId(20L)
                .status("ACTIVE").customerRoleActive(true).partyRoleActive(true)
                .build();

        when(linkRepo.findById(55L)).thenReturn(Optional.of(link));
        when(customerRepo.findByIdAndFirmId(10L, FIRM_A)).thenReturn(Optional.of(customer));
        when(partyRepo.findByIdAndFirmId(20L, FIRM_A)).thenReturn(Optional.of(party));
        when(partyRepo.save(any(Party.class))).thenAnswer(inv -> inv.getArgument(0));

        SyncProfileRequest req = SyncProfileRequest.builder()
                .source("CUSTOMER")
                .name("Alpha Unified Co.")
                .phone("8888888888")
                .email("unified@alpha.com")
                .address("New Unified HQ")
                .gstin("27NEWGS1234F1Z2")
                .build();

        BusinessPartyLinkResponse res = linkService.syncProfiles(55L, req);

        // Shared identity fields updated on Party
        assertEquals("Alpha Unified Co.", party.getName());
        assertEquals("8888888888", party.getPhone());
        assertEquals("unified@alpha.com", party.getEmail());
        assertEquals("New Unified HQ", party.getAddress());
        assertEquals("27NEWGS1234F1Z2", party.getGstin());
        assertEquals("unified@alpha.com", res.getPartyEmail());

        // Supplier-only financial & bank fields strictly preserved
        assertEquals("HDFC Bank", party.getBankName());
        assertEquals("1234567890", party.getBankAccount());
        assertEquals("HDFC0000123", party.getBankIfsc());
        assertEquals("alpha@hdfcbank", party.getUpiId());
        assertEquals("ABCDE1234F", party.getPan());
        assertEquals(new BigDecimal("15000.00"), party.getOpeningBalance());
        assertEquals("PAYABLE", party.getOpeningBalanceType());
    }

    @Test
    @DisplayName("Safeguard blocks disabling Customer role when Customer has unsettled balance")
    void testDisableCustomerRole_BlockedWhenUnsettledBalanceExists() {
        BusinessPartyLink link = BusinessPartyLink.builder()
                .id(60L).firmId(FIRM_A).customerId(10L).partyId(20L)
                .status("ACTIVE").customerRoleActive(true).partyRoleActive(true)
                .build();
        when(linkRepo.findById(60L)).thenReturn(Optional.of(link));
        when(customerRepo.findByIdAndFirmId(10L, FIRM_A)).thenReturn(Optional.of(customer));
        when(partyRepo.findByIdAndFirmId(20L, FIRM_A)).thenReturn(Optional.of(party));
        when(customerBalanceService.getCustomerBalance(FIRM_A, 10L)).thenReturn(
                CustomerBalanceSummary.builder().customerId(10L).closingBalance(new BigDecimal("2500.00")).build()
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                linkService.disableRole(60L, RoleLifecycleRequest.builder().role("CUSTOMER").reason("Test").build()));
        assertTrue(ex.getMessage().contains("unsettled outstanding receivable"));
        verify(linkRepo, never()).save(any());
    }

    @Test
    @DisplayName("Safeguard blocks disabling Supplier role when pending POs or unsettled payables exist")
    void testDisablePartyRole_BlockedWhenPendingPosOrPayableExists() {
        BusinessPartyLink link = BusinessPartyLink.builder()
                .id(61L).firmId(FIRM_A).customerId(10L).partyId(20L)
                .status("ACTIVE").customerRoleActive(true).partyRoleActive(true)
                .build();
        when(linkRepo.findById(61L)).thenReturn(Optional.of(link));
        when(customerRepo.findByIdAndFirmId(10L, FIRM_A)).thenReturn(Optional.of(customer));
        when(partyRepo.findByIdAndFirmId(20L, FIRM_A)).thenReturn(Optional.of(party));

        // 1) Pending PO blocks
        when(partyService.getFinancialSummary(20L, FIRM_A)).thenReturn(
                PartyFinancialSummary.builder().partyId(20L).pendingPurchaseOrders(2L).netBalance(BigDecimal.ZERO).build()
        );
        IllegalStateException poEx = assertThrows(IllegalStateException.class, () ->
                linkService.disableRole(61L, RoleLifecycleRequest.builder().role("PARTY").build()));
        assertTrue(poEx.getMessage().contains("pending or issued Purchase Order"));

        // 2) Unsettled payable blocks
        when(partyService.getFinancialSummary(20L, FIRM_A)).thenReturn(
                PartyFinancialSummary.builder().partyId(20L).pendingPurchaseOrders(0L).netBalance(new BigDecimal("4200.50")).build()
        );
        IllegalStateException balEx = assertThrows(IllegalStateException.class, () ->
                linkService.disableRole(61L, RoleLifecycleRequest.builder().role("SUPPLIER").build()));
        assertTrue(balEx.getMessage().contains("unsettled outstanding payable"));
        verify(linkRepo, never()).save(any());
    }

    @Test
    @DisplayName("Soft disable & re-enable preserves link and reuses existing counterpart record")
    void testDisableAndReenable_SoftLifecycleReusesExistingRecord() {
        BusinessPartyLink link = BusinessPartyLink.builder()
                .id(62L).firmId(FIRM_A).customerId(10L).partyId(20L)
                .status("ACTIVE").customerRoleActive(true).partyRoleActive(true)
                .linkedAt(LocalDateTime.now())
                .build();

        when(linkRepo.findById(62L)).thenReturn(Optional.of(link));
        when(customerRepo.findByIdAndFirmId(10L, FIRM_A)).thenReturn(Optional.of(customer));
        when(partyRepo.findByIdAndFirmId(20L, FIRM_A)).thenReturn(Optional.of(party));
        when(partyService.getFinancialSummary(20L, FIRM_A)).thenReturn(
                PartyFinancialSummary.builder().partyId(20L).pendingPurchaseOrders(0L).netBalance(BigDecimal.ZERO).balanceStatus("SETTLED").build()
        );
        when(customerBalanceService.getCustomerBalance(FIRM_A, 10L)).thenReturn(
                CustomerBalanceSummary.builder().customerId(10L).closingBalance(BigDecimal.ZERO).build()
        );
        when(linkRepo.save(any(BusinessPartyLink.class))).thenAnswer(inv -> inv.getArgument(0));

        BusinessPartyLinkResponse disabled = linkService.disableRole(62L, RoleLifecycleRequest.builder().role("PARTY").reason("Seasonal break").build());
        assertEquals("PARTY_DISABLED", disabled.getStatus());
        assertFalse(disabled.getPartyRoleActive());
        assertTrue(disabled.getCustomerRoleActive());
        assertEquals("Seasonal break", disabled.getDisabledReason());

        // Calling enablePartyRole while soft-disabled re-enables and reuses Party #20 instead of creating duplicate
        when(linkRepo.findByFirmIdAndCustomerId(FIRM_A, 10L)).thenReturn(Optional.of(link));
        BusinessPartyLinkResponse reenabledViaEnable = linkService.enablePartyRole(EnablePartyRoleRequest.builder().customerId(10L).build());
        assertEquals("ACTIVE", reenabledViaEnable.getStatus());
        assertTrue(reenabledViaEnable.getPartyRoleActive());
        assertEquals(20L, reenabledViaEnable.getPartyId());
        verify(partyRepo, never()).save(any());
        verify(linkRepo, never()).delete(any());
    }

    @Test
    @DisplayName("Rejects cross-firm disable/reenable/preview operations")
    void testCrossFirmLifecycleRejected() {
        BusinessPartyLink firmBLink = BusinessPartyLink.builder()
                .id(99L).firmId(FIRM_B).customerId(10L).partyId(20L).build();
        when(linkRepo.findById(99L)).thenReturn(Optional.of(firmBLink));

        assertThrows(TenantSecurityException.class, () ->
                linkService.disableRole(99L, RoleLifecycleRequest.builder().role("PARTY").build()));
        assertThrows(TenantSecurityException.class, () ->
                linkService.reenableRole(99L, RoleLifecycleRequest.builder().role("PARTY").build()));

        when(partyRepo.findByIdAndFirmId(999L, FIRM_A)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () ->
                linkService.previewEnableCustomerRole(999L, null, null));
    }

    @Test
    @DisplayName("getFirmLinksMap includes role lifecycle status and common emails/GSTINs")
    void testGetFirmLinksMapIncludesRoleStatuses() {
        BusinessPartyLink link = BusinessPartyLink.builder()
                .id(70L).firmId(FIRM_A).customerId(10L).partyId(20L)
                .status("CUSTOMER_DISABLED").customerRoleActive(false).partyRoleActive(true)
                .build();
        when(linkRepo.findByFirmId(FIRM_A)).thenReturn(List.of(link));
        when(customerRepo.findAllById(any())).thenReturn(List.of(customer));
        when(partyRepo.findAllById(any())).thenReturn(List.of(party));

        Map<String, Object> map = linkService.getFirmLinksMap(FIRM_A);
        @SuppressWarnings("unchecked")
        Map<Long, Map<String, Object>> partyToCust = (Map<Long, Map<String, Object>>) map.get("partyToCustomer");
        Map<String, Object> cInfo = partyToCust.get(20L);
        assertNotNull(cInfo);
        assertEquals("CUSTOMER_DISABLED", cInfo.get("status"));
        assertEquals(Boolean.FALSE, cInfo.get("customerRoleActive"));
        assertEquals(Boolean.TRUE, cInfo.get("partyRoleActive"));
        assertEquals("alpha.cust@example.com", cInfo.get("customerEmail"));
    }
}

