package com.billing.simple.billsoft.regression.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.billing.simple.billsoft.dtos.PageResponse;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.service.ProductService;
import com.billing.simple.billsoft.util.PaginationUtils;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class PaginationBoundaryAndHiddenLimitRegressionTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private FirmDetailsRepository firmRepo;

    private Long firmId;

    @BeforeEach
    void setup() {
        FirmDetails firm = firmRepo.findAll().stream().findFirst().orElseGet(() -> {
            FirmDetails f = new FirmDetails();
            f.setFirmName("Pagination Regression Firm");
            return firmRepo.save(f);
        });
        firmId = firm.getId();
        TenantContext.setCurrentFirmId(firmId);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testPaginationBoundary_ZeroRecords() {
        Pageable p0 = PaginationUtils.createPageRequest(0, 25, Sort.unsorted());
        PageResponse<Product> resp = productService.getPaginatedProducts(firmId, null, p0);
        assertThat(resp.getContent()).isEmpty();
        assertThat(resp.getTotalElements()).isZero();
        assertThat(resp.getTotalPages()).isZero();
    }

    @Test
    void testPaginationBoundary_Exact_25_And_26_Records() {
        // Create 26 products
        List<Product> products = new ArrayList<>();
        for (int i = 1; i <= 26; i++) {
            products.add(Product.builder()
                    .name("Boundary Product " + (i < 10 ? "0" + i : i))
                    .price(new BigDecimal("100.00"))
                    .stockQuantity(BigDecimal.TEN)
                    .firmId(firmId)
                    .build());
        }
        productRepo.saveAll(products);

        // Page 0 (should have 25 items)
        Pageable p0 = PaginationUtils.createPageRequest(0, 25, Sort.by("name").ascending());
        PageResponse<Product> page0 = productService.getPaginatedProducts(firmId, null, p0);
        assertThat(page0.getContent()).hasSize(25);
        assertThat(page0.getTotalElements()).isEqualTo(26);
        assertThat(page0.getTotalPages()).isEqualTo(2);

        // Page 1 (should have 1 item: the 26th record)
        Pageable p1 = PaginationUtils.createPageRequest(1, 25, Sort.by("name").ascending());
        PageResponse<Product> page1 = productService.getPaginatedProducts(firmId, null, p1);
        assertThat(page1.getContent()).hasSize(1);
        assertThat(page1.getContent().get(0).getName()).isEqualTo("Boundary Product 26");
    }

    @Test
    void testPagination_SearchFilteringAndZeroTruncation() {
        List<Product> products = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            products.add(Product.builder()
                    .name(i == 99 ? "Rare Special Item" : "Standard SKU " + i)
                    .price(new BigDecimal("50.00"))
                    .stockQuantity(BigDecimal.TEN)
                    .firmId(firmId)
                    .build());
        }
        productRepo.saveAll(products);

        // Search for rare item near the very end of 100 items
        Pageable p0 = PaginationUtils.createPageRequest(0, 25, Sort.unsorted());
        PageResponse<Product> searchResp = productService.getPaginatedProducts(firmId, "Rare Special", p0);
        assertThat(searchResp.getContent()).hasSize(1);
        assertThat(searchResp.getContent().get(0).getName()).isEqualTo("Rare Special Item");
    }

    @Test
    void testExactPaginationOffByOneBoundaries() {
        int[] targetSizes = {0, 1, 24, 25, 26, 99, 100, 101, 199, 200, 201, 500, 1000, 5000};
        int pageSize = 25;

        for (int size : targetSizes) {
            FirmDetails tenantFirm = new FirmDetails();
            tenantFirm.setFirmName("Pagination Boundary Firm Size " + size);
            tenantFirm = firmRepo.save(tenantFirm);
            Long tenantFirmId = tenantFirm.getId();

            if (size > 0) {
                List<Product> products = new ArrayList<>(size);
                for (int i = 1; i <= size; i++) {
                    products.add(Product.builder()
                            .name(String.format("P-%04d-%04d", size, i))
                            .price(new BigDecimal("100.00"))
                            .stockQuantity(BigDecimal.TEN)
                            .firmId(tenantFirmId)
                            .build());
                }
                productRepo.saveAll(products);
            }

            // 1 & 2. Page 0 request and element / total pages assertion
            Pageable p0 = PaginationUtils.createPageRequest(0, pageSize, Sort.by("name").ascending());
            PageResponse<Product> page0 = productService.getPaginatedProducts(tenantFirmId, null, p0);

            assertThat(page0.getTotalElements()).isEqualTo(size);
            int expectedTotalPages = size == 0 ? 0 : (int) Math.ceil((double) size / pageSize);
            assertThat(page0.getTotalPages()).isEqualTo(expectedTotalPages);

            if (size == 0) {
                assertThat(page0.getContent()).isEmpty();
                continue;
            }

            // 3. First page content and count
            int expectedFirstPageCount = Math.min(pageSize, size);
            assertThat(page0.getContent()).hasSize(expectedFirstPageCount);
            assertThat(page0.getContent().get(0).getName()).isEqualTo(String.format("P-%04d-0001", size));

            if (expectedTotalPages > 1) {
                // 4. Intermediate page where applicable
                int midPageIdx = expectedTotalPages / 2;
                Pageable pMid = PaginationUtils.createPageRequest(midPageIdx, pageSize, Sort.by("name").ascending());
                PageResponse<Product> pageMid = productService.getPaginatedProducts(tenantFirmId, null, pMid);
                assertThat(pageMid.getContent()).isNotEmpty();
                int expectedMidStart = midPageIdx * pageSize + 1;
                assertThat(pageMid.getContent().get(0).getName()).isEqualTo(String.format("P-%04d-%04d", size, expectedMidStart));

                // 5. Last page records and identity
                int lastPageIdx = expectedTotalPages - 1;
                Pageable pLast = PaginationUtils.createPageRequest(lastPageIdx, pageSize, Sort.by("name").ascending());
                PageResponse<Product> pageLast = productService.getPaginatedProducts(tenantFirmId, null, pLast);
                int expectedLastPageCount = (size % pageSize == 0) ? pageSize : (size % pageSize);
                assertThat(pageLast.getContent()).hasSize(expectedLastPageCount);
                assertThat(pageLast.getContent().get(pageLast.getContent().size() - 1).getName())
                        .isEqualTo(String.format("P-%04d-%04d", size, size));
            }

            // 6, 7, 8, 9, 10. For datasets up to 201 items, iterate all pages to assert full completeness, no duplicates, no missing items
            if (size <= 201) {
                List<String> allNames = new ArrayList<>();
                for (int pageIdx = 0; pageIdx < expectedTotalPages; pageIdx++) {
                    Pageable pReq = PaginationUtils.createPageRequest(pageIdx, pageSize, Sort.by("name").ascending());
                    PageResponse<Product> pResp = productService.getPaginatedProducts(tenantFirmId, null, pReq);
                    for (Product p : pResp.getContent()) {
                        allNames.add(p.getName());
                    }
                }
                assertThat(allNames).hasSize(size);
                Set<String> distinctSet = Set.copyOf(allNames);
                assertThat(distinctSet).hasSize(size);
                assertThat(allNames.get(0)).isEqualTo(String.format("P-%04d-0001", size));
                assertThat(allNames.get(size - 1)).isEqualTo(String.format("P-%04d-%04d", size, size));
            }
        }
    }
}
