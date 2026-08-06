package com.nuono.next.datapull.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.datapull.orchestration.DataPullScope;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.DataPullScheduleAnchorMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class MyBatisDataPullScheduleAnchorStoreTest {

    private static final OperationCode OPERATION = OperationCode.DP04;
    private static final String CUTOVER = "release-20260802";
    private static final LocalDateTime ACTIVATED_AT = LocalDateTime.of(2026, 8, 2, 2, 0);
    private static final DataPullScope EXISTING_SCOPE = scope("NOON-existing");

    @Test
    void opensOnlyACompleteManifestAndReturnsTheSealedCutoverAnchor() {
        DataPullScheduleAnchorMapper mapper = Mockito.mock(DataPullScheduleAnchorMapper.class);
        DataPullScopeAdmission admission = cutoverAdmission(EXISTING_SCOPE);
        DataPullScheduleAnchor anchor = cutoverAnchor(admission);
        stubActiveCutover(mapper, List.of(anchor), List.of(anchor));
        when(mapper.selectActiveAnchor(
                OPERATION, EXISTING_SCOPE.getStableScopeKey()
        )).thenReturn(anchor);

        LocalDateTime resolved = new MyBatisDataPullScheduleAnchorStore(mapper)
                .open(OPERATION)
                .reconcileAfterUtc(new AdmittedDataPullScope(EXISTING_SCOPE, admission));

        assertEquals(anchor.getReconcileAfterUtc(), resolved);
        verify(mapper, never()).insertPostCutoverAnchorIfActive(
                any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void incompleteCutoverManifestBlocksBeforeAnyScopeResolution() {
        DataPullScheduleAnchorMapper mapper = Mockito.mock(DataPullScheduleAnchorMapper.class);
        DataPullScheduleAnchor expected = cutoverAnchor(cutoverAdmission(EXISTING_SCOPE));
        stubActiveCutover(mapper, List.of(expected), List.of());

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new MyBatisDataPullScheduleAnchorStore(mapper).open(OPERATION)
        );

        assertEquals("DP_SCHEDULE_CUTOVER_MANIFEST_MISMATCH:DP04", failure.getMessage());
        verify(mapper, never()).insertPostCutoverAnchorIfActive(
                any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void missingCutoverExistingAnchorFailsClosed() {
        DataPullScheduleAnchorMapper mapper = Mockito.mock(DataPullScheduleAnchorMapper.class);
        DataPullScopeAdmission admission = cutoverAdmission(EXISTING_SCOPE);
        stubActiveCutover(mapper, List.of(), List.of());
        when(mapper.selectActiveAnchor(
                OPERATION, EXISTING_SCOPE.getStableScopeKey()
        )).thenReturn(null);

        assertThrows(
                IllegalStateException.class,
                () -> new MyBatisDataPullScheduleAnchorStore(mapper)
                        .open(OPERATION)
                        .reconcileAfterUtc(new AdmittedDataPullScope(
                                EXISTING_SCOPE,
                                admission
                        ))
        );
        verify(mapper, never()).insertPostCutoverAnchorIfActive(
                any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void exactPostCutoverAdmissionCreatesOneAnchorAndRestartReusesEligibleAt() {
        DataPullScheduleAnchorMapper mapper = Mockito.mock(DataPullScheduleAnchorMapper.class);
        stubActiveCutover(mapper, List.of(), List.of());
        DataPullScope source = scope("NOON-new");
        LocalDateTime eligibleAt = LocalDateTime.of(2026, 8, 5, 2, 0);
        LocalDateTime admittedAt = eligibleAt.plusMinutes(1);
        DataPullScopeAdmission admission = DataPullScopeAdmission.postCutover(
                source, eligibleAt, CUTOVER, admittedAt
        );
        LocalDateTime expectedStart = eligibleAt;
        DataPullScheduleAnchor persisted = DataPullScheduleAnchor.postCutoverScope(
                OPERATION, admission, expectedStart, admittedAt
        );
        when(mapper.selectActiveAnchor(OPERATION, source.getStableScopeKey()))
                .thenReturn(null, persisted, persisted);
        when(mapper.insertPostCutoverAnchorIfActive(
                eq(OPERATION), eq(source.getStableScopeKey()), eq(CUTOVER),
                eq(expectedStart), eq(persisted.getAnchorEvidenceSha256()),
                eq(eligibleAt), eq(admission.getSourceBindingSha256()), eq(admittedAt)
        )).thenReturn(1);

        MyBatisDataPullScheduleAnchorStore store = new MyBatisDataPullScheduleAnchorStore(mapper);
        AdmittedDataPullScope admitted = new AdmittedDataPullScope(source, admission);
        LocalDateTime first = store.open(OPERATION).reconcileAfterUtc(admitted);
        LocalDateTime restarted = store.open(OPERATION).reconcileAfterUtc(admitted);

        assertEquals(expectedStart, first);
        assertEquals(expectedStart, restarted);
        ArgumentCaptor<LocalDateTime> start = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper, times(1)).insertPostCutoverAnchorIfActive(
                eq(OPERATION), eq(source.getStableScopeKey()), eq(CUTOVER),
                start.capture(), eq(persisted.getAnchorEvidenceSha256()),
                eq(eligibleAt), eq(admission.getSourceBindingSha256()), eq(admittedAt)
        );
        assertEquals(expectedStart, start.getValue());
    }

    @Test
    void postCutoverEligibilityBeforeActivationCannotBeReclassifiedAsNew() {
        DataPullScheduleAnchorMapper mapper = Mockito.mock(DataPullScheduleAnchorMapper.class);
        stubActiveCutover(mapper, List.of(), List.of());
        DataPullScope source = scope("NOON-omitted");
        DataPullScopeAdmission admission = DataPullScopeAdmission.postCutover(
                source,
                ACTIVATED_AT.minusMinutes(1),
                CUTOVER,
                ACTIVATED_AT.plusMinutes(1)
        );

        assertThrows(
                IllegalStateException.class,
                () -> new MyBatisDataPullScheduleAnchorStore(mapper)
                        .open(OPERATION)
                        .reconcileAfterUtc(new AdmittedDataPullScope(source, admission))
        );
        verify(mapper, never()).insertPostCutoverAnchorIfActive(
                any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    private void stubActiveCutover(
            DataPullScheduleAnchorMapper mapper,
            List<DataPullScheduleAnchor> sealedAnchors,
            List<DataPullScheduleAnchor> persistedAnchors
    ) {
        String digest = DataPullScheduleAnchorManifest.sha256(
                OPERATION, CUTOVER, sealedAnchors
        );
        DataPullScheduleCutover cutover = DataPullScheduleCutover.active(
                OPERATION, CUTOVER, sealedAnchors.size(), digest, ACTIVATED_AT
        );
        when(mapper.selectActiveCutover(OPERATION)).thenReturn(cutover);
        when(mapper.listCutoverAnchors(OPERATION, CUTOVER)).thenReturn(persistedAnchors);
    }

    private DataPullScheduleAnchor cutoverAnchor(DataPullScopeAdmission admission) {
        return DataPullScheduleAnchor.cutover(
                OPERATION,
                admission,
                LocalDateTime.of(2026, 8, 1, 15, 59, 59, 999_000_000),
                ACTIVATED_AT,
                "d".repeat(64)
        );
    }

    private DataPullScopeAdmission cutoverAdmission(DataPullScope source) {
        return DataPullScopeAdmission.cutoverExisting(
                source,
                CUTOVER,
                ACTIVATED_AT.minusMinutes(5)
        );
    }

    private static DataPullScope scope(String stableScopeKey) {
        return new DataPullScope(
                "NOON", 307L, 108065L, "PRJ108065", null,
                "PRJ108065", "STR108065-NSA", "SA", stableScopeKey
        );
    }
}
