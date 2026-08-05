package com.nuono.next.datapull.schedule;

import com.nuono.next.datapull.orchestration.DataPullScope;
import com.nuono.next.datapull.runtime.OperationCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Test Adapter with source-to-admission closure and no lazy admission fallback. */
public final class InMemoryDataPullScopeAdmissionStore
        implements DataPullScopeAdmissionStore {

    private final Map<String, DataPullScopeAdmission> admissions = new HashMap<>();

    public InMemoryDataPullScopeAdmissionStore(DataPullScopeAdmission... values) {
        for (DataPullScopeAdmission admission : values) {
            DataPullScopeAdmission item = Objects.requireNonNull(admission, "admission");
            item.validate();
            if (admissions.putIfAbsent(item.getScopeKey(), item) != null) {
                throw new IllegalStateException("duplicate test admission");
            }
        }
    }

    @Override
    public List<AdmittedDataPullScope> admitCurrent(
            OperationCode operationCode,
            List<DataPullScope> activeSourceScopes
    ) {
        return requireActiveAdmissions(operationCode, activeSourceScopes);
    }

    @Override
    public List<AdmittedDataPullScope> requireActiveAdmissions(
            OperationCode operationCode,
            List<DataPullScope> activeSourceScopes
    ) {
        OperationCode operation = Objects.requireNonNull(operationCode, "operationCode");
        List<DataPullScope> sources = List.copyOf(Objects.requireNonNull(
                activeSourceScopes,
                "activeSourceScopes"
        ));
        Set<String> seen = new HashSet<>();
        List<AdmittedDataPullScope> result = new ArrayList<>(sources.size());
        for (DataPullScope source : sources) {
            DataPullScope value = Objects.requireNonNull(source, "source");
            if (!seen.add(value.getStableScopeKey())) {
                throw new IllegalStateException("duplicate source for " + operation);
            }
            DataPullScopeAdmission admission = admissions.get(value.getStableScopeKey());
            if (admission == null) {
                throw new IllegalStateException("missing admission for " + operation);
            }
            result.add(new AdmittedDataPullScope(value, admission));
        }
        return List.copyOf(result);
    }
}
