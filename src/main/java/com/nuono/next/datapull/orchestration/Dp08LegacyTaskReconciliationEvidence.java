package com.nuono.next.datapull.orchestration;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.datapull.orchestration.Dp08LegacyTaskReconciliationModels.ActiveRun;
import com.nuono.next.datapull.orchestration.Dp08LegacyTaskReconciliationModels.ActiveTask;
import com.nuono.next.infrastructure.mapper.Dp08LegacyTaskReconciliationMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Fail-closed proof that only internally consistent manual DP08 work remains active. */
public final class Dp08LegacyTaskReconciliationEvidence
        implements DataPullRuntimeReleaseEvidence {
    private static final String REFRESH = "OPERATIONS_COMPETITOR_REFRESH";
    private static final String MONITORING = "OPERATIONS_COMPETITOR_MONITORING";
    private static final String MONITORING_CYCLE =
            "OPERATIONS_COMPETITOR_MONITORING_CYCLE";
    private static final String MANUAL_REFRESH = "MANUAL_REFRESH";
    private static final String MANUAL_MONITOR = "MANUAL_MONITOR";
    private static final Set<String> MANUAL = Set.of(MANUAL_REFRESH, MANUAL_MONITOR);
    private static final Set<String> SCHEDULED = Set.of(
            "SCHEDULED_RANK_MONITOR", "SCHEDULED_DETAIL_MONITOR"
    );
    private static final Set<String> ACTIVE = Set.of("QUEUED", "RUNNING");
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    private final Dp08LegacyTaskReconciliationMapper mapper;

    public Dp08LegacyTaskReconciliationEvidence(
            Dp08LegacyTaskReconciliationMapper mapper
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public DataPullRuntimeReleaseRequirement requirement() {
        return DataPullRuntimeReleaseRequirement.DP08_LEGACY_TASK_RECONCILIATION;
    }

    @Override
    public boolean verified() {
        try {
            return reconciled(List.copyOf(Objects.requireNonNull(
                    mapper.listActiveRows(), "active DP08 rows"
            )));
        } catch (RuntimeException invalidEvidence) {
            return false;
        }
    }

    private boolean reconciled(List<Dp08LegacyTaskReconciliationRow> rows) {
        Map<Long, ActiveTask> tasks = new HashMap<>();
        Map<Long, ActiveRun> runs = new HashMap<>();
        for (Dp08LegacyTaskReconciliationRow row : rows) {
            Dp08LegacyTaskReconciliationRow value = Objects.requireNonNull(row, "active row");
            if ("TASK".equals(value.getRecordKind())) {
                ActiveTask task = task(value);
                if (tasks.put(task.id(), task) != null) {
                    return false;
                }
            } else if ("RUN".equals(value.getRecordKind())) {
                ActiveRun run = run(value);
                if (runs.put(run.id(), run) != null) {
                    return false;
                }
            } else {
                return false;
            }
        }

        Map<Long, List<ActiveRun>> runsByTask = new HashMap<>();
        for (ActiveRun run : runs.values()) {
            if (SCHEDULED.contains(run.triggerMode())
                    || !MANUAL.contains(run.triggerMode())) {
                return false;
            }
            runsByTask.computeIfAbsent(run.taskId(), ignored -> new ArrayList<>()).add(run);
        }

        for (ActiveTask task : tasks.values()) {
            String triggerMode = strictText(task.payload(), "triggerMode");
            if (SCHEDULED.contains(triggerMode)) {
                return false;
            }
            List<ActiveRun> linked = runsByTask.getOrDefault(task.id(), List.of());
            if (REFRESH.equals(task.taskType())) {
                if (!MANUAL.contains(triggerMode)
                        || linked.size() != 1
                        || !validRefreshPair(task, linked.get(0))) {
                    return false;
                }
            } else if (MONITORING.equals(task.taskType())) {
                if (!linked.isEmpty() || !validManualBatch(task, triggerMode)) {
                    return false;
                }
            } else if (MONITORING_CYCLE.equals(task.taskType())) {
                return false;
            } else {
                return false;
            }
        }

        for (ActiveRun run : runs.values()) {
            ActiveTask task = tasks.get(run.taskId());
            if (task == null || !REFRESH.equals(task.taskType())) {
                return false;
            }
        }
        return true;
    }

    private boolean validRefreshPair(ActiveTask task, ActiveRun run) {
        String triggerMode = strictText(task.payload(), "triggerMode");
        String executionMode = MANUAL_REFRESH.equals(run.triggerMode())
                ? "full" : "full-monitor";
        String naturalKey = "watchProduct:" + run.watchProductId()
                + (MANUAL_MONITOR.equals(run.triggerMode()) ? ":full-monitor" : "");
        return task.status().equals(run.status())
                && triggerMode.equals(run.triggerMode())
                && executionMode.equals(strictText(task.payload(), "executionMode"))
                && positiveLong(task.payload(), "watchProductId") == run.watchProductId()
                && strictBoolean(task.payload(), "rankRefresh")
                && strictBoolean(task.payload(), "detailRefresh")
                && naturalKey.equals(task.naturalKey());
    }

    private boolean validManualBatch(ActiveTask task, String triggerMode) {
        if (!MANUAL_MONITOR.equals(triggerMode)
                || !"full-monitor".equals(strictText(task.payload(), "executionMode"))
                || task.ownerUserId() == null || task.ownerUserId() <= 0L) {
            return false;
        }
        String storeCode = identityText(task.storeCode());
        String siteCode = identityText(task.siteCode());
        if (!("store:" + task.ownerUserId() + ":" + storeCode + ":" + siteCode)
                .equals(task.naturalKey())) {
            return false;
        }
        JsonNode batchKind = task.payload().get("batchKind");
        if (batchKind == null || batchKind.isNull()) {
            return nonNegativeLong(task.payload(), "watchProductTotal") >= 0L
                    && strictBoolean(task.payload(), "rankRefresh")
                    && strictBoolean(task.payload(), "detailRefresh");
        }
        return "STORE".equals(strictText(task.payload(), "batchKind"))
                && !strictText(task.payload(), "batchKey").isEmpty()
                && positiveLong(task.payload(), "currentOwnerUserId") == task.ownerUserId()
                && storeCode.equals(strictText(task.payload(), "currentStoreCode"))
                && siteCode.equals(strictText(task.payload(), "currentSiteCode"));
    }

    private ActiveTask task(Dp08LegacyTaskReconciliationRow row) {
        long id = positive(row.getRecordId(), "task id");
        String taskType = Objects.requireNonNull(row.getTaskType(), "task type");
        if (!Set.of(REFRESH, MONITORING, MONITORING_CYCLE).contains(taskType)) {
            throw new IllegalArgumentException("unknown DP08 task type");
        }
        return new ActiveTask(
                id, taskType, activeStatus(row.getStatus()), object(row.getPayloadJson()),
                row.getOwnerUserId(), row.getStoreCode(), row.getSiteCode(),
                identityText(row.getNaturalKey())
        );
    }

    private ActiveRun run(Dp08LegacyTaskReconciliationRow row) {
        return new ActiveRun(
                positive(row.getRecordId(), "run id"),
                positive(row.getTaskId(), "run task id"),
                positive(row.getWatchProductId(), "watch product id"),
                activeStatus(row.getStatus()),
                identityText(row.getTriggerMode())
        );
    }

    private static ObjectNode object(String payloadJson) {
        try {
            JsonNode value = JSON.readTree(Objects.requireNonNull(payloadJson, "payload"));
            if (!value.isObject()) {
                throw new IllegalArgumentException("DP08 payload must be an object");
            }
            return (ObjectNode) value;
        } catch (java.io.IOException invalidJson) {
            throw new IllegalArgumentException("DP08 payload is malformed", invalidJson);
        }
    }

    private static String strictText(ObjectNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || !value.isTextual()) {
            throw new IllegalArgumentException(field + " must be text");
        }
        return identityText(value.textValue());
    }

    private static boolean strictBoolean(ObjectNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || !value.isBoolean()) {
            throw new IllegalArgumentException(field + " must be boolean");
        }
        return value.booleanValue();
    }

    private static long positiveLong(ObjectNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return positive(value.longValue(), field);
    }

    private static long nonNegativeLong(ObjectNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()
                || value.longValue() < 0L) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
        return value.longValue();
    }

    private static long positive(Long value, String field) {
        if (value == null || value <= 0L) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static String activeStatus(String value) {
        String status = identityText(value);
        if (!ACTIVE.contains(status)) {
            throw new IllegalArgumentException("unknown active status");
        }
        return status;
    }

    private static String identityText(String value) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException("identity text is malformed");
        }
        return value;
    }

}
