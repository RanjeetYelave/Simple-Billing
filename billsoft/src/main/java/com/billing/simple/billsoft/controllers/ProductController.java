package com.billing.simple.billsoft.controllers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.entities.StockMovement;
import com.billing.simple.billsoft.service.ProductService;

@RestController
@RequestMapping("/api/products")
@CrossOrigin
public class ProductController {

	private final ProductService service;

	public ProductController(ProductService service) {
		this.service = service;
	}

	@PostMapping
	public ResponseEntity<Product> create(@RequestBody Product product) {
		return ResponseEntity.ok(service.create(product));
	}

	@PostMapping("/bulk")
	public ResponseEntity<List<Product>> createBulk(
			@RequestBody List<com.billing.simple.billsoft.dtos.ProductBulkRowDto> rows,
			@RequestParam(required = false) Long firmId) {
		return ResponseEntity.ok(service.createBulk(rows, firmId));
	}

	@PutMapping("/bulk")
	public ResponseEntity<com.billing.simple.billsoft.dtos.ProductBulkUpdateResponseDto> updateBulk(
			@RequestBody com.fasterxml.jackson.databind.JsonNode body,
			@RequestParam(required = false) Long firmId) {
		com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
		mapper.findAndRegisterModules();
		java.util.List<com.billing.simple.billsoft.dtos.ProductBulkUpdateItemDto> rows;
		if (body.isArray()) {
			rows = mapper.convertValue(body, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<com.billing.simple.billsoft.dtos.ProductBulkUpdateItemDto>>() {});
		} else if (body.has("updates") && body.get("updates").isArray()) {
			rows = mapper.convertValue(body.get("updates"), new com.fasterxml.jackson.core.type.TypeReference<java.util.List<com.billing.simple.billsoft.dtos.ProductBulkUpdateItemDto>>() {});
		} else {
			throw new IllegalArgumentException("Invalid bulk update payload: expected array or object with 'updates' array");
		}
		return ResponseEntity.ok(service.updateBulk(rows, firmId));
	}

	@GetMapping
	public ResponseEntity<?> getAll(
			@RequestParam(required = false) Long firmId,
			@RequestParam(required = false) Boolean inInventory,
			@RequestParam(required = false) Boolean all,
			@RequestParam(required = false) String search,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		if (page != null || size != null) {
			org.springframework.data.domain.Pageable pageable = com.billing.simple.billsoft.util.PaginationUtils.createDefaultTransactionPageRequest(
					page != null ? page : 0, size != null ? size : 25, "name");
			return ResponseEntity.ok(service.getPaginatedProducts(firmId, search, pageable));
		}
		if (Boolean.TRUE.equals(all)) {
			return ResponseEntity.ok(service.getAllProducts(firmId));
		}
		if (Boolean.FALSE.equals(inInventory)) {
			return ResponseEntity.ok(service.getUnimportedProducts(firmId));
		}
		return ResponseEntity.ok(service.getAll(firmId, inInventory != null ? inInventory : true));
	}

	@GetMapping("/unimported")
	public ResponseEntity<List<Product>> getUnimported(@RequestParam(required = false) Long firmId) {
		return ResponseEntity.ok(service.getUnimportedProducts(firmId));
	}

	@PostMapping("/{id}/import-to-inventory")
	public ResponseEntity<Product> importToInventory(@PathVariable Long id) {
		Product imported = service.importToInventory(id);
		if (imported == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(imported);
	}

	@GetMapping("/summary")
	public ResponseEntity<Map<String, Object>> getSummary(@RequestParam(required = false) Long firmId) {
		return ResponseEntity.ok(service.getInventorySummary(firmId));
	}

	@GetMapping("/categories")
	public ResponseEntity<List<String>> getCategories(@RequestParam(required = false) Long firmId) {
		return ResponseEntity.ok(service.getCategories(firmId));
	}

	@GetMapping("/movements")
	public ResponseEntity<?> getAllMovements(
			@RequestParam(required = false) Long firmId,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		if (page != null || size != null) {
			org.springframework.data.domain.Pageable pageable = com.billing.simple.billsoft.util.PaginationUtils.createDefaultTransactionPageRequest(
					page != null ? page : 0, size != null ? size : 25, "createdAt");
			return ResponseEntity.ok(service.getPaginatedMovements(null, firmId, pageable));
		}
		return ResponseEntity.ok(service.getStockMovements(null, firmId));
	}

	@GetMapping("/{id}")
	public ResponseEntity<Product> getById(@PathVariable Long id) {
		Product p = service.getById(id);
		if (p == null)
			return ResponseEntity.notFound().build();
		return ResponseEntity.ok(p);
	}

	@PutMapping("/{id}")
	public ResponseEntity<Product> update(@PathVariable Long id, @RequestBody Product product) {
		Product updated = service.update(id, product);
		if (updated == null)
			return ResponseEntity.notFound().build();
		return ResponseEntity.ok(updated);
	}

	@PostMapping("/{id}/adjust-stock")
	public ResponseEntity<Product> adjustStock(@PathVariable Long id, @RequestBody Map<String, Object> req) {
		BigDecimal qty = BigDecimal.ZERO;
		if (req.get("quantity") != null) {
			try {
				qty = new BigDecimal(req.get("quantity").toString());
			} catch (Exception ignored) { }
		}
		String mode = req.get("mode") != null ? req.get("mode").toString() : "SET";
		String note = req.get("note") != null ? req.get("note").toString() : "";

		Product updated = service.adjustStock(id, qty, mode, note);
		if (updated == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(updated);
	}

	@GetMapping("/{id}/movements")
	public ResponseEntity<?> getProductMovements(
			@PathVariable Long id,
			@RequestParam(required = false) Long firmId,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		if (page != null || size != null) {
			org.springframework.data.domain.Pageable pageable = com.billing.simple.billsoft.util.PaginationUtils.createDefaultTransactionPageRequest(
					page != null ? page : 0, size != null ? size : 25, "createdAt");
			return ResponseEntity.ok(service.getPaginatedMovements(id, firmId, pageable));
		}
		return ResponseEntity.ok(service.getStockMovements(id, firmId));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		boolean removed = service.delete(id);
		if (!removed)
			return ResponseEntity.notFound().build();
		return ResponseEntity.noContent().build();
	}
}
