package com.nuono.next.salesforecast;

import com.nuono.next.infrastructure.mapper.SalesDataMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

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
    public List<SalesForecastResultRecord> listResults(Long runId) {
        return mapper.selectSalesForecastResults(runId);
    }
}
