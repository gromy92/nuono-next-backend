package com.nuono.next.procurement;

import com.nuono.next.procurement.ProcurementCandidatePoolView.CandidateView;
import com.nuono.next.procurement.ProcurementCandidatePoolView.DemandItemView;
import com.nuono.next.procurement.ProcurementCandidatePoolView.FieldEvidenceView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
public class ProcurementStructuredFieldParser {

    private static final Pattern SIZE_PATTERN = Pattern.compile(
            "(\\d+(?:\\.\\d+)?(?:\\s*[-~至]\\s*\\d+(?:\\.\\d+)?)?)\\s*(cm|厘米)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern DAY_RANGE_PATTERN = Pattern.compile("(\\d+)\\s*[-~至]\\s*(\\d+)\\s*天");
    private static final Pattern DAY_LIMIT_PATTERN = Pattern.compile("(\\d+)\\s*天内");

    public void enrichDemandItem(DemandItemView demandItem) {
        if (demandItem == null) {
            return;
        }

        SourceCounter counter = new SourceCounter();
        String rawText = collectRawText(
                demandItem.getSourceTitle(),
                demandItem.getSpecialRequirement(),
                demandItem.getTargetMaterial(),
                demandItem.getTargetPowerMode(),
                demandItem.getTargetSizeText(),
                demandItem.getTargetPackageType(),
                demandItem.getDeliveryExpectation()
        );

        demandItem.setTargetMaterial(resolveValue(demandItem.getTargetMaterial(), parseMaterial(rawText), counter));
        demandItem.setTargetPowerMode(resolveValue(demandItem.getTargetPowerMode(), parsePowerMode(rawText), counter));
        demandItem.setTargetSizeText(resolveValue(demandItem.getTargetSizeText(), parseSize(rawText), counter));
        demandItem.setTargetPackageType(resolveValue(demandItem.getTargetPackageType(), parsePackage(rawText), counter));
        demandItem.setDeliveryExpectation(resolveValue(demandItem.getDeliveryExpectation(), parseDelivery(rawText), counter));
        demandItem.setStructuredFieldSource(counter.toSourceCode());
    }

    public void enrichCandidate(CandidateView candidate) {
        if (candidate == null) {
            return;
        }

        SourceCounter counter = new SourceCounter();
        List<SourceFragment> sources = buildCandidateSources(candidate);
        List<FieldEvidenceView> evidences = new ArrayList<>();

        FieldResolution materialResolution = resolveField(
                "MATERIAL",
                "材质",
                candidate.getMaterialText(),
                sources,
                this::parseMaterial
        );
        candidate.setMaterialText(applyFieldResolution(materialResolution, counter, evidences));

        FieldResolution powerModeResolution = resolveField(
                "POWER_MODE",
                "供电方式",
                candidate.getPowerModeText(),
                sources,
                this::parsePowerMode
        );
        candidate.setPowerModeText(applyFieldResolution(powerModeResolution, counter, evidences));

        FieldResolution sizeResolution = resolveField(
                "SIZE",
                "尺寸",
                candidate.getSizeText(),
                sources,
                this::parseSize
        );
        candidate.setSizeText(applyFieldResolution(sizeResolution, counter, evidences));

        FieldResolution packageResolution = resolveField(
                "PACKAGE",
                "包装",
                candidate.getPackageText(),
                sources,
                this::parsePackage
        );
        candidate.setPackageText(applyFieldResolution(packageResolution, counter, evidences));

        FieldResolution deliveryResolution = resolveField(
                "DELIVERY",
                "交期",
                candidate.getDeliveryTimelineText(),
                sources,
                this::parseDelivery
        );
        candidate.setDeliveryTimelineText(applyFieldResolution(deliveryResolution, counter, evidences));

        candidate.setStructuredFieldSource(counter.toSourceCode());
        candidate.setExtractionEvidences(evidences);
    }

    private String applyFieldResolution(
            FieldResolution resolution,
            SourceCounter counter,
            List<FieldEvidenceView> evidences
    ) {
        if (resolution == null || !StringUtils.hasText(resolution.getFieldValue())) {
            return null;
        }
        if ("MANUAL".equals(resolution.getSourceType())) {
            counter.manualCount += 1;
        } else {
            counter.autoCount += 1;
        }
        evidences.add(resolution.toView());
        return resolution.getFieldValue();
    }

    private FieldResolution resolveField(
            String fieldKey,
            String fieldLabel,
            String existingValue,
            List<SourceFragment> sources,
            Function<String, String> parser
    ) {
        if (StringUtils.hasText(existingValue)) {
            return new FieldResolution(fieldKey, fieldLabel, existingValue.trim(), "MANUAL", "人工维护", existingValue.trim());
        }

        for (SourceFragment source : sources) {
            if (!StringUtils.hasText(source.getText())) {
                continue;
            }
            String parsedValue = parser.apply(source.getText());
            if (StringUtils.hasText(parsedValue)) {
                return new FieldResolution(
                        fieldKey,
                        fieldLabel,
                        parsedValue.trim(),
                        "AUTO_PARSED",
                        source.getLabel(),
                        compactEvidence(source.getText())
                );
            }
        }
        return null;
    }

    private List<SourceFragment> buildCandidateSources(CandidateView candidate) {
        List<SourceFragment> sources = new ArrayList<>();
        sources.add(new SourceFragment("属性快照", candidate.getAttributeSnapshotText()));
        sources.add(new SourceFragment("详情卖点", candidate.getDetailHighlightText()));
        sources.add(new SourceFragment("包装说明", candidate.getPackageSnapshotText()));
        sources.add(new SourceFragment("物流说明", candidate.getShippingSnapshotText()));
        sources.add(new SourceFragment("结果卡片", candidate.getResultCardText()));
        sources.add(new SourceFragment("标题", candidate.getTitle()));
        sources.add(new SourceFragment("供应商", candidate.getSupplierName()));
        sources.add(new SourceFragment("标签", candidate.getBadgesText()));
        sources.add(new SourceFragment("推荐理由", candidate.getReasonsText()));
        sources.add(new SourceFragment("风险点", candidate.getWarningsText()));
        sources.add(new SourceFragment("价格与起订量", collectRawText(candidate.getPriceText(), candidate.getMoqText(), candidate.getLocationText())));
        return sources;
    }

    private String resolveValue(String existingValue, String parsedValue, SourceCounter counter) {
        if (StringUtils.hasText(existingValue)) {
            counter.manualCount += 1;
            return existingValue.trim();
        }
        if (StringUtils.hasText(parsedValue)) {
            counter.autoCount += 1;
            return parsedValue.trim();
        }
        return null;
    }

    private String collectRawText(String... rawValues) {
        List<String> parts = new ArrayList<>();
        if (rawValues == null) {
            return "";
        }
        for (String rawValue : rawValues) {
            if (StringUtils.hasText(rawValue)) {
                parts.add(rawValue.trim());
            }
        }
        return String.join(" | ", parts);
    }

    private String parseMaterial(String rawText) {
        String normalized = normalize(rawText);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if ((normalized.contains("abs") || normalized.contains("塑料")) && normalized.contains("电镀")) {
            return "ABS+电镀外壳";
        }
        if (normalized.contains("abs") && (normalized.contains("陶瓷内胆") || normalized.contains("陶瓷仓"))) {
            return "ABS+陶瓷内胆";
        }
        if (normalized.contains("釉面陶瓷") || normalized.contains("陶瓷上釉") || normalized.contains("上釉")) {
            return "釉面陶瓷";
        }
        if (normalized.contains("陶瓷") && normalized.contains("树脂")) {
            return "陶瓷/树脂待确认";
        }
        if (normalized.contains("陶瓷")) {
            return "陶瓷";
        }
        if (normalized.contains("金属")) {
            return "金属";
        }
        if (normalized.contains("树脂")) {
            return "树脂";
        }
        if (normalized.contains("塑料")) {
            return "塑料";
        }
        if (normalized.contains("abs")) {
            return "ABS";
        }
        return null;
    }

    private String parsePowerMode(String rawText) {
        String normalized = normalize(rawText);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if (normalized.contains("蜡烛") || normalized.contains("木炭") || normalized.contains("炭烧")) {
            return "蜡烛/炭";
        }
        if (normalized.contains("无电") || normalized.contains("非电")) {
            return "无电";
        }
        if (normalized.contains("插电") || normalized.contains("插头") || normalized.contains("plug")) {
            return "插电款";
        }
        if (normalized.contains("usb") || normalized.contains("充电") || normalized.contains("rechargeable") || normalized.contains("电池")) {
            return "充电款";
        }
        return null;
    }

    private String parseSize(String rawText) {
        String normalized = normalize(rawText);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }

        Matcher matcher = SIZE_PATTERN.matcher(rawText);
        if (matcher.find()) {
            return matcher.group(1).replaceAll("\\s+", "") + "cm";
        }
        if (normalized.contains("手持")) {
            return "手持便携";
        }
        if (normalized.contains("便携") || normalized.contains("迷你") || normalized.contains("portable")) {
            return "便携小型";
        }
        if (normalized.contains("落地")) {
            return "落地款";
        }
        if (normalized.contains("桌面") || normalized.contains("家居") || normalized.contains("home")) {
            return "桌面款";
        }
        return null;
    }

    private String parsePackage(String rawText) {
        String normalized = normalize(rawText);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if (normalized.contains("轻奢礼盒")) {
            return "轻奢礼盒";
        }
        if (normalized.contains("礼盒") || normalized.contains("礼品")) {
            return "礼盒装";
        }
        if (normalized.contains("牛皮盒")) {
            return "牛皮盒";
        }
        if (normalized.contains("彩盒")) {
            return "彩盒装";
        }
        if (normalized.contains("opp")) {
            return "OPP袋";
        }
        if (normalized.contains("袋装")) {
            return "袋装";
        }
        if (normalized.contains("箱装")) {
            return "箱装";
        }
        if (normalized.contains("普通盒") || normalized.contains("box")) {
            return "普通盒";
        }
        return null;
    }

    private String parseDelivery(String rawText) {
        String normalized = normalize(rawText);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if (normalized.contains("48小时")) {
            return "48小时发货";
        }
        if (normalized.contains("72小时")) {
            return "72小时发货";
        }
        if (normalized.contains("现货")) {
            return "48小时发货";
        }

        Matcher dayRangeMatcher = DAY_RANGE_PATTERN.matcher(normalized);
        if (dayRangeMatcher.find()) {
            return dayRangeMatcher.group(1) + "-" + dayRangeMatcher.group(2) + "天";
        }

        Matcher dayLimitMatcher = DAY_LIMIT_PATTERN.matcher(normalized);
        if (dayLimitMatcher.find()) {
            return dayLimitMatcher.group(1) + "天内";
        }
        return null;
    }

    private String normalize(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return "";
        }
        return rawText
                .toLowerCase(Locale.ROOT)
                .replace("（", "(")
                .replace("）", ")")
                .replaceAll("\\s+", "");
    }

    private String compactEvidence(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return "";
        }
        String compact = rawText.trim().replaceAll("\\s+", " ");
        if (compact.length() <= 96) {
            return compact;
        }
        return compact.substring(0, 93) + "...";
    }

    private static final class SourceCounter {
        private int manualCount;
        private int autoCount;

        private String toSourceCode() {
            if (manualCount > 0 && autoCount > 0) {
                return "MIXED";
            }
            if (manualCount > 0) {
                return "MANUAL";
            }
            if (autoCount > 0) {
                return "AUTO_PARSED";
            }
            return "EMPTY";
        }
    }

    private static final class SourceFragment {
        private final String label;
        private final String text;

        private SourceFragment(String label, String text) {
            this.label = label;
            this.text = text;
        }

        private String getLabel() {
            return label;
        }

        private String getText() {
            return text;
        }
    }

    private static final class FieldResolution {
        private final String fieldKey;
        private final String fieldLabel;
        private final String fieldValue;
        private final String sourceType;
        private final String sourceLabel;
        private final String evidenceText;

        private FieldResolution(
                String fieldKey,
                String fieldLabel,
                String fieldValue,
                String sourceType,
                String sourceLabel,
                String evidenceText
        ) {
            this.fieldKey = fieldKey;
            this.fieldLabel = fieldLabel;
            this.fieldValue = fieldValue;
            this.sourceType = sourceType;
            this.sourceLabel = sourceLabel;
            this.evidenceText = evidenceText;
        }

        private String getFieldValue() {
            return fieldValue;
        }

        private String getSourceType() {
            return sourceType;
        }

        private FieldEvidenceView toView() {
            FieldEvidenceView view = new FieldEvidenceView();
            view.setFieldKey(fieldKey);
            view.setFieldLabel(fieldLabel);
            view.setFieldValue(fieldValue);
            view.setSourceType(sourceType);
            view.setSourceLabel(sourceLabel);
            view.setEvidenceText(evidenceText);
            return view;
        }
    }
}
