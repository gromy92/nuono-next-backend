package com.nuono.next.noonsync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.sales.DailySalesFact;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NoonSalesCorrectionPlannerServiceTest {

    private static final NoonSyncScope SCOPE = NoonSyncScope.of(10002L, 245027L, "STR245027-SAU", "SA");

    @Test
    void dailySyncNeverCreatesRollingCorrectionAndWeeklyCorrectionIsIndependent() {
        NoonSalesDailySyncCommand daily = new NoonSalesDailySyncCommand(
                SCOPE,
                LocalDate.of(2026, 5, 19),
                10003L
        );
        NoonSalesCorrectionPlannerService planner = plannerOn(LocalDate.of(2026, 5, 21));

        NoonSalesCorrectionPlan plan = planner.planWeeklyCorrection(new NoonSalesCorrectionPlanRequest(
                SCOPE,
                LocalDate.of(2026, 5, 19),
                null,
                NoonSalesCorrectionPolicy.defaultPolicy(),
                List.of()
        ));

        assertFalse(daily.isRollingCorrection());
        assertTrue(plan.isDue());
        assertFalse(plan.isDailySyncCoupled());
        assertEquals(NoonSyncTriggerMode.LOW_FREQUENCY_CORRECTION, plan.getTriggerMode());
        assertEquals(LocalDate.of(2026, 4, 5), plan.getTarget().getDateFrom());
        assertEquals(LocalDate.of(2026, 5, 19), plan.getTarget().getDateTo());
    }

    @Test
    void weeklyCorrectionDueAndNotDueUseFixedClock() {
        NoonSalesCorrectionPlannerService planner = plannerOn(LocalDate.of(2026, 5, 21));

        NoonSalesCorrectionPlan due = planner.planWeeklyCorrection(new NoonSalesCorrectionPlanRequest(
                SCOPE,
                LocalDate.of(2026, 5, 19),
                LocalDate.of(2026, 5, 14),
                NoonSalesCorrectionPolicy.defaultPolicy(),
                List.of()
        ));
        NoonSalesCorrectionPlan notDue = planner.planWeeklyCorrection(new NoonSalesCorrectionPlanRequest(
                SCOPE,
                LocalDate.of(2026, 5, 19),
                LocalDate.of(2026, 5, 18),
                NoonSalesCorrectionPolicy.defaultPolicy(),
                List.of()
        ));

        assertTrue(due.isDue());
        assertFalse(notDue.isDue());
        assertEquals(LocalDate.of(2026, 5, 25), notDue.getNextEligibleDate());
    }

    @Test
    void correctionWindowIsConfigurableOnlyInsideFirstVersionCandidateRange() {
        NoonSalesCorrectionPlannerService planner = plannerOn(LocalDate.of(2026, 5, 21));

        NoonSalesCorrectionPlan thirty = planner.planWeeklyCorrection(requestWithWindow(30));
        NoonSalesCorrectionPlan fortyFive = planner.planWeeklyCorrection(requestWithWindow(45));
        NoonSalesCorrectionPlan sixty = planner.planWeeklyCorrection(requestWithWindow(60));

        assertEquals(LocalDate.of(2026, 4, 20), thirty.getTarget().getDateFrom());
        assertEquals(LocalDate.of(2026, 4, 5), fortyFive.getTarget().getDateFrom());
        assertEquals(LocalDate.of(2026, 3, 21), sixty.getTarget().getDateFrom());
        assertEquals(List.of(30, 45, 60), sixty.getCandidateWindowDays());
        assertThrows(IllegalArgumentException.class, () -> planner.planWeeklyCorrection(requestWithWindow(21)));
        assertThrows(IllegalArgumentException.class, () -> planner.planWeeklyCorrection(requestWithWindow(180)));
        assertFalse(sixty.isScheduledDeepCorrection());
    }

    @Test
    void measurementEvidenceCapturesDurationRowsAndHistoricalChangesForCandidateWindows() {
        NoonSalesCorrectionPlannerService planner = plannerOn(LocalDate.of(2026, 5, 21));
        List<NoonSalesCorrectionMeasurement> measurements = List.of(
                new NoonSalesCorrectionMeasurement(30, Duration.ofSeconds(8), 7262, 3),
                new NoonSalesCorrectionMeasurement(45, Duration.ofSeconds(13), 9988, 7),
                new NoonSalesCorrectionMeasurement(60, Duration.ofSeconds(21), 12031, 11)
        );

        NoonSalesCorrectionPlan plan = planner.planWeeklyCorrection(new NoonSalesCorrectionPlanRequest(
                SCOPE,
                LocalDate.of(2026, 5, 19),
                null,
                NoonSalesCorrectionPolicy.defaultPolicy(),
                measurements
        ));

        assertEquals(3, plan.getMeasurements().size());
        assertEquals(45, plan.getMeasurements().get(1).getWindowDays());
        assertEquals(Duration.ofSeconds(13), plan.getMeasurements().get(1).getReportDuration());
        assertEquals(9988, plan.getMeasurements().get(1).getRowVolume());
        assertEquals(7, plan.getMeasurements().get(1).getObservedHistoricalChangeCount());
    }

    @Test
    void lowFrequencyCorrectionReusesSalesImportPathAndIdempotentFactUpdates() {
        TestHarness harness = new TestHarness(csvFixture(LocalDate.of(2026, 4, 20)));
        NoonSalesSyncBridgeService bridge = harness.bridge();
        NoonSalesCorrectionSyncCommand command = new NoonSalesCorrectionSyncCommand(
                SCOPE,
                LocalDate.of(2026, 4, 20),
                LocalDate.of(2026, 5, 19),
                45,
                10003L
        );

        NoonSalesSyncRunResult first = bridge.runLowFrequencyCorrection(command);
        NoonSalesSyncRunResult second = bridge.runLowFrequencyCorrection(command);

        assertEquals(NoonSyncTriggerMode.LOW_FREQUENCY_CORRECTION, first.getFoundationTask().getTriggerMode());
        assertEquals("low_frequency_correction", first.getSalesTask().getTriggerType());
        assertEquals(NoonSyncTaskStatus.SUCCEEDED, first.getFoundationTask().getStatus());
        assertEquals(1, harness.factRepository().count());
        assertEquals(9002L, harness.factRepository().singleFact().getSourceBatchId());
        assertEquals("succeeded", second.getSalesTask().getStatus());
    }

    private NoonSalesCorrectionPlannerService plannerOn(LocalDate date) {
        return new NoonSalesCorrectionPlannerService(Clock.fixed(
                Instant.parse(date + "T00:00:00Z"),
                ZoneId.of("UTC")
        ));
    }

    private NoonSalesCorrectionPlanRequest requestWithWindow(int windowDays) {
        return new NoonSalesCorrectionPlanRequest(
                SCOPE,
                LocalDate.of(2026, 5, 19),
                null,
                NoonSalesCorrectionPolicy.weekly(windowDays),
                List.of()
        );
    }

    private String csvFixture(LocalDate date) {
        return String.join("\n",
                "Visit_Date,Partner_SKU,Mp_Code,SKU_CONFIG,SKU,Family,Product_Type,Product_Subtype,Brand,Currency_Code,Product_Title,Country_Code,Your_Visitors,Total_Visitors,Gross_Units,Shipped_Units,Cancelled_Units,Revenue_Shipped,Buy_Box_Visitor_Percentage,Conversion_Visitors_Percentage,ASP_shipped_Percentage",
                date + ",SAMPLE-SKU-001,noon,Z57C90A4184D0CFD75218Z,Z57C90A4184D0CFD75218Z-1,bags_luggage,handbag,shopper_tote,papersay,SAR,Sanitized sample product,SA,10,10,1,1,,19.8,100,10,19.8"
        );
    }

    private static class TestHarness {
        private final InMemorySalesFactRepository factRepository = new InMemorySalesFactRepository();
        private final SalesSyncTaskService salesSyncTaskService;

        private TestHarness(String csv) {
            this.salesSyncTaskService = new SalesSyncTaskService(
                    new InMemorySalesSyncTaskRepository(),
                    new FixtureSalesReportProvider(csv),
                    new NoonSalesCsvImportService(new NoonProductViewsSalesReportParser(), factRepository)
            );
        }

        private NoonSalesSyncBridgeService bridge() {
            return new NoonSalesSyncBridgeService(new NoonSyncFoundationService(), salesSyncTaskService);
        }

        private InMemorySalesFactRepository factRepository() {
            return factRepository;
        }
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

        private int count() {
            return facts.size();
        }

        private DailySalesFact singleFact() {
            return facts.values().iterator().next();
        }
    }
}
