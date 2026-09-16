package com.billing.simple.billsoft.assistant.nlu;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OmnisearchLanguageDetectorTest {

    private OmnisearchLanguageDetector detector;

    @BeforeEach
    void setUp() {
        detector = new OmnisearchLanguageDetector();
    }

    @Test
    void testDetectEnglish() {
        assertThat(detector.detectLanguage("Show customer balance for Amit"))
                .isEqualTo(OmnisearchLanguageDetector.DetectedScript.ENGLISH);
    }

    @Test
    void testDetectDevanagariHindi() {
        assertThat(detector.detectLanguage("अमित का बकाया कितना है"))
                .isEqualTo(OmnisearchLanguageDetector.DetectedScript.DEVANAGARI_HINDI);
    }

    @Test
    void testDetectDevanagariMarathi() {
        assertThat(detector.detectLanguage("अमितची उधारी दाखवा"))
                .isEqualTo(OmnisearchLanguageDetector.DetectedScript.DEVANAGARI_MARATHI);
    }

    @Test
    void testDetectRomanizedHindi() {
        assertThat(detector.detectLanguage("Amit ka hisab kitna hai"))
                .isEqualTo(OmnisearchLanguageDetector.DetectedScript.ROMANIZED_HINDI);
    }

    @Test
    void testDetectRomanizedMarathi() {
        assertThat(detector.detectLanguage("Amit chi baki kiti ahe"))
                .isEqualTo(OmnisearchLanguageDetector.DetectedScript.ROMANIZED_MARATHI);
    }
}
