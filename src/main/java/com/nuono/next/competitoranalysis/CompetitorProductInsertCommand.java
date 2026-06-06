package com.nuono.next.competitoranalysis;

public class CompetitorProductInsertCommand {
    private Long id;
    private Long watchProductId;
    private String noonProductCode;
    private String codeType;
    private String canonicalUrl;
    private String titleSnapshot;
    private String brandSnapshot;
    private String imageUrlSnapshot;
    private String sourceType;
    private String reviewStatus;
    private Long actorUserId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getWatchProductId() { return watchProductId; }
    public void setWatchProductId(Long watchProductId) { this.watchProductId = watchProductId; }
    public String getNoonProductCode() { return noonProductCode; }
    public void setNoonProductCode(String noonProductCode) { this.noonProductCode = noonProductCode; }
    public String getCodeType() { return codeType; }
    public void setCodeType(String codeType) { this.codeType = codeType; }
    public String getCanonicalUrl() { return canonicalUrl; }
    public void setCanonicalUrl(String canonicalUrl) { this.canonicalUrl = canonicalUrl; }
    public String getTitleSnapshot() { return titleSnapshot; }
    public void setTitleSnapshot(String titleSnapshot) { this.titleSnapshot = titleSnapshot; }
    public String getBrandSnapshot() { return brandSnapshot; }
    public void setBrandSnapshot(String brandSnapshot) { this.brandSnapshot = brandSnapshot; }
    public String getImageUrlSnapshot() { return imageUrlSnapshot; }
    public void setImageUrlSnapshot(String imageUrlSnapshot) { this.imageUrlSnapshot = imageUrlSnapshot; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }
    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }
}
