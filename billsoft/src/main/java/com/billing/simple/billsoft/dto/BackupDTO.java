package com.billing.simple.billsoft.dto;

import com.billing.simple.billsoft.entities.*;

import java.util.List;
import java.util.Map;

public class BackupDTO {
    private Map<String, Object> metadata;
    private FirmDetails firmDetails;
    private List<Customer> customers;
    private List<Product> products;
    private List<StockMovement> stockMovements;
    private List<Invoice> invoices;
    private List<Party> parties;
    private List<PartyPayment> partyPayments;
    private List<PurchaseOrder> purchaseOrders;
    private List<Reminder> reminders;
    private List<Note> notes;
    private List<Expense> expenses;
    private List<Employee> employees;
    private List<AttendanceRecord> attendanceRecords;
    private List<LeaveRecord> leaveRecords;
    private List<SalaryRecord> salaryRecords;
    private List<EmployeeAdvance> advances;
    private List<PromotionRecord> promotions;
    private List<BusinessLetter> businessLetters;
    private List<InboxMessage> inboxMessages;

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

    public List<StockMovement> getStockMovements() {
        return stockMovements;
    }

    public void setStockMovements(List<StockMovement> stockMovements) {
        this.stockMovements = stockMovements;
    }

    public List<Invoice> getInvoices() {
        return invoices;
    }

    public void setInvoices(List<Invoice> invoices) {
        this.invoices = invoices;
    }

    public List<Party> getParties() {
        return parties;
    }

    public void setParties(List<Party> parties) {
        this.parties = parties;
    }

    public List<PartyPayment> getPartyPayments() {
        return partyPayments;
    }

    public void setPartyPayments(List<PartyPayment> partyPayments) {
        this.partyPayments = partyPayments;
    }

    public List<PurchaseOrder> getPurchaseOrders() {
        return purchaseOrders;
    }

    public void setPurchaseOrders(List<PurchaseOrder> purchaseOrders) {
        this.purchaseOrders = purchaseOrders;
    }

    public List<Reminder> getReminders() {
        return reminders;
    }

    public void setReminders(List<Reminder> reminders) {
        this.reminders = reminders;
    }

    public List<Note> getNotes() {
        return notes;
    }

    public void setNotes(List<Note> notes) {
        this.notes = notes;
    }

    public List<Expense> getExpenses() {
        return expenses;
    }

    public void setExpenses(List<Expense> expenses) {
        this.expenses = expenses;
    }

    public List<Employee> getEmployees() {
        return employees;
    }

    public void setEmployees(List<Employee> employees) {
        this.employees = employees;
    }

    public List<AttendanceRecord> getAttendanceRecords() {
        return attendanceRecords;
    }

    public void setAttendanceRecords(List<AttendanceRecord> attendanceRecords) {
        this.attendanceRecords = attendanceRecords;
    }

    public List<LeaveRecord> getLeaveRecords() {
        return leaveRecords;
    }

    public void setLeaveRecords(List<LeaveRecord> leaveRecords) {
        this.leaveRecords = leaveRecords;
    }

    public List<SalaryRecord> getSalaryRecords() {
        return salaryRecords;
    }

    public void setSalaryRecords(List<SalaryRecord> salaryRecords) {
        this.salaryRecords = salaryRecords;
    }

    public List<EmployeeAdvance> getAdvances() {
        return advances;
    }

    public void setAdvances(List<EmployeeAdvance> advances) {
        this.advances = advances;
    }

    public List<PromotionRecord> getPromotions() {
        return promotions;
    }

    public void setPromotions(List<PromotionRecord> promotions) {
        this.promotions = promotions;
    }

    public List<BusinessLetter> getBusinessLetters() {
        return businessLetters;
    }

    public void setBusinessLetters(List<BusinessLetter> businessLetters) {
        this.businessLetters = businessLetters;
    }

    public List<InboxMessage> getInboxMessages() {
        return inboxMessages;
    }

    public void setInboxMessages(List<InboxMessage> inboxMessages) {
        this.inboxMessages = inboxMessages;
    }
}
