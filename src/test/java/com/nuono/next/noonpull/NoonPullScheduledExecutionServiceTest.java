package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoonPullScheduledExecutionServiceTest {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private InMemoryNoonPullRepository repository;
    private NoonPullFoundationService foundationService;
    private InMemorySalesFactWriter writer;
    private NoonPullScheduledExecutionService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-24T00:30:00Z"), SHANGHAI);
        repository = new InMemoryNoonPullRepository();
        foundationService = new NoonPullFoundationService(repository, clock, new NoonPullFailurePolicy(clock));
        writer = new InMemorySalesFactWriter();
        service = new NoonPullScheduledExecutionService(
                new NoonPullScheduler(
                        foundationService,
                        clock,
                        new NoonOrderReportSchedulePolicy(clock),
                        new NoonOrderBackfillPlanner(),
                        new NoonSalesRetentionPolicy(clock),
                        (plan) -> true
                ),
                foundationService,
                new NoonReportPuller(foundationService),
                new NoonInterfacePuller(foundationService),
                new NoonSalesReportAdapter(writer),
                new NoonOrderReportAdapter((fact) -> {
                }, clock),
                (Supplier<NoonReportProvider>) this::salesProviderForRequestDate,
                (Supplier<NoonReportProvider>) () -> null,
                (Supplier<NoonSalesPageQueryProvider>) () -> null,
                true
        );
    }

    @Test
    void shouldCreateAndExecuteStableSalesReportAfterDailyReadyTime() {
        foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(10002L)
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.SALES)
                .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                .scheduleExpression("latest-day after 08:00 Asia/Shanghai")
                .build());

        NoonPullScheduledExecutionResult result = service.runOnce();

        assertEquals(1, result.getCreatedTaskCount());
        assertEquals(1, result.getExecutedTaskCount());
        List<NoonPullTaskRecord> tasks = repository.listTasks();
        assertEquals(1, tasks.size());
        assertEquals(NoonPullTaskStatus.SUCCEEDED, tasks.get(0).getStatus());
        assertEquals(1, writer.facts.size());
        assertEquals(1L, writer.facts.get("10002|STR245027-NAE|AE|2026-05-22|PSKU-1").unitsSold);
    }

    @Test
    void shouldExecuteExistingQueuedSalesReportTaskWhenSchedulerCreatesNoNewTask() {
        NoonPullPlanRecord plan = foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(10002L)
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.SALES)
                .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                .scheduleExpression("latest-day after 08:00 Asia/Shanghai")
                .build());
        NoonPullTaskRecord queued = foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(plan.getOwnerUserId())
                .storeCode(plan.getStoreCode())
                .siteCode(plan.getSiteCode())
                .pullType(plan.getPullType())
                .dataDomain(plan.getDataDomain())
                .triggerMode(plan.getTriggerMode())
                .targetIdentity("sales:2026-05-22")
                .targetDateFrom(LocalDate.of(2026, 5, 22))
                .targetDateTo(LocalDate.of(2026, 5, 22))
                .build()).orElseThrow();

        NoonPullScheduledExecutionResult result = service.runOnce();

        assertEquals(0, result.getCreatedTaskCount());
        assertEquals(1, result.getExecutedTaskCount());
        NoonPullTaskRecord executed = repository.selectTask(queued.getId());
        assertEquals(NoonPullTaskStatus.SUCCEEDED, executed.getStatus());
        assertEquals(1, writer.facts.size());
        assertEquals(1L, writer.facts.get("10002|STR245027-NAE|AE|2026-05-22|PSKU-1").unitsSold);
    }

    @Test
    void shouldExecuteExistingQueuedTaskOutsideRecentTaskWindow() {
        NoonPullPlanRecord plan = foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(10002L)
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.SALES)
                .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                .scheduleExpression("latest-day after 08:00 Asia/Shanghai")
                .build());
        NoonPullTaskRecord queued = foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(plan.getOwnerUserId())
                .storeCode(plan.getStoreCode())
                .siteCode(plan.getSiteCode())
                .pullType(plan.getPullType())
                .dataDomain(plan.getDataDomain())
                .triggerMode(plan.getTriggerMode())
                .targetIdentity("sales:2026-05-22")
                .targetDateFrom(LocalDate.of(2026, 5, 22))
                .targetDateTo(LocalDate.of(2026, 5, 22))
                .build()).orElseThrow();
        for (int i = 0; i < 210; i++) {
            NoonPullTaskRecord recent = foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                    .ownerUserId(plan.getOwnerUserId())
                    .storeCode(plan.getStoreCode())
                    .siteCode(plan.getSiteCode())
                    .pullType(plan.getPullType())
                    .dataDomain(plan.getDataDomain())
                    .triggerMode(plan.getTriggerMode())
                    .targetIdentity("sales:recent:" + i)
                    .targetDateFrom(LocalDate.of(2026, 1, 1))
                    .targetDateTo(LocalDate.of(2026, 1, 1))
                    .build()).orElseThrow();
            foundationService.markSucceeded(recent.getId(), "batch-" + i, "done");
        }
        repository.limitListTasksToRecent(200);

        NoonPullScheduledExecutionResult result = service.runOnce();

        assertEquals(0, result.getCreatedTaskCount());
        assertEquals(1, result.getExecutedTaskCount());
        assertEquals(NoonPullTaskStatus.SUCCEEDED, repository.selectTask(queued.getId()).getStatus());
        assertEquals(1L, writer.facts.get("10002|STR245027-NAE|AE|2026-05-22|PSKU-1").unitsSold);
    }

    @Test
    void shouldCreateAndExecuteStableAndLatestSalesReportAfterLatestReadyTime() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-24T12:30:00Z"), SHANGHAI);
        repository = new InMemoryNoonPullRepository();
        foundationService = new NoonPullFoundationService(repository, clock, new NoonPullFailurePolicy(clock));
        writer = new InMemorySalesFactWriter();
        service = new NoonPullScheduledExecutionService(
                new NoonPullScheduler(
                        foundationService,
                        clock,
                        new NoonOrderReportSchedulePolicy(clock),
                        new NoonOrderBackfillPlanner(),
                        new NoonSalesRetentionPolicy(clock),
                        (plan) -> true
                ),
                foundationService,
                new NoonReportPuller(foundationService),
                new NoonInterfacePuller(foundationService),
                new NoonSalesReportAdapter(writer),
                new NoonOrderReportAdapter((fact) -> {
                }, clock),
                (Supplier<NoonReportProvider>) this::salesProviderForRequestDate,
                (Supplier<NoonReportProvider>) () -> null,
                (Supplier<NoonSalesPageQueryProvider>) () -> null,
                true
        );
        foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(10002L)
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.SALES)
                .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                .scheduleExpression("latest-day after 08:00 Asia/Shanghai")
                .build());

        NoonPullScheduledExecutionResult result = service.runOnce();

        assertEquals(2, result.getCreatedTaskCount());
        assertEquals(2, result.getExecutedTaskCount());
        List<NoonPullTaskRecord> tasks = repository.listTasks();
        assertEquals(2, tasks.size());
        assertEquals(NoonPullTaskStatus.SUCCEEDED, tasks.get(0).getStatus());
        assertEquals(NoonPullTaskStatus.SUCCEEDED, tasks.get(1).getStatus());
        assertEquals(2, writer.facts.size());
        assertEquals(1L, writer.facts.get("10002|STR245027-NAE|AE|2026-05-22|PSKU-1").unitsSold);
        NoonSalesDailyFact fact = writer.facts.get("10002|STR245027-NAE|AE|2026-05-23|PSKU-1");
        assertEquals(1L, fact.unitsSold);
        assertEquals("49.50", fact.salesAmount.toPlainString());
    }

    @Test
    void shouldExecuteScheduledSalesPageQueryTask() {
        service = new NoonPullScheduledExecutionService(
                new NoonPullScheduler(
                        foundationService,
                        Clock.fixed(Instant.parse("2026-05-24T00:30:00Z"), SHANGHAI),
                        new NoonOrderReportSchedulePolicy(Clock.fixed(Instant.parse("2026-05-24T00:30:00Z"), SHANGHAI)),
                        new NoonOrderBackfillPlanner(),
                        new NoonSalesRetentionPolicy(Clock.fixed(Instant.parse("2026-05-24T00:30:00Z"), SHANGHAI)),
                        (plan) -> true
                ),
                foundationService,
                new NoonReportPuller(foundationService),
                new NoonInterfacePuller(foundationService),
                new NoonSalesReportAdapter(writer),
                new NoonOrderReportAdapter((fact) -> {
                }, Clock.fixed(Instant.parse("2026-05-24T00:30:00Z"), SHANGHAI)),
                (Supplier<NoonReportProvider>) () -> null,
                (Supplier<NoonReportProvider>) () -> null,
                (Supplier<NoonSalesPageQueryProvider>) () -> (request, pageNumber) -> NoonInterfacePullPage.builder()
                        .items(List.of(Map.of(
                                "partner_sku", "MILKYWAYA09",
                                "sku", "Z580978E7ED8F9491B50BZ-1",
                                "status", "Delivered"
                        )))
                        .pageNumber(pageNumber)
                        .totalItems(1)
                        .requestCount(1)
                        .hasNextPage(false)
                        .build(),
                true
        );
        foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(10002L)
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .pullType(NoonPullType.PAGE_QUERY)
                .dataDomain(NoonPullDataDomain.SALES)
                .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                .scheduleExpression("latest-day page-query after 08:00 Asia/Shanghai")
                .build());

        NoonPullScheduledExecutionResult result = service.runOnce();

        assertEquals(1, result.getCreatedTaskCount());
        assertEquals(1, result.getExecutedTaskCount());
        NoonPullTaskRecord task = repository.listTasks().get(0);
        assertEquals(NoonPullType.PAGE_QUERY, task.getPullType());
        assertEquals(NoonPullTaskStatus.SUCCEEDED, task.getStatus());
        assertEquals(1, task.getProcessedItemCount());
        assertEquals("ready", task.getReadinessState());
    }

    @Test
    void shouldExecuteScheduledSalesPageQueryBeforeSalesReport() {
        List<String> executionOrder = new ArrayList<>();
        service = new NoonPullScheduledExecutionService(
                new NoonPullScheduler(
                        foundationService,
                        Clock.fixed(Instant.parse("2026-05-24T00:30:00Z"), SHANGHAI),
                        new NoonOrderReportSchedulePolicy(Clock.fixed(Instant.parse("2026-05-24T00:30:00Z"), SHANGHAI)),
                        new NoonOrderBackfillPlanner(),
                        new NoonSalesRetentionPolicy(Clock.fixed(Instant.parse("2026-05-24T00:30:00Z"), SHANGHAI)),
                        (plan) -> true
                ),
                foundationService,
                new NoonReportPuller(foundationService),
                new NoonInterfacePuller(foundationService),
                new NoonSalesReportAdapter(writer),
                new NoonOrderReportAdapter((fact) -> {
                }, Clock.fixed(Instant.parse("2026-05-24T00:30:00Z"), SHANGHAI)),
                (Supplier<NoonReportProvider>) () -> salesProviderRecordingOrder(executionOrder),
                (Supplier<NoonReportProvider>) () -> null,
                (Supplier<NoonSalesPageQueryProvider>) () -> (request, pageNumber) -> {
                    executionOrder.add("PAGE_QUERY:" + request.getDateFrom());
                    return NoonInterfacePullPage.builder()
                            .items(List.of(Map.of(
                                    "partner_sku", "MILKYWAYA09",
                                    "sku", "Z580978E7ED8F9491B50BZ-1",
                                    "status", "Delivered"
                            )))
                            .pageNumber(pageNumber)
                            .totalItems(1)
                            .requestCount(1)
                            .hasNextPage(false)
                            .build();
                },
                true
        );
        foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(10002L)
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.SALES)
                .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                .scheduleExpression("daily report")
                .build());
        foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(10002L)
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .pullType(NoonPullType.PAGE_QUERY)
                .dataDomain(NoonPullDataDomain.SALES)
                .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                .scheduleExpression("daily page-query")
                .build());

        NoonPullScheduledExecutionResult result = service.runOnce();

        assertEquals(2, result.getCreatedTaskCount());
        assertEquals(2, result.getExecutedTaskCount());
        assertEquals("PAGE_QUERY:2026-05-23", executionOrder.get(0));
        assertEquals("REPORT:2026-05-22", executionOrder.get(1));
    }

    @Test
    void shouldLimitSalesReportExecutionsPerTickToAvoidBurstingNoonAtLatestWindow() {
        for (int i = 0; i < 6; i++) {
            foundationService.createPlan(NoonPullPlanDraft.builder()
                    .ownerUserId(10002L)
                    .storeCode("STR-SALES-" + i)
                    .siteCode(i % 2 == 0 ? "AE" : "SA")
                    .pullType(NoonPullType.REPORT)
                    .dataDomain(NoonPullDataDomain.SALES)
                    .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                    .scheduleExpression("latest-day after 08:00 Asia/Shanghai")
                    .build());
        }

        NoonPullScheduledExecutionResult result = service.runOnce();

        assertEquals(6, result.getCreatedTaskCount());
        assertEquals(4, result.getExecutedTaskCount());
        long queued = repository.listTasks().stream()
                .filter((task) -> task.getStatus() == NoonPullTaskStatus.QUEUED)
                .count();
        assertEquals(2L, queued);
    }

    private NoonReportProvider salesProvider(String csv) {
        return new NoonReportProvider() {
            @Override
            public String createExport(NoonReportPullRequest request) {
                return "EXP-SALES";
            }

            @Override
            public NoonReportExportStatus pollExport(NoonReportPullRequest request, String exportId) {
                return NoonReportExportStatus.ready("https://download.test/sales.csv");
            }

            @Override
            public byte[] download(NoonReportPullRequest request, String downloadUrl) {
                return csv.getBytes(StandardCharsets.UTF_8);
            }
        };
    }

    private NoonReportProvider salesProviderForRequestDate() {
        return new NoonReportProvider() {
            @Override
            public String createExport(NoonReportPullRequest request) {
                return "EXP-SALES";
            }

            @Override
            public NoonReportExportStatus pollExport(NoonReportPullRequest request, String exportId) {
                return NoonReportExportStatus.ready("https://download.test/sales.csv");
            }

            @Override
            public byte[] download(NoonReportPullRequest request, String downloadUrl) {
                String csv = "Visit_Date,Partner_SKU,SKU,Currency_Code,Shipped_Units,Revenue_Shipped\n"
                        + request.getDateFrom() + ",PSKU-1,SKU-1,AED,1,49.50\n";
                return csv.getBytes(StandardCharsets.UTF_8);
            }
        };
    }

    private NoonReportProvider salesProviderRecordingOrder(List<String> executionOrder) {
        return new NoonReportProvider() {
            @Override
            public String createExport(NoonReportPullRequest request) {
                executionOrder.add("REPORT:" + request.getDateFrom());
                return "EXP-SALES";
            }

            @Override
            public NoonReportExportStatus pollExport(NoonReportPullRequest request, String exportId) {
                return NoonReportExportStatus.ready("https://download.test/sales.csv");
            }

            @Override
            public byte[] download(NoonReportPullRequest request, String downloadUrl) {
                String csv = "Visit_Date,Partner_SKU,SKU,Currency_Code,Shipped_Units,Revenue_Shipped\n"
                        + request.getDateFrom() + ",PSKU-1,SKU-1,AED,1,49.50\n";
                return csv.getBytes(StandardCharsets.UTF_8);
            }
        };
    }

    private static final class InMemorySalesFactWriter implements NoonSalesFactWriter {
        private final Map<String, NoonSalesDailyFact> facts = new LinkedHashMap<>();

        @Override
        public void upsert(NoonSalesDailyFact fact) {
            facts.put(fact.key(), fact);
        }
    }
}
