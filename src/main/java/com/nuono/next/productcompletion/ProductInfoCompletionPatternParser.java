package com.nuono.next.productcompletion;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ProductInfoCompletionPatternParser {

    private static final Pattern DIMENSION_3D_PATTERN = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*[xX*×]\\s*(\\d+(?:\\.\\d+)?)\\s*[xX*×]\\s*(\\d+(?:\\.\\d+)?)\\s*(cm|厘米|mm|毫米)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SIZE_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(cm|厘米|mm|毫米)", Pattern.CASE_INSENSITIVE);
    private static final Pattern WEIGHT_PATTERN = Pattern.compile(
            "(?:毛重|净重|重量|单重|克重|weight)\\s*[:：]?\\s*(\\d+(?:\\.\\d+)?)\\s*(kg|公斤|千克|g|克)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CARTON_QTY_PATTERN = Pattern.compile(
            "(?:装箱数|装箱量|每箱|一箱|整箱)\\s*[:：]?\\s*(\\d+)\\s*(?:个|件|pcs|只)?",
            Pattern.CASE_INSENSITIVE
    );

    String extractCategory(String rawText) {
        String normalized = normalize(rawText);
        if (containsAny(normalized, "香薰", "熏香", "香炉")) {
            return "香薰/熏香用品";
        }
        if (containsAny(normalized, "收纳", "置物架", "储物")) {
            return "收纳用品";
        }
        if (containsAny(normalized, "玩具", "积木", "娃娃")) {
            return "玩具";
        }
        if (containsAny(normalized, "灯", "照明", "台灯")) {
            return "灯具/照明";
        }
        return null;
    }

    String extractMaterial(String rawText) {
        String explicit = firstMatch(rawText, "(?:材质|材料|面料)\\s*[:：]?\\s*([^|，。；;\\n]{1,40})");
        if (StringUtils.hasText(explicit)) {
            return cleanFieldValue(explicit, "尺寸", "规格", "重量", "毛重", "净重", "包装", "装箱");
        }
        String normalized = normalize(rawText).toLowerCase(Locale.ROOT);
        if (normalized.contains("abs") && normalized.contains("陶瓷")) {
            return "ABS+陶瓷";
        }
        if (normalized.contains("陶瓷") && normalized.contains("树脂")) {
            return "陶瓷/树脂待确认";
        }
        if (normalized.contains("陶瓷")) {
            return "陶瓷";
        }
        if (normalized.contains("玻璃")) {
            return "玻璃";
        }
        if (normalized.contains("树脂")) {
            return "树脂";
        }
        if (normalized.contains("金属")) {
            return "金属";
        }
        if (normalized.contains("塑料")) {
            return "塑料";
        }
        if (normalized.contains("abs")) {
            return "ABS";
        }
        return null;
    }

    String extractPowerMode(String rawText) {
        String normalized = normalize(rawText).toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "无电", "非电", "不带电")) {
            return "无电";
        }
        if (containsAny(normalized, "锂电", "电池", "充电", "rechargeable")) {
            return "电池/充电款";
        }
        if (containsAny(normalized, "usb")) {
            return "USB 供电";
        }
        if (containsAny(normalized, "插电", "插头", "plug")) {
            return "插电款";
        }
        return null;
    }

    String extractDimensions(String rawText) {
        Matcher threeDimensional = DIMENSION_3D_PATTERN.matcher(rawText == null ? "" : rawText);
        if (threeDimensional.find()) {
            return threeDimensional.group(1) + "x" + threeDimensional.group(2) + "x" + threeDimensional.group(3)
                    + normalizeUnit(threeDimensional.group(4));
        }
        String explicit = firstMatch(rawText, "(?:尺寸|规格|产品尺寸)\\s*[:：]?\\s*([^|，。；;\\n]{2,60})");
        if (StringUtils.hasText(explicit)) {
            return cleanFieldValue(explicit, "重量", "毛重", "净重", "包装", "装箱");
        }
        Matcher singleSize = SIZE_PATTERN.matcher(rawText == null ? "" : rawText);
        return singleSize.find() ? singleSize.group(1) + normalizeUnit(singleSize.group(2)) : null;
    }

    String extractWeight(String rawText) {
        Matcher matcher = WEIGHT_PATTERN.matcher(rawText == null ? "" : rawText);
        if (matcher.find()) {
            return matcher.group(1) + normalizeWeightUnit(matcher.group(2));
        }
        return firstMatch(rawText, "(?:重量|毛重|净重)\\s*[:：]?\\s*([^|，。；;\\n]{1,30})");
    }

    String extractPackageSpec(String rawText) {
        String explicit = firstMatch(rawText, "(?:包装|包装方式|包装规格)\\s*[:：]?\\s*([^|，。；;\\n]{2,60})");
        if (StringUtils.hasText(explicit)) {
            return cleanFieldValue(explicit, "装箱", "重量", "毛重", "净重", "尺寸");
        }
        String normalized = normalize(rawText).toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "彩盒")) {
            return "彩盒包装";
        }
        if (containsAny(normalized, "opp袋", "opp 袋")) {
            return "OPP 袋包装";
        }
        return null;
    }

    String extractQuantityPerCarton(String rawText) {
        Matcher matcher = CARTON_QTY_PATTERN.matcher(rawText == null ? "" : rawText);
        return matcher.find() ? matcher.group(1) + "个/箱" : null;
    }

    boolean hasBatterySignal(String rawText) {
        return containsAny(normalize(rawText).toLowerCase(Locale.ROOT), "锂电", "电池", "充电");
    }

    boolean hasLiquidSignal(String rawText) {
        return containsAny(normalize(rawText), "液体", "精油", "香水", "喷雾", "膏体");
    }

    boolean hasFragileSignal(String rawText) {
        return containsAny(normalize(rawText), "陶瓷", "玻璃", "易碎");
    }

    boolean hasMagneticSignal(String rawText) {
        return containsAny(normalize(rawText), "磁铁", "磁性", "强磁");
    }

    boolean isOversize(String dimensions) {
        if (!StringUtils.hasText(dimensions)) {
            return false;
        }
        Matcher matcher = Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(dimensions);
        while (matcher.find()) {
            if (Double.parseDouble(matcher.group(1)) >= 100d) {
                return true;
            }
        }
        return false;
    }

    String parseHtmlTitle(String html) {
        if (!StringUtils.hasText(html)) {
            return null;
        }
        String title = Jsoup.parse(html).title();
        return StringUtils.hasText(title) ? title : null;
    }

    String stripHtml(String html) {
        return StringUtils.hasText(html) ? Jsoup.parse(html).text() : null;
    }

    private String firstMatch(String rawText, String regex) {
        Matcher matcher = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(rawText == null ? "" : rawText);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String cleanFieldValue(String value, String... nextLabels) {
        String cleaned = normalize(value);
        for (String label : nextLabels) {
            int index = cleaned.indexOf(label);
            if (index > 0) {
                cleaned = cleaned.substring(0, index);
            }
        }
        return cleaned.replaceAll("[:：,，/|\\s]+$", "").trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private String normalizeUnit(String unit) {
        return unit == null || unit.toLowerCase(Locale.ROOT).startsWith("c") || unit.contains("厘") ? "cm" : "mm";
    }

    private String normalizeWeightUnit(String unit) {
        return unit == null || unit.toLowerCase(Locale.ROOT).startsWith("k") || unit.contains("斤") || unit.contains("千") ? "kg" : "g";
    }

    private boolean containsAny(String value, String... keywords) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
