package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.entities.BusinessLetter;
import com.billing.simple.billsoft.entities.LetterRecipientType;
import com.billing.simple.billsoft.entities.LetterStatus;
import com.billing.simple.billsoft.service.BusinessLetterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class BusinessLetterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BusinessLetterService service;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testCreateLetterForParty() throws Exception {
        BusinessLetter letter = BusinessLetter.builder()
                .letterNumber("LTR-2026-0001")
                .firmId(1L)
                .letterDate(LocalDate.now())
                .recipientType(LetterRecipientType.PARTY)
                .partyId(10L)
                .recipientName("Acme Supplies")
                .subject("Payment Advice & Purchase Follow-up")
                .content("Dear Partner,\n\nPlease find the payment details attached.")
                .status(LetterStatus.ISSUED)
                .build();

        when(service.createLetter(any(BusinessLetter.class))).thenReturn(letter);

        mockMvc.perform(post("/api/letters")
                .header("X-Firm-Id", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(letter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.letterNumber").value("LTR-2026-0001"))
                .andExpect(jsonPath("$.recipientType").value("PARTY"));
    }

    @Test
    void testCreateLetterForCustomer() throws Exception {
        BusinessLetter letter = BusinessLetter.builder()
                .letterNumber("LTR-2026-0002")
                .firmId(1L)
                .letterDate(LocalDate.now())
                .recipientType(LetterRecipientType.CUSTOMER)
                .customerId(20L)
                .recipientName("John Doe")
                .subject("Payment Reminder")
                .content("Dear Customer,\n\nPlease remit the pending payment.")
                .status(LetterStatus.ISSUED)
                .build();

        when(service.createLetter(any(BusinessLetter.class))).thenReturn(letter);

        mockMvc.perform(post("/api/letters")
                .header("X-Firm-Id", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(letter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.letterNumber").value("LTR-2026-0002"))
                .andExpect(jsonPath("$.recipientType").value("CUSTOMER"));
    }

    @Test
    void testListLetters() throws Exception {
        when(service.getLettersByFirm(eq(1L), any(), any(), any(), any(), any(), any()))
                .thenReturn(Arrays.asList(
                        BusinessLetter.builder().id(1L).letterNumber("LTR-001").firmId(1L).build(),
                        BusinessLetter.builder().id(2L).letterNumber("LTR-002").firmId(1L).build()
                ));

        mockMvc.perform(get("/api/letters").header("X-Firm-Id", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void testGetNextNumber() throws Exception {
        when(service.generateNextLetterNumber(1L)).thenReturn("LTR-2026-0010");

        mockMvc.perform(get("/api/letters/next-number").header("X-Firm-Id", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextNumber").value("LTR-2026-0010"));
    }

    @Test
    void testUpdateStatus() throws Exception {
        BusinessLetter letter = BusinessLetter.builder()
                .id(1L)
                .firmId(1L)
                .status(LetterStatus.ARCHIVED)
                .build();

        when(service.updateStatus(eq(1L), eq(1L), eq(LetterStatus.ARCHIVED))).thenReturn(letter);

        mockMvc.perform(patch("/api/letters/1/status")
                .header("X-Firm-Id", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "ARCHIVED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    void testDownloadPdf() throws Exception {
        when(service.generateLetterPdf(1L, 1L)).thenReturn(new byte[]{1, 2, 3, 4});

        mockMvc.perform(get("/api/letters/1/pdf").header("X-Firm-Id", 1L))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }
}
