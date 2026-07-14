package com.nuono.next.productlisting;

import java.util.ArrayList;
import java.util.List;

public class ProductListingAiCompetitorMaterial {

    private String id;
    private String url;
    private String note;
    private String sourceHost;
    private String fetchedAt;
    private String titleEn;
    private String titleAr;
    private String descriptionEn;
    private String descriptionAr;
    private List<String> sellingPointsEn = new ArrayList<>();
    private List<String> sellingPointsAr = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getSourceHost() {
        return sourceHost;
    }

    public void setSourceHost(String sourceHost) {
        this.sourceHost = sourceHost;
    }

    public String getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(String fetchedAt) {
        this.fetchedAt = fetchedAt;
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

    public String getDescriptionEn() {
        return descriptionEn;
    }

    public void setDescriptionEn(String descriptionEn) {
        this.descriptionEn = descriptionEn;
    }

    public String getDescriptionAr() {
        return descriptionAr;
    }

    public void setDescriptionAr(String descriptionAr) {
        this.descriptionAr = descriptionAr;
    }

    public List<String> getSellingPointsEn() {
        return sellingPointsEn;
    }

    public void setSellingPointsEn(List<String> sellingPointsEn) {
        this.sellingPointsEn = sellingPointsEn == null ? new ArrayList<>() : new ArrayList<>(sellingPointsEn);
    }

    public List<String> getSellingPointsAr() {
        return sellingPointsAr;
    }

    public void setSellingPointsAr(List<String> sellingPointsAr) {
        this.sellingPointsAr = sellingPointsAr == null ? new ArrayList<>() : new ArrayList<>(sellingPointsAr);
    }
}
