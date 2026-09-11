# Billsoft / RupeeCRM — Comprehensive Backup & Restore Feature Audit

**Audit Date:** September 11, 2026  
**Audited System:** Billsoft (RupeeCRM) Enterprise Billing & Inventory Suite  
**Scope:** Complete Database Entities, Export/Import Pipelines, Auto-Backup Engine, Referential Integrity, Factory Reset, and Disaster Recovery.

---

## 1. Executive Summary

A comprehensive architectural and code audit was conducted on the Backup & Restore subsystem in **Billsoft**. The audit examined **all 27 JPA database entities**, data transfer objects ([BackupDTO.java](file:///Users/afk/Documents/GitHub/Simple-Billing/billsoft/src/main/java/com/billing/simple/billsoft/dto/BackupDTO.java), [BackupInspectionDTO.java](file:///Users/afk/Documents/GitHub/Simple-Billing/billsoft/src/main/java/com/billing/simple/billsoft/dto/BackupInspectionDTO.java)), services ([BackupService.java](file:///Users/afk/Documents/GitHub/Simple-Billing/billsoft/src/main/java/com/billing/simple/billsoft/service/BackupService.java), [AutoBackupService.java](file:///Users/afk/Documents/GitHub/Simple-Billing/billsoft/src/main/java/com/billing/simple/billsoft/service/AutoBackupService.java)), controller endpoints ([BackupController.java](file:///Users/afk/Documents/GitHub/Simple-Billing/billsoft/src/main/java/com/billing/simple/billsoft/controllers/BackupController.java)), and frontend API integrations ([api.js](file:///Users/afk/Documents/GitHub/Simple-Billing/billsoft/src/main/webapp/js/api.js)).

### Audit Verdict: **PASSED (100% Business Data Coverage)**
- **100% of business entities and child collections** are serialized in JSON exports and restored with relational integrity.
- Foreign Key (FK) remapping maps parent-child relations across databases with divergent IDs.
- Automated daily backups write atomically to a dedicated storage location outside the source codebase.
- Multi-firm isolation and single-firm backup export/import pipelines function with selective restoration capability.

---

## 2. Complete Entity Coverage Matrix (27/27 Entities)

The table below audits all database entities in `com.billing.simple.billsoft.entities` against the backup and restore pipeline:

| # | Entity Name | Database Table | Exported | Restored | Foreign Key / Mapping Handling | Multi-Firm / Tenant Scope |
|---|-------------|----------------|:--------:|:--------:|--------------------------------|---------------------------|
| 1 | `FirmDetails` | `firm_details` | ✅ Yes | ✅ Yes | Primary firm & multi-firm list (`allFirms`) mapped to new ID. | Firm Root Entity |
| 2 | `Customer` | `customer` | ✅ Yes | ✅ Yes | Translated via `oldToNewCustomerMap` for FK cascading. | Firm-scoped (`firmId`) |
| 3 | `Product` | `product` | ✅ Yes | ✅ Yes | Rates, stock, HSN, SKU, barcodes; mapped via `oldToNewProductMap`. | Firm-scoped (`firmId`) |
| 4 | `StockMovement` | `stock_movement` | ✅ Yes | ✅ Yes | Remaps `productId` to newly generated product IDs. | Firm-scoped (`firmId`) |
| 5 | `Invoice` | `invoice` | ✅ Yes | ✅ Yes | Remaps `customer` FK; mapped via `oldToNewInvoiceMap`. | Firm-scoped (`firmId`) |
| 6 | `InvoiceItem` | `invoice_item` | ✅ Yes | ✅ Yes | Embedded child collection in `Invoice.items`; remaps `product` FK. | Inherited from `Invoice` |
| 7 | `InvoicePayment` | `invoice_payment` | ✅ Yes | ✅ Yes | Remaps `invoiceId` and `customerId` to new database IDs. | Firm-scoped (`firmId`) |
| 8 | `Party` (Vendors) | `party` | ✅ Yes | ✅ Yes | Vendor bank, GSTIN, PAN; mapped via `oldToNewPartyMap`. | Firm-scoped (`firmId`) |
| 9 | `PartyPayment` | `party_payment` | ✅ Yes | ✅ Yes | Remaps `partyId` and `purchaseOrderId`. | Firm-scoped (`firmId`) |
| 10 | `PurchaseOrder` | `purchase_order` | ✅ Yes | ✅ Yes | Remaps `party` FK; mapped via `oldToNewPoMap`. | Firm-scoped (`firmId`) |
| 11 | `PurchaseOrderItem` | `purchase_order_item` | ✅ Yes | ✅ Yes | Embedded child collection in `PurchaseOrder.items`; remaps `productId`. | Inherited from `PurchaseOrder` |
| 12 | `SalesReturn` | `sales_return` | ✅ Yes | ✅ Yes | Remaps parent `invoice` and `customer`. | Firm-scoped (`firmId`) |
| 13 | `SalesReturnItem` | `sales_return_item` | ✅ Yes | ✅ Yes | Embedded child collection in `SalesReturn.items`; remaps `product`. | Inherited from `SalesReturn` |
| 14 | `Expense` | `expense` | ✅ Yes | ✅ Yes | Preserves category, dates, payment mode, amounts. | Firm-scoped (`firmId`) |
| 15 | `Employee` | `employee` | ✅ Yes | ✅ Yes | HR profile, payroll baseline; mapped via `oldToNewEmpMap`. | Firm-scoped (`firmId`) |
| 16 | `EmployeeDocument` | `employee_document` | ✅ Yes | ✅ Yes | Preserves base64 payload; remaps `employee` FK. | Inherited from `Employee` |
| 17 | `EmployeeAdvance` | `employee_advance` | ✅ Yes | ✅ Yes | Advance balance tracking; remaps `employee` FK. | Inherited from `Employee` |
| 18 | `SalaryRecord` | `salary_record` | ✅ Yes | ✅ Yes | Payroll history & deductions; remaps `employee` FK. | Inherited from `Employee` |
| 19 | `LeaveRecord` | `leave_record` | ✅ Yes | ✅ Yes | Leave ledger & approvals; remaps `employee` FK. | Inherited from `Employee` |
| 20 | `AttendanceRecord` | `attendance_record` | ✅ Yes | ✅ Yes | Daily check-in status; remaps `employee` FK. | Inherited from `Employee` |
| 21 | `PromotionRecord` | `promotion_record` | ✅ Yes | ✅ Yes | Career & salary revision logs; remaps `employee` FK. | Inherited from `Employee` |
| 22 | `BusinessLetter` | `business_letter` | ✅ Yes | ✅ Yes | Formal letters; remaps `partyId` and `customerId`. | Firm-scoped (`firmId`) |
| 23 | `Note` | `notes` | ✅ Yes | ✅ Yes | Internal scratchpad notes; remaps `customerId`. | Firm-scoped (`firmId`) |
| 24 | `Reminder` | `reminders` | ✅ Yes | ✅ Yes | Task schedule; mapped via `oldToNewReminderMap`. | Firm-scoped (`firmId`) |
| 25 | `InboxMessage` | `inbox_message` | ✅ Yes | ✅ Yes | In-app alerts; remaps `reminderId`. | Firm-scoped (`firmId`) |
| 26 | `AppConfig` | `app_config` | ✅ Yes | ✅ Yes | System settings, security hashes, feature flags. | System-wide |
| 27 | `SystemStat` | `system_stats` | ⚙️ Dynamic | ⚙️ Dynamic | Runtime hardware/JVM metrics (uptime, request count). Automatically re-initialized on startup. | Node/Runtime Specific |

---

## 3. Architecture & Functional Capabilities

### 3.1 Single-Firm vs Full-System Exports
- **Single-Firm Export (`/api/backup/export?firmId={id}`)**:
  - Isolates data strictly belonging to the requested firm ID.
  - Traverses and exports firm-level customers, products, stock movements, invoices, invoice payments, sales returns, purchase orders, parties, party payments, expenses, business letters, notes, reminders, inbox messages, employees, and employee sub-records (attendance, leaves, salaries, advances, promotions, documents).
- **Full-System Export (`/api/backup/export/all`)**:
  - Exports the entire relational graph across all firms.
  - Tagged in metadata with `type: "FULL_SYSTEM_BACKUP"`.
  - Serializes `allFirms` list containing all configured enterprises.

### 3.2 Backup Inspection & Pre-Flight Analysis
- **Endpoint (`/api/backup/inspect` and `/api/backup/auto/inspect`)**:
  - Allows previewing backup contents before applying changes.
  - Parses metadata, backup type (`SINGLE_FIRM` vs `FULL_SYSTEM_BACKUP`), export timestamps, firm identities, and per-firm record counters (Customers, Products, Invoices, Purchase Orders, Employees, Expenses, Letters).

### 3.3 Relational ID Translation & Referential Integrity
- During import/restore operations into an existing database:
  1. `FirmDetails` is created or merged, producing a new `mappedFirmId`.
  2. `Customer` and `Product` records are persisted first, populating `oldToNewCustomerMap` and `oldToNewProductMap`.
  3. `Invoice` records are inserted with customer linkage, followed by embedded `InvoiceItem` records with product mapping, creating `oldToNewInvoiceMap`.
  4. `InvoicePayment` records are created using the translated `newInvoiceId` and `newCustId`.
  5. `SalesReturn` and `SalesReturnItem` records link directly to the new `Invoice` and `Product` IDs.
  6. `Party` (Vendor) records populate `oldToNewPartyMap`.
  7. `PurchaseOrder` and `PurchaseOrderItem` records link to new `Party` and `Product` IDs, creating `oldToNewPoMap`.
  8. `PartyPayment` records reference translated `newPartyId` and `newPoId`.
  9. `Employee` records populate `oldToNewEmpMap`, and all 6 dependent HR collections (`AttendanceRecord`, `LeaveRecord`, `SalaryRecord`, `EmployeeAdvance`, `PromotionRecord`, `EmployeeDocument`) are saved with employee foreign keys intact.
  10. `BusinessLetter`, `Note`, `Reminder`, and `InboxMessage` records link to their respective parents.

### 3.4 Automated Backup Subsystem (`AutoBackupService`)
- **Storage Location**: Configured via `BILLSOFT_DATA_DIR` or OS-specific standard application data directories (`~/Library/Application Support/SimpleBilling/backups` on macOS, `%APPDATA%\SimpleBilling\backups` on Windows).
- **Atomic File Write Strategy**: Writes first to a temporary file (`autobackup_temp_{timestamp}.json`), validates non-empty status, and performs an atomic filesystem move (`Files.move(..., ATOMIC_MOVE)`).
- **Startup Diagnostic Check**: Automatically verifies if a valid backup exists for the current day; if not, triggers an immediate background backup generation.
- **Retention & Pruning**: Automatically prunes stale/temporary files, keeping the latest backup (`autobackup_latest.json`).

### 3.5 Factory Reset & Clean Wipe Protection
- **Factory Reset Sequence**: Executes batch deletions in reverse topological dependency order (Child invoice items/payments -> Invoices -> Sales returns -> Stock movements -> PO items/payments -> Purchase orders -> Parties -> Letters -> HR sub-records -> Employees -> Operational logs -> Catalogs -> Firms -> App config).
- **Security Validation**: Protected by `MASTER_KEY_HASH` (SHA-256) and optional admin password verification when authentication is active.

---

## 4. Verification Summary

- [x] All 27 entities verified against `BackupDTO`, `BackupService`, and entity models.
- [x] Cascade persistence confirmed for embedded collections (`InvoiceItem`, `PurchaseOrderItem`, `SalesReturnItem`).
- [x] Base64 binary payload preservation confirmed for `EmployeeDocument` and `FirmDetails.logoBase64`.
- [x] Foreign key remapping verified for multi-tenant and single-tenant imports.
- [x] Atomic write and automatic daily backup mechanics verified.

**Conclusion:** The Billsoft backup feature is comprehensive and includes all business data, master catalogs, financial transactions, HR records, documents, correspondence, notes, and configuration settings across single-firm and multi-firm operations.
