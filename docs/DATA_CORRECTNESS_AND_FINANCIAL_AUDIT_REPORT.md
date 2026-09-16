# Comprehensive Data Correctness & Financial Calculation Audit Report

**Date:** September 7, 2026  
**Subject:** Repository-Wide Audit on Financial Data Correctness, Edge Cases, Ledger Integrity, and Stock Tracking  
**Target Repository:** Simple-Billing (BillSoft & RupeeCRM)  
**Status:** COMPLETE AUDIT & IMPLEMENTATION BLUEPRINT  

---

## 1. Executive Summary & Root Cause Analysis

### 1.1 The Reported Anomaly Trace
**User Scenario:**
1. Created an Invoice of **₹1,200**.
2. Processed a Sales Return (Credit Note) of **₹200** for returned items.
3. Marked the Invoice as **Paid**.
4. **Observed Failures**:
   - KPI cards reflected **₹1,200** as Paid instead of **₹1,000**.
   - Before being marked paid (or when partially paid with ₹1,000), the system showed **₹200 pending** despite the customer owing ₹0 net.

### 1.2 Exact Code Root Causes (Verified in Source Code)

#### Root Cause 1: Invoice Analytics Completely Ignores Sales Returns / Credit Notes
- **File:** `billsoft/src/main/java/com/billing/simple/billsoft/service/InvoiceService.java` (`getFirmAnalytics` lines 956–983 and `getFirmStats` lines 1105–1123)
- **Defective Code:**
  ```java
  BigDecimal amt = nz(inv.getTotalAmount()); // 1200
  totalBusiness = totalBusiness.add(amt);    // totalBusiness += 1200 (Gross instead of Net 1000)

  BigDecimal paidForInv = invoicePaymentMap.getOrDefault(inv.getId(), BigDecimal.ZERO);
  if (paidForInv.compareTo(BigDecimal.ZERO) == 0 && Boolean.TRUE.equals(inv.getPaid())) {
      paidForInv = amt; // When marked paid, paidForInv becomes 1200!
  }
  if (paidForInv.compareTo(amt) > 0) {
      paidForInv = amt;
  }
  BigDecimal pendingForInv = amt.subtract(paidForInv); // 1200 - 1000 = 200 pending!
  totalPaid = totalPaid.add(paidForInv);
  totalPending = totalPending.add(pendingForInv);
  ```
- **Consequence:** `InvoiceService` never loads or queries `SalesReturnRepository`. When ₹200 is returned, `inv.getTotalAmount()` remains ₹1,200. If the customer pays the legitimate net amount of ₹1,000 via `InvoicePayment`, the pending amount is calculated as `₹1200 - ₹1000 = ₹200`. If marked paid via boolean toggle, `paidForInv` defaults to gross ₹1,200!

#### Root Cause 2: Payment Settlement Logic Compares Against Gross Amount
- **File:** `billsoft/src/main/java/com/billing/simple/billsoft/service/InvoiceService.java` (`recordPayment` lines 1327–1340)
- **Defective Code:**
  ```java
  BigDecimal invoiceTotal = invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO;
  invoice.setPaid(totalPaid.compareTo(invoiceTotal) >= 0); // Compares against 1200, ignoring 200 return
  ```
- **Consequence:** If an invoice of ₹1,200 has a ₹200 return, the customer only needs to pay ₹1,000. But `recordPayment(1000)` checks `1000 >= 1200` (false), leaving `invoice.paid = false` and status `UNPAID` / `PARTIAL`.

#### Root Cause 3: Customer Statements & Ledgers Omit Credit Notes
- **File:** `billsoft/src/main/java/com/billing/simple/billsoft/service/StatementServiceImpl.java` (`getCustomerStatement` lines 88–233)
- **Defective Code:**
  The ledger builder aggregates `INVOICE` debits and `PAYMENT` credits. It has **zero integration** with `SalesReturnRepository`.
- **Consequence:** Customer ledger reports an invoice debit of ₹1,200 and a payment credit of ₹1,000, leaving a false overdue closing balance of ₹200 on the official PDF statement.

#### Root Cause 4: Phantom Inventory Duplication on Cancellation/Deletion of Invoices with Returns
- **File:** `billsoft/src/main/java/com/billing/simple/billsoft/service/InvoiceService.java` (`updateStatus(CANCELLED)` lines 623–640 and `delete` lines 480–493)
- **Defective Code:**
  ```java
  // On invoice cancellation / deletion:
  for (InvoiceItem it : i.getItems()) {
      productService.recordStockMovement(it.getProduct().getId(), ..., qty, "INVOICE_CANCELLED", ...);
  }
  ```
- **Consequence:** When the invoice was created, -10 units were deducted. When 2 units were returned, `createSalesReturn` credited +2 units back to stock (net -8). If the user subsequently cancels or deletes the invoice, the code restores the full original 10 units (+10), resulting in +12 total units returned (+2 phantom stock created out of thin air).

---

## 2. Systematic Financial Math & Data Contract Definitions

To ensure application-wide mathematical correctness across all modules, we define the following standardized financial formulas:

| Metric | Accounting Definition | Formula |
| :--- | :--- | :--- |
| **Gross Invoiced Volume** | Total initial invoiced amount | $\sum \text{Invoice.totalAmount}$ (where status $\neq$ `ESTIMATE`, `DRAFT`, `CANCELLED`) |
| **Total Sales Returns** | Total credit note refund volume | $\sum \text{SalesReturn.totalRefundAmount}$ |
| **Net Invoiced Revenue (Realized Business)** | Actual net billable volume | $\text{Gross Invoiced} - \text{Total Sales Returns}$ |
| **Effective Invoice Receivable** | Outstanding billable on single invoice | $\max(0, \text{Invoice.totalAmount} - \sum \text{SalesReturn.refundAmount})$ |
| **Effective Invoice Paid** | Total cash collected toward invoice | $\min(\text{Effective Invoice Receivable}, \sum \text{InvoicePayment.amount})$ (or Effective Receivable if `paid == true`) |
| **Effective Invoice Pending** | Real unpaid balance on invoice | $\max(0, \text{Effective Invoice Receivable} - \text{Effective Invoice Paid})$ |
| **Invoice Settlement Status** | Automatic status transition | If $\text{Effective Invoice Pending} == 0 \implies \text{PAID}$<br>If $\text{Effective Invoice Paid} > 0 \implies \text{PARTIAL}$<br>If $\text{Effective Invoice Paid} == 0 \implies \text{UNPAID}$ |
| **Net Stock Deduction** | Real inventory sold | $\text{InvoiceItem.qty} - \sum \text{SalesReturnItem.returnQty}$ |

---

## 3. Comprehensive Edge-Case Vulnerability Matrix

The table below catalogs all edge cases identified across the application, their severity, current defect behavior, and the required fix.

| ID | Module | Scenario / Edge Case | Current Behavior (Defect) | Impact | Required Remediation |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **EC-01** | **Invoices & Analytics** | Sales Return created on an unpaid invoice, customer later pays net balance. | Pending amount continues to show the return amount as unpaid (`pending = 200`). | 🔴 High | Deduct `sum(SalesReturn)` from invoice total amount when computing receivable and pending. |
| **EC-02** | **Invoices & Analytics** | Sales Return created on a fully paid invoice (refund issued or ledger credit). | KPI total business and total paid show gross amount (₹1,200 instead of ₹1,000 net). | 🔴 High | Net out sales returns from `totalBusiness` and clamp `totalPaid` to net invoice receivable. |
| **EC-03** | **Customer Statement** | Customer statement generated for a customer with sales returns / credit notes. | Statement only lists `INVOICE` and `PAYMENT`. Returns are omitted; closing balance is incorrect. | 🔴 High | Add `CREDIT_NOTE` / `SALES_RETURN` ledger events into `StatementServiceImpl.getCustomerStatement`. |
| **EC-04** | **Inventory / Stock** | Invoice cancelled or deleted after items were already returned via Credit Note. | Stock restoration restores full original quantity, duplicating inventory (+2 phantom stock). | 🔴 High | Calculate unreturned quantity $(\text{originalQty} - \text{returnedQty})$ before restoring stock on cancel/delete. |
| **EC-05** | **Payments** | Payment recorded via `recordPayment()` after partial sales return. | Auto-paid detection fails because it checks `totalPaid >= invoice.totalAmount`. | 🟠 Med | Change auto-settlement condition to `totalPaid >= (invoice.totalAmount - totalReturns)`. |
| **EC-06** | **Invoices & Status** | Invoice marked paid via `PUT /api/invoices/{id}/paid?paid=true` vs `InvoicePayment`. | Boolean toggle doesn't create `InvoicePayment` row; date-filtered analytics/cashflow desyncs. | 🟠 Med | Standardize boolean vs payment rows or ensure analytics date filters fallback seamlessly. |
| **EC-07** | **GST Reports** | Sales return with tax vs tax-excluded return (`excludeTax = true` / penalty). | GST breakdown reports gross tax without deducting credit note GST (GSTR-1 Section 9B mismatch). | 🟠 Med | Net out `SalesReturn.taxAmount` from GST output tax breakdown. |
| **EC-08** | **Vendor / Party** | Purchase Order cancelled after partial payment recorded. | Party financial summary may still count payments or fail to reflect cancellation correctly. | 🟡 Low | Ensure cancelled PO payments are treated as vendor advance credit in party statement. |
| **EC-09** | **Vendor / Party** | Vendor statement across date boundaries where opening balance includes prior POs. | Verified correct in `PartyServiceImpl`, but `StatementServiceImpl` needs consistent ledger sorting. | 🟡 Low | Ensure chronological tie-breaking by transaction order in statement ledgers. |
| **EC-10** | **Quotation Pipeline** | Quotation converted to invoice, original quotation remains in pipeline KPI. | If status not updated to `CONVERTED`, quote pipeline value is artificially inflated. | 🟡 Low | Set quotation status to `CONVERTED` and exclude `CONVERTED` from pipeline KPI valuation. |

---

## 4. Architectural Remediation & Implementation Plan

### Phase 1: Database & Repository Query Layer
1. **SalesReturnRepository**:
   - Add batch aggregation query:
     ```java
     @Query("SELECT sr.invoice.id, SUM(COALESCE(sr.totalRefundAmount, 0)) FROM SalesReturn sr WHERE sr.firmId = :firmId GROUP BY sr.invoice.id")
     List<Object[]> sumRefundTotalsByInvoiceForFirm(@Param("firmId") Long firmId);
     ```
   - Add customer return totals:
     ```java
     @Query("SELECT sr.customer.id, SUM(COALESCE(sr.totalRefundAmount, 0)) FROM SalesReturn sr WHERE sr.firmId = :firmId GROUP BY sr.customer.id")
     List<Object[]> sumRefundTotalsByCustomerForFirm(@Param("firmId") Long firmId);
     ```

2. **SalesReturnItemRepository**:
   - Add query to get returned quantities by `invoiceId` and `productId`/`invoiceItemId` to prevent phantom stock on invoice cancellation.

### Phase 2: Invoice Service & Analytics Standardization
1. **`InvoiceService.java#getFirmAnalytics` and `getFirmStats`**:
   - Query all sales return totals for the firm in a single O(N) map `Map<Long, BigDecimal> returnMap`.
   - Compute `effectiveReceivable = inv.getTotalAmount().subtract(returnMap.getOrDefault(inv.getId(), BigDecimal.ZERO))`.
   - Set `totalBusiness = totalBusiness.add(effectiveReceivable)`.
   - Set `pendingForInv = max(0, effectiveReceivable.subtract(paidForInv))`.
   - Set `paidForInv = min(effectiveReceivable, paidForInv)`.
2. **`InvoiceService.java#recordPayment`**:
   - Fetch returns for the invoice and check `totalPaid.compareTo(effectiveReceivable) >= 0` to set `paid = true` and `status = PAID`.
3. **`InvoiceService.java#updateStatus(CANCELLED)` and `delete(id)`**:
   - Subtract returned quantities before restoring stock: `restorableQty = item.getQty() - returnedQty`.

### Phase 3: Statement & Ledger Integration
1. **`StatementServiceImpl.java#getCustomerStatement`**:
   - Fetch `SalesReturn` entities for the customer within the firm.
   - Insert `CREDIT_NOTE` / `SALES_RETURN` ledger events with `credit = return.getTotalRefundAmount()` and `order = 1`.
   - Update `openingBalance`, `totalBilled`, `totalCredits`, and `closingBalance` to include Credit Notes.
2. **`StatementServiceImpl.java#getFirmStatement`**:
   - Include credit notes in journal entries, top customer stats, and revenue reconciliation.

### Phase 4: Frontend Alignment (`index.html`)
1. In `loadSummary()` and `FirmAnalyticsResponse`, ensure tooltips and labels clarify:
   - **Total Revenue**: Net realized revenue (Gross Invoices minus Credit Notes).
   - **Realization Rate**: $\frac{\text{Total Paid}}{\text{Net Invoiced}} \times 100$.
2. In customer statement modal, render credit note rows with clear `CN-XXXX` reference badges and refund breakdowns.

---

## 5. Verification & Test Plan

1. **Unit & Integration Tests**:
   - Test `InvoiceServiceTest`: Invoice ₹1,200 + Sales Return ₹200 $\implies$ Net Business ₹1,000.
   - Test `InvoicePaymentTest`: Pay ₹1,000 on ₹1,200 invoice with ₹200 return $\implies$ Status becomes `PAID`, pending becomes ₹0.
   - Test `StatementServiceTest`: Customer statement verifies `CREDIT_NOTE` event and exact ₹0 closing balance.
   - Test `StockIntegrityTest`: Invoice 10 units, return 2 units, cancel invoice $\implies$ Total net stock returned to inventory equals exactly 10 units (2 from return + 8 from cancel), avoiding duplicate restoration.
2. **Regression Check**:
   - Run full `./mvnw test` suite to verify all existing and new tests pass without regressions.
