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
import com.billing.simple.billsoft.repo.InvoiceItemRepository;
import com.billing.simple.billsoft.repo.SalesReturnItemRepository;
import com.billing.simple.billsoft.repositories.PartyRepository;
import com.billing.simple.billsoft.dtos.ProductBulkUpdateItemDto;
import com.billing.simple.billsoft.dtos.ProductBulkUpdateResponseDto;
import com.billing.simple.billsoft.entities.AppConfig;
import com.billing.simple.billsoft.exception.InsufficientStockException;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
public class ProductService {

	private final ProductRepository repo;
	private final StockMovementRepository stockMovementRepo;
	private final InvoiceItemRepository invoiceItemRepo;
	private final SalesReturnItemRepository salesReturnItemRepo;
	private final PartyRepository partyRepo;
	private final AppConfigRepository appConfigRepo;

	@jakarta.persistence.PersistenceContext
	private jakarta.persistence.EntityManager entityManager;

	public ProductService(ProductRepository repo, StockMovementRepository stockMovementRepo,
			InvoiceItemRepository invoiceItemRepo, SalesReturnItemRepository salesReturnItemRepo,
			PartyRepository partyRepo,
			@org.springframework.beans.factory.annotation.Autowired(required = false) AppConfigRepository appConfigRepo) {
		this.repo = repo;
		this.stockMovementRepo = stockMovementRepo;
		this.invoiceItemRepo = invoiceItemRepo;
		this.salesReturnItemRepo = salesReturnItemRepo;
		this.partyRepo = partyRepo;
		this.appConfigRepo = appConfigRepo;
	}

	public boolean isAllowNegativeStock(Long firmId) {
		if (appConfigRepo == null) return false;
		Long targetFirmId = resolveFirmId(firmId);
		String configKey = "INVENTORY_ALLOW_NEGATIVE_STOCK_" + (targetFirmId != null ? targetFirmId : 1L);
		return appConfigRepo.findById(configKey)
				.map(AppConfig::getConfigValue)
				.map(v -> "true".equalsIgnoreCase(v.trim()) || "1".equals(v.trim()))
				.orElse(false);
	}

	@PostConstruct
	public void sanitizeNegativeStock() {
		try {
			List<Product> all = repo.findAll();
			for (Product p : all) {
				boolean changed = false;
				if (p.getInInventory() == null) {
					p.setInInventory(true);
					changed = true;
				}
				if (changed) {
					repo.save(p);
				}
			}
		} catch (Exception ignored) {
		}
	}

	private Long resolveFirmId(Long explicitFirmId) {
		Long target = explicitFirmId != null ? explicitFirmId : com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
		return target;
	}

	@Transactional
	public Product create(Product product) {
		Long authoritativeFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
		if (authoritativeFirmId != null && product.getFirmId() != null && !authoritativeFirmId.equals(product.getFirmId())) {
			throw new com.billing.simple.billsoft.security.TenantSecurityException("Cross-firm product creation prohibited");
		}
		Long targetFirmId = authoritativeFirmId != null ? authoritativeFirmId : product.getFirmId();
		if (targetFirmId != null) {
			product.setFirmId(targetFirmId);
		}
		if (product.getInInventory() == null) {
			product.setInInventory(true);
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
		if (saved.getStockQuantity() != null && saved.getStockQuantity().compareTo(BigDecimal.ZERO) > 0
				&& !"SERVICE".equalsIgnoreCase(saved.getItemType())) {
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

	@Transactional(rollbackFor = Exception.class)
	public List<Product> createBulk(List<com.billing.simple.billsoft.dtos.ProductBulkRowDto> rows, Long clientFirmId) {
		Long authoritativeFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
		if (authoritativeFirmId != null && clientFirmId != null && !authoritativeFirmId.equals(clientFirmId)) {
			throw new com.billing.simple.billsoft.security.TenantSecurityException("Cross-firm bulk product creation prohibited");
		}
		Long targetFirmId = authoritativeFirmId != null ? authoritativeFirmId : clientFirmId;
		if (targetFirmId == null) {
			throw new com.billing.simple.billsoft.security.TenantSecurityException("Active firm context is required for bulk product creation");
		}

		if (rows == null || rows.isEmpty()) {
			throw new IllegalArgumentException("Bulk product batch cannot be empty");
		}

		// Track intra-batch duplicates
		Set<String> batchSkus = new HashSet<>();
		Set<String> batchBarcodes = new HashSet<>();
		List<Product> productsToSave = new ArrayList<>();
		List<BigDecimal> initialStocks = new ArrayList<>();

		for (int i = 0; i < rows.size(); i++) {
			com.billing.simple.billsoft.dtos.ProductBulkRowDto row = rows.get(i);
			int rowNum = i + 1;

			if (row.getName() == null || row.getName().trim().isEmpty()) {
				throw new IllegalArgumentException("Row " + rowNum + ": Product name is required");
			}
			String name = row.getName().trim();

			String sku = row.getSku() != null && !row.getSku().trim().isEmpty() ? row.getSku().trim() : null;
			if (sku != null) {
				String skuLower = sku.toLowerCase();
				if (batchSkus.contains(skuLower)) {
					throw new IllegalArgumentException("Row " + rowNum + ": Duplicate SKU in batch: '" + sku + "'");
				}
				batchSkus.add(skuLower);
				if (repo.existsByFirmIdAndSku(targetFirmId, sku)) {
					throw new IllegalArgumentException("Row " + rowNum + ": Product with SKU '" + sku + "' already exists for this firm");
				}
			}

			String barcode = row.getBarcode() != null && !row.getBarcode().trim().isEmpty() ? row.getBarcode().trim() : null;
			if (barcode != null) {
				String barcodeLower = barcode.toLowerCase();
				if (batchBarcodes.contains(barcodeLower)) {
					throw new IllegalArgumentException("Row " + rowNum + ": Duplicate Barcode in batch: '" + barcode + "'");
				}
				batchBarcodes.add(barcodeLower);
				if (repo.existsByFirmIdAndBarcode(targetFirmId, barcode)) {
					throw new IllegalArgumentException("Row " + rowNum + ": Product with Barcode '" + barcode + "' already exists for this firm");
				}
			}

			BigDecimal price = row.getPrice() != null ? row.getPrice() : BigDecimal.ZERO;
			if (price.compareTo(BigDecimal.ZERO) < 0) {
				throw new IllegalArgumentException("Row " + rowNum + ": Selling price cannot be negative");
			}

			BigDecimal costPrice = row.getCostPrice();
			if (costPrice != null && costPrice.compareTo(BigDecimal.ZERO) < 0) {
				throw new IllegalArgumentException("Row " + rowNum + ": Cost price cannot be negative");
			}

			BigDecimal gst = row.getGstPercentage() != null ? row.getGstPercentage() : BigDecimal.ZERO;
			if (gst.compareTo(BigDecimal.ZERO) < 0 || gst.compareTo(new BigDecimal("100.00")) > 0) {
				throw new IllegalArgumentException("Row " + rowNum + ": GST percentage must be between 0 and 100");
			}

			BigDecimal initStock = row.getInitialStock() != null ? row.getInitialStock() : BigDecimal.ZERO;
			if (initStock.compareTo(BigDecimal.ZERO) < 0) {
				throw new IllegalArgumentException("Row " + rowNum + ": Initial stock cannot be negative");
			}

			BigDecimal minStock = row.getMinStockLevel() != null ? row.getMinStockLevel() : new BigDecimal("5.000");
			if (minStock.compareTo(BigDecimal.ZERO) < 0) {
				throw new IllegalArgumentException("Row " + rowNum + ": Minimum stock level cannot be negative");
			}

			String itemType = row.getItemType() != null && !row.getItemType().trim().isEmpty()
					? row.getItemType().trim().toUpperCase() : "GOODS";
			String unit = row.getUnit() != null && !row.getUnit().trim().isEmpty()
					? row.getUnit().trim() : "pcs";
			String category = row.getCategory() != null && !row.getCategory().trim().isEmpty()
					? row.getCategory().trim() : null;

			Product p = Product.builder()
					.firmId(targetFirmId)
					.name(name)
					.sku(sku)
					.barcode(barcode)
					.category(category)
					.itemType(itemType)
					.unit(unit)
					.hsnCode(row.getHsnCode() != null ? row.getHsnCode().trim() : null)
					.price(price)
					.costPrice(costPrice)
					.gstPercentage(gst)
					.stockQuantity(initStock)
					.minStockLevel(minStock)
					.description(row.getDescription())
					.inInventory(row.getInInventory() != null ? row.getInInventory() : true)
					.preferredPartyId(row.getPreferredPartyId())
					.isArchived(false)
					.build();

			productsToSave.add(p);
			initialStocks.add(initStock);
		}

		List<Product> savedProducts = repo.saveAll(productsToSave);

		// Record initial stock movements for physical items with opening stock
		for (int i = 0; i < savedProducts.size(); i++) {
			Product saved = savedProducts.get(i);
			BigDecimal stockQty = initialStocks.get(i);
			if (stockQty != null && stockQty.compareTo(BigDecimal.ZERO) > 0
					&& !"SERVICE".equalsIgnoreCase(saved.getItemType())) {
				StockMovement movement = StockMovement.builder()
						.productId(saved.getId())
						.productName(saved.getName())
						.firmId(saved.getFirmId())
						.movementType("INITIAL_STOCK")
						.quantityChange(stockQty)
						.previousStock(BigDecimal.ZERO)
						.newStock(stockQty)
						.referenceType("MANUAL")
						.referenceId("OPENING_BALANCE")
						.note("Initial opening stock recorded on bulk creation")
						.createdAt(LocalDateTime.now())
						.build();
				stockMovementRepo.save(movement);
			}
		}

		return savedProducts;
	}

	public List<Product> getAll(Long firmId) {
		Long authoritativeFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
		Long targetFirmId = firmId != null ? firmId : authoritativeFirmId;
		if (targetFirmId == null) {
			return Collections.emptyList();
		}
		List<Product> list = repo.findByFirmId(targetFirmId);
		return list.stream()
				.filter(p -> !Boolean.FALSE.equals(p.getInInventory()))
				.filter(p -> p.getIsArchived() == null || !p.getIsArchived())
				.toList();
	}

	public List<Product> getAll(Long firmId, Boolean inInventoryOnly) {
		Long authoritativeFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
		Long targetFirmId = firmId != null ? firmId : authoritativeFirmId;
		if (targetFirmId == null) {
			return Collections.emptyList();
		}
		if (Boolean.FALSE.equals(inInventoryOnly)) {
			return repo.findByFirmIdAndInInventoryFalseAndIsArchivedFalseOrderByNameAsc(targetFirmId);
		}
		if (inInventoryOnly == null) {
			return repo.findByFirmId(targetFirmId);
		}
		return repo.findByFirmIdAndInInventoryTrueAndIsArchivedFalseOrderByNameAsc(targetFirmId);
	}

	public List<Product> getAllProducts(Long firmId) {
		Long authoritativeFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
		Long targetFirmId = firmId != null ? firmId : authoritativeFirmId;
		if (targetFirmId == null) {
			return Collections.emptyList();
		}
		return repo.findByFirmIdAndIsArchivedFalseOrderByNameAsc(targetFirmId);
	}

	public List<Product> getUnimportedProducts(Long firmId) {
		Long authoritativeFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
		if (authoritativeFirmId != null && firmId != null && !authoritativeFirmId.equals(firmId)) {
			throw new com.billing.simple.billsoft.security.TenantSecurityException("Cross-firm unimported product access prohibited");
		}
		return getAll(firmId, false);
	}

	public PageResponse<Product> getPaginatedProducts(Long firmId, String search, Pageable pageable) {
		Long authoritativeFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
		Long targetFirmId = firmId != null ? firmId : authoritativeFirmId;
		if (targetFirmId == null) {
			return PageResponse.empty(pageable.getPageNumber(), pageable.getPageSize());
		}
		Page<Product> page;
		if (search != null && !search.trim().isEmpty()) {
			page = repo.findByFirmIdAndInInventoryTrueAndIsArchivedFalseAndNameContainingIgnoreCase(targetFirmId, search.trim(), pageable);
		} else {
			page = repo.findByFirmIdAndInInventoryTrueAndIsArchivedFalse(targetFirmId, pageable);
		}
		return PageResponse.of(page);
	}

	@Transactional
	public Product importToInventory(Long id) {
		return importToInventory(id, BigDecimal.ZERO);
	}

	@Transactional
	public Product importToInventory(Long id, BigDecimal openingStock) {
		Long fid = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
		if (fid == null) {
			throw new com.billing.simple.billsoft.security.TenantSecurityException("Active firm context is required to import product to inventory");
		}
		Optional<Product> opt = repo.findByIdAndFirmIdForUpdate(id, fid);
		if (opt.isEmpty()) {
			throw new com.billing.simple.billsoft.security.TenantSecurityException("Product not found or unauthorized for firm: " + fid);
		}
		Product p = opt.get();
		applyInventoryOnboarding(p, openingStock, "Initial opening stock recorded on import to inventory");
		return repo.save(p);
	}

	/**
	 * Shared authoritative implementation for promoting a catalog product into inventory.
	 * Invariant:
	 * - inInventory: false -> true with openingStock > 0 creates INITIAL_STOCK movement and sets ledger balance.
	 * - inInventory: false -> true with openingStock == 0 creates no stock movement.
	 * - inInventory: true (already tracked) preserves existing inventory and never overwrites ledger balance.
	 */
	public StockMovement applyInventoryOnboarding(Product product, BigDecimal openingStock, String note) {
		if (!Boolean.TRUE.equals(product.getInInventory())) {
			product.setInInventory(true);
			if (openingStock != null && openingStock.compareTo(BigDecimal.ZERO) > 0
					&& !"SERVICE".equalsIgnoreCase(product.getItemType())) {
				product.setStockQuantity(openingStock);
				StockMovement movement = StockMovement.builder()
						.productId(product.getId())
						.productName(product.getName())
						.firmId(product.getFirmId())
						.movementType("INITIAL_STOCK")
						.quantityChange(openingStock)
						.previousStock(BigDecimal.ZERO)
						.newStock(openingStock)
						.referenceType("MANUAL")
						.referenceId("OPENING_BALANCE")
						.note(note != null ? note : "Initial opening stock recorded on inventory onboarding")
						.createdAt(LocalDateTime.now())
						.build();
				stockMovementRepo.save(movement);
				return movement;
			}
		}
		return null;
	}

	public Product getById(Long id) {
		Long fid = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
		if (fid == null) {
			throw new com.billing.simple.billsoft.security.TenantSecurityException("Active firm context is strictly required for this operation.");
		}
		return repo.findByIdAndFirmId(id, fid).orElse(null);
	}

	public Product getById(Long id, Long firmId) {
		Long authoritativeFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
		if (authoritativeFirmId != null && firmId != null && !authoritativeFirmId.equals(firmId)) {
			throw new com.billing.simple.billsoft.security.TenantSecurityException("Cross-firm access prohibited");
		}
		Long fid = authoritativeFirmId != null ? authoritativeFirmId : firmId;
		if (fid == null) {
			throw new com.billing.simple.billsoft.security.TenantSecurityException("Active firm context is strictly required for this operation.");
		}
		return repo.findByIdAndFirmId(id, fid).orElse(null);
	}

	@Transactional
	public Product update(Long id, Product updated) {
		Long fid = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
		if (fid == null) {
			throw new com.billing.simple.billsoft.security.TenantSecurityException("Active firm context is strictly required for this operation.");
		}
		Optional<Product> opt = repo.findByIdAndFirmId(id, fid);
		if (opt.isEmpty())
			return null;
		Product existing = opt.get();

		if (updated.getVersion() != null) {
			if (!updated.getVersion().equals(existing.getVersion())) {
				throw new org.springframework.orm.ObjectOptimisticLockingFailureException(Product.class, id);
			}
			existing.setVersion(updated.getVersion());
		}

		if (updated.getName() != null && !updated.getName().trim().isEmpty()) {
			existing.setName(updated.getName().trim());
		}

		if (updated.getPrice() != null) {
			if (updated.getPrice().compareTo(BigDecimal.ZERO) < 0) {
				throw new IllegalArgumentException("Selling price cannot be negative");
			}
			existing.setPrice(updated.getPrice());
		}

		if (updated.getCostPrice() != null) {
			if (updated.getCostPrice().compareTo(BigDecimal.ZERO) < 0) {
				throw new IllegalArgumentException("Cost price cannot be negative");
			}
			existing.setCostPrice(updated.getCostPrice());
		}

		if (updated.getGstPercentage() != null) {
			if (updated.getGstPercentage().compareTo(BigDecimal.ZERO) < 0
					|| updated.getGstPercentage().compareTo(new BigDecimal("100.00")) > 0) {
				throw new IllegalArgumentException("GST percentage must be between 0 and 100");
			}
			existing.setGstPercentage(updated.getGstPercentage());
		}

		if (updated.getMinStockLevel() != null) {
			if (updated.getMinStockLevel().compareTo(BigDecimal.ZERO) < 0) {
				throw new IllegalArgumentException("Minimum stock level cannot be negative");
			}
			existing.setMinStockLevel(updated.getMinStockLevel());
		}

		if (updated.getUnit() != null && !updated.getUnit().trim().isEmpty()) {
			existing.setUnit(updated.getUnit().trim());
		}

		if (updated.getHsnCode() != null) {
			existing.setHsnCode(updated.getHsnCode().trim().isEmpty() ? null : updated.getHsnCode().trim());
		}

		if (updated.getDescription() != null) {
			existing.setDescription(updated.getDescription());
		}

		if (updated.getCategory() != null) {
			existing.setCategory(updated.getCategory().trim().isEmpty() ? null : updated.getCategory().trim());
		}

		if (updated.getItemType() != null && !updated.getItemType().trim().isEmpty()) {
			existing.setItemType(updated.getItemType().trim().toUpperCase());
		}

		// SKU uniqueness validation
		String sku = updated.getSku() != null && !updated.getSku().trim().isEmpty() ? updated.getSku().trim() : null;
		if (sku != null) {
			Optional<Product> conflict = repo.findFirstByFirmIdAndSku(fid, sku);
			if (conflict.isPresent() && !conflict.get().getId().equals(id)) {
				throw new IllegalArgumentException("Product with SKU '" + sku + "' already exists for this firm");
			}
		}
		existing.setSku(sku);

		// Barcode uniqueness validation
		String barcode = updated.getBarcode() != null && !updated.getBarcode().trim().isEmpty() ? updated.getBarcode().trim() : null;
		if (barcode != null) {
			Optional<Product> conflict = repo.findFirstByFirmIdAndBarcode(fid, barcode);
			if (conflict.isPresent() && !conflict.get().getId().equals(id)) {
				throw new IllegalArgumentException("Product with Barcode '" + barcode + "' already exists for this firm");
			}
		}
		existing.setBarcode(barcode);

		// Preferred Vendor validation
		if (updated.getPreferredPartyId() != null && updated.getPreferredPartyId() > 0) {
			if (partyRepo != null && !partyRepo.existsByIdAndFirmId(updated.getPreferredPartyId(), fid)) {
				throw new IllegalArgumentException("Preferred vendor does not exist for this firm");
			}
			existing.setPreferredPartyId(updated.getPreferredPartyId());
		} else {
			existing.setPreferredPartyId(null);
		}

		// Inventory Onboarding (Shared authoritative logic)
		if (Boolean.TRUE.equals(updated.getInInventory())) {
			applyInventoryOnboarding(existing, updated.getStockQuantity(), "Initial opening stock recorded on product edit");
		}

		return repo.save(existing);
	}

	@Transactional(rollbackFor = Exception.class)
	public ProductBulkUpdateResponseDto updateBulk(List<ProductBulkUpdateItemDto> rows, Long clientFirmId) {
		Long authoritativeFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
		if (authoritativeFirmId != null && clientFirmId != null && !authoritativeFirmId.equals(clientFirmId)) {
			throw new com.billing.simple.billsoft.security.TenantSecurityException("Cross-firm bulk product update prohibited");
		}
		Long targetFirmId = authoritativeFirmId != null ? authoritativeFirmId : clientFirmId;
		if (targetFirmId == null) {
			throw new com.billing.simple.billsoft.security.TenantSecurityException("Active firm context is required for bulk product update");
		}

		if (rows == null || rows.isEmpty()) {
			throw new IllegalArgumentException("Bulk product update batch cannot be empty");
		}

		// Track intra-batch duplicates and validate row integrity
		Set<String> batchSkus = new HashSet<>();
		Set<String> batchBarcodes = new HashSet<>();
		Set<Long> productIds = new LinkedHashSet<>();

		for (int i = 0; i < rows.size(); i++) {
			ProductBulkUpdateItemDto row = rows.get(i);
			int rowNum = i + 1;

			if (row.getProductId() == null) {
				throw new IllegalArgumentException("Row " + rowNum + ": Product ID is required");
			}
			if (productIds.contains(row.getProductId())) {
				throw new IllegalArgumentException("Row " + rowNum + ": Duplicate Product ID in bulk update batch");
			}
			productIds.add(row.getProductId());

			if (row.getName() != null && row.getName().trim().isEmpty()) {
				throw new IllegalArgumentException("Row " + rowNum + ": Product name cannot be empty");
			}

			String sku = row.getSku() != null && !row.getSku().trim().isEmpty() ? row.getSku().trim() : null;
			if (sku != null) {
				String skuLower = sku.toLowerCase();
				if (batchSkus.contains(skuLower)) {
					throw new IllegalArgumentException("Row " + rowNum + ": Duplicate SKU in batch: '" + sku + "'");
				}
				batchSkus.add(skuLower);
			}

			String barcode = row.getBarcode() != null && !row.getBarcode().trim().isEmpty() ? row.getBarcode().trim() : null;
			if (barcode != null) {
				String barcodeLower = barcode.toLowerCase();
				if (batchBarcodes.contains(barcodeLower)) {
					throw new IllegalArgumentException("Row " + rowNum + ": Duplicate Barcode in batch: '" + barcode + "'");
				}
				batchBarcodes.add(barcodeLower);
			}

			if (row.getPrice() != null && row.getPrice().compareTo(BigDecimal.ZERO) < 0) {
				throw new IllegalArgumentException("Row " + rowNum + ": Selling price cannot be negative");
			}
			if (row.getCostPrice() != null && row.getCostPrice().compareTo(BigDecimal.ZERO) < 0) {
				throw new IllegalArgumentException("Row " + rowNum + ": Cost price cannot be negative");
			}
			if (row.getGstPercentage() != null && (row.getGstPercentage().compareTo(BigDecimal.ZERO) < 0
					|| row.getGstPercentage().compareTo(new BigDecimal("100.00")) > 0)) {
				throw new IllegalArgumentException("Row " + rowNum + ": GST percentage must be between 0 and 100");
			}
			if (row.getMinStockLevel() != null && row.getMinStockLevel().compareTo(BigDecimal.ZERO) < 0) {
				throw new IllegalArgumentException("Row " + rowNum + ": Minimum stock level cannot be negative");
			}
			if (row.getOpeningStock() != null && row.getOpeningStock().compareTo(BigDecimal.ZERO) < 0) {
				throw new IllegalArgumentException("Row " + rowNum + ": Opening stock cannot be negative");
			}
		}

		List<Product> products = repo.findByIdInAndFirmId(productIds, targetFirmId);
		if (products.size() != productIds.size()) {
			throw new IllegalArgumentException("One or more products not found or unauthorized for this firm");
		}

		Map<Long, Product> productMap = new HashMap<>();
		for (Product p : products) {
			productMap.put(p.getId(), p);
		}

		for (int i = 0; i < rows.size(); i++) {
			ProductBulkUpdateItemDto row = rows.get(i);
			int rowNum = i + 1;
			Product existing = productMap.get(row.getProductId());
			if (existing == null) {
				throw new IllegalArgumentException("Row " + rowNum + ": Product ID " + row.getProductId() + " not found");
			}

			// Validate database SKU collision against other products outside the batch
			String sku = row.getSku() != null && !row.getSku().trim().isEmpty() ? row.getSku().trim() : null;
			if (sku != null) {
				Optional<Product> conflict = repo.findFirstByFirmIdAndSku(targetFirmId, sku);
				if (conflict.isPresent() && !conflict.get().getId().equals(existing.getId())) {
					throw new IllegalArgumentException("Row " + rowNum + " ('" + existing.getName() + "'): SKU '" + sku + "' is already in use by another product");
				}
				existing.setSku(sku);
			} else if (row.getSku() != null) {
				existing.setSku(null);
			}

			// Validate database Barcode collision against other products outside the batch
			String barcode = row.getBarcode() != null && !row.getBarcode().trim().isEmpty() ? row.getBarcode().trim() : null;
			if (barcode != null) {
				Optional<Product> conflict = repo.findFirstByFirmIdAndBarcode(targetFirmId, barcode);
				if (conflict.isPresent() && !conflict.get().getId().equals(existing.getId())) {
					throw new IllegalArgumentException("Row " + rowNum + " ('" + existing.getName() + "'): Barcode '" + barcode + "' is already in use by another product");
				}
				existing.setBarcode(barcode);
			} else if (row.getBarcode() != null) {
				existing.setBarcode(null);
			}

			// Validate Vendor
			if (row.getPreferredPartyId() != null && row.getPreferredPartyId() > 0) {
				if (partyRepo != null && !partyRepo.existsByIdAndFirmId(row.getPreferredPartyId(), targetFirmId)) {
					throw new IllegalArgumentException("Row " + rowNum + " ('" + existing.getName() + "'): Preferred vendor does not exist for this firm");
				}
				existing.setPreferredPartyId(row.getPreferredPartyId());
			} else if (row.getPreferredPartyId() != null && row.getPreferredPartyId() <= 0) {
				existing.setPreferredPartyId(null);
			}

			// Apply master fields (PATCH semantics: only non-null updates)
			if (row.getName() != null && !row.getName().trim().isEmpty()) {
				existing.setName(row.getName().trim());
			}
			if (row.getCategory() != null) {
				existing.setCategory(row.getCategory().trim().isEmpty() ? null : row.getCategory().trim());
			}
			if (row.getItemType() != null && !row.getItemType().trim().isEmpty()) {
				existing.setItemType(row.getItemType().trim().toUpperCase());
			}
			if (row.getUnit() != null && !row.getUnit().trim().isEmpty()) {
				existing.setUnit(row.getUnit().trim());
			}
			if (row.getHsnCode() != null) {
				existing.setHsnCode(row.getHsnCode().trim().isEmpty() ? null : row.getHsnCode().trim());
			}
			if (row.getPrice() != null) {
				existing.setPrice(row.getPrice());
			}
			if (row.getCostPrice() != null) {
				existing.setCostPrice(row.getCostPrice());
			}
			if (row.getGstPercentage() != null) {
				existing.setGstPercentage(row.getGstPercentage());
			}
			if (row.getMinStockLevel() != null) {
				existing.setMinStockLevel(row.getMinStockLevel());
			}
			if (row.getDescription() != null) {
				existing.setDescription(row.getDescription());
			}

			// Inventory Onboarding (Shared authoritative logic)
			boolean shouldTrack = Boolean.TRUE.equals(row.getTrackInInventory()) || Boolean.TRUE.equals(row.getInInventory());
			if (shouldTrack) {
				applyInventoryOnboarding(existing, row.getOpeningStock(), "Initial opening stock recorded on bulk product edit");
			}
		}

		List<Product> saved = repo.saveAll(products);

		return ProductBulkUpdateResponseDto.builder()
				.updatedCount(saved.size())
				.products(saved)
				.message("Successfully updated " + saved.size() + " products")
				.build();
	}

	@Transactional
	public boolean delete(Long id) {
		Long fid = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
		if (fid != null) {
			Optional<Product> opt = repo.findByIdAndFirmId(id, fid);
			if (opt.isEmpty())
				return false;
			Product p = opt.get();
			boolean hasHistory = (invoiceItemRepo != null && invoiceItemRepo.existsByProduct_Id(id))
					|| (salesReturnItemRepo != null && salesReturnItemRepo.existsByProduct_Id(id));
			if (hasHistory) {
				p.setIsArchived(true);
				repo.save(p);
				return true;
			}
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
		if (fid == null) {
			throw new com.billing.simple.billsoft.security.TenantSecurityException("Active firm context is strictly required for this operation.");
		}
		Optional<Product> opt = repo.findByIdAndFirmIdForUpdate(id, fid);
		if (opt.isEmpty()) {
			throw new com.billing.simple.billsoft.security.TenantSecurityException(
					"Product not found or unauthorized for firm: " + fid);
		}

		Product product = opt.get();
		BigDecimal prevStock = product.getStockQuantity() != null ? product.getStockQuantity() : BigDecimal.ZERO;
		BigDecimal change = BigDecimal.ZERO;
		BigDecimal newStock = prevStock;

		if (quantity == null || quantity.compareTo(BigDecimal.ZERO) < 0) {
			quantity = BigDecimal.ZERO;
		}

		String movementType = "MANUAL_ADJUSTMENT";
		boolean allowNegative = isAllowNegativeStock(product.getFirmId());
		if ("ADD".equalsIgnoreCase(mode)) {
			change = quantity;
			newStock = prevStock.add(quantity);
		} else if ("SUBTRACT".equalsIgnoreCase(mode) || "DEDUCT".equalsIgnoreCase(mode)) {
			if (prevStock.compareTo(quantity) < 0 && !allowNegative) {
				throw new InsufficientStockException(
						String.format("Insufficient stock for product '%s' (ID: %d). Available: %s, Requested: %s",
								product.getName(), product.getId(),
								prevStock.stripTrailingZeros().toPlainString(),
								quantity.stripTrailingZeros().toPlainString()),
						product.getId(),
						product.getName(),
						prevStock,
						quantity
				);
			}
			newStock = prevStock.subtract(quantity);
			change = quantity.negate();
		} else { // "SET"
			if (quantity.compareTo(BigDecimal.ZERO) < 0 && !allowNegative) {
				throw new InsufficientStockException(
						String.format("Negative stock not allowed for product '%s' (ID: %d). Target stock: %s",
								product.getName(), product.getId(),
								quantity.stripTrailingZeros().toPlainString()),
						product.getId(),
						product.getName(),
						prevStock,
						quantity
				);
			}
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
	 * Preserves ledger invariant: previousStock + actualChange = newStock.
	 * In default strict mode (INVENTORY_ALLOW_NEGATIVE_STOCK = false), deductions exceeding
	 * available stock throw InsufficientStockException to trigger transaction rollback.
	 * In negative stock mode (INVENTORY_ALLOW_NEGATIVE_STOCK = true), full requested quantities
	 * are deducted and a negative stock balance is recorded with a warning audit trail.
	 */
	@Transactional
	public BigDecimal recordStockMovement(Long productId, Long firmId, String movementType, BigDecimal quantityChange,
			String referenceType, String referenceId, String note) {
		if (productId == null || quantityChange == null || quantityChange.compareTo(BigDecimal.ZERO) == 0) {
			return BigDecimal.ZERO;
		}

		Long targetFirmId = (firmId != null) ? firmId : com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
		if (targetFirmId == null) {
			throw new com.billing.simple.billsoft.security.TenantSecurityException("Active firm context is strictly required for this operation.");
		}
		Optional<Product> opt = repo.findByIdAndFirmIdForUpdate(productId, targetFirmId);
		if (opt.isEmpty()) {
			return BigDecimal.ZERO;
		}

		Product product = opt.get();
		if (entityManager != null && entityManager.contains(product)) {
			try {
				entityManager.refresh(product, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
			} catch (Exception ignored) {
			}
		}

		// Skip stock deduction for service items or products not tracked in inventory
		if ("SERVICE".equalsIgnoreCase(product.getItemType()) || Boolean.FALSE.equals(product.getInInventory())) {
			return BigDecimal.ZERO;
		}

		BigDecimal prevStock = product.getStockQuantity() != null ? product.getStockQuantity() : BigDecimal.ZERO;

		BigDecimal actualChange;
		BigDecimal newStock;
		String movementNote = note;

		if (quantityChange.compareTo(BigDecimal.ZERO) < 0) {
			BigDecimal requestedDeduction = quantityChange.abs();
			boolean allowNegative = isAllowNegativeStock(targetFirmId);

			if (prevStock.compareTo(requestedDeduction) < 0) {
				if (!allowNegative) {
					throw new InsufficientStockException(
							String.format("Insufficient stock for product '%s' (ID: %d). Available: %s, Requested: %s",
									product.getName(), product.getId(),
									prevStock.stripTrailingZeros().toPlainString(),
									requestedDeduction.stripTrailingZeros().toPlainString()),
							product.getId(),
							product.getName(),
							prevStock,
							requestedDeduction
					);
				}
				// Negative stock mode: deduct full requested quantity, resulting in negative stock balance
				actualChange = quantityChange;
				newStock = prevStock.subtract(requestedDeduction);
				BigDecimal shortage = requestedDeduction.subtract(prevStock);
				movementNote = (note != null ? note + " " : "")
						+ String.format("[Negative Stock Warning - Shortage: %s, Requested: %s, Resulting Stock: %s]",
								shortage.stripTrailingZeros().toPlainString(),
								requestedDeduction.stripTrailingZeros().toPlainString(),
								newStock.stripTrailingZeros().toPlainString());
			} else {
				actualChange = quantityChange;
				newStock = prevStock.subtract(requestedDeduction);
			}
		} else {
			// Addition / Restoration
			actualChange = quantityChange;
			newStock = prevStock.add(quantityChange);
		}

		product.setStockQuantity(newStock);
		repo.save(product);

		if (actualChange.compareTo(BigDecimal.ZERO) != 0) {
			StockMovement movement = StockMovement.builder()
					.productId(product.getId())
					.productName(product.getName())
					.firmId(targetFirmId)
					.movementType(movementType)
					.quantityChange(actualChange)
					.previousStock(prevStock)
					.newStock(newStock)
					.referenceType(referenceType)
					.referenceId(referenceId)
					.note(movementNote)
					.createdAt(LocalDateTime.now())
					.build();
			stockMovementRepo.save(movement);
		}

		return actualChange;
	}

	public List<StockMovement> getStockMovements(Long productId, Long firmId) {
		Long fid = resolveFirmId(firmId);
		if (productId != null) {
			return stockMovementRepo.findByProductIdAndFirmIdOrderByCreatedAtDesc(productId, fid);
		} else {
			return stockMovementRepo.findByFirmIdOrderByCreatedAtDesc(fid);
		}
	}

	public PageResponse<StockMovement> getPaginatedMovements(Long productId, Long firmId, Pageable pageable) {
		Long fid = resolveFirmId(firmId);
		Page<StockMovement> page;
		if (productId != null) {
			page = stockMovementRepo.findByProductIdAndFirmId(productId, fid, pageable);
		} else {
			page = stockMovementRepo.findByFirmId(fid, pageable);
		}
		return PageResponse.of(page);
	}

	public List<String> getCategories(Long firmId) {
		Long fid = resolveFirmId(firmId);
		return repo.findDistinctCategoriesByFirmId(fid);
	}

	public Map<String, Object> getInventorySummary(Long firmId) {
		Long fid = resolveFirmId(firmId);
		List<Product> list = repo.findByFirmId(fid);

		long totalProducts = list.stream().filter(p -> p.getIsArchived() == null || !p.getIsArchived()).count();
		long goodsCount = 0;
		long servicesCount = 0;
		long lowStockCount = 0;
		long outOfStockCount = 0;

		BigDecimal totalRetailValue = BigDecimal.ZERO;
		BigDecimal totalCostValue = BigDecimal.ZERO;

		BigDecimal totalMarginSum = BigDecimal.ZERO;
		int marginProductCount = 0;

		for (Product p : list) {
			if (Boolean.TRUE.equals(p.getIsArchived())) {
				continue;
			}
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
				BigDecimal margin = price.subtract(cost).divide(price, 4, RoundingMode.HALF_UP)
						.multiply(new BigDecimal("100"));
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
