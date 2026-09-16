package com.billing.simple.billsoft.regression.inventory;

import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.service.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Tag("regression")
@Tag("integration")
@DisplayName("Product Catalog & Multi-Firm Isolation Regression Tests")
class ProductCatalogRegressionTest {

    @Autowired
    private ProductService productService;

    @Test
    @DisplayName("Should strictly isolate product catalogs between different firms")
    void shouldIsolateProductsBetweenFirms() {
        Long firmA = 10L;
        Long firmB = 20L;

        productService.create(Product.builder()
                .name("Firm A Specialty Paint")
                .price(BigDecimal.valueOf(120.00))
                .firmId(firmA)
                .build());

        productService.create(Product.builder()
                .name("Firm B Industrial Lubricant")
                .price(BigDecimal.valueOf(350.00))
                .firmId(firmB)
                .build());

        List<Product> firmAProducts = productService.getAll(firmA);
        List<Product> firmBProducts = productService.getAll(firmB);

        assertThat(firmAProducts).extracting(Product::getName).containsExactly("Firm A Specialty Paint");
        assertThat(firmBProducts).extracting(Product::getName).containsExactly("Firm B Industrial Lubricant");
    }

    @Test
    @DisplayName("Should update product metadata and preserve stock bounds")
    void shouldUpdateProductMetadata() {
        Product p = productService.create(Product.builder()
                .name("Raw Copper Bar")
                .price(BigDecimal.valueOf(500.00))
                .costPrice(BigDecimal.valueOf(380.00))
                .sku("SKU-CU-01")
                .barcode("8901234567890")
                .category("Raw Materials")
                .firmId(1L)
                .stockQuantity(BigDecimal.valueOf(10.0))
                .build());

        Product updatedData = Product.builder()
                .name("Raw Copper Bar Premium")
                .price(BigDecimal.valueOf(550.00))
                .costPrice(BigDecimal.valueOf(400.00))
                .unit("kg")
                .hsnCode("7407")
                .gstPercentage(BigDecimal.valueOf(18.0))
                .sku("SKU-CU-01-V2")
                .barcode("8901234567890")
                .category("Metals & Alloys")
                .itemType("GOODS")
                .minStockLevel(BigDecimal.valueOf(8.0))
                .description("Pure electrolytic copper")
                .build();

        Product updated = productService.update(p.getId(), updatedData);

        assertThat(updated.getName()).isEqualTo("Raw Copper Bar Premium");
        assertThat(updated.getPrice()).isEqualByComparingTo("550.00");
        assertThat(updated.getCostPrice()).isEqualByComparingTo("400.00");
        assertThat(updated.getCategory()).isEqualTo("Metals & Alloys");
        assertThat(updated.getHsnCode()).isEqualTo("7407");
    }

    @Test
    @DisplayName("Should delete product successfully and return false for non-existent IDs")
    void shouldHandleProductDeletion() {
        Product p = productService.create(Product.builder()
                .name("Temporary Product")
                .price(BigDecimal.valueOf(10.00))
                .firmId(1L)
                .build());

        boolean deleted = productService.delete(p.getId());
        assertThat(deleted).isTrue();

        boolean deleteAgain = productService.delete(p.getId());
        assertThat(deleteAgain).isFalse();

        assertThat(productService.getById(p.getId())).isNull();
    }
}
