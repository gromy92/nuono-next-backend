package com.nuono.next.noonsync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.sales.NoonProductViewsSalesReportParser;
import com.nuono.next.sales.NoonSalesCsvImportCommand;
import com.nuono.next.sales.NoonSalesCsvImportResult;
import com.nuono.next.sales.NoonSalesCsvImportService;
import com.nuono.next.sales.NoonSalesReportPayload;
import com.nuono.next.sales.NoonSalesReportProvider;
import com.nuono.next.sales.NoonSalesReportRequest;
import com.nuono.next.sales.SalesFactQuery;
import com.nuono.next.sales.SalesFactRepository;
import com.nuono.next.sales.SalesImportBatch;
import com.nuono.next.sales.SalesSyncTaskCommand;
import com.nuono.next.sales.SalesSyncTaskRecord;
import com.nuono.next.sales.SalesSyncTaskRepository;
import com.nuono.next.sales.SalesSyncTaskService;
import com.nuono.next.sales.DailySalesFact;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NoonSalesSyncBridgeServiceTest {

    @Test
    void scheduledDailySyncTargetsOnlyLatestAvailableSalesDayThroughSharedFoundation() {
        TestHarness harness = new TestHarness(csvFixture(LocalDate.of(2026, 5, 19)));
        NoonSalesSyncBridgeService bridge = harness.bridge();

        NoonSalesSyncRunResult result = bridge.runScheduledDaily(new NoonSalesDailySyncCommand(
                NoonSyncScope.of(10002L, 245027L, "STR245027-SAU", "SA"),
                LocalDate.of(2026, 5, 19),
                10003L
        ));

        assertEquals(NoonSyncTriggerMode.SCHEDULED_DAILY, result.getFoundationTask().getTriggerMode());
        assertEquals(LocalDate.of(2026, 5, 19), result.getFoundationTask().getTarget().getDateFrom());
        assertEquals(LocalDate.of(2026, 5, 19), result.getFoundationTask().getTarget().getDateTo());
        assertEquals(LocalDate.of(2026, 5, 19), harness.provider().lastRequest().getDateFrom());
        assertEquals(LocalDate.of(2026, 5, 19), harness.provider().lastRequest().getDateTo());
        assertEquals("succeeded", result.getSalesTask().getStatus());
        assertEquals(1, harness.factRepository().count());
    }

    @Test
    void historicalBackfillRunsOnlyForAllowedGapReasonsAndUsesRequestedDateWindow() {
        TestHarness harness = new TestHarness(csvFixture(LocalDate.of(2026, 5, 1)));
        NoonSalesSyncBridgeService bridge = harness.bridge();

        NoonSalesSyncRunResult result = bridge.runHistoricalBackfill(new NoonSalesBackfillSyncCommand(
                NoonSyncScope.of(10002L, 245027L, "STR245027-SAU", "SA"),
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 10),
                NoonSalesBackfillReason.DETECTED_GAP,
                10003L
        ));

        assertEquals(NoonSyncTriggerMode.GAP_BACKFILL, result.getFoundationTask().getTriggerMode());
        assertEquals(LocalDate.of(2026, 5, 1), harness.provider().lastRequest().getDateFrom());
        assertEquals(LocalDate.of(2026, 5, 10), harness.provider().lastRequest().getDateTo());
        assertEquals("gap_backfill", result.getSalesTask().getTriggerType());
        assertThrows(IllegalArgumentException.class, () -> bridge.runHistoricalBackfill(new NoonSalesBackfillSyncCommand(
                NoonSyncScope.of(10002L, 245027L, "STR245027-SAU", "SA"),
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                NoonSalesBackfillReason.SCHEDULED_CORRECTION,
                10003L
        )));
    }

    @Test
    void emptyReportsAndMissingColumnsMapToTypedFoundationFailuresWithoutFakeFacts() {
        TestHarness emptyHarness = new TestHarness(headersOnlyCsv());
        NoonSalesSyncRunResult emptyResult = emptyHarness.bridge().runScheduledDaily(new NoonSalesDailySyncCommand(
                NoonSyncScope.of(10002L, 245027L, "STR245027-SAU", "SA"),
                LocalDate.of(2026, 5, 19),
                10003L
        ));

        TestHarness missingColumnHarness = new TestHarness(csvMissingRequiredColumn());
        NoonSalesSyncRunResult missingColumnResult = missingColumnHarness.bridge().runScheduledDaily(new NoonSalesDailySyncCommand(
                NoonSyncScope.of(10002L, 245027L, "STR245027-SAU", "SA"),
                LocalDate.of(2026, 5, 19),
                10003L
        ));

        assertEquals(NoonSyncTaskStatus.PARTIAL, emptyResult.getFoundationTask().getStatus());
        assertEquals(NoonSyncFailureReason.EMPTY_REPORT, emptyResult.getFoundationTask().getFailureReason());
        assertEquals(0, emptyHarness.factRepository().count());
        assertEquals(NoonSyncTaskStatus.FAILED, missingColumnResult.getFoundationTask().getStatus());
        assertEquals(NoonSyncFailureReason.MISSING_COLUMNS, missingColumnResult.getFoundationTask().getFailureReason());
        assertEquals(0, missingColumnHarness.factRepository().count());
    }

    @Test
    void duplicateRerunIsIdempotentAndPartialRowsPreserveQualityState() {
        TestHarness harness = new TestHarness(csvWithOneValidAndOneMalformedRow());
        NoonSalesSyncBridgeService bridge = harness.bridge();
        NoonSalesBackfillSyncCommand command = new NoonSalesBackfillSyncCommand(
                NoonSyncScope.of(10002L, 245027L, "STR245027-SAU", "SA"),
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 2),
                NoonSalesBackfillReason.ONBOARDING,
                10003L
        );

        NoonSalesSyncRunResult first = bridge.runHistoricalBackfill(command);
        NoonSalesSyncRunResult second = bridge.runHistoricalBackfill(command);

        assertEquals(NoonSyncTaskStatus.PARTIAL, first.getFoundationTask().getStatus());
        assertEquals(NoonSyncFailureReason.MAPPING_FAILED, first.getFoundationTask().getFailureReason());
        assertEquals(1, harness.factRepository().count());
        assertEquals(9002L, harness.factRepository().singleFact().getSourceBatchId());
        assertEquals("imported_with_exceptions", second.getSalesTask().getFailureReason());
    }

    @Test
    void dailySyncDoesNotAllowRollingCorrectionWindow() {
        NoonSalesDailySyncCommand command = new NoonSalesDailySyncCommand(
                NoonSyncScope.of(10002L, 245027L, "STR245027-SAU", "SA"),
                LocalDate.of(2026, 5, 19),
                10003L
        );

        assertFalse(command.isRollingCorrection());
    }

    private String csvFixture(LocalDate date) {
        return String.join("\n",
                csvHeader(),
                date + ",SAMPLE-SKU-001,noon,Z57C90A4184D0CFD75218Z,Z57C90A4184D0CFD75218Z-1,bags_luggage,handbag,shopper_tote,papersay,SAR,Sanitized sample product,SA,10,10,1,1,,19.8,100,10,19.8"
        );
    }

    private String headersOnlyCsv() {
        return csvHeader();
    }

    private String csvMissingRequiredColumn() {
        return String.join("\n",
                csvHeader().replace("Gross_Units,", ""),
                "2026-05-19,SAMPLE-SKU-001,noon,Z57C90A4184D0CFD75218Z,Z57C90A4184D0CFD75218Z-1,bags_luggage,handbag,shopper_tote,papersay,SAR,Sanitized sample product,SA,10,10,1,,19.8,100,10,19.8"
        );
    }

    private String csvWithOneValidAndOneMalformedRow() {
        return String.join("\n",
                csvHeader(),
                "2026-05-01,SAMPLE-SKU-001,noon,Z57C90A4184D0CFD75218Z,Z57C90A4184D0CFD75218Z-1,bags_luggage,handbag,shopper_tote,papersay,SAR,Sanitized sample product,SA,10,10,1,1,,19.8,100,10,19.8",
                "2026-05-02,SAMPLE-SKU-002,noon,ZBAD,ZBAD-1,bags_luggage,handbag,shopper_tote,papersay,SAR,Bad sample,SA,10,10,bad,1,,19.8,100,10,19.8"
        );
    }

    private String csvHeader() {
        return "Visit_Date,Partner_SKU,Mp_Code,SKU_CONFIG,SKU,Family,Product_Type,Product_Subtype,Brand,Currency_Code,Product_Title,Country_Code,Your_Visitors,Total_Visitors,Gross_Units,Shipped_Units,Cancelled_Units,Revenue_Shipped,Buy_Box_Visitor_Percentage,Conversion_Visitors_Percentage,ASP_shipped_Percentage";
    }

    private static class TestHarness {
        private final FixtureSalesReportProvider provider;
        private final InMemorySalesFactRepository factRepository = new InMemorySalesFactRepository();
        private final NoonSyncFoundationService foundationService = new NoonSyncFoundationService();
        private final SalesSyncTaskService salesSyncTaskService;

        private TestHarness(String csv) {
            this.provider = new FixtureSalesReportProvider(csv);
            this.salesSyncTaskService = new SalesSyncTaskService(
                    new InMemorySalesSyncTaskRepository(),
                    provider,
                    new NoonSalesCsvImportService(new NoonProductViewsSalesReportParser(), factRepository)
            );
        }

        private NoonSalesSyncBridgeService bridge() {
            return new NoonSalesSyncBridgeService(foundationService, salesSyncTaskService);
        }

        private FixtureSalesReportProvider provider() {
            return provider;
        }

        private InMemorySalesFactRepository factRepository() {
            return factRepository;
        }
    }

    private static class FixtureSalesReportProvider implements NoonSalesReportProvider {
        private final String csv;
        private NoonSalesReportRequest lastRequest;

        private FixtureSalesReportProvider(String csv) {
            this.csv = csv;
        }

        @Override
        public NoonSalesReportPayload fetch(NoonSalesReportRequest request) {
            lastRequest = request;
            return new NoonSalesReportPayload("fixture-productviewsandsalesdata.csv", csv);
        }

        private NoonSalesReportRequest lastRequest() {
            return lastRequest;
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

        private int count() {
            return facts.size();
        }

        private DailySalesFact singleFact() {
            return facts.values().iterator().next();
        }
    }
}
