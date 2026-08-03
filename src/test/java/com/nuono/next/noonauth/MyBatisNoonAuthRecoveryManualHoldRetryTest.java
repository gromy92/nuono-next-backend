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


class MyBatisNoonAuthRecoveryManualHoldRetryTest {

    @Test
    void expiredSingleSendRateLimitHoldReopensWithoutResettingItsBudget() {
        NoonAuthRecoveryMapper mapper = mock(NoonAuthRecoveryMapper.class);
        MyBatisNoonAuthRecoveryRepository repository = new MyBatisNoonAuthRecoveryRepository(mapper);
        LocalDateTime now = LocalDateTime.of(2026, 7, 31, 2, 30);
        LocalDateTime cooldownCutoff = now.minusMinutes(30);
        NoonAuthIdentityRecoveryRecord held = recovery(
                351L,
                NoonAuthRecoveryStatus.MANUAL_HOLD,
                9L,
                "identity-hash"
        );
        held.setFailureCode("SEND_RATE_LIMITED");
        held.setConfigFingerprint("config-v1");
        held.setSendAttemptCount(1);
        held.setFirstSendAt(LocalDateTime.of(2026, 7, 29, 9, 0, 27));

        when(mapper.selectActiveRecoveryForUpdate("identity-hash")).thenReturn(held);
        when(mapper.releaseEligibleRateLimitedManualHold(
                351L,
                9L,
                "identity-hash",
                "config-v1",
                cooldownCutoff,
                now,
                now
        )).thenReturn(1);

        assertThat(repository.releaseEligibleManualHolds(
                "identity-hash",
                "config-v1",
                cooldownCutoff,
                now,
                now
        )).isEqualTo(1);

        InOrder ordered = Mockito.inOrder(mapper);
        ordered.verify(mapper).selectActiveRecoveryForUpdate("identity-hash");
        ordered.verify(mapper).releaseEligibleRateLimitedManualHold(
                351L,
                9L,
                "identity-hash",
                "config-v1",
                cooldownCutoff,
                now,
                now
        );
        ordered.verify(mapper).releaseRateLimitedProjectHolds(351L, now);
        verify(mapper, never()).reopenFailedRecoveryItems(351L, now);
    }

    @Test
    void differentManualHoldFailureNeverReopensAutomatically() {
        NoonAuthRecoveryMapper mapper = mock(NoonAuthRecoveryMapper.class);
        MyBatisNoonAuthRecoveryRepository repository = new MyBatisNoonAuthRecoveryRepository(mapper);
        LocalDateTime now = LocalDateTime.of(2026, 7, 31, 2, 30);
        NoonAuthIdentityRecoveryRecord held = recovery(
                352L,
                NoonAuthRecoveryStatus.MANUAL_HOLD,
                3L,
                "identity-hash"
        );
        held.setFailureCode("SEND_RISK_BLOCKED");
        held.setConfigFingerprint("config-v1");
        held.setSendAttemptCount(1);
        held.setFirstSendAt(now.minusHours(2));
        when(mapper.selectActiveRecoveryForUpdate("identity-hash")).thenReturn(held);

        assertThat(repository.releaseEligibleManualHolds(
                "identity-hash",
                "config-v1",
                now.minusMinutes(30),
                now,
                now
        )).isZero();

        verify(mapper, never()).releaseEligibleRateLimitedManualHold(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
        verify(mapper, never()).releaseRateLimitedProjectHolds(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void credentialChangeStillReopensAndResetsTheExistingManualHoldEpoch() {
        NoonAuthRecoveryMapper mapper = mock(NoonAuthRecoveryMapper.class);
        MyBatisNoonAuthRecoveryRepository repository = new MyBatisNoonAuthRecoveryRepository(mapper);
        LocalDateTime now = LocalDateTime.of(2026, 7, 31, 3, 0);
        LocalDateTime nextAttemptAt = now.plusMinutes(1);
        NoonAuthIdentityRecoveryRecord held = recovery(
                353L,
                NoonAuthRecoveryStatus.MANUAL_HOLD,
                7L,
                "identity-hash"
        );
        held.setFailureCode("MAILBOX_AUTH_FAILED");
        held.setConfigFingerprint("config-v1");
        when(mapper.selectActiveRecoveryForUpdate("identity-hash")).thenReturn(held);
        when(mapper.releaseChangedManualHolds(
                "identity-hash",
                "config-v2",
                nextAttemptAt,
                now
        )).thenReturn(1);

        assertThat(repository.releaseEligibleManualHolds(
                "identity-hash",
                "config-v2",
                now.minusMinutes(30),
                nextAttemptAt,
                now
        )).isEqualTo(1);

        InOrder ordered = Mockito.inOrder(mapper);
        ordered.verify(mapper).selectActiveRecoveryForUpdate("identity-hash");
        ordered.verify(mapper).releaseChangedManualHolds(
                "identity-hash",
                "config-v2",
                nextAttemptAt,
                now
        );
        ordered.verify(mapper).releaseProjectManualHolds(353L, "config-v2", now);
        ordered.verify(mapper).reopenFailedRecoveryItems(353L, now);
    }

    @Test
    void explicitRetryReopensAnOtherwiseIdenticalManualHoldBindingEpoch() {
        NoonAuthRecoveryMapper mapper = mock(NoonAuthRecoveryMapper.class);
        MyBatisNoonAuthRecoveryRepository repository = new MyBatisNoonAuthRecoveryRepository(mapper);
        LocalDateTime now = LocalDateTime.of(2026, 7, 27, 10, 50);
        LocalDateTime coalesceUntil = now.plusSeconds(15);
        LocalDateTime cooldownAt = now.plusMinutes(1);
        NoonAuthIdentityRecoveryRecord target = recovery(
                91L,
                NoonAuthRecoveryStatus.MANUAL_HOLD,
                4L,
                "identity-hash"
        );
        NoonProjectAuthStateRecord held = projectState(
                NoonProjectAuthStatus.MANUAL_HOLD,
                91L,
                4L,
                "binding-v1",
                "config-v1"
        );
        held.setIdentityKey("identity-hash");
        NoonProjectAuthStateRecord rebased = projectState(
                NoonProjectAuthStatus.REAUTH_REQUIRED,
                91L,
                5L,
                "binding-v1",
                "config-v1"
        );
        rebased.setIdentityKey("identity-hash");
        NoonAuthRecoveryItemRecord sourceLess = new NoonAuthRecoveryItemRecord();
        sourceLess.setRecoveryId(91L);
        sourceLess.setExpectedAuthVersion(4L);
        sourceLess.setStatus(NoonAuthRecoveryItemStatus.PENDING);

        when(mapper.selectRecoveryForUpdate(91L)).thenReturn(target);
        when(mapper.selectProjectAuthStateForUpdate(307L, "PRJ1")).thenReturn(held, rebased);
        when(mapper.selectSourceLessProjectRecoveryItemForUpdate(91L, 307L, "PRJ1"))
                .thenReturn(sourceLess);
        when(mapper.rebaseActiveRecoveryForBindingEpoch(
                91L,
                NoonAuthRecoveryStatus.MANUAL_HOLD,
                4L,
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
                "binding-v1",
                "config-v1",
                now
        )).thenReturn(2);

        assertThat(repository.rebaseProjectBindingEpoch(
                91L,
                307L,
                "PRJ1",
                "identity-hash",
                "binding-v1",
                "config-v1",
                coalesceUntil,
                cooldownAt,
                now
        )).isEqualTo(5L);

        verify(mapper).rebaseActiveRecoveryForBindingEpoch(
                91L,
                NoonAuthRecoveryStatus.MANUAL_HOLD,
                4L,
                "config-v1",
                coalesceUntil,
                cooldownAt,
                now
        );
        verify(mapper).rebaseProjectAuthStateForBindingEpoch(
                307L,
                "PRJ1",
                "identity-hash",
                91L,
                "binding-v1",
                "config-v1",
                now
        );
        verify(mapper).reopenProjectItemsForBindingEpoch(91L, 307L, "PRJ1", 5L, now);
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
