package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.dtos.debtor.*;
import com.billing.simple.billsoft.entities.CustomerCollectionActivity;
import com.billing.simple.billsoft.entities.CustomerPromiseToPay;
import com.billing.simple.billsoft.entities.PromiseStatus;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;
import com.billing.simple.billsoft.service.DebtorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DebtorControllerTest {

    @Mock
    private DebtorService debtorService;

    @InjectMocks
    private DebtorController controller;

    private final Long firmId = 1L;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TenantContext.setCurrentFirmId(firmId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testGetOverviewEndpoint() {
        DebtorsOverviewDto mockDto = DebtorsOverviewDto.builder()
                .totalOutstanding(new BigDecimal("50000.00"))
                .overdueAmount(new BigDecimal("12000.00"))
                .totalDebtorsCount(5)
                .build();

        when(debtorService.getOverview(firmId)).thenReturn(mockDto);

        ResponseEntity<DebtorsOverviewDto> resp = controller.getOverview(firmId, null);
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertEquals(new BigDecimal("50000.00"), resp.getBody().getTotalOutstanding());
    }

    @Test
    void testGetDebtorsListEndpoint() {
        DebtorListItemDto item = DebtorListItemDto.builder()
                .customerId(10L)
                .customerName("Acme Corp")
                .outstandingAmount(new BigDecimal("15000.00"))
                .riskLevel("MEDIUM")
                .build();

        when(debtorService.getDebtors(firmId, null, "all", "default", "desc"))
                .thenReturn(List.of(item));

        ResponseEntity<List<DebtorListItemDto>> resp = controller.getDebtors(firmId, null, null, "all", "default", "desc");
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertEquals(1, resp.getBody().size());
        assertEquals("Acme Corp", resp.getBody().get(0).getCustomerName());
    }

    @Test
    void testCrossFirmSecurityBlocked() {
        // Active firm is 1, but client tries to pass firmId 2
        assertThrows(TenantSecurityException.class, () -> {
            controller.getOverview(2L, null);
        });
    }

    @Test
    void testCreatePromiseEndpoint() {
        CreatePromiseRequest req = CreatePromiseRequest.builder()
                .customerId(10L)
                .promisedAmount(new BigDecimal("5000.00"))
                .promisedDate(LocalDate.now().plusDays(3))
                .build();

        CustomerPromiseToPay created = CustomerPromiseToPay.builder()
                .id(1L)
                .firmId(firmId)
                .customerId(10L)
                .promisedAmount(new BigDecimal("5000.00"))
                .status(PromiseStatus.PROMISED)
                .build();

        when(debtorService.createPromise(eq(firmId), any(CreatePromiseRequest.class), anyString()))
                .thenReturn(created);

        ResponseEntity<CustomerPromiseToPay> resp = controller.createPromise(firmId, null, req, () -> "AdminUser");
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertEquals(1L, resp.getBody().getId());
    }

    @Test
    void testExportCsvEndpoint() {
        when(debtorService.exportDebtorsCsv(firmId)).thenReturn("Customer Name,Phone".getBytes());

        ResponseEntity<byte[]> resp = controller.exportCsv(firmId, null);
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertTrue(resp.getHeaders().getContentType().toString().contains("csv"));
    }
}
