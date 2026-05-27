package com.nuono.next.sales;

import com.nuono.next.infrastructure.mapper.SalesDataMapper;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisSalesFactRepository implements SalesFactRepository {

    private final SalesDataMapper mapper;

    public MyBatisSalesFactRepository(SalesDataMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public long saveBatch(SalesImportBatch batch) {
        Long id = mapper.nextSalesImportBatchId();
        mapper.insertImportBatch(id, batch);
        return id;
    }

    @Override
    public void upsert(DailySalesFact fact) {
        Long id = mapper.nextDailySalesFactId();
        mapper.upsertDailySalesFact(id, fact);
    }

    @Override
    public void saveExceptions(long sourceBatchId, List<SalesImportExceptionRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        for (SalesImportExceptionRecord record : records) {
            Long id = mapper.nextSalesImportExceptionId();
            mapper.insertSalesImportException(id, record);
        }
    }

    @Override
    public List<DailySalesFact> list(SalesFactQuery query) {
        return mapper.selectDailySalesFacts(query);
    }

    @Override
    public LocalDate findLatestFactDate(Long ownerUserId, String storeCode, String siteCode) {
        return mapper.selectLatestDailySalesFactDate(ownerUserId, storeCode, siteCode);
    }

    @Override
    public List<SalesImportBatchRecord> listImportBatches(SalesImportBatchQuery query) {
        return mapper.selectSalesImportBatches(query);
    }

    @Override
    public SalesImportBatchRecord findImportBatch(Long batchId) {
        return mapper.selectSalesImportBatchById(batchId);
    }

    @Override
    public List<SalesImportExceptionRecord> listImportExceptions(Long batchId) {
        return mapper.selectSalesImportExceptions(batchId);
    }
}
