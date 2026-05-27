package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nuono.next.infrastructure.mapper.IdSequenceCommand;
import com.nuono.next.infrastructure.mapper.SalesDataMapper;
import com.nuono.next.salesforecast.SalesForecastResultRecord;
import com.nuono.next.salesforecast.SalesForecastRunRecord;
import com.nuono.next.salesforecast.SalesForecastStockSnapshot;
import com.nuono.next.salesforecast.SalesForecastFollowUpCommand;
import com.nuono.next.salesforecast.SalesForecastFollowUpRecord;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MyBatisSalesFactRepositoryTest {

    @Test
    void savesImportBatchAndUpsertsDailySalesFactThroughMapper() {
        RecordingSalesDataMapper mapper = new RecordingSalesDataMapper();
        SalesFactRepository repository = new MyBatisSalesFactRepository(mapper);
        SalesImportBatch batch = new SalesImportBatch(
                NoonSalesCsvImportService.SOURCE_SYSTEM,
                "confirmed-sanitized.csv",
                10002L,
                245027L,
                "STR245027-SAU",
                "SA",
                LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 5, 4),
                2,
                2,
                0
        );

        long batchId = repository.saveBatch(batch);
        repository.upsert(fact(batchId));

        assertEquals(10001L, batchId);
        assertEquals(10001L, mapper.insertedBatchId);
        assertEquals("confirmed-sanitized.csv", mapper.insertedBatch.getSourceFilename());
        assertEquals(100001L, mapper.upsertedFactId);
        assertEquals(batchId, mapper.upsertedFact.getSourceBatchId());
        assertEquals("SAMPLE-SKU-001", mapper.upsertedFact.getPartnerSku());
        assertEquals(new BigDecimal("19.8"), mapper.upsertedFact.getRevenueShipped());
    }

    @Test
    void savesImportExceptionsThroughMapper() {
        RecordingSalesDataMapper mapper = new RecordingSalesDataMapper();
        SalesFactRepository repository = new MyBatisSalesFactRepository(mapper);

        repository.saveExceptions(10001L, List.of(new SalesImportExceptionRecord(
                null,
                10001L,
                "one-bad-row.csv",
                10002L,
                "STR245027-SAU",
                "SA",
                3,
                "malformed_number",
                "Gross_Units",
                "not-a-number",
                "2026-05-05,SAMPLE-SKU-002",
                "Invalid numeric value for Gross_Units: not-a-number",
                "确认该字段是整数或留空后重导。"
        )));

        assertEquals(30001L, mapper.insertedExceptionId);
        assertEquals("malformed_number", mapper.insertedException.getExceptionType());
        assertEquals("Gross_Units", mapper.insertedException.getFieldName());
        assertEquals(3, mapper.insertedException.getRowNumber());
    }

    @Test
    void listsImportBatchesAndExceptionsThroughMapper() {
        RecordingSalesDataMapper mapper = new RecordingSalesDataMapper();
        SalesFactRepository repository = new MyBatisSalesFactRepository(mapper);
        SalesImportBatchQuery query = new SalesImportBatchQuery(
                10002L,
                "STR245027-SAU",
                "SA",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 5)
        );

        List<SalesImportBatchRecord> batches = repository.listImportBatches(query);
        SalesImportBatchRecord batch = repository.findImportBatch(10001L);
        List<SalesImportExceptionRecord> exceptions = repository.listImportExceptions(10001L);

        assertEquals(query, mapper.lastBatchQuery);
        assertEquals(1, batches.size());
        assertEquals("imported_with_exceptions", batch.getStatus());
        assertEquals(1, exceptions.size());
        assertEquals("malformed_number", exceptions.get(0).getExceptionType());
    }



    private DailySalesFact fact(long batchId) {
        return new DailySalesFact(
                NoonSalesCsvImportService.SOURCE_SYSTEM,
                batchId,
                10002L,
                245027L,
                "STR245027-SAU",
                "SA",
                LocalDate.of(2026, 4, 30),
                "SAMPLE-SKU-001",
                "Z57C90A4184D0CFD75218Z-1",
                "Z57C90A4184D0CFD75218Z",
                "SA",
                "SAR",
                "Sanitized sample product",
                null,
                null,
                1,
                1,
                null,
                1,
                new BigDecimal("19.8"),
                null,
                null,
                new BigDecimal("19.8")
        );
    }

    private static class RecordingSalesDataMapper implements SalesDataMapper {
        private Long insertedBatchId;
        private SalesImportBatch insertedBatch;
        private Long upsertedFactId;
        private DailySalesFact upsertedFact;
        private Long insertedExceptionId;
        private SalesImportExceptionRecord insertedException;
        private SalesImportBatchQuery lastBatchQuery;

        @Override
        public int allocateSalesDataId(IdSequenceCommand command) {
            if ("sales_import_batch".equals(command.getSequenceName())) {
                command.setAllocatedId(10001L);
                return 1;
            }
            if ("daily_sales_fact".equals(command.getSequenceName())) {
                command.setAllocatedId(100001L);
                return 1;
            }
            if ("sales_import_exception".equals(command.getSequenceName())) {
                command.setAllocatedId(30001L);
                return 1;
            }
            throw new IllegalArgumentException("Unexpected sequence: " + command.getSequenceName());
        }

        @Override
        public int insertImportBatch(Long id, SalesImportBatch batch) {
            this.insertedBatchId = id;
            this.insertedBatch = batch;
            return 1;
        }

        @Override
        public int upsertDailySalesFact(Long id, DailySalesFact fact) {
            this.upsertedFactId = id;
            this.upsertedFact = fact;
            return 1;
        }

        @Override
        public List<DailySalesFact> selectDailySalesFacts(SalesFactQuery query) {
            return List.of();
        }

        @Override
        public LocalDate selectLatestDailySalesFactDate(Long ownerUserId, String storeCode, String siteCode) {
            return null;
        }

        @Override
        public int insertSalesImportException(Long id, SalesImportExceptionRecord record) {
            this.insertedExceptionId = id;
            this.insertedException = record;
            return 1;
        }

        @Override
        public List<SalesImportBatchRecord> selectSalesImportBatches(SalesImportBatchQuery query) {
            this.lastBatchQuery = query;
            return List.of(batchRecord());
        }

        @Override
        public SalesImportBatchRecord selectSalesImportBatchById(Long batchId) {
            return batchRecord();
        }

        @Override
        public List<SalesImportExceptionRecord> selectSalesImportExceptions(Long batchId) {
            return List.of(new SalesImportExceptionRecord(
                    30001L,
                    batchId,
                    "one-bad-row.csv",
                    10002L,
                    "STR245027-SAU",
                    "SA",
                    3,
                    "malformed_number",
                    "Gross_Units",
                    "not-a-number",
                    "2026-05-05,SAMPLE-SKU-002",
                    "Invalid numeric value for Gross_Units: not-a-number",
                    "确认该字段是整数或留空后重导。"
            ));
        }

        @Override
        public int insertSalesForecastRun(Long id, SalesForecastRunRecord run) {
            return 0;
        }

        @Override
        public SalesForecastRunRecord selectSalesForecastRunById(Long id) {
            return null;
        }

        @Override
        public SalesForecastRunRecord selectLatestSalesForecastRun(
                com.nuono.next.salesforecast.SalesForecastQuery query
        ) {
            return null;
        }

        @Override
        public List<SalesForecastStockSnapshot> selectSalesForecastCurrentStock(
                com.nuono.next.salesforecast.SalesForecastQuery query
        ) {
            return List.of();
        }

        @Override
        public int insertSalesForecastResult(Long id, Long runId, SalesForecastResultRecord record) {
            return 0;
        }

        @Override
        public List<SalesForecastResultRecord> selectSalesForecastResults(Long runId) {
            return List.of();
        }

        @Override
        public int upsertSalesForecastFollowUp(Long id, SalesForecastFollowUpCommand command) {
            return 0;
        }

        @Override
        public List<SalesForecastFollowUpRecord> selectSalesForecastFollowUps(
                com.nuono.next.salesforecast.SalesForecastQuery query
        ) {
            return List.of();
        }

        private SalesImportBatchRecord batchRecord() {
            return new SalesImportBatchRecord(
                    10001L,
                    NoonSalesCsvImportService.SOURCE_SYSTEM,
                    "one-bad-row.csv",
                    10002L,
                    245027L,
                    "STR245027-SAU",
                    "SA",
                    LocalDate.of(2026, 5, 4),
                    LocalDate.of(2026, 5, 5),
                    2,
                    1,
                    1,
                    "imported_with_exceptions",
                    "1 row(s) excluded from sales facts",
                    null
            );
        }

        @Override
        public int insertSalesSyncTask(Long id, SalesSyncTaskCommand command) {
            return 0;
        }

        @Override
        public int updateSalesSyncTaskRunning(Long taskId) {
            return 0;
        }

        @Override
        public int updateSalesSyncTaskSucceeded(Long taskId, NoonSalesCsvImportResult result) {
            return 0;
        }

        @Override
        public int updateSalesSyncTaskFailed(Long taskId, String failureReason) {
            return 0;
        }

        @Override
        public SalesSyncTaskRecord selectSalesSyncTaskById(Long taskId) {
            return null;
        }

        @Override
        public int insertSalesActivityWindow(Long id, SalesActivityWindowRecord record) {
            return 0;
        }

        @Override
        public int updateSalesActivityWindowEnabled(Long id, boolean enabled, Long updatedBy) {
            return 0;
        }

        @Override
        public SalesActivityWindowRecord selectSalesActivityWindowById(Long id) {
            return null;
        }

        @Override
        public List<SalesActivityWindowRecord> selectSalesActivityWindowHistory(SalesActivityWindowScope scope) {
            return List.of();
        }

        @Override
        public List<SalesActivityWindowRecord> selectActiveSalesActivityWindows(SalesActivityWindowScope scope) {
            return List.of();
        }

        @Override
        public List<SalesProductDimensionSnapshot> selectSalesProductDimensions(SalesFactQuery query) {
            return List.of();
        }
    }
}
