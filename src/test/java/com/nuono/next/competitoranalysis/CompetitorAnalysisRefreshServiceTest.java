package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.noonpull.NoonRiskBackoffGuard;
import com.nuono.next.noonpull.NoonRiskBackoffHold;
import com.nuono.next.noonpull.NoonRiskBackoffRepository;
import com.nuono.next.noonpull.NoonRiskBackoffScope;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskPayload;
import com.nuono.next.system.task.OperationalTaskRepository;
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
    private CompetitorKeywordRefreshTransactionRunner keywordRefreshRunner;

    @Mock
    private CompetitorProductDetailRefreshService productDetailRefreshService;

    private InMemoryOperationalTaskRepository taskRepository;
    private List<Runnable> submittedTasks;
    private CompetitorAnalysisRefreshService service;

    @BeforeEach
    void setUp() {
        taskRepository = new InMemoryOperationalTaskRepository();
        submittedTasks = new ArrayList<>();
        service = new CompetitorAnalysisRefreshService(
                mapper,
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
        when(mapper.selectSearchRunByTaskId(150000L)).thenReturn(searchRun(220123L, 150000L, "RUNNING"));

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
        verify(mapper).completeSearchRun(
                org.mockito.ArgumentMatchers.eq(220123L),
                org.mockito.ArgumentMatchers.eq("FAILED"),
                org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq("PROVIDER_UNAVAILABLE"),
                org.mockito.ArgumentMatchers.eq("Noon down"),
                org.mockito.ArgumentMatchers.eq(601L)
        );
        verify(mapper).updateWatchProductLatestRun(180123L, 220123L, "FAILED", 601L);
    }

    @Test
    void refreshRunsConfirmedCompetitorDetailRefreshOncePerWatchProductRun() {
        CompetitorWatchProductRow watchProduct = watchProduct();
        service = new CompetitorAnalysisRefreshService(
                mapper,
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
                new OperationalTaskService(
                        taskRepository,
                        Clock.fixed(Instant.parse("2026-06-06T08:00:00Z"), ZoneOffset.UTC)
                ),
                (accountKey, task) -> submittedTasks.add(task),
                keywordRefreshRunner,
                productDetailRefreshService,
                Clock.fixed(Instant.parse("2026-06-06T08:00:00Z"), ZoneOffset.UTC)
        );
        when(mapper.listRefreshableWatchProducts(501L, "STR108065-NSA", "SA", 500))
                .thenReturn(List.of(watchProduct), List.of(watchProduct));
        when(mapper.listActiveKeywordsByWatchProductId(180123L)).thenReturn(List.of(keyword(190001L, "laundry basket")));
        when(mapper.nextSearchRunId()).thenReturn(220123L);
        when(mapper.selectWatchProductForRefresh(180123L)).thenReturn(watchProduct);
        when(keywordRefreshRunner.runKeyword(
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
                new OperationalTaskService(
                        taskRepository,
                        Clock.fixed(Instant.parse("2026-06-06T08:00:00Z"), ZoneOffset.UTC)
                ),
                (accountKey, task) -> submittedTasks.add(task),
                keywordRefreshRunner,
                productDetailRefreshService,
                Clock.fixed(Instant.parse("2026-06-06T08:00:00Z"), ZoneOffset.UTC)
        );
        when(mapper.listRefreshableWatchProducts(501L, "STR108065-NSA", "SA", 500))
                .thenReturn(List.of(watchProduct), List.of(watchProduct));
        when(mapper.listActiveKeywordsByWatchProductId(180123L))
                .thenReturn(List.of(stableKeyword, transientKeyword));
        when(mapper.nextSearchRunId()).thenReturn(220123L);
        when(mapper.selectWatchProductForRefresh(180123L)).thenReturn(watchProduct);
        when(keywordRefreshRunner.runKeyword(220123L, watchProduct, stableKeyword, null))
                .thenReturn(CompetitorKeywordRefreshResult.success(1, 2));
        when(keywordRefreshRunner.runKeyword(220123L, watchProduct, transientKeyword, null))
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
        verify(keywordRefreshRunner, times(1)).runKeyword(220123L, watchProduct, stableKeyword, null);
        verify(keywordRefreshRunner, times(2)).runKeyword(220123L, watchProduct, transientKeyword, null);
        verify(mapper).completeSearchRun(
                org.mockito.ArgumentMatchers.eq(220123L),
                org.mockito.ArgumentMatchers.eq("SUCCEEDED"),
                org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(4),
                org.mockito.ArgumentMatchers.eq(6),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull()
        );
        verify(mapper).updateWatchProductLatestRun(180123L, 220123L, "SUCCEEDED", null);
    }

    @Test
    void scheduledMonitoringStopsBeforeSubmittingWhenNoonScopeIsInRiskBackoff() {
        NoonRiskBackoffGuard riskBackoffGuard = riskBackoffGuardWithGlobalHold();
        service = new CompetitorAnalysisRefreshService(
                mapper,
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
        verify(mapper, never()).listRefreshableWatchProducts(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt()
        );
        assertTrue(submittedTasks.isEmpty());
    }

    @Test
    void scheduledRankMonitoringRecordsRiskBackoffAndStopsAfterRateLimitFailure() {
        CompetitorWatchProductRow watchProduct = watchProduct();
        CompetitorKeywordRow rateLimitedKeyword = keyword(190001L, "laundry basket");
        CompetitorKeywordRow skippedKeyword = keyword(190002L, "storage basket");
        CapturingRiskBackoffRepository riskRepository = new CapturingRiskBackoffRepository();
        service = new CompetitorAnalysisRefreshService(
                mapper,
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
        when(mapper.listRefreshableWatchProducts(501L, "STR108065-NSA", "SA", 500))
                .thenReturn(List.of(watchProduct), List.of(watchProduct));
        when(mapper.listActiveKeywordsByWatchProductId(180123L))
                .thenReturn(List.of(rateLimitedKeyword, skippedKeyword));
        when(mapper.nextSearchRunId()).thenReturn(220123L);
        when(mapper.selectWatchProductForRefresh(180123L)).thenReturn(watchProduct);
        when(keywordRefreshRunner.runKeyword(220123L, watchProduct, rateLimitedKeyword, null))
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
        verify(keywordRefreshRunner, times(1)).runKeyword(220123L, watchProduct, rateLimitedKeyword, null);
        verify(keywordRefreshRunner, never()).runKeyword(220123L, watchProduct, skippedKeyword, null);
    }

    @Test
    void queuedProductRefreshStopsBeforeCallingNoonWhenEarlierProductRecordsRiskBackoff() {
        CompetitorWatchProductRow first = watchProduct(180123L, "ZSELF001");
        CompetitorWatchProductRow second = watchProduct(180124L, "ZSELF002");
        CompetitorKeywordRow rateLimitedKeyword = keyword(190001L, "laundry basket");
        CompetitorKeywordRow blockedByBackoffKeyword = keyword(190002L, "storage basket");
        blockedByBackoffKeyword.setWatchProductId(180124L);
        CapturingRiskBackoffRepository riskRepository = new CapturingRiskBackoffRepository();
        service = new CompetitorAnalysisRefreshService(
                mapper,
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
        when(mapper.listRefreshableWatchProducts(501L, "STR108065-NSA", "SA", 500))
                .thenReturn(List.of(first, second), List.of(first, second));
        when(mapper.listActiveKeywordsByWatchProductId(180123L)).thenReturn(List.of(rateLimitedKeyword));
        when(mapper.listActiveKeywordsByWatchProductId(180124L)).thenReturn(List.of(blockedByBackoffKeyword));
        when(mapper.nextSearchRunId()).thenReturn(220123L, 220124L);
        when(mapper.selectWatchProductForRefresh(180123L)).thenReturn(first);
        when(mapper.selectWatchProductForRefresh(180124L)).thenReturn(second);
        when(keywordRefreshRunner.runKeyword(220123L, first, rateLimitedKeyword, null))
                .thenReturn(CompetitorKeywordRefreshResult.failure("RATE_LIMITED", "Noon 前台搜索返回 HTTP 429。"));

        CompetitorTaskView view = service.requestScheduledRankMonitoring(501L, "STR108065-NSA", "SA");
        submittedTasks.get(0).run();
        submittedTasks.get(1).run();
        submittedTasks.get(2).run();

        OperationalTask firstProductTask = taskRepository.selectById(view.getTaskId() + 1);
        OperationalTask secondProductTask = taskRepository.selectById(view.getTaskId() + 2);
        assertEquals(OperationalTaskStatus.FAILED, firstProductTask.getStatus());
        assertEquals("COMPETITOR_RISK_BACKOFF", firstProductTask.getErrorCode());
        assertEquals(OperationalTaskStatus.FAILED, secondProductTask.getStatus());
        assertEquals("COMPETITOR_RISK_BACKOFF", secondProductTask.getErrorCode());
        verify(keywordRefreshRunner, times(1)).runKeyword(220123L, first, rateLimitedKeyword, null);
        verify(keywordRefreshRunner, never()).runKeyword(220124L, second, blockedByBackoffKeyword, null);
        verify(mapper).markSearchRunFailed(
                org.mockito.ArgumentMatchers.eq(220124L),
                org.mockito.ArgumentMatchers.eq("COMPETITOR_RISK_BACKOFF"),
                org.mockito.ArgumentMatchers.contains("rate_limited")
        );
    }

    @Test
    void retriesRecentTransientScheduledRankKeywordFailuresWithoutRefreshingWholeProduct() {
        CompetitorWatchProductRow watchProduct = watchProduct();
        CompetitorKeywordRow failedKeyword = keyword(190002L, "storage basket");
        CompetitorSearchRunRow partialRun = searchRun(220123L, 150123L, "PARTIAL_FAILED");
        partialRun.setKeywordTotal(2);
        partialRun.setKeywordSuccess(1);
        partialRun.setKeywordFailed(1);
        partialRun.setCandidateUpsertedCount(5);
        partialRun.setRankFactWrittenCount(7);
        partialRun.setErrorCode("PROVIDER_UNAVAILABLE");
        partialRun.setErrorMessage("Noon 前台搜索返回 HTTP 502。");
        service = new CompetitorAnalysisRefreshService(
                mapper,
                new OperationalTaskService(
                        taskRepository,
                        Clock.fixed(Instant.parse("2026-06-06T08:00:00Z"), ZoneOffset.UTC)
                ),
                (accountKey, task) -> submittedTasks.add(task),
                keywordRefreshRunner,
                Clock.fixed(Instant.parse("2026-06-06T08:00:00Z"), ZoneOffset.UTC)
        );
        when(mapper.listRetryableTransientRankKeywordFailures(
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.eq(50)
        )).thenReturn(List.of(retryCandidate(220123L, 180123L, 190002L)));
        when(mapper.selectSearchRunById(220123L)).thenReturn(partialRun);
        when(mapper.selectWatchProductForRefresh(180123L)).thenReturn(watchProduct);
        when(mapper.selectKeywordById(190002L)).thenReturn(failedKeyword);
        when(keywordRefreshRunner.runKeyword(220123L, watchProduct, failedKeyword, null))
                .thenReturn(CompetitorKeywordRefreshResult.success(3, 4));

        int recovered = service.retryRecentTransientRankKeywordFailures(Duration.ofHours(24), 50);

        assertEquals(1, recovered);
        verify(keywordRefreshRunner, times(1)).runKeyword(220123L, watchProduct, failedKeyword, null);
        verify(mapper).completeSearchRun(
                org.mockito.ArgumentMatchers.eq(220123L),
                org.mockito.ArgumentMatchers.eq("SUCCEEDED"),
                org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(8),
                org.mockito.ArgumentMatchers.eq(11),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull()
        );
        verify(mapper).updateWatchProductLatestRun(180123L, 220123L, "SUCCEEDED", null);
    }

    @Test
    void scheduledDetailMonitoringRunsDetailSnapshotsWithoutKeywordRank() {
        CompetitorWatchProductRow watchProduct = watchProduct();
        service = new CompetitorAnalysisRefreshService(
                mapper,
                new OperationalTaskService(
                        taskRepository,
                        Clock.fixed(Instant.parse("2026-06-06T08:00:00Z"), ZoneOffset.UTC)
                ),
                (accountKey, task) -> submittedTasks.add(task),
                keywordRefreshRunner,
                productDetailRefreshService,
                Clock.fixed(Instant.parse("2026-06-06T08:00:00Z"), ZoneOffset.UTC)
        );
        when(mapper.listRefreshableWatchProducts(501L, "STR108065-NSA", "SA", 500))
                .thenReturn(List.of(watchProduct), List.of(watchProduct));
        when(mapper.nextSearchRunId()).thenReturn(220123L);
        when(mapper.selectWatchProductForRefresh(180123L)).thenReturn(watchProduct);

        CompetitorTaskView view = service.requestScheduledDetailMonitoring(501L, "STR108065-NSA", "SA");
        submittedTasks.get(0).run();
        submittedTasks.get(1).run();

        OperationalTask productTask = taskRepository.selectById(view.getTaskId() + 1);
        assertEquals(OperationalTaskStatus.SUCCEEDED, productTask.getStatus());
        assertEquals("竞品详情快照刷新完成。", productTask.getMessage());
        assertTrue(productTask.getNaturalKey().endsWith(":detail"));
        verify(productDetailRefreshService).refreshConfirmedCompetitors(
                watchProduct,
                220123L,
                productTask.getId(),
                null
        );
        verify(keywordRefreshRunner, never()).runKeyword(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
        verify(mapper, never()).listActiveKeywordsByWatchProductId(180123L);
    }

    @Test
    void refreshFailsStaleTaskAndCreatesNewTaskRun() {
        OperationalTask stale = runningTask(150000L);
        stale.setUpdatedAt(LocalDateTime.parse("2026-06-06T07:20:00"));
        taskRepository.insert(stale);
        when(mapper.selectWatchProductById(501L, 180123L)).thenReturn(watchProduct());
        when(mapper.selectSearchRunByTaskId(150000L)).thenReturn(searchRun(220000L, 150000L, "RUNNING"));
        when(mapper.listActiveKeywordsByWatchProductId(180123L)).thenReturn(List.of(keyword(190001L, "laundry basket")));
        when(mapper.nextSearchRunId()).thenReturn(220124L);

        CompetitorRefreshRunView view = service.requestRefresh(operatorContext(), 180123L);

        assertEquals(150001L, view.getTaskId());
        assertEquals(220124L, view.getRunId());
        assertEquals(OperationalTaskStatus.FAILED, taskRepository.selectById(150000L).getStatus());
        assertEquals("FAILED_STALE", taskRepository.selectById(150000L).getErrorCode());
        verify(mapper).markSearchRunFailed(220000L, "FAILED_STALE", "刷新任务超过 30 分钟未完成，已自动释放。");
        verify(mapper).insertSearchRun(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void recoverStaleRefreshTasksFailsActiveTaskAndLinkedSearchRun() {
        OperationalTask stale = runningTask(150000L);
        stale.setNaturalKey("watchProduct:180123:detail");
        stale.setUpdatedAt(LocalDateTime.parse("2026-06-06T07:20:00"));
        taskRepository.insert(stale);
        when(mapper.selectSearchRunByTaskId(150000L)).thenReturn(searchRun(220000L, 150000L, "RUNNING"));

        int recovered = service.recoverStaleRefreshTasks();

        assertEquals(1, recovered);
        OperationalTask task = taskRepository.selectById(150000L);
        assertEquals(OperationalTaskStatus.FAILED, task.getStatus());
        assertEquals("FAILED_STALE", task.getErrorCode());
        assertEquals("刷新任务超过 30 分钟未完成，已自动释放。", task.getMessage());
        verify(mapper).markSearchRunFailed(220000L, "FAILED_STALE", "刷新任务超过 30 分钟未完成，已自动释放。");
    }

    @Test
    void storeMonitoringSubmitsRefreshForEveryRefreshableWatchProduct() {
        CompetitorWatchProductRow first = watchProduct(180123L, "ZSELF001");
        CompetitorWatchProductRow second = watchProduct(180124L, "ZSELF002");
        when(mapper.listRefreshableWatchProducts(501L, "STR108065-NSA", "SA", 500))
                .thenReturn(List.of(first, second), List.of(first, second));
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
        when(mapper.listRefreshableWatchProducts(501L, "STR108065-NSA", "SA", 500)).thenReturn(List.of());

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.requestStoreMonitoring(operatorContext(), "STR108065-NSA", "SA")
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals("COMPETITOR_MONITOR_NO_REFRESHABLE_PRODUCT", error.getReason());
        assertTrue(taskRepository.tasks.isEmpty());
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

    private static CompetitorTransientKeywordFailureRow retryCandidate(
            Long searchRunId,
            Long watchProductId,
            Long keywordId
    ) {
        CompetitorTransientKeywordFailureRow row = new CompetitorTransientKeywordFailureRow();
        row.setSearchRunId(searchRunId);
        row.setWatchProductId(watchProductId);
        row.setKeywordId(keywordId);
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
        CapturingRiskBackoffRepository repository = new CapturingRiskBackoffRepository();
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

    private static final class InMemoryOperationalTaskRepository implements OperationalTaskRepository {
        private long nextId = 150000L;
        private final Map<Long, OperationalTask> tasks = new LinkedHashMap<>();

        @Override
        public Long nextId(String sequenceName, Long initialValue) {
            return nextId++;
        }

        @Override
        public void insert(OperationalTask task) {
            tasks.put(task.getId(), task.copy());
            if (task.getId() != null) {
                nextId = Math.max(nextId, task.getId() + 1);
            }
        }

        @Override
        public OperationalTask selectById(Long taskId) {
            OperationalTask task = tasks.get(taskId);
            return task == null ? null : task.copy();
        }

        @Override
        public OperationalTask selectActiveByNaturalKey(String taskType, String naturalKey) {
            return tasks.values().stream()
                    .filter((task) -> taskType.equals(task.getTaskType()))
                    .filter((task) -> naturalKey.equals(task.getNaturalKey()))
                    .filter((task) -> task.getStatus() != null && task.getStatus().isActive())
                    .findFirst()
                    .map(OperationalTask::copy)
                    .orElse(null);
        }

        @Override
        public OperationalTask selectLatestByNaturalKey(String taskType, String naturalKey) {
            return tasks.values().stream()
                    .filter((task) -> taskType.equals(task.getTaskType()))
                    .filter((task) -> naturalKey.equals(task.getNaturalKey()))
                    .max(Comparator.comparing(OperationalTask::getId))
                    .map(OperationalTask::copy)
                    .orElse(null);
        }

        @Override
        public void update(OperationalTask task) {
            tasks.put(task.getId(), task.copy());
        }

        @Override
        public List<OperationalTask> listActiveByTaskType(String taskType, int limit) {
            return tasks.values().stream()
                    .filter((task) -> taskType.equals(task.getTaskType()))
                    .filter((task) -> task.getStatus() != null && task.getStatus().isActive())
                    .sorted(Comparator.comparing(OperationalTask::getId))
                    .limit(limit)
                    .map(OperationalTask::copy)
                    .collect(Collectors.toList());
        }

        @Override
        public List<OperationalTask> listRecent(String taskType, int limit) {
            return tasks.values().stream()
                    .filter((task) -> taskType == null || taskType.equals(task.getTaskType()))
                    .sorted(Comparator.comparing(OperationalTask::getId).reversed())
                    .limit(limit)
                    .map(OperationalTask::copy)
                    .collect(Collectors.toList());
        }
    }

    private static final class CapturingRiskBackoffRepository implements NoonRiskBackoffRepository {
        private final Map<String, NoonRiskBackoffHold> holds = new LinkedHashMap<>();

        @Override
        public void upsert(NoonRiskBackoffHold hold) {
            holds.put(hold.getScopeKey(), hold.copy());
        }

        @Override
        public NoonRiskBackoffHold selectActiveHold(String scopeKey, LocalDateTime now) {
            NoonRiskBackoffHold hold = holds.get(scopeKey);
            if (hold == null || hold.getBlockedUntil() == null || !hold.getBlockedUntil().isAfter(now)) {
                return null;
            }
            return hold.copy();
        }

        @Override
        public NoonRiskBackoffHold selectLatestHold(String scopeKey) {
            NoonRiskBackoffHold hold = holds.get(scopeKey);
            return hold == null ? null : hold.copy();
        }
    }
}
