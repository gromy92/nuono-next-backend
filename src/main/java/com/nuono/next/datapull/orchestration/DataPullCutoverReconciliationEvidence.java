package com.nuono.next.datapull.orchestration;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.schedule.DataPullScheduleAnchor;
import com.nuono.next.datapull.schedule.DataPullScheduleAnchorEvidence;
import com.nuono.next.datapull.schedule.DataPullScheduleAnchorManifest;
import com.nuono.next.datapull.schedule.DataPullScheduleCutover;
import com.nuono.next.datapull.schedule.AdmittedDataPullScope;
import com.nuono.next.datapull.schedule.DataPullScopeAdmission;
import com.nuono.next.datapull.schedule.DataPullScopeAdmissionStore;
import com.nuono.next.infrastructure.mapper.DataPullScheduleAnchorMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Verifies the immutable legacy cutover cohort without admitting post-cutover scopes. */
public final class DataPullCutoverReconciliationEvidence
        implements DataPullRuntimeReleaseEvidence {

    private final DataPullJobRegistry jobs;
    private final DataPullScheduleAnchorMapper mapper;
    private final DataPullScopeAdmissionStore admissionStore;

    public DataPullCutoverReconciliationEvidence(
            DataPullJobRegistry jobs,
            DataPullScheduleAnchorMapper mapper,
            DataPullScopeAdmissionStore admissionStore
    ) {
        this.jobs = Objects.requireNonNull(jobs, "jobs");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.admissionStore = Objects.requireNonNull(admissionStore, "admissionStore");
    }

    @Override
    public DataPullRuntimeReleaseRequirement requirement() {
        return DataPullRuntimeReleaseRequirement.LEGACY_CUTOVER_RECONCILIATION;
    }

    @Override
    public boolean verified() {
        try {
            for (DataPullJob job : jobs.all()) {
                if (!verified(job)) {
                    return false;
                }
            }
            return true;
        } catch (RuntimeException invalidEvidence) {
            return false;
        }
    }

    private boolean verified(DataPullJob job) {
        DataPullJob operationJob = Objects.requireNonNull(job, "job");
        OperationCode operation = Objects.requireNonNull(
                operationJob.operationCode(),
                "job operation"
        );
        DataPullScheduleCutover cutover = mapper.selectActiveCutover(operation);
        if (cutover == null) {
            return false;
        }
        cutover.validateActive();

        List<DataPullScheduleAnchor> sealed = List.copyOf(Objects.requireNonNull(
                mapper.listCutoverAnchors(operation, cutover.getCutoverKey()),
                "sealed anchors"
        ));
        if (sealed.size() != cutover.getExpectedScopeCount()
                || !DataPullScheduleAnchorManifest.sha256(
                        operation,
                        cutover.getCutoverKey(),
                        sealed
                ).equals(cutover.getAnchorManifestSha256())) {
            return false;
        }

        Map<String, DataPullScheduleAnchor> active = activeAnchors(cutover, sealed);
        List<DataPullScope> sources = List.copyOf(Objects.requireNonNull(
                operationJob.listScopes(),
                "active source scopes"
        ));
        List<AdmittedDataPullScope> admitted = admissionStore.requireActiveAdmissions(
                operation,
                sources
        );
        requireCurrentClosure(cutover, admitted, active);
        return true;
    }

    private void requireCurrentClosure(
            DataPullScheduleCutover cutover,
            List<AdmittedDataPullScope> admitted,
            Map<String, DataPullScheduleAnchor> activeByScope
    ) {
        for (AdmittedDataPullScope current : admitted) {
            AdmittedDataPullScope scope = Objects.requireNonNull(current, "admitted scope");
            DataPullScopeAdmission admission = scope.getAdmission();
            DataPullScheduleAnchor anchor = activeByScope.get(admission.getScopeKey());
            if (anchor == null
                    || !cutover.getCutoverKey().equals(admission.getCutoverKey())
                    || admission.getAdmissionKind() != anchor.getAdmissionKind()
                    || !Objects.equals(
                            admission.getFirstEligibleAtUtc(),
                            anchor.getFirstEligibleAtUtc()
                    )
                    || !admission.getSourceBindingSha256().equals(
                            anchor.getSourceBindingSha256()
                    )) {
                throw new IllegalStateException("active source admission/anchor closure mismatch");
            }
            if (admission.getAdmissionKind()
                    == DataPullScopeAdmission.Kind.CUTOVER_EXISTING) {
                if (anchor.getAnchorKind()
                        != DataPullScheduleAnchor.Kind.CUTOVER_RECONCILED) {
                    throw new IllegalStateException("pre-cutover source was omitted from seal");
                }
                continue;
            }
            if (admission.getFirstEligibleAtUtc().isBefore(cutover.getActivatedAtUtc())
                    || anchor.getAnchorKind()
                    != DataPullScheduleAnchor.Kind.POST_CUTOVER_SCOPE
                    || !DataPullScheduleAnchorEvidence.postCutoverReconcileAfter(
                            admission.getFirstEligibleAtUtc()
                    ).equals(anchor.getReconcileAfterUtc())
                    || !admission.getAdmittedAtUtc().equals(anchor.getCreatedAtUtc())) {
                throw new IllegalStateException("post-cutover source anchor is not exact");
            }
        }
    }

    private Map<String, DataPullScheduleAnchor> activeAnchors(
            DataPullScheduleCutover cutover,
            List<DataPullScheduleAnchor> sealed
    ) {
        Map<String, DataPullScheduleAnchor> sealedByScope = new HashMap<>();
        for (DataPullScheduleAnchor anchor : sealed) {
            if (sealedByScope.put(anchor.getScopeKey(), anchor) != null) {
                throw new IllegalStateException("duplicate sealed schedule scope");
            }
        }

        List<DataPullScheduleAnchor> active = List.copyOf(Objects.requireNonNull(
                mapper.listActiveAnchors(
                        cutover.getOperationCode(),
                        cutover.getCutoverKey()
                ),
                "active anchors"
        ));
        Map<String, DataPullScheduleAnchor> activeByScope = new HashMap<>();
        for (DataPullScheduleAnchor anchor : active) {
            DataPullScheduleAnchor value = Objects.requireNonNull(anchor, "active anchor");
            value.validate();
            if (value.getOperationCode() != cutover.getOperationCode()
                    || !cutover.getCutoverKey().equals(value.getCutoverKey())
                    || activeByScope.put(value.getScopeKey(), value) != null) {
                throw new IllegalStateException("active schedule anchor cohort mismatch");
            }
            if (value.getAnchorKind() == DataPullScheduleAnchor.Kind.CUTOVER_RECONCILED) {
                DataPullScheduleAnchor expected = sealedByScope.get(value.getScopeKey());
                if (expected == null || !sameAnchorEvidence(expected, value)) {
                    throw new IllegalStateException("sealed schedule anchor changed");
                }
            } else if (value.getFirstEligibleAtUtc().isBefore(
                    cutover.getActivatedAtUtc()
            )) {
                throw new IllegalStateException("post-cutover scope eligibility predates activation");
            }
        }
        if (!activeByScope.keySet().containsAll(sealedByScope.keySet())) {
            throw new IllegalStateException("sealed schedule anchor is not active");
        }
        return activeByScope;
    }

    private static boolean sameAnchorEvidence(
            DataPullScheduleAnchor expected,
            DataPullScheduleAnchor actual
    ) {
        return expected.getOperationCode() == actual.getOperationCode()
                && expected.getScopeKey().equals(actual.getScopeKey())
                && expected.getCutoverKey().equals(actual.getCutoverKey())
                && expected.getAnchorKind() == actual.getAnchorKind()
                && expected.getReconcileAfterUtc().equals(actual.getReconcileAfterUtc())
                && expected.getAdmissionKind() == actual.getAdmissionKind()
                && Objects.equals(
                        expected.getFirstEligibleAtUtc(),
                        actual.getFirstEligibleAtUtc()
                )
                && expected.getSourceBindingSha256().equals(
                        actual.getSourceBindingSha256()
                )
                && expected.getAnchorEvidenceSha256().equals(
                        actual.getAnchorEvidenceSha256()
                );
    }
}
