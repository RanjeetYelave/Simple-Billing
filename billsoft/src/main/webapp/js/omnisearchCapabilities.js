/**
 * RupeeCRM Authoritative Omnisearch Capabilities Registry
 * 
 * Single deterministic source of truth for:
 * 1. In-app Interactive Omnisearch Command & Capability Guide
 * 2. Autocomplete, Suggestion Rotation & Shuffled Prompts
 * 3. Testing Suites (Playwright / Node)
 * 4. Architecture & User Guide Documentation Sync (docs/OMNISEARCH_GUIDE.md)
 */

(function (root, factory) {
  if (typeof define === 'function' && define.amd) {
    define([], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  } else {
    root.OmnisearchCapabilitiesRegistry = factory();
  }
}(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  // Broad Discovery Area Definitions
  const CAPABILITY_GROUPS = [
    { id: 'all', label: 'All Capabilities', icon: '✨', badgeClass: 'omni-group-all' },
    { id: 'search_nav', label: 'Search & Navigation', icon: '🔍', badgeClass: 'omni-group-nav' },
    { id: 'money_calc', label: 'Money & Calculations', icon: '💰', badgeClass: 'omni-group-math' },
    { id: 'dates_conv', label: 'Dates & Conversions', icon: '📐', badgeClass: 'omni-group-conv' },
    { id: 'biz_reports', label: 'Business & Reports', icon: '📊', badgeClass: 'omni-group-biz' },
    { id: 'cust_prod_staff', label: 'Customers, Products & Staff', icon: '👥', badgeClass: 'omni-group-cust' },
    { id: 'smart_lang', label: 'Language & Smart Queries', icon: '🌐', badgeClass: 'omni-group-lang' }
  ];

  const CAPABILITIES = [
    // ─────────────────────────────────────────────────────────────────────────
    // 1. SEARCH & NAVIGATION
    // ─────────────────────────────────────────────────────────────────────────
    {
      id: 'CAP_NAV_SLASH',
      group: 'search_nav',
      name: 'Direct Slash Navigation Shortcuts',
      icon: '⚡',
      shortDesc: 'Instant keyboard slash shortcuts to jump directly to any RupeeCRM workspace or screen.',
      quickExamples: ['/inv', '/khata', '/theme'],
      detailedDesc: 'Type a forward slash followed by the screen abbreviation to immediately route to that screen or execute a global action without navigating manually.',
      allExamples: [
        { query: '/inv', note: 'Opens New Tax Invoice billing creator modal' },
        { query: '/quo', note: 'Opens New Estimate / Quotation creator' },
        { query: '/khata', note: 'Direct jump to Customer Statements & Ledger' },
        { query: '/pay', note: 'Opens Staff Payroll, Attendance & Salary manager' },
        { query: '/po', note: 'Opens Purchase Order desk for vendor orders' },
        { query: '/party', note: 'Opens Vendor & Supplier directory' },
        { query: '/stock', note: 'Opens Inventory & Product master catalog' },
        { query: '/goal', note: 'Opens Financial Goals tracker' },
        { query: '/save', note: 'Opens Business & Personal Savings ledger' },
        { query: '/todo', note: 'Opens Interactive Kanban Planner board' },
        { query: '/personal', note: 'Switches to Personal Finance dashboard' },
        { query: '/biz', note: 'Switches to Core Business Intelligence cockpit' },
        { query: '/backup', note: 'Opens Backup & Restore settings' },
        { query: '/theme', note: 'Toggles between light and dark UI themes' },
        { query: '/lock', note: 'Locks active session into PIN-protected Safe Mode' }
      ],
      aliases: ['slash', 'navigation', 'shortcuts', 'route', 'goto', 'switch', 'lock', 'theme', 'dark mode', 'light mode', 'screen lock'],
      supportedVariants: ['/inv', '/quo', '/khata', '/pay', '/po', '/party', '/stock', '/goal', '/save', '/todo', '/personal', '/biz', '/backup', '/theme', '/lock', 'dark mode', 'light mode', 'lock app'],
      relatedCapabilities: ['CAP_NAV_PAGE', 'CAP_SEARCH_ENTITIES'],
      limitations: 'Slash commands execute immediately upon selection or Enter.'
    },
    {
      id: 'CAP_NAV_PAGE',
      group: 'search_nav',
      name: 'Natural Language Screen Navigation',
      icon: '🧭',
      shortDesc: 'Jump to application tabs and workspaces using plain English or spoken commands.',
      quickExamples: ['open customers', 'go to inventory', 'open expenses'],
      detailedDesc: 'Type natural navigational phrases like "go to", "open", or "show" to instantly route to any page in RupeeCRM.',
      allExamples: [
        { query: 'open customers', note: 'Navigates to Customer Directory' },
        { query: 'go to inventory', note: 'Navigates to Warehouse & Products Master' },
        { query: 'open expenses', note: 'Navigates to Business Expense Tracker' },
        { query: 'go to vendors', note: 'Navigates to Supplier & Party Ledger' },
        { query: 'show statements', note: 'Navigates to Customer Account Statements' }
      ],
      aliases: ['open', 'go to', 'goto', 'navigate', 'show screen', 'switch page'],
      supportedVariants: ['open customers', 'go to inventory', 'open expenses', 'go to vendors', 'open planner', 'open reports'],
      relatedCapabilities: ['CAP_NAV_SLASH', 'CAP_SEARCH_ENTITIES'],
      limitations: 'Routes seamlessly without losing unsaved form states.'
    },
    {
      id: 'CAP_SEARCH_ENTITIES',
      group: 'search_nav',
      name: 'Unified Entity & Record Search',
      icon: '🔍',
      shortDesc: 'Fuzzy search across customers, suppliers, inventory items, invoices, and staff members.',
      quickExamples: ['Rahul Patil', 'INV-1001', 'UltraTech Cement'],
      detailedDesc: 'Instantly matches names, phone numbers, invoice serial numbers, item names, barcodes, and staff records across the entire database.',
      allExamples: [
        { query: 'Rahul Patil', note: 'Finds customer profile, phone & ledger' },
        { query: '9822113344', note: 'Finds entity by mobile number' },
        { query: 'INV-1001', note: 'Opens invoice breakdown and payment details' },
        { query: 'UltraTech Cement 50kg', note: 'Finds product stock, pricing & barcode' },
        { query: 'ABC Steel Traders', note: 'Finds vendor supplier profile & payable dues' },
        { query: 'Sachin Kamble', note: 'Finds staff record, salary & attendance' }
      ],
      aliases: ['search', 'find', 'lookup', 'customer search', 'product search', 'invoice search', 'vendor search', 'staff search'],
      supportedVariants: ['Search by Name', 'Search by Phone', 'Search by Bill #', 'Search by Barcode / SKU'],
      relatedCapabilities: ['CAP_BI_RECEIVABLES', 'CAP_BI_INVENTORY', 'CAP_BI_DOCUMENTS'],
      limitations: 'Performs realtime in-memory indexing for instantaneous offline results.'
    },
    {
      id: 'CAP_ACTION_CREATE',
      group: 'search_nav',
      name: 'Smart In-Place Fast Actions',
      icon: '⚡',
      shortDesc: 'Create customers, log expenses, pin tasks, generate UPI QR codes, and draft notes without leaving the omnibar.',
      quickExamples: ['customer Manoj 9822113344 Kolhapur', 'todo call rahul 4pm', 'upi 500'],
      detailedDesc: 'Executes rapid business creation workflows inline with an interactive preview card before final persistence.',
      allExamples: [
        { query: 'customer Manoj 9822113344 Kolhapur', note: 'Registers new customer in-place with name, phone and city' },
        { query: 'todo call Rahul Patil 4pm', note: 'Pins interactive sticky task to Kanban Planner' },
        { query: 'upi 500', note: 'Generates live dynamic on-screen UPI QR code for ₹500' },
        { query: 'note client requested bulk discount', note: 'Pins quick business note to dashboard scratchpad' },
        { query: 'remind Rahul Patil', note: 'Opens WhatsApp draft with payment due link' }
      ],
      aliases: ['create customer', 'new customer', 'todo', 'task', 'upi', 'qr code', 'note', 'scratchpad', 'remind', 'whatsapp reminder'],
      supportedVariants: ['customer <Name> <Phone> <City>', 'todo <Task Description>', 'upi <Amount>', 'note <Text>', 'remind <Customer>'],
      relatedCapabilities: ['CAP_BI_EXPENSES', 'CAP_BI_RECEIVABLES'],
      limitations: 'Interactive actions show editable confirmation cards before database save.'
    },

    // ─────────────────────────────────────────────────────────────────────────
    // 2. MONEY & CALCULATIONS
    // ─────────────────────────────────────────────────────────────────────────
    {
      id: 'CAP_MATH_ARITH',
      group: 'money_calc',
      name: 'Spotlight AST Arithmetic Engine',
      icon: '🧮',
      shortDesc: 'Safe mathematical evaluation with operator precedence, brackets, powers, square roots, and decimals.',
      quickExamples: ['(450 * 12) + 2500', '17% of 5633', 'sqrt 144 + 2^4'],
      detailedDesc: 'Evaluates complex expressions deterministically via AST token parsing (zero unsafe eval). Supports +, -, *, /, ^, %, sqrt, brackets, and negative numbers.',
      allExamples: [
        { query: '(450 * 12) + 2500', note: 'Parenthesized math with operator precedence (= 7,900)' },
        { query: '17% of 5633', note: 'Standard percentage calculation (= ₹957.61)' },
        { query: '5633 + 17%', note: 'Percentage markup / addition (= ₹6,590.61)' },
        { query: '5633 - 17%', note: 'Percentage deduction (= ₹4,675.39)' },
        { query: 'sqrt 144 + 2^4', note: 'Square root and exponentiation (= 28)' },
        { query: 'divide 10000 by 4', note: 'Natural English division (= 2,500)' },
        { query: 'multiply 250 by 12', note: 'Natural English multiplication (= 3,000)' },
        { query: '5000 less 750', note: 'Natural subtraction expression (= 4,250)' },
        { query: '-50 + 100', note: 'Negative number handling (= 50)' },
        { query: '0.0001 + 0.0002', note: 'High-precision decimal arithmetic (= 0.0003)' }
      ],
      aliases: ['math', 'arithmetic', 'calculate', 'calc', 'plus', 'minus', 'multiply', 'divide', 'percentage', 'power', 'sqrt', 'square root'],
      supportedVariants: ['+', '-', '*', '/', '^', '%', 'sqrt', '(', ')', 'of', 'less', 'divide by', 'multiply by'],
      relatedCapabilities: ['CAP_MATH_INDIAN_WORDS', 'CAP_MATH_GST_EXCL', 'CAP_SMART_SESSION'],
      limitations: 'Evaluates strictly locally in <2ms with zero cloud dependency.'
    },
    {
      id: 'CAP_MATH_INDIAN_WORDS',
      group: 'money_calc',
      name: 'Indian Number Words & Colloquial Quantifiers',
      icon: '🔢',
      shortDesc: 'Full support for lakh, crore, hazar, dedh, dhai, sawa, paune, and compound Indian denominations.',
      quickExamples: ['dedh lakh + 50 hazar', 'dhai hazar * 4', 'sawa do lakh - 25000'],
      detailedDesc: 'Seamlessly understands traditional Indian numerical expressions and colloquial fractional prefixes in arithmetic calculations.',
      allExamples: [
        { query: '1.5 lakh + 50 hazar', note: 'Indian number denomination arithmetic (= ₹2,00,000)' },
        { query: 'dedh lakh + 50000', note: 'Dedh (1.5x) resolution (= 150,000 + 50,000 = 200,000)' },
        { query: 'dhai hazar * 4', note: 'Dhai (2.5x) resolution (= 2,500 * 4 = 10,000)' },
        { query: 'sawa lakh', note: 'Sawa (1.25x) resolution (= 125,000)' },
        { query: 'paune lakh', note: 'Paune (0.75x) resolution (= 75,000)' },
        { query: 'sawa do lakh', note: 'Sawa Do (2.25x) resolution (= 225,000)' },
        { query: 'paune do hazar', note: 'Paune Do (1.75x) resolution (= 1,750)' },
        { query: 'sawa teen lakh', note: 'Sawa Teen (3.25x) resolution (= 325,000)' },
        { query: 'paune teen lakh', note: 'Paune Teen (2.75x) resolution (= 275,000)' },
        { query: 'aadha crore', note: 'Aadha (0.5x) resolution (= 50,00,000 / 5 Million)' }
      ],
      aliases: ['lakh', 'crore', 'hazar', 'hazaar', 'haz', 'lac', 'cr', 'k', 'dedh', 'dhai', 'adhai', 'sawa', 'paune', 'aadha', 'adha', 'sawa do', 'paune do', 'sawa teen', 'paune teen'],
      supportedVariants: ['hazar (1,000)', 'lakh (100,000)', 'crore (10,000,000)', 'dedh (1.5x)', 'dhai (2.5x)', 'sawa (1.25x)', 'paune (0.75x)', 'aadha (0.5x)'],
      relatedCapabilities: ['CAP_MATH_ARITH', 'CAP_LANG_VERNACULAR'],
      limitations: 'Applies to any standard arithmetic operation or business query.'
    },
    {
      id: 'CAP_MATH_GST_EXCL',
      group: 'money_calc',
      name: 'Forward GST (Tax-Exclusive Addition)',
      icon: '🏛️',
      shortDesc: 'Calculate taxable total with split CGST and SGST breakdown for any tax slab.',
      quickExamples: ['gst 18% on 5000', 'calculate 18% GST on 50000', '1500 + 18% gst'],
      detailedDesc: 'Calculates tax amount on base amount and displays full breakdown: Base Amount, CGST (50%), SGST (50%), and Final Grand Total.',
      allExamples: [
        { query: 'gst 18% on 5000', note: 'Base ₹5000 + CGST 9% (₹450) + SGST 9% (₹450) = ₹5,900' },
        { query: 'calculate 18% GST on 50000', note: 'Base ₹50000 + ₹9000 GST = ₹59,000 total' },
        { query: '1500 + 18% gst', note: 'Base ₹1500 + ₹270 GST = ₹1,770 total' },
        { query: '28% gst on 12000', note: '28% tax slab calculation on luxury / auto goods' },
        { query: '5% gst on 2400', note: '5% essential commodities tax calculation' }
      ],
      aliases: ['gst', 'tax', 'cgst', 'sgst', 'forward gst', 'exclusive gst', 'add gst', 'gst calculation'],
      supportedVariants: ['<rate>% gst on <amount>', '<amount> + <rate>% gst', 'calculate <rate>% gst on <amount>'],
      relatedCapabilities: ['CAP_MATH_GST_INCL', 'CAP_BI_FIRM'],
      limitations: 'Supports all standard GST slabs (0%, 3%, 5%, 12%, 18%, 28%) and custom rates.'
    },
    {
      id: 'CAP_MATH_GST_INCL',
      group: 'money_calc',
      name: 'Reverse GST (Tax-Inclusive Extraction)',
      icon: '🏷️',
      shortDesc: 'Extract pre-tax base price and embedded GST from tax-inclusive retail selling prices.',
      quickExamples: ['gst 18% inclusive 5900', '1500 with 18% gst', '5900 reverse gst 18%'],
      detailedDesc: 'Formula: Base = Amount / (1 + Rate/100), Tax = Amount - Base. Provides exact CGST, SGST, and net billing values.',
      allExamples: [
        { query: 'gst 18% inclusive 5900', note: 'Calculates ₹5,000 net base + ₹900 tax breakdown' },
        { query: '1500 with 18% gst', note: 'Extracts ₹1,271.19 base + ₹228.81 tax from ₹1,500 bill' },
        { query: '5900 reverse gst 18%', note: 'Reverse GST calculation on ₹5,900 gross' },
        { query: '10000 inclusive 28% gst', note: 'Extracts net cost from ₹10,000 gross total' }
      ],
      aliases: ['reverse gst', 'inclusive gst', 'with gst', 'extract gst', 'net base price', 'tax inclusive'],
      supportedVariants: ['gst <rate>% inclusive <amount>', '<amount> with <rate>% gst', '<amount> inclusive <rate>% gst', '<amount> reverse gst <rate>%'],
      relatedCapabilities: ['CAP_MATH_GST_EXCL', 'CAP_MATH_DISCOUNT'],
      limitations: 'Accurate to 2 decimal places with standard commercial rounding.'
    },
    {
      id: 'CAP_MATH_DISCOUNT',
      group: 'money_calc',
      name: 'Discounts & Savings Calculator',
      icon: '🏷️',
      shortDesc: 'Calculate percentage discounts, flat price deductions, and total customer savings.',
      quickExamples: ['10% discount on 5000', '5000 with 15% discount', 'discount 500 on 4500'],
      detailedDesc: 'Evaluates trade discounts and cash discounts. Displays original base, discounted final payable, and net savings.',
      allExamples: [
        { query: '10% discount on 5000', note: 'Calculates discounted total ₹4,500 and ₹500 savings' },
        { query: '5000 with 15% discount', note: 'Deducts 15% (₹750) leaving ₹4,250 payable' },
        { query: 'discount 500 on 4500', note: 'Flat discount calculation leaving ₹4,000 payable' },
        { query: 'reduce 5633 by 17%', note: 'Natural discount phrasing (= ₹4,675.39)' }
      ],
      aliases: ['discount', 'rebate', 'concession', 'savings', 'less discount', 'discounted price'],
      supportedVariants: ['<rate>% discount on <amount>', '<amount> with <rate>% discount', 'discount <amount> on <total>', 'reduce <amount> by <rate>%'],
      relatedCapabilities: ['CAP_MATH_MARGIN', 'CAP_MATH_ARITH'],
      limitations: 'Supports sequential or single discount structures.'
    },
    {
      id: 'CAP_MATH_MARGIN',
      group: 'money_calc',
      name: 'Profit Margin & Markup Analysis',
      icon: '📈',
      shortDesc: 'Compute gross margin %, cost markup %, and absolute net profit per item.',
      quickExamples: ['cost 500 selling 750 margin', 'cost 800 price 1200', 'cost 500 markup 50%'],
      detailedDesc: 'Margin = (Price - Cost) / Price. Markup = (Price - Cost) / Cost. Instantly calculates profit metrics for pricing decisions.',
      allExamples: [
        { query: 'margin cost 800 price 1200', note: 'Gross margin: 33.33% margin, 50% markup, ₹400 profit' },
        { query: 'cost 500 selling 750 margin', note: 'Calculates ₹250 profit, 33.3% margin, 50% markup' },
        { query: 'cost 500 markup 50%', note: 'Calculates selling price ₹750 based on 50% markup' }
      ],
      aliases: ['margin', 'markup', 'profit margin', 'gross margin', 'cost price', 'selling price'],
      supportedVariants: ['cost <Cost> selling <Price> margin', 'margin cost <Cost> price <Price>', 'cost <Cost> markup <Rate>%'],
      relatedCapabilities: ['CAP_BI_SALES', 'CAP_MATH_DISCOUNT'],
      limitations: 'Calculates both margin (on sales) and markup (on cost) simultaneously.'
    },
    {
      id: 'CAP_MATH_CHANGE',
      group: 'money_calc',
      name: 'Cashier Change Helper & Denominations',
      icon: '💵',
      shortDesc: 'Calculate cash register return change with exact currency notes breakdown.',
      quickExamples: ['change for 2000 bill 1435', 'paid 2000 for 1450 bill', 'change for 500 bill 380'],
      detailedDesc: 'Computes return balance when a customer tenders a cash note and gives optimal denomination count (₹500, ₹200, ₹100, ₹50, ₹20, ₹10, ₹5, ₹2, ₹1).',
      allExamples: [
        { query: 'change for 2000 bill 1435', note: 'Cash change: ₹565 (1x ₹500, 1x ₹50, 1x ₹10, 1x ₹5)' },
        { query: 'paid 2000 for 1450 bill', note: 'Change return: ₹550 (1x ₹500, 1x ₹50)' },
        { query: 'change for 500 bill 380', note: 'Change return: ₹120 (1x ₹100, 1x ₹20)' }
      ],
      aliases: ['change', 'cash return', 'paid cash', 'tendered', 'cashier change', 'return change'],
      supportedVariants: ['change for <Tendered> bill <BillAmount>', 'paid <Tendered> for <BillAmount> bill'],
      relatedCapabilities: ['CAP_MATH_SPLIT', 'CAP_BI_DOCUMENTS'],
      limitations: 'Standard Indian currency denominations used for tender breakdown.'
    },
    {
      id: 'CAP_MATH_SPLIT',
      group: 'money_calc',
      name: 'Bill Splitting & Ratio Distribution',
      icon: '👥',
      shortDesc: 'Divide shared expenses evenly among people or apportion amounts by custom mathematical ratios.',
      quickExamples: ['split 4500 by 4', 'split 10000 in 2:3:5', 'divide 4500 among 4 people'],
      detailedDesc: 'Even split divides total equally per head. Ratio split (e.g. 2:3:5) computes exact proportional share per participant.',
      allExamples: [
        { query: 'split 4500 by 4', note: 'Divides bill evenly (₹1,125 per person)' },
        { query: 'split 10000 in 2:3:5', note: 'Divides amount by ratio (₹2,000 : ₹3,000 : ₹5,000)' },
        { query: 'divide 4500 among 4 people', note: 'Natural English phrasing for bill division (= ₹1,125 each)' }
      ],
      aliases: ['split', 'split bill', 'divide bill', 'ratio split', 'share expense'],
      supportedVariants: ['split <amount> by <N>', 'split <amount> into <N>', 'split <amount> in <r1>:<r2>:<r3>', 'divide <amount> among <N> people'],
      relatedCapabilities: ['CAP_MATH_CHANGE', 'CAP_BI_EXPENSES'],
      limitations: 'Ratios support any number of colon-separated integer parts.'
    },
    {
      id: 'CAP_MATH_SI',
      group: 'money_calc',
      name: 'Simple Interest (SI) Calculator',
      icon: '🏦',
      shortDesc: 'Calculate simple interest, annual yield, and total maturity amount on deposits and loans.',
      quickExamples: ['SI on 50000 at 8.5% for 3 years', 'SI on 1 lakh at 7% for 5 years'],
      detailedDesc: 'Formula: Interest = (P * R * T) / 100. Total Maturity = Principal + Interest.',
      allExamples: [
        { query: 'SI on 50000 at 8.5% for 3 years', note: 'Interest: ₹12,750, Total Maturity: ₹62,750' },
        { query: 'SI on 1 lakh at 7% for 5 years', note: 'Interest: ₹35,000, Total Maturity: ₹1,35,000' }
      ],
      aliases: ['si', 'simple interest', 'interest on deposit', 'interest calculation'],
      supportedVariants: ['SI on <Principal> at <Rate>% for <Time> years'],
      relatedCapabilities: ['CAP_MATH_CI', 'CAP_MATH_EMI'],
      limitations: 'Time specified in years (or decimals thereof).'
    },
    {
      id: 'CAP_MATH_CI',
      group: 'money_calc',
      name: 'Compound Interest (CI) Calculator',
      icon: '📈',
      shortDesc: 'Compute compound interest with annual compounding and total wealth growth.',
      quickExamples: ['CI on 1 lakh at 7% for 5 years', 'CI on 50000 at 8% for 3 years'],
      detailedDesc: 'Formula: Amount = P * (1 + R/100)^T, Interest = Amount - Principal.',
      allExamples: [
        { query: 'CI on 1 lakh at 7% for 5 years', note: 'Interest: ₹40,255, Total: ₹1,40,255' },
        { query: 'CI on 50000 at 8% for 3 years', note: 'Interest: ₹12,985.60, Total: ₹62,985.60' }
      ],
      aliases: ['ci', 'compound interest', 'compounding', 'growth investment'],
      supportedVariants: ['CI on <Principal> at <Rate>% for <Time> years'],
      relatedCapabilities: ['CAP_MATH_SI', 'CAP_MATH_SIP'],
      limitations: 'Calculated using standard annual compounding intervals.'
    },
    {
      id: 'CAP_MATH_SIP',
      group: 'money_calc',
      name: 'Systematic Investment Plan (SIP)',
      icon: '💎',
      shortDesc: 'Project monthly mutual fund SIP wealth accumulation, total invested, and capital gains.',
      quickExamples: ['SIP 5000 at 12% for 10 years', 'SIP 10000 at 15% for 5 years'],
      detailedDesc: 'Formula: M = P * [((1 + i)^n - 1) / i] * (1 + i) where i = R/1200, n = Years * 12.',
      allExamples: [
        { query: 'SIP 5000 at 12% for 10 years', note: 'Invested: ₹6,00,000, Wealth Gain: ₹5,61,695, Maturity: ₹11,61,695' },
        { query: 'SIP 10000 at 15% for 5 years', note: 'Invested: ₹6,00,000, Wealth Gain: ₹2,96,818, Maturity: ₹8,96,818' }
      ],
      aliases: ['sip', 'systematic investment', 'mutual fund sip', 'wealth projection'],
      supportedVariants: ['SIP <MonthlyAmount> at <Rate>% for <Years> years'],
      relatedCapabilities: ['CAP_MATH_CI', 'CAP_MATH_EMI'],
      limitations: 'Assumes consistent monthly contributions with compounded growth.'
    },
    {
      id: 'CAP_MATH_TDS',
      group: 'money_calc',
      name: 'Tax Deducted at Source (TDS)',
      icon: '📑',
      shortDesc: 'Compute statutory TDS withholding tax and net contractor/vendor disbursement.',
      quickExamples: ['TDS 10% on 75000', 'TDS 2% on 150000', 'TDS 1% on 500000'],
      detailedDesc: 'Calculates statutory TDS deduction amount and remaining net payable payout to vendor/contractor.',
      allExamples: [
        { query: 'TDS 10% on 75000', note: 'TDS: ₹7,500, Net Payable: ₹67,500' },
        { query: 'TDS 2% on 150000', note: 'TDS: ₹3,000, Net Payable: ₹1,47,000' },
        { query: 'TDS 1% on 500000', note: 'TDS: ₹5,000, Net Payable: ₹4,95,000' }
      ],
      aliases: ['tds', 'tax deducted at source', 'withholding tax', 'tax deduction'],
      supportedVariants: ['TDS <Rate>% on <Amount>'],
      relatedCapabilities: ['CAP_MATH_GST_EXCL', 'CAP_BI_PAYABLES'],
      limitations: 'Calculates standard percentage withholding on gross invoice total.'
    },
    {
      id: 'CAP_MATH_EMI',
      group: 'money_calc',
      name: 'Loan Equated Monthly Installment (EMI)',
      icon: '🏦',
      shortDesc: 'Compute monthly loan repayments, total interest liability, and overall payback for vehicle, home, or business loans.',
      quickExamples: ['emi 500000 at 9.5% for 5 years', 'emi on 5 lakh at 10% for 5 years'],
      detailedDesc: 'Formula: EMI = [P * r * (1+r)^n] / [(1+r)^n - 1] where r = annual rate / 1200, n = tenure in months.',
      allExamples: [
        { query: 'emi 500000 at 9.5% for 5 years', note: 'Monthly EMI: ₹10,501, Total Interest: ₹1,30,060, Total Payback: ₹6,30,060' },
        { query: 'emi on 5 lakh at 10% for 5 years', note: 'Monthly EMI: ₹10,624, Total Interest: ₹1,37,411, Total Payback: ₹6,37,411' }
      ],
      aliases: ['emi', 'loan emi', 'monthly installment', 'home loan', 'car loan', 'business loan emi'],
      supportedVariants: ['emi <Principal> at <Rate>% for <Tenure> years', 'emi on <Principal> at <Rate>% for <Tenure> years'],
      relatedCapabilities: ['CAP_MATH_SI', 'CAP_MATH_SIP'],
      limitations: 'Calculates standard reducing balance monthly installments.'
    },
    {
      id: 'CAP_MATH_WORDS',
      group: 'money_calc',
      name: 'Number to Words & Cheque Spelling',
      icon: '✍️',
      shortDesc: 'Convert numeric values into official English words formatted for bank cheques and invoices.',
      quickExamples: ['words 125000', 'words 54320', 'words 10000000'],
      detailedDesc: 'Converts any numerical amount into official Indian grammatical English words (e.g. Rupees One Lakh Twenty-Five Thousand Only).',
      allExamples: [
        { query: 'words 125000', note: 'Converts number to English words for official bank cheques' },
        { query: 'words 54320', note: 'Fifty-Four Thousand Three Hundred Twenty Rupees' },
        { query: 'words 10000000', note: 'One Crore Rupees' }
      ],
      aliases: ['words', 'cheque words', 'spelling', 'in words', 'number to words', 'amount in words'],
      supportedVariants: ['words <Number>', 'cheque words <Number>'],
      relatedCapabilities: ['CAP_MATH_INDIAN_WORDS', 'CAP_BI_DOCUMENTS'],
      limitations: 'Supports standard Indian numbering denomination format (Lakhs, Crores).'
    },

    // ─────────────────────────────────────────────────────────────────────────
    // 3. DATES & CONVERSIONS
    // ─────────────────────────────────────────────────────────────────────────
    {
      id: 'CAP_CONV_AREA',
      group: 'dates_conv',
      name: 'Area & Indian Land Unit Conversions',
      icon: '📐',
      shortDesc: 'Convert between sq ft, sq m, acres, hectares, guntha, bigha, and brass.',
      quickExamples: ['1 guntha in sq ft', '100 sqft in sqm', '2 bigha in acre'],
      detailedDesc: 'Supports standard metric, imperial, and traditional Indian land measurement units with exact mathematical conversion factors.',
      allExamples: [
        { query: '1 guntha in sq ft', note: '1 Guntha = 1,089 sq ft' },
        { query: '1 bigha in guntha', note: '1 Bigha = 25 Gunthas (27,225 sq ft)' },
        { query: '1 acre in guntha', note: '1 Acre = 40 Gunthas (43,560 sq ft)' },
        { query: '1 hectare in sqft', note: '1 Hectare = 107,639 sq ft' },
        { query: '500 sqft in brass', note: '1 Brass = 100 sq ft' },
        { query: '100 sqyd in sqm', note: 'Square Yard to Square Meter (= 83.61 sq m)' },
        { query: '1 sqmi in sqkm', note: 'Square Mile to Square Kilometer (= 2.59 sq km)' }
      ],
      aliases: ['guntha', 'gunta', 'गुंठा', 'गुंठे', 'bigha', 'बीघा', 'acre', 'एकर', 'hectare', 'brass', 'sqft', 'sqm', 'sqyd', 'sqkm', 'sqmi', 'land', 'area', 'plot'],
      supportedUnits: ['sqft', 'sqm', 'sqyd', 'sqkm', 'sqmi', 'guntha (1,089 sqft)', 'bigha (27,225 sqft)', 'acre (43,560 sqft)', 'hectare (107,639 sqft)', 'brass (100 sqft)'],
      relatedCapabilities: ['CAP_CONV_LENGTH', 'CAP_MATH_ARITH'],
      limitations: 'Uses authoritative Indian land conversion benchmarks (1 Guntha = 1,089 sq ft).'
    },
    {
      id: 'CAP_CONV_LENGTH',
      group: 'dates_conv',
      name: 'Length & Distance Conversions',
      icon: '📏',
      shortDesc: 'Convert between mm, cm, meters, km, inches, feet, yards, miles, nautical miles, and gaj.',
      quickExamples: ['100 meters in feet', '5 miles in km', '10 gaj in feet'],
      detailedDesc: 'Comprehensive metric and imperial distance conversion registry including traditional trade units like Gaj (0.9144 m / 1 yard).',
      allExamples: [
        { query: '100 meters in feet', note: '100 m = 328.08 ft' },
        { query: '5 miles in km', note: '5 mi = 8.05 km' },
        { query: '10 gaj in feet', note: '10 Gaj = 30 ft (9.14 m)' },
        { query: '12 inches in cm', note: '12 in = 30.48 cm' },
        { query: '1 nautical mile in km', note: '1 NM = 1.852 km' },
        { query: '500 mm in inches', note: '500 mm = 19.69 inches' }
      ],
      aliases: ['length', 'distance', 'mm', 'cm', 'meter', 'km', 'inch', 'inches', 'foot', 'feet', 'yard', 'mile', 'miles', 'gaj', 'nautical mile', 'nm'],
      supportedUnits: ['mm', 'cm', 'm', 'km', 'in', 'ft', 'yd', 'mi', 'nm', 'gaj'],
      relatedCapabilities: ['CAP_CONV_AREA', 'CAP_CONV_SPEED'],
      limitations: 'Exact to 4 decimal places.'
    },
    {
      id: 'CAP_CONV_WEIGHT',
      group: 'dates_conv',
      name: 'Weight, Mass & Indian Bullion Units',
      icon: '⚖️',
      shortDesc: 'Convert mg, grams, kg, quintal, tonne, tola, carat, pounds, ounces, and stone.',
      quickExamples: ['5 kg in grams', '1 tola in grams', '2 quintal in kg'],
      detailedDesc: 'Supports standard metric mass, imperial avoirdupois weights, gold/bullion trade units (1 Tola = 11.6638 g), and gemological units (1 Carat = 0.2 g).',
      allExamples: [
        { query: '5 kg in grams', note: '5 kg = 5,000 g' },
        { query: '1 tola in grams', note: '1 Tola = 11.6638 g (Indian bullion gold unit)' },
        { query: '2 quintal in kg', note: '2 Quintal = 200 kg' },
        { query: '5 tonnes in kg', note: '5 Tonne = 5,000 kg' },
        { query: '10 carats in grams', note: '10 Carat = 2.0 g (Gemstone unit)' },
        { query: '100 lbs in kg', note: '100 lbs = 45.36 kg' },
        { query: '16 ounces in grams', note: '16 oz = 453.59 g (1 lb)' },
        { query: '10 stone in kg', note: '10 Stone = 63.5 kg' }
      ],
      aliases: ['weight', 'mass', 'kg', 'gram', 'gms', 'mg', 'quintal', 'tonne', 'ton', 'tola', 'carat', 'pound', 'lbs', 'ounce', 'oz', 'stone'],
      supportedUnits: ['mg', 'g', 'kg', 'quintal (100 kg)', 'tonne (1,000 kg)', 'tola (11.6638 g)', 'carat (0.2 g)', 'lb', 'oz', 'stone'],
      relatedCapabilities: ['CAP_CONV_VOLUME', 'CAP_MATH_ARITH'],
      limitations: 'All mass factors mathematically anchored to base gram standard.'
    },
    {
      id: 'CAP_CONV_VOLUME',
      group: 'dates_conv',
      name: 'Volume & Liquid Measure Conversions',
      icon: '🧪',
      shortDesc: 'Convert milliliters, liters, US gallons, fluid ounces, cups, pints, and quarts.',
      quickExamples: ['5 liters in ml', '1 gallon in liters', '2 cups in ml'],
      detailedDesc: 'Converts liquid measures across metric volume and imperial/US customary culinary & commercial units.',
      allExamples: [
        { query: '5 liters in ml', note: '5 L = 5,000 mL' },
        { query: '1 gallon in liters', note: '1 US Gallon = 3.785 L' },
        { query: '2 cups in ml', note: '2 Cups = 473.18 mL' },
        { query: '100 floz in liters', note: '100 fl oz = 2.96 L' },
        { query: '4 pints in liters', note: '4 Pints = 1.89 L' },
        { query: '2 quarts in liters', note: '2 Quarts = 1.89 L' }
      ],
      aliases: ['volume', 'liquid', 'liter', 'liters', 'ltr', 'ml', 'gallon', 'gal', 'floz', 'cup', 'cups', 'pint', 'quart'],
      supportedUnits: ['ml', 'l', 'gal (3.785 L)', 'floz (29.57 mL)', 'cup (236.59 mL)', 'pint (473.18 mL)', 'quart (946.35 mL)'],
      relatedCapabilities: ['CAP_CONV_WEIGHT', 'CAP_MATH_ARITH'],
      limitations: 'Uses US customary liquid measure standards.'
    },
    {
      id: 'CAP_CONV_TEMP',
      group: 'dates_conv',
      name: 'Temperature Scale Conversions',
      icon: '🌡️',
      shortDesc: 'Convert between Celsius (°C), Fahrenheit (°F), and Kelvin (K) with exact affine transformations.',
      quickExamples: ['100 c to f', '-40 c to f', '0 k to c'],
      detailedDesc: 'Affine conversions preserving exact scientific formulas: F = (C * 9/5) + 32, K = C + 273.15.',
      allExamples: [
        { query: '100 c to f', note: '100 °C = 212 °F (Boiling point)' },
        { query: '-40 c to f', note: '-40 °C = -40 °F (Equivalence point)' },
        { query: '0 k to c', note: '0 K = -273.15 °C (Absolute zero)' },
        { query: '98.6 f to c', note: '98.6 °F = 37 °C (Human body temperature)' },
        { query: '300 k to f', note: '300 K = 80.33 °F' }
      ],
      aliases: ['temperature', 'temp', 'celsius', 'centigrade', 'fahrenheit', 'kelvin', 'c to f', 'f to c'],
      supportedUnits: ['Celsius (°C)', 'Fahrenheit (°F)', 'Kelvin (K)'],
      relatedCapabilities: ['CAP_CONV_SPEED', 'CAP_MATH_ARITH'],
      limitations: 'Evaluates affine zero offsets accurately.'
    },
    {
      id: 'CAP_CONV_SPEED',
      group: 'dates_conv',
      name: 'Speed & Velocity Conversions',
      icon: '⚡',
      shortDesc: 'Convert between km/h, mph, m/s, knots, and feet per second.',
      quickExamples: ['100 kmh in mph', '50 mps in kmh', '25 knots in kmh'],
      detailedDesc: 'Supports automotive, meteorological, and nautical speed units with exact velocity factors.',
      allExamples: [
        { query: '100 kmh in mph', note: '100 km/h = 62.14 mph' },
        { query: '50 mps in kmh', note: '50 m/s = 180 km/h' },
        { query: '25 knots in kmh', note: '25 Knots = 46.3 km/h' },
        { query: '100 fps in mph', note: '100 ft/s = 68.18 mph' }
      ],
      aliases: ['speed', 'velocity', 'kmh', 'kph', 'km/h', 'mph', 'mps', 'm/s', 'knot', 'knots', 'fps', 'ft/s'],
      supportedUnits: ['km/h (kph)', 'mph', 'm/s (mps)', 'knot (kt)', 'ft/s (fps)'],
      relatedCapabilities: ['CAP_CONV_LENGTH', 'CAP_CONV_TIME'],
      limitations: 'Anchored to km/h base scale.'
    },
    {
      id: 'CAP_CONV_STORAGE',
      group: 'dates_conv',
      name: 'Data Storage & Memory Units',
      icon: '💾',
      shortDesc: 'Convert Bytes, KB, MB, GB, TB, and PB using binary 1024 standard multiples.',
      quickExamples: ['1024 mb in gb', '500 gb in tb', '1 tb in mb'],
      detailedDesc: 'Binary 1,024 byte multiples for computer memory and drive storage computations.',
      allExamples: [
        { query: '1024 mb in gb', note: '1,024 MB = 1.0 GB' },
        { query: '500 gb in tb', note: '500 GB = 0.49 TB' },
        { query: '1 tb in mb', note: '1 TB = 1,048,576 MB' },
        { query: '1000000 bytes in kb', note: '1,000,000 Bytes = 976.56 KB' },
        { query: '1 pb in tb', note: '1 PB = 1,024 TB' }
      ],
      aliases: ['storage', 'data', 'memory', 'bytes', 'kb', 'mb', 'gb', 'tb', 'pb', 'kib', 'mib', 'gib', 'tib'],
      supportedUnits: ['Bytes (B)', 'Kilobytes (KB)', 'Megabytes (MB)', 'Gigabytes (GB)', 'Terabytes (TB)', 'Petabytes (PB)'],
      relatedCapabilities: ['CAP_CONV_SPEED', 'CAP_MATH_ARITH'],
      limitations: 'Uses standard binary IEC/JEDEC 1024 exponent basis.'
    },
    {
      id: 'CAP_CONV_TIME',
      group: 'dates_conv',
      name: 'Time Duration Conversions',
      icon: '⏱️',
      shortDesc: 'Convert milliseconds, seconds, minutes, hours, days, weeks, months, and years.',
      quickExamples: ['72 hours in days', '5000 minutes in hours', '2 weeks in hours'],
      detailedDesc: 'Converts temporal durations between high-resolution milliseconds up to calendar years.',
      allExamples: [
        { query: '72 hours in days', note: '72 Hours = 3 Days' },
        { query: '5000 minutes in hours', note: '5,000 Minutes = 83.33 Hours' },
        { query: '2 weeks in hours', note: '2 Weeks = 336 Hours' },
        { query: '86400 seconds in days', note: '86,400 Seconds = 1 Day' },
        { query: '365 days in months', note: '365 Days = 12.17 Months' }
      ],
      aliases: ['time', 'duration', 'ms', 'seconds', 'sec', 'minutes', 'min', 'hours', 'hr', 'days', 'weeks', 'months', 'years'],
      supportedUnits: ['ms', 'sec', 'min', 'hr', 'day', 'week', 'month (30d)', 'year (365d)'],
      relatedCapabilities: ['CAP_DATE_OFFSETS', 'CAP_DATE_INTERVALS'],
      limitations: 'Assumes standard 30-day month and 365-day year multipliers.'
    },
    {
      id: 'CAP_CONV_CURRENCY',
      group: 'dates_conv',
      name: 'World Currency Conversions (40+ Currencies)',
      icon: '💱',
      shortDesc: 'Static offline currency conversions across 40+ world currencies using benchmark reference rates as of September 2026.',
      quickExamples: ['100 usd in inr', '500 eur in inr', '1000 aed in inr'],
      detailedDesc: 'Deterministic offline exchange rates for INR, USD, EUR, GBP, JPY, CNY, CHF, CAD, AUD, NZD, SGD, HKD, AED, SAR, QAR, KWD, BHD, OMR, MYR, THB, IDR, PHP, KRW, ZAR, RUB, TRY, BRL, MXN, SEK, NOK, DKK, PLN, CZK, HUF, ILS, EGP, NGN, BDT, PKR, LKR, NPR, VND. Includes explicit disclaimer tag.',
      allExamples: [
        { query: '100 usd in inr', note: '100 USD = ₹8,650 (Rate: 86.50)' },
        { query: '500 eur in inr', note: '500 EUR = ₹45,600 (Rate: 91.20)' },
        { query: '200 gbp in inr', note: '200 GBP = ₹21,960 (Rate: 109.80)' },
        { query: '1000 aed in inr', note: '1,000 AED = ₹23,550 (Rate: 23.55)' },
        { query: '100 kwd in inr', note: '100 KWD = ₹28,150 (Kuwaiti Dinar @ 281.50)' },
        { query: '10000 jpy in inr', note: '10,000 JPY = ₹5,800 (Japanese Yen @ 0.58)' },
        { query: '50000 inr in usd', note: '₹50,000 = $578.03 USD' },
        { query: '1000 sar in inr', note: '1,000 SAR = ₹23,050 (Saudi Riyal @ 23.05)' }
      ],
      aliases: ['currency', 'forex', 'exchange', 'usd', 'eur', 'gbp', 'aed', 'sar', 'qar', 'kwd', 'bhd', 'omr', 'cad', 'aud', 'nzd', 'sgd', 'hkd', 'jpy', 'cny', 'chf', 'myr', 'thb', 'idr', 'php', 'krw', 'zar', 'rub', 'try', 'brl', 'mxn', 'sek', 'nok', 'dkk', 'pln', 'czk', 'huf', 'ils', 'egp', 'ngn', 'bdt', 'pkr', 'lkr', 'npr', 'vnd', 'dollars', 'pounds', 'euros', 'dirham', 'riyal', 'dinar', 'yen', 'yuan'],
      supportedCurrencies: [
        'USD ($86.50)', 'EUR (€91.20)', 'GBP (£109.80)', 'AED (23.55)', 'SAR (23.05)', 'KWD (281.50)',
        'QAR (23.75)', 'BHD (229.40)', 'OMR (224.70)', 'CAD (61.20)', 'AUD (55.40)', 'SGD (64.80)',
        'JPY (0.58)', 'CNY (11.90)', 'CHF (97.40)', 'HKD (11.10)', 'NZD (50.80)', 'MYR (19.80)',
        'THB (2.52)', 'IDR (0.0053)', 'PHP (1.48)', 'KRW (0.062)', 'ZAR (4.85)', 'RUB (0.94)',
        'TRY (2.45)', 'BRL (15.20)', 'MXN (4.35)', 'SEK (8.25)', 'NOK (8.05)', 'DKK (12.20)',
        'PLN (21.40)', 'CZK (3.65)', 'HUF (0.23)', 'ILS (23.40)', 'EGP (1.78)', 'NGN (0.054)',
        'BDT (0.72)', 'PKR (0.31)', 'LKR (0.29)', 'NPR (0.625)', 'VND (0.0034)'
      ],
      relatedCapabilities: ['CAP_MATH_ARITH', 'CAP_BI_DOCUMENTS'],
      limitations: 'Static offline benchmark reference rates as of September 2026 for indicative planning.'
    },
    {
      id: 'CAP_DATE_OFFSETS',
      group: 'dates_conv',
      name: 'Date Arithmetic & Relative Offsets',
      icon: '📅',
      shortDesc: 'Compute forward and past calendar dates, weekday names, and offset milestones.',
      quickExamples: ['today + 45 days', '2 weeks from now', '10 days ago'],
      detailedDesc: 'Calculates exact future/past calendar dates factoring leap years and month boundaries.',
      allExamples: [
        { query: 'today + 45 days', note: 'Target date, weekday name, and ISO date string' },
        { query: '2 weeks from now', note: 'Calculates date +14 days forward' },
        { query: '3 months after today', note: 'Target date +3 calendar months' },
        { query: '10 days ago', note: 'Past target date -10 days' },
        { query: '1 year from today', note: 'Target date +365 days' }
      ],
      aliases: ['date', 'calendar', 'today', 'days ago', 'from now', 'weeks from now', 'months from now', 'date arithmetic'],
      supportedVariants: ['today + <N> days', '<N> weeks from now', '<N> months after today', '<N> days ago'],
      relatedCapabilities: ['CAP_DATE_INTERVALS', 'CAP_DATE_ANCHORS'],
      limitations: 'Always evaluates relative to local device time.'
    },
    {
      id: 'CAP_DATE_INTERVALS',
      group: 'dates_conv',
      name: 'Date Intervals & Day Differences',
      icon: '🗓️',
      shortDesc: 'Calculate exact elapsed days and durations between two calendar dates.',
      quickExamples: ['days between 2026-03-01 and 2026-03-20', 'difference between 2026-01-01 and 2026-12-31'],
      detailedDesc: 'Parses YYYY-MM-DD, DD/MM/YYYY, or DD-MM-YYYY formats and calculates the exact duration in days and weeks.',
      allExamples: [
        { query: 'days between 2026-03-01 and 2026-03-20', note: '19 Days Duration (2.7 Weeks)' },
        { query: 'days between 2026-04-01 and 2027-03-31', note: '364 Days Duration (Indian Fiscal Year)' }
      ],
      aliases: ['days between', 'date difference', 'duration', 'date gap', 'elapsed days'],
      supportedVariants: ['days between <Date1> and <Date2>', 'difference between <Date1> and <Date2>'],
      relatedCapabilities: ['CAP_DATE_OFFSETS', 'CAP_DATE_ANCHORS'],
      limitations: 'Supports standard ISO YYYY-MM-DD and Indian DD/MM/YYYY date notations.'
    },
    {
      id: 'CAP_DATE_ANCHORS',
      group: 'dates_conv',
      name: 'Fiscal & Calendar Period Anchors',
      icon: '📌',
      shortDesc: 'Resolve current Month End, Quarter End, and Indian Fiscal Year End (FY End) dates.',
      quickExamples: ['month end', 'quarter end', 'FY end'],
      detailedDesc: 'Anchors queries to closing fiscal milestones: Month End (last day of active month), Quarter End (Mar 31, Jun 30, Sep 30, Dec 31), and FY End (March 31).',
      allExamples: [
        { query: 'month end', note: 'Resolves to last day of current month' },
        { query: 'quarter end', note: 'Resolves to upcoming fiscal quarter closing date' },
        { query: 'FY end', note: 'Resolves to Indian Fiscal Year closing date (March 31)' },
        { query: 'fiscal year end', note: 'Full phrasing for Indian FY End' }
      ],
      aliases: ['month end', 'quarter end', 'fy end', 'fiscal year end', 'q end', 'financial year end'],
      supportedVariants: ['month end', 'quarter end', 'FY end', 'fiscal year end'],
      relatedCapabilities: ['CAP_DATE_OFFSETS', 'CAP_BI_SALES'],
      limitations: 'Anchored to Indian corporate fiscal year standard (April 1 – March 31).'
    },

    // ─────────────────────────────────────────────────────────────────────────
    // 4. BUSINESS & REPORTS
    // ─────────────────────────────────────────────────────────────────────────
    {
      id: 'CAP_BI_SALES',
      group: 'biz_reports',
      name: 'Sales Turnover, Revenue & Net Profit',
      icon: '📈',
      shortDesc: 'Authoritative live business turnover, invoice volume, collections, and net profit metrics.',
      quickExamples: ['today sales', 'this month sales', 'this month profit'],
      detailedDesc: 'Queries live sales database with date filtering: Today, Yesterday, This Week, This Month, This Quarter, This Year, and Net Profit (Sales - Purchases - Expenses).',
      allExamples: [
        { query: 'today sales', note: 'Live sales revenue, invoice count & paid/unpaid split today' },
        { query: 'yesterday sales', note: 'Sales volume and invoices logged yesterday' },
        { query: 'this week sales', note: 'Cumulative weekly turnover and invoice count' },
        { query: 'this month sales', note: 'Monthly sales turnover and total collection metrics' },
        { query: 'this year revenue', note: 'Financial year sales total and invoice volume' },
        { query: 'this month profit', note: 'Net profit calculation (Gross Revenue - Purchases - Expenses)' },
        { query: 'total collections', note: 'Total cash and bank payment receipts collected' },
        { query: 'gst report', note: 'Tax summary breakdown (Taxable value, CGST, SGST, Total tax)' }
      ],
      aliases: ['sales', 'revenue', 'turnover', 'profit', 'dhanda', 'bikri', 'vyapar', 'collections', 'gst report', 'daily sales', 'monthly sales'],
      supportedVariants: ['today sales', 'yesterday sales', 'this week sales', 'this month sales', 'this year revenue', 'this month profit'],
      relatedCapabilities: ['CAP_BI_EXPENSES', 'CAP_BI_COMPARISONS'],
      limitations: 'Calculates authoritative live metrics directly from indexed SQL transactions.'
    },
    {
      id: 'CAP_BI_EXPENSES',
      group: 'biz_reports',
      name: 'Business Expenses & Outflow Tracking',
      icon: '💸',
      shortDesc: 'Track operating expenses, category-wise spending splits, and fast expense logging.',
      quickExamples: ['today expenses', 'this month expenses', 'expense category breakdown'],
      detailedDesc: 'Inspects operating expense totals and category breakdowns (Rent, Utilities, Travel, Tea/Snacks, Supplies) across customizable date ranges.',
      allExamples: [
        { query: 'today expenses', note: 'Total expense outflow logged today with category breakdown' },
        { query: 'this month expenses', note: 'Monthly cumulative operating expenses & payment mode split' },
        { query: 'expense category breakdown', note: 'Category-wise expense breakdown (Rent, Utilities, Travel, Tea)' },
        { query: 'kharcha 120 chai nashta', note: 'Instantly logs ₹120 tea/snack expense without page switch' },
        { query: 'kharcha 500 petrol delivery', note: 'Instantly logs ₹500 travel/fuel expense to records' }
      ],
      aliases: ['expenses', 'expense', 'kharcha', 'kharch', 'spent', 'outflows', 'petty cash', 'tea expense', 'rent expense'],
      supportedVariants: ['today expenses', 'this month expenses', 'expense category breakdown', 'kharcha <amount> <category>'],
      relatedCapabilities: ['CAP_BI_SALES', 'CAP_ACTION_CREATE'],
      limitations: 'Expenses update live financial dashboards instantly.'
    },
    {
      id: 'CAP_BI_COMPARISONS',
      group: 'biz_reports',
      name: 'Period Comparisons (MoM & DoD)',
      icon: '📊',
      shortDesc: 'Compare sales turnover between consecutive periods with growth percentages and delta values.',
      quickExamples: ['sales this month vs last month', 'sales today vs yesterday'],
      detailedDesc: 'Executes side-by-side comparative analysis: Month-over-Month (MoM) and Day-over-Day (DoD) sales volumes with percentage growth or contraction.',
      allExamples: [
        { query: 'sales this month vs last month', note: 'Month-over-Month sales total, delta, and growth percentage' },
        { query: 'sales today vs yesterday', note: 'Day-over-Day revenue comparison with volume change' }
      ],
      aliases: ['comparison', 'vs', 'growth', 'sales comparison', 'mom', 'dod', 'this month vs last month', 'today vs yesterday'],
      supportedVariants: ['sales this month vs last month', 'sales today vs yesterday'],
      relatedCapabilities: ['CAP_BI_SALES', 'CAP_BI_RANKINGS'],
      limitations: 'Compares full matching date windows.'
    },
    {
      id: 'CAP_BI_RANKINGS',
      group: 'biz_reports',
      name: 'Top & Bottom N Rankings',
      icon: '🏆',
      shortDesc: 'Rank top/bottom customers by revenue, top selling items by volume, and largest debtors.',
      quickExamples: ['top 5 customers by revenue', 'top 3 products by volume', 'bottom 5 selling items'],
      detailedDesc: 'Generates ranked leaderboards based on sales turnover, unit volume, or outstanding balance.',
      allExamples: [
        { query: 'top 5 customers by revenue', note: 'Ranked list of highest revenue client accounts' },
        { query: 'top 3 products by volume', note: 'Highest velocity inventory items ranked by sales volume' },
        { query: 'bottom 5 selling items', note: 'Lowest velocity catalog items for clearance review' },
        { query: 'top debtors', note: 'Ranked list of top accounts with highest outstanding market dues' },
        { query: 'top creditors', note: 'Ranked list of suppliers owed highest pending payouts' }
      ],
      aliases: ['top', 'bottom', 'rankings', 'top customers', 'top products', 'top debtors', 'top creditors', 'leaderboard', 'best selling'],
      supportedVariants: ['top <N> customers by revenue', 'top <N> products by volume', 'bottom <N> selling items', 'top debtors', 'top creditors'],
      relatedCapabilities: ['CAP_BI_SALES', 'CAP_BI_RECEIVABLES'],
      limitations: 'Configurable ranking size from 1 to 50 items.'
    },
    {
      id: 'CAP_BI_THRESHOLDS',
      group: 'biz_reports',
      name: 'Threshold & Range Filter Queries',
      icon: '🔍',
      shortDesc: 'Filter invoices, customer balances, or expenses above or below specific numerical thresholds.',
      quickExamples: ['invoices over 50000', 'customers owing more than 10000', 'expenses above 2000'],
      detailedDesc: 'Instant numerical range filtering across database records without opening manual filter dialogs.',
      allExamples: [
        { query: 'invoices over 50000', note: 'Filters all invoices with grand total >= ₹50,000' },
        { query: 'invoices under 5000', note: 'Filters small ticket bills <= ₹5,000' },
        { query: 'customers owing more than 10000', note: 'Filters accounts with receivables >= ₹10,000' },
        { query: 'expenses above 2000', note: 'Filters high-value expense vouchers >= ₹2,000' }
      ],
      aliases: ['threshold', 'filter', 'above', 'below', 'over', 'under', 'more than', 'less than', 'invoices over', 'expenses above'],
      supportedVariants: ['invoices over <Amount>', 'invoices under <Amount>', 'customers owing more than <Amount>', 'expenses above <Amount>'],
      relatedCapabilities: ['CAP_BI_SALES', 'CAP_BI_RECEIVABLES'],
      limitations: 'Understands Indian numbers (e.g. "invoices over 1 lakh").'
    },
    {
      id: 'CAP_BI_AGING',
      group: 'biz_reports',
      name: 'Accounts Receivable Aging Analysis',
      icon: '⏳',
      shortDesc: 'Bucketed aging report of outstanding market receivables (0-30, 31-60, 61-90, 90+ days).',
      quickExamples: ['aging summary', 'aging report', 'overdue invoices'],
      detailedDesc: 'Categorizes all unpaid customer bills by overdue age brackets to assist recovery and working capital management.',
      allExamples: [
        { query: 'aging summary', note: 'Bucketed analysis of receivables (0-30d, 31-60d, 61-90d, 90+d)' },
        { query: 'aging report', note: 'Full overdue accounts receivable aging breakdown' },
        { query: 'overdue invoices', note: 'Filter all overdue customer bills past credit period' }
      ],
      aliases: ['aging', 'aging report', 'aging summary', 'overdue', 'ageing', 'receivables aging', 'bucket analysis'],
      supportedVariants: ['aging summary', 'aging report', 'overdue invoices'],
      relatedCapabilities: ['CAP_BI_RECEIVABLES', 'CAP_BI_DOCUMENTS'],
      limitations: 'Calculates overdue days based on individual invoice payment terms.'
    },

    // ─────────────────────────────────────────────────────────────────────────
    // 5. CUSTOMERS, PRODUCTS & STAFF
    // ─────────────────────────────────────────────────────────────────────────
    {
      id: 'CAP_BI_RECEIVABLES',
      group: 'cust_prod_staff',
      name: 'Customer Receivables, Khata & Dues',
      icon: '💰',
      shortDesc: 'Authoritative market udhari, customer ledger statements, balances, and payment recovery.',
      quickExamples: ['total udhari', 'Rahul Patil udhari', 'statement of Rahul Patil'],
      detailedDesc: 'Queries live customer accounts receivable ledger: Total market dues, individual customer balance, transaction statements, and WhatsApp payment reminders.',
      allExamples: [
        { query: 'total udhari', note: 'Authoritative accounts receivable total dues across all customers' },
        { query: 'Rahul Patil udhari', note: 'Realtime unpaid invoice balance for customer' },
        { query: 'balance of Gauri Shinde', note: 'Live customer ledger balance & payment status' },
        { query: 'statement of Rahul Patil', note: 'Full transaction history, bills & payment records' },
        { query: 'who owes money', note: 'List of all customer accounts with outstanding market dues' }
      ],
      aliases: ['udhari', 'dues', 'receivables', 'khata', 'balance', 'statement', 'shillak', 'baki', 'khata book', 'customer ledger'],
      supportedVariants: ['total udhari', '<Customer> udhari', 'balance of <Customer>', 'statement of <Customer>', 'who owes money'],
      relatedCapabilities: ['CAP_BI_AGING', 'CAP_BI_PAYABLES'],
      limitations: 'Calculates real-time net balances factoring credit notes and receipts.'
    },
    {
      id: 'CAP_BI_PAYABLES',
      group: 'cust_prod_staff',
      name: 'Vendor Payables & Supplier Dues',
      icon: '🏢',
      shortDesc: 'Track outstanding purchase bills, supplier dues, and vendor payment settlement history.',
      quickExamples: ['total payables', 'payable to ABC Steel', 'vendor payouts'],
      detailedDesc: 'Inspects accounts payable owed across all suppliers and vendors with purchase order settlement logs.',
      allExamples: [
        { query: 'total payables', note: 'Total accounts payable balance owed across all suppliers & vendors' },
        { query: 'payable to ABC Steel Traders', note: 'Outstanding purchase balance owed to vendor' },
        { query: 'vendor payouts', note: 'Vendor payment payout history and settled supplier invoices' },
        { query: 'purchase orders', note: 'Vendor purchase orders list, pending deliveries and amounts' }
      ],
      aliases: ['payables', 'vendor dues', 'supplier dues', 'payable to', 'vendor payouts', 'purchase orders', 'supplier balance'],
      supportedVariants: ['total payables', 'payable to <Vendor>', 'vendor payouts', 'purchase orders'],
      relatedCapabilities: ['CAP_BI_RECEIVABLES', 'CAP_BI_DOCUMENTS'],
      limitations: 'Factored from purchase bills, debit notes, and payment vouchers.'
    },
    {
      id: 'CAP_BI_INVENTORY',
      group: 'cust_prod_staff',
      name: 'Warehouse Inventory & Live Stock',
      icon: '📦',
      shortDesc: 'Inspect product stock quantities, low stock shortage alerts, and warehouse asset valuation.',
      quickExamples: ['stock of UltraTech Cement', 'low stock', 'inventory valuation'],
      detailedDesc: 'Real-time warehouse inventory quantities, critical shortage alerts below reorder levels, stock movements, and total asset valuation (Cost & Selling Price).',
      allExamples: [
        { query: 'stock of UltraTech Cement 50kg', note: 'Live warehouse inventory quantity & valuation of item' },
        { query: 'low stock', note: 'Critical inventory shortage alert below minimum stock levels' },
        { query: 'stock movements', note: 'Recent inventory inward / outward movements and adjustments' },
        { query: 'top selling products', note: 'Highest velocity inventory items ranked by sales volume' },
        { query: 'inventory valuation', note: 'Total warehouse asset value (Cost price & Selling price)' }
      ],
      aliases: ['stock', 'inventory', 'warehouse', 'low stock', 'reorder', 'stock movements', 'valuation', 'asset value', 'item stock'],
      supportedVariants: ['stock of <Product>', 'low stock', 'stock movements', 'top selling products', 'inventory valuation'],
      relatedCapabilities: ['CAP_SEARCH_ENTITIES', 'CAP_BI_SALES'],
      limitations: 'Integrates real-time inventory decrements from invoices and returns.'
    },
    {
      id: 'CAP_BI_DOCUMENTS',
      group: 'cust_prod_staff',
      name: 'Invoices, Quotations & Sales Returns',
      icon: '🧾',
      shortDesc: 'Inspect bill details, line items, unpaid invoice lists, estimates, credit notes, and receipts.',
      quickExamples: ['invoice INV-1001', 'invoice list', 'unpaid invoices'],
      detailedDesc: 'Lookup and filter billing documents: Tax Invoices, Estimates / Quotations, Sales Return Credit Notes, and Payment Receipts.',
      allExamples: [
        { query: 'invoice INV-1001', note: 'Instant breakdown of line items, taxes, payments & balance' },
        { query: 'invoice list', note: 'List recent billing invoices with payment status and totals' },
        { query: 'unpaid invoices', note: 'Filter all overdue and unpaid customer bills' },
        { query: 'estimate list', note: 'List recent quotations and estimates issued to clients' },
        { query: 'sales returns', note: 'Sales return credit notes and refund amounts summary' }
      ],
      aliases: ['invoice', 'invoices', 'bill', 'quotation', 'estimate', 'sales return', 'credit note', 'pavti', 'kaccha bill', 'andaj patrak'],
      supportedVariants: ['invoice <Number>', 'invoice list', 'unpaid invoices', 'estimate list', 'sales returns'],
      relatedCapabilities: ['CAP_SEARCH_ENTITIES', 'CAP_BI_SALES'],
      limitations: 'Displays full printable invoice previews on click.'
    },
    {
      id: 'CAP_BI_STAFF',
      group: 'cust_prod_staff',
      name: 'Staff Payroll, Attendance & Advances',
      icon: '👥',
      shortDesc: 'Manage employee monthly salaries, daily attendance logs, and staff salary advance loans.',
      quickExamples: ['salary of Sachin', 'salaries summary', 'attendance Sachin present today'],
      detailedDesc: 'Comprehensive HR desk: Employee salary structures, net payable payroll, one-click attendance marking, and advance loan repayment tracking.',
      allExamples: [
        { query: 'salary of Sachin Kamble', note: 'Salary structure, advances & net payable for staff' },
        { query: 'salaries summary', note: 'Firm-wide salary ledger, total monthly payroll & pending payouts' },
        { query: 'attendance of Sachin Kamble', note: 'Monthly attendance log & present days for staff' },
        { query: 'attendance Sachin present today', note: 'Marks attendance for employee with one click' },
        { query: 'advance of Sachin Kamble', note: 'Current advance balance and repayment ledger for staff' },
        { query: 'advance Sachin 2000', note: 'Records salary advance of ₹2,000 for employee' },
        { query: 'staff advances', note: 'Firm-wide employee advance balances and outstanding loans' }
      ],
      aliases: ['salary', 'salaries', 'staff', 'hr', 'payroll', 'attendance', 'advance', 'hazri', 'tankha', 'karmachari', 'shikshak', 'vetan suchi'],
      supportedVariants: ['salary of <Staff>', 'salaries summary', 'attendance of <Staff>', 'attendance <Staff> present today', 'advance of <Staff>', 'advance <Staff> <Amount>', 'staff advances'],
      relatedCapabilities: ['CAP_ACTION_CREATE', 'CAP_BI_EXPENSES'],
      limitations: 'Calculates salary net payable = Base Salary - Unpaid Leaves - Deducted Advances.'
    },
    {
      id: 'CAP_BI_FIRM',
      group: 'cust_prod_staff',
      name: 'Company Bank, UPI & Tax Profile',
      icon: '🏛️',
      shortDesc: 'Quickly display company bank account numbers, IFSC codes, UPI IDs, and GSTIN registration.',
      quickExamples: ['bank details', 'gstin', 'business summary'],
      detailedDesc: 'Instant lookup for registered business profile data to share with clients or verify official tax details.',
      allExamples: [
        { query: 'bank details', note: 'Registered company bank account, IFSC code, UPI ID & GSTIN' },
        { query: 'gstin', note: 'Official registered GST Identification Number' },
        { query: 'business summary', note: 'Executive macro summary of firm assets, receivables, and profits' }
      ],
      aliases: ['bank', 'bank details', 'ifsc', 'account number', 'gstin', 'gst number', 'tax id', 'company info', 'firm profile'],
      supportedVariants: ['bank details', 'gstin', 'business summary'],
      relatedCapabilities: ['CAP_MATH_GST_EXCL', 'CAP_BI_SALES'],
      limitations: 'Pulls directly from active Firm Configuration.'
    },

    // ─────────────────────────────────────────────────────────────────────────
    // 6. LANGUAGE & SMART QUERIES
    // ─────────────────────────────────────────────────────────────────────────
    {
      id: 'CAP_LANG_VERNACULAR',
      group: 'smart_lang',
      name: 'Multilingual & Vernacular Normalization',
      icon: '🌐',
      shortDesc: 'Full support for Hindi, Marathi, Hinglish, Marathish, and Devanagari numerals (०-९).',
      quickExamples: ['5000 ka 10 percent', '5000 मध्ये 10% वाढ', 'aaj ka dhanda'],
      detailedDesc: '100% offline normalizer translates Devanagari digits (०, १, २, ३, ४, ५, ६, ७, ८, ९), vernacular mathematical verbs (वाढ, कमी, kam karo, add karo), and regional business terms into canonical intents.',
      allExamples: [
        { query: '5000 ka 10 percent', note: 'Hindi percentage calculation (= ₹500)' },
        { query: '5000 mein 10% add karo', note: 'Hinglish percentage increase / markup (= ₹5,500)' },
        { query: '5000 se 10% kam karo', note: 'Hinglish percentage discount / deduction (= ₹4,500)' },
        { query: '5000 मध्ये 10% वाढ', note: 'Marathi percentage increase (= ₹5,500)' },
        { query: '5000 madhun 10% kami', note: 'Marathi percentage decrease (= ₹4,500)' },
        { query: 'karmachari tankha', note: 'Hindi resolution for employee payroll & salaries summary' },
        { query: 'vetan suchi', note: 'Marathi resolution for employee payroll ledger' },
        { query: 'mal wapas', note: 'Hindi resolution for sales return credit notes' },
        { query: 'aaj ka dhanda', note: 'Hindi resolution for today live sales & collection' },
        { query: 'aajchi bikri', note: 'Marathi resolution for today sales turnover' },
        { query: 'jama rakkam', note: 'Hindi resolution for payment receipts & collections' },
        { query: 'khata book Rahul Patil', note: 'Hinglish resolution for customer ledger statement' }
      ],
      aliases: ['hindi', 'marathi', 'hinglish', 'marathish', 'devanagari', 'vernacular', 'khata book', 'dhanda', 'bikri', 'tankha', 'hazri', 'kharcha', 'mal wapas', 'jama rakkam'],
      supportedVariants: ['<Devanagari Digits>', '<Amount> ka <Rate> percent', '<Amount> mein <Rate>% add karo', '<Amount> मध्ये <Rate>% वाढ', '<Amount> madhun <Rate>% kami'],
      relatedCapabilities: ['CAP_MATH_INDIAN_WORDS', 'CAP_BI_SALES', 'CAP_BI_RECEIVABLES'],
      limitations: 'Translates all regional variations deterministically in memory.'
    },
    {
      id: 'CAP_SMART_MULTI_INTENT',
      group: 'smart_lang',
      name: 'Compound Multi-Intent Query Processing',
      icon: '🔗',
      shortDesc: 'Execute multiple queries or actions combined with conjunctions (and, then, aur, ani, ;).',
      quickExamples: ['Ramesh balance and 1500 + 500 then /invoices', 'today sales and today expenses'],
      detailedDesc: 'The QuerySegmenter splits compound queries across conjunction boundaries and executes each segment in sequence while propagating results.',
      allExamples: [
        { query: 'Rahul Patil balance and 1500 + 500 then /invoices', note: 'Segments: (1) Check customer balance ➔ (2) Calculate math ➔ (3) Jump to Invoices' },
        { query: 'today sales and today expenses', note: 'Simultaneously reviews daily revenue and operating expenses' },
        { query: 'stock of UltraTech Cement and /po', note: 'Checks inventory and opens Purchase Order creator' }
      ],
      aliases: ['multi-intent', 'compound query', 'and', 'then', 'aur', 'ani', 'chained query', 'multiple actions'],
      supportedVariants: ['<Query 1> and <Query 2>', '<Query 1> then <Query 2>', '<Query 1> aur <Query 2>', '<Query 1> ani <Query 2>', '<Query 1> ; <Query 2>'],
      relatedCapabilities: ['CAP_SMART_SESSION', 'CAP_NAV_SLASH'],
      limitations: 'Executes up to 5 chained intents with unified composite preview.'
    },
    {
      id: 'CAP_SMART_SESSION',
      group: 'smart_lang',
      name: 'Session Context, "ans" Chaining & Anaphora',
      icon: '🧠',
      shortDesc: 'Continue math calculations with "ans" and resolve pronouns like "unka udhari" or "his phone number".',
      quickExamples: ['1500 + 3500 (stores ans) -> ans + 250', 'Tata Motors balance -> unka udhari'],
      detailedDesc: 'Multi-tenant isolated in-memory session manager remembers the last calculation output as "ans" and the last inspected customer/entity for pronoun continuity.',
      allExamples: [
        { query: '1500 + 3500 (yields 5000) ➔ ans + 250', note: 'Chained calculation (= 5,250)' },
        { query: '18% gst on ans', note: 'Calculates tax on previous result stored in ans' },
        { query: 'Tata Motors balance ➔ his phone number', note: 'Resolves phone number for Tata Motors from session memory' },
        { query: 'Rahul Patil ➔ unka udhari', note: 'Resolves udhari for Rahul Patil using pronoun anaphora' }
      ],
      aliases: ['ans', 'session', 'continuity', 'memory', 'context', 'pronoun', 'his', 'her', 'unka', 'tyanche', 'chaining'],
      supportedVariants: ['ans + <N>', '<Rate>% gst on ans', 'his phone number', 'unka udhari', 'unka balance', 'tyancha khata'],
      relatedCapabilities: ['CAP_MATH_ARITH', 'CAP_SMART_MULTI_INTENT'],
      limitations: 'Context is strictly isolated by firm ID to guarantee zero multi-tenant data leakage.'
    }
  ];

  // Helper functions
  const OmnisearchRegistryHelper = {
    getGroups() {
      return CAPABILITY_GROUPS;
    },

    getAllCapabilities() {
      return CAPABILITIES;
    },

    getCapabilityById(id) {
      return CAPABILITIES.find(c => c.id === id);
    },

    // Shuffled pool of representative queries across ALL capabilities
    getAllSampleQueries() {
      const queries = [];
      CAPABILITIES.forEach(cap => {
        if (cap.quickExamples) {
          cap.quickExamples.forEach(ex => {
            if (!queries.includes(ex)) queries.push(ex);
          });
        }
        if (cap.allExamples) {
          cap.allExamples.forEach(ex => {
            if (!queries.includes(ex.query)) queries.push(ex.query);
          });
        }
      });
      return queries;
    },

    // Completely randomize array order (Fisher-Yates)
    getRandomizedQueries(limit = 10) {
      const all = this.getAllSampleQueries();
      const shuffled = [...all];
      for (let i = shuffled.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
      }
      return limit ? shuffled.slice(0, limit) : shuffled;
    },

    // Exhaustive Deep Search across ALL fields
    searchCapabilities(searchText, category = 'all') {
      const text = (searchText || '').trim().toLowerCase();
      return CAPABILITIES.filter(cap => {
        // 1. Category Filter
        if (category && category !== 'all' && cap.group !== category) {
          return false;
        }

        if (!text) return true;

        // 2. Tokenized search terms
        const tokens = text.split(/\s+/).filter(Boolean);

        // Build searchable corpus for this capability
        const corpusParts = [
          cap.id,
          cap.name,
          cap.shortDesc,
          cap.detailedDesc || '',
          ...(cap.quickExamples || []),
          ...(cap.allExamples || []).map(e => `${e.query} ${e.note || ''}`),
          ...(cap.aliases || []),
          ...(cap.supportedVariants || []),
          ...(cap.supportedUnits || []),
          ...(cap.supportedCurrencies || []),
          cap.limitations || ''
        ];
        const corpus = corpusParts.join(' ').toLowerCase();

        // Must match all tokens
        return tokens.every(tok => corpus.includes(tok));
      });
    }
  };

  return {
    CAPABILITY_GROUPS,
    CAPABILITIES,
    ...OmnisearchRegistryHelper
  };
}));
