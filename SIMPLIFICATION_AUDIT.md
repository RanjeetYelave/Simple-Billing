# RUPEECRM SOFTWARE MANAGEMENT APPLICATION — SIMPLIFICATION AUDIT

**Audit Date:** 2026-09-23  
**Target Repository:** `RanjeetYelave/SoftwareManagementTool`  
**Customer Repository:** `Simple-Billing`  
**Legacy Reference Tool:** `Simple-Billing/tools/SoftwareManagementTool.html`

---

## 1. Current New License Fields vs. Simplified Operator Experience

### Current New License / Sale Fields in Operator UI:
- `customerName` (Customer Full Name)
- `companyName` (Firm Name)
- `customerPhone` (Mobile)
- `customerEmail` (Email)
- `gstin` (GST Identification Number)
- `billingAddress` (Full Billing Address)
- `machineId` (MID)
- `salesmanId` (Assigned Salesman dropdown)
- `tier` (Software Tier: BRONZE, SILVER, GOLD, DIAMOND, PROFESSIONAL, ENTERPRISE)
- `licenseTenure` (License Tenure: 1_YEAR, 3_YEARS, LIFETIME)
- `basePrice` (Catalog / Base Price)
- `discountAmount` (Discount Amount)
- `agreedPrice` (Final / Negotiated Price)
- `paymentMode` (Dropdown: FULL_PAYMENT, INSTALLMENT)
- `paymentMethod` (Dropdown: CASH, UPI, BANK_TRANSFER, CHEQUE, CARD)
- `referenceNumber` (Payment Txn Reference)
- `notes` (Internal Sale Notes)
- `dpActive` (Data Protection Checkbox)
- `dpTenure` (Data Protection Tenure: 1_YEAR, 2_YEARS, 3_YEARS)
- `downPayment` (EMI Down Payment)
- `tenureMonths` (EMI Tenure Months)
- `interestType` (Dropdown: NO_COST, FLAT_RATE, FIXED_TOTAL)
- `interestRate` (Interest Rate %)
- `fixedTotal` (Fixed Total Price)
- `graceDays` (Grace Period Days)
- `firstDueDate` (First Installment Date)

### Fields Removed from Operator UI:
- Separate `basePrice`, `discountAmount`, and `agreedPrice` (replaced with **ONE** field: `Software Price`).
- Redundant `gstin`, `billingAddress`, `notes`, `customerEmail` from the primary license modal (kept optional or auto-managed).
- Redundant `paymentMode` dropdown (replaced by a clean `☐ EMI` checkbox).
- Unnecessary `interestType`, `fixedTotal`, `graceDays` inputs for standard sales (defaults are handled server-authoritatively).
- Separate "Create Draft", "Sign", and "Push" buttons (replaced with single `[ SIGN & PUBLISH ]`).

### Existing Backend Fields Kept Internal:
- `agreedPrice` $\leftarrow$ mapped directly from `Software Price`.
- `basePrice` $\leftarrow$ mapped directly from `Software Price` (discount = 0 by default).
- `paymentMode` $\leftarrow$ `INSTALLMENT` if EMI checked, else `FULL_PAYMENT`.
- `graceDays` $\leftarrow$ default 7 days.
- `interestType` $\leftarrow$ `NO_COST` or `FLAT_RATE` based on commercial configuration.
- `firstDueDate` $\leftarrow$ default `LocalDate.now().plusMonths(1)`.

---

## 2. GitHub Publication Flow: Asynchronous Queue vs. Instant Direct Publication

### Current GitHub Flow:
- `SaleService.createSale(...)` created a `SyncQueueEntry` in status `PENDING`.
- A background worker (`SyncQueueWorkerService` scheduled every 5s) processed the queue.
- **Problem for Operator:** Operator had no immediate confirmation if the license was actually accepted by GitHub; they saw "Sale created" without knowing if the GitHub commit succeeded or failed due to PAT or permissions.

### Simplified Direct Flow:
- `SaleService` / `LicenseService`: On `[ SIGN & PUBLISH ]`, the backend immediately invokes `gitHubSyncService.publishFile(...)` synchronously to commit directly to GitHub, capturing the commit SHA and returning HTTP 200 with the commit details.
- The `SyncQueueEntry` is still logged for audit and retry fallback, but the operator receives **instant real-time confirmation** and a commit SHA link.
- If GitHub publication fails, the backend returns clear actionable feedback (e.g. `HTTP 403: GitHub token lacks write permissions`) and offers a one-click `[ RETRY PUBLICATION ]` button.

---

## 3. Data Protection Flow

### Current DP Implementation:
- Implemented in `DataProtectionService` and `DataProtectionRecord`, but hidden as an auxiliary toggle in Sales.
- No dedicated top-level section to inspect, renew, extend, or revoke active customer backup tokens and subscriptions.

### Simplified DP Implementation:
- **In New License:** Single checkbox `☐ Data Protection`. When checked, displays `Data Protection Tenure` dropdown and `Additional Price ₹____`. Clearly sums `Software Price + DP Price = Total Price`.
- **Top-Level Section:** Dedicated `Data Protection` tab in the main navigation. Displays all active DP subscriptions, Machine ID, Customer Name, Expiry Date, Price, and actions (`Renew`, `Extend`, `Revoke`, `Emergency Recover`).

---

## 4. Broadcasts & Messaging Flow

### Current Implementation:
- Backend has `AnnouncementService`, `AnnouncementController`, and `AnnouncementRecord`.
- Was buried inside auxiliary tabs without high visibility of delivery/publication state.

### Simplified Implementation:
- Elevated to a dedicated top-level section: `Broadcasts & Messaging`.
- Live tables for Customer Announcements & In-App Banners with status pills (`DRAFT`, `PUBLISHED`, `FAILED`), publication timestamps, and direct GitHub publication to the announcements repository.

---

## 5. Emergency Recovery Functionality

### Analysis of Legacy `SoftwareManagementTool.html` Recovery:
- In `SoftwareManagementTool.html`, Tab 6 was "🚑 Emergency In-Browser Data Recovery".
- Allowed an operator to provide a `Machine ID` and optional `License ID`.
- Fetched `backups/{machineId}.enc` from the Data Protection GitHub vault.
- Header byte was checked (`0x01`), IV extracted (12 bytes), and key derived: $\text{SHA-256}(\text{licenseId.trim()})$.
- Decrypted AES-256-GCM (128-bit tag) in-browser and exported the recovered JSON database (`RupeeCRM-Recovered-{machineId}.json`).

### Server-Based Implementation:
- Provide both **Server-Authoritative Recovery** (`POST /api/dp/recover`) and **In-Browser Decrypt** capability with full audit logging in `mgmt_audit_log`.
- Operator can view decrypted database JSON or download the recovered JSON payload directly.
- Confirmation modal explains the recovery scope and registers an audit trail event.

---

## 6. Functional Comparison: Legacy HTML Tool vs. Current Server Application

| Feature / Workflow | Old HTML Tool (`SoftwareManagementTool.html`) | Current Server Application (`licensemanagement`) | Simplified Target State |
| :--- | :--- | :--- | :--- |
| **Architecture** | Client-only browser JS, direct GitHub API calls | Spring Boot 2.7, H2 Database, Flyway, Ed25519 signing | Preserve Spring Boot & H2, simplify UI interactions |
| **New License** | 10+ inputs, client-side signing | Unified Sale with complex financial/accounting inputs | **Single simple 6-field form, 1 price, optional DP/EMI toggles** |
| **GitHub Commit** | Direct client commit via PAT | Background sync queue worker (opaque to user) | **Direct synchronous commit on Sign & Publish with instant SHA** |
| **EMI Section** | Basic schedule display | Embedded in Sales sub-tabs | **Dedicated top-level EMI section with fast [MARK PAID] and [FULL SETTLE]** |
| **Data Protection** | Separate Add-ons tab | Embedded in Sale Item | **Dedicated top-level Data Protection section + simple checkbox on Sale** |
| **Broadcasts** | Announcements & Messages tabs | Sub-features | **Dedicated top-level Broadcasts & Messaging section** |
| **Emergency Recovery** | In-browser AES-256-GCM decrypt | Missing | **Restored with backend-authoritative recovery + audit logging** |
| **Diagnostics** | Basic test button | Basic test button | **Independent diagnostics for Registry, DP, and Announcements with HTTP status** |

---

## 7. Required Backend and Frontend Enhancements

1. **Backend (`SaleService` / `LicenseService` / `SyncQueueWorkerService`):**
   - Add direct synchronous publication support on `createSale` / `signLicense` returning `PublishResult` (commit SHA, status, error).
   - Add `fullSettlement(scheduleId, paymentMethod, referenceNumber)` in `PaymentService` / `EmiScheduleService`.
   - Add `EmergencyRecoveryService` / `DataProtectionController.recoverBackup(...)` for decrypting vault backups with AES-256-GCM.
2. **Frontend (`index.html`, `app.js`, `style.css`):**
   - 9 Top-Level Navigation Sections:
     1. Dashboard
     2. Sales & Customers
     3. Licensing & EMI
     4. Salesmen & Commissions
     5. EMI (Fast Collections)
     6. Broadcasts & Messaging
     7. Data Protection
     8. Reports & Statements
     9. System & Settings
   - Ultra-clean "New License" modal (Firm, MID, Mobile, Customer, Tier, Salesman, 1 Price, DP toggle, EMI toggle, instant preview).
   - Instant "Sign & Publish" button with live progress and commit SHA toast.
   - Quick EMI `[ MARK PAID ]` modal and `[ FULL SETTLEMENT ]` modal.
   - Emergency Recovery workbench.
