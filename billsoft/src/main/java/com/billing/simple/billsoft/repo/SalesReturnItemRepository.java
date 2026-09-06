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
}
