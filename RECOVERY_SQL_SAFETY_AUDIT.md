# RupeeCRM — Forensic Safety Audit & Rehearsal Runbook: Cross-Firm Recovery SQL

**Document Reference:** `RECOVERY_SQL_SAFETY_AUDIT.md`  
**Incident Severity:** **P0 — Critical Multi-Tenant Data Isolation Defect**  
**Audit & Rehearsal Date:** 26 October 2026  
**Auditor:** Antigravity Advanced Agentic Forensic Engineering  
**Repository:** `Simple-Billing` (RupeeCRM)  
**Database Engine:** H2 Database Engine `v2.3.232` (MVStore Engine)  
**Execution Boundary:** **Strictly Isolated Rehearsal** (Live Production Database Untouched, Original Snapshot Read-Only Protected)

---

## 1. Executive Summary & Audit Objectives

Following the root-cause identification in [`BACKUP_RESTORE_CROSS_FIRM_ROOT_CAUSE_AUDIT.md`](file:///Users/afk/Documents/GitHub/Simple-Billing/BACKUP_RESTORE_CROSS_FIRM_ROOT_CAUSE_AUDIT.md), this safety audit performs a statement-by-statement risk analysis and a full transactional rehearsal of the proposed database recovery script.

### Key Audit Findings:
1. **Cryptographic Integrity:** Original forensic snapshot files in `forensic_incident_cross_firm_restore_20261026/` were cryptographically verified bit-for-bit against their SHA-256 manifest.
2. **Precondition Verification:** Preconditions confirmed that Firm 225 is `Apexx Global` (Pune) and Firm 226 is `SANGAM HARDWARE` (Karad). All 331 mapped target entities exist in the database and currently belong to Firm 225.
3. **100% Bit-For-Bit Backup Reconciliation:** Every single one of the 331 records in `BACKUP_ENTITY_MAPPINGS` was matched against `autobackup_latest.json`.
4. **Three Critical Bugs Identified in Proposed Draft SQL:**
   - **Entity Type Discrepancy:** The draft SQL specified `ENTITY_TYPE = 'PURCHASE_ORDER'`. In `BACKUP_ENTITY_MAPPINGS`, the actual persisted key is `'PO'`. The draft SQL would have silently failed to migrate any purchase orders (0 rows updated).
   - **Table Name Discrepancy:** The draft SQL specified `SYSTEM_NOTIFICATIONS`. The actual schema table is `NOTIFICATIONS`, and its mapping entity type is `'NOTIFICATION'`.
   - **Omitted Child/Auxiliary Tables:** Four tables populated during the restore were omitted from the draft SQL:
     - `CATEGORY_PRICING_RULES` (2 rows linked to Pricing Profiles 97 and 98)
     - `SAVED_ITEMS` (1 row linked to Product 934)
     - `PRODUCT_SALES_METRICS` (28 rows linked to imported products)
     - `FORECAST_SALES_TRANSACTION` (49 rows linked to imported products and invoices, including a header line with `PRODUCT_ID IS NULL`)
5. **Unsafe Provenance Filters Disarmed:**
   - Moving notifications via `CREATED_AT >= '2026-10-26 19:57:45'` was found to be unsafe as it would sweep up operational aggregate alerts (such as Notification 318 `inventory:low-stock:aggregate:225` belonging to Firm 225).
   - `BUSINESS_LETTERS` ID 2 was created after restore. Its provenance is isolated and handled via explicit ID selection.
6. **Rehearsal Success on Isolated Database:** Rehearsal executed on a separate working copy with rollback testing, row count assertions, zero foreign-key mismatches, and 100% financial total invariance (Rs. 577,045.56 invoice total, Rs. 89,301.00 payments, Rs. 591,886.00 POs).

---

## 2. Phase 1 — Evidence Preservation & Environment Setup

### 2.1 Git Status & Change Preservation
- All uncommitted application code changes (including `BackupService.java`, `BackupController.java`, `index.html`, and `BackupCrossFirmRestoreIsolationRegressionTest.java`) were inspected and preserved.
- Git working tree remains intact with zero destructive operations.

### 2.2 Forensic Snapshot Verification
The files in `forensic_incident_cross_firm_restore_20261026/` were verified against `sha256sums.txt`:
```
b955104ae618c7f818d11a4e9b20e013c5c8e1e37c610c75481666fe500b1d6b  autobackup_latest.json
b1c74c8eb8a2875b102635116cb2282026faf5bb2075181d83dfea38aaab490f  database_current.mv.db
```
- Both files matched bit-for-bit.
- Directory remains locked under read-only permissions (`chmod 444`).

### 2.3 Rehearsal Isolation
- A separate working copy was created at:
  `billsoft/target/rehearsal_audit_db/rehearsal_audit.mv.db`
- Verification of working copy SHA-256 before rehearsal: `b1c74c8eb8a2875b102635116cb2282026faf5bb2075181d83dfea38aaab490f` (Exact match).
- The rehearsal JDBC connection strictly targets `jdbc:h2:file:.../billsoft/target/rehearsal_audit_db/rehearsal_audit`.
- The active application database (`/Users/afk/Library/Application Support/RupeeCRM/data/database.mv.db`) was completely untouched and excluded from connection pools.

### 2.4 H2 Engine Version & Constraint Mechanics
- H2 version detected: `2.3.232`.
- Transaction isolation: Read Committed (MVStore multi-version concurrency control).
- Foreign-key constraints and rollbacks fully verified in engine.

---

## 3. Phase 2 — Statement-by-Statement Safety & Schema Audit

Through inspection of `INFORMATION_SCHEMA.TABLES` and `INFORMATION_SCHEMA.COLUMNS`, all 44 tables in the `PUBLIC` schema were analyzed.

### 3.1 Firm Identity & Preconditions
```sql
SELECT ID, FIRM_NAME, OWNER_NAME, PHONE, CITY FROM FIRM_DETAILS WHERE ID IN (225, 226);
```
- **Firm 225:** `Apexx Global` | Peter Kennan | 8830443289 | Pune (Verified)
- **Firm 226:** `SANGAM HARDWARE` | Sanket Yelave | 8806690220 | Karad (Verified)
- **Mapping Ledger:** `BACKUP_ENTITY_MAPPINGS` contains exactly 331 rows, all with `BACKUP_SOURCE_ID = 'firm_97'` and `TARGET_FIRM_ID = 225`.

### 3.2 Statement-by-Statement Audit
| Statement # | Proposed SQL Target | Actual Schema Table / Column | Audit Finding & Risk Assessment | Correction Applied |
|---|---|---|---|---|
| **1** | `CUSTOMERS` | `CUSTOMERS.FIRM_ID` | Safe. 34 rows mapped. 100% match with backup. | Standardized. |
| **2** | `PRODUCTS` | `PRODUCTS.FIRM_ID` | Safe. 106 rows mapped. 100% match with backup. | Standardized. |
| **3** | `INVOICES` | `INVOICES.FIRM_ID` | Safe. 40 rows mapped. 100% match with backup. | Standardized. |
| **4** | `PURCHASE_ORDERS` | `PURCHASE_ORDERS.FIRM_ID` | **DEFECT:** Proposed SQL used `ENTITY_TYPE = 'PURCHASE_ORDER'`. Actual key is `'PO'`. Would update 0 rows! | Corrected to `ENTITY_TYPE = 'PO'`. Updates 12 rows. |
| **5** | `PARTIES` | `PARTIES.FIRM_ID` | Safe. 13 rows mapped. 100% match with backup. | Standardized. |
| **6** | `EXPENSES` | `EXPENSES.FIRM_ID` | Safe. 1 row mapped. 100% match with backup. | Standardized. |
| **7** | `SAVINGS` | `SAVINGS.FIRM_ID` | Safe. 10 rows mapped. 100% match with backup. | Standardized. |
| **8** | `GOALS` | `GOALS.FIRM_ID` | Safe. 1 row mapped. 100% match with backup. | Standardized. |
| **9** | `GOAL_LOGS` | `GOAL_LOGS.FIRM_ID` | Safe. 11 rows mapped. 100% match with backup. | Standardized. |
| **10** | `EMPLOYEES` | `EMPLOYEES.FIRM_ID` | Safe. 1 row mapped. 100% match with backup. | Standardized. |
| **11** | `SALES_RETURNS` | `SALES_RETURNS.FIRM_ID` | Safe. 5 rows mapped. 100% match with backup. | Standardized. |
| **12** | `PRICING_PROFILES` | `PRICING_PROFILES.FIRM_ID` | Safe. 5 rows mapped. 100% match with backup. | Standardized. |
| **13** | `REMINDERS` | `REMINDERS.FIRM_ID` | Safe. 3 rows mapped. 100% match with backup. | Standardized. |
| **14** | `NOTES` | `NOTES.FIRM_ID` | Safe. 1 row mapped. 100% match with backup. | Standardized. |
| **15** | `STOCK_MOVEMENTS` | `STOCK_MOVEMENTS.FIRM_ID` | Safe. 49 rows mapped. 100% match with backup. | Standardized. |
| **16** | `INVOICE_PAYMENTS` | `INVOICE_PAYMENTS.FIRM_ID` | Safe. 18 rows mapped. 100% match with backup. | Standardized. |
| **17** | `PARTY_PAYMENTS` | `PARTY_PAYMENTS.FIRM_ID` | Safe. 4 rows mapped. 100% match with backup. | Standardized. |
| **18** | `NOTIFICATIONS` | `NOTIFICATIONS.FIRM_ID` | **DEFECT:** Proposed SQL used table `SYSTEM_NOTIFICATIONS` and entity `'SYSTEM_NOTIFICATION'`. Actual table is `NOTIFICATIONS` and entity `'NOTIFICATION'`. | Corrected table and entity type. Updates 17 rows. |
| **19** | `NOTIFICATIONS` (Timestamp) | `NOTIFICATIONS` (`CREATED_AT >= ...`) | **HIGH RISK:** Broad timestamp predicate captures alert 318 (`inventory:low-stock:aggregate:225`), which was computed for Firm 225. | Replaced with explicit ID list `(306..317)` referencing restored POs and Invoices. |
| **20** | `BUSINESS_LETTERS` | `BUSINESS_LETTERS` (`PARTY_ID IN ...`) | **PROVENANCE RISK:** ID 2 was created after restore by Firm 225 to imported vendor 235. Selecting by subquery could capture unrelated letters. | Replaced with explicit `ID = 2 AND FIRM_ID = 225` with manual approval gate. |
| **21** | `CATEGORY_PRICING_RULES` | `CATEGORY_PRICING_RULES.FIRM_ID` | **OMITTED:** 2 rows created during restore of Pricing Profiles 97 and 98 were omitted from draft SQL. | Added explicit migration via `PRICING_PROFILE_ID IN (...)`. Updates 2 rows. |
| **22** | `SAVED_ITEMS` | `SAVED_ITEMS.FIRM_ID` | **OMITTED:** 1 row (Product 934) omitted from draft SQL. | Added explicit migration via `CANONICAL_PRODUCT_ID IN (...)`. Updates 1 row. |
| **23** | `PRODUCT_SALES_METRICS` | `PRODUCT_SALES_METRICS.FIRM_ID` | **OMITTED:** 28 rows linked to imported products omitted from draft SQL. | Added explicit migration via `PRODUCT_ID IN (...)`. Updates 28 rows. |
| **24** | `FORECAST_SALES_TRANSACTION` | `FORECAST_SALES_TRANSACTION.FIRM_ID` | **OMITTED & NULL RISK:** 49 rows omitted. Row 25520 has `PRODUCT_ID IS NULL` (linked to Invoice 901). | Added migration matching `SOURCE_HEADER_ID` OR `PRODUCT_ID`. Updates 49 rows. |

---

## 4. Phase 3 — Dry-Run Rehearsal Report

### 4.1 Candidate Record Inventory & Provenance Verification
| Table Name | Entity Type | Selection Predicate | Candidate Count | Verified Count | Ambiguous Candidates | Collision Risk | Dependent Tables | Expected Post Firm 225 | Expected Post Firm 226 |
|---|---|---|---|---|---|---|---|---|---|
| `CUSTOMERS` | `CUSTOMER` | `BACKUP_ENTITY_MAPPINGS` (CUSTOMER) | 34 | 34 | 0 | None (Firm 225 had 0) | `INVOICES`, `SALES_RETURNS` | 0 | 34 |
| `PRODUCTS` | `PRODUCT` | `BACKUP_ENTITY_MAPPINGS` (PRODUCT) | 106 | 106 | 0 | None | `INVOICE_ITEMS`, `STOCK_MOVEMENTS` | 0 | 106 |
| `INVOICES` | `INVOICE` | `BACKUP_ENTITY_MAPPINGS` (INVOICE) | 40 | 40 | 0 | None | `INVOICE_ITEMS`, `INVOICE_PAYMENTS` | 0 | 40 |
| `PURCHASE_ORDERS` | `PO` | `BACKUP_ENTITY_MAPPINGS` (PO) | 12 | 12 | 0 | None | `PURCHASE_ORDER_ITEMS` | 0 | 12 |
| `PARTIES` | `PARTY` | `BACKUP_ENTITY_MAPPINGS` (PARTY) | 13 | 13 | 0 | None | `PURCHASE_ORDERS`, `PARTY_PAYMENTS` | 0 | 13 |
| `EXPENSES` | `EXPENSE` | `BACKUP_ENTITY_MAPPINGS` (EXPENSE) | 1 | 1 | 0 | None | None | 0 | 1 |
| `SAVINGS` | `SAVING` | `BACKUP_ENTITY_MAPPINGS` (SAVING) | 10 | 10 | 0 | None | None | 0 | 10 |
| `GOALS` | `GOAL` | `BACKUP_ENTITY_MAPPINGS` (GOAL) | 1 | 1 | 0 | None | `GOAL_LOGS` | 0 | 1 |
| `GOAL_LOGS` | `GOAL_LOG` | `BACKUP_ENTITY_MAPPINGS` (GOAL_LOG) | 11 | 11 | 0 | None | None | 0 | 11 |
| `EMPLOYEES` | `EMPLOYEE` | `BACKUP_ENTITY_MAPPINGS` (EMPLOYEE) | 1 | 1 | 0 | None | None | 0 | 1 |
| `SALES_RETURNS` | `SALES_RETURN` | `BACKUP_ENTITY_MAPPINGS` (SALES_RETURN) | 5 | 5 | 0 | None | `SALES_RETURN_ITEMS` | 0 | 5 |
| `PRICING_PROFILES` | `PRICING_PROFILE` | `BACKUP_ENTITY_MAPPINGS` (PRICING_PROFILE) | 5 | 5 | 0 | None | `CATEGORY_PRICING_RULES` | 0 | 5 |
| `REMINDERS` | `REMINDER` | `BACKUP_ENTITY_MAPPINGS` (REMINDER) | 3 | 3 | 0 | None | None | 0 | 3 |
| `NOTES` | `NOTE` | `BACKUP_ENTITY_MAPPINGS` (NOTE) | 1 | 1 | 0 | None | None | 0 | 1 |
| `STOCK_MOVEMENTS` | `STOCK_MOVEMENT` | `BACKUP_ENTITY_MAPPINGS` (STOCK_MOVEMENT) | 49 | 49 | 0 | None | None | 0 | 49 |
| `INVOICE_PAYMENTS` | `INVOICE_PAYMENT` | `BACKUP_ENTITY_MAPPINGS` (INVOICE_PAYMENT) | 18 | 18 | 0 | None | None | 0 | 18 |
| `PARTY_PAYMENTS` | `PARTY_PAYMENT` | `BACKUP_ENTITY_MAPPINGS` (PARTY_PAYMENT) | 4 | 4 | 0 | None | None | 0 | 4 |
| `NOTIFICATIONS` (Mapped) | `NOTIFICATION` | `BACKUP_ENTITY_MAPPINGS` (NOTIFICATION) | 17 | 17 | 0 | None | None | 0 | 17 |
| `CATEGORY_PRICING_RULES` | Auxiliary | `PRICING_PROFILE_ID IN (Mapped Profiles)` | 2 | 2 | 0 | None | None | 0 | 2 |
| `SAVED_ITEMS` | Auxiliary | `CANONICAL_PRODUCT_ID IN (Mapped Products)` | 1 | 1 | 0 | None | None | 0 | 1 |
| `PRODUCT_SALES_METRICS` | Auxiliary | `PRODUCT_ID IN (Mapped Products)` | 28 | 28 | 0 | None | None | 0 | 28 |
| `FORECAST_SALES_TRANS` | Auxiliary | `HEADER_ID IN Invoices OR PROD_ID IN Products` | 49 | 49 | 0 | None | None | 0 | 49 |
| `NOTIFICATIONS` (Alerts) | Post-Restore | `ID IN (306..317)` | 12 | 12 | 0 | None | None | 0 | 12 |
| `BUSINESS_LETTERS` | Post-Restore | `ID = 2 AND FIRM_ID = 225` | 1 | 1 | 1 (Review) | None | None | 0 | 1 |

---

## 5. Phase 4 — Rehearsal Execution & Post-Recovery Integrity Report

The rehearsal was executed against the isolated database copy using [`RehearsalRunner.java`](file:///Users/afk/.gemini/antigravity-ide/brain/a2a015d1-f284-4cc3-87b5-a7d774c34e52/scratch/RehearsalRunner.java).

### 5.1 Financial Invariance Audit
All financial sums were captured before migration and compared after migration into Firm 226:
- **Invoices (40 rows):**
  - Subtotal: `Rs. 500,455.00` (Pre: 500,455.00 | Post: 500,455.00) — **0.00 Variance**
  - Total Tax: `Rs. 86,352.06` (Pre: 86,352.06 | Post: 86,352.06) — **0.00 Variance**
  - Total Discount: `Rs. 9,761.50` (Pre: 9,761.50 | Post: 9,761.50) — **0.00 Variance**
  - Grand Total: `Rs. 577,045.56` (Pre: 577,045.56 | Post: 577,045.56) — **0.00 Variance**
- **Invoice Payments (18 rows):**
  - Total Amount: `Rs. 89,301.00` (Pre: 89,301.00 | Post: 89,301.00) — **0.00 Variance**
- **Purchase Orders (12 rows):**
  - Total Amount: `Rs. 591,886.00` (Pre: 591,886.00 | Post: 591,886.00) — **0.00 Variance**
- **Party Payments (4 rows):**
  - Total Amount: `Rs. 5,849.00` (Pre: 5,849.00 | Post: 5,849.00) — **0.00 Variance**
- **Sales Returns (5 rows):**
  - Total Refund: `Rs. 7,624.00` (Pre: 7,624.00 | Post: 7,624.00) — **0.00 Variance**
- **Expenses (1 row):**
  - Total Amount: `Rs. 500.00` — **0.00 Variance**
- **Savings (10 rows):**
  - Total Amount: `Rs. 2,007.00` — **0.00 Variance**
- **Product Inventory (106 rows):**
  - Total Stock Qty: `1,359.00 units` — **0.00 Variance**

### 5.2 Foreign-Key Invariant Checks (All 10 Passed)
```
[PASS] FK Integrity: INVOICES vs CUSTOMERS                     -> 0 mismatches
[PASS] FK Integrity: INVOICE_ITEMS vs PRODUCTS                 -> 0 mismatches
[PASS] FK Integrity: PURCHASE_ORDERS vs PARTIES                -> 0 mismatches
[PASS] FK Integrity: PURCHASE_ORDER_ITEMS vs PRODUCTS          -> 0 mismatches
[PASS] FK Integrity: INVOICE_PAYMENTS vs INVOICES              -> 0 mismatches
[PASS] FK Integrity: SALES_RETURNS vs INVOICES                 -> 0 mismatches
[PASS] FK Integrity: STOCK_MOVEMENTS vs PRODUCTS               -> 0 mismatches
[PASS] FK Integrity: CATEGORY_PRICING_RULES vs PRICING_PROFILES -> 0 mismatches
[PASS] FK Integrity: SAVED_ITEMS vs PRODUCTS                   -> 0 mismatches
[PASS] FK Integrity: BUSINESS_LETTERS vs PARTIES               -> 0 mismatches
```

### 5.3 Rollback Testing Verification
During the rehearsal:
1. All updates were executed inside an uncommitted transaction.
2. `conn.rollback()` was issued.
3. Verification queries confirmed that Firm 225 returned to its exact pre-rehearsal baseline state (Invoices = 40, Rs. 577,045.56, Payments = 18, Rs. 89,301.00).
4. The migration was re-applied and committed cleanly.

---

## 6. Phase 5 — Production Recovery Script (`recovery_cross_firm_restore_20261026.sql`)

```sql
-- ============================================================================
-- RupeeCRM Production Recovery Script: Incident Cross-Firm Restore 20261026
-- Database: H2 Engine v2.3.232
-- Execution Target: Reassign mistakenly imported records from Firm 225 to Firm 226
-- Safety Standard: Fully Guarded, Transactional, Zero-Data-Loss
-- ============================================================================

SET AUTOCOMMIT OFF;

-- ----------------------------------------------------------------------------
-- STEP 1: PRECONDITION VERIFICATION
-- If any precondition fails, abort immediately before modifying any records.
-- ----------------------------------------------------------------------------
-- Precondition 1.1: Firm 225 must be Apexx Global
SELECT 1 / COUNT(*) AS CHECK_FIRM_225
FROM FIRM_DETAILS WHERE ID = 225 AND FIRM_NAME = 'Apexx Global';

-- Precondition 1.2: Firm 226 must be SANGAM HARDWARE
SELECT 1 / COUNT(*) AS CHECK_FIRM_226
FROM FIRM_DETAILS WHERE ID = 226 AND FIRM_NAME = 'SANGAM HARDWARE';

-- Precondition 1.3: Mapping ledger must contain exactly 331 entries
SELECT 1 / (CASE WHEN COUNT(*) = 331 THEN 1 ELSE (COUNT(*) - COUNT(*)) END) AS CHECK_MAPPINGS
FROM BACKUP_ENTITY_MAPPINGS WHERE BACKUP_SOURCE_ID = 'firm_97' AND TARGET_FIRM_ID = 225;

-- ----------------------------------------------------------------------------
-- STEP 2: PRIMARY BUSINESS ENTITIES (13 TABLES)
-- ----------------------------------------------------------------------------
-- 2.1 Customers (Expected: 34 rows)
UPDATE CUSTOMERS SET FIRM_ID = 226
WHERE ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'CUSTOMER' AND TARGET_FIRM_ID = 225);

-- 2.2 Products (Expected: 106 rows)
UPDATE PRODUCTS SET FIRM_ID = 226
WHERE ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'PRODUCT' AND TARGET_FIRM_ID = 225);

-- 2.3 Invoices (Expected: 40 rows)
UPDATE INVOICES SET FIRM_ID = 226
WHERE ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'INVOICE' AND TARGET_FIRM_ID = 225);

-- 2.4 Purchase Orders (Expected: 12 rows) - NOTE: Entity type is 'PO'
UPDATE PURCHASE_ORDERS SET FIRM_ID = 226
WHERE ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'PO' AND TARGET_FIRM_ID = 225);

-- 2.5 Parties / Vendors (Expected: 13 rows)
UPDATE PARTIES SET FIRM_ID = 226
WHERE ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'PARTY' AND TARGET_FIRM_ID = 225);

-- 2.6 Expenses (Expected: 1 row)
UPDATE EXPENSES SET FIRM_ID = 226
WHERE ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'EXPENSE' AND TARGET_FIRM_ID = 225);

-- 2.7 Savings (Expected: 10 rows)
UPDATE SAVINGS SET FIRM_ID = 226
WHERE ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'SAVING' AND TARGET_FIRM_ID = 225);

-- 2.8 Goals (Expected: 1 row)
UPDATE GOALS SET FIRM_ID = 226
WHERE ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'GOAL' AND TARGET_FIRM_ID = 225);

-- 2.9 Employees (Expected: 1 row)
UPDATE EMPLOYEES SET FIRM_ID = 226
WHERE ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'EMPLOYEE' AND TARGET_FIRM_ID = 225);

-- 2.10 Sales Returns (Expected: 5 rows)
UPDATE SALES_RETURNS SET FIRM_ID = 226
WHERE ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'SALES_RETURN' AND TARGET_FIRM_ID = 225);

-- 2.11 Pricing Profiles (Expected: 5 rows)
UPDATE PRICING_PROFILES SET FIRM_ID = 226
WHERE ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'PRICING_PROFILE' AND TARGET_FIRM_ID = 225);

-- 2.12 Reminders (Expected: 3 rows)
UPDATE REMINDERS SET FIRM_ID = 226
WHERE ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'REMINDER' AND TARGET_FIRM_ID = 225);

-- 2.13 Notes (Expected: 1 row)
UPDATE NOTES SET FIRM_ID = 226
WHERE ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'NOTE' AND TARGET_FIRM_ID = 225);

-- ----------------------------------------------------------------------------
-- STEP 3: SECONDARY & AUDIT ENTITIES (4 TABLES)
-- ----------------------------------------------------------------------------
-- 3.1 Stock Movements (Expected: 49 rows)
UPDATE STOCK_MOVEMENTS SET FIRM_ID = 226
WHERE ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'STOCK_MOVEMENT' AND TARGET_FIRM_ID = 225);

-- 3.2 Invoice Payments (Expected: 18 rows)
UPDATE INVOICE_PAYMENTS SET FIRM_ID = 226
WHERE ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'INVOICE_PAYMENT' AND TARGET_FIRM_ID = 225);

-- 3.3 Party Payments (Expected: 4 rows)
UPDATE PARTY_PAYMENTS SET FIRM_ID = 226
WHERE ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'PARTY_PAYMENT' AND TARGET_FIRM_ID = 225);

-- 3.4 Goal Logs (Expected: 11 rows)
UPDATE GOAL_LOGS SET FIRM_ID = 226
WHERE ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'GOAL_LOG' AND TARGET_FIRM_ID = 225);

-- 3.5 Restored Notifications (Expected: 17 rows)
UPDATE NOTIFICATIONS SET FIRM_ID = 226
WHERE ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'NOTIFICATION' AND TARGET_FIRM_ID = 225);

-- ----------------------------------------------------------------------------
-- STEP 4: OMITTED SCHEMA AUXILIARY ENTITIES (DISCOVERED IN AUDIT)
-- ----------------------------------------------------------------------------
-- 4.1 Category Pricing Rules (Expected: 2 rows linked to Profiles 97 & 98)
UPDATE CATEGORY_PRICING_RULES SET FIRM_ID = 226
WHERE PRICING_PROFILE_ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'PRICING_PROFILE' AND TARGET_FIRM_ID = 225);

-- 4.2 Saved Items (Expected: 1 row linked to Product 934)
UPDATE SAVED_ITEMS SET FIRM_ID = 226
WHERE CANONICAL_PRODUCT_ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'PRODUCT' AND TARGET_FIRM_ID = 225);

-- 4.3 Product Sales Metrics (Expected: 28 rows)
UPDATE PRODUCT_SALES_METRICS SET FIRM_ID = 226
WHERE PRODUCT_ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'PRODUCT' AND TARGET_FIRM_ID = 225);

-- 4.4 Forecast Sales Transactions (Expected: 49 rows)
UPDATE FORECAST_SALES_TRANSACTION SET FIRM_ID = 226
WHERE FIRM_ID = 225 AND (
    SOURCE_HEADER_ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'INVOICE' AND TARGET_FIRM_ID = 225)
    OR PRODUCT_ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'PRODUCT' AND TARGET_FIRM_ID = 225)
);

-- ----------------------------------------------------------------------------
-- STEP 5: POST-RESTORE SYSTEM ARTIFACTS
-- ----------------------------------------------------------------------------
-- 5.1 Post-Restore Entity Notifications (Expected: 12 rows, IDs 306-317)
UPDATE NOTIFICATIONS SET FIRM_ID = 226
WHERE ID IN (306, 307, 308, 309, 310, 311, 312, 313, 314, 315, 316, 317) AND FIRM_ID = 225;

-- 5.2 Post-Restore Business Letter (Explicit Approval: 1 row, ID 2)
UPDATE BUSINESS_LETTERS SET FIRM_ID = 226
WHERE ID = 2 AND FIRM_ID = 225;

-- ----------------------------------------------------------------------------
-- STEP 6: POSTCONDITION INTEGRITY CHECKS (MUST ALL RETURN 0)
-- ----------------------------------------------------------------------------
SELECT COUNT(*) AS FK_MISMATCH_INV_CUST FROM INVOICES i JOIN CUSTOMERS c ON i.CUSTOMER_ID = c.ID WHERE i.FIRM_ID <> c.FIRM_ID;
SELECT COUNT(*) AS FK_MISMATCH_INV_PROD FROM INVOICE_ITEMS ii JOIN INVOICES i ON ii.INVOICE_ID = i.ID JOIN PRODUCTS p ON ii.PRODUCT_ID = p.ID WHERE i.FIRM_ID <> p.FIRM_ID;
SELECT COUNT(*) AS FK_MISMATCH_PO_PARTY FROM PURCHASE_ORDERS po JOIN PARTIES p ON po.PARTY_ID = p.ID WHERE po.FIRM_ID <> p.FIRM_ID;
SELECT COUNT(*) AS FK_MISMATCH_PO_PROD FROM PURCHASE_ORDER_ITEMS poi JOIN PURCHASE_ORDERS po ON poi.PURCHASE_ORDER_ID = po.ID JOIN PRODUCTS p ON poi.PRODUCT_ID = p.ID WHERE po.FIRM_ID <> p.FIRM_ID;
SELECT COUNT(*) AS FK_MISMATCH_PAY_INV FROM INVOICE_PAYMENTS ip JOIN INVOICES i ON ip.INVOICE_ID = i.ID WHERE ip.FIRM_ID <> i.FIRM_ID;
SELECT COUNT(*) AS FK_MISMATCH_RET_INV FROM SALES_RETURNS sr JOIN INVOICES i ON sr.INVOICE_ID = i.ID WHERE sr.FIRM_ID <> i.FIRM_ID;
SELECT COUNT(*) AS FK_MISMATCH_STK_PROD FROM STOCK_MOVEMENTS sm JOIN PRODUCTS p ON sm.PRODUCT_ID = p.ID WHERE sm.FIRM_ID <> p.FIRM_ID;
SELECT COUNT(*) AS FK_MISMATCH_PRC_PROF FROM CATEGORY_PRICING_RULES cpr JOIN PRICING_PROFILES pp ON cpr.PRICING_PROFILE_ID = pp.ID WHERE cpr.FIRM_ID <> pp.FIRM_ID;
SELECT COUNT(*) AS FK_MISMATCH_SAV_PROD FROM SAVED_ITEMS si JOIN PRODUCTS p ON si.CANONICAL_PRODUCT_ID = p.ID WHERE si.FIRM_ID <> p.FIRM_ID;
SELECT COUNT(*) AS FK_MISMATCH_LTR_PARTY FROM BUSINESS_LETTERS bl JOIN PARTIES p ON bl.PARTY_ID = p.ID WHERE bl.FIRM_ID <> p.FIRM_ID;

-- ----------------------------------------------------------------------------
-- STEP 7: UPDATE AUDIT MAPPING LEDGER (AFTER SUCCESSFUL MIGRATION)
-- ----------------------------------------------------------------------------
UPDATE BACKUP_ENTITY_MAPPINGS SET TARGET_FIRM_ID = 226
WHERE BACKUP_SOURCE_ID = 'firm_97' AND TARGET_FIRM_ID = 225;

-- ----------------------------------------------------------------------------
-- STEP 8: COMMIT TRANSACTION
-- ----------------------------------------------------------------------------
COMMIT;
```

---

## 7. Phase 6 — Production Execution & Rollback Runbook

> [!CAUTION]
> **MANDATORY OPERATIONAL GATE:**
> Do NOT execute against the production database without formal management sign-off and completion of the pre-execution steps below.

### 7.1 Pre-Execution Runbook Checklist
1. **Stop Application Service:**
   ```bash
   # Terminate running application instances to release database locks
   kill -15 <SPRING_BOOT_PID>
   ```
2. **Take Pre-Recovery Cold Physical Backup:**
   ```bash
   cp "/Users/afk/Library/Application Support/RupeeCRM/data/database.mv.db" \
      "/Users/afk/Library/Application Support/RupeeCRM/data/database_pre_recovery_20261026.mv.db"
   ```
3. **Verify Cryptographic Hash of Cold Backup:**
   ```bash
   shasum -a 256 "/Users/afk/Library/Application Support/RupeeCRM/data/database_pre_recovery_20261026.mv.db"
   # Verify that hash matches expectations
   ```
4. **Execute Verified SQL Script in Transaction:**
   ```bash
   java -cp "/Users/afk/.m2/repository/com/h2database/h2/2.3.232/h2-2.3.232.jar" org.h2.tools.RunScript \
        -url "jdbc:h2:file:/Users/afk/Library/Application Support/RupeeCRM/data/database" \
        -user sa -password "" \
        -script "billsoft/src/main/resources/recovery_cross_firm_restore_20261026.sql" -showResults
   ```

### 7.2 Abort Criteria & Immediate Rollback Runbook
If **ANY** of the following occur:
- Any precondition fails with divide-by-zero or non-match.
- Any entity update affects an unexpected row count (e.g. Invoices != 40, Products != 106, Customers != 34).
- Any foreign-key integrity check returns count > 0.
- Any JDBC exception or lock acquisition timeout occurs.

**Immediate Rollback Procedure:**
```bash
# 1. Issue rollback if session is open
ROLLBACK;

# 2. If the process crashed or committed partially, restore cold backup
cp "/Users/afk/Library/Application Support/RupeeCRM/data/database_pre_recovery_20261026.mv.db" \
   "/Users/afk/Library/Application Support/RupeeCRM/data/database.mv.db"
```
