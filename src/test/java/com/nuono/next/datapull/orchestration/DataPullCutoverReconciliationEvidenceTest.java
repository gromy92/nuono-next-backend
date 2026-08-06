package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.schedule.DataPullScheduleAnchor;
import com.nuono.next.datapull.schedule.DataPullScheduleAnchorEvidence;
import com.nuono.next.datapull.schedule.DataPullScheduleAnchorManifest;
import com.nuono.next.datapull.schedule.DataPullScheduleCutover;
import com.nuono.next.datapull.schedule.DataPullScopeAdmission;
import com.nuono.next.datapull.schedule.InMemoryDataPullScopeAdmissionStore;
import com.nuono.next.infrastructure.mapper.DataPullScheduleAnchorMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DataPullCutoverReconciliationEvidenceTest {

    private static final OperationCode OPERATION = OperationCode.DP04;
    private static final String CUTOVER = "release-20260802";
    private static final LocalDateTime ACTIVATED_AT = LocalDateTime.of(2026, 8, 2, 2, 0);
    private static final DataPullScope SCOPE = scope("NOON-existing", 307L);

    @Test
    void verifiesCurrentSourceAdmissionAndSealedAnchorClosure() {
        DataPullScheduleAnchorMapper mapper = Mockito.mock(DataPullScheduleAnchorMapper.class);
        DataPullScopeAdmission admission = cutoverAdmission(SCOPE);
        DataPullScheduleAnchor anchor = cutoverAnchor(admission);
        stubCutover(mapper, List.of(anchor), List.of(anchor));

        assertTrue(evidence(
                mapper,
                List.of(SCOPE),
                new InMemoryDataPullScopeAdmissionStore(admission)
        ).verified());
    }

    @Test
    void activeSourceWithoutAdmissionFailsClosed() {
        DataPullScheduleAnchorMapper mapper = Mockito.mock(DataPullScheduleAnchorMapper.class);
        DataPullScopeAdmission admission = cutoverAdmission(SCOPE);
        DataPullScheduleAnchor anchor = cutoverAnchor(admission);
        stubCutover(mapper, List.of(anchor), List.of(anchor));

        assertFalse(evidence(
                mapper,
                List.of(SCOPE),
                new InMemoryDataPullScopeAdmissionStore()
        ).verified());
    }

    @Test
    void preCutoverAdmissionOmittedFromAnchorSealFailsClosed() {
        DataPullScheduleAnchorMapper mapper = Mockito.mock(DataPullScheduleAnchorMapper.class);
        DataPullScopeAdmission admission = cutoverAdmission(SCOPE);
        stubCutover(mapper, List.of(), List.of());

        assertFalse(evidence(
                mapper,
                List.of(SCOPE),
                new InMemoryDataPullScopeAdmissionStore(admission)
        ).verified());
    }

    @Test
    void exactPostCutoverAdmissionAndAnchorAreAccepted() {
        DataPullScheduleAnchorMapper mapper = Mockito.mock(DataPullScheduleAnchorMapper.class);
        DataPullScopeAdmission existingAdmission = cutoverAdmission(SCOPE);
        DataPullScheduleAnchor existingAnchor = cutoverAnchor(existingAdmission);
        DataPullScope postSource = scope("NOON-post", 308L);
        LocalDateTime eligibleAt = LocalDateTime.of(2026, 8, 5, 2, 0);
        LocalDateTime admittedAt = eligibleAt.plusMinutes(1);
        DataPullScopeAdmission postAdmission = DataPullScopeAdmission.postCutover(
                postSource, eligibleAt, CUTOVER, admittedAt
        );
        DataPullScheduleAnchor postAnchor = DataPullScheduleAnchor.postCutoverScope(
                OPERATION,
                postAdmission,
                DataPullScheduleAnchorEvidence.postCutoverReconcileAfter(eligibleAt),
                admittedAt
        );
        stubCutover(
                mapper,
                List.of(existingAnchor),
                List.of(existingAnchor, postAnchor)
        );

        assertTrue(evidence(
                mapper,
                List.of(SCOPE, postSource),
                new InMemoryDataPullScopeAdmissionStore(existingAdmission, postAdmission)
        ).verified());
    }

    @Test
    void historicalInactiveAdmissionAndAnchorMayRemainOutsideCurrentSourceSet() {
        DataPullScheduleAnchorMapper mapper = Mockito.mock(DataPullScheduleAnchorMapper.class);
        DataPullScopeAdmission admission = cutoverAdmission(SCOPE);
        DataPullScheduleAnchor anchor = cutoverAnchor(admission);
        stubCutover(mapper, List.of(anchor), List.of(anchor));

        assertTrue(evidence(
                mapper,
                List.of(),
                new InMemoryDataPullScopeAdmissionStore(admission)
        ).verified());
    }

    @Test
    void activeCutoverAnchorMustMatchEverySealedEvidenceField() {
        DataPullScheduleAnchorMapper mapper = Mockito.mock(DataPullScheduleAnchorMapper.class);
        DataPullScopeAdmission admission = cutoverAdmission(SCOPE);
        DataPullScheduleAnchor sealed = cutoverAnchor(admission);
        DataPullScheduleAnchor tampered = DataPullScheduleAnchor.cutover(
                OPERATION,
                admission,
                sealed.getReconcileAfterUtc(),
                sealed.getCreatedAtUtc(),
                "f".repeat(64)
        );
        stubCutover(mapper, List.of(sealed), List.of(tampered));

        assertFalse(evidence(
                mapper,
                List.of(SCOPE),
                new InMemoryDataPullScopeAdmissionStore(admission)
        ).verified());
    }

    private DataPullCutoverReconciliationEvidence evidence(
            DataPullScheduleAnchorMapper mapper,
            List<DataPullScope> scopes,
            InMemoryDataPullScopeAdmissionStore admissions
    ) {
        DataPullJob job = new TestDataPullJob(
                OPERATION,
                "noon-partner",
                scopes,
                ignored -> AdvanceResult.succeeded()
        );
        return new DataPullCutoverReconciliationEvidence(
                new DataPullJobRegistry(List.of(job)),
                mapper,
                admissions
        );
    }

    private void stubCutover(
            DataPullScheduleAnchorMapper mapper,
            List<DataPullScheduleAnchor> sealed,
            List<DataPullScheduleAnchor> active
    ) {
        String digest = DataPullScheduleAnchorManifest.sha256(
                OPERATION,
                CUTOVER,
                sealed
        );
        DataPullScheduleCutover cutover = DataPullScheduleCutover.active(
                OPERATION, CUTOVER, sealed.size(), digest, ACTIVATED_AT
        );
        when(mapper.selectActiveCutover(OPERATION)).thenReturn(cutover);
        when(mapper.listCutoverAnchors(OPERATION, CUTOVER)).thenReturn(sealed);
        when(mapper.listActiveAnchors(OPERATION, CUTOVER)).thenReturn(active);
    }

    private DataPullScheduleAnchor cutoverAnchor(DataPullScopeAdmission admission) {
        return DataPullScheduleAnchor.cutover(
                OPERATION,
                admission,
                LocalDateTime.of(2026, 8, 1, 15, 59, 59, 999_000_000),
                ACTIVATED_AT.minusMinutes(5),
                "e".repeat(64)
        );
    }

    private DataPullScopeAdmission cutoverAdmission(DataPullScope scope) {
        return DataPullScopeAdmission.cutoverExisting(
                scope,
                CUTOVER,
                ACTIVATED_AT.minusMinutes(10)
        );
    }

    private static DataPullScope scope(String stableKey, long owner) {
        return new DataPullScope(
                "NOON", owner, owner * 100L, "PRJ" + owner, null,
                "PRJ" + owner, "STR" + owner + "-NSA", "SA", stableKey
        );
    }
}
