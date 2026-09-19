/**
 * RupeeCRM Unified Casio-Style Calculator & Omnisearch NLP Math Engine
 * 
 * Features:
 * - Single Unified Calculator Window (No separate tabs or modes)
 * - Immediate live calculations on every keystroke:
 *   - Normal arithmetic: calculated locally inside CalcEngine (never touches Omnisearch)
 *   - NLP / Conversions: evaluated via existing Omnisearch NLP pipeline with minimal debounce
 * - Smart Backspace/Delete:
 *   - Normal mode: deletes last character one by one (12500 -> 1250 -> 125 -> 12 -> 1)
 *   - NLP mode: deletes the entire query as one unit
 * - Authentic Casio Physical Handheld Aesthetics (Solar strip, LCD screen, tactile 3D keys)
 * - Auto-focuses on open, stops intercepting when unfocused
 */

(function (root, factory) {
  if (typeof define === 'function' && define.amd) {
    define(['react', 'react-dom'], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory(require('react'), require('react-dom'));
  } else {
    root.RupeeCRMCalculator = factory(root.React, root.ReactDOM);
  }
}(typeof self !== 'undefined' ? self : this, function (React, ReactDOM) {
  'use strict';

  if (!React) return null;
  const { useState, useEffect, useRef, useMemo, useCallback } = React;

  // ─────────────────────────────────────────────────────────────────────────────
  // 1. SAFE DETERMINISTIC ARITHMETIC ENGINE (Zero eval)
  // ─────────────────────────────────────────────────────────────────────────────
  const CalcEngine = {
    isNlp(str) {
      if (!str || typeof str !== 'string') return false;
      // Strip valid math characters, digits, whitespaces, currency symbols
      const stripped = str.replace(/[0-9\s+\-*\/÷×%^().,₹$€£=]/g, '');
      // If alphabetic words are present (like 'of', 'lakh', 'miles', 'km', 'gst', 'divided'), it's NLP
      return /[a-zA-Z]{2,}/.test(stripped);
    },

    formatDisplay(num) {
      if (num === null || num === undefined || isNaN(num) || !isFinite(num)) {
        return 'Error';
      }
      // Round floating point noise safely
      const rounded = Number(Math.round(Number(num + 'e+10')) + 'e-10');
      const str = rounded.toString();
      const parts = str.split('.');
      const isNeg = parts[0].startsWith('-');
      let intPart = isNeg ? parts[0].substring(1) : parts[0];

      // Indian comma format
      if (intPart.length > 3) {
        const last3 = intPart.slice(-3);
        const other = intPart.slice(0, -3);
        intPart = other.replace(/\B(?=(\d{2})+(?!\d))/g, ',') + ',' + last3;
      }
      return (isNeg ? '-' : '') + intPart + (parts[1] ? '.' + parts[1] : '');
    },

    tokenize(expr) {
      if (!expr || typeof expr !== 'string') return null;
      let s = expr.replace(/,/g, '').replace(/[₹$€£]/g, '').replace(/×/g, '*').replace(/÷/g, '/');
      const tokens = [];
      let i = 0;

      while (i < s.length) {
        const ch = s[i];
        if (/\s/.test(ch)) {
          i++;
          continue;
        }

        if (ch === '(' || ch === ')') {
          tokens.push({ type: ch === '(' ? 'LPAREN' : 'RPAREN', val: ch });
          i++;
          continue;
        }

        if (ch === '+' || ch === '-' || ch === '*' || ch === '/' || ch === '%' || ch === '^') {
          tokens.push({ type: 'OP', val: ch });
          i++;
          continue;
        }

        if (/\d/.test(ch) || (ch === '.' && /\d/.test(s[i + 1] || ''))) {
          let numStr = '';
          while (i < s.length && /[\d.]/.test(s[i])) {
            numStr += s[i];
            i++;
          }
          const numVal = parseFloat(numStr);
          if (isNaN(numVal)) return null;
          tokens.push({ type: 'NUM', val: numVal });
          continue;
        }

        return null; // Contains non-arithmetic character
      }
      return tokens;
    },

    evaluate(exprStr) {
      if (!exprStr || !exprStr.trim()) return null;
      const rawTokens = this.tokenize(exprStr);
      if (!rawTokens || rawTokens.length === 0) return null;

      // Single number shortcut
      if (rawTokens.length === 1 && rawTokens[0].type === 'NUM') {
        return rawTokens[0].val;
      }

      // 1. Process Unary Operators
      const sanitized = [];
      for (let i = 0; i < rawTokens.length; i++) {
        const t = rawTokens[i];
        if (t.type === 'OP' && (t.val === '-' || t.val === '+')) {
          const prev = sanitized[sanitized.length - 1];
          if (!prev || prev.type === 'OP' || prev.type === 'LPAREN') {
            sanitized.push({ type: 'UNARY_OP', val: t.val });
            continue;
          }
        }
        sanitized.push(t);
      }

      // 2. Handle Infix Percentages: "A + B%" -> A + (A * B / 100), "A - B%" -> A - (A * B / 100), "A * B%" -> A * (B / 100)
      const finalTokens = [];
      for (let i = 0; i < sanitized.length; i++) {
        const t = sanitized[i];
        if (t.type === 'OP' && t.val === '%') {
          if (finalTokens.length >= 2) {
            const prevOpToken = finalTokens[finalTokens.length - 2];
            if (prevOpToken && prevOpToken.type === 'OP' && (prevOpToken.val === '+' || prevOpToken.val === '-')) {
              const baseToken = finalTokens.length >= 3 ? finalTokens[finalTokens.length - 3] : null;
              const baseVal = (baseToken && baseToken.type === 'NUM') ? baseToken.val : 1;
              const currentNumToken = finalTokens[finalTokens.length - 1];
              if (currentNumToken && currentNumToken.type === 'NUM') {
                finalTokens[finalTokens.length - 1] = { type: 'NUM', val: (baseVal * currentNumToken.val) / 100 };
                continue;
              }
            }
          }
          const currentNum = finalTokens[finalTokens.length - 1];
          if (currentNum && currentNum.type === 'NUM') {
            finalTokens[finalTokens.length - 1] = { type: 'NUM', val: currentNum.val / 100 };
          }
        } else {
          finalTokens.push(t);
        }
      }

      // 3. Shunting-Yard Algorithm to Postfix (RPN)
      const outputQueue = [];
      const opStack = [];
      const precedence = { '+': 1, '-': 1, '*': 2, '/': 2, '^': 3, 'UNARY': 4 };

      for (const token of finalTokens) {
        if (token.type === 'NUM') {
          outputQueue.push(token);
        } else if (token.type === 'UNARY_OP') {
          opStack.push(token);
        } else if (token.type === 'OP') {
          while (
            opStack.length > 0 &&
            opStack[opStack.length - 1].type !== 'LPAREN' &&
            (precedence[opStack[opStack.length - 1].type === 'UNARY_OP' ? 'UNARY' : opStack[opStack.length - 1].val] >= precedence[token.val])
          ) {
            outputQueue.push(opStack.pop());
          }
          opStack.push(token);
        } else if (token.type === 'LPAREN') {
          opStack.push(token);
        } else if (token.type === 'RPAREN') {
          let found = false;
          while (opStack.length > 0) {
            const top = opStack.pop();
            if (top.type === 'LPAREN') {
              found = true;
              break;
            }
            outputQueue.push(top);
          }
          if (!found) return null;
        }
      }

      while (opStack.length > 0) {
        const top = opStack.pop();
        if (top.type === 'LPAREN' || top.type === 'RPAREN') return null;
        outputQueue.push(top);
      }

      // 4. Evaluate RPN
      const evalStack = [];
      for (const t of outputQueue) {
        if (t.type === 'NUM') {
          evalStack.push(t.val);
        } else if (t.type === 'UNARY_OP') {
          if (evalStack.length < 1) return null;
          const a = evalStack.pop();
          evalStack.push(t.val === '-' ? -a : +a);
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
              if (b === 0) return null; // Prevent div by zero
              res = a / b;
              break;
            case '^': res = Math.pow(a, b); break;
            default: return null;
          }
          evalStack.push(res);
        }
      }

      if (evalStack.length !== 1) return null;
      const finalResult = evalStack[0];
      return (typeof finalResult === 'number' && !isNaN(finalResult) && isFinite(finalResult)) ? finalResult : null;
    }
  };

  // ─────────────────────────────────────────────────────────────────────────────
  // 2. MAIN UNIFIED CASIO CALCULATOR COMPONENT
  // ─────────────────────────────────────────────────────────────────────────────
  function RupeeCRMCalculator({ isOpen, onClose }) {
    if (!isOpen) return null;

    // State
    const [query, setQuery] = useState('');
    const [displayResult, setDisplayResult] = useState('0');
    const [detailsText, setDetailsText] = useState('');
    const [lastAnswer, setLastAnswer] = useState(null);
    const [memory, setMemory] = useState(0);
    const [showHistory, setShowHistory] = useState(false);
    const [history, setHistory] = useState(() => {
      try {
        const saved = localStorage.getItem('rupeecrm_calc_history');
        return saved ? JSON.parse(saved) : [];
      } catch (e) {
        return [];
      }
    });
    const [isMinimized, setIsMinimized] = useState(false);
    const [copied, setCopied] = useState(false);
    const [isFocused, setIsFocused] = useState(true);

    // Window position & dragging
    const [pos, setPos] = useState(() => {
      const w = window.innerWidth;
      const h = window.innerHeight;
      return {
        x: Math.max(20, w - 370),
        y: Math.max(70, Math.min(90, h - 560))
      };
    });
    const [isDragging, setIsDragging] = useState(false);
    const dragOffset = useRef({ x: 0, y: 0 });
    const calcRef = useRef(null);
    const inputRef = useRef(null);
    const nlpDebounceTimer = useRef(null);

    // Immediate & robust auto-focus on open
    const focusInput = useCallback(() => {
      if (inputRef.current) {
        inputRef.current.focus();
        setIsFocused(true);
      }
    }, []);

    useEffect(() => {
      if (isOpen) {
        focusInput();
        const animId = requestAnimationFrame(focusInput);
        const t1 = setTimeout(focusInput, 20);
        const t2 = setTimeout(focusInput, 80);
        const t3 = setTimeout(focusInput, 200);
        return () => {
          cancelAnimationFrame(animId);
          clearTimeout(t1);
          clearTimeout(t2);
          clearTimeout(t3);
        };
      }
    }, [isOpen, focusInput]);

    // Save history
    useEffect(() => {
      try {
        localStorage.setItem('rupeecrm_calc_history', JSON.stringify(history.slice(0, 50)));
      } catch (e) {}
    }, [history]);

    // Dragging listeners
    useEffect(() => {
      const handleMouseMove = (e) => {
        if (!isDragging) return;
        const newX = Math.max(10, Math.min(window.innerWidth - 340, e.clientX - dragOffset.current.x));
        const newY = Math.max(10, Math.min(window.innerHeight - 80, e.clientY - dragOffset.current.y));
        setPos({ x: newX, y: newY });
      };
      const handleMouseUp = () => setIsDragging(false);

      if (isDragging) {
        window.addEventListener('mousemove', handleMouseMove);
        window.addEventListener('mouseup', handleMouseUp);
      }
      return () => {
        window.removeEventListener('mousemove', handleMouseMove);
        window.removeEventListener('mouseup', handleMouseUp);
      };
    }, [isDragging]);

    const startDrag = (e) => {
      if (e.target.tagName === 'BUTTON' || e.target.tagName === 'INPUT' || e.target.closest('button')) return;
      setIsDragging(true);
      dragOffset.current = {
        x: e.clientX - pos.x,
        y: e.clientY - pos.y
      };
    };

    // Add History Item
    const addHistory = (expr, res) => {
      const formattedRes = typeof res === 'number' ? CalcEngine.formatDisplay(res) : String(res);
      const item = {
        id: Date.now().toString() + Math.random().toString(36).substring(2, 5),
        expr,
        result: formattedRes,
        rawResult: res,
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      };
      setHistory(prev => [item, ...prev.slice(0, 49)]);
    };

    // ─────────────────────────────────────────────────────────────────────────
    // LIVE CALCULATION DISPATCHER (ON EVERY KEYSTROKE)
    // ─────────────────────────────────────────────────────────────────────────
    const evaluateLive = (rawText, isFinalSubmit = false) => {
      const text = rawText !== undefined ? rawText : query;
      const q = text.trim();

      if (!q) {
        setDisplayResult('0');
        setDetailsText('');
        return;
      }

      // Check if this query is NLP vs Normal Arithmetic
      const isNlp = CalcEngine.isNlp(q);

      if (!isNlp) {
        // ── MODE A: NORMAL ARITHMETIC (100% Local, Never touches Omnisearch) ──
        const mathRes = CalcEngine.evaluate(q);
        if (mathRes !== null && !isNaN(mathRes) && isFinite(mathRes)) {
          const formatted = CalcEngine.formatDisplay(mathRes);
          setDisplayResult(formatted);
          setDetailsText('');
          if (isFinalSubmit) {
            setLastAnswer(mathRes);
            addHistory(q, mathRes);
          }
        } else if (isFinalSubmit) {
          setDisplayResult('Error');
          setDetailsText('Invalid mathematical expression');
        }
        return;
      }

      // ── MODE B: NLP / CONVERSIONS (Delegated to Omnisearch NLP Engine) ──
      const runNlp = () => {
        try {
          let handled = false;

          if (typeof OmnibarPipeline !== 'undefined') {
            // A. Process query through OmnibarPipeline.processQuery
            if (OmnibarPipeline.processQuery) {
              const out = OmnibarPipeline.processQuery(q, {});
              if (out && out.status === 'ANSWER' && out.data) {
                const d = out.data;
                const numeric = (typeof d.result === 'number') ? d.result :
                                (typeof d.total === 'number') ? d.total :
                                (typeof d.toValue === 'number') ? d.toValue :
                                (typeof d.finalAmount === 'number') ? d.finalAmount : null;

                const displayStr = (d.toUnit && (d.result !== undefined || d.toValue !== undefined))
                  ? `${CalcEngine.formatDisplay(d.result !== undefined ? d.result : d.toValue)} ${d.toUnit}`
                  : (numeric !== null) ? CalcEngine.formatDisplay(numeric)
                  : (d.formattedResult || (d.summaryText || out.title));

                setDisplayResult(displayStr);
                if (numeric !== null && isFinalSubmit) setLastAnswer(numeric);

                // Subtitle / Breakdown line
                if (out.subtitle) {
                  setDetailsText(out.subtitle);
                } else if (d.summaryText) {
                  setDetailsText(d.summaryText);
                }

                if (isFinalSubmit) {
                  addHistory(q, numeric !== null ? numeric : displayStr);
                }
                handled = true;
              }
            }

            // B. Direct Fallback via CalculationEvaluator
            if (!handled && OmnibarPipeline.CalculationEvaluator) {
              const evalObj = OmnibarPipeline.CalculationEvaluator;
              if (evalObj.evaluateUnitConversion) {
                const conv = evalObj.evaluateUnitConversion(q);
                if (conv) {
                  const convVal = conv.result !== undefined ? conv.result : conv.toValue;
                  const resStr = `${CalcEngine.formatDisplay(convVal)} ${conv.toUnit || ''}`;
                  setDisplayResult(resStr);
                  if (typeof convVal === 'number' && isFinalSubmit) setLastAnswer(convVal);
                  setDetailsText(conv.summaryText || `${conv.value || conv.fromValue} ${conv.fromUnit} = ${convVal} ${conv.toUnit}`);
                  if (isFinalSubmit) addHistory(q, convVal);
                  handled = true;
                }
              }
              if (!handled && evalObj.evaluateArithmetic) {
                const arith = evalObj.evaluateArithmetic(q);
                if (arith && arith.result !== undefined) {
                  const formatted = arith.formattedResult || CalcEngine.formatDisplay(arith.result);
                  setDisplayResult(formatted);
                  if (isFinalSubmit) setLastAnswer(arith.result);
                  setDetailsText(arith.summaryText || `${arith.expression} = ₹${formatted}`);
                  if (isFinalSubmit) addHistory(q, arith.result);
                  handled = true;
                }
              }
            }
          }

          if (!handled && isFinalSubmit) {
            setDisplayResult('Error');
            setDetailsText('Unrecognized calculation');
          }
        } catch (err) {
          if (isFinalSubmit) {
            setDisplayResult('Error');
            setDetailsText('Calculation error');
          }
        }
      };

      if (isFinalSubmit) {
        if (nlpDebounceTimer.current) clearTimeout(nlpDebounceTimer.current);
        runNlp();
      } else {
        // Minimal short debounce (50ms) to avoid excessive pipeline churning
        if (nlpDebounceTimer.current) clearTimeout(nlpDebounceTimer.current);
        nlpDebounceTimer.current = setTimeout(runNlp, 50);
      }
    };

    // Live effect when query changes
    const updateQueryLive = (newQuery) => {
      setQuery(newQuery);
      evaluateLive(newQuery, false);
    };

    // ─────────────────────────────────────────────────────────────────────────
    // SMART DELETE / BACKSPACE LOGIC
    // Normal: deletes character by character (12500 -> 1250 -> 125 -> 12 -> 1)
    // NLP: deletes entire query in one step
    // ─────────────────────────────────────────────────────────────────────────
    const handleSmartDelete = () => {
      if (!query) {
        setDisplayResult('0');
        setDetailsText('');
        return;
      }

      if (CalcEngine.isNlp(query)) {
        // NLP mode: Clear whole query as one unit
        updateQueryLive('');
      } else {
        // Normal arithmetic mode: delete last character
        const trimmedEnd = query.trimEnd();
        const next = trimmedEnd.slice(0, -1);
        updateQueryLive(next);
      }
      if (inputRef.current) inputRef.current.focus();
    };

    // ─────────────────────────────────────────────────────────────────────────
    // Keypad Handlers
    // ─────────────────────────────────────────────────────────────────────────
    const appendToQuery = (str) => {
      const next = query + str;
      updateQueryLive(next);
      if (inputRef.current) inputRef.current.focus();
    };

    const handleDigit = (d) => {
      appendToQuery(d);
    };

    const handleOperator = (op) => {
      const symbol = op === '*' ? '×' : op === '/' ? '÷' : op;
      if (!query && displayResult !== '0' && displayResult !== 'Error') {
        const clean = displayResult.replace(/,/g, '');
        updateQueryLive(clean + ' ' + symbol + ' ');
      } else {
        updateQueryLive(query + ' ' + symbol + ' ');
      }
      if (inputRef.current) inputRef.current.focus();
    };

    const handleClearAll = () => {
      setQuery('');
      setDisplayResult('0');
      setDetailsText('');
      if (inputRef.current) inputRef.current.focus();
    };

    const handleNegate = () => {
      if (!query && displayResult !== '0' && displayResult !== 'Error') {
        const clean = displayResult.replace(/,/g, '');
        updateQueryLive(`-(${clean})`);
      } else {
        updateQueryLive(`-(${query})`);
      }
      if (inputRef.current) inputRef.current.focus();
    };

    const handlePercent = () => {
      appendToQuery('%');
    };

    const handleSquare = () => {
      if (!query && displayResult !== '0' && displayResult !== 'Error') {
        const clean = displayResult.replace(/,/g, '');
        const q = `(${clean})^2`;
        setQuery(q);
        evaluateLive(q, true);
      } else if (query) {
        const q = `(${query})^2`;
        setQuery(q);
        evaluateLive(q, true);
      }
    };

    const handleSqrt = () => {
      if (!query && displayResult !== '0' && displayResult !== 'Error') {
        const clean = displayResult.replace(/,/g, '');
        const q = `sqrt(${clean})`;
        setQuery(q);
        evaluateLive(q, true);
      } else if (query) {
        const q = `sqrt(${query})`;
        setQuery(q);
        evaluateLive(q, true);
      }
    };

    // Memory Controls
    const handleMemoryAdd = () => {
      const cur = lastAnswer !== null ? lastAnswer : parseFloat(displayResult.replace(/,/g, '')) || 0;
      setMemory(prev => prev + cur);
    };
    const handleMemorySub = () => {
      const cur = lastAnswer !== null ? lastAnswer : parseFloat(displayResult.replace(/,/g, '')) || 0;
      setMemory(prev => prev - cur);
    };
    const handleMemoryRecall = () => {
      appendToQuery(memory.toString());
    };
    const handleMemoryClear = () => {
      setMemory(0);
    };

    // Copy to clipboard
    const handleCopy = () => {
      if (navigator.clipboard && navigator.clipboard.writeText && displayResult !== 'Error') {
        const clean = displayResult.replace(/,/g, '');
        navigator.clipboard.writeText(clean).then(() => {
          setCopied(true);
          setTimeout(() => setCopied(false), 1500);
        }).catch(() => {});
      }
    };

    // Form submit on Enter in unified input
    const handleSubmit = (e) => {
      if (e) e.preventDefault();
      evaluateLive(query, true);
    };

    // ─────────────────────────────────────────────────────────────────────────
    // Keyboard Event Listener (Only intercepts when Calculator has focus)
    // ─────────────────────────────────────────────────────────────────────────
    useEffect(() => {
      const handleKeyDown = (e) => {
        const active = document.activeElement;
        const isCalcActive = calcRef.current && (calcRef.current.contains(active) || active === inputRef.current);

        // Escape closes calculator when Calculator or its input has focus
        if (e.key === 'Escape') {
          if (isCalcActive) {
            e.preventDefault();
            e.stopPropagation();
            onClose();
            return;
          }
        }

        if (isCalcActive) {
          if (e.key === 'Backspace') {
            // Check if active element is the input and if it's NLP mode
            if (CalcEngine.isNlp(query)) {
              e.preventDefault();
              handleSmartDelete();
            }
          }
        }
      };

      window.addEventListener('keydown', handleKeyDown);
      return () => window.removeEventListener('keydown', handleKeyDown);
    }, [query, onClose]);

    // ─────────────────────────────────────────────────────────────────────────
    // Render JSX Structure
    // ─────────────────────────────────────────────────────────────────────────
    const content = React.createElement('div', {
      id: 'rupeecrm-calculator-window',
      ref: calcRef,
      className: `casio-calc-window ${isFocused ? 'calc-active-focus' : 'calc-inactive-focus'}`,
      role: 'dialog',
      'aria-label': 'RupeeCRM Casio Calculator',
      tabIndex: -1,
      onClick: (e) => {
        // Clicking inside the calculator window (not on a button) restores focus to the input
        if (e.target.tagName !== 'BUTTON' && !e.target.closest('button')) {
          focusInput();
        }
      },
      style: {
        position: 'fixed',
        left: `${pos.x}px`,
        top: `${pos.y}px`,
        width: '344px',
        zIndex: 99999,
        userSelect: 'none',
        outline: 'none',
        transition: isDragging ? 'none' : 'box-shadow 0.2s ease, transform 0.15s ease'
      }
    },
      // ── CASIO CALCULATOR BODY ──
      React.createElement('div', {
        className: 'casio-calc-casing',
        style: {
          background: 'linear-gradient(165deg, #2b303a 0%, #1e2229 60%, #14171c 100%)',
          borderRadius: 20,
          padding: '14px 16px 16px',
          boxShadow: isFocused
            ? '0 24px 48px -8px rgba(0, 0, 0, 0.75), 0 0 0 2px #6366f1, 0 0 16px rgba(99, 102, 241, 0.3), 0 2px 4px rgba(255, 255, 255, 0.15) inset'
            : '0 24px 48px -8px rgba(0, 0, 0, 0.75), 0 0 0 1px rgba(255, 255, 255, 0.1) inset, 0 2px 4px rgba(255, 255, 255, 0.15) inset, 0 10px 20px rgba(0,0,0,0.5)',
          border: isFocused ? '1px solid #6366f1' : '1px solid #111418',
          color: '#e2e8f0',
          fontFamily: 'Inter, system-ui, -apple-system, sans-serif',
          transition: 'box-shadow 0.2s ease, border-color 0.2s ease'
        }
      },
        // ── TOP HEADER / SOLAR BAR / BRAND ──
        React.createElement('div', {
          className: 'casio-top-bar',
          onMouseDown: startDrag,
          style: {
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            cursor: 'grab',
            marginBottom: 10,
            paddingBottom: 6,
            borderBottom: '1px solid rgba(255, 255, 255, 0.05)'
          }
        },
          // Brand & Model
          React.createElement('div', { style: { display: 'flex', flexDirection: 'column' } },
            React.createElement('div', {
              style: {
                fontSize: '0.82rem',
                fontWeight: 900,
                letterSpacing: '1.5px',
                color: '#cbd5e1',
                textTransform: 'uppercase',
                display: 'flex',
                alignItems: 'center',
                gap: 5
              }
            },
              React.createElement('span', { style: { color: '#6366f1' } }, 'Rupee'),
              'CRM',
              React.createElement('span', {
                style: {
                  fontSize: '0.6rem',
                  fontWeight: 700,
                  background: 'rgba(255,255,255,0.1)',
                  padding: '1px 4px',
                  borderRadius: 3,
                  letterSpacing: '0.5px',
                  color: '#94a3b8'
                }
              }, 'fx-82RC')
            ),
            React.createElement('span', {
              style: {
                fontSize: '0.53rem',
                color: '#64748b',
                letterSpacing: '0.8px',
                fontWeight: 600,
                textTransform: 'uppercase'
              }
            }, 'NATURAL-V.P.A.M. • TWO WAY POWER')
          ),

          // Solar Cell Strip
          React.createElement('div', {
            className: 'casio-solar-cell',
            title: 'RupeeCRM Photovoltaic Cell',
            style: {
              width: 66,
              height: 19,
              background: 'linear-gradient(180deg, #1c1511 0%, #3a2214 50%, #150f0c 100%)',
              border: '1px solid #000',
              borderRadius: 3,
              boxShadow: 'inset 0 1px 2px rgba(0,0,0,0.8), 0 1px 0 rgba(255,255,255,0.08)',
              display: 'grid',
              gridTemplateColumns: 'repeat(4, 1fr)',
              gap: 1,
              padding: 1
            }
          },
            [0, 1, 2, 3].map(i => React.createElement('div', {
              key: i,
              style: {
                background: 'linear-gradient(135deg, rgba(80,45,25,0.9), rgba(50,25,12,0.95))',
                borderRadius: 1
              }
            }))
          ),

          // Window Controls (Minimize / Collapse Keypad, Close)
          React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 6 } },
            React.createElement('button', {
              type: 'button',
              id: 'calc-minimize-btn',
              'aria-label': isMinimized ? 'Expand Keypad' : 'Collapse Keypad',
              title: isMinimized ? 'Expand Keypad (+)' : 'Collapse Keypad (−)',
              onClick: () => {
                setIsMinimized(prev => !prev);
                setTimeout(() => {
                  focusInput();
                }, 30);
              },
              style: {
                background: isMinimized ? 'rgba(99, 102, 241, 0.25)' : 'rgba(255,255,255,0.08)',
                border: isMinimized ? '1px solid #6366f1' : 'none',
                color: isMinimized ? '#a5b4fc' : '#94a3b8',
                borderRadius: 4,
                width: 22,
                height: 22,
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '0.85rem',
                fontWeight: 800,
                lineHeight: 1
              }
            }, isMinimized ? '+' : '−'),
            React.createElement('button', {
              type: 'button',
              id: 'calc-close-btn',
              'aria-label': 'Close Calculator (Esc)',
              title: 'Close Calculator (Esc)',
              onClick: onClose,
              style: {
                background: 'rgba(239, 68, 68, 0.2)',
                border: 'none',
                color: '#ef4444',
                borderRadius: 4,
                width: 22,
                height: 22,
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '0.85rem',
                fontWeight: 700
              }
            }, '✕')
          )
        ),

        // ── UNIFIED CASIO LCD DISPLAY & INTEGRATED QUERY INPUT (ALWAYS VISIBLE) ──
        React.createElement('div', {
          className: 'casio-lcd-bezel',
          style: {
            background: 'linear-gradient(180deg, #111417 0%, #1a1e24 100%)',
            borderRadius: 8,
            padding: 6,
            boxShadow: 'inset 0 3px 6px rgba(0,0,0,0.9), 0 1px 0 rgba(255,255,255,0.1)',
            border: '2px solid #2e3440',
            marginBottom: isMinimized ? 0 : 12
          }
        },
          React.createElement('div', {
            className: 'casio-lcd-screen',
            style: {
              background: '#8c9c7f', // Classic Casio greenish-grey LCD
              backgroundImage: 'radial-gradient(rgba(0,0,0,0.04) 15%, transparent 16%)',
              backgroundSize: '3px 3px',
              borderRadius: 5,
              padding: '6px 10px 6px',
              color: '#142111', // Dark LCD pigment
              fontFamily: 'monospace, "Courier New", Courier',
              boxShadow: 'inset 0 2px 4px rgba(0,0,0,0.4)',
              position: 'relative'
            }
          },
            // Micro-Indicators row (DEG, M, LIVE, COPY)
            React.createElement('div', {
              style: {
                display: 'flex',
                justifyContent: 'space-between',
                fontSize: '0.6rem',
                fontWeight: 800,
                opacity: 0.85,
                lineHeight: 1,
                marginBottom: 3,
                letterSpacing: '1px'
              }
            },
              React.createElement('div', { style: { display: 'flex', gap: 6 } },
                React.createElement('span', null, 'DEG'),
                React.createElement('span', { style: { opacity: memory !== 0 ? 1 : 0.2, fontWeight: 900 } }, 'M'),
                React.createElement('span', { style: { opacity: CalcEngine.isNlp(query) ? 1 : 0.4 } }, CalcEngine.isNlp(query) ? 'NLP' : 'MATH')
              ),
              React.createElement('div', {
                onClick: handleCopy,
                style: { fontSize: '0.58rem', opacity: copied ? 1 : 0.6, cursor: 'pointer' }
              },
                copied ? '✓ COPIED!' : 'CLICK TO COPY'
              )
            ),

            // Integrated Expression & NLP Query Input Field
            React.createElement('form', {
              onSubmit: handleSubmit,
              style: { margin: 0, padding: 0 }
            },
              React.createElement('input', {
                ref: inputRef,
                id: 'calc-unified-input',
                type: 'text',
                value: query,
                onChange: e => updateQueryLive(e.target.value),
                onFocus: () => setIsFocused(true),
                onBlur: (e) => {
                  if (!calcRef.current || !calcRef.current.contains(e.relatedTarget)) {
                    setIsFocused(false);
                  }
                },
                placeholder: 'Type math or NLP (e.g. 18% of 12500)...',
                'aria-label': 'Calculation formula or query',
                autoComplete: 'off',
                spellCheck: 'false',
                autoFocus: true,
                style: {
                  width: '100%',
                  background: 'transparent',
                  border: 'none',
                  outline: 'none',
                  color: '#142111',
                  fontFamily: 'monospace, "Courier New", Courier',
                  fontSize: '0.88rem',
                  fontWeight: 700,
                  textAlign: 'right',
                  padding: '2px 0',
                  margin: 0
                }
              })
            ),

            // Main Display / Live Result
            React.createElement('div', {
              id: 'calc-lcd-digits',
              onClick: handleCopy,
              title: 'Click to copy result',
              style: {
                fontSize: displayResult.length > 13 ? '1.35rem' : '1.75rem',
                fontWeight: 900,
                textAlign: 'right',
                letterSpacing: '0.5px',
                lineHeight: 1.15,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
                cursor: 'pointer',
                marginTop: 2
              }
            }, displayResult),

            // Optional Subtitle / Breakdown Details (GST CGST/SGST, Units, etc.)
            detailsText && React.createElement('div', {
              id: 'calc-details-line',
              style: {
                fontSize: '0.66rem',
                fontWeight: 700,
                opacity: 0.85,
                textAlign: 'right',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
                marginTop: 3,
                borderTop: '1px dashed rgba(0,0,0,0.2)',
                paddingTop: 2
              }
            }, detailsText)
          )
        ),

        // ── CASIO PHYSICAL KEYPAD ──
        !isMinimized && React.createElement('div', {
          className: 'casio-keypad-grid',
          style: {
            display: 'flex',
            flexDirection: 'column',
            gap: 6
          }
        },
          // Row 1: Memory & Tape
          React.createElement('div', { style: { display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 5 } },
            React.createElement(CasioKey, { id: 'calc-btn-mc', label: 'MC', sub: 'CLEAR', type: 'mem', onClick: handleMemoryClear }),
            React.createElement(CasioKey, { id: 'calc-btn-mr', label: 'MR', sub: 'RECALL', type: 'mem', onClick: handleMemoryRecall }),
            React.createElement(CasioKey, { id: 'calc-btn-mminus', label: 'M-', sub: 'SUB', type: 'mem', onClick: handleMemorySub }),
            React.createElement(CasioKey, { id: 'calc-btn-mplus', label: 'M+', sub: 'ADD', type: 'mem', onClick: handleMemoryAdd }),
            React.createElement(CasioKey, { id: 'calc-btn-hist', label: 'HIST', sub: 'TAPE', type: 'fn', active: showHistory, onClick: () => setShowHistory(p => !p) })
          ),

          // Row 2: Functions & Clear
          React.createElement('div', { style: { display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 5 } },
            React.createElement(CasioKey, { id: 'calc-btn-lparen', label: '(', type: 'fn', onClick: () => appendToQuery('(') }),
            React.createElement(CasioKey, { id: 'calc-btn-rparen', label: ')', type: 'fn', onClick: () => appendToQuery(')') }),
            React.createElement(CasioKey, { id: 'calc-btn-sqr', label: 'x²', type: 'fn', onClick: handleSquare }),
            React.createElement(CasioKey, { id: 'calc-btn-sqrt', label: '√', type: 'fn', onClick: handleSqrt }),
            React.createElement(CasioKey, { id: 'calc-btn-del', label: 'DEL', sub: 'INS', type: 'del', onClick: handleSmartDelete })
          ),

          // Row 3: 7 8 9 ÷ AC
          React.createElement('div', { style: { display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 5 } },
            React.createElement(CasioKey, { id: 'calc-btn-7', label: '7', type: 'num', onClick: () => handleDigit('7') }),
            React.createElement(CasioKey, { id: 'calc-btn-8', label: '8', type: 'num', onClick: () => handleDigit('8') }),
            React.createElement(CasioKey, { id: 'calc-btn-9', label: '9', type: 'num', onClick: () => handleDigit('9') }),
            React.createElement(CasioKey, { id: 'calc-btn-div', label: '÷', type: 'op', onClick: () => handleOperator('/') }),
            React.createElement(CasioKey, { id: 'calc-btn-ac', label: 'AC', sub: 'OFF', type: 'ac', onClick: handleClearAll })
          ),

          // Row 4: 4 5 6 × %
          React.createElement('div', { style: { display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 5 } },
            React.createElement(CasioKey, { id: 'calc-btn-4', label: '4', type: 'num', onClick: () => handleDigit('4') }),
            React.createElement(CasioKey, { id: 'calc-btn-5', label: '5', type: 'num', onClick: () => handleDigit('5') }),
            React.createElement(CasioKey, { id: 'calc-btn-6', label: '6', type: 'num', onClick: () => handleDigit('6') }),
            React.createElement(CasioKey, { id: 'calc-btn-mul', label: '×', type: 'op', onClick: () => handleOperator('*') }),
            React.createElement(CasioKey, { id: 'calc-btn-percent', label: '%', type: 'op', onClick: handlePercent })
          ),

          // Row 5: 1 2 3 - +/-
          React.createElement('div', { style: { display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 5 } },
            React.createElement(CasioKey, { id: 'calc-btn-1', label: '1', type: 'num', onClick: () => handleDigit('1') }),
            React.createElement(CasioKey, { id: 'calc-btn-2', label: '2', type: 'num', onClick: () => handleDigit('2') }),
            React.createElement(CasioKey, { id: 'calc-btn-3', label: '3', type: 'num', onClick: () => handleDigit('3') }),
            React.createElement(CasioKey, { id: 'calc-btn-sub', label: '-', type: 'op', onClick: () => handleOperator('-') }),
            React.createElement(CasioKey, { id: 'calc-btn-neg', label: '+/-', type: 'fn', onClick: handleNegate })
          ),

          // Row 6: 0 . 00 + =
          React.createElement('div', { style: { display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 5 } },
            React.createElement(CasioKey, { id: 'calc-btn-0', label: '0', type: 'num', onClick: () => handleDigit('0') }),
            React.createElement(CasioKey, { id: 'calc-btn-dot', label: '.', type: 'num', onClick: () => handleDigit('.') }),
            React.createElement(CasioKey, { id: 'calc-btn-00', label: '00', type: 'num', onClick: () => handleDigit('00') }),
            React.createElement(CasioKey, { id: 'calc-btn-add', label: '+', type: 'op', onClick: () => handleOperator('+') }),
            React.createElement(CasioKey, { id: 'calc-btn-eq', label: '=', type: 'eq', onClick: () => evaluateLive(query, true) })
          )
        ),

        // ── HISTORY TAPE DRAWER ──
        (!isMinimized && showHistory) && React.createElement('div', {
          id: 'calc-history-drawer',
          style: {
            marginTop: 10,
            background: '#16191f',
            borderRadius: 8,
            border: '1px solid #334155',
            padding: '10px 12px',
            maxHeight: 140,
            overflowY: 'auto'
          }
        },
          React.createElement('div', {
            style: {
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginBottom: 6,
              borderBottom: '1px solid rgba(255,255,255,0.06)',
              paddingBottom: 4
            }
          },
            React.createElement('span', { style: { fontSize: '0.72rem', fontWeight: 800, color: '#94a3b8' } }, '📜 CALCULATION TAPE'),
            React.createElement('button', {
              type: 'button',
              id: 'calc-clear-history-btn',
              onClick: () => setHistory([]),
              style: {
                background: 'none',
                border: 'none',
                color: '#ef4444',
                fontSize: '0.68rem',
                cursor: 'pointer',
                fontWeight: 700
              }
            }, 'Clear Tape')
          ),
          history.length === 0 ? React.createElement('div', {
            style: { fontSize: '0.75rem', color: '#64748b', textAlign: 'center', padding: '8px 0' }
          }, 'No previous calculations') :
          history.map(item => React.createElement('div', {
            key: item.id,
            onClick: () => {
              setQuery(item.expr);
              setDisplayResult(item.result);
              if (inputRef.current) inputRef.current.focus();
            },
            title: 'Click to reload into calculator',
            style: {
              display: 'flex',
              justifyContent: 'space-between',
              padding: '4px 6px',
              borderRadius: 4,
              cursor: 'pointer',
              fontSize: '0.78rem',
              color: '#cbd5e1',
              fontFamily: 'monospace'
            }
          },
            React.createElement('span', { style: { opacity: 0.8 } }, item.expr),
            React.createElement('span', { style: { fontWeight: 700, color: '#6ee7b7' } }, `= ${item.result}`)
          ))
        )
      )
    );

    return ReactDOM.createPortal(content, document.body);
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // 3. CASIO PHYSICAL 3D BUTTON COMPONENT
  // ─────────────────────────────────────────────────────────────────────────────
  function CasioKey({ id, label, sub, type, active, onClick }) {
    const getStyle = () => {
      let bg = 'linear-gradient(180deg, #374151 0%, #1f2937 100%)';
      let color = '#f3f4f6';
      let shadowColor = '#111827';
      let border = '1px solid rgba(255,255,255,0.08)';

      if (type === 'ac') {
        bg = 'linear-gradient(180deg, #ea580c 0%, #c2410c 100%)';
        color = '#ffffff';
        shadowColor = '#7c2d12';
        border = '1px solid #f97316';
      } else if (type === 'del') {
        bg = 'linear-gradient(180deg, #dc2626 0%, #b91c1c 100%)';
        color = '#ffffff';
        shadowColor = '#7f1d1d';
        border = '1px solid #ef4444';
      } else if (type === 'num') {
        bg = 'linear-gradient(180deg, #4b5563 0%, #374151 100%)';
        color = '#ffffff';
        shadowColor = '#1e2530';
      } else if (type === 'op' || type === 'eq') {
        bg = type === 'eq' ? 'linear-gradient(180deg, #4f46e5 0%, #3730a3 100%)' : 'linear-gradient(180deg, #1e293b 0%, #0f172a 100%)';
        color = type === 'eq' ? '#ffffff' : '#38bdf8';
        shadowColor = '#090d16';
      } else if (type === 'mem' || type === 'fn') {
        bg = active ? 'linear-gradient(180deg, #6366f1 0%, #4338ca 100%)' : 'linear-gradient(180deg, #242b35 0%, #181d24 100%)';
        color = active ? '#ffffff' : '#94a3b8';
        shadowColor = '#0e1217';
      }

      return {
        background: bg,
        color,
        border,
        borderRadius: 6,
        padding: '8px 2px 6px',
        minHeight: 34,
        cursor: 'pointer',
        boxShadow: `0 3px 0 ${shadowColor}, 0 4px 6px rgba(0,0,0,0.4), inset 0 1px 0 rgba(255,255,255,0.15)`,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        position: 'relative',
        transform: 'translateY(0)',
        transition: 'transform 0.05s ease, box-shadow 0.05s ease',
        userSelect: 'none'
      };
    };

    return React.createElement('button', {
      type: 'button',
      id,
      'aria-label': label,
      className: `casio-btn casio-btn-${type}`,
      style: getStyle(),
      onClick,
      onMouseDown: (e) => {
        e.currentTarget.style.transform = 'translateY(2px)';
        e.currentTarget.style.boxShadow = '0 1px 0 rgba(0,0,0,0.5), inset 0 1px 2px rgba(0,0,0,0.4)';
      },
      onMouseUp: (e) => {
        e.currentTarget.style.transform = 'translateY(0)';
        e.currentTarget.style.boxShadow = getStyle().boxShadow;
      },
      onMouseLeave: (e) => {
        e.currentTarget.style.transform = 'translateY(0)';
        e.currentTarget.style.boxShadow = getStyle().boxShadow;
      }
    },
      sub && React.createElement('span', {
        style: {
          fontSize: '0.52rem',
          fontWeight: 800,
          color: type === 'ac' || type === 'del' ? 'rgba(255,255,255,0.7)' : '#f59e0b',
          lineHeight: 1,
          marginBottom: 1,
          letterSpacing: '0.5px'
        }
      }, sub),
      React.createElement('span', {
        style: {
          fontSize: label.length > 2 ? '0.82rem' : '0.96rem',
          fontWeight: 800,
          lineHeight: 1
        }
      }, label)
    );
  }

  return RupeeCRMCalculator;
}));
