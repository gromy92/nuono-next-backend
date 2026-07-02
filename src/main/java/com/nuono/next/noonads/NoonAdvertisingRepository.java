package com.nuono.next.noonads;

import java.util.List;

public interface NoonAdvertisingRepository {
    NoonAdvertisingLatestReportWindowView selectLatestReportWindow(NoonAdvertisingScopeQuery query);

    NoonAdvertisingSummaryView selectAdSummary(NoonAdvertisingDashboardQuery query);

    NoonAdvertisingSalesSummaryView selectSalesSummary(NoonAdvertisingDashboardQuery query);

    NoonAdvertisingDataStatus selectDataStatus(NoonAdvertisingDashboardQuery query);

    List<NoonAdvertisingCampaignRow> selectCampaignRows(NoonAdvertisingDashboardQuery query);

    List<NoonAdvertisingProductRow> selectProductRows(NoonAdvertisingDashboardQuery query);

    List<NoonAdvertisingQueryRow> selectZeroOrderQueryRows(NoonAdvertisingDashboardQuery query);

    List<NoonAdvertisingQueryRow> selectWinningQueryRows(NoonAdvertisingDashboardQuery query);
}
