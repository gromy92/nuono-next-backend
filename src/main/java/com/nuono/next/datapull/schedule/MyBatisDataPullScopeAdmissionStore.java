package com.nuono.next.datapull.schedule;

import com.nuono.next.datapull.orchestration.DataPullScope;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.DataPullScopeAdmissionMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Persistent Adapter for immutable cutover and first-observed post-cutover admissions. */
public final class MyBatisDataPullScopeAdmissionStore implements DataPullScopeAdmissionStore {

    private final DataPullScopeAdmissionMapper mapper;

    public MyBatisDataPullScopeAdmissionStore(DataPullScopeAdmissionMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public List<AdmittedDataPullScope> admitCurrent(
            OperationCode operationCode,
            List<DataPullScope> activeSourceScopes
    ) {
        OperationCode operation = Objects.requireNonNull(operationCode, "operationCode");
        List<DataPullScope> sources = requireSources(operation, activeSourceScopes);
        DataPullScheduleCutover cutover = mapper.lockActiveCutover(operation);
        if (cutover == null) {
            throw new IllegalStateException("DP_SCHEDULE_CUTOVER_NOT_ACTIVE:" + operation);
        }
        cutover.validateActive();
        if (cutover.getOperationCode() != operation) {
            throw new IllegalStateException("active cutover operation drift");
        }
        if (sources.isEmpty()) {
            return List.of();
        }
        LocalDateTime observedAtUtc = DataPullScopeAdmission.requireMillisecond(
                mapper.selectDatabaseNowUtc(), "databaseNowUtc"
        );
        if (observedAtUtc.isBefore(cutover.getActivatedAtUtc())) {
            throw new IllegalStateException("scope observation predates active cutover");
        }
        List<String> keys = scopeKeys(sources);
        Map<String, DataPullScopeAdmission> existing = byScope(
                mapper.lockByScopeKeys(keys)
        );
        for (DataPullScope source : sources) {
            if (existing.containsKey(source.getStableScopeKey())) {
                continue;
            }
            DataPullScopeAdmission admission = DataPullScopeAdmission.postCutover(
                    source, observedAtUtc, cutover.getCutoverKey(), observedAtUtc
            );
            int changed = mapper.insertPostCutoverAdmission(operation, admission);
            if (changed < 0 || changed > 1) {
                throw new IllegalStateException("scope admission insert changed invalid rows");
            }
        }
        return resolve(
                operation, sources, mapper.lockByScopeKeys(keys), cutover
        );
    }

    @Override
    public List<AdmittedDataPullScope> requireActiveAdmissions(
            OperationCode operationCode,
            List<DataPullScope> activeSourceScopes
    ) {
        OperationCode operation = Objects.requireNonNull(operationCode, "operationCode");
        List<DataPullScope> sources = requireSources(operation, activeSourceScopes);
        if (sources.isEmpty()) {
            return List.of();
        }
        return resolve(
                operation, sources, mapper.listByScopeKeys(scopeKeys(sources)), null
        );
    }

    private List<AdmittedDataPullScope> resolve(
            OperationCode operation,
            List<DataPullScope> sources,
            List<DataPullScopeAdmission> rows,
            DataPullScheduleCutover activeCutover
    ) {
        Map<String, DataPullScopeAdmission> byScope = byScope(rows);
        List<AdmittedDataPullScope> result = new ArrayList<>(sources.size());
        for (DataPullScope source : sources) {
            DataPullScopeAdmission admission = byScope.get(source.getStableScopeKey());
            if (admission == null) {
                throw new IllegalStateException(
                        "DP_ACTIVE_SCOPE_ADMISSION_MISSING:" + operation + ":"
                                + source.getStableScopeKey()
                );
            }
            if (activeCutover != null) {
                requireActiveCutover(admission, activeCutover);
            }
            result.add(new AdmittedDataPullScope(source, admission));
        }
        return List.copyOf(result);
    }

    private Map<String, DataPullScopeAdmission> byScope(
            List<DataPullScopeAdmission> values
    ) {
        List<DataPullScopeAdmission> rows = List.copyOf(Objects.requireNonNull(
                values, "scope admissions"
        ));
        Map<String, DataPullScopeAdmission> byScope = new HashMap<>();
        for (DataPullScopeAdmission admission : rows) {
            DataPullScopeAdmission value = Objects.requireNonNull(admission, "scope admission");
            value.validate();
            if (byScope.put(value.getScopeKey(), value) != null) {
                throw new IllegalStateException(
                        "DP_SCOPE_ADMISSION_DUPLICATE:" + value.getScopeKey()
                );
            }
        }
        return byScope;
    }

    private static void requireActiveCutover(
            DataPullScopeAdmission admission,
            DataPullScheduleCutover cutover
    ) {
        if (!cutover.getCutoverKey().equals(admission.getCutoverKey())) {
            throw new IllegalStateException("scope admission cutover identity drift");
        }
        if (admission.getAdmissionKind() == DataPullScopeAdmission.Kind.POST_CUTOVER
                && admission.getFirstEligibleAtUtc().isBefore(cutover.getActivatedAtUtc())) {
            throw new IllegalStateException("post-cutover scope eligibility predates activation");
        }
    }

    private static List<DataPullScope> requireSources(
            OperationCode operation,
            List<DataPullScope> activeSourceScopes
    ) {
        List<DataPullScope> sources = List.copyOf(Objects.requireNonNull(
                activeSourceScopes, "activeSourceScopes"
        ));
        Set<String> uniqueKeys = new HashSet<>();
        for (DataPullScope scope : sources) {
            DataPullScope source = Objects.requireNonNull(scope, "active source scope");
            if (!uniqueKeys.add(source.getStableScopeKey())) {
                throw new IllegalStateException(
                        "DP_ACTIVE_SCOPE_SOURCE_DUPLICATE:" + operation + ":"
                                + source.getStableScopeKey()
                );
            }
        }
        return sources;
    }

    private static List<String> scopeKeys(List<DataPullScope> sources) {
        List<String> keys = new ArrayList<>(sources.size());
        for (DataPullScope source : sources) {
            keys.add(source.getStableScopeKey());
        }
        return keys;
    }
}
