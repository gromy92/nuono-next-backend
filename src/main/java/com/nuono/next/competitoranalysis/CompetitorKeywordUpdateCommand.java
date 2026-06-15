package com.nuono.next.competitoranalysis;

public class CompetitorKeywordUpdateCommand {
    private Long id;
    private String keyword;
    private String keywordNorm;
    private String locale;
    private String status;
    private Integer displayOrder;
    private Long actorUserId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getKeywordNorm() { return keywordNorm; }
    public void setKeywordNorm(String keywordNorm) { this.keywordNorm = keywordNorm; }
    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }
}
