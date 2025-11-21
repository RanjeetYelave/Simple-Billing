package com.billing.simple.billsoft.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repo.InvoiceRepository;

@Service
public class InvoiceService {

	private final InvoiceRepository invoiceRepo;
	private final CustomerRepository customerRepo;

	public InvoiceService(InvoiceRepository invoiceRepo, CustomerRepository customerRepo) {
		this.invoiceRepo = invoiceRepo;
		this.customerRepo = customerRepo;
	}

	@Transactional
	public Invoice create(Invoice invoice) {
		// optionally validate customer exists
		if (invoice.getCustomer() != null && invoice.getCustomer().getId() != null) {
			Long custId = invoice.getCustomer().getId();
			Optional.ofNullable(customerRepo.findById(custId)).orElseThrow();
		}
		return invoiceRepo.save(invoice);
	}

	public List<Invoice> getAll() {
		return invoiceRepo.findAll();
	}

	public Invoice getById(Long id) {
		return invoiceRepo.findById(id).orElse(null);
	}

	@Transactional
	public boolean delete(Long id) {
		if (!invoiceRepo.existsById(id))
			return false;
		invoiceRepo.deleteById(id);
		return true;
	}

	// generate invoice number like INV-0001
	public String generateInvoiceNumber() {
		Invoice last = invoiceRepo.findTopByOrderByIdDesc();
		long nextId = (last == null) ? 1 : last.getId() + 1;
		return String.format("INV-%04d", nextId);
	}
}
