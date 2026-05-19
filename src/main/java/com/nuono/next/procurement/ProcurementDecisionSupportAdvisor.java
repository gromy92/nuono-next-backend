package com.nuono.next.procurement;

import com.nuono.next.procurement.ProcurementCandidatePoolView.CandidateView;
import com.nuono.next.procurement.ProcurementCandidatePoolView.DemandItemView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
public class ProcurementDecisionSupportAdvisor {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+(?:\\.\\d+)?");
    private static final Pattern DAY_RANGE_PATTERN = Pattern.compile("(\\d+)\\s*[-~至]\\s*(\\d+)\\s*天");
    private static final Pattern DAY_LIMIT_PATTERN = Pattern.compile("(\\d+)\\s*天内");
    private static final Pattern HOUR_LIMIT_PATTERN = Pattern.compile("(\\d+)\\s*小时");

    public void enrichCandidateDecisionSupport(DemandItemView demandItem, CandidateView candidate) {
        if (demandItem == null || candidate == null) {
            return;
        }

        PriceRange priceRange = parsePriceRange(candidate.getPriceText());
        Integer moq = parseLeadingInteger(candidate.getMoqText());
        DeliveryWindow deliveryWindow = parseDeliveryWindow(firstNonBlank(candidate.getDeliveryTimelineText(), candidate.getShippingSnapshotText()));

        candidate.setStandardizedPriceText(formatPriceRange(priceRange, candidate.getPriceText()));
        candidate.setStandardizedMoqText(formatMoq(moq, candidate.getMoqText()));
        candidate.setStandardizedMaterialText(normalizeLabel(candidate.getMaterialText()));
        candidate.setStandardizedPowerModeText(normalizeLabel(candidate.getPowerModeText()));
        candidate.setStandardizedSizeText(normalizeLabel(candidate.getSizeText()));
        candidate.setStandardizedPackageText(normalizeLabel(candidate.getPackageText()));
        candidate.setStandardizedDeliveryText(formatDelivery(deliveryWindow, candidate.getDeliveryTimelineText()));
        candidate.setPendingQuestions(buildPendingQuestions(demandItem, candidate, priceRange, moq, deliveryWindow));
    }

    private List<String> buildPendingQuestions(
            DemandItemView demandItem,
            CandidateView candidate,
            PriceRange priceRange,
            Integer moq,
            DeliveryWindow deliveryWindow
    ) {
        List<String> questions = new ArrayList<>();

        if (!StringUtils.hasText(candidate.getSupplierName())) {
            questions.add("确认供应商主体、工厂属性和是否能稳定供货。");
        }
        if (!StringUtils.hasText(candidate.getMainImageUrl())) {
            questions.add("补看主图和详情图，确认外观、开孔和按键位置是否同款。");
        }

        BigDecimal targetMin = demandItem.getTargetPriceMin();
        BigDecimal targetMax = demandItem.getTargetPriceMax();
        if (priceRange == null) {
            questions.add("确认真实报价是否含包装、配件和打样成本。");
        } else if (targetMin != null && targetMax != null) {
            if (priceRange.getMinPrice().compareTo(targetMax.multiply(BigDecimal.valueOf(1.05))) > 0) {
                questions.add(String.format("确认价格是否能谈到 %s 以内，当前报价偏高。", formatMoney(targetMax)));
            } else if (priceRange.getMaxPrice().compareTo(targetMin.multiply(BigDecimal.valueOf(0.85))) < 0) {
                questions.add("确认低价是否缺少配件、礼盒或关键工艺，避免只比到裸品价。");
            }
        }

        if (moq == null) {
            questions.add("确认最低起订量和阶梯价。");
        } else if (demandItem.getTargetQuantity() != null && moq > demandItem.getTargetQuantity()) {
            questions.add(String.format("确认起订量能否从 %d 件降到 %d 件左右。", moq, demandItem.getTargetQuantity()));
        }

        if (requiresMaterialConfirmation(demandItem.getTargetMaterial(), candidate.getStandardizedMaterialText())) {
            questions.add(String.format(
                    "确认材质是否满足目标要求，当前目标是 %s。",
                    defaultText(demandItem.getTargetMaterial(), "目标材质")
            ));
        }
        if (requiresPowerConfirmation(demandItem.getTargetPowerMode(), candidate.getStandardizedPowerModeText())) {
            questions.add(String.format(
                    "确认供电方式是否满足目标要求，当前目标是 %s。",
                    defaultText(demandItem.getTargetPowerMode(), "目标供电方式")
            ));
        }
        if (requiresSizeConfirmation(demandItem.getTargetSizeText(), candidate.getStandardizedSizeText())) {
            questions.add(String.format(
                    "确认尺寸和体量是否符合 %s 的要求。",
                    defaultText(demandItem.getTargetSizeText(), "目标尺寸")
            ));
        }
        if (requiresPackageConfirmation(demandItem.getTargetPackageType(), candidate.getStandardizedPackageText())) {
            questions.add(String.format(
                    "确认包装是否能做到 %s，并补齐包装清单。",
                    defaultText(demandItem.getTargetPackageType(), "目标包装")
            ));
        }

        Integer deliveryTargetMaxDays = parseDeliveryExpectationMaxDays(demandItem.getDeliveryExpectation());
        if (deliveryWindow == null) {
            questions.add(String.format(
                    "确认实际交期能否满足 %s。",
                    defaultText(demandItem.getDeliveryExpectation(), "当前交付节奏")
            ));
        } else if (deliveryTargetMaxDays != null && deliveryWindow.getMaxDays() != null
                && deliveryWindow.getMaxDays() > deliveryTargetMaxDays) {
            questions.add(String.format(
                    "确认交期能否压到 %s 内，当前识别为 %s。",
                    deliveryTargetMaxDays,
                    defaultText(candidate.getStandardizedDeliveryText(), "待确认")
            ));
        }

        String normalizedPower = lower(candidate.getStandardizedPowerModeText());
        if (normalizedPower.contains("充电")) {
            questions.add("确认电池容量、续航时间和充电接口类型。");
        } else if (normalizedPower.contains("插电")) {
            questions.add("确认插头规格、电压适配和沙特站可售性。");
        }

        String normalizedMaterial = lower(candidate.getStandardizedMaterialText());
        if (normalizedMaterial.contains("陶瓷")) {
            questions.add("确认陶瓷胆材质、厚度、是否可拆洗以及耐高温表现。");
        }

        String normalizedPackage = lower(candidate.getStandardizedPackageText());
        if (normalizedPackage.contains("礼盒") || normalizedPackage.contains("彩盒")) {
            questions.add("确认包装清单、礼盒尺寸、定制方式和是否含说明书。");
        }

        LinkedHashSet<String> deduplicated = new LinkedHashSet<>();
        for (String question : questions) {
            String normalized = normalizeLabel(question);
            if (StringUtils.hasText(normalized)) {
                deduplicated.add(normalized);
            }
            if (deduplicated.size() >= 6) {
                break;
            }
        }

        if (deduplicated.isEmpty()) {
            deduplicated.add("当前基础信息已齐，建议直接围绕价格明细、样品和交付节奏进入询价。");
        }
        return new ArrayList<>(deduplicated);
    }

    private boolean requiresMaterialConfirmation(String target, String candidate) {
        Set<String> targetKeywords = materialKeywords(target);
        if (targetKeywords.isEmpty()) {
            return !StringUtils.hasText(candidate);
        }
        Set<String> candidateKeywords = materialKeywords(candidate);
        return !candidateKeywords.containsAll(targetKeywords);
    }

    private boolean requiresPowerConfirmation(String target, String candidate) {
        String targetCategory = powerCategory(target);
        if (!StringUtils.hasText(targetCategory)) {
            return !StringUtils.hasText(candidate);
        }
        return !targetCategory.equals(powerCategory(candidate));
    }

    private boolean requiresSizeConfirmation(String target, String candidate) {
        String targetCategory = sizeCategory(target);
        if (!StringUtils.hasText(targetCategory)) {
            return !StringUtils.hasText(candidate);
        }
        return !targetCategory.equals(sizeCategory(candidate));
    }

    private boolean requiresPackageConfirmation(String target, String candidate) {
        Set<String> targetKeywords = packageKeywords(target);
        if (targetKeywords.isEmpty()) {
            return !StringUtils.hasText(candidate);
        }
        Set<String> candidateKeywords = packageKeywords(candidate);
        return !candidateKeywords.containsAll(targetKeywords);
    }

    private Set<String> materialKeywords(String rawValue) {
        Set<String> keywords = new LinkedHashSet<>();
        String normalized = lower(rawValue);
        if (!StringUtils.hasText(normalized)) {
            return keywords;
        }
        if (normalized.contains("abs")) {
            keywords.add("abs");
        }
        if (normalized.contains("陶瓷")) {
            keywords.add("陶瓷");
        }
        if (normalized.contains("电镀")) {
            keywords.add("电镀");
        }
        if (normalized.contains("金属")) {
            keywords.add("金属");
        }
        if (normalized.contains("树脂")) {
            keywords.add("树脂");
        }
        if (normalized.contains("塑料")) {
            keywords.add("塑料");
        }
        return keywords;
    }

    private Set<String> packageKeywords(String rawValue) {
        Set<String> keywords = new LinkedHashSet<>();
        String normalized = lower(rawValue);
        if (!StringUtils.hasText(normalized)) {
            return keywords;
        }
        if (normalized.contains("轻奢礼盒")) {
            keywords.add("轻奢礼盒");
        }
        if (normalized.contains("礼盒")) {
            keywords.add("礼盒");
        }
        if (normalized.contains("彩盒")) {
            keywords.add("彩盒");
        }
        if (normalized.contains("牛皮盒")) {
            keywords.add("牛皮盒");
        }
        if (normalized.contains("袋")) {
            keywords.add("袋装");
        }
        return keywords;
    }

    private String powerCategory(String rawValue) {
        String normalized = lower(rawValue);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        if (normalized.contains("充电") || normalized.contains("usb") || normalized.contains("电池")) {
            return "charge";
        }
        if (normalized.contains("插电") || normalized.contains("插头") || normalized.contains("plug")) {
            return "plug";
        }
        if (normalized.contains("蜡烛") || normalized.contains("木炭") || normalized.contains("炭")) {
            return "fire";
        }
        if (normalized.contains("无电") || normalized.contains("非电")) {
            return "none";
        }
        return normalized;
    }

    private String sizeCategory(String rawValue) {
        String normalized = lower(rawValue);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        if (normalized.contains("手持")) {
            return "handheld";
        }
        if (normalized.contains("便携") || normalized.contains("迷你")) {
            return "portable";
        }
        if (normalized.contains("桌面") || normalized.contains("家居")) {
            return "desktop";
        }
        if (normalized.contains("落地")) {
            return "floor";
        }
        return normalized;
    }

    private PriceRange parsePriceRange(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        Matcher matcher = NUMBER_PATTERN.matcher(rawValue);
        List<BigDecimal> values = new ArrayList<>();
        while (matcher.find()) {
            try {
                values.add(new BigDecimal(matcher.group()));
            } catch (NumberFormatException exception) {
                // ignore invalid number token
            }
        }
        if (values.isEmpty()) {
            return null;
        }
        BigDecimal min = values.get(0);
        BigDecimal max = values.size() > 1 ? values.get(1) : values.get(0);
        if (max.compareTo(min) < 0) {
            max = min;
        }
        return new PriceRange(min, max);
    }

    private Integer parseLeadingInteger(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        Matcher matcher = NUMBER_PATTERN.matcher(rawValue);
        if (!matcher.find()) {
            return null;
        }
        try {
            return new BigDecimal(matcher.group()).setScale(0, RoundingMode.DOWN).intValue();
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private DeliveryWindow parseDeliveryWindow(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        String normalized = normalizeLabel(rawValue);
        Matcher dayRangeMatcher = DAY_RANGE_PATTERN.matcher(normalized);
        if (dayRangeMatcher.find()) {
            return new DeliveryWindow(
                    Integer.parseInt(dayRangeMatcher.group(1)),
                    Integer.parseInt(dayRangeMatcher.group(2))
            );
        }
        Matcher dayLimitMatcher = DAY_LIMIT_PATTERN.matcher(normalized);
        if (dayLimitMatcher.find()) {
            Integer value = Integer.parseInt(dayLimitMatcher.group(1));
            return new DeliveryWindow(value, value);
        }
        Matcher hourLimitMatcher = HOUR_LIMIT_PATTERN.matcher(normalized);
        if (hourLimitMatcher.find()) {
            int hours = Integer.parseInt(hourLimitMatcher.group(1));
            int days = Math.max(1, (int) Math.ceil(hours / 24.0));
            return new DeliveryWindow(days, days);
        }
        if (normalized.contains("现货")) {
            return new DeliveryWindow(1, 3);
        }
        return null;
    }

    private Integer parseDeliveryExpectationMaxDays(String rawValue) {
        DeliveryWindow window = parseDeliveryWindow(rawValue);
        return window == null ? null : window.getMaxDays();
    }

    private String formatPriceRange(PriceRange priceRange, String rawValue) {
        if (priceRange == null) {
            return normalizeLabel(rawValue);
        }
        if (priceRange.getMinPrice().compareTo(priceRange.getMaxPrice()) == 0) {
            return formatMoney(priceRange.getMinPrice()) + " 元";
        }
        return formatMoney(priceRange.getMinPrice()) + " - " + formatMoney(priceRange.getMaxPrice()) + " 元";
    }

    private String formatMoq(Integer moq, String rawValue) {
        if (moq == null) {
            return normalizeLabel(rawValue);
        }
        return moq + " 件起";
    }

    private String formatDelivery(DeliveryWindow window, String rawValue) {
        if (window == null) {
            return normalizeLabel(rawValue);
        }
        if (window.getMinDays() != null && window.getMaxDays() != null && window.getMinDays().equals(window.getMaxDays())) {
            return window.getMaxDays() + " 天内";
        }
        return window.getMinDays() + "-" + window.getMaxDays() + " 天";
    }

    private String normalizeLabel(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        return rawValue.trim().replaceAll("\\s+", " ");
    }

    private String lower(String rawValue) {
        String normalized = normalizeLabel(rawValue);
        return normalized == null ? "" : normalized.toLowerCase(Locale.ROOT);
    }

    private String defaultText(String value, String fallback) {
        String normalized = normalizeLabel(value);
        return StringUtils.hasText(normalized) ? normalized : fallback;
    }

    private String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        return second;
    }

    private String formatMoney(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static class PriceRange {

        private final BigDecimal minPrice;
        private final BigDecimal maxPrice;

        private PriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
            this.minPrice = minPrice;
            this.maxPrice = maxPrice;
        }

        public BigDecimal getMinPrice() {
            return minPrice;
        }

        public BigDecimal getMaxPrice() {
            return maxPrice;
        }
    }

    private static class DeliveryWindow {

        private final Integer minDays;
        private final Integer maxDays;

        private DeliveryWindow(Integer minDays, Integer maxDays) {
            this.minDays = minDays;
            this.maxDays = maxDays;
        }

        public Integer getMinDays() {
            return minDays;
        }

        public Integer getMaxDays() {
            return maxDays;
        }
    }
}
