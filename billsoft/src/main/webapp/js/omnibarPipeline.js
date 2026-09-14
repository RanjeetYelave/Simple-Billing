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
      // Number + Unit/Word: "30mm" -> "30 mm", "500upi" -> "500 upi", "10000rs" -> "10000 rs"
      s = s.replace(/(\d+(?:\.\d+)?)\s*(mm|cm|m|mtr|meter|meters|km|in|inch|inches|ft|feet|foot|yd|yard|yards|sqft|sqm|sqyd|guntha|bigha|acre|hectare|brass|g|gram|grams|gm|gms|kg|kgs|kilo|kilos|quintal|qtl|tonne|ton|t|tola|carat|ml|l|ltr|liter|liters|gal|gallon|usd|eur|gbp|aed|cad|aud|inr|rs|rupees|rupee|upi|k|lakh|lakhs|lac|lacs|cr|crore|crores|hazar|hazaar|haz)\b/gi, '$1 $2');
      // Currency Symbol + Number: "rs500" -> "rs 500", "$100" -> "$ 100", "₹1200" -> "₹ 1200"
      s = s.replace(/([₹$€£]|rs\.?|inr)\s*(\d+(?:\.\d+)?)/gi, '$1 $2');
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

    // Full 9-stage normalizer
    normalize(raw) {
      const rawTrimmed = (raw || '').trim();
      // 1. Unicode & punctuation cleanup
      let cleanPunct = rawTrimmed.replace(/['"`]/g, '').replace(/[?!;]+/g, ' ');
      // 2. Glued tokens decoupling
      let decoupled = this.splitGluedTokens(cleanPunct);
      // 3. Typo corrections
      let typoFixed = this.correctTypos(decoupled);
      // 4. Case normalization
      let lower = typoFixed.toLowerCase();
      // 5. Clean fillers
      let cleaned = this.cleanFillers(lower);

      return {
        raw: rawTrimmed,
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
    'cm': { base: 'length', toBase: 0.01, name: 'Centimeter' },
    'centimeter': { base: 'length', toBase: 0.01, name: 'Centimeter' },
    'm': { base: 'length', toBase: 1, name: 'Meter' },
    'mtr': { base: 'length', toBase: 1, name: 'Meter' },
    'meter': { base: 'length', toBase: 1, name: 'Meter' },
    'meters': { base: 'length', toBase: 1, name: 'Meter' },
    'km': { base: 'length', toBase: 1000, name: 'Kilometer' },
    'kilometer': { base: 'length', toBase: 1000, name: 'Kilometer' },
    'in': { base: 'length', toBase: 0.0254, name: 'Inch' },
    'inch': { base: 'length', toBase: 0.0254, name: 'Inch' },
    'inches': { base: 'length', toBase: 0.0254, name: 'Inch' },
    'ft': { base: 'length', toBase: 0.3048, name: 'Foot' },
    'feet': { base: 'length', toBase: 0.3048, name: 'Foot' },
    'foot': { base: 'length', toBase: 0.3048, name: 'Foot' },
    'yd': { base: 'length', toBase: 0.9144, name: 'Yard' },
    'yard': { base: 'length', toBase: 0.9144, name: 'Yard' },
    'yards': { base: 'length', toBase: 0.9144, name: 'Yard' },
    'gaj': { base: 'length', toBase: 0.9144, name: 'Gaj (Yard)' },

    // Area (base = sqft)
    'sqft': { base: 'area', toBase: 1, name: 'Square Feet' },
    'sqm': { base: 'area', toBase: 10.7639, name: 'Square Meter' },
    'sqyd': { base: 'area', toBase: 9, name: 'Square Yard' },
    'guntha': { base: 'area', toBase: 1089, name: 'Guntha' },
    'bigha': { base: 'area', toBase: 27225, name: 'Bigha' },
    'acre': { base: 'area', toBase: 43560, name: 'Acre' },
    'hectare': { base: 'area', toBase: 107639, name: 'Hectare' },
    'brass': { base: 'area', toBase: 100, name: 'Brass (100 sqft / 100 cuft)' },

    // Weight (base = gram)
    'mg': { base: 'weight', toBase: 0.001, name: 'Milligram' },
    'g': { base: 'weight', toBase: 1, name: 'Gram' },
    'gram': { base: 'weight', toBase: 1, name: 'Gram' },
    'grams': { base: 'weight', toBase: 1, name: 'Gram' },
    'gm': { base: 'weight', toBase: 1, name: 'Gram' },
    'gms': { base: 'weight', toBase: 1, name: 'Gram' },
    'kg': { base: 'weight', toBase: 1000, name: 'Kilogram' },
    'kgs': { base: 'weight', toBase: 1000, name: 'Kilogram' },
    'kilo': { base: 'weight', toBase: 1000, name: 'Kilogram' },
    'quintal': { base: 'weight', toBase: 100000, name: 'Quintal (100 kg)' },
    'qtl': { base: 'weight', toBase: 100000, name: 'Quintal' },
    'tonne': { base: 'weight', toBase: 1000000, name: 'Tonne (1000 kg)' },
    'ton': { base: 'weight', toBase: 1000000, name: 'Tonne' },
    't': { base: 'weight', toBase: 1000000, name: 'Tonne' },
    'tola': { base: 'weight', toBase: 11.6638, name: 'Tola' },
    'carat': { base: 'weight', toBase: 0.2, name: 'Carat' },

    // Volume (base = liter)
    'ml': { base: 'volume', toBase: 0.001, name: 'Milliliter' },
    'l': { base: 'volume', toBase: 1, name: 'Liter' },
    'ltr': { base: 'volume', toBase: 1, name: 'Liter' },
    'liter': { base: 'volume', toBase: 1, name: 'Liter' },
    'liters': { base: 'volume', toBase: 1, name: 'Liter' },
    'gal': { base: 'volume', toBase: 3.78541, name: 'Gallon (US)' },
    'gallon': { base: 'volume', toBase: 3.78541, name: 'Gallon (US)' },

    // Currency (approx base = INR)
    'inr': { base: 'currency', toBase: 1, name: 'Indian Rupee' },
    'rs': { base: 'currency', toBase: 1, name: 'Indian Rupee' },
    'rupees': { base: 'currency', toBase: 1, name: 'Indian Rupee' },
    'usd': { base: 'currency', toBase: 86.50, name: 'US Dollar' },
    'eur': { base: 'currency', toBase: 91.20, name: 'Euro' },
    'gbp': { base: 'currency', toBase: 109.80, name: 'British Pound' },
    'aed': { base: 'currency', toBase: 23.55, name: 'UAE Dirham' },
    'cad': { base: 'currency', toBase: 61.20, name: 'Canadian Dollar' },
    'aud': { base: 'currency', toBase: 55.40, name: 'Australian Dollar' }
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
      if (s.includes('dedh lakh') || s.includes('dedh lac')) return 150000;
      if (s.includes('dhai lakh') || s.includes('adhai lakh')) return 250000;
      if (s.includes('dedh crore') || s.includes('dedh cr')) return 15000000;
      if (s.includes('dhai crore') || s.includes('adhai crore')) return 25000000;

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

      if (entities.matchingCandidates.length === 1) {
        const ent = entities.matchingCandidates[0];
        entities.targetName = ent.name;
        entities.matchedEntity = ent;
        if (ent.type === 'CUSTOMER') entities.matchedCustomer = ent.raw;
        if (ent.type === 'VENDOR') entities.matchedParty = ent.raw;
        if (ent.type === 'STAFF') {
          entities.staffName = ent.name;
          entities.matchedStaff = ent.raw;
        }
        if (ent.type === 'PRODUCT') {
          entities.productName = ent.name;
          entities.matchedProduct = ent.raw;
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
        entities.title = cleanTask ? (cleanTask.charAt(0).toUpperCase() + cleanTask.slice(1)) : 'Business Item';
      }

      return entities;
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
    tokenize(exprStr) {
      if (!exprStr) return null;
      let s = exprStr.trim();

      // Pre-normalize currency symbols, Indian commas, and unicode operators
      s = s.replace(/,/g, '');
      s = s.replace(/[₹$€£]|(?:rs\.?|inr)\s*/gi, '');
      s = s.replace(/×/g, '*').replace(/÷/g, '/').replace(/\*\*/g, '^');

      // Replace word numbers (e.g. "dedh lakh" -> "150000")
      s = s.replace(/\bdedh\s*(?:lakh|lac)\b/gi, '150000')
           .replace(/\bdhai\s*(?:lakh|lac)\b/gi, '250000')
           .replace(/\bdedh\s*hazar\b/gi, '1500')
           .replace(/\bdhai\s*hazar\b/gi, '2500')
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
    evaluate(rawQuery) {
      if (!rawQuery || typeof rawQuery !== 'string') return null;
      let q = rawQuery.trim();

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
      const tokens = this.tokenize(q);
      if (tokens && tokens.length > 0) {
        // Need at least one operator or function or expression to be a calculation (avoid bare numbers matching CRM IDs)
        const hasOp = tokens.some(t => t.type === 'OP' || t.type === 'PERCENT' || t.type === 'SQRT' || t.type === 'OF');
        if (hasOp) {
          const res = this.evaluateTokens(tokens);
          if (res !== null) {
            const rounded = Math.round(res * 100) / 100;
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
    evaluateArithmetic(rawQuery) {
      return SafeArithmeticEngine.evaluate(rawQuery);
    },

    evaluateGST(entities, raw) {
      const amt = entities.amount || 0;
      const rate = entities.rate != null ? entities.rate : 18;
      const isInclusive = /\b(inclusive|reverse|included|with tax|shamil)\b/i.test(raw);

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

    evaluateDiscount(entities) {
      const amt = entities.amount || 0;
      const rate = entities.rate != null ? entities.rate : 0;
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

    evaluateMargin(entities, raw) {
      const amounts = entities.amounts.map(a => a.val);
      if (amounts.length >= 2) {
        const cost = Math.min(amounts[0], amounts[1]);
        const selling = Math.max(amounts[0], amounts[1]);
        const profit = selling - cost;
        const marginPct = Math.round(((profit / selling) * 100) * 100) / 100;
        const markupPct = Math.round(((profit / cost) * 100) * 100) / 100;
        return {
          cost, selling, profit,
          marginPercentage: marginPct,
          marginPercent: marginPct,
          markupPercentage: markupPct,
          markupPercent: markupPct,
          summaryText: `Cost: ₹${cost.toLocaleString('en-IN')}, Selling: ₹${selling.toLocaleString('en-IN')} ➔ Profit: ₹${profit.toLocaleString('en-IN')} (${marginPct}% margin, ${markupPct}% markup)`
        };
      }
      return null;
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
      const regex = /(\d+(?:\.\d+)?)\s*([a-zA-Z]+)\s*(?:to|in|into|madhe|se|mein|ko)\s*([a-zA-Z]+)/i;
      const m = regex.exec(raw);
      if (!m) return null;
      const val = parseFloat(m[1]);
      const fromKey = m[2].toLowerCase();
      const toKey = m[3].toLowerCase();

      const fromUnit = UnitRates[fromKey];
      const toUnit = UnitRates[toKey];

      if (!fromUnit || !toUnit || fromUnit.base !== toUnit.base) {
        return null;
      }

      const inBase = val * fromUnit.toBase;
      const converted = inBase / toUnit.toBase;
      const rounded = Math.round(converted * 1000) / 1000;

      return {
        value: val,
        fromUnit: fromUnit.name,
        toUnit: toUnit.name,
        result: rounded,
        summaryText: `${val} ${fromUnit.name} = ${rounded.toLocaleString('en-IN')} ${toUnit.name}`
      };
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
      if (/\b(gst|tax|vat)\b/i.test(lower) && /\d+/.test(lower)) {
        const isInclusive = /\b(inclusive|reverse|included|with tax|shamil)\b/i.test(lower);
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
      if (/\b(discount|chut|off|rebate)\b/i.test(lower) && /\d+/.test(lower)) {
        return { category: 'SPECIAL', capabilityId: 'SPEC_MATH_DISCOUNT', confidence: 0.98 };
      }
      if (/\b(margin|markup|munafa)\b/i.test(lower) && /\d+/.test(lower)) {
        return { category: 'SPECIAL', capabilityId: 'SPEC_MATH_MARGIN', confidence: 0.97 };
      }

      // 8. Loan EMI & Interest
      if (/\b(emi|loan|simple\s*interest|compound\s*interest|byaj|vyaj)\b/i.test(lower) && /\d+/.test(lower)) {
        return { category: 'SPECIAL', capabilityId: 'SPEC_MATH_EMI', confidence: 0.97 };
      }

      // 9. Broad Deterministic NLP & Safe Expression Arithmetic Engine
      const mathEval = SafeArithmeticEngine.evaluate(lower) || SafeArithmeticEngine.evaluate(raw);
      if (mathEval) {
        return {
          category: 'SPECIAL',
          capabilityId: 'SPEC_MATH_ARITH',
          confidence: 0.99,
          data: mathEval
        };
      }

      // 10. Unit Conversions
      if (/\d+\s*[a-z]+\s+\b(?:to|in|into|madhe|se|mein|ko)\b\s+[a-z]+/i.test(lower)) {
        return { category: 'SPECIAL', capabilityId: 'SPEC_CONV_UNIT', confidence: 0.97 };
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
        return { category: 'ACTION', capabilityId: 'ACT_NAV_SLASH', target: { page: 'paperwork', tab: 'statements', statementMode: 'customer' } };
      }
      if (/^\/pay\b|\bopen\s*payroll\b|\btankha\s*(?:kholo|page|tab)\b/i.test(lower)) {
        return { category: 'ACTION', capabilityId: 'ACT_NAV_SLASH', target: { page: 'hr', hrTab: 'payroll' } };
      }
      if (/^\/po\b|\bcreate\s*purchase\s*order\b|\bsupplier\s*order\b/i.test(lower)) {
        return { category: 'ACTION', capabilityId: 'ACT_NAV_SLASH', target: { page: 'paperwork', tab: 'orders' } };
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
          (/\b(present|absent|half\s*day)\b/i.test(lower) && /\b(attendance|hazri)\b/i.test(lower) && !/\b(today|kal|who|summary|report|list)\b/i.test(lower))) {
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
      if (/\b(yesterday('?s)?\s*sales?|yesterday('?s)?\s*revenue|kal\s*ka\s*sale|kalchi\s*bikri)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_SALES_YESTERDAY', type: 'AGGREGATE' };
      }
      if (/\b(today('?s)?\s*sales?|today('?s)?\s*revenue|how\s*much\s*(?:did\s*we\s*sell|have\s*we\s*sold)|aaj\s*ka\s*sale|aaj\s*ka\s*dhanda|aajchi\s*sale|aajchi\s*bikri|today\s*sales?)\b/i.test(lower)) {
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
      if (/\b(top\s*customers?|best\s*clients?|major\s*customers?|top\s*buyers?)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_TOP_CUSTOMERS', type: 'AGGREGATE' };
      }
      if (/\b(top\s*products?|best\s*selling\s*items?|most\s*sold|fast\s*moving\s*items?)\b/i.test(lower)) {
        return { category: 'QUICK_HELP', capabilityId: 'QH_TOP_PRODUCTS', type: 'AGGREGATE' };
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
              { label: 'View Ledger', route: 'paperwork' }
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

        case 'QH_TOP_CUSTOMERS': {
          const custMap = {};
          invoices.forEach(inv => {
            const cName = inv.customerName || (inv.customer && inv.customer.name) || 'Other';
            custMap[cName] = (custMap[cName] || 0) + (parseFloat(inv.netTotal || inv.totalAmount) || 0);
          });
          const sorted = Object.entries(custMap).sort((a, b) => b[1] - a[1]).slice(0, 5);
          const summaryStr = sorted.map(([name, amt]) => `${name}: ₹${amt.toLocaleString('en-IN')}`).join(' • ');
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_TOP_CUSTOMERS',
            domain: 'SALES',
            apiEndpoint: '/api/omnisearch/sales',
            apiParams: { groupBy: 'CUSTOMER' },
            title: `🏆 Top Customers by Revenue`,
            subtitle: summaryStr || 'No sales records found',
            data: { topCustomers: sorted }
          };
        }

        case 'QH_TOP_PRODUCTS': {
          const prodMap = {};
          invoices.forEach(inv => {
            (inv.items || []).forEach(it => {
              const pName = it.name || it.productName || 'Item';
              prodMap[pName] = (prodMap[pName] || 0) + (parseFloat(it.quantity || it.qty) || 1);
            });
          });
          const sorted = Object.entries(prodMap).sort((a, b) => b[1] - a[1]).slice(0, 5);
          const summaryStr = sorted.map(([name, qty]) => `${name} (${qty} units)`).join(' • ');
          return {
            status: 'ANSWER',
            category: 'QUICK_HELP',
            capabilityId: 'QH_TOP_PRODUCTS',
            domain: 'SALES',
            apiEndpoint: '/api/omnisearch/sales',
            apiParams: { groupBy: 'PRODUCT' },
            title: `📦 Top Selling Products`,
            subtitle: summaryStr || 'No item sales history found',
            data: { topProducts: sorted }
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
                { label: 'Open Ledger', route: 'paperwork', tab: 'statements' }
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
              { label: 'Purchase Orders', route: 'paperwork', tab: 'orders' }
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
              { label: 'Open Statements', route: 'paperwork', tab: 'statements' }
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

      // 1. Deterministic NLP Normalization
      const normalized = DeterministicNormalizer.normalize(rawQuery);

      // 2. Capability Classification
      const classification = CapabilityClassifier.classify(normalized, ctx);

      // 3. Entity & Role Extraction
      const entities = RoleResolver.extractEntities(normalized, ctx);

      // 4. Disambiguation Check
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
            const gstData = CalculationEvaluator.evaluateGST(entities, normalized.raw);
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
            const discData = CalculationEvaluator.evaluateDiscount(entities);
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
            const marginData = CalculationEvaluator.evaluateMargin(entities, normalized.raw);
            if (marginData) {
              return {
                status: 'ANSWER',
                category: 'SPECIAL',
                capabilityId: 'SPEC_MATH_MARGIN',
                title: `📈 ${marginData.summaryText}`,
                subtitle: `Cost: ₹${marginData.cost.toLocaleString('en-IN')} • Selling: ₹${marginData.selling.toLocaleString('en-IN')}`,
                data: marginData
              };
            }
            break;
          }

          case 'SPEC_MATH_EMI': {
            const emiData = CalculationEvaluator.evaluateLoanEmi(entities, normalized.raw);
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

          case 'SPEC_CONV_UNIT': {
            const convData = CalculationEvaluator.evaluateUnitConversion(normalized.raw);
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
            const arithData = classification.data || CalculationEvaluator.evaluateArithmetic(normalized.raw) || CalculationEvaluator.evaluateArithmetic(normalized.lower);
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

  return {
    DeterministicNormalizer,
    UnitRates,
    WordsEngine,
    RoleResolver,
    SafeArithmeticEngine,
    CalculationEvaluator,
    CapabilityClassifier,
    QuickHelpAdapter,
    OmnibarPipeline,
    processQuery: (q, ctx) => OmnibarPipeline.processQuery(q, ctx)
  };
}));
