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
}
