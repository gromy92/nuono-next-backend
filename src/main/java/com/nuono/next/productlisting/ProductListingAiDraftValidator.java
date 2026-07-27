package com.nuono.next.productlisting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class ProductListingAiDraftValidator {

    private static final int MIN_QUALITY_SCORE = 85;

    private static final Pattern URL = Pattern.compile("(?i)(https?://|www\\.|\\b[a-z0-9.-]+\\.(com|net|org|ae|sa)\\b)");
    private static final Pattern EMAIL = Pattern.compile("(?i)\\b[\\w.+-]+@[\\w.-]+\\.[a-z]{2,}\\b");
    private static final Pattern HTML = Pattern.compile("<[^>]+>");
    private static final Pattern FORBIDDEN_TITLE_SYMBOL = Pattern.compile("[@^*#&【】<>]");
    private static final Pattern TERMINAL_PUNCTUATION = Pattern.compile("[\\s\\p{Zs}]*[.,;:!?،؛؟。！？，：]+[\\s\\p{Zs}]*$");
    private static final Pattern CURRENCY = Pattern.compile("(?i)(\\b(AED|SAR)\\b|ر\\.س|درهم|ريال)");
    private static final List<String> FORBIDDEN_COPY_TERMS = List.of(
            "free shipping", "fast shipping", "same day shipping", "shipping fee", "ships in",
            "free delivery", "fast delivery", "same day delivery", "delivery promise",
            "warranty", "guarantee", "buy now", "order today", "special offer", "discount", "best price",
            "seller", "amazon", "namshi", "ebay", "شحن مجاني", "الشحن المجاني", "شحن سريع",
            "توصيل مجاني", "توصيل سريع", "ضمان", "اشتر الآن", "اطلب الآن", "عرض خاص", "خصم",
            "أفضل سعر", "البائع", "أمازون", "نمشي"
    );
    private static final List<String> REVIEW_ONLY_TERMS = List.of(
            "not confirmed", "not verified", "should be checked", "needs confirmation", "to be confirmed",
            "before final upload", "before publication", "لم يتم تأكيد", "غير مؤكد", "يجب مراجعة",
            "بحاجة إلى تأكيد", "قبل الاعتماد"
    );

    private ProductListingAiDraftValidator() {
    }

    static List<String> validate(
            ProductListingDraftCommand draft,
            ProductListingAiFactLedger factLedger,
            Map<String, Object> data
    ) {
        return inspect(draft, factLedger, data).messages();
    }

    static ProductListingAiValidationResult inspect(
            ProductListingDraftCommand draft,
            ProductListingAiFactLedger factLedger,
            Map<String, Object> data
    ) {
        List<String> issues = new ArrayList<>();
        if (data == null || data.isEmpty()) {
            issues.add("AI 未返回结构化 Listing 数据");
            return new ProductListingAiValidationResult(issues, List.of());
        }

        Map<String, Object> uploadDraft = object(data.get("noonUploadDraft"));
        if (uploadDraft.isEmpty()) {
            issues.add("缺少 noonUploadDraft 上架草稿");
        } else {
            validateTitle(issues, "英文标题", text(uploadDraft.get("productTitleEn")));
            validateTitle(issues, "阿拉伯语标题", text(uploadDraft.get("productTitleAr")));
            validateHighlights(issues, "英文卖点", stringList(uploadDraft.get("productHighlightsEn")));
            validateHighlights(issues, "阿拉伯语卖点", stringList(uploadDraft.get("productHighlightsAr")));
            validateDescription(issues, "英文描述", text(uploadDraft.get("productDescriptionEn")));
            validateDescription(issues, "阿拉伯语描述", text(uploadDraft.get("productDescriptionAr")));
            issues.addAll(ProductListingAiFactGuard.validate(draft, uploadDraft));
            if (factLedger != null) {
                issues.addAll(factLedger.validateOutput(uploadDraft));
            }
        }

        Map<String, Object> qualityCheck = object(data.get("qualityCheck"));
        Object score = qualityCheck.get("score");
        if (!(score instanceof Number)
                || !isInteger((Number) score)
                || ((Number) score).intValue() < 0
                || ((Number) score).intValue() > 100) {
            issues.add("质检分数必须是 0-100 的整数");
        } else if (((Number) score).intValue() < MIN_QUALITY_SCORE) {
            issues.add("质检分数低于 " + MIN_QUALITY_SCORE + "，当前为 " + ((Number) score).intValue());
        }

        List<String> missingCritical = stringList(object(data.get("inputCompleteness")).get("missingCritical"));
        if (!missingCritical.isEmpty()) {
            String summary = missingCritical.stream().limit(3).collect(Collectors.joining("、"));
            issues.add("仍有 " + missingCritical.size() + " 项关键事实冲突：" + summary);
        }
        return new ProductListingAiValidationResult(issues, missingCritical);
    }

    private static boolean isInteger(Number value) {
        double number = value.doubleValue();
        return Double.isFinite(number) && number == Math.rint(number);
    }

    private static void validateTitle(List<String> issues, String field, String value) {
        validateLength(issues, field, value, 20, 160);
        validatePlainCopy(issues, field, value);
        if (FORBIDDEN_TITLE_SYMBOL.matcher(value).find()) {
            issues.add(field + "含 Noon 标题不允许的特殊字符");
        }
        if (isAllCaps(value)) {
            issues.add(field + "不能使用全大写文案");
        }
    }

    private static void validateHighlights(List<String> issues, String field, List<String> values) {
        if (values.size() < 3 || values.size() > 5) {
            issues.add(field + "必须包含 3-5 条，当前为 " + values.size() + " 条");
        }
        Set<String> unique = new HashSet<>();
        for (int index = 0; index < values.size(); index++) {
            String itemField = field + "第 " + (index + 1) + " 条";
            String value = values.get(index);
            validateLength(issues, itemField, value, 10, 250);
            validatePlainCopy(issues, itemField, value);
            if (value.contains("【") || value.contains("】")) {
                issues.add(itemField + "不能使用装饰标题括号");
            }
            if (TERMINAL_PUNCTUATION.matcher(value).find()) {
                issues.add(itemField + "末尾不能带标点");
            }
            if (!unique.add(value.toLowerCase(Locale.ROOT))) {
                issues.add(field + "包含重复内容");
            }
        }
    }

    private static void validateDescription(List<String> issues, String field, String value) {
        validateLength(issues, field, value, 250, 4000);
        validatePlainCopy(issues, field, value);
    }

    private static void validateLength(List<String> issues, String field, String value, int min, int max) {
        int length = value.codePointCount(0, value.length());
        if (length < min || length > max) {
            issues.add(field + "长度必须为 " + min + "-" + max + " 字符，当前为 " + length + " 字符");
        }
    }

    private static void validatePlainCopy(List<String> issues, String field, String value) {
        if (value.contains("**") || value.contains("__") || HTML.matcher(value).find()) {
            issues.add(field + "不能包含 Markdown 或 HTML");
        }
        if (URL.matcher(value).find() || EMAIL.matcher(value).find()) {
            issues.add(field + "不能包含链接或联系方式");
        }
        if (containsEmoji(value)) {
            issues.add(field + "不能包含 emoji");
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (FORBIDDEN_COPY_TERMS.stream().anyMatch(normalized::contains) || CURRENCY.matcher(value).find()) {
            issues.add(field + "含价格、促销、物流、保修、卖家或其他平台信息");
        }
        if (REVIEW_ONLY_TERMS.stream().anyMatch(normalized::contains)) {
            issues.add(field + "包含仅供内部使用的人工质检措辞");
        }
    }

    private static boolean isAllCaps(String value) {
        String letters = value.replaceAll("[^A-Za-z]", "");
        return letters.length() >= 10 && letters.equals(letters.toUpperCase(Locale.ROOT));
    }

    private static boolean containsEmoji(String value) {
        return value.codePoints().anyMatch(codePoint ->
                (codePoint >= 0x1F000 && codePoint <= 0x1FAFF)
                        || (codePoint >= 0x2600 && codePoint <= 0x27BF)
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List)) {
            return List.of();
        }
        return ((List<?>) value).stream()
                .map(ProductListingAiDraftValidator::text)
                .filter(item -> !item.isEmpty())
                .collect(Collectors.toList());
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
