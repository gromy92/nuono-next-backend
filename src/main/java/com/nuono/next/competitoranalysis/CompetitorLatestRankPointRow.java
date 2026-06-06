package com.nuono.next.competitoranalysis;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CompetitorLatestRankPointRow {
    private Long keywordId;
    private String keyword;
    private String trackedProductType;
    private String noonProductCode;
    private String rankStatus;
    private Integer rankNo;
    private Boolean sponsored;
    private BigDecimal priceAmount;
    private String currencyCode;
    private LocalDateTime factTime;

    public Long getKeywordId() { return keywordId; }
    public void setKeywordId(Long keywordId) { this.keywordId = keywordId; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getTrackedProductType() { return trackedProductType; }
    public void setTrackedProductType(String trackedProductType) { this.trackedProductType = trackedProductType; }
    public String getNoonProductCode() { return noonProductCode; }
    public void setNoonProductCode(String noonProductCode) { this.noonProductCode = noonProductCode; }
    public String getRankStatus() { return rankStatus; }
    public void setRankStatus(String rankStatus) { this.rankStatus = rankStatus; }
    public Integer getRankNo() { return rankNo; }
    public void setRankNo(Integer rankNo) { this.rankNo = rankNo; }
    public Boolean getSponsored() { return sponsored; }
    public void setSponsored(Boolean sponsored) { this.sponsored = sponsored; }
    public BigDecimal getPriceAmount() { return priceAmount; }
    public void setPriceAmount(BigDecimal priceAmount) { this.priceAmount = priceAmount; }
    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public LocalDateTime getFactTime() { return factTime; }
    public void setFactTime(LocalDateTime factTime) { this.factTime = factTime; }
}
