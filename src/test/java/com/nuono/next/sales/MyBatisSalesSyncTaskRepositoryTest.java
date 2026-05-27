package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.nuono.next.infrastructure.mapper.IdSequenceCommand;
import com.nuono.next.infrastructure.mapper.SalesDataMapper;
import com.nuono.next.salesforecast.SalesForecastResultRecord;
import com.nuono.next.salesforecast.SalesForecastRunRecord;
import com.nuono.next.salesforecast.SalesForecastStockSnapshot;
import com.nuono.next.salesforecast.SalesForecastFollowUpCommand;
import com.nuono.next.salesforecast.SalesForecastFollowUpRecord;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MyBatisSalesSyncTaskRepositoryTest {

    @Test
    void recordsSalesSyncTaskLifecycleThroughMapper() {
        RecordingSalesDataMapper mapper = new RecordingSalesDataMapper();
        SalesSyncTaskRepository repository = new MyBatisSalesSyncTaskRepository(mapper);

        SalesSyncTaskRecord queued = repository.createQueued(command());
        SalesSyncTaskRecord running = repository.markRunning(queued.getId());
        SalesSyncTaskRecord succeeded = repository.markSucceeded(queued.getId(), importResult());
        SalesSyncTaskRecord loaded = repository.findById(queued.getId());

        assertEquals(20001L, queued.getId());
        assertEquals("queued", queued.getStatus());
        assertEquals("running", running.getStatus());
        assertEquals("succeeded", succeeded.getStatus());
        assertEquals(9001L, loaded.getSourceBatchId());
        assertEquals(1, loaded.getTotalRows());
        assertEquals(1, loaded.getSuccessRows());
        assertEquals(0, loaded.getFailureRows());
        assertEquals(LocalDate.of(2026, 5, 4), loaded.getLatestFactDate());
        assertNull(loaded.getFailureReason());
    }

    @Test
    void recordsReadableFailureReasonThroughMapper() {
        RecordingSalesDataMapper mapper = new RecordingSalesDataMapper();
        SalesSyncTaskRepository repository = new MyBatisSalesSyncTaskRepository(mapper);

        SalesSyncTaskRecord queued = repository.createQueued(command());
        SalesSyncTaskRecord failed = repository.markFailed(queued.getId(), "Noon sales report provider unavailable");

        assertEquals("failed", failed.getStatus());
        assertEquals("Noon sales report provider unavailable", failed.getFailureReason());
    }

    private SalesSyncTaskCommand command() {
        return new SalesSyncTaskCommand(
                10002L,
                245027L,
                "STR245027-SAU",
                "SA",
                LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 5, 4),
                10003L,
                "manual"
        );
    }

    private NoonSalesCsvImportResult importResult() {
        return new NoonSalesCsvImportResult(
                NoonSalesCsvImportService.SOURCE_SYSTEM,
                9001L,
                "fixture-productviewsandsalesdata.csv",
                1,
                1,
                0,
                LocalDate.of(2026, 5, 4),
                LocalDate.of(2026, 5, 4)
        );
    }

    private static class RecordingSalesDataMapper implements SalesDataMapper {
        private final Map<Long, SalesSyncTaskRecord> tasks = new LinkedHashMap<>();

        @Override
        public int allocateSalesDataId(IdSequenceCommand command) {
            if ("sales_sync_task".equals(command.getSequenceName())) {
                command.setAllocatedId(20001L);
                return 1;
            }
            throw new IllegalArgumentException("Unexpected sequence: " + command.getSequenceName());
        }

        @Override
        public int insertImportBatch(Long id, SalesImportBatch batch) {
            return 0;
        }

        @Override
        public int upsertDailySalesFact(Long id, DailySalesFact fact) {
            return 0;
        }

        @Override
        public int insertSalesImportException(Long id, SalesImportExceptionRecord record) {
            return 0;
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
        public List<SalesImportBatchRecord> selectSalesImportBatches(SalesImportBatchQuery query) {
            return List.of();
        }

        @Override
        public SalesImportBatchRecord selectSalesImportBatchById(Long batchId) {
            return null;
        }

        @Override
        public List<SalesImportExceptionRecord> selectSalesImportExceptions(Long batchId) {
            return List.of();
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

        @Override
        public int insertSalesSyncTask(Long id, SalesSyncTaskCommand command) {
            tasks.put(id, SalesSyncTaskRecord.queued(id, command));
            return 1;
        }

        @Override
        public int updateSalesSyncTaskRunning(Long taskId) {
            tasks.computeIfPresent(taskId, (id, task) -> task.withStatus("running"));
            return 1;
        }

        @Override
        public int updateSalesSyncTaskSucceeded(Long taskId, NoonSalesCsvImportResult result) {
            tasks.computeIfPresent(taskId, (id, task) -> task.succeeded(result));
            return 1;
        }

        @Override
        public int updateSalesSyncTaskFailed(Long taskId, String failureReason) {
            tasks.computeIfPresent(taskId, (id, task) -> task.failed(failureReason));
            return 1;
        }

        @Override
        public SalesSyncTaskRecord selectSalesSyncTaskById(Long taskId) {
            return tasks.get(taskId);
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
