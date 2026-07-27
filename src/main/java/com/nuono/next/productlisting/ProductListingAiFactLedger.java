package com.nuono.next.productlisting;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

final class ProductListingAiFactLedger {

    private static final Set<String> TITLE_SOURCE_FIELDS = Set.of("titleCn", "titleEn", "titleAr");
    private static final Set<String> ATTRIBUTE_SOURCE_FIELDS = Set.of("keyAttributes", "keyAttribute");
    private final List<Map<String, Object>> facts;

    private ProductListingAiFactLedger(List<Map<String, Object>> facts) {
        this.facts = facts;
    }

    static ProductListingAiFactLedger from(Map<String, Object> data) {
        List<Map<String, Object>> facts = new ArrayList<>();
        Object value = data == null ? null : data.get("facts");
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (item instanceof Map) {
                    facts.add(copyMap(item));
                }
            }
        }
        return new ProductListingAiFactLedger(facts);
    }

    List<String> validateSource(ProductListingDraftCommand draft) {
        List<String> issues = new ArrayList<>();
        if (draft == null) {
            issues.add("事实账本缺少商品草稿");
            return issues;
        }
        if (hasPrimaryTitle(draft) && facts.isEmpty()) {
            issues.add("事实账本未提取原标题中的商品事实");
            return issues;
        }

        Set<String> factIds = new HashSet<>();
        boolean hasProductIdentity = false;
        for (Map<String, Object> fact : facts) {
            String factId = text(fact.get("factId"));
            String sourceField = text(fact.get("sourceField"));
            String sourceText = text(fact.get("sourceText"));
            String factType = text(fact.get("factType"));
            String englishCanonical = text(fact.get("englishCanonical"));
            String arabicCanonical = text(fact.get("arabicCanonical"));
            if (!StringUtils.hasText(factId) || !factIds.add(factId)) {
                issues.add("事实账本 factId 缺失或重复");
            }
            if (!StringUtils.hasText(sourceText) || !sourceContains(draft, sourceField, sourceText)) {
                issues.add("事实 " + label(factId) + " 无法回指原始商品资料：" + sourceText);
            }
            if (!StringUtils.hasText(englishCanonical) || !StringUtils.hasText(arabicCanonical)) {
                issues.add("事实 " + label(factId) + " 缺少双语标准表达");
            }
            if ("PRODUCT_IDENTITY".equalsIgnoreCase(factType)) {
                hasProductIdentity = true;
            }
        }
        if (hasPrimaryTitle(draft) && !hasProductIdentity) {
            issues.add("事实账本缺少 PRODUCT_IDENTITY 商品身份事实");
        }
        return issues.stream().distinct().collect(Collectors.toList());
    }

    ProductListingAiFactLedger withoutUntraceableOptionalFacts(ProductListingDraftCommand draft) {
        if (draft == null) {
            return this;
        }
        List<Map<String, Object>> retained = facts.stream()
                .filter(fact -> isTitleRequired(fact)
                        || sourceContains(
                                draft,
                                text(fact.get("sourceField")),
                                text(fact.get("sourceText"))
                        ))
                .map(LinkedHashMap::new)
                .collect(Collectors.toList());
        return new ProductListingAiFactLedger(retained);
    }

    List<String> validateOutput(Map<String, Object> uploadDraft) {
        if (uploadDraft == null || uploadDraft.isEmpty()) {
            return List.of();
        }
        List<String> issues = new ArrayList<>();
        String englishTitle = normalize(text(uploadDraft.get("productTitleEn")));
        String arabicTitle = normalize(text(uploadDraft.get("productTitleAr")));
        for (Map<String, Object> fact : facts) {
            if (!isTitleRequired(fact)) {
                continue;
            }
            String factId = label(text(fact.get("factId")));
            String sourceText = text(fact.get("sourceText"));
            String englishCanonical = normalize(text(fact.get("englishCanonical")));
            String arabicCanonical = normalize(text(fact.get("arabicCanonical")));
            if (!englishCanonical.isEmpty() && !containsCanonicalFact(englishTitle, englishCanonical)) {
                issues.add("英文标题未保留事实 " + factId + "：" + sourceText);
            }
            if (!arabicCanonical.isEmpty() && !containsCanonicalFact(arabicTitle, arabicCanonical)) {
                issues.add("阿拉伯语标题未保留事实 " + factId + "：" + sourceText);
            }
        }
        return issues;
    }

    private boolean containsCanonicalFact(String normalizedTitle, String normalizedCanonical) {
        if (normalizedTitle.contains(normalizedCanonical)) {
            return true;
        }
        List<String> canonicalTokens = List.of(normalizedCanonical.split(" "));
        List<String> titleTokens = List.of(normalizedTitle.split(" "));
        if (canonicalTokens.size() <= 1) {
            return false;
        }
        long matched = canonicalTokens.stream()
                .filter(StringUtils::hasText)
                .filter(canonical -> titleTokens.stream().anyMatch(title -> tokensEquivalent(title, canonical)))
                .count();
        int required = (int) Math.ceil(canonicalTokens.size() * 0.75d);
        return matched >= required;
    }

    private boolean tokensEquivalent(String left, String right) {
        if (left.equals(right)) {
            return true;
        }
        String normalizedLeft = normalizeArabicForm(left);
        String normalizedRight = normalizeArabicForm(right);
        if (normalizedLeft.equals(normalizedRight)) {
            return true;
        }
        if (Set.of("ضوء", "مصباح", "انارة").contains(normalizedLeft)
                && Set.of("ضوء", "مصباح", "انارة").contains(normalizedRight)) {
            return true;
        }
        return isLatinToken(normalizedLeft)
                && isLatinToken(normalizedRight)
                && commonPrefixLength(normalizedLeft, normalizedRight) >= 7;
    }

    private String normalizeArabicForm(String value) {
        String normalized = value;
        if (normalized.length() > 3 && normalized.startsWith("و")) {
            normalized = normalized.substring(1);
        }
        if (normalized.length() > 3 && normalized.startsWith("ال")) {
            normalized = normalized.substring(2);
        }
        if (normalized.length() > 4 && normalized.endsWith("ية")) {
            normalized = normalized.substring(0, normalized.length() - 2);
        }
        return normalized;
    }

    private boolean isLatinToken(String value) {
        return value.matches("[a-z]+") && value.length() >= 7;
    }

    private int commonPrefixLength(String left, String right) {
        int limit = Math.min(left.length(), right.length());
        int index = 0;
        while (index < limit && left.charAt(index) == right.charAt(index)) {
            index++;
        }
        return index;
    }

    List<Map<String, Object>> promptFacts() {
        return facts.stream()
                .map(fact -> {
                    Map<String, Object> promptFact = new LinkedHashMap<>(fact);
                    promptFact.put("titleRequired", isTitleRequired(fact));
                    return promptFact;
                })
                .collect(Collectors.toList());
    }

    private boolean isTitleRequired(Map<String, Object> fact) {
        if (!Boolean.TRUE.equals(fact.get("titleRequired"))) {
            return false;
        }
        if (!"STYLE".equalsIgnoreCase(text(fact.get("factType")))) {
            return true;
        }
        String source = normalize(text(fact.get("sourceText")));
        String english = normalize(text(fact.get("englishCanonical")));
        return !Set.of(
                "基础款",
                "basic",
                "basic style",
                "basic model",
                "basic version",
                "standard model",
                "standard version"
        ).contains(source) && !Set.of(
                "basic",
                "basic style",
                "basic model",
                "basic version",
                "standard model",
                "standard version"
        ).contains(english);
    }

    private boolean sourceContains(ProductListingDraftCommand draft, String sourceField, String sourceText) {
        if (TITLE_SOURCE_FIELDS.contains(sourceField)) {
            return normalize(titleValue(draft, sourceField)).contains(normalize(sourceText));
        }
        if (!ATTRIBUTE_SOURCE_FIELDS.contains(sourceField) || draft.getKeyAttributes() == null) {
            return false;
        }
        return ProductListingVerifiedAttributeEvidence.containsSource(draft.getKeyAttributes(), sourceText);
    }

    private String titleValue(ProductListingDraftCommand draft, String sourceField) {
        if ("titleCn".equals(sourceField)) {
            return text(draft.getProductTitleCn());
        }
        if ("titleEn".equals(sourceField)) {
            return text(draft.getProductTitleEn());
        }
        return text(draft.getProductTitleAr());
    }

    private boolean hasPrimaryTitle(ProductListingDraftCommand draft) {
        return StringUtils.hasText(draft.getProductTitleCn())
                || StringUtils.hasText(draft.getProductTitleEn())
                || StringUtils.hasText(draft.getProductTitleAr());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> copyMap(Object value) {
        return new LinkedHashMap<>((Map<String, Object>) value);
    }

    private static String normalize(String value) {
        return normalizeDigits(Normalizer.normalize(text(value), Normalizer.Form.NFKC))
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}\\p{Zs}\\u060C\\u061B\\u061F]+", " ")
                .replaceAll("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]", "")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static String normalizeDigits(String value) {
        StringBuilder normalized = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> {
            int digit = Character.digit(codePoint, 10);
            normalized.appendCodePoint(digit >= 0 ? '0' + digit : codePoint);
        });
        return normalized.toString();
    }

    private static String label(String value) {
        return StringUtils.hasText(value) ? value : "未编号";
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
