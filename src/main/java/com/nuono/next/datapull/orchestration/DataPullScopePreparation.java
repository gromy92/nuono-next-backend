package com.nuono.next.datapull.orchestration;

import com.nuono.next.datapull.schedule.AdmittedDataPullScope;
import java.util.List;
import java.util.Objects;

/** One immutable source cohort plus a deferred post-admission preparation action. */
public final class DataPullScopePreparation {
    @FunctionalInterface
    public interface AfterAdmission {
        void complete(List<AdmittedDataPullScope> admittedScopes);
    }

    private final List<DataPullScope> scopes;
    private final AfterAdmission afterAdmission;
    private boolean completed;

    private DataPullScopePreparation(
            List<DataPullScope> scopes,
            AfterAdmission afterAdmission
    ) {
        this.scopes = List.copyOf(Objects.requireNonNull(scopes, "scopes"));
        this.afterAdmission = Objects.requireNonNull(afterAdmission, "afterAdmission");
    }

    public static DataPullScopePreparation readOnly(List<DataPullScope> scopes) {
        return deferred(scopes, ignored -> { });
    }

    public static DataPullScopePreparation deferred(
            List<DataPullScope> scopes,
            AfterAdmission afterAdmission
    ) {
        return new DataPullScopePreparation(scopes, afterAdmission);
    }

    public List<DataPullScope> getScopes() {
        return scopes;
    }

    public void completeAfterAdmission(List<AdmittedDataPullScope> admittedScopes) {
        if (completed) {
            throw new IllegalStateException("scope preparation was already completed");
        }
        List<AdmittedDataPullScope> admitted = List.copyOf(
                Objects.requireNonNull(admittedScopes, "admittedScopes")
        );
        if (admitted.size() != scopes.size()) {
            throw new IllegalStateException("scope preparation admission cohort size drift");
        }
        for (int index = 0; index < scopes.size(); index++) {
            DataPullScope source = scopes.get(index);
            AdmittedDataPullScope admission = Objects.requireNonNull(
                    admitted.get(index), "admittedScope"
            );
            if (!source.getStableScopeKey().equals(
                    admission.getScope().getStableScopeKey()
            )) {
                throw new IllegalStateException("scope preparation admission order drift");
            }
        }
        afterAdmission.complete(admitted);
        completed = true;
    }
}
