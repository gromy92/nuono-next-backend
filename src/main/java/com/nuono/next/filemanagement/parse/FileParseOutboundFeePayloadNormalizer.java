package com.nuono.next.filemanagement.parse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

final class FileParseOutboundFeePayloadNormalizer {

    private static final Pattern DECIMAL_PATTERN = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final Pattern TEXT_DATE_PATTERN = Pattern.compile(
            "(?i)(\\d{1,2})(?:st|nd|rd|th)?\\s+([a-z]+)\\s+(\\d{4})"
    );
    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy.M.d"),
            new DateTimeFormatterBuilder()
                    .appendPattern("yyyy-M-d")
                    .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                    .toFormatter()
    };

    private FileParseOutboundFeePayloadNormalizer() {
    }

    static Map<String, Object> normalize(String itemType, Map<String, Object> payload) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (payload != null) {
            normalized.putAll(payload);
        }
        if (!FileParseOfficialOutboundFeeStandard.supportedItemTypeNames().contains(itemType)) {
            return normalized;
        }

        putIfText(normalized, "country", normalizeCountry(text(normalized.get("country"))));
        putIfText(normalized, "platform", normalizePlatform(text(normalized.get("platform"))));
        putIfText(normalized, "fulfillmentType", normalizeFulfillmentType(text(normalized.get("fulfillmentType"))));
        putIfText(normalized, "classificationName", normalizeSpaces(text(normalized.get("classificationName"))));
        putIfText(normalized, "feeItem", normalizeSpaces(text(normalized.get("feeItem"))));
        putIfText(normalized, "sizeTier", normalizeSpaces(text(normalized.get("sizeTier"))));
        putIfText(normalized, "currency", normalizeCurrency(text(normalized.get("currency"))));
        putIfText(normalized, "thresholdCurrency", normalizeCurrency(text(normalized.get("thresholdCurrency"))));
        putIfText(normalized, "dimensionUnit", normalizeDimensionUnit(text(normalized.get("dimensionUnit"))));
        putIfText(normalized, "weightUnit", normalizeWeightUnit(text(normalized.get("weightUnit"))));
        putIfText(normalized, "effectiveDate", normalizeDate(text(normalized.get("effectiveDate"))));

        normalizeDecimalField(normalized, "longestSideMaxCm");
        normalizeDecimalField(normalized, "medianSideMaxCm");
        normalizeDecimalField(normalized, "shortestSideMaxCm");
        normalizeDecimalField(normalized, "maxShippingWeightGrams");
        normalizeDecimalField(normalized, "packagingWeightGrams");
        normalizeDecimalField(normalized, "weightMinGrams");
        normalizeDecimalField(normalized, "weightMaxGrams");
        normalizeDecimalField(normalized, "standardFeeAmount");
        normalizeDecimalField(normalized, "highAspFeeAmount");
        normalizeDecimalField(normalized, "salesPriceThresholdAmount");
        normalizeDecimalField(normalized, "extraWeightStepGrams");
        normalizeDecimalField(normalized, "extraFeeAmount");
        normalizeDecimalField(normalized, "feeAmount");
        normalizeDecimalField(normalized, "minFee");

        normalizeBooleanField(normalized, "weightMinInclusive");
        normalizeBooleanField(normalized, "weightMaxInclusive");

        putIfText(normalized, "shippingWeightFormula", normalizeShippingWeightFormula(text(normalized.get("shippingWeightFormula"))));
        putIfText(normalized, "dimensionSortRule", normalizeSpaces(text(normalized.get("dimensionSortRule"))));
        putIfText(normalized, "weightBoundaryRule", normalizeSpaces(text(normalized.get("weightBoundaryRule"))));
        putIfText(normalized, "roundingRule", normalizeSpaces(text(normalized.get("roundingRule"))));
        putIfText(normalized, "policyName", normalizeSpaces(text(normalized.get("policyName"))));
        return normalized;
    }

    private static void normalizeDecimalField(Map<String, Object> payload, String key) {
        String raw = text(payload.get(key));
        if (containsFormulaOrPercent(raw)) {
            return;
        }
        BigDecimal value = normalizeDecimal(raw);
        if (value != null) {
            payload.put(key, normalizedNumber(value));
        }
    }

    private static void normalizeBooleanField(Map<String, Object> payload, String key) {
        Object raw = payload.get(key);
        if (raw instanceof Boolean) {
            return;
        }
        String value = text(raw);
        if (!StringUtils.hasText(value)) {
            return;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if ("true".equals(lower) || "yes".equals(lower) || "y".equals(lower) || "1".equals(lower) || "含".equals(value)) {
            payload.put(key, true);
        } else if ("false".equals(lower) || "no".equals(lower) || "n".equals(lower) || "0".equals(lower) || "不含".equals(value)) {
            payload.put(key, false);
        }
    }

    private static BigDecimal normalizeDecimal(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        Matcher matcher = DECIMAL_PATTERN.matcher(value.replace(",", ""));
        if (!matcher.find()) {
            return null;
        }
        try {
            return new BigDecimal(matcher.group()).stripTrailingZeros();
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean containsFormulaOrPercent(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return value.contains("%")
                || lower.contains("if(")
                || lower.contains("index(")
                || lower.contains("switch(")
                || lower.contains("_xlfn.switch");
    }

    private static Object normalizedNumber(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.scale() <= 0) {
            try {
                return stripped.longValueExact();
            } catch (ArithmeticException ignored) {
                return stripped;
            }
        }
        return stripped;
    }

    private static String normalizeCountry(String value) {
        String text = normalizeSpaces(value);
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String upper = text.toUpperCase(Locale.ROOT);
        if ("SA".equals(upper) || upper.contains("KSA") || upper.contains("SAUDI")) {
            return "KSA";
        }
        if ("AE".equals(upper) || upper.contains("UAE") || upper.contains("UNITED ARAB")) {
            return "UAE";
        }
        if ("EG".equals(upper) || upper.contains("EGY") || upper.contains("EGYPT")) {
            return "EGY";
        }
        return upper;
    }

    private static String normalizePlatform(String value) {
        String text = normalizeSpaces(value);
        if (!StringUtils.hasText(text)) {
            return "";
        }
        if (text.toLowerCase(Locale.ROOT).contains("noon")) {
            return "NOON";
        }
        return text.toUpperCase(Locale.ROOT);
    }

    private static String normalizeFulfillmentType(String value) {
        String text = normalizeSpaces(value);
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("fbn") || lower.contains("fulfilled by noon")) {
            return "FBN";
        }
        return text.toUpperCase(Locale.ROOT);
    }

    private static String normalizeCurrency(String value) {
        String text = normalizeSpaces(value);
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String upper = text.toUpperCase(Locale.ROOT);
        if (upper.contains("SAR")) {
            return "SAR";
        }
        if (upper.contains("AED")) {
            return "AED";
        }
        if (upper.contains("EGP")) {
            return "EGP";
        }
        return upper;
    }

    private static String normalizeDimensionUnit(String value) {
        String text = normalizeSpaces(value);
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if ("cm".equals(lower) || lower.contains("centimeter")) {
            return "cm";
        }
        return lower;
    }

    private static String normalizeWeightUnit(String value) {
        String text = normalizeSpaces(value);
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if ("g".equals(lower) || lower.contains("gram")) {
            return "grams";
        }
        if ("kg".equals(lower) || lower.contains("kilogram")) {
            return "kg";
        }
        return lower;
    }

    private static String normalizeShippingWeightFormula(String value) {
        String text = normalizeSpaces(value);
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("physical") && lower.contains("packag")) {
            return "physical_weight_plus_packaging_weight";
        }
        if (lower.contains("shipping") && lower.contains("packag")) {
            return "physical_weight_plus_packaging_weight";
        }
        return lower.replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    private static String normalizeDate(String value) {
        String text = normalizeSpaces(value);
        if (!StringUtils.hasText(text)) {
            return "";
        }
        Matcher textDate = TEXT_DATE_PATTERN.matcher(text);
        if (textDate.find()) {
            Integer month = monthNumber(textDate.group(2));
            if (month != null) {
                try {
                    int day = Integer.parseInt(textDate.group(1));
                    int year = Integer.parseInt(textDate.group(3));
                    return String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day);
                } catch (NumberFormatException ignored) {
                    return text;
                }
            }
        }
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(text, formatter).toString();
            } catch (DateTimeParseException ignored) {
                // Try the next formatter.
            }
        }
        return text;
    }

    private static Integer monthNumber(String monthName) {
        if (!StringUtils.hasText(monthName)) {
            return null;
        }
        switch (monthName.toLowerCase(Locale.ROOT)) {
            case "january":
                return 1;
            case "february":
                return 2;
            case "march":
                return 3;
            case "april":
                return 4;
            case "may":
                return 5;
            case "june":
                return 6;
            case "july":
                return 7;
            case "august":
                return 8;
            case "september":
                return 9;
            case "october":
                return 10;
            case "november":
                return 11;
            case "december":
                return 12;
            default:
                return null;
        }
    }

    private static void putIfText(Map<String, Object> payload, String key, String value) {
        if (StringUtils.hasText(value)) {
            payload.put(key, value);
        }
    }

    private static String normalizeSpaces(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }
}
