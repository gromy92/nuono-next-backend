package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskPayload;
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CompetitorInvalidDetailRetryPayloadTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-07-28T02:00:00Z"), ZoneOffset.UTC);

    @ParameterizedTest
    @MethodSource("invalidPayloads")
    void invalidRetryPayloadFailsTaskAndRunBeforeAnyDetailFetch(String payloadJson) {
        OperationalTaskService taskService = new OperationalTaskService(
                new InMemoryOperationalTaskRepository(),
                CLOCK
        );
        OperationalTask task = taskService.queue(
                CompetitorAnalysisRefreshService.TASK_TYPE,
                "watchProduct:180123:SCHEDULED_DETAIL_MONITOR:2026-07-28",
                OperationalTaskPayload.builder().payloadJson(payloadJson).build()
        );
        CompetitorAnalysisMapper mapper = mock(CompetitorAnalysisMapper.class);
        CompetitorSearchRunRow run = run(task.getId());
        when(mapper.selectSearchRunByTaskId(task.getId())).thenReturn(run);
        when(mapper.selectWatchProductForRefresh(180123L)).thenReturn(watchProduct());
        when(mapper.markSearchRunFailed(
                220123L,
                "INVALID_DETAIL_RETRY_PAYLOAD",
                "竞品详情重试载荷损坏，任务已终止以避免阻塞恢复队列。"
        )).thenReturn(1);
        CompetitorDetailRetryCoordinator coordinator = new CompetitorDetailRetryCoordinator(
                new CompetitorRefreshTaskFactory(mapper, taskService),
                CLOCK
        );
        AtomicInteger detailFetches = new AtomicInteger();
        CompetitorAnalysisTaskRecovery recovery = new CompetitorAnalysisTaskRecovery(
                mapper,
                taskService,
                CLOCK,
                (queued, queuedRun, product) -> {
                    if (!coordinator.isReady(queued)) {
                        return false;
                    }
                    detailFetches.incrementAndGet();
                    return true;
                },
                (product, interruptedRun) -> {
                }
        );

        assertEquals(0, recovery.resumeQueuedRefreshTasks());
        assertEquals(0, detailFetches.get());
        OperationalTask failed = taskService.find(task.getId()).orElseThrow();
        assertEquals(OperationalTaskStatus.FAILED, failed.getStatus());
        assertEquals("INVALID_DETAIL_RETRY_PAYLOAD", failed.getErrorCode());
    }

    private static Stream<String> invalidPayloads() throws Exception {
        return Stream.of(
                "{\"detailRetryStates\":[]}",
                "{\"retryAttempt\":1,"
                        + "\"maxRetryAttempts\":4,"
                        + "\"retryNotBefore\":\"2026-07-28T02:02:00\","
                        + "\"failedDetailTargets\":[]}",
                legacyTarget("\"subjectType\":\"COMPETITOR\","
                        + "\"noonProductCode\":\"ZFAIL001\""),
                legacyPayload("\"retryAttempt\":\"1\""),
                legacyPayload("\"retryAttempt\":5"),
                "{\"retryAttempt\":1,"
                        + "\"maxRetryAttempts\":\"4\","
                        + "\"retryNotBefore\":\"2026-07-28T02:02:00\","
                        + "\"failedDetailTargets\":[{"
                        + "\"subjectType\":\"SELF\","
                        + "\"noonProductCode\":\"ZSELF001\"}]}",
                legacyTarget("\"subjectType\":\"SELF\","
                        + "\"noonProductCode\":\"BAD\""),
                "{\"retryAttempt\":1,"
                        + "\"maxRetryAttempts\":4,"
                        + "\"retryNotBefore\":\"2026-07-28T02:02:00\","
                        + "\"failedDetailTargets\":[{"
                        + "\"subjectType\":\"SELF\","
                        + "\"noonProductCode\":\"ZSELF001\"},{"
                        + "\"subjectType\":\"SELF\","
                        + "\"noonProductCode\":\"ZSELF001\"}]}",
                "{\"detailRetryStates\":[{"
                        + "\"subjectType\":\"SELF\","
                        + "\"noonProductCode\":\"ZSELF001\","
                        + "\"retryAttempt\":1,"
                        + "\"retryNotBefore\":\"2026-07-28T02:02:00\"}]}",
                "{\"retryAttempt\":1,"
                        + "\"maxRetryAttempts\":4,"
                        + "\"retryNotBefore\":\"2026-07-28T02:02:00\","
                        + "\"failedDetailTargets\":[{"
                        + "\"subjectType\":\"SELF\","
                        + "\"noonProductCode\":\"ZSELF001\"}],"
                        + "\"detailRetrySchemaVersion\":2}",
                oldWriterRewrite(true, false),
                oldWriterRewrite(false, true),
                oldWriterRewrite(false, false),
                oldWriterSubsetRewrite()
        );
    }

    private static String legacyPayload(String attemptField) {
        return "{" + attemptField + ","
                + "\"maxRetryAttempts\":4,"
                + "\"retryNotBefore\":\"2026-07-28T02:02:00\","
                + "\"failedDetailTargets\":[{"
                + "\"subjectType\":\"SELF\","
                + "\"noonProductCode\":\"ZSELF001\"}]}";
    }

    private static String legacyTarget(String targetFields) {
        return "{\"retryAttempt\":1,"
                + "\"maxRetryAttempts\":4,"
                + "\"retryNotBefore\":\"2026-07-28T02:02:00\","
                + "\"failedDetailTargets\":[{" + targetFields + "}]}";
    }

    private static String oldWriterRewrite(boolean empty, boolean malformed) throws Exception {
        ObjectNode payload = (ObjectNode) JSON.readTree(
                CompetitorDetailRetryPayload.fromJson(
                        "{\"retryAttempt\":1,"
                                + "\"maxRetryAttempts\":4,"
                                + "\"retryNotBefore\":\"2026-07-28T02:02:00\","
                                + "\"failedDetailTargets\":[{"
                                + "\"subjectType\":\"COMPETITOR\","
                                + "\"competitorProductId\":88002,"
                                + "\"noonProductCode\":\"ZFAIL002\"}]}"
                ).toJson()
        );
        payload.put("retryAttempt", 2);
        payload.put("retryNotBefore", "2026-07-28T02:06:00");
        ArrayNode targets = payload.putArray("failedDetailTargets");
        if (!empty) {
            ObjectNode target = targets.addObject();
            target.put("subjectType", "COMPETITOR");
            if (malformed) {
                target.put("competitorProductId", "88002");
            } else {
                target.put("competitorProductId", 88002L);
            }
            target.put("noonProductCode", "ZFAIL002");
        }
        return JSON.writeValueAsString(payload);
    }

    private static String oldWriterSubsetRewrite() throws Exception {
        CompetitorDetailRetryPayload source = CompetitorDetailRetryPayload.empty();
        source.setRetryStates(Arrays.asList(
                state(CompetitorProductDetailTarget.self("ZSELF001"), "2026-07-28T02:02:00"),
                state(
                        CompetitorProductDetailTarget.competitor(
                                88002L,
                                "ZFAIL002",
                                null
                        ),
                        "2026-07-28T03:00:00"
                )
        ));
        ObjectNode payload = (ObjectNode) JSON.readTree(source.toJson());
        payload.put("retryAttempt", 2);
        payload.put("retryNotBefore", "2026-07-28T03:06:00");
        ArrayNode targets = payload.putArray("failedDetailTargets");
        ObjectNode target = targets.addObject();
        target.put("subjectType", "COMPETITOR");
        target.put("competitorProductId", 88002L);
        target.put("noonProductCode", "ZFAIL002");
        return JSON.writeValueAsString(payload);
    }

    private static CompetitorDetailRetryState state(
            CompetitorProductDetailTarget target,
            String wake
    ) {
        return new CompetitorDetailRetryState(
                target,
                1,
                LocalDateTime.parse(wake),
                "DETAIL_REFRESH_FAILED",
                "detail failed"
        );
    }

    private static CompetitorSearchRunRow run(Long taskId) {
        CompetitorSearchRunRow run = new CompetitorSearchRunRow();
        run.setId(220123L);
        run.setTaskId(taskId);
        run.setWatchProductId(180123L);
        run.setStatus("QUEUED");
        return run;
    }

    private static CompetitorWatchProductRow watchProduct() {
        CompetitorWatchProductRow row = new CompetitorWatchProductRow();
        row.setId(180123L);
        row.setOwnerUserId(501L);
        row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA");
        return row;
    }
}
