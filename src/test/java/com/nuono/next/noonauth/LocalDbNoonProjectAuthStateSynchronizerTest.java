package com.nuono.next.noonauth;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.NoonAuthRecoveryMapper;
import org.junit.jupiter.api.Test;

class LocalDbNoonProjectAuthStateSynchronizerTest {

    @Test
    void verifiedListingSessionReleasesTheProjectAndDrainsItsRecoveryWork() {
        NoonAuthRecoveryMapper mapper = org.mockito.Mockito.mock(
                NoonAuthRecoveryMapper.class
        );
        NoonProjectAuthStateRecord state = state(
                NoonProjectAuthStatus.MANUAL_HOLD,
                17L,
                3L
        );
        when(mapper.selectProjectAuthStateForUpdate(307L, "PRJ101128"))
                .thenReturn(state);
        when(mapper.releaseProjectAfterManualReauthentication(
                eq(307L),
                eq("PRJ101128"),
                eq(3L),
                eq(17L),
                any()
        )).thenReturn(1);
        when(mapper.completeRecoveryAfterManualReauthenticationIfDrained(
                eq(17L),
                any()
        )).thenReturn(1);

        new LocalDbNoonProjectAuthStateSynchronizer(mapper)
                .recordVerifiedProjectSession(307L, "PRJ101128");

        verify(mapper).requeueProjectPullTasksAfterManualReauthentication(
                eq(17L),
                eq(307L),
                eq("PRJ101128"),
                any()
        );
        verify(mapper).recoverProjectItemsAfterManualReauthentication(
                eq(17L),
                eq(307L),
                eq("PRJ101128"),
                any()
        );
        verify(mapper).promoteSuccessorForPredecessor(
                eq(17L),
                any(),
                any()
        );
    }

    @Test
    void healthyProjectNeedsNoRecoveryMutation() {
        NoonAuthRecoveryMapper mapper = org.mockito.Mockito.mock(
                NoonAuthRecoveryMapper.class
        );
        when(mapper.selectProjectAuthStateForUpdate(307L, "PRJ101128"))
                .thenReturn(state(NoonProjectAuthStatus.HEALTHY, null, 4L));

        new LocalDbNoonProjectAuthStateSynchronizer(mapper)
                .recordVerifiedProjectSession(307L, "PRJ101128");

        verify(mapper, never()).releaseProjectAfterManualReauthentication(
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void concurrentProjectStateChangeFailsTheOuterReauthenticationTransaction() {
        NoonAuthRecoveryMapper mapper = org.mockito.Mockito.mock(
                NoonAuthRecoveryMapper.class
        );
        when(mapper.selectProjectAuthStateForUpdate(307L, "PRJ101128"))
                .thenReturn(state(
                        NoonProjectAuthStatus.REAUTH_REQUIRED,
                        17L,
                        3L
                ));

        assertThrows(
                IllegalStateException.class,
                () -> new LocalDbNoonProjectAuthStateSynchronizer(mapper)
                        .recordVerifiedProjectSession(307L, "PRJ101128")
        );
    }

    private NoonProjectAuthStateRecord state(
            NoonProjectAuthStatus status,
            Long recoveryId,
            Long authVersion
    ) {
        NoonProjectAuthStateRecord state = new NoonProjectAuthStateRecord();
        state.setOwnerUserId(307L);
        state.setProjectCode("PRJ101128");
        state.setStatus(status);
        state.setActiveRecoveryId(recoveryId);
        state.setAuthVersion(authVersion);
        return state;
    }
}
