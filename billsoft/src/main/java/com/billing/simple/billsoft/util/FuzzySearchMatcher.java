package com.billing.simple.billsoft.util;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Universal case-insensitive, space-insensitive, special-char-insensitive,
 * partial-word, tokenized, and missing-word tolerant search matcher.
 */
public class FuzzySearchMatcher {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[\\-_/\\\\.,()\\[\\]{}|*+&!@~:;'\"?<>`#^%=]+");
    private static final Pattern MULTI_WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern NON_DIGITS = Pattern.compile("\\D+");

    public static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String clean = NON_ALPHANUMERIC.matcher(text.toLowerCase()).replaceAll(" ");
        return MULTI_WHITESPACE.matcher(clean).replaceAll(" ").trim();
    }

    public static String toCompact(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    public static String toDigits(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return NON_DIGITS.matcher(text).replaceAll("");
    }

    public static boolean isSubsequence(String needle, String haystack) {
        if (needle == null || haystack == null || needle.isEmpty() || haystack.isEmpty()) {
            return false;
        }
        int nIdx = 0;
        int nLen = needle.length();
        int hLen = haystack.length();
        for (int hIdx = 0; hIdx < hLen && nIdx < nLen; hIdx++) {
            if (needle.charAt(nIdx) == haystack.charAt(hIdx)) {
                nIdx++;
            }
        }
        return nIdx == nLen;
    }

    public static int score(String rawQuery, List<String> candidateFields, String primaryField) {
        if (rawQuery == null || rawQuery.trim().isEmpty()) {
            return 1;
        }
        String normQuery = normalize(rawQuery);
        String queryCompact = toCompact(rawQuery);
        String queryDigits = toDigits(rawQuery);

        if (normQuery.isEmpty() && queryCompact.isEmpty()) {
            return 1;
        }

        String[] queryTokens = normQuery.isEmpty() ? new String[]{queryCompact} : normQuery.split(" ");
        if (queryTokens.length == 0) {
            return 1;
        }

        List<String> validFields = candidateFields != null
                ? candidateFields.stream().filter(Objects::nonNull).toList()
                : Collections.emptyList();

        String primaryNorm = normalize(primaryField != null ? primaryField : (validFields.isEmpty() ? "" : validFields.get(0)));
        String combinedRaw = String.join(" ", validFields);
        String combinedNorm = normalize(combinedRaw);
        String combinedCompact = toCompact(combinedRaw);
        String combinedDigits = toDigits(combinedRaw);

        int baseScore = 0;
        if (!normQuery.isEmpty()) {
            if (primaryNorm.equals(normQuery)) {
                baseScore += 1000;
            } else if (primaryNorm.startsWith(normQuery)) {
                baseScore += 600;
            } else if (primaryNorm.contains(normQuery)) {
                baseScore += 400;
            } else if (combinedNorm.contains(normQuery)) {
                baseScore += 300;
            }
        }
        if (!queryCompact.isEmpty() && combinedCompact.contains(queryCompact)) {
            baseScore += 250;
        }

        String[] candidateWords = combinedNorm.isEmpty() ? new String[0] : combinedNorm.split(" ");
        Set<String> wordSet = new HashSet<>(Arrays.asList(candidateWords));

        Set<String> matchedTokenSet = new HashSet<>();
        int matchedTokens = 0;
        int tokenScore = 0;

        for (String token : queryTokens) {
            if (token == null || token.isBlank()) continue;
            boolean matched = false;

            if (wordSet.contains(token)) {
                matched = true;
                tokenScore += 80;
            } else {
                for (String word : candidateWords) {
                    if (word.startsWith(token)) {
                        matched = true;
                        tokenScore += 50;
                        break;
                    } else if (word.contains(token)) {
                        matched = true;
                        tokenScore += 30;
                        break;
                    } else if (token.length() >= 3 && isSubsequence(token, word)) {
                        matched = true;
                        tokenScore += 15;
                        break;
                    }
                }
            }

            if (!matched && combinedNorm.contains(token)) {
                matched = true;
                tokenScore += 20;
            }
            if (!matched && !queryCompact.isEmpty() && combinedCompact.contains(token)) {
                matched = true;
                tokenScore += 25;
            }
            if (!matched && !queryDigits.isEmpty() && token.length() >= 3 && combinedDigits.contains(token)) {
                matched = true;
                tokenScore += 40;
            }

            if (matched) {
                matchedTokens++;
                matchedTokenSet.add(token);
            }
        }

        // Any numeric token (e.g. dimensions, codes, numbers like "15", "20") must strictly match
        for (String token : queryTokens) {
            if (token != null && token.chars().anyMatch(Character::isDigit)) {
                boolean numMatched = matchedTokenSet.contains(token)
                        || (!queryCompact.isEmpty() && combinedCompact.contains(token))
                        || (!combinedDigits.isEmpty() && combinedDigits.contains(token));
                if (!numMatched) {
                    return 0;
                }
            }
        }

        boolean allMatched = matchedTokens == queryTokens.length;
        boolean partialMatched = queryTokens.length >= 3 && matchedTokens >= queryTokens.length - 1;

        if (!allMatched && !partialMatched && baseScore == 0) {
            if (!queryCompact.isEmpty() && queryCompact.length() >= 2 && combinedCompact.contains(queryCompact)) {
                return 100;
            }
            if (!queryDigits.isEmpty() && queryDigits.length() >= 4 && combinedDigits.contains(queryDigits)) {
                return 120;
            }
            return 0;
        }

        return baseScore + tokenScore + (matchedTokens * 20);
    }

    public static <T> List<T> filterAndSort(Collection<T> items, String query, Function<T, List<String>> fieldsExtractor, int limit) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        if (query == null || query.trim().isEmpty()) {
            return items.stream().limit(limit > 0 ? limit : 50).collect(Collectors.toList());
        }

        class Scored<I> {
            final I item;
            final int score;
            Scored(I item, int score) {
                this.item = item;
                this.score = score;
            }
        }

        List<Scored<T>> scored = new ArrayList<>();
        for (T item : items) {
            List<String> fields = fieldsExtractor != null ? fieldsExtractor.apply(item) : Collections.emptyList();
            String primary = (fields != null && !fields.isEmpty()) ? fields.get(0) : "";
            int s = score(query, fields, primary);
            if (s > 0) {
                scored.add(new Scored<>(item, s));
            }
        }

        scored.sort((a, b) -> Integer.compare(b.score, a.score));

        int max = limit > 0 ? Math.min(limit, scored.size()) : scored.size();
        List<T> result = new ArrayList<>(max);
        for (int i = 0; i < max; i++) {
            result.add(scored.get(i).item);
        }
        return result;
    }
}
