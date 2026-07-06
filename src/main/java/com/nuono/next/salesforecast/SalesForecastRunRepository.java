package com.nuono.next.salesforecast;

import java.util.List;

public interface SalesForecastRunRepository {

    SalesForecastRunRecord findLatestCompleted(SalesForecastQuery query);

    SalesForecastRunRecord saveRun(SalesForecastRunRecord run);

    void saveResults(Long runId, List<SalesForecastResultRecord> records);

    default SalesForecastRunRecord saveRunWithResults(
            SalesForecastRunRecord run,
            List<SalesForecastResultRecord> records
    ) {
        SalesForecastRunRecord savedRun = saveRun(run);
        saveResults(savedRun.getId(), records);
        return savedRun;
    }

    List<SalesForecastResultRecord> listResults(Long runId);
}
