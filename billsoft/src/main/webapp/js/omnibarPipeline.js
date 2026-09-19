/**
 * omnibarPipeline.js
 * Universal Offline Deterministic Semantic Reasoning & CRM Spotlight Pipeline
 * 
 * Architecture:
 * - 100% Deterministic, Rule-Based, Client-Side (Zero External LLMs / Generative AI)
 * - Single-Query Execution: User Query -> Normalize -> Capability Resolution -> Extraction -> Execution -> Result
 * - 3 Presentation Categories: ACTION (⚡), QUICK_HELP (💡), SPECIAL (✨ / 🧮)
 * - Robust User Mistake Tolerance: Case, Punctuation, Whitespace, High-Confidence Typos, Vernacular Trade Synonyms
 */

(function (root, factory) {
  if (typeof define === 'function' && define.amd) {
    define([], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  } else {
    const exports = factory();
    root.OmnibarPipeline = exports;
    root.BillsoftOmnibarPipeline = exports;
    if (root.BillsoftUtils) {
      root.BillsoftUtils.omnibarPipeline = exports;
    }
  }
}(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  // ─────────────────────────────────────────────────────────────────────────────
  // 1. DETERMINISTIC NLP NORMALIZATION & TYPO TOLERANCE
  // ─────────────────────────────────────────────────────────────────────────────
  const DeterministicNormalizer = {
    // High-confidence typos dictionary (Never guess ambiguous tokens)
    typoDict: {
      'custmer': 'customer', 'custmor': 'customer', 'custome': 'customer', 'grahk': 'grahak',
      'invoce': 'invoice', 'invoic': 'invoice', 'invice': 'invoice', 'invoi': 'invoice', 'bil': 'bill',
      'outstading': 'outstanding', 'outstandng': 'outstanding', 'outstandin': 'outstanding', 'udhar': 'udhari',
      'expence': 'expense', 'expenc': 'expense', 'expns': 'expense', 'kharch': 'kharcha', 'kharchaa': 'kharcha',
      'gol': 'goal', 'gaol': 'goal', 'gool': 'goal', 'lakshy': 'lakshya', 'dhyey': 'dhyeya', 'dhey': 'dhyeya', 'strek': 'streak', 'strak': 'streak', 'hbit': 'habit', 'habbit': 'habit',
      'savng': 'saving', 'savngs': 'savings', 'savnges': 'savings', 'bchat': 'bachat', 'bachatt': 'bachat', 'invst': 'invest', 'invstmnt': 'investment', 'deposite': 'deposit', 'resrv': 'reserve', 'resrve': 'reserve',
      'todoo': 'todo', 'todu': 'todo', 'notte': 'note', 'stiky': 'sticky', 'plannr': 'planner', 'planer': 'planner',
      'dashbord': 'dashboard', 'dshboard': 'dashboard', 'persnal': 'personal', 'personel': 'personal',
      'bckup': 'backup', 'bakup': 'backup', 'backp': 'backup', 'bakcup': 'backup', 'restor': 'restore', 'restre': 'restore',
      'remindr': 'reminder', 'remider': 'reminder', 'rmind': 'remind', 'remnd': 'remind',
      'septeber': 'september', 'septmber': 'september', 'setember': 'september', 'septembr': 'september',
      'febuary': 'february', 'febrary': 'february', 'feburary': 'february',
      'janury': 'january', 'januray': 'january', 'jannuary': 'january',
      'aprl': 'april', 'aprail': 'april', 'augst': 'august', 'agust': 'august',
      'octomber': 'october', 'octber': 'october', 'novmber': 'november', 'novemer': 'november',
      'decmber': 'december', 'descember': 'december',
      'tommorow': 'tomorrow', 'tomorow': 'tomorrow', 'tommorrow': 'tomorrow', 'tmrw': 'tomorrow', 'tmr': 'tomorrow',
      'yesturday': 'yesterday', 'yestarday': 'yesterday', 'yesteday': 'yesterday', 'toady': 'today', 'tday': 'today',
      'atendance': 'attendance', 'attandance': 'attendance',
      'advanc': 'advance', 'advanse': 'advance', 'salry': 'salary', 'statment': 'statement', 'statemnt': 'statement',
      'quotatn': 'quotation', 'estimat': 'estimate', 'produc': 'product', 'prdct': 'product',
      'inventry': 'inventory', 'inventroy': 'inventory', 'suppler': 'supplier', 'suplier': 'supplier',
      'reciept': 'receipt', 'recipt': 'receipt', 'discunt': 'discount', 'discnt': 'discount',
      'chng': 'change', 'chutta': 'change', 'waps': 'wapas', 'mny': 'money', 'pymnt': 'payment',
      'percnt': 'percent', 'persent': 'percent', 'prcent': 'percent', 'parcent': 'percent', 'pratishat': 'percent', 'takke': 'percent',
      'multipy': 'multiply', 'multyply': 'multiply', 'multply': 'multiply', 'multiplied': 'multiply',
      'divde': 'divide', 'devide': 'divide', 'divded': 'divide',
      'addd': 'add', 'addded': 'add',
      'substact': 'subtract', 'substract': 'subtract', 'subtarct': 'subtract', 'subtracted': 'subtract',
      'calculte': 'calculate', 'claculate': 'calculate', 'calcualte': 'calculate',
      'squar': 'square', 'sqr': 'square', 'squareroot': 'sqrt'
    },

    // Decouples glued numbers, currencies, percentages, and units
    splitGluedTokens(str) {
      if (!str || typeof str !== 'string') return '';
      let s = str.trim();
      // Number + % / percent + of / prep: "17%of5633" -> "17% of 5633", "17percentof" -> "17 percent of"
      s = s.replace(/(\d+(?:\.\d+)?)\s*(%|percent)\s*(of|ka|ki|ke|cha|chi|che|on|to|from|in|mein|se)\b/gi, '$1 $2 $3');
      // Standalone glued number + percent: "17percent" -> "17 percent"
      s = s.replace(/(\d+(?:\.\d+)?)\s*(percent)\b/gi, '$1 percent');
      // Number + % + Word: "18%gst" -> "18% gst"
      s = s.replace(/(\d+(?:\.\d+)?)\s*(%)\s*(gst|tax|vat|discount|margin|interest)\b/gi, '$1$2 $3');
      // Number + Unit/Word/Currency
      s = s.replace(/(\d+(?:\.\d+)?)\s*(mm|cm|m|mtr|meter|meters|km|in|inch|inches|ft|feet|foot|yd|yard|yards|sqft|sqm|sqyd|sqkm|sqmi|guntha|bigha|acre|hectare|brass|g|gram|grams|gm|gms|kg|kgs|kilo|kilos|quintal|qtl|tonne|ton|t|tola|carat|ml|l|ltr|liter|liters|gal|gallon|floz|cup|pint|quart|usd|eur|gbp|aed|cad|aud|nzd|sgd|hkd|sar|qar|kwd|bhd|omr|myr|thb|idr|php|krw|zar|rub|try|brl|mxn|sek|nok|dkk|pln|czk|huf|ils|egp|ngn|bdt|pkr|lkr|npr|vnd|btc|eth|usdt|inr|rs|rupees|rupee|upi|k|lakh|lakhs|lac|lacs|cr|crore|crores|hazar|hazaar|haz|c|f|kelvin|celsius|fahrenheit|kmh|kph|mph|mps|knot|knots|kb|kib|mb|mib|gb|gib|tb|tib|pb|bytes|sec|second|seconds|min|minute|minutes|hr|hrs|hour|hours|day|days|week|weeks|month|months|year|years)\b/gi, '$1 $2');
      // Currency Symbol + Number: "rs500" -> "rs 500", "$100" -> "$ 100", "₹1200" -> "₹ 1200"
      s = s.replace(/([₹$€£¥₩₽₺฿₱₪₦৳₫]|rs\.?|inr)\s*(\d+(?:\.\d+)?)/gi, '$1 $2');
      return s.replace(/\s+/g, ' ').trim();
    },

    // Clean conversational filler words
    cleanFillers(str) {
      if (!str) return '';
      const fillers = [
        'what are the', 'what is the', 'what is', 'what are', 'whats', 'what', 'which is',
        'is the', 'are the', 'the', 'tell me', 'i want to know', 'mujhe janna hai',
        'please', 'plz', 'pls', 'can you', 'could you', 'help me', 'show me', 'give me', 'find me',
        'kripya', 'zara', 'batao', 'dikhao', 'dakhva', 'sang', 'sanga', 'karo', 'kara', 'de do',
        'karna hai', 'kar do', 'ahe', 'aahe', 'hai', 'tha', 'thi', 'the', 'banao', 'banva',
        'bataiye', 'dakhva mala', 'show', 'display'
      ];
      const fillerRegex = new RegExp(`\\b(${fillers.join('|')})\\b`, 'gi');
      return str.replace(fillerRegex, ' ').replace(/\s+/g, ' ').trim();
    },

    // Apply high-confidence typo corrections
    correctTypos(str) {
      if (!str) return '';
      const words = str.split(/\s+/);
      const corrected = words.map(w => {
        const clean = w.toLowerCase().replace(/[^a-z0-9]/g, '');
        return this.typoDict[clean] || w;
      });
      return corrected.join(' ');
    },

    // Transliterates Devanagari numerals and maps vernacular/Devanagari business keywords to canonical concepts
    transliterateVernacular(str) {
      if (!str) return '';
      let s = str;
      // 1. Devanagari Numerals -> Arabic Digits
      const devanagariDigits = {
        '०': '0', '१': '1', '२': '2', '३': '3', '४': '4',
        '५': '5', '६': '6', '७': '7', '८': '8', '९': '9'
      };
      s = s.replace(/[०-९]/g, d => devanagariDigits[d] || d);

      // 2. Devanagari & Marathi/Hindi Canonical Words Mapping
      const devDict = [
        // Arithmetic & Math
        [/(?:^|\s|[0-9])(?:बेरीज|जोड़|अधिक)(?:\s|$|[0-9])/gi, ' + '],
        [/(?:^|\s|[0-9])(?:वजा|घटाओ|मायनस)(?:\s|$|[0-9])/gi, ' - '],
        [/(?:^|\s|[0-9])(?:गुणा|गुणिले|गुणाकार)(?:\s|$|[0-9])/gi, ' * '],
        [/(?:^|\s|[0-9])(?:भाग|भागाकार|भागिले)(?:\s|$|[0-9])/gi, ' / '],
        [/(?:^|\s|[0-9])(?:टक्के|प्रतिशत)(?:\s|$|[0-9])/gi, ' % '],

        // Number words
        [/(?:^|\s)(?:शंभर|सौ)(?=\s|$)/gi, ' 100 '],
        [/(?:^|\s)(?:हजार|हज़ार)(?=\s|$)/gi, ' 1000 '],
        [/(?:^|\s)(?:लाख|लाखा)(?=\s|$)/gi, ' lakh '],
        [/(?:^|\s)(?:करोड|कोटी)(?=\s|$)/gi, ' crore '],
        [/(?:^|\s)(?:दीड|डेढ)(?=\s|$)/gi, ' dedh '],
        [/(?:^|\s)(?:अडीच|ढाई)(?=\s|$)/gi, ' dhai '],
        [/(?:^|\s)(?:सव्वा|सवा)(?=\s|$)/gi, ' sawa '],
        [/(?:^|\s)(?:पावणे|पौने)(?=\s|$)/gi, ' paune '],
        [/(?:^|\s)(?:अर्धा|आधा)(?=\s|$)/gi, ' aadha '],

        // Units
        [/(?:^|\s|[0-9])(?:किलो|किग्रॅ)(?=\s|$)/gi, ' kg '],
        [/(?:^|\s|[0-9])(?:ग्राम|ग्रॅम)(?=\s|$)/gi, ' g '],
        [/(?:^|\s|[0-9])(?:मीटर|मिटर)(?=\s|$)/gi, ' meter '],
        [/(?:^|\s|[0-9])(?:फूट|फुट)(?=\s|$)/gi, ' feet '],
        [/(?:^|\s|[0-9])(?:लिटर|लीटर)(?=\s|$)/gi, ' liter '],
        [/(?:^|\s|[0-9])(?:तोल|तोळा)(?=\s|$)/gi, ' tola '],
        [/(?:^|\s|[0-9])(?:क्विंटल|क्विंटाल)(?=\s|$)/gi, ' quintal '],
        [/(?:^|\s|[0-9])(?:टन)(?=\s|$)/gi, ' ton '],
        [/(?:^|\s|[0-9])(?:गुंठा|गुंठे)(?=\s|$)/gi, ' guntha '],
        [/(?:^|\s|[0-9])(?:एकर)(?=\s|$)/gi, ' acre '],
        [/(?:^|\s|[0-9])(?:ब्रास)(?=\s|$)/gi, ' brass '],
        [/(?:^|\s|[0-9])(?:रुपये|रुपया|रु)(?=\s|$)/gi, ' rs '],

        // Dates & Periods
        [/(?:^|\s)(?:आज|आजची|आजचा|आजचे)(?=\s|$)/gi, ' today '],
        [/(?:^|\s)(?:कल\s*(?:का|की|के)|बीता\s*कल|काल|कालची|कालचा|कालचे)(?=\s|$)/gi, ' yesterday '],
        [/(?:^|\s)(?:आने\s*वाला\s*कल|उद्या)(?=\s|$)/gi, ' tomorrow '],
        [/(?:^|\s)(?:कल)(?=\s|$)/gi, ' yesterday '],
        [/(?:^|\s)(?:परवा|परसों)(?=\s|$)/gi, ' day after tomorrow '],
        [/(?:^|\s)(?:महिना|महिन्याचा|महिने)(?=\s|$)/gi, ' month '],
        [/(?:^|\s)(?:आठवडा|हफ्ता)(?=\s|$)/gi, ' week '],
        [/(?:^|\s)(?:वर्ष|साल)(?=\s|$)/gi, ' year '],

        // Business Terms
        [/(?:^|\s)(?:ग्राहक|गिर्हाईक|गिऱ्हाईक)(?=\s|$)/gi, ' customer '],
        [/(?:^|\s)(?:व्यापारी|विक्रेता|सप्लायर)(?=\s|$)/gi, ' party '],
        [/(?:^|\s)(?:पावती|बिल|चलन)(?=\s|$)/gi, ' invoice '],
        [/(?:^|\s)(?:अंदाज\s*पत्रक|कोटेशन)(?=\s|$)/gi, ' quotation '],
        [/(?:^|\s)(?:खर्च|खर्चा|व्यय)(?=\s|$)/gi, ' expense '],
        [/(?:^|\s)(?:पगार|वेतन|मानधन)(?=\s|$)/gi, ' salary '],
        [/(?:^|\s)(?:हजेरी|उपस्थिती|हाजरी)(?=\s|$)/gi, ' attendance '],
        [/(?:^|\s)(?:उधारी|बाकी|शिल्लक|येणे)(?=\s|$)/gi, ' outstanding '],
        [/(?:^|\s)(?:विक्री|सेल|खप)(?=\s|$)/gi, ' sales '],
        [/(?:^|\s)(?:नफा|फायदा|मुनाफा)(?=\s|$)/gi, ' profit '],
        [/(?:^|\s)(?:तोटा|नुकसान)(?=\s|$)/gi, ' loss '],
        [/(?:^|\s)(?:साठा|स्टॉक|माल)(?=\s|$)/gi, ' stock '],
        [/(?:^|\s)(?:सूट|सवलत|छूट)(?=\s|$)/gi, ' discount '],
        [/(?:^|\s)(?:व्याज|ब्याज)(?=\s|$)/gi, ' interest '],
        [/(?:^|\s)(?:रोकड|कॅश|नगद)(?=\s|$)/gi, ' cash ']
      ];

      for (const [pattern, replacement] of devDict) {
        s = s.replace(pattern, replacement);
      }
      return s;
    },

    // Full 9-stage normalizer
    normalize(raw) {
      const rawTrimmed = (raw || '').trim();
      // 1. Vernacular & Devanagari transliteration
      let transliterated = this.transliterateVernacular(rawTrimmed);
      // 2. Unicode & punctuation cleanup
      let cleanPunct = transliterated.replace(/['"`]/g, '').replace(/[?!;]+/g, ' ');
      // 3. Glued tokens decoupling
      let decoupled = this.splitGluedTokens(cleanPunct);
      // 4. Typo corrections
      let typoFixed = this.correctTypos(decoupled);
      // 5. Case normalization
      let lower = typoFixed.toLowerCase();
      // 6. Clean fillers
      let cleaned = this.cleanFillers(lower);

      return {
        raw: rawTrimmed,
        transliterated,
        decoupled,
        typoFixed,
        lower,
        cleaned: cleaned || lower
      };
    }
  };

  // ─────────────────────────────────────────────────────────────────────────────
  // 2. UNIT CONVERSION REGISTRY
  // ─────────────────────────────────────────────────────────────────────────────
  const UnitRates = {
    // Length (base = meter)
    'mm': { base: 'length', toBase: 0.001, name: 'Millimeter' },
    'millimeter': { base: 'length', toBase: 0.001, name: 'Millimeter' },
    'millimeters': { base: 'length', toBase: 0.001, name: 'Millimeters' },
    'cm': { base: 'length', toBase: 0.01, name: 'Centimeter' },
    'centimeter': { base: 'length', toBase: 0.01, name: 'Centimeter' },
    'centimeters': { base: 'length', toBase: 0.01, name: 'Centimeters' },
    'm': { base: 'length', toBase: 1, name: 'Meter' },
    'mtr': { base: 'length', toBase: 1, name: 'Meter' },
    'meter': { base: 'length', toBase: 1, name: 'Meter' },
    'meters': { base: 'length', toBase: 1, name: 'Meter' },
    'km': { base: 'length', toBase: 1000, name: 'Kilometer' },
    'kilometer': { base: 'length', toBase: 1000, name: 'Kilometer' },
    'kilometers': { base: 'length', toBase: 1000, name: 'Kilometers' },
    'in': { base: 'length', toBase: 0.0254, name: 'Inch' },
    'inch': { base: 'length', toBase: 0.0254, name: 'Inch' },
    'inches': { base: 'length', toBase: 0.0254, name: 'Inches' },
    'ft': { base: 'length', toBase: 0.3048, name: 'Foot' },
    'feet': { base: 'length', toBase: 0.3048, name: 'Feet' },
    'foot': { base: 'length', toBase: 0.3048, name: 'Foot' },
    'yd': { base: 'length', toBase: 0.9144, name: 'Yard' },
    'yard': { base: 'length', toBase: 0.9144, name: 'Yard' },
    'yards': { base: 'length', toBase: 0.9144, name: 'Yards' },
    'mile': { base: 'length', toBase: 1609.344, name: 'Mile' },
    'miles': { base: 'length', toBase: 1609.344, name: 'Miles' },
    'mi': { base: 'length', toBase: 1609.344, name: 'Mile' },
    'nm': { base: 'length', toBase: 1852, name: 'Nautical Mile' },
    'nmi': { base: 'length', toBase: 1852, name: 'Nautical Mile' },
    'gaj': { base: 'length', toBase: 0.9144, name: 'Gaj (Yard)' },

    // Area (base = sqft)
    'sqft': { base: 'area', toBase: 1, name: 'Square Feet' },
    'sqm': { base: 'area', toBase: 10.7639, name: 'Square Meter' },
    'sqyd': { base: 'area', toBase: 9, name: 'Square Yard' },
    'sqkm': { base: 'area', toBase: 10763910.4, name: 'Square Kilometer' },
    'sqmi': { base: 'area', toBase: 27878400, name: 'Square Mile' },
    'guntha': { base: 'area', toBase: 1089, name: 'Guntha' },
    'bigha': { base: 'area', toBase: 27225, name: 'Bigha' },
    'acre': { base: 'area', toBase: 43560, name: 'Acre' },
    'acres': { base: 'area', toBase: 43560, name: 'Acres' },
    'hectare': { base: 'area', toBase: 107639, name: 'Hectare' },
    'hectares': { base: 'area', toBase: 107639, name: 'Hectares' },
    'brass': { base: 'area', toBase: 100, name: 'Brass (100 sqft / 100 cuft)' },

    // Weight (base = gram)
    'mg': { base: 'weight', toBase: 0.001, name: 'Milligram' },
    'milligram': { base: 'weight', toBase: 0.001, name: 'Milligram' },
    'milligrams': { base: 'weight', toBase: 0.001, name: 'Milligrams' },
    'g': { base: 'weight', toBase: 1, name: 'Gram' },
    'gram': { base: 'weight', toBase: 1, name: 'Gram' },
    'grams': { base: 'weight', toBase: 1, name: 'Grams' },
    'gm': { base: 'weight', toBase: 1, name: 'Gram' },
    'gms': { base: 'weight', toBase: 1, name: 'Grams' },
    'kg': { base: 'weight', toBase: 1000, name: 'Kilogram' },
    'kgs': { base: 'weight', toBase: 1000, name: 'Kilograms' },
    'kilo': { base: 'weight', toBase: 1000, name: 'Kilogram' },
    'kilos': { base: 'weight', toBase: 1000, name: 'Kilograms' },
    'quintal': { base: 'weight', toBase: 100000, name: 'Quintal (100 kg)' },
    'qtl': { base: 'weight', toBase: 100000, name: 'Quintal' },
    'tonne': { base: 'weight', toBase: 1000000, name: 'Tonne (1000 kg)' },
    'ton': { base: 'weight', toBase: 1000000, name: 'Tonne' },
    'tons': { base: 'weight', toBase: 1000000, name: 'Tonnes' },
    't': { base: 'weight', toBase: 1000000, name: 'Tonne' },
    'tola': { base: 'weight', toBase: 11.6638, name: 'Tola' },
    'carat': { base: 'weight', toBase: 0.2, name: 'Carat' },
    'carats': { base: 'weight', toBase: 0.2, name: 'Carats' },
    'lb': { base: 'weight', toBase: 453.592, name: 'Pound' },
    'lbs': { base: 'weight', toBase: 453.592, name: 'Pounds' },
    'pound': { base: 'weight', toBase: 453.592, name: 'Pound' },
    'pounds': { base: 'weight', toBase: 453.592, name: 'Pounds' },
    'oz': { base: 'weight', toBase: 28.3495, name: 'Ounce' },
    'ounce': { base: 'weight', toBase: 28.3495, name: 'Ounce' },
    'ounces': { base: 'weight', toBase: 28.3495, name: 'Ounces' },
    'stone': { base: 'weight', toBase: 6350.29, name: 'Stone' },

    // Volume (base = liter)
    'ml': { base: 'volume', toBase: 0.001, name: 'Milliliter' },
    'milliliter': { base: 'volume', toBase: 0.001, name: 'Milliliter' },
    'milliliters': { base: 'volume', toBase: 0.001, name: 'Milliliters' },
    'l': { base: 'volume', toBase: 1, name: 'Liter' },
    'ltr': { base: 'volume', toBase: 1, name: 'Liter' },
    'liter': { base: 'volume', toBase: 1, name: 'Liter' },
    'liters': { base: 'volume', toBase: 1, name: 'Liters' },
    'gal': { base: 'volume', toBase: 3.78541, name: 'Gallon (US)' },
    'gallon': { base: 'volume', toBase: 3.78541, name: 'Gallon (US)' },
    'gallons': { base: 'volume', toBase: 3.78541, name: 'Gallons (US)' },
    'floz': { base: 'volume', toBase: 0.0295735, name: 'Fluid Ounce' },
    'cup': { base: 'volume', toBase: 0.236588, name: 'Cup' },
    'cups': { base: 'volume', toBase: 0.236588, name: 'Cups' },
    'pint': { base: 'volume', toBase: 0.473176, name: 'Pint' },
    'pints': { base: 'volume', toBase: 0.473176, name: 'Pints' },
    'quart': { base: 'volume', toBase: 0.946353, name: 'Quart' },
    'quarts': { base: 'volume', toBase: 0.946353, name: 'Quarts' },

    // Temperature (base = celsius) - Affine conversions
    'c': { base: 'temperature', toBase: x => x, fromBase: x => x, name: '°C (Celsius)' },
    'celsius': { base: 'temperature', toBase: x => x, fromBase: x => x, name: '°C (Celsius)' },
    'centigrade': { base: 'temperature', toBase: x => x, fromBase: x => x, name: '°C (Centigrade)' },
    'f': { base: 'temperature', toBase: x => (x - 32) * 5 / 9, fromBase: x => (x * 9 / 5) + 32, name: '°F (Fahrenheit)' },
    'fahrenheit': { base: 'temperature', toBase: x => (x - 32) * 5 / 9, fromBase: x => (x * 9 / 5) + 32, name: '°F (Fahrenheit)' },
    'k': { base: 'temperature', toBase: x => x - 273.15, fromBase: x => x + 273.15, name: 'K (Kelvin)' },
    'kelvin': { base: 'temperature', toBase: x => x - 273.15, fromBase: x => x + 273.15, name: 'K (Kelvin)' },

    // Speed (base = km/h)
    'kmh': { base: 'speed', toBase: 1, name: 'km/h' },
    'kph': { base: 'speed', toBase: 1, name: 'km/h' },
    'km/h': { base: 'speed', toBase: 1, name: 'km/h' },
    'mph': { base: 'speed', toBase: 1.609344, name: 'mph' },
    'mi/h': { base: 'speed', toBase: 1.609344, name: 'mph' },
    'mps': { base: 'speed', toBase: 3.6, name: 'm/s' },
    'm/s': { base: 'speed', toBase: 3.6, name: 'm/s' },
    'knot': { base: 'speed', toBase: 1.852, name: 'Knot' },
    'knots': { base: 'speed', toBase: 1.852, name: 'Knots' },
    'kt': { base: 'speed', toBase: 1.852, name: 'Knot' },
    'fps': { base: 'speed', toBase: 1.09728, name: 'ft/s' },
    'ft/s': { base: 'speed', toBase: 1.09728, name: 'ft/s' },

    // Data / Storage (base = byte)
    'b': { base: 'storage', toBase: 1, name: 'Bytes' },
    'byte': { base: 'storage', toBase: 1, name: 'Bytes' },
    'bytes': { base: 'storage', toBase: 1, name: 'Bytes' },
    'kb': { base: 'storage', toBase: 1024, name: 'KB' },
    'kib': { base: 'storage', toBase: 1024, name: 'KiB' },
    'kilobyte': { base: 'storage', toBase: 1024, name: 'Kilobytes' },
    'kilobytes': { base: 'storage', toBase: 1024, name: 'Kilobytes' },
    'mb': { base: 'storage', toBase: 1048576, name: 'MB' },
    'mib': { base: 'storage', toBase: 1048576, name: 'MiB' },
    'megabyte': { base: 'storage', toBase: 1048576, name: 'Megabytes' },
    'megabytes': { base: 'storage', toBase: 1048576, name: 'Megabytes' },
    'gb': { base: 'storage', toBase: 1073741824, name: 'GB' },
    'gib': { base: 'storage', toBase: 1073741824, name: 'GiB' },
    'gigabyte': { base: 'storage', toBase: 1073741824, name: 'Gigabytes' },
    'gigabytes': { base: 'storage', toBase: 1073741824, name: 'Gigabytes' },
    'tb': { base: 'storage', toBase: 1099511627776, name: 'TB' },
    'tib': { base: 'storage', toBase: 1099511627776, name: 'TiB' },
    'terabyte': { base: 'storage', toBase: 1099511627776, name: 'Terabytes' },
    'terabytes': { base: 'storage', toBase: 1099511627776, name: 'Terabytes' },
    'pb': { base: 'storage', toBase: 1125899906842624, name: 'PB' },
    'petabyte': { base: 'storage', toBase: 1125899906842624, name: 'Petabytes' },

    // Time (base = second)
    'ms': { base: 'time', toBase: 0.001, name: 'Millisecond' },
    'millisecond': { base: 'time', toBase: 0.001, name: 'Millisecond' },
    'milliseconds': { base: 'time', toBase: 0.001, name: 'Milliseconds' },
    's': { base: 'time', toBase: 1, name: 'Second' },
    'sec': { base: 'time', toBase: 1, name: 'Second' },
    'second': { base: 'time', toBase: 1, name: 'Second' },
    'seconds': { base: 'time', toBase: 1, name: 'Seconds' },
    'min': { base: 'time', toBase: 60, name: 'Minute' },
    'minute': { base: 'time', toBase: 60, name: 'Minute' },
    'minutes': { base: 'time', toBase: 60, name: 'Minutes' },
    'hr': { base: 'time', toBase: 3600, name: 'Hour' },
    'hrs': { base: 'time', toBase: 3600, name: 'Hours' },
    'hour': { base: 'time', toBase: 3600, name: 'Hour' },
    'hours': { base: 'time', toBase: 3600, name: 'Hours' },
    'd': { base: 'time', toBase: 86400, name: 'Day' },
    'day': { base: 'time', toBase: 86400, name: 'Day' },
    'days': { base: 'time', toBase: 86400, name: 'Days' },
    'wk': { base: 'time', toBase: 604800, name: 'Week' },
    'wks': { base: 'time', toBase: 604800, name: 'Weeks' },
    'week': { base: 'time', toBase: 604800, name: 'Week' },
    'weeks': { base: 'time', toBase: 604800, name: 'Weeks' },
    'mo': { base: 'time', toBase: 2592000, name: 'Month (30d)' },
    'month': { base: 'time', toBase: 2592000, name: 'Month' },
    'months': { base: 'time', toBase: 2592000, name: 'Months' },
    'yr': { base: 'time', toBase: 31536000, name: 'Year (365d)' },
    'yrs': { base: 'time', toBase: 31536000, name: 'Years' },
    'year': { base: 'time', toBase: 31536000, name: 'Year' },
    'years': { base: 'time', toBase: 31536000, name: 'Years' },

    // Currencies (base = INR, reference exchange rates as of September 2026)
    // INR
    'inr': { base: 'currency', toBase: 1, name: 'Indian Rupee', code: 'INR', symbol: '₹' },
    'rs': { base: 'currency', toBase: 1, name: 'Indian Rupee', code: 'INR', symbol: '₹' },
    'rupee': { base: 'currency', toBase: 1, name: 'Indian Rupee', code: 'INR', symbol: '₹' },
    'rupees': { base: 'currency', toBase: 1, name: 'Indian Rupee', code: 'INR', symbol: '₹' },
    'rupya': { base: 'currency', toBase: 1, name: 'Indian Rupee', code: 'INR', symbol: '₹' },
    'rupaye': { base: 'currency', toBase: 1, name: 'Indian Rupee', code: 'INR', symbol: '₹' },
    '₹': { base: 'currency', toBase: 1, name: 'Indian Rupee', code: 'INR', symbol: '₹' },

    // USD
    'usd': { base: 'currency', toBase: 86.50, name: 'US Dollar', code: 'USD', symbol: '$' },
    'dollar': { base: 'currency', toBase: 86.50, name: 'US Dollar', code: 'USD', symbol: '$' },
    'dollars': { base: 'currency', toBase: 86.50, name: 'US Dollar', code: 'USD', symbol: '$' },
    'bucks': { base: 'currency', toBase: 86.50, name: 'US Dollar', code: 'USD', symbol: '$' },
    'us dollar': { base: 'currency', toBase: 86.50, name: 'US Dollar', code: 'USD', symbol: '$' },
    'us dollars': { base: 'currency', toBase: 86.50, name: 'US Dollar', code: 'USD', symbol: '$' },
    '$': { base: 'currency', toBase: 86.50, name: 'US Dollar', code: 'USD', symbol: '$' },

    // EUR
    'eur': { base: 'currency', toBase: 91.20, name: 'Euro', code: 'EUR', symbol: '€' },
    'euro': { base: 'currency', toBase: 91.20, name: 'Euro', code: 'EUR', symbol: '€' },
    'euros': { base: 'currency', toBase: 91.20, name: 'Euro', code: 'EUR', symbol: '€' },
    '€': { base: 'currency', toBase: 91.20, name: 'Euro', code: 'EUR', symbol: '€' },

    // GBP
    'gbp': { base: 'currency', toBase: 109.80, name: 'British Pound', code: 'GBP', symbol: '£' },
    'pound': { base: 'currency', toBase: 109.80, name: 'British Pound', code: 'GBP', symbol: '£' },
    'pounds': { base: 'currency', toBase: 109.80, name: 'British Pound', code: 'GBP', symbol: '£' },
    'sterling': { base: 'currency', toBase: 109.80, name: 'British Pound', code: 'GBP', symbol: '£' },
    'british pound': { base: 'currency', toBase: 109.80, name: 'British Pound', code: 'GBP', symbol: '£' },
    'british pounds': { base: 'currency', toBase: 109.80, name: 'British Pound', code: 'GBP', symbol: '£' },
    '£': { base: 'currency', toBase: 109.80, name: 'British Pound', code: 'GBP', symbol: '£' },

    // JPY
    'jpy': { base: 'currency', toBase: 0.58, name: 'Japanese Yen', code: 'JPY', symbol: '¥' },
    'yen': { base: 'currency', toBase: 0.58, name: 'Japanese Yen', code: 'JPY', symbol: '¥' },
    'japanese yen': { base: 'currency', toBase: 0.58, name: 'Japanese Yen', code: 'JPY', symbol: '¥' },
    '¥': { base: 'currency', toBase: 0.58, name: 'Japanese Yen', code: 'JPY', symbol: '¥' },

    // CNY
    'cny': { base: 'currency', toBase: 11.90, name: 'Chinese Yuan', code: 'CNY', symbol: '¥' },
    'yuan': { base: 'currency', toBase: 11.90, name: 'Chinese Yuan', code: 'CNY', symbol: '¥' },
    'rmb': { base: 'currency', toBase: 11.90, name: 'Chinese Yuan (RMB)', code: 'CNY', symbol: '¥' },
    'renminbi': { base: 'currency', toBase: 11.90, name: 'Chinese Yuan (RMB)', code: 'CNY', symbol: '¥' },
    'chinese yuan': { base: 'currency', toBase: 11.90, name: 'Chinese Yuan', code: 'CNY', symbol: '¥' },

    // CHF
    'chf': { base: 'currency', toBase: 97.40, name: 'Swiss Franc', code: 'CHF', symbol: 'CHF' },
    'franc': { base: 'currency', toBase: 97.40, name: 'Swiss Franc', code: 'CHF', symbol: 'CHF' },
    'francs': { base: 'currency', toBase: 97.40, name: 'Swiss Franc', code: 'CHF', symbol: 'CHF' },
    'swiss franc': { base: 'currency', toBase: 97.40, name: 'Swiss Franc', code: 'CHF', symbol: 'CHF' },
    'swiss francs': { base: 'currency', toBase: 97.40, name: 'Swiss Franc', code: 'CHF', symbol: 'CHF' },

    // CAD
    'cad': { base: 'currency', toBase: 61.20, name: 'Canadian Dollar', code: 'CAD', symbol: 'C$' },
    'canadian dollar': { base: 'currency', toBase: 61.20, name: 'Canadian Dollar', code: 'CAD', symbol: 'C$' },
    'canadian dollars': { base: 'currency', toBase: 61.20, name: 'Canadian Dollar', code: 'CAD', symbol: 'C$' },

    // AUD
    'aud': { base: 'currency', toBase: 55.40, name: 'Australian Dollar', code: 'AUD', symbol: 'A$' },
    'australian dollar': { base: 'currency', toBase: 55.40, name: 'Australian Dollar', code: 'AUD', symbol: 'A$' },
    'australian dollars': { base: 'currency', toBase: 55.40, name: 'Australian Dollar', code: 'AUD', symbol: 'A$' },

    // NZD
    'nzd': { base: 'currency', toBase: 50.80, name: 'New Zealand Dollar', code: 'NZD', symbol: 'NZ$' },
    'new zealand dollar': { base: 'currency', toBase: 50.80, name: 'New Zealand Dollar', code: 'NZD', symbol: 'NZ$' },
    'new zealand dollars': { base: 'currency', toBase: 50.80, name: 'New Zealand Dollar', code: 'NZD', symbol: 'NZ$' },
    'kiwi dollar': { base: 'currency', toBase: 50.80, name: 'New Zealand Dollar', code: 'NZD', symbol: 'NZ$' },

    // SGD
    'sgd': { base: 'currency', toBase: 64.80, name: 'Singapore Dollar', code: 'SGD', symbol: 'S$' },
    'singapore dollar': { base: 'currency', toBase: 64.80, name: 'Singapore Dollar', code: 'SGD', symbol: 'S$' },
    'singapore dollars': { base: 'currency', toBase: 64.80, name: 'Singapore Dollar', code: 'SGD', symbol: 'S$' },

    // HKD
    'hkd': { base: 'currency', toBase: 11.10, name: 'Hong Kong Dollar', code: 'HKD', symbol: 'HK$' },
    'hong kong dollar': { base: 'currency', toBase: 11.10, name: 'Hong Kong Dollar', code: 'HKD', symbol: 'HK$' },
    'hong kong dollars': { base: 'currency', toBase: 11.10, name: 'Hong Kong Dollar', code: 'HKD', symbol: 'HK$' },

    // AED
    'aed': { base: 'currency', toBase: 23.55, name: 'UAE Dirham', code: 'AED', symbol: 'AED' },
    'dirham': { base: 'currency', toBase: 23.55, name: 'UAE Dirham', code: 'AED', symbol: 'AED' },
    'dirhams': { base: 'currency', toBase: 23.55, name: 'UAE Dirham', code: 'AED', symbol: 'AED' },
    'uae dirham': { base: 'currency', toBase: 23.55, name: 'UAE Dirham', code: 'AED', symbol: 'AED' },
    'uae dirhams': { base: 'currency', toBase: 23.55, name: 'UAE Dirham', code: 'AED', symbol: 'AED' },
    'dhs': { base: 'currency', toBase: 23.55, name: 'UAE Dirham', code: 'AED', symbol: 'AED' },

    // SAR
    'sar': { base: 'currency', toBase: 23.05, name: 'Saudi Riyal', code: 'SAR', symbol: 'SAR' },
    'riyal': { base: 'currency', toBase: 23.05, name: 'Saudi Riyal', code: 'SAR', symbol: 'SAR' },
    'riyals': { base: 'currency', toBase: 23.05, name: 'Saudi Riyal', code: 'SAR', symbol: 'SAR' },
    'saudi riyal': { base: 'currency', toBase: 23.05, name: 'Saudi Riyal', code: 'SAR', symbol: 'SAR' },
    'saudi riyals': { base: 'currency', toBase: 23.05, name: 'Saudi Riyal', code: 'SAR', symbol: 'SAR' },

    // QAR
    'qar': { base: 'currency', toBase: 23.75, name: 'Qatari Riyal', code: 'QAR', symbol: 'QAR' },
    'qatari riyal': { base: 'currency', toBase: 23.75, name: 'Qatari Riyal', code: 'QAR', symbol: 'QAR' },
    'qatari riyals': { base: 'currency', toBase: 23.75, name: 'Qatari Riyal', code: 'QAR', symbol: 'QAR' },

    // KWD
    'kwd': { base: 'currency', toBase: 281.50, name: 'Kuwaiti Dinar', code: 'KWD', symbol: 'KWD' },
    'kuwaiti dinar': { base: 'currency', toBase: 281.50, name: 'Kuwaiti Dinar', code: 'KWD', symbol: 'KWD' },
    'kuwaiti dinars': { base: 'currency', toBase: 281.50, name: 'Kuwaiti Dinar', code: 'KWD', symbol: 'KWD' },
    'kd': { base: 'currency', toBase: 281.50, name: 'Kuwaiti Dinar', code: 'KWD', symbol: 'KWD' },

    // BHD
    'bhd': { base: 'currency', toBase: 229.40, name: 'Bahraini Dinar', code: 'BHD', symbol: 'BHD' },
    'bahraini dinar': { base: 'currency', toBase: 229.40, name: 'Bahraini Dinar', code: 'BHD', symbol: 'BHD' },
    'bahraini dinars': { base: 'currency', toBase: 229.40, name: 'Bahraini Dinar', code: 'BHD', symbol: 'BHD' },
    'bd': { base: 'currency', toBase: 229.40, name: 'Bahraini Dinar', code: 'BHD', symbol: 'BHD' },

    // OMR
    'omr': { base: 'currency', toBase: 224.70, name: 'Omani Rial', code: 'OMR', symbol: 'OMR' },
    'omani rial': { base: 'currency', toBase: 224.70, name: 'Omani Rial', code: 'OMR', symbol: 'OMR' },
    'omani rials': { base: 'currency', toBase: 224.70, name: 'Omani Rial', code: 'OMR', symbol: 'OMR' },
    'ro': { base: 'currency', toBase: 224.70, name: 'Omani Rial', code: 'OMR', symbol: 'OMR' },

    // MYR
    'myr': { base: 'currency', toBase: 19.80, name: 'Malaysian Ringgit', code: 'MYR', symbol: 'RM' },
    'ringgit': { base: 'currency', toBase: 19.80, name: 'Malaysian Ringgit', code: 'MYR', symbol: 'RM' },
    'malaysian ringgit': { base: 'currency', toBase: 19.80, name: 'Malaysian Ringgit', code: 'MYR', symbol: 'RM' },
    'rm': { base: 'currency', toBase: 19.80, name: 'Malaysian Ringgit', code: 'MYR', symbol: 'RM' },

    // THB
    'thb': { base: 'currency', toBase: 2.52, name: 'Thai Baht', code: 'THB', symbol: '฿' },
    'baht': { base: 'currency', toBase: 2.52, name: 'Thai Baht', code: 'THB', symbol: '฿' },
    'thai baht': { base: 'currency', toBase: 2.52, name: 'Thai Baht', code: 'THB', symbol: '฿' },
    '฿': { base: 'currency', toBase: 2.52, name: 'Thai Baht', code: 'THB', symbol: '฿' },

    // IDR
    'idr': { base: 'currency', toBase: 0.0053, name: 'Indonesian Rupiah', code: 'IDR', symbol: 'Rp' },
    'rupiah': { base: 'currency', toBase: 0.0053, name: 'Indonesian Rupiah', code: 'IDR', symbol: 'Rp' },
    'indonesian rupiah': { base: 'currency', toBase: 0.0053, name: 'Indonesian Rupiah', code: 'IDR', symbol: 'Rp' },
    'rp': { base: 'currency', toBase: 0.0053, name: 'Indonesian Rupiah', code: 'IDR', symbol: 'Rp' },

    // PHP
    'php': { base: 'currency', toBase: 1.48, name: 'Philippine Peso', code: 'PHP', symbol: '₱' },
    'philippine peso': { base: 'currency', toBase: 1.48, name: 'Philippine Peso', code: 'PHP', symbol: '₱' },
    'philippine pesos': { base: 'currency', toBase: 1.48, name: 'Philippine Peso', code: 'PHP', symbol: '₱' },
    'peso': { base: 'currency', toBase: 1.48, name: 'Philippine Peso', code: 'PHP', symbol: '₱' },
    'pesos': { base: 'currency', toBase: 1.48, name: 'Philippine Peso', code: 'PHP', symbol: '₱' },
    '₱': { base: 'currency', toBase: 1.48, name: 'Philippine Peso', code: 'PHP', symbol: '₱' },

    // KRW
    'krw': { base: 'currency', toBase: 0.062, name: 'South Korean Won', code: 'KRW', symbol: '₩' },
    'won': { base: 'currency', toBase: 0.062, name: 'South Korean Won', code: 'KRW', symbol: '₩' },
    'korean won': { base: 'currency', toBase: 0.062, name: 'South Korean Won', code: 'KRW', symbol: '₩' },
    'south korean won': { base: 'currency', toBase: 0.062, name: 'South Korean Won', code: 'KRW', symbol: '₩' },
    '₩': { base: 'currency', toBase: 0.062, name: 'South Korean Won', code: 'KRW', symbol: '₩' },

    // ZAR
    'zar': { base: 'currency', toBase: 4.85, name: 'South African Rand', code: 'ZAR', symbol: 'R' },
    'rand': { base: 'currency', toBase: 4.85, name: 'South African Rand', code: 'ZAR', symbol: 'R' },
    'south african rand': { base: 'currency', toBase: 4.85, name: 'South African Rand', code: 'ZAR', symbol: 'R' },

    // RUB
    'rub': { base: 'currency', toBase: 0.94, name: 'Russian Ruble', code: 'RUB', symbol: '₽' },
    'ruble': { base: 'currency', toBase: 0.94, name: 'Russian Ruble', code: 'RUB', symbol: '₽' },
    'rubles': { base: 'currency', toBase: 0.94, name: 'Russian Ruble', code: 'RUB', symbol: '₽' },
    'rouble': { base: 'currency', toBase: 0.94, name: 'Russian Ruble', code: 'RUB', symbol: '₽' },
    'roubles': { base: 'currency', toBase: 0.94, name: 'Russian Ruble', code: 'RUB', symbol: '₽' },
    'russian ruble': { base: 'currency', toBase: 0.94, name: 'Russian Ruble', code: 'RUB', symbol: '₽' },
    '₽': { base: 'currency', toBase: 0.94, name: 'Russian Ruble', code: 'RUB', symbol: '₽' },

    // TRY
    'try': { base: 'currency', toBase: 2.45, name: 'Turkish Lira', code: 'TRY', symbol: '₺' },
    'lira': { base: 'currency', toBase: 2.45, name: 'Turkish Lira', code: 'TRY', symbol: '₺' },
    'liras': { base: 'currency', toBase: 2.45, name: 'Turkish Lira', code: 'TRY', symbol: '₺' },
    'turkish lira': { base: 'currency', toBase: 2.45, name: 'Turkish Lira', code: 'TRY', symbol: '₺' },
    'tl': { base: 'currency', toBase: 2.45, name: 'Turkish Lira', code: 'TRY', symbol: '₺' },
    '₺': { base: 'currency', toBase: 2.45, name: 'Turkish Lira', code: 'TRY', symbol: '₺' },

    // BRL
    'brl': { base: 'currency', toBase: 15.20, name: 'Brazilian Real', code: 'BRL', symbol: 'R$' },
    'real': { base: 'currency', toBase: 15.20, name: 'Brazilian Real', code: 'BRL', symbol: 'R$' },
    'reais': { base: 'currency', toBase: 15.20, name: 'Brazilian Real', code: 'BRL', symbol: 'R$' },
    'brazilian real': { base: 'currency', toBase: 15.20, name: 'Brazilian Real', code: 'BRL', symbol: 'R$' },
    'r$': { base: 'currency', toBase: 15.20, name: 'Brazilian Real', code: 'BRL', symbol: 'R$' },

    // MXN
    'mxn': { base: 'currency', toBase: 4.35, name: 'Mexican Peso', code: 'MXN', symbol: 'Mex$' },
    'mexican peso': { base: 'currency', toBase: 4.35, name: 'Mexican Peso', code: 'MXN', symbol: 'Mex$' },
    'mexican pesos': { base: 'currency', toBase: 4.35, name: 'Mexican Peso', code: 'MXN', symbol: 'Mex$' },

    // SEK
    'sek': { base: 'currency', toBase: 8.25, name: 'Swedish Krona', code: 'SEK', symbol: 'kr' },
    'krona': { base: 'currency', toBase: 8.25, name: 'Swedish Krona', code: 'SEK', symbol: 'kr' },
    'kronor': { base: 'currency', toBase: 8.25, name: 'Swedish Krona', code: 'SEK', symbol: 'kr' },
    'swedish krona': { base: 'currency', toBase: 8.25, name: 'Swedish Krona', code: 'SEK', symbol: 'kr' },

    // NOK
    'nok': { base: 'currency', toBase: 8.05, name: 'Norwegian Krone', code: 'NOK', symbol: 'kr' },
    'krone': { base: 'currency', toBase: 8.05, name: 'Norwegian Krone', code: 'NOK', symbol: 'kr' },
    'kroner': { base: 'currency', toBase: 8.05, name: 'Norwegian Krone', code: 'NOK', symbol: 'kr' },
    'norwegian krone': { base: 'currency', toBase: 8.05, name: 'Norwegian Krone', code: 'NOK', symbol: 'kr' },

    // DKK
    'dkk': { base: 'currency', toBase: 12.20, name: 'Danish Krone', code: 'DKK', symbol: 'kr' },
    'danish krone': { base: 'currency', toBase: 12.20, name: 'Danish Krone', code: 'DKK', symbol: 'kr' },
    'danish kroner': { base: 'currency', toBase: 12.20, name: 'Danish Krone', code: 'DKK', symbol: 'kr' },

    // PLN
    'pln': { base: 'currency', toBase: 21.40, name: 'Polish Zloty', code: 'PLN', symbol: 'zł' },
    'zloty': { base: 'currency', toBase: 21.40, name: 'Polish Zloty', code: 'PLN', symbol: 'zł' },
    'zlotys': { base: 'currency', toBase: 21.40, name: 'Polish Zloty', code: 'PLN', symbol: 'zł' },
    'polish zloty': { base: 'currency', toBase: 21.40, name: 'Polish Zloty', code: 'PLN', symbol: 'zł' },
    'zł': { base: 'currency', toBase: 21.40, name: 'Polish Zloty', code: 'PLN', symbol: 'zł' },

    // CZK
    'czk': { base: 'currency', toBase: 3.65, name: 'Czech Koruna', code: 'CZK', symbol: 'Kč' },
    'koruna': { base: 'currency', toBase: 3.65, name: 'Czech Koruna', code: 'CZK', symbol: 'Kč' },
    'korunas': { base: 'currency', toBase: 3.65, name: 'Czech Koruna', code: 'CZK', symbol: 'Kč' },
    'czech koruna': { base: 'currency', toBase: 3.65, name: 'Czech Koruna', code: 'CZK', symbol: 'Kč' },
    'kč': { base: 'currency', toBase: 3.65, name: 'Czech Koruna', code: 'CZK', symbol: 'Kč' },

    // HUF
    'huf': { base: 'currency', toBase: 0.23, name: 'Hungarian Forint', code: 'HUF', symbol: 'Ft' },
    'forint': { base: 'currency', toBase: 0.23, name: 'Hungarian Forint', code: 'HUF', symbol: 'Ft' },
    'hungarian forint': { base: 'currency', toBase: 0.23, name: 'Hungarian Forint', code: 'HUF', symbol: 'Ft' },

    // ILS
    'ils': { base: 'currency', toBase: 23.40, name: 'Israeli Shekel', code: 'ILS', symbol: '₪' },
    'shekel': { base: 'currency', toBase: 23.40, name: 'Israeli Shekel', code: 'ILS', symbol: '₪' },
    'shekels': { base: 'currency', toBase: 23.40, name: 'Israeli Shekel', code: 'ILS', symbol: '₪' },
    'israeli shekel': { base: 'currency', toBase: 23.40, name: 'Israeli Shekel', code: 'ILS', symbol: '₪' },
    'nis': { base: 'currency', toBase: 23.40, name: 'Israeli Shekel', code: 'ILS', symbol: '₪' },
    '₪': { base: 'currency', toBase: 23.40, name: 'Israeli Shekel', code: 'ILS', symbol: '₪' },

    // EGP
    'egp': { base: 'currency', toBase: 1.78, name: 'Egyptian Pound', code: 'EGP', symbol: 'E£' },
    'egyptian pound': { base: 'currency', toBase: 1.78, name: 'Egyptian Pound', code: 'EGP', symbol: 'E£' },
    'egyptian pounds': { base: 'currency', toBase: 1.78, name: 'Egyptian Pound', code: 'EGP', symbol: 'E£' },

    // NGN
    'ngn': { base: 'currency', toBase: 0.054, name: 'Nigerian Naira', code: 'NGN', symbol: '₦' },
    'naira': { base: 'currency', toBase: 0.054, name: 'Nigerian Naira', code: 'NGN', symbol: '₦' },
    'nigerian naira': { base: 'currency', toBase: 0.054, name: 'Nigerian Naira', code: 'NGN', symbol: '₦' },
    '₦': { base: 'currency', toBase: 0.054, name: 'Nigerian Naira', code: 'NGN', symbol: '₦' },

    // BDT
    'bdt': { base: 'currency', toBase: 0.72, name: 'Bangladeshi Taka', code: 'BDT', symbol: '৳' },
    'taka': { base: 'currency', toBase: 0.72, name: 'Bangladeshi Taka', code: 'BDT', symbol: '৳' },
    'bangladeshi taka': { base: 'currency', toBase: 0.72, name: 'Bangladeshi Taka', code: 'BDT', symbol: '৳' },
    '৳': { base: 'currency', toBase: 0.72, name: 'Bangladeshi Taka', code: 'BDT', symbol: '৳' },

    // PKR
    'pkr': { base: 'currency', toBase: 0.31, name: 'Pakistani Rupee', code: 'PKR', symbol: 'PKR' },
    'pakistani rupee': { base: 'currency', toBase: 0.31, name: 'Pakistani Rupee', code: 'PKR', symbol: 'PKR' },
    'pakistani rupees': { base: 'currency', toBase: 0.31, name: 'Pakistani Rupee', code: 'PKR', symbol: 'PKR' },

    // LKR
    'lkr': { base: 'currency', toBase: 0.29, name: 'Sri Lankan Rupee', code: 'LKR', symbol: 'LKR' },
    'sri lankan rupee': { base: 'currency', toBase: 0.29, name: 'Sri Lankan Rupee', code: 'LKR', symbol: 'LKR' },
    'sri lankan rupees': { base: 'currency', toBase: 0.29, name: 'Sri Lankan Rupee', code: 'LKR', symbol: 'LKR' },

    // NPR
    'npr': { base: 'currency', toBase: 0.625, name: 'Nepalese Rupee', code: 'NPR', symbol: 'NPR' },
    'nepalese rupee': { base: 'currency', toBase: 0.625, name: 'Nepalese Rupee', code: 'NPR', symbol: 'NPR' },
    'nepalese rupees': { base: 'currency', toBase: 0.625, name: 'Nepalese Rupee', code: 'NPR', symbol: 'NPR' },

    // VND
    'vnd': { base: 'currency', toBase: 0.0034, name: 'Vietnamese Dong', code: 'VND', symbol: '₫' },
    'dong': { base: 'currency', toBase: 0.0034, name: 'Vietnamese Dong', code: 'VND', symbol: '₫' },
    'vietnamese dong': { base: 'currency', toBase: 0.0034, name: 'Vietnamese Dong', code: 'VND', symbol: '₫' },
    '₫': { base: 'currency', toBase: 0.0034, name: 'Vietnamese Dong', code: 'VND', symbol: '₫' }
  };

  // ─────────────────────────────────────────────────────────────────────────────
  // 3. WORDS TO NUMBER & NUMBER TO WORDS ENGINE (Indian Numbering Format)
  // ─────────────────────────────────────────────────────────────────────────────
  const WordsEngine = {
    ones: ['', 'One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight', 'Nine', 'Ten',
      'Eleven', 'Twelve', 'Thirteen', 'Fourteen', 'Fifteen', 'Sixteen', 'Seventeen', 'Eighteen', 'Nineteen'],
    tens: ['', '', 'Twenty', 'Thirty', 'Forty', 'Fifty', 'Sixty', 'Seventy', 'Eighty', 'Ninety'],

    toWords(num) {
      if (num == null || isNaN(num)) return '';
      let n = Math.floor(Math.abs(num));
      if (n === 0) return 'Zero Rupees Only';
      if (n >= 1e12) return `${n.toLocaleString('en-IN')} Rupees`;

      const parts = [];
      const cr = Math.floor(n / 10000000);
      const remCr = n % 10000000;
      const lakh = Math.floor(remCr / 100000);
      const remLakh = remCr % 100000;
      const hazar = Math.floor(remLakh / 1000);
      const remHazar = remLakh % 1000;
      const hundred = Math.floor(remHazar / 100);
      const remHundred = remHazar % 100;

      const formatTwoDigits = (val) => {
        if (val === 0) return '';
        if (val < 20) return this.ones[val];
        const t = Math.floor(val / 10);
        const o = val % 10;
        return `${this.tens[t]}${o > 0 ? '-' + this.ones[o] : ''}`;
      };

      if (cr > 0) parts.push(`${formatTwoDigits(cr)} Crore`);
      if (lakh > 0) parts.push(`${formatTwoDigits(lakh)} Lakh`);
      if (hazar > 0) parts.push(`${formatTwoDigits(hazar)} Thousand`);
      if (hundred > 0) parts.push(`${this.ones[hundred]} Hundred`);
      if (remHundred > 0) parts.push(formatTwoDigits(remHundred));

      return `${parts.join(' ')} Rupees Only`;
    },

    convertColloquialQuantifiers(str) {
      if (!str || typeof str !== 'string') return '';
      let s = str;
      const compoundPatterns = [
        [/\b(?:dedh\s+hazar|dedh\s+hajar)\b/gi, '1500'],
        [/\b(?:dhai\s+hazar|adhai\s+hazar)\b/gi, '2500'],
        [/\b(?:sawa\s+hazar|sawa\s+hajar)\b/gi, '1250'],
        [/\b(?:paune\s+hazar|paune\s+hajar)\b/gi, '750'],
        [/\b(?:sawa\s+do\s+hazar)\b/gi, '2250'],
        [/\b(?:paune\s+do\s+hazar)\b/gi, '1750'],
        [/\b(?:sawa\s+teen\s+hazar)\b/gi, '3250'],
        [/\b(?:paune\s+teen\s+hazar)\b/gi, '2750'],
        [/\b(?:dedh\s+lakh|dedh\s+lac)\b/gi, '150000'],
        [/\b(?:dhai\s+lakh|adhai\s+lakh)\b/gi, '250000'],
        [/\b(?:sawa\s+lakh|sawa\s+lac)\b/gi, '125000'],
        [/\b(?:paune\s+lakh|paune\s+lac)\b/gi, '75000'],
        [/\b(?:aadha\s+lakh|aadha\s+lac|adha\s+lakh)\b/gi, '50000'],
        [/\b(?:sawa\s+do\s+lakh)\b/gi, '225000'],
        [/\b(?:paune\s+do\s+lakh)\b/gi, '175000'],
        [/\b(?:sawa\s+teen\s+lakh)\b/gi, '325000'],
        [/\b(?:paune\s+teen\s+lakh)\b/gi, '275000'],
        [/\b(?:sawa\s+char\s+lakh)\b/gi, '425000'],
        [/\b(?:paune\s+char\s+lakh)\b/gi, '375000'],
        [/\b(?:dedh\s+crore|dedh\s+cr)\b/gi, '15000000'],
        [/\b(?:dhai\s+crore|adhai\s+crore)\b/gi, '25000000'],
        [/\b(?:sawa\s+crore|sawa\s+cr)\b/gi, '12500000'],
        [/\b(?:paune\s+crore|paune\s+cr)\b/gi, '7500000'],
        [/\b(?:aadha\s+crore|aadha\s+cr|adha\s+crore)\b/gi, '5000000'],
        [/\b(\d+(?:\.\d+)?)\s*(?:lakh|lakhs|lac|lacs)\b/gi, (m, n) => `${parseFloat(n) * 100000}`],
        [/\b(\d+(?:\.\d+)?)\s*(?:crore|crores|cr)\b/gi, (m, n) => `${parseFloat(n) * 10000000}`],
        [/\b(\d+(?:\.\d+)?)\s*(?:hazar|hazaar|haz|k)\b/gi, (m, n) => `${parseFloat(n) * 1000}`]
      ];
      for (const [re, repl] of compoundPatterns) {
        s = s.replace(re, repl);
      }
      return s;
    },

    wordsToNumber(str) {
      if (!str || typeof str !== 'string') return null;
      const s = str.toLowerCase().trim();
      const numMap = {
        'zero': 0, 'shunya': 0, 'one': 1, 'ek': 1, 'two': 2, 'do': 2, 'three': 3, 'teen': 3,
        'four': 4, 'char': 4, 'five': 5, 'panch': 5, 'six': 6, 'chah': 6, 'sah': 6, 'seven': 7,
        'saat': 7, 'eight': 8, 'aath': 8, 'nine': 9, 'nau': 9, 'ten': 10, 'das': 10, 'daha': 10,
        'twenty': 20, 'bees': 20, 'thirty': 30, 'tees': 30, 'forty': 40, 'chalis': 40,
        'fifty': 50, 'pachas': 50, 'sixty': 60, 'saath': 60, 'seventy': 70, 'sattar': 70,
        'eighty': 80, 'assi': 80, 'ninety': 90, 'nabbe': 90, 'hundred': 100, 'sau': 100, 'she': 100,
        'thousand': 1000, 'hazar': 1000, 'hazaar': 1000, 'lakh': 100000, 'lac': 100000, 'lakhs': 100000,
        'crore': 10000000, 'cr': 10000000, 'crores': 10000000
      };

      if (s.includes('dedh hazar') || s.includes('dedh hajar')) return 1500;
      if (s.includes('dhai hazar') || s.includes('adhai hazar')) return 2500;
      if (s.includes('sawa hazar') || s.includes('sawa hajar')) return 1250;
      if (s.includes('paune hazar') || s.includes('paune hajar')) return 750;
      if (s.includes('sawa do hazar')) return 2250;
      if (s.includes('paune do hazar')) return 1750;
      if (s.includes('sawa teen hazar')) return 3250;
      if (s.includes('paune teen hazar')) return 2750;
      if (s.includes('dedh lakh') || s.includes('dedh lac')) return 150000;
      if (s.includes('dhai lakh') || s.includes('adhai lakh')) return 250000;
      if (s.includes('sawa lakh') || s.includes('sawa lac')) return 125000;
      if (s.includes('paune lakh') || s.includes('paune lac')) return 75000;
      if (s.includes('aadha lakh') || s.includes('aadha lac') || s.includes('adha lakh')) return 50000;
      if (s.includes('sawa do lakh')) return 225000;
      if (s.includes('paune do lakh')) return 175000;
      if (s.includes('sawa teen lakh')) return 325000;
      if (s.includes('paune teen lakh')) return 275000;
      if (s.includes('dedh crore') || s.includes('dedh cr')) return 15000000;
      if (s.includes('dhai crore') || s.includes('adhai crore')) return 25000000;
      if (s.includes('sawa crore') || s.includes('sawa cr')) return 12500000;
      if (s.includes('paune crore') || s.includes('paune cr')) return 7500000;
      if (s.includes('aadha crore') || s.includes('aadha cr') || s.includes('adha crore')) return 5000000;

      const words = s.split(/[\s-]+/).filter(Boolean);
      let total = 0;
      let current = 0;
      let numberWordCount = 0;

      for (let i = 0; i < words.length; i++) {
        const w = words[i];
        if (numMap[w] !== undefined) {
          // Guard for English word "do" (e.g., "who do we owe", "how do we")
          if (w === 'do') {
            const hasNumNeighbor = (i > 0 && numMap[words[i - 1]] !== undefined) ||
                                  (i < words.length - 1 && numMap[words[i + 1]] !== undefined);
            if (!hasNumNeighbor && words.length > 1) {
              continue;
            }
          }
          numberWordCount++;
          const val = numMap[w];
          if (val === 100) {
            current = (current === 0 ? 1 : current) * 100;
          } else if (val === 1000 || val === 100000 || val === 10000000) {
            current = (current === 0 ? 1 : current) * val;
            total += current;
            current = 0;
          } else {
            current += val;
          }
        }
      }
      total += current;
      // Only match if at least 50% of the words are number tokens or single word number
      if (numberWordCount > 0 && total > 0 && (numberWordCount / words.length >= 0.5 || numberWordCount === words.length)) {
        return total;
      }
      return null;
    }
  };

  // ─────────────────────────────────────────────────────────────────────────────
  // 3.5 SESSION CONTEXT & MULTI-INTENT QUERY SEGMENTATION
  // ─────────────────────────────────────────────────────────────────────────────
  const SessionContextManager = {
    _firmContexts: {},

    getContext(firmId = 'default') {
      const fKey = String(firmId != null ? firmId : 'default');
      if (!this._firmContexts[fKey]) {
        this._firmContexts[fKey] = {
          lastEntity: null,
          lastCustomer: null,
          lastParty: null,
          lastStaff: null,
          lastProduct: null,
          lastCalculation: null,
          ans: 0
        };
      }
      return this._firmContexts[fKey];
    },

    setContext(firmId = 'default', update = {}) {
      const fKey = String(firmId != null ? firmId : 'default');
      const cur = this.getContext(fKey);
      this._firmContexts[fKey] = { ...cur, ...update };
      return this._firmContexts[fKey];
    },

    clearContext(firmId) {
      if (firmId != null) {
        delete this._firmContexts[String(firmId)];
      } else {
        this._firmContexts = {};
      }
    }
  };

  const QuerySegmenter = {
    // Segments compound queries across conjunction boundaries
    segment(rawQuery) {
      if (!rawQuery) return [rawQuery];
      const trimmed = rawQuery.trim();

      // Guard 1: Date interval / range comparisons "between X and Y", "days between X and Y"
      if (/\b(?:between|from|gap\s+between|days\s+between|time\s+between|duration\s+between)\b.*?\b(?:and|to)\b/i.test(trimmed)) {
        return [trimmed];
      }

      // Guard 2: Pure arithmetic words "100 and 50", "add 100 and 50"
      if (/^(?:add\s+)?\d+(?:\.\d+)?\s+(?:and|aur|ani)\s+\d+(?:\.\d+)?$/i.test(trimmed)) {
        return [trimmed];
      }

      // Guard 3: ISO Date range "2026-03-01 and 2026-03-20"
      if (/\d{4}[-/]\d{2}[-/]\d{2}\s+(?:and|to)\s+\d{4}[-/]\d{2}[-/]\d{2}/i.test(trimmed)) {
        return [trimmed];
      }

      // Compound connectors: ' and then ', ' then ', ' aur phir ', ' aur ', ' ani mag ', ' ani ', ' and ', ';'
      const conjunctionRegex = /\s+(?:and\s+then|then|aur\s+phir|aur|ani\s+mag|ani|and)\s+|\s*;\s*/i;
      if (!conjunctionRegex.test(trimmed)) {
        return [trimmed];
      }

      const parts = trimmed.split(conjunctionRegex).map(s => s.trim()).filter(Boolean);
      return parts.length > 1 ? parts : [trimmed];
    }
  };

  // ─────────────────────────────────────────────────────────────────────────────
  // 4. ROLE RESOLVER & PARAMETER EXTRACTION
  // ─────────────────────────────────────────────────────────────────────────────
  const RoleResolver = {
    extractRates(str) {
      if (!str) return [];
      const rates = [];
      const pRegex = /(\d+(?:\.\d+)?)\s*(?:%|percent|percentage|pratishat|takke)/gi;
      let m;
      while ((m = pRegex.exec(str)) !== null) {
        rates.push({ rate: parseFloat(m[1]), raw: m[0].trim() });
      }
      if (rates.length === 0) {
        const rm = /(?:rate|gst|tax|interest|discount|margin|markup|byaj|vyaj)\s*(?:of|is|at|@|hai|aahe)?\s*(\d+(?:\.\d+)?)/i.exec(str);
        if (rm) {
          const val = parseFloat(rm[1]);
          if (val <= 100) rates.push({ rate: val, raw: rm[0].trim() });
        }
      }
      return rates;
    },

    extractAmounts(str) {
      if (!str) return [];
      const amounts = [];
      const regex = /(?:₹|rs\.?|inr|\$)?\s*(\d+(?:,\d+)*(?:\.\d+)?)\s*(cr\b|crore\b|crores\b|lakh\b|lakhs\b|lac\b|lacs\b|k\b|hazar\b|hazaar\b|thousand\b)?(?:\b|\s|$)/gi;
      let m;
      while ((m = regex.exec(str)) !== null) {
        const rawMatch = m[0];
        const restOfStr = str.slice(regex.lastIndex, regex.lastIndex + 15).toLowerCase();
        if (rawMatch.includes('%') || /^\s*(%|percent|percentage|pratishat|takke)/.test(restOfStr)) {
          continue;
        }
        const cleanDigits = m[1].replace(/,/g, '');
        const rawNum = parseFloat(cleanDigits);
        if (isNaN(rawNum)) continue;
        const unit = (m[2] || '').toLowerCase().trim();
        let multiplier = 1;
        if (unit === 'k' || unit.startsWith('thous') || unit.startsWith('haz')) multiplier = 1000;
        else if (unit.startsWith('l') || unit.startsWith('lac')) multiplier = 100000;
        else if (unit.startsWith('cr')) multiplier = 10000000;

        amounts.push({ val: rawNum * multiplier, raw: m[0].trim() });
      }
      return amounts;
    },

    extractDateTime(str, refDate = new Date()) {
      if (!str) return null;
      const q = str.trim();
      let targetDate = new Date(refDate.getTime());
      let hasDate = false;
      let hasTime = false;
      let rawTimeStr = '';
      let rawDateStr = '';

      const monthNames = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
      const monthMap = {
        january: 0, jan: 0, janury: 0, januray: 0, jannuary: 0,
        february: 1, feb: 1, febuary: 1, febrary: 1, feburary: 1,
        march: 2, mar: 2,
        april: 3, apr: 3, aprl: 3, aprail: 3,
        may: 4,
        june: 5, jun: 5,
        july: 6, jul: 6,
        august: 7, aug: 7, augst: 7, agust: 7,
        september: 8, sept: 8, sep: 8, septeber: 8, septmber: 8, setember: 8, septembr: 8,
        october: 9, oct: 9, octomber: 9, octber: 9,
        november: 10, nov: 10, novmber: 10, novemer: 10,
        december: 11, dec: 11, decmber: 11, descember: 11
      };
      const monthRegexStr = '(?:january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|jun|jul|aug|sep|sept|oct|nov|dec|septeber|septmber|setember|septembr|febuary|febrary|feburary|janury|januray|jannuary|aprl|aprail|augst|agust|octomber|octber|novmber|novemer|decmber|descember)';

      // 1. Explicit Calendar Dates:
      // a) "27 september", "27th september 2026", "27 sept", "on 27th sept"
      const dateMonthRegex = new RegExp('(?:\\b(?:on|dated|for)\\s+)?\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?(' + monthRegexStr + ')(?:\\s*,?\\s*(\\d{4}))?\\b', 'i');
      const dmMatch = q.match(dateMonthRegex);
      if (dmMatch) {
        const day = parseInt(dmMatch[1], 10);
        const mIdx = monthMap[dmMatch[2].toLowerCase()];
        const yr = dmMatch[3] ? parseInt(dmMatch[3], 10) : targetDate.getFullYear();
        if (mIdx !== undefined && day >= 1 && day <= 31) {
          targetDate.setFullYear(yr, mIdx, day);
          hasDate = true;
          rawDateStr = dmMatch[0];
        }
      }

      // b) "september 27", "septeber 27", "sept 27th 2026"
      if (!hasDate) {
        const monthDateRegex = new RegExp('(?:\\b(?:on|dated|for)\\s+)?\\b(' + monthRegexStr + ')\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:\\s*,?\\s*(\\d{4}))?\\b', 'i');
        const mdMatch = q.match(monthDateRegex);
        if (mdMatch) {
          const mIdx = monthMap[mdMatch[1].toLowerCase()];
          const day = parseInt(mdMatch[2], 10);
          const yr = mdMatch[3] ? parseInt(mdMatch[3], 10) : targetDate.getFullYear();
          if (mIdx !== undefined && day >= 1 && day <= 31) {
            targetDate.setFullYear(yr, mIdx, day);
            hasDate = true;
            rawDateStr = mdMatch[0];
          }
        }
      }

      // c) ISO format: "2026-09-27"
      if (!hasDate) {
        const isoMatch = q.match(/\b(\d{4})[-/](\d{1,2})[-/](\d{1,2})\b/);
        if (isoMatch) {
          const yr = parseInt(isoMatch[1], 10);
          const mon = parseInt(isoMatch[2], 10) - 1;
          const day = parseInt(isoMatch[3], 10);
          if (mon >= 0 && mon <= 11 && day >= 1 && day <= 31) {
            targetDate.setFullYear(yr, mon, day);
            hasDate = true;
            rawDateStr = isoMatch[0];
          }
        }
      }

      // d) Standard slash/dash: "27/09/2026", "27-09-2026", "27/09", "27-09"
      if (!hasDate) {
        const dmyMatch = q.match(/(?:\b(?:on|dated|for)\s+)?\b(\d{1,2})[-/](\d{1,2})(?:[-/](\d{2,4}))?\b/);
        if (dmyMatch) {
          const day = parseInt(dmyMatch[1], 10);
          const mon = parseInt(dmyMatch[2], 10) - 1;
          let yr = dmyMatch[3] ? parseInt(dmyMatch[3], 10) : targetDate.getFullYear();
          if (yr < 100) yr += 2000;
          if (mon >= 0 && mon <= 11 && day >= 1 && day <= 31) {
            targetDate.setFullYear(yr, mon, day);
            hasDate = true;
            rawDateStr = dmyMatch[0];
          }
        }
      }

      // e) Desi "27 tarikh ko", "27 tarikhela"
      if (!hasDate) {
        const tarikhMatch = q.match(/(?:\b(?:on|dated|for)\s+)?\b(\d{1,2})\s*(?:st|nd|rd|th)?\s*(?:tarikh|tarikhe|tarikhela|tarikh\s+ko)\b/i);
        if (tarikhMatch) {
          const day = parseInt(tarikhMatch[1], 10);
          if (day >= 1 && day <= 31) {
            targetDate.setDate(day);
            hasDate = true;
            rawDateStr = tarikhMatch[0];
          }
        }
      }

      // 2. Relative Offsets: "in 10 minutes", "in 2 hours", "in 3 days", "15 min baad"
      const relOffsetMatch = q.match(/\b(?:in\s+)?(\d+)\s*(mins?|minutes?|hrs?|hours?|ghante?|days?|din)\s*(?:baad|later|after)?\b/i);
      if (relOffsetMatch) {
        const val = parseInt(relOffsetMatch[1], 10);
        const unit = relOffsetMatch[2].toLowerCase();
        if (/mins?|minutes?/.test(unit)) {
          targetDate = new Date(targetDate.getTime() + val * 60 * 1000);
          hasTime = true;
          rawTimeStr = relOffsetMatch[0];
        } else if (/hrs?|hours?|ghante?/.test(unit)) {
          targetDate = new Date(targetDate.getTime() + val * 60 * 60 * 1000);
          hasTime = true;
          rawTimeStr = relOffsetMatch[0];
        } else if (/days?|din/.test(unit)) {
          targetDate.setDate(targetDate.getDate() + val);
          hasDate = true;
          rawDateStr = relOffsetMatch[0];
        }
      }

      // 3. Relative Dates: "today", "tomorrow", "kal", "udya", "day after tomorrow", "parso", "yesterday"
      if (!hasDate) {
        if (/\b(?:day\s+after\s+tomorrow|parso|parva)\b/i.test(q)) {
          targetDate.setDate(targetDate.getDate() + 2);
          hasDate = true;
          rawDateStr = q.match(/\b(?:day\s+after\s+tomorrow|parso|parva)\b/i)[0];
        } else if (/\b(?:tomorrow|kal|udya)\b/i.test(q)) {
          targetDate.setDate(targetDate.getDate() + 1);
          hasDate = true;
          rawDateStr = q.match(/\b(?:tomorrow|kal|udya)\b/i)[0];
        } else if (/\b(?:today|aaj|tonight|this\s+evening)\b/i.test(q)) {
          hasDate = true;
          rawDateStr = q.match(/\b(?:today|aaj|tonight|this\s+evening)\b/i)[0];
        } else if (/\b(?:yesterday)\b/i.test(q)) {
          targetDate.setDate(targetDate.getDate() - 1);
          hasDate = true;
          rawDateStr = q.match(/\b(?:yesterday)\b/i)[0];
        }
      }

      // 4. Weekday detection: "next monday", "this friday", "somvar", "shaniwar"
      if (!hasDate) {
        const daysMap = {
          sunday: 0, ravivar: 0, aitwar: 0,
          monday: 1, somvar: 1,
          tuesday: 2, mangalwar: 2, mangal: 2,
          wednesday: 3, budhwar: 3, budh: 3,
          thursday: 4, guruwar: 4, brihaspati: 4,
          friday: 5, shukrawar: 5, shukra: 5,
          saturday: 6, shaniwar: 6, shani: 6
        };
        const weekdayMatch = q.match(/\b(?:next|this|on)?\s*(sunday|monday|tuesday|wednesday|thursday|friday|saturday|ravivar|somvar|mangalwar|budhwar|guruwar|shukrawar|shaniwar)\b/i);
        if (weekdayMatch) {
          const targetDay = daysMap[weekdayMatch[1].toLowerCase()];
          if (targetDay !== undefined) {
            const curDay = targetDate.getDay();
            let diff = targetDay - curDay;
            if (diff <= 0) diff += 7;
            targetDate.setDate(targetDate.getDate() + diff);
            hasDate = true;
            rawDateStr = weekdayMatch[0];
          }
        }
      }

      // 5. Time detection:
      // a) 12-hour: "11 pm", "11:30 am", "4pm", "at 11 pm", "11.30 pm"
      const time12Match = q.match(/(?:at\s+)?\b(\d{1,2})(?::(\d{2})|\.(\d{2}))?\s*(am|pm)\b/i);
      if (time12Match) {
        let hours = parseInt(time12Match[1], 10);
        const minutes = parseInt(time12Match[2] || time12Match[3] || '0', 10);
        const meridiem = time12Match[4].toLowerCase();
        if (meridiem === 'pm' && hours < 12) hours += 12;
        if (meridiem === 'am' && hours === 12) hours = 0;
        targetDate.setHours(hours, minutes, 0, 0);
        hasTime = true;
        rawTimeStr = time12Match[0];
      }

      // b) Desi time: "shaam 6 baje", "subah 9:30 baje", "sakali 8 vajta"
      if (!hasTime) {
        const desiPrefixMatch = q.match(/\b(?:(?:shaam|dopahar|sandhyakali|ratre|raat|evening|night|subah|sakali|morning)(?:\s+(?:ko|chya|la|pe))?\s+)(\d{1,2})(?::(\d{2})|\.(\d{2}))?(?:\s*(?:baje|vajta|vaje))?\b/i);
        const desiSuffixMatch = q.match(/\b(\d{1,2})(?::(\d{2})|\.(\d{2}))?\s*(?:baje|vajta|vaje)(?:\s+(?:ko|chya|la|pe))?(?:\s+(?:shaam|dopahar|sandhyakali|ratre|raat|evening|night|subah|sakali|morning))?\b/i);
        const desiMatch = desiPrefixMatch || desiSuffixMatch;
        if (desiMatch) {
          let hours = parseInt(desiMatch[1], 10);
          const minutes = parseInt(desiMatch[2] || desiMatch[3] || '0', 10);
          const isEvening = /shaam|dopahar|sandhyakali|ratre|raat|evening|night/i.test(desiMatch[0]);
          if (isEvening && hours < 12) hours += 12;
          targetDate.setHours(hours, minutes, 0, 0);
          hasTime = true;
          rawTimeStr = desiMatch[0];
        }
      }

      // c) 24-hour time: "at 14:30", "at 23:00"
      if (!hasTime) {
        const time24Match = q.match(/(?:at\s+)?\b([01]?\d|2[0-3]):([0-5]\d)\b/i);
        if (time24Match) {
          const hours = parseInt(time24Match[1], 10);
          const minutes = parseInt(time24Match[2], 10);
          targetDate.setHours(hours, minutes, 0, 0);
          hasTime = true;
          rawTimeStr = time24Match[0];
        }
      }

      if (!hasDate && !hasTime) return null;

      const yyyy = targetDate.getFullYear();
      const mm = String(targetDate.getMonth() + 1).padStart(2, '0');
      const dd = String(targetDate.getDate()).padStart(2, '0');
      const isoDate = `${yyyy}-${mm}-${dd}`;
      const hoursStr = String(targetDate.getHours()).padStart(2, '0');
      const minsStr = String(targetDate.getMinutes()).padStart(2, '0');
      const timeStr = hasTime ? `${hoursStr}:${minsStr}` : null;

      const monStr = monthNames[targetDate.getMonth()];
      let label = `${targetDate.getDate()} ${monStr} ${yyyy}`;
      const now = new Date();
      if (now.toDateString() === targetDate.toDateString()) label = 'Today';
      else if (new Date(now.getTime() + 86400000).toDateString() === targetDate.toDateString()) label = 'Tomorrow';
      else if (new Date(now.getTime() - 86400000).toDateString() === targetDate.toDateString()) label = 'Yesterday';

      let formatted = label;
      if (hasTime) {
        let h = targetDate.getHours();
        const m = String(targetDate.getMinutes()).padStart(2, '0');
        const mer = h >= 12 ? 'PM' : 'AM';
        h = h % 12 || 12;
        formatted += `, ${h}:${m} ${mer}`;
      }

      const combinedRaw = [rawDateStr, rawTimeStr].filter(Boolean).join(' ').trim();
      const isoDateTime = `${isoDate}T${hasTime ? timeStr + ':00' : '09:00:00'}`;

      // Clean title extraction:
      let cleanTitle = q;
      // 1. Strip leading action command triggers
      cleanTitle = cleanTitle
        .replace(/^(?:please\s+)?(?:set\s+reminder\s+for|set\s+reminder|create\s+reminder\s+for|create\s+reminder|add\s+reminder\s+for|add\s+reminder|reminder\s+for|reminder\s+to|remind\s+me\s+to|remind\s+me\s+for|remind\s+me|reminder|remind|todo\s+to|todo|task\s+to|task|alarm\s+for|alarm|yaad\s+dilao|yaad\s+dilana|aathvan\s+kara|athvan\s+kara)\s+/gi, '')
        .replace(/^(?:please\s+)?(?:to\s+|for\s+)/gi, '');

      // 2. Strip trailing action triggers (e.g. "buy milk remind me", "buy milk reminder")
      cleanTitle = cleanTitle
        .replace(/\s+(?:remind\s+me\s+to|remind\s+me\s+for|remind\s+me|set\s+reminder|reminder|remind|todo|task|yaad\s+dilao|aathvan\s+kara)$/gi, '');

      // 3. Strip date & time matches
      if (rawDateStr) {
        const parts = rawDateStr.split(/\s+/).filter(Boolean);
        for (const p of parts) {
          cleanTitle = cleanTitle.replace(new RegExp(`\\b${p.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\b`, 'gi'), ' ');
        }
      }
      if (rawTimeStr) {
        const parts = rawTimeStr.split(/\s+/).filter(Boolean);
        for (const p of parts) {
          cleanTitle = cleanTitle.replace(new RegExp(`\\b${p.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\b`, 'gi'), ' ');
        }
      }

      // 4. Strip leftover date words / prepositions and common month typos
      const monthTyposRegex = new RegExp('\\b' + monthRegexStr + '\\b', 'gi');
      cleanTitle = cleanTitle
        .replace(monthTyposRegex, ' ')
        .replace(/\b(at|on|for|dated|by|today|tomorrow|yesterday|aaj|kal|udya|parso|tonight|pm|am|baje|vajta|shaam|subah|sakali|dopahar|raat|ko|la|pe|tarikh|tarikhe|tarikhela)\b/gi, ' ')
        .replace(/\s+/g, ' ')
        .trim();

      if (cleanTitle.length > 0) {
        cleanTitle = cleanTitle.charAt(0).toUpperCase() + cleanTitle.slice(1);
      }

      return {
        date: isoDate,
        dueDate: isoDateTime,
        isoDate: isoDate,
        time: timeStr,
        targetDate,
        label,
        dateLabel: label,
        formatted,
        timeFormatted: formatted,
        rawDateMatch: rawDateStr,
        rawTimeMatch: rawTimeStr,
        rawMatch: combinedRaw,
        cleanTitle,
        isScheduled: hasDate || hasTime
      };
    },

    extractDate(str) {
      const dt = this.extractDateTime(str);
      return dt ? { date: dt.date, label: dt.label } : null;
    },

    extractEntities(normalized, ctx = {}) {
      const { decoupled, lower, raw } = normalized;
      const rates = this.extractRates(decoupled);
      const amounts = this.extractAmounts(decoupled);
      const dt = this.extractDateTime(raw) || this.extractDateTime(decoupled);
      const date = dt ? { date: dt.date, label: dt.label, time: dt.time, formatted: dt.formatted, rawMatch: dt.rawMatch } : null;

      // Extract phone number (10 digits starting with 6-9)
      const phoneMatch = raw.match(/\b([6-9]\d{9})\b/);
      const phone = phoneMatch ? phoneMatch[1] : null;

      const entities = {
        amounts,
        amount: amounts.length > 0 ? amounts[0].val : null,
        rates,
        rate: rates.length > 0 ? rates[0].rate : null,
        date,
        dueDate: dt ? dt.dueDate : null,
        dateLabel: dt ? dt.label : 'Today',
        timeFormatted: dt ? dt.timeFormatted : null,
        isScheduled: dt ? dt.isScheduled : false,
        phone,
        targetName: null,
        matchedEntity: null,
        matchedCustomer: null,
        matchedParty: null,
        matchingCandidates: [],
        title: null,
        customerName: null,
        staffName: null,
        city: null,
        attendanceStatus: 'PRESENT',
        itemType: (dt && dt.isScheduled) || /\b(remind|reminder|alarm|yaad|athvan|aathvan)\b/i.test(lower) ? 'reminder' : 'task',
        rawText: raw
      };

      // Extract matching customer, vendor, staff, or product entities
      const customers = ctx.customers || [];
      const parties = ctx.parties || [];
      const staff = ctx.staff || [];
      const products = ctx.products || [];
      const allEntities = [
        ...customers.map(c => ({ name: c.name, type: 'CUSTOMER', id: c.id, raw: c })),
        ...parties.map(p => ({ name: p.name, type: 'VENDOR', id: p.id, raw: p })),
        ...staff.map(s => ({ name: s.name, type: 'STAFF', id: s.id, raw: s })),
        ...products.map(pr => ({ name: pr.name, type: 'PRODUCT', id: pr.id, raw: pr }))
      ];

      // 1. Full name boundary match
      for (const ent of allEntities) {
        if (ent.name && new RegExp(`\\b${ent.name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\b`, 'i').test(raw)) {
          entities.matchingCandidates.push(ent);
        }
      }

      // 2. If no full name match, check multi-token matching with score count
      if (entities.matchingCandidates.length === 0) {
        let scored = [];
        for (const ent of allEntities) {
          if (!ent.name) continue;
          const tokens = ent.name.split(/\s+/).filter(t => t.length >= 3);
          let matchCount = 0;
          for (const tok of tokens) {
            if (new RegExp(`\\b${tok.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\b`, 'i').test(raw)) {
              matchCount++;
            }
          }
          if (matchCount > 0) {
            scored.push({ ent, matchCount, totalTokens: tokens.length });
          }
        }
        if (scored.length > 0) {
          scored.sort((a, b) => b.matchCount - a.matchCount);
          const maxMatch = scored[0].matchCount;
          const best = scored.filter(s => s.matchCount === maxMatch);
          for (const b of best) {
            if (!entities.matchingCandidates.some(x => x.id === b.ent.id && x.type === b.ent.type)) {
              entities.matchingCandidates.push(b.ent);
            }
          }
        }
      }

      // 3. Pronoun / Anaphora Resolution via Session Context
      if (entities.matchingCandidates.length === 0) {
        const pronounMatch = /\b(his|her|their|him|unka|unki|unke|uske|uski|iske|iski|tyanche|tyancha|tyanchi|tyala)\b/i.test(raw) ||
                             /\b(his|her|their|him|unka|unki|unke|uske|uski|iske|iski|tyanche|tyancha|tyanchi|tyala)\b/i.test(normalized.transliterated || normalized.lower);
        if (pronounMatch) {
          const firmId = ctx.activeFirmId || ctx.firmId || (ctx.firm && ctx.firm.id) || 'default';
          const sessionCtx = ctx.sessionContext || SessionContextManager.getContext(firmId);
          if (sessionCtx && sessionCtx.lastCustomer) {
            const lastCust = sessionCtx.lastCustomer;
            entities.targetName = lastCust.name || lastCust.customerName;
            entities.customerName = lastCust.name || lastCust.customerName;
            entities.matchedCustomer = lastCust;
            entities.matchedEntity = { name: lastCust.name || lastCust.customerName, type: 'CUSTOMER', id: lastCust.id, raw: lastCust };
            entities.matchingCandidates.push(entities.matchedEntity);
          } else if (sessionCtx && sessionCtx.lastEntity) {
            const lastEnt = sessionCtx.lastEntity;
            entities.targetName = lastEnt.name;
            entities.matchedEntity = lastEnt;
            entities.matchingCandidates.push(lastEnt);
            if (lastEnt.type === 'CUSTOMER') {
              entities.matchedCustomer = lastEnt.raw || lastEnt;
              entities.customerName = lastEnt.name;
            }
            if (lastEnt.type === 'VENDOR') entities.matchedParty = lastEnt.raw || lastEnt;
          }
        }
      }

      if (entities.matchingCandidates.length === 1) {
        const ent = entities.matchingCandidates[0];
        entities.targetName = ent.name;
        entities.matchedEntity = ent;
        if (ent.type === 'CUSTOMER') {
          entities.matchedCustomer = ent.raw || ent;
          entities.customerName = ent.name;
        }
        if (ent.type === 'VENDOR') entities.matchedParty = ent.raw || ent;
        if (ent.type === 'STAFF') {
          entities.staffName = ent.name;
          entities.matchedStaff = ent.raw || ent;
        }
        if (ent.type === 'PRODUCT') {
          entities.productName = ent.name;
          entities.matchedProduct = ent.raw || ent;
        }
      } else if (entities.matchingCandidates.length > 1) {
        entities.isAmbiguous = true;
      }

      // Extract document number (e.g., INV-1002, EST-101, PO-50, 1002, estimate 101)
      const docMatch = raw.match(/\b(?:inv|invoice|bill|est|estimate|quote|quotation|po|order)[-_ ]?(\d+)\b/i) || raw.match(/\b#?(\d{3,})\b/);
      if (docMatch) {
        entities.documentNumber = docMatch[1] || docMatch[0];
        entities.docQuery = docMatch[0];
      }

      // Extract Expense category
      const catMatch = raw.match(/\b(rent|salary|salaries|travel|travelling|chai|tea|food|snack|snacks|electricity|power|internet|wifi|office|maintenance|cleaning|petrol|diesel|fuel|courier|stationery|marketing|ad|ads)\b/i);
      if (catMatch) {
        entities.expenseCategory = catMatch[1].charAt(0).toUpperCase() + catMatch[1].slice(1).toLowerCase();
      }

      // Extract period
      if (/\b(?:yesterday|kal)\b/i.test(lower)) entities.period = 'YESTERDAY';
      else if (/\b(?:today|aaj)\b/i.test(lower)) entities.period = 'TODAY';
      else if (/\b(?:this\s*week|current\s*week|hfte)\b/i.test(lower)) entities.period = 'THIS_WEEK';
      else if (/\b(?:last\s*month|pichle\s*mahine)\b/i.test(lower)) entities.period = 'LAST_MONTH';
      else if (/\b(?:this\s*month|current\s*month|is\s*mahine|monthly)\b/i.test(lower)) entities.period = 'THIS_MONTH';
      else if (/\b(?:quarter|this\s*quarter|quarterly)\b/i.test(lower)) entities.period = 'THIS_QUARTER';
      else if (/\b(?:this\s*year|yearly|annual|saal|fy)\b/i.test(lower)) entities.period = 'THIS_YEAR';
      else entities.period = 'TOTAL';

      // Possessive regex fallback: "Gauri's", "Gauri ka", "Tata ko"
      if (!entities.targetName) {
        const possMatch = /\b([a-zA-Z]+(?:\s+[a-zA-Z]+)?)(?:'s|\s+(?:ka|cha|ko|sathi|ki|ne))\b/i.exec(raw);
        if (possMatch) {
          entities.targetName = possMatch[1].trim();
        }
      }

      // Staff Attendance / Advance name fallback:
      if (!entities.staffName) {
        const attMatch = raw.match(/\b(?:attendance|hazri|advance|salary\s+advance|mark)\s+([a-zA-Z]+(?:\s+[a-zA-Z]+)?)\b/i);
        if (attMatch) {
          let sName = attMatch[1].trim()
            .replace(/\b(present|absent|half\s*day|today|kal|salary|for)\b/gi, '')
            .trim();
          if (sName) entities.staffName = sName;
        }
      }

      // Attendance status
      if (/\b(absent|gairhazir|chutti)\b/i.test(lower)) {
        entities.attendanceStatus = 'ABSENT';
      } else if (/\b(half\s*day|ardha\s*divas)\b/i.test(lower)) {
        entities.attendanceStatus = 'HALF_DAY';
      } else {
        entities.attendanceStatus = 'PRESENT';
      }

      // Customer Registration name & city extraction
      if (/\b(customer|cust|grahak|client)\b/i.test(lower)) {
        const custClean = raw.replace(/\b(customer|cust|grahak|client|add|new|register|naya)\b/gi, '')
          .replace(phone || '', '')
          .replace(/\b\d+(?:\.\d+)?\b/g, '')
          .trim();
        const parts = custClean.split(/\s+/).filter(Boolean);
        if (parts.length === 1) {
          entities.customerName = parts[0];
        } else if (parts.length === 2) {
          entities.customerName = parts[0];
          entities.city = parts[1];
        } else if (parts.length > 2) {
          entities.customerName = parts.slice(0, -1).join(' ');
          entities.city = parts[parts.length - 1];
        }
      }

      // Note body extraction
      if (/^(?:note|sticky\s*note|pin\s*note|add\s*note|chitthi|tippan)\b/i.test(raw)) {
        const noteBody = raw.replace(/^(?:note|sticky\s*note|pin\s*note|add\s*note|chitthi|tippan)\s+/i, '').trim();
        if (noteBody) {
          entities.title = noteBody.charAt(0).toUpperCase() + noteBody.slice(1);
        }
      } else if (dt && dt.cleanTitle && dt.cleanTitle.length > 0) {
        entities.title = dt.cleanTitle;
      } else {
        const cleanTask = decoupled.replace(/\b(kharcha|expense|todo|task|remind|reminder|note|add|record|for|towards|create|set)\b/gi, '')
          .replace(/\b\d+(?:\.\d+)?\b/g, '').replace(/\s+/g, ' ').trim();
      }

      // Extract limit for Top/Bottom queries
      const limitMatch = raw.match(/\b(?:top|bottom|best|least|first|last)\s+(\d+)\b/i) || raw.match(/\b(\d+)\s+(?:top|bottom|best|items?|customers?|products?)\b/i);
      if (limitMatch) {
        entities.limit = parseInt(limitMatch[1], 10);
      }

      // Extract Threshold and Operator (e.g., "invoices over 50000", "bills above 10000", "dues > 5000")
      const gtMatch = raw.match(/\b(?:over|above|greater\s*than|more\s*than|>|>=)\s+(?:₹|rs\.?|inr)?\s*(\d+(?:,\d+)*(?:\.\d+)?)\b/i);
      const ltMatch = raw.match(/\b(?:under|below|less\s*than|<|<=)\s+(?:₹|rs\.?|inr)?\s*(\d+(?:,\d+)*(?:\.\d+)?)\b/i);
      if (gtMatch) {
        entities.threshold = parseFloat(gtMatch[1].replace(/,/g, ''));
        entities.thresholdOp = 'GT';
      } else if (ltMatch) {
        entities.threshold = parseFloat(ltMatch[1].replace(/,/g, ''));
        entities.thresholdOp = 'LT';
      }

      // Extract Aging Days
      const agingMatch = raw.match(/\b(?:older\s*than|more\s*than|>)\s*(\d+)\s*days?\b/i);
      if (agingMatch) {
        entities.agingDays = parseInt(agingMatch[1], 10);
      }

      return entities;
    }
  };

  // ─────────────────────────────────────────────────────────────────────────────
  // 4.2 DETERMINISTIC DATE ARITHMETIC & CALENDAR CALCULATOR ENGINE
  // ─────────────────────────────────────────────────────────────────────────────
  const DateMathEngine = {
    formatDate(d) {
      if (!d || isNaN(d.getTime())) return { formatted: '', iso: '' };
      const day = d.getDate();
      const monthNames = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
      const dayNames = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
      const mStr = monthNames[d.getMonth()];
      const yStr = d.getFullYear();
      const dayName = dayNames[d.getDay()];
      const yyyymmdd = `${yStr}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
      return {
        formatted: `${day} ${mStr} ${yStr} (${dayName})`,
        iso: yyyymmdd,
        dateObj: d,
        dayName,
        day,
        month: mStr,
        year: yStr
      };
    },

    evaluate(raw, refDate = new Date()) {
      if (!raw) return null;
      const q = raw.trim().toLowerCase();

      // 1. Days between two dates: "days between 1 Jan and 15 Jan", "days from 10 Oct to 25 Dec", "difference between 2026-05-01 and 2026-05-20"
      const betweenMatch = /(?:days\s+between|days\s+from|difference\s+between|how\s+many\s+days\s+(?:between|from))\s+(.+?)\s+(?:and|to)\s+(.+)/i.exec(q);
      if (betweenMatch) {
        const d1 = RoleResolver.extractDateTime(betweenMatch[1], refDate);
        const d2 = RoleResolver.extractDateTime(betweenMatch[2], refDate);
        if (d1 && d2) {
          const diffTime = Math.abs(d2.targetDate.getTime() - d1.targetDate.getTime());
          const diffDays = Math.round(diffTime / (1000 * 60 * 60 * 24));
          const f1 = this.formatDate(d1.targetDate);
          const f2 = this.formatDate(d2.targetDate);
          return {
            type: 'DATE_DIFF',
            days: diffDays,
            from: f1.formatted,
            to: f2.formatted,
            result: diffDays,
            summaryText: `${diffDays} days between ${f1.formatted} and ${f2.formatted}`
          };
        }
      }

      // 2. Calendar / Fiscal Period Anchors: "end of month", "end of current quarter", "q1 end", "q2 end", "fy end"
      if (/\b(?:end\s+of\s+(?:this\s+)?month|month\s+end)\b/i.test(q)) {
        const endOfMonth = new Date(refDate.getFullYear(), refDate.getMonth() + 1, 0);
        const f = this.formatDate(endOfMonth);
        return {
          type: 'DATE_ANCHOR',
          date: f.formatted,
          iso: f.iso,
          result: f.iso,
          summaryText: `End of Month: ${f.formatted}`
        };
      }
      if (/\b(?:end\s+of\s+(?:current\s+|this\s+)?quarter|quarter\s+end|q[1-4]\s+end)\b/i.test(q)) {
        let curQuarter = Math.floor(refDate.getMonth() / 3);
        const qMatch = q.match(/\bq([1-4])\s+end\b/i);
        if (qMatch) {
          curQuarter = parseInt(qMatch[1], 10) - 1;
        }
        const endOfQuarter = new Date(refDate.getFullYear(), (curQuarter + 1) * 3, 0);
        const f = this.formatDate(endOfQuarter);
        return {
          type: 'DATE_ANCHOR',
          date: f.formatted,
          iso: f.iso,
          result: f.iso,
          summaryText: `End of Q${curQuarter + 1}: ${f.formatted}`
        };
      }
      if (/\b(?:financial\s*year\s*end|fy\s*end)\b/i.test(q)) {
        const year = refDate.getMonth() >= 2 ? refDate.getFullYear() + 1 : refDate.getFullYear();
        const endOfFy = new Date(year, 2, 31); // 31st March
        const f = this.formatDate(endOfFy);
        return {
          type: 'DATE_ANCHOR',
          date: f.formatted,
          iso: f.iso,
          result: f.iso,
          summaryText: `Financial Year End (FY ${year - 1}-${String(year).slice(-2)}): ${f.formatted}`
        };
      }

      // 3. Date + / - offsets: "today + 15 days", "15 Aug + 30 days", "today - 7 days", "due in 30 days"
      const dateOpMatch = /(.+?)\s*([\+\-])\s*(\d+)\s*(days?|din|weeks?|hafta|months?|mahine?|years?|saal)\b/i.exec(q) ||
                          /(?:due\s+in|after|in)\s+(\d+)\s*(days?|din|weeks?|hafta|months?|mahine?)\b/i.exec(q) ||
                          /(\d+)\s*(days?|din|weeks?|hafta|months?|mahine?)\s*(?:from\s+today|after\s+today|ago|before\s+today)\b/i.exec(q);

      if (dateOpMatch) {
        let baseDate = new Date(refDate.getTime());
        let op = '+';
        let num = 0;
        let unit = 'days';

        if (dateOpMatch[2] === '+' || dateOpMatch[2] === '-') {
          const datePart = dateOpMatch[1].trim();
          const parsed = RoleResolver.extractDateTime(datePart, refDate);
          if (parsed) baseDate = parsed.targetDate;
          op = dateOpMatch[2];
          num = parseInt(dateOpMatch[3], 10);
          unit = dateOpMatch[4].toLowerCase();
        } else if (/due|after|in/.test(dateOpMatch[0])) {
          num = parseInt(dateOpMatch[1], 10);
          unit = dateOpMatch[2].toLowerCase();
          op = '+';
        } else if (/ago|before/.test(dateOpMatch[0])) {
          num = parseInt(dateOpMatch[1], 10);
          unit = dateOpMatch[2].toLowerCase();
          op = '-';
        } else {
          num = parseInt(dateOpMatch[1], 10);
          unit = dateOpMatch[2].toLowerCase();
          op = '+';
        }

        const multiplier = op === '-' ? -1 : 1;
        const target = new Date(baseDate.getTime());
        if (/days?|din/.test(unit)) {
          target.setDate(target.getDate() + num * multiplier);
        } else if (/weeks?|hafta/.test(unit)) {
          target.setDate(target.getDate() + num * 7 * multiplier);
        } else if (/months?|mahine?/.test(unit)) {
          target.setMonth(target.getMonth() + num * multiplier);
        } else if (/years?|saal/.test(unit)) {
          target.setFullYear(target.getFullYear() + num * multiplier);
        }

        const f = this.formatDate(target);
        return {
          type: 'DATE_CALC',
          date: f.formatted,
          iso: f.iso,
          days: num,
          result: f.iso,
          summaryText: `${f.formatted} (${op === '+' ? '+' : '-'}${num} ${unit})`
        };
      }

      return null;
    }
  };

  // ─────────────────────────────────────────────────────────────────────────────
  // 4.5 COMPREHENSIVE DETERMINISTIC SAFE ARITHMETIC & NLP MATH ENGINE
  // ─────────────────────────────────────────────────────────────────────────────
  const SafeArithmeticEngine = {
    // Math synonyms dictionary for natural language conversion
    synonyms: {
      'plus': '+', 'add': '+', 'added': '+', 'addition': '+', 'adhik': '+', 'jod': '+', 'and': '+',
      'minus': '-', 'sub': '-', 'subtract': '-', 'subtracted': '-', 'deduct': '-', 'deducted': '-', 'less': '-', 'vaja': '-', 'ghatao': '-', 'kam': '-', 'kami': '-',
      'times': '*', 'multiply': '*', 'multiplied': '*', 'into': '*', 'guna': '*', 'gunile': '*', 'x': '*', '×': '*',
      'divide': '/', 'divided': '/', 'over': '/', 'bhaag': '/', 'bhagile': '/', '÷': '/',
      'mod': '%', 'modulo': '%', 'remainder': '%', 'baki': '%',
      'power': '^', 'pow': '^', 'raised': '^',
    },

    // Format Indian Number with commas & decimals
    formatIndianNumber(num) {
      if (num == null || isNaN(num)) return '0';
      const isNegative = num < 0;
      const absVal = Math.abs(num);
      const rounded = Math.round(absVal * 100) / 100;
      const parts = rounded.toString().split('.');
      let intPart = parts[0];
      const decPart = parts.length > 1 ? '.' + parts[1] : '';

      // Indian comma format
      const lastThree = intPart.slice(-3);
      const rest = intPart.slice(0, -3);
      const formattedInt = rest ? rest.replace(/\B(?=(\d{2})+(?!\d))/g, ',') + ',' + lastThree : lastThree;

      return (isNegative ? '-' : '') + formattedInt + decPart;
    },

    // Tokenizer for mathematical expression
    tokenize(exprStr, ctx = {}) {
      if (!exprStr) return null;
      let s = exprStr.trim();

      // Pre-normalize ans / previous result tokens from session context
      const firmId = ctx.activeFirmId || ctx.firmId || (ctx.firm && ctx.firm.id) || 'default';
      const sessionCtx = ctx.sessionContext || SessionContextManager.getContext(firmId);
      const ansVal = (sessionCtx && sessionCtx.ans != null) ? sessionCtx.ans : 0;
      s = s.replace(/\bans\b/gi, String(ansVal));

      // Pre-normalize currency symbols, Indian commas, and unicode operators
      s = s.replace(/,/g, '');
      s = s.replace(/[₹$€£]|(?:rs\.?|inr)\s*/gi, '');
      s = s.replace(/×/g, '*').replace(/÷/g, '/').replace(/\*\*/g, '^');

      // Pre-normalize Devanagari digits
      const devDigits = { '०': '0', '१': '1', '२': '2', '३': '3', '४': '4', '५': '5', '६': '6', '७': '7', '८': '8', '९': '9' };
      s = s.replace(/[०-९]/g, d => devDigits[d] || d);

      // Replace word numbers (e.g. "dedh lakh" -> "150000")
      s = s.replace(/\bdedh\s*(?:lakh|lakhs|lac|lacs)\b/gi, '150000')
           .replace(/\bdhai\s*(?:lakh|lakhs|lac|lacs)\b/gi, '250000')
           .replace(/\bsawa\s*(?:lakh|lakhs|lac|lacs)\b/gi, '125000')
           .replace(/\bpaune\s*(?:lakh|lakhs|lac|lacs)\b/gi, '75000')
           .replace(/\baadha\s*(?:lakh|lakhs|lac|lacs)\b/gi, '50000')
           .replace(/\bsawa\s*do\s*(?:lakh|lakhs|lac|lacs)\b/gi, '225000')
           .replace(/\bpaune\s*do\s*(?:lakh|lakhs|lac|lacs)\b/gi, '175000')
           .replace(/\bsawa\s*teen\s*(?:lakh|lakhs|lac|lacs)\b/gi, '325000')
           .replace(/\bpaune\s*teen\s*(?:lakh|lakhs|lac|lacs)\b/gi, '275000')
           .replace(/\bsawa\s*char\s*(?:lakh|lakhs|lac|lacs)\b/gi, '425000')
           .replace(/\bpaune\s*char\s*(?:lakh|lakhs|lac|lacs)\b/gi, '375000')
           .replace(/\bdedh\s*(?:crore|cr|crores)\b/gi, '15000000')
           .replace(/\bdhai\s*(?:crore|cr|crores)\b/gi, '25000000')
           .replace(/\bsawa\s*(?:crore|cr|crores)\b/gi, '12500000')
           .replace(/\bpaune\s*(?:crore|cr|crores)\b/gi, '7500000')
           .replace(/\baadha\s*(?:crore|cr|crores)\b/gi, '5000000')
           .replace(/\bdedh\s*(?:hazar|hazaar|haz|thousand)\b/gi, '1500')
           .replace(/\bdhai\s*(?:hazar|hazaar|haz|thousand)\b/gi, '2500')
           .replace(/\bsawa\s*(?:hazar|hazaar|haz|thousand)\b/gi, '1250')
           .replace(/\bpaune\s*(?:hazar|hazaar|haz|thousand)\b/gi, '750')
           .replace(/\bsawa\s*do\s*(?:hazar|hazaar|haz|thousand)\b/gi, '2250')
           .replace(/\bpaune\s*do\s*(?:hazar|hazaar|haz|thousand)\b/gi, '1750')
           .replace(/\bsawa\s*teen\s*(?:hazar|hazaar|haz|thousand)\b/gi, '3250')
           .replace(/\bpaune\s*teen\s*(?:hazar|hazaar|haz|thousand)\b/gi, '2750')
           // Compound patterns like "2 lakh 50 thousand" or "5 crore 20 lakh"
           .replace(/\b(\d+(?:\.\d+)?)\s*(?:lakh|lakhs|lac|lacs)\s*(\d+(?:\.\d+)?)\s*(?:hazar|hazaar|haz|thousand|k)\b/gi, (m, v1, v2) => String(parseFloat(v1) * 100000 + parseFloat(v2) * 1000))
           .replace(/\b(\d+(?:\.\d+)?)\s*(?:cr|crore|crores)\s*(\d+(?:\.\d+)?)\s*(?:lakh|lakhs|lac|lacs)\b/gi, (m, v1, v2) => String(parseFloat(v1) * 10000000 + parseFloat(v2) * 100000))
           .replace(/\b(\d+(?:\.\d+)?)\s*(?:lakh|lakhs|lac|lacs)\b/gi, (m, v) => String(parseFloat(v) * 100000))
           .replace(/\b(\d+(?:\.\d+)?)\s*(?:cr|crore|crores)\b/gi, (m, v) => String(parseFloat(v) * 10000000))
           .replace(/\b(\d+(?:\.\d+)?)\s*(?:k|hazar|hazaar)\b/gi, (m, v) => String(parseFloat(v) * 1000))
           .replace(/\b(\d+(?:\.\d+)?)\s*(?:m|million)\b/gi, (m, v) => String(parseFloat(v) * 1000000))
           .replace(/\b(\d+(?:\.\d+)?)\s*(?:b|billion)\b/gi, (m, v) => String(parseFloat(v) * 1000000000));

      // Token patterns
      const tokens = [];
      let i = 0;
      while (i < s.length) {
        const ch = s[i];
        if (/\s/.test(ch)) {
          i++;
          continue;
        }

        if (ch === '(' || ch === '[' || ch === '{') {
          tokens.push({ type: 'LPAREN', val: '(' });
          i++;
          continue;
        }
        if (ch === ')' || ch === ']' || ch === '}') {
          tokens.push({ type: 'RPAREN', val: ')' });
          i++;
          continue;
        }
        if (ch === '+' || ch === '-' || ch === '*' || ch === '/' || ch === '%' || ch === '^') {
          tokens.push({ type: 'OP', val: ch });
          i++;
          continue;
        }

        // Numbers (digits + decimal point)
        if (/\d/.test(ch) || (ch === '.' && /\d/.test(s[i + 1] || ''))) {
          let numStr = '';
          while (i < s.length && (/[\d.]/.test(s[i]))) {
            numStr += s[i];
            i++;
          }
          const numVal = parseFloat(numStr);
          if (isNaN(numVal)) return null;

          // Check if followed by % (e.g. "17%" or "17 %")
          let j = i;
          while (j < s.length && /\s/.test(s[j])) j++;
          if (s[j] === '%' || s.slice(j, j + 7).toLowerCase() === 'percent') {
            i = s[j] === '%' ? j + 1 : j + 7;
            tokens.push({ type: 'PERCENT', val: numVal });
          } else {
            tokens.push({ type: 'NUM', val: numVal });
          }
          continue;
        }

        // Word tokens (e.g. "of", "plus", "minus", "times", "sqrt", "mod", etc.)
        if (/[a-zA-Z\u0900-\u097F]/.test(ch)) {
          let word = '';
          while (i < s.length && /[a-zA-Z\u0900-\u097F]/.test(s[i])) {
            word += s[i];
            i++;
          }
          word = word.toLowerCase();

          if (word === 'of' || word === 'ka' || word === 'ki' || word === 'ke' || word === 'cha' || word === 'chi' || word === 'che') {
            tokens.push({ type: 'OF', val: 'of' });
          } else if (word === 'by' || word === 'with' || word === 'from' || word === 'in' || word === 'to' || word === 'pe' || word === 'se' || word === 'ne') {
            // Connector preposition - safe to ignore in expression
            continue;
          } else if (word === 'sqrt' || word === 'root') {
            tokens.push({ type: 'SQRT', val: 'sqrt' });
          } else if (word === 'percent' || word === 'percentage' || word === 'pct' || word === 'takke' || word === 'pratishat') {
            // Previous number is percent
            if (tokens.length > 0 && tokens[tokens.length - 1].type === 'NUM') {
              const last = tokens.pop();
              tokens.push({ type: 'PERCENT', val: last.val });
            }
          } else if (this.synonyms[word]) {
            tokens.push({ type: 'OP', val: this.synonyms[word] });
          } else {
            // Unrecognized alpha token in mathematical expression
            return null;
          }
          continue;
        }

        // Unexpected character
        return null;
      }

      return tokens;
    },

    // Evaluates token stream using Shunting-Yard & AST Stack evaluation
    evaluateTokens(tokens) {
      if (!tokens || tokens.length === 0) return null;

      // 1. Handle Percentage "OF" patterns & Unary operators
      const sanitized = [];
      for (let i = 0; i < tokens.length; i++) {
        const t = tokens[i];
        if (t.type === 'PERCENT') {
          // If followed by OF or NUM or LPAREN: "17% of 5633" -> 17 * 0.01 * 5633
          if (i + 1 < tokens.length && (tokens[i + 1].type === 'OF' || tokens[i + 1].type === 'NUM' || tokens[i + 1].type === 'LPAREN')) {
            sanitized.push({ type: 'NUM', val: t.val / 100 });
            sanitized.push({ type: 'OP', val: '*' });
            if (tokens[i + 1].type === 'OF') i++; // skip 'OF'
            continue;
          }
          sanitized.push(t);
        } else if (t.type === 'OF') {
          sanitized.push({ type: 'OP', val: '*' });
        } else if (t.type === 'OP' && (t.val === '-' || t.val === '+')) {
          // Check if unary
          const prev = sanitized[sanitized.length - 1];
          if (!prev || prev.type === 'OP' || prev.type === 'LPAREN') {
            sanitized.push({ type: 'UNARY_OP', val: t.val });
          } else {
            sanitized.push(t);
          }
        } else {
          sanitized.push(t);
        }
      }

      // 2. Convert Infix Percentage: "A + B%" -> A + (A * B / 100), "A - B%" -> A - (A * B / 100), "A * B%" -> A * (B / 100)
      const finalTokens = [];
      for (let i = 0; i < sanitized.length; i++) {
        const t = sanitized[i];
        if (t.type === 'PERCENT') {
          const prevOp = finalTokens.length >= 2 ? finalTokens[finalTokens.length - 1] : null;
          if (prevOp && prevOp.type === 'OP' && (prevOp.val === '+' || prevOp.val === '-')) {
            const baseToken = finalTokens[finalTokens.length - 2];
            const baseVal = (baseToken && baseToken.type === 'NUM') ? baseToken.val : 1;
            finalTokens.push({ type: 'NUM', val: (baseVal * t.val) / 100 });
          } else {
            finalTokens.push({ type: 'NUM', val: t.val / 100 });
          }
        } else {
          finalTokens.push(t);
        }
      }

      // 3. Shunting-Yard Algorithm to convert to Postfix (RPN)
      const outputQueue = [];
      const opStack = [];

      const precedence = {
        '+': 1, '-': 1,
        '*': 2, '/': 2, '%': 2,
        '^': 3,
        'UNARY': 4,
        'SQRT': 4
      };

      for (let i = 0; i < finalTokens.length; i++) {
        const t = finalTokens[i];

        if (t.type === 'NUM') {
          outputQueue.push(t);
        } else if (t.type === 'SQRT' || t.type === 'UNARY_OP') {
          opStack.push({ type: t.type, val: t.val, prec: 4 });
        } else if (t.type === 'OP') {
          const p1 = precedence[t.val] || 0;
          while (opStack.length > 0) {
            const top = opStack[opStack.length - 1];
            if (top.type === 'LPAREN') break;
            const p2 = top.prec || precedence[top.val] || 0;
            // Right-associative for '^'
            if ((t.val === '^' && p1 < p2) || (t.val !== '^' && p1 <= p2)) {
              outputQueue.push(opStack.pop());
            } else {
              break;
            }
          }
          opStack.push({ type: 'OP', val: t.val, prec: p1 });
        } else if (t.type === 'LPAREN') {
          opStack.push(t);
        } else if (t.type === 'RPAREN') {
          let foundLparen = false;
          while (opStack.length > 0) {
            const top = opStack.pop();
            if (top.type === 'LPAREN') {
              foundLparen = true;
              break;
            }
            outputQueue.push(top);
          }
          if (!foundLparen) return null; // Mismatched parentheses
        }
      }

      while (opStack.length > 0) {
        const top = opStack.pop();
        if (top.type === 'LPAREN' || top.type === 'RPAREN') return null; // Mismatched
        outputQueue.push(top);
      }

      // 4. Evaluate RPN Queue
      const evalStack = [];
      for (const t of outputQueue) {
        if (t.type === 'NUM') {
          evalStack.push(t.val);
        } else if (t.type === 'UNARY_OP') {
          if (evalStack.length < 1) return null;
          const a = evalStack.pop();
          evalStack.push(t.val === '-' ? -a : +a);
        } else if (t.type === 'SQRT') {
          if (evalStack.length < 1) return null;
          const a = evalStack.pop();
          if (a < 0) return null;
          evalStack.push(Math.sqrt(a));
        } else if (t.type === 'OP') {
          if (evalStack.length < 2) return null;
          const b = evalStack.pop();
          const a = evalStack.pop();
          let res = 0;
          switch (t.val) {
            case '+': res = a + b; break;
            case '-': res = a - b; break;
            case '*': res = a * b; break;
            case '/':
              if (b === 0) return null; // Safe division by zero prevention
              res = a / b;
              break;
            case '%':
              if (b === 0) return null;
              res = a % b;
              break;
            case '^':
              res = Math.pow(a, b);
              break;
            default:
              return null;
          }
          evalStack.push(res);
        }
      }

      if (evalStack.length !== 1) return null;
      const finalResult = evalStack[0];
      if (typeof finalResult !== 'number' || isNaN(finalResult) || !isFinite(finalResult)) {
        return null;
      }

      return finalResult;
    },

    // Evaluates natural language queries & pattern expressions
    evaluate(rawQuery, ctx = {}) {
      if (!rawQuery || typeof rawQuery !== 'string') return null;
      let q = rawQuery.trim();

      // Pre-substitute ans from session context
      const firmId = ctx.activeFirmId || ctx.firmId || (ctx.firm && ctx.firm.id) || 'default';
      const sessionCtx = ctx.sessionContext || SessionContextManager.getContext(firmId);
      const ansVal = (sessionCtx && sessionCtx.ans != null) ? sessionCtx.ans : 0;
      q = q.replace(/\bans\b/gi, String(ansVal));

      // 1. Strip natural language query triggers
      q = q.replace(/^(?:what is the|what is|whats|how much is|calculate|compute|solve|find the|find|eval|value of|result of|batao|sanga|sang|mala sanga|kitna hoga|kitna hai)\s+/gi, '').trim();

      // 2. High-Confidence Pattern 1: Percentage of Base
      // "17% of 5633", "17 percent of 5633", "5633 ka 17%", "5633 cha 17%", "17% of ₹5,633"
      let m = /^(\d+(?:,\d+)*(?:\.\d+)?)\s*(?:%|percent|percentage|pct|takke|pratishat)\s*(?:of|ka|ki|ke|cha|chi|che|mein se|from|on)\s*(?:₹|rs\.?|inr)?\s*(\d+(?:,\d+)*(?:\.\d+)?)$/i.exec(q) ||
              /^(?:₹|rs\.?|inr)?\s*(\d+(?:,\d+)*(?:\.\d+)?)\s*(?:ka|ki|ke|cha|chi|che)\s*(\d+(?:,\d+)*(?:\.\d+)?)\s*(?:%|percent|percentage|pct|takke|pratishat)$/i.exec(q);
      if (m) {
        const rate = parseFloat(m[1].replace(/,/g, ''));
        const base = parseFloat(m[2].replace(/,/g, ''));
        if (!isNaN(rate) && !isNaN(base)) {
          const res = Math.round(((base * rate) / 100) * 100) / 100;
          return {
            calculationType: 'PERCENT_OF',
            operands: { rate, base },
            result: res,
            formattedResult: this.formatIndianNumber(res),
            expression: `${rate}% of ₹${this.formatIndianNumber(base)}`,
            summaryText: `${rate}% of ₹${this.formatIndianNumber(base)} = ₹${this.formatIndianNumber(res)}`
          };
        }
      }

      // 3. High-Confidence Pattern 2: Percentage Increase / Addition / Markup
      // "5633 + 17%", "5633 plus 17%", "add 17% to 5633", "17% increase on 5633", "increase 5633 by 17%", "5633 mein 17% add karo", "5000 मध्ये 10% वाढ"
      let incBase = null, incRate = null;
      let incMatch = /^(?:increase|raise)\s*(?:₹|rs\.?|inr)?\s*(\d+(?:,\d+)*(?:\.\d+)?)\s*(?:by)\s*(\d+(?:,\d+)*(?:\.\d+)?)\s*(?:%|percent|percentage)$/i.exec(q);
      if (incMatch) {
        incBase = parseFloat(incMatch[1].replace(/,/g, ''));
        incRate = parseFloat(incMatch[2].replace(/,/g, ''));
      }
      if (incBase == null) {
        incMatch = /^(?:add|increase)\s*(\d+(?:,\d+)*(?:\.\d+)?)\s*(?:%|percent|percentage)\s*(?:to|on|in)\s*(?:₹|rs\.?|inr)?\s*(\d+(?:,\d+)*(?:\.\d+)?)$/i.exec(q) ||
                   /^(\d+(?:,\d+)*(?:\.\d+)?)\s*(?:%|percent|percentage)\s*(?:increase|markup|hike|vadh|वाढ)\s*(?:on|to|in|of)\s*(?:₹|rs\.?|inr)?\s*(\d+(?:,\d+)*(?:\.\d+)?)$/i.exec(q);
        if (incMatch) {
          incRate = parseFloat(incMatch[1].replace(/,/g, ''));
          incBase = parseFloat(incMatch[2].replace(/,/g, ''));
        }
      }
      if (incBase == null) {
        incMatch = /^(?:₹|rs\.?|inr)?\s*(\d+(?:,\d+)*(?:\.\d+)?)\s*(?:\+|plus|add|added to|increase by|with|mein|मध्ये)\s*(\d+(?:,\d+)*(?:\.\d+)?)\s*(?:%|percent|percentage)\s*(?:add|karo|vadhva|वाढ)?$/i.exec(q) ||
                   /^(?:₹|rs\.?|inr)?\s*(\d+(?:,\d+)*(?:\.\d+)?)\s*(?:mein|मध्ये)\s*(\d+(?:,\d+)*(?:\.\d+)?)\s*(?:%|percent|percentage)\s*(?:add|karo|vadh|वाढ|plus|\+)?\s*(?:karo|kara)?$/i.exec(q);
        if (incMatch) {
          incBase = parseFloat(incMatch[1].replace(/,/g, ''));
          incRate = parseFloat(incMatch[2].replace(/,/g, ''));
        }
      }
      if (incBase != null && incRate != null && !isNaN(incBase) && !isNaN(incRate)) {
        const delta = Math.round(((incBase * incRate) / 100) * 100) / 100;
        const res = Math.round((incBase + delta) * 100) / 100;
        return {
          calculationType: 'PERCENT_INCREASE',
          operands: { base: incBase, rate: incRate, delta },
          result: res,
          formattedResult: this.formatIndianNumber(res),
          expression: `₹${this.formatIndianNumber(incBase)} + ${incRate}%`,
          summaryText: `₹${this.formatIndianNumber(incBase)} + ${incRate}% = ₹${this.formatIndianNumber(res)} (+₹${this.formatIndianNumber(delta)})`
        };
      }

      // 4. High-Confidence Pattern 3: Percentage Decrease / Discount / Reduction
      // "5633 - 17%", "5633 less 17%", "reduce 5633 by 17%", "take 17 percent off 5633", "17% discount on 5633", "5633 se 17% kam karo", "5000 madhun 10 percent kami"
      let decBase = null, decRate = null;
      let decMatch = /^(?:reduce|decrease|deduct|less)\s*(?:₹|rs\.?|inr)?\s*(\d+(?:,\d+)*(?:\.\d+)?)\s*(?:by)\s*(\d+(?:,\d+)*(?:\.\d+)?)\s*(?:%|percent|percentage)$/i.exec(q);
      if (decMatch) {
        decBase = parseFloat(decMatch[1].replace(/,/g, ''));
        decRate = parseFloat(decMatch[2].replace(/,/g, ''));
      }
      if (decBase == null) {
        decMatch = /^(?:reduce|deduct|discount|take off|take)\s*(?:₹|rs\.?|inr)?\s*(\d+(?:,\d+)*(?:\.\d+)?)\s*(?:%|percent|percentage)\s*(?:from|off|on|of)\s*(?:₹|rs\.?|inr)?\s*(\d+(?:,\d+)*(?:\.\d+)?)$/i.exec(q) ||
                   /^(\d+(?:,\d+)*(?:\.\d+)?)\s*(?:%|percent|percentage)\s*(?:discount|off|rebate|chut|kam|kami|कमी|less|reduction|decrease)\s*(?:on|from|in|of)\s*(?:₹|rs\.?|inr)?\s*(\d+(?:,\d+)*(?:\.\d+)?)$/i.exec(q);
        if (decMatch) {
          decRate = parseFloat(decMatch[1].replace(/,/g, ''));
          decBase = parseFloat(decMatch[2].replace(/,/g, ''));
        }
      }
      if (decBase == null) {
        decMatch = /^(?:₹|rs\.?|inr)?\s*(\d+(?:,\d+)*(?:\.\d+)?)\s*(?:-|minus|less|subtract|deduct|reduce by|reduced by|se|madhun|मधून)\s*(\d+(?:,\d+)*(?:\.\d+)?)\s*(?:%|percent|percentage)\s*(?:kam karo|kami|ghatao|कमी|kam|less)?$/i.exec(q) ||
                   /^(?:₹|rs\.?|inr)?\s*(\d+(?:,\d+)*(?:\.\d+)?)\s*(?:se|madhun|मधून)\s*(\d+(?:,\d+)*(?:\.\d+)?)\s*(?:%|percent|percentage)\s*(?:kam|kami|कमी|ghatao|vaja|minus|-)\s*(?:karo|kara)?$/i.exec(q);
        if (decMatch) {
          decBase = parseFloat(decMatch[1].replace(/,/g, ''));
          decRate = parseFloat(decMatch[2].replace(/,/g, ''));
        }
      }
      if (decBase != null && decRate != null && !isNaN(decBase) && !isNaN(decRate)) {
        const delta = Math.round(((decBase * decRate) / 100) * 100) / 100;
        const res = Math.round((decBase - delta) * 100) / 100;
        return {
          calculationType: 'PERCENT_DECREASE',
          operands: { base: decBase, rate: decRate, delta },
          result: res,
          formattedResult: this.formatIndianNumber(res),
          expression: `₹${this.formatIndianNumber(decBase)} - ${decRate}%`,
          summaryText: `₹${this.formatIndianNumber(decBase)} - ${decRate}% = ₹${this.formatIndianNumber(res)} (-₹${this.formatIndianNumber(delta)})`
        };
      }

      // 5. Natural Language Powers, Roots, Fractions
      // "square of 25", "25 squared", "cube of 12", "square root of 144", "half of 15000", "quarter of 50000", "double of 8500", "triple of 4200"
      m = /^(?:square of|varg of|square)\s+(\d+(?:,\d+)*(?:\.\d+)?)/i.exec(q) || /^(\d+(?:,\d+)*(?:\.\d+)?)\s*(?:squared|varg)\b/i.exec(q);
      if (m) {
        const num = parseFloat(m[1].replace(/,/g, ''));
        const res = Math.round((num * num) * 100) / 100;
        return {
          calculationType: 'SQUARE',
          operands: { num },
          result: res,
          formattedResult: this.formatIndianNumber(res),
          expression: `${num}²`,
          summaryText: `Square of ${this.formatIndianNumber(num)} = ${this.formatIndianNumber(res)}`
        };
      }

      m = /^(?:cube of|ghan of|cube)\s+(\d+(?:,\d+)*(?:\.\d+)?)/i.exec(q) || /^(\d+(?:,\d+)*(?:\.\d+)?)\s*(?:cubed|ghan)\b/i.exec(q);
      if (m) {
        const num = parseFloat(m[1].replace(/,/g, ''));
        const res = Math.round((num * num * num) * 100) / 100;
        return {
          calculationType: 'CUBE',
          operands: { num },
          result: res,
          formattedResult: this.formatIndianNumber(res),
          expression: `${num}³`,
          summaryText: `Cube of ${this.formatIndianNumber(num)} = ${this.formatIndianNumber(res)}`
        };
      }

      m = /^(?:square root of|sqrt of|root of|sqrt)\s+(\d+(?:,\d+)*(?:\.\d+)?)/i.exec(q);
      if (m) {
        const num = parseFloat(m[1].replace(/,/g, ''));
        const res = Math.round((Math.sqrt(num)) * 10000) / 10000;
        return {
          calculationType: 'SQRT',
          operands: { num },
          result: res,
          formattedResult: this.formatIndianNumber(res),
          expression: `√${num}`,
          summaryText: `Square Root of ${this.formatIndianNumber(num)} = ${this.formatIndianNumber(res)}`
        };
      }

      m = /^(?:half of|aadha of|ardha of|aadha|ardha)\s+(?:₹|rs\.?|inr)?\s*(\d+(?:,\d+)*(?:\.\d+)?)/i.exec(q) ||
          /^(?:₹|rs\.?|inr)?\s*(\d+(?:,\d+)*(?:\.\d+)?)\s*(?:ka aadha|cha ardha|cha nimma)\b/i.exec(q);
      if (m) {
        const num = parseFloat(m[1].replace(/,/g, ''));
        const res = Math.round((num * 0.5) * 100) / 100;
        return {
          calculationType: 'HALF',
          operands: { num },
          result: res,
          formattedResult: this.formatIndianNumber(res),
          expression: `Half of ₹${this.formatIndianNumber(num)}`,
          summaryText: `Half of ₹${this.formatIndianNumber(num)} = ₹${this.formatIndianNumber(res)}`
        };
      }

      m = /^(?:quarter of|paav of|paav)\s+(?:₹|rs\.?|inr)?\s*(\d+(?:,\d+)*(?:\.\d+)?)/i.exec(q) ||
          /^(?:₹|rs\.?|inr)?\s*(\d+(?:,\d+)*(?:\.\d+)?)\s*(?:ka paav|cha paav)\b/i.exec(q);
      if (m) {
        const num = parseFloat(m[1].replace(/,/g, ''));
        const res = Math.round((num * 0.25) * 100) / 100;
        return {
          calculationType: 'QUARTER',
          operands: { num },
          result: res,
          formattedResult: this.formatIndianNumber(res),
          expression: `Quarter of ₹${this.formatIndianNumber(num)}`,
          summaryText: `Quarter of ₹${this.formatIndianNumber(num)} = ₹${this.formatIndianNumber(res)}`
        };
      }

      m = /^(?:double of|dugna of|dudha of|dugna|dudha)\s+(?:₹|rs\.?|inr)?\s*(\d+(?:,\d+)*(?:\.\d+)?)/i.exec(q) ||
          /^(?:₹|rs\.?|inr)?\s*(\d+(?:,\d+)*(?:\.\d+)?)\s*(?:ka dugna|chi pat|double)\b/i.exec(q);
      if (m) {
        const num = parseFloat(m[1].replace(/,/g, ''));
        const res = Math.round((num * 2) * 100) / 100;
        return {
          calculationType: 'DOUBLE',
          operands: { num },
          result: res,
          formattedResult: this.formatIndianNumber(res),
          expression: `Double of ₹${this.formatIndianNumber(num)}`,
          summaryText: `Double of ₹${this.formatIndianNumber(num)} = ₹${this.formatIndianNumber(res)}`
        };
      }

      m = /^(?:triple of|tigna of|tigna)\s+(?:₹|rs\.?|inr)?\s*(\d+(?:,\d+)*(?:\.\d+)?)/i.exec(q) ||
          /^(?:₹|rs\.?|inr)?\s*(\d+(?:,\d+)*(?:\.\d+)?)\s*(?:ka tigna|triple)\b/i.exec(q);
      if (m) {
        const num = parseFloat(m[1].replace(/,/g, ''));
        const res = Math.round((num * 3) * 100) / 100;
        return {
          calculationType: 'TRIPLE',
          operands: { num },
          result: res,
          formattedResult: this.formatIndianNumber(res),
          expression: `Triple of ₹${this.formatIndianNumber(num)}`,
          summaryText: `Triple of ₹${this.formatIndianNumber(num)} = ₹${this.formatIndianNumber(res)}`
        };
      }

      // 6. Natural Binary Operations: "divide 10000 by 4", "multiply 250 by 12", "add 500 and 1500", "subtract 300 from 1500", "5000 less 750"
      m = /^(?:divide|bhagile)\s+(?:₹|rs\.?|inr)?\s*(\d+(?:,\d+)*(?:\.\d+)?)\s+(?:by|over|with|in)\s+(\d+(?:,\d+)*(?:\.\d+)?)/i.exec(q) ||
          /^(?:₹|rs\.?|inr)?\s*(\d+(?:,\d+)*(?:\.\d+)?)\s*(?:ko|cha)\s*(\d+(?:,\d+)*(?:\.\d+)?)\s*(?:se\s*divide|divide|bhagile)/i.exec(q);
      if (m) {
        const a = parseFloat(m[1].replace(/,/g, ''));
        const b = parseFloat(m[2].replace(/,/g, ''));
        if (b !== 0) {
          const res = Math.round((a / b) * 100) / 100;
          return {
            calculationType: 'DIVISION',
            operands: { a, b },
            result: res,
            formattedResult: this.formatIndianNumber(res),
            expression: `${this.formatIndianNumber(a)} ÷ ${this.formatIndianNumber(b)}`,
            summaryText: `${this.formatIndianNumber(a)} ÷ ${this.formatIndianNumber(b)} = ${this.formatIndianNumber(res)}`
          };
        }
      }

      m = /^(?:multiply|guna|gunile)\s+(?:₹|rs\.?|inr)?\s*(\d+(?:,\d+)*(?:\.\d+)?)\s+(?:by|with|into|times|and)\s+(\d+(?:,\d+)*(?:\.\d+)?)/i.exec(q) ||
          /^(?:₹|rs\.?|inr)?\s*(\d+(?:,\d+)*(?:\.\d+)?)\s*(?:into|times|guna|gunile)\s*(\d+(?:,\d+)*(?:\.\d+)?)/i.exec(q);
      if (m) {
        const a = parseFloat(m[1].replace(/,/g, ''));
        const b = parseFloat(m[2].replace(/,/g, ''));
        const res = Math.round((a * b) * 100) / 100;
        return {
          calculationType: 'MULTIPLICATION',
          operands: { a, b },
          result: res,
          formattedResult: this.formatIndianNumber(res),
          expression: `${this.formatIndianNumber(a)} × ${this.formatIndianNumber(b)}`,
          summaryText: `${this.formatIndianNumber(a)} × ${this.formatIndianNumber(b)} = ${this.formatIndianNumber(res)}`
        };
      }

      m = /^(?:add|sum of)\s+(?:₹|rs\.?|inr)?\s*(\d+(?:,\d+)*(?:\.\d+)?)\s+(?:and|to|plus|\+)\s+(?:₹|rs\.?|inr)?\s*(\d+(?:,\d+)*(?:\.\d+)?)/i.exec(q);
      if (m) {
        const a = parseFloat(m[1].replace(/,/g, ''));
        const b = parseFloat(m[2].replace(/,/g, ''));
        const res = Math.round((a + b) * 100) / 100;
        return {
          calculationType: 'ADDITION',
          operands: { a, b },
          result: res,
          formattedResult: this.formatIndianNumber(res),
          expression: `${this.formatIndianNumber(a)} + ${this.formatIndianNumber(b)}`,
          summaryText: `${this.formatIndianNumber(a)} + ${this.formatIndianNumber(b)} = ${this.formatIndianNumber(res)}`
        };
      }

      m = /^(?:subtract|deduct)\s+(?:₹|rs\.?|inr)?\s*(\d+(?:,\d+)*(?:\.\d+)?)\s+(?:from)\s+(?:₹|rs\.?|inr)?\s*(\d+(?:,\d+)*(?:\.\d+)?)/i.exec(q);
      if (m) {
        const a = parseFloat(m[1].replace(/,/g, ''));
        const b = parseFloat(m[2].replace(/,/g, ''));
        const res = Math.round((b - a) * 100) / 100;
        return {
          calculationType: 'SUBTRACTION',
          operands: { a: b, b: a },
          result: res,
          formattedResult: this.formatIndianNumber(res),
          expression: `${this.formatIndianNumber(b)} - ${this.formatIndianNumber(a)}`,
          summaryText: `${this.formatIndianNumber(b)} - ${this.formatIndianNumber(a)} = ${this.formatIndianNumber(res)}`
        };
      }

      // 7. General Expression Parsing via Safe Shunting-Yard AST
      const tokens = this.tokenize(q, ctx);
      if (tokens && tokens.length > 0) {
        // Need at least one operator or function or expression to be a calculation (avoid bare numbers matching CRM IDs)
        const hasOp = tokens.some(t => t.type === 'OP' || t.type === 'PERCENT' || t.type === 'SQRT' || t.type === 'OF');
        if (hasOp) {
          const res = this.evaluateTokens(tokens);
          if (res !== null) {
            const rounded = Math.round((res + Number.EPSILON) * 1000000) / 1000000;
            return {
              calculationType: 'EXPRESSION',
              result: rounded,
              formattedResult: this.formatIndianNumber(rounded),
              expression: q,
              summaryText: `${q} = ${this.formatIndianNumber(rounded)}`
            };
          }
        }
      }

      return null;
    }
  };

  // ─────────────────────────────────────────────────────────────────────────────
  // 5. DETERMINISTIC MATHEMATICAL & FINANCIAL EVALUATORS
  // ─────────────────────────────────────────────────────────────────────────────
  const CalculationEvaluator = {
    evaluateArithmetic(rawQuery, ctx = {}) {
      return SafeArithmeticEngine.evaluate(rawQuery, ctx);
    },

    evaluateGST(entities, raw, ctx = {}) {
      const firmId = ctx.activeFirmId || ctx.firmId || (ctx.firm && ctx.firm.id) || 'default';
      const sessionCtx = ctx.sessionContext || SessionContextManager.getContext(firmId);
      const amt = (entities && entities.amount != null) ? entities.amount : (/\bans\b/i.test(raw) ? (sessionCtx && sessionCtx.ans != null ? sessionCtx.ans : 0) : 0);
      const rate = (entities && entities.rate != null) ? entities.rate : 18;
      const isInclusive = /\b(inclusive|reverse|included|with tax|with\s*(?:\d+%)?\s*gst|with\s*(?:\d+%)?\s*tax|shamil|incl|inc)\b/i.test(raw);

      let base, tax, total;
      if (isInclusive) {
        base = amt / (1 + (rate / 100));
        tax = amt - base;
        total = amt;
      } else {
        base = amt;
        tax = (amt * rate) / 100;
        total = base + tax;
      }
      const cgst = tax / 2;
      const sgst = tax / 2;

      return {
        base: Math.round(base * 100) / 100,
        tax: Math.round(tax * 100) / 100,
        cgst: Math.round(cgst * 100) / 100,
        sgst: Math.round(sgst * 100) / 100,
        total: Math.round(total * 100) / 100,
        rate,
        isInclusive,
        summaryText: isInclusive
          ? `₹${total.toLocaleString('en-IN')} includes ${rate}% GST (Base: ₹${base.toLocaleString('en-IN')}, GST: ₹${tax.toLocaleString('en-IN')})`
          : `₹${base.toLocaleString('en-IN')} + ${rate}% GST = ₹${total.toLocaleString('en-IN')} (GST: ₹${tax.toLocaleString('en-IN')})`
      };
    },

    evaluateChange(entities) {
      const amounts = entities.amounts.map(a => a.val);
      if (amounts.length < 2) return null;
      const paid = Math.max(amounts[0], amounts[1]);
      const bill = Math.min(amounts[0], amounts[1]);
      const change = paid - bill;

      const notes = [500, 200, 100, 50, 20, 10, 5, 2, 1];
      const denoms = {};
      const noteCounts = {};
      let rem = change;
      for (const n of notes) {
        if (rem >= n) {
          const count = Math.floor(rem / n);
          denoms[`₹${n}`] = count;
          noteCounts[`${n}`] = count;
          rem %= n;
        }
      }

      const denomStr = Object.entries(denoms).map(([k, v]) => `${v}×${k}`).join(', ');
      return {
        paid,
        bill,
        change,
        denominations: denoms,
        notes: noteCounts,
        denomStr,
        summaryText: `Return ₹${change.toLocaleString('en-IN')} change (Paid ₹${paid.toLocaleString('en-IN')} for ₹${bill.toLocaleString('en-IN')} bill) • ${denomStr}`
      };
    },

    evaluateSplit(entities, raw) {
      const amt = entities.amount || 0;
      const ratioMatch = /(\d+(?:\s*:\s*\d+)+)/.exec(raw);
      if (ratioMatch) {
        const parts = ratioMatch[1].split(':').map(p => parseFloat(p.trim())).filter(p => !isNaN(p) && p > 0);
        const sumParts = parts.reduce((a, b) => a + b, 0);
        const shares = parts.map(p => Math.round((amt * p / sumParts) * 100) / 100);
        return {
          type: 'ratio',
          total: amt,
          ratios: parts,
          shares,
          summaryText: `Split ₹${amt.toLocaleString('en-IN')} in ratio ${ratioMatch[1]} ➔ ${shares.map((s, idx) => `P${idx + 1}: ₹${s}`).join(', ')}`
        };
      }
      const countMatch = /(?:between|by|among|in)\s+(\d+)/i.exec(raw);
      if (countMatch) {
        const count = parseInt(countMatch[1], 10);
        if (count > 0) {
          const perPerson = Math.round((amt / count) * 100) / 100;
          return {
            type: 'equal',
            total: amt,
            count,
            perPerson,
            result: perPerson,
            summaryText: `Split ₹${amt.toLocaleString('en-IN')} between ${count} people = ₹${perPerson.toLocaleString('en-IN')} each`
          };
        }
      }
      return null;
    },

    evaluateDiscount(entities, raw = '') {
      const amt = entities.amount || 0;
      let rate = entities.rate != null ? entities.rate : 0;
      // Check for flat amount discount: "discount 500 on 5000"
      const flatMatch = /(?:discount|chut|off|rebate)\s+(?:₹|rs\.?|inr)?\s*(\d+(?:\.\d+)?)\s+(?:on|from|in)\s+(?:₹|rs\.?|inr)?\s*(\d+(?:\.\d+)?)/i.exec(raw);
      if (flatMatch) {
        const flatDisc = parseFloat(flatMatch[1]);
        const origAmt = parseFloat(flatMatch[2]);
        const finalAmt = origAmt - flatDisc;
        const effPct = origAmt > 0 ? Math.round(((flatDisc / origAmt) * 100) * 100) / 100 : 0;
        return {
          amount: origAmt,
          rate: effPct,
          discount: flatDisc,
          finalAmount: finalAmt,
          netTotal: finalAmt,
          result: finalAmt,
          summaryText: `₹${origAmt.toLocaleString('en-IN')} less ₹${flatDisc.toLocaleString('en-IN')} discount (${effPct}%) = ₹${finalAmt.toLocaleString('en-IN')} (Saved: ₹${flatDisc.toLocaleString('en-IN')})`
        };
      }

      const discVal = (amt * rate) / 100;
      const finalAmt = amt - discVal;
      return {
        amount: amt,
        rate,
        discount: discVal,
        finalAmount: finalAmt,
        netTotal: finalAmt,
        result: finalAmt,
        summaryText: `₹${amt.toLocaleString('en-IN')} less ${rate}% discount = ₹${finalAmt.toLocaleString('en-IN')} (Saved: ₹${discVal.toLocaleString('en-IN')})`
      };
    },

    evaluateMarkup(entities, raw = '') {
      const amt = entities.amount || 0;
      const rate = entities.rate != null ? entities.rate : 0;
      const markupVal = (amt * rate) / 100;
      const sellingPrice = amt + markupVal;
      return {
        cost: amt,
        rate,
        markup: markupVal,
        sellingPrice,
        result: sellingPrice,
        summaryText: `Cost: ₹${amt.toLocaleString('en-IN')} + ${rate}% Markup = ₹${sellingPrice.toLocaleString('en-IN')} (Markup Profit: ₹${markupVal.toLocaleString('en-IN')})`
      };
    },

    evaluateMargin(entities, raw = '') {
      // Check if query specifically requests markup
      if (/\bmarkup\b/i.test(raw)) {
        return this.evaluateMarkup(entities, raw);
      }

      const amounts = entities.amounts.map(a => a.val);
      if (amounts.length >= 2) {
        const cost = Math.min(amounts[0], amounts[1]);
        const selling = Math.max(amounts[0], amounts[1]);
        const profit = selling - cost;
        const marginPct = Math.round(((profit / selling) * 100) * 100) / 100;
        const markupPct = cost > 0 ? Math.round(((profit / cost) * 100) * 100) / 100 : 0;
        return {
          cost, selling, profit,
          marginPercentage: marginPct,
          marginPercent: marginPct,
          markupPercentage: markupPct,
          markupPercent: markupPct,
          result: profit,
          summaryText: `Cost: ₹${cost.toLocaleString('en-IN')}, Selling: ₹${selling.toLocaleString('en-IN')} ➔ Profit: ₹${profit.toLocaleString('en-IN')} (${marginPct}% margin, ${markupPct}% markup)`
        };
      }

      const sp = entities.amount || 0;
      const rate = entities.rate != null ? entities.rate : 0;
      const profit = (sp * rate) / 100;
      const cost = sp - profit;
      const markup = cost > 0 ? Math.round(((profit / cost) * 100) * 100) / 100 : 0;
      return {
        selling: sp,
        cost,
        profit,
        marginPercentage: rate,
        markupPercentage: markup,
        result: cost,
        summaryText: `Selling: ₹${sp.toLocaleString('en-IN')} with ${rate}% Margin ➔ Cost: ₹${cost.toLocaleString('en-IN')}, Profit: ₹${profit.toLocaleString('en-IN')} (${markup}% markup)`
      };
    },

    evaluateLoanEmi(entities, raw) {
      const principal = entities.amount || 0;
      const rate = entities.rate != null ? entities.rate : 10;
      const tenureMatch = /(\d+)\s*(?:years?|yrs?|yr|saal|varsh)/i.exec(raw);
      const years = tenureMatch ? parseInt(tenureMatch[1], 10) : 1;

      // Simple Interest
      const si = (principal * rate * years) / 100;
      const totalSi = principal + si;

      // Monthly EMI
      const r = (rate / 12) / 100;
      const n = years * 12;
      const emi = r > 0 ? (principal * r * Math.pow(1 + r, n)) / (Math.pow(1 + r, n) - 1) : principal / n;

      return {
        principal,
        rate,
        years,
        monthlyEmi: Math.round(emi),
        totalInterest: Math.round(si),
        totalAmount: Math.round(totalSi),
        summaryText: `EMI: ₹${Math.round(emi).toLocaleString('en-IN')}/month for ₹${principal.toLocaleString('en-IN')} at ${rate}% for ${years} yr(s)`
      };
    },

    evaluateUnitConversion(raw) {
      const regex = /(?:convert\s+)?(\d+(?:\.\d+)?)\s*([a-zA-Z/0-9_°℃℉]+)\s*(?:to|in|into|madhe|se|mein|ko)\s*([a-zA-Z/0-9_°℃℉]+)/i;
      const m = regex.exec(raw);
      if (!m) return null;
      const val = parseFloat(m[1]);
      let fromKey = m[2].toLowerCase().replace(/°|deg/g, '');
      let toKey = m[3].toLowerCase().replace(/°|deg/g, '');

      const fromUnit = UnitRates[fromKey] || UnitRates[m[2].toLowerCase()];
      const toUnit = UnitRates[toKey] || UnitRates[m[3].toLowerCase()];

      if (!fromUnit || !toUnit || fromUnit.base !== toUnit.base) {
        return null;
      }

      let converted;
      if (typeof fromUnit.toBase === 'function') {
        const inBase = fromUnit.toBase(val);
        converted = typeof toUnit.fromBase === 'function' ? toUnit.fromBase(inBase) : inBase;
      } else {
        const inBase = val * fromUnit.toBase;
        converted = inBase / toUnit.toBase;
      }

      const rounded = Math.round(converted * 1000) / 1000;

      return {
        value: val,
        fromUnit: fromUnit.name,
        toUnit: toUnit.name,
        result: rounded,
        summaryText: `${val} ${fromUnit.name} = ${rounded.toLocaleString('en-IN')} ${toUnit.name}`
      };
    },

    evaluateSimpleInterest(entities, raw = '') {
      const p = entities.amount || 0;
      const r = entities.rate != null ? entities.rate : 10;
      const tenureMatch = /(\d+(?:\.\d+)?)\s*(?:years?|yrs?|yr|saal|varsh|months?|mahine)/i.exec(raw);
      let t = 1;
      if (tenureMatch) {
        t = parseFloat(tenureMatch[1]);
        if (/months?|mahine/i.test(tenureMatch[0])) {
          t = t / 12;
        }
      }
      const si = (p * r * t) / 100;
      const total = p + si;
      return {
        principal: p,
        rate: r,
        time: t,
        interest: Math.round(si * 100) / 100,
        total: Math.round(total * 100) / 100,
        result: Math.round(si * 100) / 100,
        summaryText: `Simple Interest on ₹${p.toLocaleString('en-IN')} @ ${r}% for ${tenureMatch ? tenureMatch[0] : '1 yr'} = ₹${Math.round(si).toLocaleString('en-IN')} (Total: ₹${Math.round(total).toLocaleString('en-IN')})`
      };
    },

    evaluateCompoundInterest(entities, raw = '') {
      const p = entities.amount || 0;
      const r = entities.rate != null ? entities.rate : 10;
      const tenureMatch = /(\d+(?:\.\d+)?)\s*(?:years?|yrs?|yr|saal|varsh)/i.exec(raw);
      const t = tenureMatch ? parseFloat(tenureMatch[1]) : 1;
      const n = 1; // compounded annually
      const amount = p * Math.pow(1 + (r / (100 * n)), n * t);
      const ci = amount - p;
      return {
        principal: p,
        rate: r,
        years: t,
        interest: Math.round(ci * 100) / 100,
        total: Math.round(amount * 100) / 100,
        result: Math.round(ci * 100) / 100,
        summaryText: `Compound Interest on ₹${p.toLocaleString('en-IN')} @ ${r}% for ${t} yr(s) = ₹${Math.round(ci).toLocaleString('en-IN')} (Total: ₹${Math.round(amount).toLocaleString('en-IN')})`
      };
    },

    evaluateSIP(entities, raw = '') {
      const p = entities.amount || 5000;
      const r = entities.rate != null ? entities.rate : 12;
      const tenureMatch = /(\d+(?:\.\d+)?)\s*(?:years?|yrs?|yr|saal|varsh)/i.exec(raw);
      const years = tenureMatch ? parseFloat(tenureMatch[1]) : 5;
      const n = years * 12; // total monthly installments
      const i = (r / 100) / 12; // monthly rate
      const futureValue = i > 0 ? p * ((Math.pow(1 + i, n) - 1) / i) * (1 + i) : p * n;
      const invested = p * n;
      const wealthGain = futureValue - invested;
      return {
        monthlyDeposit: p,
        rate: r,
        years,
        invested: Math.round(invested),
        wealthGain: Math.round(wealthGain),
        totalValue: Math.round(futureValue),
        result: Math.round(futureValue),
        summaryText: `SIP ₹${p.toLocaleString('en-IN')}/mo @ ${r}% for ${years} yrs ➔ Total Maturity: ₹${Math.round(futureValue).toLocaleString('en-IN')} (Invested: ₹${Math.round(invested).toLocaleString('en-IN')}, Gain: ₹${Math.round(wealthGain).toLocaleString('en-IN')})`
      };
    },

    evaluateTDS(entities, raw = '') {
      const amt = entities.amount || 0;
      const rate = entities.rate != null ? entities.rate : 10;
      const tdsVal = (amt * rate) / 100;
      const netPayable = amt - tdsVal;
      const roundedTds = Math.round(tdsVal * 100) / 100;
      const roundedNet = Math.round(netPayable * 100) / 100;
      return {
        grossAmount: amt,
        rate,
        tdsAmount: roundedTds,
        netPayable: roundedNet,
        netAmount: roundedNet,
        result: roundedTds,
        summaryText: `TDS @ ${rate}% on ₹${amt.toLocaleString('en-IN')} = ₹${Math.round(tdsVal).toLocaleString('en-IN')} (Net Payable: ₹${Math.round(netPayable).toLocaleString('en-IN')})`
      };
    },

    evaluateCurrency(raw) {
      if (!raw || typeof raw !== 'string') return null;
      let s = raw.trim();

      // Strip natural language triggers
      s = s.replace(/^(?:convert|calculate|compute|how\s+much\s+is|what\s+is|whats|batao|kitna\s+hoga|kitna\s+hai|sang|sanga)\s+/gi, '').trim();

      // Normalize Indian number words (e.g., 1 lakh INR to USD, dedh lakh in USD)
      s = WordsEngine.convertColloquialQuantifiers(s);

      // Decouple currency symbols and amounts (e.g., $100 to INR, €50 in USD, ₹5000 in AED)
      s = s.replace(/([$€£¥₩₽₺฿₱₪₦৳₫])\s*(\d+(?:\.\d+)?)/g, (match, sym, num) => {
        const symMap = { '$': 'usd', '€': 'eur', '£': 'gbp', '¥': 'jpy', '₩': 'krw', '₽': 'rub', '₺': 'try', '฿': 'thb', '₱': 'php', '₪': 'ils', '₦': 'ngn', '৳': 'bdt', '₫': 'vnd', '₹': 'inr' };
        return `${num} ${symMap[sym] || sym}`;
      });
      s = s.replace(/(\d+(?:\.\d+)?)\s*([$€£¥₩₽₺฿₱₪₦৳₫])/g, (match, num, sym) => {
        const symMap = { '$': 'usd', '€': 'eur', '£': 'gbp', '¥': 'jpy', '₩': 'krw', '₽': 'rub', '₺': 'try', '฿': 'thb', '₱': 'php', '₪': 'ils', '₦': 'ngn', '৳': 'bdt', '₫': 'vnd', '₹': 'inr' };
        return `${num} ${symMap[sym] || sym}`;
      });

      // Match pattern: <amount> <fromUnit> (to|in|into|madhe|se|mein|ko|converted to|=) <toUnit>
      const regex = /^(\d+(?:,\d+)*(?:\.\d+)?)\s+([a-zA-Z\s$€£¥₩₽₺฿₱₪₦৳₫]+?)\s+(?:to|in|into|madhe|se|mein|ko|converted\s+to|=)\s+([a-zA-Z\s$€£¥₩₽₺฿₱₪₦৳₫]+?)(?:\s*(?:approx|please|karo|kara))?$/i;
      const m = regex.exec(s);
      if (!m) return null;

      const rawVal = parseFloat(m[1].replace(/,/g, ''));
      if (isNaN(rawVal) || rawVal < 0) return null;

      const fromStr = m[2].toLowerCase().trim();
      const toStr = m[3].toLowerCase().trim();

      const fromUnit = UnitRates[fromStr] || UnitRates[fromStr.replace(/s$/, '')];
      const toUnit = UnitRates[toStr] || UnitRates[toStr.replace(/s$/, '')];

      if (!fromUnit || !toUnit || fromUnit.base !== 'currency' || toUnit.base !== 'currency') {
        return null;
      }

      const inBaseInr = rawVal * fromUnit.toBase;
      const converted = inBaseInr / toUnit.toBase;
      const rounded = Math.round(converted * 100) / 100;
      const exchangeRate = toUnit.toBase > 0 ? (fromUnit.toBase / toUnit.toBase) : 0;

      return {
        amount: rawVal,
        fromCurrency: fromUnit.name,
        toCurrency: toUnit.name,
        fromCode: fromUnit.code || fromStr.toUpperCase(),
        toCode: toUnit.code || toStr.toUpperCase(),
        result: rounded,
        rate: rounded > 0 ? Math.round(exchangeRate * 10000) / 10000 : 0,
        isStaticRate: true,
        asOfDate: 'September 2026',
        disclaimer: 'Static reference rate (as of September 2026). Live exchange rates may vary.',
        summaryText: `${rawVal.toLocaleString('en-IN')} ${fromUnit.name} ≈ ${rounded.toLocaleString('en-IN')} ${toUnit.name} (Reference Rate as of Sep 2026)`
      };
    },

    evaluateDateMath(raw, refDate = new Date()) {
      return DateMathEngine.evaluate(raw, refDate);
    }
  };

  // ─────────────────────────────────────────────────────────────────────────────
  // 6. GRANULAR CAPABILITY CLASSIFIER (Single-Query Determinism)
  // ─────────────────────────────────────────────────────────────────────────────
  const CapabilityClassifier = {
    classify(normalized, ctx = {}) {
      const { lower, raw, decoupled, cleaned } = normalized;

      // ─────────────────────────────────────────────────────────
      // 0. EXPLICIT ACTION COMMAND PREFIXES (Precedence over Math)
      // ─────────────────────────────────────────────────────────
      if (/^(?:note|sticky\s*note|pin\s*note|add\s*note|chitthi|tippan)\b/i.test(lower)) {
        return { category: 'ACTION', capabilityId: 'ACT_CREATE_NOTE', executionType: 'CONFIRM' };
      }
      if (/^(?:set\s+reminder|create\s+reminder|add\s+reminder|reminder|remind\s+me|remind|todo|task|alarm|yaad\s+dilao|yaad\s+dilana|aathvan\s+kara|athvan\s+kara)\b/i.test(lower)) {
        return { category: 'ACTION', capabilityId: 'ACT_CREATE_TASK', executionType: 'CONFIRM' };
      }
      if (/^(?:add\s*customer|new\s*customer|register\s*customer|naya\s*grahak)\b/i.test(lower)) {
        return { category: 'ACTION', capabilityId: 'ACT_CREATE_CUSTOMER', executionType: 'CONFIRM' };
      }
      if (/^(?:attendance|hazri|mark\s+attendance|mark\s+present|mark\s+absent)\b/i.test(lower) ||
          /\b(?:mark|record)\b.*\b(present|absent|half\s*day|hazri|attendance)\b/i.test(lower)) {
        return { category: 'ACTION', capabilityId: 'ACT_MARK_ATTENDANCE', executionType: 'CONFIRM' };
      }
      if (/^(?:advance|advance\s*salary|salary\s*advance)\b/i.test(lower) && /\d+/.test(lower)) {
        return { category: 'ACTION', capabilityId: 'ACT_RECORD_ADVANCE', executionType: 'CONFIRM' };
      }

      // ─────────────────────────────────────────────────────────
      // A. SPECIAL CAPABILITIES (Deterministic Math, UPI, Words)
      // ─────────────────────────────────────────────────────────
      // 1. UPI QR Payment
      if (/\b(upi|qr|bhim|gpay|phonepe|paytm)\b/i.test(lower) && (/\d+/.test(lower) || /^(upi|qr|\/qr)$/i.test(cleaned))) {
        return { category: 'SPECIAL', capabilityId: 'SPEC_PAY_UPI_QR', confidence: 0.98 };
      }

      // 2. WhatsApp Communication Link
      if (/\b(whatsapp|wa|remind|sandesh|msg)\b/i.test(lower) && /\b(dues|pending|payment|balance|bill)\b/i.test(lower)) {
        return { category: 'SPECIAL', capabilityId: 'SPEC_COMM_WHATSAPP', confidence: 0.95 };
      }

      // 3. Number to Words & Words to Number
      if (/\b(words|spell|in words|akshari|shabdat)\b/i.test(lower) && /\d+/.test(lower)) {
        return { category: 'SPECIAL', capabilityId: 'SPEC_WORDS_TO_NUM', confidence: 0.98 };
      }
      if (!/[\+\-\*\/×÷\^]/.test(lower) && WordsEngine.wordsToNumber(lower) !== null) {
        return { category: 'SPECIAL', capabilityId: 'SPEC_NUM_TO_WORDS', confidence: 0.96 };
      }

      // 4. Cashier Change Helper
      if (/\b(change|chutta|wapas|baki|return)\b/i.test(lower) && /\d+/.test(lower)) {
        return { category: 'SPECIAL', capabilityId: 'SPEC_MATH_CHANGE', confidence: 0.98 };
      }

      // 5. Commercial GST Math
      if (/\b(gst|tax|vat)\b/i.test(lower) && (/\d+/.test(lower) || /\bans\b/i.test(lower))) {
        const isInclusive = /\b(inclusive|reverse|included|with tax|with\s*(?:\d+%)?\s*gst|with\s*(?:\d+%)?\s*tax|shamil|incl|inc)\b/i.test(lower);
        return {
          category: 'SPECIAL',
          capabilityId: isInclusive ? 'SPEC_MATH_GST_INCL' : 'SPEC_MATH_GST_EXCL',
          confidence: 0.98
        };
      }

      // 6. Bill Splitter
      if (/\b(split|batwara|hissa)\b|\bdivide\s+(?:between|among|in)\b/i.test(lower) && /\d+/.test(lower)) {
        return { category: 'SPECIAL', capabilityId: 'SPEC_MATH_SPLIT', confidence: 0.98 };
      }

      // 7. Discounts & Margins
      if (/\b(discount|chut|off|rebate)\b/i.test(lower) && (/\d+/.test(lower) || /\bans\b/i.test(lower))) {
        return { category: 'SPECIAL', capabilityId: 'SPEC_MATH_DISCOUNT', confidence: 0.98 };
      }
      if (/\b(margin|markup|munafa)\b/i.test(lower) && (/\d+/.test(lower) || /\bans\b/i.test(lower))) {
        return { category: 'SPECIAL', capabilityId: 'SPEC_MATH_MARGIN', confidence: 0.97 };
      }

      // 7.5 Financial Math: SI, CI, SIP, TDS
      if (/\b(?:simple\s*interest|si\s+on)\b/i.test(lower) && /\d+/.test(lower)) {
        return { category: 'SPECIAL', capabilityId: 'SPEC_MATH_SI', confidence: 0.98 };
      }
      if (/\b(?:compound\s*interest|ci\s+on)\b/i.test(lower) && /\d+/.test(lower)) {
        return { category: 'SPECIAL', capabilityId: 'SPEC_MATH_CI', confidence: 0.98 };
      }
      if (/\b(?:sip|systematic\s*investment)\b/i.test(lower) && /\d+/.test(lower)) {
        return { category: 'SPECIAL', capabilityId: 'SPEC_MATH_SIP', confidence: 0.98 };
      }
      if (/\b(?:tds|tax\s*deducted\s*at\s*source)\b/i.test(lower) && /\d+/.test(lower)) {
        return { category: 'SPECIAL', capabilityId: 'SPEC_MATH_TDS', confidence: 0.98 };
      }

      // 8. Loan EMI & Interest
      if (/\b(emi|loan|byaj|vyaj)\b/i.test(lower) && /\d+/.test(lower)) {
        return { category: 'SPECIAL', capabilityId: 'SPEC_MATH_EMI', confidence: 0.97 };
      }

      // 8.5 Deterministic Date Math
      const dateEval = DateMathEngine.evaluate(raw) || DateMathEngine.evaluate(lower);
      if (dateEval) {
        return { category: 'SPECIAL', capabilityId: 'SPEC_MATH_DATE', confidence: 0.98, data: dateEval };
      }

      // 8.6 Currency Conversion
      const currEval = CalculationEvaluator.evaluateCurrency(raw) || CalculationEvaluator.evaluateCurrency(lower) || CalculationEvaluator.evaluateCurrency(normalized.transliterated);
      if (currEval) {
        return { category: 'SPECIAL', capabilityId: 'SPEC_CONV_CURRENCY', confidence: 0.99, data: currEval };
      }

      // 9. Broad Deterministic NLP & Safe Expression Arithmetic Engine
      const mathEval = SafeArithmeticEngine.evaluate(lower, ctx) || SafeArithmeticEngine.evaluate(raw, ctx) || SafeArithmeticEngine.evaluate(normalized.transliterated, ctx);
      if (mathEval) {
        return {
          category: 'SPECIAL',
          capabilityId: 'SPEC_MATH_ARITH',
          confidence: 0.99,
          data: mathEval
        };
      }

      // 10. Unit Conversions
      if (/(?:convert\s+)?\d+(?:\.\d+)?\s*[a-z/0-9_°℃℉]+\s+\b(?:to|in|into|madhe|se|mein|ko)\b\s+[a-z/0-9_°℃℉]+/i.test(lower) ||
          /(?:convert\s+)\d+(?:\.\d+)?\s*[a-z/0-9_°℃℉]+/i.test(lower) ||
          /(?:convert\s+)?\d+(?:\.\d+)?\s*[a-z/0-9_°℃℉]+\s+\b(?:to|in|into|madhe|se|mein|ko)\b\s+[a-z/0-9_°℃℉]+/i.test(normalized.transliterated)) {
        return { category: 'SPECIAL', capabilityId: 'SPEC_CONV_UNIT', confidence: 0.98 };
      }

      // ─────────────────────────────────────────────────────────
      // B. ACTION CAPABILITIES (Shortcuts, Mutations, Documents)
      // ─────────────────────────────────────────────────────────
      // Slash navigation shortcuts
      if (/^\/inv\b|\bbill\s*banao\b|\bpavti\s*banva\b|\bcreate\s*invoice\b/i.test(lower)) {
        return { category: 'ACTION', capabilityId: 'ACT_NAV_SLASH', target: { page: 'invoices', subTab: 'invoices' } };
      }
      if (/^\/quo\b|\bkaccha\s*bill\b|\bandaj\s*patrak\b|\bcreate\s*quotation\b/i.test(lower)) {
        return { category: 'ACTION', capabilityId: 'ACT_NAV_SLASH', target: { page: 'invoices', subTab: 'quotations' } };
      }
      if (/^\/khata\b|\bopen\s*statements\b/i.test(lower)) {
        return { category: 'ACTION', capabilityId: 'ACT_NAV_SLASH', target: { page: 'firm', tab: 'paperwork', subTab: 'statements', statementMode: 'customer' } };
      }
      if (/^\/pay\b|\bopen\s*payroll\b|\btankha\s*(?:kholo|page|tab)\b/i.test(lower)) {
        return { category: 'ACTION', capabilityId: 'ACT_NAV_SLASH', target: { page: 'hr', hrTab: 'payroll' } };
      }
      if (/^\/po\b|\bcreate\s*purchase\s*order\b|\bsupplier\s*order\b/i.test(lower)) {
        return { category: 'ACTION', capabilityId: 'ACT_NAV_SLASH', target: { page: 'firm', tab: 'paperwork', subTab: 'orders' } };
      }
      if (/^\/goal\b|^\/goals\b|^\/habit\b|^\/habits\b|^\/streak\b|^\/streaks\b|^\/target\b|\b(?:goals?|habits?|streaks?|targets?|lakshya|dhyey)\b/i.test(lower) && !/\d+/.test(lower) && !/\b(summary|total|report|status)\b/i.test(lower)) {
        return { category: 'ACTION', capabilityId: 'ACT_NAV_SLASH', target: { page: 'planner', plannerTab: 'goals' } };
      }
      if (/^\/save\b|^\/saving\b|^\/savings\b|^\/bachat\b|^\/invest\b|^\/sip\b|^\/deposit\b|\b(?:savings?|investments?|bachat|sip|reserves?)\b/i.test(lower) && !/\d+/.test(lower) && !/\b(summary|total|report|status)\b/i.test(lower)) {
        return { category: 'ACTION', capabilityId: 'ACT_NAV_SLASH', target: { page: 'planner', plannerTab: 'savings' } };
      }
      if (/^\/personal\b|^\/wealth\b|^\/myfinances\b|^\/personalfin\b|\b(?:personal\s*(?:dashboard|wealth|finance|hisab|cockpit)|vyaktigat\s*dashboard)\b/i.test(lower)) {
        return { category: 'ACTION', capabilityId: 'ACT_NAV_SLASH', target: { page: 'dashboard', dashboardSegment: 'personal' } };
      }
      if (/^\/biz\b|^\/business\b|^\/salesdash\b|^\/maindash\b|\b(?:business\s*(?:dashboard|overview|cockpit)|vyapar\s*dashboard|sales\s*dashboard)\b/i.test(lower) && !/\b(summary|report|health)\b/i.test(lower)) {
        return { category: 'ACTION', capabilityId: 'ACT_NAV_SLASH', target: { page: 'dashboard', dashboardSegment: 'business' } };
      }
      if (/^\/backup\b|^\/bckup\b|^\/restore\b|^\/snapshot\b|\b(?:backup\s*(?:data|settings|file|download)?|restore\s*data|data\s*save\s*karo|surakshit\s*theva)\b/i.test(lower)) {
        return { category: 'ACTION', capabilityId: 'ACT_NAV_SLASH', target: { page: 'settings', tab: 'backup' } };
      }
      if (/^\/todo\b|^\/board\b|^\/kanban\b|^\/planner\b|\b(?:task\s*board|planner\s*board|kanban\s*board)\b/i.test(lower)) {
        return { category: 'ACTION', capabilityId: 'ACT_NAV_SLASH', target: { page: 'planner', plannerTab: 'board' } };
      }

      // Direct System Actions
      if (/^(?:theme|toggle\s*theme|\/theme|switch\s*theme)$/i.test(cleaned)) {
        return { category: 'ACTION', capabilityId: 'ACT_THEME_TOGGLE', executionType: 'DIRECT', actionType: 'TOGGLE' };
      }
      if (/^(?:dark\s*mode|dark\s*theme|enable\s*dark\s*mode|set\s*dark)$/i.test(cleaned)) {
        return { category: 'ACTION', capabilityId: 'ACT_THEME_DARK', executionType: 'DIRECT', actionType: 'DARK' };
      }
      if (/^(?:light\s*mode|light\s*theme|enable\s*light\s*mode|set\s*light)$/i.test(cleaned)) {
        return { category: 'ACTION', capabilityId: 'ACT_THEME_LIGHT', executionType: 'DIRECT', actionType: 'LIGHT' };
      }
      if (/^(lock|lock screen|safe mode|\/lock)$/i.test(cleaned)) {
        return { category: 'ACTION', capabilityId: 'ACT_APP_LOCK', executionType: 'DIRECT' };
      }

      // Direct Document Actions
      if (/\b(print|thermal\s*print)\b/i.test(lower) && /\b(invoice|bill|quote|estimate|receipt)\b/i.test(lower)) {
        return { category: 'ACTION', capabilityId: 'ACT_PRINT_DOC', executionType: 'DOCUMENT' };
      }
      if (/\b(download\s*pdf|pdf\s*download|get\s*pdf)\b/i.test(lower)) {
        return { category: 'ACTION', capabilityId: 'ACT_DOWNLOAD_PDF', executionType: 'DOCUMENT' };
      }

      // Safe Action Previews (Mutations)
      if (/^(?:set\s+reminder|create\s+reminder|add\s+reminder|reminder|remind\s+me|remind|todo|task|alarm|yaad\s+dilao|yaad\s+dilana|aathvan\s+kara|athvan\s+kara)\b/i.test(lower) ||
          /\b(set\s+reminder|create\s+reminder|add\s+reminder|remind\s+me\s+to|remind\s+me|yaad\s+dilao|yaad\s+dilana|aathvan\s+kara|athvan\s+kara)\b/i.test(lower) ||
          /^(?:todo|task)\b/i.test(lower)) {
        return { category: 'ACTION', capabilityId: 'ACT_CREATE_TASK', executionType: 'CONFIRM' };
      }
      if ((/\b(kharcha|expense|kharch|petty\s*cash)\b/i.test(lower) && (/\d+/.test(lower) || /^(kharcha|expense|kharch)$/i.test(cleaned))) ||
          (/^(?:add\s+expense|record\s+expense|spent|paid)\b/i.test(lower) && /\d+/.test(lower))) {
        return { category: 'ACTION', capabilityId: 'ACT_CREATE_EXPENSE', executionType: 'CONFIRM' };
      }
      if (/^(?:note|sticky\s*note|pin\s*note|add\s*note|chitthi|tippan)\b/i.test(lower)) {
        return { category: 'ACTION', capabilityId: 'ACT_CREATE_NOTE', executionType: 'CONFIRM' };
      }
      if (/^(?:add\s*customer|new\s*customer|register\s*customer|naya\s*grahak)\b/i.test(lower) ||
          (/\b(customer|grahak)\b/i.test(lower) && /\b[6-9]\d{9}\b/.test(lower) && /\b(add|new|create|register|save)\b/i.test(lower))) {
        return { category: 'ACTION', capabilityId: 'ACT_CREATE_CUSTOMER', executionType: 'CONFIRM' };
      }
      if (/^(?:mark\s+attendance|mark\s+present|mark\s+absent|record\s+attendance)\b/i.test(lower) ||
          /\b(?:mark|record)\b.*\b(present|absent|half\s*day|hazri|attendance)\b/i.test(lower) ||
          (/\b(present|absent|half\s*day)\b/i.test(lower) && /\b(attendance|hazri)\b/i.test(lower) && !/\b(today|kal|who|summary|report|list)\b/i.test(lower)) ||
          (/\b(attendance|hazri)\b/i.test(lower) && !/\b(today|kal|who|summary|report|list|kitne|kiti)\b/i.test(lower))) {
        return { category: 'ACTION', capabilityId: 'ACT_MARK_ATTENDANCE', executionType: 'CONFIRM' };
      }
      if (/\b(advance|advance\s*salary|salary\s*advance)\b/i.test(lower) && /\d+/.test(lower)) {
        return { category: 'ACTION', capabilityId: 'ACT_RECORD_ADVANCE', executionType: 'CONFIRM' };
      }

      // Navigation Page Targets
      if (/\b(open|go\s*to)\s*(?:the\s*)?customers?\b/i.test(lower)) return { category: 'ACTION', capabilityId: 'ACT_NAV_PAGE', target: { page: 'firm', tab: 'customers' } };
      if (/\b(open|go\s*to)\s*(?:the\s*)?inventory\b/i.test(lower)) return { category: 'ACTION', capabilityId: 'ACT_NAV_PAGE', target: { page: 'firm', tab: 'inventory' } };
      if (/\b(open|go\s*to)\s*(?:the\s*)?expenses?\b/i.test(lower)) return { category: 'ACTION', capabilityId: 'ACT_NAV_PAGE', target: { page: 'planner', plannerTab: 'expenses' } };
      if (/\b(open|go\s*to)\s*(?:the\s*)?(?:parties|vendors?)\b/i.test(lower)) return { category: 'ACTION', capabilityId: 'ACT_NAV_PAGE', target: { page: 'firm', tab: 'parties' } };
      if (/\b(open|go\s*to)\s*(?:the\s*)?(?:staff|employees?|hr)\b/i.test(lower)) return { category: 'ACTION', capabilityId: 'ACT_NAV_PAGE', target: { page: 'hr', hrTab: 'staff' } };

      // ─────────────────────────────────────────────────────────
      // C. QUICK_HELP CAPABILITIES (Authoritative Read Adapters)
      // ─────────────────────────────────────────────────────────
      // 1. Macro Business Summary & Health
      if (/\b(business\s*(?:summary|health|report|overview|performance)|dashboard\s*summary|how\s*is\s*business|overall\s*summary|crm\s*summary|vyapar\s*samiksha)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_MACRO_SUMMARY', type: 'AGGREGATE' };
      }

      // 2. Aggregate Sales Queries
      if (/\b(yesterday('?s)?\s*sales?|yesterday('?s)?\s*revenue|kal\s*ka\s*sale|kal\s*ki\s*sale|kalchi\s*bikri|kalchi\s*sale)\b/i.test(lower) ||
          /\b(yesterday('?s)?\s*sales?|yesterday('?s)?\s*revenue|kal\s*ka\s*sale|kal\s*ki\s*sale|kalchi\s*bikri|kalchi\s*sale)\b/i.test(normalized.transliterated)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_SALES_YESTERDAY', type: 'AGGREGATE' };
      }
      if (/\b(today('?s)?\s*sales?|today('?s)?\s*revenue|how\s*much\s*(?:did\s*we\s*sell|have\s*we\s*sold)|aaj\s*ka\s*sale|aaj\s*ka\s*dhanda|aajchi\s*sale|aajchi\s*bikri|today\s*sales?|total\s*sales?\s*today|today\s*total\s*sales?)\b/i.test(lower) ||
          /\b(today('?s)?\s*sales?|today('?s)?\s*revenue|aaj\s*ka\s*sale|aaj\s*ka\s*dhanda|aajchi\s*sale|aajchi\s*bikri|today\s*sales?|total\s*sales?\s*today|today\s*total\s*sales?)\b/i.test(normalized.transliterated)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_SALES_TODAY', type: 'AGGREGATE' };
      }
      if (/\b((?:this\s*)?week('?s)?\s*sales?|weekly\s*revenue|hfte\s*ka\s*sale|this\s*week\s*sales?)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_SALES_WEEK', type: 'AGGREGATE' };
      }
      if (/\b(last\s*month('?s)?\s*sales?|pichle\s*mahine\s*ka\s*sale)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_SALES_LAST_MONTH', type: 'AGGREGATE' };
      }
      if (/\b((?:this\s*)?month('?s)?\s*sales?|monthly\s*sales?|is\s*mahine\s*ka\s*sale|this\s*month\s*sales?)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_SALES_MONTH', type: 'AGGREGATE' };
      }
      if (/\b((?:this\s*)?quarter('?s)?\s*sales?|quarterly\s*sales?|quarterly\s*revenue)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_SALES_QUARTER', type: 'AGGREGATE' };
      }
      if (/\b((?:this\s*)?year('?s)?\s*sales?|yearly\s*sales?|annual\s*revenue|is\s*saal\s*ka\s*sale|fy\s*sales?)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_SALES_YEAR', type: 'AGGREGATE' };
      }
      if (/\b(total\s*sales?|overall\s*sales?|total\s*business|total\s*revenue|all\s*time\s*sales)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_SALES_TOTAL', type: 'AGGREGATE' };
      }

      // Period Comparisons
      if (/\b(?:sales?|revenue)\s*(?:this\s*month\s*vs\s*last\s*month|vs\s*last\s*month|compared\s*to\s*last\s*month|month\s*over\s*month|mom)\b/i.test(lower) ||
          /\b(?:this\s*month\s*vs\s*last\s*month|pichle\s*mahine\s*se\s*tulna)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_SALES_COMPARISON', type: 'AGGREGATE' };
      }
      if (/\b(?:sales?|revenue)\s*(?:today\s*vs\s*yesterday|vs\s*yesterday|today\s*aur\s*kal)\b/i.test(lower) ||
          /\b(?:today\s*vs\s*yesterday|aaj\s*aur\s*kal\s*ka\s*sale)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_SALES_TODAY_VS_YESTERDAY', type: 'AGGREGATE' };
      }

      // Top / Bottom N Rankings
      if (/\b(?:top|bottom)\s*(\d+)?\s*(?:debtors?|defaulters?|udhari\s*wale|pending\s*dues?)\b/i.test(lower) ||
          /\b(?:top\s*udhari\s*wale|top\s*debtors?)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_TOP_DEBTORS', type: 'AGGREGATE' };
      }
      if (/\b(?:top|best)\s*(\d+)?\s*(?:customers?|clients?|buyers?|giraik|grahak)\b/i.test(lower) ||
          /\b(top\s*customers?|best\s*clients?|major\s*customers?|top\s*buyers?)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_TOP_CUSTOMERS', type: 'AGGREGATE' };
      }
      if (/\b(?:bottom|least|worst|slow(?:est)?\s*moving)\s*(\d+)?\s*(?:products?|items?|selling|sold)?\b/i.test(lower) ||
          /\b(least\s*sold|bottom\s*selling|slow\s*moving\s*items?)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_BOTTOM_PRODUCTS', type: 'AGGREGATE' };
      }
      if (/\b(?:top|best)\s*(\d+)?\s*(?:products?|items?|selling|most\s*sold)\b/i.test(lower) ||
          /\b(top\s*products?|best\s*selling\s*items?|most\s*sold|fast\s*moving\s*items?|sabse\s*zyada\s*bikne\s*wale)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_TOP_PRODUCTS', type: 'AGGREGATE' };
      }

      // Threshold / Filter Queries
      if (/\b(?:invoices?|bills?)\s*(?:over|above|greater\s*than|more\s*than|>|>=)\s*(?:₹|rs\.?|inr)?\s*\d+/i.test(lower) ||
          /\b(?:sales?|invoices?|bills?)\s*(?:above|over|>|>=)\s*(?:₹|rs\.?|inr)?\s*\d+/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_INVOICES_FILTER_ABOVE', type: 'AGGREGATE' };
      }
      if (/\b(?:invoices?|bills?)\s*(?:under|below|less\s*than|<|<=)\s*(?:₹|rs\.?|inr)?\s*\d+/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_INVOICES_FILTER_BELOW', type: 'AGGREGATE' };
      }
      if (/\b(?:customers?|grahak)\s*(?:owing|dues?|balance|pending)\s*(?:over|above|greater\s*than|more\s*than|>|>=)\s*(?:₹|rs\.?|inr)?\s*\d+/i.test(lower) ||
          /\b(?:pending\s*dues?|udhari)\s*(?:over|above|>|>=)\s*(?:₹|rs\.?|inr)?\s*\d+/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_CUSTOMERS_FILTER_DUE', type: 'AGGREGATE' };
      }
      if (/\b(?:expenses?|kharcha)\s*(?:over|above|greater\s*than|more\s*than|>|>=)\s*(?:₹|rs\.?|inr)?\s*\d+/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_EXPENSES_FILTER_ABOVE', type: 'AGGREGATE' };
      }
      if (/\b(?:expenses?|kharcha)\s*(?:under|below|less\s*than|<|<=)\s*(?:₹|rs\.?|inr)?\s*\d+/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_EXPENSES_FILTER_BELOW', type: 'AGGREGATE' };
      }

      // Aging Analysis
      if (/\b(?:aging\s*(?:summary|report|analysis)|dues?\s*older\s*than\s*\d+\s*days?|overdue\s*aging|aging\s*buckets?)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_AGING_SUMMARY', type: 'AGGREGATE' };
      }

      // 3. Aggregate Receivables / Outstandings
      if (/\b(total\s*udhari|total\s*outstanding|who\s*owes\s*money|total\s*dues|pending\s*balance|all\s*udhari|all\s*customer\s*dues)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_TOTAL_UDHARI', type: 'AGGREGATE' };
      }
      if (/\b(overdue\s*invoices?|unpaid\s*bills?\s*count|how\s*many\s*unpaid\s*bills?|pending\s*bills?\s*count)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_OVERDUE_COUNT', type: 'AGGREGATE' };
      }

      // 4. Aggregate Expenses
      if (/\b(today('?s)?\s*expenses?|how\s*much\s*spent\s*today|aaj\s*ka\s*kharcha|today\s*expenses?)\b/i.test(lower) && !/\d+/.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_EXPENSE_TODAY', type: 'AGGREGATE' };
      }
      if (/\b((?:this\s*)?week('?s)?\s*expenses?|weekly\s*expenses?)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_EXPENSE_WEEK', type: 'AGGREGATE' };
      }
      if (/\b(last\s*month('?s)?\s*expenses?|pichle\s*mahine\s*ka\s*kharcha)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_EXPENSE_LAST_MONTH', type: 'AGGREGATE' };
      }
      if (/\b((?:this\s*)?month('?s)?\s*expenses?|monthly\s*expenses?|is\s*mahine\s*ka\s*kharcha|this\s*month\s*expenses?)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_EXPENSE_MONTH', type: 'AGGREGATE' };
      }
      if (/\b((?:this\s*)?year('?s)?\s*expenses?|yearly\s*expenses?|is\s*saal\s*ka\s*kharcha)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_EXPENSE_YEAR', type: 'AGGREGATE' };
      }
      if (/\b(expense\s*breakdown|expense\s*categories|category\s*wise\s*expenses?|kharcha\s*breakdown)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_EXPENSE_BREAKDOWN', type: 'AGGREGATE' };
      }
      if (/\b(rent|salary|salaries|travel|travelling|chai|tea|food|electricity|power|internet|cleaning|petrol|diesel|fuel)\s*(?:expense|expenses|kharcha|kharch)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_EXPENSE_CATEGORY', type: 'AGGREGATE' };
      }

      // 5. Aggregate & Specific Inventory & Stock Movements
      if (/\b(stock\s*movements?|stock\s*in\s*out|inward\s*outward|godown\s*movements?|stock\s*logs?|item\s*movements?)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_STOCK_MOVEMENTS_SUMMARY', type: 'AGGREGATE' };
      }
      if (/\b(low\s*stock|kam\s*stock|reorder\s*stock|out\s*of\s*stock\s*items?|stock\s*alert)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_LOW_STOCK', type: 'AGGREGATE' };
      }
      if (/\b(inventory\s*valuation|total\s*stock\s*value|total\s*inventory\s*value|godown\s*valuation)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_INVENTORY_VALUATION', type: 'AGGREGATE' };
      }
      if (/\b(stock|quantity|price|cost\s*price|bhav|kimat|shillak)\s*(?:of|for|mein)?\b/i.test(lower) || /\b(?:how\s*many|how\s*much)\s+[a-z0-9]+\s+(?:left|in\s*stock|available)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_PRODUCT_INFO', type: 'ENTITY' };
      }

      // 6. HR, Staff, Salaries & Advances
      if (/\b(salaries|payroll|tankha|vetan|monthly\s*payroll|salary\s*records?|payouts?\s*summary)\b/i.test(lower) ||
          /\b(?:salary|salaries)\s*(?:summary|list|report|total|of|for|ka|ki|cha|chi)?\b/i.test(lower) ||
          /\b(?:salary)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_SALARIES_SUMMARY', type: 'AGGREGATE' };
      }
      if (/\b(staff\s*advances?|advance\s*summary|employee\s*advances?|advance\s*balances?|who\s*took\s*advance|pending\s*advances?|advances?)\b/i.test(lower) && !/\d+/.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_ADVANCES_SUMMARY', type: 'AGGREGATE' };
      }
      if (/\b(who\s*is\s*on\s*leave|today('?s)?\s*attendance|staff\s*list|all\s*employees?|karmachari\s*suchi|who\s*is\s*absent|staff\s*attendance|attendance\s*summary|attendance\s*report)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_HR_SUMMARY', type: 'AGGREGATE' };
      }
      if (/\b(attendance|leaves?|designation|staff\s*details?)\s*(?:of|for|cha|chi|ka|ki)\b/i.test(lower) ||
          /\b(?:employee|staff|karmachari)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_EMPLOYEE_INFO', type: 'ENTITY' };
      }

      // 7. Sales Returns & Credit Notes
      if (/\b(sales\s*returns?|credit\s*notes?|returns?\s*summary|wapsi|mal\s*wapas|goods\s*returned|returned\s*orders?)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_RETURNS_SUMMARY', type: 'AGGREGATE' };
      }

      // 8. Collections & Payment Receipts
      if (/\b(collections?|payments?\s*received|money\s*collected|receipts?\s*summary|total\s*collection|jama\s*rakkam|cash\s*collection|upi\s*collection)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_COLLECTIONS_SUMMARY', type: 'AGGREGATE' };
      }

      // 9. Vendor Payouts & Supplier Payments
      if (/\b(vendor\s*payouts?|vendor\s*payments?|supplier\s*payments?|payments?\s*made|paid\s*to\s*vendors?|kharcha\s*to\s*party)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_VENDOR_PAYOUTS_SUMMARY', type: 'AGGREGATE' };
      }

      // 10. Purchase Orders
      if (/\b(purchase\s*orders?|po\s*summary|all\s*pos?|supplier\s*orders?|open\s*pos?)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_PURCHASE_ORDERS_SUMMARY', type: 'AGGREGATE' };
      }

      // 11. Ledger & Statements of Account
      if (/\b(ledger|khata\s*book|statement\s*of\s*account|account\s*statement|customer\s*ledger|vendor\s*ledger|party\s*ledger|statements?)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_LEDGER_SUMMARY', type: 'ENTITY' };
      }

      // 12. Firm Bank / Tax Details
      if (/\b(bank\s*details|our\s*ifsc|account\s*number|bank\s*khata)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_FIRM_BANK', type: 'ENTITY' };
      }
      if (/\b(our\s*gst\s*number|gstin|firm\s*address|shop\s*address)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_FIRM_TAX', type: 'ENTITY' };
      }

      // 13. Customer / Vendor Specific Lookups & Payables
      if (/\b(vendor\s*dues|total\s*payables|who\s*do\s*we\s*owe|all\s*vendor\s*dues|all\s*supplier\s*balances)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_VENDOR_DUES_TOTAL', type: 'AGGREGATE' };
      }
      if (/\b(payable|payables)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_VENDOR_DUE', type: 'ENTITY' };
      }
      if (/\b(receivable|receivables)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_CUSTOMER_DUE', type: 'ENTITY' };
      }
      if (/\b(owe|owes|udhari|udhar|outstanding|pending|balance|dues|khata|baki|bakaya)\b/i.test(lower)) {
        if (/\b(vendor|supplier|party|kaccha\s*vyapari)\b/i.test(lower)) {
          return { category: 'QUICK_HELP', capabilityId: 'QH_VENDOR_DUE', type: 'ENTITY' };
        }
        return { category: 'QUICK_HELP', capabilityId: 'QH_CUSTOMER_DUE', type: 'ENTITY' };
      }

      // 14. Invoices & Document Queries
      if (/\b(recent\s*invoices?|last\s*\d+\s*bills?|latest\s*invoices?|unpaid\s*invoices?|overdue\s*invoices?|pending\s*bills?|invoice\s*list|invoices\s*list|all\s*invoices|bill\s*list)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_INVOICE_LIST', type: 'DOCUMENT' };
      }
      if (/\b(invoices?|bills?|pavti)\b/i.test(lower) && !/\d+/.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_CUSTOMER_INVS', type: 'DOCUMENT' };
      }
      if (/\b(inv[-_ ]?\d+|bill\s*\d+|invoice\s*\d+|#\d+)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_INVOICE_INFO', type: 'DOCUMENT' };
      }
      if (/\b(?:est(?:imate)?|quote|quotation)[-_ ]?(\d+)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_QUOTE_INFO', type: 'DOCUMENT' };
      }
      if (/\b(po[-_ ]?\d+|order\s*\d+|purchase\s*order\s*\d+)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_PO_INFO', type: 'DOCUMENT' };
      }
      if (/\b(invoices?|bills?|pavti)\b/i.test(lower) && !/\d+/.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_CUSTOMER_INVS', type: 'DOCUMENT' };
      }
      if (/\b(inv[-_ ]?\d+|bill\s*\d+|invoice\s*\d+|#\d+)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_INVOICE_INFO', type: 'DOCUMENT' };
      }
      if (/\b(?:est(?:imate)?|quote|quotation)[-_ ]?(\d+)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_QUOTE_INFO', type: 'DOCUMENT' };
      }
      if (/\b(po[-_ ]?\d+|order\s*\d+|purchase\s*order\s*\d+)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_PO_INFO', type: 'DOCUMENT' };
      }

      // ─────────────────────────────────────────────────────────
      // D. SUBORDINATE SEARCH FALLBACK
      // ─────────────────────────────────────────────────────────
      return { category: 'SEARCH', capabilityId: 'SEARCH_FALLBACK', confidence: 0.5 };
    }
  };

  // ─────────────────────────────────────────────────────────────────────────────
  // 6.5 AUTHORITATIVE QUICK HELP ADAPTER (Read-Only CRM Analytics & Entity Info)
  // ─────────────────────────────────────────────────────────────────────────────
  const QuickHelpAdapter = {
    evaluate(capabilityId, entities, normalized, ctx = {}) {
      const customers = Array.isArray(ctx.customers) ? ctx.customers : [];
      const invoices = Array.isArray(ctx.invoices) ? ctx.invoices : [];
      const products = Array.isArray(ctx.products) ? ctx.products : [];
      const parties = Array.isArray(ctx.parties) ? ctx.parties : [];
      const staff = Array.isArray(ctx.staff) ? ctx.staff : [];
      const expenses = Array.isArray(ctx.expenses) ? ctx.expenses : [];
      const firm = ctx.firm || {};
      const todayStr = new Date().toISOString().slice(0, 10);
      const yesterdayStr = new Date(Date.now() - 86400000).toISOString().slice(0, 10);
      const sevenDaysAgo = new Date(Date.now() - 7 * 86400000).toISOString().slice(0, 10);
      const curMonth = todayStr.slice(0, 7);
      const nowD = new Date();
      const lastMonthD = new Date(nowD.getFullYear(), nowD.getMonth() - 1, 1);
      const lastMonthStr = `${lastMonthD.getFullYear()}-${String(lastMonthD.getMonth() + 1).padStart(2, '0')}`;

      switch (capabilityId) {
        // ─── 1. MACRO SUMMARY ───
        case 'QH_MACRO_SUMMARY': {
          const totalSales = invoices.reduce((acc, inv) => acc + (parseFloat(inv.netTotal || inv.totalAmount) || 0), 0);
          const totalReceivables = customers.reduce((acc, c) => acc + (parseFloat(c.balance || c.openingBalance) || 0), 0);
          const totalPayables = parties.reduce((acc, p) => acc + (parseFloat(p.netBalance || p.openingBalance) || 0), 0);
          const monthExp = expenses.filter(e => (e.expenseDate || e.createdAt || '').slice(0, 7) === curMonth)
            .reduce((acc, e) => acc + (parseFloat(e.amount) || 0), 0);
          const lowStockCount = products.filter(p => (parseFloat(p.stock != null ? p.stock : p.openingStock) || 0) <= (parseFloat(p.minStockAlert) || 5)).length;

          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_MACRO_SUMMARY',
            domain: 'AGGREGATE',
            apiEndpoint: '/api/omnisearch/aggregate',
            apiParams: {},
            title: `💼 Business Snapshot: ₹${totalSales.toLocaleString('en-IN')} Sales`,
            subtitle: `Receivables: ₹${totalReceivables.toLocaleString('en-IN')} • Payables: ₹${totalPayables.toLocaleString('en-IN')} • Month Exp: ₹${monthExp.toLocaleString('en-IN')}`,
            data: {
              totalSales,
              totalReceivables,
              totalPayables,
              thisMonthExpense: monthExp,
              lowStockCount,
              customerCount: customers.length,
              vendorCount: parties.length,
              staffCount: staff.length
            },
            actions: [
              { label: 'View Invoices', route: 'invoices' },
              { label: 'View Ledger', route: 'firm', tab: 'paperwork', subTab: 'statements' }
            ]
          };
        }

        // ─── 2. SALES ───
        case 'QH_SALES_YESTERDAY': {
          let yestInvs = invoices.filter(inv => (inv.invoiceDate || inv.createdAt || '').slice(0, 10) === yesterdayStr);
          const totalRev = yestInvs.reduce((acc, inv) => acc + (parseFloat(inv.netTotal || inv.totalAmount) || 0), 0);
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_SALES_YESTERDAY',
            domain: 'SALES',
            apiEndpoint: '/api/omnisearch/sales',
            apiParams: { period: 'YESTERDAY' },
            title: `📊 Yesterday's Sales: ₹${totalRev.toLocaleString('en-IN')}`,
            subtitle: `${yestInvs.length} invoice(s) generated yesterday (${yesterdayStr})`,
            data: { totalRevenue: totalRev, invoiceCount: yestInvs.length, date: yesterdayStr, period: 'YESTERDAY' },
            invoices: yestInvs
          };
        }

        case 'QH_SALES_TODAY': {
          let todayInvs = invoices.filter(inv => (inv.invoiceDate || inv.createdAt || '').slice(0, 10) === todayStr);
          const totalRev = todayInvs.reduce((acc, inv) => acc + (parseFloat(inv.netTotal || inv.totalAmount) || 0), 0);
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_SALES_TODAY',
            domain: 'SALES',
            apiEndpoint: '/api/omnisearch/sales',
            apiParams: { period: 'TODAY' },
            title: `📊 Today's Sales: ₹${totalRev.toLocaleString('en-IN')}`,
            subtitle: `${todayInvs.length} invoice(s) generated today (${todayStr})`,
            data: { totalRevenue: totalRev, invoiceCount: todayInvs.length, date: todayStr, period: 'TODAY' },
            invoices: todayInvs
          };
        }

        case 'QH_SALES_WEEK': {
          let weekInvs = invoices.filter(inv => {
            const d = (inv.invoiceDate || inv.createdAt || '').slice(0, 10);
            return d >= sevenDaysAgo && d <= todayStr;
          });
          const totalRev = weekInvs.reduce((acc, inv) => acc + (parseFloat(inv.netTotal || inv.totalAmount) || 0), 0);
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_SALES_WEEK',
            domain: 'SALES',
            apiEndpoint: '/api/omnisearch/sales',
            apiParams: { period: 'THIS_WEEK' },
            title: `📊 Last 7 Days Sales: ₹${totalRev.toLocaleString('en-IN')}`,
            subtitle: `${weekInvs.length} invoice(s) generated in the last 7 days`,
            data: { totalRevenue: totalRev, invoiceCount: weekInvs.length, period: 'THIS_WEEK' },
            invoices: weekInvs
          };
        }

        case 'QH_SALES_LAST_MONTH': {
          let lastMonthInvs = invoices.filter(inv => (inv.invoiceDate || inv.createdAt || '').slice(0, 7) === lastMonthStr);
          const totalRev = lastMonthInvs.reduce((acc, inv) => acc + (parseFloat(inv.netTotal || inv.totalAmount) || 0), 0);
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_SALES_LAST_MONTH',
            domain: 'SALES',
            apiEndpoint: '/api/omnisearch/sales',
            apiParams: { period: 'LAST_MONTH' },
            title: `📊 Last Month's Sales: ₹${totalRev.toLocaleString('en-IN')}`,
            subtitle: `${lastMonthInvs.length} invoice(s) in month ${lastMonthStr}`,
            data: { totalRevenue: totalRev, invoiceCount: lastMonthInvs.length, period: 'LAST_MONTH' },
            invoices: lastMonthInvs
          };
        }

        case 'QH_SALES_MONTH': {
          let monthInvs = invoices.filter(inv => (inv.invoiceDate || inv.createdAt || '').slice(0, 7) === curMonth);
          const totalRev = monthInvs.reduce((acc, inv) => acc + (parseFloat(inv.netTotal || inv.totalAmount) || 0), 0);
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_SALES_MONTH',
            domain: 'SALES',
            apiEndpoint: '/api/omnisearch/sales',
            apiParams: { period: 'THIS_MONTH' },
            title: `📊 This Month's Sales: ₹${totalRev.toLocaleString('en-IN')}`,
            subtitle: `${monthInvs.length} invoice(s) in month ${curMonth}`,
            data: { totalRevenue: totalRev, invoiceCount: monthInvs.length, period: 'THIS_MONTH' },
            invoices: monthInvs
          };
        }

        case 'QH_SALES_QUARTER': {
          const totalRev = invoices.reduce((acc, inv) => acc + (parseFloat(inv.netTotal || inv.totalAmount) || 0), 0);
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_SALES_QUARTER',
            domain: 'SALES',
            apiEndpoint: '/api/omnisearch/sales',
            apiParams: { period: 'THIS_QUARTER' },
            title: `📊 This Quarter's Sales: ₹${totalRev.toLocaleString('en-IN')}`,
            subtitle: `Quarterly sales performance across ${invoices.length} invoices`,
            data: { totalRevenue: totalRev, invoiceCount: invoices.length, period: 'THIS_QUARTER' }
          };
        }

        case 'QH_SALES_YEAR':
        case 'QH_SALES_TOTAL': {
          const totalRev = invoices.reduce((acc, inv) => acc + (parseFloat(inv.netTotal || inv.totalAmount) || 0), 0);
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: capabilityId,
            domain: 'SALES',
            apiEndpoint: '/api/omnisearch/sales',
            apiParams: { period: capabilityId === 'QH_SALES_YEAR' ? 'THIS_YEAR' : 'TOTAL' },
            title: `📊 Total Sales: ₹${totalRev.toLocaleString('en-IN')}`,
            subtitle: `All-time revenue across ${invoices.length} invoices`,
            data: { totalRevenue: totalRev, invoiceCount: invoices.length, period: 'TOTAL' }
          };
        }

        // Period Comparisons (Month-over-Month & Day-over-Day)
        case 'QH_SALES_COMPARISON': {
          let thisMonthSales = 0, lastMonthSales = 0;
          invoices.forEach(inv => {
            const d = (inv.invoiceDate || inv.date || inv.createdAt || '');
            const amt = parseFloat(inv.netTotal || inv.totalAmount || inv.grandTotal) || 0;
            if (d.startsWith(curMonth)) thisMonthSales += amt;
            else if (d.startsWith(lastMonthStr)) lastMonthSales += amt;
          });
          const delta = thisMonthSales - lastMonthSales;
          const growthPct = lastMonthSales > 0 ? (delta / lastMonthSales) * 100 : 0;
          const growthSign = growthPct >= 0 ? '+' : '';
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_SALES_COMPARISON',
            domain: 'SALES',
            apiEndpoint: '/api/omnisearch/sales',
            apiParams: { compare: 'MOM' },
            title: `📊 Sales Comparison: This Month vs Last Month`,
            subtitle: `This Month: ₹${thisMonthSales.toLocaleString('en-IN')} • Last Month: ₹${lastMonthSales.toLocaleString('en-IN')} (${growthSign}${growthPct.toFixed(1)}%)`,
            data: { thisMonth: thisMonthSales, lastMonth: lastMonthSales, delta, growthPct }
          };
        }

        case 'QH_SALES_TODAY_VS_YESTERDAY': {
          let todaySales = 0, yesterdaySales = 0;
          invoices.forEach(inv => {
            const d = (inv.invoiceDate || inv.date || inv.createdAt || '').slice(0, 10);
            const amt = parseFloat(inv.netTotal || inv.totalAmount || inv.grandTotal) || 0;
            if (d === todayStr) todaySales += amt;
            else if (d === yesterdayStr) yesterdaySales += amt;
          });
          const delta = todaySales - yesterdaySales;
          const growthPct = yesterdaySales > 0 ? (delta / yesterdaySales) * 100 : 0;
          const growthSign = growthPct >= 0 ? '+' : '';
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_SALES_TODAY_VS_YESTERDAY',
            domain: 'SALES',
            apiEndpoint: '/api/omnisearch/sales',
            apiParams: { compare: 'DOD' },
            title: `📊 Sales Comparison: Today vs Yesterday`,
            subtitle: `Today: ₹${todaySales.toLocaleString('en-IN')} • Yesterday: ₹${yesterdaySales.toLocaleString('en-IN')} (${growthSign}${growthPct.toFixed(1)}%)`,
            data: { todaySales, yesterdaySales, delta, growthPct }
          };
        }

        // Top / Bottom N Rankings
        case 'QH_TOP_CUSTOMERS': {
          const limit = entities.limit || 5;
          const custMap = {};
          invoices.forEach(inv => {
            const cName = inv.customerName || (inv.customer && inv.customer.name) || 'Other';
            custMap[cName] = (custMap[cName] || 0) + (parseFloat(inv.netTotal || inv.totalAmount || inv.grandTotal) || 0);
          });
          if (Object.keys(custMap).length === 0 && customers.length > 0) {
            customers.forEach(c => {
              custMap[c.name || c.customerName] = parseFloat(c.totalSpend || c.balance || 0);
            });
          }
          const sorted = Object.entries(custMap).sort((a, b) => b[1] - a[1]).slice(0, limit);
          const summaryStr = sorted.map(([name, amt]) => `${name}: ₹${amt.toLocaleString('en-IN')}`).join(' • ');
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_TOP_CUSTOMERS',
            domain: 'SALES',
            apiEndpoint: '/api/omnisearch/sales',
            apiParams: { groupBy: 'CUSTOMER', limit },
            title: `🏆 Top ${limit} Customers by Revenue`,
            subtitle: summaryStr || 'No sales records found',
            data: { topCustomers: sorted, limit }
          };
        }

        case 'QH_TOP_DEBTORS': {
          const limit = entities.limit || 5;
          const sorted = customers
            .map(c => ({ name: c.name || c.customerName, balance: parseFloat(c.balance || c.openingBalance) || 0, id: c.id, phone: c.phone }))
            .filter(c => c.balance > 0)
            .sort((a, b) => b.balance - a.balance)
            .slice(0, limit);
          const totalDue = sorted.reduce((acc, c) => acc + c.balance, 0);
          const summaryStr = sorted.map(c => `${c.name}: ₹${c.balance.toLocaleString('en-IN')}`).join(' • ');
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_TOP_DEBTORS',
            domain: 'CUSTOMER',
            apiEndpoint: '/api/omnisearch/customer',
            apiParams: { filter: 'DEBTORS', limit },
            title: `⚠️ Top ${limit} Outstanding Debtors (₹${totalDue.toLocaleString('en-IN')})`,
            subtitle: summaryStr || 'No outstanding debtor records found',
            data: { debtors: sorted, totalDue, limit }
          };
        }

        case 'QH_TOP_PRODUCTS': {
          const limit = entities.limit || 5;
          const prodMap = {};
          invoices.forEach(inv => {
            (inv.items || []).forEach(it => {
              const pName = it.name || it.productName || 'Item';
              prodMap[pName] = (prodMap[pName] || 0) + (parseFloat(it.quantity || it.qty) || 1);
            });
          });
          if (Object.keys(prodMap).length === 0 && products.length > 0) {
            products.forEach(p => {
              prodMap[p.name || p.productName] = parseFloat(p.salesVolume || p.price || 0);
            });
          }
          const sorted = Object.entries(prodMap).sort((a, b) => b[1] - a[1]).slice(0, limit);
          const summaryStr = sorted.map(([name, qty]) => `${name} (${qty} units)`).join(' • ');
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_TOP_PRODUCTS',
            domain: 'SALES',
            apiEndpoint: '/api/omnisearch/sales',
            apiParams: { groupBy: 'PRODUCT', limit },
            title: `📦 Top ${limit} Selling Products`,
            subtitle: summaryStr || 'No item sales history found',
            data: { topProducts: sorted, limit }
          };
        }

        case 'QH_BOTTOM_PRODUCTS': {
          const limit = entities.limit || 5;
          const prodMap = {};
          products.forEach(p => {
            prodMap[p.name || p.productName] = 0;
          });
          invoices.forEach(inv => {
            (inv.items || []).forEach(it => {
              const pName = it.name || it.productName || 'Item';
              prodMap[pName] = (prodMap[pName] || 0) + (parseFloat(it.quantity || it.qty) || 1);
            });
          });
          const sorted = Object.entries(prodMap).sort((a, b) => a[1] - b[1]).slice(0, limit);
          const summaryStr = sorted.map(([name, qty]) => `${name} (${qty} units)`).join(' • ');
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_BOTTOM_PRODUCTS',
            domain: 'SALES',
            apiEndpoint: '/api/omnisearch/sales',
            apiParams: { groupBy: 'PRODUCT', order: 'ASC', limit },
            title: `📦 Bottom ${limit} Selling Items`,
            subtitle: summaryStr || 'No product catalog found',
            data: { bottomProducts: sorted, limit }
          };
        }

        // Threshold Filters
        case 'QH_INVOICES_FILTER_ABOVE':
        case 'QH_INVOICES_FILTER_BELOW': {
          const threshold = entities.threshold || 0;
          const isAbove = capabilityId === 'QH_INVOICES_FILTER_ABOVE';
          const filtered = invoices.filter(inv => {
            const amt = parseFloat(inv.grandTotal || inv.netTotal || inv.totalAmount) || 0;
            return isAbove ? amt >= threshold : amt <= threshold;
          });
          const totalAmt = filtered.reduce((acc, inv) => acc + (parseFloat(inv.grandTotal || inv.netTotal || inv.totalAmount) || 0), 0);
          const opLabel = isAbove ? 'Above' : 'Below';
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId,
            domain: 'INVOICE',
            apiEndpoint: '/api/omnisearch/invoice',
            apiParams: { threshold, op: isAbove ? 'GTE' : 'LTE' },
            title: `🧾 ${filtered.length} Invoice(s) ${opLabel} ₹${threshold.toLocaleString('en-IN')} (Total: ₹${totalAmt.toLocaleString('en-IN')})`,
            subtitle: filtered.slice(0, 3).map(inv => `#${inv.invoiceNumber || inv.id || 'INV'}: ₹${(parseFloat(inv.grandTotal || inv.netTotal || 0)).toLocaleString('en-IN')}`).join(' • ') || `No invoices ${opLabel.toLowerCase()} ₹${threshold.toLocaleString('en-IN')}`,
            data: { invoices: filtered, count: filtered.length, totalAmount: totalAmt, threshold }
          };
        }

        case 'QH_CUSTOMERS_FILTER_DUE': {
          const threshold = entities.threshold || 0;
          const filtered = customers.filter(c => (parseFloat(c.balance || c.openingBalance) || 0) >= threshold);
          const totalDue = filtered.reduce((acc, c) => acc + (parseFloat(c.balance || c.openingBalance) || 0), 0);
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_CUSTOMERS_FILTER_DUE',
            domain: 'CUSTOMER',
            apiEndpoint: '/api/omnisearch/customer',
            apiParams: { minDue: threshold },
            title: `👥 ${filtered.length} Customer(s) Owing Over ₹${threshold.toLocaleString('en-IN')} (Total: ₹${totalDue.toLocaleString('en-IN')})`,
            subtitle: filtered.slice(0, 3).map(c => `${c.name}: ₹${(parseFloat(c.balance || 0)).toLocaleString('en-IN')}`).join(' • ') || `No customers owing over ₹${threshold.toLocaleString('en-IN')}`,
            data: { customers: filtered, count: filtered.length, totalDue, threshold }
          };
        }

        case 'QH_EXPENSES_FILTER_ABOVE':
        case 'QH_EXPENSES_FILTER_BELOW': {
          const threshold = entities.threshold || 0;
          const isAbove = capabilityId === 'QH_EXPENSES_FILTER_ABOVE';
          const filtered = expenses.filter(e => {
            const amt = parseFloat(e.amount) || 0;
            return isAbove ? amt >= threshold : amt <= threshold;
          });
          const totalAmt = filtered.reduce((acc, e) => acc + (parseFloat(e.amount) || 0), 0);
          const opLabel = isAbove ? 'Above' : 'Below';
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId,
            domain: 'EXPENSE',
            apiEndpoint: '/api/omnisearch/expense',
            apiParams: { threshold, op: isAbove ? 'GTE' : 'LTE' },
            title: `💸 ${filtered.length} Expense(s) ${opLabel} ₹${threshold.toLocaleString('en-IN')} (Total: ₹${totalAmt.toLocaleString('en-IN')})`,
            subtitle: filtered.slice(0, 3).map(e => `${e.title || e.category || 'Expense'}: ₹${(parseFloat(e.amount || 0)).toLocaleString('en-IN')}`).join(' • ') || `No expenses ${opLabel.toLowerCase()} ₹${threshold.toLocaleString('en-IN')}`,
            data: { expenses: filtered, count: filtered.length, totalAmount: totalAmt, threshold }
          };
        }

        case 'QH_AGING_SUMMARY': {
          let b0_30 = 0, b31_60 = 0, b61_90 = 0, b90plus = 0;
          const nowMs = Date.now();
          invoices.forEach(inv => {
            const bal = parseFloat(inv.balanceAmount || inv.netTotal || 0);
            if (bal > 0 && inv.status !== 'PAID') {
              const invDate = new Date(inv.invoiceDate || inv.date || inv.createdAt || nowMs);
              const diffDays = Math.floor((nowMs - invDate.getTime()) / (86400000));
              if (diffDays <= 30) b0_30 += bal;
              else if (diffDays <= 60) b31_60 += bal;
              else if (diffDays <= 90) b61_90 += bal;
              else b90plus += bal;
            }
          });
          if (b0_30 === 0 && b31_60 === 0 && b61_90 === 0 && b90plus === 0 && customers.length > 0) {
            customers.forEach(c => {
              const bal = parseFloat(c.balance || c.openingBalance) || 0;
              if (bal > 0) b0_30 += bal;
            });
          }
          const totalDue = b0_30 + b31_60 + b61_90 + b90plus;
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_AGING_SUMMARY',
            domain: 'CUSTOMER',
            apiEndpoint: '/api/omnisearch/aging',
            apiParams: {},
            title: `⏳ Accounts Receivable Aging (Total Due: ₹${totalDue.toLocaleString('en-IN')})`,
            subtitle: `0-30d: ₹${b0_30.toLocaleString('en-IN')} • 31-60d: ₹${b31_60.toLocaleString('en-IN')} • 61-90d: ₹${b61_90.toLocaleString('en-IN')} • 90d+: ₹${b90plus.toLocaleString('en-IN')}`,
            data: {
              buckets: { '0-30': b0_30, '31-60': b31_60, '61-90': b61_90, '90+': b90plus },
              totalDue
            }
          };
        }

        // ─── 3. RECEIVABLES & CUSTOMERS ───
        case 'QH_TOTAL_UDHARI': {
          let totalDue = 0;
          let count = 0;
          customers.forEach(c => {
            const bal = parseFloat(c.balance || c.openingBalance) || 0;
            if (bal > 0) {
              totalDue += bal;
              count++;
            }
          });
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_TOTAL_UDHARI',
            domain: 'CUSTOMER',
            apiEndpoint: '/api/omnisearch/customer',
            apiParams: {},
            title: `💰 Total Udhari / Dues: ₹${totalDue.toLocaleString('en-IN')}`,
            subtitle: `Pending balance across ${count} customer(s)`,
            data: { totalUdhari: totalDue, customerCount: count }
          };
        }

        case 'QH_OVERDUE_COUNT': {
          let overdueCount = 0;
          let overdueAmt = 0;
          invoices.forEach(inv => {
            const isUnpaid = inv.status === 'UNPAID' || inv.status === 'PENDING' || inv.status === 'PARTIAL';
            if (isUnpaid) {
              overdueCount++;
              overdueAmt += (parseFloat(inv.balanceAmount || inv.netTotal) || 0);
            }
          });
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_OVERDUE_COUNT',
            domain: 'INVOICE',
            apiEndpoint: '/api/omnisearch/invoice',
            apiParams: { filter: 'UNPAID' },
            title: `⚠️ Overdue / Unpaid Invoices: ${overdueCount}`,
            subtitle: `Total unpaid amount: ₹${overdueAmt.toLocaleString('en-IN')}`,
            data: { overdueCount, overdueAmount: overdueAmt }
          };
        }

        case 'QH_CUSTOMER_DUE':
        case 'QH_CUSTOMER_INFO': {
          if (entities.matchedCustomer) {
            const c = entities.matchedCustomer;
            const bal = parseFloat(c.balance || c.openingBalance) || 0;
            return {
              status: 'ANSWER',
              category: 'QUICK_HELP',
              capabilityId: 'QH_CUSTOMER_DUE',
              domain: 'CUSTOMER',
              apiEndpoint: '/api/omnisearch/customer',
              apiParams: { query: c.name, customerId: c.id },
              title: `👤 ${c.name}: ₹${bal.toLocaleString('en-IN')} Pending Due`,
              subtitle: `📞 ${c.phone || 'No phone'} • City: ${c.city || 'N/A'} • GST: ${c.gstin || 'None'}`,
              data: { customerName: c.name, balance: bal, phone: c.phone, customerId: c.id, city: c.city, gstin: c.gstin },
              actions: [
                { label: 'View Profile', route: 'firm', tab: 'customers' },
                { label: 'Open Ledger', route: 'firm', tab: 'paperwork', subTab: 'statements' }
              ]
            };
          }
          // Default to top 5 debtors
          const debtors = customers
            .filter(c => (parseFloat(c.balance || c.openingBalance) || 0) > 0)
            .sort((a, b) => (parseFloat(b.balance || b.openingBalance) || 0) - (parseFloat(a.balance || a.openingBalance) || 0))
            .slice(0, 5);
          const debtorsStr = debtors.map(c => `${c.name}: ₹${(parseFloat(c.balance || c.openingBalance) || 0).toLocaleString('en-IN')}`).join(' • ');
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_CUSTOMER_DUE',
            domain: 'CUSTOMER',
            apiEndpoint: '/api/omnisearch/customer',
            apiParams: {},
            title: `👤 Outstanding Customer Dues`,
            subtitle: debtorsStr || 'No customer currently has outstanding dues',
            data: { debtors }
          };
        }

        case 'QH_CUSTOMER_INVS': {
          const cName = entities.customerName || (entities.matchedCustomer && entities.matchedCustomer.name) || entities.targetName;
          const cId = entities.matchedCustomer ? entities.matchedCustomer.id : null;
          let cInvs = invoices;
          if (cId) {
            cInvs = invoices.filter(inv => inv.customerId === cId || (inv.customer && inv.customer.id === cId));
          } else if (cName) {
            cInvs = invoices.filter(inv => (inv.customerName && inv.customerName.toLowerCase().includes(cName.toLowerCase())) ||
                                           (inv.customer && inv.customer.name && inv.customer.name.toLowerCase().includes(cName.toLowerCase())));
          }
          const totalAmt = cInvs.reduce((a, b) => a + (parseFloat(b.netTotal || b.totalAmount) || 0), 0);
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_CUSTOMER_INVS',
            domain: 'INVOICE',
            apiEndpoint: '/api/omnisearch/invoice',
            apiParams: { query: cName, customerId: cId },
            title: `📄 Found ${cInvs.length} Invoice(s) for ${cName || 'Customer'}`,
            subtitle: `Total billed amount: ₹${totalAmt.toLocaleString('en-IN')}`,
            data: { customerName: cName, invoices: cInvs, totalAmount: totalAmt },
            invoices: cInvs
          };
        }

        // ─── 4. VENDORS & PAYABLES ───
        case 'QH_VENDOR_DUES_TOTAL': {
          let totalPayable = 0;
          let count = 0;
          parties.forEach(p => {
            const bal = parseFloat(p.netBalance || p.openingBalance) || 0;
            if (bal > 0) {
              totalPayable += bal;
              count++;
            }
          });
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_VENDOR_DUES_TOTAL',
            domain: 'VENDOR',
            apiEndpoint: '/api/omnisearch/vendor',
            apiParams: {},
            title: `🏭 Total Vendor Payables: ₹${totalPayable.toLocaleString('en-IN')}`,
            subtitle: `Outstanding balance across ${count} supplier(s)`,
            data: { totalPayable, vendorCount: count }
          };
        }

        case 'QH_VENDOR_DUE':
        case 'QH_VENDOR_INFO': {
          if (entities.matchedParty) {
            const p = entities.matchedParty;
            const bal = parseFloat(p.netBalance || p.openingBalance) || 0;
            return {
              status: 'ANSWER',
              category: 'QUICK_HELP',
              capabilityId: 'QH_VENDOR_DUE',
              domain: 'VENDOR',
              apiEndpoint: '/api/omnisearch/vendor',
              apiParams: { query: p.name, vendorId: p.id },
              title: `🏭 Vendor ${p.name}: ₹${bal.toLocaleString('en-IN')} Outstanding Due`,
              subtitle: `📞 ${p.phone || 'No phone'} • Contact: ${p.contactPerson || 'N/A'}`,
              data: { vendorName: p.name, balance: bal, partyId: p.id, phone: p.phone, contactPerson: p.contactPerson }
            };
          }
          const vendorDues = parties
            .filter(p => (parseFloat(p.netBalance || p.openingBalance) || 0) > 0)
            .slice(0, 5);
          const vStr = vendorDues.map(p => `${p.name}: ₹${(parseFloat(p.netBalance || p.openingBalance) || 0).toLocaleString('en-IN')}`).join(' • ');
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_VENDOR_DUE',
            domain: 'VENDOR',
            apiEndpoint: '/api/omnisearch/vendor',
            apiParams: {},
            title: `🏭 Outstanding Vendor Payables`,
            subtitle: vStr || 'No pending vendor payables found',
            data: { vendorDues }
          };
        }

        // ─── 5. INVOICES & DOCUMENTS ───
        case 'QH_INVOICE_INFO': {
          const qNum = entities.documentNumber || (normalized.raw.match(/(\d+)/) ? normalized.raw.match(/(\d+)/)[1] : null);
          const inv = invoices.find(i => String(i.invoiceNumber || '').includes(qNum) || String(i.id || '') === qNum);
          if (inv) {
            const amt = parseFloat(inv.netTotal || inv.totalAmount) || 0;
            return {
              status: 'ANSWER',
              category: 'QUICK_HELP',
              capabilityId: 'QH_INVOICE_INFO',
              domain: 'INVOICE',
              apiEndpoint: '/api/omnisearch/invoice',
              apiParams: { query: qNum, invoiceId: inv.id },
              title: `📄 Invoice #${inv.invoiceNumber || inv.id}: ₹${amt.toLocaleString('en-IN')}`,
              subtitle: `Customer: ${inv.customerName || (inv.customer && inv.customer.name) || 'N/A'} • Date: ${inv.invoiceDate || 'N/A'} • Status: ${inv.status || 'FINAL'}`,
              data: { invoice: inv }
            };
          }
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_INVOICE_INFO',
            domain: 'INVOICE',
            apiEndpoint: '/api/omnisearch/invoice',
            apiParams: { query: qNum },
            title: `📄 Invoice Lookup: #${qNum || 'N/A'}`,
            subtitle: `Fetching authoritative invoice details...`,
            data: { invoiceNumber: qNum }
          };
        }

        case 'QH_INVOICE_LIST': {
          const recent = invoices.slice(0, 5);
          const recStr = recent.map(i => `#${i.invoiceNumber || i.id}: ₹${(parseFloat(i.netTotal || i.totalAmount) || 0).toLocaleString('en-IN')}`).join(' • ');
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_INVOICE_LIST',
            domain: 'INVOICE',
            apiEndpoint: '/api/omnisearch/invoice',
            apiParams: {},
            title: `📄 Recent Invoices (${invoices.length} total)`,
            subtitle: recStr || 'No recent invoices found',
            data: { invoices: recent },
            invoices: recent
          };
        }

        case 'QH_QUOTE_INFO': {
          const qNum = entities.documentNumber || (normalized.raw.match(/(\d+)/) ? normalized.raw.match(/(\d+)/)[1] : '101');
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_QUOTE_INFO',
            domain: 'INVOICE',
            apiEndpoint: '/api/omnisearch/invoice',
            apiParams: { estimateNumber: qNum },
            title: `📑 Quotation / Estimate #${qNum}`,
            subtitle: `Fetching estimate details and line items...`,
            data: { estimateNumber: qNum }
          };
        }

        case 'QH_PO_INFO': {
          const qNum = entities.documentNumber || (normalized.raw.match(/(\d+)/) ? normalized.raw.match(/(\d+)/)[1] : '101');
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_PO_INFO',
            domain: 'DOCUMENT',
            title: `📦 Purchase Order #${qNum}`,
            subtitle: `Supplier order details and delivery status`,
            data: { poNumber: qNum }
          };
        }

        // ─── 6. EXPENSES ───
        case 'QH_EXPENSE_TODAY': {
          let todayExp = expenses.filter(e => (e.expenseDate || e.createdAt || '').slice(0, 10) === todayStr);
          let totalExp = todayExp.reduce((acc, e) => acc + (parseFloat(e.amount) || 0), 0);
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_EXPENSE_TODAY',
            domain: 'EXPENSE',
            apiEndpoint: '/api/omnisearch/expenses',
            apiParams: { period: 'TODAY' },
            title: `💸 Today's Expenses: ₹${totalExp.toLocaleString('en-IN')}`,
            subtitle: `${todayExp.length} expense entry(ies) logged today`,
            data: { totalExpense: totalExp, count: todayExp.length, period: 'TODAY' }
          };
        }

        case 'QH_EXPENSE_WEEK': {
          let weekExp = expenses.filter(e => {
            const d = (e.expenseDate || e.createdAt || '').slice(0, 10);
            return d >= sevenDaysAgo && d <= todayStr;
          });
          let totalExp = weekExp.reduce((acc, e) => acc + (parseFloat(e.amount) || 0), 0);
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_EXPENSE_WEEK',
            domain: 'EXPENSE',
            apiEndpoint: '/api/omnisearch/expenses',
            apiParams: { period: 'THIS_WEEK' },
            title: `💸 Last 7 Days Expenses: ₹${totalExp.toLocaleString('en-IN')}`,
            subtitle: `${weekExp.length} expense entry(ies) logged in the last 7 days`,
            data: { totalExpense: totalExp, count: weekExp.length, period: 'THIS_WEEK' }
          };
        }

        case 'QH_EXPENSE_LAST_MONTH': {
          let lastExp = expenses.filter(e => (e.expenseDate || e.createdAt || '').slice(0, 7) === lastMonthStr);
          let totalExp = lastExp.reduce((acc, e) => acc + (parseFloat(e.amount) || 0), 0);
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_EXPENSE_LAST_MONTH',
            domain: 'EXPENSE',
            apiEndpoint: '/api/omnisearch/expenses',
            apiParams: { period: 'LAST_MONTH' },
            title: `💸 Last Month's Expenses: ₹${totalExp.toLocaleString('en-IN')}`,
            subtitle: `${lastExp.length} expense entry(ies) in ${lastMonthStr}`,
            data: { totalExpense: totalExp, count: lastExp.length, period: 'LAST_MONTH' }
          };
        }

        case 'QH_EXPENSE_MONTH': {
          let monthExp = expenses.filter(e => (e.expenseDate || e.createdAt || '').slice(0, 7) === curMonth);
          let totalExp = monthExp.reduce((acc, e) => acc + (parseFloat(e.amount) || 0), 0);
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_EXPENSE_MONTH',
            domain: 'EXPENSE',
            apiEndpoint: '/api/omnisearch/expenses',
            apiParams: { period: 'THIS_MONTH' },
            title: `💸 This Month's Expenses: ₹${totalExp.toLocaleString('en-IN')}`,
            subtitle: `${monthExp.length} expense entry(ies) in ${curMonth}`,
            data: { totalExpense: totalExp, count: monthExp.length, period: 'THIS_MONTH' }
          };
        }

        case 'QH_EXPENSE_YEAR': {
          let totalExp = expenses.reduce((acc, e) => acc + (parseFloat(e.amount) || 0), 0);
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_EXPENSE_YEAR',
            domain: 'EXPENSE',
            apiEndpoint: '/api/omnisearch/expenses',
            apiParams: { period: 'THIS_YEAR' },
            title: `💸 Yearly Expenses: ₹${totalExp.toLocaleString('en-IN')}`,
            subtitle: `All expenses logged across ${expenses.length} records`,
            data: { totalExpense: totalExp, count: expenses.length, period: 'THIS_YEAR' }
          };
        }

        case 'QH_EXPENSE_CATEGORY':
        case 'QH_EXPENSE_BREAKDOWN': {
          const cat = entities.expenseCategory || 'General';
          const catExp = expenses.filter(e => (e.category || '').toLowerCase().includes(cat.toLowerCase()));
          const totalCat = catExp.reduce((acc, e) => acc + (parseFloat(e.amount) || 0), 0);
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: capabilityId,
            domain: 'EXPENSE',
            apiEndpoint: '/api/omnisearch/expenses',
            apiParams: { category: cat },
            title: `💸 ${cat} Expenses: ₹${totalCat.toLocaleString('en-IN')}`,
            subtitle: `${catExp.length} record(s) under category "${cat}"`,
            data: { category: cat, totalExpense: totalCat, count: catExp.length }
          };
        }

        // ─── 7. INVENTORY & PRODUCTS ───
        case 'QH_LOW_STOCK': {
          let lowItems = products.filter(p => {
            const stk = parseFloat(p.stock != null ? p.stock : p.openingStock) || 0;
            const threshold = parseFloat(p.minStockAlert) || 5;
            return stk <= threshold;
          });
          const itemNames = lowItems.slice(0, 4).map(p => `${p.name} (${p.stock || 0} ${p.unit || 'pcs'})`).join(', ');
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_LOW_STOCK',
            domain: 'INVENTORY',
            apiEndpoint: '/api/omnisearch/inventory',
            apiParams: { filter: 'LOW_STOCK' },
            title: `⚠️ Low Stock Alert: ${lowItems.length} item(s)`,
            subtitle: itemNames ? `Critical: ${itemNames}` : 'All products have adequate stock levels',
            data: { lowStockCount: lowItems.length, items: lowItems }
          };
        }

        case 'QH_INVENTORY_VALUATION': {
          let totalVal = 0;
          let totalUnits = 0;
          products.forEach(p => {
            const qty = parseFloat(p.stock != null ? p.stock : p.openingStock) || 0;
            const price = parseFloat(p.price || p.sellingPrice || p.costPrice) || 0;
            totalVal += (qty * price);
            totalUnits += qty;
          });
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_INVENTORY_VALUATION',
            domain: 'INVENTORY',
            apiEndpoint: '/api/omnisearch/inventory',
            apiParams: { filter: 'VALUATION' },
            title: `📦 Inventory Valuation: ₹${totalVal.toLocaleString('en-IN')}`,
            subtitle: `${totalUnits} total units across ${products.length} product SKUs`,
            data: { totalValuation: totalVal, totalUnits, productCount: products.length }
          };
        }

        case 'QH_PRODUCT_INFO': {
          const prod = entities.matchedProduct;
          if (prod) {
            const stk = parseFloat(prod.stock != null ? prod.stock : prod.openingStock) || 0;
            const price = parseFloat(prod.price || prod.sellingPrice) || 0;
            const cost = parseFloat(prod.costPrice) || 0;
            return {
              status: 'ANSWER',
              category: 'QUICK_HELP',
              capabilityId: 'QH_PRODUCT_INFO',
              domain: 'INVENTORY',
              apiEndpoint: '/api/omnisearch/inventory',
              apiParams: { query: prod.name },
              title: `📦 ${prod.name}: ${stk} ${prod.unit || 'pcs'} in Stock`,
              subtitle: `Selling: ₹${price.toLocaleString('en-IN')} • Cost: ₹${cost.toLocaleString('en-IN')} • Min Stock: ${prod.minStockAlert || 5}`,
              data: { productName: prod.name, stock: stk, price, costPrice: cost, unit: prod.unit }
            };
          }
          const pName = entities.productName || entities.targetName || 'Product';
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_PRODUCT_INFO',
            domain: 'INVENTORY',
            apiEndpoint: '/api/omnisearch/inventory',
            apiParams: { query: pName },
            title: `📦 Product Info: ${pName}`,
            subtitle: `Fetching product stock and pricing details...`,
            data: { productName: pName }
          };
        }

        // ─── 8. HR & STAFF ───
        case 'QH_HR_SUMMARY': {
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_HR_SUMMARY',
            domain: 'HR',
            apiEndpoint: '/api/omnisearch/hr',
            apiParams: {},
            title: `👥 Staff & Attendance: ${staff.length} Active Employees`,
            subtitle: `Today (${todayStr}): Tap to view present, absent, and on-leave staff`,
            data: { employeeCount: staff.length, date: todayStr },
            actions: [
              { label: 'Open Attendance', route: 'hr', hrTab: 'attendance' },
              { label: 'Staff Directory', route: 'hr', hrTab: 'staff' }
            ]
          };
        }

        case 'QH_EMPLOYEE_INFO': {
          const sName = entities.staffName || entities.targetName || 'Employee';
          const emp = entities.matchedStaff || staff.find(s => s.name && s.name.toLowerCase().includes(sName.toLowerCase()));
          if (emp) {
            const sal = parseFloat(emp.monthlyBaseSalary || emp.salary) || 0;
            const adv = parseFloat(emp.currentAdvanceBalance || emp.advanceBalance) || 0;
            return {
              status: 'ANSWER',
              category: 'QUICK_HELP',
              capabilityId: 'QH_EMPLOYEE_INFO',
              domain: 'HR',
              apiEndpoint: '/api/omnisearch/hr',
              apiParams: { query: emp.name, employeeId: emp.id },
              title: `👥 ${emp.name} (${emp.role || emp.designation || 'Staff'})`,
              subtitle: `Salary: ₹${sal.toLocaleString('en-IN')}/mo • Advance Due: ₹${adv.toLocaleString('en-IN')} • 📞 ${emp.phone || 'N/A'}`,
              data: { employeeName: emp.name, role: emp.role, salary: sal, advanceBalance: adv, phone: emp.phone, id: emp.id }
            };
          }
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_EMPLOYEE_INFO',
            domain: 'HR',
            apiEndpoint: '/api/omnisearch/hr',
            apiParams: { query: sName },
            title: `👥 Employee Details: ${sName}`,
            subtitle: `Fetching salary, advance balance, and attendance history...`,
            data: { employeeName: sName }
          };
        }

        // ─── 9. FIRM DETAILS ───
        case 'QH_FIRM_BANK': {
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_FIRM_BANK',
            domain: 'FIRM',
            title: `🏛️ Bank Details: ${firm.bankName || 'Not configured'}`,
            subtitle: `A/C: ${firm.accountNumber || 'N/A'} • IFSC: ${firm.ifscCode || 'N/A'} • Branch: ${firm.branchName || 'Main'}`,
            data: {
              bankName: firm.bankName,
              accountNumber: firm.accountNumber,
              ifsc: firm.ifscCode,
              branch: firm.branchName
            }
          };
        }

        case 'QH_FIRM_TAX': {
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_FIRM_TAX',
            domain: 'FIRM',
            title: `🏛️ Firm GSTIN: ${firm.gstin || 'Unregistered / N/A'}`,
            subtitle: `${firm.firmName || 'Store'} • Address: ${firm.address || 'N/A'}`,
            data: {
              gstin: firm.gstin,
              firmName: firm.firmName,
              address: firm.address
            }
          };
        }

        // ─── 10. SALARIES & PAYROLL ───
        case 'QH_SALARIES_SUMMARY': {
          const sName = entities.staffName || entities.targetName;
          const totalMonthlyPayroll = staff.reduce((acc, s) => acc + (parseFloat(s.monthlyBaseSalary || s.salary) || 0), 0);
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_SALARIES_SUMMARY',
            domain: 'HR',
            apiEndpoint: '/api/omnisearch/salaries',
            apiParams: { query: sName, period: entities.period || 'THIS_MONTH' },
            title: sName ? `💵 Staff Salary: ${sName}` : `💵 Total Monthly Payroll: ₹${totalMonthlyPayroll.toLocaleString('en-IN')}`,
            subtitle: sName ? `Fetching salary slips and advance deductions...` : `Active payroll across ${staff.length} staff members`,
            data: { totalPayroll: totalMonthlyPayroll, staffCount: staff.length, targetEmployee: sName },
            actions: [
              { label: 'Open Payroll', route: 'hr', hrTab: 'payroll' },
              { label: 'Staff Directory', route: 'hr', hrTab: 'staff' }
            ]
          };
        }

        // ─── 11. STAFF ADVANCES ───
        case 'QH_ADVANCES_SUMMARY': {
          const sName = entities.staffName || entities.targetName;
          let totalAdv = 0;
          let advStaffCount = 0;
          staff.forEach(s => {
            const adv = parseFloat(s.currentAdvanceBalance || s.advanceBalance) || 0;
            if (adv > 0) {
              totalAdv += adv;
              advStaffCount++;
            }
          });
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_ADVANCES_SUMMARY',
            domain: 'HR',
            apiEndpoint: '/api/omnisearch/advances',
            apiParams: { query: sName },
            title: sName ? `💵 Advance Due: ${sName}` : `💵 Staff Advances Outstanding: ₹${totalAdv.toLocaleString('en-IN')}`,
            subtitle: sName ? `Fetching advance history...` : `${advStaffCount} employee(s) have pending advance balances`,
            data: { totalOutstandingAdvances: totalAdv, staffWithAdvancesCount: advStaffCount, targetEmployee: sName },
            actions: [
              { label: 'Manage Advances', route: 'hr', hrTab: 'payroll' }
            ]
          };
        }

        // ─── 12. SALES RETURNS & CREDIT NOTES ───
        case 'QH_RETURNS_SUMMARY': {
          const returns = Array.isArray(ctx.returns) ? ctx.returns : [];
          const totalVal = returns.reduce((acc, r) => acc + (parseFloat(r.netTotal || r.amount) || 0), 0);
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_RETURNS_SUMMARY',
            domain: 'RETURN',
            apiEndpoint: '/api/omnisearch/returns',
            apiParams: { period: entities.period || 'THIS_MONTH' },
            title: `↩️ Sales Returns: ₹${totalVal.toLocaleString('en-IN')}`,
            subtitle: `${returns.length} return note(s) recorded`,
            data: { totalReturnValue: totalVal, returnCount: returns.length },
            actions: [
              { label: 'View Sales Returns', route: 'invoices', subTab: 'returns' }
            ]
          };
        }

        // ─── 13. COLLECTIONS & RECEIPTS ───
        case 'QH_COLLECTIONS_SUMMARY': {
          const totalPaid = invoices.reduce((acc, inv) => acc + (parseFloat(inv.paidAmount) || 0), 0);
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_COLLECTIONS_SUMMARY',
            domain: 'PAYMENT',
            apiEndpoint: '/api/omnisearch/collections',
            apiParams: { period: entities.period || 'TODAY' },
            title: `💰 Total Payment Collections: ₹${totalPaid.toLocaleString('en-IN')}`,
            subtitle: `Customer payments received across ${invoices.length} invoices`,
            data: { totalCollection: totalPaid, invoiceCount: invoices.length },
            actions: [
              { label: 'View Invoices', route: 'invoices', subTab: 'invoices' }
            ]
          };
        }

        // ─── 14. VENDOR PAYOUTS ───
        case 'QH_VENDOR_PAYOUTS_SUMMARY': {
          const vName = entities.targetName || (entities.matchedParty && entities.matchedParty.name);
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_VENDOR_PAYOUTS_SUMMARY',
            domain: 'PAYMENT',
            apiEndpoint: '/api/omnisearch/vendor-payouts',
            apiParams: { query: vName, period: entities.period || 'THIS_MONTH' },
            title: vName ? `💳 Vendor Payments to ${vName}` : `💳 Vendor Payouts & Supplier Payments`,
            subtitle: `Authoritative outgoing payment transactions to suppliers`,
            data: { targetVendor: vName },
            actions: [
              { label: 'Vendor Directory', route: 'firm', tab: 'parties' }
            ]
          };
        }

        // ─── 15. PURCHASE ORDERS ───
        case 'QH_PURCHASE_ORDERS_SUMMARY': {
          const pos = Array.isArray(ctx.purchaseOrders) ? ctx.purchaseOrders : [];
          const totalPoVal = pos.reduce((acc, p) => acc + (parseFloat(p.totalAmount || p.netTotal) || 0), 0);
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_PURCHASE_ORDERS_SUMMARY',
            domain: 'DOCUMENT',
            apiEndpoint: '/api/omnisearch/purchase-orders',
            apiParams: {},
            title: `📦 Purchase Orders (${pos.length} total): ₹${totalPoVal.toLocaleString('en-IN')}`,
            subtitle: `Supplier order logs & procurement requests`,
            data: { totalPoAmount: totalPoVal, poCount: pos.length },
            actions: [
              { label: 'Purchase Orders', route: 'firm', tab: 'paperwork', subTab: 'orders' }
            ]
          };
        }

        // ─── 16. STOCK MOVEMENTS ───
        case 'QH_STOCK_MOVEMENTS_SUMMARY': {
          const pName = entities.productName || entities.targetName;
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_STOCK_MOVEMENTS_SUMMARY',
            domain: 'INVENTORY',
            apiEndpoint: '/api/omnisearch/stock-movements',
            apiParams: { query: pName },
            title: pName ? `📊 Stock Movements: ${pName}` : `📊 Stock Inward / Outward Movement Logs`,
            subtitle: `Real-time stock audit and godown transfers`,
            data: { targetProduct: pName },
            actions: [
              { label: 'Inventory', route: 'firm', tab: 'inventory' }
            ]
          };
        }

        // ─── 17. LEDGER & STATEMENTS ───
        case 'QH_LEDGER_SUMMARY': {
          const isVendor = entities.matchedParty != null || /\b(vendor|supplier|party)\b/i.test(normalized.lower);
          const entityName = (isVendor && entities.matchedParty ? entities.matchedParty.name : (entities.matchedCustomer ? entities.matchedCustomer.name : entities.targetName)) || 'Customer';
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_LEDGER_SUMMARY',
            domain: 'STATEMENT',
            apiEndpoint: '/api/omnisearch/ledger',
            apiParams: { type: isVendor ? 'VENDOR' : 'CUSTOMER', query: entityName },
            title: `📑 Account Statement / Ledger: ${entityName}`,
            subtitle: `Complete debit, credit, invoice, and payment history`,
            data: { entityName, entityType: isVendor ? 'VENDOR' : 'CUSTOMER' },
            actions: [
              { label: 'Open Statements', route: 'firm', tab: 'paperwork', subTab: 'statements' }
            ]
          };
        }

        // ─── 18. GOALS & HABITS ───
        case 'QH_GOALS_SUMMARY': {
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_GOALS_SUMMARY',
            domain: 'PLANNER',
            title: `🎯 Goals, Streaks & Daily Habits`,
            subtitle: `Track active savings targets, check-in streaks and milestones`,
            actions: [
              { label: 'Open Goals Hub', route: 'planner', plannerTab: 'goals' }
            ]
          };
        }

        // ─── 19. SAVINGS & RESERVES ───
        case 'QH_SAVINGS_SUMMARY': {
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_SAVINGS_SUMMARY',
            domain: 'PLANNER',
            title: `💰 Savings, Investments & Reserves`,
            subtitle: `Review SIPs, mutual funds, gold deposits and financial reserve buffers`,
            actions: [
              { label: 'Open Savings Ledger', route: 'planner', plannerTab: 'savings' }
            ]
          };
        }

        // ─── 20. DATABASE BACKUP & INTEGRITY ───
        case 'QH_BACKUP_STATUS': {
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_BACKUP_STATUS',
            domain: 'SETTINGS',
            title: `💾 Database Snapshot & Backup Health`,
            subtitle: `Export or verify offline database snapshots and integrity`,
            actions: [
              { label: 'Open Backup Settings', route: 'settings', tab: 'backup' }
            ]
          };
        }
      }

      return {
        status: 'QUICK_HELP',
        category: 'QUICK_HELP',
        capabilityId: capabilityId,
        title: `💡 Quick Help: ${capabilityId.replace('QH_', '')}`,
        subtitle: `Fetching authoritative business data...`
      };
    }
  };

  // ─────────────────────────────────────────────────────────────────────────────
  // 7. AUTHORITATIVE SINGLE-QUERY PIPELINE API
  // ─────────────────────────────────────────────────────────────────────────────
  const OmnibarPipeline = {
    processQuery(rawQuery, ctx = {}) {
      if (!rawQuery || !rawQuery.trim()) {
        return { status: 'NO_MATCH', category: 'SEARCH', message: '' };
      }

      // 1. Check for compound queries across conjunction boundaries
      const segments = QuerySegmenter.segment(rawQuery);
      if (segments.length > 1) {
        return this.processCompoundQuery(segments, rawQuery, ctx);
      }

      return this.processSingleQuery(rawQuery, ctx);
    },

    processCompoundQuery(segments, rawQuery, ctx = {}) {
      const results = [];
      let currentCtx = { ...ctx };

      for (const seg of segments) {
        const res = this.processSingleQuery(seg, currentCtx);
        results.push(res);

        // Propagate entity or calculation outcomes to subsequent segments
        const firmId = currentCtx.activeFirmId || currentCtx.firmId || (currentCtx.firm && currentCtx.firm.id) || 'default';
        if (res && res.data) {
          if (res.data.customerName || res.data.customer || res.data.customerId) {
            const custObj = res.data.customer || { name: res.data.customerName, id: res.data.customerId, balance: res.data.balance };
            currentCtx.sessionContext = {
              ...currentCtx.sessionContext,
              lastCustomer: custObj,
              lastEntity: custObj
            };
            SessionContextManager.setContext(firmId, {
              lastCustomer: custObj,
              lastEntity: custObj
            });
          }
          if (res.data.result != null || res.data.total != null) {
            const val = res.data.result != null ? res.data.result : res.data.total;
            currentCtx.sessionContext = {
              ...currentCtx.sessionContext,
              ans: val
            };
            SessionContextManager.setContext(firmId, {
              ans: val
            });
          }
        }
      }

      const primary = results[0];
      const secondary = results[1] || results[0];

      return {
        status: 'COMPOSITE',
        category: 'COMPOSITE',
        capabilityId: 'COMP_MULTI_INTENT',
        title: results.map(r => r.title || r.capabilityId || 'Result').join(' ➔ '),
        subtitle: `Compound Multi-Intent: ${results.map((r, i) => `(${i + 1}) ${r.category || 'Action'}`).join(' • ')}`,
        primary,
        secondary,
        segments: results,
        data: {
          segments: results,
          segmentCount: results.length
        }
      };
    },

    processSingleQuery(rawQuery, ctx = {}) {
      if (!rawQuery || !rawQuery.trim()) {
        return { status: 'NO_MATCH', category: 'SEARCH', message: '' };
      }

      // 1. Deterministic NLP Normalization
      const normalized = DeterministicNormalizer.normalize(rawQuery);

      // 2. Capability Classification
      const classification = CapabilityClassifier.classify(normalized, ctx);

      // 3. Entity & Role Extraction
      const entities = RoleResolver.extractEntities(normalized, ctx);

      // 4. Core Query Execution
      const res = this._executeSingleQuery(normalized, classification, entities, ctx);

      // 5. Unconditional Session Context Persistence (Entity & Calculation memory)
      const firmId = ctx.activeFirmId || ctx.firmId || (ctx.firm && ctx.firm.id) || 'default';
      if (entities.matchedCustomer) {
        SessionContextManager.setContext(firmId, {
          lastCustomer: entities.matchedCustomer,
          lastEntity: entities.matchedCustomer
        });
      }
      if (res && res.data) {
        if (res.data.customerName || res.data.customer || res.data.customerId) {
          const custObj = res.data.customer || { name: res.data.customerName, id: res.data.customerId, balance: res.data.balance };
          SessionContextManager.setContext(firmId, {
            lastCustomer: custObj,
            lastEntity: custObj
          });
        }
        if (res.data.result != null || res.data.total != null) {
          const val = res.data.result != null ? res.data.result : res.data.total;
          SessionContextManager.setContext(firmId, {
            ans: val,
            lastCalculation: res.data
          });
        }
      }
      return res;
    },

    _executeSingleQuery(normalized, classification, entities, ctx = {}) {
      // Disambiguation Check
      if (entities.isAmbiguous && entities.matchingCandidates.length > 1) {
        return {
          status: 'AMBIGUOUS',
          category: 'QUICK_HELP',
          capabilityId: 'AMBIGUOUS_ENTITIES',
          title: `⚖️ Multiple matches found for "${entities.matchingCandidates[0].name}"`,
          subtitle: `Please select the intended entity:`,
          candidates: entities.matchingCandidates
        };
      }

      // 5. Execution / Evaluation
      // SPECIAL: Math & Calculators
      if (classification.category === 'SPECIAL') {
        switch (classification.capabilityId) {
          case 'SPEC_PAY_UPI_QR': {
            const firmObj = ctx.firm || {};
            const rawUpiId = (firmObj.upiId && firmObj.upiId.trim()) || (firmObj.upi && firmObj.upi.trim()) || 'merchant@upi';
            const firmName = (firmObj.firmName && firmObj.firmName.trim()) || 'RupeeCRM Merchant';
            const upiAmt = entities.amount || 0;
            const amtParam = upiAmt > 0 ? `&am=${upiAmt.toFixed(2)}` : '';
            const upiUrl = `upi://pay?pa=${rawUpiId}&pn=${encodeURIComponent(firmName)}${amtParam}&cu=INR`;
            const qrUrl = `/api/omnisearch/qr?size=260&data=${encodeURIComponent(upiUrl)}`;
            return {
              status: 'ANSWER',
              category: 'SPECIAL',
              capabilityId: 'SPEC_PAY_UPI_QR',
              title: upiAmt > 0 ? `📱 UPI Payment QR: ₹${upiAmt.toLocaleString('en-IN')}` : `📱 Live UPI Payment QR (${firmName})`,
              subtitle: `Pay to VPA: ${rawUpiId} (${firmName})`,
              data: { qrUrl, upiId: rawUpiId, amount: upiAmt, upiUrl, firmName }
            };
          }

          case 'SPEC_COMM_WHATSAPP': {
            const custName = entities.targetName || 'Customer';
            const balance = entities.amount || 0;
            return {
              status: 'ANSWER',
              category: 'SPECIAL',
              capabilityId: 'SPEC_COMM_WHATSAPP',
              title: `💬 WhatsApp Dues Reminder for ${custName}`,
              subtitle: `Generate instant WhatsApp payment reminder message`,
              data: { customerName: custName, balance }
            };
          }

          case 'SPEC_WORDS_TO_NUM': {
            const num = entities.amount || parseFloat(normalized.lower.replace(/[^0-9.]/g, '')) || 0;
            const words = WordsEngine.toWords(num);
            return {
              status: 'ANSWER',
              category: 'SPECIAL',
              capabilityId: 'SPEC_WORDS_TO_NUM',
              title: `✍️ ${words}`,
              subtitle: `Amount: ₹${num.toLocaleString('en-IN')}`,
              data: { number: num, words }
            };
          }

          case 'SPEC_NUM_TO_WORDS': {
            const numVal = WordsEngine.wordsToNumber(normalized.raw);
            if (numVal) {
              return {
                status: 'ANSWER',
                category: 'SPECIAL',
                capabilityId: 'SPEC_NUM_TO_WORDS',
                title: `🔢 Value: ₹${numVal.toLocaleString('en-IN')}`,
                subtitle: `Phrase: "${normalized.raw}" ➔ ₹${numVal}`,
                data: { number: numVal }
              };
            }
            break;
          }

          case 'SPEC_MATH_GST_EXCL':
          case 'SPEC_MATH_GST_INCL': {
            const gstData = CalculationEvaluator.evaluateGST(entities, normalized.raw, ctx);
            return {
              status: 'ANSWER',
              category: 'SPECIAL',
              capabilityId: classification.capabilityId,
              title: `🧮 ${gstData.summaryText}`,
              subtitle: `Base: ₹${gstData.base.toLocaleString('en-IN')} • GST (${gstData.rate}%): ₹${gstData.tax.toLocaleString('en-IN')} • Total: ₹${gstData.total.toLocaleString('en-IN')}`,
              data: gstData
            };
          }

          case 'SPEC_MATH_CHANGE': {
            const changeData = CalculationEvaluator.evaluateChange(entities);
            if (changeData) {
              return {
                status: 'ANSWER',
                category: 'SPECIAL',
                capabilityId: 'SPEC_MATH_CHANGE',
                title: `💵 ${changeData.summaryText}`,
                subtitle: `Tendered: ₹${changeData.paid.toLocaleString('en-IN')} • Bill: ₹${changeData.bill.toLocaleString('en-IN')} • Return: ₹${changeData.change.toLocaleString('en-IN')}`,
                data: changeData
              };
            }
            break;
          }

          case 'SPEC_MATH_SPLIT': {
            const splitData = CalculationEvaluator.evaluateSplit(entities, normalized.raw);
            if (splitData) {
              return {
                status: 'ANSWER',
                category: 'SPECIAL',
                capabilityId: 'SPEC_MATH_SPLIT',
                title: `👥 ${splitData.summaryText}`,
                subtitle: `Total: ₹${splitData.total.toLocaleString('en-IN')}`,
                data: splitData
              };
            }
            break;
          }

          case 'SPEC_MATH_DISCOUNT': {
            const discData = CalculationEvaluator.evaluateDiscount(entities, normalized.raw, ctx);
            if (discData) {
              return {
                status: 'ANSWER',
                category: 'SPECIAL',
                capabilityId: 'SPEC_MATH_DISCOUNT',
                title: `🏷️ ${discData.summaryText}`,
                subtitle: `Original: ₹${discData.amount.toLocaleString('en-IN')} • Disc: ₹${discData.discount.toLocaleString('en-IN')}`,
                data: discData
              };
            }
            break;
          }

          case 'SPEC_MATH_MARGIN': {
            const marginData = CalculationEvaluator.evaluateMargin(entities, normalized.raw, ctx);
            if (marginData) {
              const costVal = marginData.cost != null ? marginData.cost : marginData.result;
              const sellVal = marginData.selling != null ? marginData.selling : marginData.sellingPrice;
              return {
                status: 'ANSWER',
                category: 'SPECIAL',
                capabilityId: 'SPEC_MATH_MARGIN',
                title: `📈 ${marginData.summaryText}`,
                subtitle: `Cost: ₹${(costVal || 0).toLocaleString('en-IN')} • Selling: ₹${(sellVal || 0).toLocaleString('en-IN')}`,
                data: marginData
              };
            }
            break;
          }

          case 'SPEC_MATH_EMI': {
            const emiData = CalculationEvaluator.evaluateLoanEmi(entities, normalized.raw, ctx);
            if (emiData) {
              return {
                status: 'ANSWER',
                category: 'SPECIAL',
                capabilityId: 'SPEC_MATH_EMI',
                title: `🏦 ${emiData.summaryText}`,
                subtitle: `Principal: ₹${emiData.principal.toLocaleString('en-IN')} • Rate: ${emiData.rate}% • Tenure: ${emiData.years} yr(s)`,
                data: emiData
              };
            }
            break;
          }

          case 'SPEC_MATH_SI': {
            const siData = CalculationEvaluator.evaluateSimpleInterest(entities, normalized.raw, ctx);
            if (siData) {
              return {
                status: 'ANSWER',
                category: 'SPECIAL',
                capabilityId: 'SPEC_MATH_SI',
                title: `🏦 ${siData.summaryText}`,
                subtitle: `Principal: ₹${siData.principal.toLocaleString('en-IN')} • Rate: ${siData.rate}% • Total: ₹${siData.total.toLocaleString('en-IN')}`,
                data: siData
              };
            }
            break;
          }

          case 'SPEC_MATH_CI': {
            const ciData = CalculationEvaluator.evaluateCompoundInterest(entities, normalized.raw, ctx);
            if (ciData) {
              return {
                status: 'ANSWER',
                category: 'SPECIAL',
                capabilityId: 'SPEC_MATH_CI',
                title: `🏦 ${ciData.summaryText}`,
                subtitle: `Principal: ₹${ciData.principal.toLocaleString('en-IN')} • Rate: ${ciData.rate}% • Total Maturity: ₹${ciData.total.toLocaleString('en-IN')}`,
                data: ciData
              };
            }
            break;
          }

          case 'SPEC_MATH_SIP': {
            const sipData = CalculationEvaluator.evaluateSIP(entities, normalized.raw, ctx);
            if (sipData) {
              return {
                status: 'ANSWER',
                category: 'SPECIAL',
                capabilityId: 'SPEC_MATH_SIP',
                title: `📈 ${sipData.summaryText}`,
                subtitle: `Monthly: ₹${sipData.monthlyDeposit.toLocaleString('en-IN')} • Rate: ${sipData.rate}% • Tenure: ${sipData.years} yrs`,
                data: sipData
              };
            }
            break;
          }

          case 'SPEC_MATH_TDS': {
            const tdsData = CalculationEvaluator.evaluateTDS(entities, normalized.raw, ctx);
            if (tdsData) {
              return {
                status: 'ANSWER',
                category: 'SPECIAL',
                capabilityId: 'SPEC_MATH_TDS',
                title: `📋 ${tdsData.summaryText}`,
                subtitle: `Gross: ₹${tdsData.grossAmount.toLocaleString('en-IN')} • TDS (${tdsData.rate}%): ₹${tdsData.tdsAmount.toLocaleString('en-IN')} • Net: ₹${tdsData.netAmount.toLocaleString('en-IN')}`,
                data: tdsData
              };
            }
            break;
          }

          case 'SPEC_MATH_DATE': {
            const dateData = classification.data || CalculationEvaluator.evaluateDateMath(normalized.raw) || CalculationEvaluator.evaluateDateMath(normalized.lower);
            if (dateData) {
              return {
                status: 'ANSWER',
                category: 'SPECIAL',
                capabilityId: 'SPEC_MATH_DATE',
                title: `📅 ${dateData.summaryText}`,
                subtitle: dateData.type === 'DATE_DIFF' ? `Duration: ${dateData.days} Days` : `Calendar Calculation: ${dateData.date || dateData.iso}`,
                data: dateData
              };
            }
            break;
          }

          case 'SPEC_CONV_CURRENCY': {
            const curData = classification.data || CalculationEvaluator.evaluateCurrency(normalized.raw) || CalculationEvaluator.evaluateCurrency(normalized.transliterated || normalized.lower);
            if (curData) {
              return {
                status: 'ANSWER',
                category: 'SPECIAL',
                capabilityId: 'SPEC_CONV_CURRENCY',
                title: `💱 ${curData.summaryText}`,
                subtitle: `${curData.disclaimer}`,
                data: curData
              };
            }
            break;
          }

          case 'SPEC_CONV_UNIT': {
            const convData = CalculationEvaluator.evaluateUnitConversion(normalized.raw) || CalculationEvaluator.evaluateUnitConversion(normalized.transliterated || normalized.lower);
            if (convData) {
              return {
                status: 'ANSWER',
                category: 'SPECIAL',
                capabilityId: 'SPEC_CONV_UNIT',
                title: `⚖️ ${convData.summaryText}`,
                subtitle: `Converted from ${convData.fromUnit} to ${convData.toUnit}`,
                data: convData
              };
            }
            break;
          }

          case 'SPEC_MATH_ARITH': {
            const arithData = classification.data || CalculationEvaluator.evaluateArithmetic(normalized.raw) || CalculationEvaluator.evaluateArithmetic(normalized.transliterated || normalized.lower);
            if (arithData) {
              const resFormatted = arithData.formattedResult || arithData.formatted || String(arithData.result);
              return {
                status: 'ANSWER',
                category: 'SPECIAL',
                capabilityId: 'SPEC_MATH_ARITH',
                title: `🧮 ${arithData.summaryText || (arithData.expression + ' = ' + resFormatted)}`,
                subtitle: `Equation: ${arithData.expression} = ${resFormatted} • ${arithData.calculationType || 'Calculation'}`,
                data: arithData
              };
            }
            break;
          }
        }
      }

      // ACTION: Shortcuts, Previews, Navigations
      if (classification.category === 'ACTION') {
        if (classification.capabilityId === 'ACT_NAV_PAGE' || classification.capabilityId === 'ACT_NAV_SLASH') {
          return {
            status: 'NAVIGATE',
            category: 'ACTION',
            capabilityId: classification.capabilityId,
            target: classification.target,
            title: `⚡ Open ${(classification.target.page || 'PAGE').toUpperCase()}`,
            subtitle: `Navigate directly to ${classification.target.page}`
          };
        }
        if (classification.capabilityId === 'ACT_THEME_TOGGLE') {
          return {
            status: 'DIRECT_ACTION',
            category: 'ACTION',
            capabilityId: 'ACT_THEME_TOGGLE',
            title: `🎨 Toggle Theme`,
            subtitle: `Switch between Dark and Light mode`,
            action: 'THEME_TOGGLE'
          };
        }
        if (classification.capabilityId === 'ACT_THEME_DARK') {
          return {
            status: 'DIRECT_ACTION',
            category: 'ACTION',
            capabilityId: 'ACT_THEME_DARK',
            title: `🌙 Switch to Dark Mode`,
            subtitle: `Activate sleek dark theme interface`,
            action: 'THEME_DARK'
          };
        }
        if (classification.capabilityId === 'ACT_THEME_LIGHT') {
          return {
            status: 'DIRECT_ACTION',
            category: 'ACTION',
            capabilityId: 'ACT_THEME_LIGHT',
            title: `☀️ Switch to Light Mode`,
            subtitle: `Activate clean light theme interface`,
            action: 'THEME_LIGHT'
          };
        }
        if (classification.capabilityId === 'ACT_APP_LOCK') {
          return {
            status: 'DIRECT_ACTION',
            category: 'ACTION',
            capabilityId: 'ACT_APP_LOCK',
            title: `🔒 Lock Screen`,
            subtitle: `Lock current user session into Safe Mode`,
            action: 'APP_LOCK'
          };
        }
        if (classification.executionType === 'CONFIRM') {
          const capId = classification.capabilityId;

          // 1. Task / Reminder Action
          if (capId === 'ACT_CREATE_TASK') {
            const isRem = (entities.itemType === 'reminder' || entities.isScheduled || /remind|alarm|yaad|aathvan|athvan/i.test(normalized.lower));
            const cleanTitle = entities.title || 'New Task';
            const titleStr = isRem ?
              `⏰ Set Reminder: "${cleanTitle}"` :
              `✅ Add Task: "${cleanTitle}"`;
            const subStr = isRem ?
              (entities.isScheduled ? `📅 Scheduled for ${entities.timeFormatted || entities.dateLabel} • Click to edit & save reminder` : `Add to Reminders • Click to edit & save reminder`) :
              `Add to Planner Board • Click to edit & save task`;
            return {
              status: 'ACTION_PREVIEW',
              category: 'ACTION',
              capabilityId: 'ACT_CREATE_TASK',
              title: titleStr,
              subtitle: subStr,
              icon: isRem ? '⏰' : '✅',
              badge: '⚡ Action',
              entities: entities,
              editableConfig: {
                kind: isRem ? 'reminder' : 'task',
                initialValues: {
                  title: cleanTitle,
                  dueDate: entities.dueDate || '',
                  timeFormatted: entities.timeFormatted || '',
                  type: isRem ? 'reminder' : 'task',
                  priority: 'MEDIUM'
                }
              }
            };
          }

          // 2. Expense Action
          if (capId === 'ACT_CREATE_EXPENSE') {
            const cleanExpense = entities.title && entities.title !== 'Business Item' ? entities.title : 'General Expense';
            const amt = entities.amount || 0;
            return {
              status: 'ACTION_PREVIEW',
              category: 'ACTION',
              capabilityId: 'ACT_CREATE_EXPENSE',
              title: `💸 Record Expense: ₹${amt.toLocaleString('en-IN')}${cleanExpense !== 'General Expense' ? ' (' + cleanExpense + ')' : ''}`,
              subtitle: `Date: ${entities.dateLabel || 'Today'} • Mode: Cash • Click to edit & save`,
              icon: '💸',
              badge: '⚡ Action',
              entities: entities,
              editableConfig: {
                kind: 'expense',
                initialValues: {
                  title: cleanExpense,
                  amount: amt,
                  category: 'General',
                  expenseDate: entities.dueDate || new Date().toISOString().slice(0, 10),
                  paymentMode: 'Cash'
                }
              }
            };
          }

          // 3. Sticky Note Action
          if (capId === 'ACT_CREATE_NOTE') {
            return {
              status: 'ACTION_PREVIEW',
              category: 'ACTION',
              capabilityId: 'ACT_CREATE_NOTE',
              title: `📝 Save Sticky Note: "${entities.title || 'New Note'}"`,
              subtitle: `Pin to Planner Board • Click to edit & save note`,
              icon: '📝',
              badge: '⚡ Action',
              entities: entities,
              editableConfig: {
                kind: 'note',
                initialValues: {
                  title: entities.title || 'New Note',
                  color: 'yellow'
                }
              }
            };
          }

          // 4. Customer Registration Action
          if (capId === 'ACT_CREATE_CUSTOMER') {
            return {
              status: 'ACTION_PREVIEW',
              category: 'ACTION',
              capabilityId: 'ACT_CREATE_CUSTOMER',
              title: `👤 Register Customer: ${entities.customerName || 'New Customer'}${entities.phone ? ' (📞 ' + entities.phone + ')' : ''}`,
              subtitle: `${entities.city ? 'City: ' + entities.city + ' • ' : ''}Opening Due: ₹${(entities.amount || 0).toLocaleString('en-IN')} • Click to register`,
              icon: '👤',
              badge: '⚡ Action',
              entities: entities,
              editableConfig: {
                kind: 'customer',
                initialValues: {
                  name: entities.customerName || 'New Customer',
                  phone: entities.phone || '',
                  city: entities.city || '',
                  openingBalance: entities.amount || 0
                }
              }
            };
          }

          // 5. Attendance Action
          if (capId === 'ACT_MARK_ATTENDANCE') {
            return {
              status: 'ACTION_PREVIEW',
              category: 'ACTION',
              capabilityId: 'ACT_MARK_ATTENDANCE',
              title: `📋 Mark Attendance: ${entities.staffName || 'Staff'} (${entities.attendanceStatus || 'Present'})`,
              subtitle: `Date: ${entities.dateLabel || 'Today'} • Click to record attendance`,
              icon: '📋',
              badge: '⚡ Action',
              entities: entities,
              editableConfig: {
                kind: 'attendance',
                initialValues: {
                  employeeName: entities.staffName || '',
                  status: entities.attendanceStatus || 'PRESENT',
                  date: entities.dueDate || new Date().toISOString().slice(0, 10)
                }
              }
            };
          }

          // 6. Salary Advance Action
          if (capId === 'ACT_RECORD_ADVANCE') {
            return {
              status: 'ACTION_PREVIEW',
              category: 'ACTION',
              capabilityId: 'ACT_RECORD_ADVANCE',
              title: `💰 Record Salary Advance: ${entities.staffName || 'Staff'} (₹${(entities.amount || 0).toLocaleString('en-IN')})`,
              subtitle: `Date: ${entities.dateLabel || 'Today'} • Click to record salary advance`,
              icon: '💰',
              badge: '⚡ Action',
              entities: entities,
              editableConfig: {
                kind: 'advance',
                initialValues: {
                  employeeName: entities.staffName || '',
                  amount: entities.amount || 0,
                  date: entities.dueDate || new Date().toISOString().slice(0, 10),
                  reason: 'Salary Advance'
                }
              }
            };
          }

          return {
            status: 'ACTION_PREVIEW',
            category: 'ACTION',
            capabilityId: classification.capabilityId,
            title: `⚡ Confirm Action: ${classification.capabilityId.replace('ACT_', '')}`,
            subtitle: `Review parameters before executing`,
            entities: entities
          };
        }
      }

      // QUICK_HELP: Authoritative Business Data Query
      if (classification.category === 'QUICK_HELP') {
        return QuickHelpAdapter.evaluate(classification.capabilityId, entities, normalized, ctx);
      }

      // Default: Subordinate Search Fallback
      return {
        status: 'SEARCH_RECORDS',
        category: 'SEARCH',
        capabilityId: 'SEARCH_FALLBACK',
        normalized: normalized
      };
    }
  };

  // ─────────────────────────────────────────────────────────────────────────────
  // 9. QUICK ACTION COMMAND PARSER (GESTURE-BASED DASHBOARD ACTIONS)
  // ─────────────────────────────────────────────────────────────────────────────
  const QuickActionParser = {
    CATEGORY_KEYWORDS: {
      'Office Supplies': [
        'office', 'supplies', 'stationery', 'stationary', 'paper', 'print', 'printing', 'pen', 'pens',
        'ink', 'toner', 'courier', 'postal', 'postage', 'envelope', 'notebook', 'files', 'folder'
      ],
      'Rent & Facilities': [
        'rent', 'lease', 'shop rent', 'godown rent', 'office rent', 'maintenance', 'cleaning',
        'repair', 'repairs', 'plumbing', 'electrical repair', 'whitewash', 'painting', 'pest control'
      ],
      'Utilities': [
        'electricity', 'electric', 'power', 'power bill', 'light bill', 'bijli', 'bijli bill',
        'water', 'water bill', 'pani bill', 'wifi', 'internet', 'broadband', 'phone', 'mobile',
        'telephone', 'recharge', 'gas', 'cylinder'
      ],
      'Salaries & Wages': [
        'salary', 'salaries', 'wages', 'payout', 'staff', 'employee', 'staff salary', 'advance salary',
        'bonus', 'incentive', 'daily wage', 'majuri', 'vetan', 'tankha'
      ],
      'Travel & Transport': [
        'fuel', 'petrol', 'diesel', 'cng', 'travel', 'travelling', 'taxi', 'cab', 'uber', 'ola',
        'auto', 'rickshaw', 'bus', 'train', 'flight', 'air ticket', 'toll', 'toll tax', 'parking',
        'transport', 'freight', 'tempo', 'lorry', 'delivery charge'
      ],
      'Taxes & Legal': [
        'gst', 'tax', 'tds', 'income tax', 'advance tax', 'professional tax', 'pt', 'audit',
        'ca fee', 'advocate', 'legal', 'license', 'licence', 'challan', 'penalty', 'fine', 'stamp'
      ],
      'Marketing & Ads': [
        'marketing', 'ad', 'ads', 'advertising', 'facebook ad', 'google ad', 'instagram ad',
        'banner', 'pamphlet', 'leaflet', 'hoarding', 'promotion', 'sms marketing', 'whatsapp campaign'
      ],
      'Miscellaneous': [
        'chai', 'tea', 'coffee', 'water jar', 'biscuits', 'snacks', 'refreshment', 'puja', 'mandir',
        'donation', 'dan', 'tip', 'general', 'misc', 'other'
      ]
    },

    inferExpenseCategory(str) {
      if (!str) return 'Miscellaneous';
      const lower = str.toLowerCase();
      for (const [cat, keywords] of Object.entries(this.CATEGORY_KEYWORDS)) {
        for (const kw of keywords) {
          const regex = new RegExp(`\\b${kw.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\b`, 'i');
          if (regex.test(lower)) {
            return cat;
          }
        }
      }
      return 'Miscellaneous';
    },

    extractPaymentMode(str) {
      if (!str) return 'UPI';
      const lower = str.toLowerCase();
      if (/\b(?:cash|roka|rokad|nagad)\b/i.test(lower)) return 'Cash';
      if (/\b(?:bank\s*transfer|neft|rtgs|imps|cheque|check|wire|online\s*transfer)\b/i.test(lower)) return 'Bank Transfer';
      if (/\b(?:card|credit\s*card|debit\s*card|pos|swipe)\b/i.test(lower)) return 'Credit/Debit Card';
      if (/\b(?:upi|gpay|google\s*pay|phonepe|paytm|bhim|qr)\b/i.test(lower)) return 'UPI';
      return 'UPI';
    },

    extractTags(str) {
      if (!str) return { cleanText: str, tags: [] };
      const tagMatches = [];
      const clean = str.replace(/#([\w-]+)/g, (match, tag) => {
        tagMatches.push(tag);
        return ' ';
      }).replace(/\s+/g, ' ').trim();
      return { cleanText: clean, tags: tagMatches };
    },

    parse(type, rawText, ctx = {}) {
      const activeFirmId = ctx.activeFirmId || (ctx.firm && ctx.firm.id) || 1;
      const customers = ctx.customers || [];
      const text = (rawText || '').trim();

      // Extract explicit @customer tag
      let explicitCustomerName = null;
      let textWithoutTags = text.replace(/@([a-zA-Z0-9_\s]+?)(?=\s+#|\s+\d|\s*$)/g, (match, p1) => {
        explicitCustomerName = p1.trim();
        return '';
      }).trim();

      // Extract hashtags
      const tags = [];
      textWithoutTags = textWithoutTags.replace(/#([a-zA-Z0-9_]+)/g, (match, p1) => {
        tags.push(p1);
        return '';
      }).trim();

      // Match Customer Entity
      let matchedCustomer = null;
      let ambiguousCustomers = [];
      let textWithoutCustomer = textWithoutTags;

      const atMatch = textWithoutCustomer.match(/@([a-zA-Z0-9\s._-]+)/);
      if (atMatch) {
        const queryName = atMatch[1].trim().toLowerCase();
        const candidateMatches = customers.filter(c => {
          const cName = (c.name || c.customerName || '').toLowerCase();
          return cName.includes(queryName);
        });
        if (candidateMatches.length === 1) {
          matchedCustomer = candidateMatches[0];
          textWithoutCustomer = textWithoutCustomer.replace(atMatch[0], ' ').replace(/\s+/g, ' ').trim();
        } else if (candidateMatches.length > 1) {
          ambiguousCustomers = candidateMatches.slice(0, 4);
        }
      } else if (type === 'reminder' || type === 'task') {
        const candidateMatches = [];
        for (const c of customers) {
          const cFullName = (c.name || c.customerName || '').trim();
          if (!cFullName) continue;
          const fullRegex = new RegExp(`\\b${cFullName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\b`, 'i');
          if (fullRegex.test(textWithoutCustomer)) {
            candidateMatches.push(c);
            continue;
          }
          const tokens = cFullName.split(/\s+/).filter(t => t.length >= 3 && !/^(the|and|pvt|ltd|inc|co|corp|m\/s|mr|mrs|ms|dr)\b/i.test(t));
          if (tokens.length > 0) {
            const firstTok = tokens[0];
            const tokRegex = new RegExp(`\\b${firstTok.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\b`, 'i');
            if (tokRegex.test(textWithoutCustomer)) {
              candidateMatches.push(c);
            }
          }
        }
        const uniqueCandidates = Array.from(new Set(candidateMatches));
        if (uniqueCandidates.length === 1) {
          matchedCustomer = uniqueCandidates[0];
        } else if (uniqueCandidates.length > 1) {
          ambiguousCustomers = uniqueCandidates.slice(0, 4);
        }
      }

      const dt = RoleResolver.extractDateTime(textWithoutCustomer);
      let textWithoutDate = textWithoutCustomer;
      if (dt && dt.rawMatch) {
        const parts = dt.rawMatch.split(/\s+/).filter(Boolean);
        for (const p of parts) {
          textWithoutDate = textWithoutDate.replace(new RegExp(`\\b${p.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\b`, 'gi'), ' ');
        }
        textWithoutDate = textWithoutDate.replace(/\s+/g, ' ').trim();
      }

      // EXPENSE PARSER
      if (type === 'expense') {
        if (/(?:^|\s)-(?:\d|₹|rs)/i.test(textWithoutTags)) {
          return {
            valid: false,
            missing: ['amount'],
            error: 'Expense amount must be positive (e.g. "Fuel 500")',
            payload: null,
            preview: null,
            ambiguousCustomers
          };
        }
        const amounts = RoleResolver.extractAmounts(textWithoutTags);
        if (amounts && amounts.length > 1) {
          return {
            valid: false,
            missing: ['amount'],
            error: 'Multiple amounts detected. Please specify a single amount (e.g. "Fuel 500")',
            payload: null,
            preview: null,
            ambiguousCustomers
          };
        }
        const amount = (amounts && amounts.length > 0) ? amounts[0].val : null;

        if (!amount || isNaN(amount) || amount <= 0) {
          return {
            valid: false,
            missing: ['amount'],
            error: 'Please enter an amount (e.g. "Fuel 500")',
            payload: null,
            preview: null,
            ambiguousCustomers
          };
        }

        let cleanTitle = textWithoutDate;
        if (amounts && amounts.length > 0) {
          for (const a of amounts) {
            if (a.raw) {
              cleanTitle = cleanTitle.replace(new RegExp(`\\b${a.raw.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\b`, 'gi'), ' ');
            }
          }
        }

        cleanTitle = cleanTitle
          .replace(/\b(paid|spent|expense|kharcha|kharch|for|rs\.?|inr|₹|cash|upi|gpay|phonepe|card|bank transfer|neft|cheque|today|yesterday)\b/gi, ' ')
          .replace(/\s+/g, ' ')
          .trim();

        if (!cleanTitle) {
          cleanTitle = 'General Expense';
        } else {
          cleanTitle = cleanTitle.charAt(0).toUpperCase() + cleanTitle.slice(1);
        }

        const category = this.inferExpenseCategory(textWithoutTags);
        const paymentMode = this.extractPaymentMode(textWithoutTags);
        const expenseDate = (dt && dt.isoDate) ? dt.isoDate : (new Date().toISOString().slice(0, 10));

        const payload = {
          title: cleanTitle,
          amount: parseFloat(amount.toFixed(2)),
          category: category,
          expenseDate: expenseDate,
          paymentMode: paymentMode,
          notes: '',
          tags: tags,
          firmId: activeFirmId,
          customerId: matchedCustomer ? matchedCustomer.id : null
        };

        const preview = {
          title: cleanTitle,
          amount: amount,
          amountFormatted: `₹${amount.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`,
          category: category,
          paymentMode: paymentMode,
          dateLabel: (dt && dt.dateLabel) ? dt.dateLabel : 'Today',
          customerName: matchedCustomer ? (matchedCustomer.name || matchedCustomer.customerName) : null
        };

        return {
          valid: true,
          missing: [],
          payload,
          preview,
          ambiguousCustomers
        };
      }

      // REMINDER & TASK PARSER
      if (type === 'reminder' || type === 'task') {
        let cleanTitle = dt && dt.cleanTitle ? dt.cleanTitle : textWithoutDate;
        cleanTitle = cleanTitle
          .replace(/^(?:remind\s+me\s+to|remind\s+me|reminder\s+for|set\s+reminder|task\s+to|task\s*:|todo\s+to|todo\s*:|please)\s+/gi, '')
          .replace(/\s+/g, ' ')
          .trim();

        if (!cleanTitle) {
          return {
            valid: false,
            missing: ['title'],
            error: type === 'reminder' ? 'Reminder title is required' : 'Task title is required',
            payload: null,
            preview: null,
            ambiguousCustomers
          };
        }

        cleanTitle = cleanTitle.charAt(0).toUpperCase() + cleanTitle.slice(1);

        let dueDate = null;
        let dateLabel = 'No due date';
        if (dt && dt.dueDate) {
          dueDate = dt.dueDate;
          dateLabel = dt.timeFormatted || dt.dateLabel || 'Scheduled';
        } else if (type === 'reminder') {
          const tomorrow = new Date();
          tomorrow.setDate(tomorrow.getDate() + 1);
          const yyyy = tomorrow.getFullYear();
          const mm = String(tomorrow.getMonth() + 1).padStart(2, '0');
          const dd = String(tomorrow.getDate()).padStart(2, '0');
          dueDate = `${yyyy}-${mm}-${dd}T09:00:00`;
          dateLabel = 'Tomorrow 9:00 AM';
        }

        const payload = {
          title: cleanTitle,
          note: '',
          dueDate: dueDate,
          type: type,
          status: 'TODO',
          progress: 0,
          tags: tags,
          firmId: activeFirmId,
          customerId: matchedCustomer ? matchedCustomer.id : null
        };

        const preview = {
          title: cleanTitle,
          dateLabel: dateLabel,
          customerName: matchedCustomer ? (matchedCustomer.name || matchedCustomer.customerName) : null,
          isScheduled: !!dt
        };

        return {
          valid: true,
          missing: [],
          payload,
          preview,
          ambiguousCustomers
        };
      }

      // NOTE PARSER
      if (type === 'note') {
        let cleanText = textWithoutTags;
        if (!cleanText) {
          return {
            valid: false,
            missing: ['content'],
            error: 'Note content is required',
            payload: null,
            preview: null,
            ambiguousCustomers
          };
        }

        const firstLine = cleanText.split('\n')[0].trim();
        const title = firstLine.length > 60 ? firstLine.slice(0, 60) + '...' : firstLine;

        const payload = {
          title: title || 'Quick Note',
          content: cleanText,
          tags: tags,
          firmId: activeFirmId,
          customerId: matchedCustomer ? matchedCustomer.id : null
        };

        const preview = {
          title: title,
          customerName: matchedCustomer ? (matchedCustomer.name || matchedCustomer.customerName) : null
        };

        return {
          valid: true,
          missing: [],
          payload,
          preview,
          ambiguousCustomers
        };
      }

      return {
        valid: false,
        error: `Unsupported action type: ${type}`,
        payload: null,
        preview: null
      };
    }
  };

  return {
    DeterministicNormalizer,
    SessionContextManager,
    QuerySegmenter,
    UnitRates,
    WordsEngine,
    RoleResolver,
    SafeArithmeticEngine,
    CalculationEvaluator,
    CapabilityClassifier,
    QuickHelpAdapter,
    OmnibarPipeline,
    QuickActionParser,
    parseQuickAction: (type, text, ctx) => QuickActionParser.parse(type, text, ctx),
    processQuery: (q, ctx) => OmnibarPipeline.processQuery(q, ctx),
    setFirmContext: (firmId, ctx) => SessionContextManager.setContext(firmId, ctx),
    getFirmContext: (firmId) => SessionContextManager.getContext(firmId),
    clearFirmContext: (firmId) => SessionContextManager.clearContext(firmId)
  };
}));

