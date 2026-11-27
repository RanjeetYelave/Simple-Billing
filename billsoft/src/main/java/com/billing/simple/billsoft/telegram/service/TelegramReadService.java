package com.billing.simple.billsoft.telegram.service;

import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.entities.PurchaseOrder;
import com.billing.simple.billsoft.dtos.FirmAnalyticsResponse;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.service.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TelegramReadService {

    private final InvoiceService invoiceService;
    private final CustomerService customerService;
    private final ProductService productService;
    private final PurchaseOrderService poService;
    private final DebtorService debtorService;
    private final FirmDetailsService firmService;

    public TelegramReadService(InvoiceService invoiceService,
                               CustomerService customerService,
                               ProductService productService,
                               PurchaseOrderService poService,
                               DebtorService debtorService,
                               FirmDetailsService firmService) {
        this.invoiceService = invoiceService;
        this.customerService = customerService;
        this.productService = productService;
        this.poService = poService;
        this.debtorService = debtorService;
        this.firmService = firmService;
    }

    public List<FirmDetails> getActiveFirms() {
        return firmService.list();
    }

    public Optional<FirmDetails> getFirm(Long firmId) {
        return Optional.ofNullable(firmService.get(firmId));
    }

    public FirmAnalyticsResponse getDashboard(Long firmId) {
        Long prev = TenantContext.getCurrentFirmId();
        try {
            TenantContext.setCurrentFirmId(firmId);
            return invoiceService.getFirmAnalytics(firmId);
        } finally {
            if (prev != null) TenantContext.setCurrentFirmId(prev);
            else TenantContext.clear();
        }
    }

    public List<Customer> searchCustomers(Long firmId, String query) {
        Long prev = TenantContext.getCurrentFirmId();
        try {
            TenantContext.setCurrentFirmId(firmId);
            return customerService.search(firmId, query);
        } finally {
            if (prev != null) TenantContext.setCurrentFirmId(prev);
            else TenantContext.clear();
        }
    }

    public Optional<Customer> getCustomerById(Long firmId, Long customerId) {
        Long prev = TenantContext.getCurrentFirmId();
        try {
            TenantContext.setCurrentFirmId(firmId);
            return Optional.ofNullable(customerService.getById(customerId, firmId));
        } finally {
            if (prev != null) TenantContext.setCurrentFirmId(prev);
            else TenantContext.clear();
        }
    }

    public List<Product> searchProducts(Long firmId, String query) {
        Long prev = TenantContext.getCurrentFirmId();
        try {
            TenantContext.setCurrentFirmId(firmId);
            return productService.searchProducts(firmId, query);
        } finally {
            if (prev != null) TenantContext.setCurrentFirmId(prev);
            else TenantContext.clear();
        }
    }

    public Optional<Product> getProductById(Long firmId, Long productId) {
        Long prev = TenantContext.getCurrentFirmId();
        try {
            TenantContext.setCurrentFirmId(firmId);
            return Optional.ofNullable(productService.getById(productId, firmId));
        } finally {
            if (prev != null) TenantContext.setCurrentFirmId(prev);
            else TenantContext.clear();
        }
    }

    public Page<Invoice> getRecentInvoices(Long firmId, int limit) {
        Long prev = TenantContext.getCurrentFirmId();
        try {
            TenantContext.setCurrentFirmId(firmId);
            return invoiceService.getPaginated(firmId, PageRequest.of(0, limit));
        } finally {
            if (prev != null) TenantContext.setCurrentFirmId(prev);
            else TenantContext.clear();
        }
    }

    public Page<Invoice> getRecentEstimates(Long firmId, int limit) {
        Long prev = TenantContext.getCurrentFirmId();
        try {
            TenantContext.setCurrentFirmId(firmId);
            return invoiceService.getPaginatedEstimates(firmId, PageRequest.of(0, limit));
        } finally {
            if (prev != null) TenantContext.setCurrentFirmId(prev);
            else TenantContext.clear();
        }
    }

    public Optional<Invoice> getInvoiceById(Long firmId, Long invoiceId) {
        Long prev = TenantContext.getCurrentFirmId();
        try {
            TenantContext.setCurrentFirmId(firmId);
            Invoice inv = invoiceService.getById(invoiceId);
            if (inv != null && firmId.equals(inv.getFirmId())) {
                return Optional.of(inv);
            }
            return Optional.empty();
        } finally {
            if (prev != null) TenantContext.setCurrentFirmId(prev);
            else TenantContext.clear();
        }
    }

    public Object getDebtorsSummary(Long firmId) {
        Long prev = TenantContext.getCurrentFirmId();
        try {
            TenantContext.setCurrentFirmId(firmId);
            return debtorService.getOverview(firmId);
        } finally {
            if (prev != null) TenantContext.setCurrentFirmId(prev);
            else TenantContext.clear();
        }
    }

    public List<PurchaseOrder> getRecentPurchaseOrders(Long firmId, int limit) {
        Long prev = TenantContext.getCurrentFirmId();
        try {
            TenantContext.setCurrentFirmId(firmId);
            return poService.getPaginatedPurchaseOrders(firmId, PageRequest.of(0, limit)).getContent();
        } finally {
            if (prev != null) TenantContext.setCurrentFirmId(prev);
            else TenantContext.clear();
        }
    }
}
