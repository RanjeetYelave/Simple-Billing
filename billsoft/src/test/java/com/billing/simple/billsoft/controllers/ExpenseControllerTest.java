package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.dtos.PageResponse;
import com.billing.simple.billsoft.entities.Expense;
import com.billing.simple.billsoft.service.ExpenseService;
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
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@AutoConfigureMockMvc
class ExpenseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExpenseService service;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testListByFirmUnpaginated() throws Exception {
        Expense e = new Expense();
        e.setId(1L);
        e.setTitle("Office Supplies");
        e.setAmount(new BigDecimal("250.00"));
        e.setFirmId(1L);

        when(service.getExpensesByFirm(1L)).thenReturn(List.of(e));

        mockMvc.perform(get("/api/expenses?firmId=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Office Supplies"));
    }

    @Test
    void testListByFirmPaginated() throws Exception {
        Expense e = new Expense();
        e.setId(1L);
        e.setTitle("Electricity Bill");
        e.setAmount(new BigDecimal("1200.00"));
        e.setFirmId(1L);

        PageResponse<Expense> pageResponse = PageResponse.<Expense>builder()
                .content(List.of(e))
                .page(0)
                .size(25)
                .totalElements(1)
                .totalPages(1)
                .build();

        when(service.getPaginatedExpenses(eq(1L), any(), any(), any())).thenReturn(pageResponse);

        mockMvc.perform(get("/api/expenses?firmId=1&page=0&size=25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Electricity Bill"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void testCreateExpense() throws Exception {
        Expense e = new Expense();
        e.setId(10L);
        e.setTitle("Fuel");
        e.setAmount(new BigDecimal("500.00"));
        e.setExpenseDate(LocalDate.now());

        when(service.createExpense(any(Expense.class))).thenReturn(e);

        mockMvc.perform(post("/api/expenses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(e)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.title").value("Fuel"));
    }

    @Test
    void testSummary() throws Exception {
        when(service.getSummaryByFirm(1L)).thenReturn(Map.of("totalAllTime", new BigDecimal("5000.00"), "count", 10));

        mockMvc.perform(get("/api/expenses/summary?firmId=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(10));
    }
}
