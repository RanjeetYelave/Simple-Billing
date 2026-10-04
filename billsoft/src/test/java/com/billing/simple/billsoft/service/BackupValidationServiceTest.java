package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.exception.BackupValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class BackupValidationServiceTest {

    private BackupValidationService validationService;

    @BeforeEach
    public void setUp() {
        validationService = new BackupValidationService();
    }

    private BackupDTO createValidBackup() {
        BackupDTO backup = new BackupDTO();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("version", "2.1.0");
        metadata.put("type", "FULL_SYSTEM_BACKUP");
        backup.setMetadata(metadata);

        FirmDetails firm1 = new FirmDetails();
        firm1.setId(1L);
        firm1.setFirmName("Test Firm 1");

        FirmDetails firm2 = new FirmDetails();
        firm2.setId(2L);
        firm2.setFirmName("Test Firm 2");

        backup.setAllFirms(Arrays.asList(firm1, firm2));

        Invoice inv1 = new Invoice();
        inv1.setId(101L);
        inv1.setFirmId(1L);
        inv1.setInvoiceNumber("INV-2026-0001");
        inv1.setInvoiceDate(LocalDateTime.now());
        inv1.setTotalAmount(new BigDecimal("500.00"));

        InvoiceItem item1 = new InvoiceItem();
        item1.setProductName("Item A");
        item1.setQty(2);
        item1.setPricePerUnit(new BigDecimal("250.00"));
        inv1.setItems(Collections.singletonList(item1));

        Invoice inv2 = new Invoice();
        inv2.setId(102L);
        inv2.setFirmId(1L);
        inv2.setInvoiceNumber("INV-2026-0002");
        inv2.setInvoiceDate(LocalDateTime.now());
        inv2.setTotalAmount(new BigDecimal("100.00"));

        backup.setInvoices(Arrays.asList(inv1, inv2));
        return backup;
    }

    @Test
    public void testValidate_ValidBackup_Passes() {
        BackupDTO backup = createValidBackup();
        assertDoesNotThrow(() -> validationService.validate(backup));
    }

    @Test
    public void testValidate_NullBackup_ThrowsException() {
        BackupValidationException ex = assertThrows(BackupValidationException.class, () -> validationService.validate(null));
        assertEquals("MALFORMED_BACKUP", ex.getErrorCode());
    }

    @Test
    public void testValidate_MissingMetadata_ThrowsException() {
        BackupDTO backup = createValidBackup();
        backup.setMetadata(null);
        BackupValidationException ex = assertThrows(BackupValidationException.class, () -> validationService.validate(backup));
        assertEquals("MISSING_METADATA", ex.getErrorCode());
    }

    @Test
    public void testValidate_DuplicateInvoiceNumbersInSameFirm_ThrowsException() {
        BackupDTO backup = createValidBackup();
        Invoice duplicateInv = new Invoice();
        duplicateInv.setId(103L);
        duplicateInv.setFirmId(1L);
        duplicateInv.setInvoiceNumber("INV-2026-0001"); // DUPLICATE for firm 1
        duplicateInv.setInvoiceDate(LocalDateTime.now());
        duplicateInv.setTotalAmount(new BigDecimal("300.00"));

        List<Invoice> invoices = new ArrayList<>(backup.getInvoices());
        invoices.add(duplicateInv);
        backup.setInvoices(invoices);

        BackupValidationException ex = assertThrows(BackupValidationException.class, () -> validationService.validate(backup));
        assertEquals("BACKUP_VALIDATION_ERROR", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("INV-2026-0001"));
    }

    @Test
    public void testValidate_DistinctFirmsSameInvoiceNumber_PassesMultiTenantIsolation() {
        BackupDTO backup = createValidBackup();
        Invoice firm2Inv = new Invoice();
        firm2Inv.setId(201L);
        firm2Inv.setFirmId(2L); // Different firm!
        firm2Inv.setInvoiceNumber("INV-2026-0001"); // Same number, but distinct firm
        firm2Inv.setInvoiceDate(LocalDateTime.now());
        firm2Inv.setTotalAmount(new BigDecimal("300.00"));

        List<Invoice> invoices = new ArrayList<>(backup.getInvoices());
        invoices.add(firm2Inv);
        backup.setInvoices(invoices);

        assertDoesNotThrow(() -> validationService.validate(backup));
    }

    @Test
    public void testValidate_DuplicatePoNumbersInSameFirm_ThrowsException() {
        BackupDTO backup = createValidBackup();
        PurchaseOrder po1 = PurchaseOrder.builder().id(1L).firmId(1L).poNumber("PO-001").build();
        PurchaseOrder po2 = PurchaseOrder.builder().id(2L).firmId(1L).poNumber("PO-001").build(); // Duplicate
        backup.setPurchaseOrders(Arrays.asList(po1, po2));

        BackupValidationException ex = assertThrows(BackupValidationException.class, () -> validationService.validate(backup));
        assertEquals("BACKUP_VALIDATION_ERROR", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("PO-001"));
    }

    @Test
    public void testValidate_DuplicateReturnNumbersInSameFirm_ThrowsException() {
        BackupDTO backup = createValidBackup();
        SalesReturn sr1 = new SalesReturn();
        sr1.setId(1L);
        sr1.setFirmId(1L);
        sr1.setReturnNumber("RET-001");

        SalesReturn sr2 = new SalesReturn();
        sr2.setId(2L);
        sr2.setFirmId(1L);
        sr2.setReturnNumber("RET-001"); // Duplicate
        backup.setSalesReturns(Arrays.asList(sr1, sr2));

        BackupValidationException ex = assertThrows(BackupValidationException.class, () -> validationService.validate(backup));
        assertEquals("BACKUP_VALIDATION_ERROR", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("RET-001"));
    }
}
