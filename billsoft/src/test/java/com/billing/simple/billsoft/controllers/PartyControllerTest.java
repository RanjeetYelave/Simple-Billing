package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.dtos.PartyFinancialSummary;
import com.billing.simple.billsoft.entities.Party;
import com.billing.simple.billsoft.entities.PartyPayment;
import com.billing.simple.billsoft.service.PartyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PartyControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PartyService service;

    @InjectMocks
    private PartyController controller;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void testCreate() throws Exception {
        Party party = Party.builder()
                .name("Acme Supplies")
                .firmId(1L)
                .openingBalance(BigDecimal.valueOf(5000))
                .openingBalanceType("PAYABLE")
                .build();

        when(service.createParty(any(Party.class))).thenReturn(party);

        mockMvc.perform(post("/api/parties")
                .header("X-Firm-Id", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(party)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Supplies"));
    }

    @Test
    void testList() throws Exception {
        when(service.getPartiesByFirm(1L)).thenReturn(Arrays.asList(
                Party.builder().id(1L).name("Party A").firmId(1L).build(),
                Party.builder().id(2L).name("Party B").firmId(1L).build()
        ));

        mockMvc.perform(get("/api/parties").header("X-Firm-Id", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void testGetById() throws Exception {
        Party p = Party.builder().id(1L).name("Party A").firmId(1L).build();
        when(service.getPartyById(1L, 1L)).thenReturn(Optional.of(p));

        mockMvc.perform(get("/api/parties/1").header("X-Firm-Id", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Party A"));
    }

    @Test
    void testFinancialSummary() throws Exception {
        PartyFinancialSummary summary = PartyFinancialSummary.builder()
                .partyId(1L)
                .partyName("Party A")
                .totalPurchases(BigDecimal.valueOf(25000))
                .totalPaid(BigDecimal.valueOf(10000))
                .netBalance(BigDecimal.valueOf(15000))
                .balanceStatus("PAYABLE")
                .build();

        when(service.getFinancialSummary(1L, 1L)).thenReturn(summary);

        mockMvc.perform(get("/api/parties/1/financial-summary").header("X-Firm-Id", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceStatus").value("PAYABLE"))
                .andExpect(jsonPath("$.netBalance").value(15000));
    }

    @Test
    void testRecordPayment() throws Exception {
        PartyPayment payment = PartyPayment.builder()
                .id(10L)
                .partyId(1L)
                .firmId(1L)
                .amount(BigDecimal.valueOf(5000))
                .paymentDate(LocalDate.now())
                .paymentMode("BANK_TRANSFER")
                .referenceNumber("UTR12345")
                .build();

        when(service.recordPayment(any(PartyPayment.class))).thenReturn(payment);

        mockMvc.perform(post("/api/parties/1/payments")
                .header("X-Firm-Id", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payment)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(5000));
    }

    @Test
    void testDeletePayment() throws Exception {
        doNothing().when(service).deletePayment(10L, 1L);

        mockMvc.perform(delete("/api/parties/payments/10")
                .header("X-Firm-Id", 1L))
                .andExpect(status().isNoContent());

        verify(service).deletePayment(10L, 1L);
    }

    @Test
    void testDeletePaymentNotFound() throws Exception {
        doThrow(new IllegalArgumentException("Payment not found")).when(service).deletePayment(999L, 1L);

        mockMvc.perform(delete("/api/parties/payments/999")
                .header("X-Firm-Id", 1L))
                .andExpect(status().isNotFound());
    }
}
