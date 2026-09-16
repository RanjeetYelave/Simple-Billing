package com.billing.simple.billsoft.assistant.nlu;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic BigDecimal Arithmetic Semantic Parser.
 * Zero dynamic code execution (no eval(), no reflection).
 * Handles natural English, Hindi, Marathi phrasing, percentages, GST, discounts, markups,
 * double/triple/half/quarter, powers, square roots, Indian units (lakh, crore), and precedence.
 */
@Component
public class OmnisearchMathEngine {

    private static final Pattern MATH_KEYWORD_PATTERN = Pattern.compile("(?iu)(?:^|\\s|[^a-zA-Z0-9\\u0900-\\u097F])(calculate|math|what\\s+is|add|sum\\s+of|plus|subtract|difference\\s+between|minus|less|reduce|reduced\\s+by|times|multiply|multiplied\\s+by|product\\s+of|divide|divided\\s+by|remainder|modulo|percent|percentage|gst|tax|discount|markup|marked\\s+up|increase|increased|double|doubled|twice|triple|tripled|half|quarter|one\\s+fourth|square|squared|cube|cubed|power|root|square\\s+root|sqrt|lakh|crore|दुप्पट|निम्मे)(?:$|\\s|[^a-zA-Z0-9\\u0900-\\u097F])");

    public boolean isArithmeticExpression(String text) {
        if (text == null || text.isBlank()) return false;
        String trimmed = text.trim();

        // If contains percentage sign (%) with numbers: e.g. "17% of 5633", "10% of 7900", "7900 + 10%"
        if (trimmed.contains("%") && trimmed.matches(".*\\d+.*")) {
            return true;
        }

        // If contains math keywords or symbols
        if (MATH_KEYWORD_PATTERN.matcher(trimmed).find()) {
            // Must also contain at least one digit or unit
            return trimmed.matches(".*\\d+.*") || trimmed.toLowerCase().matches(".*(lakh|crore).*");
        }

        // Pure arithmetic symbols with digits: e.g. "10 + 20", "5 * 2", "(10 + 5) * 2", "100 / 4"
        String mathCharsOnly = trimmed.replaceAll("(?i)(lakh|crore|k\\b|rs|inr|₹|\\s|of|and|from|to|by)", "");
        return mathCharsOnly.matches("^[0-9+\\-*/%^().]+$") && mathCharsOnly.matches(".*[+\\-*/%^].*");
    }

    public BigDecimal evaluate(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Empty math expression");
        }

        String normalized = canonicalizeToMathExpression(expression);
        return new Parser(normalized).parse();
    }

    /**
     * Translates natural language mathematical phrasing into canonical arithmetic expressions for the AST parser.
     */
    public String canonicalizeToMathExpression(String input) {
        if (input == null || input.isBlank()) return "";
        String s = input.trim();

        // 0. Remove query prefixes like "what is", "calculate", "tell me", "kitna", "hai"
        s = s.replaceAll("(?i)^(?:what\\s+is|calculate|tell\\s+me|show\\s+me|find|how\\s+much\\s+is|what\\s+are)\\s+", "");
        s = s.replaceAll("(?i)\\s+(?:kitna|kitni|hai|kiti|ahe|batao|sang|please)$", "");

        // Word numbers normalization
        s = s.replaceAll("(?i)\\bten\\b", "10")
             .replaceAll("(?i)\\btwenty\\b", "20")
             .replaceAll("(?i)\\bfifteen\\b", "15")
             .replaceAll("(?i)\\beighteen\\b", "18")
             .replaceAll("(?i)\\bfive\\b", "5")
             .replaceAll("(?i)\\btwo\\b", "2")
             .replaceAll("(?i)\\bthree\\b", "3")
             .replaceAll("(?i)\\bfour\\b", "4");

        // Indian units and currency symbols
        s = s.replaceAll("(?i)[₹,]|\\brs\\.?\\b|\\binr\\b", " ");
        s = s.replaceAll("(?i)(\\d+(?:\\.\\d+)?)\\s*lakh(?:s)?\\b", "($1 * 100000)");
        s = s.replaceAll("(?i)(\\d+(?:\\.\\d+)?)\\s*crore(?:s)?\\b", "($1 * 10000000)");
        s = s.replaceAll("(?i)(\\d+(?:\\.\\d+)?)\\s*k\\b", "($1 * 1000)");

        // 1. GST & Tax calculations (Must run first before general plus/minus):
        s = s.replaceAll("(?i)\\bgst\\s+on\\s*(\\d+(?:\\.\\d+)?)\\s*(?:at|@)?\\s*(\\d+(?:\\.\\d+)?)\\s*(?:%|percent)?", "($1 * ($2 / 100))");
        s = s.replaceAll("(?i)\\b(\\d+(?:\\.\\d+)?)\\s*(?:%|percent)\\s*gst\\s+on\\s*(\\d+(?:\\.\\d+)?)\\b", "($2 * ($1 / 100))");
        s = s.replaceAll("(?i)\\b(?:remove|excluding|without)\\s*(\\d+(?:\\.\\d+)?)\\s*(?:%|percent)?\\s*gst\\s*(?:from)?\\s*(\\d+(?:\\.\\d+)?)\\b", "($2 / (1 + ($1 / 100)))");
        s = s.replaceAll("(?i)\\b(\\d+(?:\\.\\d+)?)\\s*(?:excluding|without|remove)\\s*(\\d+(?:\\.\\d+)?)\\s*(?:%|percent)?\\s*gst\\b", "($1 / (1 + ($2 / 100)))");
        s = s.replaceAll("(?i)\\b(?:add\\s+)?(\\d+(?:\\.\\d+)?)\\s*(?:%|percent)\\s*gst\\s*(?:on|to)\\s*(\\d+(?:\\.\\d+)?)\\b", "($2 + ($2 * $1 / 100))");
        s = s.replaceAll("(?i)\\b(\\d+(?:\\.\\d+)?)\\s*(?:\\+|plus|with)\\s*(\\d+(?:\\.\\d+)?)\\s*(?:%|percent)?\\s*(?:gst|tax)\\b", "($1 + ($1 * $2 / 100))");

        // 2. Discount & Reductions:
        s = s.replaceAll("(?i)\\b(?:discount\\s+)?(\\d+(?:\\.\\d+)?)\\s*(?:%|percent)\\s*discount\\s*on\\s*(\\d+(?:\\.\\d+)?)\\b", "($2 - ($2 * $1 / 100))");
        s = s.replaceAll("(?i)\\bdiscount\\s*(\\d+(?:\\.\\d+)?)\\s*(?:%|percent)\\s*on\\s*(\\d+(?:\\.\\d+)?)\\b", "($2 - ($2 * $1 / 100))");
        s = s.replaceAll("(?i)\\breduce\\s*(\\d+(?:\\.\\d+)?)\\s*by\\s*(\\d+(?:\\.\\d+)?)\\s*(?:%|percent)", "($1 - ($1 * $2 / 100))");
        s = s.replaceAll("(?i)\\breduce\\s*(\\d+(?:\\.\\d+)?)\\s*by\\s*(\\d+(?:\\.\\d+)?)\\b", "($1 - $2)");
        s = s.replaceAll("(?i)\\b(\\d+(?:\\.\\d+)?)\\s*(?:less|minus|reduced\\s+by|after|-)\\s*(\\d+(?:\\.\\d+)?)\\s*(?:%|percent)\\s*discount\\b", "($1 - ($1 * $2 / 100))");
        s = s.replaceAll("(?i)\\b(\\d+(?:\\.\\d+)?)\\s*(?:less|minus|reduced\\s+by|after)\\s*(\\d+(?:\\.\\d+)?)\\s*(?:%|percent)(?:\\s*discount)?", "($1 - ($1 * $2 / 100))");

        // 3. Markup & Increases:
        s = s.replaceAll("(?i)\\b(?:add\\s+)?(\\d+(?:\\.\\d+)?)\\s*(?:%|percent)\\s*(?:markup\\s+)?to\\s*(\\d+(?:\\.\\d+)?)\\b", "($2 + ($2 * $1 / 100))");
        s = s.replaceAll("(?i)\\b(?:increase|marked\\s+up)\\s*(?:cost\\s+by\\s*)?(\\d+(?:\\.\\d+)?)\\s*(?:%|percent)?\\s*by\\s*(\\d+(?:\\.\\d+)?)\\s*(?:%|percent)", "($1 + ($1 * $2 / 100))");
        s = s.replaceAll("(?i)\\b(\\d+(?:\\.\\d+)?)\\s*marked\\s+up\\s+by\\s*(\\d+(?:\\.\\d+)?)\\s*(?:%|percent)?", "($1 + ($1 * $2 / 100))");
        s = s.replaceAll("(?i)\\bincrease\\s*(\\d+(?:\\.\\d+)?)\\s*by\\s*(\\d+(?:\\.\\d+)?)\\s*(?:%|percent)", "($1 + ($1 * $2 / 100))");
        s = s.replaceAll("(?i)\\b(\\d+(?:\\.\\d+)?)\\s*(?:plus|increased\\s+by)\\s*(\\d+(?:\\.\\d+)?)\\s*(?:%|percent)", "($1 + ($1 * $2 / 100))");

        // 4. Direct trailing percent additions/subtractions: e.g. "7900 + 10%" or "7900 - 10%"
        s = s.replaceAll("(?i)\\b(\\d+(?:\\.\\d+)?)\\s*\\+\\s*(\\d+(?:\\.\\d+)?)\\s*(?:%|percent)", "($1 + ($1 * $2 / 100))");
        s = s.replaceAll("(?i)\\b(\\d+(?:\\.\\d+)?)\\s*-\\s*(\\d+(?:\\.\\d+)?)\\s*(?:%|percent)", "($1 - ($1 * $2 / 100))");

        // 4. Percentage of: "17% of 5633", "17 percent of 5633", "5633 ka 17 percent", "5633 cha 17%"
        s = s.replaceAll("(?i)\\b(\\d+(?:\\.\\d+)?)\\s*(?:%|percent)\\s*(?:of|ka|cha|che|var|madhun)\\s*(\\d+(?:\\.\\d+)?)\\b", "($2 * ($1 / 100))");
        s = s.replaceAll("(?i)\\b(\\d+(?:\\.\\d+)?)\\s*(?:ka|cha|che|var|madhun)\\s*(\\d+(?:\\.\\d+)?)\\s*(?:%|percent)", "($1 * ($2 / 100))");

        // 5. Roots: "square root of 144", "sqrt 144", "root of 144", "144 ka square root"
        s = s.replaceAll("(?i)\\b(?:square\\s+root\\s+of|sqrt|root\\s+of)\\s*(\\d+(?:\\.\\d+)?)\\b", "sqrt($1)");
        s = s.replaceAll("(?i)\\b(\\d+(?:\\.\\d+)?)\\s*(?:ka|cha)?\\s*(?:square\\s+root|sqrt|root)\\b", "sqrt($1)");

        // 6. Powers: "5 squared", "square of 5", "5 power 2", "5 to the power of 2", "3 cubed", "cube of 3"
        s = s.replaceAll("(?i)\\bsquare\\s+of\\s*(\\d+(?:\\.\\d+)?)\\b", "($1 ^ 2)");
        s = s.replaceAll("(?i)\\b(\\d+(?:\\.\\d+)?)\\s*squared?\\b", "($1 ^ 2)");
        s = s.replaceAll("(?i)\\bcube\\s+of\\s*(\\d+(?:\\.\\d+)?)\\b", "($1 ^ 3)");
        s = s.replaceAll("(?i)\\b(\\d+(?:\\.\\d+)?)\\s*cubed?\\b", "($1 ^ 3)");
        s = s.replaceAll("(?i)\\b(\\d+(?:\\.\\d+)?)\\s*(?:to\\s+the\\s+power\\s+of|power)\\s*(\\d+(?:\\.\\d+)?)\\b", "($1 ^ $2)");

        // 7. Multipliers & Fractions:
        // double / twice / duppat / दुप्पट
        s = s.replaceAll("(?iu)(?:double\\s+of|double|twice|two\\s+times)\\s*(\\d+(?:\\.\\d+)?)", "($1 * 2)");
        s = s.replaceAll("(?iu)(\\d+(?:\\.\\d+)?)\\s*(?:ka|cha|chi)?\\s*(?:doubled|double|duppat|दुप्पट)", "($1 * 2)");
        s = s.replaceAll("(?iu)increase\\s+(\\d+(?:\\.\\d+)?)\\s+to\\s+double\\b", "($1 * 2)");

        // triple / three times / trippat
        s = s.replaceAll("(?iu)(?:triple\\s+of|triple|three\\s+times)\\s*(\\d+(?:\\.\\d+)?)", "($1 * 3)");
        s = s.replaceAll("(?iu)(\\d+(?:\\.\\d+)?)\\s*(?:ka|cha|chi)?\\s*(?:tripled|triple|trippat)", "($1 * 3)");

        // half / nimme / निम्मे
        s = s.replaceAll("(?iu)(?:half\\s+of|half)\\s*(\\d+(?:\\.\\d+)?)", "($1 * 0.5)");
        s = s.replaceAll("(?iu)(\\d+(?:\\.\\d+)?)\\s*(?:ka|cha|chi)?\\s*(?:half|nimme|निम्मे)", "($1 * 0.5)");

        // quarter / one fourth
        s = s.replaceAll("(?iu)(?:quarter\\s+of|quarter|one\\s+fourth\\s+of)\\s*(\\d+(?:\\.\\d+)?)", "($1 / 4)");
        s = s.replaceAll("(?iu)(\\d+(?:\\.\\d+)?)\\s*(?:ka|cha|chi)?\\s*quarter", "($1 / 4)");
        s = s.replaceAll("(?i)\\b(?:add|sum\\s+of)\\s*(\\d+(?:\\.\\d+)?)\\s*(?:and|to)\\s*(\\d+(?:\\.\\d+)?)\\b", "($1 + $2)");
        s = s.replaceAll("(?i)\\b(?:subtract)\\s*(\\d+(?:\\.\\d+)?)\\s*from\\s*(\\d+(?:\\.\\d+)?)\\b", "($2 - $1)");
        s = s.replaceAll("(?i)\\b(?:difference\\s+between)\\s*(\\d+(?:\\.\\d+)?)\\s*and\\s*(\\d+(?:\\.\\d+)?)\\b", "($1 - $2)");
        s = s.replaceAll("(?i)\\b(?:multiply|product\\s+of)\\s*(\\d+(?:\\.\\d+)?)\\s*(?:by|and)\\s*(\\d+(?:\\.\\d+)?)\\b", "($1 * $2)");
        s = s.replaceAll("(?i)\\b(?:divide)\\s*(\\d+(?:\\.\\d+)?)\\s*by\\s*(\\d+(?:\\.\\d+)?)\\b", "($1 / $2)");

        // 9. Modulo / Remainder:
        s = s.replaceAll("(?i)\\bremainder\\s+when\\s*(\\d+(?:\\.\\d+)?)\\s*is\\s+divided\\s+by\\s*(\\d+(?:\\.\\d+)?)\\b", "($1 % $2)");
        s = s.replaceAll("(?i)\\b(\\d+(?:\\.\\d+)?)\\s*(?:remainder|modulo|mod)\\s*(\\d+(?:\\.\\d+)?)\\b", "($1 % $2)");

        // 10. Word operators
        s = s.replaceAll("(?i)\\b(?:plus|increased\\s+by)\\b", "+");
        s = s.replaceAll("(?i)\\b(?:minus|less|reduced\\s+by)\\b", "-");
        s = s.replaceAll("(?i)\\b(?:times|multiplied\\s+by)\\b", "*");
        s = s.replaceAll("(?i)\\b(?:divided\\s+by)\\b", "/");

        // Remove extra transition words
        s = s.replaceAll("(?i)\\b(take|then|and)\\b", " ");

        return s.trim();
    }

    private static class Parser {
        private final String str;
        private int pos = -1, ch;

        public Parser(String str) {
            this.str = str;
            nextChar();
        }

        private void nextChar() {
            ch = (++pos < str.length()) ? str.charAt(pos) : -1;
        }

        private boolean eat(int charToEat) {
            while (ch == ' ') nextChar();
            if (ch == charToEat) {
                nextChar();
                return true;
            }
            return false;
        }

        public BigDecimal parse() {
            BigDecimal x = parseExpression();
            if (pos < str.length()) throw new IllegalArgumentException("Unexpected char: " + (char) ch + " in expr: " + str);
            return x.setScale(2, RoundingMode.HALF_EVEN);
        }

        private BigDecimal parseExpression() {
            BigDecimal x = parseTerm();
            while (true) {
                if (eat('+')) x = x.add(parseTerm());
                else if (eat('-')) x = x.subtract(parseTerm());
                else return x;
            }
        }

        private BigDecimal parseTerm() {
            BigDecimal x = parseFactor();
            while (true) {
                if (eat('*')) x = x.multiply(parseFactor());
                else if (eat('/')) {
                    BigDecimal divisor = parseFactor();
                    if (divisor.compareTo(BigDecimal.ZERO) == 0) throw new ArithmeticException("Division by zero");
                    x = x.divide(divisor, 6, RoundingMode.HALF_EVEN);
                } else if (eat('%')) {
                    BigDecimal divisor = parseFactor();
                    x = x.remainder(divisor);
                } else return x;
            }
        }

        private BigDecimal parseFactor() {
            if (eat('+')) return parseFactor();
            if (eat('-')) return parseFactor().negate();

            BigDecimal x;
            int startPos = this.pos;

            if (eat('(')) {
                x = parseExpression();
                eat(')');
            } else if (str.startsWith("sqrt(", startPos)) {
                pos = startPos + 4; // after "sqrt"
                nextChar();
                eat('(');
                BigDecimal inner = parseExpression();
                eat(')');
                x = BigDecimal.valueOf(Math.sqrt(inner.doubleValue()));
            } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                x = new BigDecimal(str.substring(startPos, this.pos));
            } else {
                throw new IllegalArgumentException("Unexpected token at pos " + pos + ": " + (char) ch + " in expr: " + str);
            }

            if (eat('^')) {
                BigDecimal exponent = parseFactor();
                x = BigDecimal.valueOf(Math.pow(x.doubleValue(), exponent.doubleValue()));
            }

            return x;
        }
    }
}
