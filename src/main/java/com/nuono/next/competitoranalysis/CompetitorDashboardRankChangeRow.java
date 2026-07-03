package com.nuono.next.competitoranalysis;

import java.time.LocalDate;

public class CompetitorDashboardRankChangeRow {

    private Long watchProductId;
    private Long productSiteOfferId;
    private String partnerSku;
    private String title;
    private String imageUrl;
    private Long keywordId;
    private String keyword;
    private String trackedProductType;
    private String noonProductCode;
    private String previousRankStatus;
    private Integer previousRankNo;
    private LocalDate previousDate;
    private String rankStatus;
    private Integer rankNo;
    private LocalDate currentDate;
    private Integer rankDelta;
    private String priceChangeSummary;
    private String titleChangeSummary;
    private String adChangeSummary;

    public Long getWatchProductId() { return watchProductId; }
    public void setWatchProductId(Long watchProductId) { this.watchProductId = watchProductId; }

    public Long getProductSiteOfferId() { return productSiteOfferId; }
    public void setProductSiteOfferId(Long productSiteOfferId) { this.productSiteOfferId = productSiteOfferId; }

    public String getPartnerSku() { return partnerSku; }
    public void setPartnerSku(String partnerSku) { this.partnerSku = partnerSku; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public Long getKeywordId() { return keywordId; }
    public void setKeywordId(Long keywordId) { this.keywordId = keywordId; }

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }

    public String getTrackedProductType() { return trackedProductType; }
    public void setTrackedProductType(String trackedProductType) { this.trackedProductType = trackedProductType; }

    public String getNoonProductCode() { return noonProductCode; }
    public void setNoonProductCode(String noonProductCode) { this.noonProductCode = noonProductCode; }

    public String getPreviousRankStatus() { return previousRankStatus; }
    public void setPreviousRankStatus(String previousRankStatus) { this.previousRankStatus = previousRankStatus; }

    public Integer getPreviousRankNo() { return previousRankNo; }
    public void setPreviousRankNo(Integer previousRankNo) { this.previousRankNo = previousRankNo; }

    public LocalDate getPreviousDate() { return previousDate; }
    public void setPreviousDate(LocalDate previousDate) { this.previousDate = previousDate; }

    public String getRankStatus() { return rankStatus; }
    public void setRankStatus(String rankStatus) { this.rankStatus = rankStatus; }

    public Integer getRankNo() { return rankNo; }
    public void setRankNo(Integer rankNo) { this.rankNo = rankNo; }

    public LocalDate getCurrentDate() { return currentDate; }
    public void setCurrentDate(LocalDate currentDate) { this.currentDate = currentDate; }

    public Integer getRankDelta() { return rankDelta; }
    public void setRankDelta(Integer rankDelta) { this.rankDelta = rankDelta; }

    public String getPriceChangeSummary() { return priceChangeSummary; }
    public void setPriceChangeSummary(String priceChangeSummary) { this.priceChangeSummary = priceChangeSummary; }

    public String getTitleChangeSummary() { return titleChangeSummary; }
    public void setTitleChangeSummary(String titleChangeSummary) { this.titleChangeSummary = titleChangeSummary; }

    public String getAdChangeSummary() { return adChangeSummary; }
    public void setAdChangeSummary(String adChangeSummary) { this.adChangeSummary = adChangeSummary; }
}
