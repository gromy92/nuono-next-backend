package com.nuono.next.competitoranalysis;

public class CompetitorWatchProductListItemView extends CompetitorWatchProductView {
    private int activeKeywordCount;
    private int pendingCandidateCount;
    private int confirmedCompetitorCount;

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
        view.setBrand(row.getBrandSnapshot());
        view.setImageUrl(row.getImageUrlSnapshot());
        view.setProductFulltype(row.getProductFulltypeSnapshot());
        view.setStatus(row.getStatus());
        view.setLatestRunId(row.getLatestRunId());
        view.setLatestRunStatus(row.getLatestRunStatus());
        view.setLatestRunAt(row.getLatestRunAt());
        view.setActiveKeywordCount(row.getActiveKeywordCount());
        view.setPendingCandidateCount(row.getPendingCandidateCount());
        view.setConfirmedCompetitorCount(row.getConfirmedCompetitorCount());
        return view;
    }

    public int getActiveKeywordCount() { return activeKeywordCount; }
    public void setActiveKeywordCount(int activeKeywordCount) { this.activeKeywordCount = activeKeywordCount; }
    public int getPendingCandidateCount() { return pendingCandidateCount; }
    public void setPendingCandidateCount(int pendingCandidateCount) { this.pendingCandidateCount = pendingCandidateCount; }
    public int getConfirmedCompetitorCount() { return confirmedCompetitorCount; }
    public void setConfirmedCompetitorCount(int confirmedCompetitorCount) { this.confirmedCompetitorCount = confirmedCompetitorCount; }
}
