package com.nuono.next.competitoranalysis;

public class CompetitorWatchProductListRow extends CompetitorWatchProductRow {
    private int activeKeywordCount;
    private String activeKeywordSummary;
    private int pendingCandidateCount;
    private int confirmedCompetitorCount;
    private int recent7dChangedCompetitorCount;
    private int recent7dCompetitorChangeCount;

    public int getActiveKeywordCount() { return activeKeywordCount; }
    public void setActiveKeywordCount(int activeKeywordCount) { this.activeKeywordCount = activeKeywordCount; }
    public String getActiveKeywordSummary() { return activeKeywordSummary; }
    public void setActiveKeywordSummary(String activeKeywordSummary) { this.activeKeywordSummary = activeKeywordSummary; }
    public int getPendingCandidateCount() { return pendingCandidateCount; }
    public void setPendingCandidateCount(int pendingCandidateCount) { this.pendingCandidateCount = pendingCandidateCount; }
    public int getConfirmedCompetitorCount() { return confirmedCompetitorCount; }
    public void setConfirmedCompetitorCount(int confirmedCompetitorCount) { this.confirmedCompetitorCount = confirmedCompetitorCount; }
    public int getRecent7dChangedCompetitorCount() { return recent7dChangedCompetitorCount; }
    public void setRecent7dChangedCompetitorCount(int recent7dChangedCompetitorCount) {
        this.recent7dChangedCompetitorCount = recent7dChangedCompetitorCount;
    }
    public int getRecent7dCompetitorChangeCount() { return recent7dCompetitorChangeCount; }
    public void setRecent7dCompetitorChangeCount(int recent7dCompetitorChangeCount) {
        this.recent7dCompetitorChangeCount = recent7dCompetitorChangeCount;
    }
}
