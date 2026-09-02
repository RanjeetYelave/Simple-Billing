package com.billing.simple.billsoft.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.entities.StockMovement;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.repo.StockMovementRepository;

@Service
public class ProductService {

	private final ProductRepository repo;
	private final StockMovementRepository stockMovementRepo;

	public ProductService(ProductRepository repo, StockMovementRepository stockMovementRepo) {
		this.repo = repo;
		this.stockMovementRepo = stockMovementRepo;
	}

	@Transactional
	public Product create(Product product) {
		if (product.getStockQuantity() == null) {
			product.setStockQuantity(BigDecimal.ZERO);
		}
		if (product.getMinStockLevel() == null) {
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
		return repo.findByFirmId(firmId);
	}

	public Product getById(Long id) {
		return repo.findById(id).orElse(null);
	}

	@Transactional
	public Product update(Long id, Product updated) {
		Optional<Product> opt = repo.findById(id);
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
		existing.setMinStockLevel(updated.getMinStockLevel() != null ? updated.getMinStockLevel() : new BigDecimal("5.000"));
		existing.setDescription(updated.getDescription());
		return repo.save(existing);
	}

	@Transactional
	public boolean delete(Long id) {
		if (!repo.existsById(id))
			return false;
		repo.deleteById(id);
		return true;
	}

	/**
	 * Adjusts stock directly from inventory manager (Add / Deduct / Set).
	 */
	@Transactional
	public Product adjustStock(Long id, BigDecimal quantity, String mode, String note) {
		Optional<Product> opt = repo.findById(id);
		if (opt.isEmpty())
			return null;

		Product product = opt.get();
		BigDecimal prevStock = product.getStockQuantity() != null ? product.getStockQuantity() : BigDecimal.ZERO;
		BigDecimal change = BigDecimal.ZERO;
		BigDecimal newStock = prevStock;

		if (quantity == null) {
			quantity = BigDecimal.ZERO;
		}

		String movementType = "MANUAL_ADJUSTMENT";
		if ("ADD".equalsIgnoreCase(mode)) {
			change = quantity;
			newStock = prevStock.add(quantity);
		} else if ("SUBTRACT".equalsIgnoreCase(mode) || "DEDUCT".equalsIgnoreCase(mode)) {
			change = quantity.negate();
			newStock = prevStock.subtract(quantity);
		} else { // "SET"
			newStock = quantity;
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

		Optional<Product> opt = repo.findById(productId);
		if (opt.isEmpty()) {
			return;
		}

		Product product = opt.get();
		// Skip stock deduction for service items
		if ("SERVICE".equalsIgnoreCase(product.getItemType())) {
			return;
		}

		BigDecimal prevStock = product.getStockQuantity() != null ? product.getStockQuantity() : BigDecimal.ZERO;
		BigDecimal newStock = prevStock.add(quantityChange);
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
		if (productId != null && firmId != null) {
			return stockMovementRepo.findByProductIdAndFirmIdOrderByCreatedAtDesc(productId, firmId);
		} else if (productId != null) {
			return stockMovementRepo.findByProductIdOrderByCreatedAtDesc(productId);
		} else if (firmId != null) {
			return stockMovementRepo.findByFirmIdOrderByCreatedAtDesc(firmId);
		}
		return Collections.emptyList();
	}

	public List<String> getCategories(Long firmId) {
		return repo.findDistinctCategoriesByFirmId(firmId);
	}

	public Map<String, Object> getInventorySummary(Long firmId) {
		List<Product> list = repo.findByFirmId(firmId);
		long totalProducts = list.size();
		BigDecimal totalStockUnits = BigDecimal.ZERO;
		BigDecimal totalValuationAtCost = BigDecimal.ZERO;
		BigDecimal totalValuationAtRetail = BigDecimal.ZERO;
		long lowStockCount = 0;
		long outOfStockCount = 0;
		long servicesCount = 0;
		Set<String> categories = new HashSet<>();

		for (Product p : list) {
			if (p.getCategory() != null && !p.getCategory().trim().isEmpty()) {
				categories.add(p.getCategory().trim());
			}

			if ("SERVICE".equalsIgnoreCase(p.getItemType())) {
				servicesCount++;
				continue;
			}

			BigDecimal stock = p.getStockQuantity() != null ? p.getStockQuantity() : BigDecimal.ZERO;
			BigDecimal minStock = p.getMinStockLevel() != null ? p.getMinStockLevel() : new BigDecimal("5.000");
			BigDecimal price = p.getPrice() != null ? p.getPrice() : BigDecimal.ZERO;
			BigDecimal cost = p.getCostPrice() != null ? p.getCostPrice() : BigDecimal.ZERO;

			totalStockUnits = totalStockUnits.add(stock);
			totalValuationAtCost = totalValuationAtCost.add(cost.multiply(stock));
			totalValuationAtRetail = totalValuationAtRetail.add(price.multiply(stock));

			if (stock.compareTo(BigDecimal.ZERO) <= 0) {
				outOfStockCount++;
			} else if (stock.compareTo(minStock) <= 0) {
				lowStockCount++;
			}
		}

		BigDecimal estimatedMarginPercent = BigDecimal.ZERO;
		if (totalValuationAtRetail.compareTo(BigDecimal.ZERO) > 0) {
			BigDecimal profit = totalValuationAtRetail.subtract(totalValuationAtCost);
			estimatedMarginPercent = profit.multiply(new BigDecimal("100"))
					.divide(totalValuationAtRetail, 2, RoundingMode.HALF_UP);
		}

		Map<String, Object> summary = new LinkedHashMap<>();
		summary.put("totalProducts", totalProducts);
		summary.put("totalStockUnits", totalStockUnits);
		summary.put("totalValuationAtCost", totalValuationAtCost.setScale(2, RoundingMode.HALF_UP));
		summary.put("totalValuationAtRetail", totalValuationAtRetail.setScale(2, RoundingMode.HALF_UP));
		summary.put("estimatedMarginPercent", estimatedMarginPercent);
		summary.put("lowStockCount", lowStockCount);
		summary.put("outOfStockCount", outOfStockCount);
		summary.put("servicesCount", servicesCount);
		summary.put("categoriesCount", categories.size());
		summary.put("categories", new ArrayList<>(categories));
		return summary;
	}
}
