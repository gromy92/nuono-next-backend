package com.nuono.next.datapull.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.datapull.orchestration.DataPullScope;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.DataPullScopeAdmissionMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MyBatisDataPullScopeAdmissionStoreTest {

    private static final String CUTOVER = "release-20260802";
    private static final LocalDateTime ADMITTED_AT = LocalDateTime.of(2026, 8, 2, 1, 55);
    private static final DataPullScope SOURCE = scope("NOON-source", "PRJ307");

    @Test
    void firstObservationCreatesOnePostCutoverAdmissionAtDatabaseNow() {
        LocalDateTime observed = LocalDateTime.of(2026, 8, 3, 7, 12, 0, 123_000_000);
        DataPullScopeAdmission inserted = DataPullScopeAdmission.postCutover(
                SOURCE, observed, CUTOVER, observed
        );
        DataPullScopeAdmissionMapper mapper = Mockito.mock(DataPullScopeAdmissionMapper.class);
        when(mapper.lockActiveCutover(OperationCode.DP04)).thenReturn(cutover());
        when(mapper.selectDatabaseNowUtc()).thenReturn(observed);
        when(mapper.lockByScopeKeys(List.of(SOURCE.getStableScopeKey())))
                .thenReturn(List.of(), List.of(inserted));
        when(mapper.insertPostCutoverAdmission(
                org.mockito.ArgumentMatchers.eq(OperationCode.DP04), any()
        )).thenReturn(1);

        List<AdmittedDataPullScope> result = new MyBatisDataPullScopeAdmissionStore(mapper)
                .admitCurrent(OperationCode.DP04, List.of(SOURCE));

        assertEquals(DataPullScopeAdmission.Kind.POST_CUTOVER, result.get(0).getAdmissionKind());
        assertEquals(observed, result.get(0).getFirstEligibleAtUtc());
        assertEquals(observed, result.get(0).getAdmittedAtUtc());
        verify(mapper).insertPostCutoverAdmission(
                org.mockito.ArgumentMatchers.eq(OperationCode.DP04),
                org.mockito.ArgumentMatchers.argThat(value ->
                        observed.equals(value.getFirstEligibleAtUtc())
                                && observed.equals(value.getAdmittedAtUtc())
                )
        );
    }

    @Test
    void existingAdmissionIsReusedWithoutRewritingItsEligibility() {
        DataPullScopeAdmission existing = DataPullScopeAdmission.postCutover(
                SOURCE, ADMITTED_AT, CUTOVER, ADMITTED_AT
        );
        DataPullScopeAdmissionMapper mapper = Mockito.mock(DataPullScopeAdmissionMapper.class);
        when(mapper.lockActiveCutover(OperationCode.DP04)).thenReturn(cutover());
        when(mapper.selectDatabaseNowUtc()).thenReturn(ADMITTED_AT.plusDays(1));
        when(mapper.lockByScopeKeys(List.of(SOURCE.getStableScopeKey())))
                .thenReturn(List.of(existing), List.of(existing));

        List<AdmittedDataPullScope> result = new MyBatisDataPullScopeAdmissionStore(mapper)
                .admitCurrent(OperationCode.DP04, List.of(SOURCE));

        assertEquals(ADMITTED_AT, result.get(0).getFirstEligibleAtUtc());
        verify(mapper, never()).insertPostCutoverAdmission(any(), any());
    }

    @Test
    void activeCutoverIdentityDriftFailsClosed() {
        DataPullScopeAdmission wrongCutover = DataPullScopeAdmission.postCutover(
                SOURCE, ADMITTED_AT, "another-cutover", ADMITTED_AT
        );
        DataPullScopeAdmissionMapper mapper = Mockito.mock(DataPullScopeAdmissionMapper.class);
        when(mapper.lockActiveCutover(OperationCode.DP04)).thenReturn(cutover());
        when(mapper.selectDatabaseNowUtc()).thenReturn(ADMITTED_AT.plusHours(1));
        when(mapper.lockByScopeKeys(List.of(SOURCE.getStableScopeKey())))
                .thenReturn(List.of(wrongCutover), List.of(wrongCutover));

        assertThrows(
                IllegalStateException.class,
                () -> new MyBatisDataPullScopeAdmissionStore(mapper)
                        .admitCurrent(OperationCode.DP04, List.of(SOURCE))
        );
    }

    @Test
    void returnsCurrentSourcesInSourceOrderFromGlobalAdmissions() {
        DataPullScope other = scope("NOON-other", "PRJ308");
        DataPullScopeAdmission first = admission(SOURCE);
        DataPullScopeAdmission second = admission(other);
        DataPullScopeAdmissionMapper mapper = Mockito.mock(DataPullScopeAdmissionMapper.class);
        when(mapper.listByScopeKeys(List.of(
                SOURCE.getStableScopeKey(), other.getStableScopeKey()
        ))).thenReturn(List.of(second, first));

        List<AdmittedDataPullScope> resolved = new MyBatisDataPullScopeAdmissionStore(mapper)
                .requireActiveAdmissions(OperationCode.DP04, List.of(SOURCE, other));

        assertEquals(
                List.of(SOURCE.getStableScopeKey(), other.getStableScopeKey()),
                resolved.stream()
                        .map(item -> item.getScope().getStableScopeKey())
                        .collect(java.util.stream.Collectors.toList())
        );
    }

    @Test
    void currentSourceWithoutAdmissionFailsClosed() {
        DataPullScopeAdmissionMapper mapper = Mockito.mock(DataPullScopeAdmissionMapper.class);
        when(mapper.listByScopeKeys(List.of(SOURCE.getStableScopeKey())))
                .thenReturn(List.of());

        assertThrows(
                IllegalStateException.class,
                () -> new MyBatisDataPullScopeAdmissionStore(mapper)
                        .requireActiveAdmissions(OperationCode.DP04, List.of(SOURCE))
        );
    }

    @Test
    void canonicalIdentityDriftFailsEvenWhenStableScopeKeyMatches() {
        DataPullScope drifted = scope(SOURCE.getStableScopeKey(), "PRJ-DIFFERENT");
        DataPullScopeAdmissionMapper mapper = Mockito.mock(DataPullScopeAdmissionMapper.class);
        when(mapper.listByScopeKeys(List.of(SOURCE.getStableScopeKey())))
                .thenReturn(List.of(admission(drifted)));

        assertThrows(
                IllegalStateException.class,
                () -> new MyBatisDataPullScopeAdmissionStore(mapper)
                        .requireActiveAdmissions(OperationCode.DP04, List.of(SOURCE))
        );
    }

    @Test
    void historicalAdmissionOutsideCurrentSourcesDoesNotRequireReverseClosure() {
        DataPullScope historical = scope("NOON-historical", "PRJ306");
        DataPullScopeAdmissionMapper mapper = Mockito.mock(DataPullScopeAdmissionMapper.class);
        when(mapper.listByScopeKeys(List.of(SOURCE.getStableScopeKey())))
                .thenReturn(List.of(admission(historical), admission(SOURCE)));

        List<AdmittedDataPullScope> resolved = new MyBatisDataPullScopeAdmissionStore(mapper)
                .requireActiveAdmissions(OperationCode.DP04, List.of(SOURCE));

        assertEquals(1, resolved.size());
    }

    private static DataPullScopeAdmission admission(DataPullScope scope) {
        return DataPullScopeAdmission.cutoverExisting(scope, CUTOVER, ADMITTED_AT);
    }

    private static DataPullScheduleCutover cutover() {
        return DataPullScheduleCutover.active(
                OperationCode.DP04,
                CUTOVER,
                0,
                "a".repeat(64),
                ADMITTED_AT.minusMinutes(5)
        );
    }

    private static DataPullScope scope(String stableKey, String projectCode) {
        return new DataPullScope(
                "NOON", 307L, 108065L, projectCode, null,
                projectCode, "STR108065-NSA", "SA", stableKey
        );
    }
}
