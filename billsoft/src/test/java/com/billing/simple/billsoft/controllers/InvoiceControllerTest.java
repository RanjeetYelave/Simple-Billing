package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.dtos.InvoiceRequest;
import com.billing.simple.billsoft.dtos.InvoiceUpdateRequest;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.InvoiceStatus;
import com.billing.simple.billsoft.service.InvoicePdfService;
import com.billing.simple.billsoft.service.InvoiceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class InvoiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InvoiceService invoiceService;

    @MockBean
    private InvoicePdfService pdfService;

    @Autowired
    private ObjectMapper objectMapper;

    // ---------- NUMBER GENERATORS ----------
    @Test
    void testNextInvoiceNumber() throws Exception {
        when(invoiceService.generateInvoiceNumber(anyLong())).thenReturn("INV-001");
        mockMvc.perform(get("/api/invoices/next-invoice-number").param("firmId", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string("INV-001"));
    }

    @Test
    void testNextEstimateNumber() throws Exception {
        when(invoiceService.generateEstimateNumber(anyLong())).thenReturn("EST-001");
        mockMvc.perform(get("/api/invoices/next-estimate-number").param("firmId", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string("EST-001"));
    }

    // ---------- CREATE ----------
    @Test
    void testCreateInvoice() throws Exception {
        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(10L);
        req.setInvoiceNumber("INV-100");
        Invoice created = new Invoice();
        created.setId(1L);
        created.setInvoiceNumber("INV-100");
        created.setStatus(InvoiceStatus.FINAL);
        when(invoiceService.createInvoice(any(InvoiceRequest.class))).thenReturn(created);

        mockMvc.perform(post("/api/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.invoiceNumber").value("INV-100"))
                .andExpect(jsonPath("$.status").value("FINAL"));
    }

    @Test
    void testCreateEstimate() throws Exception {
        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(10L);
        req.setEstimateNumber("EST-200");
        Invoice created = new Invoice();
        created.setId(2L);
        created.setEstimateNumber("EST-200");
        created.setStatus(InvoiceStatus.ESTIMATE);
        when(invoiceService.createEstimate(any(InvoiceRequest.class))).thenReturn(created);

        mockMvc.perform(post("/api/invoices/estimate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.estimateNumber").value("EST-200"))
                .andExpect(jsonPath("$.status").value("ESTIMATE"));
    }

    // ---------- CONVERT ESTIMATE ----------
    @Test
    void testConvertEstimate() throws Exception {
        InvoiceRequest override = new InvoiceRequest();
        override.setInvoiceNumber("INV-300");
        Invoice converted = new Invoice();
        converted.setId(3L);
        converted.setInvoiceNumber("INV-300");
        when(invoiceService.convertEstimateToInvoice(eq(5L), any())).thenReturn(converted);

        mockMvc.perform(post("/api/invoices/convert/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(override)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.invoiceNumber").value("INV-300"));
    }

    // ---------- GET LINKED INVOICE ----------
    @Test
    void testGetLinkedInvoice() throws Exception {
        Invoice estimate = new Invoice();
        estimate.setId(4L);
        estimate.setConvertedInvoiceId(6L);
        Invoice linked = new Invoice();
        linked.setId(6L);
        linked.setInvoiceNumber("INV-600");
        when(invoiceService.getById(4L)).thenReturn(estimate);
        when(invoiceService.getById(6L)).thenReturn(linked);

        mockMvc.perform(get("/api/invoices/4/linked-invoice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(6))
                .andExpect(jsonPath("$.invoiceNumber").value("INV-600"));
    }

    // ---------- PREVIEW ----------
    @Test
    void testPreviewInvoice() throws Exception {
        InvoiceRequest req = new InvoiceRequest();
        Invoice preview = new Invoice();
        preview.setId(7L);
        when(invoiceService.previewInvoice(any(InvoiceRequest.class))).thenReturn(preview);

        mockMvc.perform(post("/api/invoices/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    // ---------- LIST ----------
    @Test
    void testListInvoices() throws Exception {
        Invoice i1 = new Invoice();
        i1.setId(8L);
        Invoice i2 = new Invoice();
        i2.setId(9L);
        when(invoiceService.getAll(any(), any())).thenReturn(Arrays.asList(i1, i2));

        mockMvc.perform(get("/api/invoices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ---------- GET BY ID ----------
    @Test
    void testGetById() throws Exception {
        Invoice inv = new Invoice();
        inv.setId(10L);
        when(invoiceService.getById(10L)).thenReturn(inv);

        mockMvc.perform(get("/api/invoices/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    // ---------- UPDATE ----------
    @Test
    void testUpdateInvoice() throws Exception {
        InvoiceUpdateRequest upd = new InvoiceUpdateRequest();
        upd.setStatus(InvoiceStatus.PAID);
        Invoice updated = new Invoice();
        updated.setId(11L);
        updated.setStatus(InvoiceStatus.PAID);
        when(invoiceService.updateFullInvoice(eq(11L), any())).thenReturn(updated);

        mockMvc.perform(put("/api/invoices/11")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(upd)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    // ---------- DELETE ----------
    @Test
    void testDeleteInvoice() throws Exception {
        when(invoiceService.delete(12L)).thenReturn(true);
        mockMvc.perform(delete("/api/invoices/12"))
                .andExpect(status().isNoContent());
    }

    // ---------- MARK PAID ----------
    @Test
    void testMarkPaid() throws Exception {
        Invoice inv = new Invoice();
        inv.setId(13L);
        inv.setPaid(true);
        when(invoiceService.updatePaidFlag(eq(13L), eq(true))).thenReturn(inv);
        mockMvc.perform(put("/api/invoices/13/paid").param("paid", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(13))
                .andExpect(jsonPath("$.paid").value(true));
    }

    // ---------- UPDATE STATUS ----------
    @Test
    void testUpdateStatus() throws Exception {
        Invoice inv = new Invoice();
        inv.setId(14L);
        inv.setStatus(InvoiceStatus.CANCELLED);
        when(invoiceService.updateStatus(eq(14L), any())).thenReturn(inv);
        mockMvc.perform(put("/api/invoices/14/status").param("status", "CANCELLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(14))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    // ---------- ANALYTICS ----------
    @Test
    void testAnalyticsByCustomer() throws Exception {
        // Assuming the service returns a DTO with a simple "total" field
        com.billing.simple.billsoft.dtos.CustomerAnalyticsResponse resp =
                new com.billing.simple.billsoft.dtos.CustomerAnalyticsResponse();
        resp.setTotal(5);
        when(invoiceService.getCustomerAnalytics(20L)).thenReturn(resp);

        mockMvc.perform(get("/api/invoices/analytics/customer/20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5));
    }

    // ---------- PDF DOWNLOAD ----------
    @Test
    void testDownloadPdf() throws Exception {
        Invoice inv = new Invoice();
        inv.setId(15L);
        inv.setStatus(InvoiceStatus.FINAL);
        inv.setInvoiceNumber("INV-150");
        byte[] pdfBytes = new byte[]{0x25, 0x50, 0x44, 0x46}; // minimal PDF header
        when(invoiceService.getById(15L)).thenReturn(inv);
        when(pdfService.generatePdf(eq(inv), anyString())).thenReturn(pdfBytes);

        mockMvc.perform(get("/api/invoices/15/pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=invoice-INV-150.pdf"))
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(pdfBytes));
    }
}
