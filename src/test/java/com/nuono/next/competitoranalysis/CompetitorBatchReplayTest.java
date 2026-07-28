package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.infrastructure.mapper.CompetitorMonitoringMapper;
import com.nuono.next.infrastructure.mapper.OperationalTaskMapper;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompetitorBatchReplayTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-06-06T08:00:00Z"),
            ZoneOffset.UTC
    );
    private static final String CHILD_KEY = "watchProduct:180123:detail";
    private static final String BATCH_KEY = "batch-replay-001";

    @Mock
    private CompetitorAnalysisMapper mapper;
    @Mock
    private CompetitorMonitoringMapper monitoringMapper;

    private InMemoryOperationalTaskRepository repository;
    private List<Runnable> submitted;
    private CompetitorAnalysisRefreshService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryOperationalTaskRepository();
        submitted = new ArrayList<>();
        service = new CompetitorAnalysisRefreshService(
                mapper,
                monitoringMapper,
                new OperationalTaskService(repository, CLOCK),
                (accountKey, task) -> submitted.add(task),
                CLOCK
        );
    }

    @Test
    void replayReusesMatchingHistoricalChildEvenWhenNewerTaskHasNoBatchKey() {
        OperationalTask sameBatch = terminalChild(
                150000L,
                childPayload(BATCH_KEY)
        );
        OperationalTask newerWithoutBatch = terminalChild(
                150001L,
                childPayload(null)
        );
        repository.insert(sameBatch);
        repository.insert(newerWithoutBatch);
        when(mapper.selectSearchRunByTaskId(150000L))
                .thenReturn(searchRun(220000L, 150000L, "SUCCEEDED"));
        stubProducts(List.of(watchProduct()));
        repository.insert(queuedReplayParent(150002L));

        assertEquals(1, service.resumeQueuedRefreshTasks());
        submitted.get(0).run();

        OperationalTask parent = repository.selectById(150002L);
        assertEquals(OperationalTaskStatus.SUCCEEDED, parent.getStatus());
        assertTrue(parent.getResultJson().contains("\"alreadyAttempted\":1"));
        assertEquals(3, repository.tasks.size());
        verify(mapper, never()).insertSearchRun(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void staleBatchChildReplacementRetainsOriginalBatchPayload() {
        OperationalTask stale = task(
                150000L,
                CompetitorAnalysisRefreshService.TASK_TYPE,
                CHILD_KEY
        );
        stale.setPayloadJson(childPayload(BATCH_KEY));
        stale.setStartedAt(LocalDateTime.parse("2026-06-06T07:20:00"));
        stale.setUpdatedAt(LocalDateTime.parse("2026-06-06T07:20:00"));
        repository.insert(stale);
        CompetitorSearchRunRow interruptedRun = searchRun(220000L, 150000L, "RUNNING");
        when(mapper.selectSearchRunByTaskId(150000L)).thenReturn(interruptedRun);
        when(mapper.selectWatchProductForRefresh(180123L)).thenReturn(watchProduct());
        when(mapper.nextSearchRunId()).thenReturn(220001L);

        assertEquals(1, service.recoverStaleRefreshTasks());

        OperationalTask replacement = repository.selectById(150001L);
        assertEquals(OperationalTaskStatus.FAILED, repository.selectById(150000L).getStatus());
        assertEquals(OperationalTaskStatus.QUEUED, replacement.getStatus());
        assertEquals(stale.getPayloadJson(), replacement.getPayloadJson());
        verify(mapper).insertSearchRun(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void productionLookupMatchesBatchKeyAsJsonAndSkipsReleasedStaleAttempt() throws Exception {
        Method method = OperationalTaskMapper.class.getMethod(
                "selectLatestByNaturalKeyAndBatchKey",
                String.class,
                String.class,
                String.class
        );
        String sql = String.join(" ", method.getAnnotation(Select.class).value());

        assertTrue(sql.contains("JSON_UNQUOTE(JSON_EXTRACT"));
        assertTrue(sql.contains("'$.batchKey'"));
        assertTrue(sql.contains("= #{batchKey}"));
        assertTrue(sql.contains("COALESCE(error_code, '') <> 'FAILED_STALE'"));
    }

    private void stubProducts(List<CompetitorWatchProductRow> products) {
        when(monitoringMapper.listRefreshableWatchProducts(
                org.mockito.ArgumentMatchers.eq(501L),
                org.mockito.ArgumentMatchers.eq("STR108065-NSA"),
                org.mockito.ArgumentMatchers.eq("SA"),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt()
        )).thenAnswer(invocation -> {
            long afterId = invocation.getArgument(3);
            return products.stream()
                    .filter(product -> product.getId() > afterId)
                    .collect(Collectors.toList());
        });
    }

    private static OperationalTask queuedReplayParent(long taskId) {
        CompetitorMonitoringCheckpoint checkpoint = new CompetitorMonitoringCheckpoint();
        checkpoint.setBatchKind("STORE");
        checkpoint.setBatchKey(BATCH_KEY);
        checkpoint.setTriggerMode(CompetitorRefreshExecutionMode.SCHEDULED_DETAIL.triggerMode());
        checkpoint.setExecutionMode(CompetitorRefreshExecutionMode.SCHEDULED_DETAIL.taskKey());
        checkpoint.setCurrentOwnerUserId(501L);
        checkpoint.setCurrentStoreCode("STR108065-NSA");
        checkpoint.setCurrentSiteCode("SA");
        checkpoint.setUpperWatchProductId(180123L);
        checkpoint.setEligibleProductTotal(1L);
        OperationalTask task = task(taskId, CompetitorMonitoringBatchService.STORE_TASK_TYPE, "replay-parent");
        task.setStatus(OperationalTaskStatus.QUEUED);
        task.setPayloadJson(checkpoint.toJson());
        return task;
    }

    private static OperationalTask terminalChild(long taskId, String payloadJson) {
        OperationalTask task = task(
                taskId,
                CompetitorAnalysisRefreshService.TASK_TYPE,
                CHILD_KEY
        );
        task.setStatus(OperationalTaskStatus.SUCCEEDED);
        task.setPayloadJson(payloadJson);
        task.setFinishedAt(LocalDateTime.parse("2026-06-06T07:50:00"));
        return task;
    }

    private static OperationalTask task(long taskId, String taskType, String naturalKey) {
        OperationalTask task = new OperationalTask();
        task.setId(taskId);
        task.setTaskType(taskType);
        task.setNaturalKey(naturalKey);
        task.setOwnerUserId(501L);
        task.setStoreCode("STR108065-NSA");
        task.setSiteCode("SA");
        task.setStatus(OperationalTaskStatus.RUNNING);
        task.setProgressPercent(0);
        task.setCreatedAt(LocalDateTime.parse("2026-06-06T07:40:00"));
        task.setUpdatedAt(LocalDateTime.parse("2026-06-06T07:50:00"));
        return task;
    }

    private static String childPayload(String batchKey) {
        return "{"
                + "\"watchProductId\":180123,"
                + "\"keywordTotal\":0,"
                + "\"triggerMode\":\"SCHEDULED_DETAIL_MONITOR\","
                + "\"executionMode\":\"detail\","
                + "\"rankRefresh\":false,"
                + "\"detailRefresh\":true"
                + (batchKey == null ? "" : ",\"batchKey\":\"" + batchKey + "\"")
                + "}";
    }

    private static CompetitorWatchProductRow watchProduct() {
        CompetitorWatchProductRow row = new CompetitorWatchProductRow();
        row.setId(180123L);
        row.setOwnerUserId(501L);
        row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA");
        row.setPartnerSku("BASKET-SA-001-BLUE");
        row.setSelfNoonProductCode("ZSELF001");
        row.setStatus("ACTIVE");
        return row;
    }

    private static CompetitorSearchRunRow searchRun(long runId, long taskId, String status) {
        CompetitorSearchRunRow row = new CompetitorSearchRunRow();
        row.setId(runId);
        row.setWatchProductId(180123L);
        row.setTaskId(taskId);
        row.setTriggerMode(CompetitorRefreshExecutionMode.SCHEDULED_DETAIL.triggerMode());
        row.setStatus(status);
        row.setKeywordTotal(0);
        return row;
    }
}
