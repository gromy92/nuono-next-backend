package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskPayload;
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompetitorStaleRecoveryPayloadTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-06-06T08:00:00Z"),
            ZoneOffset.UTC
    );
    private static final LocalDateTime STALE_BEFORE =
            LocalDateTime.parse("2026-06-06T07:30:00");

    @Mock
    private CompetitorAnalysisMapper mapper;
    @Mock
    private OperationalTaskService operationalTaskService;
    @Mock
    private CompetitorTaskSubmitter taskSubmitter;

    @Test
    void staleReplacementPreservesRetryStateAndWaitsForNotBefore() throws Exception {
        OperationalTask staleTask = staleTask();
        CompetitorSearchRunRow staleRun = run(220001L, 150001L, "RUNNING");
        CompetitorSearchRunRow queuedRun = run(220002L, 150002L, "QUEUED");
        AtomicReference<OperationalTask> replacementReference = new AtomicReference<>();
        when(operationalTaskService.failStaleRunning(
                150001L,
                STALE_BEFORE,
                "FAILED_STALE",
                "刷新任务超过 30 分钟未完成，已自动释放。"
        )).thenReturn(true);
        when(mapper.markActiveSearchRunFailedForTask(
                220001L,
                150001L,
                "FAILED_STALE",
                "刷新任务超过 30 分钟未完成，已自动释放。"
        )).thenReturn(1);
        when(operationalTaskService.queue(any(), any(), any()))
                .thenAnswer(invocation -> {
                    OperationalTaskPayload payload = invocation.getArgument(2);
                    OperationalTask replacement = replacementTask(payload.getPayloadJson());
                    replacementReference.set(replacement);
                    return replacement;
                });
        when(mapper.selectSearchRunByTaskId(150002L)).thenReturn(null, queuedRun);
        when(mapper.nextSearchRunId()).thenReturn(220002L);
        when(operationalTaskService.find(150002L))
                .thenAnswer(ignored -> Optional.ofNullable(replacementReference.get()));
        CompetitorRefreshTaskFactory factory =
                new CompetitorRefreshTaskFactory(mapper, operationalTaskService);
        CompetitorRefreshRecoveryCoordinator coordinator =
                new CompetitorRefreshRecoveryCoordinator(
                        mapper,
                        operationalTaskService,
                        factory,
                        new CompetitorRefreshTaskDispatcher(
                                mapper, operationalTaskService, taskSubmitter
                        ),
                        ignored -> true,
                        (taskId, runId, watchProductId, actorUserId, mode) -> {
                        },
                        CLOCK
                );

        assertTrue(coordinator.recoverInterrupted(
                staleTask, watchProduct(), staleRun, STALE_BEFORE
        ));

        ArgumentCaptor<OperationalTaskPayload> payloadCaptor =
                ArgumentCaptor.forClass(OperationalTaskPayload.class);
        verify(operationalTaskService).queue(any(), any(), payloadCaptor.capture());
        JsonNode payload = new ObjectMapper().readTree(
                payloadCaptor.getValue().getPayloadJson()
        );
        assertEquals(180001L, payload.path("watchProductId").asLong());
        assertEquals(9, payload.path("keywordTotal").asInt());
        assertEquals("SCHEDULED_DETAIL_MONITOR", payload.path("triggerMode").asText());
        assertEquals("detail", payload.path("executionMode").asText());
        assertFalse(payload.path("rankRefresh").asBoolean());
        assertTrue(payload.path("detailRefresh").asBoolean());
        assertEquals("batch-20260606", payload.path("batchKey").asText());
        assertEquals(3, payload.path("retryAttempt").asInt());
        assertEquals(4, payload.path("maxRetryAttempts").asInt());
        assertEquals("2026-06-06T09:00:00", payload.path("retryNotBefore").asText());
        assertEquals(220000L, payload.path("rootRunId").asLong());
        assertEquals(219999L, payload.path("retryOfRunId").asLong());
        assertEquals(7, payload.path("detailTargetTotal").asInt());
        assertEquals(8, payload.path("detailRequestAttemptCount").asInt());
        assertEquals(4, payload.path("detailSucceededCount").asInt());
        assertEquals(2, payload.path("detailTerminalFailedCount").asInt());
        assertEquals("N123", payload.path("detailRetryStates").get(0)
                .path("noonProductCode").asText());
        assertEquals(3, payload.path("detailRetryStates").get(0)
                .path("retryAttempt").asInt());
        assertEquals("N123", payload.path("failedDetailTargets").get(0)
                .path("noonProductCode").asText());
        assertTrue(payload.path("extension").path("keep").asBoolean());
        assertFalse(payload.has("taskId"));
        assertFalse(payload.has("runId"));
        assertFalse(payload.has("naturalKey"));
        assertFalse(CompetitorRefreshRecoveryPayload.isReady(
                replacementReference.get(), LocalDateTime.parse("2026-06-06T08:00:00")
        ));
        assertTrue(CompetitorRefreshRecoveryPayload.isReady(
                replacementReference.get(), LocalDateTime.parse("2026-06-06T09:00:00")
        ));
        verify(taskSubmitter, never()).submit(any(), any());
        verify(operationalTaskService, never()).claimQueued(any(), any());
    }

    private static OperationalTask staleTask() {
        OperationalTask task = new OperationalTask();
        task.setId(150001L);
        task.setTaskType(CompetitorAnalysisRefreshService.TASK_TYPE);
        task.setNaturalKey("watchProduct:180001:detail");
        task.setStatus(OperationalTaskStatus.RUNNING);
        task.setUpdatedAt(LocalDateTime.parse("2026-06-06T07:20:00"));
        task.setPayloadJson("{"
                + "\"watchProductId\":999,"
                + "\"keywordTotal\":9,"
                + "\"triggerMode\":\"WRONG\","
                + "\"executionMode\":\"wrong\","
                + "\"rankRefresh\":true,"
                + "\"detailRefresh\":false,"
                + "\"batchKey\" : \"batch-20260606\","
                + "\"taskId\":1,\"runId\":2,\"naturalKey\":\"dirty\","
                + "\"retryAttempt\":3,\"maxRetryAttempts\":4,"
                + "\"retryNotBefore\":\"2026-06-06T09:00:00\","
                + "\"rootRunId\":220000,\"retryOfRunId\":219999,"
                + "\"lastErrorCode\":\"PUBLIC_DETAIL_NOT_FOUND\","
                + "\"message\":\"retry later\","
                + "\"detailTargetTotal\":7,\"detailRequestAttemptCount\":8,"
                + "\"detailSucceededCount\":4,\"detailTerminalFailedCount\":2,"
                + "\"detailTerminalErrorCode\":\"INVALID_NOON_PRODUCT_CODE\","
                + "\"detailTerminalErrorMessage\":\"invalid code\","
                + "\"detailRetryStates\":[{"
                + "\"subjectType\":\"COMPETITOR\","
                + "\"competitorProductId\":200001,"
                + "\"noonProductCode\":\"N123\","
                + "\"retryAttempt\":3,"
                + "\"retryNotBefore\":\"2026-06-06T09:00:00\","
                + "\"errorCode\":\"PUBLIC_DETAIL_NOT_FOUND\","
                + "\"errorMessage\":\"retry later\"}],"
                + "\"failedDetailTargets\":[{"
                + "\"subjectType\":\"COMPETITOR\","
                + "\"competitorProductId\":200001,"
                + "\"noonProductCode\":\"N123\"}],"
                + "\"extension\":{\"keep\":true}"
                + "}");
        return task;
    }

    private static OperationalTask replacementTask(String payloadJson) {
        OperationalTask task = staleTask();
        task.setId(150002L);
        task.setStatus(OperationalTaskStatus.QUEUED);
        task.setPayloadJson(payloadJson);
        return task;
    }

    private static CompetitorSearchRunRow run(long id, long taskId, String status) {
        CompetitorSearchRunRow run = new CompetitorSearchRunRow();
        run.setId(id);
        run.setTaskId(taskId);
        run.setWatchProductId(180001L);
        run.setStatus(status);
        run.setTriggerMode("SCHEDULED_DETAIL_MONITOR");
        run.setRequestedBy(501L);
        return run;
    }

    private static CompetitorWatchProductRow watchProduct() {
        CompetitorWatchProductRow row = new CompetitorWatchProductRow();
        row.setId(180001L);
        row.setOwnerUserId(501L);
        row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA");
        return row;
    }
}
