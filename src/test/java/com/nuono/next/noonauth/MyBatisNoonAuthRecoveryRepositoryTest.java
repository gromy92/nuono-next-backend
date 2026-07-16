package com.nuono.next.noonauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.NoonAuthRecoveryMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

class MyBatisNoonAuthRecoveryRepositoryTest {

    @Test
    void explicitBindingRebasesActiveRecoveryProjectAndEveryEligibleItemInRecoveryFirstOrder() {
        NoonAuthRecoveryMapper mapper = mock(NoonAuthRecoveryMapper.class);
        MyBatisNoonAuthRecoveryRepository repository = new MyBatisNoonAuthRecoveryRepository(mapper);
        LocalDateTime now = LocalDateTime.of(2026, 7, 16, 12, 0);
        LocalDateTime coalesceUntil = now.plusSeconds(15);
        LocalDateTime cooldownAt = now.plusMinutes(1);
        NoonAuthIdentityRecoveryRecord target = recovery(
                91L,
                NoonAuthRecoveryStatus.RECOVERING_PULLS,
                7L,
                "identity-hash"
        );
        NoonProjectAuthStateRecord healthy = projectState(
                NoonProjectAuthStatus.HEALTHY,
                null,
                8L,
                "old-binding",
                "config-v1"
        );
        NoonProjectAuthStateRecord rebased = projectState(
                NoonProjectAuthStatus.REAUTH_REQUIRED,
                91L,
                9L,
                "binding-v2",
                "config-v1"
        );
        rebased.setIdentityKey("identity-hash");
        NoonAuthRecoveryItemRecord sourceLess = new NoonAuthRecoveryItemRecord();
        sourceLess.setRecoveryId(91L);
        sourceLess.setExpectedAuthVersion(7L);
        sourceLess.setStatus(NoonAuthRecoveryItemStatus.RECOVERED);

        when(mapper.selectRecoveryForUpdate(91L)).thenReturn(target);
        when(mapper.selectProjectAuthStateForUpdate(307L, "PRJ1")).thenReturn(healthy, rebased);
        when(mapper.selectSourceLessProjectRecoveryItemForUpdate(91L, 307L, "PRJ1"))
                .thenReturn(sourceLess);
        when(mapper.rebaseActiveRecoveryForBindingEpoch(
                91L,
                NoonAuthRecoveryStatus.RECOVERING_PULLS,
                7L,
                "config-v1",
                coalesceUntil,
                cooldownAt,
                now
        )).thenReturn(1);
        when(mapper.rebaseProjectAuthStateForBindingEpoch(
                307L,
                "PRJ1",
                "identity-hash",
                91L,
                "binding-v2",
                "config-v1",
                now
        )).thenReturn(2);

        assertThat(repository.rebaseProjectBindingEpoch(
                91L,
                307L,
                "PRJ1",
                "identity-hash",
                "binding-v2",
                "config-v1",
                coalesceUntil,
                cooldownAt,
                now
        )).isEqualTo(9L);

        InOrder ordered = Mockito.inOrder(mapper);
        ordered.verify(mapper).selectRecoveryForUpdate(91L);
        ordered.verify(mapper).selectProjectAuthStateForUpdate(307L, "PRJ1");
        ordered.verify(mapper).selectSourceLessProjectRecoveryItemForUpdate(91L, 307L, "PRJ1");
        ordered.verify(mapper).rebaseActiveRecoveryForBindingEpoch(
                91L,
                NoonAuthRecoveryStatus.RECOVERING_PULLS,
                7L,
                "config-v1",
                coalesceUntil,
                cooldownAt,
                now
        );
        ordered.verify(mapper).rebaseProjectAuthStateForBindingEpoch(
                307L,
                "PRJ1",
                "identity-hash",
                91L,
                "binding-v2",
                "config-v1",
                now
        );
        ordered.verify(mapper).selectProjectAuthStateForUpdate(307L, "PRJ1");
        ordered.verify(mapper).reopenProjectItemsForBindingEpoch(91L, 307L, "PRJ1", 9L, now);
    }

    @Test
    void exactPendingSourceLessBindingEpochIsIdempotent() {
        NoonAuthRecoveryMapper mapper = mock(NoonAuthRecoveryMapper.class);
        MyBatisNoonAuthRecoveryRepository repository = new MyBatisNoonAuthRecoveryRepository(mapper);
        LocalDateTime now = LocalDateTime.of(2026, 7, 16, 12, 0);
        NoonAuthIdentityRecoveryRecord target = recovery(
                91L,
                NoonAuthRecoveryStatus.COALESCING,
                3L,
                "identity-hash"
        );
        NoonProjectAuthStateRecord state = projectState(
                NoonProjectAuthStatus.REAUTH_REQUIRED,
                91L,
                4L,
                "binding-v1",
                "config-v1"
        );
        state.setIdentityKey("identity-hash");
        NoonAuthRecoveryItemRecord sourceLess = new NoonAuthRecoveryItemRecord();
        sourceLess.setRecoveryId(91L);
        sourceLess.setExpectedAuthVersion(4L);
        sourceLess.setStatus(NoonAuthRecoveryItemStatus.PENDING);

        when(mapper.selectRecoveryForUpdate(91L)).thenReturn(target);
        when(mapper.selectProjectAuthStateForUpdate(307L, "PRJ1")).thenReturn(state);
        when(mapper.selectSourceLessProjectRecoveryItemForUpdate(91L, 307L, "PRJ1"))
                .thenReturn(sourceLess);

        assertThat(repository.rebaseProjectBindingEpoch(
                91L,
                307L,
                "PRJ1",
                "identity-hash",
                "binding-v1",
                "config-v1",
                now.plusSeconds(15),
                now.plusMinutes(1),
                now
        )).isEqualTo(4L);

        verify(mapper, never()).rebaseActiveRecoveryForBindingEpoch(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
        verify(mapper, never()).rebaseProjectAuthStateForBindingEpoch(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void successorBindingEpochDoesNotResetItsActivePredecessor() {
        NoonAuthRecoveryMapper mapper = mock(NoonAuthRecoveryMapper.class);
        MyBatisNoonAuthRecoveryRepository repository = new MyBatisNoonAuthRecoveryRepository(mapper);
        LocalDateTime now = LocalDateTime.of(2026, 7, 16, 12, 0);
        NoonAuthIdentityRecoveryRecord successor = recovery(
                92L,
                NoonAuthRecoveryStatus.WAITING_PREDECESSOR,
                2L,
                "identity-hash"
        );
        successor.setPredecessorRecoveryId(91L);
        NoonProjectAuthStateRecord state = projectState(
                NoonProjectAuthStatus.REAUTH_REQUIRED,
                92L,
                4L,
                "binding-v1",
                "config-v1"
        );
        NoonProjectAuthStateRecord rebased = projectState(
                NoonProjectAuthStatus.REAUTH_REQUIRED,
                92L,
                5L,
                "binding-v2",
                "config-v1"
        );
        rebased.setIdentityKey("identity-hash");
        when(mapper.selectRecoveryForUpdate(92L)).thenReturn(successor);
        when(mapper.selectProjectAuthStateForUpdate(307L, "PRJ1")).thenReturn(state, rebased);
        when(mapper.rebaseWaitingSuccessorForBindingEpoch(92L, 2L, "config-v1", now)).thenReturn(1);
        when(mapper.rebaseProjectAuthStateForBindingEpoch(
                307L, "PRJ1", "identity-hash", 92L, "binding-v2", "config-v1", now
        )).thenReturn(2);

        assertThat(repository.rebaseProjectBindingEpoch(
                92L, 307L, "PRJ1", "identity-hash", "binding-v2", "config-v1",
                now.plusSeconds(15), now.plusMinutes(1), now
        )).isEqualTo(5L);

        verify(mapper).rebaseWaitingSuccessorForBindingEpoch(92L, 2L, "config-v1", now);
        verify(mapper, never()).rebaseActiveRecoveryForBindingEpoch(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void terminalRecoveryCannotBeRevivedAsBindingEpochTarget() {
        NoonAuthRecoveryMapper mapper = mock(NoonAuthRecoveryMapper.class);
        MyBatisNoonAuthRecoveryRepository repository = new MyBatisNoonAuthRecoveryRepository(mapper);
        NoonAuthIdentityRecoveryRecord terminal = recovery(
                80L,
                NoonAuthRecoveryStatus.COMPLETED,
                9L,
                "identity-hash"
        );
        when(mapper.selectRecoveryForUpdate(80L)).thenReturn(terminal);

        assertThatThrownBy(() -> repository.rebaseProjectBindingEpoch(
                80L,
                307L,
                "PRJ1",
                "identity-hash",
                "binding-v2",
                "config-v1",
                LocalDateTime.of(2026, 7, 16, 12, 0, 15),
                LocalDateTime.of(2026, 7, 16, 12, 1),
                LocalDateTime.of(2026, 7, 16, 12, 0)
        )).isInstanceOf(IllegalStateException.class);

        verify(mapper, never()).selectProjectAuthStateForUpdate(307L, "PRJ1");
    }

    @Test
    void terminalRecoveryBlockedTasksAreRequeuedBeforeTheirHistoricalItemsBecomeStale() {
        NoonAuthRecoveryMapper mapper = mock(NoonAuthRecoveryMapper.class);
        MyBatisNoonAuthRecoveryRepository repository = new MyBatisNoonAuthRecoveryRepository(mapper);
        LocalDateTime now = LocalDateTime.of(2026, 7, 16, 12, 0);
        NoonAuthIdentityRecoveryRecord target = recovery(
                91L,
                NoonAuthRecoveryStatus.COALESCING,
                0L,
                "identity-hash"
        );
        NoonProjectAuthStateRecord old = projectState(
                NoonProjectAuthStatus.MANUAL_HOLD,
                80L,
                7L,
                "binding-v1",
                "config-v1"
        );
        NoonProjectAuthStateRecord rebased = projectState(
                NoonProjectAuthStatus.REAUTH_REQUIRED,
                91L,
                8L,
                "binding-v2",
                "config-v1"
        );
        rebased.setIdentityKey("identity-hash");
        when(mapper.selectRecoveryForUpdate(91L)).thenReturn(target);
        when(mapper.selectProjectAuthStateForUpdate(307L, "PRJ1")).thenReturn(old, rebased);
        when(mapper.rebaseActiveRecoveryForBindingEpoch(
                91L,
                NoonAuthRecoveryStatus.COALESCING,
                0L,
                "config-v1",
                now.plusSeconds(15),
                now.plusMinutes(1),
                now
        )).thenReturn(1);
        when(mapper.rebaseProjectAuthStateForBindingEpoch(
                307L, "PRJ1", "identity-hash", 91L, "binding-v2", "config-v1", now
        )).thenReturn(2);
        when(mapper.requeueTerminalBlockedProjectTasksForBindingEpoch(80L, 307L, "PRJ1", now))
                .thenReturn(1);
        when(mapper.staleTerminalBlockedProjectItemsForBindingEpoch(80L, 307L, "PRJ1", now))
                .thenReturn(1);

        assertThat(repository.rebaseProjectBindingEpoch(
                91L,
                307L,
                "PRJ1",
                "identity-hash",
                "binding-v2",
                "config-v1",
                now.plusSeconds(15),
                now.plusMinutes(1),
                now
        )).isEqualTo(8L);

        InOrder ordered = Mockito.inOrder(mapper);
        ordered.verify(mapper).requeueTerminalBlockedProjectTasksForBindingEpoch(80L, 307L, "PRJ1", now);
        ordered.verify(mapper).staleTerminalBlockedProjectItemsForBindingEpoch(80L, 307L, "PRJ1", now);
    }

    @Test
    void fencedSendIntentAndIndependentQuotaLedgerCommitTogether() {
        NoonAuthRecoveryMapper mapper = mock(NoonAuthRecoveryMapper.class);
        MyBatisNoonAuthRecoveryRepository repository = new MyBatisNoonAuthRecoveryRepository(mapper);
        LocalDateTime now = LocalDateTime.of(2026, 7, 16, 12, 0);
        when(mapper.recordSendIntent(
                91L,
                NoonAuthRecoveryStatus.AUTHENTICATING,
                7L,
                "lease-token",
                now,
                now
        )).thenReturn(1);
        when(mapper.insertIdentitySendLedger(91L, now, now)).thenReturn(1);

        assertThat(repository.recordSendIntent(
                91L,
                NoonAuthRecoveryStatus.AUTHENTICATING,
                7L,
                "lease-token",
                now,
                now
        )).isTrue();

        InOrder ordered = Mockito.inOrder(mapper);
        ordered.verify(mapper).recordSendIntent(
                91L,
                NoonAuthRecoveryStatus.AUTHENTICATING,
                7L,
                "lease-token",
                now,
                now
        );
        ordered.verify(mapper).insertIdentitySendLedger(91L, now, now);
    }

    @Test
    void featureOffDrainCancelsSkipsRequeuesAndReleasesEveryIdentityInOneRepositoryTransaction() {
        NoonAuthRecoveryMapper mapper = mock(NoonAuthRecoveryMapper.class);
        MyBatisNoonAuthRecoveryRepository repository = new MyBatisNoonAuthRecoveryRepository(mapper);
        LocalDateTime now = LocalDateTime.of(2026, 7, 16, 12, 0);
        when(mapper.cancelRecoveriesForDrain(
                null,
                "FEATURE_DISABLED",
                "automatic auth recovery disabled; original pull task requeued",
                now
        )).thenReturn(3);

        assertThat(repository.drainDisabledRecoveries(now)).isEqualTo(3);

        InOrder ordered = Mockito.inOrder(mapper);
        ordered.verify(mapper).cancelRecoveriesForDrain(
                null,
                "FEATURE_DISABLED",
                "automatic auth recovery disabled; original pull task requeued",
                now
        );
        ordered.verify(mapper).skipItemsForDrainedRecoveries(
                null,
                "FEATURE_DISABLED",
                "automatic auth recovery disabled; original pull task requeued",
                now
        );
        ordered.verify(mapper).requeueTasksForDrainedRecoveries(
                null,
                "automatic auth recovery disabled; original pull task requeued",
                now
        );
        ordered.verify(mapper).releaseProjectsForDrainedRecoveries(
                null,
                "FEATURE_DISABLED",
                now
        );
    }

    @Test
    void identityMismatchDrainIsScopedToThePersistedOldIdentity() {
        NoonAuthRecoveryMapper mapper = mock(NoonAuthRecoveryMapper.class);
        MyBatisNoonAuthRecoveryRepository repository = new MyBatisNoonAuthRecoveryRepository(mapper);
        LocalDateTime now = LocalDateTime.of(2026, 7, 16, 12, 0);

        repository.drainIdentityRecoveries("old-identity", now);

        verify(mapper).cancelRecoveriesForDrain(
                "old-identity",
                "IDENTITY_CONFIG_MISMATCH",
                "configured shared email identity changed; original pull task requeued",
                now
        );
        verify(mapper).requeueTasksForDrainedRecoveries(
                "old-identity",
                "configured shared email identity changed; original pull task requeued",
                now
        );
    }

    @Test
    void configurationChangeReopensIdentityProjectsAndFailedItemsTogether() {
        NoonAuthRecoveryMapper mapper = mock(NoonAuthRecoveryMapper.class);
        MyBatisNoonAuthRecoveryRepository repository = new MyBatisNoonAuthRecoveryRepository(mapper);
        NoonAuthIdentityRecoveryRecord active = new NoonAuthIdentityRecoveryRecord();
        active.setId(91L);
        LocalDateTime now = LocalDateTime.of(2026, 7, 16, 12, 0);
        LocalDateTime nextAttemptAt = now.plusMinutes(1);
        when(mapper.selectActiveRecoveryForUpdate("identity-hash")).thenReturn(active);
        when(mapper.releaseManualHoldOnConfigChange(
                "identity-hash",
                "old-fingerprint",
                "new-fingerprint",
                nextAttemptAt,
                now
        )).thenReturn(1);

        boolean released = repository.releaseManualHoldOnConfigChange(
                "identity-hash",
                "old-fingerprint",
                "new-fingerprint",
                nextAttemptAt,
                now
        );

        assertThat(released).isTrue();
        verify(mapper).releaseProjectManualHolds(91L, "new-fingerprint", now);
        verify(mapper).reopenFailedRecoveryItems(91L, now);
    }

    @Test
    void unchangedOrStaleConfigurationFenceDoesNotReopenProjectsOrItems() {
        NoonAuthRecoveryMapper mapper = mock(NoonAuthRecoveryMapper.class);
        MyBatisNoonAuthRecoveryRepository repository = new MyBatisNoonAuthRecoveryRepository(mapper);
        NoonAuthIdentityRecoveryRecord active = new NoonAuthIdentityRecoveryRecord();
        active.setId(91L);
        LocalDateTime now = LocalDateTime.of(2026, 7, 16, 12, 0);
        LocalDateTime nextAttemptAt = now.plusMinutes(1);
        when(mapper.selectActiveRecoveryForUpdate("identity-hash")).thenReturn(active);

        boolean released = repository.releaseManualHoldOnConfigChange(
                "identity-hash",
                "stale-fingerprint",
                "new-fingerprint",
                nextAttemptAt,
                now
        );

        assertThat(released).isFalse();
        verify(mapper, never()).releaseProjectManualHolds(91L, "new-fingerprint", now);
        verify(mapper, never()).reopenFailedRecoveryItems(91L, now);
    }

    @Test
    void configurationChangeAlsoReleasesTerminalPartialFailureProjectHolds() {
        NoonAuthRecoveryMapper mapper = mock(NoonAuthRecoveryMapper.class);
        MyBatisNoonAuthRecoveryRepository repository = new MyBatisNoonAuthRecoveryRepository(mapper);
        LocalDateTime now = LocalDateTime.of(2026, 7, 16, 12, 0);
        when(mapper.releaseTerminalProjectHoldsOnConfigChange(
                "identity-hash",
                "new-fingerprint",
                now
        )).thenReturn(2);

        assertThat(repository.releaseTerminalProjectHoldsOnConfigChange(
                "identity-hash",
                "new-fingerprint",
                now
        )).isEqualTo(2);

        InOrder ordered = Mockito.inOrder(mapper);
        ordered.verify(mapper).requeueTerminalBlockedTasksOnConfigChange(
                "identity-hash",
                "new-fingerprint",
                now
        );
        ordered.verify(mapper).staleTerminalItemsOnConfigChange(
                "identity-hash",
                "new-fingerprint",
                now
        );
        ordered.verify(mapper).releaseTerminalProjectHoldsOnConfigChange(
                "identity-hash",
                "new-fingerprint",
                now
        );
    }

    private NoonAuthIdentityRecoveryRecord recovery(
            Long id,
            NoonAuthRecoveryStatus status,
            Long version,
            String identityKey
    ) {
        NoonAuthIdentityRecoveryRecord recovery = new NoonAuthIdentityRecoveryRecord();
        recovery.setId(id);
        recovery.setStatus(status);
        recovery.setVersionNo(version);
        recovery.setIdentityKey(identityKey);
        return recovery;
    }

    private NoonProjectAuthStateRecord projectState(
            NoonProjectAuthStatus status,
            Long recoveryId,
            Long authVersion,
            String bindingFingerprint,
            String configFingerprint
    ) {
        NoonProjectAuthStateRecord state = new NoonProjectAuthStateRecord();
        state.setStatus(status);
        state.setActiveRecoveryId(recoveryId);
        state.setAuthVersion(authVersion);
        state.setBindingFingerprint(bindingFingerprint);
        state.setConfigFingerprint(configFingerprint);
        return state;
    }
}
