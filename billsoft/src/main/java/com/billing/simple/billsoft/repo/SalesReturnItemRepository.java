package com.billing.simple.billsoft.repo;

import com.billing.simple.billsoft.entities.SalesReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SalesReturnItemRepository extends JpaRepository<SalesReturnItem, Long> {

    @Modifying
    @Query("UPDATE SalesReturnItem sri SET sri.invoiceItem = null WHERE sri.invoiceItem.id IN :itemIds")
    void nullifyInvoiceItemReferences(@Param("itemIds") List<Long> itemIds);

    @Modifying
    @Query("UPDATE SalesReturnItem sri SET sri.invoiceItem = null WHERE sri.salesReturn.invoice.id = :invoiceId")
    void nullifyInvoiceItemReferencesByInvoiceId(@Param("invoiceId") Long invoiceId);

    @Query("SELECT sri.invoiceItem.id, SUM(COALESCE(sri.returnQty, 0)) FROM SalesReturnItem sri WHERE sri.salesReturn.invoice.id = :invoiceId AND sri.invoiceItem IS NOT NULL GROUP BY sri.invoiceItem.id")
    List<Object[]> sumReturnedQtyByInvoiceItemIdForInvoice(@Param("invoiceId") Long invoiceId);

    @Query("SELECT sri.product.id, SUM(COALESCE(sri.returnQty, 0)) FROM SalesReturnItem sri WHERE sri.salesReturn.invoice.id = :invoiceId AND sri.product IS NOT NULL GROUP BY sri.product.id")
    List<Object[]> sumReturnedQtyByProductIdForInvoice(@Param("invoiceId") Long invoiceId);
}
