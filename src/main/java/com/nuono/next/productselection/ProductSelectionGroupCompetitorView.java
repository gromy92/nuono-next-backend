package com.nuono.next.productselection;

import java.util.ArrayList;
import java.util.List;

public class ProductSelectionGroupCompetitorView {

    private String id;
    private String groupId;
    private String url;
    private String note;
    private String fetchStatus;
    private String fetchedTitle;
    private String fetchedTitleAr;
    private String fetchedSourceImageUrl;
    private List<String> fetchedImageUrls = new ArrayList<>();
    private String fetchedDescriptionEn;
    private String fetchedDescriptionAr;
    private List<String> fetchedSellingPointsEn = new ArrayList<>();
    private List<String> fetchedSellingPointsAr = new ArrayList<>();
    private String fetchedSourceHost;
    private String fetchedPriceSummary;
    private String fetchedCompleteness;
    private String fetchedCollectionSource;
    private String fetchedAt;
    private String fetchMessage;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
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

    public String getFetchStatus() {
        return fetchStatus;
    }

    public void setFetchStatus(String fetchStatus) {
        this.fetchStatus = fetchStatus;
    }

    public String getFetchedTitle() {
        return fetchedTitle;
    }

    public void setFetchedTitle(String fetchedTitle) {
        this.fetchedTitle = fetchedTitle;
    }

    public String getFetchedTitleAr() {
        return fetchedTitleAr;
    }

    public void setFetchedTitleAr(String fetchedTitleAr) {
        this.fetchedTitleAr = fetchedTitleAr;
    }

    public String getFetchedSourceImageUrl() {
        return fetchedSourceImageUrl;
    }

    public void setFetchedSourceImageUrl(String fetchedSourceImageUrl) {
        this.fetchedSourceImageUrl = fetchedSourceImageUrl;
    }

    public List<String> getFetchedImageUrls() {
        return fetchedImageUrls;
    }

    public void setFetchedImageUrls(List<String> fetchedImageUrls) {
        this.fetchedImageUrls = fetchedImageUrls == null
                ? new ArrayList<>()
                : new ArrayList<>(fetchedImageUrls);
    }

    public String getFetchedDescriptionEn() {
        return fetchedDescriptionEn;
    }

    public void setFetchedDescriptionEn(String fetchedDescriptionEn) {
        this.fetchedDescriptionEn = fetchedDescriptionEn;
    }

    public String getFetchedDescriptionAr() {
        return fetchedDescriptionAr;
    }

    public void setFetchedDescriptionAr(String fetchedDescriptionAr) {
        this.fetchedDescriptionAr = fetchedDescriptionAr;
    }

    public List<String> getFetchedSellingPointsEn() {
        return fetchedSellingPointsEn;
    }

    public void setFetchedSellingPointsEn(List<String> fetchedSellingPointsEn) {
        this.fetchedSellingPointsEn = fetchedSellingPointsEn == null
                ? new ArrayList<>()
                : new ArrayList<>(fetchedSellingPointsEn);
    }

    public List<String> getFetchedSellingPointsAr() {
        return fetchedSellingPointsAr;
    }

    public void setFetchedSellingPointsAr(List<String> fetchedSellingPointsAr) {
        this.fetchedSellingPointsAr = fetchedSellingPointsAr == null
                ? new ArrayList<>()
                : new ArrayList<>(fetchedSellingPointsAr);
    }

    public String getFetchedSourceHost() {
        return fetchedSourceHost;
    }

    public void setFetchedSourceHost(String fetchedSourceHost) {
        this.fetchedSourceHost = fetchedSourceHost;
    }

    public String getFetchedPriceSummary() {
        return fetchedPriceSummary;
    }

    public void setFetchedPriceSummary(String fetchedPriceSummary) {
        this.fetchedPriceSummary = fetchedPriceSummary;
    }

    public String getFetchedCompleteness() {
        return fetchedCompleteness;
    }

    public void setFetchedCompleteness(String fetchedCompleteness) {
        this.fetchedCompleteness = fetchedCompleteness;
    }

    public String getFetchedCollectionSource() {
        return fetchedCollectionSource;
    }

    public void setFetchedCollectionSource(String fetchedCollectionSource) {
        this.fetchedCollectionSource = fetchedCollectionSource;
    }

    public String getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(String fetchedAt) {
        this.fetchedAt = fetchedAt;
    }

    public String getFetchMessage() {
        return fetchMessage;
    }

    public void setFetchMessage(String fetchMessage) {
        this.fetchMessage = fetchMessage;
    }
}
