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

    public InvoiceService(InvoiceRepository invoiceRepo, CustomerRepository customerRepo, ProductRepository productRepo) {
        this.invoiceRepo = invoiceRepo;
        this.customerRepo = customerRepo;
        this.productRepo = productRepo;
    }

    public String generateInvoiceNumber() {
        Invoice last = invoiceRepo.findTopByOrderByIdDesc();
        long next = (last == null) ? 1 : last.getId() + 1;
        return String.format("INV-%04d", next);
    }

    @Transactional
    public Invoice createInvoice(InvoiceRequest req) {

        Customer customer = customerRepo.findById(req.getCustomerId())
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        Invoice invoice = new Invoice();
        invoice.setCustomer(customer);
        invoice.setInvoiceNumber(generateInvoiceNumber());
        invoice.setNotes(req.getNotes());

        List<InvoiceItem> items = new ArrayList<>();
        double grandTotal = 0;

        for (InvoiceRequestItem it : req.getItems()) {

            Product product = productRepo.findById(it.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found: " + it.getProductId()));

            double price = product.getPrice();
            double gst = product.getGstPercentage() == null ? 0 : product.getGstPercentage();
            double total = (price * it.getQty()) + ((price * it.getQty()) * gst / 100);

            InvoiceItem item = InvoiceItem.builder()
                    .invoice(invoice)
                    .product(product)
                    .quantity(it.getQty())
                    .price(price)
                    .gstPercentage(gst)
                    .lineTotal(total)
                    .build();

            items.add(item);
            grandTotal += total;
        }

        invoice.setItems(items);
        invoice.setTotalAmount(grandTotal);

        return invoiceRepo.save(invoice);
    }

    public List<Invoice> getAll() {
        return invoiceRepo.findAll();
    }

    public Invoice getById(Long id) {
        return invoiceRepo.findById(id).orElse(null);
    }

    public boolean delete(Long id) {
        if (!invoiceRepo.existsById(id)) return false;
        invoiceRepo.deleteById(id);
        return true;
    }

    @Transactional
    public Invoice updateFullInvoice(Long id, InvoiceUpdateRequest req) {

        Invoice invoice = invoiceRepo.findById(id).orElse(null);
        if (invoice == null) return null;

        // Update customer
        if (req.getCustomerId() != null) {
            Customer c = customerRepo.findById(req.getCustomerId())
                    .orElse(invoice.getCustomer());
            invoice.setCustomer(c);
        }

        // Update invoice date
        if (req.getInvoiceDate() != null) {
            invoice.setInvoiceDate(LocalDateTime.parse(req.getInvoiceDate()));
        }

        // Update notes
        if (req.getNotes() != null) {
            invoice.setNotes(req.getNotes());
        }

        //------------------------------------------
        // HANDLE LINE ITEMS (add / remove / edit)
        //------------------------------------------
        List<InvoiceItem> existing = invoice.getItems();

        for (InvoiceUpdateRequest.ItemData data : req.getItems()) {

            // 1️⃣ REMOVE ITEM
            if (Boolean.TRUE.equals(data.getRemove())) {
                existing.removeIf(it -> it.getId().equals(data.getItemId()));
                continue;
            }

            // 2️⃣ UPDATE EXISTING ITEM
            if (data.getItemId() != null) {
                existing.forEach(it -> {
                    if (it.getId().equals(data.getItemId())) {

                        if (data.getProductId() != null) {
                            Product p = productRepo.findById(data.getProductId()).orElse(null);
                            it.setProduct(p);
                        }
                        if (data.getPrice() != null) it.setPrice(data.getPrice());
                        if (data.getGstPercentage() != null) it.setGstPercentage(data.getGstPercentage());
                        if (data.getQuantity() != null) it.setQuantity(data.getQuantity());

                        // Recalculate line total
                        double base = it.getPrice() * it.getQuantity();
                        double gstAmt = base * (it.getGstPercentage() / 100);
                        it.setLineTotal(base + gstAmt);
                    }
                });
            }

            // 3️⃣ ADD NEW ITEM
            else {
                Product p = productRepo.findById(data.getProductId()).orElse(null);
                if (p == null) continue;

                InvoiceItem newItem = new InvoiceItem();
                newItem.setInvoice(invoice);
                newItem.setProduct(p);
                newItem.setPrice(data.getPrice());
                newItem.setGstPercentage(data.getGstPercentage());
                newItem.setQuantity(data.getQuantity());

                double base = data.getPrice() * data.getQuantity();
                double gstAmt = base * (data.getGstPercentage() / 100);
                newItem.setLineTotal(base + gstAmt);

                existing.add(newItem);
            }
        }

        //------------------------------------------
        // RE-CALCULATE GRAND TOTAL
        //------------------------------------------
        double total = existing.stream()
                .mapToDouble(InvoiceItem::getLineTotal)
                .sum();

        invoice.setTotalAmount(total);

        return invoiceRepo.save(invoice);
    }

}
