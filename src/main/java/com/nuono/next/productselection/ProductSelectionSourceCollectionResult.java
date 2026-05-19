package com.nuono.next.productselection;

import java.util.ArrayList;
import java.util.List;

public class ProductSelectionSourceCollectionResult {

    private String sourcePlatform;
    private String sourceUrl;
    private String pageUrl;
    private String sourceTitle;
    private String sourceTitleCn;
    private String sourceTitleAr;
    private String sourceImageUrl;
    private List<String> imageUrls = new ArrayList<>();
    private String priceSummary;
    private String moqHint;
    private String shippingFrom;
    private List<String> specHints = new ArrayList<>();
    private String sourceDescriptionEn;
    private String sourceDescriptionAr;
    private List<String> sourceSellingPointsEn = new ArrayList<>();
    private List<String> sourceSellingPointsAr = new ArrayList<>();
    private String selectedText;
    private String selectedTextAr;

    public String getSourcePlatform() {
        return sourcePlatform;
    }

    public void setSourcePlatform(String sourcePlatform) {
        this.sourcePlatform = sourcePlatform;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getPageUrl() {
        return pageUrl;
    }

    public void setPageUrl(String pageUrl) {
        this.pageUrl = pageUrl;
    }

    public String getSourceTitle() {
        return sourceTitle;
    }

    public void setSourceTitle(String sourceTitle) {
        this.sourceTitle = sourceTitle;
    }

    public String getSourceTitleCn() {
        return sourceTitleCn;
    }

    public void setSourceTitleCn(String sourceTitleCn) {
        this.sourceTitleCn = sourceTitleCn;
    }

    public String getSourceTitleAr() {
        return sourceTitleAr;
    }

    public void setSourceTitleAr(String sourceTitleAr) {
        this.sourceTitleAr = sourceTitleAr;
    }

    public String getSourceImageUrl() {
        return sourceImageUrl;
    }

    public void setSourceImageUrl(String sourceImageUrl) {
        this.sourceImageUrl = sourceImageUrl;
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }

    public String getPriceSummary() {
        return priceSummary;
    }

    public void setPriceSummary(String priceSummary) {
        this.priceSummary = priceSummary;
    }

    public String getMoqHint() {
        return moqHint;
    }

    public void setMoqHint(String moqHint) {
        this.moqHint = moqHint;
    }

    public String getShippingFrom() {
        return shippingFrom;
    }

    public void setShippingFrom(String shippingFrom) {
        this.shippingFrom = shippingFrom;
    }

    public List<String> getSpecHints() {
        return specHints;
    }

    public void setSpecHints(List<String> specHints) {
        this.specHints = specHints;
    }

    public String getSourceDescriptionEn() {
        return sourceDescriptionEn;
    }

    public void setSourceDescriptionEn(String sourceDescriptionEn) {
        this.sourceDescriptionEn = sourceDescriptionEn;
    }

    public String getSourceDescriptionAr() {
        return sourceDescriptionAr;
    }

    public void setSourceDescriptionAr(String sourceDescriptionAr) {
        this.sourceDescriptionAr = sourceDescriptionAr;
    }

    public List<String> getSourceSellingPointsEn() {
        return sourceSellingPointsEn;
    }

    public void setSourceSellingPointsEn(List<String> sourceSellingPointsEn) {
        this.sourceSellingPointsEn = sourceSellingPointsEn;
    }

    public List<String> getSourceSellingPointsAr() {
        return sourceSellingPointsAr;
    }

    public void setSourceSellingPointsAr(List<String> sourceSellingPointsAr) {
        this.sourceSellingPointsAr = sourceSellingPointsAr;
    }

    public String getSelectedText() {
        return selectedText;
    }

    public void setSelectedText(String selectedText) {
        this.selectedText = selectedText;
    }

    public String getSelectedTextAr() {
        return selectedTextAr;
    }

    public void setSelectedTextAr(String selectedTextAr) {
        this.selectedTextAr = selectedTextAr;
    }
}
