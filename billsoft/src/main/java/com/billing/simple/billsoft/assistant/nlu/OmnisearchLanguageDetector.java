package com.billing.simple.billsoft.assistant.nlu;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Lightweight, 100% offline, pure-JVM language and script detector.
 * Identifies Devanagari (Hindi/Marathi), Latin (English/Hinglish/Romanized Marathi), and mixed text.
 */
@Component
public class OmnisearchLanguageDetector {

    private static final Set<String> MARATHI_MARKERS = Set.of(
            "baki", "kiti", "ahe", "dakhva", "navin", "kharch", "hajiri", "pagar", "dena", "yene", "mal"
    );

    private static final Set<String> HINDI_MARKERS = Set.of(
            "kitna", "kitni", "hai", "batao", "dikhao", "naya", "kharcha", "aaj", "paisa", "paise", "maal", "hisab"
    );

    public enum DetectedScript {
        DEVANAGARI_HINDI,
        DEVANAGARI_MARATHI,
        ROMANIZED_HINDI,
        ROMANIZED_MARATHI,
        ENGLISH,
        MIXED
    }

    public DetectedScript detectLanguage(String text) {
        if (text == null || text.isBlank()) {
            return DetectedScript.ENGLISH;
        }

        boolean hasDevanagari = false;
        boolean hasLatin = false;

        for (char c : text.toCharArray()) {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
            if (block == Character.UnicodeBlock.DEVANAGARI) {
                hasDevanagari = true;
            } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                hasLatin = true;
            }
        }

        if (hasDevanagari && hasLatin) {
            return DetectedScript.MIXED;
        }

        if (hasDevanagari) {
            // Check for specific Marathi Devanagari words vs Hindi
            String lower = text.trim();
            if (lower.contains("दाखवा") || lower.contains("किती") || lower.contains("आहे") || lower.contains("पगार") || lower.contains("शिल्लक") || lower.contains("हजेरी")) {
                return DetectedScript.DEVANAGARI_MARATHI;
            }
            return DetectedScript.DEVANAGARI_HINDI;
        }

        // Latin script: Check markers
        String lower = text.toLowerCase();
        String[] tokens = lower.split("\\s+");
        int marathiCount = 0;
        int hindiCount = 0;

        for (String t : tokens) {
            if (MARATHI_MARKERS.contains(t)) marathiCount++;
            if (HINDI_MARKERS.contains(t)) hindiCount++;
        }

        if (marathiCount > hindiCount && marathiCount > 0) {
            return DetectedScript.ROMANIZED_MARATHI;
        }
        if (hindiCount > 0) {
            return DetectedScript.ROMANIZED_HINDI;
        }

        return DetectedScript.ENGLISH;
    }
}
