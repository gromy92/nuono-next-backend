package com.nuono.next.product;

import java.util.ArrayList;
import java.util.List;

public class ProductImageAiExtractionSuggestionView {
    private String specSummary;
    private String titleEn;
    private String titleAr;
    private String sizeText;
    private List<String> heroSellingPoints = new ArrayList<>();
    private String packageText;

    public String getSpecSummary() {
        return specSummary;
    }

    public void setSpecSummary(String specSummary) {
        this.specSummary = specSummary;
    }

    public String getTitleEn() {
        return titleEn;
    }

    public void setTitleEn(String titleEn) {
        this.titleEn = titleEn;
    }

    public String getTitleAr() {
        return titleAr;
    }

    public void setTitleAr(String titleAr) {
        this.titleAr = titleAr;
    }

    public String getSizeText() {
        return sizeText;
    }

    public void setSizeText(String sizeText) {
        this.sizeText = sizeText;
    }

    public List<String> getHeroSellingPoints() {
        return heroSellingPoints;
    }

    public void setHeroSellingPoints(List<String> heroSellingPoints) {
        this.heroSellingPoints = heroSellingPoints == null ? new ArrayList<>() : new ArrayList<>(heroSellingPoints);
    }

    public String getPackageText() {
        return packageText;
    }

    public void setPackageText(String packageText) {
        this.packageText = packageText;
    }
}
