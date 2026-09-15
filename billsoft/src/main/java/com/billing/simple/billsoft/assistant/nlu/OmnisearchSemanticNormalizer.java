package com.billing.simple.billsoft.assistant.nlu;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Semantic Concept Normalizer for OmniSearch.
 * Transforms broad natural phrasing, multilingual particles, grammatical variations,
 * and common typos into canonical semantic tokens (e.g. _CONCEPT_OUTSTANDING_, _CONCEPT_SALARY_).
 */
@Component
public class OmnisearchSemanticNormalizer {

    // Common typo replacements to canonical words
    private static final Map<String, String> TYPO_DICTIONARY = Map.ofEntries(
            Map.entry("balnce", "balance"),
            Map.entry("balane", "balance"),
            Map.entry("outstading", "outstanding"),
            Map.entry("outstandng", "outstanding"),
            Map.entry("otstanding", "outstanding"),
            Map.entry("udhari", "udhari"),
            Map.entry("udari", "udhari"),
            Map.entry("udhary", "udhari"),
            Map.entry("baaki", "baki"),
            Map.entry("bakki", "baki"),
            Map.entry("salry", "salary"),
            Map.entry("sallary", "salary"),
            Map.entry("slary", "salary"),
            Map.entry("attendnce", "attendance"),
            Map.entry("attendence", "attendance"),
            Map.entry("atendance", "attendance"),
            Map.entry("invoce", "invoice"),
            Map.entry("invoise", "invoice"),
            Map.entry("invice", "invoice"),
            Map.entry("expence", "expense"),
            Map.entry("expencs", "expense"),
            Map.entry("kharch", "kharcha"),
            Map.entry("kharchaa", "kharcha"),
            Map.entry("recieve", "receive"),
            Map.entry("recived", "receive"),
            Map.entry("reciept", "receipt"),
            Map.entry("stok", "stock"),
            Map.entry("stockk", "stock"),
            Map.entry("inventry", "inventory"),
            Map.entry("gol", "goal"),
            Map.entry("gaol", "goal"),
            Map.entry("gool", "goal"),
            Map.entry("savng", "saving"),
            Map.entry("savngs", "savings"),
            Map.entry("invst", "invest"),
            Map.entry("bachatt", "bachat"),
            Map.entry("bckup", "backup"),
            Map.entry("bakup", "backup"),
            Map.entry("backp", "backup"),
            Map.entry("dashbord", "dashboard"),
            Map.entry("persnal", "personal"),
            Map.entry("remindr", "reminder"),
            Map.entry("todoo", "todo")
    );

    // Concept Matcher Regexes (Unicode-aware for Indic scripts: Hindi & Marathi)
    private static final Pattern OUTSTANDING_PATTERN = Pattern.compile("(?iu)(?:^|\\s|[^a-zA-Z0-9\\u0900-\\u097F])(balance|outstanding|dues?|pending(?:\\s+amount)?|amount\\s+due|remaining\\s+amount|baki|baaki|udhari|udhar|owe(?:s)?|aana\\s+baki|len[ae]\\s+hai(?:n)?|lena|lene|ghaych[ey]|receivable|बकाया|उधारी|बाकी|बॅलन्स|बाकीची)(?:$|\\s|[^a-zA-Z0-9\\u0900-\\u097F])");
    private static final Pattern SALARY_PATTERN = Pattern.compile("(?iu)(?:^|\\s|[^a-zA-Z0-9\\u0900-\\u097F])(salary|salaries|pay|wages?|compensation|net\\s+salary|salary\\s+statement|payroll|pagar|tankhwah|earn(?:s)?|earning(?:s)?|kamata|milte|वेतन|पगार|सैलरी|सॅलरी|पगारा?)(?:$|\\s|[^a-zA-Z0-9\\u0900-\\u097F])");
    private static final Pattern ATTENDANCE_PATTERN = Pattern.compile("(?iu)(?:^|\\s|[^a-zA-Z0-9\\u0900-\\u097F])(attendance|hajiri|present|absent|working\\s+days|हजेरी|हाजिरी|उपस्थित|गैरहजर)(?:$|\\s|[^a-zA-Z0-9\\u0900-\\u097F])");
    private static final Pattern ADVANCE_PATTERN = Pattern.compile("(?iu)(?:^|\\s|[^a-zA-Z0-9\\u0900-\\u097F])(advance|advance\\s+balance|uchal|उचल|ॲडव्हान्स|एडवांस)(?:$|\\s|[^a-zA-Z0-9\\u0900-\\u097F])");
    private static final Pattern EXPENSE_PATTERN = Pattern.compile("(?iu)(?:^|\\s|[^a-zA-Z0-9\\u0900-\\u097F])(expenses?|spending|kharcha|kharch|खर्च|खर्चा)(?:$|\\s|[^a-zA-Z0-9\\u0900-\\u097F])");
    private static final Pattern STOCK_PATTERN = Pattern.compile("(?iu)(?:^|\\s|[^a-zA-Z0-9\\u0900-\\u097F])(stock|stocks|inventory|quantit(?:y|ies)|available\\s+qty|available\\s+quantity|in\\s+stock|maal|satha|saatha|shillak|साठा|शिल्लक)(?:$|\\s|[^a-zA-Z0-9\\u0900-\\u097F])");
    private static final Pattern INVOICE_PATTERN = Pattern.compile("(?iu)(?:^|\\s|[^a-zA-Z0-9\\u0900-\\u097F])(invoices?|bills?|sales\\s+bill|बिल|इनव्हॉइस)(?:$|\\s|[^a-zA-Z0-9\\u0900-\\u097F])");
    private static final Pattern SALES_PATTERN = Pattern.compile("(?iu)(?:^|\\s|[^a-zA-Z0-9\\u0900-\\u097F])(sales?|turnover|revenue|bikri|dhandha|galla|vikri|विक्री|गल्ला|सेल)(?:$|\\s|[^a-zA-Z0-9\\u0900-\\u097F])");
    private static final Pattern PROFILE_PATTERN = Pattern.compile("(?iu)(?:^|\\s|[^a-zA-Z0-9\\u0900-\\u097F])(profile|details|information|info|contact|phone|mobile|address|gstin|gst\\s+number|माहिती|तपशील|नंबर)(?:$|\\s|[^a-zA-Z0-9\\u0900-\\u097F])");
    private static final Pattern GOAL_PATTERN = Pattern.compile("(?iu)(?:^|\\s|[^a-zA-Z0-9\\u0900-\\u097F])(goals?|streaks?|habits?|milestones?|targets?|lakshya|dhyeya?|dhyey|aadat|लक्ष्य|ध्येय|सवय|सवयी|स्ट्रिक)(?:$|\\s|[^a-zA-Z0-9\\u0900-\\u097F])");
    private static final Pattern SAVING_PATTERN = Pattern.compile("(?iu)(?:^|\\s|[^a-zA-Z0-9\\u0900-\\u097F])(savings?|investments?|invest|sip|deposit|deposits|reserves?|bachat|guntavanuk|बचत|गुंतवणूक|डिपॉझिट)(?:$|\\s|[^a-zA-Z0-9\\u0900-\\u097F])");
    private static final Pattern BACKUP_PATTERN = Pattern.compile("(?iu)(?:^|\\s|[^a-zA-Z0-9\\u0900-\\u097F])(backups?|restore|snapshot|export\\s+data|save\\s+database|बॅकअप|बैकअप)(?:$|\\s|[^a-zA-Z0-9\\u0900-\\u097F])");
    private static final Pattern PLANNER_PATTERN = Pattern.compile("(?iu)(?:^|\\s|[^a-zA-Z0-9\\u0900-\\u097F])(planner|todos?|tasks?|kanban|reminders?|notes?|memos?|tippan|aathvan|नियोजन|टास्क|नोंद|टिप्पण)(?:$|\\s|[^a-zA-Z0-9\\u0900-\\u097F])");

    // Relational / Possessive particles to strip when isolating entity tokens
    private static final String PARTICLE_REGEX = "(?i)\\b('s|of|for|from|to|with|about|ka|ki|ke|ko|se|cha|chi|che|la|kadun|var|sathi|karita|ne|ni|batao|dikhao|dakhva|sang|kiti|ahe|kitna|kitni|hai|hain|show|give|tell|get|find|please|what\\s+is|what\\s+are|what|is|are|how\\s+much|how\\s+many|how|does|owe|us|we|have|pay|collect|earn|monthly|staff|record|create|make|new|naya|nayi|banao|kara|add|spent|spend|likho)\\b";

    public static class NormalizedQuery {
        private final String rawQuery;
        private final String canonicalString;
        private final Set<String> recognizedConcepts;
        private final String isolatedEntityToken;

        public NormalizedQuery(String rawQuery, String canonicalString, Set<String> recognizedConcepts, String isolatedEntityToken) {
            this.rawQuery = rawQuery;
            this.canonicalString = canonicalString;
            this.recognizedConcepts = recognizedConcepts;
            this.isolatedEntityToken = isolatedEntityToken;
        }

        public String getRawQuery() { return rawQuery; }
        public String getCanonicalString() { return canonicalString; }
        public Set<String> getRecognizedConcepts() { return recognizedConcepts; }
        public String getIsolatedEntityToken() { return isolatedEntityToken; }
    }

    public NormalizedQuery normalize(String query) {
        if (query == null || query.isBlank()) {
            return new NormalizedQuery("", "", Collections.emptySet(), "");
        }

        String raw = query.trim();

        // 1. Correct Common Typos
        String[] tokens = raw.split("\\s+");
        StringBuilder typoCorrected = new StringBuilder();
        for (String t : tokens) {
            String cleanT = t.replaceAll("[^a-zA-Z0-9\\u0900-\\u097F]", "").toLowerCase();
            String repl = TYPO_DICTIONARY.getOrDefault(cleanT, t);
            typoCorrected.append(repl).append(" ");
        }
        String working = typoCorrected.toString().trim();

        // 2. Identify Semantic Concepts
        Set<String> concepts = new HashSet<>();
        if (OUTSTANDING_PATTERN.matcher(working).find()) concepts.add("CONCEPT_OUTSTANDING");
        if (SALARY_PATTERN.matcher(working).find()) concepts.add("CONCEPT_SALARY");
        if (ATTENDANCE_PATTERN.matcher(working).find()) concepts.add("CONCEPT_ATTENDANCE");
        if (ADVANCE_PATTERN.matcher(working).find()) concepts.add("CONCEPT_ADVANCE");
        if (EXPENSE_PATTERN.matcher(working).find()) concepts.add("CONCEPT_EXPENSE");
        if (STOCK_PATTERN.matcher(working).find()) concepts.add("CONCEPT_STOCK");
        if (INVOICE_PATTERN.matcher(working).find()) concepts.add("CONCEPT_INVOICE");
        if (SALES_PATTERN.matcher(working).find()) concepts.add("CONCEPT_SALES");
        if (PROFILE_PATTERN.matcher(working).find()) concepts.add("CONCEPT_PROFILE");
        if (GOAL_PATTERN.matcher(working).find()) concepts.add("CONCEPT_GOAL");
        if (SAVING_PATTERN.matcher(working).find()) concepts.add("CONCEPT_SAVING");
        if (BACKUP_PATTERN.matcher(working).find()) concepts.add("CONCEPT_BACKUP");
        if (PLANNER_PATTERN.matcher(working).find()) concepts.add("CONCEPT_PLANNER");

        // 3. Extract Isolated Entity Token by removing concept tokens and particles
        String entityCleaned = working;
        entityCleaned = OUTSTANDING_PATTERN.matcher(entityCleaned).replaceAll(" ");
        entityCleaned = SALARY_PATTERN.matcher(entityCleaned).replaceAll(" ");
        entityCleaned = ATTENDANCE_PATTERN.matcher(entityCleaned).replaceAll(" ");
        entityCleaned = ADVANCE_PATTERN.matcher(entityCleaned).replaceAll(" ");
        entityCleaned = EXPENSE_PATTERN.matcher(entityCleaned).replaceAll(" ");
        entityCleaned = STOCK_PATTERN.matcher(entityCleaned).replaceAll(" ");
        entityCleaned = INVOICE_PATTERN.matcher(entityCleaned).replaceAll(" ");
        entityCleaned = SALES_PATTERN.matcher(entityCleaned).replaceAll(" ");
        entityCleaned = PROFILE_PATTERN.matcher(entityCleaned).replaceAll(" ");
        entityCleaned = GOAL_PATTERN.matcher(entityCleaned).replaceAll(" ");
        entityCleaned = SAVING_PATTERN.matcher(entityCleaned).replaceAll(" ");
        entityCleaned = BACKUP_PATTERN.matcher(entityCleaned).replaceAll(" ");
        entityCleaned = PLANNER_PATTERN.matcher(entityCleaned).replaceAll(" ");

        // Remove particles and numbers
        entityCleaned = entityCleaned.replaceAll(PARTICLE_REGEX, " ");
        entityCleaned = entityCleaned.replaceAll("(?i)\\b(today|yesterday|this\\s+month|last\\s+month|aaj|kal|is\\s+mahine|pichle\\s+mahine)\\b", " ");
        entityCleaned = entityCleaned.replaceAll("[^a-zA-Z0-9\\u0900-\\u097F\\s]", " ");
        entityCleaned = entityCleaned.replaceAll("\\b\\d+\\b", " ");
        entityCleaned = entityCleaned.replaceAll("\\s+", " ").trim();

        // 4. Build Canonical String
        StringBuilder canonical = new StringBuilder();
        if (!entityCleaned.isBlank()) {
            canonical.append(entityCleaned).append(" ");
        }
        for (String c : concepts) {
            canonical.append("_").append(c).append("_ ");
        }

        return new NormalizedQuery(raw, canonical.toString().trim(), concepts, entityCleaned);
    }
}
