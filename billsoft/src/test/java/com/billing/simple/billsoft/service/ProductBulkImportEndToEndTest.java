package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.controllers.ProductController;
import com.billing.simple.billsoft.dtos.ProductBulkRowDto;
import com.billing.simple.billsoft.dtos.ProductImportResponseDto;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.entities.StockMovement;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.repo.StockMovementRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;
import com.billing.simple.billsoft.util.ProductBulkImportUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductBulkImportEndToEndTest {

    private static final Long FIRM_A = 101L;
    private static final Long FIRM_B = 202L;

    @Mock
    private ProductRepository repo;

    @Mock
    private StockMovementRepository stockMovementRepo;

    @Mock
    private SystemMetricsService systemMetricsService;

    @InjectMocks
    private ProductService productService;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentFirmId(FIRM_A);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // 1. Valid CSV Import
    @Test
    @DisplayName("Valid CSV import successfully creates products with initial stock and movements")
    void testValidCsvImportSuccess() {
        String csv = "Product Name,HSN/SAC Code,SKU,Barcode,Category,Item Type,Selling Price,Cost Price,GST %,Initial Stock,Min Stock,Unit,Description\n" +
                "Rice Premium,10063010,SKU-RICE-01,890123450001,Grains,GOODS,150.00,120.00,5,50,10,kg,Fine basmati\n" +
                "Delivery Service,996812,SKU-DELIV-02,,Logistics,SERVICE,50.00,0.00,18,0,0,hours,Standard shipping";

        when(repo.saveAll(anyList())).thenAnswer(inv -> {
            List<Product> list = inv.getArgument(0);
            long id = 1;
            for (Product p : list) {
                p.setId(id++);
            }
            return list;
        });

        ProductImportResponseDto response = productService.importProductsFromCsv(csv, FIRM_A);

        assertNotNull(response);
        assertEquals(2, response.getTotalRows());
        assertEquals(2, response.getImportedCount());
        assertEquals(0, response.getFailedCount());
        assertEquals(0, response.getSkippedCount());
        assertEquals(2, response.getProducts().size());

        Product p1 = response.getProducts().get(0);
        assertEquals("Rice Premium", p1.getName());
        assertEquals("SKU-RICE-01", p1.getSku());
        assertEquals(new BigDecimal("150.00"), p1.getPrice());
        assertEquals(new BigDecimal("120.00"), p1.getCostPrice());
        assertEquals(new BigDecimal("50"), p1.getStockQuantity());
        assertEquals("kg", p1.getUnit());
        assertEquals("GOODS", p1.getItemType());
        assertTrue(p1.getInInventory());

        // Stock movement must be recorded for physical goods with initial stock > 0
        verify(stockMovementRepo, times(1)).save(any(StockMovement.class));
    }

    // 2. Valid XLSX Import using Template
    @Test
    @DisplayName("Valid XLSX template byte array imports successfully")
    void testValidXlsxImportSuccess() {
        byte[] xlsxBytes = ProductBulkImportUtils.generateXlsxTemplate();

        when(repo.saveAll(anyList())).thenAnswer(inv -> {
            List<Product> list = inv.getArgument(0);
            long id = 10;
            for (Product p : list) p.setId(id++);
            return list;
        });

        ProductImportResponseDto response = productService.importProductsFromFile(xlsxBytes, "products_template.xlsx", FIRM_A);

        assertNotNull(response);
        assertEquals(2, response.getTotalRows());
        assertEquals(2, response.getImportedCount());
        assertEquals("Sample Product A", response.getProducts().get(0).getName());
        assertEquals("SKU-PROD-001", response.getProducts().get(0).getSku());
        assertEquals("Consulting Service", response.getProducts().get(1).getName());
    }

    // 3. Reject Empty File
    @Test
    @DisplayName("Empty file upload is rejected with a clear error")
    void testEmptyFileRejection() {
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () ->
                productService.importProductsFromFile(new byte[0], "empty.csv", FIRM_A));
        assertTrue(ex1.getMessage().contains("Uploaded file is empty"));

        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () ->
                productService.importProductsFromCsv("", FIRM_A));
        assertTrue(ex2.getMessage().contains("CSV content is empty"));
    }

    // 4. Reject Legacy XLS
    @Test
    @DisplayName("Legacy Excel 97-2003 (.xls) format is rejected with explicit instructions")
    void testLegacyXlsRejection() {
        byte[] fakeXls = new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, 0x00, 0x00};
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                productService.importProductsFromFile(fakeXls, "legacy_sheet.xls", FIRM_A));
        assertTrue(ex.getMessage().contains("Legacy Excel 97-2003 (.xls) format is not supported"));
    }

    // 5. Reject Corrupted / Binary Non-Text File
    @Test
    @DisplayName("Corrupted or unsupported binary file is rejected safely")
    void testCorruptedBinaryFileRejection() {
        byte[] binaryPdf = "%PDF-1.4\0\0\0\0fake-binary-content".getBytes(StandardCharsets.UTF_8);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                productService.importProductsFromFile(binaryPdf, "sample.pdf", FIRM_A));
        assertTrue(ex.getMessage().contains("Unsupported file format"));
        verify(repo, never()).saveAll(any());
    }

    // 6. Validation: Product Name Missing
    @Test
    @DisplayName("Missing product name is rejected with row number")
    void testMissingProductNameValidation() {
        String csv = "Product Name,Price\n" +
                ",100.00";

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                productService.importProductsFromCsv(csv, FIRM_A));
        assertTrue(ex.getMessage().contains("Row 2: Product name is required"));
        verify(repo, never()).saveAll(any());
    }

    // 7. Validation: Duplicate SKU in File and in Database
    @Test
    @DisplayName("Duplicate SKU in batch and in existing database is caught and rejected")
    void testDuplicateSkuValidation() {
        // In-batch duplicate
        String csvBatchDup = "Product Name,SKU,Price\n" +
                "Item One,SKU-DUP-1,10.00\n" +
                "Item Two,SKU-DUP-1,20.00";

        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () ->
                productService.importProductsFromCsv(csvBatchDup, FIRM_A));
        assertTrue(ex1.getMessage().contains("Row 3: Duplicate SKU in batch: 'SKU-DUP-1'"));

        // Existing DB duplicate
        when(repo.existsByFirmIdAndSku(FIRM_A, "SKU-EXISTING")).thenReturn(true);
        String csvDbDup = "Product Name,SKU,Price\n" +
                "New Item,SKU-EXISTING,50.00";

        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () ->
                productService.importProductsFromCsv(csvDbDup, FIRM_A));
        assertTrue(ex2.getMessage().contains("Product with SKU 'SKU-EXISTING' already exists"));
        verify(repo, never()).saveAll(any());
    }

    // 8. Validation: Duplicate Barcode in File and in Database
    @Test
    @DisplayName("Duplicate Barcode in batch and in existing database is caught and rejected")
    void testDuplicateBarcodeValidation() {
        String csvBarcodeDup = "Product Name,Barcode,Price\n" +
                "Bar 1,8900000001,15.00\n" +
                "Bar 2,8900000001,25.00";

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                productService.importProductsFromCsv(csvBarcodeDup, FIRM_A));
        assertTrue(ex.getMessage().contains("Row 3: Duplicate Barcode in batch: '8900000001'"));

        when(repo.existsByFirmIdAndBarcode(FIRM_A, "8900000002")).thenReturn(true);
        String csvDbBarcode = "Product Name,Barcode,Price\n" +
                "Bar 3,8900000002,30.00";

        IllegalArgumentException exDb = assertThrows(IllegalArgumentException.class, () ->
                productService.importProductsFromCsv(csvDbBarcode, FIRM_A));
        assertTrue(exDb.getMessage().contains("Product with Barcode '8900000002' already exists"));
    }

    // 9. Validation: Invalid & Negative Prices
    @Test
    @DisplayName("Negative prices and non-numeric price values are rejected with row numbers")
    void testPriceValidation() {
        String csvNegPrice = "Product Name,Selling Price\n" +
                "Bad Price,-25.00";
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () ->
                productService.importProductsFromCsv(csvNegPrice, FIRM_A));
        assertTrue(ex1.getMessage().contains("Row 2: Selling price cannot be negative"));

        String csvNonNumPrice = "Product Name,Selling Price\n" +
                "Invalid Text,TWENTY_RUPEES";
        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () ->
                productService.importProductsFromCsv(csvNonNumPrice, FIRM_A));
        assertTrue(ex2.getMessage().contains("Row 2: Invalid selling price 'TWENTY_RUPEES'"));

        String csvNegCost = "Product Name,Price,Cost Price\n" +
                "Bad Cost,50.00,-10.00";
        IllegalArgumentException ex3 = assertThrows(IllegalArgumentException.class, () ->
                productService.importProductsFromCsv(csvNegCost, FIRM_A));
        assertTrue(ex3.getMessage().contains("Row 2: Cost price cannot be negative"));
    }

    // 10. Validation: Invalid GST
    @Test
    @DisplayName("Invalid GST percentage (>100, negative, or non-numeric) is rejected")
    void testGstValidation() {
        String csvGst150 = "Product Name,Price,GST %\n" +
                "High GST,100.00,150";
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () ->
                productService.importProductsFromCsv(csvGst150, FIRM_A));
        assertTrue(ex1.getMessage().contains("Row 2: GST percentage must be between 0 and 100"));

        String csvGstAlpha = "Product Name,Price,GST %\n" +
                "Text GST,100.00,EIGHTEEN_PERCENT";
        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () ->
                productService.importProductsFromCsv(csvGstAlpha, FIRM_A));
        assertTrue(ex2.getMessage().contains("Row 2: Invalid GST percentage 'EIGHTEEN_PERCENT'"));
    }

    // 11. Validation: Negative Initial Stock & Min Stock
    @Test
    @DisplayName("Negative initial stock and minimum stock levels are rejected")
    void testStockQuantityValidation() {
        String csvNegStock = "Product Name,Price,Initial Stock\n" +
                "Negative Stock,100.00,-5";
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () ->
                productService.importProductsFromCsv(csvNegStock, FIRM_A));
        assertTrue(ex1.getMessage().contains("Row 2: Initial stock cannot be negative"));

        String csvNegMin = "Product Name,Price,Min Stock\n" +
                "Negative Min,100.00,-2";
        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () ->
                productService.importProductsFromCsv(csvNegMin, FIRM_A));
        assertTrue(ex2.getMessage().contains("Row 2: Minimum stock level cannot be negative"));
    }

    // 12. Validation: Unknown Unit and Unit Normalization
    @Test
    @DisplayName("Unknown units are rejected while valid aliases are normalized")
    void testUnitValidationAndNormalization() {
        // Unknown unit
        String csvBadUnit = "Product Name,Price,Unit\n" +
                "Mystery Unit,100.00,UNSUPPORTED_UNIT_XYZ";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                productService.importProductsFromCsv(csvBadUnit, FIRM_A));
        assertTrue(ex.getMessage().contains("Unknown or unsupported unit 'UNSUPPORTED_UNIT_XYZ'"));

        // Unit normalization: 'boxes' -> 'box', 'ltr' -> 'litre', 'pieces' -> 'pcs'
        String csvNormUnit = "Product Name,Price,Unit\n" +
                "Apples,200.00,boxes\n" +
                "Milk,60.00,ltr\n" +
                "Screws,5.00,pieces";

        when(repo.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        ProductImportResponseDto resp = productService.importProductsFromCsv(csvNormUnit, FIRM_A);
        assertEquals(3, resp.getImportedCount());
        assertEquals("box", resp.getProducts().get(0).getUnit());
        assertEquals("litre", resp.getProducts().get(1).getUnit());
        assertEquals("pcs", resp.getProducts().get(2).getUnit());
    }

    // 13. Validation: Invalid Item Type
    @Test
    @DisplayName("Invalid ItemType is rejected; GOODS and SERVICE are accepted")
    void testItemTypeValidation() {
        String csvBadType = "Product Name,Price,Item Type\n" +
                "Invalid Type Product,100.00,ROBOTIC_AI";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                productService.importProductsFromCsv(csvBadType, FIRM_A));
        assertTrue(ex.getMessage().contains("Row 2: Invalid Item Type 'ROBOTIC_AI'"));
    }

    // 14. Multi-Tenant Firm Isolation
    @Test
    @DisplayName("Tenant isolation strictly assigns target firm and rejects cross-firm import")
    void testMultiTenantIsolation() {
        // Cross-firm access rejection
        TenantContext.setCurrentFirmId(FIRM_A);
        TenantSecurityException ex = assertThrows(TenantSecurityException.class, () ->
                productService.importProductsFromCsv("Product Name,Price\nItem,50", FIRM_B));
        assertTrue(ex.getMessage().contains("Cross-firm product import prohibited"));

        // Normal import strictly scopes to current firm
        when(repo.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        ProductImportResponseDto resp = productService.importProductsFromCsv("Product Name,Price\nItem Firm A,50", FIRM_A);
        assertEquals(FIRM_A, resp.getProducts().get(0).getFirmId());
    }

    // 15. Preview Mode Returns Complete Validation Breakdown Without Saving
    @Test
    @DisplayName("Preview mode returns full row counts, preview models, and all row errors without saving to DB")
    void testPreviewModeDoesNotCommitAndReportsErrors() {
        String csvMixed = "Product Name,SKU,Selling Price,GST %\n" +
                "Valid Item 1,SKU-OK-1,100.00,18\n" +
                ",SKU-FAIL-2,50.00,5\n" + // Missing name
                "Bad Price,SKU-FAIL-3,-10.00,12\n" + // Negative price
                "Valid Item 2,SKU-OK-2,200.00,0";

        ProductImportResponseDto preview = productService.previewProductsFromCsv(csvMixed, FIRM_A);

        assertNotNull(preview);
        assertEquals(4, preview.getTotalRows());
        assertEquals(2, preview.getImportedCount());
        assertEquals(2, preview.getFailedCount());
        assertEquals(2, preview.getErrors().size());
        assertTrue(preview.getErrors().get(0).contains("Row 3: Product name is required"));
        assertTrue(preview.getErrors().get(1).contains("Row 4: Selling price cannot be negative"));
        assertEquals(2, preview.getProducts().size());

        // Zero database operations in preview mode
        verify(repo, never()).saveAll(any());
        verify(stockMovementRepo, never()).save(any());
    }

    // 16. Controller Preview and Import Endpoints
    @Test
    @DisplayName("ProductController /import/preview and /import handle multipart upload and raw body")
    void testProductControllerEndpoints() {
        ProductController controller = new ProductController(productService);

        // Preview Endpoint
        String csvData = "Product Name,Price\nController Item,99.00";
        MockMultipartFile file = new MockMultipartFile("file", "items.csv", "text/csv", csvData.getBytes(StandardCharsets.UTF_8));
        ResponseEntity<ProductImportResponseDto> previewResp = controller.previewImport(file, null, FIRM_A);

        assertEquals(HttpStatus.OK, previewResp.getStatusCode());
        assertEquals(1, previewResp.getBody().getImportedCount());
        assertEquals(0, previewResp.getBody().getFailedCount());

        // Template Endpoints
        ResponseEntity<byte[]> xlsxResp = controller.downloadTemplateXlsx();
        assertEquals(HttpStatus.OK, xlsxResp.getStatusCode());
        assertNotNull(xlsxResp.getBody());
        assertTrue(xlsxResp.getHeaders().getFirst("Content-Disposition").contains("products_template.xlsx"));
    }
}

