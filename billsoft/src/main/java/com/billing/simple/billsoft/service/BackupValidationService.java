package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.exception.BackupValidationException;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class BackupValidationService {

    public void validate(BackupDTO backup) {
        if (backup == null) {
            throw new BackupValidationException("MALFORMED_BACKUP", "Backup data cannot be null.");
        }
        if (backup.getMetadata() == null) {
            throw new BackupValidationException("MISSING_METADATA", "Backup metadata is missing.");
        }

        List<String> errors = new ArrayList<>();

        // 1. INVOICES - Intra-firm uniqueness on (firmId, invoiceNumber) and (firmId, estimateNumber)
        if (backup.getInvoices() != null) {
            Map<String, Long> seenInvoiceNumbers = new HashMap<>();
            Map<String, Long> seenEstimateNumbers = new HashMap<>();

            for (Invoice inv : backup.getInvoices()) {
                Long firmId = inv.getFirmId() != null ? inv.getFirmId() : backup.getSourceFirmId();
                if (firmId == null) firmId = 0L;

                if (inv.getInvoiceNumber() != null && !inv.getInvoiceNumber().trim().isEmpty()) {
                    String key = firmId + ":" + inv.getInvoiceNumber().trim().toUpperCase();
                    Long prevId = seenInvoiceNumbers.get(key);
                    if (prevId != null && !Objects.equals(prevId, inv.getId())) {
                        errors.add("Duplicate invoice number '" + inv.getInvoiceNumber() + "' in firm " + firmId 
                                + " (conflicting invoice IDs: " + prevId + ", " + inv.getId() + ")");
                    } else {
                        seenInvoiceNumbers.put(key, inv.getId());
                    }
                }

                if (inv.getEstimateNumber() != null && !inv.getEstimateNumber().trim().isEmpty()) {
                    String key = firmId + ":" + inv.getEstimateNumber().trim().toUpperCase();
                    Long prevId = seenEstimateNumbers.get(key);
                    if (prevId != null && !Objects.equals(prevId, inv.getId())) {
                        errors.add("Duplicate estimate number '" + inv.getEstimateNumber() + "' in firm " + firmId 
                                + " (conflicting invoice IDs: " + prevId + ", " + inv.getId() + ")");
                    } else {
                        seenEstimateNumbers.put(key, inv.getId());
                    }
                }
            }
        }

        // 2. PURCHASE ORDERS - Intra-firm uniqueness on (firmId, poNumber)
        if (backup.getPurchaseOrders() != null) {
            Map<String, Long> seenPoNumbers = new HashMap<>();
            for (PurchaseOrder po : backup.getPurchaseOrders()) {
                Long firmId = po.getFirmId() != null ? po.getFirmId() : backup.getSourceFirmId();
                if (firmId == null) firmId = 0L;

                if (po.getPoNumber() != null && !po.getPoNumber().trim().isEmpty()) {
                    String key = firmId + ":" + po.getPoNumber().trim().toUpperCase();
                    Long prevId = seenPoNumbers.get(key);
                    if (prevId != null && !Objects.equals(prevId, po.getId())) {
                        errors.add("Duplicate purchase order number '" + po.getPoNumber() + "' in firm " + firmId 
                                + " (conflicting PO IDs: " + prevId + ", " + po.getId() + ")");
                    } else {
                        seenPoNumbers.put(key, po.getId());
                    }
                }
            }
        }

        // 3. SALES RETURNS - Intra-firm uniqueness on (firmId, returnNumber)
        if (backup.getSalesReturns() != null) {
            Map<String, Long> seenReturnNumbers = new HashMap<>();
            for (SalesReturn sr : backup.getSalesReturns()) {
                Long firmId = sr.getFirmId() != null ? sr.getFirmId() : backup.getSourceFirmId();
                if (firmId == null) firmId = 0L;

                if (sr.getReturnNumber() != null && !sr.getReturnNumber().trim().isEmpty()) {
                    String key = firmId + ":" + sr.getReturnNumber().trim().toUpperCase();
                    Long prevId = seenReturnNumbers.get(key);
                    if (prevId != null && !Objects.equals(prevId, sr.getId())) {
                        errors.add("Duplicate sales return number '" + sr.getReturnNumber() + "' in firm " + firmId 
                                + " (conflicting Return IDs: " + prevId + ", " + sr.getId() + ")");
                    } else {
                        seenReturnNumbers.put(key, sr.getId());
                    }
                }
            }
        }

        // 4. ATTENDANCE RECORDS - Uniqueness on (employeeId, date)
        if (backup.getAttendanceRecords() != null) {
            Map<String, Long> seenAttendance = new HashMap<>();
            for (AttendanceRecord att : backup.getAttendanceRecords()) {
                Long empId = att.getEmployee() != null ? att.getEmployee().getId() : null;
                if (empId != null && att.getDate() != null) {
                    String key = empId + ":" + att.getDate();
                    Long prevId = seenAttendance.get(key);
                    if (prevId != null && !Objects.equals(prevId, att.getId())) {
                        errors.add("Duplicate attendance entry for employee " + empId + " on date " + att.getDate()
                                + " (conflicting Attendance IDs: " + prevId + ", " + att.getId() + ")");
                    } else {
                        seenAttendance.put(key, att.getId());
                    }
                }
            }
        }

        // 5. NOTIFICATIONS - Uniqueness on (firmId, eventKey)
        if (backup.getNotifications() != null) {
            Map<String, Long> seenNotifs = new HashMap<>();
            for (Notification n : backup.getNotifications()) {
                Long firmId = n.getFirmId() != null ? n.getFirmId() : backup.getSourceFirmId();
                if (firmId == null) firmId = 0L;
                if (n.getEventKey() != null && !n.getEventKey().trim().isEmpty()) {
                    String key = firmId + ":" + n.getEventKey().trim();
                    Long prevId = seenNotifs.get(key);
                    if (prevId != null && !Objects.equals(prevId, n.getId())) {
                        errors.add("Duplicate notification event key '" + n.getEventKey() + "' in firm " + firmId
                                + " (conflicting Notification IDs: " + prevId + ", " + n.getId() + ")");
                    } else {
                        seenNotifs.put(key, n.getId());
                    }
                }
            }
        }

        // 6. NOTIFICATION PREFERENCES - Uniqueness on (firmId)
        if (backup.getNotificationPreferences() != null) {
            Map<Long, Long> seenPrefs = new HashMap<>();
            for (NotificationPreference pref : backup.getNotificationPreferences()) {
                Long firmId = pref.getFirmId() != null ? pref.getFirmId() : backup.getSourceFirmId();
                if (firmId != null) {
                    Long prevId = seenPrefs.get(firmId);
                    if (prevId != null && !Objects.equals(prevId, pref.getId())) {
                        errors.add("Duplicate notification preferences for firm " + firmId);
                    } else {
                        seenPrefs.put(firmId, pref.getId());
                    }
                }
            }
        }

        // 7. SALARY RECORDS - Uniqueness on (employeeId, monthYear)
        if (backup.getSalaryRecords() != null) {
            Map<String, Long> seenSalaries = new HashMap<>();
            for (SalaryRecord sal : backup.getSalaryRecords()) {
                Long empId = sal.getEmployee() != null ? sal.getEmployee().getId() : null;
                if (empId != null && sal.getMonthYear() != null && !sal.getMonthYear().trim().isEmpty()) {
                    String key = empId + ":" + sal.getMonthYear().trim().toUpperCase();
                    Long prevId = seenSalaries.get(key);
                    if (prevId != null && !Objects.equals(prevId, sal.getId())) {
                        errors.add("Duplicate salary record for employee " + empId + " for period " + sal.getMonthYear()
                                + " (conflicting Salary IDs: " + prevId + ", " + sal.getId() + ")");
                    } else {
                        seenSalaries.put(key, sal.getId());
                    }
                }
            }
        }

        if (!errors.isEmpty()) {
            String primaryMessage = "Backup validation failed: detected " + errors.size() + " data integrity / uniqueness issue(s).";
            String firstError = errors.get(0);
            throw new BackupValidationException("BACKUP_VALIDATION_ERROR", primaryMessage + " (" + firstError + ")", errors);
        }
    }
}
