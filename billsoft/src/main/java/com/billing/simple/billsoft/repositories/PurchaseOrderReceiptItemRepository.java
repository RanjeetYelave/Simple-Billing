package com.billing.simple.billsoft.repositories;

import com.billing.simple.billsoft.entities.PurchaseOrderReceiptItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchaseOrderReceiptItemRepository extends JpaRepository<PurchaseOrderReceiptItem, Long> {

    List<PurchaseOrderReceiptItem> findByReceiptId(Long receiptId);

    List<PurchaseOrderReceiptItem> findByProductId(Long productId);

    List<PurchaseOrderReceiptItem> findByPurchaseOrderItemId(Long purchaseOrderItemId);
}
