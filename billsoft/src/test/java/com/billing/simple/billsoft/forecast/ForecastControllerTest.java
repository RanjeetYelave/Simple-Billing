package com.billing.simple.billsoft.forecast;

import com.billing.simple.billsoft.forecast.controller.ForecastController;
import com.billing.simple.billsoft.forecast.dto.*;
import com.billing.simple.billsoft.forecast.service.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ForecastControllerTest {

    @Mock
    private RestockAdvisoryService restockAdvisoryService;
    @Mock
    private ForecastProductConfigService configService;
    @Mock
    private ForecastIngestionService ingestionService;
    @Mock
    private ForecastAggregationService aggregationService;
    @Mock
    private ForecastEngineService engineService;

    @InjectMocks
    private ForecastController controller;

    private Long firmId = 1L;
    private Long productId = 100L;

    @Test
    @DisplayName("GET /api/forecast/products/{id}/insights returns product insights")
    void testGetProductInsights() {
        ProductInsightResponse mockResponse = ProductInsightResponse.builder()
                .productId(productId)
                .productName("Test Widget")
                .netSales(BigDecimal.valueOf(5000.0))
                .weightedAverageSellingPrice(BigDecimal.valueOf(100.0))
                .build();

        when(restockAdvisoryService.getInsights(firmId, productId)).thenReturn(mockResponse);

        ResponseEntity<?> res = controller.getProductInsights(productId, firmId, null);
        assertNotNull(res);
        assertEquals(200, res.getStatusCode().value());
        ProductInsightResponse body = (ProductInsightResponse) res.getBody();
        assertEquals("Test Widget", body.getProductName());
    }

    @Test
    @DisplayName("GET /api/forecast/products/{id}/trends returns trends response")
    void testGetProductTrends() {
        HistoricalTrendsResponse mockResponse = HistoricalTrendsResponse.builder()
                .productId(productId)
                .range("90d")
                .points(Collections.emptyList())
                .build();

        when(restockAdvisoryService.getTrends(firmId, productId, "90d")).thenReturn(mockResponse);

        ResponseEntity<?> res = controller.getProductTrends(productId, "90d", firmId, null);
        assertNotNull(res);
        assertEquals(200, res.getStatusCode().value());
    }

    @Test
    @DisplayName("GET /api/forecast/products/{id}/trajectory returns forecast trajectory")
    void testGetForecastTrajectory() {
        ForecastTrajectoryResponse mockResponse = ForecastTrajectoryResponse.builder()
                .productId(productId)
                .selectedModel("SES")
                .confidenceLevel("HIGH")
                .build();

        when(restockAdvisoryService.getTrajectory(firmId, productId)).thenReturn(mockResponse);

        ResponseEntity<?> res = controller.getProductTrajectory(productId, firmId, null);
        assertNotNull(res);
        assertEquals(200, res.getStatusCode().value());
        ForecastTrajectoryResponse body = (ForecastTrajectoryResponse) res.getBody();
        assertEquals("SES", body.getSelectedModel());
    }

    @Test
    @DisplayName("GET /api/forecast/products/{id}/restock returns product restock recommendation")
    void testGetProductRestock() {
        RestockRecommendationDto mockDto = RestockRecommendationDto.builder()
                .productId(productId)
                .urgencyStatus("REORDER_NEEDED")
                .recommendedOrderQty(BigDecimal.valueOf(15.0))
                .build();

        when(restockAdvisoryService.getRecommendationForProduct(firmId, productId)).thenReturn(mockDto);

        ResponseEntity<?> res = controller.getProductRestock(productId, firmId, null);
        assertNotNull(res);
        assertEquals(200, res.getStatusCode().value());
    }

    @Test
    @DisplayName("GET /api/forecast/products/{id}/restock/explanation returns explainability payload")
    void testGetProductRestockExplanation() {
        RestockExplanationDto mockDto = RestockExplanationDto.builder()
                .productId(productId)
                .explanationText("Advisory explanation test")
                .build();

        when(restockAdvisoryService.getExplanation(firmId, productId)).thenReturn(mockDto);

        ResponseEntity<?> res = controller.getProductRestockExplanation(productId, firmId, null);
        assertNotNull(res);
        assertEquals(200, res.getStatusCode().value());
    }

    @Test
    @DisplayName("GET & PUT /api/forecast/products/{id}/config manages configuration")
    void testGetAndUpdateProductConfig() {
        ForecastConfigDto mockConfig = ForecastConfigDto.builder()
                .productId(productId)
                .leadTimeDays(10)
                .minimumOrderQty(BigDecimal.valueOf(5.0))
                .build();

        when(configService.getConfigDto(firmId, productId)).thenReturn(mockConfig);
        when(configService.updateConfig(eq(firmId), eq(productId), any())).thenReturn(mockConfig);

        ResponseEntity<?> resGet = controller.getProductConfig(productId, firmId, null);
        assertEquals(200, resGet.getStatusCode().value());

        ResponseEntity<?> resPut = controller.updateProductConfig(productId, mockConfig, firmId, null);
        assertEquals(200, resPut.getStatusCode().value());
    }

    @Test
    @DisplayName("GET /api/forecast/restock/recommendations returns active recommendations page")
    void testGetRestockRecommendations() {
        RestockRecommendationDto dto = RestockRecommendationDto.builder()
                .productId(productId)
                .urgencyStatus("CRITICAL")
                .recommendedOrderQty(BigDecimal.valueOf(20.0))
                .build();

        Page<RestockRecommendationDto> page = new PageImpl<>(Collections.singletonList(dto));
        when(restockAdvisoryService.getActiveRecommendations(eq(firmId), any(), any(Pageable.class))).thenReturn(page);

        ResponseEntity<?> res = controller.getRestockRecommendations(null, 0, 25, firmId, null);
        assertNotNull(res);
        assertEquals(200, res.getStatusCode().value());
        Page<RestockRecommendationDto> body = (Page<RestockRecommendationDto>) res.getBody();
        assertEquals(1, body.getContent().size());
        assertEquals("CRITICAL", body.getContent().get(0).getUrgencyStatus());
    }

    @Test
    @DisplayName("POST /api/forecast/admin/sync triggers full pipeline sync")
    void testTriggerFullHistoricalSync() {
        when(ingestionService.syncAllInvoicesForFirm(firmId)).thenReturn(25);
        when(ingestionService.syncAllSalesReturnsForFirm(firmId)).thenReturn(2);
        when(aggregationService.aggregateAllProductsForFirm(firmId)).thenReturn(50);
        when(engineService.generateForecastsForCatalog(firmId)).thenReturn(10);
        when(restockAdvisoryService.generateCatalogRecommendations(firmId)).thenReturn(5);

        ResponseEntity<?> res = controller.triggerFirmSync(firmId, null);
        assertNotNull(res);
        assertEquals(200, res.getStatusCode().value());
        ForecastAdminSyncResponse body = (ForecastAdminSyncResponse) res.getBody();
        assertTrue(body.isSuccess());
        assertEquals(25, body.getInvoicesProcessed());
    }

    @Test
    @DisplayName("Tenant Isolation: Missing Firm ID returns 400 Bad Request")
    void testMissingFirmIdReturnsBadRequest() {
        ResponseEntity<?> res = controller.getProductInsights(productId, null, null);
        assertEquals(400, res.getStatusCode().value());
    }

    @Test
    @DisplayName("Tenant IDOR: GET & PUT config return 404 when product does not belong to active firm")
    void testConfigEndpointsReturn404ForCrossTenantProduct() {
        when(configService.getConfigDto(firmId, productId))
                .thenThrow(new IllegalArgumentException("Product not found: " + productId));
        when(configService.updateConfig(eq(firmId), eq(productId), any()))
                .thenThrow(new IllegalArgumentException("Product not found: " + productId));

        ResponseEntity<?> resGet = controller.getProductConfig(productId, firmId, null);
        assertEquals(404, resGet.getStatusCode().value());

        ResponseEntity<?> resPut = controller.updateProductConfig(productId, new ForecastConfigDto(), firmId, null);
        assertEquals(404, resPut.getStatusCode().value());
    }

    @Test
    @DisplayName("Tenant IDOR: Trends and Trajectory return 404 when product does not belong to active firm")
    void testTrendsAndTrajectoryReturn404ForCrossTenantProduct() {
        when(restockAdvisoryService.getTrends(firmId, productId, "90d"))
                .thenThrow(new IllegalArgumentException("Product not found: " + productId));
        when(restockAdvisoryService.getTrajectory(firmId, productId))
                .thenThrow(new IllegalArgumentException("Product not found: " + productId));

        ResponseEntity<?> resTrends = controller.getProductTrends(productId, "90d", firmId, null);
        assertEquals(404, resTrends.getStatusCode().value());

        ResponseEntity<?> resTraj = controller.getProductTrajectory(productId, firmId, null);
        assertEquals(404, resTraj.getStatusCode().value());
    }
}
