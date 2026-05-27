package com.nuono.next.salesforecast;

import java.util.List;

public interface SalesForecastRunRepository {

    SalesForecastRunRecord findLatestCompleted(SalesForecastQuery query);

    SalesForecastRunRecord saveRun(SalesForecastRunRecord run);

    void saveResults(Long runId, List<SalesForecastResultRecord> records);

    List<SalesForecastResultRecord> listResults(Long runId);
}
