package com.nuono.next.competitoranalysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class CompetitorRankFactInsertCommand {
    private Long id;
    private Long watchProductId;
    private Long keywordId;
    private Long keywordRunId;
    private Long searchRunId;
    private LocalDateTime factTime;
    private LocalDate factDate;
    private String trackedProductType;
    private String noonProductCode;
    private String rankStatus;
    private Integer rankNo;
    private Boolean sponsored;
    private BigDecimal priceAmount;
    private String currencyCode;
    private BigDecimal rating;
    private Integer reviewCount;
    private Long sourceResultId;
    private Long actorUserId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getWatchProductId() { return watchProductId; }
    public void setWatchProductId(Long watchProductId) { this.watchProductId = watchProductId; }
    public Long getKeywordId() { return keywordId; }
    public void setKeywordId(Long keywordId) { this.keywordId = keywordId; }
    public Long getKeywordRunId() { return keywordRunId; }
    public void setKeywordRunId(Long keywordRunId) { this.keywordRunId = keywordRunId; }
    public Long getSearchRunId() { return searchRunId; }
    public void setSearchRunId(Long searchRunId) { this.searchRunId = searchRunId; }
    public LocalDateTime getFactTime() { return factTime; }
    public void setFactTime(LocalDateTime factTime) { this.factTime = factTime; }
    public LocalDate getFactDate() { return factDate; }
    public void setFactDate(LocalDate factDate) { this.factDate = factDate; }
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
    public BigDecimal getRating() { return rating; }
    public void setRating(BigDecimal rating) { this.rating = rating; }
    public Integer getReviewCount() { return reviewCount; }
    public void setReviewCount(Integer reviewCount) { this.reviewCount = reviewCount; }
    public Long getSourceResultId() { return sourceResultId; }
    public void setSourceResultId(Long sourceResultId) { this.sourceResultId = sourceResultId; }
    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }
}
