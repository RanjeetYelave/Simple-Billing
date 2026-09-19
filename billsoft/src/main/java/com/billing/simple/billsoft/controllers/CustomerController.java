package com.billing.simple.billsoft.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.billing.simple.billsoft.dtos.CustomerRequest;
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.service.CustomerService;

@RestController
@RequestMapping("/api/customers")
@CrossOrigin
public class CustomerController {

	private final CustomerService service;
	private final com.billing.simple.billsoft.service.CustomerSettlementService settlementService;

	public CustomerController(CustomerService service, com.billing.simple.billsoft.service.CustomerSettlementService settlementService) {
		this.service = service;
		this.settlementService = settlementService;
	}

	@PostMapping
	public ResponseEntity<Customer> create(@RequestBody CustomerRequest request) {
		if (request.getName() == null || request.getName().trim().isEmpty()) {
			return ResponseEntity.badRequest().build();
		}
		Customer customer = new Customer();
		customer.setName(request.getName().trim());
		customer.setPhone(request.getPhone());
		customer.setEmail(request.getEmail());
		customer.setAddress(request.getAddress());
		customer.setGstin(request.getGstin());
		Long fid = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
		customer.setFirmId(fid != null ? fid : request.getFirmId());
		return ResponseEntity.ok(service.create(customer));

	}

	@GetMapping
	public ResponseEntity<List<Customer>> getAll(@RequestParam(required = false) Long firmId) {
		return ResponseEntity.ok(service.getAll(firmId));
	}

	@GetMapping("/{id}")
	public ResponseEntity<Customer> getById(@PathVariable Long id) {
		Customer c = service.getById(id);
		if (c == null)
			return ResponseEntity.notFound().build();
		return ResponseEntity.ok(c);
	}

	@PutMapping("/{id}")
	public ResponseEntity<Customer> update(@PathVariable Long id, @RequestBody CustomerRequest request) {
		Customer updated = service.update(id, request);
		if (updated == null)
			return ResponseEntity.notFound().build();
		return ResponseEntity.ok(updated);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		boolean removed = service.delete(id);
		if (!removed)
			return ResponseEntity.notFound().build();
		return ResponseEntity.noContent().build();
	}

	// ── Customer 360 & Operational Financial Endpoints ──

	@GetMapping("/{id}/360")
	public ResponseEntity<com.billing.simple.billsoft.dtos.Customer360Response> getCustomer360(@PathVariable Long id) {
		return ResponseEntity.ok(settlementService.getCustomer360(id));
	}

	@GetMapping("/{id}/outstanding-invoices")
	public ResponseEntity<List<com.billing.simple.billsoft.dtos.CustomerOutstandingInvoiceDto>> getOutstandingInvoices(@PathVariable Long id) {
		return ResponseEntity.ok(settlementService.getOutstandingInvoices(id));
	}

	@GetMapping("/{id}/payments")
	public ResponseEntity<List<com.billing.simple.billsoft.entities.InvoicePayment>> getPayments(@PathVariable Long id) {
		return ResponseEntity.ok(settlementService.getCustomerPayments(id));
	}

	@PostMapping("/{id}/settle")
	public ResponseEntity<com.billing.simple.billsoft.dtos.CustomerSettlementResponse> settleInvoices(
			@PathVariable Long id,
			@RequestBody com.billing.simple.billsoft.dtos.CustomerSettlementRequest request) {
		return ResponseEntity.ok(settlementService.settleInvoices(id, request));
	}

	@DeleteMapping("/payments/{paymentId}")
	public ResponseEntity<Void> deletePayment(@PathVariable Long paymentId) {
		boolean ok = settlementService.deletePayment(paymentId);
		return ok ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
	}
}
