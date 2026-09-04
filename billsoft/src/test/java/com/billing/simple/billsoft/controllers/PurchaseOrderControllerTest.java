package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.entities.Party;
import com.billing.simple.billsoft.entities.PurchaseOrder;
import com.billing.simple.billsoft.entities.PurchaseOrderStatus;
import com.billing.simple.billsoft.service.PurchaseOrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
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
class PurchaseOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PurchaseOrderService service;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testCreate() throws Exception {
        Party party = Party.builder().id(1L).name("Acme").build();
        PurchaseOrder po = PurchaseOrder.builder()
                .poNumber("PO-2026-0001")
                .firmId(1L)
                .party(party)
                .poDate(LocalDate.now())
                .totalAmount(BigDecimal.valueOf(10000))
                .status(PurchaseOrderStatus.ISSUED)
                .build();

        when(service.createPurchaseOrder(any(PurchaseOrder.class))).thenReturn(po);

        mockMvc.perform(post("/api/purchase-orders")
                .header("X-Firm-Id", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(po)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.poNumber").value("PO-2026-0001"));
    }

    @Test
    void testList() throws Exception {
        when(service.getPurchaseOrdersByFirm(1L)).thenReturn(Arrays.asList(
                PurchaseOrder.builder().id(1L).poNumber("PO-001").firmId(1L).build(),
                PurchaseOrder.builder().id(2L).poNumber("PO-002").firmId(1L).build()
        ));

        mockMvc.perform(get("/api/purchase-orders").header("X-Firm-Id", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void testGetNextNumber() throws Exception {
        when(service.generateNextPoNumber(1L)).thenReturn("PO-2026-0005");

        mockMvc.perform(get("/api/purchase-orders/next-number").header("X-Firm-Id", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextNumber").value("PO-2026-0005"));
    }

    @Test
    void testUpdateStatus() throws Exception {
        PurchaseOrder po = PurchaseOrder.builder()
                .id(1L)
                .firmId(1L)
                .status(PurchaseOrderStatus.RECEIVED)
                .build();

        when(service.updateStatus(eq(1L), eq(1L), eq(PurchaseOrderStatus.RECEIVED))).thenReturn(po);

        mockMvc.perform(patch("/api/purchase-orders/1/status")
                .header("X-Firm-Id", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "RECEIVED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"));
    }

    @Test
    void testUpdateStatusWithPutAndQueryParamWithoutHeader() throws Exception {
        PurchaseOrder po = PurchaseOrder.builder()
                .id(1L)
                .firmId(1L)
                .status(PurchaseOrderStatus.CANCELLED)
                .build();

        when(service.updateStatus(eq(1L), any(), eq(PurchaseOrderStatus.CANCELLED))).thenReturn(po);

        mockMvc.perform(put("/api/purchase-orders/1/status?status=CANCELLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void testUpdateStatusWithPatchBodyWithoutFirmIdHeader() throws Exception {
        PurchaseOrder po = PurchaseOrder.builder()
                .id(1L)
                .firmId(1L)
                .status(PurchaseOrderStatus.DRAFT)
                .build();

        when(service.updateStatus(eq(1L), any(), eq(PurchaseOrderStatus.DRAFT))).thenReturn(po);

        mockMvc.perform(patch("/api/purchase-orders/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "DRAFT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void testRecordPayment() throws Exception {
        PurchaseOrder po = PurchaseOrder.builder()
                .id(1L)
                .firmId(1L)
                .paidAmount(BigDecimal.valueOf(5000))
                .paymentStatus("PARTIAL")
                .build();

        when(service.recordPoPayment(eq(1L), eq(1L), any(BigDecimal.class), any(LocalDate.class), any(), any(), any())).thenReturn(po);

        mockMvc.perform(post("/api/purchase-orders/1/payments")
                .header("X-Firm-Id", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "amount", 5000,
                        "paymentMode", "UPI",
                        "referenceNumber", "UPI-123456"
                ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paidAmount").value(5000))
                .andExpect(jsonPath("$.paymentStatus").value("PARTIAL"));
    }

    @Test
    void testDownloadPdf() throws Exception {
        when(service.generatePoPdf(1L, 1L)).thenReturn(new byte[]{1, 2, 3, 4});

        mockMvc.perform(get("/api/purchase-orders/1/pdf").header("X-Firm-Id", 1L))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }
}
