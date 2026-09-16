# RupeeCRM Omnisearch — Full System & Functionality Guide

## 1. Overview & Architecture

**Omnisearch** is RupeeCRM's high-speed, 100% offline, zero-latency universal command palette, natural language search engine, and commercial calculator. It operates entirely on the client side using a deterministic NLP parser and mathematical evaluation engine (`BillsoftSearchEngine` in `billsoft/src/main/webapp/js/utils.js`).

### Core Architecture Highlights
- **0ms Latency**: 100% offline client-side evaluation without external API calls or LLM latency.
- **Dominant-Intent Classification**: Gated domain evaluation preventing cross-domain pollution (e.g. searching `9000 upi` does not trigger GST math or word conversion).
- **Interactive In-Place Inspector (`OmniActionInspector`)**: Inspect, customize, and edit extracted details (amounts, categories, dates, VPAs) with real-time live previews before saving or executing.
- **Permutation Invariance**: Flexible phrase order handling (e.g., `30 mm to mtr`, `30mm into meter`, and `mtr in 30 mm` all evaluate identically).
- **Multilingual Phonetic Processing**: Native support for English, Hinglish, Hindi, and Marathi trade terms and numbers.

---

## 2. Omnisearch UI & Keyboard Navigation

| Key / Action | Functionality |
| :--- | :--- |
| **`⌘K` / `Ctrl+K` or `/`** | Opens Omnisearch from anywhere in the app. |
| **`↑` / `↓`** | Navigates through results or cycles through command history. |
| **`↵ Enter`** | Executes the highlighted action, opens record, or inspects data. |
| **`Tab` / `Shift+Tab`** | Cycles filter tabs: `All` → `Actions` → `Customers` → `Invoices` → `Products` → `Vendors` → `HR` → `Tools`. |
| **`?`** | Toggles the 90+ command Capabilities & Cheat Sheet guide. |
| **`Esc`** | Closes `OmniActionInspector` back to results, or closes Omnisearch. |
| **Clipboard Auto-Detection** | Automatically detects numbers, VPAs, or phone numbers in your clipboard on modal open. |

---

## 3. Dominant-Intent Classification & Anti-Pollution Engine

Omnisearch uses **mutually exclusive intent gating** (`BillsoftSearchEngine.classifyDominantIntent`) to prioritize relevant results and suppress noise:

```
User Query ──► [ NLP Tokenizer & Normalizer ]
                     │
                     ▼
         [ Dominant-Intent Classifier ]
                     │
    ┌────────────────┼────────────────┬───────────────┐
    ▼                ▼                ▼               ▼
['UPI']        ['CONVERSION']     ['WORDS']       ['MATH']
 - Live QR Code - Unit Converter   - Numbers to    - Arithmetic
 - VPA Actions  - Currency Rates     Cheque Words  - Tax / EMI / Change
```

### Intent Domains
1. **`UPI`**: Live on-screen QR codes, VPA copying, payment link sharing.
2. **`CONVERSION`**: Units of length, weight, area, volume, Indian trade units, currencies.
3. **`WORDS`**: Digit-to-words for cheques, bank slips, and vernacular speech.
4. **`MATH` / `CHANGE` / `SPLIT` / `GST` / `DISCOUNT` / `MARGIN` / `FINANCE`**: Financial and commercial calculators.
5. **`STANDALONE_NUMBER`**: Displays multi-tool calculation palette (GST, Split, Words, UPI).
6. **`GENERAL`**: Searches records (Invoices, Customers, Products, Staff, Vendors) and triggers Smart Tasks.

---

## 4. Comprehensive Capabilities & Query Reference

### Category 1: Smart In-Place Tasks (0 Page Navigation)
Executes instant business tasks directly inside Omnisearch. Clicking **`✏️ Edit Details`** opens the in-place `OmniActionInspector`.

| Command / Query Syntax | Functionality | Supported Modifiers & Examples |
| :--- | :--- | :--- |
| `kharcha <amt> <title>` | Logs expense instantly to Ledger | `kharcha 120 chai nashta`, `expense 500 petrol`, `kharcha 1500 office stationary` |
| `todo <task> [time/date]` | Pins sticky note or reminder to Planner | `todo call Sharma ji 4pm`, `remind tomorrow 10am GST filing`, `task send quotation` |
| `customer <name> <phone> [city]` | Registers a new customer | `customer Manoj Patil 9822113344 Kolhapur`, `grahak Rahul 9890011223 Pune` |
| `remind <customer>` | Personalized WhatsApp dues reminder | `remind rahul`, `udhari sharma ji`, `send dues reminder rohit` |
| `attendance <staff> [status]` | Marks employee attendance | `attendance Ganesh present`, `hazri rahul half day`, `attendance sunil leave` |
| `advance <amt> to <staff>` | Records salary advance | `advance 5000 to ganesh`, `advance 2000 rahul cash` |

---

### Category 2: Live Business Intelligence (Instant Analytics)
Real-time financial metrics computed across current firm data without opening reports:

| Query | What It Displays |
| :--- | :--- |
| `today sales` / `aaj ka dhanda` | Today's billed revenue, cash collected, pending amount, and total invoice count. |
| `total udhari` / `kiska kitna udhar` | Total accounts receivable dues across all customers with highest debtor summary. |
| `low stock` / `kam stock` | Inventory items below reorder thresholds with alert badges. |
| `gst report` / `tax report` | Current period taxable sales, total CGST, SGST, and IGST breakdown. |
| `bank details` / `bank khata` | Active business bank name, account number, IFSC code, and GSTIN. |

---

### Category 3: Commercial & Tax Math
Specialized calculators designed for Indian retail, wholesale, and trade businesses:

| Tool | Syntax Examples | Output & Capabilities |
| :--- | :--- | :--- |
| **GST Forward Calculation** | `gst 18% on 5000` | Computes Base (₹5,000) + CGST (₹450) + SGST (₹450) = Net Total (₹5,900). |
| **Reverse GST Extraction** | `gst 18% inclusive 5900` | Extracts original Base (₹5,000) and Tax amount (₹900) from MRP. |
| **Cashier Change Calculator** | `change for 2000 bill 1435` | Calculates exact change (₹565) and note denomination breakdown (`[500, 50, 10, 5]`). |
| **Bill Splitter** | `split 4500 by 4`<br>`split 10000 in 2:3:5` | Even division per person or proportional multi-ratio division. |
| **Discount & Savings** | `10% discount on 5000` | Net payable price (₹4,500) and total savings (₹500). |
| **Profit Margin & Markup** | `margin cost 800 price 1200` | Profit (₹400), Profit Margin (33.33%), and Markup (50.00%). |
| **Growth & % Difference** | `growth 4000 to 6000` | Absolute change (+₹2,000) and percentage increase (+50.00%). |
| **Statistical Averages** | `avg 120 150 180 210` | Average (165), Minimum (120), Maximum (210), and Sum (660). |

---

### Category 4: Loans, Interest & Analytics

| Tool | Syntax Examples | Output |
| :--- | :--- | :--- |
| **EMI Calculator** | `emi 500000 at 9.5% for 5 years` | Monthly EMI (₹10,500.89), Total Interest (₹1,30,053), and Total Payout (₹6,30,053). |
| **Simple Interest (SI)** | `si 50000 at 8% for 3 years` | Interest (₹12,000) and Total Maturity Amount (₹62,000). |
| **Compound Interest (CI)** | `ci 100000 at 7.5% 2yr` | Compound Interest (₹15,562.50) and Maturity Balance (₹1,15,562.50). |

---

### Category 5: Units, Currency & Date Math

| Tool | Syntax Examples | Output |
| :--- | :--- | :--- |
| **Indian Cheque Words** | `words 125000`<br>`12500 shabdat`<br>`25000 cheque` | `One Lakh Twenty Five Thousand Rupees Only`. |
| **Words to Number** | `one lakh twenty five thousand`<br>`dedh hazar`<br>`adhai lakh` | Converts vernacular word phrases to digits (`₹1,25,000`, `₹1,500`, `₹2,50,000`). |
| **Trade & Area Units** | `5 kg in grams`<br>`2 quintal in kg`<br>`100 guntha in bigha`<br>`30 mm to mtr`<br>`5 brass in sqft` | Converts weight, length, volume, and Indian trade units (`quintal`, `tonne`, `gaj`, `guntha`, `bigha`, `brass`, `tola`, `carat`). |
| **Live Currency Rates** | `100 usd in inr`<br>`50 eur to inr`<br>`1000 inr to usd` | Multi-currency conversions with live exchange rate evaluation. |
| **Date Calculations** | `30 days from today`<br>`next monday`<br>`days until 31 Dec` | Date offsets, target day of week, and remaining day countdowns. |

---

### Category 6: Live UPI QR Gateway (`OmniActionInspector`)
Generates instant on-screen dynamic QR codes compatible with GPay, PhonePe, Paytm, and BHIM.

- **Glued Token Decoupling**: `9000 upi`, `upi 500`, `900upi`, `pay 1500 on qr`.
- **Live Dynamic Re-render**: Editing the amount or recipient VPA in `OmniActionInspector` recalculates the UPI URI (`upi://pay?pa=...&am=...`) and re-draws the QR code in real time.
- **One-Click Actions**:
  - `📋 Copy Payment Link`: Copies UPI intent link to clipboard.
  - `📋 Copy VPA`: Copies merchant UPI ID.
  - `📱 Open in UPI App`: Opens payment app directly.

---

### Category 7: Fast Routing & Slash Commands

| Slash Command | Full Phrase Equivalent | Target Screen / Modal |
| :--- | :--- | :--- |
| `/inv` | `bill banao`, `pavti banva`, `new bill` | Quick Create Tax Invoice Modal |
| `/quo` | `kaccha bill`, `andaj patrak`, `estimate` | Quick Create Quotation / Estimate Modal |
| `/khata` | `hisab`, `udhari ledger`, `statements` | Customer Ledger Statement Generator |
| `/pay` | `tankha`, `kamgar payment`, `payslip` | Staff Payroll & Payslip Desk |
| `/po` | `kharedi order`, `supplier order` | Purchase Order Creator |
| `/qr` | `upi qr`, `payment qr`, `scan pay` | Full Screen UPI Payment Terminal |
| `/lock` | `screen lock`, `safe mode` | Instant Application Privacy Lock |
| `/gst` | `tax settings`, `gst rate setup` | Tax & GST Configuration Settings |
| `/theme` | `dark mode`, `light mode` | Theme Customizer |

---

### Category 8: Record Search & Contextual Actions

When searching for existing records, Omnisearch provides 1-click in-place actions:

| Record Type | Search By | In-Place Actions Available |
| :--- | :--- | :--- |
| **Invoices** | Number, Customer name, Date, Amount | `💳 Settle / Pay` (In-place cash/UPI recording)<br>`📄 PDF` (Download A4 invoice)<br>`✏️ Edit` (Open invoice editor) |
| **Quotations** | Estimate number, Customer, Amount | `➡️ Convert to Bill` (Converts estimate to Tax Invoice)<br>`📄 PDF` (Download Estimate) |
| **Customers** | Name, Phone number, City, GSTIN | `📄 + Invoice` (Creates invoice for customer)<br>`📊 Statement` (Generates ledger statement)<br>`💬 WhatsApp` (Opens WhatsApp dues chat) |
| **Vendors** | Supplier name, Contact, Phone | `📋 + PO` (Create Purchase Order)<br>`📊 Statement` (View Vendor balance statement) |
| **Staff / HR** | Name, Role, Department | `💰 Send Payslip` (WhatsApp payslip dispatch)<br>`👤 Profile` (View employee profile) |
| **Products** | Name, SKU, Barcode, Category | `📄 + Invoice` (Adds product to active invoice) |

---

## 5. In-Place Interactive Data Inspector (`OmniActionInspector`)

The **`OmniActionInspector`** allows reviewing, correcting, and executing any AI or smart task without leaving the Omnisearch modal:

```
┌────────────────────────────────────────────────────────────────────────┐
│ ✏️ Record New Expense                             [✕ Cancel / Back]    │
│ Review and customize fields below in real-time before applying.        │
├────────────────────────────────────────────────────────────────────────┤
│ Amount (₹) *           Expense Title / Reason *                        │
│ [ 120              ]   [ Chai nashta                      ]            │
│                                                                        │
│ Category               Payment Mode             Expense Date           │
│ [ ☕ Tea & Snacks  ▼ ] [ 💵 Cash             ▼ ] [ 2026-09-12       ]  │
├────────────────────────────────────────────────────────────────────────┤
│                                 [ Cancel ]  [ ✓ Confirm & Save       ] │
└────────────────────────────────────────────────────────────────────────┘
```

- **Zero Accidental Dismissals**: Suggestion chips update the active query without closing the modal back to the dashboard.
- **Two-Way Binding**: Modifying any field (amount, date, description, recipient VPA) updates the payload in real-time.
- **Instant Persistence**: Saves directly to API/database and triggers seamless dashboard refresh.

---

## 6. Deterministic Pipeline & Reliability Hardening

The upgraded Omnisearch engine features a **Deterministic Multi-Stage Processing Pipeline**:

```
[ Raw User Query ]
       │
       ▼
1. Decouple & Normalize
   ├── splitGluedTokens ("30mm" -> "30 mm", "700upi" -> "700 upi", "18%gst" -> "18% gst")
   └── cleanNaturalQuery (removes conversational filler in Hindi, Marathi, Hinglish, English)
       │
       ▼
2. Slot & Entity Extraction
   ├── extractAmounts (scaled numbers: k, lakh, cr, hazar, standalone currency)
   ├── extractRate (explicit %, prefix & suffix rates bounded <= 100, standard slabs)
   ├── extractTenure, extractPhone, extractDateTime (12h/24h & natural relative offsets)
   └── extractGstParams, extractChangeParams, extractSplitParams, extractMarginParams
       │
       ▼
3. Dominant-Intent Classification & Confidence Scoring
   ├── Priority 1: UPI / QR Payment Gateways
   ├── Priority 2: Universal Unit & Currency Conversions
   ├── Priority 3: Cheque & Indian Number to Words
   ├── Priority 4: Arithmetic & Percentage Math Evaluator
   ├── Priority 5: Commercial Tax / GST / Reverse GST / Slabs
   ├── Priority 6: Cashier Change & Bill Splitter
   ├── Priority 7: Margin, Markup, Discount, Loans & Interest
   └── Priority 8: Mutating Smart Tasks & Record Search
       │
       ▼
4. Safety & Idempotency Layer
   ├── Read-Only Computations: Immediate 0ms local calculation & clipboard action
   └── Mutating Actions: Routed into OmniActionInspector with double-submit lock
       │
       ▼
5. Error-Preserving Recovery
   └── API errors display inline alert with 1-click retry without wiping user form inputs
```

---

## 7. Automated Test Suite & Coverage

The complete engine is verified with a **107-scenario automated test matrix** covering all query permutations, dialects, calculators, and edge cases:
- **Test File**: `scratch/test_omnisearch_deterministic_pipeline.js`
- **Execution Command**: `node scratch/test_omnisearch_deterministic_pipeline.js`
- **Pass Rate**: **100% (107 / 107 Passing)**
- **Coverage Areas**:
  1. Forward & Reverse GST Permutations (15 tests)
  2. Cash Register Change Permutations (7 tests)
  3. Bill Splitter Permutations (7 tests)
  4. Discount & Markup Calculators (6 tests)
  5. Profit Margin & CP/SP Calculators (4 tests)
  6. Loans, EMI & Interest Calculators (3 tests)
  7. Indian Words & Numbers Converters (8 tests)
  8. Universal Unit Conversions & Indian Trade Units (13 tests)
  9. Smart Mutating Tasks & Inspectors (9 tests)
  10. Arithmetic & Percentage Math (9 tests)
  11. End-to-End Search Pipeline Routing (18 tests)
  12. Glued Tokens & Negative Edge Cases (8 tests)
