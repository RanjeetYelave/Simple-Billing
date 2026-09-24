package com.billing.simple.billsoft.kpi;

import com.billing.simple.billsoft.kpi.dto.CustomerKpiSummary;
import com.billing.simple.billsoft.kpi.dto.KpiDashboardResponse;
import com.billing.simple.billsoft.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@AutoConfigureMockMvc
class KpiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KpiService kpiService;

    private final Long TEST_FIRM = 101L;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentFirmId(TEST_FIRM);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testGetDashboardKpis() throws Exception {
        KpiDashboardResponse resp = KpiDashboardResponse.builder()
                .totalBusiness(250000.0)
                .totalPaid(200000.0)
                .totalPending(50000.0)
                .unpaidInvoiceCount(3L)
                .overdueInvoiceCount(1L)
                .vendorPayables(15000.0)
                .totalInventoryCostValue(80000.0)
                .build();

        when(kpiService.getDashboardKpis(TEST_FIRM)).thenReturn(resp);

        mockMvc.perform(get("/api/kpis/dashboard")
                        .header("X-Firm-Id", String.valueOf(TEST_FIRM))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBusiness").value(250000.0))
                .andExpect(jsonPath("$.totalPaid").value(200000.0))
                .andExpect(jsonPath("$.totalPending").value(50000.0))
                .andExpect(jsonPath("$.unpaidInvoiceCount").value(3))
                .andExpect(jsonPath("$.overdueInvoiceCount").value(1))
                .andExpect(jsonPath("$.vendorPayables").value(15000.0))
                .andExpect(jsonPath("$.totalInventoryCostValue").value(80000.0));
    }

    @Test
    void testGetCustomerKpis() throws Exception {
        CustomerKpiSummary s = CustomerKpiSummary.builder()
                .customerId(1L)
                .customerName("Acme Corp")
                .totalBilled(50000.0)
                .totalPaid(30000.0)
                .totalReturns(0.0)
                .netBalance(20000.0)
                .invoiceCount(2L)
                .unpaidInvoiceCount(1L)
                .overdueInvoiceCount(0L)
                .build();

        when(kpiService.getCustomerKpiSummaries(TEST_FIRM)).thenReturn(List.of(s));

        mockMvc.perform(get("/api/kpis/customers")
                        .header("X-Firm-Id", String.valueOf(TEST_FIRM))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].customerName").value("Acme Corp"))
                .andExpect(jsonPath("$[0].netBalance").value(20000.0))
                .andExpect(jsonPath("$[0].totalBilled").value(50000.0));
    }
}
