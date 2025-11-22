package com.billing.simple.billsoft.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billing.simple.billsoft.dtos.CustomerAnalyticsResponse;
import com.billing.simple.billsoft.dtos.CustomerInvoiceSummary;
import com.billing.simple.billsoft.dtos.InvoiceRequest;
import com.billing.simple.billsoft.dtos.InvoiceRequest.Discount;
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

    public InvoiceService(InvoiceRepository invoiceRepo,
                          CustomerRepository customerRepo,
                          ProductRepository productRepo) {
        this.invoiceRepo = invoiceRepo;
        this.customerRepo = customerRepo;
        this.productRepo = productRepo;
    }

    // ---------------------------------------------------------
    // INVOICE NUMBER
    // ---------------------------------------------------------
    public String generateInvoiceNumber() {
        Invoice last = invoiceRepo.findTopByOrderByIdDesc();
        long next = (last == null) ? 1 : last.getId() + 1;
        return String.format("INV-%04d", next);
    }

    // ---------------------------------------------------------
    // HELPERS
    // ---------------------------------------------------------
    private LocalDateTime parseDateLenient(String s) {
        if (s == null) return null;
        try {
            return LocalDateTime.parse(s);
        } catch (DateTimeParseException ex) {
            // try with seconds if missing
            try {
                return LocalDateTime.parse(s + ":00");
            } catch (Exception e) {
                return null;
            }
        }
    }

    /**
     * Build & calculate a single item from request.
     * Uses product price / gst if missing from request.
     */
    private InvoiceItem buildItemFromRequest(InvoiceRequestItem reqItem) {
        if (reqItem == null) return null;

        // resolve product
        Product product = null;
        if (reqItem.getProductId() != null) {
            product = productRepo.findById(reqItem.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found: " + reqItem.getProductId()));
        }

        InvoiceItem item = new InvoiceItem();
        item.setProduct(product);

        // qty
        int qty = reqItem.getQty() != null ? reqItem.getQty() : 0;
        item.setQty(qty);

        // unit
        String unit = reqItem.getUnit() != null
                ? reqItem.getUnit()
                : (product != null ? product.getUnit() : null);
        item.setUnit(unit);

        // price
        double price = 0.0;
        if (reqItem.getPricePerUnit() != null) {
            price = reqItem.getPricePerUnit();
        } else if (product != null && product.getPrice() != null) {
            price = product.getPrice();
        }
        item.setPricePerUnit(price);

        // amount without tax
        double amountNoTax = qty * price;
        item.setAmountWithoutTax(amountNoTax);

        // discount fields
        String discountType = reqItem.getDiscountType();
        Double discountValue = reqItem.getDiscountValue();
        Double discountPercent = reqItem.getDiscountPercent();

        // infer discountType if not given
        if (discountType != null) {
            item.setDiscountType(discountType);
        } else if (discountPercent != null && discountPercent > 0) {
            item.setDiscountType("PERCENT");
        } else if (discountValue != null && discountValue > 0) {
            item.setDiscountType("VALUE");
        } else {
            item.setDiscountType(null);
        }

        item.setDiscountValue(discountValue);
        item.setDiscountPercent(discountPercent);

        // calculate discount amount
        double discountAmt = 0.0;
        if (discountPercent != null && discountPercent > 0) {
            discountAmt = amountNoTax * (discountPercent / 100.0);
        } else if (discountValue != null && discountValue > 0) {
            discountAmt = discountValue;
        }

        // taxable
        double taxable = Math.max(0.0, amountNoTax - discountAmt);
        item.setTaxableAmount(taxable);

        // gst%
        double gstPercent;
        if (reqItem.getGstPercent() != null) {
            gstPercent = reqItem.getGstPercent();
        } else if (product != null && product.getGstPercentage() != null) {
            gstPercent = product.getGstPercentage();
        } else {
            gstPercent = 0.0;
        }
        item.setGstPercent(gstPercent);

        // gst amount
        double gstAmount = taxable * (gstPercent / 100.0);
        item.setGstAmount(gstAmount);

        // final line total
        double lineTotal = taxable + gstAmount;
        item.setLineTotal(lineTotal);

        return item;
    }

    // ---------------------------------------------------------
    // CREATE (SERVER CALCULATES)
    // ---------------------------------------------------------
    @Transactional
    public Invoice createInvoice(InvoiceRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }

        Customer customer = customerRepo.findById(request.getCustomerId())
                .orElseThrow(() -> new RuntimeException("Customer not found: " + request.getCustomerId()));

        Invoice invoice = new Invoice();
        invoice.setCustomer(customer);
        invoice.setNotes(request.getNotes());
        invoice.setInvoiceNumber(generateInvoiceNumber());
        invoice.setInvoiceDate(LocalDateTime.now());
        invoice.setPaid(Boolean.TRUE.equals(request.getPaid())); // default false if null

        List<InvoiceItem> itemEntities = new ArrayList<>();
        double subtotalWithoutTax = 0.0;
        double totalTax = 0.0;
        double totalItemDiscounts = 0.0;

        if (request.getItems() != null) {
            for (InvoiceRequestItem ri : request.getItems()) {
                InvoiceItem item = buildItemFromRequest(ri);
                item.setInvoice(invoice);
                itemEntities.add(item);

                double amountNoTax = item.getAmountWithoutTax() != null ? item.getAmountWithoutTax() : 0.0;
                double gstAmt = item.getGstAmount() != null ? item.getGstAmount() : 0.0;

                subtotalWithoutTax += amountNoTax;
                totalTax += gstAmt;

                double itemDiscount;
                if (item.getDiscountPercent() != null && item.getDiscountPercent() > 0) {
                    itemDiscount = amountNoTax * (item.getDiscountPercent() / 100.0);
                } else {
                    itemDiscount = item.getDiscountValue() != null ? item.getDiscountValue() : 0.0;
                }
                totalItemDiscounts += itemDiscount;
            }
        }

        // invoice-level discount
        double invoiceDiscountAmount = 0.0;
        Discount invDisc = request.getInvoiceDiscount();
        if (invDisc != null && invDisc.getValue() != null && invDisc.getValue() > 0) {
            String type = invDisc.getType();
            double value = invDisc.getValue();
            double baseForDisc = subtotalWithoutTax - totalItemDiscounts;

            if ("PERCENT".equalsIgnoreCase(type)) {
                invoiceDiscountAmount = baseForDisc * (value / 100.0);
            } else {
                invoiceDiscountAmount = value;
            }
        }

        double grandTotal = (subtotalWithoutTax - totalItemDiscounts - invoiceDiscountAmount) + totalTax;

        // attach items (bidirectional)
        invoice.getItems().clear();
        for (InvoiceItem it : itemEntities) {
            it.setInvoice(invoice);
            invoice.getItems().add(it);
        }

        // store only final total in Invoice
        invoice.setTotalAmount(grandTotal);

        return invoiceRepo.save(invoice);
    }

    // ---------------------------------------------------------
    // READ / DELETE
    // ---------------------------------------------------------
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

    // ---------------------------------------------------------
    // UPDATE FULL INVOICE (replace items)
    // ---------------------------------------------------------
    @Transactional
    public Invoice updateFullInvoice(Long id, InvoiceUpdateRequest req) {
        Invoice invoice = invoiceRepo.findById(id).orElse(null);
        if (invoice == null) return null;

        if (req.getCustomerId() != null) {
            customerRepo.findById(req.getCustomerId())
                    .ifPresent(invoice::setCustomer);
        }

        if (req.getInvoiceDate() != null) {
            LocalDateTime dt = parseDateLenient(req.getInvoiceDate());
            if (dt != null) invoice.setInvoiceDate(dt);
        }

        if (req.getNotes() != null) {
            invoice.setNotes(req.getNotes());
        }

        if (req.getPaid() != null) {
            invoice.setPaid(req.getPaid());
        }

        List<InvoiceItem> newItems = new ArrayList<>();
        double subtotalWithoutTax = 0.0;
        double totalTax = 0.0;
        double totalItemDiscounts = 0.0;

        if (req.getItems() != null) {
            for (InvoiceRequestItem ri : req.getItems()) {
                InvoiceItem item = buildItemFromRequest(ri);
                item.setInvoice(invoice);
                newItems.add(item);

                double amountNoTax = item.getAmountWithoutTax() != null ? item.getAmountWithoutTax() : 0.0;
                double gstAmt = item.getGstAmount() != null ? item.getGstAmount() : 0.0;

                subtotalWithoutTax += amountNoTax;
                totalTax += gstAmt;

                double itemDiscount;
                if (item.getDiscountPercent() != null && item.getDiscountPercent() > 0) {
                    itemDiscount = amountNoTax * (item.getDiscountPercent() / 100.0);
                } else {
                    itemDiscount = item.getDiscountValue() != null ? item.getDiscountValue() : 0.0;
                }
                totalItemDiscounts += itemDiscount;
            }
        }

        // invoice-level discount
        double invoiceDiscountAmount = 0.0;
        Discount invDisc = req.getInvoiceDiscount();
        if (invDisc != null && invDisc.getValue() != null && invDisc.getValue() > 0) {
            String type = invDisc.getType();
            double value = invDisc.getValue();
            double baseForDisc = subtotalWithoutTax - totalItemDiscounts;

            if ("PERCENT".equalsIgnoreCase(type)) {
                invoiceDiscountAmount = baseForDisc * (value / 100.0);
            } else {
                invoiceDiscountAmount = value;
            }
        }

        double grandTotal = (subtotalWithoutTax - totalItemDiscounts - invoiceDiscountAmount) + totalTax;

        // replace items
        invoice.getItems().clear();
        for (InvoiceItem it : newItems) {
            it.setInvoice(invoice);
            invoice.getItems().add(it);
        }

        invoice.setTotalAmount(grandTotal);

        return invoiceRepo.save(invoice);
    }

    // ---------------------------------------------------------
    // UPDATE ONLY PAID FLAG
    // ---------------------------------------------------------
    @Transactional
    public Invoice updatePaidFlag(Long id, boolean paid) {
        Invoice invoice = invoiceRepo.findById(id).orElse(null);
        if (invoice == null) return null;
        invoice.setPaid(paid);
        return invoiceRepo.save(invoice);
    }

    // ---------------------------------------------------------
    // ANALYTICS
    // ---------------------------------------------------------
    public CustomerAnalyticsResponse getCustomerAnalytics(Long customerId) {
        List<Invoice> invoices = invoiceRepo.findByCustomer_Id(customerId);

        CustomerAnalyticsResponse resp = new CustomerAnalyticsResponse();
        resp.setCustomerId(customerId);

        if (invoices == null || invoices.isEmpty()) {
            resp.setCustomerName(null);
            resp.setTotalBusiness(0.0);
            resp.setTotalPaid(0.0);
            resp.setTotalPending(0.0);
            resp.setInvoiceCount(0L);
            resp.setInvoices(Collections.emptyList());
            return resp;
        }

        Customer customer = invoices.get(0).getCustomer();
        resp.setCustomerName(customer != null ? customer.getName() : null);

        double totalBusiness = invoices.stream()
                .map(Invoice::getTotalAmount)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();

        double totalPaid = invoices.stream()
                .filter(inv -> Boolean.TRUE.equals(inv.getPaid()))
                .map(Invoice::getTotalAmount)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();

        double totalPending = totalBusiness - totalPaid;

        List<CustomerInvoiceSummary> summaries = invoices.stream()
                .map(inv -> {
                    CustomerInvoiceSummary s = new CustomerInvoiceSummary();
                    s.setInvoiceId(inv.getId());
                    s.setInvoiceNumber(inv.getInvoiceNumber());
                    s.setInvoiceDate(inv.getInvoiceDate() != null ? inv.getInvoiceDate().toString() : null);
                    s.setTotalAmount(inv.getTotalAmount());
                    s.setPaid(inv.getPaid());
                    return s;
                })
                .collect(Collectors.toList());

        resp.setTotalBusiness(totalBusiness);
        resp.setTotalPaid(totalPaid);
        resp.setTotalPending(totalPending);
        resp.setInvoiceCount((long) invoices.size());
        resp.setInvoices(summaries);

        return resp;
    }

    public List<CustomerAnalyticsResponse> getCustomerAnalyticsByName(String namePart) {
        if (namePart == null || namePart.isBlank()) {
            return Collections.emptyList();
        }

        List<Invoice> invoices = invoiceRepo.findByCustomer_NameContainingIgnoreCase(namePart);
        if (invoices == null || invoices.isEmpty()) {
            return Collections.emptyList();
        }

        // get distinct customer ids
        Set<Long> customerIds = invoices.stream()
                .map(Invoice::getCustomer)
                .filter(Objects::nonNull)
                .map(Customer::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<CustomerAnalyticsResponse> result = new ArrayList<>();
        for (Long cid : customerIds) {
            result.add(getCustomerAnalytics(cid));
        }
        return result;
    }
}
