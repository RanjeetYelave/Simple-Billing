package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.InvoiceItem;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;
import com.billing.simple.billsoft.repo.InvoiceRepository;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class BackupService {

    private final FirmDetailsRepository firmDetailsRepo;
    private final CustomerRepository customerRepo;
    private final ProductRepository productRepo;
    private final InvoiceRepository invoiceRepo;
    private final AppConfigRepository appConfigRepo;

    public BackupService(FirmDetailsRepository firmDetailsRepo,
                         CustomerRepository customerRepo,
                         ProductRepository productRepo,
                         InvoiceRepository invoiceRepo,
                         AppConfigRepository appConfigRepo) {
        this.firmDetailsRepo = firmDetailsRepo;
        this.customerRepo = customerRepo;
        this.productRepo = productRepo;
        this.invoiceRepo = invoiceRepo;
        this.appConfigRepo = appConfigRepo;
    }

    public BackupDTO exportData(Long firmId) {
        BackupDTO backup = new BackupDTO();
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("version", "1.0");
        metadata.put("exportDate", LocalDateTime.now().toString());
        metadata.put("firmId", firmId);
        
        Optional<FirmDetails> firmOpt = firmDetailsRepo.findById(firmId);
        if (firmOpt.isPresent()) {
            backup.setFirmDetails(firmOpt.get());
            metadata.put("originalFirmName", firmOpt.get().getFirmName());
        }

        backup.setMetadata(metadata);
        backup.setCustomers(customerRepo.findByFirmIdOrderByNameAsc(firmId));
        backup.setProducts(productRepo.findByFirmId(firmId));
        backup.setInvoices(invoiceRepo.findAllByFirmId(firmId));

        return backup;
    }

    @Transactional
    public void importData(BackupDTO backup, Long targetFirmId, boolean merge) {
        if (backup == null || backup.getMetadata() == null) {
            throw new RuntimeException("Invalid backup file: Missing metadata");
        }
        
        if (!merge) {
            // Mode A: Restore as New Firm (Clone)
            FirmDetails newFirm = new FirmDetails();
            if (backup.getFirmDetails() != null) {
                newFirm.setFirmName(backup.getFirmDetails().getFirmName() + " (Restored)");
                newFirm.setOwnerName(backup.getFirmDetails().getOwnerName());
                newFirm.setAddressLine1(backup.getFirmDetails().getAddressLine1());
                newFirm.setAddressLine2(backup.getFirmDetails().getAddressLine2());
                newFirm.setCity(backup.getFirmDetails().getCity());
                newFirm.setState(backup.getFirmDetails().getState());
                newFirm.setPincode(backup.getFirmDetails().getPincode());
                newFirm.setPhone(backup.getFirmDetails().getPhone());
                newFirm.setEmail(backup.getFirmDetails().getEmail());
                newFirm.setGstin(backup.getFirmDetails().getGstin());
                newFirm.setLogoBase64(backup.getFirmDetails().getLogoBase64());
                newFirm.setBankName(backup.getFirmDetails().getBankName());
                newFirm.setBankAccount(backup.getFirmDetails().getBankAccount());
                newFirm.setBankIfsc(backup.getFirmDetails().getBankIfsc());
                newFirm.setFooterNote(backup.getFirmDetails().getFooterNote());
            } else {
                newFirm.setFirmName("Restored Firm");
            }
            newFirm = firmDetailsRepo.save(newFirm);
            targetFirmId = newFirm.getId();
        }

        // Import Customers
        Map<Long, Customer> oldToNewCustomerMap = new HashMap<>();
        if (backup.getCustomers() != null) {
            for (Customer c : backup.getCustomers()) {
                Customer newC = new Customer();
                newC.setFirmId(targetFirmId);
                newC.setName(c.getName());
                newC.setPhone(c.getPhone());
                newC.setAddress(c.getAddress());
                newC.setEmail(c.getEmail());
                newC.setGstin(c.getGstin());
                newC = customerRepo.save(newC);
                oldToNewCustomerMap.put(c.getId(), newC);
            }
        }

        // Import Products
        Map<Long, Product> oldToNewProductMap = new HashMap<>();
        if (backup.getProducts() != null) {
            for (Product p : backup.getProducts()) {
                Product newP = new Product();
                newP.setFirmId(targetFirmId);
                newP.setName(p.getName());
                newP.setPrice(p.getPrice());
                newP.setGstPercentage(p.getGstPercentage());
                newP = productRepo.save(newP);
                oldToNewProductMap.put(p.getId(), newP);
            }
        }

        // Import Invoices
        if (backup.getInvoices() != null) {
            for (Invoice inv : backup.getInvoices()) {
                Invoice newInv = new Invoice();
                newInv.setFirmId(targetFirmId);
                newInv.setInvoiceNumber(inv.getInvoiceNumber());
                newInv.setInvoiceDate(inv.getInvoiceDate());
                newInv.setDueDate(inv.getDueDate());
                newInv.setTotalAmount(inv.getTotalAmount());
                newInv.setSubtotalWithoutTax(inv.getSubtotalWithoutTax());
                newInv.setTotalTax(inv.getTotalTax());
                newInv.setTotalDiscount(inv.getTotalDiscount());
                newInv.setInvoiceDiscountType(inv.getInvoiceDiscountType());
                newInv.setInvoiceDiscountValue(inv.getInvoiceDiscountValue());
                newInv.setRoundOff(inv.getRoundOff());
                newInv.setStatus(inv.getStatus());
                newInv.setPaid(inv.getPaid());
                newInv.setEstimateNumber(inv.getEstimateNumber());
                newInv.setCustomerNote(inv.getCustomerNote());
                newInv.setTermsAndConditions(inv.getTermsAndConditions());
                newInv.setPaymentMethod(inv.getPaymentMethod());
                newInv.setCurrency(inv.getCurrency() != null ? inv.getCurrency() : "INR");
                newInv.setTags(inv.getTags());
                
                // Map Customer
                if (inv.getCustomer() != null && oldToNewCustomerMap.containsKey(inv.getCustomer().getId())) {
                    newInv.setCustomer(oldToNewCustomerMap.get(inv.getCustomer().getId()));
                }

                // Add Items
                if (inv.getItems() != null) {
                    for (InvoiceItem item : inv.getItems()) {
                        InvoiceItem newItem = new InvoiceItem();
                        newItem.setInvoice(newInv);
                        newItem.setQty(item.getQty());
                        newItem.setUnit(item.getUnit());
                        newItem.setPricePerUnit(item.getPricePerUnit());
                        newItem.setAmountWithoutTax(item.getAmountWithoutTax());
                        newItem.setDiscountType(item.getDiscountType());
                        newItem.setDiscountValue(item.getDiscountValue());
                        newItem.setDiscountPercent(item.getDiscountPercent());
                        newItem.setTaxableAmount(item.getTaxableAmount());
                        newItem.setGstPercent(item.getGstPercent());
                        newItem.setGstAmount(item.getGstAmount());
                        newItem.setLineTotal(item.getLineTotal());
                        
                        // Map Product
                        if (item.getProduct() != null && oldToNewProductMap.containsKey(item.getProduct().getId())) {
                            newItem.setProduct(oldToNewProductMap.get(item.getProduct().getId()));
                        }
                        
                        newInv.getItems().add(newItem);
                    }
                }
                
                invoiceRepo.save(newInv);
                //dummy
            }
        }
    }

    @Transactional
    public void factoryReset() {
        invoiceRepo.deleteAll();
        productRepo.deleteAll();
        customerRepo.deleteAll();
        firmDetailsRepo.deleteAll();
        appConfigRepo.deleteAll();
    }
}
