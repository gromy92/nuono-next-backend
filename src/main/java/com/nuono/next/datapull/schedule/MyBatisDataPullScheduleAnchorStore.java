package com.nuono.next.datapull.schedule;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.DataPullScheduleAnchorMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** Production Adapter that admits new scopes only behind a verified persistent cutover seal. */
public final class MyBatisDataPullScheduleAnchorStore implements DataPullScheduleAnchorStore {

    private final DataPullScheduleAnchorMapper mapper;

    public MyBatisDataPullScheduleAnchorStore(DataPullScheduleAnchorMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public Cohort open(OperationCode operationCode) {
        OperationCode operation = Objects.requireNonNull(operationCode, "operationCode");
        DataPullScheduleCutover cutover = mapper.selectActiveCutover(operation);
        if (cutover == null) {
            throw new IllegalStateException("DP_SCHEDULE_CUTOVER_NOT_ACTIVE:" + operation);
        }
        cutover.validateActive();
        List<DataPullScheduleAnchor> anchors = List.copyOf(Objects.requireNonNull(
                mapper.listCutoverAnchors(operation, cutover.getCutoverKey()),
                "cutover anchors"
        ));
        requireManifest(cutover, anchors);
        return admittedScope -> resolve(cutover, admittedScope);
    }

    private LocalDateTime resolve(
            DataPullScheduleCutover cutover,
            AdmittedDataPullScope admittedScope
    ) {
        AdmittedDataPullScope admitted = Objects.requireNonNull(
                admittedScope,
                "admittedScope"
        );
        DataPullScopeAdmission admission = admitted.getAdmission();
        admission.validate();
        String scope = admission.getScopeKey();
        if (!cutover.getCutoverKey().equals(admission.getCutoverKey())) {
            throw new IllegalStateException("scope admission belongs to another cutover cohort");
        }
        if (admission.getAdmissionKind() == DataPullScopeAdmission.Kind.POST_CUTOVER
                && admission.getFirstEligibleAtUtc().isBefore(cutover.getActivatedAtUtc())) {
            throw new IllegalStateException("post-cutover scope eligibility predates activation");
        }
        DataPullScheduleAnchor anchor = mapper.selectActiveAnchor(
                cutover.getOperationCode(), scope
        );
        if (anchor == null) {
            if (admission.getAdmissionKind()
                    == DataPullScopeAdmission.Kind.CUTOVER_EXISTING) {
                throw new IllegalStateException(
                        "DP_SCHEDULE_CUTOVER_SCOPE_ANCHOR_OMITTED:" + scope
                );
            }
            LocalDateTime reconcileAfterUtc = DataPullScheduleAnchorEvidence
                    .postCutoverReconcileAfter(
                            admission.getFirstEligibleAtUtc()
                    );
            String evidence = DataPullScheduleAnchorEvidence.postCutoverSha256(
                    cutover.getOperationCode(),
                    admission,
                    reconcileAfterUtc
            );
            int changed = mapper.insertPostCutoverAnchorIfActive(
                    cutover.getOperationCode(),
                    scope,
                    cutover.getCutoverKey(),
                    reconcileAfterUtc,
                    evidence,
                    admission.getFirstEligibleAtUtc(),
                    admission.getSourceBindingSha256(),
                    admission.getAdmittedAtUtc()
            );
            if (changed < 0 || changed > 1) {
                throw new IllegalStateException("new-scope anchor insert affected invalid rows: " + changed);
            }
            anchor = mapper.selectActiveAnchor(cutover.getOperationCode(), scope);
        }
        if (anchor == null) {
            throw new IllegalStateException("DP_SCHEDULE_SCOPE_ANCHOR_UNAVAILABLE:" + scope);
        }
        anchor.validate();
        if (anchor.getOperationCode() != cutover.getOperationCode()
                || !cutover.getCutoverKey().equals(anchor.getCutoverKey())) {
            throw new IllegalStateException("scope anchor belongs to another cutover cohort");
        }
        requireAdmissionEvidence(cutover, admitted, anchor);
        return anchor.getReconcileAfterUtc();
    }

    private static void requireAdmissionEvidence(
            DataPullScheduleCutover cutover,
            AdmittedDataPullScope admitted,
            DataPullScheduleAnchor anchor
    ) {
        DataPullScopeAdmission admission = admitted.getAdmission();
        if (!admission.getScopeKey().equals(anchor.getScopeKey())
                || admission.getAdmissionKind() != anchor.getAdmissionKind()
                || !Objects.equals(
                        admission.getFirstEligibleAtUtc(),
                        anchor.getFirstEligibleAtUtc()
                )
                || !admission.getSourceBindingSha256().equals(
                        anchor.getSourceBindingSha256()
                )) {
            throw new IllegalStateException("scope anchor admission evidence drift");
        }
        if (admission.getAdmissionKind() == DataPullScopeAdmission.Kind.CUTOVER_EXISTING) {
            if (anchor.getAnchorKind() != DataPullScheduleAnchor.Kind.CUTOVER_RECONCILED) {
                throw new IllegalStateException("cutover-existing scope lacks sealed anchor");
            }
            return;
        }
        LocalDateTime expectedStart = DataPullScheduleAnchorEvidence
                .postCutoverReconcileAfter(
                admission.getFirstEligibleAtUtc()
        );
        if (anchor.getAnchorKind() != DataPullScheduleAnchor.Kind.POST_CUTOVER_SCOPE
                || !expectedStart.equals(anchor.getReconcileAfterUtc())
                || !admission.getAdmittedAtUtc().equals(anchor.getCreatedAtUtc())
                || admission.getFirstEligibleAtUtc().isBefore(cutover.getActivatedAtUtc())) {
            throw new IllegalStateException("post-cutover scope anchor is not exact");
        }
    }

    private static void requireManifest(
            DataPullScheduleCutover cutover,
            List<DataPullScheduleAnchor> anchors
    ) {
        String actual = DataPullScheduleAnchorManifest.sha256(
                cutover.getOperationCode(), cutover.getCutoverKey(), anchors
        );
        if (anchors.size() != cutover.getExpectedScopeCount()
                || !actual.equals(cutover.getAnchorManifestSha256())) {
            throw new IllegalStateException(
                    "DP_SCHEDULE_CUTOVER_MANIFEST_MISMATCH:" + cutover.getOperationCode()
            );
        }
    }

}
