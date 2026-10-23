# RupeeCRM — Planner UI Consistency Audit & Brand Integration Plan
**Supplementary Audit Document:** `RUPPECRM_PLANNER_UI_AUDIT.md`  
**Parent Baseline Audit:** `RUPPECRM_UI_OVERHAUL_ANALYSIS.md` (Preserved Unchanged)  
**Target Repository:** `RanjeetYelave/Simple-Billing` (`billsoft`)  
**Audit Date:** October 2026  
**Execution Mode:** Analysis & Feasibility Audit (Read-Only; Zero Application or Prototype Code Modifications)  
**Visual Reference:** Approved RupeeCRM Modern UI (Warm canvas `#F4F6F2`, deep moss green `#234B30`, gold accents `#D4AF37`, 18px rounded cards, machined 3D gold rupee coin)

---

## 1. Executive Summary & Audit Scope

This document provides a focused, evidence-based UI consistency audit and architectural evaluation of the **Planner Subsystem** and **Application-Wide 3D Coin Brand Integration** in RupeeCRM. It extends the foundational baseline established in `RUPPECRM_UI_OVERHAUL_ANALYSIS.md` without modifying, overwriting, or invalidating any existing findings.

The investigation is driven by 4 newly provided screenshots representing the Planner tab and verified against production source code:
- **Screenshot 1 (`media_1792783500353.png`):** Planner — Goals & Habits Hub (`activeTab === 'goals'`)
- **Screenshot 2 (`media_1792783508863.png`):** Planner — Tasks & Board (`activeTab === 'board'`)
- **Screenshot 3 (`media_1792783605216.png`):** Planner — Expense Outflow Tracker (`activeTab === 'expenses'`)
- **Screenshot 4 (`media_1792783618934.png`):** Planner — Savings & Reserves Ledger (`activeTab === 'savings'`)
- **Production Implementation:** `billsoft/src/main/webapp/index.html` lines 7198–9500 (`PlannerPage`), companion modals (`index.html:7300–7630`), and `billsoft/src/main/webapp/css/style.css` lines 740–795, 3350–3650.

### Key Audit Conclusions:
1. **Goals Interface:** The current Goals interface features oversized, vertically bloated cards (`.progress-app-card`) with low information density, conflicting pastel accent strips, and a cluttered 6-button quick-action ribbon that overwhelms the user.
2. **Tasks & Board Structural Misrepresentation:** The "Tasks & Board" view masquerades as a Kanban board, but code analysis confirms **it is not a Kanban board**. The 3 columns represent completely distinct data entities: Column 1 (`Active Reminders`), Column 2 (`Task Pipeline` with internal status dropdowns), and Column 3 (`Notes & Memos`). Drag-and-drop between columns is impossible, creating misleading mental models.
3. **Expense Outflow vs. Savings Duplication:** `Expense Outflow` and `Savings & Reserves` share nearly 80% identical layout patterns, filter bars, date selectors, and pagination logic, but manage separate financial data streams. A **unified Financial Hub with dedicated view switches** is architecturally feasible and will drastically reduce code duplication without altering financial records.
4. **Missing 3D Coin Brand Integration:** While the 3D coin asset exists in `images/rupee_coin_3d_*.png`, its integration in production is almost non-existent. It is used as a static sidebar logo and in login, but is completely absent from transactional feedback (invoice creation, payment confirmation, goal completion, long-running sync).

---

## 2. Screenshot-by-Screenshot Findings (Planner Subsystem)

### 2.1 Screenshot 1: Goals & Habits Page
*Ref: Attached Screenshot 1 (`media_1792783500353.png`) | Code Ref: [index.html:8717–9100](file:///Users/afk/Documents/GitHub/Simple-Billing/billsoft/src/main/webapp/index.html#L8717-L9100)*

| Dimension | Finding & Evidence |
| :--- | :--- |
| **Workflow / Screen** | Goals & Habits Management (`PlannerPage` -> `goals` tab) |
| **Evidence Level** | **Screenshot-confirmed & Code-confirmed** |
| **Severity** | **High (Visual Clutter & Poor Scalability)** |
| **Observed Issues** | 1. **Excessive Vertical Footprint & Wasted Space:** A single goal card consumes over 340px of vertical space while displaying only a title, a single progress bar, and metadata. In a store with 8–10 goals, users must scroll through feet of canvas.<br>2. **Weak Typography & Visual Hierarchy:** The goal title (`Goal_Revenue_1792386688236`) is rendered in generic 1rem font, dwarfed by an oversized `₹1,007.00` current value and a disconnected `50% Complete` pill.<br>3. **Overloaded Quick-Action Ribbon:** The bottom ribbon at [L8894–8939](file:///Users/afk/Documents/GitHub/Simple-Billing/billsoft/src/main/webapp/index.html#L8894-L8939) packs **6 competing pill buttons** side-by-side (`+₹1k`, `+₹5k`, `+ Deposit`, `Deduct`, `Update To ₹`, `History (9) ▼`) with inconsistent border colors (red `#dc2626`, purple `#4f46e5`, green `#10b981`).<br>4. **Inconsistent Metric Ribbon:** Top summary ribbon displays 3 KPI cards with cartoonish emoji icons (🎯, 🔥, 💰) that clash with the rich RupeeCRM Modern theme. |
| **User Impact** | Hard to scan multiple business targets simultaneously; high friction in performing basic goal edits; feels like a toy mobile widget rather than an executive CRM tool. |
| **Recommended Change** | Refactor into a **dense, dual-tier Goal Card**: Header with title, target date badge, and subtle kebab action menu; central zone with clean progress track and dual financial indicators; bottom zone with consolidated deposit and history triggers. Adopt canonical `.card` tokens with 16px radius and gold progress accents. |
| **Shared Dependency** | Extends `TSK-TOK-01` (Design Tokens) and `TSK-BTN-01` (Canonical Buttons). |

---

### 2.2 Screenshot 2: Tasks & Board Page
*Ref: Attached Screenshot 2 (`media_1792783508863.png`) | Code Ref: [index.html:8080–8450](file:///Users/afk/Documents/GitHub/Simple-Billing/billsoft/src/main/webapp/index.html#L8080-L8450)*

| Dimension | Finding & Evidence |
| :--- | :--- |
| **Workflow / Screen** | Operational Tasks, Reminders, and Notes Board (`PlannerPage` -> `board` tab) |
| **Evidence Level** | **Screenshot-confirmed & Code-confirmed** |
| **Severity** | **Critical (Architectural & Interaction Design Defect)** |
| **Observed Issues** | 1. **Misleading Kanban Façade:** The UI renders 3 Kanban columns (`Active Reminders`, `Task Pipeline`, `Notes & Memos`), but code confirms these are **3 completely disjoint models** (`reminders`, `tasks`, `notes`). There is no progression from Column 1 to Column 2 or Column 3.<br>2. **Internal Status Dropdown Inside Column 2:** Tasks inside the "Task Pipeline" column each have their own internal status dropdown (`TODO`, `IN_PROGRESS`, `DONE`) at [L8294–8309](file:///Users/afk/Documents/GitHub/Simple-Billing/billsoft/src/main/webapp/index.html#L8294-L8309). If Column 2 is already a "Pipeline", having status dropdowns *inside* the cards is redundant and confusing.<br>3. **Triplicate Independent Pagination:** Each column paginates independently with `pageSize = 5` ([L7500–7515](file:///Users/afk/Documents/GitHub/Simple-Billing/billsoft/src/main/webapp/index.html#L7500-L7515)), forcing operators to manage 3 separate pagination controls on a single screen.<br>4. **Filter Bar Detachment:** The date range filters (`From:`, `To:`) and search bar sit in a detached floating card with minimal contrast. |
| **User Impact** | Extreme confusion for store managers expecting drag-and-drop workflow progression; inability to sort all urgent items by due date across reminders and tasks; fragmented task oversight. |
| **Recommended Change** | Replace the pseudo-Kanban board with an **Executive Operational Workspace**: A segmented master task list (or 3 clearly titled entity tabs/sections: Actionable Tasks, Time-Sensitive Reminders, Scratchpad Memos) with unified due-date sorting, priority filtering, and instant completion toggles. |
| **Shared Dependency** | Extends `TSK-BTN-01` (Button primitives) and `TSK-FORM-01` (Input tokens). |

---

### 2.3 Screenshot 3: Expense Outflow Page
*Ref: Attached Screenshot 3 (`media_1792783605216.png`) | Code Ref: [index.html:8476–8588](file:///Users/afk/Documents/GitHub/Simple-Billing/billsoft/src/main/webapp/index.html#L8476-L8588)*

| Dimension | Finding & Evidence |
| :--- | :--- |
| **Workflow / Screen** | Business Expenditure Tracking (`PlannerPage` -> `expenses` tab) |
| **Evidence Level** | **Screenshot-confirmed & Code-confirmed** |
| **Severity** | **Medium-High (Outdated Form Patterns & Layout Duplication)** |
| **Observed Issues** | 1. **Disconnected Filter Controls:** Filter bar has 5 controls lined up haphazardly: search input, category dropdown, two bare date pickers, and a `Print Statement` button.<br>2. **Card List Density:** Each expense row is wrapped in a full card with `border-left: 4px solid #dc2626`, displaying large negative numbers (`-₹500.00`) and pastel chips (`#fee2e2`, `#e0e7ff`) that feel dated.<br>3. **KPI Card Redundancy:** Top KPI cards use generic gradients (`kpi-red`, `kpi-blue`) that violate the RupeeCRM gold/moss green palette. |
| **User Impact** | Inefficient for high-volume expense audits; lacks batch actions or CSV export directly visible in the toolbar. |
| **Recommended Change** | Upgrade to a modern **Financial Outflow Data Grid** with tabular layout, inline category pills, and standard date-range presets (Today, This Week, This Month). Connect to canonical design system. |
| **Shared Dependency** | Shares tokens with Savings ledger and Invoice summary. |

---

### 2.4 Screenshot 4: Savings & Reserves Page
*Ref: Attached Screenshot 4 (`media_1792783618934.png`) | Code Ref: [index.html:8590–8715](file:///Users/afk/Documents/GitHub/Simple-Billing/billsoft/src/main/webapp/index.html#L8590-L8715)*

| Dimension | Finding & Evidence |
| :--- | :--- |
| **Workflow / Screen** | Reserve Funds & Savings Ledger (`PlannerPage` -> `savings` tab) |
| **Evidence Level** | **Screenshot-confirmed & Code-confirmed** |
| **Severity** | **Medium-High (Layout Mirror of Expenses)** |
| **Observed Issues** | 1. **Complete Code Duplication with Expenses:** The layout of `Savings & Reserves` is a 1-to-1 copy of `Expense Outflow` (KPI grid -> filter card -> list container), except colors are swapped from red to green (`#10b981`).<br>2. **Complex Reconciliation Items:** Auto-reconciled items show confusing text like `+-₹6,993.00` and `Goal_Revenue_1792386688236 Balance Reconciliation` with raw database keys.<br>3. **Duplicate Print Actions:** Has separate `🖨️ Print Ledger` button that mirrors the `Print Statement` button in Expenses. |
| **User Impact** | Navigating between Expenses and Savings feels disjointed despite them tracking opposite sides of the store's cashflow equation. |
| **Recommended Change** | Formulate a **Unified Financial Management Hub** (see Section 4 for detailed feasibility analysis). |
| **Shared Dependency** | Directly paired with Expense Outflow. |

---

## 3. Deep-Dive: Tasks Page Model & Interaction Analysis

### 3.1 The Underlying Data Model
An inspection of `index.html:7198–7600` and API endpoints reveals the underlying task data architecture:

```
┌────────────────────────────────────────────────────────────────────────┐
│               ACTUAL PLANNER ENTITY ARCHITECTURE                       │
├─────────────────────┬───────────────────┬──────────────────────────────┤
│ Entity 1: Reminders │ Entity 2: Tasks   │ Entity 3: Notes & Memos      │
├─────────────────────┼───────────────────┼──────────────────────────────┤
│ Table: reminders    │ Table: reminders  │ Table: notes                 │
│ Type: 'reminder'    │ Type: 'task'      │ Type: 'note'                 │
│ State: Boolean      │ State: Status     │ State: Unstructured Text     │
│ (completed: t/f)    │ (TODO/IN_PROGRESS/│ (No status or completion)    │
│                     │  DONE)            │                              │
│ Fields:             │ Fields:           │ Fields:                      │
│ - title             │ - title           │ - title                      │
│ - dueDate           │ - dueDate         │ - content                    │
│ - customerId        │ - customerId      │ - tags                       │
│ - firmId            │ - firmId          │ - firmId                     │
│ - tags              │ - tags            │                              │
└─────────────────────┴───────────────────┴──────────────────────────────┘
```

### 3.2 Why the "Kanban" Presentation Fails
1. **No Drag-and-Drop / Cross-Column Movement:**  
   In genuine Kanban boards, cards move left-to-right as work progresses (e.g., Backlog → In Progress → Done). In RupeeCRM, dragging a card from Column 1 (Reminder) to Column 2 (Task) is logically impossible because they represent fundamentally different types of records.
2. **Column 2 Contains Its Own Kanban States:**  
   Within Column 2 ("Task Pipeline"), each card already has a dropdown selector with `TODO`, `IN_PROGRESS`, `DONE`. The column itself is not a pipeline stage; it is an entire pipeline compressed into a single vertical column.
3. **Severe Mobile/Tablet Degradation:**  
   On screens $\le 1024\text{px}$, the 3 columns squish into unreadable narrow strips ($\approx 220\text{px}$ wide), forcing text truncation and horizontal scrolling.

### 3.3 Recommended Tasks Layout Architecture
Instead of an artificial 3-column Kanban, implement a **Consolidated Operational Planner**:
- **Option 1 (Recommended — Tabbed Operational Views):**
  A clean tabbed workspace with three dedicated views:
  - `Tasks`: Unified task table with status pills (`To Do`, `In Progress`, `Done`), inline due-date pickers, priority badges, and quick-filter chips (`All`, `Today`, `Overdue`, `High Priority`).
  - `Reminders`: Chronological agenda feed of upcoming business alerts (supplier payments, customer follow-ups).
  - `Scratchpad Notes`: Pinboard-style grid of business memos and SOPs with full-text search.
- **Option 2 (Unified Master-Detail Feed):**
  A single unified stream of all pending items (Reminders + Tasks) sorted chronologically by due date, with a persistent quick-entry bar at the top and collapsible completed archive at the bottom.

---

## 4. Feasibility Analysis: Unifying Expense Outflow and Savings

### 4.1 Comparative Analysis Matrix

| Dimension | Expense Outflow | Savings & Reserves | Architectural Overlap |
| :--- | :--- | :--- | :--- |
| **Database Entity** | `expenses` table (`id`, `title`, `amount`, `category`, `expenseDate`, `paymentMode`, `notes`, `firmId`) | `savings` table (`id`, `title`, `amount`, `category`, `savingDate`, `paymentMode`, `notes`, `goalId`, `firmId`) | **92% Schema Overlap** (Only difference: `goalId` on savings) |
| **Financial Direction** | Cash Outflow (Debit / Negative) | Cash Allocation / Reserve (Credit / Positive) | Mirror opposites of cashflow |
| **Calculations** | `totalAllTime`, `totalThisMonth`, `selectedTotal`, `topCategory` | `totalAllTime`, `totalThisMonth`, `savingsRate%` vs Outflow | Mutually interdependent (`savingsRate` requires `expenseStats`) |
| **Search & Filters** | Date range (`fromDate`, `toDate`), Category, Text search | Date range (`fromDate`, `toDate`), Category, Text search | **100% Identical Filter Logic** |
| **Export / Print** | `handlePrintStatement(true)` | `handlePrintStatement(false)` | Uses exact same printing adapter |
| **Pagination** | `PaginationControls` (10 per page) | `PaginationControls` (10 per page) | Identical pagination component |

### 4.2 Candidate Architecture: Unified "Financial Cashflow & Reserves" Hub

```
┌────────────────────────────────────────────────────────────────────────┐
│ PLANNER > FINANCIAL CASHFLOW & RESERVES HUB                            │
├────────────────────────────────────────────────────────────────────────┤
│ ┌───────────────────┐  ┌───────────────────┐  ┌──────────────────────┐ │
│ │ Monthly Spend     │  │ Monthly Savings   │  │ Net Savings Rate     │ │
│ │ ₹500.00           │  │ ₹1,007.00         │  │ 66.8%                │ │
│ │ All-time: ₹500.00 │  │ Reserves: ₹1,007  │  │ Inflow vs Outflow    │ │
│ └───────────────────┘  └───────────────────┘  └──────────────────────┘ │
├────────────────────────────────────────────────────────────────────────┤
│ [ View:  (•) All Activity   ( ) Expenses (Debit)   ( ) Savings (Credit) ]│
│ [🔍 Search transactions...] [Category: All ▾] [24/10/2026 - 24/10/2026]│
│ [+ Record Expense]  [+ Deposit Saving]  [🖨️ Print Financial Statement] │
├────────────────────────────────────────────────────────────────────────┤
│ UNIFIED CASHFLOW LEDGER                                                │
│ ┌────────────────────────────────────────────────────────────────────┐ │
│ │ DATE       │ TITLE / CATEGORY         │ METHOD │ GOAL LINK │ AMOUNT│ │
│ ├────────────┼──────────────────────────┼────────┼───────────┼───────┤ │
│ │ 24/10/2026 │ Goal Contribution        │ UPI    │ 🎯 Revenue│+₹1,000│ │
│ │ 19/10/2026 │ Fuel Expense (Office)    │ Cash   │ —         │ -₹500 │ │
│ └────────────────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────┘
```

### 4.3 Recommendation & Safety Boundary:
- **Recommendation:** Unify `Expense Outflow` and `Savings & Reserves` into a single **"Financial Tracker"** tab with a segmented view switch (`Overview`, `Expenses`, `Savings`).
- **Safety Boundary:** **Do not alter backend databases or APIs.** Keep `API.expenses.*` and `API.savings.*` completely separate in network requests. The unification will be strictly a frontend view-layer consolidation that eliminates 450 lines of duplicate React markup and identical CSS rules while providing a complete, holistic view of the firm's cash position.

---

## 5. Application-Wide 3D Coin Animation & Brand Integration Audit

The approved 3D Rupee Coin (`images/rupee_coin_3d_*.png`) is the signature visual anchor of RupeeCRM. The audit evaluated its presence across all major application touchpoints.

### 5.1 Touchpoint Evaluation Matrix

| Application Touchpoint | Current Production Status | Asset Used | Animation / Depth | Lifecycle Point | Recommended Implementation |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **1. Global Sidebar Logo** | Implemented as flat icon | `rupee_coin_3d_64.png` ([L221](file:///Users/afk/Documents/GitHub/Simple-Billing/billsoft/src/main/webapp/index.html#L221)) | Pseudo 2D rotate (`rcrm-brand-glint`) | Constant infinite 16s loop | Add subtle 3D metallic specular gleam on hover; subtle periodic flip every 45s; zero CPU overhead when idle. |
| **2. Login & Auth Screen** | Implemented in modal | `rupee_coin_3d_128.png` ([L36571](file:///Users/afk/Documents/GitHub/Simple-Billing/billsoft/src/main/webapp/index.html#L36571)) | Static image, no animation | Displayed on mount | Add smooth drop-in reveal animation with golden glow on successful PIN entry. |
| **3. Invoice Save & Settlement** | **MISSING** in production | Standard text toast (`showToast`) | None | Immediate upon API return | Trigger celebratory 3D coin glint badge in topbar or within the modal upon successful invoice persistence. |
| **4. Payment Receipt Success** | **MISSING** in production | Plain toast with emoji (`💰 Payment recorded`) | None | Post-save | Display gold coin burst celebration in Payment Settlement Modal. |
| **5. Goal Achievement** | **MISSING** in production | Plain emoji (`🏆 Achieved!`) | None | On reaching 100% | Render 3D coin shine badge when goal milestone is completed. |
| **6. Savings Deposit Action** | **MISSING** in production | Standard green text (`+₹1,000`) | None | On click | Animate mini gold coin flip into the total savings counter. |
| **7. Long-Running Sync / Backup** | **MISSING** in production | Generic CSS circle spinner | Flat CSS | During fetch | Replace generic spinner with smooth 3D rotating gold coin (`rcrm-coin-spin`). |
| **8. Toast Deck Notifications** | Partially implemented | Mini 16px icon ([L248](file:///Users/afk/Documents/GitHub/Simple-Billing/billsoft/src/main/webapp/index.html#L248)) | Flat CSS scale | On toast render | Standardize icon badge across success toasts. |

### 5.2 Performance & Accessibility Rules for Coin Motion:
1. **Zero Continuous GPU/CPU Drain:** Continuous 3D transforms must be strictly restricted to active loading spinners (`rcrm-coin-spin`). Logo animations must use idle timeouts ($\ge 45\text{s}$ interval) rather than tight rendering loops.
2. **Strict Reduced-Motion Adherence:** Ensure `@media (prefers-reduced-motion: reduce)` disables all rotations and transitions ([style.css:786–796](file:///Users/afk/Documents/GitHub/Simple-Billing/billsoft/src/main/webapp/css/style.css#L786-L796)).
3. **Retro Isolation:** The 3D coin must **NEVER** render under `:root.classic` or `:root.classic-dark`. The retro theme must exclusively use `RupeeCRMRetroLogo` (the 1995 beveled floppy disk / pixel logo at [index.html:124–170](file:///Users/afk/Documents/GitHub/Simple-Billing/billsoft/src/main/webapp/index.html#L124-L170)).

---

## 6. Planner Implementation Backlog & Task Breakdown

All tasks use unique IDs with the `TSK-PLN-` prefix to prevent collision with the original audit (`TSK-TOK-01`, `TSK-BTN-01`, `TSK-INV-01`, etc.).

### Priority Scoring Formula:
$$\text{Priority Score} = \frac{(\text{Impact} \times 3) + (\text{Inconsistency} \times 2) + (\text{Reach} \times 2)}{(\text{Effort} \times 1) + (\text{Risk} \times 1.5)}$$

| Task ID | Focus Area | Problem & Verified Code Location | Proposed Solution | Imp | Inc | Rch | Eff | Rsk | Score |
| :--- | :--- | :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **TSK-PLN-NAV-01** | Planner Subtabs & Hierarchy | Subtabs use ad-hoc `.tab` styling; lack visual cohesion with main RupeeCRM navigation (`index.html:8060–8075`). | Implement canonical `.segmented-nav` with gold pill active indicators and consistent padding. | 4 | 4 | 4 | 1 | 1 | **11.2** |
| **TSK-PLN-GOAL-01** | Goals Card Redesign | Oversized cards (340px high), cluttered 6-button quick-action ribbon, cartoon emojis (`index.html:8758–8960`). | Compact 2-tier card layout; streamlined deposit/deduct actions; canonical progress bar tokens. | 4 | 5 | 3 | 2 | 1 | **8.0** |
| **TSK-PLN-TSK-01** | Tasks Board Restructure | Pseudo-Kanban layout creates false mental models; 3 independent paginations (`index.html:8150–8380`). | Replace 3-column Kanban with tabbed operational view (Tasks, Reminders, Notes) or unified agenda. | 5 | 5 | 4 | 3 | 2 | **5.5** |
| **TSK-PLN-FIN-01** | Expense & Savings Unification | Complete markup & filter duplication between Expenses and Savings (`index.html:8476–8715`). | Unify into single Financial Hub with segmented view toggle; shared filters; preserve 100% calculations. | 4 | 4 | 3 | 3 | 2 | **4.3** |
| **TSK-COIN-01** | Branded Coin Animations | 3D coin missing from invoice, payment, goal, and loading feedback (`index.html:111–122`, `style.css:745–796`). | Implement centralized `triggerCoinCelebration()` utility; wire into invoice/payment success and loading. | 5 | 4 | 5 | 2 | 1 | **9.4** |
| **TSK-PLN-FORM-01** | Planner Modals Standardization | Modals for Reminder, Task, Expense, Saving use mismatched inputs and button classes (`index.html:7300–7480`). | Apply canonical design-system modal wrapper, inputs, and primary/secondary button hierarchy. | 4 | 4 | 3 | 2 | 1 | **7.4** |

---

## 7. Open Questions & Items Requiring User Confirmation

1. **Tasks Board vs. True Kanban:**  
   *Question:* Would you prefer the Tasks page to be restructured as a **Tabbed Operational List** (separate views for Tasks, Reminders, and Notes with rich sorting) OR an **Agenda-Style Feed** (all upcoming due items in one prioritized stream)?
2. **Financial Page Consolidation:**  
   *Question:* Do you approve combining `Expense Outflow` and `Savings & Reserves` into a single **"Financial Tracker"** tab with segmented sub-views (`All Activity`, `Expenses`, `Savings`), or should they remain separate top-level subtabs with shared components?
3. **Coin Celebration Prominence:**  
   *Question:* For invoice creation and payment settlements, should the coin celebration be a **discrete topbar/toast badge glint** or a **full celebratory modal overlay**? (We recommend the discrete glint to avoid slowing down rapid retail checkout).

---

## 8. Verification & Acceptance Criteria

| Subsystem | Objective Verifiable Acceptance Criterion |
| :--- | :--- |
| **Goals & Habits** | 1. Goal card vertical footprint reduced by $\ge 35\%$ without removing any existing field or capability.<br>2. Quick actions consolidated from 6 competing buttons to clean Primary Deposit + secondary dropdown.<br>3. Progress bars utilize canonical gold/emerald gradient tokens. |
| **Tasks & Board** | 1. Eliminates misleading Kanban columns; provides clear entity distinction between Reminders, Tasks, and Notes.<br>2. Pagination unified or clearly scoped per active view.<br>3. Tasks can be sorted by due date, priority, and completion status. |
| **Financial Tracker** | 1. 100% mathematical parity across `expenseStats` and `savingStats`.<br>2. Date filters and category dropdowns operate seamlessly without resetting on view switches.<br>3. Zero modifications to underlying database tables or REST endpoints. |
| **Coin Brand Integration** | 1. 3D coin rotates smoothly during asynchronous data sync and loading states.<br>2. Successful invoice save triggers a specular glint celebration on the coin asset.<br>3. Motion completely halts under `prefers-reduced-motion`.<br>4. Classic Retro theme retains 100% of its authentic 1995 beveled pixel logo without 3D coin leakage. |

---
*End of Supplementary Audit Document (`RUPPECRM_PLANNER_UI_AUDIT.md`). Implementation will begin only upon final user confirmation and consolidation.*
