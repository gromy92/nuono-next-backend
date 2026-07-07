package com.nuono.next.salesforecast;

public interface SalesForecastService {

    SalesForecastOverviewView getOverview(SalesForecastQuery query);

    SalesForecastDetailView getDetail(SalesForecastDetailQuery query);

    SalesForecastFollowUpView setFollowUp(SalesForecastFollowUpCommand command);

    SalesForecastRunStatusView recalculate(SalesForecastQuery query);

    SalesForecastExportView exportCsv(SalesForecastQuery query, SalesForecastExportQuery exportQuery);
}
