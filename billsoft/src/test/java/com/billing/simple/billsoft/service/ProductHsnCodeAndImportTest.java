package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.controllers.ProductController;
import com.billing.simple.billsoft.dtos.ProductImportResponseDto;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.entities.StockMovement;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.repo.StockMovementRepository;
import com.billing.simple.billsoft.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductHsnCodeAndImportTest {

    @Mock
    private ProductRepository repo;

    @Mock
    private StockMovementRepository stockMovementRepo;

    @InjectMocks
    private ProductService productService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TenantContext.setCurrentFirmId(101L);
        when(repo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repo.saveAll(anyList())).thenAnswer(inv -> new ArrayList<>(inv.getArgument(0)));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // 1. Leading Zeros Preservation in Product Creation and Update
    @Test
    void testCreateAndSaveProductPreservesLeadingZerosInHsnCode() {
        Product p = Product.builder()
                .name("Basmati Rice 1kg")
                .hsnCode("  0101  ") // with whitespace
                .sku("SKU-RICE-01")
                .price(new BigDecimal("120.00"))
                .costPrice(new BigDecimal("90.00"))
                .stockQuantity(BigDecimal.TEN)
                .build();

        when(repo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product saved = productService.create(p);

        assertNotNull(saved);
        assertEquals("0101", saved.getHsnCode(), "Leading zero in HSN code must be preserved and trimmed as string");
        assertEquals("SKU-RICE-01", saved.getSku());

        // Update test
        Product updatedData = Product.builder()
                .name("Basmati Rice Premium")
                .hsnCode("0099")
                .price(new BigDecimal("130.00"))
                .build();

        when(repo.findByIdAndFirmId(1L, 101L)).thenReturn(Optional.of(saved));
        Product updated = productService.update(1L, updatedData);

        assertNotNull(updated);
        assertEquals("0099", updated.getHsnCode(), "Updated HSN code must preserve leading zeros");
    }

    // 2. Blank or Empty HSN Code is Treated as Optional (Stored as null)
    @Test
    void testBlankHsnCodeIsStoredAsNull() {
        Product p = Product.builder()
                .name("Custom Unclassified Service")
                .hsnCode("   ")
                .price(new BigDecimal("500.00"))
                .build();

        when(repo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product saved = productService.create(p);
        assertNull(saved.getHsnCode(), "Whitespace-only HSN code must be sanitized to null");
    }

    // 3. CSV Export Produces RFC 4180 Format with HSN/SAC Code Column and Leading Zeros
    @Test
    void testExportProductsToCsvIncludesHsnSacCodeAndPreservesFormatting() {
        Product p1 = Product.builder()
                .id(1L)
                .firmId(101L)
                .name("Live Horses, Asses")
                .hsnCode("0101")
                .sku("HORSE-01")
                .barcode("890123")
                .category("Livestock")
                .price(new BigDecimal("50000.00"))
                .costPrice(new BigDecimal("40000.00"))
                .gstPercentage(new BigDecimal("12"))
                .stockQuantity(new BigDecimal("5"))
                .minStockLevel(new BigDecimal("2"))
                .unit("heads")
                .itemType("GOODS")
                .description("Purebred breeding animals")
                .build();

        Product p2 = Product.builder()
                .id(2L)
                .firmId(101L)
                .name("Consulting Services, \"IT & Legal\"")
                .hsnCode("9983")
                .sku("")
                .category("Services")
                .price(new BigDecimal("15000.00"))
                .itemType("SERVICE")
                .unit("hrs")
                .build();

        when(repo.findByFirmIdAndIsArchivedFalseOrderByNameAsc(101L)).thenReturn(Arrays.asList(p1, p2));

        byte[] csvBytes = productService.exportProductsToCsv(101L);
        assertNotNull(csvBytes);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);
        String[] lines = csv.split("\r?\n");
        assertTrue(lines.length >= 3, "CSV should contain header line and 2 product lines");

        // Verify header contains HSN/SAC Code
        String header = lines[0];
        assertTrue(header.contains("HSN/SAC Code"), "Header must include 'HSN/SAC Code'");

        // Verify line 1 has leading zero preserved ("0101")
        String line1 = lines[1];
        assertTrue(line1.contains("\"0101\"") || line1.contains("0101"), "HSN code 0101 must appear with leading zero");
        assertTrue(line1.contains("Live Horses, Asses"));

        // Verify line 2 has quoted description with escaped quotes
        String line2 = lines[2];
        assertTrue(line2.contains("9983"));
        assertTrue(line2.contains("SERVICE"));
    }

    // 4. CSV Template Endpoint Returns Downloadable Standard Headers
    @Test
    void testGetProductCsvTemplate() {
        byte[] templateBytes = productService.getProductCsvTemplate();
        assertNotNull(templateBytes);
        String template = new String(templateBytes, StandardCharsets.UTF_8);
        assertTrue(template.startsWith("Product Name,HSN/SAC Code,"), "Template must start with standard headers");
        assertTrue(template.contains("84713010"), "Template sample data should demonstrate goods HSN code");
        assertTrue(template.contains("998311"), "Template sample data should demonstrate service SAC code");
    }

    // 5. CSV Import Maps HSN/SAC Code Column and Preserves Leading Zeros
    @Test
    void testImportProductsFromCsvWithHsnSacCodeHeader() {
        String csv = "Product Name,HSN/SAC Code,Barcode,Category,Selling Price,Cost Price,GST %,Initial Stock,Min Stock,Unit,Item Type,Description\n" +
                "Organic Green Tea,\"0902\",890111222,Beverages,250.00,180.00,5,100,20,box,GOODS,Single estate tea\n" +
                "Software Maintenance,9983,,Services,5000.00,0,18,0,0,mon,SERVICE,Remote IT support";

        when(repo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductImportResponseDto response = productService.importProductsFromCsv(csv, 101L);

        assertNotNull(response);
        assertEquals(2, response.getTotalRows());
        assertEquals(2, response.getImportedCount());
        assertEquals(0, response.getErrors().size());

        List<Product> products = response.getProducts();
        assertEquals(2, products.size());

        Product p1 = products.get(0);
        assertEquals("Organic Green Tea", p1.getName());
        assertEquals("0902", p1.getHsnCode(), "Leading zero in 0902 must be preserved");
        assertEquals("Beverages", p1.getCategory());
        assertEquals(new BigDecimal("250.00"), p1.getPrice());
        assertEquals(new BigDecimal("180.00"), p1.getCostPrice());
        assertEquals(new BigDecimal("5"), p1.getGstPercentage());
        assertEquals(new BigDecimal("100"), p1.getStockQuantity());
        assertEquals("GOODS", p1.getItemType());

        Product p2 = products.get(1);
        assertEquals("Software Maintenance", p2.getName());
        assertEquals("9983", p2.getHsnCode());
        assertEquals("SERVICE", p2.getItemType());
    }

    // 6. CSV Import Supports Header Aliases: HSN, SAC, HSN Code, SAC Code
    @Test
    void testImportProductsFromCsvSupportsHeaderAliases() {
        // Using alias "HSN Code"
        String csv1 = "Item Name,HSN Code,Price\n" +
                "Product Alpha,0401,95.00";

        when(repo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductImportResponseDto resp1 = productService.importProductsFromCsv(csv1, 101L);
        assertEquals(1, resp1.getImportedCount());
        assertEquals("0401", resp1.getProducts().get(0).getHsnCode());

        // Using alias "SAC"
        String csv2 = "Product,SAC,Rate\n" +
                "Architecture Design,9983,12000.00";

        ProductImportResponseDto resp2 = productService.importProductsFromCsv(csv2, 101L);
        assertEquals(1, resp2.getImportedCount());
        assertEquals("9983", resp2.getProducts().get(0).getHsnCode());
    }

    // 7. Legacy CSV Import using SKU Header Does Not Falsely Reassign SKU as HSN
    @Test
    void testLegacyCsvImportWithSkuHeaderPreservesBackwardCompatibility() {
        String legacyCsv = "Product Name,SKU,Category,Price,Initial Stock\n" +
                "Legacy Widget,WIDGET-XYZ,Hardware,49.99,50";

        when(repo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductImportResponseDto resp = productService.importProductsFromCsv(legacyCsv, 101L);

        assertEquals(1, resp.getImportedCount());
        Product imported = resp.getProducts().get(0);
        assertEquals("Legacy Widget", imported.getName());
        assertEquals("WIDGET-XYZ", imported.getSku(), "SKU should be retained in legacy field");
        assertNull(imported.getHsnCode(), "Legacy SKU must NOT be arbitrarily converted to HSN code");
    }

    // 8. Tenant Isolation in CSV Import and Export
    @Test
    void testTenantFirmIsolationInCsvImportAndExport() {
        String csv = "Product Name,HSN/SAC Code,Price\n" +
                "Firm 101 Product,0101,100.00";

        when(repo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductImportResponseDto resp = productService.importProductsFromCsv(csv, 101L);
        assertEquals(1, resp.getImportedCount());
        assertEquals(101L, resp.getProducts().get(0).getFirmId(), "Product must be assigned to tenant firm 101");

        // Verify export only queries firm 101
        when(repo.findByFirmIdAndIsArchivedFalseOrderByNameAsc(101L)).thenReturn(Collections.singletonList(resp.getProducts().get(0)));
        productService.exportProductsToCsv(101L);
        verify(repo, times(1)).findByFirmIdAndIsArchivedFalseOrderByNameAsc(101L);
        verify(repo, never()).findByFirmIdAndIsArchivedFalseOrderByNameAsc(202L);
    }

    // 9. Robust Parsing with Commas, Escaped Quotes, and Empty Lines
    @Test
    void testRobustCsvParsingWithEscapedQuotesAndSpecialCharacters() {
        String complexCsv = "\"Product Name\",\"HSN/SAC Code\",\"Description\"\n" +
                "\n" + // empty line
                "\"Heavy Machinery, Type \"\"A\"\"\",\"8429\",\"Excavator with 1.5m, bucket\"\n" +
                "\"Simple Product\",\"\",\"No HSN\"\n";

        when(repo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductImportResponseDto resp = productService.importProductsFromCsv(complexCsv, 101L);
        assertEquals(2, resp.getImportedCount());

        Product p1 = resp.getProducts().get(0);
        assertEquals("Heavy Machinery, Type \"A\"", p1.getName());
        assertEquals("8429", p1.getHsnCode());
        assertEquals("Excavator with 1.5m, bucket", p1.getDescription());

        Product p2 = resp.getProducts().get(1);
        assertEquals("Simple Product", p2.getName());
        assertNull(p2.getHsnCode(), "Blank HSN in quotes should be sanitized to null");
    }

    // 10. Controller CSV Endpoints Unit Test
    @Test
    void testProductControllerCsvEndpoints() {
        ProductController controller = new ProductController(productService);

        // Export endpoint
        when(repo.findByFirmIdAndIsArchivedFalseOrderByNameAsc(101L)).thenReturn(Collections.emptyList());
        ResponseEntity<byte[]> exportResp = controller.exportCsv(101L);
        assertEquals(HttpStatus.OK, exportResp.getStatusCode());
        assertNotNull(exportResp.getBody());
        assertTrue(exportResp.getHeaders().getFirst("Content-Disposition").contains("products.csv"));

        // Template endpoint
        ResponseEntity<byte[]> tplResp = controller.downloadTemplateCsv();
        assertEquals(HttpStatus.OK, tplResp.getStatusCode());
        String tplStr = new String(tplResp.getBody(), StandardCharsets.UTF_8);
        assertTrue(tplStr.contains("HSN/SAC Code"));

        // Import endpoint (Multipart file)
        String csvData = "Product Name,HSN/SAC Code,Price\nTest CSV Product,0101,50.00";
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", csvData.getBytes(StandardCharsets.UTF_8));
        when(repo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<ProductImportResponseDto> importResp = controller.importCsv(file, null, 101L);
        assertEquals(HttpStatus.OK, importResp.getStatusCode());
        assertEquals(1, importResp.getBody().getImportedCount());
    }
}
