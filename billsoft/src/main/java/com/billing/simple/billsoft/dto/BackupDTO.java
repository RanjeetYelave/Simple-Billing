package com.billing.simple.billsoft.dto;

import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.Product;

import java.util.List;
import java.util.Map;

public class BackupDTO {
    private Map<String, Object> metadata;
    private FirmDetails firmDetails;
    private List<Customer> customers;
    private List<Product> products;
    private List<Invoice> invoices;

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public FirmDetails getFirmDetails() {
        return firmDetails;
    }

    public void setFirmDetails(FirmDetails firmDetails) {
        this.firmDetails = firmDetails;
    }

    public List<Customer> getCustomers() {
        return customers;
    }

    public void setCustomers(List<Customer> customers) {
        this.customers = customers;
    }

    public List<Product> getProducts() {
        return products;
    }

    public void setProducts(List<Product> products) {
        this.products = products;
    }

    public List<Invoice> getInvoices() {
        return invoices;
    }

    public void setInvoices(List<Invoice> invoices) {
        this.invoices = invoices;
    }
}
