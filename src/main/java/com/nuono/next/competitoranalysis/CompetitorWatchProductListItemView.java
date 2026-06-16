package com.nuono.next.competitoranalysis;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CompetitorWatchProductListItemView extends CompetitorWatchProductView {
    private int activeKeywordCount;
    private List<String> activeKeywords = List.of();
    private List<CompetitorKeywordCountView> activeKeywordStats = List.of();
    private int pendingCandidateCount;
    private int confirmedCompetitorCount;
    private int recent7dChangedCompetitorCount;
    private int recent7dCompetitorChangeCount;

    public static CompetitorWatchProductListItemView fromRow(CompetitorWatchProductListRow row) {
        CompetitorWatchProductListItemView view = new CompetitorWatchProductListItemView();
        view.setId(row.getId());
        view.setOwnerUserId(row.getOwnerUserId());
        view.setStoreCode(row.getStoreCode());
        view.setSiteCode(row.getSiteCode());
        view.setProductSiteOfferId(row.getProductSiteOfferId());
        view.setSkuParent(row.getSkuParent());
        view.setPartnerSku(row.getPartnerSku());
        view.setChildSku(row.getChildSku());
        view.setSelfNoonProductCode(row.getSelfNoonProductCode());
        view.setSelfCodeType(row.getSelfCodeType());
        view.setTitle(row.getTitleSnapshot());
        view.setTitleCn(row.getTitleCnSnapshot());
        view.setBrand(row.getBrandSnapshot());
        view.setImageUrl(row.getImageUrlSnapshot());
        view.setProductFulltype(row.getProductFulltypeSnapshot());
        view.setStatus(row.getStatus());
        view.setLatestRunId(row.getLatestRunId());
        view.setLatestRunStatus(row.getLatestRunStatus());
        view.setLatestRunAt(row.getLatestRunAt());
        view.setActiveKeywordCount(row.getActiveKeywordCount());
        view.setActiveKeywords(splitActiveKeywords(row.getActiveKeywordSummary()));
        view.setActiveKeywordStats(splitActiveKeywordStats(row.getActiveKeywordSummary()));
        view.setPendingCandidateCount(row.getPendingCandidateCount());
        view.setConfirmedCompetitorCount(row.getConfirmedCompetitorCount());
        view.setRecent7dChangedCompetitorCount(row.getRecent7dChangedCompetitorCount());
        view.setRecent7dCompetitorChangeCount(row.getRecent7dCompetitorChangeCount());
        return view;
    }

    private static List<String> splitActiveKeywords(String summary) {
        if (summary == null || summary.isBlank()) {
            return List.of();
        }
        return Arrays.stream(summary.split("\\|\\|"))
                .map(CompetitorWatchProductListItemView::extractKeyword)
                .filter(item -> !item.isEmpty())
                .collect(Collectors.toList());
    }

    private static List<CompetitorKeywordCountView> splitActiveKeywordStats(String summary) {
        if (summary == null || summary.isBlank()) {
            return List.of();
        }
        return Arrays.stream(summary.split("\\|\\|"))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .map(item -> {
                    String[] parts = item.split("\\t", 2);
                    String keyword = parts.length > 0 ? parts[0].trim() : "";
                    int monitoredCount = parts.length > 1 ? parseCount(parts[1]) : 0;
                    return CompetitorKeywordCountView.of(keyword, monitoredCount);
                })
                .filter(item -> item.getKeyword() != null && !item.getKeyword().isBlank())
                .collect(Collectors.toList());
    }

    private static String extractKeyword(String item) {
        String trimmed = item == null ? "" : item.trim();
        int tabIndex = trimmed.indexOf('\t');
        return tabIndex >= 0 ? trimmed.substring(0, tabIndex).trim() : trimmed;
    }

    private static int parseCount(String value) {
        try {
            return Integer.parseInt(value == null ? "0" : value.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public int getActiveKeywordCount() { return activeKeywordCount; }
    public void setActiveKeywordCount(int activeKeywordCount) { this.activeKeywordCount = activeKeywordCount; }
    public List<String> getActiveKeywords() { return activeKeywords; }
    public void setActiveKeywords(List<String> activeKeywords) {
        this.activeKeywords = activeKeywords == null ? List.of() : activeKeywords;
    }
    public List<CompetitorKeywordCountView> getActiveKeywordStats() { return activeKeywordStats; }
    public void setActiveKeywordStats(List<CompetitorKeywordCountView> activeKeywordStats) {
        this.activeKeywordStats = activeKeywordStats == null ? List.of() : activeKeywordStats;
    }
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
