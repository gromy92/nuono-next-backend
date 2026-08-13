package com.nuono.next.noonauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noonpull.NoonPullDataDomain;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoonAuthRecoveryRenewalAdmissionTest {
    private NoonAuthRecoveryRepository recoveryRepository;
    private NoonAuthRecoveryCoordinator coordinator;
    private NoonAuthRecoveryProperties properties;
    private StoreSyncStoreRecord project;

    @BeforeEach
    void setUp() {
        recoveryRepository = mock(NoonAuthRecoveryRepository.class);
        StoreSyncMapper storeSyncMapper = mock(StoreSyncMapper.class);
        properties = new NoonAuthRecoveryProperties();
        properties.setEnabled(true);
        properties.setCoalesceSeconds(15);
        properties.setTrustedSenderDomains("noon.com");
        coordinator = new NoonAuthRecoveryCoordinator(
                recoveryRepository, storeSyncMapper, properties, "shared@example.com", "imap-secret",
                Clock.fixed(Instant.parse("2026-07-16T04:00:00Z"), ZoneOffset.UTC)
        );
        project = new StoreSyncStoreRecord();
        project.setProjectCode("PRJ1");
        project.setStoreCode("STORE-9");
        project.setSite("AE");
        when(storeSyncMapper.selectOwnerProject(307L, "STORE-9")).thenReturn(project);
        when(storeSyncMapper.selectOwnerStore(307L, "STORE-9")).thenReturn(project);
        when(recoveryRepository.selectProjectBindingFingerprint(307L, "PRJ1"))
                .thenReturn("binding-fingerprint-v1");
    }

    @Test
    void runningRenewalAbsorbsLateTaskWithoutCreatingAPredecessorSuccessorChain() {
        when(recoveryRepository.coalesceActiveRecovery(any())).thenReturn(91L);
        when(recoveryRepository.selectActiveRecoveryForUpdate(anyString())).thenReturn(active(91L));
        NoonProjectAuthStateRecord state = renewed(91L);
        when(recoveryRepository.selectProjectAuthStateForUpdate(307L, "PRJ1")).thenReturn(state);

        assertEquals(Optional.of(91L), enqueue(7L));

        verify(recoveryRepository).coalesceRecoveryItem(any());
    }

    @Test
    void historicalManualHoldDoesNotBlockANewIdentityRenewal() {
        when(recoveryRepository.selectProjectAuthState(307L, "PRJ1")).thenReturn(held(80L, 8L));
        when(recoveryRepository.coalesceActiveRecovery(any())).thenReturn(91L);
        when(recoveryRepository.selectActiveRecoveryForUpdate(anyString())).thenReturn(active(91L));
        when(recoveryRepository.selectProjectAuthStateForUpdate(307L, "PRJ1")).thenReturn(renewed(91L));

        assertEquals(Optional.of(91L), enqueue(9L));

        verify(recoveryRepository).retireLegacyManualHoldForFreshRenewal(anyString(), any());
        verifyBoundTo(91L, 9L);
    }

    @Test
    void historicalWaitingBatchIsNeverPromotedByANewTask() {
        when(recoveryRepository.selectProjectAuthState(307L, "PRJ1")).thenReturn(held(877L, 8L));
        when(recoveryRepository.coalesceActiveRecovery(any())).thenReturn(91L);
        when(recoveryRepository.selectActiveRecoveryForUpdate(anyString())).thenReturn(active(91L));
        when(recoveryRepository.selectProjectAuthStateForUpdate(307L, "PRJ1")).thenReturn(renewed(91L));

        assertEquals(Optional.of(91L), enqueue(9L));

        verify(recoveryRepository).retireLegacyManualHoldForFreshRenewal(anyString(), any());
        verifyBoundTo(91L, 9L);
    }

    @Test
    void sameFailedTaskCannotUseHistoricalManualHoldToStartAnotherOtpRenewal() {
        when(recoveryRepository.selectProjectAuthState(307L, "PRJ1")).thenReturn(held(80L, 9L));

        assertTrue(enqueue(9L).isEmpty());

        verify(recoveryRepository, never()).coalesceActiveRecovery(any());
    }

    private NoonAuthIdentityRecoveryRecord active(Long id) {
        NoonAuthIdentityRecoveryRecord record = new NoonAuthIdentityRecoveryRecord();
        record.setId(id);
        record.setStatus(NoonAuthRecoveryStatus.COALESCING);
        return record;
    }

    private NoonProjectAuthStateRecord held(Long recoveryId, Long lastFailureTaskId) {
        NoonProjectAuthStateRecord state = renewed(recoveryId);
        state.setStatus(NoonProjectAuthStatus.MANUAL_HOLD);
        state.setLastFailureTaskId(lastFailureTaskId);
        return state;
    }

    private NoonProjectAuthStateRecord renewed(Long recoveryId) {
        NoonProjectAuthStateRecord state = new NoonProjectAuthStateRecord();
        state.setActiveRecoveryId(recoveryId);
        state.setAuthVersion(8L);
        state.setStatus(NoonProjectAuthStatus.REAUTH_REQUIRED);
        state.setBindingFingerprint("binding-fingerprint-v1");
        state.setConfigFingerprint(NoonAuthIdentityKey.configFingerprint(
                "shared@example.com", "imap-secret", properties.normalizedTrustedSenderDomains()));
        return state;
    }

    private void verifyBoundTo(Long recoveryId, Long taskId) {
        verify(recoveryRepository).upsertProjectAuthRequired(
                eq(307L), eq("PRJ1"), anyString(), eq(recoveryId),
                eq("binding-fingerprint-v1"), anyString(), eq("AUTH_REQUIRED"), eq(taskId), any()
        );
    }

    private Optional<Long> enqueue(Long taskId) {
        return coordinator.enqueue(NoonAuthWaitRequest.task(
                307L, null, "STORE-9", "AE", NoonPullDataDomain.ORDER.name(), taskId,
                NoonPullDataDomain.ORDER.name(), NoonAuthResumePolicy.AUTO_RESUME, null
        ));
    }
}
