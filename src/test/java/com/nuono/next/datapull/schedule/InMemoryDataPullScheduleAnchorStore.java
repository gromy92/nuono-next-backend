package com.nuono.next.datapull.schedule;

import com.nuono.next.datapull.runtime.OperationCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Test-only Adapter with the same sealed-cohort and immutable-anchor rules as MyBatis. */
public final class InMemoryDataPullScheduleAnchorStore implements DataPullScheduleAnchorStore {

    private final Map<OperationCode, DataPullScheduleCutover> cutovers =
            new EnumMap<>(OperationCode.class);
    private final Map<String, DataPullScheduleAnchor> anchors = new HashMap<>();

    public synchronized void activate(
            OperationCode operationCode,
            String cutoverKey,
            List<DataPullScheduleAnchor> cutoverAnchors,
            LocalDateTime activatedAtUtc
    ) {
        OperationCode operation = Objects.requireNonNull(operationCode, "operationCode");
        List<DataPullScheduleAnchor> proposed = List.copyOf(
                Objects.requireNonNull(cutoverAnchors, "cutoverAnchors")
        );
        String manifest = DataPullScheduleAnchorManifest.sha256(
                operation, cutoverKey, proposed
        );
        DataPullScheduleCutover cutover = DataPullScheduleCutover.active(
                operation, cutoverKey, proposed.size(), manifest, activatedAtUtc
        );
        if (cutovers.containsKey(operation)) {
            throw new IllegalStateException("schedule cutover is already active for " + operation);
        }
        Map<String, DataPullScheduleAnchor> additions = new HashMap<>();
        for (DataPullScheduleAnchor anchor : proposed) {
            String key = key(operation, anchor.getScopeKey());
            if (anchors.containsKey(key) || additions.putIfAbsent(key, copy(anchor)) != null) {
                throw new IllegalStateException("duplicate schedule anchor scope");
            }
        }
        anchors.putAll(additions);
        cutovers.put(operation, cutover);
    }

    @Override
    public synchronized Cohort open(OperationCode operationCode) {
        OperationCode operation = Objects.requireNonNull(operationCode, "operationCode");
        DataPullScheduleCutover cutover = cutovers.get(operation);
        if (cutover == null) {
            throw new IllegalStateException("DP_SCHEDULE_CUTOVER_NOT_ACTIVE:" + operation);
        }
        requireValidManifest(cutover);
        return admittedScope -> resolve(
                operation, cutover.getCutoverKey(), admittedScope
        );
    }

    private synchronized LocalDateTime resolve(
            OperationCode operation,
            String cutoverKey,
            AdmittedDataPullScope admittedScope
    ) {
        AdmittedDataPullScope admitted = Objects.requireNonNull(
                admittedScope,
                "admittedScope"
        );
        DataPullScopeAdmission admission = admitted.getAdmission();
        admission.validate();
        String scopeKey = admission.getScopeKey();
        DataPullScheduleCutover cutover = cutovers.get(operation);
        if (cutover == null || !cutoverKey.equals(cutover.getCutoverKey())) {
            throw new IllegalStateException("DP schedule cutover changed during reconciliation");
        }
        if (!cutoverKey.equals(admission.getCutoverKey())) {
            throw new IllegalStateException("scope admission belongs to another cutover cohort");
        }
        DataPullScheduleAnchor existing = anchors.get(key(operation, scopeKey));
        if (existing == null) {
            if (admission.getAdmissionKind()
                    == DataPullScopeAdmission.Kind.CUTOVER_EXISTING) {
                throw new IllegalStateException("cutover-existing scope anchor was omitted");
            }
            if (admission.getFirstEligibleAtUtc().isBefore(cutover.getActivatedAtUtc())) {
                throw new IllegalStateException("scope eligibility predates active schedule cutover");
            }
            existing = DataPullScheduleAnchor.postCutoverScope(
                    operation,
                    admission,
                    DataPullScheduleAnchorEvidence.postCutoverReconcileAfter(
                            admission.getFirstEligibleAtUtc()
                    ),
                    admission.getAdmittedAtUtc()
            );
            anchors.put(key(operation, scopeKey), existing);
        }
        existing.validate();
        if (!cutoverKey.equals(existing.getCutoverKey())) {
            throw new IllegalStateException("scope anchor belongs to another cutover cohort");
        }
        if (existing.getAdmissionKind() != admission.getAdmissionKind()
                || !Objects.equals(
                        existing.getFirstEligibleAtUtc(),
                        admission.getFirstEligibleAtUtc()
                )
                || !existing.getSourceBindingSha256().equals(
                        admission.getSourceBindingSha256()
                )) {
            throw new IllegalStateException("scope anchor admission evidence drift");
        }
        return existing.getReconcileAfterUtc();
    }

    private void requireValidManifest(DataPullScheduleCutover cutover) {
        cutover.validateActive();
        List<DataPullScheduleAnchor> cohort = new ArrayList<>();
        for (DataPullScheduleAnchor anchor : anchors.values()) {
            if (anchor.getOperationCode() == cutover.getOperationCode()
                    && anchor.getAnchorKind() == DataPullScheduleAnchor.Kind.CUTOVER_RECONCILED
                    && cutover.getCutoverKey().equals(anchor.getCutoverKey())) {
                cohort.add(copy(anchor));
            }
        }
        String actual = DataPullScheduleAnchorManifest.sha256(
                cutover.getOperationCode(), cutover.getCutoverKey(), cohort
        );
        if (cohort.size() != cutover.getExpectedScopeCount()
                || !actual.equals(cutover.getAnchorManifestSha256())) {
            throw new IllegalStateException(
                    "DP_SCHEDULE_CUTOVER_MANIFEST_MISMATCH:" + cutover.getOperationCode()
            );
        }
    }

    private static String key(OperationCode operationCode, String scopeKey) {
        return operationCode.name() + "\0" + scopeKey;
    }

    private static DataPullScheduleAnchor copy(DataPullScheduleAnchor source) {
        DataPullScheduleAnchor result = new DataPullScheduleAnchor();
        result.setOperationCode(source.getOperationCode());
        result.setScopeKey(source.getScopeKey());
        result.setCutoverKey(source.getCutoverKey());
        result.setAnchorKind(source.getAnchorKind());
        result.setReconcileAfterUtc(source.getReconcileAfterUtc());
        result.setCreatedAtUtc(source.getCreatedAtUtc());
        result.setAdmissionKind(source.getAdmissionKind());
        result.setFirstEligibleAtUtc(source.getFirstEligibleAtUtc());
        result.setSourceBindingSha256(source.getSourceBindingSha256());
        result.setAnchorEvidenceSha256(source.getAnchorEvidenceSha256());
        result.validate();
        return result;
    }
}
