package com.billing.simple.billsoft.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BackupInspectionDTO {
    private String backupType; // SINGLE_FIRM or FULL_SYSTEM_BACKUP
    private String version;
    private String exportDate;
    private List<FirmSummary> firms = new ArrayList<>();
    private Map<String, Integer> totalStats = new HashMap<>();

    public static class FirmSummary {
        private Long firmId;
        private String firmName;
        private String ownerName;
        private String gstin;
        private String phone;
        private String email;
        private int customerCount;
        private int productCount;
        private int invoiceCount;
        private int purchaseOrderCount;
        private int employeeCount;
        private int expenseCount;
        private int letterCount;

        public Long getFirmId() {
            return firmId;
        }

        public void setFirmId(Long firmId) {
            this.firmId = firmId;
        }

        public String getFirmName() {
            return firmName;
        }

        public void setFirmName(String firmName) {
            this.firmName = firmName;
        }

        public String getOwnerName() {
            return ownerName;
        }

        public void setOwnerName(String ownerName) {
            this.ownerName = ownerName;
        }

        public String getGstin() {
            return gstin;
        }

        public void setGstin(String gstin) {
            this.gstin = gstin;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public int getCustomerCount() {
            return customerCount;
        }

        public void setCustomerCount(int customerCount) {
            this.customerCount = customerCount;
        }

        public int getProductCount() {
            return productCount;
        }

        public void setProductCount(int productCount) {
            this.productCount = productCount;
        }

        public int getInvoiceCount() {
            return invoiceCount;
        }

        public void setInvoiceCount(int invoiceCount) {
            this.invoiceCount = invoiceCount;
        }

        public int getPurchaseOrderCount() {
            return purchaseOrderCount;
        }

        public void setPurchaseOrderCount(int purchaseOrderCount) {
            this.purchaseOrderCount = purchaseOrderCount;
        }

        public int getEmployeeCount() {
            return employeeCount;
        }

        public void setEmployeeCount(int employeeCount) {
            this.employeeCount = employeeCount;
        }

        public int getExpenseCount() {
            return expenseCount;
        }

        public void setExpenseCount(int expenseCount) {
            this.expenseCount = expenseCount;
        }

        public int getLetterCount() {
            return letterCount;
        }

        public void setLetterCount(int letterCount) {
            this.letterCount = letterCount;
        }
    }

    public String getBackupType() {
        return backupType;
    }

    public void setBackupType(String backupType) {
        this.backupType = backupType;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getExportDate() {
        return exportDate;
    }

    public void setExportDate(String exportDate) {
        this.exportDate = exportDate;
    }

    public List<FirmSummary> getFirms() {
        return firms;
    }

    public void setFirms(List<FirmSummary> firms) {
        this.firms = firms;
    }

    public Map<String, Integer> getTotalStats() {
        return totalStats;
    }

    public void setTotalStats(Map<String, Integer> totalStats) {
        this.totalStats = totalStats;
    }
}
