package com.nuono.next.competitoranalysis;

public class CompetitorWatchProductListRow extends CompetitorWatchProductRow {
    private int activeKeywordCount;
    private String activeKeywordSummary;
    private int pendingCandidateCount;
    private int confirmedCompetitorCount;

    public int getActiveKeywordCount() { return activeKeywordCount; }
    public void setActiveKeywordCount(int activeKeywordCount) { this.activeKeywordCount = activeKeywordCount; }
    public String getActiveKeywordSummary() { return activeKeywordSummary; }
    public void setActiveKeywordSummary(String activeKeywordSummary) { this.activeKeywordSummary = activeKeywordSummary; }
    public int getPendingCandidateCount() { return pendingCandidateCount; }
    public void setPendingCandidateCount(int pendingCandidateCount) { this.pendingCandidateCount = pendingCandidateCount; }
    public int getConfirmedCompetitorCount() { return confirmedCompetitorCount; }
    public void setConfirmedCompetitorCount(int confirmedCompetitorCount) { this.confirmedCompetitorCount = confirmedCompetitorCount; }
}
