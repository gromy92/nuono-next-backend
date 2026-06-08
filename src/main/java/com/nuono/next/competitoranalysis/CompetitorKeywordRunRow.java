package com.nuono.next.competitoranalysis;

import java.time.LocalDateTime;

public class CompetitorKeywordRunRow {
    private Long id;
    private Long searchRunId;
    private Long keywordId;
    private String keywordSnapshot;
    private LocalDateTime capturedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSearchRunId() { return searchRunId; }
    public void setSearchRunId(Long searchRunId) { this.searchRunId = searchRunId; }
    public Long getKeywordId() { return keywordId; }
    public void setKeywordId(Long keywordId) { this.keywordId = keywordId; }
    public String getKeywordSnapshot() { return keywordSnapshot; }
    public void setKeywordSnapshot(String keywordSnapshot) { this.keywordSnapshot = keywordSnapshot; }
    public LocalDateTime getCapturedAt() { return capturedAt; }
    public void setCapturedAt(LocalDateTime capturedAt) { this.capturedAt = capturedAt; }
}
