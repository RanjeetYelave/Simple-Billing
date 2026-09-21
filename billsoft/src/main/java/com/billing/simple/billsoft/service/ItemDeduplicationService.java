package com.billing.simple.billsoft.service;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billing.simple.billsoft.dtos.DuplicateCandidateDto;
import com.billing.simple.billsoft.dtos.ItemSummaryDto;
import com.billing.simple.billsoft.entities.ItemDuplicateDismissal;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.entities.SavedItem;
import com.billing.simple.billsoft.repo.ItemDuplicateDismissalRepository;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.repo.SavedItemRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;

@Service
public class ItemDeduplicationService {

    private final ProductRepository productRepo;
    private final SavedItemRepository savedItemRepo;
    private final ItemDuplicateDismissalRepository dismissalRepo;

    private static final Map<String, String> ABBREVIATIONS = new HashMap<>();
    private static final Map<String, String> UNIT_ALIASES = new HashMap<>();
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("(\\d+(\\.\\d+)?)");

    static {
        ABBREVIATIONS.put("ss", "stainless steel");
        ABBREVIATIONS.put("ms", "mild steel");
        ABBREVIATIONS.put("gi", "galvanized iron");
        ABBREVIATIONS.put("pvc", "polyvinyl chloride");
        ABBREVIATIONS.put("upvc", "unplasticized polyvinyl chloride");
        ABBREVIATIONS.put("cpvc", "chlorinated polyvinyl chloride");
        ABBREVIATIONS.put("al", "aluminium");
        ABBREVIATIONS.put("cu", "copper");

        UNIT_ALIASES.put("\"", "inch");
        UNIT_ALIASES.put("in", "inch");
        UNIT_ALIASES.put("inch", "inch");
        UNIT_ALIASES.put("inches", "inch");
        UNIT_ALIASES.put("kg", "kg");
        UNIT_ALIASES.put("kgs", "kg");
        UNIT_ALIASES.put("kilo", "kg");
        UNIT_ALIASES.put("kilogram", "kg");
        UNIT_ALIASES.put("gm", "g");
        UNIT_ALIASES.put("gram", "g");
        UNIT_ALIASES.put("grams", "g");
        UNIT_ALIASES.put("m", "meter");
        UNIT_ALIASES.put("mtr", "meter");
        UNIT_ALIASES.put("meter", "meter");
        UNIT_ALIASES.put("meters", "meter");
        UNIT_ALIASES.put("pc", "pcs");
        UNIT_ALIASES.put("pcs", "pcs");
        UNIT_ALIASES.put("piece", "pcs");
        UNIT_ALIASES.put("pieces", "pcs");
        UNIT_ALIASES.put("nos", "pcs");
        UNIT_ALIASES.put("no", "pcs");
        UNIT_ALIASES.put("ltr", "liter");
        UNIT_ALIASES.put("litre", "liter");
        UNIT_ALIASES.put("litres", "liter");
        UNIT_ALIASES.put("liter", "liter");
    }

    public ItemDeduplicationService(ProductRepository productRepo,
                                    SavedItemRepository savedItemRepo,
                                    ItemDuplicateDismissalRepository dismissalRepo) {
        this.productRepo = productRepo;
        this.savedItemRepo = savedItemRepo;
        this.dismissalRepo = dismissalRepo;
    }

    private Long resolveFirmId(Long explicitFirmId) {
        Long target = explicitFirmId != null ? explicitFirmId : TenantContext.getCurrentFirmId();
        if (target == null || target <= 0) {
            throw new TenantSecurityException("Active firm context is required");
        }
        return target;
    }

    /**
     * Finds potential duplicate item candidates across active Products and SavedItems for a firm.
     * Execution is purely READ-ONLY. No data is modified.
     */
    public List<DuplicateCandidateDto> findDuplicateCandidates(Long firmId) {
        Long targetFirmId = resolveFirmId(firmId);

        // Fetch all active items in the firm
        List<ItemSummaryDto> allItems = new ArrayList<>();

        List<Product> products = productRepo.findByFirmId(targetFirmId);
        for (Product p : products) {
            if (Boolean.TRUE.equals(p.getIsArchived())) continue;
            allItems.add(ItemSummaryDto.builder()
                    .sourceType("PRODUCT")
                    .id(p.getId())
                    .name(p.getName())
                    .sku(p.getSku())
                    .barcode(p.getBarcode())
                    .unit(p.getUnit())
                    .price(p.getPrice())
                    .costPrice(p.getCostPrice())
                    .stockQuantity(p.getStockQuantity())
                    .hsnCode(p.getHsnCode())
                    .gstPercentage(p.getGstPercentage())
                    .category(p.getCategory())
                    .build());
        }

        List<SavedItem> savedItems = savedItemRepo.findActiveUnpromotedByFirmId(targetFirmId);
        for (SavedItem s : savedItems) {
            if (Boolean.TRUE.equals(s.getIsArchived())) continue;
            if (s.getCanonicalProductId() != null) continue;
            allItems.add(ItemSummaryDto.builder()
                    .sourceType("SAVED_ITEM")
                    .id(s.getId())
                    .name(s.getName())
                    .unit(s.getUnit())
                    .price(s.getSellingPrice())
                    .hsnCode(s.getHsnCode())
                    .gstPercentage(s.getGstPercentage())
                    .category(s.getCategory())
                    .usageCount(s.getUsageCount())
                    .build());
        }

        // Fetch existing dismissals for fast lookup
        List<ItemDuplicateDismissal> dismissals = dismissalRepo.findByFirmId(targetFirmId);
        Set<String> dismissedKeys = new HashSet<>();
        for (ItemDuplicateDismissal d : dismissals) {
            dismissedKeys.add(formatPairKey(d.getSourceTypeA(), d.getItemIdA(), d.getSourceTypeB(), d.getItemIdB()));
        }

        List<DuplicateCandidateDto> candidates = new ArrayList<>();

        // Pairwise comparison within the active firm
        int size = allItems.size();
        for (int i = 0; i < size; i++) {
            ItemSummaryDto itemA = allItems.get(i);
            for (int j = i + 1; j < size; j++) {
                ItemSummaryDto itemB = allItems.get(j);

                String pairKey = formatPairKey(itemA.getSourceType(), itemA.getId(), itemB.getSourceType(), itemB.getId());
                if (dismissedKeys.contains(pairKey)) {
                    continue;
                }

                DuplicateCandidateDto candidate = evaluatePair(pairKey, itemA, itemB);
                if (candidate != null && candidate.getSimilarityScore() >= 70) {
                    candidates.add(candidate);
                }
            }
        }

        // Sort candidates: safe matches first, then highest similarity score
        candidates.sort((c1, c2) -> {
            if (c1.isSafe() != c2.isSafe()) {
                return c1.isSafe() ? -1 : 1;
            }
            return Integer.compare(c2.getSimilarityScore(), c1.getSimilarityScore());
        });

        return candidates;
    }

    /**
     * Evaluates similarity, extracts numeric tokens to prevent false-positive variant matches,
     * and compiles explainable reasons and conflicts.
     */
    public DuplicateCandidateDto evaluatePair(String pairKey, ItemSummaryDto a, ItemSummaryDto b) {
        String nameA = a.getName() != null ? a.getName().trim() : "";
        String nameB = b.getName() != null ? b.getName().trim() : "";
        if (nameA.isEmpty() || nameB.isEmpty()) return null;

        List<String> reasons = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 1. Numeric / Variant Safety Guard
        List<String> numsA = extractNumbers(nameA);
        List<String> numsB = extractNumbers(nameB);
        boolean numbersDiffer = !numsA.equals(numsB);

        if (numbersDiffer && (!numsA.isEmpty() || !numsB.isEmpty())) {
            // Numbers differ (e.g. 25mm vs 30mm, 1 inch vs 1.5 inch)
            // This is a product variant, not a duplicate.
            warnings.add("Different numeric dimensions detected: [" + String.join(", ", numsA) + "] vs [" + String.join(", ", numsB) + "]");
            conflicts.add("Dimension variant mismatch");
        }

        // 2. Token Normalization & Expansion
        Set<String> tokensA = tokenizeAndExpand(nameA);
        Set<String> tokensB = tokenizeAndExpand(nameB);

        double jaccard = computeJaccard(tokensA, tokensB);
        double levenshtein = computeLevenshteinRatio(SavedItem.normalizeText(nameA), SavedItem.normalizeText(nameB));

        // 3. Exact and Unit matching
        boolean exactNormalized = SavedItem.normalizeText(nameA).equals(SavedItem.normalizeText(nameB));
        String unitA = normalizeUnit(a.getUnit());
        String unitB = normalizeUnit(b.getUnit());
        boolean unitMatch = unitA != null && unitB != null && unitA.equalsIgnoreCase(unitB);

        // Compute Base Score (0 to 100)
        double score;
        if (exactNormalized) {
            score = 98.0;
            reasons.add("Identical normalized name tokens");
        } else {
            score = (jaccard * 60.0) + (levenshtein * 30.0);
            if (jaccard > 0.7) {
                reasons.add(String.format("High token overlap (%.0f%% matching keywords)", jaccard * 100));
            } else if (levenshtein > 0.8) {
                reasons.add("Very similar character spelling / punctuation variant");
            }
        }

        if (unitMatch) {
            score += 10.0;
            reasons.add("Compatible unit (" + unitA + ")");
        } else if (unitA != null && unitB != null && !unitA.equalsIgnoreCase(unitB)) {
            conflicts.add("Unit mismatch: '" + a.getUnit() + "' vs '" + b.getUnit() + "'");
        }

        // GST Comparison
        if (a.getGstPercentage() != null && b.getGstPercentage() != null) {
            if (a.getGstPercentage().compareTo(b.getGstPercentage()) == 0) {
                score += 5.0;
                reasons.add("Same GST rate (" + a.getGstPercentage().stripTrailingZeros().toPlainString() + "%)");
            } else {
                conflicts.add("Different GST rates: " + a.getGstPercentage() + "% vs " + b.getGstPercentage() + "%");
            }
        }

        // Price Comparison
        if (a.getPrice() != null && b.getPrice() != null) {
            if (a.getPrice().compareTo(b.getPrice()) == 0) {
                score += 5.0;
                reasons.add("Identical selling price (₹" + a.getPrice().stripTrailingZeros().toPlainString() + ")");
            } else {
                BigDecimal diff = a.getPrice().subtract(b.getPrice()).abs();
                conflicts.add("Selling price difference: ₹" + a.getPrice() + " vs ₹" + b.getPrice() + " (diff ₹" + diff + ")");
            }
        }

        // SKU / Barcode Match
        if (a.getSku() != null && b.getSku() != null && !a.getSku().trim().isEmpty() && !b.getSku().trim().isEmpty()) {
            if (a.getSku().trim().equalsIgnoreCase(b.getSku().trim())) {
                score = Math.max(score, 99.0);
                reasons.add("Identical SKU code: " + a.getSku());
            } else {
                conflicts.add("Different SKU codes: " + a.getSku() + " vs " + b.getSku());
            }
        }

        // If numbers differ, severely penalize similarity score so variants are not false positives
        if (numbersDiffer && (!numsA.isEmpty() || !numsB.isEmpty())) {
            score = Math.min(score * 0.4, 45.0);
        }

        int finalScore = Math.min(100, Math.max(0, (int) Math.round(score)));
        boolean isSafe = conflicts.isEmpty() && finalScore >= 80 && !numbersDiffer;

        return DuplicateCandidateDto.builder()
                .candidateKey(pairKey)
                .candidateA(a)
                .candidateB(b)
                .similarityScore(finalScore)
                .isSafe(isSafe)
                .reasons(reasons)
                .conflicts(conflicts)
                .warnings(warnings)
                .build();
    }

    @Transactional
    public void dismissCandidate(Long firmId, String typeA, Long idA, String typeB, Long idB) {
        Long targetFirmId = resolveFirmId(firmId);
        if (typeA == null || idA == null || typeB == null || idB == null) {
            throw new IllegalArgumentException("Invalid candidate pair parameters");
        }

        if (!dismissalRepo.isDismissed(targetFirmId, typeA, idA, typeB, idB)) {
            ItemDuplicateDismissal dismissal = ItemDuplicateDismissal.builder()
                    .firmId(targetFirmId)
                    .sourceTypeA(typeA)
                    .itemIdA(idA)
                    .sourceTypeB(typeB)
                    .itemIdB(idB)
                    .build();
            dismissalRepo.save(dismissal);
        }
    }

    // --- Helper Utilities ---

    public static String formatPairKey(String typeA, Long idA, String typeB, Long idB) {
        String keyA = typeA + ":" + idA;
        String keyB = typeB + ":" + idB;
        return keyA.compareTo(keyB) <= 0 ? keyA + "_" + keyB : keyB + "_" + keyA;
    }

    public static String normalizeUnit(String unit) {
        if (unit == null) return null;
        String cleaned = unit.trim().toLowerCase();
        return UNIT_ALIASES.getOrDefault(cleaned, cleaned);
    }

    public static List<String> extractNumbers(String text) {
        List<String> list = new ArrayList<>();
        if (text == null) return list;
        Matcher m = NUMERIC_PATTERN.matcher(text);
        while (m.find()) {
            list.add(m.group(1));
        }
        return list;
    }

    public static Set<String> tokenizeAndExpand(String text) {
        Set<String> tokens = new HashSet<>();
        if (text == null) return tokens;
        String[] rawTokens = SavedItem.normalizeText(text).split("\\s+");
        for (String t : rawTokens) {
            if (t.trim().isEmpty()) continue;
            tokens.add(t);
            if (ABBREVIATIONS.containsKey(t)) {
                String expanded = ABBREVIATIONS.get(t);
                tokens.addAll(Arrays.asList(expanded.split("\\s+")));
            }
        }
        return tokens;
    }

    private double computeJaccard(Set<String> setA, Set<String> setB) {
        if (setA.isEmpty() && setB.isEmpty()) return 1.0;
        if (setA.isEmpty() || setB.isEmpty()) return 0.0;
        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);
        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);
        return (double) intersection.size() / union.size();
    }

    private double computeLevenshteinRatio(String s1, String s2) {
        if (s1.equals(s2)) return 1.0;
        int len1 = s1.length();
        int len2 = s2.length();
        if (len1 == 0 || len2 == 0) return 0.0;

        int[][] dp = new int[len1 + 1][len2 + 1];
        for (int i = 0; i <= len1; i++) dp[i][0] = i;
        for (int j = 0; j <= len2; j++) dp[0][j] = j;

        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }

        int distance = dp[len1][len2];
        int maxLen = Math.max(len1, len2);
        return 1.0 - ((double) distance / maxLen);
    }
}
