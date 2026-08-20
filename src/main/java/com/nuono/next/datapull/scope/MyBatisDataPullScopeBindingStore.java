package com.nuono.next.datapull.scope;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.DataPullScopeBindingMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Persistent Adapter that serializes a complete operation cohort and closes missing epochs. */
@Component
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public class MyBatisDataPullScopeBindingStore implements DataPullScopeBindingStore {
    private final DataPullScopeBindingMapper mapper;

    public MyBatisDataPullScopeBindingStore(DataPullScopeBindingMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    @Transactional
    public List<DataPullScopeBindingEpoch> reconcileCurrent(
            OperationCode operationCode,
            List<DataPullScopeBindingCandidate> currentBindings
    ) {
        OperationCode operation = Objects.requireNonNull(operationCode, "operationCode");
        List<DataPullScopeBindingCandidate> candidates = requireCandidates(
                operation, currentBindings
        );
        if (mapper.lockActiveOperation(operation) == null) {
            throw new IllegalStateException("DP_SCOPE_BINDING_CUTOVER_INACTIVE:" + operation);
        }
        LocalDateTime now = DataPullScopeBindingCandidate.requireMillisecond(
                mapper.selectDatabaseNowUtc(), "databaseNowUtc"
        );
        Map<String, DataPullScopeBindingEpoch> open = openByScope(operation);
        List<DataPullScopeBindingEpoch> result = new ArrayList<>(candidates.size());
        for (DataPullScopeBindingCandidate candidate : candidates) {
            if (candidate.getEffectiveFromUtc().isAfter(now)) {
                throw new IllegalStateException(
                        "DP_SCOPE_BINDING_FUTURE_EFFECTIVE:" + candidate.getScopeKey()
                );
            }
            DataPullScopeBindingEpoch previous = open.remove(candidate.getScopeKey());
            result.add(reconcileOne(previous, candidate, now));
        }
        for (DataPullScopeBindingEpoch missing : open.values()) {
            close(missing, now);
        }
        return List.copyOf(result);
    }

    private DataPullScopeBindingEpoch reconcileOne(
            DataPullScopeBindingEpoch previous,
            DataPullScopeBindingCandidate candidate,
            LocalDateTime now
    ) {
        if (previous != null && previous.samePayload(candidate)) {
            return previous;
        }
        DataPullScopeBindingCandidate transition = candidate;
        if (previous != null) {
            if (!transition.getEffectiveFromUtc().isAfter(previous.getEffectiveFromUtc())) {
                transition = observedTransition(candidate, previous, now);
            }
            if (!transition.getEffectiveFromUtc().isAfter(previous.getEffectiveFromUtc())) {
                throw new IllegalStateException(
                        "DP_SCOPE_BINDING_NON_MONOTONIC:" + candidate.getScopeKey()
                );
            }
            close(previous, transition.getEffectiveFromUtc());
        }
        DataPullScopeBindingEpoch proposed = DataPullScopeBindingEpoch.open(transition, now);
        int changed = mapper.insertOpenBinding(proposed);
        if (changed < 0 || changed > 1) {
            throw new IllegalStateException("scope binding insert changed an invalid row count");
        }
        DataPullScopeBindingEpoch stored = mapper.selectById(proposed.getBindingId());
        if (stored == null || !stored.samePayload(candidate)
                || stored.getEffectiveUntilUtc() != null) {
            throw new IllegalStateException(
                    "DP_SCOPE_BINDING_INSERT_DRIFT:" + candidate.getScopeKey()
            );
        }
        return stored;
    }

    private static DataPullScopeBindingCandidate observedTransition(
            DataPullScopeBindingCandidate candidate,
            DataPullScopeBindingEpoch previous,
            LocalDateTime observedAtUtc
    ) {
        if (!observedAtUtc.isAfter(previous.getEffectiveFromUtc())) {
            return candidate;
        }
        return new DataPullScopeBindingCandidate(
                candidate.getOperationCode(), candidate.getScopeKey(),
                candidate.getPayloadType(), candidate.getPayload(), observedAtUtc
        );
    }

    private Map<String, DataPullScopeBindingEpoch> openByScope(OperationCode operation) {
        Map<String, DataPullScopeBindingEpoch> result = new LinkedHashMap<>();
        List<DataPullScopeBindingEpoch> rows = Objects.requireNonNull(
                mapper.lockOpenBindings(operation), "open scope bindings"
        );
        for (DataPullScopeBindingEpoch row : rows) {
            DataPullScopeBindingEpoch binding = Objects.requireNonNull(row, "scope binding");
            binding.validate();
            if (binding.getOperationCode() != operation
                    || result.put(binding.getScopeKey(), binding) != null) {
                throw new IllegalStateException("DP_SCOPE_BINDING_OPEN_COHORT_DRIFT:" + operation);
            }
        }
        return result;
    }

    private void close(DataPullScopeBindingEpoch binding, LocalDateTime effectiveUntilUtc) {
        if (!effectiveUntilUtc.isAfter(binding.getEffectiveFromUtc())) {
            throw new IllegalStateException(
                    "DP_SCOPE_BINDING_CLOSE_WINDOW_INVALID:" + binding.getScopeKey()
            );
        }
        int changed = mapper.closeBinding(
                binding.getBindingId(), binding.getPayloadSha256(),
                effectiveUntilUtc, effectiveUntilUtc
        );
        if (changed != 1) {
            throw new IllegalStateException(
                    "DP_SCOPE_BINDING_CLOSE_CAS_LOST:" + binding.getScopeKey()
            );
        }
    }

    private static List<DataPullScopeBindingCandidate> requireCandidates(
            OperationCode operation,
            List<DataPullScopeBindingCandidate> values
    ) {
        List<DataPullScopeBindingCandidate> candidates = List.copyOf(
                Objects.requireNonNull(values, "currentBindings")
        );
        Map<String, Boolean> scopes = new HashMap<>();
        for (DataPullScopeBindingCandidate candidate : candidates) {
            DataPullScopeBindingCandidate value = Objects.requireNonNull(candidate, "binding");
            if (value.getOperationCode() != operation
                    || scopes.put(value.getScopeKey(), Boolean.TRUE) != null) {
                throw new IllegalArgumentException("scope binding cohort is duplicate or mixed");
            }
        }
        return candidates;
    }
}
