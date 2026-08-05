package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.Dp08LegacyTaskReconciliationMapper;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class Dp08LegacyTaskReconciliationEvidenceTest {
    private static final String REFRESH = "OPERATIONS_COMPETITOR_REFRESH";
    private static final String MONITORING = "OPERATIONS_COMPETITOR_MONITORING";
    private static final String CYCLE = "OPERATIONS_COMPETITOR_MONITORING_CYCLE";

    @Test
    void acceptsConsistentManualRefreshesAndCurrentOrLegacyManualBatches() {
        List<Dp08LegacyTaskReconciliationRow> rows = List.of(
                task(1L, REFRESH, "QUEUED", refreshPayload(91L, "MANUAL_REFRESH", "full"),
                        "watchProduct:91", null, null, null),
                run(101L, 1L, 91L, "QUEUED", "MANUAL_REFRESH"),
                task(2L, REFRESH, "RUNNING", refreshPayload(92L, "MANUAL_MONITOR", "full-monitor"),
                        "watchProduct:92:full-monitor", null, null, null),
                run(102L, 2L, 92L, "RUNNING", "MANUAL_MONITOR"),
                task(3L, MONITORING, "QUEUED", currentBatchPayload(),
                        "store:307:STR108065-NSA:SA", 307L, "STR108065-NSA", "SA"),
                task(4L, MONITORING, "RUNNING", legacyBatchPayload(),
                        "store:308:STR108066-NSA:AE", 308L, "STR108066-NSA", "AE")
        );

        assertTrue(evidence(rows).verified());
    }

    @Test
    void emptyActiveSnapshotLeavesTerminalHistoryUntouchedAndOpensGate() {
        assertTrue(evidence(List.of()).verified());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedActiveRows")
    void rejectsEveryScheduledUnknownOrInconsistentActiveMatrix(
            String ignoredCaseName,
            List<Dp08LegacyTaskReconciliationRow> rows
    ) {
        assertFalse(evidence(rows).verified());
    }

    @Test
    void missingOrFailingReadModelFailsClosed() {
        assertFalse(new Dp08LegacyTaskReconciliationEvidence(() -> null).verified());
        assertFalse(new Dp08LegacyTaskReconciliationEvidence(() -> {
            throw new IllegalStateException("database unavailable");
        }).verified());
    }

    @Test
    void registersIndependentRequirementAndStableBlocker() {
        Dp08LegacyTaskReconciliationEvidence evidence = evidence(List.of());

        assertEquals(
                DataPullRuntimeReleaseRequirement.DP08_LEGACY_TASK_RECONCILIATION,
                evidence.requirement()
        );
        assertEquals(
                "DP08_LEGACY_TASK_RECONCILIATION_UNVERIFIED",
                evidence.requirement().blockerCode()
        );
    }

    @Test
    void mapperIsOneReadOnlyActiveSnapshotAndNeverTouchesTerminalRows() throws Exception {
        Method method = Dp08LegacyTaskReconciliationMapper.class
                .getMethod("listActiveRows");
        Select select = method.getAnnotation(Select.class);
        assertNotNull(select);
        String sql = String.join(" ", select.value()).toUpperCase(Locale.ROOT);

        assertTrue(sql.contains("FROM OPERATIONAL_TASK"));
        assertTrue(sql.contains("FROM OPERATIONS_COMPETITOR_SEARCH_RUN"));
        assertTrue(sql.contains("UNION ALL"));
        assertEquals(2, occurrences(sql, "STATUS IN ('QUEUED', 'RUNNING')"));
        assertFalse(sql.contains("SUCCEEDED"));
        assertFalse(sql.contains("PARTIAL_FAILED"));
        assertFalse(sql.contains("CANCELLED"));
        assertFalse(Pattern.compile(
                "\\b(INSERT|UPDATE|DELETE|REPLACE|ALTER|DROP|CREATE|TRUNCATE)\\b"
        ).matcher(sql).find());
    }

    private static Stream<Arguments> rejectedActiveRows() {
        Dp08LegacyTaskReconciliationRow manual = task(
                1L, REFRESH, "QUEUED", refreshPayload(91L, "MANUAL_REFRESH", "full"),
                "watchProduct:91", null, null, null
        );
        Dp08LegacyTaskReconciliationRow manualRun =
                run(101L, 1L, 91L, "QUEUED", "MANUAL_REFRESH");
        return Stream.of(
                Arguments.of("scheduled task", List.of(task(
                        2L, REFRESH, "QUEUED",
                        refreshPayload(91L, "SCHEDULED_RANK_MONITOR", "rank"),
                        "watchProduct:91:rank", null, null, null
                ))),
                Arguments.of("scheduled run", List.of(
                        manual, run(102L, 1L, 91L, "QUEUED", "SCHEDULED_RANK_MONITOR")
                )),
                Arguments.of("unknown task trigger", List.of(task(
                        2L, REFRESH, "QUEUED",
                        refreshPayload(91L, "UNKNOWN", "full"),
                        "watchProduct:91", null, null, null
                ))),
                Arguments.of("unknown run trigger", List.of(
                        manual, run(102L, 1L, 91L, "QUEUED", "UNKNOWN")
                )),
                Arguments.of("orphan task", List.of(manual)),
                Arguments.of("orphan run", List.of(manualRun)),
                Arguments.of("malformed payload", List.of(task(
                        2L, REFRESH, "QUEUED", "{", "watchProduct:91",
                        null, null, null
                ))),
                Arguments.of("task and run status mismatch", List.of(
                        manual, run(102L, 1L, 91L, "RUNNING", "MANUAL_REFRESH")
                )),
                Arguments.of("task and run identity mismatch", List.of(
                        manual, run(102L, 1L, 92L, "QUEUED", "MANUAL_REFRESH")
                )),
                Arguments.of("duplicate active runs", List.of(
                        manual, manualRun,
                        run(102L, 1L, 91L, "QUEUED", "MANUAL_REFRESH")
                )),
                Arguments.of("manual cycle is not a manual batch", List.of(task(
                        2L, CYCLE, "QUEUED", currentBatchPayload(),
                        "cycle:full-monitor:slot", 307L, "STR108065-NSA", "SA"
                ))),
                Arguments.of("manual batch identity mismatch", List.of(task(
                        2L, MONITORING, "QUEUED", currentBatchPayload(),
                        "store:307:WRONG:SA", 307L, "STR108065-NSA", "SA"
                )))
        );
    }

    private static Dp08LegacyTaskReconciliationEvidence evidence(
            List<Dp08LegacyTaskReconciliationRow> rows
    ) {
        return new Dp08LegacyTaskReconciliationEvidence(() -> rows);
    }

    private static Dp08LegacyTaskReconciliationRow task(
            Long id,
            String type,
            String status,
            String payload,
            String naturalKey,
            Long owner,
            String store,
            String site
    ) {
        Dp08LegacyTaskReconciliationRow row = row("TASK", id, status);
        row.setTaskType(type);
        row.setPayloadJson(payload);
        row.setNaturalKey(naturalKey);
        row.setOwnerUserId(owner);
        row.setStoreCode(store);
        row.setSiteCode(site);
        return row;
    }

    private static Dp08LegacyTaskReconciliationRow run(
            Long id,
            Long taskId,
            Long watchProductId,
            String status,
            String triggerMode
    ) {
        Dp08LegacyTaskReconciliationRow row = row("RUN", id, status);
        row.setTaskId(taskId);
        row.setWatchProductId(watchProductId);
        row.setTriggerMode(triggerMode);
        return row;
    }

    private static Dp08LegacyTaskReconciliationRow row(
            String kind,
            Long id,
            String status
    ) {
        Dp08LegacyTaskReconciliationRow row = new Dp08LegacyTaskReconciliationRow();
        row.setRecordKind(kind);
        row.setRecordId(id);
        row.setStatus(status);
        return row;
    }

    private static String refreshPayload(long watchProductId, String trigger, String execution) {
        return "{\"watchProductId\":" + watchProductId
                + ",\"triggerMode\":\"" + trigger
                + "\",\"executionMode\":\"" + execution
                + "\",\"rankRefresh\":true,\"detailRefresh\":true}";
    }

    private static String currentBatchPayload() {
        return "{\"batchKind\":\"STORE\",\"batchKey\":\"batch-1\","
                + "\"triggerMode\":\"MANUAL_MONITOR\",\"executionMode\":\"full-monitor\","
                + "\"currentOwnerUserId\":307,\"currentStoreCode\":\"STR108065-NSA\","
                + "\"currentSiteCode\":\"SA\"}";
    }

    private static String legacyBatchPayload() {
        return "{\"triggerMode\":\"MANUAL_MONITOR\","
                + "\"executionMode\":\"full-monitor\",\"rankRefresh\":true,"
                + "\"detailRefresh\":true,\"watchProductTotal\":10}";
    }

    private static int occurrences(String value, String token) {
        return (value.length() - value.replace(token, "").length()) / token.length();
    }
}
