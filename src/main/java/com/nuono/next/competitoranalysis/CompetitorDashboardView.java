package com.nuono.next.competitoranalysis;

import java.time.LocalDate;
import java.util.List;

public class CompetitorDashboardView {

    private String storeCode;
    private String siteCode;
    private Integer days;
    private List<CompetitorDashboardSummaryRow> issueSummary;
    private List<CompetitorDashboardTrendRow> issueTrend;
    private List<CompetitorDashboardProductRow> coverageTopProducts;
    private List<CompetitorDashboardProductRow> rankIssueTopProducts;
    private List<CompetitorDashboardSummaryRow> changeTypeDistribution;
    private List<CompetitorDashboardProductRow> changedProductTop;
    private List<CompetitorDashboardRankChangeRow> selfRankChanges;
    private List<CompetitorDashboardRankChangeRow> competitorRankChanges;
    private LocalDate competitorAttributeChangeDate;
    private Long competitorAttributeSnapshotCount;
    private List<CompetitorDashboardAttributeChangeRow> competitorAttributeChanges;

    public static CompetitorDashboardView of(
            String storeCode,
            String siteCode,
            Integer days,
            List<CompetitorDashboardSummaryRow> issueSummary,
            List<CompetitorDashboardTrendRow> issueTrend,
            List<CompetitorDashboardProductRow> coverageTopProducts,
            List<CompetitorDashboardProductRow> rankIssueTopProducts,
            List<CompetitorDashboardSummaryRow> changeTypeDistribution,
            List<CompetitorDashboardProductRow> changedProductTop,
            List<CompetitorDashboardRankChangeRow> selfRankChanges,
            List<CompetitorDashboardRankChangeRow> competitorRankChanges,
            LocalDate competitorAttributeChangeDate,
            Long competitorAttributeSnapshotCount,
            List<CompetitorDashboardAttributeChangeRow> competitorAttributeChanges
    ) {
        CompetitorDashboardView view = new CompetitorDashboardView();
        view.setStoreCode(storeCode);
        view.setSiteCode(siteCode);
        view.setDays(days);
        view.setIssueSummary(issueSummary == null ? List.of() : issueSummary);
        view.setIssueTrend(issueTrend == null ? List.of() : issueTrend);
        view.setCoverageTopProducts(coverageTopProducts == null ? List.of() : coverageTopProducts);
        view.setRankIssueTopProducts(rankIssueTopProducts == null ? List.of() : rankIssueTopProducts);
        view.setChangeTypeDistribution(changeTypeDistribution == null ? List.of() : changeTypeDistribution);
        view.setChangedProductTop(changedProductTop == null ? List.of() : changedProductTop);
        view.setSelfRankChanges(selfRankChanges == null ? List.of() : selfRankChanges);
        view.setCompetitorRankChanges(competitorRankChanges == null ? List.of() : competitorRankChanges);
        view.setCompetitorAttributeChangeDate(competitorAttributeChangeDate);
        view.setCompetitorAttributeSnapshotCount(competitorAttributeSnapshotCount == null ? 0L : competitorAttributeSnapshotCount);
        view.setCompetitorAttributeChanges(competitorAttributeChanges == null ? List.of() : competitorAttributeChanges);
        return view;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public void setStoreCode(String storeCode) {
        this.storeCode = storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public void setSiteCode(String siteCode) {
        this.siteCode = siteCode;
    }

    public Integer getDays() {
        return days;
    }

    public void setDays(Integer days) {
        this.days = days;
    }

    public List<CompetitorDashboardSummaryRow> getIssueSummary() {
        return issueSummary;
    }

    public void setIssueSummary(List<CompetitorDashboardSummaryRow> issueSummary) {
        this.issueSummary = issueSummary;
    }

    public List<CompetitorDashboardTrendRow> getIssueTrend() {
        return issueTrend;
    }

    public void setIssueTrend(List<CompetitorDashboardTrendRow> issueTrend) {
        this.issueTrend = issueTrend;
    }

    public List<CompetitorDashboardProductRow> getCoverageTopProducts() {
        return coverageTopProducts;
    }

    public void setCoverageTopProducts(List<CompetitorDashboardProductRow> coverageTopProducts) {
        this.coverageTopProducts = coverageTopProducts;
    }

    public List<CompetitorDashboardProductRow> getRankIssueTopProducts() {
        return rankIssueTopProducts;
    }

    public void setRankIssueTopProducts(List<CompetitorDashboardProductRow> rankIssueTopProducts) {
        this.rankIssueTopProducts = rankIssueTopProducts;
    }

    public List<CompetitorDashboardSummaryRow> getChangeTypeDistribution() {
        return changeTypeDistribution;
    }

    public void setChangeTypeDistribution(List<CompetitorDashboardSummaryRow> changeTypeDistribution) {
        this.changeTypeDistribution = changeTypeDistribution;
    }

    public List<CompetitorDashboardProductRow> getChangedProductTop() {
        return changedProductTop;
    }

    public void setChangedProductTop(List<CompetitorDashboardProductRow> changedProductTop) {
        this.changedProductTop = changedProductTop;
    }

    public List<CompetitorDashboardRankChangeRow> getSelfRankChanges() {
        return selfRankChanges;
    }

    public void setSelfRankChanges(List<CompetitorDashboardRankChangeRow> selfRankChanges) {
        this.selfRankChanges = selfRankChanges;
    }

    public List<CompetitorDashboardRankChangeRow> getCompetitorRankChanges() {
        return competitorRankChanges;
    }

    public void setCompetitorRankChanges(List<CompetitorDashboardRankChangeRow> competitorRankChanges) {
        this.competitorRankChanges = competitorRankChanges;
    }

    public LocalDate getCompetitorAttributeChangeDate() {
        return competitorAttributeChangeDate;
    }

    public void setCompetitorAttributeChangeDate(LocalDate competitorAttributeChangeDate) {
        this.competitorAttributeChangeDate = competitorAttributeChangeDate;
    }

    public Long getCompetitorAttributeSnapshotCount() {
        return competitorAttributeSnapshotCount;
    }

    public void setCompetitorAttributeSnapshotCount(Long competitorAttributeSnapshotCount) {
        this.competitorAttributeSnapshotCount = competitorAttributeSnapshotCount;
    }

    public List<CompetitorDashboardAttributeChangeRow> getCompetitorAttributeChanges() {
        return competitorAttributeChanges;
    }

    public void setCompetitorAttributeChanges(List<CompetitorDashboardAttributeChangeRow> competitorAttributeChanges) {
        this.competitorAttributeChanges = competitorAttributeChanges;
    }
}
