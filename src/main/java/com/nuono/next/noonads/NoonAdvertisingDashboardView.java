package com.nuono.next.noonads;

import java.util.List;

public class NoonAdvertisingDashboardView {
    private final NoonAdvertisingSummaryView adSummary;
    private final NoonAdvertisingSalesSummaryView salesSummary;
    private final List<NoonAdvertisingCampaignRow> campaignRows;
    private final List<NoonAdvertisingProductRow> productRows;
    private final List<NoonAdvertisingProductDiagnostic> productDiagnostics;
    private final List<NoonAdvertisingCampaignDiagnostic> campaignDiagnostics;
    private final List<NoonAdvertisingQueryRow> zeroOrderQueries;
    private final List<NoonAdvertisingQueryRow> winningQueries;
    private final NoonAdvertisingDataStatus dataStatus;

    public NoonAdvertisingDashboardView(
            NoonAdvertisingSummaryView adSummary,
            NoonAdvertisingSalesSummaryView salesSummary,
            List<NoonAdvertisingCampaignRow> campaignRows,
            List<NoonAdvertisingQueryRow> zeroOrderQueries,
            List<NoonAdvertisingQueryRow> winningQueries,
            NoonAdvertisingDataStatus dataStatus
    ) {
        this(adSummary, salesSummary, campaignRows, null, null, null, zeroOrderQueries, winningQueries, dataStatus);
    }

    public NoonAdvertisingDashboardView(
            NoonAdvertisingSummaryView adSummary,
            NoonAdvertisingSalesSummaryView salesSummary,
            List<NoonAdvertisingCampaignRow> campaignRows,
            List<NoonAdvertisingProductRow> productRows,
            List<NoonAdvertisingQueryRow> zeroOrderQueries,
            List<NoonAdvertisingQueryRow> winningQueries,
            NoonAdvertisingDataStatus dataStatus
    ) {
        this(adSummary, salesSummary, campaignRows, productRows, null, null, zeroOrderQueries, winningQueries, dataStatus);
    }

    public NoonAdvertisingDashboardView(
            NoonAdvertisingSummaryView adSummary,
            NoonAdvertisingSalesSummaryView salesSummary,
            List<NoonAdvertisingCampaignRow> campaignRows,
            List<NoonAdvertisingProductRow> productRows,
            List<NoonAdvertisingProductDiagnostic> productDiagnostics,
            List<NoonAdvertisingCampaignDiagnostic> campaignDiagnostics,
            List<NoonAdvertisingQueryRow> zeroOrderQueries,
            List<NoonAdvertisingQueryRow> winningQueries,
            NoonAdvertisingDataStatus dataStatus
    ) {
        this.adSummary = adSummary == null ? new NoonAdvertisingSummaryView() : adSummary;
        this.salesSummary = salesSummary == null ? new NoonAdvertisingSalesSummaryView() : salesSummary;
        this.campaignRows = campaignRows == null ? List.of() : List.copyOf(campaignRows);
        this.productRows = productRows == null ? List.of() : List.copyOf(productRows);
        this.productDiagnostics = productDiagnostics == null ? List.of() : List.copyOf(productDiagnostics);
        this.campaignDiagnostics = campaignDiagnostics == null ? List.of() : List.copyOf(campaignDiagnostics);
        this.zeroOrderQueries = zeroOrderQueries == null ? List.of() : List.copyOf(zeroOrderQueries);
        this.winningQueries = winningQueries == null ? List.of() : List.copyOf(winningQueries);
        this.dataStatus = dataStatus == null ? new NoonAdvertisingDataStatus() : dataStatus;
    }

    public NoonAdvertisingSummaryView getAdSummary() { return adSummary; }
    public NoonAdvertisingSalesSummaryView getSalesSummary() { return salesSummary; }
    public List<NoonAdvertisingCampaignRow> getCampaignRows() { return campaignRows; }
    public List<NoonAdvertisingProductRow> getProductRows() { return productRows; }
    public List<NoonAdvertisingProductDiagnostic> getProductDiagnostics() { return productDiagnostics; }
    public List<NoonAdvertisingCampaignDiagnostic> getCampaignDiagnostics() { return campaignDiagnostics; }
    public List<NoonAdvertisingQueryRow> getZeroOrderQueries() { return zeroOrderQueries; }
    public List<NoonAdvertisingQueryRow> getWinningQueries() { return winningQueries; }
    public NoonAdvertisingDataStatus getDataStatus() { return dataStatus; }
}
