# RupeeCRM — Firm Tab Complete Information Architecture & UI Redesign Plan
## Pre-Implementation Architectural Blueprint & Verification Audit (v2.0)

**Role:** Senior UX Architect, Product Designer & Frontend Architect
**Target Repository:** `Simple-Billing` / RupeeCRM (`billsoft`)
**Scope:** Complete Information Architecture, Interaction Design, and UI Redesign for the **Firm Tab**
**Review Status:** Pre-Implementation Approval Gate (Verified Against Live Codebase)
**Execution Constraint:** READ-ONLY AUDIT & PLANNING — Zero mutations to application source files, tests, configurations, or business logic.

---

## 1. Executive Summary & Problem Diagnosis

### 1.1 Context
The **Firm Tab** is the administrative, commercial, and operational cockpit of RupeeCRM. It governs critical business workflows: replenishment forecasting, product master catalog maintenance, customer receivables management, vendor procurement, pricing profiles and category adjustment matrices, official correspondence, and multi-firm tenant administration.

Previous attempts to remediate UI defects (clipped columns, hidden action buttons, horizontal page scrollbars, nested scroll locks) approached the challenge primarily as **CSS resizing, column coalescing, and arbitrary row budgeting**. Those attempts failed because:
1. **Container Clipping (`overflow: hidden`):** Applying `overflow: hidden` to table containers did not fit content onto the screen; it simply clipped columns 8–10 off-screen, rendering urgent recommendation reasons and primary order actions invisible.
2. **Column Coalescing Bloat:** Merging Name, SKU, and Barcode into Column 1, or Price, Cost, and Margin into Column 3 reduced column counts but crammed mismatched font sizes, badges, and labels into dense cells that wrap unpredictably when browser zoom or data length varies.
3. **Multi-Level Navigation Sprawl:** Fragmented sub-tabs (an 8-button top bar, nested component toggles, secondary tabs, and tertiary segmented bars) consume 320px–420px of vertical space before a single data row renders.
4. **Data Monoculture:** Every screen was forced into either an unconstrained 10–14 column data table or an array of vertical cards, regardless of whether the task was fast discovery, urgent decision-making, high-volume data entry, or deep financial inspection.

### 1.2 Core Architectural Principles
- **Task-Oriented Progressive Disclosure:** Expose only primary decision-making attributes on the main list view. Move secondary diagnostics, mathematical formulas, historical velocity charts, and advanced configurations into dedicated slide-out drawers, split-screen workspaces, or modal dialogs.
- **Strict Viewport Containment Without Data Concealment:** Eliminate page-level vertical and horizontal scrolling on supported desktop viewports ($\ge 1024\times768$) while never hiding actionable data with `overflow: hidden`. Long text truncates with `text-overflow: ellipsis` and native browser tooltips (`title`); overflow data is accessible via drawers.
- **100% Behavioral and Data Parity:** Preserve every existing field, calculation, validation rule, keyboard interaction, permission boundary, and tenant context. No feature is deleted or degraded to save screen space.
- **Stable Navigation & Cross-Tab Contract:** Preserve all existing top-level navigation routes and event-driven deep links (`billsoft:navigate-subtab` and `window.__billsoftPendingNav`).

---

## 2. Verification of Existing Capabilities (Evidence-Backed Audit)

To ensure this redesign remains strictly a **UI and Information Architecture refinement**—and does not invent features, make unsupported claims, or silently morph into backend feature development—every major capability has been audited directly against the codebase.

### Classification Taxonomy
- **[VERIFIED-EXISTING]**: Implemented in current source code and verified by line references.
- **[PARTIAL-EXISTING]**: Partially implemented or split across components; verified by source inspection.
- **[PROPOSED-UI-CHANGE]**: Structural or interaction redesign applied to an existing, verified workflow.
- **[NEW-FEATURE-OUT-OF-SCOPE]**: New business functionality not in the current codebase (deferred to future backlog).

---

### Audit Findings for the 9 Core Capabilities

```
┌────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│ Capability 1: Multi-Vendor PO Generation & Forecast Replenishment                                                      │
├────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Status: [VERIFIED-EXISTING]                                                                                            │
│ Source Reference: billsoft/src/main/webapp/js/components/ProductIntelligence.js:1075-1550                              │
│ Implementation Details:                                                                                                │
│ - Backend API: API.forecast.getRecommendations({ urgency, page, size }) fetches algorithmic recommendations.           │
│ - Recommendation Fields: productId, productName, sku, unit, currentStock, incomingStock, minStockLevel,               │
│   forecast30Days, leadTimeDays, reviewPeriodDays, safetyStock, targetStockLevel, netInventoryPosition,                 │
│   recommendedOrderQty, estimatedPurchaseValue, urgency (CRITICAL, URGENT, WARNING, HEALTHY), reason (FORECAST,         │
│   MINIMUM_STOCK, BOTH, HEALTHY), preferredPartyId, preferredPartyName, unitCost.                                       │
│ - Multi-Vendor PO Generator: showOrderWorkspace state manages a 3-step creation flow:                                  │
│   1. Item Selection & Configuration (lines 1240-1386)                                                                  │
│   2. Vendor Grouping & Review (lines 1428-1487) - Automatically groups items by resolved vendor via                   │
│      resolveVendorForProduct() (Preferred -> Vendor History -> Unassigned).                                            │
│   3. Batch Execution (lines 1490-1550) - Calls API.purchaseOrders.createBatch(payload) with clientRequestId           │
│      idempotency and tracks recentlyOrderedMap to prevent duplicate session orders.                                    │
│ Proposed UI Change: [PROPOSED-UI-CHANGE] Replace the cluttered 10-column table with a clean 5-column Primary           │
│ Replenishment Table paired with a slide-out Product Intelligence Drawer and a docked bottom PO batch tray.             │
└────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│ Capability 2: Product Intelligence Drawer & Recommendation Diagnostics                                                 │
├────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Status: [VERIFIED-EXISTING]                                                                                            │
│ Source Reference: billsoft/src/main/webapp/js/components/ProductIntelligence.js:327-778                                │
│ Implementation Details:                                                                                                │
│ - Component: ProductDetailDrawer({ productId, onClose, onUseRecommendation, onViewSmartReorder })                      │
│ - APIs Called: API.forecast.getInsights(productId), API.forecast.getTrends(productId, '90d'),                        │
│   API.forecast.getTrajectory(productId), API.forecast.getRestockExplanation(productId),                                │
│   API.forecast.getConfig(productId).                                                                                   │
│ - Diagnostic Fields: totalPlanningHorizonDemand, safetyStock, targetStockLevel, netInventoryPosition,                  │
│   finalRecommendedOrderQty, explanationText, weightedAverageSellingPrice (WASP), stockCoverageDays,                   │
│   forecastConfidenceLevel, forecastConfidenceScore, selectedModel.                                                     │
│ - Native Chart: DemandForecastChart renders an SVG statistical projection with 30D/90D/180D/365D horizon toggles.      │
│ - Parameter Configuration: showConfigModal allows updating leadTimeDays, reviewPeriodDays, minimumOrderQty,            │
│   packSize, and safetyStockOverride via API.forecast.updateConfig(productId, payload).                                │
│ Proposed UI Change: [PROPOSED-UI-CHANGE] Ensure the drawer is responsive (width: 580px; max-width: 90vw),              │
│ dismissible via Escape/backdrop click, and restores focus to the invoking table row upon close.                        │
└────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│ Capability 3: Customer 360 Tabs, Batch Settlement & Payment Reversal                                                   │
├────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Status: [VERIFIED-EXISTING]                                                                                            │
│ Source Reference: billsoft/src/main/webapp/index.html:19317-19590                                                      │
│ Implementation Details:                                                                                                │
│ - Component: Customer360Modal({ customer, initialTab, onClose, onBill, onSettle, onStatement, onRemind })              │
│ - API Called: API.customers.get360(customer.id) returns netBalance, overdueAmount, overdueInvoiceCount,                │
│   unallocatedCredit, totalBilled, totalPaid, invoiceCount, outstandingInvoices, recentInvoices, recentPayments.        │
│ - Tab 1 ('outstanding'): Checkbox multi-select (selectedInvoices) allows batch-selecting unpaid invoices,              │
│   recalculates the selected total dynamically, and calls onSettle(customer, selectedInvoiceIds).                       │
│ - Tab 2 ('invoices'): Lists recent invoices with status, dates, totals, paid amounts, and navigation triggers.         │
│ - Tab 3 ('payments'): Lists payment records with handleDeletePayment(pmtId) calling                                    │
│   API.customers.deletePayment(pmtId) with user confirmation to reverse payments and restore invoice balances.          │
│ Proposed UI Change: [PROPOSED-UI-CHANGE] Convert from a centered modal into an accessible right slide-out drawer       │
│ (.firm-slide-drawer) to maximize vertical scanning space and preserve customer list context.                           │
└────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│ Capability 4: Vendor Running Ledger & Advance Adjustment                                                               │
├────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Status: [VERIFIED-EXISTING]                                                                                            │
│ Source Reference: billsoft/src/main/webapp/index.html:21040-21400                                                      │
│ Implementation Details:                                                                                                │
│ - Component: PartyManager() managing vendor records and summaries (API.parties.list(), API.parties.listSummaries()).   │
│ - Ledger View: openLedger(party) opens showLedgerModal displaying date-ordered debits (POs), credits (Payments),      │
│   and running balances. Includes statement PDF download via API.statements.partyPdf(party.id).                         │
│ - Advance Payment Adjustment: adjustModalPayment state triggers API.purchaseOrders.adjustAdvance(poId, {               │
│   paymentId, amount, notes }) to allocate unallocated vendor advance payments against pending POs.                     │
│ - Payment Unlink / Reversal: handleUnadjustPayment(pmtId) calls API.purchaseOrders.unadjustPayment(pmtId);             │
│   confirmDeletePayment() calls API.parties.deletePayment(pmtId).                                                       │
│ Proposed UI Change: [PROPOSED-UI-CHANGE] Convert vendor ledger modal to a slide-out drawer with dedicated date presets │
│ and internal table scrolling, avoiding nested scroll locks.                                                            │
└────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│ Capability 5: Bulk Edit Spreadsheet, Keyboard Navigation & Change-Review Behavior                                      │
├────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Status: [VERIFIED-EXISTING]                                                                                            │
│ Source Reference: billsoft/src/main/webapp/js/components/ProductIntelligence.js:4230-4690, 4840-5235                   │
│ Implementation Details:                                                                                                │
│ - State: isBulkEditMode (toggle), draftChanges ({ [prodId]: { field: newVal } }), showReviewModal, bulkTableRef.       │
│ - Editable Fields: name, sku, barcode, category, price, costPrice, minStockLevel, unit, hsnCode, gstPercentage,        │
│   preferredPartyId, description, trackInInventory, openingStock.                                                       │
│ - Keyboard Gestures: handleBulkKeyDown(e, rowIdx, colIdx) handles Enter (navigates down to same column in next row),    │
│   Escape (blurs cell), and Ctrl+Enter / Cmd+Enter (opens review modal).                                                │
│ - Diff Review & Save: showReviewModal displays exact before/after diffs of modified fields; handleConfirmBulkSave()    │
│   dispatches API.products.bulkUpdate(updates).                                                                         │
│ Proposed UI Change: [PROPOSED-UI-CHANGE] Decouple the standard 6-column browse view from this full-width bulk edit    │
│ workspace, eliminating the 13-column clutter in standard viewing mode while preserving 100% of the spreadsheet tools. │
└────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│ Capability 6: Purchase Order Creation, Status Transitions & Key Gestures                                               │
├────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Status: [VERIFIED-EXISTING]                                                                                            │
│ Source Reference: billsoft/src/main/webapp/index.html:23510-24300                                                      │
│ Implementation Details:                                                                                                │
│ - PO Lifecycle Statuses: DRAFT, ISSUED, RECEIVED, CANCELLED. Status update dropdown in list view.                     │
│ - Payment Statuses: YET_TO_PAY, PAID, PARTIAL. Managed with payment mode (Bank Transfer, UPI, Cash, Cheque).           │
│ - PO Creator (view === 'create'): Line items table with description, HSN, quantity, unit, rate, discount, GST %,       │
│   line total. Supports inline vendor quick-add (showInlinePartyModal).                                                 │
│ - Keyboard Workflow: Pressing Tab in PO creator opens showCompletionPopup (1 = Issue, 2 = Issue & Download PDF,       │
│   3 = Save Draft, Esc = Cancel). Arrow keys navigate between item cells (focusCell).                                   │
│ Proposed UI Change: [PROPOSED-UI-CHANGE] Replace the rigid side-by-side split view with an adaptive, two-phase layout: │
│ full-width line items grid on top; sticky bottom dock for vendor info, tax calculation, and order actions.             │
└────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│ Capability 7: Pricing Matrix Editing, Validation & Keyboard Shortcuts                                                  │
├────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Status: [VERIFIED-EXISTING]                                                                                            │
│ Source Reference: billsoft/src/main/webapp/js/components/ProductIntelligence.js:3551-4115                              │
│ Implementation Details:                                                                                                │
│ - Component: PricingProfileManager() with 3 sub-tabs: PROFILES, MATRIX, SIMULATOR.                                     │
│ - Matrix Navigation: handleMatrixKeyDown() handles ArrowUp, ArrowDown, ArrowLeft, ArrowRight, Enter, Shift+Enter,      │
│   and Ctrl+Enter to move between category rows and profile columns and save.                                           │
│ - Rule Adjustment Types: PERCENT (%) and FIXED (₹), stored in matrixEdits state.                                       │
│ - Batch Save: API.categoryPricingRules.save(rulesList) persists adjustments.                                           │
│ - Live Simulator: Selects profile and computes effective selling prices against product base prices.                   │
│ Proposed UI Change: [PROPOSED-UI-CHANGE] Add sticky Category headers (position: sticky; left: 0) and container        │
│ boundaries so matrix cells remain accessible without table clipping when $>4$ pricing profiles exist.                 │
└────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│ Capability 8: Multi-Firm Switching & Unsaved-Change Protection                                                         │
├────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Status: [VERIFIED-EXISTING]                                                                                            │
│ Source Reference: billsoft/src/main/webapp/index.html:22029-22135                                                      │
│ Implementation Details:                                                                                                │
│ - State: firms, selectedFirmId, currentFirm, data, initialData, isDirty (compares JSON.stringify(data) vs initialData).│
│ - Guarded Switching: handleSwitchFirm(firmId) detects isDirty; if true, prompts confirmation:                          │
│   "You have unsaved changes in firm settings. Switch active firm anyway?".                                             │
│ - Context Update: Updates API.firmId, dispatches billsoft:firm-changed event, updates session storage.                 │
│ Proposed UI Change: [PROPOSED-UI-CHANGE] Maintain the multi-firm deck layout while compacting card padding.            │
└────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│ Capability 9: Business Profile Settings & Live Invoice Header Preview                                                  │
├────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Status: [VERIFIED-EXISTING]                                                                                            │
│ Source Reference: billsoft/src/main/webapp/index.html:22186-22450                                                      │
│ Implementation Details:                                                                                                │
│ - Left Column: Subnav tabs for 'identity', 'contact', 'banking', 'whatsapp', 'footer' with logo base64 upload,          │
│   UPI QR generator, GSTIN/PAN inputs, and invoice terms/notes.                                                         │
│ - Right Column: Live document preview rendering the firm's invoice header in real-time as fields are edited.           │
│ Proposed UI Change: [PROPOSED-UI-CHANGE] Retain the 2-column layout; budget left settings height to viewport height    │
│ to eliminate outer page scroll.                                                                                        │
└────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Navigation Architecture & Route Preservation

### 3.1 Preservation of Existing Top-Level Subtabs
In `index.html:5951-5970`, `FirmPage` renders **8 top-level sub-tabs**.

To avoid breaking external links, omnibar shortcuts, or cross-component event listeners (`billsoft:navigate-subtab`), **all 8 canonical sub-tabs remain active and accessible at the top level**:

```
[RupeeCRM Main Navigation -> FIRM TAB]
├── 1. 📦 Inventory           (tab === 'inventory' -> ProductManager)
├── 2. 🏷️ Products            (tab === 'products'  -> ProductIntelligenceHub)
├── 3. 👥 Customers           (tab === 'customers' -> CustomerManager)
├── 4. 🤝 Parties (Vendors)   (tab === 'parties'   -> PartyManager)
├── 5. 📋 Purchase Orders     (tab === 'orders'    -> PurchaseOrderManager)
├── 6. ✉️ Letter Pad          (tab === 'letters'   -> LetterPadManager)
├── 7. 📊 Statements & Ledgers(tab === 'statements'-> StatementManager)
└── 8. 🏢 Business Profile    (tab === 'profile'   -> FirmProfile)
```

### 3.2 Canonical Navigation Mapping Table

| Existing Top-Level Tab | Component Rendered | Existing Sub-Views & Modals | Proposed Destination & Architectural Paradigm | Navigation & Route Impact |
|---|---|---|---|---|
| **`📦 Inventory`** | `ProductManager` (`index.html:17591`) | - `STANDARD` Stock List<br>- `SMART_REORDER` (`SmartReorderView`)<br>- Quick Stock Modal<br>- Movement History Modal<br>- Deduplication Modal | - **Standard Inventory:** High-density 6-column stock monitoring with inline `+`/`-` steppers.<br>- **Replenishment Link:** Prominent header toggle to `Smart Reorder Queue`.<br>- **Movement History:** Right slide-out audit drawer.<br>- **Deduplication:** Dedicated tools modal. | **Unchanged Route:** Responds to `page: 'firm', tab: 'inventory'`. Also aliased to `'stock'`, `'catalog'`. |
| **`🏷️ Products`** | `ProductIntelligenceHub` (`ProductIntelligence.js:4212`) | - `INTELLIGENCE` (Master Catalog)<br>- `PRICING` (`PricingProfileManager`)<br>- `SMART_REORDER` (`SmartReorderView`)<br>- Bulk Edit Grid<br>- Bulk Add Modal | - **Catalog Master:** Clean 6-column browse view.<br>- **Bulk Edit Mode:** Dedicated full-width spreadsheet workspace.<br>- **Pricing:** 3-tab segmented controller (Profiles, Matrix, Simulator).<br>- **Smart Reorder:** Embedded replenishment workbench. | **Unchanged Route:** Responds to `page: 'firm', tab: 'products'`. Also aliased to `'intelligence'`, `'forecast'`. |
| **`👥 Customers`** | `CustomerManager` (`index.html:20103`) | - Customer Cards / Table<br>- `Customer360Modal`<br>- Settlement Modal<br>- Statement Dialog<br>- WhatsApp Modal | - **Customer Cockpit:** 5-column receivables table with `CompactActionGroup`.<br>- **Customer 360:** Slide-out drawer with 3 focused tabs (Outstanding Invoices, Recent Invoices, Payments & Credits). | **Unchanged Route:** Responds to `page: 'firm', tab: 'customers'`. Accepts `customerId` deep link to open 360 drawer directly. |
| **`🤝 Parties`** | `PartyManager` (`index.html:21015`) | - Vendor List / Cards<br>- Vendor Create/Edit Modal<br>- Quick Pay Modal<br>- Ledger Modal<br>- Advance Adjust Modal | - **Vendor Workbench:** Table with financial balance badges (Payable vs Advance).<br>- **Ledger:** Right slide-out ledger drawer with date presets.<br>- **Advance Adjustment:** Specialized reallocation modal. | **Unchanged Route:** Responds to `page: 'firm', tab: 'parties'`. Also aliased to `'vendors'`. |
| **`📋 Purchase Orders`** | `PurchaseOrderManager` (`index.html:23510`) | - PO List View (`view: 'list'`)<br>- PO Creator (`view: 'create'`)<br>- Completion Popup (Tab key)<br>- Quick Add Vendor Modal<br>- Print/PDF Modal | - **PO List View:** Status filter strip, high-density table.<br>- **PO Creator:** Adaptive 2-phase layout (full-width line items grid on top; docked totals and vendor metadata below). Preserves Tab completion popup and Arrow-key cell navigation. | **Unchanged Route:** Responds to `page: 'firm', tab: 'orders'`. Also aliased to `'purchases'`, `'po'`, `'paperwork'`. |
| **`✉️ Letter Pad`** | `LetterPadManager` (`index.html:24690`) | - Letter Archive List<br>- Letter Creator<br>- 6 Standard Templates<br>- Letterhead Preview Modal | - **Letter Archive:** Searchable list with recipient and status filters.<br>- **Letterhead Creator:** Split-screen layout (Sender/Recipient setup top; letter body and signatory controls bottom). | **Unchanged Route:** Responds to `page: 'firm', tab: 'letters'`. Also aliased to `'letter-pad'`. |
| **`📊 Statements`** | `StatementManager` (`index.html:25966`) | - Party Statement Mode<br>- Customer Statement Mode<br>- Firm Summary Mode<br>- GST Slabs Table<br>- Chronological Journal | - **Statement Explorer:** 3 hero selector cards.<br>- **Ledger Display:** Enforces internal container scrolling (`max-height: calc(100vh - 360px)`) with 15/25 row pagination budgets to prevent infinite vertical document growth. | **Unchanged Route:** Responds to `page: 'firm', tab: 'statements'`. Also aliased to `'ledgers'`. |
| **`🏢 Business Profile`**| `FirmProfile` (`index.html:22029`) | - Multi-Firm Deck<br>- 5 Settings Tabs<br>- Logo Upload<br>- Live Invoice Header Preview | - **Multi-Firm Management Deck:** Compact entity switcher.<br>- **Settings Workspace:** 2-column layout (tabbed settings left, live invoice header preview right). | **Unchanged Route:** Responds to `page: 'firm', tab: 'profile'`. Also aliased to `'business'`, `'settings'`. |

---

## 4. Corrected Desktop Zero-Scroll & Scrolling Policy Specification

### 4.1 Separate Acceptance Criteria by Scrolling Domain

To eliminate ambiguity, acceptance criteria are divided into **five distinct scrolling domains**:

```
┌───────────────────────────────────────┬──────────────────────────────────────────────────────────────────────────────┐
│ Scrolling Domain                      │ Strict Specification & Acceptance Criteria                                   │
├───────────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────┤
│ 1. Horizontal Overflow & Clipping     │ ZERO TOLERANCE:                                                              │
│                                       │ - document.documentElement.scrollWidth <= window.innerWidth on all viewports.│
│                                       │ - No table cell or container may use overflow: hidden to mask overflowing    │
│                                       │   action buttons or data fields.                                             │
│                                       │ - Long text must use text-overflow: ellipsis with native title tooltips.     │
│                                       │ - Exception: Bulk Edit Mode spreadsheet permits bounded internal horizontal  │
│                                       │   scroll strictly within the table card (.firm-table-card).                  │
├───────────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────┤
│ 2. Document-Level Vertical Scrolling  │ ZERO PAGE SCROLL ON DESKTOP:                                                 │
│                                       │ - document.documentElement.scrollHeight <= window.innerHeight + 2 on desktop │
│                                       │   viewports (width >= 1024px and height >= 700px).                           │
│                                       │ - Global shell elements (#app-root, .app-layout, .main-content) must NEVER   │
│                                       │   receive overflow: hidden or fixed heights. Scoping is strictly confined to │
│                                       │   .firm-viewport-container and .firm-workspace-pane.                         │
│                                       │ - On viewports < 1024px (tablets and phones), document scroll is permitted.  │
├───────────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────┤
│ 3. Nested Table & List Scrolling      │ CONTROLLED & BUDGETED:                                                       │
│                                       │ - Internal scrolling is permitted ONLY inside designated table containers    │
│                                       │   (.table-container) with a calculated max-height:                           │
│                                       │   max-height: calc(100vh - 320px) to calc(100vh - 360px).                    │
│                                       │ - Scrollbars must remain visible, styled, and never trap keyboard focus.     │
├───────────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────┤
│ 4. Dialog, Drawer & Editor Scrolling  │ BOUNDED VIEWPORT OVERLAYS:                                                   │
│                                       │ - Modals must have max-height: 90vh with internal modal-body overflow-y: auto.│
│                                       │ - Slide-out drawers (.firm-slide-drawer) occupy 100vh height with internal   │
│                                       │   overflow-y: auto for diagnostic charts and audit histories.                │
│                                       │ - Background scroll locking applies during modal/drawer display and is       │
│                                       │   restored immediately upon dismissal.                                      │
├───────────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────┤
│ 5. Large Dataset Navigation           │ DELIBERATE PAGINATION BUDGETS:                                               │
│                                       │ - Unbounded datasets (thousands of products, years of ledgers) must NEVER    │
│                                       │   rely on infinite scroll to satisfy zero-scroll rules.                      │
│                                       │ - Standard tables enforce a default pagination budget of 8–15 rows/page on   │
│                                       │   desktop, with options for [8, 15, 25, 50].                                 │
│                                       │ - Docked pagination footers (.firm-docked-pagination) remain pinned at the   │
│                                       │   bottom of the workspace without moving off-screen.                         │
└───────────────────────────────────────┴──────────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Comprehensive Feature-Preservation Matrix

Every discovered field, option, filter, action, and nested control across all 8 subtabs has an explicit destination, access interaction, and verification test case:

| Domain & Screen | Source Component & Line Reference | Existing Field / Control | Proposed Destination | Access Interaction | Permissions & Validation | Verification Test Case |
|---|---|---|---|---|---|---|
| **Smart Reorder** | `ProductIntelligence.js:1850-2070` | Product Name, SKU, Barcode | Reorder Queue: Col 1 (`Product`) | Direct glance; text-overflow ellipsis with tooltip | Read inventory | Search by SKU/Barcode; verify rendering with missing barcode fallback. |
| **Smart Reorder** | `ProductIntelligence.js:1850-2070` | Current Stock, Min Stock Level | Reorder Queue: Col 2 (`Stock / Min`) | Direct glance with visual threshold progress bar | Read inventory | Verify numerical stock matches database inventory count. |
| **Smart Reorder** | `ProductIntelligence.js:1850-2070` | Incoming PO Quantity | Reorder Queue: Col 3 (`Pipeline`) | Direct glance badge; links to active POs | Read POs | Verify sum of active PO quantities matches pipeline badge. |
| **Smart Reorder** | `ProductIntelligence.js:1850-2070` | 30D Forecast Demand | Reorder Queue: Col 4 (`Demand`) & Drawer | Direct glance + ℹ️ popover explanation | Read forecast | Verify statistical demand projection matches forecast model. |
| **Smart Reorder** | `ProductIntelligence.js:327-778` | Replenishment Parameters (Lead Time, Review Period, MOQ, Pack Size, Safety Stock) | `ProductDetailDrawer` -> `⚙️ Params` | Click `[Insights]` -> Click `⚙️ Params` | Edit replenishment config | Modify lead time and pack size; confirm persistence via `PUT /api/forecast/config`. |
| **Smart Reorder** | `ProductIntelligence.js:1850-2070` | Final Recommended Quantity | Reorder Queue: Col 5 (`Recommended`) | Direct glance (bold highlight) | Read forecast | Verify recommended quantity enforces pack size multiple and MOQ. |
| **Smart Reorder** | `ProductIntelligence.js:1850-2070` | Urgency Badge & Reason Badge | Reorder Queue: Col 6 (`Status`) | Direct glance (color-coded badge) | Read forecast | Filter by `CRITICAL`; confirm only critical items render. |
| **Smart Reorder** | `ProductIntelligence.js:327-778` | Demand Trajectory Chart | `ProductDetailDrawer` | Click row or `[Insights]` button | Read forecast | Toggle 30D/90D/180D/365D horizons; verify SVG chart re-renders. |
| **Smart Reorder** | `ProductIntelligence.js:1240-1550` | Multi-Vendor Batch PO Creation | Docked bottom bar -> `Create Purchase Orders` | Click `[+ Order]` -> Click `Generate POs` | Create POs (tenant-isolated) | Select 3 items with 2 different vendors; confirm 2 separate POs are created. |
| **Standard Inventory** | `index.html:18246-18345` | 5 KPI Cards (Total, In Stock, Low Stock, Out of Stock, Services) | Unified `.firm-kpi-ribbon` | 1-row header strip with click-to-filter toggles | Read inventory | Click `Low Stock`; confirm table filters to items below min stock level. |
| **Standard Inventory** | `index.html:17654-17658` | Quick Stock Adjustment (`+`/`-`) | Stock Column Stepper | Hover stock cell -> Click `+` or `-` | Edit stock (creates audit movement) | Click `+`; confirm stock increments by 1 and calls `adjust-stock` API. |
| **Standard Inventory** | `index.html:17627-17632` | Stock Movement Audit History | Context Menu -> `Movement History` Drawer | Row action `⋯` -> `View Movements` | Read stock movements | Verify chronological audit log displays date, delta, reason, and user. |
| **Standard Inventory** | `index.html:17665-17745` | Duplicate Scan & Merge | Inventory Tools Dropdown -> Modal | Click `Tools` -> `Scan Duplicates` | Manage inventory duplicates | Test candidate comparison and merge strategies (`KEEP_PRIMARY`, etc.). |
| **Standard Inventory** | `index.html:17685-17736` | Bulk Category Assignment | Floating bottom action dock | Select checkboxes -> Pick category | Bulk edit products | Select 5 products; assign category; confirm batch update persists. |
| **Product Master** | `ProductIntelligence.js:4230-4690` | 13-Column Inline Editable Table | Dedicated Bulk Edit Mode | Toggle `📋 Bulk Edit Spreadsheet` | Edit catalog master | Navigate cells with Tab/Enter/Arrow keys; verify draft tracking. |
| **Product Master** | `ProductIntelligence.js:4428-4550` | Change Review & Diff Modal | Bulk Edit Dock -> `Review & Save` | Press Ctrl+Enter or click `Review` | Validate bulk updates | Review modal shows exact before/after field diffs; confirm `bulkUpdate` call. |
| **Product Master** | `ProductIntelligence.js:4266-4280` | Re-sync Forecast Models | Catalog Tools -> `🔄 Re-sync Models` | Click button (displays spinner) | Admin forecast execution | Click re-sync; verify toast notification and model count returned. |
| **Customers** | `index.html:20183-20233` | Receivables KPIs (Total Dues, Debtors, Realized, Turnover) | Unified `.firm-kpi-ribbon` | 1-row header ribbon | Read customer metrics | Verify metrics match aggregate sum of `/api/kpis/customers`. |
| **Customers** | `index.html:19317-19590` | Customer 360 Overview | Slide-out `Customer360Drawer` | Click Customer Name or `[360°]` | Read customer financials | Drawer opens with Net Outstanding, Overdue Amount, and Unallocated Credit. |
| **Customers** | `index.html:19491-19530` | Batch Invoice Settlement | Customer 360: Tab 1 (`Outstanding`) | Checkbox multi-select -> `Settle Selected` | Settle invoices | Select 2 invoices; confirm batch settlement applies payment correctly. |
| **Customers** | `index.html:19360-19370` | Payment Reversal / Delete | Customer 360: Tab 3 (`Payments`) | Click `🗑️` on payment record | Reverse payment (admin only) | Confirm dialog warning; verify payment reversed and customer balance updated. |
| **Customers** | `index.html:20510-20530` | WhatsApp Payment Reminder | Row Context Menu -> `💬 WhatsApp` | Click WhatsApp icon | Read customer phone | Verify pre-filled reminder template includes invoice number and balance due. |
| **Parties / Vendors** | `index.html:21360-21382` | Vendor Balance (Payable vs Advance) | Vendor Table: Col 3 (`Balance`) | Direct glance with color badge (Red/Green) | Read vendor balance | Confirm red badge for payable dues and green badge for advance credit. |
| **Parties / Vendors** | `index.html:21040-21295` | Vendor Running Ledger | Slide-out `VendorLedgerDrawer` | Click `[📜 Ledger]` action button | Read vendor ledger | Verify debit, credit, and running balance match statement PDF export. |
| **Parties / Vendors** | `index.html:21250-21277` | Advance Payment Adjustment to PO | Vendor Ledger -> `Adjust Advance` | Click `Adjust Advance` on payment row | Allocate advance to PO | Reallocate ₹5,000 advance to pending PO; confirm balance reconciliation. |
| **Purchase Orders** | `index.html:23510-23700` | PO Creation: Line Items Grid | PO Creator: Upper Main Workspace | Interactive tabular grid with auto-add | Create purchase order | Enter product, qty, rate, GST; verify calculation of subtotal and tax. |
| **Purchase Orders** | `index.html:23513-23521` | PO Creation: Tab Completion Popup | PO Creator: Global Keydown Listener | Press Tab key in creator | Create purchase order | Press Tab; verify completion popup displays (1=Issue, 2=PDF, 3=Draft). |
| **Purchase Orders** | `index.html:23661-23700` | PO Creation: Arrow Key Cell Navigation | PO Items Table | ArrowUp, ArrowDown, ArrowLeft, ArrowRight | PO line item editing | Navigate between Item, Qty, and Rate cells via Arrow keys without mouse. |
| **Pricing Profiles** | `ProductIntelligence.js:3840-3950` | Pricing Profiles Cards & Default Badge | Pricing: Tab 1 (`Profiles`) | Card grid with `★ DEFAULT` badge | Manage pricing profiles | Verify default profile cannot be deleted; create/edit profile persists. |
| **Pricing Profiles** | `ProductIntelligence.js:3953-4034` | Category Pricing Adjustment Matrix | Pricing: Tab 2 (`Category Matrix`) | Sticky category header spreadsheet | Manage pricing rules | Edit margin %; navigate with Arrow keys; press Ctrl+Enter to save matrix. |
| **Pricing Profiles** | `ProductIntelligence.js:4036-4090` | Live Effective Price Simulator | Pricing: Tab 3 (`Live Simulator`) | Profile dropdown + comparison table | Read effective prices | Select wholesale profile; verify base price and effective price match rule. |
| **Letter Pad** | `index.html:24709-24734` | 6 Official Business Templates | Letter Creator: Template Dropdown | Select template from dropdown | Manage letters | Select `Notice`; confirm subject and multi-paragraph body load correctly. |
| **Letter Pad** | `index.html:25178-25210` | Official Letterhead vs Custom Sender | Letter Creator: Sender Setup | Segmented toggle (`Firm` vs `Custom`) | Manage letters | Toggle to `Firm`; verify firm address, GSTIN, and logo load automatically. |
| **Statements** | `index.html:25977-26015` | 3 Hero Mode Selector Cards | Statement Explorer Header | Segmented cards (`Vendor`, `Customer`, `Firm`) | Read statements | Click `Customer`; select customer; verify date-filtered ledger entries. |
| **Statements** | `index.html:26630-26733` | Firm Executive KPIs (6 Cards) | Firm Statement Mode | Compact executive ribbon | Read firm financials | Verify: $Cashflow = Collections - Vendor Outflows$. |
| **Business Profile** | `index.html:22220-22271` | Multi-Firm Corporate Switcher Deck | Profile Top Deck | Click firm card | Switch active firm | Click another firm; verify confirmation prompt if unsaved changes exist. |
| **Business Profile** | `index.html:22273-22450` | 5 Settings Tabs + Live Invoice Preview | 2-Column Profile Workspace | Edit settings on left; glance preview right | Edit firm profile | Change firm name or logo; verify live invoice header preview updates immediately. |

---

## 6. Removal of Unrelated Business-Policy Decisions & Out-of-Scope Backlog

In the initial draft, four questions were posed as pre-implementation considerations. Upon inspection of the codebase, **none of these represent UI prerequisites**. The application already possesses unambiguous, established business logic for all four:

1. **Purchase Order Approval Tiers:**
   - *Existing Behavior:* The codebase implements a direct transition between `DRAFT` and `ISSUED` status (`PurchaseOrderManager`, `index.html:23554`). Stock commitment occurs upon transition to `ISSUED`.
   - *UI Redesign Requirement:* Preserve existing `[Save Draft]` and `[⚡ Issue PO]` buttons exactly as they operate today. Do NOT introduce approval tiers.
2. **Customer Credit Limit Enforcement:**
   - *Existing Behavior:* The backend computes `netBalance` and overdue amounts for display in `Customer360Modal` (`index.html:19419`). Invoice creation does not hard-block based on credit limits.
   - *UI Redesign Requirement:* Display credit status badges prominently in the Customer 360 drawer. Do NOT introduce arbitrary blocking logic.
3. **Bulk Edit Audit Granularity:**
   - *Existing Behavior:* The backend endpoint `API.products.bulkUpdate` (`ProductIntelligence.js:4555`) accepts a batch payload of modified rows.
   - *UI Redesign Requirement:* Submit the existing batch payload via the review modal diff table. Do NOT alter backend auditing schemas.
4. **Replenishment Forecasting Horizons:**
   - *Existing Behavior:* The forecasting engine computes a standard 30-day demand horizon by default (`ProductIntelligence.js:1075`), with interactive UI toggles for 30D, 90D, 180D, and 365D in the drawer (`ProductIntelligence.js:597`).
   - *UI Redesign Requirement:* Display the existing 30-day default in list rows and preserve the drawer's horizon selector buttons. Do NOT modify forecasting mathematical models.

### Explicitly Out-of-Scope Backlog (Deferred to Future Product Releases)
- Automated multi-tier purchase order approval routing based on monetary thresholds.
- Hard credit-limit blocking rules during point-of-sale invoice generation.
- Granular per-cell historical audit log tables for product master revisions.
- Machine-learning seasonal demand models beyond the current Holt-Winters / SMA engine.

---

## 7. Phased Implementation Roadmap with Measurable Exit Gates

The implementation must proceed in **six sequential, dependency-aware phases**. No phase may begin until the exit criteria of the preceding phase have been validated and signed off.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ Phase 1: Shared Foundation & Workspace Shell                                │
│ Scope: style.css (.firm-viewport-container, .firm-kpi-ribbon, etc.)         │
│ Dependencies: None                                                          │
│ Exit Gate: Zero scrollbars on empty Firm shell; non-Firm tabs unaffected.   │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ Phase 2: Pilot 1 — Smart Reorder Queue & Replenishment Workbench            │
│ Scope: ProductIntelligence.js (SmartReorderView & ProductDetailDrawer)      │
│ Dependencies: Phase 1 Shell                                                 │
│ Exit Gate: 5-col table fits 1024x768; drawer renders SVG chart; PO queues.  │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ Phase 3: Pilot 2 — Customer Receivables Cockpit & Customer 360 Drawer       │
│ Scope: index.html (CustomerManager & Customer360Modal)                      │
│ Dependencies: Phase 1 Shell                                                 │
│ Exit Gate: CompactActionGroup fits 1024x768; 3-tab 360 drawer batch settles.│
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ Phase 4: Standard Inventory & Bulk Edit Spreadsheet Workspace               │
│ Scope: index.html (ProductManager) & ProductIntelligence.js (Hub)           │
│ Dependencies: Phase 1 Shell, Phase 2 components                             │
│ Exit Gate: Standard view fits 6 cols; Bulk Edit handles Arrow/Tab keys.     │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ Phase 5: Procurement, Paperwork & Pricing Workspaces                        │
│ Scope: PartyManager, PurchaseOrderManager, PricingProfileManager, LetterPad │
│ Dependencies: Phase 1 Shell, Phases 2-4 patterns                            │
│ Exit Gate: PO creator 2-phase layout; Pricing Matrix Arrow keys; Letter Pad.│
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ Phase 6: Financial Statements, Business Profile & Final Regression Gate      │
│ Scope: StatementManager, FirmProfile, cross-resolution visual verification  │
│ Dependencies: All previous phases                                           │
│ Exit Gate: Zero document scroll at 1024x768 to 1920x1080; 100% tests pass.  │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 8. Pre-Implementation Approval Checklist

Before any code edits or implementation tasks are authorized, this checklist must be reviewed and approved by the product owner:

- [ ] **1. Navigation Model Approved:** Confirm that all 8 canonical Firm subtabs (`inventory`, `products`, `customers`, `parties`, `orders`, `letters`, `statements`, `profile`) remain active and accessible at the top level without breaking deep links.
- [ ] **2. Verification Baseline Accepted:** Confirm that all 9 capabilities audited in Section 2 are verified as existing in the current codebase and will be preserved with 100% functional parity.
- [ ] **3. Scrolling Standards Accepted:** Confirm the five-domain scrolling policy (zero document scroll on desktop $\ge 1024\times768$; internal container scroll bounded to $\text{calc}(100\text{vh} - 320\text{px})$; slide-out drawers for deep diagnostics; strict 8–15 row pagination budgets).
- [ ] **4. Feature-Preservation Matrix Approved:** Confirm that every field, button, indicator, and workflow documented in Section 5 has a designated destination in the redesigned UI.
- [ ] **5. Business Logic Policy Confirmed:** Confirm that no new purchase-order approval thresholds, credit-limit hard stops, audit-log schema changes, or forecasting algorithm modifications will be introduced.
- [ ] **6. Pilot Sequencing Authorized:** Authorize implementation to proceed starting with **Pilot 1 (Smart Reorder Queue)** and **Pilot 2 (Customers & Customer 360 Drawer)** as proof-of-concept benchmarks before modifying the remaining screens.
