# RupeeCRM — Complete UI/UX Overhaul: Analysis, Design System & Feasibility Audit

**Document Reference:** `RUPPECRM_UI_OVERHAUL_ANALYSIS.md`  
**Target Repository:** `RanjeetYelave/Simple-Billing` (`billsoft`)  
**Audit Date:** October 2026  
**Execution Mode:** Analysis and Feasibility Audit (Read-Only; Zero Application Code Changes)  
**Primary Design References:**  
1. *Reference Image 1:* High-polish modern CRM interface (Velo dashboard: soft mint/cream backdrop, clean typography, curved 16–20px cards, prominent pill action buttons, active moss/emerald navigation, structured metric cards with period filter chips, clean dual-column activity feeds and checklist cards).  
2. *Reference Image 2:* Polished dimensional RupeeCRM gold Indian Rupee coin (₹) with beveled metallic outer rim, concentric relief, specular gleam highlights, and warm golden amber inner core.

---

## Section 1 — Executive Summary

### 1.1 Current UI Architecture & Principal Inconsistencies
RupeeCRM is an enterprise-grade, offline-first retail ERP / POS desktop application built on a Spring Boot 3.3.4 (Java 21) backend with an embedded H2 relational database, supervised by a Java desktop supervisor (`launcher`) and packaged via WiX Toolset (`installer`). Its frontend is a Single Page Application (SPA) located at `billsoft/src/main/webapp/index.html` (39,646 lines) and companion modules in `billsoft/src/main/webapp/js/`, executing on pre-bundled React 18 in vanilla production mode without a modern build-time bundler (e.g. Vite or Webpack). All interface styling is managed through a single 9,731-line CSS stylesheet (`billsoft/src/main/webapp/css/style.css`, 214 KB).

The repository currently supports two primary theme families:
1. **Modern Family:** Consisting of *Modern Light* (`light-1`) and *Modern Dark* (`dark-1`).
2. **Classic Family:** An authentic Windows 95/98 retro theme consisting of *Classic Retro Light* (`classic`) and *Classic Noir Dark* (`classic-dark`), enforced with 0px border radii, beveled 3D borders, and retro serif/sans typography.

The application-wide UI audit identified several systemic inconsistencies across the current Modern themes:
- **Generic, Fragmented Color Language:** The Modern theme defaults to standard Tailwind-like indigo (`#4F46E5`) in light mode and generic cobalt blue (`#60A5FA` / `#3B82F6`) in dark mode, lacking distinct brand identity. More critically, dozens of components bypass CSS tokens and inject hardcoded inline hex colors (e.g., `#6366f1`, `#dc2626`, `#059669`, `#d97706`).
- **Button & Control Geometry Drift:** Buttons across screens feature at least five competing implementations (`.btn-primary`, `.btn-secondary`, `.btn-outline`, `.btn-ghost`, plus arbitrary inline overrides), with heights ranging irregularly from 24px, 28px, 32px, 36px, 40px to 44px, and border radii fluctuating arbitrarily between 4px, 6px, 8px, 12px, 16px, and 9999px (pill).
- **Inconsistent Card Elevations & Radii:** Surface cards vary between 8px, 10px, 12px, 16px, and 24px corner radii, with mismatched multi-layer box shadows and conflicting padding scales (ranging from 10px to 28px).
- **Search & Input Disparity:** The application employs multiple diverging input paradigms: the pill-shaped topbar trigger (`.topbar-search-pill`), full-screen modal omnisearch (`.omni-search-input`), native comboboxes in paperwork, and bespoke autocomplete search inputs (`ProductSearchInput`, `CustomerSearchInput`), each exhibiting distinct focus rings, padding, and keyboard traps.
- **Absence of a Cohesive Branded Motion System:** Animation across the app is scattered across 38 independent `@keyframes` declarations in `style.css`. There is no centralized asset or animation coordinator for brand identity, loading transitions, or transactional confirmation.

### 1.2 Proposed Target Experience
The proposed overhaul replaces the generic Modern Light and Modern Dark themes with a unified, high-polish **RupeeCRM Gold Design System** inspired by Reference Image 1 (visual hierarchy, card geometry, typography, and layout balance) and Reference Image 2 (the dimensional Rupee gold coin):
- **Visual Atmosphere:** A warm, luminous canvas palette featuring a subtle warm mint/cream backdrop (`#F7F9F6`), pure elevated white surfaces (`#FFFFFF`), refined gold accents (`#D4AF37` / `#C5A028`), and deep charcoal typography (`#1A202C`).
- **Dark Mode Elevation:** A companion "Obsidian & Antique Gold" nighttime palette (`#0D1117` canvas, `#161B22` cards, `#E6CA65` gold accents) that fully preserves theme switching and circadian scheduling (Sunset-to-Sunrise, OS match, manual, and custom hours).
- **Cohesive Card & Navigation Language:** Generous 16px–20px card corner radii with subtle borders (`rgba(0,0,0,0.06)`), an elevated sidebar with a prominent "+ Create New Invoice" pill action button, an active emerald/moss pill state with radial sheen, and clean tabular data presentation with tabular figures (`font-variant-numeric: tabular-nums`).
- **Rupee Gold Coin Motion System:** A lightweight, vector-rendered (SVG) 3D gold coin component with restrained micro-animations (spin for loading, glint/shimmer for successful invoice/payment creation, drop-in reveal for login) with strict `prefers-reduced-motion` compliance.

### 1.3 Highest-Impact Opportunities
1. **Consolidated Design Token System:** Centralizing brand colors, elevations, radii, typography, and interactive states into standard CSS variables within `style.css` will instantly modernize all 43 screens and modals while eliminating hundreds of ad-hoc inline styles.
2. **Dashboard Restructuring:** Aligning the cluttered multi-segment dashboard with the dual-column, card-based layout from Reference Image 1 (metric cards with period filter chips, structured Recent Activities feed, and prioritized task checklists with status flags).
3. **Sidebar & Topbar Modernization:** Elevating the primary application shell with modern typography, an intuitive quick-action button, clear active indicator states, and a unified omnibar search trigger.
4. **Branded Transactional Feedback:** Introducing the gold coin celebration glint on invoice creation, payment settlement, and backup completion to provide tangible positive reinforcement for retail store owners.

### 1.4 Major Constraints & Implementation Risks
- **Zero Disruption to 90s Retro Theme (`classic` / `classic-dark`):** The retro theme is an explicit product requirement with deep emotional attachment and user adoption. Refactoring modern tokens and shared components must strictly avoid altering the retro 0px beveled Win95/98 styling.
- **State Preservation in Multi-Tab Document Editor:** The Invoices & Quotes Hub maintains up to 10 simultaneous open document tabs with keep-alive state (`InvoiceForm`). Restyling must preserve component mounting hierarchies to prevent discarding unsaved invoice line items.
- **Strict Tenant Boundary & Header Contracts:** Every business API depends on `TenantInterceptor` via the `X-Firm-Id` header and `API.firmId`. Layout restyling must never bypass or disrupt the active firm context.
- **Playwright Regression Safety:** The `ui-regression` test harness contains 38 end-to-end test suites with explicit DOM selector assertions (`.nav-item`, `.sidebar-logo`, `.topbar-search-pill`, `.sub-tabs-bar button`). CSS refactoring must preserve or augment existing test attributes to prevent test suite failures.

---

## Section 2 — Repository Architecture

### 2.1 Framework, Version & Entry Points
- **Client Framework:** React 18.2.0 (pre-bundled via `billsoft/src/main/webapp/js/vendor/react.production.min.js` and `react-dom.production.min.js`).
- **Execution Architecture:** In-browser runtime rendering using `React.createElement` (no JSX compilation step during dev or build). The application is loaded directly by Tomcat as a WAR webapp.
- **Primary HTML Shell:** `billsoft/src/main/webapp/index.html` (39,646 lines, 2.2 MB). Contains the root mount point (`<div id="root"></div>`), script tags, inline SVG icon dictionary (`const Icons`), core React components, and the main `App` component (lines 36627–39646).
- **Secondary Shell (Diagnostic/Disaster Recovery):** `billsoft/src/main/webapp/recovery.html` (2,401 lines, 99.7 KB). An isolated, standalone diagnostic console with dedicated cyber-dark CSS, intentionally independent from `style.css` to allow recovery during stylesheet corruption.

### 2.2 Modular JavaScript Components (`billsoft/src/main/webapp/js/`)
| File | Size | Lines | Subsystem & UI Responsibility |
| :--- | :--- | :--- | :--- |
| `themeToggle.js` | 7.2 KB | 212 | Theme state machine, `localStorage` persistence, dark mode calculation, circadian scheduler, OS media query listeners, and `themeChanged` event dispatcher. |
| `components/ProductIntelligence.js` | 284 KB | 5,637 | Product insights, demand forecasting SVG charts, explainable replenishment advisory, multi-vendor PO generator workspace, and stock drawers. |
| `components/TakeABreakHub.js` | 9.5 KB | 215 | Break hub container hosting Maharashtra journey and Snake Classic minigame. |
| `components/MaharashtraJourney.js` | 36.7 KB | 874 | Audio-visual travel progression UI across Sahyadri forts and landmarks. |
| `components/MaharashtraAtmosphere.js` | 22.5 KB | 545 | Procedural atmospheric backdrop (clouds, mist, rain) with canvas rendering. |
| `components/SnakeClassic.js` | 119 KB | 2,750 | Retro arcade canvas minigame with sound engine, Peshwa gold coin renderers, and scoreboards. |
| `components/TrophyProgressBar.js` | 9.0 KB | 240 | Milestone progress bar with 3D trophy reveal animations. |
| `components/ProgressionExperience.js` | 48.0 KB | 1,120 | Journey milestone popovers, level celebrations, and location badge drawer. |
| `calculator.js` | 48.1 KB | 1,166 | Floating Casio-style desk calculator with solar strip, LCD screen, and arithmetic parser. |
| `omnibarPipeline.js` | 264 KB | 4,890 | Omnisearch natural language parser, intent resolution, business math, and query tokenizer. |
| `omnisearchCapabilities.js` | 69.4 KB | 1,420 | Category cards, cheat sheet pills, and guide chips for the omnisearch modal. |
| `featureLock.js` | 67.7 KB | 1,510 | In-app security lock screens, PIN verification gates, and timed lockout controllers. |
| `utils.js` | 305 KB | 6,240 | Number formatting, Indian currency formatting (`₹`), date handling, PDF preview adapters, and helper utilities. |
| `api.js` | 56.4 KB | 1,320 | REST API client wrapping `fetch` with `X-Firm-Id` multi-tenant injection, error handling, and JSON serialization. |

### 2.3 Styling & Theme Architecture
All production styles reside in `billsoft/src/main/webapp/css/style.css` (9,731 lines).

#### Theme Switching & Cascade Hierarchy
1. `themeToggle.js` reads stored state from `localStorage`:
   - `themeFamily`: `'modern'` | `'classic'`
   - `themeDarkMode`: `'true'` | `'false'`
   - `themeDarkSchedule`: `'sunset'` | `'manual'` | `'system'` | `'custom'`
   - `themeCustomStart`, `themeCustomEnd`: e.g. `'19:00'`, `'07:00'`
2. Active classes are applied to `document.documentElement` (`:root`):
   - Modern Light: `:root.light-1` (`data-theme="light"`)
   - Modern Dark: `:root.dark-1` (`data-theme="dark"`, `.dark`, `.dark-mode`)
   - Retro Light: `:root.classic` (`data-theme="light"`)
   - Retro Dark: `:root.classic-dark` (`data-theme="dark"`, `.dark`, `.dark-mode`)
3. `themeToggle.js` emits a `themeChanged` event on `window` containing complete state details.
4. Legacy fallback: `localStorage.getItem('themeBase')` is maintained for backward compatibility.

#### Retro Theme Isolation Mechanism
Lines 73–350 and 366–649 of `style.css` enforce the retro theme using explicit overrides:
```css
:root.classic, :root.classic * {
  border-radius: 0px !important;
  font-family: 'Segoe UI', Tahoma, 'MS Sans Serif', Arial, sans-serif !important;
}
:root.classic-dark, :root.classic-dark * {
  border-radius: 0px !important;
  font-family: 'Segoe UI', Tahoma, 'MS Sans Serif', Arial, sans-serif !important;
}
```
Because the Retro theme relies heavily on explicit `:root.classic` and `:root.classic-dark` overrides with `!important` declarations, modifying the `:root`, `:root.light-1`, and `:root.dark-1` definitions will **not** distort retro styling, provided class names and DOM structures are preserved.

### 2.4 Existing Motion & Animation Utilities
The application uses pure CSS `@keyframes` animations (38 declared in `style.css`). Notable declarations include:
- `toastPulse`, `toastProgress`: multi-deck toast notifications (lines 1528, 1726).
- `spin`: general loading spinner (line 2057).
- `slideDown`, `slideUpModal`, `rcrmFadeIn`: modal and drawer entrance effects (lines 3420, 4239, 5542).
- `rcrm-core-pulse`, `rcrm-orbit-cw`, `rcrm-sheen-sweep`: fluid sync island telemetry (lines 5751, 5765, 5881).
- `omni-text-slide`, `omni-fade-in`: omnisearch placeholder cycling and backdrop blur (lines 6866, 6932).

There are **zero external animation runtime dependencies** (no Framer Motion, GSAP, or Lottie). The new coin animation system can seamlessly build upon this zero-dependency CSS `@keyframes` approach.

### 2.5 Existing Test Verification Commands
From repository root:
```bash
# 1. Build and verify packaging
./mvnw clean package -DskipTests

# 2. Run backend business integration tests
./mvnw test -Dtest=EndToEndBusinessLogicCrossModuleTest -Djacoco.skip=true -pl billsoft

# 3. Launch local backend instance on standard test port
java -jar billsoft/target/billsoft-0.0.1-SNAPSHOT.war --server.port=28080

# 4. Execute complete Playwright E2E UI regression test suite
cd ui-regression && npx playwright test

# 5. Execute targeted UI regression suites
cd ui-regression && npx playwright test tests/p0-app-shell.spec.ts tests/p1-invoices.spec.ts tests/p1-customers.spec.ts tests/p3-toast-visual.spec.ts
```

---

## Section 3 — Complete Screen Inventory

The following table comprehensively inventories every application screen, hub, tab, and modal in RupeeCRM:

| Screen/Module | File Paths | Current Patterns | Inconsistencies | Recommended Changes | Dependencies | Risk |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Shell: Sidebar** | `index.html:4746-4871`, `style.css:45-47,133-149,431-455` | 250px fixed dark sidebar (`#1E293B`), dual Quick Create split button (`+ Invoice` / `+ Quote`), icon + label menu list, bottom firm name and logout. | Dark sidebar clashes with light modern canvas; split button has hardcoded inline border colors (`rgba(255,255,255,0.2)`); active menu state uses standard blue. | Adopt Velo-inspired clean sidebar: prominent "+ Create New Contact / Invoice" pill button, active moss/gold pill background with radial highlight, refined SVG line icons. | `RupeeCRMLogo`, `Icons`, `App` navigation handler | Medium (Playwright asserts `.sidebar-nav-item` and `.nav-item`) |
| **Shell: Topbar** | `index.html:38235-38449`, `style.css:157-161,2257-2275,6486-6535` | 3-cluster header: 1. Hamburger toggle & page title; 2. Omnisearch trigger pill, calc button, refresh button, bell icon, theme toggle; 3. Firm capsule with firm select dropdown and session timer badge. | Cluttered icon spacing (5px gap); search pill has hardcoded border and height; session badge uses red pulse animation that creates visual noise during regular work. | Restructure into balanced header: wide rounded search bar (`border-radius: 9999px`), polished icon buttons with gold hover ring, cohesive firm selector chip. | `themeToggle.js`, `SessionTimerBadge`, `GlobalSearchModal` | Medium (Tests check `.topbar-search-pill`, `.theme-toggle-btn`) |
| **Shell: Status Bar** | `index.html:38830-38890`, `style.css:609-620,8300-8350` | Bottom fixed 28px bar showing storage, JVM memory, latency, and session count. Toggleable via Settings. | Visually separated from main card; typography differs from body tables; hardcoded background `#0F172A` in dark mode. | Standardize with sunken surface token (`--surface-sunken`), tabular figures (`tabular-nums`), and subtle gold accents for normal telemetry. | `SettingsAppearance` status bar toggle | Low |
| **Dashboard: Business View** | `index.html:10748-12700`, `style.css:1120-1250` | 6 KPI metric cards (`kpi-blue`, `kpi-green`, `kpi-amber`, `kpi-purple`, `kpi-teal`, `kpi-indigo`), Revenue Bar Chart, Expected Revenue Donut, Recent Invoices, Low Stock Alerts, Quick Action Palette. | Fragmented KPI gradient classes; hardcoded inline colors on quick action buttons (`#6366f1`, `#dc2626`, `#059669`); dense vertical stack without clear grid hierarchy. | Implement Velo-inspired metric grid (soft pastel-tinted cards, 16px radius, bold figure, period filter chip); restructure into 2-column feed (Recent Activities + Tasks with priority flags). | `API.analytics`, `API.invoices`, `Icons` | High (Central landing screen) |
| **Dashboard: Personal View** | `index.html:11407-12400` | 4 Personal KPIs (Savings, Monthly Expenses, Active Goals, Habit Streaks), interactive SVG Goal Progress Chart, Category Donut Chart, habit streaks. | Inconsistent button heights for quick habit check-in; mismatched modal styles for record expense/saving. | Standardize KPI cards to match business view grid; embed mini gold coin icon alongside savings reserves; unify modal triggers. | `API.goals`, `API.savings`, `API.expenses` | Low |
| **Invoices & Quotes: Overview** | `index.html:35810-36320`, `style.css:3200-3350` | KPI statistics strip (Total Invoiced, Pending, Collected, Quotes), filter pill buttons (All, Unpaid, Overdue, Paid, Estimates, Cancelled), Recent Invoices list with action dropdown. | Filter buttons lack active pressed states; KPI strip mixes bold and regular fonts arbitrarily; return notification banner has hardcoded orange styling. | Align filter strip with Velo pill buttons (`border-radius: 9999px`, active gold-accented pill); restyle KPI cards to match dashboard design tokens. | `InvoicesQuotesHub`, `API.invoices` | Medium |
| **Invoices: List View** | `index.html:16535-17052`, `style.css:210-238,3350-3480` | Searchable data table with columns for Inv #, Date, Customer, Status, Total, Paid, Balance, Actions. Pagination, bulk CSV export, column sort. | Inconsistent table row padding (varies from 6px to 14px); status dropdowns use distinct border treatments; pagination controls lack clear active page indicator. | Standardize table headers with uppercase 11px muted typography; apply subtle alternating row backgrounds; refine status badges with soft pill styling and accessible contrast. | `InvoiceList`, `StatusDropdown`, `API.invoices` | Medium (P1 test target in `p1-invoices.spec.ts`) |
| **Invoices: Document Editor (InvoiceForm)** | `index.html:13115-14690`, `style.css:3500-3850` | Keep-alive multi-tab editor (up to 10 tabs). Customer search/add, barcode scan input, item table (Item, HSN, Qty, Rate, Tax%, Disc, Total), summary sidebar (Subtotal, CGST, SGST, IGST, Roundoff, Grand Total). | High information density; inline styles on tax inputs; tab bar strip has hardcoded background `#f1f5f9`; Save button has custom blue gradient. | Preserve 100% of keep-alive DOM architecture and form state; wrap editor in clean card surface; polish line-item grid with sticky header and gold-accented totals card. | `InvoiceForm`, `ProductSearchInput`, `CustomerSearchInput`, `GstStateUtils` | Critical (Highest financial regression risk) |
| **Invoices: Quotations List** | `index.html:17053-17576` | Quotation table, status filters (DRAFT, ISSUED, ACCEPTED, REJECTED, EXPIRED), "Convert to Invoice" one-click action, PDF preview trigger. | Convert button has inline orange border; status badges differ from invoice status badges; table headers use different font sizes. | Unify status badge variant component with InvoiceList; restyle Convert action to canonical primary button with convert icon. | `QuotationList`, `API.quotations` | Medium (`p1-quotations.spec.ts`) |
| **Invoices: Sales Returns & Credit Notes** | `index.html:16100-16486` | Credit note summary cards, return transactions table, return detail modal, restock indicators. | Credit note numbers (`CN-xxxx`) lack distinct styling; return button in table has different height than invoice action buttons. | Style credit note IDs with distinct tabular badge; unify return action buttons with canonical button primitives. | `SalesReturnsHub`, `SalesReturnModal`, `SalesReturnDetailModal` | Medium (`sales-returns.spec.ts`) |
| **Invoices: Print & Download Modal** | `index.html:744-1103`, `style.css:3900-4100` | Config modal for A4/A3/A5, thermal receipt (80mm/58mm), 5 color palettes, 4 invoice themes (Classic, Modern, Professional, Minimal), live PDF.js preview. | Preset color chips have hardcoded sizes and border outlines; zoom buttons have inconsistent padding; theme selection cards lack hover animation. | Align modal wrapper with canonical modal primitive; modernize preset palette pills; add smooth transitions to theme radio cards. | `InvoicePrintModal`, `pdfjsLib`, `BillsoftPdfRenderer` | Low (Print output must remain functionally unchanged) |
| **Invoices: WhatsApp Share Modal** | `index.html:1104-1250` | Phone input, prefilled text template generator, direct WhatsApp Web deep-link (`https://wa.me/...`), prefilled PDF blob link. | WhatsApp brand green (`#25D366`) is hardcoded inline in multiple sub-elements; textarea lacks standard focus state. | Preserve authentic WhatsApp brand color for the action button while conforming the container modal and phone inputs to design tokens. | `WhatsAppShareModal`, `Icons.whatsapp` | Low |
| **Invoices: Record Payment Modal** | `index.html:15559-15894` | Invoice balance banner, payment amount input, payment mode (Cash, UPI, Card, NetBanking, Cheque), reference ID, notes, settlement button. | Hardcoded green header background; amount input lacks prominent currency prefix; quick settlement chips lack pressed states. | Elevate with large currency input featuring Rupee symbol; add gold coin confirmation glint upon settlement; conform buttons to design system. | `RecordPaymentModal`, `API.invoices.recordPayment` | High (Financial record creation) |
| **Firm: Inventory / Product Manager** | `index.html:17577-19302`, `style.css:4200-4450` | SKU data table, stock search, category filters, low stock warnings, add/edit product modal, quick stock adjustment dialog, CSV import/export. | Search input differs from topbar search; category filter buttons have uneven horizontal margins; low-stock badges use competing red shades (`#ef4444` vs `#dc2626`). | Standardize table headers; introduce uniform filter strip; unify stock badge tokens (`--danger`, `--warning`, `--success`). | `ProductManager`, `API.products`, `ProductIntelligenceHub` | High (`p2-inventory.spec.ts`) |
| **Firm: Product Intelligence & Replenishment** | `ProductIntelligence.js:1-5637` | Demand forecasting trajectory SVG charts, explainable stock recommendations (CRITICAL, URGENT, WARNING, HEALTHY), multi-vendor PO generator workspace, product detail drawer. | Standalone file with internal styling; forecast chart tooltips have hardcoded z-index and border; PO generator steps use custom segmented button bar. | Update CSS variables within `ProductIntelligence.js` to inherit from global tokens; ensure chart stroke and fill colors utilize brand gold and semantic tokens. | `ProductIntelligence.js`, `API.forecast` | High (`smart-reorder-audit.spec.ts`) |
| **Firm: Customer Manager** | `index.html:20089-21000`, `style.css:4500-4680` | Customer directory table, outstanding receivables total, search bar, add/edit customer modal, Customer 360 drawer trigger. | Outstanding amounts lack clear color-coding hierarchy (positive vs zero balance); action buttons in table rows are cramped; mobile layout wraps awkwardly. | Apply tabular numerals to customer balances; color-code receivables using `--danger` and zero balances with `--text-muted`; implement clean action dropdown. | `CustomerManager`, `API.customers` | High (`p1-customers.spec.ts`) |
| **Firm: Customer 360 Drawer** | `index.html:19303-19686` | Right slide-out drawer. Financial KPI summary, outstanding invoices tab, payment history tab, statements, reminder trigger, settlement action. | Custom drawer z-index; tab buttons (`.c360-tab-btn`) use custom styles instead of shared sub-tab classes; hardcoded border lines. | Standardize drawer container with canonical drawer primitive; align internal tabs with design system sub-tabs; embed gold coin indicator on total settled metric. | `Customer360Modal`, `CustomerSettlementModal` | Medium (`p1-customers.spec.ts`) |
| **Firm: Customer Bulk Settlement Modal** | `index.html:19687-20088` | Multi-invoice selection checkboxes, auto-allocation calculation (FIFO), payment mode, total settlement summary. | Auto-allocation preview table lacks borders; total allocated box has hardcoded background `#ecfdf5`. | Wrap summary box in elevated surface token; use tabular numbers for balance calculations; standardize form inputs. | `CustomerSettlementModal`, `CustomerBalanceServiceImpl` | High (Multi-invoice balance recalculation) |
| **Firm: Parties (Vendors) Manager** | `index.html:21001-22014` | Vendor directory table, payable balance, purchase order history, contact information, add/edit party modal. | Visual structure mirrors Customer Manager but uses different table column widths; search bar lacks clear placeholder. | Align layout and search patterns with Customer Manager for cross-module consistency. | `PartyManager`, `API.parties` | Medium |
| **Firm: Purchase Order Manager** | `index.html:22711-24675` | PO list, PO builder, vendor selector, item lines, status progression (DRAFT -> ISSUED -> RECEIVED), inward stock receipt confirmation. | Step indicators in PO builder have hardcoded background circles; status pills use inconsistent uppercase transformations. | Unify step indicators with design system stepper primitive; conform status pills to canonical badge tokens. | `PurchaseOrderManager`, `API.purchaseOrders` | High (Stock ledger increment on receipt) |
| **Firm: Paperwork & Letterhead Hub** | `index.html:24676-25687,26917-26977` | Official letterhead generator, rich text editor, document templates, PDF print/export preview. | Rich text toolbar buttons have inconsistent heights (26px to 32px); template selection cards lack hover elevations. | Standardize toolbar buttons with canonical icon-button primitive; align template cards with card elevation tokens. | `PaperworkHub`, `LetterPadManager` | Low |
| **Firm: Statement Manager** | `index.html:25952-26916` | Customer and vendor ledger statement builder, date range picker, opening/closing balance calculation, PDF statement export. | Date picker inputs lack consistent height with comboboxes; balance badges have hardcoded font weights. | Standardize form filter row using CSS grid; format closing balance in prominent card with tabular figures. | `StatementManager`, `API.statements` | Medium (`reports-statements-pdf.spec.ts`) |
| **Firm: Business Profile & Tenancy** | `index.html:22015-22710` | Tabbed profile editor: Identity (Name, Tagline, Logo), Contact & Address, Banking & UPI (QR code generator), WhatsApp integration, Invoice Terms & Footer. | 5 tab buttons have custom IDs and inline background styling; logo upload preview box has fixed 100px square without border radius; bank details form lacks grouping. | Restructure into structured card sections with clean section headers; unify sub-tabs with standard sub-tab primitive; polish logo upload dropzone. | `FirmProfile`, `API.firm` | High (Company header snapshot on invoices) |
| **Planner: Kanban Board** | `index.html:7228-8750`, `style.css:2950-3050` | 3 Kanban columns (TODO, IN_PROGRESS, DONE), draggable task cards, priority flags, progress bars, quick task creation input. | Column headers have hardcoded backgrounds in dark mode; priority flags use distinct emoji icons (`🚩`, `🏳️`); card hover shadow lacks smooth easing. | Restyle task cards inspired by Reference Image 1 (rounded checkbox, priority flag chip, clean title typography, subtle border); modernize column headers. | `PlannerPage`, `API.reminders` | Medium (`p2-planner-hr.spec.ts`) |
| **Planner: Goals & Habits Tracker** | `index.html:8748-9969` | Savings goals cards, habit streak calendars, progress donut SVG, deposit modal, deduct modal, reconcile modal. | Donut chart colors are hardcoded inline; habit check-in buttons vary in size; reconcile modal lacks explanation text. | Align goal progress cards with design tokens; incorporate gold coin micro-icon on savings goals; standardize deposit/deduct modals. | `PlannerPage`, `GoalProgressChart`, `API.goals` | Low (`p2-expenses-goals.spec.ts`) |
| **Planner: Expense Outflow Manager** | `index.html:8508-8621` | Expense list, category breakdown pills, payment mode tags, quick expense entry palette. | Expense amount column lacks right-alignment; category pills have inconsistent horizontal padding; quick entry input has no border highlight. | Ensure tabular numeric alignment on table amounts; standardize category badges; polish quick entry palette with design tokens. | `PlannerPage`, `API.expenses` | Low (`p2-expenses-goals.spec.ts`) |
| **Planner: Savings & Reserves Ledger** | `index.html:8622-8747` | Emergency fund ledger, linked goals list, deposit history table. | Missing empty-state illustration; deposit action button is styled differently from expense action button. | Introduce clean empty state with gold coin watermark; harmonize deposit button with canonical primary action. | `PlannerPage`, `API.savings` | Low |
| **Inbox: Notifications & Alerts Feed** | `index.html:9970-10355`, `style.css:6525-6540` | Notification list, category filter tabs (Billing, Inventory, Purchase, Planner, HR, Licensing, System), primary/secondary action buttons, snooze/dismiss. | Unread notification cards have hardcoded blue borders (`#3b82f6`); action button row has inconsistent flex-wrap on narrow screens. | Style unread cards with subtle gold-accented left border indicator; standardize action buttons with canonical `.btn-sm` variants. | `InboxPage`, `API.notifications` | Medium (`p1-notifications.spec.ts`) |
| **HR: Staff Directory** | `index.html:33718-34400` | Employee cards/table, designation, base salary, join date, status toggle, add/edit employee modal. | Salary amounts use standard fonts instead of tabular numbers; status toggle switch has different dimensions than Settings switch. | Format salary figures with tabular font; standardize toggle switch with canonical switch token. | `HRManager`, `API.employees` | Medium (`p2-planner-hr.spec.ts`) |
| **HR: Attendance & Shifts** | `index.html:34401-34900` | Monthly attendance grid, clock-in status, overtime hours, leave tracker. | Calendar table lacks sticky column for employee names during horizontal scroll; attendance status dots lack accessible labels. | Implement sticky employee name column; add accessible tooltips and clear color contrast to status dots. | `HRManager`, `API.attendance` | Medium |
| **HR: Monthly Payroll & Payslips** | `index.html:34901-35809` | Salary slip calculation sheet, allowances, deductions, advance salary adjustment, payslip PDF download. | Calculation table uses dense non-standard font; payslip download button has hardcoded purple color (`#7c3aed`). | Standardize calculation sheet with clean financial table styling; unify PDF download button with primary action tokens. | `HRManager`, `API.payroll` | High (Payroll calculation accuracy) |
| **Take a Break: Maharashtra Journey & Arcade** | `TakeABreakHub.js`, `MaharashtraJourney.js`, `SnakeClassic.js` | Cultural journey progression, Sahyadri landmark cards, Snake Classic arcade canvas game with retro audio and Peshwa coin sprite. | Distinct thematic component. Currently isolated with custom retro styles. | Preserve authentic cultural and arcade aesthetics; ensure outer container smoothly respects the active modern gold or retro frame. | `TakeABreakHub.js`, audio oscillators | Low |
| **Settings: Appearance & Theme Engine** | `index.html:30449-30748` | Theme family switcher (Modern vs Classic Retro '98), Light/Dark toggle, scheduler (Sunset, Manual, System, Custom), live preview boxes, status bar switch. | Preview box for Modern theme displays old blue palette; schedule buttons have inconsistent horizontal margins; custom time inputs lack labels. | Update Modern preview box to showcase the new RupeeCRM Gold Design System; clean up button grid and time inputs; preserve 100% of retro switching logic. | `SettingsAppearance`, `themeToggle.js` | High (`p3-settings-system.spec.ts`) |
| **Settings: Notifications & Sounds** | `index.html:32260-32423` | Notification channel toggles, sound alert preferences, desktop notification permissions, quiet hours. | Toggle switches have misaligned labels on medium screens; sound test button lacks loading indicator. | Clean up form grid with CSS grid; standardize switch components; add visual feedback to sound test. | `SettingsNotifications` | Low |
| **Settings: Licensing, EULA & Security** | `index.html:30923-32259` | License key input, trial days remaining countdown banner, plan details, Feature Lock PIN setup, session timeout. | License banner uses hardcoded amber gradients (`#fffbeb`, `#fef3c7`); PIN input fields lack masked bullet styling. | Restyle license banner with refined gold accent border; standardize PIN inputs with secure numeric input styling. | `SettingsLicensingEula`, `SettingsSecurity`, `FeatureLock` | Critical (Security & licensing boundaries) |
| **Settings: Backup & Data Recovery** | `index.html:26978-27950` | Full backup export (AES-256 encrypted), selective firm restore/merge, DPAPI cloud sync status, safety snapshot rollback. | Warning alerts use disparate red backgrounds; restore file dropzone has fixed dotted border without drag-hover state. | Style backup vaults with elevated cards; standardize warning banners with `--warning` tokens; polish file dropzone. | `BackupRestore`, `RecoveryTool`, `API.backup` | Critical (`backup-restore.spec.ts`) |
| **Settings: Software Updates** | `index.html:33200-33717` | OTA update check, Ed25519 signature validation status, download progress SSE stream, restart supervisor trigger. | Progress bar uses standard blue fill; update release notes box has raw unformatted text. | Style progress bar with animated gold sheen; wrap release notes in structured card with markdown rendering. | `SettingsHub`, `UpdateService`, `LauncherMain` | High (`ota-update-lifecycle.spec.ts`) |
| **Search: Global Omnisearch Modal** | `index.html:2088-2250`, `style.css:6960-7850` | Omnibar dialog (`Cmd+K` / `/`), NLP query tokenizer, quick math results, business entity jump cards, category guide chips. | Large modal with over 15 distinct internal card classes; dark mode overrides are partially duplicate in `style.css` (lines 7703–7708). | Consolidate omnisearch card styles to inherit directly from CSS design tokens; modernize input field with gold focus ring. | `GlobalSearchModal`, `omnibarPipeline.js` | High (`p0-app-shell.spec.ts`) |
| **Tool: Floating Desk Calculator** | `calculator.js:1-1166`, `style.css:8800-8880` | Casio-style handheld calculator window, solar panel strip, LCD numeric screen, tactile 3D buttons, GST +/- keys. | Authentic Casio handheld skeuomorphism. | Preserve authentic physical calculator aesthetics; ensure floating window header and close button harmonize with design tokens. | `calculator.js`, `RupeeCRMCalculator` | Low (`p4-calculator.spec.ts`) |
| **Auth: Login Screen** | `index.html:29296-30448` | Employee PIN / Master Password login, tenant firm selector, remember me checkbox, session initialization. | Login card uses old blue gradient accent; submit button has hardcoded blue background (`#4f46e5`). | Restructure login card with elegant gold-accented brand header, prominent Rupee gold coin logo, and gold primary login action. | `Login`, `API.auth`, `TenantContext` | Critical (`p0-app-shell.spec.ts`) |
| **Gates: Feature Lock Screen** | `featureLock.js:1-1510` | Full-page PIN lock screen intercepting protected modules (Invoices, Settings, HR, Firm), error shake animation, lockout timer. | Lock icon uses generic gray; PIN display dots have uneven spacing; error text uses un-tokenized red. | Polish lock screen with refined security card, masked bullet indicators, and gold submit button. | `FeatureLock.LockScreen` | High (`AUDIT-038`) |
| **Gates: EULA Gate Modal** | `index.html:31864-32259` | Mandatory legal agreement gate on first run / update, version acceptance tracking, scroll-to-bottom validator, decline exit. | Accept button remains disabled until scrolled, but disabled state has poor visual distinction; modal scrollbar lacks styling. | Clean up legal text container with styled scrollbar; provide clear visual feedback when bottom is reached and button activates. | `EulaGateModal`, `EulaService` | Medium (`p3-eula.spec.ts`) |
| **Overlays: Fluid Workspace Sync Capsule** | `index.html:36510-36624`, `style.css:5750-5890` | Non-blocking floating status capsule during `billsoft:app-refresh`, orbital telemetry animation, retro file-copy animation in Classic mode. | Already has modern vs retro conditional rendering, but modern mode uses hardcoded blue orbital rings. | Update orbital rings to use luminous gold and emerald sheen sweeps in modern mode, while keeping retro file-fly intact. | `RupeeCRMSyncOverlay`, `App` | Low |
| **Overlays: Toast Stack (ToastDeck)** | `index.html:262-397`, `style.css:1500-1750` | Multi-deck toast stack with hover expansion, duplicate trigger multiplier badge (`×2`, `×3`), progress countdown line. | Multiplier badge has hardcoded background `#4f46e5`; progress bar uses blue fill. | Style multiplier badge and progress indicator using design token variables (`--primary`, `--primary-dark`). | `Toast`, `window.showToast` | Low (`p3-toast-visual.spec.ts`) |

---

## Section 4 — Component Inventory and Duplication

### 4.1 Canonical Components Already Available
The codebase contains a set of mature, battle-tested UI components that have been iteratively hardened:
1. `Toast` (`index.html:262-397`): Robust notification deck with deduplication counters, hover expansion, and auto-dismiss.
2. `ConfirmDialog` (`index.html:398-461`): Accessible modal for destructive confirmation (cancellations, deletions).
3. `OutOfStockDialog` (`index.html:462-550`): Specialized alert dialog preventing overselling of non-tracked items.
4. `SessionTimerBadge` (`index.html:36330-36508`): Inactivity countdown badge with auto-lock and click-to-reset.
5. `RupeeCRMSyncOverlay` (`index.html:36510-36624`): Non-blocking fluid reload capsule with dual-theme awareness.
6. `GlobalSearchModal` (`index.html:2088-2250`): Full-featured omnisearch modal with keyboard trapping and fuzzy matching.
7. `Customer360Modal` (`index.html:19303-19686`): Unified customer ledger, invoice history, and balance drawer.
8. `RecordPaymentModal` (`index.html:15559-15894`): Transactional modal for recording partial and full payments.
9. `RupeeCRMLogo` & `RupeeCRMRetroLogo` (`index.html:151-261`): Dual vector brandmarks for Modern and Retro themes.

### 4.2 Duplicate & Competing Implementations
Direct source code inspection reveals multiple competing implementations for equivalent UI patterns:

#### A. Competing Button Implementations
- **Implementation 1 (Standard CSS Classes):** `.btn.btn-primary`, `.btn.btn-outline`, `.btn.btn-ghost`, `.btn.btn-secondary`, `.btn.btn-danger` defined in `style.css:163-194` and `style.css:800-890`.
- **Implementation 2 (Inline Styled Primary Buttons):**
  - `index.html:8098`: `style: { backgroundColor: '#6366f1', borderColor: '#6366f1' }` (Indigo)
  - `index.html:8104`: `style: { backgroundColor: '#dc2626', borderColor: '#dc2626' }` (Red)
  - `index.html:8105`: `style: { backgroundColor: '#059669', borderColor: '#059669' }` (Green)
  - `index.html:38484`: `style: { padding: '2px 10px', fontSize: '0.75rem', background: '#d97706', borderColor: '#b45309' }` (Amber)
  - `index.html:38671`: `style: { fontSize: '0.75rem', padding: '3px 10px', fontWeight: 600 }` (Custom padding)
- **Implementation 3 (Sidebar Quick Create Split):** `index.html:4810-4856`: Custom flex buttons with 0px border radius and manual divider border (`rgba(255,255,255,0.2)`).
- **Implementation 4 (Retro Overrides):** `:root.classic .btn` (lines 163–182) enforcing beveled 3D borders and 0px radii.

#### B. Competing Search & Filter Inputs
- **Implementation 1 (Topbar Omnisearch Pill):** `.topbar-search-pill` (`style.css:2257`, height 36px, rounded 18px).
- **Implementation 2 (Full-Screen Omnisearch Input):** `.omni-search-input` (`style.css:7024`, height 52px, bottom border only).
- **Implementation 3 (Autocomplete Comboboxes):** `ProductSearchInput` (`index.html:12706`) and `CustomerSearchInput` (`index.html:12947`) with independent dropdown rendering, positioning, and keyboard arrow handlers.
- **Implementation 4 (Paperwork Combobox):** `SearchableEntityCombobox` (`index.html:25688`) with distinct DOM layout.

#### C. Inconsistent Modal Dialog Wrappers
- `index.html:38565` (`showInboxModal`): Fixed overlay `rgba(0,0,0,0.5)`, `.modal-card`, `max-width: 560px`.
- `index.html:38720` (`showExpiryModal`): Fixed overlay `rgba(0,0,0,0.65)`, `backdrop-filter: blur(4px)`, `.modal-card`, `max-width: 540px`, `border-radius: 16px`.
- `index.html:398` (`ConfirmDialog`): Fixed overlay `rgba(0,0,0,0.5)`, `.confirm-dialog`, `max-width: 440px`.
- `index.html:744` (`InvoicePrintModal`): Fixed overlay, `.invoice-print-dialog`, `max-width: 900px`.

### 4.3 Consolidation & Migration Strategy
1. **Preserve External CSS Selectors:** Ensure all consolidated components retain classes relied upon by Playwright tests (`.btn`, `.btn-primary`, `.btn-outline`, `.btn-ghost`, `.nav-item`, `.topbar-search-pill`, `.sub-tabs-bar button`).
2. **Eliminate Hardcoded Inline Colors:** Remove ad-hoc `backgroundColor` and `borderColor` overrides from `index.html` and map them to semantic CSS variables (`--primary`, `--danger`, `--success`, `--warning`).
3. **Canonical Size Variants:** Establish strict button height tokens (`--btn-height-sm: 28px`, `--btn-height-md: 36px`, `--btn-height-lg: 44px`) so components across all tabs have mathematically consistent visual rhythms.

---

## Section 5 — Proposed RupeeCRM Design System

### 5.1 Design Tokens (CSS Variables)
The proposed design system replaces `:root.light-1` and `:root.dark-1` with cohesive, accessible tokens while leaving `:root.classic` and `:root.classic-dark` isolated.

```css
/* ============================================================
   RUPEECRM GOLD DESIGN SYSTEM TOKENS (LIGHT & DARK MODERN)
   ============================================================ */

/* ---------- Modern Light (Rupee Gold & Warm Canvas) ---------- */
:root.light-1,
:root[data-theme='light'] {
  /* Brand Gold Palette */
  --brand-gold-50:  #FFFDF5;
  --brand-gold-100: #FEF8E2;
  --brand-gold-200: #FDF0C0;
  --brand-gold-300: #F9E292;
  --brand-gold-400: #F3CF55;
  --brand-gold-500: #D4AF37; /* Canonical Rupee Brand Gold */
  --brand-gold-600: #B89223;
  --brand-gold-700: #937117;
  --brand-gold-800: #745615;
  --brand-gold-900: #584013;

  /* Primary Brand Actions (Accessible Contrast) */
  --primary:        #2E5A36; /* Deep Moss Green for primary contrast (Reference Image 1) */
  --primary-hover:  #24482B;
  --primary-light:  #EBF3EC;
  --primary-50:     #F3F7F4;
  --accent-gold:    #D4AF37; /* Specular brand gold highlight */
  --accent-gold-bg: rgba(212, 175, 55, 0.12);

  /* Semantic Feedback */
  --success:        #0D9488;
  --success-light:  #E6F7F5;
  --warning:        #D97706;
  --warning-light:  #FEF3C7;
  --danger:         #DC2626;
  --danger-light:   #FEE2E2;
  --info:           #0284C7;
  --info-light:     #E0F2FE;

  /* Surfaces & Hierarchy (Velo-inspired Warm Mint/Cream Canvas) */
  --bg:             #F5F7F4; /* Soft warm canvas background */
  --bg-card:        #FFFFFF; /* Pure elevated white card surface */
  --bg-secondary:   #FAFCF9;
  --bg-card-header: #FFFFFF;
  --bg-subtle:      #EEF2ED;
  --surface-sunken: #E8ECE6;
  --bg-hover:       #EFF3EE;

  /* Typography */
  --text:           #1A221E; /* Deep Charcoal Green for high legibility */
  --text-secondary: #4A5850;
  --text-muted:     #7C8B82;

  /* Borders & Dividers */
  --border:         #E1E7E0;
  --border-light:   #EDF2EC;
  --border-focus:   #2E5A36;

  /* Shadows (Multi-layer Ambient Soft Shadows) */
  --shadow-sm:      0 1px 2px rgba(26, 34, 30, 0.04);
  --shadow:         0 2px 6px -1px rgba(26, 34, 30, 0.06), 0 1px 4px -1px rgba(26, 34, 30, 0.03);
  --shadow-md:      0 8px 16px -2px rgba(26, 34, 30, 0.06), 0 4px 8px -2px rgba(26, 34, 30, 0.03);
  --shadow-lg:      0 16px 28px -4px rgba(26, 34, 30, 0.08), 0 8px 12px -4px rgba(26, 34, 30, 0.04);

  /* Geometry & Scaling */
  --radius-sm:      6px;
  --radius:         10px;
  --radius-card:    18px; /* Distinctive curved card radius (Reference Image 1) */
  --radius-pill:    9999px; /* Pill action buttons */

  /* Navigation & Controls */
  --sidebar-bg:     #FFFFFF; /* Modern clean white sidebar */
  --sidebar-text:   #4A5850;
  --sidebar-active: #2E5A36;
}

/* ---------- Modern Dark (Obsidian & Antique Gold) ---------- */
:root.dark-1,
:root[data-theme='dark'] {
  --primary:        #D4AF37; /* Metallic gold primary for dark mode */
  --primary-hover:  #E6CA65;
  --primary-light:  rgba(212, 175, 55, 0.15);
  --primary-50:     rgba(212, 175, 55, 0.08);
  --accent-gold:    #F5D061;
  --accent-gold-bg: rgba(245, 208, 97, 0.16);

  --success:        #10B981;
  --success-light:  rgba(16, 185, 129, 0.15);
  --warning:        #F59E0B;
  --warning-light:  rgba(245, 158, 11, 0.15);
  --danger:         #EF4444;
  --danger-light:   rgba(239, 68, 68, 0.15);
  --info:           #38BDF8;
  --info-light:     rgba(56, 189, 248, 0.15);

  --bg:             #0B0F0D; /* Obsidian night canvas */
  --bg-card:        #131A16; /* Elevated dark card surface */
  --bg-secondary:   #17201C;
  --bg-card-header: #131A16;
  --bg-subtle:      #0F1512;
  --surface-sunken: #080C0A;
  --bg-hover:       #1D2722;

  --text:           #F2F6F3;
  --text-secondary: #97A69E;
  --text-muted:     #65746C;

  --border:         #232F28;
  --border-light:   #1A241F;
  --border-focus:   #D4AF37;

  --shadow-sm:      0 1px 2px rgba(0, 0, 0, 0.3);
  --shadow:         0 4px 8px rgba(0, 0, 0, 0.4);
  --shadow-md:      0 8px 20px rgba(0, 0, 0, 0.45);
  --shadow-lg:      0 16px 32px rgba(0, 0, 0, 0.55);

  --sidebar-bg:     #0F1512;
  --sidebar-text:   #97A69E;
  --sidebar-active: #D4AF37;
}
```

### 5.2 Typography Specification & Fallbacks
- **Primary Typeface:** `Inter`, with robust local system font fallbacks ensuring immediate offline desktop fidelity:
  ```css
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
  ```
- **Financial & Numeric Figures:** Enforce tabular numbers to guarantee perfect decimal point alignment across invoice totals, price lists, and ledger balances:
  ```css
  .tabular-nums, .data-table td.numeric, .kpi-value, .stat-value {
    font-variant-numeric: tabular-nums;
    font-feature-settings: "tnum" 1;
  }
  ```
- **Scale Hierarchy:**
  - Page Headings: 24px (`1.5rem`), 700 weight, `-0.02em` letter spacing.
  - Card Titles: 16px (`1.0rem`), 600 weight.
  - KPI Stat Values: 28px (`1.75rem`), 700 weight, tabular numerals.
  - Body Text: 14px (`0.875rem`), 400 weight, 1.5 line height.
  - Table Data & Labels: 13px (`0.8125rem`), 500 weight.
  - Micro Badges & Telemetry: 11px (`0.6875rem`), 600 weight, uppercase, `+0.04em` tracking.

---

## Section 6 — Coin Asset and Animation Architecture

### 6.1 Vector Coin Asset (`RupeeGoldCoin`)
To ensure crisp rendering at all desktop display resolutions (100% to 250% DPI scale on Windows/macOS retina screens) and zero HTTP request overhead during offline operation, the gold coin is specified as a canonical SVG component:

```
┌────────────────────────────────────────────────────────┐
│                   RupeeGoldCoin SVG                    │
│                                                        │
│                  ╭────────────────╮                    │
│               ╭──╯   Outer Rim    ╰──╮                 │
│             ╭─╯ ░░░░░░░░░░░░░░░░░░░░ ╰─╮               │
│            ╭╯  ░░  Inner Bevel   ░░   ╰╮               │
│           ╭╯  ░░  ╭────────────╮  ░░   ╰╮              │
│           │   ░░  │     ₹      │  ░░    │              │
│           │   ░░  │  Embossed  │  ░░    │              │
│           ╰╮  ░░  ╰────────────╯  ░░   ╭╯              │
│            ╰╮  ░░ Specular Gleam ░░   ╭╯               │
│             ╰─╮ ░░░░░░░░░░░░░░░░░░░░ ╭─╯               │
│               ╰──╮                ╭──╯                 │
│                  ╰────────────────╯                    │
└────────────────────────────────────────────────────────┘
```

#### SVG Gradients & Architecture
- Outer Edge: Linear gradient from `#F3CF55` (top highlight) to `#937117` (bottom depth).
- Inner Coin Face: Radial gradient centered at 35% 35% (`#FFFDF5` highlight, `#D4AF37` body, `#805B00` rim shadow).
- Embossed Rupee Character (`₹`): Dual path with dark shadow offset (`#745615`, dy: 1px) and light specular bevel (`#FFF8E2`, dy: -1px).
- Diameter Presets:
  - Micro (`size={16}`): Inline table currency and badge watermarks.
  - Standard (`size={28}`): Topbar logo and card headers.
  - Celebration (`size={48}`): Modal confirmations and login reveal.
  - Hero (`size={80}`): Splash, onboarding, and milestone trophies.

### 6.2 Animation Lifecycle & Trigger API
Animations are triggered via lightweight DOM events without coupling business logic to presentation:
```javascript
// Centralized celebration dispatcher
window.dispatchCoinCelebration = function(type, options = {}) {
  window.dispatchEvent(new CustomEvent('rupeecrm:coin-celebrate', {
    detail: { type, ...options }
  }));
};
```

### 6.3 Animation Keyframes & Motion Specification
All animations are hardware-accelerated (`transform` and `opacity` only) and respect accessibility constraints:

```css
/* 1. Continuous Coin Spin (Loading State) */
@keyframes rcrm-coin-spin {
  0%   { transform: rotateY(0deg); }
  50%  { transform: rotateY(180deg) scale(1.04); }
  100% { transform: rotateY(360deg); }
}

/* 2. Success Transaction Glint (Brief Shimmer) */
@keyframes rcrm-coin-glint {
  0%   { filter: brightness(1); transform: scale(1); }
  35%  { filter: brightness(1.35) drop-shadow(0 0 12px rgba(212, 175, 55, 0.6)); transform: scale(1.08); }
  70%  { filter: brightness(1.15) drop-shadow(0 0 6px rgba(212, 175, 55, 0.3)); transform: scale(1.02); }
  100% { filter: brightness(1); transform: scale(1); }
}

/* 3. Welcome / Login Reveal */
@keyframes rcrm-coin-reveal {
  0%   { opacity: 0; transform: scale(0.6) translateY(12px); }
  60%  { opacity: 1; transform: scale(1.06) translateY(-2px); }
  100% { opacity: 1; transform: scale(1.0) translateY(0); }
}

/* Accessibility: Full reduced-motion suppression */
@media (prefers-reduced-motion: reduce) {
  .rcrm-coin-animated {
    animation: none !important;
    transform: none !important;
    filter: none !important;
  }
}
```

### 6.4 Use-Case Evaluation Matrix
| Candidate Use Case | Recommended Behavior | Motion Type | Duration | Rationale |
| :--- | :--- | :--- | :--- | :--- |
| **Initial App Boot / Loading** | Small gold rupee coin rotating smoothly. | `rcrm-coin-spin` | 1.2s infinite | Replaces generic spinning circle with distinctive brand identity. |
| **Table / Section Data Fetch** | Standard muted spinner or skeleton card. | No coin motion | N/A | Excessive coin motion during routine list pagination causes interaction fatigue. |
| **Successful Invoice Creation** | Brief gold glint on confirmation toast. | `rcrm-coin-glint` | 600ms once | Satisfying, non-blocking positive tactile feedback for retail sales. |
| **Successful Payment Settlement** | Coin glint with subtle particle sheen. | `rcrm-coin-glint` | 700ms once | Reinforces successful collection of outstanding funds. |
| **Successful Login** | Centered coin reveal into topbar logo. | `rcrm-coin-reveal` | 500ms once | Warm, premium greeting upon entering the workspace. |
| **Destructive / Error Actions** | Clear red danger badge (`--danger`). | **Zero coin motion** | N/A | Strictly avoid using brand coin in negative or error states. |

---

## Section 7 — Screen Restructuring Proposals

### 7.1 Dashboard Restructuring (Reference Image 1 Alignment)
```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│  Velo-Inspired Dashboard Restructuring Blueprint                                                 │
├──────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Top Row: Metrics Grid (4-6 Rounded Cards, 16px Radius, Subtle Tint, Period Dropdown Chip)        │
│ ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐             │
│ │ [₹] Revenue      │ │ [✓] Paid Sales   │ │ [⏰] Receivables  │ │ [📄] Quotes      │             │
│ │ ₹ 1,42,500       │ │ ₹ 1,18,000       │ │ ₹ 24,500         │ │ 12 Active        │             │
│ │ [This Month ▾]   │ │ [92% Coll. ▾]    │ │ [4 Overdue ▾]    │ │ [₹38k Value ▾]   │             │
│ └──────────────────┘ └──────────────────┘ └──────────────────┘ └──────────────────┘             │
│                                                                                                  │
│ Split Body: Dual-Column Task & Activity Architecture                                             │
│ ┌──────────────────────────────────────────────┐ ┌──────────────────────────────────────────────┐│
│ │ 🕒 Recent Business Activities            [⋮] │ │ 📋 Immediate Store Tasks          [+ Add New]││
│ ├──────────────────────────────────────────────┤ ├──────────────────────────────────────────────┤│
│ │ [📄] Rahul Patil created Inv #1042 (₹4,200)   │ │ [ ] Settle overdue invoice for Sharma Traders 🚩││
│ │ [₹] Cash settlement recorded: Inv #1039       │ │ [ ] Reorder 25 units of Amul Butter 500g    🚩││
│ │ [📦] Received PO #28 from Metro Wholesale     │ │ [✓] Send monthly GST summary to CA          🏳️││
│ │ [📝] Generated Quotation #88 for Vijay Auto   │ │ [ ] Follow up on Quotation #88              🏳️││
│ └──────────────────────────────────────────────┘ └──────────────────────────────────────────────┘│
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
```
- **Existing Problem:** Current dashboard features an awkward toggle between "Business View" and "Personal View" that conceals critical financial data behind segmented buttons, while stacking long bar charts vertically.
- **Proposed Restructuring:** A single, cohesive command center. Top metric cards feature soft gradient pastel backdrops with period selector chips (`This Month ▾`). The lower half provides a side-by-side split between an automated Recent Activity stream and an actionable Task Checklist with priority flags (`🚩`).
- **Preserved Behavior:** All event subscriptions (`billsoft:app-refresh`, goal deduction, streak increments) and API aggregations remain identical.

### 7.2 Sidebar & Header Modernization
- **Existing Problem:** The 250px dark sidebar (`#1E293B`) visually severs the workspace from the content canvas and uses a cramped dual-split button with hardcoded borders.
- **Proposed Restructuring:** Clean, elevated white sidebar matching Reference Image 1. Features an "Onboarding (×)" progress chip at the top, a prominent "+ Create New Invoice" pill action button, and an active pill navigation state styled with deep moss green (`#2E5A36`) and radial highlight.
- **Preserved Behavior:** All page navigation handlers (`onNavigate`) and deep-link routing contracts (`billsoft:navigate-subtab`) are preserved 100%.

### 7.3 Invoices & Quotes Hub Overview
- **Existing Problem:** Overview screen contains redundant filter buttons and an orange notification banner with hardcoded styling.
- **Proposed Restructuring:** Replace rectangular sub-tab buttons with clean rounded filter pills (`border-radius: 9999px`) featuring active pill counts and subtle gold badge highlights.

---

## Section 8 — Prioritized Implementation Backlog

### Scoring Methodology
- **User Impact (UI, 1–10):** Visual clarity, brand premium feel, and daily usability improvement.
- **Consistency Impact (CI, 1–10):** Elimination of hardcoded styles and cross-screen standardization.
- **Performance Impact (PI, 1–10):** Rendering efficiency, paint reduction, and memory footprint.
- **Effort (EF, 1–10):** Implementation complexity and time investment (treated as cost).
- **Regression Risk (RR, 1–10):** Likelihood of breaking existing workflows, tests, or tenants (treated as cost).
- **Priority Calculation:**
  $$\text{Priority Score} = \frac{\text{UI} \times 1.5 + \text{CI} \times 1.5 + \text{PI} \times 1.0}{(\text{EF} \times 0.8) + (\text{RR} \times 1.2)}$$

```
Priority Ranking:
★ P0 (Critical Path, Score ≥ 3.5)
★ P1 (High Impact Foundation, Score 2.5–3.4)
★ P2 (Core Hub Upgrades, Score 1.8–2.4)
★ P3 (Polish & Refinement, Score < 1.8)
```

| ID | Task | Files / Components | Problem Evidence | Proposed Solution | UI | CI | PI | EF | RR | Dependencies | Validation | Priority |
| :--- | :--- | :--- | :--- | :--- | :---: | :---: | :---: | :---: | :---: | :--- | :--- | :---: |
| **UI-001** | Test Suite Baseline & Selector Map | `ui-regression/tests/*` | UI changes could accidentally invalidate Playwright DOM selectors (`.nav-item`, `.sidebar-logo`). | Map and freeze all E2E test selectors before touching CSS or markup. | 5 | 8 | 5 | 3 | 1 | None | `npm test` passes cleanly | **P0** (5.1) |
| **UI-002** | Centralize CSS Design Tokens | `billsoft/src/main/webapp/css/style.css:9-72` | Standard indigo/blue colors, hardcoded hex values across components. | Define complete RupeeCRM Gold Design System tokens in `:root.light-1` and `:root.dark-1`. | 9 | 10 | 7 | 4 | 2 | UI-001 | Visual token check in dev | **P0** (4.9) |
| **UI-003** | Retro Theme Isolation Verification | `style.css:73-200,366-500` | Risk of accidental CSS leakage from modern tokens into Win95/98 retro theme. | Verify `:root.classic` and `:root.classic-dark` overrides maintain 0px radii and beveled styling. | 6 | 9 | 5 | 3 | 2 | UI-002 | Theme switch E2E tests | **P0** (4.3) |
| **UI-004** | Canonical Button Primitive | `style.css:800-890`, `index.html` | 5 competing button styles and arbitrary inline hex colors (`#6366f1`, `#dc2626`). | Standardize `.btn-primary`, `.btn-outline`, `.btn-ghost` with fixed height and radius tokens. | 8 | 9 | 6 | 4 | 3 | UI-002 | Cross-browser button audit | **P1** (3.7) |
| **UI-005** | Form Control & Search Input Tokens | `style.css:195-207`, `index.html` | Multiple input heights, diverging focus rings, and disparate search bars. | Unify text inputs, selects, and search fields with standard focus ring and 36px/40px scale. | 8 | 9 | 6 | 5 | 3 | UI-004 | Input focus & typing tests | **P1** (3.4) |
| **UI-006** | Vector Rupee Gold Coin Component | `index.html:110-150` | No branded coin asset exists; only blue chevrons and canvas sprite. | Implement canonical `RupeeGoldCoin` SVG with 4 size presets and gold metallic gradient. | 9 | 7 | 8 | 4 | 1 | UI-002 | Scalability check (16px to 80px)| **P1** (3.8) |
| **UI-007** | Coin Animation Engine & Event API | `style.css`, `index.html` | Scattered keyframe animations with no standardized transactional celebration. | Implement `rcrm-coin-spin`, `rcrm-coin-glint`, `rcrm-coin-reveal` with reduced-motion fallback. | 8 | 7 | 7 | 4 | 2 | UI-006 | Motion & reduced-motion audit | **P1** (3.3) |
| **UI-008** | Sidebar & Topbar Shell Overhaul | `index.html:4746-4871,38235-38449` | Dark sidebar clashes with canvas; cramped split button; cluttered topbar clusters. | Restyle sidebar with Velo-inspired clean canvas, prominent pill action, and rounded search bar. | 9 | 9 | 6 | 5 | 3 | UI-004, UI-006 | `p0-app-shell.spec.ts` | **P1** (3.3) |
| **UI-009** | Dashboard Velo Layout Restructure | `index.html:10748-12700` | Segmented views hide data; inconsistent KPI cards; long vertical bar charts. | Restructure into Velo-inspired KPI grid and dual-column feed (Recent Activities + Task list). | 9 | 8 | 7 | 6 | 4 | UI-005, UI-008 | `Dashboard` E2E tests | **P1** (2.8) |
| **UI-010** | Invoices & Quotes Hub Restyling | `index.html:35810-36320` | Filter buttons lack active states; inconsistent table padding; orange alert banner. | Modernize KPI strip, apply pill filter buttons, and align table typography with tabular figures. | 8 | 8 | 6 | 5 | 3 | UI-004, UI-005 | `p1-invoices.spec.ts` | **P2** (2.6) |
| **UI-011** | Keep-Alive Multi-Tab Form Preservation | `index.html:13115-14690` | Extreme financial regression risk if form re-renders or tab state is discarded. | Apply design tokens to line items and summary card without altering DOM state bindings. | 7 | 8 | 6 | 6 | 5 | UI-005, UI-010 | Multi-tab persistence test | **P2** (2.1) |
| **UI-012** | Customer 360 Drawer Modernization | `index.html:19303-19686` | Non-standard tab classes; hardcoded border lines; cramped ledger table. | Apply canonical drawer primitive, standard sub-tabs, and tabular balance numbers. | 7 | 8 | 6 | 4 | 2 | UI-004, UI-005 | `p1-customers.spec.ts` | **P2** (2.7) |
| **UI-013** | Payment & Settlement Modal Upgrades | `index.html:15559-15894,19687-20088`| Hardcoded green backgrounds; amount input lacks prominent currency styling. | Modernize payment modal with large currency display and gold coin settlement glint. | 8 | 7 | 7 | 4 | 3 | UI-006, UI-007 | Payment calculation test | **P2** (2.5) |
| **UI-014** | Inventory & Stock Forecasting Drawer | `ProductIntelligence.js`, `index.html`| Stock drawer and forecast charts have internal hardcoded colors. | Harmonize SVG forecast chart strokes and recommendation badges with design tokens. | 7 | 8 | 6 | 5 | 3 | UI-002, UI-005 | `smart-reorder-audit.spec.ts` | **P2** (2.3) |
| **UI-015** | Planner Kanban Board Restyling | `index.html:7228-8750` | Kanban cards use outdated borders; column headers clash in dark mode. | Restyle task cards with Velo checklist aesthetic (rounded checkboxes, priority flags). | 7 | 7 | 6 | 4 | 2 | UI-004, UI-005 | `p2-planner-hr.spec.ts` | **P2** (2.4) |
| **UI-016** | Settings & Theme Switcher Alignment | `index.html:30449-30748` | Preview box shows old blue theme; schedule buttons have uneven margins. | Update preview boxes to showcase RupeeCRM Gold System; clean up schedule controls. | 6 | 8 | 5 | 3 | 2 | UI-002, UI-003 | `p3-settings-system.spec.ts` | **P3** (2.2) |
| **UI-017** | Omnisearch Modal & Guide Chips | `index.html:2088-2250`, `style.css` | Duplicate dark mode overrides; multiple card sub-classes. | Consolidate omnisearch card styles to inherit directly from CSS design tokens. | 7 | 7 | 7 | 4 | 2 | UI-005, UI-008 | `omnisearch-guide-redesign.spec.ts` | **P3** (2.3) |
| **UI-018** | Toast Deck & Overlays Polish | `index.html:262-397,36510-36624` | Multiplier badge has hardcoded background; sync capsule rings use old blue. | Restyle toast multiplier and fluid sync orbital rings using design token variables. | 6 | 6 | 6 | 3 | 1 | UI-002, UI-007 | `p3-toast-visual.spec.ts` | **P3** (2.2) |
| **UI-019** | Accessibility & Contrast Verification | Full Application | Some light text on muted backgrounds may fail WCAG AA contrast. | Run automated WCAG AA audit on all gold and green text combinations ($\ge 4.5:1$). | 8 | 8 | 5 | 4 | 1 | All UI tasks | Axe-core accessibility scan | **P3** (2.9) |
| **UI-020** | Responsive Viewport Optimization | Full Application | Modals and tables wrap awkwardly on 1024x768 and 390px mobile viewports. | Validate responsive drawer collapse and eliminate horizontal scrollbars. | 7 | 7 | 6 | 5 | 2 | All UI tasks | `responsive-viewports.spec.ts` | **P3** (2.3) |

---

## Section 9 — Feasibility and Risk Assessment

### 9.1 Technical Feasibility
The proposed UI overhaul is **100% technically feasible** within the current repository architecture:
1. **Zero New Build Tools Required:** The overhaul does not require introducing Vite, Webpack, or Babel. It operates cleanly within the existing pre-bundled React 18 and vanilla CSS architecture.
2. **Centralized CSS Lever:** Because 90%+ of application styling originates in `billsoft/src/main/webapp/css/style.css`, updating the root design tokens and component utility classes will instantly transform the application without requiring invasive rewrites of business logic.
3. **No External Asset Dependencies:** The Rupee gold coin and its animation system are implemented via pure inline SVG and CSS `@keyframes`, ensuring immediate zero-latency offline availability without external image downloads or WebGL runtimes.

### 9.2 Key Sensitive Boundaries & Invariants
```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                             CRITICAL SYSTEM INVARIANTS                                           │
├──────────────────────────────────────────────────────────────────────────────────────────────────┤
│ 1. Tenant Scoping: Every request must carry X-Firm-Id via TenantInterceptor.                     │
│ 2. Financial Precision: Math and tax calculations (CGST, SGST, IGST) must remain untouched.      │
│ 3. Form Keep-Alive: Multi-tab invoice editor state must never be destroyed during restyling.     │
│ 4. Retro Independence: :root.classic must remain 100% pixel-perfect Windows 95/98 styling.      │
│ 5. Test Selectors: Playwright E2E selectors (.nav-item, .sidebar-logo, etc.) must be preserved.  │
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
```

### 9.3 Potential Regressions & Mitigation Strategy
1. **Regression Risk: Breaking Multi-Tab Document Editor State**
   - *Cause:* Re-wrapping `InvoiceForm` in new parent elements can trigger unmounting in React, resetting line items and customer selections.
   - *Mitigation:* Leave `open-forms-keep-alive-wrapper` and its direct child mappings intact. Only modify CSS class names and styles on outer containers.
2. **Regression Risk: Breaking E2E Playwright Tests**
   - *Cause:* Changing class names or element tags will break test locators (`page.locator('.sidebar-nav-item')`).
   - *Mitigation:* Preserve all existing class names as structural hooks, adding design system classes alongside them (e.g. `class="sidebar-nav-item rcrm-nav-pill"`).
3. **Regression Risk: Retro Theme Distortion**
   - *Cause:* CSS token changes bleeding into classic mode.
   - *Mitigation:* Maintain `:root.classic` and `:root.classic-dark` rules with explicit `!important` declarations, ensuring zero radius and authentic retro fonts remain strictly enforced.
4. **Regression Risk: Gold Accent Contrast Deficiencies (WCAG)**
   - *Cause:* Yellow gold (`#D4AF37`) on white backgrounds has an insufficient contrast ratio (~1.8:1), violating WCAG AA accessibility standards.
   - *Mitigation:* Never use light gold for primary body text or small labels. Gold is strictly reserved for decorative borders, card highlights, badges with dark text, and specular coin gleams. Primary text uses high-contrast charcoal green (`#1A221E`, 14:1 contrast ratio) and primary actions use deep moss green (`#2E5A36`, 6.5:1 contrast ratio).

---

## Section 10 — Regression Test Strategy

A rigorous four-tier verification harness will protect existing capabilities:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        4-TIER REGRESSION HARNESS                       │
├────────────────────────────────────────────────────────────────────────┤
│ Tier 1: Core Financial Business Tests (Maven)                         │
│ ./mvnw test -Dtest=EndToEndBusinessLogicCrossModuleTest -pl billsoft   │
│ Validates invoice sequence, stock deduction, and balance calculations. │
├────────────────────────────────────────────────────────────────────────┤
│ Tier 2: Application Shell & Navigation Suite (Playwright)              │
│ npx playwright test tests/p0-app-shell.spec.ts                         │
│ Asserts zero JS console errors, clean mount, and navigation routes.    │
├────────────────────────────────────────────────────────────────────────┤
│ Tier 3: Core Transactional Workflows (Playwright)                      │
│ npx playwright test tests/p1-invoices.spec.ts tests/p1-customers.spec.ts│
│ Tests invoice creation, quote conversion, payments, and Customer 360.  │
├────────────────────────────────────────────────────────────────────────┤
│ Tier 4: Theme Switching & Visual Fidelity Tests                        │
│ npx playwright test tests/p3-toast-visual.spec.ts tests/responsive-*.ts│
│ Verifies Modern Gold vs Retro Win98 fidelity and viewport containment. │
└────────────────────────────────────────────────────────────────────────┘
```

---

## Section 11 — Acceptance Criteria

| Subsystem | Objective Verifiable Acceptance Criterion |
| :--- | :--- |
| **Component Consistency** | 100% of primary, secondary, and ghost buttons conform to defined height tokens (28px, 36px, 44px) and radius tokens. Zero hardcoded inline hex colors remain in `index.html`. |
| **Theme Replacement** | Modern Light and Modern Dark themes are completely transformed into the cohesive RupeeCRM Gold Design System. Blue/indigo default palette is fully eliminated from modern views. |
| **Retro Preservation** | Classic Retro Light (`classic`) and Classic Noir Dark (`classic-dark`) render with 100% fidelity: 0px border radii, beveled 3D borders, and authentic retro fonts are completely intact. |
| **Rupee Coin Animation** | Vector SVG coin renders cleanly across all DPI scales. Coin spin is active only during loading; transactional glint triggers once upon invoice/payment success. Motion is fully disabled under `prefers-reduced-motion`. |
| **Functional Equivalence** | 100% of existing workflows (Invoice creation, quote conversion, returns, PO inward stock, customer statements, payroll, backups) execute with zero behavioral changes. |
| **Multi-Tenant Scoping** | Switching firms cleanly refreshes workspace state and strictly injects `X-Firm-Id` on all API calls without tenant data leakage. |
| **Accessibility (WCAG)** | All text elements achieve WCAG AA contrast ratio ($\ge 4.5:1$ for normal text, $\ge 3:1$ for large text). No actionable data is obscured by `overflow: hidden`. |
| **Test Suite Pass Rate** | All 38 Playwright E2E test suites in `ui-regression/tests/` and all backend Maven integration tests pass with 0 failures and 0 console errors. |

---

## Section 12 — Recommended Implementation Order

To deliver maximum value while minimizing regression risk, implementation must follow a staged, phased rollout:

```
Phase 1: Foundation (Zero Risk)
  Task UI-001: Test Suite Baseline & Selector Map
  Task UI-002: Centralize CSS Design Tokens (style.css)
  Task UI-003: Retro Theme Isolation Verification
       │
       ▼
Phase 2: Primitives & Branded Assets (Low Risk)
  Task UI-004: Canonical Button Primitives
  Task UI-005: Form Control & Search Input Tokens
  Task UI-006: Vector Rupee Gold Coin SVG Asset
  Task UI-007: Coin Animation Engine & Event API
       │
       ▼
Phase 3: Shell & Command Center (Medium Risk)
  Task UI-008: Sidebar & Topbar Shell Overhaul
  Task UI-009: Dashboard Velo Layout Restructure
       │
       ▼
Phase 4: Core Workflows & Hubs (Controlled Risk)
  Task UI-010: Invoices & Quotes Hub Restyling
  Task UI-011: Multi-Tab Form Preservation (InvoiceForm)
  Task UI-012: Customer 360 Drawer Modernization
  Task UI-013: Payment & Settlement Modal Upgrades
  Task UI-014: Inventory & Stock Forecasting Drawer
  Task UI-015: Planner Kanban Board Restyling
       │
       ▼
Phase 5: Administration & Polish (Low Risk)
  Task UI-016: Settings & Theme Switcher Alignment
  Task UI-017: Omnisearch Modal & Guide Chips
  Task UI-018: Toast Deck & Sync Capsule Polish
  Task UI-019: Accessibility & Contrast Audit
  Task UI-020: Responsive Viewport Optimization
```

*Rationale for Sequencing:* Phase 1 establishes the tokens without changing markup. Phase 2 introduces the reusable primitives and the coin asset. Phase 3 modernizes the primary entry shell. Phase 4 updates core business hubs while verifying multi-tab form preservation at every step. Phase 5 concludes with polish, accessibility validation, and responsive verification.

---

## Section 13 — Baseline Metrics and Measurement Plan

| Performance Metric | Measurement Method | Target Objective |
| :--- | :--- | :--- |
| **Initial DOM Mount & Render** | Chrome DevTools Performance Timeline (`DOMContentLoaded` to `#root` paint) | $\le 180\text{ ms}$ on local desktop runtime. |
| **Navigation Tab Switch Latency** | `performance.now()` delta during `onNavigate` page switches | $\le 45\text{ ms}$ for warm views; zero layout thrashing. |
| **CSS Animation CPU/GPU Cost** | DevTools Performance Monitor (Rendering FPS & Layer GPU VRAM) | Sustained 60 FPS during coin spin/glint; 0% CPU overhead when idle. |
| **Stylesheet Size & Overhead** | File size audit of `style.css` | Reduce or maintain $\le 220\text{ KB}$ through class deduplication. |
| **Accessibility Score** | Automated Lighthouse & Axe-core audit | 100% WCAG AA compliance on all modern views. |
| **Playwright Suite Execution** | Sequential execution of 38 Playwright tests (`ui-regression`) | Complete suite execution within $\le 3.5\text{ minutes}$ with 0 failures. |

*(Note: Exact before-and-after benchmark numbers will be recorded at the onset of Phase 1 implementation).*
