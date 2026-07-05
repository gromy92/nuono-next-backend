package com.nuono.next.productselection;

import java.util.ArrayList;
import java.util.List;

public class ProductSelectionAnalysisCommand {

    private List<CompetitorContext> competitors = new ArrayList<>();
    private List<Ali1688CandidateContext> ali1688Candidates = new ArrayList<>();
    private ProfitEstimateContext profitEstimate;

    public List<CompetitorContext> getCompetitors() {
        return competitors;
    }

    public void setCompetitors(List<CompetitorContext> competitors) {
        this.competitors = competitors == null ? new ArrayList<>() : new ArrayList<>(competitors);
    }

    public List<Ali1688CandidateContext> getAli1688Candidates() {
        return ali1688Candidates;
    }

    public void setAli1688Candidates(List<Ali1688CandidateContext> ali1688Candidates) {
        this.ali1688Candidates = ali1688Candidates == null ? new ArrayList<>() : new ArrayList<>(ali1688Candidates);
    }

    public ProfitEstimateContext getProfitEstimate() {
        return profitEstimate;
    }

    public void setProfitEstimate(ProfitEstimateContext profitEstimate) {
        this.profitEstimate = profitEstimate;
    }

    public static class CompetitorContext {
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

    public static class Ali1688CandidateContext {
        private String title;
        private String supplierName;
        private String candidateUrl;
        private String priceText;
        private String moqText;
        private String level;
        private Integer totalScore;
        private List<String> reasons = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getSupplierName() {
            return supplierName;
        }

        public void setSupplierName(String supplierName) {
            this.supplierName = supplierName;
        }

        public String getCandidateUrl() {
            return candidateUrl;
        }

        public void setCandidateUrl(String candidateUrl) {
            this.candidateUrl = candidateUrl;
        }

        public String getPriceText() {
            return priceText;
        }

        public void setPriceText(String priceText) {
            this.priceText = priceText;
        }

        public String getMoqText() {
            return moqText;
        }

        public void setMoqText(String moqText) {
            this.moqText = moqText;
        }

        public String getLevel() {
            return level;
        }

        public void setLevel(String level) {
            this.level = level;
        }

        public Integer getTotalScore() {
            return totalScore;
        }

        public void setTotalScore(Integer totalScore) {
            this.totalScore = totalScore;
        }

        public List<String> getReasons() {
            return reasons;
        }

        public void setReasons(List<String> reasons) {
            this.reasons = reasons == null ? new ArrayList<>() : new ArrayList<>(reasons);
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public void setWarnings(List<String> warnings) {
            this.warnings = warnings == null ? new ArrayList<>() : new ArrayList<>(warnings);
        }
    }

    public static class ProfitEstimateContext {
        private String status;
        private String estimatedProfitText;
        private String marginText;
        private List<String> warnings = new ArrayList<>();

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getEstimatedProfitText() {
            return estimatedProfitText;
        }

        public void setEstimatedProfitText(String estimatedProfitText) {
            this.estimatedProfitText = estimatedProfitText;
        }

        public String getMarginText() {
            return marginText;
        }

        public void setMarginText(String marginText) {
            this.marginText = marginText;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public void setWarnings(List<String> warnings) {
            this.warnings = warnings == null ? new ArrayList<>() : new ArrayList<>(warnings);
        }
    }
}
