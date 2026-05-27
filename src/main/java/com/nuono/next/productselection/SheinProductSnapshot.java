package com.nuono.next.productselection;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

class SheinProductSnapshot {

    private static final int MAX_IMAGES = 20;
    private static final int MAX_SPEC_HINTS = 64;
    private static final int MAX_SELLING_POINTS = 12;

    private String title = "";
    private String price = "";
    private String description = "";
    private String sku = "";
    private String brand = "";
    private String color = "";
    private String material = "";
    private String composition = "";
    private String size = "";
    private final LinkedHashSet<String> images = new LinkedHashSet<>();
    private final LinkedHashSet<String> specs = new LinkedHashSet<>();
    private final LinkedHashSet<String> sellingPoints = new LinkedHashSet<>();

    void ingest(ProductSelectionSourceCollectionResult result) {
        if (result == null) {
            return;
        }
        setTitle(result.getSourceTitle());
        setPrice(result.getPriceSummary());
        setDescription(result.getSourceDescriptionEn());
        if (result.getImageUrls() != null) {
            result.getImageUrls().forEach(this::addImage);
        }
        if (result.getSpecHints() != null) {
            result.getSpecHints().forEach(this::addRawSpec);
        }
        if (result.getSourceSellingPointsEn() != null) {
            result.getSourceSellingPointsEn().forEach(this::addSellingPoint);
        }
    }

    String title() {
        return title;
    }

    String price() {
        return price;
    }

    String description() {
        return description;
    }

    int imageCount() {
        return images.size();
    }

    void setTitle(String value) {
        if (!StringUtils.hasText(title) && StringUtils.hasText(value)) {
            title = value;
        }
    }

    void setPrice(String value) {
        if (!StringUtils.hasText(price) && StringUtils.hasText(value)) {
            price = value;
        }
    }

    void setDescription(String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        String text = value.trim();
        if (!StringUtils.hasText(description)
                || description.equals(title)
                || (text.length() > description.length() && !text.equals(title))) {
            description = text;
        }
    }

    void setSku(String value) {
        if (!StringUtils.hasText(sku) && StringUtils.hasText(value)) {
            sku = value;
            addSpec("SHEIN SKU", value);
        }
    }

    void setBrand(String value) {
        if (!StringUtils.hasText(brand) && StringUtils.hasText(value)) {
            brand = value;
            addSpec("Brand", value);
        }
    }

    void setColor(String value) {
        if (!StringUtils.hasText(color) && StringUtils.hasText(value)) {
            color = value;
            addSpec("Color", value);
        }
    }

    void setMaterial(String value) {
        if (!StringUtils.hasText(material) && StringUtils.hasText(value)) {
            material = value;
            addSpec("Material", value);
        }
    }

    void setComposition(String value) {
        if (!StringUtils.hasText(composition) && StringUtils.hasText(value)) {
            composition = value;
            addSpec("Composition", value);
        }
    }

    void setSize(String value) {
        if (!StringUtils.hasText(size) && StringUtils.hasText(value)) {
            size = value;
            addSpec("Size", value);
        }
    }

    void addSpec(String label, String value) {
        String normalizedLabel = label == null ? "" : label.trim();
        String normalizedValue = value == null ? "" : value.trim();
        if (StringUtils.hasText(normalizedLabel) && StringUtils.hasText(normalizedValue)) {
            specs.add(normalizedLabel + ": " + normalizedValue);
        }
    }

    void addRawSpec(String value) {
        if (StringUtils.hasText(value)) {
            specs.add(value.trim());
        }
    }

    void addImage(String value) {
        String url = normalizeImageUrl(value);
        if (StringUtils.hasText(url)) {
            images.add(url);
        }
    }

    void addSellingPoint(String value) {
        String text = value == null ? "" : value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
        if (text.length() >= 8 && text.length() <= 360) {
            sellingPoints.add(text);
        }
    }

    List<String> imageList() {
        return images.stream().limit(MAX_IMAGES).collect(Collectors.toList());
    }

    List<String> specList() {
        return specs.stream().filter(StringUtils::hasText).limit(MAX_SPEC_HINTS).collect(Collectors.toList());
    }

    List<String> sellingPointList() {
        return sellingPoints.stream().limit(MAX_SELLING_POINTS).collect(Collectors.toList());
    }

    private static String normalizeImageUrl(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            return "";
        }
        String value = rawUrl.trim().replace("\\/", "/");
        if (value.startsWith("//")) {
            value = "https:" + value;
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            return "";
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("data:") || lower.endsWith(".svg") || lower.contains("sprite") || lower.contains("logo")) {
            return "";
        }
        return value;
    }
}
