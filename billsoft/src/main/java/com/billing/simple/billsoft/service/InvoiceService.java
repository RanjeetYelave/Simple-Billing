package com.billing.simple.billsoft.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billing.simple.billsoft.dtos.InvoiceRequest;
import com.billing.simple.billsoft.dtos.InvoiceRequestItem;
import com.billing.simple.billsoft.dtos.InvoiceUpdateRequest;
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.InvoiceItem;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repo.InvoiceRepository;
import com.billing.simple.billsoft.repo.ProductRepository;

@Service
public class InvoiceService {

	private final InvoiceRepository invoiceRepo;
	private final CustomerRepository customerRepo;
	private final ProductRepository productRepo;

	public InvoiceService(InvoiceRepository invoiceRepo, CustomerRepository customerRepo,
			ProductRepository productRepo) {

		this.invoiceRepo = invoiceRepo;
		this.customerRepo = customerRepo;
		this.productRepo = productRepo;
	}

	// ---------------------------------------------------------
	// GENERATE INVOICE NUMBER
	// ---------------------------------------------------------
	public String generateInvoiceNumber() {
		Invoice last = invoiceRepo.findTopByOrderByIdDesc();
		long next = (last == null) ? 1 : last.getId() + 1;
		return String.format("INV-%04d", next);
	}

	// ---------------------------------------------------------
	// CREATE INVOICE (UI CALCULATES EVERYTHING)
	// ---------------------------------------------------------
	@Transactional
	public Invoice createInvoice(InvoiceRequest request) {

		Invoice invoice = new Invoice();

		// CUSTOMER
		Customer customer = customerRepo.findById(request.getCustomerId())
				.orElseThrow(() -> new RuntimeException("Customer not found: " + request.getCustomerId()));

		invoice.setCustomer(customer);
		invoice.setInvoiceDate(LocalDateTime.now());
		invoice.setNotes(request.getNotes());
		invoice.setInvoiceNumber(generateInvoiceNumber());
		invoice = invoiceRepo.save(invoice);

		// ITEMS
		List<InvoiceItem> itemEntities = new ArrayList<>();

		for (InvoiceRequestItem i : request.getItems()) {

			Product product = productRepo.findById(i.getProductId())
					.orElseThrow(() -> new RuntimeException("Product not found: " + i.getProductId()));

			InvoiceItem item = new InvoiceItem();
			item.setInvoice(invoice);
			item.setProduct(product);

			// ---------- UI-SENT FIELDS ----------
			item.setQty(i.getQty());
			item.setUnit(i.getUnit());

			item.setPricePerUnit(i.getPricePerUnit());
			item.setAmountWithoutTax(i.getAmountWithoutTax());

			item.setDiscountType(i.getDiscountType());
			item.setDiscountValue(i.getDiscountValue());
			item.setDiscountPercent(i.getDiscountPercent());

			item.setTaxableAmount(i.getTaxableAmount());

			item.setGstPercent(i.getGstPercent());
			item.setGstAmount(i.getGstAmount());

			item.setLineTotal(i.getLineTotal());

			itemEntities.add(item);
		}

		invoice.setItems(itemEntities);

		// GRAND TOTAL
		double invoiceTotal = itemEntities.stream().mapToDouble(InvoiceItem::getLineTotal).sum();

		invoice.setTotalAmount(invoiceTotal);

		return invoiceRepo.save(invoice);
	}

	// ---------------------------------------------------------
	// FETCH ALL / FETCH BY ID
	// ---------------------------------------------------------
	public List<Invoice> getAll() {
		return invoiceRepo.findAll();
	}

	public Invoice getById(Long id) {
		return invoiceRepo.findById(id).orElse(null);
	}

	// ---------------------------------------------------------
	// DELETE INVOICE
	// ---------------------------------------------------------
	public boolean delete(Long id) {
		if (!invoiceRepo.existsById(id))
			return false;
		invoiceRepo.deleteById(id);
		return true;
	}

	// ---------------------------------------------------------
	// UPDATE FULL INVOICE (UI CALCULATED FIELDS)
	// ---------------------------------------------------------
	@Transactional
	public Invoice updateFullInvoice(Long id, InvoiceUpdateRequest req) {

		Invoice invoice = invoiceRepo.findById(id).orElse(null);
		if (invoice == null)
			return null;

		// UPDATE CUSTOMER
		if (req.getCustomerId() != null) {
			Customer c = customerRepo.findById(req.getCustomerId()).orElse(invoice.getCustomer());
			invoice.setCustomer(c);
		}

		// UPDATE DATE
		if (req.getInvoiceDate() != null) {
			invoice.setInvoiceDate(LocalDateTime.parse(req.getInvoiceDate()));
		}

		// UPDATE NOTES
		if (req.getNotes() != null) {
			invoice.setNotes(req.getNotes());
		}

		// -----------------------------------------------------------------
		// UPDATE ITEMS (NO SERVER CALCULATIONS, UI SENDS ALL VALUES)
		// -----------------------------------------------------------------
		List<InvoiceItem> existing = invoice.getItems();

		for (InvoiceUpdateRequest.ItemData data : req.getItems()) {

			// REMOVE ITEM
			if (Boolean.TRUE.equals(data.getRemove())) {
				existing.removeIf(it -> it.getId().equals(data.getItemId()));
				continue;
			}

			// UPDATE EXISTING ITEM
			if (data.getItemId() != null) {
				for (InvoiceItem it : existing) {
					if (it.getId().equals(data.getItemId())) {

						if (data.getProductId() != null) {
							Product p = productRepo.findById(data.getProductId()).orElse(null);
							it.setProduct(p);
						}

						it.setQty(data.getQty());
						it.setUnit(data.getUnit());

						it.setPricePerUnit(data.getPricePerUnit());
						it.setAmountWithoutTax(data.getAmountWithoutTax());

						it.setDiscountType(data.getDiscountType());
						it.setDiscountValue(data.getDiscountValue());
						it.setDiscountPercent(data.getDiscountPercent());

						it.setTaxableAmount(data.getTaxableAmount());

						it.setGstPercent(data.getGstPercent());
						it.setGstAmount(data.getGstAmount());

						it.setLineTotal(data.getLineTotal());
					}
				}
			}

			// ADD NEW ITEM
			else {
				Product p = productRepo.findById(data.getProductId()).orElse(null);
				if (p == null)
					continue;

				InvoiceItem newItem = new InvoiceItem();
				newItem.setInvoice(invoice);
				newItem.setProduct(p);

				newItem.setQty(data.getQty());
				newItem.setUnit(data.getUnit());

				newItem.setPricePerUnit(data.getPricePerUnit());
				newItem.setAmountWithoutTax(data.getAmountWithoutTax());

				newItem.setDiscountType(data.getDiscountType());
				newItem.setDiscountValue(data.getDiscountValue());
				newItem.setDiscountPercent(data.getDiscountPercent());

				newItem.setTaxableAmount(data.getTaxableAmount());

				newItem.setGstPercent(data.getGstPercent());
				newItem.setGstAmount(data.getGstAmount());

				newItem.setLineTotal(data.getLineTotal());

				existing.add(newItem);
			}
		}

		// RECALCULATE GRAND TOTAL
		double total = existing.stream().mapToDouble(InvoiceItem::getLineTotal).sum();

		invoice.setTotalAmount(total);

		return invoiceRepo.save(invoice);
	}
}
