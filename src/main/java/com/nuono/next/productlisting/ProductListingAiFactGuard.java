package com.nuono.next.productlisting;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class ProductListingAiFactGuard {

    private static final Pattern NUMBER = Pattern.compile("(?<!\\p{N})\\p{N}+(?!\\p{N})");
    private static final Pattern LETTER_NUMBER_IDENTIFIER = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}])(?=[\\p{L}\\p{N}]*\\p{L})(?=[\\p{L}\\p{N}]*\\p{N})[\\p{L}\\p{N}]+(?![\\p{L}\\p{N}])"
    );
    private ProductListingAiFactGuard() {
    }

    static List<String> validate(ProductListingDraftCommand draft, Map<String, Object> uploadDraft) {
        if (draft == null || uploadDraft == null || uploadDraft.isEmpty()) {
            return List.of();
        }
        List<String> issues = new ArrayList<>();
        String outputEnglish = text(uploadDraft.get("productTitleEn"));
        String outputArabic = text(uploadDraft.get("productTitleAr"));

        validateCrossLanguageLiteralFacts(issues, draft, outputEnglish, outputArabic);
        validateTitleAttributeValues(issues, draft, outputEnglish, outputArabic);
        return issues.stream().distinct().collect(Collectors.toList());
    }

    private static void validateCrossLanguageLiteralFacts(
            List<String> issues,
            ProductListingDraftCommand draft,
            String outputEnglish,
            String outputArabic
    ) {
        List<String> sourceTitles = List.of(
                text(draft.getProductTitleCn()),
                text(draft.getProductTitleEn()),
                text(draft.getProductTitleAr())
        );
        Set<String> numbers = new LinkedHashSet<>();
        Set<String> identifiers = new LinkedHashSet<>();
        sourceTitles.forEach(source -> {
            numbers.addAll(matches(NUMBER, normalizeDigits(source)));
            identifiers.addAll(matches(LETTER_NUMBER_IDENTIFIER, normalizeLiteral(source)));
        });
        requireLiteralFacts(issues, "英文标题", outputEnglish, numbers, identifiers);
        requireLiteralFacts(issues, "阿拉伯语标题", outputArabic, numbers, identifiers);
    }

    private static void requireLiteralFacts(
            List<String> issues,
            String field,
            String outputTitle,
            Set<String> numbers,
            Set<String> identifiers
    ) {
        Set<String> outputNumbers = new LinkedHashSet<>(matches(NUMBER, normalizeDigits(outputTitle)));
        Set<String> outputIdentifiers = new LinkedHashSet<>(matches(
                LETTER_NUMBER_IDENTIFIER,
                normalizeLiteral(outputTitle)
        ));
        List<String> missing = new ArrayList<>();
        numbers.stream().filter(item -> !outputNumbers.contains(item)).forEach(missing::add);
        identifiers.stream().filter(item -> !outputIdentifiers.contains(item)).forEach(missing::add);
        if (!missing.isEmpty()) {
            issues.add(field + "未保留原标题核心事实：" + summarize(missing));
        }
    }

    private static void validateTitleAttributeValues(
            List<String> issues,
            ProductListingDraftCommand draft,
            String outputEnglish,
            String outputArabic
    ) {
        if (draft.getKeyAttributes() == null) {
            return;
        }
        String sourceEnglish = normalizeLiteral(draft.getProductTitleEn());
        String sourceArabic = normalizeLiteral(draft.getProductTitleAr());
        String normalizedOutputEnglish = normalizeLiteral(outputEnglish);
        String normalizedOutputArabic = normalizeLiteral(outputArabic);
        for (Map<String, Object> attribute : draft.getKeyAttributes()) {
            if (attribute == null || !ProductListingVerifiedAttributeEvidence.isProtectedFactEvidence(attribute)) {
                continue;
            }
            requireAttributeValue(
                    issues,
                    "英文标题",
                    text(attribute.get("enValue")),
                    sourceEnglish,
                    normalizedOutputEnglish
            );
            requireAttributeValue(
                    issues,
                    "阿拉伯语标题",
                    text(attribute.get("arValue")),
                    sourceArabic,
                    normalizedOutputArabic
            );
        }
    }

    private static void requireAttributeValue(
            List<String> issues,
            String field,
            String value,
            String sourceTitle,
            String outputTitle
    ) {
        String normalizedValue = normalizeLiteral(value);
        if (normalizedValue.length() >= 2
                && sourceTitle.contains(normalizedValue)
                && !outputTitle.contains(normalizedValue)) {
            issues.add(field + "未保留原标题中的已验证属性：" + value.trim());
        }
    }

    private static List<String> matches(Pattern pattern, String value) {
        List<String> values = new ArrayList<>();
        Matcher matcher = pattern.matcher(value);
        while (matcher.find()) {
            values.add(matcher.group());
        }
        return values;
    }

    private static String normalizeLiteral(String value) {
        return normalizeDigits(Normalizer.normalize(text(value), Normalizer.Form.NFKC))
                .toLowerCase(Locale.ROOT);
    }

    private static String normalizeDigits(String value) {
        StringBuilder normalized = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> {
            int digit = Character.digit(codePoint, 10);
            normalized.appendCodePoint(digit >= 0 ? '0' + digit : codePoint);
        });
        return normalized.toString();
    }

    private static String summarize(List<String> values) {
        return values.stream().distinct().limit(5).collect(Collectors.joining("、"));
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
