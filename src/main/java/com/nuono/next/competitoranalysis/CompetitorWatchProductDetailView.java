package com.nuono.next.competitoranalysis;

import java.util.ArrayList;
import java.util.List;

public class CompetitorWatchProductDetailView {
    private CompetitorWatchProductView watchProduct;
    private List<CompetitorKeywordRow> keywords = new ArrayList<>();
    private List<CompetitorProductRow> candidates = new ArrayList<>();
    private List<CompetitorKeywordProductRow> keywordRelations = new ArrayList<>();
    private List<CompetitorLatestRankPointRow> latestRankPoints = new ArrayList<>();

    public static CompetitorWatchProductDetailView fromRows(
            CompetitorWatchProductRow watchProduct,
            List<CompetitorKeywordRow> keywords,
            List<CompetitorProductRow> candidates,
            List<CompetitorKeywordProductRow> keywordRelations,
            List<CompetitorLatestRankPointRow> latestRankPoints
    ) {
        CompetitorWatchProductDetailView view = new CompetitorWatchProductDetailView();
        view.setWatchProduct(CompetitorWatchProductView.fromRow(watchProduct));
        view.setKeywords(keywords);
        view.setCandidates(candidates);
        view.setKeywordRelations(keywordRelations);
        view.setLatestRankPoints(latestRankPoints);
        return view;
    }

    public CompetitorWatchProductView getWatchProduct() { return watchProduct; }
    public void setWatchProduct(CompetitorWatchProductView watchProduct) { this.watchProduct = watchProduct; }
    public List<CompetitorKeywordRow> getKeywords() { return keywords; }
    public void setKeywords(List<CompetitorKeywordRow> keywords) { this.keywords = keywords == null ? new ArrayList<>() : keywords; }
    public List<CompetitorProductRow> getCandidates() { return candidates; }
    public void setCandidates(List<CompetitorProductRow> candidates) { this.candidates = candidates == null ? new ArrayList<>() : candidates; }
    public List<CompetitorKeywordProductRow> getKeywordRelations() { return keywordRelations; }
    public void setKeywordRelations(List<CompetitorKeywordProductRow> keywordRelations) {
        this.keywordRelations = keywordRelations == null ? new ArrayList<>() : keywordRelations;
    }
    public List<CompetitorLatestRankPointRow> getLatestRankPoints() { return latestRankPoints; }
    public void setLatestRankPoints(List<CompetitorLatestRankPointRow> latestRankPoints) {
        this.latestRankPoints = latestRankPoints == null ? new ArrayList<>() : latestRankPoints;
    }
}
