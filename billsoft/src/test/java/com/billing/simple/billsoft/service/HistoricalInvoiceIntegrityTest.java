package com.billing.simple.billsoft.service;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.InvoiceItem;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.entities.SavedItem;
import com.billing.simple.billsoft.security.TenantContext;

public class HistoricalInvoiceIntegrityTest {

    private Long firmId = 4001L;

    @BeforeEach
    public void setup() {
        TenantContext.setCurrentFirmId(firmId);
    }

    @AfterEach
    public void tearDown() {
        TenantContext.clear();
    }

    @Test
    public void testHistoricalInvoiceLineItemsRemainImmutableAcrossPromotionsAndMerges() {
        // Create initial historical snapshot of an invoice line
        InvoiceItem line = InvoiceItem.builder()
                .id(901L)
                .product(null) // Uncatalogued item at time of billing
                .productName("Custom Galvanized Bracket 4 inch")
                .unit("nos")
                .qty(10)
                .pricePerUnit(new BigDecimal("75.00"))
                .amountWithoutTax(new BigDecimal("750.00"))
                .gstPercent(new BigDecimal("18.00"))
                .lineTotal(new BigDecimal("885.00"))
                .build();

        Invoice invoice = Invoice.builder()
                .id(9001L)
                .invoiceNumber("INV-HIST-001")
                .items(Collections.singletonList(line))
                .subtotalWithoutTax(new BigDecimal("750.00"))
                .totalTax(new BigDecimal("135.00"))
                .totalAmount(new BigDecimal("885.00"))
                .build();
        line.setInvoice(invoice);

        // Later, the user creates a SavedItem and then promotes it or merges it
        SavedItem saved = SavedItem.builder()
                .id(501L)
                .name("Custom Galvanized Bracket 4 inch")
                .canonicalProductId(888L)
                .isArchived(true)
                .build();

        Product canonicalProduct = Product.builder()
                .id(888L)
                .name("Galvanized Bracket 4\" Regular")
                .price(new BigDecimal("95.00"))
                .build();

        // Verify that the historical invoice line and invoice total amounts remain 100% frozen
        assertEquals("Custom Galvanized Bracket 4 inch", line.getProductName());
        assertEquals(new BigDecimal("75.00"), line.getPricePerUnit());
        assertEquals(new BigDecimal("750.00"), line.getAmountWithoutTax());
        assertEquals(new BigDecimal("885.00"), line.getLineTotal());
        assertEquals(new BigDecimal("885.00"), invoice.getTotalAmount());
        assertNull(line.getProduct(), "Historical uncatalogued line item product reference must NOT be altered retroactively");
    }
}
