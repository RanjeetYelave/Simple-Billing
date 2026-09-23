# Omnisearch & Natural Language Processing (NLP) Engine
## Complete Architectural & User Guide

---

## 1. Introduction & Core Philosophy
The Simple-Billing / RupeeCRM **Omnisearch & NLP Engine** is a high-performance, **100% offline, deterministic rule-based natural language processing and omnichannel search system**. It empowers users to navigate the application, inspect business intelligence, perform complex multi-unit conversions and financial computations, manage session-continuous calculations, and execute multi-intent workflows in English, Hindi, Marathi, Hinglish, and Marathish with zero external cloud or LLM dependencies.

---

## 2. Global Shortcuts & Invocation
- **Global Search Focus**: Press `Ctrl + K` or `Cmd + K` anywhere in the application.
- **Floating Calculator & Math Engine**: Press `Ctrl + Space` or click the Calculator action icon.
- **Immediate Evaluation**: Calculations and search results update dynamically as you type (live keystroke debounce) without requiring the Enter key for simple arithmetic and unit conversions.

---

## 3. Mathematical & Financial Computation Capabilities

### 3.1 Standard Arithmetic & AST Expressions
- **Supported Operators**: `+` (addition), `-` (subtraction), `*` (multiplication), `/` (division), `^` (exponentiation), `%` (modulo / percentage), `sqrt` (square root), `( )` (nested parentheses).
- **Operator Precedence**: Handled via deterministic Shunting-Yard AST algorithm preserving mathematical precedence: `^` > `*, /` > `+, -`.
- **Examples**:
  - `(1500 + 3500) * 1.18` ➔ `5900`
  - `sqrt(144) + 2^4` ➔ `28`
  - `17% of 5633` ➔ `957.61`
  - `5633 + 17%` ➔ `6590.61`
  - `1000 - 15%` ➔ `850`
  - `-50 + 100` ➔ `50`
  - `0.0001 + 0.0002` ➔ `0.0003`

### 3.2 Indian Number Words & Colloquial Quantifiers
- **Base Units**:
  - `hazar / hazaar / haz / k` ➔ Multiplier: $1,000$
  - `lakh / lakhs / lac / lacs` ➔ Multiplier: $100,000$
  - `crore / crores / cr` ➔ Multiplier: $10,000,000$
- **Fractional & Compound Prefixes**:
  - `dedh` ➔ $1.5\times$ base (`dedh hazar` = 1,500; `dedh lakh` = 150,000; `dedh crore` = 15,000,000)
  - `dhai / adhai` ➔ $2.5\times$ base (`dhai hazar` = 2,500; `dhai lakh` = 250,000; `dhai crore` = 25,000,000)
  - `sawa` ➔ $1.25\times$ base (`sawa hazar` = 1,250; `sawa lakh` = 125,000; `sawa crore` = 12,500,000)
  - `paune` ➔ $0.75\times$ base (`paune hazar` = 750; `paune lakh` = 75,000; `paune crore` = 7,500,000)
  - `sawa do` ➔ $2.25\times$ base (`sawa do hazar` = 2,250; `sawa do lakh` = 225,000)
  - `paune do` ➔ $1.75\times$ base (`paune do hazar` = 1,750; `paune do lakh` = 175,000)
  - `sawa teen` ➔ $3.25\times$ base (`sawa teen hazar` = 3,250; `sawa teen lakh` = 325,000)
  - `paune teen` ➔ $2.75\times$ base (`paune teen hazar` = 2,750; `paune teen lakh` = 275,000)
  - `aadha / adha` ➔ $0.5\times$ base (`aadha lakh` = 50,000; `aadha crore` = 5,000,000)
- **Examples**:
  - `dedh lakh + 50000` ➔ `200000`
  - `dhai hazar * 4` ➔ `10000`
  - `sawa do lakh - 25000` ➔ `200000`

### 3.3 Commercial & Tax Calculators
- **Forward GST (Exclusive)**:
  - *Syntax*: `<amount> + <rate>% gst` or `<rate>% gst on <amount>`
  - *Calculation*: $\text{Base} = \text{amount}$, $\text{Tax} = \text{amount} \times \frac{\text{rate}}{100}$, $\text{Total} = \text{Base} + \text{Tax}$, $\text{CGST} = \frac{\text{Tax}}{2}$, $\text{SGST} = \frac{\text{Tax}}{2}$
  - *Example*: `1500 + 18% gst` ➔ Base: ₹1,500, CGST: ₹135, SGST: ₹135, Total: ₹1,770
- **Reverse GST (Inclusive)**:
  - *Syntax*: `<amount> with <rate>% gst` or `<amount> inclusive <rate>% gst` or `<amount> reverse gst <rate>%`
  - *Calculation*: $\text{Base} = \frac{\text{amount}}{1 + (\text{rate}/100)}$, $\text{Tax} = \text{amount} - \text{Base}$
  - *Example*: `1500 with 18% gst` ➔ Base: ₹1,271.19, Tax: ₹228.81, Total: ₹1,500
- **Discounts (Percentage & Flat)**:
  - *Syntax*: `<amount> with <rate>% discount` or `discount 500 on 4500`
  - *Example*: `5000 with 15% discount` ➔ Discount: ₹750, Final Payable: ₹4,250
- **Markup & Gross Margin**:
  - *Syntax*: `cost 500 selling 750 margin` or `cost 500 markup 50%`
  - *Example*: `cost 500 selling 750 margin` ➔ Profit: ₹250, Margin: 33.33%, Markup: 50%
- **Cashier Change Helper**:
  - *Syntax*: `paid 2000 for 1450 bill` or `change for 2000 bill 1450`
  - *Example*: `paid 2000 for 1450 bill` ➔ Change Return: ₹550
- **Bill Splitting**:
  - *Syntax*: `split 4500 into 4` or `divide 4500 among 4 people`
  - *Example*: `split 4500 into 4` ➔ Per Person: ₹1,125

### 3.4 Banking, Investment & Interest Calculators
- **Simple Interest (SI)**:
  - *Formula*: $I = \frac{P \times R \times T}{100}$, $\text{Total} = P + I$
  - *Syntax*: `SI on <principal> at <rate>% for <time> years`
  - *Example*: `SI on 50000 at 8.5% for 3 years` ➔ Interest: ₹12,750, Maturity: ₹62,750
- **Compound Interest (CI)**:
  - *Formula*: $A = P \times \left(1 + \frac{R}{100}\right)^T$, $I = A - P$
  - *Syntax*: `CI on <principal> at <rate>% for <time> years`
  - *Example*: `CI on 1 lakh at 7% for 5 years` ➔ Interest: ₹40,255, Total: ₹140,255
- **Systematic Investment Plan (SIP)**:
  - *Formula*: $M = P \times \frac{(1+i)^n - 1}{i} \times (1+i)$ where $i = \frac{R}{1200}$, $n = T \times 12$
  - *Syntax*: `SIP <monthly_amount> at <rate>% for <years> years`
  - *Example*: `SIP 5000 at 12% for 10 years` ➔ Invested: ₹6,00,000, Wealth Gain: ₹5,61,695, Maturity: ₹11,61,695
- **Tax Deducted at Source (TDS)**:
  - *Syntax*: `TDS <rate>% on <amount>`
  - *Example*: `TDS 10% on 75000` ➔ TDS: ₹7,500, Net Payable: ₹67,500
- **Loan EMI Calculator**:
  - *Syntax*: `emi on 500000 at 9.5% for 5 years`
  - *Example*: `emi on 5 lakh at 10% for 5 years` ➔ Monthly EMI: ₹10,624

---

## 4. Date Arithmetic & Fiscal Period Anchors

### 4.1 Relative Offsets & Natural Language Offsets
- **Syntax**: `today + <N> days/weeks/months/years` or `<N> days ago` or `<N> days from now`
- **Examples**:
  - `today + 45 days` ➔ Target date, weekday name, and ISO string
  - `2 weeks from now` ➔ Target date (+14 days)
  - `3 months after today` ➔ Target date (+3 calendar months)
  - `10 days ago` ➔ Past target date (-10 days)

### 4.2 Date Interval Calculations
- **Syntax**: `days between <Date1> and <Date2>` or `difference between <Date1> and <Date2>`
- **Formats Supported**: `YYYY-MM-DD`, `DD/MM/YYYY`, `DD-MM-YYYY`
- **Example**: `days between 2026-03-01 and 2026-03-20` ➔ `19 Days Duration`

### 4.3 Calendar & Fiscal Anchors
- **Month End (`month end`)**: Resolves to the last day of the current calendar month.
- **Quarter End (`quarter end`)**: Resolves to the end of Q1 (Mar 31), Q2 (Jun 30), Q3 (Sep 30), or Q4 (Dec 31).
- **Fiscal Year End (`FY end` / `fiscal year end`)**: Resolves to Indian Fiscal Year End (March 31 of the fiscal period).

---

## 5. Comprehensive Unit Conversion Registry

The engine supports 8 dimensional conversion families with deterministic mathematical conversion factors:

### 5.1 Length (Base: Meter)
- **Millimeter (`mm`, `millimeter`, `millimeters`)**: $0.001\text{ m}$
- **Centimeter (`cm`, `centimeter`, `centimeters`)**: $0.01\text{ m}$
- **Meter (`m`, `mtr`, `meter`, `meters`)**: $1.0\text{ m}$
- **Kilometer (`km`, `kilometer`, `kilometers`)**: $1,000\text{ m}$
- **Inch (`in`, `inch`, `inches`)**: $0.0254\text{ m}$
- **Foot (`ft`, `foot`, `feet`)**: $0.3048\text{ m}$
- **Yard (`yd`, `yard`, `yards`)**: $0.9144\text{ m}$
- **Mile (`mi`, `mile`, `miles`)**: $1,609.344\text{ m}$
- **Nautical Mile (`nm`, `nmi`)**: $1,852\text{ m}$
- **Gaj (`gaj`)**: $0.9144\text{ m}$ (Traditional Indian Yard)

### 5.2 Area (Base: Square Feet)
- **Square Feet (`sqft`)**: $1.0\text{ sqft}$
- **Square Meter (`sqm`)**: $10.7639\text{ sqft}$
- **Square Yard (`sqyd`)**: $9.0\text{ sqft}$
- **Square Kilometer (`sqkm`)**: $10,763,910.4\text{ sqft}$
- **Square Mile (`sqmi`)**: $27,878,400\text{ sqft}$
- **Guntha (`guntha`)**: $1,089\text{ sqft}$ (Traditional Indian Land Unit)
- **Bigha (`bigha`)**: $27,225\text{ sqft}$ (Traditional Indian Land Unit, 25 Gunthas)
- **Acre (`acre`, `acres`)**: $43,560\text{ sqft}$
- **Hectare (`hectare`, `hectares`)**: $107,639\text{ sqft}$
- **Brass (`brass`)**: $100\text{ sqft}$ (Commercial Indian Construction Unit)

### 5.3 Weight & Mass (Base: Gram)
- **Milligram (`mg`, `milligram`, `milligrams`)**: $0.001\text{ g}$
- **Gram (`g`, `gm`, `gms`, `gram`, `grams`)**: $1.0\text{ g}$
- **Kilogram (`kg`, `kgs`, `kilo`, `kilos`)**: $1,000\text{ g}$
- **Quintal (`quintal`, `qtl`)**: $100,000\text{ g}$ ($100\text{ kg}$)
- **Tonne (`tonne`, `ton`, `tons`, `t`)**: $1,000,000\text{ g}$ ($1,000\text{ kg}$)
- **Tola (`tola`)**: $11.6638\text{ g}$ (Indian Bullion/Gold Weight Unit)
- **Carat (`carat`, `carats`)**: $0.2\text{ g}$ ($200\text{ mg}$)
- **Pound (`lb`, `lbs`, `pound`, `pounds`)**: $453.592\text{ g}$
- **Ounce (`oz`, `ounce`, `ounces`)**: $28.3495\text{ g}$
- **Stone (`stone`)**: $6,350.29\text{ g}$ ($14\text{ lbs}$)

### 5.4 Volume & Liquid (Base: Liter)
- **Milliliter (`ml`, `milliliter`, `milliliters`)**: $0.001\text{ L}$
- **Liter (`l`, `ltr`, `liter`, `liters`)**: $1.0\text{ L}$
- **US Gallon (`gal`, `gallon`, `gallons`)**: $3.78541\text{ L}$
- **Fluid Ounce (`floz`)**: $0.0295735\text{ L}$
- **Cup (`cup`, `cups`)**: $0.236588\text{ L}$
- **Pint (`pint`, `pints`)**: $0.473176\text{ L}$
- **Quart (`quart`, `quarts`)**: $0.946353\text{ L}$

### 5.5 Temperature (Affine Transformation)
- **Celsius (`c`, `celsius`, `centigrade`)**: Base unit
- **Fahrenheit (`f`, `fahrenheit`)**: $F = (C \times 9/5) + 32$, $C = (F - 32) \times 5/9$
- **Kelvin (`k`, `kelvin`)**: $K = C + 273.15$, $C = K - 273.15$
- *Examples*:
  - `100 c to f` ➔ `212 °F`
  - `-40 c to f` ➔ `-40 °F`
  - `0 k to c` ➔ `-273.15 °C`

### 5.6 Speed & Velocity (Base: km/h)
- **Kilometers per Hour (`kmh`, `kph`, `km/h`)**: $1.0\text{ km/h}$
- **Miles per Hour (`mph`, `mi/h`)**: $1.609344\text{ km/h}$
- **Meters per Second (`mps`, `m/s`)**: $3.6\text{ km/h}$
- **Knot (`knot`, `knots`, `kt`)**: $1.852\text{ km/h}$
- **Feet per Second (`fps`, `ft/s`)**: $1.09728\text{ km/h}$

### 5.7 Data Storage & Memory (Base: Byte, Binary $1024$ Multiples)
- **Bytes (`b`, `byte`, `bytes`)**: $1\text{ Byte}$
- **Kilobyte (`kb`, `kib`, `kilobyte`, `kilobytes`)**: $1,024\text{ Bytes}$
- **Megabyte (`mb`, `mib`, `megabyte`, `megabytes`)**: $1,048,576\text{ Bytes}$
- **Gigabyte (`gb`, `gib`, `gigabyte`, `gigabytes`)**: $1,073,741,824\text{ Bytes}$
- **Terabyte (`tb`, `tib`, `terabyte`, `terabytes`)**: $1,099,511,627,776\text{ Bytes}$
- **Petabyte (`pb`, `petabyte`, `petabytes`)**: $1,125,899,906,842,624\text{ Bytes}$

### 5.8 Time Duration (Base: Second)
- **Millisecond (`ms`, `millisecond`, `milliseconds`)**: $0.001\text{ s}$
- **Second (`s`, `sec`, `second`, `seconds`)**: $1.0\text{ s}$
- **Minute (`min`, `minute`, `minutes`)**: $60\text{ s}$
- **Hour (`hr`, `hrs`, `hour`, `hours`)**: $3,600\text{ s}$
- **Day (`d`, `day`, `days`)**: $86,400\text{ s}$
- **Week (`wk`, `wks`, `week`, `weeks`)**: $604,800\text{ s}$
- **Month (`mo`, `month`, `months`)**: $2,592,000\text{ s}$ ($30\text{ days}$)
- **Year (`yr`, `yrs`, `year`, `years`)**: $31,536,000\text{ s}$ ($365\text{ days}$)

---

## 6. World Currency Registry (Reference Rates as of September 2026)

The engine provides static offline currency conversions across 40+ world currencies based on benchmark reference rates as of September 2026. Every currency response includes an explicit disclaimer communicating that rates are static reference rates.

| Currency Name | ISO Code | Symbol / Aliases | Reference Rate (1 Unit in INR) |
| :--- | :--- | :--- | :--- |
| **Indian Rupee** | `INR` | `₹`, `rs`, `rupee`, `rupees`, `rupya`, `rupaye` | `1.00` (Base) |
| **US Dollar** | `USD` | `$`, `dollar`, `dollars`, `bucks`, `us dollar` | `86.50` |
| **Euro** | `EUR` | `€`, `euro`, `euros` | `91.20` |
| **British Pound** | `GBP` | `£`, `pound`, `pounds`, `sterling`, `british pound` | `109.80` |
| **Japanese Yen** | `JPY` | `¥`, `yen`, `japanese yen` | `0.58` |
| **Chinese Yuan** | `CNY` | `¥`, `yuan`, `rmb`, `renminbi`, `chinese yuan` | `11.90` |
| **Swiss Franc** | `CHF` | `CHF`, `franc`, `francs`, `swiss franc` | `97.40` |
| **Canadian Dollar** | `CAD` | `C$`, `cad`, `canadian dollar` | `61.20` |
| **Australian Dollar** | `AUD` | `A$`, `aud`, `australian dollar` | `55.40` |
| **New Zealand Dollar** | `NZD` | `NZ$`, `nzd`, `new zealand dollar`, `kiwi dollar` | `50.80` |
| **Singapore Dollar** | `SGD` | `S$`, `sgd`, `singapore dollar` | `64.80` |
| **Hong Kong Dollar** | `HKD` | `HK$`, `hkd`, `hong kong dollar` | `11.10` |
| **UAE Dirham** | `AED` | `AED`, `dirham`, `dirhams`, `uae dirham`, `dhs` | `23.55` |
| **Saudi Riyal** | `SAR` | `SAR`, `riyal`, `riyals`, `saudi riyal` | `23.05` |
| **Qatari Riyal** | `QAR` | `QAR`, `qatari riyal`, `qatari riyals` | `23.75` |
| **Kuwaiti Dinar** | `KWD` | `KWD`, `kuwaiti dinar`, `kd` | `281.50` |
| **Bahraini Dinar** | `BHD` | `BHD`, `bahraini dinar`, `bd` | `229.40` |
| **Omani Rial** | `OMR` | `OMR`, `omani rial`, `ro` | `224.70` |
| **Malaysian Ringgit** | `MYR` | `RM`, `ringgit`, `malaysian ringgit` | `19.80` |
| **Thai Baht** | `THB` | `฿`, `baht`, `thai baht` | `2.52` |
| **Indonesian Rupiah** | `IDR` | `Rp`, `rupiah`, `indonesian rupiah` | `0.0053` |
| **Philippine Peso** | `PHP` | `₱`, `peso`, `pesos`, `philippine peso` | `1.48` |
| **South Korean Won** | `KRW` | `₩`, `won`, `korean won`, `south korean won` | `0.062` |
| **South African Rand** | `ZAR` | `R`, `rand`, `south african rand` | `4.85` |
| **Russian Ruble** | `RUB` | `₽`, `ruble`, `rubles`, `rouble`, `russian ruble` | `0.94` |
| **Turkish Lira** | `TRY` | `₺`, `lira`, `liras`, `turkish lira`, `tl` | `2.45` |
| **Brazilian Real** | `BRL` | `R$`, `real`, `reais`, `brazilian real` | `15.20` |
| **Mexican Peso** | `MXN` | `Mex$`, `mexican peso`, `mexican pesos` | `4.35` |
| **Swedish Krona** | `SEK` | `kr`, `krona`, `kronor`, `swedish krona` | `8.25` |
| **Norwegian Krone** | `NOK` | `kr`, `krone`, `kroner`, `norwegian krone` | `8.05` |
| **Danish Krone** | `DKK` | `kr`, `danish krone`, `danish kroner` | `12.20` |
| **Polish Zloty** | `PLN` | `zł`, `zloty`, `zlotys`, `polish zloty` | `21.40` |
| **Czech Koruna** | `CZK` | `Kč`, `koruna`, `korunas`, `czech koruna` | `3.65` |
| **Hungarian Forint** | `HUF` | `Ft`, `forint`, `hungarian forint` | `0.23` |
| **Israeli Shekel** | `ILS` | `₪`, `shekel`, `shekels`, `israeli shekel`, `nis` | `23.40` |
| **Egyptian Pound** | `EGP` | `E£`, `egyptian pound`, `egyptian pounds` | `1.78` |
| **Nigerian Naira** | `NGN` | `₦`, `naira`, `nigerian naira` | `0.054` |
| **Bangladeshi Taka** | `BDT` | `৳`, `taka`, `bangladeshi taka` | `0.72` |
| **Pakistani Rupee** | `PKR` | `PKR`, `pakistani rupee`, `pakistani rupees` | `0.31` |
| **Sri Lankan Rupee** | `LKR` | `LKR`, `sri lankan rupee`, `sri lankan rupees` | `0.29` |
| **Nepalese Rupee** | `NPR` | `NPR`, `nepalese rupee`, `nepalese rupees` | `0.625` |
| **Vietnamese Dong** | `VND` | `₫`, `dong`, `vietnamese dong` | `0.0034` |

---

## 7. Multilingual & Vernacular Translation Mappings

The engine normalizes multilingual inputs in **Marathi**, **Hindi**, **Hinglish**, **Marathish**, and **Devanagari script**:

### 7.1 Devanagari Digits
`०, १, २, ३, ४, ५, ६, ७, ८, ९` map directly to standard Arabic numbers `0, 1, 2, 3, 4, 5, 6, 7, 8, 9` before arithmetic parsing and entity identification.

### 7.2 Core Vernacular Vocabulary Mappings
- **Expenses & Outflows**: `खर्च`, `खर्चा`, `kharcha`, `kharch`, `petty cash`, `spent`, `paid` ➔ `ACT_CREATE_EXPENSE` / `QH_EXPENSE_TODAY`
- **Receivables & Dues**: `उधारी`, `udhari`, `shillak`, `baki`, `dues`, `balance` ➔ `QH_TOTAL_UDHARI` / `QH_CUSTOMER_DUE`
- **Sales & Revenue**: `विक्री`, `bikri`, `sale`, `sales`, `dhanda`, `vyapar` ➔ `QH_SALES_TODAY` / `QH_SALES_MONTH`
- **Attendance & HR**: `हजेरी`, `hazri`, `karmachari`, `shikshak`, `staff`, `attendance`, `tankha` ➔ `ACT_MARK_ATTENDANCE` / `QH_HR_SUMMARY`
- **Documents & Bills**: `पावती`, `pavti`, `kaccha bill`, `andaj patrak`, `bill`, `invoice`, `quotation` ➔ Invoices & Paperwork Hub

---

## 8. Multi-Intent Query Processing & Contextual Memory

### 8.1 Compound Query Segmentation (`QuerySegmenter`)
- Multiple actions or queries combined with conjunctions (`and`, `then`, `aur`, `ani`, `;`) are segmented deterministically:
  - *Example*: `Ramesh balance and 1500 + 500 then /invoices`
  - *Execution*:
    1. Segment 1: `Ramesh balance` (Retrieves customer balance card)
    2. Segment 2: `1500 + 500` (Evaluates arithmetic to `2000`)
    3. Segment 3: `/invoices` (Navigates to the Invoices management screen)

### 8.2 Session Continuity & Chained Calculations (`SessionContextManager`)
- **`ans` Chaining**: The output of any mathematical or financial operation is automatically stored as the current session's `ans` variable.
  - *Query 1*: `1500 + 3500` ➔ `5000` (stored as `ans`)
  - *Query 2*: `ans + 250` ➔ `5250`
  - *Query 3*: `18% gst on ans` ➔ Tax: `₹945`, Total: `₹6,195`
- **Entity Anaphora & Pronoun Resolution**:
  - *Query 1*: `Tata Motors balance` ➔ Resolves customer "Tata Motors"
  - *Query 2*: `his phone number` / `unka udhari` ➔ Automatically resolves "Tata Motors" from session memory.
- **Strict Multi-Tenant Isolation**:
  - Contexts, last entities, and `ans` variables are keyed by `firmId` in-memory. Data never bleeds across different tenants or active firm switches.

---

## 9. Business Intelligence, Rankings & Aggregation Queries

### 9.1 Top & Bottom N Rankings
- `top 5 customers by revenue` ➔ Ranked customer revenue breakdown
- `top 3 products by volume` ➔ Highest selling products by unit sales
- `bottom 5 selling items` ➔ Lowest velocity catalog items
- `top 5 debtors / udhari wale` ➔ Highest outstanding customer receivables

### 9.2 Threshold & Range Filter Queries
- `invoices over 50000` / `invoices above 1 lakh` ➔ Invoices matching grand total $\ge 50,000$
- `invoices under 5000` ➔ Invoices matching grand total $\le 5,000$
- `customers owing more than 10000` ➔ Customers with balance $\ge 10,000$
- `expenses above 2000` ➔ Expense line items $\ge 2,000$

### 9.3 Period Comparisons (MoM & DoD)
- `sales this month vs last month` ➔ Month-over-Month sales total, delta, and growth percentage
- `sales today vs yesterday` ➔ Day-over-Day revenue comparison

### 9.4 Accounts Receivable Aging
- `aging summary` / `aging report` ➔ Bucketed analysis of receivables:
  - `0–30 Days`
  - `31–60 Days`
  - `61–90 Days`
  - `90+ Days`

### 9.5 Inventory Valuation & Low Stock Alerts
- `low stock products` ➔ Items at or below minimum reorder alert levels
- `inventory valuation` ➔ Total valuation calculated as $\sum (\text{Stock} \times \text{Price})$

---

## 10. Direct Slash Commands & Navigation Shortcuts

| Slash Shortcut | Canonical Action | Target Screen |
| :--- | :--- | :--- |
| `/inv` | Create Invoice | Invoices ➔ Invoices Tab |
| `/quo` | Create Quotation | Invoices ➔ Quotations Tab |
| `/khata` | Customer Statements / Ledger | Firm ➔ Paperwork ➔ Statements |
| `/pay` | Payroll Hub | HR ➔ Payroll Management |
| `/po` | Create Purchase Order | Firm ➔ Paperwork ➔ Orders |
| `/party` | Vendors & Suppliers Directory | Customers ➔ Suppliers Tab |
| `/stock` | Warehouse Stock & Inventory | Inventory ➔ Master Catalog |
| `/goal` | Goal Setting | Planner ➔ Goals Tab |
| `/save` | Savings Tracker | Planner ➔ Savings Tab |
| `/todo` | Kanban Board | Planner ➔ Board Tab |
| `/personal` | Personal Wealth Cockpit | Dashboard ➔ Personal Segment |
| `/biz` | Business Dashboard | Dashboard ➔ Business Segment |
| `/backup` | Backup & Restore | Settings ➔ Backup Tab |
| `/theme` | Toggle Theme | Direct Dark / Light Toggle |
| `/lock` | Safe Screen Lock | Direct Application Lock |

---

## 11. In-App Interactive Capabilities Guide & Single Source of Truth

The in-app Omnisearch Guide (accessible via the `?` button next to the Omnisearch bar or keyboard shortcut) is driven by an authoritative, unified capability registry:
- **Registry File**: `billsoft/src/main/webapp/js/omnisearchCapabilities.js` (`OmnisearchCapabilitiesRegistry`)
- **Single Source of Truth**: Houses 22+ comprehensive capability definitions, 8 unit families (32+ units), 40+ fiat currencies, financial formulas, vernacular synonyms, parameter badges, and 120+ live interactive query examples.
- **Search-First Progressive Disclosure**:
  - **Category Filters**: 6 broad discovery areas (`search_nav`, `money_calc`, `dates_conv`, `biz_reports`, `cust_prod_staff`, `smart_lang`).
  - **Deep Instant Search**: Multi-token full-text search indexing capability IDs, names, descriptions, parameters, supported units, currencies, and synonyms.
  - **Actionable Buttons**: Every example card features instant `Try it →` (direct execution) and `Fill ✎` (prefill for editing).
  - **Randomized Suggestion Pool**: Dynamic Fisher-Yates shuffled prompt chips (`getRandomizedQueries`) representing all supported capabilities to guide user exploration without overwhelming them.
- **Cross-Platform Compatibility**: Loaded via universal module definition (UMD) for synchronous client-side execution and automated test harnesses (Playwright / Node.js).

---

## 12. Security, Resilience & Quality Assurance
- **Zero Unsafe `eval()`**: All mathematical expressions are parsed into an Abstract Syntax Tree (AST) using a token-based Shunting-Yard parser.
- **Zero Cloud AI / External API Callout**: 100% locally evaluated in under 2ms without network connectivity.
- **Input Sanitization**: Injection payloads (XSS, SQL, Prototype Pollution) are safely treated as plaintext search queries without execution.
- **Deterministic Regression Testing**: Automated Playwright UI test suites and 598+ Maven backend tests guarantee strict stability across releases.