package com.nuono.next.datapull.schedule;

import com.nuono.next.datapull.orchestration.DataPullScope;
import java.time.LocalDateTime;
import java.util.Objects;

/** Current source scope paired with its immutable persisted admission fact. */
public final class AdmittedDataPullScope {

    private final DataPullScope scope;
    private final DataPullScopeAdmission admission;

    public AdmittedDataPullScope(DataPullScope scope, DataPullScopeAdmission admission) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.admission = Objects.requireNonNull(admission, "admission");
        admission.validate();
        if (!admission.matchesSource(scope)) {
            throw new IllegalStateException(
                    "DP_SCOPE_ADMISSION_IDENTITY_DRIFT:" + scope.getStableScopeKey()
            );
        }
    }

    public DataPullScope getScope() { return scope; }
    public DataPullScopeAdmission.Kind getAdmissionKind() {
        return admission.getAdmissionKind();
    }
    public LocalDateTime getFirstEligibleAtUtc() {
        return admission.getFirstEligibleAtUtc();
    }
    public String getSourceBindingSha256() {
        return admission.getSourceBindingSha256();
    }
    public String getCutoverKey() { return admission.getCutoverKey(); }
    public LocalDateTime getAdmittedAtUtc() { return admission.getAdmittedAtUtc(); }
    public DataPullScopeAdmission getAdmission() { return admission; }
}
