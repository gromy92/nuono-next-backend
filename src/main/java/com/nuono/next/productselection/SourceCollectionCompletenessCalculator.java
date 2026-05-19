package com.nuono.next.productselection;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SourceCollectionCompletenessCalculator {

    static final Set<String> UNIT_COUNT_SPEC_LABELS = Set.of(
            "unit count", "item quantity", "item qty", "number of pieces", "quantity",
            "qty", "package quantity", "pieces", "piece count"
    );

    private static final int DEFAULT_SOURCE_COLLECTION_FIELD_TOTAL = 8;
    private static final int AMAZON_SOURCE_COLLECTION_FIELD_TOTAL = 15;
    private static final int NOON_SOURCE_COLLECTION_FIELD_TOTAL = 15;
    private static final int SHEIN_SOURCE_COLLECTION_FIELD_TOTAL = 15;

    int fieldTotal(ProductSelectionSourceCollectionView view) {
        if ("Amazon".equalsIgnoreCase(defaultText(view.getSourcePlatform(), ""))) {
            return AMAZON_SOURCE_COLLECTION_FIELD_TOTAL;
        }
        if ("Noon".equalsIgnoreCase(defaultText(view.getSourcePlatform(), ""))) {
            return NOON_SOURCE_COLLECTION_FIELD_TOTAL;
        }
        if ("SHEIN".equalsIgnoreCase(defaultText(view.getSourcePlatform(), ""))) {
            return SHEIN_SOURCE_COLLECTION_FIELD_TOTAL;
        }
        return DEFAULT_SOURCE_COLLECTION_FIELD_TOTAL;
    }

    int countCollectedFields(ProductSelectionSourceCollectionView view) {
        if ("Amazon".equalsIgnoreCase(defaultText(view.getSourcePlatform(), ""))) {
            return countAmazonCollectedFields(view);
        }
        if ("Noon".equalsIgnoreCase(defaultText(view.getSourcePlatform(), ""))) {
            return countNoonCollectedFields(view);
        }
        if ("SHEIN".equalsIgnoreCase(defaultText(view.getSourcePlatform(), ""))) {
            return countSheinCollectedFields(view);
        }
        int count = 0;
        if (StringUtils.hasText(view.getSourceTitle())) {
            count++;
        }
        if (StringUtils.hasText(view.getSourceTitleCn())) {
            count++;
        }
        if (StringUtils.hasText(view.getSourceTitleAr())) {
            count++;
        }
        if (StringUtils.hasText(view.getSourceImageUrl())) {
            count++;
        }
        if (!view.getImageUrls().isEmpty()) {
            count++;
        }
        if (StringUtils.hasText(view.getPriceSummary())) {
            count++;
        }
        if (StringUtils.hasText(view.getMoqHint())) {
            count++;
        }
        if (StringUtils.hasText(view.getShippingFrom())) {
            count++;
        }
        if (!view.getSpecHints().isEmpty()) {
            count++;
        }
        if (StringUtils.hasText(view.getPageUrl()) || StringUtils.hasText(view.getSourceUrl())) {
            count++;
        }
        if (StringUtils.hasText(view.getSelectedTextAr())) {
            count++;
        }
        return count;
    }

    int countSpecAttributes(List<String> specHints) {
        if (specHints == null || specHints.isEmpty()) {
            return 0;
        }
        return (int) specHints.stream()
                .map(this::parseSpecPair)
                .filter(pair -> pair != null)
                .count();
    }

    String firstSpecValue(List<String> specHints, Set<String> labels) {
        return specValue(specHints == null ? List.of() : specHints, labels).stream()
                .findFirst()
                .orElse("");
    }

    private int countSheinCollectedFields(ProductSelectionSourceCollectionView view) {
        int count = 0;
        List<String> specHints = view.getSpecHints() == null ? List.of() : view.getSpecHints();
        if (hasSheinProductId(view, specHints)) {
            count++;
        }
        if (StringUtils.hasText(view.getSourceTitle())) {
            count++;
        }
        if (StringUtils.hasText(view.getSourceTitleAr())) {
            count++;
        }
        if (StringUtils.hasText(view.getPriceSummary())) {
            count++;
        }
        if (StringUtils.hasText(view.getSourceImageUrl())) {
            count++;
        }
        if (!view.getImageUrls().isEmpty()) {
            count++;
        }
        if (!view.getSourceSellingPointsEn().isEmpty()) {
            count++;
        }
        if (!view.getSourceSellingPointsAr().isEmpty()) {
            count++;
        }
        if (StringUtils.hasText(view.getSourceDescriptionEn())) {
            count++;
        }
        if (StringUtils.hasText(view.getSourceDescriptionAr())) {
            count++;
        }
        if (StringUtils.hasText(view.getBrandName()) || hasSpecValue(specHints, Set.of("brand", "brand name"))) {
            count++;
        }
        if (StringUtils.hasText(view.getColorName())
                || hasSpecValue(specHints, Set.of("color", "colour", "color name", "colour name"))) {
            count++;
        }
        if (hasSpecValue(specHints, Set.of("material", "material type", "base material", "secondary material"))) {
            count++;
        }
        if (StringUtils.hasText(view.getUnitCount()) || hasSpecValue(specHints, UNIT_COUNT_SPEC_LABELS)) {
            count++;
        }
        if (hasSpecValue(specHints, Set.of("size", "size name", "composition", "fabric", "product dimensions", "item dimensions"))) {
            count++;
        }
        return count;
    }

    private int countNoonCollectedFields(ProductSelectionSourceCollectionView view) {
        int count = 0;
        List<String> specHints = view.getSpecHints() == null ? List.of() : view.getSpecHints();
        if (hasNoonSku(view, specHints)) {
            count++;
        }
        if (StringUtils.hasText(view.getSourceTitle())) {
            count++;
        }
        if (StringUtils.hasText(view.getSourceTitleAr())) {
            count++;
        }
        if (StringUtils.hasText(view.getPriceSummary())) {
            count++;
        }
        if (StringUtils.hasText(view.getBrandName()) || hasSpecValue(specHints, Set.of("brand", "brand name"))) {
            count++;
        }
        if (StringUtils.hasText(view.getUnitCount()) || hasSpecValue(specHints, UNIT_COUNT_SPEC_LABELS)) {
            count++;
        }
        if (StringUtils.hasText(view.getColorName())
                || hasSpecValue(specHints, Set.of("color", "colour", "color name", "colour name"))) {
            count++;
        }
        if (hasAmazonRating(specHints)) {
            count++;
        }
        if (hasAmazonReviewCount(specHints)) {
            count++;
        }
        if (StringUtils.hasText(view.getSourceImageUrl())) {
            count++;
        }
        if (!view.getImageUrls().isEmpty()) {
            count++;
        }
        if (!view.getSourceSellingPointsEn().isEmpty()) {
            count++;
        }
        if (!view.getSourceSellingPointsAr().isEmpty()) {
            count++;
        }
        if (StringUtils.hasText(view.getSourceDescriptionEn())) {
            count++;
        }
        if (StringUtils.hasText(view.getSourceDescriptionAr())) {
            count++;
        }
        return count;
    }

    private int countAmazonCollectedFields(ProductSelectionSourceCollectionView view) {
        int count = 0;
        List<String> specHints = view.getSpecHints() == null ? List.of() : view.getSpecHints();
        if (hasAmazonAsin(view, specHints)) {
            count++;
        }
        if (StringUtils.hasText(view.getSourceTitle())) {
            count++;
        }
        if (StringUtils.hasText(view.getSourceTitleAr())) {
            count++;
        }
        if (StringUtils.hasText(view.getPriceSummary())) {
            count++;
        }
        if (StringUtils.hasText(view.getBrandName()) || hasSpecValue(specHints, Set.of("brand", "brand name"))) {
            count++;
        }
        if (StringUtils.hasText(view.getUnitCount()) || hasSpecValue(specHints, Set.of("unit count"))) {
            count++;
        }
        if (StringUtils.hasText(view.getColorName()) || hasSpecValue(specHints, Set.of("color", "colour"))) {
            count++;
        }
        if (hasAmazonRating(specHints)) {
            count++;
        }
        if (hasAmazonReviewCount(specHints)) {
            count++;
        }
        if (StringUtils.hasText(view.getSourceImageUrl())) {
            count++;
        }
        if (!view.getImageUrls().isEmpty()) {
            count++;
        }
        if (!view.getSourceSellingPointsEn().isEmpty()) {
            count++;
        }
        if (!view.getSourceSellingPointsAr().isEmpty()) {
            count++;
        }
        if (hasAmazonEnglishSummary(view)) {
            count++;
        }
        if (StringUtils.hasText(view.getSourceDescriptionAr())) {
            count++;
        }
        return count;
    }

    private boolean hasAmazonAsin(ProductSelectionSourceCollectionView view, List<String> specHints) {
        String urlText = defaultText(view.getPageUrl(), view.getSourceUrl());
        if (urlText.matches("(?i).*\\b[A-Z0-9]{10}\\b.*")) {
            return true;
        }
        return specValue(specHints, Set.of("asin")).stream().anyMatch(StringUtils::hasText);
    }

    private boolean hasNoonSku(ProductSelectionSourceCollectionView view, List<String> specHints) {
        String urlText = defaultText(view.getPageUrl(), view.getSourceUrl());
        if (urlText.matches("(?i).*\\b[NPZ][A-Z0-9]{8,}\\b.*")) {
            return true;
        }
        return specValue(specHints, Set.of("noon sku", "sku")).stream().anyMatch(StringUtils::hasText);
    }

    private boolean hasSheinProductId(ProductSelectionSourceCollectionView view, List<String> specHints) {
        String urlText = defaultText(view.getPageUrl(), view.getSourceUrl());
        if (urlText.matches("(?i).*-p-\\d+\\.html.*")) {
            return true;
        }
        return specValue(specHints, Set.of("shein product id", "shein sku", "sku", "goods sn", "goods id"))
                .stream()
                .anyMatch(StringUtils::hasText);
    }

    private boolean hasAmazonRating(List<String> specHints) {
        return specValue(specHints, Set.of("rating", "customer reviews")).stream()
                .anyMatch(value -> value.toLowerCase(Locale.ROOT).contains("out of 5") || value.matches(".*\\d+(\\.\\d+)?.*"));
    }

    private boolean hasAmazonReviewCount(List<String> specHints) {
        return specValue(specHints, Set.of("review count", "customer reviews")).stream()
                .anyMatch(value -> value.toLowerCase(Locale.ROOT).contains("review") || value.matches(".*\\d{2,}.*"));
    }

    private boolean hasAmazonEnglishSummary(ProductSelectionSourceCollectionView view) {
        return defaultText(view.getSourceDescriptionEn(), "").matches(".*[A-Za-z]{3,}.*");
    }

    private boolean hasSpecValue(List<String> specHints, Set<String> labels) {
        return specValue(specHints, labels).stream().anyMatch(StringUtils::hasText);
    }

    private List<String> specValue(List<String> specHints, Set<String> labels) {
        return specHints.stream()
                .map(this::parseSpecPair)
                .filter(pair -> pair != null && labels.contains(normalizeSpecLabel(pair.getLabel())))
                .map(SpecPair::getValue)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    private SpecPair parseSpecPair(String value) {
        int separatorIndex = value == null ? -1 : Math.max(value.indexOf(':'), value.indexOf('：'));
        if (separatorIndex <= 0) {
            return null;
        }
        String label = value.substring(0, separatorIndex).trim();
        String specValue = value.substring(separatorIndex + 1).trim();
        if (!StringUtils.hasText(label) || !StringUtils.hasText(specValue)) {
            return null;
        }
        return new SpecPair(label, specValue);
    }

    private String normalizeSpecLabel(String label) {
        return defaultText(label, "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private static final class SpecPair {
        private final String label;
        private final String value;

        private SpecPair(String label, String value) {
            this.label = label;
            this.value = value;
        }

        private String getLabel() {
            return label;
        }

        private String getValue() {
            return value;
        }
    }
}
