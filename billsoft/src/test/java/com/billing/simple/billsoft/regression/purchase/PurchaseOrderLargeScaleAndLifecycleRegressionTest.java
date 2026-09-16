package com.billing.simple.billsoft.regression.purchase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.billing.simple.billsoft.dtos.BatchPurchaseOrderRequest;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.entities.Party;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.entities.PurchaseOrder;
import com.billing.simple.billsoft.entities.PurchaseOrderItem;
import com.billing.simple.billsoft.entities.PurchaseOrderStatus;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.repositories.PartyRepository;
import com.billing.simple.billsoft.repositories.PurchaseOrderRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.service.PurchaseOrderPdfService;
import com.billing.simple.billsoft.service.PurchaseOrderService;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class PurchaseOrderLargeScaleAndLifecycleRegressionTest {

    @Autowired
    private PurchaseOrderService poService;

    @Autowired
    private PurchaseOrderPdfService poPdfService;

    @Autowired
    private PurchaseOrderRepository poRepo;

    @Autowired
    private PartyRepository partyRepo;

    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private FirmDetailsRepository firmRepo;

    private Long firmId;
    private Party vendorA;
    private Party vendorB;

    @BeforeEach
    void setup() {
        FirmDetails firm = firmRepo.findAll().stream().findFirst().orElseGet(() -> {
            FirmDetails f = new FirmDetails();
            f.setFirmName("PO Lifecycle Scale Firm");
            return firmRepo.save(f);
        });
        firmId = firm.getId();
        TenantContext.setCurrentFirmId(firmId);

        vendorA = partyRepo.save(Party.builder().name("Vendor Alpha").firmId(firmId).phone("9888811111").build());
        vendorB = partyRepo.save(Party.builder().name("Vendor Beta").firmId(firmId).phone("9888822222").build());
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testPurchaseOrder_Lifecycle_Draft_Issued_Received_Cancelled() {
        Product prod = productRepo.save(Product.builder()
                .name("Lifecycle Hardware SKU")
                .price(new BigDecimal("100.00"))
                .stockQuantity(new BigDecimal("10.00"))
                .firmId(firmId)
                .build());

        PurchaseOrder po = new PurchaseOrder();
        po.setFirmId(firmId);
        po.setParty(vendorA);
        po.setStatus(PurchaseOrderStatus.DRAFT);
        po.setPoNumber("PO-LC-101");
        po.setPoDate(LocalDate.now());

        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setProductId(prod.getId());
        item.setProductName(prod.getName());
        item.setQuantity(new BigDecimal("50.00"));
        item.setUnitPrice(new BigDecimal("60.00"));
        item.setPurchaseOrder(po);
        po.setItems(new ArrayList<>(List.of(item)));

        PurchaseOrder created = poService.createPurchaseOrder(po);
        assertThat(created.getStatus()).isEqualTo(PurchaseOrderStatus.DRAFT);
        // Draft must NOT increase stock
        Product pAfterDraft = productRepo.findById(prod.getId()).orElseThrow();
        assertThat(pAfterDraft.getStockQuantity()).isEqualByComparingTo(new BigDecimal("10.00"));

        // Transition to ISSUED
        PurchaseOrder issued = poService.updateStatus(created.getId(), firmId, PurchaseOrderStatus.ISSUED);
        assertThat(issued.getStatus()).isEqualTo(PurchaseOrderStatus.ISSUED);

        // Transition to RECEIVED -> increases stock
        PurchaseOrder received = poService.updateStatus(created.getId(), firmId, PurchaseOrderStatus.RECEIVED);
        assertThat(received.getStatus()).isEqualTo(PurchaseOrderStatus.RECEIVED);
        Product pAfterReceived = productRepo.findById(prod.getId()).orElseThrow();
        assertThat(pAfterReceived.getStockQuantity()).isEqualByComparingTo(new BigDecimal("60.00")); // 10 + 50

        // Transition to CANCELLED -> reverses stock
        PurchaseOrder cancelled = poService.updateStatus(created.getId(), firmId, PurchaseOrderStatus.CANCELLED);
        assertThat(cancelled.getStatus()).isEqualTo(PurchaseOrderStatus.CANCELLED);
        Product pAfterCancelled = productRepo.findById(prod.getId()).orElseThrow();
        assertThat(pAfterCancelled.getStockQuantity()).isEqualByComparingTo(new BigDecimal("10.00")); // back to 10
    }

    @Test
    void testLargePo_Pdf_CombinedPdf_And_ZipBundle() throws Exception {
        List<PurchaseOrder> pos = new ArrayList<>();

        for (int i = 1; i <= 5; i++) {
            PurchaseOrder po = new PurchaseOrder();
            po.setFirmId(firmId);
            po.setParty(i % 2 == 0 ? vendorA : vendorB);
            po.setStatus(PurchaseOrderStatus.ISSUED);
            po.setPoNumber("PO-BUNDLE-" + i);
            po.setPoDate(LocalDate.now());

            List<PurchaseOrderItem> items = new ArrayList<>();
            for (int j = 1; j <= 20; j++) {
                PurchaseOrderItem it = new PurchaseOrderItem();
                it.setProductName("Material Item #" + j);
                it.setQuantity(new BigDecimal("10.00"));
                it.setUnitPrice(new BigDecimal("45.00"));
                it.setPurchaseOrder(po);
                items.add(it);
            }
            po.setItems(items);
            pos.add(poService.createPurchaseOrder(po));
        }

        // 1. Individual PDF
        byte[] singlePdf = poPdfService.generatePdf(pos.get(0));
        assertThat(singlePdf).isNotEmpty();

        // 2. Combined / Merged PDF
        byte[] combinedPdf = poPdfService.generateMergedPdf(pos);
        assertThat(combinedPdf).isNotEmpty();
        PdfReader combinedReader = new PdfReader(combinedPdf);
        assertThat(combinedReader.getNumberOfPages()).isGreaterThanOrEqualTo(5);
        combinedReader.close();

        // 3. ZIP Bundle
        byte[] zipBytes = poPdfService.generateZipBundle(pos);
        assertThat(zipBytes).isNotEmpty();
    }

    @Test
    void testPurchaseOrderWith500LinesAndBatch() throws Exception {
        // ==========================================
        // Part A — 500-Line Purchase Order
        // ==========================================
        int lineCount = 500;
        PurchaseOrder po = new PurchaseOrder();
        po.setFirmId(firmId);
        po.setParty(vendorA);
        po.setStatus(PurchaseOrderStatus.ISSUED);
        po.setPoNumber("PO-SCALE-500L");
        po.setPoDate(LocalDate.now());

        List<PurchaseOrderItem> items = new ArrayList<>();
        for (int j = 1; j <= lineCount; j++) {
            PurchaseOrderItem it = new PurchaseOrderItem();
            it.setProductName(String.format("Bulk Raw Material SKU-%04d", j));
            it.setQuantity(new BigDecimal("10.00"));
            it.setUnitPrice(new BigDecimal("25.00"));
            it.setGstPercent(new BigDecimal("18.00"));
            it.setDiscountValue(BigDecimal.ZERO);
            it.setPurchaseOrder(po);
            items.add(it);
        }
        po.setItems(items);

        // 1 & 2. Create and persist through real service path
        PurchaseOrder createdPo = poService.createPurchaseOrder(po);

        // 3. Assert submitted == persisted lines
        assertThat(items).hasSize(500);
        assertThat(createdPo.getItems()).hasSize(500);

        // 8. Verify no duplicate lines and all item identities preserved
        Set<String> distinctNames = createdPo.getItems().stream()
                .map(PurchaseOrderItem::getProductName)
                .collect(Collectors.toSet());
        assertThat(distinctNames).hasSize(500);
        assertThat(distinctNames).contains("Bulk Raw Material SKU-0001", "Bulk Raw Material SKU-0250", "Bulk Raw Material SKU-0500");

        // 4. Verify independently calculated totals
        // Subtotal = 500 * 250.00 = 125,000.00. GST (18%) = 500 * 45.00 = 22,500.00. Grand Total = 147,500.00.
        BigDecimal expectedSubtotal = new BigDecimal("125000.00");
        BigDecimal expectedGst = new BigDecimal("22500.00");
        BigDecimal expectedTotal = new BigDecimal("147500.00");

        assertThat(createdPo.getSubtotalWithoutTax()).isEqualByComparingTo(expectedSubtotal);
        assertThat(createdPo.getTotalGstAmount()).isEqualByComparingTo(expectedGst);
        assertThat(createdPo.getTotalAmount()).isEqualByComparingTo(expectedTotal);

        // 5 & 6. Generate PO PDF, assert valid magic header and multi-page
        byte[] pdfBytes = poPdfService.generatePdf(createdPo);
        assertThat(pdfBytes).isNotEmpty();
        String header = new String(pdfBytes, 0, Math.min(pdfBytes.length, 8), StandardCharsets.US_ASCII);
        assertThat(header).startsWith("%PDF-");

        PdfReader reader = new PdfReader(pdfBytes);
        int totalPages = reader.getNumberOfPages();
        assertThat(totalPages).isGreaterThan(5);

        // 7. Parse/extract PDF content and assert item identities represented
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        StringBuilder fullText = new StringBuilder();
        for (int p = 1; p <= totalPages; p++) {
            fullText.append(extractor.getTextFromPage(p)).append("\n");
        }
        reader.close();
        String pdfText = fullText.toString();
        assertThat(pdfText).contains("Bulk Raw Material SKU-0001");
        assertThat(pdfText).contains("Bulk Raw Material SKU-0250");
        assertThat(pdfText).contains("Bulk Raw Material SKU-0500");

        // ==========================================
        // Part B — 500 PO Batch Creation
        // ==========================================
        int batchSize = 500;
        List<PurchaseOrder> batchOrders = new ArrayList<>();
        for (int i = 1; i <= batchSize; i++) {
            PurchaseOrder batchPo = new PurchaseOrder();
            batchPo.setParty(i % 2 == 0 ? vendorA : vendorB);
            batchPo.setPoDate(LocalDate.now());
            batchPo.setHidePricesOnPo(false);

            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setProductName("Batch Order Line Item " + i);
            item.setQuantity(new BigDecimal("5.00"));
            item.setUnitPrice(new BigDecimal("100.00"));
            item.setGstPercent(new BigDecimal("18.00"));
            item.setPurchaseOrder(batchPo);
            batchPo.setItems(new ArrayList<>(List.of(item)));

            batchOrders.add(batchPo);
        }

        String clientRequestId = "BATCH-REQ-500-" + UUID.randomUUID();
        BatchPurchaseOrderRequest batchRequest = BatchPurchaseOrderRequest.builder()
                .orders(batchOrders)
                .clientRequestId(clientRequestId)
                .build();

        // 1 & 6. Create batch of 500 POs via real batch service path
        List<PurchaseOrder> createdBatch = poService.createPurchaseOrdersBatch(batchRequest, firmId);

        // 2 & 6. Verify all 500 are persisted and returned
        assertThat(createdBatch).hasSize(500);

        // 3 & 5. Verify unique PO numbers and no duplicate POs
        Set<String> poNumbers = createdBatch.stream()
                .map(PurchaseOrder::getPoNumber)
                .collect(Collectors.toSet());
        assertThat(poNumbers).hasSize(500);

        // 4. Verify correct vendor associations
        for (int i = 0; i < createdBatch.size(); i++) {
            PurchaseOrder created = createdBatch.get(i);
            Party expectedParty = (i + 1) % 2 == 0 ? vendorA : vendorB;
            assertThat(created.getParty().getId()).isEqualTo(expectedParty.getId());
            assertThat(created.getItems()).hasSize(1);
        }

        // 7. Verify duplicate submission idempotency rejection
        assertThatThrownBy(() -> poService.createPurchaseOrdersBatch(batchRequest, firmId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate batch submission detected");
    }
}
