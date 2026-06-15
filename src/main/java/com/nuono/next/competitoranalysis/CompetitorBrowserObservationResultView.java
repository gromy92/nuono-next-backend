package com.nuono.next.competitoranalysis;

public class CompetitorBrowserObservationResultView {
    private int observedCount;
    private int sponsoredObservedCount;
    private int searchResultUpdatedCount;
    private int searchResultInsertedCount;
    private int competitorInsertedCount;
    private int relationUpsertedCount;
    private int rankFactUpdatedCount;

    public int getObservedCount() { return observedCount; }
    public void setObservedCount(int observedCount) { this.observedCount = observedCount; }
    public int getSponsoredObservedCount() { return sponsoredObservedCount; }
    public void setSponsoredObservedCount(int sponsoredObservedCount) { this.sponsoredObservedCount = sponsoredObservedCount; }
    public int getSearchResultUpdatedCount() { return searchResultUpdatedCount; }
    public void setSearchResultUpdatedCount(int searchResultUpdatedCount) { this.searchResultUpdatedCount = searchResultUpdatedCount; }
    public int getSearchResultInsertedCount() { return searchResultInsertedCount; }
    public void setSearchResultInsertedCount(int searchResultInsertedCount) { this.searchResultInsertedCount = searchResultInsertedCount; }
    public int getCompetitorInsertedCount() { return competitorInsertedCount; }
    public void setCompetitorInsertedCount(int competitorInsertedCount) { this.competitorInsertedCount = competitorInsertedCount; }
    public int getRelationUpsertedCount() { return relationUpsertedCount; }
    public void setRelationUpsertedCount(int relationUpsertedCount) { this.relationUpsertedCount = relationUpsertedCount; }
    public int getRankFactUpdatedCount() { return rankFactUpdatedCount; }
    public void setRankFactUpdatedCount(int rankFactUpdatedCount) { this.rankFactUpdatedCount = rankFactUpdatedCount; }
}
