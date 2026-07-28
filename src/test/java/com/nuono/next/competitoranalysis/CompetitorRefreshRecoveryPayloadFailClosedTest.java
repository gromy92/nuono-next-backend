package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.system.task.OperationalTask;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompetitorRefreshRecoveryPayloadFailClosedTest {
    private static final LocalDateTime NOW =
            LocalDateTime.parse("2026-07-28T09:00:00");

    @Test
    void targetedRetryMetadataAlwaysParksForTypedCoordinator() {
        List<String> fields = List.of(
                "\"detailRetryStates\":[]",
                "\"detailRetryProtocol\":\"v2\"",
                "\"detailRetrySchemaVersion\":2",
                "\"detailRetryProjectionVersion\":2",
                "\"detailRetryStateChecksum\":\"state\"",
                "\"detailRetryLegacyProjectionChecksum\":\"legacy\"",
                "\"failedDetailTargets\":[]",
                "\"retryAttempt\":0",
                "\"maxRetryAttempts\":4",
                "\"rootRunId\":220001",
                "\"retryOfRunId\":220000",
                "\"lastErrorCode\":null",
                "\"message\":\"retry\""
        );

        for (String field : fields) {
            OperationalTask task = task(
                    "{\"retryNotBefore\":\"2026-07-28T08:00:00\"," + field + "}"
            );
            assertFalse(
                    CompetitorRefreshRecoveryPayload.isReady(task, NOW),
                    field
            );
        }
    }

    @Test
    void plainGlobalRetryNotBeforeRequiresStrictTextualIsoTime() {
        assertTrue(CompetitorRefreshRecoveryPayload.isReady(task(null), NOW));
        assertTrue(CompetitorRefreshRecoveryPayload.isReady(task("{}"), NOW));
        assertFalse(CompetitorRefreshRecoveryPayload.isReady(
                task("{\"retryNotBefore\":\"2026-07-28T09:00:01\"}"), NOW
        ));
        assertTrue(CompetitorRefreshRecoveryPayload.isReady(
                task("{\"retryNotBefore\":\"2026-07-28T09:00:00\"}"), NOW
        ));
        assertTrue(CompetitorRefreshRecoveryPayload.isReady(
                task("{\"retryNotBefore\":\"2026-07-28T08:59:59\"}"), NOW
        ));
        for (String invalid : List.of(
                "null",
                "0",
                "true",
                "\"\"",
                "\"not-a-date\"",
                "{}",
                "[]"
        )) {
            assertThrows(
                    CompetitorDetailRetryPayloadException.class,
                    () -> CompetitorRefreshRecoveryPayload.isReady(
                            task("{\"retryNotBefore\":" + invalid + "}"), NOW
                    )
            );
        }
    }

    @Test
    void malformedOrNonObjectPayloadNeverBecomesAnEmptyFullRefresh() {
        for (String invalid : List.of("{", "[]", "\"text\"", "1", "null", "{} []")) {
            OperationalTask staleTask = task(invalid);
            assertThrows(
                    CompetitorDetailRetryPayloadException.class,
                    () -> CompetitorRefreshRecoveryPayload.isReady(staleTask, NOW)
            );
            assertThrows(
                    CompetitorRefreshRecoveryPayloadException.class,
                    () -> CompetitorRefreshRecoveryPayload.replacement(
                            staleTask,
                            180001L,
                            3,
                            CompetitorRefreshExecutionMode.SCHEDULED_RANK,
                            null
                    )
            );
            assertThrows(
                    CompetitorRefreshRecoveryPayloadException.class,
                    () -> CompetitorRefreshRecoveryPayload.batchKey(staleTask)
            );
        }
    }

    private static OperationalTask task(String payloadJson) {
        OperationalTask task = new OperationalTask();
        task.setPayloadJson(payloadJson);
        return task;
    }
}
