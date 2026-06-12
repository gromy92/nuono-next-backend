package com.nuono.next.competitoranalysis;

public class CompetitorKeywordProductSearchCommand {
    private Long id;
    private Long keywordId;
    private Long competitorProductId;
    private String relationStatus;
    private Long searchRunId;
    private Integer rankNo;
    private Boolean sponsored;
    private Long actorUserId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getKeywordId() { return keywordId; }
    public void setKeywordId(Long keywordId) { this.keywordId = keywordId; }
    public Long getCompetitorProductId() { return competitorProductId; }
    public void setCompetitorProductId(Long competitorProductId) { this.competitorProductId = competitorProductId; }
    public String getRelationStatus() { return relationStatus; }
    public void setRelationStatus(String relationStatus) { this.relationStatus = relationStatus; }
    public Long getSearchRunId() { return searchRunId; }
    public void setSearchRunId(Long searchRunId) { this.searchRunId = searchRunId; }
    public Integer getRankNo() { return rankNo; }
    public void setRankNo(Integer rankNo) { this.rankNo = rankNo; }
    public Boolean getSponsored() { return sponsored; }
    public void setSponsored(Boolean sponsored) { this.sponsored = sponsored; }
    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }
}
