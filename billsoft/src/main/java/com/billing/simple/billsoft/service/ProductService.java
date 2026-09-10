package com.billing.simple.billsoft.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billing.simple.billsoft.dtos.PageResponse;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.entities.StockMovement;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.repo.StockMovementRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
public class ProductService {

	private final ProductRepository repo;
	private final StockMovementRepository stockMovementRepo;

	public ProductService(ProductRepository repo, StockMovementRepository stockMovementRepo) {
		this.repo = repo;
		this.stockMovementRepo = stockMovementRepo;
	}

	@PostConstruct
	public void sanitizeNegativeStock() {
		try {
			List<Product> all = repo.findAll();
			for (Product p : all) {
				if (p.getStockQuantity() != null && p.getStockQuantity().compareTo(BigDecimal.ZERO) < 0) {
					p.setStockQuantity(BigDecimal.ZERO);
					repo.save(p);
				}
			}
		} catch (Exception ignored) {
		}
	}

	@Transactional
	public Product create(Product product) {
		Long currentFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
		if (currentFirmId != null) {
			product.setFirmId(currentFirmId);
		}
		if (product.getStockQuantity() == null || product.getStockQuantity().compareTo(BigDecimal.ZERO) < 0) {
			product.setStockQuantity(BigDecimal.ZERO);
		}
		if (product.getMinStockLevel() == null || product.getMinStockLevel().compareTo(BigDecimal.ZERO) < 0) {
			product.setMinStockLevel(new BigDecimal("5.000"));
		}
		if (product.getItemType() == null || product.getItemType().trim().isEmpty()) {
			product.setItemType("GOODS");
		}
		if (product.getUnit() == null || product.getUnit().trim().isEmpty()) {
			product.setUnit("pcs");
		}

		Product saved = repo.save(product);

		// Record initial opening stock ledger if > 0
		if (saved.getStockQuantity() != null && saved.getStockQuantity().compareTo(BigDecimal.ZERO) > 0 && !"SERVICE".equalsIgnoreCase(saved.getItemType())) {
			StockMovement movement = StockMovement.builder()
					.productId(saved.getId())
					.productName(saved.getName())
					.firmId(saved.getFirmId())
					.movementType("INITIAL_STOCK")
					.quantityChange(saved.getStockQuantity())
					.previousStock(BigDecimal.ZERO)
					.newStock(saved.getStockQuantity())
					.referenceType("MANUAL")
					.referenceId("OPENING_BALANCE")
					.note("Initial opening stock recorded on creation")
					.createdAt(LocalDateTime.now())
					.build();
			stockMovementRepo.save(movement);
		}

		return saved;
	}

	public List<Product> getAll(Long firmId) {
		Long targetFirmId = firmId != null ? firmId : com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
		if (targetFirmId == null) {
			return Collections.emptyList();
		}
		return repo.findByFirmId(targetFirmId);
	}

	public PageResponse<Product> getPaginatedProducts(Long firmId, String search, Pageable pageable) {
		Long targetFirmId = firmId != null ? firmId : com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
		if (targetFirmId == null) {
			return PageResponse.empty(pageable.getPageNumber(), pageable.getPageSize());
		}
		Page<Product> page;
		if (search != null && !search.trim().isEmpty()) {
			page = repo.findByFirmIdAndNameContainingIgnoreCase(targetFirmId, search.trim(), pageable);
		} else {
			page = repo.findByFirmId(targetFirmId, pageable);
		}
		return PageResponse.of(page);
	}

	public Product getById(Long id) {
		Long fid = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
		return fid != null ? repo.findByIdAndFirmId(id, fid).orElse(null) : repo.findById(id).orElse(null);
	}

	public Product getById(Long id, Long firmId) {
		Long fid = firmId != null ? firmId : com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
		return fid != null ? repo.findByIdAndFirmId(id, fid).orElse(null) : repo.findById(id).orElse(null);
	}

	@Transactional
	public Product update(Long id, Product updated) {
		Long fid = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
		Optional<Product> opt = fid != null ? repo.findByIdAndFirmId(id, fid) : repo.findById(id);
		if (opt.isEmpty())
			return null;
		Product existing = opt.get();
		existing.setName(updated.getName());
		existing.setPrice(updated.getPrice());
		existing.setCostPrice(updated.getCostPrice());
		existing.setUnit(updated.getUnit());
		existing.setHsnCode(updated.getHsnCode());
		existing.setGstPercentage(updated.getGstPercentage());
		existing.setSku(updated.getSku());
		existing.setBarcode(updated.getBarcode());
		existing.setCategory(updated.getCategory());
		existing.setItemType(updated.getItemType() != null ? updated.getItemType() : "GOODS");
		existing.setMinStockLevel(updated.getMinStockLevel() != null && updated.getMinStockLevel().compareTo(BigDecimal.ZERO) >= 0 ? updated.getMinStockLevel() : new BigDecimal("5.000"));
		existing.setDescription(updated.getDescription());
		if (updated.getStockQuantity() != null) {
			existing.setStockQuantity(updated.getStockQuantity().compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : updated.getStockQuantity());
		}
		return repo.save(existing);
	}

	@Transactional
	public boolean delete(Long id) {
		Long fid = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
		if (fid != null) {
			if (!repo.existsByIdAndFirmId(id, fid))
				return false;
			repo.deleteByIdAndFirmId(id, fid);
			return true;
		} else {
			if (!repo.existsById(id))
				return false;
			repo.deleteById(id);
			return true;
		}
	}


	/**
	 * Adjusts stock directly from inventory manager (Add / Deduct / Set).
	 */
	@Transactional
	public Product adjustStock(Long id, BigDecimal quantity, String mode, String note) {
		Long fid = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
		Optional<Product> opt;
		if (fid != null) {
			opt = repo.findByIdAndFirmId(id, fid);
			if (opt.isEmpty()) {
				throw new com.billing.simple.billsoft.security.TenantSecurityException("Product not found or unauthorized for firm: " + fid);
			}
		} else {
			opt = repo.findById(id);
		}
		if (opt.isEmpty())
			return null;

		Product product = opt.get();
		BigDecimal prevStock = product.getStockQuantity() != null ? product.getStockQuantity() : BigDecimal.ZERO;
		if (prevStock.compareTo(BigDecimal.ZERO) < 0) {
			prevStock = BigDecimal.ZERO;
		}
		BigDecimal change = BigDecimal.ZERO;
		BigDecimal newStock = prevStock;

		if (quantity == null || quantity.compareTo(BigDecimal.ZERO) < 0) {
			quantity = BigDecimal.ZERO;
		}

		String movementType = "MANUAL_ADJUSTMENT";
		if ("ADD".equalsIgnoreCase(mode)) {
			change = quantity;
			newStock = prevStock.add(quantity);
		} else if ("SUBTRACT".equalsIgnoreCase(mode) || "DEDUCT".equalsIgnoreCase(mode)) {
			newStock = prevStock.subtract(quantity);
			if (newStock.compareTo(BigDecimal.ZERO) < 0) {
				newStock = BigDecimal.ZERO;
			}
			change = newStock.subtract(prevStock);
		} else { // "SET"
			newStock = quantity.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : quantity;
			change = newStock.subtract(prevStock);
		}

		product.setStockQuantity(newStock);
		Product saved = repo.save(product);

		StockMovement movement = StockMovement.builder()
				.productId(saved.getId())
				.productName(saved.getName())
				.firmId(saved.getFirmId())
				.movementType(movementType)
				.quantityChange(change)
				.previousStock(prevStock)
				.newStock(newStock)
				.referenceType("MANUAL")
				.referenceId("ADJUSTMENT")
				.note(note != null && !note.trim().isEmpty() ? note.trim() : "Manual stock adjustment (" + mode + ")")
				.createdAt(LocalDateTime.now())
				.build();
		stockMovementRepo.save(movement);

		return saved;
	}

	/**
	 * Programmatic stock ledger update for Invoices, Purchase Orders, and Returns.
	 */
	@Transactional
	public void recordStockMovement(Long productId, Long firmId, String movementType, BigDecimal quantityChange, String referenceType, String referenceId, String note) {
		if (productId == null || quantityChange == null || quantityChange.compareTo(BigDecimal.ZERO) == 0) {
			return;
		}

		Optional<Product> opt = (firmId != null) ? repo.findByIdAndFirmId(productId, firmId) : repo.findById(productId);
		if (opt.isEmpty()) {
			return;
		}

		Product product = opt.get();
		// Skip stock deduction for service items
		if ("SERVICE".equalsIgnoreCase(product.getItemType())) {
			return;
		}

		BigDecimal prevStock = product.getStockQuantity() != null ? product.getStockQuantity() : BigDecimal.ZERO;
		if (prevStock.compareTo(BigDecimal.ZERO) < 0) {
			prevStock = BigDecimal.ZERO;
		}
		BigDecimal newStock = prevStock.add(quantityChange);
		if (newStock.compareTo(BigDecimal.ZERO) < 0) {
			newStock = BigDecimal.ZERO;
		}
		product.setStockQuantity(newStock);
		repo.save(product);

		StockMovement movement = StockMovement.builder()
				.productId(product.getId())
				.productName(product.getName())
				.firmId(firmId != null ? firmId : product.getFirmId())
				.movementType(movementType)
				.quantityChange(quantityChange)
				.previousStock(prevStock)
				.newStock(newStock)
				.referenceType(referenceType)
				.referenceId(referenceId)
				.note(note)
				.createdAt(LocalDateTime.now())
				.build();
		stockMovementRepo.save(movement);
	}

	public List<StockMovement> getStockMovements(Long productId, Long firmId) {
		Long fid = firmId != null ? firmId : com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
		if (fid != null && productId != null) {
			return stockMovementRepo.findByProductIdAndFirmIdOrderByCreatedAtDesc(productId, fid);
		} else if (fid != null) {
			return stockMovementRepo.findByFirmIdOrderByCreatedAtDesc(fid);
		}
		return Collections.emptyList();
	}

	public PageResponse<StockMovement> getPaginatedMovements(Long productId, Long firmId, Pageable pageable) {
		Long fid = firmId != null ? firmId : com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
		if (fid == null) {
			return PageResponse.empty(pageable.getPageNumber(), pageable.getPageSize());
		}
		Page<StockMovement> page;
		if (productId != null) {
			page = stockMovementRepo.findByProductIdAndFirmId(productId, fid, pageable);
		} else {
			page = stockMovementRepo.findByFirmId(fid, pageable);
		}
		return PageResponse.of(page);
	}


	public List<String> getCategories(Long firmId) {
		Long fid = firmId != null ? firmId : com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
		return fid != null ? repo.findDistinctCategoriesByFirmId(fid) : Collections.emptyList();
	}

	public Map<String, Object> getInventorySummary(Long firmId) {
		Long fid = firmId != null ? firmId : com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
		if (fid == null) {
			Map<String, Object> emptySummary = new HashMap<>();
			emptySummary.put("totalProducts", 0L);
			emptySummary.put("goodsCount", 0L);
			emptySummary.put("servicesCount", 0L);
			emptySummary.put("lowStockCount", 0L);
			emptySummary.put("outOfStockCount", 0L);
			emptySummary.put("totalRetailValue", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
			emptySummary.put("totalCostValue", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
			emptySummary.put("averageGrossMarginPercent", BigDecimal.ZERO);
			return emptySummary;
		}
		List<Product> list = repo.findByFirmId(fid);

		long totalProducts = list.size();
		long goodsCount = 0;
		long servicesCount = 0;
		long lowStockCount = 0;
		long outOfStockCount = 0;

		BigDecimal totalRetailValue = BigDecimal.ZERO;
		BigDecimal totalCostValue = BigDecimal.ZERO;

		BigDecimal totalMarginSum = BigDecimal.ZERO;
		int marginProductCount = 0;

		for (Product p : list) {
			if ("SERVICE".equalsIgnoreCase(p.getItemType())) {
				servicesCount++;
				continue;
			}
			goodsCount++;

			BigDecimal stock = p.getStockQuantity() != null ? p.getStockQuantity() : BigDecimal.ZERO;
			BigDecimal minLevel = p.getMinStockLevel() != null ? p.getMinStockLevel() : new BigDecimal("5.000");
			BigDecimal price = p.getPrice() != null ? p.getPrice() : BigDecimal.ZERO;
			BigDecimal cost = p.getCostPrice() != null ? p.getCostPrice() : BigDecimal.ZERO;

			if (stock.compareTo(BigDecimal.ZERO) <= 0) {
				outOfStockCount++;
			} else if (stock.compareTo(minLevel) <= 0) {
				lowStockCount++;
			}

			if (stock.compareTo(BigDecimal.ZERO) > 0) {
				totalRetailValue = totalRetailValue.add(price.multiply(stock));
				totalCostValue = totalCostValue.add(cost.multiply(stock));
			}

			if (price.compareTo(BigDecimal.ZERO) > 0 && cost.compareTo(BigDecimal.ZERO) > 0) {
				BigDecimal margin = price.subtract(cost).divide(price, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
				totalMarginSum = totalMarginSum.add(margin);
				marginProductCount++;
			}
		}

		BigDecimal avgGrossMargin = marginProductCount > 0
				? totalMarginSum.divide(new BigDecimal(marginProductCount), 1, RoundingMode.HALF_UP)
				: BigDecimal.ZERO;

		Map<String, Object> summary = new HashMap<>();
		summary.put("totalProducts", totalProducts);
		summary.put("goodsCount", goodsCount);
		summary.put("servicesCount", servicesCount);
		summary.put("lowStockCount", lowStockCount);
		summary.put("outOfStockCount", outOfStockCount);
		summary.put("totalRetailValue", totalRetailValue.setScale(2, RoundingMode.HALF_UP));
		summary.put("totalCostValue", totalCostValue.setScale(2, RoundingMode.HALF_UP));
		summary.put("averageGrossMarginPercent", avgGrossMargin);

		return summary;
	}
}
