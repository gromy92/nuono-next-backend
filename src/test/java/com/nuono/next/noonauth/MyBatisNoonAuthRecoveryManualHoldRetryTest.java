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
