# Multi-Firm Invoice Migration & Revert Subsystem — Implementation Report

**Document Version:** 1.0.0
**Repository:** `RanjeetYelave/Simple-Billing` (`billsoft`)
**Status:** IMPLEMENTATION COMPLETE
**Environment:** PRE-PRODUCTION

---

## 1. Architecture Overview

The Cross-Firm Invoice Migration & Revert subsystem enables multi-tenant enterprises operating multiple business entities (firms) within RupeeCRM / Simple-Billing to safely transfer invoice ownership between firms while preserving data integrity, accounting history, inventory ledgers, and forecasting accuracy.

```
+---------------------------------------------------------------------------------------------------+
|                                      FRONTEND (Tab-Local Isolation)                                |
|   Tab A (sessionStorage: Firm Alpha)                     Tab B (sessionStorage: Firm Beta)       |
|   Header: X-Firm-Id: 193                                 Header: X-Firm-Id: 194                  |
|   Shared Master Auth Token: X-Auth-Token: <jwt/session-token>                                    |
+-------------------------------------------------+-------------------------------------------------+
                                                  |
                                                  v
+---------------------------------------------------------------------------------------------------+
|                                 REST API & TENANT SECURITY INTERCEPTOR                             |
|   TenantInterceptor sets TenantContext.currentFirmId per HTTP request; clears on afterCompletion  |
|   Endpoints:                                                                                      |
|     - GET  /api/invoices/{id}/migration-preview?targetFirmId={firmId} (Read-Only Preview)        |
|     - POST /api/invoices/{id}/migrate (Atomic Forward Migration)                                  |
|     - POST /api/invoices/{id}/revert-migration (Snapshot Restoration Forward Revert)              |
|     - GET  /api/invoices/{id}/migration-history (Immutable Audit Trail)                           |
+-------------------------------------------------+-------------------------------------------------+
                                                  |
                                                  v
+---------------------------------------------------------------------------------------------------+
|                                   INVOICE MIGRATION SERVICE                                       |
|   - Pessimistic Row Locking (PESSIMISTIC_WRITE)                                                   |
|   - Strict Precondition Validation (0 payments, 0 returns, non-cancelled)                         |
|   - Immutable Pre-Migration Snapshot (CLOB JSON)                                                  |
|   - Reversible Stock Movement Ledger Inversion                                                    |
|   - Multi-Dimension Customer Resolution (GSTIN, Phone, Name)                                     |
|   - Exact Product & Saved Item Fallback Binding                                                   |
|   - Frozen Historical Pricing (Zero master rate drift)                                            |
|   - Monotonic Invoice Sequence Allocation (No ID reuse)                                           |
|   - Dynamic Quotation / Converted Invoice Decoupling                                              |
|   - Tenant-Scoped Reminder Relocation & Restoration                                               |
|   - Real-Time Forecast Transaction Re-aggregation                                                 |
+---------------------------------------------------------------------------------------------------+
```

---

## 2. Migration Lifecycle (Forward $A \to B$)

1. **Client Request:** Frontend identifies active firm ($B$) differs from invoice owner firm ($A$) and requests `/api/invoices/{id}/migration-preview?targetFirmId=B`.
2. **Pessimistic Locking & Validation:**
   - Obtains `PESSIMISTIC_WRITE` lock on `Invoice` row via `invoiceRepo.findByIdForUpdate(id)`.
   - Re-checks authoritative `invoice.getFirmId() == A` (idempotency check against double submission).
   - Validates target firm $B$ exists and $B \neq A$.
   - Validates invoice is eligible: zero recorded payments, zero sales returns, status is not `CANCELLED` or `ESTIMATE`.
3. **Snapshot Capture:** Complete JSON snapshot of Firm $A$ state is serialized (including financial headers, customer details, line item pricing, quotation link, and physical stock deductions).
4. **Source Inventory Inversion:**
   - Queries ledger via `stockMovementRepo.sumQuantityChangeByProductIdForInvoice(firmA, invNo, invId)`.
   - Restores exact physical stock deducted in Firm $A$ recording `INVOICE_MIGRATION_REVERSAL` movements.
5. **Source Forecast Reversal:**
   - Deletes Firm $A$ forecast sales transactions for this invoice.
   - Recalculates daily aggregated metrics for all affected Firm $A$ products.
6. **Target Customer Resolution:**
   - Resolves or creates customer in Firm $B$ using multi-dimensional matching (GSTIN, phone, name) or explicit user selection.
7. **Target Product Resolution & Saved Item Fallback:**
   - Matches items by SKU or Name in Firm $B$, or binds as unlinked Saved Item (`product = null`).
   - Line totals, taxes, discounts, and historical unit prices are strictly preserved without re-pricing from Firm $B$'s catalog.
8. **Target Number Allocation:**
   - Generates next monotonic sequence number in Firm $B$ (e.g., `INV-0001`).
9. **Entity Re-Homing:**
   - Sets `invoice.firmId = B`, `invoice.invoiceNumber = newNumber`, `invoice.customer = customerB`, `invoice.convertedInvoiceId = null`.
10. **Target Inventory Deduction:**
    - Records `INVOICE_SALE` deductions for linked goods products in Firm $B$.
11. **Target Forecast Ingestion:**
    - Generates Firm $B$ forecast transactions and aggregates daily metrics.
12. **Reminder Relocation:**
    - Updates `Reminder.firmId = B` and `Reminder.customerId = customerB.id` for all reminders tied to this invoice.
13. **Migration Record Persistence:**
    - Persists new `InvoiceMigrationRecord` with status `ACTIVE`, marking any previous active record `SUPERSEDED`.

---

## 3. Revert Lifecycle (Restoration $B \to A$)

Revert is **not** a database transaction rollback; it is a compensating forward transaction that restores the invoice to Firm $A$ based on the immutable snapshot.

1. **Pessimistic Lock & Validation:**
   - Locks invoice row in Firm $B$.
   - Finds latest `ACTIVE` migration record.
   - Verifies zero payments and zero returns recorded while in Firm $B$.
2. **Current Target Inversion:**
   - Inverts actual physical inventory deductions in Firm $B$.
   - Deletes Firm $B$ forecast transactions and recalculates Firm $B$ metrics.
3. **Snapshot Deserialization:**
   - Deserializes pre-migration snapshot containing Firm $A$ state.
4. **Customer & Product Restoration:**
   - Re-attaches original Firm $A$ customer and original Firm $A$ product catalog references.
5. **Estimate Link Reconnection:**
   - Re-attaches `convertedInvoiceId` if quotation still exists in Firm $A$; otherwise detaches cleanly without breaking.
6. **Reminder Restoration:**
   - Moves reminders back to Firm $A$ and source customer ID.
7. **Monotonic Sequence Allocation in Source Firm:**
   - Allocates a **new** invoice sequence number in Firm $A$ (e.g., `INV-0002`). The original `INV-0001` is permanently consumed and never reused.
8. **Source Inventory Re-deduction:**
   - Deducts physical stock in Firm $A$ matching snapshot item quantities.
9. **Source Forecast Re-sync:**
   - Ingests sales forecast in Firm $A$ and recalculates metrics.
10. **History Update:**
    - Updates previous migration record to `REVERTED`.
    - Persists a new `InvoiceMigrationRecord` with `isRevert = true`, `status = ACTIVE`, and `parentMigrationId = record.id`.

---

## 4. Tenant Security Model & Hardening

1. **Master Authentication Token:**
   - Single shared authentication token (`X-Auth-Token`) valid across all firms for authenticated users.
   - Zero token-to-firm binding.
2. **Dynamic Request Scoping:**
   - Active firm resolved dynamically per HTTP request from `X-Firm-Id` header via `TenantInterceptor` and `TenantContext`.
   - Stored in `ThreadLocal` and cleared in `afterCompletion`.
3. **Removed Unsafe Unscoped Fallbacks:**
   - `ReminderService`: Removed unscoped `findById` fallbacks on null firmId.
   - `GoalService`: Removed unscoped `findById` fallbacks.
   - `InvoiceService`: Added product firm ownership check (`p.getFirmId().equals(invoice.getFirmId())`) to `updateFullInvoice` to prevent cross-firm product attachments during updates.

---

## 5. Customer Resolution Rules

Resolution evaluates target firm customers across all 3 identity dimensions:
1. **GSTIN Match:** Exact case-insensitive GSTIN match (`findByFirmIdAndGstinIgnoreCase`).
2. **Phone Match:** Normalized phone number match (`findByFirmIdAndPhone`).
3. **Name Match:** Case-insensitive name match (`findByFirmIdAndNameIgnoreCase`).

- **Match Types:**
  - `EXACT_MATCH`: Exactly 1 candidate found across dimensions.
  - `AMBIGUOUS`: Multiple candidates found across dimensions. Client selects candidate explicitly.
  - `NO_MATCH`: 0 candidates found. New customer created in target firm within the migration transaction.
- **Master Data Immutability:** Selected customer's master records are **never** mutated during migration.

---

## 6. Product Resolution & Saved Item Fallback

- Products in target firm matched by **SKU** or **Name**.
- **Existing Product Match:** Invoice item linked to target `Product` entity; inventory deducted accordingly.
- **Missing Product in Target Firm:** Item is preserved as generic/Saved Item (`item.product = null`).
  - Historical `productName`, `hsnCode`, `unit`, `pricePerUnit`, `taxableAmount`, `gstPercent`, `gstAmount`, and `lineTotal` are preserved verbatim.
  - Stock effect is `0` (no false inventory deductions in target firm).
- **Master Data Immutability:** Target product master price/SKU/name is **never** mutated.

---

## 7. Inventory Ledger Semantics

- Never uses naïve `stock += qty` additions.
- Reuses authoritative `stockMovementRepo.sumQuantityChangeByProductIdForInvoice(firmId, invNo, invId)` to compute exact physical deltas.
- Restores physical stock in source firm with movement type `INVOICE_MIGRATION_REVERSAL`.
- Deducts physical stock in target firm with movement type `INVOICE_SALE`.
- Handles shortage clamping, service items (0 deduction), and unlinked Saved Items (0 deduction).

---

## 8. Forecast & KPI Synchronization

- Migrations immediately reflect in sales forecasting tables.
- Source firm sales contributions removed via `forecastTransactionRepo.deleteAllByFirmIdAndSourceTypeAndSourceHeaderId(sourceFirmId, "SALE", invoiceId)`.
- Target firm sales contributions created via `forecastIngestionService.syncInvoice(invoice)`.
- Daily aggregation tables updated for affected products via `forecastAggregationService.aggregateProductDailyMetrics(...)`.

---

## 9. Quotation / Converted Invoice Decoupling

- `Invoice.convertedInvoiceId` pointing to an estimate/quotation in Firm $A$ is detached (`null`) on forward migration to prevent invalid cross-firm references.
- Original estimate ID preserved in pre-migration snapshot.
- On revert to Firm $A$, `convertedInvoiceId` is restored if the estimate still exists in Firm $A$.

---

## 10. Reminder Re-homing

- When an invoice migrates, associated reminders (`Reminder.invoiceId == id`) are updated:
  - `Reminder.firmId = targetFirmId`
  - `Reminder.customerId = resolvedTargetCustomer.id`
- On revert, reminders are restored to source firm and original customer ID.

---

## 11. Pre-Migration Snapshot Structure

CLOB JSON stored in `InvoiceMigrationRecord.preMigrationSnapshot`:

```json
{
  "snapshotVersion": 1,
  "snapshotTimestamp": "2026-10-08T19:25:33.078",
  "sourceFirmId": 193,
  "invoiceId": 449,
  "invoiceNumber": "INV-0001",
  "status": "UNPAID",
  "invoiceDate": "2026-10-08T19:00:00",
  "dueDate": "2026-10-22",
  "convertedInvoiceId": null,
  "paid": false,
  "subtotalWithoutTax": 5000.00,
  "totalTax": 900.00,
  "totalDiscount": 0.00,
  "totalAmount": 5900.00,
  "customer": {
    "customerId": 481,
    "name": "Live Alpha Customer",
    "phone": "9111111111",
    "gstin": "27CUSTA1234A1Z1"
  },
  "items": [
    {
      "itemId": 3937,
      "productId": 353,
      "productName": "Super Gadget",
      "qty": 5,
      "pricePerUnit": 1000.00,
      "taxableAmount": 5000.00,
      "gstPercent": 18.00,
      "gstAmount": 900.00,
      "lineTotal": 5900.00,
      "physicalStockDeducted": 5.000
    }
  ]
}
```

---

## 12. Migration History Chain

The migration history represents an immutable ledger of transitions:

```
[Record 1] Firm 193 (INV-0001) -> Firm 194 (INV-0001) | isRevert=false | status=REVERTED
      |
[Record 2] Firm 194 (INV-0002) -> Firm 193 (INV-0002) | isRevert=true  | status=SUPERSEDED (parent=1)
      |
[Record 3] Firm 193 (INV-0002) -> Firm 195 (INV-0001) | isRevert=false | status=ACTIVE
```

---

## 13. Concurrency & Locking

- Row-level `PESSIMISTIC_WRITE` lock acquired via `invoiceRepo.findByIdForUpdate(id)`.
- Protects against concurrent migrations, concurrent updates, concurrent cancellations, and concurrent payments.
- Idempotency verified after acquiring lock (`if (invoice.getFirmId().equals(targetFirmId)) throw 409`).

---

## 14. REST API Contract

| Method | Endpoint | Description | Status Codes |
|---|---|---|---|
| `GET` | `/api/invoices/{id}/migration-preview?targetFirmId={firmId}` | Read-only eligibility & candidate preview | 200, 400, 404 |
| `POST` | `/api/invoices/{id}/migrate` | Atomic forward migration | 200, 400, 404, 409 |
| `POST` | `/api/invoices/{id}/revert-migration` | Snapshot restoration forward revert | 200, 400, 404, 409 |
| `GET` | `/api/invoices/{id}/migration-history` | Immutable audit trail | 200, 404 |

---

## 15. Frontend Workflow & Multi-Tab Isolation

1. **Multi-Tab Isolation:** Active firm stored strictly in `sessionStorage.getItem('currentFirmId')`. `localStorage` firm bleed eliminated.
2. **Cross-Firm Banner Notice:** When opening an invoice whose `firmId` differs from active tab firm, an alert banner displays:
   - *"This invoice belongs to Firm Alpha. Your active tab is Firm Beta."*
   - Offers action buttons: **Migrate to Firm Beta** and **Switch Active Tab to Firm Alpha**.
3. **Migration Modal Dialog:**
   - Displays source/target firms, estimated invoice number, customer candidate dropdown, product resolution mapping, stock adjustments, and frozen rate notices.
   - Prevents duplicate clicks by showing a loading spinner and disabling action buttons during execution.

---

## 16. Live Verification Results

Live HTTP verification executed on local running daemon (`http://127.0.0.1:28080`):

| Test Case | Method / Endpoint | Expected Result | Actual Result | Status |
|---|---|---|---|---|
| **Auth Clearance** | `POST /api/auth/login` | Returns session token | `token: 06146156-...` | **PASS** |
| **Firm Creation** | `POST /api/firm` | Created Alpha (193), Beta (194), Gamma (195) | Firms 193, 194, 195 created | **PASS** |
| **Alpha Invoice** | `POST /api/invoices` | Invoice 449 in Firm 193 (5 units of Prod 353) | INV-0001 created, Prod 353 stock: 50 -> 45 | **PASS** |
| **Preview** | `GET .../449/migration-preview?targetFirmId=194` | Eligible: true, SKU match, stock ded: 5 | Candidate resolved, 0 payments, 0 returns | **PASS** |
| **Forward Migration** | `POST .../449/migrate` (Target: 194) | Invoice moved to 194, Prod 353 restored (50), Prod 354 deducted (15) | Firm 194 INV-0001 created, Stock A=50, B=15 | **PASS** |
| **Revert Migration** | `POST .../449/revert-migration` | Invoice moved back to 193 with NEW number INV-0002, Prod 354 restored (20), Prod 353 deducted (45) | Firm 193 INV-0002 created, Stock A=45, B=20 | **PASS** |
| **Multi-Hop Migration** | `POST .../449/migrate` (Target: 195) | Invoice moved to 195, Prod 353 restored (50), Saved Item fallback in 195 | Firm 195 INV-0001, product=null, Stock A=50 | **PASS** |
| **Audit Chain** | `GET .../449/migration-history` | 3 immutable records (REVERTED, SUPERSEDED, ACTIVE) | Complete 3-link chain verified | **PASS** |
| **Same Firm Rejection** | `POST .../449/migrate` (Target: 195) | HTTP 409 Conflict | `{"error":"...already belongs...","status":409}` | **PASS** |
| **Invalid Firm Rejection** | `POST .../449/migrate` (Target: 99999) | HTTP 400 Bad Request | `{"error":"...not found...","status":400}` | **PASS** |
| **Invalid Invoice Rejection** | `POST .../999999/migrate` | HTTP 404 Not Found | `{"error":"...not found...","status":404}` | **PASS** |
| **Update Tenant Integrity**| `PUT /api/invoices/449` (with foreign product 354) | Reject cross-firm product reference | `{"error":"Product ... belongs to a different firm."}` | **PASS** |

---

## 17. Test Suite Summary

- **Unit Tests (`InvoiceMigrationServiceUnitTest`):** **17/17 tests passed** (1.658s)
  - `testPreviewMigration_Eligible` (PASS)
  - `testPreviewMigration_BlockedByPayments` (PASS)
  - `testPreviewMigration_BlockedByReturns` (PASS)
  - `testMigrateInvoice_Success` (PASS)
  - `testRevertMigration_Success` (PASS)
  - `testMigrateInvoice_SavedItemFallback` (PASS)
  - `testMigrateInvoice_BlockedWhenCancelled` (PASS)
  - `testMigrateInvoice_BlockedWhenSameFirm` (PASS)
  - `testMigrateInvoice_RemindersMigrated` (PASS)
  - `testCustomerResolution_GstinOnlyMatch` (PASS)
  - `testCustomerResolution_PhoneOnlyMatch` (PASS)
  - `testCustomerResolution_MultipleMatchesAmbiguous` (PASS)
  - `testProductResolution_MultipleCandidatesAmbiguous` (PASS)
  - `testInventoryRestoration_SourceShortageClamping` (PASS)
  - `testRevert_BlockedByPaymentInTarget` (PASS)
  - `testRevert_BlockedByReturnInTarget` (PASS)
  - `testMigrationHistory_Retrieval` (PASS)

- **Integration Tests (`CrossFirmInvoiceMigrationIntegrationTest`):** **11/11 tests passed** (7.057s)
  - `testForwardMigration_Success_InventoryReversedInSource_DeductedInTarget_CustomerResolved` (PASS)
  - `testForwardMigration_SavedItemFallback_WhenNoTargetProductExists` (PASS)
  - `testForwardMigration_BlockedWhenInvoiceHasPayments` (PASS)
  - `testForwardMigration_BlockedWhenInvoiceHasReturns` (PASS)
  - `testForwardMigration_BlockedWhenTargetFirmIsSame` (PASS)
  - `testRevertMigration_RestoresExactSnapshot_AllocatesNewMonotonicNumber` (PASS)
  - `testRevertMigration_WhenTargetInvoiceWasEdited_RestoresSourceSnapshotAndReversesTargetLedger` (PASS)
  - `testRevertMigration_BlockedWhenPaymentAddedInTargetFirm` (PASS)
  - `testMultiHopMigrationChain_A_to_B_to_A_to_C` (PASS)
  - `testInvoiceUpdate_RejectsForeignProduct` (PASS)
  - `testMigrationTransactionRollback_WhenExceptionThrownMidTransaction` (PASS)

- **Combined Migration Suite:** **28/28 tests passed (0 failures, 0 errors, 0 skipped, BUILD SUCCESS)**.
- **Maven WAR Build:** Clean compilation of 315 source files and 131 test files with 0 errors.

---

## 18. Conclusion

IMPLEMENTATION COMPLETE — VERIFIED COMPLETE
