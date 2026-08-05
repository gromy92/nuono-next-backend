package com.nuono.next.competitoranalysis.dp08;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.schedule.ScheduleTaskBindingRow;
import com.nuono.next.datapull.schedule.ScheduleTaskPayloadBinder;
import com.nuono.next.datapull.scope.DataPullScopeBindingCandidate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Validates and attaches compact DP08 temporal payloads to immutable scheduled tasks. */
@Component
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public final class Dp08ScheduleTaskPayloadBinder implements ScheduleTaskPayloadBinder {
    private static final Set<OperationCode> OPERATIONS = Set.of(
            OperationCode.DP08A,
            OperationCode.DP08B
    );

    private final Dp08MemberSetHandleCodec handles;
    private final Dp08ScopeSnapshotCodec legacy;

    public Dp08ScheduleTaskPayloadBinder(ObjectMapper objectMapper) {
        handles = new Dp08MemberSetHandleCodec(objectMapper);
        legacy = new Dp08ScopeSnapshotCodec(objectMapper);
    }

    @Override
    public Set<OperationCode> operations() {
        return OPERATIONS;
    }

    @Override
    public void bind(
            OperationCode operation,
            List<DataPullTask> tasks,
            List<ScheduleTaskBindingRow> temporalBindings
    ) {
        OperationCode expected = requireOperation(operation);
        Map<String, ScheduleTaskBindingRow> bindings = bindings(temporalBindings);
        for (DataPullTask task : List.copyOf(tasks)) {
            if (task == null || task.getOperationCode() != expected) {
                throw new IllegalArgumentException(
                        "DP08 payload batch contains a task for another operation"
                );
            }
            ScheduleTaskBindingRow binding = bindings.remove(key(task));
            if (binding == null) {
                throw new IllegalStateException("DP08_TEMPORAL_BINDING_MISSING");
            }
            attach(expected, task, binding);
            decode(expected, task, binding.getPayloadType());
        }
        if (!bindings.isEmpty()) {
            throw new IllegalStateException("DP08_TEMPORAL_BINDING_UNKNOWN_TASK");
        }
    }

    private void attach(
            OperationCode operation,
            DataPullTask task,
            ScheduleTaskBindingRow binding
    ) {
        DataPullScopeBindingCandidate proof = new DataPullScopeBindingCandidate(
                operation,
                task.getScopeKey(),
                binding.getPayloadType(),
                binding.getPayload(),
                binding.getEffectiveFromUtc()
        );
        if (!proof.getBindingId().equals(binding.getBindingId())
                || !proof.getPayloadSha256().equals(binding.getPayloadSha256())) {
            throw new IllegalStateException("DP08_TEMPORAL_BINDING_DIGEST_DRIFT");
        }
        task.setScopeBindingId(binding.getBindingId());
        task.setScopeBindingEffectiveFromUtc(binding.getEffectiveFromUtc());
        task.setScopePayloadType(binding.getPayloadType());
        task.setScopePayloadSha256(binding.getPayloadSha256());
        task.setScopePayload(binding.getPayload());
    }

    private void decode(OperationCode operation, DataPullTask task, String payloadType) {
        if (operation == OperationCode.DP08A) {
            if (Dp08MemberSetHandleCodec.KEYWORD_TYPE.equals(payloadType)) {
                handles.decode(task);
                return;
            }
            if (Dp08ScopeSnapshotCodec.KEYWORD_V1.equals(payloadType)) {
                legacy.decodeKeyword(task);
                return;
            }
        } else {
            if (Dp08MemberSetHandleCodec.LIST_TYPE.equals(payloadType)) {
                handles.decode(task);
                return;
            }
            if (Dp08ScopeSnapshotCodec.LIST_TARGET_V1.equals(payloadType)) {
                legacy.decodeListTarget(task);
                return;
            }
        }
        throw new IllegalStateException("DP08_TEMPORAL_PAYLOAD_TYPE_UNSUPPORTED");
    }

    private static Map<String, ScheduleTaskBindingRow> bindings(
            List<ScheduleTaskBindingRow> values
    ) {
        Map<String, ScheduleTaskBindingRow> result = new HashMap<>();
        for (ScheduleTaskBindingRow row : List.copyOf(
                Objects.requireNonNull(values, "temporalBindings")
        )) {
            ScheduleTaskBindingRow value = Objects.requireNonNull(row, "temporal binding");
            String key = value.getScopeKey() + "\0" + value.getScheduleSlot();
            if (result.put(key, value) != null) {
                throw new IllegalStateException("DP08_TEMPORAL_BINDING_DUPLICATE");
            }
        }
        return result;
    }

    private static OperationCode requireOperation(OperationCode operation) {
        OperationCode value = Objects.requireNonNull(operation, "operation");
        if (!OPERATIONS.contains(value)) {
            throw new IllegalArgumentException("DP08 payload binder received " + value);
        }
        return value;
    }

    private static String key(DataPullTask task) {
        return task.getScopeKey() + "\0" + task.getScheduleSlot();
    }
}
