package com.nuono.next.competitoranalysis;

public class CompetitorWatchProductListRow extends CompetitorWatchProductRow {
    private int activeKeywordCount;
    private int pendingCandidateCount;
    private int confirmedCompetitorCount;

    public int getActiveKeywordCount() { return activeKeywordCount; }
    public void setActiveKeywordCount(int activeKeywordCount) { this.activeKeywordCount = activeKeywordCount; }
    public int getPendingCandidateCount() { return pendingCandidateCount; }
    public void setPendingCandidateCount(int pendingCandidateCount) { this.pendingCandidateCount = pendingCandidateCount; }
    public int getConfirmedCompetitorCount() { return confirmedCompetitorCount; }
    public void setConfirmedCompetitorCount(int confirmedCompetitorCount) { this.confirmedCompetitorCount = confirmedCompetitorCount; }
}
