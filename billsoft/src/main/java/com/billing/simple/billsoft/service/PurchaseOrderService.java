package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.entities.PurchaseOrder;
import com.billing.simple.billsoft.entities.PurchaseOrderStatus;

import java.util.List;
import java.util.Optional;

public interface PurchaseOrderService {

    PurchaseOrder createPurchaseOrder(PurchaseOrder purchaseOrder);

    PurchaseOrder updatePurchaseOrder(Long id, PurchaseOrder updated);

    List<PurchaseOrder> getPurchaseOrdersByFirm(Long firmId);

    com.billing.simple.billsoft.dtos.PageResponse<PurchaseOrder> getPaginatedPurchaseOrders(Long firmId, org.springframework.data.domain.Pageable pageable);

    List<PurchaseOrder> getPurchaseOrdersByParty(Long firmId, Long partyId);

    Optional<PurchaseOrder> getPurchaseOrderById(Long id, Long firmId);

    PurchaseOrder updateStatus(Long id, Long firmId, PurchaseOrderStatus status);

    void deletePurchaseOrder(Long id, Long firmId);

    String generateNextPoNumber(Long firmId);

    byte[] generatePoPdf(Long id, Long firmId) throws Exception;

    PurchaseOrder recordPoPayment(Long id, Long firmId, java.math.BigDecimal amount, java.time.LocalDate paymentDate, String paymentMode, String referenceNumber, String notes);

    List<PurchaseOrder> createPurchaseOrdersBatch(com.billing.simple.billsoft.dtos.BatchPurchaseOrderRequest request, Long firmId);

    byte[] generateMergedPoPdf(List<Long> poIds, Long firmId) throws Exception;

    byte[] generateZipBundle(List<Long> poIds, Long firmId) throws Exception;

    java.util.Map<Long, com.billing.simple.billsoft.dtos.ProductVendorHistoryDto> getProductVendorHistory(Long firmId);
}
