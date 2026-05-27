package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nuono.next.nooncompleteness.NoonDataCategory;
import com.nuono.next.nooncompleteness.NoonDataCompletenessQuery;
import com.nuono.next.nooncompleteness.NoonDataCompletenessRecord;
import com.nuono.next.nooncompleteness.NoonDataCompletenessRepository;
import com.nuono.next.nooncompleteness.NoonDataGapQuery;
import com.nuono.next.nooncompleteness.NoonDataGapStatus;
import com.nuono.next.nooncompleteness.NoonDataGapWindowRecord;
import com.nuono.next.nooncompleteness.NoonDataGapWindowType;
import com.nuono.next.nooncompleteness.NoonDataHistoryStatus;
import com.nuono.next.nooncompleteness.NoonDataLatestStatus;
import com.nuono.next.nooncompleteness.NoonGapTaskOutcomeReducer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
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
    void shouldCreateAndExecuteStableAndLatestSalesReportAfterDailyReadyTime() {
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

        assertEquals(3, result.getCreatedTaskCount());
        assertEquals(3, result.getExecutedTaskCount());
        assertEquals("PAGE_QUERY:2026-05-23", executionOrder.get(0));
    }

    @Test
    void shouldExecuteQueuedGapBackfillTaskCreatedBeforeScheduledTick() {
        NoonPullPlanRecord plan = foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(10002L)
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.SALES)
                .triggerMode(NoonPullTriggerMode.GAP_BACKFILL)
                .scheduleExpression("backfill:2025-11-26..2026-05-25;maxDays=31;maxWindows=1")
                .build());
        foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(10002L)
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.SALES)
                .triggerMode(NoonPullTriggerMode.GAP_BACKFILL)
                .targetIdentity("sales-product-views:2025-11-26..2026-05-25")
                .targetDateFrom(LocalDate.parse("2025-11-26"))
                .targetDateTo(LocalDate.parse("2026-05-25"))
                .build());

        NoonPullScheduledExecutionResult result = service.runOnce();

        assertEquals(0, result.getCreatedTaskCount());
        assertEquals(1, result.getExecutedTaskCount());
        NoonPullTaskRecord task = repository.listTasks().get(0);
        assertEquals(NoonPullTaskStatus.SUCCEEDED, task.getStatus());
        assertEquals(1, writer.facts.size());
        assertEquals(1L, writer.facts.get("10002|STR245027-NAE|AE|2025-11-26|PSKU-1").unitsSold);
    }

    @Test
    void shouldReduceQueuedGapBackfillOutcomeAfterScheduledExecutionSucceeds() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-24T00:30:00Z"), SHANGHAI);
        InMemoryCompletenessRepository completenessRepository = new InMemoryCompletenessRepository();
        NoonGapTaskOutcomeReducer reducer = new NoonGapTaskOutcomeReducer(completenessRepository, clock);
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
                true,
                (Supplier<NoonGapTaskOutcomeReducer>) () -> reducer
        );
        NoonPullPlanRecord plan = foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(10002L)
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.SALES)
                .triggerMode(NoonPullTriggerMode.GAP_BACKFILL)
                .scheduleExpression("backfill:2025-11-26..2026-05-25;maxDays=31;maxWindows=1")
                .build());
        NoonPullTaskRecord task = foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(10002L)
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.SALES)
                .triggerMode(NoonPullTriggerMode.GAP_BACKFILL)
                .targetIdentity("sales-product-views:2025-11-26..2026-05-25")
                .targetDateFrom(LocalDate.parse("2025-11-26"))
                .targetDateTo(LocalDate.parse("2026-05-25"))
                .build()).orElseThrow();
        completenessRepository.insertCompleteness(completenessRow(NoonDataCategory.SALES_PRODUCT_VIEWS));
        completenessRepository.insertGapWindow(gapWindow(
                NoonDataCategory.SALES_PRODUCT_VIEWS,
                NoonDataGapWindowType.HISTORY_BACKFILL,
                LocalDate.parse("2025-11-26"),
                LocalDate.parse("2026-05-25"),
                plan.getId(),
                task.getId()
        ));

        NoonPullScheduledExecutionResult result = service.runOnce();

        assertEquals(1, result.getExecutedTaskCount());
        NoonDataGapWindowRecord gap = completenessRepository.listGapWindows(new NoonDataGapQuery()).get(0);
        assertEquals(NoonDataGapStatus.SUCCEEDED, gap.getStatus());
        assertEquals(1, gap.getRowOrItemCount());
        NoonDataCompletenessRecord completeness = completenessRepository.listCompleteness(new NoonDataCompletenessQuery()).get(0);
        assertEquals(LocalDate.parse("2025-11-26"), completeness.getHistoryCoveredFrom());
        assertEquals(LocalDate.parse("2026-05-25"), completeness.getHistoryCoveredTo());
        assertEquals(NoonDataHistoryStatus.COMPLETE, completeness.getHistoryStatus());
    }

    @Test
    void shouldReduceTerminalGapBackfillTaskLeftFromEarlierScheduledRun() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-24T00:30:00Z"), SHANGHAI);
        InMemoryCompletenessRepository completenessRepository = new InMemoryCompletenessRepository();
        NoonGapTaskOutcomeReducer reducer = new NoonGapTaskOutcomeReducer(completenessRepository, clock);
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
                true,
                (Supplier<NoonGapTaskOutcomeReducer>) () -> reducer
        );
        NoonPullPlanRecord plan = foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(10002L)
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.SALES)
                .triggerMode(NoonPullTriggerMode.GAP_BACKFILL)
                .scheduleExpression("backfill:2025-11-26..2026-05-25;maxDays=31;maxWindows=1")
                .build());
        NoonPullTaskRecord task = foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(10002L)
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.SALES)
                .triggerMode(NoonPullTriggerMode.GAP_BACKFILL)
                .targetIdentity("sales-product-views:2025-11-26..2026-05-25")
                .targetDateFrom(LocalDate.parse("2025-11-26"))
                .targetDateTo(LocalDate.parse("2026-05-25"))
                .build()).orElseThrow();
        foundationService.markSucceeded(
                task.getId(),
                "noon-report-sales-130007-d8b89baa",
                "productviewsandsalesdata 2025-11-26..2026-05-25; imported=328; exceptions=0"
        );
        completenessRepository.insertCompleteness(completenessRow(NoonDataCategory.SALES_PRODUCT_VIEWS));
        completenessRepository.insertGapWindow(gapWindow(
                NoonDataCategory.SALES_PRODUCT_VIEWS,
                NoonDataGapWindowType.HISTORY_BACKFILL,
                LocalDate.parse("2025-11-26"),
                LocalDate.parse("2026-05-25"),
                plan.getId(),
                task.getId()
        ));

        NoonPullScheduledExecutionResult result = service.runOnce();

        assertEquals(0, result.getExecutedTaskCount());
        NoonDataGapWindowRecord gap = completenessRepository.listGapWindows(new NoonDataGapQuery()).get(0);
        assertEquals(NoonDataGapStatus.SUCCEEDED, gap.getStatus());
        assertEquals(328, gap.getRowOrItemCount());
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

    private static NoonDataCompletenessRecord completenessRow(NoonDataCategory category) {
        NoonDataCompletenessRecord row = new NoonDataCompletenessRecord();
        row.setId(900001L + category.ordinal());
        row.setOwnerUserId(10002L);
        row.setStoreCode("STR245027-NAE");
        row.setSiteCode("AE");
        row.setCategory(category);
        row.setLatestStatus(NoonDataLatestStatus.INCOMPLETE);
        row.setHistoryStatus(NoonDataHistoryStatus.INCOMPLETE);
        row.setPatrolEnabled(true);
        row.setActiveGapCount(1);
        row.setCreatedAt(LocalDateTime.parse("2026-05-24T08:30:00"));
        row.setUpdatedAt(LocalDateTime.parse("2026-05-24T08:30:00"));
        return row;
    }

    private static NoonDataGapWindowRecord gapWindow(
            NoonDataCategory category,
            NoonDataGapWindowType windowType,
            LocalDate dateFrom,
            LocalDate dateTo,
            Long planId,
            Long taskId
    ) {
        NoonDataGapWindowRecord gap = new NoonDataGapWindowRecord();
        gap.setId(910001L + category.ordinal());
        gap.setCompletenessId(900001L + category.ordinal());
        gap.setOwnerUserId(10002L);
        gap.setStoreCode("STR245027-NAE");
        gap.setSiteCode("AE");
        gap.setCategory(category);
        gap.setWindowType(windowType);
        gap.setDateFrom(dateFrom);
        gap.setDateTo(dateTo);
        gap.setStatus(NoonDataGapStatus.PENDING);
        gap.setRetryable(Boolean.TRUE);
        gap.setLinkedPullPlanId(planId);
        gap.setLinkedPullTaskId(taskId);
        gap.setCreatedAt(LocalDateTime.ofInstant(Instant.parse("2026-05-24T00:30:00Z"), ZoneOffset.UTC));
        gap.setUpdatedAt(LocalDateTime.ofInstant(Instant.parse("2026-05-24T00:30:00Z"), ZoneOffset.UTC));
        return gap;
    }

    private static final class InMemoryCompletenessRepository implements NoonDataCompletenessRepository {
        private final List<NoonDataCompletenessRecord> completeness = new ArrayList<>();
        private final List<NoonDataGapWindowRecord> gaps = new ArrayList<>();

        @Override
        public Long nextId(String sequenceName, Long initialValue) {
            return initialValue;
        }

        @Override
        public void insertCompleteness(NoonDataCompletenessRecord record) {
            for (int index = 0; index < completeness.size(); index++) {
                NoonDataCompletenessRecord existing = completeness.get(index);
                if (sameCompleteness(existing, record)) {
                    completeness.set(index, record.copy());
                    return;
                }
            }
            completeness.add(record.copy());
        }

        @Override
        public List<NoonDataCompletenessRecord> listCompleteness(NoonDataCompletenessQuery query) {
            return completeness.stream()
                    .filter((row) -> query.getOwnerUserId() == null || query.getOwnerUserId().equals(row.getOwnerUserId()))
                    .filter((row) -> query.getStoreCode() == null || query.getStoreCode().equals(row.getStoreCode()))
                    .filter((row) -> query.getSiteCode() == null || query.getSiteCode().equals(row.getSiteCode()))
                    .filter((row) -> query.getCategory() == null || query.getCategory() == row.getCategory())
                    .map(NoonDataCompletenessRecord::copy)
                    .collect(Collectors.toList());
        }

        @Override
        public void insertGapWindow(NoonDataGapWindowRecord record) {
            for (int index = 0; index < gaps.size(); index++) {
                NoonDataGapWindowRecord existing = gaps.get(index);
                if (sameGap(existing, record)) {
                    gaps.set(index, record.copy());
                    return;
                }
            }
            gaps.add(record.copy());
        }

        @Override
        public List<NoonDataGapWindowRecord> listGapWindows(NoonDataGapQuery query) {
            return gaps.stream()
                    .filter((gap) -> query.getOwnerUserId() == null || query.getOwnerUserId().equals(gap.getOwnerUserId()))
                    .filter((gap) -> query.getStoreCode() == null || query.getStoreCode().equals(gap.getStoreCode()))
                    .filter((gap) -> query.getSiteCode() == null || query.getSiteCode().equals(gap.getSiteCode()))
                    .filter((gap) -> query.getCategory() == null || query.getCategory() == gap.getCategory())
                    .filter((gap) -> query.getStatus() == null || query.getStatus() == gap.getStatus())
                    .map(NoonDataGapWindowRecord::copy)
                    .collect(Collectors.toList());
        }

        private boolean sameCompleteness(NoonDataCompletenessRecord left, NoonDataCompletenessRecord right) {
            return Objects.equals(left.getOwnerUserId(), right.getOwnerUserId())
                    && Objects.equals(left.getStoreCode(), right.getStoreCode())
                    && Objects.equals(left.getSiteCode(), right.getSiteCode())
                    && left.getCategory() == right.getCategory();
        }

        private boolean sameGap(NoonDataGapWindowRecord left, NoonDataGapWindowRecord right) {
            return Objects.equals(left.getCompletenessId(), right.getCompletenessId())
                    && left.getCategory() == right.getCategory()
                    && left.getWindowType() == right.getWindowType()
                    && Objects.equals(left.getDateFrom(), right.getDateFrom())
                    && Objects.equals(left.getDateTo(), right.getDateTo());
        }
    }
}
