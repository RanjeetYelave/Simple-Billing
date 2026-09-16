package com.billing.simple.billsoft.repo;

import com.billing.simple.billsoft.entities.InvoiceItem;
import com.billing.simple.billsoft.entities.InvoiceStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, Long> {

    @Query("SELECT item.product.id, item.product.name, SUM(item.qty), SUM(item.lineTotal) " +
           "FROM InvoiceItem item " +
           "WHERE item.invoice.firmId = :firmId AND item.invoice.status IN :statuses AND item.product IS NOT NULL " +
           "GROUP BY item.product.id, item.product.name " +
           "ORDER BY SUM(item.qty) DESC")
    List<Object[]> findTopProductsByFirmId(@Param("firmId") Long firmId, @Param("statuses") List<InvoiceStatus> statuses, Pageable pageable);

    @Query("SELECT item.product.id, item.product.name, SUM(item.qty), SUM(item.lineTotal) " +
           "FROM InvoiceItem item " +
           "WHERE item.invoice.status IN :statuses AND item.product IS NOT NULL " +
           "GROUP BY item.product.id, item.product.name " +
           "ORDER BY SUM(item.qty) DESC")
    List<Object[]> findTopProductsAll(@Param("statuses") List<InvoiceStatus> statuses, Pageable pageable);

    List<InvoiceItem> findByInvoice_IdAndInvoice_FirmId(Long invoiceId, Long firmId);
}
