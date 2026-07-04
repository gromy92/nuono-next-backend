package com.nuono.next.preorderprofit;

public class PreOrderProfitCandidateCommand extends PreOrderProfitCalculationRequest {
    private String title;
    private String skuHint;
    private String candidateStatus;
    private String categoryLabel;
    private String logisticsCarrierLabel;
    private String notes;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSkuHint() {
        return skuHint;
    }

    public void setSkuHint(String skuHint) {
        this.skuHint = skuHint;
    }

    public String getCandidateStatus() {
        return candidateStatus;
    }

    public void setCandidateStatus(String candidateStatus) {
        this.candidateStatus = candidateStatus;
    }

    public String getCategoryLabel() {
        return categoryLabel;
    }

    public void setCategoryLabel(String categoryLabel) {
        this.categoryLabel = categoryLabel;
    }

    public String getLogisticsCarrierLabel() {
        return logisticsCarrierLabel;
    }

    public void setLogisticsCarrierLabel(String logisticsCarrierLabel) {
        this.logisticsCarrierLabel = logisticsCarrierLabel;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
