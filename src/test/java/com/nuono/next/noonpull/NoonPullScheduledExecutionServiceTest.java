package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nuono.next.noonads.NoonAdvertisingCampaignFact;
import com.nuono.next.noonads.NoonAdvertisingImportRepository;
import com.nuono.next.noonads.NoonAdvertisingImportService;
import com.nuono.next.noonads.NoonAdvertisingQueryFact;
import com.nuono.next.noonads.NoonAdvertisingReportAdapter;
import com.nuono.next.noonads.NoonAdvertisingReportBatch;
import com.nuono.next.noonads.NoonAdvertisingReportDescriptor;
import com.nuono.next.noonads.NoonAdvertisingReportProvider;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnExportQueryService;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnReceivedReportImportService;
import com.nuono.next.officialwarehouse.OfficialWarehouseInventorySyncService;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.FbnExportCreateCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.FbnReceivedImportCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.InventorySyncCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.FbnExportCreateView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.FbnExportStatusView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.FbnReceivedImportResultView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.InventorySyncResultView;
import com.nuono.next.orderfinance.NoonFinanceTransactionFact;
import com.nuono.next.orderfinance.NoonFinanceTransactionFactWriter;
import com.nuono.next.orderfinance.NoonFinanceTransactionReportAdapter;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private CapturingProductProjectionWriter productWriter;
    private NoonPullScheduledExecutionService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-24T00:30:00Z"), SHANGHAI);
        repository = new InMemoryNoonPullRepository();
        foundationService = new NoonPullFoundationService(repository, clock, new NoonPullFailurePolicy(clock));
        writer = new InMemorySalesFactWriter();
        productWriter = new CapturingProductProjectionWriter();
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
                new NoonProductListPullAdapter(productWriter),
                new NoonSalesReportAdapter(writer),
                new NoonOrderReportAdapter((fact) -> {
                }, clock),
                (Supplier<NoonReportProvider>) this::salesProviderForRequestDate,
                (Supplier<NoonReportProvider>) () -> null,
                (Supplier<NoonSalesPageQueryProvider>) () -> null,
                (Supplier<NoonProductInterfaceSmokeProvider>) () -> null,
                true
        );
    }

    @Test
    void shouldWaitForLateReadyTimeBeforeCreatingSalesReport() {
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

        assertEquals(0, result.getCreatedTaskCount());
        assertEquals(0, result.getExecutedTaskCount());
        assertEquals(0, repository.listTasks().size());
        assertEquals(0, writer.facts.size());
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
    void shouldNotExecuteExistingScheduledTaskUnderMaintenance() {
        NoonPullPlanRecord plan = foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(10002L)
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.SALES)
                .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                .scheduleExpression("daily")
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
        service.setMaintenanceGate((ownerUserId, storeCode, siteCode) -> true);

        NoonPullScheduledExecutionResult result = service.runOnce();

        assertEquals(0, result.getExecutedTaskCount());
        assertEquals(1, result.getSkippedTaskCount());
        assertEquals(NoonPullTaskStatus.QUEUED, repository.selectTask(queued.getId()).getStatus());
        assertEquals(0, writer.facts.size());
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
    void shouldCreateAndExecuteOneSalesRollingWindowReportAfterLatestReadyTime() {
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
                new NoonProductListPullAdapter(productWriter),
                new NoonSalesReportAdapter(writer),
                new NoonOrderReportAdapter((fact) -> {
                }, clock),
                (Supplier<NoonReportProvider>) this::salesProviderForRequestDate,
                (Supplier<NoonReportProvider>) () -> null,
                (Supplier<NoonSalesPageQueryProvider>) () -> null,
                (Supplier<NoonProductInterfaceSmokeProvider>) () -> null,
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

        assertEquals(1, result.getCreatedTaskCount());
        assertEquals(1, result.getExecutedTaskCount());
        List<NoonPullTaskRecord> tasks = repository.listTasks();
        assertEquals(1, tasks.size());
        assertEquals(NoonPullTaskStatus.SUCCEEDED, tasks.get(0).getStatus());
        assertEquals("sales:2026-04-24..2026-05-23", tasks.get(0).getTargetIdentity());
        assertEquals(LocalDate.of(2026, 4, 24), tasks.get(0).getTargetDateFrom());
        assertEquals(LocalDate.of(2026, 5, 23), tasks.get(0).getTargetDateTo());
        assertEquals(1, writer.facts.size());
        NoonSalesDailyFact fact = writer.facts.get("10002|STR245027-NAE|AE|2026-04-24|PSKU-1");
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
                new NoonProductListPullAdapter(productWriter),
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
                (Supplier<NoonProductInterfaceSmokeProvider>) () -> null,
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
        Clock clock = Clock.fixed(Instant.parse("2026-05-24T12:30:00Z"), SHANGHAI);
        List<String> executionOrder = new ArrayList<>();
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
                new NoonProductListPullAdapter(productWriter),
                new NoonSalesReportAdapter(writer),
                new NoonOrderReportAdapter((fact) -> {
                }, clock),
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
                (Supplier<NoonProductInterfaceSmokeProvider>) () -> null,
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
        assertEquals("REPORT:2026-04-24", executionOrder.get(1));
    }

    @Test
    void shouldResumeReportRiskBackoffTaskWithoutExportAfterDelay() {
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
                new NoonProductListPullAdapter(productWriter),
                new NoonSalesReportAdapter(writer),
                new NoonOrderReportAdapter((fact) -> {
                }, clock),
                (Supplier<NoonReportProvider>) this::salesProviderForRequestDate,
                (Supplier<NoonReportProvider>) () -> null,
                (Supplier<NoonSalesPageQueryProvider>) () -> null,
                (Supplier<NoonProductInterfaceSmokeProvider>) () -> null,
                true
        );
        NoonPullPlanRecord plan = foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(10002L)
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.SALES)
                .triggerMode(NoonPullTriggerMode.MANUAL_BACKFILL)
                .scheduleExpression("manual")
                .build());
        NoonPullTaskRecord task = foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(plan.getOwnerUserId())
                .storeCode(plan.getStoreCode())
                .siteCode(plan.getSiteCode())
                .pullType(plan.getPullType())
                .dataDomain(plan.getDataDomain())
                .triggerMode(plan.getTriggerMode())
                .targetIdentity("sales:2026-04-24..2026-05-23")
                .targetDateFrom(LocalDate.of(2026, 4, 24))
                .targetDateTo(LocalDate.of(2026, 5, 23))
                .build()).orElseThrow();
        foundationService.markRunning(task.getId(), "test");
        NoonRiskBackoffHold hold = new NoonRiskBackoffHold();
        hold.setRiskType(NoonPullFailureType.BLOCKED_BY_RISK_CONTROL.code());
        hold.setAttemptCount(1);
        hold.setBlockedUntil(LocalDateTime.of(2026, 5, 24, 12, 29));
        hold.setSourceDomain("SALES");
        hold.setSourceTaskId(task.getId());
        foundationService.recordReportRiskBackoffDelay(task.getId(), hold, "sales report");

        NoonPullScheduledExecutionResult result = service.runOnce();

        assertEquals(0, result.getCreatedTaskCount());
        assertEquals(1, result.getExecutedTaskCount());
        NoonPullTaskRecord executed = repository.selectTask(task.getId());
        assertEquals(NoonPullTaskStatus.SUCCEEDED, executed.getStatus());
        assertEquals("EXP-SALES", executed.getReportExportId());
        assertEquals(1L, writer.facts.get("10002|STR245027-NAE|AE|2026-04-24|PSKU-1").unitsSold);
    }

    @Test
    void shouldExecuteScheduledOfficialWarehouseInventoryTask() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-24T15:01:00Z"), SHANGHAI);
        repository = new InMemoryNoonPullRepository();
        foundationService = new NoonPullFoundationService(repository, clock, new NoonPullFailurePolicy(clock));
        CapturingOfficialWarehouseInventorySyncService inventorySyncService =
                new CapturingOfficialWarehouseInventorySyncService();
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
                new NoonProductListPullAdapter(productWriter),
                new NoonSalesReportAdapter(writer),
                new NoonOrderReportAdapter((fact) -> {
                }, clock),
                null,
                inventorySyncService,
                null,
                null,
                (Supplier<NoonReportProvider>) () -> null,
                (Supplier<NoonReportProvider>) () -> null,
                (Supplier<NoonReportProvider>) () -> null,
                (Supplier<NoonSalesPageQueryProvider>) () -> null,
                (Supplier<NoonProductInterfaceSmokeProvider>) () -> null,
                true
        );
        foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(307L)
                .storeCode("STR108065-NSA")
                .siteCode("SA")
                .pullType(NoonPullType.INTERFACE)
                .dataDomain(NoonPullDataDomain.OFFICIAL_WAREHOUSE_INVENTORY)
                .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                .scheduleExpression("daily official warehouse inventory after 23:00 Asia/Shanghai")
                .build());

        NoonPullScheduledExecutionResult result = service.runOnce();

        assertEquals(1, result.getCreatedTaskCount());
        assertEquals(1, result.getExecutedTaskCount());
        NoonPullTaskRecord task = repository.listTasks().get(0);
        assertEquals(NoonPullTaskStatus.SUCCEEDED, task.getStatus());
        assertEquals("official-warehouse-inventory:2026-05-24", task.getTargetIdentity());
        assertEquals("official-warehouse-inventory-" + task.getId() + "-INV-1", task.getSourceBatchId());
        assertEquals(307L, inventorySyncService.access.getBusinessOwnerUserId());
        assertEquals(307L, inventorySyncService.access.getSessionUserId());
        assertEquals("STR108065-NSA", inventorySyncService.command.storeCode);
        assertEquals("SA", inventorySyncService.command.siteCode);
    }

    @Test
    void shouldExecuteScheduledOfficialWarehouseFbnReceivedReportTask() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-24T15:31:00Z"), SHANGHAI);
        repository = new InMemoryNoonPullRepository();
        foundationService = new NoonPullFoundationService(repository, clock, new NoonPullFailurePolicy(clock));
        CapturingOfficialWarehouseFbnExportQueryService fbnExportQueryService =
                new CapturingOfficialWarehouseFbnExportQueryService();
        CapturingOfficialWarehouseFbnReceivedImportService fbnReceivedImportService =
                new CapturingOfficialWarehouseFbnReceivedImportService();
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
                new NoonProductListPullAdapter(productWriter),
                new NoonSalesReportAdapter(writer),
                new NoonOrderReportAdapter((fact) -> {
                }, clock),
                null,
                null,
                fbnExportQueryService,
                fbnReceivedImportService,
                (Supplier<NoonReportProvider>) () -> null,
                (Supplier<NoonReportProvider>) () -> null,
                (Supplier<NoonReportProvider>) () -> null,
                (Supplier<NoonSalesPageQueryProvider>) () -> null,
                (Supplier<NoonProductInterfaceSmokeProvider>) () -> null,
                true
        );
        foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(307L)
                .storeCode("STR108065-NSA")
                .siteCode("SA")
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.OFFICIAL_WAREHOUSE_FBN_RECEIVED)
                .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                .scheduleExpression("daily FBN received report after 23:30 Asia/Shanghai")
                .build());

        NoonPullScheduledExecutionResult result = service.runOnce();

        assertEquals(1, result.getCreatedTaskCount());
        assertEquals(1, result.getExecutedTaskCount());
        NoonPullTaskRecord task = repository.listTasks().get(0);
        assertEquals(NoonPullTaskStatus.SUCCEEDED, task.getStatus());
        assertEquals("official-warehouse-fbn-received:2026-05-23..2026-05-23", task.getTargetIdentity());
        assertEquals("EXP-FBN-1", task.getReportExportId());
        assertEquals("READY", task.getReportExportStatus());
        assertEquals("official-warehouse-fbn-received-" + task.getId() + "-IMP-1", task.getSourceBatchId());
        assertEquals("fbn_inbound_fbnreceivedreport", fbnExportQueryService.createCommand.exportCategoryCode);
        assertEquals("2026-05-23", fbnExportQueryService.createCommand.fromDate);
        assertEquals("2026-05-23", fbnExportQueryService.createCommand.toDate);
        assertEquals("EXP-FBN-1", fbnReceivedImportService.exportCode);
        assertEquals("STR108065-NSA", fbnReceivedImportService.command.storeCode);
        assertEquals("SA", fbnReceivedImportService.command.siteCode);
    }

    @Test
    void shouldLimitSalesReportExecutionsPerTickToAvoidBurstingNoonAtLatestWindow() {
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
                new NoonProductListPullAdapter(productWriter),
                new NoonSalesReportAdapter(writer),
                new NoonOrderReportAdapter((fact) -> {
                }, clock),
                (Supplier<NoonReportProvider>) this::salesProviderForRequestDate,
                (Supplier<NoonReportProvider>) () -> null,
                (Supplier<NoonSalesPageQueryProvider>) () -> null,
                (Supplier<NoonProductInterfaceSmokeProvider>) () -> null,
                true
        );
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

    @Test
    void shouldCreateExecuteAndApplyScheduledProductInterfaceTask() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-24T00:30:00Z"), SHANGHAI);
        repository = new InMemoryNoonPullRepository();
        foundationService = new NoonPullFoundationService(repository, clock, new NoonPullFailurePolicy(clock));
        productWriter = new CapturingProductProjectionWriter();
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
                new NoonProductListPullAdapter(productWriter),
                new NoonSalesReportAdapter(writer),
                new NoonOrderReportAdapter((fact) -> {
                }, clock),
                (Supplier<NoonReportProvider>) () -> null,
                (Supplier<NoonReportProvider>) () -> null,
                (Supplier<NoonSalesPageQueryProvider>) () -> null,
                (Supplier<NoonProductInterfaceSmokeProvider>) () -> (request, pageNumber) -> NoonInterfacePullPage.builder()
                        .items(List.of(Map.of(
                                "sku_parent", "Z-PRODUCT-1",
                                "sku", "Z-PRODUCT-1-1",
                                "title", "Scheduled Product"
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
                .pullType(NoonPullType.INTERFACE)
                .dataDomain(NoonPullDataDomain.PRODUCT)
                .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                .scheduleExpression("daily product offer list")
                .build());

        NoonPullScheduledExecutionResult result = service.runOnce();

        assertEquals(1, result.getCreatedTaskCount());
        assertEquals(1, result.getExecutedTaskCount());
        NoonPullTaskRecord task = repository.listTasks().get(0);
        assertEquals(NoonPullTaskStatus.SUCCEEDED, task.getStatus());
        assertEquals("product-list:2026-05-24..2026-05-24", task.getTargetIdentity());
        assertEquals(1, task.getProcessedItemCount());
        assertEquals("PRJ245027", productWriter.command.getProjectCode());
        assertEquals("STR245027-NAE", productWriter.command.getReferenceStoreCode());
        assertEquals(1, productWriter.command.getProductSeeds().size());
    }

    @Test
    void shouldExecuteScheduledFinanceTransactionReportTask() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-24T14:31:00Z"), SHANGHAI);
        repository = new InMemoryNoonPullRepository();
        foundationService = new NoonPullFoundationService(repository, clock, new NoonPullFailurePolicy(clock));
        CapturingFinanceTransactionFactWriter financeWriter = new CapturingFinanceTransactionFactWriter();
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
                new NoonProductListPullAdapter(productWriter),
                new NoonSalesReportAdapter(writer),
                new NoonOrderReportAdapter((fact) -> {
                }, clock),
                new NoonFinanceTransactionReportAdapter(financeWriter),
                (Supplier<NoonReportProvider>) () -> null,
                (Supplier<NoonReportProvider>) () -> null,
                (Supplier<NoonReportProvider>) this::financeProvider,
                (Supplier<NoonSalesPageQueryProvider>) () -> null,
                (Supplier<NoonProductInterfaceSmokeProvider>) () -> null,
                true
        );
        foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(307L)
                .storeCode("STR108065-NSA")
                .siteCode("SA")
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.FINANCE_TRANSACTION)
                .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                .scheduleExpression("daily finance transaction after 22:30 Asia/Shanghai")
                .build());

        NoonPullScheduledExecutionResult result = service.runOnce();

        assertEquals(1, result.getCreatedTaskCount());
        assertEquals(1, result.getExecutedTaskCount());
        NoonPullTaskRecord task = repository.listTasks().get(0);
        assertEquals(NoonPullTaskStatus.SUCCEEDED, task.getStatus());
        assertEquals("finance-transactions:2026-05-17..2026-05-23", task.getTargetIdentity());
        assertEquals(1, financeWriter.facts.size());
        NoonFinanceTransactionFact fact = financeWriter.facts.get(0);
        assertEquals(307L, fact.getOwnerUserId());
        assertEquals("STR108065-NSA", fact.getStoreCode());
        assertEquals("SA", fact.getSiteCode());
        assertEquals(LocalDate.of(2026, 5, 23), fact.getTransactionDate());
        assertEquals("PAPERSAYSB359", fact.getPartnerSku());
        assertEquals("SAR", fact.getCurrency());
        assertEquals("12.34", fact.getNetProceeds().toPlainString());
        assertEquals("-1.23", fact.getReferralFeeIncludingVat().toPlainString());
        assertEquals("-2.34", fact.getFulfillmentLogisticsFeesIncludingVat().toPlainString());
        assertEquals("8.77", fact.getTotalAmount().toPlainString());
    }

    @Test
    void shouldExecuteScheduledNoonAdvertisingReportTask() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-24T00:01:00Z"), SHANGHAI);
        repository = new InMemoryNoonPullRepository();
        foundationService = new NoonPullFoundationService(repository, clock, new NoonPullFailurePolicy(clock));
        RecordingNoonAdvertisingImportRepository adsRepository = new RecordingNoonAdvertisingImportRepository();
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
                new NoonProductListPullAdapter(productWriter),
                new NoonSalesReportAdapter(writer),
                new NoonOrderReportAdapter((fact) -> {
                }, clock),
                null,
                new NoonAdvertisingReportAdapter(new NoonAdvertisingImportService(adsRepository)),
                (Supplier<NoonReportProvider>) () -> null,
                (Supplier<NoonReportProvider>) () -> null,
                (Supplier<NoonReportProvider>) () -> null,
                (Supplier<NoonAdvertisingReportProvider>) this::adsProvider,
                (Supplier<NoonSalesPageQueryProvider>) () -> null,
                (Supplier<NoonProductInterfaceSmokeProvider>) () -> null,
                true
        );
        foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(307L)
                .storeCode("STR69486-NSA")
                .siteCode("SA")
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.NOON_ADVERTISING)
                .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                .scheduleExpression("noon ads T-1 daily after 08:00 Asia/Shanghai")
                .build());

        NoonPullScheduledExecutionResult result = service.runOnce();

        assertEquals(1, result.getCreatedTaskCount());
        assertEquals(1, result.getExecutedTaskCount());
        NoonPullTaskRecord task = repository.listTasks().get(0);
        assertEquals(NoonPullTaskStatus.SUCCEEDED, task.getStatus());
        assertEquals("ads:2026-05-23..2026-05-23", task.getTargetIdentity());
        assertEquals("EXP-ADS", task.getReportExportId());
        assertEquals("READY", task.getReportExportStatus());
        assertEquals(1, adsRepository.insertedBatches.size());
        assertEquals(1, adsRepository.campaignFacts.size());
        assertEquals(1, adsRepository.queryFacts.size());
        assertEquals("PRJ69486", adsRepository.insertedBatches.get(0).getProjectCode());
        assertEquals("STR69486-NSA", adsRepository.insertedBatches.get(0).getStoreCode());
        assertEquals("SA", adsRepository.insertedBatches.get(0).getSiteCode());
        assertEquals("C_ADS_001", adsRepository.campaignFacts.get(0).getCampaignCode());
        assertEquals("notebook", adsRepository.queryFacts.get(0).getQueryText());
    }

    @Test
    void shouldLimitProductInterfaceExecutionsPerTickToAvoidBurstingNoon() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-24T00:30:00Z"), SHANGHAI);
        repository = new InMemoryNoonPullRepository();
        foundationService = new NoonPullFoundationService(repository, clock, new NoonPullFailurePolicy(clock));
        productWriter = new CapturingProductProjectionWriter();
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
                new NoonProductListPullAdapter(productWriter),
                new NoonSalesReportAdapter(writer),
                new NoonOrderReportAdapter((fact) -> {
                }, clock),
                (Supplier<NoonReportProvider>) () -> null,
                (Supplier<NoonReportProvider>) () -> null,
                (Supplier<NoonSalesPageQueryProvider>) () -> null,
                (Supplier<NoonProductInterfaceSmokeProvider>) () -> (request, pageNumber) -> NoonInterfacePullPage.builder()
                        .items(List.of(Map.of(
                                "sku_parent", "Z-PRODUCT-" + request.getStoreCode(),
                                "sku", "Z-PRODUCT-" + request.getStoreCode() + "-1"
                        )))
                        .pageNumber(pageNumber)
                        .totalItems(1)
                        .requestCount(1)
                        .hasNextPage(false)
                        .build(),
                true
        );
        for (int i = 0; i < 3; i++) {
            foundationService.createPlan(NoonPullPlanDraft.builder()
                    .ownerUserId(10002L)
                    .storeCode("STR24502" + i + "-NAE")
                    .siteCode("AE")
                    .pullType(NoonPullType.INTERFACE)
                    .dataDomain(NoonPullDataDomain.PRODUCT)
                    .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                    .scheduleExpression("daily product offer list")
                    .build());
        }

        NoonPullScheduledExecutionResult result = service.runOnce();

        assertEquals(3, result.getCreatedTaskCount());
        assertEquals(1, result.getExecutedTaskCount());
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

    private NoonReportProvider financeProvider() {
        return new NoonReportProvider() {
            @Override
            public String createExport(NoonReportPullRequest request) {
                assertEquals(NoonPullDataDomain.FINANCE_TRANSACTION, request.getDataDomain());
                assertEquals(NoonFinanceTransactionReportDescriptor.DEFAULT_REPORT_TYPE, request.getReportType());
                assertEquals(LocalDate.of(2026, 5, 17), request.getDateFrom());
                assertEquals(LocalDate.of(2026, 5, 23), request.getDateTo());
                return "EXP-FINANCE";
            }

            @Override
            public NoonReportExportStatus pollExport(NoonReportPullRequest request, String exportId) {
                return NoonReportExportStatus.ready("https://download.test/finance.csv");
            }

            @Override
            public byte[] download(NoonReportPullRequest request, String downloadUrl) {
                String csv = "Contract,Contract Title,Reference Nr,Order Nr,Item Nr,Order Date,Transaction Date,Title,SKUs,Partner SKUs,Transaction Type,Currency,Net Proceeds,Referral Fee including VAT,Fullfilment & Logistics Fees including VAT,Shipping Credits including VAT,Other Order Fees including VAT,Order Subsidies including VAT,Non-Order Fees including VAT,Non-Order Subsidies including VAT,Others including VAT,Total\n"
                        + "NOON-SA,Saudi Contract,REF-1,ORDER-1,ITEM-1,2026-05-23,2026-05-23,Paper,SKU-1,PAPERSAYSB359,Order,SAR,12.34,-1.23,-2.34,0,0,0,0,0,0,8.77\n";
                return csv.getBytes(StandardCharsets.UTF_8);
            }
        };
    }

    private NoonAdvertisingReportProvider adsProvider() {
        return new NoonAdvertisingReportProvider() {
            @Override
            public String createExport(NoonReportPullRequest request) {
                assertEquals(NoonPullDataDomain.NOON_ADVERTISING, request.getDataDomain());
                assertEquals(NoonAdvertisingReportDescriptor.DEFAULT_REPORT_TYPE, request.getReportType());
                assertEquals(LocalDate.of(2026, 5, 23), request.getDateFrom());
                assertEquals(LocalDate.of(2026, 5, 23), request.getDateTo());
                return "EXP-ADS";
            }

            @Override
            public NoonReportExportStatus pollExport(NoonReportPullRequest request, String exportId) {
                return NoonReportExportStatus.ready("https://download.test/ads.csv");
            }

            @Override
            public byte[] download(NoonReportPullRequest request, String downloadUrl) {
                String csv = "row_type,project_code,campaign_code,campaign_name,ad_sku_code,partner_sku,query_text,"
                        + "query_kind,campaign_status,views,clicks,orders_count,spend_amount,ad_revenue\n"
                        + "campaign,PRJ69486,C_ADS_001,Summer core exact,ZADS001-1,PAPER001,,,active,"
                        + "1200,80,12,150.59,450.10\n"
                        + "query,PRJ69486,C_ADS_001,Summer core exact,ZADS001-1,PAPER001,notebook,exact,,"
                        + "90,10,2,21.57,82.30\n";
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

    private static final class CapturingProductProjectionWriter implements NoonProductProjectionWriter {
        private NoonProductProjectionWriteCommand command;

        @Override
        public void write(NoonProductProjectionWriteCommand command) {
            this.command = command;
        }
    }

    private static final class CapturingFinanceTransactionFactWriter implements NoonFinanceTransactionFactWriter {
        private final List<NoonFinanceTransactionFact> facts = new ArrayList<>();

        @Override
        public void upsert(NoonFinanceTransactionFact fact) {
            facts.add(fact);
        }
    }

    private static final class RecordingNoonAdvertisingImportRepository implements NoonAdvertisingImportRepository {
        private final List<NoonAdvertisingReportBatch> insertedBatches = new ArrayList<>();
        private final List<NoonAdvertisingCampaignFact> campaignFacts = new ArrayList<>();
        private final List<NoonAdvertisingQueryFact> queryFacts = new ArrayList<>();

        @Override
        public Long nextReportBatchId() {
            return 200001L + insertedBatches.size();
        }

        @Override
        public Long nextCampaignFactId() {
            return 210001L + campaignFacts.size();
        }

        @Override
        public Long nextQueryFactId() {
            return 220001L + queryFacts.size();
        }

        @Override
        public Long findReportBatchId(NoonAdvertisingReportBatch batch) {
            return null;
        }

        @Override
        public void insertReportBatch(NoonAdvertisingReportBatch batch) {
            insertedBatches.add(batch);
        }

        @Override
        public void upsertCampaignFact(NoonAdvertisingCampaignFact fact) {
            campaignFacts.add(fact);
        }

        @Override
        public void upsertQueryFact(NoonAdvertisingQueryFact fact) {
            queryFacts.add(fact);
        }
    }

    private static final class CapturingOfficialWarehouseInventorySyncService
            extends OfficialWarehouseInventorySyncService {
        private BusinessAccessContext access;
        private InventorySyncCommand command;

        private CapturingOfficialWarehouseInventorySyncService() {
            super(null, null, null);
        }

        @Override
        public InventorySyncResultView sync(BusinessAccessContext access, InventorySyncCommand command) {
            this.access = access;
            this.command = command;
            InventorySyncResultView result = new InventorySyncResultView();
            result.syncBatchId = "INV-1";
            result.storeCode = command.storeCode;
            result.siteCode = command.siteCode;
            result.fetchedRows = 3;
            result.insertedRows = 3;
            result.sourceType = "FBN_INVENTORY_API";
            return result;
        }
    }

    private static final class CapturingOfficialWarehouseFbnExportQueryService
            extends OfficialWarehouseFbnExportQueryService {
        private FbnExportCreateCommand createCommand;
        private String statusExportCode;

        private CapturingOfficialWarehouseFbnExportQueryService() {
            super(null);
        }

        @Override
        public FbnExportCreateView createExport(BusinessAccessContext access, FbnExportCreateCommand command) {
            this.createCommand = command;
            FbnExportCreateView view = new FbnExportCreateView();
            view.storeCode = command.storeCode;
            view.siteCode = command.siteCode;
            view.exportCode = "EXP-FBN-1";
            view.status = "CREATED";
            view.reportType = command.exportCategoryCode;
            view.fromDate = command.fromDate;
            view.toDate = command.toDate;
            view.sourceType = "FBN_REPORT_EXPORT_API";
            return view;
        }

        @Override
        public FbnExportStatusView exportStatus(
                BusinessAccessContext access,
                String storeCode,
                String siteCode,
                String exportCode,
                Boolean log
        ) {
            this.statusExportCode = exportCode;
            FbnExportStatusView view = new FbnExportStatusView();
            view.storeCode = storeCode;
            view.siteCode = siteCode;
            view.exportCode = exportCode;
            view.status = "COMPLETE";
            view.fileName = "fbn-received.csv";
            view.downloadUrl = "https://download.test/fbn-received.csv";
            view.totalRows = 2;
            view.sourceType = "FBN_REPORT_EXPORT_API";
            return view;
        }
    }

    private static final class CapturingOfficialWarehouseFbnReceivedImportService
            extends OfficialWarehouseFbnReceivedReportImportService {
        private String exportCode;
        private FbnReceivedImportCommand command;

        private CapturingOfficialWarehouseFbnReceivedImportService() {
            super(null, null, null, null);
        }

        @Override
        public FbnReceivedImportResultView importByExportCode(
                BusinessAccessContext access,
                String exportCode,
                FbnReceivedImportCommand command
        ) {
            this.exportCode = exportCode;
            this.command = command;
            FbnReceivedImportResultView view = new FbnReceivedImportResultView();
            view.importId = "IMP-1";
            view.storeCode = command.storeCode;
            view.siteCode = command.siteCode;
            view.exportCode = exportCode;
            view.reportType = OfficialWarehouseFbnReceivedReportImportService.REPORT_TYPE;
            view.status = "IMPORTED";
            view.totalRows = 2;
            view.validRows = 2;
            view.warningRows = 0;
            view.errorRows = 0;
            view.insertedReceiptLines = 2;
            view.sourceType = "FBN_REPORT_EXPORT_API";
            return view;
        }
    }
}
