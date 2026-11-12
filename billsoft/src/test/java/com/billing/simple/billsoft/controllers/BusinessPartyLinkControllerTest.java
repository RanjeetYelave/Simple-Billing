package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.dtos.*;
import com.billing.simple.billsoft.service.BusinessPartyLinkService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class BusinessPartyLinkControllerTest {

    private MockMvc mockMvc;

    @Mock
    private BusinessPartyLinkService linkService;

    @InjectMocks
    private BusinessPartyLinkController controller;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void testLink() throws Exception {
        BusinessPartyLinkResponse res = BusinessPartyLinkResponse.builder()
                .id(1L)
                .firmId(1L)
                .customerId(10L)
                .partyId(20L)
                .customerName("Rahul Sharma")
                .partyName("Rahul Traders")
                .linkedAt(LocalDateTime.now())
                .build();

        when(linkService.link(any(BusinessPartyLinkRequest.class))).thenReturn(res);

        BusinessPartyLinkRequest req = new BusinessPartyLinkRequest();
        req.setCustomerId(10L);
        req.setPartyId(20L);
        req.setNotes("Test link");

        mockMvc.perform(post("/api/party-links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.customerName").value("Rahul Sharma"))
                .andExpect(jsonPath("$.partyName").value("Rahul Traders"));
    }

    @Test
    void testUnlinkById() throws Exception {
        doNothing().when(linkService).unlink(1L);

        mockMvc.perform(delete("/api/party-links/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void testGetByCustomer() throws Exception {
        BusinessPartyLinkResponse res = BusinessPartyLinkResponse.builder()
                .id(1L)
                .customerId(10L)
                .partyId(20L)
                .customerName("Rahul Sharma")
                .partyName("Rahul Traders")
                .build();

        when(linkService.getLinkedPartyForCustomer(10L)).thenReturn(res);

        mockMvc.perform(get("/api/party-links/customer/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(10))
                .andExpect(jsonPath("$.partyName").value("Rahul Traders"));
    }

    @Test
    void testGetByCustomerNotFound() throws Exception {
        when(linkService.getLinkedPartyForCustomer(999L)).thenReturn(null);

        mockMvc.perform(get("/api/party-links/customer/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetByParty() throws Exception {
        BusinessPartyLinkResponse res = BusinessPartyLinkResponse.builder()
                .id(1L)
                .customerId(10L)
                .partyId(20L)
                .customerName("Rahul Sharma")
                .partyName("Rahul Traders")
                .build();

        when(linkService.getLinkedCustomerForParty(20L)).thenReturn(res);

        mockMvc.perform(get("/api/party-links/party/20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partyId").value(20))
                .andExpect(jsonPath("$.customerName").value("Rahul Sharma"));
    }

    @Test
    void testGetMap() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("linkedCustomerIds", Collections.singletonList(10L));
        map.put("linkedPartyIds", Collections.singletonList(20L));

        when(linkService.getFirmLinksMap(any())).thenReturn(map);

        mockMvc.perform(get("/api/party-links/map"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedCustomerIds[0]").value(10))
                .andExpect(jsonPath("$.linkedPartyIds[0]").value(20));
    }

    @Test
    void testGetUnifiedStatement() throws Exception {
        UnifiedCounterpartyStatementResponse stmt = UnifiedCounterpartyStatementResponse.builder()
                .customerId(10L)
                .customerName("Rahul Sharma")
                .partyId(20L)
                .partyName("Rahul Traders")
                .totalReceivables(15000.0)
                .totalPayables(10000.0)
                .netPosition(5000.0)
                .netPositionLabel("NET_RECEIVABLE")
                .entries(new ArrayList<>())
                .build();

        when(linkService.getUnifiedStatement(eq(10L), eq(20L), any(), any())).thenReturn(stmt);

        mockMvc.perform(get("/api/party-links/unified-statement")
                        .param("customerId", "10")
                        .param("partyId", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReceivables").value(15000.0))
                .andExpect(jsonPath("$.totalPayables").value(10000.0))
                .andExpect(jsonPath("$.netPosition").value(5000.0))
                .andExpect(jsonPath("$.netPositionLabel").value("NET_RECEIVABLE"));
    }

    @Test
    void testEnableCustomerRole() throws Exception {
        BusinessPartyLinkResponse res = BusinessPartyLinkResponse.builder()
                .id(99L)
                .customerId(10L)
                .partyId(20L)
                .customerName("Rahul Traders Customer")
                .partyName("Rahul Traders")
                .build();

        when(linkService.enableCustomerRole(any(EnableCustomerRoleRequest.class))).thenReturn(res);

        EnableCustomerRoleRequest req = EnableCustomerRoleRequest.builder()
                .partyId(20L)
                .customerName("Rahul Traders Customer")
                .build();

        mockMvc.perform(post("/api/party-links/enable-customer-role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(99L))
                .andExpect(jsonPath("$.customerId").value(10L))
                .andExpect(jsonPath("$.customerName").value("Rahul Traders Customer"));
    }

    @Test
    void testEnablePartyRole() throws Exception {
        BusinessPartyLinkResponse res = BusinessPartyLinkResponse.builder()
                .id(100L)
                .customerId(10L)
                .partyId(25L)
                .customerName("Rahul Sharma")
                .partyName("Rahul Sharma Supplier")
                .build();

        when(linkService.enablePartyRole(any(EnablePartyRoleRequest.class))).thenReturn(res);

        EnablePartyRoleRequest req = EnablePartyRoleRequest.builder()
                .customerId(10L)
                .partyName("Rahul Sharma Supplier")
                .build();

        mockMvc.perform(post("/api/party-links/enable-party-role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100L))
                .andExpect(jsonPath("$.partyId").value(25L))
                .andExpect(jsonPath("$.partyName").value("Rahul Sharma Supplier"));
    }

    @Test
    void testSyncProfiles() throws Exception {
        BusinessPartyLinkResponse res = BusinessPartyLinkResponse.builder()
                .id(50L)
                .customerId(10L)
                .partyId(20L)
                .customerName("Synced Name")
                .partyName("Synced Name")
                .build();

        when(linkService.syncProfiles(eq(50L), any(SyncProfileRequest.class))).thenReturn(res);

        SyncProfileRequest req = SyncProfileRequest.builder()
                .source("CUSTOMER")
                .name("Synced Name")
                .build();

        mockMvc.perform(put("/api/party-links/50/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(50L))
                .andExpect(jsonPath("$.customerName").value("Synced Name"));
    }

    @Test
    void testPreviewEnableCustomerRole() throws Exception {
        RoleMatchPreviewResponse preview = RoleMatchPreviewResponse.builder()
                .targetRole("CUSTOMER")
                .resolution("REUSE_EXISTING")
                .matchedBy("GSTIN")
                .existingEntityId(101L)
                .existingEntityName("Existing Customer")
                .message("Matched existing Customer")
                .build();

        when(linkService.previewEnableCustomerRole(20L, "27AAAAA0000A1Z5", "9876543210"))
                .thenReturn(preview);

        mockMvc.perform(get("/api/party-links/preview-customer-role")
                        .param("partyId", "20")
                        .param("gstin", "27AAAAA0000A1Z5")
                        .param("phone", "9876543210"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolution").value("REUSE_EXISTING"))
                .andExpect(jsonPath("$.matchedBy").value("GSTIN"))
                .andExpect(jsonPath("$.existingEntityId").value(101L));
    }

    @Test
    void testPreviewEnablePartyRole() throws Exception {
        RoleMatchPreviewResponse preview = RoleMatchPreviewResponse.builder()
                .targetRole("PARTY")
                .resolution("CREATE_NEW")
                .matchedBy("NONE")
                .message("No matching supplier found")
                .build();

        when(linkService.previewEnablePartyRole(10L, null, null)).thenReturn(preview);

        mockMvc.perform(get("/api/party-links/preview-party-role")
                        .param("customerId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetRole").value("PARTY"))
                .andExpect(jsonPath("$.resolution").value("CREATE_NEW"));
    }

    @Test
    void testDisableAndReenableRole() throws Exception {
        BusinessPartyLinkResponse disabledRes = BusinessPartyLinkResponse.builder()
                .id(77L)
                .customerId(10L)
                .partyId(20L)
                .status("PARTY_DISABLED")
                .customerRoleActive(true)
                .partyRoleActive(false)
                .disabledReason("Seasonal supplier paused")
                .build();

        BusinessPartyLinkResponse enabledRes = BusinessPartyLinkResponse.builder()
                .id(77L)
                .customerId(10L)
                .partyId(20L)
                .status("ACTIVE")
                .customerRoleActive(true)
                .partyRoleActive(true)
                .build();

        when(linkService.disableRole(eq(77L), any(RoleLifecycleRequest.class))).thenReturn(disabledRes);
        when(linkService.reenableRole(eq(77L), any(RoleLifecycleRequest.class))).thenReturn(enabledRes);

        RoleLifecycleRequest req = RoleLifecycleRequest.builder()
                .role("PARTY")
                .reason("Seasonal supplier paused")
                .build();

        mockMvc.perform(put("/api/party-links/77/disable-role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTY_DISABLED"))
                .andExpect(jsonPath("$.partyRoleActive").value(false))
                .andExpect(jsonPath("$.customerRoleActive").value(true));

        mockMvc.perform(put("/api/party-links/77/reenable-role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.partyRoleActive").value(true));
    }
}
