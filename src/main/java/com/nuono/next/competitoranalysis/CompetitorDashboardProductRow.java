package com.nuono.next.competitoranalysis;

public class CompetitorDashboardProductRow {

    private Long watchProductId;
    private Long productSiteOfferId;
    private String partnerSku;
    private String title;
    private String issueType;
    private Long value;
    private Long targetValue;
    private String changeType;

    public Long getWatchProductId() {
        return watchProductId;
    }

    public void setWatchProductId(Long watchProductId) {
        this.watchProductId = watchProductId;
    }

    public Long getProductSiteOfferId() {
        return productSiteOfferId;
    }

    public void setProductSiteOfferId(Long productSiteOfferId) {
        this.productSiteOfferId = productSiteOfferId;
    }

    public String getPartnerSku() {
        return partnerSku;
    }

    public void setPartnerSku(String partnerSku) {
        this.partnerSku = partnerSku;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getIssueType() {
        return issueType;
    }

    public void setIssueType(String issueType) {
        this.issueType = issueType;
    }

    public Long getValue() {
        return value;
    }

    public void setValue(Long value) {
        this.value = value;
    }

    public Long getTargetValue() {
        return targetValue;
    }

    public void setTargetValue(Long targetValue) {
        this.targetValue = targetValue;
    }

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }
}
