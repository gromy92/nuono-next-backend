package com.nuono.next.productselection;

import java.util.ArrayList;
import java.util.List;

class NoonPublicProductSnapshot {

    String title;
    String sku;
    String brand;
    String priceSummary;
    String rating;
    String reviewCount;
    String longDescription;
    final List<String> imageUrls = new ArrayList<>();
    final List<String> featureBullets = new ArrayList<>();
    final List<String> specHints = new ArrayList<>();

    boolean hasStableProductData() {
        return hasText(title) || hasText(sku) || !imageUrls.isEmpty();
    }

    void addImageUrl(String value) {
        addUnique(imageUrls, value);
    }

    void addFeatureBullet(String value) {
        addUnique(featureBullets, value);
    }

    void addSpecHint(String value) {
        addUnique(specHints, value);
    }

    void mergeMissingFrom(NoonPublicProductSnapshot fallback) {
        if (fallback == null) {
            return;
        }
        title = firstText(title, fallback.title);
        sku = firstText(sku, fallback.sku);
        brand = firstText(brand, fallback.brand);
        priceSummary = firstText(priceSummary, fallback.priceSummary);
        rating = firstText(rating, fallback.rating);
        reviewCount = firstText(reviewCount, fallback.reviewCount);
        longDescription = firstText(longDescription, fallback.longDescription);
        fallback.imageUrls.forEach(this::addImageUrl);
        fallback.featureBullets.forEach(this::addFeatureBullet);
        fallback.specHints.forEach(this::addSpecHint);
    }

    private void addUnique(List<String> target, String value) {
        String text = compactText(value);
        if (!hasText(text) || target.contains(text)) {
            return;
        }
        target.add(text);
    }

    private String firstText(String first, String second) {
        return hasText(first) ? first : compactText(second);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String compactText(String value) {
        return value == null ? "" : value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }
}
