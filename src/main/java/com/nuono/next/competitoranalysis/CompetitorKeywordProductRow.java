package com.nuono.next.competitoranalysis;

import java.time.LocalDateTime;

public class CompetitorKeywordProductRow {
    private Long id;
    private Long keywordId;
    private Long competitorProductId;
    private String relationStatus;
    private Integer firstSeenRankNo;
    private Integer lastSeenRankNo;
    private Boolean lastSeenSponsored;
    private LocalDateTime lastSeenAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getKeywordId() { return keywordId; }
    public void setKeywordId(Long keywordId) { this.keywordId = keywordId; }
    public Long getCompetitorProductId() { return competitorProductId; }
    public void setCompetitorProductId(Long competitorProductId) { this.competitorProductId = competitorProductId; }
    public String getRelationStatus() { return relationStatus; }
    public void setRelationStatus(String relationStatus) { this.relationStatus = relationStatus; }
    public Integer getFirstSeenRankNo() { return firstSeenRankNo; }
    public void setFirstSeenRankNo(Integer firstSeenRankNo) { this.firstSeenRankNo = firstSeenRankNo; }
    public Integer getLastSeenRankNo() { return lastSeenRankNo; }
    public void setLastSeenRankNo(Integer lastSeenRankNo) { this.lastSeenRankNo = lastSeenRankNo; }
    public Boolean getLastSeenSponsored() { return lastSeenSponsored; }
    public void setLastSeenSponsored(Boolean lastSeenSponsored) { this.lastSeenSponsored = lastSeenSponsored; }
    public LocalDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(LocalDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }
}
