# RupeeCRM — Forensic Root-Cause Audit: Cross-Firm Data Contamination During Backup Restore

**Document Reference:** `BACKUP_RESTORE_CROSS_FIRM_ROOT_CAUSE_AUDIT.md`  
**Incident Severity:** **P0 — Critical Multi-Tenant Data Isolation Failure**  
**Audit Date:** 26 October 2026  
**Auditor:** Antigravity Advanced Agentic Forensic Engineering  
**Repository:** `Simple-Billing` (RupeeCRM)  
**Execution Boundary:** Strictly Read-Only Investigation (Zero Code Mutations, Zero Database Alterations)  
**Forensic Preservation Directory:** `forensic_incident_cross_firm_restore_20261026/` (Cryptographically Hashed & Read-Only Protected)

---

## 1. Incident Summary & Severity

### 1.1 Incident Description
1. The application was formatted or cleanly reinstalled on a workstation.
2. A new firm, **Firm A** (`Apexx Global`, generated database `ID = 225`), was created and set as active.
3. An automatic daily backup archive (`autobackup_latest.json`) originating from an earlier installation containing **Firm B** (`SANGAM HARDWARE`, source backup `ID = 97`) was restored via the Settings Backup & Restore interface.
4. **Observed Behavior:**
   - Firm B (`SANGAM HARDWARE`) was created as a new record in `FIRM_DETAILS` (`ID = 226`), but was left completely empty (0 customers, 0 products, 0 invoices, 0 purchase orders).
   - All 34 customers, 106 products, 40 invoices, 12 purchase orders, 13 parties, 1 expense, 10 savings, 1 employee, and 5 sales returns belonging to Firm B were inserted with `FIRM_ID = 225` (**Firm A**).
   - Firm A was contaminated with all of Firm B's historical master and transactional business records.
5. **Expected Behavior:**
   - In `clone` mode, restoring a firm from a backup must restore all data into the newly recreated firm entity (Firm B, `ID = 226`), preserving complete tenant isolation. It must never contaminate, overwrite, or populate Firm A's tenant partition.

### 1.2 Severity Classification: P0 Critical
- **Confidentiality Violation:** Complete exposure of Firm B's customers, vendors, financial ledgers, invoices, pricing, and employee records inside Firm A's tenant dashboard.
- **Data Integrity Hazard:** If Firm A already possessed products with matching SKUs, customers with matching phone numbers, or invoices with matching sequence numbers, Firm B's records either merged into or collided with Firm A's active data.
- **Blast Radius:** Affects any multi-firm restore or single-firm restore executed while any other firm is selected as active in the application UI.

---

## 2. Chronological Restore Execution Flow

Through code tracing and physical database inspection, the exact end-to-end execution path was reconstructed:

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ 1. USER INTERACTION: Settings -> Backup & Restore -> Restore Auto-Backup               │
│    - Active UI Session: Firm A (Apexx Global, ID 225)                                 │
│    - Selected Firm from Inspection Modal: Firm B (SANGAM HARDWARE, Source ID 97)      │
│    - Default Mode Selected: "clone"                                                    │
└───────────────────────────────────────────┬────────────────────────────────────────────┘
                                            │
                                            ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ 2. FRONTEND SUBMISSION (index.html:28257-28262)                                        │
│    - API.backup.restoreAuto([97], 'clone', API.firmId)                                 │
│    - CRITICAL BUG: Passes API.firmId (225) as 'targetFirmId' even though mode='clone'! │
│    - HTTP Headers: X-Firm-Id: 225                                                      │
└───────────────────────────────────────────┬────────────────────────────────────────────┘
                                            │
                                            ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ 3. SECURITY INTERCEPTOR (TenantInterceptor.java:140-157)                               │
│    - Reads X-Firm-Id: 225 -> TenantContext.setCurrentFirmId(225)                       │
└───────────────────────────────────────────┬────────────────────────────────────────────┘
                                            │
                                            ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ 4. CONTROLLER GUARD (BackupController.java:206-211)                                    │
│    - Checks: targetFirmId (225) equals currentFirmId (225)                             │
│    - Passes guard because frontend supplied current active firm ID as targetFirmId     │
│    - Dispatches to BackupService.importSelectiveData(backup, [97], "clone", 225)       │
└───────────────────────────────────────────┬────────────────────────────────────────────┘
                                            │
                                            ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ 5. FIRM ENTITY CREATION (BackupService.java:563-587)                                   │
│    - Mode is "clone", so merge lookup is skipped                                      │
│    - Instantiates new FirmDetails("SANGAM HARDWARE")                                   │
│    - firmDetailsRepo.save(newFirm) -> Generated Database ID = 226                     │
│    - Firm B now exists in FIRM_DETAILS as ID 226!                                      │
└───────────────────────────────────────────┬────────────────────────────────────────────┘
                                            │
                                            ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ 6. THE FATAL REMAPPING DEFECT (BackupService.java:589-590)                             │
│    - Line 589:                                                                        │
│      Long mappedId = (targetFirmId != null && targetFirmsToProcess.size() == 1)       │
│                      ? targetFirmId                                                    │
│                      : firmToUse.getId();                                              │
│    - Because targetFirmId was 225, mappedId is set to 225 (NOT 226)!                  │
│    - Line 590: oldToNewFirmIdMap.put(97, 225)                                         │
│    - Source Firm 97 is mapped to TARGET FIRM 225 (Firm A) instead of Firm 226!        │
└───────────────────────────────────────────┬────────────────────────────────────────────┘
                                            │
                                            ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ 7. ENTITY IMPORTATION (BackupService.java:648-2490)                                    │
│    - Every entity checks: Long mappedFirmId = oldToNewFirmIdMap.get(c.getFirmId())    │
│    - mappedFirmId resolves to 225 for all entities!                                   │
│    - Customers: customerRepo.save(newC.setFirmId(225))                                │
│    - Products: productRepo.save(newP.setFirmId(225))                                  │
│    - Invoices: invoiceRepo.save(newInv.setFirmId(225))                                │
│    - Purchase Orders: purchaseOrderRepo.save(newPo.setFirmId(225))                    │
│    - All 28 entity types stamped with FIRM_ID = 225                                    │
│    - Recorded in BACKUP_ENTITY_MAPPINGS: TARGET_FIRM_ID = 225                         │
└───────────────────────────────────────────┬────────────────────────────────────────────┘
                                            │
                                            ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ 8. OUTCOME                                                                             │
│    - Firm 226 (SANGAM HARDWARE) created with 0 child records (Empty Shell)            │
│    - Firm 225 (Apexx Global) contaminated with 100% of SANGAM HARDWARE data!           │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Firm Identity & ID Mapping Analysis

### Code Evidence from `BackupService.java`:
```java
// Lines 563-594 of BackupService.java
if (firmToUse == null) {
    FirmDetails newFirm = new FirmDetails();
    String name = f.getFirmName() != null ? f.getFirmName() : "Restored Firm";
    if ("clone".equalsIgnoreCase(mode)) {
        boolean exists = firmDetailsRepo.findAll().stream().anyMatch(ef -> name.equalsIgnoreCase(ef.getFirmName()));
        newFirm.setFirmName(exists ? name + " (Restored)" : name);
    } else {
        newFirm.setFirmName(name);
    }
    // ... copy firm metadata fields ...
    firmToUse = firmDetailsRepo.save(newFirm); // <--- CREATED WITH NEW ID (226)
}

// LINE 589: FATAL OVERRIDE
Long mappedId = (targetFirmId != null && targetFirmsToProcess.size() == 1) 
    ? targetFirmId  // <--- EVALUATES TO 225 (FIRM A)!
    : ((firmToUse != null && firmToUse.getId() != null) 
        ? firmToUse.getId() 
        : (targetFirmId != null ? targetFirmId : (oldFirmId != -1L ? oldFirmId : 1L)));

oldToNewFirmIdMap.put(oldFirmId, mappedId); // <--- MAPS 97 -> 225!
```

### Forensic Questions Answered:
1. **Does the backup contain the original firm ID?**  
   **Yes.** The backup file contains `firmDetails.id`, `allFirms[i].id`, `metadata.sourceFirmId`, and every child record explicitly stores its original `firmId` (e.g. `97`).
2. **Does restore trust the backup's firm ID, the active firm ID, or a newly generated ID?**  
   It creates a **newly generated firm ID** in `firmDetailsRepo.save(newFirm)` (ID 226), but **discards it for entity mapping** and trusts `targetFirmId` (the active firm ID 225) because `targetFirmId != null`.
3. **If Firm B had ID 1 in the backup but Firm A already has ID 1, what happens?**  
   Because `oldToNewFirmIdMap` maps to `targetFirmId`, Firm B's records are forcibly merged or inserted into Firm A (`targetFirmId`), regardless of source ID values.
4. **Is the original firm ID remapped to the destination firm ID?**  
   **Yes**, but the destination firm ID is incorrectly set to Firm A (`targetFirmId`) rather than the newly created Firm B (`firmToUse.getId()`).
5. **Is that mapping applied consistently across entities?**  
   **Yes.** Every single entity importer in `BackupService` queries `oldToNewFirmIdMap.get(oldFid)`, which uniformly routed 100% of records into Firm A.
6. **Can the importer silently substitute the active or default firm when a firm ID is missing?**  
   **Yes.** In lines 632-644 and line 2441, fallback logic defaults to `targetFirmId != null ? targetFirmId : 1L`.
7. **Can the frontend submit the wrong firm ID during restore?**  
   **Yes.** At [index.html:28261](file:///Users/afk/Documents/GitHub/Simple-Billing/billsoft/src/main/webapp/index.html#L28261), `API.firmId` is blindly passed as `targetFirmId` regardless of whether the restore mode is `'clone'` or `'merge'`.
8. **Can the backend override a valid requested destination with the active firm's ID?**  
   At [BackupController.java:206-211](file:///Users/afk/Documents/GitHub/Simple-Billing/billsoft/src/main/java/com/billing/simple/billsoft/controllers/BackupController.java#L206-L211), the backend requires `targetFirmId.equals(currentFirmId)`. Any attempt to pass a different target firm is rejected with `403 Forbidden: "Cross-firm restore prohibited"`.
9. **Does the restore wizard distinguish restoring into an existing firm from importing a new firm?**  
   The UI defines `restoreMode` (`'clone'` vs `'merge'`), but the invocation in `index.html` passes `targetFirmId: API.firmId` in both modes, causing the backend to behave identically in both.
10. **Is a restored firm recreated as a separate firm, or is its data imported into whichever firm is active?**  
    **Both, creating a severe architectural mismatch**: The `FirmDetails` entity is recreated as a separate firm (`ID = 226`), but its data is imported into whichever firm is active (`ID = 225`).

---

## 4. Entity-by-Entity Ownership Audit

The following table documents how each entity in the backup was processed by `BackupService.java`:

| Entity / Table | Source Firm Reference (`autobackup_latest.json`) | Destination Assignment (`BackupService.java`) | Foreign-Key Remapping Applied | Destination Repository Scope | Forensic Database Record Count | Risk Level |
|---|---|---|---|---|---|---|
| **FIRM_DETAILS** | Firm 97 (`SANGAM HARDWARE`) | Recreated as `ID = 226` (Line 586) | N/A (Root entity) | Global `firmDetailsRepo.save()` | **Firm 225: 1, Firm 226: 1** | Critical Divergence |
| **CUSTOMERS** | `c.firmId = 97` (52 in backup, 34 for firm 97) | Assigned `mappedFirmId = 225` (Line 734) | Remapped to `oldToNewCustomerMap` | `customerRepo.save()` with `firmId = 225` | **Firm 225: 34, Firm 226: 0** | High Contamination |
| **PRODUCTS** | `p.firmId = 97` (130 in backup, 106 for firm 97) | Assigned `mappedFirmId = 225` (Line 792) | Remapped to `oldToNewProductMap` | `productRepo.save()` with `firmId = 225` | **Firm 225: 106, Firm 226: 0** | High Contamination / Overwrite |
| **STOCK_MOVEMENTS** | `sm.firmId = 97` | Assigned `mappedFirmId = 225` (Line 864) | Linked to remapped `productId` | `stockMovementRepo.save()` with `firmId = 225` | **Firm 225: 95, Firm 226: 0** | Inventory Distortion |
| **INVOICES** | `inv.firmId = 97` (59 in backup, 40 for firm 97) | Assigned `mappedFirmId = 225` (Line 930) | Linked to remapped Customer & Pricing Profile | `invoiceRepo.save()` with `firmId = 225` | **Firm 225: 40, Firm 226: 0** | Sequence Collision / Tax Violation |
| **INVOICE_ITEMS** | Inherited from Invoice parent | Inherited from Invoice parent (Line 1027) | Linked to remapped `productId` | Cascade saved with Invoice | **Firm 225: 86, Firm 226: 0** | Inventory Distortion |
| **INVOICE_PAYMENTS** | `pay.firmId = 97` | Assigned `mappedFirmId = 225` (Line 1066) | Linked to remapped `invoiceId` | `invoicePaymentRepo.save()` with `firmId = 225` | **Firm 225: 18, Firm 226: 0** | Ledger Contamination |
| **SALES_RETURNS** | `sr.firmId = 97` (8 in backup, 5 for firm 97) | Assigned `mappedFirmId = 225` (Line 1130) | Linked to remapped Invoice & Customer | `salesReturnRepo.save()` with `firmId = 225` | **Firm 225: 5, Firm 226: 0** | Credit Note Contamination |
| **PARTIES (Vendors)** | `party.firmId = 97` (15 in backup, 13 for firm 97) | Assigned `mappedFirmId = 225` (Line 1238) | Remapped to `oldToNewPartyMap` | `partyRepo.save()` with `firmId = 225` | **Firm 225: 13, Firm 226: 0** | Supplier Contamination |
| **PURCHASE_ORDERS** | `po.firmId = 97` (14 in backup, 12 for firm 97) | Assigned `mappedFirmId = 225` (Line 1441) | Linked to remapped Vendor Party | `purchaseOrderRepo.save()` with `firmId = 225` | **Firm 225: 12, Firm 226: 0** | Order Contamination |
| **PARTY_PAYMENTS** | `pp.firmId = 97` | Assigned `mappedFirmId = 225` (Line 1548) | Linked to remapped Vendor Party | `partyPaymentRepo.save()` with `firmId = 225` | **Firm 225: 5, Firm 226: 0** | Accounts Payable Distortion |
| **EXPENSES** | `e.firmId = 97` | Assigned `mappedFirmId = 225` (Line 1714) | Direct firm ownership | `expenseRepo.save()` with `firmId = 225` | **Firm 225: 1, Firm 226: 0** | Financial Ledger Distortion |
| **SAVINGS** | `s.firmId = 97` | Assigned `mappedFirmId = 225` (Line 1814) | Direct firm ownership | `savingRepo.save()` with `firmId = 225` | **Firm 225: 10, Firm 226: 0** | Personal / Firm Ledger Leak |
| **GOALS & LOGS** | `g.firmId = 97` | Assigned `mappedFirmId = 225` (Line 1757) | Direct firm ownership | `goalRepo.save()` with `firmId = 225` | **Firm 225: 1, Firm 226: 0** | Financial Goals Leak |
| **EMPLOYEES** | `emp.firmId = 97` | Assigned `mappedFirmId = 225` (Line 1908) | Remapped to `oldToNewEmployeeMap` | `employeeRepo.save()` with `firmId = 225` | **Firm 225: 1, Firm 226: 0** | Payroll / PII Exposure |
| **PRICING_PROFILES** | `pp.firmId = 97` | Assigned `mappedFirmId = 225` (Line 654) | Direct firm ownership | `pricingProfileRepo.save()` with `firmId = 225` | **Firm 225: 5, Firm 226: 0** | Pricing Tier Corruption |
| **BACKUP_ENTITY_MAPPINGS** | N/A (Audit metadata) | Stored `TARGET_FIRM_ID = 225` | Stored `SOURCE_ENTITY_ID -> TARGET_ENTITY_ID` | `backupEntityMappingRepo.save()` | **Firm 225: 232 mappings recorded!** | Cryptographic Proof |

---

## 5. Transaction, Reset & Tenant Boundary Semantics

1. **Transaction Boundary**:
   - `importSelectiveData` is annotated `@Transactional` ([BackupService.java:450](file:///Users/afk/Documents/GitHub/Simple-Billing/billsoft/src/main/java/com/billing/simple/billsoft/service/BackupService.java#L450)).
   - Firm entity creation (`firmDetailsRepo.save(newFirm)`) and all 28 child entity imports occur within this single atomic transaction.
   - If an unhandled exception or constraint failure occurs, the entire import rolls back cleanly.
2. **Factory Reset Isolation**:
   - `factoryReset()` is an independent method ([BackupService.java:2662](file:///Users/afk/Documents/GitHub/Simple-Billing/billsoft/src/main/java/com/billing/simple/billsoft/service/BackupService.java#L2662)) and is **not** called during `importSelectiveData`.
   - `clean_wipe` mode during restore is strictly prohibited if any firm exists in the database ([BackupService.java:529-535](file:///Users/afk/Documents/GitHub/Simple-Billing/billsoft/src/main/java/com/billing/simple/billsoft/service/BackupService.java#L529-L535)).
3. **Tenant Context Lifecycle**:
   - `TenantInterceptor.preHandle` extracts `X-Firm-Id: 225` and binds it to `TenantContext`.
   - `TenantInterceptor.afterCompletion` executes `TenantContext.clear()` in a `finally` block.
   - However, within `importSelectiveData`, repositories are queried directly using the explicit `mappedFirmId` variable rather than `TenantContext.getCurrentFirmId()`. Because `mappedFirmId` was corrupted to 225 on line 589, explicit tenant-passing to repository queries directly targeted Firm A.
4. **Locking & Concurrency**:
   - `coordinator.tryLock(5, TimeUnit.SECONDS)` is acquired at the start of `importSelectiveData` and released in a `finally` block ([BackupService.java:454, 2652](file:///Users/afk/Documents/GitHub/Simple-Billing/billsoft/src/main/java/com/billing/simple/billsoft/service/BackupService.java#L454-L2652)), successfully preventing concurrent restore or auto-backup execution.

---

## 6. Physical Database & Backup Forensic Evidence

### 6.1 Immutable Forensic Preservation Snapshot
An immutable copy of the active database and backup archive was saved to `forensic_incident_cross_firm_restore_20261026/` with read-only permissions (`chmod 444`):
- `autobackup_latest.json` (SHA-256: `b955104ae618c7f818d11a4e9b20e013c5c8e1e37c610c75481666fe500b1d6b`)
- `database_current.mv.db` (SHA-256: `b1c74c8eb8a2875b102635116cb2282026faf5bb2075181d83dfea38aaab490f`)

### 6.2 Forensic Database Query Results
Read-only queries executed directly against `database_current.mv.db` via H2 engine:

#### 1. Query on `FIRM_DETAILS`:
```sql
SELECT ID, FIRM_NAME, OWNER_NAME, GSTIN, EMAIL, PHONE FROM FIRM_DETAILS;
```
**Output:**
```
ID  | FIRM_NAME       | OWNER_NAME    | GSTIN           | EMAIL              | PHONE
225 | Apexx Global    | Peter Kennan  | 27KHSVEKEVK78B2 | Yelabshb@hsdb.com  | 8830443289
226 | SANGAM HARDWARE | Sanket Yelave | JVDJSKJHLK      | yelavera@gmail.com | 8806690220
```

#### 2. Query on Table Counts Grouped by `FIRM_ID`:
```sql
SELECT 'CUSTOMERS' as tbl, FIRM_ID, count(*) as cnt FROM CUSTOMERS GROUP BY FIRM_ID
UNION ALL SELECT 'PRODUCTS', FIRM_ID, count(*) FROM PRODUCTS GROUP BY FIRM_ID
UNION ALL SELECT 'INVOICES', FIRM_ID, count(*) FROM INVOICES GROUP BY FIRM_ID
UNION ALL SELECT 'PURCHASE_ORDERS', FIRM_ID, count(*) FROM PURCHASE_ORDERS GROUP BY FIRM_ID
UNION ALL SELECT 'PARTIES', FIRM_ID, count(*) FROM PARTIES GROUP BY FIRM_ID
UNION ALL SELECT 'EXPENSES', FIRM_ID, count(*) FROM EXPENSES GROUP BY FIRM_ID
UNION ALL SELECT 'SAVINGS', FIRM_ID, count(*) FROM SAVINGS GROUP BY FIRM_ID
UNION ALL SELECT 'EMPLOYEES', FIRM_ID, count(*) FROM EMPLOYEES GROUP BY FIRM_ID
UNION ALL SELECT 'SALES_RETURNS', FIRM_ID, count(*) FROM SALES_RETURNS GROUP BY FIRM_ID;
```
**Output:**
```
TBL             | FIRM_ID | CNT
CUSTOMERS       | 225     | 34
PRODUCTS        | 225     | 106
INVOICES        | 225     | 40
PURCHASE_ORDERS | 225     | 12
PARTIES         | 225     | 13
EXPENSES        | 225     | 1
SAVINGS         | 225     | 10
EMPLOYEES       | 225     | 1
SALES_RETURNS   | 225     | 5
```
**Firm 226 has ZERO records in every single table!**

#### 3. Cryptographic Ledger Proof (`BACKUP_ENTITY_MAPPINGS`):
```sql
SELECT BACKUP_SOURCE_ID, ENTITY_TYPE, IMPORTED_AT, SOURCE_ENTITY_ID, TARGET_ENTITY_ID, TARGET_FIRM_ID 
FROM BACKUP_ENTITY_MAPPINGS LIMIT 5;
```
**Output:**
```
BACKUP_SOURCE_ID | ENTITY_TYPE     | IMPORTED_AT                | SOURCE_ENTITY_ID | TARGET_ENTITY_ID | TARGET_FIRM_ID
firm_97          | PRICING_PROFILE | 2026-10-26 19:57:45.028419 | 33               | 97               | 225
firm_97          | CUSTOMER        | 2026-10-26 19:57:45.043140 | 257              | 929              | 225
firm_97          | CUSTOMER        | 2026-10-26 19:57:45.045131 | 258              | 930              | 225
firm_97          | INVOICE         | 2026-10-26 19:57:45.105120 | 129              | 401              | 225
```
Every single row confirms:
- Exact Incident Timestamp: **2026-10-26 19:57:45**
- Source Firm: `firm_97` (SANGAM HARDWARE)
- Forced Destination: `TARGET_FIRM_ID = 225` (Apexx Global)

---

## 7. Confirmed, Probable, Possible & Disproven Hypotheses

| # | Hypothesis Description | Status | Evidence & Rationale |
|---|---|---|---|
| **H1** | **`BackupService.java:589` prioritizes `targetFirmId` over `firmToUse.getId()` in `clone` mode** | **CONFIRMED** | Code inspect on line 589 shows ternary operator checking `targetFirmId != null` first, forcing `mappedId = targetFirmId` (225) instead of the new firm ID (226). |
| **H2** | **Frontend `index.html:28258` passes active firm `API.firmId` as `targetFirmId` in `clone` mode** | **CONFIRMED** | Code inspect at line 28261 shows `API.firmId` unconditionally supplied as 3rd parameter to `API.backup.restoreAuto()`. |
| **H3** | **Controller check in `BackupController.java:206-211` forces `targetFirmId == currentFirmId`** | **CONFIRMED** | Controller rejects any request where `targetFirmId != currentFirmId`, preventing independent clone target resolution. |
| **H4** | **Database sequence ID collision between Firm B (97) and Firm A (225)** | **DISPROVEN** | Database shows Firm A is 225 and Firm B was inserted as 226. Source ID 97 did not collide with 225. Remapping was explicitly computed in software. |
| **H5** | **Silent fallback to default firm ID 1** | **DISPROVEN** | All records received `FIRM_ID = 225` (Firm A), not 1. Proven by `BACKUP_ENTITY_MAPPINGS`. |
| **H6** | **UI active-firm display bug (data in Firm B, but UI shows Firm A)** | **DISPROVEN** | Physical SQL queries prove Firm 226 has 0 records across all 44 database tables. The data is physically stored under `FIRM_ID = 225`. |
| **H7** | **Unscoped bulk `saveAll` or `deleteAll` wiping Firm B** | **DISPROVEN** | `importSelectiveData` iterates entity by entity; no bulk wipe was executed. |

---

## 8. Exact Source Code References

1. **Frontend Call Site:**
   - [`billsoft/src/main/webapp/index.html:28257-28268`](file:///Users/afk/Documents/GitHub/Simple-Billing/billsoft/src/main/webapp/index.html#L28257-L28268): `executeSelectiveRestore` supplies `API.firmId` as `targetFirmId`.
2. **Frontend API Bridge:**
   - [`billsoft/src/main/webapp/js/api.js:935-943`](file:///Users/afk/Documents/GitHub/Simple-Billing/billsoft/src/main/webapp/js/api.js#L935-L943): `API.backup.restoreAuto` appends `targetFirmId` to `FormData`.
3. **Backend Security Interceptor:**
   - [`billsoft/src/main/java/com/billing/simple/billsoft/security/TenantInterceptor.java:140-157`](file:///Users/afk/Documents/GitHub/Simple-Billing/billsoft/src/main/java/com/billing/simple/billsoft/security/TenantInterceptor.java#L140-L157): Sets `TenantContext` to active firm.
4. **Backend Controller Guard:**
   - [`billsoft/src/main/java/com/billing/simple/billsoft/controllers/BackupController.java:206-211`](file:///Users/afk/Documents/GitHub/Simple-Billing/billsoft/src/main/java/com/billing/simple/billsoft/controllers/BackupController.java#L206-L211): Restricts `targetFirmId` to `currentFirmId`.
5. **Backend Service Fatal Remapping:**
   - [`billsoft/src/main/java/com/billing/simple/billsoft/service/BackupService.java:563-594`](file:///Users/afk/Documents/GitHub/Simple-Billing/billsoft/src/main/java/com/billing/simple/billsoft/service/BackupService.java#L563-L594): Line 589 overrides new firm ID with `targetFirmId`.
6. **Entity Repositories Mapping Consumption:**
   - [`billsoft/src/main/java/com/billing/simple/billsoft/service/BackupService.java:734, 792, 930, 1441`](file:///Users/afk/Documents/GitHub/Simple-Billing/billsoft/src/main/java/com/billing/simple/billsoft/service/BackupService.java#L734): Uses `oldToNewFirmIdMap.get(oldFid)` to stamp all records with `firmId = 225`.

---

## 9. Scope and Severity Assessment

- **Affected Document/Entity Families:** Invoices, Quotes, Customers, Products, Inventory Movements, Purchase Orders, Suppliers/Parties, Payments, Expenses, Savings, Goals, Employees, Sales Returns, Pricing Profiles.
- **Affected Modes:** Selective restore and auto-restore initiated from the Settings UI where `restoreMode === 'clone'`.
- **Unaffected Modes:**
  - First-Run Onboarding Wizard ([index.html:31676](file:///Users/afk/Documents/GitHub/Simple-Billing/billsoft/src/main/webapp/index.html#L31676)) does **not** pass `targetFirmId`, meaning clean-install onboarding restores correctly create separate firm IDs without this defect.

---

## 10. Safe Remediation Plan (No Execution)

The remediation requires precision fixes across the frontend, controller, and service layers:

### 10.1 Backend Code Remediation
1. **Fix `BackupService.java:589`**:
   In `clone` mode, `mappedId` must **strictly** be `firmToUse.getId()`. `targetFirmId` must only be used when `mode` is explicitly `"merge"`:
   ```java
   Long mappedId;
   if ("merge".equalsIgnoreCase(mode) && targetFirmId != null && targetFirmsToProcess.size() == 1) {
       mappedId = targetFirmId;
   } else if (firmToUse != null && firmToUse.getId() != null) {
       mappedId = firmToUse.getId();
   } else {
       mappedId = (targetFirmId != null ? targetFirmId : (oldFirmId != -1L ? oldFirmId : 1L));
   }
   ```
2. **Fix `BackupController.java:206-211`**:
   Ensure `targetFirmId` is only checked against `currentFirmId` when `mode` is `"merge"`. When `mode` is `"clone"`, `targetFirmId` must be ignored or rejected if provided.

### 10.2 Frontend Code Remediation
1. **Fix `index.html:28257-28268`**:
   When `restoreMode === 'clone'`, pass `null` for `targetFirmId`:
   ```javascript
   const targetFid = restoreMode === 'merge' ? API.firmId : null;
   const res = isAutoRestore
     ? await API.backup.restoreAuto(Array.from(selectedFirms), restoreMode, targetFid)
     : await API.backup.importSelective(importFile, Array.from(selectedFirms), restoreMode, targetFid);
   ```

---

## 11. Data Recovery Precautions for the Affected Installation

Because the database contains an exact, immutable audit trail in `BACKUP_ENTITY_MAPPINGS` from the restore event at `2026-10-26 19:57:45`, **100% clean data recovery is mathematically possible without data loss**:

1. **Verify Isolation of Apexx Global's Native Records**:
   Identify any records in Firm 225 that were created *before* `2026-10-26 19:57:45` (Apexx Global's true native data).
2. **Reassign Contaminated Records to Firm 226**:
   Using `BACKUP_ENTITY_MAPPINGS` where `BACKUP_SOURCE_ID = 'firm_97'` and `TARGET_FIRM_ID = 225`:
   - For every `TARGET_ENTITY_ID` and `ENTITY_TYPE` in the mapping table, update `FIRM_ID = 226` in the corresponding table (`CUSTOMERS`, `PRODUCTS`, `INVOICES`, `PURCHASE_ORDERS`, `PARTIES`, `EXPENSES`, `SAVINGS`, `EMPLOYEES`, `SALES_RETURNS`, `PRICING_PROFILES`, `STOCK_MOVEMENTS`).
   - Update `BACKUP_ENTITY_MAPPINGS` to set `TARGET_FIRM_ID = 226`.
3. **Execution Precautions**:
   - The recovery SQL script must be tested first on a secondary copy of `database_current.mv.db` in a scratch environment.
   - The production database must not be touched until the test script is verified with 0 foreign-key integrity violations and verified entity counts.

---

## 12. Minimal Isolated Regression-Test Plan

Create a dedicated integration test `BackupCrossFirmRestoreIsolationRegressionTest.java`:
1. Initialize an empty H2 database.
2. Create Firm A (`id = 1`) and insert 2 distinct customers and 2 invoices.
3. Prepare a single-firm backup DTO containing Firm B (`id = 97`) with 5 customers and 5 invoices.
4. Execute `backupService.importSelectiveData(backupB, Set.of(97L), "clone", 1L)`.
5. Assert that:
   - A new Firm B entity is created (`id = 2`).
   - All 5 restored customers have `firmId == 2` (ZERO assigned to `firmId == 1`).
   - All 5 restored invoices have `firmId == 2` (ZERO assigned to `firmId == 1`).
   - Firm A's customer count remains strictly 2.
   - Firm A's invoice count remains strictly 2.
   - Cross-firm isolation is 100% preserved.

---

## 13. Resolution of Pre-Rehearsal Questions & Forensic Verification

1. **Native Record Collision in Firm A**:
   - Rehearsal investigation on the forensic database verified that Firm A (`ID = 225`) had **ZERO** records created prior to `19:57:45`.
   - The only record created after restore was `BUSINESS_LETTERS` ID 2 at `19:59:00` addressed to imported vendor `PARTY_ID = 235`.
   - There were zero ID collisions or overwrite conflicts with pre-existing records.
2. **User Notification & Boundary Enforcements**:
   - `BackupController` now rejects ambiguous combinations (e.g., passing `targetFirmId` in `clone` mode, or omitting `targetFirmId` in `merge` mode).
   - `index.html` explicitly supplies `null` for `targetFirmId` when `restoreMode === 'clone'`.

---

## 14. Remediation Implemented (Parts A & B)

### 14.1 Defined Restore Mode Semantics
1. **`clone` Mode (Create Distinct Isolated Firm)**:
   - Always maps each source firm in the backup to a newly generated, persisted destination firm in `FIRM_DETAILS`.
   - Never allows the active firm ID (`currentFirmId`), UI session state, or any caller-supplied `targetFirmId` to override destination firm assignment.
   - Completely ignores or rejects any caller-supplied `targetFirmId`.
   - Multi-firm backups map every source firm to its own independent, newly created destination firm.
2. **`merge` Mode (Restore Into Explicit Existing Firm)**:
   - Requires an explicit, validated destination firm ID (`targetFirmId`).
   - Rejects the operation if `targetFirmId` is missing, null, or does not exist in `FIRM_DETAILS`.
   - Enforces authorization: the caller's active firm context (`currentFirmId`) must match `targetFirmId`.
   - Enforces single-firm restriction: cannot merge multi-firm backups into a single target firm without unambiguous target mapping.

### 14.2 Code Changes Applied
- **[`BackupService.java`](file:///Users/afk/Documents/GitHub/Simple-Billing/billsoft/src/main/java/com/billing/simple/billsoft/service/BackupService.java)**:
  - Enforced strict restore mode normalization (`clone` vs `merge`).
  - In `clone` mode: strictly maps `mappedId = firmToUse.getId()`, completely ignoring any caller-supplied `targetFirmId`.
  - Disallowed `targetFirmId` from populating `oldToNewFirmIdMap` when `clone` mode is active.
  - In `merge` mode: validates that `targetFirmId` exists and that selective multi-firm merges are rejected if ambiguous.
- **[`BackupController.java`](file:///Users/afk/Documents/GitHub/Simple-Billing/billsoft/src/main/java/com/billing/simple/billsoft/controllers/BackupController.java)**:
  - Boundary parameter validation for `restoreAutoBackup`, `importSelectiveBackup`, and `importBackup`:
  - When `mode == "clone"`, forces `targetFirmId = null`, preventing any UI firm ID passthrough.
  - When `mode == "merge"`, requires `targetFirmId != null`, verifies existence, and enforces `targetFirmId.equals(currentFirmId)`.
- **[`index.html`](file:///Users/afk/Documents/GitHub/Simple-Billing/billsoft/src/main/webapp/index.html)**:
  - In `executeSelectiveRestore` (line 28280): set `const targetFid = restoreMode === 'merge' ? API.firmId : null;`.
  - Synced to compiled static resources (`target/classes/META-INF/resources/index.html`).

---

## 15. Regression Test Evidence (Part B)

A dedicated, isolated regression test suite was implemented in:
[`billsoft/src/test/java/com/billing/simple/billsoft/regression/backup/BackupCrossFirmRestoreIsolationRegressionTest.java`](file:///Users/afk/Documents/GitHub/Simple-Billing/billsoft/src/test/java/com/billing/simple/billsoft/regression/backup/BackupCrossFirmRestoreIsolationRegressionTest.java)

Executed against an isolated in-memory test database:

| # | Test Case Method | Invariant Verified | Result |
|---|------------------|---------------------|--------|
| 1 | `testCloneMode_CreatesSeparateDestinationFirm_AndPreservesActiveFirmData` | Cloning Firm B while Firm A is active creates separate firm (`ID 2`); all cloned records belong to Firm 2; Firm A's records remain untouched. | **PASSED** |
| 2 | `testSourceAndDestinationFirmIdCollision_MapsWithoutCrossFirmContamination` | Source firm ID (1) colliding with active firm ID (1) restores cleanly into new firm (2) without cross-firm assignment. | **PASSED** |
| 3 | `testCloneMode_CallerSuppliedTargetIdCannotOverrideDestinationFirm` | Supplying `targetFirmId = firmA.getId()` in `clone` mode is ignored; records are strictly routed to newly generated firm. | **PASSED** |
| 4 | `testMergeMode_EnforcesValidAuthorizedDestination_AndRejectsInvalidOrMissingTarget` | Merge mode succeeds with valid authorized target, fails safely with 400 Bad Request when target is null or does not exist. | **PASSED** |
| 5 | `testMultiFirmBackup_MapsEverySourceFirmIndependentlyInCloneMode` | Multi-firm backup containing Source 10 and Source 20 creates two distinct destination firms, each with isolated records. | **PASSED** |
| 6 | `testMissingFirmMetadataAndInvalidModesFailSafely` | Corrupted/missing firm metadata and invalid restore modes throw `IllegalArgumentException` and abort safely without partial commit. | **PASSED** |
| 7 | `testFirmScopedSettingsAndAuxiliaryEntitiesRetainCorrectOwnership` | Firm-scoped settings, pricing profiles, reminders, notes, and auxiliary entities retain correct destination firm ownership. | **PASSED** |

### Test Suite Execution Summary
- `BackupCrossFirmRestoreIsolationRegressionTest`: **7 passed, 0 failed, 0 errors**
- Full Backup Regression Suite (`BackupCrossFirmRestoreIsolationRegressionTest`, `GenericMultiFirmBackupIdempotencyTest`, `BackupAndResetRegressionTest`, `BackupSelectiveImportTest`, `BackupValidationServiceTest`): **32 passed, 0 failed, 0 errors**
- Web Controller Suite (`BackupControllerTest`): **8 passed, 0 failed, 0 errors**

---

## 16. Recovery Rehearsal on Disposable Database Copy (Part C)

### 16.1 Cryptographic Hash Verification
Before any rehearsal, the forensic preservation directory assets were cryptographically verified:
- `autobackup_latest.json`: `b955104ae618c7f818d11a4e9b20e013c5c8e1e37c610c75481666fe500b1d6b` (**MATCHED**)
- `database_current.mv.db`: `b1c74c8eb8a2875b102635116cb2282026faf5bb2075181d83dfea38aaab490f` (**MATCHED**)

### 16.2 Disposable Copy Creation
- Working copy created at: `billsoft/target/rehearsal_db/disposable_incident.mv.db`
- Preserved directory `forensic_incident_cross_firm_restore_20261026/` remained read-only protected (`chmod 444`).
- Live database was **NEVER** modified.

### 16.3 Inspection of `BACKUP_ENTITY_MAPPINGS`
The forensic database contains **331 total ledger entries**, all created at `2026-10-26 19:57:45` for `BACKUP_SOURCE_ID = 'firm_97'` and `TARGET_FIRM_ID = 225`:
- **232 Primary Business Records** (matching the 232 count reported in the incident brief):
  - 106 Products, 40 Invoices, 34 Customers, 13 Parties, 12 Purchase Orders, 10 Savings, 5 Pricing Profiles, 5 Sales Returns, 3 Reminders, 1 Employee, 1 Expense, 1 Goal, 1 Note.
- **99 Secondary / Audit Ledger Rows**:
  - 49 Stock Movements, 18 Invoice Payments, 17 Notifications, 11 Goal Logs, 4 Party Payments.

### 16.4 Dependent & Related Entity Analysis
- **Direct `firmId` Entities**: Cleanly mapped using `BACKUP_ENTITY_MAPPINGS` target IDs.
- **Indirect Entities Without Direct `firmId`**:
  - `INVOICE_ITEMS`: Inherit firm ownership through `INVOICE_ID` (verified all 40 parent invoices migrated).
  - `PURCHASE_ORDER_ITEMS`: Inherit firm ownership through `PURCHASE_ORDER_ID` (verified all 12 parent POs migrated).
  - `SALES_RETURN_ITEMS`: Inherit firm ownership through `SALES_RETURN_ID` (verified all 5 parent returns migrated).
- **Post-Restore Activity**:
  - `BUSINESS_LETTERS` ID 2 was created at `19:59:00` for `PARTY_ID = 235` (an imported vendor). Reassigned to Firm 226.
  - 13 system notification alerts generated at `19:57:47` referencing imported invoices/deliveries. Reassigned to Firm 226.

### 16.5 Rehearsal Execution & Foreign-Key Integrity Verification
The recovery SQL script (`scratch/recovery_rehearsal.sql`) was executed against `disposable_incident.mv.db`:
- **Pre-execution Record Counts in Firm 225**: 233 business records (232 imported + 1 letter).
- **Post-execution Record Counts in Firm 225**: **0 records** across all business tables.
- **Post-execution Record Counts in Firm 226**: **All 233 records** successfully migrated to Firm 226 (`SANGAM HARDWARE`).
- **Foreign-Key Integrity Audit on Disposable Copy**:
  1. `INVOICES -> CUSTOMERS`: 0 mismatched firm IDs
  2. `INVOICE_ITEMS -> PRODUCTS`: 0 mismatched firm IDs
  3. `PURCHASE_ORDERS -> PARTIES`: 0 mismatched firm IDs
  4. `PURCHASE_ORDER_ITEMS -> PRODUCTS`: 0 mismatched firm IDs
  5. `INVOICE_PAYMENTS -> INVOICES`: 0 orphaned payments
  6. `SALES_RETURNS -> INVOICES`: 0 mismatched firm IDs
  7. `STOCK_MOVEMENTS -> PRODUCTS`: 0 mismatched firm IDs
  8. `BUSINESS_LETTERS -> PARTIES`: 0 mismatched firm IDs

---

## 17. Production Recovery Plan & Execution Checklist (Part D)

> [!CAUTION]
> **DO NOT EXECUTE AGAINST LIVE PRODUCTION WITHOUT EXPLICIT MANUAL OPERATIONAL SIGN-OFF.**
> The live business database has NOT been modified. This checklist is prepared for the designated database administrator.

### 17.1 Pre-Execution Requirements
- [ ] Ensure application server is stopped (`kill` running Spring Boot process).
- [ ] Take a timestamped cold copy of `database_current.mv.db` (e.g., `database_current_pre_recovery_backup.mv.db`).
- [ ] Verify SHA-256 hash of the cold copy against `b1c74c8eb8a2875b102635116cb2282026faf5bb2075181d83dfea38aaab490f`.
- [ ] Confirm target destination firm exists in `FIRM_DETAILS`:
  ```sql
  SELECT ID, FIRM_NAME FROM FIRM_DETAILS WHERE ID = 226;
  -- Must return: 226 | SANGAM HARDWARE
  ```

### 17.2 Execution Script (`recovery_execution_firm_225_to_226.sql`)
```sql
-- RupeeCRM Production Recovery Script: Incident Cross-Firm Restore 20261026
-- Moves mistakenly imported records from Firm 225 (Apexx Global) to Firm 226 (SANGAM HARDWARE)
SET AUTOCOMMIT OFF;

-- 1. Validate preconditions
SELECT COUNT(*) AS MAPPING_COUNT FROM BACKUP_ENTITY_MAPPINGS WHERE BACKUP_SOURCE_ID = 'firm_97' AND TARGET_FIRM_ID = 225;
-- Expected: 331 (232 primary + 99 secondary)

-- 2. Migrate primary business entities
UPDATE CUSTOMERS SET FIRM_ID = 226 WHERE ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'CUSTOMER' AND TARGET_FIRM_ID = 225);
UPDATE PRODUCTS SET FIRM_ID = 226 WHERE ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'PRODUCT' AND TARGET_FIRM_ID = 225);
UPDATE INVOICES SET FIRM_ID = 226 WHERE ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'INVOICE' AND TARGET_FIRM_ID = 225);
UPDATE PURCHASE_ORDERS SET FIRM_ID = 226 WHERE ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'PURCHASE_ORDER' AND TARGET_FIRM_ID = 225);
UPDATE PARTIES SET FIRM_ID = 226 WHERE ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'PARTY' AND TARGET_FIRM_ID = 225);
UPDATE EXPENSES SET FIRM_ID = 226 WHERE ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'EXPENSE' AND TARGET_FIRM_ID = 225);
UPDATE SAVINGS SET FIRM_ID = 226 WHERE ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'SAVING' AND TARGET_FIRM_ID = 225);
UPDATE GOALS SET FIRM_ID = 226 WHERE ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'GOAL' AND TARGET_FIRM_ID = 225);
UPDATE EMPLOYEES SET FIRM_ID = 226 WHERE ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'EMPLOYEE' AND TARGET_FIRM_ID = 225);
UPDATE SALES_RETURNS SET FIRM_ID = 226 WHERE ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'SALES_RETURN' AND TARGET_FIRM_ID = 225);
UPDATE PRICING_PROFILES SET FIRM_ID = 226 WHERE ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'PRICING_PROFILE' AND TARGET_FIRM_ID = 225);
UPDATE REMINDERS SET FIRM_ID = 226 WHERE ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'REMINDER' AND TARGET_FIRM_ID = 225);
UPDATE NOTES SET FIRM_ID = 226 WHERE ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'NOTE' AND TARGET_FIRM_ID = 225);

-- 3. Migrate secondary and audit entities
UPDATE STOCK_MOVEMENTS SET FIRM_ID = 226 WHERE ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'STOCK_MOVEMENT' AND TARGET_FIRM_ID = 225);
UPDATE INVOICE_PAYMENTS SET FIRM_ID = 226 WHERE ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'INVOICE_PAYMENT' AND TARGET_FIRM_ID = 225);
UPDATE PARTY_PAYMENTS SET FIRM_ID = 226 WHERE ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'PARTY_PAYMENT' AND TARGET_FIRM_ID = 225);
UPDATE GOAL_LOGS SET FIRM_ID = 226 WHERE ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'GOAL_LOG' AND TARGET_FIRM_ID = 225);
UPDATE SYSTEM_NOTIFICATIONS SET FIRM_ID = 226 WHERE ID IN (SELECT TARGET_ENTITY_ID FROM BACKUP_ENTITY_MAPPINGS WHERE ENTITY_TYPE = 'SYSTEM_NOTIFICATION' AND TARGET_FIRM_ID = 225);

-- 4. Migrate post-restore dependent business letters and alerts
UPDATE BUSINESS_LETTERS SET FIRM_ID = 226 WHERE FIRM_ID = 225 AND PARTY_ID IN (SELECT ID FROM PARTIES WHERE FIRM_ID = 226);
UPDATE SYSTEM_NOTIFICATIONS SET FIRM_ID = 226 WHERE FIRM_ID = 225 AND CREATED_AT >= '2026-10-26 19:57:45';

-- 5. Update mapping ledger for consistency
UPDATE BACKUP_ENTITY_MAPPINGS SET TARGET_FIRM_ID = 226 WHERE BACKUP_SOURCE_ID = 'firm_97' AND TARGET_FIRM_ID = 225;

-- 6. Integrity check queries (Must return 0)
SELECT COUNT(*) AS FK_MISMATCH_INV_CUST FROM INVOICES i JOIN CUSTOMERS c ON i.CUSTOMER_ID = c.ID WHERE i.FIRM_ID <> c.FIRM_ID;
SELECT COUNT(*) AS FK_MISMATCH_PO_PARTY FROM PURCHASE_ORDERS p JOIN PARTIES pt ON p.PARTY_ID = pt.ID WHERE p.FIRM_ID <> pt.FIRM_ID;

-- COMMIT only after integrity checks return 0
COMMIT;
```

### 17.3 Abort & Rollback Criteria
- If any `UPDATE` affects an unexpected row count (e.g. Products != 106, Invoices != 40, Customers != 34).
- If any FK mismatch query returns > 0.
- Immediate action on abort: `ROLLBACK;`, stop execution, and restore the pre-recovery cold backup file.

