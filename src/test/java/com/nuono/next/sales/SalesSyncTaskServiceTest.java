package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SalesSyncTaskServiceTest {

    @Test
    void runsFixtureBackedSyncTaskThroughCsvImporter() {
        InMemorySalesFactRepository factRepository = new InMemorySalesFactRepository();
        NoonSalesCsvImportService importService = new NoonSalesCsvImportService(
                new NoonProductViewsSalesReportParser(),
                factRepository
        );
        InMemorySalesSyncTaskRepository taskRepository = new InMemorySalesSyncTaskRepository();
        SalesSyncTaskService service = new SalesSyncTaskService(
                taskRepository,
                new FixtureSalesReportProvider(csvFixture()),
                importService
        );

        SalesSyncTaskRecord task = service.triggerAndRun(new SalesSyncTaskCommand(
                10002L,
                245027L,
                "STR245027-SAU",
                "SA",
                LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 5, 4),
                10003L,
                "manual"
        ));

        assertEquals(5001L, task.getId());
        assertEquals("succeeded", task.getStatus());
        assertEquals(9001L, task.getSourceBatchId());
        assertEquals(1, task.getTotalRows());
        assertEquals(1, task.getSuccessRows());
        assertEquals(0, task.getFailureRows());
        assertEquals(LocalDate.of(2026, 5, 4), task.getLatestFactDate());
        assertEquals(1, factRepository.count());
    }

    @Test
    void marksTaskFailedWhenReportProviderFails() {
        InMemorySalesFactRepository factRepository = new InMemorySalesFactRepository();
        NoonSalesCsvImportService importService = new NoonSalesCsvImportService(
                new NoonProductViewsSalesReportParser(),
                factRepository
        );
        InMemorySalesSyncTaskRepository taskRepository = new InMemorySalesSyncTaskRepository();
        SalesSyncTaskService service = new SalesSyncTaskService(
                taskRepository,
                request -> {
                    throw new IllegalStateException("Noon sales report provider unavailable");
                },
                importService
        );

        SalesSyncTaskRecord task = service.triggerAndRun(new SalesSyncTaskCommand(
                10002L,
                245027L,
                "STR245027-SAU",
                "SA",
                LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 5, 4),
                10003L,
                "manual"
        ));

        assertEquals("failed", task.getStatus());
        assertEquals("Noon sales report provider unavailable", task.getFailureReason());
        assertEquals(0, factRepository.count());
    }

    @Test
    void rerunningSameDateRangeDoesNotDuplicateFacts() {
        InMemorySalesFactRepository factRepository = new InMemorySalesFactRepository();
        NoonSalesCsvImportService importService = new NoonSalesCsvImportService(
                new NoonProductViewsSalesReportParser(),
                factRepository
        );
        InMemorySalesSyncTaskRepository taskRepository = new InMemorySalesSyncTaskRepository();
        SalesSyncTaskService service = new SalesSyncTaskService(
                taskRepository,
                new FixtureSalesReportProvider(csvFixture()),
                importService
        );
        SalesSyncTaskCommand command = new SalesSyncTaskCommand(
                10002L,
                245027L,
                "STR245027-SAU",
                "SA",
                LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 5, 4),
                10003L,
                "manual"
        );

        SalesSyncTaskRecord first = service.triggerAndRun(command);
        SalesSyncTaskRecord second = service.triggerAndRun(command);

        assertEquals("succeeded", first.getStatus());
        assertEquals("succeeded", second.getStatus());
        assertEquals(1, factRepository.count());
        assertEquals(9002L, factRepository.singleFact().getSourceBatchId());
    }

    @Test
    void returnsTaskByIdForStatusInspection() {
        InMemorySalesFactRepository factRepository = new InMemorySalesFactRepository();
        NoonSalesCsvImportService importService = new NoonSalesCsvImportService(
                new NoonProductViewsSalesReportParser(),
                factRepository
        );
        InMemorySalesSyncTaskRepository taskRepository = new InMemorySalesSyncTaskRepository();
        SalesSyncTaskService service = new SalesSyncTaskService(
                taskRepository,
                new FixtureSalesReportProvider(csvFixture()),
                importService
        );
        SalesSyncTaskRecord created = service.triggerAndRun(new SalesSyncTaskCommand(
                10002L,
                245027L,
                "STR245027-SAU",
                "SA",
                LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 5, 4),
                10003L,
                "manual"
        ));

        SalesSyncTaskRecord loaded = service.getTask(created.getId());

        assertEquals("succeeded", loaded.getStatus());
        assertEquals(9001L, loaded.getSourceBatchId());
    }

    private String csvFixture() {
        return String.join("\n",
                "Visit_Date,Partner_SKU,Mp_Code,SKU_CONFIG,SKU,Family,Product_Type,Product_Subtype,Brand,Currency_Code,Product_Title,Country_Code,Your_Visitors,Total_Visitors,Gross_Units,Shipped_Units,Cancelled_Units,Revenue_Shipped,Buy_Box_Visitor_Percentage,Conversion_Visitors_Percentage,ASP_shipped_Percentage",
                "2026-05-04,SAMPLE-SKU-001,noon,Z57C90A4184D0CFD75218Z,Z57C90A4184D0CFD75218Z-1,bags_luggage,handbag,shopper_tote,papersay,SAR,Sanitized sample product,SA,10,10,1,1,,19.8,100,10,19.8"
        );
    }

    private static class FixtureSalesReportProvider implements NoonSalesReportProvider {
        private final String csv;

        private FixtureSalesReportProvider(String csv) {
            this.csv = csv;
        }

        @Override
        public NoonSalesReportPayload fetch(NoonSalesReportRequest request) {
            return new NoonSalesReportPayload("fixture-productviewsandsalesdata.csv", csv);
        }
    }

    private static class InMemorySalesSyncTaskRepository implements SalesSyncTaskRepository {
        private final Map<Long, SalesSyncTaskRecord> tasks = new LinkedHashMap<>();
        private long nextTaskId = 5001L;

        @Override
        public SalesSyncTaskRecord createQueued(SalesSyncTaskCommand command) {
            SalesSyncTaskRecord task = SalesSyncTaskRecord.queued(nextTaskId++, command);
            tasks.put(task.getId(), task);
            return task;
        }

        @Override
        public SalesSyncTaskRecord markRunning(Long taskId) {
            return tasks.computeIfPresent(taskId, (id, task) -> task.withStatus("running"));
        }

        @Override
        public SalesSyncTaskRecord markSucceeded(Long taskId, NoonSalesCsvImportResult result) {
            return tasks.computeIfPresent(taskId, (id, task) -> task.succeeded(result));
        }

        @Override
        public SalesSyncTaskRecord markFailed(Long taskId, String failureReason) {
            return tasks.computeIfPresent(taskId, (id, task) -> task.failed(failureReason));
        }

        @Override
        public SalesSyncTaskRecord findById(Long taskId) {
            return tasks.get(taskId);
        }
    }

    private static class InMemorySalesFactRepository implements SalesFactRepository {
        private final Map<String, DailySalesFact> facts = new LinkedHashMap<>();
        private long nextBatchId = 9001L;

        @Override
        public long saveBatch(SalesImportBatch batch) {
            return nextBatchId++;
        }

        @Override
        public void upsert(DailySalesFact fact) {
            facts.put(fact.getOwnerUserId() + "|" + fact.getStoreCode() + "|" + fact.getSiteCode()
                    + "|" + fact.getFactDate() + "|" + fact.getPartnerSku() + "|" + fact.getSku(), fact);
        }

        @Override
        public List<DailySalesFact> list(SalesFactQuery query) {
            return List.copyOf(facts.values());
        }

        int count() {
            return facts.size();
        }

        DailySalesFact singleFact() {
            return facts.values().iterator().next();
        }
    }
}
