package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.entities.PurchaseOrder;
import com.billing.simple.billsoft.entities.PurchaseOrderStatus;

import java.util.List;
import java.util.Optional;

public interface PurchaseOrderService {

    PurchaseOrder createPurchaseOrder(PurchaseOrder purchaseOrder);

    PurchaseOrder updatePurchaseOrder(Long id, PurchaseOrder updated);

    List<PurchaseOrder> getPurchaseOrdersByFirm(Long firmId);

    List<PurchaseOrder> getPurchaseOrdersByParty(Long firmId, Long partyId);

    Optional<PurchaseOrder> getPurchaseOrderById(Long id, Long firmId);

    PurchaseOrder updateStatus(Long id, Long firmId, PurchaseOrderStatus status);

    void deletePurchaseOrder(Long id, Long firmId);

    String generateNextPoNumber(Long firmId);

    byte[] generatePoPdf(Long id, Long firmId) throws Exception;

    PurchaseOrder recordPoPayment(Long id, Long firmId, java.math.BigDecimal amount, java.time.LocalDate paymentDate, String paymentMode, String referenceNumber, String notes);
}
