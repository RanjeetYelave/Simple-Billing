package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.dtos.SalesReturnRequest;
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.SalesReturn;
import com.billing.simple.billsoft.entities.SalesReturnItem;
import com.billing.simple.billsoft.service.InvoiceService;
import com.billing.simple.billsoft.service.SalesReturnPdfService;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@AutoConfigureMockMvc
class SalesReturnControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InvoiceService invoiceService;

    @MockitoBean
    private SalesReturnPdfService pdfService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testCreateSalesReturnSuccess() throws Exception {
        Long invoiceId = 445L;
        Invoice invoice = Invoice.builder().id(invoiceId).firmId(1L).build();
        Customer customer = Customer.builder().id(2L).name("John Doe").build();

        SalesReturn salesReturn = SalesReturn.builder()
                .id(10L)
                .firmId(1L)
                .returnNumber("CN-0001")
                .returnDate(LocalDate.now())
                .invoice(invoice)
                .customer(customer)
                .subtotal(BigDecimal.valueOf(100))
                .taxAmount(BigDecimal.valueOf(18))
                .totalRefundAmount(BigDecimal.valueOf(118))
                .items(new ArrayList<>())
                .build();

        SalesReturnItem item = SalesReturnItem.builder()
                .id(100L)
                .salesReturn(salesReturn)
                .productName("Widget")
                .returnQty(2)
                .unitPrice(BigDecimal.valueOf(50))
                .gstPercent(BigDecimal.valueOf(18))
                .gstAmount(BigDecimal.valueOf(18))
                .refundTotal(BigDecimal.valueOf(118))
                .build();
        salesReturn.getItems().add(item);

        when(invoiceService.createSalesReturn(eq(invoiceId), any(SalesReturnRequest.class))).thenReturn(salesReturn);

        SalesReturnRequest req = SalesReturnRequest.builder()
                .returnNumber("CN-0001")
                .returnDate(LocalDate.now())
                .reason("Defective")
                .refundMode("CASH")
                .items(Collections.singletonList(
                        SalesReturnRequest.SalesReturnItemRequest.builder()
                                .invoiceItemId(1L)
                                .returnQty(2)
                                .unitPrice(BigDecimal.valueOf(50))
                                .gstPercent(BigDecimal.valueOf(18))
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/invoices/" + invoiceId + "/returns")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.returnNumber").value("CN-0001"))
                .andExpect(jsonPath("$.totalRefundAmount").value(118));
    }

    @Test
    void testNextNumberEndpoints() throws Exception {
        when(invoiceService.peekNextReturnNumber(1L)).thenReturn("CN-0005");
        when(invoiceService.generateReturnNumber(1L)).thenReturn("CN-0005");

        mockMvc.perform(get("/api/invoices/next-return-number?firmId=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextNumber").value("CN-0005"))
                .andExpect(jsonPath("$.returnNumber").value("CN-0005"));

        mockMvc.perform(get("/api/returns/next-number?firmId=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextNumber").value("CN-0005"));
    }
}
