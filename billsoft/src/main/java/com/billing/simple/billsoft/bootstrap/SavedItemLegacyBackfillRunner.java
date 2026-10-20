package com.billing.simple.billsoft.bootstrap;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.entities.SavedItem;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.repo.SavedItemRepository;

/**
 * Idempotent startup runner to safely backfill legacy unpromoted SavedItems
 * into canonical Product records with category = 'UNCATEGORIZED'.
 */
@Component
@Order(100)
public class SavedItemLegacyBackfillRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SavedItemLegacyBackfillRunner.class);

    private final SavedItemRepository savedItemRepo;
    private final ProductRepository productRepo;

    public SavedItemLegacyBackfillRunner(SavedItemRepository savedItemRepo, ProductRepository productRepo) {
        this.savedItemRepo = savedItemRepo;
        this.productRepo = productRepo;
    }

    @Override
    @Transactional
    public void run(String... args) {
        try {
            List<SavedItem> unpromoted = savedItemRepo.findAll().stream()
                    .filter(s -> !Boolean.TRUE.equals(s.getIsArchived()) && s.getCanonicalProductId() == null)
                    .collect(Collectors.toList());

            if (unpromoted.isEmpty()) {
                return;
            }

            log.info("Starting one-time backfill of {} legacy saved items into canonical products", unpromoted.size());
            int converted = 0;

            for (SavedItem item : unpromoted) {
                Long firmId = item.getFirmId();
                if (firmId == null || item.getName() == null || item.getName().trim().isEmpty()) {
                    continue;
                }

                String normalized = SavedItem.normalizeText(item.getName());
                Optional<Product> existing = productRepo.findByFirmId(firmId).stream()
                        .filter(p -> !Boolean.TRUE.equals(p.getIsArchived()) &&
                                     SavedItem.normalizeText(p.getName()).equals(normalized))
                        .findFirst();

                Product target;
                if (existing.isPresent()) {
                    target = existing.get();
                } else {
                    target = productRepo.save(Product.builder()
                            .firmId(firmId)
                            .name(item.getName().trim())
                            .category("UNCATEGORIZED")
                            .itemType("GOODS")
                            .price(item.getSellingPrice() != null ? item.getSellingPrice() : BigDecimal.ZERO)
                            .costPrice(null)
                            .unit(item.getUnit() != null && !item.getUnit().trim().isEmpty() ? item.getUnit().trim() : "pcs")
                            .hsnCode(item.getHsnCode() != null && !item.getHsnCode().trim().isEmpty() ? item.getHsnCode().trim() : null)
                            .gstPercentage(item.getGstPercentage())
                            .stockQuantity(BigDecimal.ZERO)
                            .minStockLevel(new BigDecimal("5.000"))
                            .isArchived(false)
                            .build());
                    converted++;
                }
                item.setCanonicalProductId(target.getId());
                savedItemRepo.save(item);
            }

            log.info("Completed legacy saved items backfill. Created {} new canonical UNCATEGORIZED products.", converted);
        } catch (Exception ex) {
            log.warn("Non-fatal issue during legacy saved items backfill: {}", ex.getMessage());
        }
    }
}
