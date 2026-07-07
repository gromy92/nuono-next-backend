package com.nuono.next.salesforecast;

import com.nuono.next.infrastructure.mapper.SalesDataMapper;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class MyBatisSalesForecastRunRepository implements SalesForecastRunRepository {

    private final SalesDataMapper mapper;

    public MyBatisSalesForecastRunRepository(SalesDataMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public SalesForecastRunRecord findLatestCompleted(SalesForecastQuery query) {
        return mapper.selectLatestSalesForecastRun(query);
    }

    @Override
    public SalesForecastRunRecord saveRun(SalesForecastRunRecord run) {
        Long id = mapper.nextSalesForecastRunId();
        mapper.insertSalesForecastRun(id, run);
        return mapper.selectSalesForecastRunById(id);
    }

    @Override
    public void saveResults(Long runId, List<SalesForecastResultRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        for (SalesForecastResultRecord record : records) {
            Long id = mapper.nextSalesForecastResultId();
            mapper.insertSalesForecastResult(id, runId, record);
        }
    }

    @Override
    @Transactional
    public SalesForecastRunRecord saveRunWithResults(
            SalesForecastRunRecord run,
            List<SalesForecastResultRecord> records
    ) {
        SalesForecastRunRecord savedRun = saveRun(run);
        saveResults(savedRun.getId(), records);
        return savedRun;
    }

    @Override
    public List<SalesForecastResultRecord> listResults(Long runId) {
        return mapper.selectSalesForecastResults(runId);
    }
}
