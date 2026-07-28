package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.infrastructure.mapper.CompetitorMonitoringMapper;
import com.nuono.next.noonpull.NoonRiskBackoffGuard;
import com.nuono.next.noonpull.NoonRiskBackoffScope;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskPayload;
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CompetitorAnalysisRefreshServiceTest {

    @Mock
    private CompetitorAnalysisMapper mapper;

    @Mock
    private CompetitorMonitoringMapper monitoringMapper;

    @Mock
    private CompetitorKeywordRefreshTransactionRunner keywordRefreshRunner;

    @Mock
    private CompetitorProductDetailRefreshService productDetailRefreshService;

    private InMemoryOperationalTaskRepository taskRepository;
    private List<Runnable> submittedTasks;
    private Map<Long, CompetitorSearchRunRow> persistedRuns;
    private CompetitorAnalysisRefreshService service;

    @BeforeEach
    void setUp() {
        taskRepository = new InMemoryOperationalTaskRepository();
        submittedTasks = new ArrayList<>();
        persistedRuns = new LinkedHashMap<>();
        org.mockito.Mockito.lenient().doAnswer(invocation -> {
            CompetitorSearchRunInsertCommand command = invocation.getArgument(0);
            persistedRuns.put(command.getTaskId(), searchRun(command));
            return null;
        }).when(mapper).insertSearchRun(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.lenient().when(mapper.selectSearchRunByTaskId(
                org.mockito.ArgumentMatchers.anyLong()
        )).thenAnswer(invocation -> persistedRuns.get(invocation.getArgument(0)));
        org.mockito.Mockito.lenient().when(mapper.markSearchRunRunning(
                org.mockito.ArgumentMatchers.anyLong()
        )).thenReturn(1);
        service = new CompetitorAnalysisRefreshService(
                mapper,
                monitoringMapper,
                new OperationalTaskService(
                        taskRepository,
                        Clock.fixed(Instant.parse("2026-06-06T08:00:00Z"), ZoneOffset.UTC)
                ),
                (accountKey, task) -> submittedTasks.add(task),
                Clock.fixed(Instant.parse("2026-06-06T08:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void refreshRejectsWatchProductWithoutActiveKeywords() {
        when(mapper.selectWatchProductById(501L, 180123L)).thenReturn(watchProduct());
        when(mapper.listActiveKeywordsByWatchProductId(180123L)).thenReturn(List.of());

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.requestRefresh(operatorContext(), 180123L)
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals("COMPETITOR_NO_ACTIVE_KEYWORD", error.getReason());
        assertTrue(taskRepository.tasks.isEmpty());
        verify(mapper, never()).insertSearchRun(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refreshReusesCurrentRunningTaskAndRun() {
        when(mapper.selectWatchProductById(501L, 180123L)).thenReturn(watchProduct());
        when(mapper.listActiveKeywordsByWatchProductId(180123L)).thenReturn(List.of(keyword(190001L, "laundry basket")));
        when(mapper.nextSearchRunId()).thenReturn(220123L);
        when(mapper.selectSearchRunByTaskId(150000L)).thenReturn(
                null,
                searchRun(220123L, 150000L, "RUNNING")
        );

        CompetitorRefreshRunView first = service.requestRefresh(operatorContext(), 180123L);
        CompetitorRefreshRunView second = service.requestRefresh(operatorContext(), 180123L);

        assertEquals(first.getTaskId(), second.getTaskId());
        assertEquals(first.getRunId(), second.getRunId());
        assertEquals(1, taskRepository.tasks.size());
        assertEquals(1, submittedTasks.size());
        verify(mapper).insertSearchRun(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refreshMarksTaskFailedWhenEveryKeywordFails() {
        CompetitorKeywordRefreshTransactionRunner failingRunner =
                new CompetitorKeywordRefreshTransactionRunner(
                        mapper,
                        (context) -> CompetitorKeywordRefreshOutcome.failure("PROVIDER_UNAVAILABLE", "Noon down")
                );
        service = new CompetitorAnalysisRefreshService(
                mapper,
                monitoringMapper,
                new OperationalTaskService(
                        taskRepository,
                        Clock.fixed(Instant.parse("2026-06-06T08:00:00Z"), ZoneOffset.UTC)
                ),
                (accountKey, task) -> submittedTasks.add(task),
                failingRunner,
                Clock.fixed(Instant.parse("2026-06-06T08:00:00Z"), ZoneOffset.UTC)
        );
        when(mapper.selectWatchProductById(501L, 180123L)).thenReturn(watchProduct());
        when(mapper.listActiveKeywordsByWatchProductId(180123L)).thenReturn(List.of(keyword(190001L, "laundry basket")));
        when(mapper.nextSearchRunId()).thenReturn(220123L);
        when(mapper.selectWatchProductForRefresh(180123L)).thenReturn(watchProduct());
        when(mapper.nextKeywordRunId()).thenReturn(230123L);

        CompetitorRefreshRunView view = service.requestRefresh(operatorContext(), 180123L);
        submittedTasks.get(0).run();

        OperationalTask task = taskRepository.selectById(view.getTaskId());
        assertEquals(OperationalTaskStatus.FAILED, task.getStatus());
        assertEquals("PROVIDER_UNAVAILABLE", task.getErrorCode());
        assertEquals("竞品刷新失败。", task.getMessage());
        verify(mapper).completeRunningRefreshRun(
                org.mockito.ArgumentMatchers.eq(view.getTaskId()),
                org.mockito.ArgumentMatchers.eq(220123L),
                org.mockito.ArgumentMatchers.eq(180123L),
                org.mockito.ArgumentMatchers.eq("FAILED"),
                org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq("PROVIDER_UNAVAILABLE"),
                org.mockito.ArgumentMatchers.eq("Noon down"),
                org.mockito.ArgumentMatchers.eq(601L)
        );
        verify(mapper).updateLatestRefreshRunIfNotOlder(
                180123L, 220123L, "FAILED", 601L
        );
    }

    @Test
    void refreshRunsConfirmedCompetitorDetailRefreshOncePerWatchProductRun() {
        CompetitorWatchProductRow watchProduct = watchProduct();
        service = new CompetitorAnalysisRefreshService(
                mapper,
                monitoringMapper,
                new OperationalTaskService(
                        taskRepository,
                        Clock.fixed(Instant.parse("2026-06-06T08:00:00Z"), ZoneOffset.UTC)
                ),
                (accountKey, task) -> submittedTasks.add(task),
                keywordRefreshRunner,
                productDetailRefreshService,
                Clock.fixed(Instant.parse("2026-06-06T08:00:00Z"), ZoneOffset.UTC)
        );
        when(mapper.selectWatchProductById(501L, 180123L)).thenReturn(watchProduct);
        when(mapper.listActiveKeywordsByWatchProductId(180123L)).thenReturn(List.of(
                keyword(190001L, "laundry basket"),
                keyword(190002L, "storage basket")
        ));
        when(mapper.nextSearchRunId()).thenReturn(220123L);
        when(mapper.selectWatchProductForRefresh(180123L)).thenReturn(watchProduct);
        when(keywordRefreshRunner.runKeyword(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.eq(220123L),
                org.mockito.ArgumentMatchers.eq(watchProduct),
                org.mockito.ArgumentMatchers.any(CompetitorKeywordRow.class),
                org.mockito.ArgumentMatchers.eq(601L)
        )).thenReturn(CompetitorKeywordRefreshResult.success(0, 1));

        CompetitorRefreshRunView view = service.requestRefresh(operatorContext(), 180123L);
        submittedTasks.get(0).run();

        verify(productDetailRefreshService, times(1)).refreshConfirmedCompetitors(
                watchProduct,
                220123L,
                view.getTaskId(),
                601L
        );
        verify(keywordRefreshRunner, times(2)).runKeyword(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.eq(220123L),
                org.mockito.ArgumentMatchers.eq(watchProduct),
                org.mockito.ArgumentMatchers.any(CompetitorKeywordRow.class),
                org.mockito.ArgumentMatchers.eq(601L)
        );
    }

    @Test
    void scheduledRankMonitoringRunsKeywordsWithoutDetailSnapshots() {
        CompetitorWatchProductRow watchProduct = watchProduct();
        service = new CompetitorAnalysisRefreshService(
                mapper,
                monitoringMapper,
                new OperationalTaskService(
                        taskRepository,
                        Clock.fixed(Instant.parse("2026-06-06T08:00:00Z"), ZoneOffset.UTC)
                ),
                (accountKey, task) -> submittedTasks.add(task),
                keywordRefreshRunner,
                productDetailRefreshService,
                Clock.fixed(Instant.parse("2026-06-06T08:00:00Z"), ZoneOffset.UTC)
        );
        stubMonitoringProducts(List.of(watchProduct));
        when(mapper.listActiveKeywordsByWatchProductId(180123L)).thenReturn(List.of(keyword(190001L, "laundry basket")));
        when(mapper.nextSearchRunId()).thenReturn(220123L);
        when(mapper.selectWatchProductForRefresh(180123L)).thenReturn(watchProduct);
        when(keywordRefreshRunner.runKeyword(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.eq(220123L),
                org.mockito.ArgumentMatchers.eq(watchProduct),
                org.mockito.ArgumentMatchers.any(CompetitorKeywordRow.class),
                org.mockito.ArgumentMatchers.isNull()
        )).thenReturn(CompetitorKeywordRefreshResult.success(0, 1));

        CompetitorTaskView view = service.requestScheduledRankMonitoring(501L, "STR108065-NSA", "SA");
        submittedTasks.get(0).run();
        submittedTasks.get(1).run();

        OperationalTask productTask = taskRepository.selectById(view.getTaskId() + 1);
        assertEquals(OperationalTaskStatus.SUCCEEDED, productTask.getStatus());
        assertEquals("竞品排名刷新完成。", productTask.getMessage());
        assertTrue(productTask.getNaturalKey().endsWith(":rank"));
        verify(productDetailRefreshService, never()).refreshConfirmedCompetitors(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
        verify(keywordRefreshRunner).runKeyword(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.eq(220123L),
                org.mockito.ArgumentMatchers.eq(watchProduct),
                org.mockito.ArgumentMatchers.any(CompetitorKeywordRow.class),
                org.mockito.ArgumentMatchers.isNull()
        );
    }

    @Test
    void scheduledRankMonitoringRetriesTransientFailedKeywordOnly() {
        CompetitorWatchProductRow watchProduct = watchProduct();
        CompetitorKeywordRow stableKeyword = keyword(190001L, "laundry basket");
        CompetitorKeywordRow transientKeyword = keyword(190002L, "storage basket");
        service = new CompetitorAnalysisRefreshService(
                mapper,
                monitoringMapper,
                new OperationalTaskService(
                        taskRepository,
                        Clock.fixed(Instant.parse("2026-06-06T08:00:00Z"), ZoneOffset.UTC)
                ),
                (accountKey, task) -> submittedTasks.add(task),
                keywordRefreshRunner,
                productDetailRefreshService,
                Clock.fixed(Instant.parse("2026-06-06T08:00:00Z"), ZoneOffset.UTC)
        );
        stubMonitoringProducts(List.of(watchProduct));
        when(mapper.listActiveKeywordsByWatchProductId(180123L))
                .thenReturn(List.of(stableKeyword, transientKeyword));
        when(mapper.nextSearchRunId()).thenReturn(220123L);
        when(mapper.selectWatchProductForRefresh(180123L)).thenReturn(watchProduct);
        when(keywordRefreshRunner.runKeyword(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.eq(220123L),
                org.mockito.ArgumentMatchers.eq(watchProduct),
                org.mockito.ArgumentMatchers.eq(stableKeyword),
                org.mockito.ArgumentMatchers.isNull()
        ))
                .thenReturn(CompetitorKeywordRefreshResult.success(1, 2));
        when(keywordRefreshRunner.runKeyword(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.eq(220123L),
                org.mockito.ArgumentMatchers.eq(watchProduct),
                org.mockito.ArgumentMatchers.eq(transientKeyword),
                org.mockito.ArgumentMatchers.isNull()
        ))
                .thenReturn(
                        CompetitorKeywordRefreshResult.failure("PROVIDER_UNAVAILABLE", "Noon 前台搜索返回 HTTP 502。"),
                        CompetitorKeywordRefreshResult.success(3, 4)
                );

        CompetitorTaskView view = service.requestScheduledRankMonitoring(501L, "STR108065-NSA", "SA");
        submittedTasks.get(0).run();
        submittedTasks.get(1).run();

        OperationalTask productTask = taskRepository.selectById(view.getTaskId() + 1);
        assertEquals(OperationalTaskStatus.SUCCEEDED, productTask.getStatus());
        assertEquals("竞品排名刷新完成。", productTask.getMessage());
        verify(keywordRefreshRunner, times(1)).runKeyword(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.eq(220123L),
                org.mockito.ArgumentMatchers.eq(watchProduct),
                org.mockito.ArgumentMatchers.eq(stableKeyword),
                org.mockito.ArgumentMatchers.isNull()
        );
        verify(keywordRefreshRunner, times(2)).runKeyword(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.eq(220123L),
                org.mockito.ArgumentMatchers.eq(watchProduct),
                org.mockito.ArgumentMatchers.eq(transientKeyword),
                org.mockito.ArgumentMatchers.isNull()
        );
        verify(mapper).completeRunningRefreshRun(
                org.mockito.ArgumentMatchers.eq(productTask.getId()),
                org.mockito.ArgumentMatchers.eq(220123L),
                org.mockito.ArgumentMatchers.eq(180123L),
                org.mockito.ArgumentMatchers.eq("SUCCEEDED"),
                org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(4),
                org.mockito.ArgumentMatchers.eq(6),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull()
        );
        verify(mapper).updateLatestRefreshRunIfNotOlder(
                180123L, 220123L, "SUCCEEDED", null
        );
    }

    @Test
    void scheduledMonitoringStopsBeforeSubmittingWhenNoonScopeIsInRiskBackoff() {
        NoonRiskBackoffGuard riskBackoffGuard = riskBackoffGuardWithGlobalHold();
        service = new CompetitorAnalysisRefreshService(
                mapper,
                monitoringMapper,
                new OperationalTaskService(
                        taskRepository,
                        Clock.fixed(Instant.parse("2026-06-06T08:00:00Z"), ZoneOffset.UTC)
                ),
                (accountKey, task) -> submittedTasks.add(task),
                keywordRefreshRunner,
                productDetailRefreshService,
                Clock.fixed(Instant.parse("2026-06-06T08:00:00Z"), ZoneOffset.UTC),
                riskBackoffGuard
        );

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.requestScheduledRankMonitoring(501L, "STR108065-NSA", "SA")
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatus());
        assertEquals("NOON_RISK_BACKOFF", error.getReason());
        verify(monitoringMapper, never()).selectRefreshableWatchProductBoundary(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
        assertTrue(submittedTasks.isEmpty());
    }

    @Test
    void scheduledRankMonitoringRecordsRiskBackoffAndStopsAfterRateLimitFailure() {
        CompetitorWatchProductRow watchProduct = watchProduct();
        CompetitorKeywordRow rateLimitedKeyword = keyword(190001L, "laundry basket");
        CompetitorKeywordRow skippedKeyword = keyword(190002L, "storage basket");
        CompetitorTestRiskBackoffRepository riskRepository =
                new CompetitorTestRiskBackoffRepository();
        service = new CompetitorAnalysisRefreshService(
                mapper,
                monitoringMapper,
                new OperationalTaskService(
                        taskRepository,
                        Clock.fixed(Instant.parse("2026-06-06T08:00:00Z"), ZoneOffset.UTC)
                ),
                (accountKey, task) -> submittedTasks.add(task),
                keywordRefreshRunner,
                productDetailRefreshService,
                Clock.fixed(Instant.parse("2026-06-06T08:00:00Z"), ZoneOffset.UTC),
                new NoonRiskBackoffGuard(riskRepository)
        );
        stubMonitoringProducts(List.of(watchProduct));
        when(mapper.listActiveKeywordsByWatchProductId(180123L))
                .thenReturn(List.of(rateLimitedKeyword, skippedKeyword));
        when(mapper.nextSearchRunId()).thenReturn(220123L);
        when(mapper.selectWatchProductForRefresh(180123L)).thenReturn(watchProduct);
        when(keywordRefreshRunner.runKeyword(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.eq(220123L),
                org.mockito.ArgumentMatchers.eq(watchProduct),
                org.mockito.ArgumentMatchers.eq(rateLimitedKeyword),
                org.mockito.ArgumentMatchers.isNull()
        ))
                .thenReturn(CompetitorKeywordRefreshResult.failure("RATE_LIMITED", "Noon 前台搜索返回 HTTP 429。"));

        CompetitorTaskView view = service.requestScheduledRankMonitoring(501L, "STR108065-NSA", "SA");
        submittedTasks.get(0).run();
        submittedTasks.get(1).run();

        OperationalTask productTask = taskRepository.selectById(view.getTaskId() + 1);
        assertEquals(OperationalTaskStatus.FAILED, productTask.getStatus());
        assertEquals("COMPETITOR_RISK_BACKOFF", productTask.getErrorCode());
        assertEquals("rate_limited", riskRepository.selectLatestHold(
                NoonRiskBackoffScope.publicSearch(501L, "STR108065-NSA", "SA").getScopeKey()
        ).getRiskType());
        assertEquals("rate_limited", riskRepository.selectLatestHold(
                NoonRiskBackoffScope.allPublicNoon(501L, "STR108065-NSA", "SA").getScopeKey()
        ).getRiskType());
        verify(keywordRefreshRunner, times(1)).runKeyword(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.eq(220123L),
                org.mockito.ArgumentMatchers.eq(watchProduct),
                org.mockito.ArgumentMatchers.eq(rateLimitedKeyword),
                org.mockito.ArgumentMatchers.isNull()
        );
        verify(keywordRefreshRunner, never()).runKeyword(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.eq(220123L),
                org.mockito.ArgumentMatchers.eq(watchProduct),
                org.mockito.ArgumentMatchers.eq(skippedKeyword),
                org.mockito.ArgumentMatchers.isNull()
        );
    }

    @Test
    void queuedProductRefreshStopsBeforeCallingNoonWhenEarlierProductRecordsRiskBackoff() {
        CompetitorWatchProductRow first = watchProduct(180123L, "ZSELF001");
        CompetitorWatchProductRow second = watchProduct(180124L, "ZSELF002");
        CompetitorKeywordRow rateLimitedKeyword = keyword(190001L, "laundry basket");
        CompetitorKeywordRow blockedByBackoffKeyword = keyword(190002L, "storage basket");
        blockedByBackoffKeyword.setWatchProductId(180124L);
        CompetitorTestRiskBackoffRepository riskRepository =
                new CompetitorTestRiskBackoffRepository();
        service = new CompetitorAnalysisRefreshService(
                mapper,
                monitoringMapper,
                new OperationalTaskService(
                        taskRepository,
                        Clock.fixed(Instant.parse("2026-06-06T08:00:00Z"), ZoneOffset.UTC)
                ),
                (accountKey, task) -> submittedTasks.add(task),
                keywordRefreshRunner,
                productDetailRefreshService,
                Clock.fixed(Instant.parse("2026-06-06T08:00:00Z"), ZoneOffset.UTC),
                new NoonRiskBackoffGuard(
                        riskRepository,
                        Clock.fixed(Instant.parse("2026-06-06T08:00:00Z"), ZoneOffset.UTC)
                )
        );
        stubMonitoringProducts(List.of(first, second));
        when(mapper.listActiveKeywordsByWatchProductId(180123L)).thenReturn(List.of(rateLimitedKeyword));
        when(mapper.listActiveKeywordsByWatchProductId(180124L)).thenReturn(List.of(blockedByBackoffKeyword));
        when(mapper.nextSearchRunId()).thenReturn(220123L, 220124L);
        when(mapper.selectWatchProductForRefresh(180123L)).thenReturn(first);
        when(mapper.selectWatchProductForRefresh(180124L)).thenReturn(second);
        when(keywordRefreshRunner.runKeyword(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.eq(220123L),
                org.mockito.ArgumentMatchers.eq(first),
                org.mockito.ArgumentMatchers.eq(rateLimitedKeyword),
                org.mockito.ArgumentMatchers.isNull()
        ))
                .thenReturn(CompetitorKeywordRefreshResult.failure("RATE_LIMITED", "Noon 前台搜索返回 HTTP 429。"));

        CompetitorTaskView view = service.requestScheduledRankMonitoring(501L, "STR108065-NSA", "SA");
        submittedTasks.get(0).run();
        submittedTasks.get(1).run();
        submittedTasks.get(2).run();

        OperationalTask firstProductTask = taskRepository.selectById(view.getTaskId() + 1);
        OperationalTask secondProductTask = taskRepository.selectById(view.getTaskId() + 2);
        assertEquals(OperationalTaskStatus.FAILED, firstProductTask.getStatus());
        assertEquals("COMPETITOR_RISK_BACKOFF", firstProductTask.getErrorCode());
        assertEquals(OperationalTaskStatus.QUEUED, secondProductTask.getStatus());
        assertNull(secondProductTask.getErrorCode());
        verify(keywordRefreshRunner, times(1)).runKeyword(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.eq(220123L),
                org.mockito.ArgumentMatchers.eq(first),
                org.mockito.ArgumentMatchers.eq(rateLimitedKeyword),
                org.mockito.ArgumentMatchers.isNull()
        );
        verify(keywordRefreshRunner, never()).runKeyword(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.eq(220124L),
                org.mockito.ArgumentMatchers.eq(second),
                org.mockito.ArgumentMatchers.eq(blockedByBackoffKeyword),
                org.mockito.ArgumentMatchers.isNull()
        );
        verify(mapper, never()).failRunningRefreshRun(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.eq(220124L),
                org.mockito.ArgumentMatchers.eq(180124L),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.isNull()
        );
    }

    @Test
    void parksTransientRankKeywordRetryUntilDurableTargetedRetryExists() {
        assertEquals(0, service.retryRecentTransientRankKeywordFailures(Duration.ofHours(24), 50));
        verify(mapper, never()).listRetryableTransientRankKeywordFailures(
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.anyInt()
        );
    }

    @Test
    void storeMonitoringSubmitsRefreshForEveryRefreshableWatchProduct() {
        CompetitorWatchProductRow first = watchProduct(180123L, "ZSELF001");
        CompetitorWatchProductRow second = watchProduct(180124L, "ZSELF002");
        stubMonitoringProducts(List.of(first, second));
        when(mapper.listActiveKeywordsByWatchProductId(180123L)).thenReturn(List.of(keyword(190001L, "laundry basket")));
        when(mapper.listActiveKeywordsByWatchProductId(180124L)).thenReturn(List.of(keyword(190002L, "storage basket")));
        when(mapper.nextSearchRunId()).thenReturn(220123L, 220124L);

        CompetitorTaskView view = service.requestStoreMonitoring(operatorContext(), "STR108065-NSA", "SA");

        assertEquals(CompetitorAnalysisRefreshService.MONITOR_TASK_TYPE, view.getTaskType());
        assertEquals(1, submittedTasks.size());

        submittedTasks.get(0).run();

        OperationalTask task = taskRepository.selectById(view.getTaskId());
        assertEquals(OperationalTaskStatus.SUCCEEDED, task.getStatus());
        assertEquals("竞品监控批次已提交。", task.getMessage());
        assertTrue(task.getResultJson().contains("\"submittedCount\":2"));
        assertEquals(OperationalTaskStatus.QUEUED, taskRepository.selectById(view.getTaskId() + 1).getStatus());
        verify(mapper, times(2)).insertSearchRun(org.mockito.ArgumentMatchers.argThat(command -> "QUEUED".equals(command.getStatus())));
    }

    @Test
    void storeMonitoringRejectsEmptyScope() {
        stubMonitoringProducts(List.of());

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.requestStoreMonitoring(operatorContext(), "STR108065-NSA", "SA")
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals("COMPETITOR_MONITOR_NO_REFRESHABLE_PRODUCT", error.getReason());
        assertTrue(taskRepository.tasks.isEmpty());
    }

    private void stubMonitoringProducts(List<CompetitorWatchProductRow> products) {
        CompetitorMonitoringBoundaryRow boundary = new CompetitorMonitoringBoundaryRow();
        boundary.setEligibleTotal((long) products.size());
        boundary.setUpperWatchProductId(
                products.stream().map(CompetitorWatchProductRow::getId).max(Long::compareTo).orElse(null)
        );
        when(monitoringMapper.selectRefreshableWatchProductBoundary(501L, "STR108065-NSA", "SA"))
                .thenReturn(boundary);
        org.mockito.Mockito.lenient().when(monitoringMapper.listRefreshableWatchProducts(
                org.mockito.ArgumentMatchers.eq(501L),
                org.mockito.ArgumentMatchers.eq("STR108065-NSA"),
                org.mockito.ArgumentMatchers.eq("SA"),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt()
        )).thenAnswer(invocation -> {
            long afterId = invocation.getArgument(3);
            long upperId = invocation.getArgument(4);
            int limit = invocation.getArgument(5);
            return products.stream()
                    .filter(product -> product.getId() > afterId && product.getId() <= upperId)
                    .sorted(Comparator.comparing(CompetitorWatchProductRow::getId))
                    .limit(limit)
                    .collect(Collectors.toList());
        });
        for (CompetitorWatchProductRow product : products) {
            org.mockito.Mockito.lenient().when(mapper.selectWatchProductForRefresh(product.getId()))
                    .thenReturn(product);
        }
    }

    private static CompetitorWatchProductRow watchProduct() {
        return watchProduct(180123L, "ZSELF001");
    }

    private static CompetitorWatchProductRow watchProduct(Long id, String noonCode) {
        CompetitorWatchProductRow row = new CompetitorWatchProductRow();
        row.setId(id);
        row.setOwnerUserId(501L);
        row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA");
        row.setPartnerSku("BASKET-SA-001-BLUE");
        row.setSelfNoonProductCode(noonCode);
        row.setStatus("ACTIVE");
        return row;
    }

    private static CompetitorKeywordRow keyword(Long id, String keyword) {
        CompetitorKeywordRow row = new CompetitorKeywordRow();
        row.setId(id);
        row.setWatchProductId(180123L);
        row.setKeyword(keyword);
        row.setKeywordNorm(keyword);
        row.setStatus("ACTIVE");
        return row;
    }

    private static CompetitorSearchRunRow searchRun(Long runId, Long taskId, String status) {
        CompetitorSearchRunRow row = new CompetitorSearchRunRow();
        row.setId(runId);
        row.setWatchProductId(180123L);
        row.setTaskId(taskId);
        row.setTriggerMode("MANUAL_REFRESH");
        row.setStatus(status);
        row.setKeywordTotal(1);
        return row;
    }

    private static CompetitorSearchRunRow searchRun(CompetitorSearchRunInsertCommand command) {
        CompetitorSearchRunRow row = new CompetitorSearchRunRow();
        row.setId(command.getId());
        row.setWatchProductId(command.getWatchProductId());
        row.setTaskId(command.getTaskId());
        row.setTriggerMode(command.getTriggerMode());
        row.setStatus(command.getStatus());
        row.setRequestedBy(command.getRequestedBy());
        row.setKeywordTotal(command.getKeywordTotal());
        return row;
    }

    private static OperationalTask runningTask(Long taskId) {
        OperationalTask task = new OperationalTask();
        task.setId(taskId);
        task.setTaskType(CompetitorAnalysisRefreshService.TASK_TYPE);
        task.setNaturalKey("watchProduct:180123");
        task.setOwnerUserId(501L);
        task.setStoreCode("STR108065-NSA");
        task.setSiteCode("SA");
        task.setStatus(OperationalTaskStatus.RUNNING);
        task.setProgressPercent(0);
        task.setMessage("竞品刷新正在后台执行。");
        task.setStartedAt(LocalDateTime.parse("2026-06-06T07:20:00"));
        task.setCreatedAt(LocalDateTime.parse("2026-06-06T07:20:00"));
        task.setUpdatedAt(LocalDateTime.parse("2026-06-06T07:20:00"));
        return task;
    }

    private static BusinessAccessContext operatorContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(601L)
                .businessOwnerUserId(501L)
                .accountType(BusinessAccountType.OPERATOR)
                .roleLevel(3)
                .roleName("运营")
                .storeCodes(Set.of("STR108065-NSA"))
                .storeOwnerUserIds(Map.of("STR108065-NSA", 501L))
                .menuPaths(Set.of("/operations/competitor-analysis"))
                .build();
    }

    private static NoonRiskBackoffGuard riskBackoffGuardWithGlobalHold() {
        CompetitorTestRiskBackoffRepository repository =
                new CompetitorTestRiskBackoffRepository();
        NoonRiskBackoffGuard guard = new NoonRiskBackoffGuard(repository);
        guard.recordRiskSignal(
                NoonRiskBackoffScope.allPublicNoon(501L, "STR108065-NSA", "SA"),
                "blocked_by_risk_control",
                "PUBLIC_SEARCH",
                130001L,
                LocalDateTime.now().plusMinutes(5),
                "blocked"
        );
        return guard;
    }
}
