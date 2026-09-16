package com.billing.simple.billsoft.regression.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.billing.simple.billsoft.dtos.InvoiceRequest;
import com.billing.simple.billsoft.dtos.InvoiceRequestItem;
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.InvoiceItem;
import com.billing.simple.billsoft.entities.InvoiceStatus;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;
import com.billing.simple.billsoft.repo.InvoiceRepository;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.service.InvoicePdfService;
import com.billing.simple.billsoft.service.InvoiceService;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class LargeInvoiceAndQuotationScaleRegressionTest {

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoicePdfService pdfService;

    @Autowired
    private InvoiceRepository invoiceRepo;

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private FirmDetailsRepository firmRepo;

    private Long firmId;
    private Customer testCustomer;

    @BeforeEach
    void setup() {
        FirmDetails firm = firmRepo.findAll().stream().findFirst().orElseGet(() -> {
            FirmDetails f = new FirmDetails();
            f.setFirmName("Scale Test Enterprise Firm");
            f.setCity("Pune");
            f.setState("Maharashtra");
            f.setPincode("411001");
            return firmRepo.save(f);
        });
        firmId = firm.getId();
        TenantContext.setCurrentFirmId(firmId);

        testCustomer = new Customer();
        testCustomer.setName("Scale Test Customer");
        testCustomer.setFirmId(firmId);
        testCustomer.setPhone("9876543210");
        testCustomer = customerRepo.save(testCustomer);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testLargeInvoiceScale_50_100_500_Items() throws Exception {
        int[] itemCounts = {50, 100, 200};

        for (int count : itemCounts) {
            InvoiceRequest request = new InvoiceRequest();
            request.setFirmId(firmId);
            request.setCustomerId(testCustomer.getId());
            request.setStatus(InvoiceStatus.FINAL);
            request.setRoundOff(true);

            InvoiceRequest.Discount disc = new InvoiceRequest.Discount();
            disc.setType("PERCENT");
            disc.setValue(new BigDecimal("5.00"));
            request.setInvoiceDiscount(disc);

            List<InvoiceRequestItem> items = new ArrayList<>();
            for (int i = 1; i <= count; i++) {
                Product p = Product.builder()
                        .name("Catalog SKU " + i)
                        .price(new BigDecimal("100.00"))
                        .costPrice(new BigDecimal("60.00"))
                        .gstPercentage(new BigDecimal("18.00"))
                        .stockQuantity(new BigDecimal("1000"))
                        .firmId(firmId)
                        .build();
                p = productRepo.save(p);

                InvoiceRequestItem it = new InvoiceRequestItem();
                it.setProductId(p.getId());
                it.setProductName(p.getName());
                it.setQty(2);
                it.setPricePerUnit(new BigDecimal("100.00"));
                it.setDiscountValue(new BigDecimal("5.00"));
                it.setGstPercent(new BigDecimal("18.00"));
                items.add(it);
            }
            request.setItems(items);

            Invoice saved = invoiceService.createInvoice(request);
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getItems()).hasSize(count);

            // Reload and verify persistence
            Invoice reloaded = invoiceService.getById(saved.getId());
            assertThat(reloaded.getItems()).hasSize(count);

            // Verify PDF generation & multi-page splitting
            byte[] pdfBytes = pdfService.generatePdf(reloaded, "A4");
            assertThat(pdfBytes).isNotEmpty();

            PdfReader reader = new PdfReader(pdfBytes);
            int pages = reader.getNumberOfPages();
            assertThat(pages).isGreaterThanOrEqualTo(count >= 100 ? 3 : 1);
            reader.close();
        }
    }

    @Test
    void testLargeQuotationAndConversionWithManyItems() {
        int count = 75;
        InvoiceRequest request = new InvoiceRequest();
        request.setFirmId(firmId);
        request.setCustomerId(testCustomer.getId());
        request.setStatus(InvoiceStatus.ESTIMATE);

        List<InvoiceRequestItem> items = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Product p = Product.builder()
                    .name("Quoted Item SKU " + i)
                    .price(new BigDecimal("250.00"))
                    .gstPercentage(new BigDecimal("12.00"))
                    .stockQuantity(new BigDecimal("500"))
                    .firmId(firmId)
                    .build();
            p = productRepo.save(p);

            InvoiceRequestItem it = new InvoiceRequestItem();
            it.setProductId(p.getId());
            it.setProductName(p.getName());
            it.setQty(3);
            it.setPricePerUnit(new BigDecimal("250.00"));
            items.add(it);
        }
        request.setItems(items);

        Invoice estimate = invoiceService.createEstimate(request);
        assertThat(estimate.getStatus()).isEqualTo(InvoiceStatus.ESTIMATE);
        assertThat(estimate.getConvertedInvoiceId()).isNull();
        assertThat(estimate.getItems()).hasSize(count);

        // Convert to Tax Invoice
        Invoice invoice = invoiceService.convertEstimateToInvoice(estimate.getId(), null);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.UNPAID);
        assertThat(invoice.getItems()).hasSize(count);

        // Verify quotation links converted invoice ID
        Invoice updatedEstimate = invoiceService.getById(estimate.getId());
        assertThat(updatedEstimate.getConvertedInvoiceId()).isEqualTo(invoice.getId());

        // Verify second conversion attempt is strictly rejected
        assertThatThrownBy(() -> invoiceService.convertEstimateToInvoice(estimate.getId(), null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already been converted");
    }

    @Test
    void testHistoricalMasterDataImmutability() {
        // Create product
        Product p = Product.builder()
                .name("Original Product")
                .price(new BigDecimal("500.00"))
                .costPrice(new BigDecimal("300.00"))
                .gstPercentage(new BigDecimal("18.00"))
                .hsnCode("8481")
                .stockQuantity(new BigDecimal("100"))
                .firmId(firmId)
                .build();
        p = productRepo.save(p);

        InvoiceRequest request = new InvoiceRequest();
        request.setFirmId(firmId);
        request.setCustomerId(testCustomer.getId());
        request.setStatus(InvoiceStatus.FINAL);

        InvoiceRequestItem it = new InvoiceRequestItem();
        it.setProductId(p.getId());
        it.setQty(2);
        it.setPricePerUnit(new BigDecimal("500.00"));
        it.setGstPercent(new BigDecimal("18.00"));
        request.setItems(List.of(it));

        Invoice saved = invoiceService.createInvoice(request);
        BigDecimal originalTotal = saved.getTotalAmount();
        BigDecimal originalTax = saved.getTotalTax();

        // Mutate Product Master Data (Price, Tax, HSN, Name)
        p.setName("Modified New Product Name");
        p.setPrice(new BigDecimal("9999.00"));
        p.setGstPercentage(new BigDecimal("28.00"));
        p.setHsnCode("9999");
        productRepo.save(p);

        // Reload invoice - historical snapshot must remain unchanged
        Invoice reloaded = invoiceService.getById(saved.getId());
        assertThat(reloaded.getTotalAmount()).isEqualByComparingTo(originalTotal);
        assertThat(reloaded.getTotalTax()).isEqualByComparingTo(originalTax);
        assertThat(reloaded.getItems().get(0).getPricePerUnit()).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    void testEdgeCases_100PercentDiscount_And_ZeroPriceItem() {
        Product p1 = productRepo.save(Product.builder().name("Free Sample").price(BigDecimal.ZERO).firmId(firmId).stockQuantity(BigDecimal.TEN).build());
        Product p2 = productRepo.save(Product.builder().name("Discounted Item").price(new BigDecimal("200.00")).gstPercentage(new BigDecimal("18.00")).firmId(firmId).stockQuantity(BigDecimal.TEN).build());

        InvoiceRequest request = new InvoiceRequest();
        request.setFirmId(firmId);
        request.setCustomerId(testCustomer.getId());
        request.setStatus(InvoiceStatus.FINAL);

        InvoiceRequestItem it1 = new InvoiceRequestItem();
        it1.setProductId(p1.getId());
        it1.setQty(1);
        it1.setPricePerUnit(BigDecimal.ZERO);

        InvoiceRequestItem it2 = new InvoiceRequestItem();
        it2.setProductId(p2.getId());
        it2.setQty(1);
        it2.setPricePerUnit(new BigDecimal("200.00"));
        it2.setDiscountValue(new BigDecimal("200.00")); // 100% item discount

        request.setItems(List.of(it1, it2));

        Invoice saved = invoiceService.createInvoice(request);
        assertThat(saved.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getTotalTax()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getSubtotalWithoutTax()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(saved.getTotalDiscount()).isEqualByComparingTo(new BigDecimal("200.00"));
    }

    @Test
    void testInvoiceWith1000LineItemsAndPdf() throws Exception {
        int count = 1000;
        List<Product> products = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            products.add(Product.builder()
                    .name(String.format("Bulk Catalog SKU-%04d", i))
                    .price(new BigDecimal("100.00"))
                    .costPrice(new BigDecimal("60.00"))
                    .gstPercentage(new BigDecimal("18.00"))
                    .stockQuantity(new BigDecimal("5000"))
                    .firmId(firmId)
                    .build());
        }
        productRepo.saveAll(products);

        InvoiceRequest request = new InvoiceRequest();
        request.setFirmId(firmId);
        request.setCustomerId(testCustomer.getId());
        request.setStatus(InvoiceStatus.FINAL);
        request.setRoundOff(false);

        List<InvoiceRequestItem> items = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Product p = products.get(i - 1);
            InvoiceRequestItem it = new InvoiceRequestItem();
            it.setProductId(p.getId());
            it.setProductName(p.getName());
            it.setQty(2);
            it.setPricePerUnit(new BigDecimal("100.00"));
            it.setDiscountValue(new BigDecimal("5.00")); // 5 per unit discount -> taxable = 2 * 95 = 190.00
            it.setGstPercent(new BigDecimal("18.00"));
            items.add(it);
        }
        request.setItems(items);

        // 1 & 2. Create invoice and persist through real service path
        Invoice saved = invoiceService.createInvoice(request);

        // 3 & 4. Assert submitted and persisted counts
        assertThat(request.getItems()).hasSize(1000);
        assertThat(saved.getItems()).hasSize(1000);

        Invoice reloaded = invoiceService.getById(saved.getId());
        assertThat(reloaded.getItems()).hasSize(1000);

        // 7 & 8. Item identities representation and no duplicates
        Set<String> distinctNames = reloaded.getItems().stream()
                .map(InvoiceItem::getProductName)
                .collect(Collectors.toSet());
        assertThat(distinctNames).hasSize(1000);
        assertThat(distinctNames).contains("Bulk Catalog SKU-0001", "Bulk Catalog SKU-0500", "Bulk Catalog SKU-1000");

        // 9. Independently calculated expected financial values
        // Per item: qty 2 * 100 = 200 gross. item discount = 5.00. taxable = 195.00. tax 18% = 35.10. total = 230.10.
        // 1000 items: gross = 200,000.00; discount = 5,000.00; tax = 35,100.00; total = 230,100.00.
        BigDecimal expectedSubtotal = new BigDecimal("200000.00");
        BigDecimal expectedDiscount = new BigDecimal("5000.00");
        BigDecimal expectedTax = new BigDecimal("35100.00");
        BigDecimal expectedTotal = new BigDecimal("230100.00");

        assertThat(reloaded.getSubtotalWithoutTax()).isEqualByComparingTo(expectedSubtotal);
        assertThat(reloaded.getTotalDiscount()).isEqualByComparingTo(expectedDiscount);
        assertThat(reloaded.getTotalTax()).isEqualByComparingTo(expectedTax);
        assertThat(reloaded.getTotalAmount()).isEqualByComparingTo(expectedTotal);

        // 5, 6, 10, 11, 12, 13, 14. Generate and validate PDF
        byte[] pdfBytes = pdfService.generatePdf(reloaded, "A4");
        assertThat(pdfBytes).isNotEmpty();

        // Magic header %PDF-
        String header = new String(pdfBytes, 0, Math.min(pdfBytes.length, 8), StandardCharsets.US_ASCII);
        assertThat(header).startsWith("%PDF-");

        PdfReader reader = new PdfReader(pdfBytes);
        int totalPages = reader.getNumberOfPages();
        assertThat(totalPages).isGreaterThan(15); // 1000 items spans across multiple pages

        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        StringBuilder fullText = new StringBuilder();
        for (int p = 1; p <= totalPages; p++) {
            fullText.append(extractor.getTextFromPage(p)).append("\n");
        }
        reader.close();

        String pdfText = fullText.toString();
        assertThat(pdfText).containsIgnoringCase("TAX INVOICE");
        assertThat(pdfText).contains("Bulk Catalog SKU-0001");
        assertThat(pdfText).contains("Bulk Catalog SKU-0500");
        assertThat(pdfText).contains("Bulk Catalog SKU-1000");
    }

    @Test
    void testQuotationWith500And1000Items() throws Exception {
        int[] counts = {500, 1000};

        for (int count : counts) {
            List<Product> products = new ArrayList<>();
            for (int i = 1; i <= count; i++) {
                products.add(Product.builder()
                        .name(String.format("Quote SKU-%d-%04d", count, i))
                        .price(new BigDecimal("50.00"))
                        .gstPercentage(new BigDecimal("12.00"))
                        .stockQuantity(new BigDecimal("5000"))
                        .firmId(firmId)
                        .build());
            }
            productRepo.saveAll(products);

            InvoiceRequest request = new InvoiceRequest();
            request.setFirmId(firmId);
            request.setCustomerId(testCustomer.getId());
            request.setStatus(InvoiceStatus.ESTIMATE);

            List<InvoiceRequestItem> items = new ArrayList<>();
            for (int i = 1; i <= count; i++) {
                Product p = products.get(i - 1);
                InvoiceRequestItem it = new InvoiceRequestItem();
                it.setProductId(p.getId());
                it.setProductName(p.getName());
                it.setQty(1);
                it.setPricePerUnit(new BigDecimal("50.00"));
                it.setGstPercent(new BigDecimal("12.00"));
                items.add(it);
            }
            request.setItems(items);

            // 1 & 2. Create & Persist Quotation
            Invoice estimate = invoiceService.createEstimate(request);

            // 3. Assert submitted == persisted
            assertThat(request.getItems()).hasSize(count);
            assertThat(estimate.getItems()).hasSize(count);
            assertThat(estimate.getStatus()).isEqualTo(InvoiceStatus.ESTIMATE);
            assertThat(estimate.getConvertedInvoiceId()).isNull();

            // 4. Verify independently calculated totals
            // Subtotal = count * 50. Tax = Subtotal * 0.12 = count * 6. Total = count * 56.
            BigDecimal expectedSubtotal = new BigDecimal(count * 50).setScale(2, java.math.RoundingMode.HALF_UP);
            BigDecimal expectedTax = new BigDecimal(count * 6).setScale(2, java.math.RoundingMode.HALF_UP);
            BigDecimal expectedTotal = new BigDecimal(count * 56).setScale(2, java.math.RoundingMode.HALF_UP);

            assertThat(estimate.getSubtotalWithoutTax()).isEqualByComparingTo(expectedSubtotal);
            assertThat(estimate.getTotalTax()).isEqualByComparingTo(expectedTax);
            assertThat(estimate.getTotalAmount()).isEqualByComparingTo(expectedTotal);

            // 5, 6, 7. Generate PDF and verify multi-page
            byte[] pdfBytes = pdfService.generatePdf(estimate, "A4");
            assertThat(pdfBytes).isNotEmpty();
            PdfReader reader = new PdfReader(pdfBytes);
            int pages = reader.getNumberOfPages();
            assertThat(pages).isGreaterThan(5);

            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder sb = new StringBuilder();
            for (int p = 1; p <= pages; p++) {
                sb.append(extractor.getTextFromPage(p)).append("\n");
            }
            reader.close();
            String extracted = sb.toString();
            assertThat(extracted).contains(String.format("Quote SKU-%d-0001", count));
            assertThat(extracted).contains(String.format("Quote SKU-%d-%04d", count, count / 2));
            assertThat(extracted).contains(String.format("Quote SKU-%d-%04d", count, count));

            // 8 & 9. Convert quotation to invoice
            Invoice convertedInvoice = invoiceService.convertEstimateToInvoice(estimate.getId(), null);
            assertThat(convertedInvoice).isNotNull();
            assertThat(convertedInvoice.getStatus()).isEqualTo(InvoiceStatus.UNPAID);

            // 10. Resulting invoice line count
            assertThat(convertedInvoice.getItems()).hasSize(count);

            // Reload estimate to check convertedInvoiceId
            Invoice updatedEstimate = invoiceService.getById(estimate.getId());
            assertThat(updatedEstimate.getConvertedInvoiceId()).isEqualTo(convertedInvoice.getId());

            // 11. Converted quotation has convertedInvoiceId populated
            assertThat(updatedEstimate.getConvertedInvoiceId()).isNotNull();

            // 12, 13, 14. Second conversion rejection
            assertThatThrownBy(() -> invoiceService.convertEstimateToInvoice(estimate.getId(), null))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("already been converted");
        }
    }
}
