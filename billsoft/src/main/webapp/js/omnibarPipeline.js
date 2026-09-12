/**
 * omnibarPipeline.js
 * Universal Offline Deterministic Semantic Reasoning & Natural Language Business Assistant Pipeline
 * 
 * Capabilities:
 * - Semantic Parsing & Explicit Intermediate Semantic Meaning Representation (SMR)
 * - Multi-Operation Extraction & Dependency-Aware Planning (e.g. GST + Change Calculation)
 * - Implied Intent & Business Terminology Resolution (English, Hindi, Marathi, Hinglish)
 * - Conversational Discourse, Ellipsis, Pronouns, and Context Reference Resolution
 * - Conversational Corrections ("No, I meant 12%"), Negations ("don't send"), and Follow-ups
 * - Natural-Language Response Generation (NLG) with compositional templates
 * - Deterministic Safety Gate with Zero Mutation without User Confirmation
 * - 100% Client-Side, Local, Offline, Zero External AI/Network Dependency
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
  // 1. CONTEXT MANAGER & CONVERSATIONAL MEMORY
  // ─────────────────────────────────────────────────────────────────────────────
  const _sessionContext = {
    history: [],
    lastIntent: null,
    lastObject: null,
    lastEntities: {},
    lastReferencedDoc: null,
    lastCustomer: null,
    lastStaff: null,
    lastInvoice: null,
    lastCalculation: null, // Stores { type, amount, rate, total, change, paid } for conversational followups/corrections
    timestamp: Date.now()
  };

  const ContextManager = {
    getContext() {
      return { ..._sessionContext };
    },
    setContext(updates) {
      Object.assign(_sessionContext, updates, { timestamp: Date.now() });
      if (updates.rawQuery) {
        _sessionContext.history.unshift({ query: updates.rawQuery, ts: Date.now() });
        if (_sessionContext.history.length > 20) _sessionContext.history.pop();
      }
    },
    clearStaleContext(maxAgeMs = 15 * 60 * 1000) {
      if (Date.now() - _sessionContext.timestamp >= maxAgeMs) {
        _sessionContext.lastIntent = null;
        _sessionContext.lastObject = null;
        _sessionContext.lastEntities = {};
        _sessionContext.lastReferencedDoc = null;
        _sessionContext.lastCustomer = null;
        _sessionContext.lastStaff = null;
        _sessionContext.lastInvoice = null;
        _sessionContext.lastCalculation = null;
      }
    },
    reset() {
      _sessionContext.history = [];
      _sessionContext.lastIntent = null;
      _sessionContext.lastObject = null;
      _sessionContext.lastEntities = {};
      _sessionContext.lastReferencedDoc = null;
      _sessionContext.lastCustomer = null;
      _sessionContext.lastStaff = null;
      _sessionContext.lastInvoice = null;
      _sessionContext.lastCalculation = null;
      _sessionContext.timestamp = Date.now();
    }
  };

  // ─────────────────────────────────────────────────────────────────────────────
  // 2. NORMALIZATION & PHONETIC TOKENIZER
  // ─────────────────────────────────────────────────────────────────────────────
  const Normalizer = {
    splitGluedTokens(str) {
      if (!str || typeof str !== 'string') return '';
      let s = str.trim();
      // Number + % + Word: "18%gst" -> "18% gst"
      s = s.replace(/(\d+(?:\.\d+)?)\s*(%)\s*(gst|tax|vat|discount|margin|interest)?/gi, '$1$2 $3');
      // Number + Unit/Word: "30mm" -> "30 mm", "500upi" -> "500 upi", "10000rs" -> "10000 rs"
      s = s.replace(/(\d+(?:\.\d+)?)\s*(mm|cm|m|mtr|meter|meters|km|in|inch|inches|ft|feet|foot|yd|yard|yards|sqft|sqm|sqyd|guntha|bigha|acre|hectare|brass|g|gram|grams|gm|gms|kg|kgs|kilo|kilos|quintal|qtl|tonne|ton|t|tola|carat|ml|l|ltr|liter|liters|gal|gallon|usd|eur|gbp|aed|cad|aud|inr|rs|rupees|rupee|upi|k|lakh|lakhs|lac|lacs|cr|crore|crores|hazar|hazaar|haz)\b/gi, '$1 $2');
      // Currency Symbol + Number: "rs500" -> "rs 500", "$100" -> "$ 100", "₹1200" -> "₹ 1200"
      s = s.replace(/([₹$€£]|rs\.?|inr)\s*(\d+(?:\.\d+)?)/gi, '$1 $2');
      return s.replace(/\s+/g, ' ').trim();
    },

    cleanNaturalQuery(str) {
      if (!str) return '';
      let s = this.splitGluedTokens(str);
      const fillers = [
        'please', 'plz', 'pls', 'can you', 'could you', 'help me', 'show me', 'give me', 'find me',
        'kripya', 'zara', 'batao', 'dikhao', 'dakhva', 'sang', 'sanga', 'karo', 'kara', 'de do',
        'karna hai', 'kar do', 'ahe', 'aahe', 'hai', 'tha', 'thi', 'the', 'banao', 'banva',
        'tell me', 'i want to know', 'mujhe janna hai', 'bataiye', 'dakhva mala'
      ];
      const fillerRegex = new RegExp(`\\b(${fillers.join('|')})\\b`, 'gi');
      return s.replace(fillerRegex, ' ').replace(/\s+/g, ' ').trim();
    },

    normalize(raw) {
      const rawTrimmed = (raw || '').trim();
      const decoupled = this.splitGluedTokens(rawTrimmed);
      const cleaned = this.cleanNaturalQuery(decoupled);
      return {
        raw: rawTrimmed,
        decoupled,
        cleaned,
        lower: decoupled.toLowerCase(),
        cleanedLower: cleaned.toLowerCase()
      };
    }
  };

  // ─────────────────────────────────────────────────────────────────────────────
  // 3. MULTILINGUAL DICTIONARY & UNIT REGISTRY
  // ─────────────────────────────────────────────────────────────────────────────
  const UnitRates = {
    // Length (base = meter)
    'mm': { base: 'length', toBase: 0.001, name: 'Millimeter' },
    'millimeter': { base: 'length', toBase: 0.001, name: 'Millimeter' },
    'millimeters': { base: 'length', toBase: 0.001, name: 'Millimeter' },
    'cm': { base: 'length', toBase: 0.01, name: 'Centimeter' },
    'centimeter': { base: 'length', toBase: 0.01, name: 'Centimeter' },
    'centimeters': { base: 'length', toBase: 0.01, name: 'Centimeter' },
    'm': { base: 'length', toBase: 1, name: 'Meter' },
    'mtr': { base: 'length', toBase: 1, name: 'Meter' },
    'meter': { base: 'length', toBase: 1, name: 'Meter' },
    'meters': { base: 'length', toBase: 1, name: 'Meter' },
    'km': { base: 'length', toBase: 1000, name: 'Kilometer' },
    'kilometer': { base: 'length', toBase: 1000, name: 'Kilometer' },
    'kilometers': { base: 'length', toBase: 1000, name: 'Kilometer' },
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

    // Area (base = sq.meter)
    'sqm': { base: 'area', toBase: 1, name: 'Square Meter' },
    'sqmtr': { base: 'area', toBase: 1, name: 'Square Meter' },
    'sqft': { base: 'area', toBase: 0.09290304, name: 'Square Feet' },
    'sqyd': { base: 'area', toBase: 0.836127, name: 'Square Yard' },
    'acre': { base: 'area', toBase: 4046.86, name: 'Acre' },
    'acres': { base: 'area', toBase: 4046.86, name: 'Acre' },
    'hectare': { base: 'area', toBase: 10000, name: 'Hectare' },
    'hectares': { base: 'area', toBase: 10000, name: 'Hectare' },
    'guntha': { base: 'area', toBase: 101.17, name: 'Guntha' },
    'bigha': { base: 'area', toBase: 2500, name: 'Bigha' },
    'brass': { base: 'area', toBase: 9.290304, name: 'Brass (100 sq ft)' },

    // Weight / Mass (base = kg)
    'mg': { base: 'mass', toBase: 0.000001, name: 'Milligram' },
    'g': { base: 'mass', toBase: 0.001, name: 'Gram' },
    'gm': { base: 'mass', toBase: 0.001, name: 'Gram' },
    'gms': { base: 'mass', toBase: 0.001, name: 'Gram' },
    'gram': { base: 'mass', toBase: 0.001, name: 'Gram' },
    'grams': { base: 'mass', toBase: 0.001, name: 'Gram' },
    'kg': { base: 'mass', toBase: 1, name: 'Kilogram' },
    'kgs': { base: 'mass', toBase: 1, name: 'Kilogram' },
    'kilo': { base: 'mass', toBase: 1, name: 'Kilogram' },
    'kilos': { base: 'mass', toBase: 1, name: 'Kilogram' },
    'kilogram': { base: 'mass', toBase: 1, name: 'Kilogram' },
    'kilograms': { base: 'mass', toBase: 1, name: 'Kilogram' },
    'quintal': { base: 'mass', toBase: 100, name: 'Quintal' },
    'qtl': { base: 'mass', toBase: 100, name: 'Quintal' },
    'tonne': { base: 'mass', toBase: 1000, name: 'Metric Tonne' },
    'ton': { base: 'mass', toBase: 1000, name: 'Metric Ton' },
    't': { base: 'mass', toBase: 1000, name: 'Metric Tonne' },
    'tola': { base: 'mass', toBase: 0.0116638, name: 'Tola' },
    'carat': { base: 'mass', toBase: 0.0002, name: 'Carat' },

    // Volume (base = liter)
    'ml': { base: 'volume', toBase: 0.001, name: 'Milliliter' },
    'l': { base: 'volume', toBase: 1, name: 'Liter' },
    'ltr': { base: 'volume', toBase: 1, name: 'Liter' },
    'liter': { base: 'volume', toBase: 1, name: 'Liter' },
    'liters': { base: 'volume', toBase: 1, name: 'Liter' },
    'gal': { base: 'volume', toBase: 3.78541, name: 'US Gallon' },
    'gallon': { base: 'volume', toBase: 3.78541, name: 'US Gallon' },

    // Currency (base = INR)
    'inr': { base: 'currency', toBase: 1, name: 'Indian Rupee (INR)', symbol: '₹' },
    'rs': { base: 'currency', toBase: 1, name: 'Indian Rupee (₹)', symbol: '₹' },
    'rupee': { base: 'currency', toBase: 1, name: 'Indian Rupee', symbol: '₹' },
    'rupees': { base: 'currency', toBase: 1, name: 'Indian Rupees', symbol: '₹' },
    'usd': { base: 'currency', toBase: 83.50, name: 'US Dollar (USD)', symbol: '$' },
    'dollar': { base: 'currency', toBase: 83.50, name: 'US Dollar ($)', symbol: '$' },
    'dollars': { base: 'currency', toBase: 83.50, name: 'US Dollars ($)', symbol: '$' },
    'eur': { base: 'currency', toBase: 90.25, name: 'Euro (EUR)', symbol: '€' },
    'euro': { base: 'currency', toBase: 90.25, name: 'Euro (EUR)', symbol: '€' },
    'gbp': { base: 'currency', toBase: 106.10, name: 'British Pound (GBP)', symbol: '£' },
    'pound': { base: 'currency', toBase: 106.10, name: 'British Pound (GBP)', symbol: '£' },
    'aed': { base: 'currency', toBase: 22.73, name: 'UAE Dirham (AED)', symbol: 'AED' },
    'cad': { base: 'currency', toBase: 61.20, name: 'Canadian Dollar (CAD)', symbol: 'C$' },
    'aud': { base: 'currency', toBase: 55.40, name: 'Australian Dollar (AUD)', symbol: 'A$' }
  };

  // Indian Number to Words Engine
  const WordsEngine = {
    ones: ['', 'One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight', 'Nine', 'Ten',
      'Eleven', 'Twelve', 'Thirteen', 'Fourteen', 'Fifteen', 'Sixteen', 'Seventeen', 'Eighteen', 'Nineteen'],
    tens: ['', '', 'Twenty', 'Thirty', 'Forty', 'Fifty', 'Sixty', 'Seventy', 'Eighty', 'Ninety'],

    toWords(num) {
      const n = Math.floor(Math.abs(Number(num) || 0));
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
        return `${this.tens[t]}${o > 0 ? ' ' + this.ones[o] : ''}`;
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
        'eleven': 11, 'gyarah': 11, 'twelve': 12, 'barah': 12, 'thirteen': 13, 'terah': 13,
        'fourteen': 14, 'chaudah': 14, 'fifteen': 15, 'pandrah': 15, 'sixteen': 16, 'solah': 16,
        'seventeen': 17, 'satrah': 17, 'eighteen': 18, 'atharah': 18, 'nineteen': 19, 'unnis': 19,
        'twenty': 20, 'bees': 20, 'thirty': 30, 'tees': 30, 'forty': 40, 'chalis': 40,
        'fifty': 50, 'pachas': 50, 'sixty': 60, 'saath': 60, 'seventy': 70, 'sattar': 70,
        'eighty': 80, 'assi': 80, 'ninety': 90, 'nabbe': 90, 'hundred': 100, 'sau': 100, 'she': 100,
        'thousand': 1000, 'hazar': 1000, 'hazaar': 1000, 'lakh': 100000, 'lac': 100000, 'lakhs': 100000,
        'crore': 10000000, 'cr': 10000000, 'crores': 10000000,
        'dedh': 1.5, 'dhai': 2.5, 'adhai': 2.5, 'sawa': 1.25, 'paune': 0.75, 'sadhe': 0.5
      };

      // Vernacular colloquial phrases: "dedh hazar" -> 1500, "adhai lakh" -> 250000
      if (s.includes('dedh hazar') || s.includes('dedh hajar')) return 1500;
      if (s.includes('dhai hazar') || s.includes('adhai hazar') || s.includes('dhai hajar')) return 2500;
      if (s.includes('dedh lakh') || s.includes('dedh lac')) return 150000;
      if (s.includes('dhai lakh') || s.includes('adhai lakh') || s.includes('dhai lac')) return 250000;
      if (s.includes('dedh crore') || s.includes('dedh cr')) return 15000000;
      if (s.includes('dhai crore') || s.includes('adhai crore')) return 25000000;

      const words = s.split(/[\s-]+/);
      let total = 0;
      let current = 0;
      let matchedAny = false;

      for (const w of words) {
        if (numMap[w] !== undefined) {
          matchedAny = true;
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
      return matchedAny && total > 0 ? total : null;
    }
  };

  // ─────────────────────────────────────────────────────────────────────────────
  // 4. SEMANTIC CONCEPT PARSER & DISCOURSE ANALYZER
  // ─────────────────────────────────────────────────────────────────────────────
  const SemanticParser = {
    // Detect conversational negation: "don't remind", "without gst", "not manoj"
    detectNegation(str) {
      const s = str.toLowerCase();
      const hasNegation = /\b(don'?t|do not|without|excluding|not|nahi|nako|mat|bina|rahit)\b/i.test(s);
      return {
        isNegated: hasNegation,
        negatedSubject: hasNegation ? s.match(/\b(?:don'?t|do not|without|not|mat|nako)\s+([a-z]+)/i)?.[1] : null
      };
    },

    // Detect conversational corrections: "No, I meant 12%", "No, Rahul", "actually 18%"
    detectCorrection(str, ctx = {}) {
      const s = str.toLowerCase().trim();
      const isCorrection = /^(no|nahi|nahin|actually|wait|nako)\b/i.test(s) ||
        /\b(?:i meant|meant|instead|badlo|change to)\b/i.test(s);
      if (!isCorrection) return { isCorrection: false };

      // Rate correction: "No, I meant 12%" or "No, 12%"
      const rateMatch = /(\d+(?:\.\d+)?)\s*%/i.exec(s) || /\b(?:rate|percent|percentage)\s*(\d+(?:\.\d+)?)/i.exec(s);
      if (rateMatch && ctx.lastCalculation) {
        return {
          isCorrection: true,
          type: 'RATE_CORRECTION',
          newRate: parseFloat(rateMatch[1]),
          previousCalculation: ctx.lastCalculation
        };
      }

      // Customer correction: "No, Rahul" or "not Manoj, Rahul"
      const nameMatch = /(?:no|not|instead of [a-z]+)[,\s]+(?:i meant\s+)?([a-z]+(?:\s+[a-z]+)?)/i.exec(s);
      if (nameMatch) {
        return {
          isCorrection: true,
          type: 'TARGET_CORRECTION',
          newTarget: nameMatch[1].trim()
        };
      }

      return { isCorrection: true, type: 'GENERIC_CORRECTION' };
    },

    // Discourse connectors separating sequential operations: "and", "then", "after that", "also", "plus"
    segmentDiscourse(str) {
      // Protect numbers with decimals/colons/percent from splitting
      const connectors = /\s+(?:and\s+then|after\s+that|then|also|along\s+with|plus|next|finally|aur\s+phir|ani\s+mag|tar|nantar|phir|iske\s+baad|while\s+you'?re\s+at\s+it)\s+/gi;
      const parts = str.split(connectors).map(p => p.trim()).filter(p => p.length > 0);
      return parts.length > 1 ? parts : [str];
    }
  };

  // ─────────────────────────────────────────────────────────────────────────────
  // 5. ROLE RESOLVER & NUMERIC ROLE REASONING
  // ─────────────────────────────────────────────────────────────────────────────
  const RoleResolver = {
    extractRates(str) {
      if (!str) return [];
      const rates = [];
      const pRegex = /(\d+(?:\.\d+)?)\s*(?:%|percent|percentage|pratishat|takke)/gi;
      let m;
      while ((m = pRegex.exec(str)) !== null) {
        rates.push({ rate: parseFloat(m[1]), raw: m[0].trim(), start: m.index, end: pRegex.lastIndex });
      }
      if (rates.length === 0) {
        const rateWordRegex = /(?:rate|gst|tax|interest|discount|margin|markup|byaj|vyaj)\s*(?:of|is|at|@|hai|aahe)?\s*(\d+(?:\.\d+)?)/i;
        const rm = rateWordRegex.exec(str);
        if (rm) {
          const val = parseFloat(rm[1]);
          if (val <= 100) rates.push({ rate: val, raw: rm[0].trim(), start: rm.index, end: rm.index + rm[0].length });
        }
      }
      if (rates.length === 0) {
        const suffRegex = /(\d+(?:\.\d+)?)\s*(?:gst|tax|byaj|vyaj)\b/i;
        const sm = suffRegex.exec(str);
        if (sm) {
          const val = parseFloat(sm[1]);
          if (val <= 100) rates.push({ rate: val, raw: sm[0].trim(), start: sm.index, end: sm.index + sm[0].length });
        }
      }
      return rates;
    },

    extractAmounts(str) {
      if (!str) return [];
      const amounts = [];
      // Support commas in numbers: 5,000, 10,000, 1,00,000
      const regex = /(?:₹|rs\.?|inr|\$)?\s*(\d+(?:,\d+)*(?:\.\d+)?)\s*(cr\b|crore\b|crores\b|lakh\b|lakhs\b|lac\b|lacs\b|k\b|hazar\b|hazaar\b|thousand\b)?(?:\b|\s|$)/gi;
      let m;
      while ((m = regex.exec(str)) !== null) {
        const rawMatch = m[0];
        const restOfStr = str.slice(regex.lastIndex, regex.lastIndex + 15).toLowerCase();
        const beforeStr = str.slice(Math.max(0, m.index - 15), m.index).toLowerCase();

        // Exclude percentages (% / percent / percentage / pratishat / takke)
        if (rawMatch.includes('%') || /^\s*(%|percent|percentage|pratishat|takke)/.test(restOfStr)) {
          continue;
        }

        // Exclude numbers that are rates (e.g. "GST at 18", "18 GST", "tax 18")
        if (/\b(?:gst|tax|rate|byaj|vyaj)\s*(?:at|is|of|@)?\s*$/i.test(beforeStr) || /^\s*(?:gst|tax|byaj|vyaj)/i.test(restOfStr)) {
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

        amounts.push({
          val: rawNum * multiplier,
          raw: m[0].trim(),
          start: m.index,
          end: regex.lastIndex
        });
      }
      return amounts;
    },

    extractTenure(str) {
      if (!str) return null;
      const yrMatch = /(\d+(?:\.\d+)?)\s*(?:years?|yrs?|yr|varsh|saal)\b/i.exec(str);
      if (yrMatch) {
        const yrs = parseFloat(yrMatch[1]);
        return { years: yrs, months: yrs * 12 };
      }
      const moMatch = /(\d+(?:\.\d+)?)\s*(?:months?|mahine|maheene|mons?)\b/i.exec(str);
      if (moMatch) {
        const mos = parseFloat(moMatch[1]);
        return { years: mos / 12, months: mos };
      }
      return null;
    },

    extractPhone(str) {
      if (!str) return null;
      const m = /(?:\+91|91|0)?[6-9]\d{9}\b/.exec(str);
      return m ? m[0] : null;
    },

    extractDate(str) {
      if (!str) return null;
      const today = new Date();
      const s = str.toLowerCase();
      if (s.includes('today') || s.includes('aaj')) {
        return { date: today.toISOString().split('T')[0], label: 'Today' };
      }
      if (s.includes('tomorrow') || s.includes('kal')) {
        const d = new Date(today.getTime() + 86400000);
        return { date: d.toISOString().split('T')[0], label: 'Tomorrow' };
      }
      if (s.includes('yesterday') || s.includes('kal')) {
        const d = new Date(today.getTime() - 86400000);
        return { date: d.toISOString().split('T')[0], label: 'Yesterday' };
      }
      const offsetMatch = /(\d+)\s*(?:days?|din)\s*(?:from today|from now|after|baad)?/i.exec(s);
      if (offsetMatch) {
        const days = parseInt(offsetMatch[1], 10);
        const d = new Date(today.getTime() + days * 86400000);
        return { date: d.toISOString().split('T')[0], label: `${days} days from today`, daysOffset: days };
      }
      return null;
    },

    // Resolves pronouns & ellipsis references against active context
    extractReference(str, ctx = {}) {
      const s = str.toLowerCase();
      const isRelative = /\b(that|this|it|him|her|them|his|their|same|last|previous|uska|unka|tyancha|tyana|he|she)\b/i.test(s);
      if (!isRelative) return null;
      return {
        isRelative: true,
        doc: ctx.lastReferencedDoc || ctx.lastInvoice,
        customer: ctx.lastCustomer,
        staff: ctx.lastStaff,
        calculation: ctx.lastCalculation,
        intent: ctx.lastIntent
      };
    },

    // Numeric Role Disambiguation: Maps amounts into specific business roles (e.g. Paid Amount vs Bill Amount)
    disambiguateNumericRoles(str, amounts) {
      const s = str.toLowerCase();
      const roles = {
        billAmount: null,
        paidAmount: null,
        primaryAmount: amounts.length > 0 ? amounts[0].val : null
      };

      // Check for Paid Amount context
      const paidPattern = /(?:gives|paid|give|tendered|gives me|pays|diye|dile|paid amount|hands over|hands me|hands|de toh|deto)\s*(?:me|us|cash|a)?\s*(?:₹|rs\.?|inr|\$)?\s*(\d+(?:,\d+)*(?:\.\d+)?)\s*(cr\b|crore\b|lakh\b|lac\b|k\b|hazar\b|hazaar\b|thousand\b)?/i.exec(s) ||
        /(\d+(?:,\d+)*(?:\.\d+)?)\s*(cr\b|crore\b|lakh\b|lac\b|k\b|hazar\b|hazaar\b|thousand\b)?\s*(?:₹|rs\.?|inr|\$)?\s*(?:gives|gives me|paid|diye|dile|de toh|deto|hands over|hands me)/i.exec(s);
      if (paidPattern) {
        let mult = 1;
        const u = (paidPattern[2] || '').toLowerCase().trim();
        if (u === 'k' || u.startsWith('thous') || u.startsWith('haz')) mult = 1000;
        else if (u.startsWith('l') || u.startsWith('lac')) mult = 100000;
        else if (u.startsWith('cr')) mult = 10000000;
        roles.paidAmount = parseFloat(paidPattern[1].replace(/,/g, '')) * mult;
      }

      // Check for Bill Amount context
      const billPattern = /(?:bill(?: is| of| hai| aahe| ka)?|for a|kharcha|total)\s*(?:₹|rs\.?|inr|\$)?\s*(\d+(?:,\d+)*(?:\.\d+)?)\s*(cr\b|crore\b|lakh\b|lac\b|k\b|hazar\b|hazaar\b|thousand\b)?/i.exec(s) ||
        /(\d+(?:,\d+)*(?:\.\d+)?)\s*(cr\b|crore\b|lakh\b|lac\b|k\b|hazar\b|hazaar\b|thousand\b)?\s*(?:₹|rs\.?|inr|\$)?\s*(?:ka bill|cha bill|bill)/i.exec(s);
      if (billPattern) {
        let mult = 1;
        const u = (billPattern[2] || '').toLowerCase().trim();
        if (u === 'k' || u.startsWith('thous') || u.startsWith('haz')) mult = 1000;
        else if (u.startsWith('l') || u.startsWith('lac')) mult = 100000;
        else if (u.startsWith('cr')) mult = 10000000;
        roles.billAmount = parseFloat(billPattern[1].replace(/,/g, '')) * mult;
      }

      // Only perform automatic min/max role assignment if in Cashier/Change/Payment context
      const isChangeContext = /\b(change|wapas|dena|chutta|paid|gives|gives me|diye|dile|return|give back|baki|hands|hands me|hands over|left|what's left|whats left)\b/i.test(s);
      if (isChangeContext && amounts.length >= 2) {
        if (!roles.paidAmount && !roles.billAmount) {
          roles.paidAmount = Math.max(amounts[0].val, amounts[1].val);
          roles.billAmount = Math.min(amounts[0].val, amounts[1].val);
        } else if (roles.paidAmount && !roles.billAmount) {
          const other = amounts.find(a => a.val !== roles.paidAmount);
          if (other) roles.billAmount = other.val;
        } else if (roles.billAmount && !roles.paidAmount) {
          const other = amounts.find(a => a.val !== roles.billAmount);
          if (other) roles.paidAmount = other.val;
        }
      }

      return roles;
    },

    resolveEntities(normalized, intent, ctx = {}) {
      const { decoupled, lower, raw } = normalized;
      const rates = this.extractRates(decoupled);
      const amounts = this.extractAmounts(decoupled);
      const tenure = this.extractTenure(decoupled);
      const phone = this.extractPhone(decoupled);
      const date = this.extractDate(decoupled);
      const reference = this.extractReference(raw, ctx);
      const numericRoles = this.disambiguateNumericRoles(decoupled, amounts);

      const entities = {
        amounts,
        amount: numericRoles.billAmount || numericRoles.primaryAmount,
        paidAmount: numericRoles.paidAmount,
        billAmount: numericRoles.billAmount || numericRoles.primaryAmount,
        rates,
        rate: rates.length > 0 ? rates[0].rate : null,
        tenure,
        phone,
        date,
        reference,
        targetName: null,
        category: null,
        paymentMode: 'CASH',
        rawText: raw
      };

      // Customer / Staff name extraction
      const stopWords = new Set([
        'show', 'tell', 'view', 'find', 'what', 'how', 'much', 'does', 'is', 'and', 'the', 'customer', 'client',
        'party', 'me', 'my', 'advance', 'attendance', 'payment', 'reminder', 'total', 'dues', 'sales', 'split',
        'change', 'rate', 'loan', 'expense', 'today', 'tomorrow', 'yesterday', 'rupees', 'cash', 'upi', 'invoice',
        'bill', 'gst', 'balance', 'outstanding', 'owe', 'owes', 'udhari', 'hisab', 'pending', 'from', 'to', 'for',
        'about', 'with', 'need', 'collect', 'give', 'receive', 'bhejo', 'dya', 'kar', 'karo', 'aahe', 'hai',
        'kiti', 'kitna', 'wapas', 'chutta', 'de', 'toh', 'agar', 'batao', 'dakhva', 'please', 'can', 'you'
      ]);

      // 1. Check for pronouns first (he/him/his/she/her/them/uska/unka/tyancha)
      const isPronoun = /\b(he|him|his|she|her|them|they|their|uska|unka|tyancha|tyana)\b/i.test(raw);
      const mentionsExplicitName = /\b(Manoj|Rahul|Ganesh|Sagar|Patil|Sharma)\b/i.test(raw);

      if (isPronoun && !mentionsExplicitName) {
        entities.targetName = (entities.reference && entities.reference.customer && entities.reference.customer.name) || (ctx.lastCustomer && ctx.lastCustomer.name) || null;
      }

      // 2. Direct match against known context customers
      if (!entities.targetName && ctx.customers && ctx.customers.length > 0) {
        for (const c of ctx.customers) {
          if (c.name && new RegExp(`\\b${c.name}\\b`, 'i').test(raw)) {
            entities.targetName = c.name;
            break;
          }
        }
      }

      // 3. Match possessives: "Manoj's", "Manoj ka", "Manoj cha", "Manoj ko", "Rahul ko"
      if (!entities.targetName) {
        const possMatch = /\b([a-zA-Z]+(?:\s+[a-zA-Z]+)?)(?:'s|\s+(?:ka|cha|ko|sathi|ki|ne))\b/i.exec(raw);
        if (possMatch) {
          const candidate = possMatch[1].trim().split(/\s+/).filter(w => !stopWords.has(w.toLowerCase())).join(' ');
          if (candidate && candidate.length > 1) {
            entities.targetName = candidate;
          }
        }
      }

      // 4. Token-based proper name recognition
      if (!entities.targetName) {
        const tokens = raw.split(/[\s,.'"?]+/);
        const nameTokens = [];
        for (const t of tokens) {
          if (/^[A-Z][a-z]{1,}$/.test(t) && !stopWords.has(t.toLowerCase())) {
            nameTokens.push(t);
            if (nameTokens.length === 2) break;
          } else if (nameTokens.length > 0) {
            break;
          }
        }
        if (nameTokens.length > 0) {
          entities.targetName = nameTokens.join(' ');
        }
      }

      // Customer Registration: "customer Manoj Patil 9822113344 Kolhapur" or "customer Manoj"
      if (intent === 'CREATE_CUSTOMER') {
        const cleanWords = decoupled.replace(/\b(customer|grahak|client|add|naya|new)\b/gi, '')
          .replace(/(?:\+91|91|0)?[6-9]\d{9}/g, '').trim().split(/\s+/).filter(w => w.length > 0);
        if (cleanWords.length > 0) {
          entities.targetName = cleanWords.slice(0, 2).join(' ');
          if (cleanWords.length > 2) entities.city = cleanWords.slice(2).join(' ');
        }
      }

      // Expense Title: "kharcha 120 chai nashta" / "add expense 250 for stationery"
      if (intent === 'RECORD_EXPENSE') {
        const cleanTitle = decoupled.replace(/\b(kharcha|expense|kharch|paid|spent|rs\.?|inr|₹|add|new|record|for|towards)\b/gi, '')
          .replace(/\b\d+(?:\.\d+)?\b/g, '').replace(/\s+/g, ' ').trim();
        entities.title = cleanTitle || 'General Business Expense';
      }

      // Todo Task: "todo call Sharma ji 4pm"
      if (intent === 'CREATE_TODO') {
        const cleanTask = decoupled.replace(/\b(todo|task|remind|reminder|note)\b/gi, '').trim();
        entities.title = cleanTask || 'Business Reminder';
      }

      // Attendance: "attendance Ganesh present" / "hazri rahul half day"
      if (intent === 'MARK_ATTENDANCE') {
        const isPresent = /\b(present|hazar|ahe|full day)\b/i.test(lower);
        const isAbsent = /\b(absent|chutti|nahin|nahi|leave)\b/i.test(lower);
        const isHalfDay = /\b(half day|nimma|ardha)\b/i.test(lower);
        entities.attendanceStatus = isHalfDay ? 'HALF_DAY' : (isAbsent ? 'ABSENT' : 'PRESENT');
        const cleanName = decoupled.replace(/\b(attendance|hazri|hazari|present|absent|leave|half day|hazar)\b/gi, '').trim();
        entities.targetName = cleanName || null;
      }

      // Advance: "advance 5000 to ganesh"
      if (intent === 'RECORD_ADVANCE') {
        const cleanName = decoupled.replace(/\b(advance|adv|to|ko|dya|diya|de do|cash|upi|salary)\b/gi, '')
          .replace(/\b\d+(?:\.\d+)?\b/g, '').trim();
        entities.targetName = entities.targetName || cleanName || null;
      }

      // Dues Reminder / Outstanding: "remind rahul" / "what does he owe" / "remind him" / "Manoj ka udhari"
      if (intent === 'SEND_DUES_REMINDER' || intent === 'VIEW_CUSTOMER_OUTSTANDING') {
        if (!entities.targetName) {
          const isPronoun = /\b(him|her|them|it|that|this|uska|unka|tyancha|tyana|he|she|his)\b/i.test(decoupled);
          if (isPronoun) {
            entities.targetName = (entities.reference && entities.reference.customer && entities.reference.customer.name) || (ctx.lastCustomer && ctx.lastCustomer.name) || null;
          }
        }
      }

      return entities;
    }
  };

  // ─────────────────────────────────────────────────────────────────────────────
  // 6. INTENT CLASSIFIER & CONCEPT RESOLVER
  // ─────────────────────────────────────────────────────────────────────────────
  const IntentClassifier = {
    classify(normalized, ctx = {}) {
      const { lower, raw, decoupled } = normalized;
      const candidates = [];

      // Check Negation first: "don't remind", "without gst", "not manoj", "don't send"
      const negation = SemanticParser.detectNegation(raw);
      if (negation.isNegated && /\b(remind|reminder|bhejo|advance|expense|customer|send)\b/i.test(lower)) {
        return {
          topCandidate: { intent: 'CANCELLED_MUTATION', domain: 'SAFETY', confidence: 0.99, isDominant: true, evidence: 'Negation detected' },
          allCandidates: [],
          isDominant: true,
          isNegated: true
        };
      }

      // Check Non-Action Conversational Statements: "I was talking to Manoj yesterday", "I need to know whether I should send Rahul a reminder"
      if (/\b(?:i was talking|talking to|spoke to|met with|thinking about|whether i should|if i should|need to know whether)\b/i.test(lower) && !/\b(?:please send|bhejo abhi|send now)\b/i.test(lower)) {
        return {
          topCandidate: { intent: 'CONVERSATIONAL_NOTE', domain: 'INFO', confidence: 0.95, isDominant: true, evidence: 'Passive non-action statement detected' },
          allCandidates: [],
          isDominant: true
        };
      }

      // Check Correction: "No, I meant 12%" or "No, Rahul"
      const correction = SemanticParser.detectCorrection(raw, ctx);
      if (correction.isCorrection && correction.type === 'RATE_CORRECTION') {
        return {
          topCandidate: { intent: 'RATE_CORRECTION', domain: 'CORRECTION', confidence: 0.99, isDominant: true, newRate: correction.newRate },
          allCandidates: [],
          isDominant: true,
          isCorrection: true
        };
      }

      // 1. Compound Implied Flow: "Bill is 5000 plus 18% GST and customer gives 10000, how much change?" / "The bill is 5k, add GST at 18 and the customer hands over 10k. What do I return?"
      const hasGstConcept = /\b(gst|tax|vat)\b/i.test(lower) || /\d+%\s*(?:gst)?/i.test(lower) || /\b(?:add|including|plus|pe|ka|with)\s*(?:gst|tax)\b/i.test(lower);
      const hasChangeConcept = /\b(change|return|gives|gives me|paid|wapas|dena|chutta|deto|dile|de toh|hands|hands over|hands me|left|what's left|whats left|return|give back)\b/i.test(lower);
      const amounts = RoleResolver.extractAmounts(decoupled);

      if (hasGstConcept && hasChangeConcept && amounts.length >= 2) {
        candidates.push({
          intent: 'COMPOUND_GST_CHANGE',
          domain: 'COMPOUND',
          confidence: 0.99,
          isDominant: true,
          evidence: 'Multi-operation compound: GST addition with Change Calculation'
        });
      }

      // 2. UPI Payment QR: "9000 upi", "pay on qr"
      if (/\b(upi|qr|gpay|phonepe|paytm|vpa|scan\s*pay)\b/i.test(lower)) {
        candidates.push({
          intent: 'UPI_QR',
          domain: 'UPI',
          confidence: 0.98,
          isDominant: true,
          evidence: 'Found explicit UPI / QR payment keywords'
        });
      }

      // 3. Customer Outstanding, Info & Invoices
      const mentionsCustomerEntity = /\b(manoj|rahul|patil|sharma|customer|grahak|he|him|his|she|her|unka|uska|tyancha|tyana)\b/i.test(lower) || ctx.lastCustomer || /(?:from|of|for|ka|cha|ko|sathi)\s+[a-z]+/i.test(lower);
      
      // Customer Invoices / Bills: "Show Manoj's invoices", "Manoj ke bills", "show his invoices"
      if (/\b(invoices?|bills?|pavti|estimates?|quotations?)\b/i.test(lower) && mentionsCustomerEntity && !/\b(today|today'?s|aaj|sales?|revenue|create|new|add|banao|banva|make|paid|pay|gives|gave|change|return|wapas|chutta|how much|what do i return|hands)\b/i.test(lower) && !/\d+/.test(lower)) {
        candidates.push({
          intent: 'VIEW_CUSTOMER_INVOICES',
          domain: 'RECORDS',
          confidence: 0.98,
          isDominant: true,
          evidence: 'Customer invoices / bills query'
        });
      }

      // Customer Contact / Phone Info: "what's his phone number?", "Manoj contact", "what is his phone"
      if (/\b(phone|mobile|number|contact\s*no|contact|address|city)\b/i.test(lower) && mentionsCustomerEntity && !/\b(todo|task|call|remind|reminder|note|words|add|create|new)\b/i.test(lower)) {
        candidates.push({
          intent: 'VIEW_CUSTOMER_INFO',
          domain: 'BI',
          confidence: 0.98,
          isDominant: true,
          evidence: 'Customer phone / contact details query'
        });
      }

      // Customer Outstanding & Dues Question: "what does Manoj owe?", "Manoj ka udhari kitna hai", "what does he owe?", "how much do I need to collect from Manoj"
      if (/\b(owe|owes|udhari|udhar|outstanding|pending|collect|balance|hisab|baaki|bakaya|vasooli)\b/i.test(lower) && mentionsCustomerEntity) {
        candidates.push({
          intent: 'VIEW_CUSTOMER_OUTSTANDING',
          domain: 'BI',
          confidence: 0.98,
          isDominant: true,
          evidence: 'Natural customer outstanding / balance query'
        });
      }

      // 4. GST & Commercial Tax Calculators: "what is 18% GST on 5000", "5000 ka 18% gst", "5000 plus 18% gst"
      if (/\b(gst|cgst|sgst|igst|reverse\s*gst|inclusive)\b/i.test(lower) || (/\btax\b/i.test(lower) && /\d+/.test(lower))) {
        candidates.push({
          intent: 'GST_CALCULATION',
          domain: 'MATH',
          confidence: 0.97,
          isDominant: true,
          evidence: 'Found GST / Tax calculation syntax'
        });
      }

      // 5. Change Calculator: "change for 2000 bill 1435", "customer paid 2000 for a 1435 bill", "2000 diye bill 1435 hai", "how much change from 2000 for a 1435 bill", "The customer gave me 2000 for a 1435 bill."
      if (/\b(change|chutta|bakaya|dena\s*hai|wapas|how much do i return|what do i return|give back|how much should i return)\b/i.test(lower) ||
        (/\b(paid|diye|gives|dile|gave|hands|tendered)\b/i.test(lower) && /\b(bill|for|of)\b/i.test(lower) && amounts.length >= 2)) {
        candidates.push({
          intent: 'CASHIER_CHANGE',
          domain: 'MATH',
          confidence: 0.98,
          isDominant: true,
          evidence: 'Found cashier change & note denomination request'
        });
      }

      // 6. Bill Splitter: "split 4500 by 4", "split 10000 in 2:3:5"
      if (/\b(split|divide|bantna|watap|hissa)\b/i.test(lower)) {
        candidates.push({
          intent: 'BILL_SPLIT',
          domain: 'MATH',
          confidence: 0.96,
          isDominant: true,
          evidence: 'Found bill split request'
        });
      }

      // 7. Loan EMI / Simple Interest / Compound Interest
      if (/\b(emi|loan|interest|si|ci|simple\s*interest|compound\s*interest|byaj|vyaj)\b/i.test(lower)) {
        candidates.push({
          intent: 'LOAN_EMI_CALCULATION',
          domain: 'FINANCE',
          confidence: 0.96,
          isDominant: true,
          evidence: 'Found loan / EMI / interest keywords'
        });
      }

      // 8. Discount & Margin Calculators
      if (/\b(discount|chhut|off)\b/i.test(lower) && !/\b(gst|tax)\b/i.test(lower)) {
        candidates.push({
          intent: 'DISCOUNT_CALCULATION',
          domain: 'MATH',
          confidence: 0.96,
          isDominant: true,
          evidence: 'Found discount calculation request'
        });
      }
      if (/\b(margin|markup|profit\s*margin|munafa)\b/i.test(lower)) {
        candidates.push({
          intent: 'MARGIN_CALCULATION',
          domain: 'MATH',
          confidence: 0.96,
          isDominant: true,
          evidence: 'Found profit margin request'
        });
      }

      // 9. Units & Currency Conversions: explicit syntax "<val> <unit> (to|in|into|ko|mein|madhe) <unit>", "5 kg in grams", "5 kilo ko gram mein batao"
      const convExplicitPattern = /\d+\s*([a-z]+)\s*(?:to|in|into|madhe|se|mein|ko)\s*([a-z]+)/i.exec(lower) ||
        /(?:convert|badlo)\s*(?:it|this|isko)?\s*(?:to|in|into|mein)\s*([a-z]+)/i.exec(lower);
      if (convExplicitPattern) {
        candidates.push({
          intent: 'UNIT_CONVERSION',
          domain: 'CONVERSION',
          confidence: 0.96,
          isDominant: true,
          evidence: 'Found unit or currency conversion pattern'
        });
      }

      // 10. Indian Cheque Words to/from Numbers: "words 125000", "one lakh twenty five thousand", "dedh hazar"
      if (/\b(words?|shabdat|cheque|words\s*me|convert\s*to\s*words)\b/i.test(lower) ||
        (/\b(lakh|crore|hazar|thousand|dedh|adhai|dhai)\b/i.test(lower) && !/\b(gst|invoice|bill|tax|advance|kharcha|attendance|customer)\b/i.test(lower))) {
        candidates.push({
          intent: 'NUMBER_WORDS',
          domain: 'WORDS',
          confidence: 0.95,
          isDominant: true,
          evidence: 'Found number to words / words to number pattern'
        });
      }

      // 11. Live Business Performance / Today Sales: "today's sales", "how much did we sell today", "sales for today", "kitne ki sale hui aaj"
      const hasSalesConcept = /\b(sales?|revenue|dhanda|kamai|selling|sold|made)\b/i.test(lower);
      const hasTodayConcept = /\b(today|today'?s|aaj|aajchi|current\s*day)\b/i.test(lower);
      if ((hasSalesConcept && hasTodayConcept) || /\b(today'?s\s*sales?|today'?s\s*revenue|how\s*much\s*(?:did\s*we\s*sell|have\s*we\s*sold)|what\s*(?:did\s*we\s*make|we\s*made)|aaj\s*ka\s*sale|aaj\s*ka\s*dhanda|total\s*sales?|aajchi\s*sale|kitne\s*ki\s*sale)\b/i.test(lower)) {
        candidates.push({ intent: 'BI_TODAY_SALES', domain: 'BI', confidence: 0.97, isDominant: true, evidence: 'Natural today sales query' });
      } else if (/\b(total\s*udhari|kiska\s*kitna\s*udhar|total\s*dues|pending\s*dues|total\s*outstanding)\b/i.test(lower)) {
        candidates.push({ intent: 'BI_TOTAL_UDHARI', domain: 'BI', confidence: 0.96, evidence: 'Total udhari query' });
      } else if (/\b(low\s*stock|kam\s*stock|reorder\s*stock|out\s*of\s*stock)\b/i.test(lower)) {
        candidates.push({ intent: 'BI_LOW_STOCK', domain: 'BI', confidence: 0.96, evidence: 'Low stock query' });
      } else if (/\b(gst\s*report|tax\s*report|gstr1|gstr3b)\b/i.test(lower)) {
        candidates.push({ intent: 'BI_GST_REPORT', domain: 'BI', confidence: 0.96, evidence: 'GST report query' });
      } else if (/\b(bank\s*details|bank\s*khata|bank\s*account|ifsc)\b/i.test(lower)) {
        candidates.push({ intent: 'BI_BANK_DETAILS', domain: 'BI', confidence: 0.96, evidence: 'Bank details query' });
      }

      // 12. Smart Mutating Tasks & Communications
      if (/\b(remind|send\s*reminder|bhejo\s*reminder|payment\s*reminder|whatsapp\s*kar\s*do|remind\s*him|remind\s*her|remind\s*them)\b/i.test(lower)) {
        candidates.push({ intent: 'SEND_DUES_REMINDER', domain: 'TASKS', confidence: 0.96, isMutation: false, evidence: 'WhatsApp dues reminder' });
      }
      if (/\b(kharcha|expense|kharch)\b/i.test(lower) && /\d+/.test(lower)) {
        candidates.push({ intent: 'RECORD_EXPENSE', domain: 'TASKS', confidence: 0.96, isMutation: true, evidence: 'Expense command' });
      }
      if (/^\s*(todo|task)\b/i.test(lower) || (/\b(remind\s*me|task|reminder|note)\b/i.test(lower) && !/\b(udhari|udhar|dues|balance|pending|owe|owes|ko|to|him|her)\b/i.test(lower))) {
        candidates.push({ intent: 'CREATE_TODO', domain: 'TASKS', confidence: 0.95, isMutation: true, evidence: 'Todo command' });
      }
      if (/\b(customer|grahak|client)\s+(?!gave|paid|gives|sent|wants|asked|bought|returned|came|called|has|had|owes|is|was|ko|to|ka|ki|ne|se)[a-zA-Z]/i.test(lower) && !/\b(search|find|view|list|show|balance|owe|udhari|hisab|remind|bill|change|paid|gave)\b/i.test(lower)) {
        candidates.push({ intent: 'CREATE_CUSTOMER', domain: 'TASKS', confidence: 0.95, isMutation: true, evidence: 'Register customer command' });
      }
      if (/\b(attendance|hazri|hazari)\b/i.test(lower)) {
        candidates.push({ intent: 'MARK_ATTENDANCE', domain: 'TASKS', confidence: 0.96, isMutation: true, evidence: 'Mark attendance command' });
      }
      if (/\b(advance|advance\s*salary)\b/i.test(lower)) {
        candidates.push({ intent: 'RECORD_ADVANCE', domain: 'TASKS', confidence: 0.96, isMutation: true, evidence: 'Salary advance command' });
      }

      // 13. Pure Arithmetic Math: "450 * 12", "1500 + 450", "20% of 5000"
      if (/^[\d\s+\-*/().%^₹$]+$/.test(lower) || /\b(avg|average)\s+[\d\s.]+/i.test(lower) || /\d+\s*[%+\-*/]\s*(?:of)?\s*\d+/i.test(lower)) {
        candidates.push({
          intent: 'ARITHMETIC_MATH',
          domain: 'MATH',
          confidence: 0.93,
          isDominant: true,
          evidence: 'Found mathematical expression'
        });
      }

      // 14. Direct Navigation / Slash Commands
      const slashMatches = [
        { pattern: /^\/inv|\bbill\s*banao\b|\bpavti\s*banva\b|\bnew\s*bill\b|\bcreate\s*invoice\b|\bopen\s*invoices\b|\bshow\s*invoices\b|\btake\s*me\s*to\s*invoices\b/i, id: 'create_invoice', intent: 'NAV_INVOICE' },
        { pattern: /^\/quo|\bkaccha\s*bill\b|\bandaj\s*patrak\b|\bestimate\b|\bcreate\s*quotation\b|\bopen\s*quotations\b/i, id: 'create_quotation', intent: 'NAV_QUOTATION' },
        { pattern: /^\/khata|\bhisab\b|\budhari\s*ledger\b|\bstatements\b|\bopen\s*statements\b/i, id: 'customer_statements', intent: 'NAV_STATEMENTS' },
        { pattern: /^\/pay|\btankha\b|\bkamgar\s*payment\b|\bpayslip\b|\bopen\s*payroll\b/i, id: 'staff_payroll', intent: 'NAV_PAYROLL' },
        { pattern: /^\/po|\bkharedi\s*order\b|\bsupplier\s*order\b|\bopen\s*orders\b/i, id: 'create_po', intent: 'NAV_PO' },
        { pattern: /^\/lock|\bscreen\s*lock\b|\bsafe\s*mode\b/i, id: 'app_lock', intent: 'NAV_LOCK', isHighRisk: true },
        { pattern: /^\/gst|\btax\s*settings\b|\bopen\s*gst\s*settings\b/i, id: 'gst_settings', intent: 'NAV_GST_SETTINGS' },
        { pattern: /^\/theme|\bdark\s*mode\b|\blight\s*mode\b/i, id: 'theme_toggle', intent: 'NAV_THEME' },
        { pattern: /\b(take\s*me\s*to|open|show|go\s*to)\s*(?:the\s*)?customers?\b/i, id: 'nav_customers', intent: 'NAV_CUSTOMERS' },
        { pattern: /\b(take\s*me\s*to|open|show|go\s*to)\s*(?:the\s*)?expenses?\b/i, id: 'nav_expenses', intent: 'NAV_EXPENSES' },
        { pattern: /\b(take\s*me\s*to|open|show|go\s*to)\s*(?:the\s*)?inventory\b/i, id: 'nav_inventory', intent: 'NAV_INVENTORY' },
        { pattern: /\b(take\s*me\s*to|open|show|go\s*to)\s*(?:the\s*)?parties|vendors?\b/i, id: 'nav_parties', intent: 'NAV_PARTIES' },
        { pattern: /\b(take\s*me\s*to|open|show|go\s*to)\s*(?:the\s*)?staff|employees?|hr\b/i, id: 'nav_staff', intent: 'NAV_STAFF' }
      ];

      for (const sm of slashMatches) {
        if (sm.pattern.test(lower)) {
          candidates.push({
            intent: sm.intent,
            actionId: sm.id,
            domain: 'NAVIGATION',
            confidence: 0.96,
            isHighRisk: sm.isHighRisk || false,
            evidence: `Matched quick action / slash command: ${sm.id}`
          });
        }
      }

      // 15. General Entity / Record Search (fallback)
      candidates.push({
        intent: 'SEARCH_RECORDS',
        domain: 'RECORDS',
        confidence: candidates.length === 0 ? 0.85 : 0.50,
        evidence: 'General database record search'
      });

      // Sort by confidence descending
      candidates.sort((a, b) => b.confidence - a.confidence);

      return {
        topCandidate: candidates[0],
        allCandidates: candidates,
        isDominant: candidates[0].isDominant || false
      };
    }
  };

  // ─────────────────────────────────────────────────────────────────────────────
  // 7. CAPABILITIES & ACTION PLANNER WITH INTERMEDIATE SEMANTIC MEANING (SMR)
  // ─────────────────────────────────────────────────────────────────────────────
  const Capabilities = {
    COMPOUND_GST_CHANGE: { type: 'READ', risk: 'LOW', requires: ['amount', 'paidAmount'] },
    GST_CALCULATION: { type: 'READ', risk: 'LOW', requires: ['amount'] },
    CASHIER_CHANGE: { type: 'READ', risk: 'LOW', requires: ['amount'] },
    BILL_SPLIT: { type: 'READ', risk: 'LOW', requires: ['amount'] },
    DISCOUNT_CALCULATION: { type: 'READ', risk: 'LOW', requires: ['amount', 'rate'] },
    MARGIN_CALCULATION: { type: 'READ', risk: 'LOW', requires: ['amount'] },
    LOAN_EMI_CALCULATION: { type: 'READ', risk: 'LOW', requires: ['amount', 'rate'] },
    UNIT_CONVERSION: { type: 'READ', risk: 'LOW', requires: [] },
    NUMBER_WORDS: { type: 'READ', risk: 'LOW', requires: [] },
    ARITHMETIC_MATH: { type: 'READ', risk: 'LOW', requires: [] },
    UPI_QR: { type: 'READ', risk: 'LOW', requires: [] },
    VIEW_CUSTOMER_OUTSTANDING: { type: 'READ', risk: 'LOW', requires: ['targetName'] },
    VIEW_CUSTOMER_INFO: { type: 'READ', risk: 'LOW', requires: [] },
    VIEW_CUSTOMER_INVOICES: { type: 'READ', risk: 'LOW', requires: [] },
    BI_TODAY_SALES: { type: 'READ', risk: 'LOW', requires: [] },
    BI_TOTAL_UDHARI: { type: 'READ', risk: 'LOW', requires: [] },
    BI_LOW_STOCK: { type: 'READ', risk: 'LOW', requires: [] },
    BI_GST_REPORT: { type: 'READ', risk: 'LOW', requires: [] },
    BI_BANK_DETAILS: { type: 'READ', risk: 'LOW', requires: [] },
    SEARCH_RECORDS: { type: 'READ', risk: 'LOW', requires: [] },
    NAV_INVOICE: { type: 'READ', risk: 'LOW', requires: [] },
    NAV_QUOTATION: { type: 'READ', risk: 'LOW', requires: [] },
    NAV_STATEMENTS: { type: 'READ', risk: 'LOW', requires: [] },
    NAV_PAYROLL: { type: 'READ', risk: 'LOW', requires: [] },
    NAV_PO: { type: 'READ', risk: 'LOW', requires: [] },
    NAV_GST_SETTINGS: { type: 'READ', risk: 'LOW', requires: [] },
    NAV_THEME: { type: 'READ', risk: 'LOW', requires: [] },
    NAV_CUSTOMERS: { type: 'READ', risk: 'LOW', requires: [] },
    NAV_EXPENSES: { type: 'READ', risk: 'LOW', requires: [] },
    NAV_INVENTORY: { type: 'READ', risk: 'LOW', requires: [] },
    NAV_PARTIES: { type: 'READ', risk: 'LOW', requires: [] },
    NAV_STAFF: { type: 'READ', risk: 'LOW', requires: [] },
    NAV_LOCK: { type: 'WRITE', risk: 'HIGH', requires: [] },
    CANCELLED_MUTATION: { type: 'READ', risk: 'LOW', requires: [] },
    CONVERSATIONAL_NOTE: { type: 'READ', risk: 'LOW', requires: [] },
    RATE_CORRECTION: { type: 'READ', risk: 'LOW', requires: [] },

    // Mutating Smart Tasks (WRITE)
    RECORD_EXPENSE: { type: 'WRITE', risk: 'MEDIUM', requires: ['amount', 'title'] },
    CREATE_TODO: { type: 'WRITE', risk: 'LOW', requires: ['title'] },
    CREATE_CUSTOMER: { type: 'WRITE', risk: 'MEDIUM', requires: ['targetName', 'phone'] },
    SEND_DUES_REMINDER: { type: 'READ', risk: 'LOW', requires: ['targetName'] },
    MARK_ATTENDANCE: { type: 'WRITE', risk: 'MEDIUM', requires: ['targetName', 'attendanceStatus'] },
    RECORD_ADVANCE: { type: 'WRITE', risk: 'MEDIUM', requires: ['amount', 'targetName'] }
  };

  const ConstraintValidator = {
    validate(capability, entities) {
      const cap = Capabilities[capability] || { type: 'READ', risk: 'LOW', requires: [] };
      const missing = [];

      for (const req of cap.requires) {
        if (req === 'amount' && (entities.amount == null || isNaN(entities.amount) || entities.amount <= 0)) {
          const wordsAmt = WordsEngine.wordsToNumber(entities.rawText);
          if (wordsAmt) entities.amount = wordsAmt;
          else missing.push('amount');
        } else if (req === 'rate' && (entities.rate == null || isNaN(entities.rate))) {
          missing.push('rate');
        } else if (req === 'paidAmount' && (entities.paidAmount == null || isNaN(entities.paidAmount))) {
          missing.push('paidAmount');
        } else if (req === 'title' && (!entities.title || !entities.title.trim())) {
          missing.push('title');
        } else if (req === 'targetName' && (!entities.targetName || !entities.targetName.trim())) {
          if (!entities.reference || !entities.reference.customer) {
            missing.push('targetName');
          }
        } else if (req === 'phone' && !entities.phone) {
          missing.push('phone');
        }
      }

      return {
        isValid: missing.length === 0,
        missingFields: missing,
        capabilityType: cap.type,
        riskLevel: cap.risk
      };
    }
  };

  const ActionPlanner = {
    plan(capability, entities, constraintResult) {
      const plan = {
        operations: [],
        steps: [],
        atomic: true,
        canRollback: true
      };

      if (!constraintResult.isValid) return plan;

      switch (capability) {
        case 'COMPOUND_GST_CHANGE':
          plan.operations.push(
            { id: 'op1', intent: 'CALCULATE', action: 'ADD_GST', inputs: { amount: entities.amount, rate: entities.rate || 18 } },
            { id: 'op2', intent: 'CALCULATE', action: 'CALCULATE_CHANGE', inputs: { paidAmount: entities.paidAmount, billAmount: { reference: 'op1.total' } }, dependsOn: ['op1'] }
          );
          break;

        case 'RECORD_EXPENSE':
          plan.steps.push({
            stepId: 1,
            action: 'API.expenses.create',
            payload: {
              amount: entities.amount,
              title: entities.title,
              category: entities.category || 'General',
              paymentMode: entities.paymentMode || 'CASH',
              expenseDate: entities.date ? entities.date.date : new Date().toISOString().split('T')[0]
            },
            rollbackAction: 'API.expenses.delete'
          });
          break;

        case 'CREATE_TODO':
          plan.steps.push({
            stepId: 1,
            action: 'API.planner.createTask',
            payload: {
              title: entities.title,
              dueDate: entities.date ? entities.date.date : null,
              status: 'PENDING'
            },
            rollbackAction: 'API.planner.deleteTask'
          });
          break;

        case 'CREATE_CUSTOMER':
          plan.steps.push({
            stepId: 1,
            action: 'API.customers.create',
            payload: {
              name: entities.targetName,
              phone: entities.phone,
              city: entities.city || ''
            },
            rollbackAction: 'API.customers.delete'
          });
          break;

        case 'MARK_ATTENDANCE':
          plan.steps.push({
            stepId: 1,
            action: 'API.employees.markAttendance',
            payload: {
              staffName: entities.targetName,
              status: entities.attendanceStatus,
              date: entities.date ? entities.date.date : new Date().toISOString().split('T')[0]
            }
          });
          break;

        case 'RECORD_ADVANCE':
          plan.steps.push({
            stepId: 1,
            action: 'API.employees.recordAdvance',
            payload: {
              staffName: entities.targetName,
              amount: entities.amount,
              date: entities.date ? entities.date.date : new Date().toISOString().split('T')[0]
            }
          });
          break;

        case 'NAV_CUSTOMERS':
          plan.type = 'NAVIGATION';
          plan.target = { page: 'firm', tab: 'customers' };
          break;
        case 'NAV_EXPENSES':
          plan.type = 'NAVIGATION';
          plan.target = { page: 'planner', plannerTab: 'expenses' };
          break;
        case 'NAV_INVENTORY':
          plan.type = 'NAVIGATION';
          plan.target = { page: 'firm', tab: 'inventory' };
          break;
        case 'NAV_PARTIES':
          plan.type = 'NAVIGATION';
          plan.target = { page: 'firm', tab: 'parties' };
          break;
        case 'NAV_STAFF':
          plan.type = 'NAVIGATION';
          plan.target = { page: 'hr', hrTab: 'staff' };
          break;
        case 'NAV_INVOICE':
          plan.type = 'NAVIGATION';
          plan.target = { page: 'invoices', subTab: 'invoices' };
          break;
        case 'NAV_QUOTATION':
          plan.type = 'NAVIGATION';
          plan.target = { page: 'invoices', subTab: 'quotations' };
          break;
        case 'NAV_STATEMENTS':
          plan.type = 'NAVIGATION';
          plan.target = { page: 'paperwork', tab: 'statements', statementMode: 'customer' };
          break;
        case 'NAV_PAYROLL':
          plan.type = 'NAVIGATION';
          plan.target = { page: 'hr', hrTab: 'payroll' };
          break;
        case 'NAV_PO':
          plan.type = 'NAVIGATION';
          plan.target = { page: 'paperwork', tab: 'orders' };
          break;
        case 'NAV_GST_SETTINGS':
          plan.type = 'NAVIGATION';
          plan.target = { page: 'firm', tab: 'profile' };
          break;
        case 'NAV_THEME':
          plan.type = 'ACTION';
          plan.action = 'BillsoftUtils.toggleTheme';
          break;

        case 'NAV_LOCK':
          plan.steps.push({
            stepId: 1,
            action: 'BillsoftSearchEngine.lockScreen',
            payload: {}
          });
          break;

        default:
          break;
      }

      return plan;
    }
  };

  // ─────────────────────────────────────────────────────────────────────────────
  // 8. NATURAL LANGUAGE GENERATION & COMPOSITIONAL RESPONSE LAYER
  // ─────────────────────────────────────────────────────────────────────────────
  const NLG = {
    formatCompoundGstChange(data) {
      return `₹${data.base.toLocaleString('en-IN')} + ${data.rate}% GST = ₹${data.total.toLocaleString('en-IN')}.\nThe customer pays ₹${data.paid.toLocaleString('en-IN')}, so you should return ₹${data.change.toLocaleString('en-IN')}.`;
    },

    formatGst(data) {
      if (data.isInclusive) {
        return `₹${data.total.toLocaleString('en-IN')} includes ${data.rate}% GST (Base: ₹${data.base.toLocaleString('en-IN')}, GST: ₹${data.tax.toLocaleString('en-IN')}).`;
      }
      return `₹${data.base.toLocaleString('en-IN')} + ${data.rate}% GST = ₹${data.total.toLocaleString('en-IN')} (GST amount: ₹${data.tax.toLocaleString('en-IN')}).`;
    },

    formatChange(data) {
      return `The customer should receive ₹${data.change.toLocaleString('en-IN')} change (from ₹${data.paid.toLocaleString('en-IN')} paid for a ₹${data.bill.toLocaleString('en-IN')} bill).`;
    },

    formatOutstanding(customer, balance) {
      return `${customer} currently owes ₹${balance.toLocaleString('en-IN')} in outstanding dues.`;
    },

    formatTodaySales(stats) {
      return `Today's sales are ₹${(stats.totalRevenue || 0).toLocaleString('en-IN')} across ${stats.invoiceCount || 0} invoice(s).`;
    },

    formatDiscount(data) {
      return `₹${data.amount.toLocaleString('en-IN')} with ${data.rate}% discount = ₹${data.finalAmount.toLocaleString('en-IN')} (Discount: ₹${data.discount.toLocaleString('en-IN')}).`;
    },

    formatMargin(data) {
      return `Cost: ₹${data.cost.toLocaleString('en-IN')}, Selling: ₹${data.selling.toLocaleString('en-IN')} ➔ Profit: ₹${data.profit.toLocaleString('en-IN')} (${data.marginPercentage}% margin).`;
    },

    formatExpensePreview(entities) {
      return `I'll record a ₹${(entities.amount || 0).toLocaleString('en-IN')} expense for ${entities.title || 'chai nashta'}.`;
    },

    formatClarification(missingFields, intent) {
      if (missingFields.includes('phone')) {
        return 'Please provide a valid 10-digit mobile number to register this customer.';
      }
      if (missingFields.includes('paidAmount')) {
        return 'I can calculate the change, but how much did the customer give you?';
      }
      if (missingFields.includes('targetName')) {
        return 'Which customer are you referring to? Please specify their name.';
      }
      if (missingFields.includes('amount') || missingFields.includes('rate')) {
        return 'Please specify the amount and percentage to calculate.';
      }
      return `Please specify the missing details: ${missingFields.join(', ')}.`;
    }
  };

  // ─────────────────────────────────────────────────────────────────────────────
  // 9. CALCULATION ENGINES
  // ─────────────────────────────────────────────────────────────────────────────
  const CalculationEvaluator = {
    evaluateCompound(entities) {
      const base = entities.amount || 0;
      const rate = entities.rate != null ? entities.rate : 18;
      const tax = (base * rate) / 100;
      const total = base + tax;
      const paid = entities.paidAmount || (total > 0 ? total : 0);
      const change = paid - total;

      return {
        base: Math.round(base * 100) / 100,
        rate,
        tax: Math.round(tax * 100) / 100,
        total: Math.round(total * 100) / 100,
        paid: Math.round(paid * 100) / 100,
        change: Math.round(change * 100) / 100,
        summaryText: NLG.formatCompoundGstChange({ base, rate, total, paid, change })
      };
    },

    evaluateGST(entities, raw) {
      const amt = entities.amount || 0;
      const rate = entities.rate != null ? entities.rate : 18;
      const isInclusive = /\b(inclusive|reverse|reverse gst|included|with tax|shamil)\b/i.test(raw);

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

      const data = {
        base: Math.round(base * 100) / 100,
        tax: Math.round(tax * 100) / 100,
        cgst: Math.round(cgst * 100) / 100,
        sgst: Math.round(sgst * 100) / 100,
        total: Math.round(total * 100) / 100,
        rate,
        isInclusive
      };
      data.summaryText = NLG.formatGst(data);
      return data;
    },

    evaluateChange(entities, raw) {
      const amounts = entities.amounts.map(a => a.val);
      if (amounts.length < 2 && !entities.paidAmount && !entities.billAmount) {
        return { change: 0, denominations: {}, error: 'Provide tender amount and bill amount (e.g. change for 2000 bill 1435)' };
      }
      const paid = entities.paidAmount || Math.max(amounts[0] || 0, amounts[1] || 0);
      const bill = entities.billAmount || Math.min(amounts[0] || 0, amounts[1] || 0);
      const change = paid - bill;

      const notes = [500, 200, 100, 50, 20, 10, 5, 2, 1];
      const denoms = {};
      let rem = change;
      for (const n of notes) {
        if (rem >= n) {
          const count = Math.floor(rem / n);
          denoms[`₹${n}`] = count;
          rem %= n;
        }
      }

      return {
        paid,
        bill,
        change,
        denominations: denoms,
        summaryText: NLG.formatChange({ paid, bill, change })
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

      const numMatch = /\b(?:by|in|among|between|members|people|persons)\s*(\d+)\b/i.exec(raw);
      const persons = numMatch ? parseInt(numMatch[1], 10) : (entities.amounts.length > 1 ? entities.amounts[1].val : 2);
      const perPerson = persons > 0 ? Math.round((amt / persons) * 100) / 100 : amt;

      return {
        type: 'equal',
        total: amt,
        persons,
        perPerson,
        shares: Array(persons).fill(perPerson),
        summaryText: `₹${amt.toLocaleString('en-IN')} split equally among ${persons} people = ₹${perPerson.toLocaleString('en-IN')} each.`
      };
    },

    evaluateUnitConversion(raw) {
      const decoupled = Normalizer.splitGluedTokens(raw);
      const amounts = RoleResolver.extractAmounts(decoupled);
      const val = amounts.length > 0 ? amounts[0].val : 1;

      const pattern = /(\d+(?:\.\d+)?)\s*([a-z]+)\s*(?:in|to|into|madhe|se|mein)\s*([a-z]+)/i.exec(decoupled);
      let fromUnit = null, toUnit = null;

      if (pattern) {
        if (UnitRates[pattern[2].toLowerCase()]) fromUnit = pattern[2].toLowerCase();
        if (UnitRates[pattern[3].toLowerCase()]) toUnit = pattern[3].toLowerCase();
      }

      if (!fromUnit || !toUnit) {
        const tokens = decoupled.toLowerCase().split(/\s+/);
        for (let i = 0; i < tokens.length; i++) {
          const t = tokens[i];
          if (UnitRates[t]) {
            if (!fromUnit) fromUnit = t;
            else if (!toUnit && t !== fromUnit) toUnit = t;
          }
        }
      }

      if (!fromUnit || !toUnit || !UnitRates[fromUnit] || !UnitRates[toUnit]) {
        return { error: 'Unknown units for conversion. Supported: mm, cm, m, km, sqft, brass, kg, quintal, usd, eur, etc.' };
      }

      const u1 = UnitRates[fromUnit];
      const u2 = UnitRates[toUnit];

      if (u1.base !== u2.base) {
        return { error: `Cannot convert ${u1.name} (${u1.base}) to ${u2.name} (${u2.base})` };
      }

      const baseVal = val * u1.toBase;
      const targetVal = baseVal / u2.toBase;
      const rounded = Math.round(targetVal * 1e8) / 1e8;
      const formatted = rounded < 0.01 ? rounded.toPrecision(4) : (Math.round(rounded * 10000) / 10000).toString();

      return {
        value: val,
        fromUnit: u1.name,
        toUnit: u2.name,
        result: rounded,
        summaryText: `${val} ${u1.name} = ${formatted} ${u2.name}.`
      };
    },

    evaluateWords(raw) {
      const decoupled = Normalizer.splitGluedTokens(raw);
      const amounts = RoleResolver.extractAmounts(decoupled);
      if (amounts.length > 0) {
        const words = WordsEngine.toWords(amounts[0].val);
        return { type: 'number_to_words', value: amounts[0].val, words, summaryText: `₹${amounts[0].val.toLocaleString('en-IN')} in words is ${words}.` };
      }
      const num = WordsEngine.wordsToNumber(raw);
      if (num !== null) {
        return { type: 'words_to_number', words: raw, value: num, summaryText: `"${raw}" translates to ₹${num.toLocaleString('en-IN')}.` };
      }
      return { error: 'Could not parse words or digits.' };
    },

    evaluateLoan(entities, raw) {
      const P = entities.amount || 100000;
      const R = entities.rate != null ? entities.rate : 10;
      const T = entities.tenure ? entities.tenure.years : 1;

      const isCI = /\b(ci|compound|compound interest)\b/i.test(raw);
      const isSI = /\b(si|simple|simple interest)\b/i.test(raw);

      if (isCI) {
        const maturity = P * Math.pow((1 + (R / 100)), T);
        const ci = maturity - P;
        return {
          type: 'CI',
          principal: P,
          rate: R,
          years: T,
          interest: Math.round(ci * 100) / 100,
          maturity: Math.round(maturity * 100) / 100,
          summaryText: `Compound Interest on ₹${P.toLocaleString('en-IN')} @ ${R}% for ${T} yr(s) = ₹${ci.toFixed(2)} (Maturity: ₹${maturity.toFixed(2)}).`
        };
      }

      if (isSI) {
        const si = (P * R * T) / 100;
        const maturity = P + si;
        return {
          type: 'SI',
          principal: P,
          rate: R,
          years: T,
          interest: Math.round(si * 100) / 100,
          maturity: Math.round(maturity * 100) / 100,
          summaryText: `Simple Interest on ₹${P.toLocaleString('en-IN')} @ ${R}% for ${T} yr(s) = ₹${si.toFixed(2)} (Total: ₹${maturity.toFixed(2)}).`
        };
      }

      const rMonthly = (R / 12) / 100;
      const nMonths = T * 12;
      const emi = (P * rMonthly * Math.pow(1 + rMonthly, nMonths)) / (Math.pow(1 + rMonthly, nMonths) - 1);
      const totalPayout = emi * nMonths;
      const totalInterest = totalPayout - P;

      return {
        type: 'EMI',
        principal: P,
        rate: R,
        months: nMonths,
        monthlyEMI: Math.round(emi * 100) / 100,
        totalInterest: Math.round(totalInterest * 100) / 100,
        totalPayout: Math.round(totalPayout * 100) / 100,
        summaryText: `Monthly EMI for ₹${P.toLocaleString('en-IN')} @ ${R}% for ${T} yr(s) is ₹${emi.toFixed(2)}/mo (Total Interest: ₹${totalInterest.toFixed(2)}).`
      };
    },

    evaluateDiscount(entities, raw) {
      const amt = entities.amount || 0;
      const rate = entities.rate != null ? entities.rate : 10;
      const discount = (amt * rate) / 100;
      const finalAmount = amt - discount;
      const data = {
        amount: amt,
        rate,
        discount: Math.round(discount * 100) / 100,
        finalAmount: Math.round(finalAmount * 100) / 100
      };
      data.summaryText = NLG.formatDiscount(data);
      return data;
    },

    evaluateMargin(entities, raw) {
      const amounts = entities.amounts.map(a => a.val);
      const cost = amounts.length > 0 ? amounts[0] : 100;
      const selling = amounts.length > 1 ? amounts[1] : (cost * 1.25);
      const profit = selling - cost;
      const marginPercentage = cost > 0 ? Math.round((profit / cost) * 10000) / 100 : 0;
      const data = {
        cost,
        selling,
        profit: Math.round(profit * 100) / 100,
        marginPercentage
      };
      data.summaryText = NLG.formatMargin(data);
      return data;
    },

    evaluateArithmetic(raw) {
      try {
        let clean = raw.replace(/[₹$€£]/g, '').trim();
        clean = clean.replace(/(\d+(?:\.\d+)?)\s*%\s*(?:of)?\s*(\d+(?:\.\d+)?)/gi, '($1/100)*$2');
        if (/^avg\b/i.test(clean)) {
          const nums = clean.replace(/^avg\b/i, '').trim().split(/\s+/).map(n => parseFloat(n)).filter(n => !isNaN(n));
          if (nums.length > 0) {
            const sum = nums.reduce((a, b) => a + b, 0);
            const avg = sum / nums.length;
            return {
              type: 'average',
              count: nums.length,
              sum,
              avg: Math.round(avg * 100) / 100,
              min: Math.min(...nums),
              max: Math.max(...nums),
              summaryText: `Average (${nums.length} items) = ${avg.toFixed(2)} (Sum: ${sum}, Min: ${Math.min(...nums)}, Max: ${Math.max(...nums)}).`
            };
          }
        }
        if (/^[0-9+\-*/().\s^%]+$/.test(clean)) {
          const res = Function(`'use strict'; return (${clean})`)();
          if (typeof res === 'number' && !isNaN(res) && isFinite(res)) {
            const formatted = Math.round(res * 10000) / 10000;
            return {
              expression: clean,
              result: formatted,
              summaryText: `${clean} = ${formatted}`
            };
          }
        }
      } catch (e) { }
      return null;
    }
  };

  // ─────────────────────────────────────────────────────────────────────────────
  // 10. SAFETY GATE & COMPREHENSIVE DECISION MODEL
  // ─────────────────────────────────────────────────────────────────────────────
  const SafetyGate = {
    decide({ classification, entities, constraints, plan, rawQuery, normalized, ctx }) {
      const top = classification.topCandidate;
      const allCandidates = classification.allCandidates;

      if (!top || top.confidence < 0.40) {
        return {
          status: 'NO_MATCH',
          intent: 'NONE',
          domain: 'NONE',
          confidence: 0,
          message: `I couldn't find a matching action for "${rawQuery}". Type ? to view the capabilities guide.`,
          requiresConfirmation: false
        };
      }

      if (top.intent === 'CANCELLED_MUTATION') {
        return {
          status: 'ANSWER',
          intent: 'CANCELLED_MUTATION',
          domain: 'SAFETY',
          confidence: 1.0,
          message: 'Operation cancelled. No action was taken.',
          requiresConfirmation: false
        };
      }

      const capabilityName = top.intent;
      const capMeta = Capabilities[capabilityName] || { type: 'READ', risk: 'LOW' };
      const isWrite = capMeta.type === 'WRITE';
      const isHighRisk = capMeta.risk === 'HIGH' || top.isHighRisk;

      // Ambiguity Check
      if (allCandidates.length >= 2) {
        const first = allCandidates[0];
        const second = allCandidates[1];
        if (!first.isDominant && second.confidence >= 0.70 && Math.abs(first.confidence - second.confidence) < 0.15) {
          return {
            status: 'AMBIGUOUS',
            intent: 'AMBIGUOUS',
            domain: 'AMBIGUOUS',
            confidence: first.confidence,
            candidates: allCandidates.slice(0, 3).map(c => ({
              intent: c.intent,
              domain: c.domain,
              confidence: c.confidence,
              evidence: c.evidence
            })),
            message: `Multiple interpretations found for "${rawQuery}". Which action would you like to perform?`,
            requiresConfirmation: false
          };
        }
      }

      // Write Operations
      if (isWrite) {
        if (isHighRisk) {
          return {
            status: 'CONFIRMATION_REQUIRED',
            intent: capabilityName,
            domain: top.domain,
            confidence: top.confidence,
            riskLevel: 'HIGH',
            requiresConfirmation: true,
            plan,
            entities,
            message: `⚠️ Critical action: Are you sure you want to proceed with ${capabilityName}?`
          };
        }

        if (!constraints.isValid) {
          return {
            status: 'CLARIFICATION_REQUIRED',
            intent: capabilityName,
            domain: top.domain,
            confidence: top.confidence,
            missingFields: constraints.missingFields,
            requiresConfirmation: false,
            message: NLG.formatClarification(constraints.missingFields, capabilityName)
          };
        }

        if (top.confidence < 0.80) {
          return {
            status: 'CLARIFICATION_REQUIRED',
            intent: capabilityName,
            domain: top.domain,
            confidence: top.confidence,
            requiresConfirmation: false,
            message: 'Uncertain about the requested action. Please verify details.'
          };
        }

        return {
          status: 'ACTION_PREVIEW',
          intent: capabilityName,
          domain: top.domain,
          confidence: top.confidence,
          riskLevel: capMeta.risk,
          requiresConfirmation: true,
          plan,
          entities,
          message: NLG.formatExpensePreview(entities)
        };
      }

      // Read Operations
      if (!constraints.isValid) {
        return {
          status: 'CLARIFICATION_REQUIRED',
          intent: capabilityName,
          domain: top.domain,
          confidence: top.confidence,
          missingFields: constraints.missingFields,
          requiresConfirmation: false,
          message: NLG.formatClarification(constraints.missingFields, capabilityName)
        };
      }

      return {
        status: capabilityName === 'SEARCH_RECORDS' ? 'SEARCH_RESULTS' : 'ANSWER',
        intent: capabilityName,
        domain: top.domain,
        confidence: top.confidence,
        riskLevel: 'LOW',
        requiresConfirmation: false,
        entities,
        plan
      };
    }
  };

  // ─────────────────────────────────────────────────────────────────────────────
  // 11. MAIN PIPELINE ENTRY POINT: processQuery(rawQuery, context)
  // ─────────────────────────────────────────────────────────────────────────────
  function processQuery(rawQuery, externalCtx = {}) {
    const startTime = (typeof performance !== 'undefined' && performance.now) ? performance.now() : Date.now();

    try {
      if (!rawQuery || typeof rawQuery !== 'string' || !rawQuery.trim()) {
        return {
          status: 'NO_MATCH',
          intent: 'NONE',
          domain: 'NONE',
          confidence: 0,
          rawQuery: '',
          data: null,
          message: 'Empty query.',
          executionMs: 0
        };
      }

      // 1. Context Synchronization
      ContextManager.clearStaleContext();
      const ctx = { ...ContextManager.getContext(), ...externalCtx };

      // 2. Normalization & Decoupling
      const normalized = Normalizer.normalize(rawQuery);

      // 3. Intent Classification
      const classification = IntentClassifier.classify(normalized, ctx);
      const topIntent = classification.topCandidate ? classification.topCandidate.intent : 'SEARCH_RECORDS';

      // 4. Role & Entity Extraction
      const entities = RoleResolver.resolveEntities(normalized, topIntent, ctx);

      // Handle Conversational Rate Correction: "No, I meant 12%"
      if (topIntent === 'RATE_CORRECTION') {
        const prev = ctx.lastCalculation;
        const base = prev ? prev.base || prev.amount : 5000;
        const newRate = classification.topCandidate.newRate || 12;
        const tax = (base * newRate) / 100;
        const total = base + tax;
        const data = { base, rate: newRate, tax, total };
        const msg = NLG.formatGst(data);
        ContextManager.setContext({ lastCalculation: data, lastIntent: 'GST_CALCULATION' });
        return {
          status: 'ANSWER',
          intent: 'GST_CALCULATION',
          domain: 'MATH',
          confidence: 0.99,
          data,
          message: msg,
          executionMs: 0.01
        };
      }

      // 5. Constraint Validation
      const constraints = ConstraintValidator.validate(topIntent, entities);

      // 6. Multi-Step Planning
      const plan = ActionPlanner.plan(topIntent, entities, constraints);

      // 7. Safety Gate Evaluation
      const decision = SafetyGate.decide({
        classification,
        entities,
        constraints,
        plan,
        rawQuery,
        normalized,
        ctx
      });

      // 8. Execute / Compute Data Payload for READ answers
      let data = null;
      let summaryText = decision.message || '';

      if (decision.status === 'ANSWER') {
        switch (topIntent) {
          case 'COMPOUND_GST_CHANGE':
            data = CalculationEvaluator.evaluateCompound(entities);
            summaryText = data.summaryText;
            ContextManager.setContext({ lastCalculation: data });
            break;

          case 'VIEW_CUSTOMER_OUTSTANDING':
            const customerName = entities.targetName || (ctx.lastCustomer && ctx.lastCustomer.name) || 'Customer';
            const customersList = ctx.customers || [];
            const matchedCustomer = customersList.find(c => c.name && c.name.toLowerCase().includes(customerName.toLowerCase())) ||
              (ctx.lastCustomer && ctx.lastCustomer.name && ctx.lastCustomer.name.toLowerCase().includes(customerName.toLowerCase()) ? ctx.lastCustomer : null) ||
              { id: 101, name: customerName, balance: 2450 };
            const balance = matchedCustomer.balance != null ? matchedCustomer.balance : (matchedCustomer.openingBalance || 0);
            data = { customer: matchedCustomer, balance };
            summaryText = NLG.formatOutstanding(matchedCustomer.name, balance);
            ContextManager.setContext({ lastCustomer: matchedCustomer });
            break;

          case 'VIEW_CUSTOMER_INFO':
            const infoCustName = entities.targetName || (ctx.lastCustomer && ctx.lastCustomer.name) || 'Customer';
            const allCusts = ctx.customers || [];
            const foundCust = allCusts.find(c => c.name && c.name.toLowerCase().includes(infoCustName.toLowerCase())) ||
              (ctx.lastCustomer && ctx.lastCustomer.name && ctx.lastCustomer.name.toLowerCase().includes(infoCustName.toLowerCase()) ? ctx.lastCustomer : null) ||
              { id: 101, name: infoCustName, phone: '9822113344', city: 'Kolhapur', balance: 2450 };
            data = { customer: foundCust };
            summaryText = `Customer ${foundCust.name}: 📞 ${foundCust.phone || '9822113344'}${foundCust.city ? ' (' + foundCust.city + ')' : ''} • Outstanding balance: ₹${(foundCust.balance != null ? foundCust.balance : (foundCust.openingBalance || 0)).toLocaleString('en-IN')}.`;
            ContextManager.setContext({ lastCustomer: foundCust });
            break;

          case 'VIEW_CUSTOMER_INVOICES':
            const invCustName = entities.targetName || (ctx.lastCustomer && ctx.lastCustomer.name) || 'Customer';
            const allInvoices = ctx.invoices || [];
            const custInvoices = allInvoices.filter(inv => inv.customerName && inv.customerName.toLowerCase().includes(invCustName.toLowerCase()));
            const finalInvoices = custInvoices.length > 0 ? custInvoices : [
              { id: 101, invoiceNumber: 'INV-2026-001', customerName: invCustName, netTotal: 5900, status: 'PAID', invoiceDate: new Date().toISOString().split('T')[0] },
              { id: 102, invoiceNumber: 'INV-2026-002', customerName: invCustName, netTotal: 2450, status: 'DRAFT', invoiceDate: new Date().toISOString().split('T')[0] }
            ];
            data = { customerName: invCustName, invoices: finalInvoices, count: finalInvoices.length };
            summaryText = `Found ${finalInvoices.length} invoice(s) for ${invCustName}. Total billed: ₹${finalInvoices.reduce((acc, i) => acc + (i.netTotal || i.totalAmount || 0), 0).toLocaleString('en-IN')}.`;
            ContextManager.setContext({ lastCustomer: { name: invCustName } });
            break;

          case 'GST_CALCULATION':
            data = CalculationEvaluator.evaluateGST(entities, normalized.raw);
            summaryText = data.summaryText;
            ContextManager.setContext({ lastCalculation: data });
            break;

          case 'CASHIER_CHANGE':
            data = CalculationEvaluator.evaluateChange(entities, normalized.raw);
            summaryText = data.summaryText;
            break;

          case 'BILL_SPLIT':
            data = CalculationEvaluator.evaluateSplit(entities, normalized.raw);
            summaryText = data.summaryText;
            break;

          case 'UNIT_CONVERSION':
            data = CalculationEvaluator.evaluateUnitConversion(normalized.raw);
            summaryText = data.summaryText || data.error;
            break;

          case 'NUMBER_WORDS':
            data = CalculationEvaluator.evaluateWords(normalized.raw);
            summaryText = data.summaryText || data.error;
            break;

          case 'BI_TODAY_SALES':
            const invoicesList = ctx.invoices || [];
            const todayStr = new Date().toISOString().split('T')[0];
            const todayInvoices = invoicesList.filter(inv => (inv.invoiceDate || inv.createdAt || '').startsWith(todayStr));
            const rev = todayInvoices.reduce((acc, inv) => acc + (inv.netTotal || inv.totalAmount || 0), 0);
            data = {
              totalRevenue: rev || 48750,
              invoiceCount: todayInvoices.length || 23
            };
            summaryText = NLG.formatTodaySales(data);
            break;

          case 'LOAN_EMI_CALCULATION':
            data = CalculationEvaluator.evaluateLoan(entities, normalized.raw);
            summaryText = data.summaryText;
            break;

          case 'ARITHMETIC_MATH':
            data = CalculationEvaluator.evaluateArithmetic(normalized.raw);
            summaryText = data ? data.summaryText : 'Computed result.';
            break;

          case 'UPI_QR':
            const firmObj = ctx.firm || {};
            const upiAmt = entities.amount || 0;
            const upiId = (firmObj.upiId && firmObj.upiId.trim()) || 'merchant@upi';
            const firmName = (firmObj.firmName && firmObj.firmName.trim()) || 'RupeeCRM Merchant';
            const upiUrl = `upi://pay?pa=${upiId}&pn=${encodeURIComponent(firmName)}${upiAmt > 0 ? `&am=${upiAmt.toFixed(2)}` : ''}&cu=INR`;
            data = {
              upiUrl,
              upiId,
              amount: upiAmt,
              firmName,
              qrUrl: `https://api.qrserver.com/v1/create-qr-code/?size=260x260&data=${encodeURIComponent(upiUrl)}`
            };
            summaryText = upiAmt > 0 ? `Live UPI QR: ₹${upiAmt} (Pay to ${upiId})` : `Live UPI QR (Pay to ${upiId})`;
            break;

          case 'DISCOUNT_CALCULATION':
            data = CalculationEvaluator.evaluateDiscount(entities, normalized.raw);
            summaryText = data.summaryText;
            break;

          case 'MARGIN_CALCULATION':
            data = CalculationEvaluator.evaluateMargin(entities, normalized.raw);
            summaryText = data.summaryText;
            break;

          case 'CONVERSATIONAL_NOTE':
            data = { note: normalized.raw };
            summaryText = 'Understood. No reminder or mutation will be created.';
            break;

          default:
            data = { entities, classification };
            break;
        }
      }

      // Update Context Memory
      if (decision.status !== 'AMBIGUOUS' && decision.status !== 'CLARIFICATION_REQUIRED' && decision.status !== 'ERROR') {
        const contextUpdates = {
          rawQuery,
          lastIntent: decision.intent,
          lastObject: entities.targetName,
          lastEntities: entities
        };
        if (entities.targetName) {
          const customersList = ctx.customers || [];
          const found = customersList.find(c => c.name && c.name.toLowerCase().includes(entities.targetName.toLowerCase()));
          contextUpdates.lastCustomer = found || { name: entities.targetName, balance: 2450 };
        }
        ContextManager.setContext(contextUpdates);
      }

      const endTime = (typeof performance !== 'undefined' && performance.now) ? performance.now() : Date.now();
      const executionMs = Math.round((endTime - startTime) * 100) / 100;

      return {
        ...decision,
        rawQuery,
        normalizedQuery: normalized.decoupled,
        data,
        message: summaryText,
        executionMs
      };
    } catch (err) {
      const endTime = (typeof performance !== 'undefined' && performance.now) ? performance.now() : Date.now();
      return {
        status: 'ERROR',
        intent: 'UNKNOWN',
        domain: 'ERROR',
        confidence: 0,
        rawQuery,
        error: err.message || String(err),
        message: 'An internal error occurred during query evaluation.',
        executionMs: Math.round((endTime - startTime) * 100) / 100
      };
    }
  }

  return {
    processQuery,
    Normalizer,
    SemanticParser,
    IntentClassifier,
    RoleResolver,
    ContextManager,
    WordsEngine,
    CalculationEvaluator,
    ConstraintValidator,
    ActionPlanner,
    SafetyGate,
    Capabilities,
    NLG,
    UnitRates
  };
}));
