package com.billing.simple.billsoft.regression.billing;

import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.dtos.InvoiceRequest;
import com.billing.simple.billsoft.dtos.InvoiceRequestItem;
import com.billing.simple.billsoft.dtos.SalesReturnRequest;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.service.BackupService;
import com.billing.simple.billsoft.service.FirmDetailsService;
import com.billing.simple.billsoft.service.InvoicePdfService;
import com.billing.simple.billsoft.service.InvoiceService;
import com.billing.simple.billsoft.service.pdf.InvoiceMasterDataResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@DisplayName("Group 3 Backup & Restore Round-Trip Fidelity Regression Tests")
public class Group3BackupRestoreFidelityRegressionTest {

    @Autowired
    private BackupService backupService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoicePdfService invoicePdfService;

    @Autowired
    private FirmDetailsService firmService;

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private InvoiceRepository invoiceRepo;

    @Autowired
    private InvoiceItemRepository invoiceItemRepo;

    @Autowired
    private InvoicePaymentRepository invoicePaymentRepo;

    @Autowired
    private SalesReturnRepository salesReturnRepo;

    @Autowired
    private SalesReturnItemRepository salesReturnItemRepo;

    @Autowired
    private FirmDetailsRepository firmRepo;

    private Long testFirmId;
    private Customer testCustomer;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        FirmDetails firm = new FirmDetails();
        firm.setFirmName("Apex Industrial Systems");
        firm.setGstin("27ABCDE1234F1Z5");
        firm.setState("Maharashtra");
        firm.setAddressLine1("Plot 42, Hinjewadi Phase 1");
        firm.setCity("Pune");
        firm.setPincode("411057");
        firm.setPhone("9876543210");
        firm.setEmail("billing@apexind.com");

        List<FirmDetails> existing = firmRepo.findAll();
        if (existing.isEmpty()) {
            firm = firmService.create(firm);
        } else {
            firm = firmService.update(existing.get(0).getId(), firm);
        }
        testFirmId = firm.getId();
        TenantContext.setCurrentFirmId(testFirmId);

        salesReturnItemRepo.deleteAll();
        salesReturnRepo.deleteAll();
        invoicePaymentRepo.deleteAll();
        invoiceItemRepo.deleteAll();
        invoiceRepo.deleteAll();

        testCustomer = new Customer();
        testCustomer.setFirmId(testFirmId);
        testCustomer.setName("Karnataka Tech Corp");
        testCustomer.setGstin("29GHIJK5678L1Z9");
        testCustomer.setAddress("Electronic City Phase 2, Bengaluru, Karnataka");
        testCustomer.setPhone("9123456780");
        testCustomer = customerRepo.save(testCustomer);

        testProduct = Product.builder()
                .firmId(testFirmId)
                .name("Precision Sensor X100")
                .category("ELECTRONICS")
                .itemType("GOODS")
                .price(new BigDecimal("2000.00"))
                .costPrice(new BigDecimal("1200.00"))
                .stockQuantity(new BigDecimal("100.00"))
                .unit("pcs")
                .hsnCode("8543")
                .gstPercentage(new BigDecimal("18.00"))
                .build();
        testProduct = productRepo.save(testProduct);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("AUDIT-032 & AUDIT-036: Issued invoice with snapshots and GST decomposition survives full Backup -> Restore round trip")
    void testGroup3BackupRestorePreservesSnapshots() throws Exception {
        // 1. Create and issue an invoice (Inter-state: Maharashtra -> Karnataka)
        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(testFirmId);
        req.setCustomerId(testCustomer.getId());
        req.setPlaceOfSupply("29");
        req.setStatus(InvoiceStatus.UNPAID);
        req.setItems(List.of(
                InvoiceRequestItem.builder()
                        .productId(testProduct.getId())
                        .productName(testProduct.getName())
                        .qty(2)
                        .pricePerUnit(new BigDecimal("2000.00"))
                        .discountValue(new BigDecimal("400.00")) // 400 discount: taxable = 3600.00
                        .gstPercent(new BigDecimal("18.00"))     // IGST = 648.00
                        .build()
        ));

        Invoice originalInv = invoiceService.createInvoice(req);
        Long origInvId = originalInv.getId();
        String invNumber = originalInv.getInvoiceNumber();

        // Verify initial Group 3 fields on the created invoice
        assertThat(originalInv.getPlaceOfSupply()).isEqualTo("29");
        assertThat(originalInv.getIgstAmount()).isEqualByComparingTo("648.00");
        assertThat(originalInv.getCgstAmount()).isEqualByComparingTo("0.00");
        assertThat(originalInv.getSgstAmount()).isEqualByComparingTo("0.00");
        assertThat(originalInv.getCustomerNameSnapshot()).isEqualTo("Karnataka Tech Corp");
        assertThat(originalInv.getCustomerGstinSnapshot()).isEqualTo("29GHIJK5678L1Z9");
        assertThat(originalInv.getCustomerAddressSnapshot()).contains("Electronic City");
        assertThat(originalInv.getCustomerPhoneSnapshot()).isEqualTo("9123456780");
        assertThat(originalInv.getFirmNameSnapshot()).isEqualTo("Apex Industrial Systems");
        assertThat(originalInv.getFirmGstinSnapshot()).isEqualTo("27ABCDE1234F1Z5");
        assertThat(originalInv.getFirmPhoneSnapshot()).isEqualTo("9876543210");
        assertThat(originalInv.getFirmEmailSnapshot()).isEqualTo("billing@apexind.com");

        // 2. Create a Sales Return for 1 unit on this invoice
        InvoiceItem item = originalInv.getItems().get(0);
        SalesReturnRequest retReq = new SalesReturnRequest();
        retReq.setReason("Defective component");
        retReq.setRefundMode("CASH");
        retReq.setItems(List.of(
                SalesReturnRequest.SalesReturnItemRequest.builder()
                        .invoiceItemId(item.getId())
                        .productId(testProduct.getId())
                        .returnQty(1)
                        .build()
        ));

        SalesReturn ret = invoiceService.createSalesReturn(origInvId, retReq);
        assertThat(ret.getIgstAmount()).isEqualByComparingTo("324.00");
        assertThat(ret.getCgstAmount()).isEqualByComparingTo("0.00");
        assertThat(ret.getSgstAmount()).isEqualByComparingTo("0.00");

        // 3. Create backup DTO via exportData
        BackupDTO backup = backupService.exportData(testFirmId);
        assertThat(backup.getInvoices()).isNotEmpty();
        assertThat(backup.getSalesReturns()).isNotEmpty();

        // 4. Perform factory reset to completely wipe existing database state
        backupService.factoryReset();
        assertThat(invoiceRepo.count()).isZero();
        assertThat(salesReturnRepo.count()).isZero();

        // 5. Restore through the actual production restore path
        List<FirmDetails> restoredFirms = backupService.importSelectiveData(backup, null, "clone", null);
        assertThat(restoredFirms).isNotEmpty();
        Long restoredFirmId = restoredFirms.get(0).getId();
        TenantContext.setCurrentFirmId(restoredFirmId);

        // 6. Reload restored invoice and verify all Group 3 fields persisted faithfully
        Invoice restoredInv = invoiceRepo.findByInvoiceNumberAndFirmId(invNumber, restoredFirmId)
                .orElseThrow(() -> new AssertionError("Restored invoice not found: " + invNumber));

        // AUDIT-032: Place of Supply & Tax Decomposition
        assertThat(restoredInv.getPlaceOfSupply()).isEqualTo("29");
        assertThat(restoredInv.getIgstAmount()).isEqualByComparingTo("648.00");
        assertThat(restoredInv.getCgstAmount()).isEqualByComparingTo("0.00");
        assertThat(restoredInv.getSgstAmount()).isEqualByComparingTo("0.00");

        // Restored InvoiceItem Tax Decomposition
        assertThat(restoredInv.getItems()).hasSize(1);
        InvoiceItem restoredItem = restoredInv.getItems().get(0);
        assertThat(restoredItem.getIgstAmount()).isEqualByComparingTo("648.00");
        assertThat(restoredItem.getCgstAmount()).isEqualByComparingTo("0.00");
        assertThat(restoredItem.getSgstAmount()).isEqualByComparingTo("0.00");

        // AUDIT-036: Master Data Snapshots
        assertThat(restoredInv.getCustomerNameSnapshot()).isEqualTo("Karnataka Tech Corp");
        assertThat(restoredInv.getCustomerGstinSnapshot()).isEqualTo("29GHIJK5678L1Z9");
        assertThat(restoredInv.getCustomerAddressSnapshot()).contains("Electronic City");
        assertThat(restoredInv.getCustomerPhoneSnapshot()).isEqualTo("9123456780");
        assertThat(restoredInv.getFirmNameSnapshot()).isEqualTo("Apex Industrial Systems");
        assertThat(restoredInv.getFirmGstinSnapshot()).isEqualTo("27ABCDE1234F1Z5");
        assertThat(restoredInv.getFirmPhoneSnapshot()).isEqualTo("9876543210");
        assertThat(restoredInv.getFirmEmailSnapshot()).isEqualTo("billing@apexind.com");

        // Restored Sales Return tax decomposition
        List<SalesReturn> restoredReturns = salesReturnRepo.findByFirmIdOrderByReturnDateDescIdDesc(restoredFirmId);
        assertThat(restoredReturns).hasSize(1);
        SalesReturn restoredReturn = restoredReturns.get(0);
        assertThat(restoredReturn.getIgstAmount()).isEqualByComparingTo("324.00");
        assertThat(restoredReturn.getCgstAmount()).isEqualByComparingTo("0.00");
        assertThat(restoredReturn.getSgstAmount()).isEqualByComparingTo("0.00");

        assertThat(restoredReturn.getItems()).hasSize(1);
        SalesReturnItem restoredRetItem = restoredReturn.getItems().get(0);
        assertThat(restoredRetItem.getIgstAmount()).isEqualByComparingTo("324.00");
        assertThat(restoredRetItem.getCgstAmount()).isEqualByComparingTo("0.00");
        assertThat(restoredRetItem.getSgstAmount()).isEqualByComparingTo("0.00");

        // 7. Verify PDF rendering still uses the restored historical snapshot values even if live firm/customer mutate
        Customer liveCustomer = customerRepo.findById(restoredInv.getCustomer().getId()).orElseThrow();
        liveCustomer.setName("MUTATED CUSTOMER NAME");
        customerRepo.save(liveCustomer);

        FirmDetails liveFirm = firmRepo.findById(restoredFirmId).orElseThrow();
        liveFirm.setFirmName("MUTATED FIRM NAME");
        firmRepo.save(liveFirm);

        // Snapshot resolution verifies historical integrity
        assertThat(InvoiceMasterDataResolver.getCustomerName(restoredInv)).isEqualTo("Karnataka Tech Corp");
        assertThat(InvoiceMasterDataResolver.getFirmName(restoredInv, liveFirm)).isEqualTo("Apex Industrial Systems");

        byte[] pdfBytes = invoicePdfService.generatePdf(restoredInv, "A4");
        assertThat(pdfBytes).isNotEmpty();
        assertThat(pdfBytes.length).isGreaterThan(1000);
    }

    @Test
    @DisplayName("Backward Compatibility: Restore legacy backup lacking Group 3 fields without error or corruption")
    void testBackwardCompatibilityWithoutGroup3Fields() throws Exception {
        // Construct a legacy BackupDTO simulating older versions without AUDIT-032 / AUDIT-036 fields
        BackupDTO legacyBackup = new BackupDTO();
        java.util.Map<String, Object> meta = new java.util.HashMap<>();
        meta.put("version", "1.0.0");
        meta.put("type", "SINGLE_FIRM");
        meta.put("sourceFirmId", 99L);
        meta.put("backupSourceId", "firm_99");
        meta.put("exportDate", LocalDateTime.now().toString());
        meta.put("firmId", 99L);
        legacyBackup.setMetadata(meta);

        FirmDetails legacyFirm = new FirmDetails();
        legacyFirm.setId(99L);
        legacyFirm.setFirmName("Legacy Hardware Store");
        legacyFirm.setGstin("27LEGACY1234L1Z1");
        legacyFirm.setState("Maharashtra");
        legacyFirm.setCity("Mumbai");
        legacyFirm.setAddressLine1("Station Road");
        legacyFirm.setPhone("9988776655");
        legacyBackup.setFirmDetails(legacyFirm);

        Customer legacyCustomer = new Customer();
        legacyCustomer.setId(88L);
        legacyCustomer.setFirmId(99L);
        legacyCustomer.setName("Old Time Customer");
        legacyCustomer.setPhone("9876000000");
        legacyCustomer.setAddress("Station Road, Mumbai, Maharashtra");
        legacyBackup.setCustomers(List.of(legacyCustomer));

        Product legacyProd = Product.builder()
                .id(77L)
                .firmId(99L)
                .name("Legacy Cable")
                .price(new BigDecimal("500.00"))
                .costPrice(new BigDecimal("300.00"))
                .stockQuantity(new BigDecimal("50.00"))
                .unit("m")
                .build();
        legacyBackup.setProducts(List.of(legacyProd));

        Invoice legacyInv = new Invoice();
        legacyInv.setId(66L);
        legacyInv.setFirmId(99L);
        legacyInv.setInvoiceNumber("INV-LEGACY-001");
        legacyInv.setInvoiceDate(LocalDateTime.now().minusDays(100));
        legacyInv.setCustomer(legacyCustomer);
        legacyInv.setTotalAmount(new BigDecimal("500.00"));
        legacyInv.setSubtotalWithoutTax(new BigDecimal("500.00"));
        legacyInv.setTotalTax(BigDecimal.ZERO);
        legacyInv.setTotalDiscount(BigDecimal.ZERO);
        legacyInv.setStatus(InvoiceStatus.PAID);
        legacyInv.setPaid(true);

        // Group 3 fields are deliberately NULL in legacy backups
        legacyInv.setPlaceOfSupply(null);
        legacyInv.setCgstAmount(null);
        legacyInv.setSgstAmount(null);
        legacyInv.setIgstAmount(null);
        legacyInv.setCustomerNameSnapshot(null);
        legacyInv.setCustomerGstinSnapshot(null);
        legacyInv.setCustomerAddressSnapshot(null);
        legacyInv.setCustomerPhoneSnapshot(null);
        legacyInv.setFirmNameSnapshot(null);
        legacyInv.setFirmGstinSnapshot(null);
        legacyInv.setFirmAddressSnapshot(null);
        legacyInv.setFirmPhoneSnapshot(null);
        legacyInv.setFirmEmailSnapshot(null);

        InvoiceItem legacyItem = new InvoiceItem();
        legacyItem.setInvoice(legacyInv);
        legacyItem.setProduct(legacyProd);
        legacyItem.setProductName(legacyProd.getName());
        legacyItem.setQty(1);
        legacyItem.setPricePerUnit(new BigDecimal("500.00"));
        legacyItem.setAmountWithoutTax(new BigDecimal("500.00"));
        legacyItem.setTaxableAmount(new BigDecimal("500.00"));
        legacyItem.setLineTotal(new BigDecimal("500.00"));
        legacyItem.setGstPercent(BigDecimal.ZERO);
        legacyItem.setGstAmount(BigDecimal.ZERO);
        legacyItem.setCgstAmount(null);
        legacyItem.setSgstAmount(null);
        legacyItem.setIgstAmount(null);
        legacyInv.setItems(new ArrayList<>(List.of(legacyItem)));

        legacyBackup.setInvoices(List.of(legacyInv));

        // Restore legacy backup
        List<FirmDetails> restoredFirms = backupService.importSelectiveData(legacyBackup, null, "clone", null);
        assertThat(restoredFirms).isNotEmpty();
        Long restoredFirmId = restoredFirms.get(0).getId();

        Invoice restored = invoiceRepo.findByInvoiceNumberAndFirmId("INV-LEGACY-001", restoredFirmId).orElseThrow();
        assertThat(restored.getTotalAmount()).isEqualByComparingTo("500.00");

        // Verify null snapshot fallback resolves seamlessly to live master entity
        FirmDetails currentFirm = firmRepo.findById(restoredFirmId).orElseThrow();
        assertThat(InvoiceMasterDataResolver.getCustomerName(restored)).isEqualTo("Old Time Customer");
        assertThat(InvoiceMasterDataResolver.getFirmName(restored, currentFirm)).isEqualTo("Legacy Hardware Store");

        // Verify PDF generation does not throw NPE
        byte[] pdfBytes = invoicePdfService.generatePdf(restored, "A4");
        assertThat(pdfBytes).isNotEmpty();
    }
}
